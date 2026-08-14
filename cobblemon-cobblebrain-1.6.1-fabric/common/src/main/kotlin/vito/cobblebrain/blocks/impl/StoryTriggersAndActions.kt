package vito.cobblebrain.blocks.impl

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import vito.cobblebrain.blocks.interfaces.IAction
import vito.cobblebrain.blocks.interfaces.ITrigger
import vito.cobblebrain.engine.StoryContext
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.NodeType
import kotlin.math.sqrt

// ==========================================================
// AÇÕES (ACTIONS)
// ==========================================================

class SendMessageAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val rawText = node.params["messageText"]?.ifBlank { node.content.ifBlank { node.title } }
            ?: node.content.ifBlank { node.title }

        var messageText = rawText
        context.variables.forEach { (k, v) ->
            messageText = messageText.replace("{$k}", v.toString())
        }

        val player = context.player
        val messageType = node.params["messageType"] ?: "CHAT"
        val comp = Component.literal(messageText)

        if (player != null) {
            when (messageType) {
                "TITLE" -> {
                    val sub = node.params["subTitle"] ?: ""
                    player.sendSystemMessage(Component.literal("=== $messageText ==="), false)
                    if (sub.isNotBlank()) player.sendSystemMessage(Component.literal(sub), false)
                }
                "ACTION_BAR" -> player.sendSystemMessage(comp, true)
                else -> player.sendSystemMessage(comp, false)
            }
        } else {
            context.server?.playerList?.players?.forEach { p ->
                p.sendSystemMessage(comp, false)
            }
        }
    }
}

class ShowTitleAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val mainTitle = node.params["mainTitle"] ?: "Missão Concluída!"
        val subTitle = node.params["subTitle"] ?: ""
        val fadeIn = node.params["fadeIn"]?.toIntOrNull() ?: 10
        val stay = node.params["stay"]?.toIntOrNull() ?: 40
        val fadeOut = node.params["fadeOut"]?.toIntOrNull() ?: 10

        try {
            val server = context.server ?: player.server
            val name = player.scoreboardName
            server?.commands?.performPrefixedCommand(server.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "title $name times $fadeIn $stay $fadeOut")
            if (subTitle.isNotBlank()) {
                server?.commands?.performPrefixedCommand(server.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "title $name subtitle {\"text\":\"$subTitle\"}")
            }
            server?.commands?.performPrefixedCommand(server.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "title $name title {\"text\":\"$mainTitle\"}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class TeleportAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val destX = node.params["destX"]?.toDoubleOrNull() ?: player.x
        val destY = node.params["destY"]?.toDoubleOrNull() ?: player.y
        val destZ = node.params["destZ"]?.toDoubleOrNull() ?: player.z

        player.teleportTo(destX, destY, destZ)
        player.sendSystemMessage(Component.literal("Teleportado para: $destX, $destY, $destZ"))
    }
}

class ChangeWeatherAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val server = context.server ?: context.player?.server ?: return
        val weatherType = (node.params["weatherType"] ?: "CLEAR").lowercase()
        val duration = node.params["durationTicks"]?.toIntOrNull() ?: 6000
        try {
            server.commands.performPrefixedCommand(server.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "weather $weatherType $duration")
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
            server.commands.performPrefixedCommand(server.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "time set $timeTicks")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class SpawnBlockAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player
        val server = context.server ?: player?.server ?: return
        val blockId = node.params["blockId"]?.ifBlank { "minecraft:stone" } ?: "minecraft:stone"
        val px = node.params["posX"]?.ifBlank { "~" } ?: "~"
        val py = node.params["posY"]?.ifBlank { "~" } ?: "~"
        val pz = node.params["posZ"]?.ifBlank { "~" } ?: "~"
        try {
            val cmd = "setblock $px $py $pz $blockId"
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
        val px = node.params["posX"]?.ifBlank { "~" } ?: "~"
        val py = node.params["posY"]?.ifBlank { "~" } ?: "~"
        val pz = node.params["posZ"]?.ifBlank { "~" } ?: "~"
        val propKey = node.params["propertyKey"] ?: "open"
        val propVal = node.params["propertyValue"] ?: "true"
        try {
            val cmd = "setblock $px $py $pz minecraft:lever[$propKey=$propVal]"
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
        val entityId = node.params["entityId"]?.ifBlank { "minecraft:villager" } ?: "minecraft:villager"
        val customName = node.params["customName"]
        val px = node.params["posX"]?.ifBlank { "~" } ?: "~"
        val py = node.params["posY"]?.ifBlank { "~" } ?: "~"
        val pz = node.params["posZ"]?.ifBlank { "~" } ?: "~"
        try {
            val tag = if (!customName.isNullOrBlank()) " {CustomName:'{\"text\":\"$customName\"}'}" else ""
            val cmd = "summon $entityId $px $py $pz$tag"
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
        val selector = node.params["entitySelector"]?.ifBlank { "@e[type=zombie,distance=..10]" } ?: "@e[type=zombie,distance=..10]"
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
        val customName = node.params["customName"] ?: ""
        val noAi = node.params["noAi"] == "true"
        val selector = node.params["entitySelector"] ?: "@e[type=!player,distance=..5,limit=1]"
        try {
            val noAiVal = if (noAi) "1b" else "0b"
            val nbt = "{NoAI:$noAiVal" + (if (customName.isNotBlank()) ",CustomName:'{\"text\":\"$customName\"}'" else "") + "}"
            val cmd = "data merge entity $selector $nbt"
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

class SpawnCobblemonAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val species = node.params["species"]?.ifBlank { "Pikachu" } ?: "Pikachu"
        val level = node.params["level"]?.toIntOrNull() ?: 5
        val isShiny = node.params["shiny"] == "true"
        val gender = node.params["gender"]
        val form = node.params["form"]

        try {
            val sb = StringBuilder("spawnpokemon $species level=$level")
            if (isShiny) sb.append(" shiny=yes")
            if (!gender.isNullOrBlank() && gender != "RANDOM") sb.append(" gender=${gender.lowercase()}")
            if (!form.isNullOrBlank()) sb.append(" form=$form")
            sb.append(" at ${player.blockX} ${player.blockY} ${player.blockZ}")

            val cmd = sb.toString()
            player.server?.commands?.performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                cmd
            )
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
        val player = context.player ?: return
        val effectId = node.params["effectId"]?.ifBlank { "minecraft:speed" } ?: "minecraft:speed"
        val duration = node.params["durationSec"]?.toIntOrNull() ?: 10
        val amplifier = ((node.params["amplifier"]?.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
        try {
            val cmd = "effect give ${player.scoreboardName} $effectId $duration $amplifier"
            player.server?.commands?.performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
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
        try {
            val cmd = "effect give @e[distance=..$radius] $effectId $duration $amplifier"
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
        val amount = node.params["amount"]?.toIntOrNull() ?: 1
        val px = node.params["posX"]?.ifBlank { "~" } ?: "~"
        val py = node.params["posY"]?.ifBlank { "~" } ?: "~"
        val pz = node.params["posZ"]?.ifBlank { "~" } ?: "~"
        try {
            val cmd = "summon item $px $py $pz {Item:{id:\"$itemId\",Count:${amount}b}}"
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
        val px = node.params["posX"]?.ifBlank { "~" } ?: "~"
        val py = node.params["posY"]?.ifBlank { "~" } ?: "~"
        val pz = node.params["posZ"]?.ifBlank { "~" } ?: "~"
        try {
            val cmd = "particle $particleId $px $py $pz 0.5 0.5 0.5 0.1 $count"
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

// ==========================================================
// GATILHOS (TRIGGERS)
// ==========================================================

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
        val tx = node.params["targetX"]?.toDoubleOrNull() ?: 0.0
        val ty = node.params["targetY"]?.toDoubleOrNull() ?: 64.0
        val tz = node.params["targetZ"]?.toDoubleOrNull() ?: 0.0
        val radius = node.params["radius"]?.toDoubleOrNull() ?: 5.0

        val px = player.x
        val py = player.y
        val pz = player.z

        val dist = sqrt((px - tx) * (px - tx) + (py - ty) * (py - ty) + (pz - tz) * (pz - tz))
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
