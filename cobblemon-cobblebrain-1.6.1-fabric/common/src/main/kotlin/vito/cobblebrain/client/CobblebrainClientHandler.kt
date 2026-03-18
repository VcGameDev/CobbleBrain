package vito.cobblebrain.client

object CobblebrainClientCommon {

    // Fabric vai injetar isso
    var sendToServer: ((String) -> Unit)? = null

    fun onPromptReceived(prompt: String) {
        AIClientHandler.sendPrompt(prompt).thenAccept { response ->
            // quando a IA responder → manda pro server
            sendToServer?.invoke(response)
        }.exceptionally { e ->
            e.printStackTrace()
            null
        }
    }
}