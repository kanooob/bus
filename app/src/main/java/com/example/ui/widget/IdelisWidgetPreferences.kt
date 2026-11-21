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
    val themeStyle: WidgetThemeStyle = WidgetThemeStyle.SYSTEM,
    val colorMode: WidgetColorMode = WidgetColorMode.APP_DEFAULT,
    val timeDisplayMode: WidgetTimeDisplayMode = WidgetTimeDisplayMode.COUNTDOWN,
    val densityMode: WidgetDensityMode = WidgetDensityMode.STANDARD,
    val showQuickActions: Boolean = true,
    val showFavoriteSwitch: Boolean = true,
    val filteredLines: Set<String> = emptySet()
)

/**
 * Gestion de la persistance de configuration indépendante par widget (appWidgetId).
 * Permet à chaque widget :
 * - D'avoir un arrêt spécifique fixe ou le mode dynamique "Arrêt le plus proche"
 * - De personnaliser son thème, sa palette (Appli / Material You), sa densité, le format d'heure, les boutons rapides et le filtre de lignes
 */
object IdelisWidgetPreferences {

    private const val PREFS_NAME = "com.example.ui.widget.IdelisWidgetPrefs"
    private const val PREF_PREFIX_STOP_ID = "widget_stop_id_"
    private const val PREF_PREFIX_STOP_NAME = "widget_stop_name_"
    private const val PREF_PREFIX_STOP_DESC = "widget_stop_desc_"
    private const val PREF_PREFIX_IS_DYNAMIC_NEAREST = "widget_is_dynamic_nearest_"
    private const val PREF_PREFIX_THEME_STYLE = "widget_theme_style_"
    private const val PREF_PREFIX_COLOR_MODE = "widget_color_mode_"
    private const val PREF_PREFIX_TIME_MODE = "widget_time_mode_"
    private const val PREF_PREFIX_DENSITY_MODE = "widget_density_mode_"
    private const val PREF_PREFIX_SHOW_ACTIONS = "widget_show_actions_"
    private const val PREF_PREFIX_SHOW_FAV_SWITCH = "widget_show_fav_switch_"
    private const val PREF_PREFIX_FILTERED_LINES = "widget_filtered_lines_"
    private const val PREF_PREFIX_LAST_NEAREST_ID = "widget_last_nearest_id_"
    private const val PREF_PREFIX_LAST_NEAREST_NAME = "widget_last_nearest_name_"

    const val DYNAMIC_NEAREST_STOP_ID = "DYNAMIC_NEAREST_STOP"

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
        getPrefs(context).edit()
            .putString(PREF_PREFIX_STOP_ID + appWidgetId, stopId)
            .putString(PREF_PREFIX_STOP_NAME + appWidgetId, cleanDisplayStopName(stopName))
            .putString(PREF_PREFIX_STOP_DESC + appWidgetId, stopDesc)
            .putBoolean(PREF_PREFIX_IS_DYNAMIC_NEAREST + appWidgetId, isDynamicNearest)
            .apply()
    }

    fun saveWidgetConfig(context: Context, config: WidgetConfig) {
        val id = config.appWidgetId
        getPrefs(context).edit()
            .putString(PREF_PREFIX_STOP_ID + id, config.stopId)
            .putString(PREF_PREFIX_STOP_NAME + id, cleanDisplayStopName(config.stopName))
            .putString(PREF_PREFIX_STOP_DESC + id, config.stopDesc)
            .putBoolean(PREF_PREFIX_IS_DYNAMIC_NEAREST + id, config.isDynamicNearest)
            .putString(PREF_PREFIX_THEME_STYLE + id, config.themeStyle.name)
            .putString(PREF_PREFIX_COLOR_MODE + id, config.colorMode.name)
            .putString(PREF_PREFIX_TIME_MODE + id, config.timeDisplayMode.name)
            .putString(PREF_PREFIX_DENSITY_MODE + id, config.densityMode.name)
            .putBoolean(PREF_PREFIX_SHOW_ACTIONS + id, config.showQuickActions)
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

        return WidgetConfig(
            appWidgetId = appWidgetId,
            stopId = getWidgetStopId(context, appWidgetId),
            stopName = getWidgetStopName(context, appWidgetId),
            stopDesc = getWidgetStopDesc(context, appWidgetId),
            isDynamicNearest = isWidgetDynamicNearest(context, appWidgetId),
            themeStyle = themeStyle,
            colorMode = colorMode,
            timeDisplayMode = timeMode,
            densityMode = densityMode,
            showQuickActions = prefs.getBoolean(PREF_PREFIX_SHOW_ACTIONS + appWidgetId, true),
            showFavoriteSwitch = prefs.getBoolean(PREF_PREFIX_SHOW_FAV_SWITCH + appWidgetId, true),
            filteredLines = (prefs.getStringSet(PREF_PREFIX_FILTERED_LINES + appWidgetId, emptySet()) ?: emptySet())
                .map { normalizeLineCode(it) }
                .filter { it.isNotBlank() }
                .toSet()
        )
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
            editor.remove(PREF_PREFIX_THEME_STYLE + appWidgetId)
            editor.remove(PREF_PREFIX_COLOR_MODE + appWidgetId)
            editor.remove(PREF_PREFIX_TIME_MODE + appWidgetId)
            editor.remove(PREF_PREFIX_DENSITY_MODE + appWidgetId)
            editor.remove(PREF_PREFIX_SHOW_ACTIONS + appWidgetId)
            editor.remove(PREF_PREFIX_SHOW_FAV_SWITCH + appWidgetId)
            editor.remove(PREF_PREFIX_FILTERED_LINES + appWidgetId)
            editor.remove(PREF_PREFIX_LAST_NEAREST_ID + appWidgetId)
            editor.remove(PREF_PREFIX_LAST_NEAREST_NAME + appWidgetId)
        }
        editor.apply()
    }
}
