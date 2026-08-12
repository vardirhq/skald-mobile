package no.vardir.skald.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.model.AttachmentRef
import no.vardir.skald.ui.components.EmptyState
import no.vardir.skald.ui.components.Hairline
import no.vardir.skald.ui.components.IconButtonSlot
import no.vardir.skald.ui.components.Rune
import no.vardir.skald.ui.components.SkaldLogo
import no.vardir.skald.ui.screens.ConstellationScreen
import no.vardir.skald.ui.screens.EditorScreen
import no.vardir.skald.ui.screens.FolderActionsSheet
import no.vardir.skald.ui.screens.ConfirmSheet
import no.vardir.skald.ui.screens.HallHit
import no.vardir.skald.ui.screens.HallSheet
import no.vardir.skald.ui.screens.NewFolderSheet
import no.vardir.skald.ui.screens.NewThreadSheet
import no.vardir.skald.ui.screens.MoveSheet
import no.vardir.skald.ui.screens.NoteActionsSheet
import no.vardir.skald.ui.screens.NotesScreen
import no.vardir.skald.ui.screens.SettingsScreen
import no.vardir.skald.ui.screens.SyncPane
import no.vardir.skald.ui.screens.ThreadSheet
import no.vardir.skald.ui.screens.ThreadTarget
import no.vardir.skald.ui.screens.ThreadsScreen
import no.vardir.skald.ui.screens.TodayScreen
import no.vardir.skald.ui.screens.TrashScreen
import no.vardir.skald.ui.extensions.LocalGitHubService
import no.vardir.skald.ui.theme.Skald
import no.vardir.skald.ui.theme.SkaldTheme

/**
 * The shell. A bottom tab bar replaces the desktop's activity rail and surfaces
 * stack instead of sitting in panes — the same system, one thumb.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkaldShell(
    viewModel: SkaldViewModel,
    onOpenExternal: (String) -> Unit,
    onOpenAttachment: (AttachmentRef) -> Unit,
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val ui by viewModel.ui.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val ticket by viewModel.pairingTicket.collectAsState()
    val githubStatus by viewModel.githubStatus.collectAsState()

    // Back takes the top surface down rather than the app: only a press made with
    // nothing stacked falls through to the system. Surfaces that own a stack of
    // their own — the sync pane, the compose dialog — intercept it first.
    BackHandler(enabled = ui.backStep != null) { viewModel.back() }

    CompositionLocalProvider(LocalGitHubService provides viewModel.github) {
    SkaldTheme(snapshot.settings.theme, snapshot.settings.density) {
        val colors = Skald.colors
        // The sheets. Each one is held as the thing it acts on rather than as a
        // copy of it, so a reindex underneath — a sync landing, a rename — is
        // reflected in the open sheet instead of stranding it on stale data.
        var composing by remember { mutableStateOf<String?>(null) }
        var noteMenu by remember { mutableStateOf<String?>(null) }
        var folderMenu by remember { mutableStateOf<String?>(null) }
        var newFolderUnder by remember { mutableStateOf<String?>(null) }
        var threadMenu by remember { mutableStateOf<String?>(null) }
        var newThread by remember { mutableStateOf(false) }
        var bulkMove by remember { mutableStateOf(false) }
        var bulkDelete by remember { mutableStateOf(false) }
        val imeVisible = WindowInsets.isImeVisible

        val knownTags = remember(snapshot.notes, snapshot.tasks) {
            (snapshot.notes.flatMap { it.tags } + snapshot.tasks.flatMap { it.tags }).distinct().sorted()
        }

        Box(Modifier.fillMaxSize().background(colors.bg2)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .imePadding()
            ) {
                TopBar(
                    title = topTitle(ui, snapshot),
                    subtitle = topSubtitle(ui, snapshot, syncStatus.pending),
                    inNote = ui.openNote != null,
                    inSettings = ui.settingsOpen || ui.trashOpen,
                    logoVariant = snapshot.settings.logoVariant,
                    editorMode = ui.editorMode,
                    onBack = {
                        when {
                            ui.syncPaneOpen -> viewModel.setSyncPaneOpen(false)
                            ui.trashOpen -> viewModel.setTrashOpen(false)
                            ui.settingsOpen -> viewModel.setSettingsOpen(false)
                            else -> viewModel.closeNote()
                        }
                    },
                    onSearch = { viewModel.setSearchOpen(true) },
                    onSettings = { viewModel.setSettingsOpen(true) },
                    onEditorMode = viewModel::setEditorMode,
                )
                Hairline()

                Box(Modifier.weight(1f)) {
                    when {
                        ui.loading -> EmptyState("Reading the vault…", "")

                        ui.syncPaneOpen -> SyncPane(
                            status = syncStatus,
                            devices = devices,
                            ticket = ticket,
                            onPair = viewModel::pairWith,
                            onConnect = viewModel::connectSync,
                            onSyncNow = viewModel::syncNow,
                            onRepublish = viewModel::republishEverything,
                            onMintPairing = viewModel::mintPairing,
                            onClearTicket = viewModel::clearPairingTicket,
                            onRevoke = viewModel::revokeDevice,
                            onSetEnabled = viewModel::setSyncEnabled,
                            onDisconnect = viewModel::disconnectSync,
                        )

                        ui.trashOpen -> TrashScreen(
                            entries = ui.deletedNotes,
                            onRestore = { viewModel.restoreDeleted(it.path, it.versionId) },
                        )

                        ui.settingsOpen -> SettingsScreen(
                            snapshot = snapshot,
                            syncStatus = syncStatus,
                            onTheme = viewModel::setTheme,
                            onDensity = viewModel::setDensity,
                            onLogoVariant = viewModel::setLogoVariant,
                            onEditorFontSize = viewModel::setEditorFontSize,
                            onSchemaTemplate = viewModel::setSchemaTemplate,
                            onOpenTrash = { viewModel.setTrashOpen(true) },
                            onOpenSync = { viewModel.setSyncPaneOpen(true) },
                            githubStatus = githubStatus,
                            onConnectGitHub = { viewModel.connectGitHub(onOpenExternal) },
                            onCancelGitHub = viewModel::cancelGitHubLogin,
                            onDisconnectGitHub = viewModel::disconnectGitHub,
                            onOpenExternal = onOpenExternal,
                        )

                        ui.openNote != null -> EditorScreen(
                            note = ui.openNote!!,
                            snapshot = snapshot,
                            todayIso = viewModel.today,
                            mode = ui.editorMode,
                            onOpenNote = viewModel::openNote,
                            onSave = viewModel::saveOpenNote,
                            onDraftChanged = viewModel::noteDraftChanged,
                            onOpenExternal = onOpenExternal,
                            onOpenAttachment = onOpenAttachment,
                            onNoteMenu = { noteMenu = ui.openNote?.meta?.path },
                        )

                        else -> when (ui.tab) {
                            Tab.Today -> TodayScreen(
                                snapshot = snapshot,
                                todayIso = viewModel.today,
                                onOpenNote = viewModel::openNote,
                                onToggleTask = viewModel::toggleTask,
                                onThreadMenu = { threadMenu = it.id },
                            )

                            Tab.Notes -> NotesScreen(
                                snapshot = snapshot,
                                collapsed = ui.collapsedFolders,
                                onOpenNote = viewModel::openNote,
                                onToggleFolder = viewModel::toggleFolder,
                                onNoteMenu = { noteMenu = it.path },
                                onFolderMenu = { folderMenu = it },
                                onNewFolder = { newFolderUnder = "" },
                                selected = ui.selectedNotes,
                                onToggleSelection = viewModel::toggleNoteSelection,
                                onMoveSelection = { bulkMove = true },
                                onDeleteSelection = { bulkDelete = true },
                                onClearSelection = viewModel::clearNoteSelection,
                            )

                            Tab.Threads -> ThreadsScreen(
                                snapshot = snapshot,
                                todayIso = viewModel.today,
                                onOpenNote = viewModel::openNote,
                                onToggleTask = viewModel::toggleTask,
                                onThreadMenu = { threadMenu = it.id },
                            )

                            Tab.Constellation -> ConstellationScreen(snapshot, viewModel::openNote)
                        }
                    }

                    // The compose button, on the surfaces where writing is the
                    // point. On the threads list it writes a thread instead of a
                    // note — the same gesture for the same intent, aimed at
                    // whatever the surface is actually about.
                    if (ui.openNote == null && !ui.settingsOpen && !ui.trashOpen && !ui.syncPaneOpen && ui.selectedNotes.isEmpty() &&
                        (ui.tab == Tab.Notes || ui.tab == Tab.Today || ui.tab == Tab.Threads)
                    ) {
                        val writingThread = ui.tab == Tab.Threads
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .size(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.accent)
                                .clickable(onClickLabel = if (writingThread) "New thread" else "New note") {
                                    if (writingThread) newThread = true else composing = ""
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (writingThread) "☑" else "+", style = Skald.type.title, color = colors.onAccent)
                        }
                    }
                }

                // With the keyboard up, the bottom of the screen belongs to
                // whatever is being typed into — the editor puts its own bar
                // there, and a tab bar underneath it would only be in the way.
                when {
                    imeVisible -> Unit
                    ui.settingsOpen || ui.trashOpen || ui.syncPaneOpen -> Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                    else -> TabBar(
                        current = ui.tab,
                        inNote = ui.openNote != null,
                        onSelect = viewModel::selectTab,
                        onSearch = { viewModel.setSearchOpen(true) },
                    )
                }
            }

            AnimatedVisibility(
                visible = ui.searchOpen,
                enter = slideInVertically { it / 3 },
                exit = slideOutVertically { it / 3 },
            ) {
                HallSheet(
                    snapshot = snapshot,
                    todayIso = viewModel.today,
                    cantos = cantos(viewModel),
                    onOpenNote = viewModel::openNote,
                    initialQuery = ui.searchQuery,
                    onSaveSearch = viewModel::saveSearch,
                    onRemoveSavedSearch = viewModel::removeSavedSearch,
                    onClose = { viewModel.setSearchOpen(false) },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                )
            }

            composing?.let { startFolder ->
                ComposeSheet(
                    snapshot = snapshot,
                    startFolder = startFolder,
                    onDismiss = { composing = null },
                    onCreate = { folder, title, schema ->
                        composing = null
                        viewModel.createNote(folder, title, schema)
                    },
                    onNewFolder = { newFolderUnder = startFolder },
                )
            }

            // A long press anywhere a note is listed, and the same menu from the
            // editor's own title — one place that knows what can be done with a
            // note, rather than a rename hidden in one surface and a delete in
            // another.
            noteMenu?.let { path ->
                // Looked up rather than captured: a sheet left open across a
                // reindex should show what the vault says now, and one whose
                // note is gone should simply not draw.
                snapshot.byPath[path]?.let { note ->
                    NoteActionsSheet(
                        note = note,
                        snapshot = snapshot,
                        onOpen = { viewModel.openNote(path) },
                        onRename = { viewModel.renameNote(path, it) },
                        onMove = { viewModel.moveNote(path, it) },
                        onDuplicate = { viewModel.duplicateNote(path) },
                        onSetPinned = { viewModel.setPinnedNote(if (it) path else null) },
                        onEditFrontmatter = { changes, remove -> viewModel.editFrontmatter(path, changes, remove) },
                        onDelete = { viewModel.deleteNote(path) },
                        onDismiss = { noteMenu = null },
                    )
                }
            }

            folderMenu?.let { path ->
                FolderActionsSheet(
                    path = path,
                    noteCount = snapshot.notes.count { it.path.startsWith("$path/") },
                    onNewNote = { composing = path },
                    onNewSubfolder = { newFolderUnder = path },
                    onRename = { viewModel.renameFolder(path, it) },
                    onDelete = { viewModel.deleteFolder(path) },
                    onDismiss = { folderMenu = null },
                )
            }

            newFolderUnder?.let { parent ->
                NewFolderSheet(
                    snapshot = snapshot,
                    parent = parent,
                    onConfirm = {
                        newFolderUnder = null
                        viewModel.createFolder(it)
                    },
                    onDismiss = { newFolderUnder = null },
                )
            }

            // The thread sheet is looked up by id every time it draws, so ticking
            // a box or renaming its note underneath does not strand it.
            threadMenu?.let { id ->
                snapshot.tasks.firstOrNull { it.id == id }?.let { task ->
                    ThreadSheet(
                        target = ThreadTarget(
                            notePath = task.notePath,
                            noteTitle = task.noteTitle,
                            line = task.line,
                            content = task.content,
                            status = task.status,
                            priority = task.priority,
                            due = task.due,
                            tags = task.tags,
                        ),
                        knownTags = knownTags,
                        todayIso = viewModel.today,
                        onApply = { viewModel.editThread(task.notePath, task.line, it) },
                        onOpenNote = { viewModel.openNote(task.notePath) },
                        onDismiss = { threadMenu = null },
                    )
                }
            }

            if (newThread) {
                NewThreadSheet(
                    snapshot = snapshot,
                    todayIso = viewModel.today,
                    knownTags = knownTags,
                    onCreate = { path, content, due, priority, tags ->
                        viewModel.createThread(path, content, due, priority, tags)
                    },
                    onDismiss = { newThread = false },
                )
            }

            if (bulkMove && ui.selectedNotes.isNotEmpty()) {
                MoveSheet(
                    title = "${ui.selectedNotes.size} selected notes",
                    current = "__selection__",
                    snapshot = snapshot,
                    onConfirm = {
                        bulkMove = false
                        viewModel.moveSelectedNotes(it)
                    },
                    onDismiss = { bulkMove = false },
                )
            }

            if (bulkDelete && ui.selectedNotes.isNotEmpty()) {
                ConfirmSheet(
                    title = "Delete ${ui.selectedNotes.size} notes?",
                    subtitle = "Recently deleted",
                    body = "The Markdown files will be removed, but every note can be restored from its local deletion history.",
                    confirm = "Delete",
                    onConfirm = {
                        bulkDelete = false
                        viewModel.deleteSelectedNotes()
                    },
                    onDismiss = { bulkDelete = false },
                )
            }

            ui.message?.let { message ->
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp, start = 18.dp, end = 18.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bgPop)
                        .clickable { viewModel.dismissMessage() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(message, style = Skald.type.small, color = colors.tx1)
                }
            }
        }
    }
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    inNote: Boolean,
    inSettings: Boolean,
    logoVariant: no.vardir.skald.core.model.LogoVariant,
    editorMode: EditorMode,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onEditorMode: (EditorMode) -> Unit,
) {
    val colors = Skald.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inNote || inSettings) {
            IconButtonSlot(onBack, contentDescription = "Back") {
                Text("‹", style = Skald.type.title, color = colors.accent)
            }
        } else {
            Box(Modifier.padding(start = 6.dp, end = 4.dp)) { SkaldLogo(logoVariant, 20.dp) }
        }

        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                title,
                style = Skald.type.screenTitle,
                color = colors.tx0,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = Skald.type.meta,
                    color = colors.tx3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (inNote) {
            ModeToggle(editorMode, onEditorMode)
        }
        if (!inSettings) {
            IconButtonSlot(onSearch, contentDescription = "Search") {
                Text("⌕", style = Skald.type.title, color = colors.tx2)
            }
        }
        if (!inNote && !inSettings) {
            IconButtonSlot(onSettings, contentDescription = "Settings") {
                Text("⋯", style = Skald.type.title, color = colors.tx2)
            }
        }
    }
}

/**
 * Live, read, source — the desktop's three, in the width a phone's top bar can
 * spare. Small enough to sit beside the title, and still a 44dp target each.
 */
@Composable
private fun ModeToggle(current: EditorMode, onSelect: (EditorMode) -> Unit) {
    val colors = Skald.colors
    Row(
        Modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(colors.bg1)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((mode, label) in MODES) {
            val active = mode == current
            Box(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (active) colors.bg3 else Color.Transparent)
                    .clickable(onClickLabel = "$label view") { onSelect(mode) }
                    .padding(horizontal = 8.dp, vertical = 9.dp),
            ) {
                Text(
                    label,
                    style = Skald.type.metaSmall,
                    color = if (active) colors.accent else colors.tx3,
                )
            }
        }
    }
}

private val MODES = listOf(
    EditorMode.Live to "live",
    EditorMode.Read to "read",
    EditorMode.Source to "src",
)

@Composable
private fun TabBar(current: Tab, inNote: Boolean, onSelect: (Tab) -> Unit, onSearch: () -> Unit) {
    val colors = Skald.colors
    Column {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.bg1)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (tab in Tab.entries) {
                TabItem(
                    label = tab.label(),
                    schema = tab.schema(),
                    active = !inNote && current == tab,
                    modifier = Modifier.weight(1f),
                ) { onSelect(tab) }
            }
            TabItem(label = "Hall", schema = null, active = false, modifier = Modifier.weight(1f), onClick = onSearch)
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    schema: no.vardir.skald.core.model.SchemaName?,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = Skald.colors
    val tint = if (active) colors.accent else colors.tx3
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (schema != null) {
            Rune(schema, 22.dp, tint)
        } else {
            Text("⌕", style = Skald.type.heading, color = tint)
        }
        Text(label, style = Skald.type.metaSmall, color = tint)
    }
}

/**
 * The tabs wear runes rather than generic icons, so the bar speaks the same
 * visual language as the rest of the vault.
 */
private fun Tab.label(): String = when (this) {
    Tab.Today -> "Today"
    Tab.Notes -> "Notes"
    Tab.Threads -> "Threads"
    Tab.Constellation -> "Map"
}

private fun Tab.schema(): no.vardir.skald.core.model.SchemaName = when (this) {
    Tab.Today -> no.vardir.skald.core.model.SchemaName.Daily
    Tab.Notes -> no.vardir.skald.core.model.SchemaName.Note
    Tab.Threads -> no.vardir.skald.core.model.SchemaName.Code
    Tab.Constellation -> no.vardir.skald.core.model.SchemaName.Idea
}

private fun topTitle(ui: UiState, snapshot: no.vardir.skald.core.model.VaultSnapshot): String = when {
    ui.syncPaneOpen -> "Sync"
    ui.trashOpen -> "Recently deleted"
    ui.settingsOpen -> "Settings"
    ui.openNote != null -> ui.openNote.meta.title
    else -> when (ui.tab) {
        Tab.Today -> "Today"
        Tab.Notes -> "Notes"
        Tab.Threads -> "Threads"
        Tab.Constellation -> "Constellation"
    }
}

private fun topSubtitle(
    ui: UiState,
    snapshot: no.vardir.skald.core.model.VaultSnapshot,
    pending: Int,
): String = when {
    ui.syncPaneOpen -> if (pending > 0) "$pending waiting to publish" else ""
    ui.trashOpen -> "${ui.deletedNotes.size} recoverable"
    ui.settingsOpen -> snapshot.vaultName
    ui.openNote != null -> ui.openNote.meta.path
    else -> when (ui.tab) {
        Tab.Today -> "${snapshot.stats.notes} notes · ${snapshot.stats.tasksOpen} open"
        Tab.Notes -> "${snapshot.stats.notes} notes · ${snapshot.stats.folders} folders"
        Tab.Threads -> "${snapshot.stats.tasksOpen} open · ${snapshot.stats.tasksTotal} total"
        Tab.Constellation -> "${snapshot.graph.nodes.size} stars · ${snapshot.graph.edges.size} edges"
    }
}

private fun cantos(viewModel: SkaldViewModel): List<HallHit.Canto> = listOf(
    HallHit.Canto("Go to today's page") { viewModel.openToday() },
    HallHit.Canto("Open the constellation") { viewModel.selectTab(Tab.Constellation) },
    HallHit.Canto("Open threads") { viewModel.selectTab(Tab.Threads) },
    HallHit.Canto("Sync now") { viewModel.syncNow() },
    HallHit.Canto("Open settings") { viewModel.setSettingsOpen(true) },
    HallHit.Canto("Open recently deleted") { viewModel.setTrashOpen(true) },
    HallHit.Canto("Open sync") {
        viewModel.setSettingsOpen(true)
        viewModel.setSyncPaneOpen(true)
    },
)
