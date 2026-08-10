package no.vardir.skald.core.gesh

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The half of GESH the relay is designed never to learn.
 *
 * Everything uploaded is sealed here first: AES-256-GCM with a fresh 96-bit
 * nonce per event, the nonce prepended so the blob is self-describing. The
 * content key is generated on the first device and only ever leaves it through
 * the `#k=` fragment of a pairing URI.
 *
 * The parameters match the desktop client's WebCrypto usage exactly — 12-byte
 * IV, 128-bit tag — because both ends have to open each other's events.
 */
object Crypto {

    const val NONCE_BYTES = 12
    const val KEY_BYTES = 32
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    class DecryptionFailed(message: String) : Exception(message)

    fun generateContentKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256, random) }.generateKey()

    fun importContentKey(raw: ByteArray): SecretKey {
        require(raw.size == KEY_BYTES) { "A content key must be 32 bytes" }
        return SecretKeySpec(raw, "AES")
    }

    fun exportContentKey(key: SecretKey): ByteArray = key.encoded

    fun contentKeyToBase64Url(key: SecretKey): String = Bytes.base64UrlEncode(exportContentKey(key))

    fun contentKeyFromBase64Url(text: String): SecretKey = importContentKey(Bytes.base64UrlDecode(text))

    /** Seal a payload into the exact bytes GESH will store: nonce ‖ ciphertext ‖ tag. */
    fun seal(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return Bytes.concat(iv, cipher.doFinal(plaintext))
    }

    /**
     * Open a blob downloaded from the relay. A wrong key, a truncated body and a
     * tampered tag all surface as the same failure — the caller must treat the
     * result as untrusted input either way.
     */
    fun open(key: SecretKey, blob: ByteArray): ByteArray {
        if (blob.size <= NONCE_BYTES) throw DecryptionFailed("Event blob is too short to be sealed")
        val iv = blob.copyOfRange(0, NONCE_BYTES)
        val body = blob.copyOfRange(NONCE_BYTES, blob.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(body)
        } catch (e: Exception) {
            throw DecryptionFailed("Could not decrypt event — wrong content key or damaged data")
        }
    }

    /** Content digest used for change detection and conflict comparison. */
    fun sha256Hex(bytes: ByteArray): String =
        Bytes.toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun sha256Hex(text: String): String = sha256Hex(Bytes.utf8(text))
}
