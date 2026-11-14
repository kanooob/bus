package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppUpdateCheckFrequency
import com.example.data.model.GtfsSyncFrequency
import com.example.data.model.InAppRefreshFrequency
import com.example.data.model.PrimaryColorMode
import com.example.data.model.ThemeMode
import com.example.data.model.TrafficRefreshFrequency
import com.example.data.model.UpdateNetworkMode
import com.example.data.model.WidgetLocationFrequency
import com.example.data.model.WidgetRefreshFrequency
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gestionnaire de persistance des préférences utilisateur :
 * - Apparence : Thème (Clair / Sombre / Système), Couleur principale (Material You / Couleur de l'application)
 * - Données et actualisation : Fréquence app, Fréquence widgets, Géolocalisation widget « Arrêt proche », GTFS
 * - Mises à jour de l'application : Vérification automatique, Réseau autorisé
 * - Avancé : Sauvegarde/restauration JSON, Configuration réseau / API (SIRI / GTFS), GitHub
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "idelis_user_prefs"
        private const val KEY_LAST_STOP_ID = "last_selected_stop_id"
        private const val KEY_DARK_THEME = "dark_theme_enabled"
        private const val KEY_SIMULATION_MODE = "simulation_mode_enabled"
        private const val KEY_AUTO_REFRESH_PAUSED = "auto_refresh_paused"
        private const val KEY_PERIODIC_SYNC = "periodic_sync_enabled"
        private const val KEY_FAVORITE_STOP_IDS = "favorite_stop_ids"
        private const val KEY_LAST_GTFS_SYNC_TIME = "last_gtfs_sync_timestamp"
        private const val KEY_LAST_APP_UPDATE_CHECK_TIME = "last_app_update_check_timestamp"
        private const val KEY_LAST_NOTIFIED_UPDATE_TAG = "last_notified_update_tag"
        private const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"

        // Hiérarchie des paramètres
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PRIMARY_COLOR_MODE = "primary_color_mode"
        private const val KEY_IN_APP_REFRESH_FREQ = "in_app_refresh_frequency"
        private const val KEY_WIDGET_REFRESH_FREQ = "widget_refresh_frequency"
        private const val KEY_WIDGET_LOCATION_FREQ = "widget_location_frequency"
        private const val KEY_GTFS_SYNC_FREQ = "gtfs_sync_frequency"
        private const val KEY_TRAFFIC_REFRESH_FREQ = "traffic_refresh_frequency"
        private const val KEY_TRAFFIC_NOTIFICATIONS_ENABLED = "traffic_notifications_enabled"
        private const val KEY_WIDGET_UPDATE_NOTIFICATIONS_ENABLED = "widget_update_notifications_enabled"
        private const val KEY_LAST_TRAFFIC_REFRESH_TIME = "last_traffic_refresh_timestamp"
        private const val KEY_KNOWN_TRAFFIC_ALERT_KEYS = "known_traffic_alert_keys"
        private const val KEY_TRAFFIC_BASELINE_INITIALIZED = "traffic_baseline_initialized"
        private const val KEY_APP_UPDATE_CHECK_FREQ = "app_update_check_frequency"
        private const val KEY_UPDATE_NETWORK_MODE = "update_network_mode"

        // URLs & Requêtes configurables (Paramètres > Avancé)
        private const val KEY_URL_WEB_MONITORING = "url_web_monitoring"
        private const val KEY_URL_GTFS_ZIP = "url_gtfs_zip"
        private const val KEY_URL_SIRI_SOAP = "url_siri_soap"
        private const val KEY_QUERY_TYPE_PARAM = "query_type_param"

        // GitHub versions
        private const val KEY_GITHUB_REPO = "github_repo_target"
        private const val KEY_GITHUB_TAG = "github_tag_target"

        // Compteur d'utilisateurs (statistiques quotidiennes)
        private const val KEY_USAGE_STATS_ENABLED = "usage_stats_enabled"
        private const val KEY_LAST_USAGE_STATS_PING_DATE = "last_usage_stats_ping_date"

        const val DEFAULT_WEB_URL = "https://www.idelis.fr/?type=777"
        private const val LEGACY_GTFS_ZIP_URL = "https://opendata.idelis.fr/?file=GTFS_MultiRoutes/Data.GTFS.zip"
        const val DEFAULT_GTFS_ZIP_URL = "https://data.idelis.fr/api/explore/v2.1/catalog/datasets/fichier-des-donnees-theoriques-du-reseau-idelis-au-format-gtfs/alternative_exports/https_opendata_idelis_fr_file_gtfs_multiroutes_data_gtfs_zip/"
        const val DEFAULT_SIRI_URL = "https://api.idelis.fr/siri/2.0"
        const val DEFAULT_QUERY_TYPE = "777"
        const val DEFAULT_GITHUB_REPO = "https://github.com/kanooob/bus"
        const val DEFAULT_GITHUB_TAG = "latest"
    }

    private inline fun <reified T : Enum<T>> getEnum(key: String, default: T): T {
        val raw = prefs.getString(key, null) ?: return default
        return enumValues<T>().firstOrNull { it.name == raw } ?: default
    }

    // 1. Apparence
    fun getThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, null)
        if (raw != null) {
            return enumValues<ThemeMode>().firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
        }
        return if (prefs.contains(KEY_DARK_THEME)) {
            if (prefs.getBoolean(KEY_DARK_THEME, false)) ThemeMode.DARK else ThemeMode.LIGHT
        } else {
            ThemeMode.SYSTEM
        }
    }

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit()
            .putString(KEY_THEME_MODE, mode.name)
            .putBoolean(KEY_DARK_THEME, mode == ThemeMode.DARK)
            .apply()
    }

    fun getPrimaryColorMode(): PrimaryColorMode = getEnum(KEY_PRIMARY_COLOR_MODE, PrimaryColorMode.APP_DEFAULT)

    fun savePrimaryColorMode(mode: PrimaryColorMode) {
        prefs.edit().putString(KEY_PRIMARY_COLOR_MODE, mode.name).apply()
    }

    // 2. Données et actualisation
    fun getInAppRefreshFrequency(): InAppRefreshFrequency =
        getEnum(KEY_IN_APP_REFRESH_FREQ, InAppRefreshFrequency.EVERY_1_MIN)

    fun saveInAppRefreshFrequency(freq: InAppRefreshFrequency) {
        prefs.edit()
            .putString(KEY_IN_APP_REFRESH_FREQ, freq.name)
            .putBoolean(KEY_AUTO_REFRESH_PAUSED, freq == InAppRefreshFrequency.ON_OPEN_OR_STOP_CHANGE)
            .apply()
    }

    fun getWidgetRefreshFrequency(): WidgetRefreshFrequency =
        getEnum(KEY_WIDGET_REFRESH_FREQ, WidgetRefreshFrequency.EVERY_15_MIN)

    fun saveWidgetRefreshFrequency(freq: WidgetRefreshFrequency) {
        prefs.edit().putString(KEY_WIDGET_REFRESH_FREQ, freq.name).apply()
    }

    fun getWidgetLocationFrequency(): WidgetLocationFrequency =
        getEnum(KEY_WIDGET_LOCATION_FREQ, WidgetLocationFrequency.EVERY_15_MIN)

    fun saveWidgetLocationFrequency(freq: WidgetLocationFrequency) {
        prefs.edit().putString(KEY_WIDGET_LOCATION_FREQ, freq.name).apply()
    }

    fun getGtfsSyncFrequency(): GtfsSyncFrequency =
        getEnum(KEY_GTFS_SYNC_FREQ, GtfsSyncFrequency.DAILY_ON_OPEN)

    fun saveGtfsSyncFrequency(freq: GtfsSyncFrequency) {
        prefs.edit()
            .putString(KEY_GTFS_SYNC_FREQ, freq.name)
            .putBoolean(KEY_PERIODIC_SYNC, freq != GtfsSyncFrequency.NEVER)
            .apply()
    }

    fun getTrafficRefreshFrequency(): TrafficRefreshFrequency =
        getEnum(KEY_TRAFFIC_REFRESH_FREQ, TrafficRefreshFrequency.EVERY_2_HOURS)

    fun saveTrafficRefreshFrequency(freq: TrafficRefreshFrequency) {
        prefs.edit().putString(KEY_TRAFFIC_REFRESH_FREQ, freq.name).apply()
    }

    fun isTrafficNotificationsEnabled(): Boolean =
        prefs.getBoolean(KEY_TRAFFIC_NOTIFICATIONS_ENABLED, false)

    fun setTrafficNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRAFFIC_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun isWidgetUpdateNotificationsEnabled(): Boolean =
        prefs.getBoolean(KEY_WIDGET_UPDATE_NOTIFICATIONS_ENABLED, true)

    fun setWidgetUpdateNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIDGET_UPDATE_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun getLastTrafficRefreshTimestamp(): Long =
        prefs.getLong(KEY_LAST_TRAFFIC_REFRESH_TIME, 0L)

    fun saveLastTrafficRefreshTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_TRAFFIC_REFRESH_TIME, timestamp).apply()
    }

    fun getKnownTrafficAlertKeys(): Set<String> =
        prefs.getStringSet(KEY_KNOWN_TRAFFIC_ALERT_KEYS, emptySet()) ?: emptySet()

    fun saveKnownTrafficAlertKeys(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_KNOWN_TRAFFIC_ALERT_KEYS, keys).apply()
    }

    fun hasTrafficBaselineInitialized(): Boolean =
        prefs.getBoolean(KEY_TRAFFIC_BASELINE_INITIALIZED, false)

    fun setTrafficBaselineInitialized(initialized: Boolean) {
        prefs.edit().putBoolean(KEY_TRAFFIC_BASELINE_INITIALIZED, initialized).apply()
    }

    // 3. Mises à jour de l'application
    fun getAppUpdateCheckFrequency(): AppUpdateCheckFrequency =
        getEnum(KEY_APP_UPDATE_CHECK_FREQ, AppUpdateCheckFrequency.DAILY_ON_OPEN)

    fun saveAppUpdateCheckFrequency(freq: AppUpdateCheckFrequency) {
        prefs.edit().putString(KEY_APP_UPDATE_CHECK_FREQ, freq.name).apply()
    }

    fun getUpdateNetworkMode(): UpdateNetworkMode =
        getEnum(KEY_UPDATE_NETWORK_MODE, UpdateNetworkMode.WIFI_AND_CELLULAR)

    fun saveUpdateNetworkMode(mode: UpdateNetworkMode) {
        prefs.edit().putString(KEY_UPDATE_NETWORK_MODE, mode.name).apply()
    }

    fun getLastAppUpdateCheckTimestamp(): Long = prefs.getLong(KEY_LAST_APP_UPDATE_CHECK_TIME, 0L)

    fun saveLastAppUpdateCheckTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_APP_UPDATE_CHECK_TIME, timestamp).apply()
    }

    fun getLastNotifiedUpdateTag(): String? = prefs.getString(KEY_LAST_NOTIFIED_UPDATE_TAG, null)

    fun saveLastNotifiedUpdateTag(tag: String) {
        prefs.edit().putString(KEY_LAST_NOTIFIED_UPDATE_TAG, tag).apply()
    }

    fun hasAskedNotificationPermission(): Boolean =
        prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false)

    fun setNotificationPermissionAsked() {
        prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true).apply()
    }

    // Favoris et dernier arrêt
    fun getLastSelectedStopId(): String? = prefs.getString(KEY_LAST_STOP_ID, null)

    fun saveLastSelectedStopId(stopId: String) {
        prefs.edit().putString(KEY_LAST_STOP_ID, stopId).apply()
    }

    fun getFavoriteStopIds(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITE_STOP_IDS, emptySet()) ?: emptySet()
    }

    fun saveFavoriteStopIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITE_STOP_IDS, ids).apply()
    }

    fun isDarkThemeEnabled(): Boolean = getThemeMode() == ThemeMode.DARK

    fun setDarkThemeEnabled(enabled: Boolean) {
        saveThemeMode(if (enabled) ThemeMode.DARK else ThemeMode.LIGHT)
    }

    fun isSimulationModeEnabled(): Boolean = prefs.getBoolean(KEY_SIMULATION_MODE, true)

    fun setSimulationModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SIMULATION_MODE, enabled).apply()
    }

    fun isAutoRefreshPaused(): Boolean =
        getInAppRefreshFrequency() == InAppRefreshFrequency.ON_OPEN_OR_STOP_CHANGE ||
            prefs.getBoolean(KEY_AUTO_REFRESH_PAUSED, false)

    fun setAutoRefreshPaused(paused: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REFRESH_PAUSED, paused).apply()
    }

    fun isPeriodicSyncEnabled(): Boolean = getGtfsSyncFrequency() != GtfsSyncFrequency.NEVER

    fun setPeriodicSyncEnabled(enabled: Boolean) {
        saveGtfsSyncFrequency(if (enabled) GtfsSyncFrequency.DAILY_ON_OPEN else GtfsSyncFrequency.NEVER)
    }

    fun getLastGtfsSyncTimestamp(): Long = prefs.getLong(KEY_LAST_GTFS_SYNC_TIME, 0L)

    fun saveLastGtfsSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_GTFS_SYNC_TIME, timestamp).apply()
    }

    // URLs & Requêtes avancées
    fun getWebMonitoringUrl(): String = prefs.getString(KEY_URL_WEB_MONITORING, DEFAULT_WEB_URL) ?: DEFAULT_WEB_URL

    fun saveWebMonitoringUrl(url: String) {
        prefs.edit().putString(KEY_URL_WEB_MONITORING, url.trim()).apply()
    }

    fun getGtfsZipUrl(): String {
        val saved = prefs.getString(KEY_URL_GTFS_ZIP, DEFAULT_GTFS_ZIP_URL) ?: DEFAULT_GTFS_ZIP_URL
        return if (saved.isBlank() || saved == LEGACY_GTFS_ZIP_URL) {
            DEFAULT_GTFS_ZIP_URL
        } else {
            saved
        }
    }

    fun saveGtfsZipUrl(url: String) {
        prefs.edit().putString(KEY_URL_GTFS_ZIP, url.trim()).apply()
    }

    fun getSiriSoapUrl(): String = prefs.getString(KEY_URL_SIRI_SOAP, DEFAULT_SIRI_URL) ?: DEFAULT_SIRI_URL

    fun saveSiriSoapUrl(url: String) {
        prefs.edit().putString(KEY_URL_SIRI_SOAP, url.trim()).apply()
    }

    fun getQueryTypeParam(): String = prefs.getString(KEY_QUERY_TYPE_PARAM, DEFAULT_QUERY_TYPE) ?: DEFAULT_QUERY_TYPE

    fun saveQueryTypeParam(param: String) {
        prefs.edit().putString(KEY_QUERY_TYPE_PARAM, param.trim()).apply()
    }

    fun resetAdvancedUrlsToDefaults() {
        prefs.edit()
            .putString(KEY_URL_WEB_MONITORING, DEFAULT_WEB_URL)
            .putString(KEY_URL_GTFS_ZIP, DEFAULT_GTFS_ZIP_URL)
            .putString(KEY_URL_SIRI_SOAP, DEFAULT_SIRI_URL)
            .putString(KEY_QUERY_TYPE_PARAM, DEFAULT_QUERY_TYPE)
            .apply()
    }

    // Gestion GitHub
    fun getGithubRepo(): String {
        val saved = prefs.getString(KEY_GITHUB_REPO, DEFAULT_GITHUB_REPO) ?: DEFAULT_GITHUB_REPO
        return if (saved.contains("galaxie-bus/galaxie-bus", ignoreCase = true) || saved.isBlank()) {
            DEFAULT_GITHUB_REPO
        } else {
            saved
        }
    }

    fun saveGithubRepo(repo: String) {
        prefs.edit().putString(KEY_GITHUB_REPO, repo.trim()).apply()
    }

    fun getGithubTag(): String = prefs.getString(KEY_GITHUB_TAG, DEFAULT_GITHUB_TAG) ?: DEFAULT_GITHUB_TAG

    fun saveGithubTag(tag: String) {
        prefs.edit().putString(KEY_GITHUB_TAG, tag.trim()).apply()
    }

    // Compteur d'utilisateurs (activé par défaut)
    fun isUsageStatsEnabled(): Boolean = prefs.getBoolean(KEY_USAGE_STATS_ENABLED, true)

    fun setUsageStatsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USAGE_STATS_ENABLED, enabled).apply()
    }

    fun getLastUsageStatsPingDate(): String? = prefs.getString(KEY_LAST_USAGE_STATS_PING_DATE, null)

    fun saveLastUsageStatsPingDate(dateKey: String) {
        prefs.edit().putString(KEY_LAST_USAGE_STATS_PING_DATE, dateKey).apply()
    }

    // 4. Avancé : Sauvegarde et restauration JSON
    fun exportBackupJson(): String {
        val favArray = JSONArray()
        getFavoriteStopIds().sorted().forEach { favArray.put(it) }

        val settingsObj = JSONObject().apply {
            put("themeMode", getThemeMode().name)
            put("primaryColorMode", getPrimaryColorMode().name)
            put("inAppRefreshFrequency", getInAppRefreshFrequency().name)
            put("widgetRefreshFrequency", getWidgetRefreshFrequency().name)
            put("widgetLocationFrequency", getWidgetLocationFrequency().name)
            put("gtfsSyncFrequency", getGtfsSyncFrequency().name)
            put("trafficRefreshFrequency", getTrafficRefreshFrequency().name)
            put("trafficNotificationsEnabled", isTrafficNotificationsEnabled())
            put("widgetUpdateNotificationsEnabled", isWidgetUpdateNotificationsEnabled())
            put("appUpdateCheckFrequency", getAppUpdateCheckFrequency().name)
            put("updateNetworkMode", getUpdateNetworkMode().name)
            put("webMonitoringUrl", getWebMonitoringUrl())
            put("gtfsZipUrl", getGtfsZipUrl())
            put("siriSoapUrl", getSiriSoapUrl())
            put("queryTypeParam", getQueryTypeParam())
            put("githubRepo", getGithubRepo())
            put("githubTag", getGithubTag())
            put("usageStatsEnabled", isUsageStatsEnabled())
        }

        val root = JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("lastSelectedStopId", getLastSelectedStopId() ?: "")
            put("favoriteStopIds", favArray)
            put("settings", settingsObj)
        }
        return root.toString(2)
    }

    fun importBackupJson(jsonString: String): Result<Int> {
        return try {
            val root = JSONObject(jsonString)
            if (!root.has("settings") && !root.has("favoriteStopIds")) {
                return Result.failure(IllegalArgumentException("Fichier JSON invalide : champs manquants"))
            }

            var favoritesCount = 0
            if (root.has("favoriteStopIds")) {
                val favArray = root.getJSONArray("favoriteStopIds")
                val restoredFavs = mutableSetOf<String>()
                for (i in 0 until favArray.length()) {
                    val id = favArray.optString(i, "").trim()
                    if (id.isNotBlank()) restoredFavs.add(id)
                }
                saveFavoriteStopIds(restoredFavs)
                favoritesCount = restoredFavs.size
            }

            val lastStop = root.optString("lastSelectedStopId", "").trim()
            if (lastStop.isNotBlank()) {
                saveLastSelectedStopId(lastStop)
            }

            val settingsObj = root.optJSONObject("settings")
            if (settingsObj != null) {
                settingsObj.optString("themeMode").takeIf { it.isNotBlank() }?.let { raw ->
                    enumValues<ThemeMode>().firstOrNull { it.name == raw }?.let { saveThemeMode(it) }
                }
                settingsObj.optString("primaryColorMode").takeIf { it.isNotBlank() }?.let { raw ->
                    enumValues<PrimaryColorMode>().firstOrNull { it.name == raw }?.let { savePrimaryColorMode(it) }
                }
                settingsObj.optString("inAppRefreshFrequency").takeIf { it.isNotBlank() }?.let { raw ->
                    enumValues<InAppRefreshFrequency>().firstOrNull { it.name == raw }?.let { saveInAppRefreshFrequency(it) }
                }
                settingsObj.optString("widgetRefreshFrequency").takeIf { it.isNotBlank() }?.let { raw ->
                    enumValues<WidgetRefreshFrequency>().firstOrNull { it.name == raw }?.let { saveWidgetRefreshFrequency(it) }
                }
                settingsObj.optString("widgetLocationFrequency").takeIf { it.isNotBlank() }?.let { raw ->
                    enumValues<WidgetLocationFrequency>().firstOrNull { it.name == raw }?.let { saveWidgetLocationFrequency(it) }
                }
                settingsObj.optString("gtfsSyncFrequency").takeIf { it.isNotBlank() }?.let { raw ->
                    enumValues<GtfsSyncFrequency>().firstOrNull { it.name == raw }?.let { saveGtfsSyncFrequency(it) }
                }
                settingsObj.optString("trafficRefreshFrequency").takeIf { it.isNotBlank() }?.let { raw ->
                    enumValues<TrafficRefreshFrequency>().firstOrNull { it.name == raw }?.let { saveTrafficRefreshFrequency(it) }
                }
                if (settingsObj.has("trafficNotificationsEnabled")) {
                    setTrafficNotificationsEnabled(settingsObj.optBoolean("trafficNotificationsEnabled", false))
                }
                if (settingsObj.has("widgetUpdateNotificationsEnabled")) {
                    setWidgetUpdateNotificationsEnabled(settingsObj.optBoolean("widgetUpdateNotificationsEnabled", true))
                }
                settingsObj.optString("appUpdateCheckFrequency").takeIf { it.isNotBlank() }?.let { raw ->
                    enumValues<AppUpdateCheckFrequency>().firstOrNull { it.name == raw }?.let { saveAppUpdateCheckFrequency(it) }
                }
                settingsObj.optString("updateNetworkMode").takeIf { it.isNotBlank() }?.let { raw ->
                    enumValues<UpdateNetworkMode>().firstOrNull { it.name == raw }?.let { saveUpdateNetworkMode(it) }
                }
                settingsObj.optString("webMonitoringUrl").takeIf { it.isNotBlank() }?.let { saveWebMonitoringUrl(it) }
                settingsObj.optString("gtfsZipUrl").takeIf { it.isNotBlank() }?.let { saveGtfsZipUrl(it) }
                settingsObj.optString("siriSoapUrl").takeIf { it.isNotBlank() }?.let { saveSiriSoapUrl(it) }
                settingsObj.optString("queryTypeParam").takeIf { it.isNotBlank() }?.let { saveQueryTypeParam(it) }
                settingsObj.optString("githubRepo").takeIf { it.isNotBlank() }?.let { saveGithubRepo(it) }
                settingsObj.optString("githubTag").takeIf { it.isNotBlank() }?.let { saveGithubTag(it) }
                if (settingsObj.has("usageStatsEnabled")) {
                    setUsageStatsEnabled(settingsObj.optBoolean("usageStatsEnabled", true))
                }
            }

            Result.success(favoritesCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
