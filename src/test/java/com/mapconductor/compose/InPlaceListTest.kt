package com.mapconductor.compose

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import com.mapconductor.compose.circle.Circles
import com.mapconductor.compose.circle.LocalCircleCollector
import com.mapconductor.compose.marker.LocalMarkerCollector
import com.mapconductor.compose.marker.Markers
import com.mapconductor.compose.polygon.LocalPolygonCollector
import com.mapconductor.compose.polygon.Polygons
import com.mapconductor.compose.polyline.LocalPolylineCollector
import com.mapconductor.compose.polyline.Polylines
import com.mapconductor.core.circle.CircleState
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.marker.MarkerState
import com.mapconductor.core.polygon.PolygonState
import com.mapconductor.core.polyline.PolylineState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * `Markers` / `Circles` / `Polylines` / `Polygons` に `mutableStateListOf` を渡し、
 * 同じ一覧に足したり消したりしたものが地図の collector まで届く。
 *
 * 一覧が差し替わったときにしか渡し直していなかったので、タップのたびに
 * `markers.add(…)` するコードはログでは 1 本ずつ増え、地図には 1 本も出なかった。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InPlaceListTest {
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

    /** CirclesCollectionTest と同じ: 合成し、各手順のあと落ち着くまで回す。 */
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

    private fun point(id: String): GeoPoint {
        val d = id.hashCode() % 1000 * 1e-5
        return GeoPoint.fromLatLong(35.68 + d, 139.76)
    }

    private fun marker(id: String) = MarkerState(position = point(id), id = id)

    private fun circle(id: String) = CircleState(center = point(id), radiusMeters = 10.0, id = id)

    private fun line(id: String) = PolylineState(points = listOf(point(id), point("$id-end")), id = id)

    private fun polygon(id: String) = PolygonState(points = listOf(point(id), point("$id-b"), point("$id-c")), id = id)

    @Test
    fun statesAddedToAMutableStateListReachTheMap() {
        val scope = MapViewScope()
        val markers = mutableStateListOf<MarkerState>()
        val circles = mutableStateListOf(circle("c1"))
        val lines = mutableStateListOf<PolylineState>()
        val polygons = mutableStateListOf<PolygonState>()
        val seen = mutableListOf<List<Set<String>>>()

        fun look() {
            seen +=
                listOf(
                    scope.markerCollector.flow.value.keys
                        .toSet(),
                    scope.circleCollector.flow.value.keys
                        .toSet(),
                    scope.polylineCollector.flow.value.keys
                        .toSet(),
                    scope.polygonCollector.flow.value.keys
                        .toSet(),
                )
        }

        compose(
            {
                CompositionLocalProvider(
                    LocalMarkerCollector provides scope.markerCollector,
                    LocalCircleCollector provides scope.circleCollector,
                    LocalPolylineCollector provides scope.polylineCollector,
                    LocalPolygonCollector provides scope.polygonCollector,
                ) {
                    scope.Markers(markers)
                    scope.Circles(circles)
                    scope.Polylines(lines)
                    scope.Polygons(polygons)
                }
            },
            { look() },
            // タップ 1 回ぶん: 同じ一覧に足す
            {
                markers.add(marker("m1"))
                circles.add(circle("c2"))
                lines.add(line("l1"))
                polygons.add(polygon("p1"))
            },
            { look() },
            {
                markers.add(marker("m2"))
                circles.removeAt(0)
            },
            { look() },
        )

        assertEquals(listOf(emptySet(), setOf("c1"), emptySet(), emptySet<String>()), seen[0])
        assertEquals(listOf(setOf("m1"), setOf("c1", "c2"), setOf("l1"), setOf("p1")), seen[1])
        assertEquals(listOf(setOf("m1", "m2"), setOf("c2"), setOf("l1"), setOf("p1")), seen[2])
    }
}
