package com.example.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences

enum class WidgetThemeStyle(val label: String, val shortLabel: String) {
    SYSTEM("Automatique (Système)", "Auto"),
    DARK("Sombre", "Sombre"),
    LIGHT("Clair", "Clair"),
    TRANSLUCENT("Translucide (Verre)", "Verre")
}

enum class WidgetColorMode(val label: String, val shortLabel: String) {
    APP_DEFAULT("Couleur de l'application", "Appli"),
    MATERIAL_YOU("Material You (Couleurs dynamiques)", "Material You")
}

enum class WidgetTimeDisplayMode(val label: String, val shortLabel: String) {
    COUNTDOWN("Temps restant (Dans 4 min)", "Compte à rebours"),
    CLOCK("Heure prévue (14:32)", "Heure exacte"),
    BOTH("Les deux (4 min • 14:32)", "Les deux")
}

enum class WidgetDensityMode(val label: String, val shortLabel: String) {
    STANDARD("Détaillé (avec ponctualité)", "Détaillé"),
    COMPACT("Compact (plus de bus)", "Compact")
}

data class WidgetConfig(
    val appWidgetId: Int,
    val stopId: String = "ID_BOSQUET",
    val stopName: String = "Pôle Bosquet",
    val stopDesc: String = "",
    val isDynamicNearest: Boolean = false,
    val homeStopId: String = stopId,
    val homeStopName: String = stopName,
    val homeStopDesc: String = stopDesc,
    val homeIsDynamicNearest: Boolean = isDynamicNearest,
    val themeStyle: WidgetThemeStyle = WidgetThemeStyle.SYSTEM,
    val colorMode: WidgetColorMode = WidgetColorMode.APP_DEFAULT,
    val timeDisplayMode: WidgetTimeDisplayMode = WidgetTimeDisplayMode.COUNTDOWN,
    val densityMode: WidgetDensityMode = WidgetDensityMode.STANDARD,
    val showRefreshButton: Boolean = true,
    val showMenuButton: Boolean = true,
    val showFavoriteSwitch: Boolean = true,
    val filteredLines: Set<String> = emptySet()
)

data class WidgetDepartureSnapshot(
    val stopId: String,
    val departures: List<com.example.data.model.DepartureComparison>
)

/**
 * Gestion de la persistance de configuration indépendante par widget (appWidgetId).
 * Permet à chaque widget :
 * - D'avoir un arrêt principal fixe ou le mode dynamique "Arrêt le plus proche" (conservé lors de la bascule favoris)
 * - De personnaliser son thème, sa palette (Appli / Material You), sa densité, le format d'heure, chaque bouton rapide et le filtre de lignes
 * - De stocker les prochains départs affichés dans la liste déroulante du widget
 */
object IdelisWidgetPreferences {

    private const val PREFS_NAME = "com.example.ui.widget.IdelisWidgetPrefs"
    private const val PREF_PREFIX_STOP_ID = "widget_stop_id_"
    private const val PREF_PREFIX_STOP_NAME = "widget_stop_name_"
    private const val PREF_PREFIX_STOP_DESC = "widget_stop_desc_"
    private const val PREF_PREFIX_IS_DYNAMIC_NEAREST = "widget_is_dynamic_nearest_"
    private const val PREF_PREFIX_HOME_STOP_ID = "widget_home_stop_id_"
    private const val PREF_PREFIX_HOME_STOP_NAME = "widget_home_stop_name_"
    private const val PREF_PREFIX_HOME_STOP_DESC = "widget_home_stop_desc_"
    private const val PREF_PREFIX_HOME_IS_DYNAMIC_NEAREST = "widget_home_is_dynamic_nearest_"
    private const val PREF_PREFIX_THEME_STYLE = "widget_theme_style_"
    private const val PREF_PREFIX_COLOR_MODE = "widget_color_mode_"
    private const val PREF_PREFIX_TIME_MODE = "widget_time_mode_"
    private const val PREF_PREFIX_DENSITY_MODE = "widget_density_mode_"
    private const val PREF_PREFIX_SHOW_ACTIONS = "widget_show_actions_"
    private const val PREF_PREFIX_SHOW_REFRESH_BTN = "widget_show_refresh_btn_"
    private const val PREF_PREFIX_SHOW_MENU_BTN = "widget_show_menu_btn_"
    private const val PREF_PREFIX_SHOW_FAV_SWITCH = "widget_show_fav_switch_"
    private const val PREF_PREFIX_FILTERED_LINES = "widget_filtered_lines_"
    private const val PREF_PREFIX_LAST_NEAREST_ID = "widget_last_nearest_id_"
    private const val PREF_PREFIX_LAST_NEAREST_NAME = "widget_last_nearest_name_"
    private const val PREF_PREFIX_SNAPSHOT_STOP_ID = "widget_snapshot_stop_id_"
    private const val PREF_PREFIX_SNAPSHOT_DEPARTURES = "widget_snapshot_departures_"

    const val DYNAMIC_NEAREST_STOP_ID = "DYNAMIC_NEAREST_STOP"
    const val MAX_WIDGET_DEPARTURES = 30

    val STANDARD_IDELIS_LINES = listOf(
        "F", "T1", "T2", "T3", "T4",
        "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17",
        "COXI"
    )

    fun normalizeLineCode(raw: String): String {
        val upper = raw.trim().uppercase(java.util.Locale.ROOT)
        if (upper.isBlank()) return ""
        val clean = upper
            .removePrefix("LIGNE ")
            .removePrefix("LIGNE_")
            .removePrefix("LIGNE-")
            .removePrefix("CHRONOBUS ")
            .removePrefix("CHRONOBUS_")
            .removePrefix("CHRONOBUS-")
            .removePrefix("PICTO ")
            .removePrefix("PICTO_")
            .removePrefix("PICTO-")
            .removePrefix("BUS ")
            .trim()
        val noL = clean.removePrefix("L").takeIf {
            clean.startsWith("L") && it.isNotBlank() && it.all { ch -> ch.isDigit() }
        } ?: clean
        return when {
            noL == "F" || noL == "FÉBUS" || noL == "FEBUS" -> "F"
            noL == "COXI" || noL == "COXITIS" || noL == "NAVETTE" || noL == "NCV" -> "COXI"
            noL.all { it.isDigit() } -> noL.trimStart('0').ifEmpty { "0" }
            else -> noL
        }
    }

    private fun lineSortKey(normalizedCode: String): Triple<Int, Int, String> {
        return when {
            normalizedCode == "F" -> Triple(0, 0, normalizedCode)
            normalizedCode.startsWith("T") && normalizedCode.drop(1).all { it.isDigit() } -> {
                Triple(1, normalizedCode.drop(1).toIntOrNull() ?: Int.MAX_VALUE, normalizedCode)
            }
            normalizedCode.all { it.isDigit() } -> {
                Triple(2, normalizedCode.toIntOrNull() ?: Int.MAX_VALUE, normalizedCode)
            }
            normalizedCode == "COXI" -> Triple(3, 0, normalizedCode)
            else -> {
                val digitsSuffix = normalizedCode.takeLastWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
                Triple(4, digitsSuffix, normalizedCode)
            }
        }
    }

    fun isValidCommercialLineCode(raw: String): Boolean {
        val code = normalizeLineCode(raw)
        if (code.isBlank() || code.length > 5) return false
        if (code.any { !it.isLetterOrDigit() }) return false
        return when {
            code == "F" || code == "COXI" -> true
            code.matches(Regex("""^T\d{1,2}$""")) -> true
            code.all { it.isDigit() } -> (code.toIntOrNull() ?: 0) in 5..39
            code.matches(Regex("""^[A-PR-Z]$""")) -> true
            code.matches(Regex("""^[NP]\d{1,2}$""")) -> true
            else -> false
        }
    }

    fun sortLinesNaturally(lines: Iterable<String>): List<String> {
        return lines
            .map { normalizeLineCode(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(
                compareBy<String>(
                    { lineSortKey(it).first },
                    { lineSortKey(it).second },
                    { lineSortKey(it).third }
                )
            )
    }

    /**
     * Construit la boucle de rotation du bouton "Arrêt favori" :
     * l'arrêt sélectionné/configuré à la base (fixe ou GPS proche) reste toujours dans la boucle
     * afin de pouvoir y revenir même lorsqu'il y a 2 favoris ou plus.
     */
    fun buildStopCycleOrder(
        homeStopId: String,
        homeIsDynamicNearest: Boolean,
        favoriteStopIds: Collection<String>
    ): List<String> {
        val homeTarget = if (homeIsDynamicNearest) DYNAMIC_NEAREST_STOP_ID else homeStopId.trim()
        val sortedFavs = favoriteStopIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        return buildList {
            if (homeTarget.isNotBlank()) add(homeTarget)
            sortedFavs.forEach { fav ->
                if (fav != homeTarget) add(fav)
            }
            if (size == 1 && !homeIsDynamicNearest) {
                add(DYNAMIC_NEAREST_STOP_ID)
            }
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun cleanDisplayStopName(raw: String): String {
        return raw.replace("📍", "").trim()
    }

    fun saveWidgetStop(
        context: Context,
        appWidgetId: Int,
        stopId: String,
        stopName: String,
        stopDesc: String = "",
        isDynamicNearest: Boolean = false
    ) {
        val cleanName = cleanDisplayStopName(stopName)
        getPrefs(context).edit()
            .putString(PREF_PREFIX_STOP_ID + appWidgetId, stopId)
            .putString(PREF_PREFIX_STOP_NAME + appWidgetId, cleanName)
            .putString(PREF_PREFIX_STOP_DESC + appWidgetId, stopDesc)
            .putBoolean(PREF_PREFIX_IS_DYNAMIC_NEAREST + appWidgetId, isDynamicNearest)
            .putString(PREF_PREFIX_HOME_STOP_ID + appWidgetId, stopId)
            .putString(PREF_PREFIX_HOME_STOP_NAME + appWidgetId, cleanName)
            .putString(PREF_PREFIX_HOME_STOP_DESC + appWidgetId, stopDesc)
            .putBoolean(PREF_PREFIX_HOME_IS_DYNAMIC_NEAREST + appWidgetId, isDynamicNearest)
            .apply()
    }

    fun saveWidgetConfig(context: Context, config: WidgetConfig) {
        val id = config.appWidgetId
        getPrefs(context).edit()
            .putString(PREF_PREFIX_STOP_ID + id, config.stopId)
            .putString(PREF_PREFIX_STOP_NAME + id, cleanDisplayStopName(config.stopName))
            .putString(PREF_PREFIX_STOP_DESC + id, config.stopDesc)
            .putBoolean(PREF_PREFIX_IS_DYNAMIC_NEAREST + id, config.isDynamicNearest)
            .putString(PREF_PREFIX_HOME_STOP_ID + id, config.homeStopId)
            .putString(PREF_PREFIX_HOME_STOP_NAME + id, cleanDisplayStopName(config.homeStopName))
            .putString(PREF_PREFIX_HOME_STOP_DESC + id, config.homeStopDesc)
            .putBoolean(PREF_PREFIX_HOME_IS_DYNAMIC_NEAREST + id, config.homeIsDynamicNearest)
            .putString(PREF_PREFIX_THEME_STYLE + id, config.themeStyle.name)
            .putString(PREF_PREFIX_COLOR_MODE + id, config.colorMode.name)
            .putString(PREF_PREFIX_TIME_MODE + id, config.timeDisplayMode.name)
            .putString(PREF_PREFIX_DENSITY_MODE + id, config.densityMode.name)
            .putBoolean(PREF_PREFIX_SHOW_REFRESH_BTN + id, config.showRefreshButton)
            .putBoolean(PREF_PREFIX_SHOW_MENU_BTN + id, config.showMenuButton)
            .putBoolean(PREF_PREFIX_SHOW_FAV_SWITCH + id, config.showFavoriteSwitch)
            .putStringSet(
                PREF_PREFIX_FILTERED_LINES + id,
                config.filteredLines.map { normalizeLineCode(it) }.filter { it.isNotBlank() }.toSet()
            )
            .apply()
    }

    fun getWidgetConfig(context: Context, appWidgetId: Int): WidgetConfig {
        val prefs = getPrefs(context)
        val themeRaw = prefs.getString(PREF_PREFIX_THEME_STYLE + appWidgetId, null)
        val colorRaw = prefs.getString(PREF_PREFIX_COLOR_MODE + appWidgetId, null)
        val timeRaw = prefs.getString(PREF_PREFIX_TIME_MODE + appWidgetId, null)
        val densityRaw = prefs.getString(PREF_PREFIX_DENSITY_MODE + appWidgetId, null)

        val themeStyle = enumValues<WidgetThemeStyle>().firstOrNull { it.name == themeRaw }
            ?: WidgetThemeStyle.SYSTEM
        val colorMode = enumValues<WidgetColorMode>().firstOrNull { it.name == colorRaw }
            ?: WidgetColorMode.APP_DEFAULT
        val timeMode = enumValues<WidgetTimeDisplayMode>().firstOrNull { it.name == timeRaw }
            ?: WidgetTimeDisplayMode.COUNTDOWN
        val densityMode = enumValues<WidgetDensityMode>().firstOrNull { it.name == densityRaw }
            ?: WidgetDensityMode.STANDARD

        val activeStopId = getWidgetStopId(context, appWidgetId)
        val activeStopName = getWidgetStopName(context, appWidgetId)
        val activeStopDesc = getWidgetStopDesc(context, appWidgetId)
        val activeDynamic = isWidgetDynamicNearest(context, appWidgetId)

        val homeStopId = prefs.getString(PREF_PREFIX_HOME_STOP_ID + appWidgetId, null)
            ?.takeIf { it.isNotBlank() }
            ?: activeStopId
        val homeStopName = prefs.getString(PREF_PREFIX_HOME_STOP_NAME + appWidgetId, null)
            ?.let(::cleanDisplayStopName)
            ?.takeIf { it.isNotBlank() }
            ?: activeStopName
        val homeStopDesc = prefs.getString(PREF_PREFIX_HOME_STOP_DESC + appWidgetId, null)
            ?: activeStopDesc
        val homeIsDynamic = if (prefs.contains(PREF_PREFIX_HOME_IS_DYNAMIC_NEAREST + appWidgetId)) {
            prefs.getBoolean(PREF_PREFIX_HOME_IS_DYNAMIC_NEAREST + appWidgetId, false) ||
                homeStopId == DYNAMIC_NEAREST_STOP_ID
        } else {
            activeDynamic
        }

        val legacyQuickActions = prefs.getBoolean(PREF_PREFIX_SHOW_ACTIONS + appWidgetId, true)
        val showRefreshButton = if (prefs.contains(PREF_PREFIX_SHOW_REFRESH_BTN + appWidgetId)) {
            prefs.getBoolean(PREF_PREFIX_SHOW_REFRESH_BTN + appWidgetId, true)
        } else {
            legacyQuickActions
        }
        val showMenuButton = if (prefs.contains(PREF_PREFIX_SHOW_MENU_BTN + appWidgetId)) {
            prefs.getBoolean(PREF_PREFIX_SHOW_MENU_BTN + appWidgetId, true)
        } else {
            legacyQuickActions
        }

        return WidgetConfig(
            appWidgetId = appWidgetId,
            stopId = activeStopId,
            stopName = activeStopName,
            stopDesc = activeStopDesc,
            isDynamicNearest = activeDynamic,
            homeStopId = homeStopId,
            homeStopName = homeStopName,
            homeStopDesc = homeStopDesc,
            homeIsDynamicNearest = homeIsDynamic,
            themeStyle = themeStyle,
            colorMode = colorMode,
            timeDisplayMode = timeMode,
            densityMode = densityMode,
            showRefreshButton = showRefreshButton,
            showMenuButton = showMenuButton,
            showFavoriteSwitch = prefs.getBoolean(PREF_PREFIX_SHOW_FAV_SWITCH + appWidgetId, true),
            filteredLines = (prefs.getStringSet(PREF_PREFIX_FILTERED_LINES + appWidgetId, emptySet()) ?: emptySet())
                .map { normalizeLineCode(it) }
                .filter { it.isNotBlank() }
                .toSet()
        )
    }

    fun saveWidgetDeparturesSnapshot(
        context: Context,
        appWidgetId: Int,
        stopId: String,
        departures: List<com.example.data.model.DepartureComparison>
    ) {
        val array = org.json.JSONArray()
        departures.take(MAX_WIDGET_DEPARTURES).forEach { dep ->
            val obj = org.json.JSONObject().apply {
                put("id", dep.id)
                put("lineRef", dep.lineRef)
                put("lineName", dep.lineName)
                put("destinationName", dep.destinationName)
                put("iconUrl", dep.iconUrl ?: "")
                put("routeColorHex", dep.routeColorHex)
                put("routeTextColorHex", dep.routeTextColorHex)
                put("aimedTime", dep.aimedDepartureTimeFormatted)
                put("expectedTime", dep.expectedDepartureTimeFormatted ?: "")
                put("delayMinutes", dep.delayMinutes)
                put("status", dep.status.name)
                put("isRealTime", dep.isRealTime)
                put("minutesUntilDeparture", dep.minutesUntilDeparture)
                put("comparisonLabel", dep.comparisonLabel)
            }
            array.put(obj)
        }
        getPrefs(context).edit()
            .putString(PREF_PREFIX_SNAPSHOT_STOP_ID + appWidgetId, stopId)
            .putString(PREF_PREFIX_SNAPSHOT_DEPARTURES + appWidgetId, array.toString())
            .apply()
    }

    fun clearWidgetDeparturesSnapshot(context: Context, appWidgetId: Int) {
        getPrefs(context).edit()
            .remove(PREF_PREFIX_SNAPSHOT_STOP_ID + appWidgetId)
            .remove(PREF_PREFIX_SNAPSHOT_DEPARTURES + appWidgetId)
            .apply()
    }

    fun getWidgetDeparturesSnapshot(context: Context, appWidgetId: Int): WidgetDepartureSnapshot {
        val prefs = getPrefs(context)
        val stopId = prefs.getString(PREF_PREFIX_SNAPSHOT_STOP_ID + appWidgetId, "") ?: ""
        val raw = prefs.getString(PREF_PREFIX_SNAPSHOT_DEPARTURES + appWidgetId, null)
            ?: return WidgetDepartureSnapshot(stopId = stopId, departures = emptyList())
        val list = try {
            val array = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val statusName = obj.optString("status", com.example.data.model.DepartureStatus.ON_TIME.name)
                    val status = enumValues<com.example.data.model.DepartureStatus>()
                        .firstOrNull { it.name == statusName }
                        ?: com.example.data.model.DepartureStatus.ON_TIME
                    val expected = obj.optString("expectedTime", "").takeIf { it.isNotBlank() }
                    val iconUrl = obj.optString("iconUrl", "").takeIf { it.isNotBlank() }
                    add(
                        com.example.data.model.DepartureComparison(
                            id = obj.optString("id", "dep_$i"),
                            lineRef = obj.optString("lineRef", ""),
                            lineName = obj.optString("lineName", ""),
                            destinationName = obj.optString("destinationName", ""),
                            iconUrl = iconUrl,
                            routeColorHex = obj.optString("routeColorHex", "0288D1"),
                            routeTextColorHex = obj.optString("routeTextColorHex", "FFFFFF"),
                            aimedDepartureTimeFormatted = obj.optString("aimedTime", "--:--"),
                            expectedDepartureTimeFormatted = expected,
                            delayMinutes = obj.optInt("delayMinutes", 0),
                            status = status,
                            isRealTime = obj.optBoolean("isRealTime", false),
                            minutesUntilDeparture = obj.optLong("minutesUntilDeparture", 0L),
                            comparisonLabel = obj.optString("comparisonLabel", "")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        return WidgetDepartureSnapshot(stopId = stopId, departures = list)
    }

    fun saveLastNearestStop(context: Context, appWidgetId: Int, stopId: String, stopName: String) {
        getPrefs(context).edit()
            .putString(PREF_PREFIX_LAST_NEAREST_ID + appWidgetId, stopId)
            .putString(PREF_PREFIX_LAST_NEAREST_NAME + appWidgetId, cleanDisplayStopName(stopName))
            .apply()
    }

    fun getLastNearestStopId(context: Context, appWidgetId: Int): String? {
        return getPrefs(context).getString(PREF_PREFIX_LAST_NEAREST_ID + appWidgetId, null)
            ?.takeIf { it.isNotBlank() && it != DYNAMIC_NEAREST_STOP_ID }
    }

    fun getLastNearestStopName(context: Context, appWidgetId: Int): String? {
        return getPrefs(context).getString(PREF_PREFIX_LAST_NEAREST_NAME + appWidgetId, null)
            ?.let(::cleanDisplayStopName)
            ?.takeIf { it.isNotBlank() && !it.equals("Arrêt le plus proche", ignoreCase = true) }
    }

    fun getActiveWidgetIds(context: Context): IntArray {
        return try {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, IdelisAppWidgetProvider::class.java)
            manager.getAppWidgetIds(component)
        } catch (_: Exception) {
            intArrayOf()
        }
    }

    fun isWidgetDynamicNearest(context: Context, appWidgetId: Int): Boolean {
        val isDynamic = getPrefs(context).getBoolean(PREF_PREFIX_IS_DYNAMIC_NEAREST + appWidgetId, false)
        val stopId = getWidgetStopId(context, appWidgetId)
        return isDynamic || stopId == DYNAMIC_NEAREST_STOP_ID
    }

    fun setWidgetDynamicNearest(context: Context, appWidgetId: Int, enabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(PREF_PREFIX_IS_DYNAMIC_NEAREST + appWidgetId, enabled)
            .apply()
    }

    fun getWidgetStopId(context: Context, appWidgetId: Int): String {
        return getPrefs(context).getString(PREF_PREFIX_STOP_ID + appWidgetId, null) ?: "ID_BOSQUET"
    }

    fun getWidgetStopName(context: Context, appWidgetId: Int): String {
        val raw = getPrefs(context).getString(PREF_PREFIX_STOP_NAME + appWidgetId, null) ?: "Pôle Bosquet"
        return cleanDisplayStopName(raw).ifBlank { "Pôle Bosquet" }
    }

    fun getWidgetStopDesc(context: Context, appWidgetId: Int): String {
        return getPrefs(context).getString(PREF_PREFIX_STOP_DESC + appWidgetId, "") ?: ""
    }

    fun isWidgetConfigured(context: Context, appWidgetId: Int): Boolean {
        return getPrefs(context).contains(PREF_PREFIX_STOP_ID + appWidgetId)
    }

    fun deleteWidgetPrefs(context: Context, appWidgetIds: IntArray) {
        val editor = getPrefs(context).edit()
        for (appWidgetId in appWidgetIds) {
            editor.remove(PREF_PREFIX_STOP_ID + appWidgetId)
            editor.remove(PREF_PREFIX_STOP_NAME + appWidgetId)
            editor.remove(PREF_PREFIX_STOP_DESC + appWidgetId)
            editor.remove(PREF_PREFIX_IS_DYNAMIC_NEAREST + appWidgetId)
            editor.remove(PREF_PREFIX_HOME_STOP_ID + appWidgetId)
            editor.remove(PREF_PREFIX_HOME_STOP_NAME + appWidgetId)
            editor.remove(PREF_PREFIX_HOME_STOP_DESC + appWidgetId)
            editor.remove(PREF_PREFIX_HOME_IS_DYNAMIC_NEAREST + appWidgetId)
            editor.remove(PREF_PREFIX_THEME_STYLE + appWidgetId)
            editor.remove(PREF_PREFIX_COLOR_MODE + appWidgetId)
            editor.remove(PREF_PREFIX_TIME_MODE + appWidgetId)
            editor.remove(PREF_PREFIX_DENSITY_MODE + appWidgetId)
            editor.remove(PREF_PREFIX_SHOW_ACTIONS + appWidgetId)
            editor.remove(PREF_PREFIX_SHOW_REFRESH_BTN + appWidgetId)
            editor.remove(PREF_PREFIX_SHOW_MENU_BTN + appWidgetId)
            editor.remove(PREF_PREFIX_SHOW_FAV_SWITCH + appWidgetId)
            editor.remove(PREF_PREFIX_FILTERED_LINES + appWidgetId)
            editor.remove(PREF_PREFIX_LAST_NEAREST_ID + appWidgetId)
            editor.remove(PREF_PREFIX_LAST_NEAREST_NAME + appWidgetId)
            editor.remove(PREF_PREFIX_SNAPSHOT_STOP_ID + appWidgetId)
            editor.remove(PREF_PREFIX_SNAPSHOT_DEPARTURES + appWidgetId)
        }
        editor.apply()
    }
}
