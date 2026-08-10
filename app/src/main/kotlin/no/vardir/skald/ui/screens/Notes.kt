package no.vardir.skald.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.model.FolderNode
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.ui.components.EmptyState
import no.vardir.skald.ui.components.Rune
import no.vardir.skald.ui.components.SkaldRow
import no.vardir.skald.ui.theme.Skald

/**
 * The explorer. Folders collapse, notes carry their rune, and the count sits
 * where the CSS puts it — right-aligned in the folder header.
 */
@Composable
fun NotesScreen(
    snapshot: VaultSnapshot,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collapsed = remember { mutableStateListOf<String>() }

    if (snapshot.notes.isEmpty()) {
        EmptyState("Nothing written yet", "Tap the button to start the first page of the saga.", modifier)
        return
    }

    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        // The root's loose notes come first, then each folder in turn.
        if (snapshot.tree.notes.isNotEmpty()) {
            item {
                FolderSection(
                    name = snapshot.vaultName,
                    notes = snapshot.tree.notes,
                    snapshot = snapshot,
                    collapsed = false,
                    onToggle = {},
                    onOpenNote = onOpenNote,
                )
            }
        }
        for (folder in flatten(snapshot.tree.folders)) {
            item(key = folder.path) {
                FolderSection(
                    name = folder.path,
                    notes = folder.notes,
                    snapshot = snapshot,
                    collapsed = folder.path in collapsed,
                    onToggle = { if (folder.path in collapsed) collapsed.remove(folder.path) else collapsed.add(folder.path) },
                    onOpenNote = onOpenNote,
                )
            }
        }
        item { Box(Modifier.padding(bottom = 40.dp)) }
    }
}

@Composable
private fun FolderSection(
    name: String,
    notes: List<String>,
    snapshot: VaultSnapshot,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onOpenNote: (String) -> Unit,
) {
    val colors = Skald.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                name.uppercase(),
                style = Skald.type.eyebrow,
                color = colors.tx2,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(notes.size.toString(), style = Skald.type.meta, color = colors.tx4)
        }
        AnimatedVisibility(!collapsed) {
            Column {
                for (path in notes) {
                    val note = snapshot.byPath[path] ?: continue
                    SkaldRow(onClick = { onOpenNote(path) }) {
                        Rune(note.schema)
                        Column(Modifier.weight(1f)) {
                            Text(
                                note.title,
                                style = Skald.type.row,
                                color = colors.tx1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (note.openTaskCount > 0) {
                                Text(
                                    "${note.openTaskCount} open ${if (note.openTaskCount == 1) "thread" else "threads"}",
                                    style = Skald.type.meta,
                                    color = colors.tx3,
                                )
                            }
                        }
                        Text("›", style = Skald.type.meta, color = colors.tx4)
                    }
                }
            }
        }
    }
}

/** Nested folders read as flat sections on a phone; the path carries the depth. */
private fun flatten(folders: List<FolderNode>): List<FolderNode> =
    folders.flatMap { listOf(it) + flatten(it.folders) }.filter { it.notes.isNotEmpty() }
