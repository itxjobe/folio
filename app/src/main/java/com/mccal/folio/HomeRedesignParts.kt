package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

// Kept out of LauncherScreen's one big composable lambda: it is past ART's per-method limit, and code added inline
// there made the 08:49 build fail verification on launch (VerifyError in LauncherScreenKt).

/** iPadOS dock (#130): up to three apps opened lately from Folio, refreshed whenever Home comes back. */
@Composable
internal fun rememberDockRecents(enabled: Boolean, dock: List<String?>, appsById: Map<String, AppEntry>): List<AppEntry> {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    if (!enabled) return emptyList()
    return remember(tick, dock, appsById) { RecentApps.load(context).filter { it !in dock }.mapNotNull(appsById::get).take(3) }
}

@Composable
internal fun DockRecents(apps: List<AppEntry>, pitch: Float, iconSize: Float, onLaunch: (AppEntry, android.graphics.Rect?) -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxHeight().padding(end = FolioSpace.SMALL.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(horizontal = 8.dp).width(1.dp).fillMaxHeight(.6f).background(Color.White.copy(alpha = .3f)))
        apps.forEach { app ->
            Box(Modifier.width(pitch.dp).fillMaxHeight().clickable { onLaunch(app, null) }, contentAlignment = Alignment.Center) {
                AppIcon(app, app.label, Modifier.size(iconSize.dp))
            }
        }
    }
}

/** iPhone Fold's small round search button beside the page dots. */
@Composable
internal fun HomeSearchButton(modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(FolioTouch.MIN.dp).clip(CircleShape).clickable(onClick = onClick).testTag("home-search-button"), contentAlignment = Alignment.Center) {
        Box(Modifier.size(34.dp).background(LocalGlassLook.current.fill(Glass), CircleShape)
            .border(1.dp, LocalGlassLook.current.outlineColor, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Search, stringResource(R.string.search), tint = LocalHomeInk.current.primary, modifier = Modifier.size(18.dp))
        }
    }
}

/** A firm tick when something you were dragging lands, like iOS's drop. Android's touch-feedback setting still decides. */
@Composable
internal fun DropHaptic(drag: HomeDragState) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var carrying by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(drag.active, drag.moved) {
        if (drag.active && drag.moved) carrying = true
        else if (!drag.active && carrying) { carrying = false; haptic.perform(FolioHaptic.Commit) }
    }
}
