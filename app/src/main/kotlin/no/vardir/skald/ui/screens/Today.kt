package no.vardir.skald.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.model.TaskItem
import no.vardir.skald.core.model.TaskStatus
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.ui.components.Eyebrow
import no.vardir.skald.ui.components.Hairline
import no.vardir.skald.ui.components.PriorityMark
import no.vardir.skald.ui.components.Rune
import no.vardir.skald.ui.components.SectionHeader
import no.vardir.skald.ui.components.SkaldCard
import no.vardir.skald.ui.components.SkaldCheckbox
import no.vardir.skald.ui.components.SkaldRow
import no.vardir.skald.ui.components.formatDue
import no.vardir.skald.ui.theme.Skald
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Logbook. Today's page in the saga: the date set large, honest counts, a
 * week of activity, the threads due soonest, what you touched recently, and a
 * pinned note.
 */
@Composable
fun TodayScreen(
    snapshot: VaultSnapshot,
    todayIso: String,
    onOpenNote: (String) -> Unit,
    onToggleTask: (String, Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    val today = LocalDate.parse(todayIso)
    val openThreads = snapshot.tasks
        .filter { it.status != TaskStatus.Done }
        .sortedWith(compareBy({ it.due ?: "9999" }, { it.noteTitle }))
    val editedToday = snapshot.notes.count { millisToIso(it.updated) == todayIso }

    LazyColumn(modifier.fillMaxWidth()) {
        item {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp)) {
                Eyebrow("Daily · ${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        buildAnnotatedString {
                            append(today.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " ")
                            withStyle(SpanStyle(color = colors.accent)) { append(today.dayOfMonth.toString()) }
                        },
                        style = Skald.type.display,
                        color = colors.tx0,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(
                    Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Stat(snapshot.stats.tasksOpen.toString(), "open")
                    Stat(snapshot.stats.overdue.toString(), "overdue", warn = snapshot.stats.overdue > 0)
                    Stat(editedToday.toString(), "edited")
                }
            }
            Hairline()
        }

        item {
            Column(Modifier.padding(18.dp)) {
                SectionHeader("This week", weekLabel(today))
                WeekStrip(snapshot, today)
            }
        }

        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                SectionHeader(
                    "Open threads",
                    if (openThreads.isEmpty()) "nothing due" else "${openThreads.size} open",
                )
            }
        }

        items(openThreads.take(5).size) { index ->
            val task = openThreads[index]
            Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                ThreadCard(task, todayIso, onOpenNote, onToggleTask)
            }
        }

        item {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 26.dp)) {
                SectionHeader("Recently touched")
                for (note in snapshot.notes.sortedByDescending { it.updated }.take(5)) {
                    SkaldRow(onClick = { onOpenNote(note.path) }) {
                        Rune(note.schema)
                        Text(
                            note.title,
                            style = Skald.type.row,
                            color = colors.tx1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(relativeTime(note.updated), style = Skald.type.meta, color = colors.tx4)
                    }
                }
            }
        }

        item {
            val pinned = snapshot.settings.pinnedNote?.let { snapshot.byPath[it] }
                ?: snapshot.notes.firstOrNull { it.path.contains("design rationale", ignoreCase = true) }
            if (pinned != null) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 32.dp)) {
                    SectionHeader("Pinned")
                    SkaldCard(onClick = { onOpenNote(pinned.path) }, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                        Column {
                            Text(pinned.excerpt, style = Skald.type.row, color = colors.tx1)
                            Text(
                                "pinned · ${pinned.title}",
                                style = Skald.type.meta,
                                color = colors.tx3,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }
            } else {
                Box(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, warn: Boolean = false) {
    Column {
        Text(value, style = Skald.type.stat, color = if (warn) Skald.colors.warn else Skald.colors.tx0)
        Text(label.uppercase(), style = Skald.type.metaSmall, color = Skald.colors.tx3)
    }
}

/** Seven days, each bar scaled by how much was written that day. */
@Composable
private fun WeekStrip(snapshot: VaultSnapshot, today: LocalDate) {
    val colors = Skald.colors
    val start = today.minusDays(today.dayOfWeek.value.toLong() % 7)
    val counts = (0..6).map { offset ->
        val day = start.plusDays(offset.toLong())
        snapshot.notes.count { millisToIso(it.updated) == day.toString() }
    }
    val busiest = (counts.maxOrNull() ?: 0).coerceAtLeast(1)

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (offset in 0..6) {
            val day = start.plusDays(offset.toLong())
            val isToday = day == today
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isToday) colors.accentGhost else colors.bg1)
                    .border(
                        BorderStroke(1.dp, if (isToday) colors.accentLine else colors.line),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = Skald.type.metaSmall,
                    color = colors.tx3,
                )
                Text(day.dayOfMonth.toString(), style = Skald.type.small, color = colors.tx1)
                Box(
                    Modifier
                        .padding(top = 7.dp, start = 6.dp, end = 6.dp)
                        .fillMaxWidth(if (counts[offset] == 0) 1f else (0.2f + 0.8f * counts[offset] / busiest))
                        .height(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (counts[offset] == 0) colors.line2 else colors.accent),
                )
            }
        }
    }
}

@Composable
fun ThreadCard(
    task: TaskItem,
    todayIso: String,
    onOpenNote: (String) -> Unit,
    onToggleTask: (String, Int, Boolean) -> Unit,
) {
    val colors = Skald.colors
    val overdue = task.isOverdue(todayIso)
    SkaldCard(onClick = { onOpenNote(task.notePath) }) {
        SkaldCheckbox(
            checked = task.status == TaskStatus.Done,
            onCheckedChange = { onToggleTask(task.notePath, task.line, it) },
            tint = when (task.status) {
                TaskStatus.Working -> colors.blue
                TaskStatus.Blocked -> colors.err
                else -> colors.accent
            },
        )
        Column(Modifier.weight(1f)) {
            Text(task.content, style = Skald.type.row, color = colors.tx0, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                threadMeta(task, todayIso, overdue),
                style = Skald.type.meta,
                color = if (overdue) colors.err else colors.tx3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        PriorityMark(task.priority)
    }
}

private fun threadMeta(task: TaskItem, todayIso: String, overdue: Boolean): String = buildString {
    append("↗ ").append(task.noteTitle)
    if (task.status == TaskStatus.Working) append(" · working")
    if (task.status == TaskStatus.Blocked) append(" · blocked")
    task.due?.let {
        append(" · ")
        append(if (overdue) "overdue" else if (it == todayIso) "due today" else "due ${formatDue(it)}")
    }
}

internal fun millisToIso(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()

internal fun relativeTime(millis: Long): String {
    val elapsed = System.currentTimeMillis() - millis
    val minutes = elapsed / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)}d"
        else -> "${minutes / (60 * 24 * 30)}mo"
    }
}

private fun weekLabel(today: LocalDate): String {
    val start = today.minusDays(today.dayOfWeek.value.toLong() % 7)
    val end = start.plusDays(6)
    val month = start.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return "$month ${start.dayOfMonth}–${end.dayOfMonth}"
}
