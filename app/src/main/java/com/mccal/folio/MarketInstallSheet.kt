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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.PackageSafety

/**
 * What you agree to before a package is applied: what it changes, what it can't reach, and where it came from.
 *
 * Everything here is read from the manifest, so it says the same thing whoever wrote the package. Nothing has been
 * applied when this is on screen; Get is the first moment anything changes.
 */
@Composable
internal fun MarketInstallSheet(entry: IndexPackage, builtIn: Boolean, onGet: () -> Unit, onCancel: () -> Unit) {
    val manifest = entry.manifest ?: return
    MarketInstallSheet(
        manifest = manifest,
        origin = InstallOrigin(
            line = if (builtIn) "Built into Folio, so there's nothing to download." else "Downloaded from a source you added.",
            provenance = entry.provenance?.let { "Built from ${it.repo} @ ${it.commit}" },
            checksum = entry.sha256,
        ),
        onGet = onGet,
        onCancel = onCancel,
    )
}

/** Where a package came from, in the words the sheet shows. */
internal data class InstallOrigin(
    val line: String,
    val provenance: String? = null,
    val checksum: String? = null,
    /** Shown in amber when there's something to be careful about, like a file nobody signed. */
    val warning: String? = null,
)

@Composable
internal fun MarketInstallSheet(
    manifest: com.mccal.folio.market.PackageManifest,
    origin: InstallOrigin,
    onGet: () -> Unit,
    onCancel: () -> Unit,
) {
    val safety = PackageSafety.of(manifest)
    val name = manifest.name.english
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).testTag("market-install-sheet")) {
        Text("Get $name", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Text(
            "${manifest.author.name.english} · version ${manifest.version}",
            color = Color.White.copy(alpha = .55f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp),
        )

        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF2C2C2E)).padding(14.dp)) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(20.dp))
            Text(safety.summary, color = Color.White.copy(alpha = .85f), fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
        }

        SheetGroupLabel(if (safety.changes.isEmpty()) "Changes" else "What it changes")
        SheetGroup {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (safety.changes.isEmpty()) {
                    Line(Icons.Rounded.Check, Color(0xFF30D158), "How Folio looks, and nothing else")
                } else {
                    safety.changes.forEach { Line(Icons.Rounded.Check, Color(0xFFFFB340), it) }
                }
            }
        }

        SheetGroupLabel("What it can't reach")
        SheetGroup {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                safety.cannotAccess.forEach { Line(Icons.Rounded.Close, Color.White.copy(alpha = .45f), it) }
            }
        }

        SheetGroupLabel("Where it came from")
        SheetGroup {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                origin.warning?.let {
                    Text(it, color = Color(0xFFFFB340), fontSize = 14.sp)
                }
                Text(origin.line, color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
                origin.provenance?.let { Text(it, color = Color.White.copy(alpha = .55f), fontSize = 13.sp) }
                origin.checksum?.let {
                    Text("Checksum ${it.take(16)}…", color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cancel",
                color = Color(0xFF0A84FF), fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onCancel)
                    .heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                "Get",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF0A84FF))
                    .clickable(onClickLabel = "Get $name", onClick = onGet)
                    .heightIn(min = 44.dp).padding(horizontal = 22.dp, vertical = 12.dp)
                    .testTag("market-install-confirm"),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Line(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Text(text, color = Color.White.copy(alpha = .85f), fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
    }
}
