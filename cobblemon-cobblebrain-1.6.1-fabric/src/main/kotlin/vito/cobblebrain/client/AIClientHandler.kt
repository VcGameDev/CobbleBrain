package vito.cobblebrain.client

object AIClientHandler {
    // instancia única do AIHandler, apontando para a pasta "cobblebrain-ai"
    private val handler = AIHandler()

    fun sendPrompt(prompt: String): String {
        // chama diretamente o metodo que gera resposta
        return try {
            // usa a mesma lógica do AIHandler (respostaNormal)
            handler.respostaNormal(prompt)
        } catch (e: Exception) {
            "Erro ao gerar resposta da IA: ${e.message}"
        }
    }
}
