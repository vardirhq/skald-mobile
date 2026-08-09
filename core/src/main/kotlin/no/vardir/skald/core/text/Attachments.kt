package no.vardir.skald.core.text

import no.vardir.skald.core.model.AttachmentKind
import java.net.URLDecoder
import java.net.URLEncoder

/** Markdown links that point at files in the vault rather than at other notes. */
object Attachments {

    data class ParsedLink(val target: String, val label: String, val embedded: Boolean)

    private val LINK_RE = Regex("""(!)?\[([^\]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
    private val EXTERNAL = Regex("""^(?:[a-z][a-z0-9+.-]*:|#)""", RegexOption.IGNORE_CASE)

    fun isExternal(target: String): Boolean = EXTERNAL.containsMatchIn(target)

    fun links(markdown: String): List<ParsedLink> =
        LINK_RE.findAll(markdown).mapNotNull { m ->
            val target = m.groupValues[3].trim('<', '>')
            if (isExternal(target)) null
            else ParsedLink(target, m.groupValues[2], m.groupValues[1] == "!")
        }.toList()

    /**
     * Resolve a written target against the note that contains it. Returns null
     * when the target escapes the vault or lands inside `.skald/`, which is
     * per-device state and never an attachment.
     */
    fun resolvePath(notePath: String, target: String): String? {
        val withoutSuffix = target.split('?', '#').first()
        val decoded = try {
            URLDecoder.decode(withoutSuffix, Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val clean = decoded.replace('\\', '/')
        val parts = if (clean.startsWith("/")) {
            mutableListOf()
        } else {
            notePath.split('/').dropLast(1).filter { it.isNotEmpty() }.toMutableList()
        }
        for (part in clean.split('/')) {
            when {
                part.isEmpty() || part == "." -> Unit
                part == ".." -> {
                    if (parts.isEmpty()) return null
                    parts.removeAt(parts.size - 1)
                }
                else -> parts += part
            }
        }
        val resolved = parts.joinToString("/")
        return if (resolved.isEmpty() || resolved == ".skald" || resolved.startsWith(".skald/")) null else resolved
    }

    /** The relative, percent-encoded target to write when linking a file into a note. */
    fun relativeTarget(notePath: String, attachmentPath: String): String {
        val from = notePath.split('/').dropLast(1).filter { it.isNotEmpty() }
        val to = attachmentPath.split('/').filter { it.isNotEmpty() }
        var common = 0
        while (common < from.size && common < to.size && from[common] == to[common]) common++
        val relative = List(from.size - common) { ".." } + to.drop(common)
        return relative.joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20") }
    }

    private val MIMES = mapOf(
        "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
        "gif" to "image/gif", "webp" to "image/webp", "svg" to "image/svg+xml", "bmp" to "image/bmp",
        "heic" to "image/heic",
        "pdf" to "application/pdf",
        "mp3" to "audio/mpeg", "wav" to "audio/wav", "ogg" to "audio/ogg", "m4a" to "audio/mp4",
        "mp4" to "video/mp4", "webm" to "video/webm",
        "txt" to "text/plain", "csv" to "text/csv", "json" to "application/json", "zip" to "application/zip",
    )

    fun mime(name: String, provided: String = ""): String {
        if (provided.isNotEmpty()) return provided
        val ext = name.substringAfterLast('.', "").lowercase()
        return MIMES[ext] ?: "application/octet-stream"
    }

    fun kind(name: String, mime: String = ""): AttachmentKind = when {
        this.mime(name, mime).startsWith("image/") -> AttachmentKind.Image
        this.mime(name, mime) == "application/pdf" -> AttachmentKind.Pdf
        this.mime(name, mime).startsWith("audio/") -> AttachmentKind.Audio
        this.mime(name, mime).startsWith("video/") -> AttachmentKind.Video
        else -> AttachmentKind.File
    }

    fun markdownFor(notePath: String, attachmentPath: String, displayName: String, kind: AttachmentKind): String {
        val target = relativeTarget(notePath, attachmentPath)
        val label = displayName.replace(Regex("""[\[\]\\]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifEmpty { "attachment" }
        return if (kind == AttachmentKind.Image) "![$label]($target)" else "[$label]($target)"
    }
}
