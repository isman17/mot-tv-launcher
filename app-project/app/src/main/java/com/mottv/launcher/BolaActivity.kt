package com.mottv.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Jadwal Bola (design frames 6–7) with REAL fixtures from a HYBRID of two
 * free sources — neither is invented, and each league has a fallback:
 *
 * Phase-4 source strategy (0.3.0 still hit HTTP 429 on the TV for every
 * league): root cause is structural — TheSportsDB's free tier uses the
 * PUBLIC demo key "123", shared by the whole world, so it rate-limits
 * regardless of UA/retry care. The five big leagues therefore moved to
 * the keyless ESPN unofficial API, which returns FULL match slates:
 *  - ESPN: site.api.espn.com/apis/site/v2/sports/soccer/{slug}/scoreboard
 *    ?dates=YYYYMMDD (no key). Events carry UTC ISO dates, competitors
 *    with team.displayName + team.logo (500px), scores, status
 *    (state pre/in/post + displayClock), and leagues[0].logos for the
 *    league logo. Verified from this project (HTTP 200, full slates).
 *  - Liga Indonesia stays TheSportsDB-primary (league 4790): ESPN's
 *    idn.1 is EMPTY for the whole 2026/27 season (verified: 0 events on
 *    2026-10-25/31, 2026-11-01/07) while TheSportsDB has its matches
 *    (verified 2026-10-31 & 2026-11-01, 3 events each). ESPN idn.1 is
 *    its fallback if TheSportsDB errors — and vice versa for the others.
 *  - Fallback triggers on ERRORS only, never on a legitimately empty
 *    day (an empty response is a real answer, not a failure).
 *
 * Carried over from phases 2–3: browser UA everywhere; SEQUENTIAL loads
 * (Liga Indonesia first) with a small delay; one retry with backoff on
 * 429/5xx/IO; 15s/20s timeouts; a fully successful day's REAL results
 * are persisted and re-shown WITH a "data tersimpan dari HH:mm" note
 * when a reload fails completely; error states carry the real HTTP code.
 * TheSportsDB league IDs + ESPN slugs were both verified against the
 * live APIs — see LEAGUES below; nothing here is guessed.
 */
class BolaActivity : AppCompatActivity() {

    data class League(
        /** TheSportsDB league id (also the chain key: 4790 = TSDB-primary). */
        val id: String,
        /** ESPN scoreboard slug, e.g. "eng.1" — primary for all but 4790. */
        val espnSlug: String,
        val apiName: String,
        val displayName: String,
        val country: String,
        val initials: String,
        val initialsColor: Int,
        /** "LIGA" or "CUP" — explicit per-league table (cup-format competition). */
        val category: String,
        /**
         * MANUAL per-season mapping of Indonesian broadcasters (2025/26).
         * TheSportsDB has no Indonesian TV data, so this table is maintained
         * by hand per season. null = not confident → the UI honestly shows
         * "Platform: cek penyedia" instead of a guessed platform.
         */
        val platforms: String?,
        var badgeUrl: String? = null
    )

    data class Match(
        val home: String,
        val away: String,
        val homeBadge: String?,
        val awayBadge: String?,
        val leagueBadge: String?,
        val kickoffWib: LocalDateTime?,
        val timeFallback: String?,
        val status: String,
        val homeScore: String?,
        val awayScore: String?,
        val postponed: Boolean,
        /** ESPN live matches only: the match clock, e.g. "67'". */
        val liveClock: String? = null
    )

    data class LeagueDay(val league: League, val matches: List<Match>)

    /** One league's day from whichever source answered: matches + league logo URL. */
    private data class LeagueFetch(val matches: List<Match>, val leagueBadge: String?)

    private val io = Executors.newFixedThreadPool(2)
    private val loadIo = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var statusText: TextView
    private lateinit var dateLabel: TextView
    private lateinit var emptyText: TextView
    private lateinit var groupsContainer: LinearLayout
    private lateinit var chipsAdapter: ChipsAdapter
    private lateinit var datePills: Map<String, TextView>

    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedLeagueId: String? = null // null = Semua
    private var loadSeq = 0
    private var lastErrorShort: String = ""

    // In-memory cache of REAL fetched data only (per date), this session.
    private val dayCache = HashMap<String, List<LeagueDay>>()

    private val calendarLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val iso = result.data?.getStringExtra(CalendarActivity.EXTRA_DATE)
            if (!iso.isNullOrBlank()) {
                try {
                    selectDate(LocalDate.parse(iso))
                } catch (e: Exception) {
                    // ignore malformed result
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bola)
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))

        statusText = findViewById(R.id.bolaStatus)
        dateLabel = findViewById(R.id.dateLabel)
        emptyText = findViewById(R.id.bolaEmpty)
        groupsContainer = findViewById(R.id.groupsContainer)

        stylePill(findViewById(R.id.backBtn)) { finish() }
        datePills = mapOf(
            "prev" to findViewById(R.id.datePrev),
            "today" to findViewById(R.id.dateToday),
            "next" to findViewById(R.id.dateNext)
        )
        stylePill(datePills.getValue("prev")) { selectDate(LocalDate.now().minusDays(1)) }
        stylePill(datePills.getValue("today")) { selectDate(LocalDate.now()) }
        stylePill(datePills.getValue("next")) { selectDate(LocalDate.now().plusDays(1)) }
        findViewById<TextView>(R.id.datePick).apply {
            setOnFocusChangeListener { v, hasFocus ->
                val t = v as TextView
                t.background = ContextCompat.getDrawable(
                    this@BolaActivity, if (hasFocus) R.drawable.pill_on else R.drawable.pill_gold
                )
            }
            setOnClickListener {
                calendarLauncher.launch(
                    Intent(this@BolaActivity, CalendarActivity::class.java)
                        .putExtra(
                            CalendarActivity.EXTRA_INITIAL,
                            selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        )
                )
            }
        }

        val chips = findViewById<RecyclerView>(R.id.leagueChips)
        chips.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        chipsAdapter = ChipsAdapter { leagueId ->
            selectedLeagueId = leagueId
            renderCached()
        }
        chips.adapter = chipsAdapter

        fetchLeagueBadges()
        selectDate(LocalDate.now())
        datePills.getValue("today").requestFocus()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun stylePill(tv: TextView, onClick: () -> Unit) {
        tv.setOnFocusChangeListener { v, hasFocus ->
            val t = v as TextView
            val selected = when (t) {
                datePills["prev"] -> selectedDate == LocalDate.now().minusDays(1)
                datePills["today"] -> selectedDate == LocalDate.now()
                datePills["next"] -> selectedDate == LocalDate.now().plusDays(1)
                else -> false
            }
            if (hasFocus || selected) {
                t.background = ContextCompat.getDrawable(this, R.drawable.pill_on)
                t.setTextColor(Color.parseColor("#191C22"))
            } else {
                t.background = ContextCompat.getDrawable(this, R.drawable.pill_bg)
                t.setTextColor(Color.WHITE)
            }
        }
        tv.setOnClickListener { onClick() }
    }

    private fun refreshDatePills() {
        for ((_, pill) in datePills) {
            val selected = when (pill) {
                datePills["prev"] -> selectedDate == LocalDate.now().minusDays(1)
                datePills["today"] -> selectedDate == LocalDate.now()
                datePills["next"] -> selectedDate == LocalDate.now().plusDays(1)
                else -> false
            }
            if (selected && !pill.hasFocus()) {
                pill.background = ContextCompat.getDrawable(this, R.drawable.pill_on)
                pill.setTextColor(Color.parseColor("#191C22"))
            } else if (!selected && !pill.hasFocus()) {
                pill.background = ContextCompat.getDrawable(this, R.drawable.pill_bg)
                pill.setTextColor(Color.WHITE)
            }
        }
    }

    private fun selectDate(date: LocalDate) {
        selectedDate = date
        dateLabel.text = date.format(LABEL_FMT)
        refreshDatePills()
        val key = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val cached = dayCache[key]
        if (cached != null) {
            statusText.text = ""
            render(cached)
        } else {
            loadDate(date)
        }
    }

    private fun renderCached() {
        val key = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        dayCache[key]?.let { render(it) }
    }

    /** Short, user-reportable description of a failure (HTTP code included). */
    private fun shortError(e: Exception): String = when (e) {
        is HttpException -> "HTTP ${e.code}"
        is IOException -> "jaringan (${e.message?.take(60) ?: "timeout"})"
        else -> "${e.javaClass.simpleName} (${e.message?.take(60) ?: "?"})"
    }

    /**
     * One JSON fetch with a single retry after backoff when the failure is
     * plausibly transient (HTTP 429 rate limit, 5xx, or any I/O error).
     * Records the last error in [lastErrorShort] for diagnostics.
     */
    private fun fetchWithRetry(url: String): JSONObject {
        var firstError: Exception? = null
        repeat(2) { attempt ->
            try {
                return Net.getJson(url)
            } catch (e: Exception) {
                lastErrorShort = shortError(e)
                if (firstError == null) firstError = e
                val retryable = when (e) {
                    is HttpException -> e.code == 429 || e.code >= 500
                    is IOException -> true
                    else -> false
                }
                if (!retryable || attempt == 1) throw e
                try {
                    Thread.sleep(1_600)
                } catch (ie: InterruptedException) {
                    throw e
                }
            }
        }
        throw firstError ?: IllegalStateException("fetch gagal")
    }

    // ---------- Hybrid sources (phase 4) ----------

    /**
     * Per-league source chain. Liga Indonesia: TheSportsDB primary (it is
     * the only free source that actually has the 2026/27 fixtures), ESPN
     * fallback. The five big leagues: keyless ESPN primary (full slates,
     * no shared-key rate limit), TheSportsDB fallback. The fallback runs
     * ONLY when the primary throws — an empty day is a valid answer.
     */
    private fun fetchLeagueDay(league: League, dateKey: String): LeagueFetch {
        val sources: List<() -> LeagueFetch> = if (league.id == IDN_LEAGUE_ID) {
            listOf({ fetchTsdb(league, dateKey) }, { fetchEspn(league, dateKey) })
        } else {
            listOf({ fetchEspn(league, dateKey) }, { fetchTsdb(league, dateKey) })
        }
        var last: Exception? = null
        for (source in sources) {
            try {
                return source()
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("tidak ada sumber yang menjawab")
    }

    private fun fetchTsdb(league: League, dateKey: String): LeagueFetch {
        val json = fetchWithRetry(
            "https://www.thesportsdb.com/api/v1/json/123/eventsday.php" +
                "?d=$dateKey&s=Soccer&l=${league.id}"
        )
        val matches = parseEvents(json)
        return LeagueFetch(matches, matches.firstOrNull()?.leagueBadge)
    }

    private fun espnUrl(league: League, dateKey: String): String =
        "https://site.api.espn.com/apis/site/v2/sports/soccer/${league.espnSlug}" +
            "/scoreboard?dates=${dateKey.replace("-", "")}"

    private fun fetchEspn(league: League, dateKey: String): LeagueFetch {
        val json = fetchWithRetry(espnUrl(league, dateKey))
        return parseEspn(json)
    }

    /**
     * ESPN scoreboard → our Match list. Structure verified live from this
     * project: events[].date is ISO-8601 UTC ("2026-11-01T16:30Z");
     * competitions[0].competitors[] carry homeAway, team.displayName,
     * team.logo (500px PNG URL), score (string, meaningful unless the
     * state is "pre"); status.type.state is pre/in/post and
     * status.displayClock is the live match clock.
     */
    private fun parseEspn(json: JSONObject): LeagueFetch {
        val leagueBadge = espnLeagueLogo(json)
        val arr = json.optJSONArray("events") ?: return LeagueFetch(emptyList(), leagueBadge)
        val out = ArrayList<Match>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            val competitors = comp.optJSONArray("competitors") ?: continue
            var home: JSONObject? = null
            var away: JSONObject? = null
            for (j in 0 until competitors.length()) {
                val c = competitors.optJSONObject(j) ?: continue
                if (c.optString("homeAway") == "home") home = c else away = c
            }
            val h = home ?: continue
            val a = away ?: continue
            val hTeam = h.optJSONObject("team") ?: continue
            val aTeam = a.optJSONObject("team") ?: continue

            val statusObj = e.optJSONObject("status")
            val typeObj = statusObj?.optJSONObject("type")
            val state = typeObj?.optString("state") ?: "pre"
            val typeName = typeObj?.optString("name") ?: ""
            val clock = statusObj?.optString("displayClock")?.ifBlank { null }
            val (status, liveClock) = when (state) {
                "post" -> "FT" to null
                "in" -> "LIVE" to clock
                else -> "NS" to null
            }
            fun scoreOf(c: JSONObject): String? =
                if (state == "pre") null
                else c.optString("score", "").ifBlank { null }

            out.add(
                Match(
                    home = hTeam.optString("displayName").ifBlank { "?" },
                    away = aTeam.optString("displayName").ifBlank { "?" },
                    homeBadge = hTeam.optString("logo").ifBlank { null },
                    awayBadge = aTeam.optString("logo").ifBlank { null },
                    leagueBadge = leagueBadge,
                    kickoffWib = parseEspnKickoff(e.optString("date")),
                    timeFallback = null,
                    status = status,
                    homeScore = scoreOf(h),
                    awayScore = scoreOf(a),
                    postponed = typeName == "STATUS_POSTPONED",
                    liveClock = liveClock
                )
            )
        }
        return LeagueFetch(
            out.sortedBy { it.kickoffWib ?: LocalDateTime.MAX }, leagueBadge
        )
    }

    /** League logo from an ESPN response: the "default" (coloured) variant,
     *  shown on the designed white circle; the "dark" variant is the
     *  light-on-transparent one and is skipped here. */
    private fun espnLeagueLogo(json: JSONObject): String? {
        val league = json.optJSONArray("leagues")?.optJSONObject(0) ?: return null
        val logos = league.optJSONArray("logos") ?: return null
        var fallback: String? = null
        for (i in 0 until logos.length()) {
            val entry = logos.optJSONObject(i) ?: continue
            val href = entry.optString("href").ifBlank { null } ?: continue
            if (fallback == null) fallback = href
            val rel = entry.optJSONArray("rel")
            var isDefault = false
            var isDark = false
            if (rel != null) {
                for (j in 0 until rel.length()) {
                    when (rel.optString(j)) {
                        "default" -> isDefault = true
                        "dark" -> isDark = true
                    }
                }
            }
            if (isDefault && !isDark) return href
        }
        return fallback
    }

    /** ESPN dates are ISO-8601 UTC; the UI shows Asia/Jakarta (WIB). */
    private fun parseEspnKickoff(iso: String?): LocalDateTime? {
        if (iso.isNullOrBlank()) return null
        return try {
            java.time.OffsetDateTime.parse(iso)
                .atZoneSameInstant(ZoneId.of("Asia/Jakarta"))
                .toLocalDateTime()
        } catch (e: Exception) {
            try {
                LocalDateTime.parse(iso.removeSuffix("Z"))
                    .atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(ZoneId.of("Asia/Jakarta"))
                    .toLocalDateTime()
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun loadDate(date: LocalDate) {
        val seq = ++loadSeq
        statusText.text = "Memuat jadwal…"
        groupsContainer.removeAllViews()
        emptyText.visibility = View.GONE
        val key = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        loadIo.execute {
            var failures = 0
            var badgeUpdated = false
            lastErrorShort = ""
            val days = ArrayList<LeagueDay>()
            // SEQUENTIAL on purpose (Liga Indonesia first): bursting all six
            // leagues at once got the free tier to reject the TV's requests.
            for ((index, league) in LEAGUES.withIndex()) {
                if (index > 0) {
                    try {
                        Thread.sleep(350)
                    } catch (e: InterruptedException) {
                        // keep loading the remaining leagues
                    }
                }
                try {
                    val fetched = fetchLeagueDay(league, key)
                    if (fetched.leagueBadge != null && league.badgeUrl == null) {
                        league.badgeUrl = fetched.leagueBadge
                        badgeUpdated = true
                    }
                    days.add(LeagueDay(league, fetched.matches))
                } catch (e: Exception) {
                    failures++
                    lastErrorShort = shortError(e)
                    days.add(LeagueDay(league, emptyList()))
                }
            }
            val err = lastErrorShort
            handler.post {
                if (seq != loadSeq) return@post // a newer date was picked meanwhile
                if (badgeUpdated) chipsAdapter.notifyDataSetChanged()
                when {
                    failures == LEAGUES.size -> {
                        val stored = readStoredDay(key)
                        if (stored != null) {
                            dayCache[key] = stored.first
                            render(stored.first)
                            statusText.text =
                                "Menampilkan data tersimpan dari ${stored.second} — " +
                                    "pemuatan ulang gagal ($err). Cek koneksi internet TV."
                        } else {
                            statusText.text = ""
                            emptyText.visibility = View.VISIBLE
                            emptyText.text =
                                "Gagal memuat jadwal ($err) — cek koneksi internet TV, " +
                                    "lalu buka lagi halaman ini. Kode error ini sebutkan kalau lapor masalah."
                        }
                    }
                    failures > 0 -> {
                        statusText.text =
                            "$failures dari ${LEAGUES.size} liga gagal dimuat (terakhir: $err) — " +
                                "yang tampil adalah data asli yang berhasil diambil."
                        render(days)
                    }
                    else -> {
                        dayCache[key] = days
                        writeStoredDay(key, days)
                        statusText.text = ""
                        render(days)
                    }
                }
            }
        }
    }

    // ---------- Persistent cache of REAL fetched days ----------

    private fun prefs() = getSharedPreferences(BOLA_PREFS, Context.MODE_PRIVATE)

    private fun writeStoredDay(key: String, days: List<LeagueDay>) {
        try {
            val leaguesArr = JSONArray()
            for (day in days) {
                val matchesArr = JSONArray()
                for (m in day.matches) {
                    matchesArr.put(JSONObject().apply {
                        put("home", m.home)
                        put("away", m.away)
                        put("homeBadge", m.homeBadge ?: JSONObject.NULL)
                        put("awayBadge", m.awayBadge ?: JSONObject.NULL)
                        put("leagueBadge", m.leagueBadge ?: JSONObject.NULL)
                        put("kickoff", m.kickoffWib?.toString() ?: JSONObject.NULL)
                        put("timeFallback", m.timeFallback ?: JSONObject.NULL)
                        put("status", m.status)
                        put("homeScore", m.homeScore ?: JSONObject.NULL)
                        put("awayScore", m.awayScore ?: JSONObject.NULL)
                        put("postponed", m.postponed)
                        put("liveClock", m.liveClock ?: JSONObject.NULL)
                    })
                }
                leaguesArr.put(JSONObject().apply {
                    put("leagueId", day.league.id)
                    put("matches", matchesArr)
                })
            }
            val root = JSONObject().apply {
                put("fetchedAt", System.currentTimeMillis())
                put("leagues", leaguesArr)
            }
            prefs().edit().putString("day_$key", root.toString()).apply()
        } catch (e: Exception) {
            // Cache write failure is non-fatal; the in-memory copy still works.
        }
    }

    /** Returns (days, "HH:mm" fetch-time label) or null when nothing stored. */
    private fun readStoredDay(key: String): Pair<List<LeagueDay>, String>? {
        return try {
            val raw = prefs().getString("day_$key", null) ?: return null
            val root = JSONObject(raw)
            val fetchedAt = root.optLong("fetchedAt", 0L)
            val arr = root.optJSONArray("leagues") ?: return null
            val byId = HashMap<String, List<Match>>()
            for (i in 0 until arr.length()) {
                val lo = arr.getJSONObject(i)
                val marr = lo.optJSONArray("matches") ?: continue
                val matches = ArrayList<Match>()
                for (j in 0 until marr.length()) {
                    val o = marr.getJSONObject(j)
                    fun sOrNull(name: String): String? =
                        if (o.isNull(name)) null else o.optString(name, "").ifBlank { null }
                    matches.add(
                        Match(
                            home = o.optString("home", "?"),
                            away = o.optString("away", "?"),
                            homeBadge = sOrNull("homeBadge"),
                            awayBadge = sOrNull("awayBadge"),
                            leagueBadge = sOrNull("leagueBadge"),
                            kickoffWib = sOrNull("kickoff")?.let {
                                try { LocalDateTime.parse(it) } catch (e: Exception) { null }
                            },
                            timeFallback = sOrNull("timeFallback"),
                            status = o.optString("status", "NS"),
                            homeScore = sOrNull("homeScore"),
                            awayScore = sOrNull("awayScore"),
                            postponed = o.optBoolean("postponed", false),
                            liveClock = sOrNull("liveClock")
                        )
                    )
                }
                byId[lo.optString("leagueId")] = matches
            }
            val days = LEAGUES.map { LeagueDay(it, byId[it.id] ?: emptyList()) }
            val timeLabel = if (fetchedAt > 0) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = fetchedAt }
                String.format(
                    Locale.US, "%02d:%02d",
                    cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)
                )
            } else "--:--"
            days to timeLabel
        } catch (e: Exception) {
            null
        }
    }

    private fun JSONObject.s(name: String): String? {
        if (isNull(name)) return null
        return optString(name, "").ifBlank { null }
    }

    private fun parseEvents(json: JSONObject): List<Match> {
        if (json.isNull("events")) return emptyList()
        val arr = json.optJSONArray("events") ?: return emptyList()
        val out = ArrayList<Match>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            out.add(
                Match(
                    home = e.s("strHomeTeam") ?: "?",
                    away = e.s("strAwayTeam") ?: "?",
                    homeBadge = e.s("strHomeTeamBadge"),
                    awayBadge = e.s("strAwayTeamBadge"),
                    leagueBadge = e.s("strLeagueBadge"),
                    kickoffWib = parseKickoff(e.s("strTimestamp"), e.s("dateEvent"), e.s("strTime")),
                    timeFallback = e.s("strTime"),
                    status = e.s("strStatus") ?: "NS",
                    homeScore = e.s("intHomeScore"),
                    awayScore = e.s("intAwayScore"),
                    postponed = e.s("strPostponed") == "yes"
                )
            )
        }
        return out.sortedBy { it.kickoffWib ?: LocalDateTime.MAX }
    }

    /** TheSportsDB timestamps are UTC; the UI shows Asia/Jakarta (WIB). */
    private fun parseKickoff(
        timestamp: String?, dateEvent: String?, time: String?
    ): LocalDateTime? {
        return try {
            val utc = when {
                !timestamp.isNullOrBlank() ->
                    LocalDateTime.parse(timestamp.replace(" ", "T"))
                !dateEvent.isNullOrBlank() && !time.isNullOrBlank() ->
                    LocalDateTime.parse("${dateEvent}T$time")
                else -> return null
            }
            utc.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.of("Asia/Jakarta"))
                .toLocalDateTime()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * League badge URLs for the filter chips, once per session. Phase 4:
     * ESPN first (keyless — every scoreboard response carries the league
     * logos, even on a day with no matches), TheSportsDB lookupleague only
     * as fallback, so chip loading adds no pressure on the shared key.
     */
    private fun fetchLeagueBadges() {
        if (LEAGUES.all { it.badgeUrl != null }) return
        io.execute {
            val todayKey = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            for (league in LEAGUES) {
                if (league.badgeUrl != null) continue
                try {
                    val badge = espnLeagueLogo(fetchWithRetry(espnUrl(league, todayKey)))
                    if (!badge.isNullOrBlank()) league.badgeUrl = badge
                } catch (e: Exception) {
                    // try TheSportsDB below
                }
                if (league.badgeUrl != null) continue
                try {
                    val json = fetchWithRetry(
                        "https://www.thesportsdb.com/api/v1/json/123/lookupleague.php?id=${league.id}"
                    )
                    val arr = if (json.isNull("leagues")) null else json.optJSONArray("leagues")
                    val badge = arr?.optJSONObject(0)?.s("strBadge")
                    if (!badge.isNullOrBlank()) league.badgeUrl = badge
                } catch (e: Exception) {
                    // chip falls back to initials — honest, no fake logo
                }
            }
            handler.post { chipsAdapter.notifyDataSetChanged() }
        }
    }

    // ---------------- Rendering ----------------

    private fun render(days: List<LeagueDay>) {
        groupsContainer.removeAllViews()
        val filtered = if (selectedLeagueId == null) days
        else days.filter { it.league.id == selectedLeagueId }
        var totalMatches = 0
        for (day in filtered) {
            if (selectedLeagueId == null && day.matches.isEmpty()) continue
            totalMatches += day.matches.size
            groupsContainer.addView(buildGroupHeader(day))
            if (day.matches.isEmpty()) {
                groupsContainer.addView(infoRow("Belum ada jadwal / belum dirilis di liga ini untuk tanggal ini."))
            } else {
                for (match in day.matches) groupsContainer.addView(buildMatchRow(day.league, match))
            }
        }
        if (totalMatches == 0 && selectedLeagueId == null) {
            emptyText.visibility = View.VISIBLE
            emptyText.text =
                "Belum ada jadwal / belum dirilis untuk tanggal ini di liga yang dipantau — " +
                    "ini tampilan kosong yang jujur, bukan error."
        } else {
            emptyText.visibility = View.GONE
        }
    }

    private fun circleBadge(sizeDp: Int, initials: String, color: Int): FrameLayout {
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
            val circle = GradientDrawable()
            circle.shape = GradientDrawable.OVAL
            circle.setColor(Color.WHITE)
            background = circle
        }
        val tv = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
            text = initials
            textSize = (sizeDp / 3.2f).coerceAtLeast(8f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color)
        }
        val img = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        img.tag = "badgeImg"
        frame.addView(tv)
        frame.addView(img)
        return frame
    }

    private fun loadBadge(frame: FrameLayout, url: String?) {
        if (url.isNullOrBlank()) return
        val img = frame.findViewWithTag<ImageView>("badgeImg") ?: return
        ImgCache.load(url, img)
    }

    private fun buildGroupHeader(day: LeagueDay): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
                bottomMargin = dp(6)
            }
        }
        val badgeFrame = circleBadge(34, day.league.initials, day.league.initialsColor)
        loadBadge(badgeFrame, day.matches.firstOrNull()?.leagueBadge ?: day.league.badgeUrl)
        row.addView(badgeFrame)

        val name = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(10) }
            text = day.league.displayName
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        row.addView(name)
        val country = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6) }
            text = "· ${day.league.country}"
            textSize = 14f
            setTextColor(Color.parseColor("#A8FFFFFF"))
        }
        row.addView(country)

        val isLiga = day.league.category == "LIGA"
        val tag = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(10) }
            setPadding(dp(8), dp(3), dp(8), dp(3))
            text = day.league.category
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#10131A"))
            val bg = GradientDrawable()
            bg.cornerRadius = dp(6).toFloat()
            bg.setColor(Color.parseColor(if (isLiga) "#2EE6A8" else "#FFD166"))
            background = bg
        }
        row.addView(tag)
        return row
    }

    private fun infoRow(text: String): View = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) }
        setPadding(dp(12), dp(8), dp(12), dp(8))
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#A8FFFFFF"))
    }

    private fun buildMatchRow(league: League, match: Match): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(7) }
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = ContextCompat.getDrawable(this@BolaActivity, R.drawable.menu_item)
            elevation = dp(2).toFloat()
            setOnFocusChangeListener { v, hasFocus ->
                v.background = ContextCompat.getDrawable(
                    this@BolaActivity,
                    if (hasFocus) R.drawable.tile_focus_border else R.drawable.menu_item
                )
            }
        }

        val homeBadge = circleBadge(34, initialsOf(match.home), Color.parseColor("#333333"))
        loadBadge(homeBadge, match.homeBadge)
        row.addView(homeBadge)
        row.addView(teamName(match.home, right = false))

        // Center: kickoff time (WIB) or score for live/finished matches.
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val finished = match.status in FINISHED
        val live = match.status in LIVE
        val mainText: String
        val subText: String
        when {
            match.postponed || match.status == "PST" -> {
                mainText = "Ditunda"; subText = "jadwal ulang menyusul"
            }
            (finished || live) && match.homeScore != null && match.awayScore != null -> {
                mainText = "${match.homeScore} - ${match.awayScore}"
                subText = when {
                    live && match.liveClock != null -> "Sedang main · ${match.liveClock}"
                    live -> "Sedang main"
                    else -> "Selesai"
                }
            }
            else -> {
                mainText = match.kickoffWib?.format(TIME_FMT)
                    ?: match.timeFallback?.take(5) ?: "--:--"
                subText = "WIB"
            }
        }
        center.addView(TextView(this).apply {
            gravity = Gravity.CENTER
            text = mainText
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(
                if (match.postponed) Color.parseColor("#FFD166") else Color.WHITE
            )
        })
        center.addView(TextView(this).apply {
            gravity = Gravity.CENTER
            text = subText
            textSize = 11f
            setTextColor(
                if (live) Color.parseColor("#FF7A7A") else Color.parseColor("#A8FFFFFF")
            )
        })
        row.addView(center)

        row.addView(teamName(match.away, right = true))
        val awayBadge = circleBadge(34, initialsOf(match.away), Color.parseColor("#333333"))
        loadBadge(awayBadge, match.awayBadge)
        row.addView(awayBadge)

        val platform = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(12) }
            setPadding(dp(10), dp(5), dp(10), dp(5))
            text = league.platforms ?: "Platform: cek penyedia"
            textSize = 11f
            setTextColor(
                if (league.platforms != null) Color.WHITE else Color.parseColor("#A8FFFFFF")
            )
            background = ContextCompat.getDrawable(this@BolaActivity, R.drawable.pill_bg)
        }
        row.addView(platform)
        return row
    }

    private fun teamName(name: String, right: Boolean): TextView = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply {
                marginStart = if (right) dp(4) else dp(10)
                marginEnd = if (right) dp(10) else dp(4)
            }
        text = name
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        gravity = if (right) Gravity.END else Gravity.START
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun initialsOf(team: String): String =
        team.split(" ").filter { it.isNotBlank() }
            .take(3).map { it.first().uppercaseChar() }.joinToString("")

    // ---------------- Chips ----------------

    inner class ChipsAdapter(private val onSelect: (String?) -> Unit) :
        RecyclerView.Adapter<ChipsAdapter.VH>() {

        // null entry = "Semua"; LEAGUES order rules the rest (Indonesia first).
        private val items: List<League?> = listOf(null) + LEAGUES

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val card: View = view.findViewById(R.id.chipCard)
            val logoFrame: FrameLayout = view.findViewById(R.id.chipLogoFrame)
            val logoImg: ImageView = view.findViewById(R.id.chipLogo)
            val initials: TextView = view.findViewById(R.id.chipInitials)
            val name: TextView = view.findViewById(R.id.chipName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_league_chip, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val league = items[position]
            val isAll = league == null
            val selected = (selectedLeagueId == league?.id) || (isAll && selectedLeagueId == null)
            holder.name.text = league?.displayName ?: "Semua"
            holder.logoFrame.visibility = if (isAll) View.GONE else View.VISIBLE
            if (league != null) {
                val circle = GradientDrawable()
                circle.shape = GradientDrawable.OVAL
                circle.setColor(Color.WHITE)
                holder.logoFrame.background = circle
                holder.initials.text = league.initials
                holder.initials.setTextColor(league.initialsColor)
                holder.logoImg.setImageBitmap(null)
                holder.logoImg.tag = null
                if (!league.badgeUrl.isNullOrBlank()) {
                    holder.initials.text = ""
                    ImgCache.load(league.badgeUrl, holder.logoImg)
                }
            }
            applyStyle(holder, selected, focused = false)
            holder.card.setOnFocusChangeListener { _, hasFocus ->
                applyStyle(holder, selected, hasFocus)
            }
            holder.card.setOnClickListener {
                onSelect(league?.id)
                notifyDataSetChanged()
            }
        }

        private fun applyStyle(holder: VH, selected: Boolean, focused: Boolean) {
            val active = selected || focused
            holder.card.background = ContextCompat.getDrawable(
                this@BolaActivity, if (active) R.drawable.pill_on else R.drawable.pill_bg
            )
            holder.name.setTextColor(
                if (active) Color.parseColor("#191C22") else Color.WHITE
            )
        }
    }

    companion object {
        private const val BOLA_PREFS = "bola_cache"
        private val LABEL_FMT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID"))
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
        private val FINISHED = setOf("FT", "AET", "PEN", "AWD", "WO")
        private val LIVE = setOf("1H", "2H", "HT", "ET", "BT", "P", "LIVE")

        // League IDs VERIFIED against TheSportsDB free API (key "123") on
        // 2026-10-27 via all_leagues.php, search_all_leagues.php (Indonesia)
        // and lookupleague.php per league (badges confirmed for all six).
        // ESPN slugs VERIFIED live on 2026-11-01 (scoreboard HTTP 200 for
        // eng.1/esp.1/ita.1/ger.1/uefa.champions/idn.1; idn.1 empty for
        // 2026/27 dates while TSDB 4790 has the matches — hence the chain).
        // ORDER (phase 3, user request): Liga Indonesia FIRST, then Premier
        // League, Champions League, La Liga, Serie A, Bundesliga. This order
        // drives group order, chip order, and sequential load order.
        // Platform mapping = manual 2025/26 Indonesia table; null = unsure.
        const val IDN_LEAGUE_ID = "4790"
        val LEAGUES: List<League> = listOf(
            League(
                "4790", "idn.1", "Indonesian Super League", "Liga 1 Indonesia", "Indonesia",
                "L1", 0xFFC8102E.toInt(), "LIGA", "Indosiar · Vidio"
            ),
            League(
                "4328", "eng.1", "English Premier League", "Premier League", "Inggris",
                "PL", 0xFF3D195B.toInt(), "LIGA", "Vidio · SCTV"
            ),
            League(
                "4480", "uefa.champions", "UEFA Champions League", "Champions League", "Eropa",
                "★", 0xFF0A1E5E.toInt(), "CUP", "SCTV · beIN Sports"
            ),
            League(
                "4335", "esp.1", "Spanish La Liga", "La Liga", "Spanyol",
                "LL", 0xFFEE8707.toInt(), "LIGA", "beIN Sports"
            ),
            League(
                "4332", "ita.1", "Italian Serie A", "Serie A", "Italia",
                "SA", 0xFF024494.toInt(), "LIGA", "beIN Sports"
            ),
            League(
                "4331", "ger.1", "German Bundesliga", "Bundesliga", "Jerman",
                "BL", 0xFFD20515.toInt(), "LIGA", null
            )
        )
    }
}
