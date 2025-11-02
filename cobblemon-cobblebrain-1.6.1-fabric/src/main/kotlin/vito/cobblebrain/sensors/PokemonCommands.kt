package vito.cobblebrain.sensors

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.AABB
import vito.cobblebrain.CobblebrainMod.config
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

// 4) Tick handler
fun registerTickHandler() {
    var tickCounter = 0

    ServerTickEvents.END_SERVER_TICK.register { server ->
        tickCounter++
        if (tickCounter % 20 != 0) return@register // a cada 20 ticks (~1s)

        val level = server.overworld() as ServerLevel

        CommandState.activeCommands.forEach { (pokemonId, action) ->
            val pokemon = level.getEntity(pokemonId) as? Mob ?: return@forEach

            if (action == "attack") {
                val target = CommandState.activeTargets[pokemonId]?.let { level.getEntity(it) as? LivingEntity }

                val finalTarget = if (target != null && target.isAlive) {
                    target
                } else {
                    findClosestNonPlayerLiving(level, pokemon)?.also {
                        CommandState.activeTargets[pokemonId] = it.uuid
                    }
                }

                if (finalTarget != null) {
                    pokemon.target = finalTarget
                    val attackRange = 2.5

                    //val isMental = pokemon.type // ou como você identifica o tipo
                    val telepathyEnabled = config.telepaticDamage

                    val canHitNormally = pokemon.distanceTo(finalTarget) <= attackRange && pokemon.hasLineOfSight(finalTarget)
                    //val canHitTelepathically = isMental && telepathyEnabled
                    val canHitTelepathically = false

                    if (canHitNormally || canHitTelepathically) {
                        finalTarget.hurt(
                            pokemon.damageSources().mobAttack(pokemon),
                            2.0f // dano base, pode ser diferente se for telepático
                        )
                    }
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
