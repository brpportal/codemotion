package com.brpportal.codemotion

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val LAYER_TYPES = listOf("text", "code", "card", "image", "highlight")
private val SPRING_EFFECTS = setOf("pop-in", "keyword-pulse", "glow-pulse")
private val STAGGER_EFFECTS = setOf("word-reveal", "code-typewriter", "char-stagger-in")

private val COLOR_PRESETS: List<Pair<String, String>> = listOf(
    "White" to "#FFFFFF",
    "Yellow" to "#FFEB3B",
    "Amber" to "#FFD54F",
    "Cyan" to "#4DD0E1",
    "Green" to "#69F0AE",
    "Pink" to "#FF80AB",
    "Orange" to "#FFAB40",
    "Red" to "#FF5252",
    "Blue" to "#40C4FF",
    "Black" to "#000000"
)

private const val TRANSPARENT_HEX = "#00000000"

private fun typeColor(type: String): Color = when (type) {
    "text" -> Color(0xFF42A5F5)
    "code" -> Color(0xFF26A69A)
    "card" -> Color(0xFFAB47BC)
    "image" -> Color(0xFF66BB6A)
    "highlight" -> Color(0xFFFFCA28)
    else -> Color(0xFF8D6E63)
}

/**
 * Professional editor (Alight Motion / InShot style):
 *  - Top: interactive animated preview — tap a layer to select it, drag the
 *    selected layer to move it (x/y update live).
 *  - Middle: TIMELINE — seconds ruler (drag to scrub), one track per layer;
 *    a bar's body drag moves startMs, its EDGES trim start/duration.
 *    Zoom with - / + (px per second).
 *  - Bottom: tabbed property panel for the selected layer
 *    (Content / Transform / Style / Animation / Layer).
 * Every control writes a copy() straight into the project — the preview,
 * timeline and export all read the same model.
 */
@Composable
fun EditorScreen(
    project: Project,
    sceneIndex: Int,
    layerIndex: Int,
    effects: List<EffectDef>,
    onProjectChange: (Project) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var sceneIdx by remember {
        mutableStateOf(sceneIndex.coerceIn(0, (project.scenes.size - 1).coerceAtLeast(0)))
    }
    val scene = project.scenes.getOrNull(sceneIdx)
    var selectedId by remember {
        mutableStateOf(scene?.layers?.getOrNull(layerIndex)?.id)
    }
    var timeMs by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var loop by remember { mutableStateOf(true) }
    var pxPerSec by remember { mutableStateOf(64f) }
    var tab by remember { mutableStateOf(0) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var imageTargetId by remember { mutableStateOf<String?>(null) }

    if (scene == null) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Button(onClick = onBack) { Text("Done") }
            Spacer(Modifier.height(12.dp))
            Text("Project me koi scene nahi hai.")
        }
        return
    }

    val selectedIndex = scene.layers.indexOfFirst { it.id == selectedId }
    val layer = scene.layers.getOrNull(selectedIndex)

    fun updateScene(newScene: Scene) {
        onProjectChange(project.copy(
            scenes = project.scenes.mapIndexed { si, s -> if (si == sceneIdx) newScene else s }
        ))
    }

    fun updateLayer(id: String, transform: (Layer) -> Layer) {
        val cur = project.scenes.getOrNull(sceneIdx) ?: return
        updateScene(cur.copy(layers = cur.layers.map { if (it.id == id) transform(it) else it }))
    }

    // ---- Playback clock (scene-local) ----
    val sceneDur = scene.durationMs.toLong()
    LaunchedEffect(playing, sceneDur, sceneIdx) {
        if (!playing || sceneDur <= 0L) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameMillis { now ->
                if (last < 0L) last = now
                val dt = now - last
                last = now
                var t = timeMs + dt
                if (t >= sceneDur) {
                    if (loop) t = if (sceneDur > 0) t % sceneDur else 0L
                    else {
                        t = sceneDur
                        playing = false
                    }
                }
                timeMs = t
            }
        }
    }

    // ---- Image picker: copy into internal images dir, set imagePath ----
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val targetId = imageTargetId
        imageTargetId = null
        if (uri != null && targetId != null) {
            val path = importImageFromUri(context, uri)
            if (path != null) {
                updateLayer(targetId) { it.copy(imagePath = path, type = "image") }
                statusMsg = "Image set ho gayi."
            } else {
                statusMsg = "Image import fail — dobara try karo."
            }
        }
    }

    fun pickImageFor(layerId: String) {
        imageTargetId = layerId
        imagePicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun addLayer(type: String) {
        val newLayer = defaultLayerAt(project, scene, type, timeMs.toInt())
        updateScene(scene.copy(layers = scene.layers + newLayer))
        selectedId = newLayer.id
        tab = 0
        if (type == "image") pickImageFor(newLayer.id)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
        // ---------------- Header ----------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Done") }
            Spacer(Modifier.width(10.dp))
            Text(
                "Pro Editor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "preview par tap = select, drag = move",
                style = MaterialTheme.typography.bodySmall
            )
        }
        // Scene chips
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
            project.scenes.forEachIndexed { i, s ->
                FilterChip(
                    selected = i == sceneIdx,
                    onClick = {
                        sceneIdx = i
                        timeMs = 0L
                        selectedId = null
                    },
                    label = { Text(s.id) }
                )
                Spacer(Modifier.width(6.dp))
            }
        }

        // ---------------- Interactive preview ----------------
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val ratio = project.width.toFloat() / project.height.coerceAtLeast(1)
            val h = (maxWidth / ratio).coerceAtMost(330.dp)
            AndroidView(
                factory = { ctx -> ScenePlayerView(ctx) },
                update = { view ->
                    view.project = project
                    view.sceneOverride = sceneIdx
                    view.localTimeMs = timeMs
                    view.selectedLayerId = selectedId
                    view.onLayerTap = { id -> selectedId = id }
                    view.onLayerDrag = { id, nx, ny ->
                        updateLayer(id) {
                            it.copy(
                                x = nx.coerceIn(-it.w * 0.5f, project.width - it.w * 0.5f),
                                y = ny.coerceIn(-it.h * 0.5f, project.height - it.h * 0.5f)
                            )
                        }
                    }
                    view.invalidate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(h)
                    .heightIn(min = 150.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        }

        // ---------------- Transport + zoom ----------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Button(onClick = { playing = !playing }) {
                Text(if (playing) "Pause" else "Play")
            }
            Spacer(Modifier.width(8.dp))
            Text(
                String.format("%.1fs / %.1fs", timeMs / 1000.0, sceneDur / 1000.0),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.width(6.dp))
            Checkbox(checked = loop, onCheckedChange = { loop = it })
            Text("Loop", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { pxPerSec = (pxPerSec / 1.4f).coerceAtLeast(16f) }) {
                Text("−")
            }
            Spacer(Modifier.width(4.dp))
            Text("${pxPerSec.toInt()}px/s", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = { pxPerSec = (pxPerSec * 1.4f).coerceAtMost(320f) }) {
                Text("+")
            }
        }

        // ---------------- Timeline ----------------
        TimelineEditor(
            scene = scene,
            timeMs = timeMs,
            pxPerSec = pxPerSec,
            selectedId = selectedId,
            onSeek = { timeMs = it },
            onSelect = { selectedId = it },
            onTiming = { id, start, dur ->
                updateLayer(id) { it.copy(startMs = start, durationMs = dur) }
            }
        )

        // ---------------- Add layer ----------------
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Add: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { addLayer("text") }) { Text("+ Text") }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(onClick = { addLayer("code") }) { Text("+ Code") }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(onClick = { addLayer("card") }) { Text("+ Card") }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(onClick = { addLayer("image") }) { Text("+ Image") }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(onClick = { addLayer("highlight") }) { Text("+ Highlight") }
        }
        if (statusMsg != null) {
            Text(statusMsg!!, style = MaterialTheme.typography.bodySmall)
        }

        // ---------------- Property panel ----------------
        if (layer == null) {
            Text(
                "Koi layer select nahi. Timeline bar ya preview par tap karo — " +
                    "ya upar se naya layer add karo.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            // Scene controls are useful even without a selection.
            EditorSlider(
                label = "Scene duration",
                valueText = "${scene.durationMs}ms",
                value = scene.durationMs.toFloat(),
                range = 500f..15000f,
                onChange = { updateScene(scene.copy(durationMs = it.toInt())) }
            )
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("Content", "Transform", "Style", "Animation", "Layer").forEachIndexed { i, name ->
                    FilterChip(
                        selected = tab == i,
                        onClick = { tab = i },
                        label = { Text(name) }
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 6.dp)
            ) {
                when (tab) {
                    0 -> ContentTab(
                        layer = layer,
                        onUpdate = { newLayer -> updateLayer(layer.id) { newLayer } },
                        onPickImage = { pickImageFor(layer.id) }
                    )
                    1 -> TransformTab(
                        project = project,
                        layer = layer,
                        onUpdate = { newLayer -> updateLayer(layer.id) { newLayer } }
                    )
                    2 -> StyleTab(
                        layer = layer,
                        onUpdate = { newLayer -> updateLayer(layer.id) { newLayer } }
                    )
                    3 -> AnimationTab(
                        scene = scene,
                        layer = layer,
                        effects = effects,
                        onUpdate = { newLayer -> updateLayer(layer.id) { newLayer } }
                    )
                    else -> LayerTab(
                        scene = scene,
                        layer = layer,
                        selectedIndex = selectedIndex,
                        onUpdate = { newLayer -> updateLayer(layer.id) { newLayer } },
                        onUpdateScene = { updateScene(it) },
                        onSelect = { selectedId = it }
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ===============================================================
// Timeline: ruler + one track per layer. Bars: body drag = move
// startMs, left/right EDGE drag = trim. Playhead drags on ruler.
// ===============================================================

@Composable
private fun TimelineEditor(
    scene: Scene,
    timeMs: Long,
    pxPerSec: Float,
    selectedId: String?,
    onSeek: (Long) -> Unit,
    onSelect: (String) -> Unit,
    onTiming: (String, Int, Int) -> Unit
) {
    val density = LocalDensity.current
    val pxPerSecPx = with(density) { pxPerSec.dp.toPx() }
    val durationMs = scene.durationMs.coerceAtLeast(500)
    val contentWidthDp = (durationMs / 1000f * pxPerSec + 48f).dp
    val hScroll = rememberScrollState()
    val playheadDp = (timeMs / 1000f * pxPerSec).dp

    fun msToDp(ms: Int): androidx.compose.ui.unit.Dp = (ms / 1000f * pxPerSec).dp

    Box(
        Modifier
            .fillMaxWidth()
            .height(178.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF14181D))
            .verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.horizontalScroll(hScroll)) {
            Box(Modifier.width(contentWidthDp)) {
                Column {
                    // ---- Ruler (drag/tap to scrub) ----
                    Box(
                        Modifier
                            .width(contentWidthDp)
                            .height(24.dp)
                            .background(Color(0xFF1C2129))
                            .pointerInput(scene.id, durationMs, pxPerSecPx) {
                                detectTapGestures { pos ->
                                    onSeek(
                                        (pos.x / pxPerSecPx * 1000f).toLong()
                                            .coerceIn(0L, durationMs.toLong())
                                    )
                                }
                            }
                            .pointerInput(scene.id, durationMs, pxPerSecPx) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        onSeek(
                                            (pos.x / pxPerSecPx * 1000f).toLong()
                                                .coerceIn(0L, durationMs.toLong())
                                        )
                                    },
                                    onDrag = { change, _ ->
                                        onSeek(
                                            (change.position.x / pxPerSecPx * 1000f).toLong()
                                                .coerceIn(0L, durationMs.toLong())
                                        )
                                    }
                                )
                            }
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val totalSec = durationMs / 1000f
                            var s = 0f
                            val tickPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#9E9E9E")
                                textSize = 9.dp.toPx()
                            }
                            while (s <= totalSec + 0.001f) {
                                val x = s * pxPerSecPx
                                drawLine(
                                    Color(0xFF5A636E),
                                    Offset(x, size.height * 0.45f),
                                    Offset(x, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    "${s.toInt()}s", x + 3.dp.toPx(), 10.dp.toPx(), tickPaint
                                )
                                s += 1f
                            }
                        }
                    }
                    // ---- Tracks ----
                    if (scene.layers.isEmpty()) {
                        Text(
                            "Is scene me koi layer nahi — neeche se add karo.",
                            color = Color(0xFF9E9E9E),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    scene.layers.forEach { layer ->
                        TimelineTrack(
                            layer = layer,
                            sceneDurationMs = durationMs,
                            selected = layer.id == selectedId,
                            msToDp = { msToDp(it) },
                            pxPerSecPx = pxPerSecPx,
                            onSelect = { onSelect(layer.id) },
                            onTiming = { start, dur -> onTiming(layer.id, start, dur) }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                // ---- Playhead (over ruler + tracks) ----
                Box(
                    Modifier
                        .offset(x = playheadDp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color(0xFFFF5252))
                )
                Box(
                    Modifier
                        .offset(x = playheadDp - 5.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF5252))
                )
            }
        }
    }
}

@Composable
private fun TimelineTrack(
    layer: Layer,
    sceneDurationMs: Int,
    selected: Boolean,
    msToDp: (Int) -> androidx.compose.ui.unit.Dp,
    pxPerSecPx: Float,
    onSelect: () -> Unit,
    onTiming: (Int, Int) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(vertical = 2.dp)
            .background(Color(0xFF101317))
    ) {
        var mode by remember(layer.id) { mutableStateOf(0) } // 0 move, 1 trim-left, 2 trim-right
        var totalDx by remember(layer.id) { mutableStateOf(0f) }
        var origStart by remember(layer.id) { mutableStateOf(layer.startMs) }
        var origDur by remember(layer.id) { mutableStateOf(layer.durationMs) }
        val barWidth = msToDp(layer.durationMs).coerceAtLeast(10.dp)
        Box(
            Modifier
                .offset(x = msToDp(layer.startMs))
                .width(barWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(5.dp))
                .background(
                    typeColor(layer.type).copy(alpha = if (layer.hidden) 0.3f else 0.85f)
                )
                .then(
                    if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(5.dp))
                    else Modifier
                )
                .clickable { onSelect() }
                .pointerInput(layer.id, sceneDurationMs) {
                    val edgePx = 14.dp.toPx()
                    detectDragGestures(
                        onDragStart = { pos ->
                            origStart = layer.startMs
                            origDur = layer.durationMs
                            totalDx = 0f
                            mode = when {
                                pos.x < edgePx && size.width > edgePx * 2 -> 1
                                pos.x > size.width - edgePx && size.width > edgePx * 2 -> 2
                                else -> 0
                            }
                            onSelect()
                        },
                        onDrag = { _, dragAmount ->
                            totalDx += dragAmount.x
                            val dMs = (totalDx / pxPerSecPx * 1000f).toInt()
                            when (mode) {
                                1 -> { // trim left: start moves, end fixed
                                    val end = origStart + origDur
                                    val newStart = (origStart + dMs)
                                        .coerceIn(0, end - 100)
                                    onTiming(newStart, end - newStart)
                                }
                                2 -> { // trim right: duration grows/shrinks
                                    val newDur = (origDur + dMs)
                                        .coerceIn(100, sceneDurationMs - origStart)
                                    onTiming(origStart, newDur)
                                }
                                else -> { // move whole bar
                                    val newStart = (origStart + dMs)
                                        .coerceIn(0, sceneDurationMs - origDur)
                                    onTiming(newStart, origDur)
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val label = buildString {
                append(
                    layer.text.replace("\n", " ").take(14).ifEmpty { layer.type }
                )
                append(" • ")
                append(layer.effect)
                if (layer.locked) append(" 🔒")
                if (layer.hidden) append(" (hidden)")
            }
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

// ===============================================================
// Property tabs
// ===============================================================

@Composable
private fun ContentTab(
    layer: Layer,
    onUpdate: (Layer) -> Unit,
    onPickImage: () -> Unit
) {
    Text("Layer type", fontWeight = FontWeight.Bold)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        LAYER_TYPES.forEach { t ->
            FilterChip(
                selected = layer.type == t,
                onClick = { onUpdate(layer.copy(type = t)) },
                label = { Text(t) }
            )
            Spacer(Modifier.width(6.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = layer.text,
        onValueChange = { onUpdate(layer.copy(text = it)) },
        label = { Text("Text") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 1,
        maxLines = 3
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = layer.code,
        onValueChange = { onUpdate(layer.copy(code = it)) },
        label = { Text("Code (code layers ke liye)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 7
    )
    if (layer.type == "image") {
        Spacer(Modifier.height(10.dp))
        Text("Image", fontWeight = FontWeight.Bold)
        Text(
            if (layer.imagePath.isNullOrEmpty()) {
                "Koi image select nahi — placeholder dikhega."
            } else {
                "File: ${layer.imagePath.substringAfterLast("/")}"
            },
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(6.dp))
        Button(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
            Text(if (layer.imagePath.isNullOrEmpty()) "Upload Image" else "Replace Image")
        }
        Text(
            "Image app ke andar copy ho jati hai — project save karne par " +
                "us ka path project.json me rehta hai.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun TransformTab(
    project: Project,
    layer: Layer,
    onUpdate: (Layer) -> Unit
) {
    var scalePct by remember(layer.id) { mutableStateOf(100f) }
    EditorSlider(
        label = "X", valueText = "${layer.x.toInt()}",
        value = layer.x, range = 0f..project.width.toFloat(),
        onChange = { onUpdate(layer.copy(x = it)) }
    )
    EditorSlider(
        label = "Y", valueText = "${layer.y.toInt()}",
        value = layer.y, range = 0f..project.height.toFloat(),
        onChange = { onUpdate(layer.copy(y = it)) }
    )
    EditorSlider(
        label = "Width", valueText = "${layer.w.toInt()}",
        value = layer.w, range = 40f..project.width.toFloat(),
        onChange = { onUpdate(layer.copy(w = it)) }
    )
    EditorSlider(
        label = "Height", valueText = "${layer.h.toInt()}",
        value = layer.h, range = 40f..project.height.toFloat(),
        onChange = { onUpdate(layer.copy(h = it)) }
    )
    EditorSlider(
        label = "Scale (size %)", valueText = "${scalePct.toInt()}%",
        value = scalePct, range = 30f..250f,
        onChange = { newPct ->
            val ratio = (newPct / scalePct.coerceAtLeast(1f)).coerceIn(0.2f, 4f)
            scalePct = newPct
            val newW = (layer.w * ratio).coerceIn(20f, project.width * 1.5f)
            val newH = (layer.h * ratio).coerceIn(20f, project.height * 1.5f)
            onUpdate(
                layer.copy(
                    x = layer.x + (layer.w - newW) / 2f,
                    y = layer.y + (layer.h - newH) / 2f,
                    w = newW,
                    h = newH
                )
            )
        }
    )
    EditorSlider(
        label = "Opacity", valueText = "${(layer.opacity * 100).toInt()}%",
        value = layer.opacity * 100f, range = 0f..100f,
        onChange = { onUpdate(layer.copy(opacity = it / 100f)) }
    )
    EditorSlider(
        label = "Rotation", valueText = "${layer.rotation.toInt()}°",
        value = layer.rotation, range = -180f..180f,
        onChange = { onUpdate(layer.copy(rotation = it)) }
    )
}

@Composable
private fun StyleTab(layer: Layer, onUpdate: (Layer) -> Unit) {
    EditorSlider(
        label = "Font size", valueText = "${layer.fontSize.toInt()}",
        value = layer.fontSize, range = 20f..200f,
        onChange = { onUpdate(layer.copy(fontSize = it)) }
    )
    Spacer(Modifier.height(8.dp))
    ColorEditor(
        title = "Text color",
        hex = layer.color,
        allowTransparent = false,
        onChange = { onUpdate(layer.copy(color = it)) }
    )
    Spacer(Modifier.height(8.dp))
    ColorEditor(
        title = "Background (bg)",
        hex = layer.bg,
        allowTransparent = true,
        onChange = { onUpdate(layer.copy(bg = it)) }
    )
}

@Composable
private fun AnimationTab(
    scene: Scene,
    layer: Layer,
    effects: List<EffectDef>,
    onUpdate: (Layer) -> Unit
) {
    Text("Effect", fontWeight = FontWeight.Bold)
    val categories = effects.map { it.category }.distinct()
    if (categories.isEmpty()) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Renderer.ALL_EFFECT_IDS.forEach { id ->
                EffectChip(id, layer.effect) { onUpdate(layer.copy(effect = id)) }
            }
        }
    } else {
        categories.forEach { cat ->
            Text(cat, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                effects.filter { it.category == cat }.forEach { fx ->
                    EffectChip(fx.id, layer.effect) { onUpdate(layer.copy(effect = fx.id)) }
                }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Text("Easing (GSAP)", fontWeight = FontWeight.Bold)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        FilterChip(
            selected = layer.easing.isEmpty(),
            onClick = { onUpdate(layer.copy(easing = "")) },
            label = { Text("Default") }
        )
        Spacer(Modifier.width(6.dp))
        Easings.BY_NAME.keys.forEach { name ->
            FilterChip(
                selected = layer.easing == name,
                onClick = { onUpdate(layer.copy(easing = name)) },
                label = { Text(name) }
            )
            Spacer(Modifier.width(6.dp))
        }
    }
    Text(
        "Default = effect ki apni professional curve. Override sirf tab jab " +
            "khaas feel chahiye.",
        style = MaterialTheme.typography.bodySmall
    )

    if (layer.effect in SPRING_EFFECTS) {
        Spacer(Modifier.height(10.dp))
        Text("Spring profile", fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(
                selected = layer.spring.isEmpty(),
                onClick = { onUpdate(layer.copy(spring = "")) },
                label = { Text("Default") }
            )
            Spacer(Modifier.width(6.dp))
            listOf("smooth", "snappy", "bouncy").forEach { prof ->
                FilterChip(
                    selected = layer.spring == prof,
                    onClick = { onUpdate(layer.copy(spring = prof)) },
                    label = { Text(prof) }
                )
                Spacer(Modifier.width(6.dp))
            }
        }
    }

    if (layer.effect in STAGGER_EFFECTS) {
        Spacer(Modifier.height(10.dp))
        Text("Stagger (har unit ke darmiyan delay)", fontWeight = FontWeight.Bold)
        EditorSlider(
            label = "Stagger",
            valueText = if (layer.staggerMs < 0) "Default" else "${layer.staggerMs}ms",
            value = layer.staggerMs.toFloat().coerceAtLeast(-1f),
            range = -1f..400f,
            onChange = { onUpdate(layer.copy(staggerMs = it.toInt())) }
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("" to "From: default", "start" to "start", "center" to "center",
                "end" to "end", "edges" to "edges").forEach { (value, labelText) ->
                FilterChip(
                    selected = layer.staggerFrom == value,
                    onClick = { onUpdate(layer.copy(staggerFrom = value)) },
                    label = { Text(labelText) }
                )
                Spacer(Modifier.width(6.dp))
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Text("Timing", fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Start", modifier = Modifier.width(52.dp), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = {
            onUpdate(layer.copy(startMs = (layer.startMs - 100).coerceAtLeast(0)))
        }) { Text("−100") }
        Spacer(Modifier.width(6.dp))
        Text("${layer.startMs}ms", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = {
            onUpdate(layer.copy(startMs = (layer.startMs + 100).coerceAtMost(scene.durationMs - 100)))
        }) { Text("+100") }
    }
    EditorSlider(
        label = "Start",
        valueText = "${layer.startMs}ms",
        value = layer.startMs.toFloat(),
        range = 0f..scene.durationMs.toFloat().coerceAtLeast(100f),
        onChange = { onUpdate(layer.copy(startMs = it.toInt())) }
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Anim", modifier = Modifier.width(52.dp), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = {
            onUpdate(layer.copy(durationMs = (layer.durationMs - 100).coerceAtLeast(100)))
        }) { Text("−100") }
        Spacer(Modifier.width(6.dp))
        Text("${layer.durationMs}ms", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = {
            onUpdate(layer.copy(durationMs = (layer.durationMs + 100).coerceAtMost(8000)))
        }) { Text("+100") }
    }
    EditorSlider(
        label = "Duration (animation)",
        valueText = "${layer.durationMs}ms",
        value = layer.durationMs.toFloat(),
        range = 100f..8000f,
        onChange = { onUpdate(layer.copy(durationMs = it.toInt())) }
    )
}

@Composable
private fun LayerTab(
    scene: Scene,
    layer: Layer,
    selectedIndex: Int,
    onUpdate: (Layer) -> Unit,
    onUpdateScene: (Scene) -> Unit,
    onSelect: (String?) -> Unit
) {
    Text("Layer: ${layer.id} (${selectedIndex + 1}/${scene.layers.size})",
        style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = layer.locked, onCheckedChange = { onUpdate(layer.copy(locked = it)) })
        Text("Lock (select/drag band)")
        Spacer(Modifier.width(12.dp))
        Checkbox(checked = layer.hidden, onCheckedChange = { onUpdate(layer.copy(hidden = it)) })
        Text("Hide (render nahi hoga)")
    }
    Spacer(Modifier.height(8.dp))
    Row {
        OutlinedButton(onClick = {
            val copyLayer = layer.copy(id = uniqueLayerId(scene, layer.id))
            val newLayers = scene.layers.toMutableList().apply {
                add(selectedIndex + 1, copyLayer)
            }
            onUpdateScene(scene.copy(layers = newLayers))
            onSelect(copyLayer.id)
        }) { Text("Duplicate") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = {
                if (selectedIndex > 0) {
                    val newLayers = scene.layers.toMutableList()
                    val tmp = newLayers[selectedIndex - 1]
                    newLayers[selectedIndex - 1] = newLayers[selectedIndex]
                    newLayers[selectedIndex] = tmp
                    onUpdateScene(scene.copy(layers = newLayers))
                }
            },
            enabled = selectedIndex > 0
        ) { Text("Move down ⬇") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = {
                if (selectedIndex < scene.layers.size - 1) {
                    val newLayers = scene.layers.toMutableList()
                    val tmp = newLayers[selectedIndex + 1]
                    newLayers[selectedIndex + 1] = newLayers[selectedIndex]
                    newLayers[selectedIndex] = tmp
                    onUpdateScene(scene.copy(layers = newLayers))
                }
            },
            enabled = selectedIndex < scene.layers.size - 1
        ) { Text("Move up ⬆") }
    }
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = {
            val newLayers = scene.layers.toMutableList().apply { removeAt(selectedIndex) }
            onUpdateScene(scene.copy(layers = newLayers))
            onSelect(null)
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Delete layer") }

    Spacer(Modifier.height(16.dp))
    Text("Scene", fontWeight = FontWeight.Bold)
    EditorSlider(
        label = "Scene duration",
        valueText = "${scene.durationMs}ms",
        value = scene.durationMs.toFloat(),
        range = 500f..15000f,
        onChange = { onUpdateScene(scene.copy(durationMs = it.toInt())) }
    )
}

// ===============================================================
// Shared editor controls
// ===============================================================

private fun uniqueLayerId(scene: Scene, base: String): String {
    var candidate = "$base-copy"
    var n = 2
    val ids = scene.layers.map { it.id }.toSet()
    while (ids.contains(candidate)) {
        candidate = "$base-copy$n"
        n++
    }
    return candidate
}

private fun defaultLayerAt(project: Project, scene: Scene, type: String, startMs: Int): Layer {
    val base = System.currentTimeMillis() % 100000L
    var id = "layer-$base"
    val ids = scene.layers.map { it.id }.toSet()
    if (ids.contains(id)) id = "layer-$base-x"
    val w = project.width.toFloat()
    val h = project.height.toFloat()
    val start = startMs.coerceIn(0, (scene.durationMs - 100).coerceAtLeast(0))
    return when (type) {
        "code" -> Layer(
            id = id, type = "code", effect = "code-typewriter",
            startMs = start, durationMs = 2000,
            x = w * 0.05f, y = h * 0.25f, w = w * 0.9f, h = h * 0.3f,
            text = "", code = "fun main() {\n  println(\"Code Urdu\")\n}",
            color = "#9CDCFE", bg = "#1E1E2E", fontSize = 44f
        )
        "card" -> Layer(
            id = id, type = "card", effect = "slide-card",
            startMs = start, durationMs = 700,
            x = w * 0.08f, y = h * 0.55f, w = w * 0.84f, h = h * 0.12f,
            text = "New Card", code = "",
            color = "#FFFFFF", bg = "#1E88E5", fontSize = 56f
        )
        "highlight" -> Layer(
            id = id, type = "highlight", effect = "circle-marker",
            startMs = start, durationMs = 900,
            x = w * 0.25f, y = h * 0.4f, w = w * 0.5f, h = h * 0.08f,
            text = "subscribe()", code = "",
            color = "#FFEB3B", bg = "#00000000", fontSize = 48f
        )
        "image" -> Layer(
            id = id, type = "image", effect = "fade",
            startMs = start, durationMs = 800,
            x = w * 0.1f, y = h * 0.3f, w = w * 0.8f, h = h * 0.3f,
            text = "IMAGE", code = "",
            color = "#FFFFFF", bg = "#00000000", fontSize = 48f
        )
        else -> Layer(
            id = id, type = "text", effect = "pop-in",
            startMs = start, durationMs = 600,
            x = w * 0.05f, y = h * 0.35f, w = w * 0.9f, h = h * 0.12f,
            text = "New Text", code = "",
            color = "#FFFFFF", bg = "#00000000", fontSize = 72f
        )
    }
}

@Composable
private fun EffectChip(id: String, currentEffect: String, onPick: () -> Unit) {
    FilterChip(
        selected = currentEffect == id,
        onClick = onPick,
        label = { Text(id) }
    )
    Spacer(Modifier.width(6.dp))
}

@Composable
private fun EditorSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text(valueText, modifier = Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ColorEditor(
    title: String,
    hex: String,
    allowTransparent: Boolean,
    onChange: (String) -> Unit
) {
    var hexText by remember(hex) { mutableStateOf(hex) }
    Text(title, fontWeight = FontWeight.Bold)
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (allowTransparent) {
            ColorSwatch(
                label = "None",
                color = null,
                selected = hex.equals(TRANSPARENT_HEX, ignoreCase = true),
                onClick = { onChange(TRANSPARENT_HEX) }
            )
        }
        COLOR_PRESETS.forEach { (_, presetHex) ->
            ColorSwatch(
                label = presetHex,
                color = Color(parseColor(presetHex)),
                selected = hex.equals(presetHex, ignoreCase = true),
                onClick = { onChange(presetHex) }
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = hexText,
            onValueChange = { hexText = it },
            label = { Text("Hex (#RRGGBB ya #AARRGGBB)") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = {
            val candidate = hexText.trim()
            val valid = candidate.matches(Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?"))
            if (valid) onChange(candidate.uppercase())
        }) { Text("Set") }
    }
}

@Composable
private fun ColorSwatch(
    label: String,
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(color ?: Color(0xFF444444))
                .then(
                    if (selected) Modifier.border(3.dp, Color.White, CircleShape)
                    else Modifier.border(1.dp, Color(0xFF888888), CircleShape)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (color == null) {
                Text("✕", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
