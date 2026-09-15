package com.mccal.folio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Software Update (like iOS Settings › General › Software Update): checks GitHub Releases for a newer Folio, downloads
 * the APK, verifies its SHA-256 and that it's signed with the same key as the installed app, then hands it to Android's
 * package installer. Nothing is sent anywhere until you check (or turn on automatic checks); the only request is to
 * GitHub's public releases API. Folio Dev builds update from Android Studio instead, so this is off for them.
 */
internal object SoftwareUpdate {
    private const val RELEASES = "https://api.github.com/repos/McCal-Codes/folio/releases/latest"
    private const val PREFS = "software_update"
    private const val AUTO = "auto"
    private const val LAST_CHECK = "lastCheck"
    private const val AUTO_INSTALL = "autoInstall"
    private const val NOTIFY = "notify"
    private const val NOTIFIED_VERSION = "notifiedVersion"
    private const val CHANNEL = "software_update"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    data class Release(val version: String, val apkUrl: String, val sumsUrl: String?, val notesUrl: String)

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data object UpToDate : Status
        data class Available(val release: Release) : Status
        data class Downloading(val release: Release) : Status
        data object Installing : Status
        data class Failed(val message: String) : Status
    }

    val status = MutableStateFlow<Status>(Status.Idle)

    fun supported(context: Context) = context.packageName == FOLIO_CLASSES
    fun autoCheck(context: Context) = context.getSharedPreferences(PREFS, 0).getBoolean(AUTO, false)
    fun setAutoCheck(context: Context, on: Boolean) = context.getSharedPreferences(PREFS, 0).edit().putBoolean(AUTO, on).apply()
    /** Like iOS "Install iOS Updates": after a daily check finds one, download and install it too. */
    /** Post a notification when a daily check finds an update (off until the user turns it on). */
    fun notify(context: Context) = context.getSharedPreferences(PREFS, 0).getBoolean(NOTIFY, false)
    fun setNotify(context: Context, on: Boolean) = context.getSharedPreferences(PREFS, 0).edit().putBoolean(NOTIFY, on).apply()

    fun canPostNotifications(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** One notification per new version, in its own "Software updates" channel the user can mute in Android settings. */
    private fun postAvailable(context: Context, release: Release) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        if (!notify(context) || !canPostNotifications(context) || prefs.getString(NOTIFIED_VERSION, null) == release.version) return
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel(CHANNEL, "Software updates", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "When a new version of Folio is available" })
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .setAction(android.content.Intent.ACTION_APPLICATION_PREFERENCES).putExtra(EXTRA_OPEN_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Folio ${release.version} is available")
            .setContentText("Tap to see what's new and install it.")
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
        prefs.edit().putString(NOTIFIED_VERSION, release.version).apply()
    }

    const val EXTRA_OPEN_UPDATE = "folio_open_software_update"
    /** Set when the update notification is tapped, so Settings opens straight to Software Update. */
    @Volatile var openRequested = false
    private const val NOTIFICATION_ID = 4101

    fun autoInstall(context: Context) = context.getSharedPreferences(PREFS, 0).getBoolean(AUTO_INSTALL, false)
    fun setAutoInstall(context: Context, on: Boolean) = context.getSharedPreferences(PREFS, 0).edit().putBoolean(AUTO_INSTALL, on).apply()

    fun installedVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    /** 1.10.0 is newer than 1.9.2: numeric comparison part by part. */
    fun isNewer(candidate: String, installed: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate); val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Called when Folio comes to the front: checks at most once a day, only if automatic checks are on. */
    suspend fun checkIfDue(context: Context) {
        if (!supported(context) || !autoCheck(context)) return
        val prefs = context.getSharedPreferences(PREFS, 0)
        if (System.currentTimeMillis() - prefs.getLong(LAST_CHECK, 0) < DAY_MS) return
        check(context)
        val available = (status.value as? Status.Available)?.release ?: return
        if (autoInstall(context)) downloadAndInstall(context, available) else postAvailable(context, available)
    }

    suspend fun check(context: Context) {
        if (!supported(context)) return
        status.value = Status.Checking
        status.value = withContext(Dispatchers.IO) {
            runCatching {
                val json = JSONObject(get(RELEASES))
                context.getSharedPreferences(PREFS, 0).edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()
                val version = json.getString("tag_name").removePrefix("v")
                val assets = json.getJSONArray("assets")
                fun asset(predicate: (String) -> Boolean) = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { predicate(it.getString("name")) }?.getString("browser_download_url")
                val apk = asset { it.endsWith(".apk") } ?: error("This release has no APK.")
                if (isNewer(version, installedVersion(context)))
                    Status.Available(Release(version, apk, asset { it == "SHA256SUMS.txt" }, json.optString("html_url")))
                else Status.UpToDate
            }.getOrElse { Status.Failed("Couldn't check for updates. Check your connection and try again.") }
        }
    }

    /** Downloads, verifies and installs [release]. Android shows its own confirmation when it needs one. */
    suspend fun downloadAndInstall(context: Context, release: Release) {
        status.value = Status.Downloading(release)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
                val apk = File(dir, "Folio-${release.version}.apk")
                download(release.apkUrl, apk)
                release.sumsUrl?.let { url ->
                    val expected = get(url).lines().firstOrNull { it.trim().endsWith(".apk") }?.substringBefore(' ')?.trim()
                    require(expected != null && expected.equals(sha256(apk), ignoreCase = true)) { "The download didn't match its checksum." }
                }
                require(sameSigner(context, apk)) { "The update isn't signed with Folio's key, so it wasn't installed." }
                install(context, apk)
            }
        }
        status.value = result.fold({ Status.Installing }, { Status.Failed(it.message ?: "The update couldn't be installed.") })
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Folio")
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    private fun download(url: String, target: File) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Folio")
        c.connectTimeout = 10_000; c.readTimeout = 60_000; c.instanceFollowRedirects = true
        c.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        c.disconnect()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun sameSigner(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return false
        if (archive.packageName != context.packageName) return false
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val a = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        val b = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        return a.isNotEmpty() && a == b
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Once Folio installed itself, Android 12+ can update it without asking again.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input -> session.openWrite("folio.apk", 0, apk.length()).use { out -> input.copyTo(out); session.fsync(out) } }
            val intent = Intent(context, SoftwareUpdateReceiver::class.java)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }
}

/** Android's installer reports back here; when it needs the user's OK, its confirmation screen is shown. */
class SoftwareUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> SoftwareUpdate.status.value = SoftwareUpdate.Status.Failed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { "The update wasn't installed: $it" } ?: "The update wasn't installed.")
        }
    }
}
