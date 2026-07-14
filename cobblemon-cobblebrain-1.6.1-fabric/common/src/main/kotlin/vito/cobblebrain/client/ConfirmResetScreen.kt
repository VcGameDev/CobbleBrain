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
) : Screen(Component.literal("Confirm Reset")) {

    override fun init() {
        val cx = width / 2
        val cy = height / 2

        // Confirm button
        addRenderableWidget(
            Button.builder(Component.literal("Yes, Reset")) {
                onConfirm()
                minecraft?.setScreen(parentScreen)
            }.bounds(cx - 110, cy + 30, 100, 20).build()
        )

        // Cancel button
        addRenderableWidget(
            Button.builder(Component.literal("Cancel")) {
                minecraft?.setScreen(parentScreen)
            }.bounds(cx + 10, cy + 30, 100, 20).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val cx = width / 2
        val cy = height / 2

        // Draw background box
        guiGraphics.fill(cx - 155, cy - 55, cx + 155, cy + 60, 0xCC000000.toInt())
        guiGraphics.fill(cx - 154, cy - 54, cx + 154, cy + 59, 0xFF1A1A1A.toInt())

        // Title
        guiGraphics.drawCenteredString(font, "⚠  Reset Personality", cx, cy - 46, 0xFFFF5555.toInt())

        // Message line 1
        val line1 = "This will completely erase $pokemonDisplayName's"
        guiGraphics.drawCenteredString(font, line1, cx, cy - 26, 0xFFFFFFFF.toInt())

        // Message line 2
        guiGraphics.drawCenteredString(font, "personality data.", cx, cy - 14, 0xFFFFFFFF.toInt())

        // If in PC, warn about removal from editor
        if (!inParty) {
            guiGraphics.drawCenteredString(
                font,
                "It will also be removed from the editor.",
                cx, cy - 2, 0xFFFFAA00.toInt()
            )
        }

        guiGraphics.drawCenteredString(font, "Are you sure?", cx, cy + 14, 0xFFAAAAAA.toInt())
    }

    override fun shouldCloseOnEsc(): Boolean = true
}
