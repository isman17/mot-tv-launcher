package com.mottv.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Pemutar Video (design frame 5): Media3 ExoPlayer for DIRECT media URLs
 * (.mp4/.m3u8/.mpd/…). Web pages are never played here — a non-media URL is
 * handed off to the browser module instead of failing silently, and playback
 * errors surface honestly with a browser fallback button.
 * Controls: D-pad (center = play/pause, left/right = ∓10s), auto-hide 4s.
 */
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var topBar: View
    private lateinit var controlsBar: View
    private lateinit var seekBar: SeekBar
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView
    private lateinit var playPauseBtn: TextView
    private lateinit var errorBox: View
    private lateinit var errorText: TextView

    private var mediaUrl: String = ""
    private val handler = Handler(Looper.getMainLooper())

    private val progressTick = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }
    private val hideControls = Runnable {
        if (player?.isPlaying == true) {
            topBar.visibility = View.GONE
            controlsBar.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        // Honest routing: this screen only plays direct media. A web page goes
        // to the browser module instead of erroring inside the player.
        if (mediaUrl.isBlank() || !MediaRouter.isDirectMedia(mediaUrl)) {
            if (mediaUrl.isNotBlank()) {
                startActivity(
                    Intent(this, BrowserActivity::class.java)
                        .putExtra(BrowserActivity.EXTRA_URL, mediaUrl)
                )
            } else {
                Toast.makeText(this, "Tidak ada media buat diputar", Toast.LENGTH_SHORT).show()
            }
            finish()
            return
        }

        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.playerView)
        topBar = findViewById(R.id.topBar)
        controlsBar = findViewById(R.id.controlsBar)
        seekBar = findViewById(R.id.seekBar)
        timeCurrent = findViewById(R.id.timeCurrent)
        timeTotal = findViewById(R.id.timeTotal)
        playPauseBtn = findViewById(R.id.btnPlayPause)
        errorBox = findViewById(R.id.errorBox)
        errorText = findViewById(R.id.errorText)

        findViewById<TextView>(R.id.playerTitle).text = title.ifBlank { mediaUrl }
        findViewById<TextView>(R.id.playerDomain).text =
            MediaRouter.hostOf(mediaUrl) ?: mediaUrl

        val exo = ExoPlayer.Builder(this).build()
        player = exo
        playerView.player = exo
        playerView.useController = false
        playerView.keepScreenOn = true

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playPauseBtn.text = if (isPlaying) "❚❚ Jeda" else "▶ Putar"
                pokeControls()
            }

            override fun onPlayerError(error: PlaybackException) {
                errorText.text =
                    "Video ini tidak bisa diputar di pemutar bawaan.\n(${error.errorCodeName})"
                errorBox.visibility = View.VISIBLE
                topBar.visibility = View.VISIBLE
                controlsBar.visibility = View.GONE
                findViewById<TextView>(R.id.btnOpenBrowser).requestFocus()
            }
        })

        exo.setMediaItem(MediaItem.fromUri(mediaUrl))
        exo.prepare()
        exo.playWhenReady = true

        wireButton(R.id.btnRestart) {
            exo.seekTo(0)
        }
        wireButton(R.id.btnBack10) {
            exo.seekTo(maxOf(0, exo.currentPosition - 10_000))
        }
        wireButton(R.id.btnFwd10) {
            val dur = exo.duration
            val target = exo.currentPosition + 10_000
            exo.seekTo(if (dur != C.TIME_UNSET) minOf(target, dur) else target)
        }
        wireButton(R.id.btnEnd) {
            val dur = exo.duration
            if (dur != C.TIME_UNSET) exo.seekTo(dur)
        }
        playPauseBtn.apply {
            // Fase 6.1: bg is mot_focus_white in the layout (unfocused =
            // old white pill; focused adds a gold ring — a white ring
            // would be invisible on the white fill).
            focusFx(1.06f)
            setOnClickListener { togglePlayPause() }
        }
        wireButton(R.id.btnSubs) { toggleSubtitles() }
        findViewById<TextView>(R.id.btnOpenBrowser).apply {
            focusFx(1.06f) // bg is mot_focus_gold in layout
            setOnClickListener {
                startActivity(
                    Intent(this@PlayerActivity, BrowserActivity::class.java)
                        .putExtra(BrowserActivity.EXTRA_URL, mediaUrl)
                )
                finish()
            }
        }
        findViewById<TextView>(R.id.btnClose).apply {
            focusFx(1.06f)
            setOnClickListener { finish() }
        }

        pokeControls()
        playPauseBtn.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(progressTick)
        handler.post(progressTick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(progressTick)
        handler.removeCallbacks(hideControls)
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun wireButton(id: Int, onClick: () -> Unit) {
        findViewById<TextView>(id).apply {
            // Fase 6.1: bg is the shared mot_focus_pill selector (layout);
            // focusFx adds scale/lift. Replaces the old pill_on swap.
            focusFx(1.06f)
            setOnClickListener {
                onClick()
                pokeControls()
            }
        }
    }

    private fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
        pokeControls()
    }

    private fun toggleSubtitles() {
        val exo = player ?: return
        val hasText = exo.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
        if (!hasText) {
            Toast.makeText(this, "Video ini tidak punya subtitel", Toast.LENGTH_SHORT).show()
            return
        }
        val disabled = exo.trackSelectionParameters.disabledTrackTypes
            .contains(C.TRACK_TYPE_TEXT)
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !disabled)
            .build()
        Toast.makeText(
            this,
            if (disabled) "Subtitel dinyalakan" else "Subtitel dimatikan",
            Toast.LENGTH_SHORT
        ).show()
        pokeControls()
    }

    private fun pokeControls() {
        if (errorBox.visibility == View.VISIBLE) return
        topBar.visibility = View.VISIBLE
        controlsBar.visibility = View.VISIBLE
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, 4_000)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val exo = player ?: return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (currentFocus is TextView && currentFocus != playPauseBtn &&
                    errorBox.visibility != View.VISIBLE
                ) {
                    // Let focused buttons handle their own OK press.
                    super.onKeyDown(keyCode, event)
                } else {
                    togglePlayPause(); true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // Controls visible → left/right move button focus (the ±10 dtk
                // buttons do the seeking). Controls hidden → seek directly.
                if (controlsBar.visibility == View.VISIBLE) {
                    super.onKeyDown(keyCode, event)
                } else {
                    pokeControls()
                    exo.seekTo(maxOf(0, exo.currentPosition - 10_000)); true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (controlsBar.visibility == View.VISIBLE) {
                    super.onKeyDown(keyCode, event)
                } else {
                    pokeControls()
                    val dur = exo.duration
                    val target = exo.currentPosition + 10_000
                    exo.seekTo(if (dur != C.TIME_UNSET) minOf(target, dur) else target); true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                pokeControls()
                super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_BACK -> {
                finish(); true
            }
            else -> {
                pokeControls()
                super.onKeyDown(keyCode, event)
            }
        }
    }

    private fun updateProgress() {
        val exo = player ?: return
        val dur = exo.duration
        val pos = exo.currentPosition
        timeCurrent.text = formatTime(pos)
        if (dur != C.TIME_UNSET && dur > 0) {
            timeTotal.text = formatTime(dur)
            seekBar.progress = ((pos * 1000) / dur).toInt()
        } else {
            timeTotal.text = "--:--"
            seekBar.progress = 0
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
    }
}
