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

        // Instanciar todas as variáveis cadastradas no catálogo do projeto
        project.variables.forEach { variable ->
            context.variables[variable.id] = variable.parseTypedDefaultValue()
        }

        val instance = ActiveStoryInstance(storyId = project.id, project = project, context = context)
        activeStories[project.id] = instance

        // Buscar explicitamente a cena marcada com isStartScene == true ou a cena ativa/primeira
        val scene = project.scenes.find { it.isStartScene } ?: project.getActiveScene() ?: project.scenes.firstOrNull()
        if (scene != null) {
            project.activeSceneId = scene.id

            // Localizar nó BeginSceneBlock ou Trigger/Inicial como ponto de partida da cena
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

        // 0. COMPORTAMENTO DO BLOCO TRIGGER (NÓ DE ESPERA VS ESCUTADOR GLOBAL)
        if (currentNode.nodeType == NodeType.TRIGGER) {
            val requireInput = currentNode.params["requireInputSignal"] != "false"
            if (requireInput) {
                // Se a chamada veio de uma conexão de entrada (targetPortId != null), armar o Trigger
                if (targetPortId != null) {
                    instance.context.waitingTriggers.add(currentNode.id)
                    return // Pausa a sequência aqui. Aguarda o evento do mundo ocorrer.
                } else {
                    // Se veio de um evento do mundo, só dispara se estiver armado no conjunto de espera
                    if (!instance.context.waitingTriggers.contains(currentNode.id)) {
                        return // Não recebeu o sinal de entrada ainda. Ignora.
                    }
                    instance.context.waitingTriggers.remove(currentNode.id)
                }
            }
            // Se requireInput == false, atua autonomamente como Escutador Global
        }

        // 1. BLOCO DE TRANSMISSÃO DE LINK (LINK SEND)
        if (currentNode.nodeType == NodeType.LINK_SEND) {
            val channelTag = currentNode.params["channelTag"] ?: "canal_1"
            val allNodes = mutableListOf<NodeData>()
            instance.project.scenes.forEach { scene -> allNodes.addAll(scene.nodes) }

            val targetReceivers = allNodes.filter { it.nodeType == NodeType.LINK_RECEIVE && it.params["channelTag"] == channelTag }
            for (receiver in targetReceivers) {
                executeNodeChain(instance, receiver, targetPortId = null, stepCount = stepCount + 1)
            }
            return
        }

        // 2. BLOCO DE RECEPÇÃO DE LINK (LINK RECEIVE)
        if (currentNode.nodeType == NodeType.LINK_RECEIVE) {
            continueOutgoingConnections(instance, currentNode, stepCount + 1)
            return
        }

        // 3. BLOCO REPETIDOR / LOOP (LOOP)
        if (currentNode.nodeType == NodeType.LOOP) {
            val loopState = instance.context.activeLoops.getOrPut(currentNode.id) { LoopRuntimeState() }

            // Verificar se o sinal entrou na porta STOP
            val stopPort = currentNode.inputs.find { it.name.equals("Stop", ignoreCase = true) }
            if (targetPortId != null && stopPort != null && targetPortId == stopPort.id) {
                loopState.isStopped = true
                val donePort = currentNode.outputs.find { it.name.equals("Done", ignoreCase = true) || it.name.equals("COMPLETED", ignoreCase = true) }
                if (donePort != null) {
                    continuePortConnections(instance, currentNode, donePort.id, stepCount + 1)
                }
                return
            }

            // Iniciar o Loop a partir da porta IN
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
            } else { // Modo TIME (Tempo Contínuo)
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

        // 4. BLOCO MODIFICADOR DE VARIÁVEL (VARIABLE_SET - EXECUTION SETTER)
        if (currentNode.nodeType == NodeType.VARIABLE_SET) {
            val varKey = currentNode.params["varKey"] ?: "var_nova"
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
            continueOutgoingConnections(instance, currentNode, stepCount + 1)
            return
        }

        // 5. BLOCO LEITOR DE VARIÁVEL (VARIABLE_GET - DATA GETTER)
        if (currentNode.nodeType == NodeType.VARIABLE_GET) {
            continueOutgoingConnections(instance, currentNode, stepCount + 1)
            return
        }

        // 6. BLOCO DE RAMIFICAÇÃO (BRANCH - IF/ELSE)
        if (currentNode.nodeType == NodeType.BRANCH) {
            val varKey = currentNode.params["varKey"] ?: "var_nova"
            val op = currentNode.params["varOp"] ?: "=="
            val targetValStr = currentNode.params["varValue"] ?: "true"

            val actualVal = instance.context.variables[varKey]
            val evalResult = evaluateVariableCondition(actualVal, op, targetValStr)

            val scene = instance.project.getActiveScene()
            if (scene != null) {
                val targetPortIdx = if (evalResult) 0 else 1
                val targetPort = currentNode.outputs.getOrNull(targetPortIdx) ?: currentNode.outputs.firstOrNull()
                if (targetPort != null) {
                    continuePortConnections(instance, currentNode, targetPort.id, stepCount + 1)
                }
            }
            return
        }

        // 7. BLOCO DE FINALIZAÇÃO DE CENA (END_SCENE)
        if (currentNode.nodeType == NodeType.END_SCENE) {
            finishSceneExecution(instance, stepCount)
            return
        }

        // 8. BLOCO PORTÃO SINCRONIZADOR (GATE)
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

                // Só avança a execução quando TODAS as portas IN conectadas tiverem recebido o sinal
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

    private fun evaluateVariableCondition(actualVal: Any?, op: String, targetValStr: String): Boolean {
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

        // Se a cena atual estiver marcada com isEndScene == true, declara a história como concluída
        if (scene.isEndScene) {
            stopStory(instance.storyId)
            return
        }

        // Caso contrário, emite o sinal na porta de Saída (OUT) da Cena no grafo global
        val outgoingSceneConnections = instance.project.sceneConnections.filter { it.fromNodeId == scene.id }
        for (sceneConn in outgoingSceneConnections) {
            val targetScene = instance.project.scenes.find { it.id == sceneConn.toNodeId }
            if (targetScene != null) {
                instance.project.activeSceneId = targetScene.id

                // Iniciar a cena destino priorizando o nó BeginSceneBlock (Ponto de Entrada da Cena)
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
                        val varKey = node.params["varKey"] ?: "var_nova"
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
            NodeType.TIMER, NodeType.BRANCH, NodeType.CONSTRUCTION, NodeType.END_SCENE, NodeType.GATE, NodeType.LINK_SEND, NodeType.LINK_RECEIVE, NodeType.LOOP, NodeType.COMMENT, NodeType.VARIABLE_GET, NodeType.VARIABLE_SET -> {
                // Executados pela lógica de controle de fluxo do grafo
            }
            else -> {}
        }
    }
}
