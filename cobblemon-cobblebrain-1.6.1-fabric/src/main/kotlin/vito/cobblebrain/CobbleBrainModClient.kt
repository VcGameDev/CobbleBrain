package vito.cobblebrain

import net.fabricmc.api.ClientModInitializer

object CobbleBrainModClient : ClientModInitializer {
        override fun onInitializeClient() {
            println("Cobblebrain carregado no cliente")
        }
    }