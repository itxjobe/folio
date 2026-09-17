package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

/**
 * Settings › Supporter: redeem a Ko-fi code, see what it unlocks, and choose whether to take the beta features that
 * come with it. Everything happens on the phone — the code is checked against a key inside Folio, and nothing about
 * it is ever sent anywhere.
 */
@Composable
internal fun SupporterPage() {
    val context = LocalContext.current
    var code by remember { mutableStateOf(Supporter.code(context)) }
    var stored by remember { mutableStateOf(Supporter.storedText(context)) }
    var beta by remember { mutableStateOf(Supporter.betaOn(context)) }
    var redeeming by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    SettingsCard("CODE") {
        val current = code
        if (current == null) {
            CardAction("Redeem a Code", onClick = { problem = null; redeeming = true })
            CardNote("Codes come with a Ko-fi thank-you. Folio checks a code on the phone against a key inside the " +
                "app: it works with no signal, needs no account, and tells nobody that you supported.")
        } else {
            InfoRow("Code", shortCode(stored.orEmpty()))
            InfoRow("Unlocks", unlocksText(current.scopes))
            current.expires?.let { InfoRow("Until", it.format(DateTimeFormatter.ofPattern("d MMM yyyy"))) }
            CardAction("Remove Code", destructive = true, onClick = {
                Supporter.remove(context); code = null; stored = null; beta = false
            })
        }
        problem?.let { CardNote(it) }
    }

    if (code?.scopes?.contains(BetaCodes.SCOPE_BETA) == true) SettingsCard("BETA FEATURES") {
        SettingsSwitch("Beta Features", beta, { on -> Supporter.setBetaOn(context, on); beta = on }, "supporter-beta-switch")
        CardNote("Beta features arrive a release or two early, before they've been through everyone's phones. Expect " +
            "more bugs than usual — that's the trade. Turn this off whenever you like and Folio goes straight back " +
            "to the way the stable release behaves.")
    }

    SettingsCard("SUPPORT") {
        CardNote("Folio's core is free and stays free — Home, the island, panels, gestures and themes are never " +
            "behind a code. Supporting buys time to keep building, and these extras are the thank-you.")
    }

    if (redeeming) RedeemAlert(onCancel = { redeeming = false }, onRedeem = { typed ->
        when (val result = Supporter.redeem(context, typed)) {
            is BetaCodes.Result.Valid -> {
                code = result.code; stored = Supporter.storedText(context); problem = null; redeeming = false
            }
            is BetaCodes.Result.Expired -> problem = "That code has run out. Ko-fi codes have a date on them; a new one will work."
            BetaCodes.Result.NotOurs -> problem = "Folio doesn't recognize that code. Check for a typo — the letters I, L and O aren't used."
            BetaCodes.Result.Unreadable -> problem = "That doesn't look like a Folio code. Paste the whole thing, dashes and all."
        }
    })
}

/** Enough of the code to recognize it, without four lines of letters in a settings row. */
private fun shortCode(code: String): String =
    code.split('-').let { if (it.size <= 3) code else "${it.first()}…${it.last()}" }

private fun unlocksText(scopes: Set<String>): String = listOf(
    BetaCodes.SCOPE_BETA to "Beta features", BetaCodes.SCOPE_LOOK to "Personalization",
    BetaCodes.SCOPE_POWER to "Automation", BetaCodes.SCOPE_KEYS to "Keyboard extras")
    .filter { it.first in scopes }.joinToString(", ") { it.second }.ifEmpty { "Nothing yet" }

@Composable private fun InfoRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 17.sp)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 6.dp))
        Text(value, color = FolioColors.SecondaryLabel, fontSize = 15.sp, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth())
    }
}

/** iOS-style alert with one field, like Save Backup: paste the code, tap Redeem. */
@Composable private fun RedeemAlert(onCancel: () -> Unit, onRedeem: (String) -> Unit) {
    var typed by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(onDismissRequest = onCancel,
        title = { Text("Redeem a Code") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Paste the code from your Ko-fi thank-you.", fontSize = 13.sp)
                BasicTextField(typed, { typed = it.take(160) },
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = .1f)).padding(horizontal = 10.dp, vertical = 8.dp)
                        .focusRequester(focus).testTag("redeem-code"),
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onRedeem(typed) }))
            }
        },
        confirmButton = { TextButton(onClick = { onRedeem(typed) }) { Text("Redeem") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } })
}
