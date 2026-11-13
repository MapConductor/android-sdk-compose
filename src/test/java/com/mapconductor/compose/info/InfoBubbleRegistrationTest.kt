package com.mapconductor.compose.info

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mapconductor.compose.MapViewScope
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.marker.BitmapIcon
import com.mapconductor.core.marker.MarkerIconInterface
import com.mapconductor.core.marker.MarkerState
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
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * InfoBubble / InfoBubbleCustom が、後から変わった中身・見た目・アイコンに追従すること。
 *
 * 以前は `DisposableEffect(marker)` の中で最初の合成時のエントリを登録していたので、
 * 中身のラムダが捕まえた値や `bubbleColor`、マーカーのアイコンが変わっても吹き出しは
 * 古いままだった。落ちもせず吹き出しも出るので、画面で「数字が変わらない」を見るまで
 * 気づけない。
 *
 * 直し方の約束:
 * - 中身や見た目が変わっても**エントリは同じ参照のまま**（bubbleFlow を鳴らさない）
 * - それでもエントリの content を描けば**最新の中身**が出る
 * - 位置・アイコン・接続点が変わったときだけエントリを差し替える
 *
 * UI を持たない Applier の上で Compose ランタイムだけを回す。中身の描画確認は
 * UI を出さない InfoBubbleCustom で行う。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InfoBubbleRegistrationTest {
    // MapViewScope の OverlayCollector は Main で購読を始める。JVM には Main が無い。
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

    /** [content] を合成し、[steps] を順に実行して、各手順のあと再合成が落ち着くまで回す。 */
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
                repeat(5) {
                    Snapshot.sendApplyNotifications()
                    yield()
                    clock.sendFrame(0L)
                    yield()
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

    /** DefaultMarkerIcon は JVM では作れない（Typeface などが要る）ので、寸法だけの代役。 */
    private class StubIcon(
        override val iconSize: Dp,
    ) : MarkerIconInterface {
        override val scale = 1f
        override val anchor = Offset(0.5f, 1f)
        override val infoAnchor = Offset(0.5f, 0f)
        override val debug = false

        override fun toBitmapIcon(): BitmapIcon = error("not drawn in this test")
    }

    private fun marker() =
        MarkerState(
            id = "m1",
            position = GeoPoint.fromLatLong(35.0, 139.0),
            icon = StubIcon(48.dp),
        )

    @Test
    fun customBubbleShowsLatestContentWithoutReRegistering() {
        val scope = MapViewScope()
        val marker = marker()
        var label by mutableStateOf("count 0")
        val rendered = mutableListOf<String>()
        val entries = mutableListOf<InfoBubbleEntry?>()

        compose(
            {
                // 値をラムダの外で読む: ラムダは String を捕まえるので、毎回別のラムダになる。
                val text = label
                scope.InfoBubbleCustom(marker = marker, tailOffset = Offset(0.5f, 1f)) {
                    rendered += text
                }
                // プロバイダ側の描画（MapViewBase と同じく bubbleFlow を読んで content を呼ぶ）。
                val bubbles by scope.bubbleFlow.collectAsState()
                bubbles.values.forEach { entry -> key(entry.id) { entry.content() } }
            },
            { entries += scope.bubbleFlow.value["m1"] },
            { label = "count 1" },
            { entries += scope.bubbleFlow.value["m1"] },
            { label = "count 2" },
            { entries += scope.bubbleFlow.value["m1"] },
        )

        assertEquals("count 2", rendered.last())
        assertTrue("count 1 も描かれている: $rendered", "count 1" in rendered)
        assertSame("中身の変更でエントリを作り直さない", entries[0], entries[1])
        assertSame(entries[1], entries[2])
        assertNull("合成を抜けたら登録も消える", scope.bubbleFlow.value["m1"])
    }

    @Test
    fun styledBubbleKeepsEntryWhenOnlyAppearanceChanges() {
        val scope = MapViewScope()
        val marker = marker()
        var color by mutableStateOf(Color.White)
        val entries = mutableListOf<InfoBubbleEntry?>()

        compose(
            {
                scope.InfoBubble(marker = marker, bubbleColor = color) {}
                val bubbles by scope.bubbleFlow.collectAsState()
                bubbles.size // 読むだけ
            },
            { entries += scope.bubbleFlow.value["m1"] },
            { color = Color.Yellow },
            { color = Color.Green },
            { entries += scope.bubbleFlow.value["m1"] },
        )
        assertSame("見た目の変更でエントリを作り直さない", entries[0], entries[1])
    }

    @Test
    fun iconChangeOnTheSameMarkerReplacesTheEntry() {
        val scope = MapViewScope()
        val marker = marker()
        val oldIcon = marker.icon
        val newIcon = StubIcon(64.dp)
        val entries = mutableListOf<InfoBubbleEntry?>()

        compose(
            { scope.InfoBubble(marker = marker) {} },
            { entries += scope.bubbleFlow.value["m1"] },
            { marker.icon = newIcon },
            { entries += scope.bubbleFlow.value["m1"] },
        )

        assertNotSame("アイコンが変わったら接続位置の計算を変えるため差し替える", entries[0], entries[1])
        assertSame(newIcon, entries[1]?.icon)
        assertSame(oldIcon, entries[0]?.icon)
    }

    @Test
    fun positionBubbleIsReRegisteredOnlyWhenThePositionChanges() {
        val scope = MapViewScope()
        var position by mutableStateOf(GeoPoint.fromLatLong(35.0, 139.0))
        var label by mutableStateOf("a")
        val entries = mutableListOf<InfoBubbleEntry?>()

        compose(
            {
                val text = label
                scope.InfoBubble(position = position) { text.length }
            },
            {
                entries +=
                    scope.bubbleFlow.value.values
                        .single()
            },
            { label = "b" },
            {
                entries +=
                    scope.bubbleFlow.value.values
                        .single()
            },
            { position = GeoPoint.fromLatLong(36.0, 140.0) },
            {
                entries +=
                    scope.bubbleFlow.value.values
                        .single()
            },
        )

        assertSame(entries[0], entries[1])
        assertNotSame(entries[1], entries[2])
        assertEquals(36.0, entries[2]!!.positionProvider().latitude, 0.0)
    }
}
