package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.GtfsStop
import com.example.data.model.IdelisWebStop
import com.example.ui.theme.SketchBlack
import com.example.ui.theme.SketchRed

/**
 * Calcule la distance en mètres entre deux coordonnées géodésiques.
 */
private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return 6371000.0 * c
}

private fun formatDistance(meters: Double): String {
    return if (meters < 1000) {
        "${meters.toInt()} m"
    } else {
        String.format(java.util.Locale.FRANCE, "%.1f km", meters / 1000.0)
    }
}

private fun cleanStopIdForWeb(pole: String): String {
    return "WEB_" + pole.trim().replace(Regex("[^a-zA-Z0-9]"), "_")
}

@SuppressLint("MissingPermission")
private fun getLastKnownLocation(context: Context): Location? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
    for (provider in providers) {
        try {
            if (locationManager.isProviderEnabled(provider)) {
                val loc = locationManager.getLastKnownLocation(provider)
                if (loc != null) return loc
            }
        } catch (_: SecurityException) {}
    }
    return null
}

/**
 * Modale de sélection d'arrêt :
 * - Aucun arrêt théorique affiché.
 * - Recherche en direct via les requêtes officielles Idelis (?type=777).
 * - Possibilité de mettre en favori (⭐) n'importe quel arrêt reçu par requête directement depuis les résultats.
 * - Affichage prioritaire de "Mes arrêts favoris" avec désélection / suppression aisée.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StopPickerModal(
    stops: List<GtfsStop>,
    selectedStop: GtfsStop?,
    favoriteStopIds: Set<String> = emptySet(),
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onToggleFavoriteWebStop: (IdelisWebStop) -> Unit = {},
    webSuggestions: List<IdelisWebStop> = emptyList(),
    isWebSearching: Boolean = false,
    onWebStopSelected: (IdelisWebStop) -> Unit = {},
    onStopSelected: (GtfsStop) -> Unit,
    onDismissRequest: () -> Unit,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bgColor = if (isDarkTheme) Color(0xFF141B26) else Color.White
    val titleColor = if (isDarkTheme) Color.White else SketchBlack
    val contentColor = if (isDarkTheme) Color(0xFFF0F4F8) else SketchBlack
    val surfaceColor = if (isDarkTheme) Color(0xFF1C2636) else Color(0xFFF5F5F5)

    var userLocation by remember { mutableStateOf<Location?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var locationFeedback by remember { mutableStateOf<String?>(null) }

    val favoriteStops = remember(stops, favoriteStopIds) {
        stops.filter { favoriteStopIds.contains(it.stopId) }
    }

    val nonFavoriteRecentStops = remember(stops, favoriteStopIds) {
        stops.filter { !favoriteStopIds.contains(it.stopId) }
    }

    // Système de localisation de l'arrêt de bus amélioré par celui des requêtes officielles Idelis
    fun findAndSelectNearest() {
        val loc = userLocation ?: getLastKnownLocation(context) ?: Location("default_pau").apply {
            latitude = 43.2974
            longitude = -0.3688
        }
        userLocation = loc

        // 1. Recherche parmi les arrêts géolocalisés issus des requêtes officielles (Explore / OpenData)
        val requestStops = com.example.data.repository.IdelisRepository.globalDownloadedStopsCache.filter { it.lat != 0.0 && it.lon != 0.0 }
        val nearestFromRequest = if (requestStops.isNotEmpty()) {
            requestStops.minByOrNull {
                calculateDistanceMeters(loc.latitude, loc.longitude, it.lat, it.lon)
            }
        } else null

        // 2. Recherche parmi les arrêts déjà mémorisés en base
        val dbGeoStops = stops.filter { it.stopLat != 0.0 && it.stopLon != 0.0 }
        val nearestFromDb = if (dbGeoStops.isNotEmpty()) {
            dbGeoStops.minByOrNull {
                calculateDistanceMeters(loc.latitude, loc.longitude, it.stopLat, it.stopLon)
            }
        } else null

        // Choisir le plus proche absolu
        var chosenStop: GtfsStop? = null
        var chosenDist = Double.MAX_VALUE

        if (nearestFromRequest != null) {
            val d = calculateDistanceMeters(loc.latitude, loc.longitude, nearestFromRequest.lat, nearestFromRequest.lon)
            chosenDist = d
            chosenStop = GtfsStop(
                stopId = "WEB_" + nearestFromRequest.stopName.trim().replace(Regex("[^a-zA-Z0-9]"), "_"),
                stopName = nearestFromRequest.stopName,
                stopDesc = "Arrêt géolocalisé via requêtes officielles • Ligne ${nearestFromRequest.lineRef}",
                parentStation = nearestFromRequest.pole ?: nearestFromRequest.stopName,
                stopLat = nearestFromRequest.lat,
                stopLon = nearestFromRequest.lon
            )
        }

        if (nearestFromDb != null) {
            val d = calculateDistanceMeters(loc.latitude, loc.longitude, nearestFromDb.stopLat, nearestFromDb.stopLon)
            if (d < chosenDist) {
                chosenDist = d
                chosenStop = nearestFromDb
            }
        }

        if (chosenStop == null) {
            chosenStop = stops.firstOrNull() ?: com.example.data.repository.IdelisRepository.DEFAULT_INITIAL_STOP
        }

        locationFeedback = "Arrêt le plus proche : ${chosenStop.stopName}" + if (chosenDist < 100000) " (${formatDistance(chosenDist)})" else ""
        onStopSelected(chosenStop)
        onDismissRequest()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        isLocating = false
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            val loc = getLastKnownLocation(context)
            if (loc != null) userLocation = loc
            findAndSelectNearest()
        } else {
            findAndSelectNearest()
        }
    }

    fun onNearestStopClick() {
        isLocating = true
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            isLocating = false
            val loc = getLastKnownLocation(context)
            if (loc != null) userLocation = loc
            findAndSelectNearest()
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    val isSearching = searchQuery.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = bgColor,
        titleContentColor = titleColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.testTag("stop_picker_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsBus,
                        contentDescription = null,
                        tint = titleColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Choisir l'arrêt",
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
            Column(modifier = Modifier.fillMaxWidth()) {
                // OPTION : Arrêt le plus proche
                Button(
                    onClick = { onNearestStopClick() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("nearest_stop_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SketchRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Localisation...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.NearMe,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sélectionner l'arrêt le plus proche",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }

                if (locationFeedback != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = locationFeedback ?: "",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SketchRed,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Champ de recherche d'arrêt envoyant la requête en direct à idelis.fr
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        onSearchQueryChange(it)
                    },
                    placeholder = {
                        Text(
                            "Rechercher un arrêt en direct (ex: Bosquet, Gare, Verdun)...",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = SketchRed)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isWebSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = SketchRed,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = {
                                    onSearchQueryChange("")
                                }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Effacer", tint = Color.Gray)
                                }
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SketchRed,
                        unfocusedBorderColor = if (isDarkTheme) Color(0xFF2A3A52) else Color(0xFFDCDCDC),
                        focusedTextColor = contentColor,
                        unfocusedTextColor = contentColor,
                        focusedContainerColor = surfaceColor,
                        unfocusedContainerColor = surfaceColor
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("stop_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // CAS 1 : RECHERCHE ACTIVE -> Affiche EXCLUSIVEMENT les arrêts reçus par requête idelis.fr
                    if (isSearching) {
                        item(key = "web_suggestions_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2E7D32))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ARRÊTS REÇUS PAR REQUÊTE (${webSuggestions.size})",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            color = SketchRed,
                                            letterSpacing = 0.5.sp
                                        )
                                    )
                                }

                                Text(
                                    text = "⭐ Toucher l'étoile pour ajouter en favori",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        if (webSuggestions.isEmpty() && !isWebSearching && searchQuery.trim().length >= 2) {
                            item(key = "no_web_results") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Aucun arrêt trouvé pour \"$searchQuery\"",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color.Gray,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Vérifiez l'orthographe du pôle ou de l'arrêt",
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                                        )
                                    }
                                }
                            }
                        }

                        items(webSuggestions, key = { "web_${it.pole}_${it.name}" }) { webStop ->
                            val webStopId = cleanStopIdForWeb(webStop.pole)
                            val isFav = favoriteStopIds.contains(webStopId)
                            val isSelected = selectedStop?.stopId == webStopId ||
                                selectedStop?.parentStation.equals(webStop.pole, ignoreCase = true)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isSelected -> SketchRed.copy(alpha = 0.16f)
                                            isFav -> Color(0xFFFFB300).copy(alpha = 0.12f)
                                            else -> SketchRed.copy(alpha = 0.10f)
                                        }
                                    )
                                    .clickable {
                                        onWebStopSelected(webStop)
                                        onDismissRequest()
                                    }
                                    .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                                    .testTag("web_stop_${webStop.pole}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (isFav) Color(0xFFFFB300) else SketchRed),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsBus,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = webStop.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                color = if (isSelected) SketchRed else contentColor
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Pôle requête : ${webStop.pole}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = if (isFav) Color(0xFFD97706) else SketchRed,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Sélectionné",
                                            tint = SketchRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    } else {
                                        Text(
                                            text = "En direct",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF2E7D32),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF2E7D32).copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }

                                    // Bouton étoile permettant de mettre en favori l'arrêt reçu par requête
                                    IconButton(
                                        onClick = { onToggleFavoriteWebStop(webStop) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .testTag("fav_web_button_${webStop.pole}")
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = if (isFav) "Retirer des favoris" else "Mettre en favori",
                                            tint = if (isFav) Color(0xFFFFB300) else (if (isDarkTheme) Color.LightGray else Color.Gray),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // CAS 2 : PAS DE RECHERCHE -> Affiche les arrêts mis en favoris par l'utilisateur
                        if (favoriteStops.isNotEmpty()) {
                            item(key = "favorites_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "MES ARRÊTS FAVORIS (${favoriteStops.size})",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFD97706),
                                            letterSpacing = 0.5.sp
                                        )
                                    )
                                }
                            }

                            items(favoriteStops, key = { "fav_${it.stopId}" }) { stop ->
                                val isSelected = stop.stopId == selectedStop?.stopId
                                val distanceMeters = remember(stop, userLocation) {
                                    if (userLocation != null && stop.stopLat != 0.0 && stop.stopLon != 0.0) {
                                        calculateDistanceMeters(userLocation!!.latitude, userLocation!!.longitude, stop.stopLat, stop.stopLon)
                                    } else null
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) SketchRed.copy(alpha = 0.14f)
                                            else Color(0xFFFFB300).copy(alpha = 0.10f)
                                        )
                                        .clickable {
                                            onStopSelected(stop)
                                            onDismissRequest()
                                        }
                                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 6.dp)
                                        .testTag("fav_stop_item_${stop.stopId}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) SketchRed else Color(0xFFFFB300)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stop.stopName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Black,
                                                    color = if (isSelected) SketchRed else contentColor
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = stop.stopDesc ?: (stop.parentStation?.let { "Pôle : $it" } ?: "Arrêt réseau"),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color.Gray,
                                                    fontSize = 11.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (distanceMeters != null) {
                                            Text(
                                                text = formatDistance(distanceMeters),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (isSelected) SketchRed else Color.Gray,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Sélectionné",
                                                tint = SketchRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                        }

                                        IconButton(
                                            onClick = { onToggleFavorite(stop.stopId) },
                                            modifier = Modifier
                                                .size(38.dp)
                                                .testTag("unfav_button_${stop.stopId}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Retirer des favoris",
                                                tint = Color(0xFFFFB300),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // État vide quand aucun favori n'a encore été mis
                            item(key = "empty_favorites") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(surfaceColor)
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.StarBorder,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Aucun arrêt favori pour le moment",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = contentColor
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Recherchez un arrêt ci-dessus et appuyez sur l'étoile ⭐ pour l'ajouter à vos favoris.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color.Gray,
                                                fontSize = 11.5.sp
                                            ),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // Suggestions rapides de recherche en 1 clic (masquées dès qu'il y a des arrêts en favoris)
                        if (favoriteStops.isEmpty()) {
                            item(key = "quick_search_suggestions") {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                    Text(
                                        text = "SUGGESTIONS RAPIDES DE RECHERCHE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Gray,
                                            letterSpacing = 0.5.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val quickPoles = listOf(
                                            "Bosquet",
                                            "Gare de Pau",
                                            "Verdun",
                                            "Université",
                                            "Hôpital"
                                        )
                                        for (pole in quickPoles) {
                                            SuggestionChip(
                                                onClick = {
                                                    onSearchQueryChange(pole)
                                                },
                                                label = { Text(pole, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    containerColor = surfaceColor,
                                                    labelColor = contentColor
                                                ),
                                                border = SuggestionChipDefaults.suggestionChipBorder(
                                                    enabled = true,
                                                    borderColor = SketchRed.copy(alpha = 0.3f)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Autres arrêts récemment consultés (non favoris)
                        if (nonFavoriteRecentStops.isNotEmpty()) {
                            item(key = "recent_stops_header") {
                                Text(
                                    text = "ARRÊTS RÉCENTS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        letterSpacing = 0.5.sp
                                    ),
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                )
                            }

                            items(nonFavoriteRecentStops, key = { "recent_${it.stopId}" }) { stop ->
                                val isSelected = stop.stopId == selectedStop?.stopId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) SketchRed.copy(alpha = 0.12f) else surfaceColor)
                                        .clickable {
                                            onStopSelected(stop)
                                            onDismissRequest()
                                        }
                                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 6.dp)
                                        .testTag("recent_stop_${stop.stopId}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) SketchRed else Color(0xFFE0E0E0)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DirectionsBus,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else SketchBlack,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stop.stopName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                                    color = if (isSelected) SketchRed else contentColor
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = stop.parentStation?.let { "Pôle : $it" } ?: "Arrêt consulté",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color.Gray,
                                                    fontSize = 11.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { onToggleFavorite(stop.stopId) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("fav_recent_button_${stop.stopId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.StarBorder,
                                            contentDescription = "Ajouter aux favoris",
                                            tint = if (isDarkTheme) Color.LightGray else Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}
