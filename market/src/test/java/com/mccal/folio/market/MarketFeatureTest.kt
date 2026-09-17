package com.mccal.folio.market

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketFeatureTest {
    @Test fun `Folio Dev shows the Market before release`() {
        assertTrue(MarketFeature.isEnabled("com.mccal.folio.dev"))
    }

    @Test fun `the signed release hides it until 0_7_0 ships`() {
        assertFalse(MarketFeature.RELEASED)
        assertFalse(MarketFeature.isEnabled("com.mccal.folio"))
    }
}
