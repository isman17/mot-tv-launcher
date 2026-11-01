package com.mottv.launcher

import android.content.Context
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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

/** Beranda (design frame 1) — also the HOME / LEANBACK_LAUNCHER entry point. */
class MainActivity : AppCompatActivity() {

    private lateinit var heroName: TextView
    private lateinit var heroDesc: TextView
    private lateinit var heroArt: FitArtView
    private lateinit var heroGlow: View
    private lateinit var clockPill: TextView
    private lateinit var wifiIcon: WifiIconView
    private lateinit var tileRow: LinearLayout
    private lateinit var pillBola: TextView

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

        pillBola = findViewById(R.id.pillBola)
        setupPill(pillBola) { startActivity(Intent(this, BolaActivity::class.java)) }
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
        updateBolaPill()
        if (tileRow.childCount > 0 && currentFocus == null) {
            tileRow.getChildAt(0).requestFocus()
        }
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTick)
    }

    /** Remote MENU key opens the same long-press app menu (phase 5). */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (AppActions.handleMenuKey(this, event)) return true
        return super.dispatchKeyEvent(event)
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
        // Long-press menu (phase 5): only on tiles backed by a real
        // installed package — the in-app modules (Browser/Games) and
        // not-installed tiles have no package to act on.
        val pkg = resolved.installedPackage
        if (tile.action is TileAction.LaunchApp && pkg != null) {
            AppActions.bind(card, this, resolved.displayLabel, pkg) {
                onTileClick(tile, resolved)
            }
        }
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

    // ---------------- Bola pill: next-match suffix (phase 5) ----------------

    /** One upcoming kickoff candidate for the pill. */
    private data class NextMatch(
        val leagueId: String,
        val home: String,
        val kickoff: LocalDateTime // WIB wall time, same basis as BolaActivity
    )

    private val bolaIo = Executors.newSingleThreadExecutor()

    @Volatile
    private var bolaPillFetching = false

    /**
     * Shows "⚽ Bola · {Home} {HH:mm}" on the Bola pill when a kickoff is
     * coming within 24 h, else leaves the plain label. Sources, cheapest
     * first: BolaActivity's persisted real days (SharedPreferences
     * "bola_cache", written only from full successful loads) and this
     * screen's own tiny cache of the last background lookup. Only when
     * everything is missing or older than 30 minutes do we fetch in the
     * background — failures are silent and the pill just stays plain.
     * Priority: a Liga Indonesia match wins over earlier kickoffs in
     * other leagues; within a group, earliest kickoff wins.
     */
    private fun updateBolaPill() {
        val nowWib = LocalDateTime.now(WIB_ZONE)
        val windowEnd = nowWib.plusHours(24)
        val (stored, storedFetchedAt) = readStoredNextMatches(nowWib, windowEnd)
        val (own, ownFetchedAt) = readOwnNextMatch(nowWib, windowEnd)
        val pick = pickNext(stored + listOfNotNull(own))
        pillBola.text = if (pick != null) {
            "⚽ Bola · ${shortTeam(pick.home)} ${pick.kickoff.format(HHMM_FMT)}"
        } else {
            PLAIN_BOLA_LABEL
        }
        val freshest = maxOf(storedFetchedAt, ownFetchedAt)
        val stale = freshest == 0L ||
            System.currentTimeMillis() - freshest > STALE_MS
        if (stale) fetchBolaNextInBackground()
    }

    private fun pickNext(candidates: List<NextMatch>): NextMatch? {
        if (candidates.isEmpty()) return null
        val idn = candidates.filter { it.leagueId == BolaActivity.IDN_LEAGUE_ID }
        return (idn.ifEmpty { candidates }).minByOrNull { it.kickoff }
    }

    /** Team name compressed to ≤10 chars for the pill: first word when
     *  that reads well ("Persib Bandung" → "Persib"), else hard cut. */
    private fun shortTeam(name: String): String {
        if (name.length <= 10) return name
        val first = name.substringBefore(' ')
        return if (first.length in 3..10) first else name.take(10).trim()
    }

    /** Candidates from BolaActivity's persisted days (today + tomorrow). */
    private fun readStoredNextMatches(
        nowWib: LocalDateTime,
        windowEnd: LocalDateTime
    ): Pair<List<NextMatch>, Long> {
        val out = ArrayList<NextMatch>()
        var freshest = 0L
        try {
            val prefs = getSharedPreferences(BOLA_CACHE_PREFS, Context.MODE_PRIVATE)
            for (offset in 0..1) {
                val date = nowWib.toLocalDate().plusDays(offset.toLong())
                val raw = prefs.getString(
                    "day_${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}", null
                ) ?: continue
                val root = JSONObject(raw)
                freshest = maxOf(freshest, root.optLong("fetchedAt", 0L))
                val leagues = root.optJSONArray("leagues") ?: continue
                for (i in 0 until leagues.length()) {
                    val lo = leagues.optJSONObject(i) ?: continue
                    val leagueId = lo.optString("leagueId")
                    val matches = lo.optJSONArray("matches") ?: continue
                    for (j in 0 until matches.length()) {
                        val m = matches.optJSONObject(j) ?: continue
                        if (m.optString("status", "NS") != "NS") continue
                        if (m.optBoolean("postponed", false)) continue
                        if (m.isNull("kickoff")) continue
                        val kickoff = try {
                            LocalDateTime.parse(m.optString("kickoff"))
                        } catch (e: Exception) {
                            null
                        } ?: continue
                        if (!kickoff.isAfter(nowWib) || !kickoff.isBefore(windowEnd)) continue
                        val home = m.optString("home")
                        if (home.isBlank()) continue
                        out.add(NextMatch(leagueId, home, kickoff))
                    }
                }
            }
        } catch (e: Exception) {
            // Corrupt/absent cache → no candidates; background fetch decides.
        }
        return out to freshest
    }

    /** This screen's own last background-lookup result, if still usable. */
    private fun readOwnNextMatch(
        nowWib: LocalDateTime,
        windowEnd: LocalDateTime
    ): Pair<NextMatch?, Long> {
        val prefs = getSharedPreferences(HOME_BOLA_PREFS, Context.MODE_PRIVATE)
        val fetchedAt = prefs.getLong("fetched_at", 0L)
        val kickoffIso = prefs.getString("kickoff", null) ?: return null to fetchedAt
        val kickoff = try {
            LocalDateTime.parse(kickoffIso)
        } catch (e: Exception) {
            return null to fetchedAt
        }
        if (!kickoff.isAfter(nowWib) || !kickoff.isBefore(windowEnd)) return null to fetchedAt
        val home = prefs.getString("home", null)?.ifBlank { null } ?: return null to fetchedAt
        val leagueId = prefs.getString("league_id", "") ?: ""
        return NextMatch(leagueId, home, kickoff) to fetchedAt
    }

    /**
     * Background lookup over the same hybrid sources BolaActivity uses
     * (Liga Indonesia via TheSportsDB, the big five via keyless ESPN,
     * each with the other as fallback), today first — if an Indonesian
     * match turns up we stop there, since it outranks everything else.
     * Only the single best pick is kept (in HOME_BOLA_PREFS, deliberately
     * NOT in BolaActivity's day cache, whose full-day format this light
     * lookup doesn't reproduce). Every failure is silent.
     */
    private fun fetchBolaNextInBackground() {
        if (bolaPillFetching) return
        bolaPillFetching = true
        bolaIo.execute {
            try {
                val nowWib = LocalDateTime.now(WIB_ZONE)
                val windowEnd = nowWib.plusHours(24)
                val candidates = ArrayList<NextMatch>()
                for (offset in 0..1) {
                    val date = nowWib.toLocalDate().plusDays(offset.toLong())
                    for (league in BolaActivity.LEAGUES) {
                        try {
                            candidates += fetchLeagueNext(league, date)
                        } catch (e: Exception) {
                            // silent by design
                        }
                    }
                    if (candidates.any { it.leagueId == BolaActivity.IDN_LEAGUE_ID }) break
                }
                val pick = pickNext(
                    candidates.filter {
                        it.kickoff.isAfter(nowWib) && it.kickoff.isBefore(windowEnd)
                    }
                )
                getSharedPreferences(HOME_BOLA_PREFS, Context.MODE_PRIVATE).edit().apply {
                    putLong("fetched_at", System.currentTimeMillis())
                    if (pick != null) {
                        putString("league_id", pick.leagueId)
                        putString("home", pick.home)
                        putString("kickoff", pick.kickoff.toString())
                    } else {
                        remove("league_id")
                        remove("home")
                        remove("kickoff")
                    }
                    apply()
                }
                clockHandler.post { updateBolaPill() }
            } catch (e: Exception) {
                // silent by design — pill keeps the plain label
            } finally {
                bolaPillFetching = false
            }
        }
    }

    /** One league-day through the hybrid chain; errors propagate to the
     *  caller, which treats them silently. Fallback only on error. */
    private fun fetchLeagueNext(league: BolaActivity.League, date: LocalDate): List<NextMatch> {
        val sources: List<() -> List<NextMatch>> =
            if (league.id == BolaActivity.IDN_LEAGUE_ID) {
                listOf({ fetchTsdbNext(league, date) }, { fetchEspnNext(league, date) })
            } else {
                listOf({ fetchEspnNext(league, date) }, { fetchTsdbNext(league, date) })
            }
        var last: Exception? = null
        for (source in sources) {
            try {
                return source()
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("no source answered")
    }

    private fun fetchEspnNext(league: BolaActivity.League, date: LocalDate): List<NextMatch> {
        val ymd = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val json = Net.getJson(
            "https://site.api.espn.com/apis/site/v2/sports/soccer/${league.espnSlug}" +
                "/scoreboard?dates=$ymd"
        )
        val out = ArrayList<NextMatch>()
        val events = json.optJSONArray("events") ?: return out
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            val type = e.optJSONObject("status")?.optJSONObject("type") ?: continue
            if (type.optString("state") != "pre") continue
            if (type.optString("name") == "STATUS_POSTPONED") continue
            val kickoff = parseUtcToWib(e.optString("date")) ?: continue
            val competitors = e.optJSONArray("competitions")
                ?.optJSONObject(0)?.optJSONArray("competitors") ?: continue
            var homeName: String? = null
            for (j in 0 until competitors.length()) {
                val c = competitors.optJSONObject(j) ?: continue
                if (c.optString("homeAway") == "home") {
                    homeName = c.optJSONObject("team")?.optString("displayName")
                    break
                }
            }
            val name = homeName?.ifBlank { null } ?: continue
            out.add(NextMatch(league.id, name, kickoff))
        }
        return out
    }

    private fun fetchTsdbNext(league: BolaActivity.League, date: LocalDate): List<NextMatch> {
        val key = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val json = Net.getJson(
            "https://www.thesportsdb.com/api/v1/json/123/eventsday.php" +
                "?d=$key&s=Soccer&l=${league.id}"
        )
        if (json.isNull("events")) return emptyList()
        val events = json.optJSONArray("events") ?: return emptyList()
        val out = ArrayList<NextMatch>()
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            if (e.optString("strPostponed") == "yes") continue
            val status = e.optString("strStatus")
            if (status.isNotBlank() && status !in TSDB_UPCOMING) continue
            val kickoff = parseUtcToWib(e.optString("strTimestamp")) ?: continue
            val home = e.optString("strHomeTeam")
            if (home.isBlank()) continue
            out.add(NextMatch(league.id, home, kickoff))
        }
        return out
    }

    /** UTC ISO timestamp ("2026-11-01T16:30Z" or "2026-11-01 16:30:00")
     *  → WIB wall time, mirroring BolaActivity's conversions. */
    private fun parseUtcToWib(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        val iso = raw.trim().replace(" ", "T")
        return try {
            OffsetDateTime.parse(iso)
                .atZoneSameInstant(WIB_ZONE)
                .toLocalDateTime()
        } catch (e: Exception) {
            try {
                LocalDateTime.parse(iso.removeSuffix("Z"))
                    .atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(WIB_ZONE)
                    .toLocalDateTime()
            } catch (e2: Exception) {
                null
            }
        }
    }

    companion object {
        // The pill's resting label, exactly as laid out in activity_main.xml.
        private const val PLAIN_BOLA_LABEL = "Jadwal Bola"
        // Storage contract shared with BolaActivity (its "bola_cache" file).
        private const val BOLA_CACHE_PREFS = "bola_cache"
        private const val HOME_BOLA_PREFS = "home_bola"
        private const val STALE_MS = 30 * 60 * 1000L
        private val WIB_ZONE: ZoneId = ZoneId.of("Asia/Jakarta")
        private val HHMM_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val TSDB_UPCOMING = setOf("NS", "Not Started")
    }
}
