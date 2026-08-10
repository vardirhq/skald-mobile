package no.vardir.skald.core.text

/**
 * What a formatting bar does to a string and a selection.
 *
 * A phone has no ⌘B, so the marks have to be buttons — and a button that only
 * ever adds syntax is a trap, because the way back out is fiddly with a thumb.
 * Every operation here is a toggle: applied to text that already wears the mark,
 * it takes it off again.
 *
 * Pure, so the live editor's block field and the source view share one set of
 * rules, and so the rules are tested without a keyboard.
 */
object Formatting {

    /** The rewritten text, and the selection that should survive into it. */
    data class Edit(val text: String, val start: Int, val end: Int)

    enum class Mark(val marker: String) {
        Bold("**"),
        Italic("*"),
        Code("`"),
        Strike("~~"),
    }

    enum class LineStyle { Bullet, Numbered, Task, Quote }

    private val LIST_MARKER = Regex("""^(\s*)(?:[-*+]\s+\[[ xX]]\s+|[-*+]\s+|\d+[.)]\s+)""")
    private val BULLET_MARKER = Regex("""^(\s*)[-*+]\s+(?!\[[ xX]]\s)""")
    private val TASK_MARKER = Regex("""^(\s*)[-*+]\s+\[[ xX]]\s+""")
    private val NUMBER_MARKER = Regex("""^(\s*)\d+[.)]\s+""")
    private val QUOTE_MARKER = Regex("""^(\s*)>\s?""")
    private val HEADING_MARKER = Regex("""^(\s*)(#{1,6})\s+""")

    // ---------- inline marks ----------

    /**
     * Bold, italic, code, strike. With nothing selected the pair is inserted
     * empty and the caret lands between the halves, which is how a thumb starts
     * a bold word it has not typed yet.
     */
    fun toggleMark(text: String, start: Int, end: Int, mark: Mark): Edit {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val m = mark.marker
        val selected = text.substring(from, to)

        if (wrappedInside(selected, m)) {
            val inner = selected.substring(m.length, selected.length - m.length)
            return Edit(text.replaceRange(from, to, inner), from, from + inner.length)
        }
        if (wrappedOutside(text, from, to, m)) {
            val stripped = text.removeRange(to, to + m.length).removeRange(from - m.length, from)
            return Edit(stripped, from - m.length, to - m.length)
        }
        val wrapped = m + selected + m
        return Edit(text.replaceRange(from, to, wrapped), from + m.length, from + m.length + selected.length)
    }

    /**
     * A single `*` sits inside every `**`, so a one-character mark only counts as
     * present when it is not the edge of a longer run — otherwise asking for
     * italics on a bold word would quietly make it italic instead.
     */
    private fun wrappedInside(selected: String, marker: String): Boolean {
        if (selected.length < marker.length * 2) return false
        if (!selected.startsWith(marker) || !selected.endsWith(marker)) return false
        if (marker.length > 1) return true
        val doubled = marker + marker
        return !(selected.startsWith(doubled) && selected.endsWith(doubled))
    }

    private fun wrappedOutside(text: String, start: Int, end: Int, marker: String): Boolean {
        if (start < marker.length || end + marker.length > text.length) return false
        if (!text.regionMatches(start - marker.length, marker, 0, marker.length)) return false
        if (!text.regionMatches(end, marker, 0, marker.length)) return false
        if (marker.length == 1) {
            if (start - marker.length - 1 >= 0 && text[start - marker.length - 1] == marker[0]) return false
            if (end + marker.length < text.length && text[end + marker.length] == marker[0]) return false
        }
        return true
    }

    /** `[label](url)` — caret in whichever half is still empty. */
    fun link(text: String, start: Int, end: Int): Edit {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val label = text.substring(from, to)
        val inserted = "[$label]()"
        val caret = if (label.isEmpty()) from + 1 else from + inserted.length - 1
        return Edit(text.replaceRange(from, to, inserted), caret, caret)
    }

    /** `[[Target]]` — the vault's own kind of link. */
    fun wikilink(text: String, start: Int, end: Int): Edit {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val target = text.substring(from, to)
        val inserted = "[[$target]]"
        return Edit(text.replaceRange(from, to, inserted), from + 2, from + 2 + target.length)
    }

    // ---------- line marks ----------

    /**
     * Bullets, numbers, checkboxes and quotes, applied to every line the
     * selection touches. The three list kinds replace each other rather than
     * stacking, because `- 1. [ ] thing` is nobody's intent.
     */
    fun toggleLine(text: String, start: Int, end: Int, style: LineStyle): Edit {
        val marker = when (style) {
            LineStyle.Bullet -> BULLET_MARKER
            LineStyle.Numbered -> NUMBER_MARKER
            LineStyle.Task -> TASK_MARKER
            LineStyle.Quote -> QUOTE_MARKER
        }

        return rewriteLines(text, start, end) { lines ->
            val meaningful = lines.filter { it.isNotBlank() }.ifEmpty { lines }
            val alreadyOn = meaningful.all { marker.containsMatchIn(it) }
            var n = 1
            lines.map { line ->
                if (line.isBlank() && lines.size > 1) return@map line
                when {
                    alreadyOn -> marker.replace(line) { it.groupValues[1] }
                    style == LineStyle.Quote -> {
                        val indent = line.takeWhile { it == ' ' || it == '\t' }
                        indent + "> " + line.substring(indent.length)
                    }
                    else -> {
                        val stripped = LIST_MARKER.replace(line) { it.groupValues[1] }
                        val indent = stripped.takeWhile { it == ' ' || it == '\t' }
                        val opener = when (style) {
                            LineStyle.Bullet -> "- "
                            LineStyle.Task -> "- [ ] "
                            LineStyle.Numbered -> "${n++}. "
                            LineStyle.Quote -> ""
                        }
                        indent + opener + stripped.substring(indent.length)
                    }
                }
            }
        }
    }

    /**
     * Heading level, cycled rather than picked: none → H1 → H2 → H3 → none. One
     * button covers the range a note actually uses, and a fourth tap is the way
     * back to prose.
     */
    fun cycleHeading(text: String, start: Int, end: Int): Edit = rewriteLines(text, start, start) { lines ->
        val line = lines.first()
        val match = HEADING_MARKER.find(line)
        val indent = match?.groupValues?.get(1) ?: line.takeWhile { it == ' ' || it == '\t' }
        val bare = if (match != null) line.substring(match.value.length) else line.substring(indent.length)
        val level = match?.groupValues?.get(2)?.length ?: 0
        val next = when (level) {
            0 -> 1
            1 -> 2
            2 -> 3
            else -> 0
        }
        listOf(indent + (if (next == 0) "" else "#".repeat(next) + " ") + bare)
    }

    // ---------- whole blocks ----------

    /** A fenced code block around the selection, or an empty one to type into. */
    fun fence(text: String, start: Int, end: Int): Edit {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val selected = text.substring(from, to)
        val lead = if (from == 0 || text[from - 1] == '\n') "" else "\n"
        val tail = if (to == text.length || text[to] == '\n') "" else "\n"
        val inserted = "$lead```\n$selected\n```$tail"
        val caret = from + lead.length + 4
        return Edit(text.replaceRange(from, to, inserted), caret, caret + selected.length)
    }

    /** A horizontal rule on a line of its own. */
    fun rule(text: String, start: Int, end: Int): Edit {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val lead = if (from == 0) "" else if (text[from - 1] == '\n') "" else "\n\n"
        val inserted = "$lead---\n\n"
        val caret = from + inserted.length
        return Edit(text.replaceRange(from, to, inserted), caret, caret)
    }

    // ---------- the line window a selection touches ----------

    private fun rewriteLines(
        text: String,
        start: Int,
        end: Int,
        rewrite: (List<String>) -> List<String>,
    ): Edit {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val head = text.lastIndexOf('\n', maxOf(0, from - 1)).let { if (it < 0) 0 else it + 1 }
        val foot = text.indexOf('\n', to).let { if (it < 0) text.length else it }
        val lines = text.substring(head, foot).split("\n")
        val rewritten = rewrite(lines)

        val out = text.substring(0, head) + rewritten.joinToString("\n") + text.substring(foot)

        // An offset moves by whatever its own line grew or shrank by, so the
        // selection stays on the words it was on rather than on the markers.
        fun map(offset: Int): Int {
            var oldPos = head
            var newPos = head
            for (i in lines.indices) {
                val old = lines[i]
                val new = rewritten.getOrElse(i) { old }
                if (offset <= oldPos + old.length) {
                    val within = offset - oldPos + (new.length - old.length)
                    return newPos + within.coerceIn(0, new.length)
                }
                oldPos += old.length + 1
                newPos += new.length + 1
            }
            return newPos
        }

        return Edit(out, map(from), map(to))
    }
}
