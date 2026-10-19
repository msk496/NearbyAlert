package com.example.nearbyalert

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager

class AlertAcknowledgeReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ACKNOWLEDGE = "com.example.nearbyalert.ACKNOWLEDGE_ALERT"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_ACKNOWLEDGE) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            }
            vibrator.cancel()
        } catch (_: Exception) { }
        val notificationId = intent.getIntExtra("notification_id", -1)
        if (notificationId >= 0) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId)
        }
    }
}
