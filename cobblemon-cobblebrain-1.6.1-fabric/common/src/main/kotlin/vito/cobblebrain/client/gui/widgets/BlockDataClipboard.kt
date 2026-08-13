package vito.cobblebrain.client.gui.widgets

import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.NodeType

object BlockDataClipboard {
    var title: String = ""
    var nodeType: NodeType? = null
    var content: String = ""
    val params: MutableMap<String, String> = mutableMapOf()

    fun copyFrom(node: NodeData) {
        title = node.title
        nodeType = node.nodeType
        content = node.content
        params.clear()
        params.putAll(node.params)
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
}
