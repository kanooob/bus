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
import com.example.data.model.TrafficAlert
import java.util.Locale

/**
 * Gère la détection des nouvelles alertes info trafic et l'envoi de notifications locales.
 */
object TrafficAlertNotifier {

    private const val TAG = "TrafficAlertNotifier"
    const val CHANNEL_ID = NotificationChannels.CHANNEL_TRAFFIC_ALERTS
    private const val NOTIFICATION_ID_TRAFFIC = 7002
    const val EXTRA_OPEN_TRAFFIC_INFO = "EXTRA_OPEN_TRAFFIC_INFO"

    fun alertFingerprint(alert: TrafficAlert): String {
        val title = alert.title.trim().lowercase(Locale.ROOT)
        val msg = alert.messageText.trim().lowercase(Locale.ROOT)
        val link = alert.deviationLink.orEmpty().trim().lowercase(Locale.ROOT)
        return "${title.hashCode()}_${msg.hashCode()}_${link.hashCode()}_${title.take(40)}"
    }

    fun ensureChannel(context: Context) {
        NotificationChannels.ensureAllChannels(context)
    }

    /**
     * Compare les alertes récupérées aux alertes déjà connues.
     * Sur la toute première initialisation (baseline non établie), mémorise les alertes
     * existantes sans déclencher de notification afin d'éviter un flot initial.
     * Envoie une notification uniquement pour les nouvelles alertes si l'option est activée.
     */
    fun processAlerts(
        context: Context?,
        prefs: PreferencesManager?,
        alerts: List<TrafficAlert>,
        allowNotification: Boolean
    ): List<TrafficAlert> {
        if (prefs == null) return emptyList()

        val currentKeys = alerts.map { alertFingerprint(it) }.toSet()
        if (!prefs.hasTrafficBaselineInitialized()) {
            prefs.saveKnownTrafficAlertKeys(currentKeys)
            prefs.setTrafficBaselineInitialized(true)
            return emptyList()
        }

        val knownKeys = prefs.getKnownTrafficAlertKeys()
        val newAlerts = alerts.filter { alertFingerprint(it) !in knownKeys }
        prefs.saveKnownTrafficAlertKeys(currentKeys)

        if (allowNotification && newAlerts.isNotEmpty() && prefs.isTrafficNotificationsEnabled() && context != null) {
            notifyNewTrafficAlerts(context, newAlerts)
        }
        return newAlerts
    }

    fun notifyNewTrafficAlerts(context: Context, newAlerts: List<TrafficAlert>): Boolean {
        if (newAlerts.isEmpty()) return false
        if (!AppUpdateNotifier.hasNotificationPermission(context)) {
            Log.d(TAG, "Permission POST_NOTIFICATIONS non accordée, notification info trafic ignorée")
            return false
        }

        return try {
            ensureChannel(context)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_TRAFFIC_INFO, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_TRAFFIC,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title: String
            val summaryText: String
            val expandedText: String

            if (newAlerts.size == 1) {
                val alert = newAlerts.first()
                title = "Nouvelle info trafic : ${alert.title}"
                summaryText = alert.messageText.ifBlank { "Appuyez pour consulter le détail dans l'application." }
                expandedText = buildString {
                    if (alert.affectedLines.isNotEmpty()) {
                        append("Lignes concernées : ${alert.affectedLines.joinToString(", ")}")
                        if (alert.messageText.isNotBlank()) append("\n\n")
                    }
                    if (alert.messageText.isNotBlank()) {
                        append(alert.messageText)
                    } else if (alert.affectedLines.isEmpty()) {
                        append("Une nouvelle perturbation a été publiée sur idelis.fr.")
                    }
                }
            } else {
                title = "${newAlerts.size} nouvelles infos trafic Idelis"
                summaryText = newAlerts.joinToString(" • ") { it.title }
                expandedText = newAlerts.joinToString("\n") { "• ${it.title}" }
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_widget_bus)
                .setContentTitle(title)
                .setContentText(summaryText)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText(expandedText)
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TRAFFIC, notification)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification info trafic refusée: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Erreur envoi notification info trafic: ${e.message}")
            false
        }
    }
}
