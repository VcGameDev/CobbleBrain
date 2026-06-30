package vito.cobblebrain.social

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.UUID
import vito.cobblebrain.config.ConfigHandler.config

data class Memory(
    val participants: List<String>,
    val memory: String,
    val keywords: List<String>,
    val createdTick: Long
)

data class PokemonPersonality(
    val traits: MutableList<String> = mutableListOf(),
    val quirks: MutableList<String> = mutableListOf()
)

data class CachedMemory(
    val memory: Memory,
    var interactionsLeft: Int
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
        val file = getTraitsFile(pokemonUuid)
        if (!file.exists()) return PokemonPersonality()
        return try {
            val text = file.readText()
            gson.fromJson(text, PokemonPersonality::class.java) ?: PokemonPersonality()
        } catch (e: Exception) {
            println("Error loading personality for $pokemonUuid: ${e.message}")
            PokemonPersonality()
        }
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
                    memories.add(Memory(participants, memory, keywords, createdTick))
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
                    }
                    writer.println(gson.toJson(obj))
                }
            }
        } catch (e: Exception) {
            println("Error saving memory for $pokemonUuid: ${e.message}")
        }
    }
}

object MemoryCache {
    val cache = mutableMapOf<UUID, MutableList<CachedMemory>>()

    fun get(playerUuid: UUID): List<CachedMemory> {
        return cache[playerUuid] ?: emptyList()
    }

    fun addOrUpdate(playerUuid: UUID, memory: Memory, lifetime: Int, maxCount: Int) {
        val list = cache.getOrPut(playerUuid) { mutableListOf() }
        val existing = list.firstOrNull { it.memory.memory == memory.memory && it.memory.participants == memory.participants }
        if (existing != null) {
            existing.interactionsLeft = lifetime
        } else {
            list.add(CachedMemory(memory, lifetime))
        }

        if (list.size > maxCount) {
            list.sortByDescending { it.interactionsLeft }
            while (list.size > maxCount) {
                list.removeAt(list.size - 1)
            }
        }
    }

    fun tick(playerUuid: UUID) {
        val list = cache[playerUuid] ?: return
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val cached = iterator.next()
            cached.interactionsLeft--
            if (cached.interactionsLeft <= 0) {
                iterator.remove()
            }
        }
    }
}
