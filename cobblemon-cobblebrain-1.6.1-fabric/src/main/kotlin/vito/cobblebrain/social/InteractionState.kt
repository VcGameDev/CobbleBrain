package vito.cobblebrain.social

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.nio.file.Path

object InteractionStore {
    private val gson = Gson()
    private val interactions = mutableMapOf<String, Int>()
    private var loaded = false

    // agora a chave inclui nome + UUID
    private fun keyOf(aName: String, aId: String, bName: String, bId: String): String {
        return listOf("${aName}#${aId}", "${bName}#${bId}")
            .sorted()
            .joinToString("_")
    }

    private fun file(server: MinecraftServer): Path {
        val dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data")
        Files.createDirectories(dataDir)
        return dataDir.resolve("cobblebrain_interactions.json")
    }

    fun load(server: MinecraftServer) {
        if (loaded) return
        val f = file(server)
        if (Files.exists(f)) {
            val text = Files.readString(f)
            val type = object : TypeToken<Map<String, Int>>() {}.type
            val map: Map<String, Int> = gson.fromJson(text, type) ?: emptyMap()
            interactions.putAll(map)
        }
        loaded = true
    }

    fun save(server: MinecraftServer) {
        val f = file(server)
        val json = gson.toJson(interactions)
        Files.writeString(f, json)
    }

    fun addInteraction(server: MinecraftServer, aName: String, aId: String, bName: String, bId: String): Int {
        load(server)
        val key = keyOf(aName, aId, bName, bId)
        val newValue = (interactions[key] ?: 0) + 1
        interactions[key] = newValue
        save(server)
        return newValue
    }

    fun getInteractionCount(server: MinecraftServer, aName: String, aId: String, bName: String, bId: String): Int {
        load(server)
        return interactions[keyOf(aName, aId, bName, bId)] ?: 0
    }
}

object DialogueStore {
    private val gson = Gson()
    private var loaded = false
    private val dialogues = ArrayDeque<List<String>>() // fila de diálogos

    private fun file(server: MinecraftServer): Path {
        val dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data")
        Files.createDirectories(dataDir)
        return dataDir.resolve("cobblebrain_dialogues.json")
    }

    fun load(server: MinecraftServer) {
        if (loaded) return
        val f = file(server)
        if (Files.exists(f)) {
            val text = Files.readString(f)
            val type = object : TypeToken<List<List<String>>>() {}.type
            val list: List<List<String>> = gson.fromJson(text, type) ?: emptyList()
            dialogues.clear()
            dialogues.addAll(list)
        }
        loaded = true
    }

    fun save(server: MinecraftServer) {
        val f = file(server)
        val json = gson.toJson(dialogues.toList())
        Files.writeString(f, json)
    }

    fun addDialogue(server: MinecraftServer, dialogue: List<String>) {
        load(server)
        dialogues.addLast(dialogue)
        while (dialogues.size > 2) { // mantém só os 2 últimos
            dialogues.removeFirst()
        }
        save(server)
    }

    fun getDialogues(server: MinecraftServer): List<List<String>> {
        load(server)
        return dialogues.toList()
    }
}


