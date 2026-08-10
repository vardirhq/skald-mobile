package no.vardir.skald.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.model.TaskStatus
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.ui.components.EmptyState
import no.vardir.skald.ui.components.SectionHeader
import no.vardir.skald.ui.components.Segmented
import no.vardir.skald.ui.theme.Skald

/**
 * Threads. The desktop offers table, kanban and calendar; on a phone all three
 * collapse to one filtered list grouped by when it is due, which is the only
 * question a thumb actually asks.
 */
@Composable
fun ThreadsScreen(
    snapshot: VaultSnapshot,
    todayIso: String,
    onOpenNote: (String) -> Unit,
    onToggleTask: (String, Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf<TaskStatus?>(null) }

    val filtered = remember(snapshot.tasks, filter) {
        snapshot.tasks.filter { filter == null || it.status == filter }
    }
    val grouped = remember(filtered, todayIso) { groupByDue(filtered, todayIso) }

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Segmented(
                options = listOf(
                    null to "All",
                    TaskStatus.Open to "Open",
                    TaskStatus.Working to "Working",
                    TaskStatus.Blocked to "Blocked",
                    TaskStatus.Done to "Done",
                ),
                selected = filter,
                onSelect = { filter = it },
            )
        }

        if (filtered.isEmpty()) {
            EmptyState(
                "No threads here",
                "Write `- [ ] something` in any note and it shows up in this list.",
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxWidth()) {
            for ((group, tasks) in grouped) {
                item(key = "h-$group") {
                    Box(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp)) {
                        SectionHeader(group, tasks.size.toString())
                    }
                }
                items(tasks, key = { it.id }) { task ->
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                        ThreadCard(task, todayIso, onOpenNote, onToggleTask)
                    }
                }
            }
            item { Box(Modifier.padding(bottom = 40.dp)) }
        }
    }
}

/**
 * Overdue first, then today, then the near future, then everything with no date
 * at all — and done last, because it is a record rather than a question.
 */
private fun groupByDue(
    tasks: List<no.vardir.skald.core.model.TaskItem>,
    todayIso: String,
): List<Pair<String, List<no.vardir.skald.core.model.TaskItem>>> {
    val order = listOf("Overdue", "Today", "Soon", "Later", "No date", "Done")
    return tasks
        .groupBy { task ->
            when {
                task.status == TaskStatus.Done -> "Done"
                task.due == null -> "No date"
                task.due < todayIso -> "Overdue"
                task.due == todayIso -> "Today"
                task.due <= plusDays(todayIso, 7) -> "Soon"
                else -> "Later"
            }
        }
        .toList()
        .sortedBy { order.indexOf(it.first) }
}

private fun plusDays(iso: String, days: Long): String =
    java.time.LocalDate.parse(iso).plusDays(days).toString()
