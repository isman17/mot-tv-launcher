package com.mottv.launcher

import java.net.URI

/**
 * Decides where a web result URL belongs:
 *  - direct media files (.mp4/.m3u8/.mpd/…) → PlayerActivity (Media3)
 *  - everything else → in-app WebView browser
 * Known video *pages* (YouTube watch pages etc.) stay in the browser: they are
 * web pages, not media files, and the player honestly says so if handed one.
 * DRM services (Netflix, Vidio, WeTV…) are never routed to the player.
 */
object MediaRouter {

    private val MEDIA_EXTENSIONS = listOf(
        ".mp4", ".m3u8", ".mpd", ".webm", ".mkv", ".mov", ".mp3", ".aac", ".ogg", ".flac"
    )

    private val VIDEO_HOSTS = listOf(
        "youtube.com", "youtu.be", "vimeo.com", "dailymotion.com", "dai.ly",
        "twitch.tv", "facebook.com", "instagram.com", "tiktok.com",
        "streamable.com", "bilibili.com", "ok.ru", "rumble.com"
    )

    fun isDirectMedia(url: String): Boolean {
        val path = try {
            URI(url).path ?: url
        } catch (e: Exception) {
            url
        }.lowercase()
        return MEDIA_EXTENSIONS.any { path.endsWith(it) } ||
            path.contains(".m3u8") || path.contains(".mpd")
    }

    fun isVideoHost(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return VIDEO_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    fun hostOf(url: String): String? = try {
        URI(url).host?.lowercase()
    } catch (e: Exception) {
        null
    }
}
