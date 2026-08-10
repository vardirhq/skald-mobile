package no.vardir.skald.core.gesh

import java.util.Base64

/**
 * base64 / base64url / UTF-8 / hex, in the exact shapes the desktop client
 * uses. Written out rather than assumed, because the two platforms have to
 * agree byte for byte on what a content key and a hash look like.
 */
object Bytes {

    private val URL_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val URL_DECODER: Base64.Decoder = Base64.getUrlDecoder()

    fun utf8(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

    fun utf8(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

    fun base64UrlEncode(bytes: ByteArray): String = URL_ENCODER.encodeToString(bytes)

    /** Tolerates padding and the standard alphabet, the way the web client does. */
    fun base64UrlDecode(text: String): ByteArray {
        val normalized = text.trim().trimEnd('=').replace('+', '-').replace('/', '_')
        return URL_DECODER.decode(normalized)
    }

    private const val HEX = "0123456789abcdef"

    fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) {
            val v = b.toInt() and 0xff
            append(HEX[v ushr 4])
            append(HEX[v and 0x0f])
        }
    }

    fun concat(a: ByteArray, b: ByteArray): ByteArray {
        val out = ByteArray(a.size + b.size)
        a.copyInto(out, 0)
        b.copyInto(out, a.size)
        return out
    }
}
