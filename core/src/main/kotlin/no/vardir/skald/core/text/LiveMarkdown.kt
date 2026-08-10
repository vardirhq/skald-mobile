package no.vardir.skald.core.text

/**
 * The live editor's model, ported from the desktop's `src-shared/liveMarkdown.ts`
 * so the two builds split a note the same way.
 *
 * A body is cut into blocks; the one holding the caret is edited as raw Markdown
 * while every other block stays rendered. Everything here is a pure function of
 * text and an offset, which is what lets the phone's editor be tested without a
 * device attached — the same bargain the rest of `core` makes.
 */
object LiveMarkdown {

    enum class Kind { Blank, Heading, Code, Quote, Task, List, Rule, Paragraph }

    data class Block(
        /** Stable only for as long as the block keeps its lines. */
        val id: String,
        val kind: Kind,
        /** 0-based, within the body. */
        val startLine: Int,
        val endLine: Int,
        val raw: String,
    )

    /** A caret, as a position in the whole body rather than in one block. */
    data class Position(val line: Int, val col: Int)

    /** A block rewritten, and where the caret belongs inside the rewrite. */
    data class Edit(val raw: String, val caret: Int)

    private val TASK_LINE = Regex("""^\s*[-*+]\s+\[[ xX]]\s+""")
    private val UL_LINE = Regex("""^\s*[-*+]\s+(?!\[[ xX]]\s)""")
    private val OL_LINE = Regex("""^\s*\d+[.)]\s+""")
    private val HR_LINE = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")
    private val FENCE = Regex("""^\s*```""")
    private val HEADING = Regex("""^#{1,6}\s+""")
    private val QUOTE = Regex("""^\s*>""")

    /** The bullet, number or checkbox that opens a list line, with its indent. */
    private val LIST_PREFIX = Regex("""^(\s*)([-*+]\s+\[[ xX]]\s+|[-*+]\s+|\d+[.)]\s+)""")
    private val ORDERED_PREFIX = Regex("""^(\s*)(\d+)([.)])(\s+)$""")
    private val QUOTE_PREFIX = Regex("""^(\s*>\s?)""")
    private val TICKED = Regex("""\[[xX]]""")

    // ---------- splitting ----------

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
                !HR_LINE.containsMatchIn(lines[i])
            ) {
                i++
            }
            push(Kind.Paragraph, start, i - 1)
        }

        return blocks
    }

    /** The block that holds this body line, or null when the caret is nowhere. */
    fun blockAt(blocks: List<Block>, line: Int): Int =
        blocks.indexOfFirst { line >= it.startLine && line <= it.endLine }

    // ---------- caret arithmetic ----------
    //
    // The caret is held as a line and column in the whole body, not as an offset
    // into one block: a keystroke can re-split the blocks under it — pressing
    // Enter is exactly that — and a line survives the re-split where an offset
    // into a block that no longer exists would not.

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

    /**
     * Maps a position in a block's *rendered* text back to an offset in its
     * Markdown source.
     *
     * A reader taps what they can see, and what they can see is the source with
     * the syntax taken out — so the two are walked in step. Characters that agree
     * advance both; anything left over in the source is markup the reader never
     * saw, and is stepped over. Whitespace is treated as equivalent throughout,
     * because a rendered paragraph joins source lines with a space.
     *
     * Where the rendered text is not the source minus syntax — a due date shown
     * as "1 May" — alignment cannot be exact, so the search for the next agreeing
     * character is bounded and falls back to the last position that did agree.
     * Being a few characters out beats landing at the end of the block.
     */
    fun sourceOffsetFromRendered(raw: String, rendered: String): Int {
        val maxMarkupRun = 400
        var source = 0
        var shown = 0
        var agreed = 0

        while (shown < rendered.length && source < raw.length) {
            if (rendered[shown].isWhitespace()) {
                while (shown < rendered.length && rendered[shown].isWhitespace()) shown++
                while (source < raw.length && raw[source].isWhitespace()) source++
                // Landing at the start of a source line means the marker that
                // opens it — a bullet, a number, a quote caret — is still ahead.
                // A reader who taps the start of a list item means its text.
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

    // ---------- typing ----------

    /**
     * Was this change exactly one newline typed at `caret`?
     *
     * A phone has no key events to intercept — the soft keyboard hands over a
     * whole new string — so Enter is recognised after the fact, by the shape of
     * the edit, and then replayed through [enter] instead.
     */
    fun insertedNewline(before: String, after: String, caret: Int): Int? {
        if (after.length != before.length + 1) return null
        if (caret < 1 || caret > after.length) return null
        if (after[caret - 1] != '\n') return null
        if (after.removeRange(caret - 1, caret) != before) return null
        return caret - 1
    }

    /** The marker that should open the item after this one. */
    private fun nextMarker(prefix: String): String {
        val ordered = ORDERED_PREFIX.find(prefix)
        if (ordered != null) {
            val (indent, number, punct, space) = ordered.destructured
            return "$indent${(number.toIntOrNull() ?: 1) + 1}$punct$space"
        }
        // A checked box does not carry its tick to the next item.
        return TICKED.replace(prefix, "[ ]")
    }

    private fun lineStart(raw: String, caret: Int): Int =
        raw.lastIndexOf('\n', maxOf(0, caret - 1)).let { if (it < 0) 0 else it + 1 }

    private fun lineEnd(raw: String, caret: Int): Int =
        raw.indexOf('\n', caret).let { if (it < 0) raw.length else it }

    /**
     * Enter. Inside a list it opens the next item; on an empty item it leaves the
     * list; inside code it is just a newline. Everywhere else it ends this block
     * and opens a new one — the blank line between them is what makes them two
     * blocks rather than one paragraph with a newline in it.
     */
    fun enter(kind: Kind, raw: String, caret: Int): Edit {
        val at = caret.coerceIn(0, raw.length)
        val before = raw.substring(0, at)
        val after = raw.substring(at)

        if (kind == Kind.Code) return Edit("$before\n$after", at + 1)

        if (kind == Kind.List || kind == Kind.Task) {
            val start = lineStart(raw, at)
            val end = lineEnd(raw, at)
            val line = raw.substring(start, end)
            val match = LIST_PREFIX.find(line)
            if (match != null) {
                val marker = match.value
                if (line.trim() == marker.trim()) {
                    // An empty item means "I am done listing". Drop it and start
                    // a block.
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

    /**
     * A line break that stays inside this block. Markdown only breaks a line when
     * it is asked to, so the break is written the portable way — two trailing
     * spaces — rather than left as a newline the renderer would swallow.
     */
    fun softBreak(kind: Kind, raw: String, caret: Int): Edit {
        val at = caret.coerceIn(0, raw.length)
        val before = raw.substring(0, at)
        val after = raw.substring(at)
        if (kind == Kind.Code) return Edit("$before\n$after", at + 1)
        val padded = if (before.isNotEmpty() && before.last().isWhitespace()) before.trimEnd(' ', '\t') else before
        val inserted = "$padded  \n"
        return Edit(inserted + after, inserted.length)
    }

    /**
     * Backspace at the very top of a block reaches into the one above it, closing
     * the gap between two blocks — or removing one you opened by accident. The
     * caret lands at the seam.
     */
    fun joinWithPrevious(body: String, blocks: List<Block>, index: Int): Pair<String, Position>? {
        if (index <= 0 || index >= blocks.size) return null
        val block = blocks[index]
        val previous = blocks[index - 1]

        if (previous.kind == Kind.Blank) {
            // Remove the paragraph break, so this block joins the one above.
            val body2 = replaceBlock(body, previous.startLine, block.endLine, block.raw)
            return body2 to Position(previous.startLine, 0)
        }
        val lines = previous.raw.split("\n")
        val body2 = replaceBlock(body, previous.startLine, block.endLine, previous.raw + block.raw)
        return body2 to Position(previous.startLine + lines.size - 1, lines.last().length)
    }

    // ---------- splicing ----------

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

    /** Put an edited body back under the frontmatter it was cut from. */
    fun replaceBody(content: String, bodyStartLine: Int, body: String): String {
        if (bodyStartLine <= 0) return body
        val frontmatter = content.split("\n").take(bodyStartLine).joinToString("\n")
        return if (frontmatter.isEmpty()) body else "$frontmatter\n$body"
    }
}
