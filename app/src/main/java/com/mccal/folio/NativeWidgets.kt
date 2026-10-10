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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Person
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
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
const val LOCATION_WIDGET = -12

/** Folio's own glass widgets added in the polish pass. */
internal val NativeWidgetIds = setOf(WEATHER_WIDGET, BATTERY_WIDGET, NOW_PLAYING_WIDGET, LOCATION_WIDGET)

internal data class Weather(val tempC: Double, val code: Int, val highC: Double, val lowC: Double, val place: String? = null,
    /** Next hours (hour of day, temp, code) and the days ahead (day, code, high, low) for the medium and large sizes. */
    val hours: List<Triple<Int, Double, Int>> = emptyList(), val days: List<WeatherDay> = emptyList())
internal data class WeatherDay(val date: java.time.LocalDate, val code: Int, val highC: Double, val lowC: Double)

/** WMO weather code to a short label, as Open-Meteo reports it. */
internal fun weatherLabel(code: Int) = when (code) {
    0 -> R.string.weather_clear; 1, 2 -> R.string.weather_partly_cloudy; 3 -> R.string.weather_cloudy; 45, 48 -> R.string.weather_fog
    in 51..57 -> R.string.weather_drizzle; in 61..67, in 80..82 -> R.string.weather_rain; in 71..77, 85, 86 -> R.string.weather_snow
    in 95..99 -> R.string.weather_thunderstorms
    else -> R.string.weather_unknown
}

internal fun parseOpenMeteo(json: String): Weather? = runCatching {
    val o = JSONObject(json); val cur = o.getJSONObject("current"); val d = o.getJSONObject("daily")
    val hours = o.optJSONObject("hourly")?.let { h ->
        val times = h.getJSONArray("time"); val temps = h.getJSONArray("temperature_2m"); val codes = h.getJSONArray("weather_code")
        val now = cur.optString("time").take(13)
        val start = (0 until times.length()).firstOrNull { times.getString(it).take(13) >= now } ?: 0
        (start until minOf(start + 6, times.length())).map { Triple(times.getString(it).substring(11, 13).toInt(), temps.getDouble(it), codes.getInt(it)) }
    }.orEmpty()
    val days = d.optJSONArray("time")?.let { t ->
        (0 until t.length()).mapNotNull { i -> runCatching { WeatherDay(java.time.LocalDate.parse(t.getString(i)), d.getJSONArray("weather_code").getInt(i),
            d.getJSONArray("temperature_2m_max").getDouble(i), d.getJSONArray("temperature_2m_min").getDouble(i)) }.getOrNull() }
    }.orEmpty()
    Weather(cur.getDouble("temperature_2m"), cur.getInt("weather_code"),
        d.getJSONArray("temperature_2m_max").getDouble(0), d.getJSONArray("temperature_2m_min").getDouble(0), hours = hours, days = days)
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
        "&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=6")
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
            else -> WeatherBody(w, ink.primary, ink.secondary)
        }
    }
}

@Composable
private fun WeatherBody(w: Weather, primary: androidx.compose.ui.graphics.Color, secondary: androidx.compose.ui.graphics.Color) {
    val size = LocalWidgetSize.current
    Text(deg(w.tempC), color = primary, fontWeight = FontWeight.Light, fontSize = FolioType.WIDGET_FIGURE.sp)
    Text(androidx.compose.ui.res.stringResource(weatherLabel(w.code)), color = primary, fontSize = FolioType.GROUP_LABEL.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    Text("H:${deg(w.highC)} L:${deg(w.lowC)}", color = secondary, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1)
    // Medium and large add the next hours in a row; large adds the days ahead, like iOS.
    if (size != WidgetSize.SMALL && w.hours.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        w.hours.forEach { (h, t, c) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (h == 0) "12a" else if (h < 12) "${h}a" else if (h == 12) "12p" else "${h - 12}p", color = secondary, fontSize = FolioType.GROUP_LABEL.sp)
                Icon(weatherIcon(c), null, tint = primary, modifier = Modifier.size(18.dp))
                Text(deg(t), color = primary, fontSize = FolioType.GROUP_LABEL.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    if (size == WidgetSize.LARGE) w.days.drop(1).take(5).forEach { d ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()), color = primary,
                fontSize = FolioType.FOOTNOTE.sp, modifier = Modifier.width(44.dp))
            Icon(weatherIcon(d.code), null, tint = primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.weight(1f))
            Text("${deg(d.lowC)}  ${deg(d.highC)}", color = primary, fontSize = FolioType.FOOTNOTE.sp)
        }
    }
}

private fun weatherIcon(code: Int) = when (code) {
    0 -> Icons.Rounded.WbSunny; 1, 2, 3, 45, 48 -> Icons.Rounded.Cloud
    in 71..77, 85, 86 -> Icons.Rounded.AcUnit; in 95..99 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.WaterDrop
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
                charging = isCharging(i.getIntExtra(BatteryManager.EXTRA_STATUS, -1), i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0))
                devices = bluetoothLevels(c)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val size = LocalWidgetSize.current
    GlassCard(onClick = onClick) {
        Text(androidx.compose.ui.res.stringResource(R.string.batteries), color = ink.secondary, fontSize = FolioType.GROUP_LABEL.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        // iOS Batteries: a ring per device, green while charging, red at 20% or less.
        val all = listOf(androidx.compose.ui.res.stringResource(R.string.battery_phone) to level) + devices
        val shown = all.take(when (size) { WidgetSize.SMALL -> 1; WidgetSize.MEDIUM -> 4; WidgetSize.LARGE -> 6 })
        if (size == WidgetSize.LARGE) shown.forEachIndexed { i, (name, l) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BatteryRing(l, i == 0 && charging, ink.primary, 34.dp)
                Spacer(Modifier.width(10.dp))
                Text(name, color = ink.primary, fontSize = FolioType.FOOTNOTE.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(if (l >= 0) "$l%" else "—", color = ink.primary, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold)
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            shown.forEachIndexed { i, (_, l) -> BatteryRing(l, i == 0 && charging, ink.primary, if (size == WidgetSize.SMALL) 64.dp else 52.dp) }
        }
        if (size != WidgetSize.LARGE) Text(if (level >= 0) "$level%" else "—", color = ink.primary, fontWeight = FontWeight.SemiBold, fontSize = FolioType.SUBHEAD.sp)
    }
}

@Composable
private fun BatteryRing(level: Int, charging: Boolean, ink: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp) {
    val color = when { charging -> FolioColors.Green; level in 0..20 -> FolioColors.Red; else -> FolioColors.Green }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * .1f
            val inset = stroke / 2
            val arc = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(ink.copy(alpha = .18f), 0f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            if (level > 0) drawArc(color, -90f, 360f * level.coerceAtMost(100) / 100f, false, androidx.compose.ui.geometry.Offset(inset, inset), arc,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Icon(if (charging) Icons.Rounded.Bolt else Icons.Rounded.PhoneAndroid, null, tint = ink, modifier = Modifier.size(size * .38f))
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
            val art = remember(meta) { meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART) }
            val size = LocalWidgetSize.current
            if (art != null && size != WidgetSize.SMALL) androidx.compose.foundation.Image(art.asImageBitmap(), null,
                Modifier.size(if (size == WidgetSize.LARGE) 140.dp else 56.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Text(meta.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "", color = ink.primary, fontWeight = FontWeight.SemiBold,
                fontSize = FolioType.SUBHEAD.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "", color = ink.secondary,
                fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (LocalWidgetSize.current != WidgetSize.SMALL) {
                    Icon(Icons.Rounded.SkipPrevious, androidx.compose.ui.res.stringResource(R.string.previous), tint = ink.primary,
                        modifier = Modifier.size(30.dp).clickable { c.transportControls.skipToPrevious(); tick++ })
                    Spacer(Modifier.width(16.dp))
                }
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

internal data class Place(val street: String?, val city: String?, val lat: Double, val lon: Double)

@android.annotation.SuppressLint(PERMISSION_CHECKED)
private suspend fun currentPlace(context: Context): Place? = withContext(Dispatchers.IO) {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return@withContext null
    val lm = context.getSystemService(android.location.LocationManager::class.java) ?: return@withContext null
    val loc = runCatching { lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time } }.getOrNull()
        ?: return@withContext null
    val a = runCatching {
        @Suppress("DEPRECATION") android.location.Geocoder(context).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
    }.getOrNull()
    Place(a?.thoroughfare, listOfNotNull(a?.locality, a?.adminArea).joinToString(", ").ifBlank { null }, loc.latitude, loc.longitude)
}

/** Find My-style card: a map-tinted square with a pin, the street and the city. Taps open the map there. */
@Composable
internal fun LocationCard(onClick: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    var place by remember { mutableStateOf<Place?>(null) }
    LaunchedEffect(allowed) { while (allowed) { currentPlace(context)?.let { place = it }; delay(15 * 60_000L) } }
    val ink = HomeInk(dark = true)
    GlassCard(onClick = {
        val p = place
        when {
            !allowed -> ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            p != null -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:${p.lat},${p.lon}?z=16"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { onClick() }
            else -> onClick()
        }
    }, paint = androidx.compose.ui.graphics.Brush.linearGradient(listOf(FolioColors.MapLand, FolioColors.MapPark))) {
        Box(Modifier.size(34.dp).background(FolioColors.MapPin, CircleShape).border(2.dp, androidx.compose.ui.graphics.Color.White, CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Person, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(androidx.compose.ui.res.stringResource(R.string.now_label), color = ink.secondary, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1)
            Text(place?.street ?: androidx.compose.ui.res.stringResource(if (allowed) R.string.location else R.string.allow_approximate_location),
                color = ink.primary, fontWeight = FontWeight.SemiBold, fontSize = FolioType.SUBHEAD.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            place?.city?.let { Text(it, color = ink.secondary, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
