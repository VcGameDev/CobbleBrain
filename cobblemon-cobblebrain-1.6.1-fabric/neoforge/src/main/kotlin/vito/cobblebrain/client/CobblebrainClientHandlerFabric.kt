package vito.cobblebrain.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import vito.cobblebrain.currentServer

object CobblebrainClientHandler {
    fun registerReceivers() {
        // inicia IA
        ClientLifecycleEvents.CLIENT_STARTED.register {
            AIHandler().start()
        }

        // conecta envio do Common → Server
        CobblebrainClientCommon.sendToServer = { response ->
            if (currentServer != null) {
                ClientPlayNetworking.send(AIResponsePayload(response))
            }

            ClientPlayNetworking.send(
                AIResponsePayload(response)
            )
        }

        // recebe prompt do server
        ClientPlayNetworking.registerGlobalReceiver(PromptPayload.TYPE) { payload, context ->
            val prompt = payload.prompt

            context.client().execute {
                CobblebrainClientCommon.onPromptReceived(prompt)
            }
        }
    }

    // ===== PAYLOADS (Fabric only) =====

    data class PromptPayload(val prompt: String) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "send_prompt")
            val TYPE = CustomPacketPayload.Type<PromptPayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, PromptPayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeUtf(payload.prompt) },
                    { buf -> PromptPayload(buf.readUtf()) }
                )
        }

        override fun type() = TYPE
    }

    data class AIResponsePayload(val content: String) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "ai_response")
            val TYPE = CustomPacketPayload.Type<AIResponsePayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, AIResponsePayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeUtf(payload.content) },
                    { buf -> AIResponsePayload(buf.readUtf()) }
                )
        }

        override fun type() = TYPE
    }
    data class ActionPayload(val action: String) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "send_action")
            val TYPE = CustomPacketPayload.Type<ActionPayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, ActionPayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeUtf(payload.action) },
                    { buf -> ActionPayload(buf.readUtf()) }
                )
        }

        override fun type(): CustomPacketPayload.Type<ActionPayload> = TYPE
    }
}