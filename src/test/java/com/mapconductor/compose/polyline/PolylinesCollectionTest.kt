package com.mapconductor.compose.polyline

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.mapconductor.compose.MapViewScope
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.polyline.PolylineState
import com.mapconductor.core.polyline.PolylineTilingOptions
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * `Polylines(states)` は `Polygons(states)`・`Markers(states)` と同じく、そのインスタンスが
 * 持つ id だけを地図の collector に足し引きする。複数の Polylines / Polyline が同じ地図で
 * 共存でき、片方を外しても他方は残る。
 *
 * js-sdk-react の `Polylines.test.tsx`、ios-sdk の `PolylinesContentTests` と同じ内容。
 * UI を持たない Applier の上で Compose ランタイムだけを回す（InfoBubbleRegistrationTest と同じ）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PolylinesCollectionTest {
    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class NoNodeApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(
            index: Int,
            instance: Unit,
        ) = Unit

        override fun insertBottomUp(
            index: Int,
            instance: Unit,
        ) = Unit

        override fun remove(
            index: Int,
            count: Int,
        ) = Unit

        override fun move(
            from: Int,
            to: Int,
            count: Int,
        ) = Unit

        override fun onClear() = Unit
    }

    /**
     * [content] を合成し、[steps] を順に実行して、各手順のあと再合成と
     * Polylines の差分（Dispatchers.Default で走る）が落ち着くまで回す。
     */
    private fun compose(
        content: @Composable () -> Unit,
        vararg steps: () -> Unit,
    ) = runBlocking {
        val clock = BroadcastFrameClock()
        withContext(clock + Dispatchers.Unconfined) {
            val recomposer = Recomposer(coroutineContext)
            val job =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    recomposer.runRecomposeAndApplyChanges()
                }
            val composition = Composition(NoNodeApplier(), recomposer)
            composition.setContent(content)

            suspend fun settle() {
                repeat(10) {
                    Snapshot.sendApplyNotifications()
                    yield()
                    clock.sendFrame(0L)
                    yield()
                    Thread.sleep(20)
                }
            }
            settle()
            for (step in steps) {
                step()
                settle()
            }
            composition.dispose()
            recomposer.close()
            job.join()
        }
    }

    // 点を id ごとに変える: PolylineState の equals は id を見ないので、同じ点だと
    // 別の一覧に差し替えても mutableStateOf が変化と見なさない。
    private fun line(id: String): PolylineState {
        val d = id.hashCode() % 1000 * 1e-5
        return PolylineState(
            points = listOf(GeoPoint.fromLatLong(42.35 + d, -71.06), GeoPoint.fromLatLong(42.36 + d, -71.05)),
            id = id,
        )
    }

    @Test
    fun twoPolylinesShareTheMap_andRemovingOneKeepsTheOther() {
        val scope = MapViewScope()
        val a = listOf(line("a1"), line("a2"))
        val b = listOf(line("b1"))
        var showB by mutableStateOf(true)
        var listA by mutableStateOf(a)
        val seen = mutableListOf<Set<String>>()

        compose(
            {
                CompositionLocalProvider(LocalPolylineCollector provides scope.polylineCollector) {
                    scope.Polylines(listA)
                    if (showB) scope.Polylines(b)
                    scope.Polyline(line("single"))
                }
            },
            { seen += scope.polylineCollector.flow.value.keys },
            // 片方の Polylines を外す: 自分の id だけ消える
            { showB = false },
            { seen += scope.polylineCollector.flow.value.keys },
            // 一覧を差し替える: 消えた id だけ消え、他の Polylines / Polyline のものは残る
            { listA = listOf(line("a2"), line("a3")) },
            { seen += scope.polylineCollector.flow.value.keys },
        )

        assertEquals(setOf("a1", "a2", "b1", "single"), seen[0])
        assertEquals(setOf("a1", "a2", "single"), seen[1])
        assertEquals(setOf("a2", "a3", "single"), seen[2])
    }

    @Test
    fun polylineTilingSetsTheMapsOptions() {
        val scope = MapViewScope()
        var options by mutableStateOf(PolylineTilingOptions.Disabled)
        val seen = mutableListOf<PolylineTilingOptions?>()
        assertNull("指定が無ければ null（プロバイダの既定）", scope.polylineTilingOptions.value)

        compose(
            {
                CompositionLocalProvider(LocalPolylineCollector provides scope.polylineCollector) {
                    scope.PolylineTiling(options)
                }
            },
            { seen += scope.polylineTilingOptions.value },
            { options = PolylineTilingOptions(minPolylineCount = 10) },
            { seen += scope.polylineTilingOptions.value },
        )

        assertEquals(listOf(PolylineTilingOptions.Disabled, PolylineTilingOptions(minPolylineCount = 10)), seen)
    }
}
