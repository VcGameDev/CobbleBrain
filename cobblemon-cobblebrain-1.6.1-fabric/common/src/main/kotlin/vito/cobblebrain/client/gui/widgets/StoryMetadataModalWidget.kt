package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.StoryProject

enum class MetadataTab {
    GENERAL,
    PREREQUISITES
}

class StoryMetadataModalWidget(
    val project: StoryProject,
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    val onClose: () -> Unit,
    val onDataChanged: () -> Unit
) {

    private val modalWidth = 500.coerceAtMost(screenWidth - 20)
    private val modalHeight = 315.coerceAtMost(screenHeight - 20)
    private val modalX = maxOf(10, (screenWidth - modalWidth) / 2)
    private val modalY = maxOf(10, (screenHeight - modalHeight) / 2)

    private var activeTab: MetadataTab = MetadataTab.GENERAL

    // Scrolling support
    private var generalScrollOffset: Double = 0.0
    private var prereqScrollOffset: Double = 0.0
    private var isDraggingScrollbar: Boolean = false
    private var dragStartMouseY: Double = 0.0
    private var dragStartScrollOffset: Double = 0.0

    private val viewportY get() = modalY + 46
    private val viewportBottom get() = modalY + modalHeight - 30
    private val viewportH get() = viewportBottom - viewportY
    private val contentHGeneral = 290.0
    private val contentHPrereq = 320.0

    // Tab Header Buttons
    private val tabGeneralBtn: Button
    private val tabPrereqBtn: Button

    // Tab 1: General Info Widgets
    private val idBox: EditBox
    private val titleBox: EditBox
    private val authorBox: EditBox
    private val versionBox: EditBox
    private val descBox: EditBox

    // Tab 2: Prerequisites Widgets
    // World & Game
    private var freshWorld: Boolean
    private val freshWorldBtn: Button
    private val dimBox: EditBox
    private var gameModeIndex: Int = 0
    private val gameModes = listOf("ANY", "SURVIVAL", "ADVENTURE", "CREATIVE")
    private val gameModeBtn: Button

    // Inventory & Dependencies
    private var emptyInventory: Boolean
    private val emptyInvBtn: Button
    private val reqStoriesBox: EditBox

    // Party Constraints
    private val minPartyBox: EditBox
    private val maxPartyBox: EditBox
    private val levelCapBox: EditBox
    private val reqTypeBox: EditBox

    // Failure Handling
    private var failureAction: String
    private val failureActionBtn: Button
    private val failMsgBox: EditBox

    // Bottom Action Buttons
    private val saveButton: Button
    private val closeButton: Button

    private var focusedBox: EditBox? = null

    init {
        val prereqs = project.prerequisites

        // 1. Tab Navigation Header
        val tabW = (modalWidth - 28) / 2
        tabGeneralBtn = Button.builder(Component.literal("📋 General Metadata")) {
            activeTab = MetadataTab.GENERAL
            updateWidgetPositions()
            setFocus(titleBox)
        }.bounds(modalX + 12, modalY + 24, tabW, 18).build()

        tabPrereqBtn = Button.builder(Component.literal("🔒 Execution Prerequisites")) {
            activeTab = MetadataTab.PREREQUISITES
            updateWidgetPositions()
            setFocus(dimBox)
        }.bounds(modalX + 16 + tabW, modalY + 24, tabW, 18).build()

        // --- General Tab Setup ---
        val inputX = modalX + 15
        val inputW = modalWidth - 36

        idBox = EditBox(font, inputX, modalY + 60, inputW, 15, Component.literal("Story ID"))
        idBox.value = project.id.ifBlank { project.name }
        idBox.setMaxLength(2000)

        titleBox = EditBox(font, inputX, modalY + 96, inputW, 15, Component.literal("Title"))
        titleBox.value = project.name
        titleBox.setMaxLength(2000)

        authorBox = EditBox(font, inputX, modalY + 132, inputW, 15, Component.literal("Author"))
        authorBox.value = project.author
        authorBox.setMaxLength(2000)

        versionBox = EditBox(font, inputX, modalY + 168, inputW, 15, Component.literal("Version"))
        versionBox.value = project.version
        versionBox.setMaxLength(2000)

        descBox = EditBox(font, inputX, modalY + 204, inputW, 48, Component.literal("Description"))
        descBox.value = project.description
        descBox.setMaxLength(9999)

        // --- Prerequisites Tab Setup ---
        val colW = (modalWidth - 42) / 2
        val leftX = modalX + 14
        val rightX = modalX + 22 + colW

        // Left Column: World & Game
        freshWorld = prereqs.freshWorldOnly
        freshWorldBtn = Button.builder(Component.literal(if (freshWorld) "🌱 Fresh World Only: YES" else "🌱 Fresh World Only: NO")) {
            freshWorld = !freshWorld
            freshWorldBtn.message = Component.literal(if (freshWorld) "🌱 Fresh World Only: YES" else "🌱 Fresh World Only: NO")
        }.bounds(leftX, modalY + 64, colW, 18).build()

        dimBox = EditBox(font, leftX, modalY + 102, colW, 15, Component.literal("Dimension"))
        dimBox.value = prereqs.requiredDimension
        dimBox.setMaxLength(2000)
        dimBox.setHint(Component.literal("§8e.g. minecraft:overworld (empty = any)"))

        gameModeIndex = maxOf(0, gameModes.indexOf(prereqs.requiredGameMode.uppercase()))
        gameModeBtn = Button.builder(Component.literal("🎮 GameMode: ${gameModes[gameModeIndex]}")) {
            gameModeIndex = (gameModeIndex + 1) % gameModes.size
            gameModeBtn.message = Component.literal("🎮 GameMode: ${gameModes[gameModeIndex]}")
        }.bounds(leftX, modalY + 126, colW, 18).build()

        emptyInventory = prereqs.emptyInventoryRequired
        emptyInvBtn = Button.builder(Component.literal(if (emptyInventory) "🎒 Empty Inventory: YES" else "🎒 Empty Inventory: NO")) {
            emptyInventory = !emptyInventory
            emptyInvBtn.message = Component.literal(if (emptyInventory) "🎒 Empty Inventory: YES" else "🎒 Empty Inventory: NO")
        }.bounds(leftX, modalY + 152, colW, 18).build()

        reqStoriesBox = EditBox(font, leftX, modalY + 190, colW, 15, Component.literal("Required Stories"))
        reqStoriesBox.value = prereqs.requiredCompletedStories.joinToString(", ")
        reqStoriesBox.setMaxLength(2000)
        reqStoriesBox.setHint(Component.literal("§8e.g. story_1, story_2 (empty = none)"))

        // Right Column: Party & Failure
        minPartyBox = EditBox(font, rightX, modalY + 76, colW / 2 - 4, 15, Component.literal("Min Party"))
        minPartyBox.value = if (prereqs.minPartySize >= 0) prereqs.minPartySize.toString() else ""
        minPartyBox.setMaxLength(10)
        minPartyBox.setHint(Component.literal("§8Min (1..6)"))

        maxPartyBox = EditBox(font, rightX + colW / 2 + 4, modalY + 76, colW / 2 - 4, 15, Component.literal("Max Party"))
        maxPartyBox.value = if (prereqs.maxPartySize >= 0) prereqs.maxPartySize.toString() else ""
        maxPartyBox.setMaxLength(10)
        maxPartyBox.setHint(Component.literal("§8Max (1..6)"))

        levelCapBox = EditBox(font, rightX, modalY + 112, colW, 15, Component.literal("Level Cap"))
        levelCapBox.value = if (prereqs.partyLevelCap >= 0) prereqs.partyLevelCap.toString() else ""
        levelCapBox.setMaxLength(10)
        levelCapBox.setHint(Component.literal("§8e.g. 50 (-1 to disable)"))

        reqTypeBox = EditBox(font, rightX, modalY + 148, colW, 15, Component.literal("Required Type"))
        reqTypeBox.value = prereqs.requiredPokemonType
        reqTypeBox.setMaxLength(2000)
        reqTypeBox.setHint(Component.literal("§8e.g. fire, water (empty = any)"))

        failureAction = prereqs.failureAction
        val isAlert = failureAction.equals("ALERT_MESSAGE", ignoreCase = true)
        failureActionBtn = Button.builder(Component.literal(if (isAlert) "📢 Action: Show Warning Alert" else "🤫 Action: Silent Ignore")) {
            val nextAlert = !failureAction.equals("ALERT_MESSAGE", ignoreCase = true)
            failureAction = if (nextAlert) "ALERT_MESSAGE" else "SILENT_IGNORE"
            failureActionBtn.message = Component.literal(if (nextAlert) "📢 Action: Show Warning Alert" else "🤫 Action: Silent Ignore")
        }.bounds(rightX, modalY + 184, colW, 18).build()

        failMsgBox = EditBox(font, rightX, modalY + 222, colW, 15, Component.literal("Failure Message"))
        failMsgBox.value = prereqs.failureMessage
        failMsgBox.setMaxLength(2000)
        failMsgBox.setHint(Component.literal("§8Custom alert message to player..."))

        listOf(idBox, titleBox, authorBox, versionBox, descBox, dimBox, reqStoriesBox, minPartyBox, maxPartyBox, levelCapBox, reqTypeBox, failMsgBox).forEach {
            it.setEditable(true)
            it.active = true
        }

        // Save & Close Button
        saveButton = Button.builder(Component.literal("💾 Save & Apply")) {
            project.id = idBox.value.trim().ifBlank { project.name }
            project.name = titleBox.value.trim()
            project.author = authorBox.value.trim()
            project.version = versionBox.value.trim()
            project.description = descBox.value.trim()

            // Save Prerequisites
            prereqs.freshWorldOnly = freshWorld
            prereqs.requiredDimension = dimBox.value.trim()
            prereqs.requiredGameMode = gameModes[gameModeIndex]
            prereqs.emptyInventoryRequired = emptyInventory
            prereqs.requiredCompletedStories = reqStoriesBox.value.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()

            prereqs.minPartySize = minPartyBox.value.trim().toIntOrNull() ?: -1
            prereqs.maxPartySize = maxPartyBox.value.trim().toIntOrNull() ?: -1
            prereqs.partyLevelCap = levelCapBox.value.trim().toIntOrNull() ?: -1
            prereqs.requiredPokemonType = reqTypeBox.value.trim()
            prereqs.failureAction = failureAction
            prereqs.failureMessage = failMsgBox.value.trim()

            onDataChanged()
            onClose()
        }.bounds(modalX + modalWidth - 135, modalY + modalHeight - 24, 120, 18).build()

        closeButton = Button.builder(Component.literal("✖ Cancel")) {
            onClose()
        }.bounds(modalX + 15, modalY + modalHeight - 24, 75, 18).build()

        updateWidgetPositions()
    }

    private fun updateWidgetPositions() {
        val curGeneral = generalScrollOffset.toInt()
        idBox.y = modalY + 60 - curGeneral
        titleBox.y = modalY + 96 - curGeneral
        authorBox.y = modalY + 132 - curGeneral
        versionBox.y = modalY + 168 - curGeneral
        descBox.y = modalY + 204 - curGeneral

        val curPrereq = prereqScrollOffset.toInt()
        freshWorldBtn.y = modalY + 64 - curPrereq
        dimBox.y = modalY + 102 - curPrereq
        gameModeBtn.y = modalY + 126 - curPrereq
        emptyInvBtn.y = modalY + 152 - curPrereq
        reqStoriesBox.y = modalY + 190 - curPrereq

        minPartyBox.y = modalY + 76 - curPrereq
        maxPartyBox.y = modalY + 76 - curPrereq
        levelCapBox.y = modalY + 112 - curPrereq
        reqTypeBox.y = modalY + 148 - curPrereq
        failureActionBtn.y = modalY + 184 - curPrereq
        failMsgBox.y = modalY + 222 - curPrereq
    }

    private fun getActiveContentHeight(): Double {
        return if (activeTab == MetadataTab.GENERAL) contentHGeneral else contentHPrereq
    }

    private fun getActiveScrollOffset(): Double {
        return if (activeTab == MetadataTab.GENERAL) generalScrollOffset else prereqScrollOffset
    }

    private fun setActiveScrollOffset(value: Double) {
        val maxScroll = maxOf(0.0, getActiveContentHeight() - viewportH)
        val clamped = value.coerceIn(0.0, maxScroll)
        if (activeTab == MetadataTab.GENERAL) {
            generalScrollOffset = clamped
        } else {
            prereqScrollOffset = clamped
        }
        updateWidgetPositions()
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF14141A.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 22, 0xFF22222E.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())

        guiGraphics.drawString(font, "📋 Story Metadata & Execution Settings", modalX + 10, modalY + 6, 0xFF00FFCC.toInt(), false)

        // Render Tabs
        tabGeneralBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        tabPrereqBtn.render(guiGraphics, mouseX, mouseY, partialTick)

        val tabW = (modalWidth - 28) / 2
        val indicatorX = if (activeTab == MetadataTab.GENERAL) modalX + 12 else modalX + 16 + tabW
        guiGraphics.fill(indicatorX, modalY + 42, indicatorX + tabW, modalY + 44, 0xFF00FFCC.toInt())

        // Enable Scissor for scrollable viewport
        guiGraphics.enableScissor(modalX + 2, viewportY, modalX + modalWidth - 2, viewportBottom)

        val scroll = getActiveScrollOffset().toInt()

        when (activeTab) {
            MetadataTab.GENERAL -> {
                guiGraphics.drawString(font, "Story ID (Command identifier):", modalX + 15, modalY + 48 - scroll, 0xFFA0A0A0.toInt(), false)
                idBox.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "Display Title:", modalX + 15, modalY + 84 - scroll, 0xFFA0A0A0.toInt(), false)
                titleBox.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "Author:", modalX + 15, modalY + 120 - scroll, 0xFFA0A0A0.toInt(), false)
                authorBox.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "Version:", modalX + 15, modalY + 156 - scroll, 0xFFA0A0A0.toInt(), false)
                versionBox.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "Description:", modalX + 15, modalY + 192 - scroll, 0xFFA0A0A0.toInt(), false)
                descBox.render(guiGraphics, mouseX, mouseY, partialTick)

                val charCount = descBox.value.length
                if (charCount >= 9000) {
                    val countText = "$charCount/9999"
                    val countColor = if (charCount >= 9999) 0xFFFF5555.toInt() else 0xFFFFEE55.toInt()
                    val countW = font.width(countText)
                    val countX = modalX + modalWidth - 18 - countW
                    val countY = descBox.y + descBox.height + 2
                    guiGraphics.drawString(font, countText, countX, countY, countColor, false)
                }
            }

            MetadataTab.PREREQUISITES -> {
                val colW = (modalWidth - 42) / 2
                val leftX = modalX + 14
                val rightX = modalX + 22 + colW

                // Left Column: World & Game
                guiGraphics.drawString(font, "🌍 World & Game Conditions:", leftX, modalY + 50 - scroll, 0xFF00FFCC.toInt(), false)
                freshWorldBtn.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "🌌 Required Dimension:", leftX, modalY + 90 - scroll, 0xFFA0A0A0.toInt(), false)
                dimBox.render(guiGraphics, mouseX, mouseY, partialTick)

                gameModeBtn.render(guiGraphics, mouseX, mouseY, partialTick)

                emptyInvBtn.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "📜 Required Completed Stories:", leftX, modalY + 178 - scroll, 0xFFA0A0A0.toInt(), false)
                reqStoriesBox.render(guiGraphics, mouseX, mouseY, partialTick)

                // Right Column: Party & Failure
                guiGraphics.drawString(font, "🐾 Cobblemon Party Constraints:", rightX, modalY + 50 - scroll, 0xFF00FFCC.toInt(), false)
                guiGraphics.drawString(font, "👥 Party Size Range (Min / Max):", rightX, modalY + 64 - scroll, 0xFFA0A0A0.toInt(), false)
                minPartyBox.render(guiGraphics, mouseX, mouseY, partialTick)
                maxPartyBox.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "⭐ Party Level Cap (-1 to disable):", rightX, modalY + 100 - scroll, 0xFFA0A0A0.toInt(), false)
                levelCapBox.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "🔥 Required Elemental Type:", rightX, modalY + 136 - scroll, 0xFFA0A0A0.toInt(), false)
                reqTypeBox.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "⚠️ Failure Handling Action:", rightX, modalY + 172 - scroll, 0xFFA0A0A0.toInt(), false)
                failureActionBtn.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "💬 Custom Warning Message:", rightX, modalY + 210 - scroll, 0xFFA0A0A0.toInt(), false)
                val failCharCount = failMsgBox.value.length
                if (failCharCount >= 1800) {
                    val countText = "$failCharCount/2000"
                    val countColor = if (failCharCount >= 2000) 0xFFFF5555.toInt() else 0xFFFFEE55.toInt()
                    val countW = font.width(countText)
                    val countX = rightX + colW - countW
                    guiGraphics.drawString(font, countText, countX, modalY + 210 - scroll, countColor, false)
                }
                failMsgBox.render(guiGraphics, mouseX, mouseY, partialTick)

                guiGraphics.drawString(font, "ℹ️ If prerequisites fail, story execution is suppressed.", leftX, modalY + 250 - scroll, 0xFF888899.toInt(), false)
            }
        }

        guiGraphics.disableScissor()

        // Render Scrollbar
        val contentH = getActiveContentHeight()
        val maxScroll = maxOf(0.0, contentH - viewportH)
        if (maxScroll > 0) {
            val scrollbarX = modalX + modalWidth - 7
            val scrollbarW = 4
            guiGraphics.fill(scrollbarX, viewportY, scrollbarX + scrollbarW, viewportBottom, 0x33000000)

            val thumbH = maxOf(18.0, (viewportH.toDouble() / contentH) * viewportH)
            val thumbY = viewportY + (getActiveScrollOffset() / maxScroll) * (viewportH - thumbH)
            guiGraphics.fill(scrollbarX, thumbY.toInt(), scrollbarX + scrollbarW, (thumbY + thumbH).toInt(), 0xFF3D5AFE.toInt())
        }

        // Bottom Bar Background & Buttons
        guiGraphics.fill(modalX + 1, modalY + modalHeight - 28, modalX + modalWidth - 1, modalY + modalHeight - 1, 0xFF181822.toInt())
        saveButton.render(guiGraphics, mouseX, mouseY, partialTick)
        closeButton.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun setFocus(target: EditBox?) {
        listOf(idBox, titleBox, authorBox, versionBox, descBox, dimBox, reqStoriesBox, minPartyBox, maxPartyBox, levelCapBox, reqTypeBox, failMsgBox).forEach {
            it.isFocused = (it == target)
        }
        focusedBox = target
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        if (mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= viewportY && mouseY <= viewportBottom) {
            setActiveScrollOffset(getActiveScrollOffset() - scrollY * 18.0)
            return true
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (isDraggingScrollbar) {
            val contentH = getActiveContentHeight()
            val maxScroll = maxOf(0.0, contentH - viewportH)
            if (maxScroll > 0) {
                val thumbH = maxOf(18.0, (viewportH.toDouble() / contentH) * viewportH)
                val trackRange = viewportH - thumbH
                if (trackRange > 0) {
                    val deltaY = mouseY - dragStartMouseY
                    val scrollDelta = (deltaY / trackRange) * maxScroll
                    setActiveScrollOffset(dragStartScrollOffset + scrollDelta)
                    return true
                }
            }
        }
        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        isDraggingScrollbar = false
        return false
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (tabGeneralBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (tabPrereqBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (saveButton.mouseClicked(mouseX, mouseY, button)) return true
        if (closeButton.mouseClicked(mouseX, mouseY, button)) return true

        // Check Scrollbar Click
        val contentH = getActiveContentHeight()
        val maxScroll = maxOf(0.0, contentH - viewportH)
        if (maxScroll > 0 && mouseX >= modalX + modalWidth - 12 && mouseX <= modalX + modalWidth && mouseY >= viewportY && mouseY <= viewportBottom) {
            isDraggingScrollbar = true
            dragStartMouseY = mouseY
            dragStartScrollOffset = getActiveScrollOffset()
            return true
        }

        // Only handle viewport clicks if within viewport
        if (mouseY >= viewportY && mouseY <= viewportBottom) {
            when (activeTab) {
                MetadataTab.GENERAL -> {
                    val boxes = listOf(idBox, titleBox, authorBox, versionBox, descBox)
                    for (b in boxes) {
                        if (b.mouseClicked(mouseX, mouseY, button)) {
                            setFocus(b)
                            return true
                        }
                    }
                }

                MetadataTab.PREREQUISITES -> {
                    if (freshWorldBtn.mouseClicked(mouseX, mouseY, button)) return true
                    if (gameModeBtn.mouseClicked(mouseX, mouseY, button)) return true
                    if (emptyInvBtn.mouseClicked(mouseX, mouseY, button)) return true
                    if (failureActionBtn.mouseClicked(mouseX, mouseY, button)) return true

                    val boxes = listOf(dimBox, reqStoriesBox, minPartyBox, maxPartyBox, levelCapBox, reqTypeBox, failMsgBox)
                    for (b in boxes) {
                        if (b.mouseClicked(mouseX, mouseY, button)) {
                            setFocus(b)
                            return true
                        }
                    }
                }
            }
        }

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
        if (keyCode == 256) { // ESC
            onClose()
            return true
        }
        return false
    }
}
