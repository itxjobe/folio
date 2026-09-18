package com.mccal.folio.market

/** How Featured looks. McCal's call (2026-09-17): the carousel is the default, and the introduction offers both. */
enum class FeaturedStyle(val id: String, val label: String, val description: String) {
    CAROUSEL("carousel", "Carousel", "Large banners you can swipe through"),
    CALM("calm", "Calm", "A simple list, like Settings");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: CAROUSEL
    }
}

/**
 * The Market's own settings, kept with the rest of its data rather than in the launcher's state: they only matter
 * inside the store, and they shouldn't travel in a Home layout backup.
 */
class MarketPrefs(private val store: KeyValueStore) {
    var featuredStyle: FeaturedStyle
        get() = FeaturedStyle.from(store.get(FEATURED_STYLE))
        set(value) { store.set(FEATURED_STYLE, value.id) }

    /**
     * Whether Folio checks the sources you added in the background. Off by default: Folio is local-first, so with this
     * off it only goes online when you open the store and refresh.
     */
    var backgroundRefresh: Boolean
        get() = store.get(BACKGROUND_REFRESH) == "1"
        set(value) { store.set(BACKGROUND_REFRESH, if (value) "1" else null) }

    /** Whether a background refresh waits for an unmetered network. On by default, so it never spends mobile data. */
    var refreshOnWifiOnly: Boolean
        get() = store.get(WIFI_ONLY) != "0"
        set(value) { store.set(WIFI_ONLY, if (value) null else "0") }

    /** The introduction is shown once, after updating to 0.7.0, and again if the user asks for it in Settings. */
    var introductionSeen: Boolean
        get() = store.get(INTRO_SEEN) == "1"
        set(value) { store.set(INTRO_SEEN, if (value) "1" else null) }

    private companion object {
        const val FEATURED_STYLE = "market:featured-style"
        const val INTRO_SEEN = "market:intro-seen"
        const val BACKGROUND_REFRESH = "market:background-refresh"
        const val WIFI_ONLY = "market:wifi-only"
    }
}
