package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.example.ui.widget.IdelisWidgetHelper

/**
 * Transformation Coil qui supprime les éventuelles marges transparentes/blanches des PNG officiels
 * (ex: 470x486), recadre au centre en carré et applique un arrondi circulaire lisse de 30% sur les 4 angles
 * afin d'éviter les angles biseautés ou non arrondis.
 */
object IdelisLineIconTransformation : Transformation {
    override val cacheKey: String = "idelis_line_icon_round_30_v2"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val targetDim = maxOf(input.width, input.height).coerceIn(128, 256)
        return IdelisWidgetHelper.roundCornerSquareBitmap(
            source = input,
            targetSize = targetDim,
            cornerPercent = 0.30f
        )
    }
}

/**
 * Affiche l'icône officielle d'une ligne Idelis avec des angles uniformément arrondis à 30%
 * (sans effet biseauté), ou le badge coloré de repli si l'image n'est pas chargée.
 */
@Composable
fun IdelisLineIconBadge(
    lineRef: String,
    iconUrl: String?,
    routeColor: Color,
    routeTextColor: Color,
    size: Dp = 46.dp,
    contentDescription: String = "Icône officielle ligne $lineRef",
    modifier: Modifier = Modifier
) {
    val shape = remember { RoundedCornerShape(percent = 30) }
    var isImageLoaded by remember(iconUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (isImageLoaded) Color.Transparent else routeColor),
        contentAlignment = Alignment.Center
    ) {
        if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(iconUrl)
                    .transformations(IdelisLineIconTransformation)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
                contentScale = ContentScale.Crop,
                onSuccess = { isImageLoaded = true },
                onError = { isImageLoaded = false }
            )
        }

        if (iconUrl.isNullOrBlank() || !isImageLoaded) {
            val baseFont = if (lineRef.length > 3) size.value * 0.28f else size.value * 0.40f
            Text(
                text = lineRef,
                color = routeTextColor,
                fontWeight = FontWeight.Black,
                fontSize = baseFont.sp,
                maxLines = 1
            )
        }
    }
}
