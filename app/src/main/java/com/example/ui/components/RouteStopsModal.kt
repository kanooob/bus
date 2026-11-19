package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.RouteLineDetail
import com.example.data.model.RouteStopItem
import com.example.ui.theme.IdelisGold
import com.example.ui.theme.SketchWhite
import com.example.ui.theme.TransitDarkBg
import java.text.Normalizer

/**
 * Modale « Plus d'info sur le bus » affichant :
 * - En haut : le Terminus fixe du bus sélectionné (sans changement de direction),
 *   les informations temps réel (attente, passage, retard/avance, quai, arrêts restants)
 * - En dessous : l'ensemble des arrêts de la ligne dans l'ordre exact (Terminus en haut, Début en bas).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteStopsModal(
    routeDetail: RouteLineDetail,
    currentStopName: String,
    onStopSelected: ((String) -> Unit)? = null,
    onDismissRequest: () -> Unit,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismissRequest)

    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val bgColor = if (isDarkTheme) TransitDarkBg else SketchWhite
    val surfaceCardColor = if (isDarkTheme) Color(0xFF171E29) else Color(0xFFF8FAFC)
    val innerPillColor = if (isDarkTheme) Color(0xFF222B3A) else Color(0xFFFFFFFF)
    val textPrimaryColor = if (isDarkTheme) Color.White else Color(0xFF111827)
    val textSecondaryColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
    val dividerColor = if (isDarkTheme) Color(0xFF263040) else Color(0xFFE2E8F0)

    val routeColor = remember(routeDetail.colorHex) {
        try {
            Color(android.graphics.Color.parseColor("#" + routeDetail.colorHex.removePrefix("#")))
        } catch (_: Exception) {
            IdelisGold
        }
    }

    val routeTextColor = remember(routeDetail.textColorHex) {
        try {
            Color(android.graphics.Color.parseColor("#" + routeDetail.textColorHex.removePrefix("#")))
        } catch (_: Exception) {
            Color.White
        }
    }

    val filteredStops = remember(routeDetail.stops, searchQuery) {
        if (searchQuery.isBlank()) {
            routeDetail.stops
        } else {
            val q = normalizeQuery(searchQuery)
            routeDetail.stops.filter {
                normalizeQuery(it.stopName).contains(q) ||
                    normalizeQuery(it.canonicalName).contains(q)
            }
        }
    }

    LaunchedEffect(routeDetail.selectedDirection, routeDetail.totalStopsCount) {
        val currentIndex = filteredStops.indexOfFirst { it.isCurrentStop }
        if (currentIndex > 1) {
            listState.animateScrollToItem(maxOf(0, currentIndex - 1))
        }
    }

    val waitLabel = remember(routeDetail.waitMinutes) {
        val mins = routeDetail.waitMinutes
        when {
            mins == null -> null
            mins <= 0L -> "Imminent"
            mins == 1L -> "Dans 1 min"
            else -> "Dans $mins min"
        }
    }
    val waitIsImminent = (routeDetail.waitMinutes ?: 99L) <= 1L

    val displayArrivalTime = routeDetail.expectedTimeFormatted?.takeIf { it.contains(":") }
        ?: routeDetail.aimedTimeFormatted?.takeIf { it.contains(":") }

    val statusText = when {
        !routeDetail.isRealTime -> "Théorique"
        routeDetail.delayMinutes > 0 -> "+${routeDetail.delayMinutes} min"
        routeDetail.delayMinutes < 0 -> "${routeDetail.delayMinutes} min"
        else -> "À l'heure"
    }
    val statusColor = when {
        !routeDetail.isRealTime -> textSecondaryColor
        routeDetail.delayMinutes > 0 -> Color(0xFFD32F2F)
        routeDetail.delayMinutes < 0 -> Color(0xFF0288D1)
        routeDetail.isDelayIndeterminate -> textSecondaryColor
        else -> Color(0xFF2E7D32)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("route_stops_modal"),
            color = bgColor
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Barre supérieure : Badge de la ligne, libellé, et bouton Fermer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IdelisLineIconBadge(
                        lineRef = routeDetail.lineCode,
                        iconUrl = routeDetail.iconUrl,
                        routeColor = routeColor,
                        routeTextColor = routeTextColor,
                        size = 44.dp,
                        contentDescription = "Icône ${routeDetail.lineLabel}"
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = routeDetail.lineLabel,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = textPrimaryColor
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = routeDetail.routeLongName
                            .takeIf { it.isNotBlank() && !it.equals(routeDetail.lineLabel, ignoreCase = true) }
                            ?: "Détails du trajet et arrêts desservis"
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = textSecondaryColor,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("close_route_stops_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = textPrimaryColor
                        )
                    }
                }

                // 2. Carte Hero fixe en haut : TERMINUS + Infos temps réel du bus sélectionné
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("route_terminus_header_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceCardColor),
                    border = BorderStroke(1.dp, routeColor.copy(alpha = if (isDarkTheme) 0.45f else 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        // Ligne de tête : badge TERMINUS + badge Quai éventuel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = routeColor.copy(alpha = 0.16f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = null,
                                        tint = routeColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "TERMINUS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.6.sp,
                                        color = if (isDarkTheme) Color.White else textPrimaryColor
                                    )
                                }
                            }

                            if (!routeDetail.quaiLabel.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isDarkTheme) Color(0xFF283344) else Color(0xFFE2E8F0)
                                ) {
                                    Text(
                                        text = routeDetail.quaiLabel,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textPrimaryColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Nom du Terminus bien visible en haut
                        Text(
                            text = routeDetail.terminusName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                lineHeight = 24.sp,
                                color = textPrimaryColor
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("route_terminus_name")
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Depuis ${routeDetail.startName}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = textSecondaryColor,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Bandeau d'infos du bus (Attente, Heure de passage, Ponctualité)
                        if (waitLabel != null || displayArrivalTime != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = innerPillColor,
                                border = BorderStroke(1.dp, dividerColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Temps d'attente + arrêt actuel
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (waitLabel != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.AccessTime,
                                                    contentDescription = null,
                                                    tint = if (waitIsImminent) Color(0xFFD32F2F) else routeColor,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = waitLabel,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (waitIsImminent) Color(0xFFD32F2F) else textPrimaryColor
                                                )
                                            }
                                        }
                                        Text(
                                            text = "À $currentStopName",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = textSecondaryColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Passage prévu / estimé & statut ponctualité
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (displayArrivalTime != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    tint = textSecondaryColor,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Passage $displayArrivalTime",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textPrimaryColor
                                                )
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (routeDetail.delayMinutes != 0 &&
                                                !routeDetail.aimedTimeFormatted.isNullOrBlank() &&
                                                routeDetail.aimedTimeFormatted != displayArrivalTime
                                            ) {
                                                Text(
                                                    text = "Prévu ${routeDetail.aimedTimeFormatted}",
                                                    fontSize = 11.sp,
                                                    color = textSecondaryColor
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(5.dp),
                                                color = statusColor.copy(alpha = 0.13f)
                                            ) {
                                                Text(
                                                    text = statusText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = statusColor,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Résumé des arrêts & progression vers le terminus
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isDarkTheme) Color(0xFF232C3B) else Color(0xFFEAF0F6)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Route,
                                        contentDescription = null,
                                        tint = routeColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${routeDetail.totalStopsCount} arrêts dans l'ordre",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimaryColor
                                    )
                                }
                            }

                            val remaining = routeDetail.stopsRemainingToTerminus
                            if (remaining != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = routeColor.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = when (remaining) {
                                            0 -> "Terminus atteint"
                                            1 -> "1 arrêt jusqu'au terminus"
                                            else -> "$remaining arrêts jusqu'au terminus"
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkTheme) Color.White else textPrimaryColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Champ de recherche rapide d'un arrêt sur la ligne
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("search_stop_in_route_input"),
                    placeholder = {
                        Text(
                            text = "Filtrer les ${routeDetail.totalStopsCount} arrêts...",
                            style = MaterialTheme.typography.bodySmall.copy(color = textSecondaryColor)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Rechercher",
                            tint = textSecondaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Effacer",
                                    tint = textSecondaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = routeColor,
                        unfocusedBorderColor = dividerColor,
                        focusedContainerColor = surfaceCardColor,
                        unfocusedContainerColor = surfaceCardColor,
                        focusedTextColor = textPrimaryColor,
                        unfocusedTextColor = textPrimaryColor
                    )
                )

                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                // 4. Liste complète et ordonnée des arrêts (Terminus en haut -> Début en bas)
                if (filteredStops.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucun arrêt ne correspond à \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = textSecondaryColor,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("route_stops_list"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        itemsIndexed(
                            items = filteredStops,
                            key = { index, item -> "${item.stopName}_${item.sequenceIndex}_$index" }
                        ) { index, item ->
                            RouteStopTimelineRow(
                                item = item,
                                routeColor = routeColor,
                                isFirstItem = (index == 0),
                                isLastItem = (index == filteredStops.lastIndex),
                                isDarkTheme = isDarkTheme,
                                onStopClick = {
                                    onStopSelected?.invoke(item.stopName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeQuery(raw: String): String {
    val noDiacritics = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
    return Regex("\\p{InCombiningDiacriticalMarks}+").replace(noDiacritics, "").trim()
}

/**
 * Ligne d'arrêt avec tracé vertical et distinction claire des arrêts déjà passés vs à venir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteStopTimelineRow(
    item: RouteStopItem,
    routeColor: Color,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    isDarkTheme: Boolean,
    onStopClick: () -> Unit
) {
    val textPrimaryColor = if (isDarkTheme) Color.White else Color(0xFF111827)
    val textSecondaryColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
    val trackUpcomingColor = routeColor.copy(alpha = 0.85f)
    val trackPassedColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
    val trackColor = if (item.isPassedBeforeCurrent) trackPassedColor else trackUpcomingColor
    val contentAlpha = if (item.isPassedBeforeCurrent && !item.isStart) 0.65f else 1f

    val rowContainerColor = if (item.isCurrentStop) {
        routeColor.copy(alpha = if (isDarkTheme) 0.18f else 0.10f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(rowContainerColor)
            .then(
                if (item.isCurrentStop) {
                    Modifier.border(1.dp, routeColor.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .clickable(
                onClick = onStopClick,
                onClickLabel = "Voir les prochains passages à ${item.stopName}"
            )
            .padding(end = 8.dp, top = 2.dp, bottom = 2.dp)
            .testTag("stop_item_${item.sequenceIndex}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colonne gauche : tracé vertical + marqueur
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(if (item.isCurrentStop || item.isTerminus || item.isStart) 66.dp else 50.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .padding(
                        top = if (isFirstItem) 24.dp else 0.dp,
                        bottom = if (isLastItem) 24.dp else 0.dp
                    )
                    .background(trackColor)
            )

            when {
                item.isTerminus -> {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD32F2F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Terminus",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                item.isCurrentStop -> {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(routeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
                item.isStart -> {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1976D2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBus,
                            contentDescription = "Début de ligne",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) TransitDarkBg else SketchWhite)
                            .border(
                                width = 2.5.dp,
                                color = if (item.isPassedBeforeCurrent) trackPassedColor else routeColor,
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Colonne centrale : tags contextuels, nom de l'arrêt, correspondances
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(contentAlpha)
                .padding(vertical = 6.dp)
        ) {
            if (item.isTerminus) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = Color(0xFFD32F2F).copy(alpha = 0.14f),
                    modifier = Modifier.padding(bottom = 3.dp)
                ) {
                    Text(
                        text = "TERMINUS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            } else if (item.isStart) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = Color(0xFF1976D2).copy(alpha = 0.14f),
                    modifier = Modifier.padding(bottom = 3.dp)
                ) {
                    Text(
                        text = "DÉPART DE LA LIGNE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFF1976D2),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }

            if (item.isCurrentStop) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = routeColor.copy(alpha = 0.20f),
                    modifier = Modifier.padding(bottom = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (isDarkTheme) Color.White else textPrimaryColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "VOUS ÊTES ICI",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.4.sp,
                            color = if (isDarkTheme) Color.White else textPrimaryColor
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.stopName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (item.isCurrentStop || item.isTerminus || item.isStart) {
                            FontWeight.Black
                        } else {
                            FontWeight.SemiBold
                        },
                        fontSize = if (item.isCurrentStop || item.isTerminus || item.isStart) 15.5.sp else 14.5.sp,
                        color = if (item.isCurrentStop && !isDarkTheme) textPrimaryColor else textPrimaryColor
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "${item.sequenceIndex}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textSecondaryColor.copy(alpha = 0.75f)
                )
            }

            if (item.connectingLines.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Corresp.",
                        fontSize = 11.sp,
                        color = textSecondaryColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    item.connectingLines.take(6).forEach { conn ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isDarkTheme) Color(0xFF263040) else Color(0xFFE2E8F0)
                        ) {
                            Text(
                                text = conn,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimaryColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (item.connectingLines.size > 6) {
                        Text(
                            text = "+${item.connectingLines.size - 6}",
                            fontSize = 10.5.sp,
                            color = textSecondaryColor,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(2.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = textSecondaryColor.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp)
        )
    }
}
