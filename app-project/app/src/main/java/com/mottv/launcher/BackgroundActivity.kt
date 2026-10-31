package com.mottv.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.FileOutputStream
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/** Background picker (design frame 9, panel kanan). */
class BackgroundActivity : AppCompatActivity() {

    private lateinit var bgImage: ImageView
    private lateinit var bgOverlay: View
    private lateinit var overlayLabel: TextView

    private val options = linkedMapOf<Int, Pair<String, String>>(
        R.id.optDefault to (BackgroundStore.TYPE_DEFAULT to "Abu Gelap (bawaan)"),
        R.id.optGunung to (BackgroundStore.TYPE_GUNUNG to "Gunung"),
        R.id.optPantai to (BackgroundStore.TYPE_PANTAI to "Pantai"),
        R.id.optKota to (BackgroundStore.TYPE_KOTA to "Kota Malam"),
        R.id.optCustom to (BackgroundStore.TYPE_CUSTOM to "Gambar Sendiri (dari penyimpanan TV)…")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_background)
        bgImage = findViewById(R.id.bgImage)
        bgOverlay = findViewById(R.id.bgOverlay)
        overlayLabel = findViewById(R.id.overlayLabel)

        for ((id, pair) in options) {
            val tv = findViewById<TextView>(id)
            focusStyle(tv)
            tv.setOnClickListener {
                if (pair.first == BackgroundStore.TYPE_CUSTOM) {
                    pickImage()
                } else {
                    BackgroundStore.save(this, type = pair.first)
                    refresh()
                }
            }
        }

        val seek = findViewById<SeekBar>(R.id.overlaySeek)
        seek.progress = BackgroundStore.getOverlayPct(this)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    BackgroundStore.save(this@BackgroundActivity, overlayPct = progress)
                    applyPreview()
                    updateOverlayLabel()
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        modeButton(R.id.modeCrop, BackgroundStore.MODE_CROP)
        modeButton(R.id.modeFit, BackgroundStore.MODE_FIT)

        refresh()
        findViewById<TextView>(R.id.optDefault).requestFocus()
    }

    private fun modeButton(id: Int, mode: String) {
        val tv = findViewById<TextView>(id)
        focusStyle(tv)
        tv.setOnClickListener {
            BackgroundStore.save(this, mode = mode)
            refresh()
        }
    }

    private fun focusStyle(tv: TextView) {
        tv.setOnFocusChangeListener { v, hasFocus ->
            val t = v as TextView
            if (hasFocus) {
                t.background = ContextCompat.getDrawable(this, R.drawable.pill_on)
                t.setTextColor(0xFF191C22.toInt())
            } else {
                t.background = ContextCompat.getDrawable(this, R.drawable.menu_item)
                t.setTextColor(0xFFFFFFFF.toInt())
            }
        }
    }

    private fun refresh() {
        val type = BackgroundStore.getType(this)
        for ((id, pair) in options) {
            val tv = findViewById<TextView>(id)
            tv.text = pair.second + if (pair.first == type) "  ✓" else ""
        }
        val mode = BackgroundStore.getMode(this)
        findViewById<TextView>(R.id.modeCrop).text =
            "Penuhi Layar" + if (mode == BackgroundStore.MODE_CROP) "  ✓" else ""
        findViewById<TextView>(R.id.modeFit).text =
            "Pas di Layar" + if (mode == BackgroundStore.MODE_FIT) "  ✓" else ""
        updateOverlayLabel()
        applyPreview()
    }

    private fun updateOverlayLabel() {
        overlayLabel.text = "Kegelapan lapisan di atas gambar: ${BackgroundStore.getOverlayPct(this)}%"
    }

    private fun applyPreview() {
        BackgroundStore.applyTo(this, bgImage, bgOverlay)
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_PICK_IMAGE)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "TV ini tidak punya pemilih file — gambar sendiri belum bisa dipilih di perangkat ini.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Deprecated("Activity result API lama, cukup untuk v1")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK_IMAGE || resultCode != Activity.RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Permission persistence is best-effort; we copy the file anyway.
        }
        Toast.makeText(this, "Menyalin gambar…", Toast.LENGTH_SHORT).show()
        thread {
            val ok = copyDownscaled(uri)
            runOnUiThread {
                if (ok) {
                    BackgroundStore.save(this, type = BackgroundStore.TYPE_CUSTOM)
                    refresh()
                    Toast.makeText(this, "Background diganti ke gambar sendiri", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Gambar gagal dibaca dari penyimpanan", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Copies the picked image into internal storage, downscaled to max 1920px wide. */
    private fun copyDownscaled(uri: Uri): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return false
            var sample = 1
            while (bounds.outWidth / sample > 1920) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp: Bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false
            val scaled = if (bmp.width > 1920) {
                val h = (bmp.height * (1920f / bmp.width)).roundToInt()
                Bitmap.createScaledBitmap(bmp, 1920, h, true)
            } else bmp
            FileOutputStream(BackgroundStore.customFile(this)).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val REQ_PICK_IMAGE = 42
    }
}
