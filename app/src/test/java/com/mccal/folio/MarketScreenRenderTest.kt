package com.mccal.folio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
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
        compose.onNodeWithText("Packages").performClick()
        compose.onNodeWithContentDescription("Get Cabinet").performClick()
        compose.onNodeWithText("Cabinet is on").assertIsDisplayed()
        compose.onNodeWithText("Undo").assertIsDisplayed()
    }
}
