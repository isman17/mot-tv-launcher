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
     * kepotong" fix: artwork is never cropped again). The blur is a cheap
     * hard downscale (÷16) followed by a filtered upscale — pre-rendered
     * once per source bitmap and cached, no runtime blur passes.
     */
    fun blurredBackdrop(d: Drawable?): Bitmap? {
        val src = toBitmap(d) ?: return null
        synchronized(backdropCache) {
            backdropCache[src]?.let { return it }
        }
        val smallW = (src.width / 16).coerceAtLeast(1)
        val smallH = (src.height / 16).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
        val upW = (src.width / 2).coerceAtLeast(2)
        val upH = (src.height / 2).coerceAtLeast(2)
        val up = Bitmap.createScaledBitmap(small, upW, upH, true)
        if (small !== src && small !== up) small.recycle()
        val out = up.copy(Bitmap.Config.ARGB_8888, true)
        if (up !== src) up.recycle()
        // Darken so foreground art + labels stay readable over it.
        val canvas = Canvas(out)
        val paint = Paint().apply { color = Color.argb(105, 0, 0, 0) }
        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        synchronized(backdropCache) { backdropCache[src] = out }
        return out
    }
}
