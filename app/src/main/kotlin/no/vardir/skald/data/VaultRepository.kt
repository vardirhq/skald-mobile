package no.vardir.skald.data

import android.content.Context
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.vardir.skald.core.graph.Layout
import no.vardir.skald.core.model.BacklinkRef
import no.vardir.skald.core.model.DeletedNoteEntry
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

/** The one place that owns the vault, its index, note payloads and sync engine. */
class VaultRepository(
    context: Context,
    private val scope: CoroutineScope,
    vaultDir: String,
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
        val laidOut = built.graph.nodes.associate { it.path to (it.x to it.y) }
        if (laidOut != vault.loadPositions()) vault.savePositions(laidOut)
        _snapshot.value = built
        sync.notifyVaultChanged()
    }

    suspend fun seed() = withContext(Dispatchers.IO) {
        SeedVault.write(vault, today())
        reindex()
    }

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
            noteTheme = vault.resolveNoteTheme(parsed.frontmatter, meta.schema, settings),
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

    suspend fun createNote(folder: String, title: String, schema: String? = null): String =
        withContext(Dispatchers.IO) {
            val path = vault.createNote(folder, title, schema, settings, today())
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

    suspend fun moveNote(path: String, folder: String): String? = withContext(Dispatchers.IO) {
        val result = vault.move(path, folder)
        reindex()
        result
    }

    suspend fun moveNotes(paths: Set<String>, folder: String): Map<String, String>? = withContext(Dispatchers.IO) {
        val result = vault.moveMany(paths, folder)
        reindex()
        result
    }

    suspend fun deleteNotes(paths: Set<String>): Int = withContext(Dispatchers.IO) {
        val result = vault.deleteMany(paths)
        reindex()
        result
    }

    suspend fun duplicateNote(path: String): String? = withContext(Dispatchers.IO) {
        val result = vault.duplicate(path)
        reindex()
        result
    }

    suspend fun editFrontmatter(
        path: String,
        changes: Map<String, Any?> = emptyMap(),
        remove: Set<String> = emptySet(),
    ): Boolean = withContext(Dispatchers.IO) {
        val raw = vault.read(path) ?: return@withContext false
        val updated = Frontmatter.apply(raw, changes, remove)
        if (updated == raw) return@withContext false
        vault.write(path, updated)
        reindex()
        true
    }

    suspend fun createFolder(path: String): Boolean = withContext(Dispatchers.IO) {
        val made = vault.createFolder(path)
        if (made) reindex()
        made
    }

    suspend fun renameFolder(from: String, to: String): String? = withContext(Dispatchers.IO) {
        val result = vault.renameFolder(from, to)
        reindex()
        result
    }

    suspend fun deleteFolder(path: String): Boolean = withContext(Dispatchers.IO) {
        val gone = vault.deleteFolder(path)
        if (gone) reindex()
        gone
    }

    suspend fun openDaily(): String = withContext(Dispatchers.IO) {
        val path = vault.ensureDaily(settings, today())
        reindex()
        path
    }

    suspend fun history(path: String): List<NoteHistoryEntry> = withContext(Dispatchers.IO) { vault.history(path) }
    suspend fun readVersion(path: String, id: String): String? = withContext(Dispatchers.IO) { vault.readVersion(path, id) }

    suspend fun restoreVersion(path: String, id: String) = withContext(Dispatchers.IO) {
        vault.restoreVersion(path, id)
        reindex()
    }

    suspend fun deletedNotes(): List<DeletedNoteEntry> = withContext(Dispatchers.IO) { vault.deletedNotes() }

    suspend fun restoreDeleted(path: String, id: String): Boolean = withContext(Dispatchers.IO) {
        val restored = vault.restoreDeleted(path, id)
        if (restored) reindex()
        restored
    }

    suspend fun editTask(path: String, line: Int, edits: Tasks.Edits) = withContext(Dispatchers.IO) {
        if (vault.editTask(path, line, edits)) reindex()
    }

    suspend fun addTask(notePath: String?, line: String): String = withContext(Dispatchers.IO) {
        val target = notePath?.takeIf { vault.exists(it) } ?: vault.ensureDaily(settings, today())
        vault.appendLine(target, line)
        reindex()
        target
    }

    suspend fun updateSettings(transform: (VaultSettings) -> VaultSettings) = withContext(Dispatchers.IO) {
        settings = transform(settings)
        vault.saveSettings(settings)
        _snapshot.value = _snapshot.value.copy(settings = settings)
    }

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
