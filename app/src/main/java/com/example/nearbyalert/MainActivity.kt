package com.example.nearbyalert

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import android.location.Location
import org.osmdroid.config.Configuration
import org.json.JSONArray
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.events.MapEventsReceiver
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private lateinit var status: TextView
    private lateinit var destinationsView: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var distanceView: TextView
    private lateinit var trackingToggle: Button
    private var currentMarker: Marker? = null
    private var lastKnownLocation: Location? = null
    private lateinit var fusedLocation: FusedLocationProviderClient
    private var locationUpdatesStarted = false
    private val requestCode = 501

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location -> updateLiveLocation(location) }
        }
    }

    private data class SearchPlace(val name: String, val latitude: Double, val longitude: Double)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = "NearbyAlert/1.0 (com.example.nearbyalert)"
        fusedLocation = LocationServices.getFusedLocationProviderClient(this)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        val root = LinearLayout(this).apply {
            isFocusableInTouchMode = true
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            setBackgroundColor(Color.rgb(247, 249, 252))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "Nearby Alert"
                textSize = 25f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(20, 38, 64))
            })
            addView(TextView(this@MainActivity).apply {
                text = "내 주변의 중요한 장소를 놓치지 마세요"
                textSize = 13f
                setTextColor(Color.rgb(105, 120, 142))
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "● GPS"
            textSize = 13f
            setTextColor(Color.rgb(22, 132, 90))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedBackground(Color.rgb(226, 248, 239), dp(18))
        })
        root.addView(header)

        status = TextView(this).apply {
            text = "장소를 검색하거나 지도를 길게 눌러 목적지를 추가하세요."
            textSize = 14f
            setTextColor(Color.rgb(78, 94, 115))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val searchCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(8), 0)
            background = roundedBackground(Color.WHITE, dp(16))
            elevation = dp(2).toFloat()
        }
        searchInput = EditText(this).apply {
            hint = "장소·주소 검색 (예: 평촌역)"
            textSize = 16f
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(8), 0, dp(4), 0)
            background = null
            setTextColor(Color.rgb(28, 42, 61))
        }
        searchCard.addView(TextView(this).apply { text = "⌕"; textSize = 28f; setTextColor(Color.rgb(35, 113, 239)) })
        searchCard.addView(searchInput, LinearLayout.LayoutParams(0, dp(54), 1f))
        searchButton = Button(this).apply {
            text = "검색"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.rgb(27, 115, 239), dp(12))
            setOnClickListener { searchPlaces() }
        }
        searchCard.addView(searchButton, LinearLayout.LayoutParams(dp(72), dp(42)))
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { searchPlaces(); true } else false
        }
        root.addView(searchCard)

        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setTilesScaledToDpi(true)
            controller.setZoom(13.5)
            controller.setCenter(GeoPoint(37.5665,126.9780))
        }
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?) = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) addDestination(p)
                return true
            }
        }))
        val mapFrame = FrameLayout(this).apply {
            background = roundedBackground(Color.WHITE, dp(20))
            clipToOutline = true
            elevation = dp(3).toFloat()
            addView(map, FrameLayout.LayoutParams(-1, -1))
        }
        val mapLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mapLayout.addView(mapFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(mapLayout, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(10)
            bottomMargin = dp(8)
        })

        distanceView = TextView(this).apply {
            text = "현재 위치를 확인하는 중…"
            textSize = 15f
            setTextColor(Color.rgb(28, 50, 80))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBackground(Color.WHITE, dp(16))
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        val distanceTitle = TextView(this).apply {
            text = "활성 목적지까지의 거리"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(27, 45, 70))
            setPadding(dp(4), dp(2), dp(4), dp(5))
        }
        val distancePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(distanceTitle)
            addView(ScrollView(this@MainActivity).apply {
                isFillViewport = false
                addView(distanceView)
            }, LinearLayout.LayoutParams(-1, dp(86)))
        }
        root.addView(distancePanel, LinearLayout.LayoutParams(-1, dp(120)))

        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(2))
        }
        trackingToggle = Button(this).apply {
            textSize = 13f
            isAllCaps = false
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener { toggleTracking() }
        }
        bottomPanel.addView(trackingToggle, LinearLayout.LayoutParams(0, dp(52), 0.82f).apply { rightMargin = dp(6) })
        bottomPanel.addView(Button(this).apply {
            text = "◎ 현재 위치"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.rgb(25, 116, 239), dp(15))
            setOnClickListener { showCurrentLocation() }
        }, LinearLayout.LayoutParams(0, dp(52), 1.05f).apply { rightMargin = dp(6) })
        bottomPanel.addView(Button(this).apply {
            text = "등록된 위치  ›"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.rgb(30, 76, 132))
            background = roundedBackground(Color.rgb(229, 240, 255), dp(15))
            setOnClickListener { startActivity(Intent(this@MainActivity, RegisteredLocationsActivity::class.java)) }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(bottomPanel)
        updateTrackingToggle()

        destinationsView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(root)
        root.requestFocus()
        searchInput.clearFocus()
        requestBasicPermissions()
        renderDestinations()
        handleDestinationIntent(intent)
    }

    private fun roundedBackground(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun smallActionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.rgb(47, 67, 91))
        background = roundedBackground(Color.WHITE, dp(12))
        setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun searchPlaces() {
        val query = searchInput.text.toString().trim()
        if (query.isBlank()) {
            searchInput.error = "검색할 장소나 주소를 입력하세요."
            return
        }
        searchButton.isEnabled = false
        status.text = "장소를 검색하고 있어요…"
        val encoded = URLEncoder.encode(query, "UTF-8")
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=jsonv2&limit=7&accept-language=ko")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "NearbyAlert/1.0 (Android location reminder app)")
                    setRequestProperty("Accept-Language", "ko")
                }
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONArray(response)
                val places = (0 until json.length()).mapNotNull { i ->
                    val item = json.optJSONObject(i) ?: return@mapNotNull null
                    val lat = item.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                    val lon = item.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                    SearchPlace(item.optString("display_name", query), lat, lon)
                }
                runOnUiThread {
                    searchButton.isEnabled = true
                    if (places.isEmpty()) {
                        status.text = "검색 결과가 없어요. 다른 검색어를 입력해 보세요."
                    } else {
                        status.text = "${places.size}개의 검색 결과를 찾았어요."
                        showSearchResults(places)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    searchButton.isEnabled = true
                    status.text = "검색에 실패했어요. 인터넷 연결을 확인하고 다시 시도해 주세요."
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun showSearchResults(places: List<SearchPlace>) {
        val labels = places.map { it.name }
        AlertDialog.Builder(this)
            .setTitle("검색 결과")
            .setItems(labels.toTypedArray()) { _, which ->
                val place = places[which]
                val point = GeoPoint(place.latitude, place.longitude)
                map.controller.setZoom(16.0)
                map.controller.animateTo(point)
                status.text = place.name
                AlertDialog.Builder(this)
                    .setTitle("이 장소를 목적지로 추가할까요?")
                    .setMessage(place.name)
                    .setNegativeButton("아니요", null)
                    .setPositiveButton("목적지 추가") { _, _ -> addDestination(point, place.name.substringBefore(",")) }
                    .show()
            }
            .setNegativeButton("닫기", null)
            .show()
    }

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
            map.controller.setZoom(16.0)
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

    private fun addDestination(point: GeoPoint, suggestedName: String? = null) {
        val input = EditText(this).apply {
            hint = "목적지 이름"
            setSingleLine(true)
            if (!suggestedName.isNullOrBlank()) setText(suggestedName)
        }
        val radiusInput = EditText(this).apply {
            hint = "알림 반경(미터, 예: 750)"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("1000")
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input)
            addView(TextView(this@MainActivity).apply {
                text = "이 거리 안으로 들어오면 알림을 보내요."
                textSize = 12f
                setTextColor(Color.rgb(100, 115, 135))
                setPadding(0, dp(4), 0, 0)
            })
            addView(radiusInput)
        }
        val dialog = AlertDialog.Builder(this).setTitle("목적지 추가").setView(panel)
            .setNegativeButton("취소", null)
            .setPositiveButton("추가", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val radius = radiusInput.text.toString().trim().toFloatOrNull()
                if (radius == null || radius < 25f || radius > 50000f) {
                    radiusInput.error = "25~50000 사이의 미터 값을 입력하세요."
                    return@setOnClickListener
                }
                val items = DestinationStore.get(this)
                val destination = Destination(
                    id = UUID.randomUUID().toString(),
                    name = input.text.toString().trim().ifBlank { "목적지 ${items.size + 1}" },
                    latitude = point.latitude,
                    longitude = point.longitude,
                    radiusMeters = radius,
                    enabled = true,
                    favorite = false
                )
                items.add(destination)
                DestinationStore.save(this, items)
                renderDestinations()
                refreshService()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun renderDestinations() {
        if (!::map.isInitialized || !::destinationsView.isInitialized) return
        destinationsView.removeAllViews()
        map.overlays.removeAll { it is Polygon || (it is Marker && it.title != "내 위치") }
        DestinationStore.get(this).forEach { d ->
            val point = GeoPoint(d.latitude, d.longitude)
            map.overlays.add(Marker(map).apply { position = point; title = d.name; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) })
            map.overlays.add(Polygon(map).apply {
                points = Polygon.pointsAsCircle(point, d.radiusMeters.toDouble())
                fillPaint.color = if (d.enabled) Color.argb(28,31,91,217) else Color.argb(12,120,120,120)
                outlinePaint.color = if (d.enabled) Color.rgb(31,91,217) else Color.GRAY
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = "${d.name} · 반경 ${d.radiusMeters.toInt()}m"; textSize = 16f }, LinearLayout.LayoutParams(0,-2,1f))
            row.addView(Button(this).apply { text = if (d.enabled) "ON" else "OFF"; textSize = 13f; setOnClickListener { updateDestination(d.copy(enabled = !d.enabled)) } })
            row.addView(Button(this).apply { text = "지도"; textSize = 13f; setOnClickListener { map.controller.setZoom(16.0); map.controller.animateTo(point) } })
            row.addView(Button(this).apply { text = "삭제"; textSize = 13f; setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setMessage("${d.name}을(를) 삭제할까요?")
                    .setNegativeButton("취소",null).setPositiveButton("삭제") { _, _ ->
                        DestinationStore.save(this@MainActivity, DestinationStore.get(this@MainActivity).filterNot { it.id == d.id })
                        renderDestinations(); refreshService()
                    }.show()
            } })
            destinationsView.addView(row)
        }
        if (DestinationStore.get(this).isEmpty()) destinationsView.addView(TextView(this).apply { text = "아직 목적지가 없습니다. 지도를 길게 눌러 추가하세요."; textSize = 16f })
        map.invalidate()
        lastKnownLocation?.let { updateLiveLocation(it) }
    }

    private fun showRegisteredDestinations() {
        val destinations = DestinationStore.get(this)
        if (destinations.isEmpty()) {
            AlertDialog.Builder(this).setTitle("등록된 위치")
                .setMessage("아직 등록된 위치가 없습니다. 지도에서 위치를 길게 누르거나 ‘위치 추가’를 눌러 등록하세요.")
                .setPositiveButton("확인", null).show()
            return
        }
        val labels = destinations.map { d ->
            "${if (d.enabled) "●" else "○"}  ${d.name} · 반경 ${d.radiusMeters.toInt()}m"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("등록된 위치")
            .setItems(labels) { _, which ->
                val d = destinations[which]
                AlertDialog.Builder(this).setTitle(d.name)
                    .setItems(arrayOf(if (d.enabled) "알림 끄기" else "알림 켜기", "지도에서 보기", "삭제")) { _, action ->
                        when (action) {
                            0 -> updateDestination(d.copy(enabled = !d.enabled))
                            1 -> { map.controller.setZoom(16.0); map.controller.animateTo(GeoPoint(d.latitude, d.longitude)) }
                            2 -> AlertDialog.Builder(this).setMessage("${d.name}을(를) 삭제할까요?")
                                .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                                    DestinationStore.save(this, DestinationStore.get(this).filterNot { it.id == d.id })
                                    renderDestinations()
                                    refreshService()
                                    lastKnownLocation?.let { updateLiveLocation(it) }
                                }.show()
                        }
                    }.setNegativeButton("취소", null).show()
            }.setPositiveButton("닫기", null).show()
    }

    private fun updateDestination(d: Destination) {
        val items = DestinationStore.get(this)
        val index = items.indexOfFirst { it.id == d.id }
        if (index >= 0) items[index] = d
        DestinationStore.save(this, items)
        renderDestinations()
        refreshService()
        lastKnownLocation?.let { updateLiveLocation(it) }
    }

    private fun refreshService() {
        try { startService(Intent(this, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_DESTINATIONS_CHANGED)) } catch (_: Exception) {}
    }

    private fun updateLiveLocation(location: Location) {
        val point = GeoPoint(location.latitude, location.longitude)
        if (currentMarker == null) {
            currentMarker = Marker(map).apply {
                title = "내 위치"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_menu_mylocation)
            }
            map.overlays.add(currentMarker)
        }
        lastKnownLocation = location
        currentMarker?.position = point
        val destinations = DestinationStore.get(this).filter { it.enabled }
        if (destinations.isEmpty()) {
            distanceView.text = "활성화된 목적지가 없습니다.\n‘위치 추가’를 눌러 목적지를 등록하세요."
        } else {
            distanceView.text = destinations.map { d ->
                val out = FloatArray(1)
                Location.distanceBetween(location.latitude, location.longitude, d.latitude, d.longitude, out)
                val meters = out[0]
                val distance = if (meters < 1000) "${meters.toInt()}m" else String.format(java.util.Locale.KOREA, "%.1fkm", meters / 1000f)
                "●  ${d.name}  ·  $distance"
            }.joinToString("\n")
        }
        map.invalidate()
    }

    private fun beginLiveLocationUpdates() {
        if (locationUpdatesStarted) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1500L)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fusedLocation.requestLocationUpdates(request, locationCallback, mainLooper)
            locationUpdatesStarted = true
        } catch (_: SecurityException) { }
    }

    private fun stopLiveLocationUpdates() {
        if (locationUpdatesStarted) fusedLocation.removeLocationUpdates(locationCallback)
        locationUpdatesStarted = false
    }

    private fun showCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestBasicPermissions(); return
        }
        LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    updateLiveLocation(loc)
                    val point = GeoPoint(loc.latitude, loc.longitude)
                    map.controller.setZoom(16.0)
                    map.controller.animateTo(point)
                } else {
                    Toast.makeText(this, "현재 위치를 확인하지 못했습니다. 위치 서비스를 확인하세요.", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun toggleTracking() {
        val active = DestinationStore.prefs(this).getBoolean("tracking_active", false)
        if (active) {
            DestinationStore.prefs(this).edit().putBoolean("tracking_active", false).apply()
            stopService(Intent(this, LocationTrackingService::class.java))
            status.text = "위치 감지를 중지했습니다."
        } else {
            startTracking()
        }
        updateTrackingToggle()
    }

    private fun updateTrackingToggle() {
        if (!::trackingToggle.isInitialized) return
        val active = DestinationStore.prefs(this).getBoolean("tracking_active", false)
        trackingToggle.text = if (active) "● 감지 중지" else "▶ 감지 시작"
        trackingToggle.setTextColor(if (active) Color.rgb(177, 47, 47) else Color.WHITE)
        trackingToggle.background = roundedBackground(
            if (active) Color.rgb(255, 235, 235) else Color.rgb(25, 116, 239), dp(15)
        )
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

    override fun onResume() { super.onResume(); if (::map.isInitialized) map.onResume(); renderDestinations(); updateTrackingToggle(); if (::fusedLocation.isInitialized) beginLiveLocationUpdates() }
    override fun onPause() { stopLiveLocationUpdates(); if (::map.isInitialized) map.onPause(); super.onPause() }
    override fun onDestroy() { if (::map.isInitialized) map.onDetach(); super.onDestroy() }
}
