package vito.cobblebrain.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

object StorySerializer {
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    val storageDir: File
        get() {
            val dir = File("cobblebrain-ai/storypacks")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun save(project: StoryProject, fileName: String = "${project.name.replace(" ", "_").lowercase()}.json"): File? {
        return try {
            val safeFileName = if (fileName.endsWith(".json")) fileName else "$fileName.json"
            val file = File(storageDir, safeFileName)
            val json = gson.toJson(project)
            file.writeText(json)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun load(file: File): StoryProject? {
        return try {
            if (!file.exists()) return null
            val json = file.readText()
            gson.fromJson(json, StoryProject::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadByName(fileName: String): StoryProject? {
        val safeFileName = if (fileName.endsWith(".json")) fileName else "$fileName.json"
        val file = File(storageDir, safeFileName)
        return load(file)
    }

    fun listStoryPacks(): List<File> {
        val dir = storageDir
        return dir.listFiles { _, name -> name.endsWith(".json") }?.toList() ?: emptyList()
    }

    fun toJson(project: StoryProject): String {
        return gson.toJson(project)
    }

    fun fromJson(json: String): StoryProject? {
        return try {
            gson.fromJson(json, StoryProject::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
