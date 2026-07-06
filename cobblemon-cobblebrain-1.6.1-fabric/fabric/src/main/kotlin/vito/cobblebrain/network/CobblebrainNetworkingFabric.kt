package vito.cobblebrain.network

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import vito.cobblebrain.config.ConfigHandler.config

object CobblebrainNetworkingFabric {
    fun debug(player: ServerPlayer?, msg: String) {
        if (player != null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[DEBUG] $msg"))
        } else {
            println("[DEBUG] $msg")
        }
    }

    fun sendToPlayer(player: ServerPlayer, prompt: String) {
        ServerPlayNetworking.send(
            player,
            CobblebrainPayloads.PromptPayload(prompt)
        )
    }

    fun sendSummaryToPlayer(player: ServerPlayer, contextData: String) {
        ServerPlayNetworking.send(
            player,
            CobblebrainPayloads.SummaryPromptPayload(contextData)
        )
    }

    fun sendActionToServer(action: String) {
        ClientPlayNetworking.send(CobblebrainPayloads.ActionPayload(action))
    }

    fun sendConfig(player: ServerPlayer) {
        val payload = CobblebrainPayloads.SyncConfigPayload(
            config.useDefaultOutput,
            config.outputDialogue,
            config.outputActions,
            config.outputFriendship,
            config.outputMemories,
            config.outputApril1,
            config.outputQuests,
            config.outputPokemonLanguage,
            config.needsPokemonTranslator,
            config.outputGuaranteedCatch,
            config.enableKarma,
            config.maxStoredMemories,
            config.maxRelevantMemories
        )

        ServerPlayNetworking.send(player, payload)
    }

    fun sendQuests(player: ServerPlayer) {
        val quests = vito.cobblebrain.social.CobblebrainWorldSave.getActiveQuests(player)
        val array = com.google.gson.JsonArray()
        quests.forEach { array.add(it) }

        ServerPlayNetworking.send(
            player,
            CobblebrainPayloads.QuestSyncPayload(array.toString())
        )
    }

    fun sendCooldowns(player: ServerPlayer, buff: Long, repair: Long, shift: Long, debuff: Long) {
        ServerPlayNetworking.send(
            player,
            CobblebrainPayloads.SyncCooldownsPayload(buff, repair, shift, debuff)
        )
    }
}
