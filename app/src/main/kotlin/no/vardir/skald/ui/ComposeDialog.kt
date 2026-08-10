package no.vardir.skald.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import no.vardir.skald.ui.components.Eyebrow
import no.vardir.skald.ui.components.FilterChip
import no.vardir.skald.ui.theme.Skald

/** A new page in the saga: a title, and which folder it belongs to. */
@Composable
fun ComposeDialog(
    folders: List<String>,
    onDismiss: () -> Unit,
    onCreate: (folder: String, title: String) -> Unit,
) {
    val colors = Skald.colors
    var title by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(folders.firstOrNull() ?: "") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Skald.metrics.sheet))
                .background(colors.bgPop)
                .border(BorderStroke(1.dp, colors.line2), RoundedCornerShape(Skald.metrics.sheet))
                .padding(20.dp),
        ) {
            Eyebrow("New note", Modifier.padding(bottom = 12.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.bg1)
                    .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                if (title.isEmpty()) Text("Title", style = Skald.type.row, color = colors.tx4)
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = Skald.type.row.copy(color = colors.tx0),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }

            if (folders.isNotEmpty()) {
                Eyebrow("Folder", Modifier.padding(top = 16.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        label = "vault root",
                        selected = folder.isEmpty(),
                        onClick = { folder = "" },
                    )
                    for (candidate in folders.take(4)) {
                        FilterChip(candidate, folder == candidate, { folder = candidate })
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    "Cancel",
                    style = Skald.type.row,
                    color = colors.tx2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Text(
                    "Write",
                    style = Skald.type.row,
                    color = if (title.isBlank()) colors.tx4 else colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = title.isNotBlank()) { onCreate(folder, title.trim()) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}
