package vito.cobblebrain.client

object CobblebrainClientCommon {
    var openConfigScreen: (() -> Unit)? = null
    fun openConfig() {
        openConfigScreen?.invoke()
    }

    // Fabric/NeoForge vão injetar isso
    var sendToServer: ((String) -> Unit)? = null

    // HUD Quests
    var currentQuestsJson: String = "[]"

    fun onQuestsSynced(json: String) {
        currentQuestsJson = json
    }

    fun onPromptReceived(prompt: String) {
        if (prompt == "OPEN_CONFIG_SCREEN") {
            openConfig()
            return
        }

        // fluxo normal da IA
        AIClientHandler.sendPrompt(prompt).thenAccept { response ->
            sendToServer?.invoke(response)
        }.exceptionally { e ->
            e.printStackTrace()
            null
        }
    }

    fun onSummaryPromptReceived(contextData: String) {
        AIClientHandler.sendSummaryPrompt(contextData)
    }
}