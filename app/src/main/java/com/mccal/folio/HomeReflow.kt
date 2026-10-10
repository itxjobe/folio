package com.mccal.folio

/**
 * Per-display columns: the saved Home stays one 4-column layout (the cover screen's, and the one every edit works
 * on), and the inner screen shows it reflowed into more columns, like iPadOS. Nothing is rewritten in storage, so a
 * reflow can't lose or duplicate an app: each cell maps to exactly one new place, in the same reading order.
 *
 * Rules: widgets keep their row and column (pulled left if they'd overflow); apps and folders keep their reading
 * order and fill the wider rows, never going above the first row that held one or into a widget-only row.
 */
internal data class HomeReflow(
    val columns: Int,
    /** Page-local 4-column cell index to its (column, row) in [columns] columns. */
    val cells: Map<Int, Pair<Int, Int>>,
    /** Widget slot to its (column, row). */
    val widgets: Map<Int, Pair<Int, Int>>,
    /** Rows the reflowed page uses. */
    val rows: Int,
)

internal fun reflowHomePage(occupied: List<Boolean>, widgets: List<WidgetPlacement>, columns: Int,
    fromColumns: Int = GRID_COLUMNS): HomeReflow {
    require(columns >= fromColumns)
    val widgetPos = widgets.associate { w -> w.slot to ((w.column.coerceAtMost(columns - w.spanX)).coerceAtLeast(0) to w.row) }
    val taken = HashSet<Pair<Int, Int>>()
    widgets.forEach { w -> val (c, r) = widgetPos.getValue(w.slot); for (y in 0 until w.spanY) for (x in 0 until w.spanX) taken += (c + x) to (r + y) }
    val widgetRows = widgets.flatMap { w -> w.row until w.row + w.spanY }.toSet()
    val items = occupied.indices.filter { occupied[it] }
    val appRows = items.map { it / fromColumns }.toSet()
    val firstRow = items.minOfOrNull { it / fromColumns } ?: 0
    fun allowed(r: Int) = r >= firstRow && (r !in widgetRows || r in appRows)
    val cells = LinkedHashMap<Int, Pair<Int, Int>>()
    var c = 0; var r = firstRow
    for (index in items) {
        while (!allowed(r) || (c to r) in taken) { c++; if (c >= columns) { c = 0; r++ } }
        cells[index] = c to r
        taken += c to r
        c++; if (c >= columns) { c = 0; r++ }
    }
    val rows = maxOf(cells.values.maxOfOrNull { it.second + 1 } ?: 0, widgets.maxOfOrNull { it.row + it.spanY } ?: 0)
    return HomeReflow(columns, cells, widgetPos, rows)
}
