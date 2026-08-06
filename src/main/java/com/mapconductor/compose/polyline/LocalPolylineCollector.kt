package com.mapconductor.compose.polyline

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.OverlayCollectorInterface
import com.mapconductor.core.polyline.PolylineState

val LocalPolylineCollector =
    compositionLocalOf<OverlayCollectorInterface<PolylineState>> {
        error("Polyline must be under the <MapView />")
    }
