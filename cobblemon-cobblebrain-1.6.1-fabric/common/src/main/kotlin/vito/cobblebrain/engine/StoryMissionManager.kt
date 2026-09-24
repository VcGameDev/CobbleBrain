package vito.cobblebrain.engine

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import vito.cobblebrain.model.NodeData

data class ActiveMission(
    val instance: ActiveStoryInstance,
    val questNode: NodeData,
    val questTitle: String,
    val questTrigger: String,
    val targetCount: Int,
    val timeLimitSec: Int,
    val failOnDeath: Boolean,
    val showHud: Boolean,
    var currentProgress: Int = 0,
    var isCompleted: Boolean = false,
    var isFailed: Boolean = false,
    val startTimeMs: Long = System.currentTimeMillis()
)

object StoryMissionManager {
    val activeMissions = mutableListOf<ActiveMission>()
    val completedMissionIds = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()
    private var tickCounter: Long = 0L

    fun markMissionCompleted(mission: ActiveMission, player: ServerPlayer?) {
        mission.isCompleted = true
        completedMissionIds.computeIfAbsent(mission.instance.storyId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.addAll(listOf(mission.questNode.id, mission.questTitle))
        syncMissions(player)
        fireQuestOutput(mission, "SUCCESS_OUT")
        StoryListenerManager.onMissionCompleted(player, mission.questNode.id)
    }

    fun isItemMatching(itemKey: String, targetItem: String): Boolean {
        if (itemKey.isBlank() || targetItem.isBlank()) return false
        val k1 = itemKey.trim().lowercase()
        val k2 = targetItem.trim().lowercase()
        if (k1 == k2) return true

        val path1 = if (k1.contains(":")) k1.substringAfter(":") else k1
        val path2 = if (k2.contains(":")) k2.substringAfter(":") else k2

        val ns1 = if (k1.contains(":")) k1.substringBefore(":") else ""
        val ns2 = if (k2.contains(":")) k2.substringBefore(":") else ""

        if (ns1.isNotBlank() && ns2.isNotBlank()) {
            return ns1 == ns2 && path1 == path2
        }

        return path1 == path2
    }

    fun isSpeciesMatching(species: String, targetSpecies: String): Boolean {
        if (species.isBlank() || targetSpecies.isBlank()) return false
        val s1 = species.trim().lowercase()
        val s2 = targetSpecies.trim().lowercase()
        if (s1 == s2) return true

        val path1 = if (s1.contains(":")) s1.substringAfter(":") else s1
        val path2 = if (s2.contains(":")) s2.substringAfter(":") else s2
        if (path1 == path2) return true

        val clean1 = path1.replace("_", "").replace("-", "")
        val clean2 = path2.replace("_", "").replace("-", "")
        return clean1 == clean2
    }

    fun getPlayerItemCount(player: ServerPlayer, targetItem: String): Int {
        if (targetItem.isBlank()) return 0
        var total = 0
        val allStacks = player.inventory.items + player.inventory.offhand + player.inventory.armor
        for (stack in allStacks) {
            if (!stack.isEmpty) {
                val key = BuiltInRegistries.ITEM.getKey(stack.item).toString()
                if (isItemMatching(key, targetItem)) {
                    total += stack.count
                }
            }
        }
        return total
    }

    fun isPlayerHoldingItem(player: ServerPlayer, targetItem: String): Boolean {
        if (targetItem.isBlank()) return false
        val hands = listOf(player.mainHandItem, player.offhandItem)
        for (stack in hands) {
            if (!stack.isEmpty) {
                val key = BuiltInRegistries.ITEM.getKey(stack.item).toString()
                if (isItemMatching(key, targetItem)) {
                    return true
                }
            }
        }
        return false
    }

    fun syncMissions(player: ServerPlayer?) {
        if (player == null) return
        vito.cobblebrain.social.DialogueSystem.syncQuests?.invoke(player)
    }

    fun getActiveMissionsJson(player: ServerPlayer): List<com.google.gson.JsonObject> {
        val list = mutableListOf<com.google.gson.JsonObject>()
        val snapshot = activeMissions.toList()
        for (mission in snapshot) {
            if (mission.isCompleted || mission.isFailed) continue
            if (!mission.showHud) continue
            if (mission.instance.context.player?.uuid != player.uuid) continue

            val obj = com.google.gson.JsonObject()
            obj.addProperty("id", "story_${mission.instance.storyId}_${mission.questNode.id}")
            obj.addProperty("type", "STORY_MISSION")
            obj.addProperty("title", mission.questTitle.ifBlank { "Mission" })
            obj.addProperty("status", "IN_PROGRESS")
            obj.addProperty("targetCount", mission.targetCount)
            obj.addProperty("currentProgress", mission.currentProgress)

            val ratio = if (mission.targetCount > 0) {
                (mission.currentProgress.toFloat() / mission.targetCount.toFloat()).coerceIn(0f, 1f)
            } else 0f
            obj.addProperty("progress", ratio)

            val triggerLabel = when (mission.questTrigger) {
                "POKEMON_CATCH" -> {
                    val sp = mission.questNode.params["targetSpecies"]?.trim()
                    if (!sp.isNullOrBlank()) "Catch $sp: ${mission.currentProgress}/${mission.targetCount}"
                    else "Catch Pokémon: ${mission.currentProgress}/${mission.targetCount}"
                }
                "PLAYER_HELD_ITEM" -> {
                    val itm = (mission.questNode.params["targetItem"] ?: mission.questNode.params["heldItemId"] ?: "item").substringAfter(":")
                    "Hold $itm"
                }
                "PLAYER_INVENTORY_HAS_ITEM", "ITEM_IN_INVENTORY" -> {
                    val itm = (mission.questNode.params["targetItem"] ?: mission.questNode.params["requiredItem"] ?: "item").substringAfter(":")
                    "Collect $itm: ${mission.currentProgress}/${mission.targetCount}"
                }
                "PLAYER_ITEM_COUNT" -> {
                    val itm = (mission.questNode.params["targetItem"] ?: mission.questNode.params["checkItemId"] ?: "item").substringAfter(":")
                    "Obtain $itm: ${mission.currentProgress}/${mission.targetCount}"
                }
                "BATTLE_VICTORY" -> {
                    val sp = mission.questNode.params["targetSpecies"]?.trim()
                    if (!sp.isNullOrBlank()) "Defeat $sp: ${mission.currentProgress}/${mission.targetCount}"
                    else "Win Battles: ${mission.currentProgress}/${mission.targetCount}"
                }
                "INTERACT_POKEMON", "TALK_TO_POKEMON" -> {
                    val sp = mission.questNode.params["targetSpecies"]?.trim()
                    if (!sp.isNullOrBlank()) "Interact with $sp: ${mission.currentProgress}/${mission.targetCount}"
                    else "Interact: ${mission.currentProgress}/${mission.targetCount}"
                }
                "PLAYER_LEVEL" -> "Reach Level ${mission.targetCount}: ${mission.currentProgress}/${mission.targetCount}"
                "PLAYER_COORDINATES", "LOCATION" -> "Reach Target Location"
                else -> "${mission.currentProgress} / ${mission.targetCount}"
            }
            obj.addProperty("progressText", triggerLabel)
            obj.addProperty("timeLimitSec", mission.timeLimitSec)
            obj.addProperty("startTimeMs", mission.startTimeMs)
            obj.addProperty("questTrigger", mission.questTrigger)
            list.add(obj)
        }
        return list
    }

    fun evaluateMissionProgress(mission: ActiveMission) {
        if (mission.isCompleted || mission.isFailed) return
        val player = mission.instance.context.player ?: return

        val targetItem = (mission.questNode.params["targetItem"]
            ?: mission.questNode.params["requiredItem"]
            ?: mission.questNode.params["heldItemId"]
            ?: mission.questNode.params["checkItemId"]
            ?: mission.questNode.params["itemId"]
            ?: "").trim()

        when (mission.questTrigger) {
            "PLAYER_HELD_ITEM" -> {
                if (targetItem.isNotBlank() && isPlayerHoldingItem(player, targetItem)) {
                    mission.currentProgress = mission.targetCount
                    markMissionCompleted(mission, player)
                }
            }

            "PLAYER_INVENTORY_HAS_ITEM", "ITEM_IN_INVENTORY" -> {
                if (targetItem.isNotBlank()) {
                    val count = getPlayerItemCount(player, targetItem)
                    val prevProgress = mission.currentProgress
                    mission.currentProgress = count.coerceAtMost(mission.targetCount)
                    if (mission.currentProgress != prevProgress && mission.currentProgress < mission.targetCount) {
                        syncMissions(player)
                        fireQuestOutput(mission, "PROGRESS_OUT")
                    }
                    if (count >= mission.targetCount) {
                        markMissionCompleted(mission, player)
                    }
                }
            }

            "PLAYER_ITEM_COUNT" -> {
                if (targetItem.isNotBlank()) {
                    val count = getPlayerItemCount(player, targetItem)
                    val prevProgress = mission.currentProgress
                    mission.currentProgress = count.coerceAtMost(mission.targetCount)
                    if (mission.currentProgress != prevProgress && mission.currentProgress < mission.targetCount) {
                        syncMissions(player)
                        fireQuestOutput(mission, "PROGRESS_OUT")
                    }
                    if (count >= mission.targetCount) {
                        markMissionCompleted(mission, player)
                    }
                }
            }

            "PLAYER_COORDINATES", "LOCATION" -> {
                val tx: Double
                val ty: Double
                val tz: Double
                val coordsStr = mission.questNode.params["coordinates"]
                if (!coordsStr.isNullOrBlank()) {
                    val vec = CoordinateResolver.resolveVec3(coordsStr, player, player.server)
                    tx = vec.x
                    ty = vec.y
                    tz = vec.z
                } else {
                    tx = mission.questNode.params["targetX"]?.toDoubleOrNull() ?: 0.0
                    ty = mission.questNode.params["targetY"]?.toDoubleOrNull() ?: 64.0
                    tz = mission.questNode.params["targetZ"]?.toDoubleOrNull() ?: 0.0
                }
                val radius = mission.questNode.params["radius"]?.toDoubleOrNull() ?: 5.0
                val dx = player.x - tx
                val dy = player.y - ty
                val dz = player.z - tz
                if ((dx * dx + dy * dy + dz * dz) <= radius * radius) {
                    mission.currentProgress = mission.targetCount
                    markMissionCompleted(mission, player)
                }
            }

            "PLAYER_LEVEL" -> {
                val minLvl = mission.questNode.params["minLevel"]?.toIntOrNull() ?: mission.targetCount
                val curLvl = player.experienceLevel
                mission.currentProgress = curLvl.coerceAtMost(mission.targetCount)
                if (curLvl >= minLvl) {
                    markMissionCompleted(mission, player)
                }
            }

            "SPECIFIC_POKEMON_IN_PARTY" -> {
                val targetSpecies = mission.questNode.params["targetSpecies"]?.trim() ?: ""
                if (targetSpecies.isNotBlank()) {
                    val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
                    val hasPokemon = party.any { p ->
                        isSpeciesMatching(p.species.name, targetSpecies) ||
                        isSpeciesMatching(p.species.showdownId(), targetSpecies)
                    }
                    if (hasPokemon) {
                        mission.currentProgress = mission.targetCount
                        markMissionCompleted(mission, player)
                    }
                }
            }

            "POKEMON_FRIENDSHIP" -> {
                val targetSpecies = mission.questNode.params["targetSpecies"]?.trim() ?: ""
                val minFriendship = mission.questNode.params["minFriendship"]?.toIntOrNull() ?: 220
                val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
                val match = party.any { p ->
                    (targetSpecies.isBlank() || isSpeciesMatching(p.species.name, targetSpecies) || isSpeciesMatching(p.species.showdownId(), targetSpecies)) &&
                    p.friendship >= minFriendship
                }
                if (match) {
                    mission.currentProgress = mission.targetCount
                    markMissionCompleted(mission, player)
                }
            }

            "HIGHEST_POKEMON_LEVEL" -> {
                val targetLevel = mission.questNode.params["targetLevel"]?.toIntOrNull() ?: mission.targetCount
                val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
                val maxLvl = party.maxOfOrNull { it.level } ?: 0
                mission.currentProgress = maxLvl.coerceAtMost(mission.targetCount)
                if (maxLvl >= targetLevel) {
                    markMissionCompleted(mission, player)
                }
            }
        }
    }

    fun onServerTick() {
        tickCounter++
        val snapshot = activeMissions.toList()
        if (snapshot.isEmpty()) return

        val checkInterval = (tickCounter % 10L == 0L)

        for (mission in snapshot) {
            if (mission.isCompleted || mission.isFailed) continue
            val player = mission.instance.context.player ?: continue

            if (mission.timeLimitSec > 0 && checkInterval) {
                val elapsedSec = (System.currentTimeMillis() - mission.startTimeMs) / 1000
                if (elapsedSec > mission.timeLimitSec) {
                    failMission(mission, "Time Limit Reached")
                    continue
                }
            }

            if (checkInterval) {
                evaluateMissionProgress(mission)
            }
        }
    }

    fun startMission(instance: ActiveStoryInstance, node: NodeData) {
        val title = node.params["questTitle"]?.ifBlank { node.title } ?: node.title
        val rawTrigger = node.params["questTrigger"] ?: "POKEMON_CATCH"
        val trigger = vito.cobblebrain.model.TriggerRegistry.find(rawTrigger).id
        val targetCount = maxOf(1, node.params["targetCount"]?.toIntOrNull() ?: node.params["requiredCount"]?.toIntOrNull() ?: node.params["minCount"]?.toIntOrNull() ?: 1)
        val timeLimitSec = node.params["timeLimitSec"]?.toIntOrNull() ?: 0
        val failOnDeath = node.params["failOnDeath"] == "true"
        val showHud = node.params["showHud"] != "false"

        // Cancel existing mission instance for same node if any
        activeMissions.removeAll { it.instance.storyId == instance.storyId && it.questNode.id == node.id }

        val mission = ActiveMission(
            instance = instance,
            questNode = node,
            questTitle = title,
            questTrigger = trigger,
            targetCount = targetCount,
            timeLimitSec = timeLimitSec,
            failOnDeath = failOnDeath,
            showHud = showHud
        )
        activeMissions.add(mission)
        syncMissions(instance.context.player)

        evaluateMissionProgress(mission)
    }

    fun onTriggerFired(player: ServerPlayer?, triggerId: String, eventData: Map<String, String> = emptyMap()) {
        val snapshot = activeMissions.toList()
        val normalizedIncoming = vito.cobblebrain.model.TriggerRegistry.find(triggerId).id

        for (mission in snapshot) {
            if (mission.isCompleted || mission.isFailed) continue
            if (mission.instance.context.player?.uuid != player?.uuid && player != null) continue

            // Time limit check
            if (mission.timeLimitSec > 0) {
                val elapsedSec = (System.currentTimeMillis() - mission.startTimeMs) / 1000
                if (elapsedSec > mission.timeLimitSec) {
                    failMission(mission, "Time Limit Reached")
                    continue
                }
            }

            val missionNormalized = vito.cobblebrain.model.TriggerRegistry.find(mission.questTrigger).id
            val isMatch = mission.questTrigger == triggerId || missionNormalized == normalizedIncoming

            if (isMatch) {
                if (normalizedIncoming == "POKEMON_CATCH") {
                    val reqSpecies = mission.questNode.params["targetSpecies"]?.trim()
                    if (!reqSpecies.isNullOrBlank()) {
                        val caughtSpecies = eventData["targetSpecies"]?.trim() ?: ""
                        if (!isSpeciesMatching(caughtSpecies, reqSpecies)) {
                            continue
                        }
                    }
                } else if (normalizedIncoming == "BATTLE_VICTORY") {
                    val reqSpecies = mission.questNode.params["targetSpecies"]?.trim()
                    if (!reqSpecies.isNullOrBlank()) {
                        val defeatedSpecies = eventData["targetSpecies"]?.trim() ?: ""
                        if (defeatedSpecies.isNotBlank() && !isSpeciesMatching(defeatedSpecies, reqSpecies)) {
                            continue
                        }
                    }
                } else if (normalizedIncoming in listOf("BLOCK_INTERACTED", "BLOCK_PLACED")) {
                    val reqBlock = mission.questNode.params["blockId"]?.trim()
                    if (!reqBlock.isNullOrBlank()) {
                        val blockId = eventData["blockId"]?.trim() ?: ""
                        if (blockId.isNotBlank() && !isItemMatching(blockId, reqBlock)) {
                            continue
                        }
                    }
                }

                mission.currentProgress += 1
                syncMissions(mission.instance.context.player)

                if (mission.currentProgress >= mission.targetCount) {
                    markMissionCompleted(mission, mission.instance.context.player)
                } else {
                    fireQuestOutput(mission, "PROGRESS_OUT")
                }
            }
        }
    }

    fun onEntityTriggerFired(player: ServerPlayer?, triggerId: String, entity: Entity?) {
        val snapshot = activeMissions.toList()
        for (mission in snapshot) {
            if (mission.isCompleted || mission.isFailed) continue
            if (mission.instance.context.player?.uuid != player?.uuid && player != null) continue

            // Time limit check
            if (mission.timeLimitSec > 0) {
                val elapsedSec = (System.currentTimeMillis() - mission.startTimeMs) / 1000
                if (elapsedSec > mission.timeLimitSec) {
                    failMission(mission, "Time Limit Reached")
                    continue
                }
            }

            val trigMatches = mission.questTrigger == triggerId ||
                    (triggerId == "INTERACT_ENTITY" && mission.questTrigger == "INTERACT_POKEMON") ||
                    (triggerId == "INTERACT_POKEMON" && mission.questTrigger == "INTERACT_ENTITY") ||
                    (triggerId == "INTERACT_ENTITY" && mission.questTrigger == "TALK_TO_POKEMON")

            if (trigMatches) {
                if (entity != null) {
                    val reqTag = mission.questNode.params["requiredStoryTag"]?.trim()
                    if (!reqTag.isNullOrBlank() && !entity.tags.contains(reqTag)) {
                        continue
                    }
                    if (entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                        val reqSpecies = mission.questNode.params["targetSpecies"]?.trim()
                        if (!reqSpecies.isNullOrBlank()) {
                            if (!isSpeciesMatching(entity.pokemon.species.name, reqSpecies) &&
                                !isSpeciesMatching(entity.pokemon.species.showdownId(), reqSpecies)) {
                                continue
                            }
                        }
                    }
                }

                mission.currentProgress += 1
                syncMissions(mission.instance.context.player)

                if (mission.currentProgress >= mission.targetCount) {
                    markMissionCompleted(mission, mission.instance.context.player)
                } else {
                    fireQuestOutput(mission, "PROGRESS_OUT")
                }
            }
        }
    }

    fun onPlayerDeath(player: ServerPlayer) {
        val snapshot = activeMissions.toList()
        for (mission in snapshot) {
            if (mission.isCompleted || mission.isFailed) continue
            if (mission.instance.context.player?.uuid == player.uuid && mission.failOnDeath) {
                failMission(mission, "Player Died")
            }
        }
    }

    private fun failMission(mission: ActiveMission, reason: String) {
        mission.isFailed = true
        syncMissions(mission.instance.context.player)
        fireQuestOutput(mission, "FAIL_OUT")
    }

    private fun fireQuestOutput(mission: ActiveMission, portIdOrName: String) {
        val outputPort = mission.questNode.outputs.find {
            it.id == portIdOrName || it.name.equals(portIdOrName, true) || it.name.contains(portIdOrName, true)
        }
        val portId = outputPort?.id ?: portIdOrName
        StoryExecutor.continuePortConnections(mission.instance, mission.questNode, portId, 0)
    }

    fun clearMissionsForStory(storyId: String) {
        val toRemove = activeMissions.filter { it.instance.storyId == storyId }
        activeMissions.removeAll(toRemove)
        toRemove.mapNotNull { it.instance.context.player }.distinctBy { it.uuid }.forEach { player ->
            syncMissions(player)
        }
    }

    fun getQuestProgressSnapshot(player: ServerPlayer?): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        activeMissions.forEach { m ->
            if (player == null || m.instance.context.player?.uuid == player.uuid) {
                result[m.questNode.id] = m.currentProgress
            }
        }
        return result
    }

    fun restoreQuestProgress(player: ServerPlayer, progressMap: Map<String, Int>) {
        activeMissions.forEach { m ->
            if (m.instance.context.player?.uuid == player.uuid && progressMap.containsKey(m.questNode.id)) {
                m.currentProgress = progressMap[m.questNode.id] ?: m.currentProgress
            }
        }
        syncMissions(player)
    }
}
