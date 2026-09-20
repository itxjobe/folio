package com.mccal.folio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.Source
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Installing an app from the Market, the way Sileo does it: the store downloads, checks, and hands it over,
 * rather than sending you to somebody else's app and hoping you come back.
 *
 * **Off by default, and any source the user added may offer an app** (McCal, 2026-09-20). That is Cydia's and
 * Sileo's bargain, said plainly: you chose the source, so you chose what it may hand you.
 *
 * Four things still hold, and the setting's own words say so:
 *
 * - The index that named the app is **signed** by the source, and its key was pinned when it was added, so the
 *   listing is really that source's.
 * - The bytes must match the **checksum** the index promised, checked before the file is written anywhere
 *   Android can reach, so nothing can be swapped in on the way.
 * - **Android asks, every time.** Folio isn't the installer of record for other people's apps, so the system's
 *   own install screen appears with the app's real name on it.
 * - It is **off until someone turns it on**.
 *
 * What is not protected, and cannot be: Folio can't tell whether the app itself is any good. Software Update
 * can insist an update is signed with the same key as the copy already running; there is no such anchor for
 * somebody else's app. The source vouches for it, and that is the whole of it.
 */
internal object MarketApkInstall {

    /** Where an install has got to, for the store's banner. */
    sealed interface Status {
        data object Idle : Status
        data class Working(val name: String) : Status
        data class Handed(val name: String) : Status
        data class Failed(val message: String) : Status
    }

    val status = MutableStateFlow<Status>(Status.Idle)

    /**
     * Whether Folio may install [entry] itself: the setting is on, and the listing has an APK with a checksum to
     * check it against. A listing with no checksum is never installed, whatever the setting says - there would
     * be nothing to compare the download with.
     */
    fun canInstall(source: Source, entry: IndexPackage, on: Boolean): Boolean =
        on && entry.url != null && entry.sha256 != null && source.kind != Source.Kind.BUILT_IN

    /**
     * Downloads the APK, checks it against the checksum the index promised, and hands it to Android.
     *
     * The bytes are checked before the file is written anywhere Android can reach, so a source that serves
     * something other than what it listed never gets as far as the install screen.
     */
    suspend fun install(
        context: Context,
        name: String,
        entry: IndexPackage,
        fetch: suspend (String, (Long, Long) -> Unit) -> ByteArray?,
    ): Boolean {
        val url = entry.url ?: return fail(context, R.string.that_listing_has_no_app_to_download)
        val expected = entry.sha256 ?: return fail(context, R.string.that_listing_has_no_app_to_download)
        status.value = Status.Working(name)
        val bytes = fetch(url) { read, total -> MarketWork.downloaded(read, total) }
            ?: return fail(context, R.string.folio_couldn_t_download_that_app)
        if (!sha256(bytes).equals(expected, ignoreCase = true)) {
            return fail(context, R.string.that_app_didn_t_match_what_its_source)
        }
        MarketWork.applying()
        val apk = File(context.cacheDir, "market-install.apk")
        return runCatching {
            apk.writeBytes(bytes)
            hand(context, apk)
            status.value = Status.Handed(name)
            true
        }.getOrElse {
            runCatching { apk.delete() }
            fail(context, R.string.folio_couldn_t_hand_that_app_to_android)
        }
    }

    private fun sha256(bytes: ByteArray) =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fail(context: Context, message: Int): Boolean {
        status.value = Status.Failed(context.getString(message))
        return false
    }

    private fun hand(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        // No `setAppPackageName` and no `USER_ACTION_NOT_REQUIRED`: this is somebody else's app, so Android asks,
        // and Folio does not get to say which package these bytes claim to be.
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("package.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val pending = PendingIntent.getBroadcast(
                    context, sessionId, Intent(context, MarketInstallReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (error: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        } finally {
            runCatching { apk.delete() }
        }
    }
}

/** Android's answer to an install Folio handed over: the confirm screen, then whether it worked. */
class MarketInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> MarketApkInstall.status.value = MarketApkInstall.Status.Idle
            else -> MarketApkInstall.status.value = MarketApkInstall.Status.Failed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.let { context.getString(R.string.android_didn_t_install_it_1_s, it) }
                    ?: context.getString(R.string.android_didn_t_install_that_app),
            )
        }
    }
}
