package com.mccal.folio

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateOf
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Optional Shizuku access (Bifold 1.2). Nothing depends on it: every caller checks [active] and keeps its normal path
 * when Shizuku is missing, stopped or not allowed. The binder listeners re-check the state, so when Shizuku starts on
 * its own after a reboot the extra features turn on without opening Settings.
 */
internal object ShizukuBridge {
    enum class Status { NOT_INSTALLED, NOT_RUNNING, PERMISSION_NEEDED, ACTIVE }

    const val PACKAGE = "moe.shizuku.privileged.api" // english-only
    const val PREFS = "shizuku" // english-only
    const val KEY_HIDE_HOME = "hide_bars_home" // english-only
    const val KEY_HIDE_GLOBAL = "hide_bars_global" // english-only
    private const val REQUEST_CODE = 4242

    val status = mutableStateOf(Status.NOT_RUNNING)
    private var app: Context? = null
    private var started = false

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh(); applyGlobalBars() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh(); applyGlobalBars() }

    fun start(context: Context) {
        app = context.applicationContext
        refresh()
        if (started) return
        started = true
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
        }
    }

    fun refresh(): Status {
        val context = app
        val next = when {
            context != null && !installed(context) -> Status.NOT_INSTALLED
            !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> Status.NOT_RUNNING
            runCatching { Shizuku.isPreV11() }.getOrDefault(true) -> Status.NOT_RUNNING
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false) -> Status.ACTIVE
            else -> Status.PERMISSION_NEEDED
        }
        status.value = next
        return next
    }

    val active get() = status.value == Status.ACTIVE

    fun installed(context: Context) =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    fun requestPermission() {
        runCatching { if (Shizuku.pingBinder() && !Shizuku.shouldShowRequestPermissionRationale()) Shizuku.requestPermission(REQUEST_CODE) }
        refresh()
    }

    /** Runs one shell command as Shizuku's user (shell, uid 2000). Returns the exit code and output, or null without access. */
    fun run(vararg command: String): Pair<Int, String>? {
        if (refresh() != Status.ACTIVE) return null
        return runCatching {
            // Shizuku 13 keeps newProcess private; it still works and is how its own sample runs a command.
            val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", command.joinToString(" ")), null, null) as Process
            val out = process.inputStream.bufferedReader().readText() + process.errorStream.bufferedReader().readText()
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroy()
            process.exitValue() to out.trim()
        }.getOrNull()
    }

    /** Runs on a background thread, then [done] on the main thread with whether it worked. */
    fun runAsync(vararg command: String, done: (Boolean) -> Unit = {}) {
        Thread {
            val ok = run(*command)?.first == 0
            android.os.Handler(android.os.Looper.getMainLooper()).post { done(ok) }
        }.start()
    }

    // ---- Control Center toggles -------------------------------------------------------------------------------
    fun setWifi(on: Boolean, done: (Boolean) -> Unit) = runAsync("svc wifi " + if (on) "enable" else "disable", done = done) // english-only
    fun setData(on: Boolean, done: (Boolean) -> Unit) = runAsync("svc data " + if (on) "enable" else "disable", done = done) // english-only
    fun setBluetooth(on: Boolean, done: (Boolean) -> Unit) = runAsync("cmd bluetooth_manager " + if (on) "enable" else "disable", done = done) // english-only
    fun setAirplane(on: Boolean, done: (Boolean) -> Unit) = runAsync("cmd connectivity airplane-mode " + if (on) "enable" else "disable", done = done) // english-only
    fun setRotationLocked(locked: Boolean, done: (Boolean) -> Unit) =
        runAsync("settings put system accelerometer_rotation " + if (locked) "0" else "1", done = done) // english-only
    fun setDnd(on: Boolean, done: (Boolean) -> Unit) = runAsync("cmd notification set_dnd " + if (on) "priority" else "off", done = done) // english-only
    /** Wi-Fi hotspot: `cmd wifi start-softap` needs a config, so this uses the tethering service's own toggle. */
    fun setHotspot(on: Boolean, done: (Boolean) -> Unit) =
        runAsync(if (on) "cmd connectivity tethering start wifi || cmd wifi start-softap BifoldHotspot open" else "cmd connectivity tethering stop wifi || cmd wifi stop-softap", done = done) // english-only
    fun hotspotOn(): Boolean? = run("dumpsys wifi | grep -m1 -i 'SoftApState\\|mApState'")?.second?.let { // english-only
        it.contains("ENABLED") && !it.contains("DISABLED") || it.contains("state=13") } // english-only
    fun dataOn(): Boolean? = run("settings get global mobile_data")?.second?.trim()?.let { it == "1" } // english-only

    // ---- System bars -------------------------------------------------------------------------------------------
    private fun prefs() = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun hideOnHome(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDE_HOME, false)
    fun hideGlobally(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDE_GLOBAL, false)
    fun setHideOnHome(context: Context, on: Boolean) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_HIDE_HOME, on).apply()

    /**
     * Everywhere: Android 11 removed `policy_control` (it is written, but One UI 8 / Android 16 ignores it), so the part that
     * really works on the Fold is Samsung's own key for the gesture pill. Both are written and both are undone when turned off.
     * [done] gets whether the immersive key read back, so Settings can say which part took.
     */
    fun setHideGlobally(context: Context, on: Boolean, done: (pill: Boolean, immersive: Boolean) -> Unit) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_HIDE_GLOBAL, on).apply()
        Thread {
            val pill = run("settings put global navigation_bar_gesture_hint " + if (on) "0" else "1")?.first == 0 // english-only
            if (on) run("settings put global policy_control immersive.full=*") else run("settings delete global policy_control") // english-only
            val immersive = on && run("settings get global policy_control")?.second?.contains("immersive") == true // english-only
            android.os.Handler(android.os.Looper.getMainLooper()).post { done(pill, immersive) }
        }.start()
    }

    /** Re-applies the global choice after Shizuku comes back (a reboot resets nothing here, but a One UI update can). */
    private fun applyGlobalBars() {
        val context = app ?: return
        if (active && prefs()?.getBoolean(KEY_HIDE_GLOBAL, false) == true) setHideGlobally(context, true) { _, _ -> }
    }
}
