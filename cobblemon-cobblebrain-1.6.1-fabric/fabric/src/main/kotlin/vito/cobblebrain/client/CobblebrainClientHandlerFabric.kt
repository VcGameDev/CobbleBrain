package vito.cobblebrain.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import vito.cobblebrain.currentServer

object CobblebrainClientHandler {
    fun registerReceivers() {
        ClientLifecycleEvents.CLIENT_STARTED.register {
            AIHandler().start()
        }

        // Recebe PROMPT do servidor
        ClientPlayNetworking.registerGlobalReceiver(PromptPayload.TYPE) { payload, context ->
            val prompt = payload.prompt

            AIClientHandler.sendPrompt(prompt).thenAccept { resposta ->
                // volta para o thread do cliente
                context.client().execute {
                    currentServer ?: return@execute
                    ClientPlayNetworking.send(
                        AIResponsePayload(resposta)
                    )
                }
            }.exceptionally { e ->
                e.printStackTrace()
                context.client().execute {
                    context.player()?.sendSystemMessage(
                        Component.literal("Erro ao processar IA: ${e.message}")
                    )
                }
                null
            }
        }
    }

    // Payload para enviar PROMPT (Server → Client)
    data class PromptPayload(val prompt: String) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "send_prompt")
            val TYPE: CustomPacketPayload.Type<PromptPayload> = CustomPacketPayload.Type(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, PromptPayload> =
                StreamCodec.of({ buf, payload -> buf.writeUtf(payload.prompt) },
                    { buf -> PromptPayload(buf.readUtf()) })
        }

        override fun type(): CustomPacketPayload.Type<PromptPayload> = TYPE
    }

    // Payload para enviar ACTION (Client → Server)
    data class ActionPayload(val action: String) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "send_action")
            val TYPE: CustomPacketPayload.Type<ActionPayload> = CustomPacketPayload.Type(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, ActionPayload> =
                StreamCodec.of({ buf, payload -> buf.writeUtf(payload.action) },
                    { buf -> ActionPayload(buf.readUtf()) })
        }

        override fun type(): CustomPacketPayload.Type<ActionPayload> = TYPE
    }

    data class AIResponsePayload(val content: String) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "ai_response")
            val TYPE: CustomPacketPayload.Type<AIResponsePayload> =
                CustomPacketPayload.Type(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, AIResponsePayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeUtf(payload.content) },
                    { buf -> AIResponsePayload(buf.readUtf()) }
                )
        }

        override fun type(): CustomPacketPayload.Type<AIResponsePayload> = TYPE
    }
}
