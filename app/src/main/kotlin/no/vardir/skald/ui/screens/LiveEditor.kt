package no.vardir.skald.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.vardir.skald.core.text.LiveMarkdown
import no.vardir.skald.core.text.Markdown
import no.vardir.skald.ui.components.Hairline
import no.vardir.skald.ui.components.MarkdownContext
import no.vardir.skald.ui.components.MarkdownView
import no.vardir.skald.ui.theme.Skald

/**
 * Where the caret is in the body, and how much of it is selected.
 *
 * A line and column rather than an offset, because the block under the caret is
 * re-split on every keystroke and an offset into a block that no longer exists
 * would be nonsense. [length] is the selection, measured forward from there.
 */
data class LiveCaret(val line: Int, val col: Int, val length: Int = 0)

/**
 * The live editor, the desktop's arrangement re-housed for a thumb: the block
 * you are in is raw Markdown in a field, and every block around it stays
 * rendered. Tap a block to move in; tap a link to follow it instead.
 */
@Composable
fun LiveBlocks(
    blocks: List<LiveMarkdown.Block>,
    activeIndex: Int,
    selection: TextRange,
    bodyStartLine: Int,
    fontSize: Int,
    context: (LiveMarkdown.Block) -> MarkdownContext,
    focusRequester: FocusRequester,
    onFieldChange: (TextFieldValue) -> Unit,
    onJoinPrevious: () -> Unit,
    onOpenBlock: (LiveMarkdown.Block, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            when {
                // Keyed by a constant rather than by the block: the field has to
                // keep its identity — and so its focus and its IME session —
                // while the blocks under it are re-cut on every keystroke.
                index == activeIndex -> key("live-field") {
                    ActiveBlock(block, selection, fontSize, focusRequester, onFieldChange, onJoinPrevious)
                }

                // The gap between two blocks is deliberately not a tap target:
                // typing into it would splice over the blank line that keeps
                // its neighbours apart and fold them into one paragraph. The
                // way to put a paragraph between two others is Enter at the end
                // of the first, which is the gesture the block rules are built
                // around. Only the end of the note invites writing this way.
                block.kind == LiveMarkdown.Kind.Blank -> key(block.id) {
                    if (index == blocks.lastIndex) {
                        BlankBlock(alone = blocks.size == 1) { onOpenBlock(block, block.raw.length) }
                    }
                }

                else -> key(block.id) {
                    RenderedBlock(block, bodyStartLine, context(block)) { offset -> onOpenBlock(block, offset) }
                }
            }
        }
    }
}

/** The block being written: its Markdown, plainly, marked at the seam. */
@Composable
private fun ActiveBlock(
    block: LiveMarkdown.Block,
    selection: TextRange,
    fontSize: Int,
    focusRequester: FocusRequester,
    onChange: (TextFieldValue) -> Unit,
    onJoinPrevious: () -> Unit,
) {
    val colors = Skald.colors
    val code = block.kind == LiveMarkdown.Kind.Code
    val seam = 2.dp
    val value = TextFieldValue(block.raw, selection)

    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = (if (code) Skald.type.code else Skald.type.body).copy(
            color = colors.tx0,
            fontSize = (if (code) fontSize - 2 else fontSize).sp,
        ),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = if (code) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Default,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(Skald.metrics.r3))
            .background(colors.bg1)
            .drawBehind { drawRect(colors.accent, size = Size(seam.toPx(), size.height)) }
            .focusRequester(focusRequester)
            // Backspace at the very top of a block reaches into the one above
            // it: closing the gap between two blocks, or taking back a block
            // that was opened by accident. Nothing changes in the field itself,
            // so there is no edit to notice after the fact — this is the one
            // key that has to be caught as it is pressed.
            .onPreviewKeyEvent { event ->
                val atStart = value.selection.collapsed && value.selection.start == 0
                if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && atStart) {
                    onJoinPrevious()
                    true
                } else {
                    false
                }
            }
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    )

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
}

/**
 * The gap between two blocks, tappable so a paragraph can be started in it. It
 * stays invisible unless the whole note is empty, where the page needs to say
 * where writing begins.
 */
@Composable
private fun BlankBlock(alone: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (alone) 56.dp else 22.dp)
            .clickable(onClickLabel = "Write here", onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (alone) {
            Text("An empty page. Tap to begin.", style = Skald.type.body, color = Skald.colors.tx4)
        }
    }
}

/**
 * A block as the reader sees it. The tap that opens it for editing is the
 * fallback — [MarkdownContext.tapAt] aims it properly where the block is text,
 * and this catches everything else: a code listing, a rule, the padding.
 */
@Composable
private fun RenderedBlock(
    block: LiveMarkdown.Block,
    bodyStartLine: Int,
    ctx: MarkdownContext,
    onTap: (Int) -> Unit,
) {
    val parsed = remember(block.raw, bodyStartLine, block.startLine) {
        Markdown.parse(block.raw, bodyStartLine + block.startLine)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Skald.metrics.r3))
            .clickable(onClickLabel = "Edit this block") { onTap(block.raw.length) },
    ) {
        MarkdownView(parsed, ctx)
    }
}

// ---------- the formatting bar ----------

/**
 * What the bar above the keyboard can do. The glyphs are the design's own —
 * mono, quiet, and drawn in the mark they apply where the mark can be drawn.
 */
enum class FormatAction(val glyph: String, val label: String) {
    Heading("H", "Heading level"),
    Bold("B", "Bold"),
    Italic("I", "Italic"),
    Strike("S", "Strike through"),
    Code("`", "Code"),
    Wikilink("[[ ]]", "Link to a note"),
    Link("↗", "Web link"),
    Bullet("•", "Bullets"),
    Numbered("1.", "Numbers"),
    Task("☐", "Thread"),
    Quote("❝", "Quote"),
    Fence("```", "Code block"),
    Rule("—", "Divider"),
    Break("↵", "Line break"),
    Done("✓", "Done"),
}

private val MARKS = listOf(
    FormatAction.Heading,
    FormatAction.Bold,
    FormatAction.Italic,
    FormatAction.Strike,
    FormatAction.Code,
    FormatAction.Wikilink,
    FormatAction.Link,
    FormatAction.Bullet,
    FormatAction.Numbered,
    FormatAction.Task,
    FormatAction.Quote,
    FormatAction.Fence,
    FormatAction.Rule,
    FormatAction.Break,
)

/**
 * The bar that sits on the keyboard. Everything on it is a toggle, so the way
 * out of a mark is the button that put it on — which is the only arrangement
 * that works when the alternative is placing a caret between two asterisks.
 */
@Composable
fun FormatBar(onAction: (FormatAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = Skald.colors
    Column(modifier.fillMaxWidth().background(colors.bg1)) {
        Hairline()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (action in MARKS) FormatButton(action, onAction)
            }
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(colors.accentGhost)
                    .border(BorderStroke(1.dp, colors.accentLine), RoundedCornerShape(9.dp))
                    .clickable(onClickLabel = FormatAction.Done.label) { onAction(FormatAction.Done) }
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            ) {
                Text(FormatAction.Done.glyph, style = Skald.type.meta, color = colors.accent)
            }
        }
    }
}

@Composable
private fun FormatButton(action: FormatAction, onAction: (FormatAction) -> Unit) {
    val colors = Skald.colors
    Box(
        Modifier
            .defaultMinSize(minWidth = 42.dp, minHeight = 40.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(role = Role.Button, onClickLabel = action.label) { onAction(action) }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            action.glyph,
            style = Skald.type.meta.copy(
                fontSize = 14.sp,
                fontWeight = if (action == FormatAction.Bold) FontWeight.Bold else null,
                fontStyle = if (action == FormatAction.Italic) FontStyle.Italic else null,
                textDecoration = if (action == FormatAction.Strike) TextDecoration.LineThrough else null,
            ),
            color = colors.tx2,
        )
    }
}
