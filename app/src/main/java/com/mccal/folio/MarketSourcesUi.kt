package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mccal.folio.market.RefreshResult
import com.mccal.folio.market.Source
import com.mccal.folio.market.SourceKey

/** The Sources tab: Folio's own, the ones the user added, and how to add another. */
@Composable
internal fun MarketSourcesTab(
    builtInName: String,
    builtInCount: Int,
    statuses: List<SourceStatus>,
    localDevAllowed: Boolean,
    onAdd: () -> Unit,
    onAddLocalDev: () -> Unit,
    onRefresh: (Source) -> Unit,
    onForget: (Source) -> Unit,
) {
    Column {
        SheetGroupLabel(stringResource(R.string.sources))
        SheetGroup(Modifier.padding(bottom = 10.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Home, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(20.dp))
                Column(Modifier.padding(start = 10.dp)) {
                    Text(builtInName, color = Color.White, fontSize = 16.sp)
                    Text(stringResource(R.string.built_into_the_app_1_d_packages_no, builtInCount), color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
                }
            }
            statuses.forEach { status ->
                MenuDivider()
                SourceRow(status, onRefresh = { onRefresh(status.source) }, onForget = { onForget(status.source) })
            }
        }
        SheetGroup(Modifier.padding(bottom = 10.dp)) {
            IosActionRow(stringResource(R.string.add_a_source)) { onAdd() }
            if (localDevAllowed) {
                MenuDivider()
                IosActionRow(stringResource(R.string.add_a_local_source_folio_dev)) { onAddLocalDev() }
            }
        }
        Text(
            stringResource(R.string.folio_shows_a_source_s_key_fingerprint),
            color = Color.White.copy(alpha = .55f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun SourceRow(status: SourceStatus, onRefresh: () -> Unit, onForget: () -> Unit) {
    val source = status.source
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (source.kind == Source.Kind.LOCAL_DEV) Icons.Rounded.Warning else Icons.Rounded.Public,
                contentDescription = null,
                tint = if (source.kind == Source.Kind.LOCAL_DEV) Color(0xFFFFB340) else Color(0xFF6CB4FF),
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(source.label, color = Color.White, fontSize = 16.sp)
                Text(
                    when {
                        status.refreshing -> stringResource(R.string.refreshing)
                        status.failure != null -> status.failure.message
                        source.kind == Source.Kind.LOCAL_DEV -> stringResource(R.string.unsigned_served_from_this_phone)
                        // A source nobody typed in says where it came from, or it looks like something that
                        // appeared on its own.
                        source.kind == Source.Kind.SUPPORTER && status.snapshot == null ->
                            stringResource(R.string.added_with_your_supporter_code)
                        status.snapshot != null -> stringResource(R.string.n_packages, status.packages.size)
                        else -> stringResource(R.string.not_read_yet)
                    },
                    color = if (status.failure != null) Color(0xFFFF6961) else Color.White.copy(alpha = .55f),
                    fontSize = 13.sp,
                )
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(stringResource(R.string.refresh), onRefresh)
            Pill(stringResource(R.string.remove), onForget, destructive = true)
        }
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Text(
        label,
        color = if (destructive) Color(0xFFFF6961) else Color(0xFF0A84FF),
        fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .08f))
            .clickable(onClick = onClick).heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/**
 * Trust on first use, out loud: the fingerprint of the key the source signs with, so it can be compared with what the
 * publisher says it should be. Folio pins it, and a later key change comes back here rather than being followed.
 */
@Composable
internal fun MarketTrustSheet(
    url: String,
    key: SourceKey,
    previous: SourceKey?,
    onTrust: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(20.dp).testTag("market-trust-sheet")) {
        Text(
            stringResource(if (previous == null) R.string.add_this_source else R.string.this_source_changed_its_key),
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        )
        Text(url, color = Color.White.copy(alpha = .55f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
        if (previous != null) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF3A2A16)).padding(14.dp)) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color(0xFFFFB340), modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.a_source_s_key_normally_never_changes_if),
                    color = Color.White.copy(alpha = .9f), fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp),
                )
            }
            SheetGroupLabel(stringResource(R.string.key_folio_has))
            Fingerprint(previous)
        }
        SheetGroupLabel(stringResource(if (previous == null) R.string.its_key_fingerprint else R.string.new_key))
        Fingerprint(key)
        Text(
            stringResource(R.string.compare_this_with_the_fingerprint_the),
            color = Color.White.copy(alpha = .55f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.cancel), color = Color(0xFF0A84FF), fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onCancel)
                    .heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                stringResource(if (previous == null) R.string.trust_and_add else R.string.trust_the_new_key),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF0A84FF))
                    .clickable(onClick = onTrust).heightIn(min = 44.dp).padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("market-trust-confirm"),
            )
        }
    }
}

@Composable
private fun Fingerprint(key: SourceKey) {
    Text(
        key.fingerprintGroups,
        color = Color.White, fontSize = 15.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2E)).padding(12.dp),
    )
}

/** What a refresh said, in a line the store can show. */
internal fun refreshMessage(context: android.content.Context, source: Source, result: RefreshResult): String = when (result) {
    is RefreshResult.Updated -> context.getString(R.string.source_updated, result.snapshot.index.name.english)
    is RefreshResult.Unchanged -> context.getString(R.string.source_is_up_to_date, source.label)
    is RefreshResult.NeedsTrust -> context.getString(R.string.source_needs_its_key_checked, source.label)
    is RefreshResult.Failed -> result.message
}

/** Typing in a source's address. Folio checks it's https and reads its key before anything else happens. */
@Composable
internal fun MarketAddSourceSheet(url: String, onUrl: (String) -> Unit, onNext: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp).testTag("market-add-source")) {
        Text(stringResource(R.string.add_a_source), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.paste_the_address_the_publisher_gave_you),
            color = Color.White.copy(alpha = .55f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp),
        )
        IosSearchField(
            query = url,
            onQuery = onUrl,
            placeholder = "https://…",
            modifier = Modifier.padding(vertical = 4.dp),
            onSearch = onNext,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.cancel), color = Color(0xFF0A84FF), fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onCancel)
                    .heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                stringResource(R.string.next), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if (url.isBlank()) Color(0xFF0A84FF).copy(alpha = .4f) else Color(0xFF0A84FF))
                    .clickable(enabled = url.isNotBlank(), onClick = onNext)
                    .heightIn(min = 44.dp).padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("market-add-source-next"),
            )
        }
    }
}
