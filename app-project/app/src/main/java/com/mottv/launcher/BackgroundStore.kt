package com.mottv.launcher

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import java.io.File

/** Stores and applies the launcher background selection (design frame 9). */
object BackgroundStore {
    const val TYPE_DEFAULT = "default"
    const val TYPE_GUNUNG = "gunung"
    const val TYPE_PANTAI = "pantai"
    const val TYPE_KOTA = "kota"
    const val TYPE_CUSTOM = "custom"

    const val MODE_CROP = "crop"   // Penuhi Layar
    const val MODE_FIT = "fit"     // Pas di Layar

    private const val PREFS = "launcher_prefs"
    private const val KEY_TYPE = "bg_type"
    private const val KEY_OVERLAY = "bg_overlay_pct"
    private const val KEY_MODE = "bg_mode"

    fun customFile(context: Context): File = File(context.filesDir, "bg_custom.jpg")

    fun getType(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TYPE, TYPE_DEFAULT) ?: TYPE_DEFAULT

    fun getOverlayPct(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_OVERLAY, 45)

    fun getMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_CROP) ?: MODE_CROP

    fun save(context: Context, type: String? = null, overlayPct: Int? = null, mode: String? = null) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (type != null) e.putString(KEY_TYPE, type)
        if (overlayPct != null) e.putInt(KEY_OVERLAY, overlayPct)
        if (mode != null) e.putString(KEY_MODE, mode)
        e.apply()
    }

    /**
     * Phase 3: Gunung/Pantai/Kota Malam are REAL bundled photos now
     * (drawable-nodpi JPEGs, 1920×1080 q80, generated for this launcher) —
     * in 0.1–0.2 they were only gradient drawables and the user noticed.
     * "Abu Gelap" stays the gradient default.
     */
    fun drawableResFor(type: String): Int = when (type) {
        TYPE_GUNUNG -> R.drawable.bg_photo_gunung
        TYPE_PANTAI -> R.drawable.bg_photo_pantai
        TYPE_KOTA -> R.drawable.bg_photo_kota
        else -> R.drawable.bg_default
    }

    /**
     * Applies the stored background to [imageView] and the darkness overlay to [overlayView].
     * Custom photo wins when type == custom and the file exists; otherwise
     * falls back to the bundled choice (photo or default gradient).
     */
    fun applyTo(context: Context, imageView: ImageView, overlayView: View) {
        val type = getType(context)
        val mode = getMode(context)
        imageView.scaleType =
            if (mode == MODE_FIT) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP

        val custom = customFile(context)
        if (type == TYPE_CUSTOM && custom.exists()) {
            val bmp = BitmapFactory.decodeFile(custom.absolutePath)
            if (bmp != null) {
                imageView.setImageBitmap(bmp)
            } else {
                imageView.setImageResource(R.drawable.bg_default)
            }
        } else {
            imageView.setImageResource(drawableResFor(type))
        }
        val pct = getOverlayPct(context).coerceIn(0, 80)
        val alpha = (pct * 255) / 100
        overlayView.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
    }
}
