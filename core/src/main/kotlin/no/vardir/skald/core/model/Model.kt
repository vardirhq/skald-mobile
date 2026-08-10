package no.vardir.skald.core.model

import kotlinx.serialization.Serializable

/**
 * The typed schema every note carries. Each one wears a monoline rune that
 * follows the note wherever it is mentioned — sidebar, search, backlinks, graph.
 */
enum class SchemaName {
    Note, Project, Person, Daily, Idea, Source, Code, Place;

    companion object {
        fun fromOrNull(raw: String?): SchemaName? =
            raw?.let { value -> entries.firstOrNull { it.name.equals(value, ignoreCase = true) } }
    }
}

enum class TaskStatus {
    Open, Working, Blocked, Done;

    /** The token written into `@status(...)`, and read back out of it. */
    val token: String get() = name.lowercase()

    companion object {
        fun fromToken(raw: String?): TaskStatus? = when (raw?.trim()?.lowercase()) {
            "open" -> Open
            "working", "in-progress", "doing" -> Working
            "blocked" -> Blocked
            "done" -> Done
            else -> null
        }
    }
}

enum class TaskPriority {
    Low, Med, High;

    val token: String get() = name.lowercase()

    companion object {
        fun fromToken(raw: String?): TaskPriority = when (raw?.trim()?.lowercase()) {
            "high", "3" -> High
            "low", "1" -> Low
            else -> Med
        }
    }
}

/**
 * A checkbox somewhere in the vault. `id` is stable within one index snapshot
 * only — a line number moves when the note above it is edited.
 */
data class TaskItem(
    val id: String,
    val notePath: String,
    val noteTitle: String,
    /** 1-based line number in the raw file, frontmatter included. */
    val line: Int,
    val content: String,
    val status: TaskStatus,
    val priority: TaskPriority,
    /** `YYYY-MM-DD`, or null. */
    val due: String? = null,
    val tags: List<String> = emptyList(),
) {
    fun isOverdue(todayIso: String): Boolean = due != null && due < todayIso && status != TaskStatus.Done
}

data class HeadingItem(val level: Int, val text: String, val line: Int)

data class BacklinkRef(
    val path: String,
    val title: String,
    val schema: SchemaName,
    val folder: String,
    val snippet: String,
    val updated: Long,
)

data class NoteMeta(
    /** Vault-relative path; also the note's identity. */
    val path: String,
    val title: String,
    /** Top-level folder, or "" at the vault root. */
    val folder: String,
    val schema: SchemaName,
    val tags: List<String> = emptyList(),
    val frontmatter: Map<String, Any?> = emptyMap(),
    /** Outgoing wikilinks that resolved to a note path. */
    val links: List<String> = emptyList(),
    /** Wikilink names that resolve to nothing. */
    val unresolved: List<String> = emptyList(),
    val headings: List<HeadingItem> = emptyList(),
    val excerpt: String = "",
    val wordCount: Int = 0,
    val taskCount: Int = 0,
    val openTaskCount: Int = 0,
    val created: Long = 0,
    val updated: Long = 0,
)

data class FolderNode(
    val name: String,
    val path: String,
    val folders: List<FolderNode> = emptyList(),
    val notes: List<String> = emptyList(),
) {
    /** Every note under this folder, at any depth. */
    fun allNotes(): List<String> = notes + folders.flatMap { it.allNotes() }

    /** Every folder under this one, at any depth, parents before children. */
    fun allFolders(): List<FolderNode> = folders.flatMap { listOf(it) + it.allFolders() }

    /** How deep this folder sits, so a flat list can still be indented. */
    val depth: Int get() = if (path.isEmpty()) 0 else path.count { it == '/' } + 1
}

@Serializable
data class GraphNode(
    val path: String,
    val label: String,
    val schema: SchemaName,
    val folder: String,
    val deg: Int,
    val x: Float,
    val y: Float,
    val updated: Long = 0,
)

data class GraphEdge(val from: String, val to: String)

data class GraphData(val nodes: List<GraphNode> = emptyList(), val edges: List<GraphEdge> = emptyList())

/** A named cluster of stars, drawn as a region behind them. */
data class Constellation(val name: String, val nodes: List<String>)

data class ActivityEvent(
    val kind: Kind,
    val verb: String,
    val title: String,
    val ref: String,
    val ts: Long,
) {
    enum class Kind { Note, Task }
}

data class VaultStats(
    val notes: Int = 0,
    val folders: Int = 0,
    val tasksOpen: Int = 0,
    val tasksTotal: Int = 0,
    val overdue: Int = 0,
    val wikilinks: Int = 0,
    val resolved: Int = 0,
    val orphans: Int = 0,
)

enum class ThemeName { Midnight, Slate, Daybreak }

enum class Density { Compact, Regular, Cozy }

enum class LogoVariant { Sigil, Monogram, Bracket }

@Serializable
data class VaultSettings(
    val theme: ThemeName = ThemeName.Midnight,
    val density: Density = Density.Regular,
    val logoVariant: LogoVariant = LogoVariant.Sigil,
    /** Show the backlinks/outline sheet affordance in the editor. */
    val marginOn: Boolean = true,
    val pinnedNote: String? = null,
    val dailyFolder: String = "Daily",
    val attachmentsFolder: String = "Attachments",
    val editorFontSize: Int = 16,
    val autosaveMs: Int = 800,
)

/** One fully indexed view of the vault, which every screen reads from. */
data class VaultSnapshot(
    val vaultName: String,
    val tree: FolderNode,
    val notes: List<NoteMeta>,
    val tasks: List<TaskItem>,
    val stats: VaultStats,
    val graph: GraphData,
    val constellations: List<Constellation>,
    val activity: List<ActivityEvent>,
    val settings: VaultSettings,
) {
    val byPath: Map<String, NoteMeta> by lazy { notes.associateBy { it.path } }
}

enum class AttachmentKind { Image, Pdf, Audio, Video, File }

data class AttachmentRef(
    /** Target exactly as written in the Markdown. */
    val target: String,
    /** Resolved vault-relative path, or null when the target escapes the vault. */
    val path: String?,
    val label: String,
    val embedded: Boolean,
    val exists: Boolean,
    val mime: String,
    val kind: AttachmentKind,
    val size: Long?,
)

/** Everything the editor needs for one note. */
data class NotePayload(
    val meta: NoteMeta,
    /** Raw file content, frontmatter included. */
    val content: String,
    /** Body only. */
    val body: String,
    /** Line offset of the body within the raw content. */
    val bodyStartLine: Int,
    val backlinks: List<BacklinkRef>,
    val attachments: List<AttachmentRef>,
)

enum class NoteHistoryReason { Edit, External, Rename, Delete, Restore, Sync }

data class NoteHistoryEntry(
    val id: String,
    val notePath: String,
    val createdAt: Long,
    val size: Long,
    val reason: NoteHistoryReason,
)
