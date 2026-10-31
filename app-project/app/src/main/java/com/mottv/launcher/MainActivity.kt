package com.mottv.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Beranda (design frame 1) — also the HOME / LEANBACK_LAUNCHER entry point. */
class MainActivity : AppCompatActivity() {

    private lateinit var heroName: TextView
    private lateinit var heroDesc: TextView
    private lateinit var heroArt: FitArtView
    private lateinit var heroGlow: View
    private lateinit var clockPill: TextView
    private lateinit var wifiIcon: WifiIconView
    private lateinit var tileRow: LinearLayout

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            updateWifiStatus()
            clockHandler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        heroName = findViewById(R.id.heroName)
        heroDesc = findViewById(R.id.heroDesc)
        heroArt = findViewById(R.id.heroArt)
        heroGlow = findViewById(R.id.heroGlow)
        clockPill = findViewById(R.id.clockPill)
        wifiIcon = findViewById(R.id.wifiIcon)
        tileRow = findViewById(R.id.tileRow)

        findViewById<TextView>(R.id.greeting).text = greeting()

        setupPill(findViewById(R.id.pillBola)) { startActivity(Intent(this, BolaActivity::class.java)) }
        setupPill(findViewById(R.id.pillSholat)) { startActivity(Intent(this, SholatActivity::class.java)) }
        // "Semua App" button — a real button, so it keeps the pill/button
        // styling (unlike the passive Wi-Fi indicator). Since 0.4.1 it
        // lives beside the daily quote (right end of the quote row)
        // instead of in the top bar, which was getting crowded.
        setupPill(findViewById(R.id.pillAllApps)) { startActivity(Intent(this, AllAppsActivity::class.java)) }
        setupPill(findViewById(R.id.pillSettings)) { startActivity(Intent(this, SettingsActivity::class.java)) }

        showQuote()
        buildTiles()
        updateClock()
        updateWifiStatus()
    }

    override fun onResume() {
        super.onResume()
        // Background (frame 9 choices) applies here.
        BackgroundStore.applyTo(
            this,
            findViewById(R.id.bgImage),
            findViewById(R.id.bgOverlay)
        )
        clockHandler.removeCallbacks(clockTick)
        clockHandler.post(clockTick)
        if (tileRow.childCount > 0 && currentFocus == null) {
            tileRow.getChildAt(0).requestFocus()
        }
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTick)
    }

    private fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 4 -> "Selamat malam"
            h < 11 -> "Selamat pagi"
            h < 15 -> "Selamat siang"
            h < 19 -> "Selamat sore"
            else -> "Selamat malam"
        }
    }

    private fun updateClock() {
        val fmt = SimpleDateFormat("HH:mm · EEE, d MMM", Locale("id", "ID"))
        clockPill.text = fmt.format(Calendar.getInstance().time)
    }

    /**
     * Real Wi-Fi status (phase 3; icon-only since 0.4.1 at user request):
     * just the signal icon, NOT a pill/button — the 0.2.0 "Wi-Fi" pill
     * looked tappable, and the phase-3 SSID/level text made the top bar
     * crowded. [WifiStatus.describe] still computes the full description;
     * only its level drives the icon's arcs now. Refreshed on resume and
     * on the same 15s tick as the clock (spec asked ≥30s).
     */
    private fun updateWifiStatus() {
        val (_, level) = WifiStatus.describe(this)
        wifiIcon.level = level
    }

    private fun showQuote() {
        val tv = findViewById<TextView>(R.id.quoteLine)
        val q = Quotes.today(this)
        if (q == null) {
            tv.visibility = View.GONE
            return
        }
        val text = "“${q.text}”"
        val author = " — ${q.figure}"
        val span = SpannableString(text + author)
        span.setSpan(
            ForegroundColorSpan(Color.parseColor("#FFD166")),
            text.length, text.length + author.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        span.setSpan(
            StyleSpan(Typeface.BOLD),
            text.length, text.length + author.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tv.text = span
    }

    private fun setupPill(pill: TextView, onClick: () -> Unit) {
        pill.setOnClickListener { onClick() }
        pill.setOnFocusChangeListener { v, hasFocus ->
            val tv = v as TextView
            if (hasFocus) {
                tv.background = ContextCompat.getDrawable(this, R.drawable.pill_on)
                tv.setTextColor(Color.parseColor("#191C22"))
            } else {
                tv.background = ContextCompat.getDrawable(this, R.drawable.pill_bg)
                tv.setTextColor(Color.WHITE)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildTiles() {
        tileRow.removeAllViews()
        var first: Pair<HomeTile, ResolvedTile>? = null
        for (tile in AppResolver.HOME_TILES) {
            val resolved = AppResolver.resolve(this, tile)
            if (first == null) first = tile to resolved
            val card = buildTileCard(tile, resolved)
            tileRow.addView(card)
        }
        // Hero starts populated with the first tile before any focus event.
        first?.let { (t, r) -> updateHero(t, r) }
    }

    private fun buildTileCard(tile: HomeTile, resolved: ResolvedTile): View {
        val card = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(5)
                marginEnd = dp(5)
            }
            val grad = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(tile.accentStart, tile.accentEnd)
            )
            grad.cornerRadius = dp(14).toFloat()
            background = grad
            clipToOutline = true
            elevation = dp(3).toFloat()
            isFocusable = true
            isClickable = true
        }

        // Artwork (phase-3 quality rule + phase-4 no-crop rule + phase-4.2
        // uniform framing): a real banner is shown only when wide enough
        // to stay sharp, and then ALWAYS whole — FIT_CENTER with a uniform
        // inset over its own blurred backdrop (0.3.0 filled the tile with
        // CENTER_CROP and cut the artwork's edges: user "thumbnail
        // kepotong"). Icons get the same framed treatment, centered.
        val art = resolved.artwork
        when {
            TileArt.isUsableBanner(art) -> {
                val fit = FitArtView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setFullArt(art!!.banner)
                }
                card.addView(fit)
            }
            TileArt.centeredDrawable(art) != null -> {
                // Phase 4.2: icons go through the same framed FitArtView
                // system as banners (own blurred backdrop + centered front)
                // instead of a bare ImageView over the brand gradient, so
                // every tile looks uniform. Pad 30dp on the 118dp-high tile
                // keeps the icon at the same ~58dp it had as a fixed box.
                val fit = FitArtView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setCentered(TileArt.centeredDrawable(art), dp(30))
                }
                card.addView(fit)
            }
            else -> {
                val initial = TextView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply { gravity = Gravity.TOP or Gravity.START }
                    setPadding(dp(12), dp(8), 0, 0)
                    text = tile.initial
                    textSize = 26f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (tile.key == "playstore") Color.parseColor("#0A9D58") else Color.WHITE)
                }
                card.addView(initial)
            }
        }

        val label = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM or Gravity.START }
            setPadding(dp(12), 0, dp(8), dp(9))
            text = resolved.displayLabel
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (tile.key == "playstore" && !TileArt.isUsableBanner(resolved.artwork)) Color.parseColor("#14202E") else Color.WHITE)
            setShadowLayer(6f, 0f, 1f, Color.parseColor("#99000000"))
        }
        card.addView(label)

        if (tile.action is TileAction.LaunchApp && !resolved.installed) {
            val badge = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.TOP or Gravity.END }
                setPadding(dp(8), dp(6), dp(8), dp(6))
                text = "Belum terinstal"
                textSize = 10f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#B3000000"))
            }
            card.addView(badge)
        }

        card.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.foreground = ContextCompat.getDrawable(this, R.drawable.tile_focus_border)
                v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start()
                updateHero(tile, resolved)
            } else {
                v.foreground = null
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
        }
        card.setOnClickListener { onTileClick(tile, resolved) }
        return card
    }

    private fun updateHero(tile: HomeTile, resolved: ResolvedTile) {
        heroName.text = resolved.displayLabel
        heroDesc.text = if (tile.action is TileAction.LaunchApp && !resolved.installed) {
            tile.description + " — belum terinstal di TV ini"
        } else {
            tile.description
        }
        // Same rules as the tiles (phase 4.2 uniform framing): a sharp
        // banner is shown WHOLE over its blurred backdrop (no crop);
        // otherwise the icon rests centered over its own blurred backdrop.
        val art = resolved.artwork
        when {
            TileArt.isUsableBanner(art) -> heroArt.setFullArt(art!!.banner)
            TileArt.centeredDrawable(art) != null ->
                heroArt.setCentered(TileArt.centeredDrawable(art), dp(30))
            else -> heroArt.clear()
        }
        setGlow(tile.accentStart)
    }

    /** Soft radial glow behind the hero, tinted by the focused tile's brand color. */
    private fun setGlow(accent: Int) {
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.RECTANGLE
        gd.gradientType = GradientDrawable.RADIAL_GRADIENT
        gd.setGradientCenter(0.60f, 0.40f)
        gd.gradientRadius = dp(460).toFloat()
        gd.colors = intArrayOf(
            Color.argb(84, Color.red(accent), Color.green(accent), Color.blue(accent)),
            Color.TRANSPARENT
        )
        heroGlow.background = gd
    }

    private fun onTileClick(tile: HomeTile, resolved: ResolvedTile) {
        when (val action = tile.action) {
            is TileAction.OpenBrowser ->
                // Frame 3: the Browser tile opens the search page (which itself
                // can open a typed URL directly in the WebView browser).
                startActivity(Intent(this, SearchActivity::class.java))
            is TileAction.OpenGames ->
                startActivity(Intent(this, GamesActivity::class.java))
            is TileAction.LaunchApp -> {
                val pkg = resolved.installedPackage
                if (pkg == null) {
                    Toast.makeText(this, "${tile.label} belum terinstal di TV ini", Toast.LENGTH_SHORT).show()
                    return
                }
                val intent = AppResolver.launchIntent(this, pkg)
                if (intent == null) {
                    Toast.makeText(this, "${tile.label} tidak punya launcher di TV ini", Toast.LENGTH_SHORT).show()
                    return
                }
                startActivity(intent)
            }
        }
    }
}
