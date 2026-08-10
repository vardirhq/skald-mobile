package no.vardir.skald.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.model.SchemaName
import no.vardir.skald.core.model.TaskPriority
import no.vardir.skald.core.model.TaskStatus
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.core.text.Dates
import no.vardir.skald.core.text.Fuzzy
import no.vardir.skald.ui.theme.Skald

/**
 * The pickers. Every one of these exists because the desktop's answer to the
 * same question is "type the syntax" — a folder path, `schema: Project`,
 * `@due(2026-06-01)`, `#tag`. None of that is thumb-typeable, and none of it is
 * discoverable by somebody who has not read the manual, so on a phone each one
 * becomes something you point at.
 */

// ---------- folders ----------

/** A folder as the picker shows it: where it sits, and how full it is. */
data class FolderOption(val path: String, val name: String, val depth: Int, val notes: Int)

/**
 * Every folder in the vault, at any depth — which is the fix for a "new note"
 * dialog that only ever offered the first four at the top level. A vault with
 * `Projects/Sagas/Winter` in it has to be able to put a note there.
 */
fun folderOptions(snapshot: VaultSnapshot): List<FolderOption> =
    snapshot.tree.allFolders().map { node ->
        FolderOption(
            path = node.path,
            name = node.name,
            depth = node.depth,
            notes = node.allNotes().size,
        )
    }

@Composable
fun FolderPicker(
    options: List<FolderOption>,
    selected: String,
    rootLabel: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    var filter by remember { mutableStateOf("") }
    val matching = remember(options, filter) {
        if (filter.isBlank()) options else options.filter { it.path.contains(filter.trim(), ignoreCase = true) }
    }

    Column(modifier.fillMaxWidth()) {
        // A handful fits on the screen; a vault with real structure needs to be
        // able to narrow the list rather than scroll it by feel.
        if (options.size > 7) {
            SkaldTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = "Find a folder…",
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .heightIn(max = 232.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            FolderRow(rootLabel, depth = 0, count = null, selected = selected.isEmpty()) { onSelect("") }
            for (option in matching) {
                FolderRow(option.name, option.depth, option.notes, selected == option.path) { onSelect(option.path) }
            }
            if (matching.isEmpty() && filter.isNotBlank()) {
                Text(
                    "No folder called that.",
                    style = Skald.type.small,
                    color = colors.tx3,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun FolderRow(name: String, depth: Int, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val colors = Skald.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) colors.accentGhost else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = (10 + depth * 14).dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (selected) "✓" else "▸", style = Skald.type.meta, color = if (selected) colors.accent else colors.tx4)
        Text(
            name,
            style = Skald.type.row,
            color = if (selected) colors.accent else colors.tx1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) Text(count.toString(), style = Skald.type.meta, color = colors.tx4)
    }
}

// ---------- notes ----------

/**
 * Which note something goes into. Ordered by what was touched most recently,
 * because the note you want is nearly always one you were just in, and narrowed
 * by the same fuzzy matcher the Hall uses when it is not.
 */
@Composable
fun NotePicker(
    snapshot: VaultSnapshot,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = Skald.colors
    var filter by remember { mutableStateOf("") }
    val matching = remember(snapshot.notes, filter) {
        if (filter.isBlank()) {
            snapshot.notes.sortedByDescending { it.updated }.take(12)
        } else {
            snapshot.notes
                .mapNotNull { note ->
                    val hit = Fuzzy.match(filter, note.title) ?: Fuzzy.match(filter, note.path)
                    hit?.let { note to it.score }
                }
                .sortedByDescending { it.second }
                .take(12)
                .map { it.first }
        }
    }

    Column(modifier.fillMaxWidth()) {
        SkaldTextField(value = filter, onValueChange = { filter = it }, placeholder = "Find a note…")
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .heightIn(max = 232.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            leading?.invoke()
            for (note in matching) {
                val active = note.path == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) colors.accentGhost else Color.Transparent)
                        .clickable { onSelect(note.path) }
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Rune(note.schema, 15.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            note.title,
                            style = Skald.type.row,
                            color = if (active) colors.accent else colors.tx1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            note.path,
                            style = Skald.type.metaSmall,
                            color = colors.tx4,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (active) Text("✓", style = Skald.type.meta, color = colors.accent)
                }
            }
        }
    }
}

// ---------- schema ----------

/**
 * The schema, as the eight runes rather than as a string in a YAML block. This
 * is the whole of "schema needs to be properly editable" for the case that
 * matters: what kind of note is this.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchemaPicker(selected: SchemaName?, onSelect: (SchemaName) -> Unit, modifier: Modifier = Modifier) {
    val colors = Skald.colors
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (schema in SchemaName.entries) {
            val active = schema == selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) colors.accentGhost else colors.bg3)
                    .border(
                        BorderStroke(1.dp, if (active) colors.accentLine else colors.line),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable { onSelect(schema) }
                    .padding(start = 9.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Rune(schema, 15.dp)
                Text(schema.name, style = Skald.type.small, color = if (active) colors.accent else colors.tx2)
            }
        }
    }
}

// ---------- tags ----------

/**
 * Tags as chips: the ones on this thing, tappable to take off, and the ones the
 * vault already knows, tappable to put on. Typing is the last resort rather
 * than the only way in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(
    selected: List<String>,
    known: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    var typed by remember { mutableStateOf("") }

    fun add(tag: String) {
        val clean = tag.trim().removePrefix("#").replace(Regex("""\s+"""), "-")
        if (clean.isEmpty() || selected.any { it.equals(clean, ignoreCase = true) }) return
        onChange(selected + clean)
        typed = ""
    }

    val offers = remember(known, selected, typed) {
        known.distinct()
            .filter { tag -> selected.none { it.equals(tag, ignoreCase = true) } }
            .filter { typed.isBlank() || it.contains(typed.trim(), ignoreCase = true) }
            .take(8)
    }

    Column(modifier.fillMaxWidth()) {
        if (selected.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (tag in selected) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.accentGhost)
                            .clickable(onClickLabel = "Remove #$tag") { onChange(selected - tag) }
                            .padding(start = 11.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text("#$tag", style = Skald.type.small, color = colors.accent)
                        Text("✕", style = Skald.type.metaSmall, color = colors.accent)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                SkaldTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = "Add a tag…",
                    onSubmit = { add(typed) },
                )
            }
            Text(
                "Add",
                style = Skald.type.row,
                color = if (typed.isBlank()) colors.tx4 else colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = typed.isNotBlank()) { add(typed) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }

        if (offers.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (tag in offers) FilterChip("#$tag", selected = false, onClick = { add(tag) })
            }
        }
    }
}

// ---------- a due date ----------

/**
 * A due date, without anybody having to know that `@due()` is a thing. The
 * chips cover the days a thread is actually given; the field takes "friday",
 * "+3d" or "24/6" and says what it made of it before it is committed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuePicker(
    due: String?,
    todayIso: String,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    var typed by remember { mutableStateOf("") }
    val choices = remember(todayIso) { Dates.dueChoices(todayIso) }
    val understood = remember(typed, todayIso) { Dates.parse(typed, todayIso) }

    Column(modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (choice in choices) {
                FilterChip(choice.label, due == choice.iso, { onChange(choice.iso) })
            }
            // A date picked earlier that none of the chips offers still has to
            // be visible, or the sheet would look like it had lost it.
            if (due != null && choices.none { it.iso == due }) {
                FilterChip(Dates.label(due, todayIso), selected = true, onClick = {})
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                SkaldTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = "or say when — friday, +3d, 24/6",
                    onSubmit = { understood?.let { onChange(it); typed = "" } },
                )
            }
            Text(
                understood?.let { Dates.label(it, todayIso) } ?: "—",
                style = Skald.type.meta,
                color = if (understood == null) colors.tx4 else colors.accent,
                modifier = Modifier
                    .width(64.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(enabled = understood != null) {
                        understood?.let { onChange(it) }
                        typed = ""
                    }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

// ---------- the two enums a thread carries ----------

@Composable
fun PriorityPicker(selected: TaskPriority, onSelect: (TaskPriority) -> Unit, modifier: Modifier = Modifier) {
    Segmented(
        options = TaskPriority.entries.map { it to it.name.lowercase() },
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
fun StatusPicker(selected: TaskStatus, onSelect: (TaskStatus) -> Unit, modifier: Modifier = Modifier) {
    Segmented(
        options = TaskStatus.entries.map { it to it.name.lowercase() },
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
    )
}
