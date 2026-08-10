package no.vardir.skald.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.model.NoteMeta
import no.vardir.skald.core.model.SchemaName
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.core.text.Frontmatter
import no.vardir.skald.core.text.Notes
import no.vardir.skald.core.text.Wikilinks
import no.vardir.skald.ui.components.FieldLabel
import no.vardir.skald.ui.components.FolderPicker
import no.vardir.skald.ui.components.Hairline
import no.vardir.skald.ui.components.SchemaPicker
import no.vardir.skald.ui.components.SheetAction
import no.vardir.skald.ui.components.SheetButtons
import no.vardir.skald.ui.components.SkaldSheet
import no.vardir.skald.ui.components.SkaldTextField
import no.vardir.skald.ui.components.TagPicker
import no.vardir.skald.ui.components.folderOptions
import no.vardir.skald.ui.theme.Skald

/**
 * What a long press on a note offers, and the small forms behind each answer.
 *
 * The desktop has a right-click menu, a rename field in the title bar and a
 * folder tree you can drag onto. None of those exist under a thumb, so all of
 * it arrives here: one sheet that starts as a menu and becomes whichever form
 * was asked for.
 */
private enum class NoteStage { Menu, Rename, Move, Properties, Delete }

@Composable
fun NoteActionsSheet(
    note: NoteMeta,
    snapshot: VaultSnapshot,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onMove: (String) -> Unit,
    onDuplicate: () -> Unit,
    onSetPinned: (Boolean) -> Unit,
    onEditFrontmatter: (Map<String, Any?>, Set<String>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var stage by remember(note.path) { mutableStateOf(NoteStage.Menu) }
    val pinned = snapshot.settings.pinnedNote == note.path

    when (stage) {
        NoteStage.Menu -> {
            val clipboard = LocalClipboardManager.current
            val linkIndex = remember(snapshot.notes) {
                Wikilinks.buildIndex(snapshot.notes.map { Wikilinks.Linkable(it.path, it.title) })
            }
            SkaldSheet(title = note.title, subtitle = note.path, onDismiss = onDismiss) {
                SheetAction("↗", "Open", "read and write it") {
                    onDismiss()
                    onOpen()
                }
                SheetAction("✎", "Properties", "schema, title, tags") { stage = NoteStage.Properties }
                SheetAction("A", "Rename the file", "every link that points here follows") {
                    stage = NoteStage.Rename
                }
                SheetAction("⇄", "Move to a folder", note.path.substringBeforeLast('/', "vault root")) {
                    stage = NoteStage.Move
                }
                SheetAction("⧉", "Duplicate", "a copy beside this one") {
                    onDismiss()
                    onDuplicate()
                }
                SheetAction("[[", "Copy a link to it", "paste it into any note") {
                    val target = Wikilinks.shortestTarget(note.path, linkIndex)
                    clipboard.setText(AnnotatedString("[[$target]]"))
                    onDismiss()
                }
                SheetAction(
                    glyph = if (pinned) "★" else "☆",
                    label = if (pinned) "Unpin from the logbook" else "Pin to the logbook",
                ) {
                    onSetPinned(!pinned)
                    onDismiss()
                }
                Box(Modifier.padding(vertical = 6.dp)) { Hairline() }
                SheetAction("⌫", "Delete", "an earlier version stays in its history", destructive = true) {
                    stage = NoteStage.Delete
                }
            }
        }

        NoteStage.Rename -> RenameSheet(
            title = "Rename",
            subtitle = note.path,
            initial = Notes.titleFromPath(note.path),
            label = "File name",
            hint = "The name a wikilink points at. Every link that already points " +
                "here is rewritten, in whichever form it was written.",
            onConfirm = {
                onDismiss()
                onRename(it)
            },
            onDismiss = onDismiss,
        )

        NoteStage.Move -> MoveSheet(
            title = note.title,
            current = Notes.parentFolder(note.path),
            snapshot = snapshot,
            onConfirm = {
                onDismiss()
                onMove(it)
            },
            onDismiss = onDismiss,
        )

        NoteStage.Properties -> PropertiesSheet(
            path = note.path,
            frontmatter = note.frontmatter,
            schema = note.schema,
            knownTags = snapshot.notes.flatMap { it.tags }.distinct().sorted(),
            onApply = { changes, remove ->
                onDismiss()
                onEditFrontmatter(changes, remove)
            },
            onDismiss = onDismiss,
        )

        NoteStage.Delete -> ConfirmSheet(
            title = "Delete ${note.title}?",
            subtitle = note.path,
            body = "The file goes, but a copy stays in this vault's local history — and " +
                "if this vault syncs, the deletion travels to your other devices.",
            confirm = "Delete",
            onConfirm = {
                onDismiss()
                onDelete()
            },
            onDismiss = onDismiss,
        )
    }
}

/**
 * The properties of a note as a form: the schema as runes, the title, the tags,
 * and every other frontmatter field as a row you can edit or take out. The raw
 * Markdown editor is still there for anything stranger — it is just no longer
 * the only way to change what kind of note this is.
 */
@Composable
fun PropertiesSheet(
    path: String,
    frontmatter: Map<String, Any?>,
    schema: SchemaName,
    knownTags: List<String>,
    onApply: (changes: Map<String, Any?>, remove: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Skald.colors
    val reserved = setOf("title", "schema", "tags")

    var title by remember(path) { mutableStateOf(frontmatter["title"]?.toString() ?: "") }
    var chosen by remember(path) { mutableStateOf(schema) }
    var tags by remember(path) { mutableStateOf(Frontmatter.tagsOf(frontmatter)) }
    val fields = remember(path) {
        mutableStateListOf<Field>().apply {
            for ((key, value) in frontmatter) {
                if (key in reserved) continue
                this += Field(key, renderValue(value), value is List<*>)
            }
        }
    }
    val dropped = remember(path) { mutableStateListOf<String>() }
    var newKey by remember(path) { mutableStateOf("") }

    SkaldSheet(title = "Properties", subtitle = path, onDismiss = onDismiss) {
        FieldLabel("Schema")
        SchemaPicker(chosen) { chosen = it }
        Text(
            "Written into the note as `schema: ${chosen.name}`. Without it, the folder decides.",
            style = Skald.type.metaSmall,
            color = colors.tx3,
            modifier = Modifier.padding(top = 6.dp),
        )

        FieldLabel("Title")
        SkaldTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = Notes.titleFromPath(path),
        )
        Text(
            "What every list and link label shows. Leave it empty to go by the file name.",
            style = Skald.type.metaSmall,
            color = colors.tx3,
            modifier = Modifier.padding(top = 6.dp),
        )

        FieldLabel("Tags")
        TagPicker(selected = tags, known = knownTags, onChange = { tags = it })

        if (fields.isNotEmpty()) FieldLabel("Other fields")
        for ((index, field) in fields.withIndex()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    field.key,
                    style = Skald.type.meta,
                    color = colors.tx3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(74.dp),
                )
                Box(Modifier.weight(1f)) {
                    SkaldTextField(
                        value = field.value,
                        onValueChange = { fields[index] = field.copy(value = it) },
                        placeholder = if (field.list) "a, list" else "value",
                    )
                }
                Text(
                    "✕",
                    style = Skald.type.meta,
                    color = colors.tx3,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = "Remove ${field.key}") {
                            dropped += field.key
                            fields.removeAt(index)
                        }
                        .padding(8.dp),
                )
            }
        }

        FieldLabel("Add a field")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                SkaldTextField(value = newKey, onValueChange = { newKey = it }, placeholder = "key")
            }
            Text(
                "Add",
                style = Skald.type.row,
                color = if (newKey.isBlank()) colors.tx4 else colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = newKey.isNotBlank()) {
                        val key = newKey.trim().replace(Regex("""[^A-Za-z0-9_-]"""), "-")
                        if (key.isNotEmpty() && fields.none { it.key == key } && key !in reserved) {
                            fields += Field(key, "", list = false)
                            dropped.remove(key)
                        }
                        newKey = ""
                    }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }

        SheetButtons(
            confirm = "Save",
            onConfirm = {
                val changes = LinkedHashMap<String, Any?>()
                val remove = mutableSetOf<String>()

                changes["schema"] = chosen.name
                if (title.isBlank()) remove += "title" else changes["title"] = title.trim()
                if (tags.isEmpty()) remove += "tags" else changes["tags"] = tags
                for (field in fields) {
                    changes[field.key] = if (field.list) {
                        field.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    } else {
                        field.value.trim()
                    }
                }
                remove += dropped.filter { key -> fields.none { it.key == key } }
                onApply(changes, remove)
            },
            onDismiss = onDismiss,
        )
    }
}

/** One frontmatter row being edited. [list] remembers it arrived as an array. */
private data class Field(val key: String, val value: String, val list: Boolean)

private fun renderValue(value: Any?): String = when (value) {
    null -> ""
    is List<*> -> value.joinToString(", ") { it?.toString().orEmpty() }
    else -> value.toString()
}

// ---------- the small forms behind a menu ----------

@Composable
fun RenameSheet(
    title: String,
    subtitle: String,
    initial: String,
    label: String,
    hint: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(subtitle) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    SkaldSheet(title = title, subtitle = subtitle, onDismiss = onDismiss) {
        FieldLabel(label)
        SkaldTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = initial,
            focusRequester = focus,
            onSubmit = { if (text.isNotBlank()) onConfirm(text.trim()) },
        )
        Text(
            hint,
            style = Skald.type.metaSmall,
            color = Skald.colors.tx3,
            modifier = Modifier.padding(top = 8.dp),
        )
        SheetButtons(
            confirm = "Rename",
            enabled = text.isNotBlank() && text.trim() != initial,
            onConfirm = { onConfirm(text.trim()) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun MoveSheet(
    title: String,
    current: String,
    snapshot: VaultSnapshot,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var folder by remember(title) { mutableStateOf(current) }
    val options = remember(snapshot.tree) { folderOptions(snapshot) }

    SkaldSheet(title = "Move", subtitle = title, onDismiss = onDismiss) {
        FolderPicker(
            options = options,
            selected = folder,
            rootLabel = snapshot.vaultName,
            onSelect = { folder = it },
        )
        SheetButtons(
            confirm = "Move",
            enabled = folder != current,
            onConfirm = { onConfirm(folder) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun ConfirmSheet(
    title: String,
    subtitle: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    SkaldSheet(title = title, subtitle = subtitle, onDismiss = onDismiss) {
        Text(body, style = Skald.type.small, color = Skald.colors.tx2, modifier = Modifier.padding(vertical = 8.dp))
        SheetButtons(confirm = confirm, onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

/**
 * The folder menu: the same shape as the note one, minus the operations a
 * folder cannot survive. Removing one is offered only while it is empty —
 * a long press should never be able to take a shelf of notes with it.
 */
private enum class FolderStage { Menu, Rename, Delete }

@Composable
fun FolderActionsSheet(
    path: String,
    noteCount: Int,
    onNewNote: () -> Unit,
    onNewSubfolder: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var stage by remember(path) { mutableStateOf(FolderStage.Menu) }

    when (stage) {
        FolderStage.Menu -> SkaldSheet(
            title = path.substringAfterLast('/'),
            subtitle = "$path · $noteCount ${if (noteCount == 1) "note" else "notes"}",
            onDismiss = onDismiss,
        ) {
            SheetAction("+", "New note here") {
                onDismiss()
                onNewNote()
            }
            SheetAction("⌗", "New folder inside it") {
                onDismiss()
                onNewSubfolder()
            }
            SheetAction("A", "Rename", "the notes in it keep their links") { stage = FolderStage.Rename }
            Box(Modifier.padding(vertical = 6.dp)) { Hairline() }
            SheetAction(
                glyph = "⌫",
                label = "Remove the folder",
                hint = if (noteCount == 0) "it is empty" else "move its $noteCount notes out first",
                destructive = true,
                enabled = noteCount == 0,
            ) { stage = FolderStage.Delete }
        }

        FolderStage.Rename -> RenameSheet(
            title = "Rename folder",
            subtitle = path,
            initial = path.substringAfterLast('/'),
            label = "Folder name",
            hint = "Every note inside moves with it, and every wikilink that named " +
                "the old folder is rewritten to the new one.",
            onConfirm = {
                onDismiss()
                onRename(it)
            },
            onDismiss = onDismiss,
        )

        FolderStage.Delete -> ConfirmSheet(
            title = "Remove $path?",
            subtitle = "empty folder",
            body = "Nothing is written in it, so nothing is lost.",
            confirm = "Remove",
            onConfirm = {
                onDismiss()
                onDelete()
            },
            onDismiss = onDismiss,
        )
    }
}

/** Making a folder: a name, and where it goes. */
@Composable
fun NewFolderSheet(
    snapshot: VaultSnapshot,
    parent: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var under by remember { mutableStateOf(parent) }
    val options = remember(snapshot.tree) { folderOptions(snapshot) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    SkaldSheet(title = "New folder", subtitle = snapshot.vaultName, onDismiss = onDismiss) {
        FieldLabel("Name")
        SkaldTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "Sagas",
            focusRequester = focus,
        )
        FieldLabel("Inside")
        FolderPicker(
            options = options,
            selected = under,
            rootLabel = snapshot.vaultName,
            onSelect = { under = it },
        )
        SheetButtons(
            confirm = "Make it",
            enabled = name.isNotBlank(),
            onConfirm = {
                val safe = Notes.safeFileName(name)
                onConfirm(if (under.isEmpty()) safe else "$under/$safe")
            },
            onDismiss = onDismiss,
        )
    }
}
