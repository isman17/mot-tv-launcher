package com.mottv.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONArray

/**
 * Pencarian (design frame 3): big input + category chips + on-screen keyboard
 * usable with the D-pad + persisted recent searches. The Browser tile on Home
 * opens this page; a typed URL still opens the WebView browser directly.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var input: EditText
    private var selectedCategory: String = CATEGORIES[0]
    private val chipViews = HashMap<String, TextView>()

    /**
     * Voice search (phase 5): delegated to the system's speech
     * recognizer — no audio permission of our own. The top hypothesis
     * fills the query field and the search runs immediately.
     */
    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) {
                input.setText(spoken)
                input.setSelection(input.length())
                doSearch()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))

        input = findViewById(R.id.queryInput)
        // Fase 6.1: pill backgrounds are the shared mot_focus_* selectors
        // in the layout; focusFx adds scale/lift. (The old
        // styleFocusablePill background swap is gone app-wide.)
        findViewById<TextView>(R.id.backBtn).apply {
            focusFx(1.05f)
            setOnClickListener { finish() }
        }
        findViewById<TextView>(R.id.searchBtn).apply {
            focusFx(1.05f)
            setOnClickListener { doSearch() }
        }
        findViewById<TextView>(R.id.micBtn).apply {
            focusFx(1.05f)
            setOnClickListener { startVoiceSearch() }
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) {
                doSearch(); true
            } else false
        }

        buildChips()
        buildKeyboard()
        renderRecents()
        input.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        renderRecents()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildChips() {
        val row = findViewById<LinearLayout>(R.id.chipsRow)
        row.removeAllViews()
        for (cat in CATEGORIES) {
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
                // Fase 6.1: selected chip = mot_focus_white (default is the
                // old solid-white pill_on; focused adds a gold ring),
                // unselected = mot_focus_pill. Focus itself is the
                // drawable's job now — no listener swap.
                background = ContextCompat.getDrawable(
                    this@SearchActivity,
                    if (cat == selectedCategory) R.drawable.mot_focus_white
                    else R.drawable.mot_focus_pill
                )
                setTextColor(
                    if (cat == selectedCategory) Color.parseColor("#191C22") else Color.WHITE
                )
                focusFx(1.05f)
                setOnClickListener {
                    selectedCategory = cat
                    buildChips()
                    chipViews[cat]?.requestFocus()
                }
            }
            chipViews[cat] = chip
            row.addView(chip)
        }
    }

    private fun buildKeyboard() {
        val grid = findViewById<GridLayout>(R.id.keyboardGrid)
        grid.removeAllViews()
        val rows = listOf(
            "abcdefghij",
            "klmnopqrst",
            "uvwxyz0123",
            "456789"
        )
        for ((r, rowStr) in rows.withIndex()) {
            for ((c, ch) in rowStr.withIndex()) {
                if (ch == ' ') continue
                addKey(grid, ch.toString(), r, c, 1) { appendText(ch.toString()) }
            }
        }
        // Function keys on the last row: Spasi (2), Hapus (1), Cari (1 gold).
        addKey(grid, "Spasi", 3, 6, 2) { appendText(" ") }
        addKey(grid, "Hapus", 3, 8, 1) {
            val t = input.text?.toString().orEmpty()
            if (t.isNotEmpty()) input.setText(t.dropLast(1)).also { input.setSelection(input.length()) }
        }
        addKey(grid, "Cari", 3, 9, 1, gold = true) { doSearch() }
    }

    private fun addKey(
        grid: GridLayout, label: String, row: Int, col: Int, span: Int,
        gold: Boolean = false, onClick: () -> Unit
    ) {
        val key = TextView(this).apply {
            text = label
            textSize = if (label.length == 1) 15f else 13f
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setTextColor(if (gold) Color.parseColor("#26221A") else Color.WHITE)
            // Fase 6.1: keys use the shared selectors (gold key = gold
            // selector; letter keys = row selector, same radius/fill as
            // the old menu_item) + focusFx. No more pill_on swap.
            background = ContextCompat.getDrawable(
                this@SearchActivity,
                if (gold) R.drawable.mot_focus_gold else R.drawable.mot_focus_row
            )
            focusFx(1.06f)
            setOnClickListener { onClick() }
        }
        val params = GridLayout.LayoutParams(
            GridLayout.spec(row), GridLayout.spec(col, span, 1f)
        ).apply {
            width = 0
            height = dp(44)
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
        grid.addView(key, params)
    }

    private fun appendText(s: String) {
        input.append(s)
        input.setSelection(input.length())
    }

    private fun looksLikeUrl(raw: String): Boolean {
        if (raw.contains(" ")) return false
        if (raw.startsWith("http://") || raw.startsWith("https://")) return true
        // domain.tld[/path] — a dot with a plausible TLD and no spaces.
        return raw.contains(".") && raw.substringAfterLast(".").length in 2..6 &&
            !raw.startsWith(".")
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Bicara sekarang…")
        }
        // No recognizer on this TV (or it refuses) → honest message,
        // the on-screen keyboard stays fully usable.
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(
                this, "Pencarian suara tidak tersedia di TV ini", Toast.LENGTH_SHORT
            ).show()
            return
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this, "Pencarian suara tidak tersedia di TV ini", Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** Remotes with a dedicated search key trigger voice search too. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_SEARCH && event.action == KeyEvent.ACTION_UP) {
            startVoiceSearch()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun doSearch() {
        val raw = input.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return
        saveRecent(raw)
        if (looksLikeUrl(raw)) {
            val url = if (raw.startsWith("http")) raw else "https://$raw"
            startActivity(
                Intent(this, BrowserActivity::class.java)
                    .putExtra(BrowserActivity.EXTRA_URL, url)
            )
            return
        }
        startActivity(
            Intent(this, ResultsActivity::class.java)
                .putExtra(ResultsActivity.EXTRA_QUERY, raw)
                .putExtra(ResultsActivity.EXTRA_CATEGORY, selectedCategory)
        )
    }

    // --- Recent searches: SharedPreferences, JSON array, newest first, max 8 ---

    private fun loadRecents(): List<String> {
        val raw = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RECENT, null)
            ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRecent(q: String) {
        val list = loadRecents().filterNot { it.equals(q, ignoreCase = true) }
        val next = (listOf(q) + list).take(8)
        val arr = JSONArray()
        next.forEach { arr.put(it) }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_RECENT, arr.toString()).apply()
    }

    private fun renderRecents() {
        val recents = loadRecents()
        val label = findViewById<TextView>(R.id.recentsLabel)
        val row = findViewById<LinearLayout>(R.id.recentsRow)
        row.removeAllViews()
        label.visibility = if (recents.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        for (recent in recents) {
            val pill = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setPadding(dp(14), dp(7), dp(14), dp(7))
                text = recent
                textSize = 13f
                setTextColor(Color.WHITE)
                background = ContextCompat.getDrawable(this@SearchActivity, R.drawable.mot_focus_pill)
                isFocusable = true
                isClickable = true
                focusFx(1.05f)
                setOnClickListener {
                    input.setText(recent)
                    input.setSelection(input.length())
                    doSearch()
                }
            }
            row.addView(pill)
        }
    }

    companion object {
        val CATEGORIES = listOf("Semua", "Film", "Musik", "Video", "Web")
        private const val PREFS = "search_prefs"
        private const val KEY_RECENT = "recent_searches"
    }
}
