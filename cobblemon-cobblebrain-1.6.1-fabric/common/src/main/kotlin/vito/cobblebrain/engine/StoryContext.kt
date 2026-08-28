package vito.cobblebrain.engine

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import vito.cobblebrain.model.StoryProject

data class LoopRuntimeState(
    var currentIteration: Int = 0,
    var isStopped: Boolean = false
)

data class ActiveConstructionScope(
    val beginNodeId: String,
    val constructionName: String,
    val buildSpeedMode: String,
    val tickDelayBetweenSteps: Int,
    val timeoutTicks: Int,
    val startTimeMs: Long = System.currentTimeMillis(),
    var isCompleted: Boolean = false,
    var endNodeId: String? = null
)

data class StoryContext(
    val player: ServerPlayer? = null,
    val server: MinecraftServer? = null,
    var storyId: String = "",
    var project: StoryProject? = null,
    val variables: MutableMap<String, Any> = mutableMapOf(),
    val gateState: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    val waitingTriggers: MutableSet<String> = mutableSetOf(),
    val activeLoops: MutableMap<String, LoopRuntimeState> = mutableMapOf(),
    val activeConstructions: MutableMap<String, ActiveConstructionScope> = mutableMapOf(),
    var currentNodeId: String? = null,
    var isCancelled: Boolean = false,
    var isPaused: Boolean = false,
    val pendingResumes: MutableList<() -> Unit> = mutableListOf()
)
