package com.mapconductor.compose.circle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mapconductor.compose.MapViewScope
import com.mapconductor.core.circle.CircleState
import com.mapconductor.core.copyOverlayStates
import com.mapconductor.core.overlayStateIds
import com.mapconductor.core.circle.CircleTilingOptions
import com.mapconductor.core.circle.OnCircleEventHandler
import com.mapconductor.core.features.GeoPointInterface
import java.io.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@Composable
fun MapViewScope.Circle(state: CircleState) {
    val collector = LocalCircleCollector.current
    LaunchedEffect(state) {
        collector.add(state)
    }

    DisposableEffect(state.id) {
        onDispose {
            collector.remove(state.id)
        }
    }
}

/**
 * Efficiently add many circles without creating one Composable per circle.
 *
 * The same shape as [com.mapconductor.compose.polyline.Polylines] and
 * [com.mapconductor.compose.polygon.Polygons]: one effect diffs the list
 * against the ids this call added last time and publishes the result in a
 * single collector update. Several Circles()/Circle() can share one map.
 */
@Composable
fun MapViewScope.Circles(states: List<CircleState>) {
    val collector = LocalCircleCollector.current
    val prevIdsState = remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(states) {
        withContext(Dispatchers.Default) {
            val prevIds = prevIdsState.value
            val nextIds = overlayStateIds(states)
            prevIdsState.value = nextIds
            // Merge only the ids this Circles() owns, so several
            // Circles()/Circle() can share one map's collector.
            collector.flow.update { current ->
                val next = copyOverlayStates(current)
                for (id in prevIds) if (id !in nextIds) next.remove(id)
                for (state in states) next[state.id] = state
                next
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Remove only the circles this Circles() owns on dispose.
            val ownIds = prevIdsState.value
            if (ownIds.isNotEmpty()) {
                collector.flow.update { current ->
                    val next = copyOverlayStates(current)
                    for (id in ownIds) next.remove(id)
                    next
                }
            }
            prevIdsState.value = emptySet()
        }
    }
}

/**
 * How this map draws many circles: as raster tiles once there are enough of
 * them ([CircleTilingOptions.Default]), never ([CircleTilingOptions.Disabled]),
 * or at another threshold. Without it the map uses [CircleTilingOptions.Default].
 *
 * Read when the circles are handed to the map, so put it in the map's content
 * before them; a change takes effect the next time they are handed over
 * (recreate the map to apply it to an unchanged list).
 */
@Composable
fun MapViewScope.CircleTiling(options: CircleTilingOptions) {
    val scope = this
    SideEffect {
        scope.circleTilingOptions.value = options
    }
}

@Composable
fun MapViewScope.Circle(
    center: GeoPointInterface,
    radiusMeters: Double,
    id: String? = null,
    geodesic: Boolean = true,
    clickable: Boolean = true,
    strokeColor: Color = Color.Red,
    strokeWidth: Dp = 1.dp,
    fillColor: Color = Color.White.copy(alpha = 0.5f),
    zIndex: Int? = null,
    extra: Serializable? = null,
    onClick: OnCircleEventHandler? = null,
) {
    val state =
        CircleState(
            id = id,
            center = center,
            radiusMeters = radiusMeters,
            geodesic = geodesic,
            clickable = clickable,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            fillColor = fillColor,
            zIndex = zIndex,
            extra = extra,
            onClick = onClick,
        )
    Circle(state)
}
