package com.example.ui.widget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.GtfsDatabaseSeeder
import com.example.data.local.IdelisDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.DepartureComparison
import com.example.data.model.GtfsStop
import com.example.data.model.IdelisWebStop
import com.example.data.model.PrimaryColorMode
import com.example.data.model.ThemeMode
import com.example.data.model.WidgetLocationFrequency
import com.example.data.model.WidgetRefreshFrequency
import com.example.data.remote.GtfsScheduleIndex
import com.example.data.remote.StopServing
import com.example.data.repository.IdelisRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class IdelisWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val extras = intent?.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Initialisation par défaut si le widget vient d'être ajouté
        if (!IdelisWidgetPreferences.isWidgetConfigured(this, appWidgetId)) {
            val defaultStop = GtfsDatabaseSeeder.INITIAL_STOPS.first()
            IdelisWidgetPreferences.saveWidgetStop(
                context = this,
                appWidgetId = appWidgetId,
                stopId = defaultStop.stopId,
                stopName = defaultStop.stopName,
                stopDesc = defaultStop.stopDesc ?: ""
            )
            val appWidgetManager = AppWidgetManager.getInstance(this)
            IdelisAppWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        }

        val defaultResult = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, defaultResult)

        val prefs = PreferencesManager(this)
        val themeMode = prefs.getThemeMode()
        val primaryColorMode = prefs.getPrimaryColorMode()

        setContent {
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val effectiveDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            MyApplicationTheme(
                darkTheme = effectiveDark,
                dynamicColor = primaryColorMode == PrimaryColorMode.DYNAMIC
            ) {
                WidgetConfigureScreen(
                    appWidgetId = appWidgetId,
                    onConfigConfirmed = { finalConfig, refreshFreq, locationFreq ->
                        confirmWidgetConfiguration(finalConfig, refreshFreq, locationFreq)
                    },
                    onCancel = {
                        val resultValue = Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        setResult(Activity.RESULT_OK, resultValue)
                        finish()
                    }
                )
            }
        }
    }

    private fun confirmWidgetConfiguration(
        config: WidgetConfig,
        refreshFreq: WidgetRefreshFrequency,
        locationFreq: WidgetLocationFrequency
    ) {
        val context = this
        val prefs = PreferencesManager(context)
        prefs.saveWidgetRefreshFrequency(refreshFreq)
        prefs.saveWidgetLocationFrequency(locationFreq)

        IdelisWidgetPreferences.saveWidgetConfig(context, config)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        IdelisAppWidgetProvider.updateAppWidget(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = appWidgetId,
            postSilentNotification = false
        )

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WidgetConfigureScreen(
    appWidgetId: Int,
    onConfigConfirmed: (WidgetConfig, WidgetRefreshFrequency, WidgetLocationFrequency) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler(onBack = onCancel)

    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Arrêt, 1 = Apparence & Menu
    var stops by remember { mutableStateOf<List<GtfsStop>>(emptyList()) }
    var favoriteStopIds by remember { mutableStateOf(prefs.getFavoriteStopIds()) }
    var searchQuery by remember { mutableStateOf("") }
    var webSuggestions by remember { mutableStateOf<List<IdelisWebStop>>(emptyList()) }
    var isWebSearching by remember { mutableStateOf(false) }

    val initialConfig = remember(appWidgetId) {
        IdelisWidgetPreferences.getWidgetConfig(context, appWidgetId)
    }
    var widgetConfig by remember(appWidgetId) { mutableStateOf(initialConfig) }
    var resolvedNearestName by remember(appWidgetId) {
        mutableStateOf(
            IdelisWidgetPreferences.getLastNearestStopName(context, appWidgetId)
                ?: initialConfig.stopName
                    .replace("📍", "")
                    .trim()
                    .takeIf { it.isNotBlank() && !it.equals("Arrêt le plus proche", ignoreCase = true) }
                ?: GtfsDatabaseSeeder.INITIAL_STOPS.first().stopName
        )
    }
    var selectedStop by remember(appWidgetId) {
        mutableStateOf<GtfsStop?>(
            if (initialConfig.isDynamicNearest) {
                GtfsStop(
                    stopId = IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID,
                    stopName = resolvedNearestName,
                    stopDesc = "Suit automatiquement votre position et vos déplacements"
                )
            } else {
                GtfsStop(
                    stopId = initialConfig.stopId,
                    stopName = initialConfig.stopName,
                    stopDesc = initialConfig.stopDesc
                )
            }
        )
    }
    var refreshFreq by remember { mutableStateOf(prefs.getWidgetRefreshFrequency()) }
    var locationFreq by remember { mutableStateOf(prefs.getWidgetLocationFrequency()) }

    var previewDepartures by remember { mutableStateOf<List<DepartureComparison>>(emptyList()) }
    var isLoadingPreview by remember { mutableStateOf(false) }
    var hasLocPerm by remember { mutableStateOf(hasLocationPermission(context)) }
    var officialRouteLines by remember {
        mutableStateOf(IdelisRepository.globalOfficialRoutesMap.values.map { it.lineCode })
    }
    var stopsByLineSnapshot by remember {
        mutableStateOf<List<StopServing>>(IdelisRepository.globalDownloadedStopsCache)
    }
    var resolvedTargetStop by remember(appWidgetId) { mutableStateOf(selectedStop) }

    val locPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasLocPerm = hasLocationPermission(context)
    }

    // Chargement initial des arrêts et lignes commerciales du réseau (Room + cache disque + OpenData)
    LaunchedEffect(Unit) {
        val (loadedStops, loadedFavs) = withContext(Dispatchers.IO) {
            val database = IdelisDatabase.getInstance(context)
            val repo = IdelisRepository(database, context, prefs)
            try {
                repo.hydrateCachesFromDiskIfNeeded()
            } catch (_: Exception) {}
            val dbStops = database.stopDao().getAllStops().firstOrNull() ?: emptyList()
            val list = if (dbStops.isNotEmpty()) dbStops else GtfsDatabaseSeeder.INITIAL_STOPS
            list to prefs.getFavoriteStopIds()
        }
        stops = loadedStops
        favoriteStopIds = loadedFavs
        officialRouteLines = IdelisRepository.globalOfficialRoutesMap.values.map { it.lineCode }
        stopsByLineSnapshot = IdelisRepository.globalDownloadedStopsCache

        if (!widgetConfig.isDynamicNearest) {
            val matched = loadedStops.firstOrNull { it.stopId == widgetConfig.stopId }
                ?: loadedStops.firstOrNull { it.stopName.equals(widgetConfig.stopName, ignoreCase = true) }
            if (matched != null) {
                selectedStop = matched
                resolvedTargetStop = matched
            }
        }

        // Compléter depuis OpenData en arrière-plan si les caches réseau sont incomplets
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val database = IdelisDatabase.getInstance(context)
                val repo = IdelisRepository(database, context, prefs)
                withTimeoutOrNull(4_000L) {
                    repo.ensureStopsAndRoutesLoaded()
                }
                withContext(Dispatchers.Main) {
                    officialRouteLines = IdelisRepository.globalOfficialRoutesMap.values.map { it.lineCode }
                    stopsByLineSnapshot = IdelisRepository.globalDownloadedStopsCache
                }
            } catch (_: Exception) {}
        }
    }

    // Recherche en direct sur idelis.fr avec debounce
    LaunchedEffect(searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.length < 2) {
            webSuggestions = emptyList()
            isWebSearching = false
            return@LaunchedEffect
        }
        delay(320L)
        isWebSearching = true
        val results = withContext(Dispatchers.IO) {
            val database = IdelisDatabase.getInstance(context)
            val repo = IdelisRepository(database, context, prefs)
            repo.searchWebStops(trimmed).getOrDefault(emptyList())
        }
        webSuggestions = results
        isWebSearching = false
    }

    // Chargement de l'aperçu des passages quand l'arrêt change
    LaunchedEffect(selectedStop, widgetConfig.isDynamicNearest, hasLocPerm) {
        val stop = selectedStop ?: return@LaunchedEffect
        isLoadingPreview = true
        val (loadedDeps, loadedNearestName, effectiveStop) = withContext(Dispatchers.IO) {
            try {
                val database = IdelisDatabase.getInstance(context)
                val repo = IdelisRepository(database, context, prefs)
                var targetStop = stop
                var nearestStopName: String? = null

                if (widgetConfig.isDynamicNearest) {
                    val lastLoc = if (hasLocPerm) {
                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                        listOf(
                            android.location.LocationManager.GPS_PROVIDER,
                            android.location.LocationManager.NETWORK_PROVIDER,
                            android.location.LocationManager.PASSIVE_PROVIDER
                        ).firstNotNullOfOrNull { provider ->
                            try {
                                lm?.getLastKnownLocation(provider)
                            } catch (_: SecurityException) {
                                null
                            } catch (_: Exception) {
                                null
                            }
                        }
                    } else null

                    val resolved = if (lastLoc != null) {
                        repo.resolveAndSaveNearestStop(lastLoc.latitude, lastLoc.longitude, stops)
                    } else null

                    if (resolved != null) {
                        targetStop = resolved.stop
                        nearestStopName = resolved.stop.stopName.replace("📍", "").trim()
                        IdelisWidgetPreferences.saveLastNearestStop(
                            context = context,
                            appWidgetId = appWidgetId,
                            stopId = resolved.stop.stopId,
                            stopName = nearestStopName
                        )
                    } else {
                        val fallbackId = IdelisWidgetPreferences.getLastNearestStopId(context, appWidgetId)
                            ?: prefs.getLastSelectedStopId()?.takeIf { it != IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID }
                            ?: GtfsDatabaseSeeder.INITIAL_STOPS.first().stopId
                        val dbStop = database.stopDao().getStopById(fallbackId)
                            ?: GtfsDatabaseSeeder.INITIAL_STOPS.first()
                        targetStop = dbStop
                        nearestStopName = dbStop.stopName.replace("📍", "").trim()
                    }
                }

                val res = repo.getDeparturesForStop(
                    stopId = targetStop.stopId,
                    useSimulationMode = true,
                    forceOffline = false,
                    stopOverride = if (widgetConfig.isDynamicNearest && targetStop.stopId == IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID) null else targetStop
                )
                Triple(res.departures, nearestStopName, targetStop)
            } catch (_: Exception) {
                Triple(emptyList<DepartureComparison>(), null, stop)
            }
        }
        previewDepartures = loadedDeps
        resolvedTargetStop = effectiveStop
        if (!loadedNearestName.isNullOrBlank()) {
            resolvedNearestName = loadedNearestName
            if (widgetConfig.isDynamicNearest) {
                widgetConfig = widgetConfig.copy(stopName = loadedNearestName)
            }
        }
        isLoadingPreview = false
    }

    val favoriteStops = remember(stops, favoriteStopIds) {
        stops.filter { it.stopId in favoriteStopIds }
    }

    val filteredStops = remember(stops, searchQuery) {
        if (searchQuery.isBlank()) {
            stops
        } else {
            stops.filter {
                it.stopName.contains(searchQuery, ignoreCase = true) ||
                    (it.stopDesc?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    val stopSpecificLines = remember(
        previewDepartures,
        resolvedTargetStop,
        stopsByLineSnapshot
    ) {
        val rawStopLines = buildList {
            previewDepartures
                .map { IdelisWidgetPreferences.normalizeLineCode(it.lineRef) }
                .filter { it.isNotBlank() && it.length <= 5 }
                .forEach { add(it) }
            val target = resolvedTargetStop
            if (target != null) {
                GtfsScheduleIndex.getDeparturesForStop(
                    stopName = target.stopName,
                    poleName = target.parentStation
                ).map { it.routeShortName }
                    .filter { IdelisWidgetPreferences.isValidCommercialLineCode(it) }
                    .forEach { add(it) }

                val canonical = IdelisRepository.getCanonicalStopName(target.stopName).lowercase(Locale.ROOT)
                val mapMatches = IdelisRepository.globalServingsByStopName[canonical].orEmpty()
                if (mapMatches.isNotEmpty()) {
                    mapMatches.map { it.lineRef }
                        .filter { IdelisWidgetPreferences.isValidCommercialLineCode(it) }
                        .forEach { add(it) }
                } else if (stopsByLineSnapshot.isNotEmpty()) {
                    stopsByLineSnapshot.forEach { serving ->
                        val sCanon = IdelisRepository.getCanonicalStopName(serving.stopName).lowercase(Locale.ROOT)
                        if ((sCanon == canonical || sCanon.contains(canonical) || canonical.contains(sCanon)) &&
                            IdelisWidgetPreferences.isValidCommercialLineCode(serving.lineRef)
                        ) {
                            add(serving.lineRef)
                        }
                    }
                }

                target.stopDesc?.let { desc ->
                    val match = Regex("""Lignes?\s+([A-Za-z0-9,\s]+)""", RegexOption.IGNORE_CASE).find(desc)
                    match?.groupValues?.getOrNull(1)
                        ?.split(",")
                        ?.filter { IdelisWidgetPreferences.isValidCommercialLineCode(it) }
                        ?.forEach { add(it) }
                }

                GtfsDatabaseSeeder.INITIAL_SERVINGS
                    .filter { it.stopId == target.stopId && IdelisWidgetPreferences.isValidCommercialLineCode(it.lineRef) }
                    .forEach { add(it.lineRef) }
            }
        }
        IdelisWidgetPreferences.sortLinesNaturally(rawStopLines)
    }

    val allNetworkLines = remember(
        stopSpecificLines,
        officialRouteLines,
        stopsByLineSnapshot,
        widgetConfig.filteredLines
    ) {
        val rawAll = buildList {
            addAll(IdelisWidgetPreferences.STANDARD_IDELIS_LINES)
            addAll(GtfsDatabaseSeeder.INITIAL_ROUTES.map { it.routeShortName })
            officialRouteLines
                .filter { IdelisWidgetPreferences.isValidCommercialLineCode(it) }
                .forEach { add(it) }
            stopsByLineSnapshot.forEach { serving ->
                if (IdelisWidgetPreferences.isValidCommercialLineCode(serving.lineRef)) {
                    add(serving.lineRef)
                }
            }
            addAll(stopSpecificLines)
            addAll(widgetConfig.filteredLines)
        }
        IdelisWidgetPreferences.sortLinesNaturally(rawAll)
    }

    fun selectFixedStop(stop: GtfsStop) {
        selectedStop = stop
        widgetConfig = widgetConfig.copy(
            stopId = stop.stopId,
            stopName = stop.stopName,
            stopDesc = stop.stopDesc ?: "",
            isDynamicNearest = false,
            homeStopId = stop.stopId,
            homeStopName = stop.stopName,
            homeStopDesc = stop.stopDesc ?: "",
            homeIsDynamicNearest = false
        )
    }

    fun selectDynamicNearest() {
        val displayStopName = resolvedNearestName.ifBlank { GtfsDatabaseSeeder.INITIAL_STOPS.first().stopName }
        val dynStop = GtfsStop(
            stopId = IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID,
            stopName = displayStopName,
            stopDesc = "Suit vos déplacements et actualise plus vite en mouvement"
        )
        selectedStop = dynStop
        widgetConfig = widgetConfig.copy(
            stopId = IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID,
            stopName = displayStopName,
            stopDesc = "Suivi dynamique selon vos déplacements",
            isDynamicNearest = true,
            homeStopId = IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID,
            homeStopName = displayStopName,
            homeStopDesc = "Suivi dynamique selon vos déplacements",
            homeIsDynamicNearest = true
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val headerStopSummary = if (widgetConfig.isDynamicNearest) {
        "$resolvedNearestName (GPS)"
    } else {
        widgetConfig.stopName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Menu du widget",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$headerStopSummary • ${widgetConfig.themeStyle.shortLabel} • ${widgetConfig.colorMode.shortLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Annuler"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            selectedTab = if (selectedTab == 0) 1 else 0
                        }
                    ) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Default.Palette else Icons.Default.Place,
                            contentDescription = if (selectedTab == 0) "Personnaliser le style" else "Choisir l'arrêt",
                            tint = primaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val sortedSummaryLines = IdelisWidgetPreferences.sortLinesNaturally(widgetConfig.filteredLines)
                    val filterSummary = if (sortedSummaryLines.isNotEmpty()) {
                        " • Lignes ${sortedSummaryLines.joinToString(", ")}"
                    } else {
                        ""
                    }
                    Text(
                        text = "$headerStopSummary • ${widgetConfig.themeStyle.shortLabel} • ${widgetConfig.colorMode.shortLabel} • ${widgetConfig.densityMode.shortLabel}$filterSummary",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onConfigConfirmed(widgetConfig, refreshFreq, locationFreq)
                        },
                        enabled = selectedStop != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("confirm_widget_stop_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enregistrer et appliquer au widget",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
            ) {
                // Sélecteur d'onglet segmenté : Arrêt vs Apparence & Menu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WidgetMenuTabButton(
                        title = "Arrêt",
                        icon = Icons.Default.Place,
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("widget_tab_stop")
                    )
                    WidgetMenuTabButton(
                        title = "Apparence & Menu",
                        icon = Icons.Default.Tune,
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("widget_tab_style")
                    )
                }

                if (selectedTab == 0) {
                    // ONGLET 1 : SÉLECTION DE L'ARRÊT (tout défile ensemble sans écraser la liste)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("widget_stop_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Mode dynamique Arrêt le plus proche
                        item(key = "dynamic_nearest_card") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { selectDynamicNearest() }
                                    .testTag("dynamic_nearest_widget_option"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (widgetConfig.isDynamicNearest) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    }
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (widgetConfig.isDynamicNearest) primaryColor else Color(0xFF0288D1)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Arrêt le plus proche",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF22C55E).copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "Auto GPS",
                                                        color = Color(0xFF15803D),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "Détecte l'arrêt à proximité et suit vos déplacements",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (widgetConfig.isDynamicNearest) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Sélectionné",
                                                tint = primaryColor,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    if (widgetConfig.isDynamicNearest && !hasLocPerm) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedButton(
                                            onClick = {
                                                locPermissionLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                )
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
                                            Text("Autoriser la localisation pour le GPS", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Barre de recherche (locale + idelis.fr en direct)
                        item(key = "search_field") {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Rechercher un arrêt Idelis (ex: Bosquet, Gare...)") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = "Recherche")
                                },
                                trailingIcon = {
                                    if (isWebSearching) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Effacer")
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("widget_search_stop_input"),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryColor
                                )
                            )
                        }

                        // Favoris de l'utilisateur
                        if (favoriteStops.isNotEmpty() && searchQuery.isBlank()) {
                            item(key = "favorites_header") {
                                Text(
                                    text = "Mes arrêts favoris (${favoriteStops.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            }
                            items(favoriteStops, key = { "fav_${it.stopId}" }) { stop ->
                                val isSelected = !widgetConfig.isDynamicNearest && widgetConfig.stopId == stop.stopId
                                WidgetStopRowCard(
                                    stop = stop,
                                    isSelected = isSelected,
                                    isFavorite = true,
                                    onClick = { selectFixedStop(stop) },
                                    testTag = "fav_stop_item_${stop.stopId}"
                                )
                            }
                        }

                        // Suggestions en direct depuis idelis.fr
                        if (webSuggestions.isNotEmpty()) {
                            item(key = "web_results_header") {
                                Text(
                                    text = "Résultats en direct sur idelis.fr (${webSuggestions.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            }
                            items(webSuggestions, key = { "web_${it.pole}_${it.name}" }) { webStop ->
                                val webId = "WEB_" + webStop.pole.trim().replace(Regex("[^a-zA-Z0-9]"), "_")
                                val isSelected = !widgetConfig.isDynamicNearest && widgetConfig.stopId == webId
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                val saved = withContext(Dispatchers.IO) {
                                                    val db = IdelisDatabase.getInstance(context)
                                                    val repo = IdelisRepository(db, context, prefs)
                                                    repo.saveWebStop(webStop)
                                                }
                                                if (stops.none { it.stopId == saved.stopId }) {
                                                    stops = listOf(saved) + stops
                                                }
                                                selectFixedStop(saved)
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                        }
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) primaryColor else MaterialTheme.colorScheme.surface),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DirectionsBus,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else primaryColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = webStop.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Arrêt Idelis en direct",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Sélectionné",
                                                tint = primaryColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Tous les arrêts locaux
                        item(key = "all_stops_header") {
                            Text(
                                text = "Arrêts disponibles (${filteredStops.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        items(filteredStops, key = { it.stopId }) { stop ->
                            val isSelected = !widgetConfig.isDynamicNearest && widgetConfig.stopId == stop.stopId
                            WidgetStopRowCard(
                                stop = stop,
                                isSelected = isSelected,
                                isFavorite = stop.stopId in favoriteStopIds,
                                onClick = { selectFixedStop(stop) },
                                testTag = "stop_item_${stop.stopId}"
                            )
                        }
                    }
                } else {
                    // ONGLET 2 : APPARENCE, FILTRES DE LIGNES & MENU DU WIDGET
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("widget_style_menu"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Aperçu en temps réel
                        item(key = "preview_section") {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "APERÇU EN DIRECT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = primaryColor
                                    )
                                    Text(
                                        text = "Sans défilement • Adaptatif",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                WidgetLivePreviewCard(
                                    config = widgetConfig,
                                    departures = previewDepartures,
                                    favoriteCount = favoriteStopIds.size,
                                    isLoading = isLoadingPreview,
                                    resolvedStopName = if (widgetConfig.isDynamicNearest) resolvedNearestName else widgetConfig.stopName
                                )
                            }
                        }

                        // Thème et palette de couleurs du widget (Couleur de l'application ou Material You)
                        item(key = "theme_section") {
                            WidgetSettingsGroup(title = "Thème et couleur du widget", icon = Icons.Default.Palette) {
                                Text(
                                    text = "Apparence",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    WidgetThemeStyle.entries.forEach { style ->
                                        val selected = widgetConfig.themeStyle == style
                                        FilterChip(
                                            selected = selected,
                                            onClick = { widgetConfig = widgetConfig.copy(themeStyle = style) },
                                            label = { Text(style.label, fontSize = 12.sp) },
                                            leadingIcon = if (selected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null,
                                            modifier = Modifier.testTag("widget_theme_${style.name}")
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )

                                Text(
                                    text = "Couleur du thème",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    WidgetColorMode.entries.forEach { colorMode ->
                                        val selected = widgetConfig.colorMode == colorMode
                                        FilterChip(
                                            selected = selected,
                                            onClick = { widgetConfig = widgetConfig.copy(colorMode = colorMode) },
                                            label = { Text(colorMode.label, fontSize = 12.sp) },
                                            leadingIcon = if (selected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null,
                                            modifier = Modifier.testTag("widget_color_${colorMode.name}")
                                        )
                                    }
                                }
                                if (widgetConfig.colorMode == WidgetColorMode.MATERIAL_YOU &&
                                    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S
                                ) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Material You nécessite Android 12+ (couleur de l'application utilisée)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Format d'affichage de l'attente
                        item(key = "time_mode_section") {
                            WidgetSettingsGroup(title = "Format des horaires", icon = Icons.Default.Schedule) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    WidgetTimeDisplayMode.entries.forEach { mode ->
                                        val selected = widgetConfig.timeDisplayMode == mode
                                        FilterChip(
                                            selected = selected,
                                            onClick = { widgetConfig = widgetConfig.copy(timeDisplayMode = mode) },
                                            label = { Text(mode.label, fontSize = 12.sp) },
                                            leadingIcon = if (selected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }

                        // Densité d'affichage
                        item(key = "density_section") {
                            WidgetSettingsGroup(title = "Densité d'affichage", icon = Icons.Default.DirectionsBus) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    WidgetDensityMode.entries.forEach { density ->
                                        val selected = widgetConfig.densityMode == density
                                        FilterChip(
                                            selected = selected,
                                            onClick = { widgetConfig = widgetConfig.copy(densityMode = density) },
                                            label = { Text(density.label, fontSize = 12.sp) },
                                            leadingIcon = if (selected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }

                        // Boutons d'actions rapides dans l'en-tête du widget (chacun indépendant)
                        item(key = "actions_section") {
                            WidgetSettingsGroup(title = "Boutons sur le widget", icon = Icons.Default.Tune) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            widgetConfig = widgetConfig.copy(showRefreshButton = !widgetConfig.showRefreshButton)
                                        }
                                        .padding(vertical = 4.dp)
                                        .testTag("widget_toggle_refresh_button"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Bouton Actualiser",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Affiche le bouton d'actualisation manuelle des horaires",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = widgetConfig.showRefreshButton,
                                        onCheckedChange = {
                                            widgetConfig = widgetConfig.copy(showRefreshButton = it)
                                        },
                                        colors = SwitchDefaults.colors(checkedTrackColor = primaryColor)
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            widgetConfig = widgetConfig.copy(showMenuButton = !widgetConfig.showMenuButton)
                                        }
                                        .padding(vertical = 4.dp)
                                        .testTag("widget_toggle_menu_button"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Bouton Menu",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Affiche le bouton d'accès rapide aux paramètres du widget",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = widgetConfig.showMenuButton,
                                        onCheckedChange = {
                                            widgetConfig = widgetConfig.copy(showMenuButton = it)
                                        },
                                        colors = SwitchDefaults.colors(checkedTrackColor = primaryColor)
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            widgetConfig = widgetConfig.copy(showFavoriteSwitch = !widgetConfig.showFavoriteSwitch)
                                        }
                                        .padding(vertical = 4.dp)
                                        .testTag("widget_toggle_favorite_button"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Bouton bascule d'arrêt favori",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (favoriteStopIds.isEmpty()) {
                                                "Ajoutez un arrêt en favori ⭐ dans l'appli pour l'utiliser"
                                            } else {
                                                "Fait défiler l'arrêt sélectionné et vos ${favoriteStopIds.size} arrêt(s) favori(s)"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = widgetConfig.showFavoriteSwitch,
                                        onCheckedChange = {
                                            widgetConfig = widgetConfig.copy(showFavoriteSwitch = it)
                                        },
                                        colors = SwitchDefaults.colors(checkedTrackColor = primaryColor)
                                    )
                                }
                            }
                        }

                        // Filtre des lignes desservies (lignes de l'arrêt + toutes les lignes du réseau Idelis)
                        item(key = "lines_filter_section") {
                            val sortedSelectedLines = IdelisWidgetPreferences.sortLinesNaturally(widgetConfig.filteredLines)
                            val stopLineSet = stopSpecificLines.toSet()
                            val otherNetworkLines = allNetworkLines.filterNot { it in stopLineSet }

                            fun toggleFilteredLine(lineCode: String) {
                                val normalized = IdelisWidgetPreferences.normalizeLineCode(lineCode)
                                val nextSet = widgetConfig.filteredLines
                                    .map { IdelisWidgetPreferences.normalizeLineCode(it) }
                                    .filter { it.isNotBlank() }
                                    .toMutableSet()
                                if (normalized in nextSet) nextSet.remove(normalized) else nextSet.add(normalized)
                                widgetConfig = widgetConfig.copy(filteredLines = nextSet)
                            }

                            WidgetSettingsGroup(title = "Filtrer les lignes affichées", icon = Icons.Default.DirectionsBus) {
                                Text(
                                    text = if (sortedSelectedLines.isEmpty()) {
                                        "Toutes les lignes sont affichées (${allNetworkLines.size} lignes du réseau)."
                                    } else {
                                        "Uniquement : ${sortedSelectedLines.joinToString(", ")}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FilterChip(
                                    selected = sortedSelectedLines.isEmpty(),
                                    onClick = {
                                        widgetConfig = widgetConfig.copy(filteredLines = emptySet())
                                    },
                                    label = { Text("Toutes", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.testTag("widget_filter_all")
                                )

                                if (stopSpecificLines.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (widgetConfig.isDynamicNearest) {
                                            "À l'arrêt détecté (${stopSpecificLines.size})"
                                        } else {
                                            "À cet arrêt (${stopSpecificLines.size})"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        stopSpecificLines.forEach { line ->
                                            val isSelected = line in sortedSelectedLines
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { toggleFilteredLine(line) },
                                                label = { Text(formatWidgetLineChipLabel(line), fontSize = 12.sp) },
                                                leadingIcon = if (isSelected) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                                } else null,
                                                modifier = Modifier.testTag("widget_line_filter_$line")
                                            )
                                        }
                                    }
                                }

                                val remainingLines = if (stopSpecificLines.isNotEmpty()) otherNetworkLines else allNetworkLines
                                if (remainingLines.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = if (stopSpecificLines.isNotEmpty()) {
                                            "Autres lignes du réseau (${remainingLines.size})"
                                        } else {
                                            "Toutes les lignes du réseau (${remainingLines.size})"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        remainingLines.forEach { line ->
                                            val isSelected = line in sortedSelectedLines
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { toggleFilteredLine(line) },
                                                label = { Text(formatWidgetLineChipLabel(line), fontSize = 12.sp) },
                                                leadingIcon = if (isSelected) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                                } else null,
                                                modifier = Modifier.testTag("widget_line_filter_$line")
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Fréquence d'actualisation (globale)
                        item(key = "refresh_freq_section") {
                            WidgetSettingsGroup(title = "Actualisation automatique", icon = Icons.Default.Refresh) {
                                Text(
                                    text = if (widgetConfig.isDynamicNearest) {
                                        "Fréquence GPS (« Arrêt proche ») :"
                                    } else {
                                        "Fréquence des widgets :"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (widgetConfig.isDynamicNearest) {
                                        WidgetLocationFrequency.entries.forEach { freq ->
                                            FilterChip(
                                                selected = locationFreq == freq,
                                                onClick = { locationFreq = freq },
                                                label = { Text(freq.label, fontSize = 11.sp) }
                                            )
                                        }
                                    } else {
                                        WidgetRefreshFrequency.entries.forEach { freq ->
                                            FilterChip(
                                                selected = refreshFreq == freq,
                                                onClick = { refreshFreq = freq },
                                                label = { Text(freq.label, fontSize = 11.sp) }
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
}

@Composable
private fun WidgetMenuTabButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun WidgetStopRowCard(
    stop: GtfsStop,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSelected -> primaryColor
                            isFavorite -> Color(0xFFF59E0B).copy(alpha = 0.18f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.Place,
                    contentDescription = null,
                    tint = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        isFavorite -> Color(0xFFD97706)
                        else -> primaryColor
                    },
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stop.stopName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!stop.stopDesc.isNullOrBlank()) {
                    Text(
                        text = stop.stopDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Sélectionné",
                    tint = primaryColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun WidgetSettingsGroup(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

private fun formatWidgetLineChipLabel(lineCode: String): String {
    return when (lineCode) {
        "F" -> "Fébus (F)"
        "COXI" -> "Coxitis"
        else -> "Ligne $lineCode"
    }
}
