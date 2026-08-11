package no.vardir.skald.core

import no.vardir.skald.core.extensions.ExtensionCatalog
import no.vardir.skald.core.extensions.ExtensionDescriptor
import no.vardir.skald.core.extensions.ExtensionManifest
import no.vardir.skald.core.extensions.ExtensionPlatform
import no.vardir.skald.core.extensions.BuiltInExtensions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExtensionCatalogTest {
    private fun extension(id: String, component: String = "demo") = ExtensionDescriptor(
        manifest = ExtensionManifest(
            id = id,
            name = id,
            version = "1.0.0",
            description = "test",
            platforms = setOf(ExtensionPlatform.Android),
            capabilities = mapOf(ExtensionPlatform.Android to emptySet()),
        ),
        markdownComponents = setOf(component),
    )

    @Test
    fun `component lookup is case insensitive`() {
        val catalog = ExtensionCatalog(listOf(BuiltInExtensions.GitHub))
        assertEquals("dev.skald.github", catalog.component("GITHUB")?.manifest?.id)
    }

    @Test
    fun `collisions fail instead of depending on registration order`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ExtensionCatalog(listOf(extension("dev.skald.first"), extension("dev.skald.second")))
        }
        assertEquals(true, error.message?.contains("Duplicate Markdown component"))
    }

    @Test
    fun `manifest identity version and Android support are validated`() {
        assertFailsWith<IllegalArgumentException> { ExtensionCatalog(listOf(extension("Not valid"))) }
        val invalidVersion = extension("dev.skald.valid").copy(
            manifest = extension("dev.skald.valid").manifest.copy(version = "next"),
        )
        assertFailsWith<IllegalArgumentException> { ExtensionCatalog(listOf(invalidVersion)) }
        val desktopOnly = extension("dev.skald.valid").copy(
            manifest = extension("dev.skald.valid").manifest.copy(platforms = setOf(ExtensionPlatform.Desktop)),
        )
        assertFailsWith<IllegalArgumentException> { ExtensionCatalog(listOf(desktopOnly)) }
    }
}
