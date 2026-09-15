package com.mccal.folio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer

/**
 * One glass language for every Folio overlay (Control Center, Notification Center, Spotlight,
 * pickers): a darker, more solid frost than the Home rail, so busy blurred icons behind never
 * compete with the content, plus a hairline light edge like iOS materials.
 */
internal object FolioGlass {
    /** Dim over the blurred Home behind an overlay. */
    val scrim = Color.Black.copy(alpha = .42f)
    /** Large tiles (Control Center modules). */
    val module = Color(0xFF1C1C1E).copy(alpha = .76f)
    /** Cards and rows (notifications, Spotlight sections, search field). */
    val card = Color(0xFF242428).copy(alpha = .82f)
    /** Controls sitting on a card (inactive toggles, pills, chips). */
    val raised = Color.White.copy(alpha = .14f)
    /** Hairline edge that separates glass from glass. */
    val edge = BorderStroke(1.dp, Color.White.copy(alpha = .08f))
    /** Secondary text on glass. */
    val secondary = Color.White.copy(alpha = .62f)
}

/** Dark, iOS-like colors for Folio's sheets (settings, app options, setup). */
internal val FolioSheetColors = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF0A84FF), onPrimary = Color.White,
    primaryContainer = Color(0xFF0A84FF), onPrimaryContainer = Color.White,
    secondary = Color(0xFF64D2FF), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3A3A3C), onSecondaryContainer = Color.White,
    surface = Color(0xFF1C1C1E), onSurface = Color.White, onSurfaceVariant = Color(0xFFA1A1A6),
    surfaceContainerLowest = Color(0xFF141416), surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF242428), surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF545458), outlineVariant = Color(0xFF38383A), error = Color(0xFFFF453A),
)

/** Set while any launcher sheet is open, so Home blurs behind it like the other overlays. */
internal val LauncherSheetsOpen = androidx.compose.runtime.mutableIntStateOf(0)

/**
 * Top-safe insets that respect the real camera cutout. Folio hides the status bar on Home, which
 * makes `statusBarsPadding()` zero, so content slid under the camera; the display cutout is still
 * reported while bars are hidden, so union both.
 */
internal val WindowInsets.Companion.folioSafeTop: WindowInsets
    @androidx.compose.runtime.Composable get() = WindowInsets.statusBars.union(WindowInsets.displayCutout).union(rememberHiddenCameraInsets())

/** Per-folder tint colors, provided from saved settings. */
internal val LocalFolderColors = androidx.compose.runtime.compositionLocalOf { emptyMap<String, Long>() }

/** Velvet/ColorFlow tint options, provided from settings. */
@androidx.compose.runtime.Immutable
internal data class TintOptions(val notifications: Boolean = false, val media: Boolean = true, val notificationAppRow: Boolean = true)
internal val LocalTintOptions = androidx.compose.runtime.staticCompositionLocalOf { TintOptions() }

/** How strong Home's glass is: widget frost and the outline around widgets and Side Bar capsules (Settings › Glass). */
internal data class GlassLook(val widget: Float = .26f, val outline: Float = .16f) {
    val outlineColor get() = androidx.compose.ui.graphics.Color.White.copy(alpha = outline)
}
internal val LocalGlassLook = androidx.compose.runtime.staticCompositionLocalOf { GlassLook() }

/** Settings › Home Screen & Dock › Folders. */
enum class FolderBackground(val label: String) { GLASS("Glass"), SOLID("Solid"), CLEAR("Clear") }
internal data class FolderLook(val columns: Int = 0, val background: FolderBackground = FolderBackground.GLASS)
internal val LocalFolderLook = androidx.compose.runtime.staticCompositionLocalOf { FolderLook() }

/** App name size on Home (Settings › Icons & Side Bar). */
enum class LabelSize(val label: String, val sp: Float, val lineSp: Float) { SMALL("Small", 10f, 13f), STANDARD("Standard", 11f, 14f), LARGE("Large", 13f, 16f) }
internal val LocalLabelSize = androidx.compose.runtime.staticCompositionLocalOf { LabelSize.STANDARD }

/**
 * Animation Speed (Settings › Gestures & Actions): scales the stiffness of Folio's springs, so panels, folders, menus
 * and page snaps all move faster or slower together. Android's Remove animations still turns motion off.
 */
enum class MotionSpeed(val label: String, val factor: Float) {
    RELAXED("Relaxed", .55f), STANDARD("Standard", 1f), SNAPPY("Snappy", 1.8f);
    companion object {
        @Volatile var current: MotionSpeed = STANDARD
        fun <T> spring(dampingRatio: Float, stiffness: Float) =
            androidx.compose.animation.core.spring<T>(dampingRatio = dampingRatio, stiffness = stiffness * current.factor)
    }
}

/**
 * Soft edges on scrolling content, like iOS: instead of rows being chopped off at the edge of a list, they fade out
 * over [size] — but only on a side where there's more to scroll, so the first and last rows stay crisp.
 */
internal fun androidx.compose.ui.Modifier.edgeFade(canScrollUp: () -> Boolean, canScrollDown: () -> Boolean,
    size: androidx.compose.ui.unit.Dp = 20.dp): androidx.compose.ui.Modifier =
    // An offscreen layer only while an edge is actually fading, so a list at rest draws normally.
    graphicsLayer { compositingStrategy = if (canScrollUp() || canScrollDown()) androidx.compose.ui.graphics.CompositingStrategy.Offscreen
        else androidx.compose.ui.graphics.CompositingStrategy.Auto }
        .drawWithContent {
            drawContent()
            val px = size.toPx().coerceAtMost(this.size.height / 3f)
            if (canScrollUp()) drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black), startY = 0f, endY = px),
                size = androidx.compose.ui.geometry.Size(this.size.width, px), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
            if (canScrollDown()) drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color.Black, Color.Transparent), startY = this.size.height - px, endY = this.size.height),
                topLeft = androidx.compose.ui.geometry.Offset(0f, this.size.height - px),
                size = androidx.compose.ui.geometry.Size(this.size.width, px), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
        }

internal fun androidx.compose.ui.Modifier.edgeFade(state: androidx.compose.foundation.ScrollState) =
    edgeFade({ state.value > 0 }, { state.value < state.maxValue })
internal fun androidx.compose.ui.Modifier.edgeFade(state: androidx.compose.foundation.lazy.LazyListState) =
    edgeFade({ state.canScrollBackward }, { state.canScrollForward })
internal fun androidx.compose.ui.Modifier.edgeFade(state: androidx.compose.foundation.lazy.grid.LazyGridState) =
    edgeFade({ state.canScrollBackward }, { state.canScrollForward })

/** verticalScroll with soft edges (see edgeFade). */
internal fun androidx.compose.ui.Modifier.fadingVerticalScroll() = composed {
    val state = androidx.compose.foundation.rememberScrollState()
    edgeFade(state).verticalScroll(state)
}
