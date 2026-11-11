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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Live looping preview of one catalog effect (~2s loop).
 * The gallery is allowed to loop (it is a showcase); the REAL renderer is
 * strictly playhead-deterministic. All motion here runs through the same
 * [Easings] / [Springs] / [Stagger] engines the renderer uses — no local
 * ad-hoc curves — so what the gallery shows is the feel the export gets.
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

/** Fade in->out triangle driven by sine.inOut (gallery loop helper). */
private fun fadeTriangle(p: Float): Float =
    if (p < 0.5f) Easings.sineInOut(p * 2f) else 1f - Easings.sineInOut((p - 0.5f) * 2f)

@Composable
private fun TextSample(id: String, p: Float) {
    when (id) {
        "typewriter" -> {
            val full = "Code Urdu"
            val n = (p * (full.length + 2)).toInt().coerceIn(0, full.length)
            val caret = if ((p * 4f).toInt() % 2 == 0) "▌" else ""
            Text(
                full.substring(0, n) + if (n < full.length) caret else "",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        "word-reveal" -> {
            val words = listOf("Code", "Urdu", "Seekho")
            Row {
                words.forEachIndexed { i, word ->
                    val local = Stagger.local(p, i, words.size, 0.6f, Stagger.FROM_START)
                    Text(
                        word + if (i < words.size - 1) " " else "",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer {
                            alpha = local.coerceIn(0f, 1f)
                            translationY = (1f - Easings.power2Out(local)) * 14f
                        }
                    )
                }
            }
        }

        "char-stagger-in" -> {
            val chars = "CODE"
            Row {
                chars.forEachIndexed { i, c ->
                    val local = Stagger.local(p, i, chars.length, 0.35f, Stagger.FROM_CENTER)
                    val s = Easings.backOut(local).coerceAtLeast(0.01f)
                    Text(
                        c.toString(),
                        color = Color(0xFFFFD54F),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer {
                            alpha = local.coerceIn(0f, 1f)
                            scaleX = s
                            scaleY = s
                        }
                    )
                }
            }
        }

        "keyword-pulse" -> {
            // ONE spring pulse per loop (renderer: pulse then rest).
            val scale = 1f + 0.30f * Springs.pulse(p * 2f)
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

        "highlight-sweep" -> {
            val sweep = Easings.power2InOut((p / 0.45f).coerceIn(0f, 1f))
            Box(contentAlignment = Alignment.CenterStart) {
                Box(
                    Modifier
                        .width((150f * sweep).dp)
                        .height(26.dp)
                        .background(Color(0xFFFFEB3B).copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                )
                Text(
                    "Code Urdu",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }

        "underline-draw" -> {
            val q = Easings.power2Out((p / 0.45f).coerceIn(0f, 1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Code Urdu", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .width((130f * q).dp)
                        .height(3.dp)
                        .background(Color(0xFFFFEB3B), RoundedCornerShape(2.dp))
                )
            }
        }

        "counter-up" -> {
            val v = (10000f * Easings.power3Out(p)).roundToInt()
            Text(
                String.format("%,d", v) + " Subs",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        "glow-pulse" -> {
            val glow = 0.25f + 0.75f * Springs.pulse((p * 2f) % 1.6f)
            Text(
                "FREE",
                color = Color(0xFFFFEB3B),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0xFFFFEB3B),
                        offset = Offset.Zero,
                        blurRadius = 26f * glow
                    )
                )
            )
        }

        "slide-up" -> {
            val q = Easings.power3Out((p / 0.35f).coerceIn(0f, 1f))
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
                modifier = Modifier.graphicsLayer { alpha = fadeTriangle(p) }
            )
        }

        else -> { // pop-in: true spring (bouncy profile), 2s loop time
            val tSec = p * 2f
            val scale = Springs.atProfile("bouncy", tSec).coerceAtLeast(0.01f)
            Text(
                "Code Urdu",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    alpha = (tSec * 4f).coerceIn(0f, 1f)
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
            // Band glides between lines (power2.inOut), no jumps.
            val pos = Easings.power2InOut(p) * (lines.size - 1)
            val active = pos.roundToInt().coerceIn(0, lines.size - 1)
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
            val scale = 1f + 0.35f * Easings.expoOut((p * 1.4f).coerceIn(0f, 1f))
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
            val q = Easings.power3Out((p / 0.30f).coerceIn(0f, 1f))
            SampleCard(
                Modifier.graphicsLayer {
                    alpha = q
                    translationX = (1f - q) * -160f
                    rotationZ = (1f - q) * -2.5f
                }
            )
        }

        "flip-in" -> {
            val q = Easings.power2InOut((p / 0.35f).coerceIn(0f, 1f))
            SampleCard(
                Modifier.graphicsLayer {
                    rotationY = (1f - q) * 80f
                    cameraDistance = 12f * density
                }
            )
        }

        "bounce-drop" -> {
            val q = Easings.bounceOut((p / 0.40f).coerceIn(0f, 1f))
            SampleCard(
                Modifier.graphicsLayer { translationY = (1f - q) * -80f }
            )
        }

        "elastic-snap" -> {
            val q = Easings.elasticOut((p / 0.55f).coerceIn(0f, 1f))
            val s = (0.3f + 0.7f * q).coerceAtLeast(0.05f)
            SampleCard(
                Modifier.graphicsLayer {
                    scaleX = s
                    scaleY = s
                }
            )
        }

        "box-marker" -> {
            Box(contentAlignment = Alignment.Center) {
                Text("FREE", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Canvas(Modifier.size(width = 110.dp, height = 48.dp)) {
                    val perim = 2f * (size.width + size.height)
                    val drawn = perim * Easings.power2Out((p / 0.5f).coerceIn(0f, 1f))
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(
                        path,
                        color = Color(0xFFFFEB3B),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(drawn, perim), 0f
                            )
                        )
                    )
                }
            }
        }

        "path-arrow" -> {
            Canvas(Modifier.fillMaxSize()) {
                val sx = size.width * 0.12f
                val sy = size.height * 0.85f
                val cx = size.width * 0.45f
                val cy = -size.height * 0.25f
                val ex = size.width * 0.88f
                val ey = size.height * 0.2f
                // Faint full curve.
                val full = Path().apply {
                    moveTo(sx, sy)
                    quadraticBezierTo(cx, cy, ex, ey)
                }
                drawPath(full, color = Color(0xFFFFEB3B).copy(alpha = 0.25f), style = Stroke(width = 3.dp.toPx()))
                // Moving head along the curve (MotionPath autoRotate model).
                val t = Easings.power1InOut(p)
                val mt = 1f - t
                val hx = mt * mt * sx + 2f * mt * t * cx + t * t * ex
                val hy = mt * mt * sy + 2f * mt * t * cy + t * t * ey
                val tx = 2f * mt * (cx - sx) + 2f * t * (ex - cx)
                val ty = 2f * mt * (cy - sy) + 2f * t * (ey - cy)
                val ang = kotlin.math.atan2(ty, tx)
                val head = 11.dp.toPx()
                for (sign in floatArrayOf(1f, -1f)) {
                    val a = ang + Math.PI.toFloat() * 0.82f * sign
                    drawLine(
                        Color(0xFFFFEB3B),
                        Offset(hx, hy),
                        Offset(hx + cos(a) * head, hy + sin(a) * head),
                        strokeWidth = 3.dp.toPx()
                    )
                }
                drawCircle(Color(0xFFFFEB3B), radius = 3.5.dp.toPx(), center = Offset(hx, hy))
            }
        }

        "draw-arrow" -> {
            Box(Modifier.fillMaxSize()) {
                SampleCard(Modifier.align(Alignment.BottomStart))
                Canvas(Modifier.fillMaxSize()) {
                    val q = Easings.power1InOut(p)
                    val start = Offset(size.width * 0.15f, size.height * 0.45f)
                    val endFull = Offset(size.width * 0.85f, size.height * 0.12f)
                    val end = Offset(
                        start.x + (endFull.x - start.x) * q,
                        start.y + (endFull.y - start.y) * q
                    )
                    drawLine(Color(0xFFFFEB3B), start, end, strokeWidth = 3.dp.toPx())
                    if (q > 0.05f) {
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
                        sweepAngle = 360f * Easings.power2Out(p),
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
                val s = 1f + 0.45f * Easings.sineInOut((p * 1.4f).coerceIn(0f, 1f))
                scaleX = s
                scaleY = s
            }
        )

        "pan" -> content(
            Modifier.graphicsLayer {
                translationX = -30f + 60f * Easings.sineInOut(p)
            }
        )

        "impact-shake" -> {
            // Deterministic decaying wiggle — same curve as the renderer.
            val q = (p / 0.45f).coerceIn(0f, 1f)
            val w = if (q < 1f) Renderer.wiggle(q) else (0f to 0f)
            content(
                Modifier.graphicsLayer {
                    translationX = w.first * 0.5f
                    translationY = w.second * 0.5f
                }
            )
        }

        "whoosh" -> {
            val q = p * 2f
            val dx = if (q < 1f) {
                Easings.expoIn(q) * 260f
            } else {
                -260f + Easings.expoOut(q - 1f) * 260f
            }
            content(Modifier.graphicsLayer { translationX = dx })
        }

        "fade" -> content(
            Modifier.graphicsLayer { alpha = fadeTriangle(p) }
        )

        else -> content(Modifier)
    }
}
