package vito.cobblebrain.server

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import vito.cobblebrain.client.CobblebrainClientHandler.ActionPayload
import vito.cobblebrain.client.CobblebrainClientHandler.PromptPayload
import vito.cobblebrain.sensors.CommandState
import vito.cobblebrain.sensors.PokemonCommand
import vito.cobblebrain.sensors.parseCommand
import com.cobblemon.mod.common.pokemon.Pokemon
import vito.cobblebrain.social.PokemonQuery

object CobblebrainServerHandler {
    fun registerReceivers() {
        // Recebe ACTION do client
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE) { payload: ActionPayload, context ->
            val action = payload.action

            context.server().execute {
                val player: ServerPlayer = context.player()
                player.sendSystemMessage(Component.literal("Executando ação: $action"))

                val command: PokemonCommand? = parseCommand(action)
                if (command != null) {
                    val ativos: List<Pokemon> = PokemonQuery.findActivePokemon(player)
                    val pokemon = ativos.find { poke ->
                        poke.nickname?.string.equals(command.pokemonName, ignoreCase = true) ||
                                poke.species.name.equals(command.pokemonName, ignoreCase = true) ||
                                poke.species.resourceIdentifier.path.equals(command.pokemonName, ignoreCase = true)
                    }

                    if (pokemon != null && pokemon.entity != null) {
                        CommandState.activeCommands[pokemon.entity!!.uuid] = command.action
                        player.sendSystemMessage(Component.literal("Comando '${command.action}' aplicado ao ${command.pokemonName}"))

                        // envia um prompt de volta para o cliente
                        ServerPlayNetworking.send(
                            player,
                            PromptPayload("${command.pokemonName}: executando ${command.action}")
                        )
                    } else {
                        player.sendSystemMessage(Component.literal("Não encontrei Pokémon ativo chamado ${command.pokemonName}"))
                    }
                }
            }
        }
    }
}
