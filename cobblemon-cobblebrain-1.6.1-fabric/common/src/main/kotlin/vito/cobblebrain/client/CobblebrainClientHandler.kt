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
    var requestPersonalityList: (() -> Unit)? = null
    var savePersonality: ((String, String) -> Unit)? = null
    var deletePersonality: ((String) -> Unit)? = null
    var sendRequestPromptWithMemory: ((String) -> Unit)? = null

    // Callback ao receber do servidor
    var onPersonalityListReceived: ((String) -> Unit)? = null
    
    // HUD Quests
    var currentQuestsJson: String = "[]"
    
    // KeyMappings para a HUD dinâmica
    var keyUp: KeyMapping? = null
    var keyDown: KeyMapping? = null
    var keyExecute: KeyMapping? = null
    var keyToggle: KeyMapping? = null
    var keyPing: KeyMapping? = null

    fun onQuestsSynced(json: String) {
        currentQuestsJson = json
    }

    fun onPromptReceived(prompt: String) {
        if (prompt == "OPEN_CONFIG_SCREEN") {
            openConfig()
            return
        }

        if (vito.cobblebrain.config.SyncedConfig.enableAiMemoryRetrieval && !prompt.contains("[RELEVANT MEMORIES]")) {
            val playerMessage = if (prompt.contains("[PLAYER_MESSAGE]")) {
                prompt.substringAfter("[PLAYER_MESSAGE]").substringBefore("[").trim()
            } else {
                prompt.lines().lastOrNull { it.isNotBlank() } ?: ""
            }

            val candidateMemories = mutableListOf<String>()
            if (prompt.contains("[CANDIDATE_MEMORIES]")) {
                val block = prompt.substringAfter("[CANDIDATE_MEMORIES]").substringBefore("[/CANDIDATE_MEMORIES]")
                block.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank() && !trimmed.startsWith("PLAYER_MESSAGE:")) {
                        // Remove line number prefix if present (e.g., "1. ")
                        val text = trimmed.replaceFirst(Regex("^\\d+\\.\\s*"), "")
                        candidateMemories.add(text)
                    }
                }
            }

            AIClientHandler.executeMemoryRetrieval(playerMessage, candidateMemories).thenAccept { result ->
                val memoryToSend = if (result.isBlank()) "NO_MEMORY" else result
                sendRequestPromptWithMemory?.invoke(memoryToSend)
            }.exceptionally { e ->
                e.printStackTrace()
                // Fallback to NO_MEMORY prompt execution
                sendRequestPromptWithMemory?.invoke("NO_MEMORY")
                null
            }
            return
        }

        // fluxo normal da IA (quando já reconstruído com RELEVANT MEMORIES ou quando AI memory retrieval está desligado)
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