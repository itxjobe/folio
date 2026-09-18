package com.mccal.folio.market

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Codes minted by `tools/folio-code.py` against the codes Folio accepts.
 *
 * The tool signs with openssl and the phone checks with `java.security`, so this is the same interop guard as the
 * source signatures: if it broke, every code already handed out would stop working with no way to tell why. The
 * fixture was made with a throwaway key.
 */
class SupporterCodeTest {
    private val fixture = JSONObject(
        File(javaClass.classLoader.getResource("openssl/codes.json")!!.toURI()).readText(),
    )
    private val key = SourceKey.parse(fixture.getString("publicKey"))
    private val store = MemoryStore()
    private var now = System.currentTimeMillis() / 1000

    private fun access() = EarlyAccess(store, key) { now }

    @Test fun `a code the tool minted opens the Market`() {
        val early = access()
        val result = early.redeem(fixture.getString("market"), EarlyAccess.MARKET)
        assertTrue("$result", result is EarlyAccess.Result.Valid)
        assertTrue(early.has(EarlyAccess.MARKET))
        // It's remembered, so the next start doesn't ask again.
        assertTrue(access().has(EarlyAccess.MARKET))
        early.forget()
        assertTrue(!access().has(EarlyAccess.MARKET))
    }

    @Test fun `a code for something else, one that has run out, and one somebody edited are all refused`() {
        val early = access()
        assertEquals(EarlyAccess.Result.WrongFeature, early.check(fixture.getString("market"), "keyboard"))
        assertEquals(EarlyAccess.Result.Expired, early.check(fixture.getString("expired"), EarlyAccess.MARKET))

        // Moving the expiry date without the key doesn't work, which is the whole point of signing it.
        val edited = fixture.getString("expired").replace(Regex(":\\d+\\."), ":0.")
        assertEquals(EarlyAccess.Result.Invalid, early.check(edited, EarlyAccess.MARKET))
        assertTrue(!early.has(EarlyAccess.MARKET))
    }

    @Test fun `a build with no key refuses everything, rather than taking anything`() {
        val noKey = EarlyAccess(MemoryStore(), publicKey = null)
        assertEquals(EarlyAccess.Result.NoKey, noKey.check(fixture.getString("market"), EarlyAccess.MARKET))
        assertTrue(!noKey.has(EarlyAccess.MARKET))
    }
}
