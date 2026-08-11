package vito.cobblebrain.server

import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import vito.cobblebrain.sensors.CommandState
import vito.cobblebrain.sensors.PokemonCommand
import vito.cobblebrain.sensors.parseCommand
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.MinecraftServer
import vito.cobblebrain.config.ConfigHandler
import vito.cobblebrain.social.DialogueSystem
import vito.cobblebrain.social.DialogueSystem.checkIaResponse
import vito.cobblebrain.social.MemorySystem
import vito.cobblebrain.social.PokemonPersonality
import vito.cobblebrain.social.PokemonQuery
import vito.cobblebrain.social.RecentEventsSystem

object CobblebrainServerHandler {
    // Função que processa o comando recebido de um player
    fun processAction(player: ServerPlayer, action: String) {
        val command: PokemonCommand? = parseCommand(action)
        if (command != null) {
            val actionKey = command.action.lowercase().replace(" ", "_")
            val isActionActiveOnServer = when (actionKey) {
                "cook" -> ConfigHandler.config.actionSettings.cook.active
                "grow" -> ConfigHandler.config.actionSettings.grow.active
                "repair" -> ConfigHandler.config.actionSettings.repair.active
                "shift" -> ConfigHandler.config.actionSettings.shift.active
                "fish" -> ConfigHandler.config.actionSettings.fish.active
                "nightmare" -> ConfigHandler.config.actionSettings.nightmare.active
                "light" -> ConfigHandler.config.actionSettings.light.active
                "scout" -> ConfigHandler.config.actionSettings.scout.active
                "teleport" -> ConfigHandler.config.actionSettings.teleport.active
                "attack" -> ConfigHandler.config.actionSettings.attack.active
                "protect" -> ConfigHandler.config.actionSettings.protect.active
                "eat" -> ConfigHandler.config.actionSettings.eat.active
                "buff" -> ConfigHandler.config.actionSettings.buff.active
                "debuff", "debuff_enemy" -> ConfigHandler.config.actionSettings.debuffEnemy.active
                "excavate", "demolish" -> ConfigHandler.config.actionSettings.excavate.active
                "prospect" -> ConfigHandler.config.actionSettings.prospect.active
                "rest", "sit" -> ConfigHandler.config.actionSettings.rest.active
                "idle" -> ConfigHandler.config.actionSettings.idle.active
                else -> true
            }

            if (!isActionActiveOnServer) {
                player.sendSystemMessage(Component.translatable("cobblebrain.feedback.action_disabled", command.action))
                return
            }

            val ativos: List<Pokemon> = PokemonQuery.findActivePokemon(player)
            
            if (command.pokemonName.equals("ALL", ignoreCase = true)) {
                // Aplica para TODOS os pokémons ativos
                ativos.forEach { poke ->
                    poke.entity?.let { entity ->
                        RecentEventsSystem.commandSources[entity.uuid] = RecentEventsSystem.CommandSource.HUD
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
                    RecentEventsSystem.commandSources[entity.uuid] = RecentEventsSystem.CommandSource.HUD
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

    fun handleRequestPersonalityList(player: ServerPlayer) {
        MemorySystem.warnAboutAnyFilenameConflicts(player)
        val gson = com.google.gson.Gson()
        val array = com.google.gson.JsonArray()

        // --- Party Pokémon (always shown) ---
        val partyPokemon = PokemonQuery.getAllPokemon(player)
        val partyUuids = partyPokemon.map { it.uuid.toString() }.toSet()

        partyPokemon.forEach { p ->
            val uuidStr = p.uuid.toString()
            val displayName = p.nickname?.string?.takeIf { it.isNotBlank() } ?: p.species.name
            val personality = MemorySystem.loadPersonality(uuidStr, displayName)
            val personalityJson = gson.toJson(personality)
            val memories = MemorySystem.loadMemories(uuidStr, displayName)
            val memoriesJson = gson.toJson(memories)

            val entry = com.google.gson.JsonObject()
            entry.addProperty("uuid", uuidStr)
            entry.addProperty("displayName", displayName)
            entry.addProperty("species", p.species.name)
            entry.addProperty("personalityJson", personalityJson)
            entry.addProperty("memoriesJson", memoriesJson)
            entry.addProperty("inParty", true)
            array.add(entry)
        }

        // --- PC Pokémon that have a personality file (previously edited) ---
        try {
            val pc = com.cobblemon.mod.common.Cobblemon.storage.getPC(player)
            for (p in pc) {
                val uuidStr = p.uuid.toString()
                // Skip if already in party
                if (uuidStr in partyUuids) continue
                // Only include if a personality file exists for this Pokémon
                val displayName = p.nickname?.string?.takeIf { it.isNotBlank() } ?: p.species.name
                if (!MemorySystem.hasStoredPersonality(uuidStr, displayName)) continue
                val personality = MemorySystem.loadPersonality(uuidStr, displayName)
                val personalityJson = gson.toJson(personality)
                val memories = MemorySystem.loadMemories(uuidStr, displayName)
                val memoriesJson = gson.toJson(memories)

                val entry = com.google.gson.JsonObject()
                entry.addProperty("uuid", uuidStr)
                entry.addProperty("displayName", displayName)
                entry.addProperty("species", p.species.name)
                entry.addProperty("personalityJson", personalityJson)
                entry.addProperty("memoriesJson", memoriesJson)
                entry.addProperty("inParty", false)
                array.add(entry)
            }
        } catch (e: Exception) {
            println("[CobbleBrain] Could not read PC storage for personality list: ${e.message}")
        }

        DialogueSystem.sendPersonalityList?.invoke(player, array.toString())
    }

    fun handleSavePersonality(player: ServerPlayer, pokemonUuid: String, personalityJson: String, memoriesJson: String = "") {
        val cfg = ConfigHandler.config
        if (!cfg.allowClientPersonalityEditing) {
            player.sendSystemMessage(Component.literal("Client personality editing is disabled by the server.").withStyle(net.minecraft.ChatFormatting.RED))
            return
        }

        try {
            val gson = com.google.gson.Gson()
            val personality = gson.fromJson(personalityJson, PokemonPersonality::class.java)
            if (personality != null) {
                MemorySystem.warnAboutFilenameConflict(player, pokemonUuid)
                MemorySystem.savePersonality(pokemonUuid, personality)
                if (memoriesJson.isNotBlank()) {
                    try {
                        val memoryType = object : com.google.gson.reflect.TypeToken<List<vito.cobblebrain.social.Memory>>() {}.type
                        val memories: List<vito.cobblebrain.social.Memory> = gson.fromJson(memoriesJson, memoryType) ?: emptyList()
                        MemorySystem.saveMemories(pokemonUuid, memories)
                    } catch (ex: Exception) {
                        println("Error parsing memoriesJson on save: ${ex.message}")
                    }
                }
                player.sendSystemMessage(Component.literal("Personality saved successfully.").withStyle(net.minecraft.ChatFormatting.GREEN))
            }
        } catch (e: Exception) {
            player.sendSystemMessage(Component.literal("Error parsing/saving personality: ${e.message}").withStyle(net.minecraft.ChatFormatting.RED))
        }
    }

    fun handleDeletePersonality(player: ServerPlayer, pokemonUuid: String) {
        val cfg = ConfigHandler.config
        if (!cfg.allowClientPersonalityEditing) {
            player.sendSystemMessage(Component.literal("Client personality editing is disabled by the server.").withStyle(net.minecraft.ChatFormatting.RED))
            return
        }

        MemorySystem.warnAboutFilenameConflict(player, pokemonUuid)
        val file = MemorySystem.getTraitsFile(pokemonUuid)
        if (file.exists()) {
            file.delete()
            player.sendSystemMessage(Component.literal("Personality reset successfully.").withStyle(net.minecraft.ChatFormatting.GREEN))
        }
    }
}
