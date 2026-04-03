package vito.cobblebrain.config

import vito.cobblebrain.network.CobblebrainPayloads

object SyncedConfig {
    var received = false
        private set
    var isServerControlled = false
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
    var maxLongMemory = 3
        private set
    var maxShortMemory = 2
        private set

    fun apply(payload: CobblebrainPayloads.SyncConfigPayload) {
        outputDialogue = payload.outputDialogue
        outputActions = payload.outputActions
        outputFriendship = payload.outputFriendship
        outputMemories = payload.outputMemories
        outputApril1 = payload.outputApril1
        outputQuests = payload.outputQuests
        maxLongMemory = payload.maxLongMemory
        maxShortMemory = payload.maxShortMemory
        received = true
        isServerControlled = true
    }

    fun updateLocal(
        outputDialogue: Boolean,
        outputActions: Boolean,
        outputFriendship: Boolean,
        outputMemories: Boolean,
        outputApril1: Boolean,
        outputQuests: Boolean,
        maxLongMemory: Int,
        maxShortMemory: Int
    ) {
        if (isServerControlled) {
            println("Attempt to change config blocked (server-controlled)")
            return
        }

        this.outputDialogue = outputDialogue
        this.outputActions = outputActions
        this.outputFriendship = outputFriendship
        this.outputMemories = outputMemories
        this.outputApril1 = outputApril1
        this.outputQuests = outputQuests
        this.maxLongMemory = maxLongMemory
        this.maxShortMemory = maxShortMemory
    }

    fun resetToLocal() {
        isServerControlled = false
        received = false
    }
}