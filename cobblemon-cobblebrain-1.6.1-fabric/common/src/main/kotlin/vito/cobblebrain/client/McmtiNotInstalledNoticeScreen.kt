package vito.cobblebrain.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class McmtiNotInstalledNoticeScreen(
    private val parentScreen: Screen?
) : Screen(Component.translatable("cobblebrain.screen.mcmti_notice.title")) {

    override fun init() {
        val cx = width / 2
        val cy = height / 2

        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.button.back")) {
                minecraft?.setScreen(parentScreen)
            }.bounds(cx - 50, cy + 25, 100, 20).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val cx = width / 2
        val cy = height / 2

        // Draw background box
        guiGraphics.fill(cx - 165, cy - 45, cx + 165, cy + 55, 0xCC000000.toInt())
        guiGraphics.fill(cx - 164, cy - 44, cx + 164, cy + 54, 0xFF1A1A1A.toInt())

        // Title
        guiGraphics.drawCenteredString(font, Component.translatable("cobblebrain.screen.mcmti_notice.title"), cx, cy - 35, 0xFFFFCC00.toInt())

        // Lines
        guiGraphics.drawCenteredString(font, Component.translatable("cobblebrain.screen.mcmti_notice.line1"), cx, cy - 12, 0xFFCCCCCC.toInt())
        guiGraphics.drawCenteredString(font, Component.translatable("cobblebrain.screen.mcmti_notice.line2"), cx, cy + 2, 0xFFCCCCCC.toInt())
    }
}
