package com.mapconductor.compose.map

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * A lifecycle for one map view that follows the host's but can end on its own.
 *
 * Some SDKs tie what a map view holds to the [LifecycleOwner] it is given or
 * finds in the view tree -- coroutines in its `lifecycleScope`, observers,
 * component callbacks -- and let go of it only when that lifecycle is
 * destroyed. Given the Activity's, a map the user switched away from stays
 * until the Activity goes. Give the view this instead and call [destroy] from
 * `onDestroyMapView`.
 *
 * Until [destroy] it moves with [host] (it catches up on creation). [destroy]
 * stops following and moves it down to DESTROYED, which the SDK sees as
 * ON_PAUSE / ON_STOP / ON_DESTROY as far as it had got. Calling it again, or
 * after the host was destroyed, does nothing.
 */
class MapViewLifecycleOwner(
    private val host: Lifecycle,
) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    private val follow =
        LifecycleEventObserver { _, event ->
            if (registry.currentState != Lifecycle.State.DESTROYED) registry.handleLifecycleEvent(event)
        }

    init {
        host.addObserver(follow)
    }

    override val lifecycle: Lifecycle
        get() = registry

    fun destroy() {
        host.removeObserver(follow)
        // INITIALIZED cannot go straight to DESTROYED; nothing was started then.
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
