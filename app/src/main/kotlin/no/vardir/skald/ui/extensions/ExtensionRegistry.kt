package no.vardir.skald.ui.extensions

import androidx.compose.runtime.Composable
import no.vardir.skald.core.extensions.ExtensionCatalog
import no.vardir.skald.core.extensions.ExtensionDescriptor
import no.vardir.skald.core.text.Markdown
import no.vardir.skald.core.text.Insertions
import no.vardir.skald.ui.components.MarkdownContext

data class MarkdownComponentContribution(
    val type: String,
    val render: @Composable (Markdown.Block.Callout, MarkdownContext) -> Unit,
)

data class InsertionPropertyPrompt(
    val key: String,
    val label: String,
    val placeholder: String,
    val help: String,
    val normalize: (Any?) -> String?,
)

data class EditorInsertionContribution(
    val id: String,
    val label: String,
    val description: String,
    val glyph: String,
    val keywords: Set<String> = emptySet(),
    val template: Insertions.Template,
    val property: InsertionPropertyPrompt? = null,
)

data class CodeFenceContribution(
    val language: String,
    val render: @Composable (Markdown.Block.Code) -> Unit,
)

data class RendererExtension(
    val descriptor: ExtensionDescriptor,
    val markdownComponents: List<MarkdownComponentContribution>,
    val editorInsertions: List<EditorInsertionContribution> = emptyList(),
    val codeFences: List<CodeFenceContribution> = emptyList(),
)

class RendererExtensionRegistry(extensions: List<RendererExtension>) {
    val extensions: List<RendererExtension> = extensions.toList()
    val catalog = ExtensionCatalog(extensions.map { it.descriptor })
    private val components: Map<String, MarkdownComponentContribution>
    val editorInsertions: List<EditorInsertionContribution>
    private val fences: Map<String, CodeFenceContribution>

    init {
        val values = mutableMapOf<String, MarkdownComponentContribution>()
        val insertionValues = mutableMapOf<String, EditorInsertionContribution>()
        val fenceValues = mutableMapOf<String, CodeFenceContribution>()
        for (extension in extensions) {
            val declared = extension.descriptor.markdownComponents.map(String::lowercase).toSet()
            val supplied = extension.markdownComponents.map { it.type.lowercase() }.toSet()
            require(declared == supplied) {
                "Renderer contributions for ${extension.descriptor.manifest.id} do not match its manifest"
            }
            val declaredInsertions = extension.descriptor.editorInsertions
            val suppliedInsertions = extension.editorInsertions.map { it.id }.toSet()
            require(declaredInsertions == suppliedInsertions) {
                "Editor insertion contributions for ${extension.descriptor.manifest.id} do not match its manifest"
            }
            val declaredFences = extension.descriptor.codeFences.map(String::lowercase).toSet()
            val suppliedFences = extension.codeFences.map { it.language.lowercase() }.toSet()
            require(declaredFences == suppliedFences) {
                "Code fence contributions for ${extension.descriptor.manifest.id} do not match its manifest"
            }
            for (component in extension.markdownComponents) {
                require(values.put(component.type.lowercase(), component) == null) {
                    "Duplicate component renderer: ${component.type}"
                }
            }
            for (insertion in extension.editorInsertions) {
                require(insertionValues.put(insertion.id, insertion) == null) {
                    "Duplicate editor insertion: ${insertion.id}"
                }
                val property = insertion.property
                require(property == null || property.key in extension.descriptor.noteProperties) {
                    "Editor insertion ${insertion.id} requires an undeclared note property"
                }
            }
            for (fence in extension.codeFences) {
                require(fenceValues.put(fence.language.lowercase(), fence) == null) {
                    "Duplicate code fence renderer: ${fence.language}"
                }
            }
        }
        components = values
        editorInsertions = insertionValues.values.toList()
        fences = fenceValues
    }

    fun component(type: String): MarkdownComponentContribution? = components[type.lowercase()]
    fun editorInsertion(id: String): EditorInsertionContribution? =
        editorInsertions.firstOrNull { it.id == id }
    fun codeFence(language: String): CodeFenceContribution? = fences[language.lowercase()]
}

val builtInExtensionRegistry = RendererExtensionRegistry(listOf(githubRendererExtension, mermaidRendererExtension))
