package no.vardir.skald.core.text

/**
 * The YAML-ish subset Skald writes: scalars, quoted strings, inline arrays and
 * block lists. Anything more exotic survives as a string rather than being
 * dropped, because the file on disk belongs to the person, not to us.
 */
object Frontmatter {

    data class ParsedNote(
        val frontmatter: Map<String, Any?>,
        val body: String,
        /** 0-based line index where the body starts in the raw content. */
        val bodyStartLine: Int,
        val hasFrontmatter: Boolean,
    )

    private val FENCE = Regex("""^---[ \t]*\r?\n([\s\S]*?)\r?\n---[ \t]*\r?\n?""")
    private val LIST_ITEM = Regex("""^\s+-\s+(.*)$""")
    private val KEY_VALUE = Regex("""^([A-Za-z0-9_-]+):\s*(.*)$""")
    private val NUMBER = Regex("""^-?\d+(\.\d+)?$""")
    private val DATEISH = Regex("""^\d{4}-\d{2}""")

    fun parse(raw: String): ParsedNote {
        val match = FENCE.find(raw)
            ?: return ParsedNote(emptyMap(), raw, 0, hasFrontmatter = false)

        val fmText = match.groupValues[1]
        val body = raw.substring(match.value.length)
        val bodyStartLine = match.value.count { it == '\n' }

        val frontmatter = LinkedHashMap<String, Any?>()
        // A key with an empty value may be opening a block list; remember which
        // keys did so, to tell `tags:` (an empty list) from `tags:` (a header).
        val openedAsBlock = LinkedHashSet<String>()
        val gotItems = LinkedHashSet<String>()
        var currentListKey: String? = null

        for (line in fmText.split(Regex("\r?\n"))) {
            if (line.isBlank() || line.trim().startsWith("#")) continue

            val item = LIST_ITEM.find(line)
            val listKey = currentListKey
            if (item != null && listKey != null) {
                @Suppress("UNCHECKED_CAST")
                val list = (frontmatter[listKey] as? MutableList<Any?>) ?: mutableListOf()
                list.add(coerceScalar(item.groupValues[1].trim()))
                frontmatter[listKey] = list
                gotItems += listKey
                continue
            }

            val kv = KEY_VALUE.find(line) ?: continue
            val key = kv.groupValues[1]
            val value = kv.groupValues[2].trim()

            if (value.isEmpty()) {
                currentListKey = key
                openedAsBlock += key
                frontmatter[key] = mutableListOf<Any?>()
                continue
            }
            currentListKey = null

            frontmatter[key] = if (value.startsWith("[") && value.endsWith("]")) {
                val inner = value.substring(1, value.length - 1).trim()
                if (inner.isEmpty()) mutableListOf() else inner.split(",").map { coerceScalar(it.trim()) }.toMutableList()
            } else {
                coerceScalar(value)
            }
        }

        // A key that opened a block list and never received an item was just an
        // empty value, not a list. Drop it rather than inventing `[]`.
        for (key in openedAsBlock) {
            if (key !in gotItems) frontmatter.remove(key)
        }

        return ParsedNote(frontmatter, body, bodyStartLine, hasFrontmatter = true)
    }

    private fun coerceScalar(v: String): Any? {
        val unquoted = v.removeSurrounding("\"").let { if (it != v) it else v.removeSurrounding("'") }
        if (unquoted != v) return unquoted
        return when {
            v == "true" -> true
            v == "false" -> false
            v == "null" || v == "~" -> null
            NUMBER.matches(v) && !DATEISH.containsMatchIn(v) -> v.toDoubleOrNull()?.let {
                if (it == it.toLong().toDouble()) it.toLong() else it
            } ?: v
            else -> v
        }
    }

    fun serialize(frontmatter: Map<String, Any?>, body: String): String {
        if (frontmatter.isEmpty()) return body
        val lines = frontmatter.entries.map { (key, value) ->
            when (value) {
                is List<*> -> "$key: [${value.joinToString(", ") { serializeScalar(it) }}]"
                else -> "$key: ${serializeScalar(value)}"
            }
        }
        return "---\n${lines.joinToString("\n")}\n---\n\n${body.trimStart('\n')}"
    }

    private val NEEDS_QUOTES = Regex("""[:#\[\]{}"'\n]""")

    private fun serializeScalar(v: Any?): String = when (v) {
        null -> "null"
        is Boolean, is Number -> v.toString()
        else -> {
            val s = v.toString()
            if (NEEDS_QUOTES.containsMatchIn(s) || s != s.trim()) quote(s) else s
        }
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (ch in s) when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\t' -> append("\\t")
            else -> append(ch)
        }
        append('"')
    }

    /** Read `tags:` in either of the shapes we write, plus a bare comma string. */
    fun tagsOf(frontmatter: Map<String, Any?>): List<String> {
        return when (val raw = frontmatter["tags"]) {
            is List<*> -> raw.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
            is String -> raw.split(",", " ").map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }
}
