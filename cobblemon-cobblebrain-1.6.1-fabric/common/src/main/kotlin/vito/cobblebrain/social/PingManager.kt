package vito.cobblebrain.social

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtAccounter
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.LevelResource
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PlayerPing(
    val pos: BlockPos,
    val dimension: ResourceKey<Level>,
    val timestamp: Long,
    val direction: Direction
)

object PingManager {

    private val pings = ConcurrentHashMap<UUID, PlayerPing>()
    private var saveFile: File? = null

    // Pokémon UUID → Player UUID: tracks which Pokémon are moving toward a ping.
    val activePingTargets: MutableMap<UUID, UUID> = ConcurrentHashMap()

    fun removePingTarget(pokemonUuid: UUID) {
        activePingTargets.remove(pokemonUuid)
    }

    // Máximo de distância permitida para o ping (servidor e cliente usam o mesmo limite)
    const val MAX_PING_DISTANCE = 64.0

    fun init(server: MinecraftServer) {
        val dataDir = server.getWorldPath(LevelResource.ROOT).resolve("data").toFile()
        dataDir.mkdirs()
        saveFile = File(dataDir, "cobblebrainPings.dat")
        load()
    }

    fun getPing(playerId: UUID): PlayerPing? = pings[playerId]

    fun setPing(playerId: UUID, ping: PlayerPing) {
        pings[playerId] = ping
        save()
    }

    fun removePing(playerId: UUID) {
        pings.remove(playerId)
        save()
    }

    fun hasPing(playerId: UUID): Boolean = pings.containsKey(playerId)

    fun handlePingPacket(
        player: ServerPlayer,
        pos: BlockPos,
        direction: Direction
    ): Boolean {

        val distanceSq =
            player.distanceToSqr(
                pos.x + 0.5,
                pos.y + 0.5,
                pos.z + 0.5
            )

        if (
            distanceSq >
            MAX_PING_DISTANCE *
            MAX_PING_DISTANCE
        ) {
            return false
        }

        val dimension =
            player.serverLevel()
                .dimension()

        val ping =
            PlayerPing(
                pos,
                dimension,
                System.currentTimeMillis(),
                direction
            )

        setPing(
            player.uuid,
            ping
        )

        val party =
            Cobblemon.storage.getParty(
                player
            )

        party.forEach { pokemon ->

            pokemon.entity?.let {
                activePingTargets[it.uuid] = player.uuid
            }
        }

        return true
    }

    fun tickPingMovement(server: MinecraftServer) {
        val toRemove = mutableListOf<UUID>()

        for ((pokemonEntityUuid, ownerUuid) in activePingTargets) {
            val ping = getPing(ownerUuid)
            if (ping == null) {
                // Ping was removed or expired — stop tracking
                toRemove.add(pokemonEntityUuid)
                continue
            }

            val pokemon = server.allLevels
                .firstNotNullOfOrNull { it.getEntity(pokemonEntityUuid) as? Mob }

            if (pokemon == null || !pokemon.isAlive) {
                // Entity gone — clean up
                toRemove.add(pokemonEntityUuid)
                continue
            }

            val distSq = pokemon.distanceToSqr(
                ping.pos.x + 0.5,
                ping.pos.y + 0.5,
                ping.pos.z + 0.5
            )

            if (distSq < 4.0) {
                // Arrived — clear the ping target
                toRemove.add(pokemonEntityUuid)
            } else {
                pokemon.navigation.moveTo(
                    ping.pos.x + 0.5,
                    ping.pos.y.toDouble(),
                    ping.pos.z + 0.5,
                    1.2
                )
            }
        }

        toRemove.forEach { activePingTargets.remove(it) }
    }

    // -------------------------------------------------------------------------
    // NBT persistence
    // -------------------------------------------------------------------------

    private fun load() {
        pings.clear()
        val file = saveFile ?: return
        if (!file.exists()) return

        try {
            val root: CompoundTag = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap())
            val pingList = root.getCompound("pings")
            for (key in pingList.allKeys) {
                val uuid = UUID.fromString(key)
                val entry = pingList.getCompound(key)
                val x = entry.getInt("x")
                val y = entry.getInt("y")
                val z = entry.getInt("z")
                val dimStr = entry.getString("dimension")
                val timestamp =
                    entry.getLong("timestamp")

                val direction =
                    if (
                        entry.contains("direction")
                    ) {

                        Direction.valueOf(
                            entry.getString(
                                "direction"
                            )
                        )

                    } else {

                        Direction.UP
                    }

                val parts = dimStr.split(":", limit = 2)
                val loc = if (parts.size == 2) {
                    ResourceLocation.fromNamespaceAndPath(parts[0], parts[1])
                } else {
                    ResourceLocation.fromNamespaceAndPath("minecraft", dimStr)
                }
                val dimKey = ResourceKey.create(Registries.DIMENSION, loc)
                pings[uuid] =
                    PlayerPing(
                        BlockPos(x, y, z),
                        dimKey,
                        timestamp,
                        direction
                    )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun save() {
        val file = saveFile ?: return
        try {
            val root = CompoundTag()
            val pingList = CompoundTag()
            for ((uuid, ping) in pings) {
                val entry = CompoundTag()
                entry.putInt("x", ping.pos.x)
                entry.putInt("y", ping.pos.y)
                entry.putInt("z", ping.pos.z)
                entry.putString("dimension", ping.dimension.location().toString())
                entry.putLong(
                    "timestamp",
                    ping.timestamp
                )

                entry.putString(
                    "direction",
                    ping.direction.name
                )
                pingList.put(uuid.toString(), entry)
            }
            root.put("pings", pingList)
            NbtIo.writeCompressed(root, file.toPath())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
