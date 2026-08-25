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

        CobblebrainClientCommon.sendVoiceInputToServer = { text ->
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                ClientPlayNetworking.send(
                    CobblebrainPayloads.VoiceInputPayload(text)
                )
            }
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

        CobblebrainClientCommon.savePersonality = { uuid, json, memoriesJson ->
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                ClientPlayNetworking.send(CobblebrainPayloads.SavePersonalityPayload(uuid, json, memoriesJson))
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

        DialogueHudOverlay.onAdvanceCallback = { instId, nodeId ->
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                ClientPlayNetworking.send(CobblebrainPayloads.AdvanceAIDialoguePayload(instId, nodeId))
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(
            CobblebrainPayloads.PersonalityListPayload.TYPE
        ) { payload, context ->
            context.client().execute {
                CobblebrainClientCommon.onPersonalityListReceived?.invoke(payload.dataJson)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(
            CobblebrainPayloads.AIDialogueBoxPayload.TYPE
        ) { payload, context ->
            context.client().execute {
                DialogueHudOverlay.showDialogue(
                    speaker = payload.speakerName,
                    type = payload.speakerType,
                    text = payload.dialogueText,
                    freeze = payload.freezePlayer,
                    instId = payload.instanceId,
                    nId = payload.nodeId
                )
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(
            CobblebrainPayloads.SetEntityTexturePayload.TYPE
        ) { payload, context ->
            context.client().execute {
                val tex = vito.cobblebrain.social.StoryAssetManager.getOrCreateDynamicTexture(payload.storyId, payload.textureName)
                if (tex != null) {
                    vito.cobblebrain.social.StoryAssetManager.setEntityOverride(payload.entityId, tex)
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(
            CobblebrainPayloads.ClearEntityTexturePayload.TYPE
        ) { payload, context ->
            context.client().execute {
                vito.cobblebrain.social.StoryAssetManager.clearEntityOverride(payload.entityId)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(
            CobblebrainPayloads.StoryDebugSyncPayload.TYPE
        ) { payload, context ->
            context.client().execute {
                val nodeType = try { vito.cobblebrain.model.NodeType.valueOf(payload.blockType) } catch (_: Exception) { vito.cobblebrain.model.NodeType.ACTION }
                val status = try { vito.cobblebrain.engine.NodeExecutionStatus.valueOf(payload.status) } catch (_: Exception) { vito.cobblebrain.engine.NodeExecutionStatus.IDLE }
                vito.cobblebrain.engine.StoryDebugger.recordLog(
                    storyId = payload.storyId,
                    blockId = payload.blockId,
                    blockType = nodeType,
                    status = status,
                    level = payload.level,
                    message = payload.message,
                    details = payload.details.takeIf { it.isNotBlank() },
                    server = null
                )
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(
            CobblebrainPayloads.StorySessionStateSyncPayload.TYPE
        ) { payload, context ->
            context.client().execute {
                vito.cobblebrain.engine.StoryDebugger.updateSessionStateFromPayload(payload)
            }
        }
    }
}
