package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.R
import com.example.data.local.PreferencesManager
import com.example.data.model.AppUpdateCheckFrequency
import com.example.data.model.GtfsSyncFrequency
import com.example.data.model.GtfsSyncReport
import com.example.data.model.InAppRefreshFrequency
import com.example.data.model.PrimaryColorMode
import com.example.data.model.ThemeMode
import com.example.data.model.TrafficRefreshFrequency
import com.example.data.model.UpdateNetworkMode
import com.example.data.model.WidgetLocationFrequency
import com.example.data.model.WidgetRefreshFrequency
import com.example.data.remote.AppUpdateNotifier
import com.example.data.remote.AppUpdateState
import com.example.data.remote.GitHubReleaseInfo
import com.example.data.remote.NotificationChannels
import com.example.data.remote.formatReleaseNotesForDisplay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SettingsCategory(
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    APPEARANCE(
        title = "Apparence",
        icon = Icons.Default.Palette,
        testTag = "settings_category_appearance"
    ),
    DATA_AND_REFRESH(
        title = "Données et actualisation",
        icon = Icons.Default.Sync,
        testTag = "settings_category_data_refresh"
    ),
    APP_UPDATES(
        title = "Mises à jour de l'application",
        icon = Icons.Default.SystemUpdate,
        testTag = "settings_category_app_updates"
    ),
    ADVANCED(
        title = "Avancé",
        icon = Icons.Default.Tune,
        testTag = "settings_category_advanced"
    )
}

private enum class ActiveSettingsDialog {
    THEME,
    PRIMARY_COLOR,
    IN_APP_REFRESH,
    WIDGET_REFRESH,
    WIDGET_LOCATION,
    GTFS_SYNC,
    TRAFFIC_REFRESH,
    UPDATE_CHECK_FREQ,
    UPDATE_NETWORK,
    ABOUT,
    BACKUP_RESTORE,
    NETWORK_API_CONFIG
}

private const val ANDROID_WIDGET_CONSTRAINT =
    "Contrainte Android : Sous Android, WorkManager impose un intervalle minimum de 15 minutes pour les tâches d'arrière-plan automatiques. En dessous (ex. 1 ou 2 min), le système ou les optimisations d'alimentation (One UI) risquent de geler la tâche, sauf si un rafraîchissement manuel est privilégié."

private const val ANDROID_LOCATION_CONSTRAINT =
    "Contrainte Android : Nécessite la permission de localisation en arrière-plan (« Toujours autoriser ») pour fonctionner lorsque l'application est fermée."

@Composable
fun SettingsModal(
    isDarkTheme: Boolean,
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    primaryColorMode: PrimaryColorMode,
    onSelectPrimaryColorMode: (PrimaryColorMode) -> Unit,
    inAppRefreshFrequency: InAppRefreshFrequency,
    onSelectInAppRefreshFrequency: (InAppRefreshFrequency) -> Unit,
    widgetRefreshFrequency: WidgetRefreshFrequency,
    onSelectWidgetRefreshFrequency: (WidgetRefreshFrequency) -> Unit,
    widgetLocationFrequency: WidgetLocationFrequency,
    onSelectWidgetLocationFrequency: (WidgetLocationFrequency) -> Unit,
    gtfsSyncFrequency: GtfsSyncFrequency,
    onSelectGtfsSyncFrequency: (GtfsSyncFrequency) -> Unit,
    isGtfsSyncing: Boolean = false,
    gtfsReport: GtfsSyncReport? = null,
    onDownloadGtfsClick: () -> Unit = {},
    lastGtfsSyncTimestamp: Long = 0L,
    trafficRefreshFrequency: TrafficRefreshFrequency = TrafficRefreshFrequency.EVERY_2_HOURS,
    onSelectTrafficRefreshFrequency: (TrafficRefreshFrequency) -> Unit = {},
    trafficNotificationsEnabled: Boolean = false,
    onToggleTrafficNotifications: (Boolean) -> Unit = {},
    widgetUpdateNotificationsEnabled: Boolean = true,
    onToggleWidgetUpdateNotifications: (Boolean) -> Unit = {},
    lastTrafficRefreshTimestamp: Long = 0L,
    appUpdateCheckFrequency: AppUpdateCheckFrequency,
    onSelectAppUpdateCheckFrequency: (AppUpdateCheckFrequency) -> Unit,
    updateNetworkMode: UpdateNetworkMode,
    onSelectUpdateNetworkMode: (UpdateNetworkMode) -> Unit,
    appUpdateState: AppUpdateState = AppUpdateState.Idle,
    isWifiConnected: Boolean = false,
    onCheckForUpdate: () -> Unit = {},
    onDownloadUpdate: (forceWithoutWifi: Boolean) -> Unit = {},
    onInstallUpdate: (apkFilePath: String) -> Unit = {},
    onExportBackupJson: () -> String = { "{}" },
    onImportBackupJson: (String) -> Unit = {},
    backupRestoreMessage: String? = null,
    isBackupError: Boolean = false,
    onClearBackupMessage: () -> Unit = {},
    webMonitoringUrl: String = "",
    gtfsZipUrl: String = "",
    siriSoapUrl: String = "",
    queryTypeParam: String = "",
    githubRepo: String = "",
    githubTag: String = "",
    onSaveAdvancedSettings: (webUrl: String, gtfsUrl: String, siriUrl: String, queryParam: String, githubRepo: String, githubTag: String) -> Unit = { _, _, _, _, _, _ -> },
    onResetAdvancedUrls: () -> Unit = {},
    usageStatsEnabled: Boolean = true,
    onToggleUsageStats: (Boolean) -> Unit = {},
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var activeDialog by remember { mutableStateOf<ActiveSettingsDialog?>(null) }
    var localBackupStatus by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    BackHandler(enabled = activeDialog == null) {
        if (selectedCategory != null) {
            selectedCategory = null
        } else {
            onDismissRequest()
        }
    }

    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkTheme) 0.55f else 0.7f)
    val contentColor = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    val lastGtfsFormatted = remember(lastGtfsSyncTimestamp) {
        if (lastGtfsSyncTimestamp > 0L) {
            SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(Date(lastGtfsSyncTimestamp))
        } else {
            "Jamais téléchargé"
        }
    }

    val lastTrafficFormatted = remember(lastTrafficRefreshTimestamp) {
        if (lastTrafficRefreshTimestamp > 0L) {
            SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(Date(lastTrafficRefreshTimestamp))
        } else {
            "Jamais vérifiée"
        }
    }

    var hasNotifPermission by remember {
        mutableStateOf(AppUpdateNotifier.hasNotificationPermission(context))
    }
    val trafficNotifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted || AppUpdateNotifier.hasNotificationPermission(context)
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonContent = onExportBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(jsonContent.toByteArray(Charsets.UTF_8))
                }
                localBackupStatus = "Export JSON enregistré avec succès." to false
            } catch (e: Exception) {
                localBackupStatus = "Erreur d'export : ${e.localizedMessage ?: "Échec d'écriture"}" to true
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonContent = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                }
                if (!jsonContent.isNullOrBlank()) {
                    localBackupStatus = null
                    onImportBackupJson(jsonContent)
                } else {
                    localBackupStatus = "Fichier JSON vide." to true
                }
            } catch (e: Exception) {
                localBackupStatus = "Erreur de lecture : ${e.localizedMessage ?: "Fichier illisible"}" to true
            }
        }
    }

    val foregroundLocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasForegroundLocationPermission(context)) {
            openAppLocationSettings(context)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_dialog"),
        color = bgColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                // En-tête de la page Paramètres ou du sous-menu actif
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (selectedCategory != null) 8.dp else 20.dp,
                            end = 12.dp,
                            top = 12.dp,
                            bottom = 12.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedCategory != null) {
                            IconButton(
                                onClick = { selectedCategory = null },
                                modifier = Modifier.testTag("settings_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Retour aux paramètres",
                                    tint = contentColor
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(primaryColor.copy(alpha = 0.14f))
                                    .testTag("settings_main_icon"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            if (selectedCategory != null) {
                                Text(
                                    text = "Paramètres",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = primaryColor,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = selectedCategory?.title ?: "Paramètres",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    color = contentColor
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.testTag("close_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = contentColor
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Navigation entre le menu principal des catégories et chaque sous-menu
                    AnimatedContent(
                        targetState = selectedCategory,
                        transitionSpec = {
                            if (targetState != null) {
                                (slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(tween(180)) { -it / 4 } + fadeOut(tween(160)))
                            } else {
                                (slideInHorizontally(tween(220)) { -it / 3 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(tween(180)) { it / 4 } + fadeOut(tween(160)))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        label = "settings_submenu_transition"
                    ) { activeCategory ->
                        if (activeCategory == null) {
                            val hasBgLoc = hasBackgroundLocationPermission(context)
                            val dataWarningCount = listOf(
                                widgetRefreshFrequency.intervalMinutes in 1..14,
                                !hasBgLoc && widgetLocationFrequency != WidgetLocationFrequency.MANUAL_ONLY,
                                trafficNotificationsEnabled && (!hasNotifPermission || trafficRefreshFrequency == TrafficRefreshFrequency.NEVER),
                                widgetUpdateNotificationsEnabled && !hasNotifPermission
                            ).count { it }

                            val updateBadgeText = when (appUpdateState) {
                                is AppUpdateState.ReadyToInstall -> "Prêt"
                                is AppUpdateState.UpdateAvailableWaitingWifi -> "Nouveau"
                                is AppUpdateState.Downloading -> "${appUpdateState.progressPercent}%"
                                else -> null
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("settings_categories_menu"),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    SettingsCategoryMenuCard(
                                        category = SettingsCategory.APPEARANCE,
                                        summary = "Thème : ${themeMode.label} • Couleur : ${primaryColorMode.label}",
                                        cardColor = cardColor,
                                        primaryColor = primaryColor,
                                        onClick = { selectedCategory = SettingsCategory.APPEARANCE }
                                    )
                                }

                                item {
                                    val dataSummary = if (isGtfsSyncing) {
                                        "Synchronisation des horaires GTFS en cours..."
                                    } else {
                                        "Appli : ${inAppRefreshFrequency.label} • Widgets : ${widgetRefreshFrequency.label}"
                                    }
                                    val dataBadge = when {
                                        isGtfsSyncing -> "Sync"
                                        dataWarningCount > 0 -> "$dataWarningCount alerte" + if (dataWarningCount > 1) "s" else ""
                                        else -> null
                                    }
                                    SettingsCategoryMenuCard(
                                        category = SettingsCategory.DATA_AND_REFRESH,
                                        summary = dataSummary,
                                        badgeText = dataBadge,
                                        isBadgeWarning = !isGtfsSyncing && dataWarningCount > 0,
                                        isSummaryHighlighted = isGtfsSyncing,
                                        cardColor = cardColor,
                                        primaryColor = primaryColor,
                                        onClick = { selectedCategory = SettingsCategory.DATA_AND_REFRESH }
                                    )
                                }

                                item {
                                    val updateSummary = when (appUpdateState) {
                                        is AppUpdateState.ReadyToInstall -> "Mise à jour ${appUpdateState.release.tagName} prête à installer"
                                        is AppUpdateState.UpdateAvailableWaitingWifi -> "Version ${appUpdateState.release.tagName} disponible"
                                        is AppUpdateState.Downloading -> "Téléchargement ${appUpdateState.release.tagName} (${appUpdateState.progressPercent}%)"
                                        else -> "v${BuildConfig.VERSION_NAME} • Vérif. : ${appUpdateCheckFrequency.label}"
                                    }
                                    SettingsCategoryMenuCard(
                                        category = SettingsCategory.APP_UPDATES,
                                        summary = updateSummary,
                                        badgeText = updateBadgeText,
                                        isSummaryHighlighted = updateBadgeText != null,
                                        cardColor = cardColor,
                                        primaryColor = primaryColor,
                                        onClick = { selectedCategory = SettingsCategory.APP_UPDATES }
                                    )
                                }

                                item {
                                    SettingsCategoryMenuCard(
                                        category = SettingsCategory.ADVANCED,
                                        summary = "Sauvegarde JSON • URLs réseau / API • Compteur d'utilisateurs",
                                        cardColor = cardColor,
                                        primaryColor = primaryColor,
                                        onClick = { selectedCategory = SettingsCategory.ADVANCED }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("settings_submenu_list"),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                when (activeCategory) {
                                    SettingsCategory.APPEARANCE -> {
                                        item {
                                            SettingsSubmenuCard(cardColor = cardColor) {
                                                SettingsOptionRow(
                                                    icon = Icons.Default.DarkMode,
                                                    title = "Thème",
                                                    currentValue = formatValueWithDefault(
                                                        themeMode.label,
                                                        themeMode == ThemeMode.SYSTEM
                                                    ),
                                                    onClick = { activeDialog = ActiveSettingsDialog.THEME },
                                                    testTag = "setting_theme_row"
                                                )

                                                SectionDivider()

                                                SettingsOptionRow(
                                                    icon = Icons.Default.Palette,
                                                    title = "Couleur principale",
                                                    currentValue = formatValueWithDefault(
                                                        primaryColorMode.label,
                                                        primaryColorMode == PrimaryColorMode.APP_DEFAULT
                                                    ),
                                                    supportingNote = if (primaryColorMode == PrimaryColorMode.DYNAMIC && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                                        "Material You nécessite Android 12+ (couleur par défaut utilisée)"
                                                    } else null,
                                                    onClick = { activeDialog = ActiveSettingsDialog.PRIMARY_COLOR },
                                                    testTag = "setting_primary_color_row"
                                                )
                                            }
                                        }
                                    }

                                    SettingsCategory.DATA_AND_REFRESH -> {
                                        item {
                                            SettingsSectionGroup(
                                                sectionTitle = "Actualisation et géolocalisation",
                                                primaryColor = primaryColor,
                                                cardColor = cardColor
                                            ) {
                                                SettingsOptionRow(
                                                    icon = Icons.Default.Refresh,
                                                    title = "Fréquence dans l'application",
                                                    currentValue = formatValueWithDefault(
                                                        inAppRefreshFrequency.label,
                                                        inAppRefreshFrequency == InAppRefreshFrequency.EVERY_1_MIN
                                                    ),
                                                    onClick = { activeDialog = ActiveSettingsDialog.IN_APP_REFRESH },
                                                    testTag = "setting_in_app_refresh_row"
                                                )

                                                SectionDivider()

                                                SettingsOptionRow(
                                                    icon = Icons.Default.Widgets,
                                                    title = "Fréquence des widgets",
                                                    currentValue = formatValueWithDefault(
                                                        widgetRefreshFrequency.label,
                                                        widgetRefreshFrequency == WidgetRefreshFrequency.EVERY_15_MIN
                                                    ),
                                                    constraintNote = ANDROID_WIDGET_CONSTRAINT,
                                                    isConstraintHighlighted = widgetRefreshFrequency.intervalMinutes in 1..14,
                                                    onClick = { activeDialog = ActiveSettingsDialog.WIDGET_REFRESH },
                                                    testTag = "setting_widget_refresh_row"
                                                )

                                                SectionDivider()

                                                val hasBgLoc = hasBackgroundLocationPermission(context)
                                                SettingsOptionRow(
                                                    icon = Icons.Default.LocationOn,
                                                    title = "Géolocalisation du widget « Arrêt proche »",
                                                    currentValue = formatValueWithDefault(
                                                        widgetLocationFrequency.label,
                                                        widgetLocationFrequency == WidgetLocationFrequency.EVERY_15_MIN
                                                    ),
                                                    constraintNote = ANDROID_LOCATION_CONSTRAINT,
                                                    isConstraintHighlighted = !hasBgLoc && widgetLocationFrequency != WidgetLocationFrequency.MANUAL_ONLY,
                                                    onClick = { activeDialog = ActiveSettingsDialog.WIDGET_LOCATION },
                                                    testTag = "setting_widget_location_row"
                                                )
                                            }
                                        }

                                        item {
                                            SettingsSectionGroup(
                                                sectionTitle = "Horaires, info trafic et notifications",
                                                primaryColor = primaryColor,
                                                cardColor = cardColor
                                            ) {
                                                Column {
                                                    SettingsOptionRow(
                                                        icon = Icons.Default.Schedule,
                                                        title = "Mise à jour des horaires (GTFS)",
                                                        currentValue = formatValueWithDefault(
                                                            gtfsSyncFrequency.label,
                                                            gtfsSyncFrequency == GtfsSyncFrequency.DAILY_ON_OPEN
                                                        ),
                                                        supportingNote = "Dernière mise à jour : $lastGtfsFormatted",
                                                        onClick = { activeDialog = ActiveSettingsDialog.GTFS_SYNC },
                                                        testTag = "setting_gtfs_sync_row"
                                                    )

                                                    if (isGtfsSyncing || gtfsReport != null) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            if (isGtfsSyncing) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(14.dp),
                                                                    color = primaryColor,
                                                                    strokeWidth = 2.dp
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    text = "Téléchargement et analyse GTFS en cours...",
                                                                    style = MaterialTheme.typography.bodySmall.copy(color = primaryColor)
                                                                )
                                                            } else if (gtfsReport != null) {
                                                                Text(
                                                                    text = if (gtfsReport.success) {
                                                                        val suffix = if (gtfsReport.usedWaybackFallback) " (Wayback Machine)" else ""
                                                                        "✓ ${gtfsReport.stopsCount} arrêts • ${gtfsReport.routesCount} lignes$suffix"
                                                                    } else {
                                                                        "⚠ ${gtfsReport.errorMessage}"
                                                                    },
                                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                                        color = if (gtfsReport.success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                SectionDivider()

                                                SettingsOptionRow(
                                                    icon = Icons.Default.Traffic,
                                                    title = "Actualisation de l'info trafic",
                                                    currentValue = formatValueWithDefault(
                                                        trafficRefreshFrequency.label,
                                                        trafficRefreshFrequency == TrafficRefreshFrequency.EVERY_2_HOURS
                                                    ),
                                                    supportingNote = "Dernière vérification : $lastTrafficFormatted",
                                                    onClick = { activeDialog = ActiveSettingsDialog.TRAFFIC_REFRESH },
                                                    testTag = "setting_traffic_refresh_row"
                                                )

                                                SectionDivider()

                                                SettingsSwitchRow(
                                                    icon = if (trafficNotificationsEnabled) {
                                                        Icons.Default.NotificationsActive
                                                    } else {
                                                        Icons.Default.Notifications
                                                    },
                                                    title = "Notifications info trafic",
                                                    subtitle = if (trafficNotificationsEnabled) {
                                                        "Activées — notification lors d'une nouvelle info trafic"
                                                    } else {
                                                        "Recevoir une notification lors d'une nouvelle info trafic"
                                                    },
                                                    supportingNote = when {
                                                        trafficNotificationsEnabled && !hasNotifPermission ->
                                                            "Permission de notification requise (appuyez pour autoriser)"
                                                        trafficNotificationsEnabled && trafficRefreshFrequency == TrafficRefreshFrequency.NEVER ->
                                                            "Activez une fréquence d'actualisation ci-dessus pour recevoir des alertes."
                                                        else -> null
                                                    },
                                                    isWarningNote = trafficNotificationsEnabled &&
                                                        (!hasNotifPermission || trafficRefreshFrequency == TrafficRefreshFrequency.NEVER),
                                                    checked = trafficNotificationsEnabled,
                                                    onCheckedChange = { enabled ->
                                                        if (enabled &&
                                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                            !AppUpdateNotifier.hasNotificationPermission(context)
                                                        ) {
                                                            trafficNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                        }
                                                        onToggleTrafficNotifications(enabled)
                                                    },
                                                    testTag = "setting_traffic_notifications_row",
                                                    switchTestTag = "setting_traffic_notifications_switch"
                                                )

                                                SectionDivider()

                                                SettingsSwitchRow(
                                                    icon = Icons.Default.Widgets,
                                                    title = "Notifications actualisation widgets",
                                                    subtitle = if (widgetUpdateNotificationsEnabled) {
                                                        "Silencieuse uniquement pendant l'actualisation des widgets"
                                                    } else {
                                                        "Désactivées pour l'actualisation des widgets"
                                                    },
                                                    supportingNote = if (widgetUpdateNotificationsEnabled && !hasNotifPermission) {
                                                        "Permission de notification requise (appuyez pour autoriser)"
                                                    } else null,
                                                    isWarningNote = widgetUpdateNotificationsEnabled && !hasNotifPermission,
                                                    checked = widgetUpdateNotificationsEnabled,
                                                    onCheckedChange = { enabled ->
                                                        if (enabled &&
                                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                            !AppUpdateNotifier.hasNotificationPermission(context)
                                                        ) {
                                                            trafficNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                        }
                                                        onToggleWidgetUpdateNotifications(enabled)
                                                    },
                                                    testTag = "setting_widget_notifications_row",
                                                    switchTestTag = "setting_widget_notifications_switch"
                                                )

                                                SectionDivider()

                                                SettingsOptionRow(
                                                    icon = Icons.Default.Notifications,
                                                    title = "Catégories de notifications (Infos de l'appli)",
                                                    currentValue = "Mises à jour • Info trafic • Widgets (silencieux)",
                                                    supportingNote = "Configurer chaque catégorie dans les paramètres système Android",
                                                    isSubtitleNeutral = true,
                                                    onClick = { NotificationChannels.openAppNotificationSettings(context) },
                                                    testTag = "setting_notification_categories_row"
                                                )
                                            }
                                        }
                                    }

                                    SettingsCategory.APP_UPDATES -> {
                                        item {
                                            SettingsSubmenuCard(cardColor = cardColor) {
                                                SettingsOptionRow(
                                                    icon = Icons.Default.Update,
                                                    title = "Vérification automatique",
                                                    currentValue = formatValueWithDefault(
                                                        appUpdateCheckFrequency.label,
                                                        appUpdateCheckFrequency == AppUpdateCheckFrequency.DAILY_ON_OPEN
                                                    ),
                                                    onClick = { activeDialog = ActiveSettingsDialog.UPDATE_CHECK_FREQ },
                                                    testTag = "setting_update_check_row"
                                                )

                                                SectionDivider()

                                                SettingsOptionRow(
                                                    icon = Icons.Default.Wifi,
                                                    title = "Réseau autorisé",
                                                    currentValue = formatValueWithDefault(
                                                        updateNetworkMode.label,
                                                        updateNetworkMode == UpdateNetworkMode.WIFI_AND_CELLULAR
                                                    ),
                                                    onClick = { activeDialog = ActiveSettingsDialog.UPDATE_NETWORK },
                                                    testTag = "setting_update_network_row"
                                                )

                                                SectionDivider()

                                                // Vérifier maintenant (Action)
                                                Column {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable(
                                                                enabled = appUpdateState !is AppUpdateState.Checking &&
                                                                    appUpdateState !is AppUpdateState.Downloading
                                                            ) { onCheckForUpdate() }
                                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                                            .testTag("check_github_update_button"),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        SettingsLeadingIcon(
                                                            icon = Icons.Default.SystemUpdate,
                                                            tint = primaryColor
                                                        )
                                                        Spacer(modifier = Modifier.width(14.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = "Vérifier maintenant",
                                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = contentColor
                                                                )
                                                            )
                                                            Text(
                                                                text = when (appUpdateState) {
                                                                    is AppUpdateState.Checking -> "Recherche sur GitHub..."
                                                                    is AppUpdateState.UpToDate -> "Application à jour (v${appUpdateState.currentVersion})"
                                                                    is AppUpdateState.UpdateAvailableWaitingWifi -> "Nouvelle version ${appUpdateState.release.tagName} disponible"
                                                                    is AppUpdateState.Downloading -> "Téléchargement ${appUpdateState.release.tagName} (${appUpdateState.progressPercent}%)"
                                                                    is AppUpdateState.ReadyToInstall -> "Mise à jour ${appUpdateState.release.tagName} prête à installer"
                                                                    is AppUpdateState.Error -> appUpdateState.message
                                                                    AppUpdateState.Idle -> "Rechercher une nouvelle version sur GitHub"
                                                                },
                                                                style = MaterialTheme.typography.bodySmall.copy(
                                                                    color = when (appUpdateState) {
                                                                        is AppUpdateState.UpToDate -> Color(0xFF2E7D32)
                                                                        is AppUpdateState.Error -> MaterialTheme.colorScheme.error
                                                                        is AppUpdateState.UpdateAvailableWaitingWifi,
                                                                        is AppUpdateState.ReadyToInstall -> primaryColor
                                                                        else -> mutedColor
                                                                    }
                                                                )
                                                            )
                                                        }

                                                        if (appUpdateState is AppUpdateState.Checking) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(20.dp),
                                                                color = primaryColor,
                                                                strokeWidth = 2.dp
                                                            )
                                                        } else {
                                                            Icon(
                                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                                contentDescription = null,
                                                                tint = mutedColor
                                                            )
                                                        }
                                                    }

                                                    // Description de la release GitHub lorsqu'une mise à jour est présente
                                                    val updateRelease: GitHubReleaseInfo? = when (appUpdateState) {
                                                        is AppUpdateState.UpdateAvailableWaitingWifi -> appUpdateState.release
                                                        is AppUpdateState.Downloading -> appUpdateState.release
                                                        is AppUpdateState.ReadyToInstall -> appUpdateState.release
                                                        is AppUpdateState.Error -> appUpdateState.release
                                                        else -> null
                                                    }
                                                    if (updateRelease != null) {
                                                        val notesText = remember(updateRelease.releaseNotes) {
                                                            formatReleaseNotesForDisplay(updateRelease.releaseNotes)
                                                        }
                                                        val showTitle = updateRelease.releaseName.isNotBlank() &&
                                                            !updateRelease.releaseName.equals(updateRelease.tagName, ignoreCase = true)
                                                        if (showTitle || notesText.isNotBlank()) {
                                                            Surface(
                                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                                shape = RoundedCornerShape(10.dp),
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(start = 56.dp, end = 16.dp, bottom = 10.dp)
                                                                    .testTag("settings_update_release_notes")
                                                            ) {
                                                                Column(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .heightIn(max = 140.dp)
                                                                        .verticalScroll(rememberScrollState())
                                                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                                                ) {
                                                                    Text(
                                                                        text = if (showTitle) updateRelease.releaseName else "Notes de version (${updateRelease.tagName})",
                                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = contentColor
                                                                        )
                                                                    )
                                                                    if (notesText.isNotBlank()) {
                                                                        Spacer(modifier = Modifier.height(4.dp))
                                                                        Text(
                                                                            text = notesText,
                                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                                color = mutedColor,
                                                                                lineHeight = 16.sp
                                                                            )
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // Contrôles contextuels de téléchargement / installation
                                                    when (appUpdateState) {
                                                        is AppUpdateState.UpdateAvailableWaitingWifi -> {
                                                            if (!appUpdateState.release.apkDownloadUrl.isNullOrBlank()) {
                                                                Button(
                                                                    onClick = { onDownloadUpdate(true) },
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
                                                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                                                    shape = RoundedCornerShape(10.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.CloudDownload,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text(
                                                                        text = if (isWifiConnected) "Télécharger (Wi-Fi)" else "Télécharger maintenant",
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 12.5.sp
                                                                    )
                                                                }
                                                            } else {
                                                                OutlinedButton(
                                                                    onClick = {
                                                                        try {
                                                                            context.startActivity(
                                                                                Intent(Intent.ACTION_VIEW, Uri.parse(appUpdateState.release.htmlUrl))
                                                                            )
                                                                        } catch (_: Exception) {}
                                                                    },
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
                                                                    shape = RoundedCornerShape(10.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Text("Ouvrir la release sur GitHub", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                                                }
                                                            }
                                                        }
                                                        is AppUpdateState.Downloading -> {
                                                            LinearProgressIndicator(
                                                                progress = { (appUpdateState.progressPercent / 100f).coerceIn(0f, 1f) },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(start = 56.dp, end = 16.dp, bottom = 12.dp)
                                                                    .height(6.dp)
                                                                    .clip(RoundedCornerShape(3.dp)),
                                                                color = primaryColor
                                                            )
                                                        }
                                                        is AppUpdateState.ReadyToInstall -> {
                                                            Button(
                                                                onClick = { onInstallUpdate(appUpdateState.apkFilePath) },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(start = 56.dp, end = 16.dp, bottom = 12.dp)
                                                                    .testTag("settings_install_apk_button"),
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = Color(0xFF2E7D32),
                                                                    contentColor = Color.White
                                                                ),
                                                                shape = RoundedCornerShape(10.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.InstallMobile,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text("Installer la mise à jour", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                                            }
                                                        }
                                                        is AppUpdateState.Error -> {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    if (appUpdateState.release != null &&
                                                                        !appUpdateState.release.apkDownloadUrl.isNullOrBlank()
                                                                    ) {
                                                                        onDownloadUpdate(true)
                                                                    } else {
                                                                        onCheckForUpdate()
                                                                    }
                                                                },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
                                                                shape = RoundedCornerShape(10.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Refresh,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text("Réessayer", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                                            }
                                                        }
                                                        else -> {}
                                                    }
                                                }

                                                SectionDivider()

                                                // À propos
                                                SettingsOptionRow(
                                                    icon = Icons.Default.Info,
                                                    title = "À propos",
                                                    currentValue = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                                    supportingNote = githubRepo.ifBlank { PreferencesManager.DEFAULT_GITHUB_REPO },
                                                    onClick = { activeDialog = ActiveSettingsDialog.ABOUT },
                                                    testTag = "setting_about_row"
                                                )
                                            }
                                        }
                                    }

                                    SettingsCategory.ADVANCED -> {
                                        item {
                                            SettingsSubmenuCard(cardColor = cardColor) {
                                                SettingsOptionRow(
                                                    icon = Icons.Default.Backup,
                                                    title = "Sauvegarde et restauration",
                                                    currentValue = "Exporter ou importer les favoris et les paramètres au format JSON.",
                                                    isSubtitleNeutral = true,
                                                    onClick = {
                                                        localBackupStatus = null
                                                        onClearBackupMessage()
                                                        activeDialog = ActiveSettingsDialog.BACKUP_RESTORE
                                                    },
                                                    testTag = "setting_backup_restore_row"
                                                )

                                                SectionDivider()

                                                SettingsOptionRow(
                                                    icon = Icons.Default.Code,
                                                    title = "Configuration réseau / API",
                                                    currentValue = "Personnaliser les URLs des requêtes (SIRI / GTFS).",
                                                    isSubtitleNeutral = true,
                                                    onClick = { activeDialog = ActiveSettingsDialog.NETWORK_API_CONFIG },
                                                    testTag = "setting_network_api_row"
                                                )

                                                SectionDivider()

                                                SettingsSwitchRow(
                                                    icon = Icons.Default.BarChart,
                                                    title = "Compteur d'utilisateurs",
                                                    subtitle = if (usageStatsEnabled) {
                                                        "Activé (par défaut) — statistiques anonymes"
                                                    } else {
                                                        "Désactivé — aucun envoi statistique"
                                                    },
                                                    supportingNote = "Envoie silencieusement la version de l'application et d'Android une fois par jour à l'ouverture.",
                                                    checked = usageStatsEnabled,
                                                    onCheckedChange = onToggleUsageStats,
                                                    testTag = "setting_usage_stats_row",
                                                    switchTestTag = "setting_usage_stats_switch"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }

    // =========================================================================
    // SOUS-DIALOGUES DE SÉLECTION ET CONFIGURATION
    // =========================================================================
    when (activeDialog) {
        ActiveSettingsDialog.THEME -> {
            SingleChoiceSettingsDialog(
                title = "Thème",
                options = ThemeMode.entries,
                selectedOption = themeMode,
                defaultOption = ThemeMode.SYSTEM,
                labelProvider = { it.label },
                onSelect = {
                    onSelectThemeMode(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }

        ActiveSettingsDialog.PRIMARY_COLOR -> {
            SingleChoiceSettingsDialog(
                title = "Couleur principale",
                options = PrimaryColorMode.entries,
                selectedOption = primaryColorMode,
                defaultOption = PrimaryColorMode.APP_DEFAULT,
                labelProvider = { it.label },
                footerContent = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        AndroidConstraintCard(
                            text = "Note : Les couleurs dynamiques (Material You) sont disponibles à partir d'Android 12. Sur cet appareil, la couleur de l'application sera appliquée.",
                            isWarning = false
                        )
                    }
                },
                onSelect = {
                    onSelectPrimaryColorMode(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }

        ActiveSettingsDialog.IN_APP_REFRESH -> {
            SingleChoiceSettingsDialog(
                title = "Fréquence dans l'application",
                options = InAppRefreshFrequency.entries,
                selectedOption = inAppRefreshFrequency,
                defaultOption = InAppRefreshFrequency.EVERY_1_MIN,
                labelProvider = { it.label },
                onSelect = {
                    onSelectInAppRefreshFrequency(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }

        ActiveSettingsDialog.WIDGET_REFRESH -> {
            SingleChoiceSettingsDialog(
                title = "Fréquence des widgets",
                options = WidgetRefreshFrequency.entries,
                selectedOption = widgetRefreshFrequency,
                defaultOption = WidgetRefreshFrequency.EVERY_15_MIN,
                labelProvider = { it.label },
                footerContent = {
                    AndroidConstraintCard(
                        text = ANDROID_WIDGET_CONSTRAINT,
                        isWarning = true
                    )
                },
                onSelect = {
                    onSelectWidgetRefreshFrequency(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }

        ActiveSettingsDialog.WIDGET_LOCATION -> {
            val hasFgLoc = hasForegroundLocationPermission(context)
            val hasBgLoc = hasBackgroundLocationPermission(context)
            SingleChoiceSettingsDialog(
                title = "Géolocalisation du widget « Arrêt proche »",
                options = WidgetLocationFrequency.entries,
                selectedOption = widgetLocationFrequency,
                defaultOption = WidgetLocationFrequency.EVERY_15_MIN,
                labelProvider = { it.label },
                footerContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AndroidConstraintCard(
                            text = ANDROID_LOCATION_CONSTRAINT,
                            isWarning = !hasBgLoc
                        )
                        if (!hasBgLoc) {
                            OutlinedButton(
                                onClick = {
                                    if (!hasFgLoc) {
                                        foregroundLocLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    } else {
                                        openAppLocationSettings(context)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (!hasFgLoc) "Autoriser la localisation" else "Activer « Toujours autoriser »",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                )
                            }
                        } else {
                            Text(
                                text = "✓ Permission de localisation en arrière-plan active",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                },
                onSelect = {
                    onSelectWidgetLocationFrequency(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }

        ActiveSettingsDialog.GTFS_SYNC -> {
            SingleChoiceSettingsDialog(
                title = "Mise à jour des horaires (GTFS)",
                options = GtfsSyncFrequency.entries,
                selectedOption = gtfsSyncFrequency,
                defaultOption = GtfsSyncFrequency.DAILY_ON_OPEN,
                labelProvider = { it.label },
                footerContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Dernier téléchargement : $lastGtfsFormatted",
                            style = MaterialTheme.typography.bodySmall.copy(color = mutedColor)
                        )
                        Button(
                            onClick = {
                                onDownloadGtfsClick()
                                activeDialog = null
                            },
                            enabled = !isGtfsSyncing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("download_gtfs_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isGtfsSyncing) "Téléchargement en cours..." else "Mettre à jour maintenant",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                onSelect = {
                    onSelectGtfsSyncFrequency(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }

        ActiveSettingsDialog.TRAFFIC_REFRESH -> {
            SingleChoiceSettingsDialog(
                title = "Actualisation de l'info trafic",
                options = TrafficRefreshFrequency.entries,
                selectedOption = trafficRefreshFrequency,
                defaultOption = TrafficRefreshFrequency.EVERY_2_HOURS,
                labelProvider = { it.label },
                onSelect = {
                    onSelectTrafficRefreshFrequency(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }

        ActiveSettingsDialog.UPDATE_CHECK_FREQ -> {
            SingleChoiceSettingsDialog(
                title = "Vérification automatique",
                options = AppUpdateCheckFrequency.entries,
                selectedOption = appUpdateCheckFrequency,
                defaultOption = AppUpdateCheckFrequency.DAILY_ON_OPEN,
                labelProvider = { it.label },
                onSelect = {
                    onSelectAppUpdateCheckFrequency(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }

        ActiveSettingsDialog.UPDATE_NETWORK -> {
            SingleChoiceSettingsDialog(
                title = "Réseau autorisé",
                options = UpdateNetworkMode.entries,
                selectedOption = updateNetworkMode,
                defaultOption = UpdateNetworkMode.WIFI_AND_CELLULAR,
                labelProvider = { it.label },
                onSelect = {
                    onSelectUpdateNetworkMode(it)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }

        ActiveSettingsDialog.ABOUT -> {
            val effectiveRepo = githubRepo.ifBlank { PreferencesManager.DEFAULT_GITHUB_REPO }
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = {
                    Text(
                        text = "À propos",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = primaryColor.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.galaxie_bus_logo),
                                        contentDescription = "Logo Galaxie Bus",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Galaxie Bus",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            color = primaryColor
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Version actuelle : v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "ID : ${BuildConfig.APPLICATION_ID}",
                                        style = MaterialTheme.typography.bodySmall.copy(color = mutedColor)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Dépôt GitHub :",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = effectiveRepo,
                            style = MaterialTheme.typography.bodySmall.copy(color = primaryColor)
                        )

                        Button(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effectiveRepo)))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_github_repo_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24292E), contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Accéder au dépôt GitHub", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val releasesUrl = if (effectiveRepo.endsWith("/")) "${effectiveRepo}releases" else "$effectiveRepo/releases"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releasesUrl)))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_github_releases_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Voir les versions (Releases)")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Fermer")
                    }
                }
            )
        }

        ActiveSettingsDialog.BACKUP_RESTORE -> {
            val activeMessage = localBackupStatus?.first ?: backupRestoreMessage
            val activeError = localBackupStatus?.second ?: isBackupError

            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = {
                    Text(
                        text = "Sauvegarde et restauration",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Exporter ou importer les favoris et les paramètres au format JSON.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = mutedColor)
                        )

                        Button(
                            onClick = {
                                val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                exportJsonLauncher.launch("galaxie_bus_backup_$dateSuffix.json")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_backup_json_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exporter (JSON)", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_backup_json_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Importer (JSON)", fontWeight = FontWeight.Bold)
                        }

                        if (!activeMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (activeError) {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                } else {
                                    Color(0xFF2E7D32).copy(alpha = 0.12f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = activeMessage,
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (activeError) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Fermer")
                    }
                }
            )
        }

        ActiveSettingsDialog.NETWORK_API_CONFIG -> {
            var editWebUrl by remember(webMonitoringUrl) { mutableStateOf(webMonitoringUrl) }
            var editGtfsUrl by remember(gtfsZipUrl) { mutableStateOf(gtfsZipUrl) }
            var editSiriUrl by remember(siriSoapUrl) { mutableStateOf(siriSoapUrl) }
            var editQueryParam by remember(queryTypeParam) { mutableStateOf(queryTypeParam) }
            var editGithubRepo by remember(githubRepo) { mutableStateOf(githubRepo) }
            var editGithubTag by remember(githubTag) { mutableStateOf(githubTag) }

            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = {
                    Text(
                        text = "Configuration réseau / API",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Personnaliser les URLs des requêtes (SIRI / GTFS).",
                            style = MaterialTheme.typography.bodySmall.copy(color = mutedColor)
                        )

                        OutlinedTextField(
                            value = editWebUrl,
                            onValueChange = { editWebUrl = it },
                            label = { Text("URL Requêtes Temps Réel") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_web_url"),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )

                        OutlinedTextField(
                            value = editGtfsUrl,
                            onValueChange = { editGtfsUrl = it },
                            label = { Text("URL Fichier ZIP GTFS") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_gtfs_url"),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )

                        OutlinedTextField(
                            value = editSiriUrl,
                            onValueChange = { editSiriUrl = it },
                            label = { Text("URL Endpoint SIRI SOAP") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_siri_url"),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )

                        OutlinedTextField(
                            value = editQueryParam,
                            onValueChange = { editQueryParam = it },
                            label = { Text("Paramètre de requête (?type=)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_query_param"),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )

                        OutlinedTextField(
                            value = editGithubRepo,
                            onValueChange = { editGithubRepo = it },
                            label = { Text("Dépôt GitHub") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_github_repo"),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )

                        OutlinedTextField(
                            value = editGithubTag,
                            onValueChange = { editGithubTag = it },
                            label = { Text("Branche ou Tag GitHub") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_github_tag"),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )

                        OutlinedButton(
                            onClick = {
                                onResetAdvancedUrls()
                                editWebUrl = PreferencesManager.DEFAULT_WEB_URL
                                editGtfsUrl = PreferencesManager.DEFAULT_GTFS_ZIP_URL
                                editSiriUrl = PreferencesManager.DEFAULT_SIRI_URL
                                editQueryParam = PreferencesManager.DEFAULT_QUERY_TYPE
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Réinitialiser les URLs par défaut")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Annuler")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onSaveAdvancedSettings(
                                editWebUrl,
                                editGtfsUrl,
                                editSiriUrl,
                                editQueryParam,
                                editGithubRepo,
                                editGithubTag
                            )
                            activeDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        modifier = Modifier.testTag("save_settings_button")
                    ) {
                        Text("Enregistrer", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        null -> {}
    }
}

@Composable
private fun SettingsCategoryMenuCard(
    category: SettingsCategory,
    summary: String,
    cardColor: Color,
    primaryColor: Color,
    badgeText: String? = null,
    isBadgeWarning: Boolean = false,
    isSummaryHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(category.testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(primaryColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isSummaryHighlighted) primaryColor else mutedColor,
                        fontWeight = if (isSummaryHighlighted) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!badgeText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                val badgeBg = if (isBadgeWarning) {
                    Color(0xFFF59E0B).copy(alpha = 0.16f)
                } else {
                    primaryColor.copy(alpha = 0.16f)
                }
                val badgeFg = if (isBadgeWarning) Color(0xFFD97706) else primaryColor
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = badgeFg,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = mutedColor
            )
        }
    }
}

@Composable
private fun SettingsSubmenuCard(
    cardColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsSectionGroup(
    sectionTitle: String,
    primaryColor: Color,
    cardColor: Color,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = sectionTitle,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Black,
                color = primaryColor,
                letterSpacing = 0.3.sp
            ),
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        SettingsSubmenuCard(cardColor = cardColor, content = content)
    }
}

@Composable
private fun SettingsOptionRow(
    icon: ImageVector,
    title: String,
    currentValue: String,
    supportingNote: String? = null,
    constraintNote: String? = null,
    isConstraintHighlighted: Boolean = false,
    isSubtitleNeutral: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsLeadingIcon(icon = icon, tint = primaryColor)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isSubtitleNeutral) mutedColor else primaryColor,
                        fontWeight = if (isSubtitleNeutral) FontWeight.Normal else FontWeight.SemiBold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!supportingNote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = supportingNote,
                        style = MaterialTheme.typography.labelSmall.copy(color = mutedColor),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = mutedColor
            )
        }

        if (!constraintNote.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            AndroidConstraintCard(
                text = constraintNote,
                isWarning = isConstraintHighlighted,
                modifier = Modifier.padding(start = 50.dp)
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    supportingNote: String? = null,
    isWarningNote: Boolean = false,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    switchTestTag: String
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsLeadingIcon(icon = icon, tint = primaryColor)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (checked) primaryColor else mutedColor,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
                )
            )
            if (!supportingNote.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = supportingNote,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isWarningNote) Color(0xFFD97706) else mutedColor,
                        fontWeight = if (isWarningNote) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = primaryColor
            ),
            modifier = Modifier.testTag(switchTestTag)
        )
    }
}

@Composable
private fun SettingsLeadingIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    )
}

@Composable
private fun AndroidConstraintCard(
    text: String,
    isWarning: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isWarning) {
        Color(0xFFF59E0B).copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    }
    val iconColor = if (isWarning) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = if (isWarning) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = textColor,
                lineHeight = 15.sp
            )
        )
    }
}

@Composable
private fun <T> SingleChoiceSettingsDialog(
    title: String,
    options: List<T>,
    selectedOption: T,
    defaultOption: T,
    labelProvider: (T) -> String,
    footerContent: (@Composable () -> Unit)? = null,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option == selectedOption
                    val isDefault = option == defaultOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(option) }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = primaryColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = labelProvider(option),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                            if (isDefault) {
                                Text(
                                    text = "Par défaut",
                                    style = MaterialTheme.typography.labelSmall.copy(color = primaryColor)
                                )
                            }
                        }
                    }
                }

                if (footerContent != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    footerContent()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

private fun formatValueWithDefault(label: String, isDefault: Boolean): String {
    return if (isDefault) "$label (défaut)" else label
}

private fun hasForegroundLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun hasBackgroundLocationPermission(context: Context): Boolean {
    if (!hasForegroundLocationPermission(context)) return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun openAppLocationSettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}
