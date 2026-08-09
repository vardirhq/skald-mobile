package no.vardir.skald.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import no.vardir.skald.core.gesh.Crypto
import no.vardir.skald.core.gesh.GeshClient
import no.vardir.skald.core.gesh.GeshError
import no.vardir.skald.core.gesh.Ids
import no.vardir.skald.core.gesh.Pairing
import no.vardir.skald.core.model.NoteHistoryReason
import javax.crypto.SecretKey

/**
 * Everything that knows both what a Skald vault is and what a GESH relay is.
 * The protocol client below it knows nothing about notes; the vault above it
 * knows nothing about sync.
 *
 * One pass is always pull → apply → acknowledge → push, in that order. Pulling
 * first means a conflict is resolved before this device publishes, so the
 * revision it publishes already reflects the merge. Acknowledging is what tells
 * the relay it may erase an event, so it happens only after the change is on
 * disk — never after download, never after decrypt.
 *
 * Scheduling deliberately lives outside: on a phone that is WorkManager's job,
 * not a timer inside a long-lived object.
 */
class SyncEngine(
    private val vault: SyncVault,
    private val secrets: SecretStore,
    private val stateStore: SyncStateStore,
    private val devicePrefix: String = "phone",
    private val makeClient: (String) -> GeshClient = { GeshClient(it) },
) {

    private companion object {
        const val PAGE_LIMIT = 100
        const val TOMBSTONE_KEEP_MS = 90L * 24 * 60 * 60 * 1000
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val lock = Mutex()

    private var state: SyncStateFile? = null
    private var phase: SyncPhase = SyncPhase.Off
    private var lastError: String? = null

    /** Set when a pass completed but had to skip something the user should know about. */
    private var passWarning: String? = null
    private var retryAtMs: Long? = null
    private var pendingPaths = 0
    private var oversize: List<String> = emptyList()

    /**
     * Attachments this relay refused with a 413. Only an upload can discover
     * these — its limit may be lower than the one Skald assumes — so they are
     * remembered across passes rather than recomputed from the vault each time.
     */
    private val relayRejected = linkedSetOf<String>()

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> get() = _status.asStateFlow()

    init {
        state = readState()
        phase = if (state != null) SyncPhase.Idle else SyncPhase.Off
        emit()
    }

    // ---------- state ----------

    private fun readState(): SyncStateFile? = runCatching {
        val raw = stateStore.read() ?: return null
        val parsed = json.decodeFromString<SyncStateFile>(raw)
        if (parsed.version != 1 || parsed.rootId.isEmpty() || parsed.deviceId.isEmpty() || parsed.serverUrl.isEmpty()) {
            null
        } else {
            parsed
        }
    }.getOrNull()

    private fun writeState() {
        val s = state ?: return
        // Tombstones keep a deleted path's clock so a stale re-upload cannot
        // resurrect it. They are only useful for as long as peers can still be
        // carrying that old revision.
        val cutoff = System.currentTimeMillis() - TOMBSTONE_KEEP_MS
        for ((path, at) in s.tombstonedAtMs.entries.toList()) {
            if (at < cutoff) {
                s.tombstonedAtMs.remove(path)
                if (s.files[path]?.hash == Merge.ABSENT) s.files.remove(path)
            }
        }
        for (path in s.assetStamps.keys.toList()) {
            if (path !in s.files) s.assetStamps.remove(path)
        }
        runCatching { stateStore.write(json.encodeToString(SyncStateFile.serializer(), s)) }
    }

    private fun requireState(): SyncStateFile =
        state ?: error("This vault is not connected to a sync server")

    private fun requireSecrets(): RootSecrets {
        val s = requireState()
        return secrets.load(SecretStore.deviceKey(s.appId, s.rootId, s.deviceId))
            ?: error(
                "Skald cannot read this vault's sync credentials. Pair this device again, " +
                    "or disconnect the vault from sync."
            )
    }

    private fun ref(): GeshClient.RootRef {
        val s = requireState()
        return GeshClient.RootRef(s.appId, s.rootId)
    }

    private fun client(): GeshClient = makeClient(requireState().serverUrl)

    // ---------- status ----------

    fun snapshotStatus(): SyncStatus {
        val s = state
        return SyncStatus(
            configured = s != null,
            enabled = s?.enabled ?: false,
            serverUrl = s?.serverUrl,
            appId = s?.appId ?: SYNC_APP_ID,
            rootId = s?.rootId,
            handle = s?.handle,
            deviceId = s?.deviceId,
            isRoot = s?.isRoot ?: false,
            phase = phase,
            lastSyncMs = s?.lastSyncMs,
            lastError = lastError,
            pending = pendingPaths,
            tracked = s?.files?.values?.count { it.hash != Merge.ABSENT } ?: 0,
            secretsProtected = secrets.protected,
            retryAtMs = retryAtMs,
            oversize = oversize,
        )
    }

    private fun emit() {
        _status.value = snapshotStatus()
    }

    // ---------- setup ----------

    /** Provisions a brand-new root. The device that does this becomes the authority. */
    suspend fun connect(
        serverUrl: String,
        handle: String? = null,
        provisioningSecret: String? = null,
    ): SyncStatus = lock.withLock {
        check(state == null) { "This vault is already connected to a sync server" }
        // Check this before provisioning: a root whose credentials we cannot keep
        // is a root nobody can ever use again, and GESH has no delete-root call.
        secrets.requireStore()

        val client = makeClient(serverUrl)
        val deviceId = Ids.newDeviceId(devicePrefix)
        try {
            val root = client.provisionRoot(SYNC_APP_ID, deviceId, handle, provisioningSecret)
            val contentKey = Crypto.contentKeyToBase64Url(Crypto.generateContentKey())
            secrets.save(
                SecretStore.deviceKey(root.appId, root.rootId, root.deviceId),
                RootSecrets(deviceToken = root.deviceToken, contentKey = contentKey, rootToken = root.rootToken),
            )
            state = SyncStateFile(
                serverUrl = client.baseUrl,
                appId = root.appId,
                rootId = root.rootId,
                deviceId = root.deviceId,
                handle = root.handle,
                isRoot = true,
            )
            lastError = null
            phase = SyncPhase.Idle
            writeState()
            emit()
        } catch (e: Exception) {
            fail(e)
        }
        // A fresh root has nothing on it; publish the vault so the next device to
        // pair has something to receive.
        runPass(snapshot = true)
        snapshotStatus()
    }

    /** Redeems a pairing link minted by another device, and adopts its content key. */
    suspend fun pair(pairingUri: String): SyncStatus = lock.withLock {
        check(state == null) { "This vault is already connected to a sync server" }
        val invite = Pairing.parse(pairingUri)
        val contentKey = invite.contentKey
            ?: throw Pairing.MalformedInvite(
                "That pairing link carries no content key, so this device could not read anything it synced"
            )
        // Fail before anything is stored if the key is unusable, or if there is
        // nowhere safe to put it — redeeming burns a single-use code.
        Crypto.contentKeyFromBase64Url(contentKey)
        secrets.requireStore()

        val client = makeClient(invite.server)
        val deviceId = Ids.newDeviceId(devicePrefix)
        try {
            val enrolled = client.redeemEnrollment(invite.code, deviceId)
            if (enrolled.appId != SYNC_APP_ID) {
                error("That pairing link belongs to a different app (${enrolled.appId})")
            }
            secrets.save(
                SecretStore.deviceKey(enrolled.appId, enrolled.rootId, enrolled.deviceId),
                RootSecrets(deviceToken = enrolled.token, contentKey = contentKey),
            )
            state = SyncStateFile(
                serverUrl = client.baseUrl,
                appId = enrolled.appId,
                rootId = enrolled.rootId,
                deviceId = enrolled.deviceId,
                isRoot = false,
            )
            lastError = null
            phase = SyncPhase.Idle
            writeState()
            emit()
        } catch (e: Exception) {
            fail(e)
        }
        runPass(snapshot = false)
        snapshotStatus()
    }

    /**
     * Mints a one-time code and returns the URI to put in a QR code, with the
     * content key appended as a fragment. A fragment is never transmitted, which
     * is what lets one code carry both halves while the relay receives only one.
     */
    suspend fun mintPairing(): PairingTicket = lock.withLock {
        val s = requireState()
        val held = requireSecrets()
        val rootToken = held.rootToken
            ?: error("Only the device that created this sync root can pair another device")
        try {
            val minted = client().mintEnrollment(ref(), rootToken)
            val base = minted.pairingUri ?: Pairing.buildUri(s.serverUrl, minted.code)
            // A new peer starts empty and cannot rebuild state from a feed it was
            // not present for, so the vault has to be on the relay before the code
            // is in anyone's hands. A failure here is not worth withholding the
            // code over — the snapshot can be republished from Settings.
            runCatching { runPass(snapshot = true) }
            PairingTicket(
                code = minted.code,
                displayCode = Ids.formatEnrollmentCode(minted.code),
                expiresAtMs = minted.expiresAtMs,
                uri = Pairing.withContentKey(base, held.contentKey),
                uriIsLocal = minted.pairingUri == null,
            )
        } catch (e: Exception) {
            fail(e)
        }
    }

    suspend fun listDevices(): List<SyncDeviceInfo> = lock.withLock { listDevicesLocked() }

    private suspend fun listDevicesLocked(): List<SyncDeviceInfo> {
        val s = requireState()
        val rootToken = requireSecrets().rootToken
            ?: error("Only the device that created this sync root can list its devices")
        return try {
            client().listDevices(ref(), rootToken).map {
                SyncDeviceInfo(it.deviceId, it.enrolledAtMs, it.lastSeenMs, it.ackCursor, it.deviceId == s.deviceId)
            }
        } catch (e: Exception) {
            fail(e)
        }
    }

    suspend fun revokeDevice(deviceId: String): List<SyncDeviceInfo> = lock.withLock {
        val s = requireState()
        val rootToken = requireSecrets().rootToken
            ?: error("Only the device that created this sync root can revoke a device")
        check(deviceId != s.deviceId) { "A device cannot revoke itself" }
        try {
            client().revokeDevice(ref(), rootToken, deviceId)
        } catch (e: Exception) {
            fail(e)
        }
        listDevicesLocked()
    }

    /**
     * Forgets the root locally. Notes are untouched; other devices keep syncing
     * with each other. Revoking this device from another one is a separate act,
     * and the one that actually stops it talking to the relay.
     */
    fun disconnect(): SyncStatus {
        state?.let { s -> secrets.forget(SecretStore.deviceKey(s.appId, s.rootId, s.deviceId)) }
        runCatching { stateStore.clear() }
        state = null
        phase = SyncPhase.Off
        lastError = null
        retryAtMs = null
        pendingPaths = 0
        oversize = emptyList()
        relayRejected.clear()
        emit()
        return snapshotStatus()
    }

    fun setEnabled(enabled: Boolean): SyncStatus {
        val s = requireState()
        state = s.copy(enabled = enabled)
        if (!enabled) phase = SyncPhase.Idle
        writeState()
        emit()
        return snapshotStatus()
    }

    // ---------- the pass ----------

    /**
     * @param force run even while a rate-limit backoff is in effect, for a pass
     *   the person explicitly asked for.
     */
    suspend fun syncNow(snapshot: Boolean = false, force: Boolean = false): SyncStatus = lock.withLock {
        if (state == null) return@withLock snapshotStatus()
        val backoff = retryAtMs
        if (!force && !snapshot && backoff != null && System.currentTimeMillis() < backoff) {
            return@withLock snapshotStatus()
        }
        runPass(snapshot)
        snapshotStatus()
    }

    private suspend fun runPass(snapshot: Boolean) {
        if (state == null) return
        phase = SyncPhase.Syncing
        emit()
        try {
            passWarning = null
            pull()
            push(full = snapshot)
            state = requireState().copy(lastSyncMs = System.currentTimeMillis())
            lastError = passWarning
            retryAtMs = null
            phase = SyncPhase.Idle
            writeState()
        } catch (e: Exception) {
            lastError = e.message ?: e.toString()
            phase = SyncPhase.Error
            if (e is GeshError) {
                e.retryAfterMs?.let { retryAtMs = System.currentTimeMillis() + it }
                // A rejected credential will not start working on a timer, and
                // retrying it every minute only earns a lockout.
                if (e.status == 401) state = requireState().copy(enabled = false)
            }
            writeState()
        } finally {
            refreshPending()
            emit()
        }
    }

    // ---------- pulling ----------

    private suspend fun pull() {
        var s = requireState()
        val held = requireSecrets()
        val key = Crypto.contentKeyFromBase64Url(held.contentKey)
        val client = client()

        while (true) {
            val page = client.listEvents(ref(), held.deviceToken, after = s.cursor, limit = PAGE_LIMIT)
            if (page.events.isEmpty()) return

            var applied = s.cursor
            for (meta in page.events) {
                if (meta.deviceId == s.deviceId) {
                    // Our own upload coming back around. Nothing to apply, and the
                    // relay never requires a device to acknowledge its own events —
                    // but the cursor still has to move past it.
                    applied = meta.cursor
                    continue
                }
                val blob = client.getEvent(ref(), meta.deviceId, meta.eventId, held.deviceToken)
                if (blob == null) {
                    // Already erased between listing and fetching. Nothing to recover.
                    applied = meta.cursor
                    continue
                }
                val event = try {
                    Payload.decode(Crypto.open(key, blob))
                } catch (e: Exception) {
                    // One unreadable event must not wedge the feed forever: record
                    // it and carry on, or this device never catches up.
                    passWarning = when (e) {
                        is Payload.PayloadError -> "Skipped an event Skald could not read: ${e.message}"
                        else -> "Skipped an event Skald could not decrypt — check that both devices share a content key"
                    }
                    applied = meta.cursor
                    continue
                }
                applyEvent(event, meta.deviceId)
                applied = meta.cursor
            }

            if (applied <= s.cursor) {
                // The relay handed back a page that does not move the cursor
                // forward. Continuing would loop on it for as long as the app runs.
                passWarning = "The sync server returned a feed page that did not advance"
                return
            }
            s = requireState().copy(cursor = applied)
            state = s
            writeState()
            // Acknowledge only now: every op in this page is on disk, so the relay
            // is free to erase what it was holding for us.
            client.ack(ref(), s.deviceId, held.deviceToken, s.cursor)

            if (page.nextCursor == null || page.events.size < PAGE_LIMIT) return
        }
    }

    private fun applyEvent(event: Payload.Event, sender: String) {
        val s = requireState()
        for (op in event.header.ops) {
            val isNote = Payload.isNotePath(op.path)
            val localRaw = if (isNote) vault.syncRead(op.path) else null
            val localHash = localHashOf(op.path)
            val decision = Merge.decide(
                incoming = op,
                incomingWriter = event.header.device.ifEmpty { sender },
                known = s.files[op.path],
                localHash = localHash,
                localDeviceId = s.deviceId,
            )

            if (decision.action == Merge.Action.Apply) {
                if (decision.preserveLocal && isNote && localRaw != null) {
                    // The local edit is about to lose. Park it in the note's own
                    // history so it is recoverable rather than gone.
                    vault.captureVersion(op.path, NoteHistoryReason.Sync)
                }
                when (op) {
                    is Payload.Put -> {
                        if (Crypto.sha256Hex(op.content) != op.hash) {
                            passWarning = "Skipped ${op.path}: it did not match the hash the sending device gave it"
                            continue
                        }
                        vault.syncWrite(op.path, op.content)
                    }

                    is Payload.PutBin -> {
                        if (Crypto.sha256Hex(event.body) != op.hash) {
                            passWarning = "Skipped ${op.path}: it did not match the hash the sending device gave it"
                            continue
                        }
                        vault.syncWriteAsset(op.path, event.body)
                        // Force the next diff to re-stat this file rather than
                        // trust a stamp taken before the write.
                        s.assetStamps.remove(op.path)
                    }

                    is Payload.Delete -> {
                        if (isNote) {
                            vault.syncDelete(op.path)
                        } else {
                            vault.syncDeleteAsset(op.path)
                            s.assetStamps.remove(op.path)
                        }
                    }
                }
            }
            decision.record?.let { record ->
                s.files[op.path] = record
                if (record.hash == Merge.ABSENT) {
                    s.tombstonedAtMs[op.path] = System.currentTimeMillis()
                } else {
                    s.tombstonedAtMs.remove(op.path)
                }
            }
        }
        writeState()
    }

    /** Hash of whatever is at that path right now, notes and attachments alike. */
    private fun localHashOf(path: String): String =
        if (Payload.isNotePath(path)) {
            vault.syncRead(path)?.let { Crypto.sha256Hex(it) } ?: Merge.ABSENT
        } else {
            vault.syncReadAsset(path)?.let { Crypto.sha256Hex(it) } ?: Merge.ABSENT
        }

    // ---------- pushing ----------

    /**
     * Hashing an attachment means reading all of it, so a file whose size and
     * modification time are unchanged is taken at its recorded word. This is the
     * difference between a vault with photographs in it syncing quietly and
     * re-reading every one of them on each pass.
     */
    private fun assetHash(path: String, size: Long, mtimeMs: Long): String? {
        val s = requireState()
        val stamp = s.assetStamps[path]
        if (stamp != null && stamp.size == size && stamp.mtimeMs == mtimeMs) return stamp.hash
        val bytes = vault.syncReadAsset(path) ?: return null
        val hash = Crypto.sha256Hex(bytes)
        s.assetStamps[path] = SyncStateFile.AssetStamp(size, mtimeMs, hash)
        return hash
    }

    private class LocalOps(
        val text: List<Payload.FileOp>,
        val assets: List<Payload.PutBin>,
        val oversize: List<String>,
    )

    /**
     * What this device would publish right now. Notes and deletions batch into
     * shared events; attachments get one event each, because their bytes are the
     * event body rather than a field in it.
     */
    private fun localOps(full: Boolean): LocalOps {
        val s = requireState()
        val now = System.currentTimeMillis()
        val text = mutableListOf<Payload.FileOp>()
        val assets = mutableListOf<Payload.PutBin>()
        val oversize = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        for (file in vault.syncNotes()) {
            seen += file.path
            val hash = Crypto.sha256Hex(file.raw)
            val known = s.files[file.path]
            if (!full && known?.hash == hash) continue
            text += Payload.Put(
                path = file.path,
                rev = if (known?.hash == hash) known.rev else Merge.nextRev(known),
                ts = now,
                content = file.raw,
                hash = hash,
            )
        }

        for (asset in vault.syncAssets()) {
            if (!Payload.isAttachmentPath(asset.path)) continue
            // Counted as seen either way: a file too large to send has not been
            // deleted, and must not be published as one.
            seen += asset.path
            if (asset.size > Payload.MAX_ATTACHMENT_BYTES) {
                oversize += asset.path
                continue
            }
            val hash = assetHash(asset.path, asset.size, asset.mtimeMs) ?: continue
            val known = s.files[asset.path]
            if (!full && known?.hash == hash) continue
            assets += Payload.PutBin(
                path = asset.path,
                rev = if (known?.hash == hash) known.rev else Merge.nextRev(known),
                ts = now,
                hash = hash,
                size = asset.size,
            )
        }

        // Anything we had agreed on that is no longer there has been deleted here.
        for ((path, fileState) in s.files) {
            if (path in seen || fileState.hash == Merge.ABSENT) continue
            text += Payload.Delete(path, Merge.nextRev(fileState), now)
        }
        return LocalOps(text, assets, oversize)
    }

    private suspend fun push(full: Boolean) {
        val s = requireState()
        val ops = localOps(full)
        oversize = composeOversize(ops.oversize)
        if (ops.text.isEmpty() && ops.assets.isEmpty()) return

        val held = requireSecrets()
        val key: SecretKey = Crypto.contentKeyFromBase64Url(held.contentKey)
        val client = client()

        /*
         * A snapshot republishes unchanged files at the revision they already
         * hold. Claiming authorship of those would change the tiebreak this
         * device applies without changing it anywhere else, and two devices
         * would stop agreeing on who wins.
         */
        fun record(path: String, hash: String, rev: Long) {
            val known = s.files[path]
            val unchanged = known != null && known.hash == hash && known.rev == rev
            s.files[path] = Merge.FileState(hash, rev, if (unchanged) known.writer else s.deviceId)
            s.tombstonedAtMs.remove(path)
        }

        for (batch in Payload.batch(ops.text)) {
            val sealed = Crypto.seal(
                key,
                Payload.encode(
                    Payload.Header(
                        kind = if (full) Payload.Kind.Snapshot else Payload.Kind.Delta,
                        device = s.deviceId,
                        ts = System.currentTimeMillis(),
                        ops = batch,
                    )
                )
            )
            // A 409 means this event id already landed, which on a retry is success.
            client.putEvent(ref(), s.deviceId, Ids.newEventId(), sealed, held.deviceToken)

            // Only record what actually shipped, so a failure part-way through a
            // large vault leaves the rest to be retried rather than forgotten.
            for (op in batch) when (op) {
                is Payload.Delete -> {
                    s.files[op.path] = Merge.FileState(Merge.ABSENT, op.rev, s.deviceId)
                    s.tombstonedAtMs[op.path] = System.currentTimeMillis()
                }
                is Payload.Put -> record(op.path, op.hash, op.rev)
                is Payload.PutBin -> Unit
            }
            writeState()
        }

        for (op in ops.assets) {
            val bytes = vault.syncReadAsset(op.path)
            // Deleted or rewritten since it was listed. Either way the next pass
            // sees the truth; publishing this one now would publish a lie.
            if (bytes == null || bytes.size.toLong() != op.size) continue
            if (Crypto.sha256Hex(bytes) != op.hash) continue

            val sealed = Crypto.seal(
                key,
                Payload.encode(
                    Payload.Header(Payload.Kind.Blob, s.deviceId, System.currentTimeMillis(), listOf(op)),
                    bytes,
                )
            )
            try {
                client.putEvent(ref(), s.deviceId, Ids.newEventId(), sealed, held.deviceToken)
            } catch (e: GeshError) {
                if (e.status == 413) {
                    // This relay's limit is lower than the one Skald assumes.
                    // Report the file rather than failing the whole pass over it.
                    relayRejected += op.path
                    oversize = composeOversize(oversize)
                    continue
                }
                throw e
            }
            relayRejected.remove(op.path)
            record(op.path, op.hash, op.rev)
            writeState()
        }
    }

    /** Publishes the whole vault, for a device away past the relay's retention. */
    suspend fun republishEverything(): SyncStatus = syncNow(snapshot = true, force = true)

    /** Files Skald refused to send, plus files this relay refused to take. */
    private fun composeOversize(local: List<String>): List<String> =
        (local + relayRejected).distinct().sorted()

    /** Recount what a push would carry, without contacting the relay. */
    fun refreshPending() {
        if (state == null) {
            pendingPaths = 0
            return
        }
        try {
            val ops = localOps(full = false)
            pendingPaths = ops.text.size + ops.assets.size
            oversize = composeOversize(ops.oversize)
        } catch (_: Exception) {
            pendingPaths = 0
        }
    }

    /** Recount and publish the new status; for the UI after a local edit. */
    fun notifyVaultChanged() {
        refreshPending()
        emit()
    }

    private fun fail(err: Throwable): Nothing {
        lastError = err.message ?: err.toString()
        phase = SyncPhase.Error
        if (err is GeshError) err.retryAfterMs?.let { retryAtMs = System.currentTimeMillis() + it }
        emit()
        throw err
    }
}
