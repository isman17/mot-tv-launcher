package com.mottv.launcher

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * "Tombol Home Langsung ke Sini" (Fase 6) — control screen for the
 * Home-button override. Android only lets the USER enable an
 * accessibility service, from the system's own accessibility settings,
 * so this screen: explains the why in plain words, shows the live
 * status (recomputed every onResume — including right after returning
 * from the settings page), jumps straight to that settings page, and
 * carries the in-app master switch that [HomeInterceptService] checks
 * on every event.
 */
class HomeOverrideActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var toggleBtn: TextView
    private lateinit var diagConnected: TextView
    private lateinit var diagCounts: TextView
    private lateinit var diagError: TextView
    private lateinit var diagInterpretation: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_override)
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))

        statusText = findViewById(R.id.homeOverrideStatus)
        toggleBtn = findViewById(R.id.homeOverrideToggle)
        diagConnected = findViewById(R.id.homeDiagConnected)
        diagCounts = findViewById(R.id.homeDiagCounts)
        diagError = findViewById(R.id.homeDiagError)
        diagInterpretation = findViewById(R.id.homeDiagInterpretation)

        // Focus visibility (fase 6.1): backgrounds are the mot_focus_*
        // selectors in the layout; focusFx adds scale/lift on all three.
        findViewById<TextView>(R.id.homeOverrideEnable).apply {
            setOnClickListener { openAccessibilitySettings() }
            focusFx(1.05f)
        }
        toggleBtn.setOnClickListener {
            HomeOverrideStore.setOverrideOn(this, !HomeOverrideStore.isOverrideOn(this))
            refreshStatus()
        }
        toggleBtn.focusFx(1.05f)
        findViewById<TextView>(R.id.homeOverrideBack).apply {
            setOnClickListener { finish() }
            focusFx(1.05f)
        }

        refreshStatus()
        findViewById<TextView>(R.id.homeOverrideEnable).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))
        refreshStatus()
    }

    private fun refreshStatus() {
        val enabled = isServiceEnabled()
        val on = HomeOverrideStore.isOverrideOn(this)
        statusText.text = when {
            enabled && on -> "Status: AKTIF — tombol Home membuka launcher ini"
            enabled -> "Status: layanan aktif tapi override MATI"
            else -> "Status: BELUM AKTIF"
        }
        toggleBtn.text = if (on) "Override: ON" else "Override: OFF"
        refreshDiary()
    }

    /** "Diagnostik Layanan" card (Fase 6.2): render the [ServiceDiary]
     *  so a silently-reverting enable toggle stops being a mystery —
     *  did the system ever bind the service, did it let go instantly,
     *  and did the service itself record an error? */
    private fun refreshDiary() {
        val s = ServiceDiary.snapshot(this)

        diagConnected.text = if (s.connectedCount > 0) {
            "Terhubung terakhir: ${formatTime(s.connectedAt)}"
        } else {
            "Belum pernah terhubung"
        }

        val releasedAt = maxOf(s.unboundAt, s.destroyedAt)
        diagCounts.text = "Jumlah terhubung: ${s.connectedCount} · Terputus terakhir: " +
            if (releasedAt > 0L) formatTime(releasedAt) else "—"

        diagError.text = if (s.errorAt > 0L) {
            "Kesalahan terakhir: ${s.errorClass ?: "?"}: ${s.errorMessage ?: ""} @ ${formatTime(s.errorAt)}"
        } else {
            "Kesalahan terakhir: Tidak ada"
        }

        diagInterpretation.text = when {
            s.connectedCount == 0 ->
                "Layanan belum pernah dihubungkan sistem — TV menolak mengaktifkan " +
                    "layanan ini (kemungkinan kunci 'setelan terbatas' Android untuk " +
                    "aplikasi sideload)."
            releasedAt > s.connectedAt && releasedAt - s.connectedAt < QUICK_RELEASE_MS ->
                "Layanan sempat terhubung lalu dilepas sistem dengan cepat."
            else -> "Layanan pernah terhubung normal."
        }
    }

    private fun formatTime(ms: Long): String = try {
        SimpleDateFormat("HH:mm:ss dd MMM", Locale("id", "ID")).format(ms)
    } catch (e: Exception) {
        ms.toString()
    }

    /** Enabled = our component appears in the system's enabled-services
     *  list. Checked two independent ways (the Secure setting string and
     *  the AccessibilityManager list) because TV builds vary in how
     *  promptly each reflects a change. */
    private fun isServiceEnabled(): Boolean {
        return try {
            val mine = ComponentName(this, HomeInterceptService::class.java)

            val secure = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val inSecure = secure?.split(':')?.any { entry ->
                val c = ComponentName.unflattenFromString(entry.trim())
                c != null && c.packageName == mine.packageName &&
                    c.className == mine.className
            } == true
            if (inSecure) return true

            val am = getSystemService(AccessibilityManager::class.java)
            am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.any { info ->
                    val si = info.resolveInfo?.serviceInfo
                    si != null && si.packageName == mine.packageName &&
                        si.name == mine.className
                } == true
        } catch (e: Exception) {
            false
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(
                    this,
                    "Halaman aksesibilitas tidak bisa dibuka dari sini — buka " +
                        "manual: Setelan TV → Aksesibilitas → \"Mot TV — " +
                        "Tombol Home\" → aktifkan.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        /** A release (unbind/destroy) landing within this window after
         *  the latest connect counts as "dilepas sistem dengan cepat"
         *  in the diagnostic interpretation (Fase 6.2). */
        private const val QUICK_RELEASE_MS = 10_000L
    }
}
