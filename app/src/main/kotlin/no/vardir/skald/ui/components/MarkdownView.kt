package no.vardir.skald.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.vardir.skald.core.model.AttachmentRef
import no.vardir.skald.core.model.NoteThemeSpec
import no.vardir.skald.core.model.TaskStatus
import no.vardir.skald.core.text.Markdown
import no.vardir.skald.ui.extensions.builtInExtensionRegistry
import no.vardir.skald.ui.theme.NoteThemeSurface
import no.vardir.skald.ui.theme.Skald

data class MarkdownContext(
    val resolve: (String) -> String?,
    val openNote: (String) -> Unit,
    val openExternal: (String) -> Unit,
    val resolveAttachment: (String) -> AttachmentRef?,
    val openAttachment: (AttachmentRef) -> Unit,
    val toggleTask: (Int, Boolean) -> Unit,
    val todayIso: String,
    val frontmatter: Map<String, Any?> = emptyMap(),
    val noteTheme: NoteThemeSpec? = null,
    val tapAt: ((String) -> Unit)? = null,
)

private const val TAG_WIKILINK = "wikilink"
private const val TAG_LINK = "link"

@Composable
fun MarkdownView(blocks: List<Markdown.Block>, ctx: MarkdownContext, modifier: Modifier = Modifier) {
    NoteThemeSurface(ctx.noteTheme, modifier) {
        MarkdownBlocks(blocks, ctx)
    }
}

@Composable
private fun MarkdownBlocks(blocks: List<Markdown.Block>, ctx: MarkdownContext, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        var stanza = 0
        for (block in blocks) {
            when (block) {
                is Markdown.Block.Heading -> {
                    if (block.level == 2) stanza++
                    HeadingBlock(block, if (block.level == 2) stanza else null, ctx)
                }
                is Markdown.Block.Paragraph -> ParagraphBlock(block, ctx)
                is Markdown.Block.Code -> {
                    val renderer = block.lang?.let(builtInExtensionRegistry::codeFence)
                    if (renderer != null) renderer.render(block) else CodeBlock(block)
                }
                is Markdown.Block.Quote -> QuoteBlock(block, ctx)
                is Markdown.Block.Callout -> CalloutBlock(block, ctx)
                Markdown.Block.Rule -> RuleBlock()
                is Markdown.Block.Tasks -> TasksBlock(block, ctx)
                is Markdown.Block.Bullets -> ListBlock(block.items, ordered = false, ctx = ctx)
                is Markdown.Block.Numbers -> ListBlock(block.items, ordered = true, ctx = ctx)
                is Markdown.Block.Table -> TableBlock(block, ctx)
                is Markdown.Block.Container -> SemanticContainerBlock(block, ctx)
            }
        }
    }
}

@Composable
private fun SemanticContainerBlock(block: Markdown.Block.Container, ctx: MarkdownContext) {
    val colors = Skald.colors
    when (block.kind) {
        Markdown.ContainerKind.Aside -> Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 18.dp)
                .clip(RoundedCornerShape(Skald.metrics.r3))
                .background(colors.accentGhost)
                .border(BorderStroke(1.dp, colors.accentLine), RoundedCornerShape(Skald.metrics.r3))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Eyebrow("aside", Modifier.padding(bottom = 8.dp), colors.accent)
            MarkdownBlocks(block.children, ctx)
        }

        Markdown.ContainerKind.Gallery -> Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            Eyebrow("gallery", Modifier.padding(bottom = 8.dp), colors.tx3)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                block.children.forEach { child ->
                    Box(
                        Modifier
                            .width(260.dp)
                            .clip(RoundedCornerShape(Skald.metrics.r3))
                            .background(colors.bg1)
                            .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(Skald.metrics.r3))
                            .padding(12.dp)
                    ) { MarkdownBlocks(listOf(child), ctx) }
                }
            }
        }

        Markdown.ContainerKind.Group -> Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp)
                .clip(RoundedCornerShape(Skald.metrics.card - 2.dp))
                .border(BorderStroke(1.dp, colors.line2), RoundedCornerShape(Skald.metrics.card - 2.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) { MarkdownBlocks(block.children, ctx) }
    }
}

@Composable
private fun HeadingBlock(block: Markdown.Block.Heading, stanza: Int?, ctx: MarkdownContext) {
    val colors = Skald.colors
    when (block.level) {
        1 -> LinkedText(annotate(block.content, ctx), ctx, Skald.type.title, colors.tx0, Modifier.padding(top = 18.dp, bottom = 12.dp))
        2 -> Row(
            Modifier.padding(top = 26.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stanza?.let(::roman) ?: "##", style = Skald.type.metaSmall, color = colors.accent.copy(alpha = 0.7f))
            LinkedText(annotate(block.content, ctx), ctx, Skald.type.heading, colors.tx0)
        }
        else -> LinkedText(
            annotate(block.content, ctx), ctx,
            Skald.type.row.copy(fontWeight = FontWeight.SemiBold), colors.tx1,
            Modifier.padding(top = 18.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun ParagraphBlock(block: Markdown.Block.Paragraph, ctx: MarkdownContext) {
    val images = block.content.filterIsInstance<Markdown.Inline.Image>()
    LinkedText(
        text = annotate(block.content, ctx), ctx = ctx, style = Skald.type.body,
        color = Skald.colors.tx1, modifier = Modifier.padding(bottom = 16.dp),
    )
    for (image in images) {
        val ref = ctx.resolveAttachment(image.target)
        AttachmentCard(ref, image.alt.ifEmpty { image.target }, ctx)
    }
}

@Composable
private fun CodeBlock(block: Markdown.Block.Code) {
    val colors = Skald.colors
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp)
            .clip(RoundedCornerShape(Skald.metrics.r3)).background(colors.bg1)
            .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(Skald.metrics.r3)).padding(12.dp),
    ) {
        block.lang?.let { Eyebrow(it, Modifier.padding(bottom = 6.dp), colors.tx3) }
        Text(block.text, style = Skald.type.code, color = colors.tx1,
            modifier = Modifier.horizontalScroll(rememberScrollState()), softWrap = false)
    }
}

@Composable
private fun QuoteBlock(block: Markdown.Block.Quote, ctx: MarkdownContext) {
    val colors = Skald.colors
    Row(Modifier.padding(top = 6.dp, bottom = 18.dp).height(IntrinsicSize.Min)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(colors.accent))
        LinkedText(annotate(block.content, ctx), ctx, Skald.type.body.copy(fontStyle = FontStyle.Italic),
            colors.tx2, Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun CalloutBlock(block: Markdown.Block.Callout, ctx: MarkdownContext) {
    val component = builtInExtensionRegistry.component(block.label)
    if (component != null) { component.render(block, ctx); return }
    val colors = Skald.colors
    Column(
        Modifier.fillMaxWidth().padding(bottom = 18.dp)
            .clip(RoundedCornerShape(Skald.metrics.card - 2.dp)).background(colors.accentGhost)
            .border(BorderStroke(1.dp, colors.accentLine), RoundedCornerShape(Skald.metrics.card - 2.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Eyebrow(block.label, Modifier.padding(bottom = 6.dp), colors.accent)
        LinkedText(annotate(block.content, ctx), ctx, Skald.type.row, colors.tx1)
    }
}

@Composable
private fun RuleBlock() { Box(Modifier.fillMaxWidth().padding(vertical = 20.dp)) { Hairline() } }

@Composable
private fun TasksBlock(block: Markdown.Block.Tasks, ctx: MarkdownContext) {
    val colors = Skald.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        for (task in block.items) {
            val done = task.status == TaskStatus.Done
            val due = task.due
            val overdue = due != null && due < ctx.todayIso && !done
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                SkaldCheckbox(
                    checked = done, onCheckedChange = { ctx.toggleTask(task.line, it) },
                    tint = when (task.status) {
                        TaskStatus.Working -> colors.blue
                        TaskStatus.Blocked -> colors.err
                        else -> colors.accent
                    },
                )
                Column(Modifier.weight(1f)) {
                    LinkedText(
                        annotate(task.content, ctx), ctx,
                        Skald.type.row.copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                        if (done) colors.tx3 else colors.tx1,
                    )
                    val meta = buildList {
                        if (task.status == TaskStatus.Working) add("working")
                        if (task.status == TaskStatus.Blocked) add("blocked")
                        task.due?.let { add(if (overdue) "overdue · ${formatDue(it)}" else formatDue(it)) }
                    }
                    if (meta.isNotEmpty()) Text(
                        meta.joinToString(" · "), style = Skald.type.meta,
                        color = when {
                            overdue || task.status == TaskStatus.Blocked -> colors.err
                            task.status == TaskStatus.Working -> colors.blue
                            else -> colors.tx3
                        }, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListBlock(items: List<List<Markdown.Inline>>, ordered: Boolean, ctx: MarkdownContext) {
    val colors = Skald.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        items.forEachIndexed { i, item ->
            Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (ordered) "${i + 1}." else "·", style = Skald.type.meta, color = colors.tx3, modifier = Modifier.width(20.dp))
                LinkedText(annotate(item, ctx), ctx, Skald.type.body, colors.tx1, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TableBlock(block: Markdown.Block.Table, ctx: MarkdownContext) {
    val colors = Skald.colors
    Column(
        Modifier.fillMaxWidth().padding(bottom = 18.dp).horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(Skald.metrics.r3))
            .border(BorderStroke(1.dp, colors.line2), RoundedCornerShape(Skald.metrics.r3)),
    ) {
        TableRow(block.headers, header = true, ctx = ctx)
        block.rows.forEach { TableRow(it, header = false, ctx = ctx) }
    }
}

@Composable
private fun TableRow(cells: List<List<Markdown.Inline>>, header: Boolean, ctx: MarkdownContext) {
    val colors = Skald.colors
    Row(Modifier.width(IntrinsicSize.Max).height(IntrinsicSize.Min)) {
        cells.forEachIndexed { index, cell ->
            Box(
                Modifier.width(144.dp).fillMaxHeight().background(if (header) colors.bg1 else Color.Transparent)
                    .then(if (index > 0) Modifier.border(BorderStroke(0.5.dp, colors.line)) else Modifier)
                    .padding(horizontal = 11.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                LinkedText(
                    annotate(cell, ctx), ctx,
                    if (header) Skald.type.small.copy(fontWeight = FontWeight.SemiBold) else Skald.type.small,
                    if (header) colors.tx0 else colors.tx2,
                )
            }
        }
    }
    Hairline()
}

@Composable
private fun AttachmentCard(ref: AttachmentRef?, label: String, ctx: MarkdownContext) {
    val colors = Skald.colors
    val usable = ref?.exists == true
    SkaldCard(modifier = Modifier.padding(bottom = 16.dp), onClick = if (usable) ({ ctx.openAttachment(ref) }) else null) {
        Text(
            when (ref?.kind) {
                no.vardir.skald.core.model.AttachmentKind.Image -> "▧"
                no.vardir.skald.core.model.AttachmentKind.Pdf -> "PDF"
                no.vardir.skald.core.model.AttachmentKind.Audio -> "♫"
                no.vardir.skald.core.model.AttachmentKind.Video -> "▶"
                else -> "◇"
            },
            style = Skald.type.meta, color = if (usable) colors.accent else colors.tx4,
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = Skald.type.row, color = colors.tx0)
            Text(if (usable) ref.kind.name.lowercase() else "missing file", style = Skald.type.meta,
                color = if (usable) colors.tx3 else colors.err)
        }
    }
}

@Composable
private fun annotate(inlines: List<Markdown.Inline>, ctx: MarkdownContext): AnnotatedString {
    val colors = Skald.colors
    val codeFamily = Skald.type.code.fontFamily
    return buildAnnotatedString {
        fun walk(nodes: List<Markdown.Inline>) {
            for (node in nodes) when (node) {
                is Markdown.Inline.Text -> append(node.text)
                is Markdown.Inline.Code -> withStyle(SpanStyle(fontFamily = codeFamily, fontSize = 13.sp,
                    color = colors.orange, background = colors.bg1)) { append(node.text) }
                is Markdown.Inline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.tx0)) { walk(node.children) }
                is Markdown.Inline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = colors.tx0)) { walk(node.children) }
                is Markdown.Inline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = colors.tx3)) { walk(node.children) }
                is Markdown.Inline.Wikilink -> {
                    val path = ctx.resolve(node.target)
                    pushStringAnnotation(TAG_WIKILINK, path ?: "")
                    withStyle(SpanStyle(color = if (path != null) colors.accent else colors.tx3,
                        textDecoration = TextDecoration.Underline)) { append(node.display) }
                    pop()
                }
                is Markdown.Inline.Link -> {
                    pushStringAnnotation(TAG_LINK, node.url)
                    withStyle(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)) { walk(node.label) }
                    pop()
                }
                is Markdown.Inline.Image -> Unit
            }
        }
        walk(inlines)
    }
}

@Composable
private fun LinkedText(
    text: AnnotatedString,
    ctx: MarkdownContext,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val tapAt = ctx.tapAt
    if (tapAt == null) {
        val firstWikilink = text.getStringAnnotations(TAG_WIKILINK, 0, text.length).firstOrNull { it.item.isNotEmpty() }
        val firstLink = text.getStringAnnotations(TAG_LINK, 0, text.length).firstOrNull()
        Text(
            text, style = style, color = color,
            modifier = when {
                firstWikilink != null -> modifier.clickable { ctx.openNote(firstWikilink.item) }
                firstLink != null -> modifier.clickable { ctx.openExternal(firstLink.item) }
                else -> modifier
            },
        )
        return
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text, style = style, color = color, onTextLayout = { layout = it },
        modifier = modifier.pointerInput(text, tapAt) {
            detectTapGestures { position ->
                val offset = layout?.getOffsetForPosition(position)?.coerceIn(0, text.length) ?: text.length
                val probe = (offset + 1).coerceAtMost(text.length)
                val wikilink = text.getStringAnnotations(TAG_WIKILINK, offset, probe).firstOrNull { it.item.isNotEmpty() }
                val link = text.getStringAnnotations(TAG_LINK, offset, probe).firstOrNull()
                when {
                    wikilink != null -> ctx.openNote(wikilink.item)
                    link != null -> ctx.openExternal(link.item)
                    else -> tapAt(text.text.substring(0, offset))
                }
            }
        },
    )
}

private fun roman(n: Int): String {
    val numerals = listOf(10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i")
    var value = n
    return buildString {
        for ((amount, symbol) in numerals) while (value >= amount) { append(symbol); value -= amount }
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun formatDue(due: String): String {
    val parts = due.split('-')
    if (parts.size != 3) return due
    val month = parts[1].toIntOrNull() ?: return due
    val day = parts[2].toIntOrNull() ?: return due
    return "$day ${MONTHS.getOrElse(month - 1) { parts[1] }}"
}
