package com.mottv.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Semua App (phase 4, user request): a grid of ALL user-launchable apps
 * installed on the TV — everything the stock app drawer would show.
 * Opened from the "Semua App" button in Home's top bar.
 *
 * Query: ACTION_MAIN with LEANBACK_LAUNCHER and LAUNCHER categories,
 * deduped by package (leanback entry wins), our own package excluded,
 * sorted A–Z by the app's real label. Artwork follows the same rules as
 * Home/Games: sharp banners whole over a blurred backdrop (never
 * cropped), otherwise the icon centered; label sits UNDER the artwork.
 * If the query genuinely finds nothing, an honest empty state is shown.
 */
class AllAppsActivity : AppCompatActivity() {

    data class AppEntry(
        val packageName: String,
        val activityName: String?,
        val label: String,
        val art: Artwork?,
        val initial: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_apps)
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))

        val apps = loadApps()
        findViewById<TextView>(R.id.countPill).text = "${apps.size} aplikasi"
        findViewById<TextView>(R.id.emptyText).visibility =
            if (apps.isEmpty()) View.VISIBLE else View.GONE

        val grid = findViewById<RecyclerView>(R.id.appsGrid)
        grid.layoutManager = GridLayoutManager(this, 6)
        grid.adapter = AppsAdapter(apps)
        grid.post { grid.getChildAt(0)?.requestFocus() }
    }

    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val byPackage = linkedMapOf<String, AppEntry>()

        fun addEntry(ri: ResolveInfo) {
            val pkg = ri.activityInfo?.packageName ?: return
            if (pkg == packageName) return // never list ourselves
            if (byPackage.containsKey(pkg)) return
            val info: ApplicationInfo = ri.activityInfo.applicationInfo
            val banner = try { pm.getApplicationBanner(info) } catch (e: Exception) { null }
            var icon = try { pm.getApplicationIcon(info) } catch (e: Exception) { null }
            if (icon == null) icon = try { ri.loadIcon(pm) } catch (e: Exception) { null }
            TileArt.prepare(banner)
            TileArt.prepare(icon)
            val art = Artwork(banner, icon, TileArt.widthPx(banner))
            val label = try {
                ri.loadLabel(pm)?.toString()
            } catch (e: Exception) {
                null
            } ?: pm.getApplicationLabel(info)?.toString() ?: pkg
            byPackage[pkg] = AppEntry(
                pkg, ri.activityInfo.name, label, art, label.take(2).uppercase()
            )
        }

        return try {
            // Leanback first so the TV entry of a package wins the dedupe.
            for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
                for (ri in pm.queryIntentActivities(intent, 0)) addEntry(ri)
            }
            byPackage.values.sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            // Query failure → honest empty state (header shows 0 aplikasi).
            emptyList()
        }
    }

    private fun launch(entry: AppEntry) {
        var intent = AppResolver.launchIntent(this, entry.packageName)
        if (intent == null && entry.activityName != null) {
            intent = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(entry.packageName, entry.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        if (intent != null) {
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                // fall through to the toast
            }
        }
        Toast.makeText(this, "${entry.label} tidak bisa dibuka", Toast.LENGTH_SHORT).show()
    }

    inner class AppsAdapter(private val items: List<AppEntry>) :
        RecyclerView.Adapter<AppsAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val card: View = view.findViewById(R.id.appCard)
            val art: FitArtView = view.findViewById(R.id.appArt)
            val shimmer: View = view.findViewById(R.id.appShimmer)
            val initial: TextView = view.findViewById(R.id.appInitial)
            val label: TextView = view.findViewById(R.id.appLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val grad = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#4A4F59"), Color.parseColor("#2E3138"))
            )
            grad.cornerRadius = 14f * holder.card.resources.displayMetrics.density
            holder.card.background = grad

            val density = holder.card.resources.displayMetrics.density
            // Shimmer placeholder sits behind the artwork while it is
            // bound (phase 4.3); tiles with no artwork at all use the
            // initial-letter fallback instead, so the shimmer hides.
            holder.shimmer.visibility =
                if (item.art?.banner != null || item.art?.icon != null) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            when {
                TileArt.isUsableBanner(item.art) ->
                    holder.art.setFullArt(item.art!!.banner)
                TileArt.centeredDrawable(item.art) != null ->
                    holder.art.setCentered(
                        TileArt.centeredDrawable(item.art), (18 * density).toInt()
                    )
                else -> holder.art.clear()
            }
            holder.initial.text =
                if (TileArt.centeredDrawable(item.art) == null) item.initial else ""
            holder.label.text = item.label

            holder.card.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.foreground = ContextCompat.getDrawable(v.context, R.drawable.tile_focus_border)
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
                } else {
                    v.foreground = null
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
            holder.card.setOnClickListener { launch(item) }
            // Long-press menu (phase 5): every entry here is a real
            // installed package (our own package is never listed).
            AppActions.bind(holder.card, this@AllAppsActivity, item.label, item.packageName) {
                launch(item)
            }
        }
    }

    /** Remote MENU key opens the same long-press app menu (phase 5). */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (AppActions.handleMenuKey(this, event)) return true
        return super.dispatchKeyEvent(event)
    }
}
