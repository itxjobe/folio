package com.mccal.folio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * iOS-style rubber band for Home's pages: past the first or last page the pages follow the finger with growing
 * resistance and spring back on release, instead of Android's stretch. Horizontal only.
 */
internal class BounceOverscroll(private val scope: CoroutineScope) : OverscrollEffect {
    private val offset = Animatable(0f)
    private var limit = 1000f

    override val isInProgress: Boolean get() = offset.value != 0f || offset.isRunning

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        // Pulling back toward rest first undoes the stretch.
        val current = offset.value
        if (current != 0f && sign(delta.x) != sign(current)) {
            val back = if (abs(delta.x) > abs(current)) -current else delta.x
            scope.launch { offset.snapTo(current + back) }
            val rest = delta - Offset(back, 0f)
            return Offset(back, 0f) + performScroll(rest)
        }
        val consumed = performScroll(delta)
        val left = delta.x - consumed.x
        if (left != 0f && source == NestedScrollSource.UserInput) {
            // Resistance grows with the stretch, like UIScrollView: about half near rest, far less near the limit.
            val resistance = (1f - abs(current) / limit).coerceIn(.08f, 1f) * .55f
            scope.launch { offset.snapTo((current + left * resistance).coerceIn(-limit / 3, limit / 3)) }
            return Offset(delta.x, consumed.y)
        }
        return consumed
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        val left = performFling(velocity)
        offset.animateTo(0f, spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow), initialVelocity = left.x * .1f)
    }

    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val p = measurable.measure(constraints)
            limit = p.width.toFloat().coerceAtLeast(1f)
            return layout(p.width, p.height) { p.placeWithLayer(0, 0) { translationX = offset.value } }
        }
    }
}

@Composable
internal fun rememberBounceOverscroll(): BounceOverscroll {
    val scope = rememberCoroutineScope()
    return remember(scope) { BounceOverscroll(scope) }
}
