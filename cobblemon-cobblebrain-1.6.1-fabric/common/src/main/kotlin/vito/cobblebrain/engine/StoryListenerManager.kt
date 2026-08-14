package vito.cobblebrain.engine

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import vito.cobblebrain.model.NodeType

object StoryListenerManager {

    /**
     * Chamado periodicamente (ex: a cada 10 ou 20 ticks) para checar gatilhos contínuos
     * como tempo decorrido, coordenadas do jogador, bioma, dia/noite, clima, etc.
     */
    fun onServerTick() {
        val activeList = StoryExecutor.activeStories.values.toList()
        for (instance in activeList) {
            val player = instance.context.player ?: continue
            val scene = instance.project.getActiveScene() ?: continue

            // Verificar gatilhos passivos/reativos que não dependem de sinal de entrada
            val reactiveTriggers = scene.nodes.filter { node ->
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
                    else -> false
                }

                if (shouldTrigger) {
                    val isIfNot = trigNode.params["triggerCondition"] == "IF_NOT"
                    val finalResult = if (isIfNot) !shouldTrigger else shouldTrigger
                    if (finalResult) {
                        StoryExecutor.executeNodeChain(instance, trigNode, targetPortId = null)
                    }
                }
            }
        }
    }

    fun onPokemonCatch(player: ServerPlayer, species: String) {
        dispatchReactiveTrigger(player, "POKEMON_CATCH", mapOf("targetSpecies" to species))
    }

    fun onBattleVictory(player: ServerPlayer, targetSpecies: String = "") {
        dispatchReactiveTrigger(player, "BATTLE_VICTORY", mapOf("targetSpecies" to targetSpecies))
    }

    fun onBattleDefeat(player: ServerPlayer) {
        dispatchReactiveTrigger(player, "BATTLE_DEFEAT", emptyMap())
    }

    fun onPokemonInteract(player: ServerPlayer, species: String) {
        dispatchReactiveTrigger(player, "INTERACT_POKEMON", mapOf("targetSpecies" to species))
    }

    fun onBlockInteract(player: ServerPlayer, blockId: String) {
        dispatchReactiveTrigger(player, "BLOCK_INTERACTED", mapOf("blockId" to blockId))
    }

    fun onBlockPlaced(player: ServerPlayer, blockId: String) {
        dispatchReactiveTrigger(player, "BLOCK_PLACED", mapOf("blockId" to blockId))
    }

    fun onEntityDied(entity: Entity, killer: Entity?) {
        val player = killer as? ServerPlayer ?: return
        val entityId = entity.type.toString()
        dispatchReactiveTrigger(player, "ENTITY_DIED", mapOf("entityType" to entityId))
    }

    private fun dispatchReactiveTrigger(player: ServerPlayer, triggerType: String, eventData: Map<String, String>) {
        val activeList = StoryExecutor.activeStories.values.filter { it.context.player?.uuid == player.uuid }
        for (instance in activeList) {
            val scene = instance.project.getActiveScene() ?: continue
            val matchingNodes = scene.nodes.filter { node ->
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

                if (matches) {
                    val isIfNot = node.params["triggerCondition"] == "IF_NOT"
                    val finalResult = if (isIfNot) !matches else matches
                    if (finalResult) {
                        StoryExecutor.executeNodeChain(instance, node, targetPortId = null)
                    }
                }
            }
        }
    }
}
