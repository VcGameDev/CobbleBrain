package vito.cobblebrain.engine

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import vito.cobblebrain.social.PokemonQuery
import java.util.concurrent.ConcurrentHashMap

data class TaggedBlockLocation(
    val dimensionId: String,
    val pos: BlockPos
)

object StoryTagManager {

    private val worldBlockTags = ConcurrentHashMap<String, TaggedBlockLocation>()

    fun setBlockTag(tagName: String, level: ServerLevel, pos: BlockPos) {
        val safeTag = tagName.trim()
        if (safeTag.isBlank()) return
        val dimId = level.dimension().location().toString()
        worldBlockTags[safeTag] = TaggedBlockLocation(dimId, pos)
    }

    fun removeBlockTag(tagName: String) {
        val safeTag = tagName.trim()
        if (safeTag.isBlank()) return
        worldBlockTags.remove(safeTag)
    }

    fun clearBlockTags() {
        worldBlockTags.clear()
    }

    fun getBlockLocation(tagName: String): TaggedBlockLocation? {
        val safeTag = tagName.trim()
        if (safeTag.isBlank()) return null
        return worldBlockTags[safeTag]
    }

    fun getBlockPos(tagName: String): BlockPos? {
        return getBlockLocation(tagName)?.pos
    }

    fun getBlockVec3(tagName: String): Vec3? {
        val loc = getBlockLocation(tagName) ?: return null
        return Vec3(loc.pos.x + 0.5, loc.pos.y + 0.5, loc.pos.z + 0.5)
    }

    fun resolveTargetEntity(
        player: ServerPlayer?,
        server: MinecraftServer,
        selector: String,
        identifier: String
    ): LivingEntity? {
        val level = player?.serverLevel() ?: server.overworld()

        return when (selector.uppercase()) {
            "CLOSEST_MOB", "NEAREST_MOB", "NEAREST" -> {
                if (player == null) return null
                val radius = identifier.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 16.0
                val box = player.boundingBox.inflate(radius)
                val candidates = level.getEntitiesOfClass(LivingEntity::class.java, box) {
                    it != player && it.isAlive
                }
                candidates.minByOrNull { it.distanceToSqr(player) }
            }
            "LOOKING_AT_MOB", "CROSSHAIR_MOB", "LOOK_AT" -> {
                if (player == null) return null
                val maxDist = identifier.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 32.0
                raycastEntity(player, maxDist)
            }
            "BY_EXISTING_TAG", "EXISTING_TAG", "TAG" -> {
                val tag = identifier.trim()
                if (tag.isBlank()) return null
                val box = player?.boundingBox?.inflate(128.0) ?: AABB(-1000.0, -100.0, -1000.0, 1000.0, 300.0, 1000.0)
                val levels = player?.let { listOf(it.serverLevel()) } ?: server.allLevels.toList()
                for (lvl in levels) {
                    val candidate = lvl.getEntitiesOfClass(LivingEntity::class.java, box) {
                        it.isAlive && it.tags.contains(tag)
                    }.minByOrNull { if (player != null) it.distanceToSqr(player) else 0.0 }
                    if (candidate != null) return candidate
                }
                null
            }
            "PLAYER_POKEMON_SLOT", "POKEMON_SLOT", "PARTY_SLOT" -> {
                if (player == null) return null
                val slotIdx = (identifier.toIntOrNull() ?: 0).coerceIn(0, 5)
                try {
                    val party = Cobblemon.storage.getParty(player)
                    val poke = party.get(slotIdx)
                    val ent = poke?.entity
                    if (ent != null && ent.isAlive) return ent
                } catch (_: Exception) {}

                try {
                    val activeList = PokemonQuery.findActivePokemon(player)
                    val ent = activeList.getOrNull(slotIdx)?.entity ?: activeList.firstOrNull()?.entity
                    if (ent != null && ent.isAlive) return ent
                } catch (_: Exception) {}
                null
            }
            else -> {
                // Fallback: search by tag or closest mob
                val tag = identifier.trim()
                if (tag.isNotBlank()) {
                    val box = player?.boundingBox?.inflate(64.0) ?: AABB(-500.0, -100.0, -500.0, 500.0, 300.0, 500.0)
                    level.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive && it.tags.contains(tag) }.firstOrNull()
                } else null
            }
        }
    }

    fun resolveTargetBlock(
        player: ServerPlayer?,
        server: MinecraftServer,
        selector: String,
        identifier: String
    ): Pair<ServerLevel, BlockPos>? {
        val level = player?.serverLevel() ?: server.overworld()

        return when (selector.uppercase()) {
            "LOOKING_AT_BLOCK", "CROSSHAIR_BLOCK", "LOOK_AT" -> {
                if (player == null) return null
                val maxDist = identifier.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 32.0
                val start = player.eyePosition
                val end = start.add(player.lookAngle.scale(maxDist))
                val context = ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)
                val hit = level.clip(context)
                if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) {
                    Pair(level, hit.blockPos)
                } else null
            }
            "BLOCK_UNDER_PLAYER", "UNDER_PLAYER", "GROUND" -> {
                if (player == null) return null
                Pair(level, player.blockPosition().below())
            }
            "COORDINATES", "POSITION", "POS", "COORDS" -> {
                val coords = CoordinateResolver.resolveVec3(identifier, player, server)
                val targetPos = BlockPos.containing(coords.x, coords.y, coords.z)
                Pair(level, targetPos)
            }
            else -> {
                // Check if identifier is coordinates or anchor
                if (identifier.contains(" ") || identifier.contains(",") || identifier.startsWith("~") || identifier.startsWith("^") || identifier.startsWith("@")) {
                    val coords = CoordinateResolver.resolveVec3(identifier, player, server)
                    Pair(level, BlockPos.containing(coords.x, coords.y, coords.z))
                } else {
                    // Default to block under player or look target
                    if (player != null) Pair(level, player.blockPosition().below()) else null
                }
            }
        }
    }

    private fun raycastEntity(player: ServerPlayer, maxDist: Double): LivingEntity? {
        val start = player.eyePosition
        val look = player.lookAngle
        val end = start.add(look.scale(maxDist))
        val box = player.boundingBox.expandTowards(look.scale(maxDist)).inflate(1.0)
        val candidates = player.serverLevel().getEntitiesOfClass(LivingEntity::class.java, box) {
            it != player && it.isAlive
        }

        var closestEntity: LivingEntity? = null
        var closestDistSqr = maxDist * maxDist

        for (candidate in candidates) {
            val cBox = candidate.boundingBox.inflate(candidate.pickRadius.toDouble().coerceAtLeast(0.3))
            val clipOpt = cBox.clip(start, end)
            if (clipOpt.isPresent) {
                val dSqr = start.distanceToSqr(clipOpt.get())
                if (dSqr < closestDistSqr) {
                    closestDistSqr = dSqr
                    closestEntity = candidate
                }
            }
        }
        return closestEntity
    }

    fun parseCoordinates(str: String?, base: Vec3): Vec3 {
        return CoordinateResolver.resolveVec3(str, null, null, base)
    }
}
