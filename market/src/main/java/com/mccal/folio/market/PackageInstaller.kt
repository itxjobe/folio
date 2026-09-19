package com.mccal.folio.market

import org.json.JSONArray
import org.json.JSONObject

/**
 * What the launcher lets a package change. `:market` never touches the Home screen itself, so Folio Lite can reuse the
 * store later with a different host.
 */
interface PackageHost {
    /** What this build of Folio can do. A package needing anything else is never applied. */
    val capabilities: Set<Capability>

    /**
     * Applies [change] and returns a snapshot of what it replaced, which Folio keeps until the user dismisses Undo.
     * Throwing means nothing was applied.
     */
    fun apply(change: PackageChange): String

    /** Puts back what [apply] replaced. Called for Undo, for Remove, and when a later step of an install fails. */
    fun restore(change: PackageChange, snapshot: String)
}

/** A package Folio has installed, and what it replaced. */
data class InstalledPackage(
    val id: String,
    val version: DebVersion,
    val name: String,
    val origin: Origin,
    val sourceUrl: String?,
    val installedAt: Long,
    /** What each change replaced, in the order it was applied, so Undo and Remove can walk back. */
    val snapshots: List<String>,
    val enabled: Boolean = true,
    /** Why Safe Mode turned this package off, when it did. */
    val disabledReason: String? = null,
) {
    enum class Origin(val id: String) {
        FOLIO_SOURCE("folio-source"), FILE("file"), PLAY_ICON_PACK("play-icon-pack"), LAUNCHER_IMPORT("launcher-import");

        companion object {
            fun from(id: String) = entries.firstOrNull { it.id == id } ?: FOLIO_SOURCE
        }
    }
}

sealed interface InstallResult {
    /** Applied. [undo] puts everything back, and stays valid until the user dismisses the message. */
    data class Installed(val installed: InstalledPackage, val replaced: InstalledPackage?, val notes: List<String>) : InstallResult

    /** The package is fine but this Folio can't run it. */
    data class NeedsNewerFolio(val missing: List<String>) : InstallResult

    data class Failed(val reason: Reason, val message: String) : InstallResult

    enum class Reason { HASH, SIZE, ARCHIVE, MANIFEST, MISMATCH, CONFLICT, DEPENDS, APPLY, REVOKED, NEEDS_NEWER, AUTHOR }
}

/**
 * Installs, removes and undoes packages (Phase 3).
 *
 * The order is download, hash, open, read, check, stage, apply, record — and every step happens before anything on the
 * Home screen changes. If applying the third change of a package fails, the first two are put back, so a package is
 * never half applied. The previous version is kept until Undo is dismissed.
 */
class PackageInstaller(
    private val store: InstalledStore,
    private val host: PackageHost,
    private val safeMode: PackageSafeMode = PackageSafeMode(store.keyValue),
    /** Who owns which package id. Signatures are only checked for packages that came from a source. */
    private val authors: AuthorTrust = AuthorTrust(store.keyValue),
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    /** This build's release number, for a package's `minFolio`. Null skips that check, which only a test does. */
    private val folioVersion: FolioVersion? = null,
) {
    /**
     * Reads [bytes] as a package and applies it. [expected] is the index entry it came from, when there was one: its
     * hash, size, id and version all have to match what's inside the file (T1, and the "no bait and switch" rule).
     */
    fun install(
        bytes: ByteArray,
        expected: IndexPackage? = null,
        origin: InstalledPackage.Origin = InstalledPackage.Origin.FOLIO_SOURCE,
        sourceUrl: String? = null,
    ): InstallResult {
        expected?.size?.let { if (bytes.size != it) return InstallResult.Failed(InstallResult.Reason.SIZE, "that download isn't the size the source listed") }
        expected?.sha256?.let {
            if (sha256Hex(bytes) != it) return InstallResult.Failed(InstallResult.Reason.HASH, "that download doesn't match the source's checksum")
        }
        // Who wrote it, which is a different question from who handed it over. Checked here, against the bytes that
        // actually arrived, so a mirror can carry a package but can't alter it or publish under its author's name.
        var pinning: Pair<String, String>? = null
        if (expected?.sha256 != null) {
            when (val author = authors.check(expected.id, expected.version, expected.sha256, expected.signedBy)) {
                is AuthorTrust.Result.FirstTime -> pinning = expected.id to author.keyBase64
                else -> if (!author.installable) {
                    return InstallResult.Failed(InstallResult.Reason.AUTHOR, author.message)
                }
            }
        }
        val pkg = when (val read = read(bytes)) {
            is ReadResult.Ok -> read.pkg
            is ReadResult.NeedsNewerFolio -> return InstallResult.NeedsNewerFolio(read.missing)
            is ReadResult.Failed -> return InstallResult.Failed(read.reason, read.message)
        }
        // A package shared as a file has no index to carry a signature, so it carries its own. The same rules
        // apply: a name that already belongs to another key is refused, whichever way the package arrived.
        if (expected?.signedBy == null) {
            when (val author = authors.checkFiles(pkg.id, pkg.version, pkg.files)) {
                is AuthorTrust.Result.FirstTime -> pinning = pkg.id to author.keyBase64
                else -> if (!author.installable) {
                    return InstallResult.Failed(InstallResult.Reason.AUTHOR, author.message)
                }
            }
        }
        if (expected != null && (expected.id != pkg.id || expected.version != pkg.version)) {
            return InstallResult.Failed(InstallResult.Reason.MISMATCH, "that package isn't the one the source listed")
        }
        return apply(pkg, origin, sourceUrl, pinning = pinning)
    }

    private fun apply(
        pkg: FolioPackage,
        origin: InstalledPackage.Origin,
        sourceUrl: String?,
        builtIn: Boolean = false,
        /** The id and author key to remember, once this package is really on. */
        pinning: Pair<String, String>? = null,
    ): InstallResult {
        val missing = pkg.manifest.missingCapabilities(host.capabilities).map { it.id } +
            pkg.changes.flatMap { it.capabilities }.filterNot { it in host.capabilities }.map { it.id }
        if (missing.isNotEmpty()) return InstallResult.NeedsNewerFolio(missing.distinct())
        // Capabilities catch a package that names something this build hasn't got; `minFolio` catches one that needs a
        // later Folio's behaviour without naming anything. Both mean the same thing to the user.
        val needs = pkg.manifest.minFolio
        if (!builtIn && folioVersion != null && needs > folioVersion) {
            return InstallResult.NeedsNewerFolio(listOf("Folio $needs"))
        }
        val already = store.installed()
        already.firstOrNull { it.id != pkg.id && pkg.manifest.conflicts.any { c -> c.id == it.id && c.matches(it.version) } }
            ?.let { return InstallResult.Failed(InstallResult.Reason.CONFLICT, "that package replaces ${it.name}") }
        // Dependencies have to be installed first; the store's queue sheet offers to add them (Phase 5).
        val missingDepends = pkg.manifest.depends.filterNot { needed ->
            already.any { it.id == needed.id && it.enabled && needed.matches(it.version) }
        }
        if (missingDepends.isNotEmpty()) {
            return InstallResult.Failed(InstallResult.Reason.DEPENDS, "that package needs ${missingDepends.joinToString { it.toString() }} first")
        }

        // Applying starts here. Safe Mode watches from now until the marker is cleared, so a crash while a package is
        // being applied turns that package off instead of leaving Home unusable.
        val replaced = store.find(pkg.id)
        safeMode.beginChange(pkg.id)
        val snapshots = mutableListOf<String>()
        try {
            replaced?.let { undoChanges(it) }
            for (change in pkg.changes) snapshots += host.apply(change)
        } catch (e: Exception) {
            // Put back everything this install had already changed, newest first.
            pkg.changes.take(snapshots.size).zip(snapshots).reversed().forEach { (change, snapshot) ->
                runCatching { host.restore(change, snapshot) }
            }
            // An update undoes the version it replaces before applying the new one, so put that version back too:
            // a failed update leaves the phone exactly as it was, with the old version still working.
            replaced?.let { old ->
                val previous = store.changesFor(old.id, old.version)
                if (previous != null) {
                    val again = mutableListOf<String>()
                    runCatching { previous.forEach { again += host.apply(it) } }
                    store.put(old.copy(snapshots = again), previous)
                }
            }
            safeMode.endChange()
            return InstallResult.Failed(InstallResult.Reason.APPLY, "Folio couldn't apply that package, so nothing changed")
        }
        val installed = InstalledPackage(
            id = pkg.id,
            version = pkg.version,
            name = pkg.manifest.name.english,
            origin = origin,
            sourceUrl = sourceUrl,
            installedAt = clock(),
            snapshots = snapshots,
        )
        pinning?.let { (id, key) -> authors.remember(id, key) }
        // Nothing stays applied that Folio couldn't write down. A package on the Home screen and missing from the
        // list is one nobody can remove, so a store that won't write means the whole install is put back.
        if (!store.put(installed, changes = pkg.changes)) {
            pkg.changes.zip(snapshots).reversed().forEach { (change, snapshot) ->
                runCatching { host.restore(change, snapshot) }
            }
            safeMode.endChange()
            return InstallResult.Failed(InstallResult.Reason.APPLY, "Folio couldn't save that package, so nothing changed")
        }
        safeMode.endChange()
        return InstallResult.Installed(installed, replaced, pkg.notes)
    }

    /**
     * Installs a package that ships inside Folio, from files rather than a download: the built-in themes and tweaks in
     * `docs/sdk/source`. Everything after opening the archive is the same.
     */
    fun installBuiltIn(files: Map<String, ByteArray>): InstallResult {
        val pkg = when (val read = readFiles(files)) {
            is ReadResult.Ok -> read.pkg
            is ReadResult.NeedsNewerFolio -> return InstallResult.NeedsNewerFolio(read.missing)
            is ReadResult.Failed -> return InstallResult.Failed(read.reason, read.message)
        }
        // A package that ships inside this APK can't need a later Folio than the one it's part of, whatever its
        // manifest says: Folio's own packages name the release they're written for, and the version number is only
        // bumped when that release goes out.
        return apply(pkg, InstalledPackage.Origin.FOLIO_SOURCE, sourceUrl = null, builtIn = true)
    }

    /**
     * Turns a package off the way Safe Mode does: its changes come off the Home screen, and its record stays so it
     * can be put back with [enable].
     *
     * Taking the changes off is the point. Marking the record and leaving a theme applied turns nothing off — Folio
     * would start, crash on the same thing, and say it had already dealt with it.
     */
    fun disable(id: String, reason: String): Boolean {
        val installed = store.find(id)?.takeIf { it.enabled } ?: return false
        safeMode.beginChange(id)
        undoChanges(installed)
        store.setEnabled(id, enabled = false, reason = reason)
        safeMode.endChange()
        return true
    }

    /**
     * Puts a package that was turned off back on: Try Again, after Safe Mode.
     *
     * The changes are the ones recorded when it was installed, applied again from where Home is now, so the
     * snapshots are new. If applying fails halfway it goes back off rather than being left half on.
     */
    fun enable(id: String): Boolean {
        val installed = store.find(id)?.takeIf { !it.enabled } ?: return false
        val changes = store.changesFor(installed.id, installed.version) ?: return false
        safeMode.beginChange(id)
        val snapshots = mutableListOf<String>()
        try {
            for (change in changes) snapshots += host.apply(change)
        } catch (e: Exception) {
            changes.take(snapshots.size).zip(snapshots).reversed().forEach { (change, snapshot) ->
                runCatching { host.restore(change, snapshot) }
            }
            safeMode.endChange()
            return false
        }
        store.setEnabled(id, enabled = true, snapshots = snapshots)
        safeMode.endChange()
        return true
    }

    /** Takes a package off, putting back whatever it replaced. */
    fun remove(id: String): Boolean {
        val installed = store.find(id) ?: return false
        safeMode.beginChange(id)
        // One that Safe Mode turned off has already had its changes taken off; undoing them again would restore
        // whatever Home looked like before it, over whatever the user has done since.
        if (installed.enabled) undoChanges(installed)
        store.remove(id)
        safeMode.endChange()
        return true
    }

    /** Undo right after an install: remove what went on, and put the previous version back if there was one. */
    fun undo(result: InstallResult.Installed): Boolean {
        val previous = result.replaced
        // The previous version's own changes were recorded when it was installed. Read them before anything is taken
        // off: without them there is nothing to put back, and removing first would leave the user with neither
        // version.
        val changes = previous?.let { store.changesFor(it.id, it.version) }
        if (previous != null && changes == null) return false
        remove(result.installed.id)
        if (previous == null || changes == null) return true
        safeMode.beginChange(previous.id)
        val snapshots = mutableListOf<String>()
        runCatching { changes.forEach { snapshots += host.apply(it) } }
        store.put(previous.copy(snapshots = snapshots, installedAt = clock()), changes)
        safeMode.endChange()
        return true
    }

    private fun undoChanges(installed: InstalledPackage) {
        val changes = store.changesFor(installed.id, installed.version) ?: return
        changes.zip(installed.snapshots).reversed().forEach { (change, snapshot) ->
            runCatching { host.restore(change, snapshot) }
        }
    }

    sealed interface ReadResult {
        data class Ok(val pkg: FolioPackage) : ReadResult
        data class NeedsNewerFolio(val missing: List<String>) : ReadResult
        data class Failed(val reason: InstallResult.Reason, val message: String) : ReadResult
    }

    /** Opens a package and reads everything in it, without applying anything. Used by the install sheet's preview. */
    fun read(bytes: ByteArray): ReadResult = when (val archive = PackageArchive.read(bytes)) {
        is PackageArchive.Result.Ok -> readFiles(archive.files)
        is PackageArchive.Result.Rejected -> ReadResult.Failed(InstallResult.Reason.ARCHIVE, archive.reason)
    }

    /** The same, for a package whose files Folio already has: the built-in ones. */
    fun readFiles(files: Map<String, ByteArray>): ReadResult {
        if (PackageArchive.MANIFEST !in files) return ReadResult.Failed(InstallResult.Reason.ARCHIVE, "a package needs a manifest.json")
        val notes = mutableListOf<String>()
        val manifest = when (val parsed = PackageManifest.parse(files.getValue(PackageArchive.MANIFEST).decodeToString())) {
            is ParseResult.Ok -> parsed.value.also { notes += parsed.ignored }
            is ParseResult.Unsupported -> return ReadResult.NeedsNewerFolio(parsed.needs)
            is ParseResult.Invalid -> return ReadResult.Failed(InstallResult.Reason.MANIFEST, parsed.errors.first())
        }
        val depiction = manifest.depiction?.let { path ->
            files[path]?.let { bytes ->
                when (val parsed = Depiction.parse(bytes.decodeToString())) {
                    is ParseResult.Ok -> parsed.value.also { notes += parsed.ignored }
                    // A page that can't be read is a shame, not a reason to refuse the package.
                    else -> null.also { notes += "$path couldn't be read" }
                }
            }
        }
        val changes = mutableListOf<PackageChange>()
        for (kind in manifest.kinds) {
            val change = when (kind) {
                PackageKind.THEME -> files["theme.json"]?.let { PackageChange.Theme(it.decodeToString()) }
                    ?: return ReadResult.Failed(InstallResult.Reason.MANIFEST, "that package says it has a theme but has no theme.json")
                PackageKind.TWEAK_BUNDLE -> {
                    val text = files["tweaks.json"]?.decodeToString()
                        ?: return ReadResult.Failed(InstallResult.Reason.MANIFEST, "that package says it has tweaks but has no tweaks.json")
                    when (val parsed = TweakBundle.parse(text)) {
                        is ParseResult.Ok -> PackageChange.Tweaks(parsed.value)
                        is ParseResult.Unsupported -> return ReadResult.NeedsNewerFolio(parsed.needs)
                        is ParseResult.Invalid -> return ReadResult.Failed(InstallResult.Reason.MANIFEST, "tweaks.json: ${parsed.errors.first()}")
                    }
                }
                PackageKind.LAYOUT_PRESET -> files["layout.json"]?.let { PackageChange.Layout(it.decodeToString()) }
                    ?: return ReadResult.Failed(InstallResult.Reason.MANIFEST, "that package says it has a layout but has no layout.json")
                // A picture, named as one: the archive allows other file types under assets/, and handing one of
                // those to the wallpaper as image bytes is a guess about a name an author chose.
                PackageKind.WALLPAPER -> files.entries.firstOrNull {
                    it.key.startsWith("assets/") && it.key.substringAfterLast('.').lowercase() in IMAGE_TYPES
                }
                    ?.let { PackageChange.Wallpaper(it.key, it.value) }
                    ?: return ReadResult.Failed(InstallResult.Reason.MANIFEST, "that package says it has a wallpaper but has no image")
                PackageKind.ICON_PACK_LINK -> {
                    val json = files["iconpack.json"]?.decodeToString()
                        ?: return ReadResult.Failed(InstallResult.Reason.MANIFEST, "that package says it links an icon pack but has no iconpack.json")
                    val name = readIconPackName(json)
                        ?: return ReadResult.Failed(InstallResult.Reason.MANIFEST, "iconpack.json needs the icon pack's package name")
                    PackageChange.IconPack(name)
                }
                // Reserved kinds: readable, but nothing is applied until the phase that builds them.
                PackageKind.SETTINGS_SCHEMA, PackageKind.SCRIPT, PackageKind.EXTERNAL_APP -> null
            }
            change?.let(changes::add)
        }
        val assets = files.filterKeys { it.startsWith("assets/") }
        return ReadResult.Ok(FolioPackage(manifest, depiction, changes, assets, notes.take(Problems.MAX_REPORTED), files))
    }

    private fun readIconPackName(text: String): String? {
        val problems = Problems()
        val json = parseStrictObject(text, 4096, problems) ?: return null
        val name = Fields(json, "", problems, setOf("\$schema", "format", "package")).string("package", true, ANDROID_PACKAGE, 200)
        return if (problems.errors.isEmpty()) name else null
    }

    private companion object {
        val ANDROID_PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+\\z")

        /** What a wallpaper picture can be, matching the archive's own list of allowed types. */
        val IMAGE_TYPES = setOf("png", "webp", "jpg", "jpeg")
    }
}

/**
 * Safe Mode for one package (T9), on top of the launcher's own. A marker is written while a package is being changed;
 * if Folio crashes twice within a minute of that, the package that was being changed starts turned off, with its
 * settings kept, so the user can Try Again, Remove it or look at the details.
 */
class PackageSafeMode(private val store: KeyValueStore, private val clock: () -> Long = { System.currentTimeMillis() / 1000 }) {
    fun beginChange(id: String) = store.set(KEY, JSONObject().put("id", id).put("at", clock()).toString())

    fun endChange() = store.set(KEY, null)

    /**
     * Called when Folio starts after a crash. Returns the package to turn off, if a change was in flight recently and
     * this is the second crash.
     */
    fun noteCrash(): String? {
        val marker = store.get(KEY)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val id = marker.optString("id").takeIf { it.isNotEmpty() } ?: return null
        if (clock() - marker.optLong("at") > WINDOW_SECONDS) {
            store.set(KEY, null)
            return null
        }
        val crashes = marker.optInt("crashes") + 1
        if (crashes < 2) {
            store.set(KEY, marker.put("crashes", crashes).toString())
            return null
        }
        store.set(KEY, null)
        return id
    }

    private companion object {
        const val KEY = "market:safe-mode"
        const val WINDOW_SECONDS = 60L
    }
}

/** What's installed, what each package changed, and what it replaced. */
class InstalledStore(internal val keyValue: KeyValueStore) {
    fun installed(): List<InstalledPackage> = read().values.toList()

    fun find(id: String): InstalledPackage? = read()[id]

    /**
     * Records a package and what it changed. False means nothing could be written down, which matters: a change Folio
     * can't remember is one the user can't undo or remove.
     */
    fun put(installed: InstalledPackage, changes: List<PackageChange>): Boolean {
        // The changes go first: a record pointing at changes that aren't there is worse than no record.
        if (!keyValue.set(changesKey(installed.id, installed.version), encodeChanges(changes))) return false
        val all = read().toMutableMap()
        all[installed.id] = installed
        return write(all)
    }

    fun remove(id: String) {
        val all = read().toMutableMap()
        // What that version changed goes with it. Kept, these pile up for ever, and a wallpaper's record holds a
        // whole image; the only reader is Undo, which runs before the record is dropped.
        all.remove(id)?.let { keyValue.set(changesKey(it.id, it.version), null) }
        write(all)
    }

    /**
     * Marks a package off or on again, keeping its record and what it changed. Only the record: putting the changes
     * back or taking them off is [PackageInstaller.disable] and [PackageInstaller.enable], because that touches Home.
     */
    fun setEnabled(id: String, enabled: Boolean, reason: String? = null, snapshots: List<String>? = null) {
        val all = read().toMutableMap()
        all[id]?.let {
            all[id] = it.copy(enabled = enabled, disabledReason = if (enabled) null else reason,
                snapshots = snapshots ?: it.snapshots)
        }
        write(all)
    }

    /**
     * Everything about installed packages, for Folio's layout backup: the records and what each package changed, so a
     * restored backup knows what to put back. Restoring only writes the list; the launcher applies it afterwards.
     */
    /** Everything the backup carries. Unreadable data is left out rather than thrown, as everywhere else here. */
    fun export(): String {
        val packages = runCatching { JSONArray(keyValue.get(KEY) ?: "[]") }.getOrDefault(JSONArray())
        val changes = JSONObject()
        for (i in 0 until packages.length()) {
            val json = packages.optJSONObject(i) ?: continue
            val key = "${json.optString("id")}@${json.optString("version")}"
            keyValue.get("installed:changes:$key")?.let { text ->
                runCatching { changes.put(key, JSONArray(text)) }
            }
        }
        return JSONObject().put("format", 1).put("packages", packages).put("changes", changes).toString()
    }

    /** Reads what [export] wrote. Returns false, and changes nothing, when the backup can't be read. */
    fun restore(text: String): Boolean {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return false
        if (json.optInt("format") != 1) return false
        val packages = json.optJSONArray("packages") ?: return false
        val changes = json.optJSONObject("changes") ?: JSONObject()
        keyValue.set(KEY, packages.toString())
        for (key in changes.keys()) keyValue.set("installed:changes:$key", changes.optJSONArray(key)?.toString())
        return true
    }

    fun changesFor(id: String, version: DebVersion): List<PackageChange>? =
        keyValue.get(changesKey(id, version))?.let(::decodeChanges)

    private fun changesKey(id: String, version: DebVersion) = "installed:changes:$id@$version"

    private fun read(): Map<String, InstalledPackage> {
        val text = keyValue.get(KEY) ?: return emptyMap()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyMap()
        return (0 until array.length()).mapNotNull { i ->
            val json = array.optJSONObject(i) ?: return@mapNotNull null
            val version = DebVersion.parse(json.optString("version")) ?: return@mapNotNull null
            InstalledPackage(
                id = json.optString("id"),
                version = version,
                name = json.optString("name"),
                origin = InstalledPackage.Origin.from(json.optString("origin")),
                sourceUrl = json.optString("sourceUrl").takeIf { it.isNotEmpty() },
                installedAt = json.optLong("installedAt"),
                snapshots = json.optJSONArray("snapshots")?.let { a -> (0 until a.length()).map(a::optString) }.orEmpty(),
                enabled = json.optBoolean("enabled", true),
                disabledReason = json.optString("disabledReason").takeIf { it.isNotEmpty() },
            ).takeIf { it.id.isNotEmpty() }
        }.associateBy { it.id }
    }

    private fun write(all: Map<String, InstalledPackage>): Boolean {
        val array = JSONArray()
        for (p in all.values) {
            array.put(
                JSONObject()
                    .put("id", p.id).put("version", p.version.text).put("name", p.name)
                    .put("origin", p.origin.id).put("sourceUrl", p.sourceUrl).put("installedAt", p.installedAt)
                    .put("snapshots", JSONArray(p.snapshots)).put("enabled", p.enabled).put("disabledReason", p.disabledReason),
            )
        }
        return keyValue.set(KEY, array.toString())
    }

    // Changes are stored as data, so Undo and Remove work after a restart without keeping the package file around.
    private fun encodeChanges(changes: List<PackageChange>): String {
        val array = JSONArray()
        for (change in changes) {
            val json = JSONObject()
            when (change) {
                is PackageChange.Theme -> json.put("kind", "theme").put("json", change.json)
                is PackageChange.Layout -> json.put("kind", "layout").put("json", change.json)
                is PackageChange.IconPack -> json.put("kind", "iconPack").put("package", change.packageName)
                is PackageChange.Wallpaper -> json.put("kind", "wallpaper").put("path", change.path)
                    .put("bytes", java.util.Base64.getEncoder().encodeToString(change.bytes))
                is PackageChange.Tweaks -> json.put("kind", "tweaks").put(
                    "tweaks",
                    JSONArray().apply {
                        change.bundle.tweaks.forEach {
                            put(JSONObject().put("id", it.id.id).put("enabled", it.enabled).put("cover", it.cover).put("inner", it.inner))
                        }
                    },
                )
            }
            array.put(json)
        }
        return array.toString()
    }

    private fun decodeChanges(text: String): List<PackageChange>? {
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return null
        return (0 until array.length()).mapNotNull { i ->
            val json = array.optJSONObject(i) ?: return@mapNotNull null
            when (json.optString("kind")) {
                "theme" -> PackageChange.Theme(json.optString("json"))
                "layout" -> PackageChange.Layout(json.optString("json"))
                "iconPack" -> PackageChange.IconPack(json.optString("package"))
                "wallpaper" -> PackageChange.Wallpaper(
                    json.optString("path"),
                    runCatching { java.util.Base64.getDecoder().decode(json.optString("bytes")) }.getOrDefault(ByteArray(0)),
                )
                "tweaks" -> {
                    val list = json.optJSONArray("tweaks") ?: return@mapNotNull null
                    PackageChange.Tweaks(
                        TweakBundle(
                            (0 until list.length()).mapNotNull { k ->
                                val t = list.optJSONObject(k) ?: return@mapNotNull null
                                TweakId.from(t.optString("id"))?.let {
                                    TweakSetting(it, t.optBoolean("enabled"), t.optBoolean("cover", true), t.optBoolean("inner", true))
                                }
                            },
                        ),
                    )
                }
                else -> null
            }
        }
    }

    private companion object {
        const val KEY = "installed:packages"
    }
}
