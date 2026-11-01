package com.mottv.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Jadwal Sholat (design frame 8) with REAL data from the MyQuran API
 * (Kemenag-based city list + daily schedule). No data is faked: network or API
 * failures surface as an honest status message.
 *
 * Phase-3 layout (user feedback on a 1366×768 TV):
 *  - The city list is NOT shown by default. The page opens on the schedule
 *    for the saved city — first-run default KOTA BANDUNG (was Jakarta) —
 *    with a clear "Ganti Kota" button. The searchable picker only appears
 *    when that button is pressed; picking a city (or Back) closes it.
 *  - The 8 times are a compact 4×2 tile grid and the whole schedule sits in
 *    a ScrollView, sized for 768p — in 0.2.0 the right-hand column (city
 *    list beside a tall 8-row list) overflowed and clipped on the TV.
 */
class SholatActivity : AppCompatActivity() {

    data class City(val id: String, val name: String)

    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var statusText: TextView
    private lateinit var cityName: TextView
    private lateinit var dateLine: TextView
    private lateinit var nextPrayer: TextView
    private lateinit var countdown: TextView
    private lateinit var timesGrid: GridLayout
    private lateinit var nextPrayerCard: View
    private lateinit var sholatSkeleton: View
    private lateinit var scheduleScroll: View
    private lateinit var pickerPanel: View
    private lateinit var pickerStatus: TextView
    private lateinit var changeCityBtn: TextView
    private lateinit var adapter: CityAdapter

    private var allCities: List<City> = emptyList()
    private var citiesLoaded = false
    private var currentTimes: List<Pair<String, String>> = emptyList() // label -> "HH:mm"
    private var currentCity: City? = null
    private var pickerOpen = false

    private val countdownTick = object : Runnable {
        override fun run() {
            renderNext()
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sholat)
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))

        statusText = findViewById(R.id.statusText)
        cityName = findViewById(R.id.cityName)
        dateLine = findViewById(R.id.dateLine)
        nextPrayer = findViewById(R.id.nextPrayer)
        countdown = findViewById(R.id.countdown)
        timesGrid = findViewById(R.id.timesGrid)
        nextPrayerCard = findViewById(R.id.nextPrayerCard)
        sholatSkeleton = findViewById(R.id.sholatSkeleton)
        // Skeleton corners match the real cards/tiles (12dp).
        fun roundBars(v: View) {
            if (v is ShimmerBar) v.cornerRadiusDp = 12f
            if (v is ViewGroup) for (i in 0 until v.childCount) roundBars(v.getChildAt(i))
        }
        roundBars(sholatSkeleton)
        scheduleScroll = findViewById(R.id.scheduleScroll)
        pickerPanel = findViewById(R.id.pickerPanel)
        pickerStatus = findViewById(R.id.pickerStatus)
        changeCityBtn = findViewById(R.id.changeCityBtn)

        // Fase 6.1: bg is the shared mot_focus_gold selector (layout,
        // static dark text); focusFx adds scale/lift.
        changeCityBtn.focusFx(1.05f)
        changeCityBtn.setOnClickListener { openPicker() }

        val list = findViewById<RecyclerView>(R.id.cityList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = CityAdapter { city -> selectCity(city) }
        list.adapter = adapter

        findViewById<EditText>(R.id.citySearch).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                adapter.submit(
                    if (q.isEmpty()) allCities
                    else allCities.filter { it.name.contains(q, ignoreCase = true) }
                )
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Back closes the picker without changing the city; otherwise exits.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pickerOpen) closePicker() else finish()
            }
        })

        showSkeleton()
        loadCities()
        changeCityBtn.requestFocus()
    }

    // ---------- Loading skeleton (phase 4.3) ----------

    /** While the schedule is in flight, the real card + grid step aside
     *  and the shimmer blocks take their exact place. */
    private fun showSkeleton() {
        sholatSkeleton.visibility = View.VISIBLE
        nextPrayerCard.visibility = View.GONE
        timesGrid.visibility = View.GONE
    }

    private fun hideSkeleton() {
        sholatSkeleton.visibility = View.GONE
        nextPrayerCard.visibility = View.VISIBLE
        timesGrid.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(countdownTick)
        handler.post(countdownTick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(countdownTick)
    }

    private fun openPicker() {
        pickerOpen = true
        scheduleScroll.visibility = View.GONE
        pickerPanel.visibility = View.VISIBLE
        pickerStatus.text = if (citiesLoaded) "" else "Memuat daftar kota…"
        findViewById<EditText>(R.id.citySearch).requestFocus()
    }

    private fun closePicker() {
        pickerOpen = false
        pickerPanel.visibility = View.GONE
        scheduleScroll.visibility = View.VISIBLE
        changeCityBtn.requestFocus()
    }

    private fun loadCities() {
        statusText.text = "Memuat…"
        io.execute {
            try {
                val json = Net.getJson("https://api.myquran.com/v2/sholat/kota/semua")
                val arr = json.getJSONArray("data")
                val cities = ArrayList<City>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    cities.add(City(o.getString("id"), o.getString("lokasi")))
                }
                handler.post {
                    allCities = cities
                    citiesLoaded = true
                    adapter.submit(cities)
                    pickerStatus.text = ""
                    statusText.text = ""
                    val savedId = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getString(KEY_CITY_ID, null)
                    // Saved city wins; first-run default is KOTA BANDUNG
                    // (user's home city — phase-3 request, replaces Jakarta).
                    val initial = cities.firstOrNull { it.id == savedId }
                        ?: cities.firstOrNull { it.name.equals("KOTA BANDUNG", ignoreCase = true) }
                        ?: cities.firstOrNull { it.name.startsWith("KOTA BANDUNG", ignoreCase = true) }
                        ?: cities.firstOrNull()
                    if (initial != null) selectCity(initial, keepPickerAsIs = true) else {
                        hideSkeleton()
                        statusText.text = "Daftar kota kosong dari server."
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    hideSkeleton()
                    statusText.text = "Gagal memuat daftar kota — cek koneksi internet TV. (${e.message})"
                    pickerStatus.text = "Gagal memuat daftar kota (${e.message})"
                }
            }
        }
    }

    private fun selectCity(city: City, keepPickerAsIs: Boolean = false) {
        currentCity = city
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CITY_ID, city.id)
            .putString(KEY_CITY_NAME, city.name)
            .apply()
        if (pickerOpen && !keepPickerAsIs) closePicker()
        cityName.text = titleCase(city.name)
        statusText.text = "Memuat jadwal…"
        showSkeleton()
        val cal = Calendar.getInstance()
        val yyyy = cal.get(Calendar.YEAR).toString()
        val mm = String.format(Locale.US, "%02d", cal.get(Calendar.MONTH) + 1)
        val dd = String.format(Locale.US, "%02d", cal.get(Calendar.DAY_OF_MONTH))
        io.execute {
            try {
                val json = Net.getJson(
                    "https://api.myquran.com/v2/sholat/jadwal/${city.id}/$yyyy/$mm/$dd"
                )
                val data = json.getJSONObject("data")
                val jadwal = data.getJSONObject("jadwal")
                val times = listOf(
                    "Imsak" to jadwal.getString("imsak"),
                    "Subuh" to jadwal.getString("subuh"),
                    "Terbit" to jadwal.getString("terbit"),
                    "Dhuha" to jadwal.getString("dhuha"),
                    "Dzuhur" to jadwal.getString("dzuhur"),
                    "Ashar" to jadwal.getString("ashar"),
                    "Maghrib" to jadwal.getString("maghrib"),
                    "Isya" to jadwal.getString("isya")
                )
                val tanggal = jadwal.optString("tanggal")
                handler.post {
                    currentTimes = times
                    dateLine.text = tanggal
                    statusText.text = ""
                    renderTimes()
                    renderNext()
                }
            } catch (e: Exception) {
                handler.post {
                    hideSkeleton()
                    statusText.text = "Gagal memuat jadwal — cek koneksi internet TV. (${e.message})"
                }
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** 4×2 tile grid — sized to fit 768p fully, next prayer highlighted gold. */
    private fun renderTimes() {
        hideSkeleton()
        timesGrid.removeAllViews()
        val nextLabel = computeNext()?.first?.removeSuffix(" (besok)")
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        currentTimes.forEachIndexed { index, (label, time) ->
            val isNext = label == nextLabel
            val parts = time.split(":")
            val t = (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
                (parts.getOrNull(1)?.toIntOrNull() ?: 0)
            val passed = !isNext && t <= nowMinutes

            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val row = index / 4
                val col = index % 4
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(row), GridLayout.spec(col, 1f)
                ).apply {
                    width = 0
                    height = dp(76)
                    setMargins(
                        if (col == 0) 0 else dp(5),
                        if (row == 0) 0 else dp(10),
                        if (col == 3) 0 else dp(5),
                        0
                    )
                }
                val bg = GradientDrawable()
                bg.cornerRadius = dp(12).toFloat()
                if (isNext) {
                    bg.setColor(Color.parseColor("#FFD166"))
                } else {
                    bg.setColor(Color.parseColor("#1FFFFFFF"))
                    bg.setStroke(dp(1), Color.parseColor("#2BFFFFFF"))
                }
                background = bg
                elevation = dp(2).toFloat()
                if (passed) alpha = 0.45f
            }
            val name = TextView(this).apply {
                text = label.uppercase(Locale("id", "ID"))
                textSize = 11.5f
                letterSpacing = 0.08f
                setTextColor(
                    if (isNext) Color.parseColor("#7A5A00")
                    else Color.parseColor("#A8FFFFFF")
                )
            }
            val value = TextView(this).apply {
                text = time
                textSize = 21f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (isNext) Color.parseColor("#26221A") else Color.WHITE)
            }
            tile.addView(name)
            tile.addView(value)
            timesGrid.addView(tile)
        }
    }

    /** Returns (label, minutesUntil) of the next prayer today, or Imsak-tomorrow if all passed. */
    private fun computeNext(): Pair<String, Int>? {
        if (currentTimes.isEmpty()) return null
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        for ((label, time) in currentTimes) {
            val parts = time.split(":")
            if (parts.size != 2) continue
            val t = parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0) ?: continue
            if (t > nowMinutes) return label to (t - nowMinutes)
        }
        // All today's times passed: next is Imsak tomorrow.
        val first = currentTimes.first()
        val parts = first.second.split(":")
        val t = (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
        return (first.first + " (besok)") to (24 * 60 - nowMinutes + t)
    }

    private fun renderNext() {
        val next = computeNext()
        if (next == null) {
            nextPrayer.text = ""
            countdown.text = ""
            return
        }
        val baseLabel = next.first.removeSuffix(" (besok)")
        val time = currentTimes.firstOrNull { it.first == baseLabel }?.second ?: ""
        nextPrayer.text = "${next.first} · $time"
        val mins = next.second
        countdown.text = when {
            mins >= 60 -> "−${mins / 60} jam ${mins % 60} menit lagi"
            mins > 0 -> "−$mins menit lagi"
            else -> "Sebentar lagi"
        }
        // Keep the grid highlight in sync.
        if (timesGrid.childCount == currentTimes.size && currentTimes.isNotEmpty()) renderTimes()
    }

    private fun titleCase(s: String): String =
        s.lowercase(Locale("id", "ID")).split(" ").joinToString(" ") {
            it.replaceFirstChar { c -> c.titlecase(Locale("id", "ID")) }
        }

    inner class CityAdapter(private val onClick: (City) -> Unit) :
        RecyclerView.Adapter<CityAdapter.VH>() {

        private var items: List<City> = emptyList()

        fun submit(list: List<City>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val city = items[position]
            holder.text.text = titleCase(city.name)
            holder.text.setTextColor(Color.WHITE)
            holder.text.textSize = 15f
            holder.text.setPadding(dp(14), dp(10), dp(14), dp(10))
            // Fase 6.1: shared row selector — focus brightens + strokes
            // the row; text stays white (no more pill_on swap). Row → no
            // scale.
            holder.itemView.background = ContextCompat.getDrawable(this@SholatActivity, R.drawable.mot_focus_row)
            holder.itemView.isFocusable = true
            holder.itemView.isClickable = true
            holder.itemView.setOnClickListener { onClick(city) }
        }
    }

    companion object {
        private const val PREFS = "sholat_prefs"
        private const val KEY_CITY_ID = "city_id"
        private const val KEY_CITY_NAME = "city_name"
    }
}
