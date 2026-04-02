package vito.cobblebrain.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

object CobblebrainPayloads {
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

    data class SyncConfigPayload(
        val outputDialogue: Boolean,
        val outputActions: Boolean,
        val outputFriendship: Boolean,
        val outputMemories: Boolean,
        val outputApril1: Boolean,
        val outputQuests: Boolean,
        val maxLongMemory: Int,
        val maxShortMemory: Int
    ) : CustomPacketPayload {

        companion object {
            val ID = ResourceLocation("cobblebrain", "sync_config")
            val TYPE = CustomPacketPayload.Type<SyncConfigPayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncConfigPayload> =
                StreamCodec.of(
                    { buf, payload ->
                        buf.writeBoolean(payload.outputDialogue)
                        buf.writeBoolean(payload.outputActions)
                        buf.writeBoolean(payload.outputFriendship)
                        buf.writeBoolean(payload.outputMemories)
                        buf.writeBoolean(payload.outputApril1)
                        buf.writeBoolean(payload.outputQuests)
                        buf.writeInt(payload.maxLongMemory)
                        buf.writeInt(payload.maxShortMemory)
                    },
                    { buf ->
                        SyncConfigPayload(
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readInt(),
                            buf.readInt()
                        )
                    }
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

        override fun type() = TYPE
    }
}