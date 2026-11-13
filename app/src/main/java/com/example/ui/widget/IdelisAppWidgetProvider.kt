package com.example.ui.widget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.IdelisDatabase
import com.example.data.repository.IdelisRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IdelisAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.ui.widget.ACTION_REFRESH_WIDGET"
        const val EXTRA_STOP_ID = "EXTRA_STOP_ID"

        private data class RowIds(
            val rowId: Int,
            val badgeId: Int,
            val destId: Int,
            val delayId: Int,
            val timeId: Int
        )

        private val ROW_DEFINITIONS = listOf(
            RowIds(R.id.widget_row_1, R.id.row_1_badge, R.id.row_1_destination, R.id.row_1_delay, R.id.row_1_time),
            RowIds(R.id.widget_row_2, R.id.row_2_badge, R.id.row_2_destination, R.id.row_2_delay, R.id.row_2_time),
            RowIds(R.id.widget_row_3, R.id.row_3_badge, R.id.row_3_destination, R.id.row_3_delay, R.id.row_3_time),
            RowIds(R.id.widget_row_4, R.id.row_4_badge, R.id.row_4_destination, R.id.row_4_delay, R.id.row_4_time),
            RowIds(R.id.widget_row_5, R.id.row_5_badge, R.id.row_5_destination, R.id.row_5_delay, R.id.row_5_time),
            RowIds(R.id.widget_row_6, R.id.row_6_badge, R.id.row_6_destination, R.id.row_6_delay, R.id.row_6_time),
            RowIds(R.id.widget_row_7, R.id.row_7_badge, R.id.row_7_destination, R.id.row_7_delay, R.id.row_7_time),
            RowIds(R.id.widget_row_8, R.id.row_8_badge, R.id.row_8_destination, R.id.row_8_delay, R.id.row_8_time)
        )

        private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return 6371000.0 * c
        }

        @SuppressLint("MissingPermission")
        private fun getLastKnownLocation(context: Context): Location? {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            for (provider in providers) {
                try {
                    if (locationManager.isProviderEnabled(provider)) {
                        val loc = locationManager.getLastKnownLocation(provider)
                        if (loc != null) return loc
                    }
                } catch (_: SecurityException) {}
            }
            return null
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val isDynamicNearest = IdelisWidgetPreferences.isWidgetDynamicNearest(context, appWidgetId)
            var stopId = IdelisWidgetPreferences.getWidgetStopId(context, appWidgetId)
            var stopName = IdelisWidgetPreferences.getWidgetStopName(context, appWidgetId)

            var isMoving = false

            // Option Arrêt le plus proche automatique avec détection de mouvement
            if (isDynamicNearest) {
                val loc = getLastKnownLocation(context)
                if (loc != null) {
                    if (loc.hasSpeed() && loc.speed > 0.8f) { // ~3 km/h
                        isMoving = true
                    }
                    val requestStops = IdelisRepository.globalDownloadedStopsCache.filter { it.lat != 0.0 && it.lon != 0.0 }
                    val nearest = if (requestStops.isNotEmpty()) {
                        requestStops.minByOrNull {
                            calculateDistanceMeters(loc.latitude, loc.longitude, it.lat, it.lon)
                        }
                    } else null

                    if (nearest != null) {
                        stopName = "📍 ${nearest.stopName}"
                        stopId = "WEB_" + nearest.stopName.trim().replace(Regex("[^a-zA-Z0-9]"), "_")
                    } else {
                        stopName = "📍 Arrêt le plus proche"
                    }
                }
            }

            // Récupérer les dimensions du widget pour adapter le nombre de départs sans défilement
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140)

            // Calcul adaptatif du nombre de bus affichables en fonction de la hauteur
            val maxRowsAllowed = when {
                minHeight < 80 -> 1
                minHeight < 115 -> 2
                minHeight < 155 -> 3
                minHeight < 200 -> 4
                minHeight < 250 -> 5
                minHeight < 300 -> 6
                minHeight < 350 -> 7
                else -> 8
            }

            val views = RemoteViews(context.packageName, R.layout.widget_idelis_departures)
            views.setTextViewText(R.id.widget_stop_name, stopName)

            val timeFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)
            val currentTimeStr = timeFormat.format(Date())
            views.setTextViewText(
                R.id.widget_status_text,
                if (isMoving) "En mouvement ⚡ • $currentTimeStr" else "Temps réel • $currentTimeStr"
            )

            // Intent pour ouvrir l'application directement sur cet arrêt
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_STOP_ID, stopId)
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 1,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title_area, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_icon_bus, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            // 1. Mise à jour SYNCHRONE immédiate : le lanceur reçoit instantanément une RemoteViews valide
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // 2. Chargement asynchrone des passages réels
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = IdelisDatabase.getInstance(context)
                    val prefs = com.example.data.local.PreferencesManager(context)
                    val repository = IdelisRepository(database, context, prefs)
                    val departuresResult = repository.getDeparturesForStop(
                        stopId = stopId,
                        useSimulationMode = false,
                        forceOffline = false
                    )

                    val departures = departuresResult.departures
                    val visibleRowsCount = minOf(departures.size, maxRowsAllowed)

                    if (visibleRowsCount == 0) {
                        views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)
                        ROW_DEFINITIONS.forEach { def ->
                            views.setViewVisibility(def.rowId, View.GONE)
                        }
                    } else {
                        views.setViewVisibility(R.id.widget_empty_view, View.GONE)
                        for (i in ROW_DEFINITIONS.indices) {
                            val def = ROW_DEFINITIONS[i]
                            if (i < visibleRowsCount) {
                                val dep = departures[i]
                                views.setViewVisibility(def.rowId, View.VISIBLE)

                                // Génération de la pastille de ligne bitmap
                                val badgeBitmap = IdelisWidgetHelper.createLineBadgeBitmap(
                                    lineRef = dep.lineRef,
                                    routeColorHex = dep.routeColorHex,
                                    routeTextColorHex = dep.routeTextColorHex
                                )
                                views.setImageViewBitmap(def.badgeId, badgeBitmap)
                                views.setTextViewText(def.destId, dep.destinationName)
                                views.setTextViewText(def.delayId, IdelisWidgetHelper.formatStatusSubtitle(dep))
                                views.setTextViewText(def.timeId, IdelisWidgetHelper.formatWaitingTime(dep))

                                views.setOnClickPendingIntent(def.rowId, openAppPendingIntent)
                            } else {
                                views.setViewVisibility(def.rowId, View.GONE)
                            }
                        }
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)

                    // Planifier le prochain rafraîchissement selon les réglages utilisateur
                    val intervalMs = if (isDynamicNearest) {
                        val locFreq = prefs.getWidgetLocationFrequency()
                        if (locFreq.intervalMinutes > 0) locFreq.intervalMinutes * 60_000L else 0L
                    } else {
                        val widgetFreq = prefs.getWidgetRefreshFrequency()
                        if (widgetFreq.intervalMinutes > 0) widgetFreq.intervalMinutes * 60_000L else 0L
                    }
                    if (intervalMs > 0L) {
                        scheduleFastRefresh(context, appWidgetId, intervalMs)
                    } else {
                        cancelScheduledRefresh(context, appWidgetId)
                    }
                } catch (e: Exception) {
                    views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)
                    views.setTextViewText(R.id.widget_empty_view, "Horaires en cours d'actualisation")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        fun refreshAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = android.content.ComponentName(context, IdelisAppWidgetProvider::class.java)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                if (ids.isNotEmpty()) {
                    for (id in ids) {
                        updateAppWidget(context, appWidgetManager, id)
                    }
                }
            } catch (_: Exception) {}
        }

        private fun scheduleFastRefresh(context: Context, appWidgetId: Int, delayMs: Long) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, IdelisAppWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WIDGET
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 10 + 2,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + delayMs,
                    pendingIntent
                )
            } catch (_: Exception) {}
        }

        private fun cancelScheduledRefresh(context: Context, appWidgetId: Int) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, IdelisAppWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WIDGET
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 10 + 2,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        IdelisWidgetPreferences.deleteWidgetPrefs(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}
