import com.google.gson.Gson
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.*
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess
import vito.cobblebrain.config.CobblebrainConfig

// ------------------------------------------------------------
// DATA CLASSES
// ------------------------------------------------------------
data class Mensagem(val role: String, val text: String)

// ------------------------------------------------------------
// AI HANDLER
// ------------------------------------------------------------
class AIHandler(dirPath: String) {

    companion object {
        private val gson = Gson()
        private val configFile = File("config/cobblebrain.json")
        private val config =
            gson.fromJson(configFile.readText(), CobblebrainConfig::class.java)

        private val MODEL = config.aiModel
        private val INSTRUCTS = config.instruct.trimIndent()
        private val TEMPERATURE = config.temperature
        private val PROVIDER_HINT = config.aiProvider.trim()
        private val REASONING = config.reasoningEffort.trim().lowercase()
        private val DEBUG = config.debugLogging
        private val TIMEOUT_SECONDS = config.requestTimeoutSeconds
    }

    private val apiKey = config.apiKey?.trim()
    private val apiBase =
        config.apiBaseUrl
            .trimEnd('/')
            .replace("localhost", "127.0.0.1") // defensive

    private val comandoPath = Paths.get(dirPath, "comando_ia.txt")
    private val respostaPath = Paths.get(dirPath, "resposta_ia.txt")

    // ---------------- Logging ----------------
    private val logDir = Paths.get(dirPath, "logs").also {
        if (DEBUG) Files.createDirectories(it)
    }

    private val logFile =
        if (DEBUG)
            logDir.resolve(
                "ai_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))}.log"
            )
        else null

    private fun log(text: String) {
        if (!DEBUG || logFile == null) return
        Files.writeString(
            logFile,
            "[${LocalDateTime.now()}] $text\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }

    // ---------------- Conversation ----------------
    private val historico = mutableListOf<Mensagem>()
    private var lastPromptHash: String? = null
    private var lastSpecies: String? = null

    init {
        if (!Files.exists(comandoPath)) Files.writeString(comandoPath, "")
        if (!Files.exists(respostaPath)) Files.writeString(respostaPath, "")
    }

    // ------------------------------------------------------------
    fun start() {
        val watchService = FileSystems.getDefault().newWatchService()
        comandoPath.parent.register(
            watchService,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE
        )

        while (true) {
            try {
                val key = watchService.take()
                for (event in key.pollEvents()) {
                    if ((event.context() as Path).endsWith(comandoPath.fileName)) {
                        Thread.sleep(60)
                        processCommandFile()
                    }
                }
                key.reset()
            } catch (e: Exception) {
                Thread.sleep(200)
            }
        }
    }

    // ------------------------------------------------------------
    private fun processCommandFile() {
        if (!config.pokemonTalk) return

        val fullText = Files.readString(comandoPath).trim()
        println(comandoPath.fileName.toString())
        if (fullText.isEmpty()) return

        val playerLine = extractPlayerUtterance(fullText) ?: return
        val hash = sha256(playerLine)
        if (hash == lastPromptHash) return

        lastPromptHash = hash
        log("FULL PROMPT:\n${playerLine.lines().joinToString("\n") { "│ $it" }}")

        enviarMensagem(fullText)
    }

    private fun extractPlayerUtterance(text: String): String? =
        text.lines()
            .map { it.trim() }
            .lastOrNull { it.startsWith("[the player") && it.contains("said]:") }

    // ------------------------------------------------------------
    private fun enviarMensagem(prompt: String) {
        if (prompt == "/end") exitProcess(0)

        try {
            respostaNormal(prompt)
        } catch (e: Exception) {
            lastPromptHash = null
            Files.writeString(respostaPath, "Erro interno: ${e.message}")
        }
    }

    private fun respostaNormal(prompt: String) {
        val responseText = if (apiBase.contains("generativelanguage.googleapis.com")) {
            callGoogleGemma(prompt)
        } else {
            callOpenAISchema(prompt)
        }

        if (responseText.isBlank() || responseText.startsWith("Erro")) {
            Files.writeString(respostaPath, "IA indisponível no momento")
            lastPromptHash = null
            return
        }

        val formatted = responseText
            .replace("\\n", "\n")
            .replace("\n", "|")
            .replace("\\", "")

        Files.writeString(respostaPath, formatted)

        historico.add(Mensagem("user", prompt))
        historico.add(Mensagem("assistant", responseText))
        limitarHistorico()
    }

    // ================= OPENAI-SCHEMA =================
    private fun callOpenAISchema(prompt: String): String {
        val jsonBody = buildOpenAIJson(prompt)
        log("REQUEST JSON:\n${jsonBody.lines().joinToString("\n") { "│ $it" }}")

        val builder = HttpRequest.newBuilder()
            .uri(URI.create("$apiBase/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))

        if (!apiKey.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }

        val req = builder.build()

        return try {
            val client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build()

            val res = client.send(req, HttpResponse.BodyHandlers.ofString())

            log("HTTP ${res.statusCode()} RESPONSE:\n${res.body()}")

            if (res.statusCode() != 200) {
                throw RuntimeException("HTTP ${res.statusCode()}")
            }

            extractOpenAIContent(res.body())
        } catch (e: Exception) {
            log("ERROR: ${e.message}")
            "Erro API: ${e.message}"
        }
    }

    private fun buildOpenAIJson(prompt: String): String {
        val tempHistory = historico + Mensagem("user", prompt)

        val messages = tempHistory.joinToString(",") {
            """{ "role": "${it.role}", "content": "${escape(it.text)}" }"""
        }

        val extras = mutableListOf<String>()

        extras.add("\"temperature\": $TEMPERATURE")
        extras.add("\"stream\": false")       // REQUIRED FOR LM STUDIO
        extras.add("\"max_tokens\": -1")      // REQUIRED FOR LM STUDIO

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

        val extraJson =
            if (extras.isNotEmpty()) ",\n" + extras.joinToString(",\n") else ""

        return """
        {
          "model": "$MODEL",
          "messages": [
            { "role": "system", "content": "${escape(INSTRUCTS)}" },
            $messages
          ]$extraJson
        }
        """.trimIndent()
    }

    private fun extractOpenAIContent(body: String): String =
        try {
            val json = gson.fromJson(body, Map::class.java)
            val choices = json["choices"] as List<*>
            val msg = choices[0] as Map<*, *>
            val message = msg["message"] as Map<*, *>
            message["content"] as String
        } catch (_: Exception) {
            "Erro parsing resposta"
        }

    // ================= GOOGLE GEMMA / GEMINI =================
    private fun callGoogleGemma(prompt: String): String {
        // Monta URL COM a API key como query param (padrão da Generative Language API)
        val url = "$apiBase/v1beta/models/$MODEL:generateContent?key=${apiKey ?: ""}"

        val jsonBody = """
    {
      "contents": [
        {
          "role": "user",
          "parts": [
            { "text": "${escape(INSTRUCTS + prompt)}" }
          ]
        }
      ],
      "generationConfig": {
        "temperature": $TEMPERATURE
      }
    }
    """.trimIndent()

        log("REQUEST JSON (Google Gemma/Gemini):\n${jsonBody.lines().joinToString("\n") { "│ $it" }}")

        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))

        // REMOVIDO: Authorization Bearer — Google usa ?key= no endpoint
        val req = builder.build()

        return try {
            val client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build()

            val res = client.send(req, HttpResponse.BodyHandlers.ofString())

            log("HTTP ${res.statusCode()} RESPONSE:\n${res.body()}")

            if (res.statusCode() != 200) {
                throw RuntimeException("HTTP ${res.statusCode()} - ${res.body()}")
            }

            extractGoogleGemmaContent(res.body())
        } catch (e: Exception) {
            log("ERROR (Google Gemma/Gemini): ${e.message}")
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

    fun maybeResetHistoryIfSpeciesChanged(currentSpecies: String) {
        if (lastSpecies != null && lastSpecies != currentSpecies) {
            historico.clear()
            lastPromptHash = null
        }
        lastSpecies = currentSpecies
    }
}

// ------------------------------------------------------------
fun main() {
    AIHandler(Paths.get("").toAbsolutePath().toString()).start()
}
