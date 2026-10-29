package com.jarred.floatingspeedometer

import android.app.*
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.view.*
import android.widget.*
import org.prowl.torque.remote.ITorqueService
import kotlin.math.roundToInt

class SpeedOverlayService : Service(), LocationListener {

    private lateinit var wm: WindowManager
    private var overlay: LinearLayout? = null
    private var speedText: TextView? = null
    private var eotText: TextView? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var lm: LocationManager

    private val channel = "gps_speed"

    private var torqueService: ITorqueService? = null
    private var torqueBound = false
    private var eotPidId: String? = null

    private val handler = Handler(Looper.getMainLooper())

    private val torqueUpdater = object : Runnable {
        override fun run() {
            updateEot()
            handler.postDelayed(this, 500L)
        }
    }

    private val torqueConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            torqueService = ITorqueService.Stub.asInterface(binder)
            torqueBound = true
            findEotPid()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            torqueService = null
            torqueBound = false
            eotPidId = null
            eotText?.text = "EOT --°F"
        }
    }

    override fun onCreate() {
        super.onCreate()

        createChannel()
        startForeground(1, notification())

        showOverlay()

        lm = getSystemService(LOCATION_SERVICE) as LocationManager

        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                250L,
                0f,
                this
            )
        } catch (_: SecurityException) {
        }

        bindToTorque()
        handler.post(torqueUpdater)
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {

        handler.removeCallbacks(torqueUpdater)

        try {
            lm.removeUpdates(this)
        } catch (_: Exception) {
        }

        if (torqueBound) {
            try {
                unbindService(torqueConnection)
            } catch (_: Exception) {
            }
        }

        torque
