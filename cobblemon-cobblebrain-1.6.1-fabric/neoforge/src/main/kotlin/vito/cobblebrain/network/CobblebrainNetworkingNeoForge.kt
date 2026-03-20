package vito.cobblebrain.network

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor

object CobblebrainNetworkingNeoForge {

    fun debug(player: ServerPlayer?, msg: String) {
        if (player != null) {
            player.sendSystemMessage(Component.literal("[DEBUG] $msg"))
        } else {
            println("[DEBUG] $msg")
        }
    }

    // SERVER → CLIENT
    fun sendToPlayer(player: ServerPlayer, prompt: String) {
        debug(player, "ENVIANDO PACOTE: ${prompt.take(50)}")
        PacketDistributor.sendToPlayer(
            player,
            CobblebrainPayloads.PromptPayload(prompt)
        )

    }

    // CLIENT → SERVER
    fun sendToServer(response: String) {
        PacketDistributor.sendToServer(
            CobblebrainPayloads.AIResponsePayload(response)
        )
    }
}