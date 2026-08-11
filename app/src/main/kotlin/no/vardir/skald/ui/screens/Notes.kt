package no.vardir.skald.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.model.FolderNode
import no.vardir.skald.core.model.NoteMeta
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.core.text.Wikilinks
import no.vardir.skald.ui.components.EmptyState
import no.vardir.skald.ui.components.Hairline
import no.vardir.skald.ui.components.Rune
import no.vardir.skald.ui.theme.Skald

/**
 * The explorer.
 *
 * Folders nest here rather than being flattened into headings: a vault with
 * `Projects/Sagas/Winter` in it has three levels of meaning, and reading them
 * as three sibling sections loses the one thing a tree is for. Empty folders
 * show too — you have to be able to see the shelf before you put anything on it.
 *
 * A press opens; a long press asks what else. That is the whole of the desktop's
 * right-click menu, in the one gesture a thumb has spare.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    snapshot: VaultSnapshot,
    collapsed: Set<String>,
    onOpenNote: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onNoteMenu: (NoteMeta) -> Unit,
    onFolderMenu: (String) -> Unit,
    onNewFolder: () -> Unit,
    selected: Set<String> = emptySet(),
    onToggleSelection: (String) -> Unit = {},
    onMoveSelection: () -> Unit = {},
    onDeleteSelection: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    val rows = remember(snapshot.tree, collapsed) { flatten(snapshot.tree, collapsed) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val selectedLinks = remember(snapshot.notes, selected) {
        val index = Wikilinks.buildIndex(snapshot.notes.map { Wikilinks.Linkable(it.path, it.title) })
        selected.sorted().joinToString("\n") { "[[${Wikilinks.shortestTarget(it, index)}]]" }
    }

    Column(modifier.fillMaxWidth()) {
        if (selected.isNotEmpty()) SelectionBar(
            count = selected.size,
            onMove = onMoveSelection,
            onDelete = onDeleteSelection,
            onCopy = { clipboard.setText(AnnotatedString(selectedLinks)) },
            onShare = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, selectedLinks)
                }, "Share note links"))
            },
            onClear = onClearSelection,
        ) else Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${snapshot.stats.notes} notes · ${snapshot.stats.folders} folders",
                style = Skald.type.eyebrow,
                color = colors.tx3,
                modifier = Modifier.weight(1f),
            )
            Text(
                "＋ Folder",
                style = Skald.type.metaSmall,
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClickLabel = "New folder", onClick = onNewFolder)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }
        Hairline()

        if (snapshot.notes.isEmpty() && rows.isEmpty()) {
            EmptyState("Nothing written yet", "Tap the button to start the first page of the saga.")
            return@Column
        }

        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is TreeRow.Folder -> FolderHeader(
                        node = row.node,
                        depth = row.depth,
                        collapsed = row.collapsed,
                        onToggle = { onToggleFolder(row.node.path) },
                        onMenu = { onFolderMenu(row.node.path) },
                    )

                    is TreeRow.Note -> {
                        val note = snapshot.byPath[row.path]
                        if (note != null) {
                            NoteRow(
                                note = note,
                                depth = row.depth,
                                selected = note.path in selected,
                                selectionActive = selected.isNotEmpty(),
                                onOpen = { if (selected.isEmpty()) onOpenNote(note.path) else onToggleSelection(note.path) },
                                onMenu = { if (selected.isEmpty()) onToggleSelection(note.path) else onToggleSelection(note.path) },
                                onActions = { onNoteMenu(note) },
                            )
                        }
                    }
                }
            }
            item { Box(Modifier.padding(bottom = 88.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderHeader(
    node: FolderNode,
    depth: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = Skald.colors
    val held = node.allNotes().size
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onLongClick = onMenu, onClickLabel = "Open folder", onClick = onToggle)
            .padding(start = (6 + depth * 15).dp, end = 8.dp, top = 9.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (collapsed) "▸" else "▾", style = Skald.type.metaSmall, color = colors.tx3)
        Text(
            node.name.uppercase(),
            style = Skald.type.eyebrow,
            color = colors.tx2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (held == 0) {
            Text("empty", style = Skald.type.metaSmall, color = colors.tx4)
        } else {
            Text(held.toString(), style = Skald.type.meta, color = colors.tx4)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: NoteMeta,
    depth: Int,
    selected: Boolean,
    selectionActive: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onActions: () -> Unit,
) {
    val colors = Skald.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) colors.accentGhost else androidx.compose.ui.graphics.Color.Transparent)
                .combinedClickable(onLongClick = onMenu, onClickLabel = "Open note", onClick = onOpen)
                .padding(start = (10 + depth * 15).dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Rune(note.schema)
            Column(Modifier.weight(1f)) {
                Text(
                    note.title,
                    style = Skald.type.row,
                    color = colors.tx1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = noteMeta(note)
                if (meta.isNotEmpty()) Text(meta, style = Skald.type.meta, color = colors.tx3, maxLines = 1)
            }
            if (selectionActive) {
                Text(if (selected) "✓" else "○", style = Skald.type.row, color = if (selected) colors.accent else colors.tx4)
            } else {
                Text("⋯", style = Skald.type.row, color = colors.tx4, modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "What can be done with this note", onClick = onActions)
                    .padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Box(Modifier.padding(start = (10 + depth * 15).dp)) { Hairline() }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = Skald.colors
    Column(Modifier.fillMaxWidth().background(colors.bg1).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$count selected", style = Skald.type.row, color = colors.tx0, modifier = Modifier.weight(1f))
            Text(
                "Done",
                style = Skald.type.metaSmall,
                color = colors.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClear).padding(10.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((label, action) in listOf("Move" to onMove, "Copy links" to onCopy, "Share" to onShare, "Delete" to onDelete)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(colors.bg2)
                        .clickable(onClick = action).padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = Skald.type.metaSmall, color = if (label == "Delete") colors.err else colors.accent)
                }
            }
        }
    }
    Hairline()
}

private fun noteMeta(note: NoteMeta): String = buildList {
    if (note.openTaskCount > 0) add("${note.openTaskCount} open ${if (note.openTaskCount == 1) "thread" else "threads"}")
    if (note.tags.isNotEmpty()) add(note.tags.take(2).joinToString(" ") { "#$it" })
}.joinToString(" · ")

/** One line of the tree as the list draws it. */
private sealed interface TreeRow {
    val key: String

    data class Folder(val node: FolderNode, val depth: Int, val collapsed: Boolean) : TreeRow {
        override val key: String get() = "d:${node.path}"
    }

    data class Note(val path: String, val depth: Int) : TreeRow {
        override val key: String get() = "n:$path"
    }
}

/**
 * The tree as a flat list of visible rows. A collapsed folder keeps its header
 * and hides everything under it, children included — which is what makes the
 * chevron worth having on a deep vault.
 */
private fun flatten(tree: FolderNode, collapsed: Set<String>): List<TreeRow> {
    val out = mutableListOf<TreeRow>()

    fun walk(node: FolderNode, depth: Int) {
        for (path in node.notes) out += TreeRow.Note(path, depth)
        for (child in node.folders) {
            val shut = child.path in collapsed
            out += TreeRow.Folder(child, depth, shut)
            if (!shut) walk(child, depth + 1)
        }
    }

    walk(tree, 0)
    return out
}
