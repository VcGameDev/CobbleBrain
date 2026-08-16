package vito.cobblebrain.engine

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import vito.cobblebrain.blocks.impl.*
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.NodeType
import vito.cobblebrain.model.StoryProject
import vito.cobblebrain.model.VariableType

data class ActiveStoryInstance(
    val storyId: String,
    val project: StoryProject,
    val context: StoryContext
)

object StoryExecutor {
    val activeStories = mutableMapOf<String, ActiveStoryInstance>()
    private const val MAX_NODES_PER_TICK = 50

    fun startStory(project: StoryProject, player: ServerPlayer? = null, server: MinecraftServer? = null): ActiveStoryInstance {
        val context = StoryContext(player = player, server = server ?: player?.server)

        // Instantiate all variables registered in project catalog
        project.variables.forEach { variable ->
            context.variables[variable.id] = variable.parseTypedDefaultValue()
        }

        val instance = ActiveStoryInstance(storyId = project.id, project = project, context = context)
        activeStories[project.id] = instance

        // Explicitly search for scene marked with isStartScene == true or active/first scene
        val scene = project.scenes.find { it.isStartScene } ?: project.getActiveScene() ?: project.scenes.firstOrNull()
        if (scene != null) {
            project.activeSceneId = scene.id

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
    }

    fun stopAllStories(player: ServerPlayer? = null) {
        val keysToRemove = activeStories.filter { (_, inst) ->
            player == null || inst.context.player?.uuid == player.uuid
        }.keys.toList()

        keysToRemove.forEach { id ->
            stopStory(id)
        }
    }

    fun executeNodeChain(instance: ActiveStoryInstance, currentNode: NodeData, targetPortId: String? = null, stepCount: Int = 0) {
        if (instance.context.isCancelled) return

        if (stepCount >= MAX_NODES_PER_TICK) {
            TickManager.schedule(1) {
                executeNodeChain(instance, currentNode, targetPortId, stepCount = 0)
            }
            return
        }

        instance.context.currentNodeId = currentNode.id

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
            instance.project.scenes.forEach { scene -> allNodes.addAll(scene.nodes) }

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
                        is MutableList<*> -> (currentVal as MutableList<Any?>).map { it.toString() }.toMutableList()
                        is List<*> -> currentVal.map { it.toString() }.toMutableList()
                        null -> {
                            val registeredVar = instance.project.variables.find { it.id == varKey }
                            (registeredVar?.parseTypedDefaultValue() as? MutableList<String>) ?: mutableListOf()
                        }
                        else -> currentVal.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
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
            val scene = instance.project.getActiveScene()
            if (scene != null) {
                val activeInputPortIds = scene.connections.filter { it.toNodeId == currentNode.id }.map { it.toPortId }.toSet()
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
        }

        if (currentNode.nodeType == NodeType.CONSTRUCTION) {
            executeConstruction(instance, currentNode, stepCount)
            return
        }

        executeNodeAction(instance.context, currentNode)

        if (currentNode.nodeType == NodeType.QUEST) {
            StoryMissionManager.startMission(instance, currentNode)
            return
        }

        val condType = currentNode.params["condType"] ?: "LOCATION"
        if (currentNode.nodeType == NodeType.TIMER || (currentNode.nodeType == NodeType.TRIGGER && condType == "TIMER")) {
            val delaySec = currentNode.params["timerSeconds"]?.toDoubleOrNull() ?: (currentNode.params["timerSeconds"]?.toIntOrNull()?.toDouble() ?: 5.0)
            val delayTicks = maxOf(1, (delaySec * 20.0).toInt())

            TickManager.schedule(delayTicks) {
                continueOutgoingConnections(instance, currentNode, stepCount)
            }
            return
        } else {
            continueOutgoingConnections(instance, currentNode, stepCount)
        }
    }

    fun evaluateVariableCondition(actualVal: Any?, op: String, targetValStr: String): Boolean {
        if (actualVal is List<*> || op in listOf("CONTAINS", "SIZE", "IS_EMPTY", "GET_INDEX")) {
            val list = when (actualVal) {
                is List<*> -> actualVal.map { it?.toString() ?: "" }
                null -> emptyList()
                else -> actualVal.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }
            }

            return when (op) {
                "CONTAINS" -> list.any { it.equals(targetValStr, ignoreCase = true) }
                "SIZE" -> {
                    val expectedSize = targetValStr.toIntOrNull() ?: 0
                    list.size == expectedSize
                }
                "IS_EMPTY" -> list.isEmpty()
                "GET_INDEX" -> {
                    if (targetValStr.contains(":")) {
                        val parts = targetValStr.split(":", limit = 2)
                        val idx = parts[0].trim().toIntOrNull() ?: 0
                        val expectedVal = parts[1].trim()
                        if (idx in list.indices) list[idx].equals(expectedVal, ignoreCase = true) else false
                    } else {
                        val idx = targetValStr.toIntOrNull() ?: 0
                        idx in list.indices
                    }
                }
                "==" -> list.joinToString(",").equals(targetValStr, ignoreCase = true)
                "!=" -> !list.joinToString(",").equals(targetValStr, ignoreCase = true)
                else -> list.any { it.equals(targetValStr, ignoreCase = true) }
            }
        }

        val actualStr = actualVal?.toString() ?: ""
        val actualNum = (actualVal as? Number)?.toDouble() ?: actualStr.toDoubleOrNull()
        val targetNum = targetValStr.toDoubleOrNull()

        if (actualNum != null && targetNum != null) {
            return when (op) {
                "==" -> actualNum == targetNum
                "!=" -> actualNum != targetNum
                ">" -> actualNum > targetNum
                "<" -> actualNum < targetNum
                ">=" -> actualNum >= targetNum
                "<=" -> actualNum <= targetNum
                else -> actualNum == targetNum
            }
        }

        val actualBool = actualVal as? Boolean ?: actualStr.toBooleanStrictOrNull()
        val targetBool = targetValStr.toBooleanStrictOrNull()
        if (actualBool != null && targetBool != null) {
            return when (op) {
                "==" -> actualBool == targetBool
                "!=" -> actualBool != targetBool
                else -> actualBool == targetBool
            }
        }

        return when (op) {
            "==" -> actualStr.equals(targetValStr, ignoreCase = true)
            "!=" -> !actualStr.equals(targetValStr, ignoreCase = true)
            else -> actualStr.equals(targetValStr, ignoreCase = true)
        }
    }

    private fun finishSceneExecution(instance: ActiveStoryInstance, stepCount: Int) {
        val scene = instance.project.getActiveScene() ?: return

        // If current scene is marked with isEndScene == true, declare story finished
        if (scene.isEndScene) {
            stopStory(instance.storyId)
            return
        }

        // Otherwise, emit signal on Scene OUT port in global graph
        val outgoingSceneConnections = instance.project.sceneConnections.filter { it.fromNodeId == scene.id }
        for (sceneConn in outgoingSceneConnections) {
            val targetScene = instance.project.scenes.find { it.id == sceneConn.toNodeId }
            if (targetScene != null) {
                instance.project.activeSceneId = targetScene.id

                // Start destination scene prioritizing BeginSceneBlock (Scene Entry Point)
                val initialNode = targetScene.nodes.find { it.nodeType == NodeType.BEGIN_SCENE }
                    ?: targetScene.nodes.find { it.nodeType == NodeType.TRIGGER }
                    ?: targetScene.nodes.firstOrNull()

                if (initialNode != null) {
                    executeNodeChain(instance, initialNode, targetPortId = null, stepCount = stepCount + 1)
                }
            }
        }
    }

    private fun executeConstruction(instance: ActiveStoryInstance, constructionNode: NodeData, stepCount: Int) {
        val subNodes = constructionNode.innerNodes
        val subConns = constructionNode.innerConnections

        if (subNodes.isEmpty()) {
            continueOutgoingConnections(instance, constructionNode, stepCount)
            return
        }

        val firstSubNode = subNodes.firstOrNull() ?: return

        fun runSubNode(currentSubNode: NodeData, subStep: Int) {
            if (instance.context.isCancelled) return

            executeNodeAction(instance.context, currentSubNode)

            val outgoingSub = subConns.filter { it.fromNodeId == currentSubNode.id }
            if (outgoingSub.isNotEmpty()) {
                for (conn in outgoingSub) {
                    val nextSubNode = subNodes.find { it.id == conn.toNodeId }
                    if (nextSubNode != null) {
                        runSubNode(nextSubNode, subStep + 1)
                    }
                }
            } else {
                continueOutgoingConnections(instance, constructionNode, stepCount + 1)
            }
        }

        runSubNode(firstSubNode, 0)
    }

    private fun continuePortConnections(instance: ActiveStoryInstance, currentNode: NodeData, portId: String, stepCount: Int) {
        if (instance.context.isCancelled) return
        val scene = instance.project.getActiveScene() ?: return
        val outgoingConnections = scene.connections.filter { it.fromNodeId == currentNode.id && it.fromPortId == portId }

        for (conn in outgoingConnections) {
            val targetNode = scene.nodes.find { it.id == conn.toNodeId }
            if (targetNode != null) {
                executeNodeChain(instance, targetNode, targetPortId = conn.toPortId, stepCount = stepCount + 1)
            }
        }
    }

    private fun continueOutgoingConnections(instance: ActiveStoryInstance, currentNode: NodeData, stepCount: Int) {
        if (instance.context.isCancelled) return
        val scene = instance.project.getActiveScene() ?: return
        val outgoingConnections = scene.connections.filter { it.fromNodeId == currentNode.id }

        if (outgoingConnections.isNotEmpty()) {
            for (conn in outgoingConnections) {
                val targetNode = scene.nodes.find { it.id == conn.toNodeId }
                if (targetNode != null) {
                    executeNodeChain(instance, targetNode, targetPortId = conn.toPortId, stepCount = stepCount + 1)
                }
            }
        } else {
            finishSceneExecution(instance, stepCount)
        }
    }

    private fun executeNodeAction(context: StoryContext, node: NodeData) {
        when (node.nodeType) {
            NodeType.BEGIN_SCENE -> {
                BeginSceneBlock().evaluate(context, node)
            }
            NodeType.DIALOGUE, NodeType.TRIGGER -> {
                SendMessageAction().execute(context, node)
            }
            NodeType.ACTION -> {
                val actionSubtype = node.params["actionSubtype"] ?: "MESSAGE"
                when (actionSubtype) {
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
                    "SPAWN_PARTICLES" -> SpawnParticlesAction().execute(context, node)
                    "PLAY_SOUND", "SOUND" -> PlaySoundAction().execute(context, node)
                    "PLAY_MUSIC" -> PlayMusicAction().execute(context, node)
                    else -> SendMessageAction().execute(context, node)
                }
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
            NodeType.TIMER, NodeType.CONDITION_NODE, NodeType.COMMAND_NODE, NodeType.CONSTRUCTION, NodeType.END_SCENE, NodeType.GATE, NodeType.LINK_SEND, NodeType.LINK_RECEIVE, NodeType.LOOP, NodeType.COMMENT, NodeType.VARIABLE_GET, NodeType.VARIABLE_SET -> {
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

            try {
                val source = if (isServerSource) {
                    var s = server.createCommandSourceStack().withPermission(4)
                    if (isSilent) s = s.withSuppressedOutput()
                    s
                } else {
                    if (player != null) {
                        var s = player.createCommandSourceStack()
                        if (isSilent) s = s.withSuppressedOutput()
                        s
                    } else {
                        var s = server.createCommandSourceStack().withPermission(4)
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
            val strVal = if (value is List<*>) value.joinToString(",") else value?.toString() ?: ""
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
        val allNodes = mutableListOf<NodeData>()
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
}
