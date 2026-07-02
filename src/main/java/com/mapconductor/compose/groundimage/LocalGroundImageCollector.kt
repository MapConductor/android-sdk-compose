package com.mapconductor.compose.groundimage

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.ChildCollector
import com.mapconductor.core.groundimage.GroundImageState

val LocalGroundImageCollector =
    compositionLocalOf<ChildCollector<GroundImageState>> {
        error("GroundImage must be under the <MapView />")
    }
