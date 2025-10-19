package vito.cobblebrain.sensors

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.LightLayer

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
        player.boundingBox.inflate(8.0) // raio de 8 blocos ao redor
    )
    val nearbyItems = if (nearbyItemsList.isNotEmpty()) {
        nearbyItemsList.joinToString { itemEntity ->
            val stack = itemEntity.item
            val displayName = stack.displayName.string
            val id = BuiltInRegistries.ITEM.getKey(stack.item)
            "$displayName (${id}) x${stack.count}"
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
        nearbyItems,
        specialBlocks,
        health,
        maxHealth,
        armor,
        mainHand,
        offHand,
    )
}
