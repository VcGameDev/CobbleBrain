package vito.cobblebrain.sensors

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

object CommandTickHandlerFabric {
    private var tickCounter = 0

    fun registerTickHandler() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            tickCounter++
            if (tickCounter % 2 != 0) return@register // roda a cada 2 ticks (~0.1s)

            // chama o Common
            CommandTickHandler.processActiveCommands(server)
        }
    }
}