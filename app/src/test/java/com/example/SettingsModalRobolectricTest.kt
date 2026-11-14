package com.example

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
}
