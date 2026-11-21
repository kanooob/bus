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
import com.example.data.local.IdelisDatabase
import com.example.data.model.AppUpdateCheckFrequency
import com.example.data.model.DepartureComparison
import com.example.data.model.DepartureStatus
import com.example.data.model.GtfsStop
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
import kotlinx.coroutines.runBlocking
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
            showRefreshButton = false,
            showMenuButton = true,
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
        assertFalse(loaded.showRefreshButton)
        assertTrue(loaded.showMenuButton)
        assertFalse(loaded.showFavoriteSwitch)
        assertEquals(setOf("F", "T1"), loaded.filteredLines)

        IdelisWidgetPreferences.deleteWidgetPrefs(context, intArrayOf(widgetId))
        val afterDelete = IdelisWidgetPreferences.getWidgetConfig(context, widgetId)
        assertEquals(WidgetThemeStyle.SYSTEM, afterDelete.themeStyle)
        assertEquals(WidgetColorMode.APP_DEFAULT, afterDelete.colorMode)
        assertTrue(afterDelete.showRefreshButton)
        assertTrue(afterDelete.showMenuButton)
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
    fun `widget red time pill activates only for realtime arrival under 2 minutes and not for delayed buses further away`() {
        val base = DepartureComparison(
            id = "d_base",
            lineRef = "F",
            lineName = "Fébus",
            destinationName = "Gare de Pau",
            routeColorHex = "F18700",
            routeTextColorHex = "121212",
            aimedDepartureTimeFormatted = "14:30",
            expectedDepartureTimeFormatted = "14:35",
            delayMinutes = 5,
            status = DepartureStatus.DELAYED,
            isRealTime = true,
            minutesUntilDeparture = 8L,
            comparisonLabel = "14:30 -> 14:35"
        )

        // Retardé mais à 8 min -> ne doit PAS être rouge
        assertFalse(IdelisWidgetHelper.isRedImminentPill(base))
        // À 2 min -> ne doit PAS être rouge (< 1/2 min)
        assertFalse(IdelisWidgetHelper.isRedImminentPill(base.copy(minutesUntilDeparture = 2L)))
        // Théorique même à 1 min -> ne doit PAS être rouge
        assertFalse(IdelisWidgetHelper.isRedImminentPill(base.copy(isRealTime = false, minutesUntilDeparture = 1L)))

        // Temps réel à 1 min ou 0 min (même à l'heure) -> DOIT être rouge
        assertTrue(IdelisWidgetHelper.isRedImminentPill(base.copy(delayMinutes = 0, status = DepartureStatus.ON_TIME, minutesUntilDeparture = 1L)))
        assertTrue(IdelisWidgetHelper.isRedImminentPill(base.copy(delayMinutes = 0, status = DepartureStatus.ON_TIME, minutesUntilDeparture = 0L)))
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

    @Test
    fun `commercial line validator excludes raw GTFS route ids and school codes`() {
        assertTrue(IdelisWidgetPreferences.isValidCommercialLineCode("F"))
        assertTrue(IdelisWidgetPreferences.isValidCommercialLineCode("T1"))
        assertTrue(IdelisWidgetPreferences.isValidCommercialLineCode("5"))
        assertTrue(IdelisWidgetPreferences.isValidCommercialLineCode("17"))
        assertTrue(IdelisWidgetPreferences.isValidCommercialLineCode("COXI"))

        assertFalse(IdelisWidgetPreferences.isValidCommercialLineCode("1"))
        assertFalse(IdelisWidgetPreferences.isValidCommercialLineCode("4"))
        assertFalse(IdelisWidgetPreferences.isValidCommercialLineCode("101"))
        assertFalse(IdelisWidgetPreferences.isValidCommercialLineCode("S12"))
        assertFalse(IdelisWidgetPreferences.isValidCommercialLineCode("FLEX1"))
        assertFalse(IdelisWidgetPreferences.isValidCommercialLineCode("DESSERVIES"))
    }

    @Test
    fun `widget configure preserves line filter when switching between favorite, fixed and nearest stops`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.local.PreferencesManager(context)
        runBlocking {
            IdelisDatabase.getInstance(context).stopDao().insertStops(
                listOf(
                    GtfsStop("ID_GARE", "Gare de Pau", "Correspondance SNCF • Lignes F, T1, 5, 9", 43.2928, -0.3705),
                    GtfsStop("ID_BOSQUET", "Pôle Bosquet", "Pôle central • Lignes F, T1, T2, T3, T4", 43.2951, -0.3698)
                )
            )
        }
        prefs.saveFavoriteStopIds(setOf("ID_GARE"))
        IdelisWidgetPreferences.saveWidgetConfig(
            context,
            WidgetConfig(
                appWidgetId = 99,
                stopId = "ID_BOSQUET",
                stopName = "Pôle Bosquet",
                filteredLines = setOf("F", "T1")
            )
        )

        var confirmedConfig: WidgetConfig? = null
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                WidgetConfigureScreen(
                    appWidgetId = 99,
                    onConfigConfirmed = { cfg, _, _ -> confirmedConfig = cfg },
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeTestRule.onNodeWithTag("widget_stop_list")
                    .performScrollToNode(hasTestTag("fav_stop_item_ID_GARE"))
                true
            }.getOrDefault(false)
        }
        // Basculer sur l'arrêt favori Gare de Pau
        composeTestRule.onNodeWithTag("fav_stop_item_ID_GARE").performClick()
        composeTestRule.waitForIdle()
        // Puis basculer sur Arrêt le plus proche (GPS)
        composeTestRule.onNodeWithTag("widget_stop_list")
            .performScrollToNode(hasTestTag("dynamic_nearest_widget_option"))
        composeTestRule.onNodeWithTag("dynamic_nearest_widget_option").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("confirm_widget_stop_button").performClick()
        composeTestRule.waitForIdle()

        assertNotNull(confirmedConfig)
        assertTrue(confirmedConfig?.isDynamicNearest == true)
        assertEquals(setOf("F", "T1"), confirmedConfig?.filteredLines)
    }

    @Test
    fun `widget time pill has horizontal padding and minimum dimensions on static 16-row layout without ListView`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val widgetViews = RemoteViews(context.packageName, R.layout.widget_idelis_departures)
        val inflatedWidget = widgetViews.apply(context, null)

        val row1 = inflatedWidget.findViewById<android.view.View>(R.id.widget_row_1)
        val row16 = inflatedWidget.findViewById<android.view.View>(R.id.widget_row_16)
        assertNotNull(row1)
        assertNotNull(row16)

        val pill = inflatedWidget.findViewById<android.widget.TextView>(R.id.row_1_time)
        assertNotNull(pill)
        assertTrue(pill.paddingStart > 0)
        assertTrue(pill.paddingEnd > 0)
        assertTrue(pill.minimumWidth > 0)
    }

    @Test
    fun `widget cycle order includes selected stop when 2 or more favorites exist`() {
        // Arrêt choisi manuellement hors favoris + 2 favoris -> doit revenir sur l'arrêt choisi
        val fixedWithTwoFavs = IdelisWidgetPreferences.buildStopCycleOrder(
            homeStopId = "ID_UNIV",
            homeIsDynamicNearest = false,
            favoriteStopIds = listOf("ID_GARE", "ID_BOSQUET")
        )
        assertEquals(listOf("ID_UNIV", "ID_BOSQUET", "ID_GARE"), fixedWithTwoFavs)

        // Mode Arrêt le plus proche (GPS) + 2 favoris -> doit revenir sur GPS proche
        val nearestWithTwoFavs = IdelisWidgetPreferences.buildStopCycleOrder(
            homeStopId = IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID,
            homeIsDynamicNearest = true,
            favoriteStopIds = listOf("ID_GARE", "ID_BOSQUET")
        )
        assertEquals(
            listOf(IdelisWidgetPreferences.DYNAMIC_NEAREST_STOP_ID, "ID_BOSQUET", "ID_GARE"),
            nearestWithTwoFavs
        )

        // L'arrêt choisi est déjà un des 2 favoris -> pas de doublon
        val favHomeWithTwoFavs = IdelisWidgetPreferences.buildStopCycleOrder(
            homeStopId = "ID_GARE",
            homeIsDynamicNearest = false,
            favoriteStopIds = listOf("ID_GARE", "ID_BOSQUET")
        )
        assertEquals(listOf("ID_GARE", "ID_BOSQUET"), favHomeWithTwoFavs)
    }

    @Test
    fun `widget height calculation scales smoothly from compact 1-cell shrink up to 16 complete rows without clipping`() {
        // Rétréci au minimum (~50dp -> 0 ligne de départ, en-tête seul sans ligne coupée)
        assertEquals(
            0,
            com.example.ui.widget.IdelisAppWidgetProvider.calculateMaxRowsForHeight(
                heightDp = 50f,
                isCompact = false,
                fontScale = 1.0f
            )
        )
        // Taille réduite standard (120dp -> 1 ligne complète)
        assertEquals(
            1,
            com.example.ui.widget.IdelisAppWidgetProvider.calculateMaxRowsForHeight(
                heightDp = 120f,
                isCompact = false,
                fontScale = 1.0f
            )
        )
        // Mode compact permet davantage de lignes dans la même hauteur
        val standardRows = com.example.ui.widget.IdelisAppWidgetProvider.calculateMaxRowsForHeight(
            heightDp = 360f,
            isCompact = false,
            fontScale = 1.0f
        )
        val compactRows = com.example.ui.widget.IdelisAppWidgetProvider.calculateMaxRowsForHeight(
            heightDp = 360f,
            isCompact = true,
            fontScale = 1.0f
        )
        assertTrue(compactRows >= standardRows)

        // Grand widget -> affiche plus de 8 bus et s'adapte jusqu'à 16 lignes
        val tallRows = com.example.ui.widget.IdelisAppWidgetProvider.calculateMaxRowsForHeight(
            heightDp = 560f,
            isCompact = false,
            fontScale = 1.0f
        )
        assertTrue(tallRows in 9..16)
        assertEquals(
            16,
            com.example.ui.widget.IdelisAppWidgetProvider.calculateMaxRowsForHeight(
                heightDp = 900f,
                isCompact = false,
                fontScale = 1.0f
            )
        )
    }

    @Test
    fun `widget click PendingIntent targets currently displayed stop id and name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pendingIntent = com.example.ui.widget.IdelisAppWidgetProvider.createOpenAppPendingIntent(
            context = context,
            appWidgetId = 12,
            stopId = "WEB_Gare_de_Pau",
            stopName = "Gare de Pau"
        )
        val shadowPi = org.robolectric.Shadows.shadowOf(pendingIntent)
        val intent = shadowPi.savedIntent
        assertEquals("WEB_Gare_de_Pau", intent.getStringExtra(com.example.ui.widget.IdelisAppWidgetProvider.EXTRA_STOP_ID))
        assertEquals("Gare de Pau", intent.getStringExtra(com.example.ui.widget.IdelisAppWidgetProvider.EXTRA_STOP_NAME))
        assertTrue(intent.data.toString().contains("WEB_Gare_de_Pau"))
    }

    @Test
    fun `viewModel selectStopById opens displayed widget stop instead of reverting to previously saved stop`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.local.PreferencesManager(context)
        prefs.setUsageStatsEnabled(false)
        prefs.saveAppUpdateCheckFrequency(AppUpdateCheckFrequency.NEVER)
        prefs.saveTrafficRefreshFrequency(com.example.data.model.TrafficRefreshFrequency.NEVER)
        prefs.saveGtfsSyncFrequency(GtfsSyncFrequency.NEVER)
        prefs.saveLastSelectedStopId("ID_BOSQUET")

        val db = IdelisDatabase.getInstance(context)
        runBlocking {
            db.stopDao().insertStops(
                listOf(
                    GtfsStop("ID_BOSQUET", "Pôle Bosquet", "Pôle central", 43.2951, -0.3698),
                    GtfsStop("ID_GARE", "Gare de Pau", "Correspondance SNCF", 43.2928, -0.3705)
                )
            )
        }

        val offlineClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(okhttp3.ResponseBody.create(null, ""))
                    .build()
            }
            .build()

        val repo = com.example.data.repository.IdelisRepository(
            database = db,
            appContext = context,
            preferencesManager = prefs,
            exploreService = com.example.data.remote.IdelisExploreService(offlineClient),
            gtfsDownloadService = com.example.data.remote.GtfsDownloadService(db, offlineClient),
            webScraperService = com.example.data.remote.IdelisWebScraperService(client = offlineClient)
        )
        val viewModel = com.example.ui.viewmodel.TransitBoardViewModel(repo, prefs)

        // Simule l'ouverture depuis le widget sur un arrêt favori/dynamique affiché
        viewModel.selectStopById("WEB_Parc_Lawrance", "Parc Lawrance")

        val shadowMainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        repeat(4) {
            shadowMainLooper.idleFor(java.time.Duration.ofMillis(50))
            Thread.sleep(15)
        }
        shadowMainLooper.idle()

        val selected = viewModel.uiState.value.selectedStop
        assertNotNull(selected)
        assertEquals("WEB_Parc_Lawrance", selected?.stopId)
        assertEquals("Parc Lawrance", selected?.stopName)
    }

    @Test
    fun `widget configure menu no longer shows Poles majeurs section`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                WidgetConfigureScreen(
                    appWidgetId = 606,
                    onConfigConfirmed = { _, _, _ -> },
                    onCancel = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(
            0,
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Pôles majeurs")).fetchSemanticsNodes().size
        )
    }

    @Test
    fun `widget configure separates Refresh, Menu, and Favorite button toggles`() {
        var confirmedConfig: WidgetConfig? = null

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                WidgetConfigureScreen(
                    appWidgetId = 303,
                    onConfigConfirmed = { cfg, _, _ -> confirmedConfig = cfg },
                    onCancel = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("widget_tab_style").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("widget_style_menu")
            .performScrollToNode(hasTestTag("widget_toggle_favorite_button"))
        composeTestRule.onNodeWithTag("widget_toggle_refresh_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("widget_toggle_menu_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("widget_toggle_favorite_button").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("confirm_widget_stop_button").performClick()
        composeTestRule.waitForIdle()

        assertNotNull(confirmedConfig)
        assertFalse(confirmedConfig!!.showRefreshButton)
        assertTrue(confirmedConfig!!.showMenuButton)
        assertTrue(confirmedConfig!!.showFavoriteSwitch)
    }
}
