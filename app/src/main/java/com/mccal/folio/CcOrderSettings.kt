package com.mccal.folio

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** [list] with the item at [from] moved one step by [by] (−1 up, +1 down); unchanged at either end. */
internal fun <T> moveItem(list: List<T>, from: Int, by: Int): List<T> {
    val to = from + by
    if (from !in list.indices || to !in list.indices) return list
    return list.toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Settings › Notifications & Control Center › Controls: the order Control Center shows its controls in, plus add and
 * remove — the same list Control Center's own edit mode changes, so either place works.
 */
@Composable internal fun CcOrderSettings(controls: List<String>, onControls: (List<String>) -> Unit) {
    val chosen = controls.mapNotNull { name -> CcControl.entries.firstOrNull { it.name == name } }
    SettingsCard(stringResource(R.string.cc_controls)) {
        chosen.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().testTag("cc-order-${c.name}"), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onControls(controls - c.name) }) {
                    Icon(Icons.Rounded.RemoveCircle, stringResource(R.string.remove), tint = Color(0xFFFF3B30))
                }
                Icon(c.icon(), null, Modifier.padding(end = 10.dp))
                Text(stringResource(c.label), Modifier.weight(1f))
                IconButton(onClick = { onControls(moveItem(chosen.map { it.name }, i, -1)) }, enabled = i > 0) {
                    Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.move_up))
                }
                IconButton(onClick = { onControls(moveItem(chosen.map { it.name }, i, 1)) }, enabled = i < chosen.lastIndex) {
                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.move_down))
                }
            }
        }
        CcControl.entries.filter { it !in chosen }.forEach { c ->
            Row(Modifier.fillMaxWidth().testTag("cc-add-${c.name}"), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onControls(controls + c.name) }) {
                    Icon(Icons.Rounded.AddCircle, stringResource(R.string.add_control, stringResource(c.label)), tint = Color(0xFF34C759))
                }
                Icon(c.icon(), null, Modifier.padding(end = 10.dp))
                Text(stringResource(c.label), Modifier.weight(1f))
                Spacer(Modifier.width(96.dp))
            }
        }
        CardAction(stringResource(R.string.cc_controls_reset), onClick = { onControls(CcControl.DEFAULTS) })
    }
}
