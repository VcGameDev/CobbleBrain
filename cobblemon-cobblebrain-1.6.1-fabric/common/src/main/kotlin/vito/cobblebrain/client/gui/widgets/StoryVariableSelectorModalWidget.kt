package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.StoryProject
import vito.cobblebrain.model.StoryVariable

class StoryVariableSelectorModalWidget(
    val project: StoryProject,
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    initialSelectedKeys: Set<String> = emptySet(),
    val isMultiSelect: Boolean = false,
    val onSelect: ((StoryVariable) -> Unit)? = null,
    val onSaveSelection: ((Set<String>) -> Unit)? = null,
    val onClose: () -> Unit
) {
    private val modalWidth = if (isMultiSelect) 440.coerceAtMost(screenWidth - 20) else 340.coerceAtMost(screenWidth - 20)
    private val modalHeight = if (isMultiSelect) 310.coerceAtMost(screenHeight - 20) else 250.coerceAtMost(screenHeight - 20)
    private val modalX = maxOf(10, (screenWidth - modalWidth) / 2)
    private val modalY = maxOf(10, (screenHeight - modalHeight) / 2)

    private val selectedKeys = initialSelectedKeys.toMutableSet()
    private val searchBox: EditBox
    private var scrollOffset = 0
    private val closeButton: Button
    private var saveButton: Button? = null

    init {
        searchBox = EditBox(font, modalX + 15, modalY + 30, modalWidth - 30, 16, Component.literal("Search Variable"))
        searchBox.setMaxLength(2000)
        searchBox.setHint(Component.literal("§8🔍 Type to filter..."))
        searchBox.setEditable(true)
        searchBox.active = true

        closeButton = Button.builder(Component.literal("✖ Close")) {
            onClose()
        }.bounds(modalX + modalWidth - 75, modalY + 5, 65, 16).build()

        if (isMultiSelect) {
            saveButton = Button.builder(Component.literal("✔ Save Selection")) {
                onSaveSelection?.invoke(selectedKeys)
                onClose()
            }.bounds(modalX + modalWidth - 140, modalY + modalHeight - 25, 125, 18).build()
        }
    }

    private fun getFilteredVariables(): List<StoryVariable> {
        val query = searchBox.value.trim().lowercase()
        if (query.isEmpty()) return project.variables
        return project.variables.filter { it.id.lowercase().contains(query) || it.name.lowercase().contains(query) }
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Modal Frame
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF181820.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 24, 0xFF22222E.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0xFF00ACC1.toInt())
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF00ACC1.toInt())
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0xFF00ACC1.toInt())

        val title = if (isMultiSelect) "📦 Select Variables for Snapshot" else "📦 Select Catalog Variable"
        guiGraphics.drawString(font, title, modalX + 10, modalY + 7, 0xFF00FFCC.toInt(), false)
        closeButton.render(guiGraphics, mouseX, mouseY, partialTick)
        saveButton?.render(guiGraphics, mouseX, mouseY, partialTick)

        searchBox.render(guiGraphics, mouseX, mouseY, partialTick)

        val listX = modalX + 15
        val listY = modalY + 52
        val listW = modalWidth - 30
        val itemH = 22
        val maxVisible = if (isMultiSelect) 9 else 7
        val listH = itemH * maxVisible

        guiGraphics.fill(listX, listY, listX + listW, listY + listH, 0xFF121218.toInt())

        val filtered = getFilteredVariables()
        if (filtered.isEmpty()) {
            guiGraphics.drawString(font, "No variable registered or found.", listX + 10, listY + 30, 0xFF777788.toInt(), false)
            return
        }

        val startIndex = scrollOffset.coerceIn(0, maxOf(0, filtered.size - maxVisible))
        val endIndex = (startIndex + maxVisible).coerceAtMost(filtered.size)

        for (i in startIndex until endIndex) {
            val idx = i - startIndex
            val variable = filtered[i]
            val iy = listY + idx * itemH

            val isSelected = selectedKeys.contains(variable.id)
            val isHovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= iy && mouseY < iy + itemH
            val bg = if (isSelected) 0xFF00695C.toInt() else if (isHovered) 0xFF00ACC1.toInt() else if (idx % 2 == 0) 0xFF1A1A22.toInt() else 0xFF22222C.toInt()

            guiGraphics.fill(listX + 2, iy + 1, listX + listW - 2, iy + itemH - 1, bg)

            val scopeBadge = if (variable.scope.name == "GLOBAL") "🌐 Global" else "📁 Local"
            val typeBadge = variable.type.name
            val prefix = if (isMultiSelect) (if (isSelected) "[✔] " else "[  ] ") else ""
            val text = "$prefix${variable.id}  [$typeBadge] ($scopeBadge)"

            val textColor = if (isSelected || isHovered) 0xFFFFFFFF.toInt() else 0xFFCCCCCC.toInt()
            guiGraphics.drawString(font, font.plainSubstrByWidth(text, listW - 12), listX + 8, iy + 6, textColor, false)
        }

        // Scrollbar
        if (filtered.size > maxVisible) {
            val sbX = listX + listW - 5
            val scrollRatio = maxVisible.toFloat() / filtered.size
            val thumbH = (listH * scrollRatio).toInt().coerceAtLeast(10)
            val maxScrollable = filtered.size - maxVisible
            val thumbY = listY + (scrollOffset.toFloat() / maxScrollable * (listH - thumbH)).toInt()

            guiGraphics.fill(sbX, listY, sbX + 3, listY + listH, 0xFF2A2A36.toInt())
            guiGraphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFF00FFCC.toInt())
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (closeButton.mouseClicked(mouseX, mouseY, button)) return true
        if (saveButton?.mouseClicked(mouseX, mouseY, button) == true) return true
        val clickedSearch = searchBox.mouseClicked(mouseX, mouseY, button)
        searchBox.isFocused = clickedSearch
        if (clickedSearch) return true

        val listX = modalX + 15
        val listY = modalY + 52
        val listW = modalWidth - 30
        val itemH = 22
        val maxVisible = if (isMultiSelect) 9 else 7

        val filtered = getFilteredVariables()
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY < listY + itemH * maxVisible) {
            val idx = ((mouseY - listY) / itemH).toInt() + scrollOffset
            if (idx in filtered.indices) {
                val v = filtered[idx]
                if (isMultiSelect) {
                    if (selectedKeys.contains(v.id)) selectedKeys.remove(v.id) else selectedKeys.add(v.id)
                } else {
                    onSelect?.invoke(v)
                }
                return true
            }
        }
        return true
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        val filtered = getFilteredVariables()
        val maxVisible = if (isMultiSelect) 9 else 7
        if (filtered.size > maxVisible) {
            if (scrollY > 0) {
                scrollOffset = (scrollOffset - 1).coerceAtLeast(0)
            } else if (scrollY < 0) {
                scrollOffset = (scrollOffset + 1).coerceAtMost(filtered.size - maxVisible)
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

