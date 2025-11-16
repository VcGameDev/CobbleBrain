package vito.cobblebrain.config

import com.google.gson.GsonBuilder
import java.io.File

class ConfigBuilder<T> private constructor(
    private val clazz: Class<T>,
    private val path: String
) {
    companion object {
        fun <T> load(clazz: Class<T>, path: String): T {
            val gson = GsonBuilder()
                .disableHtmlEscaping()
                .setPrettyPrinting()
                .create()

            val file = File("config/$path.json").apply { parentFile.mkdirs() }

            val config = if (file.exists()) {
                try {
                    gson.fromJson(file.readText(), clazz)
                } catch (e: Exception) {
                    println("Erro ao ler config, usando padrão")
                    clazz.getDeclaredConstructor().newInstance()
                }
            } else {
                val defaults = clazz.getDeclaredConstructor().newInstance()
                file.writeText(gson.toJson(defaults))
                defaults
            }

            return config
        }
    }

    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    private val configFile = File("config/$path.json").apply { parentFile.mkdirs() }

    // inicializado dentro do load(), sem lateinit
    var config: T? = null
        private set

    private fun _load(): T {
        return if (configFile.exists()) {
            try {
                gson.fromJson(configFile.readText(), clazz)
            } catch (e: Exception) {
                println("Error reading config file, using defaults")
                clazz.getDeclaredConstructor().newInstance()
            }
        } else {
            val defaultConfig = clazz.getDeclaredConstructor().newInstance()
            update(defaultConfig)
            defaultConfig
        }
    }

    fun update(config: T) {
        configFile.writeText(gson.toJson(config))
        this.config = config
    }
}
