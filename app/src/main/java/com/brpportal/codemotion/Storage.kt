package com.brpportal.codemotion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Serialize a Project back to the FORMAT_SPEC project.json schema. */
fun projectToJson(project: Project): String {
    val canvas = JSONObject()
        .put("w", project.width)
        .put("h", project.height)
        .put("fps", project.fps)
        .put("bg", project.bg)
    val scenesArr = JSONArray()
    for (scene in project.scenes) {
        val layersArr = JSONArray()
        for (layer in scene.layers) {
            layersArr.put(
                JSONObject()
                    .put("id", layer.id)
                    .put("type", layer.type)
                    .put("effect", layer.effect)
                    .put("startMs", layer.startMs)
                    .put("durationMs", layer.durationMs)
                    .put("x", layer.x.toDouble())
                    .put("y", layer.y.toDouble())
                    .put("w", layer.w.toDouble())
                    .put("h", layer.h.toDouble())
                    .put("text", layer.text)
                    .put("code", layer.code)
                    .put("color", layer.color)
                    .put("bg", layer.bg)
                    .put("fontSize", layer.fontSize.toDouble())
                    .apply {
                        // Optional v2 fields — written only when non-default,
                        // so old projects stay byte-compatible.
                        if (layer.easing.isNotEmpty()) put("easing", layer.easing)
                        if (layer.spring.isNotEmpty()) put("spring", layer.spring)
                        if (layer.staggerMs >= 0) {
                            if (layer.staggerFrom.isNotEmpty()) {
                                put("stagger", JSONObject()
                                    .put("eachMs", layer.staggerMs)
                                    .put("from", layer.staggerFrom))
                            } else {
                                put("stagger", layer.staggerMs)
                            }
                        }
                        if (layer.opacity != 1f) put("opacity", layer.opacity.toDouble())
                        if (layer.rotation != 0f) put("rotation", layer.rotation.toDouble())
                        if (!layer.imagePath.isNullOrEmpty()) put("imagePath", layer.imagePath)
                        if (layer.locked) put("locked", true)
                        if (layer.hidden) put("hidden", true)
                    }
            )
        }
        scenesArr.put(
            JSONObject()
                .put("id", scene.id)
                .put("durationMs", scene.durationMs)
                .put("layers", layersArr)
        )
    }
    return JSONObject()
        .put("canvas", canvas)
        .put("scenes", scenesArr)
        .toString(2)
}

// ---------------------------------------------------------------
// Internal saves (app files dir / projects) — "My Projects"
// ---------------------------------------------------------------

fun projectsDir(context: Context): File =
    File(context.filesDir, "projects").apply { mkdirs() }

fun sanitizeProjectName(name: String): String {
    val cleaned = name.trim().replace(Regex("[^A-Za-z0-9 _-]"), "_")
        .ifEmpty { "project" }
    return if (cleaned.endsWith(".json")) cleaned else "$cleaned.json"
}

fun saveInternalProject(context: Context, name: String, project: Project): File {
    val file = File(projectsDir(context), sanitizeProjectName(name))
    file.writeText(projectToJson(project), Charsets.UTF_8)
    return file
}

fun listInternalProjects(context: Context): List<File> =
    (projectsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".json") }
        ?: emptyArray())
        .sortedByDescending { it.lastModified() }

fun loadInternalProject(file: File): Project =
    parseProject(file.readText(Charsets.UTF_8))

fun deleteInternalProject(file: File): Boolean = file.delete()

// ---------------------------------------------------------------
// Imported images (app files dir / images) — image layers
// ---------------------------------------------------------------

fun imagesDir(context: Context): File =
    File(context.filesDir, "images").apply { mkdirs() }

/**
 * Copies a picked image (content Uri) into the app's internal images dir so
 * the project keeps working after the picker grant is gone. Each import gets
 * a unique file name, so Replace never fights the renderer's bitmap cache.
 * Returns the absolute path, or null on failure.
 */
fun importImageFromUri(context: Context, uri: android.net.Uri): String? {
    return try {
        val ext = when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val file = File(imagesDir(context), "img_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (file.length() == 0L) {
            file.delete()
            null
        } else {
            file.absolutePath
        }
    } catch (e: Exception) {
        null
    }
}
