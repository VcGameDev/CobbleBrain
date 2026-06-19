package vito.cobblebrain.social

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import vito.cobblebrain.social.OfflineDialogueManager.FeelingContext
import java.util.UUID
import kotlin.random.Random

object AmbientReactionManager {
    // Cooldown per Pokemon in ticks (1s = 20 ticks)
    private val nextReactionTick = mutableMapOf<UUID, Long>()

    // Look/Walk active tracking states
    private val activeLooks = mutableMapOf<UUID, ActiveLook>()
    private val activeWalks = mutableMapOf<UUID, ActiveWalk>()

    data class ActiveLook(
        val targetEntityUuid: UUID?,
        val targetBlockPos: BlockPos?,
        val endTime: Long
    )

    data class ActiveWalk(
        val targetEntityUuid: UUID?,
        val targetBlockPos: BlockPos?,
        val startPos: Vec3,
        val maxDistSqr: Double,
        val endTime: Long
    )

    private fun hasLineOfSight(
        mob: Mob,
        targetPos: Vec3
    ): Boolean {

        val hit = mob.level().clip(
            ClipContext(
                mob.eyePosition,
                targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mob
            )
        )

        return hit.type == HitResult.Type.MISS
    }

    fun triggerReaction(
        player: ServerPlayer,
        activePokemon: List<Pokemon>,
        currentContext: FeelingContext
    ) {
        try {
            val server = player.server
            val currentTick = server.tickCount.toLong()

            // Filter context probabilities:
            val chance = when (currentContext) {
                FeelingContext.HOSTILE_MOBS -> 0.70
                FeelingContext.HUNGRY -> 0.50
                FeelingContext.BERRY -> 0.40
                FeelingContext.POKEMON_GROUP -> 0.30 // WILD_POKEMON
                FeelingContext.ITEMS -> 0.25
                else -> 0.0
            }

            if (chance <= 0.0 || Random.nextDouble() > chance) {
                return
            }

            for (pokemon in activePokemon) {
                val uuid = pokemon.uuid
                val nextAvailable = nextReactionTick[uuid] ?: 0L
                if (currentTick < nextAvailable) {
                    continue
                }

                val mob = pokemon.entity as? Mob ?: continue
                val level = mob.level() as? ServerLevel ?: continue

                var reactionExecuted = false

                // Handle the reactions depending on context
                when (currentContext) {
                    FeelingContext.HOSTILE_MOBS -> {
                        val targetHostile = level.getEntities(player, player.boundingBox.inflate(10.0))
                            .filterIsInstance<Monster>()
                            .filter { it.isAlive }
                            .minByOrNull { it.distanceTo(mob) }

                        if (targetHostile != null) {
                            val distToPlayer = mob.distanceTo(player)
                            if (distToPlayer <= 9.0) {
                                mob.navigation.moveTo(player, 1.1)
                                activeWalks[uuid] = ActiveWalk(
                                    targetEntityUuid = player.uuid,
                                    targetBlockPos = null,
                                    startPos = mob.position(),
                                    maxDistSqr = 81.0,
                                    endTime = currentTick + 100
                                )
                            }

                            val lookDurationTicks = Random.nextLong(2, 5) * 20
                            activeLooks[uuid] = ActiveLook(
                                targetEntityUuid = targetHostile.uuid,
                                targetBlockPos = null,
                                endTime = currentTick + lookDurationTicks
                            )

                            emitParticlesAt(level, mob, ParticleTypes.LARGE_SMOKE, 5)
                            emitParticlesAt(level, mob, ParticleTypes.CRIT, 5)
                            reactionExecuted = true
                        }
                    }

                    FeelingContext.HUNGRY -> {
                        val distToPlayer = mob.distanceTo(player)
                        if (distToPlayer <= 9.0) {
                            mob.navigation.moveTo(player, 1.0)
                            activeWalks[uuid] = ActiveWalk(
                                targetEntityUuid = player.uuid,
                                targetBlockPos = null,
                                startPos = mob.position(),
                                maxDistSqr = 81.0,
                                endTime = currentTick + 100
                            )
                        }

                        val lookDurationTicks = Random.nextLong(2, 5) * 20
                        activeLooks[uuid] = ActiveLook(
                            targetEntityUuid = player.uuid,
                            targetBlockPos = null,
                            endTime = currentTick + lookDurationTicks
                        )

                        emitParticlesAt(level, mob, ParticleTypes.ANGRY_VILLAGER, 3)
                        reactionExecuted = true
                    }

                    FeelingContext.BERRY -> {

                        val pos = mob.blockPosition()

                        var berryPos: BlockPos? = null

                        search@ for (x in -9..9) {
                            for (z in -9..9) {

                                val surfacePos =
                                    level.getHeightmapPos(
                                        Heightmap.Types.MOTION_BLOCKING,
                                        pos.offset(x, 0, z)
                                    )

                                val candidates = listOf(
                                    surfacePos,
                                    surfacePos.below()
                                )

                                for (bp in candidates) {

                                    val blockId =
                                        level.getBlockState(bp)
                                            .block
                                            .descriptionId

                                    if (
                                        (blockId.contains("berry") ||
                                                blockId.contains("berries")) &&
                                        hasLineOfSight(
                                            mob,
                                            Vec3.atCenterOf(bp)
                                        )
                                    ) {

                                        berryPos = bp.immutable()
                                        break@search
                                    }
                                }
                            }
                        }

                        if (berryPos != null) {

                            mob.navigation.moveTo(
                                berryPos.x + 0.5,
                                berryPos.y.toDouble(),
                                berryPos.z + 0.5,
                                1.0
                            )

                            activeWalks[uuid] =
                                ActiveWalk(
                                    targetEntityUuid = null,
                                    targetBlockPos = berryPos,
                                    startPos = mob.position(),
                                    maxDistSqr = 81.0,
                                    endTime = currentTick + 100
                                )

                            val lookDurationTicks =
                                Random.nextLong(2, 5) * 20

                            activeLooks[uuid] =
                                ActiveLook(
                                    targetEntityUuid = null,
                                    targetBlockPos = berryPos,
                                    endTime = currentTick + lookDurationTicks
                                )

                            emitParticlesAt(
                                level,
                                mob,
                                ParticleTypes.HAPPY_VILLAGER,
                                5
                            )
                            reactionExecuted = true
                        }
                    }

                    FeelingContext.ITEMS -> {
                        val targetItem =
                            level.getEntitiesOfClass(
                                ItemEntity::class.java,
                                mob.boundingBox.inflate(9.0)
                            )
                                .filter {
                                    it.isAlive &&
                                            hasLineOfSight(
                                                mob,
                                                it.position()
                                            )
                                }
                                .minByOrNull {
                                    it.distanceTo(mob)
                                }

                        if (targetItem != null) {

                            mob.navigation.moveTo(
                                targetItem,
                                1.0
                            )

                            activeWalks[uuid] =
                                ActiveWalk(
                                    targetEntityUuid = targetItem.uuid,
                                    targetBlockPos = null,
                                    startPos = mob.position(),
                                    maxDistSqr = 81.0,
                                    endTime = currentTick + 100
                                )

                            val lookDurationTicks =
                                Random.nextLong(2, 5) * 20

                            activeLooks[uuid] =
                                ActiveLook(
                                    targetEntityUuid = targetItem.uuid,
                                    targetBlockPos = null,
                                    endTime = currentTick + lookDurationTicks
                                )
                            reactionExecuted = true
                        }
                    }

                    FeelingContext.POKEMON_GROUP -> {
                        val wildPoke = level.getEntitiesOfClass(PokemonEntity::class.java, mob.boundingBox.inflate(9.0))
                            .filter { entity ->
                                entity != mob && entity.isAlive && entity.pokemon.getOwnerUUID() == null
                            }
                            .minByOrNull { it.distanceTo(mob) }

                        if (wildPoke != null) {
                            mob.navigation.stop()
                            val lookDurationTicks = Random.nextLong(2, 5) * 20
                            activeLooks[uuid] = ActiveLook(
                                targetEntityUuid = wildPoke.uuid,
                                targetBlockPos = null,
                                endTime = currentTick + lookDurationTicks
                            )

                            emitParticlesAt(level, mob, ParticleTypes.HAPPY_VILLAGER, 6)
                            reactionExecuted = true
                        }
                    }

                    else -> {}
                }

                if (reactionExecuted) {
                    nextReactionTick[uuid] =
                        currentTick + Random.nextLong(15, 36) * 20
                    break
                }
            }
        } catch (e: Exception) {
        }
    }

    fun tick(server: MinecraftServer) {
        try {
            val currentTick = server.tickCount.toLong()

            // Update walks
            activeWalks.entries.removeIf { (uuid, state) ->
                val mob = server.allLevels.firstNotNullOfOrNull { it.getEntity(uuid) as? Mob }
                if (mob == null || currentTick >= state.endTime) {
                    mob?.navigation?.stop()
                    true
                } else {
                    var targetPos: Vec3? = null
                    if (state.targetEntityUuid != null) {
                        val targetEntity = server.allLevels.firstNotNullOfOrNull { it.getEntity(state.targetEntityUuid) }
                        if (targetEntity != null) {
                            targetPos = targetEntity.position()
                        }
                    } else if (state.targetBlockPos != null) {
                        val bp = state.targetBlockPos
                        targetPos = Vec3(bp.x + 0.5, bp.y.toDouble(), bp.z + 0.5)
                    }

                    if (targetPos != null) {
                        val distSqr = mob.position().distanceToSqr(targetPos)
                        if (distSqr > state.maxDistSqr) {
                            mob.navigation.stop()
                            true
                        } else {
                            false
                        }
                    } else {
                        mob.navigation.stop()
                        true
                    }
                }
            }

            // Update looks
            activeLooks.entries.removeIf { (uuid, state) ->
                val mob = server.allLevels.firstNotNullOfOrNull { it.getEntity(uuid) as? Mob }
                if (mob == null || currentTick >= state.endTime) {
                    true
                } else {
                    if (state.targetEntityUuid != null) {
                        val targetEntity = server.allLevels.firstNotNullOfOrNull { it.getEntity(state.targetEntityUuid) }
                        if (targetEntity != null) {
                            mob.lookControl.setLookAt(targetEntity, 30f, 30f)
                        }
                    } else if (state.targetBlockPos != null) {
                        val bp = state.targetBlockPos
                        mob.lookControl.setLookAt(bp.x + 0.5, bp.y + 0.5, bp.z + 0.5, 30f, 30f)
                    }
                    false
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun emitParticlesAt(level: ServerLevel, mob: Mob, particleType: net.minecraft.core.particles.SimpleParticleType, count: Int) {
        val px = mob.x
        val py = mob.y + mob.bbHeight / 2.0
        val pz = mob.z
        level.sendParticles(particleType, px, py, pz, count, 0.3, 0.3, 0.3, 0.02)
    }
}
