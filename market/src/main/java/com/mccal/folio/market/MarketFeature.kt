package com.mccal.folio.market

/**
 * Whether the Market shows up. It stays hidden in release builds until 0.7.0 is ready; Folio Dev (debug and fast
 * builds, package name ending in ".dev") shows it so it can be tested next to the signed release.
 */
object MarketFeature {
    /** Flip to true for the 0.7.0 release (or a 0.7.0 beta). */
    const val RELEASED = false

    fun isEnabled(packageName: String): Boolean = RELEASED || packageName.endsWith(".dev")
}
