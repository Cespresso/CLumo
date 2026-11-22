package io.github.cespresso.clumo.ui.prototype

import io.github.cespresso.clumo.ui.components.product.ProductCameraPose
import io.github.cespresso.clumo.ui.components.product.ProductOrbit
import io.github.cespresso.clumo.ui.components.product.controlsCameraPose
import io.github.cespresso.clumo.ui.components.product.fullscreenCameraPose
import io.github.cespresso.clumo.ui.components.product.upperControlsCameraPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductOrbitTest {
    @Test
    fun sameFractionOfViewportProducesSameRotation() {
        val phone = ProductOrbit().apply { rotate(100f, 50f, 400, 300) }
        val tablet = ProductOrbit().apply { rotate(400f, 200f, 1600, 1200) }
        assertEquals(phone.yaw, tablet.yaw, .0001f)
        assertEquals(phone.pitch, tablet.pitch, .0001f)
    }

    @Test
    fun pinchChangesOnlyDistanceAndIsReversible() {
        val orbit = ProductOrbit()
        val distance = orbit.distance
        val yaw = orbit.yaw
        val pitch = orbit.pitch
        orbit.zoom(1.5f)
        assertEquals(distance / 1.5f, orbit.distance, .0001f)
        orbit.zoom(1f / 1.5f)
        assertEquals(distance, orbit.distance, .0001f)
        assertEquals(yaw, orbit.yaw, .0001f)
        assertEquals(pitch, orbit.pitch, .0001f)
    }

    @Test
    fun extremesCannotFlipOrEnterTheProduct() {
        val orbit = ProductOrbit()
        orbit.rotate(0f, 100000f, 400, 300)
        assertTrue(orbit.pitch <= 1.35f)
        orbit.zoom(1000f)
        assertEquals(1.15f, orbit.distance, .0001f)
        orbit.zoom(.00001f)
        assertEquals(5f, orbit.distance, .0001f)
        orbit.zoom(Float.NaN)
        assertEquals(5f, orbit.distance, .0001f)
    }

    @Test
    fun lowerCameraTargetsMakerWorldCropInNormalizedCoordinates() {
        val pose = controlsCameraPose(0f, .122f, 0f, 1f / .244f)
        assertEquals(-.078f / .244f, pose.targetY, .0001f)
        assertEquals(0f, pose.yaw, .0001f)
        assertEquals(0f, pose.pitch, .0001f)
        assertEquals(.102f / .244f, pose.distance * 24f / pose.focalLength, .0001f)
    }

    @Test
    fun transitionStartsAtCurrentPoseAndEndsAtDestination() {
        val start = ProductCameraPose()
        val end = controlsCameraPose(0f, .122f, 0f, 1f / .244f)
        assertEquals(start, start.towards(end, 0f))
        val actual = start.towards(end, 1f)
        assertEquals(end.targetY, actual.targetY, .0001f)
        assertEquals(end.distance, actual.distance, .0001f)
        assertEquals(end.yaw, actual.yaw, .0001f)
        assertEquals(end.focalLength, actual.focalLength, .0001f)
    }

    @Test
    fun portraitExplanationFitsAboveBottomOverlay() {
        val crop = controlsCameraPose(0f, .122f, 0f, 1f / .244f)
        val framed = fullscreenCameraPose(crop, .45f, detail = true, landscape = false)
        val cropHeight = crop.distance * 24f / crop.focalLength
        val screenHeight = framed.distance * 24f / framed.focalLength
        fun screenY(y: Float) = .5f - (y - framed.targetY) / screenHeight
        assertTrue(screenY(crop.targetY + cropHeight / 2f) >= .10f)
        assertTrue(screenY(crop.targetY - cropHeight / 2f) <= .60f)
    }

    @Test
    fun landscapeExplanationStaysLeftOfSidePanel() {
        val crop = controlsCameraPose(0f, .122f, 0f, 1f / .244f).copy(targetX = 2.5f)
        val framed = fullscreenCameraPose(crop, 2.2f, detail = true, landscape = true)
        val screenWidth = framed.distance * 24f / framed.focalLength * 2.2f
        val screenX = .5f + (crop.targetX - framed.targetX) / screenWidth
        assertTrue(screenX in 0.2f..0.4f)
    }

    @Test
    fun selectedControlFaceFitsAboveMenuAcrossAspectRatios() {
        val crop = controlsCameraPose(0f, .122f, 0f, 1f / .244f)
        for (aspect in listOf(.3f, .45f, .75f, 2.2f)) {
            val pose = upperControlsCameraPose(crop, aspect)
            val height = pose.distance * 24f / pose.focalLength
            val halfCrop = crop.distance * 24f / crop.focalLength / 2f
            fun screenY(y: Float): Float {
                val dy = y - pose.targetY
                val depth = pose.distance - dy * kotlin.math.sin(pose.pitch)
                return .5f - dy * kotlin.math.cos(pose.pitch) / (depth * 24f / pose.focalLength)
            }
            assertTrue(screenY(crop.targetY + halfCrop) >= .15f)
            assertTrue(screenY(crop.targetY - halfCrop) < .52f)
            assertEquals(crop.targetX, pose.targetX, .0001f)
            assertEquals(crop.yaw, pose.yaw, .0001f)
            val camera = ProductOrbit().apply { this.pose = pose }
            assertTrue("Camera must stay above floor", camera.y > -.502f)
            assertTrue("Show floor from above", pose.pitch > 0f)
            // Contact point on the floor, at the front of the product.
            assertTrue("Keep shadow space before menu", screenY(-.502f) < .50f)
        }
    }
}
