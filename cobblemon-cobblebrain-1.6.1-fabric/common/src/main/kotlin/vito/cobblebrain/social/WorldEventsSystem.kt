package vito.cobblebrain.social

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.phys.Vec3
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.levelgen.Heightmap
import vito.cobblebrain.config.ConfigHandler
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object WorldEventsSystem {
    private const val CHECK_INTERVAL = 300
    private const val RAID_COOLDOWN_TICKS = 9000 // 7 minutos e 30 segundos
    private const val RAID_DELAY_TICKS = 1200 // 1 minuto
    private const val SPAWN_RADIUS_MIN = 4.0
    private const val SPAWN_RADIUS_MAX = 15.0
    private const val MAX_WORLD_RAIDS = 3

    private var raidCooldown = 0

    private val attackCooldowns: MutableMap<UUID, Int> = mutableMapOf()

    private data class PendingRaid(
        val player: ServerPlayer,
        val speciesName: String,
        val karma: Int,
        var ticksRemaining: Int
    )

    private data class ActiveRaid(
        val player: ServerPlayer,
        val speciesName: String,
        val spawnedPokemon: MutableList<PokemonEntity> = mutableListOf()
    )

    private val pendingRaids = mutableListOf<PendingRaid>()
    private val activeRaids = mutableListOf<ActiveRaid>()

    private enum class RaidDifficulty { EASY, MEDIUM, HARD }

    private val legendarySpecies = setOf(
        "Mewtwo", "Mew", "Zapdos", "Moltres", "Articuno",
        "Raikou", "Entei", "Suicune", "Lugia", "Ho-Oh",
        "Kyogre", "Groudon", "Rayquaza", "Dialga", "Palkia",
        "Giratina", "Reshiram", "Zekrom", "Kyurem", "Xerneas",
        "Yveltal", "Zygarde", "Solgaleo", "Lunala", "Necrozma",
        "Zacian", "Zamazenta", "Eternatus", "Arceus"
    )

    private val pseudoLegendarySpecies = setOf(
        "Dragonite", "Tyranitar", "Salamence", "Metagross",
        "Garchomp", "Hydreigon", "Goodra", "Kommo-o", "Dragapult",
        "Slowpoke","Slowbro"
    )

    fun onServerTick(server: MinecraftServer) {
        if (!ConfigHandler.config.scheduleRaids) return

        if (raidCooldown > 0) raidCooldown--

        if (server.tickCount % CHECK_INTERVAL == 0 && raidCooldown <= 0) {
            for (player in server.playerList.players) {
                checkForRaid(player)
            }
        }

        processPendingRaids()
        processActiveRaids()
    }

    private fun checkForRaid(player: ServerPlayer) {
        if (activeRaids.any { it.player == player }) return
        if (activeRaids.size >= MAX_WORLD_RAIDS) return

        val saveData = CobblebrainWorldSave.data
        if (!saveData.has("karma")) return

        val world = player.serverLevel()

        val karmaRoot = saveData.getAsJsonObject("karma") ?: return
        val playerObj = karmaRoot.getAsJsonObject(player.uuid.toString()) ?: return
        playerObj.entrySet()
            .shuffled() // embaralha a ordem
            .forEach { (speciesName, karmaJson) ->

                if (speciesName in legendarySpecies) return@forEach
                if (speciesName in pseudoLegendarySpecies) return@forEach

                val karma = karmaJson.asInt

                // Apenas raides para karma <= -8
                if (karma > -8) return@forEach

                val chance = (0.03 + (abs(karma) - 8) * (0.37 / 22.0))
                    .coerceAtMost(0.4)

                if (Random.nextDouble() <= chance) {
                    scheduleRaid(world, player, speciesName, karma)
                    raidCooldown = RAID_COOLDOWN_TICKS
                    return@forEach // sai do forEach após agendar
                }
            }
    }

    private fun scheduleRaid(
        world: ServerLevel,
        player: ServerPlayer,
        speciesName: String,
        karma: Int
    ) {

        val difficulty = getDifficulty(karma)

        player.sendSystemMessage(
            Component.literal(
                "You hear a rumble and feel that something is coming... it seems like you only have 1 minute to get ready..."
            ).withStyle(ChatFormatting.RED)
        )

        when (difficulty) {
            RaidDifficulty.MEDIUM ->
                world.setWeatherParameters(0, 1200, true, false)
            RaidDifficulty.HARD ->
                world.setWeatherParameters(0, 2400, true, true)
            else -> {}
        }

        if (difficulty != RaidDifficulty.EASY) {
            world.playSound(
                null,
                player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER,
                2.0f,
                1.0f
            )
        }

        pendingRaids.add(
            PendingRaid(player, speciesName, karma, RAID_DELAY_TICKS)
        )
    }

    private fun processPendingRaids() {

        val iterator = pendingRaids.iterator()

        while (iterator.hasNext()) {
            val raid = iterator.next()

            raid.ticksRemaining--

            if (raid.ticksRemaining <= 0) {
                if (raid.player.isAlive) {
                    spawnRaid(
                        raid.player.serverLevel(),
                        raid.player,
                        raid.speciesName,
                        raid.karma
                    )
                }
                iterator.remove()
            }
        }
    }

    private fun spawnRaid(
        world: ServerLevel,
        player: ServerPlayer,
        speciesName: String,
        karma: Int
    ) {

        val basePos = player.position()
        val extra = abs(karma)

        val count = (4 + extra / 5).coerceIn(4, 15)
        val minLevel = 5
        val maxLevel = (10 + extra).coerceAtMost(40)

        val activeRaid = ActiveRaid(player, speciesName)

        for (i in 0 until count) {

            val distance = Random.nextDouble(SPAWN_RADIUS_MIN, SPAWN_RADIUS_MAX)
            val angle = Random.nextDouble(0.0, Math.PI * 2)

            val offsetX = cos(angle) * distance
            val offsetZ = sin(angle) * distance

            val groundY = world.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (basePos.x + offsetX).toInt(),
                (basePos.z + offsetZ).toInt()
            )

            val spawnPos = Vec3(
                basePos.x + offsetX,
                groundY.toDouble(),
                basePos.z + offsetZ
            )

            val properties = PokemonProperties()
            val species = PokemonSpecies.getByName(speciesName.lowercase()) ?: continue
            properties.species = species.resourceIdentifier.toString()

            properties.level = Random.nextInt(minLevel, maxLevel + 1)

            val pokemon = try {
                properties.createEntity(world)
            } catch (e: Exception) {
                println("[RAID ERROR] Failed to create entity for $speciesName")
                e.printStackTrace()
                continue
            }

            pokemon.moveTo(
                spawnPos.x,
                spawnPos.y,
                spawnPos.z,
                Random.nextFloat() * 360f,
                0f
            )

            pokemon.finalizeSpawn(
                world,
                world.getCurrentDifficultyAt(player.blockPosition()),
                MobSpawnType.EVENT,
                null
            )

            pokemon.isAggressive = true
            pokemon.setPersistenceRequired()
            pokemon.target = player

            world.addFreshEntity(pokemon)

            activeRaid.spawnedPokemon.add(pokemon)
        }

        activeRaids.add(activeRaid)

        player.sendSystemMessage(
            Component.literal(
                "A group of $count $speciesName wants to attack you because of your actions against them (level range: $minLevel - $maxLevel)..."
            ).withStyle(ChatFormatting.RED)
        )
    }

    private fun processActiveRaids() {

        val iterator = activeRaids.iterator()

        while (iterator.hasNext()) {
            val raid = iterator.next()
            val player = raid.player

            // DERROTA: player morreu
            if (!player.isAlive) {

                val saveData = CobblebrainWorldSave.data
                val karmaRoot = saveData.getAsJsonObject("karma")

                if (karmaRoot != null) {
                    val playerObj = karmaRoot.getAsJsonObject(player.uuid.toString())

                    if (playerObj != null) {
                        playerObj.addProperty(raid.speciesName, 0)
                    }
                }

                // remove pokémons restantes
                raid.spawnedPokemon.forEach {
                    if (it.isAlive) {
                        it.discard()
                    }
                }

                player.sendSystemMessage(
                    Component.literal("You were defeated... The Pokémon seem to calm down...")
                        .withStyle(ChatFormatting.RED)
                )

                iterator.remove()
                continue
            }

            val pokemonIterator = raid.spawnedPokemon.iterator()

            while (pokemonIterator.hasNext()) {

                val pokemon = pokemonIterator.next()

                if (!pokemon.isAlive) {
                    pokemonIterator.remove()
                    attackCooldowns.remove(pokemon.uuid)
                    continue
                }

                // Força target
                if (pokemon.target != player) {
                    pokemon.target = player
                }

                // Move até o player
                pokemon.navigation.moveTo(player, 0.6)

                // ATAQUE MANUAL
                val distance = pokemon.distanceTo(player)
                val inRange = distance <= 1.5

                val pokemonId = pokemon.uuid
                val cd = attackCooldowns.getOrDefault(pokemonId, 0)

                if (inRange && cd <= 0) {

                    val pokeData = pokemon.pokemon
                    val scaledDamage = pokeData.level * 0.25f

                    player.hurt(
                        pokemon.damageSources().mobAttack(pokemon),
                        scaledDamage
                    )

                    pokemon.swing(InteractionHand.MAIN_HAND)

                    attackCooldowns[pokemonId] = 10

                } else {
                    attackCooldowns[pokemonId] = maxOf(0, cd - 1)
                }
            }

            // VITÓRIA: todos pokémons morreram
            if (raid.spawnedPokemon.isEmpty()) {

                val saveData = CobblebrainWorldSave.data
                val karmaRoot = saveData.getAsJsonObject("karma")

                if (karmaRoot != null) {
                    val playerObj = karmaRoot.getAsJsonObject(player.uuid.toString())

                    if (playerObj != null) {
                        // RESETA KARMA PRA 0
                        playerObj.addProperty(raid.speciesName, 0)
                    }
                }

                player.sendSystemMessage(
                    Component.literal("You survived the raid. The Pokémon seem to calm down...")
                        .withStyle(ChatFormatting.GREEN)
                )

                iterator.remove()
                continue
            }
        }
    }

    private fun getDifficulty(karma: Int): RaidDifficulty? {
        return when {
            karma <= -8 && karma > -19 -> RaidDifficulty.EASY
            karma <= -19 && karma > -30 -> RaidDifficulty.MEDIUM
            karma <= -30 -> RaidDifficulty.HARD
            else -> null
        }
    }

    fun spawnPokemon(
        world: ServerLevel,
        speciesName: String,
        x: Int,
        y: Int,
        z: Int
    ): PokemonEntity? {

        val properties = PokemonProperties()

        val species = PokemonSpecies.getByName(speciesName.lowercase()) ?: return null
        properties.species = species.resourceIdentifier.toString()

        // nível simples (pode ajustar depois)
        properties.level = Random.nextInt(10, 40)

        val pokemon = try {
            properties.createEntity(world)
        } catch (e: Exception) {
            println("[SPAWN ERROR] Failed to create entity for $speciesName")
            e.printStackTrace()
            return null
        }

        pokemon.moveTo(
            x.toDouble(),
            y.toDouble(),
            z.toDouble(),
            Random.nextFloat() * 360f,
            0f
        )

        pokemon.finalizeSpawn(
            world,
            world.getCurrentDifficultyAt(BlockPos(x, y, z)),
            MobSpawnType.EVENT,
            null
        )

        // comportamento leve (não agressivo igual raid)
        pokemon.setPersistenceRequired()

        world.addFreshEntity(pokemon)

        return pokemon
    }
}
