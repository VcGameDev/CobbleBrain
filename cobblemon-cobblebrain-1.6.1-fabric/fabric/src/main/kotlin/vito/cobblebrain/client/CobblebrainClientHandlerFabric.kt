package vito.cobblebrain.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import vito.cobblebrain.config.SyncedConfig
import vito.cobblebrain.network.CobblebrainPayloads

object CobblebrainClientHandlerFabric {
    fun registerReceivers() {
        // inicia IA
        ClientLifecycleEvents.CLIENT_STARTED.register {
            AIHandler().start()
        }

        // CLIENT → SERVER
        CobblebrainClientCommon.sendToServer = { response ->
            ClientPlayNetworking.send(
                CobblebrainPayloads.AIResponsePayload(response)
            )
        }

        CobblebrainClientCommon.callTeamAction = { action ->
            ClientPlayNetworking.send(
                CobblebrainPayloads.ActionPayload(action)
            )
        }

        // SERVER → CLIENT
        ClientPlayNetworking.registerGlobalReceiver(
            CobblebrainPayloads.PromptPayload.TYPE
        ) { payload, context ->

            context.client().execute {
                CobblebrainClientCommon.onPromptReceived(payload.prompt)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(
            CobblebrainPayloads.SyncConfigPayload.TYPE
        ) { payload, context ->

            context.client().execute {
                SyncedConfig.apply(payload)
                println("[CobbleBrain] Synced config received from server")
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(
            CobblebrainPayloads.QuestSyncPayload.TYPE
        ) { payload, context ->
            context.client().execute {
                CobblebrainClientCommon.onQuestsSynced(payload.questsJson)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(
            CobblebrainPayloads.SummaryPromptPayload.TYPE
        ) { payload, context ->
            context.client().execute {
                CobblebrainClientCommon.onSummaryPromptReceived(payload.contextData)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(
            CobblebrainPayloads.SyncCooldownsPayload.TYPE
        ) { payload, context ->
            context.client().execute {
                CobblebrainClientCommon.onCooldownsSynced(
                    payload.buffRemaining,
                    payload.repairRemaining,
                    payload.shiftRemaining,
                    payload.debuffRemaining
                )
            }
        }
    }
}