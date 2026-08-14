package vito.cobblebrain.client.gui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import vito.cobblebrain.config.StoryEditorConfig
import vito.cobblebrain.config.StoryEditorConfigData

class EditorSettingsScreen(
    private val parentEditor: StoryEditorScreen
) : Screen(Component.literal("Configurações do Editor")) {

    private var configData: StoryEditorConfigData = StoryEditorConfig.load()
    private var intervalEditBox: EditBox? = null

    override fun init() {
        super.init()
        clearWidgets()

        val cx = width / 2
        val cy = height / 2

        // 1. Botão Alternar Auto-Save
        val autoSaveText = if (configData.autoSaveEnabled) "Auto-Save: ATIVADO" else "Auto-Save: DESATIVADO"
        addRenderableWidget(
            Button.builder(Component.literal(autoSaveText)) {
                configData.autoSaveEnabled = !configData.autoSaveEnabled
                init()
            }.bounds(cx - 110, cy - 50, 220, 20).build()
        )

        // 2. Campo para editar Intervalo (em segundos)
        val intervalBox = EditBox(font, cx - 110, cy - 10, 220, 20, Component.literal("Intervalo (segundos)"))
        intervalBox.value = configData.autoSaveIntervalSeconds.toString()
        intervalBox.setFilter { text -> text.isEmpty() || text.all { it.isDigit() } }
        intervalEditBox = intervalBox
        addRenderableWidget(intervalBox)

        // 3. Botão Alternar Auto-Abrir Último Projeto
        val autoOpenText = if (configData.autoOpenLastProject) "Auto-Abrir Último Projeto: ATIVADO" else "Auto-Abrir Último Projeto: DESATIVADO"
        addRenderableWidget(
            Button.builder(Component.literal(autoOpenText)) {
                configData.autoOpenLastProject = !configData.autoOpenLastProject
                init()
            }.bounds(cx - 110, cy + 30, 220, 20).build()
        )

        // 4. Botão Salvar e Fechar
        addRenderableWidget(
            Button.builder(Component.literal("Salvar Configurações")) {
                val inputSec = intervalEditBox?.value?.toIntOrNull()
                if (inputSec != null && inputSec >= 10) {
                    configData.autoSaveIntervalSeconds = inputSec
                }
                StoryEditorConfig.save(configData)
                parentEditor.autoSaveEnabled = configData.autoSaveEnabled
                parentEditor.autoSaveIntervalSeconds = configData.autoSaveIntervalSeconds
                parentEditor.autoOpenLastProject = configData.autoOpenLastProject
                minecraft?.setScreen(parentEditor)
            }.bounds(cx - 110, cy + 70, 220, 20).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0xCC000000.toInt())

        val cx = width / 2
        val cy = height / 2

        // Moldura do modal
        guiGraphics.fill(cx - 130, cy - 75, cx + 130, cy + 105, 0xFF1E1E24.toInt())
        guiGraphics.drawCenteredString(font, "Configurações do Editor", cx, cy - 67, 0xFF00FFCC.toInt())
        guiGraphics.drawString(font, "Intervalo Auto-Save (segundos):", cx - 110, cy - 23, 0xFFCCCCCC.toInt(), false)

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}
}
