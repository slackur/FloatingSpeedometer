package com.jarred.floatingspeedometer

import android.app.*
import android.content.Context
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
    private var overlay: LinearLayout?=null
    private var speedText: TextView?=null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var lm: LocationManager
    private val channel="gps_speed"
    override fun onCreate(){ super.onCreate(); createChannel(); startForeground(1,notification()); showOverlay(); lm=getSystemService(LOCATION_SERVICE) as LocationManager
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,250L,0f,this) } catch(_:SecurityException){} }
    override fun onBind(i:Intent?)=null
    override fun onDestroy(){ try{lm.removeUpdates(this)}catch(_:Exception){}; overlay?.let{try{wm.removeView(it)}catch(_:Exception){}}; super.onDestroy() }
    override fun onLocationChanged(l:Location){ val mph=if(l.hasSpeed()) (l.speed*2.23693629).roundToInt().coerceAtLeast(0) else 0; speedText?.text=mph.toString() }
    private fun showOverlay(){
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=getDrawable(com.jarred.floatingspeedometer.R.drawable.overlay_bg);setPadding(28,10,28,10)}
        val n=TextView(this).apply{text="0";textSize=44f;setTextColor(android.graphics.Color.WHITE);gravity=Gravity.CENTER;includeFontPadding=false}
        val unit=TextView(this).apply{text="MPH";textSize=12f;setTextColor(android.graphics.Color.LTGRAY);gravity=Gravity.CENTER;includeFontPadding=false}
        box.addView(n);box.addView(unit);speedText=n;overlay=box
        params=WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;x=30;y=180}
        var sx=0f;var sy=0f;var ox=0;var oy=0
        box.setOnTouchListener{_,e-> when(e.action){MotionEvent.ACTION_DOWN->{sx=e.rawX;sy=e.rawY;ox=params.x;oy=params.y;true};MotionEvent.ACTION_MOVE->{params.x=ox+(e.rawX-sx).toInt();params.y=oy+(e.rawY-sy).toInt();wm.updateViewLayout(box,params);true};else->false}}
        wm.addView(box,params)
    }
    private fun createChannel(){ if(Build.VERSION.SDK_INT>=26)(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(channel,"GPS Speedometer",NotificationManager.IMPORTANCE_LOW)) }
    private fun notification():Notification{ val stop=PendingIntent.getService(this,2,Intent(this,SpeedOverlayService::class.java).apply{action="STOP"},PendingIntent.FLAG_IMMUTABLE); return Notification.Builder(this,channel).setContentTitle("GPS Speedometer running").setContentText("Floating MPH is active").setSmallIcon(android.R.drawable.ic_menu_mylocation).build() }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int { if(intent?.action=="STOP")stopSelf(); return START_STICKY }
}
