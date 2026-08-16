package vito.cobblebrain.engine

import net.minecraft.server.level.ServerPlayer
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

    fun startMission(instance: ActiveStoryInstance, node: NodeData) {
        val title = node.params["questTitle"]?.ifBlank { node.title } ?: node.title
        val trigger = node.params["questTrigger"] ?: "POKEMON_CATCH"
        val targetCount = node.params["targetCount"]?.toIntOrNull() ?: 1
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
    }

    fun onTriggerFired(player: ServerPlayer?, triggerId: String) {
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

            if (mission.questTrigger == triggerId) {
                mission.currentProgress += 1

                if (mission.currentProgress >= mission.targetCount) {
                    mission.isCompleted = true
                    fireQuestOutput(mission, "SUCCESS_OUT")
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
        fireQuestOutput(mission, "FAIL_OUT")
    }

    private fun fireQuestOutput(mission: ActiveMission, portIdOrName: String) {
        val outputPort = mission.questNode.outputs.find { it.id == portIdOrName || it.name.equals(portIdOrName, true) || it.name.contains(portIdOrName, true) }
            ?: mission.questNode.outputs.firstOrNull() ?: return

        val scene = mission.instance.project.scenes.find { it.nodes.any { n -> n.id == mission.questNode.id } }
        val conns = scene?.connections ?: mission.instance.project.sceneConnections
        val activeConns = conns.filter { it.fromNodeId == mission.questNode.id && it.fromPortId == outputPort.id }

        activeConns.forEach { conn ->
            val targetNode = scene?.nodes?.find { it.id == conn.toNodeId }
            if (targetNode != null) {
                StoryExecutor.executeNodeChain(mission.instance, targetNode, targetPortId = conn.toPortId)
            }
        }
    }
}
