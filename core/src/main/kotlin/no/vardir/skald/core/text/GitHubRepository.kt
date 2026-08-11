package no.vardir.skald.core.text

/** The portable `owner/repository` contract shared with Skald desktop. */
object GitHubRepository {
    private val part = Regex("""^[A-Za-z0-9_.-]+$""")
    private val url = Regex("""^https?://github\.com/([^/]+)/([^/?#]+)(?:[/].*)?$""", RegexOption.IGNORE_CASE)

    fun normalize(input: Any?): String? {
        var value = (input as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        value = value.replace(Regex("""^github:""", RegexOption.IGNORE_CASE), "")
        val match = url.matchEntire(value)
        if (Regex("""^[a-z][a-z\d+.-]*://""", RegexOption.IGNORE_CASE).containsMatchIn(value)) {
            if (match == null) return null
            value = "${match.groupValues[1]}/${match.groupValues[2]}"
        }
        value = value.replace(Regex("""\.git$""", RegexOption.IGNORE_CASE), "").trim('/')
        val parts = value.split('/')
        if (parts.size != 2 || parts.any { !part.matches(it) || it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }

    fun url(repo: String): String? = normalize(repo)?.let { "https://github.com/$it" }
}
