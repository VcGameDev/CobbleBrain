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
                
                val (typeInfo, explanation) = when(command.action.uppercase()) {
                    "COOK" -> "(FIRE)" to "Can cook food and smelt ores. 5% chance of item turning into charcoal."
                    "GROW" -> "(PLANT)" to "Grows tree saplings and crops."
                    "REPAIR" -> "(METAL)" to "Repairs tools and weapons up to a certain durability threshold."
                    "SHIFT" -> "(GHOST)" to "Player becomes invisible, gains increased speed and jump height, but suffers from high weakness."
                    "FISH" -> "(WATER)" to "Searches nearby waters for fish and other resources."
                    "NIGHTMARE" -> "(DARK)" to "Inflicts fear on nearby creatures, causing them to flee in panic and giving ."
                    "LIGHT" -> "(ELECTRIC)" to "Creates a temporary light source and illuminates dark areas."
                    "SCOUT" -> "(FLYING)" to "Performs aerial reconnaissance and reports nearby structures and entities."
                    "TELEPORT" -> "(PSYCHIC)" to "Teleports the trainer to the currently marked location."
                    "ATTACK" -> "" to "Pokémon attacks any mob close to it; it will stop after killing the target."
                    "PROTECT" -> "" to "Pokémon targets hostile mobs nearest to the player; if none are found, it follows the player."
                    "EAT" -> "" to "Pokémon eat any edible item dropped on the ground. Some foods and berries may grant temporary effects."
                    "BUFF" -> "" to "Pokémon grants the player a positive status effect based on its type."
                    "DEBUFF ENEMY" -> "" to "Pokémon applies a negative status effect to nearby mobs based on its primary type."
                    "SIT" -> "" to "Pokémon stays fixed in place, ignoring other actions."
                    "IDLE" -> "" to "Pokémon cancels all active commands and returns to normal behavior."
                    else -> "" to "Executing command."
                }
                
                val actionName = command.action.uppercase()
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
                    .append(Component.literal("$actionName $typeInfo".trim()).withStyle(net.minecraft.ChatFormatting.BOLD).withStyle(actionColor))
                    .append(Component.literal(": $explanation").withStyle(net.minecraft.ChatFormatting.GRAY))

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
                    player.sendSystemMessage(Component.literal("Command '${command.action}' applied to ${command.pokemonName}."))

                    // Send prompt back (make AI talk) only for individual commands
                    DialogueSystem.sendToPlayer?.let { it(player, "${command.pokemonName}: executing ${command.action}") }
                } ?: run {
                    player.sendSystemMessage(Component.literal("Could not find active Pokémon named ${command.pokemonName}."))
                }
            }
        }
    }

    // Função que processa a resposta da IA
    fun processIaResponse(server: MinecraftServer, player: ServerPlayer, content: String) {
        checkIaResponse(server, player, content)
    }
}