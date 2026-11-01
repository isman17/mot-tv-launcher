package com.mottv.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewParent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/**
 * Long-press app menu (phase 5): the same small dialog on Home tiles,
 * the Games grid and the All Apps grid — "Buka", "Info Aplikasi"
 * (system app-details page), "Copot Pemasangan" (system uninstall).
 * Items that make no sense are hidden, honestly:
 *  - "Copot Pemasangan" is hidden for our own package (the launcher
 *    itself) and for the Play Store (com.android.vending) — losing
 *    either would strand the user.
 *  - System apps still go through ACTION_DELETE, where the system
 *    itself decides (it offers "disable" instead of uninstall there).
 *
 * The remote's MENU key triggers the same menu on the focused card:
 * [bind] stores the show-action in the view's tag, and the host
 * activity forwards key events to [handleMenuKey].
 */
object AppActions {

    /** Everything the menu needs to know about one tile/card. */
    class Spec(
        val activity: Activity,
        val label: String,
        val packageName: String,
        val open: () -> Unit
    ) {
        fun show() = showMenu(activity, label, packageName, open)
    }

    /** Attaches the long-press menu to [view]. Call again on rebind. */
    fun bind(view: View, activity: Activity, label: String, packageName: String, open: () -> Unit) {
        val spec = Spec(activity, label, packageName, open)
        view.setTag(R.id.app_menu_action, spec)
        view.setOnLongClickListener {
            spec.show()
            true // consume: the normal click must NOT also fire
        }
    }

    /** Removes a previously bound menu (RecyclerView rebind safety). */
    fun unbind(view: View) {
        view.setTag(R.id.app_menu_action, null)
        view.setOnLongClickListener(null)
    }

    /**
     * KEYCODE_MENU support: the host activity calls this from
     * dispatchKeyEvent; when the focused view (or one of its parents)
     * carries a bound menu, the menu opens and the event is consumed.
     */
    fun handleMenuKey(activity: Activity, event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_MENU || event.action != KeyEvent.ACTION_UP) {
            return false
        }
        var v: View? = activity.currentFocus
        while (v != null) {
            val spec = v.getTag(R.id.app_menu_action) as? Spec
            if (spec != null) {
                spec.show()
                return true
            }
            val parent: ViewParent? = v.parent
            v = parent as? View
        }
        return false
    }

    fun showMenu(activity: Activity, label: String, packageName: String, open: () -> Unit) {
        val items = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        items.add("Buka")
        actions.add(open)
        items.add("Info Aplikasi")
        actions.add { openAppInfo(activity, packageName) }
        if (packageName != activity.packageName && packageName != "com.android.vending") {
            items.add("Copot Pemasangan")
            actions.add { requestUninstall(activity, packageName, label) }
        }

        // Fase 6.1: NOT the stock AlertDialog list. The app theme is a
        // light AppCompat theme, so setItems rendered a pale list whose
        // focus highlight was nearly invisible on the TV. The menu is now
        // a custom dark panel: own title + one focusable row per action,
        // each row carrying the shared mot_focus_row selector, so D-pad
        // focus shows the same brighten + white stroke as everywhere
        // else in the app. First row takes focus on open.
        fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
        }
        container.addView(TextView(activity).apply {
            text = label
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(4), 0, dp(4), dp(12))
        })
        val dialogHolder = arrayOfNulls<AlertDialog>(1)
        var firstRow: TextView? = null
        items.forEachIndexed { i, text ->
            val row = TextView(activity).apply {
                this.text = text
                textSize = 16f
                setTextColor(Color.WHITE)
                background = ContextCompat.getDrawable(activity, R.drawable.mot_focus_row)
                isFocusable = true
                isClickable = true
                setPadding(dp(16), dp(12), dp(16), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (i > 0) topMargin = dp(6) }
                focusFx(1.01f)
                setOnClickListener {
                    dialogHolder[0]?.dismiss()
                    actions[i]()
                }
            }
            if (firstRow == null) firstRow = row
            container.addView(row)
        }
        val dialog = AlertDialog.Builder(activity)
            .setView(container)
            .create()
        dialogHolder[0] = dialog
        dialog.show()
        // The window background must be replaced AFTER show() (the
        // AppCompat panel drawable would otherwise frame the dark panel
        // in light grey).
        dialog.window?.setBackgroundDrawable(
            ContextCompat.getDrawable(activity, R.drawable.mot_dialog_bg)
        )
        firstRow?.requestFocus()
    }

    private fun openAppInfo(activity: Activity, packageName: String) {
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            Toast.makeText(
                activity, "Info aplikasi tidak bisa dibuka dari sini", Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun requestUninstall(activity: Activity, packageName: String, label: String) {
        try {
            @Suppress("DEPRECATION")
            activity.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            Toast.makeText(
                activity, "$label tidak bisa dicopot dari sini", Toast.LENGTH_SHORT
            ).show()
        }
    }
}
