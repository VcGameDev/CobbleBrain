package vito.cobblebrain

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import vito.cobblebrain.social.DebugPartyCommand
import vito.cobblebrain.social.DialogueSystem.register
import java.io.File
import net.minecraft.server.MinecraftServer
import vito.cobblebrain.client.CobblebrainClientHandler
import vito.cobblebrain.client.social.CobblebrainWorldSave
import vito.cobblebrain.client.social.CobblebrainWorldSave.giveCobblebrainGuide
import vito.cobblebrain.config.ClientConfigHandler
import vito.cobblebrain.config.ConfigHandler
import vito.cobblebrain.sensors.registerTickHandler
import vito.cobblebrain.social.ConfigCommands
import vito.cobblebrain.social.DialogueSystem
import vito.cobblebrain.social.PokemonTalkCommand
import vito.cobblebrain.social.WorldEventsSystem


object CobblebrainMod : ModInitializer {
    @Suppress("MemberVisibilityCanBePrivate")
    const val MOD_ID = "cobblebrain"

    // Quando o jogo inicializa
    override fun onInitialize() {
        ConfigHandler.load()
        ClientConfigHandler.load()
        val pasta = File("cobblebrain-ai")

        // cria a pasta se não existir
        if (!pasta.exists()) {
            pasta.mkdirs()
        }

        println("o mod cobblebrain carregou")
        register()
        WorldEventsSystem.register()
        registerTickHandler()

        // registra o tipo de payload PROMPT (server → client)
        PayloadTypeRegistry.playS2C().register(
            CobblebrainClientHandler.PromptPayload.TYPE,
            CobblebrainClientHandler.PromptPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainClientHandler.ActionPayload.TYPE,
            CobblebrainClientHandler.ActionPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainClientHandler.AIResponsePayload.TYPE,
            CobblebrainClientHandler.AIResponsePayload.CODEC
        )

        // registra handlers de networking
        vito.cobblebrain.server.CobblebrainServerHandler.registerReceivers()

        // Aqui registramos o comando
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            DebugPartyCommand.register(dispatcher)
            PokemonTalkCommand.register(dispatcher)
            ConfigCommands.register(dispatcher)
        }

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { _, _, _ ->
            ConfigHandler.load()

        }

        ServerLifecycleEvents.SERVER_STARTED.register { server: MinecraftServer ->
            currentServer = server
            // remover se der problemas
            CobblebrainWorldSave.init(server)
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            val player = handler.player
            DialogueSystem.validateQuestGiversOnPlayerJoin(server, player)

            if (!CobblebrainWorldSave.hasReceivedGuide(player)) {
                giveCobblebrainGuide(player)
                CobblebrainWorldSave.markGuideReceived(player)
            }
        }

        // limpa quando o servidor para
        ServerLifecycleEvents.SERVER_STOPPED.register {
            currentServer = null
        }
    }
}