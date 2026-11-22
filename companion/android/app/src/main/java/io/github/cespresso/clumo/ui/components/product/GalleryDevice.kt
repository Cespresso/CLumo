package io.github.cespresso.clumo.ui.components.product

import io.github.cespresso.clumo.domain.DeviceKind

/** Presentation only. Demo entries never enter the repository or BLE registry. */
data class GalleryDevice(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val status: String,
    val address: String? = null,
    val primary: Boolean = false,
    val display: ProductDisplayState? = null,
    val unidentified: Boolean = false,
    val pairingDemoStartMs: Long? = null,
) {
    fun asset(mode: Int) =
        if (unidentified) {
            "models/unknown-tag-box.glb"
        } else if (display != null) {
            "models/${if (kind == DeviceKind.HEADPHONE_STAND) "headphone-stand" else "clumo"}-live.glb"
        } else if (kind == DeviceKind.HEADPHONE_STAND) {
            "models/headphone-stand-${if (mode == 0) "baked" else "pbr"}.glb"
        } else {
            "models/clumo.glb"
        }
}

internal val demoGalleryDevices = listOf(
    GalleryDevice("demo-clumo", "CLumo", DeviceKind.CLUMO, "デモ"),
    GalleryDevice("demo-stand", "デスクのスタンド", DeviceKind.HEADPHONE_STAND, "デモ", primary = true),
    GalleryDevice("demo-stand-2", "リビングのスタンド", DeviceKind.HEADPHONE_STAND, "デモ"),
)

internal fun selectedGalleryDevice(devices: List<GalleryDevice>, id: String?): GalleryDevice? = devices.firstOrNull { it.id == id } ?: devices.firstOrNull { it.primary } ?: devices.firstOrNull()

internal fun galleryX(index: Int) = index * .77f
internal fun galleryCameraPose(index: Int, count: Int) =
    if (count == 1) {
        ProductCameraPose(targetX = galleryX(index))
    } else {
        ProductCameraPose(yaw = -.08f, pitch = .22f, distance = 3.7f, targetX = galleryX(index))
    }

internal const val SETTINGS_GALLERY_ID = "__gallery_settings__"
internal const val FIND_GALLERY_ID = "__gallery_find__"
internal fun isGalleryAction(id: String?) = id == SETTINGS_GALLERY_ID || id == FIND_GALLERY_ID
