package com.mccal.folio.market

/**
 * Who sees the Market.
 *
 * 0.7.0 gives it to the people who asked to test Folio early — Folio Dev, anyone on Beta Updates, and supporters with
 * a code — while everyone else keeps every feature they already had. The tweaks and themes the Market hands out are
 * all still in Settings, so nobody on the stable release loses anything by not seeing the store yet.
 *
 * When 0.7.0 ships properly, [RELEASED] becomes true and the gate stops mattering.
 */
object MarketFeature {
    /** True once the Market ships to everyone. */
    const val RELEASED = false

    /** Folio Dev (debug and fast builds) always has it, so it can be tested beside the signed release. */
    fun isDevBuild(packageName: String) = packageName.endsWith(".dev")

    /**
     * [onBeta] is the existing Beta Updates switch, and [hasEarlyCode] is a supporter's code with the beta scope. Either
     * one opens the Market; neither one is needed once it's released.
     */
    fun isEnabled(packageName: String, onBeta: Boolean = false, hasEarlyCode: Boolean = false): Boolean =
        RELEASED || isDevBuild(packageName) || onBeta || hasEarlyCode
}
