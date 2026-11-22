package io.github.cespresso.clumo.ui.prototype

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import io.github.cespresso.clumo.ui.components.product.FIND_GALLERY_ID
import io.github.cespresso.clumo.ui.components.product.ProductScene
import io.github.cespresso.clumo.ui.components.product.SETTINGS_GALLERY_ID
import io.github.cespresso.clumo.ui.components.product.demoGalleryDevices
import io.github.cespresso.clumo.ui.components.product.galleryX
import io.github.cespresso.clumo.ui.theme.ClumoTheme
import io.github.sceneview.SceneView
import io.github.sceneview.node.ModelNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProductNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun discoveryUpdatesKeepSceneCameraAndExistingModels() {
        val candidate = io.github.cespresso.clumo.ui.components.product.GalleryDevice(
            id = "find:AA",
            name = "Unknown",
            kind = io.github.cespresso.clumo.domain.DeviceKind.CLUMO,
            status = "接続前",
            address = "AA",
            unidentified = true,
        )
        val discoveries = mutableStateOf(emptyList<io.github.cespresso.clumo.ui.components.product.GalleryDevice>())
        val destination = mutableStateOf("overview")
        val selected = mutableStateOf(demoGalleryDevices[1].id)
        compose.setContent {
            ClumoTheme {
                ProductScene(
                    devices = listOf(demoGalleryDevices[1]), discoveryDevices = discoveries.value,
                    selectedId = selected.value, onSelect = { selected.value = it },
                    mode = 0, destination = destination.value, reset = 0, aspect = .45f, landscape = false,
                    modifier = Modifier.fillMaxSize(), onOpenSettings = {}, onFindDevices = {},
                )
            }
        }
        compose.waitForIdle()
        val scene = requireNotNull(findScene(compose.activity.window.decorView))
        val camera = scene.cameraNode
        val engine = scene.engine
        val existing = scene.childNodes.filterIsInstance<ModelNode>().single()
        compose.runOnIdle {
            destination.value = "find"
            discoveries.value = listOf(candidate)
            selected.value = candidate.id
        }
        compose.waitForIdle()
        val unknown = scene.childNodes.filterIsInstance<ModelNode>().first { it !== existing }
        compose.runOnIdle {
            assertSame(scene, findScene(compose.activity.window.decorView))
            assertSame(camera, scene.cameraNode)
            assertSame(engine, scene.engine)
            assertSame(existing, scene.childNodes.first { it.name == existing.name })
            assertTrue(camera.position.x > 1f)
            discoveries.value = listOf(
                candidate.copy(
                    unidentified = false,
                    kind = io.github.cespresso.clumo.domain.DeviceKind.HEADPHONE_STAND,
                    display = io.github.cespresso.clumo.ui.components.product.ProductDisplayState(),
                ),
            )
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(scene, findScene(compose.activity.window.decorView))
            assertSame(camera, scene.cameraNode)
            assertSame(existing, scene.childNodes.first { it.name == existing.name })
            assertTrue(scene.childNodes.filterIsInstance<ModelNode>().none { it === unknown })
            destination.value = "controls"
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(scene, findScene(compose.activity.window.decorView))
            assertEquals(1, scene.childNodes.filterIsInstance<ModelNode>().size)
            destination.value = "overview"
            selected.value = demoGalleryDevices[1].id
            // Keep the scan results: leaving Find must detach them without discarding the cache.
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(scene, findScene(compose.activity.window.decorView))
            assertSame(camera, scene.cameraNode)
            assertSame(existing, scene.childNodes.filterIsInstance<ModelNode>().single())
        }
    }

    @Test
    fun unknownTagBoxIsVisibleAndTappable() {
        var opened: String? = null
        val unknown = io.github.cespresso.clumo.ui.components.product.GalleryDevice(
            id = "unknown",
            name = "CLumo · AA:01",
            kind = io.github.cespresso.clumo.domain.DeviceKind.CLUMO,
            status = "タップして接続",
            address = "AA:01",
            unidentified = true,
        )
        compose.setContent {
            ClumoTheme {
                ProductScene(
                    devices = listOf(unknown), selectedId = unknown.id, onSelect = {},
                    mode = 0, destination = "overview", reset = 0, aspect = .45f, landscape = false,
                    modifier = Modifier.fillMaxSize(), onOpenDevice = { opened = it },
                    onOpenSettings = {}, onFindDevices = {},
                )
            }
        }
        compose.waitForIdle()
        val scene = findScene(compose.activity.window.decorView)!!
        val point = scene.cameraNode.worldToScreenPoint(io.github.sceneview.collision.Vector3(0f, -.25f, 0f))
        compose.onNodeWithTag("product-scene").performTouchInput { click(Offset(point.x, point.y)) }
        compose.runOnIdle {
            assertEquals("AA:01", opened)
            val node = scene.childNodes.filterIsInstance<ModelNode>().single()
            assertEquals(-90f, node.rotation.x, .01f)
            assertEquals(-.5f, node.position.y - node.size.z * node.scale.z / 2f + node.center.z * node.scale.z, .001f)
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let {
            java.io.File(compose.activity.getExternalFilesDir(null), "unknown-tag-box.png").outputStream().use { output -> it.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output) }
        }
    }

    @Test
    fun swipesIncludeSettingsAndFindInTheDeviceRow() {
        val selected = mutableStateOf(demoGalleryDevices[1].id)
        compose.setContent {
            ClumoTheme {
                ProductScene(
                    devices = listOf(demoGalleryDevices[1]), selectedId = selected.value,
                    onSelect = { selected.value = it }, mode = 0, destination = "overview", reset = 0,
                    aspect = .45f, landscape = false, modifier = Modifier.fillMaxSize(),
                    onOpenSettings = {}, onFindDevices = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("product-scene").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(SETTINGS_GALLERY_ID, selected.value) }
        compose.onNodeWithTag("product-scene").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(demoGalleryDevices[1].id, selected.value) }
        compose.onNodeWithTag("product-scene").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(FIND_GALLERY_ID, selected.value) }
    }

    @Test
    fun floorButtonsWorkWithoutRegisteredDevices() {
        val selected = mutableStateOf(FIND_GALLERY_ID)
        var settings = 0
        var finds = 0
        compose.setContent {
            ClumoTheme {
                ProductScene(
                    devices = emptyList(), selectedId = selected.value, onSelect = { selected.value = it }, mode = 0,
                    destination = "overview", reset = 0, aspect = .45f, landscape = false,
                    modifier = Modifier.fillMaxSize(), onOpenSettings = { settings++ }, onFindDevices = { finds++ },
                )
            }
        }
        compose.waitForIdle()
        fun tapAction(name: String) {
            var point = Offset.Zero
            compose.runOnIdle {
                val scene = requireNotNull(findScene(compose.activity.window.decorView))
                val node = scene.childNodes.first { it.name == name }

                @Suppress("DEPRECATION")
                val screen = scene.cameraNode.worldToScreenPoint(io.github.sceneview.collision.Vector3(node.position.x, node.position.y, node.position.z))
                point = Offset(screen.x, screen.y)
            }
            compose.onNodeWithTag("product-scene").performTouchInput { click(point) }
        }
        compose.onNodeWithTag("product-scene").performTouchInput { swipeRight() }
        compose.waitForIdle()
        tapAction("floor-settings")
        compose.onNodeWithTag("product-scene").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        tapAction("floor-find")
        compose.runOnIdle {
            assertEquals(1, settings)
            assertEquals(1, finds)
        }
    }

    @Test
    fun newlyOpenedControlsStartsAtOverviewInsteadOfDefaultSideCamera() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ClumoTheme {
                ProductScene(
                    devices = listOf(demoGalleryDevices[1]), selectedId = demoGalleryDevices[1].id,
                    onSelect = {}, mode = 0, destination = "controls", reset = 0,
                    aspect = .45f, landscape = false, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            val scene = requireNotNull(findScene(compose.activity.window.decorView))
            assertEquals("Opening controls must not jump sideways", 0f, scene.cameraNode.position.x, .001f)
            assertEquals(.028, scene.cameraNode.focalLength, .00001)
        }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        assertDestination("controls")
    }

    @Test
    fun destinationMovesExistingSceneCameraAndBackReturnsToOverview() {
        var exits = 0
        compose.setContent { ClumoTheme { Prototype3DScreen(onBack = { exits++ }, devices = listOf(demoGalleryDevices[1])) } }
        compose.waitForIdle()
        var originalY = 0f
        lateinit var originalScene: SceneView
        compose.runOnIdle {
            originalScene = requireNotNull(findScene(compose.activity.window.decorView))
            originalY = originalScene.cameraNode.position.y
            assertEquals(0f, originalScene.cameraNode.position.x, .001f)
            assertEquals(22f, originalScene.childNodes.filterIsInstance<ModelNode>().single().rotation.y, .01f)
        }
        assertDestination("overview")
        val rootBounds = compose.onNodeWithTag("gallery-root").fetchSemanticsNode().boundsInRoot
        assertEquals(rootBounds, compose.onNodeWithTag("product-scene").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("mode-controls").performClick()
        compose.waitForIdle()
        assertDestination("controls")
        compose.onNodeWithTag("controls-guide").assertExists()
        var beforeScrollY = 0f
        compose.runOnIdle { beforeScrollY = originalScene.cameraNode.position.y }
        compose.onNodeWithTag("guide-scroll").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(beforeScrollY, originalScene.cameraNode.position.y, .001f) }
        compose.runOnIdle {
            assertSame(originalScene, findScene(compose.activity.window.decorView))
            assertTrue(originalScene.cameraNode.position.y < -.2f)
            // Filament returns focal length in metres although the lens setter takes millimetres.
            assertEquals(.085, originalScene.cameraNode.focalLength, .00001)
            assertEquals(0f, originalScene.childNodes.filterIsInstance<ModelNode>().single().rotation.y, .01f)
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertDestination("overview")
        compose.runOnIdle {
            assertSame(originalScene, findScene(compose.activity.window.decorView))
            assertEquals(originalY, originalScene.cameraNode.position.y, .001f)
            assertEquals(22f, originalScene.childNodes.filterIsInstance<ModelNode>().single().rotation.y, .01f)
            assertEquals(.028, originalScene.cameraNode.focalLength, .00001)
            assertEquals(0, exits)
        }
    }

    @Test
    fun gallerySelectsIdentityAndKeepsCameraWhenStatusChanges() {
        val initial = demoGalleryDevices.mapIndexed { index, item ->
            item.copy(address = "test-address-$index", status = "接続済み")
        }
        val devices = mutableStateOf(initial)
        var opened: String? = null
        compose.setContent {
            ClumoTheme { Prototype3DScreen(onBack = {}, devices = devices.value, onOpenDevice = { opened = it }) }
        }
        compose.waitForIdle()
        lateinit var original: SceneView
        var initialCameraX = 0f
        compose.runOnIdle {
            original = requireNotNull(findScene(compose.activity.window.decorView))
            initialCameraX = original.cameraNode.position.x
        }
        compose.onNodeWithTag("device-chooser").performClick()
        compose.onNodeWithTag("device-demo-stand-2").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("gallery-selection").assertTextContains("リビングのスタンド", substring = true)
        compose.onNodeWithTag("open-selected-device").performClick()
        compose.runOnIdle {
            assertEquals("test-address-2", opened)
            assertSame(original, findScene(compose.activity.window.decorView))
            assertEquals(initialCameraX + galleryX(1), original.cameraNode.position.x, .001f)
            devices.value = initial.map { if (it.id == "demo-stand-2") it.copy(name = "名前変更", status = "未接続") else it }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("gallery-selection").assertTextContains("名前変更 · 未接続")
        compose.onNodeWithTag("mode-controls").performClick()
        compose.waitForIdle()
        assertDestination("controls")
        compose.runOnIdle {
            assertSame(original, findScene(compose.activity.window.decorView))
            assertEquals(galleryX(2), original.cameraNode.position.x, .001f)
        }
        compose.onNodeWithTag("mode-overview").performClick()
        compose.waitForIdle()
        compose.runOnIdle { devices.value = initial.filter { it.id != "demo-stand-2" } }
        compose.waitForIdle()
        compose.onNodeWithTag("gallery-selection").assertTextContains("デスクのスタンド", substring = true)
        compose.runOnIdle { devices.value = emptyList() }
        compose.waitForIdle()
        compose.onNodeWithTag("gallery-empty").assertExists()
        compose.onNodeWithTag("product-scene").assertDoesNotExist()
    }

    @Test
    fun demoIsExplicitAndDoesNotOfferRealDeviceOperations() {
        compose.setContent { ClumoTheme { Prototype3DScreen(onBack = {}) } }
        compose.onNodeWithTag("gallery-empty").assertExists()
        compose.onNodeWithTag("gallery-options").performClick()
        compose.onNodeWithTag("gallery-demo").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("gallery-selection").assertTextContains("デモ", substring = true)
        compose.onNodeWithTag("open-selected-device").assertDoesNotExist()
        // Actual mesh hit testing, not just the name chips: CLumo is left of the selected stand.
        var clumoPoint = Offset.Zero
        compose.runOnIdle {
            val scene = requireNotNull(findScene(compose.activity.window.decorView))

            @Suppress("DEPRECATION")
            val point = scene.cameraNode.worldToScreenPoint(io.github.sceneview.collision.Vector3(.2f, -.2f, 0f))
            clumoPoint = Offset(point.x, point.y)
        }
        compose.onNodeWithTag("product-scene").performTouchInput { click(clumoPoint) }
        compose.waitForIdle()
        compose.onNodeWithTag("gallery-selection").assertTextContains("CLumo · デモ")
        compose.onNodeWithTag("mode-controls").assertDoesNotExist()
        compose.onNodeWithTag("gallery-options").performClick()
        compose.onNodeWithTag("gallery-real").performClick()
        compose.onNodeWithTag("gallery-empty").assertExists()
    }

    private fun assertDestination(route: String) {
        compose.onNodeWithTag("product-scene").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, route),
        )
    }

    private fun findScene(view: View): SceneView? {
        if (view is SceneView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findScene(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
