package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.StoryProject
import vito.cobblebrain.model.StorySerializer

class StoryMetadataInspectorWidget(
    val project: StoryProject,
    val panelX: Int,
    val panelY: Int,
    val panelWidth: Int = 150,
    val panelHeight: Int,
    val font: Font,
    val onClose: () -> Unit,
    val onDataChanged: () -> Unit,
    val onDuplicateStory: (StoryProject) -> Unit,
    val onStatus: (String) -> Unit
) {
    val childrenWidgets = mutableListOf<GuiEventListener>()
    private var focusedEditBox: EditBox? = null
    private var idError: Boolean = false

    init {
        buildUi()
    }

    fun buildUi() {
        childrenWidgets.clear()

        val inputX = panelX + 6
        val inputW = panelWidth - 12
        var currentY = panelY + 24

        // Close Button on Top Right Corner
        val closeBtn = Button.builder(Component.literal("✖")) {
            onClose()
        }.bounds(panelX + panelWidth - 20, panelY + 3, 16, 16).build()
        childrenWidgets.add(closeBtn)

        // 1. Story ID Field (Strict validation: only a-z, A-Z, 0-9, _, -)
        val idEdit = EditBox(font, inputX, currentY + 10, inputW, 16, Component.literal("ID"))
        idEdit.setMaxLength(60)
        idEdit.value = project.id
        idEdit.setFilter { text -> text.isEmpty() || text.matches(Regex("^[a-zA-Z0-9_-]+$")) }
        idEdit.setResponder { valText ->
            if (valText.isBlank() || !valText.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                idError = true
            } else {
                idError = false
                project.id = valText
                onDataChanged()
            }
        }
        childrenWidgets.add(idEdit)
        currentY += 30

        // 2. Display Name
        val nameEdit = EditBox(font, inputX, currentY + 10, inputW, 16, Component.literal("Display Name"))
        nameEdit.setMaxLength(60)
        nameEdit.value = project.name
        nameEdit.setResponder { valText ->
            project.name = valText
            onDataChanged()
        }
        childrenWidgets.add(nameEdit)
        currentY += 30

        // 3. Description
        val descEdit = EditBox(font, inputX, currentY + 10, inputW, 36, Component.literal("Description"))
        descEdit.setMaxLength(9999)
        descEdit.value = project.description
        descEdit.setResponder { valText ->
            project.description = valText
            onDataChanged()
        }
        childrenWidgets.add(descEdit)
        currentY += 50

        // 4. Version (e.g. 1.0.0)
        val verEdit = EditBox(font, inputX, currentY + 10, inputW, 16, Component.literal("Version"))
        verEdit.setMaxLength(30)
        verEdit.value = project.version
        verEdit.setResponder { valText ->
            project.version = valText
            onDataChanged()
        }
        childrenWidgets.add(verEdit)
        currentY += 34

        // 5. "Copy Data" Button
        val copyBtn = Button.builder(Component.literal("📋 Copy Data")) {
            val jsonStr = StorySerializer.toJson(project)
            onStatus("Story data copied to clipboard!")
        }.bounds(inputX, currentY + 5, inputW, 16).build()
        childrenWidgets.add(copyBtn)
        currentY += 22

        // 6. "Duplicate Story" Button
        val dupBtn = Button.builder(Component.literal("📋 Duplicate Story")) {
            val newId = StoryProject.generateUniqueNewStoryId()
            val duplicate = StorySerializer.fromJson(StorySerializer.toJson(project)) ?: project
            duplicate.id = newId
            duplicate.name = "${project.name} (Copy)"
            onDuplicateStory(duplicate)
            onStatus("Story duplicated successfully: ${duplicate.id}")
        }.bounds(inputX, currentY + 5, inputW, 16).build()
        childrenWidgets.add(dupBtn)
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0141418.toInt())
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 20, 0xFF22222A.toInt())

        guiGraphics.drawString(font, "📋 Story Metadata", panelX + 6, panelY + 5, 0xFF00FFCC.toInt(), false)

        var currentY = panelY + 24
        val idLabelColor = if (idError) 0xFFFF5555.toInt() else 0xFFA0A0A0.toInt()
        guiGraphics.drawString(font, if (idError) "ID (invalid!):" else "Story ID:", panelX + 6, currentY, idLabelColor, false)
        currentY += 30

        guiGraphics.drawString(font, "Display Name:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
        currentY += 30

        guiGraphics.drawString(font, "Description:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
        val charCount = project.description.length
        if (charCount >= 9000) {
            val countText = "$charCount/9999"
            val countColor = if (charCount >= 9999) 0xFFFF5555.toInt() else 0xFFFFEE55.toInt()
            val countW = font.width(countText)
            guiGraphics.drawString(font, countText, panelX + panelWidth - 6 - countW, currentY, countColor, false)
        }
        currentY += 50

        guiGraphics.drawString(font, "Version (e.g. 1.0.0):", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)

        childrenWidgets.toList().forEach { widget ->
            if (widget is Button) widget.render(guiGraphics, mouseX, mouseY, partialTick)
            if (widget is EditBox) widget.render(guiGraphics, mouseX, mouseY, partialTick)
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (mouseX < panelX || mouseX > panelX + panelWidth || mouseY < panelY || mouseY > panelY + panelHeight) {
            focusedEditBox?.isFocused = false
            focusedEditBox = null
            return false
        }
        var handled = false
        val snapshot = childrenWidgets.toList()
        for (w in snapshot) {
            if (w is EditBox) {
                val clicked = w.mouseClicked(mouseX, mouseY, button)
                if (clicked) {
                    w.isFocused = true
                    focusedEditBox = w
                    handled = true
                } else {
                    w.isFocused = false
                }
            } else if (w.mouseClicked(mouseX, mouseY, button)) {
                handled = true
            }
        }
        return handled
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        val focused = focusedEditBox
        if (focused != null && focused.isFocused) return focused.charTyped(codePoint, modifiers)
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val focused = focusedEditBox
        if (focused != null && focused.isFocused) return focused.keyPressed(keyCode, scanCode, modifiers)
        return false
    }
}
