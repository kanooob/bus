package com.example.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.R

/**
 * Service fournissant la liste déroulante des départs du widget sans limite fixe de 8 lignes.
 * Chaque élément est dimensionné individuellement à sa hauteur naturelle pour éviter toute découpe ou déformation.
 */
class IdelisWidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return IdelisWidgetRemoteViewsFactory(applicationContext, appWidgetId)
    }
}

internal class IdelisWidgetRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var snapshot = WidgetDepartureSnapshot(stopId = "", departures = emptyList())
    private var config = WidgetConfig(appWidgetId = appWidgetId)
    private var palette = IdelisAppWidgetProvider.resolvePalette(
        context = context,
        themeStyle = WidgetThemeStyle.SYSTEM,
        colorMode = WidgetColorMode.APP_DEFAULT
    )

    override fun onCreate() {
        reloadFromPreferences()
    }

    override fun onDataSetChanged() {
        reloadFromPreferences()
    }

    private fun reloadFromPreferences() {
        config = IdelisWidgetPreferences.getWidgetConfig(context, appWidgetId)
        palette = IdelisAppWidgetProvider.resolvePalette(
            context = context,
            themeStyle = config.themeStyle,
            colorMode = config.colorMode
        )
        snapshot = IdelisWidgetPreferences.getWidgetDeparturesSnapshot(context, appWidgetId)
    }

    override fun onDestroy() {
        snapshot = WidgetDepartureSnapshot(stopId = "", departures = emptyList())
    }

    override fun getCount(): Int = snapshot.departures.size

    override fun getViewAt(position: Int): RemoteViews? {
        val dep = snapshot.departures.getOrNull(position) ?: return null
        val rowViews = RemoteViews(context.packageName, R.layout.widget_departure_item)

        rowViews.setInt(R.id.widget_row_item, "setBackgroundResource", palette.rowBgRes)
        rowViews.setTextColor(R.id.row_item_destination, palette.primaryTextColor)

        val badgeBitmap = IdelisWidgetHelper.loadLineIconBitmap(dep.iconUrl, context)
            ?: IdelisWidgetHelper.createLineBadgeBitmap(
                lineRef = dep.lineRef,
                routeColorHex = dep.routeColorHex,
                routeTextColorHex = dep.routeTextColorHex
            )
        rowViews.setImageViewBitmap(R.id.row_item_badge, badgeBitmap)
        rowViews.setTextViewText(R.id.row_item_destination, dep.destinationName)

        val isCompact = config.densityMode == WidgetDensityMode.COMPACT
        if (isCompact) {
            rowViews.setViewVisibility(R.id.row_item_delay, View.GONE)
        } else {
            rowViews.setViewVisibility(R.id.row_item_delay, View.VISIBLE)
            rowViews.setTextViewText(R.id.row_item_delay, IdelisWidgetHelper.formatStatusSubtitle(dep))
            val delayColor = when {
                !dep.isRealTime -> palette.secondaryTextColor
                dep.delayMinutes > 0 -> palette.delayedColor
                dep.delayMinutes < 0 -> palette.earlyColor
                else -> palette.onTimeColor
            }
            rowViews.setTextColor(R.id.row_item_delay, delayColor)
        }

        rowViews.setTextViewText(
            R.id.row_item_time,
            IdelisWidgetHelper.formatWaitingTime(dep, config.timeDisplayMode)
        )
        val pillBg = when {
            !dep.isRealTime -> R.drawable.widget_time_pill_theoretical
            dep.minutesUntilDeparture <= 1L -> R.drawable.widget_time_pill_imminent
            else -> palette.normalTimePillBgRes
        }
        rowViews.setInt(R.id.row_item_time, "setBackgroundResource", pillBg)

        val fillInIntent = Intent().apply {
            putExtra(IdelisAppWidgetProvider.EXTRA_STOP_ID, snapshot.stopId.ifBlank { config.stopId })
        }
        rowViews.setOnClickFillInIntent(R.id.widget_row_item, fillInIntent)
        return rowViews
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
