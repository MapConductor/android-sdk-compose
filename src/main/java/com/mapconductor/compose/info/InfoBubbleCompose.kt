package com.mapconductor.compose.info

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mapconductor.compose.MapViewScope
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.marker.DefaultMarkerIcon
import com.mapconductor.core.marker.MarkerIconInterface
import com.mapconductor.core.marker.MarkerState
import java.util.UUID

// @Composable
// fun MapViewScope.InfoAnchor(
//    props: MarkerState,
//    anchor: Offset = Offset(0.5f, 1f),
//    content: @Composable () -> Unit,
// ) {
//    val bubble = remember { InfoBubbleSpec(props, anchor, content) }
//    SideEffect {
//        selectedInfo.value = bubble
//    }
// }

@Composable
fun MapViewScope.InfoBubble(
    marker: MarkerState,
    bubbleColor: Color = Color.Companion.White,
    borderColor: Color = Color.Companion.Black,
    borderWidth: Dp = 2.dp,
    contentPadding: Dp = 8.dp,
    cornerRadius: Dp = 4.dp,
    tailSize: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    val wrapped: @Composable () -> Unit = {
        DrawInfoBubble(
            modifier = Modifier,
            bubbleColor = bubbleColor,
            borderColor = borderColor,
            borderWidth = borderWidth,
            contentPadding = contentPadding,
            cornerRadius = cornerRadius,
            tailSize = tailSize,
            content = content,
        )
    }

    RegisterInfoBubble(
        id = marker.id,
        anchorKey = marker,
        positionProvider = { marker.position },
        // Marker本体もicon未指定時はDefaultMarkerIconを描画するため、InfoBubbleの
        // 接続位置計算にも同じアイコン寸法・anchor・infoAnchorを使用する。
        // marker.icon はスナップショット状態なので、ここで読めば差し替えで再合成される。
        icon = marker.icon,
        defaultIcon = true,
        tailOffset = Offset(0.5f, 1.0f),
        content = wrapped,
    )
}

/**
 * Places an InfoBubble at the given [position] on the map without requiring a [MarkerState].
 *
 * Usage:
 * ```
 * InfoBubble(position = GeoPoint(35.68, 139.77)) {
 *     Text("Hello!")
 * }
 * ```
 */
@Composable
fun MapViewScope.InfoBubble(
    position: GeoPoint,
    bubbleColor: Color = Color.Companion.White,
    borderColor: Color = Color.Companion.Black,
    borderWidth: Dp = 2.dp,
    contentPadding: Dp = 8.dp,
    cornerRadius: Dp = 4.dp,
    tailSize: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    val id = remember { UUID.randomUUID().toString() }

    val wrapped: @Composable () -> Unit = {
        DrawInfoBubble(
            modifier = Modifier,
            bubbleColor = bubbleColor,
            borderColor = borderColor,
            borderWidth = borderWidth,
            contentPadding = contentPadding,
            cornerRadius = cornerRadius,
            tailSize = tailSize,
            content = content,
        )
    }

    // Re-register when position changes so the bubble moves to the new coordinates.
    RegisterInfoBubble(
        id = id,
        anchorKey = position,
        positionProvider = { position },
        icon = null,
        defaultIcon = false,
        tailOffset = Offset(0.5f, 1.0f),
        content = wrapped,
    )
}

/**
 * Register a fully custom InfoBubble content positioned by the existing overlay engine.
 *
 * - Reuses the internal InfoBubbleOverlay for screen positioning.
 * - `tailOffset` specifies where, inside your content box (0..1), the connection point sits.
 *   For example, right-side middle is Offset(1f, 0.5f); bottom-center is Offset(0.5f, 1f).
 * - The provided [content] should draw the bubble (including its tail) in any shape you like.
 */
@Composable
fun MapViewScope.InfoBubbleCustom(
    marker: MarkerState,
    tailOffset: Offset,
    content: @Composable () -> Unit,
) {
    RegisterInfoBubble(
        id = marker.id,
        anchorKey = marker,
        positionProvider = { marker.position },
        icon = marker.icon,
        defaultIcon = true,
        tailOffset = tailOffset,
        content = content,
    )
}

/**
 * 吹き出しを [MapViewScope.bubbleFlow] に登録し、中身を常に最新に保つ。
 *
 * 以前は各関数が `DisposableEffect(marker)` の中で**最初の合成時の** [InfoBubbleEntry] を
 * 登録していた。エントリの `content` はそのときのラムダで、色や中身の値を捕まえたまま
 * 固まるので、`bubbleColor` やラムダに渡した値、マーカーのアイコンが後から変わっても
 * 画面の吹き出しは古いままだった。
 *
 * ここではエントリを作り直さず、`content` を「最新のラムダを読んで呼ぶ」ラムダにする
 * （[rememberUpdatedState]）。中身や見た目が変わっても再合成されるのは吹き出しの中だけで、
 * `bubbleFlow` は鳴らない。位置・アイコン・接続点のように**どこに出すか**が変わったときだけ
 * エントリを差し替える。
 *
 * @param anchorKey 位置を決めるもの（マーカーまたは座標）。変わったら登録し直す。
 * @param icon 接続位置の計算に使うアイコン。[defaultIcon] が true で null なら既定のピン。
 */
@Composable
private fun MapViewScope.RegisterInfoBubble(
    id: String,
    anchorKey: Any,
    positionProvider: () -> GeoPointInterface,
    icon: MarkerIconInterface?,
    defaultIcon: Boolean,
    tailOffset: Offset,
    content: @Composable () -> Unit,
) {
    val currentContent = rememberUpdatedState(content)
    val entry =
        remember(id, anchorKey, icon, tailOffset) {
            InfoBubbleEntry(
                id = id,
                positionProvider = positionProvider,
                icon = icon ?: if (defaultIcon) DefaultMarkerIcon() else null,
                tailOffset = tailOffset,
                content = { currentContent.value() },
            )
        }

    DisposableEffect(entry) {
        val newMap = bubbleFlow.value.toMutableMap()
        newMap[entry.id] = entry
        bubbleFlow.value = newMap

        onDispose {
            // 差し替えでは新しいエントリが同じ id で入る。消すのは自分が入れたものだけ。
            val current = bubbleFlow.value
            if (current[entry.id] === entry) {
                val newMap = current.toMutableMap()
                newMap.remove(entry.id)
                bubbleFlow.value = newMap
            }
        }
    }
}
