package vito.cobblebrain.client.gui.widgets

import vito.cobblebrain.model.ConnectionData
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.NodeType
import vito.cobblebrain.model.PortData
import java.util.UUID

data class CopiedNodeData(
    val originalId: String,
    val title: String,
    val nodeType: NodeType,
    val content: String,
    val width: Double,
    val height: Double,
    val relX: Double,
    val relY: Double,
    val preDelayTicks: Int = 0,
    val postDelayTicks: Int = 0,
    val inputs: List<PortData>,
    val outputs: List<PortData>,
    val params: Map<String, String>,
    val innerNodes: List<NodeData> = emptyList(),
    val innerConnections: List<ConnectionData> = emptyList()
)

data class CopiedConnectionData(
    val fromNodeId: String,
    val fromPortId: String,
    val toNodeId: String,
    val toPortId: String
)

object BlockDataClipboard {
    // Single node property buffer (legacy context menu compatibility)
    var title: String = ""
    var nodeType: NodeType? = null
    var content: String = ""
    val params: MutableMap<String, String> = mutableMapOf()

    // Full multi-node & single-node clipboard storage
    val copiedNodes = mutableListOf<CopiedNodeData>()
    val copiedConnections = mutableListOf<CopiedConnectionData>()

    fun copyFrom(node: NodeData) {
        title = node.title
        nodeType = node.nodeType
        content = node.content
        params.clear()
        params.putAll(node.params)
        copy(listOf(node), emptyList())
    }

    fun pasteTo(node: NodeData): Boolean {
        if (nodeType != null && nodeType == node.nodeType) {
            node.content = content
            node.params.clear()
            node.params.putAll(params)
            return true
        }
        return false
    }

    fun hasCompatibleData(targetType: NodeType): Boolean {
        return nodeType == targetType
    }

    fun copy(nodes: List<NodeData>, activeConnections: List<ConnectionData>) {
        copiedNodes.clear()
        copiedConnections.clear()
        if (nodes.isEmpty()) return

        val nodeIds = nodes.map { it.id }.toSet()
        val minX = nodes.minOf { it.x }
        val minY = nodes.minOf { it.y }

        nodes.forEach { node ->
            copiedNodes.add(
                CopiedNodeData(
                    originalId = node.id,
                    title = node.title,
                    nodeType = node.nodeType,
                    content = node.content,
                    width = node.width,
                    height = node.height,
                    relX = node.x - minX,
                    relY = node.y - minY,
                    preDelayTicks = node.preDelayTicks,
                    postDelayTicks = node.postDelayTicks,
                    inputs = node.inputs.map { PortData(id = it.id, name = it.name, type = it.type) },
                    outputs = node.outputs.map { PortData(id = it.id, name = it.name, type = it.type) },
                    params = HashMap(node.params),
                    innerNodes = node.innerNodes,
                    innerConnections = node.innerConnections
                )
            )
        }

        // Copy internal connections between the selected nodes
        activeConnections.forEach { conn ->
            if (nodeIds.contains(conn.fromNodeId) && nodeIds.contains(conn.toNodeId)) {
                copiedConnections.add(
                    CopiedConnectionData(
                        fromNodeId = conn.fromNodeId,
                        fromPortId = conn.fromPortId,
                        toNodeId = conn.toNodeId,
                        toPortId = conn.toPortId
                    )
                )
            }
        }

        // Update single-node legacy fields with first node
        val first = nodes.first()
        title = first.title
        nodeType = first.nodeType
        content = first.content
        params.clear()
        params.putAll(first.params)
    }

    fun hasCopiedNodes(): Boolean = copiedNodes.isNotEmpty()

    fun paste(targetX: Double, targetY: Double, targetSceneId: String?): Pair<List<NodeData>, List<ConnectionData>> {
        if (copiedNodes.isEmpty()) return Pair(emptyList(), emptyList())

        val idMap = mutableMapOf<String, String>() // oldNodeId -> newNodeId
        val portIdMap = mutableMapOf<String, String>() // oldPortId -> newPortId
        val newNodes = mutableListOf<NodeData>()

        copiedNodes.forEach { copied ->
            val newNodeId = UUID.randomUUID().toString()
            idMap[copied.originalId] = newNodeId

            val newInputs = copied.inputs.map { oldPort ->
                val newPortId = UUID.randomUUID().toString()
                portIdMap[oldPort.id] = newPortId
                PortData(id = newPortId, name = oldPort.name, type = oldPort.type)
            }.toMutableList()

            val newOutputs = copied.outputs.map { oldPort ->
                val newPortId = if (oldPort.id == "OUT_COND" || oldPort.name.startsWith("Cond Out", ignoreCase = true)) {
                    "OUT_COND"
                } else {
                    UUID.randomUUID().toString()
                }
                portIdMap[oldPort.id] = newPortId
                PortData(id = newPortId, name = oldPort.name, type = oldPort.type)
            }.toMutableList()

            val node = NodeData(
                id = newNodeId,
                parentSceneId = targetSceneId,
                title = if (copiedNodes.size == 1) "${copied.title} (Copy)" else copied.title,
                nodeType = copied.nodeType,
                content = copied.content,
                x = targetX + copied.relX,
                y = targetY + copied.relY,
                width = copied.width,
                height = copied.height,
                preDelayTicks = copied.preDelayTicks,
                postDelayTicks = copied.postDelayTicks,
                inputs = newInputs,
                outputs = newOutputs,
                params = HashMap(copied.params),
                innerNodes = copied.innerNodes.toMutableList(),
                innerConnections = copied.innerConnections.toMutableList()
            )
            newNodes.add(node)
        }

        val newConnections = mutableListOf<ConnectionData>()
        copiedConnections.forEach { conn ->
            val newFromNode = idMap[conn.fromNodeId]
            val newToNode = idMap[conn.toNodeId]
            val newFromPort = portIdMap[conn.fromPortId] ?: conn.fromPortId
            val newToPort = portIdMap[conn.toPortId] ?: conn.toPortId

            if (newFromNode != null && newToNode != null) {
                newConnections.add(
                    ConnectionData(
                        fromNodeId = newFromNode,
                        fromPortId = newFromPort,
                        toNodeId = newToNode,
                        toPortId = newToPort
                    )
                )
            }
        }

        return Pair(newNodes, newConnections)
    }
}
