package vito.cobblebrain.social

import AIHandler
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents

object StartAi : ModInitializer {
    override fun onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            println("Servidor iniciado, inicializando IA...")
            Thread {
                try {
                    val chat = AIHandler("cobblebrain-ai")
                    chat.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { server ->
            println("Servidor encerrado, desligando IA...")
            // aqui você pode sinalizar para a thread parar
        }
    }
}
