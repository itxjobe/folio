package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mccal.folio.market.FeaturedStyle

/**
 * The introduction, shown the first time the Market opens after updating (and again from its settings).
 *
 * Three steps: what's in here, how Featured should look, and where packages come from. Skip is on every step, and the
 * style choice is the one McCal asked for: the carousel by default, with Calm offered up front rather than buried.
 */
@Composable
internal fun MarketIntroduction(style: FeaturedStyle, onStyle: (FeaturedStyle) -> Unit, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
        Text(
            "Skip",
            color = Color(0xFF0A84FF), fontSize = 16.sp,
            modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = "Skip the introduction", onClick = onDone).padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Column(Modifier.align(Alignment.Center).fillMaxWidth()) {
            when (step) {
                0 -> {
                    Title("Welcome to the Folio Market")
                    Body(
                        "Folio's own themes and tweaks live here, as packages you can get and remove. Later you'll be " +
                            "able to add sources other people publish.",
                    )
                }
                1 -> {
                    Title("Choose how Featured looks")
                    Body("You can change this any time in the Market's settings.")
                    Spacer(Modifier.height(16.dp))
                    for (option in FeaturedStyle.entries) {
                        StyleCard(option, chosen = option == style) { onStyle(option) }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                else -> {
                    Title("Where packages come from")
                    Body(
                        "Folio's own packages come with the app. A source you add is signed by whoever publishes it, " +
                            "and Folio shows its fingerprint before you trust it. Every package's page lists exactly " +
                            "what it may change.",
                    )
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Start) {
                repeat(3) { index ->
                    Box(
                        Modifier.padding(end = 6.dp).width(7.dp).height(7.dp).clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = if (index == step) 1f else .3f)),
                    )
                }
            }
            Text(
                if (step < 2) "Next" else "Start",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF0A84FF))
                    .clickable(onClickLabel = if (step < 2) "Next step" else "Open the Market") {
                        if (step < 2) step++ else onDone()
                    }
                    .padding(horizontal = 20.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, color = Color.White.copy(alpha = .75f), fontSize = 16.sp)
}

@Composable
private fun StyleCard(option: FeaturedStyle, chosen: Boolean, onChoose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF2C2C2E))
            .border(if (chosen) 2.dp else 0.dp, if (chosen) Color(0xFF0A84FF) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClickLabel = option.label, onClick = onChoose)
            .padding(14.dp),
    ) {
        Text(option.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(option.description, color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
    }
}
