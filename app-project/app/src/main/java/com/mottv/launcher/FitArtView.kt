package com.mottv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Uniform framed artwork view (phase 4 base, phase 4.2 "seragam" pass,
 * phase 4.3 "nge-blend" pass — user: "thumbnail dan background blurnya
 * masih kurang ngeblend").
 *
 * EVERY app tile in the app (Home row, hero preview, Games, All Apps)
 * renders through this one system:
 *
 *  - The whole view is clipped to a consistent rounded frame
 *    ([FRAME_RADIUS_DP]).
 *  - Layer 1 (backdrop): a heavily blurred + darkened copy of the tile's
 *    OWN artwork, CENTER_CROP filling the whole tile — for banners AND
 *    icons alike. Phase 4.3: the backdrop ImageView is overscanned
 *    (scaled ×[BACKDROP_OVERSCAN]) so its hard outer edge — and the
 *    brightest/darkest ringing of the blur — sits OUTSIDE the clipped
 *    frame; combined with the finer blur in [TileArt.blurredBackdrop]
 *    there is no visible rectangle seam left.
 *  - Layer 2 (veil): a soft radial gradient in the tile's dark colour
 *    family ([VEIL_RGB]), transparent in the middle and deepening toward
 *    the edges/bottom, so the backdrop melts into the tile surface.
 *  - Layer 3 (front): the original artwork FIT_CENTER with a consistent
 *    inset ([FULL_INSET_DP] for full art; a caller pad for centered
 *    icons), corners rounded ([FRONT_RADIUS_DP]). The foreground is
 *    NEVER cropped — the phase-4 no-crop rule stays absolute. Phase 4.3
 *    adds a soft stacked shadow under the artwork (drawn in
 *    [drawChild]) so it floats instead of ending in a hard border.
 */
class FitArtView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density: Float = resources.displayMetrics.density
    private val frameRadiusPx = FRAME_RADIUS_DP * density
    private val frontRadiusPx = FRONT_RADIUS_DP * density
    private val fullInsetPx = (FULL_INSET_DP * density).toInt()

    private val backdrop = ImageView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        scaleType = ImageView.ScaleType.CENTER_CROP
        // Overscan: push the blurred image ~15% past the frame on every
        // side so its edge never shows inside the tile.
        scaleX = BACKDROP_OVERSCAN
        scaleY = BACKDROP_OVERSCAN
    }
    private val veil = VeilView(context)
    private val front = ImageView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        scaleType = ImageView.ScaleType.FIT_CENTER
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                // Round the padded (inset) rect, i.e. the artwork itself.
                val l = view.paddingLeft
                val t = view.paddingTop
                val r = view.width - view.paddingRight
                val b = view.height - view.paddingBottom
                if (r > l && b > t) {
                    outline.setRoundRect(l, t, r, b, frontRadiusPx)
                } else {
                    outline.setEmpty()
                }
            }
        }
    }

    // Soft shadow layers under the foreground artwork: biggest+ faintest
    // first. Alphas chosen to read as ONE soft shadow, not three borders.
    private val shadowPaints: List<Paint> = listOf(10, 18, 30).map { alpha ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(alpha, 0, 0, 0) }
    }
    private val shadowExpansionsDp = listOf(9f, 5.5f, 2.5f)

    init {
        // The whole tile is one rounded frame, whatever the parent is.
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, frameRadiusPx)
            }
        }
        addView(backdrop)
        addView(veil)
        addView(front)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        // Soft shadow beneath the foreground artwork: painted at the exact
        // moment the front layer is about to be drawn — i.e. above the
        // backdrop + veil, below the artwork itself.
        if (child === front && front.drawable != null && width > 0 && height > 0) {
            val l = front.paddingLeft.toFloat()
            val t = front.paddingTop.toFloat()
            val r = (width - front.paddingRight).toFloat()
            val b = (height - front.paddingBottom).toFloat()
            val drop = 1.5f * density
            for (i in shadowPaints.indices) {
                val e = shadowExpansionsDp[i] * density
                canvas.drawRoundRect(
                    l - e, t - e + drop, r + e, b + e + drop,
                    frontRadiusPx + e, frontRadiusPx + e, shadowPaints[i]
                )
            }
        }
        return super.drawChild(canvas, child, drawingTime)
    }

    /** Full artwork (banner), framed: blurred own-art backdrop + the whole
     *  banner FIT_CENTER with the uniform [FULL_INSET_DP] inset. */
    fun setFullArt(d: Drawable?) {
        setFrontPadding(fullInsetPx)
        front.setImageDrawable(d)
        backdrop.setImageBitmap(TileArt.blurredBackdrop(d))
    }

    /** Small artwork (icon) centered inside the same framed system: its
     *  own blurred backdrop behind it, icon sized by the caller's pad. */
    fun setCentered(d: Drawable?, padPx: Int) {
        setFrontPadding(padPx)
        front.setImageDrawable(d)
        backdrop.setImageBitmap(TileArt.blurredBackdrop(d))
    }

    private fun setFrontPadding(px: Int) {
        front.setPadding(px, px, px, px)
        front.invalidateOutline()
    }

    fun clear() {
        backdrop.setImageDrawable(null)
        front.setImageDrawable(null)
    }

    /**
     * Gradient veil over the backdrop: transparent over the middle of
     * the tile, the tile family's dark tone deepening toward the edges —
     * this is what makes the blurred backdrop read as part of the tile
     * instead of a picture pasted under another picture.
     */
    private inner class VeilView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
            )
            isFocusable = false
            isClickable = false
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return
            val (vr, vg, vb) = VEIL_RGB
            paint.shader = RadialGradient(
                w / 2f, h * 0.42f,
                maxOf(w, h) * 0.78f,
                intArrayOf(
                    Color.argb(0, vr, vg, vb),
                    Color.argb(54, vr, vg, vb),
                    Color.argb(128, vr, vg, vb)
                ),
                floatArrayOf(0.42f, 0.74f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width > 0 && height > 0) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }
    }

    companion object {
        const val FRAME_RADIUS_DP = 12f
        const val FRONT_RADIUS_DP = 10f
        const val FULL_INSET_DP = 10f
        const val BACKDROP_OVERSCAN = 1.15f

        /** Dark tone of the tile/background family (bg gradient end #26282E). */
        val VEIL_RGB = Triple(0x26, 0x28, 0x2E)
    }
}
