package vito.cobblebrain.network

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import vito.cobblebrain.config.ConfigHandler.config

object CobblebrainNetworkingFabric {
    fun sendToPlayer(player: ServerPlayer, prompt: String) {
        ServerPlayNetworking.send(
            player,
            CobblebrainPayloads.PromptPayload(prompt)
        )
    }
    fun sendConfig(player: ServerPlayer) {
        val payload = CobblebrainPayloads.SyncConfigPayload(
            config.outputDialogue,
            config.outputActions,
            config.outputFriendship,
            config.outputMemories,
            config.outputApril1,
            config.outputQuests,
            config.maxLongMemory,
            config.maxShortMemory
        )

        ServerPlayNetworking.send(player, payload)
    }
}