package com.mccal.folio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mccal.folio.market.Capability
import com.mccal.folio.market.DepictionBlock
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.InstallResult
import com.mccal.folio.market.InstalledPackage
import com.mccal.folio.market.FeaturedStyle
import com.mccal.folio.market.PackagePermission
import com.mccal.folio.market.RepoIndex
import com.mccal.folio.market.Section

/** The Market's tabs. Adding a source over the network comes in Phase 6; Sources shows what Folio has today. */
internal enum class MarketTab(val label: String, val icon: ImageVector) {
    FEATURED("Featured", Icons.Rounded.AutoAwesome),
    SOURCES("Sources", Icons.Rounded.Public),
    PACKAGES("Packages", Icons.Rounded.Storefront),
    INSTALLED("Installed", Icons.Rounded.Download),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

/**
 * The Market: the packages Folio ships, what you have, and a page for each one.
 *
 * The layout follows the rest of Folio: one pane with a tab bar on a phone or a cover screen, and a list beside the
 * page when there's room ([isRegularSize]), so folding never loses your place.
 */
@Composable
internal fun MarketScreen(
    session: MarketSession,
    installedTweaks: Set<String>,
    onClose: () -> Unit,
    /** Folio's own Settings, shown in the Settings tab. Without it the tab shows the Market's settings on their own. */
    settingsContent: (@Composable () -> Unit)? = null,
) {
    // Saveable, so folding, rotating or leaving and coming back keeps the tab and the package that was open.
    var tab by rememberSaveable { mutableStateOf(MarketTab.FEATURED) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var introducing by rememberSaveable { mutableStateOf(!session.prefs.introductionSeen) }
    var style by rememberSaveable { mutableStateOf(session.prefs.featuredStyle) }
    var undo by remember { mutableStateOf<InstallResult.Installed?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    // Re-read after every change, so the list always shows what's really installed.
    var revision by rememberSaveable { mutableIntStateOf(0) }
    val index = remember(revision) { session.index() }
    val installed = remember(revision) { session.installed().associateBy { it.id } }

    fun refresh() { revision++ }

    fun get(entry: IndexPackage) {
        when (val result = session.get(entry)) {
            is InstallResult.Installed -> {
                undo = result
                message = "${result.installed.name} is on"
            }
            is InstallResult.NeedsNewerFolio -> message = "${entry.manifest?.name?.english ?: entry.id} needs a newer Folio"
            is InstallResult.Failed -> message = result.message
        }
        refresh()
    }

    fun remove(id: String, name: String) {
        if (session.remove(id)) { undo = null; message = "$name removed" }
        refresh()
    }

    BackHandler(enabled = openId != null) { openId = null }

    if (introducing) {
        MarketIntroduction(
            style = style,
            onStyle = { chosen -> style = chosen; session.prefs.featuredStyle = chosen },
            onDone = { session.prefs.introductionSeen = true; introducing = false },
        )
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val split = isRegularSize(maxWidth.value, maxHeight.value, LocalConfiguration.current.classScale) && maxWidth.value >= 700f
        val packages = index?.packages.orEmpty()
        val open = openId?.let { id -> packages.firstOrNull { it.id == id } }

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f)) {
                if (tab == MarketTab.SETTINGS && settingsContent != null) {
                    Box(Modifier.fillMaxSize()) { settingsContent() }
                } else if (split || open == null) {
                    Box(if (split) Modifier.width(360.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                        MarketList(
                            tab = tab,
                            index = index,
                            packages = packages,
                            installed = installed,
                            openId = openId,
                            style = style,
                            onStyle = { chosen -> style = chosen; session.prefs.featuredStyle = chosen },
                            onIntroduce = { session.prefs.introductionSeen = false; introducing = true },
                            onOpen = { openId = it },
                            onGet = ::get,
                            onRemove = { id, name -> remove(id, name) },
                        )
                    }
                }
                if (open != null) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        MarketPackagePage(
                            entry = open,
                            session = session,
                            installed = installed[open.id],
                            showBack = !split,
                            onBack = { openId = null },
                            onGet = { get(open) },
                            onRemove = { remove(open.id, open.manifest?.name?.english ?: open.id) },
                        )
                    }
                }
            }
            message?.let { text ->
                MarketMessage(
                    text = text,
                    undo = undo?.let { result -> { session.undo(result); undo = null; message = null; refresh() } },
                    onDismiss = { message = null },
                )
            }
            MarketTabs(tab) { tab = it; openId = null }
        }
        if (index == null) {
            Text(
                "Folio couldn't read its own packages. Reinstalling the app puts them back.",
                color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }
    }
}

@Composable
private fun MarketTabs(selected: MarketTab, onSelect: (MarketTab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF1C1C1E)).padding(vertical = 6.dp)) {
        for (tab in MarketTab.entries) {
            val on = tab == selected
            Column(
                Modifier.weight(1f).clickable(onClickLabel = tab.label) { onSelect(tab) }
                    .testTag("market-tab-${tab.name.lowercase()}").padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, contentDescription = null, tint = if (on) Color(0xFF0A84FF) else Color.White.copy(alpha = .55f), modifier = Modifier.size(22.dp))
                Text(tab.label, color = if (on) Color(0xFF0A84FF) else Color.White.copy(alpha = .55f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MarketList(
    tab: MarketTab,
    index: RepoIndex?,
    packages: List<IndexPackage>,
    installed: Map<String, InstalledPackage>,
    openId: String?,
    style: FeaturedStyle,
    onStyle: (FeaturedStyle) -> Unit,
    onIntroduce: () -> Unit,
    onOpen: (String) -> Unit,
    onGet: (IndexPackage) -> Unit,
    onRemove: (String, String) -> Unit,
) {
    val shown = when (tab) {
        MarketTab.FEATURED, MarketTab.PACKAGES -> packages
        MarketTab.INSTALLED -> packages.filter { it.id in installed }
        MarketTab.SOURCES, MarketTab.SETTINGS -> emptyList()
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                if (tab == MarketTab.FEATURED) Text("Welcome to Folio", color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
                Text(tab.label, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (tab == MarketTab.FEATURED && index != null) {
            item(key = "featured") {
                MarketFeatured(
                    featured = index.featured,
                    packages = packages,
                    calm = style == FeaturedStyle.CALM,
                    onOpen = onOpen,
                )
            }
        }
        if (tab == MarketTab.SOURCES) {
            item(key = "sources") { MarketSources(index) }
        }
        if (tab == MarketTab.SETTINGS) {
            item(key = "settings") { MarketSettings(style = style, onStyle = onStyle, onIntroduce = onIntroduce) }
        }
        if (shown.isEmpty() && tab == MarketTab.INSTALLED) {
            item {
                Text(
                    "Nothing yet. Themes and tweaks you get show up here.",
                    color = Color.White.copy(alpha = .55f), modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        for (section in Section.entries) {
            val inSection = shown.filter { it.manifest?.section == section }
            if (inSection.isEmpty()) continue
            item(key = "label-${section.id}") { SheetGroupLabel(section.id.replaceFirstChar(Char::uppercase)) }
            item(key = "group-${section.id}") {
                SheetGroup(Modifier.padding(bottom = 10.dp)) {
                    for (entry in inSection) {
                        MarketRow(
                            entry = entry,
                            installed = installed[entry.id],
                            selected = entry.id == openId,
                            onOpen = { onOpen(entry.id) },
                            onGet = { onGet(entry) },
                            onRemove = { onRemove(entry.id, entry.manifest?.name?.english ?: entry.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketRow(
    entry: IndexPackage,
    installed: InstalledPackage?,
    selected: Boolean,
    onOpen: () -> Unit,
    onGet: () -> Unit,
    onRemove: () -> Unit,
) {
    val name = entry.manifest?.name?.english ?: entry.id
    val author = entry.manifest?.author?.name?.english.orEmpty()
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Color.White.copy(alpha = .06f) else Color.Transparent)
            .clickable(onClickLabel = "Open $name", onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 16.sp)
            Text(
                if (installed?.enabled == false) "Turned off after a crash" else author,
                color = if (installed?.enabled == false) Color(0xFFFFB340) else Color.White.copy(alpha = .55f),
                fontSize = 13.sp,
            )
        }
        if (entry.needs.isNotEmpty()) {
            Text("Needs a newer Folio", color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
        } else {
            MarketActionButton(
                label = if (installed != null) "Remove" else "Get",
                name = name,
                onClick = if (installed != null) onRemove else onGet,
            )
        }
    }
}

/**
 * Sources: where packages come from. Folio's own comes with the app and needs no network; adding one over the network,
 * with its key pinned by fingerprint, is Phase 6.
 */
@Composable
private fun MarketSources(index: RepoIndex?) {
    Column {
        SheetGroupLabel("Sources")
        SheetGroup(Modifier.padding(bottom = 10.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text(index?.name?.english ?: "Folio", color = Color.White, fontSize = 16.sp)
                Text(
                    "Built into the app. ${index?.packages?.size ?: 0} packages, no network.",
                    color = Color.White.copy(alpha = .55f), fontSize = 13.sp,
                )
            }
        }
        Text(
            "Sources other people publish come next: Folio shows a source's key fingerprint before you trust it, and " +
                "refuses one that changes its key without asking you.",
            color = Color.White.copy(alpha = .55f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

/** The Market's own settings: how Featured looks, and the introduction again. */
@Composable
private fun MarketSettings(style: FeaturedStyle, onStyle: (FeaturedStyle) -> Unit, onIntroduce: () -> Unit) {
    Column {
        SheetGroupLabel("Featured style")
        IosSegmented(
            options = FeaturedStyle.entries.map { it to it.label },
            selected = style,
            onSelect = onStyle,
            modifier = Modifier.padding(vertical = 8.dp),
            tag = "market-featured-style",
        )
        Text(
            FeaturedStyle.entries.first { it == style }.description,
            color = Color.White.copy(alpha = .55f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp),
        )
        SheetGroup(Modifier.padding(bottom = 10.dp)) {
            IosActionRow("Show the introduction again", onClick = onIntroduce)
        }
        SheetGroupLabel("Folio")
        val context = androidx.compose.ui.platform.LocalContext.current
        SheetGroup(Modifier.padding(bottom = 16.dp)) {
            // Wallpaper, Home, tweaks and the rest still live in Folio's own Settings. Bringing those pages in here is
            // the next slice; until then this opens them rather than showing half of them twice.
            IosActionRow("Open Folio Settings") {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_APPLICATION_PREFERENCES)
                            .setPackage(context.packageName)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }
}

/** The Get / Remove pill. Its name says which package it belongs to, so a screen reader hears more than "Get". */
@Composable
private fun MarketActionButton(label: String, name: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color(0xFF0A84FF),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            // 44 dp tall, so it's a comfortable target rather than just big enough to see.
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = "$label $name" },
    )
}

@Composable
private fun MarketPackagePage(
    entry: IndexPackage,
    session: MarketSession,
    installed: InstalledPackage?,
    showBack: Boolean,
    onBack: () -> Unit,
    onGet: () -> Unit,
    onRemove: () -> Unit,
) {
    val pkg = remember(entry.id) { session.read(entry.id) }
    val name = entry.manifest?.name?.english ?: entry.id
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (showBack) {
            Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Back", onClick = onBack).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(18.dp))
                Text("Back", color = Color(0xFF0A84FF), fontSize = 16.sp)
            }
        }
        Text(name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        entry.manifest?.author?.name?.english?.let { Text(it, color = Color.White.copy(alpha = .55f), fontSize = 14.sp) }

        if (installed?.enabled == false) {
            SheetGroup(Modifier.padding(top = 12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Color(0xFFFFB340), modifier = Modifier.size(20.dp))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("Turned off after a crash", color = Color.White, fontSize = 15.sp)
                        Text(
                            installed.disabledReason ?: "Your settings are kept.",
                            color = Color.White.copy(alpha = .55f), fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MarketActionButton(if (installed != null) "Remove" else "Get", name, if (installed != null) onRemove else onGet)
            Spacer(Modifier.width(8.dp))
            Text(
                if (installed != null) "Version ${installed.version}" else "Built in",
                color = Color.White.copy(alpha = .55f), fontSize = 13.sp,
            )
        }

        entry.manifest?.description?.english?.let {
            Text(it, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(bottom = 12.dp))
        }

        // The page the author wrote: Folio draws each block itself, and skips any it doesn't know.
        pkg?.depiction?.blocks?.forEach { block ->
            when (block) {
                is DepictionBlock.Markdown -> Text(block.text.english, color = Color.White.copy(alpha = .85f), fontSize = 15.sp, modifier = Modifier.padding(bottom = 10.dp))
                is DepictionBlock.FeatureList -> Column(Modifier.padding(bottom = 10.dp)) {
                    block.items.forEach { Text("· ${it.english}", color = Color.White.copy(alpha = .85f), fontSize = 15.sp) }
                }
                is DepictionBlock.Changelog -> Column(Modifier.padding(bottom = 10.dp)) {
                    SheetGroupLabel("What's new")
                    block.entries.forEach { Text("${it.version} — ${it.notes.english}", color = Color.White.copy(alpha = .7f), fontSize = 14.sp) }
                }
                else -> Unit
            }
        }

        // The privacy label comes from the manifest's permissions, never from anything the author wrote.
        SheetGroupLabel(if (entry.manifest?.permissions.isNullOrEmpty()) "No data collected" else "What this package changes")
        SheetGroup(Modifier.padding(bottom = 24.dp)) {
            val lines = entry.manifest?.permissions.orEmpty().mapNotNull(PackagePermission::label)
            if (lines.isEmpty()) {
                Text("Changes appearance only.", color = Color.White.copy(alpha = .7f), fontSize = 14.sp, modifier = Modifier.padding(14.dp))
            } else {
                Column(Modifier.padding(14.dp)) {
                    lines.forEach { Text(it, color = Color.White.copy(alpha = .85f), fontSize = 14.sp) }
                }
            }
        }
    }
}

/** The "… is on · Undo" line, the same shape as Folio's other undo messages. */
@Composable
private fun MarketMessage(text: String, undo: (() -> Unit)?, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp)).background(Color(0xFF2C2C2E))
            .clickable(onClickLabel = "Dismiss", onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (undo != null) {
            Text("Undo", color = Color(0xFF0A84FF), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = undo))
        }
    }
}

/** What Folio can do today, for the "Needs a newer Folio" check. Kept next to the screen that shows it. */
internal val MARKET_CAPABILITIES: Set<Capability> = MarketHost(NoLauncher).capabilities

private object NoLauncher : MarketLauncher {
    override val state = LauncherState()
    override fun installTweak(feature: TweakFeature) = Unit
    override fun removeTweak(feature: TweakFeature) = Unit
    override fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue) = Unit
    override fun applyTheme(theme: FolioTheme) = Unit
}

/**
 * Which sheet the Folio app icon opens. The Market holds Settings as a tab, so the icon opens the Market once it
 * exists — but anything that asked for a particular Settings page (a permission prompt, an update) still gets
 * Settings, because that's what it asked for.
 */
internal fun sheetForAppIcon(linkedPage: CustomizationPage?, currentPage: CustomizationPage, marketEnabled: Boolean): String =
    if (linkedPage == null && currentPage == CustomizationPage.OVERVIEW && marketEnabled) "market" else "settings"
