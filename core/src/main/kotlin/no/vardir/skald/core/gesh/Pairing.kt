package no.vardir.skald.core.gesh

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Pairing URIs, parsed and built by hand rather than through a URI class,
 * because `gesh:` is not a special scheme and platforms disagree about what
 * they do with the authority and fragment of one.
 *
 *     gesh://pair?s=<server>&c=<code>#k=<base64url content key>
 *
 * The query half is what the relay minted. The fragment half is ours, and is
 * the reason one QR code can carry both without GESH ever receiving the key —
 * a fragment is never transmitted to a server.
 */
object Pairing {

    data class Invite(
        /** Base URL of the relay, e.g. `https://gesh.vardir.no`. */
        val server: String,
        /** The pairing code, normalized. */
        val code: String,
        /** base64url content key from the fragment, or null when the URI carried none. */
        val contentKey: String?,
    )

    class MalformedInvite(message: String) : Exception(message)

    private val PAIRING_RE = Regex("""^gesh://pair\?([^#]*)(?:#(.*))?$""", RegexOption.IGNORE_CASE)
    private val HTTP_URL = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)

    private fun parsePairs(input: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (chunk in input.split('&')) {
            if (chunk.isEmpty()) continue
            val eq = chunk.indexOf('=')
            val key = if (eq == -1) chunk else chunk.substring(0, eq)
            val value = if (eq == -1) "" else chunk.substring(eq + 1)
            try {
                out[decode(key)] = decode(value.replace("+", " "))
            } catch (_: IllegalArgumentException) {
                // A malformed escape makes the whole parameter unusable; skip it
                // and let the caller fail on the missing field instead.
            }
        }
        return out
    }

    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8)

    /** Build the query half ourselves, for a relay with no `GESH_PUBLIC_URL` set. */
    fun buildUri(server: String, code: String): String {
        val clean = server.trimEnd('/')
        return "gesh://pair?s=${encode(clean)}&c=${encode(code)}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    /**
     * Append the content key as a fragment. This is the only place the key is
     * ever allowed to appear in a URI — a query parameter or a header would hand
     * it straight to the relay and lose the entire security model.
     */
    fun withContentKey(pairingUri: String, contentKeyBase64Url: String): String {
        require(contentKeyBase64Url.isNotEmpty()) { "A pairing URI needs a content key" }
        return "${pairingUri.substringBefore('#')}#k=$contentKeyBase64Url"
    }

    fun parse(uri: String): Invite {
        val match = PAIRING_RE.find(uri.trim())
            ?: throw MalformedInvite("That does not look like a Skald pairing link")
        val query = parsePairs(match.groupValues[1])
        val fragment = parsePairs(match.groupValues.getOrElse(2) { "" })

        val server = (query["s"] ?: "").trim()
        if (!HTTP_URL.matches(server)) throw MalformedInvite("The pairing link has no usable server address")
        val code = Ids.normalizeEnrollmentCode((query["c"] ?: "").trim())
        if (code.isEmpty()) throw MalformedInvite("The pairing link has no pairing code")

        return Invite(
            server = server.trimEnd('/'),
            code = code,
            contentKey = (fragment["k"] ?: "").trim().ifEmpty { null },
        )
    }
}
