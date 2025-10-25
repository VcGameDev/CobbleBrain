package vito.cobblebrain.social

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.party.PartyStore
import net.minecraft.server.level.ServerPlayer
import net.minecraft.commands.CommandSourceStack
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.Commands
import com.cobblemon.mod.common.pokemon.Pokemon
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.network.chat.Component
import vito.cobblebrain.social.DialogueSystem.onPlayerChat

object PokemonQuery {

    // Retorna apenas os Pokémon vivos e invocados no mundo (fora da Pokébola)
    fun findActivePokemon(player: ServerPlayer): List<Pokemon> {
        val party: PartyStore = Cobblemon.storage.getParty(player)

        val ativos = mutableListOf<Pokemon>()
        for (i in 0..5) {
            val p = party.get(i)
            if (p != null && p.currentHealth > 0 && p.entity != null) {
                ativos.add(p)
            }
        }
        return ativos
    }
}

object PokemonTalkCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("msgpk") // comando: /msgpk <mensagem>
                .then(Commands.argument("mensagem", StringArgumentType.greedyString())
                    .executes { ctx ->
                        val player: ServerPlayer = ctx.source.playerOrException
                        val conteudo = StringArgumentType.getString(ctx, "mensagem")

                        // Chama sua função já existente
                        onPlayerChat(player, conteudo)

                        //remanda a mensagem no chat
                        player.sendSystemMessage(
                            Component.literal("${player.name.string} disse: $conteudo")
                        )

                        1
                    }
                )
        )
    }
}

object DebugPartyCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("debugparty")
                .executes { ctx ->
                    val player: ServerPlayer = ctx.source.playerOrException

                    // Chama nossa função de debug passando o jogador
                    debugParty(player)
                    1
                }
        )
    }

}

// codigo de debug
private fun debugParty(player: ServerPlayer) {
    val party = Cobblemon.storage.getParty(player)

    println("[DEBUG] PartyStore class: ${party.javaClass.name}")

    // Itera pelos 6 slots da equipe
    for (i in 0..5) {
        val p = party.get(i) // pode ser null se o slot estiver vazio
        if (p != null) {
            val species = p.species.name
            val hp = p.currentHealth
            val maxHp = p.maxHealth
            val isAlive = hp > 0
            val isSummoned = p.entity != null // se tem entidade no mundo

            println(
                "[DEBUG] Slot $i: $species | HP=$hp/$maxHp | vivo=$isAlive | ativoNoMundo=$isSummoned"
            )
        }
        else
            println(
                "[DEBUG] Nenhum Pokemon encontrado..."
            )
    }
}