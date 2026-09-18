package com.mccal.folio

import android.content.Context
import com.mccal.folio.market.BuiltInSource
import com.mccal.folio.market.FileStore
import com.mccal.folio.market.FolioPackage
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.InstallResult
import com.mccal.folio.market.InstalledPackage
import com.mccal.folio.market.InstalledStore
import com.mccal.folio.market.MarketFeature
import com.mccal.folio.market.RepoClient
import com.mccal.folio.market.Source
import com.mccal.folio.market.SourceList
import com.mccal.folio.market.SourceStore
import com.mccal.folio.market.UrlHttpClient
import com.mccal.folio.market.EarlyAccess
import com.mccal.folio.market.MarketPrefs
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

    private val files = FileStore(File(appContext.filesDir, "market"))

    /** How Featured looks, and whether the introduction has been seen. */
    val prefs = MarketPrefs(files)

    private val store = InstalledStore(files)
    private val safeMode = PackageSafeMode(files)
    private val installer = PackageInstaller(store, MarketHost(launcher), safeMode)

    /** Whether this build can read an unsigned source served from the phone: Folio Dev only. */
    val localDevAllowed = MarketFeature.isDevBuild(appContext.packageName)

    /** Sources the user added. Folio Dev can also point at a source served from the phone (unsigned, localhost only). */
    val sources = MarketSources(
        client = RepoClient(
            http = UrlHttpClient(),
            store = SourceStore(files),
            allowLocalDev = localDevAllowed,
        ),
        list = SourceList(files),
        http = UrlHttpClient(),
    )

    /** The package list, or null when the bundled files are unreadable, which only a broken build can cause. */
    fun index(): RepoIndex? = source.index()

    /** Folio's own source, as a [Source], so built-in packages carry a source like any other. */
    val builtIn = Source("folio://built-in/", name = "Folio", kind = Source.Kind.BUILT_IN)

    /**
     * Every package the store can show: Folio's own first, then each source the user added, from its cached list.
     * Revoked packages keep their place with the reason, so nothing disappears without an explanation.
     */
    fun entries(): List<MarketEntry> = buildList {
        index()?.packages?.forEach { add(MarketEntry(it, builtIn)) }
        for (status in sources.cached()) {
            val snapshot = status.snapshot ?: continue
            val unsigned = status.source.kind == Source.Kind.LOCAL_DEV
            snapshot.index.packages.forEach { entry ->
                add(MarketEntry(entry, status.source, snapshot.revokedReason(entry), unsigned))
            }
        }
    }

    /** Downloads and installs a package from a source the user added. */
    suspend fun get(entry: MarketEntry): InstallResult = when (entry.source.kind) {
        Source.Kind.BUILT_IN -> get(entry.entry)
        else -> sources.download(entry.entry, entry.source, installer)
    }

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

    /** Reads a `.foliopkg` someone opened, without applying it: the confirm sheet shows what's inside. */
    fun read(bytes: ByteArray): PackageInstaller.ReadResult = installer.read(bytes)

    /** Installs a file someone opened. It's recorded as coming from a file, not from a source. */
    fun installFile(bytes: ByteArray): InstallResult =
        installer.install(bytes, origin = InstalledPackage.Origin.FILE)

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

/** The Market's settings, for screens that only need those (Settings › Market) rather than the whole session. */
internal fun rememberedMarketPrefs(context: Context): MarketPrefs =
    MarketPrefs(FileStore(File(context.applicationContext.filesDir, "market")))

/** Where `folio-pkg serve` plus `adb reverse tcp:8787 tcp:8787` puts a source being written. */
internal const val DEFAULT_LOCAL_SOURCE = "http://localhost:8787/"

/**
 * Whether this phone sees the Market: Folio Dev, Beta Updates, or a supporter's code. Everything the Market hands out
 * is also in Settings, so a stable-release user isn't missing a feature — only the store that lists them.
 */
internal object MarketAccess {
    fun isOpen(context: Context): Boolean = MarketFeature.isEnabled(
        packageName = context.packageName,
        onBeta = runCatching { SoftwareUpdate.beta(context) }.getOrDefault(false),
        hasEarlyCode = early(context).has(EarlyAccess.MARKET),
    )

    fun early(context: Context) = EarlyAccess(FileStore(File(context.applicationContext.filesDir, "market")))

    /** Takes a supporter's code and says what happened, in the words the user sees. */
    fun redeem(context: Context, code: String): EarlyAccess.Result =
        early(context).redeem(code, EarlyAccess.MARKET)
}
