package io.github.cespresso.clumo.ui.device

import io.github.cespresso.clumo.data.ble.StandDisplaySettings
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class StandbyPattern(
    val name: String,
    val frames: List<List<Int>> = listOf(List(8) { 0 }),
    val duration: Int = 300,
    val mode: String = "frames",
) {
    fun settings(brightness: Byte): StandDisplaySettings {
        val data = ByteArray(StandDisplaySettings.SIZE)
        data[0] = brightness
        data[1] = modes.indexOf(mode).toByte()
        frames.first().forEachIndexed { row, value -> data[row + 2] = value.toByte() }
        data[10] = 1
        data[11] = frames.size.toByte()
        data[12] = duration.toByte()
        data[13] = (duration shr 8).toByte()
        frames.flatten().forEachIndexed { i, value -> data[14 + i] = value.toByte() }
        return requireNotNull(StandDisplaySettings.decode(data))
    }

    fun json(): JSONObject =
        JSONObject().put("format", "clumo.standby").put("version", 1)
            .put("name", name).put("mode", mode).put("brightness", 4).put("frameDurationMs", duration)
            .put("frames", JSONArray(frames.map { rows -> rows.map { it.toString(2).padStart(8, '0') } }))

    fun matches(other: StandbyPattern) =
        mode == other.mode && frames == other.frames &&
            (frames.size == 1 || duration == other.duration)

    companion object {
        val modes = listOf("frames", "scroll", "off")
        val off = StandbyPattern("Off", mode = "off")
        val presets = listOf(
            StandbyPattern(
                "Heartbeat",
                listOf(
                    listOf(0, 0, 36, 126, 126, 60, 24, 0),
                    listOf(0, 102, 255, 255, 126, 60, 24, 0),
                    listOf(0, 0, 36, 126, 126, 60, 24, 0),
                    listOf(0, 0, 0, 36, 60, 24, 0, 0),
                ),
                250,
            ),
            StandbyPattern("Music note", listOf(listOf(14, 10, 10, 10, 58, 122, 115, 3))),
            StandbyPattern(
                "Wave",
                List(8) { t ->
                    List(8) { y ->
                        (0..7).sumOf { x: Int ->
                            if ((3.5 + 2.5 * sin((x + t) * Math.PI / 4)).roundToInt() == y) 128 shr x else 0
                        }
                    }
                },
                150,
            ),
            StandbyPattern(
                "CLumo logo",
                listOf(
                    listOf(0, 0, 0, 0, 0, 96, 96, 0),
                    listOf(0, 0, 0, 96, 96, 96, 96, 0),
                    listOf(0, 112, 112, 96, 96, 96, 96, 0),
                    listOf(0, 126, 126, 96, 96, 96, 96, 0),
                    listOf(0, 126, 126, 96, 96, 96, 96, 0),
                    listOf(0, 126, 126, 96, 96, 96, 96, 0),
                    listOf(0, 112, 112, 96, 96, 96, 96, 0),
                    listOf(0, 0, 0, 96, 96, 96, 96, 0),
                ),
                200,
            ),
        )
        fun fromDevice(settings: StandDisplaySettings): StandbyPattern {
            val b = settings.encode()
            fun row(i: Int) = b[i].toInt() and 255
            val mode = modes[row(1)]
            if (mode == "off") return off
            val count = if (mode == "scroll") 1 else row(11).coerceAtLeast(1)
            val frames = List(count) { f -> List(8) { y -> row(if (row(11) == 0) 2 + y else 14 + 8 * f + y) } }
            return StandbyPattern("On device", frames, if (mode == "scroll") 600 else (row(12) + 256 * row(13)).takeIf { it in 100..2000 } ?: 300, mode)
        }
        fun parse(text: String): StandbyPattern {
            require(text.length <= 16384) { "Pattern is too large." }
            val source = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parser = org.json.JSONTokener(source)
            val j = parser.nextValue() as? JSONObject ?: error("Expected a JSON object.")
            require(
                parser.nextClean() == '\u0000' && j.keys().asSequence().toSet() ==
                    setOf("format", "version", "name", "mode", "brightness", "frameDurationMs", "frames"),
            ) { "Unexpected pattern format." }
            fun integer(key: String, range: IntRange): Int {
                val n = j.get(key)
                require(n is Int && n in range) { "Invalid $key." }
                return n
            }
            require(j.get("format") == "clumo.standby")
            integer("version", 1..1)
            integer("brightness", 0..15)
            val name = j.get("name") as? String
            require(!name.isNullOrBlank()) { "A pattern name is required." }
            val mode = j.get("mode") as? String
            require(mode != null && mode in modes) { "Unknown mode." }
            val duration = integer("frameDurationMs", 100..2000)
            val all = j.getJSONArray("frames")
            require(all.length() in 1..8) { "Use one to eight frames." }
            val frames = List(all.length()) { f ->
                val rows = all.getJSONArray(f)
                require(rows.length() == 8) { "Each frame needs eight rows." }
                List(8) { y ->
                    val row = rows.get(y) as? String
                    require(row != null && row.matches(Regex("[01]{8}"))) { "Each row needs eight binary digits." }
                    row.toInt(2)
                }
            }
            require(mode != "scroll" || (frames.size == 1 && duration == 600)) { "Scroll uses one frame at 600 ms." }
            require(mode != "off" || (frames == off.frames && duration == 300)) { "Off uses one empty frame at 300 ms." }
            return StandbyPattern(name.trim(), frames, duration, mode)
        }
    }
}

internal fun readStandbyLibrary(prefs: android.content.SharedPreferences): Map<String, StandbyPattern> =
    prefs.all.mapNotNull { (id, text) ->
        (text as? String)?.let { runCatching { StandbyPattern.parse(it) }.getOrNull() }?.let { id to it }
    }.toMap()
