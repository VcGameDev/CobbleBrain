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

    fun executeMemoryRetrieval(playerMessage: String, candidateMemories: List<String>): CompletableFuture<String> {
        return CompletableFuture.supplyAsync {
            try {
                val handler = createHandler()
                handler.executeMemoryRetrieval(playerMessage, candidateMemories)
            } catch (e: Exception) {
                "NO_MEMORY"
            }
        }
    }

    fun sendSummaryPrompt(contextData: String): CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync {
            try {
                val handler = createHandler()
                handler.generateSessionSummary(contextData)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendBackgroundPrompt(prompt: String): CompletableFuture<String> {
        return CompletableFuture.supplyAsync {
            try {
                val handler = createHandler()
                handler.generateBackgroundState(prompt)
            } catch (e: Exception) {
                "Background state evaluation error: ${e.message}"
            }
        }
    }
}

