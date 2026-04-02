package vito.cobblebrain.client

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import vito.cobblebrain.network.CobblebrainNetworkingNeoForge
import vito.cobblebrain.social.DialogueSystem

object CobblebrainClientRuntimeNeoForge {
    // STATE
    private var waitingResponse = false
    private var waitTicks = 0

    fun init() {
        // hook do dialogue system
        DialogueSystem.onSendPromptClient = {
            markWaiting()
        }

        // ligação client → server
        CobblebrainClientCommon.sendToServer = { response ->
            CobblebrainNetworkingNeoForge.sendToServer(response)
        }

        // registra tick
        NeoForge.EVENT_BUS.register(this)
    }

    fun markWaiting() {
        waitingResponse = true
        waitTicks = 0
    }

    fun markResponseReceived() {
        waitingResponse = false
    }

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        if (!waitingResponse) return

        waitTicks++

        if (waitTicks >= 60) {
            waitingResponse = false
            println("Cobblebrain NÃO RECEBEU PAYLOAD (timeout)")
        }
    }
}