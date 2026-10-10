package com.mccal.folio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.SupplicantState
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.Settings
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DeviceStatus(
    val battery: Int? = null,
    val charging: Boolean = false,
    val wifiConnected: Boolean = false,
    val wifiLevel: Int? = null,
    val cellularLevel: Int? = null,
    val airplane: Boolean = false,
    /** Ringer on silent or vibrate. */
    val silent: Boolean = false,
    /** Mobile data technology as iOS labels it ("5G", "LTE", "3G"), or null when unknown. */
    val networkType: String? = null,
)

/**
 * Charging as Home shows it: on the cable. At a charge limit, like Samsung's battery protection holding at 80%, Android
 * reports the battery as not charging while the phone is still plugged in, and Home used to show it as on battery
 * (StandBy dropping out with it). Plugged in counts now, as iOS shows the bolt while charging is on hold.
 */
internal fun isCharging(status: Int, plugged: Int): Boolean =
    plugged != 0 && status != BatteryManager.BATTERY_STATUS_DISCHARGING

/** Observe only while visible. No location, phone-state, or notification access required. */
class DeviceStatusMonitor(private val context: Context) : DefaultLifecycleObserver {
    private val connection = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val phone = context.getSystemService(TelephonyManager::class.java)
    private val mutable = MutableStateFlow(DeviceStatus())
    val state = mutable.asStateFlow()
    private var networkRegistered = false
    private var phoneRegistered = false
    private var receiverRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateConnection()
        override fun onLost(network: Network) = updateConnection()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = updateConnection()
    }
    /** Kept apart from [phoneCallback]: if Android refuses this one, the signal bars still register. */
    private val displayCallback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
        override fun onDisplayInfoChanged(info: android.telephony.TelephonyDisplayInfo) {
            mutable.update { it.copy(networkType = networkTypeLabel(info.networkType, info.overrideNetworkType)) }
        }
    }
    private var displayRegistered = false
    private val phoneCallback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            mutable.update { it.copy(cellularLevel = signalStrength.level.coerceIn(0, 4)) }
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val charge = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                mutable.update { it.copy(battery = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null,
                    charging = isCharging(charge, plugged)) }
            }
            updateConnection()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(WifiManager.RSSI_CHANGED_ACTION); addAction(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION)
        })
        receiverRegistered = true
        networkRegistered = runCatching { connection.registerDefaultNetworkCallback(networkCallback); true }.getOrDefault(false)
        phoneRegistered = runCatching { phone.registerTelephonyCallback(context.mainExecutor, phoneCallback); true }.getOrDefault(false)
        displayRegistered = runCatching { phone.registerTelephonyCallback(context.mainExecutor, displayCallback); true }.getOrDefault(false)
        mutable.update { it.copy(cellularLevel = runCatching { phone.signalStrength?.level }.getOrNull()) }
        updateConnection()
    }

    @Suppress("DEPRECATION")
    private fun updateConnection() {
        val caps = runCatching { connection.getNetworkCapabilities(connection.activeNetwork) }.getOrNull()
        val info = (caps?.transportInfo as? WifiInfo) ?: runCatching { wifi.connectionInfo }.getOrNull()
        val connected = info?.supplicantState == SupplicantState.COMPLETED || caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val level = info?.rssi?.takeIf { connected && it > -127 }?.let { WifiManager.calculateSignalLevel(it, 5) }
        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        val silent = runCatching { context.getSystemService(android.media.AudioManager::class.java).ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL }
            .getOrDefault(false)
        mutable.update { it.copy(wifiConnected = connected, wifiLevel = level, airplane = airplane, silent = silent) }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (networkRegistered) runCatching { connection.unregisterNetworkCallback(networkCallback) }
        if (phoneRegistered) runCatching { phone.unregisterTelephonyCallback(phoneCallback) }
        if (displayRegistered) runCatching { phone.unregisterTelephonyCallback(displayCallback) }; displayRegistered = false
        if (receiverRegistered) runCatching { context.unregisterReceiver(receiver) }
        networkRegistered = false; phoneRegistered = false; receiverRegistered = false
    }
}

/** iOS's label for the mobile data technology; the 5G override (NSA/Advanced) wins over the LTE it rides on. */
internal fun networkTypeLabel(type: Int, override: Int): String? = when {
    override == android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
        override == android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> NET_5G
    override == android.telephony.TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> NET_LTE_PLUS
    else -> when (type) {
        android.telephony.TelephonyManager.NETWORK_TYPE_NR -> NET_5G
        android.telephony.TelephonyManager.NETWORK_TYPE_LTE, android.telephony.TelephonyManager.NETWORK_TYPE_IWLAN -> NET_LTE
        android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP, android.telephony.TelephonyManager.NETWORK_TYPE_HSPA,
        android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA, android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA,
        android.telephony.TelephonyManager.NETWORK_TYPE_UMTS -> NET_3G
        android.telephony.TelephonyManager.NETWORK_TYPE_EDGE -> NET_EDGE
        android.telephony.TelephonyManager.NETWORK_TYPE_GPRS -> NET_GPRS
        else -> null
    }
}
// Technology names, the same in every language, as iOS shows them.
private const val NET_5G = "5G"
private const val NET_LTE_PLUS = "LTE+"
private const val NET_LTE = "LTE"
private const val NET_3G = "3G"
private const val NET_EDGE = "E"
private const val NET_GPRS = "G"
