package vito.cobblebrain.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

class ConfirmPersonalityComplexityScreen(
    private val parentScreen: Screen?,
    private val pokemonDisplayName: String,
    private val complexityScore: Double,
    private val onConfirm: () -> Unit
) : Screen(Component.translatable("cobblebrain.screen.personality_edit.complexity.warning_title")) {

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

        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.button.cancel")) {
                minecraft?.setScreen(parentScreen)
            }.bounds(cx - 110, cy + 42, 100, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.button.save_anyway")) {
                onConfirm()
            }.bounds(cx + 10, cy + 42, 100, 20).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val cx = width / 2
        val cy = height / 2
        val textWrapWidth = 316
        val sections = listOf(
            font.split(Component.translatable("cobblebrain.screen.personality_edit.complexity.warning_line1", pokemonDisplayName, complexityScore.roundToInt().toString()), textWrapWidth),
            font.split(Component.translatable("cobblebrain.screen.personality_edit.complexity.warning_line2"), textWrapWidth),
            font.split(Component.translatable("cobblebrain.screen.personality_edit.complexity.warning_line3"), textWrapWidth)
        )

        guiGraphics.fill(cx - 170, cy - 68, cx + 170, cy + 82, 0xCC000000.toInt())
        guiGraphics.fill(cx - 169, cy - 67, cx + 169, cy + 81, 0xFF1A1A1A.toInt())

        var y = cy - 54
        guiGraphics.drawCenteredString(font, Component.translatable("cobblebrain.screen.personality_edit.complexity.warning_title"), cx, y, 0xFFFF5555.toInt())
        y = cy - 30
        sections.forEachIndexed { sectionIdx, lines ->
            val color = when (sectionIdx) {
                0 -> 0xFFFFFFFF.toInt()
                1 -> 0xFFFFCC66.toInt()
                else -> 0xFFCCCCCC.toInt()
            }
            if (sectionIdx == 2) {
                y += font.lineHeight - 2
            }
            lines.forEach { line ->
                guiGraphics.drawCenteredString(font, line, cx, y, color)
                y += font.lineHeight - 1
            }
            y += 2
        }
        drawButtonFrame(guiGraphics, cx - 110, cy + 42, 100, 20)
        drawButtonFrame(guiGraphics, cx + 10, cy + 42, 100, 20)
    }

    override fun shouldCloseOnEsc(): Boolean = true
}
