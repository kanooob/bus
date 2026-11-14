package com.example.data.remote

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.PreferencesManager
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gère la notification silencieuse affichée uniquement pendant l'actualisation des widgets.
 */
object WidgetUpdateNotifier {

    private const val TAG = "WidgetUpdateNotifier"
    const val CHANNEL_ID = NotificationChannels.CHANNEL_WIDGET_UPDATES
    private const val NOTIFICATION_ID_WIDGET_REFRESH = 7003

    private val activeRefreshCount = AtomicInteger(0)

    fun ensureChannel(context: Context) {
        NotificationChannels.ensureAllChannels(context)
    }

    /**
     * Affiche une notification silencieuse avec barre de progression pendant l'actualisation d'un widget.
     * Retourne true si la notification a été affichée (auquel cas [finishWidgetRefreshing] doit être appelé en fin de tâche).
     */
    fun showWidgetRefreshing(
        context: Context,
        stopId: String,
        stopName: String,
        prefs: PreferencesManager = PreferencesManager(context)
    ): Boolean {
        if (!prefs.isWidgetUpdateNotificationsEnabled()) {
            return false
        }
        if (!AppUpdateNotifier.hasNotificationPermission(context)) {
            Log.d(TAG, "Permission POST_NOTIFICATIONS non accordée, notification widget ignorée")
            return false
        }
        if (!NotificationChannels.isChannelEnabled(context, CHANNEL_ID)) {
            return false
        }

        return try {
            ensureChannel(context)
            val activeCount = activeRefreshCount.incrementAndGet()

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_STOP_ID", stopId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_WIDGET_REFRESH,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cleanStopName = stopName.removePrefix("📍").trim().ifBlank { "Arrêt Idelis" }
            val contentText = if (activeCount > 1) {
                "Mise à jour de $activeCount widgets en cours…"
            } else {
                "Mise à jour des horaires ($cleanStopName)…"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_widget_bus)
                .setContentTitle("Actualisation du widget")
                .setContentText(contentText)
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setShowWhen(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_WIDGET_REFRESH, notification)
            true
        } catch (e: SecurityException) {
            activeRefreshCount.updateAndGet { (it - 1).coerceAtLeast(0) }
            Log.w(TAG, "Notification widget refusée (SecurityException): ${e.message}")
            false
        } catch (e: Exception) {
            activeRefreshCount.updateAndGet { (it - 1).coerceAtLeast(0) }
            Log.w(TAG, "Erreur affichage notification widget: ${e.message}")
            false
        }
    }

    /**
     * Masque la notification d'actualisation dès que la mise à jour du ou des widgets est terminée.
     */
    fun finishWidgetRefreshing(context: Context) {
        val remaining = activeRefreshCount.updateAndGet { (it - 1).coerceAtLeast(0) }
        if (remaining <= 0) {
            try {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_WIDGET_REFRESH)
            } catch (_: Exception) {}
        }
    }
}
