package com.mapconductor.compose.polygon

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.OverlayCollectorInterface
import com.mapconductor.core.polygon.PolygonState

val LocalPolygonCollector =
    compositionLocalOf<OverlayCollectorInterface<PolygonState>> {
        error("Polygon must be under the <MapView />")
    }
