package com.mccal.folio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mccal.folio.market.MarketFeature

/**
 * The Market, in its own window: the themes and tweaks Folio ships, as packages.
 *
 * It's hidden until 0.7.0 ships ([MarketFeature]), so Folio Dev has it and the release doesn't. Changes go through
 * [MarketHost] into the same model the rest of Folio uses, so getting a theme here and changing it in Settings are the
 * same thing.
 */
class MarketActivity : ComponentActivity() {
    private val model: LauncherModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!MarketFeature.isEnabled(packageName)) {
            finish()
            return
        }
        val session = MarketSession(this, ModelLauncher(model))
        // If Folio stopped twice while a package was being applied, that package starts turned off.
        session.noteCrash()
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            DuoTheme(rememberSavedAppearance().dark) {
                BackHandler { finish() }
                MarketScreen(session, state.installedTweaks, onClose = ::finish)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }
}
