package com.example.ui.widget

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestion de la persistance de configuration indépendante par widget (appWidgetId).
 * Permet à chaque widget :
 * - D'avoir un arrêt spécifique fixe
 * - OU de fonctionner en mode "Arrêt le plus proche" dynamique (suit les déplacements et s'actualise plus vite en mouvement)
 */
object IdelisWidgetPreferences {

    private const val PREFS_NAME = "com.example.ui.widget.IdelisWidgetPrefs"
    private const val PREF_PREFIX_STOP_ID = "widget_stop_id_"
    private const val PREF_PREFIX_STOP_NAME = "widget_stop_name_"
    private const val PREF_PREFIX_STOP_DESC = "widget_stop_desc_"
    private const val PREF_PREFIX_IS_DYNAMIC_NEAREST = "widget_is_dynamic_nearest_"

    const val DYNAMIC_NEAREST_STOP_ID = "DYNAMIC_NEAREST_STOP"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
            .putString(PREF_PREFIX_STOP_NAME + appWidgetId, stopName)
            .putString(PREF_PREFIX_STOP_DESC + appWidgetId, stopDesc)
            .putBoolean(PREF_PREFIX_IS_DYNAMIC_NEAREST + appWidgetId, isDynamicNearest)
            .apply()
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
        return getPrefs(context).getString(PREF_PREFIX_STOP_NAME + appWidgetId, null) ?: "Pôle Bosquet"
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
        }
        editor.apply()
    }
}
