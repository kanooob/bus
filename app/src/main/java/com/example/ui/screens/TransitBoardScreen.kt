package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.NotServedEntry
import com.example.data.model.UpdateNetworkMode
import com.example.data.remote.AppUpdateNotifier
import com.example.data.remote.AppUpdateState
import com.example.data.remote.GitHubReleaseInfo
import com.example.data.remote.GitHubUpdaterService
import com.example.data.remote.formatReleaseNotesForDisplay
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
    effectiveDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = effectiveDarkTheme || uiState.isDarkTheme && uiState.themeMode == com.example.data.model.ThemeMode.DARK

    var isStopPickerOpen by remember { mutableStateOf(false) }
    var isTrafficInfoOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    val updaterHelper = remember { GitHubUpdaterService() }
    var pendingInstallApkPath by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.markNotificationPermissionAsked()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AppUpdateNotifier.hasNotificationPermission(context) &&
            !viewModel.hasAskedNotificationPermission()
        ) {
            viewModel.markNotificationPermissionAsked()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uiState.trafficInfoPromptVisible) {
        if (uiState.trafficInfoPromptVisible) {
            isTrafficInfoOpen = true
            viewModel.refreshTrafficAlerts(notifyIfNew = false)
            viewModel.consumeTrafficInfoPrompt()
        }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val path = pendingInstallApkPath
        if (path != null && updaterHelper.canInstallUnknownApps(context)) {
            val file = File(path)
            if (file.exists()) {
                try {
                    context.startActivity(updaterHelper.buildInstallApkIntent(context, file))
                } catch (e: Exception) {
                    viewModel.reportUpdateError(
                        "Impossible de lancer l'installation : ${e.localizedMessage ?: "erreur"}"
                    )
                }
            } else {
                viewModel.reportUpdateError("Fichier APK introuvable, veuillez retélécharger la mise à jour.")
            }
            pendingInstallApkPath = null
        }
    }

    fun triggerApkInstall(apkFilePath: String) {
        val file = File(apkFilePath)
        if (!file.exists()) {
            viewModel.reportUpdateError("Fichier APK introuvable, veuillez retélécharger la mise à jour.")
            return
        }
        if (updaterHelper.canInstallUnknownApps(context)) {
            try {
                context.startActivity(updaterHelper.buildInstallApkIntent(context, file))
            } catch (e: Exception) {
                viewModel.reportUpdateError(
                    "Impossible de lancer l'installation : ${e.localizedMessage ?: "erreur"}"
                )
            }
        } else {
            pendingInstallApkPath = apkFilePath
            try {
                unknownSourcesLauncher.launch(updaterHelper.buildInstallUnknownAppsIntent(context))
            } catch (e: Exception) {
                viewModel.reportUpdateError(
                    "Impossible d'ouvrir les paramètres d'installation : ${e.localizedMessage ?: "erreur"}"
                )
            }
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val contentTextColor = MaterialTheme.colorScheme.onBackground
    val primaryAccent = MaterialTheme.colorScheme.primary

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
                    viewModel.refreshTrafficAlerts(notifyIfNew = false)
                },
                onSettingsClick = {
                    viewModel.refreshWifiStatus()
                    isSettingsOpen = true
                },
                isDarkTheme = effectiveDarkTheme
            )

            // Indicateur de téléchargement initial en tâche de fond au lancement de l'application
            if (uiState.isDownloadingData) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = primaryAccent,
                    trackColor = primaryAccent.copy(alpha = 0.2f)
                )
            }

            // Bandeau de mise à jour GitHub (téléchargement + installation APK in-app)
            if (uiState.updatePromptVisible) {
                AppUpdateBanner(
                    updateState = uiState.appUpdateState,
                    networkMode = uiState.updateNetworkMode,
                    isWifiConnected = uiState.isWifiConnected,
                    isDarkTheme = effectiveDarkTheme,
                    canInstallUnknownApps = updaterHelper.canInstallUnknownApps(context),
                    onDownloadNow = { viewModel.downloadPendingUpdate(forceWithoutWifi = true) },
                    onRetryCheck = { viewModel.checkForAppUpdate(autoDownloadAllowed = true, manualCheck = true) },
                    onInstallNow = { apkPath -> triggerApkInstall(apkPath) },
                    onDismiss = { viewModel.dismissUpdatePrompt() }
                )
            }

            // 2. Liste épurée des passages prochains avec support du geste "Tirer de haut en bas pour actualiser" (Pull to refresh)
            // L'actualisation automatique périodique reste discrète (isManualRefreshing = false : pas de flèche ronde).
            PullToRefreshBox(
                isRefreshing = uiState.isManualRefreshing,
                onRefresh = { viewModel.refreshDepartures(isManual = true) },
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
                                color = if (effectiveDarkTheme) Color.White else Color(0xFF111111),
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
                    // Affichage compact et discret lorsque l'arrêt n'est pas desservi
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StopNotServedCard(
                            message = uiState.notServedMessage,
                            notServedEntries = uiState.notServedEntries,
                            isDarkTheme = effectiveDarkTheme
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Glissez de haut en bas pour actualiser ou sélectionnez un autre arrêt",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                            textAlign = TextAlign.Center
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
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    StopNotServedCard(
                                        message = uiState.notServedMessage,
                                        notServedEntries = uiState.notServedEntries,
                                        isDarkTheme = effectiveDarkTheme
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
                                isDarkTheme = effectiveDarkTheme,
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
                isDarkTheme = effectiveDarkTheme
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
                isDarkTheme = effectiveDarkTheme
            )
        }

        // Modale d'info trafic (ouverte par 'info', alimentée par POST infostrafic)
        if (isTrafficInfoOpen) {
            TrafficInfoModal(
                alerts = uiState.trafficAlerts,
                activePeriod = uiState.activePeriod,
                isLoading = uiState.isTrafficLoading,
                errorMessage = uiState.trafficErrorMessage,
                onRefresh = { viewModel.refreshTrafficAlerts(notifyIfNew = false) },
                onDismissRequest = { isTrafficInfoOpen = false },
                isDarkTheme = effectiveDarkTheme
            )
        }

        // Modale de paramètres (ouverte par 'paramètres')
        if (isSettingsOpen) {
            SettingsModal(
                isDarkTheme = effectiveDarkTheme,
                themeMode = uiState.themeMode,
                onSelectThemeMode = { viewModel.setThemeMode(it) },
                primaryColorMode = uiState.primaryColorMode,
                onSelectPrimaryColorMode = { viewModel.setPrimaryColorMode(it) },
                inAppRefreshFrequency = uiState.inAppRefreshFrequency,
                onSelectInAppRefreshFrequency = { viewModel.setInAppRefreshFrequency(it) },
                widgetRefreshFrequency = uiState.widgetRefreshFrequency,
                onSelectWidgetRefreshFrequency = { viewModel.setWidgetRefreshFrequency(it) },
                widgetLocationFrequency = uiState.widgetLocationFrequency,
                onSelectWidgetLocationFrequency = { viewModel.setWidgetLocationFrequency(it) },
                gtfsSyncFrequency = uiState.gtfsSyncFrequency,
                onSelectGtfsSyncFrequency = { viewModel.setGtfsSyncFrequency(it) },
                isGtfsSyncing = uiState.isGtfsSyncing,
                gtfsReport = uiState.gtfsSyncReport,
                onDownloadGtfsClick = { viewModel.triggerGtfsZipSync() },
                lastGtfsSyncTimestamp = uiState.lastGtfsSyncTimestamp,
                trafficRefreshFrequency = uiState.trafficRefreshFrequency,
                onSelectTrafficRefreshFrequency = { viewModel.setTrafficRefreshFrequency(it) },
                trafficNotificationsEnabled = uiState.trafficNotificationsEnabled,
                onToggleTrafficNotifications = { viewModel.setTrafficNotificationsEnabled(it) },
                lastTrafficRefreshTimestamp = uiState.lastTrafficRefreshTimestamp,
                appUpdateCheckFrequency = uiState.appUpdateCheckFrequency,
                onSelectAppUpdateCheckFrequency = { viewModel.setAppUpdateCheckFrequency(it) },
                updateNetworkMode = uiState.updateNetworkMode,
                onSelectUpdateNetworkMode = { viewModel.setUpdateNetworkMode(it) },
                appUpdateState = uiState.appUpdateState,
                isWifiConnected = uiState.isWifiConnected,
                onCheckForUpdate = { viewModel.checkForAppUpdate(autoDownloadAllowed = true, manualCheck = true) },
                onDownloadUpdate = { force -> viewModel.downloadPendingUpdate(forceWithoutWifi = force) },
                onInstallUpdate = { apkPath -> triggerApkInstall(apkPath) },
                onExportBackupJson = { viewModel.exportBackupJson() },
                onImportBackupJson = { json -> viewModel.importBackupJson(json) },
                backupRestoreMessage = uiState.backupRestoreMessage,
                isBackupError = uiState.isBackupError,
                onClearBackupMessage = { viewModel.clearBackupRestoreMessage() },
                webMonitoringUrl = uiState.webMonitoringUrl,
                gtfsZipUrl = uiState.gtfsZipUrl,
                siriSoapUrl = uiState.siriSoapUrl,
                queryTypeParam = uiState.queryTypeParam,
                githubRepo = uiState.githubRepo,
                githubTag = uiState.githubTag,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopNotServedCard(
    message: String?,
    notServedEntries: List<NotServedEntry>,
    isDarkTheme: Boolean
) {
    val cardBg = if (isDarkTheme) Color(0xFF2B191C) else Color(0xFFFEF2F2)
    val borderColor = if (isDarkTheme) Color(0xFFEF9A9A).copy(alpha = 0.25f) else Color(0xFFFCA5A5).copy(alpha = 0.7f)
    val titleColor = if (isDarkTheme) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
    val bodyColor = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF334155)
    val mutedColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
    val pillBg = if (isDarkTheme) Color(0xFF3F2327) else Color(0xFFFEE2E2)
    val pillText = if (isDarkTheme) Color(0xFFFECACA) else Color(0xFF991B1B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("stop_not_served_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Arrêt non desservi",
                tint = titleColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Arrêt non desservi",
                color = titleColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (notServedEntries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                notServedEntries.forEach { entry ->
                    val badgeColor = try {
                        Color(android.graphics.Color.parseColor("#" + entry.lineColorHex.removePrefix("#")))
                    } catch (_: Exception) {
                        titleColor
                    }
                    val badgeTextColor = if (entry.lineRef.equals("F", ignoreCase = true)) {
                        Color(0xFF121212)
                    } else {
                        Color.White
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(badgeColor),
                                contentAlignment = Alignment.Center
                            ) {
                                var isImageLoaded by remember(entry.linePictoUrl) { mutableStateOf(false) }

                                if (!entry.linePictoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(entry.linePictoUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Icône ligne ${entry.lineRef}",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(9.dp)),
                                        contentScale = ContentScale.Fit,
                                        onSuccess = { isImageLoaded = true },
                                        onError = { isImageLoaded = false }
                                    )
                                }

                                if (entry.linePictoUrl.isNullOrBlank() || !isImageLoaded) {
                                    Text(
                                        text = entry.lineRef,
                                        color = badgeTextColor,
                                        fontSize = if (entry.lineRef.length > 3) 9.5.sp else 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.direction,
                                    color = bodyColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (entry.times.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(5.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(start = 36.dp)
                            ) {
                                entry.times.forEach { time ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(pillBg)
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = time,
                                            color = pillText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                color = mutedColor,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun AppUpdateBanner(
    updateState: AppUpdateState,
    networkMode: UpdateNetworkMode,
    isWifiConnected: Boolean,
    isDarkTheme: Boolean,
    canInstallUnknownApps: Boolean,
    onDownloadNow: () -> Unit,
    onRetryCheck: () -> Unit,
    onInstallNow: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isError = updateState is AppUpdateState.Error
    val bgColor = when {
        isError -> if (isDarkTheme) Color(0xFF33181C) else Color(0xFFFFEBEE)
        isDarkTheme -> Color(0xFF1A2438)
        else -> Color(0xFFEEF2FF)
    }
    val textColor = when {
        isError -> if (isDarkTheme) Color(0xFFFFCDD2) else Color(0xFF7F1D1D)
        isDarkTheme -> Color.White
        else -> Color(0xFF1E1B4B)
    }
    val accent = if (isError) SketchRed else Color(0xFF4F46E5)

    val release: GitHubReleaseInfo? = when (updateState) {
        is AppUpdateState.UpdateAvailableWaitingWifi -> updateState.release
        is AppUpdateState.Downloading -> updateState.release
        is AppUpdateState.ReadyToInstall -> updateState.release
        is AppUpdateState.Error -> updateState.release
        else -> null
    }

    if (updateState is AppUpdateState.UpdateAvailableWaitingWifi ||
        updateState is AppUpdateState.Downloading ||
        updateState is AppUpdateState.ReadyToInstall ||
        updateState is AppUpdateState.Error
    ) {
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
                            imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val headerText = when (updateState) {
                            is AppUpdateState.UpdateAvailableWaitingWifi ->
                                "Mise à jour ${updateState.release.tagName} disponible"
                            is AppUpdateState.Downloading ->
                                "Téléchargement ${updateState.release.tagName} (${updateState.progressPercent}%)"
                            is AppUpdateState.ReadyToInstall ->
                                "Mise à jour ${updateState.release.tagName} prête !"
                            is AppUpdateState.Error ->
                                updateState.release?.let { "Échec mise à jour ${it.tagName}" }
                                    ?: "Erreur de mise à jour"
                            else -> ""
                        }
                        Text(
                            text = headerText,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Masquer",
                            tint = textColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Titre + description de la release GitHub
                if (release != null) {
                    val formattedNotes = remember(release.releaseNotes) {
                        formatReleaseNotesForDisplay(release.releaseNotes)
                    }
                    val showReleaseName = release.releaseName.isNotBlank() &&
                        !release.releaseName.equals(release.tagName, ignoreCase = true)

                    if (showReleaseName || formattedNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = if (isDarkTheme) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_release_notes")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                if (showReleaseName) {
                                    Text(
                                        text = release.releaseName,
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    if (formattedNotes.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                    }
                                }
                                if (formattedNotes.isNotBlank()) {
                                    Text(
                                        text = formattedNotes,
                                        color = textColor.copy(alpha = 0.88f),
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                when (updateState) {
                    is AppUpdateState.UpdateAvailableWaitingWifi -> {
                        Spacer(modifier = Modifier.height(6.dp))
                        val hasApk = !updateState.release.apkDownloadUrl.isNullOrBlank()
                        val subtitleText = when {
                            !hasApk -> "Aucun fichier APK joint à cette version sur GitHub."
                            isWifiConnected -> "Connexion Wi-Fi détectée — prêt à télécharger l'APK."
                            networkMode == UpdateNetworkMode.WIFI_ONLY ->
                                "En attente d'une connexion Wi-Fi pour télécharger automatiquement."
                            else -> "Prêt à télécharger l'APK."
                        }
                        Text(
                            text = subtitleText,
                            color = textColor.copy(alpha = 0.78f),
                            fontSize = 11.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (hasApk) {
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
                        } else {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(updateState.release.htmlUrl))
                                        )
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ouvrir sur GitHub",
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
                    is AppUpdateState.Error -> {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = updateState.message,
                            color = textColor.copy(alpha = 0.85f),
                            fontSize = 11.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (updateState.release != null && !updateState.release.apkDownloadUrl.isNullOrBlank()) {
                                    onDownloadNow()
                                } else {
                                    onRetryCheck()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("retry_update_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Réessayer",
                                color = accent,
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
}
