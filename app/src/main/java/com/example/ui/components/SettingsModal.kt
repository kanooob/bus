package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.GtfsSyncReport
import com.example.data.remote.AppUpdateState
import com.example.ui.theme.SketchBlack
import com.example.ui.theme.SketchRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsModal(
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    isSimulationMode: Boolean = false,
    onToggleSimulationMode: () -> Unit = {},
    isAutoRefreshPaused: Boolean,
    onToggleAutoRefresh: () -> Unit,
    isGtfsSyncing: Boolean = false,
    gtfsReport: GtfsSyncReport? = null,
    onDownloadGtfsClick: () -> Unit = {},
    isPeriodicSyncEnabled: Boolean = true,
    onTogglePeriodicSync: (Boolean) -> Unit = {},
    onDownloadSupplementaryFilesClick: () -> Unit = {},
    webMonitoringUrl: String = "",
    gtfsZipUrl: String = "",
    siriSoapUrl: String = "",
    queryTypeParam: String = "",
    githubRepo: String = "",
    githubTag: String = "",
    lastGtfsSyncTimestamp: Long = 0L,
    appUpdateState: AppUpdateState = AppUpdateState.Idle,
    isWifiConnected: Boolean = false,
    onCheckForUpdate: () -> Unit = {},
    onDownloadUpdate: (forceWithoutWifi: Boolean) -> Unit = {},
    onInstallUpdate: (apkFilePath: String) -> Unit = {},
    onSaveAdvancedSettings: (webUrl: String, gtfsUrl: String, siriUrl: String, queryParam: String, githubRepo: String, githubTag: String) -> Unit = { _, _, _, _, _, _ -> },
    onResetAdvancedUrls: () -> Unit = {},
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bgColor = if (isDarkTheme) Color(0xFF141B26) else Color.White
    val titleColor = if (isDarkTheme) Color.White else SketchBlack
    val contentColor = if (isDarkTheme) Color(0xFFF0F4F8) else SketchBlack
    val surfaceColor = if (isDarkTheme) Color(0xFF1C2636) else Color(0xFFF7F7F7)

    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var isGithubExpanded by remember { mutableStateOf(false) }

    var editWebUrl by remember(webMonitoringUrl) { mutableStateOf(webMonitoringUrl) }
    var editGtfsUrl by remember(gtfsZipUrl) { mutableStateOf(gtfsZipUrl) }
    var editSiriUrl by remember(siriSoapUrl) { mutableStateOf(siriSoapUrl) }
    var editQueryParam by remember(queryTypeParam) { mutableStateOf(queryTypeParam) }
    var editGithubRepo by remember(githubRepo) { mutableStateOf(githubRepo) }
    var editGithubTag by remember(githubTag) { mutableStateOf(githubTag) }

    val lastGtfsFormatted = remember(lastGtfsSyncTimestamp) {
        if (lastGtfsSyncTimestamp > 0) {
            val sdf = SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE)
            sdf.format(Date(lastGtfsSyncTimestamp))
        } else {
            "Jamais téléchargé"
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = bgColor,
        titleContentColor = titleColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.testTag("settings_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = titleColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Paramètres",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = titleColor
                        )
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer", tint = contentColor)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Thème Sombre
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(surfaceColor)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mode Sombre",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        )
                        Text(
                            text = if (isDarkTheme) "Thème sombre galaxie" else "Thème clair",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }

                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onToggleDarkTheme() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SketchRed,
                            checkedTrackColor = SketchRed.copy(alpha = 0.3f)
                        )
                    )
                }

                // Rafraîchissement automatique 1 min
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(surfaceColor)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-actualisation (1 min)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        )
                        Text(
                            text = if (!isAutoRefreshPaused) "Actif toutes les 60s" else "En pause",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                    }

                    Switch(
                        checked = !isAutoRefreshPaused,
                        onCheckedChange = { onToggleAutoRefresh() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SketchRed,
                            checkedTrackColor = SketchRed.copy(alpha = 0.3f)
                        )
                    )
                }

                // Référentiel GTFS ZIP Idelis (Téléchargé au plus 1 fois par jour)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(surfaceColor)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Référentiel GTFS Zip Idelis",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                            )
                            Text(
                                text = "Téléchargé au max 1x par jour à l'ouverture",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Dernier téléchargement : $lastGtfsFormatted",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontSize = 11.sp)
                            )
                        }

                        if (isGtfsSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = SketchRed,
                                strokeWidth = 2.5.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onDownloadGtfsClick,
                        enabled = !isGtfsSyncing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("download_gtfs_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SketchRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isGtfsSyncing) "Téléchargement & Analyse..." else "📥 Forcer le téléchargement GTFS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }

                    if (gtfsReport != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (gtfsReport.success) {
                                "✓ ${gtfsReport.stopsCount} arrêts • ${gtfsReport.routesCount} lignes analysés"
                            } else {
                                "⚠ ${gtfsReport.errorMessage}"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (gtfsReport.success) Color(0xFF2E7D32) else SketchRed,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // SECTION 1 DEMANDÉE : PARAMÈTRES AVANCÉS (URLs & Requêtes)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(surfaceColor)
                        .padding(12.dp)
                        .testTag("advanced_settings_section")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = Color(0xFF0288D1),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Avancé : URLs & Requêtes",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = contentColor
                                    )
                                )
                                Text(
                                    text = "Personnaliser les endpoints temps réel et GTFS",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                                )
                            }
                        }

                        Icon(
                            imageVector = if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isAdvancedExpanded) "Replier" else "Déplier",
                            tint = contentColor
                        )
                    }

                    AnimatedVisibility(visible = isAdvancedExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "URL Requêtes Temps Réel / Autocomplete :",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = contentColor)
                            )
                            OutlinedTextField(
                                value = editWebUrl,
                                onValueChange = { editWebUrl = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_web_url"),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                text = "URL Fichier ZIP GTFS Idelis :",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = contentColor)
                            )
                            OutlinedTextField(
                                value = editGtfsUrl,
                                onValueChange = { editGtfsUrl = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_gtfs_url"),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                text = "URL Endpoint SIRI SOAP :",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = contentColor)
                            )
                            OutlinedTextField(
                                value = editSiriUrl,
                                onValueChange = { editSiriUrl = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_siri_url"),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                text = "Paramètre de requête (?type=) :",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = contentColor)
                            )
                            OutlinedTextField(
                                value = editQueryParam,
                                onValueChange = { editQueryParam = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_query_param"),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onResetAdvancedUrls()
                                        editWebUrl = com.example.data.local.PreferencesManager.DEFAULT_WEB_URL
                                        editGtfsUrl = com.example.data.local.PreferencesManager.DEFAULT_GTFS_ZIP_URL
                                        editSiriUrl = com.example.data.local.PreferencesManager.DEFAULT_SIRI_URL
                                        editQueryParam = com.example.data.local.PreferencesManager.DEFAULT_QUERY_TYPE
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Réinitialiser URLs", fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }

                // SECTION 2 DEMANDÉE : GITHUB & CHANGEMENT FACILE DE VERSION
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(surfaceColor)
                        .padding(12.dp)
                        .testTag("github_version_section")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isGithubExpanded = !isGithubExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Versions & GitHub",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = contentColor
                                    )
                                )
                                Text(
                                    text = "Changer facilement de version sur GitHub",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                                )
                            }
                        }

                        Icon(
                            imageVector = if (isGithubExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isGithubExpanded) "Replier" else "Déplier",
                            tint = contentColor
                        )
                    }

                    AnimatedVisibility(visible = isGithubExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF6366F1).copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "📦 ${BuildConfig.APPLICATION_ID} • v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = if (isDarkTheme) Color(0xFFA5B4FC) else Color(0xFF4338CA)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Wifi,
                                            contentDescription = null,
                                            tint = if (isWifiConnected) Color(0xFF2E7D32) else Color.Gray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isWifiConnected) "Wi-Fi" else "Hors Wi-Fi",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isWifiConnected) Color(0xFF2E7D32) else Color.Gray
                                        )
                                    }
                                }
                            }

                            // Bouton vérifier les mises à jour sur GitHub
                            Button(
                                onClick = onCheckForUpdate,
                                enabled = appUpdateState !is AppUpdateState.Checking && appUpdateState !is AppUpdateState.Downloading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("check_github_update_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4F46E5),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (appUpdateState is AppUpdateState.Checking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Vérification GitHub...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                } else {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Vérifier les mises à jour", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // Affichage dynamique de l'état de mise à jour
                            when (appUpdateState) {
                                is AppUpdateState.UpToDate -> {
                                    Text(
                                        text = "✓ Application à jour (v${appUpdateState.currentVersion})",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                is AppUpdateState.UpdateAvailableWaitingWifi -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Nouvelle version disponible : ${appUpdateState.release.tagName}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.5.sp,
                                            color = contentColor
                                        )
                                        Text(
                                            text = if (isWifiConnected) {
                                                "Prête au téléchargement en Wi-Fi."
                                            } else {
                                                "En attente du Wi-Fi pour télécharger automatiquement l'APK."
                                            },
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                        if (!appUpdateState.release.apkDownloadUrl.isNullOrBlank()) {
                                            Button(
                                                onClick = { onDownloadUpdate(true) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFFD97706),
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isWifiConnected) "Télécharger (Wi-Fi)" else "Télécharger maintenant",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                is AppUpdateState.Downloading -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0288D1).copy(alpha = 0.12f))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Téléchargement ${appUpdateState.release.tagName} (${appUpdateState.progressPercent}%)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = contentColor
                                        )
                                        LinearProgressIndicator(
                                            progress = { (appUpdateState.progressPercent / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                            color = Color(0xFF0288D1)
                                        )
                                    }
                                }
                                is AppUpdateState.ReadyToInstall -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF2E7D32).copy(alpha = 0.12f))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Mise à jour ${appUpdateState.release.tagName} téléchargée !",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.5.sp,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Button(
                                            onClick = { onInstallUpdate(appUpdateState.apkFilePath) },
                                            modifier = Modifier.fillMaxWidth().testTag("settings_install_apk_button"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF2E7D32),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Installer la mise à jour", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                                is AppUpdateState.Error -> {
                                    Text(
                                        text = "⚠ ${appUpdateState.message}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = SketchRed,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                                AppUpdateState.Idle, AppUpdateState.Checking -> {}
                            }

                            Text(
                                text = "Dépôt GitHub du projet :",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = contentColor)
                            )
                            OutlinedTextField(
                                value = editGithubRepo,
                                onValueChange = { editGithubRepo = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_github_repo"),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                text = "Branche ou Version / Tag ciblé (ex: main, v1.0, latest) :",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = contentColor)
                            )
                            OutlinedTextField(
                                value = editGithubTag,
                                onValueChange = { editGithubTag = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_github_tag"),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )

                            // Bouton d'accès direct aux versions / releases GitHub
                            Button(
                                onClick = {
                                    try {
                                        val releaseUrl = if (editGithubRepo.endsWith("/")) "${editGithubRepo}releases" else "${editGithubRepo}/releases"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.fillMaxWidth().testTag("open_github_releases_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF24292E),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ouvrir les versions sur GitHub", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
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
                    onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SketchRed, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Text("Enregistrer les réglages", fontWeight = FontWeight.Bold)
            }
        }
    )
}
