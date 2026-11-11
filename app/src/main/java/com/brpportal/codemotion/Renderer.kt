package com.brpportal.codemotion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Shared scene renderer — pure android.graphics.Canvas drawing.
 * Used by BOTH the animated preview (PlayerScreen in ProjectViewer) and the
 * MP4 exporter, so what you preview is exactly what gets exported.
 *
 * Animation model (GSAP/Remotion-informed):
 * - A layer appears at startMs; its effect animates over durationMs with a
 *   named easing from [Easings]. A layer's own `easing` field (FORMAT_SPEC
 *   v2) overrides the effect's default curve.
 * - After the entrance animation completes, the layer stays at its final
 *   state until the scene ends.
 * - pop-in settles with a true spring ([Springs], profile from the layer's
 *   `spring` field, default bouncy); keyword-pulse is ONE spring pulse,
 *   never a looping sine. Camera push-in / pan use sine.inOut.
 * - word-reveal / code-typewriter / char-stagger-in use the GSAP stagger
 *   model ([Stagger]: each/amount + from start/center/end/edges), with the
 *   per-layer `stagger` override when present.
 * - impact-shake is a deterministic decaying wiggle (CustomWiggle model) —
 *   no random, no wall-clock: every value is a pure function of the
 *   playhead, so preview and export are frame-identical.
 * - Hidden layers never render; layer opacity and rotation always apply.
 */
object Renderer {

    private const val DEFAULT_BG_HEX = "#0B0B10"
    private const val CODE_BG_HEX = "#1E1E2E"
    private const val CARD_BG_HEX = "#1E88E5"

    val CAMERA_EFFECTS: Set<String> =
        setOf("push-in", "pan", "impact-shake", "whoosh", "fade")

    /** All 26 catalog effect IDs (fallback lists / tooling). */
    val ALL_EFFECT_IDS: List<String> = listOf(
        "pop-in", "slide-up", "typewriter", "word-reveal", "keyword-pulse",
        "char-stagger-in", "highlight-sweep", "underline-draw", "counter-up",
        "code-typewriter", "line-highlight", "zoom-to-line",
        "slide-card", "flip-in", "draw-arrow", "circle-marker",
        "box-marker", "path-arrow", "bounce-drop", "elastic-snap",
        "push-in", "pan", "impact-shake", "whoosh", "fade",
        "glow-pulse"
    )

    // ---------------------------------------------------------------
    // Timeline helpers (used by the player and the exporter)
    // ---------------------------------------------------------------

    fun totalDurationMs(project: Project): Long =
        project.scenes.sumOf { it.durationMs.toLong() }

    fun sceneStartMs(project: Project, sceneIndex: Int): Long {
        var acc = 0L
        for (i in 0 until min(sceneIndex, project.scenes.size)) {
            acc += project.scenes[i].durationMs
        }
        return acc
    }

    /** For an absolute project time, returns (sceneIndex, scene-local timeMs). */
    fun locate(project: Project, timeMs: Long): Pair<Int, Long> {
        if (project.scenes.isEmpty()) return 0 to 0L
        var acc = 0L
        for (i in project.scenes.indices) {
            val dur = project.scenes[i].durationMs.toLong()
            if (timeMs < acc + dur) return i to (timeMs - acc)
            acc += dur
        }
        val last = project.scenes.size - 1
        return last to project.scenes[last].durationMs.toLong()
    }

    // ---------------------------------------------------------------
    // Editor support: coordinate mapping, hit test, selection outline
    // ---------------------------------------------------------------

    /** View px -> project canvas coords (inverse of drawScene's letterbox). */
    fun viewToProject(
        project: Project, viewW: Int, viewH: Int, vx: Float, vy: Float
    ): FloatArray? {
        if (viewW <= 0 || viewH <= 0) return null
        val projW = max(1, project.width).toFloat()
        val projH = max(1, project.height).toFloat()
        val scale = min(viewW / projW, viewH / projH)
        if (scale <= 0f) return null
        val ox = (viewW - projW * scale) / 2f
        val oy = (viewH - projH * scale) / 2f
        val px = (vx - ox) / scale
        val py = (vy - oy) / scale
        if (px < 0f || py < 0f || px > projW || py > projH) return null
        return floatArrayOf(px, py)
    }

    /**
     * Topmost layer under project point (px, py) at [timeMs] — visible,
     * started, not hidden, not locked. Uses the layer's base bounds
     * (entrance offsets ignored; the editor selects what you see placed).
     */
    fun hitLayer(project: Project, scene: Scene, timeMs: Long, px: Float, py: Float): Layer? {
        for (i in scene.layers.indices.reversed()) {
            val l = scene.layers[i]
            if (l.hidden || l.locked || timeMs < l.startMs) continue
            if (px >= l.x && px <= l.x + l.w && py >= l.y && py <= l.y + l.h) return l
        }
        return null
    }

    /** Dashed selection outline around [layer], drawn in view space. */
    fun drawSelectionOutline(canvas: Canvas, project: Project, layer: Layer) {
        val targetW = canvas.width
        val targetH = canvas.height
        if (targetW <= 0 || targetH <= 0) return
        val projW = max(1, project.width).toFloat()
        val projH = max(1, project.height).toFloat()
        val scale = min(targetW / projW, targetH / projH)
        val ox = (targetW - projW * scale) / 2f
        val oy = (targetH - projH * scale) / 2f
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(scale, scale)
        val rect = RectF(layer.x - 6f, layer.y - 6f, layer.x + layer.w + 6f, layer.y + layer.h + 6f)
        val outline = Paint().apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#FFD54F")
            strokeWidth = 4f / scale
            pathEffect = DashPathEffect(floatArrayOf(18f / scale, 12f / scale), 0f)
            isAntiAlias = true
        }
        canvas.drawRect(rect, outline)
        val dot = Paint().apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FFD54F")
            isAntiAlias = true
        }
        val r = 11f / scale
        for ((cx, cy) in listOf(
            rect.left to rect.top, rect.right to rect.top,
            rect.left to rect.bottom, rect.right to rect.bottom
        )) {
            canvas.drawCircle(cx, cy, r, dot)
        }
        canvas.restore()
    }

    /**
     * Deterministic decaying wiggle (GSAP CustomWiggle model): ~2.5
     * oscillations whose amplitude decays quadratically. Pure function of
     * progress — same frame, same shake, in preview and in export.
     */
    fun wiggle(p: Float): Pair<Float, Float> {
        val decay = (1f - p) * (1f - p)
        val dx = 18f * decay * sin(p * PI.toFloat() * 5f)
        val dy = 13f * decay * sin(p * PI.toFloat() * 4f + 1.3f)
        return dx to dy
    }

    /** The layer's own easing override, or [default] when unset/unknown. */
    fun easeFor(layer: Layer, default: (Float) -> Float): (Float) -> Float =
        if (layer.easing.isNotEmpty() && Easings.BY_NAME.containsKey(layer.easing)) {
            Easings.byName(layer.easing)
        } else {
            default
        }

    // ---------------------------------------------------------------
    // Main entry
    // ---------------------------------------------------------------

    fun drawScene(canvas: Canvas, project: Project, sceneIndex: Int, timeMs: Long) {
        val targetW = canvas.width
        val targetH = canvas.height
        if (targetW <= 0 || targetH <= 0) return

        // Background fills the whole target (letterbox bars included).
        val bgPaint = Paint().apply {
            style = Paint.Style.FILL
            color = colorInt(project.bg, DEFAULT_BG_HEX)
        }
        canvas.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), bgPaint)
        if (project.scenes.isEmpty()) return
        val scene = project.scenes[sceneIndex.coerceIn(0, project.scenes.size - 1)]

        val projW = max(1, project.width).toFloat()
        val projH = max(1, project.height).toFloat()
        val scale = min(targetW / projW, targetH / projH)
        val ox = (targetW - projW * scale) / 2f
        val oy = (targetH - projH * scale) / 2f

        // ---- Scene-level camera pre-pass (full-canvas camera layers) ----
        var sceneScale = 1f
        var sceneTx = 0f
        var sceneTy = 0f
        var sceneAlpha = 1f
        val sceneCamLayers = HashSet<Int>()
        scene.layers.forEachIndexed { idx, layer ->
            if (!layer.hidden && layer.effect in CAMERA_EFFECTS &&
                layer.w >= projW * 0.9f && layer.h >= projH * 0.9f &&
                timeMs >= layer.startMs
            ) {
                sceneCamLayers.add(idx)
                val p = progress(layer, timeMs)
                when (layer.effect) {
                    "push-in" -> sceneScale *= 1f + 0.15f * easeFor(layer, Easings::sineInOut)(p)
                    "pan" -> sceneTx += (easeFor(layer, Easings::sineInOut)(p) * 2f - 1f) * projW * 0.03f
                    "impact-shake" -> {
                        val w = wiggle(p)
                        sceneTx += w.first
                        sceneTy += w.second
                    }
                    "whoosh" -> {
                        val q = min(1f, p / 0.45f)
                        sceneTx += (1f - Easings.expoOut(q)) * projW * 0.5f
                    }
                    "fade" -> sceneAlpha *= p
                }
            }
        }

        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, projW, projH)
        // Scene background (again, inside the letterbox so it is exact).
        canvas.drawRect(0f, 0f, projW, projH, bgPaint)

        canvas.save()
        val cx = projW / 2f
        val cy = projH / 2f
        canvas.translate(cx + sceneTx, cy + sceneTy)
        canvas.scale(sceneScale, sceneScale)
        canvas.translate(-cx, -cy)

        scene.layers.forEachIndexed { idx, layer ->
            if (layer.hidden || timeMs < layer.startMs) return@forEachIndexed
            val eval = evaluate(layer, timeMs, projW)
            val alpha = (sceneAlpha * eval.alpha * layer.opacity).coerceIn(0f, 1f)
            if (alpha <= 0.004f && layer.effect != "fade") return@forEachIndexed
            val inSceneCam = idx in sceneCamLayers

            canvas.save()
            if (!inSceneCam && (eval.dx != 0f || eval.dy != 0f ||
                    eval.scale != 1f || eval.scaleX != 1f || eval.rot != 0f)
            ) {
                val lx = layer.x + layer.w / 2f
                val ly = layer.y + layer.h / 2f
                canvas.translate(lx + eval.dx, ly + eval.dy)
                canvas.rotate(eval.rot)
                canvas.scale(eval.scale * eval.scaleX, eval.scale)
                canvas.translate(-lx, -ly)
            }
            drawLayerContent(canvas, layer, eval, alpha)
            // flip-in depth shading: darkest at the edge-on moment (Three.js
            // lighting lesson applied in 2D).
            if (layer.effect == "flip-in" && eval.p < 1f) {
                val shade = (0.38f * (1f - kotlin.math.abs(eval.scaleX))).coerceIn(0f, 0.38f)
                if (shade > 0.01f) {
                    val sp = Paint().apply {
                        style = Paint.Style.FILL
                        color = Color.BLACK
                        this.alpha = (255f * shade * alpha).roundToInt().coerceIn(0, 255)
                    }
                    canvas.drawRect(
                        RectF(layer.x, layer.y, layer.x + layer.w, layer.y + layer.h), sp
                    )
                }
            }
            canvas.restore()
        }
        canvas.restore()
        canvas.restore()
    }

    // ---------------------------------------------------------------
    // Effect evaluation
    // ---------------------------------------------------------------

    private class Eval(
        val alpha: Float = 1f,
        val dx: Float = 0f,
        val dy: Float = 0f,
        val scale: Float = 1f,
        val scaleX: Float = 1f,
        val p: Float = 1f,
        /** Static layer rotation + any effect rotation settle (degrees). */
        val rot: Float = 0f,
        /** Scene-local playhead time (for carets / pulses). */
        val tMs: Long = 0L
    )

    private fun progress(layer: Layer, timeMs: Long): Float {
        if (layer.durationMs <= 0) return 1f
        return ((timeMs - layer.startMs).toFloat() / layer.durationMs.toFloat())
            .coerceIn(0f, 1f)
    }

    private fun evaluate(layer: Layer, timeMs: Long, projW: Float): Eval {
        val p = progress(layer, timeMs)
        val elapsedSec = ((timeMs - layer.startMs).coerceAtLeast(0L)) / 1000f
        val base = layer.rotation
        return when (layer.effect) {
            // True spring settle (Remotion spring): profile from the layer's
            // `spring` field, default bouncy. The spring runs on elapsed
            // time and settles to exactly 1 — then the layer rests.
            "pop-in" -> Eval(
                alpha = min(1f, elapsedSec * 5f),
                scale = Springs.atProfile(layer.spring.ifEmpty { "bouncy" }, elapsedSec)
                    .coerceAtLeast(0.01f),
                p = p, rot = base, tMs = timeMs
            )
            "slide-up" -> {
                val e = easeFor(layer, Easings::power3Out)(p)
                Eval(alpha = e, dy = (1f - e) * (layer.h * 0.6f + 80f), p = p, rot = base, tMs = timeMs)
            }
            "typewriter" -> Eval(p = p, rot = base, tMs = timeMs)
            "word-reveal" -> Eval(p = p, rot = base, tMs = timeMs)
            "char-stagger-in" -> Eval(p = p, rot = base, tMs = timeMs)
            "counter-up" -> Eval(p = p, rot = base, tMs = timeMs)
            "glow-pulse" -> Eval(p = p, rot = base, tMs = timeMs)
            "highlight-sweep" -> Eval(p = p, rot = base, tMs = timeMs)
            "underline-draw" -> Eval(p = p, rot = base, tMs = timeMs)
            // ONE spring pulse with overshoot, then rest (no sin loop).
            "keyword-pulse" -> {
                val entrance = 0.8f + 0.2f * Easings.backOut(min(1f, p * 1.4f))
                val pulse = 1f + 0.30f * Springs.pulse(elapsedSec)
                Eval(
                    alpha = min(1f, p * 3f),
                    scale = entrance * pulse,
                    p = p, rot = base, tMs = timeMs
                )
            }
            "code-typewriter" -> Eval(p = p, rot = base, tMs = timeMs)
            "line-highlight" -> Eval(p = p, rot = base, tMs = timeMs)
            "zoom-to-line" -> Eval(
                scale = 1f + 0.6f * easeFor(layer, Easings::expoOut)(p),
                p = p, rot = base, tMs = timeMs
            )
            "slide-card" -> {
                val e = easeFor(layer, Easings::power3Out)(p)
                Eval(
                    alpha = e,
                    dx = -(1f - e) * (layer.x + layer.w + 40f),
                    // 2.5° tilt that settles flat as the card lands (GSAP).
                    rot = base + (1f - e) * -2.5f,
                    p = p, tMs = timeMs
                )
            }
            "flip-in" -> {
                val e = easeFor(layer, Easings::power2InOut)(p)
                Eval(
                    alpha = min(1f, p * 2f),
                    scaleX = sin(e * PI.toFloat() / 2f).coerceAtLeast(0.02f),
                    p = p, rot = base, tMs = timeMs
                )
            }
            "bounce-drop" -> Eval(
                dy = -(layer.y + layer.h + 60f) * (1f - easeFor(layer, Easings::bounceOut)(p)),
                p = p, rot = base, tMs = timeMs
            )
            "elastic-snap" -> Eval(
                alpha = min(1f, p * 4f),
                scale = 0.3f + 0.7f * easeFor(layer, Easings::elasticOut)(p),
                p = p, rot = base, tMs = timeMs
            )
            "draw-arrow" -> Eval(p = easeFor(layer, Easings::power2Out)(p), rot = base, tMs = timeMs)
            "circle-marker" -> Eval(p = easeFor(layer, Easings::power2Out)(p), rot = base, tMs = timeMs)
            "box-marker" -> Eval(p = easeFor(layer, Easings::power2Out)(p), rot = base, tMs = timeMs)
            "path-arrow" -> Eval(p = easeFor(layer, Easings::power1InOut)(p), rot = base, tMs = timeMs)
            "push-in" -> Eval(scale = 1f + 0.15f * easeFor(layer, Easings::sineInOut)(p), p = p, rot = base, tMs = timeMs)
            "pan" -> Eval(dx = (easeFor(layer, Easings::sineInOut)(p) * 2f - 1f) * 40f, p = p, rot = base, tMs = timeMs)
            "impact-shake" -> {
                val w = wiggle(p)
                Eval(dx = w.first, dy = w.second, p = p, rot = base, tMs = timeMs)
            }
            "whoosh" -> {
                val q = min(1f, p / 0.45f)
                Eval(alpha = min(1f, p * 2.5f), dx = (1f - Easings.expoOut(q)) * projW * 0.5f, p = p, rot = base, tMs = timeMs)
            }
            "fade" -> Eval(alpha = easeFor(layer, Easings::sineInOut)(p), p = p, rot = base, tMs = timeMs)
            else -> Eval(p = p, rot = base, tMs = timeMs)
        }
    }

    /**
     * Stagger resolution for one unit: the layer's own `stagger` (ms model)
     * wins; otherwise the effect default (fraction model). Origin: the
     * layer's `staggerFrom`, or the effect's [defaultFrom].
     */
    private fun staggerP(
        layer: Layer,
        p: Float,
        index: Int,
        count: Int,
        defaultEach: Float,
        defaultFrom: String = Stagger.FROM_START
    ): Float {
        val from = layer.staggerFrom.ifEmpty { defaultFrom }
        return if (layer.staggerMs >= 0) {
            val elapsed = (p * layer.durationMs).toLong()
            Stagger.localMs(elapsed, layer.durationMs.toLong(), index, count, layer.staggerMs, from)
        } else {
            Stagger.local(p, index, count, defaultEach, from)
        }
    }

    // ---------------------------------------------------------------
    // Layer content drawing (project coordinates)
    // ---------------------------------------------------------------

    private fun drawLayerContent(canvas: Canvas, layer: Layer, eval: Eval, alpha: Float) {
        when (layer.type) {
            "code" -> drawCode(canvas, layer, eval, alpha)
            "card" -> drawCard(canvas, layer, eval, alpha)
            "image" -> {
                val bmp = layer.imagePath?.let { cachedBitmap(it) }
                if (bmp != null) drawImageBitmap(canvas, layer, bmp, alpha)
                else drawImagePlaceholder(canvas, layer, alpha)
            }
            "highlight" -> drawHighlight(canvas, layer, eval, alpha)
            else -> drawText(canvas, layer, eval, alpha)
        }
        // Marker overlays draw on top of any layer type.
        when (layer.effect) {
            "box-marker" -> drawBoxMarker(canvas, layer, eval, alpha)
            "path-arrow" -> drawPathArrow(canvas, layer, eval, alpha)
        }
    }

    // ---------------------------------------------------------------
    // Image layers: decode + cache + center-crop draw
    // ---------------------------------------------------------------

    private val bitmapCache = HashMap<String, Bitmap>()

    /**
     * Decodes (downsampled to at most 1024px on the long side) and caches by
     * path. Imported images always get unique file names, so a path's content
     * never changes under the cache. Returns null when the file is missing.
     */
    fun cachedBitmap(path: String): Bitmap? {
        bitmapCache[path]?.let { return it }
        val file = java.io.File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) {
            sample *= 2
        }
        val bmp = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        bitmapCache[path] = bmp
        return bmp
    }

    /** Draws the bitmap center-cropped into the layer bounds. */
    private fun drawImageBitmap(canvas: Canvas, layer: Layer, bmp: Bitmap, alpha: Float) {
        val dst = RectF(layer.x, layer.y, layer.x + layer.w, layer.y + layer.h)
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val dstRatio = layer.w / layer.h.coerceAtLeast(1f)
        val srcRatio = bw / bh
        val src = if (srcRatio > dstRatio) {
            // Source wider: crop left/right.
            val cropW = bh * dstRatio
            val left = ((bw - cropW) / 2f).toInt()
            Rect(left, 0, (left + cropW).toInt(), bmp.height)
        } else {
            // Source taller: crop top/bottom.
            val cropH = bw / dstRatio
            val top = ((bh - cropH) / 2f).toInt()
            Rect(0, top, bmp.width, (top + cropH).toInt())
        }
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            this.alpha = (255f * alpha).roundToInt().coerceIn(0, 255)
        }
        canvas.save()
        val clipPath = Path().apply {
            addRoundRect(dst, 16f, 16f, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        canvas.drawBitmap(bmp, src, dst, paint)
        canvas.restore()
    }

    private fun fillBg(canvas: Canvas, layer: Layer, alpha: Float, radius: Float, defaultHex: String?) {
        val bgLong = parseColor(layer.bg, 0x00000000L)
        val hex = if (bgLong != 0x00000000L) layer.bg else defaultHex ?: return
        val paint = Paint().apply {
            style = Paint.Style.FILL
            color = colorInt(hex, "#000000")
            this.alpha = (Color.alpha(this.color) * alpha).roundToInt().coerceIn(0, 255)
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            RectF(layer.x, layer.y, layer.x + layer.w, layer.y + layer.h),
            radius, radius, paint
        )
    }

    private fun drawText(canvas: Canvas, layer: Layer, eval: Eval, alpha: Float) {
        fillBg(canvas, layer, alpha, 24f, null)
        if (layer.text.isEmpty()) return
        val paint = Paint().apply {
            color = colorInt(layer.color, "#FFFFFF")
            textSize = layer.fontSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
            this.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        }
        val maxW = (layer.w - layer.fontSize * 0.4f).coerceAtLeast(10f)
        val lineHeight = layer.fontSize * 1.25f

        when (layer.effect) {
            "word-reveal" -> {
                drawWordReveal(canvas, layer, eval, paint, maxW, lineHeight)
                return
            }
            "char-stagger-in" -> {
                drawCharStagger(canvas, layer, eval, paint, maxW, lineHeight)
                return
            }
            "glow-pulse" -> {
                // One spring pulse of glow per ~1.6s of playhead (deterministic).
                val cycleT = ((eval.tMs - layer.startMs).coerceAtLeast(0L) % 1600L) / 1000f
                val glow = 0.25f + 0.75f * Springs.pulse(cycleT)
                paint.setShadowLayer(
                    layer.fontSize * 0.55f * glow, 0f, 0f,
                    colorInt(layer.color, "#FFFFFF")
                )
            }
        }

        val displayText = when (layer.effect) {
            // Typing itself is linear by design (GSAP TextPlugin bhi yahi
            // karta hai); the caret blinks on the playhead (deterministic).
            "typewriter" -> {
                val n = (layer.text.length * eval.p).toInt().coerceIn(0, layer.text.length)
                val caret = if (eval.p < 1f && (eval.tMs / 530L) % 2L == 0L) "▌" else ""
                layer.text.substring(0, n) + caret
            }
            "counter-up" -> counterText(layer, eval.p)
            else -> layer.text
        }
        if (displayText.isEmpty()) return
        val lines = wrapText(paint, displayText, maxW)
        val blockH = lines.size * lineHeight
        val blockTop = layer.y + (layer.h - blockH) / 2f

        if (layer.effect == "highlight-sweep") {
            // Marker band sweeps left->right BEHIND the text (rough-notation).
            val sweep = easeFor(layer, Easings::power2InOut)(eval.p)
            val band = Paint().apply {
                style = Paint.Style.FILL
                color = colorInt(layer.color, "#FFEB3B")
                this.alpha = (110f * alpha).roundToInt().coerceIn(0, 255)
                isAntiAlias = true
            }
            val bandRect = RectF(
                layer.x + layer.fontSize * 0.1f,
                blockTop + layer.fontSize * 0.12f,
                layer.x + layer.fontSize * 0.1f + (layer.w - layer.fontSize * 0.2f) * sweep,
                blockTop + blockH - layer.fontSize * 0.05f
            )
            canvas.drawRoundRect(bandRect, 12f, 12f, band)
        }

        var baseline = blockTop + layer.fontSize * 0.85f
        var lastLineWidth = 0f
        var lastBaseline = baseline
        for (line in lines) {
            val tw = paint.measureText(line)
            canvas.drawText(line, layer.x + (layer.w - tw) / 2f, baseline, paint)
            lastLineWidth = tw
            lastBaseline = baseline
            baseline += lineHeight
        }

        if (layer.effect == "underline-draw" && lines.isNotEmpty()) {
            // Hand-drawn underline under the last line (two-segment wobble).
            val q = easeFor(layer, Easings::power2Out)(eval.p)
            val startX = layer.x + (layer.w - lastLineWidth) / 2f
            val uy = lastBaseline + layer.fontSize * 0.24f
            val endX = startX + lastLineWidth * q
            val midX = (startX + endX) / 2f
            val up = Paint().apply {
                style = Paint.Style.STROKE
                color = colorInt(layer.color, "#FFEB3B")
                strokeWidth = max(4f, layer.fontSize * 0.09f)
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
                this.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
            }
            val path = Path().apply {
                moveTo(startX, uy)
                lineTo(midX, uy + layer.fontSize * 0.03f)
                lineTo(endX, uy)
            }
            canvas.drawPath(path, up)
        }
        paint.clearShadowLayer()
    }

    /** counter-up: first number in the text counts 0 -> target (power3.out). */
    private fun counterText(layer: Layer, p: Float): String {
        val m = Regex("""\d[\d,]*(\.\d+)?""").find(layer.text) ?: return layer.text
        val target = m.value.replace(",", "").toDoubleOrNull() ?: return layer.text
        val eased = easeFor(layer, Easings::power3Out)(p)
        val v = target * eased
        val shown = if (target % 1.0 == 0.0) {
            String.format(java.util.Locale.US, "%,.0f", v)
        } else {
            String.format(java.util.Locale.US, "%,.1f", v)
        }
        return layer.text.substring(0, m.range.first) + shown +
            layer.text.substring(m.range.last + 1)
    }

    /** Per-char stagger pop (GSAP SplitText model), default from: center. */
    private fun drawCharStagger(
        canvas: Canvas,
        layer: Layer,
        eval: Eval,
        paint: Paint,
        maxW: Float,
        lineHeight: Float
    ) {
        // Layout: wrap by words, then place chars; keep global char indices.
        data class Ch(val ch: String, val dx: Float, val line: Int, val index: Int)
        val placed = ArrayList<Ch>()
        var lineIdx = 0
        var cursorX = 0f
        var charIdx = 0
        val spaceW = paint.measureText(" ")
        for (word in layer.text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val wordW = paint.measureText(word)
            if (cursorX > 0f && cursorX + spaceW + wordW > maxW) {
                lineIdx++
                cursorX = 0f
            }
            if (cursorX > 0f) cursorX += spaceW
            for (c in word) {
                val s = c.toString()
                placed.add(Ch(s, cursorX, lineIdx, charIdx++))
                cursorX += paint.measureText(s)
            }
        }
        if (placed.isEmpty()) return
        val lineCount = lineIdx + 1
        val lineWidths = FloatArray(lineCount)
        for (pc in placed) {
            val end = pc.dx + paint.measureText(pc.ch)
            if (end > lineWidths[pc.line]) lineWidths[pc.line] = end
        }
        val blockH = lineCount * lineHeight
        val topBaseline = layer.y + (layer.h - blockH) / 2f + layer.fontSize * 0.85f
        val padX = (layer.w - maxW) / 2f
        val total = charIdx
        for (pc in placed) {
            val local = staggerP(layer, eval.p, pc.index, total, 0.06f, Stagger.FROM_CENTER)
            if (local <= 0f) continue
            val s = Easings.backOut(local).coerceAtLeast(0.01f)
            val charPaint = Paint(paint).apply {
                this.alpha = (Color.alpha(color) *
                    local.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
            }
            val lineStartX = layer.x + padX + (maxW - lineWidths[pc.line]) / 2f
            val cxp = lineStartX + pc.dx + paint.measureText(pc.ch) / 2f
            val cyp = topBaseline + pc.line * lineHeight - layer.fontSize * 0.3f
            canvas.save()
            canvas.translate(cxp, cyp)
            canvas.scale(s, s)
            canvas.translate(-cxp, -cyp)
            canvas.drawText(pc.ch, lineStartX + pc.dx, topBaseline + pc.line * lineHeight, charPaint)
            canvas.restore()
        }
    }

    /** Words appear one by one with GSAP-style stagger; each word fades/rises. */
    private fun drawWordReveal(
        canvas: Canvas,
        layer: Layer,
        eval: Eval,
        paint: Paint,
        maxW: Float,
        lineHeight: Float
    ) {
        val p = eval.p
        val words = layer.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return
        // Layout: wrap words into lines (word, xOffset within layer, lineIndex).
        data class Placed(val word: String, val dx: Float, val line: Int, val index: Int)
        val linesWords = ArrayList<Placed>()
        var lineIdx = 0
        var cursorX = 0f
        // First pass with left alignment, then center each line afterwards.
        words.forEachIndexed { i, word ->
            val ww = paint.measureText(word)
            val space = if (cursorX > 0f) paint.measureText(" ") else 0f
            if (cursorX > 0f && cursorX + space + ww > maxW) {
                lineIdx++
                cursorX = 0f
            }
            val sx = if (cursorX > 0f) paint.measureText(" ") else 0f
            linesWords.add(Placed(word, cursorX + sx, lineIdx, i))
            cursorX += sx + ww
        }
        val lineCount = lineIdx + 1
        // Line widths for centering.
        val lineWidths = FloatArray(lineCount)
        for (pl in linesWords) {
            val end = pl.dx + paint.measureText(pl.word)
            if (end > lineWidths[pl.line]) lineWidths[pl.line] = end
        }
        val blockH = lineCount * lineHeight
        val topBaseline = layer.y + (layer.h - blockH) / 2f + layer.fontSize * 0.85f
        val padX = (layer.w - maxW) / 2f
        for (pl in linesWords) {
            val local = staggerP(layer, p, pl.index, words.size, 0.10f)
            if (local <= 0f) continue
            val lineStartX = layer.x + padX + (maxW - lineWidths[pl.line]) / 2f
            val wordPaint = Paint(paint).apply {
                this.alpha = (Color.alpha(color) * local).roundToInt().coerceIn(0, 255)
            }
            val dy = (1f - Easings.power2Out(local)) * layer.fontSize * 0.25f
            canvas.drawText(
                pl.word,
                lineStartX + pl.dx,
                topBaseline + pl.line * lineHeight + dy,
                wordPaint
            )
        }
    }

    private fun drawCode(canvas: Canvas, layer: Layer, eval: Eval, alpha: Float) {
        fillBg(canvas, layer, alpha, 24f, CODE_BG_HEX)
        val lines = layer.code.split("\n")
        val paint = Paint().apply {
            color = colorInt(layer.color, "#9CDCFE")
            textSize = layer.fontSize
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
            this.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        }
        val pad = layer.fontSize * 0.6f
        val lineHeight = layer.fontSize * 1.4f
        val maxLines = max(1, floor((layer.h - pad * 2) / lineHeight).toInt())
        val visible = lines.take(maxLines)

        if (layer.effect == "line-highlight" && visible.isNotEmpty()) {
            // The band GLIDES between lines (power2.inOut), never jumps.
            val bandPos = easeFor(layer, Easings::power2InOut)(eval.p) *
                (visible.size - 1).coerceAtLeast(0)
            val bandY = layer.y + pad + bandPos * lineHeight - layer.fontSize * 0.12f
            val band = Paint().apply {
                style = Paint.Style.FILL
                color = colorInt("#FFD54F", "#FFD54F")
                this.alpha = (90f * alpha).roundToInt().coerceIn(0, 255)
                isAntiAlias = true
            }
            canvas.drawRoundRect(
                RectF(
                    layer.x + pad * 0.4f, bandY,
                    layer.x + layer.w - pad * 0.4f, bandY + lineHeight
                ),
                10f, 10f, band
            )
        }

        if (layer.effect == "code-typewriter") {
            // Soft band behind the line currently being typed.
            var current = -1
            visible.forEachIndexed { i, rawLine ->
                if (staggerP(layer, eval.p, i, visible.size, 0.08f) > 0f) current = i
            }
            if (current >= 0) {
                val band = Paint().apply {
                    style = Paint.Style.FILL
                    color = colorInt("#FFFFFF", "#FFFFFF")
                    this.alpha = (26f * alpha).roundToInt().coerceIn(0, 255)
                    isAntiAlias = true
                }
                val by = layer.y + pad + current * lineHeight - layer.fontSize * 0.12f
                canvas.drawRoundRect(
                    RectF(layer.x + pad * 0.4f, by, layer.x + layer.w - pad * 0.4f, by + lineHeight),
                    10f, 10f, band
                )
            }
        }

        visible.forEachIndexed { i, rawLine ->
            val text = if (layer.effect == "code-typewriter") {
                val local = staggerP(layer, eval.p, i, visible.size, 0.08f)
                val n = (rawLine.length * local).toInt().coerceIn(0, rawLine.length)
                rawLine.substring(0, n)
            } else {
                rawLine
            }
            if (text.isNotEmpty()) {
                canvas.drawText(
                    text,
                    layer.x + pad,
                    layer.y + pad + layer.fontSize + i * lineHeight,
                    paint
                )
            }
            // Blinking caret at the end of the line being typed (playhead time).
            if (layer.effect == "code-typewriter" && eval.p < 1f &&
                (eval.tMs / 530L) % 2L == 0L && text.isNotEmpty() &&
                staggerP(layer, eval.p, i, visible.size, 0.08f) < 1f
            ) {
                val caretW = layer.fontSize * 0.55f
                val cx = layer.x + pad + paint.measureText(text) + 4f
                val caret = Paint().apply {
                    style = Paint.Style.FILL
                    color = colorInt(layer.color, "#9CDCFE")
                    this.alpha = (230f * alpha).roundToInt().coerceIn(0, 255)
                }
                canvas.drawRect(
                    RectF(
                        cx,
                        layer.y + pad + i * lineHeight + layer.fontSize * 0.12f,
                        cx + caretW,
                        layer.y + pad + i * lineHeight + layer.fontSize * 1.05f
                    ),
                    caret
                )
            }
        }
    }

    private fun drawCard(canvas: Canvas, layer: Layer, eval: Eval, alpha: Float) {
        fillBg(canvas, layer, alpha, 32f, CARD_BG_HEX)
        if (layer.text.isEmpty()) return
        val paint = Paint().apply {
            color = colorInt(layer.color, "#FFFFFF")
            textSize = layer.fontSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
            this.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        }
        val maxW = (layer.w - layer.fontSize * 0.8f).coerceAtLeast(10f)
        val lines = wrapText(paint, layer.text, maxW)
        val lineHeight = layer.fontSize * 1.25f
        val blockH = lines.size * lineHeight
        var baseline = layer.y + (layer.h - blockH) / 2f + layer.fontSize * 0.85f
        for (line in lines) {
            val tw = paint.measureText(line)
            canvas.drawText(line, layer.x + (layer.w - tw) / 2f, baseline, paint)
            baseline += lineHeight
        }
    }

    private fun drawImagePlaceholder(canvas: Canvas, layer: Layer, alpha: Float) {
        val fill = Paint().apply {
            style = Paint.Style.FILL
            color = colorInt("#263238", "#263238")
            this.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
            isAntiAlias = true
        }
        val rect = RectF(layer.x, layer.y, layer.x + layer.w, layer.y + layer.h)
        canvas.drawRoundRect(rect, 24f, 24f, fill)
        val stroke = Paint().apply {
            style = Paint.Style.STROKE
            color = colorInt("#90A4AE", "#90A4AE")
            strokeWidth = 5f
            pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
            this.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 24f, 24f, stroke)
        val label = layer.text.ifEmpty { "IMAGE" }
        val paint = Paint().apply {
            color = colorInt("#CFD8DC", "#CFD8DC")
            textSize = layer.fontSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
            this.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        }
        val tw = paint.measureText(label)
        canvas.drawText(
            label,
            layer.x + (layer.w - tw) / 2f,
            layer.y + layer.h / 2f + layer.fontSize * 0.35f,
            paint
        )
    }

    private fun drawHighlight(canvas: Canvas, layer: Layer, eval: Eval, alpha: Float) {
        val strokeW = max(6f, layer.fontSize * 0.16f)
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            color = colorInt(layer.color, "#FFEB3B")
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
            this.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        }
        when (layer.effect) {
            "circle-marker" -> {
                val inset = strokeW
                val rect = RectF(
                    layer.x + inset, layer.y + inset,
                    layer.x + layer.w - inset, layer.y + layer.h - inset
                )
                drawHandEllipse(canvas, rect, eval.p, paint, layer.id.hashCode())
            }
            "draw-arrow" -> {
                val sx = layer.x + layer.fontSize * 0.4f
                val sy = layer.y + layer.h - layer.fontSize * 0.4f
                val ex = layer.x + layer.w - layer.fontSize * 0.4f
                val ey = layer.y + layer.fontSize * 0.4f
                val path = Path().apply { moveTo(sx, sy); lineTo(ex, ey) }
                val measure = PathMeasure(path, false)
                val len = measure.length
                val sub = Path()
                measure.getSegment(0f, len * eval.p, sub, true)
                canvas.drawPath(sub, paint)
                if (eval.p > 0.03f) {
                    val pos = FloatArray(2)
                    val tan = FloatArray(2)
                    measure.getPosTan(len * eval.p, pos, tan)
                    val angle = atan2(tan[1], tan[0])
                    // Arrowhead POPS in with back.out over the last quarter.
                    val headScale = if (eval.p <= 0.75f) 1f
                        else Easings.backOut((eval.p - 0.75f) / 0.25f)
                    val headLen = layer.fontSize * 0.55f * headScale.coerceAtLeast(0.05f)
                    for (sign in floatArrayOf(1f, -1f)) {
                        val a = angle + PI.toFloat() * 0.82f * sign
                        canvas.drawLine(
                            pos[0], pos[1],
                            pos[0] + cos(a) * headLen, pos[1] + sin(a) * headLen,
                            paint
                        )
                    }
                }
            }
            else -> {
                val rect = RectF(layer.x, layer.y, layer.x + layer.w, layer.y + layer.h)
                canvas.drawRoundRect(rect, 18f, 18f, paint)
            }
        }
        // Optional label text inside the marker.
        if (layer.text.isNotEmpty() && layer.effect != "draw-arrow") {
            val textPaint = Paint().apply {
                color = colorInt(layer.color, "#FFEB3B")
                textSize = layer.fontSize
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
                this.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
            }
            val tw = textPaint.measureText(layer.text)
            canvas.drawText(
                layer.text,
                layer.x + (layer.w - tw) / 2f,
                layer.y + layer.h / 2f + layer.fontSize * 0.35f,
                textPaint
            )
        }
    }

    /**
     * Hand-drawn ellipse (rough-notation Circle): radius wobble seeded by the
     * layer id (deterministic), drawn as TWO overlapping strokes — a perfect
     * geometric arc is exactly what reads as "boring".
     */
    private fun drawHandEllipse(
        canvas: Canvas, rect: RectF, p: Float, paint: Paint, seed: Int
    ) {
        if (p <= 0f) return
        val cx = rect.centerX()
        val cy = rect.centerY()
        val rx = rect.width() / 2f
        val ry = rect.height() / 2f
        val seedF = (kotlin.math.abs(seed) % 10).toFloat()
        fun pass(offX: Float, offY: Float, sweepScale: Float, alphaScale: Float) {
            val sweep = 2f * PI.toFloat() * p * sweepScale
            val steps = max(4, (48f * p * sweepScale).toInt())
            val path = Path()
            for (i in 0..steps) {
                val a = -PI.toFloat() / 2f + sweep * (i.toFloat() / steps)
                val wob = 1f + 0.035f * sin(a * 3f + seedF) + 0.02f * sin(a * 7f + seedF * 2f)
                val x = cx + offX + rx * wob * cos(a)
                val y = cy + offY + ry * wob * sin(a)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val passPaint = Paint(paint).apply {
                this.alpha = (this.alpha * alphaScale).roundToInt().coerceIn(0, 255)
            }
            canvas.drawPath(path, passPaint)
        }
        pass(0f, 0f, 1f, 1f)
        pass(rx * 0.03f, -ry * 0.05f, 0.92f, 0.5f)
    }

    /** box-marker: hand-drawn double-stroke box around the layer bounds. */
    private fun drawBoxMarker(canvas: Canvas, layer: Layer, eval: Eval, alpha: Float) {
        if (eval.p <= 0f) return
        val strokeW = max(6f, layer.fontSize * 0.14f)
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            color = colorInt(layer.color, "#FFEB3B")
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
            this.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        }
        fun pass(dx: Float, dy: Float, pScale: Float, alphaScale: Float) {
            val rect = RectF(
                layer.x + dx, layer.y + dy,
                layer.x + layer.w + dx, layer.y + layer.h + dy
            )
            val path = Path().apply { addRoundRect(rect, 14f, 14f, Path.Direction.CW) }
            val measure = PathMeasure(path, false)
            val sub = Path()
            measure.getSegment(0f, measure.length * eval.p * pScale, sub, true)
            val passPaint = Paint(paint).apply {
                this.alpha = (this.alpha * alphaScale).roundToInt().coerceIn(0, 255)
            }
            canvas.drawPath(sub, passPaint)
        }
        pass(0f, 0f, 1f, 1f)
        pass(5f, -4f, 0.9f, 0.45f)
    }

    /** path-arrow: curved (quadratic) arrow; head auto-rotates to the tangent. */
    private fun drawPathArrow(canvas: Canvas, layer: Layer, eval: Eval, alpha: Float) {
        if (eval.p <= 0f) return
        val strokeW = max(6f, layer.fontSize * 0.14f)
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            color = colorInt(layer.color, "#FFEB3B")
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
            this.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        }
        val sx = layer.x + layer.w * 0.08f
        val sy = layer.y + layer.h * 0.85f
        val cxP = layer.x + layer.w * 0.45f
        val cyP = layer.y - layer.h * 0.30f
        val ex = layer.x + layer.w * 0.90f
        val ey = layer.y + layer.h * 0.20f
        val path = Path().apply {
            moveTo(sx, sy)
            quadTo(cxP, cyP, ex, ey)
        }
        val measure = PathMeasure(path, false)
        val len = measure.length
        val sub = Path()
        measure.getSegment(0f, len * eval.p, sub, true)
        canvas.drawPath(sub, paint)
        if (eval.p > 0.03f) {
            val pos = FloatArray(2)
            val tan = FloatArray(2)
            measure.getPosTan(len * eval.p, pos, tan)
            val angle = atan2(tan[1], tan[0])
            val headScale = if (eval.p <= 0.8f) 1f
                else Easings.backOut((eval.p - 0.8f) / 0.2f)
            val headLen = layer.fontSize * 0.55f * headScale.coerceAtLeast(0.05f)
            for (sign in floatArrayOf(1f, -1f)) {
                val a = angle + PI.toFloat() * 0.82f * sign
                canvas.drawLine(
                    pos[0], pos[1],
                    pos[0] + cos(a) * headLen, pos[1] + sin(a) * headLen,
                    paint
                )
            }
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private fun wrapText(paint: Paint, text: String, maxWidth: Float): List<String> {
        val out = ArrayList<String>()
        for (paragraph in text.split("\n")) {
            if (paragraph.isEmpty()) {
                out.add("")
                continue
            }
            var current = ""
            for (word in paragraph.split(" ")) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                    out.add(current)
                    current = word
                } else {
                    current = candidate
                }
            }
            if (current.isNotEmpty() || out.isEmpty()) out.add(current)
        }
        return out
    }

    /** Hex string -> android.graphics.Color int (via the shared Long parser). */
    private fun colorInt(hex: String, fallbackHex: String): Int {
        val fallback = parseColor(fallbackHex, 0xFF000000L)
        return parseColor(hex, fallback).toInt()
    }
}
