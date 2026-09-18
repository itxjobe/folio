package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Supporter codes: checked on the phone, with nothing about who redeemed one. A code that was edited, expired, or
 * signed by anyone else is refused, and a build without the key refuses everything.
 */
class EarlyAccessTest {
    private val keys: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val key = requireNotNull(SourceKey.parse(Base64.getEncoder().encodeToString(keys.public.encoded)))
    private var now = 1_789_000_000L
    private val store = MemoryStore()
    private val access = EarlyAccess(store, key) { now }

    private fun code(feature: String = EarlyAccess.MARKET, expires: Long = 0, pair: KeyPair = keys): String {
        val payload = "${EarlyAccess.PREFIX}:$feature:$expires"
        val signature = Base64.getEncoder().encodeToString(
            Signature.getInstance(SourceKey.ALGORITHM).run { initSign(pair.private); update(payload.toByteArray()); sign() },
        )
        return "$payload.$signature"
    }

    @Test fun `a code McCal signed opens the Market, and is remembered`() {
        assertTrue(!access.has(EarlyAccess.MARKET))
        val result = access.redeem(code(), EarlyAccess.MARKET)
        assertTrue("$result", result is EarlyAccess.Result.Valid)
        assertTrue(access.has(EarlyAccess.MARKET))
        // Nothing about the person is kept: only the code itself.
        assertEquals(setOf("market:early-access"), storedKeys())
        assertNull("a code with no end date has no expiry to show", access.expires())
    }

    @Test fun `a code that ran out stops working, without being deleted behind the user's back`() {
        val soon = now + 60
        assertTrue(access.redeem(code(expires = soon), EarlyAccess.MARKET) is EarlyAccess.Result.Valid)
        assertEquals(soon, access.expires())
        now += 3600
        assertTrue(!access.has(EarlyAccess.MARKET))
        assertEquals(EarlyAccess.Result.Expired, access.check(code(expires = soon), EarlyAccess.MARKET))
    }

    @Test fun `anything not signed by Folio is refused`() {
        val theirs = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        assertEquals(EarlyAccess.Result.Invalid, access.check(code(pair = theirs), EarlyAccess.MARKET))
        // Editing the payload after signing breaks it, which is the whole point.
        val edited = code(expires = now + 60).replace("${EarlyAccess.PREFIX}:market:", "${EarlyAccess.PREFIX}:market:9999999999")
        assertEquals(EarlyAccess.Result.Invalid, access.check(edited, EarlyAccess.MARKET))
        for (junk in listOf("", "   ", "nonsense", "a.b", "folio-early:market:0", "x".repeat(600))) {
            assertEquals(junk, EarlyAccess.Result.Invalid, access.check(junk, EarlyAccess.MARKET))
        }
        assertTrue(!access.has(EarlyAccess.MARKET))
    }

    @Test fun `a code for something else doesn't open the Market, and a wildcard does`() {
        assertEquals(EarlyAccess.Result.WrongFeature, access.check(code(feature = "keyboard"), EarlyAccess.MARKET))
        assertTrue(access.check(code(feature = "*"), EarlyAccess.MARKET) is EarlyAccess.Result.Valid)
    }

    @Test fun `a build with no supporter key refuses every code`() {
        val noKey = EarlyAccess(MemoryStore(), publicKey = null) { now }
        assertEquals(EarlyAccess.Result.NoKey, noKey.check(code(), EarlyAccess.MARKET))
        assertTrue(!noKey.has(EarlyAccess.MARKET))
        // And the key that does ship is a real one: a typo here would refuse every code with no way to tell why.
        assertTrue("the built-in supporter key has to parse", SourceKey.parse(EarlyAccess.PUBLIC_KEY!!) != null)
    }

    @Test fun `forgetting a code turns it off`() {
        access.redeem(code(), EarlyAccess.MARKET)
        access.forget()
        assertTrue(!access.has(EarlyAccess.MARKET))
        assertTrue(storedKeys().isEmpty())
    }

    private fun storedKeys(): Set<String> =
        listOf("market:early-access").filter { store.get(it) != null }.toSet()
}
