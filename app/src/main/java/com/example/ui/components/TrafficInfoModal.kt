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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.ui.theme.StatusDelayed
import com.example.ui.theme.StatusGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val onSecondaryContainerColor = MaterialTheme.colorScheme.onSecondaryContainer
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val titleColor = MaterialTheme.colorScheme.onSurface
    val contentColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLow
    val cardBorderColor = MaterialTheme.colorScheme.outlineVariant

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

    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val isSheetExpanded = sheetState.targetValue == SheetValue.Expanded ||
        sheetState.currentValue == SheetValue.Expanded

    ModalBottomSheet(
        onDismissRequest = {
            stopHorn()
            onDismissRequest()
        },
        sheetState = sheetState,
        containerColor = bgColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = mutedColor.copy(alpha = 0.45f))
        },
        modifier = modifier.testTag("traffic_info_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
        ) {
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
                        tint = primaryColor,
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
                                color = primaryColor,
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

                    // Bouton Klaxon à gauche des contrôles (joue le son tant qu'on maintient appuyé)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isHornPressed) primaryColor else secondaryContainerColor
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
                            tint = if (isHornPressed) onPrimaryColor else onSecondaryContainerColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                if (isSheetExpanded) {
                                    sheetState.partialExpand()
                                } else {
                                    sheetState.expand()
                                }
                            }
                        },
                        modifier = Modifier.testTag("traffic_expand_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isSheetExpanded) {
                                Icons.Default.KeyboardArrowDown
                            } else {
                                Icons.Default.KeyboardArrowUp
                            },
                            contentDescription = if (isSheetExpanded) "Réduire" else "Agrandir vers le haut",
                            tint = mutedColor
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

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Perturbations en direct (${activePeriod.label}) :",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = mutedColor,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                if (isLoading && alerts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = primaryColor, strokeWidth = 3.dp)
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
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(12.dp)) {
                                Text("Réessayer", fontWeight = FontWeight.Bold, color = primaryColor)
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
                                containerColor = cardColor
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(cardBorderColor)
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
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = primaryColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Voir la déviation / le plan",
                                            color = primaryColor,
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
        }
    }
}
