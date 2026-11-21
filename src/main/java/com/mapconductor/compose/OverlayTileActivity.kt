package com.mapconductor.compose

import androidx.compose.runtime.compositionLocalOf
import com.mapconductor.core.InternalMapConductorApi
import com.mapconductor.core.controller.OverlayKind
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether this map's tilers of each overlay kind are busy, for the collections'
 * onAddStart / onAddEnd (see [AddProgressTracker]). Nothing outside a map view
 * that provides it: the collections then go by their hand-overs alone.
 */
@InternalMapConductorApi
val LocalOverlayTileActivity =
    compositionLocalOf<(OverlayKind) -> List<StateFlow<Boolean>>> { { emptyList() } }
