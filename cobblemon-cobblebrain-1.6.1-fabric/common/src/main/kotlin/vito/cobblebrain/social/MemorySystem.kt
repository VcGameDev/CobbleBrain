package vito.cobblebrain.social

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.UUID
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import vito.cobblebrain.config.ConfigHandler.config
import vito.cobblebrain.currentServer

data class Memory(
    val participants: List<String>,
    val memory: String,
    val keywords: List<String>,
    val createdTick: Long,
    val playerMessage: String = "",
    val isFavorite: Boolean = false
)

data class PokemonPersonality(
    val traits: MutableList<String> = mutableListOf(),
    val quirks: MutableList<String> = mutableListOf(),
    val about: String = "",
    val likes: MutableList<String> = mutableListOf(),
    val dislikes: MutableList<String> = mutableListOf()
)

object MemorySystem {
    private enum class FileType(val suffix: String) {
        TRAITS("_traits.json"),
        MEMORIES(".jsonl")
    }

    private data class PokemonFileInfo(
        val uuid: String,
        val displayName: String
    )

    private val gson: Gson = GsonBuilder().create()

    @Suppress("unused")
    fun getTraitsFile(pokemonUuid: String): File = resolveTraitsFile(pokemonUuid, null)

    fun hasStoredPersonality(pokemonUuid: String, displayName: String? = null): Boolean {
        return resolveExistingFile(pokemonUuid, displayName, FileType.TRAITS)?.exists() == true
    }

    fun getLastModifiedTime(pokemonUuid: String, displayName: String? = null): Long {
        val traits = resolveExistingFile(pokemonUuid, displayName, FileType.TRAITS)
        val memories = resolveExistingFile(pokemonUuid, displayName, FileType.MEMORIES)
        val tTime = traits?.takeIf { it.exists() }?.lastModified() ?: 0L
        val mTime = memories?.takeIf { it.exists() }?.lastModified() ?: 0L
        return maxOf(tTime, mTime)
    }

    fun warnAboutFilenameConflict(player: ServerPlayer, pokemonUuid: String) {
        val conflicts = findFilenameConflicts(player)
        val match = conflicts.firstOrNull { group -> group.any { it.uuid == pokemonUuid } } ?: return
        sendConflictWarning(player, match)
    }

    fun warnAboutAnyFilenameConflicts(player: ServerPlayer) {
        findFilenameConflicts(player).forEach { sendConflictWarning(player, it) }
    }

    private fun sendConflictWarning(player: ServerPlayer, conflict: List<PokemonFileInfo>) {
        val names = conflict.joinToString(", ") { it.displayName }
        player.sendSystemMessage(
            Component.translatable("cobblebrain.memory.filename_conflict", names)
                .withStyle(net.minecraft.ChatFormatting.YELLOW)
        )
    }

    fun loadPersonality(pokemonUuid: String, displayName: String?): PokemonPersonality {
        val file = resolveTraitsFile(pokemonUuid, displayName)
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
                savePersonality(pokemonUuid, personality, displayName)
            }
        }

        return personality
    }

    @Suppress("unused")
    fun savePersonality(pokemonUuid: String, personality: PokemonPersonality) {
        savePersonality(pokemonUuid, personality, null)
    }

    fun savePersonality(pokemonUuid: String, personality: PokemonPersonality, displayName: String?) {
        val file = resolveTraitsFile(pokemonUuid, displayName)
        try {
            file.parentFile?.mkdirs()
            val text = gson.toJson(personality)
            file.writeText(text)
        } catch (e: Exception) {
            println("Error saving personality for $pokemonUuid: ${e.message}")
        }
    }

    @Suppress("unused")
    fun loadMemories(pokemonUuid: String): List<Memory> {
        return loadMemories(pokemonUuid, null)
    }

    fun loadMemories(pokemonUuid: String, displayName: String?): List<Memory> {
        val file = resolveMemoryFile(pokemonUuid, displayName)
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
                    val isFavorite = obj.get("isFavorite")?.asBoolean ?: obj.get("favorite")?.asBoolean ?: false
                    memories.add(Memory(participants, memory, keywords, createdTick, playerMessage, isFavorite))
                }
            }
        } catch (e: Exception) {
            println("Error loading memories for $pokemonUuid: ${e.message}")
        }
        return memories
    }

    @Suppress("unused")
    fun saveMemory(pokemonUuid: String, memory: Memory) {
        saveMemory(pokemonUuid, memory, null)
    }

    fun saveMemory(pokemonUuid: String, memory: Memory, displayName: String?) {
        DiskWriteExecutor.submit {
            try {
                val file = resolveMemoryFile(pokemonUuid, displayName)
                val memories = loadMemories(pokemonUuid, displayName).toMutableList()
                memories.add(memory)

                val limit = config.maxStoredMemories
                val toSave = if (memories.size > limit) memories.takeLast(limit) else memories

                file.parentFile?.mkdirs()
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
                            addProperty("isFavorite", m.isFavorite)
                        }
                        writer.println(gson.toJson(obj))
                    }
                }
            } catch (e: Exception) {
                println("Error saving memory for $pokemonUuid: ${e.message}")
            }
        }
    }

    @Suppress("unused")
    fun saveMemories(pokemonUuid: String, memories: List<Memory>) {
        saveMemories(pokemonUuid, memories, null)
    }

    fun saveMemories(pokemonUuid: String, memories: List<Memory>, displayName: String?) {
        DiskWriteExecutor.submit {
            try {
                val file = resolveMemoryFile(pokemonUuid, displayName)
                val limit = config.maxStoredMemories
                val toSave = if (memories.size > limit) memories.takeLast(limit) else memories

                file.parentFile?.mkdirs()
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
                            addProperty("isFavorite", m.isFavorite)
                        }
                        writer.println(gson.toJson(obj))
                    }
                }
            } catch (e: Exception) {
                println("Error saving memories for $pokemonUuid: ${e.message}")
            }
        }
    }

    fun resolveTraitsFile(pokemonUuid: String, displayName: String?): File {
        val preferred = buildPreferredFile(pokemonUuid, displayName, FileType.TRAITS)
        val existing = resolveExistingFile(pokemonUuid, displayName, FileType.TRAITS)
        if (displayName.isNullOrBlank() && existing != null) {
            return existing
        }
        return moveIfNeeded(existing, preferred)
    }

    fun resolveMemoryFile(pokemonUuid: String, displayName: String?): File {
        val preferred = buildPreferredFile(pokemonUuid, displayName, FileType.MEMORIES)
        val existing = resolveExistingFile(pokemonUuid, displayName, FileType.MEMORIES)
        if (displayName.isNullOrBlank() && existing != null) {
            return existing
        }
        return moveIfNeeded(existing, preferred)
    }

    private fun moveIfNeeded(existing: File?, preferred: File): File {
        if (existing == null || existing.absolutePath == preferred.absolutePath) {
            preferred.parentFile?.mkdirs()
            return existing ?: preferred
        }

        // Never downgrade an existing named file to generic "Pokemon-xxxxxxx"
        if (preferred.name.startsWith("Pokemon-") && !existing.name.startsWith("Pokemon-")) {
            return existing
        }

        return try {
            preferred.parentFile?.mkdirs()
            existing.copyTo(preferred, overwrite = true)
            existing.delete()
            preferred
        } catch (e: Exception) {
            println("Error migrating memory file ${existing.path} -> ${preferred.path}: ${e.message}")
            existing
        }
    }

    private fun resolveExistingFile(pokemonUuid: String, displayName: String?, type: FileType): File? {
        val preferred = buildPreferredFile(pokemonUuid, displayName, type)
        if (preferred.exists()) return preferred

        val worldDir = getWorldMemoryDirectory()
        val suffixMatches = worldDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith("-${uuidSuffix(pokemonUuid)}${type.suffix}") }
            .orEmpty()
        if (suffixMatches.size == 1) return suffixMatches.first()

        val modernLegacy = File(getModernBaseDirectory(), legacyFileName(pokemonUuid, type))
        if (modernLegacy.exists()) return modernLegacy

        val legacy = File(getLegacyBaseDirectory(), legacyFileName(pokemonUuid, type))
        if (legacy.exists()) return legacy

        return null
    }

    private fun buildPreferredFile(pokemonUuid: String, displayName: String?, type: FileType): File {
        val info = resolvePokemonFileInfo(pokemonUuid, displayName)
        val fileName = "${buildFileStem(info.displayName, info.uuid)}${type.suffix}"
        return File(getWorldMemoryDirectory(), fileName)
    }

    private fun resolvePokemonFileInfo(pokemonUuid: String, displayName: String?): PokemonFileInfo {
        val resolvedName = displayName
            ?.takeIf { it.isNotBlank() }
            ?: findPokemonByUuid(pokemonUuid)?.displayName
            ?: "Pokemon"
        return PokemonFileInfo(pokemonUuid, resolvedName)
    }

    private fun findFilenameConflicts(player: ServerPlayer): List<List<PokemonFileInfo>> {
        val pokemon = linkedMapOf<String, PokemonFileInfo>()

        PokemonQuery.getAllPokemon(player).forEach { p ->
            pokemon[p.uuid.toString()] = PokemonFileInfo(p.uuid.toString(), getDisplayName(p))
        }

        try {
            val pc = Cobblemon.storage.getPC(player)
            for (p in pc) {
                pokemon.putIfAbsent(p.uuid.toString(), PokemonFileInfo(p.uuid.toString(), getDisplayName(p)))
            }
        } catch (e: Exception) {
            println("[CobbleBrain] Could not inspect PC storage for filename conflicts: ${e.message}")
        }

        return pokemon.values
            .groupBy { buildFileStem(it.displayName, it.uuid) }
            .values
            .filter { it.size > 1 }
    }

    private fun findPokemonByUuid(pokemonUuid: String): PokemonFileInfo? {
        val uuid = try {
            UUID.fromString(pokemonUuid)
        } catch (_: Exception) {
            return null
        }

        val server = currentServer ?: return null
        server.playerList.players.forEach { player ->
            PokemonQuery.getAllPokemon(player).firstOrNull { it.uuid == uuid }?.let { pokemon ->
                return PokemonFileInfo(pokemon.uuid.toString(), getDisplayName(pokemon))
            }

            try {
                val pc = Cobblemon.storage.getPC(player)
                for (stored in pc) {
                    if (stored.uuid == uuid) {
                        return PokemonFileInfo(stored.uuid.toString(), getDisplayName(stored))
                    }
                }
            } catch (_: Exception) {
            }
        }

        server.allLevels.forEach { level ->
            val entity = level.getEntity(uuid)
            if (entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                return PokemonFileInfo(entity.pokemon.uuid.toString(), getDisplayName(entity.pokemon))
            }
        }

        return null
    }

    private fun getDisplayName(pokemon: Pokemon): String {
        return pokemon.nickname?.string?.takeIf { it.isNotBlank() } ?: pokemon.species.name
    }

    private fun getWorldMemoryDirectory(): File {
        val dir = File(getModernBaseDirectory(), sanitizePathSegment(getWorldFolderName()))
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getModernBaseDirectory(): File {
        val legacyAiDir = File("cobblebrain-ai/stored_memories")
        val dir = File("cobblebrain/stored_memories")
        if (!dir.exists() && legacyAiDir.exists() && legacyAiDir.isDirectory) {
            legacyAiDir.renameTo(dir)
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getLegacyBaseDirectory(): File {
        val dir = File("stored_memories")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getWorldFolderName(): String {
        val path = currentServer?.getWorldPath(LevelResource.ROOT)
        val rawName = path?.fileName?.toString()?.takeIf { it.isNotBlank() && it != "." }
        return rawName ?: "default_world"
    }

    private fun buildFileStem(displayName: String, pokemonUuid: String): String {
        val sanitizedName = sanitizePathSegment(displayName).ifBlank { "Pokemon" }
        return "$sanitizedName-${uuidSuffix(pokemonUuid)}"
    }

    private fun uuidSuffix(pokemonUuid: String): String {
        val normalized = pokemonUuid.replace("-", "")
        return if (normalized.length <= 7) normalized else normalized.takeLast(7)
    }

    private fun legacyFileName(pokemonUuid: String, type: FileType): String {
        return when (type) {
            FileType.TRAITS -> "${pokemonUuid}_traits.json"
            FileType.MEMORIES -> "$pokemonUuid.jsonl"
        }
    }

    private fun sanitizePathSegment(value: String): String {
        return value
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '.', ' ')
            .ifBlank { "Pokemon" }
    }
}
