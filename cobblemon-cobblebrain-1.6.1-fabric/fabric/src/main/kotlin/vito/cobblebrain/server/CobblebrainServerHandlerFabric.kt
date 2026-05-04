package vito.cobblebrain.server

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import vito.cobblebrain.network.CobblebrainPayloads.ActionPayload
import vito.cobblebrain.network.CobblebrainPayloads.AIResponsePayload

object CobblebrainServerHandlerFabric {
    fun register() {
        // ACTION do client
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE) { payload: ActionPayload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                player.sendSystemMessage(Component.literal("Executando ação: ${payload.action}"))

                // chama o Common
                CobblebrainServerHandler.processAction(player, payload.action)
            }
        }

        // Resposta da IA
        ServerPlayNetworking.registerGlobalReceiver(AIResponsePayload.TYPE) { payload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                println("[SERVER RECEIVED RESPONSE] from ${player.name.string}")

                // chama o Common
                CobblebrainServerHandler.processIaResponse(player.server, player, payload.content)
            }
        }

        // Requisição de Resumo (Tecla L)
        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.RequestSummaryPayload.TYPE) { _, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                vito.cobblebrain.social.DialogueSystem.triggerSessionSummary(player)
            }
        }
    }
}