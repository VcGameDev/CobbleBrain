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

    // CLIENT → SERVER
    fun sendToServer(response: String) {
        PacketDistributor.sendToServer(
            CobblebrainPayloads.AIResponsePayload(response)
        )
    }

    fun sendConfig(player: ServerPlayer) {
        val cfg = ConfigHandler.config

        val payload = CobblebrainPayloads.SyncConfigPayload(
            cfg.outputDialogue,
            cfg.outputActions,
            cfg.outputFriendship,
            cfg.outputMemories,
            cfg.outputApril1,
            cfg.outputQuests,
            cfg.outputPokemonLanguage,
            cfg.maxLongMemory,
            cfg.maxShortMemory
        )

        PacketDistributor.sendToPlayer(player, payload)

        println("CONFIG ENVIADA PRO CLIENT")
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