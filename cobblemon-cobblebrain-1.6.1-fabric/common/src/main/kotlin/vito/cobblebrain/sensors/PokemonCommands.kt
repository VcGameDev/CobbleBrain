package vito.cobblebrain.sensors

import com.cobblemon.mod.common.api.pokemon.experience.ExperienceSource
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Ghast
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.projectile.SmallFireball
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.Fireworks
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.SaplingBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import vito.cobblebrain.config.ConfigHandler.config
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
private val disabledGoals: MutableMap<UUID, List<Goal>> = mutableMapOf()

object MobBridge {
    var addGoal: ((Mob, Int, Goal) -> Unit)? = null
    var removeGoal: ((Mob, Goal) -> Unit)? = null
    var getGoals: ((Mob) -> List<Goal>)? = null
}

private fun enterAttackMode(pokemon: Mob) {
    val getGoals = MobBridge.getGoals ?: return
    val toDisable = getGoals(pokemon)
        .filterIsInstance<FollowOwnerGoal>()

    if (toDisable.isNotEmpty()) {
        disabledGoals[pokemon.uuid] = toDisable
        toDisable.forEach { MobBridge.removeGoal?.invoke(pokemon, it) }
    }
    pokemon.isAggressive = true
}

private fun exitAttackMode(pokemon: Mob) {
    disabledGoals.remove(pokemon.uuid)?.forEach { goal ->
        val addGoal = MobBridge.addGoal ?: return
        addGoal(pokemon, 2, goal)
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

// 1 april
val fireballCooldown = mutableMapOf<UUID, Int>()
val fireballState = mutableMapOf<UUID, String>()
val fireballWait = mutableMapOf<UUID, Int>()
val activeFireballs = mutableSetOf<UUID>()

val nukeTimer = mutableMapOf<UUID, Int>()
val nukeActive = mutableMapOf<UUID, Boolean>()

val psychicStandActive = mutableMapOf<UUID, Boolean>()
val psychicStandTimer = mutableMapOf<UUID, Int>()

val imaginaryActive = mutableMapOf<UUID, Boolean>()
val imaginaryTimer = mutableMapOf<UUID, Int>()
val imaginaryTarget = mutableMapOf<UUID, UUID>()
val imaginaryRunning = mutableSetOf<UUID>()

val finalJudgmentActive = mutableMapOf<UUID, Boolean>()
val finalJudgmentTimer = mutableMapOf<UUID, Int>()

val ssStyleActive = mutableMapOf<UUID, Boolean>()
val ssStyleTimer = mutableMapOf<UUID, Int>()
val ssStyleKills = mutableMapOf<UUID, Int>()
val ssStyleWave = mutableMapOf<UUID, Int>()
val ssStyleSpawnedMobs = mutableMapOf<UUID, MutableSet<UUID>>()
val ssStyleStarted = mutableMapOf<UUID, Boolean>()
val ssStyleLastReport = mutableMapOf<UUID, Int>()


private val announcedStates: MutableMap<UUID, String> = mutableMapOf()

enum class FoodTier {
    COMMON,
    UNCOMMON,
    RARE
}

object FoodExperienceSource : ExperienceSource

object CommandTickHandler {
    fun processActiveCommands(server: MinecraftServer) {
        val level = server.overworld() as ServerLevel
        val debuffCooldowns = mutableMapOf<UUID, Int>()

        handleNukeSystem(level)
        handleImaginaryTechnique(level)
        handlePsychicStand(level)
        handleFinalJudgment(level)
        handleSSStyle(level)

        val toRemove = mutableListOf<UUID>()

        activeFireballs.forEach { id ->
            val entity = level.getEntity(id)

            if (entity !is SmallFireball || !entity.isAlive) {
                toRemove.add(id)
                return@forEach
            }

            val velocity = entity.deltaMovement.length()

            // detecta colisão com entidade
            val hitEntity = level.getEntitiesOfClass(
                LivingEntity::class.java,
                entity.boundingBox.inflate(0.3)
            ).any { it != entity.owner }

            // condição de impacto
            if (hitEntity || velocity < 0.03) {

                level.explode(
                    entity.owner,
                    entity.x,
                    entity.y,
                    entity.z,
                    1.5f,
                    Level.ExplosionInteraction.TNT
                )

                entity.level().playSound(
                    null,
                    entity.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.HOSTILE,
                    0.8f,
                    1.0f
                )

                entity.discard()
                toRemove.add(id)
            }
        }

        toRemove.forEach { activeFireballs.remove(it) }

        CommandState.activeCommands.forEach { (pokemonId, action) ->
            val pokemon = level.getEntity(pokemonId) as? Mob ?: return@forEach
            val cobblemonPokemon = (pokemon as? PokemonEntity)?.pokemon ?: return@forEach
            val ownerUUID = cobblemonPokemon.getOwnerUUID() ?: return@forEach
            val owner = level.server.playerList.getPlayer(ownerUUID) ?: return@forEach

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
                            val recipeTypes =
                                listOf(RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING)

                            // escolhe um único item "cozinhável" mais próximo
                            val target = items
                                .filter { entity ->
                                    val stack = entity.item
                                    if (stack.isEmpty) return@filter false
                                    val input = SingleRecipeInput(stack)
                                    // existe ao menos uma receita válida
                                    recipeTypes.any { type ->
                                        level.recipeManager.getRecipeFor(
                                            type,
                                            input,
                                            level
                                        ).isPresent
                                    }
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
                                                    val py =
                                                        pokemon.y + level.random.nextDouble() * pokemon.bbHeight
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
                        sendMessage(
                            owner, "${pokemon.displayName?.string} stopped ATTACKING...",
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

                    val pursuer =
                        level.getEntitiesOfClass(Mob::class.java, owner.boundingBox.inflate(16.0)) { mob ->
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
                            sendMessage(
                                owner, "${pokemon.displayName?.string} stopped PROTECTING...",
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
                                level.playSound(
                                    null,
                                    pokemon.blockPosition(),
                                    sound,
                                    SoundSource.NEUTRAL,
                                    1.0f,
                                    1.0f
                                )
                            }
                            if (foodComponent != null) {
                                val tier = determineFoodTier(stack.item)
                                applyFoodEffects(pokemon, foodComponent, tier, stack.item)
                            } else if (id.namespace == "cobblemon") {
                                applyCobblemonBerryEffects(pokemon, stack)
                            }
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
                        sendMessage(
                            owner, "${pokemon.displayName?.string} is recharging DEBUFF.",
                            ChatFormatting.RED
                        )
                        debuffCooldowns[pokemonId] = maxOf(0, cd - 1)
                        CommandState.activeCommands[pokemonId] = "idle"
                        return@forEach
                    }

                    if (finalTarget == null || !finalTarget.isAlive || !isEnemy(pokemon, finalTarget)) {
                        exitAttackMode(pokemon)
                        sendMessage(
                            owner, "${pokemon.displayName?.string} found no target.",
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

                        val effectName =
                            BuiltInRegistries.MOB_EFFECT.getKey(effectHolder.value())?.path?.uppercase()

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
                            val items =
                                level.getEntitiesOfClass(ItemEntity::class.java, pokemon.boundingBox.inflate(range))
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

                "nuke" -> {
                    if (!config.outputApril1) return@forEach
                    val pokemonId = pokemon.uuid

                    if (nukeActive.getOrDefault(pokemonId, false)) return@forEach

                    nukeActive[pokemonId] = true
                    nukeTimer[pokemonId] = 372 // 38.6 segundos
                }

                "psychic stand" -> {
                    if (!config.outputApril1) return@forEach
                    val id = pokemon.uuid

                    if (psychicStandActive.getOrDefault(id, false)) return@forEach

                    psychicStandActive[id] = true
                    psychicStandTimer[id] = 300 // 30s
                }

                "imaginary technique" -> {
                    if (!config.outputApril1) return@forEach
                    val pokemonId = pokemon.uuid

                    // evita repetir toda hora
                    if (announcedStates[pokemonId] != "imaginary technique") {
                        sendMessage(
                            owner,
                            "Gomen... Amanai...",
                            ChatFormatting.LIGHT_PURPLE
                        )

                        announcedStates[pokemonId] = "imaginary technique"
                    }

                    // se já está ativo, não reinicia
                    if (imaginaryActive.getOrDefault(pokemonId, false)) return@forEach

                    imaginaryActive[pokemonId] = true
                    imaginaryTimer[pokemonId] = 120
                }

                "final judgment" -> {
                    if (!config.outputApril1) return@forEach
                    val pokemonId = pokemon.uuid

                    if (announcedStates[pokemonId] != "final judgment") {
                        sendMessage(owner, "With the power of Rayquaza, I summon: FINAL JUDGMENT", ChatFormatting.YELLOW)
                        announcedStates[pokemonId] = "final judgment"
                    }

                    if (finalJudgmentActive.getOrDefault(pokemonId, false)) return@forEach

                    finalJudgmentActive[pokemonId] = true
                    finalJudgmentTimer[pokemonId] = 120 // 6 segundos total
                }

                "ssstyle" -> {
                    if (!config.outputApril1) return@forEach
                    val id = owner.uuid

                    if (ssStyleActive.getOrDefault(id, false)) return@forEach

                    sendMessage(owner, "Machine… I will cut you down.", ChatFormatting.RED)

                    ssStyleActive[id] = true
                    ssStyleTimer[id] = 4600
                    ssStyleKills[id] = 0
                }

                "fireball machine" -> {
                    if (!config.outputApril1) return@forEach
                    val primaryType = cobblemonPokemon.types.firstOrNull()?.name ?: return@forEach
                    if (primaryType.lowercase() != "fire") return@forEach

                    val pokemonId = pokemon.uuid

                    val cooldown = fireballCooldown.getOrDefault(pokemonId, 0)
                    if (cooldown > 0) {
                        fireballCooldown[pokemonId] = cooldown - 1
                        return@forEach
                    }

                    val state = fireballState.getOrDefault(pokemonId, "hostile")

                    val toRemove = mutableListOf<UUID>()

                    activeFireballs.forEach { id ->
                        val entity = level.getEntity(id)

                        if (entity !is SmallFireball || !entity.isAlive) {
                            toRemove.add(id)
                            return@forEach
                        }

                        // se bateu em algo (ou quase parou)
                        if (entity.horizontalCollision || entity.verticalCollision) {

                            level.explode(
                                null,
                                entity.x,
                                entity.y,
                                entity.z,
                                1.5f, // força da explosão
                                Level.ExplosionInteraction.TNT // qubrea bloco
                            )

                            entity.discard()
                            toRemove.add(id)
                        }
                    }

                    toRemove.forEach { activeFireballs.remove(it) }

                    when (state) {

                        // FASE 1 — HOSTIS
                        "hostile" -> {
                            val target = findClosestMonsterToPokemon(level, pokemon)

                            if (target != null && pokemon.distanceTo(target) <= 150) {
                                val dx = target.x - pokemon.x
                                val dy = (target.y + target.eyeHeight - pokemon.y) * 0.45
                                val dz = target.z - pokemon.z

                                shootFireball(level, pokemon, dx, dy, dz)
                                fireballCooldown[pokemonId] = 1
                            } else {
                                fireballState[pokemonId] = "waiting"
                                fireballWait[pokemonId] = 100

                                sendMessage(
                                    owner,
                                    "${pokemon.displayName?.string} can't find enemies...",
                                    ChatFormatting.RED
                                )
                            }
                        }

                        // FASE 2 — ESPERA
                        "waiting" -> {
                            val wait = fireballWait.getOrDefault(pokemonId, 0)

                            if (wait > 0) {
                                fireballWait[pokemonId] = wait - 1
                            } else {
                                fireballState[pokemonId] = "passive"
                            }
                        }

                        // FASE 3 — PASSIVOS
                        "passive" -> {
                            val range = 150.0
                            val box = pokemon.boundingBox.inflate(range)

                            val target = level.getEntitiesOfClass(LivingEntity::class.java, box)
                                .filter { it.isAlive && it !is Monster && it != pokemon }
                                .minByOrNull { it.distanceTo(pokemon) }

                            if (target != null) {
                                val dx = target.x - pokemon.x
                                val dy = (target.y + target.eyeHeight - pokemon.y) * 0.45
                                val dz = target.z - pokemon.z

                                shootFireball(level, pokemon, dx, dy, dz)
                                fireballCooldown[pokemonId] = 1
                            } else {
                                fireballState[pokemonId] = "random"
                            }
                        }

                        // FASE 4 — ALEATÓRIO
                        "random" -> {
                            val dx = (level.random.nextDouble() - 0.5) * 2
                            val dy = (level.random.nextDouble() - 0.2) * 0.45
                            val dz = (level.random.nextDouble() - 0.5) * 2

                            shootFireball(level, pokemon, dx, dy, dz)
                            fireballCooldown[pokemonId] = 1
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

private fun findClosestMonsterToPokemon(level: ServerLevel, pokemon: LivingEntity): LivingEntity? {
    val range = 150.0
    val box = pokemon.boundingBox.inflate(range)

    return level.getEntitiesOfClass(Mob::class.java, box) { mob ->
        mob.isAlive &&
                mob.type.category == MobCategory.MONSTER &&
                mob !is PokemonEntity &&
                isEnemy(pokemon, mob) // usa o pokemon como fonte
    }.minByOrNull { it.distanceTo(pokemon) }
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
    pokemon.addExperience(FoodExperienceSource, amount)
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

// 1 april
fun shootFireball(level: ServerLevel, pokemon: Mob, dx: Double, dy: Double, dz: Double) {
    val norm = sqrt(dx * dx + dy * dy + dz * dz)
    if (norm == 0.0) return

    val direction = Vec3(dx / norm, dy / norm, dz / norm)

    val fireball = SmallFireball(
        level,
        pokemon,
        direction
    )

    activeFireballs.add(fireball.uuid)

    val offset = 1.2 // distância pra frente

    fireball.setPos(
        pokemon.x + direction.x * offset,
        pokemon.y + pokemon.eyeHeight * 0.8,
        pokemon.z + direction.z * offset
    )

    level.addFreshEntity(fireball)

    level.playSound(
        null,
        pokemon.blockPosition(),
        SoundEvents.BLAZE_SHOOT,
        SoundSource.HOSTILE,
        0.6f,
        (0.8f + level.random.nextFloat() * 0.4f)
    )
}

fun applyNukeKnockback(level: ServerLevel, x: Double, y: Double, z: Double) {
    val radius = 30.0

    val entities = level.getEntitiesOfClass(
        LivingEntity::class.java,
        AABB(
            x - radius, y - radius, z - radius,
            x + radius, y + radius, z + radius
        )
    )

    entities.forEach { entity ->
        val dx = entity.x - x
        val dy = entity.y - y
        val dz = entity.z - z

        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        if (distance == 0.0) return@forEach

        val strength = (1.5 - (distance / radius)).coerceAtLeast(0.1)

        entity.push(
            dx / distance * strength * 3.5, // horizontal
            0.8 * strength,                 // vertical
            dz / distance * strength * 3.5
        )
    }
}

fun createNuke(level: ServerLevel, x: Double, y: Double, z: Double) {
    val radius = 112 // ~7 chunks
    val step = 8

    for (dx in -radius..radius step step) {
        for (dz in -radius..radius step step) {

            val distance = sqrt((dx * dx + dz * dz).toDouble())
            if (distance > radius) continue

            val px = x + dx
            val pz = z + dz

            level.explode(
                null,
                px,
                y,
                pz,
                4.0f,
                Level.ExplosionInteraction.BLOCK // destrói o mapa
            )
        }
    }
}

fun handleNukeSystem(level: ServerLevel) {
    val toRemove = mutableListOf<UUID>()

    nukeActive.forEach { (pokemonId, active) ->
        if (!active) return@forEach

        val pokemon = level.getEntity(pokemonId) as? Mob ?: return@forEach
        val ownerUUID = (pokemon as? PokemonEntity)?.ownerUUID
        val owner = ownerUUID?.let { level.server.playerList.getPlayer(it) }

        val time = nukeTimer.getOrDefault(pokemonId, 0)

        if (time <= 0) {
            val target = findClosestMonsterToPokemon(level, pokemon)

            val x = target?.x ?: pokemon.x
            val y = target?.y ?: pokemon.y
            val z = target?.z ?: pokemon.z

            if (time % 3 == 0) {
                repeat(3) {
                    val offsetX = (level.random.nextDouble() - 0.5) * 6
                    val offsetZ = (level.random.nextDouble() - 0.5) * 6

                    createNuke(level, x + offsetX, y, z + offsetZ)
                }

                applyNukeKnockback(level, x, y, z)

                level.playSound(
                    null,
                    BlockPos.containing(x, y, z),
                    SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.HOSTILE,
                    5.0f,
                    0.6f
                )
            }

            if (time <= -100) {
                toRemove.add(pokemonId)
            }

            nukeTimer[pokemonId] = time - 1
            return@forEach
        }

        //contagem
        val elapsed = 372 - time

        // 0.0s — AVISO + MÚSICA
        if (elapsed == 0) {
            sendMessage(
                owner,
                "INCOMING NUKE...",
                ChatFormatting.DARK_RED
            )

            //val sound = SoundEvent.createVariableRangeEvent(
                //ResourceLocation("cobblebrain", "nuke_music")
            //)

            //level.playSeededSound(
                //null,
                //pokemon.x,
                //pokemon.y,
                //pokemon.z,
                //sound,
                //SoundSource.RECORDS,
                //3.0f,
                //1.0f,
                //level.random.nextLong()
            //)
        }

        // 3.3s — LEVITAR + PARTÍCULAS
        if (elapsed >= 66) {
            pokemon.addEffect(
                MobEffectInstance(
                    MobEffects.LEVITATION,
                    10,
                    1
                )
            )

            repeat(5) {
                level.sendParticles(
                    ParticleTypes.FLAME,
                    pokemon.x,
                    pokemon.y + 1.0,
                    pokemon.z,
                    1,
                    0.3, 0.5, 0.3,
                    0.0
                )
            }
        }

        // 6.4s — TELA PISCANDO
        if (elapsed >= 128 && elapsed % 20 == 0) {
            println("piscar")
            // triggerRedFlash(owner)
        }

        // ↓ decrementa no final
        nukeTimer[pokemonId] = time - 1
    }

    // cleanup seguro
    toRemove.forEach {
        nukeActive.remove(it)
        nukeTimer.remove(it)
    }
}

fun handlePsychicStand(level: ServerLevel) {
    val toRemove = mutableListOf<UUID>()

    psychicStandActive.forEach { (pokemonId, active) ->
        if (!active) return@forEach

        val pokemon = level.getEntity(pokemonId) as? Mob ?: return@forEach
        val time = psychicStandTimer.getOrDefault(pokemonId, 0)
        val ownerUUID = (pokemon as? PokemonEntity)?.ownerUUID

        if (time <= 0) {
            val radius = 96.0

            val entities = level.getEntitiesOfClass(
                LivingEntity::class.java,
                pokemon.boundingBox.inflate(radius)
            )

            entities.forEach { entity ->
                entity.removeEffect(MobEffects.LEVITATION)
            }

            toRemove.add(pokemonId)
            return@forEach
        }

        //if (time == 300) {
            //val sound = SoundEvent.createVariableRangeEvent(
                //ResourceLocation("cobblebrain", "psychicstand_music")
            //)

            //level.playSeededSound(
                //null,
                //pokemon.x,
                //pokemon.y,
                //pokemon.z,
                //sound,
                //SoundSource.RECORDS,
                //3.0f,
                //1.0f,
                //level.random.nextLong()
            //)
        //}

        // dia/noite
        val interval = 6

        if (time % interval == 0) {
            val current = level.dayTime % 24000

            if (current < 12000) {
                level.dayTime = level.dayTime - current + 13000
            } else {
                level.dayTime = level.dayTime - current + 1000
            }
        }

        // levitacao
        val radius = 96.0

        val entities = level.getEntitiesOfClass(
            LivingEntity::class.java,
            pokemon.boundingBox.inflate(radius)
        )

        entities.forEach { entity ->
            if (entity != pokemon && entity.uuid != ownerUUID) {
                entity.addEffect(
                    MobEffectInstance(
                        MobEffects.LEVITATION,
                        10,
                        1
                    )
                )
            }
        }

        // particulas
        repeat(10) {
            level.sendParticles(
                ParticleTypes.PORTAL,
                pokemon.x,
                pokemon.y + 1.0,
                pokemon.z,
                1,
                1.0, 1.0, 1.0,
                0.0
            )
        }

        psychicStandTimer[pokemonId] = time - 1
    }

    toRemove.forEach {
        psychicStandActive.remove(it)
        psychicStandTimer.remove(it)
    }
}

fun handleImaginaryTechnique(level: ServerLevel) {
    val toRemove = mutableListOf<UUID>()

    imaginaryActive.forEach { (pokemonId, active) ->
        if (!active) return@forEach

        val pokemon = level.getEntity(pokemonId) as? Mob ?: return@forEach
        val time = imaginaryTimer.getOrDefault(pokemonId, 0)

        if (time <= 0) {
            toRemove.add(pokemonId)
            return@forEach
        }

        val elapsed = 120 - time

        // START + TARGET
        if (time == 120) {
            //val sound = SoundEvent.createVariableRangeEvent(
                //ResourceLocation("cobblebrain", "imaginarytechnique_music")
            //)

            //level.playSeededSound(
                //null,
                //pokemon.x,
                //pokemon.y,
                //pokemon.z,
                //sound,
                //SoundSource.RECORDS,
                //3.0f,
                //1.0f,
                //level.random.nextLong()
            //)

            val target = findTarget(level, pokemon)

            if (target != null) {
                imaginaryTarget[pokemonId] = target.uuid
                target.addEffect(MobEffectInstance(MobEffects.GLOWING, 500, 0))
            }
        }

        if (elapsed <= 105 || imaginaryRunning.contains(pokemonId)) {
            val targetY = pokemon.y + 0.05

            pokemon.setDeltaMovement(0.0, 0.0, 0.0)
            pokemon.setPos(pokemon.x, targetY, pokemon.z)
            pokemon.fallDistance = 0f

            pokemon.addEffect(
                MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, false, false)
            )
        }

        // red + blue be like:
        if (elapsed in 20..40) {
            spawnSphere(level, pokemon, ParticleTypes.SOUL_FIRE_FLAME, 2.0)
        }

        if (elapsed in 40..60) {
            spawnSphere(level, pokemon, ParticleTypes.FLAME, 2.0)
        }

        if (elapsed in 60..100) {
            spawnSphere(level, pokemon, ParticleTypes.PORTAL, 6.0)

            level.sendParticles(
                ParticleTypes.PORTAL,
                pokemon.x,
                pokemon.y + 1.5,
                pokemon.z,
                50,
                1.5, 1.5, 1.5,
                0.2
            )
        }

        if (elapsed == 100 && !imaginaryRunning.contains(pokemonId)) {
            imaginaryRunning.add(pokemonId)
        }

        if (imaginaryRunning.contains(pokemonId)) {

            val targetUUID = imaginaryTarget[pokemonId]
            val target = targetUUID?.let { level.getEntity(it) as? LivingEntity }

            val direction = if (target != null) {
                target.eyePosition.subtract(pokemon.eyePosition).normalize()
            } else {
                pokemon.lookAngle
            }

            val start = pokemon.eyePosition.add(direction.scale(2.5))

            val speed = 1.2 + (elapsed * 0.05)
            val distance = (elapsed - 100) * speed

            if (distance > 0) {

                val pos = start.add(direction.scale(distance))
                val radius = 2.5 + (elapsed - 100) * 0.15

                // esfera
                repeat(60) {
                    val theta = level.random.nextDouble() * Math.PI * 2
                    val phi = level.random.nextDouble() * Math.PI

                    val x = pos.x + cos(theta) * sin(phi) * radius
                    val y = pos.y + cos(phi) * radius
                    val z = pos.z + sin(theta) * sin(phi) * radius

                    level.sendParticles(
                        ParticleTypes.PORTAL,
                        x, y, z,
                        1,
                        0.0, 0.0, 0.0,
                        0.0
                    )
                }

                val entities = level.getEntitiesOfClass(
                    LivingEntity::class.java,
                    AABB(
                        pos.x - radius, pos.y - radius, pos.z - radius,
                        pos.x + radius, pos.y + radius, pos.z + radius
                    )
                )

                if (distance < 3.0) {
                    imaginaryTimer[pokemonId] = time - 1
                    return@forEach
                }

                val hitEntity = entities.any { it != pokemon }
                val hitBlock = !level.isEmptyBlock(BlockPos.containing(pos))

                if (hitEntity || hitBlock ||
                    (target != null && pos.distanceTo(target.position()) < 2.5)
                ) {

                    val explosionPower = 10 // raio base
                    val step = 3

                    for (dx in -explosionPower..explosionPower step step) {
                        for (dz in -explosionPower..explosionPower step step) {

                            val distance = sqrt((dx * dx + dz * dz).toDouble())
                            if (distance > explosionPower) continue

                            level.explode(
                                null,
                                pos.x + dx,
                                pos.y,
                                pos.z + dz,
                                10.0f,
                                Level.ExplosionInteraction.BLOCK
                            )
                        }
                    }

                    applyNukeKnockback(level, pos.x, pos.y, pos.z)

                    imaginaryRunning.remove(pokemonId)
                    imaginaryActive[pokemonId] = false
                    CommandState.activeCommands[pokemonId] = "idle"

                    return@forEach
                }

                entities.forEach {
                    if (it != pokemon) {
                        val pull = pos.subtract(it.position()).normalize()
                        it.deltaMovement = it.deltaMovement.add(pull.scale(0.2))
                    }
                }

                if (elapsed > 160) {
                    imaginaryRunning.remove(pokemonId)
                    imaginaryActive[pokemonId] = false
                    CommandState.activeCommands[pokemonId] = "idle"
                }
            }
        }

        imaginaryTimer[pokemonId] = time - 1
    }

    toRemove.forEach {
        imaginaryActive.remove(it)
        imaginaryTimer.remove(it)
        imaginaryRunning.remove(it)
        imaginaryTarget.remove(it)

        CommandState.activeCommands[it] = "idle"
    }
}

fun spawnSphere(
    level: ServerLevel,
    entity: Mob,
    particle: ParticleOptions,
    radius: Double
) {
    for (i in 0..80) {
        val angle = i * (Math.PI * 2 / 80)

        val x = entity.x + cos(angle) * radius
        val z = entity.z + sin(angle) * radius

        level.sendParticles(
            particle,
            x,
            entity.y + 1.5,
            z,
            1,
            0.0, 0.0, 0.0,
            0.0
        )
    }
}

fun findTarget(level: ServerLevel, pokemon: Mob): LivingEntity? {
    val minRange = 15.0
    val maxRange = 30.0

    val box = pokemon.boundingBox.inflate(maxRange)

    val candidates = level.getEntitiesOfClass(Mob::class.java, box) { mob ->
        mob.isAlive && mob != pokemon
    }

    // filtra distância mínima
    val filtered = candidates.filter {
        val d = it.distanceTo(pokemon)
        d in minRange..maxRange
    }

    if (filtered.isEmpty()) return null

    return filtered.maxByOrNull { it.distanceTo(pokemon) }
}

fun handleFinalJudgment(level: ServerLevel) {
    val toRemove = mutableListOf<UUID>()

    finalJudgmentActive.forEach { (pokemonId, active) ->
        if (!active) return@forEach

        val pokemon = level.getEntity(pokemonId) as? Mob ?: return@forEach
        val time = finalJudgmentTimer.getOrDefault(pokemonId, 0)

        if (time <= 0) {
            pokemon.isInvulnerable = false
            toRemove.add(pokemonId)
            return@forEach
        }

        val elapsed = 120 - time

        if (time == 120) {
            level.server.overworld().setWeatherParameters(0, 600, true, true)

            pokemon.invulnerableTime = 100
            pokemon.isInvulnerable = true
            //val sound = SoundEvent.createVariableRangeEvent(
            //    ResourceLocation("cobblebrain", "finaljudgment_music")
            //)

            //level.playSeededSound(
                //null,
                //pokemon.x,
                //pokemon.y,
                //pokemon.z,
                //sound,
                //SoundSource.RECORDS,
                //7.0f,
                //1.0f,
                //level.random.nextLong()
            //)
        }

        if (elapsed % 40 == 0) {

            // raio no próprio pokemon (cura)
            val lightning = EntityType.LIGHTNING_BOLT.create(level)
            lightning?.moveTo(pokemon.x, pokemon.y, pokemon.z)
            level.addFreshEntity(lightning)

            pokemon.heal(pokemon.maxHealth)

            val range = 20.0
            val box = pokemon.boundingBox.inflate(range)

            val hostiles = level.getEntitiesOfClass(
                Monster::class.java,
                box
            ).filter { it.isAlive }

            val targets = if (hostiles.isNotEmpty()) {
                hostiles.shuffled().take(5)
            } else {
                val passives = level.getEntitiesOfClass(
                    LivingEntity::class.java,
                    box
                ).filter { it.isAlive && it !is Monster && it != pokemon }

                if (passives.isNotEmpty()) {
                    passives.shuffled().take(5)
                } else {
                    emptyList()
                }
            }

            if (targets.isNotEmpty()) {
                targets.forEach { target ->
                    val lightning = EntityType.LIGHTNING_BOLT.create(level)
                    lightning?.moveTo(target.x, target.y, target.z)
                    level.addFreshEntity(lightning)

                    target.hurt(level.damageSources().lightningBolt(), 12f)
                }
            } else {
                // fallback: posições aleatórias
                repeat(5) {
                    val dx = (level.random.nextDouble() - 0.5) * 20
                    val dz = (level.random.nextDouble() - 0.5) * 20

                    val px = pokemon.x + dx
                    val pz = pokemon.z + dz

                    val lightning = EntityType.LIGHTNING_BOLT.create(level)
                    lightning?.moveTo(px, pokemon.y, pz)
                    level.addFreshEntity(lightning)
                }
            }

            // som forte
            level.playSound(
                null,
                pokemon.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.HOSTILE,
                3.0f,
                1.0f
            )
        }

        finalJudgmentTimer[pokemonId] = time - 1
    }

    toRemove.forEach {
        finalJudgmentActive.remove(it)
        finalJudgmentTimer.remove(it)

        CommandState.activeCommands[it] = "idle"
        announcedStates[it] = "idle"
    }
}
fun handleSSStyle(level: ServerLevel) {
    val toRemove = mutableListOf<UUID>()

    ssStyleActive.forEach { (playerId, active) ->
        if (!active) return@forEach

        val player = level.server.playerList.getPlayer(playerId)

        if (player == null || !player.isAlive) {
            toRemove.add(playerId)
            return@forEach
        }

        val time = ssStyleTimer.getOrDefault(playerId, 0)

        if (time <= 0) {
            toRemove.add(playerId)
            return@forEach
        }

        val pokemon = level.getEntitiesOfClass(
            PokemonEntity::class.java,
            player.boundingBox.inflate(20.0)
        ).firstOrNull {
            it.pokemon.getOwnerUUID() == player.uuid
        }

        pokemon?.isInvulnerable = true

        val mobs = ssStyleSpawnedMobs.getOrPut(playerId) { mutableSetOf() }

        // =========================
        // INIT (uma vez)
        // =========================
        if (!ssStyleStarted.getOrDefault(playerId, false)) {
            ssStyleStarted[playerId] = true
            ssStyleWave[playerId] = 1
            ssStyleLastReport[playerId] = 0

            level.server.overworld().dayTime = 13000

            //val sound = SoundEvent.createVariableRangeEvent(
                //ResourceLocation("cobblebrain", "ssstyle_music")
            //)

            //player.playNotifySound(sound, SoundSource.RECORDS, 5.0f, 1.0f)

            val enchantRegistry = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)

            val bow = ItemStack(Items.BOW)
            bow.enchant(enchantRegistry.getHolderOrThrow(Enchantments.POWER), 5)
            bow.enchant(enchantRegistry.getHolderOrThrow(Enchantments.PUNCH), 2)
            bow.enchant(enchantRegistry.getHolderOrThrow(Enchantments.FLAME), 1)
            bow.enchant(enchantRegistry.getHolderOrThrow(Enchantments.INFINITY), 1)
            bow.enchant(enchantRegistry.getHolderOrThrow(Enchantments.UNBREAKING), 3)
            bow.enchant(enchantRegistry.getHolderOrThrow(Enchantments.MULTISHOT), 3)

            player.inventory.setItem(8, bow)
            player.inventory.setItem(0, ItemStack(Items.ARROW, 1))

            val sword = ItemStack(Items.DIAMOND_SWORD)
            sword.enchant(enchantRegistry.getHolderOrThrow(Enchantments.KNOCKBACK), 2)
            sword.enchant(enchantRegistry.getHolderOrThrow(Enchantments.FIRE_ASPECT), 1)
            sword.enchant(enchantRegistry.getHolderOrThrow(Enchantments.SHARPNESS), 5)

            player.addItem(sword)

            player.setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.ELYTRA))

            val fireworks = ItemStack(Items.FIREWORK_ROCKET, 64)
            fireworks.set(DataComponents.FIREWORKS, Fireworks(3, listOf()))

            player.setItemInHand(InteractionHand.OFF_HAND, fireworks.copy())
            repeat(2) { player.addItem(fireworks.copy()) }

            val extra = ItemStack(Items.FIREWORK_ROCKET, 68)
            extra.set(DataComponents.FIREWORKS, Fireworks(3, listOf()))
            player.addItem(extra)
        }

        // SPAWN CONTROLADO
        fun spawnWave(wave: Int) {
            mobs.clear()

            fun spawn(type: EntityType<out Mob>, amount: Int, yOffset: Double) {
                val radius = 35.0

                for (i in 0 until amount) {
                    val angle = (2 * Math.PI / amount) * i

                    val x = player.x + Math.cos(angle) * radius
                    val z = player.z + Math.sin(angle) * radius
                    val y = player.y + yOffset

                    val mob = type.create(level)
                    if (mob != null) {
                        mob.moveTo(x, y, z)
                        mob.addEffect(MobEffectInstance(MobEffects.GLOWING, 200, 0))
                        level.addFreshEntity(mob)
                        mobs.add(mob.uuid)
                    }
                }
            }

            when (wave) {
                1 -> {
                    spawn(EntityType.GHAST, 15, 10.0)
                    spawn(EntityType.PHANTOM, 6, 5.0)
                    sendMessage(
                        player,
                        "WAVE 1",
                        ChatFormatting.RED
                    )
                }

                2 -> {
                    spawn(EntityType.GHAST, 20, 10.0)
                    spawn(EntityType.PHANTOM, 9, 5.0)
                    sendMessage(
                        player,
                        "WAVE 2",
                        ChatFormatting.RED
                    )
                }

                3 -> {
                    spawn(EntityType.GHAST, 3, 10.0)
                    sendMessage(
                        player,
                        "WAVE 3",
                        ChatFormatting.RED
                    )

                    val dragon = EntityType.WITHER.create(level)
                    dragon?.moveTo(player.x, player.y + 30, player.z)
                    dragon?.let { level.addFreshEntity(it) }
                }
                4 -> {
                    spawn(EntityType.GHAST, 20, 10.0)
                    spawn(EntityType.PHANTOM, 9, 5.0)
                    sendMessage(
                        player,
                        "FINAL WAVE",
                        ChatFormatting.RED
                    )
                    val dragon = EntityType.WITHER.create(level)
                    dragon?.moveTo(player.x, player.y + 30, player.z)
                    dragon?.let { level.addFreshEntity(it) }
                }
            }
        }

        if (mobs.isEmpty()) {
            val wave = ssStyleWave.getOrDefault(playerId, 1)
            spawnWave(wave)
        }

        val iterator = mobs.iterator()

        while (iterator.hasNext()) {
            val uuid = iterator.next()
            val entity = level.getEntity(uuid) as? LivingEntity

            if (entity == null || !entity.isAlive) {
                iterator.remove()

                if (entity?.lastHurtByMob?.uuid == player.uuid) {
                    ssStyleKills[playerId] =
                        ssStyleKills.getOrDefault(playerId, 0) + 1
                }
            }
        }

        if (player.isUsingItem && player.useItem.item is BowItem) {

            val used = player.ticksUsingItem

            if (used >= 12) { // ajusta aqui
                player.releaseUsingItem()
            }
        }

        // AVANÇO POR GHAST
        var ghastAlive = false

        for (uuid in mobs) {
            val entity = level.getEntity(uuid)

            if (entity is Ghast && entity.isAlive) {
                ghastAlive = true
                break
            }
        }

        if (!ghastAlive) {
            val currentWave = ssStyleWave.getOrDefault(playerId, 1)

            if (currentWave < 4) {
                ssStyleWave[playerId] = currentWave + 1
                mobs.clear()
            }
        }

        // STYLE (30s)
        val tick = ssStyleLastReport.getOrDefault(playerId, 0) + 1
        ssStyleLastReport[playerId] = tick

        if (tick % 600 == 0) {
            val kills = ssStyleKills.getOrDefault(playerId, 0)

            player.sendSystemMessage(
                Component.literal("STYLE: $kills")
            )
        }

        val duration = 20 * 230

        player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 7))
        player.addEffect(MobEffectInstance(MobEffects.JUMP, duration, 4))
        player.addEffect(MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 1))
        player.addEffect(MobEffectInstance(MobEffects.DIG_SPEED, duration, 4))

        ssStyleTimer[playerId] = time - 1
    }

    // CLEANUP
    toRemove.forEach { id ->
        val player = level.server.playerList.getPlayer(id)

        val pokemon = player?.let {
            level.getEntitiesOfClass(
                PokemonEntity::class.java,
                it.boundingBox.inflate(20.0)
            ).firstOrNull { p ->
                p.pokemon.getOwnerUUID() == id
            }
        }

        pokemon?.isInvulnerable = false

        ssStyleActive.remove(id)
        ssStyleTimer.remove(id)
        ssStyleKills.remove(id)
        ssStyleWave.remove(id)
        ssStyleSpawnedMobs.remove(id)
        ssStyleStarted.remove(id)
        ssStyleLastReport.remove(id)

        CommandState.activeCommands[id] = "idle"
        announcedStates[id] = "idle"
    }
}