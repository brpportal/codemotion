package com.brpportal.codemotion

import kotlin.math.abs

/**
 * GSAP-style stagger: units (words / chars / lines) start one after another.
 * Each unit's delay is proportional to its DISTANCE from an origin:
 *  - "start": unit 0 first, delay grows to the right.
 *  - "end":   last unit first.
 *  - "center": middle unit first, delays grow outward.
 *  - "edges": outer units first, delays grow inward.
 *
 * Two models, both deterministic:
 *  - fraction model: [each] = delay between neighbours as a fraction of one
 *    unit's own duration; [local] maps global progress p (0..1 over the whole
 *    staggered group) to a unit's local progress.
 *  - time model: [localMs] uses an explicit per-unit delay in milliseconds
 *    (the FORMAT_SPEC layers[].stagger value).
 */
object Stagger {

    const val FROM_START = "start"
    const val FROM_CENTER = "center"
    const val FROM_END = "end"
    const val FROM_EDGES = "edges"

    fun distance(index: Int, count: Int, from: String): Float {
        if (count <= 1) return 0f
        return when (from) {
            FROM_END -> (count - 1 - index).toFloat()
            FROM_CENTER -> abs(index - (count - 1) / 2f)
            FROM_EDGES -> minOf(index, count - 1 - index).toFloat()
            else -> index.toFloat() // start
        }
    }

    fun maxDistance(count: Int, from: String): Float {
        if (count <= 1) return 0f
        var m = 0f
        for (i in 0 until count) m = maxOf(m, distance(i, count, from))
        return m
    }

    /** Fraction model: global p (0..1) -> this unit's local progress (0..1). */
    fun local(p: Float, index: Int, count: Int, each: Float, from: String): Float {
        val span = 1f + each * maxDistance(count, from)   // total timeline in unit-durations
        val start = distance(index, count, from) * each
        return ((p * span - start) / 1f).coerceIn(0f, 1f)
    }

    /** Time model: explicit stagger delay in ms between neighbouring units. */
    fun localMs(
        elapsedMs: Long,
        durationMs: Long,
        index: Int,
        count: Int,
        staggerMs: Int,
        from: String
    ): Float {
        if (durationMs <= 0L) return 1f
        val unit = (durationMs - (maxDistance(count, from) * staggerMs).toLong())
            .coerceAtLeast(1L)
        val start = (distance(index, count, from) * staggerMs).toLong()
        return ((elapsedMs - start).toFloat() / unit.toFloat()).coerceIn(0f, 1f)
    }
}
