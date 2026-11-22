package io.github.cespresso.clumo.ui.components.product

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class ProductCameraPose(
    val yaw: Float = atan2(-1.55f, 1.76f),
    val pitch: Float = atan2(.66f, sqrt(1.55f * 1.55f + 1.76f * 1.76f)),
    val distance: Float = sqrt(1.55f * 1.55f + .66f * .66f + 1.76f * 1.76f),
    val targetX: Float = 0f,
    val targetY: Float = 0f,
    val targetZ: Float = 0f,
    val focalLength: Float = 28f,
) {
    fun towards(end: ProductCameraPose, fraction: Float): ProductCameraPose {
        val t = fraction.coerceIn(0f, 1f)
        fun mix(a: Float, b: Float) = a + (b - a) * t
        // Take the shortest route around the product, even after multiple manual turns.
        val yawDelta = atan2(sin(end.yaw - yaw), cos(end.yaw - yaw))
        return ProductCameraPose(
            yaw = yaw + yawDelta * t,
            pitch = mix(pitch, end.pitch),
            distance = mix(distance, end.distance),
            targetX = mix(targetX, end.targetX),
            targetY = mix(targetY, end.targetY),
            targetZ = mix(targetZ, end.targetZ),
            focalLength = mix(focalLength, end.focalLength),
        )
    }
}

/** Convert the MakerWorld lower crop (Z -7..95 mm) into the centered, unit-height GLB. */
internal fun controlsCameraPose(centerX: Float, centerY: Float, centerZ: Float, scale: Float): ProductCameraPose =
    ProductCameraPose(
        yaw = 0f,
        pitch = 0f,
        // Filament's lens projection has a 24 mm vertical sensor. Keep 102 mm visible at the front.
        distance = .102f * scale * 85f / 24f,
        targetX = -centerX * scale,
        targetY = (.044f - centerY) * scale,
        targetZ = (.048f - centerZ) * scale,
        focalLength = 85f,
    )

/** A centered product orbit, independent of the point at which a gesture starts. */
internal class ProductOrbit {
    var pose = ProductCameraPose()
    val yaw get() = pose.yaw
    val pitch get() = pose.pitch
    val distance get() = pose.distance

    fun rotate(dx: Float, dy: Float, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        pose = pose.copy(
            yaw = yaw - dx / width * (2 * PI).toFloat(),
            pitch = (pitch + dy / height * PI.toFloat()).coerceIn(-1.35f, 1.35f),
        )
    }

    fun zoom(factor: Float, minimum: Float = 1.15f, maximum: Float = 5f) {
        if (!factor.isFinite() || factor <= 0f) return
        pose = pose.copy(distance = (distance / factor).coerceIn(minimum, maximum))
    }

    val x get() = pose.targetX + distance * cos(pitch) * sin(yaw)
    val y get() = pose.targetY + distance * sin(pitch)
    val z get() = pose.targetZ + distance * cos(pitch) * cos(yaw)
}

/** Fit the product into the area between the top navigation and the overlay card. */
internal fun fullscreenCameraPose(pose: ProductCameraPose, aspect: Float, detail: Boolean, landscape: Boolean, home: Boolean = false): ProductCameraPose {
    val safeAspect = aspect.coerceAtLeast(.3f)
    if (detail) {
        val distance = pose.distance * (if (landscape) 2f else 2.15f)
        val height = distance * 24f / pose.focalLength
        return pose.copy(
            distance = distance,
            targetX = pose.targetX + if (landscape) height * safeAspect * .22f else 0f,
            targetY = pose.targetY + if (landscape) height * .10f else -height * .15f,
        )
    }
    val distance = if (landscape) 2.6f else (1.45f / safeAspect * 28f / 24f).coerceAtLeast(2.6f)
    // The home gallery uses the space between the app bar and the carousel controls.
    val galleryDistance = distance * when {
        !home -> 1f
        landscape -> .93f
        else -> .79f
    }
    val height = galleryDistance * 24f / 28f
    return pose.copy(
        yaw = 0f,
        pitch = .38f,
        distance = galleryDistance,
        targetX = pose.targetX + if (landscape && !home) height * safeAspect * .22f else 0f,
        targetY = if (landscape) {
            if (home) -height * .04f else 0f
        } else {
            height * if (home) -.055f else -.09f
        },
    )
}

/** Center the Matrix between the toolbar and the controls, or beside the landscape panel. */
internal fun matrixControlsCameraPose(slotX: Float, aspect: Float, landscape: Boolean, overview: Boolean = false): ProductCameraPose {
    val visibleWidth = if (landscape) .57f else 1f
    val height = maxOf(
        if (landscape) {
            1.12f
        } else if (overview) {
            1.32f
        } else {
            1.8f
        },
        .88f / (aspect * visibleWidth),
    )
    val pitch = .22f
    val shift = height * if (landscape) {
        -.04f
    } else if (overview) {
        .04f
    } else {
        .16f
    }
    return ProductCameraPose(
        yaw = 0f,
        pitch = pitch,
        focalLength = 40f,
        distance = height * 40f / 24f,
        targetX = slotX + height * aspect * (1f - visibleWidth) / 2f,
        targetY = -.2f - shift * cos(pitch),
        targetZ = shift * sin(pitch),
    )
}

/** Detail composition includes the physical floor, rather than reserving space for a screen overlay. */
internal fun floorSettingsCameraPose(x: Float, aspect: Float): ProductCameraPose =
    ProductCameraPose(
        yaw = -.10f,
        pitch = .82f,
        distance = 4.15f * (0.45f / aspect.coerceAtLeast(.3f)).coerceAtLeast(1f),
        targetX = x - .033f,
        targetY = -.48f,
        targetZ = .64f,
        focalLength = 52f,
    )

/** Reserve the lower half for Compose controls while keeping the whole control face visible. */
internal fun upperControlsCameraPose(crop: ProductCameraPose, aspect: Float): ProductCameraPose {
    val cropHeight = crop.distance * 24f / crop.focalLength
    // Fit the lower control face in the upper third, with a little extra room on narrow phones.
    val height = cropHeight * maxOf(3.05f, 1.25f / aspect.coerceAtLeast(.3f))
    return crop.copy(
        distance = height * crop.focalLength / 24f,
        // Lowering a horizontal camera puts it below the floor. A gentle downward
        // view keeps the camera above ground and reveals the contact shadow.
        pitch = .20f,
        targetY = crop.targetY - height * .18f,
    )
}

internal const val STAND_PREVIEW_HEIGHT = .48f
internal const val STAND_PREVIEW_WIDTH_WIDE = .5f

/** Frame the product in the uncovered part of the full-size scene, using camera-space offsets. */
internal fun appearanceCameraPose(x: Float, aspect: Float, landscape: Boolean, stand: Boolean): ProductCameraPose {
    val visibleWidth = if (stand && landscape) STAND_PREVIEW_WIDTH_WIDE else 1f
    val visibleHeight = if (stand && !landscape) STAND_PREVIEW_HEIGHT else 1f
    val height = if (stand) {
        maxOf(1.72f / visibleHeight, .55f / (aspect * visibleWidth))
    } else {
        maxOf(1.12f, .82f / aspect)
    }
    val pitch = .23f
    val shiftUp = height * (1f - visibleHeight) / 2f
    val verticalCenter = if (stand) {
        height * visibleHeight * .08f
    } else {
        -.2f - height * (if (landscape) 0f else .09f)
    }
    return ProductCameraPose(
        yaw = 0f,
        pitch = pitch,
        focalLength = 40f,
        distance = height * 40f / 24f,
        targetX = x + height * aspect * (1f - visibleWidth) / 2f,
        targetY = verticalCenter - shiftUp * cos(pitch),
        targetZ = shiftUp * sin(pitch),
    )
}
