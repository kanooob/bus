package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestionnaire de persistance des préférences utilisateur :
 * - Dernier arrêt sélectionné (stopId)
 * - Mode sombre (activé/désactivé)
 * - Mode simulation des retards (activé/désactivé)
 * - Auto-actualisation en pause (activé/désactivé)
 * - Synchronisation périodique du GTFS (activé/désactivé)
 * - Arrêts favoris
 * - URLs et requêtes personnalisées (Paramètres > Avancé)
 * - Horodatage du dernier téléchargement GTFS (limité à 1 fois par jour)
 * - Configuration GitHub (versions et dépôts)
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

        // URLs & Requêtes configurables (Paramètres > Avancé)
        private const val KEY_URL_WEB_MONITORING = "url_web_monitoring"
        private const val KEY_URL_GTFS_ZIP = "url_gtfs_zip"
        private const val KEY_URL_SIRI_SOAP = "url_siri_soap"
        private const val KEY_QUERY_TYPE_PARAM = "query_type_param"

        // GitHub versions
        private const val KEY_GITHUB_REPO = "github_repo_target"
        private const val KEY_GITHUB_TAG = "github_tag_target"

        const val DEFAULT_WEB_URL = "https://www.idelis.fr/?type=777"
        const val DEFAULT_GTFS_ZIP_URL = "https://opendata.idelis.fr/?file=GTFS_MultiRoutes/Data.GTFS.zip"
        const val DEFAULT_SIRI_URL = "https://api.idelis.fr/siri/2.0"
        const val DEFAULT_QUERY_TYPE = "777"
        const val DEFAULT_GITHUB_REPO = "https://github.com/galaxie-bus/galaxie-bus"
        const val DEFAULT_GITHUB_TAG = "v1.0"
    }

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

    fun isDarkThemeEnabled(): Boolean = prefs.getBoolean(KEY_DARK_THEME, false)

    fun setDarkThemeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    fun isSimulationModeEnabled(): Boolean = prefs.getBoolean(KEY_SIMULATION_MODE, true)

    fun setSimulationModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SIMULATION_MODE, enabled).apply()
    }

    fun isAutoRefreshPaused(): Boolean = prefs.getBoolean(KEY_AUTO_REFRESH_PAUSED, false)

    fun setAutoRefreshPaused(paused: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REFRESH_PAUSED, paused).apply()
    }

    fun isPeriodicSyncEnabled(): Boolean = prefs.getBoolean(KEY_PERIODIC_SYNC, true)

    fun setPeriodicSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PERIODIC_SYNC, enabled).apply()
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

    fun getGtfsZipUrl(): String = prefs.getString(KEY_URL_GTFS_ZIP, DEFAULT_GTFS_ZIP_URL) ?: DEFAULT_GTFS_ZIP_URL

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
    fun getGithubRepo(): String = prefs.getString(KEY_GITHUB_REPO, DEFAULT_GITHUB_REPO) ?: DEFAULT_GITHUB_REPO

    fun saveGithubRepo(repo: String) {
        prefs.edit().putString(KEY_GITHUB_REPO, repo.trim()).apply()
    }

    fun getGithubTag(): String = prefs.getString(KEY_GITHUB_TAG, DEFAULT_GITHUB_TAG) ?: DEFAULT_GITHUB_TAG

    fun saveGithubTag(tag: String) {
        prefs.edit().putString(KEY_GITHUB_TAG, tag.trim()).apply()
    }
}
