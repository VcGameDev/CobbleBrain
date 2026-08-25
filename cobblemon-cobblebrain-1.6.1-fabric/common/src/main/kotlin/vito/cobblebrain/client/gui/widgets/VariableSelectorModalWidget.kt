package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.StoryProject
import vito.cobblebrain.model.StoryVariable

class VariableSelectorModalWidget(
    val project: StoryProject,
    initialSelectedKeys: Set<String>,
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    val onSaveSelection: (Set<String>) -> Unit,
    val onClose: () -> Unit
) {
    private val modalWidth = 440.coerceAtMost(screenWidth - 20)
    private val modalHeight = 310.coerceAtMost(screenHeight - 20)
    private val modalX = maxOf(10, (screenWidth - modalWidth) / 2)
    private val modalY = maxOf(10, (screenHeight - modalHeight) / 2)

    private val selectedKeys = initialSelectedKeys.toMutableSet()
    private val searchBox: EditBox
    private var scrollOffset: Int = 0

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
        fun render(guiGraphics: GuiGraphics, font: Font, mouseX: Int, mouseY: Int) {
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

        fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (button == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                onClick()
                return true
            }
            return false
        }
    }

    private val closeButton: ModernButton
    private val applyButton: ModernButton
    private val selectAllButton: ModernButton
    private val deselectAllButton: ModernButton

    init {
        searchBox = EditBox(font, modalX + 15, modalY + 30, 200, 16, Component.literal("Search Variable"))
        searchBox.setMaxLength(2000)
        searchBox.setHint(Component.literal("§8🔍 Filter variables..."))
        searchBox.setEditable(true)
        searchBox.active = true

        selectAllButton = ModernButton(modalX + 225, modalY + 30, 90, 16, "Select All") {
            val all = getAllVariables().map { it.key }
            selectedKeys.addAll(all)
        }

        deselectAllButton = ModernButton(modalX + 320, modalY + 30, 105, 16, "Deselect All") {
            selectedKeys.clear()
        }

        closeButton = ModernButton(modalX + modalWidth - 26, modalY + 6, 20, 16, "✖") {
            onClose()
        }

        applyButton = ModernButton(modalX + modalWidth - 145, modalY + modalHeight - 26, 130, 20, "💾 Apply Selection", isPrimary = true) {
            onSaveSelection(selectedKeys)
            onClose()
        }
    }

    data class VariableEntry(
        val key: String,
        val typeStr: String,
        val groupName: String
    )

    private fun getAllVariables(): List<VariableEntry> {
        val result = mutableListOf<VariableEntry>()

        // 1. Global Story Variables
        project.variables.forEach { v ->
            result.add(VariableEntry(v.id, v.type.name, "🌐 Global Variables"))
        }

        // 2. Scene Specific Variables
        project.scenes.forEach { scene ->
            scene.nodes.forEach { n ->
                n.params.keys.filter { it.startsWith("var_") || it.endsWith("_var") }.forEach { k ->
                    if (result.none { it.key == k }) {
                        result.add(VariableEntry(k, "LOCAL", "🎬 Scene: ${scene.title}"))
                    }
                }
            }
        }

        val query = searchBox.value.trim().lowercase()
        if (query.isBlank()) return result
        return result.filter { it.key.lowercase().contains(query) || it.groupName.lowercase().contains(query) }
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Outer Container Card
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xEE0F172A.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 1, 0xFF38BDF8.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0x33FFFFFF.toInt())
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0x33FFFFFF.toInt())
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0x33FFFFFF.toInt())

        // Header Title
        guiGraphics.drawString(font, "📋 Hierarchical Variable Selection", modalX + 12, modalY + 9, 0xFF38BDF8.toInt(), true)

        closeButton.render(guiGraphics, font, mouseX, mouseY)

        // Top Filter Bar
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick)
        selectAllButton.render(guiGraphics, font, mouseX, mouseY)
        deselectAllButton.render(guiGraphics, font, mouseX, mouseY)

        // Variable List Card Container
        val listX = modalX + 15
        val listY = modalY + 54
        val listW = modalWidth - 30
        val itemH = 20
        val maxVisible = 10
        val listH = itemH * maxVisible

        guiGraphics.fill(listX, listY, listX + listW, listY + listH, 0xCC1E293B.toInt())

        val variables = getAllVariables()
        if (variables.isEmpty()) {
            guiGraphics.drawString(font, "No matching story variables found.", listX + 15, listY + 80, 0xFF94A3B8.toInt(), false)
        } else {
            val startIndex = scrollOffset.coerceIn(0, maxOf(0, variables.size - maxVisible))
            val endIndex = (startIndex + maxVisible).coerceAtMost(variables.size)

            for (i in startIndex until endIndex) {
                val idx = i - startIndex
                val entry = variables[i]
                val iy = listY + idx * itemH
                val isChecked = selectedKeys.contains(entry.key)

                val isHovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= iy && mouseY < iy + itemH
                val bg = if (isHovered) 0xFF334155.toInt() else if (idx % 2 == 0) 0xAA1E293B.toInt() else 0xAA0F172A.toInt()

                guiGraphics.fill(listX + 2, iy + 1, listX + listW - 2, iy + itemH - 1, bg)

                // Checkbox Box
                val cbBox = if (isChecked) "☑" else "☐"
                val cbCol = if (isChecked) 0xFF38BDF8.toInt() else 0xFF64748B.toInt()
                guiGraphics.drawString(font, cbBox, listX + 8, iy + 6, cbCol, false)

                // Variable Key
                val textCol = if (isChecked) 0xFFFFFFFF.toInt() else 0xFFCBD5E1.toInt()
                guiGraphics.drawString(font, font.plainSubstrByWidth(entry.key, listW - 140), listX + 25, iy + 6, textCol, false)

                // Type Badge & Group Origin
                val badgeText = "[${entry.typeStr}] ${entry.groupName}"
                guiGraphics.drawString(font, font.plainSubstrByWidth(badgeText, 110), listX + listW - 120, iy + 6, 0xFF94A3B8.toInt(), false)
            }

            // Scrollbar
            if (variables.size > maxVisible) {
                val sbX = listX + listW - 4
                val scrollRatio = maxVisible.toFloat() / variables.size
                val thumbH = (listH * scrollRatio).toInt().coerceAtLeast(10)
                val maxScrollable = variables.size - maxVisible
                val thumbY = listY + (scrollOffset.toFloat() / maxScrollable * (listH - thumbH)).toInt()

                guiGraphics.fill(sbX, listY, sbX + 3, listY + listH, 0xFF0F172A.toInt())
                guiGraphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFF38BDF8.toInt())
            }
        }

        // Footer Bar
        val footerText = "Selected: ${selectedKeys.size} of ${variables.size} variables"
        guiGraphics.drawString(font, footerText, modalX + 15, modalY + modalHeight - 19, 0xFF38BDF8.toInt(), false)

        applyButton.render(guiGraphics, font, mouseX, mouseY)
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (closeButton.mouseClicked(mouseX, mouseY, button)) return true
        if (selectAllButton.mouseClicked(mouseX, mouseY, button)) return true
        if (deselectAllButton.mouseClicked(mouseX, mouseY, button)) return true
        if (applyButton.mouseClicked(mouseX, mouseY, button)) return true

        val clickedSearch = searchBox.mouseClicked(mouseX, mouseY, button)
        searchBox.isFocused = clickedSearch
        if (clickedSearch) return true

        val listX = modalX + 15
        val listY = modalY + 54
        val listW = modalWidth - 30
        val itemH = 20
        val maxVisible = 10

        val variables = getAllVariables()
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY < listY + itemH * maxVisible) {
            val idx = ((mouseY - listY) / itemH).toInt() + scrollOffset
            if (idx in variables.indices) {
                val key = variables[idx].key
                if (selectedKeys.contains(key)) {
                    selectedKeys.remove(key)
                } else {
                    selectedKeys.add(key)
                }
                return true
            }
        }
        return mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= modalY && mouseY <= modalY + modalHeight
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        val variables = getAllVariables()
        val maxVisible = 10
        if (variables.size > maxVisible) {
            if (scrollY > 0) {
                scrollOffset = (scrollOffset - 1).coerceAtLeast(0)
            } else if (scrollY < 0) {
                scrollOffset = (scrollOffset + 1).coerceAtMost(variables.size - maxVisible)
            }
            return true
        }
        return false
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        return searchBox.charTyped(codePoint, modifiers)
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searchBox.keyPressed(keyCode, scanCode, modifiers)) return true
        if (keyCode == 256) {
            onClose()
            return true
        }
        return false
    }
}
