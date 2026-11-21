package com.example.ui.widget

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DepartureComparison
import com.example.ui.components.IdelisLineIconBadge
import java.util.Locale

/**
 * Aperçu fidèle en temps réel du widget d'écran d'accueil dans l'application et le menu de configuration.
 */
@Composable
fun WidgetLivePreviewCard(
    config: WidgetConfig,
    departures: List<DepartureComparison>,
    favoriteCount: Int = 0,
    isLoading: Boolean = false,
    resolvedStopName: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val effectiveTheme = when (config.themeStyle) {
        WidgetThemeStyle.SYSTEM -> if (systemDark) WidgetThemeStyle.DARK else WidgetThemeStyle.LIGHT
        else -> config.themeStyle
    }
    val useMaterialYou = config.colorMode == WidgetColorMode.MATERIAL_YOU &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicScheme = if (useMaterialYou) {
        if (effectiveTheme == WidgetThemeStyle.LIGHT) {
            dynamicLightColorScheme(context)
        } else {
            dynamicDarkColorScheme(context)
        }
    } else null

    val containerBg = when {
        effectiveTheme == WidgetThemeStyle.TRANSLUCENT -> Color(0xCC0F172A)
        dynamicScheme != null -> dynamicScheme.surfaceContainerLow
        effectiveTheme == WidgetThemeStyle.LIGHT -> Color.White
        else -> Color(0xFF141B26)
    }
    val containerBorder = when {
        effectiveTheme == WidgetThemeStyle.TRANSLUCENT -> Color(0x3DFFFFFF)
        dynamicScheme != null -> dynamicScheme.outlineVariant
        effectiveTheme == WidgetThemeStyle.LIGHT -> Color(0xFFE2E8F0)
        else -> Color(0xFF2A3A52)
    }
    val rowBg = when {
        effectiveTheme == WidgetThemeStyle.TRANSLUCENT -> Color(0x26FFFFFF)
        dynamicScheme != null -> dynamicScheme.surfaceContainerHigh
        effectiveTheme == WidgetThemeStyle.LIGHT -> Color(0xFFF1F5F9)
        else -> Color(0xFF1E293B)
    }
    val actionBg = when {
        effectiveTheme == WidgetThemeStyle.TRANSLUCENT -> Color(0x33FFFFFF)
        dynamicScheme != null -> dynamicScheme.secondaryContainer
        effectiveTheme == WidgetThemeStyle.LIGHT -> Color(0xFFE2E8F0)
        else -> Color(0xFF243144)
    }
    val badgeBg = if (dynamicScheme != null) dynamicScheme.primary else Color(0xFF8C935D)
    val badgeIconTint = if (dynamicScheme != null) dynamicScheme.onPrimary else Color.White
    val normalPillColor = if (dynamicScheme != null) dynamicScheme.primary else Color(0xFF6B7342)
    val normalPillTextColor = if (dynamicScheme != null) dynamicScheme.onPrimary else Color.White

    val primaryText = when {
        effectiveTheme == WidgetThemeStyle.TRANSLUCENT -> Color.White
        dynamicScheme != null -> dynamicScheme.onSurface
        effectiveTheme == WidgetThemeStyle.LIGHT -> Color(0xFF0F172A)
        else -> Color.White
    }
    val secondaryText = when {
        effectiveTheme == WidgetThemeStyle.TRANSLUCENT -> Color(0xFFCBD5E1)
        dynamicScheme != null -> dynamicScheme.onSurfaceVariant
        effectiveTheme == WidgetThemeStyle.LIGHT -> Color(0xFF64748B)
        else -> Color(0xFF94A3B8)
    }
    val dividerColor = when {
        effectiveTheme == WidgetThemeStyle.TRANSLUCENT -> Color(0x33FFFFFF)
        dynamicScheme != null -> dynamicScheme.outlineVariant
        effectiveTheme == WidgetThemeStyle.LIGHT -> Color(0xFFE2E8F0)
        else -> Color(0xFF2A3A52)
    }
    val actionTint = when {
        effectiveTheme == WidgetThemeStyle.TRANSLUCENT -> if (dynamicScheme != null) dynamicScheme.primary else Color.White
        dynamicScheme != null -> dynamicScheme.onSecondaryContainer
        effectiveTheme == WidgetThemeStyle.LIGHT -> Color(0xFF334155)
        else -> Color(0xFFE2E8F0)
    }
    val onTimeColor = if (effectiveTheme == WidgetThemeStyle.LIGHT) Color(0xFF15803D) else Color(0xFF4ADE80)
    val delayedColor = if (effectiveTheme == WidgetThemeStyle.LIGHT) Color(0xFFDC2626) else Color(0xFFF87171)
    val earlyColor = if (effectiveTheme == WidgetThemeStyle.LIGHT) Color(0xFF0288D1) else Color(0xFF38BDF8)

    val displayedStopName = (resolvedStopName ?: config.stopName)
        .replace("📍", "")
        .trim()
        .takeIf { it.isNotBlank() && !it.equals("Arrêt le plus proche", ignoreCase = true) }
        ?: "Pôle Bosquet"

    val sortedFilteredLines = IdelisWidgetPreferences.sortLinesNaturally(config.filteredLines)
    val filtered = if (sortedFilteredLines.isNotEmpty()) {
        val allowed = sortedFilteredLines.toSet()
        departures.filter { IdelisWidgetPreferences.normalizeLineCode(it.lineRef) in allowed }
    } else {
        departures
    }
    val maxPreviewRows = if (config.densityMode == WidgetDensityMode.COMPACT) 4 else 3
    val visibleDepartures = filtered.take(maxPreviewRows)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(containerBg)
            .border(1.dp, containerBorder, RoundedCornerShape(24.dp))
            .padding(12.dp)
    ) {
        // En-tête du widget
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (config.isDynamicNearest) Icons.Default.LocationOn else Icons.Default.DirectionsBus,
                    contentDescription = if (config.isDynamicNearest) "Arrêt le plus proche (GPS)" else "Idelis Bus",
                    tint = badgeIconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayedStopName,
                    color = primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val filterText = if (sortedFilteredLines.isNotEmpty()) {
                        " • ${sortedFilteredLines.take(3).joinToString(",")}"
                    } else ""
                    Text(
                        text = "Temps réel • Aperçu$filterText",
                        color = secondaryText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val showFavPill = config.showFavoriteSwitch && favoriteCount > 0
            if (showFavPill || config.showRefreshButton || config.showMenuButton) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showFavPill) {
                        PreviewActionPill(
                            icon = Icons.Default.SwapHoriz,
                            bg = actionBg,
                            tint = actionTint
                        )
                    }
                    if (config.showRefreshButton) {
                        PreviewActionPill(
                            icon = Icons.Default.Refresh,
                            bg = actionBg,
                            tint = actionTint
                        )
                    }
                    if (config.showMenuButton) {
                        PreviewActionPill(
                            icon = Icons.Default.Tune,
                            bg = actionBg,
                            tint = actionTint
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = dividerColor, thickness = 1.dp)
        Spacer(modifier = Modifier.height(7.dp))

        if (isLoading) {
            Text(
                text = "Chargement des prochains départs...",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else if (visibleDepartures.isEmpty()) {
            Text(
                text = if (sortedFilteredLines.isNotEmpty() && departures.isNotEmpty()) {
                    "Aucun départ pour les lignes ${sortedFilteredLines.joinToString(", ")}"
                } else {
                    "Aucun départ immédiat pour cet arrêt"
                },
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                visibleDepartures.forEach { dep ->
                    val routeColor = try {
                        Color(android.graphics.Color.parseColor("#" + dep.routeColorHex.removePrefix("#")))
                    } catch (_: Exception) {
                        Color(0xFF0288D1)
                    }
                    val routeTextColor = try {
                        Color(android.graphics.Color.parseColor("#" + dep.routeTextColorHex.removePrefix("#")))
                    } catch (_: Exception) {
                        Color.White
                    }
                    val pillColor = when {
                        !dep.isRealTime -> Color(0xFF475569)
                        dep.minutesUntilDeparture <= 1L -> Color(0xFFDC2626)
                        else -> normalPillColor
                    }
                    val pillTextColor = when {
                        !dep.isRealTime || dep.minutesUntilDeparture <= 1L -> Color.White
                        else -> normalPillTextColor
                    }
                    val delayColor = when {
                        !dep.isRealTime -> secondaryText
                        dep.delayMinutes > 0 -> delayedColor
                        dep.delayMinutes < 0 -> earlyColor
                        else -> onTimeColor
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(rowBg)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        IdelisLineIconBadge(
                            lineRef = dep.lineRef,
                            iconUrl = dep.iconUrl,
                            routeColor = routeColor,
                            routeTextColor = routeTextColor,
                            size = 28.dp,
                            contentDescription = "Icône ligne ${dep.lineRef}"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dep.destinationName,
                                color = primaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (config.densityMode == WidgetDensityMode.STANDARD) {
                                Text(
                                    text = IdelisWidgetHelper.formatStatusSubtitle(dep),
                                    color = delayColor,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(pillColor)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = IdelisWidgetHelper.formatWaitingTime(dep, config.timeDisplayMode),
                                color = pillTextColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bg: Color,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}
