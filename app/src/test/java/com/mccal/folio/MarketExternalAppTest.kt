package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import com.mccal.folio.market.ExternalSource
import com.mccal.folio.market.PackageManifest
import com.mccal.folio.market.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An app Folio points at rather than installs.
 *
 * The line that matters is the one Folio doesn't cross: it can install an APK - Software Update does - and it
 * never does so for a package a source named. These check the pointing, and that a listing can't smuggle a
 * download past it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarketExternalAppTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun manifest(via: String): PackageManifest {
        val json = """
            {
              "format": 1, "id": "dev.mccal.keyd", "name": "Keyd", "version": "0.1.0",
              "author": { "name": "Folio" }, "minFolio": "0.7.0", "section": "tweaks",
              "kind": ["externalApp"], "permissions": [], "via": [$via]
            }
        """.trimIndent()
        return when (val r = PackageManifest.parse(json)) {
            is ParseResult.Ok -> r.value
            is ParseResult.Invalid -> error("manifest fixture is wrong: " + r.errors)
            is ParseResult.Unsupported -> error("manifest fixture needs a newer Folio: " + r.needs)
        }
    }

    @Test fun `each store gets the address it actually uses`() {
        assertEquals(
            "market://details?id=dev.mccal.keyd",
            MarketExternalApp.uriFor(ExternalSource(ExternalSource.Store.PLAY_STORE, "dev.mccal.keyd", null)),
        )
        assertEquals(
            "https://f-droid.org/packages/dev.mccal.keyd/",
            MarketExternalApp.uriFor(ExternalSource(ExternalSource.Store.FDROID, "dev.mccal.keyd", null)),
        )
        assertEquals(
            "obtainium://add/https://github.com/McCal-Codes/folio-keyd",
            MarketExternalApp.uriFor(ExternalSource(ExternalSource.Store.OBTAINIUM, null, "https://github.com/McCal-Codes/folio-keyd")),
        )
    }

    @Test fun `a listing with nothing to point at points nowhere`() {
        // The schema requires an id for the two stores and a repo for Obtainium, so this shouldn't arrive - and if
        // it does, an option that leads nowhere is better than one that opens something unexpected.
        assertNull(MarketExternalApp.uriFor(ExternalSource(ExternalSource.Store.PLAY_STORE, null, null)))
        assertNull(MarketExternalApp.uriFor(ExternalSource(ExternalSource.Store.OBTAINIUM, "dev.mccal.keyd", null)))
    }

    @Test fun `an external package is recognised, and an ordinary one isn't`() {
        assertTrue(MarketExternalApp.isExternal(manifest("""{ "store": "obtainium", "repoUrl": "https://github.com/McCal-Codes/folio-keyd" }""")))
        val tweak = (PackageManifest.parse(
            """{"format":1,"id":"dev.a.b","name":"B","version":"1.0.0","author":{"name":"A"},"minFolio":"0.7.0","section":"tweaks","kind":["tweakBundle"],"permissions":["tweaks"]}""",
        ) as ParseResult.Ok).value
        assertTrue(!MarketExternalApp.isExternal(tweak))
        assertTrue(!MarketExternalApp.isExternal(null))
    }

    @Test fun `an app nobody has installed is not on the phone`() {
        val keys = manifest("""{ "store": "playStore", "id": "dev.mccal.keyd.nothere" }""")
        assertNull(MarketExternalApp.installedAppId(context, keys))
        // A listing with no app id at all - Obtainium only - has nothing to look for, and says so rather than
        // guessing from the package id, which is Folio's name for it and not Android's.
        val obtainium = manifest("""{ "store": "obtainium", "repoUrl": "https://github.com/McCal-Codes/folio-keyd" }""")
        assertNull(MarketExternalApp.installedAppId(context, obtainium))
    }

    @Test fun `Folio finds an app that is installed`() {
        // Robolectric's own package stands in for one that is really there.
        val mine = manifest("""{ "store": "playStore", "id": "${context.packageName}" }""")
        assertEquals(context.packageName, MarketExternalApp.installedAppId(context, mine))
    }
}
