package com.mottv.launcher

import android.content.Context

/**
 * Master switch for the Home-button override (Fase 6). One boolean in
 * SharedPreferences, read fresh on every key/window event by
 * [HomeInterceptService] — so the toggle in Pengaturan works instantly,
 * with no service restart. Default ON: once the user has gone to the
 * trouble of enabling the accessibility service, the override is what
 * they came for; turning it off here leaves the service enabled but
 * completely passive.
 */
object HomeOverrideStore {

    private const val PREFS = "home_override"
    private const val KEY_OVERRIDE = "home_override"

    fun isOverrideOn(context: Context): Boolean = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_OVERRIDE, true)
    } catch (e: Exception) {
        true
    }

    fun setOverrideOn(context: Context, on: Boolean) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_OVERRIDE, on).apply()
        } catch (e: Exception) {
            // If the write fails the switch simply keeps its old value;
            // the settings screen re-reads it on resume and shows truth.
        }
    }
}
