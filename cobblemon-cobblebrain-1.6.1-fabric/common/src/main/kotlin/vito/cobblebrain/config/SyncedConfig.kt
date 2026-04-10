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
    var maxLongMemory = 3
        private set
    var maxShortMemory = 2
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
        maxLongMemory = payload.maxLongMemory
        maxShortMemory = payload.maxShortMemory
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
        maxLongMemory: Int,
        maxShortMemory: Int
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
        this.maxLongMemory = maxLongMemory
        this.maxShortMemory = maxShortMemory

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
        cfg.maxLongMemory = maxLongMemory
        cfg.maxShortMemory = maxShortMemory

        ConfigHandler.save()
    }

    fun resetToLocal() {
        isServerControlled = false
        received = false
    }
}