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
import com.cobblemon.mod.common.battles.BattleRegistry

class DialogueSystemNeoForge {
    companion object {
        fun register() {
            NeoForge.EVENT_BUS.register(DialogueSystemNeoForge())

            DialogueSystem.sendToPlayer = { player, prompt ->
                CobblebrainNetworkingNeoForge.sendToPlayer(player, prompt)
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

            CobblemonEvents.BATTLE_VICTORY.subscribe {
                DialogueSystem.onBattleVictory(it)
            }

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
                    println("[CobbleBrain] Guaranteed capture triggered for $playerUuid (NeoForge)")
                }
            }
        }
    }

    @SubscribeEvent
    fun onJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        DialogueSystem.onPlayerJoin(player)
        println("JOIN EVENT DISPAROU")
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
        if (entity !is PokemonEntity) return

        val killer = event.source.entity as? ServerPlayer ?: return
        DialogueSystem.onPokemonDeath(entity, killer)
    }

    @SubscribeEvent
    fun onTick(event: ServerTickEvent.Post) {
        DialogueSystem.onServerTick(event.server)
    }
}