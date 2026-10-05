package com.mapconductor.compose.info

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 吹き出しが自分のタップを消費する範囲。
 *
 * 消費しすぎても消費しなさすぎても、落ちも警告も出ない。狭ければタップが下の
 * 地図へ抜けて別のマーカーが選ばれ、広ければ地図が見えている場所を叩いても
 * 何も起きない。どちらも「たまに反応がおかしい」としか見えないので、境界は
 * ここで固定する。
 *
 * 座標系は [DrawInfoBubble] の Box の左上原点。高さはしっぽを含む。
 */
class DrawInfoBubbleHitTest {
    private val width = 200f
    private val height = 108f
    private val tail = 8f
    private val bodyBottom = height - tail // 100f

    private fun inside(
        x: Float,
        y: Float,
    ) = isInsideBubble(Offset(x, y), height, width, tail)

    @Test
    fun bodyIsInside() {
        assertTrue("中央", inside(width / 2f, bodyBottom / 2f))
        assertTrue("左上の角", inside(0f, 0f))
        assertTrue("右上の角", inside(width, 0f))
        assertTrue("本体の下端", inside(10f, bodyBottom))
    }

    /**
     * しっぽの左右は箱の中だが吹き出しではない。ここが本題で、外接矩形をその
     * まま使っていたときはこの領域まで吹き出し扱いになっていた。
     */
    @Test
    fun besideTheTailIsOutside() {
        assertFalse("しっぽの左", inside(10f, bodyBottom + tail / 2f))
        assertFalse("しっぽの右", inside(width - 10f, bodyBottom + tail / 2f))
        assertFalse("左下の角", inside(0f, height))
        assertFalse("右下の角", inside(width, height))
    }

    /**
     * しっぽ自体も外側。
     *
     * 既定 8dp の三角形なので、外して困るのは先端を狙って叩いた場合だけで、
     * そのとき起きるのは下の地図が反応することにすぎない。
     */
    @Test
    fun theTailIsOutside() {
        assertFalse("しっぽの中心", inside(width / 2f, bodyBottom + tail / 2f))
        assertFalse("先端", inside(width / 2f, height))
    }

    @Test
    fun outsideTheBoxIsOutside() {
        assertFalse(inside(-1f, 10f))
        assertFalse(inside(width + 1f, 10f))
        assertFalse(inside(10f, -1f))
        assertFalse(inside(10f, height + 1f))
    }
}
