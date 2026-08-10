package no.vardir.skald.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.vardir.skald.core.graph.Layout
import no.vardir.skald.core.model.BacklinkRef
import no.vardir.skald.core.model.NoteHistoryEntry
import no.vardir.skald.core.model.NotePayload
import no.vardir.skald.core.model.VaultSettings
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.core.sync.PairingTicket
import no.vardir.skald.core.sync.SyncDeviceInfo
import no.vardir.skald.core.sync.SyncEngine
import no.vardir.skald.core.sync.SyncStatus
import no.vardir.skald.core.text.Frontmatter
import no.vardir.skald.core.text.Tasks
import no.vardir.skald.core.vault.VaultIndex
import java.io.File
import java.time.LocalDate

/**
 * The one place that owns the vault, the index and the sync engine. Every
 * screen reads the same [VaultSnapshot]; nothing else parses a note.
 */
class VaultRepository(
    context: Context,
    private val scope: CoroutineScope,
    /** Folder under `files/vaults/`, chosen during setup. */
    vaultDir: String,
    /** What the person called it, which the folder name may have had to sanitize. */
    private val vaultName: String,
) {

    private val appContext = context.applicationContext

    val vault: FileVault = FileVault(File(appContext.filesDir, "vaults/$vaultDir"))

    private val secrets = KeystoreSecrets(appContext)

    val sync: SyncEngine = SyncEngine(
        vault = vault,
        secrets = secrets,
        stateStore = vault.syncStateStore(),
        devicePrefix = "phone",
    )

    val syncStatus: StateFlow<SyncStatus> get() = sync.status

    private val _snapshot = MutableStateFlow(emptySnapshot())
    val snapshot: StateFlow<VaultSnapshot> get() = _snapshot.asStateFlow()

    private var settings: VaultSettings = VaultSettings()

    private fun emptySnapshot(): VaultSnapshot = VaultIndex.build(
        vaultName = vaultName,
        files = emptyList(),
        settings = VaultSettings(),
        todayIso = today(),
    )

    fun today(): String = LocalDate.now().toString()

    // ---------- indexing ----------

    /** Re-reads the vault from disk and republishes the snapshot. */
    suspend fun reindex() = withContext(Dispatchers.IO) {
        settings = vault.loadSettings()
        val positions = vault.loadPositions().mapValues { Layout.Point(it.value.first, it.value.second) }
        val built = VaultIndex.build(
            vaultName = vaultName,
            files = vault.notes(),
            settings = settings,
            positions = positions,
            todayIso = today(),
            emptyFolders = vault.folders(),
        )
        // The layout is only interesting once: persist what it decided, so the
        // constellation is a place you return to rather than a fresh guess.
        val laidOut = built.graph.nodes.associate { it.path to (it.x to it.y) }
        if (laidOut != vault.loadPositions()) vault.savePositions(laidOut)
        _snapshot.value = built
        sync.notifyVaultChanged()
    }

    /**
     * Writes the sample vault. Only ever called from setup, and only when it was
     * asked for: these notes are examples, not the person's, and a vault that
     * refills itself with them is one nobody can empty.
     */
    suspend fun seed() = withContext(Dispatchers.IO) {
        SeedVault.write(vault, today())
        reindex()
    }

    // ---------- notes ----------

    suspend fun note(path: String): NotePayload? = withContext(Dispatchers.IO) {
        val meta = _snapshot.value.byPath[path] ?: return@withContext null
        val raw = vault.read(path) ?: return@withContext null
        val parsed = Frontmatter.parse(raw)
        NotePayload(
            meta = meta,
            content = raw,
            body = parsed.body,
            bodyStartLine = parsed.bodyStartLine,
            backlinks = backlinks(path),
            attachments = vault.attachmentsIn(path, parsed.body),
        )
    }

    private fun backlinks(path: String): List<BacklinkRef> =
        VaultIndex.backlinks(_snapshot.value, path) { source ->
            vault.read(source)?.let { Frontmatter.parse(it).body }
        }

    suspend fun saveNote(path: String, content: String) = withContext(Dispatchers.IO) {
        vault.write(path, content)
        reindex()
    }

    suspend fun createNote(folder: String, title: String): String = withContext(Dispatchers.IO) {
        val path = vault.createNote(folder, title)
        reindex()
        path
    }

    suspend fun deleteNote(path: String) = withContext(Dispatchers.IO) {
        vault.delete(path)
        reindex()
    }

    suspend fun renameNote(from: String, to: String): String? = withContext(Dispatchers.IO) {
        val result = vault.rename(from, to)
        reindex()
        result
    }

    suspend fun openDaily(): String = withContext(Dispatchers.IO) {
        val path = vault.ensureDaily(settings, today())
        reindex()
        path
    }

    suspend fun history(path: String): List<NoteHistoryEntry> = withContext(Dispatchers.IO) { vault.history(path) }

    suspend fun readVersion(path: String, id: String): String? =
        withContext(Dispatchers.IO) { vault.readVersion(path, id) }

    suspend fun restoreVersion(path: String, id: String) = withContext(Dispatchers.IO) {
        vault.restoreVersion(path, id)
        reindex()
    }

    // ---------- threads ----------

    /**
     * The bidirectional binding: a task edited anywhere rewrites the line in its
     * parent note, and the index picks the change back up.
     */
    suspend fun editTask(path: String, line: Int, edits: Tasks.Edits) = withContext(Dispatchers.IO) {
        if (vault.editTask(path, line, edits)) reindex()
    }

    // ---------- settings ----------

    suspend fun updateSettings(transform: (VaultSettings) -> VaultSettings) = withContext(Dispatchers.IO) {
        settings = transform(settings)
        vault.saveSettings(settings)
        _snapshot.value = _snapshot.value.copy(settings = settings)
    }

    /** A star dragged to a new place stays there. */
    fun moveStar(path: String, x: Float, y: Float) {
        scope.launch(Dispatchers.IO) {
            val positions = vault.loadPositions().toMutableMap()
            positions[path] = x to y
            vault.savePositions(positions)
            _snapshot.value = _snapshot.value.let { snap ->
                snap.copy(
                    graph = snap.graph.copy(
                        nodes = snap.graph.nodes.map { if (it.path == path) it.copy(x = x, y = y) else it }
                    )
                )
            }
        }
    }

    // ---------- sync ----------

    suspend fun connectSync(serverUrl: String, handle: String?, provisioningSecret: String?) {
        sync.connect(serverUrl, handle?.ifBlank { null }, provisioningSecret?.ifBlank { null })
        SyncScheduler.enable(appContext)
        reindex()
    }

    suspend fun pairSync(pairingUri: String) {
        sync.pair(pairingUri)
        SyncScheduler.enable(appContext)
        reindex()
    }

    suspend fun syncNow() {
        sync.syncNow(force = true)
        reindex()
    }

    suspend fun republishEverything() {
        sync.republishEverything()
        reindex()
    }

    suspend fun mintPairing(): PairingTicket = sync.mintPairing()

    suspend fun listDevices(): List<SyncDeviceInfo> = sync.listDevices()

    suspend fun revokeDevice(deviceId: String): List<SyncDeviceInfo> = sync.revokeDevice(deviceId)

    fun setSyncEnabled(enabled: Boolean) {
        sync.setEnabled(enabled)
        if (enabled) SyncScheduler.enable(appContext) else SyncScheduler.disable(appContext)
    }

    fun disconnectSync() {
        sync.disconnect()
        SyncScheduler.disable(appContext)
    }
}
