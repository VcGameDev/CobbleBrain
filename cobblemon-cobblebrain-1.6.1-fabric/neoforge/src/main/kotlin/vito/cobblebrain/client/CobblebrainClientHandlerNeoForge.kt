package vito.cobblebrain.client

import vito.cobblebrain.config.SyncedConfig
import vito.cobblebrain.network.CobblebrainPayloads

object CobblebrainClientHandlers {

    fun onPrompt(payload: CobblebrainPayloads.PromptPayload) {
        CobblebrainClientRuntimeNeoForge.markResponseReceived()
        CobblebrainClientCommon.onPromptReceived(payload.prompt)
    }

    fun onConfigSync(payload: CobblebrainPayloads.SyncConfigPayload) {
        SyncedConfig.apply(payload)
        println("CONFIG RECEBIDA DO SERVER")
    }

    fun onQuestSync(payload: CobblebrainPayloads.QuestSyncPayload) {
        CobblebrainClientCommon.onQuestsSynced(payload.questsJson)
    }

    fun onSummaryPrompt(payload: CobblebrainPayloads.SummaryPromptPayload) {
        CobblebrainClientRuntimeNeoForge.markResponseReceived()
        CobblebrainClientCommon.onSummaryPromptReceived(payload.contextData)
    }

    fun onCooldownSync(payload: CobblebrainPayloads.SyncCooldownsPayload) {
        CobblebrainClientCommon.onCooldownsSynced(
            payload.buffRemaining,
            payload.repairRemaining,
            payload.shiftRemaining,
            payload.debuffRemaining
        )
    }

    fun onPersonalityList(payload: CobblebrainPayloads.PersonalityListPayload) {
        CobblebrainClientCommon.onPersonalityListReceived?.invoke(payload.dataJson)
    }
}