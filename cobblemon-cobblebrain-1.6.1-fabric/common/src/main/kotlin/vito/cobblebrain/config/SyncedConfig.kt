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
    var outputMemories = false
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
    var maxStoredMemories = 100
        private set
    var maxRelevantMemories = 4
        private set
    var baseCandidateMemories = 10
        private set
    var allowClientPersonalityEditing = true
        private set
    var forceOfflineMode = false
        private set
    var enableAiMemoryRetrieval = false
        private set

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
        baseCandidateMemories = payload.baseCandidateMemories
        allowClientPersonalityEditing = payload.allowClientPersonalityEditing
        forceOfflineMode = payload.forceOfflineMode
        enableAiMemoryRetrieval = payload.enableAiMemoryRetrieval
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
        outputMemories: Boolean,
        outputApril1: Boolean,
        outputQuests: Boolean,
        outputPokemonLanguage: Boolean,
        needsPokemonTranslator: Boolean,
        outputGuaranteedCatch: Boolean,
        enableKarma: Boolean,
        maxStoredMemories: Int,
        maxRelevantMemories: Int,
        baseCandidateMemories: Int = 10,
        allowClientPersonalityEditing: Boolean,
        enableAiMemoryRetrieval: Boolean = false
    ) {
        if (isServerControlled) {
            println("Attempt to change config blocked (server-controlled)")
            return
        }

        this.useDefaultOutput = useDefaultOutput
        this.outputDialogue = outputDialogue
        this.outputActions = outputActions
        this.outputFriendship = outputFriendship
        this.outputMemories = outputMemories
        this.outputApril1 = outputApril1
        this.outputQuests = outputQuests
        this.outputPokemonLanguage = outputPokemonLanguage
        this.needsPokemonTranslator = needsPokemonTranslator
        this.outputGuaranteedCatch = outputGuaranteedCatch
        this.enableKarma = enableKarma
        this.maxStoredMemories = maxStoredMemories
        this.maxRelevantMemories = maxRelevantMemories
        this.baseCandidateMemories = baseCandidateMemories
        this.allowClientPersonalityEditing = allowClientPersonalityEditing
        this.enableAiMemoryRetrieval = enableAiMemoryRetrieval

        val cfg = ConfigHandler.config

        cfg.useDefaultOutput = useDefaultOutput
        cfg.outputDialogue = outputDialogue
        cfg.outputActions = outputActions
        cfg.outputFriendship = outputFriendship
        cfg.outputMemories = outputMemories
        cfg.outputApril1 = outputApril1
        cfg.outputQuests = outputQuests
        cfg.outputPokemonLanguage = outputPokemonLanguage
        cfg.needsPokemonTranslator = needsPokemonTranslator
        cfg.outputGuaranteedCatch = outputGuaranteedCatch
        cfg.enableKarma = enableKarma
        cfg.maxStoredMemories = maxStoredMemories
        cfg.maxRelevantMemories = maxRelevantMemories
        cfg.baseCandidateMemories = baseCandidateMemories
        cfg.allowClientPersonalityEditing = allowClientPersonalityEditing
        cfg.enableAiMemoryRetrieval = enableAiMemoryRetrieval

        ConfigHandler.save()
    }

    fun resetToLocal() {
        isServerControlled = false
        received = false
    }
}
