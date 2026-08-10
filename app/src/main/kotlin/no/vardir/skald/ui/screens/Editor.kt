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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import no.vardir.skald.core.model.AttachmentRef
import no.vardir.skald.core.model.NotePayload
import no.vardir.skald.core.model.TaskStatus
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.core.text.Formatting
import no.vardir.skald.core.text.Frontmatter
import no.vardir.skald.core.text.LiveMarkdown
import no.vardir.skald.core.text.Markdown
import no.vardir.skald.core.text.Tasks
import no.vardir.skald.core.text.Wikilinks
import no.vardir.skald.ui.EditorMode
import no.vardir.skald.ui.components.Eyebrow
import no.vardir.skald.ui.components.Hairline
import no.vardir.skald.ui.components.MarkdownContext
import no.vardir.skald.ui.components.MarkdownView
import no.vardir.skald.ui.components.Rune
import no.vardir.skald.ui.components.SectionHeader
import no.vardir.skald.ui.theme.Skald

/**
 * The note surface, in three readings of the same file.
 *
 * *Live* is the one you write in: typed frontmatter as a carved tablet, the note
 * rendered underneath, and the one block your caret is in shown as the Markdown
 * it really is. *Read* is that page with nothing to type into. *Source* is the
 * whole file, because the file is the thing you actually own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    note: NotePayload,
    snapshot: VaultSnapshot,
    todayIso: String,
    mode: EditorMode,
    onOpenNote: (String) -> Unit,
    onSave: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    onOpenAttachment: (AttachmentRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    val fontSize = snapshot.settings.editorFontSize

    // The draft is what you have typed; the payload is what is on disk. They are
    // the same thing again as soon as the autosave lands, and only then does the
    // vault's copy get to speak for the note — which is what keeps a sync that
    // arrives mid-sentence from taking the sentence away.
    var draft by remember(note.meta.path) { mutableStateOf<String?>(null) }
    val content = draft ?: note.content

    LaunchedEffect(note.content) {
        if (draft == note.content) draft = null
    }
    // Autosave on a pause rather than on every keystroke, so a long note is not
    // rewritten — and re-hashed for sync — once per character.
    LaunchedEffect(content) {
        if (content != note.content) {
            delay(snapshot.settings.autosaveMs.toLong())
            onSave(content)
        }
    }

    val parsed = remember(content) { Frontmatter.parse(content) }
    val body = parsed.body
    val bodyStartLine = parsed.bodyStartLine

    fun setBody(next: String) {
        draft = LiveMarkdown.replaceBody(content, bodyStartLine, next)
    }

    // The link index is the indexer's, not a second opinion: same tiers, same
    // winner, so a tap goes exactly where the graph says the edge goes.
    val linkIndex = remember(snapshot.notes) {
        Wikilinks.buildIndex(snapshot.notes.map { Wikilinks.Linkable(it.path, it.title) })
    }

    // Ticking a box rewrites the draft rather than the file. The two would
    // otherwise race — an unsaved paragraph against a task write straight to
    // disk — and the same autosave carries both.
    fun toggleTask(line: Int, done: Boolean) {
        setBody(
            Tasks.updateLine(
                body,
                line - bodyStartLine,
                Tasks.Edits(status = if (done) TaskStatus.Done else TaskStatus.Open),
            )
        )
    }

    val baseCtx = remember(note.meta.path, linkIndex, content) {
        MarkdownContext(
            resolve = { target -> linkIndex.resolve(target) },
            openNote = onOpenNote,
            openExternal = onOpenExternal,
            resolveAttachment = { target -> note.attachments.firstOrNull { it.target == target } },
            openAttachment = onOpenAttachment,
            toggleTask = ::toggleTask,
            todayIso = todayIso,
        )
    }

    if (mode == EditorMode.Source) {
        SourceEditor(
            content = content,
            path = note.meta.path,
            fontSize = fontSize,
            onChange = { draft = it },
            modifier = modifier,
        )
        return
    }

    // ---------- the live editor's caret ----------

    var caret by remember(note.meta.path) { mutableStateOf<LiveCaret?>(null) }
    LaunchedEffect(mode) { if (mode != EditorMode.Live) caret = null }

    val blocks = remember(body) { LiveMarkdown.split(body) }
    val here = caret
    val activeIndex = if (mode == EditorMode.Live && here != null) LiveMarkdown.blockAt(blocks, here.line) else -1
    val active = blocks.getOrNull(activeIndex)

    val selection = if (active != null && here != null) {
        val start = LiveMarkdown.offsetAt(active.raw, here.line - active.startLine, here.col)
        TextRange(start, (start + here.length).coerceIn(start, active.raw.length))
    } else {
        TextRange.Zero
    }

    val focusRequester = remember(note.meta.path) { FocusRequester() }

    fun moveCaret(startLine: Int, edit: LiveMarkdown.Position, length: Int = 0) {
        caret = LiveCaret(startLine + edit.line, edit.col, length)
    }

    /** A keystroke in the open block. Enter is the one that has to be replayed. */
    fun onFieldChange(value: TextFieldValue) {
        val block = active ?: return
        val newline = LiveMarkdown.insertedNewline(block.raw, value.text, value.selection.start)
        if (newline != null) {
            val edit = LiveMarkdown.enter(block.kind, block.raw, newline)
            setBody(LiveMarkdown.replaceBlock(body, block, edit.raw))
            moveCaret(block.startLine, LiveMarkdown.positionAt(edit.raw, edit.caret))
            return
        }
        setBody(LiveMarkdown.replaceBlock(body, block, value.text))
        val start = LiveMarkdown.positionAt(value.text, value.selection.min)
        moveCaret(block.startLine, start, value.selection.length)
    }

    fun joinPrevious() {
        val (joined, position) = LiveMarkdown.joinWithPrevious(body, blocks, activeIndex) ?: return
        setBody(joined)
        caret = LiveCaret(position.line, position.col)
    }

    fun openBlock(block: LiveMarkdown.Block, offset: Int) {
        moveCaret(block.startLine, LiveMarkdown.positionAt(block.raw, offset))
    }

    fun applyFormat(action: FormatAction) {
        if (action == FormatAction.Done) {
            caret = null
            return
        }
        val block = active ?: return
        val edit = formatEdit(action, block.kind, block.raw, selection.min, selection.max)
        setBody(LiveMarkdown.replaceBlock(body, block, edit.text))
        moveCaret(block.startLine, LiveMarkdown.positionAt(edit.text, edit.start), edit.end - edit.start)
        // The bar took the caret to reach the button; give it back.
        runCatching { focusRequester.requestFocus() }
    }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 16.dp, bottom = 48.dp)
        ) {
            Eyebrow(note.meta.path, Modifier.padding(bottom = 10.dp))
            Text(
                note.meta.title,
                style = Skald.type.title,
                color = colors.tx0,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            FrontmatterTablet(note, parsed.frontmatter)

            if (mode == EditorMode.Live) {
                LiveBlocks(
                    blocks = blocks,
                    activeIndex = activeIndex,
                    selection = selection,
                    bodyStartLine = bodyStartLine,
                    fontSize = fontSize,
                    context = { block ->
                        baseCtx.copy(
                            tapAt = { shown ->
                                openBlock(block, LiveMarkdown.sourceOffsetFromRendered(block.raw, shown))
                            }
                        )
                    },
                    focusRequester = focusRequester,
                    onFieldChange = ::onFieldChange,
                    onJoinPrevious = ::joinPrevious,
                    onOpenBlock = ::openBlock,
                )
            } else {
                MarkdownView(remember(body, bodyStartLine) { Markdown.parse(body, bodyStartLine) }, baseCtx)
            }

            if (note.backlinks.isNotEmpty()) {
                Hairline(Modifier.padding(top = 22.dp))
                Column(Modifier.padding(top = 16.dp)) {
                    SectionHeader(
                        "Linked from",
                        "${note.backlinks.size} ${if (note.backlinks.size == 1) "note" else "notes"}",
                    )
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

        if (active != null) FormatBar(::applyFormat)
    }
}

/**
 * One toolbar press, as an edit to a string and a selection. A line break is the
 * odd one out — it belongs to the block rules rather than to the marks, because
 * only they know that a break inside a fence is a plain newline.
 */
private fun formatEdit(
    action: FormatAction,
    kind: LiveMarkdown.Kind,
    raw: String,
    start: Int,
    end: Int,
): Formatting.Edit = when (action) {
    FormatAction.Bold -> Formatting.toggleMark(raw, start, end, Formatting.Mark.Bold)
    FormatAction.Italic -> Formatting.toggleMark(raw, start, end, Formatting.Mark.Italic)
    FormatAction.Strike -> Formatting.toggleMark(raw, start, end, Formatting.Mark.Strike)
    FormatAction.Code -> Formatting.toggleMark(raw, start, end, Formatting.Mark.Code)
    FormatAction.Link -> Formatting.link(raw, start, end)
    FormatAction.Wikilink -> Formatting.wikilink(raw, start, end)
    FormatAction.Heading -> Formatting.cycleHeading(raw, start, end)
    FormatAction.Bullet -> Formatting.toggleLine(raw, start, end, Formatting.LineStyle.Bullet)
    FormatAction.Numbered -> Formatting.toggleLine(raw, start, end, Formatting.LineStyle.Numbered)
    FormatAction.Task -> Formatting.toggleLine(raw, start, end, Formatting.LineStyle.Task)
    FormatAction.Quote -> Formatting.toggleLine(raw, start, end, Formatting.LineStyle.Quote)
    FormatAction.Fence -> Formatting.fence(raw, start, end)
    FormatAction.Rule -> Formatting.rule(raw, start, end)
    FormatAction.Break -> LiveMarkdown.softBreak(kind, raw, start).let { Formatting.Edit(it.raw, it.caret, it.caret) }
    FormatAction.Done -> Formatting.Edit(raw, start, end)
}

/**
 * Typed frontmatter as a single sunken block labelled at the seam, rather than
 * YAML you have to read. Parsed from the draft rather than from the index, so it
 * stays honest while the note is being written.
 */
@Composable
private fun FrontmatterTablet(note: NotePayload, frontmatter: Map<String, Any?>) {
    val colors = Skald.colors
    if (frontmatter.isEmpty()) return

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
            for ((key, value) in frontmatter) {
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

/** Raw Markdown, autosaved, with the same bar on the keyboard. */
@Composable
private fun SourceEditor(
    content: String,
    path: String,
    fontSize: Int,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    val focus = LocalFocusManager.current
    val requester = remember(path) { FocusRequester() }
    // Re-taken whenever the note or the mode changes, and owned here in between:
    // the source view is one long field, so its selection is its own business.
    var value by remember(path) { mutableStateOf(TextFieldValue(content)) }

    fun press(action: FormatAction) {
        if (action == FormatAction.Done) {
            focus.clearFocus()
            return
        }
        val edit = formatEdit(action, LiveMarkdown.Kind.Paragraph, value.text, value.selection.min, value.selection.max)
        value = TextFieldValue(edit.text, TextRange(edit.start, edit.end))
        onChange(edit.text)
        // The bar took the caret to reach the button; give it back, or the next
        // press would be aimed at a field nobody is in.
        runCatching { requester.requestFocus() }
    }

    Column(modifier.fillMaxSize()) {
        Eyebrow("Source · $path", Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
        Hairline()
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                onChange(it.text)
            },
            textStyle = Skald.type.code.copy(color = colors.tx1, fontSize = (fontSize - 2).sp),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(requester)
                .padding(18.dp),
        )
        FormatBar(::press)
    }
}
