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
            "\"apiKey\"" to "//========================= // AI CONFIGURATION // =========================\n\n\n// The API key used for authentication with the AI system. It can be a Bearer token or a Google API key depending on the provider.",
            "\"apiBaseUrl\"" to "\n// The base URL of the API endpoint. Examples include OpenAI, OpenRouter, Google AI Studio, Player2, or a local LM Studio server.",
            "\"useChatEndpoint\"" to "\n// Automatically appends the standard Chat Completions endpoint to the API Base URL. Disable it if your provider already includes the full endpoint.",
            "\"apiModel\"" to "\n// The names of the AI models to use. Examples are gemini-2.5-flash, gemma-3-12b-it, or gpt-4.1-mini.",
            "\"temperature\"" to "\n// Controls the randomness of responses. Lower values are more deterministic, higher values are more creative.",
            "\"aiProvider\"" to "\n// Optional provider hint for OpenRouter routing. Ignored by other providers.",
            "\"reasoningEffort\"" to "\n// Defines the reasoning effort for supported models. Options include high, medium, low, auto or none.",
            "\"requestTimeoutSeconds\"" to "\n// Maximum time to wait for an AI response before cancelling the request.",
            "\"debugLogging\"" to "\n// Enables debug logging. Logs are stored in the cobblebrain-ai/logs folder.",
            "\"localApiProvider\"" to "\n// If apiBaseUrl points to a local server (127.0.0.1), this tells CobbleBrain how to format requests. Supported: player2, lmstudio.",
            "\"selectedLanguage\"" to "\n// Language used for Pokémon dialogue and AI responses.",
            "\"preferredName\"" to "\n// Preferred name Pokémon will use instead of your Minecraft username.",
            "\"offlineMode\"" to "\n// Enables Offline Mode. Pokémon responses are generated locally without internet access.",
            "\"offlineTalkMode\"" to "\n// Selects the offline dialogue generator to use.",
            "\"psychicTranslation\"" to "\n// Allows Psychic-type Pokémon to naturally speak human language and occasionally interpret other Pokémon.",
            "\"dialogueInChat\"" to "\n\n//========================= // GAME AND INTERACTIONS // =========================\n\n\n// Shows generated dialogue directly in chat.",
            "\"chatbubbles\"" to "\n// Displays dialogue above Pokémon as chat bubbles.",
            "\"forceOfflineMode\"" to "\n// Forces Offline Mode for every player on this server. Clients cannot disable it while connected.",
            "\"disableWelcomeMessage\"" to "\n// Disables the CobbleBrain welcome message shown when players join.",
            "\"pokemonTalk\"" to "\n// Enables Pokémon dialogue.",
            "\"needsPokemonTranslator\"" to "\n// Requires an Exp. Share to understand Pokémon. Otherwise they speak using creature vocalizations.",
            "\"allowPokemonPVP\"" to "\n// Allows Pokémon to battle other players' Pokémon.",
            "\"allowPokemonPVE\"" to "\n// Allows Pokémon to attack hostile mobs.",
            "\"enableKarma\"" to "\n// Enables karma gain/loss and karma-based mechanics.",
            "\"scheduleRaids\"" to "\n// Enables karma raids when your relationship with a species becomes very negative.",
            "\"lowTokenMode\"" to "\n// Sends less world information to the AI, reducing token usage and response cost.",
            "\"dialogueOnDamage\"" to "\n// Pokémon may comment when entities take damage.",
            "\"dialogueOnBattle\"" to "\n// Pokémon may speak during battles.",
            "\"spontaneousDialogueChance\"" to "\n// Chance for Pokémon to start spontaneous conversations.",
            "\"wildPokemonTalkChance\"" to "\n// Chance for nearby wild Pokémon to join an existing conversation.",
            "\"wildQuestChance\"" to "\n// Chance for a wild Pokémon to naturally start a STORY, SECONDARY or ADVICE quest.",
            "\"listenToChat\"" to "\n// Allows regular (non-command) chat messages to trigger AI responses.",
            "\"onlyNearbyChat\"" to "\n// Only nearby chat messages are considered when Listen To Chat is enabled.",
            "\"characteristics\"" to "\n// Defines custom personality notes for individual Pokémon.",
            "\"maxInteractionSaves\"" to "\n// Number of recent interaction summaries kept for conversation continuity.",
            "\"maxStoredMemories\"" to "\n// Maximum number of stored memories per Pokémon.",
            "\"maxRelevantMemories\"" to "\n// Maximum relevant memories retrieved for each prompt.",
            "\"decreaseFriendship\"" to "\n// Allows dialogue to decrease friendship.",
            "\"increaseFriendship\"" to "\n// Allows dialogue to increase friendship.",
            "\"showFriendship\"" to "\n// Displays friendship changes in chat.",
            "\"instruct\"" to "\n\n//========================= // PROMPT AND OUTPUT // =========================\n\n\n// Custom instructions sent to the AI before every conversation.",
            "\"outputFormat\"" to "\n// Custom AI output format. Only edit if you know how CobbleBrain parses responses.",
            "\"useDefaultOutput\"" to "\n// Uses the recommended output format bundled with this version of CobbleBrain.",
            "\"keyRotation\"" to "\n// Automatically switches API keys when configured errors occur.",
            "\"modelRotation\"" to "\n// Automatically switches AI models when configured errors occur.",
            "\"keyRotationTrigger\"" to "\n// HTTP status codes that trigger API key rotation.",
            "\"modelRotationTrigger\"" to "\n// HTTP status codes that trigger model rotation.",
            "\"outputDialogue\"" to "\n// Enables dialogue generation.",
            "\"outputActions\"" to "\n// Enables AI-controlled Pokémon actions.",
            "\"outputFriendship\"" to "\n// Enables friendship changes.",
            "\"outputMemories\"" to "\n// Enables Pokémon memories.",
            "\"outputQuests\"" to "\n// Enables the quest system.",
            "\"outputPokemonLanguage\"" to "\n// Makes Pokémon communicate using creature vocalizations instead of human language.",
            "\"outputGuaranteedCatch\"" to "\n// Enables the Guaranteed Catch mechanic after exceptional interactions."
        )


        comments.forEach { (field, comment) ->
            commentedJson = commentedJson.replace(field, "$comment\n  $field")
        }

        PrintWriter(file).use { pw ->
            pw.print(commentedJson)
        }
    }
}

object ConfigHandler {
    // instância atual da config
    lateinit var config: CobblebrainConfig

    fun load() {
        config = ConfigBuilder.load(CobblebrainConfig::class.java, "cobblebrain")
    }

    fun save() {
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val json = JsonParser.parseString(gson.toJson(config)).asJsonObject
        val file = File("config/cobblebrain.json5")
        PrintWriter(file).use { pw ->
            pw.print(gson.toJson(json))
        }
    }
}

object ClientConfigHandler {
    lateinit var clientConfig: CobblebrainClientConfig

    fun load() {
        clientConfig = ConfigBuilder.load(CobblebrainClientConfig::class.java, "cobblebrain_client")
    }

    fun save() {
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val json = JsonParser.parseString(gson.toJson(clientConfig)).asJsonObject
        val file = File("config/cobblebrain_client.json5")
        PrintWriter(file).use { pw ->
            pw.print(gson.toJson(json))
        }
    }
}
