import com.google.gson.GsonBuilder
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

        var config = gson.fromJson("{}", clazz)
        val configFile = File("config/$path.json5")
        configFile.parentFile.mkdirs()

        if (configFile.exists()) {
            try {
                // remove comentários antes de passar para o Gson
                val text = configFile.readText()
                val cleanText = text.replace(Regex("//.*"), "")
                config = gson.fromJson(cleanText, clazz)
            } catch (e: Exception) {
                println("Error reading config file: ${e.message}")
            }
        } else {
            // só cria se não existir
            val json = gson.toJson(config)

            // Mapa de comentários (copiados do CobblebrainConfig.kt)
            val comments = mapOf(
                "\"apiKey\"" to "\n// API key used for authentication with the AI system.\n// For OpenAI‑compatible: Bearer token.\n// For Google AI Studio: Google API key.",
                "\"apiBaseUrl\"" to "\n// Base URL of the API.\n// Examples:\n//  - OpenAI: https://api.openai.com\n//  - OpenRouter: https://openrouter.ai/api\n//  - Google AI Studio (Gemma/Gemini): https://generativelanguage.googleapis.com\n//  - Lm studio: http://localhost:1234",
                "\"aiModel\"" to "\n// Name of the AI model.\n// Examples:\n//  - Gemini (Google): gemini-2.5-flash\n//  - Gemma (Google): gemma-3-12b-it\n//  - OpenAI: gpt-4.1-mini\n//  - OpenRouter: anthropic/claude-3.5-sonnet",
                "\"temperature\"" to "\n/** Temperature sent to the model (0.0 … 2.0). */",
                "\"aiProvider\"" to "\n/** Provider hint for routing in OpenRouter.\n * Example: \"DeepInfra\", \"OpenAI\", \"Anthropic\", etc.\n * Ignored for Google AI Studio.\n */",
                "\"reasoningEffort\"" to "\n/** Reasoning effort for models that support it.\n * Accepted values: \"high\", \"medium\", \"low\", \"auto\", \"none\".\n * \"none\" disables the reasoning block.\n */",
                "\"debugLogging\"" to "\n// Enables debug logging.",
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
                "\"maxShortMemory\"" to "\n// Maximum short memory size.",
                "\"maxLongMemory\"" to "\n// Maximum long memory size.",
                "\"selectedLanguage\"" to "\n// The language selected for the AI to respond.",
                "\"decreaseFriendship\"" to "\n// Defines whether the dialogue decreases the Pokémon's friendship with the players.",
                "\"increaseFriendship\"" to "\n// Defines whether the dialogue increases the Pokémon's friendship with the players.",
                "\"showFriendship\"" to "\n// Defines whether friendship is shown.",
                "\"instruct\"" to "\n// Instructions for the AI to generate dialogue.\n// It is NOT recommended to change the output format; doing so may break the mod."
            )

            // Insere comentários antes das variáveis
            var commentedJson = json
            comments.forEach { (field, comment) ->
                commentedJson = commentedJson.replace(field, "$comment\n  $field")
            }

            val pw = PrintWriter(configFile)
            pw.print(commentedJson)
            pw.close()
        }

        return config
    }
}