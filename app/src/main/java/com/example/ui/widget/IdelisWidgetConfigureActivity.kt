package com.example.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.GtfsDatabaseSeeder
import com.example.data.local.IdelisDatabase
import com.example.data.model.DepartureComparison
import com.example.data.model.GtfsStop
import com.example.data.repository.IdelisRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IdelisWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Récupération de l'identifiant du widget transmis par le lanceur
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // Si aucun appWidgetId valide n'a été passé, on termine
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Initialisation immédiate avec l'arrêt par défaut si nécessaire pour garantir que le widget est prêt
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

        // Renvoyer par défaut RESULT_OK avec l'ID du widget pour éviter l'échec "impossible d'ajouter le widget"
        val defaultResult = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, defaultResult)

        setContent {
            MyApplicationTheme {
                WidgetConfigureScreen(
                    appWidgetId = appWidgetId,
                    onStopConfigured = { selectedStop, isDynamicNearest ->
                        confirmWidgetConfiguration(selectedStop, isDynamicNearest)
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

    private fun confirmWidgetConfiguration(stop: GtfsStop, isDynamicNearest: Boolean = false) {
        val context = this
        // 1. Sauvegarde des préférences pour CE widget spécifiquement
        IdelisWidgetPreferences.saveWidgetStop(
            context = context,
            appWidgetId = appWidgetId,
            stopId = if (isDynamicNearest) IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID else stop.stopId,
            stopName = if (isDynamicNearest) "📍 Arrêt le plus proche" else stop.stopName,
            stopDesc = if (isDynamicNearest) "Suivi dynamique selon vos déplacements" else (stop.stopDesc ?: ""),
            isDynamicNearest = isDynamicNearest
        )

        // 2. Déclenchement de la première mise à jour graphique du widget
        val appWidgetManager = AppWidgetManager.getInstance(context)
        IdelisAppWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId)

        // 3. Renvoi du résultat positif au système Android
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WidgetConfigureScreen(
    appWidgetId: Int,
    onStopConfigured: (GtfsStop, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler(onBack = onCancel)

    val context = LocalContext.current
    var stops by remember { mutableStateOf<List<GtfsStop>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStop by remember { mutableStateOf<GtfsStop?>(null) }
    var isDynamicNearestSelected by remember { mutableStateOf(false) }
    var previewDepartures by remember { mutableStateOf<List<DepartureComparison>>(emptyList()) }
    var isLoadingPreview by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Chargement initial des arrêts
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val database = IdelisDatabase.getInstance(context)
            val dbStops = database.stopDao().getAllStops().firstOrNull() ?: emptyList()
            val list = if (dbStops.isNotEmpty()) dbStops else listOf(IdelisRepository.DEFAULT_INITIAL_STOP)
            stops = list

            val isDynamic = IdelisWidgetPreferences.isWidgetDynamicNearest(context, appWidgetId)
            isDynamicNearestSelected = isDynamic

            if (isDynamic) {
                selectedStop = GtfsStop(
                    stopId = IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID,
                    stopName = "📍 Arrêt le plus proche",
                    stopDesc = "Suit automatiquement votre position et vos déplacements"
                )
            } else {
                val currentSavedId = IdelisWidgetPreferences.getWidgetStopId(context, appWidgetId)
                val defaultChoice = list.firstOrNull { it.stopId == currentSavedId }
                    ?: list.firstOrNull { it.stopName.contains("Bosquet", ignoreCase = true) }
                    ?: list.firstOrNull()
                selectedStop = defaultChoice
            }
        }
    }

    // Chargement de l'aperçu quand l'arrêt sélectionné change
    LaunchedEffect(selectedStop, isDynamicNearestSelected) {
        val stop = selectedStop ?: return@LaunchedEffect
        isLoadingPreview = true
        withContext(Dispatchers.IO) {
            try {
                val database = IdelisDatabase.getInstance(context)
                val repo = IdelisRepository(database)
                val targetId = if (isDynamicNearestSelected) "ID_BOSQUET" else stop.stopId
                val res = repo.getDeparturesForStop(targetId, useSimulationMode = true)
                previewDepartures = res.departures.take(4)
            } catch (_: Exception) {
                previewDepartures = emptyList()
            } finally {
                isLoadingPreview = false
            }
        }
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

    val popularHubs = remember {
        listOf("Pôle Bosquet", "Gare de Pau", "Université", "Place de Verdun", "Hôpital François Mitterrand", "Catherine de Bourbon")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Arrêt du Widget #$appWidgetId",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Arrêt fixe ou mode dynamique au plus proche",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        .padding(16.dp)
                ) {
                    val stop = selectedStop
                    if (stop != null) {
                        Text(
                            text = if (isDynamicNearestSelected) {
                                "Mode sélectionné : 📍 Arrêt le plus proche automatique (Mouvement actif)"
                            } else {
                                "Arrêt sélectionné : ${stop.stopName}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            selectedStop?.let { onStopConfigured(it, isDynamicNearestSelected) }
                        },
                        enabled = selectedStop != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("confirm_widget_stop_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Valider et placer le widget",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // OPTION SPÉCIALE DEMANDÉE : Arrêt de bus le plus proche (dynamique + actualisation en mouvement)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable {
                        isDynamicNearestSelected = true
                        selectedStop = GtfsStop(
                            stopId = IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID,
                            stopName = "📍 Arrêt le plus proche",
                            stopDesc = "Suit vos déplacements et actualise plus vite en mouvement"
                        )
                    }
                    .testTag("dynamic_nearest_widget_option"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDynamicNearestSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDynamicNearestSelected) MaterialTheme.colorScheme.primary else Color(0xFF0288D1)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
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
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF22C55E).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Auto GPS",
                                    color = Color(0xFF15803D),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Actualise la position et rafraîchit les prochains bus plus rapidement en mouvement",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isDynamicNearestSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Sélectionné",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
            // Barre de recherche
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher un arrêt (ex: Bosquet, Gare, Univ...)") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Recherche")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("widget_search_stop_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            // Pôles principaux en accès rapide
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = "Pôles majeurs recommandés :",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    popularHubs.forEach { hubName ->
                        val matchingStop = stops.firstOrNull { it.stopName.contains(hubName, ignoreCase = true) }
                        val isSelected = !isDynamicNearestSelected && selectedStop?.stopName?.contains(hubName, ignoreCase = true) == true
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (matchingStop != null) {
                                    isDynamicNearestSelected = false
                                    selectedStop = matchingStop
                                }
                            },
                            label = { Text(hubName, fontSize = 12.sp) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Carte d'aperçu du widget
            selectedStop?.let { stop ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF18202A)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBus,
                                contentDescription = null,
                                tint = Color(0xFFF18700),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Aperçu pour : ${stop.stopName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Sans scroll",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF22C55E),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFF334155))
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isLoadingPreview) {
                            Text(
                                text = "Calcul des prochains départs...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        } else if (previewDepartures.isEmpty()) {
                            Text(
                                text = "Lignes Idelis desservies : ${stop.stopDesc ?: "Lignes régulières"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        } else {
                            previewDepartures.forEach { dep ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF0288D1))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = dep.lineRef,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = dep.destinationName,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0F5132))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = IdelisWidgetHelper.formatWaitingTime(dep),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Liste de tous les arrêts disponibles
            Text(
                text = "${filteredStops.size} arrêts trouvés :",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredStops, key = { it.stopId }) { stop ->
                    val isSelected = !isDynamicNearestSelected && selectedStop?.stopId == stop.stopId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isDynamicNearestSelected = false
                                selectedStop = stop
                            }
                            .testTag("stop_item_${stop.stopId}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
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
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stop.stopName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (!stop.stopDesc.isNullOrBlank()) {
                                    Text(
                                        text = stop.stopDesc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sélectionné",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
