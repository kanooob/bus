package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.RouteLineDetail
import com.example.data.model.RouteStopItem
import com.example.ui.theme.IdelisGold
import com.example.ui.theme.SketchBlack
import com.example.ui.theme.SketchWhite
import com.example.ui.theme.TransitDarkBg

/**
 * Modale affichant la liste complète des arrêts où le bus s'arrête dans l'ordre,
 * avec en haut le terminus et en bas le début selon la direction sélectionnée.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteStopsModal(
    routeDetail: RouteLineDetail,
    currentStopName: String,
    onDirectionSelected: (String) -> Unit,
    onToggleOrder: () -> Unit,
    onStopSelected: ((String) -> Unit)? = null,
    onDismissRequest: () -> Unit,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismissRequest)

    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val bgColor = if (isDarkTheme) TransitDarkBg else SketchWhite
    val surfaceCardColor = if (isDarkTheme) Color(0xFF1E2430) else Color(0xFFF8FAFC)
    val textPrimaryColor = if (isDarkTheme) Color.White else Color(0xFF111111)
    val textSecondaryColor = if (isDarkTheme) Color(0xFF9E9E9E) else Color(0xFF555555)
    val dividerColor = if (isDarkTheme) Color(0xFF262C36) else Color(0xFFE5E7EB)

    val routeColor = try {
        Color(android.graphics.Color.parseColor("#" + routeDetail.colorHex.removePrefix("#")))
    } catch (_: Exception) {
        IdelisGold
    }

    val routeTextColor = try {
        Color(android.graphics.Color.parseColor("#" + routeDetail.textColorHex.removePrefix("#")))
    } catch (_: Exception) {
        Color.White
    }

    // Filtrage des arrêts par recherche si saisie
    val filteredStops = remember(routeDetail.stops, searchQuery) {
        if (searchQuery.isBlank()) {
            routeDetail.stops
        } else {
            val q = searchQuery.trim().lowercase()
            routeDetail.stops.filter {
                it.stopName.lowercase().contains(q) ||
                        it.canonicalName.lowercase().contains(q)
            }
        }
    }

    // Auto-scroll vers l'arrêt actuel de l'utilisateur au premier affichage
    LaunchedEffect(routeDetail.selectedDirection, routeDetail.isTerminusAtTop) {
        val currentIndex = filteredStops.indexOfFirst { it.isCurrentStop }
        if (currentIndex >= 0) {
            val targetScroll = maxOf(0, currentIndex - 1)
            listState.animateScrollToItem(targetScroll)
        }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // 1. Barre supérieure : Ligne, Nom officiel et bouton Fermer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge squircle de la ligne
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(routeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        var isImageLoaded by remember { mutableStateOf(false) }

                        if (!routeDetail.iconUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = routeDetail.iconUrl,
                                contentDescription = "Icône ${routeDetail.lineLabel}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(15.dp)),
                                contentScale = ContentScale.Fit,
                                onSuccess = { isImageLoaded = true },
                                onError = { isImageLoaded = false }
                            )
                        }

                        if (routeDetail.iconUrl.isNullOrBlank() || !isImageLoaded) {
                            Text(
                                text = routeDetail.lineCode,
                                color = routeTextColor,
                                fontWeight = FontWeight.Black,
                                fontSize = if (routeDetail.lineCode.length > 3) 13.sp else 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
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
                        Text(
                            text = "${routeDetail.totalStopsCount} arrêts sur la ligne",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = textSecondaryColor,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    // Bouton Fermer
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag("close_route_stops_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = textPrimaryColor
                        )
                    }
                }

                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                // 2. Sélecteur de direction (Aller / Retour)
                if (routeDetail.availableDirections.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(routeDetail.availableDirections) { index, dir ->
                            val isSelected = dir.equals(routeDetail.selectedDirection, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onDirectionSelected(dir) },
                                label = {
                                    Text(
                                        text = "➔ Vers $dir",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = routeColor.copy(alpha = 0.18f),
                                    selectedLabelColor = if (isDarkTheme) Color.White else Color(0xFF111111),
                                    containerColor = if (isDarkTheme) Color(0xFF1E2430) else Color(0xFFF1F5F9),
                                    labelColor = textSecondaryColor
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) routeColor else dividerColor
                                ),
                                modifier = Modifier.testTag("direction_chip_$index")
                            )
                        }
                    }
                }

                // 3. Bandeau d'indication de l'ordre conforme à la consigne :
                // "en haut le terminus et en bas le debus (selon la direction)"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = surfaceCardColor
                    ),
                    border = CardDefaults.outlinedCardBorder(enabled = true)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (routeDetail.isTerminusAtTop) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = routeColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (routeDetail.isTerminusAtTop) {
                                    "🏁 Terminus en haut ➔ 🚩 Début en bas"
                                } else {
                                    "🚩 Début en haut ➔ 🏁 Terminus en bas"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimaryColor
                                )
                            )
                        }

                        // Bouton d'inversion d'ordre
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDarkTheme) Color(0xFF2A3445) else Color(0xFFE2E8F0),
                            modifier = Modifier
                                .clickable(onClick = onToggleOrder)
                                .testTag("toggle_order_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Inverser l'ordre",
                                    tint = textPrimaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Inverser",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimaryColor
                                )
                            }
                        }
                    }
                }

                // 4. Barre de recherche rapide d'un arrêt sur la ligne
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("search_stop_in_route_input"),
                    placeholder = {
                        Text(
                            text = "Rechercher un arrêt sur cette ligne...",
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
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Effacer",
                                    tint = textSecondaryColor,
                                    modifier = Modifier.size(16.dp)
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

                Spacer(modifier = Modifier.height(4.dp))

                // 5. Liste chronologique ordonnée des arrêts
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
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        itemsIndexed(
                            items = filteredStops,
                            key = { index, item -> "${item.stopName}_${item.sequenceIndex}_$index" }
                        ) { index, item ->
                            val isFirstItem = (index == 0)
                            val isLastItem = (index == filteredStops.lastIndex)

                            RouteStopTimelineRow(
                                item = item,
                                routeColor = routeColor,
                                isFirstItem = isFirstItem,
                                isLastItem = isLastItem,
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

/**
 * Ligne d'arrêt avec track verticale représentant le cheminement du bus.
 */
@Composable
private fun RouteStopTimelineRow(
    item: RouteStopItem,
    routeColor: Color,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    isDarkTheme: Boolean,
    onStopClick: () -> Unit
) {
    val textPrimaryColor = if (isDarkTheme) Color.White else Color(0xFF111111)
    val textSecondaryColor = if (isDarkTheme) Color(0xFF9E9E9E) else Color(0xFF555555)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onStopClick)
            .padding(vertical = 2.dp)
            .testTag("stop_item_${item.sequenceIndex}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track verticale (colonne de gauche) avec point ou icône
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(if (item.isCurrentStop || item.isTerminus || item.isStart) 66.dp else 48.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ligne verticale connectant les arrêts
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .padding(
                        top = if (isFirstItem) 22.dp else 0.dp,
                        bottom = if (isLastItem) 22.dp else 0.dp
                    )
                    .background(routeColor.copy(alpha = 0.45f))
            )

            // Pastille / marqueur de l'arrêt
            when {
                item.isTerminus -> {
                    // Badge Terminus (En haut dans la consigne)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
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
                item.isStart -> {
                    // Badge Début (En bas dans la consigne)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
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
                item.isCurrentStop -> {
                    // Arrêt actuel où l'utilisateur se trouve
                    Box(
                        modifier = Modifier
                            .size(22.dp)
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
                else -> {
                    // Arrêt intermédiaire régulier
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color(0xFF475569) else Color(0xFFCBD5E1))
                            .border(2.dp, routeColor.copy(alpha = 0.85f), CircleShape)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Contenu textuel de l'arrêt (colonne de droite)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
        ) {
            // Badges contextuels : Terminus / Début / Votre arrêt actuel
            if (item.isTerminus) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFD32F2F).copy(alpha = 0.15f),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = "🏁 TERMINUS (ARRIVÉE)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (item.isStart) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1976D2).copy(alpha = 0.15f),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = "🚩 DÉBUT DE LIGNE (DÉPART)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1976D2),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (item.isCurrentStop) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = routeColor.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, routeColor),
                    modifier = Modifier.padding(bottom = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = routeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "VOTRE ARRÊT ACTUEL (Vous êtes ici)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = textPrimaryColor
                        )
                    }
                }
            }

            // Nom de l'arrêt
            Text(
                text = item.stopName,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (item.isCurrentStop || item.isTerminus || item.isStart) FontWeight.Black else FontWeight.SemiBold,
                    fontSize = if (item.isCurrentStop || item.isTerminus || item.isStart) 16.sp else 14.5.sp,
                    color = if (item.isCurrentStop) routeColor else textPrimaryColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Lignes en correspondance à cet arrêt
            if (item.connectingLines.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lignes : ",
                        fontSize = 11.sp,
                        color = textSecondaryColor,
                        fontWeight = FontWeight.Medium
                    )
                    item.connectingLines.take(5).forEach { conn ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isDarkTheme) Color(0xFF262C36) else Color(0xFFE2E8F0),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = conn,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimaryColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (item.connectingLines.size > 5) {
                        Text(
                            text = "+${item.connectingLines.size - 5}",
                            fontSize = 10.sp,
                            color = textSecondaryColor
                        )
                    }
                }
            }
        }
    }
}
