package no.vardir.skald.core.text

import no.vardir.skald.core.model.TaskPriority
import no.vardir.skald.core.model.TaskStatus

/**
 * Markdown → a small block/inline tree.
 *
 * Markdown stays the storage format; this is the reading surface. Parsing lives
 * here rather than in the UI so the renderer stays a pure function of a tree,
 * and so the block rules can be tested without a device attached.
 *
 * Skald semantic containers are runtime structure only: the canonical source is
 * still fenced Markdown. V1 deliberately allows containers at document level
 * but not containers inside containers.
 */
object Markdown {

    sealed interface Inline {
        data class Text(val text: String) : Inline
        data class Code(val text: String) : Inline
        data class Strong(val children: List<Inline>) : Inline
        data class Emphasis(val children: List<Inline>) : Inline
        data class Strike(val children: List<Inline>) : Inline
        data class Wikilink(val target: String, val heading: String?, val display: String) : Inline
        data class Link(val label: List<Inline>, val url: String) : Inline
        data class Image(val alt: String, val target: String) : Inline
    }

    data class TaskLine(
        val line: Int,
        val content: List<Inline>,
        val status: TaskStatus,
        val priority: TaskPriority,
        val due: String?,
        val tags: List<String>,
    )

    enum class ContainerKind(val sourceName: String) {
        Aside("aside"),
        Gallery("gallery"),
        Group("group");

        companion object {
            fun fromSource(value: String): ContainerKind? = entries.firstOrNull { it.sourceName == value }
        }
    }

    sealed interface Block {
        data class Heading(val level: Int, val content: List<Inline>, val line: Int) : Block
        data class Paragraph(val content: List<Inline>) : Block
        data class Code(val lang: String?, val text: String) : Block
        data class Quote(val content: List<Inline>) : Block
        data class Callout(val label: String, val content: List<Inline>) : Block
        data object Rule : Block
        data class Tasks(val items: List<TaskLine>) : Block
        data class Bullets(val items: List<List<Inline>>) : Block
        data class Numbers(val items: List<List<Inline>>) : Block
        data class Table(
            val headers: List<List<Inline>>,
            val alignments: List<TableAlignment>,
            val rows: List<List<List<Inline>>>,
        ) : Block
        data class Container(
            val kind: ContainerKind,
            val children: List<Block>,
            val startLine: Int,
            val endLine: Int,
        ) : Block
    }

    enum class TableAlignment { Left, Center, Right }

    private val TASK_LINE = Regex("""^\s*[-*+]\s+\[[ xX]]\s+""")
    private val UL_LINE = Regex("""^\s*[-*+]\s+(?!\[[ xX]]\s)""")
    private val OL_LINE = Regex("""^\s*\d+[.)]\s+""")
    private val FENCE = Regex("""^\s*```(\w*)""")
    private val CLOSING_FENCE = Regex("""^\s*```""")
    private val HEADING = Regex("""^(#{1,6})\s+(.+?)\s*$""")
    private val RULE = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")
    private val QUOTE = Regex("""^\s*>""")
    private val CALLOUT = Regex("""^\[!(\w+)]\s*(.*)$""")
    private val CONTAINER_OPEN = Regex("""^\s*:::(aside|gallery|group)\s*$""")
    private val CONTAINER_CLOSE = Regex("""^\s*:::\s*$""")

    fun parse(body: String, lineOffset: Int = 0): List<Block> = parseInternal(body, lineOffset, allowContainers = true)

    private fun parseInternal(body: String, lineOffset: Int, allowContainers: Boolean): List<Block> {
        val lines = body.split("\n")
        val out = mutableListOf<Block>()
        var i = 0

        fun tableAt(index: Int): Pair<List<String>, List<TableAlignment>>? {
            if (index + 1 >= lines.size) return null
            val header = splitTableRow(lines[index]) ?: return null
            val delimiter = splitTableRow(lines[index + 1]) ?: return null
            if (header.isEmpty() || delimiter.size != header.size) return null
            val alignments = delimiter.map { cell ->
                val marker = cell.trim()
                if (!Regex("""^:?-{3,}:?$""").matches(marker)) return null
                when {
                    marker.startsWith(":") && marker.endsWith(":") -> TableAlignment.Center
                    marker.endsWith(":") -> TableAlignment.Right
                    else -> TableAlignment.Left
                }
            }
            return header to alignments
        }

        fun startsBlock(index: Int): Boolean {
            val line = lines[index]
            return line.isBlank() ||
                HEADING.containsMatchIn(line) ||
                QUOTE.containsMatchIn(line) ||
                CLOSING_FENCE.containsMatchIn(line) ||
                TASK_LINE.containsMatchIn(line) ||
                UL_LINE.containsMatchIn(line) ||
                OL_LINE.containsMatchIn(line) ||
                RULE.matches(line) ||
                (allowContainers && CONTAINER_OPEN.matches(line)) ||
                tableAt(index) != null
        }

        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { i++; continue }

            if (allowContainers) {
                val opener = CONTAINER_OPEN.find(line)
                if (opener != null) {
                    var cursor = i + 1
                    var inCode = false
                    var nestedDepth = 0
                    var closeAt = -1
                    while (cursor < lines.size) {
                        if (CLOSING_FENCE.containsMatchIn(lines[cursor])) {
                            inCode = !inCode
                            cursor++
                            continue
                        }
                        if (!inCode && CONTAINER_OPEN.matches(lines[cursor])) {
                            nestedDepth++
                        } else if (!inCode && CONTAINER_CLOSE.matches(lines[cursor])) {
                            if (nestedDepth > 0) nestedDepth-- else {
                                closeAt = cursor
                                break
                            }
                        }
                        cursor++
                    }
                    if (closeAt >= 0) {
                        val kind = requireNotNull(ContainerKind.fromSource(opener.groupValues[1]))
                        val childSource = lines.subList(i + 1, closeAt).joinToString("\n")
                        val children = parseInternal(childSource, lineOffset + i + 1, allowContainers = false)
                        out += Block.Container(kind, children, i + 1 + lineOffset, closeAt + 1 + lineOffset)
                        i = closeAt + 1
                        continue
                    }
                    // Malformed directives are visible/recoverable rather than wedging the parser.
                    out += Block.Paragraph(inline(line.trim()))
                    i++
                    continue
                }
            }

            val fence = FENCE.find(line)
            if (fence != null) {
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !CLOSING_FENCE.containsMatchIn(lines[i])) { code += lines[i]; i++ }
                if (i < lines.size) i++
                out += Block.Code(fence.groupValues[1].ifEmpty { null }, code.joinToString("\n"))
                continue
            }

            val heading = HEADING.find(line)
            if (heading != null) {
                out += Block.Heading(heading.groupValues[1].length, inline(heading.groupValues[2]), i + 1 + lineOffset)
                i++; continue
            }

            if (RULE.matches(line)) { out += Block.Rule; i++; continue }

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
                } else Block.Quote(inline(quoted.joinToString(" ")))
                continue
            }

            if (TASK_LINE.containsMatchIn(line)) {
                val start = i
                while (i < lines.size && TASK_LINE.containsMatchIn(lines[i])) i++
                val chunk = lines.subList(start, i).joinToString("\n")
                out += Block.Tasks(Tasks.extract(chunk, start + lineOffset).map {
                    TaskLine(it.line, inline(it.content), it.status, it.priority, it.due, it.tags)
                })
                continue
            }

            if (UL_LINE.containsMatchIn(line)) {
                val items = mutableListOf<List<Inline>>()
                while (i < lines.size && UL_LINE.containsMatchIn(lines[i])) {
                    items += inline(lines[i].replace(Regex("""^\s*[-*+]\s+"""), "")); i++
                }
                out += Block.Bullets(items); continue
            }

            if (OL_LINE.containsMatchIn(line)) {
                val items = mutableListOf<List<Inline>>()
                while (i < lines.size && OL_LINE.containsMatchIn(lines[i])) {
                    items += inline(lines[i].replace(Regex("""^\s*\d+[.)]\s+"""), "")); i++
                }
                out += Block.Numbers(items); continue
            }

            val table = tableAt(i)
            if (table != null) {
                val (header, alignments) = table
                i += 2
                val rows = mutableListOf<List<List<Inline>>>()
                while (i < lines.size) {
                    val cells = splitTableRow(lines[i]) ?: break
                    if (cells.isEmpty()) break
                    rows += (0 until header.size).map { column -> inline(cells.getOrElse(column) { "" }) }
                    i++
                }
                out += Block.Table(header.map(::inline), alignments, rows)
                continue
            }

            val buf = mutableListOf<String>()
            while (i < lines.size && !startsBlock(i)) { buf += lines[i]; i++ }
            if (buf.isEmpty() && i < lines.size && CONTAINER_CLOSE.matches(lines[i])) { buf += lines[i]; i++ }
            val text = buf.joinToString(" ").trim()
            if (text.isNotEmpty()) out += Block.Paragraph(inline(text))
        }
        return out
    }

    private fun splitTableRow(line: String): List<String>? {
        val trimmed = line.trim()
        if ('|' !in trimmed) return null
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        var code = false
        for (character in trimmed) {
            when {
                escaped -> { current.append(character); escaped = false }
                character == '\\' -> escaped = true
                character == '`' -> { code = !code; current.append(character) }
                character == '|' && !code -> { cells += current.toString().trim(); current.clear() }
                else -> current.append(character)
            }
        }
        if (escaped) current.append('\\')
        cells += current.toString().trim()
        if (trimmed.startsWith('|')) cells.removeFirst()
        if (trimmed.endsWith('|')) cells.removeLast()
        return cells
    }

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
            if (m == null) { out += Inline.Text(rest); break }
            if (m.range.first > 0) out += Inline.Text(rest.substring(0, m.range.first))
            val token = m.value
            rest = rest.substring(m.range.last + 1)
            out += when {
                token.startsWith("`") -> Inline.Code(token.trim('`'))
                token.startsWith("[[") -> {
                    val parts = Wikilinks.parse(token.removeSurrounding("[[", "]]"))
                    Inline.Wikilink(parts.target, parts.heading, parts.display)
                }
                token.startsWith("![") -> IMAGE_RE.find(token)?.let { Inline.Image(it.groupValues[1], it.groupValues[2]) } ?: Inline.Text(token)
                token.startsWith("**") -> Inline.Strong(inline(token.removeSurrounding("**")))
                token.startsWith("__") -> Inline.Strong(inline(token.removeSurrounding("__")))
                token.startsWith("~~") -> Inline.Strike(inline(token.removeSurrounding("~~")))
                token.startsWith("*") -> Inline.Emphasis(inline(token.removeSurrounding("*")))
                token.startsWith("_") -> Inline.Emphasis(inline(token.removeSurrounding("_")))
                token.startsWith("[") -> LINK_RE.find(token)?.let { Inline.Link(inline(it.groupValues[1]), it.groupValues[2]) } ?: Inline.Text(token)
                else -> Inline.Text(token)
            }
        }
        return out
    }

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
