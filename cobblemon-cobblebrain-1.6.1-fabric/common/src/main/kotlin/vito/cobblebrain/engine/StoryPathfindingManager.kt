package vito.cobblebrain.engine

import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.sqrt

data class ActivePathfinding(
    val subjectUuid: UUID,
    val serverLevel: ServerLevel,
    val targetDestination: Vec3,
    val speedModifier: Double,
    val waitForCompletion: Boolean,
    var remainingTimeoutTicks: Int,
    val onTimeoutBehavior: String, // "TELEPORT_TO_DESTINATION" | "ADVANCE_ANYWAY"
    val lockPositionOnArrival: Boolean,
    val wasNoAi: Boolean = false,
    var ticksSinceLastRepath: Int = 0,
    val onArrival: (() -> Unit)? = null
)

data class LockedEntityPosition(
    val serverLevel: ServerLevel,
    val pos: Vec3
)

object StoryPathfindingManager {

    private val activePathfindings = ConcurrentHashMap<UUID, ActivePathfinding>()
    private val lockedPositions = ConcurrentHashMap<UUID, LockedEntityPosition>()

    fun startPathfinding(
        subject: LivingEntity,
        targetDestination: Vec3,
        speedModifier: Double = 1.0,
        waitForCompletion: Boolean = true,
        timeoutTicks: Int = 100,
        onTimeoutBehavior: String = "TELEPORT_TO_DESTINATION",
        lockPositionOnArrival: Boolean = true,
        onArrival: (() -> Unit)? = null
    ) {
        val sLevel = subject.level() as? ServerLevel ?: return

        // Release any previous position lock for this subject
        lockedPositions.remove(subject.uuid)

        val originallyNoAi = if (subject is Mob) subject.isNoAi else false
        if (subject is Mob && originallyNoAi) {
            subject.isNoAi = false
        }

        val pf = ActivePathfinding(
            subjectUuid = subject.uuid,
            serverLevel = sLevel,
            targetDestination = targetDestination,
            speedModifier = speedModifier.coerceIn(0.1, 4.0),
            waitForCompletion = waitForCompletion,
            remainingTimeoutTicks = if (timeoutTicks <= 0) 100 else timeoutTicks,
            onTimeoutBehavior = onTimeoutBehavior,
            lockPositionOnArrival = lockPositionOnArrival,
            wasNoAi = originallyNoAi,
            onArrival = onArrival
        )

        activePathfindings[subject.uuid] = pf

        // Apply pathfinding immediately
        applyNavigation(subject, pf)
    }

    private fun applyNavigation(subject: LivingEntity, pf: ActivePathfinding) {
        if (subject is Mob) {
            if (subject.isNoAi) {
                subject.isNoAi = false
            }
            subject.navigation.moveTo(pf.targetDestination.x, pf.targetDestination.y, pf.targetDestination.z, pf.speedModifier)
            subject.moveControl.setWantedPosition(pf.targetDestination.x, pf.targetDestination.y, pf.targetDestination.z, pf.speedModifier)
            subject.lookControl.setLookAt(pf.targetDestination.x, pf.targetDestination.y + subject.eyeHeight * 0.5, pf.targetDestination.z, 30f, 30f)
        }
    }

    fun stopPathfinding(subjectUuid: UUID) {
        val removed = activePathfindings.remove(subjectUuid)
        if (removed != null) {
            val entity = removed.serverLevel.getEntity(subjectUuid) as? LivingEntity
            if (entity is Mob) {
                entity.navigation.stop()
                if (removed.wasNoAi) {
                    entity.isNoAi = true
                }
            }
        }
    }

    fun lockPosition(subjectUuid: UUID, level: ServerLevel, pos: Vec3) {
        lockedPositions[subjectUuid] = LockedEntityPosition(level, pos)
    }

    fun unlockPosition(subjectUuid: UUID) {
        lockedPositions.remove(subjectUuid)
    }

    fun clearAll() {
        activePathfindings.clear()
        lockedPositions.clear()
    }

    fun onServerTick() {
        // 1. Maintain position lock for arrived stationary entities
        if (lockedPositions.isNotEmpty()) {
            val lockIt = lockedPositions.entries.iterator()
            while (lockIt.hasNext()) {
                val entry = lockIt.next()
                val uuid = entry.key
                val lockData = entry.value

                // If currently actively pathfinding again, don't lock
                if (activePathfindings.containsKey(uuid)) continue

                val entity = lockData.serverLevel.getEntity(uuid) as? LivingEntity
                if (entity == null || !entity.isAlive) {
                    lockIt.remove()
                    continue
                }
                val dx = entity.x - lockData.pos.x
                val dz = entity.z - lockData.pos.z
                if (dx * dx + dz * dz > 0.5) {
                    entity.teleportTo(lockData.pos.x, entity.y, lockData.pos.z)
                    entity.deltaMovement = Vec3(0.0, entity.deltaMovement.y, 0.0)
                }
            }
        }

        // 2. Process active pathfindings
        if (activePathfindings.isEmpty()) return

        val it = activePathfindings.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val pf = entry.value
            val subject = pf.serverLevel.getEntity(pf.subjectUuid) as? LivingEntity

            if (subject == null || !subject.isAlive) {
                it.remove()
                continue
            }

            val dx = pf.targetDestination.x - subject.x
            val dy = pf.targetDestination.y - subject.y
            val dz = pf.targetDestination.z - subject.z
            val hDist = sqrt(dx * dx + dz * dz)
            val totalDistSq = dx * dx + dy * dy + dz * dz

            // Check Arrival: within 1.25 blocks horizontal radius & 1.75 blocks vertical
            val hasArrived = totalDistSq <= 1.56 || (hDist <= 1.25 && Math.abs(dy) <= 1.75)

            if (hasArrived) {
                it.remove()
                if (subject is Mob) {
                    subject.navigation.stop()
                    if (pf.wasNoAi) {
                        subject.isNoAi = true
                    }
                }
                subject.deltaMovement = Vec3(0.0, subject.deltaMovement.y, 0.0)
                if (pf.lockPositionOnArrival) {
                    lockedPositions[subject.uuid] = LockedEntityPosition(pf.serverLevel, Vec3(pf.targetDestination.x, subject.y, pf.targetDestination.z))
                }
                pf.onArrival?.invoke()
                continue
            }

            // Drive mob forward every tick
            applyNavigation(subject, pf)

            if (hDist > 0.05) {
                val targetYaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
                subject.yRot = targetYaw
                subject.yHeadRot = targetYaw
                subject.yBodyRot = targetYaw

                // If path navigation is stuck or overridden by other goals, assist movement vector
                val baseSpeed = if (subject is Mob) {
                    (subject.getAttributeValue(Attributes.MOVEMENT_SPEED) * pf.speedModifier).coerceIn(0.12, 0.5)
                } else {
                    0.25 * pf.speedModifier
                }
                val dirX = (dx / hDist) * baseSpeed
                val dirZ = (dz / hDist) * baseSpeed
                val currentYMotion = if (subject.horizontalCollision && subject.onGround()) 0.4 else subject.deltaMovement.y
                subject.deltaMovement = Vec3(dirX, currentYMotion, dirZ)
                subject.hasImpulse = true

                pf.serverLevel.chunkSource.broadcast(subject, ClientboundRotateHeadPacket(subject, (subject.yRot * 256f / 360f).toInt().toByte()))
            }

            // Decrement timeout
            pf.remainingTimeoutTicks--
            if (pf.remainingTimeoutTicks <= 0) {
                it.remove()
                if (pf.onTimeoutBehavior == "TELEPORT_TO_DESTINATION") {
                    subject.teleportTo(pf.targetDestination.x, pf.targetDestination.y, pf.targetDestination.z)
                }
                if (subject is Mob) {
                    subject.navigation.stop()
                    if (pf.wasNoAi) {
                        subject.isNoAi = true
                    }
                }
                subject.deltaMovement = Vec3(0.0, subject.deltaMovement.y, 0.0)
                if (pf.lockPositionOnArrival) {
                    lockedPositions[subject.uuid] = LockedEntityPosition(pf.serverLevel, Vec3(pf.targetDestination.x, subject.y, pf.targetDestination.z))
                }
                pf.onArrival?.invoke()
            }
        }
    }
}

