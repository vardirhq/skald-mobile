package no.vardir.skald.data

import no.vardir.skald.core.gesh.Crypto
import no.vardir.skald.core.model.AttachmentRef
import no.vardir.skald.core.model.NoteHistoryEntry
import no.vardir.skald.core.model.NoteHistoryReason
import no.vardir.skald.core.model.VaultSettings
import no.vardir.skald.core.sync.SyncAsset
import no.vardir.skald.core.sync.SyncNote
import no.vardir.skald.core.sync.SyncStateStore
import no.vardir.skald.core.sync.SyncVault
import no.vardir.skald.core.text.Attachments
import no.vardir.skald.core.text.Frontmatter
import no.vardir.skald.core.text.Notes
import no.vardir.skald.core.text.Tasks
import no.vardir.skald.core.text.Wikilinks
import no.vardir.skald.core.vault.RawNote
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A vault is a folder of Markdown files the person owns. Skald keeps its index,
 * settings, graph layout and local history in a `.skald/` directory inside it —
 * delete that and the Markdown is untouched.
 *
 * Everything here is plain `java.io.File` work against app storage, which is
 * what lets [SyncEngine] treat the vault as nine operations and nothing more.
 */
class FileVault(val root: File) : SyncVault {

    val name: String get() = root.name

    private val skaldDir get() = File(root, ".skald")
    private val historyDir get() = File(skaldDir, "history")
    private val settingsFile get() = File(skaldDir, "settings.json")
    private val positionsFile get() = File(skaldDir, "layout.json")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    init {
        root.mkdirs()
        skaldDir.mkdirs()
    }

    // ---------- reading ----------

    /** Every Markdown file in the vault, `.skald/` excluded. */
    fun notes(): List<RawNote> = walk()
        .filter { it.extension.equals("md", ignoreCase = true) }
        .map { file ->
            val path = relativePath(file)
            RawNote(
                path = path,
                raw = runCatching { file.readText() }.getOrDefault(""),
                created = file.lastModified(),
                updated = file.lastModified(),
            )
        }
        .sortedBy { it.path }
        .toList()

    /** Every folder that exists on disk, so an empty one still shows in the tree. */
    fun folders(): Set<String> = root.walkTopDown()
        .onEnter { it.name != ".skald" }
        .filter { it.isDirectory && it != root }
        .map { relativePath(it) }
        .toSet()

    fun read(path: String): String? = fileFor(path)?.takeIf { it.isFile }?.readText()

    fun exists(path: String): Boolean = fileFor(path)?.isFile == true

    private fun walk(): Sequence<File> = root.walkTopDown()
        .onEnter { it.name != ".skald" }
        .filter { it.isFile && !it.name.startsWith(".") }

    private fun relativePath(file: File): String =
        file.relativeTo(root).invariantSeparatorsPath

    /**
     * Resolve a vault-relative path to a real file, refusing anything that would
     * escape the vault or reach into `.skald/`. Every write goes through this.
     */
    private fun fileFor(path: String): File? {
        if (path.isEmpty() || path.contains('\\')) return null
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." || it.startsWith(".") }) return null
        val file = File(root, path)
        val canonicalRoot = root.canonicalPath
        return if (file.canonicalPath.startsWith("$canonicalRoot${File.separator}")) file else null
    }

    // ---------- writing ----------

    /**
     * Writes are staged beside the target and published by rename, so no reader
     * ever sees a half-written note — the same rule the relay applies to blobs.
     */
    fun write(path: String, content: String, reason: NoteHistoryReason = NoteHistoryReason.Edit) {
        val file = fileFor(path) ?: error("Skald will not write to \"$path\"")
        if (file.isFile) captureVersion(path, reason)
        file.parentFile?.mkdirs()
        val staged = File(file.parentFile, "${file.name}.skald-tmp")
        staged.writeText(content)
        if (!staged.renameTo(file)) {
            file.writeText(content)
            staged.delete()
        }
    }

    fun delete(path: String) {
        val file = fileFor(path) ?: return
        if (file.isFile) {
            captureVersion(path, NoteHistoryReason.Delete)
            file.delete()
        }
    }

    fun rename(from: String, to: String): String? {
        val source = fileFor(from) ?: return null
        val target = fileFor(to) ?: return null
        if (!source.isFile || target.exists()) return null
        captureVersion(from, NoteHistoryReason.Rename)
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) return null

        // A note whose frontmatter title was the file name is being renamed by
        // its title as far as the person is concerned — every list, chip and
        // link label reads that field first. Leaving it behind is why renaming
        // used to look like it had not worked at all.
        val oldStem = Notes.titleFromPath(from)
        val newStem = Notes.titleFromPath(to)
        if (oldStem != newStem) {
            val raw = runCatching { target.readText() }.getOrNull()
            val titled = raw?.let { Frontmatter.parse(it).frontmatter["title"] as? String }
            if (titled != null && titled.trim() == oldStem) {
                runCatching { target.writeText(Frontmatter.apply(raw, mapOf("title" to newStem))) }
            }
        }

        // Every wikilink that pointed at the old note now points at the new one,
        // in whichever form it was written.
        for (note in notes()) {
            val rewritten = Wikilinks.rewrite(
                note.raw,
                matches = { target1 -> Wikilinks.normalizeTarget(target1) == Wikilinks.normalizeTarget(from) ||
                    target1.equals(oldStem, ignoreCase = true) },
                rewrite = { written -> Wikilinks.retarget(written, to) },
            )
            if (rewritten != note.raw) write(note.path, rewritten, NoteHistoryReason.External)
        }
        return to
    }

    /** Move a note into another folder, keeping its file name. `""` is the vault root. */
    fun move(path: String, folder: String): String? {
        val name = path.substringAfterLast('/')
        val target = if (folder.isEmpty()) name else "$folder/$name"
        if (target == path) return path
        return rename(path, target)
    }

    /** A copy beside the original, titled so the two can be told apart. */
    fun duplicate(path: String): String? {
        val raw = read(path) ?: return null
        val stem = Notes.titleFromPath(path)
        val target = freePath(Notes.parentFolder(path), "$stem copy")
        val titled = if (Frontmatter.parse(raw).frontmatter["title"] != null) {
            Frontmatter.apply(raw, mapOf("title" to Notes.titleFromPath(target)))
        } else {
            raw
        }
        write(target, titled)
        return target
    }

    /** The first free `folder/stem.md`, numbered rather than clobbering. */
    private fun freePath(folder: String, stem: String): String {
        val safe = Notes.safeFileName(stem).ifEmpty { "Untitled" }
        val dir = if (folder.isEmpty()) "" else "$folder/"
        var candidate = "$dir$safe.md"
        var n = 2
        while (exists(candidate)) {
            candidate = "$dir$safe $n.md"
            n++
        }
        return candidate
    }

    /** Create a note, making its title unique within the folder rather than clobbering. */
    fun createNote(folder: String, title: String, schema: String? = null): String {
        val candidate = freePath(folder, title)
        val frontmatter = linkedMapOf<String, Any?>("title" to Notes.titleFromPath(candidate))
        if (schema != null) frontmatter["schema"] = schema
        write(candidate, Frontmatter.serialize(frontmatter, "\n"))
        return candidate
    }

    // ---------- folders ----------

    /** Make a folder, including any parent it needs. Already-there counts as made. */
    fun createFolder(path: String): Boolean {
        val dir = fileFor(path.trim().trim('/')) ?: return false
        if (dir.exists()) return dir.isDirectory
        return dir.mkdirs()
    }

    /**
     * Rename or move a folder, note by note, so every wikilink that pointed into
     * it follows. Whatever is not a note — attachments, empty subfolders — is
     * carried across afterwards, and the husk is swept up.
     */
    fun renameFolder(from: String, to: String): String? {
        val source = fileFor(from)?.takeIf { it.isDirectory } ?: return null
        val target = fileFor(to) ?: return null
        if (target.exists()) return null
        if ("$to/".startsWith("$from/")) return null // a folder cannot move inside itself

        for (note in notes().filter { it.path.startsWith("$from/") }) {
            rename(note.path, to + note.path.removePrefix(from))
        }
        if (source.isDirectory) {
            target.parentFile?.mkdirs()
            if (!source.renameTo(target)) {
                source.walkTopDown().filter { it.isFile }.forEach { file ->
                    val moved = File(target, file.relativeTo(source).invariantSeparatorsPath)
                    moved.parentFile?.mkdirs()
                    file.renameTo(moved)
                }
                source.deleteRecursively()
            }
        }
        return to
    }

    /**
     * Remove a folder that holds nothing. Deleting one that still has notes in
     * it is not offered: a folder disappearing with its contents is not an
     * undo anybody expects from a long press.
     */
    fun deleteFolder(path: String): Boolean {
        val dir = fileFor(path)?.takeIf { it.isDirectory } ?: return false
        if (dir.walkTopDown().any { it.isFile }) return false
        return dir.deleteRecursively()
    }

    /** Today's page in the saga, created on first open. */
    fun ensureDaily(settings: VaultSettings, todayIso: String): String {
        val path = "${settings.dailyFolder}/$todayIso.md"
        if (!exists(path)) {
            write(path, Frontmatter.serialize(linkedMapOf("schema" to "Daily", "created" to todayIso), "\n"))
        }
        return path
    }

    /**
     * Append a line to the end of a note, with a blank line between it and
     * whatever was there. This is how a thread captured from the threads list
     * lands in a note without anybody having to open it and find the end.
     */
    fun appendLine(path: String, line: String): Boolean {
        val raw = read(path) ?: return false
        val trimmed = raw.trimEnd('\n')
        // Straight after an existing checkbox rather than a blank line below it,
        // so a list of threads stays one list.
        val glue = when {
            trimmed.isEmpty() -> ""
            Tasks.isTaskLine(trimmed.substringAfterLast('\n')) -> "\n"
            else -> "\n\n"
        }
        write(path, "$trimmed$glue$line\n")
        return true
    }

    /** Toggle or edit the thread on a raw file line, and write the note back. */
    fun editTask(path: String, line: Int, edits: Tasks.Edits): Boolean {
        val raw = read(path) ?: return false
        val updated = Tasks.updateLine(raw, line, edits)
        if (updated == raw) return false
        write(path, updated)
        return true
    }

    // ---------- attachments ----------

    fun attachmentsIn(notePath: String, body: String): List<AttachmentRef> =
        Attachments.links(body).map { link ->
            val resolved = Attachments.resolvePath(notePath, link.target)
            val file = resolved?.let { fileFor(it) }
            val name = resolved?.substringAfterLast('/') ?: link.target
            AttachmentRef(
                target = link.target,
                path = resolved,
                label = link.label.ifEmpty { name },
                embedded = link.embedded,
                exists = file?.isFile == true,
                mime = Attachments.mime(name),
                kind = Attachments.kind(name),
                size = file?.takeIf { it.isFile }?.length(),
            )
        }

    fun assetFile(path: String): File? = fileFor(path)?.takeIf { it.isFile }

    /** Copy bytes into the vault under a collision-safe name, and return its path. */
    fun importAttachment(settings: VaultSettings, displayName: String, bytes: ByteArray): String {
        val safe = displayName.replace(Regex("""[/\\]"""), " ").trim().ifEmpty { "attachment" }
        val stem = safe.substringBeforeLast('.', safe)
        val ext = safe.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var candidate = "${settings.attachmentsFolder}/$stem$ext"
        var n = 2
        while (exists(candidate)) {
            candidate = "${settings.attachmentsFolder}/$stem $n$ext"
            n++
        }
        val file = fileFor(candidate) ?: error("Skald will not write to \"$candidate\"")
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return candidate
    }

    // ---------- history ----------

    /**
     * A snapshot before every edit, external change, rename, delete and restore,
     * so nothing sync overwrites is ever actually gone. Rapid edit snapshots
     * coalesce; a `sync` capture never does, because that is the one that holds
     * a conflict loser.
     */
    override fun captureVersion(path: String, reason: NoteHistoryReason) {
        val content = fileFor(path)?.takeIf { it.isFile }?.readText() ?: return
        val dir = File(historyDir, hashOf(path)).apply { mkdirs() }
        val now = System.currentTimeMillis()

        if (reason == NoteHistoryReason.Edit) {
            val newest = dir.listFiles()?.maxByOrNull { it.lastModified() }
            if (newest != null && now - newest.lastModified() < COALESCE_MS && newest.name.endsWith("-edit.md")) {
                return
            }
        }
        File(dir, "$now-${reason.name.lowercase()}.md").writeText(content)
        File(dir, ".path").writeText(path)
        prune(dir)
    }

    fun history(path: String): List<NoteHistoryEntry> {
        val dir = File(historyDir, hashOf(path))
        return (dir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".md") }
            .mapNotNull { file ->
                val stamp = file.name.substringBefore('-').toLongOrNull() ?: return@mapNotNull null
                val reason = file.name.removeSuffix(".md").substringAfter('-')
                NoteHistoryEntry(
                    id = file.name,
                    notePath = path,
                    createdAt = stamp,
                    size = file.length(),
                    reason = NoteHistoryReason.entries.firstOrNull { it.name.equals(reason, true) }
                        ?: NoteHistoryReason.Edit,
                )
            }
            .sortedByDescending { it.createdAt }
    }

    fun readVersion(path: String, id: String): String? =
        File(File(historyDir, hashOf(path)), id).takeIf { it.isFile }?.readText()

    fun restoreVersion(path: String, id: String): Boolean {
        val content = readVersion(path, id) ?: return false
        write(path, content, NoteHistoryReason.Restore)
        return true
    }

    private fun prune(dir: File) {
        val files = (dir.listFiles() ?: return).filter { it.name.endsWith(".md") }.sortedByDescending { it.lastModified() }
        files.drop(MAX_VERSIONS).forEach { it.delete() }
    }

    private fun hashOf(path: String): String = Crypto.sha256Hex(path).take(24)

    // ---------- settings and layout ----------

    fun loadSettings(): VaultSettings = runCatching {
        json.decodeFromString<VaultSettings>(settingsFile.readText())
    }.getOrDefault(VaultSettings())

    fun saveSettings(settings: VaultSettings) {
        runCatching {
            skaldDir.mkdirs()
            settingsFile.writeText(json.encodeToString(VaultSettings.serializer(), settings))
        }
    }

    fun loadPositions(): Map<String, Pair<Float, Float>> = runCatching {
        json.decodeFromString<Map<String, List<Float>>>(positionsFile.readText())
            .mapValues { (_, xy) -> xy[0] to xy[1] }
    }.getOrDefault(emptyMap())

    fun savePositions(positions: Map<String, Pair<Float, Float>>) {
        runCatching {
            skaldDir.mkdirs()
            positionsFile.writeText(json.encodeToString(positions.mapValues { listOf(it.value.first, it.value.second) }))
        }
    }

    /** Sync bookkeeping belongs to the vault, not to the installation. */
    fun syncStateStore(): SyncStateStore = object : SyncStateStore {
        private val file get() = File(skaldDir, "sync.json")
        override fun read(): String? = file.takeIf { it.isFile }?.readText()
        override fun write(json: String) {
            skaldDir.mkdirs()
            file.writeText(json)
        }

        override fun clear() {
            file.delete()
        }
    }

    // ---------- SyncVault ----------

    override fun syncNotes(): List<SyncNote> = notes().map { SyncNote(it.path, it.raw) }

    override fun syncRead(path: String): String? = read(path)

    override fun syncWrite(path: String, content: String) = write(path, content, NoteHistoryReason.External)

    override fun syncDelete(path: String) = delete(path)

    override fun syncAssets(): List<SyncAsset> = walk()
        .filter { !it.extension.equals("md", ignoreCase = true) }
        .map { SyncAsset(relativePath(it), it.length(), it.lastModified()) }
        .toList()

    override fun syncReadAsset(path: String): ByteArray? = fileFor(path)?.takeIf { it.isFile }?.readBytes()

    override fun syncWriteAsset(path: String, bytes: ByteArray) {
        val file = fileFor(path) ?: error("Skald will not write to \"$path\"")
        file.parentFile?.mkdirs()
        val staged = File(file.parentFile, "${file.name}.skald-tmp")
        staged.writeBytes(bytes)
        if (!staged.renameTo(file)) {
            file.writeBytes(bytes)
            staged.delete()
        }
    }

    override fun syncDeleteAsset(path: String) {
        fileFor(path)?.takeIf { it.isFile }?.delete()
    }

    private companion object {
        const val COALESCE_MS = 60_000L
        const val MAX_VERSIONS = 40
    }
}
