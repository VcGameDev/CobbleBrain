package vito.cobblebrain.client.gui

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ConfirmUnsavedAiProfilesScreen(
    private val editorScreen: Screen,
    private val exitTargetScreen: Screen?,
    private val onSaveAndClose: () -> Unit
) : Screen(Component.translatable("cobblebrain.screen.ai_customization.unsaved.title")) {

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
        val btnW = 96
        val gap = 8
        val startX = cx - (btnW * 3 + gap * 2) / 2
        val btnY = cy + 28

        // Cancel (go back to editor)
        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.button.cancel")) {
                minecraft?.setScreen(editorScreen)
            }.bounds(startX, btnY, btnW, 20).build()
        )

        // Discard changes and exit
        addRenderableWidget(
            Button.builder(
                Component.translatable("cobblebrain.screen.ai_customization.unsaved.discard")
                    .withStyle(ChatFormatting.RED)
            ) {
                minecraft?.setScreen(exitTargetScreen)
            }.bounds(startX + btnW + gap, btnY, btnW, 20).build()
        )

        // Save & Close
        addRenderableWidget(
            Button.builder(
                Component.translatable("cobblebrain.screen.ai_customization.unsaved.save_and_close")
                    .withStyle(ChatFormatting.GREEN)
            ) {
                onSaveAndClose()
                minecraft?.setScreen(exitTargetScreen)
            }.bounds(startX + (btnW + gap) * 2, btnY, btnW, 20).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val cx = width / 2
        val cy = height / 2

        // Draw background box behind buttons
        guiGraphics.fill(cx - 170, cy - 55, cx + 170, cy + 58, 0xCC000000.toInt())
        guiGraphics.fill(cx - 169, cy - 54, cx + 169, cy + 57, 0xFF1A1A1A.toInt())

        super.render(guiGraphics, mouseX, mouseY, partialTick)

        guiGraphics.drawCenteredString(
            font,
            Component.translatable("cobblebrain.screen.ai_customization.unsaved.header"),
            cx,
            cy - 44,
            0xFFFFAA00.toInt()
        )

        val messageLines = font.split(
            Component.translatable("cobblebrain.screen.ai_customization.unsaved.message"),
            310
        )
        var lineY = cy - 22
        messageLines.forEach { line ->
            guiGraphics.drawCenteredString(font, line, cx, lineY, 0xFFFFFFFF.toInt())
            lineY += font.lineHeight + 2
        }

        val btnW = 96
        val gap = 8
        val startX = cx - (btnW * 3 + gap * 2) / 2
        val btnY = cy + 28
        drawButtonFrame(guiGraphics, startX, btnY, btnW, 20)
        drawButtonFrame(guiGraphics, startX + btnW + gap, btnY, btnW, 20)
        drawButtonFrame(guiGraphics, startX + (btnW + gap) * 2, btnY, btnW, 20)
    }

    override fun onClose() {
        minecraft?.setScreen(editorScreen)
    }

    override fun shouldCloseOnEsc(): Boolean = true
}
