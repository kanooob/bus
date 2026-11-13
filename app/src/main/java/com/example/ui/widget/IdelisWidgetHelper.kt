package com.example.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.data.model.DepartureComparison
import java.util.Locale

object IdelisWidgetHelper {

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

        // Fond avec coins arrondis
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val rect = RectF(2f, 2f, (width - 2).toFloat(), (height - 2).toFloat())
        canvas.drawRoundRect(rect, 14f, 14f, bgPaint)

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
     * Formate le temps d'attente restant de manière claire et concise.
     */
    fun formatWaitingTime(departure: DepartureComparison): String {
        val minutes = departure.minutesUntilDeparture
        return when {
            minutes <= 0L -> "À l'approche"
            minutes == 1L -> "Dans 1 min"
            minutes < 60L -> "Dans $minutes min"
            else -> departure.expectedDepartureTimeFormatted
                ?: departure.aimedDepartureTimeFormatted
        }
    }

    /**
     * Libellé d'état / ponctualité pour la ligne de sous-titre.
     */
    fun formatStatusSubtitle(departure: DepartureComparison): String {
        val timeStr = departure.expectedDepartureTimeFormatted ?: departure.aimedDepartureTimeFormatted
        return when {
            departure.delayMinutes > 0 -> "Prévu à $timeStr • Retard +${departure.delayMinutes} min"
            departure.delayMinutes < 0 -> "Prévu à $timeStr • Avance ${departure.delayMinutes} min"
            departure.isRealTime -> "Prévu à $timeStr • À l'heure"
            else -> "Prévu à $timeStr • À l'heure"
        }
    }
}
