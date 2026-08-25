package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.StoryProject

class AIDialogueModalWidget(
    val node: NodeData,
    val project: StoryProject,
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    val onClose: () -> Unit,
    val onDataChanged: () -> Unit
) {
    private val modalWidth = 440.coerceAtMost(screenWidth - 20)
    private val modalHeight = 270.coerceAtMost(screenHeight - 20)
    private val modalX = maxOf(10, (screenWidth - modalWidth) / 2)
    private val modalY = maxOf(10, (screenHeight - modalHeight) / 2)

    private val contentLeft = modalX + 10
    private val contentTop = modalY + 36
    private val contentRight = modalX + modalWidth - 10
    private val contentBottom = modalY + modalHeight - 10
    private val viewportH = contentBottom - contentTop

    private var vScrollOffset: Float = 0f
    private var focusedEditBox: EditBox? = null

    // Modern Button Helper Class
    class ModernButton(
        var x: Int,
        var y: Int,
        var width: Int,
        var height: Int,
        var label: String,
        var isPrimary: Boolean = false,
        val onClick: () -> Unit
    ) {
        fun render(guiGraphics: GuiGraphics, font: Font, mouseX: Int, mouseY: Int, contentTop: Int = 0, contentBottom: Int = Int.MAX_VALUE) {
            if (y + height < contentTop || y > contentBottom) return

            val isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
            val bgCol = when {
                isPrimary && isHovered -> 0xFF0284C7.toInt()
                isPrimary -> 0xFF0EA5E9.toInt()
                isHovered -> 0xFF334155.toInt()
                else -> 0xFF1E293B.toInt()
            }
            val borderCol = if (isHovered || isPrimary) 0xFF38BDF8.toInt() else 0xFF475569.toInt()
            val textCol = if (isPrimary || isHovered) 0xFFFFFFFF.toInt() else 0xFFCBD5E1.toInt()

            guiGraphics.fill(x, y, x + width, y + height, bgCol)
            guiGraphics.fill(x, y, x + width, y + 1, borderCol)
            guiGraphics.fill(x, y, x + 1, y + height, borderCol)
            guiGraphics.fill(x + width - 1, y, x + width, y + height, borderCol)
            guiGraphics.fill(x, y + height - 1, x + width, y + height, borderCol)

            val tw = font.width(label)
            val tx = x + (width - tw) / 2
            val ty = y + (height - 8) / 2
            guiGraphics.drawString(font, label, tx, ty, textCol, false)
        }

        fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, contentTop: Int = 0, contentBottom: Int = Int.MAX_VALUE): Boolean {
            if (y + height < contentTop || y > contentBottom) return false
            if (button == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                onClick()
                return true
            }
            return false
        }
    }

    // Compact Toggle Row with Pill Badge
    class CompactToggleRow(
        val title: String,
        var isEnabled: Boolean,
        val onClick: () -> Unit
    ) {
        fun render(guiGraphics: GuiGraphics, font: Font, x: Int, y: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, contentTop: Int, contentBottom: Int) {
            if (y + height < contentTop || y > contentBottom) return

            val isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
            val bgCol = if (isHovered) 0xAA1E293B.toInt() else 0x77111827
            val borderCol = if (isHovered) 0xFF38BDF8.toInt() else 0x334B5563

            guiGraphics.fill(x, y, x + width, y + height, bgCol)
            guiGraphics.fill(x, y, x + width, y + 1, borderCol)
            guiGraphics.fill(x, y, x + 1, y + height, borderCol)
            guiGraphics.fill(x + width - 1, y, x + width, y + height, borderCol)
            guiGraphics.fill(x, y + height - 1, x + width, y + height, borderCol)

            guiGraphics.drawString(font, title, x + 8, y + (height - 8) / 2, 0xFFCBD5E1.toInt(), false)

            val pillText = if (isEnabled) "ON" else "OFF"
            val pillTextCol = if (isEnabled) 0xFF38BDF8.toInt() else 0xFF6B7280.toInt()
            val pillBgCol = if (isEnabled) 0x440EA5E9 else 0x44374151
            val pillBorderCol = if (isEnabled) 0xFF38BDF8.toInt() else 0xFF4B5563.toInt()
            val pillW = 34
            val pillH = 14
            val pillX = x + width - pillW - 6
            val pillY = y + (height - pillH) / 2

            guiGraphics.fill(pillX, pillY, pillX + pillW, pillY + pillH, pillBgCol)
            guiGraphics.fill(pillX, pillY, pillX + pillW, pillY + 1, pillBorderCol)
            guiGraphics.fill(pillX, pillY, pillX + 1, pillY + pillH, pillBorderCol)
            guiGraphics.fill(pillX + pillW - 1, pillY, pillX + pillW, pillY + pillH, pillBorderCol)
            guiGraphics.fill(pillX, pillY + pillH - 1, pillX + pillW, pillY + pillH, pillBorderCol)

            val tw = font.width(pillText)
            guiGraphics.drawString(font, pillText, pillX + (pillW - tw) / 2, pillY + (pillH - 8) / 2, pillTextCol, false)
        }

        fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, x: Int, y: Int, width: Int, height: Int, contentTop: Int, contentBottom: Int): Boolean {
            if (y + height < contentTop || y > contentBottom) return false
            if (button == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                isEnabled = !isEnabled
                onClick()
                return true
            }
            return false
        }
    }

    private val closeButton: ModernButton
    private val fallbackEdit: EditBox
    private val displayModeBtn: ModernButton
    private val bubbleTicksEdit: EditBox
    private val actionsToggle: CompactToggleRow
    private val saveVarEdit: EditBox
    private val freezeToggle: CompactToggleRow

    init {
        val inputX = modalX + 140
        val inputW = modalWidth - 165

        closeButton = ModernButton(modalX + modalWidth - 28, modalY + 6, 20, 16, "✖") {
            onClose()
        }

        fallbackEdit = EditBox(font, 0, 0, inputW, 16, Component.literal("Fallback Text"))
        fallbackEdit.setMaxLength(2000)
        fallbackEdit.setHint(Component.literal("§8Fallback text for offline / timeout"))
        fallbackEdit.value = node.params["fallbackText"] ?: "..."
        fallbackEdit.setEditable(true)
        fallbackEdit.active = true
        fallbackEdit.setResponder { node.params["fallbackText"] = it; onDataChanged() }

        val curDisplay = node.params["displayMode"] ?: "HUD_AND_BUBBLE"
        displayModeBtn = ModernButton(0, 0, inputW, 18, "Mode: $curDisplay") {
            val modes = listOf("HUD_AND_BUBBLE", "HUD_ONLY", "BUBBLE_ONLY")
            val idx = (modes.indexOf(node.params["displayMode"] ?: "HUD_AND_BUBBLE") + 1) % modes.size
            val next = modes[idx]
            node.params["displayMode"] = next
            displayModeBtn.label = "Mode: $next"
            onDataChanged()
        }

        bubbleTicksEdit = EditBox(font, 0, 0, inputW, 16, Component.literal("Bubble Ticks"))
        bubbleTicksEdit.setMaxLength(2000)
        bubbleTicksEdit.setHint(Component.literal("§8Duration in ticks (100 ticks = 5s)"))
        bubbleTicksEdit.value = node.params["bubbleDurationTicks"] ?: "100"
        bubbleTicksEdit.setEditable(true)
        bubbleTicksEdit.active = true
        bubbleTicksEdit.setResponder { node.params["bubbleDurationTicks"] = it; onDataChanged() }

        val actOn = node.params["allowActions"] != "false"
        actionsToggle = CompactToggleRow("⚡ Allow AI Companion Actions", actOn) {
            val next = !actionsToggle.isEnabled
            node.params["allowActions"] = if (next) "true" else "false"
            onDataChanged()
        }

        saveVarEdit = EditBox(font, 0, 0, inputW, 16, Component.literal("Save Output Variable"))
        saveVarEdit.setMaxLength(2000)
        saveVarEdit.setHint(Component.literal("§8Variable key (e.g. ai_reply)"))
        saveVarEdit.value = node.params["saveOutputVariable"] ?: ""
        saveVarEdit.setEditable(true)
        saveVarEdit.active = true
        saveVarEdit.setResponder { node.params["saveOutputVariable"] = it; onDataChanged() }

        val freezeOn = node.params["freezePlayer"] != "false"
        freezeToggle = CompactToggleRow("🛑 Freeze Player Movement", freezeOn) {
            val next = !freezeToggle.isEnabled
            node.params["freezePlayer"] = if (next) "true" else "false"
            onDataChanged()
        }
    }

    private fun getTotalContentHeight(): Int {
        return 210
    }

    private fun setFocused(target: EditBox?) {
        focusedEditBox?.isFocused = false
        focusedEditBox = target
        focusedEditBox?.isFocused = true
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Modern Dark Card Outer Container
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xEE0F172A.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 1, 0xFF38BDF8.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0x33FFFFFF)
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0x33FFFFFF)
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0x33FFFFFF)

        // Header Title
        guiGraphics.drawString(font, "🤖 AI Dialogue Options", modalX + 12, modalY + 10, 0xFF38BDF8.toInt(), true)
        closeButton.render(guiGraphics, font, mouseX, mouseY)

        // Viewport Scissor Clipping
        guiGraphics.fill(contentLeft, contentTop, contentRight, contentBottom, 0xCC1E293B.toInt())
        guiGraphics.enableScissor(contentLeft, contentTop, contentRight, contentBottom)

        val scrollY = vScrollOffset.toInt()
        val virtualY = contentTop + 8 - scrollY
        val inputX = modalX + 140
        val toggleW = modalWidth - 40

        var curY = virtualY
        guiGraphics.drawString(font, "🛡️ Fallback Response", contentLeft + 10, curY, 0xFF38BDF8.toInt(), false)

        curY += 14
        guiGraphics.drawString(font, "Fallback Text:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
        fallbackEdit.setX(inputX)
        fallbackEdit.setY(curY)
        if (curY + 16 >= contentTop && curY <= contentBottom) fallbackEdit.render(guiGraphics, mouseX, mouseY, partialTick)

        curY += 28
        guiGraphics.drawString(font, "🎨 Visual & Interface Rules", contentLeft + 10, curY, 0xFF38BDF8.toInt(), false)

        curY += 14
        guiGraphics.drawString(font, "Display Mode:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
        displayModeBtn.x = inputX
        displayModeBtn.y = curY
        displayModeBtn.render(guiGraphics, font, mouseX, mouseY, contentTop, contentBottom)

        curY += 24
        guiGraphics.drawString(font, "Bubble Duration Ticks:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
        bubbleTicksEdit.setX(inputX)
        bubbleTicksEdit.setY(curY)
        if (curY + 16 >= contentTop && curY <= contentBottom) bubbleTicksEdit.render(guiGraphics, mouseX, mouseY, partialTick)

        curY += 24
        actionsToggle.render(guiGraphics, font, contentLeft + 10, curY, toggleW, 20, mouseX, mouseY, contentTop, contentBottom)

        curY += 24
        guiGraphics.drawString(font, "Save Output Var:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
        saveVarEdit.setX(inputX)
        saveVarEdit.setY(curY)
        if (curY + 16 >= contentTop && curY <= contentBottom) saveVarEdit.render(guiGraphics, mouseX, mouseY, partialTick)

        curY += 24
        freezeToggle.render(guiGraphics, font, contentLeft + 10, curY, toggleW, 20, mouseX, mouseY, contentTop, contentBottom)

        guiGraphics.disableScissor()

        // Sleek Scrollbar
        val totalH = getTotalContentHeight()
        val maxScroll = (totalH - viewportH).coerceAtLeast(0)
        if (maxScroll > 0) {
            val sbX = contentRight - 5
            val scrollRatio = viewportH.toFloat() / totalH
            val thumbH = (viewportH * scrollRatio).toInt().coerceAtLeast(15)
            val thumbY = contentTop + ((vScrollOffset / maxScroll) * (viewportH - thumbH)).toInt()

            guiGraphics.fill(sbX, contentTop, sbX + 3, contentBottom, 0xFF0F172A.toInt())
            guiGraphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFF38BDF8.toInt())
        }
    }

    private fun checkEditClick(editBox: EditBox, mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (editBox.y + editBox.height >= contentTop && editBox.y <= contentBottom) {
            if (editBox.mouseClicked(mouseX, mouseY, button)) {
                setFocused(editBox)
                return true
            }
        }
        return false
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (closeButton.mouseClicked(mouseX, mouseY, button)) return true

        if (mouseX >= contentLeft && mouseX <= contentRight && mouseY >= contentTop && mouseY <= contentBottom) {
            val scrollY = vScrollOffset.toInt()
            val virtualY = contentTop + 8 - scrollY
            val toggleW = modalWidth - 40

            var curY = virtualY + 14
            if (checkEditClick(fallbackEdit, mouseX, mouseY, button)) return true

            curY += 42
            if (displayModeBtn.mouseClicked(mouseX, mouseY, button, contentTop, contentBottom)) return true

            curY += 24
            if (checkEditClick(bubbleTicksEdit, mouseX, mouseY, button)) return true

            curY += 24
            if (actionsToggle.mouseClicked(mouseX, mouseY, button, contentLeft + 10, curY, toggleW, 20, contentTop, contentBottom)) return true

            curY += 24
            if (checkEditClick(saveVarEdit, mouseX, mouseY, button)) return true

            curY += 24
            if (freezeToggle.mouseClicked(mouseX, mouseY, button, contentLeft + 10, curY, toggleW, 20, contentTop, contentBottom)) return true
        }

        if (mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= modalY && mouseY <= modalY + modalHeight) {
            setFocused(null)
            return true
        }
        return false
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (focusedEditBox != null && focusedEditBox!!.charTyped(codePoint, modifiers)) return true
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 256) {
            onClose()
            return true
        }
        if (focusedEditBox != null && focusedEditBox!!.keyPressed(keyCode, scanCode, modifiers)) return true
        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        if (mouseX >= contentLeft && mouseX <= contentRight && mouseY >= contentTop && mouseY <= contentBottom) {
            val totalH = getTotalContentHeight()
            val maxScroll = (totalH - viewportH).coerceAtLeast(0).toFloat()
            if (maxScroll > 0f) {
                vScrollOffset = (vScrollOffset - scrollY.toFloat() * 16f).coerceIn(0f, maxScroll)
                return true
            }
        }
        return mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= modalY && mouseY <= modalY + modalHeight
    }
}
