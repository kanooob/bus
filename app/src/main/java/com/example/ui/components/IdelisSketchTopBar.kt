package com.example.ui.components

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * Barre supérieure épurée :
 * - À gauche : Nom de l'arrêt + chevron pour ouvrir le sélecteur d'arrêt.
 * - À droite : Accès rapides Info Trafic (pastille si alertes non lues) et Paramètres.
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
    val hasUnreadAlerts = trafficAlertCount > 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sélecteur d'arrêt (>= 48dp de cible tactile)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onStopClick)
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .testTag("stop_header_button")
        ) {
            Text(
                text = stopName,
                color = colorScheme.onSurface,
                fontSize = 18.sp,
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

        // Info trafic (>= 48dp de cible tactile)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onTrafficInfoClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("traffic_info_button")
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Info trafic",
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                if (hasUnreadAlerts) {
                    Badge(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
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
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        // Paramètres (>= 48dp de cible tactile)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSettingsClick)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("settings_button")
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
