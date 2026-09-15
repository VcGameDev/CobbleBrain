package vito.cobblebrain.engine

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import vito.cobblebrain.blocks.impl.*
import vito.cobblebrain.model.*

import vito.cobblebrain.engine.checkpoint.StoryCheckpointManager

data class PrerequisiteValidationResult(
    val isValid: Boolean,
    val reason: String = ""
)

data class ActiveStoryInstance(
    val storyId: String,
    val project: StoryProject,
    val context: StoryContext
)

object StoryExecutor {
    val activeStories = mutableMapOf<String, ActiveStoryInstance>()
    val completedStoriesByPlayer = mutableMapOf<java.util.UUID, MutableSet<String>>()
    private const val MAX_NODES_PER_TICK = 50

    var sendStartKeyInput: ((ServerPlayer, vito.cobblebrain.network.CobblebrainPayloads.StartKeyInputPayload) -> Unit)? = null
    var sendCancelKeyInput: ((ServerPlayer, vito.cobblebrain.network.CobblebrainPayloads.CancelKeyInputPayload) -> Unit)? = null

    fun markStoryCompleted(player: ServerPlayer?, storyId: String) {
        if (player != null && storyId.isNotBlank()) {
            completedStoriesByPlayer.getOrPut(player.uuid) { mutableSetOf() }.add(storyId)
        }
    }

    fun isStoryCompleted(player: ServerPlayer?, storyId: String): Boolean {
        if (player == null || storyId.isBlank()) return false
        return completedStoriesByPlayer[player.uuid]?.contains(storyId) == true
    }

    fun validatePrerequisites(project: StoryProject, player: ServerPlayer?, server: MinecraftServer?): PrerequisiteValidationResult {
        val prereqs = project.prerequisites

        if (player != null) {
            // 1. World & Game Conditions
            if (prereqs.freshWorldOnly) {
                val srv = player?.server ?: server
                val worldTimeTicks = srv?.overworld()?.gameTime ?: 0L
                val maxTicks = prereqs.freshWorldMaxMinutes * 1200L
                if (worldTimeTicks > maxTicks) {
                    return PrerequisiteValidationResult(false, "World is older than ${prereqs.freshWorldMaxMinutes} minutes (fresh world required).")
                }
            }

            if (prereqs.requiredDimension.isNotBlank()) {
                val currentDim = player.level().dimension().location().toString()
                val targetDim = prereqs.requiredDimension.trim()
                if (!currentDim.equals(targetDim, ignoreCase = true) && !targetDim.equals(currentDim.substringAfter(":"), ignoreCase = true)) {
                    return PrerequisiteValidationResult(false, "Must be in dimension '$targetDim' (current: $currentDim).")
                }
            }

            if (prereqs.requiredGameMode.isNotBlank() && !prereqs.requiredGameMode.equals("ANY", ignoreCase = true)) {
                val currentMode = player.gameMode.gameModeForPlayer.name
                if (!currentMode.equals(prereqs.requiredGameMode, ignoreCase = true)) {
                    return PrerequisiteValidationResult(false, "Must be in game mode '${prereqs.requiredGameMode}' (current: $currentMode).")
                }
            }

            // 2. Cobblemon Party Constraints
            val partyList = try {
                val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
                (0..5).mapNotNull { party.get(it) }
            } catch (_: Exception) {
                emptyList()
            }

            if (prereqs.minPartySize > 0 && partyList.size < prereqs.minPartySize) {
                return PrerequisiteValidationResult(false, "Must have at least ${prereqs.minPartySize} Pokémon in party (current: ${partyList.size}).")
            }

            if (prereqs.maxPartySize > 0 && partyList.size > prereqs.maxPartySize) {
                return PrerequisiteValidationResult(false, "Must have at most ${prereqs.maxPartySize} Pokémon in party (current: ${partyList.size}).")
            }

            if (prereqs.partyLevelCap > 0) {
                val overleveled = partyList.filter { it.level > prereqs.partyLevelCap }
                if (overleveled.isNotEmpty()) {
                    val names = overleveled.joinToString { "${it.species.name} (Lv. ${it.level})" }
                    return PrerequisiteValidationResult(false, "Party level cap is ${prereqs.partyLevelCap}. Overleveled: $names.")
                }
            }

            if (prereqs.requiredPokemonType.isNotBlank()) {
                val reqType = prereqs.requiredPokemonType.trim()
                val hasType = partyList.any { pkmn ->
                    pkmn.primaryType.name.equals(reqType, ignoreCase = true) ||
                    (pkmn.secondaryType?.name?.equals(reqType, ignoreCase = true) == true)
                }
                if (!hasType) {
                    return PrerequisiteValidationResult(false, "Must have at least one $reqType-type Pokémon in party.")
                }
            }

            // 3. Story Dependencies & Inventory
            if (prereqs.requiredCompletedStories.isNotEmpty()) {
                val missing = prereqs.requiredCompletedStories.filter { !isStoryCompleted(player, it) }
                if (missing.isNotEmpty()) {
                    return PrerequisiteValidationResult(false, "Prerequisite stories not completed: ${missing.joinToString()}.")
                }
            }

            if (prereqs.emptyInventoryRequired) {
                val isInvEmpty = player.inventory.items.all { it.isEmpty } &&
                                 player.inventory.armor.all { it.isEmpty } &&
                                 player.inventory.offhand.all { it.isEmpty }
                if (!isInvEmpty) {
                    return PrerequisiteValidationResult(false, "Inventory must be completely empty.")
                }
            }
        }

        return PrerequisiteValidationResult(true)
    }

    fun handlePrerequisiteFailure(project: StoryProject, player: ServerPlayer?, result: PrerequisiteValidationResult) {
        if (player == null) return
        val prereqs = project.prerequisites
        if (prereqs.failureAction.equals("ALERT_MESSAGE", ignoreCase = true)) {
            val customMsg = prereqs.failureMessage.trim()
            val text = if (customMsg.isNotBlank()) customMsg else "⚠️ Cannot start '${project.name}': ${result.reason}"
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c$text"))
        }
    }

    fun startStory(project: StoryProject, player: ServerPlayer? = null, server: MinecraftServer? = null): ActiveStoryInstance? {
        val validation = validatePrerequisites(project, player, server)
        if (!validation.isValid) {
            handlePrerequisiteFailure(project, player, validation)
            return null
        }

        val context = StoryContext(
            player = player,
            server = server ?: player?.server,
            storyId = project.id,
            project = project
        )

        // Instantiate all variables registered in project catalog
        project.variables.forEach { variable ->
            context.variables[variable.id] = variable.parseTypedDefaultValue()
        }

        val instance = ActiveStoryInstance(storyId = project.id, project = project, context = context)
        activeStories[project.id] = instance

        // Explicitly search for scene marked with isStartScene == true or active/first scene
        val scene = project.scenes.find { it.isStartScene } ?: project.getActiveScene() ?: project.scenes.firstOrNull()
        if (scene != null) {
            StorySerializer.ensureSceneLoaded(project, scene)
            project.activeSceneId = scene.id
            armSceneStandaloneKeyInputs(instance, scene)
            armGlobalStandaloneKeyInputs(instance)

            // Locate BeginSceneBlock or Trigger/Initial node as entry point of scene
            val initialNode = scene.nodes.find { it.nodeType == NodeType.BEGIN_SCENE }
                ?: scene.nodes.find { it.nodeType == NodeType.TRIGGER }
                ?: scene.nodes.firstOrNull()

            if (initialNode != null) {
                executeNodeChain(instance, initialNode, targetPortId = null, stepCount = 0)
            }
        }
        return instance
    }

    fun stopStory(storyId: String) {
        val instance = activeStories.remove(storyId)
        instance?.context?.isCancelled = true
        instance?.context?.waitingCondOutNodes?.clear()
        if (instance?.context?.player != null) {
            sendCancelKeyInput?.invoke(instance.context.player, vito.cobblebrain.network.CobblebrainPayloads.CancelKeyInputPayload(storyId, ""))
            instance.context.waitingKeyInputNodeId = null
        }
        StoryDebugger.clearNodeStatuses(storyId)
        StoryLookAtManager.clearAll()
        StoryJumpManager.clearAll()
        if (instance != null) {
            val server = instance.context.server ?: instance.context.player?.server
            StoryDebugger.broadcastSessionState(
                server = server,
                storyId = storyId,
                packName = instance.project.name.ifBlank { "Story Pack" },
                sceneName = instance.project.getActiveScene()?.title ?: "Main Scene",
                activeNodeId = "",
                activeNodeType = "STOPPED",
                targetEntityName = "",
                targetEntityTag = "",
                targetEntitySlot = "",
                targetEntityId = "",
                variables = instance.context.variables,
                isActive = false
            )
        }
    }

    fun stopAllStories(player: ServerPlayer? = null) {
        val keysToRemove = activeStories.filter { (_, inst) ->
            player == null || inst.context.player?.uuid == player.uuid
        }.keys.toList()

        keysToRemove.forEach { id ->
            stopStory(id)
        }
    }

    fun pauseStory(storyId: String) {
        val instance = activeStories[storyId] ?: activeStories.values.find { it.project.id.equals(storyId, true) || it.project.name.equals(storyId, true) }
        if (instance != null) {
            instance.context.isPaused = true
            val server = instance.context.server ?: instance.context.player?.server
            StoryDebugger.recordLog(
                storyId = instance.storyId,
                blockId = instance.context.currentNodeId ?: "",
                blockType = NodeType.ACTION,
                status = NodeExecutionStatus.RUNNING,
                level = "INFO",
                message = "Story execution PAUSED.",
                server = server
            )
            StoryDebugger.broadcastSessionState(
                server = server,
                storyId = instance.storyId,
                packName = instance.project.name.ifBlank { "Story Pack" },
                sceneName = instance.project.getActiveScene()?.title ?: "Main Scene",
                activeNodeId = instance.context.currentNodeId ?: "",
                activeNodeType = "PAUSED",
                targetEntityName = "",
                targetEntityTag = "",
                targetEntitySlot = "",
                targetEntityId = "",
                variables = instance.context.variables,
                isActive = true,
                isPaused = true
            )
        }
    }

    fun resumeStory(storyId: String) {
        val instance = activeStories[storyId] ?: activeStories.values.find { it.project.id.equals(storyId, true) || it.project.name.equals(storyId, true) }
        if (instance != null) {
            instance.context.isPaused = false
            val server = instance.context.server ?: instance.context.player?.server
            StoryDebugger.recordLog(
                storyId = instance.storyId,
                blockId = instance.context.currentNodeId ?: "",
                blockType = NodeType.ACTION,
                status = NodeExecutionStatus.RUNNING,
                level = "INFO",
                message = "Story execution RESUMED.",
                server = server
            )
            StoryDebugger.broadcastSessionState(
                server = server,
                storyId = instance.storyId,
                packName = instance.project.name.ifBlank { "Story Pack" },
                sceneName = instance.project.getActiveScene()?.title ?: "Main Scene",
                activeNodeId = instance.context.currentNodeId ?: "",
                activeNodeType = "RUNNING",
                targetEntityName = "",
                targetEntityTag = "",
                targetEntitySlot = "",
                targetEntityId = "",
                variables = instance.context.variables,
                isActive = true,
                isPaused = false
            )
            val pending = instance.context.pendingResumes.toList()
            instance.context.pendingResumes.clear()
            pending.forEach { it.invoke() }
        }
    }

    fun executeNodeChain(instance: ActiveStoryInstance, currentNode: NodeData, targetPortId: String? = null, stepCount: Int = 0) {
        if (instance.context.isCancelled) return

        if (instance.context.isPaused) {
            instance.context.pendingResumes.add {
                executeNodeChain(instance, currentNode, targetPortId, stepCount)
            }
            return
        }

        if (stepCount >= MAX_NODES_PER_TICK) {
            TickManager.schedule(1) {
                executeNodeChain(instance, currentNode, targetPortId, stepCount = 0)
            }
            return
        }

        // 1. Input Trigger & Pre-Delay Handling
        if (currentNode.preDelayTicks > 0) {
            instance.context.currentNodeId = currentNode.id
            val storyId = instance.storyId.ifBlank { instance.project.id }
            val server = instance.context.server ?: instance.context.player?.server

            StoryDebugger.recordLog(
                storyId = storyId,
                blockId = currentNode.id,
                blockType = currentNode.nodeType,
                status = NodeExecutionStatus.RUNNING,
                level = "INFO",
                message = "Pre-delay active for ${currentNode.preDelayTicks} ticks (${currentNode.preDelayTicks / 20.0}s) on '${currentNode.title.ifBlank { currentNode.nodeType.name }}'",
                server = server
            )

            TickManager.schedule(currentNode.preDelayTicks) {
                if (!instance.context.isCancelled) {
                    executeNodeCore(instance, currentNode, targetPortId, stepCount)
                }
            }
            return
        }

        executeNodeCore(instance, currentNode, targetPortId, stepCount)
    }

    private fun executeNodeCore(instance: ActiveStoryInstance, currentNode: NodeData, targetPortId: String? = null, stepCount: Int = 0) {
        if (instance.context.isCancelled) return

        instance.context.currentNodeId = currentNode.id
        val storyId = instance.storyId.ifBlank { instance.project.id }
        val server = instance.context.server ?: instance.context.player?.server

        val activeScene = instance.project.getActiveScene()
        val sceneName = activeScene?.title ?: "Main Scene"
        val targetEntityName = currentNode.params["customName"] ?: currentNode.params["targetIdentifier"] ?: ""
        val targetEntityTag = currentNode.params["targetIdentifier"] ?: currentNode.params["entityTag"] ?: ""
        val targetEntitySlot = currentNode.params["pokemonSlot"] ?: ""

        StoryDebugger.broadcastSessionState(
            server = server,
            storyId = storyId,
            packName = instance.project.name.ifBlank { "Story Pack" },
            sceneName = sceneName,
            activeNodeId = currentNode.id,
            activeNodeType = currentNode.nodeType.name,
            targetEntityName = targetEntityName,
            targetEntityTag = targetEntityTag,
            targetEntitySlot = targetEntitySlot,
            targetEntityId = currentNode.id,
            variables = instance.context.variables,
            isActive = true
        )

        StoryDebugger.recordLog(
            storyId = storyId,
            blockId = currentNode.id,
            blockType = currentNode.nodeType,
            status = NodeExecutionStatus.RUNNING,
            level = "INFO",
            message = "Executing node '${currentNode.title.ifBlank { currentNode.nodeType.name }}'",
            server = server
        )

        try {
            // 0. TRIGGER BLOCK BEHAVIOR (WAITING NODE VS GLOBAL LISTENER)
            if (currentNode.nodeType == NodeType.TRIGGER) {
                val requireInput = currentNode.params["requireInputSignal"] != "false"
                if (requireInput) {
                    // If call came from input connection (targetPortId != null), arm Trigger
                    if (targetPortId != null) {
                        instance.context.waitingTriggers.add(currentNode.id)
                        return // Pause sequence here. Wait for world event to fire.
                    } else {
                        // If came from world event, only fire if armed in waiting set
                        if (!instance.context.waitingTriggers.contains(currentNode.id)) {
                            return // Input signal not received yet. Ignore.
                        }
                        instance.context.waitingTriggers.remove(currentNode.id)
                    }
                }
                // If requireInput == false, acts autonomously as Global Listener
            }

        // 1. LINK SEND TRANSMITTER BLOCK
        if (currentNode.nodeType == NodeType.LINK_SEND) {
            val channelTag = currentNode.params["channelTag"] ?: "channel_1"
            val allNodes = mutableListOf<NodeData>()
            instance.project.scenes.forEach { scene ->
                allNodes.addAll(scene.nodes)
                scene.nodes.filter { it.nodeType == NodeType.CONSTRUCTION }.forEach { allNodes.addAll(it.innerNodes) }
            }

            val targetReceivers = allNodes.filter { it.nodeType == NodeType.LINK_RECEIVE && it.params["channelTag"] == channelTag }
            for (receiver in targetReceivers) {
                executeNodeChain(instance, receiver, targetPortId = null, stepCount = stepCount + 1)
            }
            return
        }

        // 2. LINK RECEIVE RECEIVER BLOCK
        if (currentNode.nodeType == NodeType.LINK_RECEIVE) {
            continueOutgoingConnections(instance, currentNode, stepCount + 1)
            return
        }

        // 3. REPEATER / LOOP BLOCK
        if (currentNode.nodeType == NodeType.LOOP) {
            val loopState = instance.context.activeLoops.getOrPut(currentNode.id) { LoopRuntimeState() }

            // Check if signal entered STOP port
            val stopPort = currentNode.inputs.find { it.name.equals("Stop", ignoreCase = true) }
            if (targetPortId != null && stopPort != null && targetPortId == stopPort.id) {
                loopState.isStopped = true
                val donePort = currentNode.outputs.find { it.name.equals("Done", ignoreCase = true) || it.name.equals("COMPLETED", ignoreCase = true) }
                if (donePort != null) {
                    continuePortConnections(instance, currentNode, donePort.id, stepCount + 1)
                }
                return
            }

            // Start Loop from IN port
            loopState.isStopped = false
            loopState.currentIteration = 0

            val mode = currentNode.params["loopMode"] ?: "COUNT"
            val intervalSec = currentNode.params["loopIntervalSec"]?.toDoubleOrNull() ?: 1.0
            val delayTicks = maxOf(1, (intervalSec * 20.0).toInt())

            val cyclePort = currentNode.outputs.find { it.name.equals("Cycle", ignoreCase = true) || it.name.equals("EACH_CYCLE", ignoreCase = true) } ?: currentNode.outputs.firstOrNull()
            val donePort = currentNode.outputs.find { it.name.equals("Done", ignoreCase = true) || it.name.equals("COMPLETED", ignoreCase = true) }

            if (mode == "COUNT") {
                val totalCount = currentNode.params["loopCount"]?.toIntOrNull() ?: 5

                fun stepCountLoop(iter: Int) {
                    if (loopState.isStopped || instance.context.isCancelled) {
                        if (donePort != null) {
                            continuePortConnections(instance, currentNode, donePort.id, stepCount + 1)
                        }
                        return
                    }

                    if (iter >= totalCount) {
                        if (donePort != null) {
                            continuePortConnections(instance, currentNode, donePort.id, stepCount + 1)
                        }
                        return
                    }

                    if (cyclePort != null) {
                        continuePortConnections(instance, currentNode, cyclePort.id, stepCount + 1)
                    }

                    TickManager.schedule(delayTicks) {
                        stepCountLoop(iter + 1)
                    }
                }

                stepCountLoop(0)
            } else { // TIME Mode (Continuous Time)
                fun stepTimeLoop() {
                    if (loopState.isStopped || instance.context.isCancelled) {
                        if (donePort != null) {
                            continuePortConnections(instance, currentNode, donePort.id, stepCount + 1)
                        }
                        return
                    }

                    if (cyclePort != null) {
                        continuePortConnections(instance, currentNode, cyclePort.id, stepCount + 1)
                    }

                    TickManager.schedule(delayTicks) {
                        stepTimeLoop()
                    }
                }

                stepTimeLoop()
            }
            return
        }

        // 4. VARIABLE MODIFIER BLOCK (VARIABLE_SET - EXECUTION SETTER)
        if (currentNode.nodeType == NodeType.VARIABLE_SET) {
            val varKey = currentNode.params["varKey"] ?: "var_new"
            val op = currentNode.params["varOp"] ?: "="
            val valStr = currentNode.params["varValue"] ?: ""

            val varDecl = instance.project.variables.find { it.id == varKey }
            val varType = varDecl?.type ?: VariableType.STRING
            val currentVal = instance.context.variables[varKey] ?: varDecl?.parseTypedDefaultValue() ?: ""

            val newVal: Any = when (varType) {
                VariableType.NUMBER -> {
                    val curNum = (currentVal as? Number)?.toDouble() ?: currentVal.toString().toDoubleOrNull() ?: 0.0
                    val inputNum = valStr.toDoubleOrNull() ?: 0.0
                    when (op) {
                        "+" -> curNum + inputNum
                        "-" -> curNum - inputNum
                        "*" -> curNum * inputNum
                        else -> inputNum // "="
                    }
                }
                VariableType.BOOLEAN -> {
                    val curBool = (currentVal as? Boolean) ?: currentVal.toString().toBoolean()
                    when (op) {
                        "NOT" -> !curBool
                        else -> valStr.toBoolean() // "="
                    }
                }
                VariableType.STRING -> {
                    val curStr = currentVal.toString()
                    when (op) {
                        "+" -> curStr + valStr
                        else -> valStr // "="
                    }
                }
                VariableType.LIST -> {
                    val list = when (currentVal) {
                        is List<*> -> currentVal.map { it.toString() }.toMutableList()
                        is String -> currentVal.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                        else -> mutableListOf()
                    }

                    when (op) {
                        "ADD", "+=" -> list.add(valStr)
                        "REMOVE", "-=" -> list.remove(valStr)
                        "REMOVE_AT" -> {
                            val idx = valStr.toIntOrNull() ?: 0
                            if (idx in list.indices) list.removeAt(idx)
                        }
                        "CLEAR" -> list.clear()
                        "SET", "=" -> {
                            list.clear()
                            if (valStr.isNotBlank()) {
                                list.addAll(valStr.split(",").map { it.trim() }.filter { it.isNotBlank() })
                            }
                        }
                    }
                    list
                }
            }
            instance.context.variables[varKey] = newVal
            notifyVariableChanged(instance, varKey, newVal)
            continueOutgoingConnections(instance, currentNode, stepCount + 1)
            return
        }

        // 5. VARIABLE READER BLOCK (VARIABLE_GET - DATA GETTER)
        if (currentNode.nodeType == NodeType.VARIABLE_GET) {
            continueOutgoingConnections(instance, currentNode, stepCount + 1)
            return
        }

        // 6. CONDITION BLOCK (CONDITION_NODE - IF / ELSE IF / ELSE)
        if (currentNode.nodeType == NodeType.CONDITION_NODE) {
            val elseIfCount = currentNode.params["elseIfCount"]?.toIntOrNull() ?: 0
            val hasElse = currentNode.params["hasElse"] != "false"

            // 1. Evaluate Branch 0 ("SE")
            val varKey0 = currentNode.params["varKey_0"] ?: currentNode.params["varKey"] ?: "var_new"
            val op0 = currentNode.params["varOp_0"] ?: currentNode.params["varOp"] ?: "=="
            val targetVal0 = currentNode.params["varValue_0"] ?: currentNode.params["varValue"] ?: "true"
            val actualVal0 = instance.context.variables[varKey0]

            if (evaluateVariableCondition(actualVal0, op0, targetVal0)) {
                val ifPort = currentNode.outputs.find { it.id == "OUT_IF" || it.name.equals("SE", ignoreCase = true) || it.name.equals("IF", ignoreCase = true) }
                    ?: currentNode.outputs.firstOrNull()
                if (ifPort != null) {
                    continuePortConnections(instance, currentNode, ifPort.id, stepCount + 1)
                }
                return
            }

            // 2. Evaluate Else-If Branches 1..elseIfCount
            for (i in 1..elseIfCount) {
                val varKeyI = currentNode.params["varKey_$i"] ?: "var_new"
                val opI = currentNode.params["varOp_$i"] ?: "=="
                val targetValI = currentNode.params["varValue_$i"] ?: "true"
                val actualValI = instance.context.variables[varKeyI]

                if (evaluateVariableCondition(actualValI, opI, targetValI)) {
                    val elseIfPort = currentNode.outputs.find {
                        it.id == "OUT_ELSE_IF_$i" || it.name.equals("SENÃO SE $i", ignoreCase = true) || it.name.equals("SENAO SE $i", ignoreCase = true) || it.name.equals("ELSE IF $i", ignoreCase = true)
                    } ?: currentNode.outputs.getOrNull(i)

                    if (elseIfPort != null) {
                        continuePortConnections(instance, currentNode, elseIfPort.id, stepCount + 1)
                    }
                    return
                }
            }

            // 3. Fallback Else branch
            if (hasElse) {
                val elsePort = currentNode.outputs.find {
                    it.id == "OUT_ELSE" || it.name.equals("SENÃO", ignoreCase = true) || it.name.equals("SENAO", ignoreCase = true) || it.name.equals("ELSE", ignoreCase = true) || it.name.equals("False", ignoreCase = true)
                } ?: if (currentNode.outputs.size > (1 + elseIfCount)) currentNode.outputs.lastOrNull() else if (elseIfCount == 0 && currentNode.outputs.size >= 2) currentNode.outputs[1] else null

                if (elsePort != null) {
                    continuePortConnections(instance, currentNode, elsePort.id, stepCount + 1)
                }
            }
            return
        }

        // 6.1 COMMAND EXECUTION BLOCK (COMMAND_NODE)
        if (currentNode.nodeType == NodeType.COMMAND_NODE) {
            executeCommandNode(instance, currentNode)
            continueOutgoingConnections(instance, currentNode, stepCount + 1)
            return
        }

        // 7. SCENE FINISH BLOCK (END_SCENE)
        if (currentNode.nodeType == NodeType.END_SCENE) {
            finishSceneExecution(instance, stepCount)
            return
        }

        // 8. GATE SYNCHRONIZER BLOCK (GATE)
        if (currentNode.nodeType == NodeType.GATE) {
            val parentConstr = findParentConstruction(instance, currentNode.id)
            val allConns = if (parentConstr != null) parentConstr.innerConnections else instance.project.getActiveScene()?.connections ?: emptyList()
            val activeInputPortIds = allConns.filter { it.toNodeId == currentNode.id }.map { it.toPortId }.toSet()
            val receivedPorts = instance.context.gateState.getOrPut(currentNode.id) { mutableSetOf() }

            if (targetPortId != null) {
                receivedPorts.add(targetPortId)
            } else if (currentNode.inputs.isNotEmpty()) {
                receivedPorts.add(currentNode.inputs.first().id)
            }

            // Only advance execution when ALL connected IN ports have received input signal
            if (activeInputPortIds.isEmpty() || receivedPorts.containsAll(activeInputPortIds)) {
                instance.context.gateState.remove(currentNode.id)
                continueOutgoingConnections(instance, currentNode, stepCount + 1)
            }
            return
        }

        // 9. SAVE STATE CHECKPOINT BLOCK
        if (currentNode.nodeType == NodeType.SAVE_STATE_NODE) {
            val success = StoryCheckpointManager.saveCheckpoint(instance.context, currentNode)
            val outputPortId = if (success) {
                currentNode.outputs.find { it.id == "OUT_SUCCESS" || it.name.equals("Success", true) || it.name.equals("OK", true) }?.id
                    ?: currentNode.outputs.firstOrNull()?.id
            } else {
                currentNode.outputs.find { it.id == "OUT_ERROR" || it.name.equals("Error", true) }?.id
                    ?: currentNode.outputs.lastOrNull()?.id
            }
            if (outputPortId != null) {
                continuePortConnections(instance, currentNode, outputPortId, stepCount + 1)
            }
            return
        }

        // 10. LOAD STATE CHECKPOINT BLOCK
        if (currentNode.nodeType == NodeType.LOAD_STATE_NODE) {
            val server = instance.context.server ?: instance.context.player?.server
            if (server == null) {
                val errPort = currentNode.outputs.find { it.id == "OUT_ERROR" || it.name.equals("Error", true) }?.id ?: currentNode.outputs.lastOrNull()?.id
                if (errPort != null) continuePortConnections(instance, currentNode, errPort, stepCount + 1)
                return
            }

            val scope = currentNode.params["scope"] ?: "PLAYER"
            val rawProfileId = currentNode.params["profileId"]?.ifBlank { "checkpoint_1" } ?: "checkpoint_1"
            val mergeMode = currentNode.params["mergeMode"] ?: "OVERWRITE"
            val gracePeriodTicks = currentNode.params["gracePeriodTicks"]?.toIntOrNull() ?: 60
            val cleanStoryTag = currentNode.params["cleanStoryTag"]?.trim() ?: ""
            val jumpToTargetNodeId = currentNode.params["jumpToTargetNodeId"]?.trim() ?: ""

            val checkpointData = StoryCheckpointManager.loadCheckpoint(server, instance.context.player, scope, rawProfileId, instance.context.variables)

            if (checkpointData == null) {
                // File not found!
                val notFoundPort = currentNode.outputs.find { it.id == "OUT_NOT_FOUND" || it.name.contains("Not Found", true) || it.name.contains("Missing", true) }?.id
                    ?: currentNode.outputs.find { it.id == "OUT_ERROR" || it.name.equals("Error", true) }?.id
                    ?: currentNode.outputs.getOrNull(1)?.id
                    ?: currentNode.outputs.firstOrNull()?.id

                if (notFoundPort != null) {
                    continuePortConnections(instance, currentNode, notFoundPort, stepCount + 1)
                }
                return
            }

            // Apply loaded checkpoint data
            StoryCheckpointManager.applyCheckpoint(
                context = instance.context,
                checkpointData = checkpointData,
                mergeMode = mergeMode,
                gracePeriodTicks = gracePeriodTicks,
                cleanStoryTag = cleanStoryTag
            )

            // Notify trigger listeners of checkpoint load
            val player = instance.context.player
            val resolvedProfileId = StoryCheckpointManager.resolveProfileId(rawProfileId, player, instance.context.variables)
            if (player != null) {
                StoryListenerManager.onCheckpointLoaded(player, resolvedProfileId)
            }

            // Flow Redirection if jumpToTargetNodeId is specified
            if (jumpToTargetNodeId.isNotBlank()) {
                val allNodes = mutableListOf<NodeData>()
                instance.project.scenes.forEach { scene -> allNodes.addAll(scene.nodes) }
                val targetJumpNode = allNodes.find { it.id == jumpToTargetNodeId || it.title.equals(jumpToTargetNodeId, true) }
                if (targetJumpNode != null) {
                    executeNodeChain(instance, targetJumpNode, targetPortId = null, stepCount = stepCount + 1)
                    return
                }
            }

            // Otherwise, fire OUT_SUCCESS
            val successPort = currentNode.outputs.find { it.id == "OUT_SUCCESS" || it.name.equals("Success", true) || it.name.equals("OK", true) }?.id
                ?: currentNode.outputs.firstOrNull()?.id
            if (successPort != null) {
                continuePortConnections(instance, currentNode, successPort, stepCount + 1)
            }
            return
        }

        if (currentNode.nodeType == NodeType.BEGIN_CONSTRUCTION) {
            val constrName = currentNode.params["constructionName"]?.ifBlank { "Construction" } ?: "Construction"
            val speedMode = currentNode.params["buildSpeedMode"] ?: "INSTANT"

            StoryDebugger.recordLog(
                storyId = storyId,
                blockId = currentNode.id,
                blockType = currentNode.nodeType,
                status = NodeExecutionStatus.RUNNING,
                level = "INFO",
                message = "Begin Construction entry point fired: '$constrName' (Speed: $speedMode)",
                server = server
            )

            // Dispatch flow to internal nodes via single OUT port
            continueOutgoingConnections(instance, currentNode, stepCount + 1)
            return
        }

        if (currentNode.nodeType == NodeType.END_CONSTRUCTION) {
            val finalizeTags = currentNode.params["finalizeTags"] != "false"
            val playSound = currentNode.params["playCompletionSound"] == "true"
            val soundId = currentNode.params["completionSoundId"]?.ifBlank { "minecraft:block.anvil.use" } ?: "minecraft:block.anvil.use"

            val parentConstr = findParentConstruction(instance, currentNode.id)
            if (parentConstr != null) {
                val scope = instance.context.activeConstructions[parentConstr.id]
                if (scope != null) {
                    if (scope.isCompleted) return // Prevent multiple activations
                    scope.isCompleted = true
                    scope.endNodeId = currentNode.id
                }
            } else {
                val openScopes = instance.context.activeConstructions.values.filter { !it.isCompleted }
                openScopes.forEach {
                    it.isCompleted = true
                    it.endNodeId = currentNode.id
                }
            }

            if (playSound && server != null) {
                val player = instance.context.player
                if (player != null) {
                    try {
                        val soundRes = net.minecraft.resources.ResourceLocation.tryParse(soundId)
                        val soundEvent = if (soundRes != null) net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(soundRes) else null
                        if (soundEvent != null) {
                            player.level().playSound(
                                null,
                                player.x, player.y, player.z,
                                soundEvent,
                                net.minecraft.sounds.SoundSource.PLAYERS,
                                1.0f, 1.0f
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            StoryDebugger.recordLog(
                storyId = storyId,
                blockId = currentNode.id,
                blockType = currentNode.nodeType,
                status = NodeExecutionStatus.SUCCESS,
                level = "INFO",
                message = "End Construction reached. Construction finalized (Tags: $finalizeTags, Sound: $playSound). Releasing outer flow.",
                server = server
            )

            if (parentConstr != null) {
                continueOutgoingConnections(instance, parentConstr, stepCount + 1)
            } else {
                continueOutgoingConnections(instance, currentNode, stepCount + 1)
            }
            return
        }

        if (currentNode.nodeType == NodeType.CONSTRUCTION) {
            executeConstruction(instance, currentNode, stepCount)
            StoryDebugger.recordLog(
                storyId = storyId,
                blockId = currentNode.id,
                blockType = currentNode.nodeType,
                status = NodeExecutionStatus.SUCCESS,
                level = "INFO",
                message = "Construction node initiated",
                server = server
            )
            return
        }

        if (currentNode.nodeType == NodeType.KEY_INPUT) {
            executeKeyInputNode(instance, currentNode, stepCount)
            return
        }

        executeNodeAction(instance.context, currentNode)

        StoryDebugger.recordLog(
            storyId = storyId,
            blockId = currentNode.id,
            blockType = currentNode.nodeType,
            status = NodeExecutionStatus.SUCCESS,
            level = "INFO",
            message = "Node '${currentNode.title.ifBlank { currentNode.nodeType.name }}' executed successfully",
            server = server
        )

        if (currentNode.nodeType == NodeType.QUEST) {
            StoryMissionManager.startMission(instance, currentNode)
            return
        }

        val condType = currentNode.params["condType"] ?: "LOCATION"
        val actionSubtype = currentNode.params["actionSubtype"] ?: ""
        if (currentNode.nodeType == NodeType.ACTION &&
            (actionSubtype in listOf("MOVE_TO_BLOCK", "NAVIGATE_ENTITY", "MOVE_TO", "PATHFIND_ENTITY")) &&
            currentNode.params["waitForCompletion"] != "false") {
            return
        } else if (currentNode.nodeType == NodeType.ACTION &&
            (actionSubtype in listOf("ANIMATION", "ANIMATION_BLOCK", "LOOK_AT", "LOOK_AT_BLOCK")) &&
            currentNode.params["waitForCompletion"] == "true" &&
            currentNode.params["durationMode"] != "INDEFINITE" &&
            currentNode.params["operationMode"] != "RESET_LOOK") {
            val durTicks = currentNode.params["durationTicks"]?.toIntOrNull()?.coerceAtLeast(1) ?: 60
            TickManager.schedule(durTicks) {
                continueOutgoingConnections(instance, currentNode, stepCount)
            }
            return
        } else if (currentNode.nodeType == NodeType.TIMER || (currentNode.nodeType == NodeType.TRIGGER && condType == "TIMER")) {
            val delaySec = currentNode.params["timerSeconds"]?.toDoubleOrNull() ?: (currentNode.params["timerSeconds"]?.toIntOrNull()?.toDouble() ?: 5.0)
            val delayTicks = maxOf(1, (delaySec * 20.0).toInt())

            TickManager.schedule(delayTicks) {
                continueOutgoingConnections(instance, currentNode, stepCount)
            }
            return
        } else {
            continueOutgoingConnections(instance, currentNode, stepCount)
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        val storyId = instance.storyId.ifBlank { instance.project.id }
        val server = instance.context.server ?: instance.context.player?.server
        StoryDebugger.recordLog(
            storyId = storyId,
            blockId = currentNode.id,
            blockType = currentNode.nodeType,
            status = NodeExecutionStatus.FAILED,
            level = "ERROR",
            message = e.message ?: "Execution error on node '${currentNode.title.ifBlank { currentNode.nodeType.name }}'",
            details = e.stackTraceToString(),
            server = server
        )
    }
}

    fun evaluateVariableCondition(actualVal: Any?, op: String, targetValStr: String): Boolean {
        val opUpper = op.trim().uppercase()
        val targetTrimmed = targetValStr.trim()

        // 1. List Operations (or actualVal is List / JsonArray)
        if (actualVal is List<*> || actualVal is com.google.gson.JsonArray || opUpper in listOf("CONTAINS", "NOT_CONTAINS", "!CONTAINS", "SIZE", "COUNT", "IS_EMPTY", "NOT_EMPTY", "EMPTY", "!EMPTY", "GET_INDEX")) {
            val list: List<String> = when (actualVal) {
                is List<*> -> actualVal.map { it?.toString()?.trim() ?: "" }
                is com.google.gson.JsonArray -> actualVal.map { if (it.isJsonPrimitive) it.asString.trim() else it.toString().trim() }
                null -> emptyList()
                else -> actualVal.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }
            }

            return when (opUpper) {
                "CONTAINS" -> list.any { it.equals(targetTrimmed, ignoreCase = true) }
                "NOT_CONTAINS", "!CONTAINS" -> !list.any { it.equals(targetTrimmed, ignoreCase = true) }
                "SIZE", "COUNT" -> {
                    val expectedSize = targetTrimmed.toIntOrNull() ?: 0
                    list.size == expectedSize
                }
                "IS_EMPTY", "EMPTY" -> list.isEmpty()
                "NOT_EMPTY", "!EMPTY" -> list.isNotEmpty()
                "GET_INDEX" -> {
                    if (targetTrimmed.contains(":")) {
                        val parts = targetTrimmed.split(":", limit = 2)
                        val idx = parts[0].trim().toIntOrNull() ?: 0
                        val expectedVal = parts[1].trim()
                        if (idx in list.indices) list[idx].equals(expectedVal, ignoreCase = true) else false
                    } else {
                        val idx = targetTrimmed.toIntOrNull() ?: 0
                        idx in list.indices
                    }
                }
                "==" -> list.joinToString(",").equals(targetTrimmed, ignoreCase = true)
                "!=" -> !list.joinToString(",").equals(targetTrimmed, ignoreCase = true)
                else -> list.any { it.equals(targetTrimmed, ignoreCase = true) }
            }
        }

        val actualStr = actualVal?.toString()?.trim() ?: ""

        // 2. Safe Numeric Coercion (both sides toDoubleOrNull)
        val actualNum = (actualVal as? Number)?.toDouble() ?: actualStr.toDoubleOrNull()
        val targetNum = targetTrimmed.toDoubleOrNull()

        if (actualNum != null && targetNum != null) {
            return when (opUpper) {
                "==" -> actualNum == targetNum
                "!=" -> actualNum != targetNum
                ">" -> actualNum > targetNum
                "<" -> actualNum < targetNum
                ">=" -> actualNum >= targetNum
                "<=" -> actualNum <= targetNum
                else -> actualNum == targetNum
            }
        }

        // 3. Safe Boolean Coercion
        val actualBool = (actualVal as? Boolean) ?: actualStr.toBooleanStrictOrNull()
        val targetBool = targetTrimmed.toBooleanStrictOrNull()
        if (actualBool != null && targetBool != null) {
            return when (opUpper) {
                "==" -> actualBool == targetBool
                "!=" -> actualBool != targetBool
                else -> actualBool == targetBool
            }
        }

        // 4. String Comparisons
        return when (opUpper) {
            "==" -> actualStr.equals(targetTrimmed, ignoreCase = true)
            "!=" -> !actualStr.equals(targetTrimmed, ignoreCase = true)
            "CONTAINS" -> actualStr.contains(targetTrimmed, ignoreCase = true)
            "NOT_CONTAINS", "!CONTAINS" -> !actualStr.contains(targetTrimmed, ignoreCase = true)
            "STARTS_WITH" -> actualStr.startsWith(targetTrimmed, ignoreCase = true)
            "ENDS_WITH" -> actualStr.endsWith(targetTrimmed, ignoreCase = true)
            "IS_EMPTY", "EMPTY" -> actualStr.isEmpty()
            "NOT_EMPTY", "!EMPTY" -> actualStr.isNotEmpty()
            else -> actualStr.equals(targetTrimmed, ignoreCase = true)
        }
    }

    private fun finishSceneExecution(instance: ActiveStoryInstance, stepCount: Int) {
        val scene = instance.project.getActiveScene() ?: return

        // If current scene is marked with isEndScene == true, declare story finished
        if (scene.isEndScene) {
            markStoryCompleted(instance.context.player, instance.storyId)
            stopStory(instance.storyId)
            return
        }

        // Emit signal on Scene OUT port in global graph (to scenes or blocks)
        val outgoingSceneConnections = instance.project.sceneConnections.filter { it.fromNodeId == scene.id || it.fromPortId == scene.outPort.id }
        if (outgoingSceneConnections.isEmpty()) {
            markStoryCompleted(instance.context.player, instance.storyId)
            stopStory(instance.storyId)
            return
        }
        for (sceneConn in outgoingSceneConnections) {
            val targetScene = instance.project.scenes.find { it.id == sceneConn.toNodeId }
            if (targetScene != null) {
                StorySerializer.ensureSceneLoaded(instance.project, targetScene)
                instance.project.activeSceneId = targetScene.id
                armSceneStandaloneKeyInputs(instance, targetScene)

                // Start destination scene prioritizing BeginSceneBlock (Scene Entry Point)
                val initialNode = targetScene.nodes.find { it.nodeType == NodeType.BEGIN_SCENE }
                    ?: targetScene.nodes.find { it.nodeType == NodeType.TRIGGER }
                    ?: targetScene.nodes.firstOrNull()

                if (initialNode != null) {
                    executeNodeChain(instance, initialNode, targetPortId = null, stepCount = stepCount + 1)
                }
            } else {
                // Target is a block/node connected to Scene OUT port
                StorySerializer.ensureAllScenesLoaded(instance.project)
                val allNodes = instance.project.scenes.flatMap { it.nodes } + instance.project.globalNodes
                val targetNode = allNodes.find { it.id == sceneConn.toNodeId }
                if (targetNode != null) {
                    if (!targetNode.parentSceneId.isNullOrBlank()) {
                        instance.project.activeSceneId = targetNode.parentSceneId!!
                        val nextScene = instance.project.scenes.find { it.id == targetNode.parentSceneId }
                        if (nextScene != null) armSceneStandaloneKeyInputs(instance, nextScene)
                    }
                    executeNodeChain(instance, targetNode, targetPortId = sceneConn.toPortId, stepCount = stepCount + 1)
                }
            }
        }
    }

    fun findParentConstruction(instance: ActiveStoryInstance, nodeId: String): NodeData? {
        fun searchIn(nodes: List<NodeData>): NodeData? {
            for (node in nodes) {
                if (node.nodeType == NodeType.CONSTRUCTION) {
                    if (node.innerNodes.any { it.id == nodeId }) {
                        return node
                    }
                    val nested = searchIn(node.innerNodes)
                    if (nested != null) return nested
                }
            }
            return null
        }

        for (scene in instance.project.scenes) {
            val found = searchIn(scene.nodes)
            if (found != null) return found
        }
        return null
    }

    private fun executeConstruction(instance: ActiveStoryInstance, constructionNode: NodeData, stepCount: Int) {
        val subNodes = constructionNode.innerNodes
        if (subNodes.isEmpty()) {
            continueOutgoingConnections(instance, constructionNode, stepCount)
            return
        }

        val beginNode = subNodes.find { it.nodeType == NodeType.BEGIN_CONSTRUCTION }
            ?: subNodes.firstOrNull() ?: return

        val constrName = beginNode.params["constructionName"]?.ifBlank { constructionNode.title.ifBlank { "Construction" } }
            ?: constructionNode.title.ifBlank { "Construction" }
        val speedMode = beginNode.params["buildSpeedMode"] ?: "INSTANT"
        val stepDelay = beginNode.params["tickDelayBetweenSteps"]?.toIntOrNull() ?: 5
        val timeoutTicks = beginNode.params["timeoutTicks"]?.toIntOrNull() ?: 600

        val scope = ActiveConstructionScope(
            beginNodeId = beginNode.id,
            constructionName = constrName,
            buildSpeedMode = speedMode,
            tickDelayBetweenSteps = stepDelay,
            timeoutTicks = timeoutTicks
        )
        instance.context.activeConstructions[constructionNode.id] = scope
        instance.context.activeConstructions[beginNode.id] = scope

        val server = instance.context.server ?: instance.context.player?.server

        StoryDebugger.recordLog(
            storyId = instance.project.id,
            blockId = constructionNode.id,
            blockType = constructionNode.nodeType,
            status = NodeExecutionStatus.RUNNING,
            level = "INFO",
            message = "Construction initiated: '$constrName' (Speed: $speedMode, Timeout: ${timeoutTicks}t)",
            server = server
        )

        // Anti-softlock timeout protection
        if (timeoutTicks > 0) {
            TickManager.schedule(timeoutTicks) {
                if (!instance.context.isCancelled && !scope.isCompleted) {
                    scope.isCompleted = true
                    StoryDebugger.recordLog(
                        storyId = instance.project.id,
                        blockId = constructionNode.id,
                        blockType = constructionNode.nodeType,
                        status = NodeExecutionStatus.FALLBACK_TRIGGERED,
                        level = "WARN",
                        message = "Construction '$constrName' timed out after ${timeoutTicks} ticks! Releasing main flow.",
                        details = "Anti-softlock safety guard triggered to prevent story progression stall.",
                        server = server
                    )
                    continueOutgoingConnections(instance, constructionNode, stepCount + 1)
                }
            }
        }

        // Execute internal chain starting at BEGIN_CONSTRUCTION with full native pipeline support (delays, timers, branching)
        executeNodeChain(instance, beginNode, targetPortId = null, stepCount = stepCount + 1)
    }

    fun continuePortConnections(instance: ActiveStoryInstance, currentNode: NodeData, portId: String, stepCount: Int) {
        if (instance.context.isCancelled) return

        if (instance.context.isPaused) {
            instance.context.pendingResumes.add {
                continuePortConnections(instance, currentNode, portId, stepCount)
            }
            return
        }

        // Post-Delay Timing (OUT)
        if (currentNode.postDelayTicks > 0) {
            val server = instance.context.server ?: instance.context.player?.server
            StoryDebugger.recordLog(
                storyId = instance.storyId.ifBlank { instance.project.id },
                blockId = currentNode.id,
                blockType = currentNode.nodeType,
                status = NodeExecutionStatus.RUNNING,
                level = "INFO",
                message = "Post-delay waiting ${currentNode.postDelayTicks} ticks (${currentNode.postDelayTicks / 20.0}s) on '${currentNode.title.ifBlank { currentNode.nodeType.name }}'",
                server = server
            )
            TickManager.schedule(currentNode.postDelayTicks) {
                if (!instance.context.isCancelled) {
                    dispatchPortConnections(instance, currentNode, portId, stepCount)
                }
            }
            return
        }

        dispatchPortConnections(instance, currentNode, portId, stepCount)
    }

    fun isConditionalOutputPort(node: NodeData, portId: String): Boolean {
        if (portId == "OUT_COND") return true
        val condPort = node.outputs.find { it.id == "OUT_COND" || it.name.startsWith("Cond Out", ignoreCase = true) || it.name.contains("Cond", ignoreCase = true) }
        return condPort != null && condPort.id == portId
    }

    private fun dispatchPortConnections(instance: ActiveStoryInstance, currentNode: NodeData, portId: String, stepCount: Int) {
        if (instance.context.isCancelled) return

        val isCond = portId == "OUT_COND"

        val parentConstr = findParentConstruction(instance, currentNode.id)
        if (parentConstr != null) {
            val outgoingConnections = parentConstr.innerConnections.filter { 
                it.fromNodeId == currentNode.id && (it.fromPortId == portId || (isCond && isConditionalOutputPort(currentNode, it.fromPortId)))
            }
            for (conn in outgoingConnections) {
                val targetNode = parentConstr.innerNodes.find { it.id == conn.toNodeId }
                if (targetNode != null) {
                    executeNodeChain(instance, targetNode, targetPortId = conn.toPortId, stepCount = stepCount + 1)
                }
            }
            return
        }

        val scene = instance.project.scenes.find { it.id == currentNode.parentSceneId } ?: instance.project.getActiveScene()
        val outgoingConnections = scene?.connections?.filter { 
            it.fromNodeId == currentNode.id && (it.fromPortId == portId || (isCond && isConditionalOutputPort(currentNode, it.fromPortId)))
        } ?: emptyList()

        for (conn in outgoingConnections) {
            val targetNode = scene?.nodes?.find { it.id == conn.toNodeId }
            if (targetNode != null) {
                executeNodeChain(instance, targetNode, targetPortId = conn.toPortId, stepCount = stepCount + 1)
            }
        }

        // Also check inter-scene / block-to-scene / global connections
        val interConnections = instance.project.sceneConnections.filter { 
            it.fromNodeId == currentNode.id && (it.fromPortId == portId || (isCond && isConditionalOutputPort(currentNode, it.fromPortId)))
        }
        for (conn in interConnections) {
            val targetScene = instance.project.scenes.find { it.id == conn.toNodeId }
            if (targetScene != null) {
                StorySerializer.ensureSceneLoaded(instance.project, targetScene)
                instance.project.activeSceneId = targetScene.id
                val initialNode = targetScene.nodes.find { it.nodeType == NodeType.BEGIN_SCENE }
                    ?: targetScene.nodes.find { it.nodeType == NodeType.TRIGGER }
                    ?: targetScene.nodes.firstOrNull()
                if (initialNode != null) {
                    executeNodeChain(instance, initialNode, targetPortId = null, stepCount = stepCount + 1)
                }
            } else {
                StorySerializer.ensureAllScenesLoaded(instance.project)
                val allNodes = instance.project.scenes.flatMap { it.nodes } + instance.project.globalNodes
                val targetNode = allNodes.find { it.id == conn.toNodeId }
                if (targetNode != null) {
                    if (!targetNode.parentSceneId.isNullOrBlank()) {
                        instance.project.activeSceneId = targetNode.parentSceneId!!
                        val nextScene = instance.project.scenes.find { it.id == targetNode.parentSceneId }
                        if (nextScene != null) armSceneStandaloneKeyInputs(instance, nextScene)
                    }
                    executeNodeChain(instance, targetNode, targetPortId = conn.toPortId, stepCount = stepCount + 1)
                }
            }
        }
    }

    fun continueOutgoingConnections(instance: ActiveStoryInstance, currentNode: NodeData, stepCount: Int = 0) {
        if (instance.context.isCancelled) return

        if (instance.context.isPaused) {
            instance.context.pendingResumes.add {
                continueOutgoingConnections(instance, currentNode, stepCount)
            }
            return
        }

        // Post-Delay Timing (OUT)
        if (currentNode.postDelayTicks > 0) {
            val server = instance.context.server ?: instance.context.player?.server
            StoryDebugger.recordLog(
                storyId = instance.storyId.ifBlank { instance.project.id },
                blockId = currentNode.id,
                blockType = currentNode.nodeType,
                status = NodeExecutionStatus.RUNNING,
                level = "INFO",
                message = "Post-delay waiting ${currentNode.postDelayTicks} ticks (${currentNode.postDelayTicks / 20.0}s) on '${currentNode.title.ifBlank { currentNode.nodeType.name }}'",
                server = server
            )
            TickManager.schedule(currentNode.postDelayTicks) {
                if (!instance.context.isCancelled) {
                    dispatchOutgoingConnections(instance, currentNode, stepCount)
                }
            }
            return
        }

        dispatchOutgoingConnections(instance, currentNode, stepCount)
    }

    private fun dispatchOutgoingConnections(instance: ActiveStoryInstance, currentNode: NodeData, stepCount: Int) {
        if (instance.context.isCancelled) return

        val storyId = instance.storyId.ifBlank { instance.project.id }
        val server = instance.context.server ?: instance.context.player?.server
        StoryDebugger.recordLog(
            storyId = storyId,
            blockId = currentNode.id,
            blockType = currentNode.nodeType,
            status = NodeExecutionStatus.SUCCESS,
            level = "INFO",
            message = "Node completed: '${currentNode.title.ifBlank { currentNode.nodeType.name }}'",
            server = server
        )

        // 1. Evaluate Universal Conditional Output Trigger (OUT_COND) if configured
        val condEnabled = currentNode.params["condOutEnabled"] == "true"
        val condMode = currentNode.params["condOutMode"] ?: "INSTANT"
        val condVarKey = currentNode.params["condVarKey"]?.trim() ?: ""
        val condOp = currentNode.params["condOp"] ?: "=="
        val condVarValue = currentNode.params["condVarValue"] ?: ""

        if (condEnabled && condVarKey.isNotBlank()) {
            val registeredVar = instance.project.variables.find { it.id == condVarKey }
            val actualVal = instance.context.variables[condVarKey] ?: registeredVar?.parseTypedDefaultValue()
            val condMatches = evaluateVariableCondition(actualVal, condOp, condVarValue)

            if (condMatches) {
                instance.context.waitingCondOutNodes.remove(currentNode.id)
                StoryDebugger.recordLog(
                    storyId = storyId,
                    blockId = currentNode.id,
                    blockType = currentNode.nodeType,
                    status = NodeExecutionStatus.SUCCESS,
                    level = "INFO",
                    message = "Conditional trigger MATCHED ($condVarKey $condOp $condVarValue) -> Firing 'OUT_COND' port.",
                    server = server
                )
                // Dispatch connections from OUT_COND
                dispatchPortConnections(instance, currentNode, "OUT_COND", stepCount)
            } else {
                if (condMode.equals("LISTENER", ignoreCase = true)) {
                    instance.context.waitingCondOutNodes[currentNode.id] = stepCount
                    StoryDebugger.recordLog(
                        storyId = storyId,
                        blockId = currentNode.id,
                        blockType = currentNode.nodeType,
                        status = NodeExecutionStatus.RUNNING,
                        level = "INFO",
                        message = "Conditional Out LISTENER armed: waiting for $condVarKey $condOp $condVarValue (current: $actualVal).",
                        server = server
                    )
                } else {
                    StoryDebugger.recordLog(
                        storyId = storyId,
                        blockId = currentNode.id,
                        blockType = currentNode.nodeType,
                        status = NodeExecutionStatus.IDLE,
                        level = "INFO",
                        message = "Conditional trigger skipped (INSTANT mode): ($condVarKey $condOp $condVarValue, current: $actualVal).",
                        server = server
                    )
                }
            }
        }

        // 2. Dispatch Standard (non-OUT_COND) Outgoing Connections

        val parentConstr = findParentConstruction(instance, currentNode.id)
        if (parentConstr != null) {
            val outgoingConnections = parentConstr.innerConnections.filter { it.fromNodeId == currentNode.id && !isConditionalOutputPort(currentNode, it.fromPortId) }
            for (conn in outgoingConnections) {
                val targetNode = parentConstr.innerNodes.find { it.id == conn.toNodeId }
                if (targetNode != null) {
                    executeNodeChain(instance, targetNode, targetPortId = conn.toPortId, stepCount = stepCount + 1)
                }
            }
            return
        }

        val scene = instance.project.scenes.find { it.id == currentNode.parentSceneId } ?: instance.project.getActiveScene()
        val outgoingConnections = scene?.connections?.filter { it.fromNodeId == currentNode.id && !isConditionalOutputPort(currentNode, it.fromPortId) } ?: emptyList()
        val interConnections = instance.project.sceneConnections.filter { it.fromNodeId == currentNode.id && !isConditionalOutputPort(currentNode, it.fromPortId) }

        for (conn in outgoingConnections) {
            val targetNode = scene?.nodes?.find { it.id == conn.toNodeId }
            if (targetNode != null) {
                executeNodeChain(instance, targetNode, targetPortId = conn.toPortId, stepCount = stepCount + 1)
            }
        }
        for (conn in interConnections) {
            val targetScene = instance.project.scenes.find { it.id == conn.toNodeId }
            if (targetScene != null) {
                StorySerializer.ensureSceneLoaded(instance.project, targetScene)
                instance.project.activeSceneId = targetScene.id
                val initialNode = targetScene.nodes.find { it.nodeType == NodeType.BEGIN_SCENE }
                    ?: targetScene.nodes.find { it.nodeType == NodeType.TRIGGER }
                    ?: targetScene.nodes.firstOrNull()
                if (initialNode != null) {
                    executeNodeChain(instance, initialNode, targetPortId = null, stepCount = stepCount + 1)
                }
            } else {
                StorySerializer.ensureAllScenesLoaded(instance.project)
                val allNodes = instance.project.scenes.flatMap { it.nodes } + instance.project.globalNodes
                val targetNode = allNodes.find { it.id == conn.toNodeId }
                if (targetNode != null) {
                    if (!targetNode.parentSceneId.isNullOrBlank()) {
                        instance.project.activeSceneId = targetNode.parentSceneId!!
                        val nextScene = instance.project.scenes.find { it.id == targetNode.parentSceneId }
                        if (nextScene != null) armSceneStandaloneKeyInputs(instance, nextScene)
                    }
                    executeNodeChain(instance, targetNode, targetPortId = conn.toPortId, stepCount = stepCount + 1)
                }
            }
        }
    }

    private fun executeNodeAction(context: StoryContext, node: NodeData) {
        when (node.nodeType) {
            NodeType.BEGIN_SCENE -> {
                BeginSceneBlock().evaluate(context, node)
            }
            NodeType.DIALOGUE -> {
                if (node.params["useAi"] == "true") {
                    val inst = activeStories.values.find { it.context == context }
                    if (inst != null) {
                        AIDialogueBlock().evaluate(inst, node)
                    } else {
                        SendMessageAction().execute(context, node)
                    }
                } else {
                    SendMessageAction().execute(context, node)
                }
            }
            NodeType.TRIGGER -> {
                SendMessageAction().execute(context, node)
            }
            NodeType.ACTION -> {
                val actionSubtype = node.params["actionSubtype"] ?: "MESSAGE"
                when (actionSubtype) {
                    "AI_DIALOGUE" -> {
                        val inst = activeStories.values.find { it.context == context }
                        if (inst != null) {
                            AIDialogueBlock().evaluate(inst, node)
                        }
                    }
                    "VAR_MODIFY" -> {
                        val varKey = node.params["varKey"] ?: "var_new"
                        val op = node.params["varOp"] ?: "="
                        val valStr = node.params["varValue"] ?: ""

                        val currentVal = context.variables[varKey]
                        val newVal: Any = when (op) {
                            "+=" -> {
                                val curNum = (currentVal as? Number)?.toDouble() ?: currentVal?.toString()?.toDoubleOrNull() ?: 0.0
                                val addNum = valStr.toDoubleOrNull() ?: 0.0
                                curNum + addNum
                            }
                            "-=" -> {
                                val curNum = (currentVal as? Number)?.toDouble() ?: currentVal?.toString()?.toDoubleOrNull() ?: 0.0
                                val subNum = valStr.toDoubleOrNull() ?: 0.0
                                curNum - subNum
                            }
                            "TOGGLE" -> {
                                val curBool = (currentVal as? Boolean) ?: currentVal?.toString()?.toBoolean() ?: false
                                !curBool
                            }
                            else -> { // "="
                                if (valStr.equals("true", true) || valStr.equals("false", true)) {
                                    valStr.toBoolean()
                                } else if (valStr.toDoubleOrNull() != null) {
                                    valStr.toDouble()
                                } else {
                                    valStr
                                }
                            }
                        }
                        context.variables[varKey] = newVal
                        val inst = activeStories.values.find { it.context == context }
                        if (inst != null) {
                            notifyVariableChanged(inst, varKey, newVal)
                        }
                    }
                    "TELEPORT" -> TeleportAction().execute(context, node)
                    "CHANGE_WEATHER" -> ChangeWeatherAction().execute(context, node)
                    "SET_TIME_OF_DAY" -> SetTimeOfDayAction().execute(context, node)
                    "SPAWN_BLOCK" -> SpawnBlockAction().execute(context, node)
                    "MODIFY_BLOCK_PROPERTY" -> ModifyBlockPropertyAction().execute(context, node)
                    "SPAWN_ENTITY" -> SpawnEntityAction().execute(context, node)
                    "KILL_ENTITY" -> KillEntityAction().execute(context, node)
                    "MODIFY_ENTITY_PROPERTIES" -> ModifyEntityPropertiesAction().execute(context, node)
                    "ADD_ENTITY_EFFECT" -> ApplyEffectAction().execute(context, node)
                    "ADD_AREA_EFFECT" -> AreaEffectAction().execute(context, node)
                    "SPAWN_COBBLEMON", "SPAWN_POKEMON", "SPAWN" -> SpawnCobblemonAction().execute(context, node)
                    "GIVE_POKEMON" -> GivePokemonAction().execute(context, node)
                    "MODIFY_POKEMON_PROPERTIES" -> ModifyPokemonPropertiesAction().execute(context, node)
                    "KILL_PLAYER" -> KillPlayerAction().execute(context, node)
                    "DAMAGE_PLAYER" -> DamagePlayerAction().execute(context, node)
                    "GIVE_ITEM" -> GiveItemAction().execute(context, node)
                    "REMOVE_ITEM" -> RemoveItemAction().execute(context, node)
                    "ADD_PLAYER_EFFECT", "EFFECT" -> ApplyEffectAction().execute(context, node)
                    "SPAWN_ITEM" -> SpawnItemAction().execute(context, node)
                    "SEND_CHAT_MESSAGE", "MESSAGE" -> SendMessageAction().execute(context, node)
                    "SHOW_TITLE_SCREEN" -> ShowTitleAction().execute(context, node)
                    "LOOK_AT", "LOOK_AT_BLOCK" -> LookAtAction().execute(context, node)
                    "ANIMATION", "ANIMATION_BLOCK" -> AnimationAction().execute(context, node)
                    "MOVE_TO_BLOCK", "NAVIGATE_ENTITY", "MOVE_TO", "PATHFIND_ENTITY" -> MoveToAction().execute(context, node)
                    "SET_ENTITY_TEXTURE", "TEXTURE_BLOCK", "ENTITY_TEXTURE" -> SetEntityTextureAction().execute(context, node)
                    "TAG_BLOCK", "MANAGE_TAG", "TAG_ACTION", "TAG" -> TagAction().execute(context, node)
                    "SPAWN_STRUCTURE", "STRUCTURE" -> SpawnStructureAction().execute(context, node)
                    "CHANGE_POKEMON_PERSONALITY" -> ChangePokemonPersonalityAction().execute(context, node)
                    "ADD_POKEMON_PARTY_EFFECT" -> PartyPokemonEffectAction().execute(context, node)
                    "JUMP_TO_STORY_POINT" -> JumpToStoryPointAction().execute(context, node)
                    "REWIND_TO_STORY_POINT" -> RewindToStoryPointAction().execute(context, node)
                    "CHANGE_SCREEN_TINT" -> ChangeScreenTintAction().execute(context, node)
                    "SPAWN_PARTICLES" -> SpawnParticlesAction().execute(context, node)
                    "PLAY_SOUND", "SOUND" -> PlaySoundAction().execute(context, node)
                    "PLAY_MUSIC" -> PlayMusicAction().execute(context, node)
                    else -> SendMessageAction().execute(context, node)
                }
            }
            NodeType.TEXTURE -> {
                SetEntityTextureAction().execute(context, node)
            }
            NodeType.AUDIO -> {
                executeAudioNode(context, node)
            }
            NodeType.QUEST -> {
                val inst = activeStories.values.find { it.context == context }
                if (inst != null) {
                    StoryMissionManager.startMission(inst, node)
                }
            }
            NodeType.TIMER, NodeType.CONDITION_NODE, NodeType.COMMAND_NODE, NodeType.CONSTRUCTION, NodeType.BEGIN_CONSTRUCTION, NodeType.END_CONSTRUCTION, NodeType.END_SCENE, NodeType.GATE, NodeType.LINK_SEND, NodeType.LINK_RECEIVE, NodeType.LOOP, NodeType.COMMENT, NodeType.VARIABLE_GET, NodeType.VARIABLE_SET -> {
                // Executed by graph flow control logic
            }
            else -> {}
        }
    }

    private fun executeCommandNode(instance: ActiveStoryInstance, node: NodeData) {
        val context = instance.context
        val server = context.server ?: context.player?.server ?: return
        val player = context.player

        val rawContent = node.content.ifBlank { node.params["commands"] ?: "" }
        val lines = rawContent.lines().map { it.trim() }.filter { it.isNotBlank() }

        val isServerSource = (node.params["commandSource"] ?: "SERVER") == "SERVER"
        val isSilent = node.params["silent"] != "false"

        for (rawLine in lines) {
            val interpolated = interpolateCommand(rawLine, context, instance.project)
            val command = interpolated.trim().removePrefix("/")
            if (command.isBlank()) continue

            // SECURITY LOCK: Prevent execution of admin, server control, and permission escalation commands
            val blockedCmd = StoryCommandSecurity.findBlockedAdminCommand(command)
            if (blockedCmd != null) {
                println("[CobbleBrain Security] Blocked admin/server-control command '$blockedCmd' in story '${instance.project.name}' (Node: ${node.id}, Title: '${node.title}'): '$rawLine'")
                if (player != null && !isSilent) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[CobbleBrain Security] Command blocked: '/$blockedCmd' is restricted for server administration."))
                }
                continue
            }

            try {
                val source = if (isServerSource) {
                    var s = server.createCommandSourceStack().withPermission(2)
                    if (isSilent) s = s.withSuppressedOutput()
                    s
                } else {
                    if (player != null) {
                        var s = player.createCommandSourceStack().withPermission(2)
                        if (isSilent) s = s.withSuppressedOutput()
                        s
                    } else {
                        var s = server.createCommandSourceStack().withPermission(2)
                        if (isSilent) s = s.withSuppressedOutput()
                        s
                    }
                }
                server.commands.performPrefixedCommand(source, command)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun interpolateCommand(rawText: String, context: StoryContext, project: StoryProject): String {
        var result = rawText

        val player = context.player
        val playerName = player?.scoreboardName ?: "Player"
        val playerUuid = player?.uuid?.toString() ?: ""
        val playerX = player?.x?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "0"
        val playerY = player?.y?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "64"
        val playerZ = player?.z?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "0"

        result = result.replace("{player}", playerName, ignoreCase = true)
        result = result.replace("{player_name}", playerName, ignoreCase = true)
        result = result.replace("{name}", playerName, ignoreCase = true)
        result = result.replace("{uuid}", playerUuid, ignoreCase = true)
        result = result.replace("{player_x}", playerX, ignoreCase = true)
        result = result.replace("{player_y}", playerY, ignoreCase = true)
        result = result.replace("{player_z}", playerZ, ignoreCase = true)

        context.variables.forEach { (key, value) ->
            val strVal = if (value is List<*>) value.joinToString(",") else value.toString()
            result = result.replace("{$key}", strVal, ignoreCase = true)
        }

        project.variables.forEach { v ->
            if (!context.variables.containsKey(v.id)) {
                result = result.replace("{${v.id}}", v.defaultValue, ignoreCase = true)
            }
        }

        return result
    }

    fun notifyVariableChanged(instance: ActiveStoryInstance, varKey: String, newVal: Any?) {
        val storyId = instance.storyId.ifBlank { instance.project.id }
        val server = instance.context.server ?: instance.context.player?.server
        val activeScene = instance.project.getActiveScene()
        val sceneName = activeScene?.title ?: "Main Scene"

        StoryDebugger.broadcastSessionState(
            server = server,
            storyId = storyId,
            packName = instance.project.name.ifBlank { "Story Pack" },
            sceneName = sceneName,
            activeNodeId = instance.context.currentNodeId ?: "",
            activeNodeType = "VARIABLE_SET",
            targetEntityName = "",
            targetEntityTag = "",
            targetEntitySlot = "",
            targetEntityId = "",
            variables = instance.context.variables,
            updatedVarKey = varKey,
            isActive = true
        )

        StoryDebugger.recordLog(
            storyId = storyId,
            blockId = instance.context.currentNodeId ?: "var_$varKey",
            blockType = NodeType.VARIABLE_SET,
            status = NodeExecutionStatus.SUCCESS,
            level = "INFO",
            message = "Variable '$varKey' changed to '$newVal'",
            server = server
        )

        val allNodes = mutableListOf<NodeData>()
        allNodes.addAll(instance.project.globalNodes)
        instance.project.scenes.forEach { scene -> allNodes.addAll(scene.nodes) }

        // 1. Reactive VARIABLE_GET nodes with ON_CHANGED output
        val reactiveGetNodes = allNodes.filter {
            it.nodeType == NodeType.VARIABLE_GET &&
            it.params["varKey"] == varKey &&
            it.outputs.any { port -> port.id == "ON_CHANGED_OUT" || port.name.equals("On Changed", ignoreCase = true) || port.name.equals("ON_CHANGED", ignoreCase = true) }
        }

        for (node in reactiveGetNodes) {
            val onChangedPort = node.outputs.find { it.id == "ON_CHANGED_OUT" || it.name.equals("On Changed", ignoreCase = true) || it.name.equals("ON_CHANGED", ignoreCase = true) }
            if (onChangedPort != null) {
                continuePortConnections(instance, node, onChangedPort.id, stepCount = 0)
            }
        }

        // 2. Waiting Triggers with VARIABLE_VALUE_CHECK
        val waitingSnapshot = instance.context.waitingTriggers.toList()
        for (nodeId in waitingSnapshot) {
            val trigNode = allNodes.find { it.id == nodeId } ?: continue
            if (trigNode.nodeType == NodeType.TRIGGER && trigNode.params["triggerType"] == "VARIABLE_VALUE_CHECK") {
                val checkKey = trigNode.params["varKey"] ?: "var_1"
                if (checkKey == varKey) {
                    val op = trigNode.params["varOp"] ?: "=="
                    val targetVal = trigNode.params["varValue"] ?: "0"
                    val isMatch = evaluateVariableCondition(newVal, op, targetVal)
                    val isIfNot = trigNode.params["triggerCondition"] == "IF_NOT"
                    val shouldTrigger = if (isIfNot) !isMatch else isMatch
                    if (shouldTrigger) {
                        instance.context.waitingTriggers.remove(nodeId)
                        executeNodeChain(instance, trigNode, targetPortId = null, stepCount = 0)
                    }
                }
            }
        }

        // 3. Autonomous Reactive Triggers (requireInputSignal == "false")
        val reactiveTriggers = allNodes.filter {
            it.nodeType == NodeType.TRIGGER &&
            it.params["requireInputSignal"] == "false" &&
            it.params["triggerType"] == "VARIABLE_VALUE_CHECK" &&
            (it.params["varKey"] ?: "var_1") == varKey
        }
        for (trigNode in reactiveTriggers) {
            val op = trigNode.params["varOp"] ?: "=="
            val targetVal = trigNode.params["varValue"] ?: "0"
            val isMatch = evaluateVariableCondition(newVal, op, targetVal)
            val isIfNot = trigNode.params["triggerCondition"] == "IF_NOT"
            val shouldTrigger = if (isIfNot) !isMatch else isMatch
            if (shouldTrigger) {
                executeNodeChain(instance, trigNode, targetPortId = null, stepCount = 0)
            }
        }

        // 4. Waiting Conditional Out Listeners (condOutMode == "LISTENER")
        val waitingCondSnapshot = instance.context.waitingCondOutNodes.toMap()
        for ((nodeId, stepCount) in waitingCondSnapshot) {
            val node = allNodes.find { it.id == nodeId } ?: continue
            val condVarKey = node.params["condVarKey"]?.trim() ?: ""
            if (condVarKey == varKey) {
                val condOp = node.params["condOp"] ?: "=="
                val condVarValue = node.params["condVarValue"] ?: ""
                val condMatches = evaluateVariableCondition(newVal, condOp, condVarValue)
                if (condMatches) {
                    instance.context.waitingCondOutNodes.remove(nodeId)
                    StoryDebugger.recordLog(
                        storyId = storyId,
                        blockId = node.id,
                        blockType = node.nodeType,
                        status = NodeExecutionStatus.SUCCESS,
                        level = "INFO",
                        message = "Conditional Out LISTENER triggered: ($condVarKey $condOp $condVarValue matched!) -> Firing 'OUT_COND' port.",
                        server = server
                    )
                    dispatchPortConnections(instance, node, "OUT_COND", stepCount)
                }
            }
        }
    }

    private fun executeAudioNode(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val mode = node.params["audioMode"] ?: "PLAY_SOUND_EFFECT"
        val audioId = node.params["audioId"]?.ifBlank { "minecraft:entity.player.levelup" } ?: "minecraft:entity.player.levelup"
        val volume = node.params["audioVolume"]?.toFloatOrNull() ?: 1.0f
        val pitch = node.params["audioPitch"]?.toFloatOrNull() ?: 1.0f
        val isPositional = node.params["spatialMode"] == "POSITIONAL_3D"

        try {
            when (mode) {
                "STOP_ALL_MUSIC" -> {
                    val cmd = "stopsound ${player.scoreboardName}"
                    player.server?.commands?.performPrefixedCommand(
                        player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                        cmd
                    )
                }
                "PLAY_BACKGROUND_MUSIC" -> {
                    val category = "music"
                    val cmd = if (isPositional) {
                        val px = node.params["posX"] ?: "~"
                        val py = node.params["posY"] ?: "~"
                        val pz = node.params["posZ"] ?: "~"
                        "playsound $audioId $category ${player.scoreboardName} $px $py $pz $volume $pitch"
                    } else {
                        "playsound $audioId $category ${player.scoreboardName} ~ ~ ~ $volume $pitch"
                    }
                    player.server?.commands?.performPrefixedCommand(
                        player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                        cmd
                    )
                }
                else -> { // PLAY_SOUND_EFFECT
                    val category = "master"
                    val cmd = if (isPositional) {
                        val px = node.params["posX"] ?: "~"
                        val py = node.params["posY"] ?: "~"
                        val pz = node.params["posZ"] ?: "~"
                        "playsound $audioId $category ${player.scoreboardName} $px $py $pz $volume $pitch"
                    } else {
                        "playsound $audioId $category ${player.scoreboardName} ~ ~ ~ $volume $pitch"
                    }
                    player.server?.commands?.performPrefixedCommand(
                        player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                        cmd
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun executeKeyInputNode(instance: ActiveStoryInstance, currentNode: NodeData, stepCount: Int) {
        val player = instance.context.player
        val server = instance.context.server ?: player?.server
        val storyId = instance.storyId.ifBlank { instance.project.id }

        if (player == null) {
            // No player attached (e.g. headless test run) -> Auto-complete
            StoryDebugger.recordLog(
                storyId = storyId,
                blockId = currentNode.id,
                blockType = currentNode.nodeType,
                status = NodeExecutionStatus.SUCCESS,
                level = "WARN",
                message = "KeyInput node auto-completed (No player attached to story instance).",
                server = server
            )
            continueOutgoingConnections(instance, currentNode, stepCount + 1)
            return
        }

        val isStandalone = isStandaloneKeyInput(currentNode)

        if (!isStandalone) {
            // Store waiting state ON instance.context to isolate per-player/storyInstance!
            instance.context.waitingKeyInputNodeId = currentNode.id
            instance.context.waitingKeyInputStepCount = stepCount
        }

        val inputMode = currentNode.params["inputMode"] ?: if (isStandalone) "PRESS" else "HOLD_ONE_SHOT"
        val targetKey = currentNode.params["targetKey"] ?: "F"
        val holdDurationSec = currentNode.params["holdDurationSec"]?.toDoubleOrNull() ?: 2.0
        val pulseIntervalTicks = currentNode.params["pulseIntervalTicks"]?.toIntOrNull() ?: 10
        val mashTargetCount = currentNode.params["mashTargetCount"]?.toIntOrNull() ?: 10
        val mashDecayPerSec = currentNode.params["mashDecayPerSec"]?.toDoubleOrNull() ?: 2.0
        val timeoutSec = if (isStandalone) 0.0 else (currentNode.params["timeoutSec"]?.toDoubleOrNull() ?: 5.0)
        val promptText = currentNode.params["promptText"] ?: ""
        val showHud = if (isStandalone) (currentNode.params["showHud"] == "true") else (currentNode.params["showHud"] != "false")
        val cancelOnMenuOpen = currentNode.params["cancelOnMenuOpen"] != "false"

        val payload = vito.cobblebrain.network.CobblebrainPayloads.StartKeyInputPayload(
            storyId = storyId,
            nodeId = currentNode.id,
            inputMode = inputMode,
            targetKey = targetKey,
            holdDurationSec = holdDurationSec,
            pulseIntervalTicks = pulseIntervalTicks,
            mashTargetCount = mashTargetCount,
            mashDecayPerSec = mashDecayPerSec,
            timeoutSec = timeoutSec,
            promptText = promptText,
            showHud = showHud,
            cancelOnMenuOpen = cancelOnMenuOpen,
            isStandalone = isStandalone
        )

        sendStartKeyInput?.invoke(player, payload)

        StoryDebugger.recordLog(
            storyId = storyId,
            blockId = currentNode.id,
            blockType = currentNode.nodeType,
            status = NodeExecutionStatus.RUNNING,
            level = "INFO",
            message = "Awaiting player key input: mode=$inputMode, key=$targetKey, standalone=$isStandalone",
            server = server
        )
    }

    private fun isStandaloneKeyInput(node: NodeData): Boolean {
        val mode = node.params["triggerMode"]
        if (mode != null) {
            return mode.equals("STANDALONE", ignoreCase = true)
        }
        return node.params["requireInputSignal"] == "false" || node.inputs.none { it.type == PortType.INPUT }
    }

    fun armSceneStandaloneKeyInputs(instance: ActiveStoryInstance, scene: SceneData) {
        val standaloneNodes = scene.nodes.filter {
            it.nodeType == NodeType.KEY_INPUT && isStandaloneKeyInput(it)
        }
        for (node in standaloneNodes) {
            executeKeyInputNode(instance, node, 0)
        }
    }

    fun armGlobalStandaloneKeyInputs(instance: ActiveStoryInstance) {
        val standaloneNodes = instance.project.globalNodes.filter {
            it.nodeType == NodeType.KEY_INPUT && isStandaloneKeyInput(it)
        }
        for (node in standaloneNodes) {
            executeKeyInputNode(instance, node, 0)
        }
    }

    fun handleKeyInputResult(player: ServerPlayer, storyId: String, nodeId: String, resultEvent: String) {
        // Find the specific story instance matching the player and storyId (player-isolated)
        val instance = activeStories.values.find {
            (it.storyId == storyId || it.project.id == storyId) && it.context.player?.uuid == player.uuid
        } ?: activeStories[storyId]?.takeIf { it.context.player?.uuid == player.uuid }
        ?: return

        val allNodes = instance.project.scenes.flatMap { it.nodes } + instance.project.globalNodes +
                instance.project.scenes.flatMap { it.nodes }.filter { it.nodeType == NodeType.CONSTRUCTION }.flatMap { it.innerNodes }
        val currentNode = allNodes.find { it.id == nodeId } ?: return

        val isStandalone = isStandaloneKeyInput(currentNode)

        if (!isStandalone && instance.context.waitingKeyInputNodeId != nodeId) {
            // Not waiting for this node
            return
        }

        val server = instance.context.server ?: player.server
        val stepCount = if (isStandalone) 0 else instance.context.waitingKeyInputStepCount

        when (resultEvent.uppercase()) {
            "SUCCESS" -> {
                if (!isStandalone) {
                    instance.context.waitingKeyInputNodeId = null
                }
                StoryDebugger.recordLog(
                    storyId = storyId,
                    blockId = currentNode.id,
                    blockType = currentNode.nodeType,
                    status = NodeExecutionStatus.SUCCESS,
                    level = "INFO",
                    message = "KeyInput ${if (isStandalone) "[Standalone]" else ""} completed successfully by player (${player.scoreboardName}).",
                    server = server
                )
                val outPort = currentNode.outputs.find { it.name.equals("Out", true) || it.id == "OUT" } ?: currentNode.outputs.firstOrNull()
                if (outPort != null) {
                    continuePortConnections(instance, currentNode, outPort.id, stepCount + 1)
                } else {
                    continueOutgoingConnections(instance, currentNode, stepCount + 1)
                }
            }
            "PULSE" -> {
                // HOLD_STREAM continuous pulse
                val pulsePort = currentNode.outputs.find { it.name.equals("Out Pulse", true) || it.id == "OUT_PULSE" }
                if (pulsePort != null) {
                    continuePortConnections(instance, currentNode, pulsePort.id, stepCount + 1)
                }
            }
            "RELEASED" -> {
                if (!isStandalone) {
                    instance.context.waitingKeyInputNodeId = null
                }
                StoryDebugger.recordLog(
                    storyId = storyId,
                    blockId = currentNode.id,
                    blockType = currentNode.nodeType,
                    status = NodeExecutionStatus.SUCCESS,
                    level = "INFO",
                    message = "KeyInput key released by player (${player.scoreboardName}).",
                    server = server
                )
                val releasePort = currentNode.outputs.find { it.name.equals("Out Release", true) || it.id == "OUT_RELEASE" }
                if (releasePort != null) {
                    continuePortConnections(instance, currentNode, releasePort.id, stepCount + 1)
                }
            }
            "TIMEOUT", "CANCELLED" -> {
                if (!isStandalone) {
                    instance.context.waitingKeyInputNodeId = null
                }
                StoryDebugger.recordLog(
                    storyId = storyId,
                    blockId = currentNode.id,
                    blockType = currentNode.nodeType,
                    status = NodeExecutionStatus.FALLBACK_TRIGGERED,
                    level = "WARN",
                    message = "KeyInput timed out or was cancelled by player.",
                    server = server
                )
                val timeoutPort = currentNode.outputs.find { it.name.equals("Out Timeout", true) || it.id == "OUT_TIMEOUT" }
                if (timeoutPort != null) {
                    continuePortConnections(instance, currentNode, timeoutPort.id, stepCount + 1)
                }
            }
        }
    }
}
