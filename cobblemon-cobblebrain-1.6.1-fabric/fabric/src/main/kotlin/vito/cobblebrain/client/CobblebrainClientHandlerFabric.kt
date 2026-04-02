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
                SyncedConfig.outputDialogue = payload.outputDialogue
                SyncedConfig.outputActions = payload.outputActions
                SyncedConfig.outputFriendship = payload.outputFriendship
                SyncedConfig.outputMemories = payload.outputMemories
                SyncedConfig.outputApril1 = payload.outputApril1
                SyncedConfig.outputQuests = payload.outputQuests
                SyncedConfig.maxLongMemory = payload.maxLongMemory
                SyncedConfig.maxShortMemory = payload.maxShortMemory

                SyncedConfig.received = true

                println("[CobbleBrain] Synced config received from server")
            }
        }
    }
}