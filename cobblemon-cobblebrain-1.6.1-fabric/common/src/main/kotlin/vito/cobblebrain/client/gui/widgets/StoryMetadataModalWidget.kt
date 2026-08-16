package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.StoryProject

class StoryMetadataModalWidget(
    val project: StoryProject,
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    val onClose: () -> Unit,
    val onDataChanged: () -> Unit
) {

    private val modalWidth = 340.coerceAtMost(screenWidth - 20)
    private val modalHeight = 260.coerceAtMost(screenHeight - 20)
    private val modalX = maxOf(10, (screenWidth - modalWidth) / 2)
    private val modalY = maxOf(10, (screenHeight - modalHeight) / 2)

    private val idBox: EditBox
    private val titleBox: EditBox
    private val authorBox: EditBox
    private val versionBox: EditBox
    private val descBox: EditBox
    private val closeButton: Button

    private var focusedBox: EditBox? = null

    init {
        val inputX = modalX + 15
        val inputW = modalWidth - 30

        var currentY = modalY + 28

        // 1. Story ID (Used for commands / executors)
        idBox = EditBox(font, inputX, currentY, inputW, 14, Component.literal("Story ID"))
        idBox.value = project.id.ifBlank { project.name }
        idBox.setMaxLength(100)
        idBox.setResponder {
            project.id = it
            onDataChanged()
        }
        currentY += 28

        // 2. Title (Display Name)
        titleBox = EditBox(font, inputX, currentY, inputW, 14, Component.literal("Title"))
        titleBox.value = project.name
        titleBox.setMaxLength(100)
        titleBox.setResponder {
            project.name = it
            onDataChanged()
        }
        currentY += 28

        // 3. Author
        authorBox = EditBox(font, inputX, currentY, inputW, 14, Component.literal("Author"))
        authorBox.value = project.author
        authorBox.setMaxLength(100)
        authorBox.setResponder {
            project.author = it
            onDataChanged()
        }
        currentY += 28

        // 4. Version
        versionBox = EditBox(font, inputX, currentY, inputW, 14, Component.literal("Version"))
        versionBox.value = project.version
        versionBox.setMaxLength(30)
        versionBox.setResponder {
            project.version = it
            onDataChanged()
        }
        currentY += 28

        // 5. Description
        descBox = EditBox(font, inputX, currentY, inputW, 30, Component.literal("Description"))
        descBox.value = project.description
        descBox.setMaxLength(9999)
        descBox.setResponder {
            project.description = it
            onDataChanged()
        }

        listOf(idBox, titleBox, authorBox, versionBox, descBox).forEach {
            it.setEditable(true)
            it.active = true
        }

        // Save and Close Button
        closeButton = Button.builder(Component.literal("✔ Done")) {
            onClose()
        }.bounds(modalX + (modalWidth - 100) / 2, modalY + modalHeight - 24, 100, 18).build()
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF1C1C24.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 22, 0xFF22222D.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())

        guiGraphics.drawString(font, "📋 Story Metadata", modalX + 10, modalY + 6, 0xFF00FFCC.toInt(), false)

        var currentY = modalY + 20
        guiGraphics.drawString(font, "ID (Command):", modalX + 15, currentY, 0xFFA0A0A0.toInt(), false)
        idBox.render(guiGraphics, mouseX, mouseY, partialTick)
        currentY += 28

        guiGraphics.drawString(font, "Display Title:", modalX + 15, currentY, 0xFFA0A0A0.toInt(), false)
        titleBox.render(guiGraphics, mouseX, mouseY, partialTick)
        currentY += 28

        guiGraphics.drawString(font, "Author:", modalX + 15, currentY, 0xFFA0A0A0.toInt(), false)
        authorBox.render(guiGraphics, mouseX, mouseY, partialTick)
        currentY += 28

        guiGraphics.drawString(font, "Version:", modalX + 15, currentY, 0xFFA0A0A0.toInt(), false)
        versionBox.render(guiGraphics, mouseX, mouseY, partialTick)
        currentY += 28

        guiGraphics.drawString(font, "Description:", modalX + 15, currentY, 0xFFA0A0A0.toInt(), false)
        descBox.render(guiGraphics, mouseX, mouseY, partialTick)

        val charCount = descBox.value.length
        if (charCount >= 9000) {
            val countText = "$charCount/9999"
            val countColor = if (charCount >= 9999) 0xFFFF5555.toInt() else 0xFFFFEE55.toInt()
            val countW = font.width(countText)
            val countX = modalX + modalWidth - 15 - countW
            val countY = descBox.y + descBox.height + 2
            guiGraphics.drawString(font, countText, countX, countY, countColor, false)
        }

        closeButton.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun setFocus(target: EditBox?) {
        listOf(idBox, titleBox, authorBox, versionBox, descBox).forEach { it.isFocused = (it == target) }
        focusedBox = target
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val boxes = listOf(idBox, titleBox, authorBox, versionBox, descBox)
        for (b in boxes) {
            if (b.mouseClicked(mouseX, mouseY, button)) {
                setFocus(b)
                return true
            }
        }
        if (closeButton.mouseClicked(mouseX, mouseY, button)) return true
        setFocus(null)
        return true
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        val focus = focusedBox
        if (focus != null && focus.isFocused) {
            return focus.charTyped(codePoint, modifiers)
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val focus = focusedBox
        if (focus != null && focus.isFocused) {
            if (focus.keyPressed(keyCode, scanCode, modifiers)) return true
        }
        if (keyCode == 256) {
            onClose()
            return true
        }
        return false
    }
}
