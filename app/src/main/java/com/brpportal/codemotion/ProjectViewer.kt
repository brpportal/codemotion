package com.brpportal.codemotion

import android.content.Context
import android.graphics.Canvas
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Custom View that paints the current scene via the shared [Renderer]. */
class ScenePlayerView(context: Context) : View(context) {
    var project: Project? = null
    var globalTimeMs: Long = 0

    // --- Editor mode (used by the professional editor) ---
    /** When set, draw exactly this scene at [localTimeMs] instead of locating. */
    var sceneOverride: Int? = null
    var localTimeMs: Long = 0
    var selectedLayerId: String? = null
    var onLayerTap: ((String) -> Unit)? = null
    var onLayerDrag: ((String, Float, Float) -> Unit)? = null

    private var lastSceneIdx: Int = 0
    private var lastLocalMs: Long = 0
    private var dragLayerId: String? = null
    private var grabDx: Float = 0f
    private var grabDy: Float = 0f
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var dragMoved: Boolean = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = project ?: return
        if (p.scenes.isEmpty()) return
        val override = sceneOverride
        val (sceneIdx, localMs) = if (override != null) {
            override.coerceIn(0, p.scenes.size - 1) to localTimeMs
        } else {
            Renderer.locate(p, globalTimeMs)
        }
        lastSceneIdx = sceneIdx
        lastLocalMs = localMs
        Renderer.drawScene(canvas, p, sceneIdx, localMs)
        val selId = selectedLayerId
        if (selId != null) {
            p.scenes.getOrNull(sceneIdx)?.layers?.firstOrNull { it.id == selId }?.let {
                Renderer.drawSelectionOutline(canvas, p, it)
            }
        }
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        val p = project ?: return super.onTouchEvent(event)
        if (onLayerTap == null && onLayerDrag == null) return super.onTouchEvent(event)
        val scene = p.scenes.getOrNull(lastSceneIdx) ?: return super.onTouchEvent(event)
        val proj = Renderer.viewToProject(p, width, height, event.x, event.y)
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragMoved = false
                dragLayerId = null
                if (proj != null) {
                    val hit = Renderer.hitLayer(p, scene, lastLocalMs, proj[0], proj[1])
                    if (hit != null) {
                        dragLayerId = hit.id
                        grabDx = proj[0] - hit.x
                        grabDy = proj[1] - hit.y
                        if (hit.id != selectedLayerId) onLayerTap?.invoke(hit.id)
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val id = dragLayerId ?: return super.onTouchEvent(event)
                if (kotlin.math.abs(event.x - downX) > 6f ||
                    kotlin.math.abs(event.y - downY) > 6f
                ) {
                    dragMoved = true
                }
                if (dragMoved && proj != null) {
                    onLayerDrag?.invoke(id, proj[0] - grabDx, proj[1] - grabDy)
                    return true
                }
                return super.onTouchEvent(event)
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                val wasDragging = dragLayerId != null
                dragLayerId = null
                parent?.requestDisallowInterceptTouchEvent(false)
                return wasDragging || super.onTouchEvent(event)
            }
        }
        return super.onTouchEvent(event)
    }
}

@Composable
fun ProjectViewer(
    project: Project,
    effects: List<EffectDef>,
    onProjectChange: (Project) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    var editing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val editTarget = editing
    if (editTarget != null) {
        EditorScreen(
            project = project,
            sceneIndex = editTarget.first,
            layerIndex = editTarget.second,
            effects = effects,
            onProjectChange = onProjectChange,
            onBack = { editing = null }
        )
        return
    }

    val context = LocalContext.current
    val totalMs = Renderer.totalDurationMs(project)
    var playing by remember { mutableStateOf(false) }
    var loop by remember { mutableStateOf(true) }
    var timeMs by remember { mutableStateOf(0L) }
    var saveName by remember { mutableStateOf("my-project") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    // Playback clock (~display refresh; renderer itself is 30fps-modelled).
    LaunchedEffect(playing, totalMs) {
        if (!playing || totalMs <= 0L) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameMillis { now ->
                if (last < 0L) last = now
                val dt = now - last
                last = now
                var t = timeMs + dt
                if (t >= totalMs) {
                    if (loop) {
                        t = if (totalMs > 0) t % totalMs else 0L
                    } else {
                        t = totalMs
                        playing = false
                    }
                }
                timeMs = t
            }
        }
    }

    val saveDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(projectToJson(project).toByteArray(Charsets.UTF_8))
                }
                statusMsg = "project.json save ho gaya."
            } catch (e: Exception) {
                statusMsg = "Save error: ${e.message}"
            }
        }
    }

    val (currentSceneIdx, _) = Renderer.locate(project, timeMs)
    val safeSceneIdx = currentSceneIdx.coerceIn(0, (project.scenes.size - 1).coerceAtLeast(0))

    fun replaceScene(idx: Int, newScene: Scene) {
        onProjectChange(project.copy(
            scenes = project.scenes.mapIndexed { i, s -> if (i == idx) newScene else s }
        ))
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Text(
                "Project Studio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Canvas: ${project.width}x${project.height} • ${project.fps} fps • " +
                "${project.scenes.size} scenes • ${(totalMs / 1000.0)}s",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))

        // ---------------- Animated preview ----------------
        if (project.scenes.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val ratio = project.width.toFloat() / project.height.coerceAtLeast(1)
                val h = (maxWidth / ratio).coerceAtMost(440.dp)
                AndroidView(
                    factory = { ctx -> ScenePlayerView(ctx) },
                    update = { view ->
                        view.project = project
                        view.globalTimeMs = timeMs
                        view.invalidate()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(h)
                        .heightIn(min = 160.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { playing = !playing }) {
                    Text(if (playing) "Pause" else "Play")
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    String.format("%.1fs / %.1fs", timeMs / 1000.0, totalMs / 1000.0),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.width(10.dp))
                Checkbox(checked = loop, onCheckedChange = { loop = it })
                Text("Loop", style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = timeMs.toFloat(),
                onValueChange = { timeMs = it.toLong() },
                valueRange = 0f..totalMs.toFloat().coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth()
            )

            // Scene chips seek to the scene start.
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                project.scenes.forEachIndexed { i, scene ->
                    FilterChip(
                        selected = i == safeSceneIdx,
                        onClick = { timeMs = Renderer.sceneStartMs(project, i) },
                        label = { Text(scene.id) }
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // ---------------- Save / Export ----------------
        Row {
            Button(onClick = { saveDocLauncher.launch("project.json") }) {
                Text("Save JSON")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onExport) { Text("Export MP4") }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = saveName,
                onValueChange = { saveName = it },
                label = { Text("Internal save naam") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                try {
                    val f = saveInternalProject(context, saveName, project)
                    statusMsg = "Internal save ho gaya: ${f.name} (My Projects me milega)"
                } catch (e: Exception) {
                    statusMsg = "Save error: ${e.message}"
                }
            }) { Text("Save internal") }
        }
        if (statusMsg != null) {
            Spacer(Modifier.height(6.dp))
            Text(statusMsg!!, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(14.dp))

        // ---------------- Scenes & layers ----------------
        if (project.scenes.isEmpty()) {
            Text("Is project me koi scene nahi hai. Neeche se scene add karo.")
        } else {
            val scene = project.scenes[safeSceneIdx]
            Text(
                "Scene: ${scene.id} — ${scene.durationMs}ms — ${scene.layers.size} layers (layer par tap = editor)",
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            scene.layers.forEachIndexed { li, layer ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { editing = safeSceneIdx to li }
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${layer.type} • ${layer.effect}", fontWeight = FontWeight.Bold)
                        Text(
                            "start ${layer.startMs}ms • duration ${layer.durationMs}ms • " +
                                "x${layer.x.toInt()} y${layer.y.toInt()} ${layer.w.toInt()}x${layer.h.toInt()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        val snippet = when {
                            layer.code.isNotEmpty() ->
                                layer.code.replace("\n", " ⏎ ").take(90)
                            layer.text.isNotEmpty() -> layer.text.take(90)
                            else -> ""
                        }
                        if (snippet.isNotEmpty()) {
                            Text(snippet, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Tap karke edit karo ✎",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Add layer (is scene me)", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("text", "code", "card", "highlight", "image").forEach { type ->
                    OutlinedButton(onClick = {
                        val newLayer = defaultLayer(project, scene, type)
                        replaceScene(
                            safeSceneIdx,
                            scene.copy(layers = scene.layers + newLayer)
                        )
                        editing = safeSceneIdx to scene.layers.size
                    }) { Text("+ $type") }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = {
                    if (project.scenes.size > 1) {
                        val newScenes = project.scenes.toMutableList().apply {
                            removeAt(safeSceneIdx)
                        }
                        onProjectChange(project.copy(scenes = newScenes))
                        timeMs = 0L
                    }
                }) { Text("Delete scene") }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                val newScene = Scene(
                    id = uniqueSceneId(project),
                    durationMs = 3000,
                    layers = emptyList()
                )
                onProjectChange(project.copy(scenes = project.scenes + newScene))
                timeMs = Renderer.totalDurationMs(project)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add scene") }
        Spacer(Modifier.height(24.dp))
    }
}

private fun uniqueSceneId(project: Project): String {
    var n = project.scenes.size + 1
    val ids = project.scenes.map { it.id }.toSet()
    while (ids.contains("scene$n")) n++
    return "scene$n"
}

private fun defaultLayer(project: Project, scene: Scene, type: String): Layer {
    val base = System.currentTimeMillis() % 100000L
    var id = "layer-$base"
    val ids = scene.layers.map { it.id }.toSet()
    if (ids.contains(id)) id = "layer-$base-x"
    val w = project.width.toFloat()
    val h = project.height.toFloat()
    return when (type) {
        "code" -> Layer(
            id = id, type = "code", effect = "code-typewriter",
            startMs = 0, durationMs = 2000,
            x = w * 0.05f, y = h * 0.25f, w = w * 0.9f, h = h * 0.3f,
            text = "", code = "fun main() {\n  println(\"Code Urdu\")\n}",
            color = "#9CDCFE", bg = "#1E1E2E", fontSize = 44f
        )
        "card" -> Layer(
            id = id, type = "card", effect = "slide-card",
            startMs = 0, durationMs = 700,
            x = w * 0.08f, y = h * 0.55f, w = w * 0.84f, h = h * 0.12f,
            text = "New Card", code = "",
            color = "#FFFFFF", bg = "#1E88E5", fontSize = 56f
        )
        "highlight" -> Layer(
            id = id, type = "highlight", effect = "circle-marker",
            startMs = 0, durationMs = 900,
            x = w * 0.25f, y = h * 0.4f, w = w * 0.5f, h = h * 0.08f,
            text = "subscribe()", code = "",
            color = "#FFEB3B", bg = "#00000000", fontSize = 48f
        )
        "image" -> Layer(
            id = id, type = "image", effect = "fade",
            startMs = 0, durationMs = 800,
            x = w * 0.1f, y = h * 0.3f, w = w * 0.8f, h = h * 0.3f,
            text = "IMAGE", code = "",
            color = "#FFFFFF", bg = "#00000000", fontSize = 48f
        )
        else -> Layer(
            id = id, type = "text", effect = "pop-in",
            startMs = 0, durationMs = 600,
            x = w * 0.05f, y = h * 0.35f, w = w * 0.9f, h = h * 0.12f,
            text = "New Text", code = "",
            color = "#FFFFFF", bg = "#00000000", fontSize = 72f
        )
    }
}
