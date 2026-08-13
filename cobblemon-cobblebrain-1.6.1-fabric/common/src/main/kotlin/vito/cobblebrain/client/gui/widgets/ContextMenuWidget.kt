package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.SceneData

enum class ContextMenuAction {
    DELETE,
    DUPLICATE,
    DETACH_FROM_SCENE,
    COPY_DATA,
    PASTE_DATA,
    RESET_PROPERTIES,
    DISCONNECT_PORTS
}

class ContextMenuWidget(
    val screenX: Int,
    val screenY: Int,
    val targetNode: NodeData? = null,
    val targetScene: SceneData? = null,
    val font: Font,
    val onAction: (ContextMenuAction) -> Unit,
    val onClose: () -> Unit
) {
    data class MenuItem(
        val label: String,
        val action: ContextMenuAction,
        val enabled: Boolean = true
    )

    private val menuItems = mutableListOf<MenuItem>()
    private val menuWidth = 160
    private val itemHeight = 18
    var isShowingResetConfirmation: Boolean = false

    init {
        buildMenuItems()
    }

    private fun buildMenuItems() {
        menuItems.clear()
        if (targetNode != null) {
            menuItems.add(MenuItem("🗑️ Excluir Bloco", ContextMenuAction.DELETE))
            menuItems.add(MenuItem("📋 Duplicar Bloco", ContextMenuAction.DUPLICATE))
            menuItems.add(MenuItem("⛓️ Retirar da Cena", ContextMenuAction.DETACH_FROM_SCENE))
            menuItems.add(MenuItem("📄 Copiar Dados", ContextMenuAction.COPY_DATA))

            val canPaste = BlockDataClipboard.hasCompatibleData(targetNode.nodeType)
            menuItems.add(MenuItem("📌 Colar Dados", ContextMenuAction.PASTE_DATA, enabled = canPaste))
            menuItems.add(MenuItem("🔄 Resetar Propriedades", ContextMenuAction.RESET_PROPERTIES))
            menuItems.add(MenuItem("🔌 Desconectar Portas", ContextMenuAction.DISCONNECT_PORTS))
        } else if (targetScene != null) {
            menuItems.add(MenuItem("🗑️ Excluir Cena", ContextMenuAction.DELETE))
            menuItems.add(MenuItem("📋 Duplicar Cena", ContextMenuAction.DUPLICATE))
            menuItems.add(MenuItem("🔄 Resetar Cena", ContextMenuAction.RESET_PROPERTIES))
        }
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
        val totalH = menuItems.size * itemHeight + 6
        val renderX = if (screenX + menuWidth > screenW) screenW - menuWidth - 5 else screenX
        val renderY = if (screenY + totalH > screenH) screenH - totalH - 5 else screenY

        // Se estiver no modal de confirmação de Reset
        if (isShowingResetConfirmation) {
            val modalW = 220
            val modalH = 80
            val mx = (screenW - modalW) / 2
            val my = (screenH - modalH) / 2

            // Overlay escuro
            guiGraphics.fill(0, 0, screenW, screenH, 0x88000000.toInt())
            // Modal
            guiGraphics.fill(mx, my, mx + modalW, my + modalH, 0xFF1E1E24.toInt())
            guiGraphics.fill(mx, my, mx + modalW, my + 1, 0xFFFF9800.toInt())
            guiGraphics.fill(mx, my + modalH - 1, mx + modalW, my + modalH, 0xFFFF9800.toInt())
            guiGraphics.fill(mx, my, mx + 1, my + modalH, 0xFFFF9800.toInt())
            guiGraphics.fill(mx + modalW - 1, my, mx + modalW, my + modalH, 0xFFFF9800.toInt())

            guiGraphics.drawString(font, "⚠️ Resetar Propriedades", mx + 10, my + 10, 0xFFFF9800.toInt(), false)
            val msg = "Deseja resetar as propriedades deste bloco para os valores padrão?"
            val line1 = font.plainSubstrByWidth(msg, modalW - 20)
            guiGraphics.drawString(font, line1, mx + 10, my + 28, 0xFFE0E0E0.toInt(), false)

            // Botão Confirmar
            val btn1Hover = mouseX >= mx + 10 && mouseX <= mx + 100 && mouseY >= my + 50 && mouseY <= my + 70
            guiGraphics.fill(mx + 10, my + 50, mx + 100, my + 70, if (btn1Hover) 0xFFC62828.toInt() else 0xFF8E0000.toInt())
            guiGraphics.drawString(font, "Confirmar", mx + 26, my + 56, 0xFFFFFFFF.toInt(), false)

            // Botão Cancelar
            val btn2Hover = mouseX >= mx + 110 && mouseX <= mx + 200 && mouseY >= my + 50 && mouseY <= my + 70
            guiGraphics.fill(mx + 110, my + 50, mx + 200, my + 70, if (btn2Hover) 0xFF424242.toInt() else 0xFF212121.toInt())
            guiGraphics.drawString(font, "Cancelar", mx + 130, my + 56, 0xFFFFFFFF.toInt(), false)
            return
        }

        // Fundo do Menu de Contexto
        guiGraphics.fill(renderX, renderY, renderX + menuWidth, renderY + totalH, 0xF018181C.toInt())
        guiGraphics.fill(renderX, renderY, renderX + 1, renderY + totalH, 0xFF3D5AFE.toInt())
        guiGraphics.fill(renderX + menuWidth - 1, renderY, renderX + menuWidth, renderY + totalH, 0xFF3D5AFE.toInt())
        guiGraphics.fill(renderX, renderY, renderX + menuWidth, renderY + 1, 0xFF3D5AFE.toInt())
        guiGraphics.fill(renderX, renderY + totalH - 1, renderX + menuWidth, renderY + totalH, 0xFF3D5AFE.toInt())

        menuItems.forEachIndexed { idx, item ->
            val iy = renderY + 3 + idx * itemHeight
            val isHovered = item.enabled && mouseX >= renderX && mouseX <= renderX + menuWidth && mouseY >= iy && mouseY < iy + itemHeight
            val bgColor = if (isHovered) 0xFF3D5AFE.toInt() else 0xFF222228.toInt()
            val textColor = if (item.enabled) 0xFFFFFFFF.toInt() else 0xFF666666.toInt()

            guiGraphics.fill(renderX + 3, iy, renderX + menuWidth - 3, iy + itemHeight - 2, bgColor)
            guiGraphics.drawString(font, item.label, renderX + 8, iy + 4, textColor, false)
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, screenW: Int, screenH: Int): Boolean {
        if (isShowingResetConfirmation) {
            val modalW = 220
            val modalH = 80
            val mx = (screenW - modalW) / 2
            val my = (screenH - modalH) / 2

            // Clique no botão Confirmar Reset
            if (mouseX >= mx + 10 && mouseX <= mx + 100 && mouseY >= my + 50 && mouseY <= my + 70) {
                onAction(ContextMenuAction.RESET_PROPERTIES)
                onClose()
                return true
            }
            // Clique no botão Cancelar
            if (mouseX >= mx + 110 && mouseX <= mx + 200 && mouseY >= my + 50 && mouseY <= my + 70) {
                isShowingResetConfirmation = false
                onClose()
                return true
            }
            return true
        }

        val totalH = menuItems.size * itemHeight + 6
        val renderX = if (screenX + menuWidth > screenW) screenW - menuWidth - 5 else screenX
        val renderY = if (screenY + totalH > screenH) screenH - totalH - 5 else screenY

        if (mouseX < renderX || mouseX > renderX + menuWidth || mouseY < renderY || mouseY > renderY + totalH) {
            onClose()
            return false
        }

        val idx = ((mouseY - (renderY + 3)) / itemHeight).toInt()
        if (idx in menuItems.indices) {
            val item = menuItems[idx]
            if (item.enabled) {
                if (item.action == ContextMenuAction.RESET_PROPERTIES) {
                    isShowingResetConfirmation = true
                } else {
                    onAction(item.action)
                    onClose()
                }
                return true
            }
        }
        onClose()
        return true
    }
}
