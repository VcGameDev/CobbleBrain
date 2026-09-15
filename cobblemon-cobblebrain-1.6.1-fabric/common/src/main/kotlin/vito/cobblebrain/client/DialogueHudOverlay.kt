package vito.cobblebrain.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import kotlin.math.sin

object DialogueHudOverlay {
    var isActive: Boolean = false
        private set

    var speakerName: String = ""
    var speakerType: String = "COBBLEMON"
    var dialogueText: String = ""
    var freezePlayer: Boolean = true
    var instanceId: String = ""
    var nodeId: String = ""

    private var typewriterIndex: Float = 0f
    private const val TYPEWRITER_SPEED: Float = 1.2f // characters per frame
    private var lastCharSoundCount: Int = 0

    var onAdvanceCallback: ((instanceId: String, nodeId: String) -> Unit)? = null

    fun showDialogue(
        speaker: String,
        type: String,
        text: String,
        freeze: Boolean,
        instId: String,
        nId: String
    ) {
        speakerName = speaker.ifBlank { "NPC" }
        speakerType = type
        dialogueText = text
        freezePlayer = freeze
        instanceId = instId
        nodeId = nId
        typewriterIndex = 0f
        lastCharSoundCount = 0
        isActive = true
    }

    fun close() {
        isActive = false
    }

    fun render(guiGraphics: GuiGraphics, client: Minecraft) {
        if (!isActive) return

        val screenW = client.window.guiScaledWidth
        val screenH = client.window.guiScaledHeight
        val font = client.font

        val boxW = 380.coerceAtMost(screenW - 40)
        val boxH = 64
        val boxX = (screenW - boxW) / 2
        val boxY = screenH - boxH - 20

        // Dark Semi-Transparent Outer Card Container
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xEE0F172A.toInt())
        // Cyan Highlight Outline
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFF38BDF8.toInt())
        guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxH, 0x33FFFFFF)
        guiGraphics.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, 0x33FFFFFF)
        guiGraphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0x33FFFFFF)

        // Speaker Portrait/Badge Pill on top-left
        val icon = when {
            speakerType.equals("COBBLEMON", true) || speakerType.equals("PARTY_FIRST", true) || speakerType.equals("PARTY_SLOT", true) || speakerType.equals("PARTY_RANDOM", true) -> "🐾"
            speakerType.equals("NARRATOR", true) -> "📜"
            speakerName.contains("Villager", true) || speakerName.contains("Trader", true) -> "🧙"
            speakerName.contains("Golem", true) -> "🛡️"
            speakerName.contains("Zombie", true) || speakerName.contains("Skeleton", true) || speakerName.contains("Creeper", true) -> "👾"
            speakerName.contains("Cat", true) || speakerName.contains("Dog", true) || speakerName.contains("Wolf", true) || speakerName.contains("Fox", true) -> "🦊"
            speakerType.equals("NPC", true) || speakerType.equals("TARGET_MOB", true) -> "👤"
            else -> "🗣️"
        }
        val badgeText = "$icon $speakerName"
        val badgeW = font.width(badgeText) + 16
        val badgeH = 16
        val badgeX = boxX + 10
        val badgeY = boxY - 10

        guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xFF0EA5E9.toInt())
        guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, 0xFF38BDF8.toInt())
        guiGraphics.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, 0xFF38BDF8.toInt())
        guiGraphics.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, 0xFF38BDF8.toInt())
        guiGraphics.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, 0xFF38BDF8.toInt())
        guiGraphics.drawString(font, badgeText, badgeX + 8, badgeY + 4, 0xFFFFFFFF.toInt(), true)

        // Typewriter Animation Increments
        if (typewriterIndex < dialogueText.length) {
            typewriterIndex = (typewriterIndex + TYPEWRITER_SPEED).coerceAtMost(dialogueText.length.toFloat())
            val currentVisible = typewriterIndex.toInt()
            if (currentVisible > lastCharSoundCount && currentVisible % 3 == 0) {
                lastCharSoundCount = currentVisible
                try {
                    client.level?.playSound(client.player, client.player!!.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.15f, 1.8f)
                } catch (_: Exception) {}
            }
        }

        // Text Body Word Wrapping
        val currentSub = dialogueText.substring(0, typewriterIndex.toInt().coerceAtMost(dialogueText.length))
        val wrappedLines = font.split(Component.literal(currentSub), boxW - 24)
        var ly = boxY + 12
        wrappedLines.take(3).forEach { line ->
            guiGraphics.drawString(font, line, boxX + 12, ly, 0xFFF8FAFC.toInt(), false)
            ly += 11
        }

        // Advance Prompt on Bottom-Right
        val isComplete = typewriterIndex >= dialogueText.length
        val promptText = if (isComplete) "▶ Press [SPACE] or Click to continue" else "⏩ Click to skip animation"
        val time = client.level?.gameTime ?: 0L
        val alphaPulse = if (isComplete) (sin(time.toDouble() / 3.0) * 40 + 215).toInt() else 180
        val promptColor = (alphaPulse shl 24) or 0x38BDF8

        guiGraphics.drawString(font, promptText, boxX + boxW - font.width(promptText) - 10, boxY + boxH - 12, promptColor, false)
    }

    fun handleInput(): Boolean {
        if (!isActive) return false

        if (typewriterIndex < dialogueText.length) {
            // Skip typewriter animation instantly
            typewriterIndex = dialogueText.length.toFloat()
            return true
        } else {
            // Advance dialogue & trigger server callback
            val inst = instanceId
            val node = nodeId
            close()
            onAdvanceCallback?.invoke(inst, node)
            return true
        }
    }
}
