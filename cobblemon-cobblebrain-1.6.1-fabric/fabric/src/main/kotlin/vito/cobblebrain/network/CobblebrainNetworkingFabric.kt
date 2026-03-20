package vito.cobblebrain.network

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer

object CobblebrainNetworkingFabric {
    fun sendToPlayer(player: ServerPlayer, prompt: String) {
        ServerPlayNetworking.send(
            player,
            CobblebrainPayloads.PromptPayload(prompt)
        )
    }
}