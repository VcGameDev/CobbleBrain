package vito.cobblebrain.client

import com.google.gson.Gson
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import vito.cobblebrain.config.SyncedConfig
import vito.cobblebrain.social.PokemonPersonality

class PersonalityEditScreen(
    private val parentScreen: Screen?,
    private val pokemonUuid: String,
    private val displayName: String,
    private val species: String,
    personalityJson: String
) : Screen(Component.literal("Edit Personality - $displayName")) {

    private val gson = Gson()
    private var personality = try {
        gson.fromJson(personalityJson, PokemonPersonality::class.java) ?: PokemonPersonality()
    } catch (e: Exception) {
        PokemonPersonality()
    }

    private var currentTab = 0

    private var aboutEdit: EditBox? = null
    private var addInputEdit: EditBox? = null

    private val scrollTop = IntArray(3) { 0 }

    private val scrollLeftOffsets = mutableMapOf<String, Int>()
    private val scrollRightOffsets = mutableMapOf<String, Int>()

    // Drag states
    private var isDraggingVerticalScrollbar = false
    private var isDraggingHorizontalLeft = false
    private var isDraggingHorizontalRight = false
    private var draggedItemKey: String? = null

    private var pendingInputText: String = ""

    // Layout constants
    private val listAreaTop = 97
    private val rowH = 22
    private val addAreaH = 28
    private val bottomBarH = 45
    private val complexityBarY = 28

    private fun visibleRows(): Int {
        val listAreaH = height - listAreaTop - addAreaH - bottomBarH
        return maxOf(1, listAreaH / rowH)
    }

    private fun topIndex(): Int {
        val list = if (currentTab == 1) personality.traits else personality.likes
        return scrollTop[currentTab].coerceIn(0, maxOf(0, list.size - visibleRows()))
    }

    override fun init() {
        val sw = width
        val isReadOnly = !SyncedConfig.allowClientPersonalityEditing

        clearWidgets()
        aboutEdit = null
        addInputEdit = null

        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.tab.general")) {
                saveCurrentAbout()
                currentTab = 0
                refreshWidgets()
            }.bounds(sw / 2 - 145, 45, 90, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.tab.traits_quirks")) {
                saveCurrentAbout()
                savePendingInput()
                currentTab = 1
                refreshWidgets()
            }.bounds(sw / 2 - 45, 45, 90, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.tab.likes_dislikes")) {
                saveCurrentAbout()
                savePendingInput()
                currentTab = 2
                refreshWidgets()
            }.bounds(sw / 2 + 55, 45, 90, 20).build()
        )

        val saveText = if (isReadOnly) "cobblebrain.button.close" else "cobblebrain.button.save"
        addRenderableWidget(
            Button.builder(Component.translatable(saveText)) {
                if (!isReadOnly) {
                    saveCurrentAbout()
                    savePendingInput()
                    val complexityScore = getComplexityScore()
                    if (complexityScore >= 300.0) {
                        minecraft?.setScreen(
                            ConfirmPersonalityComplexityScreen(
                                this,
                                displayName,
                                complexityScore,
                            ) {
                                persistPersonality()
                            }
                        )
                    } else {
                        persistPersonality()
                    }
                } else {
                    minecraft?.setScreen(parentScreen)
                }
            }.bounds(sw / 2 - 105, height - 35, 100, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.button.cancel")) {
                minecraft?.setScreen(parentScreen)
            }.bounds(sw / 2 + 5, height - 35, 100, 20).build()
        )

        refreshWidgets()
    }

    private fun saveCurrentAbout() {
        aboutEdit?.let { personality = personality.copy(about = it.value) }
    }

    private fun savePendingInput() {
        pendingInputText = addInputEdit?.value ?: pendingInputText
    }

    private fun getLiveAboutText(): String {
        return aboutEdit?.value ?: personality.about
    }

    private fun countWords(text: String): Int {
        return text.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .size
    }

    private fun countLetters(text: String): Int {
        return text.count { it.isLetter() }
    }

    private fun sectionScore(text: String): Double {
        return countWords(text) + (countLetters(text) / 25.0)
    }

    private fun getComplexityScore(): Double {
        return sectionScore(getLiveAboutText()) +
            sectionScore(personality.traits.joinToString(" ")) +
            sectionScore(personality.quirks.joinToString(" ")) +
            sectionScore(personality.likes.joinToString(" ")) +
            sectionScore(personality.dislikes.joinToString(" "))
    }

    private fun getComplexityLevel(score: Double): Pair<Component, Int> {
        return when {
            score <= 80.0 -> Component.translatable("cobblebrain.screen.personality_edit.complexity.light") to 0xFF4CAF50.toInt()
            score <= 180.0 -> Component.translatable("cobblebrain.screen.personality_edit.complexity.recommended") to 0xFF4CAF50.toInt()
            score < 300.0 -> Component.translatable("cobblebrain.screen.personality_edit.complexity.high") to 0xFFFFC107.toInt()
            else -> Component.translatable("cobblebrain.screen.personality_edit.complexity.very_high") to 0xFFFF5252.toInt()
        }
    }

    private fun persistPersonality() {
        val newJson = gson.toJson(personality)
        CobblebrainClientCommon.savePersonality?.invoke(pokemonUuid, newJson)
        if (parentScreen is PersonalityListScreen) {
            parentScreen.updatePersonality(pokemonUuid, personality)
        }
        minecraft?.setScreen(parentScreen)
    }

    private fun clampScroll() {
        val tab = currentTab
        val leftSize = if (tab == 1) personality.traits.size else personality.likes.size
        val rightSize = if (tab == 1) personality.quirks.size else personality.dislikes.size
        val maxItems = maxOf(leftSize, rightSize)
        scrollTop[tab] = scrollTop[tab].coerceIn(0, maxOf(0, maxItems - visibleRows()))
    }

    private fun refreshWidgets() {
        val sw = width
        val isReadOnly = !SyncedConfig.allowClientPersonalityEditing

        aboutEdit?.let { removeWidget(it) }
        addInputEdit?.let { removeWidget(it) }
        children()
            .filter { w -> w is Button && w.y >= listAreaTop - 10 && w.y < height - bottomBarH + 10 }
            .toList()
            .forEach { removeWidget(it) }

        aboutEdit = null
        addInputEdit = null

        if (currentTab == 0) {
            val wBox = 260
            val editBox = EditBox(font, sw / 2 - wBox / 2, listAreaTop + 5, wBox, 20, Component.translatable("cobblebrain.screen.personality_edit.about_desc"))
            editBox.setMaxLength(9999)
            editBox.value = personality.about
            editBox.setEditable(!isReadOnly)
            editBox.setHint(Component.translatable("cobblebrain.placeholder.write_here").withStyle { it.withColor(0xFF555555.toInt()) })
            aboutEdit = editBox
            addRenderableWidget(editBox)
        } else {
            clampScroll()
            val topIdx = topIndex()
            val listLeft  = if (currentTab == 1) personality.traits  else personality.likes
            val listRight = if (currentTab == 1) personality.quirks  else personality.dislikes
            val labelLeft  = if (currentTab == 1) Component.translatable("cobblebrain.column.traits") else Component.translatable("cobblebrain.column.likes")
            val labelRight = if (currentTab == 1) Component.translatable("cobblebrain.column.quirks") else Component.translatable("cobblebrain.column.dislikes")

            val leftX  = sw / 2 - 145
            val rightX = sw / 2 + 10
            val vis = visibleRows()

            // Left delete buttons
            for (rowIdx in 0 until vis) {
                val dataIdx = topIdx + rowIdx
                if (dataIdx >= listLeft.size) break
                if (!isReadOnly) {
                    val capturedIdx = dataIdx
                    addRenderableWidget(
                        Button.builder(Component.literal("X")) {
                            listLeft.removeAt(capturedIdx)
                            clampScroll()
                            refreshWidgets()
                        }.bounds(leftX + 113, listAreaTop + rowIdx * rowH, 20, 18).build()
                    )
                }
            }

            // Right delete buttons
            for (rowIdx in 0 until vis) {
                val dataIdx = topIdx + rowIdx
                if (dataIdx >= listRight.size) break
                if (!isReadOnly) {
                    val capturedIdx = dataIdx
                    addRenderableWidget(
                        Button.builder(Component.literal("X")) {
                            listRight.removeAt(capturedIdx)
                            clampScroll()
                            refreshWidgets()
                        }.bounds(rightX + 113, listAreaTop + rowIdx * rowH, 20, 18).build()
                    )
                }
            }

            // Add fields centered
            if (!isReadOnly) {
                val addY = height - bottomBarH - addAreaH + 4
                
                val inputW = 120
                val btnW = 65
                val gap = 4
                val totalW = inputW + gap + btnW + gap + btnW
                val startX = sw / 2 - totalW / 2

                val inputEdit = EditBox(font, startX, addY, inputW, 18, Component.translatable("cobblebrain.label.new"))
                inputEdit.setMaxLength(200)
                inputEdit.value = pendingInputText
                inputEdit.setHint(Component.translatable("cobblebrain.placeholder.write_here").withStyle { it.withColor(0xFF555555.toInt()) })
                addInputEdit = inputEdit
                addRenderableWidget(inputEdit)

                val btnLeftX = startX + inputW + gap
                addRenderableWidget(
                    Button.builder(Component.literal("+ ${labelLeft.string}")) {
                        val text = inputEdit.value.trim()
                        if (text.isNotBlank() && !listLeft.contains(text)) {
                            listLeft.add(text)
                            pendingInputText = ""
                            inputEdit.value = ""
                            scrollTop[currentTab] = maxOf(0, listLeft.size - vis)
                            refreshWidgets()
                        }
                    }.bounds(btnLeftX, addY, btnW, 18).build()
                )

                val btnRightX = btnLeftX + btnW + gap
                addRenderableWidget(
                    Button.builder(Component.literal("+ ${labelRight.string}")) {
                        val text = inputEdit.value.trim()
                        if (text.isNotBlank() && !listRight.contains(text)) {
                            listRight.add(text)
                            pendingInputText = ""
                            inputEdit.value = ""
                            scrollTop[currentTab] = maxOf(0, listRight.size - vis)
                            refreshWidgets()
                        }
                    }.bounds(btnRightX, addY, btnW, 18).build()
                )
            }
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val sw = width
        guiGraphics.drawCenteredString(font, "$displayName ($species)", sw / 2, 15, 0xFFFFA500.toInt())

        val complexityScore = getComplexityScore()
        val (complexityLabel, complexityColor) = getComplexityLevel(complexityScore)
        val complexityText = Component.literal(
            "${Component.translatable("cobblebrain.screen.personality_edit.complexity").string}: ${complexityLabel.string}  ${String.format("%.1f", complexityScore)}"
        )
        guiGraphics.drawCenteredString(font, complexityText, sw / 2, complexityBarY, complexityColor)

        val tabX = when (currentTab) {
            0 -> sw / 2 - 145
            1 -> sw / 2 - 45
            else -> sw / 2 + 55
        }
        guiGraphics.fill(tabX, 43, tabX + 90, 45, 0xFFFFA500.toInt())

        if (currentTab == 0) {
            guiGraphics.drawString(font, Component.translatable("cobblebrain.screen.personality_edit.about_desc"), sw / 2 - 130, listAreaTop - 10, 0xFFAAAAAA.toInt())
        } else {
            val listLeft  = if (currentTab == 1) personality.traits  else personality.likes
            val listRight = if (currentTab == 1) personality.quirks  else personality.dislikes
            val labelLeft  = if (currentTab == 1) Component.translatable("cobblebrain.column.traits") else Component.translatable("cobblebrain.column.likes")
            val labelRight = if (currentTab == 1) Component.translatable("cobblebrain.column.quirks") else Component.translatable("cobblebrain.column.dislikes")

            val leftX  = sw / 2 - 145
            val rightX = sw / 2 + 10
            val maxTextW = 108

            guiGraphics.drawString(font, labelLeft,  leftX,  listAreaTop - 12, 0xFFFFA500.toInt())
            guiGraphics.drawString(font, labelRight, rightX, listAreaTop - 12, 0xFFFFA500.toInt())

            val topIdx = topIndex()
            val vis = visibleRows()

            // Left column render
            for (rowIdx in 0 until vis) {
                val dataIdx = topIdx + rowIdx
                if (dataIdx >= listLeft.size) break
                val itemY = listAreaTop + rowIdx * rowH + 4
                val originalText = listLeft[dataIdx]
                val fullText = "- $originalText"
                val textWidth = font.width(fullText)

                if (textWidth <= maxTextW) {
                    guiGraphics.drawString(font, fullText, leftX, itemY, 0xFFFFFFFF.toInt())
                } else {
                    val maxShift = textWidth - maxTextW
                    val shift = (scrollLeftOffsets[originalText] ?: 0).coerceIn(0, maxShift)
                    
                    guiGraphics.enableScissor(leftX, itemY, leftX + maxTextW, itemY + font.lineHeight + 3)
                    guiGraphics.drawString(font, fullText, leftX - shift, itemY, 0xFFFFFFFF.toInt())
                    
                    // Miniature horizontal scrollbar
                    val barY = itemY + font.lineHeight + 1
                    val barH = 1
                    guiGraphics.fill(leftX, barY, leftX + maxTextW, barY + barH, 0x22FFFFFF)
                    val thumbW = maxOf(4, (maxTextW.toFloat() * maxTextW.toFloat() / textWidth.toFloat()).toInt())
                    val scrollPercent = shift.toFloat() / maxShift.toFloat()
                    val thumbX = leftX + ((maxTextW - thumbW) * scrollPercent).toInt()
                    guiGraphics.fill(thumbX, barY, thumbX + thumbW, barY + barH, 0x99FFA500.toInt())

                    guiGraphics.disableScissor()
                }
            }

            // Right column render
            for (rowIdx in 0 until vis) {
                val dataIdx = topIdx + rowIdx
                if (dataIdx >= listRight.size) break
                val itemY = listAreaTop + rowIdx * rowH + 4
                val originalText = listRight[dataIdx]
                val fullText = "- $originalText"
                val textWidth = font.width(fullText)

                if (textWidth <= maxTextW) {
                    guiGraphics.drawString(font, fullText, rightX, itemY, 0xFFFFFFFF.toInt())
                } else {
                    val maxShift = textWidth - maxTextW
                    val shift = (scrollRightOffsets[originalText] ?: 0).coerceIn(0, maxShift)
                    
                    guiGraphics.enableScissor(rightX, itemY, rightX + maxTextW, itemY + font.lineHeight + 3)
                    guiGraphics.drawString(font, fullText, rightX - shift, itemY, 0xFFFFFFFF.toInt())

                    // Miniature horizontal scrollbar
                    val barY = itemY + font.lineHeight + 1
                    val barH = 1
                    guiGraphics.fill(rightX, barY, rightX + maxTextW, barY + barH, 0x22FFFFFF)
                    val thumbW = maxOf(4, (maxTextW.toFloat() * maxTextW.toFloat() / textWidth.toFloat()).toInt())
                    val scrollPercent = shift.toFloat() / maxShift.toFloat()
                    val thumbX = rightX + ((maxTextW - thumbW) * scrollPercent).toInt()
                    guiGraphics.fill(thumbX, barY, thumbX + thumbW, barY + barH, 0x99FFA500.toInt())

                    guiGraphics.disableScissor()
                }
            }

            val maxItems = maxOf(listLeft.size, listRight.size)
            if (maxItems > vis) {
                val scrollbarX = sw / 2 + 151
                val scrollbarY = listAreaTop
                val scrollbarW = 5
                val scrollbarH = vis * rowH

                guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0x33FFFFFF)

                val thumbH = maxOf(8, (scrollbarH.toFloat() * vis.toFloat() / maxItems.toFloat()).toInt())
                val maxScrollTop = maxItems - vis
                val scrollPercent = topIdx.toFloat() / maxScrollTop.toFloat()
                val thumbY = scrollbarY + ((scrollbarH - thumbH) * scrollPercent).toInt()

                guiGraphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0xFFFFA500.toInt())
            }
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (currentTab != 0) {
            val sw = width
            val leftX  = sw / 2 - 145
            val rightX = sw / 2 + 10
            val maxTextW = 108
            val topIdx = topIndex()
            val vis = visibleRows()

            val listLeft  = if (currentTab == 1) personality.traits  else personality.likes
            val listRight = if (currentTab == 1) personality.quirks  else personality.dislikes

            // Check left column hover for horizontal scrolling
            for (rowIdx in 0 until vis) {
                val dataIdx = topIdx + rowIdx
                if (dataIdx >= listLeft.size) break
                val itemY = listAreaTop + rowIdx * rowH
                if (mouseX >= leftX && mouseX <= leftX + maxTextW && mouseY >= itemY && mouseY < itemY + rowH) {
                    val originalText = listLeft[dataIdx]
                    val fullText = "- $originalText"
                    val textWidth = font.width(fullText)
                    if (textWidth > maxTextW) {
                        val maxShift = textWidth - maxTextW
                        val currentShift = scrollLeftOffsets[originalText] ?: 0
                        val delta = if (scrollY > 0) -8 else 8
                        scrollLeftOffsets[originalText] = (currentShift + delta).coerceIn(0, maxShift)
                        return true
                    }
                }
            }

            // Check right column hover for horizontal scrolling
            for (rowIdx in 0 until vis) {
                val dataIdx = topIdx + rowIdx
                if (dataIdx >= listRight.size) break
                val itemY = listAreaTop + rowIdx * rowH
                if (mouseX >= rightX && mouseX <= rightX + maxTextW && mouseY >= itemY && mouseY < itemY + rowH) {
                    val originalText = listRight[dataIdx]
                    val fullText = "- $originalText"
                    val textWidth = font.width(fullText)
                    if (textWidth > maxTextW) {
                        val maxShift = textWidth - maxTextW
                        val currentShift = scrollRightOffsets[originalText] ?: 0
                        val delta = if (scrollY > 0) -8 else 8
                        scrollRightOffsets[originalText] = (currentShift + delta).coerceIn(0, maxShift)
                        return true
                    }
                }
            }

            // Fallback: Vertical scroll for the entire list
            val delta = if (scrollY > 0) -1 else 1
            val maxItems = maxOf(listLeft.size, listRight.size)
            scrollTop[currentTab] = (scrollTop[currentTab] + delta).coerceIn(0, maxOf(0, maxItems - visibleRows()))
            refreshWidgets()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (currentTab != 0 && button == 0) {
            val sw = width
            val leftX = sw / 2 - 145
            val rightX = sw / 2 + 10
            val maxTextW = 108
            val topIdx = topIndex()
            val vis = visibleRows()

            val listLeft  = if (currentTab == 1) personality.traits  else personality.likes
            val listRight = if (currentTab == 1) personality.quirks  else personality.dislikes

            // Check if vertical scrollbar was clicked
            val maxItems = maxOf(listLeft.size, listRight.size)
            if (maxItems > vis) {
                val scrollbarX = sw / 2 + 151
                val scrollbarY = listAreaTop
                val scrollbarW = 5
                val scrollbarH = vis * rowH

                if (mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarW && mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarH) {
                    isDraggingVerticalScrollbar = true
                    updateVerticalScrollFromMouse(mouseY, scrollbarY, scrollbarH, maxItems, vis)
                    return true
                }
            }

            // Check if horizontal scrollbar for left column items was clicked
            for (rowIdx in 0 until vis) {
                val dataIdx = topIdx + rowIdx
                if (dataIdx >= listLeft.size) break
                val itemY = listAreaTop + rowIdx * rowH + 4
                val originalText = listLeft[dataIdx]
                val textWidth = font.width("- $originalText")
                if (textWidth > maxTextW) {
                    val barY = itemY + font.lineHeight + 1
                    if (mouseX >= leftX && mouseX <= leftX + maxTextW && mouseY >= barY - 1 && mouseY <= barY + 2) {
                        isDraggingHorizontalLeft = true
                        draggedItemKey = originalText
                        updateHorizontalScrollFromMouse(mouseX, leftX, maxTextW, textWidth, true)
                        return true
                    }
                }
            }

            // Check if horizontal scrollbar for right column items was clicked
            for (rowIdx in 0 until vis) {
                val dataIdx = topIdx + rowIdx
                if (dataIdx >= listRight.size) break
                val itemY = listAreaTop + rowIdx * rowH + 4
                val originalText = listRight[dataIdx]
                val textWidth = font.width("- $originalText")
                if (textWidth > maxTextW) {
                    val barY = itemY + font.lineHeight + 1
                    if (mouseX >= rightX && mouseX <= rightX + maxTextW && mouseY >= barY - 1 && mouseY <= barY + 2) {
                        isDraggingHorizontalRight = true
                        draggedItemKey = originalText
                        updateHorizontalScrollFromMouse(mouseX, rightX, maxTextW, textWidth, false)
                        return true
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (button == 0) {
            val sw = width
            val vis = visibleRows()
            val listLeft  = if (currentTab == 1) personality.traits  else personality.likes
            val listRight = if (currentTab == 1) personality.quirks  else personality.dislikes
            val maxItems = maxOf(listLeft.size, listRight.size)

            if (isDraggingVerticalScrollbar) {
                val scrollbarY = listAreaTop
                val scrollbarH = vis * rowH
                updateVerticalScrollFromMouse(mouseY, scrollbarY, scrollbarH, maxItems, vis)
                return true
            }

            val key = draggedItemKey
            if (isDraggingHorizontalLeft && key != null) {
                val leftX = sw / 2 - 145
                val maxTextW = 108
                val textWidth = font.width("- $key")
                updateHorizontalScrollFromMouse(mouseX, leftX, maxTextW, textWidth, true)
                return true
            }

            if (isDraggingHorizontalRight && key != null) {
                val rightX = sw / 2 + 10
                val maxTextW = 108
                val textWidth = font.width("- $key")
                updateHorizontalScrollFromMouse(mouseX, rightX, maxTextW, textWidth, false)
                return true
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            isDraggingVerticalScrollbar = false
            isDraggingHorizontalLeft = false
            isDraggingHorizontalRight = false
            draggedItemKey = null
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    private fun updateVerticalScrollFromMouse(mouseY: Double, scrollbarY: Int, scrollbarH: Int, totalItems: Int, vis: Int) {
        val maxScrollTop = totalItems - vis
        val relativeY = (mouseY - scrollbarY).coerceIn(0.0, scrollbarH.toDouble())
        val scrollPercent = relativeY / scrollbarH.toDouble()
        val newOffset = (scrollPercent * totalItems).toInt().coerceIn(0, maxScrollTop)

        if (newOffset != scrollTop[currentTab]) {
            scrollTop[currentTab] = newOffset
            refreshWidgets()
        }
    }

    private fun updateHorizontalScrollFromMouse(mouseX: Double, startX: Int, maxTextW: Int, textWidth: Int, isLeft: Boolean) {
        val key = draggedItemKey ?: return
        val maxShift = textWidth - maxTextW
        val relativeX = (mouseX - startX).coerceIn(0.0, maxTextW.toDouble())
        val scrollPercent = relativeX / maxTextW.toDouble()
        val newShift = (scrollPercent * maxShift).toInt().coerceIn(0, maxShift)

        if (isLeft) {
            scrollLeftOffsets[key] = newShift
        } else {
            scrollRightOffsets[key] = newShift
        }
    }

    override fun shouldCloseOnEsc(): Boolean = true
}
