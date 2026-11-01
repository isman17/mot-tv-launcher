package com.mottv.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Home-button override (Fase 6) — the Projectivy approach, used because
 * on the user's TCL Google TV the stock launcher (launcherx) holds top
 * Home priority structurally: the Home-press chooser never appears and
 * the ACTION_HOME_SETTINGS pick does not stick.
 *
 * What this service does — and only this:
 *  1. Key filter: a KEYCODE_HOME press is consumed and answered by
 *     bringing this app's MainActivity to the front.
 *  2. Safety net: if a window-state change shows the stock home
 *     (launcherx / setupwraith) in front — some Google TV builds never
 *     deliver KEYCODE_HOME to key filters — bounce to MainActivity,
 *     hard-debounced (max 1 per 3 s, never within 1.5 s of our own
 *     launch, never for our own package).
 *
 * Powers are deliberately minimal (see res/xml/home_accessibility_config):
 * canRetrieveWindowContent is false — the service cannot read screen
 * content. Everything is wrapped in try/catch: an accessibility service
 * that crash-loops would be far worse than one that quietly does
 * nothing. When the master switch (HomeOverrideStore) is OFF, every
 * event passes through untouched. Fully reversible by the user in
 * TV Settings → Accessibility.
 */
class HomeInterceptService : AccessibilityService() {

    @Volatile
    private var lastBounceMs = 0L

    @Volatile
    private var lastSelfLaunchMs = 0L

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        try {
            if (event != null &&
                event.keyCode == KeyEvent.KEYCODE_HOME &&
                event.action == KeyEvent.ACTION_DOWN &&
                HomeOverrideStore.isOverrideOn(this)
            ) {
                launchHome()
                return true
            }
        } catch (e: Exception) {
            // Fall through to the system's own handling.
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            if (!HomeOverrideStore.isOverrideOn(this)) return
            val pkg = event.packageName?.toString() ?: return
            if (pkg == packageName) return
            if (pkg != STOCK_HOME_PACKAGE && pkg != SETUP_PACKAGE) return
            val now = SystemClock.uptimeMillis()
            if (now - lastBounceMs < BOUNCE_MIN_INTERVAL_MS) return
            if (now - lastSelfLaunchMs < SELF_LAUNCH_GUARD_MS) return
            lastBounceMs = now
            launchHome()
        } catch (e: Exception) {
            // Safety net must never crash the service.
        }
    }

    override fun onInterrupt() {
        // Nothing to interrupt — the service renders no feedback.
    }

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            // Grace window right after (re)connect so a stale stock-home
            // window event can't bounce the user while they are still
            // sitting in the accessibility settings screen.
            lastSelfLaunchMs = SystemClock.uptimeMillis()
        } catch (e: Exception) {
            // no-op
        }
    }

    /** Bring our own Beranda to the front. Timestamp is set BEFORE the
     *  start so window events racing our own launch are recognised as
     *  self-inflicted by the safety-net guard. */
    private fun launchHome() {
        try {
            lastSelfLaunchMs = SystemClock.uptimeMillis()
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(packageName, MainActivity::class.java.name)
                addCategory(Intent.CATEGORY_HOME)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            // If Android refuses the start, stay silent — the next Home
            // press retries, and the stock home keeps working meanwhile.
        }
    }

    companion object {
        private const val STOCK_HOME_PACKAGE = "com.google.android.apps.tv.launcherx"
        private const val SETUP_PACKAGE = "com.google.android.tungsten.setupwraith"
        private const val BOUNCE_MIN_INTERVAL_MS = 3_000L
        private const val SELF_LAUNCH_GUARD_MS = 1_500L
    }
}
