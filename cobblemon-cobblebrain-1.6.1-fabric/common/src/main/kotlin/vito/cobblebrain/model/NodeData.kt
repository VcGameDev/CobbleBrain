package vito.cobblebrain.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

enum class NodeType {
    TRIGGER,
    ACTION,
    TIMER,
    @SerializedName(value = "CONDITION_NODE", alternate = ["BRANCH", "CONDITION"])
    CONDITION_NODE,
    DIALOGUE,
    CONSTRUCTION,
    BEGIN_SCENE,
    END_SCENE,
    GATE,
    LINK_SEND,
    LINK_RECEIVE,
    LOOP,
    COMMENT,
    VARIABLE_GET,
    VARIABLE_SET,
    QUEST,
    AUDIO,
    @SerializedName(value = "COMMAND_NODE", alternate = ["COMMAND"])
    COMMAND_NODE
}

data class NodeData(
    val id: String = UUID.randomUUID().toString(),
    var parentSceneId: String? = null,
    var title: String = "Novo Nó",
    var nodeType: NodeType = NodeType.DIALOGUE,
    var content: String = "",
    var x: Double = 0.0,
    var y: Double = 0.0,
    var width: Double = 160.0,
    var height: Double = 90.0,
    val inputs: MutableList<PortData> = mutableListOf(),
    val outputs: MutableList<PortData> = mutableListOf(),
    val params: MutableMap<String, String> = mutableMapOf(),
    val innerNodes: MutableList<NodeData> = mutableListOf(),
    val innerConnections: MutableList<ConnectionData> = mutableListOf()
)
