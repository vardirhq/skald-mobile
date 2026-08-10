package no.vardir.skald.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.vardir.skald.core.model.Density
import no.vardir.skald.core.model.LogoVariant
import no.vardir.skald.core.model.NotePayload
import no.vardir.skald.core.model.TaskStatus
import no.vardir.skald.core.model.ThemeName
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.core.sync.PairingTicket
import no.vardir.skald.core.sync.SyncDeviceInfo
import no.vardir.skald.core.sync.SyncStatus
import no.vardir.skald.core.text.Tasks
import no.vardir.skald.data.VaultRepository

enum class Tab { Today, Notes, Threads, Constellation }

/** What the whole app is showing right now. */
data class UiState(
    val tab: Tab = Tab.Today,
    val openNote: NotePayload? = null,
    val loading: Boolean = true,
    val searchOpen: Boolean = false,
    val settingsOpen: Boolean = false,
    val syncPaneOpen: Boolean = false,
    val editingSource: Boolean = false,
    val marginOpen: Boolean = false,
    /** Transient, one-line feedback — the phone equivalent of the status bar. */
    val message: String? = null,
)

class SkaldViewModel(private val repository: VaultRepository) : ViewModel() {

    val snapshot: StateFlow<VaultSnapshot> get() = repository.snapshot
    val syncStatus: StateFlow<SyncStatus> get() = repository.syncStatus

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> get() = _ui.asStateFlow()

    private val _pairingTicket = MutableStateFlow<PairingTicket?>(null)
    val pairingTicket: StateFlow<PairingTicket?> get() = _pairingTicket.asStateFlow()

    private val _devices = MutableStateFlow<List<SyncDeviceInfo>>(emptyList())
    val devices: StateFlow<List<SyncDeviceInfo>> get() = _devices.asStateFlow()

    val today: String get() = repository.today()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            repository.reindex()
            _ui.value = _ui.value.copy(loading = false, marginOpen = false)
        }
    }

    // ---------- navigation ----------

    fun selectTab(tab: Tab) {
        _ui.value = _ui.value.copy(tab = tab, openNote = null, searchOpen = false)
    }

    fun openNote(path: String) = viewModelScope.launch {
        val payload = repository.note(path)
        _ui.value = _ui.value.copy(
            openNote = payload,
            searchOpen = false,
            editingSource = false,
            marginOpen = false,
            message = if (payload == null) "That note is no longer in the vault" else null,
        )
    }

    fun closeNote() {
        _ui.value = _ui.value.copy(openNote = null, editingSource = false, marginOpen = false)
    }

    fun setSearchOpen(open: Boolean) {
        _ui.value = _ui.value.copy(searchOpen = open)
    }

    fun setSettingsOpen(open: Boolean) {
        _ui.value = _ui.value.copy(settingsOpen = open, syncPaneOpen = if (open) _ui.value.syncPaneOpen else false)
    }

    fun setSyncPaneOpen(open: Boolean) {
        _ui.value = _ui.value.copy(syncPaneOpen = open)
        if (open) refreshDevices()
    }

    fun setEditingSource(editing: Boolean) {
        _ui.value = _ui.value.copy(editingSource = editing)
    }

    fun setMarginOpen(open: Boolean) {
        _ui.value = _ui.value.copy(marginOpen = open)
    }

    fun dismissMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    private fun say(message: String) {
        _ui.value = _ui.value.copy(message = message)
    }

    // ---------- notes ----------

    fun saveOpenNote(content: String) = viewModelScope.launch {
        val path = _ui.value.openNote?.meta?.path ?: return@launch
        repository.saveNote(path, content)
        _ui.value = _ui.value.copy(openNote = repository.note(path))
    }

    fun createNote(folder: String, title: String) = viewModelScope.launch {
        val path = repository.createNote(folder, title)
        openNote(path)
    }

    fun deleteOpenNote() = viewModelScope.launch {
        val path = _ui.value.openNote?.meta?.path ?: return@launch
        repository.deleteNote(path)
        _ui.value = _ui.value.copy(openNote = null)
        say("Deleted $path — an earlier version is still in its history")
    }

    fun renameOpenNote(newTitle: String) = viewModelScope.launch {
        val note = _ui.value.openNote ?: return@launch
        val folder = note.meta.path.substringBeforeLast('/', "")
        val safe = no.vardir.skald.core.text.Notes.safeFileName(newTitle)
        if (safe.isEmpty()) return@launch
        val target = if (folder.isEmpty()) "$safe.md" else "$folder/$safe.md"
        val moved = repository.renameNote(note.meta.path, target)
        if (moved == null) {
            say("Could not rename — a note by that name is already there")
        } else {
            openNote(moved)
        }
    }

    fun openToday() = viewModelScope.launch {
        openNote(repository.openDaily())
    }

    fun restoreVersion(id: String) = viewModelScope.launch {
        val path = _ui.value.openNote?.meta?.path ?: return@launch
        repository.restoreVersion(path, id)
        _ui.value = _ui.value.copy(openNote = repository.note(path))
        say("Restored an earlier version")
    }

    suspend fun history(path: String) = repository.history(path)

    // ---------- threads ----------

    fun toggleTask(path: String, line: Int, done: Boolean) = viewModelScope.launch {
        repository.editTask(path, line, Tasks.Edits(status = if (done) TaskStatus.Done else TaskStatus.Open))
        _ui.value.openNote?.let { open ->
            if (open.meta.path == path) _ui.value = _ui.value.copy(openNote = repository.note(path))
        }
    }

    fun setTaskStatus(path: String, line: Int, status: TaskStatus) = viewModelScope.launch {
        repository.editTask(path, line, Tasks.Edits(status = status))
        _ui.value.openNote?.let { open ->
            if (open.meta.path == path) _ui.value = _ui.value.copy(openNote = repository.note(path))
        }
    }

    // ---------- settings ----------

    fun setTheme(theme: ThemeName) = viewModelScope.launch { repository.updateSettings { it.copy(theme = theme) } }

    fun setDensity(density: Density) = viewModelScope.launch { repository.updateSettings { it.copy(density = density) } }

    fun setLogoVariant(variant: LogoVariant) =
        viewModelScope.launch { repository.updateSettings { it.copy(logoVariant = variant) } }

    fun setEditorFontSize(size: Int) =
        viewModelScope.launch { repository.updateSettings { it.copy(editorFontSize = size.coerceIn(13, 22)) } }

    fun setPinnedNote(path: String?) =
        viewModelScope.launch { repository.updateSettings { it.copy(pinnedNote = path) } }

    fun setDailyFolder(folder: String) =
        viewModelScope.launch { repository.updateSettings { it.copy(dailyFolder = folder.trim().ifEmpty { "Daily" }) } }

    fun setAttachmentsFolder(folder: String) =
        viewModelScope.launch {
            repository.updateSettings { it.copy(attachmentsFolder = folder.trim().ifEmpty { "Attachments" }) }
        }

    fun moveStar(path: String, x: Float, y: Float) = repository.moveStar(path, x, y)

    // ---------- sync ----------

    fun connectSync(serverUrl: String, handle: String, provisioningSecret: String) = viewModelScope.launch {
        runCatching { repository.connectSync(serverUrl, handle, provisioningSecret) }
            .onSuccess { say("This vault now owns a sync root — pair another device from here") }
            .onFailure { say(it.message ?: "Could not reach that relay") }
    }

    fun pairWith(pairingUri: String) = viewModelScope.launch {
        runCatching { repository.pairSync(pairingUri) }
            .onSuccess { say("Paired — the vault will follow") }
            .onFailure { say(it.message ?: "That pairing link did not work") }
    }

    fun syncNow() = viewModelScope.launch {
        runCatching { repository.syncNow() }.onFailure { say(it.message ?: "Sync failed") }
    }

    fun republishEverything() = viewModelScope.launch {
        runCatching { repository.republishEverything() }
            .onSuccess { say("Republished the whole vault") }
            .onFailure { say(it.message ?: "Could not republish") }
    }

    fun mintPairing() = viewModelScope.launch {
        runCatching { repository.mintPairing() }
            .onSuccess { _pairingTicket.value = it }
            .onFailure { say(it.message ?: "Could not mint a pairing code") }
    }

    fun clearPairingTicket() {
        _pairingTicket.value = null
    }

    fun refreshDevices() = viewModelScope.launch {
        runCatching { repository.listDevices() }.onSuccess { _devices.value = it }
    }

    fun revokeDevice(deviceId: String) = viewModelScope.launch {
        runCatching { repository.revokeDevice(deviceId) }
            .onSuccess {
                _devices.value = it
                say("Revoked $deviceId")
            }
            .onFailure { say(it.message ?: "Could not revoke that device") }
    }

    fun setSyncEnabled(enabled: Boolean) = repository.setSyncEnabled(enabled)

    fun disconnectSync() {
        repository.disconnectSync()
        _devices.value = emptyList()
        say("Disconnected. Your notes are untouched.")
    }
}
