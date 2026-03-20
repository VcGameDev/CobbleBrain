package vito.cobblebrain.sensors

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.ServerTickEvent

object CommandTickHandlerNeoForge {
    private var tickCounter = 0
    fun registerTickHandler() {
        NeoForge.EVENT_BUS.register(this)
    }
    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        tickCounter++

        if (tickCounter % 2 != 0) return

        // chama o Common
        CommandTickHandler.processActiveCommands(event.server)
    }
}