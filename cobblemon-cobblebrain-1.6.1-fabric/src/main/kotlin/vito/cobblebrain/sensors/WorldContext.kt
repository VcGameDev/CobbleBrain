package vito.cobblebrain.sensors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.storage.LevelResource
import vito.cobblebrain.config.ConfigHandler.config
import vito.cobblebrain.social.PokemonQuery
import java.nio.file.Files
import java.nio.file.Path

data class WorldContext(
    val playerName: String,
    val biome: String,
    val timeOfDay: Long,
    val weather: String,
    val lightLevel: Int,
    val blockUnder: String,
    val timeLabel: String,
    val terrainHint: String,
    val nearbyEntities: Int,
    val nearbyMobs: String,
    val nearbyPokemon: String,
    val nearbyPokemonEntities: List<PokemonEntity>,
    val nearbyItems: String,
    val specialBlocks: String,
    val health: Float,
    val maxHealth: Float,
    val armor: Int,
    val mainHand: String,
    val offHand: String,
)

fun collectWorldContext(player: ServerPlayer): WorldContext {
    val level = player.level() as net.minecraft.server.level.ServerLevel
    val pos = player.blockPosition()

    val playerName = player.scoreboardName

    val biome = level.getBiome(pos).unwrapKey()
        .map { it.location().toString() }
        .orElse("unknown")

    val timeOfDay = level.dayTime % 24000

    val weather = when {
        level.isThundering -> "thunderstorm"
        level.isRaining -> "rain"
        else -> "clear"
    }

    val lightLevel = level.getBrightness(LightLayer.BLOCK, pos)
    val blockUnder = level.getBlockState(pos.below()).block.descriptionId

    val timeLabel = when (timeOfDay) {
        in 0..4000 -> "amanhecer"
        in 4001..8000 -> "manhã"
        in 8001..12000 -> "meio-dia"
        in 12001..16000 -> "anoitecer"
        else -> "madrugada"
    }

    val y = player.blockY
    val terrainHint = when {
        lightLevel < 7 && blockUnder.contains("stone") -> "em uma caverna"
        y > 100 -> "em uma montanha"
        else -> "em terreno aberto"
    }

    val nearby = level.getEntities(player, player.boundingBox.inflate(10.0))
        .filterIsInstance<LivingEntity>()
        .filter { it != player }

    val nearbyEntities = nearby.size

    val nearbyMobs = nearby
        .filter { it !is PokemonEntity } // exclui pokémons da lista de mobs
        .map { it.type.description.string }
        .groupingBy { it }
        .eachCount()
        .entries
        .joinToString { (mob, count) ->
            if (count > 1) "$count x $mob" else mob
        }
        .ifEmpty { "nenhum" }

    val nearbyItemsList = level.getEntitiesOfClass(
        ItemEntity::class.java,
        player.boundingBox.inflate(8.0)
    )

    val nearbyPokemon = level.getEntitiesOfClass(
        PokemonEntity::class.java,
        player.boundingBox.inflate(15.0) // raio médio
    )
        // exclui o time do jogador
        .filter { entity ->
            val ownerUuid = entity.pokemon.getOwnerUUID()
            ownerUuid == null || ownerUuid != player.uuid
        }
        .map { entity ->
            val poke = entity.pokemon
            val nickname = poke.nickname?.string ?: poke.species.resourceIdentifier.path
            val speciesName = poke.species.resourceIdentifier.path
            "$nickname ($speciesName)"
        }
        .groupingBy { it }
        .eachCount()
        .entries
        .joinToString { (poke, count) ->
            if (count > 1) "$count x $poke" else poke
        }
        .ifEmpty { "nenhum" }

    val nearbyPokemonEntities = level.getEntitiesOfClass(
        PokemonEntity::class.java,
        player.boundingBox.inflate(15.0) // raio médio
    )
        // exclui o time do jogador
        .filter { entity ->
            val ownerUuid = entity.pokemon.getOwnerUUID()
            ownerUuid == null || ownerUuid != player.uuid
        }


    val nearbyItems = if (nearbyItemsList.isNotEmpty()) {
        nearbyItemsList
            .map { itemEntity ->
                val stack = itemEntity.item
                val displayName = stack.displayName.string
                val id = BuiltInRegistries.ITEM.getKey(stack.item)
                "$displayName (${id})"
            }
            .groupingBy { it }
            .eachCount()
            .entries
            .joinToString { (item, count) ->
                if (count > 1) "$count x $item" else item
            }
    } else {
        "nenhum"
    }


    val specials = mutableSetOf<String>()
    BlockPos.betweenClosed(pos.offset(-5, -1, -5), pos.offset(5, 1, 5)).forEach { bp ->
        val blockId = level.getBlockState(bp).block.descriptionId
        if (blockId.contains("lava") || blockId.contains("water") ||
            blockId.contains("flower") || blockId.contains("ore") || blockId.contains("berry")) {
            specials.add(blockId.removePrefix("block.minecraft."))
        }
    }
    val specialBlocks = if (specials.isEmpty()) "nenhum" else specials.joinToString(", ")

    val health = player.health
    val maxHealth = player.maxHealth
    val armor = player.armorValue

    val mainHand = if (!player.mainHandItem.isEmpty)
        player.mainHandItem.item.descriptionId.removePrefix("item.minecraft.")
    else "vazio"

    val offHand = if (!player.offhandItem.isEmpty)
        player.offhandItem.item.descriptionId.removePrefix("item.minecraft.")
    else "vazio"

    return WorldContext(
        playerName,
        biome,
        timeOfDay,
        weather,
        lightLevel,
        blockUnder,
        timeLabel,
        terrainHint,
        nearbyEntities,
        nearbyMobs,
        nearbyPokemon,
        nearbyPokemonEntities,
        nearbyItems,
        specialBlocks,
        health,
        maxHealth,
        armor,
        mainHand,
        offHand,
    )
}

object MemoryStore {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private fun file(server: MinecraftServer): Path {
        val dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data")
        Files.createDirectories(dataDir)
        val f = dataDir.resolve("cobblebrain_memories.json")
        println(">>> memoryFile path: $f (exists=${Files.exists(f)})")
        return f
    }

    fun savePokemonMemory(server: MinecraftServer?, line: String, maxMemories: Int) {
        val s = server ?: run {
            println(">>> server is null, aborting save")
            return
        }

        println(">>> savePokemonMemory called with line='$line'")
        val regex = Regex("""(@{1,2})(?:Pokemon\s+)?([^:]+):\s*(.+)""")
        val match = regex.find(line) ?: run {
            println(">>> regex did not match, skipping")
            return
        }

        val marker = match.groupValues[1] // "@" ou "@@"
        val nameFromLine = match.groupValues[2].trim()
        val memory = match.groupValues[3].trim()
        val ativos = s.playerList.players.flatMap { PokemonQuery.findActivePokemon(it) }
        val alvo = ativos.firstOrNull { poke ->
            val nick = poke.nickname?.string
            (nick != null && nick.equals(nameFromLine, ignoreCase = true)) ||
                    poke.species.name.equals(nameFromLine, ignoreCase = true)
        } ?: run {
            println(">>> no active Pokémon found for '$nameFromLine'")
            return
        }

        val f = file(s)
        try {
            val gson = GsonBuilder().setPrettyPrinting().create()
            val root = if (Files.exists(f)) {
                gson.fromJson(Files.readString(f), JsonObject::class.java) ?: JsonObject()
            } else JsonObject()

            val arr = root.getAsJsonArray("pokemon_memories") ?: JsonArray().also {
                root.add("pokemon_memories", it)
            }

            val uuidStr = alvo.uuid.toString()
            val existing = arr.firstOrNull { it.asJsonObject["uuid"].asString == uuidStr }?.asJsonObject
            val obj = existing ?: JsonObject().apply {
                addProperty("uuid", uuidStr)
                addProperty("nickname", alvo.nickname?.string ?: "")
                addProperty("species", alvo.species.name)
                add("short_term", JsonArray())
                add("long_term", JsonArray())
                arr.add(this)
            }

            val targetArray = if (marker == "@@") {
                obj.getAsJsonArray("long_term")
            } else {
                obj.getAsJsonArray("short_term")
            }

            if (targetArray.none { it.asString == memory }) targetArray.add(memory)

            // só aplica limite em curto prazo
            if (marker == "@" && maxMemories > 0) {
                while (targetArray.size() > maxMemories) targetArray.remove(0)
            }

            if (marker == "@@" && config.maxLongMemory > 0) {
                while (targetArray.size() > config.maxLongMemory) targetArray.remove(0)
            }


            Files.writeString(f, gson.toJson(root))
            println(">>> wrote memories.json (exists=${Files.exists(f)})")
        } catch (e: Exception) {
            println(">>> ERROR writing memories.json: ${e.message}")
            e.printStackTrace()
        }
    }


    fun loadPokemonMemories(server: MinecraftServer, uuid: String, maxMemories: Int): List<String> {
        val f = file(server)
        if (!Files.exists(f)) return emptyList()

        val root = gson.fromJson(Files.readString(f), JsonObject::class.java) ?: return emptyList()
        val arr = root.getAsJsonArray("pokemon_memories") ?: return emptyList()

        val obj = arr.firstOrNull { it.asJsonObject["uuid"].asString == uuid }?.asJsonObject
            ?: return emptyList()

        val shortTerm = obj.getAsJsonArray("short_term")?.map { it.asString } ?: emptyList()
        val longTerm = obj.getAsJsonArray("long_term")?.map { it.asString } ?: emptyList()

        val shortLimited = if (maxMemories > 0) shortTerm.takeLast(maxMemories) else shortTerm
        val longLimited = if (config.maxLongMemory > 0) longTerm.takeLast(config.maxLongMemory) else longTerm

        val result = mutableListOf<String>()
        if (shortLimited.isNotEmpty()) {
            result.add("SHORT-TERM MEMORIES")
            result.addAll(shortLimited)
        }
        if (longLimited.isNotEmpty()) {
            result.add("IMPORTANT / CHARACTERISTIC MEMORIES")
            result.addAll(longLimited)
        }

        return result
    }
}