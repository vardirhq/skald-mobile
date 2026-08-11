package no.vardir.skald.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.vardir.skald.core.model.TaskStatus
import no.vardir.skald.core.model.VaultSnapshot
import no.vardir.skald.core.text.Fuzzy
import no.vardir.skald.core.text.Search
import no.vardir.skald.ui.components.FilterChip
import no.vardir.skald.ui.components.Hairline
import no.vardir.skald.ui.components.Rune
import no.vardir.skald.ui.components.SectionHeader
import no.vardir.skald.ui.theme.Skald

/** What the Hall can find: notes, threads, and cantos — the commands. */
private enum class HallFilter(val label: String) { All("All"), Notes("Notes"), Threads("Threads"), Cantos("Cantos") }

sealed interface HallHit {
    val title: String
    val trailing: String

    data class Note(
        override val title: String,
        val path: String,
        val schema: no.vardir.skald.core.model.SchemaName,
        override val trailing: String,
        val snippet: String = "",
    ) : HallHit
    data class Thread(override val title: String, val path: String, val line: Int, override val trailing: String) : HallHit
    data class Tag(override val title: String, val count: Int) : HallHit { override val trailing: String get() = count.toString() }
    data class Saved(override val title: String, val id: String, val query: String) : HallHit { override val trailing: String get() = "hold to remove" }
    // Named `action` rather than `run`, because `hit.run()` would collide with
    // the stdlib scope function.
    data class Canto(override val title: String, val action: () -> Unit) : HallHit {
        override val trailing: String get() = "↵"
    }
}

/**
 * Skald's Hall. Weighted fuzzy search over everything, as a full-screen sheet
 * rather than a palette — a phone has no ⌘K, and a sheet is the honest shape.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun HallSheet(
    snapshot: VaultSnapshot,
    todayIso: String,
    cantos: List<HallHit.Canto>,
    onOpenNote: (String) -> Unit,
    initialQuery: String = "",
    onSaveSearch: (String) -> Unit = {},
    onRemoveSavedSearch: (String) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Skald.colors
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var filter by remember { mutableStateOf(HallFilter.All) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val hits = remember(query, filter, snapshot) {
        rank(snapshot, cantos, query, filter, todayIso)
    }

    Column(modifier.fillMaxSize().background(colors.bg2)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("›", style = Skald.type.meta.copy(fontWeight = FontWeight.Bold), color = colors.accent)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Ask the skáld — notes, threads, or a canto…",
                        style = Skald.type.body.copy(fontSize = 16.sp),
                        color = colors.tx3,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = Skald.type.body.copy(color = colors.tx0, fontSize = 16.sp),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotBlank() && snapshot.settings.savedSearches.none { it.query == query.trim() }) Text(
                "Save",
                style = Skald.type.row,
                color = colors.accent,
                modifier = Modifier.clickable { onSaveSearch(query) }.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            Text("Cancel", style = Skald.type.row, color = colors.tx2, modifier = Modifier.clickable(onClick = onClose))
        }
        Hairline()

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (option in HallFilter.entries) {
                FilterChip(option.label, filter == option, { filter = option })
            }
        }
        Hairline()

        if (hits.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text(
                    "The skáld has nothing to sing. Try fewer words.",
                    style = Skald.type.row,
                    color = colors.tx3,
                    modifier = Modifier.padding(top = 60.dp, start = 20.dp, end = 20.dp),
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            for ((group, rows) in hits) {
                item(key = "g-$group") {
                    Box(Modifier.padding(start = 6.dp, top = 12.dp)) { SectionHeader(group) }
                }
                items(rows, key = { it.first.key() }) { (hit, indices) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .combinedClickable(
                                onLongClick = { if (hit is HallHit.Saved) onRemoveSavedSearch(hit.id) },
                                onClick = {
                                when (hit) {
                                    is HallHit.Note -> onOpenNote(hit.path)
                                    is HallHit.Thread -> onOpenNote(hit.path)
                                    is HallHit.Canto -> hit.action()
                                    is HallHit.Tag -> { query = "tag:${hit.title}"; filter = HallFilter.Notes }
                                    is HallHit.Saved -> { query = hit.query; filter = HallFilter.Notes }
                                }
                                if (hit is HallHit.Note || hit is HallHit.Thread || hit is HallHit.Canto) onClose()
                            })
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (hit) {
                            is HallHit.Note -> Rune(hit.schema)
                            is HallHit.Thread -> Text("☐", style = Skald.type.meta, color = colors.tx3, modifier = Modifier.width(18.dp))
                            is HallHit.Canto -> Text("›", style = Skald.type.meta, color = colors.tx3, modifier = Modifier.width(18.dp))
                            is HallHit.Tag -> Text("#", style = Skald.type.meta, color = colors.accent, modifier = Modifier.width(18.dp))
                            is HallHit.Saved -> Text("⌕", style = Skald.type.meta, color = colors.accent, modifier = Modifier.width(18.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                highlighted(hit.title, indices, colors.accent),
                                style = Skald.type.row,
                                color = colors.tx1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (hit is HallHit.Note && hit.snippet.isNotBlank()) Text(
                                hit.snippet,
                                style = Skald.type.meta,
                                color = colors.tx3,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(hit.trailing, style = Skald.type.meta, color = colors.tx4)
                    }
                }
            }
            item { Box(Modifier.padding(bottom = 30.dp)) }
        }
    }
}

private fun HallHit.key(): String = when (this) {
    is HallHit.Note -> "n:$path"
    is HallHit.Thread -> "t:$path#$line"
    is HallHit.Canto -> "c:$title"
    is HallHit.Tag -> "tag:$title"
    is HallHit.Saved -> "saved:$id"
}

private fun rank(
    snapshot: VaultSnapshot,
    cantos: List<HallHit.Canto>,
    query: String,
    filter: HallFilter,
    todayIso: String,
): List<Pair<String, List<Pair<HallHit, List<Int>>>>> {
    fun matches(items: List<HallHit>): List<Pair<HallHit, List<Int>>> =
        items.mapNotNull { hit ->
            val result = Fuzzy.match(query, hit.title) ?: return@mapNotNull null
            Triple(hit, result.score, result.indices)
        }
            .sortedByDescending { it.second }
            .map { it.first to it.third }
            .take(if (query.isEmpty()) 8 else 30)

    val notes = if (query.isBlank()) snapshot.notes.sortedByDescending { it.updated }.take(8).map {
        HallHit.Note(it.title, it.path, it.schema, it.folder.ifEmpty { snapshot.vaultName }, it.excerpt)
    } else Search.find(snapshot.notes, query, limit = 40).map {
        HallHit.Note(it.title, it.path, it.schema, "${it.path} · line ${it.line}", it.snippet)
    }
    val threads = snapshot.tasks.filter { it.status != TaskStatus.Done }.map {
        HallHit.Thread(it.content, it.notePath, it.line, it.due?.let { due -> if (due < todayIso) "overdue" else due.drop(5) } ?: "—")
    }

    return buildList {
        if (query.isBlank() && filter == HallFilter.All && snapshot.settings.savedSearches.isNotEmpty()) {
            add("Saved searches" to snapshot.settings.savedSearches.map { HallHit.Saved(it.name, it.id, it.query) to emptyList() })
        }
        if (query.isBlank() && filter == HallFilter.All) {
            val tags = snapshot.notes.flatMap { it.tags }.groupingBy { it.lowercase() }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(12)
                .map { HallHit.Tag(it.key, it.value) to emptyList<Int>() }
            if (tags.isNotEmpty()) add("Tags" to tags)
        }
        if (filter == HallFilter.All || filter == HallFilter.Cantos) {
            matches(cantos).takeIf { it.isNotEmpty() }?.let { add("Cantos" to it) }
        }
        if (filter == HallFilter.All || filter == HallFilter.Notes) {
            val noteHits = notes.map { it to emptyList<Int>() }
            noteHits.takeIf { it.isNotEmpty() }?.let { add("Notes" to it) }
        }
        if (filter == HallFilter.All || filter == HallFilter.Threads) {
            matches(threads).takeIf { it.isNotEmpty() }?.let { add("Threads" to it) }
        }
    }
}

private fun highlighted(text: String, indices: List<Int>, accent: androidx.compose.ui.graphics.Color) =
    buildAnnotatedString {
        for (segment in Fuzzy.highlight(text, indices)) {
            if (segment.hit) {
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(segment.text) }
            } else {
                append(segment.text)
            }
        }
    }
