package no.vardir.skald.core.text

/** Tag semantics shared by indexing, browsing, search and editor completion. */
object Tags {
    private val fencedCode = Regex("```[\\s\\S]*?```")
    private val inlineCode = Regex("`[^`\\n]*`")
    private val tag = Regex("(?:^|[\\s(\\[{])#([\\p{L}\\p{N}_/-]+)")

    /** Inline Markdown tags, excluding code where `#` belongs to the language. */
    fun extract(markdown: String): List<String> {
        val searchable = markdown.replace(fencedCode, "").replace(inlineCode, "")
        val seen = linkedMapOf<String, String>()
        for (hit in tag.findAll(searchable)) {
            val value = hit.groupValues[1]
            seen.putIfAbsent(value.lowercase(), value)
        }
        return seen.values.toList()
    }

    fun merge(vararg groups: List<String>): List<String> {
        val seen = linkedMapOf<String, String>()
        for (value in groups.asSequence().flatten()) {
            val clean = value.removePrefix("#").trim()
            if (clean.isNotEmpty()) seen.putIfAbsent(clean.lowercase(), clean)
        }
        return seen.values.toList()
    }
}
