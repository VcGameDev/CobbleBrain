package vito.cobblebrain.config

import net.minecraft.client.Minecraft
import vito.cobblebrain.network.CobblebrainPayloads

object SyncedConfig {
    var received = false
        private set
    var isServerControlled = false
        private set

    var useDefaultOutput = true
        private set
    var outputDialogue = true
        private set
    var outputActions = true
        private set
    var outputFriendship = true
        private set
    var outputMemories = "AVERAGE"
        private set
    val isMemoriesEnabled: Boolean
        get() = !outputMemories.equals("OFF", ignoreCase = true)
    var enableRecap = true
        private set
    var outputApril1 = false
        private set
    var outputQuests = true
        private set
    var outputPokemonLanguage = false
        private set
    var needsPokemonTranslator = false
        private set
    var outputGuaranteedCatch = true
        private set
    var enableKarma = true
        private set
    var maxStoredMemories = 90
        private set
    var maxRelevantMemories = 4
        private set
    var favoriteMemorySlots = 5
        private set
    var baseCandidateMemories = 10
        private set
    var allowClientPersonalityEditing = true
        private set
    var allowCustomClientPrompt = true
        private set
    var forceOfflineMode = false
        private set
    var enableAiMemoryRetrieval = false
        private set
    var optimizedMode = true
        private set
    val monolithMode: Boolean
        get() = !optimizedMode
    var actionSettings: ActionSettings = ActionSettings()
        private set

    fun isActionActiveForPlayer(actionName: String): Boolean {
        val client = Minecraft.getInstance()
        val settings = try {
            if (client.isLocalServer) ConfigHandler.config.actionSettings else actionSettings
        } catch (_: Throwable) {
            actionSettings
        }
        return settings.isActionActiveForPlayer(actionName)
    }

    fun isActionActiveForAI(actionName: String): Boolean {
        if (!outputActions) return false
        val client = Minecraft.getInstance()
        val settings = try {
            if (client.isLocalServer) ConfigHandler.config.actionSettings else actionSettings
        } catch (_: Throwable) {
            actionSettings
        }
        return settings.isActionActiveForAI(actionName)
    }

    fun isActionActive(actionName: String): Boolean {
        return isActionActiveForPlayer(actionName) || isActionActiveForAI(actionName)
    }

    fun apply(payload: CobblebrainPayloads.SyncConfigPayload) {
        useDefaultOutput = payload.useDefaultOutput
        outputDialogue = payload.outputDialogue
        outputActions = payload.outputActions
        outputFriendship = payload.outputFriendship
        outputMemories = payload.outputMemories
        outputApril1 = payload.outputApril1
        outputQuests = payload.outputQuests
        outputPokemonLanguage = payload.outputPokemonLanguage
        needsPokemonTranslator = payload.needsPokemonTranslator
        outputGuaranteedCatch = payload.outputGuaranteedCatch
        enableKarma = payload.enableKarma
        maxStoredMemories = payload.maxStoredMemories
        maxRelevantMemories = payload.maxRelevantMemories
        favoriteMemorySlots = payload.favoriteMemorySlots
        baseCandidateMemories = payload.baseCandidateMemories
        allowClientPersonalityEditing = payload.allowClientPersonalityEditing
        allowCustomClientPrompt = payload.allowCustomClientPrompt
        forceOfflineMode = payload.forceOfflineMode
        enableAiMemoryRetrieval = payload.enableAiMemoryRetrieval
        optimizedMode = payload.optimizedMode
        if (payload.actionSettingsJson.isNotBlank()) {
            try {
                actionSettings = com.google.gson.Gson().fromJson(payload.actionSettingsJson, ActionSettings::class.java) ?: ActionSettings()
                actionSettings.migrateLegacy()
            } catch (_: Exception) {}
        }
        received = true

        val client = Minecraft.getInstance()
        val isHost = client.isLocalServer

        isServerControlled = !isHost
    }

    fun updateLocal(
        useDefaultOutput: Boolean,
        outputDialogue: Boolean,
        outputActions: Boolean,
        outputFriendship: Boolean,
        outputMemories: String = "AVERAGE",
        enableRecap: Boolean = true,
        outputApril1: Boolean,
        outputQuests: Boolean,
        outputPokemonLanguage: Boolean,
        needsPokemonTranslator: Boolean,
        outputGuaranteedCatch: Boolean,
        enableKarma: Boolean,
        maxStoredMemories: Int,
        maxRelevantMemories: Int,
        favoriteMemorySlots: Int = 5,
        baseCandidateMemories: Int = 10,
        allowClientPersonalityEditing: Boolean,
        enableAiMemoryRetrieval: Boolean = false,
        optimizedMode: Boolean = true,
        allowCustomClientPrompt: Boolean = true
    ) {
        val client = Minecraft.getInstance()
        if (isServerControlled || (client.player != null && !client.isLocalServer)) {
            println("[CobbleBrain] Attempt to change synced config blocked (connected to remote server)")
            return
        }

        this.useDefaultOutput = useDefaultOutput
        this.outputDialogue = outputDialogue
        this.outputActions = outputActions
        this.outputFriendship = outputFriendship
        this.outputMemories = outputMemories
        this.enableRecap = enableRecap
        this.outputApril1 = outputApril1
        this.outputQuests = outputQuests
        this.outputPokemonLanguage = outputPokemonLanguage
        this.needsPokemonTranslator = needsPokemonTranslator
        this.outputGuaranteedCatch = outputGuaranteedCatch
        this.enableKarma = enableKarma
        this.maxStoredMemories = maxStoredMemories
        this.maxRelevantMemories = maxRelevantMemories
        this.favoriteMemorySlots = favoriteMemorySlots
        this.baseCandidateMemories = baseCandidateMemories
        this.allowClientPersonalityEditing = allowClientPersonalityEditing
        this.enableAiMemoryRetrieval = enableAiMemoryRetrieval
        this.optimizedMode = optimizedMode
        this.allowCustomClientPrompt = allowCustomClientPrompt

        val cfg = ConfigHandler.config

        cfg.useDefaultOutput = useDefaultOutput
        cfg.outputDialogue = outputDialogue
        cfg.outputActions = outputActions
        cfg.outputFriendship = outputFriendship
        cfg.outputMemories = outputMemories
        cfg.enableRecap = enableRecap
        cfg.outputApril1 = outputApril1
        cfg.outputQuests = outputQuests
        cfg.outputPokemonLanguage = outputPokemonLanguage
        cfg.needsPokemonTranslator = needsPokemonTranslator
        cfg.outputGuaranteedCatch = outputGuaranteedCatch
        cfg.enableKarma = enableKarma
        cfg.maxStoredMemories = maxStoredMemories
        cfg.maxRelevantMemories = maxRelevantMemories
        cfg.favoriteMemorySlots = favoriteMemorySlots
        cfg.baseCandidateMemories = baseCandidateMemories
        cfg.allowClientPersonalityEditing = allowClientPersonalityEditing
        cfg.enableAiMemoryRetrieval = enableAiMemoryRetrieval
        cfg.optimizedMode = optimizedMode
        cfg.allowCustomClientPrompt = allowCustomClientPrompt

        ConfigHandler.save()
    }

    fun resetToLocal() {
        isServerControlled = false
        received = false
        try {
            val cfg = ConfigHandler.config
            useDefaultOutput = cfg.useDefaultOutput
            outputDialogue = cfg.outputDialogue
            outputActions = cfg.outputActions
            outputFriendship = cfg.outputFriendship
            outputMemories = cfg.outputMemories
            enableRecap = cfg.enableRecap
            outputApril1 = cfg.outputApril1
            outputQuests = cfg.outputQuests
            outputPokemonLanguage = cfg.outputPokemonLanguage
            needsPokemonTranslator = cfg.needsPokemonTranslator
            outputGuaranteedCatch = cfg.outputGuaranteedCatch
            enableKarma = cfg.enableKarma
            maxStoredMemories = cfg.maxStoredMemories
            maxRelevantMemories = cfg.maxRelevantMemories
            favoriteMemorySlots = cfg.favoriteMemorySlots
            baseCandidateMemories = cfg.baseCandidateMemories
            allowClientPersonalityEditing = cfg.allowClientPersonalityEditing
            allowCustomClientPrompt = cfg.allowCustomClientPrompt
            forceOfflineMode = false
            enableAiMemoryRetrieval = cfg.enableAiMemoryRetrieval
            optimizedMode = cfg.optimizedMode
            actionSettings = cfg.actionSettings
        } catch (_: Throwable) {}
    }
}
