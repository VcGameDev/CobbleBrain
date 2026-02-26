package vito.cobblebrain.social

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.phys.Vec3
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import vito.cobblebrain.client.social.CobblebrainWorldSave
import vito.cobblebrain.config.ConfigHandler
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object WorldEventsSystem {

    private const val CHECK_INTERVAL = 200
    private const val RAID_COOLDOWN_TICKS = 12000 // 10 minutos
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

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->

            if (!ConfigHandler.config.scheduleRaids) return@register

            if (raidCooldown > 0) raidCooldown--

            if (server.tickCount % CHECK_INTERVAL == 0 && raidCooldown <= 0) {
                for (player in server.playerList.players) {
                    checkForRaid(player)
                }
            }

            processPendingRaids()
            processActiveRaids()
        }
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

                // Apenas raides para karma <= -12
                if (karma > -12) return@forEach

                val chance = (abs(karma) * 0.015).coerceAtMost(0.6)

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

            val spawnPos = Vec3(
                basePos.x + offsetX,
                basePos.y,
                basePos.z + offsetZ
            )

            val properties = PokemonProperties()
            properties.species = (PokemonSpecies.getByName(speciesName.lowercase())
                ?: continue).toString()

            properties.level = Random.nextInt(minLevel, maxLevel + 1)

            val pokemon = properties.createEntity(world)

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

            // Se player morreu → raid termina + karma +10
            if (!player.isAlive) {

                val saveData = CobblebrainWorldSave.data
                val karmaObj = saveData.getAsJsonObject("karma")

                if (karmaObj != null) {
                    val current = karmaObj.get(raid.speciesName)?.asInt ?: 0
                    karmaObj.addProperty(raid.speciesName, current + 10)
                }

                // remove pokémons restantes
                raid.spawnedPokemon.forEach {
                    if (it.isAlive) {
                        it.discard()
                    }
                }

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

                // 🔥 ATAQUE MANUAL
                val distance = pokemon.distanceTo(player)
                val inRange = distance <= 1.5

                val pokemonId = pokemon.uuid
                val cd = attackCooldowns.getOrDefault(pokemonId, 0)

                if (inRange && cd <= 0) {

                    val scaledDamage = pokemon.pokemon.level * 0.25f

                    player.hurt(
                        pokemon.damageSources().mobAttack(pokemon),
                        scaledDamage
                    )

                    pokemon.swing(InteractionHand.MAIN_HAND)

                    attackCooldowns[pokemonId] = 10 // 0.5s (10 ticks)

                } else {
                    attackCooldowns[pokemonId] = maxOf(0, cd - 1)
                }
            }
        }
    }

    private fun getDifficulty(karma: Int): RaidDifficulty? {
        return when {
            karma <= -12 && karma > -22 -> RaidDifficulty.EASY
            karma <= -22 && karma > -32 -> RaidDifficulty.MEDIUM
            karma <= -32 -> RaidDifficulty.HARD
            else -> null
        }
    }
}
