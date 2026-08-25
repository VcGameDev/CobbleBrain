package vito.cobblebrain.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

object LoadingTransitionOverlay {

    var isActive: Boolean = false
        private set

    private var startTimeMs: Long = 0L
    private var durationMs: Long = 2000L
    private var titleText: String = "Loading Checkpoint..."
    private var subTitleText: String = "Synchronizing State..."
    private var selectedTip: String = ""
    private var styleType: String = "BLACK_FADE"
    private var soundIdStr: String = "minecraft:entity.player.levelup"
    private var soundPlayed: Boolean = false

    fun startTransition(
        title: String = "Loading Checkpoint...",
        subTitle: String = "Synchronizing State...",
        tipsStr: String = "",
        style: String = "BLACK_FADE",
        durationTicks: Int = 40,
        soundId: String = "minecraft:entity.player.levelup"
    ) {
        val tipsList = tipsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
        selectedTip = tipsList.randomOrNull() ?: "Save points restore your story progress safely."
        titleText = title.ifBlank { "Loading Checkpoint..." }
        subTitleText = subTitle
        styleType = style
        soundIdStr = soundId
        durationMs = (maxOf(10, durationTicks) * 50L)
        startTimeMs = System.currentTimeMillis()
        soundPlayed = false
        isActive = true
    }

    fun stopTransition() {
        isActive = false
    }

    fun render(guiGraphics: GuiGraphics, client: Minecraft) {
        if (!isActive) return

        val now = System.currentTimeMillis()
        val elapsed = now - startTimeMs
        if (elapsed >= durationMs) {
            if (!soundPlayed) {
                playCompletionSound(client)
                soundPlayed = true
            }
            isActive = false
            return
        }

        val progress = elapsed.toFloat() / durationMs.toFloat()
        // Smooth Fade In (first 20%) and Fade Out (last 20%)
        val alphaFactor = when {
            progress < 0.2f -> progress / 0.2f
            progress > 0.8f -> (1.0f - progress) / 0.2f
            else -> 1.0f
        }.coerceIn(0.0f, 1.0f)

        val screenW = client.window.guiScaledWidth
        val screenH = client.window.guiScaledHeight

        // Background Render based on Style
        val alphaInt = (alphaFactor * 240).toInt().coerceIn(0, 255)
        val bgColor = (alphaInt shl 24) or 0x0A0A0C
        guiGraphics.fill(0, 0, screenW, screenH, bgColor)

        val font = client.font

        // Main Title (Centered & Scaled)
        guiGraphics.pose().pushPose()
        val titleScale = 1.6f
        val titleW = font.width(titleText)
        val titleX = (screenW / 2.0f) - (titleW * titleScale / 2.0f)
        val titleY = (screenH / 2.0f) - 30.0f
        guiGraphics.pose().translate(titleX, titleY, 0.0f)
        guiGraphics.pose().scale(titleScale, titleScale, 1.0f)
        val titleAlpha = (alphaFactor * 255).toInt().coerceIn(4, 255)
        val titleColor = (titleAlpha shl 24) or 0x00FFCC
        guiGraphics.drawString(font, titleText, 0, 0, titleColor, true)
        guiGraphics.pose().popPose()

        // Subtitle
        if (subTitleText.isNotBlank()) {
            val subW = font.width(subTitleText)
            val subX = (screenW - subW) / 2
            val subY = (screenH / 2) + 5
            val subColor = (titleAlpha shl 24) or 0xDDDDDD
            guiGraphics.drawString(font, subTitleText, subX, subY, subColor, true)
        }

        // Animated Loading Dots
        val dotsCount = ((elapsed / 300) % 4).toInt()
        val dotsStr = ".".repeat(dotsCount)
        guiGraphics.drawString(font, dotsStr, (screenW / 2) + (font.width(subTitleText) / 2) + 2, (screenH / 2) + 5, (titleAlpha shl 24) or 0x00FFCC, false)

        // Random Lore Tip
        if (selectedTip.isNotBlank()) {
            val tipFormatted = "💡 Tip: $selectedTip"
            val tipW = font.width(tipFormatted)
            val tipX = (screenW - tipW) / 2
            val tipY = screenH - 35
            val tipColor = (titleAlpha shl 24) or 0xFFD700
            guiGraphics.drawString(font, tipFormatted, tipX, tipY, tipColor, true)
        }
    }

    private fun playCompletionSound(client: Minecraft) {
        try {
            val soundRes = ResourceLocation.tryParse(soundIdStr)
            val soundEvent = if (soundRes != null) BuiltInRegistries.SOUND_EVENT.get(soundRes) else SoundEvents.UI_BUTTON_CLICK.value()
            val finalSound = soundEvent ?: SoundEvents.UI_BUTTON_CLICK.value()
            client.level?.playSound(
                client.player,
                client.player!!.blockPosition(),
                finalSound,
                SoundSource.MASTER,
                1.0f, 1.0f
            )
        } catch (_: Exception) {}
    }
}
