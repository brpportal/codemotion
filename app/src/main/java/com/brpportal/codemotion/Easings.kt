package com.brpportal.codemotion

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * True GSAP-style easing curves (canonical set from references/gsap-skills —
 * gsap-core). Every function maps progress t in [0,1] to an eased value.
 * back / elastic may overshoot outside [0,1] on purpose (spring settle feel,
 * like Remotion's spring()).
 *
 * GSAP power mapping: power1 = t^2, power2 = t^3, power3 = t^4, power4 = t^5.
 */
object Easings {

    private fun clamp01(t: Float): Float = t.coerceIn(0f, 1f)

    fun linear(t: Float): Float = clamp01(t)

    // ---- sine ----
    fun sineIn(t: Float): Float {
        val x = clamp01(t)
        return 1f - cos(x * (PI / 2)).toFloat()
    }

    fun sineOut(t: Float): Float {
        val x = clamp01(t)
        return sin(x * (PI / 2)).toFloat()
    }

    fun sineInOut(t: Float): Float {
        val x = clamp01(t)
        return (-(cos(PI * x) - 1.0) / 2.0).toFloat()
    }

    // ---- power (GSAP: powerN exponent = N + 1) ----
    private fun powerIn(t: Float, exponent: Int): Float =
        clamp01(t).pow(exponent)

    private fun powerOut(t: Float, exponent: Int): Float =
        1f - (1f - clamp01(t)).pow(exponent)

    private fun powerInOut(t: Float, exponent: Int): Float {
        val x = clamp01(t)
        return if (x < 0.5f) {
            (2.0.pow(exponent - 1) * x.toDouble().pow(exponent)).toFloat()
        } else {
            1f - ((-2.0 * x + 2.0).pow(exponent) / 2.0).toFloat()
        }
    }

    fun power1In(t: Float): Float = powerIn(t, 2)
    fun power1Out(t: Float): Float = powerOut(t, 2)
    fun power1InOut(t: Float): Float = powerInOut(t, 2)

    fun power2In(t: Float): Float = powerIn(t, 3)
    fun power2Out(t: Float): Float = powerOut(t, 3)
    fun power2InOut(t: Float): Float = powerInOut(t, 3)

    fun power3In(t: Float): Float = powerIn(t, 4)
    fun power3Out(t: Float): Float = powerOut(t, 4)
    fun power3InOut(t: Float): Float = powerInOut(t, 4)

    fun power4In(t: Float): Float = powerIn(t, 5)
    fun power4Out(t: Float): Float = powerOut(t, 5)
    fun power4InOut(t: Float): Float = powerInOut(t, 5)

    // ---- expo ----
    fun expoIn(t: Float): Float {
        val x = clamp01(t)
        return if (x == 0f) 0f else 2.0.pow(10.0 * x - 10.0).toFloat()
    }

    fun expoOut(t: Float): Float {
        val x = clamp01(t)
        return if (x == 1f) 1f else 1f - 2.0.pow(-10.0 * x).toFloat()
    }

    fun expoInOut(t: Float): Float {
        val x = clamp01(t)
        return when {
            x == 0f -> 0f
            x == 1f -> 1f
            x < 0.5f -> (2.0.pow(20.0 * x - 10.0) / 2.0).toFloat()
            else -> ((2.0 - 2.0.pow(-20.0 * x + 10.0)) / 2.0).toFloat()
        }
    }

    // ---- circ ----
    fun circIn(t: Float): Float {
        val x = clamp01(t).toDouble()
        return 1.0f - sqrt(1.0 - x * x).toFloat()
    }

    fun circOut(t: Float): Float {
        val x = (clamp01(t) - 1f).toDouble()
        return sqrt(1.0 - x * x).toFloat()
    }

    fun circInOut(t: Float): Float {
        val x = clamp01(t).toDouble()
        return if (x < 0.5) {
            ((1.0 - sqrt(1.0 - (2.0 * x).pow(2))) / 2.0).toFloat()
        } else {
            ((sqrt(1.0 - (-2.0 * x + 2.0).pow(2)) + 1.0) / 2.0).toFloat()
        }
    }

    // ---- back (GSAP default overshoot s = 1.70158) ----
    private const val BACK_S = 1.70158f
    private const val BACK_C3 = BACK_S + 1f

    fun backIn(t: Float): Float {
        val x = clamp01(t)
        return BACK_C3 * x * x * x - BACK_S * x * x
    }

    fun backOut(t: Float): Float {
        val x = clamp01(t) - 1f
        return 1f + BACK_C3 * x * x * x + BACK_S * x * x
    }

    fun backInOut(t: Float): Float {
        val s2 = BACK_S * 1.525f
        val x = clamp01(t)
        return if (x < 0.5f) {
            val u = 2f * x
            (u * u * ((s2 + 1f) * u - s2)) / 2f
        } else {
            val u = 2f * x - 2f
            (u * u * ((s2 + 1f) * u + s2) + 2f) / 2f
        }
    }

    // ---- elastic (GSAP elastic.out default amplitude 1, period 0.3) ----
    fun elasticOut(t: Float): Float {
        val x = clamp01(t)
        if (x == 0f || x == 1f) return x
        val c4 = (2.0 * PI) / 3.0
        return (2.0.pow(-10.0 * x) * sin((x * 10.0 - 0.75) * c4) + 1.0).toFloat()
    }

    fun elasticIn(t: Float): Float {
        val x = clamp01(t)
        if (x == 0f || x == 1f) return x
        val c4 = (2.0 * PI) / 3.0
        return (-2.0.pow(10.0 * x - 10.0) * sin((x * 10.0 - 10.75) * c4)).toFloat()
    }

    fun elasticInOut(t: Float): Float {
        val x = clamp01(t)
        if (x == 0f || x == 1f) return x
        val c5 = (2.0 * PI) / 4.5
        return if (x < 0.5f) {
            (-(2.0.pow(20.0 * x - 10.0) * sin((20.0 * x - 11.125) * c5)) / 2.0).toFloat()
        } else {
            ((2.0.pow(-20.0 * x + 10.0) * sin((20.0 * x - 11.125) * c5)) / 2.0 + 1.0).toFloat()
        }
    }

    // ---- bounce ----
    fun bounceOut(t: Float): Float {
        var x = clamp01(t)
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> {
                x -= 1.5f / d1
                n1 * x * x + 0.75f
            }
            x < 2.5f / d1 -> {
                x -= 2.25f / d1
                n1 * x * x + 0.9375f
            }
            else -> {
                x -= 2.625f / d1
                n1 * x * x + 0.984375f
            }
        }
    }

    fun bounceIn(t: Float): Float = 1f - bounceOut(1f - clamp01(t))

    fun bounceInOut(t: Float): Float {
        val x = clamp01(t)
        return if (x < 0.5f) {
            (1f - bounceOut(1f - 2f * x)) / 2f
        } else {
            (1f + bounceOut(2f * x - 1f)) / 2f
        }
    }

    /** All named easings (GSAP naming), for lookup and tooling. */
    val BY_NAME: Map<String, (Float) -> Float> = mapOf(
        "linear" to ::linear,
        "sine.in" to ::sineIn, "sine.out" to ::sineOut, "sine.inOut" to ::sineInOut,
        "power1.in" to ::power1In, "power1.out" to ::power1Out, "power1.inOut" to ::power1InOut,
        "power2.in" to ::power2In, "power2.out" to ::power2Out, "power2.inOut" to ::power2InOut,
        "power3.in" to ::power3In, "power3.out" to ::power3Out, "power3.inOut" to ::power3InOut,
        "power4.in" to ::power4In, "power4.out" to ::power4Out, "power4.inOut" to ::power4InOut,
        "expo.in" to ::expoIn, "expo.out" to ::expoOut, "expo.inOut" to ::expoInOut,
        "circ.in" to ::circIn, "circ.out" to ::circOut, "circ.inOut" to ::circInOut,
        "back.in" to ::backIn, "back.out" to ::backOut, "back.inOut" to ::backInOut,
        "elastic.in" to ::elasticIn, "elastic.out" to ::elasticOut, "elastic.inOut" to ::elasticInOut,
        "bounce.in" to ::bounceIn, "bounce.out" to ::bounceOut, "bounce.inOut" to ::bounceInOut
    )

    fun byName(name: String): (Float) -> Float = BY_NAME[name] ?: ::linear
}
