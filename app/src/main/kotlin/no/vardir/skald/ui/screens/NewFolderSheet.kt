package no.vardir.skald.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.ui.components.FieldLabel
import no.vardir.skald.ui.components.SheetButtons
import no.vardir.skald.ui.components.SkaldSheet
import no.vardir.skald.ui.components.SkaldTextField

@Composable
fun NewFolderSheet(
    snapshot: VaultSnapshot,
    parent: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(parent) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val trimmed = name.trim()
    val path = if (parent.isBlank()) trimmed else "$parent/$trimmed"
    val existing = remember(snapshot.tree, path) {
        path.isNotBlank() && snapshot.tree.allFolders().any { it.path.equals(path, ignoreCase = true) }
    }
    val valid = trimmed.isNotBlank() && '/' !in trimmed && '\\' !in trimmed && !existing

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    SkaldSheet(
        title = "New folder",
        subtitle = if (parent.isBlank()) snapshot.vaultName else parent,
        onDismiss = onDismiss,
    ) {
        FieldLabel("Folder name")
        SkaldTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "Name",
            focusRequester = focus,
            onSubmit = { if (valid) onConfirm(path) },
        )
        SheetButtons(
            confirm = "Create",
            enabled = valid,
            onConfirm = { onConfirm(path) },
            onDismiss = onDismiss,
        )
    }
}
