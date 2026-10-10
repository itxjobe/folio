package com.mccal.folio

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val WEATHER_WIDGET = -9
const val BATTERY_WIDGET = -10
const val NOW_PLAYING_WIDGET = -11

/** Folio's own glass widgets added in the polish pass. */
internal val NativeWidgetIds = setOf(WEATHER_WIDGET, BATTERY_WIDGET, NOW_PLAYING_WIDGET)

internal data class Weather(val tempC: Double, val code: Int, val highC: Double, val lowC: Double, val place: String? = null)

/** WMO weather code to a short label, as Open-Meteo reports it. */
internal fun weatherLabel(code: Int) = when (code) {
    0 -> R.string.weather_clear; 1, 2 -> R.string.weather_partly_cloudy; 3 -> R.string.weather_cloudy; 45, 48 -> R.string.weather_fog
    in 51..57 -> R.string.weather_drizzle; in 61..67, in 80..82 -> R.string.weather_rain; in 71..77, 85, 86 -> R.string.weather_snow
    in 95..99 -> R.string.weather_thunderstorms
    else -> R.string.weather_unknown
}

internal fun parseOpenMeteo(json: String): Weather? = runCatching {
    val o = JSONObject(json); val cur = o.getJSONObject("current"); val d = o.getJSONObject("daily")
    Weather(cur.getDouble("temperature_2m"), cur.getInt("weather_code"),
        d.getJSONArray("temperature_2m_max").getDouble(0), d.getJSONArray("temperature_2m_min").getDouble(0))
}.getOrNull()

/** Open-Meteo: free, no key. Only a coarse fix (two decimals, about 1 km) leaves the phone. */
@android.annotation.SuppressLint(PERMISSION_CHECKED) // Only called once ACCESS_COARSE_LOCATION is granted; runCatching covers revocation.
private suspend fun fetchWeather(context: Context): Weather? = withContext(Dispatchers.IO) {
    val lm = context.getSystemService(android.location.LocationManager::class.java) ?: return@withContext null
    if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return@withContext null
    val loc = runCatching {
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
    }.getOrNull() ?: return@withContext null
    val url = URL("https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f".format(java.util.Locale.US, loc.latitude, loc.longitude) +
        "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1")
    runCatching {
        (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 8000; readTimeout = 8000
            try { inputStream.bufferedReader().readText() } finally { disconnect() }
        }
    }.getOrNull()?.let(::parseOpenMeteo)?.copy(place = runCatching {
        @Suppress("DEPRECATION")
        android.location.Geocoder(context).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea }
    }.getOrNull())
}

private fun usesFahrenheit() = java.util.Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")
private fun deg(c: Double) = if (usesFahrenheit()) "${Math.round(c * 9 / 5 + 32)}°" else "${Math.round(c)}°"

@Composable
internal fun WeatherCard(onClick: () -> Unit) {
    val context = LocalContext.current
    // White on the blue sky whatever Home's ink is, like iOS.
    val ink = HomeInk(dark = false)
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    var weather by remember { mutableStateOf<Weather?>(null) }
    LaunchedEffect(allowed) {
        // Every 30 minutes while Home shows the card.
        while (allowed) { fetchWeather(context)?.let { weather = it }; delay(30 * 60_000L) }
    }
    GlassCard(onClick = { if (!allowed) ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) else onClick() },
        paint = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(FolioColors.WeatherSkyTop, FolioColors.WeatherSkyBottom))) {
        Text(weather?.place ?: androidx.compose.ui.res.stringResource(R.string.weather), color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.GROUP_LABEL.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        val w = weather
        when {
            !allowed -> Text(androidx.compose.ui.res.stringResource(R.string.allow_approximate_location), color = LocalAccent.current.ink, fontSize = FolioType.GROUP_LABEL.sp)
            w == null -> Text("—", color = ink.primary, fontSize = FolioType.WIDGET_FIGURE.sp)
            else -> {
                Text(deg(w.tempC), color = ink.primary, fontWeight = FontWeight.Light, fontSize = FolioType.WIDGET_FIGURE.sp)
                Text(androidx.compose.ui.res.stringResource(weatherLabel(w.code)), color = ink.primary, fontSize = FolioType.GROUP_LABEL.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text("H:${deg(w.highC)} L:${deg(w.lowC)}", color = ink.secondary, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1)
            }
        }
    }
}

/** Phone battery from the sticky broadcast, plus connected Bluetooth devices that report a level (API 33+ hidden; read via reflection, best effort). */
@Composable
internal fun BatteryCard(onClick: () -> Unit) {
    val context = LocalContext.current
    val ink = LocalHomeInk.current
    var level by remember { mutableStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(emptyList<Pair<String, Int>>()) }
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0) level = l * 100 / s.coerceAtLeast(1)
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
                devices = bluetoothLevels(c)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    GlassCard(onClick = onClick) {
        Text(androidx.compose.ui.res.stringResource(R.string.batteries), color = ink.secondary, fontSize = FolioType.GROUP_LABEL.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(if (level >= 0) "$level%" + if (charging) " ⚡" else "" else "—", color = ink.primary, fontWeight = FontWeight.SemiBold, fontSize = FolioType.WIDGET_FIGURE.sp)
        devices.take(2).forEach { (name, l) ->
            Text("$name  $l%", color = ink.secondary, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun bluetoothLevels(context: Context): List<Pair<String, Int>> = runCatching {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter ?: return emptyList()
    @android.annotation.SuppressLint(PERMISSION_CHECKED)
    adapter.bondedDevices.mapNotNull { d ->
        val connected = runCatching { d.javaClass.getMethod("isConnected").invoke(d) as Boolean }.getOrDefault(false)
        val l = runCatching { d.javaClass.getMethod("getBatteryLevel").invoke(d) as Int }.getOrDefault(-1)
        @android.annotation.SuppressLint(PERMISSION_CHECKED) val name = d.name ?: return@mapNotNull null
        if (connected && l in 0..100) name to l else null
    }
}.getOrDefault(emptyList())

/** The active media session, through Folio's notification listener (the access Island already uses). */
@Composable
internal fun NowPlayingCard(onClick: () -> Unit) {
    val context = LocalContext.current
    val ink = LocalHomeInk.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            controller = runCatching {
                context.getSystemService(MediaSessionManager::class.java)
                    .getActiveSessions(ComponentName(context, IslandListenerService::class.java)).firstOrNull()
            }.getOrNull()
            tick++; delay(2000)
        }
    }
    val c = controller
    val meta = remember(c, tick) { c?.metadata }
    val playing = remember(c, tick) { c?.playbackState?.state == PlaybackState.STATE_PLAYING }
    GlassCard(onClick = { c?.sessionActivity?.let { runCatching { it.send() } } ?: onClick() }) {
        Text(androidx.compose.ui.res.stringResource(R.string.now_playing), color = ink.secondary, fontSize = FolioType.GROUP_LABEL.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (c == null || meta == null) {
            Text(if (c == null) androidx.compose.ui.res.stringResource(R.string.nothing_playing) else "—", color = ink.primary, fontSize = FolioType.SUBHEAD.sp, maxLines = 2)
        } else {
            Text(meta.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "", color = ink.primary, fontWeight = FontWeight.SemiBold,
                fontSize = FolioType.SUBHEAD.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "", color = ink.secondary,
                fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) androidx.compose.ui.res.stringResource(R.string.pause) else androidx.compose.ui.res.stringResource(R.string.play), tint = ink.primary,
                    modifier = Modifier.size(30.dp).clickable { c.transportControls.run { if (playing) pause() else play() }; tick++ })
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Rounded.SkipNext, androidx.compose.ui.res.stringResource(R.string.next_track), tint = ink.primary, modifier = Modifier.size(30.dp).clickable { c.transportControls.skipToNext(); tick++ })
            }
        }
    }
}

/** Lint id for calls guarded by a checkSelfPermission just above. */
private const val PERMISSION_CHECKED = "MissingPermission"
