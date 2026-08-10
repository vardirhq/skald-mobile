package no.vardir.skald.core.text

import no.vardir.skald.core.model.SchemaName
import no.vardir.skald.core.model.TaskPriority
import no.vardir.skald.core.model.TaskStatus

/**
 * What the editor should offer, given where the caret is.
 *
 * The desktop can assume you know the syntax — you read the manual once and the
 * keyboard is under your hands. A phone cannot assume either: nobody is going to
 * thumb `@due(2026-06-01)` correctly, and nobody remembers it is possible. So
 * the syntax has to come to the caret instead.
 *
 * Three things live here, all pure functions of a string and an offset:
 *
 * - [autoClose] — typing the second `[` writes the `]]` for you.
 * - [triggerAt] — reads what the caret is in the middle of: a wikilink, a tag,
 *   a metadata token, or the inside of one's parentheses.
 * - [candidates] — what to offer for that trigger, ranked by the same fuzzy
 *   matcher the Hall uses, and [accept], which splices the choice back in.
 *
 * The editor's part is drawing a row of chips and calling these. Nothing here
 * knows about Compose, which is what lets the rules be tested without a device.
 */
object Suggest {

    enum class Kind {
        /** Inside `[[…`, before the closing brackets. */
        Wikilink,

        /** After a `#`, mid-tag. */
        Tag,

        /** After an `@`, before the metadata token has a name. */
        Token,

        /** Inside `@due(…)`. */
        Due,

        /** Inside `@p(…)`. */
        Priority,

        /** Inside `@status(…)`. */
        Status,
    }

    /**
     * The region the caret is completing. [start] to [end] is what has been
     * typed so far — the trigger characters themselves are not included, so
     * accepting a candidate replaces only the query.
     */
    data class Trigger(
        val kind: Kind,
        val start: Int,
        val end: Int,
        val query: String,
        /** True when the closing `]]` or `)` is already there to be stepped over. */
        val closed: Boolean = false,
    )

    /** One offer. [caret] is an offset inside [insert]; -1 means "at the end". */
    data class Candidate(
        val insert: String,
        val label: String,
        val detail: String = "",
        val schema: SchemaName? = null,
        val hit: List<Int> = emptyList(),
        val caret: Int = -1,
    )

    /** A note, as far as completing a link to it cares. */
    data class NoteRef(
        val path: String,
        val title: String,
        val schema: SchemaName,
        val updated: Long = 0,
    )

    /**
     * Everything the vault can offer. Held as a class rather than a data class
     * because [index] is identity-compared anyway — callers key their caches on
     * the note list they built it from.
     */
    class Vocabulary(
        val notes: List<NoteRef> = emptyList(),
        val tags: List<String> = emptyList(),
        val todayIso: String = "",
        /** The indexer's own link index, so an offer resolves where it says it does. */
        val index: Wikilinks.LinkIndex? = null,
    )

    private val TOKEN_NAME = Regex("""@([A-Za-z]+)$""")
    private const val TAG_CHARS = "-_/"

    // ---------- typing ----------

    /**
     * Auto-closing, for the one pair where it is unambiguously right.
     *
     * `[[` always wants `]]`, and a phone keyboard hands over a whole new string
     * rather than a key event — so the pair is recognised after the fact, by the
     * shape of the edit, the same way [LiveMarkdown.insertedNewline] recognises
     * Enter. A single `[` is left alone: `[label](url)` starts the same way.
     */
    fun autoClose(before: String, after: String, caret: Int): Formatting.Edit? {
        if (after.length != before.length + 1) return null
        if (caret < 1 || caret > after.length) return null
        if (after.removeRange(caret - 1, caret) != before) return null
        if (after[caret - 1] != '[') return null
        if (caret < 2 || after[caret - 2] != '[') return null
        if (after.startsWith("]]", caret)) return null
        return Formatting.Edit(after.substring(0, caret) + "]]" + after.substring(caret), caret, caret)
    }

    // ---------- what the caret is in the middle of ----------

    fun triggerAt(text: String, caret: Int): Trigger? {
        val at = caret.coerceIn(0, text.length)
        val head = text.substring(0, at)
        val lineStart = head.lastIndexOf('\n') + 1
        val line = head.substring(lineStart)

        // The inside of `@due(…)` first: its parentheses sit inside a line that
        // may also hold links and tags, and the innermost thing wins.
        insideParens(text, at, line, lineStart)?.let { return it }

        val open = line.lastIndexOf("[[")
        if (open != -1) {
            val inner = line.substring(open + 2)
            if (!inner.contains("]]") && !inner.contains("[")) {
                return Trigger(
                    kind = Kind.Wikilink,
                    start = lineStart + open + 2,
                    end = at,
                    query = inner,
                    closed = text.startsWith("]]", at),
                )
            }
        }

        val hash = line.lastIndexOf('#')
        if (hash != -1 && (hash == 0 || line[hash - 1].isWhitespace())) {
            val query = line.substring(hash + 1)
            val tagLike = query.all { it.isLetterOrDigit() || it in TAG_CHARS }
            // A `#` alone at the start of a line is a heading being written, not
            // a tag — offering tags there would talk over the more likely intent.
            if (tagLike && !(hash == 0 && query.isEmpty())) {
                return Trigger(Kind.Tag, lineStart + hash + 1, at, query)
            }
        }

        val marker = line.lastIndexOf('@')
        if (marker != -1 && (marker == 0 || line[marker - 1].isWhitespace())) {
            val query = line.substring(marker + 1)
            if (query.all { it.isLetter() }) return Trigger(Kind.Token, lineStart + marker + 1, at, query)
        }

        return null
    }

    private fun insideParens(text: String, at: Int, line: String, lineStart: Int): Trigger? {
        val open = line.lastIndexOf('(')
        if (open == -1 || line.indexOf(')', open) != -1) return null
        val name = TOKEN_NAME.find(line.substring(0, open))?.groupValues?.get(1)?.lowercase() ?: return null
        val kind = when (name) {
            "due" -> Kind.Due
            "p", "priority" -> Kind.Priority
            "status" -> Kind.Status
            else -> return null
        }
        return Trigger(kind, lineStart + open + 1, at, line.substring(open + 1), closed = text.startsWith(")", at))
    }

    // ---------- what to offer ----------

    fun candidates(trigger: Trigger, vocab: Vocabulary, limit: Int = 8): List<Candidate> = when (trigger.kind) {
        Kind.Wikilink -> notes(trigger.query, vocab, limit)
        Kind.Tag -> tags(trigger.query, vocab, limit)
        Kind.Token -> tokens(trigger.query)
        Kind.Due -> dates(trigger.query, vocab.todayIso)
        Kind.Priority -> TaskPriority.entries.map { Candidate(it.token, it.token, priorityHint(it)) }
            .filterByQuery(trigger.query)
        Kind.Status -> TaskStatus.entries.map { Candidate(it.token, it.token, statusHint(it)) }
            .filterByQuery(trigger.query)
    }

    /**
     * Notes, matched on both title and path — because a wikilink target *is* a
     * path, and "proj/ro" should find `Projects/Roadmap` as readily as "road"
     * does. What gets inserted is the shortest target that still resolves to
     * this note and no other, so a link stays readable until the day two notes
     * share a file name, and becomes folder-qualified exactly then.
     */
    private fun notes(query: String, vocab: Vocabulary, limit: Int): List<Candidate> {
        fun candidate(note: NoteRef, hit: List<Int>) = Candidate(
            insert = Wikilinks.shortestTarget(note.path, vocab.index),
            label = note.title,
            detail = note.path,
            schema = note.schema,
            hit = hit,
        )

        if (query.isBlank()) {
            return vocab.notes.sortedByDescending { it.updated }.take(limit).map { candidate(it, emptyList()) }
        }
        return vocab.notes
            .mapNotNull { note ->
                val byTitle = Fuzzy.match(query, note.title)
                val byPath = Fuzzy.match(query, note.path)
                val best = listOfNotNull(byTitle, byPath).maxByOrNull { it.score } ?: return@mapNotNull null
                // Highlighting is only meaningful when the match was on the
                // label the row actually shows.
                val hit = if (best === byTitle) best.indices else emptyList()
                Triple(note, best.score, hit)
            }
            .sortedWith(compareByDescending<Triple<NoteRef, Int, List<Int>>> { it.second }.thenBy { it.first.path })
            .take(limit)
            .map { candidate(it.first, it.third) }
    }

    private fun tags(query: String, vocab: Vocabulary, limit: Int): List<Candidate> {
        val known = vocab.tags.distinct()
        val ranked = if (query.isBlank()) {
            known.sorted().take(limit).map { Candidate(it, "#$it") }
        } else {
            known.mapNotNull { tag -> Fuzzy.match(query, tag)?.let { Triple(tag, it.score, it.indices) } }
                .sortedByDescending { it.second }
                .take(limit)
                .map { Candidate(it.first, "#${it.first}", hit = it.third.map { i -> i + 1 }) }
        }
        // A tag nobody has used yet is still a tag. Offering it is what makes
        // the first `#` of a new topic no different from the hundredth.
        val fresh = query.trim()
        return if (fresh.isNotEmpty() && known.none { it.equals(fresh, ignoreCase = true) }) {
            listOf(Candidate(fresh, "#$fresh", "new tag")) + ranked
        } else {
            ranked
        }
    }

    /**
     * The metadata tokens themselves — the part nobody can be expected to
     * remember. Each one is inserted with its parentheses already closed and the
     * caret inside them, which lands straight in [Kind.Due] and its date chips.
     */
    private fun tokens(query: String): List<Candidate> = listOf(
        Candidate("due()", "@due(…)", "when it is due", caret = 4),
        Candidate("p()", "@p(…)", "priority", caret = 2),
        Candidate("status()", "@status(…)", "open, working, blocked", caret = 7),
    ).filter { query.isBlank() || it.label.removePrefix("@").startsWith(query, ignoreCase = true) }

    private fun dates(query: String, todayIso: String): List<Candidate> {
        val typed = Dates.parse(query, todayIso)
        val chips = Dates.dueChoices(todayIso)
            .mapNotNull { it.iso }
            .filter { it != typed }
            .map { Candidate(it, Dates.label(it, todayIso), it) }
        return if (typed != null) listOf(Candidate(typed, Dates.label(typed, todayIso), typed)) + chips else chips
    }

    private fun List<Candidate>.filterByQuery(query: String): List<Candidate> =
        filter { query.isBlank() || it.insert.startsWith(query.trim(), ignoreCase = true) }

    private fun priorityHint(p: TaskPriority): String = when (p) {
        TaskPriority.High -> "raise it"
        TaskPriority.Med -> "the default"
        TaskPriority.Low -> "when it can wait"
    }

    private fun statusHint(s: TaskStatus): String = when (s) {
        TaskStatus.Open -> "not started"
        TaskStatus.Working -> "in hand"
        TaskStatus.Blocked -> "waiting on something"
        TaskStatus.Done -> "finished"
    }

    // ---------- taking the offer ----------

    /**
     * Splice a chosen candidate in, closing whatever the trigger opened and
     * leaving the caret past it — or inside the parentheses, when the candidate
     * is a token whose value is the next thing to pick.
     */
    fun accept(text: String, trigger: Trigger, candidate: Candidate): Formatting.Edit {
        val start = trigger.start.coerceIn(0, text.length)
        val end = trigger.end.coerceIn(start, text.length)

        val closer = when (trigger.kind) {
            Kind.Wikilink -> "]]"
            Kind.Due, Kind.Priority, Kind.Status -> ")"
            else -> ""
        }
        val needsClose = closer.isNotEmpty() && !trigger.closed
        // A tag or a token is the end of a thought; the next word wants a gap.
        val spacer = if (
            trigger.kind == Kind.Tag &&
            (end >= text.length || !text[end].isWhitespace())
        ) " " else ""

        val inserted = candidate.insert + (if (needsClose) closer else "") + spacer
        val out = text.replaceRange(start, end, inserted)

        val caret = if (candidate.caret >= 0) {
            start + candidate.caret
        } else {
            start + inserted.length + (if (trigger.closed) closer.length else 0)
        }
        return Formatting.Edit(out, caret, caret)
    }
}
