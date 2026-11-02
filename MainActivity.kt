package com.c4dt.prompter

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

internal fun Context.dp(value: Int): Int = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
).toInt()

object Store {
    private const val PREF = "c4dt_prompter"
    private const val KEY = "script"

    fun save(c: Context, s: String) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, s).apply()
    }

    fun load(c: Context): String =
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
}

class MainActivity : Activity() {

    private lateinit var editor: EditText
    private lateinit var permButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "C4DT Prompter"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val steps = TextView(this).apply {
            text = "1   Paste or share your narration below\n" +
                   "2   Allow \"Display over other apps\"\n" +
                   "3   Tap START, then switch to Smule\n" +
                   "4   Tap the teal handle to LOCK - when locked, your\n" +
                   "     taps pass straight through to Smule\n" +
                   "5   Drag the handle to move the panel anywhere"
            setTextColor(Color.parseColor("#4A4A4A"))
            setPadding(0, 0, 0, dp(14))
        }
        root.addView(steps)

        editor = EditText(this).apply {
            gravity = Gravity.TOP or Gravity.START
            setHint("Paste your narration here...")
            setText(Store.load(this@MainActivity))
        }
        root.addView(
            editor,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(button("PASTE") { pasteFromClipboard() }, equalWeight())
        row1.addView(button("CLEAR") { editor.setText("") }, equalWeight())
        root.addView(row1)

        permButton = button("ALLOW OVERLAY") { requestOverlay() }
        root.addView(
            permButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button("START") { start() }, equalWeight())
        row2.addView(button("STOP") { stop() }, equalWeight())
        root.addView(row2)

        setContentView(root)
        handleShare(intent)
        askNotifications()
    }

    override fun onResume() {
        super.onResume()
        val ok = Settings.canDrawOverlays(this)
        permButton.text = if (ok) "OVERLAY ALLOWED" else "ALLOW OVERLAY"
        permButton.isEnabled = !ok
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND && i.type == "text/plain") {
            val t = i.getStringExtra(Intent.EXTRA_TEXT)
            if (!t.isNullOrBlank()) {
                editor.setText(t)
                Store.save(this, t)
                toast("Narration received")
            }
        }
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val t = clip.getItemAt(0).coerceToText(this).toString()
            editor.setText(t)
            Store.save(this, t)
            toast("Pasted " + t.length + " characters")
        } else {
            toast("Clipboard is empty")
        }
    }

    private fun requestOverlay() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)
            )
        )
    }

    private fun start() {
        Store.save(this, editor.text.toString())
        if (!Settings.canDrawOverlays(this)) {
            toast("Allow \"Display over other apps\" first")
            requestOverlay()
            return
        }
        val i = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        toast("Prompter is on screen - switch to Smule")
        moveTaskToBack(true)
    }

    private fun stop() {
        stopService(Intent(this, OverlayService::class.java))
        toast("Prompter stopped")
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

    private fun equalWeight(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
