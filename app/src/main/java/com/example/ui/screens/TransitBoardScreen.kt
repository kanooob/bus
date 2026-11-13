package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.AppUpdateState
import com.example.data.remote.GitHubUpdaterService
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
import java.io.File

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
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var isStopPickerOpen by remember { mutableStateOf(false) }
    var isTrafficInfoOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    val updaterHelper = remember { GitHubUpdaterService() }
    var pendingInstallApkPath by remember { mutableStateOf<String?>(null) }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val path = pendingInstallApkPath
        if (path != null && updaterHelper.canInstallUnknownApps(context)) {
            val file = File(path)
            if (file.exists()) {
                try {
                    context.startActivity(updaterHelper.buildInstallApkIntent(context, file))
                } catch (_: Exception) {}
            }
            pendingInstallApkPath = null
        }
    }

    fun triggerApkInstall(apkFilePath: String) {
        val file = File(apkFilePath)
        if (!file.exists()) return
        if (updaterHelper.canInstallUnknownApps(context)) {
            try {
                context.startActivity(updaterHelper.buildInstallApkIntent(context, file))
            } catch (_: Exception) {}
        } else {
            pendingInstallApkPath = apkFilePath
            try {
                unknownSourcesLauncher.launch(updaterHelper.buildInstallUnknownAppsIntent(context))
            } catch (_: Exception) {}
        }
    }

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
            // Nom de l'arrêt                                  Info Trafic     Paramètres
            IdelisSketchTopBar(
                stopName = uiState.selectedStop?.stopName ?: "Pôle Bosquet",
                onStopClick = { isStopPickerOpen = true },
                onTrafficInfoClick = {
                    isTrafficInfoOpen = true
                    viewModel.refreshTrafficAlerts()
                },
                onSettingsClick = {
                    viewModel.refreshWifiStatus()
                    isSettingsOpen = true
                },
                isDarkTheme = uiState.isDarkTheme
            )

            // Indicateur de téléchargement initial en tâche de fond au lancement de l'application
            if (uiState.isDownloadingData) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = SketchRed,
                    trackColor = SketchRed.copy(alpha = 0.2f)
                )
            }

            // Bandeau de mise à jour GitHub (téléchargement Wi-Fi + installation APK in-app)
            if (uiState.updatePromptVisible) {
                AppUpdateBanner(
                    updateState = uiState.appUpdateState,
                    isWifiConnected = uiState.isWifiConnected,
                    isDarkTheme = uiState.isDarkTheme,
                    canInstallUnknownApps = updaterHelper.canInstallUnknownApps(context),
                    onDownloadNow = { viewModel.downloadPendingUpdate(forceWithoutWifi = true) },
                    onInstallNow = { apkPath -> triggerApkInstall(apkPath) },
                    onDismiss = { viewModel.dismissUpdatePrompt() }
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
                            .verticalScroll(rememberScrollState()),
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
                } else if (uiState.isStopNotServed && uiState.departures.isEmpty()) {
                    // Affichage EN GROS que l'arrêt n'est pas desservi
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        StopNotServedCard(
                            message = uiState.notServedMessage,
                            isDarkTheme = uiState.isDarkTheme,
                            isFullScreen = true
                        )
                    }
                } else if (uiState.departures.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
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
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Glissez de haut en bas pour actualiser ou sélectionnez un autre arrêt",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                                textAlign = TextAlign.Center
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
                        if (uiState.isStopNotServed) {
                            item(key = "stop_not_served_banner") {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    StopNotServedCard(
                                        message = uiState.notServedMessage,
                                        isDarkTheme = uiState.isDarkTheme,
                                        isFullScreen = false
                                    )
                                }
                            }
                        }

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
        // Conserve le texte recherché ET les résultats après sélection d'un arrêt
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

        // Modale d'info trafic (ouverte par 'info', alimentée par POST infostrafic)
        if (isTrafficInfoOpen) {
            TrafficInfoModal(
                alerts = uiState.trafficAlerts,
                activePeriod = uiState.activePeriod,
                isLoading = uiState.isTrafficLoading,
                errorMessage = uiState.trafficErrorMessage,
                onRefresh = { viewModel.refreshTrafficAlerts() },
                onDismissRequest = { isTrafficInfoOpen = false },
                isDarkTheme = uiState.isDarkTheme
            )
        }

        // Modale de paramètres (ouverte par 'paramètres')
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
                appUpdateState = uiState.appUpdateState,
                isWifiConnected = uiState.isWifiConnected,
                onCheckForUpdate = { viewModel.checkForAppUpdate(autoDownloadOnWifi = true, manualCheck = true) },
                onDownloadUpdate = { force -> viewModel.downloadPendingUpdate(forceWithoutWifi = force) },
                onInstallUpdate = { apkPath -> triggerApkInstall(apkPath) },
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

@Composable
private fun StopNotServedCard(
    message: String?,
    isDarkTheme: Boolean,
    isFullScreen: Boolean
) {
    val cardBg = if (isDarkTheme) Color(0xFF311419) else Color(0xFFFFEBEE)
    val titleColor = if (isDarkTheme) Color(0xFFFF8A80) else Color(0xFFB71C1C)
    val bodyColor = if (isDarkTheme) Color(0xFFFDE8E8) else Color(0xFF4A1010)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(2.dp, SketchRed.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(if (isFullScreen) 24.dp else 16.dp)
            .testTag("stop_not_served_banner"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = "Arrêt non desservi",
            tint = titleColor,
            modifier = Modifier.size(if (isFullScreen) 64.dp else 40.dp)
        )
        Spacer(modifier = Modifier.height(if (isFullScreen) 14.dp else 8.dp))
        Text(
            text = "CET ARRÊT N'EST PAS DESSERVI",
            color = titleColor,
            fontSize = if (isFullScreen) 24.sp else 19.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = if (isFullScreen) 30.sp else 24.sp
        )
        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = bodyColor,
                fontSize = if (isFullScreen) 15.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
        if (isFullScreen) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Glissez de haut en bas pour actualiser ou sélectionnez un autre arrêt.",
                color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF64748B),
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AppUpdateBanner(
    updateState: AppUpdateState,
    isWifiConnected: Boolean,
    isDarkTheme: Boolean,
    canInstallUnknownApps: Boolean,
    onDownloadNow: () -> Unit,
    onInstallNow: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val bgColor = if (isDarkTheme) Color(0xFF1A2438) else Color(0xFFEEF2FF)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF1E1B4B)
    val accent = Color(0xFF4F46E5)

    when (updateState) {
        is AppUpdateState.UpdateAvailableWaitingWifi,
        is AppUpdateState.Downloading,
        is AppUpdateState.ReadyToInstall -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("app_update_banner"),
                colors = CardDefaults.cardColors(containerColor = bgColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val headerText = when (updateState) {
                                is AppUpdateState.UpdateAvailableWaitingWifi ->
                                    "Mise à jour ${updateState.release.tagName} disponible"
                                is AppUpdateState.Downloading ->
                                    "Téléchargement Wi-Fi ${updateState.release.tagName} (${updateState.progressPercent}%)"
                                is AppUpdateState.ReadyToInstall ->
                                    "Mise à jour ${updateState.release.tagName} prête !"
                                else -> ""
                            }
                            Text(
                                text = headerText,
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Masquer",
                                tint = textColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    when (updateState) {
                        is AppUpdateState.UpdateAvailableWaitingWifi -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isWifiConnected) {
                                    "Connexion Wi-Fi détectée — prêt à télécharger l'APK."
                                } else {
                                    "En attente d'une connexion Wi-Fi pour télécharger automatiquement."
                                },
                                color = textColor.copy(alpha = 0.78f),
                                fontSize = 11.5.sp
                            )
                            if (!updateState.release.apkDownloadUrl.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onDownloadNow,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isWifiConnected) "Télécharger maintenant" else "Télécharger sans Wi-Fi",
                                        color = accent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        is AppUpdateState.Downloading -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (updateState.progressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = accent
                            )
                        }
                        is AppUpdateState.ReadyToInstall -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onInstallNow(updateState.apkFilePath) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("install_update_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InstallMobile,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (canInstallUnknownApps) {
                                        "Installer la mise à jour"
                                    } else {
                                        "Autoriser et installer la mise à jour"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
        else -> {}
    }
}
