package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class LensRectTest {
    @Test fun tallCutoutKeepsBottomSquare() {
        val r = lensLtrb(intArrayOf(500, 0, 590, 160))
        assertEquals(70, r[1]); assertEquals(160, r[3])
    }
    @Test fun roundCutoutUnchanged() {
        assertEquals(30, lensLtrb(intArrayOf(500, 30, 590, 120))[1])
    }
}
