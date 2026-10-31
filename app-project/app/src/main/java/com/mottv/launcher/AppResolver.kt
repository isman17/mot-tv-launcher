package com.mottv.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** What a home tile does when clicked. */
sealed class TileAction {
    object OpenBrowser : TileAction()
    object OpenGames : TileAction()
    data class LaunchApp(val candidates: List<String>) : TileAction()
}

data class HomeTile(
    val key: String,
    val label: String,
    val description: String,
    val initial: String,
    val accentStart: Int,
    val accentEnd: Int,
    val action: TileAction
) {
    val candidates: List<String>
        get() = (action as? TileAction.LaunchApp)?.candidates ?: emptyList()
}

/** Resolved state of a tile against the real PackageManager. */
data class ResolvedTile(
    val tile: HomeTile,
    val installedPackage: String?,
    val realLabel: String?,
    /** Banner + icon kept separate — see [Artwork] for why (phase-3 quality fix). */
    val artwork: Artwork?
) {
    val installed: Boolean get() = installedPackage != null
    val displayLabel: String get() = realLabel ?: tile.label
}

object AppResolver {

    val HOME_TILES: List<HomeTile> = listOf(
        HomeTile(
            "browser", "Browser",
            "Browser bawaan launcher — cari dan putar video dari web tanpa pindah app",
            "◎", 0xFF22B8F0.toInt(), 0xFF2563EB.toInt(), TileAction.OpenBrowser
        ),
        HomeTile(
            "youtube", "YouTube",
            "Video, musik, dan live streaming dari YouTube",
            "▶", 0xFFFF4E45.toInt(), 0xFFB2071D.toInt(),
            TileAction.LaunchApp(
                listOf("com.google.android.youtube.tv", "com.google.android.youtube")
            )
        ),
        HomeTile(
            "netflix", "Netflix",
            "Film, serial, dan dokumenter — lanjutkan tontonanmu",
            "N", 0xFFFF3D47.toInt(), 0xFF8F1019.toInt(),
            TileAction.LaunchApp(listOf("com.netflix.ninja", "com.netflix.mediaclient"))
        ),
        HomeTile(
            "wetv", "WeTV",
            "Drama Asia dan variety show",
            "W", 0xFF2EE6A8.toInt(), 0xFF0A9D7A.toInt(),
            TileAction.LaunchApp(listOf("com.tencent.qqlive"))
        ),
        HomeTile(
            "vidio", "Vidio",
            "Streaming lokal, bola, dan sinetron",
            "V", 0xFFFFB020.toInt(), 0xFFFF5E1A.toInt(),
            TileAction.LaunchApp(listOf("com.vidio.android.tv", "com.vidio.android"))
        ),
        HomeTile(
            "games", "Games",
            "Semua game yang terinstal di TV ini",
            "G", 0xFFC86BFF.toInt(), 0xFF6D28D9.toInt(), TileAction.OpenGames
        ),
        HomeTile(
            "playstore", "Play Store",
            "Instal game dan aplikasi baru, plus update",
            "▶", 0xFFFFFFFF.toInt(), 0xFFDFE5EC.toInt(),
            TileAction.LaunchApp(listOf("com.android.vending"))
        )
    )

    fun resolve(context: Context, tile: HomeTile): ResolvedTile {
        val pm = context.packageManager
        if (tile.action !is TileAction.LaunchApp) {
            // In-app modules (Browser / Games) always "exist".
            return ResolvedTile(tile, installedPackage = "inapp", realLabel = null, artwork = null)
        }
        for (pkg in tile.candidates) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val banner: Drawable? = try {
                    pm.getApplicationBanner(info)
                } catch (e: Exception) {
                    null
                }
                val icon: Drawable? = try {
                    pm.getApplicationIcon(info)
                } catch (e: Exception) {
                    null
                }
                TileArt.prepare(banner)
                TileArt.prepare(icon)
                val label = pm.getApplicationLabel(info)?.toString()
                return ResolvedTile(
                    tile, pkg, label,
                    Artwork(banner, icon, TileArt.widthPx(banner))
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // try next candidate
            }
        }
        return ResolvedTile(tile, null, null, null)
    }

    fun launchIntent(context: Context, packageName: String): Intent? {
        val pm = context.packageManager
        return pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
    }
}
