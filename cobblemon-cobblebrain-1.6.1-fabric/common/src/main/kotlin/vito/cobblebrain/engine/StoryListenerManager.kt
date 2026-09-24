package vito.cobblebrain.engine

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.NodeType

object StoryListenerManager {

    val activeContinuousTriggerStates = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    val triggerArmTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    val lastPlayerItemCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /**
     * Called periodically (e.g. every 10 or 20 ticks) to check continuous triggers
     * such as elapsed time, player coordinates, biome, day/night, weather, etc.
     */
    fun onServerTick() {
        StoryLookAtManager.onServerTick()
        StoryPathfindingManager.onServerTick()
        StoryJumpManager.onServerTick()
        StoryMissionManager.onServerTick()

        val activeList = StoryExecutor.activeStories.values.toList()
        for (instance in activeList) {
            val player = instance.context.player ?: continue
            val scene = instance.project.getActiveScene() ?: continue

            val waitingIds = instance.context.waitingTriggers
            val allSceneNodes = scene.nodes + scene.nodes.filter { it.nodeType == NodeType.CONSTRUCTION }.flatMap { it.innerNodes }
            val reactiveTriggers = (allSceneNodes + instance.project.globalNodes).filter { node ->
                node.nodeType == NodeType.TRIGGER && (node.params["requireInputSignal"] == "false" || waitingIds.contains(node.id))
            }

            for (trigNode in reactiveTriggers) {
                val trigType = trigNode.params["triggerType"] ?: "START"
                val shouldTrigger = when (trigType) {
                    "START", "STORY_STARTED" -> false
                    "PLAYER_COORDINATES", "LOCATION" -> {
                        val tx: Double
                        val ty: Double
                        val tz: Double
                        val coordsStr = trigNode.params["coordinates"]
                        if (!coordsStr.isNullOrBlank()) {
                            val vec = CoordinateResolver.resolveVec3(coordsStr, player, player.server)
                            tx = vec.x
                            ty = vec.y
                            tz = vec.z
                        } else {
                            tx = trigNode.params["targetX"]?.toDoubleOrNull() ?: 0.0
                            ty = trigNode.params["targetY"]?.toDoubleOrNull() ?: 64.0
                            tz = trigNode.params["targetZ"]?.toDoubleOrNull() ?: 0.0
                        }
                        val radius = trigNode.params["radius"]?.toDoubleOrNull() ?: 5.0
                        val dx = player.x - tx
                        val dy = player.y - ty
                        val dz = player.z - tz
                        val distSq = dx * dx + dy * dy + dz * dz
                        distSq <= radius * radius
                    }
                    "PLAYER_BIOME" -> {
                        val targetBiome = (trigNode.params["biomeId"] ?: "minecraft:plains").trim().lowercase()
                        val biomeHolder = player.serverLevel().getBiome(player.blockPosition())
                        val currentBiome = biomeHolder.unwrapKey().map { it.location().toString() }.orElse("")
                        val biomePath = currentBiome.substringAfter(":")
                        val targetPath = targetBiome.substringAfter(":")
                        currentBiome.equals(targetBiome, ignoreCase = true) || biomePath.equals(targetPath, ignoreCase = true)
                    }
                    "DAY_NIGHT_CHECK" -> {
                        val timeOfDay = Math.floorMod(player.serverLevel().dayTime, 24000L)
                        val isDay = timeOfDay < 13000L || timeOfDay >= 23000L
                        val expected = (trigNode.params["timePeriod"] ?: "DAY").trim().uppercase()
                        (isDay && expected == "DAY") || (!isDay && expected == "NIGHT")
                    }
                    "TIME_OF_DAY" -> {
                        val targetTicks = trigNode.params["timeOfDayTicks"]?.toIntOrNull() ?: 6000
                        val timeOfDay = Math.floorMod(player.serverLevel().dayTime, 24000L).toInt()
                        val diff = Math.abs(timeOfDay - targetTicks)
                        diff <= 20 || diff >= 23980
                    }
                    "DAYS_PASSED" -> {
                        val requiredDays = trigNode.params["daysCount"]?.toLongOrNull() ?: 1L
                        val currentDays = Math.max(0L, player.serverLevel().dayTime / 24000L)
                        currentDays >= requiredDays
                    }
                    "TIME_ELAPSED" -> {
                        val reqSec = trigNode.params["timeSeconds"]?.toLongOrNull() ?: trigNode.params["seconds"]?.toLongOrNull() ?: 10L
                        val armKey = "${instance.storyId}:${trigNode.id}"
                        val armTime = triggerArmTimes.computeIfAbsent(armKey) { System.currentTimeMillis() }
                        val elapsedSec = (System.currentTimeMillis() - armTime) / 1000L
                        elapsedSec >= reqSec
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
                    "PLAYER_HELD_ITEM" -> {
                        val targetItem = trigNode.params["heldItemId"] ?: trigNode.params["targetItem"] ?: "minecraft:diamond_sword"
                        StoryMissionManager.isPlayerHoldingItem(player, targetItem)
                    }
                    "PLAYER_INVENTORY_HAS_ITEM", "ITEM_IN_INVENTORY" -> {
                        val targetItem = trigNode.params["requiredItem"] ?: trigNode.params["targetItem"] ?: "cobblemon:potion"
                        val reqCount = trigNode.params["requiredCount"]?.toIntOrNull() ?: trigNode.params["targetCount"]?.toIntOrNull() ?: 1
                        StoryMissionManager.getPlayerItemCount(player, targetItem) >= reqCount
                    }
                    "PLAYER_INVENTORY_ITEM_REMOVED" -> {
                        val targetItem = trigNode.params["removedItemId"] ?: trigNode.params["targetItem"] ?: trigNode.params["heldItemId"] ?: "cobblemon:poke_ball"
                        val curCount = StoryMissionManager.getPlayerItemCount(player, targetItem)
                        val cacheKey = "${instance.storyId}:${trigNode.id}:${player.uuid}:$targetItem"
                        val prevCount = lastPlayerItemCounts[cacheKey]
                        lastPlayerItemCounts[cacheKey] = curCount
                        prevCount != null && prevCount > curCount
                    }
                    "PLAYER_ITEM_COUNT" -> {
                        val targetItem = trigNode.params["checkItemId"] ?: trigNode.params["targetItem"] ?: "cobblemon:poke_ball"
                        val minCount = trigNode.params["minCount"]?.toIntOrNull() ?: trigNode.params["targetCount"]?.toIntOrNull() ?: 1
                        val op = trigNode.params["comparisonOp"] ?: ">="
                        val count = StoryMissionManager.getPlayerItemCount(player, targetItem)
                        when (op) {
                            ">" -> count > minCount
                            "<" -> count < minCount
                            "<=" -> count <= minCount
                            "==" -> count == minCount
                            else -> count >= minCount
                        }
                    }
                    "SPECIFIC_POKEMON_IN_PARTY" -> {
                        val targetSpecies = (trigNode.params["targetSpecies"] ?: "").trim()
                        if (targetSpecies.isNotBlank()) {
                            try {
                                val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
                                party.any { p ->
                                    StoryMissionManager.isSpeciesMatching(p.species.name, targetSpecies) ||
                                    StoryMissionManager.isSpeciesMatching(p.species.showdownId(), targetSpecies)
                                }
                            } catch (e: Exception) {
                                false
                            }
                        } else false
                    }
                    "POKEMON_FRIENDSHIP" -> {
                        val targetSpecies = (trigNode.params["targetSpecies"] ?: "").trim()
                        val minFriendship = trigNode.params["minFriendship"]?.toIntOrNull() ?: 220
                        try {
                            val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
                            party.any { p ->
                                (targetSpecies.isBlank() || StoryMissionManager.isSpeciesMatching(p.species.name, targetSpecies) || StoryMissionManager.isSpeciesMatching(p.species.showdownId(), targetSpecies)) &&
                                p.friendship >= minFriendship
                            }
                        } catch (e: Exception) {
                            false
                        }
                    }
                    "HIGHEST_POKEMON_LEVEL" -> {
                        val targetLevel = trigNode.params["targetLevel"]?.toIntOrNull() ?: 20
                        val op = trigNode.params["comparisonOp"] ?: ">="
                        try {
                            val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
                            val maxLvl = party.maxOfOrNull { it.level } ?: 0
                            when (op) {
                                ">" -> maxLvl > targetLevel
                                "<" -> maxLvl < targetLevel
                                "<=" -> maxLvl <= targetLevel
                                "==" -> maxLvl == targetLevel
                                else -> maxLvl >= targetLevel
                            }
                        } catch (e: Exception) {
                            false
                        }
                    }
                    "ENTER_STRUCTURE_OR_ZONE" -> {
                        val targetStructure = (trigNode.params["structureId"] ?: "").trim().lowercase()
                        if (targetStructure.isNotBlank()) {
                            try {
                                val level = player.serverLevel()
                                val pos = player.blockPosition()
                                val structureManager = level.structureManager()
                                val registry = level.registryAccess().registry(net.minecraft.core.registries.Registries.STRUCTURE).orElse(null)
                                if (registry != null) {
                                    registry.entrySet().any { entry ->
                                        val structKey = entry.key.location().toString()
                                        val structPath = entry.key.location().path
                                        val targetPath = targetStructure.substringAfter(":")
                                        val isTarget = structKey.equals(targetStructure, ignoreCase = true) || structPath.equals(targetPath, ignoreCase = true)
                                        if (isTarget) {
                                            val holder = registry.getHolder(entry.key).orElse(null)
                                            if (holder != null) {
                                                structureManager.getStructureWithPieceAt(pos, holder.value()).isValid
                                            } else false
                                        } else false
                                    }
                                } else false
                            } catch (e: Exception) {
                                false
                            }
                        } else false
                    }
                    "KARMA_CHECK" -> {
                        val targetKarma = trigNode.params["targetKarma"]?.toIntOrNull() ?: 0
                        val op = trigNode.params["comparisonOp"] ?: ">="
                        val karmaRoot = vito.cobblebrain.social.CobblebrainWorldSave.data.getAsJsonObject("karma")
                        val playerKey = player.uuid.toString()
                        val playerKarma = karmaRoot?.getAsJsonObject(playerKey)
                        val targetSpecies = (trigNode.params["targetSpecies"] ?: trigNode.params["species"] ?: "").trim()
                        val karmaVal: Int = if (playerKarma != null) {
                            if (targetSpecies.isNotBlank() && targetSpecies != "ALL" && targetSpecies != "GLOBAL") {
                                val entry = playerKarma.entrySet().find { StoryMissionManager.isSpeciesMatching(it.key, targetSpecies) }
                                entry?.value?.asInt ?: playerKarma.get(targetSpecies)?.asInt ?: 0
                            } else {
                                if (playerKarma.size() > 0) {
                                    playerKarma.entrySet().sumOf { it.value.asInt }
                                } else 0
                            }
                        } else 0
                        when (op) {
                            ">" -> karmaVal > targetKarma
                            "<" -> karmaVal < targetKarma
                            "<=" -> karmaVal <= targetKarma
                            "==" -> karmaVal == targetKarma
                            else -> karmaVal >= targetKarma
                        }
                    }
                    "VARIABLE_VALUE_CHECK" -> {
                        val varKey = trigNode.params["varKey"] ?: "var_1"
                        val op = trigNode.params["varOp"] ?: ">="
                        val targetVal = trigNode.params["varValue"] ?: "100"
                        val actualVal = instance.context.variables[varKey]
                        StoryExecutor.evaluateVariableCondition(actualVal, op, targetVal)
                    }
                    "PREVIOUS_MISSION_COMPLETED" -> {
                        val targetMission = trigNode.params["missionId"]?.trim() ?: ""
                        if (targetMission.isNotBlank()) {
                            val completed = StoryMissionManager.completedMissionIds[instance.storyId] ?: emptySet()
                            completed.contains(targetMission) || completed.any { it.equals(targetMission, ignoreCase = true) }
                        } else false
                    }
                    "PREVIOUS_EVENT_EXECUTED" -> {
                        val targetTag = trigNode.params["eventTag"]?.trim() ?: ""
                        if (targetTag.isNotBlank()) {
                            instance.context.executedEventTags.contains(targetTag)
                        } else false
                    }
                    else -> false
                }

                val isWaiting = instance.context.waitingTriggers.contains(trigNode.id)
                val triggerKey = "${instance.storyId}:${trigNode.id}"
                val wasTriggered = activeContinuousTriggerStates[triggerKey] ?: false

                val isIfNot = trigNode.params["triggerCondition"] == "IF_NOT"
                val finalResult = if (isIfNot) !shouldTrigger else shouldTrigger

                if (finalResult) {
                    if (isWaiting || !wasTriggered) {
                        if (!isWaiting) {
                            activeContinuousTriggerStates[triggerKey] = true
                        } else {
                            activeContinuousTriggerStates.remove(triggerKey)
                        }
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
        val data = mapOf("targetSpecies" to species)
        StoryMissionManager.onTriggerFired(player, "POKEMON_CATCH", data)
        dispatchReactiveTrigger(player, "POKEMON_CATCH", data)
    }

    fun onBattleVictory(player: ServerPlayer, targetSpecies: String = "") {
        val data = if (targetSpecies.isNotBlank()) mapOf("targetSpecies" to targetSpecies) else emptyMap()
        StoryMissionManager.onTriggerFired(player, "BATTLE_VICTORY", data)
        dispatchReactiveTrigger(player, "BATTLE_VICTORY", data)
    }

    @Suppress("unused")
    fun onBattleDefeat(player: ServerPlayer) {
        StoryMissionManager.onPlayerDeath(player)
        dispatchReactiveTrigger(player, "BATTLE_DEFEAT", emptyMap())
    }

    @Suppress("unused")
    fun onPokemonInteract(player: ServerPlayer, species: String) {
        val data = mapOf("targetSpecies" to species)
        StoryMissionManager.onTriggerFired(player, "INTERACT_POKEMON", data)
        StoryMissionManager.onTriggerFired(player, "TALK_TO_POKEMON", data)
        dispatchReactiveTrigger(player, "INTERACT_POKEMON", data)
        dispatchReactiveTrigger(player, "TALK_TO_POKEMON", data)
    }

    fun onEntityInteract(player: ServerPlayer, entity: Entity) {
        StoryMissionManager.onEntityTriggerFired(player, "INTERACT_ENTITY", entity)
        dispatchReactiveEntityTrigger(player, "INTERACT_ENTITY", entity)
        if (entity is PokemonEntity) {
            val species = entity.pokemon.species.name
            val data = mapOf("targetSpecies" to species)
            StoryMissionManager.onTriggerFired(player, "INTERACT_POKEMON", data)
            StoryMissionManager.onTriggerFired(player, "TALK_TO_POKEMON", data)
            dispatchReactiveTrigger(player, "INTERACT_POKEMON", data)
            dispatchReactiveTrigger(player, "TALK_TO_POKEMON", data)
        }
    }

    @Suppress("unused")
    fun onBlockInteract(player: ServerPlayer, blockId: String) {
        val data = mapOf("blockId" to blockId)
        StoryMissionManager.onTriggerFired(player, "BLOCK_INTERACTED", data)
        dispatchReactiveTrigger(player, "BLOCK_INTERACTED", data)
    }

    @Suppress("unused")
    fun onBlockPlaced(player: ServerPlayer, blockId: String) {
        val data = mapOf("blockId" to blockId)
        StoryMissionManager.onTriggerFired(player, "BLOCK_PLACED", data)
        dispatchReactiveTrigger(player, "BLOCK_PLACED", data)
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
                if (!StoryMissionManager.isSpeciesMatching(sName, targetSpecies) && !StoryMissionManager.isSpeciesMatching(sId, targetSpecies)) {
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
                val t = node.params["triggerType"]
                node.nodeType == NodeType.TRIGGER &&
                (t == triggerType ||
                 (triggerType == "ENTITY_DIED" && (t == "ENTITY_DIED" || t == "ENTITY_DEATH" || t == "ON_ENTITY_DIED")) ||
                 (triggerType == "ENTITY_DAMAGED" && (t == "ENTITY_DAMAGED" || t == "ON_ENTITY_DAMAGED")) ||
                 (triggerType in listOf("INTERACT_ENTITY", "INTERACT_POKEMON") && t in listOf("INTERACT_POKEMON", "INTERACT_ENTITY", "TALK_TO_POKEMON")) ||
                 (triggerType == "TALK_TO_POKEMON" && t in listOf("TALK_TO_POKEMON", "INTERACT_POKEMON", "INTERACT_ENTITY")) ||
                 (triggerType == "ENTITY_SPAWNED" && t == "ENTITY_SPAWNED"))
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
                val t = node.params["triggerType"]
                node.nodeType == NodeType.TRIGGER &&
                (t == triggerType ||
                 (triggerType == "POKEMON_CATCH" && t == "CATCH_POKEMON") ||
                 (triggerType == "BATTLE_VICTORY" && t == "DEFEAT_POKEMON") ||
                 (triggerType in listOf("INTERACT_POKEMON", "INTERACT_ENTITY") && t in listOf("INTERACT_POKEMON", "INTERACT_ENTITY", "TALK_TO_POKEMON")) ||
                 (triggerType == "TALK_TO_POKEMON" && t in listOf("TALK_TO_POKEMON", "INTERACT_POKEMON", "INTERACT_ENTITY")) ||
                 (triggerType == "BATTLE_START" && t == "BATTLE_START") ||
                 (triggerType == "PREVIOUS_MISSION_COMPLETED" && t == "PREVIOUS_MISSION_COMPLETED") ||
                 (triggerType == "PREVIOUS_EVENT_EXECUTED" && t == "PREVIOUS_EVENT_EXECUTED") ||
                 (triggerType == "AI_EVALUATION" && t == "AI_EVALUATION") ||
                 (triggerType == "STORY_ENDED" && t == "STORY_ENDED"))
            }

            for (node in matchingNodes) {
                var matches = true
                val targetSpecies = node.params["targetSpecies"]
                if (!targetSpecies.isNullOrBlank() && eventData.containsKey("targetSpecies")) {
                    val evSpecies = eventData["targetSpecies"] ?: ""
                    if (!StoryMissionManager.isSpeciesMatching(evSpecies, targetSpecies) && !evSpecies.contains(targetSpecies, ignoreCase = true)) {
                        matches = false
                    }
                }

                val targetBlock = node.params["blockId"]
                if (!targetBlock.isNullOrBlank() && eventData.containsKey("blockId")) {
                    val evBlock = eventData["blockId"] ?: ""
                    if (!StoryMissionManager.isItemMatching(evBlock, targetBlock) && !evBlock.contains(targetBlock, ignoreCase = true)) {
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

                val targetMission = node.params["missionId"]
                if (!targetMission.isNullOrBlank() && eventData.containsKey("missionId")) {
                    val evMission = eventData["missionId"] ?: ""
                    if (!evMission.equals(targetMission, ignoreCase = true)) {
                        matches = false
                    }
                }

                val targetEventTag = node.params["eventTag"]
                if (!targetEventTag.isNullOrBlank() && eventData.containsKey("eventTag")) {
                    val evTag = eventData["eventTag"] ?: ""
                    if (!evTag.equals(targetEventTag, ignoreCase = true)) {
                        matches = false
                    }
                }

                val targetIntent = node.params["aiIntent"]
                if (!targetIntent.isNullOrBlank() && eventData.containsKey("aiIntent")) {
                    val evIntent = eventData["aiIntent"] ?: ""
                    if (!evIntent.equals(targetIntent, ignoreCase = true)) {
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

    fun onBattleStart(player: ServerPlayer, targetSpecies: String = "") {
        val data = if (targetSpecies.isNotBlank()) mapOf("targetSpecies" to targetSpecies) else emptyMap()
        dispatchReactiveTrigger(player, "BATTLE_START", data)
    }

    fun onStoryEnded(player: ServerPlayer, storyId: String) {
        val data = mapOf("storyId" to storyId)
        dispatchReactiveTrigger(player, "STORY_ENDED", data)
    }

    fun onMissionCompleted(player: ServerPlayer?, missionId: String) {
        if (player == null) return
        val data = mapOf("missionId" to missionId)
        dispatchReactiveTrigger(player, "PREVIOUS_MISSION_COMPLETED", data)
    }

    fun onEventExecuted(player: ServerPlayer, eventTag: String) {
        val data = mapOf("eventTag" to eventTag)
        dispatchReactiveTrigger(player, "PREVIOUS_EVENT_EXECUTED", data)
    }

    fun onAIEvaluation(player: ServerPlayer, intent: String) {
        val data = mapOf("aiIntent" to intent)
        dispatchReactiveTrigger(player, "AI_EVALUATION", data)
    }

    fun onCheckpointLoaded(player: ServerPlayer, profileId: String, firstJoinOnly: Boolean = false) {
        val eventData = mapOf(
            "slotId" to profileId,
            "profileId" to profileId,
            "firstJoinOnly" to firstJoinOnly.toString()
        )
        dispatchReactiveTrigger(player, "ON_CHECKPOINT_LOADED", eventData)
    }

    fun clearStatesForStory(storyId: String) {
        activeContinuousTriggerStates.keys.removeIf { it.startsWith("$storyId:") }
        triggerArmTimes.keys.removeIf { it.startsWith("$storyId:") }
        lastPlayerItemCounts.keys.removeIf { it.startsWith("$storyId:") }
    }
}
