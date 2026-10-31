package com.mottv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.AttributeSet
import android.view.View

/**
 * Small Wi-Fi status glyph drawn with canvas arcs (no emoji, no asset).
 * [level] 0..4 = signal strength arcs lit; -1 = offline (all arcs dim).
 * This is a passive indicator — it is never focusable/clickable and has no
 * pill/button styling (phase-3: the old "Wi-Fi" pill looked tappable).
 */
class WifiIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var level: Int = -1
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        isFocusable = false
        isClickable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h * 0.92f // arcs fan upward from a bottom-center point
        val maxR = w * 0.52f
        paint.strokeWidth = (w * 0.11f).coerceAtLeast(1.5f)
        val online = level >= 0

        // 3 arcs; arc i (0=innermost) lights when signal level (0..4) >= i+1.
        for (i in 0 until 3) {
            val r = maxR * (0.38f + 0.31f * i)
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)
            val lit = online && level >= i + 1
            paint.color = when {
                lit -> Color.WHITE
                online -> Color.argb(70, 255, 255, 255)
                else -> Color.argb(50, 255, 255, 255)
            }
            canvas.drawArc(rect, 215f, 110f, false, paint)
        }
        dotPaint.color = if (online) Color.WHITE else Color.argb(50, 255, 255, 255)
        canvas.drawCircle(cx, cy, w * 0.075f, dotPaint)

        if (!online) {
            // Subtle diagonal slash for "not connected".
            paint.color = Color.argb(160, 255, 120, 120)
            paint.strokeWidth = (w * 0.08f).coerceAtLeast(1.2f)
            canvas.drawLine(w * 0.18f, h * 0.82f, w * 0.82f, h * 0.14f, paint)
        }
    }
}

/** Reads the REAL Wi-Fi state for the home status indicator. */
object WifiStatus {

    /** Returns (displayText, iconLevel) — iconLevel -1 when offline. */
    fun describe(context: Context): Pair<String, Int> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = try {
            cm?.getNetworkCapabilities(cm.activeNetwork)
        } catch (e: Exception) {
            null
        }
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (!onWifi) return "Tidak terhubung" to -1

        var ssid: String? = null
        var level = 2 // connected but strength unknown → middle arcs
        try {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val info = wm?.connectionInfo
            if (info != null) {
                @Suppress("DEPRECATION")
                level = WifiManager.calculateSignalLevel(info.rssi, 5) // 0..4
                @Suppress("DEPRECATION")
                ssid = info.ssid?.trim()?.trim('"')?.takeIf {
                    it.isNotEmpty() && !it.equals("<unknown ssid>", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            // SSID needs location permission on modern Android — without it
            // we honestly fall back to a generic "connected" label.
        }
        val strength = when (level) {
            4, 3 -> "Kuat"
            2 -> "Sedang"
            1 -> "Lemah"
            else -> "Sangat lemah"
        }
        val label = ssid ?: "Wi-Fi Terhubung"
        return "$label · $strength" to level
    }
}
