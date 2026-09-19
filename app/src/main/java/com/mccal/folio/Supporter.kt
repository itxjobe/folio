package com.mccal.folio

import android.content.Context
import java.time.LocalDate

/**
 * What a redeemed supporter code unlocks, kept next to Folio's other small local settings. Nothing here leaves the
 * phone: the code is verified on the device and stored as typed, so it can be shown, checked again after an update,
 * or removed.
 *
 * Beta features are opt-in even with a code. Early access means more bugs than usual, so turning the switch off is
 * always one tap away and puts every beta feature back to how the last stable release behaved.
 */
internal object Supporter {
    private const val PREFS = "folio"
    private const val CODE = "supporter_code"
    private const val BETA = "supporter_beta_on"

    /** Public keys a code may be signed with: McCal's, plus a test key the dev build (com.mccal.folio.dev) accepts. */
    internal fun keys(context: Context): List<String> = listOfNotNull(
        BetaKeys.SUPPORTER.takeIf { it.isNotBlank() },
        BetaKeys.TEST.takeIf { it.isNotBlank() && context.packageName.endsWith(".dev") })

    /** Whether this build can check codes at all: without a key, Settings doesn't offer the row. */
    fun available(context: Context): Boolean = keys(context).isNotEmpty()

    /**
     * The stored code, once it has been checked.
     *
     * Checking means an ECDSA verification, which is about a millisecond, and this is asked from inside
     * composables: Settings asks on every redraw whether to show the Market row, twice over in the split view. A
     * millisecond of signature maths per frame is a sixteenth of the frame, for an answer that only changes when
     * somebody redeems or removes a code. So the answer is kept, keyed on the exact text it was worked out from,
     * and a new or removed code recomputes it.
     *
     * The day is deliberately not part of the key: an expiry is checked in [expired] against the clock each time,
     * so a code doesn't stay valid past midnight because the answer was cached before it.
     */
    fun code(context: Context, today: LocalDate = LocalDate.now(), keys: List<String> = keys(context)): BetaCodes.Code? {
        val text = stored(context) ?: return null
        val remembered = checked
        val code = if (remembered != null && remembered.text == text && remembered.keys == keys) remembered.code else {
            val verified = (BetaCodes.verify(text, keys, today, BetaKeys.WITHDRAWN) as? BetaCodes.Result.Valid)?.code
            checked = Checked(text, keys, verified)
            verified
        }
        return code?.takeIf { !it.expired(today) }
    }

    /** The last code checked and what it came to, so the same text isn't verified twice. */
    private data class Checked(val text: String, val keys: List<String>, val code: BetaCodes.Code?)

    @Volatile private var checked: Checked? = null

    private fun stored(context: Context): String? =
        context.getSharedPreferences(PREFS, 0).getString(CODE, null)?.takeIf { it.isNotBlank() }

    /** Checks a code and keeps it when it's good. The result is what Settings shows the person. */
    fun redeem(context: Context, text: String, today: LocalDate = LocalDate.now(),
        keys: List<String> = keys(context)): BetaCodes.Result {
        val result = BetaCodes.verify(text, keys, today, BetaKeys.WITHDRAWN)
        if (result is BetaCodes.Result.Valid) {
            checked = null
            context.getSharedPreferences(PREFS, 0).edit().putString(CODE, BetaCodes.group(text)).apply()
        }
        return result
    }

    fun remove(context: Context) {
        checked = null
        context.getSharedPreferences(PREFS, 0).edit().remove(CODE).remove(BETA).apply()
    }

    fun storedText(context: Context): String? = stored(context)

    /** A code unlocks a feature; beta features also need the switch, so early access can be left at any time. */
    fun has(context: Context, scope: String, keys: List<String> = keys(context)): Boolean {
        val code = code(context, keys = keys) ?: return false
        if (scope !in code.scopes) return false
        return scope != BetaCodes.SCOPE_BETA || betaOn(context)
    }

    fun betaOn(context: Context): Boolean = context.getSharedPreferences(PREFS, 0).getBoolean(BETA, false)

    fun setBetaOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, 0).edit().putBoolean(BETA, on).apply()
    }
}

/**
 * Public keys for supporter codes. [SUPPORTER] is filled in from McCal's own key pair (the private half never leaves
 * his Mac; `scripts/beta-code.py` makes both the key and the codes). [TEST] signs codes for development builds only.
 */
internal object BetaKeys {
    const val SUPPORTER = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEM4vVq0D/ZzqkVWlyQYMTFN3TTbdqRgKdt2a30T88NkeQhDEWnujFjfyXyKtPIVBKMSiRVXeY/OcC0+TaB6eypg=="
    const val TEST = ""

    /**
     * Serial numbers of codes that no longer work: one that was posted publicly, or one a refund took back. A code is
     * checked on the phone, so a withdrawal only takes effect when someone updates Folio — keep the list short and
     * only for codes that were really passed around.
     */
    val WITHDRAWN: Set<Long> = emptySet()
}
