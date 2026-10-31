package com.mottv.launcher

import android.content.Context
import org.json.JSONArray
import java.util.Calendar

data class Quote(val figure: String, val text: String)

/** Loads assets/quotes.json (verified pool, same file as quotes/quotes.json) and picks 1 per day. */
object Quotes {
    private var cache: List<Quote>? = null

    fun load(context: Context): List<Quote> {
        cache?.let { return it }
        val result = mutableListOf<Quote>()
        try {
            val json = context.assets.open("quotes.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(Quote(o.optString("figure"), o.optString("quote")))
            }
        } catch (e: Exception) {
            // Pool missing/corrupt: caller hides the quote line rather than showing fake text.
        }
        cache = result
        return result
    }

    fun today(context: Context): Quote? {
        val list = load(context)
        if (list.isEmpty()) return null
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return list[dayOfYear % list.size]
    }
}
