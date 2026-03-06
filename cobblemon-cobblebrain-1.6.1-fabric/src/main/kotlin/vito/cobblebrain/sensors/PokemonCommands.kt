package vito.cobblebrain.sensors

import com.cobblemon.mod.common.api.pokemon.experience.ExperienceSource
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.SaplingBlock
import net.minecraft.world.phys.AABB
import org.joml.Vector3f
import vito.cobblebrain.config.ConfigHandler.config
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
    println("$pokemonName action detected: $action")

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
        .filterIsInstance<FollowOwnerGoal>()

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

val biteCooldown = mutableMapOf<UUID, Int>()
val eatIdleTimer = mutableMapOf<UUID, Int>()
val cookCooldown = mutableMapOf<UUID, Int>()
val growCooldowns = mutableMapOf<UUID, Int>()
val repairCooldowns: MutableMap<UUID, Long> = mutableMapOf()

private val announcedStates: MutableMap<UUID, String> = mutableMapOf()

enum class FoodTier {
    COMMON,
    UNCOMMON,
    RARE
}

object FoodExperienceSource : ExperienceSource

fun registerTickHandler() {
    var tickCounter = 0

    ServerTickEvents.END_SERVER_TICK.register { server ->
        tickCounter++
        if (tickCounter % 2 != 0) return@register // roda a cada 2 ticks (~0.1s)

        val level = server.overworld() as ServerLevel

        CommandState.activeCommands.forEach { (pokemonId, action) ->
            val pokemon = level.getEntity(pokemonId) as? Mob ?: return@forEach
            val cobblemonPokemon = (pokemon as? PokemonEntity)?.pokemon ?: return@forEach
            val ownerUUID = cobblemonPokemon.getOwnerUUID() ?: return@forEach
            val owner = level.server.playerList.getPlayer(ownerUUID) ?: return@forEach
            val debuffCooldowns = mutableMapOf<UUID, Int>()

            val atk = cobblemonPokemon.attack
            val spd = cobblemonPokemon.speed
            val scaledDamage = 2.0f + (atk * 0.03f)
            val speed = 0.40 + (spd * 0.005)

            when (action) {
                "grow" -> {
                    val primaryType = cobblemonPokemon.types.firstOrNull()?.name ?: "normal"
                    val pokemonId = pokemon.uuid

                    when (primaryType.lowercase()) {
                        "grass" -> {
                            if (announcedStates[pokemonId] != "grow") {
                                sendMessage(
                                    owner,
                                    "${pokemon.displayName?.string} is helping plants GROW.",
                                    ChatFormatting.YELLOW
                                )
                                announcedStates[pokemonId] = "grow"
                            }
                            val range = 5

                            // lista de blocos ao redor
                            val blocksAround = BlockPos.betweenClosed(
                                pokemon.blockX - range, pokemon.blockY - 1, pokemon.blockZ - range,
                                pokemon.blockX + range, pokemon.blockY + 1, pokemon.blockZ + range
                            )

                            // encontra o alvo mais próximo que ainda pode crescer
                            val targetPos = blocksAround
                                .map { it.immutable() }
                                .filter { pos ->
                                    val state = level.getBlockState(pos)
                                    when (val block = state.block) {
                                        is CropBlock -> !block.isMaxAge(state) // só crops não maduras
                                        is SaplingBlock -> true                // toda sapling é válida
                                        else -> false
                                    }
                                }
                                .minByOrNull { pos -> pos.distManhattan(pokemon.blockPosition()) }

                            val cooldown = growCooldowns.getOrDefault(pokemonId, 0)

                            if (targetPos != null && cooldown <= 0) {
                                val state = level.getBlockState(targetPos)
                                when (val block = state.block) {
                                    is SaplingBlock -> {
                                        block.advanceTree(level, targetPos, state, level.random)
                                    }
                                    is CropBlock -> {
                                        block.performBonemeal(level, level.random, targetPos, state)
                                    }
                                }

                                // aplica cooldown de 40 ticks (~2s)
                                growCooldowns[pokemonId] = 40

                                // partículas verdes claras
                                val option = DustParticleOptions(Vector3f(0.5f, 1.0f, 0.5f), 1.0f)
                                repeat(20) {
                                    val px = pokemon.x + (level.random.nextDouble() - 0.5) * 0.8
                                    val py = pokemon.y + level.random.nextDouble() * pokemon.bbHeight
                                    val pz = pokemon.z + (level.random.nextDouble() - 0.5) * 0.8
                                    level.sendParticles(option, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0)
                                }

                                pokemon.swing(InteractionHand.MAIN_HAND)
                                level.playSound(
                                    null,
                                    targetPos,
                                    SoundEvents.BONE_MEAL_USE,
                                    SoundSource.BLOCKS,
                                    1.0f,
                                    1.0f
                                )
                            }

                            // decrementa cooldown
                            val current = growCooldowns.getOrDefault(pokemonId, 0)
                            if (current > 0) growCooldowns[pokemonId] = current - 1
                        }
                    }
                }



                // dentro do handler:
                "cook" -> {
                    val primaryType = cobblemonPokemon.types.firstOrNull()?.name ?: "normal"
                    val pokemonId = pokemon.uuid

                    when (primaryType.lowercase()) {
                        "fire" -> {
                            if (announcedStates[pokemonId] != "fire") {
                                sendMessage(
                                    owner,
                                    "${pokemon.displayName?.string} is ready to COOK or SMELT ores.",
                                    ChatFormatting.YELLOW
                                )
                                announcedStates[pokemonId] = "fire"
                            }
                            val range = 3.0
                            val items =
                                level.getEntitiesOfClass(ItemEntity::class.java, pokemon.boundingBox.inflate(range))
                            val recipeTypes = listOf(RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING)

                            // escolhe um único item "cozinhável" mais próximo
                            val target = items
                                .filter { entity ->
                                    val stack = entity.item
                                    if (stack.isEmpty) return@filter false
                                    val input = SingleRecipeInput(stack)
                                    // existe ao menos uma receita válida
                                    recipeTypes.any { type -> level.recipeManager.getRecipeFor(type, input, level).isPresent }
                                }
                                .minByOrNull { it.distanceTo(pokemon) }

                            val cooldown = cookCooldown.getOrDefault(pokemonId, 0)

                            if (target != null && target.isAlive) {
                                // só cozinha se cooldown == 0
                                if (cooldown <= 0) {
                                    val stack = target.item
                                    val input = SingleRecipeInput(stack)

                                    // pega a primeira receita aplicável
                                    val recipeOpt = recipeTypes.firstNotNullOfOrNull { type ->
                                            level.recipeManager.getRecipeFor(
                                                type,
                                                input,
                                                level
                                            ).orElse(null)
                                        }

                                    if (recipeOpt != null) {
                                        val recipe = recipeOpt.value()
                                        val result = recipe.getResultItem(level.registryAccess()).copy()
                                        if (!result.isEmpty) {
                                            // cozinha o ITEM inteiro (uma entidade por vez)
                                            result.count = stack.count
                                            target.item = result

                                            // cooldown por Pokémon (22 ticks ~1.1s)
                                            cookCooldown[pokemonId] = 22

                                            // partículas simples e confiáveis
                                            repeat(20) {
                                                val dx = (level.random.nextDouble() - 0.5) * 2 * range
                                                val dz = (level.random.nextDouble() - 0.5) * 2 * range
                                                if (dx * dx + dz * dz <= range * range) {
                                                    val px = pokemon.x + dx
                                                    val py = pokemon.y + level.random.nextDouble() * pokemon.bbHeight
                                                    val pz = pokemon.z + dz
                                                    level.sendParticles(
                                                        ParticleTypes.FLAME,
                                                        px,
                                                        py,
                                                        pz,
                                                        1,
                                                        0.0,
                                                        0.0,
                                                        0.0,
                                                        0.0
                                                    )
                                                }
                                            }

                                            pokemon.swing(InteractionHand.MAIN_HAND)
                                            level.playSound(
                                                null,
                                                target.blockPosition(),
                                                SoundEvents.FURNACE_FIRE_CRACKLE,
                                                SoundSource.BLOCKS,
                                                1.0f,
                                                1.0f
                                            )

                                            // chance de transformar UM item em carvão
                                            if (level.random.nextFloat() < 0.05f && !target.item.isEmpty) {
                                                val stack = target.item
                                                stack.shrink(1)
                                                val coal = ItemStack(Items.COAL, 1)

                                                if (stack.isEmpty) {
                                                    target.item = coal
                                                } else {
                                                    level.addFreshEntity(
                                                        ItemEntity(
                                                            level,
                                                            target.x,
                                                            target.y,
                                                            target.z,
                                                            coal
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // decrementa cooldown (como no eat)
                            val current = cookCooldown.getOrDefault(pokemonId, 0)
                            if (current > 0) cookCooldown[pokemonId] = current - 1
                        }
                    }
                }

                "attack" -> {
                    // se o pokémon está em batalha, ignora o comando
                    val ownerUUID = pokemon.ownerUUID
                    if (announcedStates[pokemonId] != "attack") {
                        sendMessage(
                            owner,
                            "${pokemon.displayName?.string} is trying to ATTACK a target...",
                            ChatFormatting.RED
                        )
                        announcedStates[pokemonId] = "attack"
                    }
                    BattleRegistry.getBattleByParticipatingPlayerId(ownerUUID ?: return@forEach)?.let {
                        // se cair aqui, significa que o dono do pokémon está em batalha
                        CommandState.activeCommands[pokemonId] = "idle"
                        return@forEach
                    }

                    enterAttackMode(pokemon)

                    val target = CommandState.activeTargets[pokemonId]?.let { level.getEntity(it) as? LivingEntity }
                    val finalTarget = if (target != null && target.isAlive) {
                        target
                    } else {
                        findClosestNonPlayerLiving(level, pokemon)?.also {
                            CommandState.activeTargets[pokemonId] = it.uuid
                        }
                    }

                    if (finalTarget == null || !finalTarget.isAlive || !isEnemy(pokemon, finalTarget)) {
                        exitAttackMode(pokemon)
                        sendMessage(owner, "${pokemon.displayName?.string} stopped ATTACKING...",
                            ChatFormatting.RED
                        )
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
                        attackCooldowns[pokemonId] = 10
                    } else {
                        attackCooldowns[pokemonId] = maxOf(0, cd - 1)
                    }
                }

                "protect" -> {
                    enterAttackMode(pokemon)
                    if (announcedStates[pokemonId] != "protect") {
                        sendMessage(
                            owner,
                            "${pokemon.displayName?.string} is PROTECTING you against mobs.",
                            ChatFormatting.BLUE
                        )
                        announcedStates[pokemonId] = "protect"
                    }

                    val ownerUUID = pokemon.ownerUUID ?: return@forEach
                    val owner = level.server.playerList.getPlayer(ownerUUID) ?: return@forEach

                    val attacker = owner.lastHurtByMob
                    val victim = owner.lastHurtMob

                    val pursuer = level.getEntitiesOfClass(Mob::class.java, owner.boundingBox.inflate(16.0)) { mob ->
                        mob.isAlive && mob.target == owner && mob !is PokemonEntity
                    }.minByOrNull { it.distanceTo(owner) }

                    val hostile = when {
                        attacker != null && attacker.isAlive -> attacker
                        victim != null && victim.isAlive -> victim
                        pursuer != null && pursuer.isAlive -> pursuer
                        else -> findClosestMonster(level, owner)
                    }

                    val finalTarget = if (hostile != null && hostile.isAlive && isEnemy(pokemon, hostile)) {
                        hostile.also { CommandState.activeTargets[pokemonId] = it.uuid }
                    } else null

                    if (finalTarget == null) {
                        pokemon.navigation.moveTo(owner, speed)
                        pokemon.target = null

                        val idleTicks = chaseCooldown.getOrDefault(pokemonId, 0)
                        if (idleTicks > 1500) {
                            exitAttackMode(pokemon)
                            sendMessage(owner, "${pokemon.displayName?.string} stopped PROTECTING...",
                                ChatFormatting.RED
                            )
                            CommandState.activeTargets.remove(pokemonId)
                            CommandState.activeCommands[pokemonId] = "idle"
                            chaseCooldown[pokemonId] = 0
                        } else {
                            chaseCooldown[pokemonId] = idleTicks + 1
                        }
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
                        attackCooldowns[pokemonId] = 10
                    } else {
                        attackCooldowns[pokemonId] = maxOf(0, cd - 1)
                    }
                }

                "eat" -> {
                    val box = pokemon.boundingBox.inflate(8.0)
                    val items = level.getEntitiesOfClass(ItemEntity::class.java, box)
                    val foodItem = items
                        .filter { entity ->
                            val stack = entity.item
                            val id = BuiltInRegistries.ITEM.getKey(stack.item)

                            stack.get(DataComponents.FOOD) != null ||
                                    (id.namespace == "cobblemon" &&
                                            (id.path.contains("berry") || id.path.contains("malasada")
                                                    || id.path.contains("candy") || id.path.contains("sweet")
                                                    || id.path.contains("casteliacone") || id.path.contains("candied")
                                                    || id.path.contains("apple") || id.path.contains("cookie")))
                        }
                        .minByOrNull { it.distanceTo(pokemon) }

                    val bite = biteCooldown.getOrDefault(pokemonId, 0)
                    val idle = eatIdleTimer.getOrDefault(pokemonId, 0)

                    if (announcedStates[pokemonId] != "eat") {
                        sendMessage(
                            owner,
                            "${pokemon.displayName?.string} wants to EAT...",
                            ChatFormatting.YELLOW
                        )
                        announcedStates[pokemonId] = "eat"
                    }

                    if (foodItem != null && foodItem.isAlive) {
                        val stack = foodItem.item
                        val id = BuiltInRegistries.ITEM.getKey(stack.item)
                        pokemon.navigation.moveTo(foodItem, 1.0)
                        if (pokemon.distanceTo(foodItem) < 2.0f && bite <= 0) {
                            val stack = foodItem.item
                            val foodComponent = stack.get(DataComponents.FOOD)

                            stack.item.eatingSound?.let { sound ->
                                level.playSound(null, pokemon.blockPosition(), sound, SoundSource.NEUTRAL, 1.0f, 1.0f)
                            }
                            if (foodComponent != null) {
                                val tier = determineFoodTier(stack.item)
                                applyFoodEffects(pokemon, foodComponent, tier, stack.item)
                            }
                            else if (id.namespace == "cobblemon") {
                                applyCobblemonBerryEffects(pokemon, stack)}
                            stack.shrink(1)
                            if (stack.isEmpty) {
                                foodItem.discard()
                            }
                            biteCooldown[pokemonId] = 10
                        }
                        eatIdleTimer[pokemonId] = 0

                    } else {
                        if (idle > 180) {
                            CommandState.activeCommands[pokemonId] = "idle"
                            eatIdleTimer[pokemonId] = 0
                        } else {
                            eatIdleTimer[pokemonId] = idle + 1
                        }
                    }

                    val currentBite = biteCooldown.getOrDefault(pokemonId, 0)
                    if (currentBite > 0) {
                        biteCooldown[pokemonId] = currentBite - 1
                    }
                }

                "sit" -> {
                    // força estado idle/sit
                    exitAttackMode(pokemon)
                    pokemon.isOrderedToSit = true
                    CommandState.activeTargets.remove(pokemonId)
                    if (announcedStates[pokemonId] != "sit") {
                        sendMessage(
                            owner,
                            "${pokemon.displayName?.string} SAT down.",
                            ChatFormatting.YELLOW
                        )
                        announcedStates[pokemonId] = "sit"
                    }

                }

                "debuff enemy" -> {
                    if (announcedStates[pokemonId] != "debuff") {
                        sendMessage(
                            owner,
                            "${pokemon.displayName?.string} is attempting to use DEBUFF on a target.",
                            ChatFormatting.RED
                        )
                        announcedStates[pokemonId] = "debuff"
                    }
                    enterAttackMode(pokemon)

                    val target = CommandState.activeTargets[pokemonId]?.let { level.getEntity(it) as? LivingEntity }
                    val finalTarget = if (target != null && target.isAlive) {
                        target
                    } else {
                        findClosestNonPlayerLiving(level, pokemon)?.also {
                            CommandState.activeTargets[pokemonId] = it.uuid
                        }
                    }

                    val cd = debuffCooldowns.getOrDefault(pokemonId, 0)

                    if (cd > 0) {
                        sendMessage(owner, "${pokemon.displayName?.string} is recharging DEBUFF.",
                            ChatFormatting.RED
                        )
                        debuffCooldowns[pokemonId] = maxOf(0, cd - 1)
                        CommandState.activeCommands[pokemonId] = "idle"
                        return@forEach
                    }

                    if (finalTarget == null || !finalTarget.isAlive || !isEnemy(pokemon, finalTarget)) {
                        exitAttackMode(pokemon)
                        sendMessage(owner, "${pokemon.displayName?.string} found no target.",
                            ChatFormatting.RED
                        )
                        CommandState.activeTargets.remove(pokemonId)
                        CommandState.activeCommands[pokemonId] = "idle"
                        return@forEach
                    }

                    pokemon.target = finalTarget
                    pokemon.navigation.moveTo(finalTarget, speed)

                    val reach = (pokemon.bbWidth * 2.0f) + 1.5f
                    val inRange = pokemon.distanceToSqr(finalTarget) <= (reach * reach) &&
                            pokemon.hasLineOfSight(finalTarget)

                    if (inRange) {
                        val primaryType = cobblemonPokemon.types.firstOrNull()?.name?.lowercase() ?: "normal"

                        val effectHolder: Holder<MobEffect> = when (primaryType) {
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

                        val effectName = BuiltInRegistries.MOB_EFFECT.getKey(effectHolder.value())?.path?.uppercase()

                        finalTarget.addEffect(MobEffectInstance(effectHolder, 600, 1)) // 30 segundos
                        pokemon.swing(InteractionHand.MAIN_HAND)

                        sendMessage(
                            owner,
                            "${pokemon.displayName?.string} gave $effectName to the mob.",
                            ChatFormatting.RED
                        )

                        // aplica cooldown de 1 minuto
                        debuffCooldowns[pokemonId] = 1200

                        // volta para idle
                        exitAttackMode(pokemon)
                        CommandState.activeCommands[pokemonId] = "idle"
                    }
                }


                "buff" -> {
                    val primaryType = cobblemonPokemon.types.firstOrNull()?.name ?: "normal"

                    val effectHolder: Holder<MobEffect> = when (primaryType.lowercase()) {
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

                    val effectName = BuiltInRegistries.MOB_EFFECT.getKey(effectHolder.value())?.path?.uppercase()

                    owner.addEffect(MobEffectInstance(effectHolder, 600, 1)) // 30 segundos
                    sendMessage(owner, "${pokemon.displayName?.string} gave you $effectName", ChatFormatting.GREEN)
                    CommandState.activeCommands[pokemonId] = "idle"
                }

                "idle" -> {
                    exitAttackMode(pokemon)
                    CommandState.activeTargets.remove(pokemonId)
                    announcedStates.remove(pokemonId)
                    chaseCooldown[pokemonId] = 0
                }

                "repair" -> {
                    val primaryType = cobblemonPokemon.types.firstOrNull()?.name ?: "normal"
                    val pokemonId = pokemon.uuid

                    when (primaryType.lowercase()) {
                        "steel" -> {
                            val now = level.gameTime
                            val lastRepair = repairCooldowns[pokemonId] // null if never repaired

                            val ownerId = pokemon.ownerUUID
                            val owner: ServerPlayer? = level.server.playerList.getPlayer(ownerId!!)
                            if (announcedStates[pokemonId] != "repair") {
                                sendMessage(
                                    owner,
                                    "${pokemon.displayName?.string} is attempting to REPAIR tools.",
                                    ChatFormatting.YELLOW
                                )
                                announcedStates[pokemonId] = "repair"
                            }

                            // cooldown of 5 minutes (6000 ticks)
                            if (lastRepair != null && now - lastRepair < 6000) {
                                if (CommandState.activeCommands[pokemonId] == "repair") {
                                    sendMessage(
                                        owner,
                                        "${pokemon.displayName?.string} is recharging REPAIR...",
                                        ChatFormatting.GOLD
                                    )
                                }
                                CommandState.activeCommands[pokemonId] = "idle"
                                return@forEach
                            }

                            val range = 3.0
                            val items = level.getEntitiesOfClass(ItemEntity::class.java, pokemon.boundingBox.inflate(range))
                            val target = items.firstOrNull { it.item.isDamageableItem }

                            if (target != null) {
                                val stack = target.item
                                stack.damageValue = (stack.damageValue - 60).coerceAtLeast(0)
                                repairCooldowns[pokemonId] = now // record repair time

                                pokemon.swing(InteractionHand.MAIN_HAND)
                                level.playSound(
                                    null,
                                    target.blockPosition(),
                                    SoundEvents.ANVIL_USE,
                                    SoundSource.BLOCKS,
                                    1.0f,
                                    1.0f
                                )

                                sendMessage(
                                    owner,
                                    "${pokemon.displayName?.string} REPAIRED your dropped weapon a bit!",
                                    ChatFormatting.GREEN
                                )

                                CommandState.activeCommands[pokemonId] = "idle"
                            }
                        }
                    }
                }


                "shift" -> {
                    val ownerUUID = pokemon.ownerUUID ?: return@forEach
                    val owner = level.server.playerList.getPlayer(ownerUUID) ?: return@forEach
                    val primaryType = cobblemonPokemon.types.firstOrNull()?.name ?: "normal"

                    when (primaryType.lowercase()) {
                        "ghost" -> {
                            val invis = owner.hasEffect(MobEffects.INVISIBILITY)
                            val jump = owner.hasEffect(MobEffects.JUMP)
                            val slowFall = owner.hasEffect(MobEffects.SLOW_FALLING)
                            val speed = owner.hasEffect(MobEffects.MOVEMENT_SPEED)

                            // só toca som se nenhum dos efeitos já estava ativo
                            if (!invis && !jump && !slowFall && !speed) {
                                level.playSound(
                                    null,
                                    pokemon.blockPosition(),
                                    SoundEvents.PORTAL_TRAVEL,
                                    SoundSource.PLAYERS,
                                    1.0f,
                                    1.0f
                                )
                            }

                            // aplica/renova os efeitos
                            owner.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, 20 * 3, 0))
                            owner.addEffect(MobEffectInstance(MobEffects.JUMP, 20 * 3, 2))
                            owner.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 3, 0))
                            owner.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 20 * 3, 2))
                            owner.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 3, 2))

                            pokemon.swing(InteractionHand.MAIN_HAND)
                            if (announcedStates[pokemonId] != "shift") {
                                sendMessage(
                                    owner,
                                    "${pokemon.displayName?.string} used SHIFT",
                                    ChatFormatting.BLUE
                                )
                                announcedStates[pokemonId] = "shift"
                            }

                        }
                    }
                }
            }
        }
    }
}

fun sendMessage(player: ServerPlayer?, text: String, color: ChatFormatting) {
    player?.sendSystemMessage(Component.literal(text).withStyle(color))
}

private fun findClosestNonPlayerLiving(level: ServerLevel, source: LivingEntity): LivingEntity? {
    val range = 16.0
    val box = AABB(
        source.x - range, source.y - range, source.z - range,
        source.x + range, source.y + range, source.z + range
    )

    val list = level.getEntitiesOfClass(LivingEntity::class.java, box) { e ->
        isEnemy(source, e)
    }

    return list.minByOrNull { it.distanceTo(source) }
}


private fun findClosestMonster(level: ServerLevel, player: ServerPlayer): LivingEntity? {
    val range = 16.0
    val box = player.boundingBox.inflate(range)

    return level.getEntitiesOfClass(Mob::class.java, box) { mob ->
        mob.isAlive &&
                mob.type.category == MobCategory.MONSTER &&
                mob !is PokemonEntity &&
                isEnemy(player, mob) // garante que não pega aliado
    }.minByOrNull { it.distanceTo(player) }
}


private fun isEnemy(source: LivingEntity, target: LivingEntity): Boolean {
    if (target == source) return false

    // nunca atacar o próprio dono
    if (source is PokemonEntity && target is ServerPlayer) {
        if (source.ownerUUID == target.uuid) {
            return false
        }
        // atacar outros players só se PvP estiver habilitado
        return config.allowPokemonPVP
    }

    // Pokémon
    if (target is PokemonEntity) {
        val sourceOwner = (source as? PokemonEntity)?.ownerUUID
        val targetOwner = target.ownerUUID
        return config.allowPokemonPVP && sourceOwner != targetOwner
    }

    // Mobs domados (lobos, gatos, cavalos etc.)
    if (target is TamableAnimal && target.isTame) {
        return false
    }


    // Mobs não agressivos com tag → nunca inimigos
    if (target.hasCustomName() && target is Mob && target.type.category != MobCategory.MONSTER) {
        return false
    }

    // Qualquer outro mob só é inimigo se PvE estiver habilitado
    return config.allowPokemonPVE
}

fun determineFoodTier(item: Item): FoodTier {
    return when (
        // RAROS – ouro / encantados
        item) {
        Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE -> FoodTier.RARE

        // INCOMUNS – cozidos / craft médio
        Items.COOKED_BEEF, Items.COOKED_CHICKEN, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON,
        Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON, Items.BAKED_POTATO,
        Items.BREAD, Items.PUMPKIN_PIE, Items.RABBIT_STEW, Items.MUSHROOM_STEW, Items.GOLDEN_CARROT -> FoodTier.UNCOMMON

        else -> FoodTier.COMMON
    }
}

fun hasTypeBonus(pokemon: PokemonEntity, item: Item): Boolean {
    val types = pokemon.pokemon.types.map { it.name.lowercase() }
    return when {
        "grass" in types && (
                item == Items.WHEAT ||
                        item == Items.CARROT ||
                        item == Items.POTATO ||
                        item == Items.BEETROOT ||
                        item == Items.SWEET_BERRIES
                ) -> true
        "fire" in types && (
                item == Items.COOKED_BEEF ||
                        item == Items.COOKED_CHICKEN ||
                        item == Items.COOKED_PORKCHOP ||
                        item == Items.COOKED_MUTTON
                ) -> true
        "water" in types && (
                item == Items.COD ||
                        item == Items.SALMON ||
                        item == Items.COOKED_COD ||
                        item == Items.COOKED_SALMON
                ) -> true
        "poison" in types && item == Items.ROTTEN_FLESH -> true
        "fairy" in types && (
                item == Items.CAKE ||
                        item == Items.PUMPKIN_PIE
                ) -> true
        "steel" in types && (
                item == Items.GOLDEN_APPLE ||
                        item == Items.ENCHANTED_GOLDEN_APPLE ||
                        item == Items.GOLDEN_CARROT
                ) -> true
        else -> false
    }
}

fun increaseFriendship(pokemonEntity: PokemonEntity, amount: Int) {
    val pokemon = pokemonEntity.pokemon
    pokemon.incrementFriendship(amount)
}

fun givePokemonExp(pokemonEntity: PokemonEntity, amount: Int) {
    val pokemon = pokemonEntity.pokemon
    // adiciona experiência
    pokemon.addExperience(FoodExperienceSource,amount)
    pokemonEntity.level().broadcastEntityEvent(pokemonEntity, 7.toByte())
}

fun applyCobblemonBerryEffects(pokemon: PokemonEntity, stack: ItemStack) {
    val id = BuiltInRegistries.ITEM.getKey(stack.item)
    val path = id.path

    when {
        path.contains("oran") -> {
            pokemon.addEffect(
                MobEffectInstance(
                    MobEffects.REGENERATION,
                    120,
                    0
                )
            )
        }

        // Cura maior
        path.contains("sitrus") -> {
            pokemon.addEffect(
                MobEffectInstance(
                    MobEffects.REGENERATION,
                    200,
                    1
                )
            )
        }

        path.contains("chesto") -> {
            pokemon.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
            pokemon.addEffect(
                MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    200,
                    0
                )
            )
        }

        // Cura poison
        path.contains("pecha") -> {
            pokemon.removeEffect(MobEffects.POISON)
        }

        // Cura burn
        path.contains("rawst") -> {
            pokemon.clearFire()
            pokemon.removeEffect(MobEffects.WEAKNESS)
            pokemon.addEffect(
                MobEffectInstance(
                    MobEffects.FIRE_RESISTANCE,
                    200,
                    0
                )
            )
        }

        // Cura freeze
        path.contains("aspear") -> {
            pokemon.addEffect(
                MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE,
                    200,
                    0
                )
            )
        }

        // Recupera PP
        path.contains("leppa") -> {
            givePokemonExp(pokemon, 10)
        }

        // Cura todos status
        path.contains("lum") -> {

            val negative = listOf(
                MobEffects.POISON,
                MobEffects.WITHER,
                MobEffects.WEAKNESS,
                MobEffects.MOVEMENT_SLOWDOWN,
                MobEffects.BLINDNESS,
                MobEffects.HUNGER
            )

            negative.forEach { pokemon.removeEffect(it) }
            pokemon.clearFire()
        }

        path.contains("chople") -> {
            pokemon.addEffect(
                MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE,
                    200,
                    0
                )
            )
        }

        path.contains("liechi") -> {
            pokemon.addEffect(
                MobEffectInstance(
                    MobEffects.DAMAGE_BOOST,
                    200,
                    0
                )
            )
        }


    }
}

fun applyFoodEffects(
    pokemon: PokemonEntity,
    foodComponent: FoodProperties,
    tier: FoodTier,
    item: Item
): Boolean {

    val baseValue = foodComponent.nutrition() + foodComponent.saturation()
    val bonus = hasTypeBonus(pokemon, item)

    // comidas vanilla
    when (tier) {

        FoodTier.COMMON -> {
            var healAmount = baseValue * 0.8f
            if (bonus) healAmount *= 1.2f
            pokemon.heal(healAmount)
        }

        FoodTier.UNCOMMON -> {
            var healAmount = baseValue * 0.8f
            if (bonus) healAmount *= 1.2f
            pokemon.heal(healAmount)

            increaseFriendship(pokemon, if (bonus) 4 else 2)
            givePokemonExp(pokemon, (baseValue * 0.7).toInt())
        }

        FoodTier.RARE -> {
            pokemon.heal(pokemon.maxHealth)

            increaseFriendship(pokemon, if (bonus) 10 else 7)
            givePokemonExp(pokemon, (baseValue).toInt())

            pokemon.addEffect(
                MobEffectInstance(
                    MobEffects.REGENERATION,
                    if (bonus) 400 else 200,
                    1
                )
            )
        }
    }

    val level = pokemon.level()

    if (bonus && level is ServerLevel) {
        level.sendParticles(
            ParticleTypes.HEART,
            pokemon.x,
            pokemon.y + 1.0,
            pokemon.z,
            5,
            0.3, 0.3, 0.3,
            0.0
        )
    }

    return bonus
}