package com.mapconductor.compose.polyline

import com.mapconductor.compose.rememberAddProgress
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
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.polyline.OnPolylineEventHandler
import com.mapconductor.core.polyline.PolylineState
import com.mapconductor.core.polyline.PolylineTilingOptions
import java.io.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@Composable
fun MapViewScope.Polyline(state: PolylineState) {
    val collector = LocalPolylineCollector.current
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
 * Efficiently add many polylines without creating one Composable per polyline.
 *
 * The same shape as [com.mapconductor.compose.polygon.Polygons] and
 * [com.mapconductor.compose.marker.Markers]: one effect diffs the list against
 * the ids this call added last time and publishes the result in a single
 * collector update. Several Polylines()/Polyline() can share one map.
 *
 * [onAddStart], [onAddProgress] and [onAddEnd] show that a long list is being
 * put on the map. `onAddStart(total)` comes when the list brings states the
 * map does not have yet, `onAddProgress(added, total)` at most every 100 ms,
 * and `onAddEnd(total)` once all of them are on the map, at the same moment as
 * their `onAdded` (for tiled states: their tiles were handed to the map). A
 * state removed before it got there no longer counts. A list that adds states
 * while an earlier one is still coming starts over without an `onAddEnd` for
 * the earlier one; a list that only updates or removes calls nothing. They run
 * on the main thread.
 */
@Composable
fun MapViewScope.Polylines(
    states: List<PolylineState>,
    onAddStart: ((total: Int) -> Unit)? = null,
    onAddProgress: ((added: Int, total: Int) -> Unit)? = null,
    onAddEnd: ((total: Int) -> Unit)? = null,
) {
    val collector = LocalPolylineCollector.current
    val prevIdsState = remember { mutableStateOf<Set<String>>(emptySet()) }
    val progress = rememberAddProgress(onAddStart, onAddProgress, onAddEnd)
    val tracking = onAddStart != null || onAddProgress != null || onAddEnd != null

    LaunchedEffect(states) {
        val prevIds = prevIdsState.value
        val (nextIds, added) =
            withContext(Dispatchers.Default) {
                states.asSequence().map { it.id }.toSet() to progress.newStates(states, prevIds, tracking)
            }
        prevIdsState.value = nextIds
        // Before the hand-over, so that no arrival comes ahead of it.
        progress.track(states, nextIds, added)
        withContext(Dispatchers.Default) {
            val removedIds = prevIds - nextIds
            // Merge only the ids this Polylines() owns, so several
            // Polylines()/Polyline() can share one map's collector.
            collector.flow.update { current ->
                val next = current.toMutableMap()
                for (id in removedIds) next.remove(id)
                for (state in states) next[state.id] = state
                next
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Remove only the polylines this Polylines() owns on dispose.
            val ownIds = prevIdsState.value
            if (ownIds.isNotEmpty()) {
                collector.flow.update { current ->
                    val next = current.toMutableMap()
                    for (id in ownIds) next.remove(id)
                    next
                }
            }
            prevIdsState.value = emptySet()
        }
    }
}

/**
 * How this map draws many polylines: as raster tiles once there are enough
 * of them ([PolylineTilingOptions.Default]), never ([PolylineTilingOptions.Disabled]),
 * or at other thresholds. Without it the map uses [PolylineTilingOptions.Default].
 *
 * Read when the polylines are handed to the map, so put it in the map's
 * content before them; a change takes effect the next time they are handed
 * over (recreate the map to apply it to an unchanged list).
 */
@Composable
fun MapViewScope.PolylineTiling(options: PolylineTilingOptions) {
    val scope = this
    SideEffect {
        scope.polylineTilingOptions.value = options
    }
}

@Composable
fun MapViewScope.Polyline(
    points: List<GeoPointInterface>,
    id: String? = null,
    strokeColor: Color = Color.Black,
    strokeWidth: Dp = 1.dp,
    geodesic: Boolean = false,
    zIndex: Int = 0,
    extra: Serializable? = null,
    onClick: OnPolylineEventHandler? = null,
) {
    val state =
        PolylineState(
            points = points,
            id = id,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            geodesic = geodesic,
            zIndex = zIndex,
            extra = extra,
            onClick = onClick,
        )
    Polyline(state)
}

@Composable
fun MapViewScope.Polyline(
    bounds: GeoRectBounds,
    id: String? = null,
    strokeColor: Color = Color.Black,
    strokeWidth: Dp = 1.dp,
    geodesic: Boolean = false,
    zIndex: Int = 0,
    extra: Serializable? = null,
    onClick: OnPolylineEventHandler? = null,
) {
    bounds.northEast?.let { ne ->
        bounds.southWest?.let { sw ->
            val points =
                listOf(
                    ne,
                    GeoPoint.fromLatLong(sw.latitude, ne.longitude),
                    sw,
                    GeoPoint.fromLatLong(ne.latitude, sw.longitude),
                    ne,
                )
            Polyline(
                points = points,
                id = id,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                geodesic = geodesic,
                zIndex = zIndex,
                extra = extra,
                onClick = onClick,
            )
        }
    }
}
