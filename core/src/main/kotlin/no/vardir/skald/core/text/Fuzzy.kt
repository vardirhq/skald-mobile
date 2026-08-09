package no.vardir.skald.core.text

/**
 * The matcher behind Skald's Hall. A subsequence match with word-boundary and
 * contiguity bonuses — small, predictable, and good enough that nobody reaches
 * for a search index on a personal vault.
 */
object Fuzzy {

    data class Result(val score: Int, val indices: List<Int>)

    private fun isBoundary(ch: Char): Boolean = ch == ' ' || ch == '\t' || ch == '-' || ch == '_' || ch == '/' || ch == '.'

    fun match(query: String, text: String): Result? {
        val q = query.lowercase()
        val t = text.lowercase()
        if (q.isEmpty()) return Result(0, emptyList())

        // Fast path: a straight substring hit always beats a scattered one.
        val sub = t.indexOf(q)
        if (sub != -1) {
            var score = 100 - sub
            if (sub == 0 || isBoundary(t[sub - 1])) score += 40
            score -= t.length / 8
            return Result(score, (sub until sub + q.length).toList())
        }

        val indices = mutableListOf<Int>()
        var ti = 0
        var score = 0
        var streak = 0
        for (ch in q) {
            val found = t.indexOf(ch, ti)
            if (found == -1) return null
            if (found == ti && indices.isNotEmpty()) {
                streak += 1
                score += 6 + streak
            } else {
                streak = 0
                score += 1
                if (found == 0 || isBoundary(t[found - 1])) score += 10
            }
            indices += found
            ti = found + 1
        }
        score -= t.length / 10
        return Result(score, indices)
    }

    data class Segment(val text: String, val hit: Boolean)

    /** Split `text` into runs of matched and unmatched characters, for highlighting. */
    fun highlight(text: String, indices: List<Int>): List<Segment> {
        if (indices.isEmpty()) return listOf(Segment(text, false))
        val set = indices.toHashSet()
        val out = mutableListOf<Segment>()
        val buf = StringBuilder()
        var current = 0 in set
        for (i in text.indices) {
            val hit = i in set
            if (hit != current) {
                if (buf.isNotEmpty()) out += Segment(buf.toString(), current)
                buf.setLength(0)
                current = hit
            }
            buf.append(text[i])
        }
        if (buf.isNotEmpty()) out += Segment(buf.toString(), current)
        return out
    }
}
