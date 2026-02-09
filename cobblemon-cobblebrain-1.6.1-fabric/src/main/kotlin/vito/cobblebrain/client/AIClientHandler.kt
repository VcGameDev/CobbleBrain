package vito.cobblebrain.client

import java.util.concurrent.CompletableFuture

object AIClientHandler {
    private val handler = AIHandler()

    fun sendPrompt(prompt: String): CompletableFuture<String> {
        return CompletableFuture.supplyAsync {
            try {
                handler.respostaNormal(prompt) // chamada pesada
            } catch (e: Exception) {
                "Erro ao gerar resposta da IA: ${e.message}"
            }
        }
    }
}

