package vito.cobblebrain.engine

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import vito.cobblebrain.social.PokemonQuery
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class LookOperationMode {
    APPLY_LOOK,
    RESET_LOOK
}

enum class LookSubjectType {
    PLAYER_POKEMON,
    NPC_TAG
}

enum class LookReferenceType {
    PLAYER,
    MOB_TAG,
    COORDINATES
}

enum class LookTargetMode {
    TOWARDS_REFERENCE,
    AWAY_FROM_REFERENCE,
    SKY,
    GROUND,
    OPPOSITE_SELF
}

enum class LookDurationMode {
    TEMPORARY,
    INDEFINITE
}

data class ActiveLookOverride(
    val subjectUuid: UUID,
    val serverLevel: ServerLevel,
    val referenceType: LookReferenceType,
    val referencePlayerUuid: UUID? = null,
    val referenceMobTag: String? = null,
    val referenceCoordinates: Vec3? = null,
    val lookMode: LookTargetMode,
    var remainingTicks: Int, // -1 for INDEFINITE
    val onComplete: (() -> Unit)? = null
)

object StoryLookAtManager {

    private val activeOverrides = ConcurrentHashMap<UUID, ActiveLookOverride>()

    fun applyLookOverride(
        subject: LivingEntity,
        referenceType: LookReferenceType,
        referencePlayerUuid: UUID? = null,
        referenceMobTag: String? = null,
        referenceCoordinates: Vec3? = null,
        lookMode: LookTargetMode,
        durationTicks: Int = -1, // -1 for indefinite
        onComplete: (() -> Unit)? = null
    ) {
        val sLevel = subject.level() as? ServerLevel ?: return
        val override = ActiveLookOverride(
            subjectUuid = subject.uuid,
            serverLevel = sLevel,
            referenceType = referenceType,
            referencePlayerUuid = referencePlayerUuid,
            referenceMobTag = referenceMobTag,
            referenceCoordinates = referenceCoordinates,
            lookMode = lookMode,
            remainingTicks = if (durationTicks <= 0) -1 else durationTicks,
            onComplete = onComplete
        )
        activeOverrides[subject.uuid] = override

        // Apply immediately on first tick
        tickSingleOverride(subject, override)
    }

    fun resetLookOverride(subjectUuid: UUID) {
        val removed = activeOverrides.remove(subjectUuid)
        if (removed != null) {
            val entity = removed.serverLevel.getEntity(subjectUuid) as? LivingEntity
            if (entity is Mob) {
                // Clear active target looking in look control
                entity.lookControl.setLookAt(entity.x, entity.eyeY, entity.z, 10f, 10f)
            }
        }
    }

    fun resetLookOverride(subject: LivingEntity) {
        resetLookOverride(subject.uuid)
    }

    fun clearAll() {
        activeOverrides.clear()
    }

    fun isUnderOverride(subjectUuid: UUID): Boolean {
        return activeOverrides.containsKey(subjectUuid)
    }

    fun onServerTick() {
        if (activeOverrides.isEmpty()) return

        val it = activeOverrides.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val override = entry.value
            val subject = override.serverLevel.getEntity(override.subjectUuid) as? LivingEntity

            if (subject == null || !subject.isAlive) {
                it.remove()
                continue
            }

            tickSingleOverride(subject, override)

            if (override.remainingTicks > 0) {
                override.remainingTicks--
                if (override.remainingTicks <= 0) {
                    it.remove()
                    if (subject is Mob) {
                        subject.lookControl.setLookAt(subject.x, subject.eyeY, subject.z, 10f, 10f)
                    }
                    override.onComplete?.invoke()
                }
            }
        }
    }

    private fun tickSingleOverride(subject: LivingEntity, override: ActiveLookOverride) {
        val sLevel = override.serverLevel

        // 1. Resolve Reference Position
        var refPos: Vec3? = null
        when (override.referenceType) {
            LookReferenceType.PLAYER -> {
                val player = override.referencePlayerUuid?.let { sLevel.server.playerList.getPlayer(it) }
                if (player != null && player.isAlive) {
                    refPos = Vec3(player.x, player.eyeY, player.z)
                }
            }
            LookReferenceType.MOB_TAG -> {
                val tag = override.referenceMobTag
                if (!tag.isNullOrBlank()) {
                    val box = subject.boundingBox.inflate(64.0)
                    val candidate = sLevel.getEntitiesOfClass(LivingEntity::class.java, box) {
                        it != subject && it.isAlive && (it.tags.contains(tag) || it.type.descriptionId.contains(tag, true))
                    }.minByOrNull { it.distanceToSqr(subject) }

                    if (candidate != null) {
                        refPos = Vec3(candidate.x, candidate.eyeY, candidate.z)
                    } else {
                        // Check if reference is a tagged world block in StoryTagManager
                        refPos = StoryTagManager.getBlockVec3(tag)
                    }
                }
            }
            LookReferenceType.COORDINATES -> {
                refPos = override.referenceCoordinates
            }
        }

        // 2. Calculate Target Yaw and Pitch
        var targetYaw = subject.yRot
        var targetPitch = subject.xRot

        when (override.lookMode) {
            LookTargetMode.TOWARDS_REFERENCE -> {
                if (refPos != null) {
                    val dx = refPos.x - subject.x
                    val dy = refPos.y - subject.eyeY
                    val dz = refPos.z - subject.z
                    val dist = sqrt(dx * dx + dz * dz)
                    targetYaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
                    targetPitch = Math.toDegrees(atan2(-dy, dist)).toFloat().coerceIn(-90f, 90f)
                }
            }
            LookTargetMode.AWAY_FROM_REFERENCE -> {
                if (refPos != null) {
                    val dx = subject.x - refPos.x
                    val dy = subject.eyeY - refPos.y
                    val dz = subject.z - refPos.z
                    val dist = sqrt(dx * dx + dz * dz)
                    targetYaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
                    targetPitch = Math.toDegrees(atan2(-dy, dist)).toFloat().coerceIn(-90f, 90f)
                }
            }
            LookTargetMode.SKY -> {
                targetPitch = -90f
            }
            LookTargetMode.GROUND -> {
                targetPitch = 90f
            }
            LookTargetMode.OPPOSITE_SELF -> {
                targetYaw = (subject.yRot + 180f) % 360f
                targetPitch = 0f
            }
        }

        // 3. Apply Look Angles & Controls
        subject.setYRot(targetYaw)
        subject.setXRot(targetPitch)
        subject.yRotO = targetYaw
        subject.xRotO = targetPitch
        subject.yHeadRot = targetYaw
        subject.yHeadRotO = targetYaw
        subject.yBodyRot = targetYaw
        subject.yBodyRotO = targetYaw

        if (subject is Mob) {
            if (refPos != null && override.lookMode == LookTargetMode.TOWARDS_REFERENCE) {
                subject.lookControl.setLookAt(refPos.x, refPos.y, refPos.z, 45f, 45f)
            } else {
                val radYaw = Math.toRadians(targetYaw.toDouble())
                val lookVec = Vec3(
                    subject.x - sin(radYaw) * 10.0,
                    subject.eyeY + sin(Math.toRadians(-targetPitch.toDouble())) * 10.0,
                    subject.z + cos(radYaw) * 10.0
                )
                subject.lookControl.setLookAt(lookVec.x, lookVec.y, lookVec.z, 45f, 45f)
            }
        }

        // 4. Synchronize with Tracking Clients
        sLevel.chunkSource.broadcast(subject, ClientboundRotateHeadPacket(subject, (targetYaw * 256f / 360f).toInt().toByte()))
        sLevel.chunkSource.broadcast(subject, ClientboundTeleportEntityPacket(subject))
    }

    fun resolveSubjectEntity(
        serverLevel: ServerLevel,
        player: ServerPlayer?,
        subjectType: LookSubjectType,
        subjectIdentifier: String
    ): LivingEntity? {
        val trimmed = subjectIdentifier.trim()
        if (subjectType == LookSubjectType.PLAYER_POKEMON && player != null) {
            val slotIdx = PokemonQuery.parsePartySlotIndex(trimmed)
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
        } else {
            val tag = trimmed
            val box = player?.boundingBox?.inflate(128.0) ?: AABB(-1000.0, -100.0, -1000.0, 1000.0, 300.0, 1000.0)
            val candidates = serverLevel.getEntitiesOfClass(LivingEntity::class.java, box) {
                it.isAlive && (tag.isBlank() || it.tags.contains(tag) || it.type.descriptionId.contains(tag, true))
            }
            return if (player != null) {
                candidates.minByOrNull { it.distanceToSqr(player) }
            } else {
                candidates.firstOrNull()
            }
        }
        return null
    }

    fun parseCoordinates(str: String?, base: Vec3): Vec3 {
        return CoordinateResolver.resolveVec3(str, null, null, base)
    }
}
