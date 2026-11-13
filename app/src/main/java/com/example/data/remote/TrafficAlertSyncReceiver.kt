package com.example.data.remote

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.example.data.local.PreferencesManager
import com.example.data.model.TrafficAlert
import com.example.data.model.TrafficRefreshFrequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver planifié via AlarmManager pour vérifier périodiquement
 * les infos trafic Idelis en arrière-plan et notifier en cas de nouvelle perturbation.
 */
class TrafficAlertSyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CHECK_TRAFFIC_ALERTS) return

        val prefs = PreferencesManager(context)
        val freq = prefs.getTrafficRefreshFrequency()
        if (freq == TrafficRefreshFrequency.NEVER || !prefs.isTrafficNotificationsEnabled()) {
            cancel(context)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scraper = IdelisWebScraperService(
                    customBaseUrl = prefs.getWebMonitoringUrl(),
                    customQueryParam = prefs.getQueryTypeParam()
                )
                val result = scraper.getInfosTrafic()
                if (result.isSuccess) {
                    val alerts = result.getOrNull().orEmpty().map { item ->
                        TrafficAlert(
                            id = item.id,
                            title = item.title,
                            messageText = item.description,
                            affectedLines = item.affectedLines,
                            severity = "WARNING",
                            publishedDate = item.publishedDate.ifBlank { "En cours • idelis.fr" },
                            affectedLinePictos = item.affectedLinePictos,
                            deviationLink = item.deviationLink
                        )
                    }
                    prefs.saveLastTrafficRefreshTimestamp(System.currentTimeMillis())
                    TrafficAlertNotifier.processAlerts(
                        context = context,
                        prefs = prefs,
                        alerts = alerts,
                        allowNotification = true
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erreur vérification info trafic en arrière-plan: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TrafficAlertSync"
        const val ACTION_CHECK_TRAFFIC_ALERTS = "com.example.ACTION_CHECK_TRAFFIC_ALERTS"
        private const val REQUEST_CODE = 7102

        fun scheduleOrCancel(context: Context, prefs: PreferencesManager) {
            val freq = prefs.getTrafficRefreshFrequency()
            if (freq == TrafficRefreshFrequency.NEVER || freq.intervalMs <= 0L || !prefs.isTrafficNotificationsEnabled()) {
                cancel(context)
                return
            }

            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, TrafficAlertSyncReceiver::class.java).apply {
                    action = ACTION_CHECK_TRAFFIC_ALERTS
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + freq.intervalMs,
                    freq.intervalMs,
                    pendingIntent
                )
            } catch (e: Exception) {
                Log.w(TAG, "Impossible de planifier le suivi info trafic: ${e.message}")
            }
        }

        fun cancel(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, TrafficAlertSyncReceiver::class.java).apply {
                    action = ACTION_CHECK_TRAFFIC_ALERTS
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                }
            } catch (_: Exception) {}
        }
    }
}
