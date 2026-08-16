package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.ActionCategory
import vito.cobblebrain.model.ActionRegistry
import vito.cobblebrain.model.TriggerCategory
import vito.cobblebrain.model.TriggerRegistry

class ActionTriggerPickerModalWidget(
    val isAction: Boolean,
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    val currentSelectedId: String,
    val onSelect: (String) -> Unit,
    val onClose: () -> Unit
) {
    private val modalWidth = 420.coerceAtMost(screenWidth - 20)
    private val modalHeight = 280.coerceAtMost(screenHeight - 20)
    private val modalX = maxOf(10, (screenWidth - modalWidth) / 2)
    private val modalY = maxOf(10, (screenHeight - modalHeight) / 2)

    private val searchBox: EditBox
    private val closeButton: Button

    private var selectedCategoryIndex: Int = 0 // 0 = All
    private var scrollOffset: Double = 0.0
    private var categoryScrollOffset: Double = 0.0

    init {
        searchBox = EditBox(font, modalX + 15, modalY + 30, modalWidth - 30, 16, Component.literal("Search"))
        searchBox.setHint(Component.literal("🔍 Type to search..."))
        searchBox.setEditable(true)
        searchBox.active = true
        searchBox.setResponder {
            scrollOffset = 0.0
        }

        closeButton = Button.builder(Component.literal("✖ Close")) {
            onClose()
        }.bounds(modalX + modalWidth - 75, modalY + 5, 65, 16).build()
    }

    private data class ItemCard(
        val id: String,
        val name: String,
        val icon: String,
        val description: String,
        val categoryName: String
    )

    private fun getCategories(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        list.add(Pair("ALL", "📁 All"))
        if (isAction) {
            ActionCategory.entries.forEach { cat ->
                list.add(Pair(cat.name, cat.displayName))
            }
        } else {
            TriggerCategory.entries.forEach { cat ->
                list.add(Pair(cat.name, cat.displayName))
            }
        }
        return list
    }

    private fun getFilteredItems(): List<ItemCard> {
        val query = searchBox.value.trim().lowercase()
        val categories = getCategories()
        val selectedCatKey = categories.getOrNull(selectedCategoryIndex)?.first ?: "ALL"

        val rawItems = if (isAction) {
            ActionRegistry.actions.map {
                ItemCard(it.id, it.name, it.icon, it.description, it.category.name)
            }
        } else {
            TriggerRegistry.triggers.map {
                ItemCard(it.id, it.name, it.icon, it.description, it.category.name)
            }
        }

        return rawItems.filter { item ->
            val matchesCat = (selectedCatKey == "ALL" || item.categoryName == selectedCatKey)
            val matchesQuery = query.isEmpty() || item.name.lowercase().contains(query) || item.description.lowercase().contains(query) || item.id.lowercase().contains(query)
            matchesCat && matchesQuery
        }
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Modal Frame
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF14141A.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 24, 0xFF22222E.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())

        val title = if (isAction) "🛠️ Select Action Type" else "⚡ Select Trigger Type"
        guiGraphics.drawString(font, title, modalX + 10, modalY + 7, 0xFF00FFCC.toInt(), false)

        closeButton.render(guiGraphics, mouseX, mouseY, partialTick)
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick)

        val contentY = modalY + 52
        val contentH = modalHeight - 60

        // 1. LEFT PANEL: Categories with Scissored Viewport and Vertical Scrolling
        val catW = 120
        val catX = modalX + 12
        guiGraphics.fill(catX, contentY, catX + catW, contentY + contentH, 0xFF0D0D12.toInt())
        guiGraphics.fill(catX + catW, contentY, catX + catW + 1, contentY + contentH, 0xFF282836.toInt())

        val categories = getCategories()
        val totalCatH = categories.size * 22 + 4

        guiGraphics.enableScissor(catX, contentY, catX + catW, contentY + contentH)

        categories.forEachIndexed { idx, (_, label) ->
            val cy = (contentY + 4 + idx * 22 - categoryScrollOffset).toInt()
            if (cy + 20 >= contentY && cy <= contentY + contentH) {
                val isSelected = idx == selectedCategoryIndex
                val isHovered = mouseX >= catX && mouseX <= catX + catW - 4 && mouseY >= cy && mouseY < cy + 20

                val bg = when {
                    isSelected -> 0xFF3D5AFE.toInt()
                    isHovered -> 0xFF222232.toInt()
                    else -> 0x00000000
                }
                if (bg != 0) guiGraphics.fill(catX + 2, cy, catX + catW - 4, cy + 20, bg)

                val txtColor = if (isSelected) 0xFF00FFCC.toInt() else if (isHovered) 0xFFFFFFFF.toInt() else 0xFFAAAAAA.toInt()
                val truncatedLabel = font.plainSubstrByWidth(label, catW - 12)
                guiGraphics.drawString(font, truncatedLabel, catX + 6, cy + 6, txtColor, false)
            }
        }

        guiGraphics.disableScissor()

        // Categories scroll bar
        val maxCatScroll = maxOf(0.0, totalCatH.toDouble() - contentH)
        if (maxCatScroll > 0) {
            val sbX = catX + catW - 3
            val thumbH = ((contentH.toDouble() / totalCatH) * contentH).toInt().coerceIn(12, contentH)
            val thumbY = contentY + ((categoryScrollOffset / maxCatScroll) * (contentH - thumbH)).toInt()

            guiGraphics.fill(sbX, contentY, sbX + 2, contentY + contentH, 0xFF1C1C24.toInt())
            guiGraphics.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, 0xFF3D5AFE.toInt())
        }

        // 2. RIGHT PANEL: Item Cards with Scissored Viewport and Vertical Scrolling
        val gridX = catX + catW + 8
        val gridW = modalWidth - (catW + 32)
        val gridH = contentH

        guiGraphics.fill(gridX, contentY, gridX + gridW, contentY + gridH, 0xFF0D0D12.toInt())

        val items = getFilteredItems()
        val cardH = 40
        val totalH = items.size * (cardH + 4) + 4

        guiGraphics.enableScissor(gridX, contentY, gridX + gridW, contentY + gridH)

        items.forEachIndexed { idx, item ->
            val cy = (contentY + 4 + idx * (cardH + 4) - scrollOffset).toInt()
            if (cy + cardH >= contentY && cy <= contentY + gridH) {
                val isSelected = item.id == currentSelectedId
                val isHovered = mouseX >= gridX + 4 && mouseX <= gridX + gridW - 6 && mouseY >= cy && mouseY < cy + cardH

                val bg = when {
                    isSelected -> 0xFF2A2A40.toInt()
                    isHovered -> 0xFF20202E.toInt()
                    else -> 0xFF161620.toInt()
                }
                guiGraphics.fill(gridX + 4, cy, gridX + gridW - 6, cy + cardH, bg)

                val border = if (isSelected) 0xFF00FFCC.toInt() else if (isHovered) 0xFF3D5AFE.toInt() else 0xFF262636.toInt()
                guiGraphics.fill(gridX + 4, cy, gridX + 5, cy + cardH, border)
                guiGraphics.fill(gridX + gridW - 7, cy, gridX + gridW - 6, cy + cardH, border)
                guiGraphics.fill(gridX + 4, cy, gridX + gridW - 6, cy + 1, border)
                guiGraphics.fill(gridX + 4, cy + cardH - 1, gridX + gridW - 6, cy + cardH, border)

                // Icon + Title
                val titleStr = "${item.icon} ${item.name}"
                guiGraphics.drawString(font, titleStr, gridX + 10, cy + 6, if (isSelected) 0xFF00FFCC.toInt() else 0xFFFFFFFF.toInt(), false)

                // Short description
                val descStr = font.plainSubstrByWidth(item.description, gridW - 24)
                guiGraphics.drawString(font, descStr, gridX + 10, cy + 22, 0xFFA0A0A0.toInt(), false)
            }
        }

        guiGraphics.disableScissor()

        // Cards scroll bar
        val maxScroll = maxOf(0.0, totalH.toDouble() - gridH)
        if (maxScroll > 0) {
            val sbX = gridX + gridW - 4
            val thumbH = ((gridH.toDouble() / totalH) * gridH).toInt().coerceIn(12, gridH)
            val thumbY = contentY + ((scrollOffset / maxScroll) * (gridH - thumbH)).toInt()

            guiGraphics.fill(sbX, contentY, sbX + 2, contentY + gridH, 0xFF1C1C24.toInt())
            guiGraphics.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, 0xFF00FFCC.toInt())
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (closeButton.mouseClicked(mouseX, mouseY, button)) return true
        val clickedSearch = searchBox.mouseClicked(mouseX, mouseY, button)
        searchBox.isFocused = clickedSearch
        if (clickedSearch) return true

        val contentY = modalY + 52
        val contentH = modalHeight - 60
        val catW = 120
        val catX = modalX + 12

        // Category list click with scroll offset
        val categories = getCategories()
        if (mouseX >= catX && mouseX <= catX + catW && mouseY >= contentY && mouseY <= contentY + contentH) {
            val relativeY = mouseY - contentY - 4 + categoryScrollOffset
            val idx = (relativeY / 22).toInt()
            if (idx in categories.indices) {
                selectedCategoryIndex = idx
                scrollOffset = 0.0
                return true
            }
        }

        // Item cards click with scroll offset
        val gridX = catX + catW + 8
        val gridW = modalWidth - (catW + 32)
        val gridH = contentH
        val cardH = 40

        if (mouseX >= gridX && mouseX <= gridX + gridW && mouseY >= contentY && mouseY <= contentY + gridH) {
            val items = getFilteredItems()
            val relativeY = mouseY - contentY - 4 + scrollOffset
            val idx = (relativeY / (cardH + 4)).toInt()
            if (idx in items.indices) {
                val item = items[idx]
                onSelect(item.id)
                onClose()
                return true
            }
        }

        return true
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        val contentY = modalY + 52
        val contentH = modalHeight - 60
        val catW = 120
        val catX = modalX + 12
        val gridX = catX + catW + 8
        val gridW = modalWidth - (catW + 32)
        val gridH = contentH

        // Rolagem no Painel de Categorias
        if (mouseX >= catX && mouseX <= catX + catW && mouseY >= contentY && mouseY <= contentY + contentH) {
            val categories = getCategories()
            val totalCatH = categories.size * 22 + 4
            val maxCatScroll = maxOf(0.0, totalCatH.toDouble() - contentH)
            if (maxCatScroll > 0) {
                if (scrollY > 0) {
                    categoryScrollOffset = (categoryScrollOffset - 22.0).coerceAtLeast(0.0)
                } else if (scrollY < 0) {
                    categoryScrollOffset = (categoryScrollOffset + 22.0).coerceAtMost(maxCatScroll)
                }
                return true
            }
        }

        // Rolagem no Painel de Cards de Ações/Gatilhos
        if (mouseX >= gridX && mouseX <= gridX + gridW && mouseY >= contentY && mouseY <= contentY + gridH) {
            val items = getFilteredItems()
            val cardH = 40
            val totalH = items.size * (cardH + 4) + 4
            val maxScroll = maxOf(0.0, totalH.toDouble() - gridH)
            if (maxScroll > 0) {
                if (scrollY > 0) {
                    scrollOffset = (scrollOffset - 26.0).coerceAtLeast(0.0)
                } else if (scrollY < 0) {
                    scrollOffset = (scrollOffset + 26.0).coerceAtMost(maxScroll)
                }
                return true
            }
        }
        return false
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (searchBox.isFocused) {
            return searchBox.charTyped(codePoint, modifiers)
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searchBox.isFocused && searchBox.keyPressed(keyCode, scanCode, modifiers)) return true
        if (keyCode == 256) { // ESC
            onClose()
            return true
        }
        return false
    }
}
