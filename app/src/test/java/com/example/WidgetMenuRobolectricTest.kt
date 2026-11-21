package com.example

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AppUpdateCheckFrequency
import com.example.data.model.DepartureComparison
import com.example.data.model.DepartureStatus
import com.example.data.model.GtfsSyncFrequency
import com.example.data.model.InAppRefreshFrequency
import com.example.data.model.PrimaryColorMode
import com.example.data.model.ThemeMode
import com.example.data.model.UpdateNetworkMode
import com.example.data.model.WidgetLocationFrequency
import com.example.data.model.WidgetRefreshFrequency
import com.example.ui.components.SettingsModal
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.widget.IdelisWidgetHelper
import com.example.ui.widget.IdelisWidgetPreferences
import com.example.ui.widget.WidgetColorMode
import com.example.ui.widget.WidgetConfig
import com.example.ui.widget.WidgetConfigureScreen
import com.example.ui.widget.WidgetDensityMode
import com.example.ui.widget.WidgetThemeStyle
import com.example.ui.widget.WidgetTimeDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetMenuRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `widget config persists theme, color mode, density, time format, toggles and line filters per widget`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val widgetId = 777

        val config = WidgetConfig(
            appWidgetId = widgetId,
            stopId = "ID_GARE",
            stopName = "Gare de Pau",
            stopDesc = "Quai A",
            isDynamicNearest = false,
            themeStyle = WidgetThemeStyle.TRANSLUCENT,
            colorMode = WidgetColorMode.MATERIAL_YOU,
            timeDisplayMode = WidgetTimeDisplayMode.BOTH,
            densityMode = WidgetDensityMode.COMPACT,
            showQuickActions = false,
            showFavoriteSwitch = false,
            filteredLines = setOf("F", "T1")
        )

        IdelisWidgetPreferences.saveWidgetConfig(context, config)
        val loaded = IdelisWidgetPreferences.getWidgetConfig(context, widgetId)

        assertEquals("ID_GARE", loaded.stopId)
        assertEquals("Gare de Pau", loaded.stopName)
        assertEquals(WidgetThemeStyle.TRANSLUCENT, loaded.themeStyle)
        assertEquals(WidgetColorMode.MATERIAL_YOU, loaded.colorMode)
        assertEquals(WidgetTimeDisplayMode.BOTH, loaded.timeDisplayMode)
        assertEquals(WidgetDensityMode.COMPACT, loaded.densityMode)
        assertFalse(loaded.showQuickActions)
        assertFalse(loaded.showFavoriteSwitch)
        assertEquals(setOf("F", "T1"), loaded.filteredLines)

        IdelisWidgetPreferences.deleteWidgetPrefs(context, intArrayOf(widgetId))
        val afterDelete = IdelisWidgetPreferences.getWidgetConfig(context, widgetId)
        assertEquals(WidgetThemeStyle.SYSTEM, afterDelete.themeStyle)
        assertEquals(WidgetColorMode.APP_DEFAULT, afterDelete.colorMode)
        assertTrue(afterDelete.filteredLines.isEmpty())
    }

    @Test
    fun `widget helper formats waiting time for countdown, clock and both modes`() {
        val dep = DepartureComparison(
            id = "d1",
            lineRef = "F",
            lineName = "Fébus",
            destinationName = "Gare de Pau",
            routeColorHex = "F18700",
            routeTextColorHex = "121212",
            aimedDepartureTimeFormatted = "14:30",
            expectedDepartureTimeFormatted = "14:32",
            delayMinutes = 2,
            status = DepartureStatus.DELAYED,
            isRealTime = true,
            minutesUntilDeparture = 4L,
            comparisonLabel = "14:30 -> 14:32"
        )

        assertEquals("Dans 4 min", IdelisWidgetHelper.formatWaitingTime(dep, WidgetTimeDisplayMode.COUNTDOWN))
        assertEquals("14:32", IdelisWidgetHelper.formatWaitingTime(dep, WidgetTimeDisplayMode.CLOCK))
        assertEquals("4 min • 14:32", IdelisWidgetHelper.formatWaitingTime(dep, WidgetTimeDisplayMode.BOTH))
    }

    @Test
    fun `upgraded widget layout inflates cleanly in RemoteViews`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val views = RemoteViews(context.packageName, R.layout.widget_idelis_departures)
        views.setImageViewResource(R.id.widget_icon_bus, R.drawable.ic_widget_gps)
        val inflated = views.apply(context, null)
        assertNotNull(inflated)
    }

    @Test
    fun `widget configure screen switches tabs, selects Material You and dynamic nearest without pin emoji`() {
        var confirmedConfig: WidgetConfig? = null

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                WidgetConfigureScreen(
                    appWidgetId = 42,
                    onConfigConfirmed = { cfg, _, _ -> confirmedConfig = cfg },
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("widget_tab_stop").assertIsDisplayed()
        composeTestRule.onNodeWithTag("widget_tab_style").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dynamic_nearest_widget_option").performClick()
        composeTestRule.waitForIdle()

        // Passer à l'onglet Apparence & Menu
        composeTestRule.onNodeWithTag("widget_tab_style").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("widget_style_menu").assertIsDisplayed()
        composeTestRule.onNodeWithTag("widget_style_menu").performScrollToIndex(1)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("widget_theme_LIGHT").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("widget_color_MATERIAL_YOU").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Valider la configuration
        composeTestRule.onNodeWithTag("confirm_widget_stop_button").performClick()
        composeTestRule.waitForIdle()

        assertNotNull(confirmedConfig)
        assertTrue(confirmedConfig?.isDynamicNearest == true)
        assertEquals(WidgetThemeStyle.LIGHT, confirmedConfig?.themeStyle)
        assertEquals(WidgetColorMode.MATERIAL_YOU, confirmedConfig?.colorMode)
        assertFalse(confirmedConfig?.stopName?.contains("📍") == true)
        assertFalse(confirmedConfig?.stopName?.equals("Arrêt le plus proche", ignoreCase = true) == true)
    }

    @Test
    fun `settings modal opens dedicated widgets menu`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                SettingsModal(
                    isDarkTheme = false,
                    themeMode = ThemeMode.SYSTEM,
                    onSelectThemeMode = {},
                    primaryColorMode = PrimaryColorMode.APP_DEFAULT,
                    onSelectPrimaryColorMode = {},
                    inAppRefreshFrequency = InAppRefreshFrequency.EVERY_1_MIN,
                    onSelectInAppRefreshFrequency = {},
                    widgetRefreshFrequency = WidgetRefreshFrequency.EVERY_15_MIN,
                    onSelectWidgetRefreshFrequency = {},
                    widgetLocationFrequency = WidgetLocationFrequency.EVERY_15_MIN,
                    onSelectWidgetLocationFrequency = {},
                    gtfsSyncFrequency = GtfsSyncFrequency.DAILY_ON_OPEN,
                    onSelectGtfsSyncFrequency = {},
                    appUpdateCheckFrequency = AppUpdateCheckFrequency.DAILY_ON_OPEN,
                    onSelectAppUpdateCheckFrequency = {},
                    updateNetworkMode = UpdateNetworkMode.WIFI_AND_CELLULAR,
                    onSelectUpdateNetworkMode = {},
                    onDismissRequest = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_categories_menu")
            .performScrollToNode(hasTestTag("settings_category_widgets"))
        composeTestRule.onNodeWithTag("settings_category_widgets").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pin_widget_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Widgets d'écran d'accueil").assertIsDisplayed()
    }

    @Test
    fun `line filter normalizes aliases and sorts Idelis network lines naturally`() {
        val sorted = IdelisWidgetPreferences.sortLinesNaturally(
            listOf("17", "10", "Coxitis", "5", "Fébus", "L06", "T4", "T1", "Ligne 13", "F", "11")
        )
        assertEquals(
            listOf("F", "T1", "T4", "5", "6", "10", "11", "13", "17", "COXI"),
            sorted
        )
    }

    @Test
    fun `widget configure line filter shows all network bus lines and toggles selection`() {
        var confirmedConfig: WidgetConfig? = null

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                WidgetConfigureScreen(
                    appWidgetId = 88,
                    onConfigConfirmed = { cfg, _, _ -> confirmedConfig = cfg },
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("widget_tab_style").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("widget_style_menu")
            .performScrollToNode(hasTestTag("widget_line_filter_17"))
        composeTestRule.onNodeWithTag("widget_line_filter_17").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("widget_line_filter_T4").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("widget_line_filter_COXI").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("confirm_widget_stop_button").performClick()
        composeTestRule.waitForIdle()

        assertNotNull(confirmedConfig)
        assertEquals(setOf("T4", "17", "COXI"), confirmedConfig?.filteredLines)
    }
}
