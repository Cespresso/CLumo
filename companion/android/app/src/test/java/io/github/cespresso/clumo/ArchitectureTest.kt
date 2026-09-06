package io.github.cespresso.clumo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Layering rules the compiler cannot state. Both encode drift that actually happened here, so
 * a failure means the shape is sliding back, not that the rule is inconvenient.
 */
class ArchitectureTest {

    private val sourceRoot = File(System.getProperty("user.dir"), "src/main/java/io/github/cespresso/clumo")

    private val sources: List<File> =
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun File.isUnder(pkg: String) =
        relativeTo(sourceRoot).invariantSeparatorsPath.startsWith("$pkg/")

    @Test
    fun sourceTreeWasFound() {
        // A path that stops matching would make every rule below pass by vacuum.
        assert(sources.size > 30) { "Expected the source tree, found ${sources.size} files" }
    }

    /**
     * The hub service owns a lifecycle, not the object graph. A screen that reaches it can reach
     * every repository behind it, which is how the UI ended up wired straight to the data layer.
     */
    @Test
    fun noScreenDependsOnTheHubService() {
        val offenders = sources
            .filter { it.isUnder("ui") }
            .filter { it.readText().contains("service.DeviceHubService") }
        assertEquals(
            "These screens reach the hub service instead of taking what they use",
            emptyList<String>(),
            offenders.map { it.name }.sorted(),
        )
    }

    /**
     * The widgets draw with Canvas and Glance, the app draws with Compose. What they share is
     * device rules and design tokens, and both belong under the layers below. Reaching into the
     * screen package for them made the widget depend on how a screen happens to be built.
     * MainActivity is the composition root, so assembling screens is its job.
     */
    @Test
    fun onlyTheUiLayerDependsOnTheUiLayer() {
        val offenders = sources
            .filterNot { it.isUnder("ui") || it.name == "MainActivity.kt" }
            .filter { it.readText().contains("import io.github.cespresso.clumo.ui.") }
        assertEquals(
            "These files reach into the screen package for something that belongs below it",
            emptyList<String>(),
            offenders.map { it.name }.sorted(),
        )
    }

    /**
     * One screen reaching into another makes the two impossible to read apart: the borrowed piece
     * keeps the layout of wherever it was first needed, and it moves when that screen moves. What
     * two screens share is either a component or a rule, so it belongs in [SHARED] or below the
     * ui layer entirely.
     */
    @Test
    fun noScreenReachesIntoAnotherScreen() {
        val offenders = sources
            .filter { it.isUnder("ui") }
            .flatMap { file ->
                val home = file.relativeTo(sourceRoot).invariantSeparatorsPath.split("/")[1]
                IMPORT.findAll(file.readText())
                    .map { it.groupValues[1] }
                    .filter { it != home && it !in SHARED }
                    .map { "${file.name} -> ui.$it" }
                    .toList()
            }
        assertEquals(
            "These screens borrow from a sibling screen",
            emptyList<String>(),
            offenders.sorted(),
        )
    }

    private companion object {
        /** Packages every screen may draw from: the shared components and the theme. */
        val SHARED = setOf("components", "theme")
        val IMPORT = Regex("""import io\.github\.cespresso\.clumo\.ui\.(\w+)\.""")
    }
}
