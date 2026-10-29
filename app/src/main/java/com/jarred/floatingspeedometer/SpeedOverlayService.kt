package com.jarred.floatingspeedometer

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.location.*
import android.os.*
import android.view.*
import android.widget.*
import org.prowl.torque.remote.ITorqueService
import kotlin.math.roundToInt

class SpeedOverlayService : Service(), LocationListener {
    private lateinit var wm: WindowManager
    private lateinit var lm: LocationManager
    private lateinit var params: WindowManager.LayoutParams
    private var overlay: LinearLayout? = null
    private var speedText: TextView? = null
    private var eotText: TextView? = null
    private var torque: ITorqueService? = null
    private var torqueBound = false
    private var eotPid: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val channel = "gps_speed"

    private val updater = object : Runnable {
        override fun run() {
            updateEot()
            handler.postDelayed(this, 1000)
        }
    }

    private val torqueConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            torque = ITorqueService.Stub.asInterface(binder)
            torqueBound = true
            findEot()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            torque = null
            torqueBound = false
            eotPid = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, notification())
        showOverlay()

        lm = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 250L, 0f, this)
        } catch (_: SecurityException) {}

        try {
            val i = Intent().setClassName(
                "org.prowl.torque",
                "org.prowl.torque.remote.TorqueService"
            )
            torqueBound = bindService(i, torqueConnection, BIND_AUTO_CREATE)
        } catch (_: Exception) {}

        handler.post(updater)
    }

    override fun onBind(intent: Intent?) = null

    override fun onLocationChanged(location: Location) {
        val mph = if (location.hasSpeed())
            (location.speed * 2.23693629).roundToInt().coerceAtLeast(0)
        else 0

        speedText?.text = mph.toString()
    }

    private fun findEot() {
        try {
            val t = torque ?: return
            for (pid in t.listAllPIDs()) {
                val info = t.getPIDInformation(arrayOf(pid))
                if (info.isNotEmpty() &&
                    (info[0].contains("Engine Oil Temp", true) ||
                     info[0].contains("EOT", true))) {
                    eotPid = pid
                    return
                }
            }
        } catch (_: Exception) {}
    }

    private fun updateEot() {
        try {
            val t = torque ?: return
            if (!t.isConnectedToECU()) {
                eotText?.text = "EOT --°F"
                return
            }

            if (eotPid == null) findEot()
            val pid = eotPid ?: return
            val values = t.getPIDValues(arrayOf(pid))

            if (values.isNotEmpty() && !values[0].isNaN()) {
                val f = values[0] * 9f / 5f + 32f
                eotText?.text = "EOT ${f.roundToInt()}°F"
            }
        } catch (_: Exception) {
            eotText?.text = "EOT --°F"
        }
    }

    private fun showOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(R.drawable.overlay_bg)
            setPadding(22, 8, 8, 8)
        }

        val speed = TextView(this).apply {
            text = "0"
            textSize = 44f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(4, 0, 12, 0)
        }

        val eot = TextView(this).apply {
            text = "EOT --°F"
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(8, 0, 12, 0)
        }

        val close = TextView(this).apply {
            text = "×"
            textSize = 28f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(12, 4, 12, 4)
            setOnClickListener { stopSelf() }
        }

        box.addView(speed)
        box.addView(eot)
        box.addView(close)

        speedText = speed
        eotText = eot
        overlay = box

        params = WindowManager.LayoutParams(
            -2, -2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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

        speed.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX
                    sy = e.rawY
                    ox = params.x
                    oy = params.y
                    true
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
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(channel, "GPS Speedometer", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(): Notification {
        return Notification.Builder(this, channel)
            .setContentTitle("GPS Speedometer running")
            .setContentText("Floating speed is active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    override fun onDestroy() {
    handler.removeCallbacks(updater)

    try {
        lm.removeUpdates(this)
    } catch (_: Exception) {
    }

    if (torqueBound) {
        try {
            unbindService(torqueConnection)
        } catch (_: Exception) {
        }
        torqueBound = false
    }

    overlay?.let {
        try {
            wm.removeView(it)
        } catch (_: Exception) {
        }
    }

    stopForeground(true)
    super.onDestroy()
}
}
