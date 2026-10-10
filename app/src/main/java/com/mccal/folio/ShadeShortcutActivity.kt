package com.mccal.folio

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * "Notification shade" and "Quick settings" as launchable entries (two aliases in the manifest), so a phone's own
 * gesture or button action that opens an app (Samsung's One-hand operation, Routines, a Home shortcut) can pull the
 * shade down over whatever is open. It uses the gestures service Folio already has, shows nothing, and leaves at once.
 * Without the service it takes you to Folio's explanation of how to turn it on.
 */
class ShadeShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val panel = intent.component?.let { component ->
            runCatching { packageManager.getActivityInfo(component, PackageManager.GET_META_DATA).metaData?.getString(META_PANEL) }.getOrNull()
        }.let(ShadePanel::fromShortcut)
        // Leave first, then pull the shade down. Starting or finishing an activity collapses the status bar panels, so
        // opening the shade during onCreate (as before) was undone by our own exit: from Routines nothing appeared (#294).
        // A Routine can also start Folio cold, before the gestures service has reconnected; wait briefly for it instead
        // of giving up with a notice nobody sees.
        finish()
        if (panel == null) return
        val app = applicationContext
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        var waited = 0L
        fun attempt() {
            when (SystemShadeAccessibilityService.open(app, panel)) {
                ShadeOpenResult.SERVICE_DISABLED -> app.startActivity(Intent(app, MainActivity::class.java)
                    .putExtra("duo_destination", "shade_setup").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ShadeOpenResult.SERVICE_STARTING -> if (waited < START_WAIT_MS) { waited += RETRY_MS; main.postDelayed(::attempt, RETRY_MS) }
                    else IslandEvents.notice(app, app.getString(R.string.folio_gestures_are_starting_swipe_down_a))
                ShadeOpenResult.ACTION_REJECTED -> if (waited == 0L) { waited = RETRY_MS; main.postDelayed(::attempt, RETRY_MS) }
                ShadeOpenResult.OPENED -> Unit
            }
        }
        main.postDelayed(::attempt, EXIT_SETTLE_MS)
    }

    private companion object {
        const val META_PANEL = "folio.shade.panel"
        const val EXIT_SETTLE_MS = 250L
        const val RETRY_MS = 250L
        const val START_WAIT_MS = 3_000L
    }
}
