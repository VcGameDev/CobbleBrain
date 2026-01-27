package vito.cobblebrain.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.PrintWriter

class ConfigBuilder<T> private constructor(
    private val clazz: Class<T>,
    private val path: String
) {
    companion object {
        fun <T> load(clazz: Class<T>, path: String): T {
            return ConfigBuilder(clazz, path)._load()
        }
    }

    fun _load(): T {
        val gson = GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create()

        val configFile = File("config/$path.json5")
        configFile.parentFile.mkdirs()

        // objeto default
        val defaultConfig = clazz.getDeclaredConstructor().newInstance()
        val defaultJson = JsonParser.parseString(gson.toJson(defaultConfig)).asJsonObject

        var userJson: JsonObject? = null

        if (configFile.exists()) {
            try {
                val text = configFile.readText()
                // cuidado: regex simples pode apagar valores dentro de strings
                val cleanText = text.replace(Regex("""^\s*//.*$""", RegexOption.MULTILINE), "")
                userJson = JsonParser.parseString(cleanText).asJsonObject
            } catch (e: Exception) {
                println("Error reading config file: ${e.message}")
            }
        }

        if (userJson == null) {
            // não existe → cria com defaults
            userJson = defaultJson.deepCopy()
            writeFile(configFile, gson, userJson)
        } else {
            // existe → mantém valores do jogador e adiciona apenas os novos
            var changed = false
            for ((key, value) in defaultJson.entrySet()) {
                if (!userJson.has(key)) {
                    userJson.add(key, value)
                    changed = true
                }
            }
            // só escreve se houve mudança
            if (changed) {
                writeFile(configFile, gson, userJson)
            }
        }

        return gson.fromJson(userJson, clazz)
    }

    private fun writeFile(file: File, gson: Gson, json: JsonObject) {
        var commentedJson = gson.toJson(json)

        // depois de gerar o JSON com gson.toJson(...)
        commentedJson = commentedJson.replace(
            Regex("""\[\s*([\s\S]*?)\s*]""")
        ) { match ->
            val conteudo = match.groupValues[1]
                .replace(Regex("""\s+"""), "") // tira espaços e quebras
            "[${conteudo}]"
        }

        val comments = mapOf(
            //========================= // AI CONFIGURATION // =========================
            "\"apiKey\"" to "//========================= // AI CONFIGURATION // =========================\n\n\n// The API key used for authentication with the AI system. It can be a Bearer token or a Google API key depending on the provider.",
            "\"apiBaseUrl\"" to "\n// The base URL of the API endpoint. Examples include OpenAI, OpenRouter, Google AI Studio, or a local LM Studio server.",
            "\"aiModel\"" to "\n// The names of the AI models to use. Examples are gemini-2.5-flash, gemma-3-12b-it, or gpt-4.1-mini.",
            "\"temperature\"" to "\n// Controls the randomness of responses. Lower values give precise answers, higher values make them more creative.",
            "\"aiProvider\"" to "\n// A provider hint used for routing in OpenRouter. This is ignored when using Google AI Studio.",
            "\"reasoningEffort\"" to "\n// Defines the reasoning effort level for supported models. Options include high, medium, low, auto, or none.",
            "\"debugLogging\"" to "\n// Enables debug logging for troubleshooting. Logs are stored in the cobblebrain-ai/logs directory.",
            "\"localApiProvider\"" to "\n// If apiBaseUrl is a local address (127.0.0.1), the system uses the provider name in localApiProvider to adapt messages for the correct provider... (Officially supported local APIs: player2, lmstudio)",
            //"" to "\n\n//========================= // GAME AND INTERACTIONS // =========================\n\n",
            "\"dialogueInChat\"" to "\n\n//========================= // GAME AND INTERACTIONS // =========================\n\n\n// Shows generated dialogue directly in the chat. This makes Pokémon conversations visible to players.",
            "\"chatbubbles\"" to "\n// Enables chat bubbles above characters. Dialogue will appear visually instead of only in text chat.",
            "\"pokemonTalk\"" to "\n// Toggles whether Pokémon can talk or listen. This acts as a simple on/off switch for dialogue.",
            "\"allowPokemonPVP\"" to "\n// Allows Pokémon to attack other players’ Pokémon. Disabling prevents player-versus-player battles.",
            "\"allowPokemonPVE\"" to "\n// Allows Pokémon to attack mobs in the world. Exceptions include tamed mobs and non-aggressive tagged mobs.",
            "\"lowTokenMode\"" to "\n// Reduces world information sent to the AI. This helps conserve tokens and lower usage costs.",
            "\"dialogueOnDamage\"" to "\n// Makes Pokémon speak when someone is hurt. Dialogue is triggered by damage events.",
            "\"dialogueOnBattle\"" to "\n// Makes Pokémon speak during battle events. Dialogue reflects combat situations.",
            "\"spontaneousDialogueChance\"" to "\n// Sets the chance of spontaneous dialogue. Pokémon may speak randomly during idle moments.",
            "\"requestTimeoutSeconds\"" to "\n// Defines the request timeout in seconds. Local models may require longer values.",
            "\"listenToChat\"" to "\n// Enables listening to regular player chat. If disabled, the AI ignores non-command messages.",
            "\"onlyNearbyChat\"" to "\n// Restricts listening to nearby players only. Works only if listenToChat is enabled.",
            "\"maxShortMemory\"" to "\n// Maximum short-term memory size per Pokémon. Controls how much recent context is stored.",
            "\"maxLongMemory\"" to "\n// Maximum long-term memory size per Pokémon. Controls how much persistent context is stored.",
            "\"selectedLanguage\"" to "\n// The language the AI uses for responses. Determines dialogue output language.",
            "\"decreaseFriendship\"" to "\n// Dialogue can decrease friendship with players. Used for negative interactions.",
            "\"increaseFriendship\"" to "\n// Dialogue can increase friendship with players. Used for positive interactions.",
            "\"showFriendship\"" to "\n// Displays friendship values in chat. Players can see relationship changes.",
            "\"instruct\"" to "\n\n//========================= // PROMPT AND OUTPUT // =========================\n\n\n// Instructions for generating dialogue.",
            //"" to "\n\n\n//========================= // PROMPT AND OUTPUT // =========================\n\n",
            "\"outputFormat\"" to "\n// System Instructions for the AI model. Do not change the output format to avoid breaking the mod.",
            "\"keyRotation\"" to "\n// Enables API key rotation when errors occur. Useful for handling invalid or expired keys.",
            "\"modelRotation\"" to "\n// Enables model rotation when errors occur. Useful for fallback to alternative models.",
            "\"keyRotationTrigger\"" to "\n// List of HTTP status codes that trigger key rotation. Defines error conditions for switching keys.",
            "\"modelRotationTrigger\"" to "\n// List of HTTP status codes that trigger model rotation. Defines error conditions for switching models."
        )

        comments.forEach { (field, comment) ->
            commentedJson = commentedJson.replace(field, "$comment\n  $field")
        }

        PrintWriter(file).use { pw ->
            pw.print(commentedJson)
        }
    }
}
