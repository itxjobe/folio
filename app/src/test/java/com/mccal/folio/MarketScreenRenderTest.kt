package com.mccal.folio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText

import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the Market on the JVM, so the screen is checked without a phone: the packages Folio ships really appear,
 * Get applies one, and the introduction is shown once.
 *
 * These check structure and behaviour, not looks: Robolectric has no fonts, so text measures at the wrong size and a
 * screenshot would be meaningless. Looks are checked in the Mockup Lab and on the phone.
 */
@RunWith(RobolectricTestRunner::class)
// A real phone window: the default test window is too small for anything to count as displayed.
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class MarketScreenRenderTest {
    @get:Rule val compose = createComposeRule()

    private fun session(): MarketSession {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        return MarketSession(context, NoopLauncher())
    }

    private class NoopLauncher : MarketLauncher {
        override var state = LauncherState()
        override fun installTweak(feature: TweakFeature) { state = state.copy(installedTweaks = state.installedTweaks + feature.id) }
        override fun removeTweak(feature: TweakFeature) { state = state.copy(installedTweaks = state.installedTweaks - feature.id) }
        override fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue) = Unit
        override fun applyTheme(theme: FolioTheme) = Unit
    }

    @Test fun `the introduction comes first, then Featured lists Folio's packages`() {
        val session = session()
        session.prefs.introductionSeen = false
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithText("Welcome to the Folio Market").assertIsDisplayed()
        compose.onNodeWithText("Skip").performClick()
        compose.onNodeWithText("Cabinet").assertIsDisplayed()
    }

    @Test fun `Get applies a package and offers Undo`() {
        val session = session()
        session.prefs.introductionSeen = true
        session.installed().forEach { session.remove(it.id) }
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithTag("market-tab-packages").performClick()
        compose.onNodeWithContentDescription("Get Cabinet").performClick()
        compose.onNodeWithTag("market-install-confirm").performScrollTo().performClick()
        compose.onNodeWithText("Cabinet is on").assertExists()
        compose.onNodeWithText("Undo").assertExists()
    }

    @Test fun `every tab opens, and the Market's settings offer both Featured styles`() {
        val session = session()
        session.prefs.introductionSeen = true
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        for (tab in MarketTab.entries) {
            compose.onNodeWithTag("market-tab-${tab.name.lowercase()}").performClick()
        }
        // Settings is the last tab; it offers the style choice and a way back to Folio's own settings.
        compose.onNodeWithText("Carousel").assertIsDisplayed()
        compose.onNodeWithText("Calm").assertIsDisplayed()
        compose.onNodeWithText("Open Folio Settings").assertIsDisplayed()
    }

    @Test fun `Sources says what Folio has and what is coming`() {
        val session = session()
        session.prefs.introductionSeen = true
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithTag("market-tab-sources").performClick()
        compose.onNodeWithText("Built into the app. 9 packages, no network.").assertIsDisplayed()
    }

    @Test fun `the Settings tab shows Folio's own settings when the launcher gives them`() {
        val session = session()
        session.prefs.introductionSeen = true
        compose.setContent {
            MarketScreen(session, emptySet(), onClose = {}, settingsContent = { androidx.compose.material3.Text("Folio settings live here") })
        }
        compose.onNodeWithTag("market-tab-settings").performClick()
        compose.onNodeWithText("Folio settings live here").assertIsDisplayed()
    }

    @Test fun `the app icon opens the Market, unless something asked for a Settings page`() {
        assertEquals("market", sheetForAppIcon(null, CustomizationPage.OVERVIEW, marketEnabled = true))
        assertEquals("settings", sheetForAppIcon(null, CustomizationPage.OVERVIEW, marketEnabled = false))
        assertEquals("settings", sheetForAppIcon(CustomizationPage.PERMISSIONS, CustomizationPage.OVERVIEW, marketEnabled = true))
        assertEquals("settings", sheetForAppIcon(null, CustomizationPage.SOFTWARE_UPDATE, marketEnabled = true))
    }

    @Test fun `Get asks first, and the sheet says what changes and what it can't reach`() {
        val session = session()
        session.prefs.introductionSeen = true
        session.installed().forEach { session.remove(it.id) }
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithTag("market-tab-packages").performClick()
        compose.onNodeWithContentDescription("Get Cabinet").performClick()
        // Nothing has been applied yet: this is the confirm step.
        compose.onNodeWithTag("market-install-sheet", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Changes Folio tweaks").assertExists()
        compose.onNodeWithText("Your apps or their data").assertExists()
        assertEquals(emptyList<com.mccal.folio.market.InstalledPackage>(), session.installed())
        compose.onNodeWithTag("market-install-confirm").performScrollTo().performClick()
        compose.onNodeWithText("Cabinet is on").assertExists()
        assertEquals(listOf("com.mccal.folio.cabinet"), session.installed().map { it.id })
    }
}
