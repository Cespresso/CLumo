package io.github.cespresso.clumo.ui.prototype

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.cespresso.clumo.MainActivity
import io.github.sceneview.SceneView
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.ModelNode
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/** On-device integration check using the phone's registered devices. */
class SharedProductSceneTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun findIconOpensThreeDimensionalDiscovery() {
        compose.waitUntil(10_000) { findScene(compose.activity.window.decorView) != null }
        compose.waitForIdle()
        val original = requireNotNull(findScene(compose.activity.window.decorView))
        val originalCamera = original.cameraNode
        val originalEngine = original.engine
        val originalModels = original.childNodes.filterIsInstance<ModelNode>()
        compose.onNodeWithTag("home-add-device").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(original, findScene(compose.activity.window.decorView))
            assertSame(originalEngine, original.engine)
            assertSame(originalCamera, original.cameraNode)
        }
        compose.onNodeWithTag("find-3d").assertExists()
        compose.onNodeWithTag("find-status").assertExists()
        compose.onNodeWithTag("product-scene").assertExists()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let {
            java.io.File(compose.activity.getExternalFilesDir(null), "find-3d.png").outputStream().use { output -> it.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output) }
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(original, findScene(compose.activity.window.decorView))
            assertSame(originalCamera, original.cameraNode)
            originalModels.forEach { model -> assertSame(model, original.childNodes.first { it.name == model.name }) }
        }
    }

    @Test
    fun settingsAndBackKeepTheGalleryScene() {
        compose.waitUntil(10_000) { findScene(compose.activity.window.decorView) != null }
        compose.waitForIdle()
        val original = requireNotNull(findScene(compose.activity.window.decorView))
        val camera = original.cameraNode
        compose.onNodeWithTag("home-settings").performClick()
        compose.onNodeWithText(compose.activity.getString(io.github.cespresso.clumo.R.string.settings_licenses)).assertExists()
        compose.runOnIdle { assertSame(original, findScene(compose.activity.window.decorView)) }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(original, findScene(compose.activity.window.decorView))
            assertSame(camera, original.cameraNode)
        }
    }

    @Test
    fun operationAndBackKeepTheGallerySceneAndModels() {
        compose.waitUntil(10_000) { findScene(compose.activity.window.decorView) != null }
        compose.waitForIdle()
        lateinit var scene: SceneView
        lateinit var models: List<ModelNode>
        lateinit var camera: CameraNode
        compose.runOnIdle {
            scene = requireNotNull(findScene(compose.activity.window.decorView))
            models = scene.childNodes.filterIsInstance<ModelNode>()
            camera = scene.cameraNode
        }
        var point = Offset.Zero
        compose.runOnIdle {
            @Suppress("DEPRECATION")
            val screen = scene.cameraNode.worldToScreenPoint(io.github.sceneview.collision.Vector3(camera.position.x, -.1f, 0f))
            point = Offset(screen.x, screen.y)
        }
        compose.onNodeWithTag("product-scene").performTouchInput { click(point) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame("Operation navigation must retain the SceneView", scene, findScene(compose.activity.window.decorView))
            assertSame(camera, scene.cameraNode)
            val selectedModel = scene.childNodes.filterIsInstance<ModelNode>().single()
            assertSame(selectedModel, models.first { it == selectedModel })
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(scene, findScene(compose.activity.window.decorView))
            assertSame(camera, scene.cameraNode)
            val returned = scene.childNodes.filterIsInstance<ModelNode>()
            models.forEachIndexed { index, model -> assertSame(model, returned[index]) }
        }
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
