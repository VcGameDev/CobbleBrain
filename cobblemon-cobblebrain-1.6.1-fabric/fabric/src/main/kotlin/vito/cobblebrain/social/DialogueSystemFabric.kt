package vito.cobblebrain.social

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokeball.PokemonCatchRateEvent
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import com.cobblemon.mod.common.battles.BattleRegistry
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

            DialogueSystem.onDamage(
                entity,
                source,
                amount,
                newHealth
            )
        }

        // SERVER TICK
        ServerTickEvents.END_SERVER_TICK.register { server: MinecraftServer ->
            DialogueSystem.onServerTick(server)
        }

        // conecta networking
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
            val player = event.thrower as? ServerPlayer ?: return@subscribe
            val target = event.pokemonEntity
            val playerUuid = player.uuid.toString()
            
            // Bloqueia se o jogador estiver em batalha ou o sistema estiver desligado
            val inBattle = BattleRegistry.getBattleByParticipatingPlayerId(player.uuid) != null
            if (inBattle || !SyncedConfig.outputGuaranteedCatch) return@subscribe

            if (target.tags.contains("cobblebrain:guaranteed_$playerUuid")) {
                event.catchRate = 1000.0f
                target.removeTag("cobblebrain:guaranteed_$playerUuid")
                println("[CobbleBrain] Guaranteed capture triggered for $playerUuid")
            }
        }

        // Flee
        CobblemonEvents.BATTLE_FLED.subscribe { event ->
            DialogueSystem.onBattleFled(event)
        }

        // Victory
        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            DialogueSystem.onBattleVictory(event)
        }

        // Death
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            if (entity !is PokemonEntity) return@register
            val killer = source.entity as? ServerPlayer ?: return@register

            DialogueSystem.onPokemonDeath(entity, killer)
        }
    }
}