package com.example.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.R

/**
 * Registre central des catégories de notifications (NotificationChannels) visibles
 * dans les informations de l'application Android (Paramètres système > Applications > Galaxie Bus > Notifications).
 */
object NotificationChannels {

    const val CHANNEL_APP_UPDATES = "galaxie_bus_app_updates"
    const val CHANNEL_TRAFFIC_ALERTS = "galaxie_bus_traffic_alerts"
    const val CHANNEL_WIDGET_UPDATES = "galaxie_bus_widget_updates"

    fun ensureAllChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val appUpdatesChannel = NotificationChannel(
            CHANNEL_APP_UPDATES,
            context.getString(R.string.notif_channel_app_updates_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_app_updates_desc)
        }

        val trafficAlertsChannel = NotificationChannel(
            CHANNEL_TRAFFIC_ALERTS,
            context.getString(R.string.notif_channel_traffic_alerts_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_traffic_alerts_desc)
        }

        val widgetUpdatesChannel = NotificationChannel(
            CHANNEL_WIDGET_UPDATES,
            context.getString(R.string.notif_channel_widget_updates_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_widget_updates_desc)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }

        manager.createNotificationChannels(
            listOf(
                appUpdatesChannel,
                trafficAlertsChannel,
                widgetUpdatesChannel
            )
        )
    }

    fun isChannelEnabled(context: Context, channelId: String): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureAllChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return true
            val channel = manager.getNotificationChannel(channelId) ?: return true
            return channel.importance != NotificationManager.IMPORTANCE_NONE
        }
        return true
    }

    fun openAppNotificationSettings(context: Context) {
        ensureAllChannels(context)
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }

    fun openChannelNotificationSettings(context: Context, channelId: String) {
        ensureAllChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        openAppNotificationSettings(context)
    }
}
