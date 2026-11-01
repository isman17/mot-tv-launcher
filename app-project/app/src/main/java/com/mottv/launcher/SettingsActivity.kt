package com.mottv.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Pengaturan launcher (design frame 9) — menu kiri, penjelasan kanan. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var hint: TextView

    /** Real installed version, read at runtime (no stale hardcoded string). */
    private val appVersion: String
        get() = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))
        hint = findViewById(R.id.settingsHint)

        menuItem(R.id.menuBackground, "Ganti gambar background Beranda: Abu Gelap, Gunung, Pantai, Kota Malam, atau gambar sendiri dari penyimpanan TV.") {
            startActivity(Intent(this, BackgroundActivity::class.java))
        }
        menuItem(R.id.menuLocation, "Lokasi jadwal sholat diatur langsung di modul Jadwal Sholat dan tersimpan otomatis.") {
            val i = Intent(this, PlaceholderActivity::class.java).apply {
                putExtra(PlaceholderActivity.EXTRA_TITLE, "Lokasi Sholat")
                putExtra(
                    PlaceholderActivity.EXTRA_BODY,
                    "Lokasi jadwal sholat diatur langsung di modul Jadwal Sholat — " +
                        "cari kota/kabupaten di kolom pencariannya, pilihan terakhir tersimpan otomatis."
                )
                putExtra(PlaceholderActivity.EXTRA_BUTTON, "Buka Jadwal Sholat")
                putExtra(PlaceholderActivity.EXTRA_TARGET, "sholat")
            }
            startActivity(i)
        }
        menuItem(R.id.menuLanguage, "Pilihan bahasa (menyusul).") {
            val i = Intent(this, PlaceholderActivity::class.java).apply {
                putExtra(PlaceholderActivity.EXTRA_TITLE, "Bahasa")
                putExtra(
                    PlaceholderActivity.EXTRA_BODY,
                    "Pilihan bahasa belum tersedia di versi $appVersion.\nLauncher saat ini memakai Bahasa Indonesia."
                )
            }
            startActivity(i)
        }
        menuItem(R.id.menuUpdate, "Periksa versi terbaru Mot TV Launcher dari GitHub Releases proyek ini, unduh, dan pasang dari sini. Versi kamu sekarang: $appVersion.") {
            startActivity(Intent(this, UpdateActivity::class.java))
        }
        menuItem(R.id.menuDefaultHome, "Pilih Mot TV Launcher sebagai aplikasi Beranda — tombol ini membuka langsung layar \"Aplikasi Beranda\" bawaan sistem, tinggal pilih Mot TV Launcher di sana.") {
            // Straight to the system's own Home-app chooser — no more
            // hunting through the TV's settings menus for the stock
            // launcher entry (the friction that prompted this item).
            try {
                startActivity(
                    Intent(Settings.ACTION_HOME_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: ActivityNotFoundException) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (e2: Exception) {
                    Toast.makeText(
                        this,
                        "Layar pilihan aplikasi Beranda tidak bisa dibuka dari sini — " +
                            "atur manual lewat Pengaturan TV → Aplikasi → Aplikasi default → Aplikasi Beranda.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        menuItem(R.id.menuAndroid, "Buka Pengaturan bawaan Android TV (Wi-Fi, Bluetooth, aplikasi, tentang TV, dan lainnya). Pengaturan sistem ini app terpisah dan selalu bisa dibuka.") {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Pengaturan Android tidak bisa dibuka dari sini", Toast.LENGTH_SHORT).show()
            }
        }
        menuItem(R.id.menuAbout, "Tentang Mot TV Launcher.") {
            val i = Intent(this, PlaceholderActivity::class.java).apply {
                putExtra(PlaceholderActivity.EXTRA_TITLE, "Tentang")
                putExtra(PlaceholderActivity.EXTRA_SHOW_ICON, true)
                putExtra(
                    PlaceholderActivity.EXTRA_BODY,
                    "Mot TV Launcher v$appVersion\n" +
                        "Launcher custom untuk TCL Google TV.\n\n" +
                        "Beranda (banner app asli) + Semua App, Games, Pencarian & hasil " +
                        "(DuckDuckGo), pemutar video (Media3), Jadwal Bola (ESPN + " +
                        "TheSportsDB), Jadwal Sholat (data MyQuran/Kemenag), " +
                        "Pengaturan + background."
                )
            }
            startActivity(i)
        }

        findViewById<TextView>(R.id.menuBackground).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))
    }

    private fun menuItem(id: Int, hintText: String, onClick: () -> Unit) {
        val tv = findViewById<TextView>(id)
        tv.setOnClickListener { onClick() }
        tv.setOnFocusChangeListener { v, hasFocus ->
            val t = v as TextView
            if (hasFocus) {
                hint.text = hintText
                t.background = ContextCompat.getDrawable(this, R.drawable.pill_on)
                if (id != R.id.menuAndroid) t.setTextColor(0xFF191C22.toInt())
            } else {
                t.background = ContextCompat.getDrawable(this, R.drawable.menu_item)
                t.setTextColor(if (id == R.id.menuAndroid) 0xFFFFD166.toInt() else 0xFFFFFFFF.toInt())
            }
        }
    }
}
