package vito.cobblebrain.client

import com.google.gson.Gson
import kotlinx.io.IOException
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
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
import vito.cobblebrain.config.CobblebrainConfig
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

class AIHandler{

    companion object {
        private val gson = Gson()
        private val configFile = File("config/cobblebrain.json5")
        private val config =
            gson.fromJson(configFile.readText(), CobblebrainConfig::class.java)

        private val INSTRUCTS = config.instruct.trimIndent()
        private val TEMPERATURE = config.temperature
        private val PROVIDER_HINT = config.aiProvider.trim()
        private val REASONING = config.reasoningEffort.trim().lowercase()
        private val DEBUG = config.debugLogging
        private val TIMEOUT_SECONDS = config.requestTimeoutSeconds
    }

    // agora usando rotadores
    private val apiKeyRotator = ApiKeyRotator(config.apiKey)
    private val modelRotator = ModelRotator(config.aiModel)

    private val apiBase =
        config.apiBaseUrl.trimEnd('/').replace("localhost", "127.0.0.1")

    // ---------------- Logging ----------------
    private fun log(text: String) {
        if (!DEBUG) return
        val line = "[${LocalDateTime.now()}] $text"

        try {
            // cria a pasta se não existir
            val dir = Minecraft.getInstance().gameDirectory.toPath().resolve("cobblebrain-ai/logs")
            Files.createDirectories(dir)

            // cria um arquivo de log por sessão (ex.: data/hora de início)
            val logFile = dir.resolve(
                "log_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))}.log"
            )

            // escreve continuamente no mesmo arquivo enquanto o jogo estiver rodando
            Files.writeString(
                logFile,
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
        if (config.localApiProvider.equals("player2", ignoreCase = true)) {
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
        if (config.keyRotation && status in config.keyRotationTrigger) {
            apiKeyRotator.next()
            if (apiKeyRotator.current() == config.apiKey.first()) {
                val msg = "API key rotation has cycled through all keys and returned to the first key. First key: ${apiKeyRotator.current()}."
                sendSystemMessage(msg)
            } else {
                val msg = "API key has been rotated due to error $status. New key: ${apiKeyRotator.current()}."
                sendSystemMessage(msg)
            }
        }
    }

    private fun rotateModel(status: Int) {
        if (config.modelRotation && status in config.modelRotationTrigger) {
            modelRotator.next()
            println("func de rotate ativa")
            if (modelRotator.current() == config.aiModel.first()) {
                val msg = "Model rotation has cycled through all models and returned to the first model. First model: ${modelRotator.current()} \n"
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
                    println("Using local provider: ${config.localApiProvider}")
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

        return formatted
    }


    private fun callOpenAISchema(prompt: String): String {
        val jsonBody = buildOpenAIJson(prompt)
        log("REQUEST JSON:\n${jsonBody.lines().joinToString("\n") { "│ $it" }}")

        val builder = HttpRequest.newBuilder()
            .uri(URI.create("$apiBase/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))

        // 🔑 Autenticação
        if (config.localApiProvider.equals("player2", ignoreCase = true)) {
            // Player2 exige Game Client id no header
            builder.header("player2-game-key", "019bfb65-9ed4-79af-a348-90d86bbb6cbb")
        } else {
            // Padrão OpenAI / OpenRouter / LM Studio
            val key = apiKeyRotator.current()
            if (key.isNotBlank()) {
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

            log("HTTP ${res.statusCode()} RESPONSE:\n${res.body()}")

            if (res.statusCode() != 200) {
                // ROTACIONA AQUI
                rotateApiKey(res.statusCode())
                rotateModel(res.statusCode())
                throw RuntimeException("HTTP ${res.statusCode()}")
            }

            return extractOpenAIContent(res.body())

        } catch (e: Exception) {
            log("ERROR: ${e.message}")
            "Erro API: ${e.message}"
        }
    }

    private fun isOpenRouter(apiBase: String) =
        apiBase.contains("openrouter.ai", ignoreCase = true)

    private fun isLMStudio() =
        // ajuste conforme sua URL local do LM Studio
        config.localApiProvider.contains("lmstudio", ignoreCase = true)

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
        return if (config.localApiProvider.equals("player2", ignoreCase = true)) {
            """
        {
          "messages": [
            { "role": "system", "content": "${escape(INSTRUCTS + config.outputFormat)}" },
            $messages
          ]$extraJson
        }
        """.trimIndent()
        } else {
            """
        {
          "model": "${modelRotator.current()}",
          "messages": [
            { "role": "system", "content": "${escape(INSTRUCTS + config.outputFormat)}" },
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
        val url = "$apiBase/v1beta/models/${modelRotator.current()}:generateContent?key=${apiKeyRotator.current()}"

        val requestBody = mapOf(
            "contents" to listOf(
                // instruções fixas (equivalente ao "system" em OpenAI)
                mapOf(
                    "role" to "user",
                    "parts" to listOf(mapOf("text" to escape(INSTRUCTS + config.outputFormat))),
                ),
                // prompt real do usuário
                mapOf(
                    "role" to "user",
                    "parts" to listOf(mapOf("text" to escape(prompt)))
                )
            ),
            "generationConfig" to mapOf("temperature" to TEMPERATURE)
        )

        val jsonBody = gson.toJson(requestBody)
        log("REQUEST JSON:\n${jsonBody.lines().joinToString("\n") { "│ $it" }}")

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

            log("HTTP ${res.statusCode()} RESPONSE:\n${res.body()}")

            if (res.statusCode() != 200) {
                // ROTACIONA AQUI
                println(res.statusCode())
                rotateApiKey(res.statusCode())
                rotateModel(res.statusCode())
                throw RuntimeException("HTTP ${res.statusCode()}")
            }

            return extractGoogleGemmaContent(res.body())

        } catch (e: Exception) {
            log("ERROR: ${e.message}")
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
