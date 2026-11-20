package dev.dazaike.cadence.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

/**
 * Motion values taken from the web dashboard (`ui.tsx`, `App.tsx`): count-up 0.9 s on
 * cubic-bezier(.16,1,.3,1), nav highlight spring (stiffness 380, damping 32), ring spring
 * (stiffness 60, damping 18), page enter 0.2 s ease-out. Always go through [reduced] so the system
 * "remove animations" setting is respected.
 */
object Motion {
    private val CountUpEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    fun <T> countUp(): AnimationSpec<T> = tween(900, easing = CountUpEasing)

    /** Shared selection highlight: damping 32 at stiffness 380 and unit mass is a 0.82 ratio. */
    fun <T> highlight(): AnimationSpec<T> = spring(dampingRatio = 0.82f, stiffness = 380f)

    /** Rings: damping 18 at stiffness 60 is slightly overdamped, so no overshoot. */
    fun <T> ring(): AnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 60f)

    fun <T> chart(): AnimationSpec<T> = tween(800, easing = FastOutSlowInEasing)

    fun <T> bar(): AnimationSpec<T> = tween(700, easing = FastOutSlowInEasing)

    const val PAGE_MS = 200
}

/** Returns [spec], or an instant snap when the user turned system animations off. */
@Composable
fun <T> reduced(spec: AnimationSpec<T>): AnimationSpec<T> = if (LocalReduceMotion.current) snap() else spec
