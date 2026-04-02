package vito.cobblebrain.config

import vito.cobblebrain.network.CobblebrainPayloads

object SyncedConfig {

    var received = false
    var outputDialogue = true
    var outputActions = true
    var outputFriendship = true
    var outputMemories = true
    var outputApril1 = false
    var outputQuests = true
    var maxLongMemory = 3
    var maxShortMemory = 2

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
    }
}