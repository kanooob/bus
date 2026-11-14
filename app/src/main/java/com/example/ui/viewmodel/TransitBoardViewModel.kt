package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.PreferencesManager
import com.example.data.model.AppUpdateCheckFrequency
import com.example.data.model.BusVehicle
import com.example.data.model.DepartureComparison
import com.example.data.model.GtfsStop
import com.example.data.model.GtfsSyncFrequency
import com.example.data.model.IdeCycleStation
import com.example.data.model.InAppRefreshFrequency
import com.example.data.model.NotServedEntry
import com.example.data.model.ParkAndRide
import com.example.data.model.PrimaryColorMode
import com.example.data.model.ThemeMode
import com.example.data.model.TrafficAlert
import com.example.data.model.TrafficRefreshFrequency
import com.example.data.model.UpdateNetworkMode
import com.example.data.model.WidgetLocationFrequency
import com.example.data.model.WidgetRefreshFrequency
import com.example.data.remote.AppUpdateNotifier
import com.example.data.remote.AppUpdateState
import com.example.data.remote.GitHubReleaseInfo
import com.example.data.remote.IdelisSiriService
import com.example.data.remote.TrafficAlertNotifier
import com.example.data.remote.TrafficAlertSyncReceiver
import com.example.data.repository.IdelisRepository
import com.example.ui.widget.IdelisAppWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppTab(val title: String) {
    DEPARTURES("Départs"),
    FLEET("Véhicules"),
    ALERTS("Trafic"),
    MOBILITY("P+R & Vélos"),
    API_CONSOLE("APIs & OpenData")
}

data class TransitBoardUiState(
    val currentTab: AppTab = AppTab.DEPARTURES,
    val selectedStop: GtfsStop? = null,
    val departures: List<DepartureComparison> = emptyList(),
    val filteredDepartures: List<DepartureComparison> = emptyList(),
    val availableLines: List<String> = emptyList(),
    val selectedLineFilter: String? = null,
    val allStops: List<GtfsStop> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isManualRefreshing: Boolean = false,
    val isOfflineFallback: Boolean = false,
    val isSimulationMode: Boolean = false, // Retards simulés désactivés, seuls les passages issus des requêtes réelles sont affichés
    val requestorRef: String = IdelisSiriService.DEFAULT_REQUESTOR_REF,
    val statusMessage: String = "",
    val lastUpdatedFormatted: String = "",
    val secondsUntilRefresh: Int = InAppRefreshFrequency.EVERY_1_MIN.intervalSeconds,
    val isAutoRefreshPaused: Boolean = false,
    val isDarkTheme: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColorMode: PrimaryColorMode = PrimaryColorMode.APP_DEFAULT,
    val inAppRefreshFrequency: InAppRefreshFrequency = InAppRefreshFrequency.EVERY_1_MIN,
    val widgetRefreshFrequency: WidgetRefreshFrequency = WidgetRefreshFrequency.EVERY_15_MIN,
    val widgetLocationFrequency: WidgetLocationFrequency = WidgetLocationFrequency.EVERY_15_MIN,
    val gtfsSyncFrequency: GtfsSyncFrequency = GtfsSyncFrequency.DAILY_ON_OPEN,
    val trafficRefreshFrequency: TrafficRefreshFrequency = TrafficRefreshFrequency.EVERY_2_HOURS,
    val trafficNotificationsEnabled: Boolean = false,
    val widgetUpdateNotificationsEnabled: Boolean = true,
    val lastTrafficRefreshTimestamp: Long = 0L,
    val appUpdateCheckFrequency: AppUpdateCheckFrequency = AppUpdateCheckFrequency.DAILY_ON_OPEN,
    val updateNetworkMode: UpdateNetworkMode = UpdateNetworkMode.WIFI_AND_CELLULAR,
    val backupRestoreMessage: String? = null,
    val isBackupError: Boolean = false,
    val isDownloadingData: Boolean = false,
    val downloadStatus: String? = null,
    // Données complémentaires Explore v2.1, GTFS & SIRI
    val activePeriod: com.example.data.model.IdelisPeriod = com.example.data.model.IdelisPeriod.VERTE,
    val worksNotes: List<String> = emptyList(),
    val fleetVehicles: List<BusVehicle> = emptyList(),
    val trafficAlerts: List<TrafficAlert> = emptyList(),
    val parkAndRides: List<ParkAndRide> = emptyList(),
    val ideCycleStations: List<IdeCycleStation> = emptyList(),
    val syncSuccessMessage: String? = null,
    // Référentiel GTFS Zip et fichiers supplémentaires
    val isGtfsSyncing: Boolean = false,
    val gtfsSyncReport: com.example.data.model.GtfsSyncReport? = null,
    val supplementaryFilesReport: List<String> = emptyList(),
    val isPeriodicSyncEnabled: Boolean = true,
    val periodicSyncIntervalMinutes: Int = 120,
    val favoriteStopIds: Set<String> = emptySet(),
    // Détail d'itinéraire et liste des arrêts ordonnés de la ligne
    val selectedDepartureForRouteDetail: DepartureComparison? = null,
    val selectedRouteDetail: com.example.data.model.RouteLineDetail? = null,
    val routeDetailIsTerminusAtTop: Boolean = true,
    val routeDetailChosenDirection: String? = null,
    // Autocomplétion et recherche en direct idelis.fr (?type=777)
    val webSuggestions: List<com.example.data.model.IdelisWebStop> = emptyList(),
    val isWebSearching: Boolean = false,
    val isLocatingNearest: Boolean = false,
    // Paramètres avancés : URLs & Requêtes
    val webMonitoringUrl: String = com.example.data.local.PreferencesManager.DEFAULT_WEB_URL,
    val gtfsZipUrl: String = com.example.data.local.PreferencesManager.DEFAULT_GTFS_ZIP_URL,
    val siriSoapUrl: String = com.example.data.local.PreferencesManager.DEFAULT_SIRI_URL,
    val queryTypeParam: String = com.example.data.local.PreferencesManager.DEFAULT_QUERY_TYPE,
    val githubRepo: String = com.example.data.local.PreferencesManager.DEFAULT_GITHUB_REPO,
    val githubTag: String = com.example.data.local.PreferencesManager.DEFAULT_GITHUB_TAG,
    val lastGtfsSyncTimestamp: Long = 0L,
    // Arrêt non desservi ("Cet arrêt n'est pas desservi...")
    val isStopNotServed: Boolean = false,
    val notServedMessage: String? = null,
    val notServedEntries: List<NotServedEntry> = emptyList(),
    // Info Trafic en direct (?type=777 requete=infostrafic)
    val isTrafficLoading: Boolean = false,
    val trafficErrorMessage: String? = null,
    val trafficInfoPromptVisible: Boolean = false,
    // Mise à jour automatique GitHub (vérification + téléchargement Wi-Fi + installation APK)
    val appUpdateState: AppUpdateState = AppUpdateState.Idle,
    val updatePromptVisible: Boolean = false,
    val isWifiConnected: Boolean = false,
    val usageStatsEnabled: Boolean = true
)

class TransitBoardViewModel(
    private val repository: IdelisRepository,
    private val preferencesManager: PreferencesManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TransitBoardUiState(
            isDarkTheme = preferencesManager?.isDarkThemeEnabled() ?: false,
            themeMode = preferencesManager?.getThemeMode() ?: ThemeMode.SYSTEM,
            primaryColorMode = preferencesManager?.getPrimaryColorMode() ?: PrimaryColorMode.APP_DEFAULT,
            inAppRefreshFrequency = preferencesManager?.getInAppRefreshFrequency() ?: InAppRefreshFrequency.EVERY_1_MIN,
            widgetRefreshFrequency = preferencesManager?.getWidgetRefreshFrequency() ?: WidgetRefreshFrequency.EVERY_15_MIN,
            widgetLocationFrequency = preferencesManager?.getWidgetLocationFrequency() ?: WidgetLocationFrequency.EVERY_15_MIN,
            gtfsSyncFrequency = preferencesManager?.getGtfsSyncFrequency() ?: GtfsSyncFrequency.DAILY_ON_OPEN,
            trafficRefreshFrequency = preferencesManager?.getTrafficRefreshFrequency() ?: TrafficRefreshFrequency.EVERY_2_HOURS,
            trafficNotificationsEnabled = preferencesManager?.isTrafficNotificationsEnabled() ?: false,
            widgetUpdateNotificationsEnabled = preferencesManager?.isWidgetUpdateNotificationsEnabled() ?: true,
            lastTrafficRefreshTimestamp = preferencesManager?.getLastTrafficRefreshTimestamp() ?: 0L,
            appUpdateCheckFrequency = preferencesManager?.getAppUpdateCheckFrequency() ?: AppUpdateCheckFrequency.DAILY_ON_OPEN,
            updateNetworkMode = preferencesManager?.getUpdateNetworkMode() ?: UpdateNetworkMode.WIFI_AND_CELLULAR,
            secondsUntilRefresh = (preferencesManager?.getInAppRefreshFrequency() ?: InAppRefreshFrequency.EVERY_1_MIN).intervalSeconds,
            isSimulationMode = preferencesManager?.isSimulationModeEnabled() ?: true,
            isAutoRefreshPaused = preferencesManager?.isAutoRefreshPaused() ?: false,
            isPeriodicSyncEnabled = preferencesManager?.isPeriodicSyncEnabled() ?: true,
            favoriteStopIds = preferencesManager?.getFavoriteStopIds() ?: emptySet(),
            webMonitoringUrl = preferencesManager?.getWebMonitoringUrl() ?: com.example.data.local.PreferencesManager.DEFAULT_WEB_URL,
            gtfsZipUrl = preferencesManager?.getGtfsZipUrl() ?: com.example.data.local.PreferencesManager.DEFAULT_GTFS_ZIP_URL,
            siriSoapUrl = preferencesManager?.getSiriSoapUrl() ?: com.example.data.local.PreferencesManager.DEFAULT_SIRI_URL,
            queryTypeParam = preferencesManager?.getQueryTypeParam() ?: com.example.data.local.PreferencesManager.DEFAULT_QUERY_TYPE,
            githubRepo = preferencesManager?.getGithubRepo() ?: com.example.data.local.PreferencesManager.DEFAULT_GITHUB_REPO,
            githubTag = preferencesManager?.getGithubTag() ?: com.example.data.local.PreferencesManager.DEFAULT_GITHUB_TAG,
            lastGtfsSyncTimestamp = preferencesManager?.getLastGtfsSyncTimestamp() ?: 0L,
            usageStatsEnabled = preferencesManager?.isUsageStatsEnabled() ?: true
        )
    )
    val uiState: StateFlow<TransitBoardUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var periodicSyncJob: Job? = null
    private var trafficRefreshJob: Job? = null
    private var webSearchJob: Job? = null
    private var updateJob: Job? = null
    private var refreshJob: Job? = null
    private var usageStatsPingJob: Job? = null

    init {
        loadStops()
        downloadDataOnLaunch()
        loadComplementaryData()
        startAutoRefreshTicker()
        startPeriodicSyncJob()
        startTrafficRefreshJob()
        syncTrafficBackgroundSchedule()
        checkForAppUpdateOnLaunch()
        sendDailyUsageStatsPingOnLaunch()
    }

    private fun sendDailyUsageStatsPingOnLaunch() {
        val pm = preferencesManager ?: return
        if (!pm.isUsageStatsEnabled()) return
        usageStatsPingJob?.cancel()
        usageStatsPingJob = viewModelScope.launch {
            repository.usageStatsPingService.pingIfDue(pm)
        }
    }

    private fun checkForAppUpdateOnLaunch() {
        val freq = _uiState.value.appUpdateCheckFrequency
        if (freq == AppUpdateCheckFrequency.NEVER) return
        val lastCheck = preferencesManager?.getLastAppUpdateCheckTimestamp() ?: 0L
        val now = System.currentTimeMillis()
        if (now - lastCheck >= freq.intervalMs) {
            checkForAppUpdate(autoDownloadAllowed = true, manualCheck = false)
        }
    }

    private fun downloadDataOnLaunch() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingData = true, downloadStatus = "Téléchargement des arrêts et données...") }
            val result = repository.downloadOpenDataOnLaunch()
            _uiState.update {
                it.copy(
                    isDownloadingData = false,
                    downloadStatus = result.getOrNull() ?: "Données locales actives",
                    activePeriod = repository.getCurrentPeriod(),
                    worksNotes = repository.getWorksAlerts()
                )
            }
            refreshDepartures()

            val gtfsFreq = _uiState.value.gtfsSyncFrequency
            val lastDownload = preferencesManager?.getLastGtfsSyncTimestamp() ?: 0L
            val isFirstDownload = lastDownload == 0L
            val now = System.currentTimeMillis()
            if (isFirstDownload || (gtfsFreq != GtfsSyncFrequency.NEVER && gtfsFreq.intervalMs > 0L && now - lastDownload >= gtfsFreq.intervalMs)) {
                triggerGtfsZipSync(isFirstSync = isFirstDownload)
            }
        }
    }

    private fun loadStops() {
        viewModelScope.launch {
            // Purger les anciens arrêts théoriques pour ne garder QUE les arrêts reçus par requêtes / favoris
            repository.purgeTheoreticalStops(preferencesManager?.getFavoriteStopIds() ?: emptySet())

            repository.getAllStops().collectLatest { stops ->
                _uiState.update { current ->
                    val savedStopId = preferencesManager?.getLastSelectedStopId()
                    val defaultStop = current.selectedStop 
                        ?: stops.firstOrNull { it.stopId == savedStopId }
                        ?: stops.firstOrNull { current.favoriteStopIds.contains(it.stopId) }
                        ?: stops.firstOrNull { it.stopId == "WEB_Bosquet" }
                        ?: stops.firstOrNull { it.stopName.contains("Bosquet", true) }
                        ?: stops.firstOrNull()
                        ?: com.example.data.repository.IdelisRepository.DEFAULT_INITIAL_STOP
                    current.copy(
                        allStops = stops,
                        selectedStop = defaultStop,
                        activePeriod = repository.getCurrentPeriod()
                    )
                }
                if (_uiState.value.departures.isEmpty() && _uiState.value.selectedStop != null && refreshJob?.isActive != true) {
                    refreshDepartures()
                }
            }
        }
    }

    private fun loadComplementaryData() {
        viewModelScope.launch {
            val pr = repository.getParkAndRide()
            val bikes = repository.getIdeCycleStations()
            val fleet = repository.getFleetVehicles()

            _uiState.update {
                it.copy(
                    parkAndRides = pr,
                    ideCycleStations = bikes,
                    fleetVehicles = fleet,
                    activePeriod = repository.getCurrentPeriod(),
                    worksNotes = repository.getWorksAlerts()
                )
            }
        }
        val trafficFreq = _uiState.value.trafficRefreshFrequency
        if (trafficFreq != TrafficRefreshFrequency.NEVER && trafficFreq.intervalMs > 0L) {
            val lastCheck = preferencesManager?.getLastTrafficRefreshTimestamp() ?: 0L
            val now = System.currentTimeMillis()
            if (now - lastCheck >= trafficFreq.intervalMs) {
                refreshTrafficAlerts(notifyIfNew = true)
            }
        }
    }

    /**
     * Exécute la requête POST https://www.idelis.fr/?type=777 (requete=infostrafic&requete_val[lignesfav]=0)
     * et remplace le contenu de la catégorie info par les alertes parsées.
     */
    fun refreshTrafficAlerts(notifyIfNew: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTrafficLoading = true, trafficErrorMessage = null) }
            val result = repository.fetchLiveTrafficAlerts()
            if (result.isSuccess) {
                val alerts = result.getOrNull().orEmpty()
                val now = System.currentTimeMillis()
                preferencesManager?.saveLastTrafficRefreshTimestamp(now)
                TrafficAlertNotifier.processAlerts(
                    context = repository.appContext,
                    prefs = preferencesManager,
                    alerts = alerts,
                    allowNotification = notifyIfNew
                )
                _uiState.update {
                    it.copy(
                        trafficAlerts = alerts,
                        isTrafficLoading = false,
                        trafficErrorMessage = null,
                        lastTrafficRefreshTimestamp = now
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        trafficAlerts = emptyList(),
                        isTrafficLoading = false,
                        trafficErrorMessage = result.exceptionOrNull()?.localizedMessage
                            ?: "Impossible de récupérer les infos trafic idelis.fr"
                    )
                }
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
        if (tab == AppTab.FLEET || tab == AppTab.MOBILITY || tab == AppTab.ALERTS) {
            loadComplementaryData()
        }
    }

    fun setRequestorRef(newRef: String) {
        _uiState.update { it.copy(requestorRef = newRef.trim()) }
        refreshDepartures()
    }

    fun syncStopsFromExploreApi() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, syncSuccessMessage = null) }
            val result = repository.downloadOpenDataOnLaunch()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    syncSuccessMessage = result.getOrNull() ?: "Données synchronisées avec succès.",
                    activePeriod = repository.getCurrentPeriod(),
                    worksNotes = repository.getWorksAlerts()
                )
            }
            refreshDepartures()
        }
    }

    fun toggleDarkTheme() {
        val next = if (_uiState.value.themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        setThemeMode(next)
    }

    fun setThemeMode(mode: ThemeMode) {
        preferencesManager?.saveThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode, isDarkTheme = mode == ThemeMode.DARK) }
    }

    fun setPrimaryColorMode(mode: PrimaryColorMode) {
        preferencesManager?.savePrimaryColorMode(mode)
        _uiState.update { it.copy(primaryColorMode = mode) }
    }

    fun setInAppRefreshFrequency(freq: InAppRefreshFrequency) {
        preferencesManager?.saveInAppRefreshFrequency(freq)
        _uiState.update {
            it.copy(
                inAppRefreshFrequency = freq,
                isAutoRefreshPaused = freq == InAppRefreshFrequency.ON_OPEN_OR_STOP_CHANGE,
                secondsUntilRefresh = freq.intervalSeconds
            )
        }
        startAutoRefreshTicker()
    }

    fun setWidgetRefreshFrequency(freq: WidgetRefreshFrequency) {
        preferencesManager?.saveWidgetRefreshFrequency(freq)
        _uiState.update { it.copy(widgetRefreshFrequency = freq) }
        repository.appContext?.let { IdelisAppWidgetProvider.refreshAllWidgets(it) }
    }

    fun setWidgetLocationFrequency(freq: WidgetLocationFrequency) {
        preferencesManager?.saveWidgetLocationFrequency(freq)
        _uiState.update { it.copy(widgetLocationFrequency = freq) }
        repository.appContext?.let { IdelisAppWidgetProvider.refreshAllWidgets(it) }
    }

    fun setGtfsSyncFrequency(freq: GtfsSyncFrequency) {
        preferencesManager?.saveGtfsSyncFrequency(freq)
        _uiState.update {
            it.copy(
                gtfsSyncFrequency = freq,
                isPeriodicSyncEnabled = freq != GtfsSyncFrequency.NEVER
            )
        }
        startPeriodicSyncJob()
    }

    fun setTrafficRefreshFrequency(freq: TrafficRefreshFrequency) {
        preferencesManager?.saveTrafficRefreshFrequency(freq)
        _uiState.update { it.copy(trafficRefreshFrequency = freq) }
        startTrafficRefreshJob()
        syncTrafficBackgroundSchedule()
    }

    fun setTrafficNotificationsEnabled(enabled: Boolean) {
        preferencesManager?.setTrafficNotificationsEnabled(enabled)
        if (enabled && preferencesManager != null && !preferencesManager.hasTrafficBaselineInitialized()) {
            val currentAlerts = _uiState.value.trafficAlerts
            if (currentAlerts.isNotEmpty()) {
                preferencesManager.saveKnownTrafficAlertKeys(
                    currentAlerts.map { TrafficAlertNotifier.alertFingerprint(it) }.toSet()
                )
                preferencesManager.setTrafficBaselineInitialized(true)
            }
        }
        _uiState.update { it.copy(trafficNotificationsEnabled = enabled) }
        syncTrafficBackgroundSchedule()
    }

    fun setWidgetUpdateNotificationsEnabled(enabled: Boolean) {
        preferencesManager?.setWidgetUpdateNotificationsEnabled(enabled)
        _uiState.update { it.copy(widgetUpdateNotificationsEnabled = enabled) }
    }

    fun openTrafficInfoFromNotification() {
        _uiState.update { it.copy(trafficInfoPromptVisible = true) }
    }

    fun consumeTrafficInfoPrompt() {
        _uiState.update { it.copy(trafficInfoPromptVisible = false) }
    }

    private fun syncTrafficBackgroundSchedule() {
        val ctx = repository.appContext ?: return
        val prefs = preferencesManager ?: return
        TrafficAlertSyncReceiver.scheduleOrCancel(ctx, prefs)
    }

    private fun startTrafficRefreshJob() {
        trafficRefreshJob?.cancel()
        val freq = _uiState.value.trafficRefreshFrequency
        if (freq == TrafficRefreshFrequency.NEVER || freq.intervalMs <= 0L) {
            return
        }
        trafficRefreshJob = viewModelScope.launch {
            while (isActive) {
                val intervalMs = _uiState.value.trafficRefreshFrequency.intervalMs
                if (intervalMs <= 0L) break
                delay(intervalMs)
                if (_uiState.value.trafficRefreshFrequency != TrafficRefreshFrequency.NEVER) {
                    refreshTrafficAlerts(notifyIfNew = true)
                }
            }
        }
    }

    fun setAppUpdateCheckFrequency(freq: AppUpdateCheckFrequency) {
        preferencesManager?.saveAppUpdateCheckFrequency(freq)
        _uiState.update { it.copy(appUpdateCheckFrequency = freq) }
    }

    fun setUpdateNetworkMode(mode: UpdateNetworkMode) {
        preferencesManager?.saveUpdateNetworkMode(mode)
        _uiState.update { it.copy(updateNetworkMode = mode) }
    }

    fun setUsageStatsEnabled(enabled: Boolean) {
        preferencesManager?.setUsageStatsEnabled(enabled)
        _uiState.update { it.copy(usageStatsEnabled = enabled) }
        if (!enabled) {
            usageStatsPingJob?.cancel()
        }
    }

    fun exportBackupJson(): String {
        return preferencesManager?.exportBackupJson() ?: "{}"
    }

    fun importBackupJson(json: String) {
        val pm = preferencesManager ?: return
        val result = pm.importBackupJson(json)
        if (result.isSuccess) {
            val count = result.getOrDefault(0)
            val theme = pm.getThemeMode()
            val primaryColor = pm.getPrimaryColorMode()
            val inAppFreq = pm.getInAppRefreshFrequency()
            val widgetFreq = pm.getWidgetRefreshFrequency()
            val widgetLocFreq = pm.getWidgetLocationFrequency()
            val gtfsFreq = pm.getGtfsSyncFrequency()
            val trafficFreq = pm.getTrafficRefreshFrequency()
            val trafficNotifEnabled = pm.isTrafficNotificationsEnabled()
            val widgetNotifEnabled = pm.isWidgetUpdateNotificationsEnabled()
            val updateFreq = pm.getAppUpdateCheckFrequency()
            val networkMode = pm.getUpdateNetworkMode()
            val webUrl = pm.getWebMonitoringUrl()
            val gtfsUrl = pm.getGtfsZipUrl()
            val siriUrl = pm.getSiriSoapUrl()
            val queryParam = pm.getQueryTypeParam()
            val ghRepo = pm.getGithubRepo()
            val ghTag = pm.getGithubTag()
            val favs = pm.getFavoriteStopIds()

            repository.webScraperService.customBaseUrl = webUrl
            repository.webScraperService.customQueryParam = queryParam
            repository.siriService.customEndpoint = siriUrl

            _uiState.update {
                it.copy(
                    themeMode = theme,
                    isDarkTheme = theme == ThemeMode.DARK,
                    primaryColorMode = primaryColor,
                    inAppRefreshFrequency = inAppFreq,
                    isAutoRefreshPaused = inAppFreq == InAppRefreshFrequency.ON_OPEN_OR_STOP_CHANGE,
                    secondsUntilRefresh = inAppFreq.intervalSeconds,
                    widgetRefreshFrequency = widgetFreq,
                    widgetLocationFrequency = widgetLocFreq,
                    gtfsSyncFrequency = gtfsFreq,
                    isPeriodicSyncEnabled = gtfsFreq != GtfsSyncFrequency.NEVER,
                    trafficRefreshFrequency = trafficFreq,
                    trafficNotificationsEnabled = trafficNotifEnabled,
                    widgetUpdateNotificationsEnabled = widgetNotifEnabled,
                    appUpdateCheckFrequency = updateFreq,
                    updateNetworkMode = networkMode,
                    webMonitoringUrl = webUrl,
                    gtfsZipUrl = gtfsUrl,
                    siriSoapUrl = siriUrl,
                    queryTypeParam = queryParam,
                    githubRepo = ghRepo,
                    githubTag = ghTag,
                    favoriteStopIds = favs,
                    usageStatsEnabled = pm.isUsageStatsEnabled(),
                    backupRestoreMessage = "Sauvegarde restaurée ($count favori(s), paramètres appliqués)",
                    isBackupError = false
                )
            }
            startAutoRefreshTicker()
            startPeriodicSyncJob()
            startTrafficRefreshJob()
            syncTrafficBackgroundSchedule()
            pm.getLastSelectedStopId()?.let { selectStopById(it) } ?: refreshDepartures()
            repository.appContext?.let { IdelisAppWidgetProvider.refreshAllWidgets(it) }
        } else {
            _uiState.update {
                it.copy(
                    backupRestoreMessage = "Échec de l'import : ${result.exceptionOrNull()?.localizedMessage ?: "JSON invalide"}",
                    isBackupError = true
                )
            }
        }
    }

    fun clearBackupRestoreMessage() {
        _uiState.update { it.copy(backupRestoreMessage = null, isBackupError = false) }
    }

    fun toggleFavoriteStop(stopId: String) {
        val current = _uiState.value.favoriteStopIds.toMutableSet()
        if (current.contains(stopId)) {
            current.remove(stopId)
        } else {
            current.add(stopId)
        }
        preferencesManager?.saveFavoriteStopIds(current)
        _uiState.update { it.copy(favoriteStopIds = current) }
    }

    /**
     * Permet d'ajouter ou de retirer des favoris un arrêt reçu par requête de recherche/autocomplétion.
     * Enregistre l'arrêt dans la base et met à jour les favoris utilisateur.
     */
    fun toggleFavoriteWebStop(webStop: com.example.data.model.IdelisWebStop) {
        viewModelScope.launch {
            val stop = repository.saveWebStop(webStop)
            toggleFavoriteStop(stop.stopId)
        }
    }

    /**
     * Permet de mettre en favori l'arrêt actuellement affiché à l'écran.
     */
    fun toggleFavoriteCurrentStop() {
        val stop = _uiState.value.selectedStop ?: return
        toggleFavoriteStop(stop.stopId)
    }

    fun selectStop(stop: GtfsStop) {
        preferencesManager?.saveLastSelectedStopId(stop.stopId)
        val interval = _uiState.value.inAppRefreshFrequency.intervalSeconds
        _uiState.update { it.copy(selectedStop = stop, secondsUntilRefresh = interval) }
        viewModelScope.launch {
            repository.ensureStopSaved(stop)
        }
        refreshDepartures()
    }

    fun selectStopById(stopId: String) {
        viewModelScope.launch {
            val stop = _uiState.value.allStops.firstOrNull { it.stopId == stopId }
                ?: repository.getStopById(stopId)
                ?: if (stopId == "WEB_Bosquet" || stopId == "ID_BOSQUET") com.example.data.repository.IdelisRepository.DEFAULT_INITIAL_STOP else null
            if (stop != null) {
                selectStop(stop)
            }
        }
    }

    /**
     * Met à jour la requête de recherche sans la réinitialiser lors de la sélection.
     */
    fun updatePickerSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchWebAutocomplete(query)
    }

    /**
     * Recherche en direct des arrêts/pôles via l'autocomplétion officielle idelis.fr (?type=777).
     * Renvoie les arrêts avec le bon nom et l'attribut data-pole correspondant.
     */
    fun searchWebAutocomplete(query: String) {
        webSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(webSuggestions = emptyList(), isWebSearching = false) }
            return
        }

        webSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isWebSearching = true) }
            delay(350L) // Débounce pour fluidité de frappe
            val result = repository.searchWebStops(trimmed)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        webSuggestions = result.getOrNull().orEmpty(),
                        isWebSearching = false
                    )
                }
            } else {
                _uiState.update { it.copy(isWebSearching = false) }
            }
        }
    }

    /**
     * Sélectionne un arrêt renvoyé par l'autocomplétion idelis.fr.
     * Enregistre l'arrêt avec son pôle exact et lance la requête des prochains passages direct (?type=777),
     * tout en conservant le texte recherché ET les résultats de recherche.
     */
    fun selectWebStop(webStop: com.example.data.model.IdelisWebStop) {
        viewModelScope.launch {
            val stop = repository.saveWebStop(webStop)
            selectStop(stop)
        }
    }

    fun clearWebSuggestions() {
        webSearchJob?.cancel()
        _uiState.update { it.copy(webSuggestions = emptyList(), isWebSearching = false) }
    }

    /**
     * Recherche et sélectionne l'arrêt le plus proche de la position GPS fournie en interrogeant
     * l'autocomplétion idelis.fr de la même façon qu'une recherche manuelle (avec repli sur quelques mots
     * et sélection du résultat le plus ressemblant).
     */
    fun selectNearestStopAsync(
        userLat: Double,
        userLon: Double,
        onResolved: (GtfsStop?, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocatingNearest = true) }
            try {
                val resolution = repository.resolveAndSaveNearestStop(
                    userLat = userLat,
                    userLon = userLon,
                    fallbackStops = _uiState.value.allStops
                )
                if (resolution != null) {
                    selectStop(resolution.stop)
                    val distLabel = if (resolution.distanceMeters < 1000) {
                        "${resolution.distanceMeters.toInt()} m"
                    } else {
                        String.format(Locale.FRANCE, "%.1f km", resolution.distanceMeters / 1000.0)
                    }
                    val feedback = "Arrêt le plus proche : ${resolution.stop.stopName} ($distLabel)"
                    _uiState.update { it.copy(isLocatingNearest = false) }
                    onResolved(resolution.stop, feedback)
                } else {
                    _uiState.update { it.copy(isLocatingNearest = false) }
                    onResolved(null, null)
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLocatingNearest = false) }
                onResolved(null, null)
            }
        }
    }

    /**
     * Sélectionne automatiquement l'arrêt le plus proche de la position GPS fournie.
     */
    fun selectNearestStop(userLat: Double, userLon: Double): GtfsStop? {
        val stops = _uiState.value.allStops
        if (stops.isEmpty()) return null

        // Calcule la distance de Haversine pour trouver l'arrêt le plus proche
        val nearest = stops.filter { it.stopLat != 0.0 && it.stopLon != 0.0 }.minByOrNull { stop ->
            val dLat = Math.toRadians(stop.stopLat - userLat)
            val dLon = Math.toRadians(stop.stopLon - userLon)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(userLat)) * Math.cos(Math.toRadians(stop.stopLat)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            6371000.0 * c // distance en mètres
        } ?: stops.firstOrNull()

        if (nearest != null) {
            selectStop(nearest)
        }
        return nearest
    }

    fun setLineFilter(line: String?) {
        _uiState.update { current ->
            val filtered = if (line == null) {
                current.departures
            } else {
                current.departures.filter { it.lineRef.equals(line, ignoreCase = true) || it.lineName.contains(line, ignoreCase = true) }
            }
            current.copy(
                selectedLineFilter = line,
                filteredDepartures = filtered
            )
        }
    }

    /**
     * Ouvre la vue des arrêts d'une ligne lors d'un appui sur un bus sur le premier écran.
     * Respecte la consigne utilisateur :
     * Affiche tous les arrêts dans l'ordre, avec en haut le terminus et en bas le début.
     */
    fun selectDepartureForRouteDetail(departure: DepartureComparison) {
        val currentStop = _uiState.value.selectedStop?.stopName ?: "Pôle Bosquet"
        val isTerminusAtTop = _uiState.value.routeDetailIsTerminusAtTop
        val detail = repository.getRouteDetail(
            departure = departure,
            currentStopName = currentStop,
            chosenDirection = departure.destinationName,
            isTerminusAtTop = isTerminusAtTop
        )
        _uiState.update {
            it.copy(
                selectedDepartureForRouteDetail = departure,
                selectedRouteDetail = detail,
                routeDetailChosenDirection = detail.selectedDirection
            )
        }
    }

    /**
     * Permet de changer la direction de la ligne (ex: aller vs retour).
     * Met à jour la liste des arrêts avec le terminus correspondant en haut et le début en bas.
     */
    fun switchRouteDetailDirection(newDirection: String) {
        val departure = _uiState.value.selectedDepartureForRouteDetail ?: return
        val currentStop = _uiState.value.selectedStop?.stopName ?: "Pôle Bosquet"
        val isTerminusAtTop = _uiState.value.routeDetailIsTerminusAtTop
        val detail = repository.getRouteDetail(
            departure = departure,
            currentStopName = currentStop,
            chosenDirection = newDirection,
            isTerminusAtTop = isTerminusAtTop
        )
        _uiState.update {
            it.copy(
                selectedRouteDetail = detail,
                routeDetailChosenDirection = newDirection
            )
        }
    }

    /**
     * Permet d'inverser l'ordre des arrêts (Terminus en haut ⇄ Début en haut).
     */
    fun toggleRouteDetailOrder() {
        val departure = _uiState.value.selectedDepartureForRouteDetail ?: return
        val currentStop = _uiState.value.selectedStop?.stopName ?: "Pôle Bosquet"
        val nextOrder = !_uiState.value.routeDetailIsTerminusAtTop
        val direction = _uiState.value.routeDetailChosenDirection
        val detail = repository.getRouteDetail(
            departure = departure,
            currentStopName = currentStop,
            chosenDirection = direction,
            isTerminusAtTop = nextOrder
        )
        _uiState.update {
            it.copy(
                routeDetailIsTerminusAtTop = nextOrder,
                selectedRouteDetail = detail
            )
        }
    }

    /**
     * Ferme la modale du détail des arrêts de la ligne.
     */
    fun clearSelectedRouteDetail() {
        _uiState.update {
            it.copy(
                selectedDepartureForRouteDetail = null,
                selectedRouteDetail = null,
                routeDetailChosenDirection = null
            )
        }
    }

    /**
     * Permet à l'utilisateur de cliquer sur n'importe quel arrêt de l'itinéraire
     * pour voir immédiatement la page des prochains bus qui y arrivent.
     */
    fun selectStopFromRouteDetail(stopName: String) {
        viewModelScope.launch {
            val canonical = com.example.data.repository.IdelisRepository.getCanonicalStopName(stopName)
            var match = _uiState.value.allStops.firstOrNull {
                it.stopName.equals(stopName, ignoreCase = true) ||
                        it.stopName.equals(canonical, ignoreCase = true) ||
                        com.example.data.repository.IdelisRepository.getCanonicalStopName(it.stopName).equals(canonical, ignoreCase = true)
            }
            if (match == null) {
                // Enregistrer dynamiquement l'arrêt cliqué pour ouvrir ses prochains bus
                val newStop = repository.saveWebStop(
                    com.example.data.model.IdelisWebStop(
                        pole = canonical,
                        name = stopName
                    )
                )
                match = newStop
            }
            selectStop(match)
            clearSelectedRouteDetail()
        }
    }


    fun toggleSimulationMode() {
        val next = !_uiState.value.isSimulationMode
        preferencesManager?.setSimulationModeEnabled(next)
        _uiState.update { it.copy(isSimulationMode = next) }
        refreshDepartures()
    }

    fun toggleAutoRefresh() {
        val next = !_uiState.value.isAutoRefreshPaused
        preferencesManager?.setAutoRefreshPaused(next)
        _uiState.update { it.copy(isAutoRefreshPaused = next) }
    }

    fun refreshDepartures(isManual: Boolean = false) {
        val stop = _uiState.value.selectedStop ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isLoading = current.departures.isEmpty(),
                    isManualRefreshing = isManual
                )
            }
            val result = repository.getDeparturesForStop(
                stopId = stop.stopId,
                useSimulationMode = false,
                forceOffline = false,
                requestorRef = _uiState.value.requestorRef,
                stopOverride = stop
            )

            if (_uiState.value.selectedStop?.stopId != stop.stopId) {
                _uiState.update { it.copy(isLoading = false, isManualRefreshing = false) }
                return@launch
            }

            val lines = result.departures.map { it.lineRef }.distinct()
            val currentFilter = _uiState.value.selectedLineFilter
            val filtered = if (currentFilter != null) {
                result.departures.filter { it.lineRef.equals(currentFilter, ignoreCase = true) }
            } else {
                result.departures
            }

            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
            val updatedStr = timeFormat.format(Date())
            val nextInterval = _uiState.value.inAppRefreshFrequency.intervalSeconds

            _uiState.update { current ->
                current.copy(
                    departures = result.departures,
                    filteredDepartures = filtered,
                    availableLines = lines,
                    isLoading = false,
                    isManualRefreshing = false,
                    isOfflineFallback = result.isOfflineFallback,
                    statusMessage = result.statusMessage,
                    lastUpdatedFormatted = updatedStr,
                    secondsUntilRefresh = nextInterval,
                    isStopNotServed = result.isStopNotServed,
                    notServedMessage = result.notServedMessage,
                    notServedEntries = result.notServedEntries
                )
            }
        }
    }

    private fun startAutoRefreshTicker() {
        tickerJob?.cancel()
        val freq = _uiState.value.inAppRefreshFrequency
        if (freq == InAppRefreshFrequency.ON_OPEN_OR_STOP_CHANGE || freq.intervalSeconds <= 0) {
            return
        }
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val currentFreq = _uiState.value.inAppRefreshFrequency
                val isPaused = _uiState.value.isAutoRefreshPaused ||
                    currentFreq == InAppRefreshFrequency.ON_OPEN_OR_STOP_CHANGE ||
                    currentFreq.intervalSeconds <= 0
                if (!isPaused) {
                    val remaining = _uiState.value.secondsUntilRefresh - 1
                    if (remaining <= 0) {
                        _uiState.update { it.copy(secondsUntilRefresh = currentFreq.intervalSeconds) }
                        refreshDepartures(isManual = false)
                    } else {
                        _uiState.update { it.copy(secondsUntilRefresh = remaining) }
                    }
                }
            }
        }
    }


    /**
     * Déclenche manuellement ou via le bouton le téléchargement et l'analyse du ZIP GTFS Idelis.
     */
    fun triggerGtfsZipSync(customUrl: String? = null, isFirstSync: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGtfsSyncing = true, syncSuccessMessage = null) }
            val effectiveUrl = customUrl?.takeIf { it.isNotBlank() } ?: _uiState.value.gtfsZipUrl
            val firstSync = isFirstSync || (preferencesManager?.getLastGtfsSyncTimestamp() ?: 0L) == 0L
            val report = repository.syncGtfsZipArchive(effectiveUrl, isFirstSync = firstSync)

            if (report.success) {
                val now = System.currentTimeMillis()
                preferencesManager?.saveLastGtfsSyncTimestamp(now)
                _uiState.update { it.copy(lastGtfsSyncTimestamp = now) }
            }

            _uiState.update { current ->
                current.copy(
                    isGtfsSyncing = false,
                    gtfsSyncReport = report,
                    activePeriod = repository.getCurrentPeriod(),
                    worksNotes = repository.getWorksAlerts(),
                    syncSuccessMessage = if (report.success) {
                        val viaSuffix = if (report.usedWaybackFallback) " via Wayback Machine" else ""
                        "GTFS synchronisé$viaSuffix : ${report.stopsCount} arrêts, ${report.routesCount} lignes (${report.activePeriod.label})"
                    } else {
                        "Échec GTFS : ${report.errorMessage}"
                    }
                )
            }
            refreshDepartures()
        }
    }

    /**
     * Sauvegarde les URLs et requêtes personnalisées dans Paramètres > Avancé
     */
    fun saveAdvancedSettings(
        webUrl: String,
        gtfsUrl: String,
        siriUrl: String,
        queryParam: String,
        githubRepo: String,
        githubTag: String
    ) {
        preferencesManager?.apply {
            saveWebMonitoringUrl(webUrl)
            saveGtfsZipUrl(gtfsUrl)
            saveSiriSoapUrl(siriUrl)
            saveQueryTypeParam(queryParam)
            saveGithubRepo(githubRepo)
            saveGithubTag(githubTag)
        }
        repository.webScraperService.customBaseUrl = webUrl
        repository.webScraperService.customQueryParam = queryParam
        repository.siriService.customEndpoint = siriUrl

        _uiState.update {
            it.copy(
                webMonitoringUrl = webUrl,
                gtfsZipUrl = gtfsUrl,
                siriSoapUrl = siriUrl,
                queryTypeParam = queryParam,
                githubRepo = githubRepo,
                githubTag = githubTag,
                syncSuccessMessage = "Paramètres avancés sauvegardés"
            )
        }
        refreshDepartures()
    }

    /**
     * Réinitialise les URLs par défaut du réseau officiel Idelis
     */
    fun resetAdvancedUrlsToDefault() {
        preferencesManager?.resetAdvancedUrlsToDefaults()
        val defWeb = com.example.data.local.PreferencesManager.DEFAULT_WEB_URL
        val defGtfs = com.example.data.local.PreferencesManager.DEFAULT_GTFS_ZIP_URL
        val defSiri = com.example.data.local.PreferencesManager.DEFAULT_SIRI_URL
        val defParam = com.example.data.local.PreferencesManager.DEFAULT_QUERY_TYPE

        repository.webScraperService.customBaseUrl = defWeb
        repository.webScraperService.customQueryParam = defParam
        repository.siriService.customEndpoint = defSiri

        _uiState.update {
            it.copy(
                webMonitoringUrl = defWeb,
                gtfsZipUrl = defGtfs,
                siriSoapUrl = defSiri,
                queryTypeParam = defParam,
                syncSuccessMessage = "URLs réinitialisées par défaut"
            )
        }
        refreshDepartures()
    }

    /**
     * Télécharge les autres fichiers de données ouvertes (P+R, stations vélos, véhicules du parc).
     */
    fun triggerSupplementaryFilesDownload() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val results = repository.downloadSupplementaryDataFiles()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    supplementaryFilesReport = results
                )
            }
            loadComplementaryData()
        }
    }

    fun togglePeriodicSync(enabled: Boolean) {
        preferencesManager?.setPeriodicSyncEnabled(enabled)
        _uiState.update { it.copy(isPeriodicSyncEnabled = enabled) }
        if (enabled) {
            startPeriodicSyncJob()
        } else {
            periodicSyncJob?.cancel()
        }
    }

    private fun startPeriodicSyncJob() {
        periodicSyncJob?.cancel()
        val gtfsFreq = _uiState.value.gtfsSyncFrequency
        if (!gtfsFreq.isPeriodic || gtfsFreq.intervalMs <= 0L) {
            return
        }
        periodicSyncJob = viewModelScope.launch {
            while (isActive) {
                val intervalMs = _uiState.value.gtfsSyncFrequency.intervalMs
                if (intervalMs <= 0L) break
                delay(intervalMs)
                if (_uiState.value.gtfsSyncFrequency.isPeriodic) {
                    triggerGtfsZipSync()
                }
            }
        }
    }

    fun refreshWifiStatus() {
        val ctx = repository.appContext ?: return
        val wifi = repository.updaterService.isWifiConnected(ctx)
        _uiState.update { it.copy(isWifiConnected = wifi) }
    }

    /**
     * Vérifie s'il existe une nouvelle version sur GitHub (https://github.com/kanooob/bus).
     * Respecte le mode réseau autorisé (Wi-Fi uniquement vs Wi-Fi et données mobiles).
     */
    fun checkForAppUpdate(autoDownloadAllowed: Boolean = true, manualCheck: Boolean = false) {
        val ctx = repository.appContext
        val wifiConnected = ctx?.let { repository.updaterService.isWifiConnected(it) } ?: false
        _uiState.update {
            it.copy(
                appUpdateState = AppUpdateState.Checking,
                isWifiConnected = wifiConnected
            )
        }

        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            val repoUrl = _uiState.value.githubRepo.ifBlank {
                com.example.data.local.PreferencesManager.DEFAULT_GITHUB_REPO
            }
            val targetTag = _uiState.value.githubTag.ifBlank {
                com.example.data.local.PreferencesManager.DEFAULT_GITHUB_TAG
            }
            val currentVersion = BuildConfig.VERSION_NAME
            val checkRes = repository.updaterService.checkLatestRelease(
                repoUrl = repoUrl,
                currentVersion = currentVersion,
                targetTag = targetTag
            )
            preferencesManager?.saveLastAppUpdateCheckTimestamp(System.currentTimeMillis())

            if (checkRes.isFailure) {
                val errMsg = checkRes.exceptionOrNull()?.localizedMessage
                    ?: "Erreur lors de la vérification GitHub"
                _uiState.update {
                    it.copy(
                        appUpdateState = if (manualCheck) AppUpdateState.Error(errMsg) else AppUpdateState.Idle
                    )
                }
                return@launch
            }

            val release = checkRes.getOrNull()!!
            if (!release.isNewerThanCurrent) {
                _uiState.update {
                    it.copy(
                        appUpdateState = AppUpdateState.UpToDate(currentVersion),
                        updatePromptVisible = false
                    )
                }
                return@launch
            }

            if (ctx != null) {
                val lastNotifiedTag = preferencesManager?.getLastNotifiedUpdateTag()
                if (manualCheck || lastNotifiedTag != release.tagName) {
                    AppUpdateNotifier.notifyUpdateAvailable(ctx, release)
                    preferencesManager?.saveLastNotifiedUpdateTag(release.tagName)
                }
            }

            val isWifiNow = ctx?.let { repository.updaterService.isWifiConnected(it) } ?: false
            val networkAllowed = _uiState.value.updateNetworkMode == UpdateNetworkMode.WIFI_AND_CELLULAR || isWifiNow
            _uiState.update { it.copy(isWifiConnected = isWifiNow, updatePromptVisible = true) }

            if (autoDownloadAllowed && networkAllowed && ctx != null && !release.apkDownloadUrl.isNullOrBlank()) {
                startApkDownload(ctx, release)
            } else {
                _uiState.update {
                    it.copy(
                        appUpdateState = AppUpdateState.UpdateAvailableWaitingWifi(release),
                        updatePromptVisible = true
                    )
                }
            }
        }
    }

    /**
     * Télécharge l'APK de la release détectée (en vérifiant le réseau autorisé sauf si l'utilisateur force le téléchargement).
     */
    fun downloadPendingUpdate(forceWithoutWifi: Boolean = false) {
        val ctx = repository.appContext ?: return
        val release: GitHubReleaseInfo = when (val st = _uiState.value.appUpdateState) {
            is AppUpdateState.UpdateAvailableWaitingWifi -> st.release
            is AppUpdateState.Error -> st.release ?: run {
                checkForAppUpdate(autoDownloadAllowed = true, manualCheck = true)
                return
            }
            is AppUpdateState.ReadyToInstall -> return
            is AppUpdateState.Downloading -> return
            else -> return
        }

        val isWifiNow = repository.updaterService.isWifiConnected(ctx)
        val networkAllowed = _uiState.value.updateNetworkMode == UpdateNetworkMode.WIFI_AND_CELLULAR || isWifiNow
        _uiState.update { it.copy(isWifiConnected = isWifiNow) }
        if (!forceWithoutWifi && !networkAllowed) {
            _uiState.update {
                it.copy(
                    appUpdateState = AppUpdateState.UpdateAvailableWaitingWifi(release),
                    updatePromptVisible = true
                )
            }
            return
        }

        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            startApkDownload(ctx, release)
        }
    }

    private suspend fun startApkDownload(ctx: android.content.Context, release: GitHubReleaseInfo) {
        _uiState.update {
            it.copy(
                appUpdateState = AppUpdateState.Downloading(
                    release = release,
                    progressPercent = 0,
                    downloadedBytes = 0L,
                    totalBytes = release.apkSizeBytes
                ),
                updatePromptVisible = true
            )
        }

        val dlResult = repository.updaterService.downloadApk(
            context = ctx,
            release = release
        ) { percent, downloaded, total ->
            _uiState.update {
                it.copy(
                    appUpdateState = AppUpdateState.Downloading(
                        release = release,
                        progressPercent = percent,
                        downloadedBytes = downloaded,
                        totalBytes = total
                    )
                )
            }
        }

        if (dlResult.isSuccess) {
            val file = dlResult.getOrNull()!!
            AppUpdateNotifier.notifyUpdateReadyToInstall(ctx, release)
            _uiState.update {
                it.copy(
                    appUpdateState = AppUpdateState.ReadyToInstall(
                        release = release,
                        apkFilePath = file.absolutePath
                    ),
                    updatePromptVisible = true
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    appUpdateState = AppUpdateState.Error(
                        message = dlResult.exceptionOrNull()?.localizedMessage ?: "Échec du téléchargement de la mise à jour",
                        release = release
                    ),
                    updatePromptVisible = true
                )
            }
        }
    }

    fun openUpdatePrompt() {
        val state = _uiState.value.appUpdateState
        if (state is AppUpdateState.UpdateAvailableWaitingWifi ||
            state is AppUpdateState.Downloading ||
            state is AppUpdateState.ReadyToInstall ||
            state is AppUpdateState.Error
        ) {
            _uiState.update { it.copy(updatePromptVisible = true) }
        } else {
            checkForAppUpdate(autoDownloadAllowed = true, manualCheck = true)
        }
    }

    fun reportUpdateError(message: String) {
        val existingRelease = when (val st = _uiState.value.appUpdateState) {
            is AppUpdateState.UpdateAvailableWaitingWifi -> st.release
            is AppUpdateState.Downloading -> st.release
            is AppUpdateState.ReadyToInstall -> st.release
            is AppUpdateState.Error -> st.release
            else -> null
        }
        _uiState.update {
            it.copy(
                appUpdateState = AppUpdateState.Error(message = message, release = existingRelease),
                updatePromptVisible = true
            )
        }
    }

    fun hasAskedNotificationPermission(): Boolean =
        preferencesManager?.hasAskedNotificationPermission() ?: false

    fun markNotificationPermissionAsked() {
        preferencesManager?.setNotificationPermissionAsked()
    }

    fun dismissUpdatePrompt() {
        _uiState.update { it.copy(updatePromptVisible = false) }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
        periodicSyncJob?.cancel()
        trafficRefreshJob?.cancel()
        updateJob?.cancel()
        refreshJob?.cancel()
        usageStatsPingJob?.cancel()
    }
}

class TransitBoardViewModelFactory(
    private val repository: IdelisRepository,
    private val preferencesManager: PreferencesManager? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransitBoardViewModel::class.java)) {
            return TransitBoardViewModel(repository, preferencesManager) as T
        }
        throw IllegalArgumentException("Classe ViewModel inconnue: ${modelClass.name}")
    }
}
