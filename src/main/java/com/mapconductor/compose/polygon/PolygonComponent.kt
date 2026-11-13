package com.mapconductor.compose.polygon

import com.mapconductor.compose.rememberAddProgress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mapconductor.compose.MapViewScope
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.polygon.OnPolygonEventHandler
import com.mapconductor.core.polygon.PolygonState
import com.mapconductor.core.polygon.unionHolesInPlace
import java.io.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@Composable
fun MapViewScope.Polygon(state: PolygonState) {
    val collector = LocalPolygonCollector.current
    LaunchedEffect(state) {
        if (state.holes.size > 1) {
            state.unionHolesInPlace()
        }
        collector.add(state)
    }

    DisposableEffect(state.id) {
        onDispose {
            collector.remove(state.id)
        }
    }
}

/**
 * Efficiently add many polygons without creating one Composable per polygon.
 *
 * The same shape as [com.mapconductor.compose.marker.Markers]: one effect diffs
 * the list against the ids this call added last time and publishes the result
 * in a single collector update.
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
fun MapViewScope.Polygons(
    states: List<PolygonState>,
    onAddStart: ((total: Int) -> Unit)? = null,
    onAddProgress: ((added: Int, total: Int) -> Unit)? = null,
    onAddEnd: ((total: Int) -> Unit)? = null,
) {
    val collector = LocalPolygonCollector.current
    val prevIdsState = remember { mutableStateOf<Set<String>>(emptySet()) }
    val progress = rememberAddProgress(onAddStart, onAddProgress, onAddEnd)
    val tracking = onAddStart != null || onAddProgress != null || onAddEnd != null

    LaunchedEffect(states) {
        val prevIds = prevIdsState.value
        val (nextIds, added) =
            withContext(Dispatchers.Default) {
                // Union the holes once per newly added state, as Polygon(state) does.
                for (state in states) {
                    if (state.id !in prevIds && state.holes.size > 1) {
                        state.unionHolesInPlace()
                    }
                }
                states.asSequence().map { it.id }.toSet() to progress.newStates(states, prevIds, tracking)
            }
        prevIdsState.value = nextIds
        // Before the hand-over, so that no arrival comes ahead of it.
        progress.track(states, nextIds, added)
        withContext(Dispatchers.Default) {
            val removedIds = prevIds - nextIds
            // Merge only the ids this Polygons() owns, so several
            // Polygons()/Polygon() can share one map's collector.
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
            // Remove only the polygons this Polygons() owns on dispose.
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

@Composable
fun MapViewScope.Polygon(
    points: List<GeoPointInterface>,
    holes: List<List<GeoPointInterface>> = emptyList(),
    id: String? = null,
    strokeColor: Color = Color.Black,
    strokeWidth: Dp = 1.dp,
    fillColor: Color = Color.Transparent,
    geodesic: Boolean = false,
    zIndex: Int = 0,
    extra: Serializable? = null,
    onClick: OnPolygonEventHandler? = null,
) {
    val state =
        PolygonState(
            points = points,
            holes = holes,
            id = id,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            fillColor = fillColor,
            geodesic = geodesic,
            zIndex = zIndex,
            extra = extra,
            onClick = onClick,
        )
    Polygon(state)
}

@Composable
fun MapViewScope.Polygon(
    bounds: GeoRectBounds,
    id: String? = null,
    strokeColor: Color = Color.Black,
    strokeWidth: Dp = 1.dp,
    fillColor: Color = Color.Transparent,
    geodesic: Boolean = false,
    zIndex: Int = 0,
    extra: Serializable? = null,
    onClick: OnPolygonEventHandler? = null,
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
            Polygon(
                points = points,
                id = id,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                fillColor = fillColor,
                geodesic = geodesic,
                zIndex = zIndex,
                extra = extra,
                onClick = onClick,
            )
        }
    }
}
