package no.vardir.skald.core.text

import no.vardir.skald.core.model.NoteMeta
import no.vardir.skald.core.model.SchemaName

data class SearchQuery(
    val terms: List<String> = emptyList(),
    val schemas: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val folders: List<String> = emptyList(),
)

data class SearchHit(
    val path: String,
    val title: String,
    val schema: SchemaName,
    val folder: String,
    val tags: List<String>,
    val snippet: String,
    val line: Int,
    val column: Int,
    val length: Int,
    val score: Int,
    val updated: Long,
)

/** Small-vault, in-memory full-text search. The Markdown files remain the source of truth. */
object Search {
    private val tokens = Regex("(?:[^\\s\"]+|\"[^\"]*\")+")

    fun parse(input: String): SearchQuery {
        val terms = mutableListOf<String>()
        val schemas = mutableListOf<String>()
        val tags = mutableListOf<String>()
        val folders = mutableListOf<String>()
        for (raw in tokens.findAll(input).map { it.value }) {
            val token = raw.removeSurrounding("\"").trim()
            val split = token.indexOf(':')
            val key = if (split > 0) token.substring(0, split).lowercase() else ""
            val value = if (split > 0) token.substring(split + 1).removeSurrounding("\"").trim().lowercase() else ""
            when {
                value.isEmpty() -> if (token.isNotEmpty()) terms += token.lowercase()
                key == "schema" -> schemas += value
                key == "tag" -> tags += value.removePrefix("#")
                key == "folder" -> folders += value.replace('\\', '/').trim('/')
                else -> terms += token.lowercase()
            }
        }
        return SearchQuery(terms, schemas, tags, folders)
    }

    fun find(notes: List<NoteMeta>, input: String, limit: Int = 100, now: Long = System.currentTimeMillis()): List<SearchHit> {
        val query = parse(input)
        if (query == SearchQuery()) return emptyList()
        return notes.mapNotNull { note -> match(note, query, now) }
            .sortedWith(compareByDescending<SearchHit> { it.score }.thenByDescending { it.updated }.thenBy { it.path })
            .take(limit)
    }

    private fun match(note: NoteMeta, query: SearchQuery, now: Long): SearchHit? {
        val schema = note.schema.name.lowercase()
        val noteTags = note.tags.map { it.removePrefix("#").lowercase() }
        val folder = Notes.parentFolder(note.path).lowercase()
        if (query.schemas.isNotEmpty() && schema !in query.schemas) return null
        if (query.tags.any { it !in noteTags }) return null
        if (query.folders.any { folder != it && !folder.startsWith("$it/") }) return null

        val title = note.title.lowercase()
        val path = note.path.lowercase()
        val body = note.body.lowercase()
        if (query.terms.any { it !in title && it !in path && it !in body }) return null

        var score = 0
        var firstIndex = -1
        var firstTerm = ""
        for (term in query.terms) {
            score += when {
                title == term -> 160
                title.startsWith(term) -> 110
                term in title -> 75
                else -> 0
            }
            if (term in path) score += 20
            val at = body.indexOf(term)
            if (at >= 0) {
                score += 35 + occurrences(body, term).coerceAtMost(20) * 2
                if (firstIndex < 0 || at < firstIndex) { firstIndex = at; firstTerm = term }
            }
        }
        if (query.terms.isEmpty()) score += 10
        val ageMonths = ((now - note.updated).coerceAtLeast(0) / 2_592_000_000L).toInt()
        score += (12 - ageMonths).coerceAtLeast(0)
        val location = locate(note.body, firstIndex, firstTerm)
        return SearchHit(
            note.path, note.title, note.schema, note.folder, note.tags,
            location.snippet, note.bodyStartLine + location.bodyLine - 1, location.column,
            if (firstIndex >= 0) firstTerm.length else 0, score, note.updated,
        )
    }

    private data class Location(val snippet: String, val bodyLine: Int, val column: Int)

    private fun occurrences(text: String, term: String): Int {
        var count = 0
        var from = 0
        while (from < text.length) {
            val at = text.indexOf(term, from)
            if (at < 0) break
            count++
            from = at + term.length.coerceAtLeast(1)
        }
        return count
    }

    private fun locate(body: String, index: Int, term: String): Location {
        val at = index.coerceAtLeast(0)
        val before = body.take(at)
        val bodyLine = before.count { it == '\n' } + 1
        val column = at - before.lastIndexOf('\n')
        val line = body.lineSequence().drop(bodyLine - 1).firstOrNull().orEmpty()
        val start = (column - 1 - 70).coerceAtLeast(0)
        val end = (column - 1 + term.length.coerceAtLeast(1) + 110).coerceAtMost(line.length)
        val core = line.substring(start, end).replace(Regex("\\s+"), " ").trim().ifEmpty { "(empty note)" }
        return Location((if (start > 0) "…" else "") + core + (if (end < line.length) "…" else ""), bodyLine, column)
    }
}
