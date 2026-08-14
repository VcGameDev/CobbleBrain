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

    private val modalWidth = 320
    private val modalHeight = 250
    private val modalX = (screenWidth - modalWidth) / 2
    private val modalY = (screenHeight - modalHeight) / 2

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

        // 1. ID do Projeto (Usado para comandos / executores)
        idBox = EditBox(font, inputX, currentY, inputW, 14, Component.literal("ID da História"))
        idBox.value = project.id.ifBlank { project.name }
        idBox.setMaxLength(100)
        idBox.setResponder {
            project.id = it
            onDataChanged()
        }
        currentY += 28

        // 2. Título (Nome para Exibição)
        titleBox = EditBox(font, inputX, currentY, inputW, 14, Component.literal("Título"))
        titleBox.value = project.name
        titleBox.setMaxLength(100)
        titleBox.setResponder {
            project.name = it
            onDataChanged()
        }
        currentY += 28

        // 3. Autor
        authorBox = EditBox(font, inputX, currentY, inputW, 14, Component.literal("Autor"))
        authorBox.value = project.author
        authorBox.setMaxLength(100)
        authorBox.setResponder {
            project.author = it
            onDataChanged()
        }
        currentY += 28

        // 4. Versão
        versionBox = EditBox(font, inputX, currentY, inputW, 14, Component.literal("Versão"))
        versionBox.value = project.version
        versionBox.setMaxLength(30)
        versionBox.setResponder {
            project.version = it
            onDataChanged()
        }
        currentY += 28

        // 5. Descrição
        descBox = EditBox(font, inputX, currentY, inputW, 30, Component.literal("Descrição"))
        descBox.value = project.description
        descBox.setMaxLength(300)
        descBox.setResponder {
            project.description = it
            onDataChanged()
        }

        // Botão Salvar e Fechar
        closeButton = Button.builder(Component.literal("✔ Concluído")) {
            onClose()
        }.bounds(modalX + (modalWidth - 100) / 2, modalY + modalHeight - 24, 100, 18).build()
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF1C1C24.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 22, 0xFF22222D.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())

        guiGraphics.drawString(font, "📋 Metadados da História", modalX + 10, modalY + 6, 0xFF00FFCC.toInt(), false)

        var currentY = modalY + 20
        guiGraphics.drawString(font, "ID (Comando):", modalX + 15, currentY, 0xFFA0A0A0.toInt(), false)
        idBox.render(guiGraphics, mouseX, mouseY, partialTick)
        currentY += 28

        guiGraphics.drawString(font, "Título Exibição:", modalX + 15, currentY, 0xFFA0A0A0.toInt(), false)
        titleBox.render(guiGraphics, mouseX, mouseY, partialTick)
        currentY += 28

        guiGraphics.drawString(font, "Autor:", modalX + 15, currentY, 0xFFA0A0A0.toInt(), false)
        authorBox.render(guiGraphics, mouseX, mouseY, partialTick)
        currentY += 28

        guiGraphics.drawString(font, "Versão:", modalX + 15, currentY, 0xFFA0A0A0.toInt(), false)
        versionBox.render(guiGraphics, mouseX, mouseY, partialTick)
        currentY += 28

        guiGraphics.drawString(font, "Descrição:", modalX + 15, currentY, 0xFFA0A0A0.toInt(), false)
        descBox.render(guiGraphics, mouseX, mouseY, partialTick)

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
