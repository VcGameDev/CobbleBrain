package vito.cobblebrain.client

import java.util.concurrent.CompletableFuture

object AIClientHandler {
    private fun createHandler() = AIHandler()

    fun sendPrompt(prompt: String): CompletableFuture<String> {
        return CompletableFuture.supplyAsync {
            try {
                val handler = createHandler()
                handler.respostaNormal(prompt)
            } catch (e: Exception) {
                "Erro ao gerar resposta da IA: ${e.message}"
            }
        }
    }
}

