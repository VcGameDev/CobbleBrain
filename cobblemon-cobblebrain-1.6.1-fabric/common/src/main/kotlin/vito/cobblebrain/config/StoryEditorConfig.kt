package vito.cobblebrain.config

import com.google.gson.GsonBuilder
import net.minecraft.client.Minecraft
import java.io.File

data class StoryEditorConfigData(
    var autoSaveEnabled: Boolean = true,
    var autoSaveIntervalSeconds: Int = 600,
    var autoOpenLastProject: Boolean = false,
    var lastProjectPath: String = ""
)

object StoryEditorConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private fun getConfigFile(): File {
        val gameDir = Minecraft.getInstance().gameDirectory
        val dir = File(gameDir, "config/cobblebrain")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "story_editor_config.json")
    }

    fun load(): StoryEditorConfigData {
        val file = getConfigFile()
        if (!file.exists()) return StoryEditorConfigData()
        return try {
            file.reader().use { reader ->
                gson.fromJson(reader, StoryEditorConfigData::class.java) ?: StoryEditorConfigData()
            }
        } catch (e: Exception) {
            StoryEditorConfigData()
        }
    }

    fun save(data: StoryEditorConfigData) {
        try {
            val file = getConfigFile()
            file.writer().use { writer ->
                gson.toJson(data, writer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
