package vito.cobblebrain

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
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
    @Suppress("MemberVisibilityCanBePrivate", "unused")
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
        val legacyPasta = File("cobblebrain-ai")
        val pasta = File("cobblebrain")
        if (!pasta.exists() && legacyPasta.exists() && legacyPasta.isDirectory) {
            legacyPasta.renameTo(pasta)
        }
        if (!pasta.exists()) {
            pasta.mkdirs()
        }

        println("o mod cobblebrain carregou")
        CommandTickHandlerFabric.registerTickHandler()

        vito.cobblebrain.sensors.PokemonCommands.sendCooldowns = { player, b, r, s, d ->
            CobblebrainNetworkingFabric.sendCooldowns(player, b, r, s, d)
        }

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

        PayloadTypeRegistry.playS2C().register(
            CobblebrainPayloads.SummaryPromptPayload.TYPE,
            CobblebrainPayloads.SummaryPromptPayload.CODEC
        )

        PayloadTypeRegistry.playS2C().register(
            CobblebrainPayloads.BackgroundPromptPayload.TYPE,
            CobblebrainPayloads.BackgroundPromptPayload.CODEC
        )

        PayloadTypeRegistry.playS2C().register(
            CobblebrainPayloads.SyncCooldownsPayload.TYPE,
            CobblebrainPayloads.SyncCooldownsPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.ActionPayload.TYPE,
            CobblebrainPayloads.ActionPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.AIResponsePayload.TYPE,
            CobblebrainPayloads.AIResponsePayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.BackgroundResponsePayload.TYPE,
            CobblebrainPayloads.BackgroundResponsePayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.RequestSummaryPayload.TYPE,
            CobblebrainPayloads.RequestSummaryPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.RequestPromptWithMemoryPayload.TYPE,
            CobblebrainPayloads.RequestPromptWithMemoryPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.PlayerNicknamePayload.TYPE,
            CobblebrainPayloads.PlayerNicknamePayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.VoiceInputPayload.TYPE,
            CobblebrainPayloads.VoiceInputPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.OfflineSettingsPayload.TYPE,
            CobblebrainPayloads.OfflineSettingsPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.PingPayload.TYPE,
            CobblebrainPayloads.PingPayload.CODEC
        )

        // PERSONALITY EDITOR PAYLOADS
        PayloadTypeRegistry.playS2C().register(
            CobblebrainPayloads.PersonalityListPayload.TYPE,
            CobblebrainPayloads.PersonalityListPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.RequestPersonalityListPayload.TYPE,
            CobblebrainPayloads.RequestPersonalityListPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.SavePersonalityPayload.TYPE,
            CobblebrainPayloads.SavePersonalityPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.DeletePersonalityPayload.TYPE,
            CobblebrainPayloads.DeletePersonalityPayload.CODEC
        )

        // AI DIALOGUE PAYLOADS
        PayloadTypeRegistry.playS2C().register(
            CobblebrainPayloads.AIDialogueBoxPayload.TYPE,
            CobblebrainPayloads.AIDialogueBoxPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.AdvanceAIDialoguePayload.TYPE,
            CobblebrainPayloads.AdvanceAIDialoguePayload.CODEC
        )

        // ENTITY TEXTURE PAYLOADS
        PayloadTypeRegistry.playS2C().register(
            CobblebrainPayloads.SetEntityTexturePayload.TYPE,
            CobblebrainPayloads.SetEntityTexturePayload.CODEC
        )

        PayloadTypeRegistry.playS2C().register(
            CobblebrainPayloads.ClearEntityTexturePayload.TYPE,
            CobblebrainPayloads.ClearEntityTexturePayload.CODEC
        )

        // STORY DEBUG PAYLOADS
        PayloadTypeRegistry.playS2C().register(
            CobblebrainPayloads.StoryDebugSyncPayload.TYPE,
            CobblebrainPayloads.StoryDebugSyncPayload.CODEC
        )

        PayloadTypeRegistry.playS2C().register(
            CobblebrainPayloads.StorySessionStateSyncPayload.TYPE,
            CobblebrainPayloads.StorySessionStateSyncPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.StoryControlRequestPayload.TYPE,
            CobblebrainPayloads.StoryControlRequestPayload.CODEC
        )

        // KEY INPUT & QTE PAYLOADS
        PayloadTypeRegistry.playS2C().register(
            CobblebrainPayloads.StartKeyInputPayload.TYPE,
            CobblebrainPayloads.StartKeyInputPayload.CODEC
        )

        PayloadTypeRegistry.playS2C().register(
            CobblebrainPayloads.CancelKeyInputPayload.TYPE,
            CobblebrainPayloads.CancelKeyInputPayload.CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            CobblebrainPayloads.KeyInputResultPayload.TYPE,
            CobblebrainPayloads.KeyInputResultPayload.CODEC
        )

        // registra handlers de networking
        vito.cobblebrain.server.CobblebrainServerHandlerFabric.register()

        DialogueSystem.sendAIDialogueBoxToPlayer = { player, payload ->
            ServerPlayNetworking.send(player, payload)
        }

        DialogueSystem.sendSetEntityTexture = { player, payload ->
            ServerPlayNetworking.send(player, payload)
        }

        DialogueSystem.sendClearEntityTexture = { player, payload ->
            ServerPlayNetworking.send(player, payload)
        }

        vito.cobblebrain.engine.StoryDebugger.sendDebugSync = { player, payload ->
            ServerPlayNetworking.send(player, payload)
        }

        vito.cobblebrain.engine.StoryDebugger.sendSessionStateSync = { player, payload ->
            ServerPlayNetworking.send(player, payload)
        }

        vito.cobblebrain.engine.StoryExecutor.sendStartKeyInput = { player, payload ->
            ServerPlayNetworking.send(player, payload)
        }

        vito.cobblebrain.engine.StoryExecutor.sendCancelKeyInput = { player, payload ->
            ServerPlayNetworking.send(player, payload)
        }

        // Aqui registramos o comando
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
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
            vito.cobblebrain.social.PingManager.init(server)
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            val player = handler.player
            CobblebrainNetworkingFabric.sendConfig(player)
            DialogueSystem.validateQuestGiversOnPlayerJoin(server, player)

            // Sync cooldowns for player
            vito.cobblebrain.sensors.PokemonCommands.syncCooldowns(player)

            if (!CobblebrainWorldSave.hasReceivedGuide(player)) {
                giveCobblebrainGuide(player)
                CobblebrainWorldSave.markGuideReceived(player)
            }
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            vito.cobblebrain.social.OfflinePlayers.removePlayer(handler.player.uuid)
        }

        // limpa quando o servidor para
        ServerLifecycleEvents.SERVER_STOPPED.register {
            currentServer = null
            vito.cobblebrain.social.DiskWriteExecutor.shutdown()
        }
    }
}