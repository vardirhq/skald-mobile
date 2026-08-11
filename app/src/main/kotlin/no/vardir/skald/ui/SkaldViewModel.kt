package no.vardir.skald.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.vardir.skald.core.model.Density
import no.vardir.skald.core.model.DeletedNoteEntry
import no.vardir.skald.core.model.LogoVariant
import no.vardir.skald.core.model.NotePayload
import no.vardir.skald.core.model.TaskPriority
import no.vardir.skald.core.model.TaskStatus
import no.vardir.skald.core.model.ThemeName
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.core.sync.PairingTicket
import no.vardir.skald.core.sync.SyncDeviceInfo
import no.vardir.skald.core.sync.SyncStatus
import no.vardir.skald.core.text.Notes
import no.vardir.skald.core.text.Tasks
import no.vardir.skald.data.VaultRepository

enum class Tab { Today, Notes, Threads, Constellation }

/**
 * The three readings of a note, as the desktop has them: writing in the rendered
 * page, reading it, and the file itself.
 */
enum class EditorMode { Live, Read, Source }

/** A surface the system back button takes down, one press at a time. */
enum class BackStep { Search, Trash, SyncPane, Settings, Note, Selection, HomeTab }

/** What the whole app is showing right now. */
data class UiState(
    val tab: Tab = Tab.Today,
    val openNote: NotePayload? = null,
    val loading: Boolean = true,
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    val trashOpen: Boolean = false,
    val deletedNotes: List<DeletedNoteEntry> = emptyList(),
    val settingsOpen: Boolean = false,
    val syncPaneOpen: Boolean = false,
    val editorMode: EditorMode = EditorMode.Live,
    val marginOpen: Boolean = false,
    /** Folders shut in the explorer, kept here so a tab change does not reopen them. */
    val collapsedFolders: Set<String> = emptySet(),
    /** Long-press selection in the explorer. */
    val selectedNotes: Set<String> = emptySet(),
    /** Transient, one-line feedback — the phone equivalent of the status bar. */
    val message: String? = null,
) {
    /**
     * What one back press should undo, or null when the shell is at rest and the
     * press belongs to the system — which is the only way out of the app.
     *
     * The order is the order the surfaces sit in on the screen: the same
     * precedence the shell renders them in, with the Hall on top of all of it.
     * A tab other than Today counts as a step, so the last press before leaving
     * is always made from the home surface rather than wherever you wandered to.
     */
    val backStep: BackStep? get() = when {
        searchOpen -> BackStep.Search
        trashOpen -> BackStep.Trash
        syncPaneOpen -> BackStep.SyncPane
        settingsOpen -> BackStep.Settings
        openNote != null -> BackStep.Note
        selectedNotes.isNotEmpty() -> BackStep.Selection
        tab != Tab.Today -> BackStep.HomeTab
        else -> null
    }
}

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
            repository.reindex()
            _ui.value = _ui.value.copy(loading = false, marginOpen = false)
        }
    }

    // ---------- navigation ----------

    fun selectTab(tab: Tab) {
        _ui.value = _ui.value.copy(tab = tab, openNote = null, searchOpen = false, selectedNotes = emptySet())
    }

    fun openNote(path: String) = viewModelScope.launch {
        // Leaving a note with an autosave still pending used to lose whatever
        // had been typed in the last second of it. Following a link is exactly
        // when that happens, so the draft lands before the next note is read.
        landPendingDraft()
        val payload = repository.note(path)
        _ui.value = _ui.value.copy(
            openNote = payload,
            searchOpen = false,
            editorMode = EditorMode.Live,
            marginOpen = false,
            message = if (payload == null) "That note is no longer in the vault" else null,
        )
    }

    fun closeNote() {
        _ui.value = _ui.value.copy(openNote = null, editorMode = EditorMode.Live, marginOpen = false)
        viewModelScope.launch { landPendingDraft() }
    }

    /**
     * The system back button, routed through the same calls the chrome's own back
     * affordances make, so the two never disagree about where back leads.
     */
    fun back() {
        when (_ui.value.backStep) {
            BackStep.Search -> setSearchOpen(false)
            BackStep.Trash -> setTrashOpen(false)
            BackStep.SyncPane -> setSyncPaneOpen(false)
            BackStep.Settings -> setSettingsOpen(false)
            BackStep.Note -> closeNote()
            BackStep.Selection -> clearNoteSelection()
            BackStep.HomeTab -> selectTab(Tab.Today)
            null -> Unit
        }
    }

    fun setSearchOpen(open: Boolean, query: String? = null) {
        _ui.value = _ui.value.copy(searchOpen = open, searchQuery = query ?: _ui.value.searchQuery)
    }

    fun setTrashOpen(open: Boolean) {
        _ui.value = _ui.value.copy(trashOpen = open)
        if (open) viewModelScope.launch {
            _ui.value = _ui.value.copy(deletedNotes = repository.deletedNotes())
        }
    }

    fun setSettingsOpen(open: Boolean) {
        _ui.value = _ui.value.copy(settingsOpen = open, syncPaneOpen = if (open) _ui.value.syncPaneOpen else false)
    }

    fun setSyncPaneOpen(open: Boolean) {
        _ui.value = _ui.value.copy(syncPaneOpen = open)
        if (open) refreshDevices()
    }

    fun setEditorMode(mode: EditorMode) {
        _ui.value = _ui.value.copy(editorMode = mode)
    }

    fun toggleFolder(path: String) {
        val shut = _ui.value.collapsedFolders
        _ui.value = _ui.value.copy(collapsedFolders = if (path in shut) shut - path else shut + path)
    }

    fun toggleNoteSelection(path: String) {
        val selected = _ui.value.selectedNotes
        _ui.value = _ui.value.copy(selectedNotes = if (path in selected) selected - path else selected + path)
    }

    fun clearNoteSelection() {
        _ui.value = _ui.value.copy(selectedNotes = emptySet())
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
        pendingDraft = null
        _ui.value = _ui.value.copy(openNote = repository.note(path))
    }

    fun createNote(folder: String, title: String, schema: String? = null) = viewModelScope.launch {
        val path = repository.createNote(folder, title, schema)
        openNote(path)
    }

    fun moveSelectedNotes(folder: String) = viewModelScope.launch {
        val selected = _ui.value.selectedNotes
        if (selected.isEmpty()) return@launch
        val moved = repository.moveNotes(selected, folder)
        if (moved == null) say("Could not move the selection — check for duplicate names")
        else {
            clearNoteSelection()
            say("Moved ${moved.size} ${if (moved.size == 1) "note" else "notes"}")
        }
    }

    fun deleteSelectedNotes() = viewModelScope.launch {
        val selected = _ui.value.selectedNotes
        if (selected.isEmpty()) return@launch
        selected.forEach { flushDraft(it) }
        val deleted = repository.deleteNotes(selected)
        clearNoteSelection()
        say("Deleted $deleted ${if (deleted == 1) "note" else "notes"} — all can be restored")
    }

    /**
     * Everything below acts on a path rather than on whatever happens to be
     * open, so the notes list can offer the same operations from a long press
     * as the editor does from its own chrome.
     */
    fun deleteNote(path: String) = viewModelScope.launch {
        // A note being written has an unsaved draft; save it first so what lands
        // in the history is what was on the screen.
        flushDraft(path)
        repository.deleteNote(path)
        if (_ui.value.openNote?.meta?.path == path) _ui.value = _ui.value.copy(openNote = null)
        say("Deleted ${Notes.titleFromPath(path)} — an earlier version is kept in its history")
    }

    fun deleteOpenNote() {
        _ui.value.openNote?.let { deleteNote(it.meta.path) }
    }

    /** Rename the *file*, which is what a wikilink actually points at. */
    fun renameNote(path: String, newName: String) = viewModelScope.launch {
        val safe = Notes.safeFileName(newName)
        if (safe.isEmpty()) {
            say("A note needs a name")
            return@launch
        }
        val folder = Notes.parentFolder(path)
        val target = if (folder.isEmpty()) "$safe.md" else "$folder/$safe.md"
        if (target == path) return@launch
        flushDraft(path)
        val moved = repository.renameNote(path, target)
        if (moved == null) {
            say("Could not rename — a note called $safe is already there")
        } else {
            follow(path, moved)
            say("Renamed to $safe — every link that pointed here followed")
        }
    }

    fun moveNote(path: String, folder: String) = viewModelScope.launch {
        flushDraft(path)
        val moved = repository.moveNote(path, folder)
        if (moved == null) {
            say("Could not move — a note by that name is already in ${folder.ifEmpty { "the vault root" }}")
        } else {
            follow(path, moved)
            say("Moved to ${folder.ifEmpty { "the vault root" }}")
        }
    }

    fun duplicateNote(path: String) = viewModelScope.launch {
        val copy = repository.duplicateNote(path)
        if (copy == null) say("Could not copy that note") else openNote(copy)
    }

    /** Set one frontmatter field — the schema picker, the tag row, the title. */
    fun editFrontmatter(path: String, changes: Map<String, Any?> = emptyMap(), remove: Set<String> = emptySet()) =
        viewModelScope.launch {
            repository.editFrontmatter(path, changes, remove)
            if (_ui.value.openNote?.meta?.path == path) {
                _ui.value = _ui.value.copy(openNote = repository.note(path))
            }
        }

    /**
     * Save the open note's pending draft before something structural happens to
     * it. Renames, moves and deletes all read the file from disk, and an
     * autosave that had not landed yet would be read as never having been typed.
     */
    private suspend fun flushDraft(path: String) {
        val pending = pendingDraft
        if (pending != null && pending.first == path) {
            repository.saveNote(path, pending.second)
            pendingDraft = null
        }
    }

    /** The same, for whatever note is holding one, on the way out of it. */
    private suspend fun landPendingDraft() {
        val pending = pendingDraft ?: return
        pendingDraft = null
        repository.saveNote(pending.first, pending.second)
    }

    /** Follow a note that moved, so the editor does not sit on a path that is gone. */
    private fun follow(from: String, to: String) {
        if (_ui.value.openNote?.meta?.path == from) openNote(to)
    }

    /**
     * The editor's unsaved text, parked here so the operations above can land it
     * before they touch the file. The editor still owns the autosave itself.
     */
    private var pendingDraft: Pair<String, String>? = null

    fun noteDraftChanged(path: String, content: String?) {
        pendingDraft = if (content == null) null else path to content
    }

    // ---------- folders ----------

    fun createFolder(path: String) = viewModelScope.launch {
        val clean = path.trim().trim('/').replace(Regex("""\s*/\s*"""), "/")
        if (clean.isEmpty()) return@launch
        if (repository.createFolder(clean)) say("Made $clean") else say("Could not make that folder")
    }

    fun renameFolder(path: String, newName: String) = viewModelScope.launch {
        val safe = Notes.safeFileName(newName)
        if (safe.isEmpty()) return@launch
        val parent = Notes.parentFolder(path)
        val target = if (parent.isEmpty()) safe else "$parent/$safe"
        if (target == path) return@launch
        val moved = repository.renameFolder(path, target)
        if (moved == null) {
            say("Could not rename that folder")
        } else {
            say("Renamed to $safe — the notes in it took their links along")
        }
    }

    fun deleteFolder(path: String) = viewModelScope.launch {
        if (repository.deleteFolder(path)) {
            say("Removed $path")
        } else {
            say("That folder still holds notes — move them out first")
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

    fun restoreDeleted(path: String, id: String) = viewModelScope.launch {
        if (repository.restoreDeleted(path, id)) {
            _ui.value = _ui.value.copy(deletedNotes = repository.deletedNotes())
            say("Restored ${Notes.titleFromPath(path)}")
        } else {
            say("Could not restore — a note already exists at that path")
        }
    }

    suspend fun history(path: String) = repository.history(path)

    // ---------- threads ----------

    fun toggleTask(path: String, line: Int, done: Boolean) = viewModelScope.launch {
        repository.editTask(path, line, Tasks.Edits(status = if (done) TaskStatus.Done else TaskStatus.Open))
        _ui.value.openNote?.let { open ->
            if (open.meta.path == path) _ui.value = _ui.value.copy(openNote = repository.note(path))
        }
    }

    fun setTaskStatus(path: String, line: Int, status: TaskStatus) = editThread(path, line, Tasks.Edits(status = status))

    /**
     * Everything a thread carries, edited at once from the thread sheet — which
     * is the whole point of it. `@due(2026-06-01) @p(high) @status(working)` is
     * a syntax nobody is going to thumb correctly, or remember exists.
     */
    /** A thread written down from the list, rather than from inside a note. */
    fun createThread(
        notePath: String?,
        content: String,
        due: String?,
        priority: TaskPriority,
        tags: List<String>,
    ) = viewModelScope.launch {
        if (content.isBlank()) return@launch
        val line = Tasks.formatLine(
            content = content.trim(),
            status = TaskStatus.Open,
            due = due,
            priority = priority,
            tags = tags,
        )
        val target = repository.addTask(notePath, line)
        say("Written into ${Notes.titleFromPath(target)}")
    }

    fun editThread(path: String, line: Int, edits: Tasks.Edits) = viewModelScope.launch {
        repository.editTask(path, line, edits)
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

    fun saveSearch(query: String) = viewModelScope.launch {
        val clean = query.trim()
        if (clean.isEmpty()) return@launch
        repository.updateSettings { settings ->
            if (settings.savedSearches.any { it.query == clean }) settings else settings.copy(
                savedSearches = settings.savedSearches + no.vardir.skald.core.model.SavedSearch(
                    id = System.currentTimeMillis().toString(36),
                    name = clean,
                    query = clean,
                )
            )
        }
        say("Search saved")
    }

    fun removeSavedSearch(id: String) = viewModelScope.launch {
        repository.updateSettings { it.copy(savedSearches = it.savedSearches.filterNot { search -> search.id == id }) }
    }

    fun setSchemaTemplate(schema: no.vardir.skald.core.model.SchemaName, template: String) = viewModelScope.launch {
        repository.updateSettings { settings ->
            settings.copy(schemaTemplates = settings.schemaTemplates.toMutableMap().apply {
                if (template.isBlank()) remove(schema.name) else put(schema.name, template)
            })
        }
        say("${schema.name} template saved")
    }

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
