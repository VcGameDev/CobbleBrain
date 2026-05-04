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
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import vito.cobblebrain.social.CobblebrainWorldSave.giveCobblebrainGuide
import vito.cobblebrain.config.ClientConfigHandler
import vito.cobblebrain.config.CobblebrainConfig
import vito.cobblebrain.config.ConfigHandler
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
            Commands.literal("mpk")
                .then(
                    Commands.argument("message", StringArgumentType.greedyString())
                        .executes { ctx ->
                            val player: ServerPlayer = ctx.source.playerOrException
                            val conteudo = StringArgumentType.getString(ctx, "message")

                            //DialogueSystem.onSendPromptClient?.invoke()

                            // UTIL PRA DEBUG
                            //player.sendSystemMessage(Component.literal("1 - comando executou"))

                            // limpa fila
                            DialogueSystem.scheduledMessages[player.uuid]?.clear()

                            //player.sendSystemMessage(Component.literal("2 - limpou mensagens"))

                            val ativos = PokemonQuery.findActivePokemon(player)
                            //player.sendSystemMessage(Component.literal("3 - ativos size: ${ativos.size}"))

                            val prompt = DialogueSystem.buildPrompt(player, ativos, "\n\n$conteudo")
                            //player.sendSystemMessage(Component.literal("4 - prompt gerado: ${prompt.take(50)}"))

                            //if (DialogueSystem.sendToPlayer == null) {
                                //player.sendSystemMessage(Component.literal("5 - SEND É NULL"))
                            //} else {
                                //player.sendSystemMessage(Component.literal("5 - SEND NÃO É NULL"))
                            //}

                            DialogueSystem.sendToPlayer?.invoke(player, prompt)

                            //player.sendSystemMessage(Component.literal("6 - passou do send"))

                            // eco
                            player.sendSystemMessage(Component.literal("${player.name.string}: $conteudo"))

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
    private val configFile = File("config/cobblebrain.json5")
    private val config: CobblebrainConfig = gson.fromJson(configFile.readText(), CobblebrainConfig::class.java)

    // Agora você já tem o objeto carregado
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("cobblebrain")
                .then(
                    Commands.literal("guide")

                        // /cobblebrain karma
                        .executes { ctx ->
                            val player = ctx.source.playerOrException

                            val hasSpace = player.inventory.freeSlot != -1

                            if (hasSpace) {
                                giveCobblebrainGuide(player)

                                ctx.source.sendSuccess(
                                    { Component.literal("Cobblebrain guide added to your inventory!") },
                                    false
                                )
                            } else {
                                ctx.source.sendFailure(
                                    Component.literal("Not enough inventory space!")
                                )
                            }

                            1
                        }
                )

                .then(
                    Commands.literal("openConfig")
                        .executes { ctx ->
                            val player = ctx.source.playerOrException
                            DialogueSystem.sendToPlayer?.invoke(player, "OPEN_CONFIG_SCREEN")

                            1
                        }
                )

                .then(
                    Commands.literal("karma")

                        // /cobblebrain karma
                        .executes { ctx ->
                            val player = ctx.source.playerOrException
                            val uuid = player.uuid.toString()

                            val karmaRoot = CobblebrainWorldSave.data.getAsJsonObject("karma")

                            if (!karmaRoot.has(uuid)) {
                                player.sendSystemMessage(
                                    Component.literal("You have no karma yet.")
                                        .withStyle(ChatFormatting.YELLOW)
                                )
                                return@executes 1
                            }

                            val playerKarma = karmaRoot.getAsJsonObject(uuid)

                            player.sendSystemMessage(
                                Component.literal("Your Karma:")
                                    .withStyle(ChatFormatting.GOLD)
                            )

                            playerKarma.entrySet().forEach { entry ->
                                val species = entry.key
                                val value = entry.value.asInt

                                val color = when {
                                    value > 0 -> ChatFormatting.GREEN
                                    value < 0 -> ChatFormatting.RED
                                    else -> ChatFormatting.GRAY
                                }

                                player.sendSystemMessage(
                                    Component.literal("- $species: $value")
                                        .withStyle(color)
                                )
                            }

                            1
                        }

                        // /cobblebrain karma <species>
                        .then(
                            Commands.argument("species", StringArgumentType.word())
                                .executes { ctx ->
                                    val player = ctx.source.playerOrException
                                    val uuid = player.uuid.toString()
                                    val species = StringArgumentType.getString(ctx, "species")

                                    val karmaRoot = CobblebrainWorldSave.data.getAsJsonObject("karma")

                                    if (!karmaRoot.has(uuid)) {
                                        player.sendSystemMessage(
                                            Component.literal("You have no karma with $species.")
                                                .withStyle(ChatFormatting.RED)
                                        )
                                        return@executes 1
                                    }

                                    val playerKarma = karmaRoot.getAsJsonObject(uuid)
                                    val entry = playerKarma.entrySet()
                                        .firstOrNull { it.key.equals(species, ignoreCase = true) }
                                    val value = entry?.value?.asInt ?: 0

                                    val realSpeciesName = entry?.key ?: species
                                    val color = when {
                                        value > 0 -> ChatFormatting.GREEN
                                        value < 0 -> ChatFormatting.RED
                                        else -> ChatFormatting.GRAY
                                    }

                                    player.sendSystemMessage(
                                        Component.literal("Your karma with $realSpeciesName: $value")
                                            .withStyle(color)
                                    )

                                    1
                                }
                        )
                )
                .then(
                    Commands.literal("stopQuestFollower")
                        .executes { ctx ->

                            val source = ctx.source
                            val player = source.playerOrException

                            var stopped = 0

                            val iterator = CobblebrainWorldSave.followers.entries.iterator()

                            while (iterator.hasNext()) {
                                val entry = iterator.next()
                                val triple = entry.value

                                val pokemon = triple.first
                                val questOwner = triple.second
                                val goal = triple.third

                                if (questOwner.uuid == player.uuid) {
                                    MobBridge.removeGoal?.invoke(pokemon, goal)
                                    pokemon.navigation.stop()
                                    iterator.remove()
                                    stopped++
                                }
                            }

                            ctx.source.sendSuccess(
                                { Component.literal("Stopped $stopped quest follower(s).")
                                    .withStyle(ChatFormatting.GREEN) },
                                false
                            )

                            1
                        }
                )

                .then(
                    Commands.literal("AddInstruct")
                        .then(
                            Commands.argument("value", StringArgumentType.string())
                                .executes { ctx ->
                                    val value = StringArgumentType.getString(ctx, "value")
                                    ClientConfigHandler.clientConfig.instruct = ClientConfigHandler.clientConfig.instruct.plus(value)
                                    ConfigHandler.save()
                                    ctx.source.sendSuccess(
                                        { Component.literal("instruct set to $value") },
                                        true
                                    )
                                    1
                                }
                        )
                )
                .then(
                    Commands.literal("SetOutputFormat")
                        .then(
                            Commands.argument("value", StringArgumentType.string())
                                .executes { ctx ->
                                    val value = StringArgumentType.getString(ctx, "value")
                                    ClientConfigHandler.clientConfig.outputFormat = value
                                    ConfigHandler.save()
                                    ctx.source.sendSuccess(
                                        { Component.literal("outputFormat set to $value") },
                                        true
                                    )
                                    1
                                }
                        )
                )
                .then(
                    Commands.literal("SetListenToChat")
                        .then(
                            Commands.argument("value", BoolArgumentType.bool())
                                .executes { ctx ->
                                    val value = BoolArgumentType.getBool(ctx, "value")
                                    ConfigHandler.config.listenToChat = value
                                    ConfigHandler.save()
                                    ctx.source.sendSuccess(
                                        { Component.literal("listenToChat set to $value") },
                                        true
                                    )
                                    1
                                }
                        )
                )
                .then(
                    Commands.literal("summary")
                        .executes { ctx ->
                            val player = ctx.source.playerOrException
                            val summary = CobblebrainWorldSave.getSessionSummary(player.uuid.toString())
                            
                            if (summary != null) {
                                player.sendSystemMessage(
                                    Component.literal("\nPREVIOUS SESSION SUMMARY:\n")
                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                )
                                player.sendSystemMessage(
                                    Component.literal(summary)
                                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
                                )
                                player.sendSystemMessage(Component.literal("\n"))
                            } else {
                                player.sendSystemMessage(
                                    Component.literal("No session summary found.")
                                        .withStyle(ChatFormatting.RED)
                                )
                            }
                            1
                        }
                )
                .then(
                    Commands.literal("saveContext")
                        .executes { ctx ->
                            val player = ctx.source.playerOrException
                            DialogueSystem.triggerSessionSummary(player)
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