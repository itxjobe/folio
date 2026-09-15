package com.mccal.folio

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import org.json.JSONObject
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.lang.ref.WeakReference

/** Activator-style triggers (idea from Activator by Ryan Petrich): gestures and events that run an action. */
enum class FolioTrigger(val label: String, val gesture: Boolean) {
    DOUBLE_TAP("Double-tap Home", true),
    TWO_FINGER_DOWN("Two-finger swipe down on Home", true),
    CHARGING("Charger connected", false),
    BLUETOOTH("Bluetooth headphones or speaker connects", false),
    HEADPHONES("Headphones plugged in", false),
}

enum class FolioAction(val label: String) {
    NONE("Nothing"), SPOTLIGHT("Spotlight"), NOTIFICATIONS("Notification Center"), CONTROL_CENTER("Control Center"),
    LOCK("Lock Screen"), SCREENSHOT("Screenshot"), TORCH("Flashlight"), DND_ON("Do Not Disturb On"), DND_OFF("Do Not Disturb Off"),
    FOCUS_SLEEP("Sleep Focus On/Off"), FOCUS_WORK("Work Focus On/Off"), FOCUS_PERSONAL("Personal Focus On/Off"), FOCUS_OFF("Turn Off Focus"),
}

internal object FolioActions {
    /** The launcher activity while it exists, so on-screen actions open in place. */
    @Volatile var home: WeakReference<MainActivity>? = null
    /** A panel to open when Home next resumes (for actions run while another app is in front). */
    @Volatile var pendingPanel: ShadePanel? = null

    fun actionFor(context: Context, trigger: FolioTrigger): FolioAction = runCatching {
        val saved = JSONObject(context.getSharedPreferences(SettingKeys.PREFS, 0).getString(SettingKeys.STATE, "{}") ?: "{}")
            .optJSONObject("triggerActions")?.optString(trigger.name)
        FolioAction.valueOf(saved ?: "NONE")
    }.getOrDefault(FolioAction.NONE)

    fun onTrigger(context: Context, trigger: FolioTrigger) {
        if (SafeMode.active) return
        val action = actionFor(context, trigger)
        if (action != FolioAction.NONE) run(context.applicationContext, action)
    }

    fun run(context: Context, action: FolioAction) {
        when (action) {
            FolioAction.NONE -> Unit
            FolioAction.SPOTLIGHT -> showOnHome(context, ShadePanel.SEARCH)
            FolioAction.NOTIFICATIONS -> showOnHome(context, ShadePanel.NOTIFICATIONS)
            FolioAction.CONTROL_CENTER -> showOnHome(context, ShadePanel.QUICK_SETTINGS)
            FolioAction.LOCK -> SystemShadeAccessibilityService.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            FolioAction.SCREENSHOT -> SystemShadeAccessibilityService.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            FolioAction.TORCH -> runCatching {
                val camera = context.getSystemService(CameraManager::class.java)
                val id = camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                if (id != null) camera.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId != id) return
                        camera.unregisterTorchCallback(this)
                        runCatching { camera.setTorchMode(id, !enabled) }
                    }
                }, android.os.Handler(android.os.Looper.getMainLooper()))
            }
            FolioAction.FOCUS_SLEEP -> FocusScheduler.toggle(context, "sleep")
            FolioAction.FOCUS_WORK -> FocusScheduler.toggle(context, "work")
            FolioAction.FOCUS_PERSONAL -> FocusScheduler.toggle(context, "personal")
            FolioAction.FOCUS_OFF -> FocusScheduler.setActive(context, null)
            FolioAction.DND_ON, FolioAction.DND_OFF -> runCatching {
                val notifications = context.getSystemService(NotificationManager::class.java)
                if (notifications.isNotificationPolicyAccessGranted) notifications.setInterruptionFilter(
                    if (action == FolioAction.DND_ON) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }
    }

    private fun showOnHome(context: Context, panel: ShadePanel) {
        val activity = home?.get()
        if (activity != null && FolioForeground.visible.value) { activity.showPanel(panel); return }
        pendingPanel = panel
        runCatching { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Two fingers moving down together on Home runs [action] (touches pass through otherwise). */
internal fun androidx.compose.ui.Modifier.twoFingerSwipeDown(action: FolioAction?, run: (FolioAction) -> Unit): androidx.compose.ui.Modifier =
    if (action == null) this else pointerInput(action) {
        val distance = 64.dp.toPx()
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var starts: Map<PointerId, Float>? = null
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2 && starts == null) starts = pressed.associate { it.id to it.position.y }
                val begun = starts ?: continue
                val moved = pressed.filter { it.id in begun }.map { it.position.y - begun.getValue(it.id) }
                if (moved.size >= 2 && moved.all { it > distance }) { event.changes.forEach { it.consume() }; run(action); break }
            }
        }
    }
