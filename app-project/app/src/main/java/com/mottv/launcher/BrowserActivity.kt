package com.mottv.launcher

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import java.util.Collections

/**
 * In-app WebView browser. Opened from the Search page (direct URL entry or a
 * web result) — search itself lives in SearchActivity/ResultsActivity, and
 * direct media URLs go to PlayerActivity instead of here.
 *
 * Phase 4.3 — SSL errors: the TV showed a dead-end "Chain validation
 * failed" when a site's certificate chain could not be verified. The
 * browser now intercepts onReceivedSslError and NEVER proceeds silently:
 * it shows a remote-friendly panel (host + plain-language explanation)
 * with [Coba Lagi] / [Tetap Buka (Tidak Aman)] / [Kembali]. Choosing
 * "Tetap Buka" approves that host for THIS SESSION ONLY (in-memory set,
 * forgotten when the app process dies) — subsequent loads of the host do
 * not re-prompt.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var sslPanel: View
    private lateinit var sslBody: TextView

    private var pendingSslUrl: String? = null
    private var pendingSslHost: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))

        urlInput = findViewById(R.id.urlInput)
        webView = findViewById(R.id.webView)
        sslPanel = findViewById(R.id.sslPanel)
        sslBody = findViewById(R.id.sslBody)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                val url = error.url
                val host = try {
                    Uri.parse(url)?.host
                } catch (e: Exception) {
                    null
                } ?: url
                // Host approved earlier in this session → proceed quietly.
                if (approvedSslHosts.contains(host)) {
                    handler.proceed()
                    return
                }
                // Never auto-proceed: block the load and ask the user.
                handler.cancel()
                showSslPanel(host, url)
            }
        }

        styleSslButton(findViewById(R.id.sslRetryBtn), gold = true) { onSslRetry() }
        styleSslButton(findViewById(R.id.sslProceedBtn), gold = false) { onSslProceed() }
        styleSslButton(findViewById(R.id.sslBackBtn), gold = false) { dismissSslBack() }

        val incoming = intent.getStringExtra(EXTRA_URL)
        if (!incoming.isNullOrBlank()) {
            urlInput.setText(incoming)
            webView.loadUrl(incoming)
            webView.requestFocus()
        } else {
            val startHtml = """
                <html><body style="background:#2b2e35;color:#eee;font-family:sans-serif;padding:32px">
                <h2>Browser bawaan Mot TV Launcher</h2>
                <p>Ketik alamat web di kolom atas, lalu tekan <b>Buka</b>.
                Buat mencari film, musik, atau video pakai halaman <b>Pencarian</b> —
                hasilnya bisa langsung diputar di pemutar bawaan kalau berupa file media.</p>
                <p style="color:#A8FFFFFF;font-size:90%">Kalau banyak situs gagal dibuka dengan
                pesan sertifikat: cek Pengaturan TV → Tanggal &amp; waktu → nyalakan otomatis.</p>
                </body></html>
            """.trimIndent()
            webView.loadDataWithBaseURL(null, startHtml, "text/html", "utf-8", null)
            urlInput.requestFocus()
        }

        findViewById<TextView>(R.id.goBtn).setOnClickListener { go() }
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                go(); true
            } else false
        }
    }

    private fun styleSslButton(tv: TextView, gold: Boolean, onClick: () -> Unit) {
        fun applyDefault(t: TextView) {
            t.background = ContextCompat.getDrawable(
                this, if (gold) R.drawable.pill_gold else R.drawable.pill_bg
            )
            t.setTextColor(
                if (gold) Color.parseColor("#26221A") else Color.WHITE
            )
        }
        tv.setOnFocusChangeListener { v, hasFocus ->
            val t = v as TextView
            if (hasFocus) {
                t.background = ContextCompat.getDrawable(this, R.drawable.pill_on)
                t.setTextColor(Color.parseColor("#191C22"))
            } else {
                applyDefault(t)
            }
        }
        applyDefault(tv)
        tv.setOnClickListener { onClick() }
    }

    private fun showSslPanel(host: String, url: String) {
        pendingSslHost = host
        pendingSslUrl = url
        sslBody.text =
            "Situs $host memakai sertifikat keamanan yang tidak bisa diverifikasi " +
                "oleh TV ini — rantai sertifikatnya tidak valid atau tidak lengkap, " +
                "atau tanggal & jam TV tidak akurat. Halaman diblokir demi keamanan."
        sslPanel.visibility = View.VISIBLE
        findViewById<TextView>(R.id.sslRetryBtn).requestFocus()
    }

    private fun hideSslPanel() {
        sslPanel.visibility = View.GONE
        pendingSslUrl = null
        pendingSslHost = null
        webView.requestFocus()
    }

    private fun onSslRetry() {
        val url = pendingSslUrl
        hideSslPanel()
        if (url != null) webView.loadUrl(url)
    }

    private fun onSslProceed() {
        val host = pendingSslHost
        val url = pendingSslUrl
        // Session-only approval (memory only — never persisted).
        if (host != null) approvedSslHosts.add(host)
        hideSslPanel()
        if (url != null) webView.loadUrl(url)
    }

    private fun dismissSslBack() {
        sslPanel.visibility = View.GONE
        pendingSslUrl = null
        pendingSslHost = null
        if (webView.canGoBack()) webView.goBack() else finish()
    }

    private fun go() {
        val raw = urlInput.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return
        val url = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            !raw.contains(" ") && raw.contains(".") -> "https://$raw"
            else -> "https://duckduckgo.com/html/?q=" + URLEncoder.encode(raw, "UTF-8")
        }
        webView.loadUrl(url)
        webView.requestFocus()
    }

    override fun onBackPressed() {
        if (sslPanel.visibility == View.VISIBLE) {
            dismissSslBack()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"

        /**
         * Hosts the user explicitly chose to open despite a certificate
         * error. In-memory only — cleared when the app process ends, never
         * written to disk.
         */
        private val approvedSslHosts: MutableSet<String> =
            Collections.synchronizedSet(HashSet())
    }
}
