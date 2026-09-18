package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Gauge's ring: the left half fills first, from the top down, then the right half from the bottom up. */
class GaugeSweepTest {
    private val side = gaugeSweeps(1f).first  // one half's full sweep, whatever the angle is set to

    @Test fun `an empty battery fills neither half`() {
        assertEquals(0f to 0f, gaugeSweeps(0f))
    }

    @Test fun `half a charge is exactly the left half`() {
        assertEquals(side to 0f, gaugeSweeps(.5f))
    }

    @Test fun `a full charge fills both halves`() {
        assertEquals(side to side, gaugeSweeps(1f))
    }

    @Test fun `a quarter fills half of the left half`() {
        assertEquals(side / 2f, gaugeSweeps(.25f).first, .001f)
        assertEquals(0f, gaugeSweeps(.25f).second, .001f)
    }

    @Test fun `three quarters fills the left half and half of the right`() {
        assertEquals(side, gaugeSweeps(.75f).first, .001f)
        assertEquals(side / 2f, gaugeSweeps(.75f).second, .001f)
    }

    @Test fun `a level outside 0 to 1 is held at the ends, never drawn past the ring`() {
        assertEquals(0f to 0f, gaugeSweeps(-.5f))
        assertEquals(side to side, gaugeSweeps(1.4f))
    }
}
