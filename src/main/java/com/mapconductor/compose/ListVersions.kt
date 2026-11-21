package com.mapconductor.compose

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.mapconductor.core.InternalMapConductorApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * [list] now and, when it is a `mutableStateListOf`, again after each change
 * made to it in place.
 *
 * `Markers(states)`, `Circles(states)`, `Polylines(states)` and
 * `Polygons(states)` hand their list to the map again when they are given a
 * different list. A `mutableStateListOf` that the app adds to is the same list
 * every time, so the states added after the first hand-over never reached the
 * map: a pin per tap logged one more marker per tap and drew none.
 *
 * Each version is a copy taken on the main thread, so the work done with it on
 * another thread never reads the list while the app is changing it.
 */
@InternalMapConductorApi
internal fun <T> listVersions(list: List<T>): Flow<List<T>> =
    if (list is SnapshotStateList<T>) {
        // Wrapped so that each change counts, even one that leaves an equal list:
        // a state's equals does not look at its id, so replacing one state with
        // another drawn the same way would otherwise go unseen.
        snapshotFlow { Version(list.toList()) }.map { it.items }
    } else {
        flowOf(list)
    }

@InternalMapConductorApi
private class Version<T>(
    val items: List<T>,
)
