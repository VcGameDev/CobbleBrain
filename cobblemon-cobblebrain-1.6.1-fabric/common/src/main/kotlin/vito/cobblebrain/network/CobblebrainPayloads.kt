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

    data class SummaryPromptPayload(val contextData: String) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "summary_prompt")
            val TYPE = CustomPacketPayload.Type<SummaryPromptPayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, SummaryPromptPayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeUtf(payload.contextData) },
                    { buf -> SummaryPromptPayload(buf.readUtf()) }
                )
        }

        override fun type() = TYPE
    }

    data class SyncConfigPayload(
        val useDefaultOutput: Boolean,
        val outputDialogue: Boolean,
        val outputActions: Boolean,
        val outputFriendship: Boolean,
        val outputMemories: Boolean,
        val outputApril1: Boolean,
        val outputQuests: Boolean,
        val outputPokemonLanguage: Boolean,
        val needsPokemonTranslator: Boolean,
        val outputGuaranteedCatch: Boolean,
        val enableKarma: Boolean,
        val maxStoredMemories: Int,
        val maxRelevantMemories: Int,
        val favoriteMemorySlots: Int = 5,
        val baseCandidateMemories: Int,
        val allowClientPersonalityEditing: Boolean,
        val forceOfflineMode: Boolean,
        val enableAiMemoryRetrieval: Boolean,
        val actionSettingsJson: String = ""
    ) : CustomPacketPayload {

        companion object {
            val ID = ResourceLocation("cobblebrain", "sync_config")
            val TYPE = CustomPacketPayload.Type<SyncConfigPayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncConfigPayload> =
                StreamCodec.of(
                    { buf, payload ->
                        buf.writeBoolean(payload.useDefaultOutput)
                        buf.writeBoolean(payload.outputDialogue)
                        buf.writeBoolean(payload.outputActions)
                        buf.writeBoolean(payload.outputFriendship)
                        buf.writeBoolean(payload.outputMemories)
                        buf.writeBoolean(payload.outputApril1)
                        buf.writeBoolean(payload.outputQuests)
                        buf.writeBoolean(payload.outputPokemonLanguage)
                        buf.writeBoolean(payload.needsPokemonTranslator)
                        buf.writeBoolean(payload.outputGuaranteedCatch)
                        buf.writeBoolean(payload.enableKarma)
                        buf.writeInt(payload.maxStoredMemories)
                        buf.writeInt(payload.maxRelevantMemories)
                        buf.writeInt(payload.favoriteMemorySlots)
                        buf.writeInt(payload.baseCandidateMemories)
                        buf.writeBoolean(payload.allowClientPersonalityEditing)
                        buf.writeBoolean(payload.forceOfflineMode)
                        buf.writeBoolean(payload.enableAiMemoryRetrieval)
                        buf.writeUtf(payload.actionSettingsJson)
                    },
                    { buf ->
                        SyncConfigPayload(
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readUtf()
                        )
                    }
                )
        }

        override fun type() = TYPE
    }

    data class RequestPromptWithMemoryPayload(val memoryText: String) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "request_prompt_memory")
            val TYPE = CustomPacketPayload.Type<RequestPromptWithMemoryPayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestPromptWithMemoryPayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeUtf(payload.memoryText) },
                    { buf -> RequestPromptWithMemoryPayload(buf.readUtf()) }
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

    data class QuestSyncPayload(val questsJson: String) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "sync_quests")
            val TYPE = CustomPacketPayload.Type<QuestSyncPayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, QuestSyncPayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeUtf(payload.questsJson) },
                    { buf -> QuestSyncPayload(buf.readUtf()) }
                )
        }

        override fun type() = TYPE
    }

    object RequestSummaryPayload : CustomPacketPayload {
        val ID = ResourceLocation("cobblebrain", "request_summary")
        val TYPE = CustomPacketPayload.Type<RequestSummaryPayload>(ID)

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestSummaryPayload> =
            StreamCodec.unit(RequestSummaryPayload)

        override fun type() = TYPE
    }

    data class SyncCooldownsPayload(
        val buffRemaining: Long,
        val repairRemaining: Long,
        val shiftRemaining: Long,
        val debuffRemaining: Long
    ) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "sync_cooldowns")
            val TYPE = CustomPacketPayload.Type<SyncCooldownsPayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncCooldownsPayload> =
                StreamCodec.of(
                    { buf, payload ->
                        buf.writeLong(payload.buffRemaining)
                        buf.writeLong(payload.repairRemaining)
                        buf.writeLong(payload.shiftRemaining)
                        buf.writeLong(payload.debuffRemaining)
                    },
                    { buf ->
                        SyncCooldownsPayload(
                            buf.readLong(),
                            buf.readLong(),
                            buf.readLong(),
                            buf.readLong()
                        )
                    }
                )
        }

        override fun type() = TYPE
    }

    data class PlayerNicknamePayload(val preferredName: String) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "player_nickname")
            val TYPE = CustomPacketPayload.Type<PlayerNicknamePayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, PlayerNicknamePayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeUtf(payload.preferredName) },
                    { buf -> PlayerNicknamePayload(buf.readUtf()) }
                )
        }

        override fun type() = TYPE
    }

    data class OfflineSettingsPayload(val offlineMode: Boolean, val offlineTalkMode: Boolean) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "offline_settings")
            val TYPE = CustomPacketPayload.Type<OfflineSettingsPayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, OfflineSettingsPayload> =
                StreamCodec.of(
                    { buf, payload ->
                        buf.writeBoolean(payload.offlineMode)
                        buf.writeBoolean(payload.offlineTalkMode)
                    },
                    { buf ->
                        OfflineSettingsPayload(
                            buf.readBoolean(),
                            buf.readBoolean()
                        )
                    }
                )
        }

        override fun type() = TYPE
    }

    data class VoiceInputPayload(val text: String) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "voice_input")
            val TYPE = CustomPacketPayload.Type<VoiceInputPayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, VoiceInputPayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeUtf(payload.text) },
                    { buf -> VoiceInputPayload(buf.readUtf()) }
                )
        }

        override fun type() = TYPE
    }

    data class PingPayload(
        val pos: net.minecraft.core.BlockPos,
        val direction: net.minecraft.core.Direction
    ) : CustomPacketPayload {

        companion object {
            val ID = ResourceLocation("cobblebrain", "ping")
            val TYPE = CustomPacketPayload.Type<PingPayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, PingPayload> =
                StreamCodec.of(
                    { buf, payload ->
                        buf.writeInt(payload.pos.x)
                        buf.writeInt(payload.pos.y)
                        buf.writeInt(payload.pos.z)
                        buf.writeEnum(payload.direction)
                    },
                    { buf ->
                        PingPayload(
                            net.minecraft.core.BlockPos(
                                buf.readInt(),
                                buf.readInt(),
                                buf.readInt()
                            ),
                            buf.readEnum(net.minecraft.core.Direction::class.java)
                        )
                    }
                )
        }

        override fun type() = TYPE
    }

    // ===================== PERSONALITY EDITOR =====================

    /** Client → Server: request the player's party Pokémon list with their personalities */
    object RequestPersonalityListPayload : CustomPacketPayload {
        val ID = ResourceLocation("cobblebrain", "request_personality_list")
        val TYPE = CustomPacketPayload.Type<RequestPersonalityListPayload>(ID)

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestPersonalityListPayload> =
            StreamCodec.unit(RequestPersonalityListPayload)

        override fun type() = TYPE
    }

    /**
     * Server → Client: responds with a JSON array of party Pokémon personality data.
     * Each entry: { uuid, displayName, species, personalityJson }
     */
    data class PersonalityListPayload(val dataJson: String) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "personality_list")
            val TYPE = CustomPacketPayload.Type<PersonalityListPayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, PersonalityListPayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeUtf(payload.dataJson) },
                    { buf -> PersonalityListPayload(buf.readUtf()) }
                )
        }

        override fun type() = TYPE
    }

    /** Client → Server: save the edited personality for a specific Pokémon */
    data class SavePersonalityPayload(
        val pokemonUuid: String,
        val personalityJson: String,
        val memoriesJson: String = ""
    ) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "save_personality")
            val TYPE = CustomPacketPayload.Type<SavePersonalityPayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, SavePersonalityPayload> =
                StreamCodec.of(
                    { buf, payload ->
                        buf.writeUtf(payload.pokemonUuid)
                        buf.writeUtf(payload.personalityJson)
                        buf.writeUtf(payload.memoriesJson)
                    },
                    { buf ->
                        SavePersonalityPayload(
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf()
                        )
                    }
                )
        }

        override fun type() = TYPE
    }

    /** Client → Server: delete (reset) the personality for a specific Pokémon */
    data class DeletePersonalityPayload(val pokemonUuid: String) : CustomPacketPayload {
        companion object {
            val ID = ResourceLocation("cobblebrain", "delete_personality")
            val TYPE = CustomPacketPayload.Type<DeletePersonalityPayload>(ID)

            val CODEC: StreamCodec<RegistryFriendlyByteBuf, DeletePersonalityPayload> =
                StreamCodec.of(
                    { buf, payload -> buf.writeUtf(payload.pokemonUuid) },
                    { buf -> DeletePersonalityPayload(buf.readUtf()) }
                )
        }

        override fun type() = TYPE
    }
}
