package vito.cobblebrain.blocks.impl

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import vito.cobblebrain.blocks.interfaces.IAction
import vito.cobblebrain.blocks.interfaces.ITrigger
import vito.cobblebrain.engine.*
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.NodeType
import vito.cobblebrain.social.DialogueSystem
import vito.cobblebrain.social.PokemonQuery
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

// ==========================================================
// ACTIONS
// ==========================================================

class SendMessageAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val rawText = node.params["messageText"]?.ifBlank { node.content.ifBlank { node.title } }
            ?: node.content.ifBlank { node.title }

        val speakerMode = node.params["speakerMode"] ?: "STANDARD"
        if (speakerMode == "COBBLEBRAIN") {
            executeCobblebrainSpeech(context, node, rawText)
            return
        }

        val player = context.player
        val messageType = node.params["messageType"] ?: "CHAT"
        val formattedComp = StoryTextFormatter.format(rawText, context)

        val players = if (player != null) listOf(player) else context.server?.playerList?.players ?: emptyList()

        for (p in players) {
            when (messageType) {
                "TITLE" -> {
                    val rawSub = node.params["subTitle"] ?: ""
                    val titleColor = node.params["titleColor"]?.trim() ?: ""
                    val formattedMain = if (titleColor.isNotBlank() && !rawText.startsWith("&") && !rawText.startsWith("#") && !rawText.startsWith("§")) {
                        "$titleColor$rawText"
                    } else {
                        rawText
                    }
                    val mainComp = StoryTextFormatter.format(formattedMain, context)
                    val subComp = if (rawSub.isNotBlank()) StoryTextFormatter.format(rawSub, context) else null

                    val fadeIn = (node.params["fadeIn"]?.toIntOrNull() ?: 10).coerceAtLeast(0)
                    val stay = (node.params["stay"]?.toIntOrNull() ?: 70).coerceAtLeast(0)
                    val fadeOut = (node.params["fadeOut"]?.toIntOrNull() ?: 20).coerceAtLeast(0)

                    p.connection.send(ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut))
                    p.connection.send(ClientboundSetTitleTextPacket(mainComp))
                    if (subComp != null) {
                        p.connection.send(ClientboundSetSubtitleTextPacket(subComp))
                    } else {
                        p.connection.send(ClientboundSetSubtitleTextPacket(Component.empty()))
                    }
                }
                "ACTION_BAR" -> p.sendSystemMessage(formattedComp, true)
                else -> p.sendSystemMessage(formattedComp, false)
            }
        }
    }

    private fun executeCobblebrainSpeech(context: StoryContext, node: NodeData, rawText: String) {
        val player = context.player
        val server = context.server ?: player?.server
        val speakerType = node.params["speakerType"] ?: "PARTY_FIRST"
        val enableChatBubble = node.params["enableChatBubble"] != "false"
        val playCry = node.params["playCry"] != "false"
        val socialLook = node.params["socialLook"] != "false"
        val jumpEffect = node.params["jumpEffect"] != "false"
        val sendToChat = node.params["sendToChat"] != "false"
        val bubbleDuration = node.params["bubbleDuration"]?.toIntOrNull() ?: 100
        val nameFormat = node.params["nameFormat"] ?: "PREFIX"

        val emotion = node.params["emotionPitch"] ?: "NEUTRAL"
        val basePitch = when (emotion) {
            "HAPPY" -> 1.25f
            "SAD" -> 0.75f
            "EXCITED" -> 1.4f
            "CUSTOM" -> node.params["customPitch"]?.toFloatOrNull() ?: 1.0f
            else -> 1.0f
        }

        var resolvedPokemon: Pokemon? = null
        var resolvedName: String? = null

        if (player != null) {
            val partyList = try {
                val party = Cobblemon.storage.getParty(player)
                (0..5).mapNotNull { party.get(it) }
            } catch (e: Exception) {
                emptyList()
            }
            val activeList = try {
                PokemonQuery.findActivePokemon(player)
            } catch (e: Exception) {
                emptyList()
            }

            when (speakerType) {
                "PARTY_FIRST" -> {
                    resolvedPokemon = activeList.firstOrNull() ?: partyList.firstOrNull()
                }
                "PARTY_SLOT" -> {
                    val slot = (node.params["partySlot"]?.toIntOrNull() ?: 1).coerceIn(1, 6) - 1
                    resolvedPokemon = try {
                        Cobblemon.storage.getParty(player).get(slot)
                    } catch (e: Exception) {
                        partyList.getOrNull(slot)
                    }
                    if (resolvedPokemon?.entity == null) {
                        resolvedPokemon = activeList.getOrNull(slot) ?: resolvedPokemon
                    }
                }
                "PARTY_RANDOM" -> {
                    resolvedPokemon = activeList.randomOrNull() ?: partyList.randomOrNull()
                }
                "NEAREST_WILD" -> {
                    try {
                        val pLevel = player.level()
                        val radius = 25.0
                        val aabb = player.boundingBox.inflate(radius)
                        val nearbyEntities = pLevel.getEntitiesOfClass(PokemonEntity::class.java, aabb)
                        resolvedPokemon = nearbyEntities.minByOrNull { it.distanceToSqr(player) }?.pokemon
                    } catch (e: Exception) {
                    }
                }
                "BY_SPECIES" -> {
                    val query = (node.params["targetSpecies"] ?: "").trim().lowercase()
                    if (query.isNotBlank()) {
                        resolvedPokemon = activeList.find {
                            it.species.name.lowercase() == query ||
                            it.species.resourceIdentifier.path.lowercase() == query ||
                            it.nickname?.string?.lowercase() == query
                        } ?: partyList.find {
                            it.species.name.lowercase() == query ||
                            it.species.resourceIdentifier.path.lowercase() == query ||
                            it.nickname?.string?.lowercase() == query
                        }
                        if (resolvedPokemon == null) {
                            try {
                                val pLevel = player.level()
                                val aabb = player.boundingBox.inflate(30.0)
                                val nearbyEntities = pLevel.getEntitiesOfClass(PokemonEntity::class.java, aabb)
                                resolvedPokemon = nearbyEntities.find {
                                    val p = it.pokemon
                                    p.species.name.lowercase() == query ||
                                    p.species.resourceIdentifier.path.lowercase() == query ||
                                    p.nickname?.string?.lowercase() == query
                                }?.pokemon
                            } catch (e: Exception) {
                            }
                        }
                    }
                    if (resolvedPokemon == null) {
                        resolvedPokemon = activeList.firstOrNull() ?: partyList.firstOrNull()
                    }
                }
                "NPC", "TARGET_MOB" -> {
                    val tag = node.params["entityStoryTag"]?.ifBlank { node.params["speakerIdentifier"] } ?: node.params["speakerIdentifier"] ?: ""
                    if (server != null) {
                        val levels = player.serverLevel().let { listOf(it) }
                        for (lvl in levels) {
                            val searchBox = player.boundingBox.inflate(128.0)
                            val candidates = lvl.getEntitiesOfClass(LivingEntity::class.java, searchBox) { it.isAlive && (tag.isBlank() || it.tags.contains(tag) || it.type.descriptionId.contains(tag, true)) }
                            val targetMob = candidates.minByOrNull { it.distanceToSqr(player) }
                            if (targetMob != null) {
                                resolvedName = node.params["customSpeakerName"]?.takeIf { it.isNotBlank() }
                                    ?: targetMob.customName?.string
                                    ?: targetMob.type.description.string
                                break
                            }
                        }
                    }
                    if (resolvedName == null) {
                        resolvedName = node.params["customSpeakerName"]?.ifBlank { tag.ifBlank { "NPC" } } ?: tag.ifBlank { "NPC" }
                    }
                }
                "CUSTOM_NAME" -> {
                    resolvedName = node.params["customSpeakerName"]?.ifBlank { "Pokémon" } ?: "Pokémon"
                    resolvedPokemon = activeList.firstOrNull() ?: partyList.firstOrNull()
                }
            }
        }

        var targetLivingEntity: LivingEntity? = null
        if (speakerType.equals("NPC", ignoreCase = true) || speakerType.equals("TARGET_MOB", ignoreCase = true)) {
            val tag = node.params["entityStoryTag"]?.ifBlank { node.params["speakerIdentifier"] } ?: node.params["speakerIdentifier"] ?: ""
            if (server != null) {
                val levels = player?.serverLevel()?.let { listOf(it) } ?: server.allLevels.toList()
                for (lvl in levels) {
                    val searchBox = player?.boundingBox?.inflate(128.0) ?: AABB(-1000.0, -100.0, -1000.0, 1000.0, 300.0, 1000.0)
                    val candidates = lvl.getEntitiesOfClass(LivingEntity::class.java, searchBox) { it.isAlive && (tag.isBlank() || it.tags.contains(tag) || it.type.descriptionId.contains(tag, true)) }
                    targetLivingEntity = if (player != null) {
                        candidates.minByOrNull { it.distanceToSqr(player) }
                    } else {
                        candidates.firstOrNull()
                    }
                    if (targetLivingEntity != null) break
                }
            }
            if (resolvedName == null) {
                resolvedName = node.params["customSpeakerName"]?.takeIf { it.isNotBlank() }
                    ?: targetLivingEntity?.customName?.string
                    ?: targetLivingEntity?.type?.description?.string
                    ?: tag.ifBlank { "NPC" }
            }
        }

        if (resolvedName == null) {
            resolvedName = resolvedPokemon?.nickname?.string?.takeIf { it.isNotBlank() }
                ?: resolvedPokemon?.species?.name
                ?: node.params["customSpeakerName"]?.takeIf { it.isNotBlank() }
                ?: "Pokémon"
        }

        val interpolatedText = StoryTextFormatter.interpolate(rawText, context)

        // Handle in-game General Mob actions
        targetLivingEntity?.let { entity ->
            if (socialLook && entity is Mob && player != null) {
                try {
                    entity.lookControl.setLookAt(player.x, player.eyeY, player.z, 30f, 30f)
                } catch (_: Exception) {}
            }

            if (jumpEffect) {
                StoryJumpManager.applyJump(entity)
            }

            if (enableChatBubble && server != null) {
                try {
                    DialogueSystem.spawnEntitySpeechBubble(server, entity, interpolatedText, bubbleDuration)
                } catch (_: Exception) {}
            }
        }

        // Handle in-game Pokémon Entity actions
        resolvedPokemon?.let { poke ->
            val entity = poke.entity
            if (playCry) {
                try {
                    DialogueSystem.expressPokemon(poke, basePitch, shouldJump = jumpEffect)
                } catch (_: Exception) {
                }
            } else if (jumpEffect && entity != null) {
                StoryJumpManager.applyJump(entity)
            }

            if (socialLook && entity != null && player != null) {
                try {
                    entity.lookControl.setLookAt(player.x, player.eyeY, player.z, 30f, 30f)
                } catch (_: Exception) {
                }
            }

            if (enableChatBubble && server != null && entity != null) {
                try {
                    DialogueSystem.spawnSpeechBubble(server, poke, interpolatedText, bubbleDuration)
                } catch (_: Exception) {
                }
            }
        }

        // Handle Text Chat
        if (sendToChat) {
            val finalMessageString = if (nameFormat == "NO_PREFIX") {
                interpolatedText
            } else {
                "§b[$resolvedName]§r $interpolatedText"
            }
            val formattedComp = StoryTextFormatter.format(finalMessageString, context)
            val players = if (player != null) listOf(player) else server?.playerList?.players ?: emptyList()
            players.forEach { it.sendSystemMessage(formattedComp, false) }
        }
    }
}

class ShowTitleAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val rawMainTitle = node.params["mainTitle"]?.ifBlank { node.content.ifBlank { node.title } }
            ?: node.content.ifBlank { node.title }
        val rawSubTitle = node.params["subTitle"] ?: ""
        val titleColor = node.params["titleColor"]?.trim() ?: ""

        val formattedMainTitle = if (titleColor.isNotBlank() && !rawMainTitle.startsWith("&") && !rawMainTitle.startsWith("#") && !rawMainTitle.startsWith("§")) {
            "$titleColor$rawMainTitle"
        } else {
            rawMainTitle
        }

        val fadeIn = (node.params["fadeIn"]?.toIntOrNull() ?: 10).coerceAtLeast(0)
        val stay = (node.params["stay"]?.toIntOrNull() ?: 70).coerceAtLeast(0)
        val fadeOut = (node.params["fadeOut"]?.toIntOrNull() ?: 20).coerceAtLeast(0)

        val mainTitleComp = StoryTextFormatter.format(formattedMainTitle, context)
        val subTitleComp = if (rawSubTitle.isNotBlank()) StoryTextFormatter.format(rawSubTitle, context) else null

        val players = if (player != null) listOf(player) else context.server?.playerList?.players ?: emptyList()

        for (p in players) {
            p.connection.send(ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut))
            p.connection.send(ClientboundSetTitleTextPacket(mainTitleComp))
            if (subTitleComp != null) {
                p.connection.send(ClientboundSetSubtitleTextPacket(subTitleComp))
            } else {
                p.connection.send(ClientboundSetSubtitleTextPacket(Component.empty()))
            }
        }
    }
}

class TeleportAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return
        val level = player?.serverLevel() ?: server.overworld()

        val destTag = node.params["destTag"]?.trim() ?: ""
        val coordInput = node.params["coordinates"]?.ifBlank {
            if (destTag.isNotBlank()) "@$destTag ~ ~ ~"
            else "${node.params["destX"] ?: "~"} ${node.params["destY"] ?: "~"} ${node.params["destZ"] ?: "~"}"
        } ?: if (destTag.isNotBlank()) "@$destTag ~ ~ ~" else "${node.params["destX"] ?: "~"} ${node.params["destY"] ?: "~"} ${node.params["destZ"] ?: "~"}"

        val safePos = node.params["safePosition"] != "false"
        val snapGround = node.params["snapToGround"] != "false"
        val maxRadius = node.params["maxSearchRadius"]?.toIntOrNull() ?: 5
        val searchPriority = SearchLayerPriority.fromString(node.params["searchPriority"])

        val destVec = CoordinateResolver.resolveSafeVec3(
            coordInput,
            level,
            player,
            server,
            safePosition = safePos,
            snapToGround = snapGround,
            maxSearchRadius = maxRadius,
            searchPriority = searchPriority
        )
        val targetMode = node.params["targetMode"] ?: "PLAYER"
        val targetStoryTag = (node.params["targetStoryTag"]?.ifBlank { node.params["storyTag"] ?: "" } ?: "").trim()

        when (targetMode.uppercase()) {
            "STORY_TAG", "TAG", "TAGGED_ENTITY" -> {
                if (targetStoryTag.isNotBlank()) {
                    val entity = StoryTagManager.resolveTargetEntity(player, server, "BY_EXISTING_TAG", targetStoryTag)
                    if (entity != null) {
                        entity.teleportTo(destVec.x, destVec.y, destVec.z)
                        if (entity is net.minecraft.world.entity.Mob) {
                            entity.navigation.stop()
                        }
                    }
                    try {
                        val cmd = "tp @e[tag=$targetStoryTag,limit=1,sort=nearest] ${destVec.x} ${destVec.y} ${destVec.z}"
                        server.commands.performPrefixedCommand(
                            player?.createCommandSourceStack()?.withPermission(4)?.withSuppressedOutput()
                                ?: server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                            cmd
                        )
                    } catch (_: Exception) {}
                }
            }
            "PLAYER_POKEMON", "PLAYER_POKEMON_SLOT", "POKEMON", "PARTY_SLOT" -> {
                val slotStr = node.params["pokemonSlot"] ?: node.params["selectorIdentifier"] ?: node.params["targetIdentifier"] ?: "1"
                val entity = StoryTagManager.resolveTargetEntity(player, server, "PLAYER_POKEMON_SLOT", slotStr)
                if (entity != null) {
                    entity.teleportTo(destVec.x, destVec.y, destVec.z)
                    if (entity is net.minecraft.world.entity.Mob) {
                        entity.navigation.stop()
                    }
                }
            }
            else -> {
                if (player != null) {
                    player.teleportTo(destVec.x, destVec.y, destVec.z)
                    player.sendSystemMessage(Component.literal("Teleported to: ${destVec.x.toInt()}, ${destVec.y.toInt()}, ${destVec.z.toInt()}"))
                }
            }
        }
    }
}

class ChangeWeatherAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val server = context.server ?: context.player?.server ?: return
        val weatherType = (node.params["weatherType"] ?: "CLEAR").uppercase()
        val durationTicks = node.params["durationTicks"]?.toIntOrNull() ?: 6000
        val cmd = when (weatherType) {
            "RAIN" -> "weather rain $durationTicks"
            "THUNDER" -> "weather thunder $durationTicks"
            else -> "weather clear $durationTicks"
        }
        try {
            server.commands.performPrefixedCommand(
                server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class SetTimeOfDayAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val server = context.server ?: context.player?.server ?: return
        val timeTicks = node.params["timeTicks"]?.toIntOrNull() ?: 1000
        try {
            server.commands.performPrefixedCommand(
                server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                "time set $timeTicks"
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
typealias SetTimeAction = SetTimeOfDayAction

class SpawnBlockAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return
        val level = player?.serverLevel() ?: server.overworld()
        val blockId = node.params["blockId"]?.ifBlank { "minecraft:stone" } ?: "minecraft:stone"
        val coordInput = node.params["coordinates"]?.ifBlank {
            "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"
        } ?: "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"

        val safePos = node.params["safePosition"] == "true"
        val snapGround = node.params["snapToGround"] == "true"
        val maxRadius = node.params["maxSearchRadius"]?.toIntOrNull() ?: 5
        val searchPriority = SearchLayerPriority.fromString(node.params["searchPriority"])

        val targetPos = if (safePos || snapGround) {
            CoordinateResolver.resolveSafeBlockPos(coordInput, level, player, server, safePosition = safePos, snapToGround = snapGround, maxSearchRadius = maxRadius, searchPriority = searchPriority)
        } else {
            CoordinateResolver.resolveBlockPos(coordInput, player, server)
        }

        try {
            val cmd = "setblock ${targetPos.x} ${targetPos.y} ${targetPos.z} $blockId"
            server.commands.performPrefixedCommand(
                player?.createCommandSourceStack()?.withPermission(4)?.withSuppressedOutput()
                    ?: server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class ModifyBlockPropertyAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return
        val coordInput = node.params["coordinates"]?.ifBlank {
            "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"
        } ?: "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"

        val targetPos = CoordinateResolver.resolveBlockPos(coordInput, player, server)
        val propKey = node.params["propertyKey"] ?: "open"
        val propVal = node.params["propertyValue"] ?: "true"
        try {
            val cmd = "setblock ${targetPos.x} ${targetPos.y} ${targetPos.z} minecraft:lever[$propKey=$propVal]"
            server.commands.performPrefixedCommand(
                player?.createCommandSourceStack()?.withPermission(4)?.withSuppressedOutput()
                    ?: server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class SpawnEntityAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return
        val level = player?.serverLevel() ?: server.overworld()
        val entityId = node.params["entityId"]?.ifBlank { "minecraft:villager" } ?: "minecraft:villager"
        val customName = node.params["entity_customName"]?.ifBlank { node.params["customName"] ?: "" } ?: ""
        val storyTag = node.params["storyTag"]?.ifBlank { node.params["entity_storyTag"] ?: "" } ?: ""
        val nameVisible = node.params["entity_nameVisible"] == "true"
        val noGravity = node.params["entity_noGravity"] == "true"
        val invulnerable = node.params["entity_invulnerable"] == "true"
        val noAi = node.params["entity_noAi"] == "true" || node.params["noAi"] == "true"
        val glowing = node.params["entity_glowing"] == "true"
        val silent = node.params["entity_silent"] == "true"
        val invisible = node.params["entity_invisible"] == "true"
        val noHitbox = node.params["entity_noHitbox"] == "true"

        val maxHealth = node.params["entity_maxHealth"]?.toDoubleOrNull()
        val speed = node.params["entity_speed"]?.toDoubleOrNull()
        val damage = node.params["entity_damage"]?.toDoubleOrNull()
        val armor = node.params["entity_armor"]?.toDoubleOrNull()

        val helmet = node.params["entity_helmet"]?.trim()?.takeIf { it.isNotBlank() }
        val chest = node.params["entity_chest"]?.trim()?.takeIf { it.isNotBlank() }
        val legs = node.params["entity_legs"]?.trim()?.takeIf { it.isNotBlank() }
        val feet = node.params["entity_feet"]?.trim()?.takeIf { it.isNotBlank() }
        val mainhand = node.params["entity_mainhand"]?.trim()?.takeIf { it.isNotBlank() }
        val offhand = node.params["entity_offhand"]?.trim()?.takeIf { it.isNotBlank() }

        val coordInput = node.params["coordinates"]?.ifBlank {
            "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"
        } ?: "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"

        val safePos = node.params["safePosition"] != "false"
        val snapGround = node.params["snapToGround"] != "false"
        val maxRadius = node.params["maxSearchRadius"]?.toIntOrNull() ?: 5
        val searchPriority = SearchLayerPriority.fromString(node.params["searchPriority"])

        val targetVec = CoordinateResolver.resolveSafeVec3(
            coordInput,
            level,
            player,
            server,
            safePosition = safePos,
            snapToGround = snapGround,
            maxSearchRadius = maxRadius,
            searchPriority = searchPriority
        )

        try {
            val nbtParts = mutableListOf<String>()
            if (customName.isNotBlank()) {
                nbtParts.add("CustomName:'{\"text\":\"$customName\"}'")
                if (nameVisible) nbtParts.add("CustomNameVisible:1b")
            }
            if (storyTag.isNotBlank()) {
                nbtParts.add("Tags:[\"$storyTag\"]")
            }
            if (noGravity) nbtParts.add("NoGravity:1b")
            if (invulnerable) nbtParts.add("Invulnerable:1b")
            if (noAi) nbtParts.add("NoAI:1b")
            if (glowing) nbtParts.add("Glowing:1b")
            if (silent) nbtParts.add("Silent:1b")
            if (invisible) {
                nbtParts.add("Invisible:1b")
                nbtParts.add("ActiveEffects:[{Id:14,Amplifier:0b,Duration:999999,ShowParticles:0b}]")
            }
            if (noHitbox) {
                nbtParts.add("Marker:1b")
                nbtParts.add("Invulnerable:1b")
            }

            val attrParts = mutableListOf<String>()
            if (maxHealth != null) attrParts.add("{Name:\"generic.max_health\",Base:${maxHealth}d}")
            if (speed != null) attrParts.add("{Name:\"generic.movement_speed\",Base:${speed}d}")
            if (damage != null) attrParts.add("{Name:\"generic.attack_damage\",Base:${damage}d}")
            if (armor != null) attrParts.add("{Name:\"generic.armor\",Base:${armor}d}")

            if (attrParts.isNotEmpty()) {
                nbtParts.add("Attributes:[${attrParts.joinToString(",")}]")
            }
            if (maxHealth != null) {
                nbtParts.add("Health:${maxHealth}f")
            }

            if (feet != null || legs != null || chest != null || helmet != null) {
                val fStr = feet?.let { "{id:\"$it\",Count:1b}" } ?: "{}"
                val lStr = legs?.let { "{id:\"$it\",Count:1b}" } ?: "{}"
                val cStr = chest?.let { "{id:\"$it\",Count:1b}" } ?: "{}"
                val hStr = helmet?.let { "{id:\"$it\",Count:1b}" } ?: "{}"
                nbtParts.add("ArmorItems:[$fStr,$lStr,$cStr,$hStr]")
            }

            if (mainhand != null || offhand != null) {
                val mStr = mainhand?.let { "{id:\"$it\",Count:1b}" } ?: "{}"
                val oStr = offhand?.let { "{id:\"$it\",Count:1b}" } ?: "{}"
                nbtParts.add("HandItems:[$mStr,$oStr]")
            }

            val tag = if (nbtParts.isNotEmpty()) " {${nbtParts.joinToString(",")}}" else ""
            val cmd = "summon $entityId ${targetVec.x} ${targetVec.y} ${targetVec.z}$tag"
            server.commands.performPrefixedCommand(
                player?.createCommandSourceStack()?.withPermission(4)?.withSuppressedOutput()
                    ?: server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class KillEntityAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return
        val targetMode = node.params["targetMode"] ?: "AREA_NEAREST"
        val targetStoryTag = node.params["targetStoryTag"]?.trim() ?: ""
        val selector = if (targetMode == "STORY_TAG" && targetStoryTag.isNotBlank()) {
            "@e[tag=$targetStoryTag]"
        } else {
            node.params["entitySelector"]?.ifBlank { "@e[type=zombie,distance=..10]" } ?: "@e[type=zombie,distance=..10]"
        }
        try {
            val cmd = "kill $selector"
            server.commands.performPrefixedCommand(
                player?.createCommandSourceStack()?.withPermission(4)?.withSuppressedOutput()
                    ?: server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class ModifyEntityPropertiesAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return
        val targetMode = node.params["targetMode"] ?: "AREA_NEAREST"
        val targetStoryTag = node.params["targetStoryTag"]?.trim() ?: ""
        val selector = if (targetMode == "STORY_TAG" && targetStoryTag.isNotBlank()) {
            "@e[tag=$targetStoryTag,limit=1,sort=nearest]"
        } else {
            node.params["entitySelector"]?.ifBlank { "@e[type=!player,distance=..5,limit=1]" } ?: "@e[type=!player,distance=..5,limit=1]"
        }

        val customName = node.params["entity_customName"]?.ifBlank { node.params["customName"] ?: "" } ?: ""
        val storyTag = node.params["storyTag"]?.ifBlank { node.params["entity_storyTag"] ?: "" } ?: ""
        val nameVisible = node.params["entity_nameVisible"] == "true"
        val noGravity = node.params["entity_noGravity"] == "true"
        val invulnerable = node.params["entity_invulnerable"] == "true"
        val noAi = node.params["entity_noAi"] == "true" || node.params["noAi"] == "true"
        val glowing = node.params["entity_glowing"] == "true"
        val silent = node.params["entity_silent"] == "true"
        val invisible = node.params["entity_invisible"] == "true"
        val noHitbox = node.params["entity_noHitbox"] == "true"

        val maxHealth = node.params["entity_maxHealth"]?.toDoubleOrNull()
        val speed = node.params["entity_speed"]?.toDoubleOrNull()
        val damage = node.params["entity_damage"]?.toDoubleOrNull()
        val armor = node.params["entity_armor"]?.toDoubleOrNull()

        val helmet = node.params["entity_helmet"]?.trim()?.takeIf { it.isNotBlank() }
        val chest = node.params["entity_chest"]?.trim()?.takeIf { it.isNotBlank() }
        val legs = node.params["entity_legs"]?.trim()?.takeIf { it.isNotBlank() }
        val feet = node.params["entity_feet"]?.trim()?.takeIf { it.isNotBlank() }
        val mainhand = node.params["entity_mainhand"]?.trim()?.takeIf { it.isNotBlank() }
        val offhand = node.params["entity_offhand"]?.trim()?.takeIf { it.isNotBlank() }

        try {
            val nbtParts = mutableListOf<String>()
            if (customName.isNotBlank()) {
                nbtParts.add("CustomName:'{\"text\":\"$customName\"}'")
                nbtParts.add("CustomNameVisible:${if (nameVisible) "1b" else "0b"}")
            }
            if (node.params.containsKey("entity_noGravity")) nbtParts.add("NoGravity:${if (noGravity) "1b" else "0b"}")
            if (node.params.containsKey("entity_invulnerable")) nbtParts.add("Invulnerable:${if (invulnerable) "1b" else "0b"}")
            if (node.params.containsKey("entity_noAi") || node.params.containsKey("noAi")) nbtParts.add("NoAI:${if (noAi) "1b" else "0b"}")
            if (node.params.containsKey("entity_glowing")) nbtParts.add("Glowing:${if (glowing) "1b" else "0b"}")
            if (node.params.containsKey("entity_silent")) nbtParts.add("Silent:${if (silent) "1b" else "0b"}")
            if (node.params.containsKey("entity_invisible")) {
                nbtParts.add("Invisible:${if (invisible) "1b" else "0b"}")
                if (invisible) {
                    nbtParts.add("ActiveEffects:[{Id:14,Amplifier:0b,Duration:999999,ShowParticles:0b}]")
                }
            }
            if (node.params.containsKey("entity_noHitbox")) {
                nbtParts.add("Marker:${if (noHitbox) "1b" else "0b"}")
            }

            val attrParts = mutableListOf<String>()
            if (maxHealth != null) attrParts.add("{Name:\"generic.max_health\",Base:${maxHealth}d}")
            if (speed != null) attrParts.add("{Name:\"generic.movement_speed\",Base:${speed}d}")
            if (damage != null) attrParts.add("{Name:\"generic.attack_damage\",Base:${damage}d}")
            if (armor != null) attrParts.add("{Name:\"generic.armor\",Base:${armor}d}")

            if (attrParts.isNotEmpty()) {
                nbtParts.add("Attributes:[${attrParts.joinToString(",")}]")
            }
            if (maxHealth != null) {
                nbtParts.add("Health:${maxHealth}f")
            }

            if (feet != null || legs != null || chest != null || helmet != null) {
                val fStr = feet?.let { "{id:\"$it\",Count:1b}" } ?: "{}"
                val lStr = legs?.let { "{id:\"$it\",Count:1b}" } ?: "{}"
                val cStr = chest?.let { "{id:\"$it\",Count:1b}" } ?: "{}"
                val hStr = helmet?.let { "{id:\"$it\",Count:1b}" } ?: "{}"
                nbtParts.add("ArmorItems:[$fStr,$lStr,$cStr,$hStr]")
            }

            if (mainhand != null || offhand != null) {
                val mStr = mainhand?.let { "{id:\"$it\",Count:1b}" } ?: "{}"
                val oStr = offhand?.let { "{id:\"$it\",Count:1b}" } ?: "{}"
                nbtParts.add("HandItems:[$mStr,$oStr]")
            }

            val source = player?.createCommandSourceStack()?.withPermission(4)?.withSuppressedOutput()
                ?: server.createCommandSourceStack().withPermission(4).withSuppressedOutput()

            if (nbtParts.isNotEmpty()) {
                val nbt = "{${nbtParts.joinToString(",")}}"
                val cmd = "data merge entity $selector $nbt"
                server.commands.performPrefixedCommand(source, cmd)
            }

            if (storyTag.isNotBlank()) {
                val tagCmd = "tag $selector add $storyTag"
                server.commands.performPrefixedCommand(source, tagCmd)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class SpawnCobblemonAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val world = player.serverLevel()
        val speciesName = node.params["species"]?.ifBlank { "Pikachu" } ?: "Pikachu"
        val level = node.params["level"]?.toIntOrNull() ?: 5
        val isShiny = node.params["shiny"] == "true"
        val gender = node.params["gender"]
        val form = node.params["form"]
        val storyTag = node.params["storyTag"]?.trim() ?: ""

        val coordInput = node.params["coordinates"]?.ifBlank {
            "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"
        } ?: "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"

        val safePos = node.params["safePosition"] != "false"
        val snapGround = node.params["snapToGround"] != "false"
        val maxRadius = node.params["maxSearchRadius"]?.toIntOrNull() ?: 5
        val searchPriority = SearchLayerPriority.fromString(node.params["searchPriority"])

        val targetVec = CoordinateResolver.resolveSafeVec3(
            coordInput,
            world,
            player,
            context.server ?: player.server,
            safePosition = safePos,
            snapToGround = snapGround,
            maxSearchRadius = maxRadius,
            searchPriority = searchPriority
        )
        val px = targetVec.x
        val py = targetVec.y
        val pz = targetVec.z

        try {
            val properties = PokemonProperties()
            val species = PokemonSpecies.getByName(speciesName.lowercase())
            if (species != null) {
                properties.species = species.resourceIdentifier.toString()
            } else {
                properties.species = speciesName.lowercase()
            }
            properties.level = level
            if (isShiny) properties.shiny = true
            if (!gender.isNullOrBlank() && gender != "RANDOM") {
                try {
                    properties.gender = Gender.valueOf(gender.uppercase())
                } catch (_: Exception) {}
            }
            if (!form.isNullOrBlank()) {
                properties.form = form
            }

            val pokemonEntity = properties.createEntity(world)
            pokemonEntity.moveTo(px, py, pz, player.yRot, 0f)
            pokemonEntity.finalizeSpawn(
                world,
                world.getCurrentDifficultyAt(BlockPos(px.toInt(), py.toInt(), pz.toInt())),
                MobSpawnType.EVENT,
                null
            )
            pokemonEntity.setPersistenceRequired()
            if (storyTag.isNotBlank()) {
                pokemonEntity.addTag(storyTag)
            }
            world.addFreshEntity(pokemonEntity)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class GivePokemonAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val species = node.params["species"]?.ifBlank { "Eevee" } ?: "Eevee"
        val level = node.params["level"]?.toIntOrNull() ?: 5
        val isShiny = node.params["shiny"] == "true"

        try {
            val sb = StringBuilder("givepokemon ${player.scoreboardName} $species level=$level")
            if (isShiny) sb.append(" shiny=yes")
            player.server?.commands?.performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                sb.toString()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class ModifyPokemonPropertiesAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val healHp = node.params["healHp"] != "false"
        try {
            if (healHp) {
                player.server?.commands?.performPrefixedCommand(
                    player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                    "pokeheal ${player.scoreboardName}"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class GiveItemAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val itemId = node.params["itemId"]?.ifBlank { "cobblemon:poke_ball" } ?: "cobblemon:poke_ball"
        val amount = node.params["amount"]?.toIntOrNull() ?: 1
        try {
            val cmd = "give ${player.scoreboardName} $itemId $amount"
            player.server?.commands?.performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class RemoveItemAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val itemId = node.params["itemId"]?.ifBlank { "cobblemon:poke_ball" } ?: "cobblemon:poke_ball"
        val amount = node.params["amount"]?.toIntOrNull() ?: 1
        try {
            val cmd = "clear ${player.scoreboardName} $itemId $amount"
            player.server?.commands?.performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class DamagePlayerAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val dmg = node.params["damageAmount"]?.toFloatOrNull() ?: 4.0f
        try {
            val cmd = "damage ${player.scoreboardName} $dmg"
            player.server?.commands?.performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class KillPlayerAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        try {
            player.server?.commands?.performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                "kill ${player.scoreboardName}"
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class ApplyEffectAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return
        val effectId = node.params["effectId"]?.ifBlank { "minecraft:speed" } ?: "minecraft:speed"
        val duration = node.params["durationSec"]?.toIntOrNull() ?: 10
        val amplifier = ((node.params["amplifier"]?.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
        val showParticles = node.params["showParticles"] != "false"
        val hideParticles = !showParticles

        val targetMode = node.params["targetMode"] ?: "AREA_NEAREST"
        val targetStoryTag = node.params["targetStoryTag"]?.trim() ?: ""
        val targetSelector = when {
            node.params["actionSubtype"] == "ADD_PLAYER_EFFECT" || node.params["actionSubtype"] == "EFFECT" -> player?.scoreboardName ?: "@p"
            targetMode == "STORY_TAG" && targetStoryTag.isNotBlank() -> "@e[tag=$targetStoryTag]"
            else -> node.params["entitySelector"]?.ifBlank { "@e[type=!player,distance=..5,limit=1]" } ?: (player?.scoreboardName ?: "@p")
        }

        try {
            val cmd = "effect give $targetSelector $effectId $duration $amplifier $hideParticles"
            server.commands.performPrefixedCommand(
                player?.createCommandSourceStack()?.withPermission(4)?.withSuppressedOutput()
                    ?: server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class AreaEffectAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return
        val effectId = node.params["effectId"]?.ifBlank { "minecraft:slowness" } ?: "minecraft:slowness"
        val radius = node.params["radius"]?.toIntOrNull() ?: 8
        val duration = node.params["durationSec"]?.toIntOrNull() ?: 10
        val amplifier = ((node.params["amplifier"]?.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
        val showParticles = node.params["showParticles"] != "false"
        val hideParticles = !showParticles
        try {
            val cmd = "effect give @e[distance=..$radius] $effectId $duration $amplifier $hideParticles"
            server.commands.performPrefixedCommand(
                player?.createCommandSourceStack()?.withPermission(4)?.withSuppressedOutput()
                    ?: server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class SpawnItemAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return
        val itemId = node.params["itemId"]?.ifBlank { "minecraft:diamond" } ?: "minecraft:diamond"
        val count = node.params["count"]?.toIntOrNull() ?: 1
        val coordInput = node.params["coordinates"]?.ifBlank {
            "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"
        } ?: "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"
        val targetVec = CoordinateResolver.resolveVec3(coordInput, player, server)
        try {
            val cmd = "summon item ${targetVec.x} ${targetVec.y} ${targetVec.z} {Item:{id:\"$itemId\",Count:${count}b}}"
            server.commands.performPrefixedCommand(
                player?.createCommandSourceStack()?.withPermission(4)?.withSuppressedOutput()
                    ?: server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class SpawnParticlesAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return
        val particleId = node.params["particleId"]?.ifBlank { "minecraft:totem_of_undying" } ?: "minecraft:totem_of_undying"
        val count = node.params["count"]?.toIntOrNull() ?: 20
        val coordInput = node.params["coordinates"]?.ifBlank {
            "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"
        } ?: "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"
        val targetVec = CoordinateResolver.resolveVec3(coordInput, player, server)
        try {
            val cmd = "particle $particleId ${targetVec.x} ${targetVec.y} ${targetVec.z} 0.5 0.5 0.5 0.1 $count"
            server.commands.performPrefixedCommand(
                player?.createCommandSourceStack()?.withPermission(4)?.withSuppressedOutput()
                    ?: server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class PlaySoundAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val soundIdStr = node.params["soundId"]?.ifBlank { "minecraft:entity.player.levelup" } ?: "minecraft:entity.player.levelup"
        val volume = node.params["volume"]?.toFloatOrNull() ?: 1.0f
        val pitch = node.params["pitch"]?.toFloatOrNull() ?: 1.0f

        val soundRes = ResourceLocation.tryParse(soundIdStr)
        val soundEvent = if (soundRes != null) BuiltInRegistries.SOUND_EVENT.get(soundRes) else SoundEvents.UI_BUTTON_CLICK.value()
        val finalSound = soundEvent ?: SoundEvents.UI_BUTTON_CLICK.value()

        val level = player.serverLevel()
        level.playSound(
            null,
            player.x, player.y, player.z,
            finalSound,
            SoundSource.PLAYERS,
            volume, pitch
        )
    }
}

class PlayMusicAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val musicId = node.params["musicId"]?.ifBlank { "minecraft:music.game" } ?: "minecraft:music.game"
        try {
            val cmd = "playsound $musicId music ${player.scoreboardName} ~ ~ ~ 1.0 1.0"
            player.server?.commands?.performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class LookAtAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return
        val sLevel = player?.serverLevel() ?: server.overworld()

        val operationMode = node.params["operationMode"] ?: "APPLY_LOOK"

        // 1. Resolve Subject Type & Identifier
        val rawSubjectType = node.params["subjectType"] ?: node.params["targetType"] ?: "PLAYER_POKEMON"
        val subjectType = if (rawSubjectType.equals("NPC_TAG", true) || rawSubjectType.equals("NPC", true) || rawSubjectType.equals("ENTITY", true)) {
            LookSubjectType.NPC_TAG
        } else {
            LookSubjectType.PLAYER_POKEMON
        }
        val subjectId = node.params["subjectIdentifier"] ?: node.params["targetIdentifier"] ?: "0"

        val subjectEntity = StoryLookAtManager.resolveSubjectEntity(sLevel, player, subjectType, subjectId)
        if (subjectEntity == null) return

        // 2. Handle RESET_LOOK
        if (operationMode == "RESET_LOOK") {
            StoryLookAtManager.resetLookOverride(subjectEntity)
            return
        }

        // 3. Handle APPLY_LOOK
        val rawLookMode = node.params["lookMode"] ?: "TOWARDS_REFERENCE"
        val lookMode = when (rawLookMode.uppercase()) {
            "AWAY_FROM_REFERENCE", "AWAY_FROM_PLAYER", "AWAY" -> LookTargetMode.AWAY_FROM_REFERENCE
            "SKY" -> LookTargetMode.SKY
            "GROUND" -> LookTargetMode.GROUND
            "OPPOSITE_SELF", "OPPOSITE_FACING", "OPPOSITE" -> LookTargetMode.OPPOSITE_SELF
            else -> LookTargetMode.TOWARDS_REFERENCE
        }

        val rawRefType = node.params["referenceType"] ?: when (rawLookMode.uppercase()) {
            "SPECIFIC_DIRECTION" -> "COORDINATES"
            "AWAY_FROM_PLAYER", "PLAYER" -> "PLAYER"
            else -> "PLAYER"
        }
        val refType = when (rawRefType.uppercase()) {
            "MOB_TAG", "MOB", "ENTITY" -> LookReferenceType.MOB_TAG
            "COORDINATES", "COORDS", "POSITION" -> LookReferenceType.COORDINATES
            else -> LookReferenceType.PLAYER
        }

        val refId = node.params["referenceIdentifier"] ?: node.params["coordinates"] ?: ""

        val referencePlayerUuid = if (refType == LookReferenceType.PLAYER) player?.uuid else null
        val referenceMobTag = if (refType == LookReferenceType.MOB_TAG) refId.trim() else null
        val referenceCoords = if (refType == LookReferenceType.COORDINATES) {
            CoordinateResolver.resolveVec3(refId, player ?: subjectEntity, server, Vec3(subjectEntity.x, subjectEntity.eyeY, subjectEntity.z))
        } else null

        val durationMode = node.params["durationMode"] ?: "TEMPORARY"
        val durationTicks = if (durationMode == "INDEFINITE") {
            -1
        } else {
            node.params["durationTicks"]?.toIntOrNull()?.coerceAtLeast(1) ?: 60
        }

        StoryLookAtManager.applyLookOverride(
            subject = subjectEntity,
            referenceType = refType,
            referencePlayerUuid = referencePlayerUuid,
            referenceMobTag = referenceMobTag,
            referenceCoordinates = referenceCoords,
            lookMode = lookMode,
            durationTicks = durationTicks
        )
    }
}

class AnimationAction : IAction {
    companion object {
        val activeAnimationOverrides = ConcurrentHashMap<UUID, Long>()
    }

    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return

        val animSystem = node.params["animationSystem"] ?: "COBBLEMON"
        val targetId = node.params["targetIdentifier"] ?: "0"
        val animationId = (node.params["animationId"] ?: if (animSystem == "COBBLEMON") "battle_idle" else "CROUCHING").trim()
        val durationMode = node.params["durationMode"] ?: "TEMPORARY"
        val durationTicks = node.params["durationTicks"]?.toIntOrNull()?.coerceAtLeast(1) ?: 60
        val overridePriority = node.params["overridePriority"] != "false"

        // 1. Resolve Target LivingEntity
        var targetEntity: LivingEntity? = null
        var targetPokemon: Pokemon? = null

        if (animSystem == "COBBLEMON" && player != null) {
            val slotIdx = PokemonQuery.parsePartySlotIndex(targetId)
            try {
                val party = Cobblemon.storage.getParty(player)
                targetPokemon = party.get(slotIdx)
            } catch (_: Exception) {}
            if (targetPokemon == null) {
                try {
                    val activeList = PokemonQuery.findActivePokemon(player)
                    targetPokemon = activeList.getOrNull(slotIdx) ?: activeList.firstOrNull()
                } catch (_: Exception) {}
            }
            targetEntity = targetPokemon?.entity
        } else {
            val levels = player?.serverLevel()?.let { listOf(it) } ?: server.allLevels.toList()
            for (lvl in levels) {
                val searchBox = player?.boundingBox?.inflate(128.0) ?: AABB(-1000.0, -100.0, -1000.0, 1000.0, 300.0, 1000.0)
                val candidates = lvl.getEntitiesOfClass(LivingEntity::class.java, searchBox) { it.isAlive && (targetId.isBlank() || it.tags.contains(targetId) || it.type.descriptionId.contains(targetId, true)) }
                targetEntity = if (player != null) {
                    candidates.minByOrNull { it.distanceToSqr(player) }
                } else {
                    candidates.firstOrNull()
                }
                if (targetEntity != null) break
            }
        }

        if (targetEntity == null) return

        // 2. Play Animation / Pose / Action
        applyAnimation(targetEntity, targetPokemon, animationId)

        // 3. Priority Override Tracking
        if (overridePriority) {
            val expireTick = if (durationMode == "TEMPORARY") server.tickCount.toLong() + durationTicks else Long.MAX_VALUE
            activeAnimationOverrides[targetEntity.uuid] = expireTick
        }

        // 4. Temporary Duration Schedule
        if (durationMode == "TEMPORARY") {
            TickManager.schedule(durationTicks) {
                server.execute {
                    if (activeAnimationOverrides[targetEntity.uuid] != Long.MAX_VALUE) {
                        activeAnimationOverrides.remove(targetEntity.uuid)
                        resetAnimation(targetEntity)
                    }
                }
            }
        }
    }

    private fun applyAnimation(entity: LivingEntity, pokemon: Pokemon?, animId: String) {
        val upper = animId.uppercase()
        val sLevel = entity.level() as? ServerLevel

        val matchingPose = try {
            Pose.valueOf(upper)
        } catch (_: Exception) {
            when (upper) {
                "SNEAK", "CROUCH" -> Pose.CROUCHING
                "SLEEP" -> Pose.SLEEPING
                "SWIM" -> Pose.SWIMMING
                "SPIN" -> Pose.SPIN_ATTACK
                "SIT" -> Pose.SITTING
                else -> null
            }
        }

        if (matchingPose != null) {
            entity.pose = matchingPose
        }

        when (upper) {
            "ATTACK", "ATTACK_SWING", "SWING", "PHYSICAL_ATTACK" -> {
                entity.swing(InteractionHand.MAIN_HAND, true)
                sLevel?.broadcastEntityEvent(entity, 4)
            }
            "HURT" -> {
                sLevel?.broadcastEntityEvent(entity, 2)
            }
            "CRITICAL_HIT", "CRIT" -> {
                entity.swing(InteractionHand.MAIN_HAND, true)
                sLevel?.sendParticles(ParticleTypes.CRIT, entity.x, entity.eyeY, entity.z, 15, 0.3, 0.3, 0.3, 0.2)
            }
            "MAGIC_SPELL", "SPECIAL_ATTACK" -> {
                sLevel?.sendParticles(ParticleTypes.ENCHANT, entity.x, entity.eyeY, entity.z, 20, 0.5, 0.5, 0.5, 0.5)
                sLevel?.broadcastEntityEvent(entity, 60)
            }
            "VILLAGER_HAPPY", "HAPPY" -> {
                sLevel?.sendParticles(ParticleTypes.HAPPY_VILLAGER, entity.x, entity.eyeY + 0.5, entity.z, 10, 0.4, 0.4, 0.4, 0.1)
                sLevel?.broadcastEntityEvent(entity, 14)
                StoryJumpManager.applyJump(entity)
            }
            "VILLAGER_ANGRY" -> {
                sLevel?.sendParticles(ParticleTypes.ANGRY_VILLAGER, entity.x, entity.eyeY + 0.5, entity.z, 8, 0.3, 0.3, 0.3, 0.0)
                sLevel?.broadcastEntityEvent(entity, 13)
            }
            "CELEBRATE", "JUMP" -> {
                StoryJumpManager.applyJump(entity)
            }
            "CRY" -> {
                if (pokemon != null) {
                    try {
                        DialogueSystem.expressPokemon(pokemon, 1.0f)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun resetAnimation(entity: LivingEntity) {
        if (entity.isAlive) {
            entity.pose = Pose.STANDING
        }
    }
}

class SetEntityTextureAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return

        val targetType = node.params["targetType"] ?: "PLAYER_POKEMON"
        val targetId = node.params["pokemonSlot"] ?: node.params["targetIdentifier"] ?: "1"
        val textureName = node.params["textureName"]?.trim() ?: "custom_texture.png"
        val resetToDefault = node.params["resetToDefault"] == "true" || node.params["textureMode"] == "CLEAR_TEXTURE"
        val storyId = context.storyId.ifBlank { context.project?.id ?: "default_story" }

        // 1. Resolve Target LivingEntity
        var targetEntity: LivingEntity? = null
        if (targetType == "PLAYER_POKEMON" && player != null) {
            val slotIdx = PokemonQuery.parsePartySlotIndex(targetId)
            try {
                val party = Cobblemon.storage.getParty(player)
                val poke = party.get(slotIdx)
                targetEntity = poke?.entity
            } catch (_: Exception) {}
            if (targetEntity == null) {
                try {
                    val activeList = PokemonQuery.findActivePokemon(player)
                    targetEntity = activeList.getOrNull(slotIdx)?.entity ?: activeList.firstOrNull()?.entity
                } catch (_: Exception) {}
            }
        } else {
            val levels = player?.serverLevel()?.let { listOf(it) } ?: server.allLevels.toList()
            for (lvl in levels) {
                val searchBox = player?.boundingBox?.inflate(128.0) ?: AABB(-1000.0, -100.0, -1000.0, 1000.0, 300.0, 1000.0)
                val candidates = lvl.getEntitiesOfClass(LivingEntity::class.java, searchBox) { it.isAlive && (targetId.isBlank() || it.tags.contains(targetId) || it.type.descriptionId.contains(targetId, true)) }
                targetEntity = if (player != null) {
                    candidates.minByOrNull { it.distanceToSqr(player) }
                } else {
                    candidates.firstOrNull()
                }
                if (targetEntity != null) break
            }
        }

        if (targetEntity == null) return

        // 2. Broadcast Dynamic Texture S2C Packet / Clear Override
        if (resetToDefault) {
            DialogueSystem.broadcastClearEntityTexture(server, targetEntity)
        } else {
            DialogueSystem.broadcastEntityTexture(server, targetEntity, storyId, textureName)
        }
    }
}

// ==========================================================
// GATILHOS (TRIGGERS) & SCENE BLOCKS
// ==========================================================

class BeginSceneBlock : ITrigger {
    override fun evaluate(context: StoryContext, node: NodeData): Boolean {
        return true
    }
}

class EndSceneBlock : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        // Scene completion hook
    }
}

class StoryStartedTrigger : ITrigger {
    override fun evaluate(context: StoryContext, node: NodeData): Boolean {
        if (node.nodeType != NodeType.TRIGGER) return false
        val isIfNot = node.params["triggerCondition"] == "IF_NOT"
        return if (isIfNot) false else true
    }
}

class PlayerLocationTrigger : ITrigger {
    override fun evaluate(context: StoryContext, node: NodeData): Boolean {
        val player = context.player ?: return false
        val server = context.server ?: player.server
        val coordInput = node.params["coordinates"]?.ifBlank {
            "${node.params["targetX"] ?: "0"} ${node.params["targetY"] ?: "64"} ${node.params["targetZ"] ?: "0"}"
        } ?: "${node.params["targetX"] ?: "0"} ${node.params["targetY"] ?: "64"} ${node.params["targetZ"] ?: "0"}"

        val targetVec = CoordinateResolver.resolveVec3(coordInput, player, server)
        val radius = node.params["radius"]?.toDoubleOrNull() ?: 5.0

        val px = player.x
        val py = player.y
        val pz = player.z

        val dist = sqrt((px - targetVec.x) * (px - targetVec.x) + (py - targetVec.y) * (py - targetVec.y) + (pz - targetVec.z) * (pz - targetVec.z))
        val rawResult = dist <= radius

        val isIfNot = node.params["triggerCondition"] == "IF_NOT"
        return if (isIfNot) !rawResult else rawResult
    }
}

class PlayerLevelTrigger : ITrigger {
    override fun evaluate(context: StoryContext, node: NodeData): Boolean {
        val player = context.player ?: return false
        val minLevel = node.params["minLevel"]?.toIntOrNull() ?: 10
        val op = node.params["comparisonOp"] ?: ">="
        val pLevel = player.experienceLevel

        val rawResult = when (op) {
            ">" -> pLevel > minLevel
            "<" -> pLevel < minLevel
            "<=" -> pLevel <= minLevel
            "==" -> pLevel == minLevel
            else -> pLevel >= minLevel
        }
        val isIfNot = node.params["triggerCondition"] == "IF_NOT"
        return if (isIfNot) !rawResult else rawResult
    }
}

class WeatherCheckTrigger : ITrigger {
    override fun evaluate(context: StoryContext, node: NodeData): Boolean {
        val player = context.player ?: return false
        val targetWeather = (node.params["weatherType"] ?: "RAIN").uppercase()
        val level = player.serverLevel()
        val isRaining = level.isRaining
        val isThundering = level.isThundering

        val currentWeather = when {
            isThundering -> "THUNDER"
            isRaining -> "RAIN"
            else -> "CLEAR"
        }
        val rawResult = (currentWeather == targetWeather)
        val isIfNot = node.params["triggerCondition"] == "IF_NOT"
        return if (isIfNot) !rawResult else rawResult
    }
}

class DayNightCheckTrigger : ITrigger {
    override fun evaluate(context: StoryContext, node: NodeData): Boolean {
        val player = context.player ?: return false
        val targetPeriod = node.params["timePeriod"] ?: "DAY"
        val timeOfDay = player.serverLevel().dayTime % 24000
        val isDay = timeOfDay in 0..12999
        val currentPeriod = if (isDay) "DAY" else "NIGHT"

        val rawResult = (currentPeriod == targetPeriod)
        val isIfNot = node.params["triggerCondition"] == "IF_NOT"
        return if (isIfNot) !rawResult else rawResult
    }
}

class TagAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return

        val category = node.params["targetCategory"] ?: "ENTITY"
        val selector = node.params["targetSelector"] ?: when (category.uppercase()) {
            "WORLD_BLOCK", "BLOCK" -> "LOOKING_AT_BLOCK"
            "PLAYER" -> "INTERACTING_PLAYER"
            else -> "CLOSEST_MOB"
        }
        val identifier = node.params["selectorIdentifier"]?.ifBlank {
            node.params["targetIdentifier"]?.ifBlank {
                node.params["pokemonSlot"] ?: "1"
            } ?: "1"
        } ?: "1"
        val operation = node.params["operation"] ?: "ADD_TAG"
        val tagName = (node.params["tagName"]?.ifBlank { node.params["storyTag"] ?: "" } ?: "").trim()

        when (category.uppercase()) {
            "ENTITY", "MOB" -> {
                val entity = StoryTagManager.resolveTargetEntity(player, server, selector, identifier)
                if (entity != null) {
                    when (operation.uppercase()) {
                        "ADD_TAG", "ADD" -> if (tagName.isNotBlank()) entity.addTag(tagName)
                        "REMOVE_TAG", "REMOVE" -> if (tagName.isNotBlank()) entity.removeTag(tagName)
                        "CLEAR_TAGS", "CLEAR" -> {
                            val toRemove = entity.tags.toList()
                            toRemove.forEach { entity.removeTag(it) }
                        }
                    }
                }
            }
            "WORLD_BLOCK", "BLOCK" -> {
                when (operation.uppercase()) {
                    "ADD_TAG", "ADD" -> {
                        if (tagName.isNotBlank()) {
                            val blockTarget = StoryTagManager.resolveTargetBlock(player, server, selector, identifier)
                            if (blockTarget != null) {
                                StoryTagManager.setBlockTag(tagName, blockTarget.first, blockTarget.second)
                            }
                        }
                    }
                    "REMOVE_TAG", "REMOVE" -> {
                        if (tagName.isNotBlank()) {
                            StoryTagManager.removeBlockTag(tagName)
                        }
                    }
                    "CLEAR_TAGS", "CLEAR" -> {
                        StoryTagManager.clearBlockTags()
                    }
                }
            }
            "PLAYER" -> {
                if (player != null) {
                    when (operation.uppercase()) {
                        "ADD_TAG", "ADD" -> if (tagName.isNotBlank()) player.addTag(tagName)
                        "REMOVE_TAG", "REMOVE" -> if (tagName.isNotBlank()) player.removeTag(tagName)
                        "CLEAR_TAGS", "CLEAR" -> {
                            val toRemove = player.tags.filter { !it.startsWith("cobblebrain:guaranteed_") }
                            toRemove.forEach { player.removeTag(it) }
                        }
                    }
                }
            }
        }
    }
}

class MoveToAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return
        val sLevel = player?.serverLevel() ?: server.overworld()

        val subjectType = node.params["subjectType"] ?: "PLAYER_POKEMON"
        val subjectId = node.params["pokemonSlot"] ?: node.params["subjectIdentifier"] ?: node.params["targetIdentifier"] ?: "1"

        // 1. Resolve Subject LivingEntity
        val subjectEntity = StoryLookAtManager.resolveSubjectEntity(
            sLevel,
            player,
            if (subjectType == "PLAYER_POKEMON") LookSubjectType.PLAYER_POKEMON else LookSubjectType.NPC_TAG,
            subjectId
        ) ?: return

        // 2. Resolve Target Destination Vec3
        val destType = node.params["targetDestinationType"] ?: "COORDINATES"
        val destId = node.params["destinationIdentifier"]?.ifBlank { node.params["coordinates"] ?: "~0 ~0 ~5" } ?: (node.params["coordinates"] ?: "~0 ~0 ~5")
        val targetVec = when (destType) {
            "PLAYER" -> {
                if (destId.isNotBlank() && destId != "~" && destId != "Player" && destId.contains(" ")) {
                    CoordinateResolver.resolveSafeVec3(
                        destId,
                        sLevel,
                        player,
                        server,
                        defaultOrigin = player?.position() ?: subjectEntity.position(),
                        safePosition = true,
                        snapToGround = true
                    )
                } else {
                    player?.position() ?: subjectEntity.position()
                }
            }
            "ENTITY_TAG" -> {
                val box = player?.boundingBox?.inflate(128.0) ?: AABB(-1000.0, -100.0, -1000.0, 1000.0, 300.0, 1000.0)
                val target = sLevel.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive && it.tags.contains(destId) }.firstOrNull()
                target?.position() ?: subjectEntity.position()
            }
            "WORLD_BLOCK_TAG" -> {
                StoryTagManager.getBlockVec3(destId) ?: subjectEntity.position()
            }
            else -> {
                CoordinateResolver.resolveSafeVec3(
                    destId,
                    sLevel,
                    player,
                    server,
                    defaultOrigin = subjectEntity.position(),
                    safePosition = true,
                    snapToGround = true
                )
            }
        }

        // 3. Resolve Speed Mode
        val speedMode = node.params["speedMode"] ?: "WALK"
        val speedMultiplier = when (speedMode) {
            "SPRINT" -> 1.5
            "SNEAK" -> 0.5
            "CUSTOM" -> node.params["customSpeedMultiplier"]?.toDoubleOrNull() ?: 1.0
            else -> 1.0
        }

        val waitForCompletion = node.params["waitForCompletion"] != "false"
        val timeoutTicks = node.params["timeoutTicks"]?.toIntOrNull() ?: 100
        val onTimeoutBehavior = node.params["onTimeoutBehavior"] ?: "TELEPORT_TO_DESTINATION"
        val lockPositionOnArrival = node.params["lockPositionOnArrival"] != "false"

        // 4. Start Pathfinding
        if (waitForCompletion) {
            StoryPathfindingManager.startPathfinding(
                subject = subjectEntity,
                targetDestination = targetVec,
                speedModifier = speedMultiplier,
                waitForCompletion = true,
                timeoutTicks = timeoutTicks,
                onTimeoutBehavior = onTimeoutBehavior,
                lockPositionOnArrival = lockPositionOnArrival
            ) {
                val instance = StoryExecutor.activeStories.values.find { it.context == context }
                if (instance != null) {
                    StoryExecutor.continueOutgoingConnections(instance, node)
                }
            }
        } else {
            StoryPathfindingManager.startPathfinding(
                subject = subjectEntity,
                targetDestination = targetVec,
                speedModifier = speedMultiplier,
                waitForCompletion = false,
                timeoutTicks = timeoutTicks,
                onTimeoutBehavior = onTimeoutBehavior,
                lockPositionOnArrival = lockPositionOnArrival
            )
        }
    }
}

class SpawnStructureAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return

        val structureId = node.params["structureId"]?.ifBlank { "minecraft:small_house" } ?: "minecraft:small_house"
        val coordInput = node.params["coordinates"]?.ifBlank {
            "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"
        } ?: "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"

        val pos = CoordinateResolver.resolveBlockPos(coordInput, player, server)
        try {
            val cmd = "place structure $structureId ${pos.x} ${pos.y} ${pos.z}"
            server.commands.performPrefixedCommand(
                player?.createCommandSourceStack()?.withPermission(4)?.withSuppressedOutput()
                    ?: server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
        } catch (e: Exception) {
            StoryDebugger.recordLog(
                storyId = "",
                blockId = node.id,
                blockType = node.nodeType,
                status = NodeExecutionStatus.FAILED,
                level = "ERROR",
                message = "Failed to spawn structure '$structureId': ${e.message}",
                server = server
            )
        }
    }
}

class ChangePokemonPersonalityAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val server = context.server ?: player.server ?: return
        val slotStr = node.params["slotIndex"] ?: node.params["pokemonSlot"] ?: "1"
        val slotIdx = PokemonQuery.parsePartySlotIndex(slotStr)
        val preset = node.params["personalityPreset"] ?: "Heroic"

        try {
            val party = Cobblemon.storage.getParty(player)
            val pokemon = party.get(slotIdx)
            if (pokemon != null) {
                context.variables["pokemon_${slotIdx}_personality"] = preset
                player.sendSystemMessage(Component.literal("§a[CobbleBrain]§f Pokémon in slot ${slotIdx + 1} personality: §b$preset"))
            } else {
                StoryDebugger.recordLog(
                    storyId = "",
                    blockId = node.id,
                    blockType = node.nodeType,
                    status = NodeExecutionStatus.FAILED,
                    level = "WARN",
                    message = "No Pokémon found in party slot ${slotIdx + 1} to change personality.",
                    server = server
                )
            }
        } catch (e: Exception) {
            StoryDebugger.recordLog(
                storyId = "",
                blockId = node.id,
                blockType = node.nodeType,
                status = NodeExecutionStatus.FAILED,
                level = "ERROR",
                message = "Error changing Pokémon personality: ${e.message}",
                server = server
            )
        }
    }
}

class PartyPokemonEffectAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val server = context.server ?: player.server ?: return

        val healFullParty = node.params["healFullParty"] != "false"
        val cureStatus = node.params["cureStatus"] != "false"
        val restorePP = node.params["restorePP"] == "true" || healFullParty

        try {
            val party = Cobblemon.storage.getParty(player)
            for (pokemon in party) {
                if (healFullParty) {
                    pokemon.currentHealth = pokemon.maxHealth
                }
                if (cureStatus) {
                    pokemon.status = null
                }
                if (restorePP) {
                    pokemon.moveSet.forEach { move ->
                        move.currentPp = move.maxPp
                    }
                }
            }
            player.sendSystemMessage(Component.literal("§a[CobbleBrain]§f Party Pokémon restored successfully!"))
        } catch (e: Exception) {
            StoryDebugger.recordLog(
                storyId = "",
                blockId = node.id,
                blockType = node.nodeType,
                status = NodeExecutionStatus.FAILED,
                level = "ERROR",
                message = "Error healing Pokémon party: ${e.message}",
                server = server
            )
        }
    }
}

class JumpToStoryPointAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val targetSceneId = node.params["targetSceneId"]?.trim() ?: ""
        val targetNodeId = node.params["targetNodeId"]?.trim() ?: ""
        val instance = StoryExecutor.activeStories.values.find { it.context == context } ?: return

        if (targetSceneId.isNotBlank() && targetSceneId != instance.project.activeSceneId) {
            val targetScene = instance.project.scenes.find { it.id == targetSceneId || it.title.equals(targetSceneId, true) }
            if (targetScene != null) {
                instance.project.activeSceneId = targetScene.id
                val startNode = if (targetNodeId.isNotBlank()) {
                    targetScene.nodes.find { it.id == targetNodeId }
                } else {
                    targetScene.nodes.find { it.nodeType == NodeType.BEGIN_SCENE } ?: targetScene.nodes.firstOrNull()
                }
                if (startNode != null) {
                    StoryExecutor.executeNodeChain(instance, startNode)
                }
            }
        } else if (targetNodeId.isNotBlank()) {
            val targetNode = instance.project.getActiveScene()?.nodes?.find { it.id == targetNodeId }
            if (targetNode != null) {
                StoryExecutor.executeNodeChain(instance, targetNode)
            }
        }
    }
}

class RewindToStoryPointAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val server = context.server ?: context.player?.server ?: return
        val targetProfileId = node.params["profileId"]?.ifBlank { "checkpoint_1" } ?: "checkpoint_1"
        val targetScope = node.params["scope"] ?: "PLAYER"
        val checkpointData = vito.cobblebrain.engine.checkpoint.StoryCheckpointManager.loadCheckpoint(server, context.player, targetScope, targetProfileId, context.variables)

        if (checkpointData != null) {
            vito.cobblebrain.engine.checkpoint.StoryCheckpointManager.applyCheckpoint(context, checkpointData)
            val instance = StoryExecutor.activeStories.values.find { it.context == context }
            val targetSceneId = node.params["targetSceneId"]?.trim() ?: ""
            if (instance != null && targetSceneId.isNotBlank()) {
                val targetScene = instance.project.scenes.find { it.id == targetSceneId || it.title.equals(targetSceneId, true) }
                if (targetScene != null) {
                    instance.project.activeSceneId = targetScene.id
                    val startNode = targetScene.nodes.find { it.nodeType == NodeType.BEGIN_SCENE } ?: targetScene.nodes.firstOrNull()
                    if (startNode != null) {
                        StoryExecutor.executeNodeChain(instance, startNode)
                    }
                }
            }
        } else {
            StoryDebugger.recordLog(
                storyId = "",
                blockId = node.id,
                blockType = node.nodeType,
                status = NodeExecutionStatus.FAILED,
                level = "WARN",
                message = "Rewind failed: checkpoint '$targetProfileId' not found on disk.",
                server = server
            )
        }
    }
}

class ChangeScreenTintAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val tintColor = node.params["tintColor"] ?: "#FF0000"
        val alpha = node.params["alpha"]?.toFloatOrNull() ?: 0.5f
        val durationSec = node.params["durationSec"]?.toIntOrNull() ?: 3

        player.sendSystemMessage(Component.literal("§e[Screen Tint] $tintColor (alpha=$alpha, duration=${durationSec}s)"))
    }
}

