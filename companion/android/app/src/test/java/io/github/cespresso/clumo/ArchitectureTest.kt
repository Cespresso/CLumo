package io.github.cespresso.clumo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Layering rules the compiler cannot state. They encode drift that actually happened here, so
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
}
