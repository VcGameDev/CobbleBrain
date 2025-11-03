package vito.cobblebrain.sensors

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal
import net.minecraft.world.phys.AABB
import vito.cobblebrain.mixin.MobAccessor
import java.util.UUID

// 1) Estrutura do comando
data class PokemonCommand(
    val pokemonName: String,
    val action: String
)

// 2) Parser simples
fun parseCommand(line: String): PokemonCommand? {
    if (!line.startsWith("#")) return null
    val parts = line.removePrefix("#").split(":")
    if (parts.size < 2) return null

    val pokemonName = parts[0].trim()
    val action = parts[1].trim().lowercase()

    return PokemonCommand(pokemonName, action)
}

// 3) Estado global
object CommandState {
    val activeCommands: MutableMap<UUID, String> = mutableMapOf()
    val activeTargets: MutableMap<UUID, UUID> = mutableMapOf()
}

// guarda goals removidos para restaurar depois
private val disabledGoals: MutableMap<UUID, List<net.minecraft.world.entity.ai.goal.Goal>> = mutableMapOf()

private fun enterAttackMode(pokemon: Mob) {
    val mobAccessor = pokemon as MobAccessor
    val toDisable = mobAccessor.goalSelector.availableGoals
        .map { it.goal }
        .filter { it is FollowOwnerGoal }

    if (toDisable.isNotEmpty()) {
        disabledGoals[pokemon.uuid] = toDisable
        toDisable.forEach { mobAccessor.goalSelector.removeGoal(it) }
    }

    pokemon.isAggressive = true
}

private fun exitAttackMode(pokemon: Mob) {
    val mobAccessor = pokemon as MobAccessor
    disabledGoals.remove(pokemon.uuid)?.forEach { goal ->
        mobAccessor.goalSelector.addGoal(2, goal) // prioridade 2 é exemplo
    }
    pokemon.isAggressive = false
    pokemon.target = null
}

// cooldown de ataque por Pokémon
private val attackCooldowns: MutableMap<UUID, Int> = mutableMapOf()

fun registerTickHandler() {
    var tickCounter = 0

    ServerTickEvents.END_SERVER_TICK.register { server ->
        tickCounter++
        if (tickCounter % 2 != 0) return@register // roda a cada 2 ticks (~0.1s)

        val level = server.overworld() as ServerLevel

        CommandState.activeCommands.forEach { (pokemonId, action) ->
            val pokemon = level.getEntity(pokemonId) as? Mob ?: return@forEach

            when (action) {
                "attack" -> {
                    enterAttackMode(pokemon)

                    val target = CommandState.activeTargets[pokemonId]?.let { level.getEntity(it) as? LivingEntity }
                    val finalTarget = if (target != null && target.isAlive) {
                        target
                    } else {
                        findClosestNonPlayerLiving(level, pokemon)?.also {
                            CommandState.activeTargets[pokemonId] = it.uuid
                        }
                    }

                    if (finalTarget == null || !finalTarget.isAlive) {
                        exitAttackMode(pokemon)
                        CommandState.activeTargets.remove(pokemonId)
                        return@forEach
                    }

                    if (pokemon.distanceTo(finalTarget) > 32f) {
                        exitAttackMode(pokemon)
                        CommandState.activeTargets.remove(pokemonId)
                        return@forEach
                    }

                    // perseguição contínua
                    pokemon.target = finalTarget
                    pokemon.navigation.moveTo(finalTarget, 1.2)

                    // alcance dinâmico
                    val reach = (pokemon.bbWidth * 2.0f) + 1.5f
                    val inRange = pokemon.distanceToSqr(finalTarget) <= (reach * reach) &&
                            pokemon.hasLineOfSight(finalTarget)

                    // cooldown de ataque
                    val cd = attackCooldowns.getOrDefault(pokemonId, 0)
                    if (inRange && cd <= 0) {
                        finalTarget.hurt(pokemon.damageSources().mobAttack(pokemon), 2.0f)
                        pokemon.swing(InteractionHand.MAIN_HAND)
                        attackCooldowns[pokemonId] = 20 // 1s de recarga
                    } else {
                        attackCooldowns[pokemonId] = maxOf(0, cd - 1)
                    }
                }

                "idle" -> {
                    exitAttackMode(pokemon)
                    CommandState.activeTargets.remove(pokemonId)
                }
            }
        }
    }
}

private fun findClosestNonPlayerLiving(level: ServerLevel, source: LivingEntity): LivingEntity? {
    val range = 16.0
    val box = AABB(
        source.x - range, source.y - range, source.z - range,
        source.x + range, source.y + range, source.z + range
    )
    val list = level.getEntitiesOfClass(LivingEntity::class.java, box) { e ->
        e !is ServerPlayer && e.isAlive && e != source
    }
    return list.minByOrNull { it.distanceTo(source) }
}