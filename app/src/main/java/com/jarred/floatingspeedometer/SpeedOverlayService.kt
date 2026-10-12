package com.jarred.floatingspeedometer

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.view.*
import android.widget.*
import kotlin.math.roundToInt

class SpeedOverlayService : Service(), LocationListener {
    private lateinit var wm: WindowManager
    private var overlay: LinearLayout? = null
    private var speedText: TextView? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var lm: LocationManager
    private val channel = "gps_speed"

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, notification())
        showOverlay()
        lm = getSystemService(LOCATION_SERVICE) as LocationManager
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 250L, 0f, this) } catch (_: SecurityException) {}
    }

    override fun onBind(i: Intent?) = null

    override fun onDestroy() {
        try { lm.removeUpdates(this) } catch (_: Exception) {}
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        stopForeground(true)
        super.onDestroy()
    }

    override fun onLocationChanged(l: Location) {
        val mph = if (l.hasSpeed()) (l.speed * 2.23693629).roundToInt().coerceAtLeast(0) else 0
        speedText?.text = mph.toString()
    }

    private fun showOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(R.drawable.overlay_bg)
            setPadding(22, 8, 8, 8)
        }

        val n = TextView(this).apply {
            text = "0"
            textSize = 44f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(4, 0, 12, 0)
        }

        val close = TextView(this).apply {
            text = "×"
            textSize = 28f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(12, 4, 12, 4)
            contentDescription = "Close speedometer"
            setOnClickListener { stopSelf() }
        }

        box.addView(n)
        box.addView(close)
        speedText = n
        overlay = box

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 180
        }

        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0

        // Drag using the speed number; X remains an independent one-tap close button.
        n.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; ox = params.x; oy = params.y; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = ox + (e.rawX - sx).toInt()
                    params.y = oy + (e.rawY - sy).toInt()
                    wm.updateViewLayout(box, params)
                    true
                }
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }

        wm.addView(box, params)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel(channel, "GPS Speedometer", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, SpeedOverlayService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, channel)
            .setContentTitle("GPS Speedometer running")
            .setContentText("Floating speed is active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stop)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }
}
