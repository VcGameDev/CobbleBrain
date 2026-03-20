package vito.cobblebrain.server

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.minecraft.server.level.ServerPlayer
import vito.cobblebrain.network.CobblebrainPayloads

object CobblebrainServerHandlerNeoForge {

    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("cobblebrain").versioned("1.0")

        registrar.playToServer(
            CobblebrainPayloads.ActionPayload.TYPE,
            CobblebrainPayloads.ActionPayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer

            context.enqueueWork {
                CobblebrainServerHandler.processAction(player, payload.action)
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.AIResponsePayload.TYPE,
            CobblebrainPayloads.AIResponsePayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer

            context.enqueueWork {
                CobblebrainServerHandler.processIaResponse(
                    player.server,
                    player,
                    payload.content
                )
            }
        }
    }
}