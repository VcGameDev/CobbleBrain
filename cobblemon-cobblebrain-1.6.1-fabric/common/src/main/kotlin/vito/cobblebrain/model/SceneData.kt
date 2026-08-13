package vito.cobblebrain.model

import java.util.UUID

data class SceneData(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "Cena Inicial",
    var description: String = "",
    var isStartScene: Boolean = false,
    var isEndScene: Boolean = false,
    var x: Double = 0.0,
    var y: Double = 0.0,
    var width: Double = 500.0,
    var height: Double = 350.0,
    val inPort: PortData = PortData(name = "In", type = PortType.INPUT),
    val outPort: PortData = PortData(name = "Out", type = PortType.OUTPUT),
    val nodes: MutableList<NodeData> = mutableListOf(),
    val connections: MutableList<ConnectionData> = mutableListOf()
) {
    init {
        if (nodes.isEmpty()) {
            val beginNode = NodeData(
                parentSceneId = id,
                title = "Início da Cena",
                nodeType = NodeType.BEGIN_SCENE,
                x = x + 30.0,
                y = y + 50.0,
                width = 160.0,
                height = 90.0,
                outputs = mutableListOf(PortData(name = "Out", type = PortType.OUTPUT))
            )
            nodes.add(beginNode)
        }
    }
}
