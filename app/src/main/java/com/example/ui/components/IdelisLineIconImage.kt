package com.example.ui.components

import android.content.Context
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
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.example.ui.widget.IdelisWidgetHelper
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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
 * Cache Coil partagé (RAM + Disque persistant) dédié aux icônes officielles des lignes Idelis.
 * Ignore les en-têtes HTTP de révalidation pour éviter de re-télécharger les mêmes PNG à chaque écran.
 */
object IdelisLineIconCache {
    @Volatile
    private var sharedImageLoader: ImageLoader? = null

    val loadedUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val prefetchedUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun getImageLoader(context: Context): ImageLoader {
        sharedImageLoader?.let { return it }
        return synchronized(this) {
            sharedImageLoader ?: run {
                val appContext = context.applicationContext
                ImageLoader.Builder(appContext)
                    .memoryCache {
                        MemoryCache.Builder(appContext)
                            .maxSizePercent(0.25)
                            .build()
                    }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(File(appContext.cacheDir, "idelis_line_icons_coil"))
                            .maxSizeBytes(20L * 1024L * 1024L)
                            .build()
                    }
                    .respectCacheHeaders(false)
                    .build()
                    .also { sharedImageLoader = it }
            }
        }
    }

    fun preloadIcons(context: Context?, urls: Collection<String>) {
        val appCtx = context?.applicationContext ?: return
        val loader = getImageLoader(appCtx)
        for (raw in urls) {
            val url = raw.trim()
            if (url.isBlank() || !prefetchedUrls.add(url)) continue
            val request = ImageRequest.Builder(appCtx)
                .data(url)
                .transformations(IdelisLineIconTransformation)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .target(
                    onSuccess = { loadedUrls.add(url) },
                    onError = { prefetchedUrls.remove(url) }
                )
                .build()
            loader.enqueue(request)
        }
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
    val context = LocalContext.current
    val imageLoader = remember(context) { IdelisLineIconCache.getImageLoader(context) }
    val shape = remember { RoundedCornerShape(percent = 30) }
    val cleanUrl = iconUrl?.trim()?.takeIf { it.isNotBlank() }
    var isImageLoaded by remember(cleanUrl) {
        mutableStateOf(cleanUrl != null && IdelisLineIconCache.loadedUrls.contains(cleanUrl))
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (isImageLoaded) Color.Transparent else routeColor),
        contentAlignment = Alignment.Center
    ) {
        if (cleanUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(cleanUrl)
                    .transformations(IdelisLineIconTransformation)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(!isImageLoaded)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
                contentScale = ContentScale.Crop,
                onSuccess = {
                    IdelisLineIconCache.loadedUrls.add(cleanUrl)
                    isImageLoaded = true
                },
                onError = {
                    IdelisLineIconCache.loadedUrls.remove(cleanUrl)
                    isImageLoaded = false
                }
            )
        }

        if (cleanUrl == null || !isImageLoaded) {
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
