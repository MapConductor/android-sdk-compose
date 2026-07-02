package com.mapconductor.compose.raster

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.ChildCollector
import com.mapconductor.core.raster.RasterLayerState

val LocalRasterLayerCollector =
    compositionLocalOf<ChildCollector<RasterLayerState>> {
        error("RasterLayer must be under the <MapView />")
    }
