package vito.cobblebrain.sensors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import vito.cobblebrain.config.ConfigHandler
import vito.cobblebrain.social.PingManager
import vito.cobblebrain.social.PlayerPing
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

enum class GatheringType {
    EXCAVATE,  // Steel type - Tunnel excavation
    BUILD      // Construction using blocks from chest in slot order
}

enum class GatheringState {
    WAITING_FOR_PING,
    NAVIGATING,
    BREAKING,
    FINISHED,
    CANCELLED
}

data class GatheringSession(
    val pokemonUuid: UUID,
    val ownerUuid: UUID,
    val type: GatheringType,
    var state: GatheringState = GatheringState.WAITING_FOR_PING,
    var ping: PlayerPing? = null,
    var pingA: PlayerPing? = null,
    var pingB: PlayerPing? = null,
    var pingChest: PlayerPing? = null,
    var chestPos: BlockPos? = null,
    var heldBlockStack: ItemStack = ItemStack.EMPTY,
    var minRightOffset: Int = -1,
    var maxRightOffset: Int = 1,
    var minUpOffset: Int = -1,
    var maxUpOffset: Int = 1,
    var pingWaitTicks: Int = 300, // 15 seconds timeout waiting for Pings
    val targetBlocks: MutableList<BlockPos> = mutableListOf(),
    val layers: MutableList<MutableList<BlockPos>> = mutableListOf(), // Batch layers for Steel Excavate
    var currentTargetIndex: Int = 0,
    var currentLayerIndex: Int = 0,
    var breakTimer: Int = 0,
    var navStuckTicks: Int = 0,
    var lastDistSqr: Double = Double.MAX_VALUE,
    var lastOreNoticeTime: Long = 0L,
    var demolishForwardDir: Direction = Direction.NORTH,
    var demolishCurrentDepth: Int = 0,
    var isSuspendedBuild: Boolean = false,
    val startTime: Long = System.currentTimeMillis()
)

object GatheringActions {

    private const val PHYSICAL_REACH_DISTANCE_SQR = 2.8 * 2.8 // 2.8 blocks reach distance
    private const val STUCK_TICK_THRESHOLD = 10 // 0.5 seconds stuck threshold (10 ticks)
    private val activeSessions = ConcurrentHashMap<UUID, GatheringSession>()

    fun startGatheringAction(pokemon: Mob, type: GatheringType, owner: ServerPlayer): Boolean {
        val cobblemonPokemon = (pokemon as? PokemonEntity)?.pokemon ?: run {
            CommandState.activeCommands[pokemon.uuid] = "idle"
            return false
        }
        val primaryType = cobblemonPokemon.primaryType.name.lowercase()

        if (type == GatheringType.EXCAVATE && primaryType != "steel") {
            sendMessage(owner, "${pokemon.displayName?.string} must be primary STEEL type to perform excavate!", ChatFormatting.RED)
            CommandState.activeCommands[pokemon.uuid] = "idle"
            return false
        }

        // Fresh start ONLY after action is activated (clear old pre-existing pings)
        val session = GatheringSession(
            pokemonUuid = pokemon.uuid,
            ownerUuid = owner.uuid,
            type = type,
            pingA = null,
            pingB = null,
            ping = null,
            state = GatheringState.WAITING_FOR_PING,
            startTime = System.currentTimeMillis()
        )

        when (type) {
            GatheringType.EXCAVATE -> {
                sendMessage(
                    owner,
                    "Excavate activated! Look at Corner A of the tunnel face and press Ping. Move crosshair to preview size and press Ping at Corner B!",
                    ChatFormatting.AQUA
                )
            }
            GatheringType.BUILD -> {
                sendMessage(
                    owner,
                    "Build activated! Mark Corner A with Ping, move crosshair to preview 3D box/plane (up to 32x32) and press Ping for Corner B, then Ping the Chest with blocks!",
                    ChatFormatting.AQUA
                )
            }
        }

        // Temporarily override FollowOwnerGoal so owner movement doesn't interrupt gathering
        disableFollowGoal(pokemon)

        activeSessions[pokemon.uuid] = session
        return true
    }

    @Suppress("unused")
    fun stopGathering(pokemonUuid: UUID, server: MinecraftServer? = null) {
        val session = activeSessions.remove(pokemonUuid)
        if (session != null) {
            CommandState.activeCommands[pokemonUuid] = "idle"
            val level = server?.allLevels?.firstNotNullOfOrNull { it.getEntity(pokemonUuid) as? Mob }?.level() as? ServerLevel
            if (level != null) {
                cleanupBuildScaffoldings(session, level)
            }
            server?.allLevels?.firstNotNullOfOrNull { it.getEntity(pokemonUuid) as? Mob }?.let { pokemon ->
                restoreFollowGoal(pokemon)
            }
        }
    }

    fun isGathering(pokemonUuid: UUID): Boolean {
        return activeSessions.containsKey(pokemonUuid)
    }

    fun tick(server: MinecraftServer) {
        val iterator = activeSessions.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val pokemonUuid = entry.key
            val session = entry.value

            val pokemon = server.allLevels.firstNotNullOfOrNull { it.getEntity(pokemonUuid) as? Mob }
            if (pokemon == null || !pokemon.isAlive) {
                iterator.remove()
                CommandState.activeCommands[pokemonUuid] = "idle"
                continue
            }

            disableFollowGoal(pokemon)

            val owner = server.playerList.getPlayer(session.ownerUuid)
            if (owner == null) {
                restoreFollowGoal(pokemon)
                iterator.remove()
                CommandState.activeCommands[pokemonUuid] = "idle"
                continue
            }

            val level = pokemon.level() as ServerLevel

            when (session.state) {
                GatheringState.WAITING_FOR_PING -> {
                    val currentPing = PingManager.getPing(session.ownerUuid)
                    val maxDim = if (session.type == GatheringType.BUILD) 31 else 8

                    // Real-time live preview stretching particle box with air fallback
                    if (session.pingA != null && session.pingB == null) {
                        val targetHit = owner.pick(24.0, 0.0f, false)
                        val livePos = if (targetHit.type == HitResult.Type.BLOCK) {
                            (targetHit as BlockHitResult).blockPos
                        } else {
                            val eyeVec = owner.eyePosition
                            val lookVec = owner.lookAngle
                            val targetVec = eyeVec.add(lookVec.scale(12.0))
                            BlockPos.containing(targetVec)
                        }
                        renderLivePreviewBox(level, session.pingA!!, livePos, pokemon, maxDim)
                    }

                    if (currentPing != null && currentPing.timestamp >= session.startTime) {
                        if (session.pingA == null) {
                            session.pingA = currentPing
                            session.ping = currentPing
                            session.pingWaitTicks = 300 // 15 seconds to set next pings
                            val nextInstruction = if (session.type == GatheringType.BUILD) {
                                "Corner A marked! Press Ping at Corner B, then Ping the supply Chest."
                            } else {
                                "Corner A marked! Move crosshair to preview face size and press Ping to confirm Corner B."
                            }
                            sendMessage(
                                owner,
                                "Corner A marked at (${currentPing.pos.x}, ${currentPing.pos.y}, ${currentPing.pos.z})! $nextInstruction",
                                ChatFormatting.AQUA
                            )
                        } else if (session.pingB == null && currentPing.timestamp > (session.pingA?.timestamp ?: 0L)) {
                            session.pingB = currentPing
                            if (session.type == GatheringType.BUILD) {
                                sendMessage(
                                    owner,
                                    "Corner B confirmed! Now press Ping on the Chest containing your building blocks.",
                                    ChatFormatting.GREEN
                                )
                            } else {
                                if (calculateGeometry(session, pokemon, level, currentPing)) {
                                    session.state = GatheringState.NAVIGATING
                                    val w = abs(session.maxRightOffset - session.minRightOffset) + 1
                                    val h = abs(session.maxUpOffset - session.minUpOffset) + 1
                                    val fitsWidth = w >= pokemon.bbWidth.toDouble()
                                    val fitsHeight = h >= pokemon.bbHeight.toDouble()

                                    if (!fitsWidth || !fitsHeight) {
                                        sendMessage(
                                            owner,
                                            "Warning: Tunnel ($w x $h) is small for ${pokemon.displayName?.string} (Hitbox: ${String.format("%.1f", pokemon.bbWidth)}x${String.format("%.1f", pokemon.bbHeight)})!",
                                            ChatFormatting.GOLD
                                        )
                                    }

                                    sendMessage(
                                        owner,
                                        "${pokemon.displayName?.string} starting $w x $h excavation tunnel!",
                                        ChatFormatting.YELLOW
                                    )
                                } else {
                                    sendMessage(owner, "No valid blocks found to excavate.", ChatFormatting.RED)
                                    restoreFollowGoal(pokemon)
                                    iterator.remove()
                                    CommandState.activeCommands[pokemonUuid] = "idle"
                                }
                            }
                        } else if (session.type == GatheringType.BUILD && session.pingB != null && session.pingChest == null && currentPing.timestamp > (session.pingB?.timestamp ?: 0L)) {
                            val blockEntity = level.getBlockEntity(currentPing.pos)
                            if (blockEntity is Container) {
                                session.pingChest = currentPing
                                session.chestPos = currentPing.pos
                                if (calculateBuildGeometry(session, level)) {
                                    session.state = GatheringState.NAVIGATING
                                    sendMessage(
                                        owner,
                                        "${pokemon.displayName?.string} starting construction! Supplying blocks from Chest at (${currentPing.pos.x}, ${currentPing.pos.y}, ${currentPing.pos.z}).",
                                        ChatFormatting.GREEN
                                    )
                                } else {
                                    sendMessage(owner, "No valid build area positions calculated.", ChatFormatting.RED)
                                    restoreFollowGoal(pokemon)
                                    iterator.remove()
                                    CommandState.activeCommands[pokemonUuid] = "idle"
                                }
                            } else {
                                sendMessage(
                                    owner,
                                    "Target block at (${currentPing.pos.x}, ${currentPing.pos.y}, ${currentPing.pos.z}) is not a Container/Chest! Please Ping a valid Chest.",
                                    ChatFormatting.RED
                                )
                            }
                        }
                    } else {
                        session.pingWaitTicks--
                        if (session.pingWaitTicks <= 0) {
                            sendMessage(
                                owner,
                                "${pokemon.displayName?.string}'s action timed out waiting for Ping.",
                                ChatFormatting.GRAY
                            )
                            restoreFollowGoal(pokemon)
                            iterator.remove()
                            CommandState.activeCommands[pokemonUuid] = "idle"
                        }
                    }
                }

                GatheringState.NAVIGATING, GatheringState.BREAKING -> {
                    when (session.type) {
                        GatheringType.EXCAVATE -> {
                            renderActiveParticleBoxOutline(level, session, pokemon)
                            tickSteelExcavateBatch(pokemon, session, level, owner, iterator)
                        }
                        GatheringType.BUILD -> {
                            renderActiveBuildBoxOutline(level, session)
                            tickBuildBatch(pokemon, session, level, owner, iterator)
                        }
                    }
                }

                GatheringState.FINISHED, GatheringState.CANCELLED -> {
                    cleanupBuildScaffoldings(session, level)
                    restoreFollowGoal(pokemon)
                    iterator.remove()
                    CommandState.activeCommands[pokemonUuid] = "idle"
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun cleanupBuildScaffoldings(session: GatheringSession, level: ServerLevel) {
        // Safe teleportation system handles positioning without placing temporary blocks
    }

    private fun renderLivePreviewBox(level: ServerLevel, pingA: PlayerPing, livePos: BlockPos, pokemon: Mob, maxDim: Int = 8) {
        val posA = pingA.pos

        val minX = min(posA.x, livePos.x).coerceIn(posA.x - maxDim, posA.x + maxDim)
        var maxX = max(posA.x, livePos.x).coerceIn(posA.x - maxDim, posA.x + maxDim)
        if (maxX - minX > maxDim) maxX = minX + maxDim

        val minY = min(posA.y, livePos.y).coerceIn(posA.y - maxDim, posA.y + maxDim)
        var maxY = max(posA.y, livePos.y).coerceIn(posA.y - maxDim, posA.y + maxDim)
        if (maxY - minY > maxDim) maxY = minY + maxDim

        val minZ = min(posA.z, livePos.z).coerceIn(posA.z - maxDim, posA.z + maxDim)
        var maxZ = max(posA.z, livePos.z).coerceIn(posA.z - maxDim, posA.z + maxDim)
        if (maxZ - minZ > maxDim) maxZ = minZ + maxDim

        val minPos = BlockPos(minX, minY, minZ)
        val maxPos = BlockPos(maxX, maxY, maxZ)

        val w = abs(maxX - minX) + 1
        val h = abs(maxY - minY) + 1
        val fits = w >= pokemon.bbWidth.toDouble() && h >= pokemon.bbHeight.toDouble()

        render3DBoxWireframe(level, minPos, maxPos, fits)
    }

    private fun renderActiveParticleBoxOutline(level: ServerLevel, session: GatheringSession, pokemon: Mob) {
        val pingA = session.pingA ?: session.ping ?: return
        val forwardDir = session.demolishForwardDir
        val rightDir = forwardDir.clockWise

        val w = abs(session.maxRightOffset - session.minRightOffset) + 1
        val h = abs(session.maxUpOffset - session.minUpOffset) + 1
        val fits = w >= pokemon.bbWidth.toDouble() && h >= pokemon.bbHeight.toDouble()

        renderBoxPerimeter(
            level,
            pingA.pos,
            forwardDir,
            rightDir,
            session.demolishCurrentDepth,
            session.minRightOffset,
            session.maxRightOffset,
            session.minUpOffset,
            session.maxUpOffset,
            fits
        )
    }

    private fun renderActiveBuildBoxOutline(level: ServerLevel, session: GatheringSession) {
        val pingA = session.pingA ?: return
        val pingB = session.pingB ?: return
        val posA = pingA.pos
        val posB = pingB.pos

        val minPos = BlockPos(min(posA.x, posB.x), min(posA.y, posB.y), min(posA.z, posB.z))
        val maxPos = BlockPos(max(posA.x, posB.x), max(posA.y, posB.y), max(posA.z, posB.z))

        render3DBoxWireframe(level, minPos, maxPos, true)
    }

    private fun render3DBoxWireframe(
        level: ServerLevel,
        minPos: BlockPos,
        maxPos: BlockPos,
        fitsHitbox: Boolean
    ) {
        val colorVector = if (fitsHitbox) Vector3f(0.2f, 0.85f, 1.0f) else Vector3f(1.0f, 0.2f, 0.2f)
        val particleOptions = DustParticleOptions(colorVector, 1.2f)

        val minX = minPos.x.toDouble()
        val maxX = maxPos.x.toDouble() + 1.0
        val minY = minPos.y.toDouble()
        val maxY = maxPos.y.toDouble() + 1.0
        val minZ = minPos.z.toDouble()
        val maxZ = maxPos.z.toDouble() + 1.0

        val step = 0.5

        var x = minX
        while (x <= maxX) {
            level.sendParticles(particleOptions, x, minY, minZ, 1, 0.0, 0.0, 0.0, 0.0)
            level.sendParticles(particleOptions, x, maxY, minZ, 1, 0.0, 0.0, 0.0, 0.0)
            level.sendParticles(particleOptions, x, minY, maxZ, 1, 0.0, 0.0, 0.0, 0.0)
            level.sendParticles(particleOptions, x, maxY, maxZ, 1, 0.0, 0.0, 0.0, 0.0)
            x += step
        }

        var y = minY
        while (y <= maxY) {
            level.sendParticles(particleOptions, minX, y, minZ, 1, 0.0, 0.0, 0.0, 0.0)
            level.sendParticles(particleOptions, maxX, y, minZ, 1, 0.0, 0.0, 0.0, 0.0)
            level.sendParticles(particleOptions, minX, y, maxZ, 1, 0.0, 0.0, 0.0, 0.0)
            level.sendParticles(particleOptions, maxX, y, maxZ, 1, 0.0, 0.0, 0.0, 0.0)
            y += step
        }

        var z = minZ
        while (z <= maxZ) {
            level.sendParticles(particleOptions, minX, minY, z, 1, 0.0, 0.0, 0.0, 0.0)
            level.sendParticles(particleOptions, maxX, minY, z, 1, 0.0, 0.0, 0.0, 0.0)
            level.sendParticles(particleOptions, minX, maxY, z, 1, 0.0, 0.0, 0.0, 0.0)
            level.sendParticles(particleOptions, maxX, maxY, z, 1, 0.0, 0.0, 0.0, 0.0)
            z += step
        }
    }

    private fun renderBoxPerimeter(
        level: ServerLevel,
        startPos: BlockPos,
        forwardDir: Direction,
        rightDir: Direction,
        depth: Int,
        minR: Int,
        maxR: Int,
        minU: Int,
        maxU: Int,
        fitsHitbox: Boolean
    ) {
        val facePos = startPos.relative(forwardDir, depth)
        val colorVector = if (fitsHitbox) Vector3f(0.2f, 0.85f, 1.0f) else Vector3f(1.0f, 0.2f, 0.2f)
        val particleOptions = DustParticleOptions(colorVector, 1.2f)
        val step = 0.4

        var r = minR.toDouble()
        while (r <= maxR + 1.0) {
            val p1 = getParticleCoord(facePos, rightDir, r, minU.toDouble())
            level.sendParticles(particleOptions, p1.x, p1.y, p1.z, 1, 0.0, 0.0, 0.0, 0.0)
            val p2 = getParticleCoord(facePos, rightDir, r, maxU + 1.0)
            level.sendParticles(particleOptions, p2.x, p2.y, p2.z, 1, 0.0, 0.0, 0.0, 0.0)
            r += step
        }

        var u = minU.toDouble()
        while (u <= maxU + 1.0) {
            val p1 = getParticleCoord(facePos, rightDir, minR.toDouble(), u)
            level.sendParticles(particleOptions, p1.x, p1.y, p1.z, 1, 0.0, 0.0, 0.0, 0.0)
            val p2 = getParticleCoord(facePos, rightDir, maxR + 1.0, u)
            level.sendParticles(particleOptions, p2.x, p2.y, p2.z, 1, 0.0, 0.0, 0.0, 0.0)
            u += step
        }
    }

    private fun getParticleCoord(basePos: BlockPos, rightDir: Direction, r: Double, u: Double): Vec3 {
        return Vec3(
            basePos.x + 0.5 + rightDir.stepX * r,
            basePos.y + u,
            basePos.z + 0.5 + rightDir.stepZ * r
        )
    }

    private fun tickSteelExcavateBatch(
        pokemon: Mob,
        session: GatheringSession,
        level: ServerLevel,
        owner: ServerPlayer,
        iterator: MutableIterator<MutableMap.MutableEntry<UUID, GatheringSession>>
    ) {
        if (session.layers.isEmpty() || session.currentLayerIndex >= session.layers.size) {
            val ping = session.ping
            if (ping != null && generateNextExcavateLayer(session, level, session.demolishForwardDir)) {
                // Continuous expansion successful
            } else {
                session.state = GatheringState.FINISHED
                sendMessage(
                    owner,
                    "${pokemon.displayName?.string} finished excavation tunnel.",
                    ChatFormatting.GREEN
                )
                restoreFollowGoal(pokemon)
                iterator.remove()
                CommandState.activeCommands[pokemon.uuid] = "idle"
                return
            }
        }

        val currentLayer = session.layers[session.currentLayerIndex]
        if (currentLayer.isEmpty()) {
            session.currentLayerIndex++
            return
        }

        val pingA = session.pingA ?: session.ping ?: return
        val forwardDir = session.demolishForwardDir
        val rightDir = forwardDir.clockWise
        val currentDepth = session.demolishCurrentDepth
        val layerPos = pingA.pos.relative(forwardDir, currentDepth)

        val centerR = (session.minRightOffset + session.maxRightOffset) / 2
        val floorU = session.minUpOffset
        val floorY = pingA.pos.y + floorU

        val faceCenterPos = layerPos.relative(rightDir, centerR).relative(Direction.UP, floorU)
        val frontFacePos = faceCenterPos.relative(forwardDir.opposite)

        val navPos = getStandableTargetPos(level, frontFacePos, floorY)

        if (pokemon.blockY > floorY + 1) {
            pokemon.teleportTo(navPos.x + 0.5, navPos.y.toDouble(), navPos.z + 0.5)
        }

        val distToFaceSqr = pokemon.distanceToSqr(faceCenterPos.x + 0.5, faceCenterPos.y.toDouble(), faceCenterPos.z + 0.5)
        val distToNavSqr = pokemon.distanceToSqr(navPos.x + 0.5, navPos.y.toDouble(), navPos.z + 0.5)
        val isWithinReach = distToFaceSqr <= (3.8 * 3.8) || distToNavSqr <= (2.8 * 2.8)

        if (!isWithinReach) {
            session.state = GatheringState.NAVIGATING
            session.breakTimer = 0

            if (abs(distToNavSqr - session.lastDistSqr) < 0.08) {
                session.navStuckTicks++
            } else {
                session.navStuckTicks = 0
            }
            session.lastDistSqr = distToNavSqr

            if (session.navStuckTicks >= STUCK_TICK_THRESHOLD) {
                session.navStuckTicks = 0
                session.lastDistSqr = Double.MAX_VALUE
                session.currentLayerIndex++
                return
            }

            pokemon.navigation.moveTo(navPos.x + 0.5, navPos.y.toDouble(), navPos.z + 0.5, 1.25)
        } else {
            session.state = GatheringState.BREAKING
            session.navStuckTicks = 0
            session.lastDistSqr = Double.MAX_VALUE
            session.breakTimer++

            val breakDelay = getBreakDelay(session.type).coerceAtLeast(3)
            if (session.breakTimer >= breakDelay) {
                session.breakTimer = 0
                pokemon.swing(InteractionHand.MAIN_HAND)

                val dropChance = ConfigHandler.config.actionSettings.excavate.dropChancePercent / 100.0f
                var brokenAny = false

                for (pos in currentLayer) {
                    val state = level.getBlockState(pos)
                    if (!state.isAir) {
                        val produceDrops = level.random.nextFloat() < dropChance
                        level.destroyBlock(pos, produceDrops)
                        level.sendParticles(
                            BlockParticleOption(ParticleTypes.BLOCK, state),
                            pos.x + 0.5,
                            pos.y + 0.5,
                            pos.z + 0.5,
                            10,
                            0.2, 0.2, 0.2,
                            0.05
                        )
                        brokenAny = true
                    }
                }

                if (brokenAny) {
                    level.playSound(
                        null,
                        faceCenterPos,
                        SoundEvents.WITHER_BREAK_BLOCK,
                        SoundSource.BLOCKS,
                        1.2f,
                        0.9f + level.random.nextFloat() * 0.2f
                    )
                }

                session.currentLayerIndex++
            }
        }
    }

    private fun findSafeBuildStandingPos(
        level: ServerLevel,
        targetBuildPos: BlockPos,
        session: GatheringSession
    ): BlockPos {
        if (session.isSuspendedBuild && session.currentTargetIndex > 0) {
            val placedSet = session.targetBlocks.take(session.currentTargetIndex).toSet()
            for (dir in Direction.entries) {
                val adj = targetBuildPos.relative(dir)
                if (adj in placedSet && level.getBlockState(adj.above()).isAir) {
                    return adj.above()
                }
            }
            for (i in (session.currentTargetIndex - 1) downTo 0) {
                val placedPos = session.targetBlocks[i]
                if (level.getBlockState(placedPos.above()).isAir) {
                    return placedPos.above()
                }
            }
        }

        for (dir in Direction.entries) {
            val neighbor = targetBuildPos.relative(dir)
            val below = neighbor.below()
            if (level.getBlockState(below).isSolidRender(level, below) && level.getBlockState(neighbor).isAir) {
                return neighbor
            }
        }

        var bestPos: BlockPos? = null
        var minDist = Double.MAX_VALUE
        for (dx in -3..3) {
            for (dy in -2..2) {
                for (dz in -3..3) {
                    val check = targetBuildPos.offset(dx, dy, dz)
                    val below = check.below()
                    if (level.getBlockState(below).isSolidRender(level, below) && level.getBlockState(check).isAir) {
                        val dist = check.distSqr(targetBuildPos)
                        if (dist < minDist) {
                            minDist = dist
                            bestPos = check
                        }
                    }
                }
            }
        }

        return bestPos ?: targetBuildPos.above()
    }

    private fun getBuildDelayFromSpeed(pokemon: Mob): Int {
        val spd = (pokemon as? PokemonEntity)?.pokemon?.speed ?: 50
        return (8 - (spd / 16)).coerceIn(1, 8)
    }

    private fun tickBuildBatch(
        pokemon: Mob,
        session: GatheringSession,
        level: ServerLevel,
        owner: ServerPlayer,
        iterator: MutableIterator<MutableMap.MutableEntry<UUID, GatheringSession>>
    ) {
        if (session.targetBlocks.isEmpty() || session.currentTargetIndex >= session.targetBlocks.size) {
            session.state = GatheringState.FINISHED
            sendMessage(owner, "${pokemon.displayName?.string} finished building structure!", ChatFormatting.GREEN)
            restoreFollowGoal(pokemon)
            iterator.remove()
            CommandState.activeCommands[pokemon.uuid] = "idle"
            return
        }

        val chestPos = session.chestPos ?: run {
            session.state = GatheringState.FINISHED
            restoreFollowGoal(pokemon)
            iterator.remove()
            CommandState.activeCommands[pokemon.uuid] = "idle"
            return
        }

        val targetBuildPos = session.targetBlocks[session.currentTargetIndex]

        // Refill heldBlockStack from Chest if empty
        if (session.heldBlockStack.isEmpty) {
            val distToChestSqr = pokemon.distanceToSqr(chestPos.x + 0.5, chestPos.y + 0.5, chestPos.z + 0.5)
            if (distToChestSqr > PHYSICAL_REACH_DISTANCE_SQR) {
                session.state = GatheringState.NAVIGATING
                pokemon.teleportTo(chestPos.x + 0.5, chestPos.y + 1.0, chestPos.z + 0.5)
                return
            }

            val container = level.getBlockEntity(chestPos) as? Container
            if (container == null) {
                sendMessage(owner, "Construction chest at (${chestPos.x}, ${chestPos.y}, ${chestPos.z}) missing!", ChatFormatting.RED)
                session.state = GatheringState.FINISHED
                restoreFollowGoal(pokemon)
                iterator.remove()
                CommandState.activeCommands[pokemon.uuid] = "idle"
                return
            }

            var extractedStack: ItemStack = ItemStack.EMPTY
            for (slot in 0 until container.containerSize) {
                val stack = container.getItem(slot)
                if (!stack.isEmpty && stack.item is BlockItem) {
                    val count = min(64, stack.count)
                    extractedStack = container.removeItem(slot, count)
                    break
                }
            }

            if (extractedStack.isEmpty) {
                session.state = GatheringState.NAVIGATING
                val now = System.currentTimeMillis()
                if (now - session.lastOreNoticeTime > 8000L) {
                    session.lastOreNoticeTime = now
                    sendMessage(owner, "Construction chest is empty! Add more blocks to container to continue.", ChatFormatting.YELLOW)
                }
                return
            }

            session.heldBlockStack = extractedStack
            level.playSound(null, chestPos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8f, 1.0f)
        }

        // Sequential adjacent block placement via instant positioning
        val safeNavPos = findSafeBuildStandingPos(level, targetBuildPos, session)

        // Teleport Pokémon adjacent to target block and face it directly
        pokemon.teleportTo(safeNavPos.x + 0.5, safeNavPos.y.toDouble(), safeNavPos.z + 0.5)

        val dx = (targetBuildPos.x + 0.5) - (safeNavPos.x + 0.5)
        val dy = (targetBuildPos.y + 0.5) - (safeNavPos.y + pokemon.eyeHeight.toDouble())
        val dz = (targetBuildPos.z + 0.5) - (safeNavPos.z + 0.5)
        val horizDist = sqrt(dx * dx + dz * dz)
        val yaw = (atan2(dz, dx) * (180.0 / Math.PI)).toFloat() - 90.0f
        val pitch = (-(atan2(dy, horizDist) * (180.0 / Math.PI))).toFloat()

        pokemon.yRot = yaw
        pokemon.yBodyRot = yaw
        pokemon.yHeadRot = yaw
        pokemon.xRot = pitch

        pokemon.lookControl.setLookAt(targetBuildPos.x + 0.5, targetBuildPos.y + 0.5, targetBuildPos.z + 0.5, 360.0f, 360.0f)
        session.state = GatheringState.BREAKING

        session.breakTimer++
        val buildDelay = getBuildDelayFromSpeed(pokemon)

        if (session.breakTimer >= buildDelay) {
            session.breakTimer = 0

            val blockItem = session.heldBlockStack.item as? BlockItem
            if (blockItem != null) {
                val stateToPlace = blockItem.block.defaultBlockState()

                if (pokemon.blockX == targetBuildPos.x && pokemon.blockZ == targetBuildPos.z && abs(pokemon.blockY - targetBuildPos.y) <= 1) {
                    pokemon.teleportTo(pokemon.x, targetBuildPos.y + 1.0, pokemon.z)
                }

                level.setBlock(targetBuildPos, stateToPlace, 3)
                pokemon.swing(InteractionHand.MAIN_HAND)

                level.playSound(
                    null,
                    targetBuildPos,
                    SoundEvents.STONE_PLACE,
                    SoundSource.BLOCKS,
                    1.0f,
                    0.9f + level.random.nextFloat() * 0.2f
                )

                level.sendParticles(
                    BlockParticleOption(ParticleTypes.BLOCK, stateToPlace),
                    targetBuildPos.x + 0.5,
                    targetBuildPos.y + 0.5,
                    targetBuildPos.z + 0.5,
                    10,
                    0.2, 0.2, 0.2,
                    0.05
                )

                session.heldBlockStack.shrink(1)
            }

            session.currentTargetIndex++
        }
    }
    }

    private fun getStandableTargetPos(level: Level, targetPos: BlockPos, floorY: Int): BlockPos {
        val exactFloorPos = BlockPos(targetPos.x, floorY, targetPos.z)
        if (level.getBlockState(exactFloorPos).isAir) return exactFloorPos

        for (dx in -1..1) {
            for (dz in -1..1) {
                val neighbor = BlockPos(targetPos.x + dx, floorY, targetPos.z + dz)
                if (level.getBlockState(neighbor).isAir) {
                    return neighbor
                }
            }
        }
        return exactFloorPos
    }

    private fun calculateGeometry(session: GatheringSession, pokemon: Mob, level: Level, ping: PlayerPing): Boolean {
        session.targetBlocks.clear()
        session.layers.clear()
        when (session.type) {
            GatheringType.EXCAVATE -> calculateExcavateLayers(session, pokemon, ping)
            GatheringType.BUILD -> calculateBuildGeometry(session, level)
        }

        if (session.type == GatheringType.EXCAVATE) {
            filterAndPreserveOresInLayers(session, level)
            return session.layers.any { it.isNotEmpty() }
        } else {
            filterProtectedBlocks(session.targetBlocks, level)
            return session.targetBlocks.isNotEmpty()
        }
    }

    private fun sortBlocksAdjacently(blocks: MutableList<BlockPos>, startPos: BlockPos) {
        if (blocks.size <= 1) return
        val unplaced = blocks.toMutableList()
        blocks.clear()

        var current = unplaced.minByOrNull { it.distManhattan(startPos) } ?: unplaced.first()
        blocks.add(current)
        unplaced.remove(current)

        while (unplaced.isNotEmpty()) {
            val next = unplaced.minByOrNull { pos ->
                val dx = abs(pos.x - current.x)
                val dy = abs(pos.y - current.y)
                val dz = abs(pos.z - current.z)
                val manhattan = dx + dy + dz
                if (manhattan == 1) 0.0 else manhattan.toDouble() * 10.0 + pos.distSqr(startPos)
            } ?: unplaced.first()

            blocks.add(next)
            unplaced.remove(next)
            current = next
        }
    }

    private fun calculateBuildGeometry(session: GatheringSession, level: Level): Boolean {
        val pingA = session.pingA ?: return false
        val pingB = session.pingB ?: return false
        val posA = pingA.pos
        val posB = pingB.pos

        val maxDim = 31

        val minX = min(posA.x, posB.x).coerceIn(posA.x - maxDim, posA.x + maxDim)
        var maxX = max(posA.x, posB.x).coerceIn(posA.x - maxDim, posA.x + maxDim)
        if (maxX - minX > maxDim) maxX = minX + maxDim

        val minY = min(posA.y, posB.y).coerceIn(posA.y - maxDim, posA.y + maxDim)
        var maxY = max(posA.y, posB.y).coerceIn(posA.y - maxDim, posA.y + maxDim)
        if (maxY - minY > maxDim) maxY = minY + maxDim

        val minZ = min(posA.z, posB.z).coerceIn(posA.z - maxDim, posA.z + maxDim)
        var maxZ = max(posA.z, posB.z).coerceIn(posA.z - maxDim, posA.z + maxDim)
        if (maxZ - minZ > maxDim) maxZ = minZ + maxDim

        session.targetBlocks.clear()
        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                for (x in minX..maxX) {
                    val blockPos = BlockPos(x, y, z)
                    if (level.getBlockState(blockPos).isAir || level.getBlockState(blockPos).canBeReplaced()) {
                        session.targetBlocks.add(blockPos)
                    }
                }
            }
        }

        if (session.targetBlocks.isEmpty()) return false

        sortBlocksAdjacently(session.targetBlocks, posA)

        var airUnderCount = 0
        for (pos in session.targetBlocks) {
            if (level.getBlockState(pos.below()).isAir) {
                airUnderCount++
            }
        }
        val airRatio = airUnderCount.toDouble() / session.targetBlocks.size
        session.isSuspendedBuild = airRatio >= 0.25 || (minY > level.minBuildHeight + 5 && airUnderCount > 0)

        return true
    }

    private fun calculateExcavateLayers(session: GatheringSession, pokemon: Mob, ping: PlayerPing) {
        val pingA = session.pingA ?: ping
        val pingB = session.pingB
        val startPos = pingA.pos
        val pingDir = (pingB ?: pingA).direction

        val forwardDir: Direction = if (pingDir == Direction.UP || pingDir == Direction.DOWN) {
            Direction.NORTH
        } else {
            pingDir.opposite
        }
        session.demolishForwardDir = forwardDir

        val rightDir = forwardDir.clockWise
        val upDir = Direction.UP

        if (pingB != null) {
            val posA = pingA.pos
            val posB = pingB.pos

            val minU = (min(posA.y, posB.y) - startPos.y).coerceIn(-8, 8)
            var maxU = (max(posA.y, posB.y) - startPos.y).coerceIn(-8, 8)
            if (maxU - minU > 8) maxU = minU + 8
            session.minUpOffset = minU
            session.maxUpOffset = maxU

            val deltaX = posB.x - posA.x
            val deltaZ = posB.z - posA.z

            val offsetRight = when (forwardDir) {
                Direction.NORTH -> deltaX
                Direction.SOUTH -> -deltaX
                Direction.EAST -> deltaZ
                Direction.WEST -> -deltaZ
                else -> deltaX
            }

            val r1 = 0
            val minR = min(r1, offsetRight).coerceIn(-8, 8)
            var maxR = max(r1, offsetRight).coerceIn(-8, 8)
            if (maxR - minR > 8) maxR = minR + 8
            session.minRightOffset = minR
            session.maxRightOffset = maxR
        } else {
            val pokemonHeight = pokemon.bbHeight.toDouble()
            val maxUp = if (pokemonHeight > 2.0) ceil(pokemonHeight - 1.0).toInt().coerceIn(1, 4) else 1
            session.minUpOffset = -1
            session.maxUpOffset = maxUp
            session.minRightOffset = -1
            session.maxRightOffset = 1
        }

        session.demolishCurrentDepth = 0
        val layerBlocks = mutableListOf<BlockPos>()
        for (r in session.minRightOffset..session.maxRightOffset) {
            for (u in session.minUpOffset..session.maxUpOffset) {
                val blockPos = startPos.relative(rightDir, r).relative(upDir, u)
                layerBlocks.add(blockPos)
            }
        }
        if (layerBlocks.isNotEmpty()) {
            session.layers.add(layerBlocks)
        }
    }

    private fun generateNextExcavateLayer(session: GatheringSession, level: Level, forwardDir: Direction): Boolean {
        val pingA = session.pingA ?: session.ping ?: return false
        val startPos = pingA.pos
        val rightDir = forwardDir.clockWise
        val upDir = Direction.UP

        var checkDepth = session.demolishCurrentDepth + 1
        val maxLookahead = checkDepth + 30

        while (checkDepth <= maxLookahead) {
            val layerPos = startPos.relative(forwardDir, checkDepth)
            val layerBlocks = mutableListOf<BlockPos>()
            for (r in session.minRightOffset..session.maxRightOffset) {
                for (u in session.minUpOffset..session.maxUpOffset) {
                    val blockPos = layerPos.relative(rightDir, r).relative(upDir, u)
                    val state = level.getBlockState(blockPos)
                    if (!state.isAir && state.getDestroySpeed(level, blockPos) >= 0f && level.getBlockEntity(blockPos) == null) {
                        layerBlocks.add(blockPos)
                    }
                }
            }

            filterProtectedBlocks(layerBlocks, level)
            filterOresFromList(layerBlocks, session, level)

            if (layerBlocks.isNotEmpty()) {
                session.layers.add(layerBlocks)
                session.demolishCurrentDepth = checkDepth
                return true
            }

            checkDepth++
        }

        return false
    }

    private fun filterProtectedBlocks(blocks: MutableList<BlockPos>, level: Level) {
        blocks.removeIf { pos ->
            val state = level.getBlockState(pos)
            if (state.getDestroySpeed(level, pos) < 0f) return@removeIf true
            if (level.getBlockEntity(pos) != null) return@removeIf true

            val block = state.block
            block == Blocks.NETHER_PORTAL ||
            block == Blocks.END_PORTAL ||
            block == Blocks.END_GATEWAY ||
            block == Blocks.COMMAND_BLOCK ||
            block == Blocks.CHAIN_COMMAND_BLOCK ||
            block == Blocks.REPEATING_COMMAND_BLOCK ||
            block == Blocks.STRUCTURE_BLOCK ||
            block == Blocks.JIGSAW
        }
    }

    private fun filterAndPreserveOresInLayers(session: GatheringSession, level: Level) {
        for (layer in session.layers) {
            filterProtectedBlocks(layer, level)
            filterOresFromList(layer, session, level)
        }
    }

    private fun filterOresFromList(blocks: MutableList<BlockPos>, session: GatheringSession, level: Level) {
        val oreIndicesToRemove = mutableListOf<BlockPos>()
        for (pos in blocks) {
            val state = level.getBlockState(pos)
            if (isOreBlock(state)) {
                oreIndicesToRemove.add(pos)
            }
        }

        if (oreIndicesToRemove.isNotEmpty()) {
            blocks.removeAll(oreIndicesToRemove.toSet())
            val now = System.currentTimeMillis()
            if (now - session.lastOreNoticeTime > 10_000L) {
                session.lastOreNoticeTime = now
                val owner = level.server?.playerList?.getPlayer(session.ownerUuid)
                if (owner != null) {
                    sendMessage(
                        owner,
                        "Pokémon discovered an ore vein and preserved it!",
                        ChatFormatting.GOLD
                    )
                }
            }
        }
    }

    private fun isOreBlock(state: BlockState): Boolean {
        val name = BuiltInRegistries.BLOCK.getKey(state.block).path.lowercase()
        return name.endsWith("_ore") || name.startsWith("raw_") && name.endsWith("_block") || name == "ancient_debris"
    }

    private fun getBreakDelay(type: GatheringType): Int {
        return when (type) {
            GatheringType.EXCAVATE -> ConfigHandler.config.actionSettings.excavate.breakDelayTicks
            GatheringType.BUILD -> 4
        }
    }

    private fun sendMessage(player: ServerPlayer, message: String, color: ChatFormatting) {
        player.sendSystemMessage(Component.literal(message).withStyle(color))
    }
