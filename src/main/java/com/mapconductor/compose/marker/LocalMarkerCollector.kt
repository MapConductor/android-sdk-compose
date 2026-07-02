package com.mapconductor.compose.marker

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.ChildCollector
import com.mapconductor.core.marker.MarkerState

val LocalMarkerCollector =
    compositionLocalOf<ChildCollector<MarkerState>> {
        error("Marker must be under the <MapView />")
    }
