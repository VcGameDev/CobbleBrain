package vito.cobblebrain.model

import java.io.File
import java.util.UUID

data class StoryProject(
    var id: String = generateUniqueNewStoryId(),
    var name: String = id,
    var description: String = "",
    var version: String = "1.0.0",
    var activeSceneId: String = "",
    val scenes: MutableList<SceneData> = mutableListOf(),
    val sceneConnections: MutableList<ConnectionData> = mutableListOf()
) {
    init {
        if (scenes.isEmpty()) {
            val defaultScene = SceneData(title = "Cena Inicial")
            scenes.add(defaultScene)
            activeSceneId = defaultScene.id
        } else if (activeSceneId.isEmpty()) {
            activeSceneId = scenes.firstOrNull()?.id ?: ""
        }
    }

    fun getActiveScene(): SceneData? {
        return scenes.find { it.id == activeSceneId } ?: scenes.firstOrNull()
    }

    companion object {
        fun generateUniqueNewStoryId(): String {
            val dir = File("cobblebrain-ai/storypacks")
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
