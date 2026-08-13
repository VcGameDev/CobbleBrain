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

// ==========================================
// TRIGGERS (Com suporte a IF e IF NOT)
// ==========================================

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

// ==========================================
// ACTIONS
// ==========================================

class SendMessageAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val messageText = node.content.ifBlank { node.title }
        val player = context.player
        val messageType = node.params["messageType"] ?: "CHAT"

        val comp = Component.literal(messageText)

        if (player != null) {
            when (messageType) {
                "TITLE" -> player.sendSystemMessage(Component.literal("=== $messageText ==="), false)
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

class SpawnCobblemonAction : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        val player = context.player ?: return
        val species = node.params["species"]?.ifBlank { "Pikachu" } ?: "Pikachu"
        val level = node.params["level"]?.toIntOrNull() ?: 5

        try {
            val cmd = "spawnpokemon $species level=$level at ${player.blockX} ${player.blockY} ${player.blockZ}"
            player.server?.commands?.performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
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

        val soundRes = ResourceLocation.tryParse(soundIdStr)
        val soundEvent = if (soundRes != null) BuiltInRegistries.SOUND_EVENT.get(soundRes) else SoundEvents.UI_BUTTON_CLICK.value()
        val finalSound = soundEvent ?: SoundEvents.UI_BUTTON_CLICK.value()

        val level = player.serverLevel()
        level.playSound(
            null,
            player.x, player.y, player.z,
            finalSound,
            SoundSource.PLAYERS,
            volume, 1.0f
        )
    }
}
