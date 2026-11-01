package com.mottv.launcher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewParent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

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
        AlertDialog.Builder(activity)
            .setTitle(label)
            .setItems(items.toTypedArray()) { _, which -> actions[which]() }
            .show()
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
