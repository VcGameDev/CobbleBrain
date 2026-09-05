package vito.cobblebrain.engine

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ActiveJumpOverride(
    val entityUuid: UUID,
    val serverLevel: ServerLevel,
    val wasNoAi: Boolean,
    val totalTicks: Int = 12,
    var currentTick: Int = 0
)

object StoryJumpManager {

    private val activeJumps = ConcurrentHashMap<UUID, ActiveJumpOverride>()

    /**
     * Applies a jump hop to any LivingEntity, seamlessly supporting both standard AI and NoAI entities.
     * For NoAI entities, NoAI is temporarily lifted for the duration of the jump arc so the Minecraft client
     * and server can process the jump velocity and trigger the native jump animation, restoring NoAI immediately
     * once the entity touches ground.
     */
    fun applyJump(entity: LivingEntity, maxHeight: Double = 0.42, durationTicks: Int = 12) {
        val sLevel = entity.level() as? ServerLevel ?: return
        val mob = entity as? Mob
        val isNoAiMob = mob?.isNoAi == true

        if (isNoAiMob && mob != null) {
            // Lift NoAI temporarily so client physics and jump animations execute
            mob.isNoAi = false
            mob.navigation.stop()
        }

        try {
            entity.setDeltaMovement(entity.deltaMovement.x, maxHeight.coerceIn(0.2, 1.0), entity.deltaMovement.z)
            entity.hasImpulse = true
            entity.hurtMarked = true
            sLevel.chunkSource.broadcast(entity, ClientboundSetEntityMotionPacket(entity))
        } catch (_: Exception) {}

        activeJumps[entity.uuid] = ActiveJumpOverride(
            entityUuid = entity.uuid,
            serverLevel = sLevel,
            wasNoAi = isNoAiMob,
            totalTicks = durationTicks.coerceAtLeast(6),
            currentTick = 0
        )
    }

    fun onServerTick() {
        if (activeJumps.isEmpty()) return

        val it = activeJumps.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val jump = entry.value
            val subject = jump.serverLevel.getEntity(jump.entityUuid) as? LivingEntity

            if (subject == null || !subject.isAlive) {
                it.remove()
                continue
            }

            jump.currentTick++

            // When jump completes or entity lands back on ground after apex
            if (jump.currentTick >= jump.totalTicks || (jump.currentTick >= 6 && subject.onGround())) {
                if (jump.wasNoAi && subject is Mob) {
                    subject.navigation.stop()
                    subject.isNoAi = true
                }
                it.remove()
            }
        }
    }

    @Suppress("unused")
    fun clearAll() {
        activeJumps.forEach { (_, jump) ->
            if (jump.wasNoAi) {
                val entity = jump.serverLevel.getEntity(jump.entityUuid) as? Mob
                entity?.isNoAi = true
            }
        }
        activeJumps.clear()
    }
}
