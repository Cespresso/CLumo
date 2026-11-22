package io.github.cespresso.clumo.ui.components.product

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.google.android.filament.ColorGrading
import com.google.android.filament.LightManager
import com.google.android.filament.ToneMapper
import dev.romainguy.kotlin.math.Quaternion
import io.github.cespresso.clumo.domain.DeviceKind
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.PlaneNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNode

private val appearanceControlRoles = setOf("stand_knob", "stand_left", "stand_right", "front_holder", "rear_holder", "stand_clip", "power_led", "volume_1", "volume_2", "volume_3")

@Composable
internal fun ProductScene(
    devices: List<GalleryDevice>,
    selectedId: String,
    onSelect: (String) -> Unit,
    mode: Int,
    destination: String,
    reset: Int,
    aspect: Float,
    landscape: Boolean,
    modifier: Modifier = Modifier,
    onOpenDevice: ((String) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onFindDevices: (() -> Unit)? = null,
    discoveryDevices: List<GalleryDevice> = emptyList(),
    interactionEnabled: Boolean = true,
    homeFraming: Boolean = false,
    floorActionsEnabled: Boolean = true,
    matrixPage: MatrixPage = MatrixPage.Overview,
    onMatrixSelect: (MatrixPage) -> Unit = {},
    selectedPart: StandPart? = null,
    onPartSelect: ((StandPart?) -> Unit)? = null,
    appearanceRoles: List<String> = emptyList(),
    appearanceLabel: String = "",
    onAppearanceRole: (String) -> Unit = {},
    partInputEnabled: Boolean = true,
    floorSettings: FloorSettingsState? = null,
) {
    val engine = rememberEngine()
    val loader = rememberModelLoader(engine)
    val materials = rememberMaterialLoader(engine)
    val modelCache = remember(engine, loader, materials) { ProductModels(engine, loader, materials) }
    DisposableEffect(modelCache) { onDispose { modelCache.destroy() } }
    val allDevices = devices + discoveryDevices
    val modelKeys = allDevices.map { it.id to it.asset(mode) }
    val result = remember(modelCache, modelKeys) { runCatching { modelCache.nodes(allDevices, mode) } }
    val models = result.getOrNull()
    if (models == null) {
        Text("3Dモデルを読み込めませんでした。画面を開き直してください。", modifier = modifier)
        return
    }
    SideEffect { modelCache.nodes(allDevices, mode) }
    val ids = allDevices.map { it.id }
    val halo = remember(engine, materials, ids) { FloorSelectionHalo(engine, materials, allDevices) }
    DisposableEffect(halo) { onDispose { halo.destroy() } }
    fun localIndex(index: Int) = if (index < devices.size) index else index + 1
    val floor = rememberNode {
        PlaneNode(
            engine = engine,
            size = Size(200f, 0f, 200f),
            materialInstance = materials.createColorInstance(Color(0xFFE0D7C9), roughness = 1f, reflectance = 0f),
            builderApply = {
                receiveShadows(true)
                castShadows(false)
            },
        ).apply {
            position = Position(0f, -.502f, 0f)
            isTouchable = false
        }
    }
    // Blender reference uses Standard (linear -> sRGB), not the default filmic curve.
    val studioGrading = remember(engine) {
        ColorGrading.Builder().toneMapper(ToneMapper.Linear()).build(engine)
    }
    DisposableEffect(studioGrading) { onDispose { engine.destroyColorGrading(studioGrading) } }
    val sunlight = rememberNode {
        LightNode(engine, LightManager.Type.DIRECTIONAL) {
            color(1f, 1f, 1f)
            intensity(65_000f)
            direction(.35f, -1.2f, -.65f)
            castShadows(true)
            shadowOptions(
                LightManager.ShadowOptions().apply {
                    mapSize = 2048
                },
            )
        }
    }
    val hasActions = floorActionsEnabled && onOpenSettings != null && onFindDevices != null
    val appearanceEdit = destination == "appearance"
    val appearanceTap by rememberUpdatedState(onAppearanceRole)
    var anchor by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val guiding = destination == "guide" || destination == "guide-stand"
    val standGuide = destination == "guide-stand"
    val finding = destination == "find" || guiding
    val selectedIndex = when {
        finding -> allDevices.indexOfFirst { it.id == selectedId }.takeIf { it >= devices.size } ?: devices.size
        hasActions && selectedId == SETTINGS_GALLERY_ID -> -1
        hasActions && selectedId == FIND_GALLERY_ID -> devices.size
        destination == "controls" || appearanceEdit -> devices.indexOfFirst { it.id == selectedId }
        else -> allDevices.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    }
    val model = models.getOrNull(selectedIndex)
    val guideKnob = remember(standGuide, model) {
        if (standGuide) model?.renderableNodes?.firstOrNull { it.name == "stand_knob" } else null
    }
    val restingPosition = remember(guideKnob) { guideKnob?.position }
    DisposableEffect(guideKnob) {
        onDispose { if (guideKnob != null && restingPosition != null) guideKnob.position = restingPosition }
    }
    val demoStart = allDevices.getOrNull(selectedIndex)?.pairingDemoStartMs
    val holdProgress = remember(guideKnob) { mutableFloatStateOf(0f) }
    var knobFace by remember(guideKnob) { mutableStateOf<Rect?>(null) }
    SideEffect { modelCache.select(if (destination == "controls") model else null, selectedPart) }
    var appearanceEdges by remember(model, destination, aspect, landscape) { mutableStateOf<Pair<Float, Float>?>(null) }
    val matrixSelect by rememberUpdatedState(onMatrixSelect)
    val matrixOverview = matrixPage == MatrixPage.Overview
    val selectPart by rememberUpdatedState(onPartSelect)
    val floorPanel = if (floorSettings?.floorUi == true && destination == "controls") remember(engine, materials, loader) { FloorSettingsPanel(engine, materials, loader) } else null
    DisposableEffect(floorPanel) { onDispose { floorPanel?.destroy() } }
    val spatialControls = floorSettings != null && destination == "controls" && allDevices.getOrNull(selectedIndex)?.kind == DeviceKind.HEADPHONE_STAND
    val sideDetail = spatialControls && selectedPart != null && floorSettings?.floorUi == false
    val floorDetail = spatialControls && selectedPart != null && floorSettings?.floorUi != false
    val floorDescription = floorSettings?.snapshot
    val floorChoosing = floorDetail && floorSettings?.destination is DeviceDestination.Assignment
    val density = LocalDensity.current.density
    var viewportWidthDp by remember { mutableFloatStateOf(380f) }
    var assignmentScroll by remember(selectedId, floorChoosing) { mutableFloatStateOf(0f) }
    var scrollVelocity by remember(selectedId, floorChoosing) { mutableFloatStateOf(0f) }
    var scrollDragging by remember { mutableStateOf(false) }
    var scrollFrame by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val scrollLimit = AssignmentLayout.maxScroll(floorDescription?.items?.size ?: 0, viewportWidthDp, aspect)
    val assignmentClosing = floorSettings?.assignmentCloseToken != null
    val floorCloseToken = floorSettings?.closeToken
    val selectedDisplay = allDevices.getOrNull(selectedIndex)?.display ?: ProductDisplayState()
    val ribbonColor = selectedDisplay.regionColors()[selectedPart?.nodeName]
        ?: selectedDisplay.appearance.panelColor.value
    SideEffect {
        floorPanel?.setRibbonColor(ribbonColor)
        floorPanel?.update(floorSettings, galleryX(localIndex(selectedIndex)), floorDetail, floorDescription, floorCloseToken)
    }
    val visibleModels = when (destination) {
        "controls", "appearance" -> listOfNotNull(model)
        "guide", "guide-stand" -> listOfNotNull(model)
        "find" -> models.drop(devices.size)
        else -> models.take(devices.size)
    }
    SideEffect { models.forEach { it.isTouchable = it in visibleModels } }
    val actions = if (hasActions) remember(engine, materials) { FloorActions(engine, materials) } else null
    DisposableEffect(actions) { onDispose { actions?.destroy() } }
    SideEffect { actions?.update(devices.size, destination == "overview") }
    val labels = if (onOpenDevice != null || hasActions) remember(engine, materials, ids) { FloorDeviceLabels(engine, materials, allDevices) } else null
    DisposableEffect(labels) { onDispose { labels?.destroy() } }
    SideEffect {
        labels?.update(allDevices, destination != "controls")
        allDevices.indices.forEach { index ->
            val active = if (finding) index >= devices.size else index < devices.size
            labels?.nodes?.get(index)?.apply {
                position = Position(galleryX(localIndex(index)), -.5006f, .60f)
                isVisible = active && destination != "controls" && !appearanceEdit && !finding
            }
        }
    }
    val openDevice by rememberUpdatedState(onOpenDevice)
    val openSettings by rememberUpdatedState(onOpenSettings)
    val findDevices by rememberUpdatedState(onFindDevices)
    var sceneView by remember { mutableStateOf<io.github.sceneview.SceneView?>(null) }
    val select by rememberUpdatedState(onSelect)
    // A newly mounted operation screen must start at the same framing as its gallery.
    // Its single model is recentered to slot zero, so use this scene's selected slot.
    // Initialize the lens as well as the position before the first rendered frame.
    val orbit = remember(engine) {
        ProductOrbit().apply {
            pose = fullscreenCameraPose(galleryCameraPose(selectedIndex, devices.size), aspect, detail = false, landscape = landscape, home = homeFraming)
        }
    }
    val camera = rememberCameraNode(engine) {
        position = Position(orbit.x, orbit.y, orbit.z)
        lookAt(Position(orbit.pose.targetX, orbit.pose.targetY, orbit.pose.targetZ))
        focalLength = orbit.pose.focalLength.toDouble()
    }
    var assignmentStartPose by remember { mutableStateOf<ProductCameraPose?>(null) }
    var wasChoosing by remember { mutableStateOf(false) }
    var measuredBodyPose by remember { mutableStateOf<ProductCameraPose?>(null) }
    var measuredBodyViewport by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var moving by remember { mutableStateOf(false) }
    var modelYaw by remember { mutableFloatStateOf(22f) }
    val matrixHeight by animateFloatAsState(
        targetValue = if (homeFraming && destination == "overview") .35f else .6f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "Matrix display height",
    )
    fun updateCamera() {
        camera.position = Position(orbit.x, orbit.y, orbit.z)
        camera.lookAt(Position(orbit.pose.targetX, orbit.pose.targetY, orbit.pose.targetZ))
        camera.focalLength = orbit.pose.focalLength.toDouble()
    }
    SideEffect {
        models.forEachIndexed { index, node ->
            node.placeGalleryModel(localIndex(index), allDevices[index], modelYaw, matrixHeight)
            halo.place(index, galleryX(localIndex(index)), modelYaw, node.halfExtent.x * node.scale.x, node.halfExtent.z * node.scale.z)
        }
        halo.updateColors(allDevices)
    }
    LaunchedEffect(destination, matrixOverview, reset, selectedId, aspect, modelKeys, selectedIndex, devices.size, homeFraming, spatialControls, floorDetail, sideDetail, floorChoosing, viewportWidthDp) {
        appearanceEdges = null
        val start = orbit.pose
        val startModelYaw = modelYaw
        val endModelYaw = if (destination == "controls" || standGuide) 0f else 22f
        fun updateModels(yaw: Float) {
            modelYaw = yaw
            models.forEachIndexed { index, node -> node.placeGalleryModel(localIndex(index), allDevices[index], yaw, matrixHeight) }
        }
        val standControls = destination == "controls" && allDevices.getOrNull(selectedIndex)?.kind == DeviceKind.HEADPHONE_STAND
        val end = if (standControls) {
            requireNotNull(model).let { controlsCameraPose(it.center.x, it.center.y, it.center.z, it.scale.x) }.let {
                it.copy(targetX = it.targetX + galleryX(localIndex(selectedIndex)), targetZ = it.targetZ)
            }
        } else {
            if (finding) galleryCameraPose(localIndex(selectedIndex), discoveryDevices.size) else galleryCameraPose(selectedIndex, devices.size)
        }
        assignmentScroll = assignmentScroll.coerceIn(0f, scrollLimit)
        scrollVelocity = 0f
        val framedEnd = if (standGuide && model != null) {
            val crop = controlsCameraPose(model.center.x, model.center.y, model.center.z, model.scale.x)
            val base = upperControlsCameraPose(crop, aspect)
            val viewHeight = base.distance * 24f / base.focalLength
            base.copy(
                targetX = crop.targetX + galleryX(localIndex(selectedIndex)) +
                    if (landscape) viewHeight * aspect * .24f else 0f,
                targetY = if (landscape) base.targetY else crop.targetY + viewHeight * .09f,
                distance = if (landscape) base.distance else base.distance * .90f,
            )
        } else if (finding) {
            val height = maxOf(1.7f, .88f / aspect)
            ProductCameraPose(
                yaw = 0f,
                pitch = .48f,
                focalLength = 36f,
                distance = height * 1.5f,
                targetX = galleryX(localIndex(selectedIndex)) + if (landscape) height * aspect * .24f else 0f,
                targetY = -.32f - if (landscape) 0f else height * .12f,
            )
        } else if (appearanceEdit) {
            appearanceCameraPose(
                galleryX(selectedIndex),
                aspect,
                landscape,
                stand = allDevices.getOrNull(selectedIndex)?.kind == DeviceKind.HEADPHONE_STAND,
            )
        } else if (destination == "controls" && !standControls && model != null) {
            matrixControlsCameraPose(galleryX(localIndex(selectedIndex)), aspect, landscape, matrixOverview)
        } else if (floorChoosing) {
            AssignmentLayout.camera(galleryX(localIndex(selectedIndex)), viewportWidthDp, aspect, assignmentScroll, floorSettings?.destination?.bindingIndex)
        } else if (floorDetail) {
            floorSettingsCameraPose(galleryX(localIndex(selectedIndex)), aspect)
        } else if (spatialControls) {
            // Keep the familiar lower-front close-up until a part is selected.
            // The vacant sheet area now belongs to the product, so frame it lower.
            val closeUp = fullscreenCameraPose(end, aspect, detail = true, landscape = landscape)
            val height = closeUp.distance * 24f / closeUp.focalLength
            if (sideDetail) {
                upperControlsCameraPose(end, aspect)
            } else {
                closeUp.copy(targetX = end.targetX, targetY = end.targetY + height * .08f)
            }
        } else {
            fullscreenCameraPose(end, aspect, standControls, landscape, home = homeFraming)
        }
        if (floorChoosing) {
            if (!wasChoosing) assignmentStartPose = start
            wasChoosing = true
            moving = true
            // The renderer's reversible timeline owns both ribbon and camera.
            return@LaunchedEffect
        }
        if (wasChoosing && floorDetail) {
            wasChoosing = false
            orbit.pose = assignmentStartPose ?: framedEnd
            updateModels(endModelYaw)
            updateCamera()
            moving = false
            return@LaunchedEffect
        }
        wasChoosing = false
        moving = true
        try {
            animate(0f, 1f, animationSpec = tween(700, easing = FastOutSlowInEasing)) { value, _ ->
                orbit.pose = start.towards(framedEnd, value)
                updateModels(startModelYaw + (endModelYaw - startModelYaw) * value)
                updateCamera()
            }
            orbit.pose = framedEnd
            updateModels(endModelYaw)
            updateCamera()
        } finally {
            moving = false
        }
    }
    Box(modifier = modifier.onSizeChanged { viewportWidthDp = it.width / density }.clipToBounds().testTag("product-scene").semantics { stateDescription = if (moving) "moving" else destination }) {
        Scene(
            modifier = Modifier.matchParentSize(),
            engine = engine,
            modelLoader = loader,
            childNodes = listOf(floor) + (
                if (appearanceEdit || guiding) {
                    emptyList()
                } else if (destination == "overview") {
                    halo.nodes.take(devices.size)
                } else {
                    halo.nodes
                }
                ) + actions?.nodes.orEmpty() + labels?.nodes.orEmpty() + visibleModels + if (floorDetail) floorPanel?.nodes.orEmpty() else emptyList(),
            mainLightNode = sunlight,
            cameraNode = camera,
            cameraManipulator = null,
            onFrame = {
                halo.onFrame(it, if (!finding && isGalleryAction(selectedId)) -1 else selectedIndex, destination == "controls")
                if (appearanceEdit && appearanceRoles.isNotEmpty() && !moving && model != null) {
                    if (appearanceEdges == null && (camera.viewport?.width ?: 0) > 0 && orbit.pose.yaw == 0f) {
                        var left = Float.POSITIVE_INFINITY
                        var right = Float.NEGATIVE_INFINITY
                        for (sx in listOf(-1f, 1f)) {
                            for (sy in listOf(-1f, 1f)) {
                                for (sz in listOf(-1f, 1f)) {
                                    val local = model.center + Position(model.halfExtent.x * sx, model.halfExtent.y * sy, model.halfExtent.z * sz)
                                    val w = model.getWorldPosition(local)
                                    val x = camera.worldToScreenPoint(io.github.sceneview.collision.Vector3(w.x, w.y, w.z)).x
                                    left = minOf(left, x)
                                    right = maxOf(right, x)
                                }
                            }
                        }
                        if (left.isFinite() && right.isFinite()) appearanceEdges = left to right
                    }
                    val part = model?.renderableNodes?.firstOrNull { n -> n.name == appearanceRoles.firstOrNull() }
                    part?.let { n ->
                        val c = n.axisAlignedBoundingBox.center
                        val w = n.getWorldPosition(Position(c[0], c[1], c[2]))
                        val point = camera.worldToScreenPoint(io.github.sceneview.collision.Vector3(w.x, w.y, w.z))
                        val next = androidx.compose.ui.geometry.Offset(point.x, point.y)
                        if (next.x.isFinite() && next.y.isFinite() && (anchor == null || (next - anchor!!).getDistance() > 1f)) anchor = next
                    }
                }
                if (demoStart != null && restingPosition != null && !moving) {
                    guideKnob?.let { knob ->
                        val elapsed = (android.os.SystemClock.uptimeMillis() - demoStart) % 5400L
                        holdProgress.floatValue = (elapsed / 3000f).coerceAtMost(1f)
                        val press = if (elapsed < 3000L) {
                            (elapsed / 180f).coerceAtMost(1f)
                        } else {
                            (1f - (elapsed - 3000L) / 200f).coerceAtLeast(0f)
                        }
                        val depth = knob.axisAlignedBoundingBox.halfExtent[2] * .45f
                        knob.position = restingPosition - Position(0f, 0f, depth * press)
                        val bounds = knob.axisAlignedBoundingBox
                        val c = bounds.center
                        val half = bounds.halfExtent

                        // The visible cap is the positive-Z end of the cylinder.
                        fun rim(x: Float, y: Float): Offset {
                            val w = knob.getWorldPosition(Position(c[0] + x * half[0], c[1] + y * half[1], c[2] + half[2]))
                            val p = camera.worldToScreenPoint(io.github.sceneview.collision.Vector3(w.x, w.y, w.z))
                            return Offset(p.x, p.y)
                        }
                        val face = Rect(rim(-1f, 0f).x, rim(0f, 1f).y, rim(1f, 0f).x, rim(0f, -1f).y)
                        knobFace = face.takeIf { it.isFinite && !it.isEmpty }
                    }
                }
                floorPanel?.onFrame(it)
                if (floorChoosing) {
                    val fraction = AssignmentMotion.cameraFraction(floorPanel?.assignmentProgress ?: 0f)
                    val target = AssignmentLayout.camera(galleryX(localIndex(selectedIndex)), viewportWidthDp, aspect, assignmentScroll, floorSettings?.destination?.bindingIndex)
                    orbit.pose = (assignmentStartPose ?: floorSettingsCameraPose(galleryX(localIndex(selectedIndex)), aspect))
                        .towards(target, FastOutSlowInEasing.transform(fraction))
                    moving = fraction < 1f || assignmentClosing
                    updateCamera()
                }
                if (floorSettings?.floorUi == false) {
                    floorSettings.cameraSettled = sideDetail && !moving
                    val viewport = (camera.viewport?.width ?: 0) to (camera.viewport?.height ?: 0)
                    if (!sideDetail) {
                        floorSettings.bodyRightPx = null
                        measuredBodyPose = null
                    } else if (!moving && model != null && viewport.first > 0 && viewport.second > 0 && (measuredBodyPose != orbit.pose || measuredBodyViewport != viewport)) {
                        // Project all corners of the model bounds; include casing, buttons and knob.
                        var right = Float.NEGATIVE_INFINITY
                        for (sx in listOf(-1f, 1f)) {
                            for (sy in listOf(-1f, 1f)) {
                                for (sz in listOf(-1f, 1f)) {
                                    val local = model.center + Position(model.halfExtent.x * sx, model.halfExtent.y * sy, model.halfExtent.z * sz)
                                    val world = model.getWorldPosition(local)
                                    val screen = camera.worldToScreenPoint(io.github.sceneview.collision.Vector3(world.x, world.y, world.z))
                                    right = maxOf(right, screen.x)
                                }
                            }
                        }
                        floorSettings.bodyRightPx = right.takeIf { it.isFinite() }
                        measuredBodyPose = orbit.pose
                        measuredBodyViewport = viewport
                    }
                    val partNode = model?.renderableNodes?.firstOrNull { it.name == selectedPart?.nodeName }
                    val anchor = if (sideDetail && partNode != null) {
                        val center = partNode.axisAlignedBoundingBox.center
                        val world = partNode.getWorldPosition(Position(center[0], center[1], center[2]))
                        val screen = camera.worldToScreenPoint(io.github.sceneview.collision.Vector3(world.x, world.y, world.z))
                        androidx.compose.ui.geometry.Offset(screen.x, screen.y)
                    } else {
                        null
                    }
                    if (anchor == null || floorSettings.controlAnchor == null || (anchor - floorSettings.controlAnchor!!).getDistance() > 1f) {
                        floorSettings.controlAnchor = anchor
                    }
                }
                val dt = if (scrollFrame == 0L) 0f else ((it - scrollFrame) / 1_000_000_000f).coerceIn(0f, .05f)
                scrollFrame = it
                if (floorChoosing && !assignmentClosing && !moving && !scrollDragging && kotlin.math.abs(scrollVelocity) > .001f) {
                    val next = (assignmentScroll + scrollVelocity * dt).coerceIn(0f, scrollLimit)
                    if (next == assignmentScroll) scrollVelocity = 0f
                    assignmentScroll = next
                    scrollVelocity *= kotlin.math.exp(-7f * dt)
                    orbit.pose = AssignmentLayout.camera(galleryX(localIndex(selectedIndex)), viewportWidthDp, aspect, assignmentScroll, floorSettings?.destination?.bindingIndex)
                    updateCamera()
                }
            },
            isOpaque = false,
            onViewCreated = {
                // Transparent UiHelper surfaces default to above the Activity. Keep UI above 3D.
                setZOrderMediaOverlay(true)
                view.setShadowingEnabled(true)
                view.colorGrading = studioGrading
                view.setShadowType(com.google.android.filament.View.ShadowType.PCF)
                environment.indirectLight?.let { it.intensity *= .50f }
                renderer.clearOptions = renderer.clearOptions.apply {
                    clear = true
                    clearColor = floatArrayOf(.878f, .843f, .788f, 1f)
                }
                sceneView = this
            },
        )
        // Overview is a fixed-angle carousel; detail allows pinch zoom without rotation.
        Box(
            modifier = Modifier.matchParentSize().pointerInput(camera, orbit, destination, moving, selectedId, modelKeys, devices.size, interactionEnabled, partInputEnabled, floorDetail, sideDetail, floorChoosing, assignmentClosing, viewportWidthDp, scrollLimit) {
                if (!interactionEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    down.consume()
                    scrollVelocity = 0f
                    scrollDragging = true
                    var dragged = false
                    var multiple = false
                    var lastPosition = down.position
                    val downFloor = if (floorDetail && !moving && partInputEnabled) {
                        sceneView?.collisionSystem?.hitTest(down.position.x, down.position.y)?.firstOrNull { floorPanel?.owns(it.node) == true }
                    } else {
                        null
                    }
                    val brightnessGesture = downFloor?.let { floorPanel?.isBrightness(it.worldPosition) } == true
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        // Android edge-back cancels the touch. Compose represents CANCEL as
                        // consumed releases; it must not also become a background tap/back.
                        // Check before consuming the event ourselves or applying any action.
                        if (event.changes.any { it.isConsumed }) {
                            scrollDragging = false
                            scrollVelocity = 0f
                            return@awaitEachGesture
                        }
                        val pressed = event.changes.count { it.pressed }
                        multiple = multiple || pressed > 1
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (pointer != null) lastPosition = pointer.position
                        dragged = dragged || (lastPosition - down.position).getDistance() > viewConfiguration.touchSlop
                        if (brightnessGesture && !multiple) {
                            sceneView?.collisionSystem?.hitTest(lastPosition.x, lastPosition.y)?.firstOrNull { floorPanel?.owns(it.node) == true }
                                ?.let { if (floorPanel?.isBrightness(it.worldPosition) == true) floorPanel?.hit(it.worldPosition) }
                        }
                        // Ignore pointer-count transitions to avoid a jump when a second finger lands.
                        if (destination == "controls" && !floorDetail && !sideDetail && !moving && event.changes.all { it.pressed == it.previousPressed }) {
                            if (pressed >= 2) {
                                orbit.zoom(event.calculateZoom(), minimum = if (destination == "controls") .65f else 1.15f, maximum = 12f)
                            }
                            updateCamera()
                        }
                        if (floorChoosing && !assignmentClosing && !moving && dragged && pressed == 1 && pointer != null) {
                            val shift = -(pointer.position.y - pointer.previousPosition.y) * AssignmentLayout.visibleDepth(viewportWidthDp, aspect) / size.height
                            assignmentScroll = (assignmentScroll + shift).coerceIn(0f, scrollLimit)
                            val seconds = (pointer.uptimeMillis - pointer.previousUptimeMillis).coerceAtLeast(1) / 1000f
                            scrollVelocity = (shift / seconds).coerceIn(-4f, 4f)
                            orbit.pose = AssignmentLayout.camera(galleryX(localIndex(selectedIndex)), viewportWidthDp, aspect, assignmentScroll, floorSettings?.destination?.bindingIndex)
                            updateCamera()
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    scrollDragging = false
                    val delta = lastPosition - down.position
                    if (!appearanceEdit && !moving && dragged && !multiple && destination != "controls" &&
                        kotlin.math.abs(delta.x) >= maxOf(size.width * .12f, viewConfiguration.touchSlop * 2f) &&
                        kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y) * 1.2f
                    ) {
                        val next = selectedIndex + if (delta.x < 0f) 1 else -1
                        when {
                            finding -> discoveryDevices.getOrNull(next - devices.size)?.let { select(it.id) }
                            hasActions && next == -1 -> select(SETTINGS_GALLERY_ID)
                            hasActions && next == devices.size -> select(FIND_GALLERY_ID)
                            else -> devices.getOrNull(next)?.let { select(it.id) }
                        }
                    }
                    if (!moving && !dragged && !multiple && destination == "controls" && partInputEnabled && allDevices.getOrNull(selectedIndex)?.kind == DeviceKind.HEADPHONE_STAND) {
                        val parts = model?.renderableNodes.orEmpty()
                        val hits = sceneView?.collisionSystem?.hitTest(lastPosition.x, lastPosition.y).orEmpty()
                        val hit = hits.firstOrNull { it.node in parts && StandPart.fromNode(it.node.name) != null }
                        val floorHit = hits.firstOrNull { floorPanel?.owns(it.node) == true }
                        if (floorChoosing) {
                            if (!assignmentClosing) floorHit?.let { floorPanel?.hit(it.worldPosition) }
                        } else if (hit != null && (floorHit == null || hit.distance < floorHit.distance)) {
                            selectPart?.invoke(StandPart.fromNode(hit.node.name))
                        } else if (floorDetail && floorHit != null) {
                            floorPanel?.hit(floorHit.worldPosition)
                        } else {
                            selectPart?.invoke(null)
                        }
                    }
                    if (!moving && !dragged && !multiple && destination == "controls" && partInputEnabled &&
                        allDevices.getOrNull(selectedIndex)?.kind == DeviceKind.CLUMO
                    ) {
                        val parts = model?.renderableNodes.orEmpty()
                        sceneView?.collisionSystem?.hitTest(lastPosition.x, lastPosition.y).orEmpty()
                            .firstNotNullOfOrNull { if (it.node in parts) MatrixPage.forNode(it.node.name) else null }
                            ?.let(matrixSelect)
                    }
                    if (appearanceEdit && !moving && !dragged && !multiple) {
                        val parts = model?.renderableNodes.orEmpty()
                        val hits = sceneView?.collisionSystem?.hitTest(lastPosition.x, lastPosition.y).orEmpty()
                        // The casing bounds enclose the recessed controls as well as the shell.
                        val control = hits.firstOrNull { hit ->
                            hit.node in parts && hit.node.name in appearanceControlRoles
                        }?.node?.name
                        if (control != null) {
                            appearanceTap(control)
                        } else {
                            val y = size.height - 1 - lastPosition.y.toInt()
                            val roles = parts.associate { it.entity to it.name }
                            sceneView?.view?.pick(lastPosition.x.toInt(), y, android.os.Handler(android.os.Looper.getMainLooper())) { hit ->
                                roles[hit.renderable]?.let(appearanceTap)
                            }
                        }
                    }
                    if (!appearanceEdit && !moving && !dragged && !multiple && destination != "controls") {
                        val hit = sceneView?.collisionSystem?.hitTest(lastPosition.x, lastPosition.y)?.firstOrNull()?.node
                        val index = models.indexOfFirst { candidate ->
                            generateSequence(hit) { it.parent }.any { it == candidate }
                        }
                        when (hit) {
                            actions?.nodes?.get(0) -> openSettings?.invoke()
                            actions?.nodes?.get(1) -> findDevices?.invoke()
                            else -> if (index >= 0) {
                                val device = allDevices[index]
                                if (openDevice != null && device.address != null) openDevice?.invoke(device.address) else select(device.id)
                            }
                        }
                    }
                }
            },
        )
        if (standGuide && !moving) knobFace?.let { GuideHoldRing(it) { holdProgress.floatValue } }
        if (appearanceEdit && appearanceLabel.isNotEmpty() && !moving) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val start = anchor ?: return@Canvas
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(57, 56, 48)
                    textSize = 14f * density
                }
                val edges = appearanceEdges ?: return@Canvas
                val gap = 8f * density
                val margin = 6f * density
                val rightSpace = size.width - margin - edges.second - gap
                val leftSpace = edges.first - gap - margin
                val words = appearanceLabel.split(' ')
                fun fits(space: Float) = words.all { paint.measureText(it) <= space }
                // Keep landscape's right side free for the palette. On compact screens the
                // palette title already identifies the part if neither gutter can fit a word.
                val leftSide = if (landscape) true else !fits(rightSpace)
                val space = if (leftSide) leftSpace else rightSpace
                if (!fits(space)) return@Canvas
                val lines = if (paint.measureText(appearanceLabel) <= space) listOf(appearanceLabel) else words
                val edge = if (leftSide) edges.first - gap else edges.second + gap
                val labelY = start.y - gap
                val lineEnd = androidx.compose.ui.geometry.Offset(edge + (if (leftSide) 1 else -1) * gap / 2, labelY)
                drawLine(Color.DarkGray.copy(alpha = .72f), start, lineEnd, density)
                paint.textAlign = if (leftSide) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.LEFT
                lines.forEachIndexed { index, line ->
                    drawContext.canvas.nativeCanvas.drawText(line, edge, labelY + 4f * density + index * paint.fontSpacing, paint)
                }
            }
        }
    }
}

/** Rotate each asset about its own centered bounds, without moving its gallery slot. */
private fun ModelNode.placeGalleryModel(index: Int, device: GalleryDevice, presentationYaw: Float, matrixHeight: Float) {
    val yaw = presentationYaw
    val radians = Math.toRadians(yaw.toDouble()).toFloat()
    val cosine = kotlin.math.cos(radians)
    val sine = kotlin.math.sin(radians)
    val isMatrix = device.kind == DeviceKind.CLUMO && !device.unidentified
    if (isMatrix) {
        val factor = matrixHeight / (2f * halfExtent.y)
        scale = io.github.sceneview.math.Scale(factor, factor, factor)
    }
    val scaledCenter = center * scale
    if (device.unidentified) {
        // The icon faces source +Z. Lay it face-up (+Y), with the underside on the floor.
        rotation = Rotation(-90f, yaw, 0f)
        position = Position(
            galleryX(index) - (scaledCenter.x * cosine - scaledCenter.y * sine),
            -.5f + size.z * scale.z / 2f - scaledCenter.z,
            scaledCenter.x * sine + scaledCenter.y * cosine,
        )
        return
    }
    val turn = Quaternion.fromAxisAngle(Position(0f, 1f, 0f), yaw)
    // Matrix is authored facing -Z with its pushbuttons on -Y.
    val orientation = if (isMatrix) turn * Quaternion.fromAxisAngle(Position(1f, 0f, 0f), 180f) else turn
    quaternion = orientation
    position = Position(galleryX(index), if (isMatrix) -.5f + matrixHeight / 2f else 0f, 0f) - orientation * scaledCenter
}

@Composable
private fun GuideHoldRing(face: Rect, progress: () -> Float) {
    val color = io.github.cespresso.clumo.ui.theme.LocalClumoAccents.current.accent
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val outline = face.inflate(2.dp.toPx())
        drawArc(
            color,
            -90f,
            360f * progress(),
            false,
            outline.topLeft,
            outline.size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()),
        )
    }
}
