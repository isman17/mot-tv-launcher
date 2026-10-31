package com.mottv.launcher

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder

/**
 * In-app WebView browser. Opened from the Search page (direct URL entry or a
 * web result) — search itself lives in SearchActivity/ResultsActivity, and
 * direct media URLs go to PlayerActivity instead of here.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))

        urlInput = findViewById(R.id.urlInput)
        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

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
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
    }
}
