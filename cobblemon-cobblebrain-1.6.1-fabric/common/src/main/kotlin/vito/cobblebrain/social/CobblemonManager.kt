package vito.cobblebrain.social

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.party.PartyStore
import net.minecraft.server.level.ServerPlayer
import net.minecraft.commands.CommandSourceStack
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.Commands
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.Gson
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.ChatFormatting
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import vito.cobblebrain.social.CobblebrainWorldSave.giveCobblebrainGuide
import vito.cobblebrain.config.ClientConfigHandler
import vito.cobblebrain.config.CobblebrainConfig
import vito.cobblebrain.config.ConfigHandler
import java.io.File

object PokemonQuery {

    fun getAllPokemon(player: ServerPlayer): List<Pokemon> {
        val storage = Cobblemon.storage
            .getParty(player)

        return storage.toList()
    }

    fun isShoulderMounted(player: ServerPlayer, pokemon: Pokemon): Boolean {
        fun matches(tag: CompoundTag): Boolean {
            if (tag.isEmpty) return false

            val pokemonTag = tag.getCompound("Pokemon")

            if (pokemonTag.hasUUID("UUID")) {
                return pokemonTag.getUUID("UUID") == pokemon.uuid
            }

            return false
        }

        return matches(player.shoulderEntityLeft) ||
                matches(player.shoulderEntityRight)
    }

    // Retorna apenas os Pokémon vivos e invocados no mundo (fora da Pokébola) ou no ombro
    fun findActivePokemon(player: ServerPlayer): List<Pokemon> {
        val party: PartyStore = Cobblemon.storage.getParty(player)

        val ativos = mutableListOf<Pokemon>()
        for (i in 0..5) {
            val p = party.get(i)
            if (p != null && p.currentHealth > 0 && (p.entity != null || isShoulderMounted(player, p))) {
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

                            processTalk(player, conteudo, isStt = false)
                            1
                        }
                )
        )
    }

    fun processTalk(player: ServerPlayer, conteudo: String, isStt: Boolean = false): Boolean {
        if (conteudo.isBlank()) return false
        DialogueSystem.lastPlayerMessage[player.uuid] = conteudo

        val success = DialogueSystem.onPlayerChat(
            player,
            conteudo,
            isStt = isStt
        )

        if (success) {
            val prefix = if (isStt) "[STT] " else ""
            player.sendSystemMessage(Component.literal("$prefix${player.name.string}: $conteudo"))
        }

        return success
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

                        .executes { ctx ->
                            val player = ctx.source.playerOrException

                            val hasSpace = player.inventory.freeSlot != -1

                            if (hasSpace) {

                                giveCobblebrainGuide(player)

                                val color = ChatFormatting.AQUA

                                player.sendSystemMessage(
                                    Component.literal("=== Cobblebrain Commands ===")
                                        .withStyle(color, ChatFormatting.BOLD)
                                )

                                player.sendSystemMessage(Component.literal(" "))

                                player.sendSystemMessage(
                                    Component.literal("/mpk <message> - Talk to nearby or active Pokémon.")
                                        .withStyle(color)
                                )

                                player.sendSystemMessage(Component.literal(" "))

                                player.sendSystemMessage(
                                    Component.literal("/cobblebrain openConfig - Opens the Cobblebrain config screen.")
                                        .withStyle(color)
                                )

                                player.sendSystemMessage(Component.literal(" "))

                                player.sendSystemMessage(
                                    Component.literal("/cobblebrain karma - Shows your karma with all Pokémon species.")
                                        .withStyle(color)
                                )

                                player.sendSystemMessage(Component.literal(" "))

                                player.sendSystemMessage(
                                    Component.literal("/cobblebrain karma <species> - Shows karma with a specific Pokémon species.")
                                        .withStyle(color)
                                )

                                player.sendSystemMessage(Component.literal(" "))

                                player.sendSystemMessage(
                                    Component.literal("/cobblebrain summary - Shows the previous session summary.")
                                        .withStyle(color)
                                )

                                player.sendSystemMessage(Component.literal(" "))

                                player.sendSystemMessage(
                                    Component.literal("/cobblebrain saveContext - Saves the current session summary for the next login.")
                                        .withStyle(color)
                                )

                                player.sendSystemMessage(Component.literal(" "))

                                player.sendSystemMessage(
                                    Component.literal("/cobblebrain quitQuest - Abandons the current active quest.")
                                        .withStyle(color)
                                )

                                player.sendSystemMessage(Component.literal(" "))

                                player.sendSystemMessage(
                                    Component.literal("/cobblebrain stopQuestFollower - Stops Pokémon currently following you for quests.")
                                        .withStyle(color)
                                )

                                player.sendSystemMessage(Component.literal(" "))

                                player.sendSystemMessage(
                                    Component.literal("/cobblebrain feedback <message> - Sends temporary AI correction feedback.")
                                        .withStyle(color)
                                )

                                player.sendSystemMessage(Component.literal(" "))

                                player.sendSystemMessage(
                                    Component.literal("/cobblebrain instructFeedback <message> - Sends AI feedback and adds it to instruct.")
                                        .withStyle(color)
                                )

                                player.sendSystemMessage(Component.literal(" "))

                                player.sendSystemMessage(
                                    Component.literal("/cobblebrain AddInstruct <message> - adds custom instructions to the AI.")
                                        .withStyle(color)
                                )

                                player.sendSystemMessage(Component.literal(" "))

                                player.sendSystemMessage(
                                    Component.literal("/cobblebrain clearChatBubbles <mode>[safe|strong] (OP only) - Removes stuck chat bubbles from the world (safe: tagged bubbles, strong: legacy bubbles, can delete other invisible armor stands with no gravity).")
                                        .withStyle(color)
                                )

                                player.sendSystemMessage(Component.literal(" "))

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
                    Commands.literal("quitQuest")
                        .executes { ctx ->
                            val player = ctx.source.playerOrException
                            DialogueSystem.abandonQuest(player)
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
                                    Component.literal("No session summary found. use /cobblebrain saveContext to generate one for the next time you enter the world!")
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

                .then(
                    Commands.literal("feedback")
                        .then(
                            Commands.argument("message", StringArgumentType.greedyString())
                                .executes { ctx ->

                                    val player = ctx.source.playerOrException

                                    val feedback =
                                        StringArgumentType.getString(
                                            ctx,
                                            "message"
                                        )

                                    DialogueSystem.addFeedback(
                                        player,
                                        feedback
                                    )

                                    player.sendSystemMessage(
                                        Component.literal(
                                            "AI feedback added for the next message."
                                        ).withStyle(ChatFormatting.GREEN)
                                    )

                                    1
                                }
                        )
                )

                .then(
                    Commands.literal("instructFeedback")
                        .then(
                            Commands.argument(
                                "message",
                                StringArgumentType.greedyString()
                            )
                                .executes { ctx ->

                                    val player =
                                        ctx.source.playerOrException

                                    val feedback =
                                        StringArgumentType.getString(
                                            ctx,
                                            "message"
                                        )

                                    // Feedback temporário
                                    DialogueSystem.addFeedback(
                                        player,
                                        feedback
                                    )

                                    // Feedback + addInstruct
                                    ClientConfigHandler.clientConfig.instruct += feedback

                                    ConfigHandler.save()

                                    player.sendSystemMessage(
                                        Component.literal(
                                            "Feedback sent to AI and added to instruct."
                                        ).withStyle(ChatFormatting.GREEN)
                                    )

                                    1
                                }
                        )
                )

                .then(
                    Commands.literal("clearChatBubbles")
                        .requires { src -> src.hasPermission(2) }
                        .executes { ctx ->
                            val server = ctx.source.server
                            val removedCount = DialogueSystem.clearChatBubbles(server, strongMode = false)
                            ctx.source.sendSuccess(
                                { Component.literal("Cleared $removedCount chat bubble(s) [safe mode].").withStyle(ChatFormatting.GREEN) },
                                false
                            )
                            1
                        }
                        .then(
                            Commands.literal("safe")
                                .executes { ctx ->
                                    val server = ctx.source.server
                                    val removedCount = DialogueSystem.clearChatBubbles(server, strongMode = false)
                                    ctx.source.sendSuccess(
                                        { Component.literal("Cleared $removedCount chat bubble(s) [safe mode].").withStyle(ChatFormatting.GREEN) },
                                        false
                                    )
                                    1
                                }
                        )
                        .then(
                            Commands.literal("strong")
                                .executes { ctx ->
                                    val server = ctx.source.server
                                    val removedCount = DialogueSystem.clearChatBubbles(server, strongMode = true)
                                    ctx.source.sendSuccess(
                                        { Component.literal("Cleared $removedCount chat bubble(s) [strong mode].").withStyle(ChatFormatting.GREEN) },
                                        false
                                    )
                                    1
                                }
                        )
                )
        )
    }
}