package vito.cobblebrain

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent

object ClientOnlySetup {
    fun register(modEventBus: IEventBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {

            // init principal do client
            modEventBus.addListener { event: FMLClientSetupEvent ->
                CobbleBrainModClientNeoForge.init()
            }

            // keybinds
            modEventBus.addListener(
                CobbleBrainModClientNeoForge::onRegisterKeybinds
            )
        }
    }
}