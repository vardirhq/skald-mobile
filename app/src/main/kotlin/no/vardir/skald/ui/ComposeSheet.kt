package no.vardir.skald.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.model.SchemaName
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.core.text.Notes
import no.vardir.skald.ui.components.FieldLabel
import no.vardir.skald.ui.components.FolderPicker
import no.vardir.skald.ui.components.SchemaPicker
import no.vardir.skald.ui.components.SheetButtons
import no.vardir.skald.ui.components.SkaldSheet
import no.vardir.skald.ui.components.SkaldTextField
import no.vardir.skald.ui.components.folderOptions
import no.vardir.skald.ui.theme.Skald

/**
 * A new page in the saga: a title, which folder it belongs to, and what kind of
 * note it is.
 *
 * The folder list is every folder in the vault, at any depth — the old dialog
 * offered the first four at the top level, which in a vault with real structure
 * meant the one you wanted was usually not there.
 */
@Composable
fun ComposeSheet(
    snapshot: VaultSnapshot,
    startFolder: String,
    onDismiss: () -> Unit,
    onCreate: (folder: String, title: String, schema: String?) -> Unit,
    onNewFolder: () -> Unit,
) {
    val colors = Skald.colors
    var title by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(startFolder) }
    // Null means "whatever the folder implies" — which is what a note written
    // into `Daily/` or `Projects/` should be without anybody saying so.
    var schema by remember { mutableStateOf<SchemaName?>(null) }
    val focus = remember { FocusRequester() }
    val options = remember(snapshot.tree) { folderOptions(snapshot) }
    val inferred = remember(folder, title) {
        Notes.inferSchema(emptyMap(), title, folder.substringBefore('/'))
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    SkaldSheet(
        title = "New note",
        subtitle = snapshot.vaultName,
        onDismiss = onDismiss,
        actions = {
            SheetButtons(
                confirm = "Write",
                enabled = title.isNotBlank(),
                onConfirm = { onCreate(folder, title.trim(), schema?.name) },
                onDismiss = onDismiss,
            )
        },
    ) {
        SkaldTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = "Title",
            focusRequester = focus,
            onSubmit = { if (title.isNotBlank()) onCreate(folder, title.trim(), schema?.name) },
        )

        Row(
            Modifier.padding(top = 14.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.weight(1f)) { FieldLabel("Folder") }
            Text(
                "＋ New folder",
                style = Skald.type.metaSmall,
                color = colors.accent,
                modifier = Modifier
                    .clickable(onClickLabel = "New folder") { onNewFolder() }
                    .padding(vertical = 8.dp),
            )
        }
        FolderPicker(
            options = options,
            selected = folder,
            rootLabel = snapshot.vaultName,
            onSelect = { folder = it },
        )

        FieldLabel("Kind")
        SchemaPicker(selected = schema ?: inferred, onSelect = { schema = it })
        Text(
            if (schema == null) {
                "${inferred.name}, because of where it is going. Tap one to fix it in the note itself."
            } else {
                "Written into the note as `schema: ${schema?.name}`."
            },
            style = Skald.type.metaSmall,
            color = colors.tx3,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}