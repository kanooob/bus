package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.model.BusVehicle
import com.example.data.model.DepartureComparison
import com.example.data.model.GtfsStop
import com.example.data.model.IdeCycleStation
import com.example.data.model.ParkAndRide
import com.example.data.model.TrafficAlert
import com.example.data.remote.IdelisSiriService
import com.example.data.repository.IdelisRepository
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
    val isOfflineFallback: Boolean = false,
    val isSimulationMode: Boolean = false, // Retards simulés désactivés, seuls les passages issus des requêtes réelles sont affichés
    val requestorRef: String = IdelisSiriService.DEFAULT_REQUESTOR_REF,
    val statusMessage: String = "",
    val lastUpdatedFormatted: String = "",
    val secondsUntilRefresh: Int = 60,
    val isAutoRefreshPaused: Boolean = false,
    val isDarkTheme: Boolean = false,
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
    // Paramètres avancés : URLs & Requêtes
    val webMonitoringUrl: String = com.example.data.local.PreferencesManager.DEFAULT_WEB_URL,
    val gtfsZipUrl: String = com.example.data.local.PreferencesManager.DEFAULT_GTFS_ZIP_URL,
    val siriSoapUrl: String = com.example.data.local.PreferencesManager.DEFAULT_SIRI_URL,
    val queryTypeParam: String = com.example.data.local.PreferencesManager.DEFAULT_QUERY_TYPE,
    val githubRepo: String = com.example.data.local.PreferencesManager.DEFAULT_GITHUB_REPO,
    val githubTag: String = com.example.data.local.PreferencesManager.DEFAULT_GITHUB_TAG,
    val lastGtfsSyncTimestamp: Long = 0L
)

class TransitBoardViewModel(
    private val repository: IdelisRepository,
    private val preferencesManager: PreferencesManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TransitBoardUiState(
            isDarkTheme = preferencesManager?.isDarkThemeEnabled() ?: false,
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
            lastGtfsSyncTimestamp = preferencesManager?.getLastGtfsSyncTimestamp() ?: 0L
        )
    )
    val uiState: StateFlow<TransitBoardUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var periodicSyncJob: Job? = null
    private var webSearchJob: Job? = null
    private val REFRESH_INTERVAL_SECONDS = 60

    init {
        loadStops()
        downloadDataOnLaunch()
        loadComplementaryData()
        startAutoRefreshTicker()
        startPeriodicSyncJob()
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

            // CONSIGNES : Télécharger le référentiel GTFS ZIP Idelis au maximum une fois par jour à l'ouverture de l'application
            val lastDownload = preferencesManager?.getLastGtfsSyncTimestamp() ?: 0L
            val now = System.currentTimeMillis()
            val oneDayMs = 24 * 60 * 60 * 1000L
            if (now - lastDownload >= oneDayMs) {
                triggerGtfsZipSync()
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
                if (_uiState.value.departures.isEmpty() && _uiState.value.selectedStop != null) {
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
            val alerts = repository.getTrafficAlerts()

            _uiState.update {
                it.copy(
                    parkAndRides = pr,
                    ideCycleStations = bikes,
                    fleetVehicles = fleet,
                    trafficAlerts = alerts,
                    activePeriod = repository.getCurrentPeriod(),
                    worksNotes = repository.getWorksAlerts()
                )
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
        val next = !_uiState.value.isDarkTheme
        preferencesManager?.setDarkThemeEnabled(next)
        _uiState.update { it.copy(isDarkTheme = next) }
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
        _uiState.update { it.copy(selectedStop = stop, secondsUntilRefresh = REFRESH_INTERVAL_SECONDS) }
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
     * Enregistre l'arrêt avec son pôle exact et lance la requête des prochains passages direct (?type=777).
     */
    fun selectWebStop(webStop: com.example.data.model.IdelisWebStop) {
        viewModelScope.launch {
            val stop = repository.saveWebStop(webStop)
            selectStop(stop)
            _uiState.update { it.copy(webSuggestions = emptyList()) }
        }
    }

    fun clearWebSuggestions() {
        webSearchJob?.cancel()
        _uiState.update { it.copy(webSuggestions = emptyList(), isWebSearching = false) }
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

    fun refreshDepartures() {
        val stop = _uiState.value.selectedStop ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.getDeparturesForStop(
                stopId = stop.stopId,
                useSimulationMode = false,
                forceOffline = false,
                requestorRef = _uiState.value.requestorRef
            )

            val lines = result.departures.map { it.lineRef }.distinct()
            val filtered = if (_uiState.value.selectedLineFilter != null) {
                result.departures.filter { it.lineRef.equals(_uiState.value.selectedLineFilter, ignoreCase = true) }
            } else {
                result.departures
            }

            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
            val updatedStr = timeFormat.format(Date())

            _uiState.update { current ->
                current.copy(
                    departures = result.departures,
                    filteredDepartures = filtered,
                    availableLines = lines,
                    isLoading = false,
                    isOfflineFallback = result.isOfflineFallback,
                    statusMessage = result.statusMessage,
                    lastUpdatedFormatted = updatedStr,
                    secondsUntilRefresh = REFRESH_INTERVAL_SECONDS
                )
            }
        }
    }

    private fun startAutoRefreshTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val isPaused = _uiState.value.isAutoRefreshPaused
                if (!isPaused) {
                    val remaining = _uiState.value.secondsUntilRefresh - 1
                    if (remaining <= 0) {
                        _uiState.update { it.copy(secondsUntilRefresh = REFRESH_INTERVAL_SECONDS) }
                        refreshDepartures()
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
    fun triggerGtfsZipSync(customUrl: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGtfsSyncing = true, syncSuccessMessage = null) }
            val report = if (customUrl.isNullOrBlank()) {
                repository.syncGtfsZipArchive()
            } else {
                repository.syncGtfsZipArchive(customUrl)
            }

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
                        "GTFS synchronisé : ${report.stopsCount} arrêts, ${report.routesCount} lignes (${report.activePeriod.label})"
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
        periodicSyncJob = viewModelScope.launch {
            while (isActive) {
                // Intervalle périodique configuré (ex: 2 heures = 120 minutes)
                val intervalMs = _uiState.value.periodicSyncIntervalMinutes * 60 * 1000L
                delay(intervalMs)
                if (_uiState.value.isPeriodicSyncEnabled) {
                    triggerGtfsZipSync()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
        periodicSyncJob?.cancel()
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
