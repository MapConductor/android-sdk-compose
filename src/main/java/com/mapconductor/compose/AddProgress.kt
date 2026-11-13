package com.mapconductor.compose

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.mapconductor.core.ComponentState
import com.mapconductor.core.InternalMapConductorApi
import com.mapconductor.core.OverlayAddedListeners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How far a collection (`Markers`, `Circles`, `Polylines`, `Polygons`) is with
 * putting its states on the map: for a list of thousands the screen shows
 * nothing until they are drawn, and these say that work is going on.
 *
 * "On the map" is what each state's `onAdded` means: drawn natively, or, for a
 * state drawn in tiles, its tiles handed to the map (their images may still be
 * loading).
 *
 * - `onAddStart(total)`: a list brought states that are not on the map yet;
 *   `total` is how many.
 * - `onAddProgress(added, total)`: how many of them are on the map so far, at
 *   most every 100 ms, and once more with `added == total` before the end.
 * - `onAddEnd(total)`: all of them are on the map. A state taken out of the
 *   list before it got there no longer counts against the end.
 *
 * A list that brings new states while an earlier one is still being added
 * starts over: a new `onAddStart` with only the new states, and no `onAddEnd`
 * for the earlier list. A list that only updates or removes states calls
 * nothing. All three run on the main thread.
 */
@InternalMapConductorApi
internal class AddProgressTracker(
    private val scope: CoroutineScope,
    private val now: () -> Long = SystemClock::uptimeMillis,
) {
    var onAddStart: ((total: Int) -> Unit)? = null
    var onAddProgress: ((added: Int, total: Int) -> Unit)? = null
    var onAddEnd: ((total: Int) -> Unit)? = null

    /** State objects handed over and not on the map yet, by id. */
    private var pending = HashMap<String, ComponentState>()
    private var total = 0
    private var added = 0
    private var lastProgressAt = Long.MIN_VALUE / 2
    private var timer: Job? = null

    /**
     * The states of [states] whose id is not in [prevIds], or none when no one
     * listens. Call it off the main thread; it walks the whole list.
     */
    fun <T : ComponentState> newStates(
        states: List<T>,
        prevIds: Set<String>,
        wanted: Boolean,
    ): List<T> = if (wanted) states.filter { it.id !in prevIds } else emptyList()

    /**
     * [states] (ids [nextIds]) is about to be handed to the collector and
     * [newStates] are the ones it adds. Call on the main thread, before the
     * hand-over, so that no arrival comes ahead of it.
     */
    fun track(
        states: List<ComponentState>,
        nextIds: Set<String>,
        newStates: List<ComponentState>,
    ) {
        if (pending.isNotEmpty()) {
            val before = pending.size
            pending.keys.retainAll(nextIds)
            total -= before - pending.size
            // A state that is still coming may be handed over as a new object
            // with the same id; the map reports that object.
            if (pending.isNotEmpty() && newStates.isEmpty()) {
                for (state in states) if (state.id in pending) pending[state.id] = state
            }
            if (newStates.isEmpty()) finishIfDone()
        }
        if (newStates.isEmpty()) return
        timer?.cancel()
        timer = null
        pending = HashMap<String, ComponentState>(newStates.size * 2).apply { for (s in newStates) put(s.id, s) }
        total = newStates.size
        added = 0
        lastProgressAt = Long.MIN_VALUE / 2
        onAddStart?.invoke(total)
    }

    /** States reached the map (any collection's, any map's: only ours count). */
    fun arrived(states: List<ComponentState>) {
        if (pending.isEmpty()) return
        var hit = false
        for (state in states) {
            if (pending[state.id] === state) {
                pending.remove(state.id)
                added++
                hit = true
            }
        }
        if (!hit) return
        if (pending.isEmpty()) finishIfDone() else reportProgress()
    }

    fun dispose() {
        timer?.cancel()
        timer = null
        pending = HashMap()
    }

    private fun finishIfDone() {
        if (pending.isNotEmpty()) return
        timer?.cancel()
        timer = null
        onAddProgress?.invoke(total, total)
        onAddEnd?.invoke(total)
    }

    private fun reportProgress() {
        if (timer != null) return
        val wait = lastProgressAt + PROGRESS_INTERVAL_MS - now()
        if (wait <= 0) {
            emitProgress()
        } else {
            timer =
                scope.launch {
                    delay(wait)
                    timer = null
                    emitProgress()
                }
        }
    }

    private fun emitProgress() {
        if (pending.isEmpty()) return
        lastProgressAt = now()
        onAddProgress?.invoke(added, total)
    }

    @InternalMapConductorApi
    private companion object {
        const val PROGRESS_INTERVAL_MS = 100L
    }
}

/** The tracker for one collection, listening only while one of the callbacks is set. */
@InternalMapConductorApi
@Composable
internal fun rememberAddProgress(
    onAddStart: ((total: Int) -> Unit)?,
    onAddProgress: ((added: Int, total: Int) -> Unit)?,
    onAddEnd: ((total: Int) -> Unit)?,
): AddProgressTracker {
    val scope = rememberCoroutineScope()
    val tracker = remember { AddProgressTracker(scope) }
    SideEffect {
        tracker.onAddStart = onAddStart
        tracker.onAddProgress = onAddProgress
        tracker.onAddEnd = onAddEnd
    }
    val wanted = onAddStart != null || onAddProgress != null || onAddEnd != null
    DisposableEffect(tracker, wanted) {
        val remove = if (wanted) OverlayAddedListeners.add(tracker::arrived) else null
        onDispose { remove?.invoke() }
    }
    DisposableEffect(tracker) { onDispose { tracker.dispose() } }
    return tracker
}
