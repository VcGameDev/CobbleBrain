package vito.cobblebrain.social

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokeball.PokemonCatchRateEvent
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.MinecraftServer
import vito.cobblebrain.config.SyncedConfig
import vito.cobblebrain.network.CobblebrainPayloads

object DialogueSystemFabric {
    fun register() {
        // Player JOIN
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player: ServerPlayer = handler.player
            DialogueSystem.onPlayerJoin(player)
        }

        // CHAT
        ServerMessageEvents.CHAT_MESSAGE.register { message, sender, _ ->
            val rawContent = message.signedContent()
            DialogueSystem.onChat(sender, rawContent)
        }

        // DAMAGE (player + pokemon)
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, amount, newHealth, absorbed ->
            vito.cobblebrain.engine.StoryListenerManager.onEntityDamaged(entity, source.entity, amount)
            DialogueSystem.onDamage(
                entity,
                source,
                amount,
                newHealth
            )
        }

        // INTERACT ENTITY
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            if (!world.isClientSide && hand == net.minecraft.world.InteractionHand.MAIN_HAND && player is ServerPlayer) {
                vito.cobblebrain.engine.StoryListenerManager.onEntityInteract(player, entity)
            }
            net.minecraft.world.InteractionResult.PASS
        }

        // SERVER TICK
        ServerTickEvents.END_SERVER_TICK.register { server: MinecraftServer ->
            DialogueSystem.onServerTick(server)
            vito.cobblebrain.engine.StoryListenerManager.onServerTick()
        }

        // connects to network
        DialogueSystem.sendToPlayer = { player, prompt ->
            ServerPlayNetworking.send(
                player,
                CobblebrainPayloads.PromptPayload(prompt)
            )
        }

        DialogueSystem.sendToPlayerSummary = { player, contextData ->
            ServerPlayNetworking.send(
                player,
                CobblebrainPayloads.SummaryPromptPayload(contextData)
            )
        }

        DialogueSystem.sendPersonalityList = { player, dataJson ->
            ServerPlayNetworking.send(
                player,
                CobblebrainPayloads.PersonalityListPayload(dataJson)
            )
        }

        DialogueSystem.syncQuests = { player ->
            vito.cobblebrain.network.CobblebrainNetworkingFabric.sendQuests(player)
        }

        // Battle start
        CobblemonEvents.BATTLE_STARTED_POST.subscribe { event ->
            DialogueSystem.onBattleStarted(event)
        }

        // Pokemon sent
        CobblemonEvents.POKEMON_SENT_POST.subscribe { event ->
            DialogueSystem.onPokemonSent(event)
        }

        // Capture
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

        // Flee
        CobblemonEvents.BATTLE_FLED.subscribe { event ->
            DialogueSystem.onBattleFled(event)
        }

        // Victory
        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            DialogueSystem.onBattleVictory(event)
            event.battle.players.forEach { p ->
                vito.cobblebrain.engine.StoryListenerManager.onBattleVictory(p)
            }
        }

        // Damage - handles damage triggers
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, amount ->
            val attacker = source.entity
            vito.cobblebrain.engine.StoryListenerManager.onEntityDamaged(entity, attacker, amount)
            true
        }

        // Death - handles Pokémon fainted, player kills and Pokémon kills
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            val killer = source.entity
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
                            cause = source.msgId,
                            timestamp = now
                        )
                    )
                }
                // Player-killed a Pokémon (existing behaviour)
                val playerKiller = killer as? ServerPlayer ?: return@register
                DialogueSystem.onPokemonDeath(entity, playerKiller)
                return@register
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
                val ownerUuid = killer.pokemon.getOwnerUUID() ?: return@register
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

        // Block break (for treasure quests)
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register { level, player, pos, state, blockEntity ->
            if (player is ServerPlayer) {
                DialogueSystem.onBlockBreak(player, pos, state)
            }
        }
    }
}