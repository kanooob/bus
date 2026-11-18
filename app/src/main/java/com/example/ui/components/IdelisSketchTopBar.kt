package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Barre supérieure ergonomique Material 3 :
 * - À gauche : Sélecteur d'arrêt en pilule tactile (>= 48dp) avec icône bus + nom de l'arrêt + chevron.
 * - À droite : Accès rapides Info Trafic (avec badge si perturbations actives) et Paramètres.
 */
@Composable
fun IdelisSketchTopBar(
    stopName: String,
    onStopClick: () -> Unit,
    onTrafficInfoClick: () -> Unit,
    onSettingsClick: () -> Unit,
    trafficAlertCount: Int = 0,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val pillContainerColor = colorScheme.surfaceContainerLow
    val pillBorderColor = colorScheme.outlineVariant.copy(alpha = 0.65f)
    val hasActiveAlerts = trafficAlertCount > 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sélecteur d'arrêt principal en pilule tactile (>= 48dp)
        Surface(
            color = pillContainerColor,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, pillBorderColor),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onStopClick)
                .testTag("stop_header_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsBus,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stopName,
                    color = colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Choisir l'arrêt",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Info trafic (>= 48dp de cible tactile)
        Surface(
            color = if (hasActiveAlerts) colorScheme.tertiaryContainer.copy(alpha = 0.72f) else pillContainerColor,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                if (hasActiveAlerts) colorScheme.tertiary.copy(alpha = 0.45f) else pillBorderColor
            ),
            modifier = Modifier
                .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onTrafficInfoClick)
                .testTag("traffic_info_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Info trafic",
                        tint = if (hasActiveAlerts) colorScheme.onTertiaryContainer else colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    if (hasActiveAlerts) {
                        Badge(
                            containerColor = colorScheme.error,
                            contentColor = colorScheme.onError,
                            modifier = Modifier.offset(x = 8.dp, y = (-4).dp)
                        ) {
                            Text(
                                text = if (trafficAlertCount > 9) "9+" else trafficAlertCount.toString(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Info",
                    color = if (hasActiveAlerts) colorScheme.onTertiaryContainer else colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        // Paramètres (>= 48dp de cible tactile)
        Surface(
            color = pillContainerColor,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, pillBorderColor),
            modifier = Modifier
                .defaultMinSize(minWidth = 68.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onSettingsClick)
                .testTag("settings_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Paramètres",
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Paramètres",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}
