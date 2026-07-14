package vito.cobblebrain.sensors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.level.LightLayer

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
    val terrainHint = when {
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

    val nearbyPokemon = level.getEntitiesOfClass(
        PokemonEntity::class.java,
        player.boundingBox.inflate(radius)
    )
        .filter { entity ->
            val ownerUuid = entity.pokemon.getOwnerUUID()
            val isNotOwned = ownerUuid == null || ownerUuid != player.uuid

            val distance = entity.distanceTo(player)

            isNotOwned && distance <= radius
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
        .ifEmpty { "none" }


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
    val specialBlocks = if (specials.isEmpty()) "none" else specials.joinToString(", ")

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
    )
}
