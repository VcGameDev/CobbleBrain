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

        registrar.playToClient(
            CobblebrainPayloads.QuestSyncPayload.TYPE,
            CobblebrainPayloads.QuestSyncPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                CobblebrainClientHandlers.onQuestSync(payload)
            }
        }

        registrar.playToClient(
            CobblebrainPayloads.SummaryPromptPayload.TYPE,
            CobblebrainPayloads.SummaryPromptPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                CobblebrainClientHandlers.onSummaryPrompt(payload)
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

        registrar.playToServer(
            CobblebrainPayloads.RequestSummaryPayload.TYPE,
            CobblebrainPayloads.RequestSummaryPayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer

            context.enqueueWork {
                vito.cobblebrain.social.DialogueSystem.triggerSessionSummary(player)
            }
        }
    }
}