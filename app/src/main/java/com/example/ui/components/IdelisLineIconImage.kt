package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.size.Size
import coil.transform.Transformation
import com.example.ui.widget.IdelisWidgetHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Transformation Coil conservée pour compatibilité : recadre au centre en carré
 * et applique un arrondi circulaire lisse de 30% sur les 4 angles.
 */
object IdelisLineIconTransformation : Transformation {
    override val cacheKey: String = "idelis_line_icon_round_30_v2"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val targetDim = maxOf(input.width, input.height).coerceIn(128, 192)
        return IdelisWidgetHelper.roundCornerSquareBitmap(
            source = input,
            targetSize = targetDim,
            cornerPercent = 0.30f
        )
    }
}

/**
 * Cache unique en RAM (`ImageBitmap`) + Disque PNG (`idelis_line_icons_png/`) avec déduplication
 * single-flight stricte par URL.
 *
 * Les ~32 icônes officielles du réseau Idelis ne sont ainsi téléchargées qu'une SEULE fois
 * (quel que soit le nombre de directions, de tailles d'affichage ou d'onglets).
 */
object IdelisLineIconCache {
    @Volatile
    private var sharedImageLoader: ImageLoader? = null

    @Volatile
    private var customHttpClient: OkHttpClient? = null

    private val defaultHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 2, TimeUnit.MINUTES))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private fun activeHttpClient(): OkHttpClient = customHttpClient ?: defaultHttpClient

    private val memoryImageBitmaps = ConcurrentHashMap<String, ImageBitmap>()
    private val inFlightLoads = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
    val loadedUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val prefetchedUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val diskWriteLocks = ConcurrentHashMap<String, Any>()

    private val iconScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preloadSemaphore = Semaphore(3)

    fun setHttpClientForTesting(client: OkHttpClient?) {
        customHttpClient = client
    }

    fun clearForTesting(context: Context? = null) {
        memoryImageBitmaps.clear()
        loadedUrls.clear()
        prefetchedUrls.clear()
        synchronized(inFlightLoads) {
            inFlightLoads.values.forEach { it.cancel() }
            inFlightLoads.clear()
        }
        sharedImageLoader = null
        context?.let {
            runCatching {
                File(it.applicationContext.cacheDir, "idelis_line_icons_png").deleteRecursively()
            }
        }
    }

    fun urlCacheFile(context: Context, url: String): File {
        val dir = File(context.applicationContext.cacheDir, "idelis_line_icons_png").apply {
            if (!exists()) mkdirs()
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$digest.png")
    }

    fun getCachedIconSync(iconUrl: String?): ImageBitmap? {
        val cleanUrl = iconUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return memoryImageBitmaps[cleanUrl]
    }

    suspend fun getOrLoadIcon(context: Context, iconUrl: String?): ImageBitmap? {
        val cleanUrl = iconUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        memoryImageBitmaps[cleanUrl]?.let { return it }

        val appCtx = context.applicationContext
        val deferred = synchronized(inFlightLoads) {
            memoryImageBitmaps[cleanUrl]?.let { return it }
            val existing = inFlightLoads[cleanUrl]
            if (existing != null && existing.isActive) {
                existing
            } else {
                iconScope.async {
                    try {
                        loadFromDiskOrNetwork(appCtx, cleanUrl)
                    } finally {
                        inFlightLoads.remove(cleanUrl)
                    }
                }.also { inFlightLoads[cleanUrl] = it }
            }
        }
        return deferred.await()
    }

    private suspend fun loadFromDiskOrNetwork(appContext: Context, cleanUrl: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            memoryImageBitmaps[cleanUrl]?.let { return@withContext it }

            val diskFile = urlCacheFile(appContext, cleanUrl)
            val lock = diskWriteLocks.getOrPut(cleanUrl) { Any() }

            // 1. Lecture depuis le cache PNG disque persistant (zéro réseau)
            synchronized(lock) {
                memoryImageBitmaps[cleanUrl]?.let { return@withContext it }
                if (diskFile.exists() && diskFile.length() > 0L) {
                    try {
                        val decodedDisk = BitmapFactory.decodeFile(diskFile.absolutePath)
                        if (decodedDisk != null) {
                            val imageBitmap = decodedDisk.asImageBitmap()
                            memoryImageBitmaps[cleanUrl] = imageBitmap
                            loadedUrls.add(cleanUrl)
                            prefetchedUrls.add(cleanUrl)
                            return@withContext imageBitmap
                        }
                    } catch (_: Exception) {}
                }
            }

            // 2. Téléchargement HTTP unique (une seule fois pour toute l'application)
            val bytes = try {
                val req = Request.Builder().url(cleanUrl).build()
                activeHttpClient().newCall(req).execute().use { res ->
                    if (!res.isSuccessful) null else res.body?.bytes()
                }
            } catch (_: Exception) {
                null
            } ?: return@withContext null

            val decoded = try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            } ?: return@withContext null

            val targetDim = maxOf(decoded.width, decoded.height).coerceIn(128, 192)
            val roundedBitmap = try {
                IdelisWidgetHelper.roundCornerSquareBitmap(
                    source = decoded,
                    targetSize = targetDim,
                    cornerPercent = 0.30f
                )
            } catch (_: Exception) {
                decoded
            }
            if (roundedBitmap !== decoded) {
                decoded.recycle()
            }

            synchronized(lock) {
                try {
                    val tmpFile = File(diskFile.parentFile, "${diskFile.name}.tmp")
                    FileOutputStream(tmpFile).use { out ->
                        roundedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    if (!tmpFile.renameTo(diskFile)) {
                        tmpFile.copyTo(diskFile, overwrite = true)
                        tmpFile.delete()
                    }
                } catch (_: Exception) {}

                val imageBitmap = roundedBitmap.asImageBitmap()
                memoryImageBitmaps[cleanUrl] = imageBitmap
                loadedUrls.add(cleanUrl)
                prefetchedUrls.add(cleanUrl)
                imageBitmap
            }
        }

    fun getImageLoader(context: Context): ImageLoader {
        sharedImageLoader?.let { return it }
        return synchronized(this) {
            sharedImageLoader ?: run {
                val appContext = context.applicationContext
                ImageLoader.Builder(appContext)
                    .okHttpClient { activeHttpClient() }
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
        val distinctUrls = urls.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        for (url in distinctUrls) {
            if (memoryImageBitmaps.containsKey(url) || !prefetchedUrls.add(url)) continue
            iconScope.launch {
                preloadSemaphore.withPermit {
                    val loaded = getOrLoadIcon(appCtx, url)
                    if (loaded == null) {
                        prefetchedUrls.remove(url)
                    }
                }
            }
        }
    }
}

/**
 * Affiche l'icône officielle d'une ligne Idelis avec des angles uniformément arrondis à 30%
 * directement depuis la RAM (`memoryImageBitmaps`) dès la première frame, ou le badge coloré de repli.
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
    val shape = remember { RoundedCornerShape(percent = 30) }
    val cleanUrl = iconUrl?.trim()?.takeIf { it.isNotBlank() }
    var iconBitmap by remember(cleanUrl) {
        mutableStateOf(IdelisLineIconCache.getCachedIconSync(cleanUrl))
    }

    LaunchedEffect(cleanUrl) {
        if (cleanUrl != null && iconBitmap == null) {
            iconBitmap = IdelisLineIconCache.getOrLoadIcon(context, cleanUrl)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (iconBitmap != null) Color.Transparent else routeColor),
        contentAlignment = Alignment.Center
    ) {
        val bmp = iconBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
                contentScale = ContentScale.Crop
            )
        } else {
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

/**
 * Affiche un picto de ligne (ex: alertes trafic) en réutilisant strictement le cache RAM/disque unique.
 */
@Composable
fun IdelisLinePictoImage(
    iconUrl: String?,
    contentDescription: String = "Picto ligne",
    size: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shape = remember { RoundedCornerShape(percent = 30) }
    val cleanUrl = iconUrl?.trim()?.takeIf { it.isNotBlank() }
    var iconBitmap by remember(cleanUrl) {
        mutableStateOf(IdelisLineIconCache.getCachedIconSync(cleanUrl))
    }

    LaunchedEffect(cleanUrl) {
        if (cleanUrl != null && iconBitmap == null) {
            iconBitmap = IdelisLineIconCache.getOrLoadIcon(context, cleanUrl)
        }
    }

    val bmp = iconBitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            modifier = modifier
                .size(size)
                .clip(shape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(Color.Gray.copy(alpha = 0.2f))
        )
    }
}
