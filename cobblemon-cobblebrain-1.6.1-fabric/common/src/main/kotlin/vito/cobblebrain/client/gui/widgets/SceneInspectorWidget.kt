package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.SceneData

class SceneInspectorWidget(
    val scene: SceneData,
    val panelX: Int,
    val panelY: Int,
    val panelWidth: Int = 140,
    val panelHeight: Int,
    val font: Font,
    val onClose: () -> Unit,
    val onDataChanged: () -> Unit
) {
    val childrenWidgets = mutableListOf<GuiEventListener>()
    private var focusedEditBox: EditBox? = null

    init {
        buildUi()
    }

    fun buildUi() {
        childrenWidgets.clear()

        val inputX = panelX + 6
        val inputW = panelWidth - 12
        var currentY = panelY + 24

        val closeBtn = Button.builder(Component.literal("✖")) {
            onClose()
        }.bounds(panelX + panelWidth - 20, panelY + 3, 16, 16).build()
        childrenWidgets.add(closeBtn)

        val tEdit = EditBox(font, inputX, currentY + 10, inputW, 16, Component.literal("Nome"))
        tEdit.setMaxLength(50)
        tEdit.value = scene.title
        tEdit.setResponder { valText ->
            scene.title = valText
            onDataChanged()
        }
        childrenWidgets.add(tEdit)
        currentY += 30

        val descEdit = EditBox(font, inputX, currentY + 10, inputW, 36, Component.literal("Descrição"))
        descEdit.setMaxLength(250)
        descEdit.value = scene.description
        descEdit.setResponder { valText ->
            scene.description = valText
            onDataChanged()
        }
        childrenWidgets.add(descEdit)
        currentY += 50

        // Alternador de Cena Inicial da História
        val startLabel = if (scene.isStartScene) "🟢 Cena Inicial: SIM" else "⚪ Cena Inicial: NÃO"
        val startBtn = Button.builder(Component.literal(startLabel)) {
            scene.isStartScene = !scene.isStartScene
            buildUi()
            onDataChanged()
        }.bounds(inputX, currentY + 10, inputW, 16).build()
        childrenWidgets.add(startBtn)
        currentY += 28

        // Alternador de Cena Final da História
        val endLabel = if (scene.isEndScene) "🛑 Cena Final: SIM" else "⚪ Cena Final: NÃO"
        val endBtn = Button.builder(Component.literal(endLabel)) {
            scene.isEndScene = !scene.isEndScene
            buildUi()
            onDataChanged()
        }.bounds(inputX, currentY + 10, inputW, 16).build()
        childrenWidgets.add(endBtn)
        currentY += 28
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0141418.toInt())
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 20, 0xFF22222A.toInt())

        val headerTitle = font.plainSubstrByWidth("Cena: ${scene.title}", panelWidth - 26)
        guiGraphics.drawString(font, headerTitle, panelX + 6, panelY + 5, 0xFF00FFCC.toInt(), false)

        var currentY = panelY + 24
        guiGraphics.drawString(font, "Nome da Cena:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
        currentY += 30
        guiGraphics.drawString(font, "Descrição:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
        currentY += 50
        guiGraphics.drawString(font, "Propriedades Globais:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)

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
