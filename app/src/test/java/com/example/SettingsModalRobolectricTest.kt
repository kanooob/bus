package com.example

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.AppUpdateCheckFrequency
import com.example.data.model.GtfsSyncFrequency
import com.example.data.model.InAppRefreshFrequency
import com.example.data.model.PrimaryColorMode
import com.example.data.model.ThemeMode
import com.example.data.model.UpdateNetworkMode
import com.example.data.model.WidgetLocationFrequency
import com.example.data.model.WidgetRefreshFrequency
import com.example.data.remote.GitHubReleaseInfo
import com.example.ui.components.SettingsModal
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsModalRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `settings categories open as submenus, hide submenu icon, and system back returns to main menu`() {
        var dismissCount = 0
        var backDispatcher: OnBackPressedDispatcher? = null

        composeTestRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
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
                    onDismissRequest = { dismissCount++ }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Menu principal : icône Paramètres visible et les 4 catégories affichées
        composeTestRule.onNodeWithTag("settings_main_icon").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_categories_menu").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_category_appearance").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_category_data_refresh").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_category_app_updates").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_categories_menu")
            .performScrollToNode(hasTestTag("settings_category_advanced"))
        composeTestRule.onNodeWithTag("settings_category_advanced").assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_theme_row").assertDoesNotExist()

        // 1. Sous-menu Apparence : bouton retour visible, icône masquée
        composeTestRule.onNodeWithTag("settings_categories_menu")
            .performScrollToNode(hasTestTag("settings_category_appearance"))
        composeTestRule.onNodeWithTag("settings_category_appearance").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_back_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_main_icon").assertDoesNotExist()
        composeTestRule.onNodeWithTag("setting_theme_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_primary_color_row").assertIsDisplayed()

        // Bouton retour système depuis le sous-menu -> revient au menu principal sans fermer les paramètres
        composeTestRule.runOnUiThread {
            backDispatcher?.onBackPressed()
        }
        composeTestRule.waitForIdle()
        assertEquals(0, dismissCount)
        composeTestRule.onNodeWithTag("settings_categories_menu").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_main_icon").assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_theme_row").assertDoesNotExist()

        // 2. Sous-menu Données et actualisation + retour via bouton en-tête
        composeTestRule.onNodeWithTag("settings_categories_menu")
            .performScrollToNode(hasTestTag("settings_category_data_refresh"))
        composeTestRule.onNodeWithTag("settings_category_data_refresh").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("setting_in_app_refresh_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Sous-menu Mises à jour + retour système
        composeTestRule.onNodeWithTag("settings_categories_menu")
            .performScrollToNode(hasTestTag("settings_category_app_updates"))
        composeTestRule.onNodeWithTag("settings_category_app_updates").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("setting_update_check_row").assertIsDisplayed()
        composeTestRule.runOnUiThread {
            backDispatcher?.onBackPressed()
        }
        composeTestRule.waitForIdle()
        assertEquals(0, dismissCount)

        // 4. Sous-menu Avancé + compteur d'utilisateurs + retour système -> puis retour système depuis le menu principal ferme les paramètres
        composeTestRule.onNodeWithTag("settings_categories_menu")
            .performScrollToNode(hasTestTag("settings_category_advanced"))
        composeTestRule.onNodeWithTag("settings_category_advanced").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("setting_backup_restore_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_network_api_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_usage_stats_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_usage_stats_switch").assertIsDisplayed()

        composeTestRule.runOnUiThread {
            backDispatcher?.onBackPressed()
        }
        composeTestRule.waitForIdle()
        assertEquals(0, dismissCount)
        composeTestRule.onNodeWithTag("settings_categories_menu").assertIsDisplayed()

        composeTestRule.runOnUiThread {
            backDispatcher?.onBackPressed()
        }
        composeTestRule.waitForIdle()
        assertEquals(1, dismissCount)
    }

    @Test
    fun `usage stats switch in advanced settings defaults to enabled and toggles callback`() {
        var currentEnabled = true
        var lastToggledValue: Boolean? = null

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
                    usageStatsEnabled = currentEnabled,
                    onToggleUsageStats = { enabled ->
                        currentEnabled = enabled
                        lastToggledValue = enabled
                    },
                    onDismissRequest = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_categories_menu")
            .performScrollToNode(hasTestTag("settings_category_advanced"))
        composeTestRule.onNodeWithTag("settings_category_advanced").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("setting_usage_stats_switch").performClick()
        composeTestRule.waitForIdle()

        assertEquals(false, lastToggledValue)
    }

    @Test
    fun `updates and about show current version content and advanced shows idelis disclaimer`() {
        val currentRelease = GitHubReleaseInfo(
            tagName = "v${BuildConfig.VERSION_NAME}",
            versionName = BuildConfig.VERSION_NAME,
            releaseName = "Mon Fébus v${BuildConfig.VERSION_NAME}",
            releaseNotes = "## Nouveautés\n- Affichage direct du contenu de version\n- Stabilité améliorée",
            htmlUrl = "https://github.com/kanooob/bus/releases/tag/v${BuildConfig.VERSION_NAME}",
            apkDownloadUrl = null,
            apkFileName = null,
            apkSizeBytes = 0L,
            isNewerThanCurrent = false
        )

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
                    currentVersionRelease = currentRelease,
                    onDismissRequest = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // 1. Mises à jour -> affiche le contenu de la version actuelle
        composeTestRule.onNodeWithTag("settings_category_app_updates").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_submenu_list")
            .performScrollToNode(hasTestTag("settings_updates_current_version_card"))
        composeTestRule.onNodeWithTag("settings_updates_current_version_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Affichage direct du contenu de version", substring = true)
            .assertIsDisplayed()

        // 2. À propos -> affiche Mon Fébus et le contenu de la version actuelle
        composeTestRule.onNodeWithTag("settings_submenu_list")
            .performScrollToNode(hasTestTag("setting_about_row"))
        composeTestRule.onNodeWithTag("setting_about_row").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Mon Fébus").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_about_current_version_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stabilité améliorée", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Fermer").performClick()
        composeTestRule.waitForIdle()

        // Retour au menu principal puis ouverture de Avancé -> affiche la provenance Idelis et les mentions
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_categories_menu")
            .performScrollToNode(hasTestTag("settings_category_advanced"))
        composeTestRule.onNodeWithTag("settings_category_advanced").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_submenu_list")
            .performScrollToNode(hasTestTag("advanced_data_disclaimer_card"))
        composeTestRule.onNodeWithTag("advanced_data_disclaimer_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Idelis", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("livrée telle quelle", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("inexactes", substring = true).assertIsDisplayed()
    }

    @Test
    fun `top bar clears alert badge after opening menu and period bubble wraps text width`() {
        val alerts = listOf(
            com.example.data.model.TrafficAlert(
                id = "t1",
                title = "Travaux Foch",
                messageText = "Déviation temporaire ligne F.",
                affectedLines = listOf("F"),
                severity = "WARNING",
                publishedDate = "En cours • idelis.fr",
                deviationLink = "https://www.idelis.fr/fileadmin/plan_foch.pdf"
            )
        )
        composeTestRule.setContent {
            var unreadCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(2) }
            MyApplicationTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Column {
                    com.example.ui.components.IdelisSketchTopBar(
                        stopName = "Pôle Bosquet",
                        onStopClick = {},
                        onTrafficInfoClick = { unreadCount = 0 },
                        onSettingsClick = {},
                        trafficAlertCount = unreadCount
                    )
                    com.example.ui.components.TrafficInfoModal(
                        alerts = alerts,
                        activePeriod = com.example.data.model.IdelisPeriod.ORANGE,
                        onDismissRequest = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("stop_header_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("traffic_info_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("2").assertIsDisplayed()

        // L'ouverture du menu Info Trafic supprime la pastille
        composeTestRule.onNodeWithTag("traffic_info_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2").assertDoesNotExist()

        composeTestRule.onNodeWithText("Voir le plan").assertExists()
        composeTestRule.onNodeWithText("Période Orange").assertExists()
        composeTestRule.onNodeWithText("Lundi au vendredi (scolaire)", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("1 alerte").assertDoesNotExist()
        composeTestRule.onNodeWithText("En cours • idelis.fr").assertDoesNotExist()

        val dialogBounds = composeTestRule.onNodeWithTag("traffic_info_dialog").fetchSemanticsNode().boundsInRoot
        val periodBounds = composeTestRule.onNodeWithTag("traffic_period_badge").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(
            "La bulle de période doit s'adapter au texte au lieu de prendre toute la largeur (${periodBounds.width} vs ${dialogBounds.width})",
            periodBounds.width < dialogBounds.width * 0.8f
        )
    }
}
