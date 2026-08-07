package com.mapconductor.compose.marker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.marker.MarkerAnimation
import com.mapconductor.core.marker.MarkerAnimationOverlayEntry
import android.view.animation.BounceInterpolator
import android.view.animation.LinearInterpolator

/**
 * Screen-space marker animation layer.
 *
 * Drop/Bounce animations move the marker's image vertically on screen from
 * above the top edge down to the marker's projected position. Because the
 * motion happens in screen coordinates, it is independent of the map
 * projection — tilted, rotated, or globe views all get a straight drop from
 * the top of the map view. The native marker stays hidden while an entry is
 * active and is shown again by [MarkerAnimationOverlayEntry.onFinished].
 */
@Composable
fun MarkerAnimationOverlayLayer(
    entries: Collection<MarkerAnimationOverlayEntry>,
    resolveScreenOffset: (GeoPointInterface) -> Offset?,
    onFinished: (MarkerAnimationOverlayEntry) -> Unit,
) {
    entries.forEach { entry ->
        key(entry.id) {
            AnimatedMarkerImage(
                entry = entry,
                resolveScreenOffset = resolveScreenOffset,
                onFinished = onFinished,
            )
        }
    }
}

@Composable
private fun AnimatedMarkerImage(
    entry: MarkerAnimationOverlayEntry,
    resolveScreenOffset: (GeoPointInterface) -> Offset?,
    onFinished: (MarkerAnimationOverlayEntry) -> Unit,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val interpolator =
            when (entry.animation) {
                MarkerAnimation.Bounce -> BounceInterpolator()
                MarkerAnimation.Drop -> LinearInterpolator()
            }
        progress.animateTo(
            targetValue = 1f,
            animationSpec =
                tween(
                    durationMillis = entry.durationMillis.toInt().coerceAtLeast(1),
                    easing = Easing { fraction -> interpolator.getInterpolation(fraction) },
                ),
        )
        onFinished(entry)
    }

    val iconWidthPx = entry.icon.size.width
    val iconHeightPx = entry.icon.size.height
    val anchor = entry.icon.anchor
    val density = LocalDensity.current
    val iconWidthDp: Dp
    val iconHeightDp: Dp
    with(density) {
        iconWidthDp = iconWidthPx.toDp()
        iconHeightDp = iconHeightPx.toDp()
    }

    Image(
        bitmap = entry.icon.bitmap.asImageBitmap(),
        contentDescription = null,
        modifier =
            Modifier
                .size(iconWidthDp, iconHeightDp)
                .graphicsLayer {
                    // Reading progress.value here re-executes this block every
                    // animation frame, so the projected target is re-resolved
                    // per frame and the drop tracks a moving camera.
                    val t = progress.value
                    val target = resolveScreenOffset(entry.state.position)
                    if (target == null) {
                        // Not projectable right now (e.g. behind the globe):
                        // hide the image but keep the animation clock running.
                        alpha = 0f
                        return@graphicsLayer
                    }
                    alpha = 1f
                    // Top-left of the icon when it has landed on its anchor.
                    val endX = target.x - anchor.x * iconWidthPx
                    val endY = target.y - anchor.y * iconHeightPx
                    // Start fully above the map view's top edge.
                    val startY = -iconHeightPx
                    translationX = endX
                    translationY = startY + (endY - startY) * t
                },
    )
}
