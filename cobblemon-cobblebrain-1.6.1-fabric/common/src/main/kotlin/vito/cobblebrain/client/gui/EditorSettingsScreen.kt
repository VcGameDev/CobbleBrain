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
) : Screen(Component.literal("Editor Settings")) {

    private var configData: StoryEditorConfigData = StoryEditorConfig.load()
    private var intervalEditBox: EditBox? = null

    override fun init() {
        super.init()
        clearWidgets()

        val cx = width / 2
        val cy = height / 2

        // 1. Toggle Auto-Save Button
        val autoSaveText = if (configData.autoSaveEnabled) "Auto-Save: ENABLED" else "Auto-Save: DISABLED"
        addRenderableWidget(
            Button.builder(Component.literal(autoSaveText)) {
                configData.autoSaveEnabled = !configData.autoSaveEnabled
                init()
            }.bounds(cx - 110, cy - 50, 220, 20).build()
        )

        // 2. Interval Edit Box (in seconds)
        val intervalBox = EditBox(font, cx - 110, cy - 10, 220, 20, Component.literal("Interval (seconds)"))
        intervalBox.value = configData.autoSaveIntervalSeconds.toString()
        intervalBox.setFilter { text -> text.isEmpty() || text.all { it.isDigit() } }
        intervalEditBox = intervalBox
        addRenderableWidget(intervalBox)

        // 3. Toggle Auto-Open Last Project Button
        val autoOpenText = if (configData.autoOpenLastProject) "Auto-Open Last Project: ENABLED" else "Auto-Open Last Project: DISABLED"
        addRenderableWidget(
            Button.builder(Component.literal(autoOpenText)) {
                configData.autoOpenLastProject = !configData.autoOpenLastProject
                init()
            }.bounds(cx - 110, cy + 30, 220, 20).build()
        )

        // 4. Save and Close Button
        addRenderableWidget(
            Button.builder(Component.literal("Save Settings")) {
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

        // Modal Frame
        guiGraphics.fill(cx - 130, cy - 75, cx + 130, cy + 105, 0xFF1E1E24.toInt())
        guiGraphics.drawCenteredString(font, "Editor Settings", cx, cy - 67, 0xFF00FFCC.toInt())
        guiGraphics.drawString(font, "Auto-Save Interval (seconds):", cx - 110, cy - 23, 0xFFCCCCCC.toInt(), false)

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}
}
