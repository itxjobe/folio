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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.mccal.folio.market.Capability
import com.mccal.folio.market.DepictionBlock
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.InstallResult
import com.mccal.folio.market.InstalledPackage
import com.mccal.folio.market.FeaturedStyle
import com.mccal.folio.market.PackageManifest
import com.mccal.folio.market.PackagePermission
import com.mccal.folio.market.PackageSafety
import kotlinx.coroutines.launch
import com.mccal.folio.market.RefreshResult
import com.mccal.folio.market.RepoIndex
import com.mccal.folio.market.Source
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
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    var addingSource by rememberSaveable { mutableStateOf(false) }
    var sourceUrl by rememberSaveable { mutableStateOf("") }
    var trusting by remember { mutableStateOf<RefreshResult.NeedsTrust?>(null) }
    var statuses by remember { mutableStateOf(emptyList<SourceStatus>()) }
    val scope = rememberCoroutineScope()
    var undo by remember { mutableStateOf<InstallResult.Installed?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    // Re-read after every change, so the list always shows what's really installed. Reading means parsing the
    // bundled index and every cached source list, which is far too much to do while a frame is being drawn - so it
    // happens off the main thread and the screen fills in when it's done.
    var revision by rememberSaveable { mutableIntStateOf(0) }
    val index by produceState<RepoIndex?>(null, revision) { value = withContext(session.io) { session.index() } }
    val entries by produceState(emptyList<MarketEntry>(), revision, statuses) {
        value = withContext(session.io) { session.entries() }
    }
    val installed by produceState(emptyMap<String, InstalledPackage>(), revision) {
        value = withContext(session.io) { session.installed().associateBy { it.id } }
    }
    LaunchedEffect(revision) { statuses = withContext(session.io) { session.sources.cached() } }
    // Installing outlives this screen: the work can't be stopped halfway, so it's kept where Back can't reach it.
    val busyId = MarketWork.busyId

    fun refresh() { revision++ }

    /**
     * Says something in the banner. Undo belongs to the install it came from, so any other message takes it away:
     * an Undo left over from an earlier install would remove a package the user is happy with.
     */
    fun say(text: String?) { message = text; undo = null }

    /** What the banner says about a finished install, and whether it can still be undone. */
    fun announce(name: String, result: InstallResult) {
        when (result) {
            is InstallResult.Installed -> { message = "${result.installed.name} is on"; undo = result }
            is InstallResult.NeedsNewerFolio -> say("$name needs a newer Folio")
            is InstallResult.Failed -> say(result.message)
        }
        refresh()
    }

    fun apply(entry: MarketEntry) {
        confirming = null
        // One at a time. Two installs at once would each write the list of what's installed from a copy read before
        // the other started, so one package would be applied to Home and forgotten, with no way left to remove it.
        MarketWork.install(entry.id, entry.name) { session.get(entry) }
    }

    // What finished while nobody was looking. Closing the Market during a download used to lose the message and the
    // Undo that went with it; now the store picks them up when it opens.
    LaunchedEffect(MarketWork.finished) {
        MarketWork.taken()?.let { announce(it.name, it.result) }
    }

    fun remove(id: String, name: String) {
        if (MarketWork.busy) return
        scope.launch {
            val removed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { session.remove(id) }
            if (removed) say("$name removed")
            refresh()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    BackHandler(enabled = openId != null) { openId = null }

    // A .foliopkg someone opened: read it once, then the same confirm sheet as anything else. Keyed on the file
    // itself, so one shared while the Market is already open is read there and then.
    var importing by remember { mutableStateOf<Pair<ByteArray, com.mccal.folio.market.FolioPackage>?>(null) }
    LaunchedEffect(MarketImport.pending) {
        val bytes = MarketImport.pending
        MarketImport.pending = null
        if (bytes != null) {
            when (val read = session.read(bytes)) {
                is com.mccal.folio.market.PackageInstaller.ReadResult.Ok -> importing = bytes to read.pkg
                is com.mccal.folio.market.PackageInstaller.ReadResult.NeedsNewerFolio ->
                    say("That package needs a newer Folio")
                is com.mccal.folio.market.PackageInstaller.ReadResult.Failed -> say(read.message)
            }
        }
    }

    // A shared folio:// link opens straight on that package, once.
    LaunchedEffect(MarketLink.pending) {
        when (val link = MarketLink.pending) {
            is MarketLink.Package -> { tab = MarketTab.PACKAGES; openId = link.id }
            is MarketLink.Source -> {
                // What the format says a source link does: the Add Source sheet, filled in. The fingerprint still
                // has to be confirmed, so a link can't add a source by itself.
                tab = MarketTab.SOURCES
                sourceUrl = link.url
                addingSource = true
            }
            // A code is redeemed by the activity that received it; by the time the Market opens it's already done.
            is MarketLink.Early -> Unit
            null -> Unit
        }
        MarketLink.pending = null
    }

    if (introducing) {
        MarketIntroduction(
            style = style,
            onStyle = { chosen -> style = chosen; session.prefs.featuredStyle = chosen },
            onDone = { session.prefs.introductionSeen = true; introducing = false },
        )
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val regular = isRegularSize(maxWidth.value, maxHeight.value, LocalConfiguration.current.classScale)
        val split = regular && maxWidth.value >= 700f
        // Where the tabs go, the same rule the Mockup Lab draws: a sidebar once the window is as wide as the Fold8
        // inner screen (iPad), a rail on the long edge when the window is too short for a bar under it (the cover
        // screen rotated), and the bar itself everywhere else.
        val tabs = when {
            !regular && maxWidth > maxHeight -> TabPlacement.RAIL
            split && maxWidth.value >= 920f -> TabPlacement.SIDEBAR
            else -> TabPlacement.BOTTOM
        }
        val packages = entries.map { it.entry }
        val open = openId?.let { id -> entries.firstOrNull { it.id == id } }

        Row(Modifier.fillMaxSize()) {
        if (tabs == TabPlacement.SIDEBAR) MarketSidebar(tab) { tab = it; openId = null }
        Column(Modifier.weight(1f)) {
            Row(Modifier.weight(1f)) {
                if (tab == MarketTab.SETTINGS && settingsContent != null) {
                    Box(Modifier.fillMaxSize()) { settingsContent() }
                } else if (split || open == null) {
                    Box(if (split) Modifier.width(360.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                        MarketList(
                            session = session,
                            tab = tab,
                            index = index,
                            statuses = statuses,
                            localDevAllowed = session.localDevAllowed,
                            onAddSource = { addingSource = true },
                            onAddLocalDev = {
                                scope.launch {
                                    val result = session.sources.addLocalDev(DEFAULT_LOCAL_SOURCE)
                                    statuses = withContext(session.io) { session.sources.cached() }
                                    say(refreshMessage(Source(DEFAULT_LOCAL_SOURCE, kind = Source.Kind.LOCAL_DEV), result))
                                    refresh()
                                }
                            },
                            onRefreshSource = { source ->
                                scope.launch {
                                    val result = session.sources.refresh(source.url, force = true)
                                    if (result is RefreshResult.NeedsTrust) trusting = result
                                    statuses = withContext(session.io) { session.sources.cached() }
                                    say(refreshMessage(source, result))
                                    refresh()
                                }
                            },
                            onForgetSource = { source ->
                                scope.launch {
                                    // Deleting a source's cache and its pinned key is file work, not frame work.
                                    withContext(session.io) { session.sources.forget(source.url) }
                                    statuses = withContext(session.io) { session.sources.cached() }
                                    say("${source.label} removed")
                                    refresh()
                                }
                            },
                            entries = entries,
                            installed = installed,
                            openId = openId,
                            busyId = busyId,
                            style = style,
                            onStyle = { chosen -> style = chosen; session.prefs.featuredStyle = chosen },
                            onIntroduce = { session.prefs.introductionSeen = false; introducing = true },
                            onOpen = { openId = it },
                            onGet = { confirming = it.id },
                            onRemove = { id, name -> remove(id, name) },
                        )
                    }
                }
                if (open != null) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        MarketPackagePage(
                            entry = open.entry,
                            session = session,
                            installed = installed[open.id],
                            revoked = open.revokedReason,
                            source = open.source,
                            showBack = !split,
                            onBack = { openId = null },
                            onGet = { confirming = open.id },
                            onRemove = { remove(open.id, open.name) },
                            onShare = { share(context, it) },
                            onReport = { report(context, index?.issuesUrl, it) },
                        )
                    }
                }
            }
            message?.let { text ->
                MarketMessage(
                    text = text,
                    undo = undo?.let { result ->
                        {
                            val undone = result
                            undo = null
                            message = null
                            scope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { session.undo(undone) }
                                refresh()
                            }
                        }
                    },
                    onDismiss = { message = null },
                )
            }
            if (tabs == TabPlacement.BOTTOM) MarketTabs(tab) { tab = it; openId = null }
        }
        if (tabs == TabPlacement.RAIL) MarketRail(tab) { tab = it; openId = null }
        }
        importing?.let { (bytes, pkg) ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).clickable { importing = null }) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Color(0xFF1C1C1E))
                        .clickable(enabled = false) {},
                ) {
                    MarketInstallSheet(
                        manifest = pkg.manifest,
                        origin = InstallOrigin(
                            line = "From a file you opened.",
                            checksum = null,
                            warning = "Nobody signed this file. Only open packages from someone you trust.",
                        ),
                        onGet = {
                            importing = null
                            MarketWork.install(pkg.manifest.id, pkg.manifest.name.english) { session.installFile(bytes) }
                        },
                        onCancel = { importing = null },
                    )
                }
            }
        }
        if (addingSource) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).clickable { addingSource = false }) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Color(0xFF1C1C1E))
                        .clickable(enabled = false) {},
                ) {
                    MarketAddSourceSheet(
                        url = sourceUrl,
                        onUrl = { sourceUrl = it },
                        onNext = {
                            val typed = sourceUrl
                            addingSource = false
                            scope.launch {
                                when (val result = session.sources.inspect(typed)) {
                                    is RefreshResult.NeedsTrust -> trusting = result
                                    is RefreshResult.Failed -> say(result.message)
                                    else -> {
                                        statuses = withContext(session.io) { session.sources.cached() }
                                        say("That source is already set up")
                                    }
                                }
                            }
                        },
                        onCancel = { addingSource = false },
                    )
                }
            }
        }
        trusting?.let { request ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f))) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Color(0xFF1C1C1E)),
                ) {
                    MarketTrustSheet(
                        url = request.url,
                        key = request.key,
                        previous = request.previous,
                        onTrust = {
                            trusting = null
                            scope.launch {
                                val result = session.sources.trust(request.url, request.key)
                                statuses = withContext(session.io) { session.sources.cached() }
                                sourceUrl = ""
                                say(refreshMessage(Source(request.url), result))
                                refresh()
                            }
                        },
                        onCancel = { trusting = null },
                    )
                }
            }
        }
        confirming?.let { id ->
            entries.firstOrNull { it.id == id }?.let { entry ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).clickable { confirming = null }) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Color(0xFF1C1C1E))
                            .clickable(enabled = false) {},
                    ) {
                        MarketInstallSheet(
                            entry = entry.entry,
                            builtIn = entry.source.kind == Source.Kind.BUILT_IN,
                            onGet = { apply(entry) },
                            onCancel = { confirming = null },
                        )
                    }
                }
            }
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
            Column(
                Modifier.weight(1f).marketTab(tab, onSelect).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { MarketTabIcon(tab, tab == selected); MarketTabLabel(tab, tab == selected) }
        }
    }
}

/** Where the tabs sit: under the content, up the trailing edge, or as a labelled sidebar on a big screen. */
private enum class TabPlacement { BOTTOM, RAIL, SIDEBAR }

/**
 * The tabs on the long edge, for a window too short for a bar underneath (the Fold8 cover screen rotated). The
 * content keeps the height it has, which is the scarce direction there.
 */
@Composable
private fun MarketRail(selected: MarketTab, onSelect: (MarketTab) -> Unit) {
    Column(
        Modifier.width(76.dp).fillMaxHeight().background(Color(0xFF1C1C1E)).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (tab in MarketTab.entries) {
            Column(
                Modifier.fillMaxWidth().marketTab(tab, onSelect).padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { MarketTabIcon(tab, tab == selected); MarketTabLabel(tab, tab == selected) }
        }
    }
}

/**
 * A labelled sidebar instead of a tab bar, the way iPad Settings and the App Store use the width they have. The
 * selected row is a rounded highlight, inset from the edges like the sidebar rows in Folio's own Settings.
 */
@Composable
private fun MarketSidebar(selected: MarketTab, onSelect: (MarketTab) -> Unit) {
    Column(
        Modifier.width(180.dp).fillMaxHeight().background(Color(0xFF1C1C1E))
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Market", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 10.dp),
        )
        for (tab in MarketTab.entries) {
            val on = tab == selected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (on) Color(0xFF0A84FF) else Color.Transparent)
                    .marketTab(tab, onSelect).padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    tab.icon, contentDescription = null, modifier = Modifier.size(20.dp),
                    tint = if (on) Color.White else Color.White.copy(alpha = .55f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    tab.label, color = if (on) Color.White else Color.White.copy(alpha = .85f), fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One tab is one control wherever it's drawn, so the tag and the spoken label don't depend on the layout. */
private fun Modifier.marketTab(tab: MarketTab, onSelect: (MarketTab) -> Unit) =
    clickable(onClickLabel = tab.label) { onSelect(tab) }.testTag("market-tab-${tab.name.lowercase()}")

@Composable
private fun MarketTabIcon(tab: MarketTab, on: Boolean) = Icon(
    tab.icon, contentDescription = null, modifier = Modifier.size(22.dp),
    tint = if (on) Color(0xFF0A84FF) else Color.White.copy(alpha = .55f),
)

@Composable
private fun MarketTabLabel(tab: MarketTab, on: Boolean) = Text(
    tab.label, color = if (on) Color(0xFF0A84FF) else Color.White.copy(alpha = .55f), fontSize = 11.sp,
    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
)

@Composable
private fun MarketList(
    session: MarketSession,
    tab: MarketTab,
    index: RepoIndex?,
    statuses: List<SourceStatus>,
    localDevAllowed: Boolean,
    onAddSource: () -> Unit,
    onAddLocalDev: () -> Unit,
    onRefreshSource: (Source) -> Unit,
    onForgetSource: (Source) -> Unit,
    entries: List<MarketEntry>,
    installed: Map<String, InstalledPackage>,
    openId: String?,
    busyId: String?,
    style: FeaturedStyle,
    onStyle: (FeaturedStyle) -> Unit,
    onIntroduce: () -> Unit,
    onOpen: (String) -> Unit,
    onGet: (MarketEntry) -> Unit,
    onRemove: (String, String) -> Unit,
) {
    // A package is an update when a source offers a higher version than the one installed.
    val updates = entries.filter { entry ->
        installed[entry.id]?.let { entry.entry.version > it.version } == true
    }
    val shown = when (tab) {
        MarketTab.FEATURED, MarketTab.PACKAGES -> entries
        MarketTab.INSTALLED -> entries.filter { it.id in installed } - updates.toSet()
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
                    // Folio's own packages only. The banners come from Folio's index, so what they point at has to
                    // come from there too, or a source could put itself in Folio's window by reusing an id.
                    packages = index.packages,
                    calm = style == FeaturedStyle.CALM,
                    onOpen = onOpen,
                )
            }
        }
        if (tab == MarketTab.SOURCES) {
            item(key = "sources") {
                MarketSourcesTab(
                    builtInName = index?.name?.english ?: "Folio",
                    builtInCount = entries.count { it.source.kind == Source.Kind.BUILT_IN },
                    statuses = statuses,
                    localDevAllowed = localDevAllowed,
                    onAdd = onAddSource,
                    onAddLocalDev = onAddLocalDev,
                    onRefresh = onRefreshSource,
                    onForget = onForgetSource,
                )
            }
        }
        if (tab == MarketTab.SETTINGS) {
            item(key = "settings") { MarketOwnSettings(style = style, onStyle = onStyle, onIntroduce = onIntroduce) }
        }
        if (tab == MarketTab.INSTALLED && updates.isNotEmpty()) {
            item(key = "updates-label") { SheetGroupLabel("Updates") }
            item(key = "updates") {
                SheetGroup(Modifier.padding(bottom = 10.dp)) {
                    for (entry in updates) {
                        MarketRow(
                            session = session,
                            entry = entry,
                            installed = installed[entry.id],
                            busy = entry.id == busyId,
                            update = true,
                            selected = entry.id == openId,
                            onOpen = { onOpen(entry.id) },
                            onGet = { onGet(entry) },
                            onRemove = { onRemove(entry.id, entry.name) },
                        )
                    }
                }
            }
        }
        if (shown.isEmpty() && tab == MarketTab.INSTALLED && updates.isEmpty()) {
            item {
                Text(
                    "Nothing yet. Themes and tweaks you get show up here.",
                    color = Color.White.copy(alpha = .55f), modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        for (section in Section.entries) {
            val inSection = shown.filter { it.entry.manifest?.section == section }
            if (inSection.isEmpty()) continue
            item(key = "label-${section.id}") { SheetGroupLabel(section.id.replaceFirstChar(Char::uppercase)) }
            item(key = "group-${section.id}") {
                SheetGroup(Modifier.padding(bottom = 10.dp)) {
                    for (entry in inSection) {
                        MarketRow(
                            session = session,
                            entry = entry,
                            installed = installed[entry.id],
                            busy = entry.id == busyId,
                            selected = entry.id == openId,
                            onOpen = { onOpen(entry.id) },
                            onGet = { onGet(entry) },
                            onRemove = { onRemove(entry.id, entry.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketRow(
    session: MarketSession,
    entry: MarketEntry,
    installed: InstalledPackage?,
    busy: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onGet: () -> Unit,
    onRemove: () -> Unit,
    update: Boolean = false,
) {
    val name = entry.name
    val author = entry.entry.manifest?.author?.name?.english.orEmpty()
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Color.White.copy(alpha = .06f) else Color.Transparent)
            .clickable(onClickLabel = "Open $name", onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PackageIcon(session, entry, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 16.sp)
            Text(
                when {
                    installed?.enabled == false -> "Turned off after a crash"
                    entry.revokedReason != null -> entry.revokedReason
                    update -> "${installed?.version} → ${entry.entry.version}"
                    // A package from somewhere other than Folio says where it came from.
                    entry.source.kind != Source.Kind.BUILT_IN -> "$author · ${entry.source.label}"
                    else -> author
                },
                color = when {
                    installed?.enabled == false || entry.revokedReason != null -> Color(0xFFFFB340)
                    else -> Color.White.copy(alpha = .55f)
                },
                fontSize = 13.sp,
            )
            if (entry.unsigned) {
                Text("Unsigned", color = Color(0xFFFFB340), fontSize = 12.sp)
            }
            when (entry.clash) {
                MarketEntry.Impostor.BUILT_IN ->
                    Text("Claims a Folio package's name", color = Color(0xFFFF453A), fontSize = 12.sp)
                MarketEntry.Impostor.ANOTHER_SOURCE ->
                    Text("Another source offers this name too", color = Color(0xFFFFB340), fontSize = 12.sp)
                null -> Unit
            }
        }
        when {
            busy -> Text("Working…", color = Color.White.copy(alpha = .55f), fontSize = 15.sp, modifier = Modifier.padding(horizontal = 14.dp))
            // A revoked package can be removed but never installed again - including as an update, which is how a
            // pulled package used to slip back in.
            entry.revokedReason != null && installed != null -> MarketActionButton("Remove", name, onRemove)
            entry.revokedReason != null -> Text("Unavailable", color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
            // Nothing can be installed under a name that belongs to a package inside Folio.
            entry.clash == MarketEntry.Impostor.BUILT_IN ->
                Text("Refused", color = Color(0xFFFF453A), fontSize = 13.sp)
            entry.entry.needs.isNotEmpty() -> Text("Needs a newer Folio", color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
            update -> MarketActionButton("Update", name, onGet)
            installed != null -> MarketActionButton("Remove", name, onRemove)
            else -> MarketActionButton("Get", name, onGet)
        }
    }
}

/**
 * The Market's own settings, for when it's shown without the launcher's Settings behind it (the tests, and any future
 * place the store stands alone). Inside Folio, the Settings tab shows Folio's real Settings instead.
 */
@Composable
private fun MarketOwnSettings(style: FeaturedStyle, onStyle: (FeaturedStyle) -> Unit, onIntroduce: () -> Unit) {
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
            style.description,
            color = Color.White.copy(alpha = .55f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp),
        )
        SheetGroup(Modifier.padding(bottom = 10.dp)) {
            IosActionRow("Show the introduction again", onClick = onIntroduce)
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        SheetGroup(Modifier.padding(bottom = 16.dp)) {
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
    revoked: String?,
    source: Source,
    showBack: Boolean,
    onBack: () -> Unit,
    onGet: () -> Unit,
    onRemove: () -> Unit,
    onShare: (IndexPackage) -> Unit,
    onReport: (IndexPackage) -> Unit,
) {
    // Keyed on the version too: after an update the page has to read the new package's own text and images, not the
    // ones it read before. Reading them means parsing every bundled package, so it happens off the main thread.
    val pkg by produceState<com.mccal.folio.market.FolioPackage?>(null, entry.id, entry.version) {
        value = withContext(session.io) { session.read(entry.id) }
    }
    val name = entry.manifest?.name?.english ?: entry.id
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (showBack) {
            Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Back", onClick = onBack).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(18.dp))
                Text("Back", color = Color(0xFF0A84FF), fontSize = 16.sp)
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PackageIcon(session, MarketEntry(entry, source), 64.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                entry.manifest?.author?.name?.english?.let { Text(it, color = Color.White.copy(alpha = .55f), fontSize = 14.sp) }
            }
        }

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
            when {
                // Pulled by its source. Removing what's already on is still allowed; getting it is not.
                revoked != null && installed == null -> Column(Modifier.testTag("package-unavailable")) {
                    Text("Unavailable", color = Color(0xFFFFB340), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Its source pulled it: $revoked", color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
                }
                installed != null -> MarketActionButton("Remove", name, onRemove)
                else -> MarketActionButton("Get", name, onGet)
            }
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
        val blocks = pkg?.depiction?.blocks.orEmpty()
        if (blocks.none { it is DepictionBlock.Hero || it is DepictionBlock.Screenshots }) NoScreenshots()
        blocks.forEach { block ->
            when (block) {
                is DepictionBlock.Hero -> MarketImage(session, source, block.image, Modifier.fillMaxWidth().height(160.dp))
                is DepictionBlock.Screenshots -> Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    block.images.forEach { MarketImage(session, source, it, Modifier.width(150.dp).height(260.dp)) }
                }
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

        val safety = entry.manifest?.let { PackageSafety.of(it) }
        safety?.let {
            SheetGroupLabel("What it can't reach")
            SheetGroup(Modifier.padding(bottom = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    it.cannotAccess.forEach { line -> Text(line, color = Color.White.copy(alpha = .7f), fontSize = 14.sp) }
                }
            }
        }

        SheetGroupLabel("Information")
        SheetGroup(Modifier.padding(bottom = 12.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Source: ${source.label}", color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
                Text(
                    entry.provenance?.let { "Built from ${it.repo} @ ${it.commit}" } ?: "Built into Folio",
                    color = Color.White.copy(alpha = .55f), fontSize = 13.sp,
                )
                installed?.let { Text("Installed ${it.version}", color = Color.White.copy(alpha = .55f), fontSize = 13.sp) }
            }
        }
        SheetGroup(Modifier.padding(bottom = 24.dp)) {
            IosActionRow("Share") { onShare(entry) }
            MenuDivider()
            IosActionRow("Report a package", destructive = true) { onReport(entry) }
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

/**
 * A picture a package shows. Folio's own packages are in the APK, so their bytes are decoded straight from assets; a
 * package from a source names a path on that source's host, which [MarketImages] fetches through Folio's own client.
 */
@Composable
private fun MarketImage(session: MarketSession, source: Source, path: String, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val url = remember(source.url, path) { MarketImages.urlFor(source, path) }
    val bundled = remember(path, url) {
        if (url != null) null else session.source.asset(path)?.let { bytes ->
            runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .06f))) {
        when {
            bundled != null -> androidx.compose.foundation.Image(
                bitmap = bundled,
                contentDescription = null, // the page's text says what it is; the picture repeats it
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            url != null -> coil3.compose.AsyncImage(
                model = url,
                imageLoader = MarketImages.loader(context),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A package's icon: the one it ships if it has one, and otherwise a tile in its section's colour with its first
 * letter. Every row has one either way, so the list doesn't change shape depending on who published what.
 */
@Composable
private fun PackageIcon(session: MarketSession, entry: MarketEntry, size: androidx.compose.ui.unit.Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon = entry.entry.manifest?.icon
    val url = remember(entry.source.url, icon) { icon?.let { MarketImages.urlFor(entry.source, it) } }
    // Folio's own icons are in the APK; a source's are on its host, and Coil fetches them.
    val bundled = remember(icon, url) {
        if (icon == null || url != null) null else session.source.asset(icon)?.let { bytes ->
            runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }
    val tint = sectionColor(entry.entry.manifest?.section)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4.5f)).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bundled != null -> androidx.compose.foundation.Image(
                bitmap = bundled, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            url != null -> coil3.compose.AsyncImage(
                model = url, imageLoader = MarketImages.loader(context), contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            else -> Text(
                entry.name.take(1).uppercase(), color = Color.White,
                fontSize = (size.value * .42f).sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** iOS system colours, one per section, so a package's tile says what kind of thing it is before you read it. */
private fun sectionColor(section: com.mccal.folio.market.Section?): Color = when (section) {
    com.mccal.folio.market.Section.THEMES -> Color(0xFF5E5CE6)
    com.mccal.folio.market.Section.TWEAKS -> Color(0xFF0A84FF)
    com.mccal.folio.market.Section.LAYOUTS -> Color(0xFF30D158)
    com.mccal.folio.market.Section.WALLPAPERS -> Color(0xFFFF9F0A)
    com.mccal.folio.market.Section.SCRIPTS -> Color(0xFFFF375F)
    null -> Color(0xFF8E8E93)
}

/**
 * What a package page shows where its pictures would be. A gap reads as something that failed to load, so Folio says
 * it plainly - and the line is aimed at whoever published the package as much as at the person reading it.
 */
@Composable
private fun NoScreenshots() {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = .05f)).padding(16.dp).testTag("package-no-screenshots"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SadFolio(46.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text("No screenshots", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Its publisher hasn't shown what it looks like yet.",
                color = Color.White.copy(alpha = .55f), fontSize = 13.sp,
            )
        }
    }
}

/** Folio's mark, a little sad: the same page with a folded corner, drawn with a small frown. */
@Composable
private fun SadFolio(size: androidx.compose.ui.unit.Dp) {
    val face = Color.White.copy(alpha = .5f)
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val u = w / 54f
        drawRoundRect(
            color = Color.White.copy(alpha = .10f),
            topLeft = androidx.compose.ui.geometry.Offset(7 * u, 4 * u),
            size = androidx.compose.ui.geometry.Size(40 * u, 46 * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(9 * u),
        )
        drawCircle(face, radius = 2.6f * u, center = androidx.compose.ui.geometry.Offset(21 * u, 26 * u))
        drawCircle(face, radius = 2.6f * u, center = androidx.compose.ui.geometry.Offset(33 * u, 26 * u))
        // A frown: an arc opening upwards, which is the same curve as a smile turned over.
        drawArc(
            color = face,
            startAngle = 200f, sweepAngle = 140f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(20 * u, 36 * u),
            size = androidx.compose.ui.geometry.Size(14 * u, 10 * u),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

/** Shares a `folio://package/<id>` link, which opens the package on another phone that has Folio. */
private fun share(context: android.content.Context, entry: IndexPackage) {
    val name = entry.manifest?.name?.english ?: entry.id
    runCatching {
        context.startActivity(
            android.content.Intent.createChooser(
                android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, "$name for Folio: folio://package/${entry.id}"),
                "Share $name",
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * Opens the source's issue form with the package already filled in, so a report carries what a maintainer needs:
 * which package, which version, and the checksum of the file that was installed.
 */
private fun report(context: android.content.Context, issuesUrl: String?, entry: IndexPackage) {
    val url = issuesUrl ?: "https://github.com/McCal-Codes/folio/issues/new"
    val name = entry.manifest?.name?.english ?: entry.id
    val body = buildString {
        append("Package: ").append(entry.id).append('\n')
        append("Version: ").append(entry.version).append('\n')
        entry.sha256?.let { append("Checksum: ").append(it).append('\n') }
        entry.provenance?.let { append("Built from: ").append(it.repo).append(" @ ").append(it.commit).append('\n') }
        append("\nWhat's wrong:\n")
    }
    val full = url + (if ('?' in url) "&" else "?") +
        "title=" + android.net.Uri.encode("Report: $name") + "&body=" + android.net.Uri.encode(body)
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, full.toUri())
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** A `folio://` link someone shared. Anything else is ignored rather than guessed at. */
internal sealed interface MarketLink {
    data class Package(val id: String) : MarketLink
    data class Source(val url: String) : MarketLink

    /** A supporter's early-access code, checked on the phone against Folio's key. */
    data class Early(val code: String) : MarketLink

    companion object {
        /** What the Market should open, or null when this isn't a link Folio knows. */
        fun parse(uri: String?): MarketLink? {
            val text = uri?.trim() ?: return null
            if (!text.startsWith("folio://")) return null
            val rest = text.removePrefix("folio://")
            val host = rest.substringBefore('/')
            val value = rest.substringAfter('/', "").substringBefore('?').substringBefore('#')
            if (value.isEmpty()) return null
            return when (host) {
                "package" -> Package(value).takeIf { PackageManifest.ID.containsMatchIn(it.id) }
                // The url is encoded, because it carries its own slashes.
                "source" -> android.net.Uri.decode(value).let { url -> Source(url).takeIf { url.startsWith("https://") } }
                "early" -> Early(android.net.Uri.decode(value)).takeIf { it.code.length in 16..512 }
                else -> null
            }
        }

        /** Where the Market is asked to go before it opens, set by the activity that received the link. */
        var pending: MarketLink? by mutableStateOf(null)
    }
}
