package com.mottv.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Hasil Pencarian (design frame 4): REAL results fetched from the DuckDuckGo
 * HTML endpoint and parsed. No results are invented: network failure, the
 * DDG robot-check page, and zero hits all surface as honest status text.
 * Thumbnails: the og:image of a result page, fetched lazily for the first
 * few results; until/unless it loads, a labelled gradient+initial placeholder
 * is shown (the design's fallback, not a fake thumbnail).
 */
class ResultsActivity : AppCompatActivity() {

    data class SearchResult(
        val title: String,
        val url: String,
        val snippet: String,
        var thumbUrl: String? = null
    )

    private val io = Executors.newFixedThreadPool(3)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var statusText: TextView
    private lateinit var countPill: TextView
    private lateinit var resultsSkeleton: View
    private lateinit var adapter: ResultsAdapter

    private var query: String = ""
    private var category: String = SearchActivity.CATEGORIES[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))

        query = intent.getStringExtra(EXTRA_QUERY).orEmpty()
        category = intent.getStringExtra(EXTRA_CATEGORY) ?: SearchActivity.CATEGORIES[0]

        findViewById<TextView>(R.id.resultTitle).text = "“$query”"
        statusText = findViewById(R.id.statusText)
        countPill = findViewById(R.id.countPill)
        resultsSkeleton = findViewById(R.id.resultsSkeleton)

        findViewById<TextView>(R.id.backBtn).apply {
            focusFx(1.05f) // fase 6.1: bg is mot_focus_pill in layout
            setOnClickListener { finish() }
        }

        val list = findViewById<RecyclerView>(R.id.resultsList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = ResultsAdapter { result -> openResult(result) }
        list.adapter = adapter

        buildChips()
        fetchResults()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildChips() {
        val row = findViewById<LinearLayout>(R.id.chipsRow)
        row.removeAllViews()
        for (cat in SearchActivity.CATEGORIES) {
            val chip = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setPadding(dp(16), dp(7), dp(16), dp(7))
                text = cat
                textSize = 14f
                isFocusable = true
                isClickable = true
                val sel = cat == category
                // Fase 6.1: same shared selectors as SearchActivity chips.
                background = ContextCompat.getDrawable(
                    this@ResultsActivity,
                    if (sel) R.drawable.mot_focus_white else R.drawable.mot_focus_pill
                )
                setTextColor(if (sel) Color.parseColor("#191C22") else Color.WHITE)
                focusFx(1.05f)
                setOnClickListener {
                    if (category != cat) {
                        category = cat
                        buildChips()
                        fetchResults()
                    }
                }
            }
            row.addView(chip)
        }
    }

    /** Category = query hint appended to the user's words (documented, not hidden magic). */
    private fun effectiveQuery(): String = when (category) {
        "Film" -> "$query film"
        "Musik" -> "$query musik lagu"
        "Video" -> "$query video"
        else -> query
    }

    private fun fetchResults() {
        statusText.text = "Mencari…"
        countPill.text = ""
        adapter.submit(emptyList())
        resultsSkeleton.visibility = View.VISIBLE
        val q = effectiveQuery()
        io.execute {
            try {
                val url = "https://html.duckduckgo.com/html/?q=" +
                    URLEncoder.encode(q, "UTF-8") + "&kl=id-id"
                val htmlText = Net.getText(url)
                if (htmlText.contains("anomaly") && !htmlText.contains("result__a")) {
                    handler.post {
                        resultsSkeleton.visibility = View.GONE
                        statusText.text =
                            "Mesin pencari menampilkan halaman pemeriksaan robot untuk jaringan ini. " +
                                "Coba lagi beberapa saat lagi."
                    }
                    return@execute
                }
                val results = parseResults(htmlText)
                handler.post {
                    resultsSkeleton.visibility = View.GONE
                    if (results.isEmpty()) {
                        statusText.text = "Tidak ada hasil untuk “$query”. Coba kata lain."
                        countPill.text = "0 hasil"
                    } else {
                        statusText.text = ""
                        countPill.text = "${results.size} hasil"
                        adapter.submit(results)
                        findViewById<RecyclerView>(R.id.resultsList)
                            .post {
                                findViewById<RecyclerView>(R.id.resultsList)
                                    .getChildAt(0)?.requestFocus()
                            }
                        enrichThumbnails(results)
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    resultsSkeleton.visibility = View.GONE
                    statusText.text =
                        "Gagal memuat hasil — cek koneksi internet TV. (${e.message})"
                }
            }
        }
    }

    /**
     * Parses the DDG HTML result page. Structure verified against the live
     * endpoint: result links are <a class="result__a" href="//duckduckgo.com/l/?uddg=…">,
     * snippets are class="result__snippet", in matching document order.
     */
    private fun parseResults(htmlText: String): List<SearchResult> {
        val linkRegex = Regex(
            """<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val snippetRegex = Regex(
            """class="result__snippet"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val snippets = snippetRegex.findAll(htmlText)
            .map { cleanHtml(it.groupValues[1]) }.toList()
        val out = ArrayList<SearchResult>()
        for ((i, m) in linkRegex.findAll(htmlText).withIndex()) {
            val rawHref = unescapeHtml(m.groupValues[1])
            val realUrl = extractRealUrl(rawHref) ?: continue
            val title = cleanHtml(m.groupValues[2])
            if (title.isBlank()) continue
            out.add(
                SearchResult(
                    title = title,
                    url = realUrl,
                    snippet = snippets.getOrElse(i) { "" }
                )
            )
        }
        return out
    }

    private fun extractRealUrl(href: String): String? {
        val full = when {
            href.startsWith("//") -> "https:$href"
            href.startsWith("/") -> "https://duckduckgo.com$href"
            else -> href
        }
        return try {
            val uri = java.net.URI(full)
            if (uri.host?.contains("duckduckgo") == true) {
                val params = (uri.rawQuery ?: "").split("&")
                    .mapNotNull {
                        val parts = it.split("=", limit = 2)
                        if (parts.size == 2) parts[0] to parts[1] else null
                    }.toMap()
                params["uddg"]?.let { URLDecoder.decode(it, "UTF-8") } ?: full
            } else full
        } catch (e: Exception) {
            full
        }
    }

    private fun unescapeHtml(s: String): String =
        s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'")
            .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private fun cleanHtml(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    /** Lazily fetch og:image for the first few results; rows update when it arrives. */
    private fun enrichThumbnails(results: List<SearchResult>) {
        for ((index, result) in results.take(6).withIndex()) {
            io.execute {
                val og = fetchOgImage(result.url) ?: return@execute
                handler.post {
                    result.thumbUrl = og
                    adapter.notifyItemChanged(index)
                }
            }
        }
    }

    /** Reads only the head of the page (max ~48 KB) looking for og:image / twitter:image. */
    private fun fetchOgImage(pageUrl: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(pageUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 6_000
            conn.readTimeout = 6_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", Net.BROWSER_UA)
            conn.setRequestProperty("Accept-Encoding", "identity")
            if (conn.responseCode !in 200..299) return null
            val buf = ByteArray(48 * 1024)
            var read = 0
            conn.inputStream?.use { input ->
                while (read < buf.size) {
                    val n = input.read(buf, read, buf.size - read)
                    if (n < 0) break
                    read += n
                    // og tags live in <head>; stop early once past it.
                    if (read > 2048 && String(buf, 0, read, Charsets.UTF_8).contains("</head>")) break
                }
            }
            val head = String(buf, 0, read, Charsets.UTF_8)
            val m = Regex(
                """(?:property|name)="(?:og:image|twitter:image)"[^>]+content="([^"]+)"""",
                RegexOption.IGNORE_CASE
            ).find(head) ?: Regex(
                """content="([^"]+)"[^>]+(?:property|name)="(?:og:image|twitter:image)"""",
                RegexOption.IGNORE_CASE
            ).find(head)
            val raw = m?.groupValues?.get(1)?.let { unescapeHtml(it) } ?: return null
            when {
                raw.startsWith("http") -> raw
                raw.startsWith("//") -> "https:$raw"
                raw.startsWith("/") -> {
                    val base = java.net.URI(pageUrl)
                    "${base.scheme}://${base.host}$raw"
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun openResult(result: SearchResult) {
        if (MediaRouter.isDirectMedia(result.url)) {
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, result.url)
                    .putExtra(PlayerActivity.EXTRA_TITLE, result.title)
            )
        } else {
            startActivity(
                Intent(this, BrowserActivity::class.java)
                    .putExtra(BrowserActivity.EXTRA_URL, result.url)
            )
        }
    }

    inner class ResultsAdapter(private val onClick: (SearchResult) -> Unit) :
        RecyclerView.Adapter<ResultsAdapter.VH>() {

        private var items: List<SearchResult> = emptyList()

        fun submit(list: List<SearchResult>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val card: View = view.findViewById(R.id.resultCard)
            val thumbFrame: FrameLayout = view.findViewById(R.id.thumbFrame)
            val thumbImg: ImageView = view.findViewById(R.id.thumbImg)
            val thumbInitial: TextView = view.findViewById(R.id.thumbInitial)
            val title: TextView = view.findViewById(R.id.resultItemTitle)
            val snippet: TextView = view.findViewById(R.id.resultSnippet)
            val domain: TextView = view.findViewById(R.id.resultDomain)
            val badge: TextView = view.findViewById(R.id.resultBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_result, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val host = MediaRouter.hostOf(item.url) ?: item.url
            holder.title.text = item.title
            holder.snippet.text = item.snippet
            holder.snippet.visibility = if (item.snippet.isBlank()) View.GONE else View.VISIBLE
            holder.domain.text = host

            // Placeholder: deterministic gradient per domain + its initial.
            val palette = PALETTES[kotlin.math.abs(host.hashCode()) % PALETTES.size]
            val grad = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(palette[0], palette[1])
            )
            grad.cornerRadius = dp(9).toFloat()
            holder.thumbFrame.background = grad
            holder.thumbImg.setImageBitmap(null)
            holder.thumbImg.tag = null
            holder.thumbInitial.text =
                (host.removePrefix("www.").firstOrNull() ?: '?').uppercase()
            if (item.thumbUrl != null) {
                holder.thumbInitial.text = ""
                ImgCache.load(item.thumbUrl, holder.thumbImg)
            }

            // Honest badge: only what can be determined from the URL itself.
            val (badgeText, badgeColor) = when {
                MediaRouter.isDirectMedia(item.url) -> "MEDIA" to Color.parseColor("#FFD166")
                MediaRouter.isVideoHost(item.url) -> "VIDEO" to Color.parseColor("#22B8F0")
                else -> null to null
            }
            if (badgeText != null && badgeColor != null) {
                holder.badge.visibility = View.VISIBLE
                holder.badge.text = badgeText
                val bg = GradientDrawable()
                bg.cornerRadius = dp(6).toFloat()
                bg.setColor(badgeColor)
                holder.badge.background = bg
            } else {
                holder.badge.visibility = View.GONE
            }

            // Fase 6.1: no listener — the card background in item_result.xml
            // IS the shared mot_focus_row selector (brighten + white
            // stroke on focus). Full-width row → no scale, by design.
            holder.card.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        const val EXTRA_QUERY = "extra_query"
        const val EXTRA_CATEGORY = "extra_category"
        private val PALETTES = arrayOf(
            intArrayOf(0xFFFFB020.toInt(), 0xFFB34700.toInt()),
            intArrayOf(0xFF2EE6A8.toInt(), 0xFF065F46.toInt()),
            intArrayOf(0xFFC86BFF.toInt(), 0xFF4C1D95.toInt()),
            intArrayOf(0xFF22B8F0.toInt(), 0xFF1D4ED8.toInt())
        )
    }
}
