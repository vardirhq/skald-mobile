package no.vardir.skald.core

import kotlinx.coroutines.test.runTest
import no.vardir.skald.core.gesh.Bytes
import no.vardir.skald.core.gesh.Crypto
import no.vardir.skald.core.gesh.GeshClient
import no.vardir.skald.core.gesh.Ids
import no.vardir.skald.core.gesh.Pairing
import no.vardir.skald.core.sync.Merge
import no.vardir.skald.core.sync.Payload
import no.vardir.skald.core.sync.SyncEngine
import no.vardir.skald.core.sync.SyncPhase
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoTest {

    @Test
    fun `seal and open round-trip`() {
        val key = Crypto.generateContentKey()
        val plaintext = Bytes.utf8("the saga continues")
        val sealed = Crypto.seal(key, plaintext)
        assertContentEquals(plaintext, Crypto.open(key, sealed))
    }

    @Test
    fun `a fresh nonce means two seals never match`() {
        val key = Crypto.generateContentKey()
        val a = Crypto.seal(key, Bytes.utf8("same"))
        val b = Crypto.seal(key, Bytes.utf8("same"))
        assertFalse(a.contentEquals(b))
        assertEquals(Crypto.NONCE_BYTES, a.size - b.size + Crypto.NONCE_BYTES)
    }

    @Test
    fun `the wrong key fails closed`() {
        val sealed = Crypto.seal(Crypto.generateContentKey(), Bytes.utf8("secret"))
        assertFailsWith<Crypto.DecryptionFailed> { Crypto.open(Crypto.generateContentKey(), sealed) }
    }

    @Test
    fun `a tampered tag fails closed`() {
        val key = Crypto.generateContentKey()
        val sealed = Crypto.seal(key, Bytes.utf8("secret"))
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()
        assertFailsWith<Crypto.DecryptionFailed> { Crypto.open(key, sealed) }
    }

    @Test
    fun `a truncated blob fails closed`() {
        assertFailsWith<Crypto.DecryptionFailed> {
            Crypto.open(Crypto.generateContentKey(), ByteArray(Crypto.NONCE_BYTES))
        }
    }

    @Test
    fun `a content key survives the base64url trip a QR code makes`() {
        val key = Crypto.generateContentKey()
        val text = Crypto.contentKeyToBase64Url(key)
        assertContentEquals(Crypto.exportContentKey(key), Crypto.exportContentKey(Crypto.contentKeyFromBase64Url(text)))
    }

    @Test
    fun `sha256 matches the known digest of the empty string`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Crypto.sha256Hex(""))
    }
}

class PairingTest {

    @Test
    fun `parses a full invite`() {
        val invite = Pairing.parse("gesh://pair?s=https%3A%2F%2Fgesh.vardir.no&c=79T54-26AJX#k=abcdef")
        assertEquals("https://gesh.vardir.no", invite.server)
        assertEquals("79t5426ajx", invite.code)
        assertEquals("abcdef", invite.contentKey)
    }

    @Test
    fun `an invite without a fragment carries no key`() {
        assertNull(Pairing.parse("gesh://pair?s=https%3A%2F%2Fx.test&c=abc").contentKey)
    }

    @Test
    fun `the key only ever appears in the fragment`() {
        val uri = Pairing.withContentKey(Pairing.buildUri("https://x.test/", "79T54-26AJX"), "THEKEY")
        assertEquals("gesh://pair?s=https%3A%2F%2Fx.test&c=79T54-26AJX#k=THEKEY", uri)
        assertFalse(uri.substringBefore('#').contains("THEKEY"))
    }

    @Test
    fun `replacing the key does not stack fragments`() {
        val once = Pairing.withContentKey("gesh://pair?s=https%3A%2F%2Fx.test&c=abc", "ONE")
        val twice = Pairing.withContentKey(once, "TWO")
        assertEquals(1, twice.count { it == '#' })
        assertEquals("TWO", Pairing.parse(twice).contentKey)
    }

    @Test
    fun `nonsense is refused`() {
        assertFailsWith<Pairing.MalformedInvite> { Pairing.parse("https://example.com") }
        assertFailsWith<Pairing.MalformedInvite> { Pairing.parse("gesh://pair?c=abc") }
        assertFailsWith<Pairing.MalformedInvite> { Pairing.parse("gesh://pair?s=https%3A%2F%2Fx.test") }
    }

    @Test
    fun `codes normalize the way they are read aloud`() {
        assertEquals("79t5426ajx", Ids.normalizeEnrollmentCode("79T54-26AJX"))
        assertEquals("79t5426ajx", Ids.normalizeEnrollmentCode(" 79t54 26ajx "))
        assertEquals("79T54-26AJX", Ids.formatEnrollmentCode("79t5426ajx"))
    }
}

class PayloadTest {

    @Test
    fun `a delta round-trips`() {
        val header = Payload.Header(
            kind = Payload.Kind.Delta,
            device = "phone_abc",
            ts = 1_786_270_000_000,
            ops = listOf(
                Payload.Put("Notes/A.md", 4, 1_786_270_000_000, "hello", Crypto.sha256Hex("hello")),
                Payload.Delete("Daily/2026-07-30.md", 2, 1_786_270_000_000),
            ),
        )
        val decoded = Payload.decode(Payload.encode(header))
        assertEquals(header, decoded.header)
        assertEquals(0, decoded.body.size)
    }

    @Test
    fun `a blob carries its bytes in the body`() {
        val bytes = ByteArray(64) { it.toByte() }
        val op = Payload.PutBin("Attachments/map.png", 1, 5, Crypto.sha256Hex(bytes), bytes.size.toLong())
        val decoded = Payload.decode(
            Payload.encode(Payload.Header(Payload.Kind.Blob, "desktop_a", 5, listOf(op)), bytes)
        )
        assertContentEquals(bytes, decoded.body)
        assertEquals(op, decoded.header.ops.single())
    }

    @Test
    fun `the header ends at the first newline even with newlines in content`() {
        val header = Payload.Header(
            Payload.Kind.Delta, "d", 1,
            listOf(Payload.Put("A.md", 1, 1, "line one\nline two\n", Crypto.sha256Hex("line one\nline two\n"))),
        )
        val decoded = Payload.decode(Payload.encode(header))
        assertEquals("line one\nline two\n", (decoded.header.ops.single() as Payload.Put).content)
    }

    @Test
    fun `paths that could escape the vault are refused`() {
        for (path in listOf(
            "../outside.md", "/absolute.md", ".skald/sync.json", "a//b.md",
            "C:/win.md", "back\\slash.md", "ends./a.md", " leading.md", "trailing /a.md",
            "con.md", "", "dir/./x.md", "quo\"te.md", "pipe|d.md",
        )) {
            assertFalse(Payload.isSafeVaultPath(path), "expected \"$path\" to be refused")
        }
        assertTrue(Payload.isSafeVaultPath("Projects/Jörmungandr API.md"))
    }

    @Test
    fun `note and attachment paths are told apart`() {
        assertTrue(Payload.isNotePath("Notes/A.md"))
        assertFalse(Payload.isNotePath("Attachments/a.png"))
        assertTrue(Payload.isAttachmentPath("Attachments/a.png"))
        assertFalse(Payload.isAttachmentPath("Notes/A.md"))
    }

    @Test
    fun `a hostile payload is rejected rather than written`() {
        val escape = """{"v":1,"kind":"delta","device":"x","ts":1,"ops":[""" +
            """{"op":"put","path":"../evil.md","rev":1,"ts":1,"content":"x","hash":"${Crypto.sha256Hex("x")}"}]}"""
        assertFailsWith<Payload.PayloadError> { Payload.decode(Bytes.utf8("$escape\n")) }
    }

    @Test
    fun `an attachment may not ride along in a note event`() {
        val mixed = """{"v":1,"kind":"delta","device":"x","ts":1,"ops":[""" +
            """{"op":"putBin","path":"a.png","rev":1,"ts":1,"hash":"${Crypto.sha256Hex("x")}","size":0}]}"""
        assertFailsWith<Payload.PayloadError> { Payload.decode(Bytes.utf8("$mixed\n")) }
    }

    @Test
    fun `a blob whose body is the wrong length is refused`() {
        val bytes = ByteArray(8)
        val op = Payload.PutBin("a.png", 1, 1, Crypto.sha256Hex(bytes), 8)
        val encoded = Payload.encode(Payload.Header(Payload.Kind.Blob, "x", 1, listOf(op)), ByteArray(4))
        assertFailsWith<Payload.PayloadError> { Payload.decode(encoded) }
    }

    @Test
    fun `a future version is refused rather than half-read`() {
        assertFailsWith<Payload.PayloadError> {
            Payload.decode(Bytes.utf8("""{"v":2,"kind":"delta","device":"x","ts":1,"ops":[]}""" + "\n"))
        }
    }

    @Test
    fun `batching keeps a lone oversized note in its own event`() {
        val big = "x".repeat(400)
        val ops = List(6) { Payload.Put("N$it.md", 1, 1, big, Crypto.sha256Hex(big)) }
        val batches = Payload.batch(ops, maxBytes = 1200)
        assertTrue(batches.size > 1)
        assertEquals(ops.size, batches.sumOf { it.size })
        assertEquals(1, Payload.batch(listOf(ops.first()), maxBytes = 10).size)
    }
}

class MergeTest {

    private fun put(path: String, rev: Long, content: String) =
        Payload.Put(path, rev, 1, content, Crypto.sha256Hex(content))

    @Test
    fun `a higher revision wins`() {
        assertTrue(Merge.beats(2, "a", 1, "z"))
        assertFalse(Merge.beats(1, "z", 2, "a"))
    }

    @Test
    fun `equal revisions break by device id, the same way everywhere`() {
        assertTrue(Merge.beats(1, "z", 1, "a"))
        assertFalse(Merge.beats(1, "a", 1, "z"))
    }

    @Test
    fun `an unseen file is applied`() {
        val result = Merge.decide(put("A.md", 1, "remote"), "other", null, Merge.ABSENT, "me")
        assertEquals(Merge.Action.Apply, result.action)
        assertFalse(result.preserveLocal)
        assertEquals(1, result.record?.rev)
    }

    @Test
    fun `a file already equal to the incoming op is a noop that still moves the clock`() {
        val hash = Crypto.sha256Hex("same")
        val result = Merge.decide(put("A.md", 3, "same"), "other", null, hash, "me")
        assertEquals(Merge.Action.Noop, result.action)
        assertEquals(3, result.record?.rev)
    }

    @Test
    fun `a replayed older op changes nothing`() {
        val known = Merge.FileState(Crypto.sha256Hex("current"), 5, "other")
        val result = Merge.decide(put("A.md", 2, "stale"), "other", known, known.hash, "me")
        assertEquals(Merge.Action.Noop, result.action)
        assertNull(result.record)
    }

    @Test
    fun `an unpublished local edit that loses is preserved`() {
        val known = Merge.FileState(Crypto.sha256Hex("base"), 1, "me")
        // Local edit is unpublished, so its claim would be rev 2 by "me".
        val result = Merge.decide(put("A.md", 5, "remote"), "zzz", known, Crypto.sha256Hex("local edit"), "me")
        assertEquals(Merge.Action.Apply, result.action)
        assertTrue(result.preserveLocal)
    }

    @Test
    fun `an unpublished local edit that wins is kept`() {
        val known = Merge.FileState(Crypto.sha256Hex("base"), 5, "me")
        val result = Merge.decide(put("A.md", 3, "remote"), "other", known, Crypto.sha256Hex("local edit"), "me")
        assertEquals(Merge.Action.KeepLocal, result.action)
        assertNull(result.record)
    }

    @Test
    fun `absence is a state with a clock, so a stale event cannot resurrect a note`() {
        val tombstone = Merge.FileState(Merge.ABSENT, 4, "me")
        val result = Merge.decide(put("A.md", 2, "back from the dead"), "other", tombstone, Merge.ABSENT, "me")
        assertEquals(Merge.Action.Noop, result.action)
        assertNull(result.record)
    }

    @Test
    fun `a newer delete removes a note`() {
        val known = Merge.FileState(Crypto.sha256Hex("here"), 1, "me")
        val result = Merge.decide(Payload.Delete("A.md", 2, 1), "other", known, known.hash, "me")
        assertEquals(Merge.Action.Apply, result.action)
        assertEquals(Merge.ABSENT, result.record?.hash)
    }
}

class SyncEngineTest {

    private fun engineFor(
        relay: FakeGesh,
        vault: MemoryVault,
        secrets: MemorySecrets = MemorySecrets(),
        state: MemoryState = MemoryState(),
        prefix: String = "phone",
    ) = SyncEngine(
        vault = vault,
        secrets = secrets,
        stateStore = state,
        devicePrefix = prefix,
        makeClient = { GeshClient(it, relay.transport) },
    )

    @Test
    fun `a fresh root publishes the vault it was connected from`() = runTest {
        val relay = FakeGesh()
        val vault = MemoryVault().apply { notes["Notes/A.md"] = "# A" }
        val engine = engineFor(relay, vault)

        val status = engine.connect("https://relay.test")

        assertTrue(status.configured)
        assertTrue(status.isRoot)
        assertEquals(SyncPhase.Idle, status.phase)
        assertEquals(1, status.tracked)
        assertEquals(0, status.pending)
        assertTrue(relay.eventCount() >= 1)
    }

    @Test
    fun `a paired device receives the vault, and edits propagate both ways`() = runTest {
        val relay = FakeGesh()

        val desk = MemoryVault().apply {
            notes["Notes/A.md"] = "# A"
            notes["Projects/B.md"] = "# B"
        }
        val deskEngine = engineFor(relay, desk, prefix = "desktop")
        deskEngine.connect("https://relay.test")

        val ticket = deskEngine.mintPairing()
        assertTrue(ticket.uri.contains("#k="))
        assertNotNull(Pairing.parse(ticket.uri).contentKey)

        val phone = MemoryVault()
        val phoneEngine = engineFor(relay, phone)
        phoneEngine.pair(ticket.uri)

        assertEquals(setOf("Notes/A.md", "Projects/B.md"), phone.notes.keys)
        assertEquals("# A", phone.notes["Notes/A.md"])

        // Phone edits; desktop pulls it.
        phone.notes["Notes/A.md"] = "# A, from the phone"
        phoneEngine.syncNow()
        deskEngine.syncNow()
        assertEquals("# A, from the phone", desk.notes["Notes/A.md"])

        // Desktop deletes; phone follows.
        desk.notes.remove("Projects/B.md")
        deskEngine.syncNow()
        phoneEngine.syncNow()
        assertFalse("Projects/B.md" in phone.notes)
    }

    @Test
    fun `two devices converge on the same winner`() = runTest {
        val relay = FakeGesh()
        val desk = MemoryVault().apply { notes["Notes/A.md"] = "base" }
        val deskEngine = engineFor(relay, desk, prefix = "desktop")
        deskEngine.connect("https://relay.test")

        val phone = MemoryVault()
        val phoneEngine = engineFor(relay, phone)
        phoneEngine.pair(deskEngine.mintPairing().uri)
        assertEquals("base", phone.notes["Notes/A.md"])

        // Both edit the same note while apart, then both catch up.
        desk.notes["Notes/A.md"] = "desktop's version"
        phone.notes["Notes/A.md"] = "phone's version"

        deskEngine.syncNow()
        phoneEngine.syncNow()
        deskEngine.syncNow()
        phoneEngine.syncNow()

        val settled = desk.notes["Notes/A.md"]
        assertEquals(settled, phone.notes["Notes/A.md"])
        assertTrue(settled == "desktop's version" || settled == "phone's version")
    }

    @Test
    fun `an unpublished edit that loses is kept in the note's history`() = runTest {
        val relay = FakeGesh()
        val desk = MemoryVault().apply { notes["Notes/A.md"] = "base" }
        val deskEngine = engineFor(relay, desk, prefix = "desktop")
        deskEngine.connect("https://relay.test")

        val phone = MemoryVault()
        val phoneEngine = engineFor(relay, phone)
        phoneEngine.pair(deskEngine.mintPairing().uri)

        // The phone edits and does not publish. Meanwhile the desktop publishes
        // twice, so its revision beats the phone's claim outright rather than by
        // the device-id tiebreak — the outcome is the same whichever ids random
        // generation handed out.
        phone.notes["Notes/A.md"] = "written on the phone, never pushed"
        desk.notes["Notes/A.md"] = "desktop, once"
        deskEngine.syncNow()
        desk.notes["Notes/A.md"] = "desktop, twice"
        deskEngine.syncNow()

        phoneEngine.syncNow()

        assertEquals("desktop, twice", phone.notes["Notes/A.md"])
        assertEquals(
            listOf("Notes/A.md" to "written on the phone, never pushed"),
            phone.history,
            "the losing text must be recoverable from the note's own history",
        )
    }

    @Test
    fun `attachments travel as their own events`() = runTest {
        val relay = FakeGesh()
        val desk = MemoryVault().apply {
            notes["Notes/A.md"] = "![map](../Attachments/map.png)"
            assets["Attachments/map.png"] = ByteArray(2048) { (it % 251).toByte() }
        }
        val deskEngine = engineFor(relay, desk, prefix = "desktop")
        deskEngine.connect("https://relay.test")

        val phone = MemoryVault()
        val phoneEngine = engineFor(relay, phone)
        phoneEngine.pair(deskEngine.mintPairing().uri)

        assertContentEquals(desk.assets["Attachments/map.png"], phone.assets["Attachments/map.png"])
    }

    @Test
    fun `an attachment too large for the relay is named rather than hidden`() = runTest {
        val relay = FakeGesh()
        val vault = MemoryVault().apply {
            notes["A.md"] = "x"
            assets["Attachments/huge.bin"] = ByteArray(4)
        }
        val engine = engineFor(relay, vault)
        engine.connect("https://relay.test")

        // The relay's own limit is lower than the one Skald assumes.
        vault.assets["Attachments/huge.bin"] = ByteArray(8)
        vault.assetMtimes["Attachments/huge.bin"] = 99
        relay.failNextPut = 413
        engine.syncNow()

        assertTrue("Attachments/huge.bin" in engine.snapshotStatus().oversize)
    }

    @Test
    fun `state survives a restart, and the second engine does not re-push`() = runTest {
        val relay = FakeGesh()
        val vault = MemoryVault().apply { notes["A.md"] = "one" }
        val secrets = MemorySecrets()
        val state = MemoryState()

        engineFor(relay, vault, secrets, state).connect("https://relay.test")
        val eventsAfterConnect = relay.eventCount()

        val restarted = engineFor(relay, vault, secrets, state)
        restarted.refreshPending()
        assertTrue(restarted.snapshotStatus().configured)
        assertEquals(0, restarted.snapshotStatus().pending)

        restarted.syncNow()
        assertEquals(eventsAfterConnect, relay.eventCount())
    }

    @Test
    fun `a revoked credential stops automatic syncing instead of retrying forever`() = runTest {
        val relay = FakeGesh()
        val vault = MemoryVault().apply { notes["A.md"] = "one" }
        val engine = engineFor(relay, vault)
        engine.connect("https://relay.test")

        vault.notes["A.md"] = "two"
        relay.failNextPut = 401
        engine.syncNow()

        val status = engine.snapshotStatus()
        assertEquals(SyncPhase.Error, status.phase)
        assertFalse(status.enabled)
        assertNotNull(status.lastError)
    }

    @Test
    fun `a rate limit is honoured rather than retried in a loop`() = runTest {
        val relay = FakeGesh()
        val vault = MemoryVault().apply { notes["A.md"] = "one" }
        val engine = engineFor(relay, vault)
        engine.connect("https://relay.test")

        vault.notes["A.md"] = "two"
        relay.failNextPut = 429
        engine.syncNow()
        assertEquals(SyncPhase.Error, engine.snapshotStatus().phase)

        // Without a Retry-After header there is no backoff to honour, so the
        // next pass runs and the change lands.
        engine.syncNow()
        assertEquals(SyncPhase.Idle, engine.snapshotStatus().phase)
        assertEquals(0, engine.snapshotStatus().pending)
    }

    @Test
    fun `disconnecting forgets the root but keeps every note`() = runTest {
        val relay = FakeGesh()
        val vault = MemoryVault().apply { notes["A.md"] = "one" }
        val secrets = MemorySecrets()
        val state = MemoryState()
        val engine = engineFor(relay, vault, secrets, state)
        engine.connect("https://relay.test")

        val status = engine.disconnect()
        assertFalse(status.configured)
        assertEquals(SyncPhase.Off, status.phase)
        assertNull(state.read())
        assertEquals(1, vault.notes.size)
    }

    @Test
    fun `only the root device can pair or revoke`() = runTest {
        val relay = FakeGesh()
        val desk = MemoryVault().apply { notes["A.md"] = "one" }
        val deskEngine = engineFor(relay, desk, prefix = "desktop")
        deskEngine.connect("https://relay.test")

        val phone = MemoryVault()
        val phoneEngine = engineFor(relay, phone)
        phoneEngine.pair(deskEngine.mintPairing().uri)

        assertFailsWith<IllegalStateException> { phoneEngine.mintPairing() }
        assertFailsWith<IllegalStateException> { phoneEngine.listDevices() }

        val devices = deskEngine.listDevices()
        assertEquals(2, devices.size)
        assertTrue(devices.any { it.isThisDevice })

        val phoneId = deskEngine.snapshotStatus().deviceId
        assertFailsWith<IllegalStateException> { deskEngine.revokeDevice(phoneId!!) }
    }

    @Test
    fun `a pairing code is single use`() = runTest {
        val relay = FakeGesh()
        val desk = MemoryVault().apply { notes["A.md"] = "one" }
        val deskEngine = engineFor(relay, desk, prefix = "desktop")
        deskEngine.connect("https://relay.test")
        val ticket = deskEngine.mintPairing()

        engineFor(relay, MemoryVault()).pair(ticket.uri)
        assertFailsWith<no.vardir.skald.core.gesh.GeshError> { engineFor(relay, MemoryVault()).pair(ticket.uri) }
    }
}
