package vito.cobblebrain.model

import com.google.gson.annotations.SerializedName
import java.io.File
import java.util.UUID

// ==========================================================
// PORTS & CONNECTIONS
// ==========================================================

enum class PortType {
    INPUT,
    OUTPUT
}

data class PortData(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Porta",
    var type: PortType = PortType.OUTPUT,
    var dataType: String = "flow"
)

data class ConnectionData(
    val id: String = UUID.randomUUID().toString(),
    val fromNodeId: String,
    val fromPortId: String,
    val toNodeId: String,
    val toPortId: String
)

// ==========================================================
// NODES
// ==========================================================

enum class NodeType {
    TRIGGER,
    ACTION,
    TIMER,
    @SerializedName(value = "CONDITION_NODE", alternate = ["BRANCH", "CONDITION"])
    CONDITION_NODE,
    DIALOGUE,
    CONSTRUCTION,
    BEGIN_CONSTRUCTION,
    END_CONSTRUCTION,
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
    COMMAND_NODE,
    @SerializedName(value = "SAVE_STATE_NODE", alternate = ["SAVE_STATE", "CHECKPOINT_SAVE", "CHECKPOINT_NODE", "CHECKPOINT"])
    SAVE_STATE_NODE,
    @SerializedName(value = "LOAD_STATE_NODE", alternate = ["LOAD_STATE", "CHECKPOINT_LOAD"])
    LOAD_STATE_NODE,
    @SerializedName(value = "TEXTURE", alternate = ["TEXTURE_BLOCK", "SET_TEXTURE", "ENTITY_TEXTURE"])
    TEXTURE,
    @SerializedName(value = "KEY_INPUT", alternate = ["KEY_INPUT_NODE", "QTE_NODE", "KEY_LISTENER", "QTE"])
    KEY_INPUT
}

data class NodeData(
    val id: String = UUID.randomUUID().toString(),
    var parentSceneId: String? = null,
    var title: String = "New Node",
    var nodeType: NodeType = NodeType.DIALOGUE,
    var content: String = "",
    var x: Double = 0.0,
    var y: Double = 0.0,
    var width: Double = 160.0,
    var height: Double = 90.0,
    var preDelayTicks: Int = 0,
    var postDelayTicks: Int = 0,
    val inputs: MutableList<PortData> = mutableListOf(),
    val outputs: MutableList<PortData> = mutableListOf(),
    val params: MutableMap<String, String> = mutableMapOf(),
    val innerNodes: MutableList<NodeData> = mutableListOf(),
    val innerConnections: MutableList<ConnectionData> = mutableListOf()
)

// ==========================================================
// SCENES
// ==========================================================

data class SceneData(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "Scene",
    var description: String = "",
    var isStartScene: Boolean = false,
    var isEndScene: Boolean = false,
    var x: Double = 0.0,
    var y: Double = 0.0,
    var width: Double = 500.0,
    var height: Double = 350.0,
    val inPort: PortData = PortData(name = "In", type = PortType.INPUT),
    val outPort: PortData = PortData(name = "Out", type = PortType.OUTPUT),
    var isLoaded: Boolean = true,
    var sourceFileName: String? = null,
    val nodes: MutableList<NodeData> = mutableListOf(),
    val connections: MutableList<ConnectionData> = mutableListOf()
) {
    companion object {
        fun createWithStartNode(
            title: String = "Scene",
            description: String = "",
            isStartScene: Boolean = false,
            isEndScene: Boolean = false,
            x: Double = 0.0,
            y: Double = 0.0,
            width: Double = 500.0,
            height: Double = 350.0
        ): SceneData {
            val scene = SceneData(
                title = title,
                description = description,
                isStartScene = isStartScene,
                isEndScene = isEndScene,
                x = x,
                y = y,
                width = width,
                height = height
            )
            val beginNode = NodeData(
                parentSceneId = scene.id,
                title = "Scene Start",
                nodeType = NodeType.BEGIN_SCENE,
                x = x + 30.0,
                y = y + 50.0,
                width = 160.0,
                height = 90.0,
                outputs = mutableListOf(PortData(name = "Out", type = PortType.OUTPUT))
            )
            scene.nodes.add(beginNode)
            return scene
        }
    }
}

// ==========================================================
// PREREQUISITES & VARIABLES
// ==========================================================

data class StoryPrerequisites(
    // 1. World & Game Conditions
    var freshWorldOnly: Boolean = false,
    var freshWorldMaxMinutes: Int = 20,
    var requiredDimension: String = "",
    var requiredGameMode: String = "ANY", // "ANY", "SURVIVAL", "ADVENTURE", "CREATIVE"

    // 2. Cobblemon Party Constraints
    var minPartySize: Int = -1, // -1 to disable limit (1..6)
    var maxPartySize: Int = -1, // -1 to disable limit (1..6)
    var partyLevelCap: Int = -1, // -1 to disable limit
    var requiredPokemonType: String = "", // e.g. "fire", "water", empty for any

    // 3. Story Dependencies & Inventory
    var requiredCompletedStories: MutableList<String> = mutableListOf(),
    var emptyInventoryRequired: Boolean = false,

    // 4. Failure Handling
    var failureAction: String = "ALERT_MESSAGE", // "SILENT_IGNORE", "ALERT_MESSAGE"
    var failureMessage: String = "" // Custom warning text
)

enum class VariableType {
    BOOLEAN,
    NUMBER,
    STRING,
    LIST
}

enum class VariableScope {
    GLOBAL,
    SCENE_LOCAL
}

data class StoryVariable(
    var id: String = "var_new",
    var name: String = "var_new",
    var type: VariableType = VariableType.STRING,
    var defaultValue: String = "",
    var scope: VariableScope = VariableScope.GLOBAL,
    var sceneId: String? = null
) {
    fun parseTypedDefaultValue(): Any {
        return when (type) {
            VariableType.BOOLEAN -> defaultValue.equals("true", ignoreCase = true)
            VariableType.NUMBER -> defaultValue.toDoubleOrNull() ?: 0.0
            VariableType.STRING -> defaultValue
            VariableType.LIST -> {
                if (defaultValue.isBlank()) {
                    mutableListOf<String>()
                } else {
                    defaultValue.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                }
            }
        }
    }
}

// ==========================================================
// STORY PROJECT
// ==========================================================

data class StoryProject(
    var id: String = generateUniqueNewStoryId(),
    var name: String = id,
    var author: String = "Creator",
    var description: String = "",
    var version: String = "1.0.0",
    var activeSceneId: String = "",
    var prerequisites: StoryPrerequisites = StoryPrerequisites(),
    val scenes: MutableList<SceneData> = mutableListOf(),
    val sceneConnections: MutableList<ConnectionData> = mutableListOf(),
    val globalNodes: MutableList<NodeData> = mutableListOf(),
    val variables: MutableList<StoryVariable> = mutableListOf(),
    var isFolderPack: Boolean = false,
    var packDirectory: File? = null,
    var isReadOnly: Boolean = false,
    var sourceZipFile: File? = null
) {
    fun getActiveScene(): SceneData? {
        return scenes.find { it.id == activeSceneId } ?: scenes.firstOrNull()
    }

    fun getAllNodes(): List<NodeData> {
        return scenes.flatMap { it.nodes } + globalNodes
    }

    companion object {
        fun createNew(): StoryProject {
            val proj = StoryProject()
            val initialScene = SceneData.createWithStartNode(
                title = "Initial Scene",
                isStartScene = true,
                x = 0.0,
                y = 0.0,
                width = 500.0,
                height = 350.0
            )
            proj.scenes.add(initialScene)
            proj.activeSceneId = initialScene.id
            return proj
        }

        fun generateUniqueNewStoryId(): String {
            val dir = File("cobblebrain/storypacks")
            if (!dir.exists()) dir.mkdirs()

            val baseName = "new_story"
            if (!File(dir, "$baseName.json").exists()) return baseName

            var index = 1
            while (File(dir, "$baseName($index).json").exists()) {
                index++
            }
            return "$baseName($index)"
        }
    }
}
