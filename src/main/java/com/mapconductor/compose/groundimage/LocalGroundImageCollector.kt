package com.mapconductor.compose.groundimage

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.OverlayCollectorInterface
import com.mapconductor.core.groundimage.GroundImageState

val LocalGroundImageCollector =
    compositionLocalOf<OverlayCollectorInterface<GroundImageState>> {
        error("GroundImage must be under the <MapView />")
    }
