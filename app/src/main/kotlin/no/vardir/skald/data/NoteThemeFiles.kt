package no.vardir.skald.data

import java.io.File
import no.vardir.skald.core.model.NoteThemeSpec
import no.vardir.skald.core.model.SchemaName
import no.vardir.skald.core.model.VaultSettings
import no.vardir.skald.core.text.NoteThemes

private val SAFE_THEME_NAME = Regex("""^[A-Za-z0-9_-]+$""")

/**
 * Resolve desktop-compatible theme precedence and read only the portable token
 * contract. CSS selectors are never executed on Android.
 */
fun FileVault.resolveNoteTheme(
    frontmatter: Map<String, Any?>,
    schema: SchemaName,
    settings: VaultSettings,
): NoteThemeSpec? {
    val name = NoteThemes.resolveName(
        noteStyle = frontmatter["style"],
        schema = schema.name,
        schemaDefaults = settings.schemaNoteThemes,
        vaultDefault = settings.defaultNoteTheme,
    ) ?: return null
    if (!SAFE_THEME_NAME.matches(name)) return null

    val themesDir = File(root, "themes")
    val file = File(themesDir, "$name.css")
    val canonicalDir = runCatching { themesDir.canonicalFile }.getOrNull() ?: return null
    val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
    if (canonical.parentFile != canonicalDir || !canonical.isFile) return null

    val css = runCatching { canonical.readText() }.getOrNull() ?: return null
    val parsed = NoteThemes.parse(name, css)
    // A future contract must be implemented intentionally rather than silently
    // pretending v1 token semantics still apply.
    if (parsed.contractVersion != 1) return null
    return NoteThemeSpec(parsed.name, parsed.contractVersion, parsed.tokens)
}
