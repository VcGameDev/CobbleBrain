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

    fun onRequestPersonalityList(player: ServerPlayer) {
        CobblebrainServerHandler.handleRequestPersonalityList(player)
    }

    fun onSavePersonality(player: ServerPlayer, payload: CobblebrainPayloads.SavePersonalityPayload) {
        CobblebrainServerHandler.handleSavePersonality(
            player,
            payload.pokemonUuid,
            payload.personalityJson
        )
    }

    fun onDeletePersonality(player: ServerPlayer, payload: CobblebrainPayloads.DeletePersonalityPayload) {
        CobblebrainServerHandler.handleDeletePersonality(player, payload.pokemonUuid)
    }
}