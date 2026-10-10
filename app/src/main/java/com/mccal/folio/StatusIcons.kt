package com.mccal.folio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * iPhone's status icons, stacked under the clock: four cellular bars, the Wi-Fi fan when joined, and the battery
 * with its level. Every shape is drawn inside its own canvas with the stroke inset, so nothing is cut at the edges.
 * Proportions follow iOS (bars 17×11, Wi-Fi 15×11, battery 25×12).
 */
@Composable
internal fun IosStatusIcons(cellularBars: Int?, wifiLevel: Int?, networkType: String?, battery: Int?, ink: Color, faint: Color, batteryFill: Color, charging: Boolean = false,
    signalTap: Modifier, batteryTap: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(top = 2.dp)) {
        // Like iOS: on Wi-Fi the fan shows; off Wi-Fi the bars carry the network type (5G, LTE) beside them.
        if (cellularBars != null) androidx.compose.foundation.layout.Row(signalTap.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Canvas(Modifier.padding(vertical = 2.dp).size(width = 17.dp, height = 11.dp)) {
            val gap = size.width * .09f
            val w = (size.width - gap * 3) / 4
            for (i in 0 until 4) {
                val h = size.height * (.4f + .2f * i)
                drawRoundRect(if (i < cellularBars) ink else faint, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(w * .3f))
            }
        }
        if (wifiLevel == null && networkType != null) androidx.compose.material3.Text(networkType, color = ink,
            fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
        if (wifiLevel != null) Canvas(signalTap.padding(2.dp).size(width = 15.dp, height = 11.dp)) {
            // Three arcs and a dot fanning up from the bottom centre; unlit arcs show the signal level (0..3).
            val center = Offset(size.width / 2, size.height)
            val stroke = size.height * .17f
            for (ring in 1..3) {
                val r = size.height * (ring / 3f) - stroke / 2
                drawArc(if (wifiLevel >= ring) ink else faint, 225f, 90f, useCenter = ring == 1,
                    topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                    style = if (ring == 1) androidx.compose.ui.graphics.drawscope.Fill else Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Canvas(batteryTap.padding(2.dp).size(width = 25.dp, height = 12.dp)) {
            val line = size.height * .09f
            val body = Size(size.width * .9f, size.height)
            drawRoundRect(ink.copy(alpha = .45f), Offset(line / 2, line / 2), Size(body.width - line, body.height - line),
                CornerRadius(size.height * .3f), style = Stroke(line))
            drawRoundRect(ink.copy(alpha = .45f), Offset(body.width + size.width * .02f, size.height * .33f),
                Size(size.width * .06f, size.height * .34f), CornerRadius(size.width * .03f))
            if (battery != null) {
                val inset = line * 2.2f
                drawRoundRect(batteryFill, Offset(inset, inset), Size((body.width - inset * 2) * battery.coerceIn(0, 100) / 100f, size.height - inset * 2),
                    CornerRadius(size.height * .18f))
            }
            if (charging) {
                // iOS lightning bolt centred on the body, cut out of the fill with a thin ink outline.
                val w = body.width; val h = size.height
                val bolt = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * .56f, h * .08f); lineTo(w * .30f, h * .56f); lineTo(w * .48f, h * .56f)
                    lineTo(w * .42f, h * .92f); lineTo(w * .70f, h * .42f); lineTo(w * .52f, h * .42f); close()
                }
                drawPath(bolt, Color.Black.copy(alpha = .55f), style = Stroke(line * 1.6f))
                drawPath(bolt, Color.White)
            }
        }
    }
}
