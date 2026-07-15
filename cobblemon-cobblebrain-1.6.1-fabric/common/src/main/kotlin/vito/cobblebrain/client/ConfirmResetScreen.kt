package vito.cobblebrain.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ConfirmResetScreen(
    private val parentScreen: Screen?,
    private val pokemonDisplayName: String,
    private val pokemonUuid: String,
    private val inParty: Boolean,
    private val onConfirm: () -> Unit
) : Screen(Component.translatable("cobblebrain.screen.confirm_reset.title")) {

    private fun drawButtonFrame(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        guiGraphics.fill(x, y, x + w, y + h, 0x55333333)
        guiGraphics.fill(x, y, x + w, y + 1, 0xFFB0B0B0.toInt())
        guiGraphics.fill(x, y, x + 1, y + h, 0xFFB0B0B0.toInt())
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0xFF4A4A4A.toInt())
        guiGraphics.fill(x, y + h - 1, x + w, y + h, 0xFF4A4A4A.toInt())
    }

    override fun init() {
        val cx = width / 2
        val cy = height / 2

        // Confirm button
        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.button.yes_reset")) {
                onConfirm()
                minecraft?.setScreen(parentScreen)
            }.bounds(cx + 10, cy + 30, 100, 20).build()
        )

        // Cancel button
        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.button.cancel")) {
                minecraft?.setScreen(parentScreen)
            }.bounds(cx - 110, cy + 30, 100, 20).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val cx = width / 2
        val cy = height / 2
        val textWrapWidth = 286
        val messageLines = font.split(Component.translatable("cobblebrain.screen.confirm_reset.message", pokemonDisplayName), textWrapWidth)

        // Draw background box
        guiGraphics.fill(cx - 165, cy - 55, cx + 165, cy + 60, 0xCC000000.toInt())
        guiGraphics.fill(cx - 164, cy - 54, cx + 164, cy + 59, 0xFF1A1A1A.toInt())

        // Title
        guiGraphics.drawCenteredString(font, Component.translatable("cobblebrain.screen.confirm_reset.header"), cx, cy - 46, 0xFFFF5555.toInt())

        // Message
        var lineY = cy - 24
        messageLines.forEach { line ->
            guiGraphics.drawCenteredString(font, line, cx, lineY, 0xFFFFFFFF.toInt())
            lineY += font.lineHeight - 1
        }

        guiGraphics.drawCenteredString(
            font,
            Component.translatable("cobblebrain.screen.confirm_reset.pc_warning"),
            cx,
            cy,
            0xFFFFAA00.toInt()
        )

        guiGraphics.drawCenteredString(font, Component.translatable("cobblebrain.screen.confirm_reset.are_you_sure"), cx, cy + 16, 0xFFAAAAAA.toInt())
        drawButtonFrame(guiGraphics, cx - 110, cy + 30, 100, 20)
        drawButtonFrame(guiGraphics, cx + 10, cy + 30, 100, 20)
    }

    override fun shouldCloseOnEsc(): Boolean = true
}
