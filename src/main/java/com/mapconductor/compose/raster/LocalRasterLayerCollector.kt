package com.mapconductor.compose.raster

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.OverlayCollectorInterface
import com.mapconductor.core.raster.RasterLayerState

val LocalRasterLayerCollector =
    compositionLocalOf<OverlayCollectorInterface<RasterLayerState>> {
        error("RasterLayer must be under the <MapView />")
    }
