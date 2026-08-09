package no.vardir.skald.core.gesh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException

/**
 * A typed client for the GESH v1 HTTP API.
 *
 * Two conventions the protocol imposes and this file absorbs so callers never
 * see them: request bodies are camelCase while response bodies are snake_case,
 * and errors are only *sometimes* the documented `{"error": "..."}` shape —
 * 413, 415 and 422 come from the framework layer with a plain-text body, so
 * every response is parsed defensively.
 */
class GeshClient(
    baseUrl: String,
    private val transport: HttpTransport = OkHttpTransport(),
) {

    val baseUrl: String = baseUrl.trim().trimEnd('/').also {
        require(Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE).matches(it)) {
            "A sync server address must be an http(s) URL"
        }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class RootRef(val appId: String, val rootId: String)

    data class ProvisionedRoot(
        val appId: String,
        val rootId: String,
        val handle: String?,
        val deviceId: String,
        val rootToken: String,
        val deviceToken: String,
    )

    data class MintedEnrollment(
        val code: String,
        val expiresAtMs: Long,
        /** null unless the operator set `GESH_PUBLIC_URL`. */
        val pairingUri: String?,
    )

    data class RedeemedEnrollment(
        val appId: String,
        val rootId: String,
        val deviceId: String,
        val token: String,
    )

    data class EnrolledDevice(
        val deviceId: String,
        val enrolledAtMs: Long,
        val lastSeenMs: Long?,
        val ackCursor: Long?,
    )

    data class EventMeta(
        val cursor: Long,
        val appId: String,
        val rootId: String,
        val deviceId: String,
        val eventId: String,
        val createdAtMs: Long,
        val size: Long,
    )

    data class EventPage(
        val events: List<EventMeta>,
        /** null when the page was empty — you are caught up. */
        val nextCursor: Long?,
    )

    data class AckResult(val deviceId: String, val ackCursor: Long, val lastSeenMs: Long)

    enum class UploadOutcome { Created, Duplicate }

    // ---------- transport ----------

    private enum class Accept { Json, Bytes, None }

    private class Result(val status: Int, val json: JsonElement?, val bytes: ByteArray)

    private suspend fun request(
        method: String,
        path: String,
        token: String? = null,
        jsonBody: JsonElement? = null,
        binaryBody: ByteArray? = null,
        accept: Accept = Accept.Json,
        allowStatus: Set<Int> = emptySet(),
    ): Result = withContext(Dispatchers.IO) {
        val headers = LinkedHashMap<String, String>()
        if (token != null) headers["Authorization"] = "Bearer $token"

        val body: ByteArray? = when {
            jsonBody != null -> {
                headers["Content-Type"] = "application/json"
                Bytes.utf8(jsonBody.toString())
            }
            binaryBody != null -> {
                headers["Content-Type"] = "application/octet-stream"
                binaryBody
            }
            else -> null
        }

        val reply = try {
            transport.execute(HttpTransport.Call(method, "$baseUrl$path", headers, body))
        } catch (e: IOException) {
            throw GeshUnreachable("The sync server is unreachable ($baseUrl)", e)
        }

        if (reply.status !in 200..299 && reply.status !in allowStatus) throw errorFrom(reply)

        when {
            accept == Accept.Bytes -> Result(reply.status, null, reply.body)
            accept == Accept.None || reply.status == 204 -> Result(reply.status, null, ByteArray(0))
            else -> Result(reply.status, runCatching { json.parseToJsonElement(Bytes.utf8(reply.body)) }.getOrNull(), ByteArray(0))
        }
    }

    private fun errorFrom(reply: HttpTransport.Reply): GeshError {
        val text = Bytes.utf8(reply.body)
        val detail = runCatching {
            json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull() ?: text
        return GeshError(reply.status, detail.take(200), parseRetryAfter(reply.header("retry-after")))
    }

    private fun parseRetryAfter(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        header.trim().toDoubleOrNull()?.let { if (it >= 0) return Math.round(it * 1000) }
        return runCatching {
            val at = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                .parse(header.trim(), java.time.Instant::from).toEpochMilli()
            maxOf(0L, at - System.currentTimeMillis())
        }.getOrNull()
    }

    // ---------- field readers ----------

    private fun JsonElement?.obj(): JsonObject = (this as? JsonObject) ?: JsonObject(emptyMap())

    private fun JsonObject.str(field: String): String {
        val value = (this[field] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (value.isNullOrEmpty()) throw GeshProtocolError("The relay omitted \"$field\"")
        return value
    }

    private fun JsonObject.strOrNull(field: String): String? =
        (this[field] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.longOrNull(field: String): Long? =
        (this[field] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()

    // ---------- unauthenticated ----------

    suspend fun health(): Boolean =
        runCatching { request("GET", "/health").json.obj()["ok"]?.jsonPrimitive?.content == "true" }
            .getOrDefault(false)

    /**
     * Creates the root and returns both credentials. This response is the only
     * time either token exists in readable form — there is no endpoint that
     * returns them again.
     */
    suspend fun provisionRoot(
        appId: String,
        deviceId: String,
        handle: String? = null,
        provisioningSecret: String? = null,
    ): ProvisionedRoot {
        Ids.requireIdentifier("An app id", appId)
        Ids.requireIdentifier("A device id", deviceId)
        handle?.let { Ids.requireHandle(it) }

        val body = buildJsonObject {
            put("appId", appId)
            put("deviceId", deviceId)
            if (handle != null) put("handle", handle)
        }
        val out = request("POST", "/v1/roots", token = provisioningSecret, jsonBody = body).json.obj()
        return ProvisionedRoot(
            appId = out.str("app_id"),
            rootId = out.str("root_id"),
            handle = out.strOrNull("handle"),
            deviceId = out.str("device_id"),
            rootToken = out.str("root_token"),
            deviceToken = out.str("device_token"),
        )
    }

    /** Trade a pairing code for this device's own sync credential. */
    suspend fun redeemEnrollment(code: String, deviceId: String, handle: String? = null): RedeemedEnrollment {
        Ids.requireIdentifier("A device id", deviceId)
        val normalized = Ids.normalizeEnrollmentCode(code)
        require(normalized.isNotEmpty()) { "A pairing code is required" }
        val path = if (handle != null) "/v1/roots/${Ids.requireHandle(handle)}/enroll" else "/v1/enroll"

        val body = buildJsonObject {
            put("code", normalized)
            put("deviceId", deviceId)
        }
        val out = request("POST", path, jsonBody = body).json.obj()
        return RedeemedEnrollment(
            appId = out.str("app_id"),
            rootId = out.str("root_id"),
            deviceId = out.str("device_id"),
            token = out.str("token"),
        )
    }

    /** Resolve a typed name. Returns null when no root holds it. */
    suspend fun resolveHandle(handle: String): RootRef? {
        Ids.requireHandle(handle)
        return try {
            val out = request("GET", "/v1/roots/$handle").json.obj()
            RootRef(out.str("app_id"), out.str("root_id"))
        } catch (e: GeshError) {
            if (e.status == 404) null else throw e
        }
    }

    // ---------- admin plane (root token only) ----------

    suspend fun setHandle(ref: RootRef, rootToken: String, handle: String) {
        Ids.requireHandle(handle)
        request(
            "PUT", "${adminBase(ref)}/handle",
            token = rootToken,
            jsonBody = buildJsonObject { put("handle", handle) },
            accept = Accept.None,
        )
    }

    suspend fun mintEnrollment(ref: RootRef, rootToken: String): MintedEnrollment {
        val out = request("POST", "${adminBase(ref)}/enrollments", token = rootToken).json.obj()
        return MintedEnrollment(
            code = out.str("code"),
            expiresAtMs = out.longOrNull("expires_at_ms") ?: (System.currentTimeMillis() + 600_000),
            pairingUri = out.strOrNull("pairing_uri"),
        )
    }

    suspend fun listDevices(ref: RootRef, rootToken: String): List<EnrolledDevice> {
        val out = request("GET", "${adminBase(ref)}/devices", token = rootToken).json
        val rows = (out as? JsonArray) ?: JsonArray(emptyList())
        return rows.map { row ->
            val r = row.obj()
            EnrolledDevice(
                deviceId = r.str("device_id"),
                enrolledAtMs = r.longOrNull("enrolled_at_ms") ?: 0L,
                lastSeenMs = r.longOrNull("last_seen_ms"),
                ackCursor = r.longOrNull("ack_cursor"),
            )
        }
    }

    suspend fun revokeDevice(ref: RootRef, rootToken: String, deviceId: String) {
        Ids.requireIdentifier("A device id", deviceId)
        request("DELETE", "${adminBase(ref)}/devices/$deviceId", token = rootToken, accept = Accept.None)
    }

    // ---------- sync plane ----------

    /**
     * Uploads one immutable event. A `409` means this event ID is already on the
     * root, which on a retry after a network failure is success, not an error —
     * the previous attempt landed.
     */
    suspend fun putEvent(
        ref: RootRef,
        deviceId: String,
        eventId: String,
        ciphertext: ByteArray,
        token: String,
    ): UploadOutcome {
        Ids.requireIdentifier("A device id", deviceId)
        Ids.requireIdentifier("An event id", eventId)
        val result = request(
            "PUT", "${syncBase(ref)}/$deviceId/$eventId",
            token = token, binaryBody = ciphertext, accept = Accept.None, allowStatus = setOf(409),
        )
        return if (result.status == 409) UploadOutcome.Duplicate else UploadOutcome.Created
    }

    suspend fun listEvents(
        ref: RootRef,
        token: String,
        after: Long? = null,
        limit: Int? = null,
        deviceId: String? = null,
    ): EventPage {
        val params = mutableListOf<String>()
        if (after != null) {
            require(after >= 0) { "A cursor must be a non-negative integer" }
            params += "after=$after"
        }
        if (limit != null) {
            require(limit in 1..500) { "A page limit must be between 1 and 500" }
            params += "limit=$limit"
        }
        if (deviceId != null) params += "deviceId=${Ids.requireIdentifier("A device id", deviceId)}"

        val query = if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        val out = request("GET", "${syncBase(ref)}$query", token = token).json.obj()
        val rows = (out["events"] as? JsonArray) ?: JsonArray(emptyList())
        return EventPage(
            events = rows.map { row ->
                val r = row.obj()
                EventMeta(
                    cursor = r.longOrNull("cursor") ?: 0L,
                    appId = r.str("app_id"),
                    rootId = r.str("root_id"),
                    deviceId = r.str("device_id"),
                    eventId = r.str("event_id"),
                    createdAtMs = r.longOrNull("created_at_ms") ?: 0L,
                    size = r.longOrNull("size") ?: 0L,
                )
            },
            nextCursor = out.longOrNull("next_cursor"),
        )
    }

    /** Returns null for an event the relay has already erased. */
    suspend fun getEvent(ref: RootRef, deviceId: String, eventId: String, token: String): ByteArray? {
        Ids.requireIdentifier("A device id", deviceId)
        Ids.requireIdentifier("An event id", eventId)
        return try {
            request("GET", "${syncBase(ref)}/$deviceId/$eventId", token = token, accept = Accept.Bytes).bytes
        } catch (e: GeshError) {
            if (e.status == 404) null else throw e
        }
    }

    /**
     * Reports the feed consumed up to and including `ackCursor`, and registers
     * this device as an active peer. Acknowledging is destructive — once every
     * peer is past an event the relay erases it — so only call this after the
     * change is durably applied.
     */
    suspend fun ack(ref: RootRef, deviceId: String, token: String, ackCursor: Long): AckResult {
        Ids.requireIdentifier("A device id", deviceId)
        require(ackCursor >= 0) { "An ack cursor must be a non-negative integer" }
        val out = request(
            "PUT", "${syncBase(ref)}/$deviceId",
            token = token,
            jsonBody = buildJsonObject { put("ackCursor", ackCursor) },
        ).json.obj()
        return AckResult(
            deviceId = out.str("device_id"),
            ackCursor = out.longOrNull("ack_cursor") ?: ackCursor,
            lastSeenMs = out.longOrNull("last_seen_ms") ?: System.currentTimeMillis(),
        )
    }

    // ---------- paths ----------

    private fun syncBase(ref: RootRef): String {
        Ids.requireIdentifier("An app id", ref.appId)
        Ids.requireIdentifier("A root id", ref.rootId)
        return "/v1/sync/${ref.appId}/${ref.rootId}"
    }

    private fun adminBase(ref: RootRef): String {
        Ids.requireIdentifier("An app id", ref.appId)
        Ids.requireIdentifier("A root id", ref.rootId)
        return "/v1/admin/${ref.appId}/${ref.rootId}"
    }
}

/** The relay answered, and said no. */
class GeshError(
    val status: Int,
    val detail: String,
    val retryAfterMs: Long?,
) : Exception(messageFor(status, detail)) {

    /** Worth trying again later on its own; a 401 or 409 never is. */
    val isTransient: Boolean get() = status == 429 || status >= 500

    companion object {
        private fun messageFor(status: Int, detail: String): String = when (status) {
            400 -> "The relay rejected the request as malformed (${detail.ifEmpty { "bad request" }})"
            401 -> "The relay did not accept this credential — the device may have been revoked, " +
                "or the pairing code is wrong or expired"
            403 -> "That action needs the root credential, and this device only holds its own"
            404 -> "The relay has no such root, device or event"
            409 -> "That identifier is already taken on this root"
            413 -> "The change is larger than the relay accepts in one event"
            415 -> "The relay rejected the content type of the request"
            422 -> "The relay could not read a required field (${detail.ifEmpty { "unprocessable" }})"
            429 -> "The relay is rate limiting this client — wait before trying again"
            else -> if (status >= 500) "The relay failed to store or read the request"
            else "The relay answered $status${if (detail.isEmpty()) "" else " ($detail)"}"
        }
    }
}

/** The relay could not be reached at all. */
class GeshUnreachable(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The relay answered, but not with what the protocol says it should. */
class GeshProtocolError(message: String) : Exception(message)
