package com.mccal.folio.market

import java.util.Base64

/**
 * Early access to features that haven't shipped yet, for people who support Folio.
 *
 * A code is a short signed token — `<payload>.<signature>` — checked on the phone against a public key built into the
 * app. There is no account, no server call, and nothing about who paid: Folio stores the code and the date it runs
 * out, and that's all. A code can be shared, and that's fine; it's a thank-you, not a licence.
 *
 * The payload is `folio-early:<feature>:<expires>`, where `expires` is a Unix time in seconds (0 means it never
 * runs out) - the same clock [check] compares against. `tools/folio-code.py` mints them.
 *
 * The key in [PUBLIC_KEY] is Folio's own. A build with no key refuses every code, which is the safe way round: a
 * placeholder key would let anyone mint their own.
 */
class EarlyAccess(
    private val store: KeyValueStore,
    private val publicKey: SourceKey? = PUBLIC_KEY?.let(SourceKey::parse),
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    /** Whether a code the user has redeemed is still valid for [feature]. */
    fun has(feature: String): Boolean = store.get(KEY)?.let { check(it, feature) is Result.Valid } == true

    /** Checks a code and remembers it when it's good. The reason is the same one the user sees. */
    fun redeem(code: String, feature: String): Result {
        val result = check(code, feature)
        if (result is Result.Valid) store.set(KEY, code.trim())
        return result
    }

    fun forget() = store.set(KEY, null)

    /** The day the stored code runs out, or null when there isn't one (or it never does). */
    fun expires(): Long? = store.get(KEY)?.let { payloadOf(it) }?.expires?.takeIf { it > 0 }

    fun check(code: String, feature: String): Result {
        val key = publicKey ?: return Result.NoKey
        val trimmed = code.trim()
        if (trimmed.length > MAX_CODE) return Result.Invalid
        val dot = trimmed.lastIndexOf('.')
        if (dot <= 0) return Result.Invalid
        val payload = trimmed.substring(0, dot)
        if (!key.verifies(payload.toByteArray(), trimmed.substring(dot + 1))) return Result.Invalid
        val parsed = payloadOf(trimmed) ?: return Result.Invalid
        if (parsed.feature != feature && parsed.feature != "*") return Result.WrongFeature
        if (parsed.expires > 0 && clock() > parsed.expires) return Result.Expired
        return Result.Valid(parsed.expires)
    }

    private fun payloadOf(code: String): Payload? {
        val payload = code.trim().substringBeforeLast('.')
        val parts = payload.split(':')
        if (parts.size != 3 || parts[0] != PREFIX) return null
        val expires = parts[2].toLongOrNull() ?: return null
        return Payload(parts[1], expires)
    }

    private data class Payload(val feature: String, val expires: Long)

    sealed interface Result {
        /** Good. [expires] is 0 when the code never runs out. */
        data class Valid(val expires: Long) : Result

        /** Not a code Folio made: wrong signature, wrong shape, or edited. */
        data object Invalid : Result
        data object Expired : Result
        data object WrongFeature : Result

        /** This build has no supporter key, so no code can be checked. */
        data object NoKey : Result

        val message: String
            get() = when (this) {
                is Valid -> "Thanks — early access is on."
                Invalid -> "That code isn't one of Folio's."
                Expired -> "That code has run out."
                WrongFeature -> "That code is for something else."
                NoKey -> "This build can't check codes yet."
            }
    }

    companion object {
        /** What a Market early-access code names. */
        const val MARKET = "market"
        const val PREFIX = "folio-early"
        private const val KEY = "market:early-access"
        private const val MAX_CODE = 512

        /**
         * The supporter key, base64 SPKI (ECDSA P-256). Its private half lives in `~/.folio/folio-supporter.pem` on
         * McCal's Mac and nowhere else; `tools/folio-code.py` signs codes with it. Changing this key stops every code
         * already handed out from working.
         *
         * Fingerprint: 6423 2915 002A CAD7 FD07 CC99 AC27 B99F
         */
        val PUBLIC_KEY: String? =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE9Be9dZyOCQ4YD4DQkNV9F8mnZfPZWizr597wQXtXLdl97pBc6PfSgI+8JLh+DRWUPwkiZyfHk8dBE4AqU2T5Uw=="
    }
}
