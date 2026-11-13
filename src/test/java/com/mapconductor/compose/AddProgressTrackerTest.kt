package com.mapconductor.compose

import com.mapconductor.core.circle.CircleState
import com.mapconductor.core.features.GeoPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `Markers` / `Circles` / `Polylines` / `Polygons` の onAddStart / onAddProgress / onAddEnd
 * （AddProgressTracker）。js-sdk-react の `addProgress.test.mjs` と同じ内容。
 *
 * - 地図に未だ無い state を持ち込んだリストで onAddStart(total)。
 * - 各 state の onAdded と同じ時点で 1 つ数える。途中経過は 100 ms に 1 回まで。
 * - 全部載ったら onAddProgress(total, total) と onAddEnd(total)。
 * - 載る前に外された state は数から抜ける。新しい追加は数え直し、前のリストの onAddEnd は来ない。
 * - 更新・削除だけのリストは何も呼ばない。他のコレクション・他の地図の state は数えない。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddProgressTrackerTest {
    private val scope = TestScope()
    private val calls = mutableListOf<String>()
    private var prevIds: Set<String> = emptySet()
    private val tracker =
        AddProgressTracker(scope, now = { scope.testScheduler.currentTime + 1000 }).apply {
            onAddStart = { calls += "start $it" }
            onAddProgress = { added, total -> calls += "progress $added/$total" }
            onAddEnd = { calls += "end $it" }
        }

    private fun circle(id: String) = CircleState(id = id, center = GeoPoint.fromLatLong(42.35, -71.06), radiusMeters = 5.0)

    /** What Circles() does with a new list. */
    private fun set(states: List<CircleState>) {
        val nextIds = states.map { it.id }.toSet()
        tracker.track(states, nextIds, tracker.newStates(states, prevIds, wanted = true))
        prevIds = nextIds
    }

    @Test
    fun `all on the map - start, progress at the total, end`() {
        val states = listOf(circle("a"), circle("b"), circle("c"))
        set(states)
        tracker.arrived(listOf(states[0]))
        tracker.arrived(listOf(states[1], states[2]))
        assertEquals(listOf("start 3", "progress 1/3", "progress 3/3", "end 3"), calls)
    }

    @Test
    fun `progress at most every 100 ms`() {
        val states = List(5) { circle("s$it") }
        set(states)
        tracker.arrived(listOf(states[0])) // now
        tracker.arrived(listOf(states[1])) // held
        tracker.arrived(listOf(states[2])) // held
        assertEquals(listOf("start 5", "progress 1/5"), calls)
        scope.advanceTimeBy(100)
        scope.runCurrent()
        assertEquals(listOf("start 5", "progress 1/5", "progress 3/5"), calls)
        tracker.arrived(listOf(states[3], states[4]))
        assertEquals("end 5", calls.last())
    }

    @Test
    fun `a state removed before it arrived no longer counts`() {
        val (a, b, c) = listOf(circle("a"), circle("b"), circle("c"))
        set(listOf(a, b, c))
        tracker.arrived(listOf(a))
        set(listOf(a, b))
        tracker.arrived(listOf(b))
        assertEquals(listOf("progress 2/2", "end 2"), calls.takeLast(2))
    }

    @Test
    fun `removing the rest ends at once`() {
        val (a, b) = listOf(circle("a"), circle("b"))
        set(listOf(a, b))
        tracker.arrived(listOf(a))
        set(listOf(a))
        assertEquals(listOf("progress 1/1", "end 1"), calls.takeLast(2))
    }

    @Test
    fun `a still-coming state handed over again as a new object is counted by the new one`() {
        val a = circle("a")
        set(listOf(a))
        val a2 = circle("a")
        set(listOf(a2))
        tracker.arrived(listOf(a2))
        assertEquals(listOf("start 1", "progress 1/1", "end 1"), calls)
    }

    @Test
    fun `new states while others are coming start over without an end for the earlier list`() {
        val (a, b, c) = listOf(circle("a"), circle("b"), circle("c"))
        set(listOf(a, b))
        set(listOf(a, b, c))
        assertEquals(listOf("start 2", "start 1"), calls)
        tracker.arrived(listOf(a, b))
        tracker.arrived(listOf(c))
        assertEquals(listOf("start 2", "start 1", "progress 1/1", "end 1"), calls)
    }

    @Test
    fun `updates, removals and other collections' states call nothing`() {
        val (a, b) = listOf(circle("a"), circle("b"))
        set(listOf(a, b))
        tracker.arrived(listOf(a, b))
        calls.clear()
        set(listOf(circle("a"))) // a updated, b removed
        tracker.arrived(listOf(circle("other"), circle("a")))
        assertEquals(emptyList<String>(), calls)
    }
}
