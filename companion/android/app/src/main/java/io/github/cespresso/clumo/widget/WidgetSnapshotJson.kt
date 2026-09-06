package io.github.cespresso.clumo.widget

import org.json.JSONArray
import org.json.JSONObject

internal fun encodeWidgetSnapshot(snapshot: WidgetSnapshot): String = JSONObject().apply {
    put("link", snapshot.link.name)
    put("headline", snapshot.headline.name)
    put("subtitle", snapshot.subtitle.name)
    put("subtitleText", snapshot.subtitleText)
    put("subtitleArgA", snapshot.subtitleArgA)
    put("subtitleArgB", snapshot.subtitleArgB)
    put("alias", snapshot.alias)
    // Stored as a string: JSON numbers go through Double in some parsers, which would
    // lose the low bits of a 64-bit mask.
    put("faceBits", snapshot.faceBits.toString())
    put("faceDimmed", snapshot.faceDimmed)
    put("facePlaceholder", snapshot.facePlaceholder)
    put("family", snapshot.family.name)
    put("actions", JSONArray().apply { snapshot.actions.forEach { put(it.name) } })
    put("enclosureArgb", snapshot.enclosureArgb)
    put("ctaArgb", snapshot.ctaArgb)
    put("onCtaArgb", snapshot.onCtaArgb)
    put("knobArgb", snapshot.knobArgb)
    put("ledArgb", snapshot.ledArgb)
    put("updatedAtRealtime", snapshot.updatedAtRealtime)
}.toString()

internal fun decodeWidgetSnapshot(raw: String?): WidgetSnapshot? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val root = JSONObject(raw)
        val actionsArray = root.optJSONArray("actions") ?: JSONArray()
        WidgetSnapshot(
            link = enumValueOf<WidgetLink>(root.getString("link")),
            headline = enumValueOf<WidgetHeadline>(root.getString("headline")),
            subtitle = enumValueOf<WidgetSubtitle>(root.getString("subtitle")),
            subtitleText = root.optString("subtitleText"),
            subtitleArgA = root.optInt("subtitleArgA"),
            subtitleArgB = root.optInt("subtitleArgB"),
            alias = root.optString("alias"),
            faceBits = root.getString("faceBits").toLong(),
            faceDimmed = root.optBoolean("faceDimmed"),
            facePlaceholder = root.optBoolean("facePlaceholder"),
            family = enumValueOf<WidgetFamily>(root.getString("family")),
            actions = List(actionsArray.length()) {
                enumValueOf<WidgetAction>(actionsArray.getString(it))
            },
            enclosureArgb = root.getInt("enclosureArgb"),
            ctaArgb = root.getInt("ctaArgb"),
            onCtaArgb = root.getInt("onCtaArgb"),
            knobArgb = root.getInt("knobArgb"),
            ledArgb = root.getInt("ledArgb"),
            updatedAtRealtime = root.getLong("updatedAtRealtime"),
        )
    }.getOrNull()
}
