package com.mccal.folio

import org.junit.Test

/** Writes Home's layout at a few window sizes to JSON for review mockups (only when FOLIO_GEOMETRY_OUT is set). */
class GeometryExportTest {
    @Test fun export() {
        val out = System.getenv("FOLIO_GEOMETRY_OUT") ?: return
        val cases = listOf(
            Triple("Small phone", 360f to 640f, DockPlacement.AUTOMATIC), Triple("Pixel 9", 411f to 923f, DockPlacement.AUTOMATIC),
            Triple("Fold8 cover", 475f to 751f, DockPlacement.AUTOMATIC), Triple("Fold8 cover, dock at bottom", 475f to 751f, DockPlacement.BOTTOM),
            Triple("Phone landscape", 851f to 393f, DockPlacement.AUTOMATIC), Triple("Split screen half, dock at bottom", 411f to 460f, DockPlacement.BOTTOM),
            Triple("Flip phone inner", 360f to 879f, DockPlacement.AUTOMATIC), Triple("Fold8 inner", 932f to 704f, DockPlacement.AUTOMATIC),
            Triple("Pixel 9 Pro Fold inner", 852f to 883f, DockPlacement.AUTOMATIC), Triple("Tri-fold open", 1350f to 900f, DockPlacement.AUTOMATIC))
        val json = cases.joinToString(",\n", "[\n", "\n]") { (name, size, dock) ->
            val (w, h) = size
            val g = homeGeometry(w, h, LayoutPreset(dockPlacement = dock), true, statusHeight = 160f)
            val cells = HomeCellLayout.forPage(g, listOf(0 to 2))
            val icons = (0 until 16).map { i -> val row = 2 + i / 4; "[${cells.x(i % 4, row)},${cells.y(row)}]" }
            """{"name":"$name","w":$w,"h":$h,"dock":"$dock","expanded":${g.expanded},"split":${g.splitColumns},"horizontalDock":${g.horizontalDock},""" +
                """"besideRail":${g.dockBesideRail},"homeWidth":${g.homeWidth},"gridWidth":${g.gridWidth},"icon":${g.iconSize},"row":${g.rowHeight},""" +
                """"widget":${g.widgetHeight},"contentTop":${g.contentTop},"statusTop":${g.statusTop},"dockTop":${g.dockTop},"dockHeight":${g.dockHeight},""" +
                """"dockBar":${g.dockBarHeight},"cell":${g.cellWidth},"widgetY":${cells.y(0)},"icons":[${icons.joinToString(",")}]}"""
        }
        java.io.File(out).writeText(json)
    }
}
