package com.mapconductor.compose.marker

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.mapconductor.compose.MapViewScope
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.marker.MarkerAnimation
import com.mapconductor.core.marker.MarkerIconInterface
import com.mapconductor.core.marker.MarkerState
import com.mapconductor.core.marker.OnMarkerEventHandler
import java.io.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@Composable
fun MapViewScope.Marker(state: MarkerState) {
    val collector = LocalMarkerCollector.current
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
 * Efficiently add many markers without creating one Composable per marker.
 *
 * This avoids large composition overhead (thousands of LaunchedEffects/DisposableEffects)
 * by performing batched add/remove in a single effect.
 */
@Composable
fun MapViewScope.Markers(states: List<MarkerState>) {
    val collector = LocalMarkerCollector.current
    val prevIdsState = remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(states) {
        val startedAt = SystemClock.elapsedRealtime()
        markerTrace("collector effect start count=${states.size}")
        // For very large marker sets, avoid per-marker SharedFlow emits which can backpressure and
        // block rendering; instead publish the whole map in one StateFlow update.
        withContext(Dispatchers.Default) {
            val nextIds = states.asSequence().map { it.id }.toSet()
            val removedIds = prevIdsState.value - nextIds
            markerTrace("collector ids created count=${nextIds.size}")
            prevIdsState.value = nextIds
            // Merge only the ids this Markers() owns into the shared collector so
            // several Markers()/Marker() can share one map without clobbering each
            // other. Assigning the whole map (a full replace) dropped everyone
            // else's markers, so the last Markers() to run won and erased the rest.
            collector.flow.update { current ->
                val next = current.toMutableMap()
                for (id in removedIds) next.remove(id)
                for (state in states) next[state.id] = state
                next
            }
            markerTrace(
                "collector flow assigned count=${states.size} " +
                    "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Remove only the markers this Markers() owns on dispose, not the
            // whole collection.
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

private fun markerTrace(message: String) {
    Log.d(
        "MCMarkerTrace",
        "[ComposeSDK][t=${SystemClock.elapsedRealtime()}]" +
            "[thread=${Thread.currentThread().name}] $message",
    )
}

@Composable
fun MapViewScope.Marker(
    position: GeoPointInterface,
    id: String? = null,
    zIndex: Int? = null,
    clickable: Boolean = true,
    draggable: Boolean = false,
    icon: MarkerIconInterface? = null,
    animation: MarkerAnimation? = null,
    extra: Serializable? = null,
    onClick: OnMarkerEventHandler? = null,
    onDragStart: OnMarkerEventHandler? = null,
    onDrag: OnMarkerEventHandler? = null,
    onDragEnd: OnMarkerEventHandler? = null,
    onAnimateStart: OnMarkerEventHandler? = null,
    onAnimateEnd: OnMarkerEventHandler? = null,
) {
    val state =
        MarkerState(
            id = id,
            position = position,
            extra = extra,
            animation = animation,
            zIndex = zIndex,
            clickable = clickable,
            draggable = draggable,
            icon = icon,
            onClick = onClick,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onAnimateStart = onAnimateStart,
            onAnimateEnd = onAnimateEnd,
        )
    Marker(state)
}
