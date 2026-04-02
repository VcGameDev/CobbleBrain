package vito.cobblebrain.client

import com.google.gson.Gson
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import vito.cobblebrain.config.ClientConfigHandler.clientConfig
import vito.cobblebrain.config.SyncedConfig
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.*
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.net.InetAddress
import java.net.http.HttpTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.get

//object KeyManager {
//    private val configFile = File("config/cobblebrain.json5")
//    private val config = Gson().fromJson(configFile.readText(), CobblebrainConfig::class.java)

//    val rotator = ApiKeyRotator(config.apiKey)
//}

// ------------------------------------------------------------
// DATA CLASSES
// ------------------------------------------------------------
data class Mensagem(val role: String, val text: String)

class ApiKeyRotator(private val keys: List<String>) {
    private var index = 0
    fun current(): String = keys[index]
    fun next() { index = (index + 1) % keys.size }
}

class ModelRotator(private val models: List<String>) {
    private var index = 0
    fun current(): String = models[index]
    fun next() { index = (index + 1) % models.size }
}

class AIHandler {
    companion object {
        private val sessionLogFile: Path by lazy {
            val dir = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("cobblebrain-ai/logs")

            Files.createDirectories(dir)

            val fileName = "session_${
                LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            }.log"

            dir.resolve(fileName)
        }

        private val gson = Gson()
        private val INSTRUCTS get() = clientConfig.instruct
            .filterNotNull()
            .joinToString("\n")
        private val TEMPERATURE get() = clientConfig.temperature
        private val PROVIDER_HINT get() = clientConfig.aiProvider.trim()
        private val REASONING get() = clientConfig.reasoningEffort.trim().lowercase()
        private val DEBUG get() = clientConfig.debugLogging
        private val TIMEOUT_SECONDS get() = clientConfig.requestTimeoutSeconds
        const val HEADER = """
        ##OUTPUT FORMAT##
        You must generate your entire response following these STRICT rules:
        """

        const val DIALOGUE = """
        DIALOGUE FORMAT
        - Each dialogue line MUST follow this format:
        <PokemonName>: <message>
        - Use pipes (|) and the Pokémon name to separate dialogue lines.
        - Each line must have 1-2 sentences.
        - If 1 Pokémon active → max 4 lines total.
        - If 2–5 Pokémon active → max 6 lines total.
        - If 6 Pokémon active → max 7 lines total.
        - Each dialogue line MUST start with the Pokémon name. The name must be repeated on every new line, even if it is the same speaker.
        - If Wild pokemon are talking, make them talk in the dialogues too.
        """

        const val FRIENDSHIP = """
        FRIENDSHIP FORMAT
        - Each friendship line MUST follow this format:
          Friendship <PokemonName>: <current_value> + <change>
          Friendship <PokemonName>: <current_value> - <change>
        - If AFFECT_FRIENDSHIP_PLUS = true → increase friendship (min +1, max +5).
        - If AFFECT_FRIENDSHIP_MINUS = true → decrease friendship (min -1, max -5).
        - If both true → decide based on positive or negative impact.
        - A Pokémon's friendship doesn't change more than once in the same dialogue
        """

        const val MEMORY = """
        MEMORY FORMAT
        - Each memory line MUST follow this format:
          @<PokemonName>: <short memory sentence>
          @@<PokemonName>: <core memory sentence>
        - Use @ for short memory, @@ for core memory.
        - Each Pokémon records events from its own perspective.
        - Short memories = fleeting perceptions; Core memories = impactful events.
        - Memories MUST be written from the perspective of a third-person narrator, describing what happens to the Pokémon
        - Do not generate memory lines EVERY response. Only generate memories when something meaningful or new happens.
        - Memories should not appear in the dialogue
        - Memories function as optional context and should only be used when they meaningfully improve the response.
        """

        const val ACTION = """
        ACTION FORMAT
        - Each action line MUST follow this format:
          #<PokemonName>: <action>
        - At the very end, output one action per Pokémon.
        - Use exactly one of:
          #PokemonName: attack
          #PokemonName: eat
          #PokemonName: buff
          #PokemonName: debuff enemy
          #PokemonName: sit
          #PokemonName: protect
          #PokemonName: idle
          (fire type) #PokemonName: cook
          (steel type) #PokemonName: repair
          (grass type) #PokemonName: grow
          (ghost type) #PokemonName: shift
        - If no action is needed, ALWAYS use idle.
        """

        const val APRIL1 = """
        The april1 mode is active, so, you can also use one of these actions following the ACTION FORMAT:
        (fire type) #PokemonName: fireball machine
        (fire type) #PokemonName: nuke
        (psychic type) #PokemonName: psychic stand
        (fairy type) #PokemonName: imaginary technique
        (flying type) #PokemonName: final judgment
        #PokemonName: ssstyle
        """

        const val QUEST = """
        QUEST FORMAT:
        - Only create a quest when you receive IMPORTANT: <PokemonName> has started an <QuestType> quest!
        - In that case, generate dialogue where the Wild Pokémon asks the player or their team to complete it.
        - From the moment the quest is created until it is completed, you must ALWAYS add one of the following lines in your response (all must begin with %):
          %CONTINUE → Quest is ongoing or lacks enough interaction/reason to end.
          %POSITIVE_END → Quest ends with a positive, satisfying outcome for the Pokémon.
          %NEGATIVE_END → Quest ends with a negative, unsatisfying outcome for the Pokémon.
          %LEAVE_END → Pokémon decides to leave the mission.
        - Delivery Quests:
          Only end the quest if you receive QUEST_COMPLETED.
          Then choose the appropriate ending marker based on interactions (except LEAVE_END).
        - Advice Quests:
          You decide when the mission ends, based on the Pokémon's personality and whether the answer solved the problem/question.
        - Hunt Quests:
          Same as Delivery Quests.
        - After sending the marker, create a small summary of the current quest reporting:
          1. Why the quest was created.
          2. The key events that happened.
          3. The Pokémon’s opinion about how the mission is progressing.
        - Keep it focused on helping the next AI continue the story.
        - Use a maximum of 6 sentences.
        - Format the summary exactly as: &<text>
        """

        const val RESUME = """
        RESUME FORMAT
        - At the end of the response, generate a short summary of the conversation.
        - Use the format: !RESUME: <summary text>
        - Describe what happened and the key emotions.
        - If needed, suggest a natural evolution of the topic without forcing it.
        - Maximum 6 sentences.
        """

        const val GENERAL = """
        GENERAL RULES
        1. Each line of dialogue must respect the sentences and line limits.
        2. Never mix nickname and species; use only one consistently.
        3. Do not invent characters outside Pokémon and the human player.
        4. if not specified in the prompt, the Pokémon should not talk to themselves or speak their thoughts
        5. Always follow the formats exactly; no hyphens or alternative separators.
        6. Friendship, memory, and action sections must appear in this order: Dialogue → Friendship → Memory → Action.
        7. If no action is relevant, always output idle.
        8. Dialogue, friendship, memory, and action content must integrate the [CREATIVEPROMPT] but never break format.
        9. Dialogue may use past memories when relevant, but must prioritize responding directly to the current situation or player input.
        
        10. Pokémon should actively engage the player by continuing thoughts, expressing feelings, and occasionally asking questions.
        11. The environment should influence behavior subtly, not be constantly described.
        12. Use the [LAST INTERACTIONS] section as context to maintain continuity.
        13. Avoid repeating ideas from the LAST INTERACTIONS. Instead, evolve the conversation naturally through feelings, reflections, or interaction, without abruptly changing the subject or pointing out unrelated elements.
        """
    }

    fun buildOutputFormat(
        dialogue: Boolean,
        actions: Boolean,
        friendship: Boolean,
        quests: Boolean,
        april1: Boolean,
        //worldContext: Boolean,
        //mobsContext: Boolean,
        //lastContext: Boolean,
        //blockSensors: Boolean,
        memories: Boolean
    ): String {

        val sections = mutableListOf<String>()

        sections += HEADER

        if (dialogue) sections += DIALOGUE
        if (friendship) sections += FRIENDSHIP
        if (memories) sections += MEMORY
        if (actions) sections += ACTION
        if (april1) sections += APRIL1
        if (quests) sections += QUEST

        //if (worldContext) sections += WORLD_CONTEXT
        //if (mobsContext) sections += MOBS_CONTEXT
        //if (lastContext) sections += LAST_CONTEXT
        //if (blockSensors) sections += BLOCK_SENSORS

        sections += RESUME
        sections += GENERAL

        return sections.joinToString("\n\n")
    }

    private fun getDefaultOutputFormat(): String {
        return buildOutputFormat(
            dialogue = SyncedConfig.outputDialogue,
            actions = SyncedConfig.outputActions,
            friendship = SyncedConfig.outputFriendship,
            quests = SyncedConfig.outputQuests,
            april1 = SyncedConfig.outputApril1,
            //worldContext = clientConfig.outputWorldContext,
            //mobsContext = clientConfig.outputMobsContext,
            //lastContext = clientConfig.outputLastContext,
            //blockSensors = clientConfig.outputBlockSensors,
            memories = SyncedConfig.outputMemories
        )
    }
    // agora usando rotadores
    private val apiKeyRotator = ApiKeyRotator(clientConfig.apiKey)
    private val modelRotator = ModelRotator(clientConfig.aiModel)

    private val apiBase =
        clientConfig.apiBaseUrl.trimEnd('/').replace("localhost", "127.0.0.1")

    fun isLocalAddress(url: String): Boolean {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return false

            val address = InetAddress.getByName(host)

            address.isAnyLocalAddress ||
                    address.isLoopbackAddress ||
                    address.isSiteLocalAddress
        } catch (e: Exception) {
            false
        }
    }

    // ---------------- Logging ----------------
    private fun log(text: String) {
        if (!DEBUG) return

        val line = "[${LocalDateTime.now()}] $text"
        println(line)

        try {
            Files.writeString(
                sessionLogFile,
                "$line\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------------- Conversation ----------------
    private val historico = mutableListOf<Mensagem>()
    private var lastPromptHash: String? = null

    // ------------------------------------------------------------
    fun start() {
        //val watchService = FileSystems.getDefault().newWatchService()
        //comandoPath.parent.register(
        //watchService,
        //StandardWatchEventKinds.ENTRY_MODIFY,
        //StandardWatchEventKinds.ENTRY_CREATE
        //)

        // Executor para rodar o pingHealth a cada 60s
        if (
            clientConfig.localApiProvider.equals("player2", ignoreCase = true)
            && isLocalAddress(clientConfig.apiBaseUrl)
        ) {
            val scheduler = Executors.newSingleThreadScheduledExecutor()
            scheduler.scheduleAtFixedRate({ pingHealth() }, 0, 60, TimeUnit.SECONDS)
        }

        //while (true) {
        //try {
        //val key = watchService.take()
        //for (event in key.pollEvents()) {
        //if ((event.context() as Path).endsWith(comandoPath.fileName)) {
        //Thread.sleep(60)
        //println("tentativa de processcommandfile")
        //processCommandFile()
        //}
        //}
        //key.reset()
        //} catch (e: Exception) {
        //Thread.sleep(200)
        //}
    }
    //}

    private fun pingHealth() {
        try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$apiBase/v1/health"))
                .header("player2-game-key", "019bfb65-9ed4-79af-a348-90d86bbb6cbb")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()

            val client = HttpClient.newHttpClient()
            val res = client.send(req, HttpResponse.BodyHandlers.ofString())

            if (res.statusCode() == 200) {
                log("Health OK → ${res.body()}")
                println("Health OK → ${res.body()}")
            } else {
                log("Health ping falhou: HTTP ${res.statusCode()} → ${res.body()}")
                println("Health ping falhou: HTTP ${res.statusCode()} → ${res.body()}")
            }
        } catch (e: Exception) {
            log("Erro no health ping: ${e.message}")
            println("Erro no health ping: ${e.message}")
        }
    }


    private fun rotateApiKey(status: Int) {
        if (clientConfig.keyRotation && status in clientConfig.keyRotationTrigger) {
            apiKeyRotator.next()
            if (apiKeyRotator.current() == clientConfig.apiKey.first()) {
                val msg =
                    "API key rotation has cycled through all keys and returned to the first key. First key: ${apiKeyRotator.current()}."
                sendSystemMessage(msg)
            } else {
                val msg = "API key has been rotated due to error $status. New key: ${apiKeyRotator.current()}."
                sendSystemMessage(msg)
            }
        }
    }

    private fun rotateModel(status: Int) {
        if (clientConfig.modelRotation && status in clientConfig.modelRotationTrigger) {
            modelRotator.next()
            println("func de rotate ativa")
            if (modelRotator.current() == clientConfig.aiModel.first()) {
                val msg =
                    "Model rotation has cycled through all models and returned to the first model. First model: ${modelRotator.current()} \n"
                sendSystemMessage(msg)
            } else {
                val msg = "Model has been rotated due to error $status. New model: ${modelRotator.current()} \n"
                sendSystemMessage(msg)
            }
        }
    }

    private fun sendSystemMessage(msg: String) {
        Minecraft.getInstance().player?.sendSystemMessage(Component.literal(msg))
        log("SYSTEM MESSAGE: $msg")
    }

    // ------------------------------------------------------------
    //private fun processCommandFile() {
    //if (!config.pokemonTalk) return

    //val fullText = Files.readString(comandoPath).trim()
    //println(comandoPath.fileName.toString())
    //if (fullText.isEmpty()) return

    // usa o prompt inteiro como base do hash
    //val hash = sha256(fullText)
    //if (hash == lastPromptHash) {
    // println("duplicata detectada")
    //return
    //}

    //lastPromptHash = hash

    // log detalhado mostrando início do prompt e hash
    //log("FULL PROMPT:\n${fullText.lines().joinToString("\n") { "│ $it" }}")
    //log("HASH BASE (primeiras linhas):\n${fullText.lines().take(5).joinToString("\n") { "│ $it" }}\n→ $hash")

    //println("processcommandfile ativo")
    //enviarMensagem(fullText)
    //}

    // ------------------------------------------------------------
    // Lista de erros HTTP mais comuns
    private val errorMessages = mapOf(
        400 to """
        !Error 400! Bad Request: Invalid request format.
        Possible solution: Check the request body and parameters for correct syntax.
    """.trimIndent(),

        401 to """
        !Error 401! Unauthorized: API key missing or invalid.
        Possible solution: Ensure you are using a valid API key in the request headers.
    """.trimIndent(),

        403 to """
        !Error 403! Forbidden: Access denied.
        Possible solution: Verify your permissions or contact the administrator for access rights.
    """.trimIndent(),

        404 to """
        !Error 404! Not Found: Endpoint not found or model unavailable.
        Possible solution: Double-check the endpoint URL or confirm the model is still supported.
    """.trimIndent(),

        429 to """
        !Error 429! Too Many Requests: Usage limit exceeded.
        Possible solution: Implement rate limiting or wait before sending new requests.
    """.trimIndent(),

        500 to """
        !Error 500! Internal Server Error: AI server encountered a problem.
        Possible solution: Try again later or contact your provider's support if the problem persists.
    """.trimIndent(),

        502 to """
        !Error 502! Bad Gateway: Communication error with the server.
        Possible solution: Check your internet connection or retry the request.
    """.trimIndent(),

        503 to """
        !Error 503! Service Unavailable: Server temporarily unavailable.
        Possible solution: Wait and try again later; monitor server status if available.
    """.trimIndent()
    )

    //Extrai uma mensagem de erro amigável a partir do status HTTP (opcional) e do corpo.
    //* - Se status != 200, tenta detalhar via JSON ou regex.
    //* - Se não houver status, tenta extrair do body (JSON/regex) e fornece fallback.

    fun extractErrorMessage(body: String, status: Int? = null): String {
        // tenta JSON
        try {
            val json = gson.fromJson(body, Map::class.java)
            val error = json["error"] as? Map<*, *>
            if (error != null) {
                val code = (error["code"] as? Number)?.toInt() ?: status
                val msg = error["message"] as? String ?: "Erro desconhecido"
                return if (code != null) "Erro $code: $msg" else "Erro: $msg"
            }
        } catch (_: Exception) {
            // tenta regex
            val regex = Regex("HTTP (\\d+)")
            val match = regex.find(body)
            if (match != null) {
                val code = match.groupValues[1].toInt()
                return errorMessages[code] ?: "Erro HTTP $code: não mapeado"
            }
        }

        // se status veio e não é 200, devolve junto
        if (status != null && status != 200) {
            return "HTTP $status: $body"
        }

        // fallback final
        return body
    }

    private fun isLocalApi(apiBase: String): Boolean {
        return apiBase.contains("127.0.0.1") || apiBase.contains("localhost")
    }

    fun respostaNormal(prompt: String): String {
        println("respostaNormal activated")

        val responseText = try {
            when {
                apiBase.contains("generativelanguage.googleapis.com") -> {
                    callGoogleGemma(prompt)
                }

                isLocalApi(apiBase) -> {
                    println("Using local provider: ${clientConfig.localApiProvider}")
                    callOpenAISchema(prompt)
                }

                else -> {
                    callOpenAISchema(prompt)
                }
            }
        } catch (e: Exception) {
            println("Request error")
            when (e) {
                is HttpTimeoutException -> "Error: Request timeout"
                is IOException -> "Error: Network problem (${e.message})"
                else -> "Error: ${e.message}"
            }
        }

        if (responseText.isBlank() || responseText.startsWith("Error")) {
            val msg = extractErrorMessage(responseText)
            lastPromptHash = null
            log("Error handled for prompt ${sha256(prompt)}: $msg")
            return msg
        }

        val formatted = responseText
            .replace("\\n", "\n")
            .replace("\n", "|")
            .replace("\\", "")

        historico.add(Mensagem("user", prompt))
        historico.add(Mensagem("assistant", responseText))
        limitarHistorico()

        println("[COMMON] Sending response to server")

        CobblebrainClientCommon.sendToServer?.invoke(formatted)

        return formatted
    }


    private fun callOpenAISchema(prompt: String): String {
        log("===== OPENAI REQUEST =====")
        log("Local Provider: ${clientConfig.localApiProvider}")
        log("ApiBaseUrl: ${clientConfig.apiBaseUrl}")
        log("Model: ${clientConfig.aiModel}")

        log("\n--- PROMPT ---")
        log(prompt)

        val jsonBody = buildOpenAIJson(prompt)

        log("\n--- REQUEST JSON ---")
        log(jsonBody.lines().joinToString("\n") { "│ $it" })

        val builder = HttpRequest.newBuilder()
            .uri(URI.create("$apiBase/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))

        // Autenticação
        if (
            clientConfig.localApiProvider.equals("player2", ignoreCase = true)
            && isLocalAddress(clientConfig.apiBaseUrl)
        ) {
            builder.header("player2-game-key", "019bfb65-9ed4-79af-a348-90d86bbb6cbb")
            log("Auth: player2-game-key header")
        } else {
            val key = apiKeyRotator.current()
            if (key.isNotBlank()) {
                val masked = if (key.length > 8)
                    key.take(4) + "..." + key.takeLast(4)
                else "INVALID_KEY"

                log("Auth: Bearer $masked")
                builder.header("Authorization", "Bearer $key")
            }
        }

        val req = builder.build()

        return try {

            val client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build()

            val res = client.send(req, HttpResponse.BodyHandlers.ofString())

            log("\n===== OPENAI RESPONSE =====")
            log("HTTP ${res.statusCode()}")
            log(res.body())

            if (res.statusCode() != 200) {
                log("Error code detected: ${res.statusCode()}")
                rotateApiKey(res.statusCode())
                rotateModel(res.statusCode())
                throw RuntimeException("HTTP ${res.statusCode()}")
            }

            extractOpenAIContent(res.body())

        } catch (e: Exception) {

            val getEnvironment: (() -> String)? = null

            val thread = Thread.currentThread().name
            val time = LocalDateTime.now()

            log("\n===== AI ERROR =====")
            log("Time: $time")
            log("Side: $getEnvironment")
            log("Thread: $thread")
            log("Provider: ${clientConfig.localApiProvider}")
            log("API Base: $apiBase")
            log("Exception: ${e::class.qualifiedName}")
            log("Message: ${e.message}")
            log("Cause: ${e.cause?.message}")
            log("Stacktrace:")
            e.printStackTrace()

            "Erro API (${clientConfig.localApiProvider} | $getEnvironment): ${e.message}"
        }
    }

    private fun isOpenRouter(apiBase: String) =
        apiBase.contains("openrouter.ai", ignoreCase = true)

    private fun isLMStudio() =
        // ajuste conforme sua URL local do LM Studio
        clientConfig.localApiProvider.contains("lmstudio", ignoreCase = true)

    //private fun usesMaxTokens(apiBase: String) =
    //isOpenRouter(apiBase) || isLMStudio(apiBase)


    private fun buildOpenAIJson(prompt: String): String {
        val tempHistory = historico + Mensagem("user", prompt)

        val messages = tempHistory.joinToString(",") {
            """{ "role": "${it.role}", "content": "${escape(it.text)}" }"""
        }

        val extras = mutableListOf<String>()
        extras.add("\"temperature\": $TEMPERATURE")
        extras.add("\"stream\": false")

        // extras específicos de OpenRouter/LM Studio
        if (isOpenRouter(apiBase) || isLMStudio()) {
            if (PROVIDER_HINT.isNotEmpty()) {
                extras.add(
                    """
                "provider": {
                    "allow_fallbacks": false,
                    "order": ["${escape(PROVIDER_HINT)}"]
                }
                """.trimIndent()
                )
            }
            if (REASONING != "none") {
                extras.add(
                    """
                "reasoning": {
                    "effort": "$REASONING"
                }
                """.trimIndent()
                )
            }
        }

        val extraJson = if (extras.isNotEmpty()) ",\n" + extras.joinToString(",\n") else ""

        // Se for Player2, não inclui "model"
        val outputFormatToUse = if (clientConfig.useDefaultOutput) {
            getDefaultOutputFormat()
        } else {
            clientConfig.outputFormat
        }

        return if (
            clientConfig.localApiProvider.equals("player2", ignoreCase = true) &&
            isLocalAddress(clientConfig.apiBaseUrl)
        ) {
            """
        {
          "messages": [
            { "role": "system", "content": "${escape(INSTRUCTS + outputFormatToUse)}" },
            $messages
          ]$extraJson
        }
        """.trimIndent()
        } else {
            """
        {
          "model": "${modelRotator.current()}",
          "messages": [
            { "role": "system", "content": "${escape(INSTRUCTS + outputFormatToUse)}" },
            $messages
          ]$extraJson
        }
        """.trimIndent()
        }
    }


    private fun extractOpenAIContent(body: String): String {
        return try {
            println(body)
            val json = gson.fromJson(body, Map::class.java)
            val choices = json["choices"] as? List<*> ?: return "Erro parsing resposta"
            val first = choices.firstOrNull() as? Map<*, *> ?: return "Erro parsing resposta"

            // Formato OpenAI/OpenRouter
            val message = first["message"] as? Map<*, *>
            val content = message?.get("content") as? String
            if (!content.isNullOrBlank()) {
                return removeThinkBlocks(content) // <<< limpeza aplicada aqui
            }

            // Alguns provedores retornam "text"
            val text = first["text"] as? String
            if (!text.isNullOrBlank()) {
                return removeThinkBlocks(text) // <<< limpeza aplicada aqui também
            }

            "Erro parsing resposta"
        } catch (_: Exception) {
            "Erro parsing resposta"
        }
    }

    // Função auxiliar para remover blocos <think>...</think>
    private fun removeThinkBlocks(text: String): String {
        val regex = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)
        return text.replace(regex, "")
    }

    // ================= GOOGLE GEMMA / GEMINI =================
    private fun callGoogleGemma(prompt: String): String {

        val currentModel = modelRotator.current()
        val currentKey = apiKeyRotator.current()

        val maskedKey = if (currentKey.length > 8)
            currentKey.take(4) + "..." + currentKey.takeLast(4)
        else
            "INVALID_KEY"

        log("===== GOOGLE GEMMA REQUEST =====")
        log("Model: $currentModel")
        log("API Key: $maskedKey")

        log("\n--- PROMPT ---")
        log(prompt)

        val url = "$apiBase/v1beta/models/$currentModel:generateContent?key=$currentKey"

        val outputFormatToUse = if (clientConfig.useDefaultOutput) {
            getDefaultOutputFormat()
        } else {
            clientConfig.outputFormat
        }

        val requestBody = mapOf(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf("text" to escape(INSTRUCTS + outputFormatToUse))
                    )
                ),
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf("text" to escape(prompt))
                    )
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to TEMPERATURE
            )
        )

        val jsonBody = gson.toJson(requestBody)

        log("\n--- REQUEST JSON ---")
        log(jsonBody.lines().joinToString("\n") { "│ $it" })

        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()

        return try {

            val client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build()

            val res = client.send(req, HttpResponse.BodyHandlers.ofString())

            log("\n===== GOOGLE GEMMA RESPONSE =====")
            log("HTTP ${res.statusCode()}")
            log(res.body())

            if (res.statusCode() != 200) {
                log("Error code detected: ${res.statusCode()}")
                rotateApiKey(res.statusCode())
                rotateModel(res.statusCode())
                throw RuntimeException("HTTP ${res.statusCode()}")
            }

            extractGoogleGemmaContent(res.body())

        } catch (e: Exception) {

            log("\n===== GOOGLE GEMMA ERROR =====")
            log("Type: ${e::class.simpleName}")
            log("Message: ${e.message}")
            e.printStackTrace()

            "Erro API Google: ${e.message}"
        }
    }

    private fun extractGoogleGemmaContent(body: String): String =
        try {
            val json = gson.fromJson(body, Map::class.java)
            val candidates = json["candidates"] as List<*>
            val first = candidates[0] as Map<*, *>
            val content = first["content"] as Map<*, *>
            val parts = content["parts"] as List<*>
            val textPart = parts[0] as Map<*, *>
            textPart["text"] as String
        } catch (_: Exception) {
            "Erro parsing resposta Google"
        }

    // ------------------------------------------------------------
    private fun escape(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

    private fun limitarHistorico() {
        while (historico.size > 6) historico.removeAt(0)
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

// ------------------------------------------------------------
fun main() {
    AIHandler().start()
}
