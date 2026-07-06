package vito.cobblebrain.network

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.network.PacketDistributor
import vito.cobblebrain.client.CobblebrainClientCommon
import vito.cobblebrain.config.CobblebrainConfigScreen
import vito.cobblebrain.config.ConfigHandler

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
        // UTIL PRA DEBUG
        //debug(player, "ENVIANDO PACOTE: ${prompt.take(50)}")
        PacketDistributor.sendToPlayer(
            player,
            CobblebrainPayloads.PromptPayload(prompt)
        )
    }

    fun sendSummaryToPlayer(player: ServerPlayer, contextData: String) {
        PacketDistributor.sendToPlayer(
            player,
            CobblebrainPayloads.SummaryPromptPayload(contextData)
        )
    }

    // CLIENT → SERVER
    fun sendToServer(response: String) {
        PacketDistributor.sendToServer(
            CobblebrainPayloads.AIResponsePayload(response)
        )
    }

    fun sendActionToServer(action: String) {
        PacketDistributor.sendToServer(
            CobblebrainPayloads.ActionPayload(action)
        )
    }

    fun sendConfig(player: ServerPlayer) {
        val cfg = ConfigHandler.config

        val payload = CobblebrainPayloads.SyncConfigPayload(
            cfg.useDefaultOutput,
            cfg.outputDialogue,
            cfg.outputActions,
            cfg.outputFriendship,
            cfg.outputMemories,
            cfg.outputApril1,
            cfg.outputQuests,
            cfg.outputPokemonLanguage,
            cfg.needsPokemonTranslator,
            cfg.outputGuaranteedCatch,
            cfg.enableKarma,
            cfg.maxStoredMemories,
            cfg.maxRelevantMemories
        )

        PacketDistributor.sendToPlayer(player, payload)

        println("CONFIG SENT TO CLIENT")
    }

    fun sendQuests(player: ServerPlayer) {
        val quests = vito.cobblebrain.social.CobblebrainWorldSave.getActiveQuests(player)
        val array = com.google.gson.JsonArray()
        quests.forEach { array.add(it) }

        PacketDistributor.sendToPlayer(
            player,
            CobblebrainPayloads.QuestSyncPayload(array.toString())
        )
    }

    fun sendCooldowns(player: ServerPlayer, buff: Long, repair: Long, shift: Long, debuff: Long) {
        PacketDistributor.sendToPlayer(
            player,
            CobblebrainPayloads.SyncCooldownsPayload(buff, repair, shift, debuff)
        )
    }

    fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {

            CobblebrainClientCommon.openConfigScreen = {
                Minecraft.getInstance().setScreen(
                    CobblebrainConfigScreen.create(Minecraft.getInstance().screen)
                )
            }

            ModLoadingContext.get().registerExtensionPoint(
                IConfigScreenFactory::class.java
            ) {
                IConfigScreenFactory { _, parent ->
                    CobblebrainConfigScreen.create(parent)
                }
            }
        }
    }
}
