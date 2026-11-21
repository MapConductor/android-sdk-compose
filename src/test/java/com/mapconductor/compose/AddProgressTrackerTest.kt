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
 * （AddProgressTracker）。js-sdk-react の `addProgress.test.mjs`、ios-sdk の
 * `AddProgressTests` と同じ内容。
 *
 * - リストを渡したら onAddStart(total)。total は地図に未だ無い state の数（削除・更新だけなら 0、空のあとの空は何も呼ばない）。
 * - 各 state の onAdded と同じ時点で 1 つ数える。途中経過は 100 ms に 1 回まで。
 * - 全部載り、タイルの描画も止まって 400 ms 静かになったら onAddProgress(total, total) と onAddEnd(total)。
 * - タイルの描画だけ（カメラの移動）は 200 ms 続いたら onAddStart(0)。それより短ければ何も呼ばない。
 * - 載る前に外された state は数から抜ける。新しい追加は数え直し、前のリストの onAddEnd は来ない。
 * - 他のコレクション・他の地図の state は数えない。
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

    private fun elapse(ms: Long) {
        scope.advanceTimeBy(ms)
        scope.runCurrent()
    }

    private fun settle() = elapse(AddProgressTracker.SETTLE_MS)

    @Test
    fun `all on the map - start, progress at the total, end once quiet`() {
        val states = listOf(circle("a"), circle("b"), circle("c"))
        set(states)
        tracker.arrived(listOf(states[0]))
        tracker.arrived(listOf(states[1], states[2]))
        assertEquals(listOf("start 3", "progress 1/3"), calls)
        settle()
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
        elapse(100)
        assertEquals(listOf("start 5", "progress 1/5", "progress 3/5"), calls)
        tracker.arrived(listOf(states[3], states[4]))
        settle()
        assertEquals("end 5", calls.last())
    }

    @Test
    fun `a state removed before it arrived no longer counts`() {
        val (a, b, c) = listOf(circle("a"), circle("b"), circle("c"))
        set(listOf(a, b, c))
        tracker.arrived(listOf(a))
        set(listOf(a, b))
        tracker.arrived(listOf(b))
        settle()
        assertEquals(listOf("progress 2/2", "end 2"), calls.takeLast(2))
    }

    @Test
    fun `removing the rest ends once quiet`() {
        val (a, b) = listOf(circle("a"), circle("b"))
        set(listOf(a, b))
        tracker.arrived(listOf(a))
        set(listOf(a))
        settle()
        assertEquals(listOf("progress 1/1", "end 1"), calls.takeLast(2))
    }

    @Test
    fun `a still-coming state handed over again as a new object is counted by the new one`() {
        val a = circle("a")
        set(listOf(a))
        val a2 = circle("a")
        set(listOf(a2))
        tracker.arrived(listOf(a2))
        settle()
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
        settle()
        assertEquals(listOf("start 2", "start 1", "progress 1/1", "end 1"), calls)
    }

    @Test
    fun `updates and removals start and end with 0, and other collections' states do not count`() {
        val (a, b) = listOf(circle("a"), circle("b"))
        set(listOf(a, b))
        tracker.arrived(listOf(a, b))
        settle()
        calls.clear()
        set(listOf(circle("a"))) // a updated, b removed
        tracker.arrived(listOf(circle("other"), circle("a")))
        assertEquals(listOf("start 0"), calls)
        settle()
        assertEquals(listOf("start 0", "end 0"), calls)
    }

    @Test
    fun `drawing keeps the end waiting until it has been quiet`() {
        val a = circle("a")
        set(listOf(a))
        tracker.arrived(listOf(a))
        tracker.drawingChanged(true)
        elapse(2000)
        assertEquals(listOf("start 1"), calls)
        tracker.drawingChanged(false)
        elapse(AddProgressTracker.SETTLE_MS - 1)
        assertEquals(listOf("start 1"), calls)
        elapse(1)
        assertEquals(listOf("start 1", "progress 1/1", "end 1"), calls)
    }

    @Test
    fun `drawing alone starts after 200 ms and ends once quiet`() {
        tracker.drawingChanged(true)
        elapse(AddProgressTracker.DRAW_START_DELAY_MS - 1)
        assertEquals(emptyList<String>(), calls)
        elapse(1)
        assertEquals(listOf("start 0"), calls)
        tracker.drawingChanged(false)
        settle()
        assertEquals(listOf("start 0", "end 0"), calls)
    }

    @Test
    fun `an empty list after nothing calls nothing`() {
        set(emptyList())
        settle()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `brief drawing calls nothing`() {
        tracker.drawingChanged(true)
        elapse(AddProgressTracker.DRAW_START_DELAY_MS / 2)
        tracker.drawingChanged(false)
        elapse(2000)
        assertEquals(emptyList<String>(), calls)
    }
}
