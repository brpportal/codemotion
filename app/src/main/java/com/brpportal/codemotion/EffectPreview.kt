package com.brpportal.codemotion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sin

private fun easeOut(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return 1f - (1f - c) * (1f - c)
}

private fun easeOutBack(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val u = c - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}

/**
 * Live looping preview of one catalog effect (~2s loop).
 * Rendered generically by sampleKind; this is the seed of the Phase 2 renderer.
 */
@Composable
fun EffectPreview(effect: EffectDef, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "fx-${effect.id}")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF101418))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        when (effect.sampleKind) {
            "code" -> CodeSample(effect.id, progress)
            "card" -> CardSample(effect.id, progress)
            "scene" -> SceneSample(effect.id, progress)
            else -> TextSample(effect.id, progress)
        }
    }
}

@Composable
private fun TextSample(id: String, p: Float) {
    when (id) {
        "typewriter" -> {
            val full = "Code Urdu"
            val n = (p * (full.length + 2)).toInt().coerceIn(0, full.length)
            Text(
                full.substring(0, n),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        "word-reveal" -> {
            val words = listOf("Code", "Urdu", "Seekho")
            val visible = ((p * 3f).toInt() + 1).coerceIn(1, 3)
            Text(
                words.take(visible).joinToString(" "),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        "keyword-pulse" -> {
            val scale = 1f + 0.30f * abs(sin(p * Math.PI.toFloat() * 2f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Code Urdu ", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "FREE",
                    color = Color(0xFFFFD54F),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                )
            }
        }

        "slide-up" -> {
            val q = easeOut(p / 0.35f)
            Text(
                "Code Urdu",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    alpha = q
                    translationY = (1f - q) * 60f
                }
            )
        }

        "fade" -> {
            Text(
                "Code Urdu",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { alpha = sin(p * Math.PI.toFloat()) }
            )
        }

        else -> { // pop-in and default
            val q = p / 0.30f
            val scale = if (q < 1f) easeOutBack(q) else 1f
            Text(
                "Code Urdu",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    alpha = q.coerceIn(0f, 1f)
                    scaleX = scale
                    scaleY = scale
                }
            )
        }
    }
}

@Composable
private fun CodeSample(id: String, p: Float) {
    val lines = listOf(
        "fun main() {",
        "  val app = \"CodeUrdu\"",
        "  println(app)",
        "}"
    )
    when (id) {
        "code-typewriter" -> {
            val full = lines.joinToString("\n")
            val n = (p * (full.length + 6)).toInt().coerceIn(0, full.length)
            Text(
                full.substring(0, n),
                color = Color(0xFF9CDCFE),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        "line-highlight" -> {
            val active = (p * lines.size).toInt().coerceIn(0, lines.size - 1)
            Column {
                lines.forEachIndexed { i, line ->
                    Text(
                        line,
                        color = if (i == active) Color(0xFF111111) else Color(0xFF9CDCFE),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (i == active) Color(0xFFFFD54F) else Color.Transparent,
                                RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        "zoom-to-line" -> {
            val scale = 1f + 0.35f * easeOut(p * 1.4f)
            Column(
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            ) {
                lines.forEachIndexed { i, line ->
                    Text(
                        line,
                        color = if (i == 1) Color(0xFFFFD54F) else Color(0xFF9CDCFE),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        else -> Column {
            lines.forEach { Text(it, color = Color(0xFF9CDCFE), fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
        }
    }
}

@Composable
private fun SampleCard(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth(0.85f)
            .height(52.dp)
            .background(Color(0xFF1E88E5), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("Code Urdu Card", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CardSample(id: String, p: Float) {
    when (id) {
        "slide-card" -> {
            val q = easeOut(p / 0.30f)
            SampleCard(
                Modifier.graphicsLayer {
                    alpha = q
                    translationX = (1f - q) * -160f
                }
            )
        }

        "flip-in" -> {
            val q = easeOut(p / 0.35f)
            SampleCard(
                Modifier.graphicsLayer {
                    rotationY = (1f - q) * 80f
                    cameraDistance = 12f * density
                }
            )
        }

        "draw-arrow" -> {
            Box(Modifier.fillMaxSize()) {
                SampleCard(Modifier.align(Alignment.BottomStart))
                Canvas(Modifier.fillMaxSize()) {
                    val start = Offset(size.width * 0.15f, size.height * 0.45f)
                    val endFull = Offset(size.width * 0.85f, size.height * 0.12f)
                    val end = Offset(
                        start.x + (endFull.x - start.x) * p,
                        start.y + (endFull.y - start.y) * p
                    )
                    drawLine(Color(0xFFFFEB3B), start, end, strokeWidth = 3.dp.toPx())
                    if (p > 0.05f) {
                        drawLine(
                            Color(0xFFFFEB3B), end,
                            Offset(end.x - 12.dp.toPx(), end.y + 2.dp.toPx()), 3.dp.toPx()
                        )
                        drawLine(
                            Color(0xFFFFEB3B), end,
                            Offset(end.x - 2.dp.toPx(), end.y + 12.dp.toPx()), 3.dp.toPx()
                        )
                    }
                }
            }
        }

        "circle-marker" -> {
            Box(contentAlignment = Alignment.Center) {
                Text("subscribe()", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Canvas(Modifier.size(width = 150.dp, height = 56.dp)) {
                    drawArc(
                        color = Color(0xFFFFEB3B),
                        startAngle = -90f,
                        sweepAngle = 360f * p,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }

        else -> SampleCard()
    }
}

@Composable
private fun SceneSample(id: String, p: Float) {
    val content: @Composable (Modifier) -> Unit = { mod ->
        Box(
            mod
                .fillMaxWidth(0.8f)
                .height(56.dp)
                .background(Color(0xFF37474F), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Scene", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
    when (id) {
        "push-in" -> content(
            Modifier.graphicsLayer {
                val s = 1f + 0.45f * p
                scaleX = s
                scaleY = s
            }
        )

        "pan" -> content(
            Modifier.graphicsLayer { translationX = -30f + 60f * p }
        )

        "impact-shake" -> {
            val dx = if (p in 0.30f..0.55f) sin(p * 260f) * 7f else 0f
            content(Modifier.graphicsLayer { translationX = dx })
        }

        "whoosh" -> {
            val q = p * 2f
            val dx = if (q < 1f) {
                val e = q * q
                e * 260f
            } else {
                val e = easeOut(q - 1f)
                -260f + e * 260f
            }
            content(Modifier.graphicsLayer { translationX = dx })
        }

        "fade" -> content(
            Modifier.graphicsLayer { alpha = sin(p * Math.PI.toFloat()) }
        )

        else -> content(Modifier)
    }
}
