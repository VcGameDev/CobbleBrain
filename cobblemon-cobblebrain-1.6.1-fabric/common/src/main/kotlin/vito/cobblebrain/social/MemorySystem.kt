package vito.cobblebrain.social

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import vito.cobblebrain.config.ConfigHandler.config

data class Memory(
    val participants: List<String>,
    val memory: String,
    val keywords: List<String>,
    val createdTick: Long,
    val playerMessage: String = ""
)

data class PokemonPersonality(
    val traits: MutableList<String> = mutableListOf(),
    val quirks: MutableList<String> = mutableListOf(),
    val about: String = "",
    val likes: MutableList<String> = mutableListOf(),
    val dislikes: MutableList<String> = mutableListOf()
)

object MemorySystem {
    private val gson: Gson = GsonBuilder().create()

    fun getTraitsFile(pokemonUuid: String): File {
        val dir = File("stored_memories")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "${pokemonUuid}_traits.json")
    }

    fun loadPersonality(pokemonUuid: String): PokemonPersonality {
        return loadPersonality(pokemonUuid, null)
    }

    fun loadPersonality(pokemonUuid: String, displayName: String?): PokemonPersonality {
        val file = getTraitsFile(pokemonUuid)
        var personality = if (!file.exists()) PokemonPersonality()
        else {
            try {
                val text = file.readText()
                gson.fromJson(text, PokemonPersonality::class.java) ?: PokemonPersonality()
            } catch (e: Exception) {
                println("Error loading personality for $pokemonUuid: ${e.message}")
                PokemonPersonality()
            }
        }

        // Lazy migration
        if (displayName != null && personality.about.isBlank()) {
            val matched = config.characteristics.firstOrNull { entry ->
                val split = entry.split(":", limit = 2)
                if (split.size >= 2) {
                    val charName = split[0].trim()
                    charName.equals(displayName, ignoreCase = true)
                } else false
            }
            if (matched != null) {
                val desc = matched.split(":", limit = 2)[1].trim()
                personality = personality.copy(about = desc)
                savePersonality(pokemonUuid, personality)
            }
        }

        return personality
    }

    fun savePersonality(pokemonUuid: String, personality: PokemonPersonality) {
        val file = getTraitsFile(pokemonUuid)
        try {
            val text = gson.toJson(personality)
            file.writeText(text)
        } catch (e: Exception) {
            println("Error saving personality for $pokemonUuid: ${e.message}")
        }
    }


    fun getMemoryFile(pokemonUuid: String): File {
        val dir = File("stored_memories")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "$pokemonUuid.jsonl")
    }

    fun loadMemories(pokemonUuid: String): List<Memory> {
        val file = getMemoryFile(pokemonUuid)
        if (!file.exists()) return emptyList()
        val memories = mutableListOf<Memory>()
        try {
            file.forEachLine { line ->
                if (line.isNotBlank()) {
                    val obj = JsonParser.parseString(line).asJsonObject
                    val participants = obj.getAsJsonArray("participants").map { it.asString }
                    val memory = obj.get("memory").asString
                    val keywords = obj.getAsJsonArray("keywords").map { it.asString }
                    val createdTick = obj.get("createdTick").asLong
                    val playerMessage = obj.get("playerMessage")?.asString ?: ""
                    memories.add(Memory(participants, memory, keywords, createdTick, playerMessage))
                }
            }
        } catch (e: Exception) {
            println("Error loading memories for $pokemonUuid: ${e.message}")
        }
        return memories
    }

    fun saveMemory(pokemonUuid: String, memory: Memory) {
        val file = getMemoryFile(pokemonUuid)
        val memories = loadMemories(pokemonUuid).toMutableList()
        memories.add(memory)
        
        val limit = config.maxStoredMemories
        val toSave = if (memories.size > limit) {
            memories.takeLast(limit)
        } else {
            memories
        }

        DiskWriteExecutor.submit {
            try {
                PrintWriter(FileWriter(file, false)).use { writer ->
                    toSave.forEach { m ->
                        val obj = JsonObject().apply {
                            val partsArr = com.google.gson.JsonArray()
                            m.participants.forEach { partsArr.add(it) }
                            add("participants", partsArr)
                            addProperty("memory", m.memory)
                            val kwArr = com.google.gson.JsonArray()
                            m.keywords.forEach { kwArr.add(it.lowercase()) }
                            add("keywords", kwArr)
                            addProperty("createdTick", m.createdTick)
                            addProperty("playerMessage", m.playerMessage)
                        }
                        writer.println(gson.toJson(obj))
                    }
                }
            } catch (e: Exception) {
                println("Error saving memory for $pokemonUuid: ${e.message}")
            }
        }
    }
}
