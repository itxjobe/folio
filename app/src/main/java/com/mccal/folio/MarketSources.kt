package com.mccal.folio

import com.mccal.folio.market.HttpClient
import com.mccal.folio.market.HttpResult
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.InstallResult
import com.mccal.folio.market.PackageInstaller
import com.mccal.folio.market.RefreshResult
import com.mccal.folio.market.RepoClient
import com.mccal.folio.market.RevocationList
import com.mccal.folio.market.Source
import com.mccal.folio.market.SourceKey
import com.mccal.folio.market.SourceList
import com.mccal.folio.market.SourceSnapshot
import com.mccal.folio.market.normalizeSourceUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A package in the store, with the source it came from. */
internal data class MarketEntry(
    val entry: IndexPackage,
    val source: Source,
    /** Why this package is turned off, from a revocation list. Null when it's fine. */
    val revokedReason: String? = null,
    /** True when the source that offers it isn't signed (a local one, during development). */
    val unsigned: Boolean = false,
) {
    val id: String get() = entry.id
    val name: String get() = entry.manifest?.name?.english ?: entry.id
}

/** What happened the last time Folio asked a source for its list. */
internal data class SourceStatus(
    val source: Source,
    val snapshot: SourceSnapshot? = null,
    val failure: RefreshResult.Failed? = null,
    val refreshing: Boolean = false,
) {
    val packages: List<IndexPackage> get() = snapshot?.index?.packages.orEmpty()
}

/**
 * The sources the user added, and the packages they offer.
 *
 * Every network call happens off the main thread and through [RepoClient], so the signature, rollback, freshness and
 * hash checks are the same ones the tests cover. A source that fails keeps its last good list.
 */
internal class MarketSources(
    private val client: RepoClient,
    private val list: SourceList,
    private val http: HttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /**
     * The revocation list Folio ships with. A source listed there is refused before Folio calls it at all, which is
     * the only way to disown a source that has been taken over between releases.
     */
    private val knownRevocations: () -> RevocationList? = { null },
) {
    fun sources(): List<Source> = list.added()

    /** The cached list for each source, without asking the network. */
    fun cached(): List<SourceStatus> = sources().map { source ->
        SourceStatus(source, client.cachedSnapshot(source.url))
    }

    /** Reads a source's key so its fingerprint can be shown before anything is trusted. */
    suspend fun inspect(url: String): RefreshResult = withContext(io) {
        val base = normalizeSourceUrl(url)
        if (base.startsWith("http://")) return@withContext client.refreshLocalDev(base)
        client.readKey(base)
    }

    /** Pins a key the user has confirmed, adds the source, and reads its list. */
    suspend fun trust(url: String, key: SourceKey): RefreshResult = withContext(io) {
        val base = normalizeSourceUrl(url)
        client.trust(base, key)
        list.add(Source(base, addedAt = System.currentTimeMillis() / 1000))
        refresh(base, force = true)
    }

    /** Adds a local source for development. Unsigned, so only Folio Dev can use it at all. */
    suspend fun addLocalDev(url: String): RefreshResult = withContext(io) {
        val base = normalizeSourceUrl(url)
        val result = client.refreshLocalDev(base)
        if (result is RefreshResult.Updated) {
            list.add(Source(base, kind = Source.Kind.LOCAL_DEV, addedAt = System.currentTimeMillis() / 1000))
            list.rename(base, result.snapshot.index.name.english)
        }
        result
    }

    suspend fun refresh(url: String, force: Boolean): RefreshResult = withContext(io) {
        val source = sources().firstOrNull { it.url == normalizeSourceUrl(url) }
        val result = if (source?.kind == Source.Kind.LOCAL_DEV) client.refreshLocalDev(url)
        else client.refresh(url, force, knownRevocations())
        val name = (result as? RefreshResult.Updated)?.snapshot?.index?.name?.english
            ?: (result as? RefreshResult.Unchanged)?.snapshot?.index?.name?.english
        name?.let { list.rename(url, it) }
        result
    }

    /** Asks every source, one at a time so a slow one doesn't hold the others up in parallel connections. */
    suspend fun refreshAll(force: Boolean): Map<String, RefreshResult> = withContext(io) {
        sources().associate { it.url to refresh(it.url, force) }
    }

    fun forget(url: String) {
        client.forget(url)
        list.remove(url)
    }

    /**
     * Downloads a package and installs it: size and checksum are checked against the index entry before anything is
     * opened, and [PackageInstaller] does the rest.
     */
    suspend fun download(entry: IndexPackage, source: Source, installer: PackageInstaller): InstallResult = withContext(io) {
        val url = entry.url ?: return@withContext InstallResult.Failed(InstallResult.Reason.ARCHIVE, "that package has nowhere to download from")
        val size = entry.size ?: return@withContext InstallResult.Failed(InstallResult.Reason.SIZE, "that package didn't say how big it is")
        val full = if (url.startsWith("https://")) url else source.url + url
        when (val result = http.get(full, size)) {
            is HttpResult.Body -> installer.install(
                result.bytes,
                expected = entry,
                origin = com.mccal.folio.market.InstalledPackage.Origin.FOLIO_SOURCE,
                sourceUrl = source.url,
            )
            is HttpResult.TooLarge -> InstallResult.Failed(InstallResult.Reason.SIZE, "that download is bigger than the source said")
            is HttpResult.NotModified -> InstallResult.Failed(InstallResult.Reason.ARCHIVE, "the source answered oddly")
            is HttpResult.Failed -> InstallResult.Failed(InstallResult.Reason.ARCHIVE, result.message)
        }
    }
}
