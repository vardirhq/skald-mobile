package no.vardir.skald.core.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.vardir.skald.core.gesh.Bytes
import java.text.Normalizer

/**
 * What Skald puts inside a GESH event, and the validation that runs on
 * everything that comes back out.
 *
 * GESH orders events; it does not merge them and it cannot vouch for them. A
 * compromised or merely buggy relay can withhold, reorder or replay, so a
 * decrypted event is treated as hostile input until every field has been
 * checked — including the paths, which become filenames inside someone's vault.
 *
 * The envelope is deliberately trivial to reimplement:
 *
 *     <JSON header> \n <raw body bytes>
 *
 * A JSON encoder never emits a literal newline, so the first 0x0A is always the
 * boundary. Notes travel in the header, as text. An attachment travels as raw
 * bytes in the body, because base64 in JSON would cost a third of every file
 * for nothing.
 */
object Payload {

    const val VERSION = 1

    /** An attachment larger than this is refused before it can earn a 413. */
    const val MAX_ATTACHMENT_BYTES = 30L * 1024 * 1024

    private const val NEWLINE = '\n'.code.toByte()

    private val json = Json { ignoreUnknownKeys = true }

    enum class Kind {
        /** What changed. */
        Delta,

        /** The whole vault, for a device that has been away past the relay's retention. */
        Snapshot,

        /** Exactly one attachment, with its bytes in the body. */
        Blob;

        val wire: String get() = name.lowercase()

        companion object {
            fun fromWire(value: String?): Kind? = entries.firstOrNull { it.wire == value }
        }
    }

    sealed interface FileOp {
        val path: String

        /** Logical clock for the path — monotonic per path, never a wall clock. */
        val rev: Long

        /** The writing device's own clock, for display only. Never used for ordering. */
        val ts: Long
    }

    data class Put(
        override val path: String,
        override val rev: Long,
        override val ts: Long,
        val content: String,
        /** SHA-256 of the UTF-8 content, so a receiver can detect a mangled payload. */
        val hash: String,
    ) : FileOp

    /** An attachment. Its bytes are the event body, not a field. */
    data class PutBin(
        override val path: String,
        override val rev: Long,
        override val ts: Long,
        val hash: String,
        /** Length of the body, checked before the bytes are trusted. */
        val size: Long,
    ) : FileOp

    data class Delete(
        override val path: String,
        override val rev: Long,
        override val ts: Long,
    ) : FileOp

    data class Header(
        val kind: Kind,
        /** The device that wrote this event; also the tiebreak in conflict resolution. */
        val device: String,
        val ts: Long,
        val ops: List<FileOp>,
    )

    data class Event(
        val header: Header,
        /** The attachment bytes for a `blob` event; empty for every other kind. */
        val body: ByteArray = ByteArray(0),
    ) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Event && header == other.header && body.contentEquals(other.body))

        override fun hashCode(): Int = 31 * header.hashCode() + body.contentHashCode()
    }

    class PayloadError(message: String) : Exception(message)

    // ---------- path rules ----------

    private val CONTROL_OR_RESERVED = Regex("[\\u0000-\\u001f<>:\"|?*]")
    private val WINDOWS_RESERVED = Regex("""^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\.|$)""", RegexOption.IGNORE_CASE)
    private val HASH = Regex("""^[0-9a-f]{64}$""")
    private val DRIVE_LETTER = Regex("""^[A-Za-z]:""")

    /**
     * A path is only acceptable if it is a plain vault-relative path. Anything
     * that could escape the vault, hide inside `.skald/`, or land on a reserved
     * Windows name is refused before it can reach the filesystem.
     */
    fun isSafeVaultPath(path: String): Boolean {
        if (path.isEmpty() || path.length > 400) return false
        if (path != Normalizer.normalize(path, Normalizer.Form.NFC)) return false
        if (path.contains('\\') || path.startsWith("/") || path.contains("//")) return false
        if (CONTROL_OR_RESERVED.containsMatchIn(path)) return false
        if (DRIVE_LETTER.containsMatchIn(path)) return false
        return path.split('/').all { seg ->
            seg.isNotEmpty() &&
                seg != "." &&
                seg != ".." &&
                !seg.startsWith(".") &&
                seg == seg.trim() &&
                !seg.endsWith(".") &&
                !WINDOWS_RESERVED.containsMatchIn(seg)
        }
    }

    /** A note: a safe path that is Markdown. */
    fun isNotePath(path: String): Boolean = isSafeVaultPath(path) && path.endsWith(".md", ignoreCase = true)

    /** An attachment: a safe path that is anything but Markdown. */
    fun isAttachmentPath(path: String): Boolean = isSafeVaultPath(path) && !path.endsWith(".md", ignoreCase = true)

    // ---------- encoding ----------

    fun encode(header: Header, body: ByteArray = ByteArray(0)): ByteArray {
        val obj = buildJsonObject {
            put("v", VERSION)
            put("kind", header.kind.wire)
            put("device", header.device)
            put("ts", header.ts)
            put("ops", buildJsonArray { header.ops.forEach { add(encodeOp(it)) } })
        }
        val headerBytes = Bytes.utf8(obj.toString())
        val out = ByteArray(headerBytes.size + 1 + body.size)
        headerBytes.copyInto(out, 0)
        out[headerBytes.size] = NEWLINE
        body.copyInto(out, headerBytes.size + 1)
        return out
    }

    private fun encodeOp(op: FileOp): JsonObject = buildJsonObject {
        when (op) {
            is Put -> {
                put("op", "put")
                put("path", op.path)
                put("rev", op.rev)
                put("ts", op.ts)
                put("content", op.content)
                put("hash", op.hash)
            }
            is PutBin -> {
                put("op", "putBin")
                put("path", op.path)
                put("rev", op.rev)
                put("ts", op.ts)
                put("hash", op.hash)
                put("size", op.size)
            }
            is Delete -> {
                put("op", "del")
                put("path", op.path)
                put("rev", op.rev)
                put("ts", op.ts)
            }
        }
    }

    // ---------- decoding ----------

    /** Parses and fully validates a decrypted event. Throws [PayloadError] on anything off. */
    fun decode(bytes: ByteArray): Event {
        val split = bytes.indexOf(NEWLINE)
        if (split == -1) throw PayloadError("The event has no header")
        val body = bytes.copyOfRange(split + 1, bytes.size)

        val root = try {
            json.parseToJsonElement(Bytes.utf8(bytes.copyOfRange(0, split))) as? JsonObject
                ?: throw PayloadError("The event did not contain readable Skald data")
        } catch (e: PayloadError) {
            throw e
        } catch (_: Exception) {
            throw PayloadError("The event did not contain readable Skald data")
        }

        val version = root.int("v")
        if (version != VERSION.toLong()) {
            throw PayloadError("This event was written by a different version of Skald (v${version ?: "?"})")
        }
        val kind = Kind.fromWire(root.string("kind")) ?: throw PayloadError("The event has an unknown kind")
        val device = root.string("device")?.takeIf { it.isNotEmpty() } ?: throw PayloadError("The event names no device")
        val ts = root.int("ts")?.takeIf { it >= 0 } ?: throw PayloadError("The event has no usable timestamp")

        val rawOps = (root["ops"] as? JsonArray) ?: throw PayloadError("The event carries no operations")
        if (rawOps.size > 20_000) throw PayloadError("The event carries implausibly many operations")
        val ops = rawOps.mapIndexed { i, raw -> parseOp(raw as? JsonObject ?: JsonObject(emptyMap()), i) }

        if (kind == Kind.Blob) {
            // One attachment per event, and the body has to be exactly what the
            // header promised before a single byte of it is written anywhere.
            val only = ops.singleOrNull() as? PutBin
                ?: throw PayloadError("An attachment event must carry exactly one attachment")
            if (body.size.toLong() != only.size) {
                throw PayloadError("The attachment is not the length the event declared")
            }
        } else {
            if (body.isNotEmpty()) throw PayloadError("A note event must not carry a body")
            if (ops.any { it is PutBin }) throw PayloadError("An attachment must travel in its own event")
        }

        return Event(Header(kind, device, ts, ops), body)
    }

    private fun parseOp(o: JsonObject, index: Int): FileOp {
        val where = "operation ${index + 1}"
        val path = o.string("path")
        if (path == null || !isSafeVaultPath(path)) throw PayloadError("$where names a path Skald will not write")
        val rev = o.int("rev")?.takeIf { it >= 0 } ?: throw PayloadError("$where has no usable revision")
        val ts = o.int("ts")?.takeIf { it >= 0 } ?: throw PayloadError("$where has no usable timestamp")

        return when (o.string("op")) {
            "del" -> Delete(path, rev, ts)

            "put" -> {
                if (!isNotePath(path)) throw PayloadError("$where sends a note that is not Markdown")
                val content = o.string("content") ?: throw PayloadError("$where carries no content")
                val hash = o.string("hash")?.takeIf { HASH.matches(it) }
                    ?: throw PayloadError("$where carries no usable content hash")
                Put(path, rev, ts, content, hash)
            }

            "putBin" -> {
                if (!isAttachmentPath(path)) throw PayloadError("$where sends a Markdown file as an attachment")
                val hash = o.string("hash")?.takeIf { HASH.matches(it) }
                    ?: throw PayloadError("$where carries no usable content hash")
                val size = o.int("size")?.takeIf { it in 0..MAX_ATTACHMENT_BYTES }
                    ?: throw PayloadError("$where declares an implausible attachment size")
                PutBin(path, rev, ts, hash, size)
            }

            else -> throw PayloadError("$where has an unknown kind")
        }
    }

    private fun JsonObject.string(field: String): String? =
        (this[field] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(field: String): Long? {
        val prim = (this[field] as? JsonPrimitive)?.takeIf { !it.isString } ?: return null
        return prim.content.toDoubleOrNull()?.takeIf { it == Math.floor(it) && !it.isInfinite() }?.toLong()
    }

    private fun ByteArray.indexOf(value: Byte): Int {
        for (i in indices) if (this[i] == value) return i
        return -1
    }

    /**
     * Splits ops into events that stay under the relay's body limit. A single
     * note larger than the limit is left in its own batch: it will fail with a
     * 413 the user can act on, rather than being silently dropped.
     */
    fun batch(ops: List<FileOp>, maxBytes: Int = MAX_EVENT_BYTES): List<List<FileOp>> {
        val batches = mutableListOf<List<FileOp>>()
        var current = mutableListOf<FileOp>()
        var size = 0L
        for (op in ops) {
            val cost = when (op) {
                is Put -> op.content.length + op.path.length + 200L
                else -> op.path.length + 120L
            }
            if (current.isNotEmpty() && size + cost > maxBytes) {
                batches += current
                current = mutableListOf()
                size = 0
            }
            current += op
            size += cost
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    /** Roughly a quarter of the 32 MiB default upload limit, leaving room for JSON overhead. */
    const val MAX_EVENT_BYTES = 6 * 1024 * 1024
}
