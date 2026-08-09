package no.vardir.skald.core.sync

import kotlinx.serialization.Serializable
import no.vardir.skald.core.model.NoteHistoryReason

const val SYNC_APP_ID = "skald"

enum class SyncPhase { Off, Idle, Syncing, Error }

data class SyncStatus(
    /** This vault is bound to a root on a relay. */
    val configured: Boolean = false,
    /** Automatic syncing is switched on. */
    val enabled: Boolean = false,
    val serverUrl: String? = null,
    val appId: String = SYNC_APP_ID,
    val rootId: String? = null,
    val handle: String? = null,
    val deviceId: String? = null,
    /** This device provisioned the root, so it holds the authority credential. */
    val isRoot: Boolean = false,
    val phase: SyncPhase = SyncPhase.Off,
    val lastSyncMs: Long? = null,
    val lastError: String? = null,
    /** Files changed locally since the last successful push. */
    val pending: Int = 0,
    /** Files — notes and attachments — this vault has agreed on with the root. */
    val tracked: Int = 0,
    /** Credentials are held in the platform keystore rather than a plain file. */
    val secretsProtected: Boolean = false,
    /** When rate limited, the moment it is worth trying again. */
    val retryAtMs: Long? = null,
    /** Attachments the relay will not accept, because they are too large. */
    val oversize: List<String> = emptyList(),
)

data class SyncDeviceInfo(
    val deviceId: String,
    val enrolledAtMs: Long,
    val lastSeenMs: Long?,
    val ackCursor: Long?,
    val isThisDevice: Boolean,
)

data class PairingTicket(
    /** Normalized code, as GESH stores it. */
    val code: String,
    /** Grouped and upper-cased, the way it is meant to be read aloud. */
    val displayCode: String,
    val expiresAtMs: Long,
    /** The full `gesh://pair?…#k=…` string, content key included. */
    val uri: String,
    /** True when the relay had no public URL and Skald built the URI itself. */
    val uriIsLocal: Boolean,
)

/** The three secrets, kept out of the vault and in the platform keystore. */
data class RootSecrets(
    val deviceToken: String,
    /** base64url AES-256 key. Never sent anywhere but the pairing fragment. */
    val contentKey: String,
    /** Only on the device that provisioned the root. */
    val rootToken: String? = null,
)

/**
 * Somewhere safe for credentials — Keystore-backed on Android, never the vault
 * folder, which is the thing most likely to end up in a cloud drive.
 */
interface SecretStore {
    fun load(key: String): RootSecrets?
    fun save(key: String, secrets: RootSecrets)
    fun forget(key: String)

    /** False when the platform gave us nothing better than plain storage. */
    val protected: Boolean

    /** Throws when there is nowhere safe to keep a credential. */
    fun requireStore()

    companion object {
        /**
         * One device holds one credential per vault-on-root, because a phone can
         * hold two vaults enrolled on the same root and each is its own device.
         */
        fun deviceKey(appId: String, rootId: String, deviceId: String): String = "$appId:$rootId:$deviceId"
    }
}

/** Where the per-vault sync bookkeeping lives — `.skald/sync.json`, as on desktop. */
interface SyncStateStore {
    fun read(): String?
    fun write(json: String)
    fun clear()
}

/** One Markdown file, as the engine sees it. */
data class SyncNote(val path: String, val raw: String)

/** One attachment, stat-ed but not read. */
data class SyncAsset(val path: String, val size: Long, val mtimeMs: Long)

/**
 * The whole surface the engine needs from a vault. Anything that provides these
 * nine operations can drive the same engine — which is exactly how the desktop
 * describes bringing up a second client.
 */
interface SyncVault {
    fun syncNotes(): List<SyncNote>
    fun syncRead(path: String): String?
    fun syncWrite(path: String, content: String)
    fun syncDelete(path: String)

    fun syncAssets(): List<SyncAsset>
    fun syncReadAsset(path: String): ByteArray?
    fun syncWriteAsset(path: String, bytes: ByteArray)
    fun syncDeleteAsset(path: String)

    /** Force a history snapshot before a note loses a conflict. */
    fun captureVersion(path: String, reason: NoteHistoryReason)
}

/** The `.skald/sync.json` shape, kept byte-compatible with the desktop client. */
@Serializable
internal data class SyncStateFile(
    val version: Int = 1,
    val serverUrl: String,
    val appId: String,
    val rootId: String,
    val deviceId: String,
    val handle: String? = null,
    /** This device provisioned the root and holds the authority credential. */
    val isRoot: Boolean = false,
    val enabled: Boolean = true,
    val cursor: Long = 0,
    val lastSyncMs: Long? = null,
    /** Per-path state this device has agreed on with the root. */
    val files: MutableMap<String, Merge.FileState> = mutableMapOf(),
    /** Paths deleted locally and not yet published, with the clock to publish them at. */
    val tombstonedAtMs: MutableMap<String, Long> = mutableMapOf(),
    /** size+mtime of each attachment when it was last hashed, to avoid re-reading it. */
    val assetStamps: MutableMap<String, AssetStamp> = mutableMapOf(),
) {
    @Serializable
    data class AssetStamp(val size: Long, val mtimeMs: Long, val hash: String)
}
