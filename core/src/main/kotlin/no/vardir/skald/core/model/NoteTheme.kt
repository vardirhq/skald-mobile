package no.vardir.skald.core.model

/**
 * A resolved user-authored note theme for a single note.
 * Values are the portable CSS custom-property subset understood by Android.
 * The original CSS remains the source of truth in `themes/<name>.css`.
 */
data class NoteThemeSpec(
    val name: String,
    val contractVersion: Int = 1,
    val tokens: Map<String, String> = emptyMap(),
)
