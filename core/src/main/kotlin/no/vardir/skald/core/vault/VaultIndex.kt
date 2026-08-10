package no.vardir.skald.core.vault

import no.vardir.skald.core.graph.Layout
import no.vardir.skald.core.model.ActivityEvent
import no.vardir.skald.core.model.BacklinkRef
import no.vardir.skald.core.model.Constellation
import no.vardir.skald.core.model.FolderNode
import no.vardir.skald.core.model.GraphData
import no.vardir.skald.core.model.GraphEdge
import no.vardir.skald.core.model.GraphNode
import no.vardir.skald.core.model.NoteMeta
import no.vardir.skald.core.model.TaskItem
import no.vardir.skald.core.model.TaskStatus
import no.vardir.skald.core.model.VaultSettings
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.core.model.VaultStats
import no.vardir.skald.core.text.Frontmatter
import no.vardir.skald.core.text.Notes
import no.vardir.skald.core.text.Tasks
import no.vardir.skald.core.text.Wikilinks

/** One Markdown file as it was read off disk. */
data class RawNote(
    val path: String,
    val raw: String,
    val created: Long,
    val updated: Long,
)

/**
 * Everything derived from the vault's Markdown, in one pass: notes, threads,
 * links, backlinks, the star chart and the honest stats. The rest of the app
 * only ever reads a [VaultSnapshot] — nothing else parses a note.
 */
object VaultIndex {

    private class Record(
        val path: String,
        val raw: String,
        val created: Long,
        val updated: Long,
    ) {
        val parsed = Frontmatter.parse(raw)
        val body: String get() = parsed.body
        val bodyStartLine: Int get() = parsed.bodyStartLine
        val folder = Notes.topFolder(path)
        val title = Notes.title(parsed.frontmatter, path)
        val schema = Notes.inferSchema(parsed.frontmatter, title, folder)
        val tags = Frontmatter.tagsOf(parsed.frontmatter)
        val linkTargets = Wikilinks.targets(parsed.body)
        val wikilinkCount = Wikilinks.count(parsed.body)
        val headings = Notes.headings(parsed.body, parsed.bodyStartLine)
        val excerpt = Notes.excerpt(parsed.body)
        val wordCount = Notes.countWords(parsed.body)
    }

    fun build(
        vaultName: String,
        files: List<RawNote>,
        settings: VaultSettings,
        /** Persisted star positions; nodes without one get laid out beside their folder. */
        positions: Map<String, Layout.Point> = emptyMap(),
        todayIso: String,
        /** Folders that exist on disk but hold no notes, so they still appear in the tree. */
        emptyFolders: Set<String> = emptySet(),
    ): VaultSnapshot {
        val records = files.map { Record(it.path, it.raw, it.created, it.updated) }
        val index = Wikilinks.buildIndex(records.map { Wikilinks.Linkable(it.path, it.title) })

        val notes = mutableListOf<NoteMeta>()
        val tasks = mutableListOf<TaskItem>()
        val edges = mutableListOf<GraphEdge>()
        val edgeKeys = mutableSetOf<String>()
        val linkedInto = mutableSetOf<String>()
        var wikilinksTotal = 0
        var resolvedTotal = 0

        for (rec in records) {
            val links = mutableListOf<String>()
            val linked = mutableSetOf<String>()
            val unresolved = mutableListOf<String>()

            for (target in rec.linkTargets) {
                val hit = index.resolve(target)
                if (hit != null && hit != rec.path) {
                    resolvedTotal++
                    // Two spellings of one note are a single link.
                    if (linked.add(hit)) links += hit
                    linkedInto += hit
                    val key = listOf(rec.path, hit).sorted().joinToString(" ")
                    if (edgeKeys.add(key)) edges += GraphEdge(rec.path, hit)
                } else if (hit == null) {
                    unresolved += target
                }
            }
            wikilinksTotal += rec.wikilinkCount

            val rawTasks = Tasks.extract(rec.body, rec.bodyStartLine)
            for (t in rawTasks) {
                tasks += TaskItem(
                    id = Tasks.idFor(rec.path, t.line),
                    notePath = rec.path,
                    noteTitle = rec.title,
                    line = t.line,
                    content = t.content,
                    status = t.status,
                    priority = t.priority,
                    due = t.due,
                    tags = t.tags,
                )
            }

            notes += NoteMeta(
                path = rec.path,
                title = rec.title,
                folder = rec.folder,
                schema = rec.schema,
                tags = rec.tags,
                frontmatter = rec.parsed.frontmatter,
                links = links,
                unresolved = unresolved,
                headings = rec.headings,
                excerpt = rec.excerpt,
                wordCount = rec.wordCount,
                taskCount = rawTasks.size,
                openTaskCount = rawTasks.count { it.status != TaskStatus.Done },
                created = rec.created,
                updated = rec.updated,
            )
        }

        notes.sortBy { it.path }
        tasks.sortWith(compareBy({ it.due ?: "9999" }, { it.id }))

        val degree = HashMap<String, Int>()
        for (e in edges) {
            degree[e.from] = (degree[e.from] ?: 0) + 1
            degree[e.to] = (degree[e.to] ?: 0) + 1
        }

        val folderOf = notes.associate { it.path to it.folder }
        val laidOut = Layout.layout(
            ids = notes.map { it.path },
            edges = edges.map { it.from to it.to },
            stored = positions,
            groups = folderOf,
        )

        val graph = GraphData(
            nodes = notes.map { n ->
                val p = laidOut[n.path] ?: Layout.Point(0.5f, 0.5f)
                GraphNode(
                    path = n.path,
                    label = n.title,
                    schema = n.schema,
                    folder = n.folder,
                    deg = degree[n.path] ?: 0,
                    x = p.x,
                    y = p.y,
                    updated = n.updated,
                )
            },
            edges = edges,
        )

        val folders = (notes.mapNotNull { Notes.parentFolder(it.path).takeIf(String::isNotEmpty) } + emptyFolders)
            .flatMap { path -> path.split('/').scan("") { acc, seg -> if (acc.isEmpty()) seg else "$acc/$seg" } }
            .filter { it.isNotEmpty() }
            .toSortedSet()

        val stats = VaultStats(
            notes = notes.size,
            folders = folders.size,
            tasksOpen = tasks.count { it.status != TaskStatus.Done },
            tasksTotal = tasks.size,
            overdue = tasks.count { it.isOverdue(todayIso) },
            wikilinks = wikilinksTotal,
            resolved = resolvedTotal,
            orphans = notes.count { it.links.isEmpty() && it.path !in linkedInto },
        )

        return VaultSnapshot(
            vaultName = vaultName,
            tree = buildTree(notes, folders),
            notes = notes,
            tasks = tasks,
            stats = stats,
            graph = graph,
            constellations = constellationsOf(graph),
            activity = activityOf(notes, tasks),
            settings = settings,
        )
    }

    private fun buildTree(notes: List<NoteMeta>, folders: Set<String>): FolderNode {
        // Built mutably, then frozen: a folder tree is naturally top-down and a
        // persistent structure would mean rebuilding every ancestor per note.
        class Builder(val name: String, val path: String) {
            val children = LinkedHashMap<String, Builder>()
            val noteIds = mutableListOf<String>()
            fun freeze(): FolderNode = FolderNode(
                name = name,
                path = path,
                folders = children.values.sortedBy { it.name.lowercase() }.map { it.freeze() },
                notes = noteIds.sortedBy { it.lowercase() },
            )
        }

        val root = Builder("", "")
        val dirs = hashMapOf("" to root)

        fun ensure(path: String): Builder = dirs.getOrPut(path) {
            val parent = ensure(path.substringBeforeLast('/', ""))
            Builder(path.substringAfterLast('/'), path).also { parent.children[path] = it }
        }

        for (folder in folders.sorted()) ensure(folder)
        for (note in notes) ensure(Notes.parentFolder(note.path)).noteIds += note.path
        return root.freeze()
    }

    /**
     * Named clusters, derived from the folders the stars belong to. The desktop
     * lets you name your own; here the folder is the name, which is what a fresh
     * layout already gathers them by.
     */
    private fun constellationsOf(graph: GraphData): List<Constellation> =
        graph.nodes.filter { it.folder.isNotEmpty() }
            .groupBy { it.folder }
            .filterValues { it.size >= 2 }
            .map { (folder, nodes) -> Constellation(folder, nodes.map { it.path }) }
            .sortedBy { it.name }

    /** The saga: the most recent things that happened, newest first. */
    private fun activityOf(notes: List<NoteMeta>, tasks: List<TaskItem>): List<ActivityEvent> {
        val recentNotes = notes.sortedByDescending { it.updated }.take(30).map { note ->
            ActivityEvent(
                kind = ActivityEvent.Kind.Note,
                verb = if (note.created >= note.updated - 1000) "drafted" else "edited",
                title = note.title,
                ref = note.folder.ifEmpty { "vault" },
                ts = note.updated,
            )
        }
        val byPath = notes.associateBy { it.path }
        val doneTasks = tasks.filter { it.status == TaskStatus.Done }
            .sortedByDescending { byPath[it.notePath]?.updated ?: 0L }
            .take(15)
            .map { task ->
                ActivityEvent(
                    kind = ActivityEvent.Kind.Task,
                    verb = "completed",
                    title = task.content,
                    ref = task.noteTitle,
                    ts = byPath[task.notePath]?.updated ?: 0L,
                )
            }
        return (recentNotes + doneTasks).sortedByDescending { it.ts }.take(60)
    }

    /** Notes that link to `path`, with a passage from each. */
    fun backlinks(snapshot: VaultSnapshot, path: String, bodyOf: (String) -> String?): List<BacklinkRef> {
        val target = snapshot.byPath[path] ?: return emptyList()
        return snapshot.notes
            .filter { it.path != path && path in it.links }
            .map { source ->
                val body = bodyOf(source.path) ?: ""
                BacklinkRef(
                    path = source.path,
                    title = source.title,
                    schema = source.schema,
                    folder = source.folder,
                    snippet = Wikilinks.snippetAround(body, target.title),
                    updated = source.updated,
                )
            }
            .sortedByDescending { it.updated }
    }
}
