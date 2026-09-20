package com.mccal.folio

import com.mccal.folio.market.SourceKey
import java.time.LocalDate

/**
 * Supporter codes. A code is a short signed ticket: Folio checks it against a public key built into the app, so
 * redeeming works offline, needs no account, and tells nobody that you supported. Only a code signed with McCal's
 * private key verifies, and Folio keeps the code itself and nothing else.
 *
 * Folio is open source, so this is a thank-you, not a lock: anyone can build the app themselves. The codes exist so
 * the official builds can give supporters their early access without asking who anyone is.
 */
internal object BetaCodes {
    /** What a code can unlock. The names are what feature code asks for, so they outlive any wording in Settings. */
    const val SCOPE_BETA = "beta"    // new features a release or two early
    const val SCOPE_LOOK = "look"    // personalization extras
    const val SCOPE_POWER = "power"  // power-user automation
    // Keyd, the keyboard. The name stays "keys": it is the label for bit 3, its position is what a code actually
    // carries, and it has to match scripts/beta-code.py's SCOPES list exactly. Renaming it buys nothing and a
    // mismatch would read every code's scopes wrong, silently.
    const val SCOPE_KEYS = "keys"

    private val SCOPE_BITS = listOf(SCOPE_BETA, SCOPE_LOOK, SCOPE_POWER, SCOPE_KEYS)

    /** Day 0 of the expiry field, so two bytes cover well past any plan of mine. */
    private val EPOCH: Long = LocalDate.of(2026, 1, 1).toEpochDay()

    private const val VERSION = 1
    private const val PAYLOAD = 9
    private const val SIGNATURE = 64

    /** Crockford's base32: no I, L, O or U, so a typed code can't be read wrong. */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    internal data class Code(val scopes: Set<String>, val tier: Int, val expiryDay: Int, val serial: Long) {
        /** The last day this code works, or null when it never expires. */
        val expires: LocalDate? get() = expiryDay.takeIf { it > 0 }?.let { LocalDate.ofEpochDay(EPOCH + it) }
        fun expired(today: LocalDate = LocalDate.now()): Boolean = expires?.isBefore(today) == true
    }

    internal sealed interface Result {
        data class Valid(val code: Code) : Result
        data class Expired(val code: Code) : Result
        /** The text isn't a Folio code at all (wrong length, stray characters, a newer format). */
        data object Unreadable : Result
        /** Well-formed, but not signed by Folio: a typo, or a code from somewhere else. */
        data object NotOurs : Result
        /** Ours, but retired: a code that went around publicly, or one a refund took back. */
        data object Withdrawn : Result
    }

    fun verify(text: String, keys: List<String>, today: LocalDate = LocalDate.now(),
        withdrawn: Set<Long> = emptySet()): Result {
        val bytes = decode(text) ?: return Result.Unreadable
        if (bytes.size != PAYLOAD + SIGNATURE || bytes[0].toInt() != VERSION) return Result.Unreadable
        val payload = bytes.copyOfRange(0, PAYLOAD)
        val signature = derSignature(bytes.copyOfRange(PAYLOAD, bytes.size)) ?: return Result.Unreadable
        val signed = keys.any { key -> runCatching { verifyWith(key, payload, signature) }.getOrDefault(false) }
        if (!signed) return Result.NotOurs
        val scopes = SCOPE_BITS.filterIndexed { bit, _ -> payload[1].toInt() shr bit and 1 == 1 }.toSet()
        val code = Code(scopes, payload[2].toInt() and 0xFF,
            (payload[3].toInt() and 0xFF shl 8) or (payload[4].toInt() and 0xFF),
            payload.copyOfRange(5, 9).fold(0L) { total, b -> total shl 8 or (b.toLong() and 0xFF) })
        return when {
            code.serial in withdrawn -> Result.Withdrawn
            code.expired(today) -> Result.Expired(code)
            else -> Result.Valid(code)
        }
    }

    /**
     * The same reader the Market uses for a source's key, rather than a second one: ECDSA P-256 with SHA-256,
     * an SPKI key in base64, and a signature in base64. That one is fuzzed and checked against openssl, and a
     * supporter code is not the place to keep a private copy of the same few lines.
     */
    private fun verifyWith(key: String, payload: ByteArray, signature: ByteArray): Boolean =
        SourceKey.parse(key)?.verifies(payload, java.util.Base64.getEncoder().encodeToString(signature)) == true

    /** Groups, spaces and lower case are all fine: people copy codes out of email. */
    internal fun decode(text: String): ByteArray? {
        val clean = text.uppercase().filter { it != '-' && !it.isWhitespace() }
            .map { if (it == 'I' || it == 'L') '1' else if (it == 'O') '0' else it }
        if (clean.isEmpty()) return null
        var buffer = 0L
        var bits = 0
        val out = java.io.ByteArrayOutputStream()
        for (c in clean) {
            val value = ALPHABET.indexOf(c).takeIf { it >= 0 } ?: return null
            buffer = buffer shl 5 or value.toLong()
            bits += 5
            if (bits >= 8) { bits -= 8; out.write((buffer shr bits and 0xFF).toInt()) }
        }
        return out.toByteArray()
    }

    /** Codes carry the raw r‖s pair; Java wants it wrapped as ASN.1 before it will look at it. */
    private fun derSignature(raw: ByteArray): ByteArray? {
        if (raw.size != SIGNATURE) return null
        val r = asn1Integer(raw.copyOfRange(0, 32))
        val s = asn1Integer(raw.copyOfRange(32, 64))
        return byteArrayOf(0x30, (r.size + s.size).toByte()) + r + s
    }

    private fun asn1Integer(value: ByteArray): ByteArray {
        var start = 0
        while (start < value.size - 1 && value[start] == 0.toByte()) start++
        val trimmed = value.copyOfRange(start, value.size)
        val body = if (trimmed[0].toInt() and 0x80 != 0) byteArrayOf(0) + trimmed else trimmed
        return byteArrayOf(0x02, body.size.toByte()) + body
    }

    /** How a code reads back to the person who typed it: FOLIO-XXXXX-XXXXX-… */
    internal fun group(text: String): String = text.uppercase().filter { it.isLetterOrDigit() }
        .chunked(5).joinToString("-")
}
