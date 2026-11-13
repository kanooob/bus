package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.IdelisDepartureRow
import com.example.ui.components.IdelisSketchTopBar
import com.example.ui.components.SettingsModal
import com.example.ui.components.StopPickerModal
import com.example.ui.components.TrafficInfoModal
import com.example.ui.theme.SketchBlack
import com.example.ui.theme.SketchRed
import com.example.ui.theme.SketchWhite
import com.example.ui.theme.TransitDarkBg
import com.example.ui.viewmodel.TransitBoardViewModel

/**
 * Écran principal simple et direct conforme au croquis utilisateur :
 * - Haut : Nom de l'arrêt (choix de l'arrêt) | Icône alerte/trafic | Icône paramètres
 * - Liste : [icône squircle] ligne : direction | Imminent / Dans Xmin (+1min / A l'heure)
 * - Sans clé API requise : fonctionne immédiatement avec les arrêts et lignes Idelis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitBoardScreen(
    viewModel: TransitBoardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var isStopPickerOpen by remember { mutableStateOf(false) }
    var isTrafficInfoOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    val backgroundColor = if (uiState.isDarkTheme) TransitDarkBg else SketchWhite
    val contentTextColor = if (uiState.isDarkTheme) Color.White else SketchBlack

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = backgroundColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Barre supérieure :
            // Nom de l'arrêt + Étoile favori               Info Trafic     Paramètres
            IdelisSketchTopBar(
                stopName = uiState.selectedStop?.stopName ?: "Pôle Bosquet",
                onStopClick = { isStopPickerOpen = true },
                onTrafficInfoClick = { isTrafficInfoOpen = true },
                onSettingsClick = { isSettingsOpen = true },
                isDarkTheme = uiState.isDarkTheme
            )

            // Indicateur de téléchargement initial en tâche de fond au lancement de l'application
            if (uiState.isDownloadingData) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = SketchRed,
                    trackColor = SketchRed.copy(alpha = 0.2f)
                )
            }

            // 2. Liste épurée des passages prochains avec support du geste "Tirer de haut en bas pour actualiser" (Pull to refresh)
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.refreshDepartures() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("pull_to_refresh_box")
            ) {
                if (uiState.isLoading && uiState.departures.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = if (uiState.isDarkTheme) Color.White else Color(0xFF111111),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Chargement des prochains passages...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = contentTextColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                } else if (uiState.departures.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBus,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Aucun passage en temps réel pour cet arrêt",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = contentTextColor
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Glissez de haut en bas pour actualiser ou sélectionnez un autre arrêt",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("departures_list"),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(
                            items = uiState.departures,
                            key = { it.id }
                        ) { departure ->
                            IdelisDepartureRow(
                                departure = departure,
                                isDarkTheme = uiState.isDarkTheme,
                                onClick = {
                                    viewModel.selectDepartureForRouteDetail(departure)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Modale affichant l'itinéraire complet et les arrêts de la ligne dans l'ordre (terminus en haut, début en bas)
        if (uiState.selectedRouteDetail != null) {
            com.example.ui.components.RouteStopsModal(
                routeDetail = uiState.selectedRouteDetail!!,
                currentStopName = uiState.selectedStop?.stopName ?: "Pôle Bosquet",
                onDirectionSelected = { newDir -> viewModel.switchRouteDetailDirection(newDir) },
                onToggleOrder = { viewModel.toggleRouteDetailOrder() },
                onStopSelected = { stopName -> viewModel.selectStopFromRouteDetail(stopName) },
                onDismissRequest = { viewModel.clearSelectedRouteDetail() },
                isDarkTheme = uiState.isDarkTheme
            )
        }

        // Modale de sélection de l'arrêt avec autocomplétion idelis.fr et favoris
        if (isStopPickerOpen) {
            StopPickerModal(
                stops = uiState.allStops,
                selectedStop = uiState.selectedStop,
                favoriteStopIds = uiState.favoriteStopIds,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { query -> viewModel.updatePickerSearchQuery(query) },
                webSuggestions = uiState.webSuggestions,
                isWebSearching = uiState.isWebSearching,
                onWebStopSelected = { webStop -> viewModel.selectWebStop(webStop) },
                onToggleFavorite = { stopId -> viewModel.toggleFavoriteStop(stopId) },
                onToggleFavoriteWebStop = { webStop -> viewModel.toggleFavoriteWebStop(webStop) },
                onStopSelected = { stop -> viewModel.selectStop(stop) },
                onDismissRequest = {
                    isStopPickerOpen = false
                },
                isDarkTheme = uiState.isDarkTheme
            )
        }

        // Modale d'info trafic (ouverte par '▲ info')
        if (isTrafficInfoOpen) {
            TrafficInfoModal(
                alerts = uiState.trafficAlerts,
                activePeriod = uiState.activePeriod,
                onDismissRequest = { isTrafficInfoOpen = false },
                isDarkTheme = uiState.isDarkTheme
            )
        }

        // Modale de paramètres (ouverte par '◯ paramètres')
        if (isSettingsOpen) {
            SettingsModal(
                isDarkTheme = uiState.isDarkTheme,
                onToggleDarkTheme = { viewModel.toggleDarkTheme() },
                isSimulationMode = uiState.isSimulationMode,
                onToggleSimulationMode = { viewModel.toggleSimulationMode() },
                isAutoRefreshPaused = uiState.isAutoRefreshPaused,
                onToggleAutoRefresh = { viewModel.toggleAutoRefresh() },
                isGtfsSyncing = uiState.isGtfsSyncing,
                gtfsReport = uiState.gtfsSyncReport,
                onDownloadGtfsClick = { viewModel.triggerGtfsZipSync() },
                isPeriodicSyncEnabled = uiState.isPeriodicSyncEnabled,
                onTogglePeriodicSync = { viewModel.togglePeriodicSync(it) },
                onDownloadSupplementaryFilesClick = { viewModel.triggerSupplementaryFilesDownload() },
                webMonitoringUrl = uiState.webMonitoringUrl,
                gtfsZipUrl = uiState.gtfsZipUrl,
                siriSoapUrl = uiState.siriSoapUrl,
                queryTypeParam = uiState.queryTypeParam,
                githubRepo = uiState.githubRepo,
                githubTag = uiState.githubTag,
                lastGtfsSyncTimestamp = uiState.lastGtfsSyncTimestamp,
                onSaveAdvancedSettings = { webUrl, gtfsUrl, siriUrl, queryParam, githubRepo, githubTag ->
                    viewModel.saveAdvancedSettings(webUrl, gtfsUrl, siriUrl, queryParam, githubRepo, githubTag)
                },
                onResetAdvancedUrls = {
                    viewModel.resetAdvancedUrlsToDefault()
                },
                onDismissRequest = { isSettingsOpen = false }
            )
        }
    }
}
