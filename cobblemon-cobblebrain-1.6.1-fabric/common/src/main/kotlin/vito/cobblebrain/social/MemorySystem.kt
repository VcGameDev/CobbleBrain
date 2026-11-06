package vito.cobblebrain.social

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
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
import net.minecraft.server.level.ServerLevel
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
    val dislikes: MutableList<String> = mutableListOf(),
    val canTalk: Boolean? = true,
    val canAct: Boolean? = true,
    val canUpdatePersonality: Boolean? = true,
    val lockedTraits: MutableList<String>? = mutableListOf(),
    val lockedQuirks: MutableList<String>? = mutableListOf(),
    val lockedLikes: MutableList<String>? = mutableListOf(),
    val lockedDislikes: MutableList<String>? = mutableListOf()
) {
    val effectiveCanTalk: Boolean
        get() = canTalk ?: true

    val effectiveCanAct: Boolean
        get() = canAct ?: true

    val effectiveCanUpdatePersonality: Boolean
        get() = canUpdatePersonality ?: true

    val isMuted: Boolean
        get() = !effectiveCanTalk

    val effectiveLockedTraits: MutableList<String>
        get() = lockedTraits ?: mutableListOf()

    val effectiveLockedQuirks: MutableList<String>
        get() = lockedQuirks ?: mutableListOf()

    val effectiveLockedLikes: MutableList<String>
        get() = lockedLikes ?: mutableListOf()

    val effectiveLockedDislikes: MutableList<String>
        get() = lockedDislikes ?: mutableListOf()
}

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
    private val personalityCache = java.util.concurrent.ConcurrentHashMap<String, PokemonPersonality>()

    fun clearCache() {
        personalityCache.clear()
        println("[CobbleBrain] Personality cache cleared.")
    }

    fun invalidatePersonalityCache(pokemonUuid: String) {
        personalityCache.remove(pokemonUuid)
    }

    fun updatePersonalityCache(pokemonUuid: String, personality: PokemonPersonality) {
        personalityCache[pokemonUuid] = personality
    }

    @Suppress("unused")
    fun getTraitsFile(pokemonUuid: String): File = resolveTraitsFile(pokemonUuid, null)

    fun isWild(pokemonUuid: String): Boolean {
        val uuid = try { UUID.fromString(pokemonUuid) } catch (_: Exception) { null }
        if (uuid != null) {
            val server = currentServer
            if (server != null) {
                for (p in server.playerList.players) {
                    if (PokemonQuery.getAllPokemon(p).any { it.uuid == uuid }) return false
                }
                for (level in server.allLevels) {
                    val entity = level.getEntity(uuid) as? PokemonEntity
                        ?: (level as? ServerLevel)?.allEntities?.firstOrNull { (it as? PokemonEntity)?.pokemon?.uuid == uuid } as? PokemonEntity
                    if (entity != null) {
                        return entity.pokemon.getOwnerUUID() == null
                    }
                }
            }
        }
        val wildObj = CobblebrainWorldSave.data.getAsJsonObject("wild_pokemon_personalities")
        return wildObj?.has(pokemonUuid) == true
    }

    fun hasStoredPersonality(pokemonUuid: String, displayName: String? = null): Boolean {
        if (isWild(pokemonUuid)) {
            val wildObj = CobblebrainWorldSave.data.getAsJsonObject("wild_pokemon_personalities")
            return wildObj?.getAsJsonObject(pokemonUuid)?.has("personality") == true
        }
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
        val cached = personalityCache[pokemonUuid]
        if (cached != null) return cached

        if (isWild(pokemonUuid)) {
            val wildObj = CobblebrainWorldSave.data.getAsJsonObject("wild_pokemon_personalities")
            val record = wildObj?.getAsJsonObject(pokemonUuid)
            var personality = if (record != null && record.has("personality")) {
                try {
                    gson.fromJson(record.getAsJsonObject("personality"), PokemonPersonality::class.java) ?: PokemonPersonality()
                } catch (_: Exception) {
                    PokemonPersonality()
                }
            } else {
                PokemonPersonality()
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
            personalityCache[pokemonUuid] = personality
            return personality
        }

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

        personalityCache[pokemonUuid] = personality
        return personality
    }

    @Suppress("unused")
    fun savePersonality(pokemonUuid: String, personality: PokemonPersonality) {
        savePersonality(pokemonUuid, personality, null, false)
    }

    fun savePersonality(pokemonUuid: String, personality: PokemonPersonality, displayName: String?, forcePermanent: Boolean = false) {
        personalityCache[pokemonUuid] = personality
        if (!forcePermanent && isWild(pokemonUuid)) {
            val wildObj = CobblebrainWorldSave.data.getAsJsonObject("wild_pokemon_personalities")
                ?: JsonObject().also { CobblebrainWorldSave.data.add("wild_pokemon_personalities", it) }
            val record = wildObj.getAsJsonObject(pokemonUuid) ?: JsonObject().also { wildObj.add(pokemonUuid, it) }
            record.add("personality", gson.toJsonTree(personality))
            CobblebrainWorldSave.save()
            return
        }

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
        if (isWild(pokemonUuid)) {
            val wildObj = CobblebrainWorldSave.data.getAsJsonObject("wild_pokemon_personalities")
            val record = wildObj?.getAsJsonObject(pokemonUuid)
            if (record == null || !record.has("memories")) return emptyList()
            val memories = mutableListOf<Memory>()
            try {
                record.getAsJsonArray("memories")?.forEach { el ->
                    if (el.isJsonObject) {
                        try {
                            val obj = el.asJsonObject
                            val participants = obj.getAsJsonArray("participants")?.map { it.asString } ?: emptyList()
                            val memory = obj.get("memory")?.asString ?: ""
                            val keywords = obj.getAsJsonArray("keywords")?.map { it.asString } ?: emptyList()
                            val createdTick = obj.get("createdTick")?.asLong ?: 0L
                            val playerMessage = obj.get("playerMessage")?.asString ?: ""
                            val isFavorite = obj.get("isFavorite")?.asBoolean ?: obj.get("favorite")?.asBoolean ?: false
                            memories.add(Memory(participants, memory, keywords, createdTick, playerMessage, isFavorite))
                        } catch (lineEx: Exception) {
                            println("[CobbleBrain] Skipping corrupted memory line for $pokemonUuid: ${lineEx.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error loading wild memories for $pokemonUuid: ${e.message}")
            }
            return memories
        }

        val file = resolveMemoryFile(pokemonUuid, displayName)
        if (!file.exists()) return emptyList()
        val memories = mutableListOf<Memory>()
        try {
            file.forEachLine { line ->
                if (line.isNotBlank()) {
                    try {
                        val obj = JsonParser.parseString(line).asJsonObject
                        val participants = obj.getAsJsonArray("participants")?.map { it.asString } ?: emptyList()
                        val memory = obj.get("memory")?.asString ?: ""
                        val keywords = obj.getAsJsonArray("keywords")?.map { it.asString } ?: emptyList()
                        val createdTick = obj.get("createdTick")?.asLong ?: 0L
                        val playerMessage = obj.get("playerMessage")?.asString ?: ""
                        val isFavorite = obj.get("isFavorite")?.asBoolean ?: obj.get("favorite")?.asBoolean ?: false
                        memories.add(Memory(participants, memory, keywords, createdTick, playerMessage, isFavorite))
                    } catch (lineEx: Exception) {
                        println("[CobbleBrain] Skipping corrupted memory line for $pokemonUuid: ${lineEx.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("Error loading memories for $pokemonUuid: ${e.message}")
        }
        return memories
    }

    fun pruneMemories(memories: List<Memory>, limit: Int): List<Memory> {
        if (memories.size <= limit) return memories
        if (limit <= 0) return emptyList()

        var excess = memories.size - limit
        val result = memories.toMutableList()

        // 1. First pass: discard non-favorite memories from oldest to newest
        val it = result.iterator()
        while (it.hasNext() && excess > 0) {
            val m = it.next()
            if (!m.isFavorite) {
                it.remove()
                excess--
            }
        }

        // 2. Second pass: if excess still remains (all remaining are favorites), discard oldest favorites
        while (excess > 0 && result.isNotEmpty()) {
            result.removeAt(0)
            excess--
        }

        return result
    }

    @Suppress("unused")
    fun saveMemory(pokemonUuid: String, memory: Memory) {
        saveMemory(pokemonUuid, memory, null)
    }

    fun saveMemory(pokemonUuid: String, memory: Memory, displayName: String?) {
        if (isWild(pokemonUuid)) {
            val memories = loadMemories(pokemonUuid, displayName).toMutableList()
            memories.add(memory)
            val toSave = pruneMemories(memories, config.maxStoredMemories)
            saveMemories(pokemonUuid, toSave, displayName)
            return
        }

        DiskWriteExecutor.submit {
            try {
                val file = resolveMemoryFile(pokemonUuid, displayName)
                val memories = loadMemories(pokemonUuid, displayName).toMutableList()
                memories.add(memory)

                val toSave = pruneMemories(memories, config.maxStoredMemories)

                file.parentFile?.mkdirs()
                writeMemoriesToFileAtomically(file, toSave)
            } catch (e: Exception) {
                println("Error saving memory for $pokemonUuid: ${e.message}")
            }
        }
    }

    @Suppress("unused")
    fun saveMemories(pokemonUuid: String, memories: List<Memory>) {
        saveMemories(pokemonUuid, memories, null, false)
    }

    fun saveMemories(pokemonUuid: String, memories: List<Memory>, displayName: String?, forcePermanent: Boolean = false) {
        if (!forcePermanent && isWild(pokemonUuid)) {
            val wildObj = CobblebrainWorldSave.data.getAsJsonObject("wild_pokemon_personalities")
                ?: JsonObject().also { CobblebrainWorldSave.data.add("wild_pokemon_personalities", it) }
            val record = wildObj.getAsJsonObject(pokemonUuid) ?: JsonObject().also { wildObj.add(pokemonUuid, it) }
            val toSave = pruneMemories(memories, config.maxStoredMemories)
            val memArr = com.google.gson.JsonArray()
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
                memArr.add(obj)
            }
            record.add("memories", memArr)
            CobblebrainWorldSave.save()
            return
        }

        DiskWriteExecutor.submit {
            try {
                val file = resolveMemoryFile(pokemonUuid, displayName)
                val toSave = pruneMemories(memories, config.maxStoredMemories)

                file.parentFile?.mkdirs()
                writeMemoriesToFileAtomically(file, toSave)
            } catch (e: Exception) {
                println("Error saving memories for $pokemonUuid: ${e.message}")
            }
        }
    }

    private fun writeMemoriesToFileAtomically(file: File, toSave: List<Memory>) {
        file.parentFile?.mkdirs()
        val tempFile = File(file.parentFile, "${file.name}.${System.currentTimeMillis()}.tmp")
        PrintWriter(FileWriter(tempFile, false)).use { writer ->
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
        try {
            java.nio.file.Files.move(
                tempFile.toPath(),
                file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            java.nio.file.Files.move(
                tempFile.toPath(),
                file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
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
        if (suffixMatches.isNotEmpty()) return suffixMatches.maxByOrNull { it.lastModified() }

        val modernLegacy = File(getModernBaseDirectory(), legacyFileName(pokemonUuid, type))
        if (modernLegacy.exists()) return modernLegacy

        val legacy = File(getLegacyBaseDirectory(), legacyFileName(pokemonUuid, type))
        if (legacy.exists()) return legacy

        val pUuid = try { UUID.fromString(pokemonUuid) } catch (_: Exception) { null }
        if (pUuid != null) {
            val server = currentServer
            if (server != null) {
                var activeEntityUuid: UUID? = null
                for (player in server.playerList.players) {
                    val p = PokemonQuery.getAllPokemon(player).firstOrNull { it.uuid == pUuid }
                    if (p?.entity != null) {
                        activeEntityUuid = p.entity?.uuid
                        break
                    }
                }
                if (activeEntityUuid == null) {
                    for (level in server.allLevels) {
                        val e = (level as? ServerLevel)?.allEntities?.firstOrNull { (it as? PokemonEntity)?.pokemon?.uuid == pUuid }
                        if (e != null) {
                            activeEntityUuid = e.uuid
                            break
                        }
                    }
                }
                if (activeEntityUuid != null) {
                    val entityUuidStr = activeEntityUuid.toString()
                    val entitySuffixMatches = worldDir.listFiles()
                        ?.filter { it.isFile && it.name.endsWith("-${uuidSuffix(entityUuidStr)}${type.suffix}") }
                        .orEmpty()
                    if (entitySuffixMatches.isNotEmpty()) return entitySuffixMatches.maxByOrNull { it.lastModified() }
                }
            }
        }

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
            if (entity is PokemonEntity) {
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