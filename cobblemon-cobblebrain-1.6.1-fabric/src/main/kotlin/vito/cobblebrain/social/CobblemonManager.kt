package vito.cobblebrain.social

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.party.PartyStore
import net.minecraft.server.level.ServerPlayer
import net.minecraft.commands.CommandSourceStack
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.Commands
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.Gson
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.network.chat.Component
import vito.cobblebrain.config.CobblebrainConfig
import vito.cobblebrain.social.DialogueSystem.onPlayerChat
import java.io.File

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
            Commands.literal("msgpk") // comando: /msgpk <message>
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes { ctx ->
                        val player: ServerPlayer = ctx.source.playerOrException
                        val conteudo = StringArgumentType.getString(ctx, "message")

                        // Chama sua função já existente
                        onPlayerChat(player, conteudo)

                        //remanda a mensagem no chat
                        player.sendSystemMessage(
                            Component.literal("${player.name.string}: $conteudo")
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

object ConfigCommands {
    private val gson = Gson()
    private val configFile = File("config/cobblebrain.json")
    private val config: CobblebrainConfig = gson.fromJson(configFile.readText(), CobblebrainConfig::class.java)
    // Agora você já tem o objeto carregado
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("setPokemonTalk")
                .then(
                    Commands.argument("value", BoolArgumentType.bool())
                        .executes { ctx ->
                            val value = BoolArgumentType.getBool(ctx, "value")
                            //config.dialogueAffectFriendship = value
                            ctx.source.sendSuccess(
                                { Component.literal("pokemonTalk set to $value") },
                                true
                            )
                            1
                        }
                )
        )

        dispatcher.register(
            Commands.literal("setDialogueAffectFriendship")
                .then(
                    Commands.argument("value", BoolArgumentType.bool())
                        .executes { ctx ->
                            val value = BoolArgumentType.getBool(ctx, "value")
                            //config.dialogueAffectFriendship = value
                            ctx.source.sendSuccess(
                                { Component.literal("dialogueAffectFriendship set to $value") },
                                true
                            )
                            1
                        }
                )
        )


        dispatcher.register(
            Commands.literal("setSpontaneousDialogueChance")
                .then(
                    Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes { ctx ->
                            val value = DoubleArgumentType.getDouble(ctx, "value")
                            config.spontaneousDialogueChance = value
                            ctx.source.sendSuccess(
                                { Component.literal("spontaneousDialogueChance set to $value") },
                                true
                            )
                            1
                        }
                )
        )


        dispatcher.register(
            Commands.literal("setListenToChat")
                .then(
                    Commands.argument("value", BoolArgumentType.bool())
                        .executes { ctx ->
                            val value = BoolArgumentType.getBool(ctx, "value")
                            config.listenToChat = value
                            ctx.source.sendSuccess(
                                { Component.literal("listenToChat set to $value") },
                                true
                            )
                            1
                        }
                )
        )


        dispatcher.register(
            Commands.literal("setAiModel")
                .then(
                    Commands.argument("value", StringArgumentType.string())
                        .executes { ctx ->
                            val value = StringArgumentType.getString(ctx, "value")
                            config.aiModel = value
                            ctx.source.sendSuccess(
                                { Component.literal("aiModel set to $value") },
                                true
                            )
                            1
                        }
                )
        )

        dispatcher.register(
            Commands.literal("addToInstruct")
                .then(
                    Commands.argument("value", StringArgumentType.string())
                        .executes { ctx ->
                            val value = StringArgumentType.getString(ctx, "value")

                            // se já existe algo em instruct, concatena com espaço
                            config.instruct = if (config.instruct.isNullOrBlank()) {
                                value
                            } else {
                                config.instruct + "\n" + value
                            }

                            ctx.source.sendSuccess(
                                { Component.literal("Added to instruct: \"$value\". Current instruct: ${config.instruct}") },
                                true
                            )
                            1
                        }
                )
        )

        dispatcher.register(
            Commands.literal("getInstruct")
                .executes { ctx ->
                    val current = config.instruct
                    ctx.source.sendSuccess(
                        { Component.literal("Current instruct:\n$current") },
                        false
                    )
                    1
                }
        )


        dispatcher.register(
            Commands.literal("removeFromInstruct")
                .then(
                    Commands.argument("value", StringArgumentType.string())
                        .executes { ctx ->
                            val value = StringArgumentType.getString(ctx, "value")
                            val current = config.instruct

                            // remove todas as ocorrências exatas da substring
                            val updated = current.replace(value, "").trim()

                            config.instruct = updated

                            ctx.source.sendSuccess(
                                { Component.literal("Removed \"$value\" from instruct.") },
                                true
                            )
                            1
                        }
                )
        )


        dispatcher.register(
            Commands.literal("setInstruct")
                .then(
                    Commands.argument("value", StringArgumentType.string())
                        .executes { ctx ->
                            val value = StringArgumentType.getString(ctx, "value")
                            config.instruct = value
                            ctx.source.sendSuccess(
                                { Component.literal("instruct set to $value") },
                                true
                            )
                            1
                        }
                )
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
        } else
            println(
                "[DEBUG] Nenhum Pokemon encontrado..."
            )
    }
}