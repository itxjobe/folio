package com.mccal.folio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mccal.folio.market.InstallResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * An install that's already started, kept outside the screen that started it.
 *
 * Downloading and applying a package can't be stopped halfway - the work is a single block of file and network calls,
 * and cancelling a coroutine doesn't interrupt those. So when someone pressed Back while a package was downloading,
 * the package still landed on their Home screen, and the screen that would have said so was gone: no message, no
 * Undo, and no way to put it back.
 *
 * Keeping the work here means the Market can leave and come back. It holds what's in progress and the result nobody
 * has read yet, so reopening the store shows "… is on · Undo" exactly as if it had never been closed.
 */
internal object MarketWork {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The package being installed, for the "Working…" line. Null when nothing is. */
    var busyId by mutableStateOf<String?>(null)
        private set

    /** A finished install the store hasn't shown yet. */
    var finished by mutableStateOf<Finished?>(null)
        private set

    data class Finished(val name: String, val result: InstallResult)

    /** True while a package is being installed. Only one at a time: two would each record the other out of the list. */
    val busy: Boolean get() = busyId != null

    /**
     * Starts an install, unless one is already running. [work] is the whole thing - download, checks, apply - and it
     * runs to the end whether or not anyone is still looking.
     */
    fun install(id: String, name: String, work: suspend () -> InstallResult): Boolean {
        if (busy) return false
        busyId = id
        scope.launch {
            val result = runCatching { work() }.getOrElse {
                InstallResult.Failed(InstallResult.Reason.APPLY, "Folio couldn't finish that install")
            }
            finished = Finished(name, result)
            busyId = null
        }
        return true
    }

    /** Called by the store once it has said what happened. */
    fun taken(): Finished? = finished.also { finished = null }
}
