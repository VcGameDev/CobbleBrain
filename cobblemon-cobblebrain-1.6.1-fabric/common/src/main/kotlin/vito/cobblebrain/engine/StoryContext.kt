package vito.cobblebrain.engine

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import vito.cobblebrain.model.StoryProject

data class LoopRuntimeState(
    var currentIteration: Int = 0,
    var isStopped: Boolean = false
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
    var currentNodeId: String? = null,
    var isCancelled: Boolean = false
)
