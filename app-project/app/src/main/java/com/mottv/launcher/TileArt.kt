package com.mottv.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.util.WeakHashMap

/**
 * Real artwork resolved from PackageManager for one app: its leanback
 * banner and its launcher icon, kept separate on purpose.
 *
 * Phase-3 fix ("thumbnail di Beranda kok burik"): 0.2.0 stretched whatever
 * it got — often a low-res icon — across a full 16:9 tile with CENTER_CROP,
 * which is exactly what made tiles look blurry/pixelated on the TV.
 * Rule now: a banner is only used to fill a tile when it is genuinely
 * wide enough (>= [TileArt.MIN_BANNER_WIDTH_PX]); otherwise the ICON is
 * shown centered at (near) natural size over the tile's brand gradient.
 */
class Artwork(
    val banner: Drawable?,
    val icon: Drawable?,
    val bannerWidthPx: Int
)

object TileArt {
    const val MIN_BANNER_WIDTH_PX = 320

    /** Max upscale factor we tolerate for centered icons (≈2x). */
    const val MAX_ICON_UPSCALE = 2.0f

    fun widthPx(d: Drawable?): Int = when {
        d == null -> 0
        d is BitmapDrawable -> d.bitmap?.width ?: d.intrinsicWidth
        else -> d.intrinsicWidth
    }

    /** Enable bilinear filtering so scaled bitmaps don't alias/block. */
    fun prepare(d: Drawable?) {
        (d as? BitmapDrawable)?.setFilterBitmap(true)
    }

    fun isUsableBanner(a: Artwork?): Boolean =
        a != null && a.banner != null && a.bannerWidthPx >= MIN_BANNER_WIDTH_PX

    /** The drawable to show centered when there is no usable banner. */
    fun centeredDrawable(a: Artwork?): Drawable? = a?.icon ?: a?.banner

    // ---------- Phase 4: no-crop letterbox backdrop ----------

    private val backdropCache = WeakHashMap<Bitmap, Bitmap>()

    /** Rasterise any drawable to a bitmap (null when it has no intrinsic size). */
    fun toBitmap(d: Drawable?): Bitmap? {
        if (d == null) return null
        (d as? BitmapDrawable)?.bitmap?.let { return it }
        val w = d.intrinsicWidth
        val h = d.intrinsicHeight
        if (w <= 0 || h <= 0) return null
        val bmp = Bitmap.createBitmap(
            w.coerceAtMost(512), h.coerceAtMost(512), Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, bmp.width, bmp.height)
        d.draw(canvas)
        return bmp
    }

    /**
     * Blurred + darkened copy of [d]'s bitmap, used as the letterbox
     * backdrop behind a FIT_CENTER foreground (phase-4 "thumbnail
     * kepotong" fix: artwork is never cropped again).
     *
     * Phase 4.3 (user: "background blurnya masih kurang ngeblend"): the
     * blur is HEAVIER than phase 4 — a ÷24 downscale (was ÷16) and TWO
     * filtered upscale passes with an extra downscale between them,
     * which stacks into a smooth gaussian-ish falloff instead of one
     * visible step. Pre-rendered once per source bitmap and cached; the
     * view layer adds overscan + a gradient veil on top (see FitArtView)
     * so the backdrop melts into the tile with no hard rectangle seam.
     * The source bitmap is never recycled (it belongs to the caller).
     */
    fun blurredBackdrop(d: Drawable?): Bitmap? {
        val src = toBitmap(d) ?: return null
        synchronized(backdropCache) {
            backdropCache[src]?.let { return it }
        }
        val temps = ArrayList<Bitmap>()
        fun scaled(b: Bitmap, w: Int, h: Int): Bitmap {
            val r = Bitmap.createScaledBitmap(b, w.coerceAtLeast(1), h.coerceAtLeast(1), true)
            if (r !== src && r !== b) temps.add(r)
            return r
        }
        val small = scaled(src, src.width / 24, src.height / 24)
        val upW = (src.width / 2).coerceAtLeast(2)
        val upH = (src.height / 2).coerceAtLeast(2)
        val up1 = scaled(small, upW, upH)
        // Second smoothing pass: shrink the upscale again, then back up.
        val mid = scaled(up1, upW / 3, upH / 3)
        val smooth = scaled(mid, upW, upH)
        val out = smooth.copy(Bitmap.Config.ARGB_8888, true)
        for (t in temps) if (t !== out) t.recycle()
        // Consistent darkening so foreground art + labels stay readable.
        val canvas = Canvas(out)
        val paint = Paint().apply { color = Color.argb(110, 0, 0, 0) }
        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        synchronized(backdropCache) { backdropCache[src] = out }
        return out
    }
}
