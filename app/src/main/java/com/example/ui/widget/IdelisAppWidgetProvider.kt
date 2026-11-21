package com.example.ui.widget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.GtfsDatabaseSeeder
import com.example.data.local.IdelisDatabase
import com.example.data.local.PreferencesManager
import com.example.data.remote.NotificationChannels
import com.example.data.remote.WidgetUpdateNotifier
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
        const val ACTION_CYCLE_FAVORITE_STOP = "com.example.ui.widget.ACTION_CYCLE_FAVORITE_STOP"
        const val EXTRA_STOP_ID = "EXTRA_STOP_ID"

        private data class RowIds(
            val rowId: Int,
            val badgeId: Int,
            val destId: Int,
            val delayId: Int,
            val timeId: Int
        )

        private data class ResolvedWidgetPalette(
            val isLight: Boolean,
            val rootBgRes: Int,
            val rowBgRes: Int,
            val actionPillRes: Int,
            val badgeBgRes: Int,
            val normalTimePillBgRes: Int,
            val primaryTextColor: Int,
            val secondaryTextColor: Int,
            val dividerColor: Int,
            val actionIconTint: Int,
            val onTimeColor: Int,
            val delayedColor: Int,
            val earlyColor: Int
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

        private fun resolvePalette(
            context: Context,
            themeStyle: WidgetThemeStyle,
            colorMode: WidgetColorMode
        ): ResolvedWidgetPalette {
            val systemNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val effectiveStyle = when (themeStyle) {
                WidgetThemeStyle.SYSTEM -> if (systemNight) WidgetThemeStyle.DARK else WidgetThemeStyle.LIGHT
                else -> themeStyle
            }
            val useMaterialYou = colorMode == WidgetColorMode.MATERIAL_YOU &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

            if (useMaterialYou) {
                val neutralPrimaryLight = androidx.core.content.ContextCompat.getColor(context, android.R.color.system_neutral1_900)
                val neutralSecondaryLight = androidx.core.content.ContextCompat.getColor(context, android.R.color.system_neutral2_600)
                val neutralDividerLight = androidx.core.content.ContextCompat.getColor(context, android.R.color.system_neutral2_200)
                val accentTintLight = androidx.core.content.ContextCompat.getColor(context, android.R.color.system_accent1_700)

                val neutralPrimaryDark = androidx.core.content.ContextCompat.getColor(context, android.R.color.system_neutral1_50)
                val neutralSecondaryDark = androidx.core.content.ContextCompat.getColor(context, android.R.color.system_neutral2_300)
                val neutralDividerDark = androidx.core.content.ContextCompat.getColor(context, android.R.color.system_neutral2_700)
                val accentTintDark = androidx.core.content.ContextCompat.getColor(context, android.R.color.system_accent1_200)

                return when (effectiveStyle) {
                    WidgetThemeStyle.LIGHT -> ResolvedWidgetPalette(
                        isLight = true,
                        rootBgRes = R.drawable.widget_bg_material_light,
                        rowBgRes = R.drawable.widget_row_bg_material_light,
                        actionPillRes = R.drawable.widget_action_pill_material_light,
                        badgeBgRes = R.drawable.widget_bus_badge_material,
                        normalTimePillBgRes = R.drawable.widget_time_pill_material,
                        primaryTextColor = neutralPrimaryLight,
                        secondaryTextColor = neutralSecondaryLight,
                        dividerColor = neutralDividerLight,
                        actionIconTint = accentTintLight,
                        onTimeColor = Color.parseColor("#15803D"),
                        delayedColor = Color.parseColor("#DC2626"),
                        earlyColor = Color.parseColor("#0288D1")
                    )
                    WidgetThemeStyle.TRANSLUCENT -> ResolvedWidgetPalette(
                        isLight = false,
                        rootBgRes = R.drawable.widget_bg_translucent,
                        rowBgRes = R.drawable.widget_row_bg_translucent,
                        actionPillRes = R.drawable.widget_action_pill_translucent,
                        badgeBgRes = R.drawable.widget_bus_badge_material,
                        normalTimePillBgRes = R.drawable.widget_time_pill_material,
                        primaryTextColor = Color.WHITE,
                        secondaryTextColor = Color.parseColor("#CBD5E1"),
                        dividerColor = Color.parseColor("#33FFFFFF"),
                        actionIconTint = accentTintDark,
                        onTimeColor = Color.parseColor("#4ADE80"),
                        delayedColor = Color.parseColor("#FCA5A5"),
                        earlyColor = Color.parseColor("#7DD3FC")
                    )
                    else -> ResolvedWidgetPalette(
                        isLight = false,
                        rootBgRes = R.drawable.widget_bg_material_dark,
                        rowBgRes = R.drawable.widget_row_bg_material_dark,
                        actionPillRes = R.drawable.widget_action_pill_material_dark,
                        badgeBgRes = R.drawable.widget_bus_badge_material,
                        normalTimePillBgRes = R.drawable.widget_time_pill_material,
                        primaryTextColor = neutralPrimaryDark,
                        secondaryTextColor = neutralSecondaryDark,
                        dividerColor = neutralDividerDark,
                        actionIconTint = accentTintDark,
                        onTimeColor = Color.parseColor("#4ADE80"),
                        delayedColor = Color.parseColor("#F87171"),
                        earlyColor = Color.parseColor("#38BDF8")
                    )
                }
            }

            return when (effectiveStyle) {
                WidgetThemeStyle.LIGHT -> ResolvedWidgetPalette(
                    isLight = true,
                    rootBgRes = R.drawable.widget_bg_light,
                    rowBgRes = R.drawable.widget_row_bg_light,
                    actionPillRes = R.drawable.widget_action_pill_light,
                    badgeBgRes = R.drawable.widget_bus_badge_bg,
                    normalTimePillBgRes = R.drawable.widget_time_pill_app,
                    primaryTextColor = Color.parseColor("#0F172A"),
                    secondaryTextColor = Color.parseColor("#64748B"),
                    dividerColor = Color.parseColor("#E2E8F0"),
                    actionIconTint = Color.parseColor("#334155"),
                    onTimeColor = Color.parseColor("#15803D"),
                    delayedColor = Color.parseColor("#DC2626"),
                    earlyColor = Color.parseColor("#0288D1")
                )
                WidgetThemeStyle.TRANSLUCENT -> ResolvedWidgetPalette(
                    isLight = false,
                    rootBgRes = R.drawable.widget_bg_translucent,
                    rowBgRes = R.drawable.widget_row_bg_translucent,
                    actionPillRes = R.drawable.widget_action_pill_translucent,
                    badgeBgRes = R.drawable.widget_bus_badge_bg,
                    normalTimePillBgRes = R.drawable.widget_time_pill_app,
                    primaryTextColor = Color.WHITE,
                    secondaryTextColor = Color.parseColor("#CBD5E1"),
                    dividerColor = Color.parseColor("#33FFFFFF"),
                    actionIconTint = Color.WHITE,
                    onTimeColor = Color.parseColor("#4ADE80"),
                    delayedColor = Color.parseColor("#FCA5A5"),
                    earlyColor = Color.parseColor("#7DD3FC")
                )
                else -> ResolvedWidgetPalette(
                    isLight = false,
                    rootBgRes = R.drawable.widget_bg_dark,
                    rowBgRes = R.drawable.widget_row_bg_dark,
                    actionPillRes = R.drawable.widget_action_pill_dark,
                    badgeBgRes = R.drawable.widget_bus_badge_bg,
                    normalTimePillBgRes = R.drawable.widget_time_pill_app,
                    primaryTextColor = Color.WHITE,
                    secondaryTextColor = Color.parseColor("#94A3B8"),
                    dividerColor = Color.parseColor("#2A3A52"),
                    actionIconTint = Color.parseColor("#E2E8F0"),
                    onTimeColor = Color.parseColor("#4ADE80"),
                    delayedColor = Color.parseColor("#F87171"),
                    earlyColor = Color.parseColor("#38BDF8")
                )
            }
        }

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
            val providers = buildList {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    add(LocationManager.FUSED_PROVIDER)
                }
                add(LocationManager.GPS_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
                add(LocationManager.PASSIVE_PROVIDER)
            }
            var best: Location? = null
            for (provider in providers) {
                try {
                    if (locationManager.isProviderEnabled(provider)) {
                        val loc = locationManager.getLastKnownLocation(provider)
                        if (loc != null && (best == null || loc.time > best.time)) {
                            best = loc
                        }
                    }
                } catch (_: SecurityException) {}
                catch (_: IllegalArgumentException) {}
            }
            return best
        }

        private fun applyHeaderAndTheme(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            config: WidgetConfig,
            palette: ResolvedWidgetPalette,
            favoriteCount: Int,
            openAppPendingIntent: PendingIntent
        ) {
            views.setInt(R.id.widget_root, "setBackgroundResource", palette.rootBgRes)
            views.setInt(R.id.widget_icon_bus_container, "setBackgroundResource", palette.badgeBgRes)
            views.setImageViewResource(
                R.id.widget_icon_bus,
                if (config.isDynamicNearest) R.drawable.ic_widget_gps else R.drawable.ic_widget_bus
            )
            views.setContentDescription(
                R.id.widget_icon_bus,
                if (config.isDynamicNearest) "Arrêt le plus proche (GPS)" else "Idelis Bus"
            )
            views.setTextColor(R.id.widget_stop_name, palette.primaryTextColor)
            views.setTextColor(R.id.widget_status_text, palette.secondaryTextColor)
            views.setInt(R.id.widget_divider, "setBackgroundColor", palette.dividerColor)
            views.setTextColor(R.id.widget_empty_view, palette.secondaryTextColor)

            if (config.showQuickActions) {
                views.setViewVisibility(R.id.widget_actions_container, View.VISIBLE)

                // Bouton Favori suivant (affiché si activé et au moins 1 favori disponible)
                if (config.showFavoriteSwitch && favoriteCount > 0) {
                    views.setViewVisibility(R.id.widget_btn_cycle_stop, View.VISIBLE)
                    views.setInt(R.id.widget_btn_cycle_stop, "setBackgroundResource", palette.actionPillRes)
                    views.setInt(R.id.widget_btn_cycle_stop, "setColorFilter", palette.actionIconTint)
                    val cycleIntent = Intent(context, IdelisAppWidgetProvider::class.java).apply {
                        action = ACTION_CYCLE_FAVORITE_STOP
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    val cyclePending = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 100 + 4,
                        cycleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_cycle_stop, cyclePending)
                } else {
                    views.setViewVisibility(R.id.widget_btn_cycle_stop, View.GONE)
                }

                // Bouton Actualiser
                views.setInt(R.id.widget_btn_refresh, "setBackgroundResource", palette.actionPillRes)
                views.setInt(R.id.widget_btn_refresh, "setColorFilter", palette.actionIconTint)
                val refreshIntent = Intent(context, IdelisAppWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WIDGET
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val refreshPending = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 100 + 2,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPending)

                // Bouton Menu / Configurer le widget
                views.setInt(R.id.widget_btn_configure, "setBackgroundResource", palette.actionPillRes)
                views.setInt(R.id.widget_btn_configure, "setColorFilter", palette.actionIconTint)
                val configureIntent = Intent(context, IdelisWidgetConfigureActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val configurePending = PendingIntent.getActivity(
                    context,
                    appWidgetId * 100 + 3,
                    configureIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_configure, configurePending)
            } else {
                views.setViewVisibility(R.id.widget_actions_container, View.GONE)
            }

            views.setOnClickPendingIntent(R.id.widget_title_area, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_icon_bus_container, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_icon_bus, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            postSilentNotification: Boolean = false,
            isManualTap: Boolean = false
        ) {
            val prefs = PreferencesManager(context)
            val config = IdelisWidgetPreferences.getWidgetConfig(context, appWidgetId)
            val palette = resolvePalette(context, config.themeStyle, config.colorMode)
            val favoriteCount = prefs.getFavoriteStopIds().size

            val isDynamicNearest = config.isDynamicNearest
            val defaultStop = GtfsDatabaseSeeder.INITIAL_STOPS.first()
            var stopId = config.stopId
            var stopName = config.stopName.replace("📍", "").trim()
                .takeIf { it.isNotBlank() && !it.equals("Arrêt le plus proche", ignoreCase = true) }
                ?: defaultStop.stopName

            var isMoving = false
            val dynamicLocation = if (isDynamicNearest) getLastKnownLocation(context) else null

            // Option Arrêt le plus proche automatique avec détection de mouvement (affiche le nom réel de l'arrêt, sans emoji épingle)
            if (isDynamicNearest) {
                val cachedNearestId = IdelisWidgetPreferences.getLastNearestStopId(context, appWidgetId)
                val cachedNearestName = IdelisWidgetPreferences.getLastNearestStopName(context, appWidgetId)

                if (dynamicLocation != null) {
                    if (dynamicLocation.hasSpeed() && dynamicLocation.speed > 0.8f) { // ~3 km/h
                        isMoving = true
                    }
                    val requestStops = IdelisRepository.globalDownloadedStopsCache.filter { it.lat != 0.0 && it.lon != 0.0 }
                    val nearest = if (requestStops.isNotEmpty()) {
                        requestStops.minByOrNull {
                            calculateDistanceMeters(dynamicLocation.latitude, dynamicLocation.longitude, it.lat, it.lon)
                        }
                    } else null

                    if (nearest != null) {
                        stopName = nearest.stopName.replace("📍", "").trim()
                        val poleOrName = nearest.pole?.takeIf { it.isNotBlank() } ?: nearest.stopName
                        stopId = "WEB_" + poleOrName.trim().replace(Regex("[^a-zA-Z0-9]"), "_")
                    } else if (!cachedNearestId.isNullOrBlank() && !cachedNearestName.isNullOrBlank()) {
                        stopId = cachedNearestId
                        stopName = cachedNearestName
                    } else {
                        stopId = defaultStop.stopId
                        stopName = defaultStop.stopName
                    }
                } else if (!cachedNearestId.isNullOrBlank() && !cachedNearestName.isNullOrBlank()) {
                    stopId = cachedNearestId
                    stopName = cachedNearestName
                } else {
                    val lastSelectedId = prefs.getLastSelectedStopId()
                        ?.takeIf { it.isNotBlank() && it != IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID }
                    val matchedSeed = GtfsDatabaseSeeder.INITIAL_STOPS.firstOrNull { it.stopId == lastSelectedId }
                    stopId = matchedSeed?.stopId ?: lastSelectedId ?: defaultStop.stopId
                    stopName = matchedSeed?.stopName ?: defaultStop.stopName
                }
            } else if (stopId == IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID) {
                stopId = defaultStop.stopId
                stopName = defaultStop.stopName
            }

            // Récupérer les dimensions du widget pour adapter le nombre de départs sans défilement
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140)

            val isCompact = config.densityMode == WidgetDensityMode.COMPACT
            val maxRowsAllowed = if (isCompact) {
                when {
                    minHeight < 75 -> 1
                    minHeight < 105 -> 2
                    minHeight < 135 -> 3
                    minHeight < 170 -> 4
                    minHeight < 205 -> 5
                    minHeight < 245 -> 6
                    minHeight < 285 -> 7
                    else -> 8
                }
            } else {
                when {
                    minHeight < 80 -> 1
                    minHeight < 115 -> 2
                    minHeight < 155 -> 3
                    minHeight < 200 -> 4
                    minHeight < 250 -> 5
                    minHeight < 300 -> 6
                    minHeight < 350 -> 7
                    else -> 8
                }
            }

            val views = RemoteViews(context.packageName, R.layout.widget_idelis_departures)
            views.setTextViewText(R.id.widget_stop_name, stopName)

            val timeFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)
            val currentTimeStr = timeFormat.format(Date())
            val sortedFilterLines = IdelisWidgetPreferences.sortLinesNaturally(config.filteredLines)
            val filterBadge = if (sortedFilterLines.isNotEmpty()) {
                " • ${sortedFilterLines.take(3).joinToString(",")}"
            } else {
                ""
            }
            val initialStatus = if (isManualTap) {
                "Actualisation…$filterBadge"
            } else if (isMoving) {
                "En mouvement ⚡ • $currentTimeStr$filterBadge"
            } else {
                "Temps réel • $currentTimeStr$filterBadge"
            }
            views.setTextViewText(R.id.widget_status_text, initialStatus)

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_STOP_ID, stopId)
            }
            var openAppPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId * 100 + 1,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            applyHeaderAndTheme(
                context = context,
                views = views,
                appWidgetId = appWidgetId,
                config = config,
                palette = palette,
                favoriteCount = favoriteCount,
                openAppPendingIntent = openAppPendingIntent
            )

            // 1. Mise à jour SYNCHRONE immédiate : le lanceur reçoit instantanément une RemoteViews valide
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // 2. Chargement asynchrone des passages réels
            CoroutineScope(Dispatchers.IO).launch {
                val notifStarted = if (postSilentNotification) {
                    WidgetUpdateNotifier.showWidgetRefreshing(
                        context = context,
                        stopId = stopId,
                        stopName = stopName,
                        prefs = prefs
                    )
                } else {
                    false
                }
                try {
                    val database = IdelisDatabase.getInstance(context)
                    val repository = IdelisRepository(database, context, prefs)

                    var resolvedStopOverride: com.example.data.model.GtfsStop? = null
                    var effectiveStopId = stopId
                    if (isDynamicNearest && dynamicLocation != null) {
                        val resolved = repository.resolveAndSaveNearestStop(
                            userLat = dynamicLocation.latitude,
                            userLon = dynamicLocation.longitude,
                            fallbackStops = emptyList()
                        )
                        if (resolved != null) {
                            resolvedStopOverride = resolved.stop
                            effectiveStopId = resolved.stop.stopId
                            val cleanResolvedName = resolved.stop.stopName.replace("📍", "").trim()
                            IdelisWidgetPreferences.saveLastNearestStop(
                                context = context,
                                appWidgetId = appWidgetId,
                                stopId = effectiveStopId,
                                stopName = cleanResolvedName
                            )
                            views.setTextViewText(R.id.widget_stop_name, cleanResolvedName)
                            val updatedOpenIntent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra(EXTRA_STOP_ID, effectiveStopId)
                            }
                            openAppPendingIntent = PendingIntent.getActivity(
                                context,
                                appWidgetId * 100 + 1,
                                updatedOpenIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            applyHeaderAndTheme(
                                context = context,
                                views = views,
                                appWidgetId = appWidgetId,
                                config = config,
                                palette = palette,
                                favoriteCount = favoriteCount,
                                openAppPendingIntent = openAppPendingIntent
                            )
                        }
                    } else if (isDynamicNearest) {
                        val dbStop = database.stopDao().getStopById(effectiveStopId)
                        if (dbStop != null) {
                            resolvedStopOverride = dbStop
                            val cleanDbName = dbStop.stopName.replace("📍", "").trim()
                            IdelisWidgetPreferences.saveLastNearestStop(
                                context = context,
                                appWidgetId = appWidgetId,
                                stopId = dbStop.stopId,
                                stopName = cleanDbName
                            )
                            views.setTextViewText(R.id.widget_stop_name, cleanDbName)
                        }
                    }

                    val updatedTimeStr = timeFormat.format(Date())
                    views.setTextViewText(
                        R.id.widget_status_text,
                        if (isMoving) {
                            "En mouvement ⚡ • $updatedTimeStr$filterBadge"
                        } else {
                            "Temps réel • $updatedTimeStr$filterBadge"
                        }
                    )

                    val departuresResult = repository.getDeparturesForStop(
                        stopId = effectiveStopId,
                        useSimulationMode = false,
                        forceOffline = false,
                        stopOverride = resolvedStopOverride
                    )

                    val allDepartures = departuresResult.departures
                    val filteredDepartures = if (config.filteredLines.isNotEmpty()) {
                        val allowed = config.filteredLines
                            .map { IdelisWidgetPreferences.normalizeLineCode(it) }
                            .filter { it.isNotBlank() }
                            .toSet()
                        allDepartures.filter {
                            IdelisWidgetPreferences.normalizeLineCode(it.lineRef) in allowed
                        }
                    } else {
                        allDepartures
                    }

                    val visibleRowsCount = minOf(filteredDepartures.size, maxRowsAllowed)

                    if (visibleRowsCount == 0) {
                        views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)
                        val emptyText = when {
                            config.filteredLines.isNotEmpty() && allDepartures.isNotEmpty() ->
                                "Aucun départ pour les lignes ${sortedFilterLines.joinToString(", ")}"
                            departuresResult.isStopNotServed ->
                                "Arrêt actuellement non desservi"
                            else ->
                                "Aucun départ prévu actuellement"
                        }
                        views.setTextViewText(R.id.widget_empty_view, emptyText)
                        ROW_DEFINITIONS.forEach { def ->
                            views.setViewVisibility(def.rowId, View.GONE)
                        }
                    } else {
                        views.setViewVisibility(R.id.widget_empty_view, View.GONE)
                        for (i in ROW_DEFINITIONS.indices) {
                            val def = ROW_DEFINITIONS[i]
                            if (i < visibleRowsCount) {
                                val dep = filteredDepartures[i]
                                views.setViewVisibility(def.rowId, View.VISIBLE)
                                views.setInt(def.rowId, "setBackgroundResource", palette.rowBgRes)
                                views.setTextColor(def.destId, palette.primaryTextColor)

                                val badgeBitmap = IdelisWidgetHelper.loadLineIconBitmap(dep.iconUrl, context)
                                    ?: IdelisWidgetHelper.createLineBadgeBitmap(
                                        lineRef = dep.lineRef,
                                        routeColorHex = dep.routeColorHex,
                                        routeTextColorHex = dep.routeTextColorHex
                                    )
                                views.setImageViewBitmap(def.badgeId, badgeBitmap)
                                views.setTextViewText(def.destId, dep.destinationName)

                                if (isCompact) {
                                    views.setViewVisibility(def.delayId, View.GONE)
                                } else {
                                    views.setViewVisibility(def.delayId, View.VISIBLE)
                                    views.setTextViewText(def.delayId, IdelisWidgetHelper.formatStatusSubtitle(dep))
                                    val delayColor = when {
                                        !dep.isRealTime -> palette.secondaryTextColor
                                        dep.delayMinutes > 0 -> palette.delayedColor
                                        dep.delayMinutes < 0 -> palette.earlyColor
                                        else -> palette.onTimeColor
                                    }
                                    views.setTextColor(def.delayId, delayColor)
                                }

                                views.setTextViewText(
                                    def.timeId,
                                    IdelisWidgetHelper.formatWaitingTime(dep, config.timeDisplayMode)
                                )
                                val pillBg = when {
                                    !dep.isRealTime -> R.drawable.widget_time_pill_theoretical
                                    dep.minutesUntilDeparture <= 1L -> R.drawable.widget_time_pill_imminent
                                    else -> palette.normalTimePillBgRes
                                }
                                views.setInt(def.timeId, "setBackgroundResource", pillBg)

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
                } finally {
                    if (notifStarted) {
                        WidgetUpdateNotifier.finishWidgetRefreshing(context)
                    }
                }
            }
        }

        private fun cycleFavoriteStopForWidget(context: Context, appWidgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = PreferencesManager(context)
                    val favoriteIds = prefs.getFavoriteStopIds().sorted()
                    if (favoriteIds.isEmpty()) {
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        updateAppWidget(context, appWidgetManager, appWidgetId, postSilentNotification = false)
                        return@launch
                    }

                    val currentConfig = IdelisWidgetPreferences.getWidgetConfig(context, appWidgetId)
                    val database = IdelisDatabase.getInstance(context)
                    val dbStops = database.stopDao().getAllStopsList()
                    val seedStops = GtfsDatabaseSeeder.INITIAL_STOPS

                    fun resolveNameForStopId(id: String): Pair<String, String> {
                        val found = dbStops.firstOrNull { it.stopId == id }
                            ?: seedStops.firstOrNull { it.stopId == id }
                        if (found != null) return found.stopName to (found.stopDesc ?: "")
                        if (id.startsWith("WEB_")) {
                            val clean = id.removePrefix("WEB_").replace('_', ' ').trim()
                            return clean.ifBlank { id } to "Arrêt favori Idelis"
                        }
                        return id to ""
                    }

                    // Construire la rotation : tous les favoris + éventuellement mode GPS proche si un seul favori
                    val cycleIds = if (favoriteIds.size == 1) {
                        listOf(favoriteIds.first(), IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID)
                    } else {
                        favoriteIds
                    }

                    val currentId = if (currentConfig.isDynamicNearest) {
                        IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID
                    } else {
                        currentConfig.stopId
                    }
                    val currentIndex = cycleIds.indexOf(currentId)
                    val nextId = cycleIds[(currentIndex + 1) % cycleIds.size]

                    val nextConfig = if (nextId == IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID) {
                        val fallbackNearestName = IdelisWidgetPreferences.getLastNearestStopName(context, appWidgetId)
                            ?: seedStops.first().stopName
                        currentConfig.copy(
                            stopId = IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID,
                            stopName = fallbackNearestName,
                            stopDesc = "Suivi dynamique selon vos déplacements",
                            isDynamicNearest = true
                        )
                    } else {
                        val (nextName, nextDesc) = resolveNameForStopId(nextId)
                        currentConfig.copy(
                            stopId = nextId,
                            stopName = nextName,
                            stopDesc = nextDesc,
                            isDynamicNearest = false,
                            filteredLines = emptySet() // réinitialise le filtre de ligne spécifique au précédent arrêt
                        )
                    }

                    IdelisWidgetPreferences.saveWidgetConfig(context, nextConfig)
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateAppWidget(
                        context = context,
                        appWidgetManager = appWidgetManager,
                        appWidgetId = appWidgetId,
                        postSilentNotification = false,
                        isManualTap = true
                    )
                } catch (_: Exception) {}
            }
        }

        fun refreshAllWidgets(
            context: Context,
            postSilentNotification: Boolean = false
        ) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, IdelisAppWidgetProvider::class.java)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                if (ids.isNotEmpty()) {
                    for (id in ids) {
                        updateAppWidget(context, appWidgetManager, id, postSilentNotification = postSilentNotification)
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
                    appWidgetId * 100 + 2,
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
                    appWidgetId * 100 + 2,
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
        NotificationChannels.ensureAllChannels(context)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, postSilentNotification = true)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId, postSilentNotification = false)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        IdelisWidgetPreferences.deleteWidgetPrefs(context, appWidgetIds)
        for (id in appWidgetIds) {
            cancelScheduledRefresh(context, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH_WIDGET -> {
                NotificationChannels.ensureAllChannels(context)
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateAppWidget(
                        context = context,
                        appWidgetManager = appWidgetManager,
                        appWidgetId = appWidgetId,
                        postSilentNotification = true,
                        isManualTap = true
                    )
                }
            }
            ACTION_CYCLE_FAVORITE_STOP -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    cycleFavoriteStopForWidget(context, appWidgetId)
                }
            }
        }
    }
}
