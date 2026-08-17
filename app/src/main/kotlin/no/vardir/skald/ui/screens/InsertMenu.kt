package no.vardir.skald.ui.screens

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import no.vardir.skald.core.text.Insertions
import no.vardir.skald.ui.components.Eyebrow
import no.vardir.skald.ui.components.FieldLabel
import no.vardir.skald.ui.components.SheetAction
import no.vardir.skald.ui.components.SheetButtons
import no.vardir.skald.ui.components.SkaldSheet
import no.vardir.skald.ui.components.SkaldTextField
import no.vardir.skald.ui.extensions.EditorInsertionContribution
import no.vardir.skald.ui.theme.Skald

enum class InsertCategory(val label: String) { Text("Text"), Lists("Lists"), Blocks("Blocks"), Extensions("Extensions") }

sealed interface InsertTarget {
    data class Format(val action: FormatAction) : InsertTarget
    data class Template(val template: Insertions.Template) : InsertTarget
    data class Extension(val contribution: EditorInsertionContribution) : InsertTarget
}

data class InsertMenuItem(
    val id: String,
    val label: String,
    val description: String,
    val glyph: String,
    val category: InsertCategory,
    val keywords: Set<String> = emptySet(),
    val target: InsertTarget,
)

private val CORE_INSERTIONS = listOf(
    format("heading", "Heading", "Start or change a section heading", "H", InsertCategory.Text, FormatAction.Heading, "title", "section"),
    format("bold", "Bold text", "Emphasize selected text", "B", InsertCategory.Text, FormatAction.Bold, "strong"),
    format("italic", "Italic text", "Add light emphasis", "I", InsertCategory.Text, FormatAction.Italic, "emphasis"),
    format("strike", "Strikethrough", "Mark text as no longer applicable", "S", InsertCategory.Text, FormatAction.Strike),
    format("inline-code", "Inline code", "Format a word or phrase as code", "`", InsertCategory.Text, FormatAction.Code, "monospace"),
    format("link", "Web link", "Link text to a URL", "↗", InsertCategory.Text, FormatAction.Link, "url", "hyperlink"),
    format("wikilink", "Note link", "Link to another note in the vault", "[[ ]]", InsertCategory.Text, FormatAction.Wikilink, "wiki", "backlink"),
    format("break", "Line break", "Continue on a new line in this block", "↵", InsertCategory.Text, FormatAction.Break, "soft break"),
    format("bullet", "Bulleted list", "Start or convert an unordered list", "•", InsertCategory.Lists, FormatAction.Bullet, "unordered"),
    format("numbered", "Numbered list", "Start or convert an ordered list", "1.", InsertCategory.Lists, FormatAction.Numbered, "ordered"),
    format("task", "Task", "Add an unchecked thread", "☐", InsertCategory.Lists, FormatAction.Task, "todo", "checkbox", "thread"),
    format("quote", "Quote", "Add or remove block quoting", "❝", InsertCategory.Blocks, FormatAction.Quote, "blockquote"),
    template(
        "aside", "Aside", "Group supporting context without changing its Markdown blocks", "◧", InsertCategory.Blocks,
        Insertions.semanticContainer("aside", "Supporting context"), "semantic", "context", "container",
    ),
    template(
        "gallery", "Gallery", "Group images or media for a mobile-friendly gallery", "▧", InsertCategory.Blocks,
        Insertions.semanticContainer("gallery", "![Image](image.jpg)"), "semantic", "images", "media", "container",
    ),
    template(
        "group", "Group", "Group related blocks under one semantic section", "⌗", InsertCategory.Blocks,
        Insertions.semanticContainer("group", "Grouped content"), "semantic", "section", "container",
    ),
    template(
        "callout", "Callout", "Add a highlighted note block", "!", InsertCategory.Blocks,
        Insertions.Template("> [!note]\n> Callout text", "Callout text"), "admonition",
    ),
    format("fence", "Code block", "Add a fenced code block", "```", InsertCategory.Blocks, FormatAction.Fence, "snippet"),
    template(
        "table", "Table", "Add a two-column Markdown table", "▦", InsertCategory.Blocks,
        Insertions.Template("| Column 1 | Column 2 |\n| --- | --- |\n| Value | Value |", "Column 1"), "grid", "columns",
    ),
    format("rule", "Divider", "Separate sections with a horizontal rule", "—", InsertCategory.Blocks, FormatAction.Rule, "separator"),
)

@Composable
fun InsertMenuSheet(
    extensionInsertions: List<EditorInsertionContribution>,
    onChoose: (InsertTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val all = remember(extensionInsertions) {
        CORE_INSERTIONS + extensionInsertions.map { contribution ->
            InsertMenuItem(
                id = contribution.id,
                label = contribution.label,
                description = contribution.description,
                glyph = contribution.glyph,
                category = InsertCategory.Extensions,
                keywords = contribution.keywords,
                target = InsertTarget.Extension(contribution),
            )
        }
    }
    val visible = remember(all, query) { all.filter { matches(it, query) } }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    SkaldSheet(title = "Insert", subtitle = "Markdown stays portable", onDismiss = onDismiss) {
        SkaldTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Heading, aside, gallery, repository…",
            focusRequester = focus,
            imeAction = ImeAction.Search,
        )
        for (category in InsertCategory.entries) {
            val entries = visible.filter { it.category == category }
            if (entries.isEmpty()) continue
            Eyebrow(category.label, Modifier.padding(top = 12.dp, bottom = 2.dp))
            for (item in entries) SheetAction(item.glyph, item.label, item.description) { onChoose(item.target) }
        }
        if (visible.isEmpty()) Text(
            "Nothing matches “${query.trim()}”.", style = Skald.type.small, color = Skald.colors.tx3,
            modifier = Modifier.padding(vertical = 24.dp),
        )
    }
}

@Composable
fun InsertionPropertySheet(
    contribution: EditorInsertionContribution,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val property = requireNotNull(contribution.property)
    var value by remember(contribution.id) { mutableStateOf("") }
    val normalized = property.normalize(value)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    SkaldSheet(
        title = contribution.label, subtitle = contribution.description, onDismiss = onDismiss,
        actions = {
            SheetButtons(
                confirm = "Connect and insert", enabled = normalized != null,
                onConfirm = { normalized?.let(onConfirm) }, onDismiss = onDismiss,
            )
        },
    ) {
        FieldLabel(property.label)
        SkaldTextField(
            value = value, onValueChange = { value = it }, placeholder = property.placeholder,
            focusRequester = focus, imeAction = ImeAction.Done, onSubmit = { normalized?.let(onConfirm) },
        )
        Text(property.help, style = Skald.type.metaSmall, color = Skald.colors.tx3, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun matches(item: InsertMenuItem, query: String): Boolean {
    val words = query.lowercase().trim().split(Regex("""\s+""")).filter(String::isNotEmpty)
    if (words.isEmpty()) return true
    val haystack = (listOf(item.label, item.description, item.category.label) + item.keywords).joinToString(" ").lowercase()
    return words.all(haystack::contains)
}

private fun format(
    id: String, label: String, description: String, glyph: String, category: InsertCategory,
    action: FormatAction, vararg keywords: String,
) = InsertMenuItem("core.$id", label, description, glyph, category, keywords.toSet(), InsertTarget.Format(action))

private fun template(
    id: String, label: String, description: String, glyph: String, category: InsertCategory,
    value: Insertions.Template, vararg keywords: String,
) = InsertMenuItem("core.$id", label, description, glyph, category, keywords.toSet(), InsertTarget.Template(value))
