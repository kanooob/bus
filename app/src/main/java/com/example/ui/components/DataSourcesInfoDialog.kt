package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.ui.theme.IdelisGold
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusTheoretical
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TransitCardBorder
import com.example.ui.theme.TransitSurface
import com.example.ui.theme.TransitSurfaceVariant

@Composable
fun DataSourcesInfoDialog(
    isSimulationMode: Boolean,
    isOfflineFallback: Boolean,
    statusMessage: String,
    onToggleSimulation: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = TransitSurface,
        titleContentColor = TextPrimary,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.testTag("data_sources_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(IdelisGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CompareArrows,
                            contentDescription = null,
                            tint = IdelisGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Architecture & Flux Idelis",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer", tint = TextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // État actuel du flux
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TransitSurfaceVariant)
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "STATUT DU FLUX ACTUEL :",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isOfflineFallback) StatusTheoretical else StatusGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Contrôle de mode : Simulateur SIRI vs Appel SOAP réel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TransitSurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Flux Temps Réel Direct",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                            Text(
                                text = "Appels HTTPS directs vers le réseau Idelis • Retards et avances calculés d'après les horaires théoriques",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                            )
                        }
                    }

                Spacer(modifier = Modifier.height(16.dp))

                // Source 1 : GTFS Théorique
                Text(
                    text = "1. DONNÉES THÉORIQUES (GTFS LOCAL)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = IdelisGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• Source : data.idelis.fr (Open Data Idelis / Agglomération de Pau).\n" +
                           "• Fichiers traités : stops.txt, routes.txt, trips.txt, stop_times.txt.\n" +
                           "• Stockage : Base Room SQLite locale, requêtée par AimedDepartureTime.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Source 2 : SIRI Temps Réel
                Text(
                    text = "2. DONNÉES TEMPS RÉEL (SIRI SOAP)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = StatusGreen,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• Endpoint : https://api.idelis.fr/\n" +
                           "• Action SOAP : GetStopMonitoring\n" +
                           "• Champs décodés : LineRef, PublishedLineName, DestinationName, AimedDepartureTime, ExpectedDepartureTime, DepartureStatus.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Logique de comparaison
                Text(
                    text = "3. CALCUL DU RETARD / AVANCE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• diff = ExpectedDepartureTime - AimedDepartureTime (en minutes).\n" +
                           "• Affichage : '17:16 GTFS ➔ 17:17 Temps réel [+1 min]'\n" +
                           "• Si l'API échoue : basculement automatique sur le GTFS local avec indicateur Hors-Ligne.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(containerColor = IdelisGold, contentColor = Color(0xFF101726)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Compris", fontWeight = FontWeight.Bold)
            }
        }
    )
}
