package vito.cobblebrain.social

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

object WorldEventsSystemNeoForge {
    fun register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(this)
    }
    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        WorldEventsSystem.onServerTick(event.server)
    }
}