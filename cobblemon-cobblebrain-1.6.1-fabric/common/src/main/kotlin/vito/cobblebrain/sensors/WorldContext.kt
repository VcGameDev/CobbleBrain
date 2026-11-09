package vito.cobblebrain.sensors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.level.LightLayer
import vito.cobblebrain.config.ConfigHandler.config

data class WorldContext(
    val playerName: String,
    val biome: String,
    val timeOfDay: Long,
    val weather: String,
    val dimension: String,
    val lightLevel: Int,
    val blockUnder: String,
    val timeLabel: String,
    val terrainHint: String,
    val nearbyEntities: Int,
    val nearbyMobs: String,
    val hostileMobs: Boolean,
    val nearbyPokemon: String,
    val nearbyPokemonEntities: List<PokemonEntity>,
    val nearbyItems: String,
    val specialBlocks: String,
    val health: Float,
    val maxHealth: Float,
    val armor: Int,
    val mainHand: String,
    val offHand: String,
    val sensoryData: SensoryData = SensoryData(),
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
        !level.dimensionType().hasSkyLight() -> "no weather"
        level.isThundering -> "thunderstorm"
        level.isRaining -> "rain"
        else -> "clear"
    }

    val dimension = when (level.dimension().location().path) {
        "overworld" -> "Overworld"
        "the_nether" -> "Nether"
        "the_end" -> "The End"
        else -> level.dimension().location().path
    }

    val lightLevel = level.getBrightness(LightLayer.BLOCK, pos)
    val blockUnder = level.getBlockState(pos.below()).block.descriptionId

    val timeLabel = when (timeOfDay) {
        in 0..1000 -> "sunrise"
        in 1001..5000 -> "morning"
        in 5001..7000 -> "near noon"
        in 7001..10000 -> "afternoon"
        in 10001..12000 -> "late afternoon"
        in 12001..13000 -> "sunset"
        in 13001..18000 -> "night"
        else -> "before dawn"
    }

    val y = player.blockY
    val sensoryData = SensoryScanner.scan(player)

    val terrainHint = when {
        sensoryData.structureName != null -> "inside ${sensoryData.structureName}"
        sensoryData.environmentType == "cave" -> "inside a cave"
        sensoryData.environmentType == "house/room" -> "inside a house"
        lightLevel < 7 && blockUnder.contains("stone") -> "inside a cave"
        y > 100 -> "on a mountain"
        else -> "in open terrain"
    }

    val nearby = level.getEntities(player, player.boundingBox.inflate(10.0))
        .filterIsInstance<LivingEntity>()
        .filter { it != player }

    val nearbyEntities = nearby.size

    val nearbyHostiles = nearby
        .filterIsInstance<Monster>()

    val hostileMobs = nearbyHostiles.isNotEmpty()

    val nearbyMobs = nearby
        .filter { it !is PokemonEntity }
        .map { it.type.description.string }
        .groupingBy { it }
        .eachCount()
        .entries
        .joinToString { (mob, count) ->
            if (count > 1) "$count x $mob" else mob
        }
        .ifEmpty { "none" }

    val nearbyItemsList = level.getEntitiesOfClass(
        ItemEntity::class.java,
        player.boundingBox.inflate(8.0)
    )

    val radius = 8.5

    val nearbyPokemonEntities = level.getEntitiesOfClass(
        PokemonEntity::class.java,
        player.boundingBox.inflate(radius)
    )
        .filter { entity ->
            val ownerUuid = entity.pokemon.getOwnerUUID()
            val isNotOwned = ownerUuid == null || ownerUuid != player.uuid

            val distance = entity.distanceTo(player)

            isNotOwned && distance <= radius
        }

    val nearbyPokemon = nearbyPokemonEntities
        .map { entity ->
            val poke = entity.pokemon
            val rawName = poke.nickname?.string?.takeIf { it.isNotBlank() } ?: poke.species.resourceIdentifier.path
            val speciesName = rawName.replaceFirstChar { it.uppercase() }
            val uuid4 = entity.uuid.toString().substring(0, 4)
            val isHostile = CommandState.activeCommands[entity.uuid] == "hostile"
            val isIrritated = CommandState.isWildIrritated(entity.uuid)
            when {
                isHostile -> "${speciesName}_$uuid4 (HOSTILE)"
                isIrritated -> "${speciesName}_$uuid4 (IRRITATED)"
                else -> "${speciesName}_$uuid4"
            }
        }
        .joinToString(", ")
        .ifEmpty { "none" }

    val maxItems = try { config.maxNotableBlocks } catch (_: Throwable) { 10 }
    val nearbyItems = if (nearbyItemsList.isNotEmpty() && maxItems > 0) {
        nearbyItemsList
            .map { itemEntity ->
                val stack = itemEntity.item
                val displayName = stack.displayName.string
                val id = BuiltInRegistries.ITEM.getKey(stack.item)
                BuiltInRegistries.ITEM.getKey(stack.item).path
            }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(maxItems)
            .joinToString { (item, count) ->
                if (count > 1) "$count x $item" else item
            }
    } else {
        "none"
    }


    val specials = mutableSetOf<String>()
    BlockPos.betweenClosed(pos.offset(-5, -1, -5), pos.offset(5, 1, 5)).forEach { bp ->
        val blockId = level.getBlockState(bp).block.descriptionId
        if (blockId.contains("lava") || blockId.contains("water") ||
            blockId.contains("flower") || blockId.contains("ore") || blockId.contains("berry")) {
            specials.add(blockId.removePrefix("block.minecraft."))
        }
    }
    val specialBlocks = if (specials.isEmpty()) "" else specials.joinToString(", ")

    val health = player.health
    val maxHealth = player.maxHealth
    val armor = player.armorValue

    val mainHand = if (!player.mainHandItem.isEmpty)
        player.mainHandItem.item.descriptionId.removePrefix("item.minecraft.")
    else "none"

    val offHand = if (!player.offhandItem.isEmpty)
        player.offhandItem.item.descriptionId.removePrefix("item.minecraft.")
    else "none"

    return WorldContext(
        playerName,
        biome,
        timeOfDay,
        weather,
        dimension,
        lightLevel,
        blockUnder,
        timeLabel,
        terrainHint,
        nearbyEntities,
        nearbyMobs,
        hostileMobs,
        nearbyPokemon,
        nearbyPokemonEntities,
        nearbyItems,
        specialBlocks,
        health,
        maxHealth,
        armor,
        mainHand,
        offHand,
        sensoryData = sensoryData
    )
}

data class SensoryData(
    val isSheltered: Boolean = false,
    val ceilingBlock: String? = null,
    val ceilingDistance: Int = 0,
    val skyLight: Int = 15,
    val blockLight: Int = 0,
    val isRainingOutside: Boolean = false,
    val isDirectlyRainingOnPlayer: Boolean = false,
    val environmentType: String = "open terrain",
    val structureName: String? = null,
    val notableBlocks: List<String> = emptyList(),
    val dominantBlocks: List<String> = emptyList()
) {
    fun toPromptString(lowTokenMode: Boolean = false): String {
        val lines = mutableListOf<String>()

        val shelterDesc = when {
            isSheltered && ceilingBlock != null -> "Blocked (ceiling ${ceilingDistance}m above: $ceilingBlock)"
            isSheltered -> "Blocked (sheltered overhead)"
            else -> "Open Sky"
        }
        lines += "Shelter: $shelterDesc | Sky Light: $skyLight, Block Light: $blockLight"

        val envParts = mutableListOf<String>()
        if (structureName != null) {
            envParts += "Structure: $structureName"
        }
        val weatherFeel = when {
            isDirectlyRainingOnPlayer -> "Exposed (rain falling directly on you)"
            isRainingOutside && isSheltered -> "Dry & sheltered indoors (hearing rain outside)"
            isRainingOutside -> "Raining nearby"
            else -> null
        }
        if (weatherFeel != null) {
            envParts += weatherFeel
        }
        val envFeel = when {
            environmentType.startsWith("underground structure") -> "Underground structure"
            environmentType == "cave" -> "Underground cavern"
            environmentType == "house/room" -> "Inside a built shelter/room"
            else -> null
        }
        if (envFeel != null) {
            envParts += envFeel
        }
        if (envParts.isNotEmpty()) {
            lines += "Environment: ${envParts.joinToString(" | ")}"
        }

        if (!lowTokenMode) {
            if (notableBlocks.isNotEmpty()) {
                lines += "Notable Blocks: ${notableBlocks.joinToString(", ")}"
            }
            if (dominantBlocks.isNotEmpty()) {
                lines += "Dominant Blocks: ${dominantBlocks.joinToString(", ")}"
            }
        }

        return lines.joinToString("\n")
    }
}

object SensoryScanner {

    fun formatStructureName(raw: String): String {
        return when {
            raw.contains("village") -> {
                val type = raw.removePrefix("village_").replaceFirstChar { it.uppercase() }
                if (type.isNotBlank() && type != raw) "$type Village" else "Village"
            }
            raw.contains("woodland_mansion") || raw == "mansion" -> "Woodland Mansion"
            raw.contains("mineshaft") -> "Abandoned Mineshaft"
            raw.contains("ancient_city") -> "Ancient City"
            raw.contains("fortress") -> "Nether Fortress"
            raw.contains("bastion") -> "Bastion Remnant"
            raw.contains("desert_pyramid") -> "Desert Pyramid"
            raw.contains("jungle_pyramid") -> "Jungle Temple"
            raw.contains("stronghold") -> "Stronghold"
            raw.contains("outpost") -> "Pillager Outpost"
            raw.contains("swamp_hut") -> "Witch Hut"
            raw.contains("monument") -> "Ocean Monument"
            raw.contains("end_city") -> "End City"
            raw.contains("trial_chambers") -> "Trial Chambers"
            else -> raw.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
    }

    private fun getBlockWeight(path: String): Int {
        return when {
            // Weight 100: Legendary / Supernatural / Portals / Boss / Vault
            path == "dragon_egg" || path == "beacon" || path == "ancient_debris" ||
            path.contains("spawner") || path.contains("portal") || path == "conduit" ||
            path == "enchanting_table" || path == "respawn_anchor" || path == "vault" ||
            path == "heavy_core" || path == "lodestone" -> 100

            // Weight 30: Human Utility / Furniture / Workstations / Bases
            path.contains("chest") || path.contains("barrel") || path.contains("furnace") ||
            path == "smoker" || path == "crafting_table" || path.contains("anvil") ||
            path == "brewing_stand" || path == "cauldron" || path.endsWith("_bed") ||
            path.contains("campfire") || path.contains("bookshelf") || path == "loom" ||
            path == "smithing_table" || path == "cartography_table" || path == "fletching_table" ||
            path == "grindstone" || path == "stonecutter" || path == "bell" || path == "jukebox" ||
            path == "lectern" || path.contains("shulker_box") -> 30

            // Weight 15: Valuables / Rare Ores / Hazards / Unusual Environment
            path.contains("diamond_ore") || path.contains("emerald_ore") || path.contains("gold_ore") ||
            path.contains("debris") || path == "lava" || path.contains("fire") ||
            path.contains("ice") || path.contains("sculk") || path == "tnt" ||
            path == "cobweb" || path.contains("dripstone") || path.contains("amethyst_cluster") ||
            path.contains("sponge") -> 15

            // Weight 5: Common Ores / Lights / Plants / Crops
            path.contains("ore") || path.contains("lantern") || path.contains("torch") ||
            path.contains("crop") || path.contains("berry") || path == "cake" ||
            path.contains("flower") || path == "hay_block" -> 5

            // Weight 1: Basic Structural / Terrain Masses
            path.contains("planks") || path.contains("stone") || path.contains("deepslate") ||
            path.contains("bricks") || path.contains("glass") || path.contains("dirt") ||
            path.contains("grass_block") || path.contains("sand") || path.contains("gravel") ||
            path.contains("water") || path.contains("terracotta") || path.contains("concrete") ||
            path.contains("wood") || path.contains("log") || path.contains("slab") ||
            path.contains("stairs") || path.contains("wool") -> 1

            else -> 0
        }
    }

    fun scan(player: ServerPlayer): SensoryData {
        val level = player.level() as net.minecraft.server.level.ServerLevel
        val pos = player.blockPosition()

        // 1. Raycast upwards for ceiling / shelter
        var isSheltered = false
        var ceilingBlock: String? = null
        var ceilingDistance = 0
        val maxCeilCheck = minOf(pos.y + 20, level.maxBuildHeight)
        for (y in (pos.y + 2)..maxCeilCheck) {
            val checkPos = BlockPos(pos.x, y, pos.z)
            val state = level.getBlockState(checkPos)
            if (!state.isAir) {
                isSheltered = true
                ceilingBlock = BuiltInRegistries.BLOCK.getKey(state.block).path
                ceilingDistance = y - (pos.y + 1)
                break
            }
        }
        if (!isSheltered && !level.canSeeSky(pos)) {
            isSheltered = true
        }

        // 2. Lighting
        val skyLight = level.getBrightness(LightLayer.SKY, pos)
        val blockLight = level.getBrightness(LightLayer.BLOCK, pos)

        // 3. Rain & weather exposure
        val isDimensionOverworld = level.dimension().location().path == "overworld"
        val isRainingOutside = level.isRaining && isDimensionOverworld
        val isDirectlyRainingOnPlayer = level.isRainingAt(pos)

        // 4. Structure detection
        var structureName: String? = null
        try {
            val structures = level.structureManager().getAllStructuresAt(pos)
            if (structures.isNotEmpty()) {
                val struct = structures.keys.firstOrNull()
                if (struct != null) {
                    val key = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(struct)
                    if (key != null) {
                        structureName = formatStructureName(key.path)
                    }
                }
            }
        } catch (_: Throwable) {}

        // 5. Environment type
        val surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.x, pos.z)
        val isUnderground = pos.y < surfaceY - 8 && !level.canSeeSky(pos)
        val environmentType = when {
            structureName != null && isUnderground -> "underground structure"
            structureName != null -> "structure ($structureName)"
            isUnderground -> "cave"
            isSheltered -> "house/room"
            else -> "open terrain"
        }

        // 6. Block scanning with Importance x Quantity
        val blockCounts = mutableMapOf<String, Int>()
        BlockPos.betweenClosed(pos.offset(-5, -2, -5), pos.offset(5, 3, 5)).forEach { bp ->
            val state = level.getBlockState(bp)
            if (!state.isAir) {
                val key = BuiltInRegistries.BLOCK.getKey(state.block).path
                val w = getBlockWeight(key)
                if (w > 0) {
                    blockCounts[key] = (blockCounts[key] ?: 0) + 1
                }
            }
        }

        // Notable Objects (weight >= 15)
        val maxNotable = try { config.maxNotableBlocks } catch (_: Throwable) { 10 }
        val notableBlocks = if (maxNotable <= 0) emptyList() else blockCounts.filter { (k, _) -> getBlockWeight(k) >= 15 }
            .map { (k, count) -> Triple(k, count, getBlockWeight(k) * count) }
            .sortedByDescending { it.third }
            .take(maxNotable.coerceIn(1, 10))
            .map { "${it.second}x ${it.first}" }

        // Dominant Blocks (weight <= 5, requires count >= 25)
        val dominantBlocks = blockCounts.filter { (k, count) -> getBlockWeight(k) <= 5 && count >= 25 }
            .toList()
            .sortedByDescending { it.second }
            .take(2)
            .map { "${it.first} (${it.second})" }

        return SensoryData(
            isSheltered = isSheltered,
            ceilingBlock = ceilingBlock,
            ceilingDistance = ceilingDistance,
            skyLight = skyLight,
            blockLight = blockLight,
            isRainingOutside = isRainingOutside,
            isDirectlyRainingOnPlayer = isDirectlyRainingOnPlayer,
            environmentType = environmentType,
            structureName = structureName,
            notableBlocks = notableBlocks,
            dominantBlocks = dominantBlocks
        )
    }
}
