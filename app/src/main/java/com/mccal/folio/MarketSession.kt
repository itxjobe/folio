package com.mccal.folio

import android.content.Context
import com.mccal.folio.market.BuiltInSource
import com.mccal.folio.market.FileStore
import com.mccal.folio.market.FolioPackage
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.InstallResult
import com.mccal.folio.market.InstalledPackage
import com.mccal.folio.market.InstalledStore
import com.mccal.folio.market.PackageInstaller
import com.mccal.folio.market.PackageSafeMode
import com.mccal.folio.market.RepoIndex
import java.io.File

/**
 * Everything the Market needs on the phone, wired together: the packages Folio ships (read from assets), what's
 * installed (a folder under `filesDir`), and the installer that applies them through [MarketHost].
 *
 * Sources over the network come in Phase 6; until then the only source is Folio's own, which needs no network at all.
 */
internal class MarketSession(context: Context, launcher: MarketLauncher) {
    private val appContext = context.applicationContext

    val source = BuiltInSource(
        read = { path -> runCatching { appContext.assets.open(path).use { it.readBytes() } }.getOrNull() },
        list = { path -> runCatching { appContext.assets.list(path)?.toList().orEmpty() }.getOrDefault(emptyList()) },
    )

    private val store = InstalledStore(FileStore(File(appContext.filesDir, "market")))
    private val safeMode = PackageSafeMode(FileStore(File(appContext.filesDir, "market")))
    private val installer = PackageInstaller(store, MarketHost(launcher), safeMode)

    /** The package list, or null when the bundled files are unreadable, which only a broken build can cause. */
    fun index(): RepoIndex? = source.index()

    fun installed(): List<InstalledPackage> = store.installed()

    fun installed(id: String): InstalledPackage? = store.find(id)

    /** The page and payload for a package, without applying anything: what the package page shows. */
    fun read(id: String): FolioPackage? {
        val files = source.filesFor(id) ?: return null
        return (installer.readFiles(files) as? PackageInstaller.ReadResult.Ok)?.pkg
    }

    fun get(entry: IndexPackage): InstallResult {
        val files = source.filesFor(entry.id) ?: return InstallResult.Failed(InstallResult.Reason.ARCHIVE, "Folio couldn't find that package")
        return installer.installBuiltIn(files)
    }

    fun remove(id: String): Boolean = installer.remove(id)

    fun undo(result: InstallResult.Installed): Boolean = installer.undo(result)

    /**
     * Called when Folio starts after a crash: if a package was being applied, it's turned off rather than left to
     * break Home again. Returns the package that was turned off, so the store can explain itself.
     */
    fun noteCrash(): InstalledPackage? {
        val id = safeMode.noteCrash() ?: return null
        store.disable(id, "Folio stopped twice just after this package changed, so it's off. Your settings are kept.")
        return store.find(id)
    }
}
