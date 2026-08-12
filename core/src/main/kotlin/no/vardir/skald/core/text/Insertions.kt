package no.vardir.skald.core.text

/** Portable Markdown snippets used by editor insertion surfaces on every Android client. */
object Insertions {
    data class Template(
        val markdown: String,
        val placeholder: String? = null,
        val block: Boolean = true,
    )

    /** The rewritten text and the selection that should survive into it. */
    data class Edit(val text: String, val start: Int, val end: Int)

    fun apply(text: String, start: Int, end: Int, template: Template): Edit {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val selected = text.substring(from, to)
        val placeholderAt = template.placeholder?.let(template.markdown::indexOf) ?: -1
        val markdown = if (selected.isNotEmpty() && placeholderAt >= 0) {
            template.markdown.replaceRange(
                placeholderAt,
                placeholderAt + requireNotNull(template.placeholder).length,
                selected,
            )
        } else {
            template.markdown
        }

        val before = text.substring(0, from)
        val after = text.substring(to)
        val prefix = if (template.block) blockPrefix(before) else ""
        val suffix = if (template.block) blockSuffix(after) else ""
        val insertedAt = before.length + prefix.length
        val next = before + prefix + markdown + suffix + after

        if (selected.isEmpty() && placeholderAt >= 0) {
            val length = requireNotNull(template.placeholder).length
            return Edit(next, insertedAt + placeholderAt, insertedAt + placeholderAt + length)
        }
        val caret = insertedAt + markdown.length
        return Edit(next, caret, caret)
    }

    private fun blockPrefix(before: String): String = when {
        before.isEmpty() || before.endsWith("\n\n") -> ""
        before.endsWith("\n") -> "\n"
        else -> "\n\n"
    }

    private fun blockSuffix(after: String): String = when {
        after.isEmpty() -> "\n"
        after.startsWith("\n\n") -> ""
        after.startsWith("\n") -> "\n"
        else -> "\n\n"
    }
}
