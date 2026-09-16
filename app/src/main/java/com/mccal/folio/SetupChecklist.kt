package com.mccal.folio

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** One thing Folio needs (or recommends) and whether it's done. */
internal data class SetupStep(
    val icon: ImageVector, val title: String, val detail: String, val done: Boolean,
    val required: Boolean, val action: String, val onAction: () -> Unit,
)

@Composable
internal fun rememberSetupSteps(isDefaultHome: Boolean, onMakeDefault: () -> Unit, onShadeSetup: () -> Unit,
    messagesApp: String? = null, onMessagesApp: (String?) -> Unit = {},
    systemWallpaper: Boolean = false, onSystemWallpaper: (Boolean) -> Unit = {}): List<SetupStep> {
    val context = LocalContext.current
    // Re-check every time Folio comes back from a settings screen.
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    val contacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    return remember(tick, isDefaultHome, messagesApp, systemWallpaper) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        listOfNotNull(
            SetupStep(Icons.Rounded.Home, "Make Folio your Home app", "So Home, gestures and the fold effect are always Folio.",
                isDefaultHome, true, "Set") { onMakeDefault() },
            SetupStep(Icons.Rounded.Notifications, "Notification access",
                "Powers the Dynamic Island and Notification Center. Nothing leaves your phone.",
                IslandListenerService.hasAccess(context), true, "Allow") { open(IslandListenerService.accessSettingsIntent(context)) },
            SetupStep(Icons.Rounded.Accessibility, "Folio gestures service",
                "Lets pull-downs open system panels, and shows the dock handle and island in other apps.",
                SystemShadeAccessibilityService.isConnected(), true, "Turn on") { onShadeSetup() },
            // Samsung's setting; on phones without it there's nothing to set, so it isn't a step.
            if (foldLockSetting(context) != null) SetupStep(Icons.Rounded.Devices, "Continue apps on cover screen: Always",
                "Keeps the cover screen on when you fold from Home. Settings › Display › Continue apps on cover screen.",
                foldStaysAwake(context), true, "Open") { open(Intent(Settings.ACTION_DISPLAY_SETTINGS)) } else null,
            SetupStep(Icons.Rounded.LightMode, "Modify system settings",
                "Lets Control Center change brightness and rotation lock.",
                Settings.System.canWrite(context), false, "Allow") {
                open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            },
            SetupStep(Icons.Rounded.DarkMode, "Do Not Disturb access", "Lets Control Center turn Do Not Disturb on and off.",
                notifications.isNotificationPolicyAccessGranted, false, "Allow") { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
            SetupStep(Icons.Rounded.Wallpaper, "Keep your wallpaper",
                "Coming from Samsung’s or another launcher? Show the same wallpaper behind Folio.",
                systemWallpaper, false, "Use") { onSystemWallpaper(true); (context as? android.app.Activity)?.recreate() },
            SetupStep(Icons.Rounded.Assistant, "Folio as your digital assistant",
                "Holding the side key opens Folio’s picker: ChatGPT, Claude, Perplexity, Gemini, or Google without AI.",
                AssistPickerActivity.isDefaultAssistant(context), false, "Choose") { open(AssistPickerActivity.settingsIntent()) },
            if (sideKeySettings(context) != null) SetupStep(Icons.Rounded.TouchApp, "Hold side key: Digital assistant",
                "Samsung: Side button › Press and hold › Digital assistant, so holding the key reaches Folio’s picker.",
                sideKeyHoldIsAssistant(context), false, "Open") { sideKeySettings(context)?.let(::open) } else null,
            SetupStep(Icons.Rounded.Contacts, "Contacts in Spotlight", "Search your contacts from Spotlight.",
                granted(context, Manifest.permission.READ_CONTACTS), false, "Allow") { contacts.launch(Manifest.permission.READ_CONTACTS) },
            // Only for people who already use OpenBubbles (iMessage on Android); Folio just opens it.
            if (Messaging.installed(context, Messaging.OPENBUBBLES)) SetupStep(Icons.Rounded.Forum, "iMessage with OpenBubbles",
                "Message contacts from Spotlight in OpenBubbles. New messages also pop out of the island with quick reply.",
                messagesApp == Messaging.OPENBUBBLES, false, "Use") { onMessagesApp(Messaging.OPENBUBBLES) } else null,
        )
    }
}

/** Samsung's "Side button › Press and hold" screen, when this phone has it. */
internal fun sideKeySettings(context: Context): Intent? =
    Intent("com.samsung.android.intent.action.SIDE_KEY_LONG_PRESS_SETTINGS")
        .takeIf { it.resolveActivity(context.packageManager) != null }

/** Samsung stores the side key hold action as a global setting; 2 is the digital assistant. */
internal fun sideKeyHoldIsAssistant(context: Context) =
    runCatching { Settings.Global.getInt(context.contentResolver, "function_key_config_longpress_type") }.getOrNull() == 2

/** Samsung's "Side button › Double press" screen, when this phone has it. */
internal fun sideKeyDoublePressSettings(context: Context): Intent? =
    Intent("com.samsung.android.intent.action.SIDE_KEY_DOUBLE_PRESS_SETTINGS")
        .takeIf { it.resolveActivity(context.packageManager) != null }

/** Whether Samsung's double press opens Google Wallet (its settings name the app it launches). */
internal fun sideKeyDoublePressIsWallet(context: Context): Boolean = runCatching {
    val r = context.contentResolver
    listOf("function_key_config_doublepress_value", "function_key_config_doublepress_intent")
        .any { Settings.Global.getString(r, it)?.contains("com.google.android.apps.walletnfcrel") == true }
}.getOrDefault(false)

private fun granted(context: Context, permission: String) =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun foldLockSetting(context: Context): String? =
    runCatching { Settings.System.getString(context.contentResolver, "fold_lock_behavior_setting") }.getOrNull()

internal fun foldStaysAwake(context: Context) = foldLockSetting(context) == "stay_awake_on_fold_key"

