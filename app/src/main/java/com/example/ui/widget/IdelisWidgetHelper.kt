package com.example.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.data.model.DepartureComparison
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object IdelisWidgetHelper {

    private val iconBitmapCache = ConcurrentHashMap<String, Bitmap>()
    private val inFlightIconLocks = ConcurrentHashMap<String, Any>()
    private val iconClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * Supprime les éventuelles marges transparentes (ou fond blanc uniforme) d'un picto PNG (ex: 470x486),
     * recadre au centre en carré targetSize x targetSize puis applique un arrondi lisse à 30% sur les 4 angles.
     */
    fun roundCornerSquareBitmap(
        source: Bitmap,
        targetSize: Int = 96,
        cornerPercent: Float = 0.30f
    ): Bitmap {
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        val readable = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            source.config == Bitmap.Config.HARDWARE
        ) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }

        var cropLeft = 0
        var cropTop = 0
        var cropRight = w - 1
        var cropBottom = h - 1

        try {
            val pixels = IntArray(w * h)
            readable.getPixels(pixels, 0, w, 0, 0, w, h)

            fun isNearWhite(pixel: Int): Boolean {
                val a = (pixel ushr 24) and 0xFF
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF
                return a >= 240 && r >= 245 && g >= 245 && b >= 245
            }

            val stripWhiteMargin = isNearWhite(pixels[0]) &&
                isNearWhite(pixels[w - 1]) &&
                isNearWhite(pixels[(h - 1) * w]) &&
                isNearWhite(pixels[h * w - 1])

            var minX = w
            var minY = h
            var maxX = -1
            var maxY = -1

            for (y in 0 until h) {
                val rowOffset = y * w
                for (x in 0 until w) {
                    val p = pixels[rowOffset + x]
                    val alpha = (p ushr 24) and 0xFF
                    val isContent = alpha >= 160 && (!stripWhiteMargin || !isNearWhite(p))
                    if (isContent) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }

            val boxW = maxX - minX + 1
            val boxH = maxY - minY + 1
            if (minX <= maxX && minY <= maxY && boxW >= (w * 0.35f) && boxH >= (h * 0.35f)) {
                cropLeft = minX
                cropTop = minY
                cropRight = maxX
                cropBottom = maxY
            }
        } catch (_: Exception) {
            // Fallback sur les dimensions complètes si getPixels échoue
        }

        val trimmedW = (cropRight - cropLeft + 1).coerceAtLeast(1)
        val trimmedH = (cropBottom - cropTop + 1).coerceAtLeast(1)
        val minSide = minOf(trimmedW, trimmedH).coerceAtLeast(1)
        val squareX = cropLeft + ((trimmedW - minSide) / 2).coerceAtLeast(0)
        val squareY = cropTop + ((trimmedH - minSide) / 2).coerceAtLeast(0)

        val squareCrop = if (squareX == 0 && squareY == 0 && minSide == w && minSide == h) {
            readable
        } else {
            Bitmap.createBitmap(readable, squareX, squareY, minSide, minSide)
        }

        val scaledSquare = if (squareCrop.width != targetSize || squareCrop.height != targetSize) {
            Bitmap.createScaledBitmap(squareCrop, targetSize, targetSize, true)
        } else {
            squareCrop
        }

        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val shader = BitmapShader(scaledSquare, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
        }
        val radius = targetSize * cornerPercent
        canvas.drawRoundRect(
            RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat()),
            radius,
            radius,
            paint
        )

        if (scaledSquare !== squareCrop && scaledSquare !== readable) scaledSquare.recycle()
        if (squareCrop !== readable) squareCrop.recycle()
        if (readable !== source) readable.recycle()
        return output
    }

    private fun urlCacheFile(context: Context, url: String): File {
        val dir = File(context.applicationContext.cacheDir, "widget_line_icons").apply {
            if (!exists()) mkdirs()
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$digest.png")
    }

    fun getCachedLineIconBitmap(iconUrl: String?): Bitmap? {
        val url = iconUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return iconBitmapCache[url]?.takeIf { !it.isRecycled }
    }

    /**
     * Télécharge et met en cache (RAM + disque si context fourni) l'icône PNG officielle de la ligne
     * recadrée en carré 96x96 avec angles arrondis à 30% pour le widget, avec déduplication par URL.
     */
    @JvmOverloads
    fun loadLineIconBitmap(iconUrl: String?, context: Context? = null): Bitmap? {
        val url = iconUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        iconBitmapCache[url]?.let { cached ->
            if (!cached.isRecycled) return cached
        }

        val lock = inFlightIconLocks.getOrPut(url) { Any() }
        return synchronized(lock) {
            iconBitmapCache[url]?.let { cached ->
                if (!cached.isRecycled) return@synchronized cached
            }

            if (context != null) {
                try {
                    val diskFile = urlCacheFile(context, url)
                    if (diskFile.exists() && diskFile.length() > 0L) {
                        val fromDisk = BitmapFactory.decodeFile(diskFile.absolutePath)
                        if (fromDisk != null) {
                            iconBitmapCache[url] = fromDisk
                            return@synchronized fromDisk
                        }
                    }
                    val sharedAppFile = com.example.ui.components.IdelisLineIconCache.urlCacheFile(context, url)
                    if (sharedAppFile.exists() && sharedAppFile.length() > 0L) {
                        val fromAppDisk = BitmapFactory.decodeFile(sharedAppFile.absolutePath)
                        if (fromAppDisk != null) {
                            val scaled = if (fromAppDisk.width == 96 && fromAppDisk.height == 96) {
                                fromAppDisk
                            } else {
                                Bitmap.createScaledBitmap(fromAppDisk, 96, 96, true).also {
                                    fromAppDisk.recycle()
                                }
                            }
                            iconBitmapCache[url] = scaled
                            try {
                                FileOutputStream(diskFile).use { out ->
                                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                            } catch (_: Exception) {}
                            return@synchronized scaled
                        }
                    }
                } catch (_: Exception) {}
            }

            try {
                val req = Request.Builder().url(url).build()
                val bytes = iconClient.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@synchronized null
                    res.body?.bytes()
                } ?: return@synchronized null

                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@synchronized null
                val finalBitmap = roundCornerSquareBitmap(decoded, targetSize = 96, cornerPercent = 0.30f)
                decoded.recycle()
                iconBitmapCache[url] = finalBitmap

                if (context != null) {
                    try {
                        val diskFile = urlCacheFile(context, url)
                        FileOutputStream(diskFile).use { out ->
                            finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    } catch (_: Exception) {}
                }
                finalBitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Génère une icône bitmap dynamique nette pour la pastille de ligne Idelis (F, T1, 5, etc.)
     * avec la couleur officielle de la ligne et le texte centré.
     */
    fun createLineBadgeBitmap(
        lineRef: String,
        routeColorHex: String,
        routeTextColorHex: String
    ): Bitmap {
        val width = 96
        val height = 60
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cleanBg = routeColorHex.removePrefix("#")
        val bgColor = try {
            Color.parseColor("#$cleanBg")
        } catch (_: Exception) {
            Color.parseColor("#0288D1")
        }

        val cleanText = routeTextColorHex.removePrefix("#")
        val txtColor = try {
            Color.parseColor("#$cleanText")
        } catch (_: Exception) {
            Color.WHITE
        }

        // Fond avec coins arrondis (~30%)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val rect = RectF(2f, 2f, (width - 2).toFloat(), (height - 2).toFloat())
        val cornerRadius = height * 0.30f
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        // Texte de la ligne centré
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = txtColor
            textSize = if (lineRef.length > 3) 24f else 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val yPos = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(lineRef.take(5), width / 2f, yPos, textPaint)

        return bitmap
    }

    /**
     * La pastille horaire passe en rouge uniquement lorsque le bus est en temps réel
     * et arrive dans moins de 2 minutes (0 ou 1 min), jamais à cause d'un simple retard.
     */
    fun isRedImminentPill(departure: DepartureComparison): Boolean {
        return departure.isRealTime && departure.minutesUntilDeparture <= 1L
    }

    /**
     * Formate le temps d'attente restant de manière claire et concise.
     */
    fun formatWaitingTime(departure: DepartureComparison): String {
        return formatWaitingTime(departure, WidgetTimeDisplayMode.COUNTDOWN)
    }

    /**
     * Formate le temps d'attente selon le mode choisi par l'utilisateur pour ce widget.
     */
    fun formatWaitingTime(
        departure: DepartureComparison,
        mode: WidgetTimeDisplayMode
    ): String {
        val minutes = departure.minutesUntilDeparture
        val clockStr = departure.expectedDepartureTimeFormatted
            ?.takeIf { it.isNotBlank() }
            ?: departure.aimedDepartureTimeFormatted

        return when (mode) {
            WidgetTimeDisplayMode.COUNTDOWN -> when {
                minutes <= 0L -> "À l'approche"
                minutes == 1L -> "Dans 1 min"
                minutes < 60L -> "Dans $minutes min"
                else -> clockStr
            }
            WidgetTimeDisplayMode.CLOCK -> clockStr
            WidgetTimeDisplayMode.BOTH -> when {
                minutes <= 0L -> "Imminent • $clockStr"
                minutes < 60L -> "${minutes} min • $clockStr"
                else -> clockStr
            }
        }
    }

    /**
     * Libellé d'état / ponctualité pour la ligne de sous-titre.
     */
    fun formatStatusSubtitle(departure: DepartureComparison): String {
        val timeStr = departure.expectedDepartureTimeFormatted ?: departure.aimedDepartureTimeFormatted
        return when {
            !departure.isRealTime -> "Horaire théorique • $timeStr"
            departure.delayMinutes > 0 -> "Prévu à $timeStr • Retard +${departure.delayMinutes} min"
            departure.delayMinutes < 0 -> "Prévu à $timeStr • Avance ${departure.delayMinutes} min"
            else -> "Prévu à $timeStr • À l'heure"
        }
    }
}
