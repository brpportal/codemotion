package com.brpportal.codemotion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One entry of catalog/effects.json. */
data class EffectDef(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val defaultDurationMs: Int,
    val sampleKind: String
)

/** One layer of a scene in project.json. Coordinates are canvas pixels. */
data class Layer(
    val id: String,
    val type: String,
    val effect: String,
    val startMs: Int,
    val durationMs: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val text: String,
    val code: String,
    val color: String,
    val bg: String,
    val fontSize: Float
)

data class Scene(
    val id: String,
    val durationMs: Int,
    val layers: List<Layer>
)

data class Project(
    val width: Int,
    val height: Int,
    val fps: Int,
    val scenes: List<Scene>
)

fun parseEffects(jsonText: String): List<EffectDef> {
    val arr = JSONObject(jsonText).getJSONArray("effects")
    val out = ArrayList<EffectDef>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        out.add(
            EffectDef(
                id = o.getString("id"),
                name = o.getString("name"),
                category = o.optString("category"),
                description = o.optString("description"),
                defaultDurationMs = o.optInt("defaultDurationMs", 1000),
                sampleKind = o.optString("sampleKind", "text")
            )
        )
    }
    return out
}

fun parseProject(jsonText: String): Project {
    val root = JSONObject(jsonText)
    val canvas = root.optJSONObject("canvas") ?: JSONObject()
    val scenesArr = root.getJSONArray("scenes")
    val scenes = ArrayList<Scene>()
    for (i in 0 until scenesArr.length()) {
        val s = scenesArr.getJSONObject(i)
        val layersArr = s.optJSONArray("layers") ?: JSONArray()
        val layers = ArrayList<Layer>()
        for (j in 0 until layersArr.length()) {
            val l = layersArr.getJSONObject(j)
            layers.add(
                Layer(
                    id = l.optString("id", "layer${j + 1}"),
                    type = l.optString("type", "text"),
                    effect = l.optString("effect", "fade"),
                    startMs = l.optInt("startMs", 0),
                    durationMs = l.optInt("durationMs", 1000),
                    x = l.optDouble("x", 0.0).toFloat(),
                    y = l.optDouble("y", 0.0).toFloat(),
                    w = l.optDouble("w", 1080.0).toFloat(),
                    h = l.optDouble("h", 200.0).toFloat(),
                    text = l.optString("text"),
                    code = l.optString("code"),
                    color = l.optString("color", "#FFFFFF"),
                    bg = l.optString("bg", "#00000000"),
                    fontSize = l.optDouble("fontSize", 48.0).toFloat()
                )
            )
        }
        scenes.add(Scene(s.optString("id", "scene${i + 1}"), s.optInt("durationMs", 3000), layers))
    }
    return Project(
        width = canvas.optInt("w", 1080),
        height = canvas.optInt("h", 1920),
        fps = canvas.optInt("fps", 30),
        scenes = scenes
    )
}

/** "#RRGGBB" or "#AARRGGBB" -> 0xAARRGGBB as Long (for Compose Color). */
fun parseColor(hex: String, fallback: Long = 0xFFFFFFFFL): Long {
    val h = hex.trim().removePrefix("#")
    return try {
        when (h.length) {
            6 -> 0xFF000000L or h.toLong(16)
            8 -> h.toLong(16)
            else -> fallback
        }
    } catch (e: Exception) {
        fallback
    }
}

fun loadEffects(context: Context): List<EffectDef> = try {
    val text = context.assets.open("effects.json").bufferedReader().readText()
    parseEffects(text)
} catch (e: Exception) {
    emptyList()
}
