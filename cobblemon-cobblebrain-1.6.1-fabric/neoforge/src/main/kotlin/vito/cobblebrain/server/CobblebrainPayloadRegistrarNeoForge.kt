package vito.cobblebrain.server

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import vito.cobblebrain.client.CobblebrainClientHandlers
import vito.cobblebrain.network.CobblebrainPayloads

object CobblebrainPayloadRegistrarNeoForge {

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("cobblebrain").versioned("1.0")

        // =========================
        // SERVER → CLIENT
        // =========================

        registrar.playToClient(
            CobblebrainPayloads.PromptPayload.TYPE,
            CobblebrainPayloads.PromptPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                CobblebrainClientHandlers.onPrompt(payload)
            }
        }

        registrar.playToClient(
            CobblebrainPayloads.SyncConfigPayload.TYPE,
            CobblebrainPayloads.SyncConfigPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                CobblebrainClientHandlers.onConfigSync(payload)
            }
        }

        registrar.playToClient(
            CobblebrainPayloads.QuestSyncPayload.TYPE,
            CobblebrainPayloads.QuestSyncPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                CobblebrainClientHandlers.onQuestSync(payload)
            }
        }

        registrar.playToClient(
            CobblebrainPayloads.SummaryPromptPayload.TYPE,
            CobblebrainPayloads.SummaryPromptPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                CobblebrainClientHandlers.onSummaryPrompt(payload)
            }
        }

        registrar.playToClient(
            CobblebrainPayloads.BackgroundPromptPayload.TYPE,
            CobblebrainPayloads.BackgroundPromptPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                CobblebrainClientHandlers.onBackgroundPrompt(payload)
            }
        }

        registrar.playToClient(
            CobblebrainPayloads.SyncCooldownsPayload.TYPE,
            CobblebrainPayloads.SyncCooldownsPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                CobblebrainClientHandlers.onCooldownSync(payload)
            }
        }

        // =========================
        // CLIENT → SERVER
        // =========================

        registrar.playToServer(
            CobblebrainPayloads.ActionPayload.TYPE,
            CobblebrainPayloads.ActionPayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer

            context.enqueueWork {
                CobblebrainServerHandlers.onAction(player, payload)
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.AIResponsePayload.TYPE,
            CobblebrainPayloads.AIResponsePayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer

            context.enqueueWork {
                CobblebrainServerHandlers.onAIResponse(player, payload)
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.BackgroundResponsePayload.TYPE,
            CobblebrainPayloads.BackgroundResponsePayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer

            context.enqueueWork {
                CobblebrainServerHandlers.onBackgroundResponse(player, payload)
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.RequestSummaryPayload.TYPE,
            CobblebrainPayloads.RequestSummaryPayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer

            context.enqueueWork {
                vito.cobblebrain.social.DialogueSystem.triggerSessionSummary(player)
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.RequestPromptWithMemoryPayload.TYPE,
            CobblebrainPayloads.RequestPromptWithMemoryPayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer

            context.enqueueWork {
                CobblebrainServerHandlers.onRequestPromptWithMemory(player, payload)
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.PlayerNicknamePayload.TYPE,
            CobblebrainPayloads.PlayerNicknamePayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer

            context.enqueueWork {
                vito.cobblebrain.social.PlayerNicknameManager.set(player.uuid, payload.preferredName)
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.OfflineSettingsPayload.TYPE,
            CobblebrainPayloads.OfflineSettingsPayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer

            context.enqueueWork {
                val forceOfflineMode = vito.cobblebrain.config.ConfigHandler.config.forceOfflineMode
                vito.cobblebrain.social.OfflinePlayers.offlineMode[player.uuid] = forceOfflineMode || payload.offlineMode
                vito.cobblebrain.social.OfflinePlayers.offlineTalkMode[player.uuid] = payload.offlineTalkMode
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.VoiceInputPayload.TYPE,
            CobblebrainPayloads.VoiceInputPayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer

            context.enqueueWork {
                vito.cobblebrain.social.PokemonTalkCommand.processTalk(player, payload.text, isStt = true)
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.PingPayload.TYPE,
            CobblebrainPayloads.PingPayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer

            context.enqueueWork {
                val accepted =
                    vito.cobblebrain.social.PingManager
                        .handlePingPacket(
                            player,
                            payload.pos,
                            payload.direction
                        )
                if (accepted) {
                    val level = player.serverLevel()
                    val pos = payload.pos
                    // Partículas visíveis no local do Ping
                    level.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                        pos.x + 0.5, pos.y + 1.2, pos.z + 0.5,
                        15, 0.3, 0.3, 0.3, 0.05
                    )
                    // Som de feedback
                    player.playNotifySound(
                        SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.PLAYERS,
                        0.5f,
                        1.5f
                    )
                }
            }
        }

        // =========================
        // PERSONALITY EDITOR PAYLOADS
        // =========================

        registrar.playToClient(
            CobblebrainPayloads.PersonalityListPayload.TYPE,
            CobblebrainPayloads.PersonalityListPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                CobblebrainClientHandlers.onPersonalityList(payload)
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.RequestPersonalityListPayload.TYPE,
            CobblebrainPayloads.RequestPersonalityListPayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer
            context.enqueueWork {
                CobblebrainServerHandlers.onRequestPersonalityList(player)
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.SavePersonalityPayload.TYPE,
            CobblebrainPayloads.SavePersonalityPayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer
            context.enqueueWork {
                CobblebrainServerHandlers.onSavePersonality(player, payload)
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.DeletePersonalityPayload.TYPE,
            CobblebrainPayloads.DeletePersonalityPayload.CODEC
        ) { payload, context ->
            val player = context.player() as? ServerPlayer ?: return@playToServer
            context.enqueueWork {
                CobblebrainServerHandlers.onDeletePersonality(player, payload)
            }
        }

        // =========================
        // AI DIALOGUE BOX PAYLOADS
        // =========================

        registrar.playToClient(
            CobblebrainPayloads.AIDialogueBoxPayload.TYPE,
            CobblebrainPayloads.AIDialogueBoxPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                vito.cobblebrain.client.DialogueHudOverlay.showDialogue(
                    speaker = payload.speakerName,
                    type = payload.speakerType,
                    text = payload.dialogueText,
                    freeze = payload.freezePlayer,
                    instId = payload.instanceId,
                    nId = payload.nodeId
                )
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.AdvanceAIDialoguePayload.TYPE,
            CobblebrainPayloads.AdvanceAIDialoguePayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                val inst = vito.cobblebrain.engine.StoryExecutor.activeStories.values.find { it.storyId == payload.instanceId }
                if (inst != null) {
                    val node = inst.project.scenes.flatMap { it.nodes }.find { it.id == payload.nodeId }
                    if (node != null) {
                        val outPort = node.outputs.find { it.name.equals("OUT", true) || it.name.equals("OUT_SUCCESS", true) } ?: node.outputs.firstOrNull()
                        if (outPort != null) {
                            vito.cobblebrain.engine.StoryExecutor.continuePortConnections(inst, node, outPort.id, 1)
                        }
                    }
                }
            }
        }

        registrar.playToClient(
            CobblebrainPayloads.SetEntityTexturePayload.TYPE,
            CobblebrainPayloads.SetEntityTexturePayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                val tex = vito.cobblebrain.social.StoryAssetManager.getOrCreateDynamicTexture(payload.storyId, payload.textureName)
                if (tex != null) {
                    vito.cobblebrain.social.StoryAssetManager.setEntityOverride(payload.entityId, tex)
                }
            }
        }

        registrar.playToClient(
            CobblebrainPayloads.ClearEntityTexturePayload.TYPE,
            CobblebrainPayloads.ClearEntityTexturePayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                vito.cobblebrain.social.StoryAssetManager.clearEntityOverride(payload.entityId)
            }
        }

        registrar.playToClient(
            CobblebrainPayloads.StoryDebugSyncPayload.TYPE,
            CobblebrainPayloads.StoryDebugSyncPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
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

        registrar.playToClient(
            CobblebrainPayloads.StorySessionStateSyncPayload.TYPE,
            CobblebrainPayloads.StorySessionStateSyncPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                vito.cobblebrain.engine.StoryDebugger.updateSessionStateFromPayload(payload)
            }
        }

        registrar.playToServer(
            CobblebrainPayloads.StoryControlRequestPayload.TYPE,
            CobblebrainPayloads.StoryControlRequestPayload.CODEC
        ) { payload, context ->
            context.enqueueWork {
                when (payload.action) {
                    "PAUSE" -> vito.cobblebrain.engine.StoryExecutor.pauseStory(payload.storyId)
                    "RESUME" -> vito.cobblebrain.engine.StoryExecutor.resumeStory(payload.storyId)
                    "STOP" -> vito.cobblebrain.engine.StoryExecutor.stopStory(payload.storyId)
                }
            }
        }
    }
}
