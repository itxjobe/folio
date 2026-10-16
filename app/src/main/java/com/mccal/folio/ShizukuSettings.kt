package com.mccal.folio

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle

internal fun shizukuStatusLabel(status: ShizukuBridge.Status) = when (status) {
    ShizukuBridge.Status.NOT_INSTALLED -> R.string.shizuku_not_installed
    ShizukuBridge.Status.NOT_RUNNING -> R.string.shizuku_not_running
    ShizukuBridge.Status.PERMISSION_NEEDED -> R.string.shizuku_permission_needed
    ShizukuBridge.Status.ACTIVE -> R.string.shizuku_active
}

/** Advanced › Shizuku: status, the permission button, the bar options and how to make Shizuku start on boot. */
@Composable internal fun ShizukuPage() {
    val context = LocalContext.current
    val status = ShizukuBridge.status.value
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { ShizukuBridge.start(context); lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { ShizukuBridge.refresh() } }
    Text(stringResource(R.string.shizuku_intro), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = FolioSpace.TINY.dp))
    SettingsCard(stringResource(R.string.shizuku_status)) {
        Text(stringResource(shizukuStatusLabel(status)), style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = FolioSpace.SMALL.dp).testTag("shizuku-status"))
        when (status) {
            ShizukuBridge.Status.NOT_INSTALLED -> CardPrimaryAction(stringResource(R.string.shizuku_get), Modifier.testTag("shizuku-get")) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } // english-only
            ShizukuBridge.Status.NOT_RUNNING -> CardPrimaryAction(stringResource(R.string.shizuku_open), Modifier.testTag("shizuku-open")) {
                context.packageManager.getLaunchIntentForPackage(ShizukuBridge.PACKAGE)?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } }
            ShizukuBridge.Status.PERMISSION_NEEDED -> CardPrimaryAction(stringResource(R.string.shizuku_request), Modifier.testTag("shizuku-request")) { ShizukuBridge.requestPermission() }
            ShizukuBridge.Status.ACTIVE -> CardNote(stringResource(R.string.shizuku_active_note))
        }
    }
    SettingsCard(stringResource(R.string.shizuku_bars)) {
        var home by remember { mutableStateOf(ShizukuBridge.hideOnHome(context)) }
        var global by remember { mutableStateOf(ShizukuBridge.hideGlobally(context)) }
        var result by remember { mutableStateOf<Int?>(null) }
        SettingsSwitch(stringResource(R.string.shizuku_hide_home), home, { on -> home = on; ShizukuBridge.setHideOnHome(context, on) }, "shizuku-hide-home")
        CardNote(stringResource(R.string.shizuku_hide_home_note))
        SettingsSwitch(stringResource(R.string.shizuku_hide_global), global, { on ->
            global = on
            ShizukuBridge.setHideGlobally(context, on) { pill, immersive ->
                result = when { !on -> null; !pill -> R.string.shizuku_hide_failed; immersive -> R.string.shizuku_hide_full; else -> R.string.shizuku_hide_pill_only }
            } }, "shizuku-hide-global", enabled = status == ShizukuBridge.Status.ACTIVE || global)
        CardNote(stringResource(R.string.shizuku_hide_global_note))
        result?.let { CardNote(stringResource(it), Modifier.testTag("shizuku-hide-result")) }
    }
    SettingsCard(stringResource(R.string.shizuku_features)) { CardNote(stringResource(R.string.shizuku_features_note)) }
    SettingsCard(stringResource(R.string.shizuku_boot)) { CardNote(stringResource(R.string.shizuku_boot_note)) }
}
