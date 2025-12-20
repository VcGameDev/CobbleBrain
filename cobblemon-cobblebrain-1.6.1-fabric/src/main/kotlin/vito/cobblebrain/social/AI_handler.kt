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
                        println("tentativa de processcommandfile")
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

        val playerLine = extractUtterance(fullText) ?: return
        val hash = sha256(playerLine)
        if (hash == lastPromptHash) return

        lastPromptHash = hash
        log("FULL PROMPT:\n${playerLine.lines().joinToString("\n") { "│ $it" }}")

        println("processcommandfile ativo")
        enviarMensagem(fullText)
    }

    fun extractUtterance(text: String): String? {
        return text.lines().firstNotNullOfOrNull { line ->
            when {
                line.startsWith("[") -> line.removePrefix("[").trim()
                line.startsWith("IMPORTANT:") -> line.removePrefix("IMPORTANT:").trim()
                else -> null
            }
        }
    }

    // ------------------------------------------------------------
    private fun enviarMensagem(prompt: String) {
        println(INSTRUCTS)
        if (prompt == "/end") exitProcess(0)

        try {
            println("tentativa de acionar respostaNormal")
            respostaNormal(prompt)
        } catch (e: Exception) {
            lastPromptHash = null
            Files.writeString(respostaPath, "Erro interno: ${e.message}")
            println("tentativa falha")
        }
    }

    // Lista de erros HTTP mais comuns
    private val errorMessages = mapOf(
        400 to "!Error 400! Bad Request: verifique o formato da requisição",
        401 to "!Error 401! Unauthorized: API key inválida ou ausente",
        403 to "!Error 403! Forbidden: acesso negado",
        404 to "!Error 404! Not Found: endpoint não encontrado ou modelo não mais disponivel",
        429 to "!Error 429! Too Many Requests: limite de uso excedido",
        500 to "!Error 500! Internal Server Error: problema no servidor da IA",
        502 to "!Error 502! Bad Gateway: erro de comunicação com o servidor, verifique sua conexão",
        503 to "!Error 503! Service Unavailable: servidor temporariamente indisponível"
    )
    /**
     * Extrai uma mensagem de erro amigável a partir do status HTTP (opcional) e do corpo.
     * - Se status != 200, tenta detalhar via JSON ou regex.
     * - Se não houver status, tenta extrair do body (JSON/regex) e fornece fallback.
     */
    fun extractErrorMessage(body: String, status: Int? = null): String {
    // 1) Se temos status e é erro, tenta detalhar
        if (status != null && status != 200) {
            // Tenta JSON estruturado (OpenAI/Google)
            try {
                val json = gson.fromJson(body, Map::class.java)
                val error = json["error"] as? Map<*, *>
                if (error != null) {
                    val code = (error["code"] as? Number)?.toInt() ?: status
                    val msg = error["message"] as? String ?: "Erro desconhecido"
                    return "Erro $code: $msg"
                }
            } catch (_: Exception) {
                // Se não for JSON, tenta regex tipo "HTTP 404"
                val regex = Regex("HTTP (\\d+)")
                val match = regex.find(body)
                if (match != null) {
                    val code = match.groupValues[1].toInt()
                    return errorMessages[code] ?: "Erro HTTP $code: não mapeado"
                }
            }
            // Fallback: status + corpo
            return "HTTP $status: $body"
        }

        // 2) Sem status (ou status 200 mas corpo indica erro): tenta extrair do body
        try {
            val json = gson.fromJson(body, Map::class.java)
            val error = json["error"] as? Map<*, *>
            if (error != null) {
                val code = (error["code"] as? Number)?.toInt()
                val msg = error["message"] as? String ?: "Erro desconhecido"
                return if (code != null) "Erro $code: $msg" else "Erro: $msg"
            }
        } catch (_: Exception) {
            val regex = Regex("HTTP (\\d+)")
            val match = regex.find(body)
            if (match != null) {
                val code = match.groupValues[1].toInt()
                return errorMessages[code] ?: "Erro HTTP $code: não mapeado"
            }
        }

        // 3) Fallback final: devolve o body
        return body
    }

    private fun respostaNormal(prompt: String) {
        println("RespostaNormal ativada")
        val responseText = try {
            if (apiBase.contains("generativelanguage.googleapis.com")) {
                callGoogleGemma(prompt)
            } else {
                callOpenAISchema(prompt)
            }
        } catch (e: Exception) {
            println("erro na requisição")
            when (e) {
                is java.net.http.HttpTimeoutException -> "Erro: Timeout na requisição"
                is java.io.IOException -> "Erro: Problema de rede (${e.message})"
                else -> "Erro: ${e.message}"
            }
        }

        // Se vier erro ou vazio
        if (responseText.isBlank() || responseText.startsWith("Erro")) {
            val msg = extractErrorMessage(responseText)

            // Loga o erro junto com o hash do prompt
            val hash = sha256(prompt)
            Files.writeString(
                respostaPath,
                msg
            )

            // Resetar o hash garante que não trava novas tentativas
            lastPromptHash = null

            log("Erro tratado para prompt $hash: $msg")
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
                .version(HttpClient.Version.HTTP_2)
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

    private fun isOpenRouter(apiBase: String) =
        apiBase.contains("openrouter.ai", ignoreCase = true)

    private fun isLMStudio(apiBase: String) =
        // ajuste conforme sua URL local do LM Studio (ex.: http://localhost:1234)
        apiBase.contains("lmstudio", ignoreCase = true) ||
                apiBase.contains("localhost", ignoreCase = true) // se você usar LM Studio local

    private fun usesMaxTokens(apiBase: String) =
        isOpenRouter(apiBase) || isLMStudio(apiBase)


    private fun buildOpenAIJson(prompt: String): String {
        val tempHistory = historico + Mensagem("user", prompt)

        val messages = tempHistory.joinToString(",") {
            """{ "role": "${it.role}", "content": "${escape(it.text)}" }"""
        }

        val extras = mutableListOf<String>()

        // sempre válido
        extras.add("\"temperature\": $TEMPERATURE")
        extras.add("\"stream\": false")

        // incluir max_tokens SOMENTE para OpenRouter e LM Studio
        if (usesMaxTokens(apiBase)) {
            extras.add("\"max_tokens\": -1") // LM Studio aceita -1; OpenRouter também costuma aceitar
        }

        // extras específicos de OpenRouter/LM Studio
        if (isOpenRouter(apiBase) || isLMStudio(apiBase)) {
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


    private fun extractOpenAIContent(body: String): String {
        return try {
            val json = gson.fromJson(body, Map::class.java)
            val choices = json["choices"] as? List<*> ?: return "Erro parsing resposta"
            val first = choices.firstOrNull() as? Map<*, *> ?: return "Erro parsing resposta"

            // Formato OpenAI/OpenRouter
            val message = first["message"] as? Map<*, *>
            val content = message?.get("content") as? String
            if (!content.isNullOrBlank()) return content

            // Alguns provedores retornam "text"
            val text = first["text"] as? String
            if (!text.isNullOrBlank()) return text

            "Erro parsing resposta"
        } catch (_: Exception) {
            "Erro parsing resposta"
        }
    }


    // ================= GOOGLE GEMMA / GEMINI =================
    private fun callGoogleGemma(prompt: String): String {
        val url = "$apiBase/v1beta/models/$MODEL:generateContent?key=${apiKey ?: ""}"

        val requestBody = mapOf(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(mapOf("text" to escape(INSTRUCTS + prompt)))
                )
            ),
            "generationConfig" to mapOf("temperature" to TEMPERATURE)
        )
        val jsonBody = gson.toJson(requestBody)

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

            if (res.statusCode() != 200) {
                throw RuntimeException("HTTP ${res.statusCode()} - ${res.body()}")
            }

            extractGoogleGemmaContent(res.body())
        } catch (e: Exception) {
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
    AIHandler(Paths.get("").toAbsolutePath().toString()).start()
}
