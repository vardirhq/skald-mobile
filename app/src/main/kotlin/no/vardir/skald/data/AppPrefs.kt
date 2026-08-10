package no.vardir.skald.data

import android.content.Context
import no.vardir.skald.core.text.Notes
import java.io.File

/**
 * The folder a typed vault name maps to. [Notes.safeFileName] takes the path
 * separators out, but leaves a leading dot alone — and `..` would put the vault
 * beside the app's files directory rather than inside `vaults/`.
 */
fun vaultFolderName(display: String): String =
    Notes.safeFileName(display).trimStart('.').trim().ifEmpty { "vault" }

/**
 * The few facts that have to be known *before* a vault exists, and so cannot
 * live in the vault's own `.skald/settings.json`: which folder under `vaults/`
 * to open, what to call it, and whether setup has been through.
 *
 * Plain preferences rather than [KeystoreSecrets] — none of this is a secret,
 * and the keystore must stay the only thing that can fail for keystore reasons.
 */
class AppPrefs(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("skald.setup", Context.MODE_PRIVATE)
    private val vaultsDir = File(appContext.filesDir, VAULTS)

    /** Setup has run to the end, so a vault exists and the shell can open it. */
    val setupComplete: Boolean get() = prefs.getBoolean(KEY_COMPLETE, false)

    /** The folder under `files/vaults/`, which is a sanitized [vaultName]. */
    val vaultDir: String get() = prefs.getString(KEY_DIR, null) ?: DEFAULT_DIR

    /** What the person typed. The chrome shows this; the folder may differ. */
    val vaultName: String get() = prefs.getString(KEY_NAME, null) ?: DEFAULT_NAME

    /**
     * Fixes which folder this vault lives in. Idempotent on purpose: a pairing
     * that fails is retried against the same folder rather than stranding the
     * one already on disk and starting a second.
     */
    fun openVault(name: String): String {
        prefs.getString(KEY_DIR, null)?.let { return it }
        val display = name.trim().ifEmpty { DEFAULT_NAME }
        val dir = availableDir(display)
        prefs.edit().putString(KEY_NAME, display).putString(KEY_DIR, dir).apply()
        return dir
    }

    /**
     * Setup is only through once the vault exists *and* whatever the person
     * asked for alongside it — a pairing, a root — actually landed. Failing
     * halfway leaves this false, so the next launch offers setup again.
     */
    fun markComplete() {
        prefs.edit().putBoolean(KEY_COMPLETE, true).apply()
    }

    /**
     * An install from before setup existed keeps its notes in `vaults/midgard`,
     * the one hard-coded path there used to be. Adopt it rather than walking
     * that person through creating a second vault their notes are not in.
     */
    fun adoptLegacyVault() {
        if (prefs.contains(KEY_COMPLETE) || prefs.contains(KEY_DIR)) return
        if (!holdsNotes(File(vaultsDir, LEGACY_DIR))) return
        prefs.edit()
            .putString(KEY_DIR, LEGACY_DIR)
            .putString(KEY_NAME, LEGACY_DIR.replaceFirstChar { it.uppercase() })
            .putBoolean(KEY_COMPLETE, true)
            .apply()
    }

    /** A folder name derived from the title that no other vault has taken. */
    private fun availableDir(display: String): String {
        val base = vaultFolderName(display)
        if (!File(vaultsDir, base).exists()) return base
        var n = 2
        while (File(vaultsDir, "$base $n").exists()) n++
        return "$base $n"
    }

    private fun holdsNotes(dir: File): Boolean = dir.isDirectory && dir.walkTopDown()
        .onEnter { it.name != ".skald" }
        .any { it.isFile && it.extension.equals("md", ignoreCase = true) }

    private companion object {
        const val VAULTS = "vaults"
        const val LEGACY_DIR = "midgard"
        const val DEFAULT_DIR = "vault"
        const val DEFAULT_NAME = "My Vault"
        const val KEY_COMPLETE = "setup.complete"
        const val KEY_DIR = "vault.dir"
        const val KEY_NAME = "vault.name"
    }
}
