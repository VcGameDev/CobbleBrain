package vito.cobblebrain.client.gui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class EditorSettingsScreen(
    private val parentEditor: StoryEditorScreen
) : Screen(Component.literal("Configurações do Editor")) {

    private var autoSaveEnabled: Boolean = parentEditor.autoSaveEnabled
    private var autoSaveInterval: Int = parentEditor.autoSaveIntervalSeconds
    private var intervalEditBox: EditBox? = null

    override fun init() {
        super.init()
        clearWidgets()

        val cx = width / 2
        val cy = height / 2

        // Botão Alternar Auto-Save
        val autoSaveText = if (autoSaveEnabled) "Auto-Save: ATIVADO" else "Auto-Save: DESATIVADO"
        addRenderableWidget(
            Button.builder(Component.literal(autoSaveText)) {
                autoSaveEnabled = !autoSaveEnabled
                init()
            }.bounds(cx - 100, cy - 30, 200, 20).build()
        )

        // Campo para editar Intervalo (em segundos)
        val intervalBox = EditBox(font, cx - 100, cy + 10, 200, 20, Component.literal("Intervalo (segundos)"))
        intervalBox.value = autoSaveInterval.toString()
        intervalBox.setFilter { text -> text.isEmpty() || text.all { it.isDigit() } }
        intervalEditBox = intervalBox
        addRenderableWidget(intervalBox)

        // Botão Salvar e Fechar
        addRenderableWidget(
            Button.builder(Component.literal("Salvar Configurações")) {
                val inputSec = intervalEditBox?.value?.toIntOrNull()
                if (inputSec != null && inputSec >= 10) {
                    autoSaveInterval = inputSec
                }
                parentEditor.autoSaveEnabled = autoSaveEnabled
                parentEditor.autoSaveIntervalSeconds = autoSaveInterval
                minecraft?.setScreen(parentEditor)
            }.bounds(cx - 100, cy + 50, 200, 20).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0xCC000000.toInt())

        val cx = width / 2
        val cy = height / 2

        // Moldura do modal
        guiGraphics.fill(cx - 120, cy - 60, cx + 120, cy + 85, 0xFF1E1E24.toInt())
        guiGraphics.drawCenteredString(font, "Configurações do Editor", cx, cy - 52, 0xFF00FFCC.toInt())
        guiGraphics.drawString(font, "Intervalo do Auto-Save (segundos):", cx - 100, cy - 3, 0xFFCCCCCC.toInt(), false)

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}
}
