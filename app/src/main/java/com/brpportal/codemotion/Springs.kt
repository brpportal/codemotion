package com.brpportal.codemotion

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Remotion-style spring engine: a true damped harmonic oscillator, evaluated
 * ANALYTICALLY from elapsed time (deterministic from the playhead — the same
 * t always gives the same value, so preview and MP4 export agree exactly).
 *
 * Physics: x'' = -(k/m)(x - 1) - (c/m) x', starting at x=0, v=0, target 1.
 * Closed-form solution per damping regime (under / critical / over damped).
 *
 * Profiles (Remotion spring() configs):
 *  - "smooth": stiffness 100, damping 200 — heavily overdamped, no bounce,
 *    a slow confident settle.
 *  - "snappy": stiffness 260, damping 26 — fast with a small overshoot.
 *  - "bouncy": stiffness 120, damping 7  — clear overshoot + wobble settle.
 */
object Springs {

    data class Params(val mass: Float, val stiffness: Float, val damping: Float)

    val PROFILES: Map<String, Params> = mapOf(
        "smooth" to Params(1f, 100f, 200f),
        "snappy" to Params(1f, 260f, 26f),
        "bouncy" to Params(1f, 120f, 7f)
    )

    fun params(name: String): Params = PROFILES[name] ?: PROFILES.getValue("snappy")

    /** Spring value at [tSec] seconds after start (target = 1). */
    fun at(p: Params, tSec: Float): Float {
        if (tSec <= 0f) return 0f
        val m = p.mass.toDouble()
        val k = p.stiffness.toDouble()
        val c = p.damping.toDouble()
        val omega = sqrt(k / m)                       // natural frequency
        val zeta = c / (2.0 * sqrt(k * m))            // damping ratio
        val t = tSec.toDouble()
        val x = when {
            zeta < 1.0 -> {                           // underdamped: overshoot + settle
                val wd = omega * sqrt(1.0 - zeta * zeta)
                1.0 - exp(-zeta * omega * t) *
                    (cos(wd * t) + (zeta * omega / wd) * sin(wd * t))
            }
            zeta > 1.0 -> {                           // overdamped: no bounce (smooth)
                val r = sqrt(zeta * zeta - 1.0)
                val s1 = -zeta * omega + omega * r
                val s2 = -zeta * omega - omega * r
                1.0 + (s2 * exp(s1 * t) - s1 * exp(s2 * t)) / (s1 - s2)
            }
            else -> 1.0 - exp(-omega * t) * (1.0 + omega * t)   // critically damped
        }
        return x.toFloat()
    }

    fun atProfile(name: String, tSec: Float): Float = at(params(name), tSec)

    /**
     * A SINGLE spring pulse: an impulse response that rises to 1, overshoots
     * slightly negative on the settle, and returns to 0 — for keyword-pulse
     * and glow. NOT a looping sine: one kick, then rest.
     * pulse(t) = e^(-ζωt) · sin(ωd·t), normalized so its peak is exactly 1.
     */
    fun pulse(tSec: Float, stiffness: Float = 170f, zeta: Float = 0.22f): Float {
        if (tSec <= 0f) return 0f
        val omega = sqrt(stiffness.toDouble())
        val wd = omega * sqrt(1.0 - zeta * zeta)
        val tStar = atan2(wd, zeta * omega) / wd      // time of first peak
        val peak = exp(-zeta * omega * tStar) * sin(wd * tStar)
        if (peak == 0.0) return 0f
        val v = exp(-zeta * omega * tSec) * sin(wd * tSec) / peak
        return v.toFloat()
    }
}
