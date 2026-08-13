package vito.cobblebrain.engine

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import vito.cobblebrain.blocks.impl.*
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.NodeType
import vito.cobblebrain.model.StoryProject

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

        // 4. BLOCO DE FINALIZAÇÃO DE CENA (END_SCENE)
        if (currentNode.nodeType == NodeType.END_SCENE) {
            finishSceneExecution(instance, stepCount)
            return
        }

        // 5. BLOCO PORTÃO SINCRONIZADOR (GATE)
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
                    "TELEPORT" -> TeleportAction().execute(context, node)
                    "SPAWN" -> SpawnCobblemonAction().execute(context, node)
                    "SOUND" -> PlaySoundAction().execute(context, node)
                    else -> SendMessageAction().execute(context, node)
                }
            }
            NodeType.TIMER, NodeType.BRANCH, NodeType.CONSTRUCTION, NodeType.END_SCENE, NodeType.GATE, NodeType.LINK_SEND, NodeType.LINK_RECEIVE, NodeType.LOOP, NodeType.COMMENT -> {
                // Executados pela lógica de controle de fluxo do grafo
            }
            else -> {}
        }
    }
}
