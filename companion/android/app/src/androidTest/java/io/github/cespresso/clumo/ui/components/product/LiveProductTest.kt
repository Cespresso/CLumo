package io.github.cespresso.clumo.ui.components.product

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.DeviceKind
import io.github.cespresso.clumo.domain.RgbColor
import io.github.sceneview.SceneView
import io.github.sceneview.node.ModelNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class LiveProductTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun liveUpdatesKeepModelsAndMaterialsAndNeverShareDeviceMaterials() {
        val display = ProductDisplayState(
            connectionState = ConnectionState.Ready,
            bits = 1L or (1L shl 8) or (1L shl 9) or Long.MIN_VALUE,
            appearance = DeviceAppearance.DEFAULT.copy(ledColor = RgbColor.of(0xFF0000)),
        )
        val devices = mutableStateOf(
            listOf(
                GalleryDevice("first", "first", DeviceKind.HEADPHONE_STAND, "ready", display = display),
                GalleryDevice("second", "second", DeviceKind.HEADPHONE_STAND, "ready", display = display.copy(appearance = display.appearance.copy(ledColor = RgbColor.of(0x0000FF)))),
            ),
        )
        compose.setContent { ProductScene(devices.value, "first", {}, 0, "overview", 0, .45f, false, Modifier.fillMaxSize()) }
        compose.waitForIdle()
        lateinit var scene: SceneView
        lateinit var models: List<ModelNode>
        var cameraX = 0f
        var firstLedMaterial = 0L
        compose.runOnIdle {
            scene = requireNotNull(findScene(compose.activity.window.decorView))
            assertTrue(scene.width > 0 && scene.height > 0)
            models = scene.childNodes.filterIsInstance<ModelNode>()
            cameraX = scene.cameraNode.position.x
            fun material(model: ModelNode, role: String) = model.renderableNodes.single { it.name == role }.materialInstances.single().nativeObject
            firstLedMaterial = material(models[0], "led_matrix")
            assertNotEquals(firstLedMaterial, material(models[1], "led_matrix"))
            assertNotEquals(material(models[0], "enclosure"), material(models[1], "enclosure"))
            devices.value = devices.value.mapIndexed { index, device ->
                if (index == 0) device.copy(display = display.copy(bits = 0L, appearance = display.appearance.copy(enclosureColor = RgbColor.of(0x2244CC)))) else device
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(scene, findScene(compose.activity.window.decorView))
            val updated = scene.childNodes.filterIsInstance<ModelNode>()
            models.indices.forEach { assertSame(models[it], updated[it]) }
            assertEquals(firstLedMaterial, updated[0].renderableNodes.single { it.name == "led_matrix" }.materialInstances.single().nativeObject)
            assertEquals(cameraX, scene.cameraNode.position.x, .0001f)
        }
        saveScreenshot("live-two-devices-updated.png")
    }

    @Test
    fun asymmetricLedPatternHasAVisualReferenceForBothKinds() {
        val kind = mutableStateOf(DeviceKind.HEADPHONE_STAND)
        val display = ProductDisplayState(
            connectionState = ConnectionState.Ready,
            bits = 1L or (1L shl 8) or (1L shl 9) or Long.MIN_VALUE,
            appearance = DeviceAppearance.DEFAULT.copy(ledColor = RgbColor.of(0xFF0000)),
        )
        compose.setContent {
            androidx.compose.runtime.key(kind.value) {
                ProductScene(listOf(GalleryDevice("device", "device", kind.value, "ready", display = display)), "device", {}, 0, "controls", 0, .45f, false, Modifier.fillMaxSize())
            }
        }
        compose.waitForIdle()
        saveScreenshot("live-stand-asymmetric.png")
        compose.runOnIdle { kind.value = DeviceKind.CLUMO }
        compose.waitForIdle()
        compose.runOnIdle {
            val model = requireNotNull(findScene(compose.activity.window.decorView)).childNodes.filterIsInstance<ModelNode>().single()
            assertEquals(1, model.renderableNodes.count { it.name == "led_matrix" })
        }
        saveScreenshot("live-clumo-asymmetric.png")
    }

    private fun saveScreenshot(name: String) {
        // Filament submits frames outside the Compose test clock.
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
