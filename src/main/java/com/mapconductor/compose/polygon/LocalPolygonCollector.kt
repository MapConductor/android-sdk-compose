package com.mapconductor.compose.polygon

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.ChildCollector
import com.mapconductor.core.polygon.PolygonState

val LocalPolygonCollector =
    compositionLocalOf<ChildCollector<PolygonState>> {
        error("Polygon must be under the <MapView />")
    }
