package vito.cobblebrain.server

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.minecraft.server.level.ServerPlayer
import vito.cobblebrain.client.CobblebrainClientHandlers
import vito.cobblebrain.network.CobblebrainPayloads

object CobblebrainPayloadRegistrarNeoForge {

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("cobblebrain").versioned("1.0")

        // =========================
        // SERVER → CLIENT
        // =========================

        registrar.playToClient(
            CobblebrainPayloads.PromptPayload.TYPE,
            CobblebrainPayloads.PromptPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                CobblebrainClientHandlers.onPrompt(payload)
            }
        }

        registrar.playToClient(
            CobblebrainPayloads.SyncConfigPayload.TYPE,
            CobblebrainPayloads.SyncConfigPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                CobblebrainClientHandlers.onConfigSync(payload)
            }
        }

        // =========================
        // CLIENT → SERVER
        // =========================

        registrar.playToServer(
            CobblebrainPayloads.ActionPayload.TYPE,
            CobblebrainPayloads.ActionPayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer

            context.enqueueWork {
                CobblebrainServerHandlers.onAction(player, payload)
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.AIResponsePayload.TYPE,
            CobblebrainPayloads.AIResponsePayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer

            context.enqueueWork {
                CobblebrainServerHandlers.onAIResponse(player, payload)
            }
        }
    }
}