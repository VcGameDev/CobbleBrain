package vito.cobblebrain.client

import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import vito.cobblebrain.network.CobblebrainNetworkingNeoForge
import vito.cobblebrain.network.CobblebrainPayloads
import vito.cobblebrain.social.DialogueSystem

object CobblebrainClientHandlerNeoForge {

    private var registered = false

    // controle de resposta
    private var waitingResponse = false
    private var waitTicks = 0

    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        if (registered) return
        registered = true

        DialogueSystem.onSendPromptClient = {
            markWaiting()
        }

        CobblebrainClientCommon.sendToServer = { response ->
            CobblebrainNetworkingNeoForge.sendToServer(response)
        }

        val registrar = event.registrar("cobblebrain").versioned("1.0")

        // registra payload
        registrar.playToClient(
            CobblebrainPayloads.PromptPayload.TYPE,
            CobblebrainPayloads.PromptPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                waitingResponse = false

                val mc = net.minecraft.client.Minecraft.getInstance()
                val player = mc.player

                player?.sendSystemMessage(
                    Component.literal("CLIENT RECEBEU PAYLOAD")
                )

                CobblebrainClientCommon.onPromptReceived(payload.prompt)
            }
        }

        // registra tick handler
        NeoForge.EVENT_BUS.register(this)
    }

    // chamar isso quando enviar o prompt (/mpk)
    fun markWaiting() {
        waitingResponse = true
        waitTicks = 0

        val mc = net.minecraft.client.Minecraft.getInstance()
        mc.player?.sendSystemMessage(
            Component.literal("...esperando resposta...")
        )
    }

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        if (!waitingResponse) return

        waitTicks++

        if (waitTicks >= 60) { // ~3 segundos
            waitingResponse = false

            val mc = net.minecraft.client.Minecraft.getInstance()
            mc.player?.sendSystemMessage(
                Component.literal("NÃO RECEBEU PAYLOAD (timeout)")
            )
        }
    }
}