package no.vardir.skald.core

import no.vardir.skald.core.gesh.Bytes
import no.vardir.skald.core.gesh.HttpTransport
import no.vardir.skald.core.model.NoteHistoryReason
import no.vardir.skald.core.sync.RootSecrets
import no.vardir.skald.core.sync.SecretStore
import no.vardir.skald.core.sync.SyncAsset
import no.vardir.skald.core.sync.SyncNote
import no.vardir.skald.core.sync.SyncStateStore
import no.vardir.skald.core.sync.SyncVault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * An in-memory GESH, encoding the protocol as it is *documented* — so a sync
 * test exercises the real client, the real sealing and the real merge rules,
 * and only the socket is missing.
 */
class FakeGesh(private val publicUrl: String? = "https://relay.test") {

    private class Root(
        val appId: String,
        val rootToken: String,
        var handle: String?,
    ) {
        val deviceTokens = linkedMapOf<String, String>()
        val enrolledAtMs = linkedMapOf<String, Long>()
        val ackCursors = linkedMapOf<String, Long>()
        val lastSeen = linkedMapOf<String, Long>()
        val events = mutableListOf<StoredEvent>()
        val pendingCodes = linkedMapOf<String, Long>()
    }

    private class StoredEvent(
        val cursor: Long,
        val deviceId: String,
        val eventId: String,
        val blob: ByteArray,
        val createdAtMs: Long,
    )

    private val roots = linkedMapOf<String, Root>()
    private var nextCursor = 0L
    private var nextCode = 0

    /** Set to a status to make the next matching call fail, for backoff tests. */
    var failNextPut: Int? = null

    val transport: HttpTransport = object : HttpTransport {
        override fun execute(call: HttpTransport.Call): HttpTransport.Reply = handle(call)
    }

    private fun handle(call: HttpTransport.Call): HttpTransport.Reply {
        val url = call.url.substringAfter("://").substringAfter('/')
        val path = "/" + url.substringBefore('?')
        val query = url.substringAfter('?', "")
        val segments = path.trim('/').split('/')
        val token = call.headers["Authorization"]?.removePrefix("Bearer ")

        return when {
            path == "/health" -> json(200, buildJsonObject { put("ok", true) })

            path == "/v1/roots" && call.method == "POST" -> provision(call)

            path == "/v1/enroll" && call.method == "POST" -> enroll(call)

            segments.size == 3 && segments[1] == "roots" && call.method == "GET" -> {
                val root = roots.entries.firstOrNull { it.value.handle == segments[2] }
                    ?: return error(404, "no such handle")
                json(200, buildJsonObject {
                    put("app_id", root.value.appId)
                    put("root_id", root.key)
                })
            }

            segments.getOrNull(1) == "admin" -> admin(call, segments, token)

            segments.getOrNull(1) == "sync" -> sync(call, segments, query, token)

            else -> error(404, "no route")
        }
    }

    // ---------- routes ----------

    private fun provision(call: HttpTransport.Call): HttpTransport.Reply {
        val body = body(call)
        val appId = body.string("appId") ?: return error(400, "appId")
        val deviceId = body.string("deviceId") ?: return error(400, "deviceId")
        val rootId = "root_${roots.size + 1}"
        val root = Root(appId, "root-token-$rootId", body.string("handle"))
        root.deviceTokens[deviceId] = "device-token-$rootId-$deviceId"
        root.enrolledAtMs[deviceId] = 1_000L
        roots[rootId] = root
        return json(201, buildJsonObject {
            put("app_id", appId)
            put("root_id", rootId)
            root.handle?.let { put("handle", it) }
            put("device_id", deviceId)
            put("root_token", root.rootToken)
            put("device_token", root.deviceTokens.getValue(deviceId))
        })
    }

    private fun enroll(call: HttpTransport.Call): HttpTransport.Reply {
        val body = body(call)
        val code = body.string("code") ?: return error(400, "code")
        val deviceId = body.string("deviceId") ?: return error(400, "deviceId")
        val entry = roots.entries.firstOrNull { code in it.value.pendingCodes }
            ?: return error(401, "unknown or expired code")
        val root = entry.value
        root.pendingCodes.remove(code) // single use
        val issued = "device-token-${entry.key}-$deviceId"
        root.deviceTokens[deviceId] = issued
        root.enrolledAtMs.putIfAbsent(deviceId, 2_000L)
        return json(201, buildJsonObject {
            put("app_id", root.appId)
            put("root_id", entry.key)
            put("device_id", deviceId)
            put("token", issued)
        })
    }

    private fun admin(
        call: HttpTransport.Call,
        segments: List<String>,
        token: String?,
    ): HttpTransport.Reply {
        // /v1/admin/{appId}/{rootId}/...
        val rootId = segments.getOrNull(3) ?: return error(404, "no root")
        val root = roots[rootId] ?: return error(404, "no root")
        if (token != root.rootToken) return error(403, "root credential required")
        val tail = segments.drop(4)

        return when {
            tail == listOf("enrollments") && call.method == "POST" -> {
                val code = "code%05d".format(nextCode++).let { it.filter(Char::isLetterOrDigit).lowercase() }
                root.pendingCodes[code] = System.currentTimeMillis() + 600_000
                json(201, buildJsonObject {
                    put("code", code)
                    put("expires_at_ms", System.currentTimeMillis() + 600_000)
                    if (publicUrl != null) {
                        put("pairing_uri", "gesh://pair?s=${publicUrl.replace(":", "%3A").replace("/", "%2F")}&c=$code")
                    } else {
                        put("pairing_uri", JsonPrimitive(null as String?))
                    }
                })
            }

            tail == listOf("handle") && call.method == "PUT" -> {
                root.handle = body(call).string("handle")
                HttpTransport.Reply(204)
            }

            tail == listOf("devices") && call.method == "GET" ->
                HttpTransport.Reply(
                    200,
                    Bytes.utf8(
                        buildJsonArray {
                            root.deviceTokens.keys.forEach { id ->
                                add(buildJsonObject {
                                    put("device_id", id)
                                    put("enrolled_at_ms", root.enrolledAtMs[id] ?: 0L)
                                    root.lastSeen[id]?.let { put("last_seen_ms", it) }
                                    root.ackCursors[id]?.let { put("ack_cursor", it) }
                                })
                            }
                        }.toString()
                    ),
                    mapOf("Content-Type" to "application/json"),
                )

            tail.size == 2 && tail[0] == "devices" && call.method == "DELETE" -> {
                if (root.deviceTokens.remove(tail[1]) == null) error(404, "no such device")
                else HttpTransport.Reply(204)
            }

            else -> error(404, "no admin route")
        }
    }

    private fun sync(
        call: HttpTransport.Call,
        segments: List<String>,
        query: String,
        token: String?,
    ): HttpTransport.Reply {
        // /v1/sync/{appId}/{rootId}[/{deviceId}[/{eventId}]]
        val rootId = segments.getOrNull(3) ?: return error(404, "no root")
        val root = roots[rootId] ?: return error(404, "no root")
        val caller = root.deviceTokens.entries.firstOrNull { it.value == token }?.key
        val isRootToken = token == root.rootToken
        if (caller == null && !isRootToken) return error(401, "unknown credential")
        val tail = segments.drop(4)

        // A device credential speaks only for itself when *writing*: it may not
        // upload under another device's name or acknowledge on its behalf.
        // Downloading is cross-device by necessity — the feed names the author.
        if (call.method == "PUT" && tail.isNotEmpty() && caller != null && tail[0] != caller) {
            return error(401, "wrong device")
        }

        return when {
            tail.isEmpty() && call.method == "GET" -> {
                val params = query.split('&').filter { it.isNotEmpty() }
                    .associate { it.substringBefore('=') to it.substringAfter('=', "") }
                val after = params["after"]?.toLongOrNull() ?: 0L
                val limit = params["limit"]?.toIntOrNull() ?: 100
                val page = root.events.filter { it.cursor > after }.take(limit)
                json(200, buildJsonObject {
                    put("events", buildJsonArray {
                        page.forEach { event ->
                            add(buildJsonObject {
                                put("cursor", event.cursor)
                                put("app_id", root.appId)
                                put("root_id", rootId)
                                put("device_id", event.deviceId)
                                put("event_id", event.eventId)
                                put("created_at_ms", event.createdAtMs)
                                put("size", event.blob.size)
                            })
                        }
                    })
                    put("next_cursor", page.lastOrNull()?.cursor)
                })
            }

            tail.size == 1 && call.method == "PUT" -> {
                val cursor = body(call).let { (it["ackCursor"] as? JsonPrimitive)?.content?.toLongOrNull() }
                    ?: return error(400, "ackCursor")
                root.ackCursors[tail[0]] = cursor
                root.lastSeen[tail[0]] = System.currentTimeMillis()
                json(200, buildJsonObject {
                    put("device_id", tail[0])
                    put("ack_cursor", cursor)
                    put("last_seen_ms", root.lastSeen.getValue(tail[0]))
                })
            }

            tail.size == 2 && call.method == "PUT" -> {
                failNextPut?.let { status ->
                    failNextPut = null
                    return error(status, "injected failure")
                }
                if (root.events.any { it.deviceId == tail[0] && it.eventId == tail[1] }) {
                    return error(409, "event exists")
                }
                root.events += StoredEvent(++nextCursor, tail[0], tail[1], call.body ?: ByteArray(0), 5_000L)
                HttpTransport.Reply(201)
            }

            tail.size == 2 && call.method == "GET" -> {
                val event = root.events.firstOrNull { it.deviceId == tail[0] && it.eventId == tail[1] }
                    ?: return error(404, "no such event")
                HttpTransport.Reply(200, event.blob, mapOf("Content-Type" to "application/octet-stream"))
            }

            else -> error(404, "no sync route")
        }
    }

    // ---------- helpers ----------

    private val json = Json { ignoreUnknownKeys = true }

    private fun body(call: HttpTransport.Call): JsonObject =
        runCatching { json.parseToJsonElement(Bytes.utf8(call.body ?: ByteArray(0))) as JsonObject }
            .getOrDefault(JsonObject(emptyMap()))

    private fun JsonObject.string(field: String): String? =
        (this[field] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun json(status: Int, obj: JsonObject) =
        HttpTransport.Reply(status, Bytes.utf8(obj.toString()), mapOf("Content-Type" to "application/json"))

    private fun error(status: Int, detail: String) =
        HttpTransport.Reply(
            status,
            Bytes.utf8(buildJsonObject { put("error", detail) }.toString()),
            mapOf("Content-Type" to "application/json"),
        )

    /** How many events the relay is holding, for assertions about pushes. */
    fun eventCount(): Int = roots.values.sumOf { it.events.size }
}

/** A vault that lives entirely in memory, for driving the engine in tests. */
class MemoryVault : SyncVault {
    val notes = linkedMapOf<String, String>()
    val assets = linkedMapOf<String, ByteArray>()
    val assetMtimes = linkedMapOf<String, Long>()
    val history = mutableListOf<Pair<String, String>>()

    override fun syncNotes(): List<SyncNote> = notes.map { SyncNote(it.key, it.value) }

    override fun syncRead(path: String): String? = notes[path]

    override fun syncWrite(path: String, content: String) {
        notes[path] = content
    }

    override fun syncDelete(path: String) {
        notes.remove(path)
    }

    override fun syncAssets(): List<SyncAsset> =
        assets.map { SyncAsset(it.key, it.value.size.toLong(), assetMtimes[it.key] ?: 0L) }

    override fun syncReadAsset(path: String): ByteArray? = assets[path]

    override fun syncWriteAsset(path: String, bytes: ByteArray) {
        assets[path] = bytes
        assetMtimes[path] = System.currentTimeMillis()
    }

    override fun syncDeleteAsset(path: String) {
        assets.remove(path)
        assetMtimes.remove(path)
    }

    override fun captureVersion(path: String, reason: NoteHistoryReason) {
        // What the engine promises: the losing text is never dropped.
        notes[path]?.let { history += path to it }
    }
}

class MemorySecrets : SecretStore {
    private val held = linkedMapOf<String, RootSecrets>()
    override fun load(key: String): RootSecrets? = held[key]
    override fun save(key: String, secrets: RootSecrets) {
        held[key] = secrets
    }

    override fun forget(key: String) {
        held.remove(key)
    }

    override val protected: Boolean get() = true
    override fun requireStore() = Unit
}

class MemoryState : SyncStateStore {
    private var raw: String? = null
    override fun read(): String? = raw
    override fun write(json: String) {
        raw = json
    }

    override fun clear() {
        raw = null
    }
}
