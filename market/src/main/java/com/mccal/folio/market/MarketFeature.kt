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

    /** What to tell someone who doesn't have it yet, on the page where they can turn Beta Updates on. */
    const val NOT_YET =
        "The Market arrives in 0.7.0. Turn on Beta Updates to try it early, or use a supporter code. Every theme and " +
            "tweak it hands out is already in Settings, so nothing is waiting behind it."
}
