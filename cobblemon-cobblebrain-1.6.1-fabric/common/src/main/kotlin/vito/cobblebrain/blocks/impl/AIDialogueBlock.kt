package vito.cobblebrain.blocks.impl

import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import vito.cobblebrain.client.AIClientHandler
import vito.cobblebrain.engine.ActiveStoryInstance
import vito.cobblebrain.engine.StoryExecutor
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.network.CobblebrainPayloads
import vito.cobblebrain.sensors.collectWorldContext
import vito.cobblebrain.social.DialogueSystem

class AIDialogueBlock {

    fun evaluate(instance: ActiveStoryInstance, node: NodeData) {
        val player = instance.context.player
        val server = instance.context.server ?: player?.server
        val rawPrompt = node.content.ifBlank { node.params["instructionPrompt"] ?: "Speak warmly to {player} about your journey together." }
        val fallbackText = node.params["fallbackText"] ?: "..."
        val speakerType = node.params["speakerType"] ?: "PARTY_FIRST"
        val speakerId = node.params["entityStoryTag"]?.ifBlank { node.params["speakerIdentifier"] } ?: node.params["speakerIdentifier"] ?: "0"
        val displayMode = node.params["displayMode"] ?: "HUD_AND_BUBBLE"
        val allowActions = node.params["allowActions"] != "false"
        val saveVarKey = node.params["saveOutputVariable"] ?: ""
        val freezePlayer = node.params["freezePlayer"] != "false"
        val bubbleTicks = node.params["bubbleDurationTicks"]?.toIntOrNull() ?: 100

        // 1. Resolve Tokens in Instruction Prompt
        var compiledPrompt = rawPrompt
        compiledPrompt = compiledPrompt.replace("{player}", player?.name?.string ?: "Player")

        instance.context.variables.forEach { (k, v) ->
            compiledPrompt = compiledPrompt.replace("{$k}", v.toString())
        }

        // 2. Resolve Speaker & Target Mob/LivingEntity
        var targetLivingEntity: LivingEntity? = null
        if (speakerType.equals("NPC", ignoreCase = true) || speakerType.equals("TARGET_MOB", ignoreCase = true)) {
            if (server != null) {
                val levels = player?.serverLevel()?.let { listOf(it) } ?: server.allLevels.toList()
                for (lvl in levels) {
                    val searchBox = player?.boundingBox?.inflate(128.0) ?: net.minecraft.world.phys.AABB(-1000.0, -100.0, -1000.0, 1000.0, 300.0, 1000.0)
                    val candidates = lvl.getEntitiesOfClass(LivingEntity::class.java, searchBox) {
                        it.isAlive && (speakerId.isBlank() || it.tags.contains(speakerId) || it.type.descriptionId.contains(speakerId, true))
                    }
                    targetLivingEntity = if (player != null) {
                        candidates.minByOrNull { it.distanceToSqr(player) }
                    } else {
                        candidates.firstOrNull()
                    }
                    if (targetLivingEntity != null) break
                }
            }
        }

        val speakerName = when (speakerType.uppercase()) {
            "PARTY_FIRST", "COBBLEMON" -> {
                val idx = (node.params["partySlot"] ?: speakerId).toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0
                if (player != null) {
                    try {
                        val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
                        val poke = party.get(idx)
                        poke?.nickname?.string?.takeIf { it.isNotBlank() } ?: poke?.species?.name ?: "Pokémon"
                    } catch (_: Exception) {
                        "Pokémon"
                    }
                } else "Pokémon"
            }
            "PARTY_SLOT" -> {
                val slotIdx = (node.params["partySlot"] ?: "1").toIntOrNull()?.minus(1)?.coerceIn(0, 5) ?: 0
                if (player != null) {
                    try {
                        val party = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
                        val poke = party.get(slotIdx)
                        poke?.nickname?.string?.takeIf { it.isNotBlank() } ?: poke?.species?.name ?: "Pokémon"
                    } catch (_: Exception) {
                        "Companion"
                    }
                } else "Companion"
            }
            "BY_SPECIES" -> node.params["targetSpecies"] ?: "Pokémon"
            "NPC", "TARGET_MOB" -> {
                node.params["customSpeakerName"]?.takeIf { it.isNotBlank() }
                    ?: targetLivingEntity?.customName?.string
                    ?: targetLivingEntity?.type?.description?.string
                    ?: speakerId.ifBlank { "NPC" }
            }
            "CUSTOM_NAME" -> node.params["customSpeakerName"]?.ifBlank { "Companion" } ?: "Companion"
            "NARRATOR" -> "Narrator"
            else -> node.params["customSpeakerName"] ?: "Companion"
        }

        // Output Ports
        val outPort = node.outputs.find { it.name.equals("OUT", true) || it.name.equals("OUT_SUCCESS", true) || it.name.equals("SUCCESS", true) } ?: node.outputs.firstOrNull()
        val fallbackPort = node.outputs.find { it.name.equals("OUT_FALLBACK", true) || it.name.equals("FALLBACK", true) }

        // Helper to complete block execution safely on server thread
        fun finishExecution(verbalText: String, isSuccess: Boolean) {
            val mainThreadAction = Runnable {
                if (saveVarKey.isNotBlank()) {
                    instance.context.variables[saveVarKey] = verbalText
                }

                // In-World Mob Actions (Look at player, speech bubble)
                if (targetLivingEntity != null && server != null) {
                    if (allowActions && targetLivingEntity is Mob && player != null) {
                        try {
                            targetLivingEntity.lookControl.setLookAt(player.x, player.eyeY, player.z, 30f, 30f)
                        } catch (_: Exception) {}
                    }
                    if (displayMode == "HUD_AND_BUBBLE" || displayMode == "BUBBLE_ONLY") {
                        try {
                            DialogueSystem.spawnEntitySpeechBubble(server, targetLivingEntity, verbalText, bubbleTicks)
                        } catch (_: Exception) {}
                    }
                }

                // Display Modes
                val showHUD = displayMode == "HUD_AND_BUBBLE" || displayMode == "HUD_ONLY"
                val showBubble = displayMode == "HUD_AND_BUBBLE" || displayMode == "BUBBLE_ONLY"

                if (showBubble && player != null) {
                    player.sendSystemMessage(Component.literal("§b[$speakerName]§f: $verbalText"))
                }

                if (showHUD && player != null) {
                    // Send S2C packet to open client HUD Dialogue Box Overlay
                    DialogueSystem.sendAIDialogueBoxToPlayer?.invoke(
                        player,
                        CobblebrainPayloads.AIDialogueBoxPayload(
                            speakerName = speakerName,
                            speakerType = speakerType,
                            dialogueText = verbalText,
                            freezePlayer = freezePlayer,
                            instanceId = instance.storyId,
                            nodeId = node.id
                        )
                    )
                } else {
                    // If HUD is not active, resume execution immediately
                    val targetPort = if (isSuccess) outPort else (fallbackPort ?: outPort)
                    if (targetPort != null) {
                        StoryExecutor.continuePortConnections(instance, node, targetPort.id, 1)
                    }
                }
            }

            if (server != null) {
                server.execute(mainThreadAction)
            } else {
                mainThreadAction.run()
            }
        }

        // 3. Assemble Rich Telemetry & Dispatch Asynchronously
        val telemetryPrompt = buildString {
            if (player != null) {
                val ctx = collectWorldContext(player)
                appendLine("System Context: Player=${ctx.playerName}, Biome=${ctx.biome}, Time=${ctx.timeLabel}, Weather=${ctx.weather}, Dimension=${ctx.dimension}, Health=${ctx.health}/${ctx.maxHealth}")
            }
            if (targetLivingEntity != null) {
                val mobType = targetLivingEntity.type.description.string
                val mobHealth = "${targetLivingEntity.health.toInt()}/${targetLivingEntity.maxHealth.toInt()}"
                val mobPos = "X=${targetLivingEntity.x.toInt()}, Y=${targetLivingEntity.y.toInt()}, Z=${targetLivingEntity.z.toInt()}"
                val mobDist = "${player?.distanceTo(targetLivingEntity)?.toInt() ?: 0} blocks"
                val mobState = "target=${(targetLivingEntity as? Mob)?.target?.name?.string ?: "None"}, isBaby=${targetLivingEntity.isBaby}, isSleeping=${targetLivingEntity.isSleeping}"
                appendLine("Target Mob Telemetry: Type=$mobType, Name=$speakerName, Health=$mobHealth, Pos=$mobPos, DistanceToPlayer=$mobDist, State=$mobState")
            }
            appendLine("Speaker: $speakerName ($speakerType)")
            append("Instruction: $compiledPrompt")
        }

        AIClientHandler.sendPrompt(telemetryPrompt).thenAccept { responseStr ->
            if (responseStr.isNullOrBlank() || responseStr.startsWith("Erro ao gerar")) {
                val fallbackVerbal = if (fallbackText.isNotBlank() && fallbackText != "...") {
                    fallbackText
                } else {
                    "..."
                }
                finishExecution(fallbackVerbal, isSuccess = false)
            } else {
                finishExecution(responseStr, isSuccess = true)
            }
        }.exceptionally {
            finishExecution(fallbackText, isSuccess = false)
            null
        }
    }
}
