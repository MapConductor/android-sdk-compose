package com.mapconductor.compose.polyline

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.ChildCollector
import com.mapconductor.core.polyline.PolylineState

val LocalPolylineCollector =
    compositionLocalOf<ChildCollector<PolylineState>> {
        error("Polyline must be under the <MapView />")
    }
