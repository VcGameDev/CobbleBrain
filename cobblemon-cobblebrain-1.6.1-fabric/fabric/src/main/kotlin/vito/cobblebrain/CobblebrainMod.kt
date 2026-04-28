package vito.cobblebrain

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import vito.cobblebrain.social.DebugPartyCommand
import java.io.File
import net.minecraft.server.MinecraftServer
import vito.cobblebrain.social.CobblebrainWorldSave
import vito.cobblebrain.social.CobblebrainWorldSave.giveCobblebrainGuide
import vito.cobblebrain.social.MobBridge
import vito.cobblebrain.config.ConfigHandler
import vito.cobblebrain.mixin.MobAccessor
import vito.cobblebrain.network.CobblebrainNetworkingFabric
import vito.cobblebrain.network.CobblebrainPayloads
import vito.cobblebrain.sensors.CommandTickHandlerFabric
import vito.cobblebrain.social.ConfigCommands
import vito.cobblebrain.social.DialogueSystem
import vito.cobblebrain.social.DialogueSystemFabric
import vito.cobblebrain.social.PokemonTalkCommand
import vito.cobblebrain.social.WorldEventsSystemFabric


object CobblebrainMod : ModInitializer {
    @Suppress("MemberVisibilityCanBePrivate")
    const val MOD_ID = "cobblebrain"

    // Quando o jogo inicializa
    override fun onInitialize() {
        MobBridge.addGoal = { mob, priority, goal ->
            val accessor = mob as MobAccessor
            accessor.goalSelector.addGoal(priority, goal)
        }

        MobBridge.removeGoal = { mob, goal ->
            val accessor = mob as MobAccessor
            accessor.goalSelector.removeGoal(goal)
        }

        MobBridge.getGoals = { mob ->
            val accessor = mob as MobAccessor
            accessor.goalSelector.availableGoals.map { it.goal }
        }
        DialogueSystemFabric.register()
        WorldEventsSystemFabric.register()
        ConfigHandler.load()
        val pasta = File("cobblebrain-ai")

        // cria a pasta se não existir
        if (!pasta.exists()) {
            pasta.mkdirs()
        }

        println("o mod cobblebrain carregou")
        CommandTickHandlerFabric.registerTickHandler()

        // registra o tipo de payload PROMPT (server → client)
        PayloadTypeRegistry.playS2C().register(
            CobblebrainPayloads.PromptPayload.TYPE,
            CobblebrainPayloads.PromptPayload.CODEC
        )

        PayloadTypeRegistry.playS2C().register(
            CobblebrainPayloads.SyncConfigPayload.TYPE,
            CobblebrainPayloads.SyncConfigPayload.CODEC
        )

        PayloadTypeRegistry.playS2C().register(
            CobblebrainPayloads.QuestSyncPayload.TYPE,
            CobblebrainPayloads.QuestSyncPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.ActionPayload.TYPE,
            CobblebrainPayloads.ActionPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.AIResponsePayload.TYPE,
            CobblebrainPayloads.AIResponsePayload.CODEC
        )

        // registra handlers de networking
        vito.cobblebrain.server.CobblebrainServerHandlerFabric.register()

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
            CobblebrainNetworkingFabric.sendConfig(player)
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