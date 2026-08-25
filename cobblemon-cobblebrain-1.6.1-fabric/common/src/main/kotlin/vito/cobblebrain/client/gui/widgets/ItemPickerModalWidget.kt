package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class ItemPickerModalWidget(
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    var selectedItemId: String = "",
    val onSelect: (String) -> Unit,
    val onClose: () -> Unit
) {
    private val modalWidth = 480.coerceAtMost(screenWidth - 20)
    private val modalHeight = 290.coerceAtMost(screenHeight - 20)
    private val modalX = maxOf(10, (screenWidth - modalWidth) / 2)
    private val modalY = maxOf(10, (screenHeight - modalHeight) / 2)

    private val searchBox: EditBox
    private val customIdBox: EditBox

    private val selectBtn: Button
    private val clearBtn: Button
    private val closeBtn: Button
    private val applyCustomBtn: Button

    private var selectedCategoryIndex: Int = 0
    private var scrollOffset: Double = 0.0
    private var categoryScrollOffset: Double = 0.0
    private var focusedEditBox: EditBox? = null

    private var isDraggingScrollbar: Boolean = false
    private var isDraggingCategoryScrollbar: Boolean = false

    private val allItemIds: List<String>

    init {
        // Retrieve all registered items from BuiltInRegistries.ITEM
        val registryList = try {
            BuiltInRegistries.ITEM.keySet().map { it.toString() }
        } catch (_: Exception) {
            emptyList()
        }

        val fallbackList = listOf(
            "minecraft:netherite_sword", "minecraft:diamond_sword", "minecraft:iron_sword", "minecraft:golden_sword", "minecraft:stone_sword", "minecraft:wooden_sword",
            "minecraft:netherite_axe", "minecraft:diamond_axe", "minecraft:iron_axe", "minecraft:bow", "minecraft:crossbow", "minecraft:trident", "minecraft:shield",
            "minecraft:netherite_helmet", "minecraft:diamond_helmet", "minecraft:iron_helmet", "minecraft:golden_helmet", "minecraft:chainmail_helmet", "minecraft:leather_helmet",
            "minecraft:netherite_chestplate", "minecraft:diamond_chestplate", "minecraft:iron_chestplate", "minecraft:golden_chestplate", "minecraft:chainmail_chestplate", "minecraft:leather_chestplate", "minecraft:elytra",
            "minecraft:netherite_leggings", "minecraft:diamond_leggings", "minecraft:iron_leggings", "minecraft:golden_leggings", "minecraft:chainmail_leggings", "minecraft:leather_leggings",
            "minecraft:netherite_boots", "minecraft:diamond_boots", "minecraft:iron_boots", "minecraft:golden_boots", "minecraft:chainmail_boots", "minecraft:leather_boots",
            "minecraft:totem_of_undying", "minecraft:golden_apple", "minecraft:enchanted_golden_apple", "minecraft:ender_pearl", "minecraft:potion",
            "cobblemon:poke_ball", "cobblemon:great_ball", "cobblemon:ultra_ball", "cobblemon:master_ball"
        )

        allItemIds = (registryList + fallbackList).distinct().filter { it != "minecraft:air" }.sorted()

        searchBox = EditBox(font, modalX + 15, modalY + 28, modalWidth - 30, 16, Component.literal("Search Items"))
        searchBox.setMaxLength(2000)
        searchBox.setHint(Component.literal("§8🔍 Type to search items (e.g. sword, helmet, poke_ball)..."))
        searchBox.setEditable(true)
        searchBox.active = true
        searchBox.setResponder { scrollOffset = 0.0 }
        focusedEditBox = searchBox

        customIdBox = EditBox(font, modalX + 150, modalY + 80, modalWidth - 165, 18, Component.literal("Custom Item ID"))
        customIdBox.setMaxLength(2000)
        customIdBox.value = selectedItemId
        customIdBox.setHint(Component.literal("§8e.g. my_mod:custom_sword"))
        customIdBox.setEditable(true)
        customIdBox.active = true

        applyCustomBtn = Button.builder(Component.literal("✔ Confirm Custom ID")) {
            val raw = customIdBox.value.trim()
            if (raw.isNotBlank()) {
                val formatted = if (!raw.contains(":")) "minecraft:$raw" else raw
                selectedItemId = formatted
                onSelect(formatted)
                onClose()
            }
        }.bounds(modalX + 150, modalY + 160, modalWidth - 165, 20).build()

        selectBtn = Button.builder(Component.literal("✔ Select Item")) {
            if (selectedItemId.isNotBlank()) {
                onSelect(selectedItemId)
                onClose()
            }
        }.bounds(modalX + modalWidth - 115, modalY + modalHeight - 24, 100, 18).build()

        clearBtn = Button.builder(Component.literal("🗑️ Clear / Unequip")) {
            selectedItemId = ""
            onSelect("")
            onClose()
        }.bounds(modalX + modalWidth - 235, modalY + modalHeight - 24, 115, 18).build()

        closeBtn = Button.builder(Component.literal("✖ Cancel")) {
            onClose()
        }.bounds(modalX + 15, modalY + modalHeight - 24, 75, 18).build()
    }

    private fun getCategories(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        list.add(Pair("ALL", "📦 All Items (${allItemIds.size})"))
        list.add(Pair("WEAPONS_TOOLS", "🗡️ Weapons & Tools"))
        list.add(Pair("ARMOR", "🛡️ Armor & Equipment"))

        val namespaces = allItemIds.map { if (it.contains(":")) it.substringBefore(":") else "minecraft" }.distinct()
        namespaces.forEach { ns ->
            val count = allItemIds.count { it.startsWith("$ns:") }
            val label = when (ns.lowercase()) {
                "minecraft" -> "⛏️ Minecraft ($count)"
                "cobblemon" -> "🐾 Cobblemon ($count)"
                else -> "📦 $ns ($count)"
            }
            list.add(Pair(ns, label))
        }

        list.add(Pair("CUSTOM_MANUAL", "✏️ Custom ID / Modded"))
        return list
    }

    data class ItemCard(
        val id: String,
        val displayName: String,
        val namespace: String,
        val stack: ItemStack,
        val isCustomCandidate: Boolean = false,
        val isRegistered: Boolean = true
    )

    private fun getFilteredItems(): List<ItemCard> {
        val query = searchBox.value.trim().lowercase()
        val categories = getCategories()
        val selectedCatKey = categories.getOrNull(selectedCategoryIndex)?.first ?: "ALL"

        if (selectedCatKey == "CUSTOM_MANUAL") return emptyList()

        val result = mutableListOf<ItemCard>()

        if (query.isNotEmpty()) {
            val formattedQuery = if (!query.contains(":")) "minecraft:$query" else query
            val isExactInList = allItemIds.any { it.equals(formattedQuery, ignoreCase = true) }
            val rl = ResourceLocation.tryParse(formattedQuery)
            val isRegistered = rl != null && (try { BuiltInRegistries.ITEM.containsKey(rl) } catch (_: Exception) { false })

            if (!isExactInList) {
                val stack = if (isRegistered && rl != null) {
                    val item = BuiltInRegistries.ITEM.get(rl)
                    if (item != Items.AIR) ItemStack(item) else ItemStack.EMPTY
                } else ItemStack.EMPTY

                result.add(
                    ItemCard(
                        id = formattedQuery,
                        displayName = "Use typed: \"$formattedQuery\"",
                        namespace = if (formattedQuery.contains(":")) formattedQuery.substringBefore(":") else "custom",
                        stack = stack,
                        isCustomCandidate = true,
                        isRegistered = isRegistered
                    )
                )
            }
        }

        val matching = allItemIds.filter { id ->
            val ns = if (id.contains(":")) id.substringBefore(":") else "minecraft"
            val path = if (id.contains(":")) id.substringAfter(":") else id

            val matchesCat = when (selectedCatKey) {
                "ALL" -> true
                "WEAPONS_TOOLS" -> path.contains("sword") || path.contains("axe") || path.contains("bow") ||
                        path.contains("trident") || path.contains("shield") || path.contains("pickaxe") ||
                        path.contains("shovel") || path.contains("hoe") || path.contains("mace")
                "ARMOR" -> path.contains("helmet") || path.contains("chestplate") || path.contains("leggings") ||
                        path.contains("boots") || path.contains("cap") || path.contains("tunic") ||
                        path.contains("pants") || path.contains("elytra")
                else -> ns.equals(selectedCatKey, ignoreCase = true)
            }

            val matchesQuery = if (query.isEmpty()) true else {
                id.lowercase().contains(query) || path.lowercase().contains(query)
            }

            matchesCat && matchesQuery
        }.map { id ->
            val rl = ResourceLocation.tryParse(id)
            val item = if (rl != null) try { BuiltInRegistries.ITEM.get(rl) } catch (_: Exception) { Items.AIR } else Items.AIR
            val stack = if (item != Items.AIR) ItemStack(item) else ItemStack.EMPTY
            val displayName = if (!stack.isEmpty) stack.hoverName.string else (if (id.contains(":")) id.substringAfter(":") else id).replace("_", " ").capitalizeWords()
            val ns = if (id.contains(":")) id.substringBefore(":") else "minecraft"

            ItemCard(
                id = id,
                displayName = displayName,
                namespace = ns,
                stack = stack,
                isCustomCandidate = false,
                isRegistered = item != Items.AIR
            )
        }

        result.addAll(matching)
        return result
    }

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Modal Window Frame
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF14141A.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 24, 0xFF22222E.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())

        guiGraphics.drawString(font, "⚔️ Item & Equipment Catalog Picker", modalX + 10, modalY + 7, 0xFF00FFCC.toInt(), false)

        val selectedDisplay = if (selectedItemId.isNotBlank()) "Selected: $selectedItemId" else "Selected: (None)"
        val selW = font.width(selectedDisplay)
        guiGraphics.drawString(font, selectedDisplay, modalX + modalWidth - selW - 12, modalY + 7, if (selectedItemId.isNotBlank()) 0xFFFFD700.toInt() else 0xFFA0A0A0.toInt(), false)

        searchBox.render(guiGraphics, mouseX, mouseY, partialTick)

        val contentY = modalY + 48
        val contentH = modalHeight - 76

        // 1. LEFT PANEL: Category Selector
        val catW = 125
        val catX = modalX + 12
        guiGraphics.fill(catX, contentY, catX + catW, contentY + contentH, 0xFF0D0D12.toInt())
        guiGraphics.fill(catX + catW, contentY, catX + catW + 1, contentY + contentH, 0xFF282836.toInt())

        val categories = getCategories()
        val isManualMode = categories.getOrNull(selectedCategoryIndex)?.first == "CUSTOM_MANUAL"

        guiGraphics.enableScissor(catX, contentY, catX + catW, contentY + contentH)
        categories.forEachIndexed { idx, (key, label) ->
            val cy = (contentY + 4 + idx * 22 - categoryScrollOffset).toInt()
            if (cy + 18 >= contentY && cy <= contentY + contentH) {
                val isSelected = (idx == selectedCategoryIndex)
                val isHovered = mouseX >= catX + 2 && mouseX <= catX + catW - 2 && mouseY >= cy && mouseY <= cy + 18

                val bg = when {
                    isSelected -> if (key == "CUSTOM_MANUAL") 0xFF6200EA.toInt() else 0xFF3D5AFE.toInt()
                    isHovered -> 0xFF222230.toInt()
                    else -> 0x00000000
                }
                if (bg != 0) {
                    guiGraphics.fill(catX + 2, cy, catX + catW - 2, cy + 18, bg)
                }

                val textColor = if (isSelected) 0xFFFFFFFF.toInt() else if (isHovered) 0xFF00FFCC.toInt() else if (key == "CUSTOM_MANUAL") 0xFFFFD700.toInt() else 0xFFA0A0A0.toInt()
                val truncLabel = font.plainSubstrByWidth(label, catW - 8)
                guiGraphics.drawString(font, truncLabel, catX + 6, cy + 5, textColor, false)
            }
        }
        guiGraphics.disableScissor()

        // Category scrollbar
        val totalCatH = categories.size * 22 + 8
        if (totalCatH > contentH) {
            val maxCatScroll = (totalCatH - contentH).toDouble()
            val thumbH = maxOf(14, (contentH * (contentH.toDouble() / totalCatH)).toInt())
            val thumbY = (contentY + 2 + (categoryScrollOffset / maxCatScroll) * (contentH - 4 - thumbH)).toInt()

            guiGraphics.fill(catX + catW - 4, contentY + 2, catX + catW - 1, contentY + contentH - 2, 0xFF181822.toInt())
            guiGraphics.fill(catX + catW - 4, thumbY, catX + catW - 1, thumbY + thumbH, 0xFF3D5AFE.toInt())
        }

        // 2. RIGHT PANEL: Item List or Custom Manual ID Entry
        val listX = catX + catW + 6
        val listW = modalWidth - (catW + 30)

        if (isManualMode) {
            guiGraphics.fill(listX, contentY, listX + listW, contentY + contentH, 0xFF0D0D12.toInt())

            guiGraphics.drawString(font, "✏️ Custom Item ID Entry:", listX + 10, contentY + 12, 0xFFFFFFFF.toInt(), false)
            guiGraphics.drawString(font, "Enter custom or modded item IDs directly.", listX + 10, contentY + 24, 0xFFA0A0A0.toInt(), false)

            customIdBox.x = listX + 10
            customIdBox.y = contentY + 42
            customIdBox.width = listW - 20
            customIdBox.render(guiGraphics, mouseX, mouseY, partialTick)

            val rl = ResourceLocation.tryParse(customIdBox.value.trim().lowercase())
            val isRegistered = rl != null && (try { BuiltInRegistries.ITEM.containsKey(rl) } catch (_: Exception) { false } || allItemIds.contains(customIdBox.value.trim()))
            val statusText = if (rl == null) "❌ Invalid ResourceLocation format" else if (isRegistered) "🟢 Registered Item" else "🟡 Custom / Mod Item"
            val statusColor = if (rl == null) 0xFFFF5555.toInt() else if (isRegistered) 0xFF00FFCC.toInt() else 0xFFFFD700.toInt()

            guiGraphics.drawString(font, statusText, listX + 10, contentY + 66, statusColor, false)

            applyCustomBtn.x = listX + 10
            applyCustomBtn.y = contentY + 86
            applyCustomBtn.width = listW - 20
            applyCustomBtn.active = customIdBox.value.isNotBlank() && rl != null
            applyCustomBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        } else {
            val filtered = getFilteredItems()
            val itemH = 26
            guiGraphics.fill(listX, contentY, listX + listW, contentY + contentH, 0xFF0D0D12.toInt())

            if (filtered.isEmpty()) {
                guiGraphics.drawCenteredString(font, "No items found.", listX + listW / 2, contentY + contentH / 2 - 4, 0xFF888899.toInt())
            } else {
                guiGraphics.enableScissor(listX, contentY, listX + listW - 6, contentY + contentH)
                filtered.forEachIndexed { idx, card ->
                    val iy = (contentY + 4 + idx * (itemH + 2) - scrollOffset).toInt()
                    if (iy + itemH >= contentY && iy <= contentY + contentH) {
                        val isSelected = card.id == selectedItemId
                        val isHovered = mouseX >= listX + 4 && mouseX <= listX + listW - 10 && mouseY >= iy && mouseY <= iy + itemH
                        val bg = if (isSelected) 0xFF1B3A4B.toInt() else if (isHovered) 0xFF222232.toInt() else 0xFF16161E.toInt()
                        val border = if (isSelected) 0xFF00FFCC.toInt() else if (isHovered) 0xFFFFD700.toInt() else 0xFF282836.toInt()

                        guiGraphics.fill(listX + 4, iy, listX + listW - 10, iy + itemH, bg)
                        guiGraphics.fill(listX + 4, iy, listX + listW - 10, iy + 1, border)
                        guiGraphics.fill(listX + 4, iy + itemH - 1, listX + listW - 10, iy + itemH, border)

                        // Render Item Icon or fallback emoji
                        if (!card.stack.isEmpty) {
                            try {
                                guiGraphics.renderItem(card.stack, listX + 8, iy + 5)
                            } catch (_: Exception) {
                                guiGraphics.drawString(font, "📦", listX + 8, iy + 8, 0xFFFFFFFF.toInt(), false)
                            }
                        } else {
                            guiGraphics.drawString(font, if (card.isCustomCandidate) "✨" else "📦", listX + 8, iy + 8, 0xFFFFFFFF.toInt(), false)
                        }

                        guiGraphics.drawString(font, font.plainSubstrByWidth(card.displayName, listW - 55), listX + 28, iy + 4, if (isSelected) 0xFF00FFCC.toInt() else 0xFFFFFFFF.toInt(), false)
                        guiGraphics.drawString(font, font.plainSubstrByWidth(card.id, listW - 55), listX + 28, iy + 15, 0xFF888899.toInt(), false)

                        if (isSelected) {
                            guiGraphics.drawString(font, "✔", listX + listW - 22, iy + 8, 0xFF00FFCC.toInt(), false)
                        }
                    }
                }
                guiGraphics.disableScissor()

                // List Scrollbar
                val totalItemsH = filtered.size * (itemH + 2) + 8
                if (totalItemsH > contentH) {
                    val maxScroll = maxOf(0.0, (totalItemsH - contentH).toDouble())
                    val thumbH = maxOf(16, (contentH * (contentH.toDouble() / totalItemsH)).toInt())
                    val thumbY = (contentY + 2 + (scrollOffset / maxScroll) * (contentH - 4 - thumbH)).toInt()

                    val sbX = listX + listW - 6
                    guiGraphics.fill(sbX, contentY + 2, sbX + 4, contentY + contentH - 2, 0xFF181822.toInt())
                    guiGraphics.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, if (isDraggingScrollbar) 0xFFFFD700.toInt() else 0xFF00FFCC.toInt())
                }
            }
        }

        // Bottom Action Buttons
        selectBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        clearBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        closeBtn.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun setFocus(target: EditBox?) {
        listOf(searchBox, customIdBox).forEach {
            it.isFocused = (it == target)
        }
        focusedEditBox = target
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (closeBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (selectBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (clearBtn.mouseClicked(mouseX, mouseY, button)) return true

        if (searchBox.mouseClicked(mouseX, mouseY, button)) {
            setFocus(searchBox)
            return true
        }

        val contentY = modalY + 48
        val contentH = modalHeight - 76
        val catW = 125
        val catX = modalX + 12

        // Category Panel click
        if (mouseX >= catX && mouseX <= catX + catW && mouseY >= contentY && mouseY <= contentY + contentH) {
            val categories = getCategories()
            categories.forEachIndexed { idx, _ ->
                val cy = (contentY + 4 + idx * 22 - categoryScrollOffset).toInt()
                if (mouseY >= cy && mouseY <= cy + 20) {
                    selectedCategoryIndex = idx
                    scrollOffset = 0.0
                    return true
                }
            }
        }

        // Right Panel: Item List or Custom Mode
        val listX = catX + catW + 6
        val listW = modalWidth - (catW + 30)
        val categories = getCategories()
        val isManualMode = categories.getOrNull(selectedCategoryIndex)?.first == "CUSTOM_MANUAL"

        if (isManualMode) {
            if (customIdBox.mouseClicked(mouseX, mouseY, button)) {
                setFocus(customIdBox)
                return true
            }
            if (applyCustomBtn.mouseClicked(mouseX, mouseY, button)) return true
        } else {
            val filtered = getFilteredItems()
            val totalItemsH = filtered.size * 28 + 8
            val maxScroll = maxOf(0.0, (totalItemsH - contentH).toDouble())

            // Check scrollbar click
            val sbX = listX + listW - 6
            if (mouseX >= sbX - 2 && mouseX <= sbX + 6 && mouseY >= contentY && mouseY <= contentY + contentH && maxScroll > 0) {
                isDraggingScrollbar = true
                val clickRatio = ((mouseY - contentY) / contentH).coerceIn(0.0, 1.0)
                scrollOffset = clickRatio * maxScroll
                return true
            }

            if (mouseX >= listX && mouseX <= listX + listW - 8 && mouseY >= contentY && mouseY <= contentY + contentH) {
                val itemH = 26
                filtered.forEachIndexed { idx, card ->
                    val iy = (contentY + 4 + idx * (itemH + 2) - scrollOffset).toInt()
                    if (mouseY >= iy && mouseY <= iy + itemH) {
                        selectedItemId = card.id
                        return true
                    }
                }
            }
        }

        setFocus(null)
        return true
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        isDraggingScrollbar = false
        isDraggingCategoryScrollbar = false
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (isDraggingScrollbar) {
            val contentY = modalY + 48
            val contentH = modalHeight - 76
            val filtered = getFilteredItems()
            val totalItemsH = filtered.size * 28 + 8
            val maxScroll = maxOf(0.0, (totalItemsH - contentH).toDouble())
            if (maxScroll > 0) {
                val clickRatio = ((mouseY - contentY) / contentH).coerceIn(0.0, 1.0)
                scrollOffset = clickRatio * maxScroll
                return true
            }
        }
        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        val contentY = modalY + 48
        val contentH = modalHeight - 76
        val catW = 125
        val catX = modalX + 12

        // Category scroll
        if (mouseX >= catX && mouseX <= catX + catW && mouseY >= contentY && mouseY <= contentY + contentH) {
            val totalCatH = getCategories().size * 22 + 8
            val maxScroll = maxOf(0.0, (totalCatH - contentH).toDouble())
            if (scrollY < 0) {
                categoryScrollOffset = (categoryScrollOffset + 22).coerceAtMost(maxScroll)
            } else if (scrollY > 0) {
                categoryScrollOffset = (categoryScrollOffset - 22).coerceAtLeast(0.0)
            }
            return true
        }

        // List scroll
        val listX = catX + catW + 6
        val listW = modalWidth - (catW + 30)
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= contentY && mouseY <= contentY + contentH) {
            val totalItemsH = getFilteredItems().size * 28 + 8
            val maxScroll = maxOf(0.0, (totalItemsH - contentH).toDouble())
            if (scrollY < 0) {
                scrollOffset = (scrollOffset + 28).coerceAtMost(maxScroll)
            } else if (scrollY > 0) {
                scrollOffset = (scrollOffset - 28).coerceAtLeast(0.0)
            }
            return true
        }

        return false
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        val focus = focusedEditBox
        if (focus != null && focus.isFocused) {
            return focus.charTyped(codePoint, modifiers)
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val focus = focusedEditBox
        if (focus != null && focus.isFocused) {
            if (focus.keyPressed(keyCode, scanCode, modifiers)) return true
        }
        if (keyCode == 256) { // ESC
            onClose()
            return true
        }
        if (keyCode == 257 || keyCode == 335) { // ENTER
            if (selectedItemId.isNotBlank()) {
                onSelect(selectedItemId)
                onClose()
                return true
            }
        }
        return false
    }
}
