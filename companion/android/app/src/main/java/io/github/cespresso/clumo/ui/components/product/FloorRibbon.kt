package io.github.cespresso.clumo.ui.components.product

import com.google.android.filament.Engine
import dev.romainguy.kotlin.math.Float2
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.node.GeometryNode
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Closed ribbon with real top, bottom, side walls and end caps. Scene units are metres. */
internal class FloorRibbon(engine: Engine, private val materials: MaterialLoader, private val branch: Boolean = false) {
    private val material = materials.createMaterial("materials/floor_ribbon.filamat")
    private val instance = materials.createInstance(material).apply {
        setParameter("reveal", 0f)
        setParameter("tint", .85f, .10f, .012f)
    }
    private var appliedColor: Int? = null

    fun setColor(rgb: Int) {
        if (appliedColor == rgb) return
        fun linear(shift: Int): Float {
            val channel = ((rgb shr shift) and 255) / 255f
            return if (channel <= .04045f) channel / 12.92f else ((channel + .055f) / 1.055f).pow(2.4f)
        }
        instance.setParameter("tint", linear(16), linear(8), linear(0))
        appliedColor = rgb
    }
    val node = GeometryNode(engine, geometry(engine, branch), instance, builderApply = {
        castShadows(true)
        receiveShadows(true)
    }).apply {
        name = "floor-ribbon-solid"
        isTouchable = false
    }

    fun update(x: Float, visible: Boolean, progress: Float, z: Float = 0f) {
        // The floor is at -.502; a tiny clearance avoids coplanar bottom faces.
        node.position = Position(x, -.50199f, z)
        node.isVisible = visible
        instance.setParameter("reveal", progress)
    }

    fun reveal(progress: Float) = instance.setParameter("reveal", progress)

    fun destroy() {
        node.destroy()
        materials.destroyMaterialInstance(instance)
        materials.destroyMaterial(material)
    }

    companion object {
        const val THICKNESS_METRES = .001f / .244f // 1mm relative to the normalized 244mm product.
        private const val HALF_WIDTH = .024f / .244f
        private const val RADIUS = .13f

        private data class Station(val x: Float, val z: Float, val nx: Float, val nz: Float)

        private fun stations(branch: Boolean = false): List<Station> {
            val stations = mutableListOf<Station>()
            fun point(x: Float, z: Float, nx: Float, nz: Float) {
                stations += Station(x, z, nx, nz)
            }
            fun arc(cx: Float, cz: Float, start: Float, sweep: Float) {
                for (i in 0..32) {
                    val a = Math.toRadians((start + sweep * i / 32f).toDouble())
                    val c = cos(a).toFloat()
                    val s = sin(a).toFloat()
                    val sign = if (sweep > 0) 1f else -1f
                    point(cx + RADIUS * c, cz + RADIUS * s, sign * c, sign * s)
                }
            }
            if (branch) {
                point(-.30f, 1.06f, 0f, -1f)
                arc(.172f / .244f - RADIUS, 1.06f + RADIUS, 270f, 90f)
                point(.172f / .244f, 3.70f, 1f, 0f)
                return stations
            }
            point(-.65f, -.40f, 0f, -1f)
            arc(.19f, -.27f, 270f, 90f)
            arc(.19f, .16f, 0f, 90f)
            arc(-.17f, .42f, 270f, -90f)
            point(-.30f, 2.55f, 1f, 0f)
            return stations
        }

        /** Same sampled centreline as the mesh UVs; foreground rows lie on its final straight. */
        fun foregroundProgress(z: Float, branch: Boolean = false): Float {
            val points = stations(branch)
            fun distance(a: Station, b: Station) = sqrt((b.x - a.x) * (b.x - a.x) + (b.z - a.z) * (b.z - a.z))
            val total = points.zipWithNext().sumOf { (a, b) -> distance(a, b).toDouble() }.toFloat()
            val last = points.last()
            return ((total - (last.z - z)) / total).coerceIn(0f, 1f)
        }

        private fun geometry(engine: Engine, branch: Boolean): Geometry {
            val stations = stations(branch)
            val distances = FloatArray(stations.size)
            for (i in 1 until stations.size) {
                val a = stations[i - 1]
                val b = stations[i]
                distances[i] = distances[i - 1] + sqrt((b.x - a.x) * (b.x - a.x) + (b.z - a.z) * (b.z - a.z))
            }
            val vertices = mutableListOf<Geometry.Vertex>()
            val indices = mutableListOf<Int>()
            fun quad(points: List<Position>, normal: Position, uv: List<Float>) {
                val start = vertices.size
                points.forEachIndexed { i, p -> vertices += Geometry.Vertex(position = p, normal = normal, uvCoordinate = Float2(uv[i], 0f)) }
                val a = points[1] - points[0]
                val b = points[2] - points[0]
                val dot = (a.y * b.z - a.z * b.y) * normal.x + (a.z * b.x - a.x * b.z) * normal.y + (a.x * b.y - a.y * b.x) * normal.z
                indices += if (dot > 0f) listOf(start, start + 1, start + 2, start, start + 2, start + 3) else listOf(start, start + 2, start + 1, start, start + 3, start + 2)
            }
            fun corner(s: Station, side: Float, y: Float) = Position(s.x + side * HALF_WIDTH * s.nx, y, s.z + side * HALF_WIDTH * s.nz)
            for (i in 1 until stations.size) {
                val a = stations[i - 1]
                val b = stations[i]
                val u = distances[i - 1] / distances.last()
                val v = distances[i] / distances.last()
                for (y in listOf(0f, THICKNESS_METRES)) {
                    quad(listOf(corner(a, -1f, y), corner(a, 1f, y), corner(b, 1f, y), corner(b, -1f, y)), Position(0f, if (y == 0f) -1f else 1f, 0f), listOf(u, u, v, v))
                }
                for (side in listOf(-1f, 1f)) {
                    val nx = (a.nx + b.nx) * side
                    val nz = (a.nz + b.nz) * side
                    val length = sqrt(nx * nx + nz * nz)
                    quad(listOf(corner(a, side, 0f), corner(a, side, THICKNESS_METRES), corner(b, side, THICKNESS_METRES), corner(b, side, 0f)), Position(nx / length, 0f, nz / length), listOf(u, u, v, v))
                }
            }
            for ((s, normal, u) in listOf(Triple(stations.first(), Position(-1f, 0f, 0f), 0f), Triple(stations.last(), Position(0f, 0f, 1f), 1f))) {
                quad(listOf(corner(s, -1f, 0f), corner(s, 1f, 0f), corner(s, 1f, THICKNESS_METRES), corner(s, -1f, THICKNESS_METRES)), normal, List(4) { u })
            }
            check(vertices.maxOf { it.position.y } - vertices.minOf { it.position.y } == THICKNESS_METRES)
            return Geometry.Builder().vertices(vertices).indices(indices).build(engine)
        }
    }
}
