package no.vardir.skald.core.text

/**
 * The cross-platform subset of Skald's desktop note-theme contract.
 *
 * Desktop remains free to execute the complete user-authored CSS file. Android
 * never executes arbitrary CSS; it reads only declared custom properties whose
 * meaning is portable to a native Compose renderer. Unknown selectors and
 * tokens are deliberately ignored.
 */
data class PortableNoteTheme(
    val name: String,
    val contractVersion: Int,
    val tokens: Map<String, String>,
) {
    operator fun get(token: String): String? = tokens[token]
}

object NoteThemes {
    /** Tokens whose intent maps cleanly to a native renderer today. */
    val supportedTokens: Set<String> = setOf(
        "--note-bg",
        "--note-tx",
        "--note-tx-muted",
        "--note-tx-heading",
        "--note-accent",
        "--note-rule",
        "--note-code-bg",
        "--note-quote-border",
        "--note-link",
        "--note-link-missing",
        "--note-task-working",
        "--note-task-blocked",
        "--note-task-overdue",
    )

    private val DECLARATION = Regex("""(--[a-zA-Z0-9-]+)\s*:\s*([^;}]+)\s*;""")

    fun parse(name: String, css: String): PortableNoteTheme {
        val declarations = DECLARATION.findAll(css).associate { match ->
            match.groupValues[1] to match.groupValues[2].trim()
        }
        val version = declarations["--skald-theme"]?.toIntOrNull() ?: 1
        return PortableNoteTheme(
            name = name,
            contractVersion = version,
            tokens = declarations.filterKeys(supportedTokens::contains),
        )
    }

    /** Desktop's precedence: note → schema → vault → built-in surface. */
    fun resolveName(
        noteStyle: Any?,
        schema: String,
        schemaDefaults: Map<String, String>,
        vaultDefault: String?,
    ): String? = (noteStyle as? String)?.trim()?.takeIf(String::isNotEmpty)
        ?: schemaDefaults[schema]?.trim()?.takeIf(String::isNotEmpty)
        ?: vaultDefault?.trim()?.takeIf(String::isNotEmpty)
}
