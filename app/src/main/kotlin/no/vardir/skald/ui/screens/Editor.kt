package no.vardir.skald.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import no.vardir.skald.core.model.AttachmentRef
import no.vardir.skald.core.model.NotePayload
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.core.text.Markdown
import no.vardir.skald.core.text.Wikilinks
import no.vardir.skald.ui.components.Eyebrow
import no.vardir.skald.ui.components.Hairline
import no.vardir.skald.ui.components.MarkdownContext
import no.vardir.skald.ui.components.MarkdownView
import no.vardir.skald.ui.components.Rune
import no.vardir.skald.ui.components.SectionHeader
import no.vardir.skald.ui.theme.Skald

/**
 * The note surface. Reading view by default — typed frontmatter as a carved
 * tablet, stanza-numbered sections, inline threads you can tick — with the raw
 * Markdown one tap away, because the file is the thing you actually own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    note: NotePayload,
    snapshot: VaultSnapshot,
    todayIso: String,
    editingSource: Boolean,
    onOpenNote: (String) -> Unit,
    onToggleTask: (String, Int, Boolean) -> Unit,
    onSave: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    onOpenAttachment: (AttachmentRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors

    if (editingSource) {
        SourceEditor(note, onSave, modifier)
        return
    }

    val blocks = remember(note.content) { Markdown.parse(note.body, note.bodyStartLine) }

    // The link index is the indexer's, not a second opinion: same tiers, same
    // winner, so a tap goes exactly where the graph says the edge goes.
    val linkIndex = remember(snapshot.notes) {
        Wikilinks.buildIndex(snapshot.notes.map { Wikilinks.Linkable(it.path, it.title) })
    }
    val ctx = remember(note.meta.path, linkIndex) {
        MarkdownContext(
            resolve = { target -> linkIndex.resolve(target) },
            openNote = onOpenNote,
            openExternal = onOpenExternal,
            resolveAttachment = { target -> note.attachments.firstOrNull { it.target == target } },
            openAttachment = onOpenAttachment,
            toggleTask = { line, done -> onToggleTask(note.meta.path, line, done) },
            todayIso = todayIso,
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 16.dp, bottom = 48.dp)
    ) {
        Eyebrow(note.meta.path, Modifier.padding(bottom = 10.dp))
        Text(note.meta.title, style = Skald.type.title, color = colors.tx0, modifier = Modifier.padding(bottom = 16.dp))

        FrontmatterTablet(note)

        MarkdownView(blocks, ctx)

        if (note.backlinks.isNotEmpty()) {
            Hairline(Modifier.padding(top = 22.dp))
            Column(Modifier.padding(top = 16.dp)) {
                SectionHeader("Linked from", "${note.backlinks.size} ${if (note.backlinks.size == 1) "note" else "notes"}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (link in note.backlinks) {
                        Row(
                            Modifier
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.bg1)
                                .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(18.dp))
                                .clickable { onOpenNote(link.path) }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Rune(link.schema, 13.dp)
                            Text(link.title, style = Skald.type.small, color = colors.tx2)
                        }
                    }
                }
            }
        }

        if (note.meta.unresolved.isNotEmpty()) {
            Column(Modifier.padding(top = 22.dp)) {
                SectionHeader("Not written yet", note.meta.unresolved.size.toString())
                Text(note.meta.unresolved.joinToString(" · "), style = Skald.type.small, color = colors.tx3)
            }
        }
    }
}

/**
 * Typed frontmatter as a single sunken block labelled at the seam, rather than
 * YAML you have to read.
 */
@Composable
private fun FrontmatterTablet(note: NotePayload) {
    val colors = Skald.colors
    if (note.meta.frontmatter.isEmpty()) return

    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Row(
            Modifier.padding(start = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Rune(note.meta.schema, 13.dp)
            Eyebrow("schema · ${note.meta.schema.name}", color = colors.tx3)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.bg1)
                .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            for ((key, value) in note.meta.frontmatter) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(key, style = Skald.type.meta, color = colors.tx3, modifier = Modifier.width(84.dp))
                    Text(
                        buildAnnotatedString {
                            val text = when (value) {
                                is List<*> -> value.joinToString(" · ") { it.toString() }
                                null -> "—"
                                else -> value.toString()
                            }
                            if (key == "schema") {
                                withStyle(SpanStyle(color = colors.accent)) { append(text) }
                            } else {
                                append(text)
                            }
                        },
                        style = Skald.type.small,
                        color = colors.tx1,
                    )
                }
            }
        }
    }
}

/** Raw Markdown, autosaved. The file is what you own; this is it. */
@Composable
private fun SourceEditor(note: NotePayload, onSave: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = Skald.colors
    var value by remember(note.meta.path) { mutableStateOf(TextFieldValue(note.content)) }

    // Autosave on a pause rather than on every keystroke, so a long note is not
    // rewritten — and re-hashed for sync — once per character.
    LaunchedEffect(value.text) {
        if (value.text != note.content) {
            delay(800)
            onSave(value.text)
        }
    }

    Column(modifier.fillMaxSize()) {
        Eyebrow(
            "Source · ${note.meta.path}",
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
        Hairline()
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            textStyle = Skald.type.code.copy(color = colors.tx1, fontSize = 14.sp),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxSize().padding(18.dp),
        )
    }
}
