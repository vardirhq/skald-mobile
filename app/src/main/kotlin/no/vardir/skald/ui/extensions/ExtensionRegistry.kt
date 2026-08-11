package no.vardir.skald.ui.extensions

import androidx.compose.runtime.Composable
import no.vardir.skald.core.extensions.ExtensionCatalog
import no.vardir.skald.core.extensions.ExtensionDescriptor
import no.vardir.skald.core.text.Markdown
import no.vardir.skald.ui.components.MarkdownContext

data class MarkdownComponentContribution(
    val type: String,
    val render: @Composable (Markdown.Block.Callout, MarkdownContext) -> Unit,
)

data class RendererExtension(
    val descriptor: ExtensionDescriptor,
    val markdownComponents: List<MarkdownComponentContribution>,
)

class RendererExtensionRegistry(extensions: List<RendererExtension>) {
    val extensions: List<RendererExtension> = extensions.toList()
    val catalog = ExtensionCatalog(extensions.map { it.descriptor })
    private val components: Map<String, MarkdownComponentContribution>

    init {
        val values = mutableMapOf<String, MarkdownComponentContribution>()
        for (extension in extensions) {
            val declared = extension.descriptor.markdownComponents.map(String::lowercase).toSet()
            val supplied = extension.markdownComponents.map { it.type.lowercase() }.toSet()
            require(declared == supplied) {
                "Renderer contributions for ${extension.descriptor.manifest.id} do not match its manifest"
            }
            for (component in extension.markdownComponents) {
                require(values.put(component.type.lowercase(), component) == null) {
                    "Duplicate component renderer: ${component.type}"
                }
            }
        }
        components = values
    }

    fun component(type: String): MarkdownComponentContribution? = components[type.lowercase()]
}

val builtInExtensionRegistry = RendererExtensionRegistry(listOf(githubRendererExtension))
