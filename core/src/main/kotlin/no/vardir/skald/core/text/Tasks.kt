package no.vardir.skald.core.text

import no.vardir.skald.core.model.TaskPriority
import no.vardir.skald.core.model.TaskStatus

/**
 * Threads. A task line looks like:
 *
 *     - [ ] Write the saga @due(2026-06-01) @p(high) #editor
 *     - [x] Done thing
 *     - [ ] Blocked thing @status(blocked)
 *
 * Unchecked is open unless `@status()` says otherwise; checked is always done.
 * The binding is bidirectional — [updateLine] rewrites the source line in place,
 * preserving the metadata tokens nobody edited.
 */
object Tasks {

    private val TASK_RE = Regex("""^(\s*)[-*+]\s+\[( |x|X)]\s+(.*)$""")
    private val TAG_RE = Regex("""(^|\s)#([\w/-]+)""")
    private val DUE_RE = Regex("""@due\(([^)]+)\)""", RegexOption.IGNORE_CASE)
    private val PRIORITY_RE = Regex("""@p(?:riority)?\(([^)]+)\)""", RegexOption.IGNORE_CASE)
    private val STATUS_RE = Regex("""@status\(([^)]+)\)""", RegexOption.IGNORE_CASE)
    private val DATE_RE = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""")

    data class RawTask(
        /** 1-based. */
        val line: Int,
        val content: String,
        val status: TaskStatus,
        val priority: TaskPriority,
        val due: String?,
        val tags: List<String>,
    )

    fun extract(body: String, lineOffset: Int = 0): List<RawTask> {
        val out = mutableListOf<RawTask>()
        body.split("\n").forEachIndexed { i, line ->
            val m = TASK_RE.find(line) ?: return@forEachIndexed
            val checked = m.groupValues[2].lowercase() == "x"
            var text = m.groupValues[3].trim()

            val tags = mutableListOf<String>()
            text = TAG_RE.replace(text) { hit ->
                tags += hit.groupValues[2]
                hit.groupValues[1]
            }

            var due: String? = null
            text = DUE_RE.replace(text) { hit ->
                due = normalizeDate(hit.groupValues[1].trim())
                ""
            }

            var priority = TaskPriority.Med
            text = PRIORITY_RE.replace(text) { hit ->
                priority = TaskPriority.fromToken(hit.groupValues[1])
                ""
            }

            var status = if (checked) TaskStatus.Done else TaskStatus.Open
            text = STATUS_RE.replace(text) { hit ->
                if (!checked) {
                    when (TaskStatus.fromToken(hit.groupValues[1])) {
                        TaskStatus.Working -> status = TaskStatus.Working
                        TaskStatus.Blocked -> status = TaskStatus.Blocked
                        else -> Unit
                    }
                }
                ""
            }

            out += RawTask(
                line = i + 1 + lineOffset,
                content = text.replace(Regex("""\s{2,}"""), " ").trim(),
                status = status,
                priority = priority,
                due = due,
                tags = tags,
            )
        }
        return out
    }

    private fun normalizeDate(d: String): String? {
        val m = DATE_RE.find(d) ?: return null
        val (y, mo, day) = m.destructured
        return "$y-${mo.padStart(2, '0')}-${day.padStart(2, '0')}"
    }

    data class Edits(
        val status: TaskStatus? = null,
        val content: String? = null,
        /** `Unset` leaves the due date alone; `Clear` removes it. */
        val due: DueEdit = DueEdit.Unset,
        val priority: TaskPriority? = null,
        /** Null leaves the tags alone; an empty list takes them all off. */
        val tags: List<String>? = null,
    )

    sealed interface DueEdit {
        data object Unset : DueEdit
        data object Clear : DueEdit
        data class Set(val value: String) : DueEdit
    }

    /**
     * Rewrite the task on `line` (1-based, in the raw file) applying `edits`.
     * Returns the raw content unchanged when that line is not a task, so a stale
     * line number can never corrupt a note.
     */
    fun updateLine(raw: String, line: Int, edits: Edits): String {
        val lines = raw.split("\n").toMutableList()
        val idx = line - 1
        if (idx < 0 || idx >= lines.size) return raw
        val m = TASK_RE.find(lines[idx]) ?: return raw

        val existing = extract(lines[idx]).firstOrNull() ?: return raw
        val status = edits.status ?: existing.status
        val content = (edits.content ?: existing.content).trim()
        val due = when (val d = edits.due) {
            DueEdit.Unset -> existing.due
            DueEdit.Clear -> null
            is DueEdit.Set -> d.value
        }
        val priority = edits.priority ?: existing.priority
        val tags = edits.tags ?: existing.tags

        lines[idx] = m.groupValues[1] + formatLine(content, status, due, priority, tags)
        return lines.joinToString("\n")
    }

    /** The single task on a raw line, for a sheet that edits one of them. */
    fun parseLine(line: String): RawTask? = extract(line).firstOrNull()

    /** Serialize a brand-new task line (without leading indentation). */
    fun formatLine(
        content: String,
        status: TaskStatus = TaskStatus.Open,
        due: String? = null,
        priority: TaskPriority = TaskPriority.Med,
        tags: List<String> = emptyList(),
    ): String {
        val checkbox = if (status == TaskStatus.Done) "x" else " "
        val parts = mutableListOf(content.trim())
        if (due != null) parts += "@due($due)"
        if (priority != TaskPriority.Med) parts += "@p(${priority.token})"
        if (status == TaskStatus.Working || status == TaskStatus.Blocked) parts += "@status(${status.token})"
        for (t in tags) parts += "#$t"
        return "- [$checkbox] ${parts.joinToString(" ")}"
    }

    fun idFor(notePath: String, line: Int): String = "$notePath#L$line"

    /** True when the given raw line is a checkbox Skald would index. */
    fun isTaskLine(line: String): Boolean = TASK_RE.containsMatchIn(line)
}
