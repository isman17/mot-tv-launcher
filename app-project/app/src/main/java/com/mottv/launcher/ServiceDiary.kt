package com.mottv.launcher

import android.content.Context

/**
 * Service lifecycle diary (Fase 6.2) — DIAGNOSTIC ONLY.
 *
 * On the user's TCL Google TV, enabling "Mot TV — Tombol Home" in the
 * system's accessibility settings silently reverts to OFF right after
 * OK, and that TvSettings build exposes no "allow restricted settings"
 * entry anywhere — so from outside we cannot tell whether the system
 * ever binds [HomeInterceptService] at all. This diary answers exactly
 * that question: the service stamps every lifecycle transition (create,
 * connect, unbind, destroy, interrupt) plus every error its catch
 * blocks swallow, into a tiny SharedPreferences file that
 * [HomeOverrideActivity] renders as a "Diagnostik Layanan" card.
 *
 * Every write is wrapped so the diary itself can NEVER throw into
 * service code — a diagnostic that crashes the patient is worthless.
 */
object ServiceDiary {

    private const val PREFS = "service_diary"

    private const val KEY_CREATED_AT = "created_at"
    private const val KEY_CREATED_COUNT = "created_count"
    private const val KEY_CONNECTED_AT = "connected_at"
    private const val KEY_CONNECTED_COUNT = "connected_count"
    private const val KEY_UNBOUND_AT = "unbound_at"
    private const val KEY_UNBOUND_COUNT = "unbound_count"
    private const val KEY_DESTROYED_AT = "destroyed_at"
    private const val KEY_DESTROYED_COUNT = "destroyed_count"
    private const val KEY_INTERRUPT_COUNT = "interrupt_count"
    private const val KEY_ERROR_CLASS = "last_error_class"
    private const val KEY_ERROR_MESSAGE = "last_error_message"
    private const val KEY_ERROR_AT = "last_error_at"

    /** Immutable view of the diary for the settings screen. Times are
     *  wall-clock millis; 0 means "never happened". */
    data class Snapshot(
        val createdAt: Long,
        val createdCount: Int,
        val connectedAt: Long,
        val connectedCount: Int,
        val unboundAt: Long,
        val unboundCount: Int,
        val destroyedAt: Long,
        val destroyedCount: Int,
        val interruptCount: Int,
        val errorClass: String?,
        val errorMessage: String?,
        val errorAt: Long
    )

    private fun stamp(context: Context, atKey: String, countKey: String) {
        try {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            p.edit()
                .putLong(atKey, System.currentTimeMillis())
                .putInt(countKey, p.getInt(countKey, 0) + 1)
                .apply()
        } catch (e: Exception) {
            // Diary writes must never throw into service code.
        }
    }

    fun recordCreated(context: Context) = stamp(context, KEY_CREATED_AT, KEY_CREATED_COUNT)

    fun recordConnected(context: Context) = stamp(context, KEY_CONNECTED_AT, KEY_CONNECTED_COUNT)

    fun recordUnbound(context: Context) = stamp(context, KEY_UNBOUND_AT, KEY_UNBOUND_COUNT)

    fun recordDestroyed(context: Context) = stamp(context, KEY_DESTROYED_AT, KEY_DESTROYED_COUNT)

    fun recordInterrupt(context: Context) {
        try {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            p.edit()
                .putInt(KEY_INTERRUPT_COUNT, p.getInt(KEY_INTERRUPT_COUNT, 0) + 1)
                .apply()
        } catch (e: Exception) {
            // Diary writes must never throw into service code.
        }
    }

    /** Called by EVERY catch block in [HomeInterceptService] before the
     *  exception is swallowed, so "service died quietly" becomes visible. */
    fun recordError(context: Context, e: Exception) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ERROR_CLASS, e.javaClass.simpleName)
                .putString(KEY_ERROR_MESSAGE, e.message ?: "")
                .putLong(KEY_ERROR_AT, System.currentTimeMillis())
                .apply()
        } catch (ignored: Exception) {
            // Diary writes must never throw into service code.
        }
    }

    fun snapshot(context: Context): Snapshot = try {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Snapshot(
            createdAt = p.getLong(KEY_CREATED_AT, 0L),
            createdCount = p.getInt(KEY_CREATED_COUNT, 0),
            connectedAt = p.getLong(KEY_CONNECTED_AT, 0L),
            connectedCount = p.getInt(KEY_CONNECTED_COUNT, 0),
            unboundAt = p.getLong(KEY_UNBOUND_AT, 0L),
            unboundCount = p.getInt(KEY_UNBOUND_COUNT, 0),
            destroyedAt = p.getLong(KEY_DESTROYED_AT, 0L),
            destroyedCount = p.getInt(KEY_DESTROYED_COUNT, 0),
            interruptCount = p.getInt(KEY_INTERRUPT_COUNT, 0),
            errorClass = p.getString(KEY_ERROR_CLASS, null),
            errorMessage = p.getString(KEY_ERROR_MESSAGE, null),
            errorAt = p.getLong(KEY_ERROR_AT, 0L)
        )
    } catch (e: Exception) {
        Snapshot(0L, 0, 0L, 0, 0L, 0, 0L, 0, 0, null, null, 0L)
    }
}
