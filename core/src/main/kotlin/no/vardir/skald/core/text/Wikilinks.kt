package no.vardir.skald.core.text

/**
 * `[[Wikilink]]` parsing, resolution and rewriting.
 *
 * A link may be written as `[[Note]]`, `[[Folder/Note]]`, `[[Folder/Note.md]]`,
 * `[[Note#Heading]]` or `[[Note|display text]]`. Folder-qualified targets beat
 * bare names, which is what keeps two notes with the same file name apart.
 */
object Wikilinks {

    data class Parts(val target: String, val heading: String?, val display: String)

    private val LINK_RE = Regex("""\[\[([^\]]+)]]""")
    private val FENCED = Regex("""```[\s\S]*?```""")
    private val INLINE_CODE = Regex("""`[^`\n]*`""")

    fun parse(inner: String): Parts {
        val pipe = inner.indexOf('|')
        val targetPart = if (pipe == -1) inner else inner.substring(0, pipe)
        val display = if (pipe == -1) null else inner.substring(pipe + 1).trim()
        val hash = targetPart.indexOf('#')
        val target = (if (hash == -1) targetPart else targetPart.substring(0, hash)).trim()
        val heading = if (hash == -1) null else targetPart.substring(hash + 1).trim()
        return Parts(
            target = target,
            heading = heading,
            display = display?.takeIf { it.isNotEmpty() }
                ?: if (heading != null) "$target › $heading" else target,
        )
    }

    private fun withoutCode(body: String): String =
        INLINE_CODE.replace(FENCED.replace(body, " "), " ")

    /** Distinct link targets in a body, in order of first appearance. */
    fun targets(body: String): List<String> {
        val out = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (m in LINK_RE.findAll(withoutCode(body))) {
            val target = parse(m.groupValues[1]).target
            if (target.isNotEmpty() && seen.add(target.lowercase())) out += target
        }
        return out
    }

    /** Every occurrence, not deduplicated. */
    fun count(body: String): Int = LINK_RE.findAll(withoutCode(body)).count()

    /** Rewrite the target of every link `matches` accepts, keeping heading and display. */
    fun rewrite(body: String, matches: (String) -> Boolean, rewrite: (String) -> String): String =
        LINK_RE.replace(body) { hit ->
            val inner = hit.groupValues[1]
            val target = parse(inner).target
            if (target.isEmpty() || !matches(target)) {
                hit.value
            } else {
                val at = inner.lowercase().indexOf(target.lowercase())
                val rest = if (at == -1) "" else inner.substring(at + target.length)
                "[[${rewrite(target)}$rest]]"
            }
        }

    fun rename(body: String, oldName: String, newName: String): String =
        rewrite(body, { it.equals(oldName, ignoreCase = true) }, { newName })

    // ---------- target resolution ----------

    /** A note as far as link resolution cares. */
    data class Linkable(val path: String, val title: String)

    private const val TIER_PATH = 0
    private const val TIER_TITLE = 1
    private const val TIER_PARTIAL = 2
    private const val TIER_STEM = 3

    /**
     * Fold a written target (or a note path) into its lookup key:
     * case-insensitive, `.md`-less, slash-normalized, free of `./`, `../` and
     * leading-slash noise.
     */
    fun normalizeTarget(target: String): String =
        target.trim()
            .replace('\\', '/')
            .replace(Regex("""\.md$""", RegexOption.IGNORE_CASE), "")
            .split('/')
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/")
            .trim()
            .lowercase()

    class LinkIndex internal constructor(private val entries: Map<String, String>) {
        fun resolve(target: String): String? = entries[normalizeTarget(target)]
        val size: Int get() = entries.size
    }

    /**
     * Index notes under every name a wikilink might reasonably use: the full
     * path, the title, and each trailing path fragment down to the bare stem.
     */
    fun buildIndex(notes: Iterable<Linkable>): LinkIndex {
        val index = HashMap<String, String>()
        val tiers = HashMap<String, Int>()

        fun add(key: String, path: String, tier: Int) {
            if (key.isEmpty()) return
            val held = tiers[key]
            if (held != null && held <= tier) return
            tiers[key] = tier
            index[key] = path
        }

        // Sorted so ties between equally specific keys resolve the same way twice.
        for (note in notes.sortedBy { it.path }) {
            val segments = normalizeTarget(note.path).split('/').filter { it.isNotEmpty() }
            if (segments.isEmpty()) continue
            add(segments.joinToString("/"), note.path, TIER_PATH)
            add(normalizeTarget(note.title), note.path, TIER_TITLE)
            for (i in 1 until segments.size) {
                val suffix = segments.subList(i, segments.size).joinToString("/")
                add(suffix, note.path, if (i == segments.size - 1) TIER_STEM else TIER_PARTIAL)
            }
        }
        return LinkIndex(index)
    }

    /**
     * Point a written target at `newPath` while keeping the shape the author
     * wrote: a bare `[[Note]]` stays bare, `[[Folder/Note]]` keeps one folder of
     * context, and an explicit `.md` or leading `/` survives.
     */
    fun retarget(written: String, newPath: String): String {
        val trimmed = written.trim()
        val rooted = trimmed.startsWith("/")
        val keepExt = trimmed.endsWith(".md", ignoreCase = true)
        val depth = normalizeTarget(trimmed).split('/').filter { it.isNotEmpty() }.size.coerceAtLeast(1)
        val segments = newPath.replace('\\', '/')
            .replace(Regex("""\.md$""", RegexOption.IGNORE_CASE), "")
            .split('/').filter { it.isNotEmpty() }
        val kept = segments.subList(maxOf(0, segments.size - depth), segments.size).joinToString("/")
        return "${if (rooted) "/" else ""}$kept${if (keepExt) ".md" else ""}"
    }

    /**
     * The shortest way to write a link to `path` that still lands on it.
     *
     * A wikilink target is a path, not a name — but a path nobody needs is only
     * noise in a sentence. So the stem is offered while it is unambiguous, one
     * folder of context is added the day a second note takes the same file name,
     * and the whole path when even that is not enough. Resolution is asked of
     * the real index rather than guessed at, so what is written is what the
     * graph will follow.
     */
    fun shortestTarget(path: String, index: LinkIndex?): String {
        val trimmed = path.replace('\\', '/').replace(Regex("""\.md$""", RegexOption.IGNORE_CASE), "")
        val segments = trimmed.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return trimmed
        if (index == null) return segments.last()
        for (i in segments.indices.reversed()) {
            val candidate = segments.subList(i, segments.size).joinToString("/")
            if (index.resolve(candidate) == path) return candidate
        }
        return trimmed
    }

    /** A short passage around the first mention of `name`, for a backlink row. */
    fun snippetAround(body: String, name: String, radius: Int = 90): String {
        val idx = body.lowercase().indexOf("[[${name.lowercase()}")
        if (idx == -1) {
            return body.take(radius * 2).replace(Regex("""\s+"""), " ").trim()
        }
        val start = maxOf(0, idx - radius)
        val end = minOf(body.length, idx + name.length + radius)
        val core = body.substring(start, end).replace(Regex("""\s+"""), " ").trim()
        return "${if (start > 0) "…" else ""}$core${if (end < body.length) "…" else ""}"
    }
}
