package no.vardir.skald.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import no.vardir.skald.ui.screens.HallHit
import no.vardir.skald.ui.screens.HallSheet
import no.vardir.skald.ui.screens.NotesScreen
import no.vardir.skald.ui.screens.SettingsScreen
import no.vardir.skald.ui.screens.SyncPane
import no.vardir.skald.ui.screens.ThreadsScreen
import no.vardir.skald.ui.screens.TodayScreen
import no.vardir.skald.ui.theme.Skald
import no.vardir.skald.ui.theme.SkaldTheme

/**
 * The shell. A bottom tab bar replaces the desktop's activity rail and surfaces
 * stack instead of sitting in panes — the same system, one thumb.
 */
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

    SkaldTheme(snapshot.settings.theme, snapshot.settings.density) {
        val colors = Skald.colors
        var composing by remember { mutableStateOf(false) }

        Box(Modifier.fillMaxSize().background(colors.bg2)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                TopBar(
                    title = topTitle(ui, snapshot),
                    subtitle = topSubtitle(ui, snapshot, syncStatus.pending),
                    inNote = ui.openNote != null,
                    inSettings = ui.settingsOpen,
                    logoVariant = snapshot.settings.logoVariant,
                    sourceMode = ui.editingSource,
                    onBack = {
                        when {
                            ui.syncPaneOpen -> viewModel.setSyncPaneOpen(false)
                            ui.settingsOpen -> viewModel.setSettingsOpen(false)
                            else -> viewModel.closeNote()
                        }
                    },
                    onSearch = { viewModel.setSearchOpen(true) },
                    onSettings = { viewModel.setSettingsOpen(true) },
                    onToggleSource = { viewModel.setEditingSource(!ui.editingSource) },
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

                        ui.settingsOpen -> SettingsScreen(
                            snapshot = snapshot,
                            syncStatus = syncStatus,
                            onTheme = viewModel::setTheme,
                            onDensity = viewModel::setDensity,
                            onLogoVariant = viewModel::setLogoVariant,
                            onEditorFontSize = viewModel::setEditorFontSize,
                            onOpenSync = { viewModel.setSyncPaneOpen(true) },
                        )

                        ui.openNote != null -> EditorScreen(
                            note = ui.openNote!!,
                            snapshot = snapshot,
                            todayIso = viewModel.today,
                            editingSource = ui.editingSource,
                            onOpenNote = viewModel::openNote,
                            onToggleTask = viewModel::toggleTask,
                            onSave = viewModel::saveOpenNote,
                            onOpenExternal = onOpenExternal,
                            onOpenAttachment = onOpenAttachment,
                        )

                        else -> when (ui.tab) {
                            Tab.Today -> TodayScreen(
                                snapshot = snapshot,
                                todayIso = viewModel.today,
                                onOpenNote = viewModel::openNote,
                                onToggleTask = viewModel::toggleTask,
                            )

                            Tab.Notes -> NotesScreen(snapshot, viewModel::openNote)

                            Tab.Threads -> ThreadsScreen(
                                snapshot = snapshot,
                                todayIso = viewModel.today,
                                onOpenNote = viewModel::openNote,
                                onToggleTask = viewModel::toggleTask,
                            )

                            Tab.Constellation -> ConstellationScreen(snapshot, viewModel::openNote)
                        }
                    }

                    // The compose FAB, on the surfaces where writing is the point.
                    if (ui.openNote == null && !ui.settingsOpen && !ui.syncPaneOpen &&
                        (ui.tab == Tab.Notes || ui.tab == Tab.Today)
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .size(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.accent)
                                .clickable(onClickLabel = "New note") { composing = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+", style = Skald.type.title, color = colors.onAccent)
                        }
                    }
                }

                if (!ui.settingsOpen && !ui.syncPaneOpen) {
                    TabBar(
                        current = ui.tab,
                        inNote = ui.openNote != null,
                        onSelect = viewModel::selectTab,
                        onSearch = { viewModel.setSearchOpen(true) },
                    )
                } else {
                    Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
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
                    onClose = { viewModel.setSearchOpen(false) },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                )
            }

            if (composing) {
                ComposeDialog(
                    folders = snapshot.tree.folders.map { it.path },
                    onDismiss = { composing = false },
                    onCreate = { folder, title ->
                        composing = false
                        viewModel.createNote(folder, title)
                    },
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

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    inNote: Boolean,
    inSettings: Boolean,
    logoVariant: no.vardir.skald.core.model.LogoVariant,
    sourceMode: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onToggleSource: () -> Unit,
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
            IconButtonSlot(onToggleSource, contentDescription = "Toggle source view") {
                Text(
                    if (sourceMode) "aA" else "{ }",
                    style = Skald.type.meta,
                    color = if (sourceMode) colors.accent else colors.tx2,
                )
            }
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
    HallHit.Canto("Open sync") {
        viewModel.setSettingsOpen(true)
        viewModel.setSyncPaneOpen(true)
    },
)
