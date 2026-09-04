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
    var favoriteMemorySlots = 5
        private set
    var baseCandidateMemories = 10
        private set
    var allowClientPersonalityEditing = true
        private set
    var forceOfflineMode = false
        private set
    var enableAiMemoryRetrieval = false
        private set
    var optimizedMode = true
        private set
    var actionSettings: ActionSettings = ActionSettings()
        private set

    fun isActionActive(actionName: String): Boolean {
        val client = Minecraft.getInstance()
        val settings = try {
            if (client.isLocalServer) ConfigHandler.config.actionSettings else actionSettings
        } catch (_: Throwable) {
            actionSettings
        }
        val key = actionName.lowercase().trim().replace(" ", "_")
        return when (key) {
            "cook" -> settings.cook.active
            "grow" -> settings.grow.active
            "repair" -> settings.repair.active
            "shift" -> settings.shift.active
            "fish" -> settings.fish.active
            "nightmare" -> settings.nightmare.active
            "light" -> settings.light.active
            "scout" -> settings.scout.active
            "teleport" -> settings.teleport.active
            "attack" -> settings.attack.active
            "protect" -> settings.protect.active
            "eat" -> settings.eat.active
            "buff" -> settings.buff.active
            "debuff", "debuff_enemy" -> settings.debuffEnemy.active
            "excavate", "demolish" -> settings.excavate.active
            "rest", "sit" -> settings.rest.active
            "idle" -> settings.idle.active
            else -> true
        }
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
        forceOfflineMode = payload.forceOfflineMode
        enableAiMemoryRetrieval = payload.enableAiMemoryRetrieval
        optimizedMode = payload.optimizedMode
        if (payload.actionSettingsJson.isNotBlank()) {
            try {
                actionSettings = com.google.gson.Gson().fromJson(payload.actionSettingsJson, ActionSettings::class.java) ?: ActionSettings()
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
        outputMemories: Boolean,
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
        optimizedMode: Boolean = true
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
        this.favoriteMemorySlots = favoriteMemorySlots
        this.baseCandidateMemories = baseCandidateMemories
        this.allowClientPersonalityEditing = allowClientPersonalityEditing
        this.enableAiMemoryRetrieval = enableAiMemoryRetrieval
        this.optimizedMode = optimizedMode

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
        cfg.favoriteMemorySlots = favoriteMemorySlots
        cfg.baseCandidateMemories = baseCandidateMemories
        cfg.allowClientPersonalityEditing = allowClientPersonalityEditing
        cfg.enableAiMemoryRetrieval = enableAiMemoryRetrieval
        cfg.optimizedMode = optimizedMode

        ConfigHandler.save()
    }

    fun resetToLocal() {
        isServerControlled = false
        received = false
    }
}
