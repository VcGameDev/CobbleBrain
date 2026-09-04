package vito.cobblebrain.client

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.Gson
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
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

object ConversationMemory {
    private val memory = mutableListOf<String>()

    fun save(text: String) {
        val max = clientConfig.maxInteractionSaves
        println("[DEBUG] Saving to ConversationMemory (max limit: $max): \"$text\"")
        memory.add(text)
        while (memory.size > max) {
            val removed = memory.removeAt(0)
            println("[DEBUG] ConversationMemory limit reached. Removed oldest: \"$removed\"")
        }
        println("[DEBUG] Current ConversationMemory size: ${memory.size}. Items: $memory")
    }

    fun get(): List<String> = memory
}

class AIHandler {
    companion object {
        private val sessionLogFile: Path by lazy {
            val legacyDir = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("cobblebrain-ai/logs")
            val dir = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("cobblebrain/logs")

            if (!Files.exists(dir) && Files.exists(legacyDir)) {
                try { Files.move(legacyDir, dir) } catch (_: Throwable) {}
            }
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
        private val SHOW_HUNGER get() = clientConfig.showHunger
        private val PROVIDER_HINT get() = clientConfig.aiProvider.trim()
        private val REASONING get() = clientConfig.reasoningEffort.trim().lowercase()
        private val DEBUG get(

        ) = clientConfig.debugLogging
        private val TIMEOUT_SECONDS get() = clientConfig.requestTimeoutSeconds
        private val USER_LANGUAGE get() = clientConfig.selectedLanguage
        const val HEADER = """
        ##OUTPUT FORMAT##
        Follow all rules strictly.
        """

        val DIALOGUE get() = """
        DIALOGUE FORMAT
        Pokémon and player fully understand each other
        Pokémon can speak normally and express complex thoughts
        format=name: text
        separator=|
        short dialogue only
        wild pokemon allowed
        response language=$USER_LANGUAGE
        """

        val CANON_DIALOGUE get() = """
        DIALOGUE FORMAT
        the player cannot understand Pokémon language
        communication is limited to vocalizations and emotion
        format=name: sound(emotion/intent)
        response language=$USER_LANGUAGE
        separator=|
        short creature sounds only
        wild pokemon allowed
        NEVER translate or explain sounds
        """

        const val FRIENDSHIP = """
        FRIENDSHIP FORMAT
        %name:+/-1~5
        only delta value
        one change per pokemon
        wild pokemon never change
        respect PLUS/MINUS settings
        """

        val PSYCHIC_TRANSLATION get() = """
        PSYCHIC TRANSLATION
        Psychic Pokémon speak $USER_LANGUAGE naturally.
        They may occasionally interpret another Pokémon's intentions or emotions.
        Do not translate literally, only if it improves the conversation.
        """

        val MEMORY get() = """
        MEMORY FORMAT
        &MEMORY:PokemonNames:MemoryText|keywords
        PokemonNames = comma separated list of pokemon names involved
        MemoryText = third-person short summary of the memory/interaction
        keywords = comma separated list of 7 relevant lowercase keywords
        Memory text and keywords must be written in $USER_LANGUAGE.
        Generate exactly one memory at the end of each conversation.
        """

        fun getActionSection(): String {
            val party: List<Pokemon> = try {
                CobblemonClient.storage.party.filterNotNull().filter { it.currentHealth > 0 }
            } catch (_: Throwable) {
                emptyList()
            }
            val presentTypes = party.flatMap { it.types }.map { it.name.lowercase() }.toSet()

            val available = mutableListOf<String>()

            if (SyncedConfig.isActionActive("attack")) available += "A (attack)"
            if (SyncedConfig.isActionActive("eat")) available += "E (eat)"
            if (SyncedConfig.isActionActive("buff")) available += "B (buff owner)"
            if (SyncedConfig.isActionActive("debuff_enemy")) available += "D (debuff enemy)"
            if (SyncedConfig.isActionActive("sit")) available += "S (sit/rest)"
            if (SyncedConfig.isActionActive("protect")) available += "P (protect owner)"
            if (SyncedConfig.isActionActive("idle")) available += "I (idle)"

            val typeActions = mutableListOf<String>()
            if (SyncedConfig.isActionActive("cook") && ("fire" in presentTypes || presentTypes.isEmpty())) typeActions += "fire type: C (cook/smelt ores)"
            if (SyncedConfig.isActionActive("repair") && ("steel" in presentTypes || presentTypes.isEmpty())) typeActions += "steel type: R (repair tools)"
            if (SyncedConfig.isActionActive("excavate") && ("steel" in presentTypes || presentTypes.isEmpty())) typeActions += "steel type: EX (excavate tunnel)"
            if (SyncedConfig.isActionActive("grow") && ("grass" in presentTypes || presentTypes.isEmpty())) typeActions += "grass type: G (grow crops)"
            if (SyncedConfig.isActionActive("shift") && ("ghost" in presentTypes || presentTypes.isEmpty())) typeActions += "ghost type: SH (shift)"
            if (SyncedConfig.isActionActive("nightmare") && ("dark" in presentTypes || presentTypes.isEmpty())) typeActions += "dark type: N (nightmare aura)"
            if (SyncedConfig.isActionActive("scout") && ("flying" in presentTypes || presentTypes.isEmpty())) typeActions += "flying type: SC (scout)"
            if (SyncedConfig.isActionActive("light") && ("electric" in presentTypes || presentTypes.isEmpty())) typeActions += "electric type: L (light)"
            if (SyncedConfig.isActionActive("fish") && ("water" in presentTypes || presentTypes.isEmpty())) typeActions += "water type: F (fish)"
            if (SyncedConfig.isActionActive("teleport") && ("psychic" in presentTypes || presentTypes.isEmpty())) typeActions += "psychic type: T (teleport)"

            if (available.isEmpty() && typeActions.isEmpty()) return ""

            return buildString {
                appendLine("ACTION FORMAT")
                appendLine("Use actions only when appropriate to the dialogue, environment, or situation.")
                appendLine("Format: #<PokemonName>:<action_code>")
                if (available.isNotEmpty()) {
                    appendLine("Universal actions (any Pokémon):")
                    appendLine("  ${available.joinToString(", ")}")
                }
                if (typeActions.isNotEmpty()) {
                    appendLine("Type-specific actions (available for current Pokémon types):")
                    appendLine("  ${typeActions.joinToString(" | ")}")
                }
            }.trim()
        }

        const val CATCH = """
        GUARANTEED CATCH FORMAT
        format=!name
        guarantees next catch
        wild pokemon only
        requires strong positive interaction
        rare
        """

        const val APRIL1 = """
        April1 mode is active: extra actions allowed (follow ACTION FORMAT):
          fire type: fireball machine | nuke
          psychic type: psychic stand
          fairy type: imaginary technique
          flying type: final judgment
          any type: ssstyle
        """

        const val QUEST = """
        QUEST SYSTEM
        types=STORY/SECONDARY/ADVICE
        quests start via natural pokemon dialogue
        ADVICE quests:
        #SCORE:-3~3
        score reflects player help quality
        wait for [QUEST COMPLETED]/[QUEST FAILED]
        &=short quest summary EN
        """

        const val RESUME = """
        RESUME FORMAT
        =summary
        recent events and conversation topics only
        do not include ongoing goals, concerns, emotions, opinions, or intentions
        do not describe what a Pokémon will continue doing
        focus only on what happened
        max 4 sentences
        EN only
        """

        val GENERAL get() = buildString {
            appendLine("GENERAL RULES")
            appendLine("- strict format only")
            appendLine("- keep section order")
            appendLine("- pokemon + player only")
            appendLine("- only ACTIVE or NEARBY Pokémon may speak")
            appendLine("- unavailable Pokémon never speak")
            appendLine("- if no Pokémon can respond, output only: \"No Pokémon heard what you said\" in $USER_LANGUAGE")
            appendLine("- consistent names")
            appendLine("- no self-talk unless specified")
            appendLine("- never speak or act for the player")
            appendLine("- nearby Pokémon do not know the player's name")
            appendLine("- player IDs belong to players, not Pokémon")

            if (!SHOW_HUNGER) {
                appendLine("- never initiate conversations about hunger, food, eating, or fullness. Only discuss them if the player explicitly asks.")
                appendLine("- STRICTLY IGNORE any past memories or context regarding food, eating, fullness, or hunger unless the player explicitly asks in their current message.")
            }
        }
    }

    private fun shouldUseNormalDialogue(): Boolean {
        val player = Minecraft.getInstance().player ?: return false

        val headItem = player.inventory.armor[3]
        val offhandItem = player.offhandItem

        return isExpShare(headItem) || isExpShare(offhandItem)
    }

    private fun isExpShare(stack: ItemStack): Boolean {
        val id = stack.item.toString().lowercase()
        return id.contains("exp_share")
    }

    private fun resolveDialogueSection(
        needsTranslator: Boolean,
        dialogue: Boolean,
        canonDialogue: Boolean
    ): String? {

        // prioridade 1: item
        if (needsTranslator) {
            val hasTranslator = shouldUseNormalDialogue()

            return if (hasTranslator) DIALOGUE else CANON_DIALOGUE
        }

        // prioridade 2: config
        if (canonDialogue) return CANON_DIALOGUE
        if (dialogue) return DIALOGUE

        return null
    }

    fun buildOutputFormat(
        dialogue: Boolean,
        actions: Boolean,
        friendship: Boolean,
        quests: Boolean,
        april1: Boolean,
        canonDialogue: Boolean,
        needsTranslator: Boolean,
        guaranteedCatch: Boolean,
        //worldContext: Boolean,
        //mobsContext: Boolean,
        //lastContext: Boolean,
        //blockSensors: Boolean,
        memories: Boolean,
        psychicTranslation: Boolean
    ): String {

        val sections = mutableListOf<String>()

        sections += HEADER

        resolveDialogueSection(needsTranslator, dialogue, canonDialogue)
            ?.let { sections += it }

        if (friendship) sections += FRIENDSHIP
        if (memories) sections += MEMORY
        if (actions) {
            val actionSec = getActionSection()
            if (actionSec.isNotBlank()) sections += actionSec
        }
        if (psychicTranslation) sections += PSYCHIC_TRANSLATION
        
        if (guaranteedCatch && dialogue) {
            sections += CATCH
        }
        
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
        if (SyncedConfig.optimizedMode) {
            val sections = mutableListOf<String>()
            sections += HEADER
            resolveDialogueSection(
                SyncedConfig.needsPokemonTranslator,
                SyncedConfig.outputDialogue,
                SyncedConfig.outputPokemonLanguage
            )?.let { sections += it }

            if (SyncedConfig.outputActions) {
                val actionSec = getActionSection()
                if (actionSec.isNotBlank()) sections += actionSec
            }

            if (clientConfig.psychicTranslation) sections += PSYCHIC_TRANSLATION
            sections += GENERAL
            sections += """
            ##ROUTING FLAGS##
            Append applicable tags at the very end of your response (omit entirely if mundane):
            Format: [FLAG: TAG1, TAG2]

            - FRIENDSHIP: Meaningful emotional impact (praise, comfort, affection, insult). Skip small-talk/orders.
            - QUEST: Active quest discussion, progress, completion, or advice.
            - MEMORY: Memorable bonding, humor, secrets, promises, battle milestones. Skip routine small-talk.
            - TRAIT: Major psychological/worldview shift (trauma, overcoming fear, huge victory). Never for routine chatter or open slots.
            - QUIRK: Developing or breaking a distinct habit, tic, or ritual from a profound event. Never for routine actions or open slots.
            - CATCH: Wild Pokémon persuaded and explicitly agrees to join team (wild only).
            
            ##STAGE 1 RESTRICTIONS##
            Generate ONLY in-character spoken dialogue, optional # actions and flags.
            """.trimIndent()

            return sections.joinToString("\n\n")
        }

        return buildOutputFormat(
            dialogue = SyncedConfig.outputDialogue,
            actions = SyncedConfig.outputActions,
            friendship = SyncedConfig.outputFriendship,
            quests = SyncedConfig.outputQuests,
            april1 = SyncedConfig.outputApril1,
            canonDialogue = SyncedConfig.outputPokemonLanguage,
            needsTranslator = SyncedConfig.needsPokemonTranslator,
            guaranteedCatch = SyncedConfig.outputGuaranteedCatch,
            //worldContext = clientConfig.outputWorldContext,
            //mobsContext = clientConfig.outputMobsContext,
            //lastContext = clientConfig.outputLastContext,
            //blockSensors = clientConfig.outputBlockSensors,
            memories = SyncedConfig.outputMemories,
            psychicTranslation = clientConfig.psychicTranslation
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
            clientConfig.customApiProvider.equals("player2", ignoreCase = true)
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

    fun executeMemoryRetrieval(playerMessage: String, candidateMemories: List<String>): String {
        val memorySearchPrompt = buildString {
            appendLine("[MEMORY SEARCH]")
            appendLine("Player message: $playerMessage")
            appendLine()
            if (candidateMemories.isNotEmpty()) {
                appendLine("Candidate memories:")
                candidateMemories.forEachIndexed { idx, mem ->
                    appendLine("${idx + 1}. $mem")
                }
            } else {
                appendLine("No candidate memories available.")
            }
            appendLine()
            appendLine("Decide whether past memories are needed to answer the player's message.")
            appendLine("If no memories are relevant or needed, reply ONLY with NO_MEMORY.")
            appendLine("If memories are needed, reply ONLY with the exact text of the selected memory/memories (one per line). Select at most 2 memories per involved Pokémon if there are 4 or fewer Pokémon, or at most 1 memory per involved Pokémon if there are more than 4 Pokémon.")
            appendLine("Do NOT generate any dialogue or additional text.")
        }

        val systemOverride = "You are a memory retrieval assistant. Your job is to decide if memories are needed to answer the player's message, and select only the relevant memories. If no memories are needed, reply ONLY with NO_MEMORY. Otherwise, output only the selected memory text."

        val response = try {
            when {
                apiBase.contains("generativelanguage.googleapis.com") -> callGoogleGemma(memorySearchPrompt, systemOverride)
                isLocalApi(apiBase) -> callOpenAISchema(memorySearchPrompt, systemOverride)
                else -> callOpenAISchema(memorySearchPrompt, systemOverride)
            }
        } catch (e: Exception) {
            "NO_MEMORY"
        }
        println("[MemoryRetrievalAI] Decision/Selection response:\n$response")
        return response.trim()
    }

    fun respostaNormal(prompt: String): String {
        println("respostaNormal activated")

        val cleanPrompt = prompt

        // Injeta LAST INTERACTIONS no prompt vindo do servidor
        val interactions = ConversationMemory.get()
        val finalPrompt = if (interactions.isNotEmpty()) {
            buildString {
                appendLine("[LAST INTERACTIONS]")
                interactions.forEach { appendLine("- $it") }
                appendLine()
                append(cleanPrompt)
            }
        } else {
            cleanPrompt
        }

        val responseText = try {
            when {
                apiBase.contains("generativelanguage.googleapis.com") -> {
                    callGoogleGemma(finalPrompt)
                }

                isLocalApi(apiBase) -> {
                    println("Using local provider: ${clientConfig.localApiProvider}")
                    callOpenAISchema(finalPrompt)
                }

                else -> {
                    callOpenAISchema(finalPrompt)
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

        historico.add(Mensagem("user", cleanPrompt))
        historico.add(Mensagem("assistant", responseText))
        limitarHistorico()

        CobblebrainClientCommon.sendToServer?.invoke(formatted)

        // Processa LAST INTERACTIONS para a memória local (ConversationMemory)
        val parts = formatted.split("|").map { it.trim() }
        println("[DEBUG] Parsing AI response lines for conversation memory. Total parts: ${parts.size}")
        
        // 1. Tenta extrair resumo explícito legado (!RESUME ou =)
        var savedExplicit = false
        parts.forEach { trimmed ->
            if (trimmed.startsWith("!RESUME", ignoreCase = true)) {
                val resumeText = trimmed.substringAfter("!RESUME")
                    .removePrefix(":")
                    .trim()
                if (resumeText.isNotBlank()) {
                    ConversationMemory.save(resumeText)
                    savedExplicit = true
                }
            } else if (trimmed.startsWith("=")) {
                val resumeText = trimmed.removePrefix("=")
                    .trim()
                if (resumeText.isNotBlank()) {
                    ConversationMemory.save(resumeText)
                    savedExplicit = true
                }
            }
        }

        // 2. Se não houver resumo explícito (como no modo otimizado), sintetiza localmente: Player Input -> Pokémon Dialogue
        if (!savedExplicit) {
            val playerMsg = if (cleanPrompt.contains("[PLAYER_MESSAGE]")) {
                cleanPrompt.substringAfter("[PLAYER_MESSAGE]").substringBefore("[").trim()
            } else {
                ""
            }

            val dialogueLines = parts.filter { line ->
                line.isNotBlank() &&
                !line.startsWith("#") &&
                !line.startsWith("&") &&
                !line.startsWith("%") &&
                !line.startsWith("!") &&
                !line.startsWith("=") &&
                !line.startsWith("[FLAG:", ignoreCase = true) &&
                line.contains(":")
            }

            val pokesSpeech = dialogueLines.joinToString(" | ")
            val entry = when {
                playerMsg.isNotBlank() && pokesSpeech.isNotBlank() ->
                    "Player: \"$playerMsg\" -> $pokesSpeech"
                pokesSpeech.isNotBlank() ->
                    pokesSpeech
                playerMsg.isNotBlank() ->
                    "Player: \"$playerMsg\""
                else -> ""
            }

            if (entry.isNotBlank()) {
                ConversationMemory.save(entry)
            }
        }

        return formatted
    }

    fun generateSessionSummary(contextData: String) {
        val systemOverride = "You are an RPG narrator. Summarize the following events of this session in 2 or 3 short sentences. Focus on the player's interactions with Pokémon, quests, and main events. Do NOT output dialogue or formatting. Start your response EXACTLY with [SUMMARY_RESPONSE]."
        val prompt = "Recent Events & Active Quests:\n$contextData"
        
        val responseText = try {
            when {
                apiBase.contains("generativelanguage.googleapis.com") -> {
                    callGoogleGemma(prompt, systemOverride)
                }
                isLocalApi(apiBase) -> {
                    callOpenAISchema(prompt, systemOverride)
                }
                else -> {
                    callOpenAISchema(prompt, systemOverride)
                }
            }
        } catch (e: Exception) {
            "[SUMMARY_RESPONSE] Failed to generate summary due to API error: ${e.message}"
        }

        val formatted = if (responseText.isBlank() || responseText.startsWith("Error")) {
            "[SUMMARY_RESPONSE] Failed to generate summary: ${extractErrorMessage(responseText)}"
        } else {
            responseText.replace("\\n", "\n").replace("\n", " ").replace("\\", "")
        }

        // Envia de volta para o servidor processar
        CobblebrainClientCommon.sendToServer?.invoke(formatted)
    }

    fun generateBackgroundState(prompt: String): String {
        val systemOverride = "You are the background state analysis engine for CobbleBrain. Analyze the conversation and player input, and output state updates for remaining systems (traits, quirks, memory diary, quest scoring, friendship, guaranteed catch) following the specified format strictly. Do NOT output dialogue or greeting text."

        val responseText = try {
            when {
                apiBase.contains("generativelanguage.googleapis.com") -> {
                    callGoogleGemma(prompt, systemOverride)
                }
                isLocalApi(apiBase) -> {
                    callOpenAISchema(prompt, systemOverride)
                }
                else -> {
                    callOpenAISchema(prompt, systemOverride)
                }
            }
        } catch (e: Exception) {
            println("[CobbleBrain AI] Background state evaluation error: ${e.message}")
            "Error: ${e.message}"
        }

        if (responseText.isBlank() || responseText.startsWith("Error")) {
            val msg = extractErrorMessage(responseText)
            log("Background state error for prompt ${sha256(prompt)}: $msg")
            return msg
        }

        val formatted = responseText
            .replace("\\n", "\n")
            .replace("\n", "|")
            .replace("\\", "")

        return formatted
    }


    private fun callOpenAISchema(prompt: String, systemOverride: String? = null): String {
        log("===== OPENAI REQUEST =====")
        log("Custom Provider: ${clientConfig.customApiProvider}")
        log("ApiBaseUrl: ${clientConfig.apiBaseUrl}")
        log("Model: ${clientConfig.aiModel}")

        log("\n--- PROMPT ---")
        log(prompt)

        // Nota: buildOpenAIJson já usa o prompt final com as interações injetadas
        val jsonBody = buildOpenAIJson(prompt, systemOverride)

        log("\n--- REQUEST JSON ---")
        log(jsonBody.lines().joinToString("\n") { "│ $it" })

        val url = when {
            clientConfig.useChatEndpoint -> "$apiBase/v1/chat/completions"
            else -> apiBase
        }

        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))

        // Autenticação
        if (
            clientConfig.customApiProvider.equals("player2", ignoreCase = true)
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
            log("Provider: ${clientConfig.customApiProvider}")
            log("API Base: $apiBase")
            log("Exception: ${e::class.qualifiedName}")
            log("Message: ${e.message}")
            log("Cause: ${e.cause?.message}")
            log("Stacktrace:")
            e.printStackTrace()

            "Erro API (${clientConfig.customApiProvider} | $getEnvironment): ${e.message}"
        }
    }

    private fun isOpenRouter(apiBase: String) =
        apiBase.contains("openrouter.ai", ignoreCase = true)

    private fun isLMStudio() =
        // ajuste conforme sua URL local do LM Studio
        clientConfig.customApiProvider.contains("lmstudio", ignoreCase = true)

    //private fun usesMaxTokens(apiBase: String) =
    //isOpenRouter(apiBase) || isLMStudio(apiBase)


    private fun buildOpenAIJson(prompt: String, systemOverride: String? = null): String {
        // Se tem override (é um resumo), não envia o histórico de conversas para economizar tokens e evitar confusão.
        val tempHistory = if (systemOverride != null) {
            listOf(Mensagem("user", prompt))
        } else {
            historico + Mensagem("user", prompt)
        }

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
        val outputFormatToUse = if (SyncedConfig.optimizedMode || SyncedConfig.useDefaultOutput) {
            getDefaultOutputFormat()
        } else {
            clientConfig.outputFormat
        }

        val systemContent = systemOverride ?: if (INSTRUCTS.isNotBlank()) "$INSTRUCTS\n\n$outputFormatToUse" else outputFormatToUse

        return if (
            clientConfig.customApiProvider.equals("player2", ignoreCase = true) &&
            isLocalAddress(clientConfig.apiBaseUrl)
        ) {
            """
        {
          "messages": [
            { "role": "system", "content": "${escape(systemContent)}" },
            $messages
          ]$extraJson
        }
        """.trimIndent()
        } else {
            """
        {
          "model": "${modelRotator.current()}",
          "messages": [
            { "role": "system", "content": "${escape(systemContent)}" },
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
    private fun callGoogleGemma(prompt: String, systemOverride: String? = null): String {

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

        val outputFormatToUse = if (SyncedConfig.optimizedMode || SyncedConfig.useDefaultOutput) {
            getDefaultOutputFormat()
        } else {
            clientConfig.outputFormat
        }

        val systemContent = systemOverride ?: if (INSTRUCTS.isNotBlank()) "$INSTRUCTS\n\n$outputFormatToUse" else outputFormatToUse

        val contents = if (systemOverride != null) {
            listOf(
                mapOf("role" to "user", "parts" to listOf(mapOf("text" to escape(systemContent)))),
                mapOf("role" to "user", "parts" to listOf(mapOf("text" to escape(prompt))))
            )
        } else {
            // Google Gemma normally receives system rules as user messages first
            val tempHistory = historico + Mensagem("user", prompt)
            val mappedHistory = mutableListOf<Map<String, Any>>()
            
            mappedHistory.add(
                mapOf("role" to "user", "parts" to listOf(mapOf("text" to escape(systemContent))))
            )
            
            tempHistory.forEach { m ->
                val role = if (m.role == "assistant") "model" else "user"
                mappedHistory.add(
                    mapOf("role" to role, "parts" to listOf(mapOf("text" to escape(m.text))))
                )
            }
            mappedHistory
        }

        val requestBody = mapOf(
            "contents" to contents,
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
