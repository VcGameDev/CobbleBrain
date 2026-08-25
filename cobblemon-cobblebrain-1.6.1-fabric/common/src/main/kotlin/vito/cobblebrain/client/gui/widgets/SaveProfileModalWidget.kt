package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.NodeType
import vito.cobblebrain.model.StoryProject

class SaveProfileModalWidget(
    val node: NodeData,
    val project: StoryProject,
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    val onClose: () -> Unit,
    val onDataChanged: () -> Unit
) {
    // Mode Detection: LOAD if nodeType is LOAD_STATE_NODE or checkpointMode is LOAD
    private var isSaveMode: Boolean = when {
        node.nodeType == NodeType.SAVE_STATE_NODE -> true
        node.nodeType == NodeType.LOAD_STATE_NODE -> false
        node.params["checkpointMode"] == "LOAD" -> false
        else -> true
    }

    private val modalWidth = 470.coerceAtMost(screenWidth - 20)
    private val modalHeight = 340.coerceAtMost(screenHeight - 20)
    private val modalX = maxOf(10, (screenWidth - modalWidth) / 2)
    private val modalY = maxOf(10, (screenHeight - modalHeight) / 2)

    private val contentLeft = modalX + 10
    private val contentTop = modalY + 78
    private val contentRight = modalX + modalWidth - 10
    private val contentBottom = modalY + modalHeight - 10
    private val viewportH = contentBottom - contentTop

    private var activeTab: Int = 0 // 0 = Modules, 1 = Flow & Scope, 2 = Transition (Load mode only)
    private var tabScrollOffsets = floatArrayOf(0f, 0f, 0f)
    private var focusedEditBox: EditBox? = null
    private var activeVarPickerModal: VariableSelectorModalWidget? = null

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

            // Label
            guiGraphics.drawString(font, title, x + 8, y + (height - 8) / 2, 0xFFCBD5E1.toInt(), false)

            // Status Badge Pill on Right
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
    private val scopeBtn: ModernButton
    private val modeToggleBtn: ModernButton?

    private val profileIdEdit: EditBox

    // Tab 0 Controls
    private val varModeBtn: ModernButton
    private val openVarPickerBtn: ModernButton
    private val varKeysEdit: EditBox

    private val posToggle: CompactToggleRow
    private val statsToggle: CompactToggleRow
    private val invToggle: CompactToggleRow
    private val armorToggle: CompactToggleRow
    private val cobblemonToggle: CompactToggleRow
    private val worldToggle: CompactToggleRow
    private val questToggle: CompactToggleRow

    // Tab 1 Controls
    private val mergeModeBtn: ModernButton
    private val jumpNodeEdit: EditBox
    private val graceTicksEdit: EditBox
    private val cleanTagEdit: EditBox

    // Tab 2 Controls (Transition Overlay)
    private val styleBtn: ModernButton
    private val durationEdit: EditBox
    private val titleEdit: EditBox
    private val subTitleEdit: EditBox
    private val tipsEdit: EditBox
    private val soundEdit: EditBox

    init {
        val inputX = modalX + 140
        val inputW = modalWidth - 155

        closeButton = ModernButton(modalX + modalWidth - 28, modalY + 6, 20, 16, "✖") {
            onClose()
        }

        // Mode switch ONLY if generic CHECKPOINT_NODE
        modeToggleBtn = if (node.nodeType == NodeType.CHECKPOINT_NODE) {
            ModernButton(modalX + modalWidth - 225, modalY + 6, 90, 16, if (isSaveMode) "Mode: SAVE" else "Mode: LOAD", isPrimary = true) {
                isSaveMode = !isSaveMode
                node.params["checkpointMode"] = if (isSaveMode) "SAVE" else "LOAD"
                modeToggleBtn?.label = if (isSaveMode) "Mode: SAVE" else "Mode: LOAD"
                if (activeTab > 1 && isSaveMode) activeTab = 0
                onDataChanged()
            }
        } else null

        profileIdEdit = EditBox(font, modalX + 105, modalY + 30, modalWidth - 230, 16, Component.literal("Profile ID"))
        profileIdEdit.setMaxLength(2000)
        profileIdEdit.setHint(Component.literal("§8e.g. {player}_slot1"))
        profileIdEdit.value = node.params["profileId"] ?: "checkpoint_1"
        profileIdEdit.setEditable(true)
        profileIdEdit.active = true
        profileIdEdit.setResponder { node.params["profileId"] = it; onDataChanged() }

        val currentScope = node.params["scope"] ?: "PLAYER"
        scopeBtn = ModernButton(modalX + modalWidth - 118, modalY + 30, 110, 16, if (currentScope == "PLAYER") "👤 Player" else "🌍 Global") {
            val next = if (node.params["scope"] == "PLAYER") "GLOBAL" else "PLAYER"
            node.params["scope"] = next
            scopeBtn.label = if (next == "PLAYER") "👤 Player" else "🌍 Global"
            onDataChanged()
        }

        // Tab 0 Controls
        val saveVarsMode = node.params["saveVariablesMode"] ?: "ALL"
        varModeBtn = ModernButton(0, 0, 105, 18, if (saveVarsMode == "ALL") "All Variables" else "Selected Only") {
            val next = if (node.params["saveVariablesMode"] == "ALL") "SELECTED" else "ALL"
            node.params["saveVariablesMode"] = next
            varModeBtn.label = if (next == "ALL") "All Variables" else "Selected Only"
            onDataChanged()
        }

        openVarPickerBtn = ModernButton(0, 0, 110, 18, "📋 Select...") {
            val currentSelected = (node.params["selectedVarKeys"] ?: "").split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
            activeVarPickerModal = VariableSelectorModalWidget(
                project = project,
                initialSelectedKeys = currentSelected,
                font = font,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                onSaveSelection = { updated ->
                    val keysStr = updated.joinToString(", ")
                    node.params["selectedVarKeys"] = keysStr
                    varKeysEdit.value = keysStr
                    onDataChanged()
                },
                onClose = { activeVarPickerModal = null }
            )
        }

        varKeysEdit = EditBox(font, 0, 0, inputW, 16, Component.literal("Variable Keys"))
        varKeysEdit.setMaxLength(2000)
        varKeysEdit.setHint(Component.literal("§8Comma-separated keys"))
        varKeysEdit.value = node.params["selectedVarKeys"] ?: ""
        varKeysEdit.setEditable(true)
        varKeysEdit.active = true
        varKeysEdit.setResponder { node.params["selectedVarKeys"] = it; onDataChanged() }

        val posOn = node.params["savePlayerPos"] != "false"
        posToggle = CompactToggleRow("📍 Player XYZ Position & Rotation", posOn) {
            val next = !posToggle.isEnabled
            node.params["savePlayerPos"] = if (next) "true" else "false"
            onDataChanged()
        }

        val statsOn = node.params["savePlayerStats"] != "false"
        statsToggle = CompactToggleRow("❤️ Health & Hunger Levels", statsOn) {
            val next = !statsToggle.isEnabled
            node.params["savePlayerStats"] = if (next) "true" else "false"
            onDataChanged()
        }

        val invOn = node.params["saveMainInventory"] != "false"
        invToggle = CompactToggleRow("🎒 Main Inventory (Slots 0-35 & Hotbar)", invOn) {
            val next = !invToggle.isEnabled
            node.params["saveMainInventory"] = if (next) "true" else "false"
            onDataChanged()
        }

        val armorOn = node.params["saveArmorOffhand"] != "false"
        armorToggle = CompactToggleRow("🛡️ Equipped Armor & Offhand Slots", armorOn) {
            val next = !armorToggle.isEnabled
            node.params["saveArmorOffhand"] = if (next) "true" else "false"
            onDataChanged()
        }

        val cobbleOn = node.params["saveCobblemonParty"] != "false"
        cobblemonToggle = CompactToggleRow("🐾 Cobblemon Party Snapshot", cobbleOn) {
            val next = !cobblemonToggle.isEnabled
            node.params["saveCobblemonParty"] = if (next) "true" else "false"
            onDataChanged()
        }

        val worldOn = node.params["saveWorldState"] != "false"
        worldToggle = CompactToggleRow("☀️ World Time & Weather Conditions", worldOn) {
            val next = !worldToggle.isEnabled
            node.params["saveWorldState"] = if (next) "true" else "false"
            onDataChanged()
        }

        val questOn = node.params["saveQuestProgress"] != "false"
        questToggle = CompactToggleRow("🏆 Story Quests & Objective Progress", questOn) {
            val next = !questToggle.isEnabled
            node.params["saveQuestProgress"] = if (next) "true" else "false"
            onDataChanged()
        }

        // Tab 1 Controls
        val currentMerge = node.params["mergeMode"] ?: "OVERWRITE"
        mergeModeBtn = ModernButton(0, 0, inputW, 18, if (currentMerge == "OVERWRITE") "Overwrite All Variables" else "Soft Merge (Keep Session)") {
            val next = if (node.params["mergeMode"] == "OVERWRITE") "SOFT_MERGE" else "OVERWRITE"
            node.params["mergeMode"] = next
            mergeModeBtn.label = if (next == "OVERWRITE") "Overwrite All Variables" else "Soft Merge (Keep Session)"
            onDataChanged()
        }

        jumpNodeEdit = EditBox(font, 0, 0, inputW, 16, Component.literal("Jump Target Node"))
        jumpNodeEdit.setMaxLength(2000)
        jumpNodeEdit.setHint(Component.literal("§8Target Node ID (blank = OUT port)"))
        jumpNodeEdit.value = node.params["jumpToTargetNodeId"] ?: ""
        jumpNodeEdit.setEditable(true)
        jumpNodeEdit.active = true
        jumpNodeEdit.setResponder { node.params["jumpToTargetNodeId"] = it; onDataChanged() }

        graceTicksEdit = EditBox(font, 0, 0, inputW, 16, Component.literal("Grace Ticks"))
        graceTicksEdit.setMaxLength(2000)
        graceTicksEdit.setHint(Component.literal("§8Invulnerability ticks (default: 60)"))
        graceTicksEdit.value = node.params["gracePeriodTicks"] ?: "60"
        graceTicksEdit.setEditable(true)
        graceTicksEdit.active = true
        graceTicksEdit.setResponder { node.params["gracePeriodTicks"] = it; onDataChanged() }

        cleanTagEdit = EditBox(font, 0, 0, inputW, 16, Component.literal("Clean Story Tag"))
        cleanTagEdit.setMaxLength(2000)
        cleanTagEdit.setHint(Component.literal("§8Despawn tag (e.g. boss_mob)"))
        cleanTagEdit.value = node.params["cleanStoryTag"] ?: ""
        cleanTagEdit.setEditable(true)
        cleanTagEdit.active = true
        cleanTagEdit.setResponder { node.params["cleanStoryTag"] = it; onDataChanged() }

        // Tab 2 Controls (Transition Overlay)
        val currentStyle = node.params["transitionStyle"] ?: "BLACK_FADE"
        styleBtn = ModernButton(0, 0, inputW, 18, "Style: $currentStyle") {
            val styles = listOf("BLACK_FADE", "SCREEN_BLUR", "CUSTOM_TEXTURE")
            val idx = (styles.indexOf(node.params["transitionStyle"] ?: "BLACK_FADE") + 1) % styles.size
            val next = styles[idx]
            node.params["transitionStyle"] = next
            styleBtn.label = "Style: $next"
            onDataChanged()
        }

        durationEdit = EditBox(font, 0, 0, inputW, 16, Component.literal("Duration Ticks"))
        durationEdit.setMaxLength(2000)
        durationEdit.setHint(Component.literal("§8Duration ticks (40 ticks = 2s)"))
        durationEdit.value = node.params["transitionDurationTicks"] ?: "40"
        durationEdit.setEditable(true)
        durationEdit.active = true
        durationEdit.setResponder { node.params["transitionDurationTicks"] = it; onDataChanged() }

        titleEdit = EditBox(font, 0, 0, inputW, 16, Component.literal("Main Title"))
        titleEdit.setMaxLength(2000)
        titleEdit.setHint(Component.literal("§8e.g. Loading Checkpoint..."))
        titleEdit.value = node.params["transitionTitle"] ?: "Loading Checkpoint..."
        titleEdit.setEditable(true)
        titleEdit.active = true
        titleEdit.setResponder { node.params["transitionTitle"] = it; onDataChanged() }

        subTitleEdit = EditBox(font, 0, 0, inputW, 16, Component.literal("Subtitle"))
        subTitleEdit.setMaxLength(2000)
        subTitleEdit.setHint(Component.literal("§8e.g. Synchronizing State..."))
        subTitleEdit.value = node.params["transitionSubTitle"] ?: "Synchronizing World & Player..."
        subTitleEdit.setEditable(true)
        subTitleEdit.active = true
        subTitleEdit.setResponder { node.params["transitionSubTitle"] = it; onDataChanged() }

        tipsEdit = EditBox(font, 0, 0, inputW, 16, Component.literal("Lore Tips"))
        tipsEdit.setMaxLength(2000)
        tipsEdit.setHint(Component.literal("§8Comma-separated tips"))
        tipsEdit.value = node.params["transitionTips"] ?: "Heal party before bosses!, Save checkpoints restore player position safely."
        tipsEdit.setEditable(true)
        tipsEdit.active = true
        tipsEdit.setResponder { node.params["transitionTips"] = it; onDataChanged() }

        soundEdit = EditBox(font, 0, 0, inputW, 16, Component.literal("Audio Sound"))
        soundEdit.setMaxLength(2000)
        soundEdit.setHint(Component.literal("§8minecraft:entity.player.levelup"))
        soundEdit.value = node.params["transitionSound"] ?: "minecraft:entity.player.levelup"
        soundEdit.setEditable(true)
        soundEdit.active = true
        soundEdit.setResponder { node.params["transitionSound"] = it; onDataChanged() }
    }

    private fun getTotalContentHeight(): Int {
        return when (activeTab) {
            0 -> 330 // Data Modules Tab Total Height
            1 -> if (isSaveMode) 150 else 180 // Scope & Options / Flow
            2 -> 230 // Transition Overlay Tab Total Height
            else -> 200
        }
    }

    private fun setFocused(target: EditBox?) {
        focusedEditBox?.isFocused = false
        focusedEditBox = target
        focusedEditBox?.isFocused = true
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (activeVarPickerModal != null) {
            activeVarPickerModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            return
        }

        // Modern Dark Card Outer Container
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xEE0F172A.toInt())
        // Border outline (Cyan Highlight)
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 1, 0xFF38BDF8.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0x33FFFFFF.toInt())
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0x33FFFFFF.toInt())
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0x33FFFFFF.toInt())

        // Header Title
        val headerTitle = if (isSaveMode) "💾 Save Profile Configuration" else "📂 Load Profile Configuration"
        guiGraphics.drawString(font, headerTitle, modalX + 12, modalY + 10, 0xFF38BDF8.toInt(), true)

        closeButton.render(guiGraphics, font, mouseX, mouseY)
        modeToggleBtn?.render(guiGraphics, font, mouseX, mouseY)

        // Profile ID & Scope Card Panel
        guiGraphics.fill(modalX + 10, modalY + 26, modalX + modalWidth - 10, modalY + 50, 0xCC1E293B.toInt())
        guiGraphics.drawString(font, "Profile ID:", modalX + 18, modalY + 34, 0xFFCBD5E1.toInt(), false)
        profileIdEdit.render(guiGraphics, mouseX, mouseY, partialTick)
        scopeBtn.render(guiGraphics, font, mouseX, mouseY)

        // Tab Bar Header
        val tabY = modalY + 54
        val tabNames = if (isSaveMode) {
            listOf("📋 Data Modules", "⚙️ Scope & Options")
        } else {
            listOf("📋 Data Modules", "⚙️ Load Flow & Advanced", "🎨 Transition Overlay")
        }

        var tabX = modalX + 12
        tabNames.forEachIndexed { idx, name ->
            val isSelected = activeTab == idx
            val tabW = font.width(name) + 16
            val isHover = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + 20

            val textCol = if (isSelected) 0xFF38BDF8.toInt() else if (isHover) 0xFFFFFFFF.toInt() else 0x94A3B8.toInt()
            guiGraphics.drawString(font, name, tabX + 8, tabY + 5, textCol, false)

            if (isSelected) {
                guiGraphics.fill(tabX, tabY + 20, tabX + tabW, tabY + 22, 0xFF38BDF8.toInt())
            }
            tabX += tabW + 6
        }
        guiGraphics.fill(modalX + 10, tabY + 22, modalX + modalWidth - 10, tabY + 23, 0x22FFFFFF.toInt())

        // Content Area Card Background
        guiGraphics.fill(contentLeft, contentTop, contentRight, contentBottom, 0xCC1E293B.toInt())

        // Enable Scissor Viewport Clipping for Content Scroll
        guiGraphics.enableScissor(contentLeft, contentTop, contentRight, contentBottom)

        val scrollY = tabScrollOffsets[activeTab].toInt()
        val virtualY = contentTop + 8 - scrollY
        val inputX = modalX + 140
        val inputW = modalWidth - 165
        val toggleW = modalWidth - 40

        when (activeTab) {
            0 -> {
                // Section 1: Variables Payload Card
                var curY = virtualY
                guiGraphics.drawString(font, "📋 Story Variable Payload", contentLeft + 10, curY, 0xFF38BDF8.toInt(), false)

                curY += 14
                guiGraphics.drawString(font, "Variable Mode:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
                varModeBtn.x = inputX
                varModeBtn.y = curY
                varModeBtn.render(guiGraphics, font, mouseX, mouseY, contentTop, contentBottom)

                openVarPickerBtn.x = inputX + 112
                openVarPickerBtn.y = curY
                openVarPickerBtn.render(guiGraphics, font, mouseX, mouseY, contentTop, contentBottom)

                curY += 24
                guiGraphics.drawString(font, "Selected Keys:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
                varKeysEdit.setX(inputX)
                varKeysEdit.setY(curY)
                if (curY + 16 >= contentTop && curY <= contentBottom) {
                    varKeysEdit.render(guiGraphics, mouseX, mouseY, partialTick)
                }

                // Section 2: Player Snapshot Toggles Card
                curY += 28
                guiGraphics.drawString(font, "👤 Player State & Inventory Snapshots", contentLeft + 10, curY, 0xFF38BDF8.toInt(), false)

                curY += 14
                posToggle.render(guiGraphics, font, contentLeft + 10, curY, toggleW, 20, mouseX, mouseY, contentTop, contentBottom)

                curY += 24
                statsToggle.render(guiGraphics, font, contentLeft + 10, curY, toggleW, 20, mouseX, mouseY, contentTop, contentBottom)

                curY += 24
                invToggle.render(guiGraphics, font, contentLeft + 10, curY, toggleW, 20, mouseX, mouseY, contentTop, contentBottom)

                curY += 24
                armorToggle.render(guiGraphics, font, contentLeft + 10, curY, toggleW, 20, mouseX, mouseY, contentTop, contentBottom)

                curY += 24
                cobblemonToggle.render(guiGraphics, font, contentLeft + 10, curY, toggleW, 20, mouseX, mouseY, contentTop, contentBottom)

                // Section 3: World & Quests Snapshot Card
                curY += 28
                guiGraphics.drawString(font, "🌍 World State & Mission Snapshots", contentLeft + 10, curY, 0xFF38BDF8.toInt(), false)

                curY += 14
                worldToggle.render(guiGraphics, font, contentLeft + 10, curY, toggleW, 20, mouseX, mouseY, contentTop, contentBottom)

                curY += 24
                questToggle.render(guiGraphics, font, contentLeft + 10, curY, toggleW, 20, mouseX, mouseY, contentTop, contentBottom)
            }
            1 -> {
                var curY = virtualY
                if (isSaveMode) {
                    guiGraphics.drawString(font, "⚙️ Storage Scope Details", contentLeft + 10, curY, 0xFF38BDF8.toInt(), false)
                    curY += 16
                    val scopeText = if (node.params["scope"] == "GLOBAL") {
                        "Scope: GLOBAL WORLD\n\n- Data is stored globally for all players.\n- Save File Path:\n  saves/<world>/cobblebrain/checkpoints/GLOBAL/<profile_id>.json"
                    } else {
                        "Scope: PLAYER SPECIFIC\n\n- Data is stored per player UUID.\n- Save File Path:\n  saves/<world>/cobblebrain/checkpoints/PLAYER/<profile_id>.json"
                    }
                    val lines = font.split(Component.literal(scopeText), toggleW - 10)
                    lines.forEach { line ->
                        if (curY + 10 >= contentTop && curY <= contentBottom) {
                            guiGraphics.drawString(font, line, contentLeft + 15, curY, 0xFFCBD5E1.toInt(), false)
                        }
                        curY += 12
                    }
                } else {
                    guiGraphics.drawString(font, "⚙️ Load Flow & Execution Rules", contentLeft + 10, curY, 0xFF38BDF8.toInt(), false)

                    curY += 16
                    guiGraphics.drawString(font, "Variable Merge Mode:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
                    mergeModeBtn.x = inputX
                    mergeModeBtn.y = curY
                    mergeModeBtn.render(guiGraphics, font, mouseX, mouseY, contentTop, contentBottom)

                    curY += 24
                    guiGraphics.drawString(font, "Jump Target Node ID:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
                    jumpNodeEdit.setX(inputX)
                    jumpNodeEdit.setY(curY)
                    if (curY + 16 >= contentTop && curY <= contentBottom) {
                        jumpNodeEdit.render(guiGraphics, mouseX, mouseY, partialTick)
                    }

                    curY += 24
                    guiGraphics.drawString(font, "Grace Period Ticks:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
                    graceTicksEdit.setX(inputX)
                    graceTicksEdit.setY(curY)
                    if (curY + 16 >= contentTop && curY <= contentBottom) {
                        graceTicksEdit.render(guiGraphics, mouseX, mouseY, partialTick)
                    }

                    curY += 24
                    guiGraphics.drawString(font, "Clean Story Tag:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
                    cleanTagEdit.setX(inputX)
                    cleanTagEdit.setY(curY)
                    if (curY + 16 >= contentTop && curY <= contentBottom) {
                        cleanTagEdit.render(guiGraphics, mouseX, mouseY, partialTick)
                    }
                }
            }
            2 -> {
                if (!isSaveMode) {
                    var curY = virtualY
                    guiGraphics.drawString(font, "🎨 Transition Overlay & Screen Effects", contentLeft + 10, curY, 0xFF38BDF8.toInt(), false)

                    curY += 16
                    guiGraphics.drawString(font, "Transition Style:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
                    styleBtn.x = inputX
                    styleBtn.y = curY
                    styleBtn.render(guiGraphics, font, mouseX, mouseY, contentTop, contentBottom)

                    curY += 24
                    guiGraphics.drawString(font, "Duration Ticks:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
                    durationEdit.setX(inputX)
                    durationEdit.setY(curY)
                    if (curY + 16 >= contentTop && curY <= contentBottom) {
                        durationEdit.render(guiGraphics, mouseX, mouseY, partialTick)
                    }

                    curY += 24
                    guiGraphics.drawString(font, "Overlay Title:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
                    titleEdit.setX(inputX)
                    titleEdit.setY(curY)
                    if (curY + 16 >= contentTop && curY <= contentBottom) {
                        titleEdit.render(guiGraphics, mouseX, mouseY, partialTick)
                    }

                    curY += 24
                    guiGraphics.drawString(font, "Overlay Subtitle:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
                    subTitleEdit.setX(inputX)
                    subTitleEdit.setY(curY)
                    if (curY + 16 >= contentTop && curY <= contentBottom) {
                        subTitleEdit.render(guiGraphics, mouseX, mouseY, partialTick)
                    }

                    curY += 24
                    guiGraphics.drawString(font, "Random Lore Tips:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
                    tipsEdit.setX(inputX)
                    tipsEdit.setY(curY)
                    if (curY + 16 >= contentTop && curY <= contentBottom) {
                        tipsEdit.render(guiGraphics, mouseX, mouseY, partialTick)
                    }

                    curY += 24
                    guiGraphics.drawString(font, "Audio Sound ID:", contentLeft + 10, curY + 4, 0xFFCBD5E1.toInt(), false)
                    soundEdit.setX(inputX)
                    soundEdit.setY(curY)
                    if (curY + 16 >= contentTop && curY <= contentBottom) {
                        soundEdit.render(guiGraphics, mouseX, mouseY, partialTick)
                    }
                }
            }
        }

        // Disable Scissor Viewport
        guiGraphics.disableScissor()

        // Render Sleek Scrollbar
        val totalH = getTotalContentHeight()
        val maxScroll = (totalH - viewportH).coerceAtLeast(0)
        if (maxScroll > 0) {
            val sbX = contentRight - 5
            val scrollRatio = viewportH.toFloat() / totalH
            val thumbH = (viewportH * scrollRatio).toInt().coerceAtLeast(15)
            val thumbY = contentTop + ((tabScrollOffsets[activeTab] / maxScroll) * (viewportH - thumbH)).toInt()

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
        if (activeVarPickerModal != null) {
            return activeVarPickerModal!!.mouseClicked(mouseX, mouseY, button)
        }

        if (closeButton.mouseClicked(mouseX, mouseY, button)) return true
        if (modeToggleBtn?.mouseClicked(mouseX, mouseY, button) == true) return true
        if (scopeBtn.mouseClicked(mouseX, mouseY, button)) return true

        if (profileIdEdit.mouseClicked(mouseX, mouseY, button)) {
            setFocused(profileIdEdit)
            return true
        }

        // Check Tab Click
        val tabY = modalY + 54
        val tabNames = if (isSaveMode) {
            listOf("📋 Data Modules", "⚙️ Scope & Options")
        } else {
            listOf("📋 Data Modules", "⚙️ Load Flow & Advanced", "🎨 Transition Overlay")
        }
        var tabX = modalX + 12
        tabNames.forEachIndexed { idx, name ->
            val tabW = font.width(name) + 16
            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + 22) {
                activeTab = idx
                setFocused(null)
                return true
            }
            tabX += tabW + 6
        }

        // Check Content Area Clicks
        if (mouseX >= contentLeft && mouseX <= contentRight && mouseY >= contentTop && mouseY <= contentBottom) {
            val scrollY = tabScrollOffsets[activeTab].toInt()
            val virtualY = contentTop + 8 - scrollY
            val toggleW = modalWidth - 40

            when (activeTab) {
                0 -> {
                    var curY = virtualY + 14
                    if (varModeBtn.mouseClicked(mouseX, mouseY, button, contentTop, contentBottom)) return true
                    if (openVarPickerBtn.mouseClicked(mouseX, mouseY, button, contentTop, contentBottom)) return true

                    curY += 24
                    if (checkEditClick(varKeysEdit, mouseX, mouseY, button)) return true

                    curY += 42
                    if (posToggle.mouseClicked(mouseX, mouseY, button, contentLeft + 10, curY, toggleW, 20, contentTop, contentBottom)) return true

                    curY += 24
                    if (statsToggle.mouseClicked(mouseX, mouseY, button, contentLeft + 10, curY, toggleW, 20, contentTop, contentBottom)) return true

                    curY += 24
                    if (invToggle.mouseClicked(mouseX, mouseY, button, contentLeft + 10, curY, toggleW, 20, contentTop, contentBottom)) return true

                    curY += 24
                    if (armorToggle.mouseClicked(mouseX, mouseY, button, contentLeft + 10, curY, toggleW, 20, contentTop, contentBottom)) return true

                    curY += 24
                    if (cobblemonToggle.mouseClicked(mouseX, mouseY, button, contentLeft + 10, curY, toggleW, 20, contentTop, contentBottom)) return true

                    curY += 42
                    if (worldToggle.mouseClicked(mouseX, mouseY, button, contentLeft + 10, curY, toggleW, 20, contentTop, contentBottom)) return true

                    curY += 24
                    if (questToggle.mouseClicked(mouseX, mouseY, button, contentLeft + 10, curY, toggleW, 20, contentTop, contentBottom)) return true
                }
                1 -> {
                    if (!isSaveMode) {
                        var curY = virtualY + 16
                        if (mergeModeBtn.mouseClicked(mouseX, mouseY, button, contentTop, contentBottom)) return true

                        curY += 24
                        if (checkEditClick(jumpNodeEdit, mouseX, mouseY, button)) return true

                        curY += 24
                        if (checkEditClick(graceTicksEdit, mouseX, mouseY, button)) return true

                        curY += 24
                        if (checkEditClick(cleanTagEdit, mouseX, mouseY, button)) return true
                    }
                }
                2 -> {
                    if (!isSaveMode) {
                        var curY = virtualY + 16
                        if (styleBtn.mouseClicked(mouseX, mouseY, button, contentTop, contentBottom)) return true

                        curY += 24
                        if (checkEditClick(durationEdit, mouseX, mouseY, button)) return true

                        curY += 24
                        if (checkEditClick(titleEdit, mouseX, mouseY, button)) return true

                        curY += 24
                        if (checkEditClick(subTitleEdit, mouseX, mouseY, button)) return true

                        curY += 24
                        if (checkEditClick(tipsEdit, mouseX, mouseY, button)) return true

                        curY += 24
                        if (checkEditClick(soundEdit, mouseX, mouseY, button)) return true
                    }
                }
            }
        }

        if (mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= modalY && mouseY <= modalY + modalHeight) {
            setFocused(null)
            return true
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        return mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= modalY && mouseY <= modalY + modalHeight
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (activeVarPickerModal != null) {
            return activeVarPickerModal!!.charTyped(codePoint, modifiers)
        }
        if (focusedEditBox != null && focusedEditBox!!.charTyped(codePoint, modifiers)) {
            return true
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (activeVarPickerModal != null) {
            return activeVarPickerModal!!.keyPressed(keyCode, scanCode, modifiers)
        }
        if (keyCode == 256) { // ESC
            onClose()
            return true
        }
        if (focusedEditBox != null && focusedEditBox!!.keyPressed(keyCode, scanCode, modifiers)) {
            return true
        }
        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        if (activeVarPickerModal != null) {
            return activeVarPickerModal!!.mouseScrolled(mouseX, mouseY, scrollY)
        }
        if (mouseX >= contentLeft && mouseX <= contentRight && mouseY >= contentTop && mouseY <= contentBottom) {
            val totalH = getTotalContentHeight()
            val maxScroll = (totalH - viewportH).coerceAtLeast(0).toFloat()
            if (maxScroll > 0f) {
                val current = tabScrollOffsets[activeTab]
                val next = (current - scrollY.toFloat() * 16f).coerceIn(0f, maxScroll)
                tabScrollOffsets[activeTab] = next
                return true
            }
        }
        return mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= modalY && mouseY <= modalY + modalHeight
    }
}
