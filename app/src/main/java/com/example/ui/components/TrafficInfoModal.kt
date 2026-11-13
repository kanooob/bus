package com.example.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.IdelisPeriod
import com.example.data.model.TrafficAlert
import com.example.ui.theme.SketchBlack
import com.example.ui.theme.SketchRed
import com.example.ui.theme.StatusDelayed
import com.example.ui.theme.StatusGreen

@Composable
fun TrafficInfoModal(
    alerts: List<TrafficAlert>,
    activePeriod: IdelisPeriod = IdelisPeriod.VERTE,
    onDismissRequest: () -> Unit,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bgColor = if (isDarkTheme) Color(0xFF141B26) else Color.White
    val titleColor = if (isDarkTheme) Color.White else SketchBlack
    val contentColor = if (isDarkTheme) Color(0xFFF0F4F8) else SketchBlack

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF57C00),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Info Trafic Idelis",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = titleColor
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

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
                        text = "État des lignes et perturbations en direct :",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = contentColor,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                items(alerts, key = { it.id }) { alert ->
                    val isWarn = alert.severity.equals("WARNING", true)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkTheme) Color(0xFF1C2636) else Color(0xFFFBFBFB)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(if (isDarkTheme) Color(0xFF2A3A52) else Color(0xFFE0E0E0))
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(if (isWarn) StatusDelayed.copy(alpha = 0.2f) else StatusGreen.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isWarn) Icons.Default.Warning else Icons.Default.Info,
                                            contentDescription = null,
                                            tint = if (isWarn) StatusDelayed else StatusGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = alert.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = alert.messageText,
                                style = MaterialTheme.typography.bodySmall.copy(color = contentColor, lineHeight = 18.sp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismissRequest,
                colors = ButtonDefaults.buttonColors(containerColor = SketchRed, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Fermer", fontWeight = FontWeight.Bold)
            }
        }
    )
}
