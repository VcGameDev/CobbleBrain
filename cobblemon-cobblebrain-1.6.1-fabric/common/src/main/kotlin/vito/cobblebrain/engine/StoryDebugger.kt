package vito.cobblebrain.engine

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import vito.cobblebrain.model.NodeType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

enum class NodeExecutionStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
    FALLBACK_TRIGGERED
}

data class StoryDebugLogEntry(
    val timestamp: Long,
    val storyId: String,
    val blockId: String,
    val blockType: NodeType,
    val status: NodeExecutionStatus,
    val level: String, // "INFO", "WARN", "ERROR"
    val message: String,
    val details: String? = null
) {
    fun getLogBadge(): String {
        return when {
            level.equals("ERROR", true) || status == NodeExecutionStatus.FAILED -> "ERROR"
            level.equals("WARN", true) || status == NodeExecutionStatus.FALLBACK_TRIGGERED -> "WARN"
            blockType == NodeType.VARIABLE_SET || message.contains("Variable", ignoreCase = true) || message.contains("SetVar", ignoreCase = true) -> "SET_VAR"
            blockType == NodeType.DIALOGUE && message.contains("AI", ignoreCase = true) -> "AI_CALL"
            blockType == NodeType.ACTION || blockType == NodeType.TEXTURE || blockType == NodeType.AUDIO -> "ACTION"
            status == NodeExecutionStatus.FALLBACK_TRIGGERED -> "FALLBACK"
            else -> "INFO"
        }
    }

    fun getLogBadgeColor(): Int {
        return when (getLogBadge()) {
            "ERROR" -> 0xFFEF4444.toInt()
            "WARN" -> 0xFFF97316.toInt()
            "FALLBACK" -> 0xFFEAB308.toInt()
            "SET_VAR" -> 0xFFF59E0B.toInt()
            "AI_CALL" -> 0xFFA855F7.toInt()
            "ACTION" -> 0xFF10B981.toInt()
            else -> 0xFF0EA5E9.toInt()
        }
    }
}

data class StoryActiveSessionState(
    val storyId: String = "",
    val packName: String = "",
    val sceneName: String = "",
    val activeNodeId: String = "",
    val activeNodeType: String = "",
    val targetEntityName: String = "",
    val targetEntityTag: String = "",
    val targetEntitySlot: String = "",
    val targetEntityId: String = "",
    val variables: Map<String, String> = emptyMap(),
    val lastUpdatedVarKey: String? = null,
    val lastVarUpdateTime: Long = 0L,
    val isActive: Boolean = false
)

object StoryDebugger {
    /** In-memory ring buffer of recent logs (capped at 500 items) */
    val logs = CopyOnWriteArrayList<StoryDebugLogEntry>()

    /** Active execution status keyed by "storyId/blockId" */
    val nodeStatuses = ConcurrentHashMap<String, NodeExecutionStatus>()

    /** Active error/diagnostic message keyed by "storyId/blockId" */
    val nodeErrorMessages = ConcurrentHashMap<String, String>()

    /** Timestamp of last status change keyed by "storyId/blockId" */
    val nodeStatusTimestamps = ConcurrentHashMap<String, Long>()

    /** Live active session overview and variables */
    var activeSessionState: StoryActiveSessionState = StoryActiveSessionState()

    /** Networking bridge for S2C debug syncing */
    var sendDebugSync: ((ServerPlayer, vito.cobblebrain.network.CobblebrainPayloads.StoryDebugSyncPayload) -> Unit)? = null
    var sendSessionStateSync: ((ServerPlayer, vito.cobblebrain.network.CobblebrainPayloads.StorySessionStateSyncPayload) -> Unit)? = null

    fun hasActiveSession(): Boolean {
        return activeSessionState.isActive || activeSessionState.storyId.isNotBlank()
    }

    fun recordLog(
        storyId: String,
        blockId: String,
        blockType: NodeType,
        status: NodeExecutionStatus,
        level: String,
        message: String,
        details: String? = null,
        server: MinecraftServer? = null
    ) {
        val safeStoryId = storyId.ifBlank { "default_story" }
        val entry = StoryDebugLogEntry(
            timestamp = System.currentTimeMillis(),
            storyId = safeStoryId,
            blockId = blockId,
            blockType = blockType,
            status = status,
            level = level,
            message = message,
            details = details
        )

        logs.add(0, entry)
        while (logs.size > 500) {
            logs.removeAt(logs.size - 1)
        }

        val key = makeKey(safeStoryId, blockId)
        nodeStatuses[key] = status
        nodeStatusTimestamps[key] = entry.timestamp

        if (level.equals("ERROR", ignoreCase = true) || status == NodeExecutionStatus.FAILED) {
            nodeErrorMessages[key] = message
        } else if (level.equals("WARN", ignoreCase = true) || status == NodeExecutionStatus.FALLBACK_TRIGGERED) {
            nodeErrorMessages[key] = message
        } else if (status == NodeExecutionStatus.SUCCESS) {
            nodeErrorMessages.remove(key)
        }

        if (server != null) {
            val payload = vito.cobblebrain.network.CobblebrainPayloads.StoryDebugSyncPayload(
                timestamp = entry.timestamp,
                storyId = safeStoryId,
                blockId = blockId,
                blockType = blockType.name,
                status = status.name,
                level = level,
                message = message,
                details = details ?: ""
            )
            server.playerList.players.forEach { p ->
                sendDebugSync?.invoke(p, payload)
            }
        }
    }

    fun makeKey(storyId: String, blockId: String): String {
        return "${storyId.trim().lowercase()}/$blockId"
    }

    fun getNodeStatus(storyId: String, blockId: String): NodeExecutionStatus {
        val key = makeKey(storyId, blockId)
        val status = nodeStatuses[key] ?: NodeExecutionStatus.IDLE

        // Auto-fade SUCCESS state after 3.5 seconds
        if (status == NodeExecutionStatus.SUCCESS) {
            val ts = nodeStatusTimestamps[key] ?: 0L
            if (System.currentTimeMillis() - ts > 3500L) {
                nodeStatuses[key] = NodeExecutionStatus.IDLE
                return NodeExecutionStatus.IDLE
            }
        }
        return status
    }

    fun getNodeErrorMessage(storyId: String, blockId: String): String? {
        val key = makeKey(storyId, blockId)
        return nodeErrorMessages[key]
    }

    fun dismissNodeError(storyId: String, blockId: String) {
        val key = makeKey(storyId, blockId)
        nodeStatuses[key] = NodeExecutionStatus.IDLE
        nodeErrorMessages.remove(key)
    }

    fun clearLogs(storyId: String? = null) {
        if (storyId.isNullOrBlank()) {
            logs.clear()
            nodeStatuses.clear()
            nodeErrorMessages.clear()
            nodeStatusTimestamps.clear()
        } else {
            val safeId = storyId.trim().lowercase()
            logs.removeIf { it.storyId.equals(safeId, ignoreCase = true) }
            val prefix = "$safeId/"
            nodeStatuses.keys.removeIf { it.startsWith(prefix) }
            nodeErrorMessages.keys.removeIf { it.startsWith(prefix) }
            nodeStatusTimestamps.keys.removeIf { it.startsWith(prefix) }
        }
    }

    fun getErrorCount(storyId: String? = null): Int {
        return if (storyId.isNullOrBlank()) {
            logs.count { it.level.equals("ERROR", ignoreCase = true) || it.status == NodeExecutionStatus.FAILED }
        } else {
            val safeId = storyId.trim().lowercase()
            logs.count { it.storyId.equals(safeId, ignoreCase = true) && (it.level.equals("ERROR", ignoreCase = true) || it.status == NodeExecutionStatus.FAILED) }
        }
    }

    fun getWarningCount(storyId: String? = null): Int {
        return if (storyId.isNullOrBlank()) {
            logs.count { it.level.equals("WARN", ignoreCase = true) || it.status == NodeExecutionStatus.FALLBACK_TRIGGERED }
        } else {
            val safeId = storyId.trim().lowercase()
            logs.count { it.storyId.equals(safeId, ignoreCase = true) && (it.level.equals("WARN", ignoreCase = true) || it.status == NodeExecutionStatus.FALLBACK_TRIGGERED) }
        }
    }

    fun updateSessionStateFromPayload(payload: vito.cobblebrain.network.CobblebrainPayloads.StorySessionStateSyncPayload) {
        val varsMap: Map<String, String> = try {
            if (payload.variablesJson.isNotBlank()) {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                val parsed: Map<String, Any?> = com.google.gson.Gson().fromJson(payload.variablesJson, type) ?: emptyMap()
                parsed.mapValues { it.value?.toString() ?: "null" }
            } else emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }

        activeSessionState = StoryActiveSessionState(
            storyId = payload.storyId,
            packName = payload.packName,
            sceneName = payload.sceneName,
            activeNodeId = payload.activeNodeId,
            activeNodeType = payload.activeNodeType,
            targetEntityName = payload.targetEntityName,
            targetEntityTag = payload.targetEntityTag,
            targetEntitySlot = payload.targetEntitySlot,
            targetEntityId = payload.targetEntityId,
            variables = varsMap,
            lastUpdatedVarKey = payload.lastUpdatedVarKey.ifBlank { null },
            lastVarUpdateTime = if (payload.lastUpdatedVarKey.isNotBlank()) System.currentTimeMillis() else activeSessionState.lastVarUpdateTime,
            isActive = payload.isActive
        )
    }

    fun broadcastSessionState(
        server: MinecraftServer?,
        storyId: String,
        packName: String,
        sceneName: String,
        activeNodeId: String,
        activeNodeType: String,
        targetEntityName: String,
        targetEntityTag: String,
        targetEntitySlot: String,
        targetEntityId: String,
        variables: Map<String, Any?>,
        updatedVarKey: String? = null,
        isActive: Boolean = true
    ) {
        val safeStoryId = storyId.ifBlank { "default_story" }
        val varsMap = variables.mapValues { it.value?.toString() ?: "null" }
        val varsJson = com.google.gson.Gson().toJson(variables)

        activeSessionState = StoryActiveSessionState(
            storyId = safeStoryId,
            packName = packName,
            sceneName = sceneName,
            activeNodeId = activeNodeId,
            activeNodeType = activeNodeType,
            targetEntityName = targetEntityName,
            targetEntityTag = targetEntityTag,
            targetEntitySlot = targetEntitySlot,
            targetEntityId = targetEntityId,
            variables = varsMap,
            lastUpdatedVarKey = updatedVarKey,
            lastVarUpdateTime = if (updatedVarKey != null) System.currentTimeMillis() else activeSessionState.lastVarUpdateTime,
            isActive = isActive
        )

        if (server != null) {
            val payload = vito.cobblebrain.network.CobblebrainPayloads.StorySessionStateSyncPayload(
                storyId = safeStoryId,
                packName = packName,
                sceneName = sceneName,
                activeNodeId = activeNodeId,
                activeNodeType = activeNodeType,
                targetEntityName = targetEntityName,
                targetEntityTag = targetEntityTag,
                targetEntitySlot = targetEntitySlot,
                targetEntityId = targetEntityId,
                variablesJson = varsJson,
                lastUpdatedVarKey = updatedVarKey ?: "",
                isActive = isActive
            )
            server.playerList.players.forEach { p ->
                sendSessionStateSync?.invoke(p, payload)
            }
        }
    }
}
