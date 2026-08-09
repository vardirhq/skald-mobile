package no.vardir.skald.core.text

import no.vardir.skald.core.model.TaskPriority
import no.vardir.skald.core.model.TaskStatus

/**
 * Markdown → a small block/inline tree.
 *
 * Markdown stays the storage format; this is the reading surface. Parsing lives
 * here rather than in the UI so the renderer stays a pure function of a tree,
 * and so the block rules can be tested without a device attached.
 */
object Markdown {

    sealed interface Inline {
        data class Text(val text: String) : Inline
        data class Code(val text: String) : Inline
        data class Strong(val children: List<Inline>) : Inline
        data class Emphasis(val children: List<Inline>) : Inline
        data class Strike(val children: List<Inline>) : Inline

        /** `[[Target#Heading|display]]`. Resolution happens at render time. */
        data class Wikilink(val target: String, val heading: String?, val display: String) : Inline
        data class Link(val label: List<Inline>, val url: String) : Inline
        data class Image(val alt: String, val target: String) : Inline
    }

    data class TaskLine(
        /** 1-based line in the raw file, so a tap can rewrite exactly that line. */
        val line: Int,
        val content: List<Inline>,
        val status: TaskStatus,
        val priority: TaskPriority,
        val due: String?,
        val tags: List<String>,
    )

    sealed interface Block {
        data class Heading(val level: Int, val content: List<Inline>, val line: Int) : Block
        data class Paragraph(val content: List<Inline>) : Block
        data class Code(val lang: String?, val text: String) : Block
        data class Quote(val content: List<Inline>) : Block

        /** `> [!Premise] …` — the design's accented callout. */
        data class Callout(val label: String, val content: List<Inline>) : Block
        data object Rule : Block
        data class Tasks(val items: List<TaskLine>) : Block
        data class Bullets(val items: List<List<Inline>>) : Block
        data class Numbers(val items: List<List<Inline>>) : Block
    }

    private val TASK_LINE = Regex("""^\s*[-*+]\s+\[[ xX]]\s+""")
    private val UL_LINE = Regex("""^\s*[-*+]\s+(?!\[[ xX]]\s)""")
    private val OL_LINE = Regex("""^\s*\d+[.)]\s+""")
    private val FENCE = Regex("""^\s*```(\w*)""")
    private val CLOSING_FENCE = Regex("""^\s*```""")
    private val HEADING = Regex("""^(#{1,6})\s+(.+?)\s*$""")
    private val RULE = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")
    private val QUOTE = Regex("""^\s*>""")
    private val CALLOUT = Regex("""^\[!(\w+)]\s*(.*)$""")

    /**
     * @param lineOffset line index of the body within the raw file, so task line
     *   numbers point at the right line of the file on disk.
     */
    fun parse(body: String, lineOffset: Int = 0): List<Block> {
        val lines = body.split("\n")
        val out = mutableListOf<Block>()
        var i = 0

        fun startsBlock(line: String): Boolean =
            line.isBlank() ||
                HEADING.containsMatchIn(line) ||
                QUOTE.containsMatchIn(line) ||
                CLOSING_FENCE.containsMatchIn(line) ||
                TASK_LINE.containsMatchIn(line) ||
                UL_LINE.containsMatchIn(line) ||
                OL_LINE.containsMatchIn(line) ||
                RULE.matches(line)

        while (i < lines.size) {
            val line = lines[i]

            if (line.isBlank()) {
                i++
                continue
            }

            val fence = FENCE.find(line)
            if (fence != null) {
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !CLOSING_FENCE.containsMatchIn(lines[i])) {
                    code += lines[i]
                    i++
                }
                i++ // closing fence, if there was one
                out += Block.Code(fence.groupValues[1].ifEmpty { null }, code.joinToString("\n"))
                continue
            }

            val heading = HEADING.find(line)
            if (heading != null) {
                out += Block.Heading(
                    level = heading.groupValues[1].length,
                    content = inline(heading.groupValues[2]),
                    line = i + 1 + lineOffset,
                )
                i++
                continue
            }

            if (RULE.matches(line)) {
                out += Block.Rule
                i++
                continue
            }

            if (QUOTE.containsMatchIn(line)) {
                val quoted = mutableListOf<String>()
                while (i < lines.size && QUOTE.containsMatchIn(lines[i])) {
                    quoted += lines[i].replace(Regex("""^\s*>\s?"""), "")
                    i++
                }
                val callout = quoted.firstOrNull()?.let { CALLOUT.find(it) }
                out += if (callout != null) {
                    val rest = (listOf(callout.groupValues[2]) + quoted.drop(1)).joinToString(" ").trim()
                    Block.Callout(callout.groupValues[1], inline(rest))
                } else {
                    Block.Quote(inline(quoted.joinToString(" ")))
                }
                continue
            }

            if (TASK_LINE.containsMatchIn(line)) {
                val start = i
                while (i < lines.size && TASK_LINE.containsMatchIn(lines[i])) i++
                val chunk = lines.subList(start, i).joinToString("\n")
                out += Block.Tasks(
                    Tasks.extract(chunk, start + lineOffset).map {
                        TaskLine(it.line, inline(it.content), it.status, it.priority, it.due, it.tags)
                    }
                )
                continue
            }

            if (UL_LINE.containsMatchIn(line)) {
                val items = mutableListOf<List<Inline>>()
                while (i < lines.size && UL_LINE.containsMatchIn(lines[i])) {
                    items += inline(lines[i].replace(Regex("""^\s*[-*+]\s+"""), ""))
                    i++
                }
                out += Block.Bullets(items)
                continue
            }

            if (OL_LINE.containsMatchIn(line)) {
                val items = mutableListOf<List<Inline>>()
                while (i < lines.size && OL_LINE.containsMatchIn(lines[i])) {
                    items += inline(lines[i].replace(Regex("""^\s*\d+[.)]\s+"""), ""))
                    i++
                }
                out += Block.Numbers(items)
                continue
            }

            // Paragraph: soak up lines until something else starts.
            val buf = mutableListOf<String>()
            while (i < lines.size && !startsBlock(lines[i])) {
                buf += lines[i]
                i++
            }
            val text = buf.joinToString(" ").trim()
            if (text.isNotEmpty()) out += Block.Paragraph(inline(text))
        }

        return out
    }

    // ---------- inline ----------

    private val INLINE_RE = Regex(
        """(`[^`\n]+`)""" +
            """|(\[\[[^\]]+]])""" +
            """|(!\[[^\]]*]\([^)]+\))""" +
            """|(\*\*[^*]+\*\*)""" +
            """|(__[^_]+__)""" +
            """|(\*[^*\s][^*]*\*)""" +
            """|(_[^_\s][^_]*_)""" +
            """|(~~[^~]+~~)""" +
            """|(\[[^\]]+]\([^)]+\))"""
    )
    private val IMAGE_RE = Regex("""^!\[([^\]]*)]\(([^)]+)\)$""")
    private val LINK_RE = Regex("""^\[([^\]]+)]\(([^)]+)\)$""")

    fun inline(text: String): List<Inline> {
        val out = mutableListOf<Inline>()
        var rest = text
        while (rest.isNotEmpty()) {
            val m = INLINE_RE.find(rest)
            if (m == null) {
                out += Inline.Text(rest)
                break
            }
            if (m.range.first > 0) out += Inline.Text(rest.substring(0, m.range.first))
            val token = m.value
            rest = rest.substring(m.range.last + 1)

            out += when {
                token.startsWith("`") -> Inline.Code(token.trim('`'))

                token.startsWith("[[") -> {
                    val parts = Wikilinks.parse(token.removeSurrounding("[[", "]]"))
                    Inline.Wikilink(parts.target, parts.heading, parts.display)
                }

                token.startsWith("![") ->
                    IMAGE_RE.find(token)?.let { Inline.Image(it.groupValues[1], it.groupValues[2]) }
                        ?: Inline.Text(token)

                token.startsWith("**") -> Inline.Strong(inline(token.removeSurrounding("**")))
                token.startsWith("__") -> Inline.Strong(inline(token.removeSurrounding("__")))
                token.startsWith("~~") -> Inline.Strike(inline(token.removeSurrounding("~~")))
                token.startsWith("*") -> Inline.Emphasis(inline(token.removeSurrounding("*")))
                token.startsWith("_") -> Inline.Emphasis(inline(token.removeSurrounding("_")))

                token.startsWith("[") ->
                    LINK_RE.find(token)?.let { Inline.Link(inline(it.groupValues[1]), it.groupValues[2]) }
                        ?: Inline.Text(token)

                else -> Inline.Text(token)
            }
        }
        return out
    }

    /** Flatten a run of inlines back to plain text, for previews and snippets. */
    fun plainText(inlines: List<Inline>): String = buildString {
        for (node in inlines) when (node) {
            is Inline.Text -> append(node.text)
            is Inline.Code -> append(node.text)
            is Inline.Strong -> append(plainText(node.children))
            is Inline.Emphasis -> append(plainText(node.children))
            is Inline.Strike -> append(plainText(node.children))
            is Inline.Wikilink -> append(node.display)
            is Inline.Link -> append(plainText(node.label))
            is Inline.Image -> append(node.alt)
        }
    }
}
