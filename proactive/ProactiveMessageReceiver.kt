package me.rerere.rikkahub.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class ProactiveMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "ProactiveMessageReceiver triggered, action=${intent.action}")
        when (intent.action) {
            ACTION_PROACTIVE_MESSAGE -> {
                val serviceIntent = Intent(context, ProactiveMessageTriggerService::class.java)
                context.startForegroundService(serviceIntent)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val enabled = prefs.getBoolean(KEY_ENABLED, false)
                if (enabled) {
                    val min = prefs.getInt(KEY_MIN_INTERVAL, 60)
                    val max = prefs.getInt(KEY_MAX_INTERVAL, 180)
                    schedule(context, min, max)
                }
            }
        }
    }

    companion object {
        const val TAG = "ProactiveReceiver"
        const val ACTION_PROACTIVE_MESSAGE = "me.rerere.rikkahub.PROACTIVE_MESSAGE"
        const val PREFS_NAME = "proactive_prefs"
        const val KEY_ENABLED = "enabled"
        const val KEY_MIN_INTERVAL = "min_interval"
        const val KEY_MAX_INTERVAL = "max_interval"
        private const val REQUEST_CODE = 10001

        fun schedule(context: Context, minMinutes: Int, maxMinutes: Int) {
            val min = minMinutes.coerceAtLeast(1)
            val max = maxMinutes.coerceAtLeast(min)
            val delay = Random.nextInt(min, max + 1)
            val triggerTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delay.toLong())

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
                action = ACTION_PROACTIVE_MESSAGE
            }
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pending)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pending)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pending)
            }

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, true)
                .putInt(KEY_MIN_INTERVAL, minMinutes)
                .putInt(KEY_MAX_INTERVAL, maxMinutes)
                .apply()

            Log.d(TAG, "Scheduled next proactive message in $delay minutes")
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ProactiveMessageReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pending?.let { alarmManager.cancel(it) }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, false).apply()
            Log.d(TAG, "Cancelled proactive message alarm")
        }
    }
}
