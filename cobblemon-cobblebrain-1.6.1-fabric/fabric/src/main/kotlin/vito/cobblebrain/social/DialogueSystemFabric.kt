package vito.cobblebrain.social

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.MinecraftServer
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