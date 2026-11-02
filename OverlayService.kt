package com.c4dt.prompter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.c4dt.prompter.STOP"
        private const val CHANNEL = "c4dt_prompter"
        private const val TEAL = "#00A499"
    }

    private lateinit var wm: WindowManager
    private lateinit var panel: LinearLayout
    private lateinit var handle: TextView
    private lateinit var body: TextView
    private lateinit var controls: LinearLayout
    private lateinit var panelBg: GradientDrawable
    private lateinit var panelParams: WindowManager.LayoutParams
    private lateinit var handleParams: WindowManager.LayoutParams

    private var attached = false
    private var locked = false
    private var dimmed = false
    private var fontSp = 24f
    private var panelW = 0
    private var panelH = 0
    private var screenW = 0
    private var screenH = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val m = resources.displayMetrics
        screenW = m.widthPixels
        screenH = m.heightPixels
        panelW = screenW - dp(16)
        panelH = (screenH * 0.34f).toInt()
        buildPanel()
        buildHandle()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        val t = Store.load(this)
        body.text = if (t.isBlank())
            "No narration saved yet.\n\nOpen C4DT Prompter and paste your text."
        else t
        if (!attached) attach()
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            if (attached) {
                wm.removeView(panel)
                wm.removeView(handle)
            }
        } catch (ignored: Throwable) {
        }
        attached = false
        super.onDestroy()
    }

    private fun buildPanel() {
        panelBg = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Color.argb(212, 8, 14, 26))
            setStroke(dp(1), Color.argb(150, 0, 164, 153))
        }

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBg
            setPadding(dp(10), dp(6), dp(10), dp(10))
        }

        controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(ctl("A-") { setFont(fontSp - 2f) })
        controls.addView(ctl("A+") { setFont(fontSp + 2f) })
        controls.addView(ctl("H-") { setHeight(panelH - dp(48)) })
        controls.addView(ctl("H+") { setHeight(panelH + dp(48)) })
        controls.addView(ctl("DIM") { toggleDim() })
        controls.addView(ctl("X") { stopSelf() })
        panel.addView(controls)

        body = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp)
            setLineSpacing(0f, 1.3f)
            setPadding(dp(2), dp(6), dp(2), dp(2))
        }

        val scroll = ScrollView(this).apply {
            addView(body)
            isVerticalScrollBarEnabled = true
        }
        panel.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        panelParams = WindowManager.LayoutParams(
            panelW,
            panelH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(90)
        }
    }

    private fun buildHandle() {
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(TEAL))
            setStroke(dp(2), Color.WHITE)
        }

        handle = TextView(this).apply {
            text = "MOVE"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            background = circle
        }

        handleParams = WindowManager.LayoutParams(
            dp(58),
            dp(58),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = panelParams.x; startY = panelParams.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > dp(8) || abs(dy) > dp(8)) moved = true
                    if (moved) {
                        panelParams.x = startX + dx
                        panelParams.y = startY + dy
                        applyLayout()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleLock()
                    true
                }
                else -> false
            }
        }
    }

    private fun ctl(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(Color.parseColor(TEAL))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(11), dp(4), dp(11), dp(4))
            setOnClickListener { onClick() }
        }

    private fun attach() {
        try {
            applyLayout()
            wm.addView(panel, panelParams)
            wm.addView(handle, handleParams)
            attached = true
        } catch (t: Throwable) {
            Toast.makeText(
                this,
                "Overlay permission is missing - enable it in the app",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
        }
    }

    private fun applyLayout() {
        panelParams.width = panelW
        panelParams.height = panelH
        panelParams.x = panelParams.x.coerceIn(0, maxOf(0, screenW - panelW))
        panelParams.y = panelParams.y.coerceIn(0, maxOf(0, screenH - panelH - dp(72)))
        handleParams.x =
            (panelParams.x + panelW - dp(58)).coerceIn(0, maxOf(0, screenW - dp(58)))
        handleParams.y =
            (panelParams.y + panelH + dp(6)).coerceIn(0, maxOf(0, screenH - dp(62)))
        if (attached) {
            wm.updateViewLayout(panel, panelParams)
            wm.updateViewLayout(handle, handleParams)
        }
    }

    private fun toggleLock() {
        locked = !locked
        panelParams.flags = if (locked)
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        else
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        controls.visibility = if (locked) View.GONE else View.VISIBLE
        handle.text = if (locked) "LOCKED" else "MOVE"
        applyLayout()
    }

    private fun setFont(size: Float) {
        fontSp = size.coerceIn(14f, 44f)
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSp)
    }

    private fun setHeight(h: Int) {
        panelH = h.coerceIn(dp(120), (screenH * 0.75f).toInt())
        applyLayout()
    }

    private fun toggleDim() {
        dimmed = !dimmed
        panelBg.setColor(Color.argb(if (dimmed) 110 else 212, 8, 14, 26))
        body.setTextColor(if (dimmed) Color.parseColor("#B8C4D0") else Color.WHITE)
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL, "Teleprompter", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("C4DT Prompter is on screen")
            .setContentText("Tap to remove the panel")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(stop)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }
}
