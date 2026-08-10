package no.vardir.skald.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.model.TaskPriority
import no.vardir.skald.core.model.TaskStatus
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.core.text.Tasks
import no.vardir.skald.ui.components.DuePicker
import no.vardir.skald.ui.components.FieldLabel
import no.vardir.skald.ui.components.Hairline
import no.vardir.skald.ui.components.NotePicker
import no.vardir.skald.ui.components.PriorityPicker
import no.vardir.skald.ui.components.SheetAction
import no.vardir.skald.ui.components.SheetButtons
import no.vardir.skald.ui.components.SkaldSheet
import no.vardir.skald.ui.components.SkaldTextField
import no.vardir.skald.ui.components.StatusPicker
import no.vardir.skald.ui.components.TagPicker
import no.vardir.skald.ui.theme.Skald

/**
 * Writing a thread down, from the list rather than from inside a note.
 *
 * A phone is where a task actually occurs to you, and "open the right note,
 * find the end of it, type a checkbox and then the syntax for a date" is four
 * steps too many. This is one: say the thing, say when, and it lands in today's
 * page unless you point somewhere else.
 */
@Composable
fun NewThreadSheet(
    snapshot: VaultSnapshot,
    todayIso: String,
    knownTags: List<String>,
    onCreate: (notePath: String?, content: String, due: String?, priority: TaskPriority, tags: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Skald.colors
    var content by remember { mutableStateOf("") }
    var due by remember { mutableStateOf<String?>(null) }
    var priority by remember { mutableStateOf(TaskPriority.Med) }
    var tags by remember { mutableStateOf(emptyList<String>()) }
    // Null means today's page, which is made on the way in if it is not there.
    var target by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    SkaldSheet(title = "New thread", subtitle = snapshot.vaultName, onDismiss = onDismiss) {
        SkaldTextField(
            value = content,
            onValueChange = { content = it },
            placeholder = "What needs doing",
            singleLine = false,
            focusRequester = focus,
        )

        FieldLabel("Due")
        DuePicker(due = due, todayIso = todayIso, onChange = { due = it })

        FieldLabel("Priority")
        PriorityPicker(priority) { priority = it }

        FieldLabel("Tags")
        TagPicker(selected = tags, known = knownTags, onChange = { tags = it })

        FieldLabel("Write it into")
        NotePicker(
            snapshot = snapshot,
            selected = target,
            onSelect = { target = it },
            leading = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { target = null }
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(if (target == null) "✓" else "▸", style = Skald.type.meta, color = if (target == null) colors.accent else colors.tx4)
                    Text(
                        "Today's page",
                        style = Skald.type.row,
                        color = if (target == null) colors.accent else colors.tx1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(todayIso, style = Skald.type.metaSmall, color = colors.tx4)
                }
            },
        )

        SheetButtons(
            confirm = "Write it",
            enabled = content.isNotBlank(),
            onConfirm = {
                onCreate(target, content.trim(), due, priority, tags)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
}

/** One thread, as it stands right now — whatever list or note it came from. */
data class ThreadTarget(
    val notePath: String,
    val noteTitle: String,
    /** 1-based, in the raw file. */
    val line: Int,
    val content: String,
    val status: TaskStatus,
    val priority: TaskPriority,
    val due: String?,
    val tags: List<String>,
)

/**
 * Everything a thread carries, as things to point at.
 *
 * `- [ ] Write the saga @due(2026-06-01) @p(high) @status(working) #editor` is a
 * fine line to type with ten fingers and a manual. On a phone it is four things
 * to get wrong and one thing to forget is possible at all — so here they are,
 * with the syntax written for you on the way out.
 */
@Composable
fun ThreadSheet(
    target: ThreadTarget,
    knownTags: List<String>,
    todayIso: String,
    onApply: (Tasks.Edits) -> Unit,
    onOpenNote: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var content by remember(target.notePath, target.line) { mutableStateOf(target.content) }
    var status by remember(target.notePath, target.line) { mutableStateOf(target.status) }
    var priority by remember(target.notePath, target.line) { mutableStateOf(target.priority) }
    var due by remember(target.notePath, target.line) { mutableStateOf(target.due) }
    var tags by remember(target.notePath, target.line) { mutableStateOf(target.tags) }

    SkaldSheet(title = "Thread", subtitle = target.noteTitle, onDismiss = onDismiss) {
        SkaldTextField(
            value = content,
            onValueChange = { content = it },
            placeholder = "What needs doing",
            singleLine = false,
        )

        FieldLabel("Due")
        DuePicker(due = due, todayIso = todayIso, onChange = { due = it })

        FieldLabel("Priority")
        PriorityPicker(priority) { priority = it }

        FieldLabel("Status")
        StatusPicker(status) { status = it }

        FieldLabel("Tags")
        TagPicker(selected = tags, known = knownTags, onChange = { tags = it })

        if (onOpenNote != null) {
            Box(Modifier.padding(top = 12.dp)) { Hairline() }
            SheetAction("↗", "Open ${target.noteTitle}", "where this line lives") {
                onDismiss()
                onOpenNote()
            }
        }

        SheetButtons(
            confirm = "Save",
            onConfirm = {
                onApply(
                    Tasks.Edits(
                        status = status,
                        content = content.trim().ifEmpty { target.content },
                        due = when {
                            due == null -> Tasks.DueEdit.Clear
                            due == target.due -> Tasks.DueEdit.Unset
                            else -> Tasks.DueEdit.Set(due!!)
                        },
                        priority = priority,
                        tags = tags,
                    )
                )
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
}
