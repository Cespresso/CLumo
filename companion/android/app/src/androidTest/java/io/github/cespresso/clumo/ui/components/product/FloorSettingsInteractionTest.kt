package io.github.cespresso.clumo.ui.components.product

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import io.github.cespresso.clumo.data.ble.ControlBindings
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceKind
import io.github.cespresso.clumo.ui.device.StandFloorSettings
import io.github.cespresso.clumo.ui.theme.ClumoTheme
import io.github.sceneview.SceneView
import io.github.sceneview.collision.Vector3
import io.github.sceneview.math.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class FloorSettingsInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun floorControlsChooseAndSaveThenAdjustBrightnessInSameScene() {
        val part = mutableStateOf<StandPart?>(StandPart.Knob)
        val brightness = mutableFloatStateOf(65f)
        val ready = mutableStateOf(true)
        val floor = FloorSettingsState()
        var hardware = ControlBindings.DEFAULT.changed(0, 7)
        var writes = 0
        var editorOpened = false
        var recoveryCalls = 0
        compose.setContent {
            ClumoTheme {
                Box(Modifier.fillMaxSize()) {
                    ProductScene(listOf(GalleryDevice("test", "Stand", DeviceKind.HEADPHONE_STAND, "Ready", display = ProductDisplayState(connectionState = ConnectionState.Ready))), "test", {}, 0, "controls", 0, .45f, false, Modifier.fillMaxSize(), selectedPart = part.value, onPartSelect = { part.value = it }, floorSettings = floor)
                    StandFloorSettings(floor, part.value, ready.value, brightness.floatValue, "未接続", { brightness.floatValue = it }, { editorOpened = true }, { part.value = null }, { recoveryCalls++ }, { hardware }, {
                        hardware = it
                        writes++
                        it
                    }, {})
                }
            }
        }
        compose.waitForIdle()
        SystemClock.sleep(900)
        val scene = requireNotNull(findScene(compose.activity.window.decorView))
        val camera = scene.cameraNode
        val node = scene.childNodes.single { it.name == "floor-part-settings" }
        compose.runOnIdle {
            for ((x, y) in listOf(75f to 75f, 955f to 75f, 955f to 1015f, 75f to 1015f)) {
                val world = node.getWorldPosition(FloorSettingsPanel.controlPosition(x, y))
                val point = camera.worldToScreenPoint(Vector3(world.x, world.y, world.z))
                assertTrue("Floor corner $x,$y projects to $point", point.x in 0f..scene.width.toFloat() && point.y in 0f..scene.height.toFloat())
            }
        }
        fun tap(x: Float, y: Float) {
            SystemClock.sleep(500)
            var point = Offset.Zero
            compose.runOnIdle {
                val world = node.getWorldPosition(FloorSettingsPanel.controlPosition(x, y))
                val screen = camera.worldToScreenPoint(Vector3(world.x, world.y, world.z))
                assertTrue("x=${screen.x}", screen.x in 0f..scene.width.toFloat())
                assertTrue("y=${screen.y}", screen.y in 0f..scene.height.toFloat())
                point = Offset(screen.x, screen.y)
            }
            compose.onNodeWithTag("product-scene").performTouchInput { click(point) }
            compose.waitForIdle()
        }
        screenshot("floor-l-knob.png")
        tap(400f, 212f)
        compose.runOnIdle {
            assertTrue(floor.snapshot.choosing)
            assertEquals(2, floor.snapshot.items.size)
        }
        screenshot("floor-l-choices.png")
        tap(400f, 275f)
        compose.runOnIdle {
            assertEquals(1, writes)
            assertEquals(0, hardware.value(5))
            assertEquals(7, hardware.value(0))
            assertFalse(floor.snapshot.choosing)
            part.value = StandPart.Matrix
        }
        compose.waitForIdle()
        tap(495f, 730f)
        compose.runOnIdle { assertEquals(50f, brightness.floatValue, 1f) }
        var dragStart = Offset.Zero
        var dragEnd = Offset.Zero
        compose.runOnIdle {
            fun screenPoint(x: Float): Offset {
                val world = node.getWorldPosition(FloorSettingsPanel.controlPosition(x, 730f))
                val point = camera.worldToScreenPoint(Vector3(world.x, world.y, world.z))
                return Offset(point.x, point.y)
            }
            dragStart = screenPoint(495f)
            dragEnd = screenPoint(690f)
        }
        compose.onNodeWithTag("product-scene").performTouchInput { swipe(dragStart, dragEnd, 300) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(80f, brightness.floatValue, 1f) }
        screenshot("floor-l-matrix.png")
        tap(400f, 280f)
        compose.runOnIdle {
            assertTrue(editorOpened)
            ready.value = false
        }
        compose.waitForIdle()
        tap(700f, 730f)
        compose.runOnIdle {
            assertEquals(80f, brightness.floatValue, 1f)
            assertSame(camera, scene.cameraNode)
            assertSame(scene, findScene(compose.activity.window.decorView))
        }
        tap(400f, 180f)
        compose.runOnIdle { assertEquals(1, recoveryCalls) }
        tap(200f, 970f)
        compose.runOnIdle { assertEquals(null, part.value) }
    }

    private fun screenshot(name: String) {
        SystemClock.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun findScene(view: View): SceneView? =
        when (view) {
            is SceneView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findScene(view.getChildAt(it)) }
            else -> null
        }
}
