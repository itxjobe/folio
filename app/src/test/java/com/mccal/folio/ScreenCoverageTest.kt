package com.mccal.folio

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screen Coverage v2: Folio supports Android windows, not a list of devices. Named devices live in [ScreenMatrixTest];
 * this sweeps the whole phone-to-tablet range and the breakpoint edges, checking the rules every Home layout keeps.
 */
class ScreenCoverageTest {
    private val probe = System.getenv("FOLIO_COVERAGE_PROBE") != null

    /** The rules for one window. Returns what broke (empty when the layout is fine). */
    private fun problems(w: Float, h: Float, preset: LayoutPreset = LayoutPreset(), labels: Boolean = true,
        labelHeight: Float = 20f, status: Float = 160f): List<String> {
        val g = homeGeometry(w, h, preset, labels, statusHeight = status, labelHeight = labelHeight)
        val out = mutableListOf<String>()
        val numbers = listOf(g.homeWidth, g.gridWidth, g.iconSize, g.rowHeight, g.widgetHeight, g.contentTop, g.dockTop,
            g.dockHeight, g.dockRowHeight, g.cellWidth, g.statusTop)
        if (numbers.any { it.isNaN() || it.isInfinite() || it < 0f }) out += "invalid number"
        if (g.iconSize < 32f) out += "icon ${g.iconSize}"
        if (g.rowHeight < 48f) out += "row ${g.rowHeight}"
        if (g.dockRowHeight < 48f) out += "dock row ${g.dockRowHeight}"
        if (g.homeWidth > w + .5f) out += "home wider than window"
        val side = if (g.horizontalDock && !g.dockBesideRail) 0f else preset.sanitized().dockWidth + 28f
        if (g.gridWidth + side > (if (g.expanded) g.homeWidth + 16f else w) + .5f) out += "grid ${g.gridWidth} too wide"
        val bottom = 44f + if (g.horizontalDock) g.dockBarHeight + 16f else 0f
        val page = if (g.splitColumns) maxOf(g.widgetHeight + 18f + 2f * g.rowHeight, 3f * g.rowHeight)
            else g.widgetHeight + 18f + 4f * g.rowHeight
        // Tier A (480 dp or taller, text up to 1.3×): the whole page fits. Shorter windows and larger text scroll the
        // page instead (HomeWorkspace), like Android asks for, so only the touch-target rules apply there.
        val mustFit = h >= 480f && labelHeight <= 26f
        if (mustFit && g.contentTop + page > h - bottom + .5f) out += "page ${g.contentTop + page} under controls at ${h - bottom}"
        if (!g.horizontalDock && g.dockTop + g.dockHeight > h + .5f) out += "dock off screen"
        if (g.dockTop < 8f) out += "dock above the top"
        // Two Home panels need a landscape or square window at least 650 dp wide.
        if (g.expanded && !(w >= 650f && w >= h)) out += "expanded in a narrow window"
        return out
    }

    private fun sweep(label: String, cases: Sequence<Triple<Float, Float, () -> List<String>>>) {
        val failures = cases.mapNotNull { (w, h, check) -> check().takeIf { it.isNotEmpty() }?.let { "${w.toInt()}×${h.toInt()}: ${it.joinToString()}" } }.toList()
        if (probe) {
            println("[$label] ${failures.size} failures")
            val parsed = failures.flatMap { f ->
                val (size, rest) = f.split(": ", limit = 2)
                val (fw, fh) = size.split("×").map { it.toInt() }
                rest.split(", ").map { Triple(it.takeWhile { c -> !c.isDigit() }.trim(), fw, fh) }
            }
            parsed.groupBy { it.first }.forEach { (k, v) ->
                println("  ${v.size}× '$k' w ${v.minOf { it.second }}..${v.maxOf { it.second }} h ${v.minOf { it.third }}..${v.maxOf { it.third }}")
                v.groupBy { it.third / 40 * 40 }.toSortedMap().forEach { (hb, vv) -> println("      h≈$hb: w ${vv.minOf { it.second }}..${vv.maxOf { it.second }} (${vv.size})") }
            }
        }
        assertTrue("$label: ${failures.size} windows broke, e.g.\n${failures.take(12).joinToString("\n")}", failures.isEmpty())
    }

    /** Tier A and B: every window at least 320 dp wide and tall, from small phones to large tablets. */
    @Test fun `sweep of phone to tablet windows keeps every layout rule`() = sweep("sweep", sequence {
        for (w in 320..1600 step 8) for (h in 320..1200 step 8) yield(Triple(w.toFloat(), h.toFloat()) { problems(w.toFloat(), h.toFloat()) })
    })

    @Test fun `options and large text keep the rules across the sweep`() = sweep("options", sequence {
        val presets = listOf(LayoutPreset(dockPlacement = DockPlacement.BOTTOM), LayoutPreset(dockPlacement = DockPlacement.SIDE),
            LayoutPreset(statusAlignToGrid = false, statusPosition = 1f), LayoutPreset(dockAlignToGrid = false, dockPosition = 1f),
            LayoutPreset(iconSize = 40f, rowGap = 28f), LayoutPreset(iconSize = 68f, rowGap = 28f, dockWidth = 84f))
        for (w in 320..1600 step 24) for (h in 320..1200 step 24) {
            for (p in presets) yield(Triple(w.toFloat(), h.toFloat()) { problems(w.toFloat(), h.toFloat(), p) })
            // Hidden labels, and label text at 1.3×, 1.5× and 2× font scale.
            for ((labels, lh) in listOf(false to 20f, true to 26f, true to 30f, true to 40f))
                yield(Triple(w.toFloat(), h.toFloat()) { problems(w.toFloat(), h.toFloat(), labels = labels, labelHeight = lh) })
            yield(Triple(w.toFloat(), h.toFloat()) { problems(w.toFloat(), h.toFloat(), status = 0f) })
        }
    })

    /** Both sides of each breakpoint, where a one-dp change flips the layout family. */
    @Test fun `breakpoint edges keep the rules`() = sweep("edges", sequence {
        val widths = listOf(319f, 320f, 359f, 360f, 599f, 600f, 601f, 649f, 650f, 651f, 839f, 840f, 841f, 1199f, 1200f, 1201f)
        val heights = listOf(479f, 480f, 481f, 559f, 560f, 561f, 899f, 900f, 901f)
        for (w in widths) for (h in heights) {
            yield(Triple(w, h) { problems(w, h) }); yield(Triple(h, w) { problems(h, w) })
        }
    })

    /** Flip phones, book-fold covers and inner screens, and tri-fold-sized windows, in both orientations. */
    @Test fun `flip, fold and tri-fold windows keep the rules`() = sweep("folds", sequence {
        val sizes = listOf(
            // Flip inner screens (tall and narrow).
            360f to 879f, 384f to 900f, 412f to 915f, 430f to 1000f,
            // Flip cover screens (square-ish, Tier C below 320).
            320f to 320f, 360f to 360f, 412f to 412f, 480f to 360f,
            // Book-fold covers.
            320f to 780f, 344f to 840f, 360f to 850f, 393f to 900f, 430f to 900f, 475f to 751f,
            // Book-fold inner screens across the 600 and 840 breakpoints.
            600f to 700f, 650f to 700f, 700f to 700f, 700f to 840f, 704f to 930f, 800f to 700f, 838f to 945f,
            840f to 700f, 841f to 701f, 852f to 883f, 932f to 704f, 1000f to 800f,
            // Tri-folds and other very large unfolded screens.
            1200f to 900f, 1350f to 900f, 1500f to 1000f)
        for ((w, h) in sizes) for (labels in listOf(true, false)) {
            yield(Triple(w, h) { problems(w, h, labels = labels) }); yield(Triple(h, w) { problems(h, w, labels = labels) })
        }
    })

    /** Tier C: micro flip covers still produce a valid layout (touch targets kept, the page scrolls). */
    @Test fun `micro cover windows stay valid`() = sweep("micro", sequence {
        for ((w, h) in listOf(240f to 260f, 260f to 240f, 280f to 280f, 300f to 300f))
            yield(Triple(w, h) { problems(w, h).filter { it.startsWith("invalid") || it.startsWith("dock row") || it.startsWith("row") } })
    })

    /** A book-style hinge anywhere across a tall page: rows under it move below it, and a widget is never split. */
    @Test fun `a hinge anywhere across the page moves whole rows below it`() {
        for ((w, h) in listOf(704f to 930f, 600f to 900f, 852f to 1100f, 700f to 1200f)) {
            val g = homeGeometry(w, h, LayoutPreset(), true)
            val widgets = listOf(0 to 2)
            val cells = HomeCellLayout.forPage(g, widgets)
            val top = g.contentTop
            var hinge = top
            while (hinge < top + cells.height(GRID_ROWS)) {
                val result = foldDisplacement(cells, GRID_ROWS, top, hinge, hinge + 24f, widgets)
                if (result != null) {
                    val (row, shift) = result
                    assertTrue("${w}×$h hinge $hinge: row $row lands below", top + cells.y(row) + shift >= hinge + 24f - .5f)
                    assertTrue("${w}×$h hinge $hinge: widget split", row == 0 || row >= 2)
                }
                hinge += 6f
            }
        }
    }
}
