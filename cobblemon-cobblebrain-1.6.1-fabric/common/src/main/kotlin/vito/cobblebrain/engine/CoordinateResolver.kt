package vito.cobblebrain.engine

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object CoordinateResolver {

    /**
     * Resolves a coordinate string into a Vec3 using absolute, relative (~), view-local (^),
     * or named-anchor (@tag:name) notation.
     */
    fun resolveVec3(
        rawStr: String?,
        sourceEntity: LivingEntity? = null,
        server: MinecraftServer? = null,
        defaultOrigin: Vec3 = sourceEntity?.position() ?: Vec3.ZERO
    ): Vec3 {
        if (rawStr == null || rawStr.isBlank()) return defaultOrigin

        var workingStr = rawStr.trim()
        var origin = defaultOrigin
        var sourceYaw = sourceEntity?.yRot ?: 0f
        var sourcePitch = sourceEntity?.xRot ?: 0f

        // 1. Check for Named Anchor Prefix: e.g. "@tag:guide_npc ~0 ~1 ~2" or "@guide_npc ^0 ^0 ^3"
        if (workingStr.startsWith("@")) {
            val spaceIdx = workingStr.indexOf(' ')
            val anchorToken = if (spaceIdx != -1) workingStr.substring(0, spaceIdx) else workingStr
            workingStr = if (spaceIdx != -1) workingStr.substring(spaceIdx + 1).trim() else "~ ~ ~"

            val anchorTag = anchorToken.removePrefix("@").removePrefix("tag:").removePrefix("mob:").trim()
            val resolvedAnchor = resolveAnchor(anchorTag, sourceEntity, server)
            if (resolvedAnchor != null) {
                origin = resolvedAnchor.first
                sourceYaw = resolvedAnchor.second
                sourcePitch = resolvedAnchor.third
            }
        }

        if (workingStr.isBlank()) return origin

        // Split coordinates by space or comma
        val parts = workingStr.split("[,\\s]+".toRegex()).filter { it.isNotBlank() }
        if (parts.isEmpty()) return origin

        val isLocalCoords = parts.any { it.startsWith("^") }

        return if (isLocalCoords) {
            // View-Local Caret Notation (^Left/Right, ^Up/Down, ^Forward/Backward)
            val left = parseCaretPart(parts.getOrNull(0))
            val up = parseCaretPart(parts.getOrNull(1))
            val forward = parseCaretPart(parts.getOrNull(2))
            resolveLocalOffset(origin, sourceYaw, sourcePitch, left, up, forward)
        } else {
            // World-Relative (~) or Absolute
            val tx = parseTildeOrAbsolutePart(parts.getOrNull(0), origin.x)
            val ty = parseTildeOrAbsolutePart(parts.getOrNull(1), origin.y)
            val tz = parseTildeOrAbsolutePart(parts.getOrNull(2), origin.z)
            Vec3(tx, ty, tz)
        }
    }

    /**
     * Resolves a coordinate string to a BlockPos.
     */
    fun resolveBlockPos(
        rawStr: String?,
        sourceEntity: LivingEntity? = null,
        server: MinecraftServer? = null,
        defaultOrigin: BlockPos = sourceEntity?.blockPosition() ?: BlockPos.ZERO
    ): BlockPos {
        val defaultVec = Vec3(defaultOrigin.x + 0.5, defaultOrigin.y.toDouble(), defaultOrigin.z + 0.5)
        val vec = resolveVec3(rawStr, sourceEntity, server, defaultVec)
        return BlockPos.containing(vec.x, vec.y, vec.z)
    }

    /**
     * Resolves coordinates and automatically applies wall collision clearance and ground snapping.
     */
    fun resolveSafeVec3(
        rawStr: String?,
        level: ServerLevel,
        sourceEntity: LivingEntity? = null,
        server: MinecraftServer? = null,
        defaultOrigin: Vec3 = sourceEntity?.position() ?: Vec3.ZERO,
        safePosition: Boolean = true,
        snapToGround: Boolean = true,
        maxSearchRadius: Int = 5,
        searchPriority: SearchLayerPriority = SearchLayerPriority.CLOSEST
    ): Vec3 {
        val baseVec = resolveVec3(rawStr, sourceEntity, server, defaultOrigin)
        return SafeLocationFinder.findSafePosition(level, baseVec, safePosition, snapToGround, maxSearchRadius, searchPriority)
    }

    /**
     * Resolves BlockPos with ground snapping and collision avoidance.
     */
    fun resolveSafeBlockPos(
        rawStr: String?,
        level: ServerLevel,
        sourceEntity: LivingEntity? = null,
        server: MinecraftServer? = null,
        defaultOrigin: BlockPos = sourceEntity?.blockPosition() ?: BlockPos.ZERO,
        safePosition: Boolean = true,
        snapToGround: Boolean = true,
        maxSearchRadius: Int = 5,
        searchPriority: SearchLayerPriority = SearchLayerPriority.CLOSEST
    ): BlockPos {
        val safeVec = resolveSafeVec3(rawStr, level, sourceEntity, server, Vec3(defaultOrigin.x.toDouble(), defaultOrigin.y.toDouble(), defaultOrigin.z.toDouble()), safePosition, snapToGround, maxSearchRadius, searchPriority)
        return BlockPos.containing(safeVec.x, safeVec.y, safeVec.z)
    }

    private fun resolveAnchor(
        tag: String,
        sourceEntity: LivingEntity?,
        server: MinecraftServer?
    ): Triple<Vec3, Float, Float>? {
        if (tag.equals("player", ignoreCase = true) && sourceEntity != null) {
            return Triple(sourceEntity.position(), sourceEntity.yRot, sourceEntity.xRot)
        }

        // Try to find entity with tag in current level or server levels
        val level = sourceEntity?.level() as? ServerLevel ?: server?.overworld()
        if (level != null) {
            val box = sourceEntity?.boundingBox?.inflate(128.0) ?: AABB(-2000.0, -100.0, -2000.0, 2000.0, 320.0, 2000.0)
            val candidates = level.getEntitiesOfClass(LivingEntity::class.java, box) {
                it.isAlive && it.tags.contains(tag)
            }
            val ent = candidates.minByOrNull { if (sourceEntity != null) it.distanceToSqr(sourceEntity) else 0.0 }
            if (ent != null) {
                return Triple(ent.position(), ent.yRot, ent.xRot)
            }
        }

        // Check if it's a tagged world block from StoryTagManager
        val blockVec = StoryTagManager.getBlockVec3(tag)
        if (blockVec != null) {
            return Triple(blockVec, 0f, 0f)
        }

        return null
    }

    private fun parseTildeOrAbsolutePart(part: String?, base: Double): Double {
        if (part == null || part.isBlank()) return base
        val s = part.trim()
        return if (s.startsWith("~")) {
            val offsetStr = s.removePrefix("~").trim()
            val offset = if (offsetStr.isEmpty() || offsetStr == "+") 0.0 else (offsetStr.toDoubleOrNull() ?: 0.0)
            base + offset
        } else {
            s.toDoubleOrNull() ?: base
        }
    }

    private fun parseCaretPart(part: String?): Double {
        if (part == null || part.isBlank()) return 0.0
        val s = part.trim()
        return if (s.startsWith("^")) {
            val offsetStr = s.removePrefix("^").trim()
            if (offsetStr.isEmpty() || offsetStr == "+") 0.0 else (offsetStr.toDoubleOrNull() ?: 0.0)
        } else {
            s.toDoubleOrNull() ?: 0.0
        }
    }

    /**
     * Minecraft view-local coordinate calculation (^Left/Right, ^Up/Down, ^Forward).
     */
    fun resolveLocalOffset(
        origin: Vec3,
        yaw: Float,
        pitch: Float,
        left: Double,
        up: Double,
        forward: Double
    ): Vec3 {
        val f = Mth.cos((yaw + 90.0f) * (Math.PI.toFloat() / 180.0f))
        val g = Mth.sin((yaw + 90.0f) * (Math.PI.toFloat() / 180.0f))
        val h = Mth.cos(-pitch * (Math.PI.toFloat() / 180.0f))
        val i = Mth.sin(-pitch * (Math.PI.toFloat() / 180.0f))
        val j = Mth.cos((-pitch + 90.0f) * (Math.PI.toFloat() / 180.0f))
        val k = Mth.sin((-pitch + 90.0f) * (Math.PI.toFloat() / 180.0f))

        val forwardVec = Vec3((f * h).toDouble(), i.toDouble(), (g * h).toDouble())
        val upVec = Vec3((f * j).toDouble(), k.toDouble(), (g * j).toDouble())
        val leftVec = forwardVec.cross(upVec).scale(-1.0)

        val dx = forwardVec.x * forward + upVec.x * up + leftVec.x * left
        val dy = forwardVec.y * forward + upVec.y * up + leftVec.y * left
        val dz = forwardVec.z * forward + upVec.z * up + leftVec.z * left

        return Vec3(origin.x + dx, origin.y + dy, origin.z + dz)
    }
}
