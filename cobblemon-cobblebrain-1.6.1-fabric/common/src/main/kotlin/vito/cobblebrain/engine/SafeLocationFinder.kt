package vito.cobblebrain.engine

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

enum class SearchLayerPriority {
    CLOSEST,
    SURFACE,
    UNDERGROUND,
    RANDOM;

    companion object {
        fun fromString(str: String?): SearchLayerPriority {
            return when (str?.uppercase()?.trim()) {
                "SURFACE" -> SURFACE
                "UNDERGROUND", "CAVES", "CAVE" -> UNDERGROUND
                "RANDOM" -> RANDOM
                else -> CLOSEST
            }
        }
    }
}

object SafeLocationFinder {

    /**
     * Finds a safe position for entities or blocks with layer priority, ground snapping, and suffocation avoidance.
     */
    fun findSafePosition(
        level: ServerLevel,
        originVec: Vec3,
        safePosition: Boolean = true,
        snapToGround: Boolean = true,
        maxSearchRadius: Int = 5,
        searchPriority: SearchLayerPriority = SearchLayerPriority.CLOSEST
    ): Vec3 {
        val baseBlockPos = BlockPos.containing(originVec.x, originVec.y, originVec.z)

        // 1. If safePosition is disabled and snapToGround is disabled, return origin directly
        if (!safePosition && !snapToGround) {
            return originVec
        }

        // 2. If snapToGround only (no obstacle clearance search required)
        if (!safePosition && snapToGround) {
            val groundY = findGroundY(level, baseBlockPos, 16)
            return if (groundY != null) Vec3(originVec.x, groundY, originVec.z) else originVec
        }

        // 3. Check if base position is already safe and satisfies search priority
        if (searchPriority == SearchLayerPriority.CLOSEST && isSafeSpot(level, baseBlockPos, snapToGround)) {
            if (snapToGround) {
                val groundY = findGroundY(level, baseBlockPos, 16)
                if (groundY != null) return Vec3(originVec.x, groundY, originVec.z)
            }
            return originVec
        }

        // 4. Gather valid safe spots in search radius
        val radius = maxSearchRadius.coerceIn(1, 16)
        val validCandidates = mutableListOf<BlockPos>()

        for (r in 1..radius) {
            for (dx in -r..r) {
                for (dz in -r..r) {
                    if (abs(dx) != r && abs(dz) != r) continue

                    for (dy in -3..3) {
                        val candidate = baseBlockPos.offset(dx, dy, dz)
                        if (isSafeSpot(level, candidate, snapToGround)) {
                            validCandidates.add(candidate)
                        }
                    }
                }
            }
            // For CLOSEST mode, stop after the first non-empty radius ring for efficiency
            if (searchPriority == SearchLayerPriority.CLOSEST && validCandidates.isNotEmpty()) {
                break
            }
        }

        // Check base position if it was valid
        if (isSafeSpot(level, baseBlockPos, snapToGround)) {
            validCandidates.add(0, baseBlockPos)
        }

        val chosenPos: BlockPos? = when (searchPriority) {
            SearchLayerPriority.CLOSEST -> {
                validCandidates.minByOrNull { it.distToCenterSqr(originVec.x, originVec.y, originVec.z) }
            }
            SearchLayerPriority.SURFACE -> {
                val surfaceCandidates = validCandidates.filter { level.canSeeSky(it.above()) }
                surfaceCandidates.minByOrNull { it.distToCenterSqr(originVec.x, originVec.y, originVec.z) }
                    ?: validCandidates.minByOrNull { it.distToCenterSqr(originVec.x, originVec.y, originVec.z) }
            }
            SearchLayerPriority.UNDERGROUND -> {
                val undergroundCandidates = validCandidates.filter {
                    !level.canSeeSky(it.above()) && hasCeiling(level, it, 10)
                }
                undergroundCandidates.minByOrNull { it.distToCenterSqr(originVec.x, originVec.y, originVec.z) }
                    ?: validCandidates.filter { !level.canSeeSky(it.above()) }.minByOrNull { it.distToCenterSqr(originVec.x, originVec.y, originVec.z) }
                    ?: validCandidates.minByOrNull { it.distToCenterSqr(originVec.x, originVec.y, originVec.z) }
            }
            SearchLayerPriority.RANDOM -> {
                if (validCandidates.isNotEmpty()) validCandidates.random() else null
            }
        }

        if (chosenPos != null) {
            val targetX = chosenPos.x + (originVec.x - baseBlockPos.x).coerceIn(0.1, 0.9)
            val targetZ = chosenPos.z + (originVec.z - baseBlockPos.z).coerceIn(0.1, 0.9)
            if (snapToGround) {
                val groundY = findGroundY(level, chosenPos, 8)
                if (groundY != null) return Vec3(targetX, groundY, targetZ)
            }
            return Vec3(targetX, chosenPos.y.toDouble(), targetZ)
        }

        // Fallback: if snapping enabled, try ground raycast from original position
        if (snapToGround) {
            val groundY = findGroundY(level, baseBlockPos, 16)
            if (groundY != null) return Vec3(originVec.x, groundY, originVec.z)
        }

        return originVec
    }

    private fun hasCeiling(level: Level, pos: BlockPos, maxUp: Int): Boolean {
        for (i in 2..maxUp) {
            val checkPos = pos.above(i)
            val state = level.getBlockState(checkPos)
            if (!state.isAir && isSolidGround(level, checkPos, state)) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a BlockPos has 2 vertical passable blocks (feet + head) and non-hazardous footing.
     */
    fun isSafeSpot(level: Level, pos: BlockPos, requireGround: Boolean): Boolean {
        // Feet block
        val feetState = level.getBlockState(pos)
        val feetFluid = level.getFluidState(pos)
        if (!isPassable(level, pos, feetState) || !feetFluid.isEmpty) return false

        // Head block (suffocation check)
        val headPos = pos.above()
        val headState = level.getBlockState(headPos)
        val headFluid = level.getFluidState(headPos)
        if (!isPassable(level, headPos, headState) || !headFluid.isEmpty) return false

        // Floor / ground support check
        val floorPos = pos.below()
        val floorState = level.getBlockState(floorPos)
        val floorFluid = level.getFluidState(floorPos)

        if (!floorFluid.isEmpty) return false // No lava or water footing

        return if (requireGround) {
            isSolidGround(level, floorPos, floorState)
        } else {
            true
        }
    }

    /**
     * Returns true if an entity can stand inside this block without colliding/suffocating.
     */
    private fun isPassable(level: Level, pos: BlockPos, state: BlockState): Boolean {
        if (state.isAir) return true
        val shape = state.getCollisionShape(level, pos)
        return shape.isEmpty
    }

    /**
     * Returns true if this block can support entity footing (solid, non-collapsing, not air).
     */
    private fun isSolidGround(level: Level, pos: BlockPos, state: BlockState): Boolean {
        if (state.isAir) return false
        val shape = state.getCollisionShape(level, pos)
        return !shape.isEmpty && shape.bounds().maxY >= 0.8
    }

    /**
     * Scans downwards from startPos to locate the nearest solid surface Y level.
     */
    private fun findGroundY(level: Level, startPos: BlockPos, maxDepth: Int): Double? {
        var curr = startPos
        for (i in 0..maxDepth) {
            val below = curr.below()
            val stateBelow = level.getBlockState(below)
            val fluidBelow = level.getFluidState(below)

            if (!fluidBelow.isEmpty) return null // Do not snap into liquid/lava

            if (isSolidGround(level, below, stateBelow)) {
                // Ensure feet and head at curr are passable
                if (isPassable(level, curr, level.getBlockState(curr)) && isPassable(level, curr.above(), level.getBlockState(curr.above()))) {
                    return below.y + 1.0
                }
            }
            curr = below
            if (curr.y <= level.minBuildHeight) break
        }
        return null
    }
}
