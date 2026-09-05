package vito.cobblebrain.server

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import vito.cobblebrain.network.CobblebrainPayloads.ActionPayload
import vito.cobblebrain.network.CobblebrainPayloads.AIResponsePayload

object CobblebrainServerHandlerFabric {
    fun register() {
        // ACTION do client
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE) { payload: ActionPayload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                //player.sendSystemMessage(Component.literal("Executing action: ${payload.action}"))

                // chama o Common
                CobblebrainServerHandler.processAction(player, payload.action)
            }
        }

        // Resposta da IA (Stage 1 Foreground)
        ServerPlayNetworking.registerGlobalReceiver(AIResponsePayload.TYPE) { payload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                println("[SERVER RECEIVED RESPONSE] from ${player.name.string}")

                // chama o Common
                CobblebrainServerHandler.processIaResponse(player.server, player, payload.content)
            }
        }

        // Resposta de Background (Stage 2 Background State Resolution)
        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.BackgroundResponsePayload.TYPE) { payload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                println("[SERVER RECEIVED BACKGROUND RESPONSE] from ${player.name.string}")

                // chama o Common
                CobblebrainServerHandler.processBackgroundResponse(player.server, player, payload.content)
            }
        }

        // Requisição de Resumo (Tecla L)
        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.RequestSummaryPayload.TYPE) { _, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                vito.cobblebrain.social.DialogueSystem.triggerSessionSummary(player)
            }
        }

        // Requisição de Rebuild de Prompt com Memória
        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.RequestPromptWithMemoryPayload.TYPE) { payload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                vito.cobblebrain.social.DialogueSystem.rebuildPromptForPlayer(player, payload.memoryText)
            }
        }

        // Recebimento de Nickname Preferido do jogador
        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.PlayerNicknamePayload.TYPE) { payload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                vito.cobblebrain.social.PlayerNicknameManager.set(player.uuid, payload.preferredName)
            }
        }

        // Recebimento de Offline Settings do jogador
        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.OfflineSettingsPayload.TYPE) { payload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                val forceOfflineMode = vito.cobblebrain.config.ConfigHandler.config.forceOfflineMode
                vito.cobblebrain.social.OfflinePlayers.offlineMode[player.uuid] = forceOfflineMode || payload.offlineMode
                vito.cobblebrain.social.OfflinePlayers.offlineTalkMode[player.uuid] = payload.offlineTalkMode
            }
        }

        // Recebimento de Entrada de Voz (STT)
        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.VoiceInputPayload.TYPE) { payload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                vito.cobblebrain.social.PokemonTalkCommand.processTalk(player, payload.text, isStt = true)
            }
        }

        // Recebimento de Ping do jogador
        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.PingPayload.TYPE) { payload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
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

        // PERSONALITY EDITOR REQUESTS
        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.RequestPersonalityListPayload.TYPE) { payload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                CobblebrainServerHandler.handleRequestPersonalityList(player)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.SavePersonalityPayload.TYPE) { payload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                CobblebrainServerHandler.handleSavePersonality(player, payload.pokemonUuid, payload.personalityJson, payload.memoriesJson)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.DeletePersonalityPayload.TYPE) { payload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                CobblebrainServerHandler.handleDeletePersonality(player, payload.pokemonUuid)
            }
        }

        // Advance AI Dialogue Payload
        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.AdvanceAIDialoguePayload.TYPE) { payload, context ->
            context.server().execute {
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

        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.StoryControlRequestPayload.TYPE) { payload, context ->
            context.server().execute {
                val player = context.player()
                val server = context.server()
                val canControl = player.hasPermissions(3) ||
                    (server.isSingleplayer && server.isSingleplayerOwner(player.gameProfile))

                if (!canControl) {
                    println("[CobbleBrain Security] Player ${player.scoreboardName} attempted to ${payload.action} story '${payload.storyId}' without Level 3 permissions.")
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[CobbleBrain] Insufficient permissions: Requires Level 3 (Admin) to manage stories."))
                    return@execute
                }

                when (payload.action) {
                    "START" -> {
                        val project = vito.cobblebrain.model.StorySerializer.loadByName(payload.storyId)
                        if (project != null) {
                            vito.cobblebrain.engine.StoryExecutor.startStory(project, player, server)
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[CobbleBrain] Story '${project.name}' started successfully!"))
                        } else {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[CobbleBrain] Story pack '${payload.storyId}' not found in storypacks!"))
                        }
                    }
                    "PAUSE" -> vito.cobblebrain.engine.StoryExecutor.pauseStory(payload.storyId)
                    "RESUME" -> vito.cobblebrain.engine.StoryExecutor.resumeStory(payload.storyId)
                    "STOP" -> vito.cobblebrain.engine.StoryExecutor.stopStory(payload.storyId)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.KeyInputResultPayload.TYPE) { payload, context ->
            context.server().execute {
                vito.cobblebrain.engine.StoryExecutor.handleKeyInputResult(context.player(), payload.storyId, payload.nodeId, payload.resultEvent)
            }
        }
    }
}
