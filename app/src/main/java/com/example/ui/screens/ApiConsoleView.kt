package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.IdelisGold
import com.example.ui.theme.IdelisT1Blue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TransitCardBorder
import com.example.ui.theme.TransitSurface
import com.example.ui.theme.TransitSurfaceVariant

@Composable
fun ApiConsoleView(
    requestorRef: String,
    isSimulationMode: Boolean,
    isLoading: Boolean,
    syncSuccessMessage: String?,
    onRequestorRefChanged: (String) -> Unit,
    onToggleSimulation: () -> Unit,
    onSyncExploreStops: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputRef by remember(requestorRef) { mutableStateOf(requestorRef) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "CONSOLE APIS & JEUX DE DONNÉES IDELIS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = IdelisGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Intégration d'Explore v2.1 (OpenDataSoft) et du webservice SOAP SIRI",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
            }
        }

        // Section 1 : Synchronisation OpenDataSoft Explore v2.1
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TransitSurface),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TransitCardBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(IdelisGold.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = IdelisGold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "1️⃣ API Explore v2.1 (data.idelis.fr)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Schéma officiel : /api/explore/v2.1/catalog/datasets/{id}/records?limit=20&offset=0\n" +
                               "Jeux synchronisés : points d'arrêt, lignes GTFS, P+R, vélos IdeCycle, parc véhicules.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (syncSuccessMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(StatusGreen.copy(alpha = 0.15f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = syncSuccessMessage, color = StatusGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Button(
                        onClick = onSyncExploreStops,
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = IdelisGold, contentColor = Color(0xFF101726)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color(0xFF101726), modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Synchroniser les arrêts depuis Explore v2.1", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section 2 : Configuration du Webservice SOAP SIRI (api.idelis.fr/siri.svc)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TransitSurface),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TransitCardBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(StatusGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = StatusGreen)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "2️⃣ Temps Réel SIRI (api.idelis.fr/siri.svc)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Endpoint SOAP : https://api.idelis.fr/siri.svc\n" +
                               "WSDL : https://api.idelis.fr/siri.svc?wsdl\n" +
                               "Opérations : GetStopMonitoring, GetVehicleMonitoring, GetGeneralMessage",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Champ RequestorRef
                    Text(
                        text = "IDENTIFIANT REQUESTOR REF :",
                        style = MaterialTheme.typography.labelSmall.copy(color = IdelisGold, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = inputRef,
                        onValueChange = { inputRef = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ex: VOTRE_COMPTE_IDELIS", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IdelisGold,
                            unfocusedBorderColor = TransitCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = TransitSurfaceVariant,
                            unfocusedContainerColor = TransitSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.idelis.fr/Login/Register"))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TransitCardBorder)
                        ) {
                            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, tint = IdelisT1Blue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Créer compte gratuit", color = IdelisT1Blue, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onRequestorRefChanged(inputRef) },
                            colors = ButtonDefaults.buttonColors(containerColor = IdelisT1Blue, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Enregistrer")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Flux temps réel direct
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(TransitSurfaceVariant)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Flux Réseau Direct (idelis.fr & SIRI)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                            Text(
                                text = "Requêtes directes vers le réseau Idelis • Retards et avances réels comparés aux horaires théoriques GTFS",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                            )
                        }
                    }
                }
            }
        }

        // Section 3 : Liens officiels Open Data
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TransitSurface),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TransitCardBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "3️⃣ Fichier GTFS Complet d'Agglomération",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Téléchargement direct du fichier GTFS théorique d'Idelis :\n" +
                               "https://opendata.idelis.fr/?file=GTFS_MultiRoutes/Data.GTFS.zip\n" +
                               "Validité jusqu'en 2027 (stops.txt, routes.txt, trips.txt, stop_times.txt).",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                    )
                }
            }
        }
    }
}
