package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Folio has to lay out on any Android window, not just the Galaxy Z Fold it's developed on. */
class ScreenMatrixTest {
    private data class Screen(val name: String, val width: Float, val height: Float)

    /**
     * Real screen sizes in dp, not guesses. Most come from the device definitions Android Studio ships in the SDK
     * (sdklib devices.xml / nexus.xml / desktop.xml: pixels × 160 / density); the Galaxy Z Fold8 screens were measured
     * with adb (2448×1848 and 1248×1972 px at 420 dpi). Split-screen entries are half of a listed screen.
     */
    private val devices = listOf(
        Screen("Small Phone (Android Studio)", 360f, 640f), Screen("Nexus 4", 384f, 640f), Screen("Pixel 5", 393f, 851f),
        Screen("Medium Phone / Pixel 9", 411f, 923f), Screen("Pixel 9 Pro", 427f, 952f), Screen("Pixel 9 Pro XL", 448f, 997f),
        Screen("6.7\" Horizontal Fold-in (flip, open)", 360f, 879f), Screen("7.4\" Rollable", 610f, 925f),
        Screen("Galaxy Z Fold8 cover", 475f, 751f), Screen("Galaxy Z Fold8 inner", 932f, 704f),
        // Galaxy Z TriFold and Z Fold8 Ultra: pixels from Samsung's specs (via GSMArena) at an assumed 420 dpi like
        // the Fold8; no measured density is published yet, so these are estimates (the TriFold's main screen most of all).
        Screen("Galaxy Z TriFold main (estimated)", 823f, 603f), Screen("Galaxy Z TriFold cover (estimated)", 411f, 960f),
        Screen("Galaxy Z Fold8 Ultra inner (estimated)", 859f, 954f), Screen("Galaxy Z Fold8 Ultra cover (estimated)", 411f, 960f),
        Screen("Pixel Fold inner", 841f, 701f), Screen("Pixel 9 Pro Fold inner", 852f, 883f), Screen("8\" Fold-out", 838f, 945f),
        Screen("7\" WSVGA tablet", 1024f, 600f), Screen("Nexus 7", 600f, 960f), Screen("Nexus 9", 1024f, 768f),
        Screen("Medium Tablet / Pixel Tablet", 1280f, 800f), Screen("Pixel C", 1280f, 900f),
        Screen("Small Desktop", 1366f, 768f), Screen("13.5\" Freeform", 1707f, 960f), Screen("Large Desktop", 1920f, 1080f),
    )

    /** Each device in both orientations, plus split-screen halves of the bigger ones. */
    private val screens = devices.flatMap { d ->
        listOf(d, Screen("${d.name} rotated", d.height, d.width)) +
            (if (maxOf(d.width, d.height) >= 800f) listOf(Screen("${d.name} split half", maxOf(d.width, d.height) / 2f, minOf(d.width, d.height))) else emptyList())
    }

    @Test fun `phones and foldables draw at the system density`() {
        for (s in devices.filter { maxOf(it.width, it.height) < 1000f }) {
            assertEquals(s.name, 1f, uiScale(s.width, s.height))
        }
    }

    @Test fun `big screens scale up but never past the cap`() {
        val tablet = uiScale(1280f, 800f)
        assertTrue(tablet > 1.05f)
        assertEquals(tablet, uiScale(800f, 1280f))
        assertEquals(1.45f, uiScale(1920f, 1080f))
        for (s in screens) {
            val scale = uiScale(s.width, s.height)
            assertTrue(s.name, scale in 1f..1.45f)
            // Scaling never turns a regular window compact: the layout family matches the real window.
            assertEquals(s.name, fitsRegularHomeLayout(s.width, s.height), fitsRegularHomeLayout(s.width / scale, s.height / scale))
        }
    }

    @Test fun `Home fits every window without cropping or overlapping`() {
        for (s in screens) for (labels in listOf(true, false)) {
            val scale = uiScale(s.width, s.height)
            val w = s.width / scale; val h = s.height / scale
            val status = if (fitsRegularHomeLayout(w, h)) 180f else 0f
            val g = homeGeometry(w, h, LayoutPreset(), labels, statusHeight = status)
            val tag = "${s.name} (${w.toInt()}×${h.toInt()})"
            assertTrue("$tag rows ${g.rowHeight}", g.rowHeight >= 48f)
            assertTrue("$tag dock rows", g.dockRowHeight >= 48f)
            assertTrue("$tag icon ${g.iconSize}", g.iconSize >= 32f)
            assertTrue("$tag home width", g.homeWidth <= w)
            if (!g.horizontalDock) assertTrue("$tag grid ${g.gridWidth} + dock beside it", g.gridWidth + LayoutPreset().sanitized().dockWidth <= g.homeWidth)
            else assertTrue("$tag grid", g.gridWidth <= w)
            assertTrue("$tag dock under the top", g.dockTop >= 8f)
            if (h >= 400f) assertTrue("$tag dock ${g.dockTop}+${g.dockHeight} inside $h", g.dockTop + g.dockHeight <= h)
            // Two Home panels only when there's room for both; tall roomy windows get the bottom dock bar.
            assertFalse("$tag expanded and bar", g.expanded && g.horizontalDock)
            if (g.expanded) assertTrue(tag, w >= 650f && w > h)
        }
    }
}
