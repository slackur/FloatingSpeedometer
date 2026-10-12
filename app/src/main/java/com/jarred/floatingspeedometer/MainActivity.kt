package com.jarred.floatingspeedometer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private val reqLocation = 7
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48,48,48,48); setBackgroundColor(Color.WHITE)
        }
        root.addView(TextView(this).apply { text="Floating GPS Speedometer"; textSize=28f; setTextColor(Color.BLACK); gravity=Gravity.CENTER })
        root.addView(TextView(this).apply { text="Big GPS MPH over Google Maps\n\n1. Allow location\n2. Allow display over other apps\n3. Tap START, then open Google Maps"; textSize=18f; setTextColor(Color.DKGRAY); gravity=Gravity.CENTER; setPadding(0,36,0,36) })
        val start=Button(this).apply { text="START SPEEDOMETER"; textSize=18f }
        val stop=Button(this).apply { text="STOP"; textSize=18f }
        root.addView(start, LinearLayout.LayoutParams(-1,-2)); root.addView(stop, LinearLayout.LayoutParams(-1,-2).apply{topMargin=20})
        setContentView(root)
        start.setOnClickListener { ensurePermissionsAndStart() }
        stop.setOnClickListener { stopService(Intent(this, SpeedOverlayService::class.java)) }
    }
    private fun ensurePermissionsAndStart(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){ requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),reqLocation); return }
        if(!Settings.canDrawOverlays(this)){
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); Toast.makeText(this,"Enable 'Display over other apps', then return and tap START",Toast.LENGTH_LONG).show(); return
        }
        startForegroundService(Intent(this,SpeedOverlayService::class.java)); Toast.makeText(this,"Speedometer running — open Google Maps",Toast.LENGTH_SHORT).show()
    }
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){ super.onRequestPermissionsResult(r,p,g); if(r==reqLocation && g.firstOrNull()==PackageManager.PERMISSION_GRANTED) ensurePermissionsAndStart() }
}
