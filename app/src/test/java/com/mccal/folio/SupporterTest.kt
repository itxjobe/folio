package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.LocalDate
import java.util.Base64

/**
 * What a redeemed code opens, and the cost of asking.
 *
 * Checking a code is an ECDSA verification, about a millisecond, and Settings asks on every redraw whether to show
 * the Market row — twice over in the split view. So the answer is remembered, and these check that remembering it
 * doesn't make it wrong: a removed code stops opening things at once, and an expiry is still measured against the
 * clock rather than against whenever the answer happened to be worked out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupporterTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    /** A throwaway key, so the test never depends on the real one being present. */
    private val keys = listOf(Base64.getEncoder().encodeToString(pair.public.encoded))

    @Before fun clean() = Supporter.remove(context)

    /** The same shape `scripts/beta-code.py` mints. */
    private fun mint(scopeBits: Int, expires: LocalDate? = null, serial: Long = 7): String {
        val day = expires?.let { (it.toEpochDay() - LocalDate.of(2026, 1, 1).toEpochDay()).toInt() } ?: 0
        val payload = byteArrayOf(1, scopeBits.toByte(), 1, (day shr 8).toByte(), day.toByte()) +
            ByteArray(4) { i -> (serial shr (24 - i * 8)).toByte() }
        val der = Signature.getInstance("SHA256withECDSA").run { initSign(pair.private); update(payload); sign() }
        var i = 2
        fun part(): ByteArray {
            val size = der[i + 1].toInt()
            val v = der.copyOfRange(i + 2, i + 2 + size).dropWhile { it == 0.toByte() }.toByteArray()
            i += 2 + size
            return ByteArray(32 - v.size) + v
        }
        val bytes = payload + part() + part()
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        var buffer = 0L; var bits = 0; val out = StringBuilder()
        for (b in bytes) {
            buffer = buffer shl 8 or (b.toLong() and 0xFF); bits += 8
            while (bits >= 5) { bits -= 5; out.append(alphabet[(buffer shr bits and 31).toInt()]) }
        }
        if (bits > 0) out.append(alphabet[(buffer shl (5 - bits) and 31).toInt()])
        return out.toString()
    }

    private val beta = 1 shl 0
    private val keysScope = 1 shl 3

    @Test fun `a beta code opens the Market and the keyboard scope doesn't`() {
        assertTrue(Supporter.redeem(context, mint(beta), keys = keys) is BetaCodes.Result.Valid)
        Supporter.setBetaOn(context, true)
        assertTrue(Supporter.has(context, BetaCodes.SCOPE_BETA, keys))
        assertFalse(Supporter.has(context, BetaCodes.SCOPE_KEYS, keys))
    }

    @Test fun `redeeming a beta code switches on the features and the builds`() {
        var channel: Boolean? = null
        assertFalse("nothing is on before the code", Supporter.betaOn(context))
        Supporter.redeem(context, mint(beta), keys = keys, onBetaChannel = { channel = it })

        // Without this a supporter redeems a code and sees nothing: the switch defaults off, and since Beta Updates
        // stopped opening the Market the code is the only way in.
        assertTrue("beta features", Supporter.betaOn(context))
        assertTrue("the Market", Supporter.has(context, BetaCodes.SCOPE_BETA, keys))
        // And the builds those features arrive in, because that is what supporting buys.
        assertEquals(true, channel)
    }

    @Test fun `redeeming adds the supporter source, and removing the code takes it away`() {
        val asked = mutableListOf<Boolean>()
        Supporter.redeem(context, mint(beta), keys = keys, onBetaChannel = {}, onSupporterSource = { asked += it })
        assertEquals("added on the way in", listOf(true), asked)

        // Redeeming the same code again doesn't add it twice - it isn't the first time any more.
        Supporter.redeem(context, mint(beta), keys = keys, onBetaChannel = {}, onSupporterSource = { asked += it })
        assertEquals(listOf(true), asked)

        Supporter.remove(context) { asked += it }
        assertEquals("and goes when the code does", listOf(true, false), asked)
    }

    @Test fun `a code with no beta scope still brings the source, because it still came from supporting`() {
        val asked = mutableListOf<Boolean>()
        Supporter.redeem(context, mint(keysScope), keys = keys, onBetaChannel = {}, onSupporterSource = { asked += it })
        assertEquals(listOf(true), asked)
    }

    @Test fun `a code without the beta scope leaves both switches alone`() {
        var channel: Boolean? = null
        Supporter.redeem(context, mint(keysScope), keys = keys, onBetaChannel = { channel = it })
        assertFalse(Supporter.betaOn(context))
        assertNull("the channel is not touched", channel)
        assertTrue("but the code is still good for what it carries", Supporter.has(context, BetaCodes.SCOPE_KEYS, keys))
    }

    @Test fun `turning the betas off afterwards sticks`() {
        Supporter.redeem(context, mint(beta), keys = keys, onBetaChannel = {})
        Supporter.setBetaOn(context, false)
        // Redeeming the same code again is the only way back through that door, and it is the same code.
        Supporter.redeem(context, mint(beta), keys = keys, onBetaChannel = { error("should not touch the channel again") })
        assertFalse("still off, because the person turned it off", Supporter.betaOn(context))
    }

    @Test fun `removing a code takes effect at once, and doesn't leave the old answer behind`() {
        Supporter.redeem(context, mint(beta or keysScope), keys = keys)
        Supporter.setBetaOn(context, true)
        assertTrue(Supporter.has(context, BetaCodes.SCOPE_BETA, keys))
        Supporter.remove(context)
        assertNull(Supporter.code(context, keys = keys))
        assertFalse(Supporter.has(context, BetaCodes.SCOPE_BETA, keys))
        assertFalse(Supporter.has(context, BetaCodes.SCOPE_KEYS, keys))
    }

    @Test fun `a second code replaces the first`() {
        Supporter.redeem(context, mint(beta), keys = keys)
        Supporter.setBetaOn(context, true)
        assertEquals(setOf(BetaCodes.SCOPE_BETA), Supporter.code(context, keys = keys)?.scopes)
        assertTrue(Supporter.redeem(context, mint(beta or keysScope, serial = 9), keys = keys) is BetaCodes.Result.Valid)
        assertEquals(setOf(BetaCodes.SCOPE_BETA, BetaCodes.SCOPE_KEYS), Supporter.code(context, keys = keys)?.scopes)
        assertTrue(Supporter.has(context, BetaCodes.SCOPE_KEYS, keys))
    }

    @Test fun `an expiry is measured against the day, not against when the answer was worked out`() {
        val last = LocalDate.of(2027, 3, 1)
        assertTrue(Supporter.redeem(context, mint(beta, expires = last), last, keys) is BetaCodes.Result.Valid)
        // Asked on the last day it works, then asked again the morning after: the remembered answer can't carry it over.
        assertNotNull(Supporter.code(context, last, keys))
        assertNull(Supporter.code(context, last.plusDays(1), keys))
        assertNotNull("and it comes back if the clock goes back", Supporter.code(context, last, keys))
    }

    @Test fun `asking again doesn't check the signature again`() {
        Supporter.redeem(context, mint(beta), keys = keys)
        Supporter.setBetaOn(context, true)
        val other = listOf(Base64.getEncoder().encodeToString(
            KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
                .generateKeyPair().public.encoded))

        fun time(block: () -> Unit): Long {
            repeat(20) { block() }
            val start = System.nanoTime()
            repeat(100) { block() }
            return System.nanoTime() - start
        }
        // Alternating the key list defeats the cache, so this is what a hundred real checks cost.
        var flip = false
        val checking = time { flip = !flip; Supporter.code(context, keys = if (flip) keys else other) }
        val remembered = time { Supporter.code(context, keys = keys) }

        // Only that it is faster, not by how much. The remembered path still reads the preference and checks the
        // expiry against today, so the saving is the signature and nothing else - about three times on this
        // machine, and a tighter bound than "faster" is a flaky test rather than a stronger claim, especially
        // with other builds running beside it.
        assertTrue(
            "a hundred repeats took ${remembered / 1000}us; a hundred real checks take ${checking / 1000}us",
            remembered < checking,
        )
    }

    @Test fun `the supporter source's key is a key Folio can use`() {
        // A typo here doesn't fail loudly: SourceKey.parse returns null, addSupporterSource quietly does nothing, and
        // every supporter redeems a code and gets no source.
        org.junit.Assert.assertNotNull(com.mccal.folio.market.SourceKey.parse(SUPPORTER_SOURCE_KEY))
    }
}
