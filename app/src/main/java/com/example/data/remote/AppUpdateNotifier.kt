package com.example.data.remote

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Gère les notifications Android locales lorsqu'une nouvelle version de Galaxie Bus
 * est détectée ou prête à être installée depuis GitHub Releases.
 */
object AppUpdateNotifier {

    private const val TAG = "AppUpdateNotifier"
    const val CHANNEL_ID = NotificationChannels.CHANNEL_APP_UPDATES
    private const val NOTIFICATION_ID_UPDATE = 7001
    const val EXTRA_OPEN_UPDATE = "EXTRA_OPEN_UPDATE"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannel(context: Context) {
        NotificationChannels.ensureAllChannels(context)
    }

    fun notifyUpdateAvailable(context: Context, release: GitHubReleaseInfo): Boolean {
        return postReleaseNotification(
            context = context,
            title = buildTitle(release, readyToInstall = false),
            release = release,
            readyToInstall = false
        )
    }

    fun notifyUpdateReadyToInstall(context: Context, release: GitHubReleaseInfo): Boolean {
        return postReleaseNotification(
            context = context,
            title = buildTitle(release, readyToInstall = true),
            release = release,
            readyToInstall = true
        )
    }

    private fun buildTitle(release: GitHubReleaseInfo, readyToInstall: Boolean): String {
        val versionLabel = release.tagName.ifBlank { "v${release.versionName}" }
        return if (readyToInstall) {
            "Mise à jour $versionLabel prête à installer"
        } else {
            "Nouvelle mise à jour $versionLabel disponible"
        }
    }

    private fun postReleaseNotification(
        context: Context,
        title: String,
        release: GitHubReleaseInfo,
        readyToInstall: Boolean
    ): Boolean {
        if (!hasNotificationPermission(context)) {
            Log.d(TAG, "Permission POST_NOTIFICATIONS non accordée, notification ignorée")
            return false
        }

        return try {
            ensureChannel(context)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_UPDATE, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_UPDATE,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val formattedNotes = formatReleaseNotesForDisplay(release.releaseNotes)
            val summaryLine = when {
                readyToInstall -> "APK téléchargé — appuyez pour installer la mise à jour."
                formattedNotes.isNotBlank() -> formattedNotes.lineSequence().firstOrNull().orEmpty()
                else -> "Appuyez pour voir et télécharger la mise à jour."
            }

            val expandedBody = buildString {
                if (readyToInstall) {
                    append("L'APK est prêt à être installé.")
                    if (formattedNotes.isNotBlank()) {
                        append("\n\n")
                    }
                }
                if (formattedNotes.isNotBlank()) {
                    append(formattedNotes)
                } else if (!readyToInstall) {
                    append("Une nouvelle version de Galaxie Bus est disponible sur GitHub.")
                }
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_widget_bus)
                .setContentTitle(title)
                .setContentText(summaryLine)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText(expandedBody)
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATE, notification)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification refusée (SecurityException): ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Erreur envoi notification mise à jour: ${e.message}")
            false
        }
    }
}
