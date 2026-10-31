package com.mottv.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

/** HTTP failure carrying the status code so callers can show it diagnostically. */
class HttpException(val code: Int) : IllegalStateException("HTTP $code")

/**
 * TLS certificate-chain failure ("Chain validation failed" on the TV),
 * translated to plain language. Callers that print `e.message` (Hasil
 * Pencarian, Jadwal Sholat, …) automatically show this friendly text
 * instead of the raw exception. Phase 4.3.
 */
class SslValidationException : IllegalStateException(
    "Sertifikat situs tidak bisa diverifikasi — rantai sertifikatnya tidak " +
        "valid/tidak lengkap, atau tanggal & jam TV tidak akurat. " +
        "Cek Pengaturan TV → Tanggal & waktu → otomatis."
)

/** Minimal HTTP helper (no extra dependency). Throws on any failure — callers show honest errors. */
object Net {

    /**
     * Browser-like UA. Phase-2 finding: DuckDuckGo HTML serves a bot-check
     * page to unknown UAs. Phase-3: TheSportsDB also appeared to reject the
     * app's custom UA on the TV ("Gagal memuat" while the same requests
     * worked via curl) — so ALL requests now use this realistic browser UA.
     */
    const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    private fun open(url: String, userAgent: String, accept: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        // Generous timeouts: a living-room TV on Wi-Fi + a rate-limited free
        // API needs more slack than the old 10s/15s (phase-3 bola fix).
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", userAgent)
        conn.setRequestProperty("Accept", accept)
        return conn
    }

    /** True when the failure chain contains a TLS certificate problem. */
    private fun isSslFailure(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t is SSLHandshakeException || t is CertPathValidatorException ||
                t is CertificateException
            ) return true
            t = t.cause
        }
        return false
    }

    /** Runs [block], rethrowing certificate failures as [SslValidationException]. */
    private fun <T> guardSsl(block: () -> T): T {
        try {
            return block()
        } catch (e: Exception) {
            if (isSslFailure(e)) throw SslValidationException()
            throw e
        }
    }

    fun getJson(url: String, userAgent: String = BROWSER_UA): JSONObject = guardSsl {
        val conn = open(url, userAgent, "application/json")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            if (code !in 200..299) throw HttpException(code)
            if (body == null) throw IllegalStateException("Respons kosong dari server")
            JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    /** Raw text GET (HTML pages). */
    fun getText(url: String, userAgent: String = BROWSER_UA): String = guardSsl {
        val conn = open(url, userAgent, "text/html,application/xhtml+xml")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("HTTP $code tanpa body")
            if (code !in 200..299) throw HttpException(code)
            body
        } finally {
            conn.disconnect()
        }
    }

    /** Bitmap GET for badges/thumbnails. Returns null on any failure (caller falls back honestly). */
    fun getBitmap(url: String): Bitmap? {
        val conn = open(url, BROWSER_UA, "image/*")
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
