package com.brpportal.codemotion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProjectViewer(project: Project, onBack: () -> Unit) {
    var sceneIndex by remember { mutableStateOf(0) }
    val safeIndex = sceneIndex.coerceIn(0, (project.scenes.size - 1).coerceAtLeast(0))

    Column(
        Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row {
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Text(
                "Project Viewer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Canvas: ${project.width}x${project.height} • ${project.fps} fps • ${project.scenes.size} scenes",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))

        Row(Modifier.horizontalScroll(rememberScrollState())) {
            project.scenes.forEachIndexed { i, scene ->
                FilterChip(
                    selected = i == safeIndex,
                    onClick = { sceneIndex = i },
                    label = { Text(scene.id) }
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(10.dp))

        if (project.scenes.isNotEmpty()) {
            val scene = project.scenes[safeIndex]
            Text(
                "Scene: ${scene.id} — ${scene.durationMs}ms — ${scene.layers.size} layers",
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            StaticScenePreview(
                scene = scene,
                canvasW = project.width,
                canvasH = project.height,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            scene.layers.forEach { layer ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "${layer.type} • ${layer.effect}",
                            fontWeight = FontWeight.Bold
                        )
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
                    }
                }
            }
        } else {
            Text("Is project me koi scene nahi hai.")
        }
    }
}

/** First-frame static preview: every layer drawn at its x/y/w/h with its colors. */
@Composable
fun StaticScenePreview(
    scene: Scene,
    canvasW: Int,
    canvasH: Int,
    modifier: Modifier = Modifier
) {
    val safeW = canvasW.coerceAtLeast(1)
    val safeH = canvasH.coerceAtLeast(1)
    val ratio = safeW.toFloat() / safeH.toFloat()
    BoxWithConstraints(
        modifier
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0B0E11))
    ) {
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        scene.layers.forEach { layer ->
            val left = (layer.x / safeW) * wDp
            val top = (layer.y / safeH) * hDp
            val lw = (layer.w / safeW) * wDp
            val lh = (layer.h / safeH) * hDp
            val fs = (layer.fontSize * wDp / safeW).coerceIn(7f, 40f)
            Box(
                Modifier
                    .offset(x = left.dp, y = top.dp)
                    .width(lw.dp)
                    .height(lh.dp)
                    .background(layerBg(layer), RoundedCornerShape(4.dp))
                    .then(
                        if (layer.type == "highlight") {
                            Modifier.border(2.dp, Color(0xFFFFEB3B), RoundedCornerShape(4.dp))
                        } else {
                            Modifier
                        }
                    )
            ) {
                Column(Modifier.padding(3.dp)) {
                    Text(
                        layer.effect,
                        fontSize = 8.sp,
                        color = Color.White,
                        modifier = Modifier
                            .background(Color(0x99000000), RoundedCornerShape(3.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                    when (layer.type) {
                        "code" -> Text(
                            layer.code,
                            fontSize = fs.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(parseColor(layer.color)),
                            maxLines = 6
                        )

                        else -> if (layer.text.isNotEmpty()) {
                            Text(
                                layer.text,
                                fontSize = fs.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(parseColor(layer.color)),
                                maxLines = 3
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun layerBg(layer: Layer): Color {
    val bg = parseColor(layer.bg, 0x00000000L)
    if (bg != 0x00000000L) return Color(bg)
    return when (layer.type) {
        "card" -> Color(0xFF1E88E5)
        "code" -> Color(0xFF1E1E1E)
        else -> Color.Transparent
    }
}
