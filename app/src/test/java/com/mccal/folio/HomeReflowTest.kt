package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeReflowTest {
    private fun page(vararg filled: Int) = List(HOME_CELLS) { it in filled }
    private fun readingOrder(r: HomeReflow) = r.cells.entries.sortedWith(compareBy({ it.value.second }, { it.value.first })).map { it.key }

    @Test fun `apps keep their order and are neither lost nor duplicated`() {
        val filled = intArrayOf(8, 9, 10, 11, 12, 13, 14, 15, 16, 21, 30, 35)
        val r = reflowHomePage(page(*filled), emptyList(), 5)
        assertEquals(filled.toList(), readingOrder(r))
        assertEquals(filled.size, r.cells.values.toSet().size)
        assertTrue(r.cells.values.all { it.first in 0 until 5 })
    }

    @Test fun `four columns round-trips to the same cells`() {
        val filled = intArrayOf(8, 9, 11, 14, 20)
        val r = reflowHomePage(page(*filled), emptyList(), 4)
        // Packing closes gaps but keeps the order; every cell is still distinct.
        assertEquals(filled.toList(), readingOrder(r))
        assertEquals(filled.size, r.cells.values.distinct().size)
    }

    @Test fun `wider rows need fewer rows`() {
        val filled = (8 until 8 + 20).toList().toIntArray()
        val r = reflowHomePage(page(*filled), emptyList(), 5)
        assertEquals(2 + 4, r.rows)
    }

    @Test fun `widgets keep their place and apps flow around them`() {
        val widgets = listOf(WidgetPlacement(0, WEATHER_WIDGET, 0, 0, 0, 2, 2), WidgetPlacement(1, LOCATION_WIDGET, 0, 2, 0, 2, 2),
            WidgetPlacement(2, UP_NEXT_WIDGET, 0, 0, 3, 4, 2))
        val filled = intArrayOf(8, 9, 10, 11, 20, 21, 22, 23)
        val r = reflowHomePage(page(*filled), widgets, 5)
        assertEquals(0 to 0, r.widgets[0]); assertEquals(2 to 0, r.widgets[1]); assertEquals(0 to 3, r.widgets[2])
        val widgetCells = widgets.flatMap { w -> val (c, row) = r.widgets.getValue(w.slot); (0 until w.spanY).flatMap { y -> (0 until w.spanX).map { x -> (c + x) to (row + y) } } }.toSet()
        assertTrue(r.cells.values.none { it in widgetCells })
        // No app climbs into the widget-only top rows, even though column 4 is free there.
        assertTrue(r.cells.values.all { it.second >= 2 })
        assertEquals(filled.toList(), readingOrder(r))
    }

    @Test fun `a widget wider than the room left is pulled in`() {
        val r = reflowHomePage(page(), listOf(WidgetPlacement(0, WEATHER_WIDGET, 0, 3, 0, 1, 2)), 5)
        assertEquals(3 to 0, r.widgets[0])
        val wide = reflowHomePage(page(), listOf(WidgetPlacement(0, WEATHER_WIDGET, 0, 0, 0, 4, 2)), 5)
        assertEquals(0 to 0, wide.widgets[0])
    }

    @Test fun `folders count as items like apps`() {
        // The reflow only sees occupied cells; a folder's cell is occupied the same way an app's is.
        val r = reflowHomePage(page(8, 12), emptyList(), 5)
        assertEquals(listOf(8, 12), readingOrder(r))
        assertEquals(1 to 2, r.cells[12])
    }
}
