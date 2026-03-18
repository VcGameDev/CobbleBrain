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
            val pokemon = ativos.find { poke ->
                poke.nickname?.string.equals(command.pokemonName, ignoreCase = true) ||
                        poke.species.name.equals(command.pokemonName, ignoreCase = true) ||
                        poke.species.resourceIdentifier.path.equals(command.pokemonName, ignoreCase = true)
            }

            pokemon?.entity?.let { entity ->
                CommandState.activeCommands[entity.uuid] = command.action
                player.sendSystemMessage(Component.literal("Comando '${command.action}' aplicado ao ${command.pokemonName}"))

                // envia prompt de volta usando Common
                DialogueSystem.sendToPlayer?.let { it(player, "${command.pokemonName}: executando ${command.action}") }
            } ?: run {
                player.sendSystemMessage(Component.literal("Não encontrei Pokémon ativo chamado ${command.pokemonName}"))
            }
        }
    }

    // Função que processa a resposta da IA
    fun processIaResponse(server: MinecraftServer, player: ServerPlayer, content: String) {
        checkIaResponse(server, player, content)
    }
}