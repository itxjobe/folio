package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import com.mccal.folio.market.ExternalSource
import com.mccal.folio.market.PackageKind
import com.mccal.folio.market.PackageManifest

/**
 * A package that is an app of its own: Keyd, an icon pack from Play, anything Android has to install itself.
 *
 * **Folio never downloads or installs an APK a source named.** It knows how to install one - that is how Software
 * Update works - and pointing that at a source would turn a launcher into an app store, which is a different thing
 * with a different permission story and the exact shape that gets a sideloaded app distrusted. So a listing here is
 * a pointer: Get opens Play, F-Droid or Obtainium, Android does the installing and the asking, and Folio's part is
 * to notice afterwards that the app arrived.
 *
 * What a listing can't do is hide what it is. The store shows where it installs from before anything is tapped.
 */
internal object MarketExternalApp {

    /** True when this package is an app to install rather than something Folio applies itself. */
    fun isExternal(manifest: PackageManifest?) = manifest?.kinds?.contains(PackageKind.EXTERNAL_APP) == true

    /**
     * Whether the app is on this phone.
     *
     * A keyboard is asked of [InputMethodManager], which lists every installed input method without Folio needing
     * package visibility for it - Android treats the list of keyboards as public, because a person has to be able
     * to choose one. Anything else needs a `<queries>` entry, and without one Android answers "not installed"
     * whether it is there or not, so that case says so rather than pretending to know.
     */
    fun installedAppId(context: Context, manifest: PackageManifest?, generation: Int = 0): String? {
        val ids = manifest?.via.orEmpty().mapNotNull { it.appId }.distinct()
        if (ids.isEmpty()) return null
        val keyboards = keyboards(context, generation)
        return ids.firstOrNull { id ->
            id in keyboards || runCatching { context.packageManager.getPackageInfo(id, 0) }.isSuccess
        }
    }

    /**
     * Every installed input method, worked out once per [generation] rather than once per row.
     *
     * Building the list is a binder call and the answer is the same for every package on the screen, so a list
     * with several external listings asked Android the same question once a row. [generation] is bumped when
     * Folio comes back to the front, which is the only moment the answer can have changed.
     */
    private fun keyboards(context: Context, generation: Int): Set<String> {
        cached?.let { (at, set) -> if (at == generation) return set }
        val set = runCatching {
            context.getSystemService(InputMethodManager::class.java)
                ?.inputMethodList.orEmpty().map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
        cached = generation to set
        return set
    }

    @Volatile private var cached: Pair<Int, Set<String>>? = null

    /** True when this package name belongs to an input method, which is the one case with no launcher icon. */
    fun isKeyboard(context: Context, appId: String, generation: Int = 0) = appId in keyboards(context, generation)

    /** Opens the app itself, once it's installed. False when there is nothing Android will open. */
    fun open(context: Context, appId: String, generation: Int = 0): Boolean {
        val launch = runCatching { context.packageManager.getLaunchIntentForPackage(appId) }.getOrNull()
        val target = launch
            // A keyboard has no launcher icon of its own, so there is nothing to open; the place to go is where
            // Android keeps the keyboards. Only for a keyboard, though - sending someone there because an icon
            // pack happens to have no launcher activity is a non-sequitur.
            ?: Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS).takeIf { isKeyboard(context, appId, generation) }
            // Anything else with no way in: its own page in Android's settings, which always exists.
            ?: Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", appId, null),
            )
        return runCatching {
            context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    /**
     * Sends the person to where the app installs from. Folio only ever opens a link; whatever happens next is
     * between them and that store.
     */
    fun install(context: Context, from: ExternalSource): Boolean {
        val uri = uriFor(from) ?: return false
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    /** The address each store uses, kept here so it can be read and tested without a phone. */
    fun uriFor(from: ExternalSource): String? = when (from.store) {
        // market:// opens the Play app itself; Android falls back to the browser when it isn't there.
        ExternalSource.Store.PLAY_STORE -> from.appId?.let { "market://details?id=$it" }
        ExternalSource.Store.FDROID -> from.appId?.let { "https://f-droid.org/packages/$it/" }
        // Obtainium's own add-app link, so the app installs and keeps updating from its releases.
        ExternalSource.Store.OBTAINIUM -> from.repoUrl?.let { "obtainium://add/$it" }
    }

    /** What the sheet calls each one, and the line under it. */
    fun label(store: ExternalSource.Store): Int = when (store) {
        ExternalSource.Store.PLAY_STORE -> R.string.play_store
        ExternalSource.Store.FDROID -> R.string.f_droid
        ExternalSource.Store.OBTAINIUM -> R.string.obtainium
    }

    fun detail(store: ExternalSource.Store): Int = when (store) {
        ExternalSource.Store.PLAY_STORE -> R.string.install_from_google_play
        ExternalSource.Store.FDROID -> R.string.install_from_f_droid
        ExternalSource.Store.OBTAINIUM -> R.string.install_from_the_repository_s_releases
    }
}
