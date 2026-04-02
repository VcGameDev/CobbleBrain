package vito.cobblebrain.server

import net.minecraft.server.level.ServerPlayer
import vito.cobblebrain.network.CobblebrainPayloads

object CobblebrainServerHandlers {

    fun onAction(player: ServerPlayer, payload: CobblebrainPayloads.ActionPayload) {
        CobblebrainServerHandler.processAction(player, payload.action)
    }

    fun onAIResponse(player: ServerPlayer, payload: CobblebrainPayloads.AIResponsePayload) {
        CobblebrainServerHandler.processIaResponse(
            player.server,
            player,
            payload.content
        )
    }
}