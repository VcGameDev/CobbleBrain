package vito.cobblebrain.server

import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import vito.cobblebrain.sensors.CommandState
import vito.cobblebrain.sensors.PokemonCommand
import vito.cobblebrain.sensors.parseCommand
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.MinecraftServer
import vito.cobblebrain.social.DialogueSystem
import vito.cobblebrain.social.DialogueSystem.checkIaResponse
import vito.cobblebrain.social.PokemonQuery

object CobblebrainServerHandler {
    // Função que processa o comando recebido de um player
    fun processAction(player: ServerPlayer, action: String) {
        val command: PokemonCommand? = parseCommand(action)
        if (command != null) {
            val ativos: List<Pokemon> = PokemonQuery.findActivePokemon(player)
            
            if (command.pokemonName.equals("ALL", ignoreCase = true)) {
                // Aplica para TODOS os pokémons ativos
                ativos.forEach { poke ->
                    poke.entity?.let { entity ->
                        CommandState.activeCommands[entity.uuid] = command.action
                    }
                }
                
                val actionName = command.action.uppercase()
                
                val typeInfo = when(actionName) {
                    "COOK" -> " (FIRE)"
                    "GROW" -> " (PLANT)"
                    "REPAIR" -> " (METAL)"
                    "SHIFT" -> " (GHOST)"
                    "FISH" -> " (WATER)"
                    "NIGHTMARE" -> " (DARK)"
                    "LIGHT" -> " (ELECTRIC)"
                    "SCOUT" -> " (FLYING)"
                    "TELEPORT" -> " (PSYCHIC)"
                    else -> ""
                }
                
                val actionKey = command.action.lowercase().replace(" ", "_")
                val actionTransName = Component.translatable("cobblebrain.action.$actionKey")
                val explanationTrans = Component.translatable("cobblebrain.action.desc.$actionKey")
                
                val actionColor = when(actionName) {
                    "COOK" -> net.minecraft.ChatFormatting.GOLD
                    "GROW" -> net.minecraft.ChatFormatting.GREEN
                    "REPAIR" -> net.minecraft.ChatFormatting.DARK_GRAY
                    "SHIFT" -> net.minecraft.ChatFormatting.DARK_PURPLE
                    "FISH" -> net.minecraft.ChatFormatting.BLUE
                    "NIGHTMARE" -> net.minecraft.ChatFormatting.DARK_RED
                    "LIGHT" -> net.minecraft.ChatFormatting.YELLOW
                    "SCOUT" -> net.minecraft.ChatFormatting.AQUA
                    "TELEPORT" -> net.minecraft.ChatFormatting.LIGHT_PURPLE
                    else -> net.minecraft.ChatFormatting.WHITE
                }

                val message = Component.literal("All Pokémon -> ")
                    .withStyle(net.minecraft.ChatFormatting.WHITE)
                    .append(actionTransName.withStyle(net.minecraft.ChatFormatting.BOLD).withStyle(actionColor))
                    .append(Component.literal(typeInfo).withStyle(net.minecraft.ChatFormatting.BOLD).withStyle(actionColor))
                    .append(Component.literal(": ").withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append(explanationTrans.withStyle(net.minecraft.ChatFormatting.GRAY))

                player.sendSystemMessage(message)
            } else {
                // Original behavior: search by specific name
                val pokemon = ativos.find { poke ->
                    poke.nickname?.string.equals(command.pokemonName, ignoreCase = true) ||
                            poke.species.name.equals(command.pokemonName, ignoreCase = true) ||
                            poke.species.resourceIdentifier.path.equals(command.pokemonName, ignoreCase = true)
                }

                pokemon?.entity?.let { entity ->
                    CommandState.activeCommands[entity.uuid] = command.action
                    player.sendSystemMessage(Component.translatable("cobblebrain.feedback.command_applied", command.action, command.pokemonName))

                    // Send prompt back (make AI talk) only for individual commands
                    DialogueSystem.sendToPlayer?.let { it(player, "${command.pokemonName}: executing ${command.action}") }
                } ?: run {
                    player.sendSystemMessage(Component.translatable("cobblebrain.feedback.pokemon_not_found", command.pokemonName))
                }
            }
        }
    }

    // Função que processa a resposta da IA
    fun processIaResponse(server: MinecraftServer, player: ServerPlayer, content: String) {
        checkIaResponse(server, player, content)
    }
}