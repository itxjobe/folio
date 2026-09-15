package com.mccal.folio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** How the side-rail status capsule looks. Saved with the launcher state. */
data class StatusStyle(
    val showTime: Boolean = true,
    val showDate: Boolean = true,
    val showBatteryPercent: Boolean = true,
    val glyph: StatusGlyph = StatusGlyph.RING,
    val colorfulBattery: Boolean = true,
    /** Frost strength shared by the status, dock and island capsules (0 = clear, 1 = solid). */
    val railGlass: Float = .26f,
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().put("showTime", showTime).put("showDate", showDate)
        .put("showBatteryPercent", showBatteryPercent).put("glyph", glyph.name).put("colorfulBattery", colorfulBattery)
        .put("railGlass", railGlass.toDouble())

    companion object {
        fun fromJson(j: org.json.JSONObject?): StatusStyle = if (j == null) StatusStyle() else StatusStyle(
            showTime = j.optBoolean("showTime", true), showDate = j.optBoolean("showDate", true),
            showBatteryPercent = j.optBoolean("showBatteryPercent", true),
            glyph = runCatching { StatusGlyph.valueOf(j.optString("glyph")) }.getOrDefault(StatusGlyph.RING),
            colorfulBattery = j.optBoolean("colorfulBattery", true),
            railGlass = j.optDouble("railGlass", .26).toFloat().coerceIn(0f, 1f))
    }
}

enum class StatusGlyph(val label: String) { RING("Ring"), ICONS("Icons"), MINIMAL("Battery only"), NONE("Hidden") }

/** Shared capsule look for the side rail (status, dock, island). */

@Composable
fun StatusRail(
    status: DeviceStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    iconSize: Dp = 40.dp,
    locationInUse: Boolean = false,
    island: (@Composable () -> Unit)? = null,
    style: StatusStyle = StatusStyle(),
    /** The Focus that's on: its icon sits above the time, as iPhone shows it beside the clock. */
    focus: FocusMode? = null,
) {
    // Text sits on the frosted capsule, not straight on the wallpaper, so pick its color from how light the capsule
    // looks: the wallpaper's main color seen through the glass. An explicit Light or Dark "Text on Home" still wins.
    val tone = LocalWallpaperTone.current
    val glassColor = Glass
    val homeInk = LocalHomeInk.current.let { base ->
        if (!base.automatic) base else {
            val wallpaperLum = tone.primary?.let { Color(it).luminance() } ?: if (tone.prefersDarkText) .75f else .25f
            val capsuleLum = wallpaperLum + (glassColor.luminance() - wallpaperLum) * style.railGlass.coerceIn(0f, 1f) * 1.6f
            HomeInk(dark = capsuleLum.coerceIn(0f, 1f) > .5f, automatic = true)
        }
    }
    val ink = homeInk.primary
    // Over light wallpapers (dark text) the frosted capsule is light too, so dim parts and colors need more weight to read.
    val onLight = homeInk.dark
    fun faint(alpha: Float) = if (onLight) (alpha * 1.6f).coerceAtMost(.6f) else alpha
    val charging = if (onLight) BatteryChargingOnLight else BatteryCharging
    val low = if (onLight) BatteryLowOnLight else BatteryLow
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_050L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val format = if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"
    val timeFormatter = remember(format) { DateTimeFormatter.ofPattern(format) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d") }
    val description = listOfNotNull(
        if (locationInUse) "Location in use" else null,
        focus?.let { "${it.name} on" },
        now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, $format")),
        status.battery?.let { "Battery $it percent${if (status.charging) ", charging" else ""}" } ?: "Battery unavailable",
        if (status.wifiConnected) "Wi-Fi connected${status.wifiLevel?.let { ", signal $it of 4" } ?: ""}" else "Wi-Fi disconnected",
        if (status.airplane) "Airplane mode" else status.cellularLevel?.let { "Cellular signal $it of 4" } ?: "Cellular signal unavailable",
    ).joinToString(". ")
    val fontScale = LocalDensity.current.fontScale
    val wifiVisual = wifiSignalVisual(status.wifiConnected, status.wifiLevel)
    val cellularVisual = cellularSignalVisual(status.cellularLevel, status.airplane)
    val activeDots = (cellularVisual as? CellularSignalVisual.Available)?.activeDots ?: 0
    val batteryColor = when {
        !style.colorfulBattery -> ink
        status.charging -> charging
        (status.battery ?: 100) <= 20 -> low
        else -> ink
    }
    val capsule = RoundedCornerShape(30.dp)
    BoxWithConstraints(modifier.testTag("status-rail").semantics(mergeDescendants = true) { contentDescription = description }) {
        val availableWidth = (maxWidth - 16.dp).coerceAtLeast(28.dp)
        val visualSize = minOf(iconSize * .9f, availableWidth, if (compact) 34.dp else 44.dp)
        val timeSize = minOf(17f, availableWidth.value / (2.5f * fontScale)).sp
        val detailSize = minOf(11f, availableWidth.value / (3.3f * fontScale)).sp
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Compact windows omit the reserve so status stays clear of the fixed dock.
            if (!compact) Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
                // Callers currently leave this false; the slot waits for a truthful activity signal.
                if (locationInUse) Icon(Icons.Rounded.LocationOn, null, tint = ink,
                    modifier = Modifier.size(18.dp))
            }
            val hasContent = style.showTime || (!compact && style.showDate) || style.glyph != StatusGlyph.NONE ||
                (!compact && style.showBatteryPercent)
            // Same frosted capsule as the dock so status and dock read as one side rail.
            if (hasContent) Column(Modifier.fillMaxWidth().background(Glass.copy(alpha = style.railGlass), capsule)
                .border(1.dp, LocalGlassLook.current.outlineColor, capsule)
                .padding(vertical = if (compact) 8.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                focus?.let { Icon(it.icon(), "${it.name} on", tint = androidx.compose.ui.graphics.Color(it.color).let { c ->
                    if (LocalHomeInk.current.dark) c else androidx.compose.ui.graphics.lerp(c, androidx.compose.ui.graphics.Color.White, .35f) },
                    modifier = Modifier.size(if (compact) 14.dp else 16.dp).testTag("status-focus")) }
                if (style.showTime) Text(now.format(timeFormatter), color = ink, fontSize = timeSize, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                if (!compact && style.showDate) Text(now.format(dateFormatter), color = ink.copy(alpha = if (onLight) .85f else .7f), fontSize = detailSize,
                    fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                when (style.glyph) {
                    StatusGlyph.RING, StatusGlyph.MINIMAL -> Box(Modifier.padding(top = 2.dp).size(visualSize), contentAlignment = Alignment.Center) {
                        // Inside the battery ring, like iPhone's status bar: Wi-Fi when joined; otherwise cellular bars,
                        // an airplane in Airplane Mode, or a slowly sweeping fan while there's no connection at all.
                        val offline = !status.wifiConnected && cellularVisual !is CellularSignalVisual.Available && !status.airplane
                        // With Reduce Motion, a still fan with a slash instead.
                        val sweep = if (offline && style.glyph == StatusGlyph.RING && !LocalReduceMotion.current) rememberInfiniteTransition(label = "no connection")
                            .animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing)), label = "sweep").value else -1f
                        Canvas(Modifier.fillMaxSize()) {
                            val w = size.width
                            val center = Offset(w / 2, w / 2)
                            // Battery: one thin full ring, filled clockwise from the top.
                            val radius = w * .44f
                            drawCircle(ink.copy(alpha = faint(.22f)), radius, center, style = Stroke(w * .06f))
                            status.battery?.let { level ->
                                drawArc(batteryColor, -90f, 360f * level / 100, false, Offset(center.x - radius, center.y - radius),
                                    Size(radius * 2, radius * 2), style = Stroke(width = w * .06f, cap = StrokeCap.Round))
                            }
                            if (style.glyph == StatusGlyph.RING) when {
                                wifiVisual is WifiSignalVisual.Connected -> {
                                    drawWifiFan(w, wifiVisual, ink = ink, onLight = onLight)
                                    // Cellular: a short row of dots under the fan.
                                    for (i in 0..4) drawCircle(ink.copy(alpha = if (i < activeDots) 1f else faint(.28f)), w * .026f,
                                        Offset(center.x + (i - 2) * w * .085f, w * .74f))
                                }
                                cellularVisual is CellularSignalVisual.Available -> drawCellBars(w, activeDots, ink, onLight)
                                status.airplane -> Unit // the airplane icon draws on top
                                else -> drawSearchingFan(w, sweep, ink, onLight)
                            } else status.battery?.let { level ->
                                // Minimal: nothing inside the ring but a small charge dot when charging.
                                if (status.charging) drawCircle(batteryColor, w * .06f, center)
                            }
                        }
                        if (style.glyph == StatusGlyph.RING && status.airplane && !status.wifiConnected)
                            Icon(Icons.Rounded.AirplanemodeActive, null, tint = ink, modifier = Modifier.size(visualSize * .42f))
                    }
                    StatusGlyph.ICONS -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)) {
                        // Wi-Fi when joined (in Home's text color, so it reads on light wallpapers), an airplane in Airplane Mode;
                        // otherwise nothing, and the cellular bars below say how you're connected.
                        if (wifiVisual is WifiSignalVisual.Connected) Canvas(Modifier.size(width = 22.dp, height = 16.dp)) { drawWifiFan(size.width, wifiVisual, centered = true, ink = ink, onLight = onLight) }
                        else if (status.airplane) Icon(Icons.Rounded.AirplanemodeActive, "Airplane Mode", tint = ink, modifier = Modifier.size(16.dp))
                        Canvas(Modifier.size(width = 22.dp, height = 14.dp)) {
                            val bar = size.width / 7
                            for (i in 0 until 5) {
                                val h = size.height * (.3f + .7f * i / 4)
                                drawRoundRect(ink.copy(alpha = if (i < activeDots) 1f else faint(.28f)),
                                    Offset(i * bar * 1.5f, size.height - h), Size(bar, h),
                                    androidx.compose.ui.geometry.CornerRadius(bar / 2))
                            }
                        }
                        Canvas(Modifier.size(width = 26.dp, height = 12.dp)) {
                            val body = Size(size.width * .86f, size.height)
                            drawRoundRect(ink.copy(alpha = if (onLight) .75f else .55f), Offset.Zero, body, androidx.compose.ui.geometry.CornerRadius(size.height * .3f),
                                style = Stroke(size.height * .1f))
                            drawRoundRect(ink.copy(alpha = if (onLight) .75f else .55f), Offset(body.width + size.width * .03f, size.height * .3f),
                                Size(size.width * .08f, size.height * .4f), androidx.compose.ui.geometry.CornerRadius(size.height * .1f))
                            status.battery?.let { level ->
                                val inset = size.height * .18f
                                drawRoundRect(batteryColor, Offset(inset, inset), Size((body.width - inset * 2) * level / 100, size.height - inset * 2),
                                    androidx.compose.ui.geometry.CornerRadius(size.height * .15f))
                            }
                        }
                    }
                    StatusGlyph.NONE -> Unit
                }
                if (!compact && style.showBatteryPercent) Text(if (status.airplane) "Airplane" else status.battery?.let { "$it%" } ?: "—",
                    color = if (status.charging && style.colorfulBattery) charging else ink,
                    fontSize = detailSize, fontWeight = FontWeight.Medium,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            }
            if (island != null) { Spacer(Modifier.height(8.dp)); island() }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWifiFan(w: Float, wifiVisual: WifiSignalVisual, centered: Boolean = false, ink: Color = Color.White, onLight: Boolean = false) {
    val cx = size.width / 2
    val fanY = if (centered) size.height * .95f else w * .56f
    val scale = if (centered) size.height / (w * .325f + w * .04f) * .9f else 1f
    if (wifiVisual is WifiSignalVisual.Connected) {
        for (i in 1..3) {
            val r = w * (.07f + i * .075f) * scale
            drawArc(ink.copy(alpha = signalAlpha(wifiVisual.elements[i], onLight)), 225f, 90f, false,
                Offset(cx - r, fanY - r), Size(r * 2, r * 2), style = Stroke(w * .05f * scale, cap = StrokeCap.Round))
        }
        drawCircle(ink.copy(alpha = signalAlpha(wifiVisual.elements[0], onLight)), w * .04f * scale, Offset(cx, fanY))
    } else {
        drawLine(ink.copy(alpha = .7f), Offset(cx - w * .1f, fanY - w * .2f), Offset(cx + w * .1f, fanY), w * .05f, StrokeCap.Round)
    }
}

private val BatteryCharging = Color(0xFF6EE39A)
private val BatteryLow = Color(0xFFFFB35C)
/** iOS's darker system green and orange, which keep their contrast on light backgrounds. */
private val BatteryChargingOnLight = Color(0xFF248A3D)
private val BatteryLowOnLight = Color(0xFFC93400)

/** Four rising cellular bars centered in the ring, lit up to [activeDots] of 5. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCellBars(w: Float, activeDots: Int, ink: Color, onLight: Boolean = false) {
    val bar = w * .075f
    val gap = w * .045f
    val left = w / 2 - (4 * bar + 3 * gap) / 2
    val bottom = w * .66f
    for (i in 0 until 4) {
        val h = w * (.12f + i * .07f)
        drawRoundRect(ink.copy(alpha = if (i < (activeDots * 4 + 4) / 5) 1f else if (onLight) .45f else .28f), Offset(left + i * (bar + gap), bottom - h), Size(bar, h),
            androidx.compose.ui.geometry.CornerRadius(bar / 2))
    }
}

/** No connection: a dim Wi-Fi fan whose arcs light one after another, like it's looking; with a slash when still ([phase] < 0). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSearchingFan(w: Float, phase: Float, ink: Color, onLight: Boolean = false) {
    val cx = size.width / 2
    val fanY = w * .6f
    for (i in 1..3) {
        val r = w * (.07f + i * .075f)
        val lit = if (phase < 0f) 0f else (1f - kotlin.math.abs(phase * 4f - i)).coerceIn(0f, 1f)
        drawArc(ink.copy(alpha = (if (onLight) .36f else .22f) + .5f * lit), 225f, 90f, false, Offset(cx - r, fanY - r), Size(r * 2, r * 2),
            style = Stroke(w * .05f, cap = StrokeCap.Round))
    }
    drawCircle(ink.copy(alpha = .3f), w * .04f, Offset(cx, fanY))
    if (phase < 0f) drawLine(ink.copy(alpha = .7f), Offset(cx - w * .16f, fanY - w * .26f), Offset(cx + w * .16f, fanY + w * .02f), w * .045f, StrokeCap.Round)
}

private fun signalAlpha(emphasis: SignalElementEmphasis, onLight: Boolean = false): Float = when (emphasis) {
    SignalElementEmphasis.DIM -> if (onLight) .45f else .3f
    SignalElementEmphasis.NEUTRAL -> if (onLight) .75f else .62f
    SignalElementEmphasis.LIT -> 1f
}
