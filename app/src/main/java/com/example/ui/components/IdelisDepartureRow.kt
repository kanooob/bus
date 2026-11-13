package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.DepartureComparison
import com.example.ui.theme.IdelisGold
import com.example.ui.theme.SketchDivider
import com.example.ui.theme.SketchRed

/**
 * Formate le nom d'affichage de la ligne.
 */
private fun formatLineLabel(lineRef: String): String {
    return when {
        lineRef.equals("F", ignoreCase = true) -> "Fébus"
        lineRef.equals("COXI", ignoreCase = true) -> "Coxitis"
        lineRef.startsWith("Ligne", ignoreCase = true) -> lineRef
        else -> "Ligne $lineRef"
    }
}

/**
 * Ligne de passage conforme aux spécifications :
 * - Clic sur la ligne pour afficher l'itinéraire et tous les arrêts dans l'ordre (terminus en haut, début en bas).
 * - Icône en squircle (coins très arrondis 15dp) SANS bordure, l'image est découpée dans le squircle.
 * - Ligne à droite de l'icône, au niveau du haut.
 * - Direction en dessous de la ligne, au niveau du bas.
 * - Tout à droite : temps d'attente au niveau de la ligne.
 * - Statut réel de retard ou d'avance calculé par rapport à l'horaire théorique GTFS (+1min, -1min, A l'heure).
 * - Séparateur horizontal net entre chaque passage.
 */
@Composable
fun IdelisDepartureRow(
    departure: DepartureComparison,
    isDarkTheme: Boolean = false,
    showSimulatedDelays: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val routeColor = try {
        Color(android.graphics.Color.parseColor("#" + departure.routeColorHex.removePrefix("#")))
    } catch (_: Exception) {
        IdelisGold
    }

    val routeTextColor = try {
        Color(android.graphics.Color.parseColor("#" + departure.routeTextColorHex.removePrefix("#")))
    } catch (_: Exception) {
        Color.White
    }

    val lineNameColor = if (isDarkTheme) Color.White else Color(0xFF111111)
    val destinationColor = if (isDarkTheme) Color(0xFFD1D5DB) else Color(0xFF2D3748)
    val dividerColor = if (isDarkTheme) Color(0xFF262C36) else Color(0xFFE5E7EB)

    // Détermination du temps d'attente (haut droit)
    val isImminent = departure.minutesUntilDeparture <= 1L
    val mainTimeText = when {
        departure.minutesUntilDeparture <= 0 -> "Imminent"
        departure.minutesUntilDeparture == 1L -> "Dans 1min"
        else -> "Dans ${departure.minutesUntilDeparture}min"
    }

    val mainTimeColor = when {
        isImminent -> Color(0xFFD32F2F) // Alerte rouge contrastée pour départ imminent
        isDarkTheme -> Color.White
        else -> Color(0xFF111111) // Contraste net sur fond blanc
    }

    // Détermination du statut de retard (bas droit)
    val subStatusText = when {
        departure.delayMinutes > 0 -> "+${departure.delayMinutes}min"
        departure.delayMinutes < 0 -> "${departure.delayMinutes}min"
        else -> "A l'heure"
    }

    val subStatusColor = when {
        departure.delayMinutes > 0 -> Color(0xFFD32F2F) // Retard en rouge
        departure.delayMinutes < 0 -> Color(0xFF0288D1) // Avance en bleu
        else -> Color(0xFF2E7D32) // À l'heure en vert ponctuel
    }

    // Calcul de l'heure exacte d'arrivée
    val arrivalClockTime = remember(departure) {
        if (!departure.expectedDepartureTimeFormatted.isNullOrBlank() && departure.expectedDepartureTimeFormatted.contains(":")) {
            departure.expectedDepartureTimeFormatted
        } else if (departure.aimedDepartureTimeFormatted.isNotBlank() && departure.aimedDepartureTimeFormatted.contains(":")) {
            departure.aimedDepartureTimeFormatted
        } else {
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Paris")).apply {
                add(java.util.Calendar.MINUTE, departure.minutesUntilDeparture.toInt())
            }
            String.format(java.util.Locale.FRANCE, "%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
        }
    }

    // Le statut est affiché uniquement quand la simulation de retard est active et que la donnée est en temps réel
    val shouldDisplayStatus = showSimulatedDelays && departure.isRealTime

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("departure_row_${departure.id}")
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        onClick = onClick,
                        onClickLabel = "Afficher les arrêts de la ligne ${departure.lineRef}"
                    )
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Icône en squircle SANS bordure : affiche l'image PNG officielle Idelis ou le badge authentique de la ligne
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(routeColor),
                contentAlignment = Alignment.Center
            ) {
                var isImageLoaded by remember(departure.iconUrl) { mutableStateOf(false) }

                if (!departure.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(departure.iconUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Icône officielle ligne ${departure.lineRef}",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(15.dp)),
                        contentScale = ContentScale.Fit,
                        onSuccess = { isImageLoaded = true },
                        onError = { isImageLoaded = false }
                    )
                }

                // Si l'icône distante n'est pas encore chargée ou absente, afficher le badge stylisé officiel avec la typographie Idelis
                if (departure.iconUrl.isNullOrBlank() || !isImageLoaded) {
                    Text(
                        text = departure.lineRef,
                        color = routeTextColor,
                        fontWeight = FontWeight.Black,
                        fontSize = if (departure.lineRef.length > 3) 13.sp else 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 2. Colonne centrale : Ligne au niveau du haut, Direction étendue jusqu'au bloc horaire d'arrivée
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // En haut : Ligne
                Text(
                    text = formatLineLabel(departure.lineRef),
                    color = lineNameColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // En bas : Direction affichée jusqu'au temps d'arrivée
                Text(
                    text = departure.destinationName,
                    color = destinationColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. Colonne de droite : Heure d'arrivée, Temps d'attente et Statut de ponctualité
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End
            ) {
                // Temps d'attente (haut droit)
                Text(
                    text = mainTimeText,
                    color = mainTimeColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )

                Spacer(modifier = Modifier.height(1.dp))

                // Heure d'arrivée programmée ou estimée
                Text(
                    text = "Arrivée $arrivalClockTime",
                    color = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.5.sp
                )

                Spacer(modifier = Modifier.height(1.dp))

                // Statut de retard / avance / à l'heure
                Text(
                    text = subStatusText,
                    color = subStatusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }

            // Flèche subtile indiquant que la ligne est cliquable pour voir l'itinéraire complet des arrêts
            if (onClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Voir tous les arrêts de la ligne",
                    tint = if (isDarkTheme) Color(0xFF6B7280) else Color(0xFF9CA3AF),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Ligne de séparation horizontale
        HorizontalDivider(
            thickness = 2.dp,
            color = dividerColor
        )
    }
}
