package vito.cobblebrain.client

import net.minecraft.client.KeyMapping

object CobblebrainClientCommon {
    var openConfigScreen: (() -> Unit)? = null
    fun openConfig() {
        openConfigScreen?.invoke()
    }

    // Fabric/NeoForge vão injetar isso
    var sendToServer: ((String) -> Unit)? = null
    var callTeamAction: ((String) -> Unit)? = null
    var sendNicknameToServer: ((String) -> Unit)? = null
    var sendOfflineSettingsToServer: ((Boolean, Boolean) -> Unit)? = null

    // HUD Quests
    var currentQuestsJson: String = "[]"
    
    // KeyMappings para a HUD dinâmica
    var keyUp: KeyMapping? = null
    var keyDown: KeyMapping? = null
    var keyExecute: KeyMapping? = null
    var keyToggle: KeyMapping? = null

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

    fun onCooldownsSynced(buff: Long, repair: Long, shift: Long, debuff: Long) {
        HudSystem.updateCooldowns(buff, repair, shift, debuff)
    }
}