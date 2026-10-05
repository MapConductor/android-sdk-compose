package com.mapconductor.compose.info

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp

@Composable
internal fun DrawInfoBubble(
    modifier: Modifier,
    bubbleColor: Color,
    borderColor: Color,
    borderWidth: Dp,
    contentPadding: Dp,
    cornerRadius: Dp,
    tailSize: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .wrapContentSize()
                .pointerInput(tailSize) {
                    // 吹き出しの上のタップを地図へ落とさない。
                    //
                    // 地図は下に敷かれた AndroidView で、誰も消費しなかった
                    // イベントはそこへ届く。吹き出しの余白を叩くと地図のタップ
                    // として扱われ、下にいたマーカーが選ばれたり吹き出しが閉じ
                    // たりする。「当たったが誰も使わなかった」と「当たらなかっ
                    // た」は別なので、前者をここで止める。
                    //
                    // 消費は Main パスで行う。Compose は Main を子から親へ配る
                    // ため、ここへ来た時点で中身のボタンや clickable は既に自分
                    // の分を受け取っている。Initial パスで消費すると、それらが
                    // 動かなくなる。
                    val tailPx = tailSize.toPx()
                    val boxHeight = size.height.toFloat()
                    val boxWidth = size.width.toFloat()
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { change ->
                                if (isInsideBubble(change.position, boxHeight, boxWidth, tailPx)) {
                                    change.consume()
                                }
                            }
                        }
                    }
                },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val tailSizePx = tailSize.toPx()
            val cornerPx = cornerRadius.toPx()

            val path =
                Path().apply {
                    moveTo(2 * cornerPx, 0f)
                    lineTo(width - 2 * cornerPx, 0f)
                    // -- top / right corner --
                    arcTo(
                        rect =
                            Rect(
                                topLeft = Offset(width - 2 * cornerPx, 0f),
                                bottomRight = Offset(width, 2 * cornerPx),
                            ),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false,
                    )
                    lineTo(width, height - tailSizePx - 2 * cornerPx)
                    // -- bottom / right corner --
                    arcTo(
                        rect =
                            Rect(
                                topLeft = Offset(width - 2 * cornerPx, height - tailSizePx - 2 * cornerPx),
                                bottomRight = Offset(width, height - tailSizePx),
                            ),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false,
                    )
                    // -- tail --
                    lineTo(width / 2 + tailSizePx / 2, height - tailSizePx)
                    lineTo(width / 2, height)
                    lineTo(width / 2 - tailSizePx / 2, height - tailSizePx)
                    lineTo(2 * cornerPx, height - tailSizePx)
                    // -- bottom / left
                    arcTo(
                        rect =
                            Rect(
                                topLeft = Offset(0f, height - tailSizePx - 2 * cornerPx),
                                bottomRight = Offset(2 * cornerPx, height - tailSizePx),
                            ),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false,
                    )
                    lineTo(0f, 2 * cornerPx)
                    arcTo(
                        rect =
                            Rect(
                                topLeft = Offset(0f, 0f),
                                bottomRight = Offset(2 * cornerPx, 2 * cornerPx),
                            ),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false,
                    )
                    close()
                }

            drawPath(path, color = bubbleColor, style = Fill)
            drawPath(path, color = borderColor, style = Stroke(width = borderWidth.toPx()))
        }
        // 内容
        Box(
            modifier =
                Modifier
                    .padding(
                        start = contentPadding,
                        top = contentPadding,
                        bottom = contentPadding + tailSize,
                        end = contentPadding,
                    ).wrapContentSize()
                    .clip(RoundedCornerShape(cornerRadius)),
        ) {
            content()
        }
    }
}

/**
 * 吹き出しの本体の内側か。
 *
 * Box はしっぽを含む外接矩形なので、しっぽの左右の角は箱の中だが吹き出しでは
 * ない。そこまで吹き出し扱いにすると、地図が見えている場所を叩いても何も起き
 * ない。
 *
 * しっぽ自体も外している。既定 8dp の三角形で、外して困るのは先端を狙って
 * 叩いた場合だけ。そのとき起きるのは下の地図が反応することで、狙っていた
 * ものが動かないのとは違う。角の丸めも同じ理由で無視している。
 */
internal fun isInsideBubble(
    point: Offset,
    height: Float,
    width: Float,
    tailPx: Float,
): Boolean =
    point.x >= 0f &&
        point.x <= width &&
        point.y >= 0f &&
        point.y <= height - tailPx
