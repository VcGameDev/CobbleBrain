package vito.cobblebrain.social

import com.google.gson.Gson
import vito.cobblebrain.config.CobblebrainConfig
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.*
import java.time.Duration
import kotlin.system.exitProcess

class AIHandler(dirPath: String) {
    val gson = Gson()
    val configFile = File("config/cobblebrain.json")
    val config: CobblebrainConfig = gson.fromJson(configFile.readText(), CobblebrainConfig::class.java)

    companion object {
        private val gson = Gson()
        private val configFile = File("config/cobblebrain.json")
        private val config: CobblebrainConfig = gson.fromJson(configFile.readText(), CobblebrainConfig::class.java)
        private val MODEL = config.aiModel

        private val INSTRUCTS = config.instruct.trimIndent()
    }
    private val apiKey = config.apiKey
    private val comandoPath: Path = Paths.get(dirPath, "comando_ia.txt")
    private val respostaPath: Path = Paths.get(dirPath, "resposta_ia.txt")

    init {
        if (!Files.exists(comandoPath)) Files.writeString(comandoPath, "")
        if (!Files.exists(respostaPath)) Files.writeString(respostaPath, "")
    }

    fun start() {
        println(Files.readString(comandoPath))

        val watchService = FileSystems.getDefault().newWatchService()
        comandoPath.parent.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)

        var lastInput = ""

        while (true) {
            val key = watchService.take()
            for (event in key.pollEvents()) {
                val changed = event.context() as Path
                if (changed.endsWith(comandoPath.fileName)) {
                    val userInput = Files.readString(comandoPath).trim()
                    if (userInput.isNotEmpty() && userInput != lastInput) {
                        enviarMensagem(userInput)
                        lastInput = userInput
                    }
                }
            }
            key.reset()
        }
    }

    private fun enviarMensagem(input: String) {
        if (input == "/end") {
            println("Encerrando IA...")
            exitProcess(0)
        } else {
            respostaNormal(input)
            println("Resposta processada")
        }
    }

    fun analisarTexto(texto: String) {
        if (texto.contains("\n")) {
            println("Tem quebra de linha real")
        }
        if (texto.contains("\\n")) {
            println("Tem texto literal \\n")
        }
    }

    private fun respostaNormal(mensagem: String) {
        val responseText = callGemini(mensagem)
        val textoFormatado = responseText.replace("\\n", "|")

        println("Resposta IA: $textoFormatado")

        Files.writeString(respostaPath, textoFormatado)
        Files.writeString(comandoPath, "")

        analisarTexto(textoFormatado)
    }

    private fun callGemini(mensagem: String): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
        val jsonBody = buildJson(mensagem)

        val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build()

        val request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            return "Erro HTTP ${response.statusCode()}: ${response.body()}"
        }

        val body = response.body()
        val marker = "\"text\":"
        val idx = body.indexOf(marker)
        if (idx != -1) {
            val start = body.indexOf('"', idx + marker.length)
            val end = body.indexOf('"', start + 1)
            if (start != -1 && end != -1) {
                return body.substring(start + 1, end)
            }
        }

        return "Não foi possível extrair texto da resposta: $body"
    }

    private fun buildJson(mensagem: String): String {
        return """
            {
              "contents": [ { "parts": [ { "text": "${escape(mensagem)}" } ] } ],
              "system_instruction": { "parts": [ { "text": "${escape(INSTRUCTS)}" } ] }
            }
        """.trimIndent()
    }

    private fun escape(text: String): String {
        return text.replace("\"", "\\\"").replace("\n", "\\n")
    }
}

fun main() {
    try {
        val dirPath = Paths.get("").toAbsolutePath().toString()
        val chat = AIHandler(dirPath)
        chat.start()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

