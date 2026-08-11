package no.vardir.skald.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.model.DeletedNoteEntry
import no.vardir.skald.ui.components.EmptyState
import no.vardir.skald.ui.components.Hairline
import no.vardir.skald.ui.components.Rune
import no.vardir.skald.ui.theme.Skald
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TrashScreen(
    entries: List<DeletedNoteEntry>,
    onRestore: (DeletedNoteEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        EmptyState("Recently deleted is empty", "Deleted notes appear here while their local history remains.")
        return
    }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Text(
                "Deletion history stays on this device. Restore puts the Markdown file back at its original path.",
                style = Skald.type.small,
                color = Skald.colors.tx3,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
            )
        }
        items(entries, key = { "${it.path}:${it.versionId}" }) { entry ->
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Rune(entry.schema)
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = Skald.type.row, color = Skald.colors.tx1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${entry.path} · ${deletedLabel(entry.deletedAt)}", style = Skald.type.meta, color = Skald.colors.tx3, maxLines = 1)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(9.dp)).clickable { onRestore(entry) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) { Text("Restore", style = Skald.type.metaSmall, color = Skald.colors.accent) }
                }
                Hairline()
            }
        }
        item { Box(Modifier.padding(bottom = 30.dp)) }
    }
}

private val deletedDate = DateTimeFormatter.ofPattern("d MMM yyyy")
private fun deletedLabel(epochMs: Long): String = Instant.ofEpochMilli(epochMs)
    .atZone(ZoneId.systemDefault()).format(deletedDate)
