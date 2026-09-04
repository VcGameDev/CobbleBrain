package vito.cobblebrain

import net.minecraft.server.level.ServerPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import vito.cobblebrain.config.ConfigHandler
import vito.cobblebrain.mixin.MobAccessor
import vito.cobblebrain.social.ConfigCommands
import vito.cobblebrain.social.DialogueSystem
import vito.cobblebrain.social.PokemonTalkCommand
import net.neoforged.fml.common.Mod
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import vito.cobblebrain.client.CobblebrainClientRuntimeNeoForge
import vito.cobblebrain.config.ClientConfigHandler
import vito.cobblebrain.network.CobblebrainNetworkingNeoForge
import vito.cobblebrain.network.CobblebrainNetworkingNeoForge.onClientSetup
import vito.cobblebrain.sensors.CommandTickHandlerNeoForge
import vito.cobblebrain.server.CobblebrainPayloadRegistrarNeoForge
import vito.cobblebrain.social.CobblebrainWorldSave
import vito.cobblebrain.social.CobblebrainWorldSave.giveCobblebrainGuide
import vito.cobblebrain.social.DialogueSystemNeoForge
import vito.cobblebrain.social.MobBridge
import vito.cobblebrain.social.WorldEventsSystemNeoForge
import java.io.File

@Mod("cobblebrain")
class CobblebrainNeoForge(modEventBus: IEventBus) {
    init {
        println("o mod cobblebrain carregou (NeoForge)")
        modEventBus.addListener(CobblebrainPayloadRegistrarNeoForge::register)

        vito.cobblebrain.sensors.PokemonCommands.sendCooldowns = { player, b, r, s, d ->
            CobblebrainNetworkingNeoForge.sendCooldowns(player, b, r, s, d)
        }

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(::onClientSetup)
        }
        ClientConfigHandler.load()
        ClientOnlySetup.register(modEventBus)
        CobblebrainClientRuntimeNeoForge.init()

        // ===== MOB BRIDGE =====
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

        // ===== SISTEMAS =====
        DialogueSystemNeoForge.register()
        WorldEventsSystemNeoForge.register()
        CommandTickHandlerNeoForge.registerTickHandler()

        // ===== NETWORKING HOOK =====
        DialogueSystem.sendToPlayer = { player, prompt ->
            CobblebrainNetworkingNeoForge.sendToPlayer(player, prompt)
        }

        DialogueSystem.sendToPlayerBackground = { player, prompt ->
            CobblebrainNetworkingNeoForge.sendBackgroundToPlayer(player, prompt)
        }

        DialogueSystem.sendToPlayerSummary = { player, contextData ->
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player,
                vito.cobblebrain.network.CobblebrainPayloads.SummaryPromptPayload(contextData)
            )
        }

        DialogueSystem.sendAIDialogueBoxToPlayer = { player, payload ->
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload)
        }

        DialogueSystem.sendSetEntityTexture = { player, payload ->
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload)
        }

        DialogueSystem.sendClearEntityTexture = { player, payload ->
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload)
        }

        vito.cobblebrain.engine.StoryDebugger.sendDebugSync = { player, payload ->
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload)
        }

        vito.cobblebrain.engine.StoryDebugger.sendSessionStateSync = { player, payload ->
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload)
        }

        vito.cobblebrain.engine.StoryExecutor.sendStartKeyInput = { player, payload ->
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload)
        }

        vito.cobblebrain.engine.StoryExecutor.sendCancelKeyInput = { player, payload ->
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload)
        }

        // ===== CONFIG =====
        ConfigHandler.load()

        val legacyPasta = File("cobblebrain-ai")
        val pasta = File("cobblebrain")
        if (!pasta.exists() && legacyPasta.exists() && legacyPasta.isDirectory) {
            legacyPasta.renameTo(pasta)
        }
        if (!pasta.exists()) pasta.mkdirs()

        // ===== EVENT BUS =====
        NeoForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        val dispatcher = event.dispatcher

        PokemonTalkCommand.register(dispatcher)
        ConfigCommands.register(dispatcher)
    }

    @SubscribeEvent
    fun onServerStart(event: ServerStartedEvent) {
        val server = event.server
        currentServer = server

        CobblebrainWorldSave.init(server)
        vito.cobblebrain.social.PingManager.init(server)
        ConfigHandler.load()
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val server = player.server

        DialogueSystem.validateQuestGiversOnPlayerJoin(server, player)
        CobblebrainNetworkingNeoForge.sendConfig(player)

        // Sync cooldowns for player
        vito.cobblebrain.sensors.PokemonCommands.syncCooldowns(player)

        if (!CobblebrainWorldSave.hasReceivedGuide(player)) {
            giveCobblebrainGuide(player)
            CobblebrainWorldSave.markGuideReceived(player)
        }
    }

    @SubscribeEvent
    fun onServerStop(event: ServerStoppingEvent) {
        currentServer = null
        vito.cobblebrain.social.DiskWriteExecutor.shutdown()
    }
}