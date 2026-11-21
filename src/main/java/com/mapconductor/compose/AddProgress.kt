package com.mapconductor.compose

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.mapconductor.core.ComponentState
import com.mapconductor.core.InternalMapConductorApi
import com.mapconductor.core.OverlayAddedListeners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * How far a collection (`Markers`, `Circles`, `Polylines`, `Polygons`) is with
 * putting its states on the map: for a list of thousands the screen shows
 * nothing until they are drawn, and these say that work is going on.
 *
 * "On the map" is what each state's `onAdded` means: drawn natively, or, for a
 * state drawn in tiles, its tiles handed to the map. The work does not end
 * there for tiles, so the collection also counts as busy while its tiles are
 * being drawn ([drawingChanged]): after a filter change, and while the camera
 * moves to where tiles are not drawn yet.
 *
 * - `onAddStart(total)`: a list was handed over, or tiles have been drawn for
 *   [DRAW_START_DELAY_MS] without one; `total` is how many states the list
 *   brought that are not on the map yet (0 when it only removed or updated
 *   states, or for tiles alone).
 * - `onAddProgress(added, total)`: how many of them are on the map so far, at
 *   most every 100 ms, and once more with `added == total` before the end
 *   when `total` is not 0.
 * - `onAddEnd(total)`: all of them are on the map and nothing has been drawn
 *   for [SETTLE_MS]. A state taken out of the list before it got there no
 *   longer counts against the end.
 *
 * A list that brings new states while an earlier one is still being added
 * starts over: a new `onAddStart` with only the new states, and no `onAddEnd`
 * for the earlier list. All three run on the main thread.
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

    /** Between an onAddStart and its onAddEnd. */
    private var started = false

    /** The last list had states: an empty list after an empty one hands nothing over. */
    private var hadStates = false

    /** The collection's tiles are being drawn (or its tiler is taking a list). */
    private var drawing = false

    /** When the last reason to be busy ended: a hand-over, the last arrival, drawing stopping. */
    private var quietSince = Long.MIN_VALUE / 2
    private var settle: Job? = null
    private var drawStart: Job? = null

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
        val hadStates = this.hadStates
        this.hadStates = states.isNotEmpty()
        if (states.isEmpty() && !hadStates) return
        if (pending.isNotEmpty()) {
            val before = pending.size
            pending.keys.retainAll(nextIds)
            total -= before - pending.size
            // A state that is still coming may be handed over as a new object
            // with the same id; the map reports that object.
            if (pending.isNotEmpty() && newStates.isEmpty()) {
                for (state in states) if (state.id in pending) pending[state.id] = state
            }
        }
        quietSince = now()
        if (newStates.isNotEmpty()) {
            timer?.cancel()
            timer = null
            pending = HashMap<String, ComponentState>((newStates.size / 0.75f).toInt() + 1).apply {
                for (s in newStates) put(s.id, s)
            }
            total = newStates.size
            added = 0
            lastProgressAt = Long.MIN_VALUE / 2
            begin(total)
        } else if (!started) {
            total = 0
            added = 0
            begin(0)
        }
        scheduleSettle()
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
        if (pending.isEmpty()) {
            // A HashMap keeps its table at the largest size it reached.
            pending = HashMap()
            quietSince = now()
            scheduleSettle()
        } else {
            reportProgress()
        }
    }

    /** Whether the collection's tiles are being drawn now. */
    fun drawingChanged(drawing: Boolean) {
        if (drawing == this.drawing) return
        this.drawing = drawing
        if (drawing) {
            settle?.cancel()
            settle = null
            if (!started && drawStart == null) {
                drawStart =
                    scope.launch {
                        delay(DRAW_START_DELAY_MS)
                        drawStart = null
                        if (this@AddProgressTracker.drawing && !started) {
                            total = 0
                            added = 0
                            begin(0)
                        }
                    }
            }
        } else {
            drawStart?.cancel()
            drawStart = null
            quietSince = now()
            scheduleSettle()
        }
    }

    fun dispose() {
        timer?.cancel()
        timer = null
        settle?.cancel()
        settle = null
        drawStart?.cancel()
        drawStart = null
        pending = HashMap()
    }

    private fun begin(total: Int) {
        started = true
        drawStart?.cancel()
        drawStart = null
        onAddStart?.invoke(total)
    }

    private fun scheduleSettle() {
        if (!started) return
        settle?.cancel()
        settle =
            scope.launch {
                delay(SETTLE_MS)
                settle = null
                finishIfIdle()
            }
    }

    private fun finishIfIdle() {
        if (!started || drawing || pending.isNotEmpty()) return
        val wait = quietSince + SETTLE_MS - now()
        if (wait > 0) {
            scheduleSettle()
            return
        }
        timer?.cancel()
        timer = null
        started = false
        val count = total
        total = 0
        added = 0
        if (count > 0) onAddProgress?.invoke(count, count)
        onAddEnd?.invoke(count)
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
    companion object {
        const val PROGRESS_INTERVAL_MS = 100L

        /** Drawing alone counts as work only once it has lasted this long: a camera move over cached tiles does not. */
        const val DRAW_START_DELAY_MS = 200L

        /** Quiet this long -- nothing handed over, arriving or drawn -- before onAddEnd. */
        const val SETTLE_MS = 400L
    }
}

/**
 * The tracker for one collection, listening only while one of the callbacks
 * is set. [activity] says whether the map's tilers for this kind of overlay
 * are drawing (see `BaseMapViewController.tileActivity`).
 */
@InternalMapConductorApi
@Composable
internal fun rememberAddProgress(
    onAddStart: ((total: Int) -> Unit)?,
    onAddProgress: ((added: Int, total: Int) -> Unit)?,
    onAddEnd: ((total: Int) -> Unit)?,
    activity: List<StateFlow<Boolean>> = emptyList(),
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
    LaunchedEffect(tracker, wanted, activity) {
        if (!wanted || activity.isEmpty()) return@LaunchedEffect
        combine(activity) { busy -> busy.any { it } }
            .distinctUntilChanged()
            .collect { tracker.drawingChanged(it) }
    }
    DisposableEffect(tracker) { onDispose { tracker.dispose() } }
    return tracker
}
