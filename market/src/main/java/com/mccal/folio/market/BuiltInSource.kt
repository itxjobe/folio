package com.mccal.folio.market

/**
 * Folio's own source: the themes and tweaks that ship inside the app, written as real packages
 * (`docs/sdk/source`, copied into assets at build time). It needs no network and no signature, because the files came
 * with the app the user already installed.
 *
 * [read] and [list] are given by the caller, so the app can read assets while the tests read the repository.
 */
class BuiltInSource(
    private val read: (path: String) -> ByteArray?,
    private val list: (path: String) -> List<String>,
) {
    /** The package list, or null when the bundled files are missing or unreadable (a build problem, not a user one). */
    fun index(): RepoIndex? = read(INDEX)?.decodeToString()?.let { (RepoIndex.parse(it) as? ParseResult.Ok)?.value }

    /** The revocation list Folio ships with, so a package pulled after a release is off even before the first refresh. */
    fun revocations(): RevocationList? =
        read(REVOKED)?.decodeToString()?.let { (RevocationList.parse(it) as? ParseResult.Ok)?.value }

    /** Every package's files, by package id. The folder names aren't ids, so each manifest says which is which. */
    fun packages(): Map<String, Map<String, ByteArray>> = list(PACKAGES).mapNotNull { folder ->
        val files = filesIn(folder)
        val manifest = files[PackageArchive.MANIFEST]?.decodeToString() ?: return@mapNotNull null
        val id = (PackageManifest.parse(manifest) as? ParseResult.Ok)?.value?.id ?: return@mapNotNull null
        id to files
    }.toMap()

    /** One package's files, for installing it. */
    fun filesFor(id: String): Map<String, ByteArray>? = packages()[id]

    /** An image the index or a page points at, such as `assets/home-clear.webp`. */
    fun asset(path: String): ByteArray? = if (SAFE_PATH.containsMatchIn(path)) read("$ROOT/$path") else null

    private fun filesIn(folder: String): Map<String, ByteArray> =
        list("$PACKAGES/$folder").mapNotNull { name -> read("$PACKAGES/$folder/$name")?.let { name to it } }.toMap()

    companion object {
        const val ROOT = "market/source"
        const val INDEX = "$ROOT/index.json"
        const val REVOKED = "$ROOT/revoked.json"
        const val PACKAGES = "$ROOT/packages"
    }
}
