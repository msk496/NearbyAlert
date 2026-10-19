package com.example.nearbyalert

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class RegisteredLocationsActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(16).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(14))
            setBackgroundColor(Color.rgb(247, 249, 252))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(Button(this).apply {
            text = "‹"
            textSize = 30f
            isAllCaps = false
            background = rounded(Color.WHITE)
            setTextColor(Color.rgb(25, 50, 85))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(52), dp(48)))
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(TextView(this@RegisteredLocationsActivity).apply {
                text = "등록된 위치"
                textSize = 24f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(23, 40, 64))
            })
            addView(TextView(this@RegisteredLocationsActivity).apply {
                text = "장소별 알림과 감지 범위를 관리하세요"
                textSize = 13f
                setTextColor(Color.rgb(105, 120, 142))
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
        val summary = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(36, 85, 145))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(229, 240, 255))
        }
        root.addView(summary, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(16)
            bottomMargin = dp(12)
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "＋  지도에서 위치 추가"
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(25, 116, 239))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        setContentView(root)
        render(summary)
    }

    private fun render(summary: TextView) {
        list.removeAllViews()
        val destinations = DestinationStore.get(this)
        val enabledCount = destinations.count { it.enabled }
        summary.text = "${enabledCount}개 활성 · 전체 ${destinations.size}개 위치"
        if (destinations.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "아직 등록된 위치가 없습니다.\n지도 화면에서 위치를 추가해 주세요."
                textSize = 16f
                setTextColor(Color.rgb(95, 110, 130))
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(40), dp(20), dp(40))
            })
            return
        }
        destinations.forEach { d ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = rounded(Color.WHITE)
                elevation = dp(1).toFloat()
            }
            val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            titleRow.addView(TextView(this).apply {
                text = if (d.enabled) "●" else "○"
                textSize = 16f
                setTextColor(if (d.enabled) Color.rgb(24, 133, 91) else Color.GRAY)
            })
            titleRow.addView(TextView(this).apply {
                text = d.name
                textSize = 17f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(29, 43, 63))
                maxLines = 2
                setPadding(dp(8), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            titleRow.addView(TextView(this).apply {
                text = "반경 ${d.radiusMeters.toInt()}m"
                textSize = 12f
                setTextColor(Color.rgb(75, 105, 145))
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = rounded(Color.rgb(239, 245, 253))
            })
            card.addView(titleRow)
            card.addView(TextView(this).apply {
                text = String.format(java.util.Locale.KOREA, "%.5f, %.5f", d.latitude, d.longitude)
                textSize = 12f
                setTextColor(Color.rgb(120, 132, 150))
                setPadding(dp(24), dp(5), 0, dp(8))
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            actions.addView(actionButton(if (d.enabled) "알림 켜짐" else "알림 꺼짐", if (d.enabled) Color.rgb(225, 246, 236) else Color.rgb(241, 243, 247), if (d.enabled) Color.rgb(24, 120, 78) else Color.rgb(85, 98, 115)) {
                update(d.copy(enabled = !d.enabled), summary)
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(6) })
            actions.addView(actionButton("지도에서 보기", Color.rgb(229, 240, 255), Color.rgb(28, 93, 174)) {
                startActivity(Intent(this, MainActivity::class.java).putExtra("destination_id", d.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finish()
            }, LinearLayout.LayoutParams(0, dp(42), 1.2f).apply { rightMargin = dp(6) })
            actions.addView(actionButton("삭제", Color.rgb(255, 235, 235), Color.rgb(177, 47, 47)) {
                AlertDialog.Builder(this).setMessage("${d.name} 위치를 삭제할까요?")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("삭제") { _, _ ->
                        DestinationStore.save(this, DestinationStore.get(this).filterNot { it.id == d.id })
                        refreshService()
                        render(summary)
                    }.show()
            }, LinearLayout.LayoutParams(0, dp(42), 0.65f))
            card.addView(actions)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    private fun actionButton(label: String, bg: Int, fg: Int, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(fg)
        background = rounded(bg)
        setPadding(dp(4), 0, dp(4), 0)
        setOnClickListener { action() }
    }

    private fun update(destination: Destination, summary: TextView) {
        val items = DestinationStore.get(this)
        val index = items.indexOfFirst { it.id == destination.id }
        if (index >= 0) items[index] = destination
        DestinationStore.save(this, items)
        refreshService()
        render(summary)
    }

    private fun refreshService() {
        try {
            startService(Intent(this, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_DESTINATIONS_CHANGED))
        } catch (_: Exception) { }
    }
}
