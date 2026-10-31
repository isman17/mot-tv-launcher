package com.mottv.launcher

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Games folder (design frame 2): grid of games really installed on the TV. */
class GamesActivity : AppCompatActivity() {

    data class GameEntry(
        val packageName: String?, // null = Play Store tile
        val label: String,
        val art: Artwork?,
        val initial: String,
        val isStoreTile: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_games)
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))

        val games = loadGames()
        findViewById<TextView>(R.id.countPill).text = "${games.size} terinstal"
        findViewById<TextView>(R.id.emptyText).visibility =
            if (games.isEmpty()) View.VISIBLE else View.GONE

        val entries = games + GameEntry(
            null, "Instal dari Play Store", null, "+", isStoreTile = true
        )
        val grid = findViewById<RecyclerView>(R.id.gamesGrid)
        grid.layoutManager = GridLayoutManager(this, 4)
        grid.adapter = GamesAdapter(entries)
        grid.post { grid.getChildAt(0)?.requestFocus() }
    }

    private fun loadGames(): List<GameEntry> {
        val pm = packageManager
        val byPackage = linkedMapOf<String, GameEntry>()

        fun addEntry(pkg: String, info: ApplicationInfo) {
            if (byPackage.containsKey(pkg)) return
            val banner = try { pm.getApplicationBanner(info) } catch (e: Exception) { null }
            val icon = try { pm.getApplicationIcon(info) } catch (e: Exception) { null }
            TileArt.prepare(banner)
            TileArt.prepare(icon)
            val art = Artwork(banner, icon, TileArt.widthPx(banner))
            val label = pm.getApplicationLabel(info)?.toString() ?: pkg
            byPackage[pkg] = GameEntry(pkg, label, art, label.take(2).uppercase())
        }

        // There is no Intent.CATEGORY_GAME: query launchable TV apps and keep the
        // ones the system categorises/flags as games.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val info = ri.activityInfo?.applicationInfo ?: continue
                val isGame = info.category == ApplicationInfo.CATEGORY_GAME ||
                    (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                if (isGame) addEntry(ri.activityInfo.packageName, info)
            }
        }
        return byPackage.values.sortedBy { it.label.lowercase() }
    }

    inner class GamesAdapter(private val items: List<GameEntry>) :
        RecyclerView.Adapter<GamesAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val card: View = view.findViewById(R.id.gameCard)
            val art: FitArtView = view.findViewById(R.id.gameArt)
            val shimmer: View = view.findViewById(R.id.gameShimmer)
            val initial: TextView = view.findViewById(R.id.gameInitial)
            val label: TextView = view.findViewById(R.id.gameLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_game, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val grad = if (item.isStoreTile) {
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#DFE5EC"))
                )
            } else {
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor("#4A4F59"), Color.parseColor("#2E3138"))
                )
            }
            grad.cornerRadius = 14f * holder.card.resources.displayMetrics.density
            holder.card.background = grad
            // Same rules as Home: sharp banner shown WHOLE over its blurred
            // backdrop (phase 4 — never cropped); otherwise icon centered.
            // Shimmer placeholder behind the artwork while it binds
            // (phase 4.3); no-artwork tiles (incl. the store tile) hide it.
            holder.shimmer.visibility =
                if (item.art?.banner != null || item.art?.icon != null) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            val density = holder.card.resources.displayMetrics.density
            when {
                TileArt.isUsableBanner(item.art) ->
                    holder.art.setFullArt(item.art!!.banner)
                TileArt.centeredDrawable(item.art) != null ->
                    holder.art.setCentered(
                        TileArt.centeredDrawable(item.art), (26 * density).toInt()
                    )
                else -> holder.art.clear()
            }
            holder.initial.text =
                if (TileArt.centeredDrawable(item.art) == null) item.initial else ""
            holder.initial.setTextColor(if (item.isStoreTile) Color.parseColor("#0A9D58") else Color.WHITE)
            holder.label.text = item.label
            holder.label.setTextColor(if (item.isStoreTile) Color.parseColor("#14202E") else Color.WHITE)

            holder.card.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.foreground = ContextCompat.getDrawable(v.context, R.drawable.tile_focus_border)
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
                } else {
                    v.foreground = null
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
            holder.card.setOnClickListener {
                if (item.isStoreTile) {
                    val intent = AppResolver.launchIntent(this@GamesActivity, "com.android.vending")
                    if (intent != null) startActivity(intent)
                    else Toast.makeText(this@GamesActivity, "Play Store tidak ditemukan di TV ini", Toast.LENGTH_SHORT).show()
                } else if (item.packageName != null) {
                    val intent = AppResolver.launchIntent(this@GamesActivity, item.packageName)
                    if (intent != null) startActivity(intent)
                    else Toast.makeText(this@GamesActivity, "${item.label} tidak bisa dibuka", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
