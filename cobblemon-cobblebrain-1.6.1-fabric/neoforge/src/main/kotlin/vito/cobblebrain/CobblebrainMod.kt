package vito.cobblebrain

import vito.cobblebrain.social.DebugPartyCommand
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

        // ===== CONFIG =====
        ConfigHandler.load()

        val pasta = File("cobblebrain-ai")
        if (!pasta.exists()) pasta.mkdirs()

        // ===== EVENT BUS =====
        NeoForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        val dispatcher = event.dispatcher

        DebugPartyCommand.register(dispatcher)
        PokemonTalkCommand.register(dispatcher)
        ConfigCommands.register(dispatcher)
    }

    @SubscribeEvent
    fun onServerStart(event: ServerStartedEvent) {
        val server = event.server
        currentServer = server

        CobblebrainWorldSave.init(server)
        ConfigHandler.load()
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val server = player.server

        DialogueSystem.validateQuestGiversOnPlayerJoin(server, player)
        CobblebrainNetworkingNeoForge.sendConfig(player)

        if (!CobblebrainWorldSave.hasReceivedGuide(player)) {
            giveCobblebrainGuide(player)
            CobblebrainWorldSave.markGuideReceived(player)
        }
    }

    @SubscribeEvent
    fun onServerStop(event: ServerStoppingEvent) {
        currentServer = null
    }
}