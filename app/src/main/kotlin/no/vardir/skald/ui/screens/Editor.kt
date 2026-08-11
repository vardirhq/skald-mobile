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
import no.vardir.skald.core.text.Dates
import no.vardir.skald.core.text.Formatting
import no.vardir.skald.core.text.Frontmatter
import no.vardir.skald.core.text.LiveMarkdown
import no.vardir.skald.core.text.Markdown
import no.vardir.skald.core.text.Suggest
import no.vardir.skald.core.text.Tasks
import no.vardir.skald.core.text.Wikilinks
import no.vardir.skald.ui.EditorMode
import no.vardir.skald.ui.components.Eyebrow
import no.vardir.skald.ui.components.Hairline
import no.vardir.skald.ui.components.MarkdownContext
import no.vardir.skald.ui.components.MarkdownView
import no.vardir.skald.ui.components.Rune
import no.vardir.skald.ui.components.SectionHeader
import no.vardir.skald.ui.components.SuggestionBar
import no.vardir.skald.ui.components.ThreadHintBar
import no.vardir.skald.ui.theme.Skald

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    note: NotePayload,
    snapshot: VaultSnapshot,
    todayIso: String,
    mode: EditorMode,
    onOpenNote: (String) -> Unit,
    onSave: (String) -> Unit,
    onDraftChanged: (String, String?) -> Unit,
    onOpenExternal: (String) -> Unit,
    onOpenAttachment: (AttachmentRef) -> Unit,
    onNoteMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    val fontSize = snapshot.settings.editorFontSize
    var draft by remember(note.meta.path) { mutableStateOf<String?>(null) }
    val content = draft ?: note.content

    LaunchedEffect(note.content) { if (draft == note.content) draft = null }
    LaunchedEffect(draft, note.meta.path) { onDraftChanged(note.meta.path, draft) }
    LaunchedEffect(content) {
        if (content != note.content) {
            delay(snapshot.settings.autosaveMs.toLong())
            onSave(content)
        }
    }

    val parsed = remember(content) { Frontmatter.parse(content) }
    val body = parsed.body
    val bodyStartLine = parsed.bodyStartLine
    fun setBody(next: String) { draft = LiveMarkdown.replaceBody(content, bodyStartLine, next) }

    val linkIndex = remember(snapshot.notes) {
        Wikilinks.buildIndex(snapshot.notes.map { Wikilinks.Linkable(it.path, it.title) })
    }
    val vocabulary = remember(snapshot.notes, snapshot.tasks, todayIso) {
        Suggest.Vocabulary(
            notes = snapshot.notes.map { Suggest.NoteRef(it.path, it.title, it.schema, it.updated) },
            tags = (snapshot.notes.flatMap { it.tags } + snapshot.tasks.flatMap { it.tags }).distinct().sorted(),
            todayIso = todayIso,
            index = linkIndex,
        )
    }
    val knownTags = remember(vocabulary) { vocabulary.tags }

    fun toggleTask(line: Int, done: Boolean) {
        setBody(Tasks.updateLine(body, line - bodyStartLine, Tasks.Edits(status = if (done) TaskStatus.Done else TaskStatus.Open)))
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
            frontmatter = parsed.frontmatter,
        )
    }

    var properties by remember(note.meta.path) { mutableStateOf(false) }
    if (properties) {
        PropertiesSheet(
            path = note.meta.path,
            frontmatter = parsed.frontmatter,
            schema = note.meta.schema,
            knownTags = knownTags,
            onApply = { changes, remove ->
                draft = Frontmatter.apply(content, changes, remove)
                properties = false
            },
            onDismiss = { properties = false },
        )
    }

    if (mode == EditorMode.Source) {
        SourceEditor(
            content = content,
            path = note.meta.path,
            fontSize = fontSize,
            vocabulary = vocabulary,
            onChange = { draft = it },
            modifier = modifier,
        )
        return
    }

    var caret by remember(note.meta.path) { mutableStateOf<LiveCaret?>(null) }
    LaunchedEffect(mode) { if (mode != EditorMode.Live) caret = null }
    val blocks = remember(body) { LiveMarkdown.split(body) }
    val here = caret
    val activeIndex = if (mode == EditorMode.Live && here != null) LiveMarkdown.blockAt(blocks, here.line) else -1
    val active = blocks.getOrNull(activeIndex)
    val selection = if (active != null && here != null) {
        val start = LiveMarkdown.offsetAt(active.raw, here.line - active.startLine, here.col)
        TextRange(start, (start + here.length).coerceIn(start, active.raw.length))
    } else TextRange.Zero
    val focusRequester = remember(note.meta.path) { FocusRequester() }

    fun moveCaret(startLine: Int, edit: LiveMarkdown.Position, length: Int = 0) {
        caret = LiveCaret(startLine + edit.line, edit.col, length)
    }

    fun onFieldChange(value: TextFieldValue) {
        val block = active ?: return
        val newline = LiveMarkdown.insertedNewline(block.raw, value.text, value.selection.start)
        if (newline != null) {
            val edit = LiveMarkdown.enter(block.kind, block.raw, newline)
            setBody(LiveMarkdown.replaceBlock(body, block, edit.raw))
            moveCaret(block.startLine, LiveMarkdown.positionAt(edit.raw, edit.caret))
            return
        }
        val closed = Suggest.autoClose(block.raw, value.text, value.selection.start)
        val text = closed?.text ?: value.text
        val at = closed?.start ?: value.selection.min
        setBody(LiveMarkdown.replaceBlock(body, block, text))
        moveCaret(block.startLine, LiveMarkdown.positionAt(text, at), if (closed != null) 0 else value.selection.length)
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
        runCatching { focusRequester.requestFocus() }
    }

    val trigger = remember(active?.raw, selection.min, activeIndex) { active?.let { Suggest.triggerAt(it.raw, selection.min) } }
    val offers = remember(trigger, vocabulary) { trigger?.let { Suggest.candidates(it, vocabulary) } ?: emptyList() }
    fun applySuggestion(candidate: Suggest.Candidate) {
        val block = active ?: return
        val where = trigger ?: return
        val edit = Suggest.accept(block.raw, where, candidate)
        setBody(LiveMarkdown.replaceBlock(body, block, edit.text))
        moveCaret(block.startLine, LiveMarkdown.positionAt(edit.text, edit.start))
        runCatching { focusRequester.requestFocus() }
    }

    val threadLine = if (active != null && here != null) here.line else -1
    val thread = remember(active?.raw, threadLine) {
        val block = active ?: return@remember null
        val line = block.raw.split("\n").getOrNull(threadLine - block.startLine) ?: return@remember null
        Tasks.parseLine(line)
    }
    var threadSheet by remember(note.meta.path) { mutableStateOf(false) }

    if (threadSheet && thread != null) {
        ThreadSheet(
            target = ThreadTarget(
                notePath = note.meta.path,
                noteTitle = note.meta.title,
                line = threadLine + 1,
                content = thread.content,
                status = thread.status,
                priority = thread.priority,
                due = thread.due,
                tags = thread.tags,
            ),
            knownTags = knownTags,
            todayIso = todayIso,
            onApply = { edits ->
                setBody(Tasks.updateLine(body, threadLine + 1, edits))
                caret = LiveCaret(threadLine, 0)
            },
            onOpenNote = null,
            onDismiss = { threadSheet = false },
        )
    }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 16.dp, bottom = 48.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Note options") { onNoteMenu() }.padding(bottom = 12.dp),
            ) {
                Eyebrow(note.meta.path, Modifier.padding(bottom = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(note.meta.title, style = Skald.type.title, color = colors.tx0, modifier = Modifier.weight(1f))
                    Text("⋯", style = Skald.type.title, color = colors.tx4)
                }
            }

            FrontmatterTablet(note, parsed.frontmatter) { properties = true }

            if (mode == EditorMode.Live) {
                LiveBlocks(
                    blocks = blocks,
                    activeIndex = activeIndex,
                    selection = selection,
                    bodyStartLine = bodyStartLine,
                    fontSize = fontSize,
                    context = { block -> baseCtx.copy(tapAt = { shown -> openBlock(block, LiveMarkdown.sourceOffsetFromRendered(block.raw, shown)) }) },
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
                    SectionHeader("Linked from", "${note.backlinks.size} ${if (note.backlinks.size == 1) "note" else "notes"}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (link in note.backlinks) {
                            Row(
                                Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(18.dp)).background(colors.bg1)
                                    .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(18.dp))
                                    .clickable { onOpenNote(link.path) }.padding(horizontal = 11.dp, vertical = 6.dp),
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

        if (active != null) {
            Column {
                when {
                    offers.isNotEmpty() -> SuggestionBar(candidates = offers, onPick = ::applySuggestion)
                    thread != null -> ThreadHintBar(summary = threadSummary(thread, todayIso), onOpen = { threadSheet = true })
                    else -> Unit
                }
                FormatBar(::applyFormat)
            }
        }
    }
}

private fun threadSummary(task: Tasks.RawTask, todayIso: String): String = buildList {
    add(task.due?.let { "due ${Dates.label(it, todayIso)}" } ?: "no date")
    add(task.priority.token)
    if (task.status != TaskStatus.Open && task.status != TaskStatus.Done) add(task.status.token)
    if (task.tags.isNotEmpty()) add(task.tags.joinToString(" ") { "#$it" })
}.joinToString(" · ")

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

@Composable
private fun FrontmatterTablet(note: NotePayload, frontmatter: Map<String, Any?>, onOpen: () -> Unit) {
    val colors = Skald.colors
    Column(
        Modifier.fillMaxWidth().padding(bottom = 22.dp).clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "Edit properties", onClick = onOpen),
    ) {
        Row(
            Modifier.padding(start = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Rune(note.meta.schema, 13.dp)
            Eyebrow("schema · ${note.meta.schema.name}", color = colors.tx3, modifier = Modifier.weight(1f))
            Eyebrow(if (frontmatter.isEmpty()) "add properties ›" else "edit ›", color = colors.accent)
        }
        if (frontmatter.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.bg1)
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
                                if (key == "schema") withStyle(SpanStyle(color = colors.accent)) { append(text) } else append(text)
                            },
                            style = Skald.type.small,
                            color = colors.tx1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceEditor(
    content: String,
    path: String,
    fontSize: Int,
    vocabulary: Suggest.Vocabulary,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    val focus = LocalFocusManager.current
    val requester = remember(path) { FocusRequester() }
    var value by remember(path) { mutableStateOf(TextFieldValue(content)) }

    val trigger = remember(value.text, value.selection.min) { Suggest.triggerAt(value.text, value.selection.min) }
    val offers = remember(trigger, vocabulary) { trigger?.let { Suggest.candidates(it, vocabulary) } ?: emptyList() }

    fun put(edit: Formatting.Edit) {
        value = TextFieldValue(edit.text, TextRange(edit.start, edit.end))
        onChange(edit.text)
        runCatching { requester.requestFocus() }
    }

    fun press(action: FormatAction) {
        if (action == FormatAction.Done) {
            focus.clearFocus()
            return
        }
        put(formatEdit(action, LiveMarkdown.Kind.Paragraph, value.text, value.selection.min, value.selection.max))
    }

    Column(modifier.fillMaxSize()) {
        Eyebrow("Source · $path", Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
        Hairline()
        BasicTextField(
            value = value,
            onValueChange = { next ->
                val closed = Suggest.autoClose(value.text, next.text, next.selection.start)
                if (closed != null) {
                    value = TextFieldValue(closed.text, TextRange(closed.start))
                    onChange(closed.text)
                } else {
                    value = next
                    onChange(next.text)
                }
            },
            textStyle = Skald.type.code.copy(color = colors.tx1, fontSize = (fontSize - 2).sp),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.weight(1f).fillMaxWidth().focusRequester(requester).padding(18.dp),
        )
        if (offers.isNotEmpty() && trigger != null) {
            SuggestionBar(candidates = offers, onPick = { candidate -> put(Suggest.accept(value.text, trigger, candidate)) })
        }
        FormatBar(::press)
    }
}
