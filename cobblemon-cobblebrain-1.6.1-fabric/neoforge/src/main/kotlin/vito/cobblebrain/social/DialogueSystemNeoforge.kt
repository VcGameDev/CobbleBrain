package vito.cobblebrain.social

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.ServerChatEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import vito.cobblebrain.network.CobblebrainNetworkingNeoForge
import vito.cobblebrain.config.SyncedConfig
import com.cobblemon.mod.common.api.events.pokeball.PokemonCatchRateEvent

class DialogueSystemNeoForge {
    companion object {
        fun register() {
            NeoForge.EVENT_BUS.register(DialogueSystemNeoForge())

            DialogueSystem.sendToPlayer = { player, prompt ->
                CobblebrainNetworkingNeoForge.sendToPlayer(player, prompt)
            }

            DialogueSystem.sendToPlayerBackground = { player, prompt ->
                CobblebrainNetworkingNeoForge.sendBackgroundToPlayer(player, prompt)
            }

            DialogueSystem.sendToPlayerSummary = { player, contextData ->
                CobblebrainNetworkingNeoForge.sendSummaryToPlayer(player, contextData)
            }

            DialogueSystem.sendPersonalityList = { player, dataJson ->
                CobblebrainNetworkingNeoForge.sendPersonalityList(player, dataJson)
            }

            DialogueSystem.syncQuests = { player ->
                CobblebrainNetworkingNeoForge.sendQuests(player)
            }

            CobblemonEvents.BATTLE_STARTED_POST.subscribe {
                DialogueSystem.onBattleStarted(it)
            }

            CobblemonEvents.POKEMON_SENT_POST.subscribe {
                DialogueSystem.onPokemonSent(it)
            }

            CobblemonEvents.BATTLE_FLED.subscribe {
                DialogueSystem.onBattleFled(it)
            }

            CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
                DialogueSystem.onBattleVictory(event)
                event.battle.players.forEach { p ->
                    vito.cobblebrain.engine.StoryListenerManager.onBattleVictory(p)
                }
            }

            CobblemonEvents.POKEMON_CATCH_RATE.subscribe { event: PokemonCatchRateEvent ->
                val thrower = event.thrower
                val player = thrower as? ServerPlayer ?: return@subscribe
                if (OfflinePlayers.isOffline(player.uuid)) return@subscribe
                val target = event.pokemonEntity
                val playerUuid = player.uuid.toString()
                
                if (!SyncedConfig.outputGuaranteedCatch) return@subscribe

                if (target.tags.contains("cobblebrain:guaranteed_$playerUuid")) {
                    event.catchRate = 9999.0f
                }
            }
        }
    }

    @SubscribeEvent
    fun onJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        DialogueSystem.onPlayerJoin(player)
    }

    @SubscribeEvent
    fun onLeave(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        OfflinePlayers.removePlayer(player.uuid)
    }

    @SubscribeEvent
    fun onChat(event: ServerChatEvent) {
        val player = event.player
        val message = event.message.string
        DialogueSystem.onChat(player, message)
    }

    @SubscribeEvent
    fun onDamage(event: LivingDamageEvent.Post) {
        val entity = event.entity
        val killer = event.source.entity
        vito.cobblebrain.engine.StoryListenerManager.onEntityDamaged(entity, killer, event.newDamage)
        DialogueSystem.onDamage(
            entity,
            event.source,
            event.newDamage,
            entity.health
        )
    }

    @SubscribeEvent
    fun onDeath(event: LivingDeathEvent) {
        val entity = event.entity
        val killer = event.source.entity
        val now = System.currentTimeMillis()
        vito.cobblebrain.engine.StoryListenerManager.onEntityDied(entity, killer)

        // 1. Pokémon Fainted: a player-owned Pokémon died
        if (entity is PokemonEntity) {
            val ownerUuid = entity.pokemon.getOwnerUUID()
            if (ownerUuid != null) {
                val pokemonName = entity.pokemon.nickname?.string ?: entity.pokemon.species.name
                RecentEventsSystem.recordEvent(
                    entity.pokemon.uuid,
                    RecentEventsSystem.FaintEvent(
                        pokemonName = pokemonName,
                        cause = event.source.msgId,
                        timestamp = now
                    )
                )
            }
            // Player-killed a Pokémon (existing behaviour)
            val playerKiller = killer as? ServerPlayer ?: return
            DialogueSystem.onPokemonDeath(entity, playerKiller)
            return
        }

        // 2. Player Kills: player killed a non-Pokémon entity
        if (killer is ServerPlayer) {
            val entityTypeName = entity.type.descriptionId.substringAfterLast(".")
            val ativos = PokemonQuery.findActivePokemon(killer)
            ativos.forEach { p ->
                RecentEventsSystem.recordEvent(
                    p.uuid,
                    RecentEventsSystem.PlayerKillEvent(
                        entityType = entityTypeName,
                        timestamp = now
                    )
                )
            }
        }

        // 3. Pokémon Kills: a player's Pokémon killed some entity
        if (killer is PokemonEntity) {
            val ownerUuid = killer.pokemon.getOwnerUUID() ?: return
            val pokemonName = killer.pokemon.nickname?.string ?: killer.pokemon.species.name
            val entityTypeName = entity.type.descriptionId.substringAfterLast(".")
            val trigger = RecentEventsSystem.commandSources[killer.uuid] ?: RecentEventsSystem.CommandSource.HUD
            RecentEventsSystem.recordEvent(
                killer.pokemon.uuid,
                RecentEventsSystem.PokemonKillEvent(
                    pokemonName = pokemonName,
                    entityType = entityTypeName,
                    trigger = if (trigger == RecentEventsSystem.CommandSource.AI) "AI" else "Action HUD",
                    timestamp = now
                )
            )
        }
    }

    @SubscribeEvent
    fun onTick(event: ServerTickEvent.Post) {
        DialogueSystem.onServerTick(event.server)
        vito.cobblebrain.engine.StoryListenerManager.onServerTick()
    }

    @SubscribeEvent
    fun onBlockBreak(event: net.neoforged.neoforge.event.level.BlockEvent.BreakEvent) {
        val player = event.player as? ServerPlayer ?: return
        DialogueSystem.onBlockBreak(player, event.pos, event.state)
    }
}