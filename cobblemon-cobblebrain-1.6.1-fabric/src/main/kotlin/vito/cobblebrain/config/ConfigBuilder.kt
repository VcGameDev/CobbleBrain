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

        val comments = mapOf(
            "\"apiKey\"" to "\n// API key used for authentication with the AI system.\n// For OpenAI‑compatible: Bearer token.\n// For Google AI Studio: Google API key.",
            "\"apiBaseUrl\"" to "\n// Base URL of the API.\n// Examples:\n//  - OpenAI: https://api.openai.com\n//  - OpenRouter: https://openrouter.ai/api\n//  - Google AI Studio (Gemma/Gemini): https://generativelanguage.googleapis.com\n//  - Lm studio: http://localhost:1234",
            "\"aiModel\"" to "\n// Name of the AI model.\n// Examples:\n//  - Gemini (Google): gemini-2.5-flash\n//  - Gemma (Google): gemma-3-12b-it\n//  - OpenAI: gpt-4.1-mini\n//  - OpenRouter: anthropic/claude-3.5-sonnet",
            "\"temperature\"" to "\n/** Controls the randomness/creativity of the model's responses.\n * Range: 0.0 (deterministic, repetitive) to 2.0 (very creative, unpredictable).\n * Recommended values:\n *  - 0.0–0.3 → factual, precise answers.\n *  - 0.7–1.0 → balanced, natural conversation.\n *  - 1.2+ → highly creative or exploratory outputs.\n */",
            "\"aiProvider\"" to "\n/** Provider hint for routing in OpenRouter.\n * Example: \"DeepInfra\", \"OpenAI\", \"Anthropic\", etc.\n * Ignored for Google AI Studio.\n */",
            "\"reasoningEffort\"" to "\n/** Reasoning effort for models that support it.\n * Accepted values: \"high\", \"medium\", \"low\", \"auto\", \"none\".\n * \"none\" disables the reasoning block.\n */",
            "\"debugLogging\"" to "\n// Enables debug logging, logs are stored in cobblebrain-ai/logs.",
            "\"dialogueInChat\"" to "\n// Shows dialogue in chat.",
            "\"chatbubbles\"" to "\n// Enables chat bubbles.",
            "\"pokemonTalk\"" to "\n// Determines if Pokémon can talk or hear (basically an on/off switch of the mod).",
            "\"allowPokemonPVP\"" to "\n// Determines whether your Pokémon can attack other players' Pokémon.",
            "\"allowPokemonPVE\"" to "\n// Determines whether your Pokémon can attack mobs (except Pokémon, tamed mobs, and non‑aggressive mobs with a tag).",
            "\"lowTokenMode\"" to "\n// When active, it omits some world information to use fewer tokens.",
            "\"dialogueOnDamage\"" to "\n// Determines if Pokémon talk when someone is hurt.",
            "\"dialogueOnBattle\"" to "\n// Determines whether Pokémon speak when something related to battle happens.",
            "\"spontaneousDialogueChance\"" to "\n// Chance for the AI to start spontaneous dialogue (e.g., Pokémon speaking on their own during idle moments).",
            "\"requestTimeoutSeconds\"" to "\n// Request timeout in seconds (local models may need longer).",
            "\"listenToChat\"" to "\n// Enables or disables listening to regular player chat.\n// If false, the AI ignores all non‑command messages (like normal chat).",
            "\"onlyNearbyChat\"" to "\n// EXPERIMENTAL: If true, the AI only listens to chat messages from players who are nearby.\n// Only applies if listenToChat is also true.",
            "\"maxShortMemory\"" to "\n// Maximum short memory size of each pokemon.",
            "\"maxLongMemory\"" to "\n// Maximum long memory size of each pokemon.",
            "\"selectedLanguage\"" to "\n// The language selected for the AI to respond.",
            "\"decreaseFriendship\"" to "\n// Defines whether the dialogue decreases the Pokémon's friendship with the players.",
            "\"increaseFriendship\"" to "\n// Defines whether the dialogue increases the Pokémon's friendship with the players.",
            "\"showFriendship\"" to "\n// Defines whether friendship is shown in chat.",
            "\"instruct\"" to "\n// Instructions for the AI to generate dialogue.\n// It is NOT recommended to change the output format; doing so may break the mod.",
            "\"keyRotation\"" to "\n// Enable or disable API key rotation when trigger errors occur.",
            "\"modelRotation\"" to "\n// Enable or disable model rotation when trigger errors occur.",
            "\"keyRotationTrigger\"" to "\n// List of HTTP status codes that trigger API key rotation.",
            "\"modelRotationTrigger\"" to "\n// List of HTTP status codes that trigger model rotation."
        )

        comments.forEach { (field, comment) ->
            commentedJson = commentedJson.replace(field, "$comment\n  $field")
        }

        PrintWriter(file).use { pw ->
            pw.print(commentedJson)
        }
    }
}
