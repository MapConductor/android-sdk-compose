package com.mapconductor.compose.groundimage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.mapconductor.compose.MapViewScope
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.groundimage.GroundImageState
import com.mapconductor.core.groundimage.GroundImageTileProvider
import com.mapconductor.core.groundimage.OnGroundImageEventHandler
import java.io.Serializable
import android.graphics.drawable.Drawable

@Composable
fun MapViewScope.GroundImage(state: GroundImageState) {
    val collector = LocalGroundImageCollector.current
    LaunchedEffect(state) {
        collector.add(state)
    }

    DisposableEffect(state.id) {
        onDispose {
            collector.remove(state.id)
        }
    }
}

@Composable
fun MapViewScope.GroundImage(
    bounds: GeoRectBounds,
    image: Drawable,
    opacity: Float = 0.5f,
    tileSize: Int = GroundImageTileProvider.DEFAULT_TILE_SIZE,
    id: String? = null,
    extra: Serializable? = null,
    onClick: OnGroundImageEventHandler? = null,
) {
    val state =
        GroundImageState(
            bounds = bounds,
            image = image,
            opacity = opacity,
            tileSize = tileSize,
            id = id,
            extra = extra,
            onClick = onClick,
        )
    GroundImage(state)
}

/**
 * [imageUrl]（http / https / file / data）の画像を貼る。SDK が読み、読み終えるまでは透明。
 * http(s) にはアプリの `android.permission.INTERNET` が要る。
 */
@Composable
fun MapViewScope.GroundImage(
    bounds: GeoRectBounds,
    imageUrl: String,
    opacity: Float = 0.5f,
    tileSize: Int = GroundImageTileProvider.DEFAULT_TILE_SIZE,
    id: String? = null,
    extra: Serializable? = null,
    onClick: OnGroundImageEventHandler? = null,
) {
    val state =
        GroundImageState(
            bounds = bounds,
            imageUrl = imageUrl,
            opacity = opacity,
            tileSize = tileSize,
            id = id,
            extra = extra,
            onClick = onClick,
        )
    GroundImage(state)
}
