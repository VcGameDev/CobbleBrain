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
                "\"apiKey\"" to "// API key used for authentication with the AI system.\n// For OpenAI‑compatible: Bearer token.\n// For Google AI Studio: Google API key.",
                "\"apiBaseUrl\"" to "// Base URL of the API.\n// Examples:\n//  - OpenAI: https://api.openai.com\n//  - OpenRouter: https://openrouter.ai/api\n//  - Google AI Studio (Gemma/Gemini): https://generativelanguage.googleapis.com",
                "\"aiModel\"" to "// Name of the AI model.\n// Examples:\n//  - Gemini: gemini-1.5-pro\n//  - Gemma: gemma-7b-it\n//  - OpenAI: gpt-4.1-mini\n//  - OpenRouter: anthropic/claude-3.5-sonnet",
                "\"temperature\"" to "/** Temperature sent to the model (0.0 … 2.0). */",
                "\"aiProvider\"" to "/** Provider hint for routing in OpenRouter.\n * Example: \"DeepInfra\", \"OpenAI\", \"Anthropic\", etc.\n * Ignored for Google AI Studio.\n */",
                "\"reasoningEffort\"" to "/** Reasoning effort for models that support it.\n * Accepted values: \"high\", \"medium\", \"low\", \"auto\", \"none\".\n * \"none\" disables the reasoning block.\n */",
                "\"debugLogging\"" to "// Enables debug logging.",
                "\"dialogueInChat\"" to "// Shows dialogue in chat.",
                "\"chatbubbles\"" to "// Enables chat bubbles.",
                "\"pokemonTalk\"" to "// Determines if Pokémon can talk or hear (basically an on/off switch of the mod).",
                "\"allowPokemonPVP\"" to "// Determines whether your Pokémon can attack other players' Pokémon.",
                "\"allowPokemonPVE\"" to "// Determines whether your Pokémon can attack mobs (except Pokémon, tamed mobs, and non‑aggressive mobs with a tag).",
                "\"lowTokenMode\"" to "// When active, it omits some world information to use fewer tokens.",
                "\"dialogueOnDamage\"" to "// Determines if Pokémon talk when someone is hurt.",
                "\"dialogueOnBattle\"" to "// Determines whether Pokémon speak when something related to battle happens.",
                "\"spontaneousDialogueChance\"" to "// Chance for the AI to start spontaneous dialogue (e.g., Pokémon speaking on their own during idle moments).",
                "\"requestTimeoutSeconds\"" to "// Request timeout in seconds (local models may need longer).",
                "\"listenToChat\"" to "// Enables or disables listening to regular player chat.\n// If false, the AI ignores all non‑command messages (like normal chat).",
                "\"onlyNearbyChat\"" to "// EXPERIMENTAL: If true, the AI only listens to chat messages from players who are nearby.\n// Only applies if listenToChat is also true.",
                "\"maxShortMemory\"" to "// Maximum short memory size.",
                "\"maxLongMemory\"" to "// Maximum long memory size.",
                "\"selectedLanguage\"" to "// The language selected for the AI to respond.",
                "\"decreaseFriendship\"" to "// Defines whether the dialogue decreases the Pokémon's friendship with the players.",
                "\"increaseFriendship\"" to "// Defines whether the dialogue increases the Pokémon's friendship with the players.",
                "\"showFriendship\"" to "// Defines whether friendship is shown.",
                "\"instruct\"" to "// Instructions for the AI to generate dialogue.\n// It is NOT recommended to change the output format; doing so may break the mod."
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