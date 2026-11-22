package io.github.cespresso.clumo.ui.prototype

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
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

class ProductGestureTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun explanationAllowsDragAndTwoFingerPinch() {
        compose.setContent {
            ClumoTheme { Prototype3DScreen(onBack = {}, devices = listOf(demoGalleryDevices[1])) }
        }
        compose.onNodeWithTag("mode-controls").performClick()
        compose.waitForIdle()
        lateinit var scene: SceneView
        var initialX = 0f
        compose.runOnIdle {
            scene = requireNotNull(findScene(compose.activity.window.decorView))
            initialX = scene.cameraNode.position.x
        }
        compose.onNodeWithTag("product-scene").performTouchInput {
            swipe(Offset(width * .25f, height * .5f), Offset(width * .65f, height * .5f), 500)
        }
        compose.runOnIdle {
            assertTrue(kotlin.math.abs(scene.cameraNode.position.x - initialX) > .1f)
        }
        fun distance(): Float {
            val p = scene.cameraNode.position
            return kotlin.math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z)
        }
        var beforeZoom = 0f
        compose.runOnIdle { beforeZoom = distance() }
        compose.onNodeWithTag("product-scene").performTouchInput {
            pinch(
                Offset(width * .4f, height * .5f),
                Offset(width * .6f, height * .5f),
                Offset(width * .2f, height * .5f),
                Offset(width * .8f, height * .5f),
                durationMillis = 500,
            )
        }
        compose.runOnIdle { assertTrue(distance() < beforeZoom * .8f) }
    }

    @Test
    fun overviewSwipesSelectDevicesWithoutRotatingAndStopAtEnds() {
        compose.setContent { ClumoTheme { Prototype3DScreen(onBack = {}, devices = demoGalleryDevices) } }
        compose.waitForIdle()
        lateinit var scene: SceneView
        var initialX = 0f
        var initialY = 0f
        var initialZ = 0f
        compose.runOnIdle {
            scene = requireNotNull(findScene(compose.activity.window.decorView))
            initialX = scene.cameraNode.position.x
            initialY = scene.cameraNode.position.y
            initialZ = scene.cameraNode.position.z
            assertEquals(galleryX(1), initialX, .001f)
            scene.childNodes.filterIsInstance<ModelNode>().drop(1).forEach { assertEquals(22f, it.rotation.y, .01f) }
        }
        fun swipeToNext() {
            compose.onNodeWithTag("product-scene").performTouchInput {
                swipe(Offset(width * .75f, height * .45f), Offset(width * .25f, height * .45f), 350)
            }
            compose.waitForIdle()
        }
        swipeToNext()
        compose.onNodeWithTag("gallery-selection").assertTextContains("リビングのスタンド", substring = true)
        compose.runOnIdle {
            assertSame(scene, findScene(compose.activity.window.decorView))
            assertEquals(initialX + galleryX(1), scene.cameraNode.position.x, .001f)
            assertEquals(initialY, scene.cameraNode.position.y, .001f)
            assertEquals(initialZ, scene.cameraNode.position.z, .001f)
        }
        swipeToNext()
        compose.onNodeWithTag("gallery-selection").assertTextContains("リビングのスタンド", substring = true)
        compose.onNodeWithTag("product-scene").performTouchInput {
            swipe(Offset(width * .25f, height * .45f), Offset(width * .75f, height * .45f), 350)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("gallery-selection").assertTextContains("デスクのスタンド", substring = true)
        compose.onNodeWithTag("product-scene").performTouchInput {
            swipe(Offset(width * .5f, height * .35f), Offset(width * .5f, height * .55f), 350)
        }
        compose.onNodeWithTag("product-scene").performTouchInput {
            pinch(
                Offset(width * .4f, height * .45f),
                Offset(width * .6f, height * .45f),
                Offset(width * .2f, height * .45f),
                Offset(width * .8f, height * .45f),
                durationMillis = 500,
            )
        }
        compose.runOnIdle {
            assertEquals(initialX, scene.cameraNode.position.x, .001f)
            assertEquals(initialY, scene.cameraNode.position.y, .001f)
            assertEquals(initialZ, scene.cameraNode.position.z, .001f)
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
