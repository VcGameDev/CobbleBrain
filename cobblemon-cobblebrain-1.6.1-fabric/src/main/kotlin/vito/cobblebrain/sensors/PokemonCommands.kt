package vito.cobblebrain.sensors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Items
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

// contador de idle/protect
private val chaseCooldown: MutableMap<UUID, Int> = mutableMapOf()

fun registerTickHandler() {
    var tickCounter = 0

    ServerTickEvents.END_SERVER_TICK.register { server ->
        tickCounter++
        if (tickCounter % 2 != 0) return@register // roda a cada 2 ticks (~0.1s)

        val level = server.overworld() as ServerLevel

        CommandState.activeCommands.forEach { (pokemonId, action) ->
            val eatCooldown = mutableMapOf<UUID, Int>()
            val pokemon = level.getEntity(pokemonId) as? Mob ?: return@forEach
            val cobblemonPokemon = (pokemon as? PokemonEntity)?.pokemon ?: return@forEach

            val atk = cobblemonPokemon.attack
            val spd = cobblemonPokemon.speed
            val scaledDamage = 2.0f + (atk * 0.3f)
            val speed = 1 + (spd * 0.05)

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
                        CommandState.activeCommands[pokemonId] = "idle"
                        return@forEach
                    }

                    pokemon.target = finalTarget
                    pokemon.navigation.moveTo(finalTarget, speed)

                    val reach = (pokemon.bbWidth * 2.0f) + 1.5f
                    val inRange = pokemon.distanceToSqr(finalTarget) <= (reach * reach) &&
                            pokemon.hasLineOfSight(finalTarget)

                    val cd = attackCooldowns.getOrDefault(pokemonId, 0)
                    if (inRange && cd <= 0) {
                        finalTarget.hurt(pokemon.damageSources().mobAttack(pokemon), scaledDamage)
                        pokemon.swing(InteractionHand.MAIN_HAND)
                        attackCooldowns[pokemonId] = 20
                    } else {
                        attackCooldowns[pokemonId] = maxOf(0, cd - 1)
                    }
                }

                "protect" -> {
                    enterAttackMode(pokemon)

                    val ownerUUID = pokemon.ownerUUID ?: return@forEach
                    val owner = level.server.playerList.getPlayer(ownerUUID) ?: return@forEach

                    val attacker = owner.lastHurtByMob
                    val victim = owner.lastHurtMob

                    // mobs que estão perseguindo o player
                    val pursuer = level.getEntitiesOfClass(Mob::class.java, owner.boundingBox.inflate(16.0)) { mob ->
                        mob.isAlive && mob.target == owner && mob !is PokemonEntity
                    }.minByOrNull { it.distanceTo(owner) }

                    // prioridade de alvo
                    val hostile = when {
                        attacker != null && attacker.isAlive -> attacker
                        victim != null && victim.isAlive -> victim
                        pursuer != null && pursuer.isAlive -> pursuer
                        else -> findClosestMonster(level, owner)
                    }


                    val finalTarget = if (hostile != null && hostile.isAlive) {
                        hostile.also { CommandState.activeTargets[pokemonId] = it.uuid }
                    } else null

                    if (finalTarget == null) {
                        // sem alvo → cola no player
                        pokemon.navigation.moveTo(owner, speed)
                        pokemon.target = null

                        val idleTicks = chaseCooldown.getOrDefault(pokemonId, 0)
                        if (idleTicks > 100) {
                            exitAttackMode(pokemon)
                            CommandState.activeTargets.remove(pokemonId)
                            CommandState.activeCommands[pokemonId] = "idle"
                            chaseCooldown[pokemonId] = 0
                        } else {
                            chaseCooldown[pokemonId] = idleTicks + 1
                        }
                        return@forEach
                    }

                    // herdando comportamento do attack
                    pokemon.target = finalTarget
                    pokemon.navigation.moveTo(finalTarget, speed)

                    val reach = (pokemon.bbWidth * 2.0f) + 1.5f
                    val inRange = pokemon.distanceToSqr(finalTarget) <= (reach * reach) &&
                            pokemon.hasLineOfSight(finalTarget)

                    val cd = attackCooldowns.getOrDefault(pokemonId, 0)
                    if (inRange && cd <= 0) {
                        finalTarget.hurt(pokemon.damageSources().mobAttack(pokemon), scaledDamage)
                        pokemon.swing(InteractionHand.MAIN_HAND)
                        attackCooldowns[pokemonId] = 20
                    } else {
                        attackCooldowns[pokemonId] = maxOf(0, cd - 1)
                    }
                }

                "heal" -> {
                    val ownerUUID = pokemon.ownerUUID ?: return@forEach
                    val owner = level.server.playerList.getPlayer(ownerUUID) ?: return@forEach
                    owner.heal(4.0f)
                    CommandState.activeCommands[pokemonId] = "idle"
                }

                "eat" -> {
                    val edibleItems = setOf(
                        Items.APPLE,
                        Items.BREAD,
                        Items.CARROT,
                        Items.GOLDEN_CARROT,
                        Items.POTATO,
                        Items.BAKED_POTATO,
                        Items.BEETROOT,
                        Items.BEETROOT_SOUP,
                        Items.MELON_SLICE,
                        Items.PUMPKIN_PIE,
                        Items.COOKIE,
                        Items.HONEY_BOTTLE,
                        Items.MUSHROOM_STEW,
                        Items.RABBIT_STEW,
                        Items.SUSPICIOUS_STEW,
                        Items.COOKED_BEEF,
                        Items.COOKED_CHICKEN,
                        Items.COOKED_MUTTON,
                        Items.COOKED_PORKCHOP,
                        Items.COOKED_RABBIT,
                        Items.COOKED_SALMON,
                        Items.COOKED_COD,
                        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "oran_berry")),
                        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "sitrus_berry")),
                        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "lum_berry")),
                        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "pecha_berry")),
                        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "rawst_berry")),
                        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "chesto_berry")),
                        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "leppa_berry")),
                        BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "persim_berry"))
                        // aqui você pode adicionar berries do Cobblemon
                    )

                    val box = pokemon.boundingBox.inflate(8.0)
                    val items = level.getEntitiesOfClass(ItemEntity::class.java, box)

                    val foodItem = items
                        .filter { it.item.item in edibleItems }
                        .minByOrNull { it.distanceTo(pokemon) }

                    if (foodItem != null && foodItem.isAlive) {
                        pokemon.navigation.moveTo(foodItem, 1.0)

                        if (pokemon.distanceTo(foodItem) < 2.0f) {
                            // toca som de comer
                            level.playSound(null, pokemon.blockPosition(), foodItem.item.item.eatingSound, SoundSource.NEUTRAL, 1.0f, 1.0f)

                            // consome 1 item do stack
                            foodItem.item.shrink(1)
                            if (foodItem.item.isEmpty) {
                                foodItem.discard()
                            }

                            // reinicia timer
                            eatCooldown[pokemon.uuid] = 0
                        }
                    } else {
                        val ticks = eatCooldown.getOrDefault(pokemon.uuid, 0)
                        if (ticks > 180) { // 9 segundos sem comida
                            CommandState.activeCommands[pokemonId] = "idle"
                            eatCooldown[pokemon.uuid] = 0
                        } else {
                            eatCooldown[pokemon.uuid] = ticks + 1
                        }
                    }
                }


                "stop" -> {
                    pokemon.target = null
                    pokemon.navigation.stop()
                }

                "debuff" -> {
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
                        CommandState.activeCommands[pokemonId] = "idle"
                        return@forEach
                    }

                    pokemon.target = finalTarget
                    pokemon.navigation.moveTo(finalTarget, speed)

                    val reach = (pokemon.bbWidth * 2.0f) + 1.5f
                    val inRange = pokemon.distanceToSqr(finalTarget) <= (reach * reach) &&
                            pokemon.hasLineOfSight(finalTarget)

                    val cd = attackCooldowns.getOrDefault(pokemonId, 0)
                    if (inRange && cd <= 0) {
                        // dano físico
                        finalTarget.hurt(pokemon.damageSources().mobAttack(pokemon), scaledDamage)

                        // debuff baseado no tipo primário
                        val primaryType = cobblemonPokemon.types.firstOrNull()?.name?.lowercase() ?: "normal"
                        val effect = when (primaryType) {
                            "grass" -> MobEffects.POISON
                            "poison" -> MobEffects.POISON
                            "fire" -> MobEffects.WITHER
                            "water" -> MobEffects.MOVEMENT_SLOWDOWN
                            "electric" -> MobEffects.GLOWING
                            "ice" -> MobEffects.MOVEMENT_SLOWDOWN
                            "fighting" -> MobEffects.WEAKNESS
                            "ground" -> MobEffects.MOVEMENT_SLOWDOWN
                            "flying" -> MobEffects.LEVITATION
                            "psychic" -> MobEffects.LEVITATION
                            "bug" -> MobEffects.MOVEMENT_SLOWDOWN
                            "rock" -> MobEffects.MOVEMENT_SLOWDOWN
                            "ghost" -> MobEffects.WITHER
                            "dragon" -> MobEffects.WEAKNESS
                            "dark" -> MobEffects.WITHER
                            "steel" -> MobEffects.MOVEMENT_SLOWDOWN
                            "fairy" -> MobEffects.WEAKNESS
                            "normal" -> MobEffects.WEAKNESS
                            else -> MobEffects.WEAKNESS
                        }

                        finalTarget.addEffect(MobEffectInstance(effect, 600, 1)) // 30 segundos
                        pokemon.swing(InteractionHand.MAIN_HAND)

                        attackCooldowns[pokemonId] = 20 // 1 segundo de cooldown
                    } else {
                        attackCooldowns[pokemonId] = maxOf(0, cd - 1)
                    }
                }


                "buff" -> {
                    val ownerUUID = pokemon.ownerUUID ?: return@forEach
                    val owner = level.server.playerList.getPlayer(ownerUUID) ?: return@forEach

                    val primaryType = cobblemonPokemon.types.firstOrNull()?.name ?: "normal"

                    val effect = when (primaryType.lowercase()) {
                        "grass" -> MobEffects.REGENERATION
                        "fire" -> MobEffects.FIRE_RESISTANCE
                        "water" -> MobEffects.WATER_BREATHING
                        "electric" -> MobEffects.MOVEMENT_SPEED
                        "ice" -> MobEffects.DAMAGE_RESISTANCE
                        "fighting" -> MobEffects.DAMAGE_BOOST
                        "poison" -> MobEffects.NIGHT_VISION
                        "ground" -> MobEffects.DIG_SPEED
                        "flying" -> MobEffects.SLOW_FALLING
                        "psychic" -> MobEffects.SLOW_FALLING
                        "bug" -> MobEffects.JUMP
                        "rock" -> MobEffects.DAMAGE_RESISTANCE
                        "ghost" -> MobEffects.INVISIBILITY
                        "dragon" -> MobEffects.DAMAGE_BOOST
                        "dark" -> MobEffects.NIGHT_VISION
                        "steel" -> MobEffects.DAMAGE_RESISTANCE
                        "fairy" -> MobEffects.LUCK
                        "normal" -> MobEffects.HEALTH_BOOST
                        else -> MobEffects.REGENERATION
                    }

                    owner.addEffect(MobEffectInstance(effect, 600, 1)) // 30 segundos
                    CommandState.activeCommands[pokemonId] = "idle"
                }




                "idle" -> {
                    exitAttackMode(pokemon)
                    CommandState.activeTargets.remove(pokemonId)
                    chaseCooldown[pokemonId] = 0
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

private fun findClosestMonster(level: ServerLevel, player: ServerPlayer): LivingEntity? {
    val range = 16.0
    val box = player.boundingBox.inflate(range)
    return level.getEntitiesOfClass(Mob::class.java, box) { mob ->
        mob.isAlive && mob.type.category == MobCategory.MONSTER && mob !is PokemonEntity
    }.minByOrNull { it.distanceTo(player) }
}

