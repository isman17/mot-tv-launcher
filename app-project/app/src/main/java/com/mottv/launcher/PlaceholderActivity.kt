package com.mottv.launcher

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Generic honest-placeholder screen used by Settings menu items not yet fully built. */
class PlaceholderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_BUTTON = "button_label"
        const val EXTRA_TARGET = "button_target" // "sholat" supported in v1
        const val EXTRA_SHOW_ICON = "show_icon" // Tentang screen shows the app logo
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)
        BackgroundStore.applyTo(this, findViewById(R.id.bgImage), findViewById(R.id.bgOverlay))

        if (intent.getBooleanExtra(EXTRA_SHOW_ICON, false)) {
            findViewById<ImageView>(R.id.msgIcon).visibility = View.VISIBLE
        }

        findViewById<TextView>(R.id.msgTitle).text = intent.getStringExtra(EXTRA_TITLE) ?: ""
        findViewById<TextView>(R.id.msgBody).text = intent.getStringExtra(EXTRA_BODY) ?: ""

        val buttonLabel = intent.getStringExtra(EXTRA_BUTTON)
        val button = findViewById<TextView>(R.id.msgButton)
        if (!buttonLabel.isNullOrEmpty()) {
            button.visibility = View.VISIBLE
            button.text = buttonLabel
            button.setOnClickListener {
                when (intent.getStringExtra(EXTRA_TARGET)) {
                    "sholat" -> startActivity(Intent(this, SholatActivity::class.java))
                }
            }
            button.requestFocus()
        }
    }
}
