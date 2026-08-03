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
                //player.sendSystemMessage(Component.literal("Executando ação: ${payload.action}"))

                // chama o Common
                CobblebrainServerHandler.processAction(player, payload.action)
            }
        }

        // Resposta da IA
        ServerPlayNetworking.registerGlobalReceiver(AIResponsePayload.TYPE) { payload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                println("[SERVER RECEIVED RESPONSE] from ${player.name.string}")

                // chama o Common
                CobblebrainServerHandler.processIaResponse(player.server, player, payload.content)
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
                CobblebrainServerHandler.handleSavePersonality(player, payload.pokemonUuid, payload.personalityJson)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(vito.cobblebrain.network.CobblebrainPayloads.DeletePersonalityPayload.TYPE) { payload, context ->
            context.server().execute {
                val player: ServerPlayer = context.player()
                CobblebrainServerHandler.handleDeletePersonality(player, payload.pokemonUuid)
            }
        }
    }
}
