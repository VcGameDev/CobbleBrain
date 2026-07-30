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

        CobblebrainClientCommon.sendNicknameToServer = { nickname ->
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                ClientPlayNetworking.send(
                    CobblebrainPayloads.PlayerNicknamePayload(nickname)
                )
            }
        }

        CobblebrainClientCommon.sendOfflineSettingsToServer = { offlineMode, offlineTalkMode ->
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                ClientPlayNetworking.send(
                    CobblebrainPayloads.OfflineSettingsPayload(offlineMode, offlineTalkMode)
                )
            }
        }

        CobblebrainClientCommon.requestPersonalityList = {
            val mc = net.minecraft.client.Minecraft.getInstance()
            if (mc.player != null) {
                ClientPlayNetworking.send(CobblebrainPayloads.RequestPersonalityListPayload)
            } else {
                mc.setScreen(PersonalityListScreen(mc.screen, "[]", noWorld = true))
            }
        }

        CobblebrainClientCommon.savePersonality = { uuid, json ->
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                ClientPlayNetworking.send(CobblebrainPayloads.SavePersonalityPayload(uuid, json))
            }
        }

        CobblebrainClientCommon.deletePersonality = { uuid ->
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                ClientPlayNetworking.send(CobblebrainPayloads.DeletePersonalityPayload(uuid))
            }
        }

        CobblebrainClientCommon.sendRequestPromptWithMemory = { memoryText ->
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                ClientPlayNetworking.send(CobblebrainPayloads.RequestPromptWithMemoryPayload(memoryText))
            }
        }

        PingClient.sendPingToServer = { pos, direction ->
            if (
                net.minecraft.client.Minecraft
                    .getInstance()
                    .player != null
            ) {

                ClientPlayNetworking.send(
                    CobblebrainPayloads.PingPayload(
                        pos,
                        direction
                    )
                )
            }
        }

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register { handler, sender, client ->
            client.execute {
                val config = vito.cobblebrain.config.ClientConfigHandler.clientConfig
                val nickname = config.preferredName.ifBlank { client.user.name }
                val forceOfflineMode = SyncedConfig.forceOfflineMode && !client.isLocalServer
                CobblebrainClientCommon.sendNicknameToServer?.invoke(nickname)
                CobblebrainClientCommon.sendOfflineSettingsToServer?.invoke(
                    config.offlineMode || forceOfflineMode,
                    config.offlineTalkMode
                )
            }
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

        ClientPlayNetworking.registerGlobalReceiver(
            CobblebrainPayloads.PersonalityListPayload.TYPE
        ) { payload, context ->
            context.client().execute {
                CobblebrainClientCommon.onPersonalityListReceived?.invoke(payload.dataJson)
            }
        }
    }
}
