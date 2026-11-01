package com.mottv.launcher

import android.view.View

/**
 * Shared D-pad focus feedback (fase 6.1).
 *
 * Backgrounds are NOT handled here — every focusable control's background
 * is one of the `mot_focus_*` selector drawables, whose default state is
 * pixel-identical to the pre-6.1 look (pill_bg / pill_gold / pill_on /
 * menu_item) and whose focused state adds a bright fill + 3dp stroke.
 * This helper adds the motion half: a quick scale-up + lift while the
 * view holds focus, so the focused control also moves.
 *
 * It installs the view's single OnFocusChangeListener slot, which is why
 * the old per-screen listeners that swapped backgrounds to pill_on were
 * removed in 6.1 — the selectors replaced them. Do NOT call this on
 * full-width rows (settings/result/match rows): a match_parent row
 * scaling would push past the screen edges; rows rely on the selector's
 * stroke + brighten alone. Buttons/pills/chips/keys use 1.04–1.06.
 *
 * translationZ is used instead of bringToFront() on purpose:
 * bringToFront reorders children inside LinearLayout parents, which
 * would visibly reshuffle rows; translationZ only changes draw order.
 */
fun View.focusFx(scale: Float = 1.04f) {
    val lift = 8f * resources.displayMetrics.density
    setOnFocusChangeListener { v, hasFocus ->
        v.animate().cancel()
        if (hasFocus) {
            v.animate()
                .scaleX(scale).scaleY(scale)
                .translationZ(lift)
                .setDuration(120)
                .start()
        } else {
            v.animate()
                .scaleX(1f).scaleY(1f)
                .translationZ(0f)
                .setDuration(120)
                .start()
        }
    }
}
