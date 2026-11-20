package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
 * Modale « Plus d'info sur le bus » :
 * - En-tête compact teinté aux couleurs de la ligne avec Terminus fixe, quai, horaire & ponctualité.
 * - Recherche rapide dépliable à la demande pour laisser un maximum de place au tracé.
 * - Tracé vertical continu avec marge inférieure généreuse au-dessus de la barre de navigation
 *   afin que l'arrêt le plus bas (« Départ de la ligne ») reste toujours parfaitement visible.
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
    var isSearchExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val bgColor = if (isDarkTheme) TransitDarkBg else SketchWhite
    val cardSurfaceColor = if (isDarkTheme) Color(0xFF172030) else Color(0xFFF6F8FB)
    val pillBgColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFFFFFFF)
    val textPrimaryColor = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondaryColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
    val dividerColor = if (isDarkTheme) Color(0xFF263244) else Color(0xFFE2E8F0)

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
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .imePadding()
                .testTag("route_stops_modal"),
            color = bgColor
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. En-tête teinté compact (statut + ligne + Terminus + infos temps réel)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        routeColor.copy(alpha = if (isDarkTheme) 0.24f else 0.14f),
                                        routeColor.copy(alpha = if (isDarkTheme) 0.06f else 0.03f),
                                        bgColor
                                    )
                                )
                            )
                        }
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            )
                        )
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 10.dp)
                        .testTag("route_terminus_header_card")
                ) {
                    // Barre supérieure : icône ligne, nom de la ligne, quai, recherche & fermeture
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IdelisLineIconBadge(
                            lineRef = routeDetail.lineCode,
                            iconUrl = routeDetail.iconUrl,
                            routeColor = routeColor,
                            routeTextColor = routeTextColor,
                            size = 40.dp,
                            contentDescription = "Icône ${routeDetail.lineLabel}"
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = routeDetail.lineLabel,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = textPrimaryColor
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!routeDetail.quaiLabel.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = routeColor.copy(alpha = if (isDarkTheme) 0.24f else 0.16f)
                                    ) {
                                        Text(
                                            text = routeDetail.quaiLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = textPrimaryColor,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            val subtitle = routeDetail.routeLongName
                                .takeIf { it.isNotBlank() && !it.equals(routeDetail.lineLabel, ignoreCase = true) }
                            if (subtitle != null) {
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
                        }

                        if (routeDetail.stops.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    if (isSearchExpanded) {
                                        isSearchExpanded = false
                                        searchQuery = ""
                                    } else {
                                        isSearchExpanded = true
                                    }
                                },
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("toggle_search_stop_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = if (isSearchExpanded) "Masquer la recherche" else "Rechercher un arrêt",
                                    tint = if (isSearchExpanded || searchQuery.isNotBlank()) routeColor else textPrimaryColor
                                )
                            }
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bloc Terminus compact
                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "DIRECTION TERMINUS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.7.sp,
                                    color = textSecondaryColor
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (isDarkTheme) Color(0xFF1F2B3D) else Color(0xFFE9EFF6)
                            ) {
                                Text(
                                    text = when {
                                        routeDetail.totalStopsCount > 0 -> "${routeDetail.totalStopsCount} arrêts"
                                        routeDetail.isLoadingStops -> "Chargement..."
                                        else -> "0 arrêt"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimaryColor,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

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

                        if (routeDetail.startName.isNotBlank()) {
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
                        }

                        // Bandeau compact temps réel du passage sélectionné
                        val remaining = routeDetail.stopsRemainingToTerminus
                        if (waitLabel != null || displayArrivalTime != null || remaining != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = pillBgColor,
                                border = BorderStroke(1.dp, dividerColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (waitLabel != null) {
                                                Icon(
                                                    imageVector = Icons.Default.AccessTime,
                                                    contentDescription = null,
                                                    tint = if (waitIsImminent) Color(0xFFD32F2F) else routeColor,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = waitLabel,
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (waitIsImminent) Color(0xFFD32F2F) else textPrimaryColor
                                                )
                                            }
                                            if (remaining != null) {
                                                if (waitLabel != null) {
                                                    Text(
                                                        text = "  •  ",
                                                        fontSize = 12.sp,
                                                        color = textSecondaryColor
                                                    )
                                                }
                                                Text(
                                                    text = when (remaining) {
                                                        0 -> "Terminus atteint"
                                                        1 -> "1 arrêt restant"
                                                        else -> "$remaining arrêts restants"
                                                    },
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = textSecondaryColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
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

                                    if (displayArrivalTime != null) {
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(horizontalAlignment = Alignment.End) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    tint = textSecondaryColor,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = displayArrivalTime,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = textPrimaryColor
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Surface(
                                                shape = RoundedCornerShape(5.dp),
                                                color = statusColor.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = statusText,
                                                    fontSize = 10.5.sp,
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
                    }

                    // 2. Barre de recherche dépliable (n'encombre pas l'écran par défaut)
                    AnimatedVisibility(
                        visible = isSearchExpanded || searchQuery.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, end = 8.dp)
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
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        isSearchExpanded = false
                                    },
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Effacer",
                                        tint = textSecondaryColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = routeColor,
                                unfocusedBorderColor = dividerColor,
                                focusedContainerColor = cardSurfaceColor,
                                unfocusedContainerColor = cardSurfaceColor,
                                focusedTextColor = textPrimaryColor,
                                unfocusedTextColor = textPrimaryColor
                            )
                        )
                    }
                }

                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                // 3. Liste complète et ordonnée des arrêts (Terminus en haut -> Début en bas)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                            )
                        )
                ) {
                    when {
                        routeDetail.stops.isEmpty() && routeDetail.isLoadingStops -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                                    .testTag("route_stops_loading"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(color = routeColor)
                                    Text(
                                        text = "Chargement de tous les arrêts de la ligne...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = textSecondaryColor,
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                        routeDetail.stops.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                                    .testTag("route_stops_empty"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Aucun arrêt détaillé disponible pour cette ligne pour le moment.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = textSecondaryColor,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                        }
                        filteredStops.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
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
                        }
                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("route_stops_list"),
                                contentPadding = PaddingValues(
                                    start = 14.dp,
                                    end = 14.dp,
                                    top = 10.dp,
                                    bottom = 32.dp
                                )
                            ) {
                                itemsIndexed(
                                    items = filteredStops,
                                    key = { index, item -> "${item.stopName}_${item.sequenceIndex}_$index" }
                                ) { index, item ->
                                    RouteStopTimelineRow(
                                        item = item,
                                        routeColor = routeColor,
                                        isTerminusAtTop = routeDetail.isTerminusAtTop,
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
    }
}

private fun normalizeQuery(raw: String): String {
    val noDiacritics = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
    return Regex("\\p{InCombiningDiacriticalMarks}+").replace(noDiacritics, "").trim()
}

/**
 * Ligne d'arrêt avec tracé vertical continu (Canvas + IntrinsicSize.Min) et mise en valeur nette
 * du Terminus (haut), de la position actuelle et de l'arrêt de départ (bas).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteStopTimelineRow(
    item: RouteStopItem,
    routeColor: Color,
    isTerminusAtTop: Boolean,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    isDarkTheme: Boolean,
    onStopClick: () -> Unit
) {
    val bgColor = if (isDarkTheme) TransitDarkBg else SketchWhite
    val textPrimaryColor = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondaryColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
    val trackUpcomingColor = routeColor.copy(alpha = 0.90f)
    val trackPassedColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)

    val isTopSegmentPassed = if (isTerminusAtTop) {
        item.isPassedBeforeCurrent
    } else {
        item.isPassedBeforeCurrent || item.isCurrentStop
    }
    val isBottomSegmentPassed = if (isTerminusAtTop) {
        item.isPassedBeforeCurrent || item.isCurrentStop
    } else {
        item.isPassedBeforeCurrent
    }

    val topTrackColor = if (isTopSegmentPassed) trackPassedColor else trackUpcomingColor
    val bottomTrackColor = if (isBottomSegmentPassed) trackPassedColor else trackUpcomingColor

    // Conserver une lisibilité maximale sur l'arrêt le plus bas (départ) et l'arrêt actuel
    val contentAlpha = if (item.isPassedBeforeCurrent && !item.isStart && !item.isCurrentStop) 0.72f else 1f

    val rowContainerColor = when {
        item.isCurrentStop -> routeColor.copy(alpha = if (isDarkTheme) 0.18f else 0.11f)
        item.isTerminus -> Color(0xFFD32F2F).copy(alpha = if (isDarkTheme) 0.12f else 0.06f)
        item.isStart -> Color(0xFF1976D2).copy(alpha = if (isDarkTheme) 0.14f else 0.07f)
        else -> Color.Transparent
    }
    val rowBorderColor = when {
        item.isCurrentStop -> routeColor.copy(alpha = 0.70f)
        item.isTerminus -> Color(0xFFD32F2F).copy(alpha = 0.30f)
        item.isStart -> Color(0xFF1976D2).copy(alpha = 0.35f)
        else -> null
    }
    val isCardHighlighted = item.isCurrentStop || item.isTerminus || item.isStart

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .defaultMinSize(minHeight = 52.dp)
            .padding(vertical = if (isCardHighlighted) 3.dp else 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(rowContainerColor)
            .then(
                if (rowBorderColor != null) {
                    Modifier.border(1.dp, rowBorderColor, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable(
                onClick = onStopClick,
                onClickLabel = "Voir les prochains passages à ${item.stopName}"
            )
            .padding(end = 10.dp)
            .testTag("stop_item_${item.sequenceIndex}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colonne gauche : tracé continu adaptatif quelle que soit la hauteur de la ligne
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val strokePx = 4.dp.toPx()

                if (!isFirstItem) {
                    drawLine(
                        color = topTrackColor,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, centerY),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Butt
                    )
                }
                if (!isLastItem) {
                    drawLine(
                        color = bottomTrackColor,
                        start = Offset(centerX, centerY),
                        end = Offset(centerX, size.height),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Butt
                    )
                }
            }

            when {
                item.isTerminus -> {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD32F2F))
                            .border(2.dp, bgColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Terminus",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                item.isCurrentStop -> {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(routeColor.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(routeColor)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }
                item.isStart -> {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1976D2))
                            .border(2.dp, bgColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBus,
                            contentDescription = "Début de ligne",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .border(
                                width = 2.5.dp,
                                color = if (item.isPassedBeforeCurrent) trackPassedColor else routeColor,
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Colonne centrale : tags compacts, nom de l'arrêt, commune et correspondances
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(contentAlpha)
                .padding(vertical = if (isCardHighlighted) 10.dp else 8.dp)
        ) {
            if (item.isTerminus || item.isStart || item.isCurrentStop) {
                FlowRow(
                    modifier = Modifier.padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (item.isTerminus) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = Color(0xFFD32F2F).copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "TERMINUS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = Color(0xFFD32F2F),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (item.isStart) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = Color(0xFF1976D2).copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "DÉPART DE LA LIGNE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = if (isDarkTheme) Color(0xFF60A5FA) else Color(0xFF1565C0),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (item.isCurrentStop) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = routeColor.copy(alpha = 0.22f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (isDarkTheme) Color.White else textPrimaryColor,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "VOUS ÊTES ICI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.4.sp,
                                    color = if (isDarkTheme) Color.White else textPrimaryColor
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.stopName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isCardHighlighted) FontWeight.Black else FontWeight.SemiBold,
                            fontSize = if (isCardHighlighted) 15.5.sp else 14.5.sp,
                            color = textPrimaryColor
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!item.commune.isNullOrBlank()) {
                        Text(
                            text = item.commune,
                            fontSize = 11.5.sp,
                            color = textSecondaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = "${item.sequenceIndex}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondaryColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (item.connectingLines.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
                            shape = RoundedCornerShape(5.dp),
                            color = if (isDarkTheme) Color(0xFF263040) else Color(0xFFE2E8F0)
                        ) {
                            Text(
                                text = conn,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimaryColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
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
    }
}
