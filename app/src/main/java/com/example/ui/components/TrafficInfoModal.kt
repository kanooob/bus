package com.example.ui.components

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.IdelisPeriod
import com.example.data.model.TrafficAlert
import com.example.ui.theme.SketchBlack
import com.example.ui.theme.SketchRed
import com.example.ui.theme.StatusDelayed
import com.example.ui.theme.StatusGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrafficInfoModal(
    alerts: List<TrafficAlert>,
    activePeriod: IdelisPeriod = IdelisPeriod.VERTE,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRefresh: () -> Unit = {},
    onDismissRequest: () -> Unit,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bgColor = if (isDarkTheme) Color(0xFF141B26) else Color.White
    val titleColor = if (isDarkTheme) Color.White else SketchBlack
    val contentColor = if (isDarkTheme) Color(0xFFF0F4F8) else SketchBlack
    val mutedColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

    var isHornPressed by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mediaPlayer = null
        }
    }

    fun startHorn() {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(context, R.raw.gong_pi_ton_bus)?.apply {
                    isLooping = true
                }
            }
            mediaPlayer?.let { player ->
                player.seekTo(0)
                player.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopHorn() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    player.seekTo(0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AlertDialog(
        onDismissRequest = {
            stopHorn()
            onDismissRequest()
        },
        containerColor = bgColor,
        titleContentColor = titleColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.testTag("traffic_info_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF57C00),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Info Trafic",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = titleColor
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("refresh_traffic_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = SketchRed,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualiser les infos trafic",
                                tint = contentColor,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    // Bouton Klaxon à gauche de la croix (joue le son tant qu'on maintient appuyé)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isHornPressed) SketchRed else (if (isDarkTheme) Color(0xFF1C2636) else Color(0xFFF0F0F0))
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isHornPressed = true
                                        startHorn()
                                        try {
                                            tryAwaitRelease()
                                        } finally {
                                            isHornPressed = false
                                            stopHorn()
                                        }
                                    }
                                )
                            }
                            .testTag("klaxon_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Klaxon bus",
                            tint = if (isHornPressed) Color.White else (if (isDarkTheme) Color.White else SketchBlack),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            stopHorn()
                            onDismissRequest()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer", tint = contentColor)
                    }
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "Perturbations en direct (idelis.fr • ${activePeriod.label}) :",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = mutedColor,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                if (isLoading && alerts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = SketchRed, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Chargement des alertes trafic...",
                                style = MaterialTheme.typography.bodySmall.copy(color = mutedColor)
                            )
                        }
                    }
                } else if (!errorMessage.isNullOrBlank() && alerts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SketchRed.copy(alpha = 0.08f))
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = SketchRed,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(8.dp)) {
                                Text("Réessayer", fontWeight = FontWeight.Bold, color = SketchRed)
                            }
                        }
                    }
                } else if (alerts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(StatusGreen.copy(alpha = 0.10f))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aucune perturbation signalée",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Le réseau Idelis circule normalement selon idelis.fr.",
                                style = MaterialTheme.typography.bodySmall.copy(color = mutedColor),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(alerts, key = { it.id }) { alert ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkTheme) Color(0xFF1C2636) else Color(0xFFFBFBFB)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (isDarkTheme) Color(0xFF2A3A52) else Color(0xFFE0E0E0)
                                )
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Lignes impactées : pictos officiels + badges texte
                                if (alert.affectedLinePictos.isNotEmpty() || alert.affectedLines.isNotEmpty()) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
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
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                        if (alert.affectedLinePictos.isEmpty()) {
                                            alert.affectedLines.forEach { line ->
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(StatusDelayed.copy(alpha = 0.16f))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "Ligne $line",
                                                        color = StatusDelayed,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                Text(
                                    text = alert.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = contentColor
                                    )
                                )

                                if (alert.messageText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = alert.messageText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = contentColor,
                                            lineHeight = 18.sp
                                        )
                                    )
                                }

                                if (alert.publishedDate.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = alert.publishedDate,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = mutedColor,
                                            fontSize = 10.5.sp
                                        )
                                    )
                                }

                                if (!alert.deviationLink.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(alert.deviationLink))
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = SketchRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Voir la déviation / le plan",
                                            color = SketchRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    stopHorn()
                    onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SketchRed, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Fermer", fontWeight = FontWeight.Bold)
            }
        }
    )
}
