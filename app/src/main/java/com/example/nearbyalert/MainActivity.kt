package com.example.nearbyalert

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.events.MapEventsReceiver
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private lateinit var status: TextView
    private lateinit var destinationsView: LinearLayout
    private val requestCode = 501

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = "NearbyAlert/1.0 (com.example.nearbyalert)"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 8)
        }
        root.addView(TextView(this).apply {
            text = "근처 알림"; textSize = 24f; setTextColor(Color.rgb(25,35,52))
        })
        status = TextView(this).apply { text = "지도 길게 누르기로 목적지를 추가하세요."; setPadding(0,8,0,8) }
        root.addView(status)
        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(12.5)
            controller.setCenter(GeoPoint(37.5665,126.9780))
        }
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?) = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) addDestination(p)
                return true
            }
        }))
        root.addView(map, LinearLayout.LayoutParams(-1, 0, 1f))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply { text = "현재 위치"; setOnClickListener { showCurrentLocation() } }, LinearLayout.LayoutParams(0,-2,1f))
        buttons.addView(Button(this).apply { text = "감지 시작"; setOnClickListener { startTracking() } }, LinearLayout.LayoutParams(0,-2,1f))
        buttons.addView(Button(this).apply { text = "전체 중지"; setOnClickListener {
            DestinationStore.prefs(this@MainActivity).edit().putBoolean("tracking_active", false).apply()
            stopService(Intent(this@MainActivity, LocationTrackingService::class.java))
            status.text = "위치 감지를 중지했습니다."
        } }, LinearLayout.LayoutParams(0,-2,1f))
        root.addView(buttons)
        root.addView(TextView(this).apply { text = "등록한 목적지"; textSize = 18f; setPadding(0,8,0,4) })
        destinationsView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(destinationsView) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(160)))
        setContentView(root)
        requestBasicPermissions()
        renderDestinations()
        handleDestinationIntent(intent)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDestinationIntent(intent)
    }
    private fun handleDestinationIntent(intent: Intent?) {
        val id = intent?.getStringExtra("destination_id") ?: return
        val d = DestinationStore.get(this).firstOrNull { it.id == id }
        if (d != null) {
            val point = GeoPoint(d.latitude, d.longitude)
            map.controller.setZoom(15.5)
            map.controller.animateTo(point)
            status.text = "알림 목적지: ${d.name} · 반경 ${d.radiusMeters.toInt()}m"
            intent.removeExtra("destination_id")
        }
    }
    private fun requestBasicPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), requestCode)
    }
    private fun addDestination(point: GeoPoint) {
        val input = EditText(this).apply { hint = "목적지 이름"; setSingleLine(true) }
        val radiusOptions = arrayOf("100m", "300m", "500m", "1km", "2km", "5km")
        val radiusValues = intArrayOf(100,300,500,1000,2000,5000)
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, radiusOptions)
            setSelection(3)
        }
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,8,24,0); addView(input); addView(spinner) }
        AlertDialog.Builder(this).setTitle("목적지 추가").setView(panel)
            .setNegativeButton("취소", null)
            .setPositiveButton("추가") { _, _ ->
                val items = DestinationStore.get(this)
                items.add(Destination(UUID.randomUUID().toString(), input.text.toString().trim().ifBlank { "목적지 ${items.size+1}" }, point.latitude, point.longitude, radiusValues[spinner.selectedItemPosition].toFloat()))
                DestinationStore.save(this, items)
                renderDestinations()
                refreshService()
            }.show()
    }
    private fun renderDestinations() {
        if (!::map.isInitialized || !::destinationsView.isInitialized) return
        destinationsView.removeAllViews()
        map.overlays.removeAll { it is Marker || it is Polygon }
        DestinationStore.get(this).forEach { d ->
            val point = GeoPoint(d.latitude, d.longitude)
            map.overlays.add(Marker(map).apply { position = point; title = d.name; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) })
            map.overlays.add(Polygon(map).apply {
                points = Polygon.pointsAsCircle(point, d.radiusMeters.toDouble())
                fillPaint.color = if (d.enabled) Color.argb(28,31,91,217) else Color.argb(12,120,120,120)
                outlinePaint.color = if (d.enabled) Color.rgb(31,91,217) else Color.GRAY
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = "${d.name} · 반경 ${d.radiusMeters.toInt()}m"; textSize = 14f }, LinearLayout.LayoutParams(0,-2,1f))
            row.addView(Button(this).apply { text = if (d.enabled) "ON" else "OFF"; setOnClickListener { updateDestination(d.copy(enabled = !d.enabled)) } })
            row.addView(Button(this).apply { text = "지도"; setOnClickListener { map.controller.setZoom(15.5); map.controller.animateTo(point) } })
            row.addView(Button(this).apply { text = "삭제"; setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setMessage("${d.name}을(를) 삭제할까요?")
                    .setNegativeButton("취소",null).setPositiveButton("삭제") { _, _ ->
                        DestinationStore.save(this@MainActivity, DestinationStore.get(this@MainActivity).filterNot { it.id == d.id })
                        renderDestinations(); refreshService()
                    }.show()
            } })
            destinationsView.addView(row)
        }
        if (DestinationStore.get(this).isEmpty()) destinationsView.addView(TextView(this).apply { text = "아직 목적지가 없습니다. 지도를 길게 눌러 추가하세요." })
        map.invalidate()
    }
    private fun updateDestination(d: Destination) {
        val items = DestinationStore.get(this)
        val index = items.indexOfFirst { it.id == d.id }
        if (index >= 0) items[index] = d
        DestinationStore.save(this, items)
        renderDestinations()
        refreshService()
    }
    private fun refreshService() {
        try { startService(Intent(this, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_DESTINATIONS_CHANGED)) } catch (_: Exception) {}
    }
    private fun showCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestBasicPermissions(); return
        }
        LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    val point = GeoPoint(loc.latitude, loc.longitude)
                    map.controller.setZoom(15.5); map.controller.animateTo(point)
                    status.text = "현재 위치가 갱신되었습니다."
                } else status.text = "현재 위치를 확인하지 못했습니다. 위치 서비스를 확인하세요."
            }
    }
    private fun startTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestBasicPermissions(); status.text = "정확한 위치 권한을 허용하세요."; return
        }
        if (DestinationStore.get(this).none { it.enabled }) {
            status.text = "먼저 활성화된 목적지를 하나 이상 등록하세요."; return
        }
        if (Build.VERSION.SDK_INT >= 30 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this).setTitle("백그라운드 위치 권한")
                .setMessage("앱 정보 > 권한 > 위치에서 '항상 허용'을 선택해야 백그라운드 감지가 가능합니다.")
                .setPositiveButton("설정 열기") { _, _ -> startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }
                .setNegativeButton("취소", null).show()
            return
        }
        DestinationStore.prefs(this).edit().putBoolean("tracking_active", true).apply()
        ContextCompat.startForegroundService(this, Intent(this, LocationTrackingService::class.java))
        status.text = "위치 감지를 시작했습니다."
    }
    override fun onResume() { super.onResume(); if (::map.isInitialized) map.onResume(); renderDestinations() }
    override fun onPause() { if (::map.isInitialized) map.onPause(); super.onPause() }
    override fun onDestroy() { if (::map.isInitialized) map.onDetach(); super.onDestroy() }
}
