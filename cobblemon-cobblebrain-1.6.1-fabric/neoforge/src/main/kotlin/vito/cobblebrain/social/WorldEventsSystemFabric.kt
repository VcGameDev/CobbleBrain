package vito.cobblebrain.social

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

object WorldEventsSystemFabric {
    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            WorldEventsSystem.onServerTick(server)
        }
    }
}