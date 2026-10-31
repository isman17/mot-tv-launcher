package com.mottv.launcher

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Uniform framed artwork view (phase 4 base, phase 4.2 "seragam" pass —
 * user: "thumbnail bikin ada background + blur supaya seragam").
 *
 * EVERY app tile in the app (Home row, hero preview, Games, All Apps)
 * renders through this one system:
 *
 *  - The whole view is clipped to a consistent rounded frame
 *    ([FRAME_RADIUS_DP]).
 *  - Layer 1 (backdrop): a blurred + darkened copy of the tile's OWN
 *    artwork, CENTER_CROP filling the whole tile — for banners AND icons
 *    alike, so no tile is left sitting on a bare gradient anymore.
 *  - Layer 2 (front): the original artwork FIT_CENTER with a consistent
 *    inset ([FULL_INSET_DP] for full art; a caller pad for centered
 *    icons), its corners rounded ([FRONT_RADIUS_DP]) via an outline over
 *    the padded rect. The foreground is NEVER cropped — the phase-4
 *    no-crop rule stays absolute.
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
    }
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

    init {
        // The whole tile is one rounded frame, whatever the parent is.
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, frameRadiusPx)
            }
        }
        addView(backdrop)
        addView(front)
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

    companion object {
        const val FRAME_RADIUS_DP = 12f
        const val FRONT_RADIUS_DP = 10f
        const val FULL_INSET_DP = 10f
    }
}
