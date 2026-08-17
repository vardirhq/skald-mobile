package no.vardir.skald.core.text

/**
 * The live editor's source-oriented projection. Semantic containers are one
 * editing region on mobile v1: inactive containers render through the document
 * tree, while tapping one exposes its complete portable fenced source. This
 * keeps fences from leaking into neighbouring rendered blocks and prevents
 * Backspace/Enter from accidentally crossing an invisible container boundary.
 */
object LiveMarkdown {

    enum class Kind { Blank, Heading, Code, Quote, Task, List, Rule, Paragraph, Container }

    data class Block(
        val id: String,
        val kind: Kind,
        val startLine: Int,
        val endLine: Int,
        val raw: String,
    )

    data class Position(val line: Int, val col: Int)
    data class Edit(val raw: String, val caret: Int)

    private val TASK_LINE = Regex("""^\s*[-*+]\s+\[[ xX]]\s+""")
    private val UL_LINE = Regex("""^\s*[-*+]\s+(?!\[[ xX]]\s)""")
    private val OL_LINE = Regex("""^\s*\d+[.)]\s+""")
    private val HR_LINE = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")
    private val FENCE = Regex("""^\s*```""")
    private val HEADING = Regex("""^#{1,6}\s+""")
    private val QUOTE = Regex("""^\s*>""")
    private val CONTAINER_OPEN = Regex("""^\s*:::(aside|gallery|group)\s*$""")
    private val CONTAINER_CLOSE = Regex("""^\s*:::\s*$""")

    private val LIST_PREFIX = Regex("""^(\s*)([-*+]\s+\[[ xX]]\s+|[-*+]\s+|\d+[.)]\s+)""")
    private val ORDERED_PREFIX = Regex("""^(\s*)(\d+)([.)])(\s+)$""")
    private val QUOTE_PREFIX = Regex("""^(\s*>\s?)""")
    private val TICKED = Regex("""\[[xX]]""")

    fun split(body: String): List<Block> {
        if (body.isEmpty()) return listOf(Block("b0-0", Kind.Blank, 0, 0, ""))

        val lines = body.split("\n")
        val blocks = mutableListOf<Block>()
        var i = 0

        fun push(kind: Kind, start: Int, end: Int) {
            blocks += Block(
                id = "b$start-$end",
                kind = kind,
                startLine = start,
                endLine = end,
                raw = lines.subList(start, end + 1).joinToString("\n"),
            )
        }

        while (i < lines.size) {
            val line = lines[i]

            if (line.isBlank()) {
                val start = i
                while (i < lines.size && lines[i].isBlank()) i++
                push(Kind.Blank, start, i - 1)
                continue
            }

            if (CONTAINER_OPEN.matches(line)) {
                val start = i
                var cursor = i + 1
                var inCode = false
                var closeAt = -1
                while (cursor < lines.size) {
                    if (FENCE.containsMatchIn(lines[cursor])) inCode = !inCode
                    if (!inCode && CONTAINER_CLOSE.matches(lines[cursor])) {
                        closeAt = cursor
                        break
                    }
                    cursor++
                }
                if (closeAt >= 0) {
                    push(Kind.Container, start, closeAt)
                    i = closeAt + 1
                    continue
                }
                // An unclosed directive is ordinary readable source.
            }

            if (FENCE.containsMatchIn(line)) {
                val start = i
                i++
                while (i < lines.size && !FENCE.containsMatchIn(lines[i])) i++
                if (i < lines.size) i++
                push(Kind.Code, start, i - 1)
                continue
            }

            if (HEADING.containsMatchIn(line)) {
                push(Kind.Heading, i, i)
                i++
                continue
            }

            if (HR_LINE.containsMatchIn(line)) {
                push(Kind.Rule, i, i)
                i++
                continue
            }

            if (QUOTE.containsMatchIn(line)) {
                val start = i
                while (i < lines.size && QUOTE.containsMatchIn(lines[i])) i++
                push(Kind.Quote, start, i - 1)
                continue
            }

            if (TASK_LINE.containsMatchIn(line)) {
                val start = i
                while (i < lines.size && TASK_LINE.containsMatchIn(lines[i])) i++
                push(Kind.Task, start, i - 1)
                continue
            }

            if (UL_LINE.containsMatchIn(line) || OL_LINE.containsMatchIn(line)) {
                val start = i
                val matcher = if (UL_LINE.containsMatchIn(line)) UL_LINE else OL_LINE
                while (i < lines.size && matcher.containsMatchIn(lines[i])) i++
                push(Kind.List, start, i - 1)
                continue
            }

            val start = i
            while (
                i < lines.size &&
                lines[i].isNotBlank() &&
                !HEADING.containsMatchIn(lines[i]) &&
                !QUOTE.containsMatchIn(lines[i]) &&
                !FENCE.containsMatchIn(lines[i]) &&
                !TASK_LINE.containsMatchIn(lines[i]) &&
                !UL_LINE.containsMatchIn(lines[i]) &&
                !OL_LINE.containsMatchIn(lines[i]) &&
                !HR_LINE.containsMatchIn(lines[i]) &&
                !CONTAINER_OPEN.matches(lines[i])
            ) {
                i++
            }
            push(Kind.Paragraph, start, i - 1)
        }

        return blocks
    }

    fun blockAt(blocks: List<Block>, line: Int): Int =
        blocks.indexOfFirst { line >= it.startLine && line <= it.endLine }

    fun offsetAt(raw: String, line: Int, col: Int): Int {
        val lines = raw.split("\n")
        val row = line.coerceIn(0, lines.size - 1)
        var offset = 0
        for (i in 0 until row) offset += lines[i].length + 1
        return offset + col.coerceIn(0, lines[row].length)
    }

    fun positionAt(raw: String, offset: Int): Position {
        val before = raw.substring(0, offset.coerceIn(0, raw.length)).split("\n")
        return Position(before.size - 1, before.last().length)
    }

    fun sourceOffsetFromRendered(raw: String, rendered: String): Int {
        val maxMarkupRun = 400
        var source = 0
        var shown = 0
        var agreed = 0

        while (shown < rendered.length && source < raw.length) {
            if (rendered[shown].isWhitespace()) {
                while (shown < rendered.length && rendered[shown].isWhitespace()) shown++
                while (source < raw.length && raw[source].isWhitespace()) source++
                if (source > 0 && raw[source - 1] == '\n') {
                    val rest = raw.substring(source)
                    val marker = LIST_PREFIX.find(rest)?.value ?: QUOTE_PREFIX.find(rest)?.groupValues?.get(1)
                    if (marker != null) source += marker.length
                }
                agreed = source
                continue
            }
            if (raw[source] == rendered[shown]) {
                source++
                shown++
                agreed = source
                continue
            }
            if (source - agreed > maxMarkupRun) return agreed
            source++
        }
        return if (shown >= rendered.length) agreed else source
    }

    fun insertedNewline(before: String, after: String, caret: Int): Int? {
        if (after.length != before.length + 1) return null
        if (caret < 1 || caret > after.length) return null
        if (after[caret - 1] != '\n') return null
        if (after.removeRange(caret - 1, caret) != before) return null
        return caret - 1
    }

    private fun nextMarker(prefix: String): String {
        val ordered = ORDERED_PREFIX.find(prefix)
        if (ordered != null) {
            val (indent, number, punct, space) = ordered.destructured
            return "$indent${(number.toIntOrNull() ?: 1) + 1}$punct$space"
        }
        return TICKED.replace(prefix, "[ ]")
    }

    private fun lineStart(raw: String, caret: Int): Int =
        raw.lastIndexOf('\n', maxOf(0, caret - 1)).let { if (it < 0) 0 else it + 1 }

    private fun lineEnd(raw: String, caret: Int): Int =
        raw.indexOf('\n', caret).let { if (it < 0) raw.length else it }

    fun enter(kind: Kind, raw: String, caret: Int): Edit {
        val at = caret.coerceIn(0, raw.length)
        val before = raw.substring(0, at)
        val after = raw.substring(at)

        // A semantic container is source-editing a mini document. Enter should
        // insert a line, not split the outer live block around invisible fences.
        if (kind == Kind.Code || kind == Kind.Container) return Edit("$before\n$after", at + 1)

        if (kind == Kind.List || kind == Kind.Task) {
            val start = lineStart(raw, at)
            val end = lineEnd(raw, at)
            val line = raw.substring(start, end)
            val match = LIST_PREFIX.find(line)
            if (match != null) {
                val marker = match.value
                if (line.trim() == marker.trim()) {
                    val head = raw.substring(0, start).removeSuffix("\n")
                    val tail = raw.substring(end)
                    val joined = if (head.isNotEmpty()) "$head\n\n" else ""
                    return Edit(joined + tail.removePrefix("\n"), joined.length)
                }
                val opened = "$before\n${nextMarker(marker)}"
                return Edit(opened + after, opened.length)
            }
        }

        if (kind == Kind.Quote) {
            val start = lineStart(raw, at)
            val prefix = QUOTE_PREFIX.find(raw.substring(start))?.groupValues?.get(1)
            if (prefix != null && raw.substring(start).trim() != prefix.trim()) {
                val opened = "$before\n$prefix"
                return Edit(opened + after, opened.length)
            }
        }

        val head = before.trimEnd(' ', '\t')
        return Edit("$head\n\n$after", head.length + 2)
    }

    fun softBreak(kind: Kind, raw: String, caret: Int): Edit {
        val at = caret.coerceIn(0, raw.length)
        val before = raw.substring(0, at)
        val after = raw.substring(at)
        if (kind == Kind.Code || kind == Kind.Container) return Edit("$before\n$after", at + 1)
        val padded = if (before.isNotEmpty() && before.last().isWhitespace()) before.trimEnd(' ', '\t') else before
        val inserted = "$padded  \n"
        return Edit(inserted + after, inserted.length)
    }

    fun joinWithPrevious(body: String, blocks: List<Block>, index: Int): Pair<String, Position>? {
        if (index <= 0 || index >= blocks.size) return null
        val block = blocks[index]
        val previous = blocks[index - 1]
        // Never erase or cross a semantic fence as an implicit Backspace join.
        if (block.kind == Kind.Container || previous.kind == Kind.Container) return null

        if (previous.kind == Kind.Blank) {
            val body2 = replaceBlock(body, previous.startLine, block.endLine, block.raw)
            return body2 to Position(previous.startLine, 0)
        }
        val lines = previous.raw.split("\n")
        val body2 = replaceBlock(body, previous.startLine, block.endLine, previous.raw + block.raw)
        return body2 to Position(previous.startLine + lines.size - 1, lines.last().length)
    }

    fun replaceBlock(body: String, startLine: Int, endLine: Int, raw: String): String {
        val lines = if (body.isEmpty()) listOf("") else body.split("\n")
        val replacement = if (raw.isEmpty()) listOf("") else raw.split("\n")
        val from = startLine.coerceIn(0, lines.size)
        val to = (endLine + 1).coerceIn(from, lines.size)
        val out = ArrayList<String>(lines.size - (to - from) + replacement.size)
        out += lines.subList(0, from)
        out += replacement
        out += lines.subList(to, lines.size)
        return out.joinToString("\n")
    }

    fun replaceBlock(body: String, block: Block, raw: String): String =
        replaceBlock(body, block.startLine, block.endLine, raw)

    fun replaceBody(content: String, bodyStartLine: Int, body: String): String {
        if (bodyStartLine <= 0) return body
        val frontmatter = content.split("\n").take(bodyStartLine).joinToString("\n")
        return if (frontmatter.isEmpty()) body else "$frontmatter\n$body"
    }
}
