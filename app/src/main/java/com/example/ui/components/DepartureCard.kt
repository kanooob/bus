package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DepartureComparison
import com.example.data.model.DepartureStatus
import com.example.ui.theme.IdelisGold
import com.example.ui.theme.StatusDelayed
import com.example.ui.theme.StatusEarly
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusTheoretical
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TransitCardBorder
import com.example.ui.theme.TransitSurface
import com.example.ui.theme.TransitSurfaceVariant

@Composable
fun DepartureCard(
    departure: DepartureComparison,
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("departure_card_${departure.id}"),
        colors = CardDefaults.cardColors(containerColor = TransitSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TransitCardBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Ligne principale : Badge de ligne, Destination, Temps estimé restant
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Badge de ligne Idelis (Fébus, T1, etc.)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(routeColor)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = departure.lineRef,
                            color = routeTextColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = departure.destinationName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = departure.lineName,
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                            maxLines = 1
                        )
                    }
                }

                // Temps restant (style afficheur LED)
                Column(horizontalAlignment = Alignment.End) {
                    val waitText = when {
                        departure.minutesUntilDeparture <= 0 -> "À quai"
                        departure.minutesUntilDeparture == 1L -> "Dans 1 min"
                        else -> "Dans ${departure.minutesUntilDeparture} min"
                    }
                    Text(
                        text = waitText,
                        color = if (departure.minutesUntilDeparture <= 3) IdelisGold else TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Cadre de comparaison spécifique : Théorique GTFS vs SIRI Temps Réel
            Surface(
                color = TransitSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TransitCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Côté Théorique (GTFS)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusTheoretical)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GTFS Théorique :",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = departure.aimedDepartureTimeFormatted,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }

                        // Flèche de comparaison
                        if (departure.isRealTime && departure.expectedDepartureTimeFormatted != null) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Vers",
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )

                            // Côté Temps Réel (SIRI)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(StatusGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SIRI Temps réel :",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = departure.expectedDepartureTimeFormatted,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (departure.delayMinutes > 0) StatusDelayed else StatusGreen,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Ligne de synthèse de l'écart (ex: "17:16 GTFS ➔ 17:17 Temps réel [+1 min]")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = departure.comparisonLabel,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        // Badge de statut (Retard / À l'heure / Avance / GTFS)
                        StatusChip(status = departure.status, delayMinutes = departure.delayMinutes)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(
    status: DepartureStatus,
    delayMinutes: Int,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label, icon) = when (status) {
        DepartureStatus.ON_TIME -> Quadruple(
            StatusGreen.copy(alpha = 0.15f),
            StatusGreen,
            "À l'heure",
            Icons.Default.CheckCircle
        )
        DepartureStatus.DELAYED -> Quadruple(
            StatusDelayed.copy(alpha = 0.18f),
            StatusDelayed,
            "+$delayMinutes min",
            Icons.Default.Warning
        )
        DepartureStatus.EARLY -> Quadruple(
            StatusEarly.copy(alpha = 0.18f),
            StatusEarly,
            "$delayMinutes min",
            Icons.Default.Schedule
        )
        DepartureStatus.THEORETICAL_ONLY -> Quadruple(
            StatusTheoretical.copy(alpha = 0.15f),
            StatusTheoretical,
            "GTFS",
            Icons.Default.Info
        )
        DepartureStatus.CANCELLED -> Quadruple(
            StatusDelayed.copy(alpha = 0.25f),
            StatusDelayed,
            "Supprimé",
            Icons.Default.Warning
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
