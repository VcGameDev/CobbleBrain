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
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import vito.cobblebrain.client.CobblebrainClientHandler
import vito.cobblebrain.client.social.CobblebrainWorldSave
import vito.cobblebrain.client.social.CobblebrainWorldSave.giveCobblebrainGuide
import vito.cobblebrain.config.CobblebrainConfig
import vito.cobblebrain.config.ConfigHandler
import vito.cobblebrain.mixin.MobAccessor
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

                            // igual ao onPlayerChat
                            DialogueSystem.scheduledMessages[player.uuid]?.clear()

                            val ativos = PokemonQuery.findActivePokemon(player)
                            val prompt = DialogueSystem.buildPrompt(player, ativos, "\n\n$conteudo")

                            ServerPlayNetworking.send(player, CobblebrainClientHandler.PromptPayload(prompt))

                            // opcional: ecoar mensagem original
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
                    Commands.literal("SetPokemonTalk")
                        .then(
                            Commands.argument("value", BoolArgumentType.bool())
                                .executes { ctx ->
                                    val value = BoolArgumentType.getBool(ctx, "value")
                                    ConfigHandler.config.pokemonTalk = value
                                    ConfigHandler.save()
                                    ctx.source.sendSuccess(
                                        { Component.literal("pokemonTalk set to $value") },
                                        true
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
                                    val accessor = pokemon as MobAccessor
                                    accessor.getGoalSelector().removeGoal(goal)
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
                    Commands.literal("SetInstruct")
                        .then(
                            Commands.argument("value", StringArgumentType.string())
                                .executes { ctx ->
                                    val value = StringArgumentType.getString(ctx, "value")
                                    ConfigHandler.config.instruct = value
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
                                    ConfigHandler.config.outputFormat = value
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