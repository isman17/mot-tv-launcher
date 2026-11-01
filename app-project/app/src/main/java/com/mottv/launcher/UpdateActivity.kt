package com.mottv.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Cek Pembaruan (phase 5): self-update from the project's OWN GitHub
 * Releases (isman17/mot-tv-launcher). Flow: GET the latest release,
 * compare its tag with the installed versionName, optionally download
 * the .apk asset to the cache and hand it to the system installer via
 * FileProvider.
 *
 * Honesty rules, same as the rest of the app:
 *  - The repo may be PRIVATE: the anonymous Releases API then answers
 *    404 (GitHub hides private repos as "not found"). Any failure shows
 *    a plain message with the reason — never a crash, never fake data.
 *  - Installing needs the user's one-time "unknown app sources" grant
 *    for this app; when it's missing we send the user to that exact
 *    system settings page with an explanation instead of failing mute.
 */
class UpdateActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var statusText: TextView
    private lateinit var notesText: TextView
    private lateinit var actionBtn: TextView

    private enum class State {
        IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING,
        NEED_PERMISSION, READY_TO_INSTALL, WORKING
    }

    private var state = State.IDLE
    private var latestTag: String? = null
    private var latestVersion: String? = null
    private var apkAssetUrl: String? = null
    private var apkAssetName: String? = null
    private var downloadedFile: File? = null

    /** Real installed version, read at runtime (no stale hardcoded string). */
    private val currentVersion: String
        get() = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))

        statusText = findViewById(R.id.updateStatus)
        notesText = findViewById(R.id.updateNotes)
        actionBtn = findViewById(R.id.updateAction)
        findViewById<TextView>(R.id.updateVersion).text = "Versi kamu saat ini: $currentVersion"
        statusText.text =
            "Pembaruan diambil dari GitHub Releases proyek ini " +
                "(isman17/mot-tv-launcher). Tekan tombol di bawah buat memeriksa."

        actionBtn.setOnClickListener { onAction() }
        findViewById<TextView>(R.id.updateBack).apply {
            setOnClickListener { finish() }
            focusFx(1.05f)
        }
        // Focus visibility (fase 6.1): background is the mot_focus_gold
        // selector in the layout; this adds the scale/lift half. The
        // button keeps this listener in every state (Periksa / Unduh &
        // Pasang / Beri Izin Instal / Pasang Sekarang) — only its text
        // and enabled-ness change.
        actionBtn.focusFx(1.05f)
        actionBtn.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))
        // Returning from the unknown-sources settings page: if the grant
        // was given and an APK is already downloaded, offer the install.
        if (state == State.NEED_PERMISSION && downloadedFile != null &&
            packageManager.canRequestPackageInstalls()
        ) {
            state = State.READY_TO_INSTALL
            actionBtn.text = "Pasang Sekarang"
            statusText.text =
                "Izin instal sudah diberikan. Tekan \"Pasang Sekarang\" buat " +
                    "membuka pemasang sistem."
        }
    }

    private fun onAction() {
        when (state) {
            State.IDLE, State.UP_TO_DATE -> checkLatest()
            State.AVAILABLE -> downloadAndMaybeInstall()
            State.READY_TO_INSTALL -> downloadedFile?.let { launchInstaller(it) }
            State.NEED_PERMISSION -> openUnknownSourcesSettings()
            else -> { /* CHECKING / DOWNLOADING / WORKING: ignore taps */ }
        }
    }

    // ---------------- Check ----------------

    private fun checkLatest() {
        state = State.CHECKING
        actionBtn.text = "Memeriksa…"
        actionBtn.isEnabled = false
        notesText.visibility = View.GONE
        statusText.text = "Menghubungi GitHub Releases…"
        io.execute {
            try {
                val json = Net.getJson(LATEST_RELEASE_URL)
                val tag = json.optString("tag_name").ifBlank { null }
                    ?: throw IllegalStateException("Respons GitHub tidak memuat tag_name")
                val version = tag.removePrefix("v").trim()
                var assetUrl: String? = null
                var assetName: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val name = a.optString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            assetName = name
                            assetUrl = a.optString("browser_download_url")
                                .ifBlank { null }
                            break
                        }
                    }
                }
                val notes = json.optString("body").trim()
                handler.post {
                    actionBtn.isEnabled = true
                    latestTag = tag
                    latestVersion = version
                    apkAssetUrl = assetUrl
                    apkAssetName = assetName
                    when {
                        !isNewer(version, currentVersion) -> {
                            state = State.UP_TO_DATE
                            actionBtn.text = "Periksa Lagi"
                            statusText.text =
                                "Sudah versi terbaru. Rilis terakhir di GitHub: " +
                                    "$tag — versi kamu: $currentVersion."
                        }
                        assetUrl == null -> {
                            state = State.UP_TO_DATE
                            actionBtn.text = "Periksa Lagi"
                            statusText.text =
                                "Versi $version tersedia ($tag), tapi rilisnya " +
                                    "tidak memuat file APK — belum bisa dipasang dari sini."
                        }
                        else -> {
                            state = State.AVAILABLE
                            actionBtn.text = "Unduh & Pasang"
                            statusText.text = "Versi $version tersedia — kamu masih di $currentVersion."
                            if (notes.isNotEmpty()) {
                                notesText.visibility = View.VISIBLE
                                notesText.text = notes.take(600)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val reason = shortError(e)
                handler.post {
                    actionBtn.isEnabled = true
                    state = State.IDLE
                    actionBtn.text = "Periksa Pembaruan"
                    statusText.text =
                        "Belum bisa memeriksa pembaruan (repo privat / tidak " +
                            "ada koneksi). Versi kamu: $currentVersion. [$reason]"
                }
            }
        }
    }

    /** Dot-separated numeric compare; non-numeric suffixes ignored per segment. */
    private fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String): List<Int> =
            v.split(".").map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun shortError(e: Exception): String = when (e) {
        is SslValidationException ->
            "sertifikat tidak bisa diverifikasi — cek tanggal & jam TV"
        is HttpException -> "HTTP ${e.code}"
        else -> e.message?.take(80) ?: e.javaClass.simpleName
    }

    // ---------------- Download + install ----------------

    private fun downloadAndMaybeInstall() {
        val url = apkAssetUrl ?: return
        val name = apkAssetName ?: "mot-tv-launcher-${latestTag ?: "update"}.apk"
        state = State.DOWNLOADING
        actionBtn.text = "Mengunduh…"
        actionBtn.isEnabled = false
        statusText.text = "Mengunduh $name…"
        io.execute {
            try {
                val dir = File(cacheDir, "updates").apply { mkdirs() }
                val outFile = File(dir, name)
                downloadTo(url, outFile) { pct, label ->
                    handler.post {
                        statusText.text = if (pct >= 0) {
                            "Mengunduh $name… $pct%"
                        } else {
                            "Mengunduh $name… $label"
                        }
                    }
                }
                handler.post {
                    actionBtn.isEnabled = true
                    downloadedFile = outFile
                    when {
                        outFile.length() <= 0L -> {
                            state = State.AVAILABLE
                            actionBtn.text = "Unduh & Pasang"
                            statusText.text =
                                "Unduhan selesai tapi file-nya kosong — coba lagi."
                        }
                        !packageManager.canRequestPackageInstalls() -> {
                            state = State.NEED_PERMISSION
                            actionBtn.text = "Beri Izin Instal"
                            statusText.text =
                                "APK versi ${latestVersion ?: ""} sudah diunduh, " +
                                    "tapi TV belum mengizinkan app ini memasang " +
                                    "aplikasi. Tekan \"Beri Izin Instal\" buat " +
                                    "membuka pengaturan izinnya (cukup sekali), " +
                                    "lalu kembali ke sini buat memasang."
                        }
                        else -> {
                            state = State.READY_TO_INSTALL
                            actionBtn.text = "Pasang Sekarang"
                            statusText.text =
                                "Unduhan selesai (${formatSize(outFile.length())}). " +
                                    "Tekan \"Pasang Sekarang\" buat membuka pemasang sistem."
                            launchInstaller(outFile)
                        }
                    }
                }
            } catch (e: Exception) {
                val reason = shortError(e)
                handler.post {
                    actionBtn.isEnabled = true
                    state = State.AVAILABLE
                    actionBtn.text = "Unduh & Pasang"
                    statusText.text = "Unduhan gagal ($reason) — cek koneksi, lalu coba lagi."
                }
            }
        }
    }

    /** Streaming download with progress callbacks (percent when the server
     *  sends a length, downloaded size otherwise). */
    private fun downloadTo(
        url: String,
        outFile: File,
        onProgress: (Int, String) -> Unit
    ) {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", Net.BROWSER_UA)
            conn.setRequestProperty("Accept", "application/octet-stream")
            val code = conn.responseCode
            if (code !in 200..299) throw HttpException(code)
            val total = conn.contentLengthLong
            var done = 0L
            var lastPct = -1
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct, "")
                            }
                        } else if (done % (512 * 1024) < 64 * 1024) {
                            onProgress(-1, formatSize(done))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (Net.isSslFailurePublic(e)) throw SslValidationException()
            throw e
        } finally {
            conn?.disconnect()
        }
    }

    private fun formatSize(bytes: Long): String =
        if (bytes >= 1024 * 1024) {
            String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        } else {
            "${bytes / 1024} KB"
        }

    private fun openUnknownSourcesSettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Halaman izin tidak bisa dibuka — aktifkan manual: Pengaturan TV " +
                    "→ Aplikasi → Akses aplikasi khusus → Instal aplikasi yang " +
                    "tidak dikenal → Mot TV Launcher.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun launchInstaller(file: File) {
        state = State.WORKING
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            statusText.text =
                "Pemasang sistem dibuka — ikuti langkahnya di layar. " +
                    "App akan mulai ulang sebagai versi ${latestVersion ?: "baru"}."
        } catch (e: Exception) {
            state = State.READY_TO_INSTALL
            actionBtn.text = "Pasang Sekarang"
            statusText.text =
                "Pemasang tidak bisa dibuka (${e.message?.take(80) ?: "error"}) — " +
                    "file tersimpan di cache app, tekan \"Pasang Sekarang\" buat coba lagi."
        }
    }

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/isman17/mot-tv-launcher/releases/latest"
    }
}
