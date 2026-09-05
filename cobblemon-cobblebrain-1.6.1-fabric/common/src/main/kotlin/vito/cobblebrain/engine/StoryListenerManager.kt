package vito.cobblebrain.engine

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.NodeType

object StoryListenerManager {

    val activeContinuousTriggerStates = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * Called periodically (e.g. every 10 or 20 ticks) to check continuous triggers
     * such as elapsed time, player coordinates, biome, day/night, weather, etc.
     */
    fun onServerTick() {
        StoryLookAtManager.onServerTick()
        StoryPathfindingManager.onServerTick()
        StoryJumpManager.onServerTick()

        val activeList = StoryExecutor.activeStories.values.toList()
        for (instance in activeList) {
            val player = instance.context.player ?: continue
            val scene = instance.project.getActiveScene() ?: continue

            // Check passive/reactive triggers that do not depend on input signal
            val reactiveTriggers = (scene.nodes + instance.project.globalNodes).filter { node ->
                node.nodeType == NodeType.TRIGGER && node.params["requireInputSignal"] == "false"
            }

            for (trigNode in reactiveTriggers) {
                val trigType = trigNode.params["triggerType"] ?: "START"
                val shouldTrigger = when (trigType) {
                    "PLAYER_COORDINATES", "LOCATION" -> {
                        val tx = trigNode.params["targetX"]?.toDoubleOrNull() ?: 0.0
                        val ty = trigNode.params["targetY"]?.toDoubleOrNull() ?: 64.0
                        val tz = trigNode.params["targetZ"]?.toDoubleOrNull() ?: 0.0
                        val radius = trigNode.params["radius"]?.toDoubleOrNull() ?: 5.0
                        val dx = player.x - tx
                        val dy = player.y - ty
                        val dz = player.z - tz
                        val distSq = dx * dx + dy * dy + dz * dz
                        distSq <= radius * radius
                    }
                    "DAY_NIGHT_CHECK" -> {
                        val timeOfDay = player.serverLevel().dayTime % 24000
                        val isDay = timeOfDay in 0..12999
                        val expected = trigNode.params["timePeriod"] ?: "DAY"
                        (isDay && expected == "DAY") || (!isDay && expected == "NIGHT")
                    }
                    "WEATHER_CHECK" -> {
                        val level = player.serverLevel()
                        val current = if (level.isThundering) "THUNDER" else if (level.isRaining) "RAIN" else "CLEAR"
                        val expected = (trigNode.params["weatherType"] ?: "RAIN").uppercase()
                        current == expected
                    }
                    "PLAYER_LEVEL" -> {
                        val minLvl = trigNode.params["minLevel"]?.toIntOrNull() ?: 10
                        val op = trigNode.params["comparisonOp"] ?: ">="
                        val curLvl = player.experienceLevel
                        when (op) {
                            ">" -> curLvl > minLvl
                            "<" -> curLvl < minLvl
                            "<=" -> curLvl <= minLvl
                            "==" -> curLvl == minLvl
                            else -> curLvl >= minLvl
                        }
                    }
                    "VARIABLE_VALUE_CHECK" -> {
                        val varKey = trigNode.params["varKey"] ?: "var_1"
                        val op = trigNode.params["varOp"] ?: ">="
                        val targetVal = trigNode.params["varValue"] ?: "100"
                        val actualVal = instance.context.variables[varKey]
                        StoryExecutor.evaluateVariableCondition(actualVal, op, targetVal)
                    }
                    else -> false
                }

                val triggerKey = "${instance.storyId}:${trigNode.id}"
                val wasTriggered = activeContinuousTriggerStates[triggerKey] ?: false

                val isIfNot = trigNode.params["triggerCondition"] == "IF_NOT"
                val finalResult = if (isIfNot) !shouldTrigger else shouldTrigger

                if (finalResult) {
                    if (!wasTriggered) {
                        activeContinuousTriggerStates[triggerKey] = true
                        StoryExecutor.executeNodeChain(instance, trigNode, targetPortId = null)
                    }
                } else {
                    activeContinuousTriggerStates[triggerKey] = false
                }
            }
        }
    }

    @Suppress("unused")
    fun onPokemonCatch(player: ServerPlayer, species: String) {
        StoryMissionManager.onTriggerFired(player, "POKEMON_CATCH")
        dispatchReactiveTrigger(player, "POKEMON_CATCH", mapOf("targetSpecies" to species))
    }

    fun onBattleVictory(player: ServerPlayer, targetSpecies: String = "") {
        StoryMissionManager.onTriggerFired(player, "BATTLE_VICTORY")
        dispatchReactiveTrigger(player, "BATTLE_VICTORY", mapOf("targetSpecies" to targetSpecies))
    }

    @Suppress("unused")
    fun onBattleDefeat(player: ServerPlayer) {
        StoryMissionManager.onPlayerDeath(player)
        dispatchReactiveTrigger(player, "BATTLE_DEFEAT", emptyMap())
    }

    @Suppress("unused")
    fun onPokemonInteract(player: ServerPlayer, species: String) {
        StoryMissionManager.onTriggerFired(player, "INTERACT_POKEMON")
        dispatchReactiveTrigger(player, "INTERACT_POKEMON", mapOf("targetSpecies" to species))
    }

    fun onEntityInteract(player: ServerPlayer, entity: Entity) {
        dispatchReactiveEntityTrigger(player, "INTERACT_ENTITY", entity)
    }

    @Suppress("unused")
    fun onBlockInteract(player: ServerPlayer, blockId: String) {
        StoryMissionManager.onTriggerFired(player, "BLOCK_INTERACTED")
        dispatchReactiveTrigger(player, "BLOCK_INTERACTED", mapOf("blockId" to blockId))
    }

    @Suppress("unused")
    fun onBlockPlaced(player: ServerPlayer, blockId: String) {
        StoryMissionManager.onTriggerFired(player, "BLOCK_PLACED")
        dispatchReactiveTrigger(player, "BLOCK_PLACED", mapOf("blockId" to blockId))
    }

    fun onEntityDied(entity: Entity, killer: Entity?) {
        if (entity is ServerPlayer) {
            StoryMissionManager.onPlayerDeath(entity)
        }

        val directPlayer = when (killer) {
            is ServerPlayer -> killer
            is net.minecraft.world.entity.projectile.Projectile -> killer.owner as? ServerPlayer
            is PokemonEntity -> killer.pokemon.getOwnerPlayer()
            else -> null
        }

        val targetPlayers = mutableSetOf<ServerPlayer>()
        if (directPlayer != null) targetPlayers.add(directPlayer)
        if (entity is ServerPlayer) targetPlayers.add(entity)
        targetPlayers.addAll(entity.level().players().filterIsInstance<ServerPlayer>())

        for (player in targetPlayers) {
            dispatchReactiveEntityTrigger(player, "ENTITY_DIED", entity)
        }
    }

    fun onEntityDamaged(entity: Entity, source: Entity?, amount: Float) {
        val directPlayer = when (source) {
            is ServerPlayer -> source
            is net.minecraft.world.entity.projectile.Projectile -> source.owner as? ServerPlayer
            is PokemonEntity -> source.pokemon.getOwnerPlayer()
            else -> null
        }

        val targetPlayers = mutableSetOf<ServerPlayer>()
        if (directPlayer != null) targetPlayers.add(directPlayer)
        if (entity is ServerPlayer) targetPlayers.add(entity)
        targetPlayers.addAll(entity.level().players().filterIsInstance<ServerPlayer>())

        val minDmgMap = mapOf("damageAmount" to amount.toString())
        for (player in targetPlayers) {
            dispatchReactiveEntityTrigger(player, "ENTITY_DAMAGED", entity, minDmgMap)
        }
    }

    @Suppress("unused")
    fun onEntitySpawned(entity: Entity) {
        val players = entity.level().players().filterIsInstance<ServerPlayer>()
        for (player in players) {
            dispatchReactiveEntityTrigger(player, "ENTITY_SPAWNED", entity)
        }
    }

    fun matchesEntityFilters(node: NodeData, entity: Entity?): Boolean {
        if (entity == null) return false

        // 1. Story Tag Check
        val requiredTag = (node.params["requiredStoryTag"] ?: node.params["storyTag"] ?: node.params["targetStoryTag"] ?: node.params["targetIdentifier"])?.trim()
        if (!requiredTag.isNullOrBlank()) {
            if (!entity.tags.contains(requiredTag)) {
                return false
            }
        }

        val targetType = node.params["targetType"] ?: if (entity is PokemonEntity) "COBBLEMON" else "GENERIC"

        if (targetType == "COBBLEMON") {
            if (entity !is PokemonEntity) return false
            val poke = entity.pokemon

            // Species check
            val targetSpecies = node.params["targetSpecies"]?.ifBlank { node.params["species"] ?: "" }?.trim() ?: ""
            if (targetSpecies.isNotBlank()) {
                val sName = poke.species.name
                val sId = poke.species.showdownId()
                if (!sName.equals(targetSpecies, ignoreCase = true) && !sId.equals(targetSpecies, ignoreCase = true)) {
                    return false
                }
            }

            // Form check
            val targetForm = node.params["form"]?.trim() ?: ""
            if (targetForm.isNotBlank()) {
                if (!poke.form.name.equals(targetForm, ignoreCase = true)) {
                    return false
                }
            }

            // Level range check
            val minLevel = node.params["minLevel"]?.toIntOrNull() ?: 1
            val maxLevel = node.params["maxLevel"]?.toIntOrNull() ?: 100
            val pokeLevel = poke.level
            if (pokeLevel < minLevel || pokeLevel > maxLevel) {
                return false
            }

            // Shiny check
            val shinyMode = node.params["shinyMode"] ?: "ANY"
            if (shinyMode == "YES" && !poke.shiny) return false
            if (shinyMode == "NO" && poke.shiny) return false

            // Status check (Wild vs Party)
            val statusMode = node.params["pokemonStatus"] ?: "ANY"
            val isOwned = poke.getOwnerUUID() != null
            if (statusMode == "WILD" && isOwned) return false
            if (statusMode == "PARTY" && !isOwned) return false

            return true
        } else {
            // Generic Entity Mode
            val targetEntityType = node.params["entityType"]?.trim() ?: ""
            if (targetEntityType.isNotBlank() && !targetEntityType.equals("ANY", ignoreCase = true) && targetEntityType != "*") {
                val curTypeStr = entity.type.toString()
                val curDescId = entity.type.descriptionId
                val regKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)
                val fullKey = regKey.toString()
                val keyPath = regKey.path
                val targetPath = targetEntityType.substringAfter(":")

                val isMatch = fullKey.equals(targetEntityType, ignoreCase = true) ||
                              keyPath.equals(targetPath, ignoreCase = true) ||
                              curTypeStr.equals(targetEntityType, ignoreCase = true) ||
                              curDescId.endsWith(".$targetPath", ignoreCase = true) ||
                              curDescId.contains(targetPath, ignoreCase = true)

                if (!isMatch) {
                    return false
                }
            }
            return true
        }
    }

    private fun dispatchReactiveEntityTrigger(player: ServerPlayer, triggerType: String, entity: Entity?, eventData: Map<String, String> = emptyMap()) {
        StoryMissionManager.onEntityTriggerFired(player, triggerType, entity)

        val activeList = StoryExecutor.activeStories.values.filter { it.context.player?.uuid == player.uuid }
        for (instance in activeList) {
            val validation = StoryExecutor.validatePrerequisites(instance.project, player, player.server)
            if (!validation.isValid) {
                StoryExecutor.handlePrerequisiteFailure(instance.project, player, validation)
                continue
            }

            val allNodes = mutableListOf<NodeData>()
            val activeScene = instance.project.getActiveScene()
            if (activeScene != null) allNodes.addAll(activeScene.nodes)
            allNodes.addAll(instance.project.globalNodes)
            instance.project.scenes.forEach { s ->
                if (s.id != activeScene?.id) {
                    allNodes.addAll(s.nodes.filter { instance.context.waitingTriggers.contains(it.id) })
                }
            }

            val matchingNodes = allNodes.filter { node ->
                node.nodeType == NodeType.TRIGGER &&
                (node.params["triggerType"] == triggerType ||
                 (triggerType == "ENTITY_DIED" && (node.params["triggerType"] == "ENTITY_DIED" || node.params["triggerType"] == "ENTITY_DEATH" || node.params["triggerType"] == "ON_ENTITY_DIED")) ||
                 (triggerType == "ENTITY_DAMAGED" && (node.params["triggerType"] == "ENTITY_DAMAGED" || node.params["triggerType"] == "ON_ENTITY_DAMAGED")) ||
                 (triggerType == "INTERACT_ENTITY" && node.params["triggerType"] == "INTERACT_POKEMON") ||
                 (triggerType == "INTERACT_POKEMON" && node.params["triggerType"] == "INTERACT_ENTITY"))
            }

            for (node in matchingNodes) {
                var matches = matchesEntityFilters(node, entity)

                val minDamage = node.params["minDamage"]?.toFloatOrNull()
                val actualDamage = eventData["damageAmount"]?.toFloatOrNull()
                if (minDamage != null && actualDamage != null && actualDamage < minDamage) {
                    matches = false
                }

                val isIfNot = node.params["triggerCondition"] == "IF_NOT"
                val finalResult = if (isIfNot) !matches else matches
                if (finalResult) {
                    StoryDebugger.recordLog(
                        storyId = instance.storyId.ifBlank { instance.project.id },
                        blockId = node.id,
                        blockType = NodeType.TRIGGER,
                        status = NodeExecutionStatus.SUCCESS,
                        level = "INFO",
                        message = "Trigger '${node.title.ifBlank { "Entity $triggerType" }}' fired on '${entity?.type?.descriptionId ?: "entity"}'",
                        server = player.server
                    )
                    StoryExecutor.executeNodeChain(instance, node, targetPortId = null)
                }
            }
        }
    }

    private fun dispatchReactiveTrigger(player: ServerPlayer, triggerType: String, eventData: Map<String, String>) {
        val activeList = StoryExecutor.activeStories.values.filter { it.context.player?.uuid == player.uuid }
        for (instance in activeList) {
            val validation = StoryExecutor.validatePrerequisites(instance.project, player, player.server)
            if (!validation.isValid) {
                StoryExecutor.handlePrerequisiteFailure(instance.project, player, validation)
                continue
            }
            val scene = instance.project.getActiveScene() ?: continue
            val matchingNodes = (scene.nodes + instance.project.globalNodes).filter { node ->
                node.nodeType == NodeType.TRIGGER &&
                (node.params["triggerType"] == triggerType ||
                 (triggerType == "POKEMON_CATCH" && node.params["triggerType"] == "CATCH_POKEMON") ||
                 (triggerType == "BATTLE_VICTORY" && node.params["triggerType"] == "DEFEAT_POKEMON") ||
                 (triggerType == "INTERACT_POKEMON" && node.params["triggerType"] == "INTERACT_ENTITY"))
            }

            for (node in matchingNodes) {
                var matches = true
                val targetSpecies = node.params["targetSpecies"]
                if (!targetSpecies.isNullOrBlank() && eventData.containsKey("targetSpecies")) {
                    val evSpecies = eventData["targetSpecies"] ?: ""
                    if (!evSpecies.contains(targetSpecies, ignoreCase = true)) {
                        matches = false
                    }
                }

                val targetBlock = node.params["blockId"]
                if (!targetBlock.isNullOrBlank() && eventData.containsKey("blockId")) {
                    val evBlock = eventData["blockId"] ?: ""
                    if (!evBlock.contains(targetBlock, ignoreCase = true)) {
                        matches = false
                    }
                }

                val targetSlot = node.params["slotId"] ?: node.params["profileId"]
                if (!targetSlot.isNullOrBlank() && eventData.containsKey("slotId")) {
                    val evSlot = eventData["slotId"] ?: ""
                    if (!evSlot.contains(targetSlot, ignoreCase = true)) {
                        matches = false
                    }
                }

                val isIfNot = node.params["triggerCondition"] == "IF_NOT"
                val finalResult = if (isIfNot) !matches else matches
                if (finalResult) {
                    StoryExecutor.executeNodeChain(instance, node, targetPortId = null)
                }
            }
        }
    }

    fun onCheckpointLoaded(player: ServerPlayer, profileId: String, firstJoinOnly: Boolean = false) {
        val eventData = mapOf(
            "slotId" to profileId,
            "profileId" to profileId,
            "firstJoinOnly" to firstJoinOnly.toString()
        )
        dispatchReactiveTrigger(player, "ON_CHECKPOINT_LOADED", eventData)
    }
}
