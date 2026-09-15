package com.mccal.folio

import android.content.Context
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One version's notes from CHANGELOG.md: its number, date, and bullet points grouped by heading (Added, Changed, Fixed). */
data class ReleaseNotes(val version: String, val date: String?, val sections: List<Pair<String, List<String>>>)

internal object WhatsNew {
    private const val PREFS = "whats_new"
    private const val SEEN = "seen_version"

    /** Parses Keep a Changelog sections ("## [1.2.0] - date", "### Added", "- item"); stops at non-version headings. */
    fun parse(markdown: String): List<ReleaseNotes> {
        val releases = mutableListOf<ReleaseNotes>()
        var version: String? = null; var date: String? = null
        val sections = mutableListOf<Pair<String, MutableList<String>>>()
        fun flush() { version?.let { v -> releases += ReleaseNotes(v, date, sections.filter { it.second.isNotEmpty() }.map { it.first to it.second.toList() }) } }
        markdown.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            val heading = Regex("""^## \[(\d+\.\d+\.\d+[^\]]*)](?:\s*-\s*(.+))?""").find(line)
            when {
                heading != null -> { flush(); version = heading.groupValues[1]; date = heading.groupValues[2].ifBlank { null }; sections.clear() }
                line.startsWith("## ") -> { flush(); version = null; sections.clear() }
                version != null && line.startsWith("### ") -> sections += line.removePrefix("### ").trim() to mutableListOf()
                version != null && line.startsWith("- ") -> {
                    if (sections.isEmpty()) sections += "" to mutableListOf()
                    sections.last().second += line.removePrefix("- ").trim()
                }
            }
        }
        flush()
        return releases
    }

    fun notes(context: Context): List<ReleaseNotes> =
        runCatching { context.assets.open("CHANGELOG.md").bufferedReader().use { parse(it.readText()) } }.getOrDefault(emptyList())

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""

    /**
     * Whether to show What's New now: only after an update to a version with notes, once. A first install records the
     * version without showing (the welcome covers it).
     */
    fun shouldShow(context: Context, firstRun: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val current = currentVersion(context)
        val seen = prefs.getString(SEEN, null)
        if (seen == current) return false
        if (firstRun || seen == null && !context.getSharedPreferences(SettingKeys.PREFS, 0).contains(SettingKeys.STATE)) {
            markSeen(context); return false
        }
        return notes(context).any { it.version == current }
    }

    fun markSeen(context: Context) = context.getSharedPreferences(PREFS, 0).edit().putString(SEEN, currentVersion(context)).apply()
}

/** iOS-style "What's New": the version's changes grouped under their headings, with a Continue button. */
@androidx.compose.runtime.Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun WhatsNewSheet(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = androidx.compose.runtime.remember { WhatsNew.currentVersion(context) }
    val notes = androidx.compose.runtime.remember { WhatsNew.notes(context) }
    val release = notes.firstOrNull { it.version == version } ?: notes.firstOrNull()
    val older = notes.filter { it != release }
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(setOf<String>()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(horizontal = 24.dp).testTag("whats-new")) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            androidx.compose.foundation.lazy.LazyColumn(androidx.compose.ui.Modifier.weight(1f).edgeFade(listState), state = listState, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                item {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        folioIconBitmap(context)?.let { androidx.compose.foundation.Image(it, null, androidx.compose.ui.Modifier.size(72.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))) }
                        androidx.compose.material3.Text("What's New in Folio", color = androidx.compose.ui.graphics.Color.White, fontSize = 30.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = androidx.compose.ui.Modifier.padding(top = 14.dp))
                        release?.let { androidx.compose.material3.Text("Version ${it.version}" + (it.date?.let { d -> " · $d" } ?: ""),
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), fontSize = 15.sp) }
                    }
                }
                release?.sections?.forEach { (heading, items) ->
                    if (heading.isNotEmpty()) item { SheetGroupLabel(heading) }
                    item {
                        SheetGroup {
                            items.forEachIndexed { index, text ->
                                if (index > 0) MenuDivider()
                                androidx.compose.material3.Text(text, color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp,
                                    modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
                            }
                        }
                    }
                }
                // Version History: every earlier release, collapsed like iOS disclosure rows.
                if (older.isNotEmpty()) item { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(top = 12.dp)) { SheetGroupLabel("Version History") } }
                older.forEach { notes ->
                    item(key = "history-${notes.version}") {
                        val open = notes.version in expanded
                        SheetGroup {
                            androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable { expanded = if (open) expanded - notes.version else expanded + notes.version }
                                .padding(horizontal = 16.dp).testTag("history-${notes.version}"), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.Text("Version ${notes.version}", color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp,
                                    modifier = androidx.compose.ui.Modifier.weight(1f))
                                notes.date?.let { androidx.compose.material3.Text(it, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), fontSize = 15.sp) }
                                androidx.compose.material3.Icon(if (open) androidx.compose.material.icons.Icons.Rounded.ExpandLess else androidx.compose.material.icons.Icons.Rounded.ExpandMore,
                                    null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = .4f), modifier = androidx.compose.ui.Modifier.padding(start = 8.dp).size(20.dp))
                            }
                            if (open) notes.sections.forEach { (heading, items) ->
                                MenuDivider()
                                if (heading.isNotEmpty()) androidx.compose.material3.Text(heading.uppercase(), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .5f),
                                    fontSize = 12.sp, modifier = androidx.compose.ui.Modifier.padding(start = 16.dp, top = 10.dp))
                                items.forEach { text ->
                                    androidx.compose.material3.Text("• $text", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .85f), fontSize = 15.sp,
                                        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
                                }
                            }
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxWidth().padding(vertical = 16.dp).heightIn(min = 52.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Color(0xFF0A84FF))
                .clickable(onClick = onDismiss).testTag("whats-new-continue"), contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.compose.material3.Text("Continue", color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        }
    }
}
