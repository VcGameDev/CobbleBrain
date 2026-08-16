package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import vito.cobblebrain.model.NodeData

enum class EntityTab {
    CATALOG,
    EQUIPMENT_ATTRIBUTES,
    METADATA_BEHAVIOR
}

class EntityConfigModalWidget(
    val node: NodeData,
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    val onOpenItemPicker: ((onSelect: (String) -> Unit) -> Unit)? = null,
    val onClose: () -> Unit,
    val onDataChanged: () -> Unit
) {
    private val modalWidth = 470.coerceAtMost(screenWidth - 20)
    private val modalHeight = 290.coerceAtMost(screenHeight - 20)
    private val modalX = maxOf(10, (screenWidth - modalWidth) / 2)
    private val modalY = maxOf(10, (screenHeight - modalHeight) / 2)

    private var activeTab: EntityTab = EntityTab.EQUIPMENT_ATTRIBUTES

    // Tab Header Buttons
    private val tabCatalogBtn: Button
    private val tabEquipAttrBtn: Button
    private val tabMetaBtn: Button

    // Tab 1: Catalog Widgets
    private val searchBox: EditBox
    private val customIdBox: EditBox
    private val applyCustomBtn: Button
    private var selectedCategoryIndex: Int = 0
    private var catalogScrollOffset: Double = 0.0
    private var categoryScrollOffset: Double = 0.0
    private val allEntityResources: List<String>
    private var currentEntityId: String = node.params["entityId"] ?: "minecraft:villager"

    // Tab 2: Equipment & Attributes
    private val equipButtons = mutableListOf<Button>()
    private val maxHealthBox: EditBox
    private val speedBox: EditBox
    private val damageBox: EditBox
    private val armorBox: EditBox

    // Tab 3: Metadata & Behavior
    private val customNameBox: EditBox
    private var nameVisible: Boolean
    private var noGravity: Boolean
    private var invulnerable: Boolean
    private var noAi: Boolean
    private var glowing: Boolean
    private var silent: Boolean

    private val nameVisibleBtn: Button
    private val noGravityBtn: Button
    private val invulnerableBtn: Button
    private val noAiBtn: Button
    private val glowingBtn: Button
    private val silentBtn: Button

    // Common Action Buttons
    private val saveBtn: Button
    private val closeBtn: Button

    private var focusedEditBox: EditBox? = null

    // Temporary local equipment state
    private var currentHelmet: String = node.params["entity_helmet"] ?: ""
    private var currentChest: String = node.params["entity_chest"] ?: ""
    private var currentLegs: String = node.params["entity_legs"] ?: ""
    private var currentFeet: String = node.params["entity_feet"] ?: ""
    private var currentMainhand: String = node.params["entity_mainhand"] ?: ""
    private var currentOffhand: String = node.params["entity_offhand"] ?: ""

    init {
        // --- 1. Tab Navigation Header ---
        val tabW = (modalWidth - 28) / 3
        tabCatalogBtn = Button.builder(Component.literal("👾 Entity Catalog")) {
            activeTab = EntityTab.CATALOG
            setFocus(searchBox)
        }.bounds(modalX + 12, modalY + 26, tabW, 18).build()

        tabEquipAttrBtn = Button.builder(Component.literal("⚔️ Equipment & Stats")) {
            activeTab = EntityTab.EQUIPMENT_ATTRIBUTES
            setFocus(null)
        }.bounds(modalX + 14 + tabW, modalY + 26, tabW, 18).build()

        tabMetaBtn = Button.builder(Component.literal("🧠 Metadata & Behavior")) {
            activeTab = EntityTab.METADATA_BEHAVIOR
            setFocus(customNameBox)
        }.bounds(modalX + 16 + tabW * 2, modalY + 26, tabW, 18).build()

        // --- 2. Catalog Tab Setup ---
        val registryList = try { BuiltInRegistries.ENTITY_TYPE.keySet().map { it.toString() } } catch (_: Exception) { emptyList() }
        val fallbackList = listOf(
            "minecraft:villager", "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper",
            "minecraft:spider", "minecraft:enderman", "minecraft:iron_golem", "minecraft:pig",
            "minecraft:cow", "minecraft:sheep", "minecraft:chicken", "minecraft:horse",
            "minecraft:wolf", "minecraft:cat", "minecraft:blaze", "minecraft:witch",
            "minecraft:wither", "minecraft:warden", "minecraft:allay", "minecraft:frog",
            "cobblemon:pokemon"
        )
        allEntityResources = (registryList + fallbackList).distinct().sorted()

        searchBox = EditBox(font, modalX + 14, modalY + 50, modalWidth - 28, 16, Component.literal("Search"))
        searchBox.setHint(Component.literal("🔍 Filter entities or enter custom ID..."))
        searchBox.setEditable(true)
        searchBox.active = true
        searchBox.setResponder { catalogScrollOffset = 0.0 }

        customIdBox = EditBox(font, modalX + 150, modalY + 90, modalWidth - 165, 18, Component.literal("Custom ID"))
        customIdBox.value = currentEntityId
        customIdBox.setHint(Component.literal("e.g. custom_mod:my_entity"))
        customIdBox.setEditable(true)
        customIdBox.active = true

        applyCustomBtn = Button.builder(Component.literal("✔ Confirm and Use this ID")) {
            val raw = customIdBox.value.trim()
            if (raw.isNotBlank()) {
                val formatted = if (!raw.contains(":")) "minecraft:$raw" else raw
                currentEntityId = formatted
                node.params["entityId"] = formatted
                onDataChanged()
            }
        }.bounds(modalX + 150, modalY + 170, modalWidth - 165, 20).build()

        // --- 3. Equipment & Stats Tab Setup ---
        val colW = (modalWidth - 36) / 2
        val leftX = modalX + 14
        val rightX = modalX + 22 + colW

        fun createEquipSlot(relY: Int, icon: String, getVal: () -> String, setVal: (String) -> Unit): Pair<Button, Button> {
            lateinit var slotBtn: Button
            fun updateBtnText() {
                val v = getVal()
                val label = if (v.isNotBlank()) {
                    val shortName = if (v.contains(":")) v.substringAfter(":") else v
                    font.plainSubstrByWidth("$icon $shortName", colW - 28)
                } else {
                    "$icon (Empty - Choose)"
                }
                slotBtn.message = Component.literal(label)
            }

            slotBtn = Button.builder(Component.literal("")) {
                onOpenItemPicker?.invoke { selectedItem ->
                    setVal(selectedItem)
                    updateBtnText()
                }
            }.bounds(leftX, modalY + relY, colW - 24, 18).build()
            updateBtnText()

            val clearBtn = Button.builder(Component.literal("🗑️")) {
                setVal("")
                updateBtnText()
            }.bounds(leftX + colW - 22, modalY + relY, 20, 18).build()

            return Pair(slotBtn, clearBtn)
        }

        val hRow = createEquipSlot(60, "🪖", { currentHelmet }, { currentHelmet = it })
        equipButtons.add(hRow.first); equipButtons.add(hRow.second)

        val cRow = createEquipSlot(90, "🥋", { currentChest }, { currentChest = it })
        equipButtons.add(cRow.first); equipButtons.add(cRow.second)

        val lRow = createEquipSlot(120, "👖", { currentLegs }, { currentLegs = it })
        equipButtons.add(lRow.first); equipButtons.add(lRow.second)

        val fRow = createEquipSlot(150, "👢", { currentFeet }, { currentFeet = it })
        equipButtons.add(fRow.first); equipButtons.add(fRow.second)

        val mRow = createEquipSlot(180, "🗡️", { currentMainhand }, { currentMainhand = it })
        equipButtons.add(mRow.first); equipButtons.add(mRow.second)

        val oRow = createEquipSlot(210, "🛡️", { currentOffhand }, { currentOffhand = it })
        equipButtons.add(oRow.first); equipButtons.add(oRow.second)

        maxHealthBox = EditBox(font, rightX, modalY + 62, colW, 16, Component.literal("Max Health"))
        maxHealthBox.value = node.params["entity_maxHealth"] ?: ""
        maxHealthBox.setHint(Component.literal("e.g. 20.0, 100.0"))
        maxHealthBox.setEditable(true); maxHealthBox.active = true

        speedBox = EditBox(font, rightX, modalY + 104, colW, 16, Component.literal("Movement Speed"))
        speedBox.value = node.params["entity_speed"] ?: ""
        speedBox.setHint(Component.literal("e.g. 0.25, 0.35"))
        speedBox.setEditable(true); speedBox.active = true

        damageBox = EditBox(font, rightX, modalY + 146, colW, 16, Component.literal("Attack Damage"))
        damageBox.value = node.params["entity_damage"] ?: ""
        damageBox.setHint(Component.literal("e.g. 2.0, 10.0"))
        damageBox.setEditable(true); damageBox.active = true

        armorBox = EditBox(font, rightX, modalY + 188, colW, 16, Component.literal("Armor"))
        armorBox.value = node.params["entity_armor"] ?: ""
        armorBox.setHint(Component.literal("e.g. 0.0, 15.0"))
        armorBox.setEditable(true); armorBox.active = true

        // --- 4. Metadata & Behavior Tab Setup ---
        customNameBox = EditBox(font, modalX + 16, modalY + 64, modalWidth - 32, 16, Component.literal("Custom Name"))
        customNameBox.value = node.params["entity_customName"] ?: node.params["customName"] ?: ""
        customNameBox.setHint(Component.literal("Name displayed above entity..."))
        customNameBox.setEditable(true); customNameBox.active = true

        nameVisible = node.params["entity_nameVisible"] == "true"
        noGravity = node.params["entity_noGravity"] == "true"
        invulnerable = node.params["entity_invulnerable"] == "true"
        noAi = node.params["entity_noAi"] == "true" || node.params["noAi"] == "true"
        glowing = node.params["entity_glowing"] == "true"
        silent = node.params["entity_silent"] == "true"

        val toggleW = (modalWidth - 40) / 2

        nameVisibleBtn = Button.builder(Component.literal(if (nameVisible) "🏷️ Name Always Visible: YES" else "🏷️ Name Always Visible: NO")) {
            nameVisible = !nameVisible
            nameVisibleBtn.message = Component.literal(if (nameVisible) "🏷️ Name Always Visible: YES" else "🏷️ Name Always Visible: NO")
        }.bounds(modalX + 16, modalY + 92, toggleW, 18).build()

        noGravityBtn = Button.builder(Component.literal(if (noGravity) "🪶 No Gravity: YES" else "🪶 No Gravity: NO")) {
            noGravity = !noGravity
            noGravityBtn.message = Component.literal(if (noGravity) "🪶 No Gravity: YES" else "🪶 No Gravity: NO")
        }.bounds(modalX + 24 + toggleW, modalY + 92, toggleW, 18).build()

        invulnerableBtn = Button.builder(Component.literal(if (invulnerable) "🛡️ Invulnerable: YES" else "🛡️ Invulnerable: NO")) {
            invulnerable = !invulnerable
            invulnerableBtn.message = Component.literal(if (invulnerable) "🛡️ Invulnerable: YES" else "🛡️ Invulnerable: NO")
        }.bounds(modalX + 16, modalY + 118, toggleW, 18).build()

        noAiBtn = Button.builder(Component.literal(if (noAi) "🧠 No AI (NoAI): YES" else "🧠 No AI (NoAI): NO")) {
            noAi = !noAi
            noAiBtn.message = Component.literal(if (noAi) "🧠 No AI (NoAI): YES" else "🧠 No AI (NoAI): NO")
        }.bounds(modalX + 24 + toggleW, modalY + 118, toggleW, 18).build()

        glowingBtn = Button.builder(Component.literal(if (glowing) "✨ Glowing: YES" else "✨ Glowing: NO")) {
            glowing = !glowing
            glowingBtn.message = Component.literal(if (glowing) "✨ Glowing: YES" else "✨ Glowing: NO")
        }.bounds(modalX + 16, modalY + 144, toggleW, 18).build()

        silentBtn = Button.builder(Component.literal(if (silent) "🔇 Silent: YES" else "🔇 Silent: NO")) {
            silent = !silent
            silentBtn.message = Component.literal(if (silent) "🔇 Silent: YES" else "🔇 Silent: NO")
        }.bounds(modalX + 24 + toggleW, modalY + 144, toggleW, 18).build()

        // --- 5. Common Save & Cancel Buttons ---
        saveBtn = Button.builder(Component.literal("💾 Save Properties")) {
            if (currentEntityId.isNotBlank()) node.params["entityId"] = currentEntityId.trim()

            if (currentHelmet.isNotBlank()) node.params["entity_helmet"] = currentHelmet.trim() else node.params.remove("entity_helmet")
            if (currentChest.isNotBlank()) node.params["entity_chest"] = currentChest.trim() else node.params.remove("entity_chest")
            if (currentLegs.isNotBlank()) node.params["entity_legs"] = currentLegs.trim() else node.params.remove("entity_legs")
            if (currentFeet.isNotBlank()) node.params["entity_feet"] = currentFeet.trim() else node.params.remove("entity_feet")
            if (currentMainhand.isNotBlank()) node.params["entity_mainhand"] = currentMainhand.trim() else node.params.remove("entity_mainhand")
            if (currentOffhand.isNotBlank()) node.params["entity_offhand"] = currentOffhand.trim() else node.params.remove("entity_offhand")

            if (maxHealthBox.value.isNotBlank()) node.params["entity_maxHealth"] = maxHealthBox.value.trim() else node.params.remove("entity_maxHealth")
            if (speedBox.value.isNotBlank()) node.params["entity_speed"] = speedBox.value.trim() else node.params.remove("entity_speed")
            if (damageBox.value.isNotBlank()) node.params["entity_damage"] = damageBox.value.trim() else node.params.remove("entity_damage")
            if (armorBox.value.isNotBlank()) node.params["entity_armor"] = armorBox.value.trim() else node.params.remove("entity_armor")

            if (customNameBox.value.isNotBlank()) {
                node.params["entity_customName"] = customNameBox.value.trim()
                node.params["customName"] = customNameBox.value.trim()
            } else {
                node.params.remove("entity_customName")
                node.params.remove("customName")
            }

            node.params["entity_nameVisible"] = nameVisible.toString()
            node.params["entity_noGravity"] = noGravity.toString()
            node.params["entity_invulnerable"] = invulnerable.toString()
            node.params["entity_noAi"] = noAi.toString()
            node.params["noAi"] = noAi.toString()
            node.params["entity_glowing"] = glowing.toString()
            node.params["entity_silent"] = silent.toString()

            onDataChanged()
            onClose()
        }.bounds(modalX + modalWidth - 145, modalY + modalHeight - 24, 130, 18).build()

        closeBtn = Button.builder(Component.literal("✖ Cancel")) {
            onClose()
        }.bounds(modalX + 15, modalY + modalHeight - 24, 75, 18).build()
    }

    private fun getCatalogCategories(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        list.add(Pair("ALL", "📁 All (${allEntityResources.size})"))

        val namespaces = allEntityResources.map { if (it.contains(":")) it.substringBefore(":") else "minecraft" }.distinct()
        namespaces.forEach { ns ->
            val count = allEntityResources.count { it.startsWith("$ns:") }
            val label = when (ns.lowercase()) {
                "minecraft" -> "⛏️ Minecraft ($count)"
                "cobblemon" -> "🐾 Cobblemon ($count)"
                else -> "📦 $ns ($count)"
            }
            list.add(Pair(ns, label))
        }

        list.add(Pair("CUSTOM_MANUAL", "✏️ Custom ID"))
        return list
    }

    private data class EntityCard(val id: String, val displayName: String, val namespace: String, val icon: String)

    private fun getFilteredEntities(): List<EntityCard> {
        val query = searchBox.value.trim().lowercase()
        val categories = getCatalogCategories()
        val selectedCatKey = categories.getOrNull(selectedCategoryIndex)?.first ?: "ALL"

        if (selectedCatKey == "CUSTOM_MANUAL") return emptyList()

        return allEntityResources.filter { id ->
            val ns = if (id.contains(":")) id.substringBefore(":") else "minecraft"
            val matchesCat = (selectedCatKey == "ALL" || ns.equals(selectedCatKey, ignoreCase = true))
            val matchesQuery = query.isEmpty() || id.lowercase().contains(query)
            matchesCat && matchesQuery
        }.map { id ->
            val path = if (id.contains(":")) id.substringAfter(":") else id
            val ns = if (id.contains(":")) id.substringBefore(":") else "minecraft"
            val icon = if (ns == "cobblemon") "🐾" else "👾"
            EntityCard(id, path, ns, icon)
        }
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Modal Window Frame
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF14141A.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 22, 0xFF22222E.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())

        guiGraphics.drawString(font, "⚙️ Entity Configuration · Target: $currentEntityId", modalX + 15, modalY + 7, 0xFF00FFCC.toInt(), false)

        // Render Tab Headers
        tabCatalogBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        tabEquipAttrBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        tabMetaBtn.render(guiGraphics, mouseX, mouseY, partialTick)

        val tabW = (modalWidth - 28) / 3
        val indicatorX = when (activeTab) {
            EntityTab.CATALOG -> modalX + 12
            EntityTab.EQUIPMENT_ATTRIBUTES -> modalX + 14 + tabW
            EntityTab.METADATA_BEHAVIOR -> modalX + 16 + tabW * 2
        }
        guiGraphics.fill(indicatorX, modalY + 44, indicatorX + tabW, modalY + 46, 0xFF00FFCC.toInt())

        when (activeTab) {
            EntityTab.CATALOG -> {
                searchBox.render(guiGraphics, mouseX, mouseY, partialTick)

                val contentY = modalY + 70
                val contentH = modalHeight - 98
                val catW = 120
                val catX = modalX + 14

                // Left Panel: Categories
                guiGraphics.fill(catX, contentY, catX + catW, contentY + contentH, 0xFF0D0D12.toInt())
                guiGraphics.fill(catX + catW, contentY, catX + catW + 1, contentY + contentH, 0xFF282836.toInt())

                val categories = getCatalogCategories()
                val isManualMode = categories.getOrNull(selectedCategoryIndex)?.first == "CUSTOM_MANUAL"

                guiGraphics.enableScissor(catX, contentY, catX + catW, contentY + contentH)
                categories.forEachIndexed { idx, (key, label) ->
                    val cy = (contentY + 4 + idx * 20 - categoryScrollOffset).toInt()
                    if (cy + 18 >= contentY && cy <= contentY + contentH - 2) {
                        val isSelected = (idx == selectedCategoryIndex)
                        val isHovered = mouseX >= catX + 2 && mouseX <= catX + catW - 2 && mouseY >= cy && mouseY <= cy + 18
                        val bg = if (isSelected) 0xFF3D5AFE.toInt() else if (isHovered) 0xFF222230.toInt() else 0
                        if (bg != 0) guiGraphics.fill(catX + 2, cy, catX + catW - 2, cy + 18, bg)
                        val textColor = if (isSelected) 0xFFFFFFFF.toInt() else if (isHovered) 0xFF00FFCC.toInt() else 0xFFA0A0A0.toInt()
                        guiGraphics.drawString(font, font.plainSubstrByWidth(label, catW - 6), catX + 6, cy + 5, textColor, false)
                    }
                }
                guiGraphics.disableScissor()

                // Right Panel: Cards or Manual ID Entry
                val listX = catX + catW + 6
                val listW = modalWidth - (catW + 34)

                if (isManualMode) {
                    guiGraphics.fill(listX, contentY, listX + listW, contentY + contentH, 0xFF0D0D12.toInt())
                    guiGraphics.drawString(font, "✏️ Manual Entity Type Entry:", listX + 10, contentY + 12, 0xFFFFFFFF.toInt(), false)

                    customIdBox.x = listX + 10
                    customIdBox.y = contentY + 30
                    customIdBox.width = listW - 20
                    customIdBox.render(guiGraphics, mouseX, mouseY, partialTick)

                    val rl = ResourceLocation.tryParse(customIdBox.value.trim().lowercase())
                    val isRegistered = rl != null && (try { BuiltInRegistries.ENTITY_TYPE.containsKey(rl) } catch (_: Exception) { false } || allEntityResources.contains(customIdBox.value.trim()))
                    val statusText = if (rl == null) "❌ Invalid ResourceLocation format" else if (isRegistered) "🟢 Registered Entity Type" else "🟡 Custom Entity Type"
                    val statusColor = if (rl == null) 0xFFFF5555.toInt() else if (isRegistered) 0xFF00FFCC.toInt() else 0xFFFFD700.toInt()

                    guiGraphics.drawString(font, statusText, listX + 10, contentY + 54, statusColor, false)

                    applyCustomBtn.x = listX + 10
                    applyCustomBtn.y = contentY + 74
                    applyCustomBtn.width = listW - 20
                    applyCustomBtn.active = customIdBox.value.isNotBlank() && rl != null
                    applyCustomBtn.render(guiGraphics, mouseX, mouseY, partialTick)
                } else {
                    val filtered = getFilteredEntities()
                    val itemH = 24
                    guiGraphics.fill(listX, contentY, listX + listW, contentY + contentH, 0xFF0D0D12.toInt())

                    if (filtered.isEmpty()) {
                        guiGraphics.drawCenteredString(font, "No entities found.", listX + listW / 2, contentY + contentH / 2 - 4, 0xFF888899.toInt())
                    } else {
                        guiGraphics.enableScissor(listX, contentY, listX + listW, contentY + contentH)
                        filtered.forEachIndexed { idx, card ->
                            val iy = (contentY + 4 + idx * (itemH + 2) - catalogScrollOffset).toInt()
                            if (iy + itemH >= contentY && iy <= contentY + contentH) {
                                val isSelected = card.id == currentEntityId
                                val isHovered = mouseX >= listX + 4 && mouseX <= listX + listW - 4 && mouseY >= iy && mouseY <= iy + itemH
                                val bg = if (isSelected) 0xFF1B3A4B.toInt() else if (isHovered) 0xFF222232.toInt() else 0xFF16161E.toInt()
                                val border = if (isSelected) 0xFF00FFCC.toInt() else if (isHovered) 0xFFFFD700.toInt() else 0xFF282836.toInt()

                                guiGraphics.fill(listX + 4, iy, listX + listW - 4, iy + itemH, bg)
                                guiGraphics.fill(listX + 4, iy, listX + listW - 4, iy + 1, border)
                                guiGraphics.fill(listX + 4, iy + itemH - 1, listX + listW - 4, iy + itemH, border)

                                guiGraphics.drawString(font, card.icon, listX + 8, iy + 7, 0xFFFFFFFF.toInt(), false)
                                guiGraphics.drawString(font, font.plainSubstrByWidth(card.displayName, listW - 40), listX + 24, iy + 3, if (isSelected) 0xFF00FFCC.toInt() else 0xFFFFFFFF.toInt(), false)
                                guiGraphics.drawString(font, font.plainSubstrByWidth(card.id, listW - 40), listX + 24, iy + 13, 0xFF888899.toInt(), false)

                                if (isSelected) guiGraphics.drawString(font, "✔", listX + listW - 16, iy + 7, 0xFF00FFCC.toInt(), false)
                            }
                        }
                        guiGraphics.disableScissor()
                    }
                }
            }

            EntityTab.EQUIPMENT_ATTRIBUTES -> {
                val colW = (modalWidth - 36) / 2
                val leftX = modalX + 14
                val rightX = modalX + 22 + colW

                // Left Column: Equipments
                guiGraphics.drawString(font, "🪖 Helmet:", leftX, modalY + 50, 0xFFA0A0A0.toInt(), false)
                guiGraphics.drawString(font, "🥋 Chestplate:", leftX, modalY + 80, 0xFFA0A0A0.toInt(), false)
                guiGraphics.drawString(font, "👖 Leggings:", leftX, modalY + 110, 0xFFA0A0A0.toInt(), false)
                guiGraphics.drawString(font, "👢 Boots:", leftX, modalY + 140, 0xFFA0A0A0.toInt(), false)
                guiGraphics.drawString(font, "🗡️ Main Hand:", leftX, modalY + 170, 0xFFA0A0A0.toInt(), false)
                guiGraphics.drawString(font, "🛡️ Offhand:", leftX, modalY + 200, 0xFFA0A0A0.toInt(), false)

                equipButtons.forEach { it.render(guiGraphics, mouseX, mouseY, partialTick) }

                // Right Column: Attributes
                guiGraphics.drawString(font, "❤️ Max Health:", rightX, modalY + 50, 0xFFA0A0A0.toInt(), false)
                maxHealthBox.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "⚡ Movement Speed:", rightX, modalY + 92, 0xFFA0A0A0.toInt(), false)
                speedBox.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "💥 Attack Damage:", rightX, modalY + 134, 0xFFA0A0A0.toInt(), false)
                damageBox.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "🛡️ Armor:", rightX, modalY + 176, 0xFFA0A0A0.toInt(), false)
                armorBox.render(guiGraphics, mouseX, mouseY, partialTick)
            }

            EntityTab.METADATA_BEHAVIOR -> {
                guiGraphics.drawString(font, "🏷️ Custom Name:", modalX + 16, modalY + 52, 0xFFA0A0A0.toInt(), false)
                customNameBox.render(guiGraphics, mouseX, mouseY, partialTick)

                nameVisibleBtn.render(guiGraphics, mouseX, mouseY, partialTick)
                noGravityBtn.render(guiGraphics, mouseX, mouseY, partialTick)
                invulnerableBtn.render(guiGraphics, mouseX, mouseY, partialTick)
                noAiBtn.render(guiGraphics, mouseX, mouseY, partialTick)
                glowingBtn.render(guiGraphics, mouseX, mouseY, partialTick)
                silentBtn.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "ℹ️ These settings override default entity behavior.", modalX + 16, modalY + 170, 0xFF888899.toInt(), false)
            }
        }

        saveBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        closeBtn.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun setFocus(target: EditBox?) {
        listOf(searchBox, customIdBox, maxHealthBox, speedBox, damageBox, armorBox, customNameBox).forEach {
            it.isFocused = (it == target)
        }
        focusedEditBox = target
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Tab Header clicks
        if (tabCatalogBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (tabEquipAttrBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (tabMetaBtn.mouseClicked(mouseX, mouseY, button)) return true

        // Bottom Action buttons
        if (saveBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (closeBtn.mouseClicked(mouseX, mouseY, button)) return true

        when (activeTab) {
            EntityTab.CATALOG -> {
                if (searchBox.mouseClicked(mouseX, mouseY, button)) {
                    setFocus(searchBox)
                    return true
                }

                val contentY = modalY + 70
                val contentH = modalHeight - 98
                val catW = 120
                val catX = modalX + 14

                // Category Click
                if (mouseX >= catX && mouseX <= catX + catW && mouseY >= contentY && mouseY <= contentY + contentH) {
                    val categories = getCatalogCategories()
                    categories.forEachIndexed { idx, _ ->
                        val cy = contentY + 4 + idx * 20 - categoryScrollOffset
                        if (mouseY >= cy && mouseY <= cy + 18) {
                            selectedCategoryIndex = idx
                            catalogScrollOffset = 0.0
                            return true
                        }
                    }
                }

                // Right Panel
                val listX = catX + catW + 6
                val listW = modalWidth - (catW + 34)
                val categories = getCatalogCategories()
                val isManualMode = categories.getOrNull(selectedCategoryIndex)?.first == "CUSTOM_MANUAL"

                if (isManualMode) {
                    if (customIdBox.mouseClicked(mouseX, mouseY, button)) {
                        setFocus(customIdBox)
                        return true
                    }
                    if (applyCustomBtn.mouseClicked(mouseX, mouseY, button)) return true
                } else {
                    if (mouseX >= listX && mouseX <= listX + listW && mouseY >= contentY && mouseY <= contentY + contentH) {
                        val filtered = getFilteredEntities()
                        val itemH = 24
                        filtered.forEachIndexed { idx, card ->
                            val iy = contentY + 4 + idx * (itemH + 2) - catalogScrollOffset
                            if (mouseY >= iy && mouseY <= iy + itemH) {
                                currentEntityId = card.id
                                node.params["entityId"] = card.id
                                onDataChanged()
                                return true
                            }
                        }
                    }
                }
            }

            EntityTab.EQUIPMENT_ATTRIBUTES -> {
                for (btn in equipButtons) {
                    if (btn.mouseClicked(mouseX, mouseY, button)) return true
                }

                val boxes = listOf(maxHealthBox, speedBox, damageBox, armorBox)
                for (box in boxes) {
                    if (box.mouseClicked(mouseX, mouseY, button)) {
                        setFocus(box)
                        return true
                    }
                }
            }

            EntityTab.METADATA_BEHAVIOR -> {
                if (customNameBox.mouseClicked(mouseX, mouseY, button)) {
                    setFocus(customNameBox)
                    return true
                }

                if (nameVisibleBtn.mouseClicked(mouseX, mouseY, button)) return true
                if (noGravityBtn.mouseClicked(mouseX, mouseY, button)) return true
                if (invulnerableBtn.mouseClicked(mouseX, mouseY, button)) return true
                if (noAiBtn.mouseClicked(mouseX, mouseY, button)) return true
                if (glowingBtn.mouseClicked(mouseX, mouseY, button)) return true
                if (silentBtn.mouseClicked(mouseX, mouseY, button)) return true
            }
        }

        setFocus(null)
        return true
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        if (activeTab == EntityTab.CATALOG) {
            val contentY = modalY + 70
            val contentH = modalHeight - 98
            val catW = 120
            val catX = modalX + 14

            if (mouseX >= catX && mouseX <= catX + catW && mouseY >= contentY && mouseY <= contentY + contentH) {
                val totalCatH = getCatalogCategories().size * 20 + 4
                val maxScroll = maxOf(0.0, (totalCatH - contentH).toDouble())
                if (scrollY < 0) {
                    categoryScrollOffset = (categoryScrollOffset + 20).coerceAtMost(maxScroll)
                } else if (scrollY > 0) {
                    categoryScrollOffset = (categoryScrollOffset - 20).coerceAtLeast(0.0)
                }
                return true
            }

            val listX = catX + catW + 6
            val listW = modalWidth - (catW + 34)
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= contentY && mouseY <= contentY + contentH) {
                val totalItemsH = getFilteredEntities().size * 26 + 4
                val maxScroll = maxOf(0.0, (totalItemsH - contentH).toDouble())
                if (scrollY < 0) {
                    catalogScrollOffset = (catalogScrollOffset + 26).coerceAtMost(maxScroll)
                } else if (scrollY > 0) {
                    catalogScrollOffset = (catalogScrollOffset - 26).coerceAtLeast(0.0)
                }
                return true
            }
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
        return false
    }
}
