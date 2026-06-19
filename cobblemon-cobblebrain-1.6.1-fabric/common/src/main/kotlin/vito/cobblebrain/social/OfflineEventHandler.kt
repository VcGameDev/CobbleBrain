package vito.cobblebrain.social

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import vito.cobblebrain.sensors.WorldContext
import vito.cobblebrain.sensors.collectWorldContext
import java.util.UUID
import vito.cobblebrain.social.OfflineDialogueManager.FeelingContext
import vito.cobblebrain.social.OfflineDialogueManager.determineFeelingContext
import vito.cobblebrain.social.OfflineDialogueManager.getNarratorMessage

object OfflineEventHandler {
    private const val COOLDOWN = 700L
    private val lastReactionTick = mutableMapOf<UUID, Long>()
    private val lastContext = mutableMapOf<UUID, FeelingContext>()

    fun applyContextChance(
        context: FeelingContext
    ): FeelingContext {

        val chance = when (context) {

            FeelingContext.HOSTILE_MOBS -> 1.0
            FeelingContext.LOW_HP -> 1.0
            FeelingContext.HUNGRY -> 0.50
            FeelingContext.BERRY -> 0.35
            FeelingContext.ITEMS -> 0.20

            FeelingContext.THUNDERSTORM -> 0.40
            FeelingContext.SNOW -> 0.20
            FeelingContext.RAIN -> 0.10
            FeelingContext.NIGHT -> 0.05
            FeelingContext.POKEMON_GROUP -> 0.15
            FeelingContext.DEFAULT -> 1.0
        }

        return if (
            Math.random() < chance
        ) {
            context
        } else {
            FeelingContext.DEFAULT
        }
    }

    fun sendNarratorMessage(
        player: ServerPlayer,
        activePokemon: List<Pokemon>,
        currentContext: FeelingContext
    ) {

        val previousContext =
            lastContext.getOrDefault(
                player.uuid,
                FeelingContext.DEFAULT
            )

        if (
            currentContext == previousContext
        ) {
            return
        }

        lastContext[player.uuid] =
            currentContext

        if (
            currentContext == FeelingContext.DEFAULT
        ) {
            return
        }

        val narratorMessage =
            getNarratorMessage(
                activePokemon.size,
                currentContext,
                activePokemon.first().nickname?.string
                    ?: activePokemon.first().species.name
            )

        if (narratorMessage.isNotBlank()) {

            player.sendSystemMessage(
                Component.literal(narratorMessage)
                    .withStyle(ChatFormatting.GRAY)
            )
        }
    }

    fun tick(player: ServerPlayer) {

        if (!OfflinePlayers.offlineMode.getOrDefault(player.uuid, false))
            return

        val currentTick = player.server.tickCount.toLong()

        val lastTick = lastReactionTick[player.uuid]

        if (
            lastTick != null &&
            currentTick - lastTick < COOLDOWN
        ) {
            return
        }

        val activePokemon = PokemonQuery.findActivePokemon(player)

        if (activePokemon.isEmpty())
            return

        val context = collectWorldContext(player)
        val speaker =
            activePokemon.random()

        val rawContext =
            determineFeelingContext(
                speaker,
                context
            )

        val currentContext =
            applyContextChance(
                rawContext
            )

        sendNarratorMessage(
            player,
            activePokemon,
            currentContext,
        )
        AmbientReactionManager.triggerReaction(player, activePokemon, currentContext)

        sendReaction(
            player,
            speaker,
            context
        )

        lastReactionTick[player.uuid] =
            currentTick
    }

    private fun sendReaction(
        player: ServerPlayer,
        pokemon: Pokemon,
        context: WorldContext
    ) {

        val displayName =
            pokemon.nickname?.string
                ?: pokemon.species.name

        val text =
            "$displayName: ${
                OfflineDialogueManager.generateOfflineResponse(
                    pokemon,
                    context
                )
            }"

        DialogueSystem.scheduledMessages
            .getOrPut(player.uuid) { mutableListOf() }
            .add(
                DialogueSystem.ScheduledMessage(
                    player = player,
                    text = text,
                    sendAtTick = player.server.tickCount.toLong(),
                    speaker = pokemon,
                    pitchMod = 0f
                )
            )
    }
}