package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.TrafficAlert
import com.example.ui.theme.IdelisGold
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TransitCardBorder
import com.example.ui.theme.TransitSurface

@Composable
fun TrafficAlertsView(
    alerts: List<TrafficAlert>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "INFO TRAFIC & PERTURBATIONS RÉSEAU IDELIS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = IdelisGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Source : Flux direct idelis.fr (?type=777 • infostrafic)",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        items(alerts, key = { it.id }) { alert ->
            TrafficAlertCard(alert = alert)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrafficAlertCard(
    alert: TrafficAlert,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TransitSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TransitCardBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                Text(
                    text = alert.publishedDate,
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                )
            }

            if (alert.messageText.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = alert.messageText,
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, lineHeight = 20.sp)
                )
            }

            if (alert.affectedLinePictos.isNotEmpty() || alert.affectedLines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    alert.affectedLinePictos.forEach { pictoUrl ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(pictoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Picto ligne impactée",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    if (alert.affectedLinePictos.isEmpty()) {
                        alert.affectedLines.forEach { line ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(IdelisGold.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Ligne $line",
                                    color = IdelisGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            if (!alert.deviationLink.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(alert.deviationLink)))
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = IdelisGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Voir la déviation", color = IdelisGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
