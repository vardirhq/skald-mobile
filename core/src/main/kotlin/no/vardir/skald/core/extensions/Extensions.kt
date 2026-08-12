package no.vardir.skald.core.extensions

enum class ExtensionPlatform { Desktop, Android }

enum class ExtensionCapability {
    Network,
    Authentication,
    SecureStorage,
    ExternalLinks,
    Settings,
    VaultRead,
    VaultWrite,
}

data class ExtensionManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val builtIn: Boolean = true,
    val platforms: Set<ExtensionPlatform>,
    val capabilities: Map<ExtensionPlatform, Set<ExtensionCapability>>,
)

data class ExtensionDescriptor(
    val manifest: ExtensionManifest,
    val markdownComponents: Set<String> = emptySet(),
    val noteProperties: Set<String> = emptySet(),
    val editorInsertions: Set<String> = emptySet(),
    val codeFences: Set<String> = emptySet(),
)

/** Pure validation and lookup shared by every Android renderer surface. */
class ExtensionCatalog(descriptors: List<ExtensionDescriptor>) {
    val extensions: List<ExtensionDescriptor> = descriptors.toList()
    private val components: Map<String, ExtensionDescriptor>
    private val insertions: Map<String, ExtensionDescriptor>
    private val fences: Map<String, ExtensionDescriptor>

    init {
        val ids = mutableSetOf<String>()
        val componentOwners = mutableMapOf<String, ExtensionDescriptor>()
        val propertyOwners = mutableMapOf<String, ExtensionDescriptor>()
        val insertionOwners = mutableMapOf<String, ExtensionDescriptor>()
        val fenceOwners = mutableMapOf<String, ExtensionDescriptor>()
        for (extension in extensions) {
            val manifest = extension.manifest
            require(Regex("""^[a-z][a-z0-9.-]+$""").matches(manifest.id)) { "Invalid extension id: ${manifest.id}" }
            require(Regex("""^\d+\.\d+\.\d+$""").matches(manifest.version)) { "Extension ${manifest.id} needs a semantic version" }
            require(ExtensionPlatform.Android in manifest.platforms) { "Extension ${manifest.id} does not support Android" }
            require(ids.add(manifest.id)) { "Duplicate extension id: ${manifest.id}" }
            for (type in extension.markdownComponents) {
                val normalized = type.lowercase()
                require(normalized.isNotBlank()) { "Markdown component cannot be empty" }
                require(componentOwners.put(normalized, extension) == null) { "Duplicate Markdown component: $normalized" }
            }
            for (property in extension.noteProperties) {
                require(property.isNotBlank()) { "Note property cannot be empty" }
                require(propertyOwners.put(property, extension) == null) { "Duplicate note property: $property" }
            }
            for (insertion in extension.editorInsertions) {
                require(insertion.isNotBlank()) { "Editor insertion cannot be empty" }
                require(insertionOwners.put(insertion, extension) == null) {
                    "Duplicate editor insertion: $insertion"
                }
            }
            for (fence in extension.codeFences) {
                val normalized = fence.lowercase()
                require(normalized.isNotBlank()) { "Code fence cannot be empty" }
                require(fenceOwners.put(normalized, extension) == null) { "Duplicate code fence: $normalized" }
            }
        }
        components = componentOwners
        insertions = insertionOwners
        fences = fenceOwners
    }

    fun component(type: String): ExtensionDescriptor? = components[type.lowercase()]
    fun editorInsertion(id: String): ExtensionDescriptor? = insertions[id]
    fun codeFence(language: String): ExtensionDescriptor? = fences[language.lowercase()]
}

object BuiltInExtensions {
    val GitHub = ExtensionDescriptor(
        manifest = ExtensionManifest(
            id = "dev.skald.github",
            name = "GitHub",
            version = "1.0.0",
            description = "Portable repository properties and GitHub repository cards.",
            platforms = setOf(ExtensionPlatform.Desktop, ExtensionPlatform.Android),
            capabilities = mapOf(
                ExtensionPlatform.Desktop to setOf(
                    ExtensionCapability.Network,
                    ExtensionCapability.Authentication,
                    ExtensionCapability.SecureStorage,
                    ExtensionCapability.ExternalLinks,
                    ExtensionCapability.Settings,
                ),
                ExtensionPlatform.Android to setOf(
                    ExtensionCapability.Network,
                    ExtensionCapability.Authentication,
                    ExtensionCapability.SecureStorage,
                    ExtensionCapability.ExternalLinks,
                    ExtensionCapability.Settings,
                ),
            ),
        ),
        markdownComponents = setOf("github"),
        noteProperties = setOf("github"),
        editorInsertions = setOf("github.repository-card"),
    )

    val Mermaid = ExtensionDescriptor(
        manifest = ExtensionManifest(
            id = "dev.skald.mermaid",
            name = "Mermaid",
            version = "1.0.0",
            description = "Local diagrams rendered from portable Mermaid code fences.",
            platforms = setOf(ExtensionPlatform.Desktop, ExtensionPlatform.Android),
            capabilities = mapOf(
                ExtensionPlatform.Desktop to emptySet(),
                ExtensionPlatform.Android to emptySet(),
            ),
        ),
        editorInsertions = setOf("mermaid.diagram"),
        codeFences = setOf("mermaid"),
    )
}
