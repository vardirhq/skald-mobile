package no.vardir.skald.core.gesh

import java.security.SecureRandom

/**
 * GESH restricts every identifier that can become a path component and answers
 * 400 to anything else before it reaches storage. Validating here turns a bad
 * identifier into a local error with a useful message instead of a round trip.
 */
object Ids {

    /** `appId`, `rootId`, `deviceId`, `eventId`: 1–128 ASCII letters, digits, `-`, `_`. */
    private val IDENTIFIER = Regex("""^[A-Za-z0-9_-]{1,128}$""")

    /** A handle is narrower: 3–64 lowercase letters, digits or `-`. */
    private val HANDLE = Regex("""^[a-z0-9-]{3,64}$""")

    private val random = SecureRandom()

    fun isIdentifier(value: String): Boolean = IDENTIFIER.matches(value)

    fun isHandle(value: String): Boolean = HANDLE.matches(value)

    fun requireIdentifier(kind: String, value: String): String {
        require(isIdentifier(value)) { "$kind must be 1–128 characters of letters, digits, \"-\" or \"_\"" }
        return value
    }

    fun requireHandle(value: String): String {
        require(isHandle(value)) { "A handle must be 3–64 characters of lowercase letters, digits or \"-\"" }
        return value
    }

    /**
     * Pairing codes are meant to be read aloud, so case and the grouping dash
     * carry no meaning. GESH normalizes them server-side; doing it here too
     * means a typed code is not rejected before it is ever sent.
     */
    fun normalizeEnrollmentCode(code: String): String =
        code.filter { it.isLetterOrDigit() && it.code < 128 }.lowercase()

    /** Formats a normalized code back into the grouped shape GESH prints. */
    fun formatEnrollmentCode(code: String): String {
        val clean = normalizeEnrollmentCode(code).uppercase()
        return if (clean.length != 10) clean else "${clean.take(5)}-${clean.drop(5)}"
    }

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    private fun randomToken(length: Int): String {
        val bytes = ByteArray(length).also(random::nextBytes)
        return bytes.map { ALPHABET[(it.toInt() and 0xff) % ALPHABET.length] }.joinToString("")
    }

    /**
     * Chosen by the app, never typed by a person. The prefix only makes a device
     * list readable; the suffix is what makes it unique. Reusing one deliberately
     * replaces that device's credential, which is how a reinstalled phone
     * recovers without becoming a second device.
     */
    fun newDeviceId(prefix: String = "phone"): String {
        val clean = prefix.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(24).ifEmpty { "device" }
        return "${clean}_${randomToken(10)}"
    }

    /** Event IDs must never repeat on a root, even across erasure. */
    fun newEventId(): String = "evt_${java.lang.Long.toString(System.currentTimeMillis(), 36)}_${randomToken(16)}"
}
