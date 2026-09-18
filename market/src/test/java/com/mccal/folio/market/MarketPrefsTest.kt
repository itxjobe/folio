package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketPrefsTest {
    @Test fun `the carousel is the default, and a choice sticks`() {
        val prefs = MarketPrefs(MemoryStore())
        assertEquals(FeaturedStyle.CAROUSEL, prefs.featuredStyle)
        assertTrue(!prefs.introductionSeen)
        prefs.featuredStyle = FeaturedStyle.CALM
        prefs.introductionSeen = true
        assertEquals(FeaturedStyle.CALM, prefs.featuredStyle)
        assertTrue(prefs.introductionSeen)
    }

    @Test fun `an unreadable value falls back to the carousel rather than failing`() {
        val store = MemoryStore()
        store.set("market:featured-style", "hologram")
        assertEquals(FeaturedStyle.CAROUSEL, MarketPrefs(store).featuredStyle)
    }

    @Test fun `the choice survives a restart`() {
        val dir = java.nio.file.Files.createTempDirectory("folio-prefs").toFile().also { it.deleteOnExit() }
        MarketPrefs(FileStore(dir)).featuredStyle = FeaturedStyle.CALM
        assertEquals(FeaturedStyle.CALM, MarketPrefs(FileStore(dir)).featuredStyle)
        assertTrue("the choice is written to a file", dir.isDirectory && dir.list()!!.isNotEmpty())
    }
}
