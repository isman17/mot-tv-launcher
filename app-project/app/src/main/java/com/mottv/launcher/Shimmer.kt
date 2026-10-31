package com.mottv.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import java.util.Collections
import java.util.WeakHashMap

/**
 * Shared shimmer driver (phase 4.3): ONE ValueAnimator for the whole app,
 * running only while at least one [ShimmerBar] is attached. Every bar reads
 * the same [phase], so all skeletons on a screen sweep in sync, and the
 * animator stops by itself when the last skeleton leaves the window —
 * deliberately TV-cheap (no per-view animators, no threads).
 */
object Shimmer {
    @Volatile
    var phase: Float = 0f
        private set

    private val bars = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private var animator: ValueAnimator? = null

    fun register(v: View) {
        val needStart = synchronized(bars) { bars.add(v); bars.size == 1 }
        if (needStart) start()
    }

    fun unregister(v: View) {
        val empty = synchronized(bars) { bars.remove(v); bars.isEmpty() }
        if (empty) stop()
    }

    private fun start() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                phase = anim.animatedValue as Float
                val snapshot = synchronized(bars) { bars.toList() }
                for (v in snapshot) v.invalidate()
            }
            start()
        }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }
}

/**
 * One skeleton placeholder shape: a rounded rect in the skeleton base
 * colour with a soft light band sweeping across it (~1200 ms loop,
 * phase-shared with every other bar via [Shimmer]).
 *
 * Not focusable / not clickable — a skeleton must never trap the D-pad.
 * Set [cornerRadiusDp] high (e.g. 999) for circles/pills.
 */
class ShimmerBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var cornerRadiusDp: Float = 10f

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A3E47")
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        isFocusable = false
        isClickable = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Shimmer.register(this)
    }

    override fun onDetachedFromWindow() {
        Shimmer.unregister(this)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val r = cornerRadiusDp * resources.displayMetrics.density
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, basePaint)
        // Light band (~0.15 alpha peak) sweeping left → right, looped.
        val band = w * 0.7f
        val startX = -band + Shimmer.phase * (w + band)
        glowPaint.shader = LinearGradient(
            startX, 0f, startX + band, 0f,
            intArrayOf(0x00FFFFFF, 0x26FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, r, r, glowPaint)
        glowPaint.shader = null
    }
}
