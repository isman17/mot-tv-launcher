package com.mottv.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pilih Tanggal (design frame 7): month-grid calendar, full D-pad navigation,
 * month/year steppers. ANY date is selectable (e.g. 11 Feb 2027) — whether a
 * schedule exists for it is the data source's business, shown honestly by the
 * caller. Returns the picked date as EXTRA_DATE ("yyyy-MM-dd").
 */
class CalendarActivity : AppCompatActivity() {

    private var displayed: YearMonth = YearMonth.now()
    private var selected: LocalDate = LocalDate.now()
    private lateinit var grid: GridLayout
    private lateinit var monthLabel: TextView
    private lateinit var selectedLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))

        intent.getStringExtra(EXTRA_INITIAL)?.let {
            try {
                selected = LocalDate.parse(it)
            } catch (e: Exception) {
                // keep today
            }
        }
        displayed = YearMonth.from(selected)

        grid = findViewById(R.id.calGrid)
        monthLabel = findViewById(R.id.monthLabel)
        selectedLabel = findViewById(R.id.selectedLabel)

        styleNav(findViewById(R.id.backBtn)) { finish() }
        styleNav(findViewById(R.id.monthPrev)) { displayed = displayed.minusMonths(1); render() }
        styleNav(findViewById(R.id.monthNext)) { displayed = displayed.plusMonths(1); render() }
        styleNav(findViewById(R.id.yearPrev)) { displayed = displayed.minusYears(1); render() }
        styleNav(findViewById(R.id.yearNext)) { displayed = displayed.plusYears(1); render() }

        render()
    }

    private fun styleNav(tv: TextView, onClick: () -> Unit) {
        tv.setOnFocusChangeListener { v, hasFocus ->
            val t = v as TextView
            t.background = ContextCompat.getDrawable(
                this, if (hasFocus) R.drawable.pill_on else R.drawable.pill_bg
            )
            t.setTextColor(if (hasFocus) Color.parseColor("#191C22") else Color.WHITE)
        }
        tv.setOnClickListener { onClick() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun render() {
        monthLabel.text = displayed.format(MONTH_FMT)
        selectedLabel.text = selected.format(LABEL_FMT)
        grid.removeAllViews()

        val first = displayed.atDay(1)
        // Week starts Monday (Sn): DayOfWeek Mon=1..Sun=7 → offset Mon=0..Sun=6.
        val offset = first.dayOfWeek.value - 1
        val days = displayed.lengthOfMonth()
        val today = LocalDate.now()

        for (i in 0 until offset) {
            grid.addView(TextView(this), cellParams())
        }
        for (day in 1..days) {
            val date = displayed.atDay(day)
            val isSelected = date == selected
            val isToday = date == today
            val cell = TextView(this).apply {
                text = day.toString()
                textSize = 16f
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                when {
                    isSelected -> {
                        background = ContextCompat.getDrawable(
                            this@CalendarActivity, R.drawable.pill_gold
                        )
                        setTextColor(Color.parseColor("#26221A"))
                        setTypeface(typeface, Typeface.BOLD)
                    }
                    isToday -> {
                        background = ContextCompat.getDrawable(
                            this@CalendarActivity, R.drawable.menu_item
                        )
                        setTextColor(Color.parseColor("#FFD166"))
                        setTypeface(typeface, Typeface.BOLD)
                    }
                    else -> {
                        background = ContextCompat.getDrawable(
                            this@CalendarActivity, R.drawable.menu_item
                        )
                        setTextColor(Color.WHITE)
                    }
                }
                setOnFocusChangeListener { v, hasFocus ->
                    val t = v as TextView
                    if (date == selected) return@setOnFocusChangeListener
                    if (hasFocus) {
                        t.background = ContextCompat.getDrawable(
                            this@CalendarActivity, R.drawable.pill_on
                        )
                        t.setTextColor(Color.parseColor("#191C22"))
                    } else {
                        t.background = ContextCompat.getDrawable(
                            this@CalendarActivity, R.drawable.menu_item
                        )
                        t.setTextColor(
                            if (date == today) Color.parseColor("#FFD166") else Color.WHITE
                        )
                    }
                }
                setOnClickListener {
                    setResult(
                        Activity.RESULT_OK,
                        Intent().putExtra(EXTRA_DATE, date.format(ISO_FMT))
                    )
                    finish()
                }
            }
            grid.addView(cell, cellParams())
            if (isSelected) cell.post { cell.requestFocus() }
        }
    }

    private fun cellParams(): GridLayout.LayoutParams {
        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = dp(46)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
        return params
    }

    companion object {
        const val EXTRA_INITIAL = "extra_initial_date"
        const val EXTRA_DATE = "extra_date"
        private val MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID"))
        private val LABEL_FMT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID"))
        private val ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
