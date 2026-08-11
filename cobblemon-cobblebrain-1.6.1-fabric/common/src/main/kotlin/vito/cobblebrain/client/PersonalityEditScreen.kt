package vito.cobblebrain.client

import com.google.gson.Gson
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import vito.cobblebrain.config.SyncedConfig
import vito.cobblebrain.social.Memory
import vito.cobblebrain.social.PokemonPersonality

class PersonalityEditScreen(
    private val parentScreen: Screen?,
    private val pokemonUuid: String,
    private val displayName: String,
    private val species: String,
    personalityJson: String,
    memoriesJson: String = "[]"
) : Screen(Component.literal("Edit Personality - $displayName")) {

    private val gson = Gson()
    private var personality = try {
        gson.fromJson(personalityJson, PokemonPersonality::class.java) ?: PokemonPersonality()
    } catch (e: Exception) {
        PokemonPersonality()
    }

    private val memories: MutableList<Memory> = try {
        val memoryListType = object : com.google.gson.reflect.TypeToken<MutableList<Memory>>() {}.type
        gson.fromJson<MutableList<Memory>>(memoriesJson, memoryListType) ?: mutableListOf()
    } catch (e: Exception) {
        mutableListOf()
    }

    private var currentTab = 0

    private var aboutEdit: EditBox? = null
    private var addInputEdit: EditBox? = null

    // Memory Diary widgets
    private var searchMemoryEditBox: EditBox? = null
    private var memoryEditBox: EditBox? = null
    private var memoryKeywordsEditBox: EditBox? = null
    private var memoryAddInputBox: EditBox? = null
    private var editingMemoryIndex: Int? = null

    private var searchMemoryQuery: String = ""

    private val scrollTop = IntArray(4) { 0 }

    private val scrollLeftOffsets = mutableMapOf<String, Int>()
    private val scrollRightOffsets = mutableMapOf<String, Int>()
    private val scrollMemoryOffsets = mutableMapOf<String, Int>()

    // Drag states
    private var isDraggingVerticalScrollbar = false
    private var isDraggingHorizontalLeft = false
    private var isDraggingHorizontalRight = false
    private var isDraggingHorizontalMemory = false
    private var draggedItemKey: String? = null

    private var pendingInputText: String = ""
    private var pendingMemoryText: String = ""

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

    private fun getFilteredAndSortedMemories(): List<Pair<Int, Memory>> {
        val query = searchMemoryQuery.trim().lowercase()
        return memories.mapIndexed { index, memory -> index to memory }
            .filter { (_, m) ->
                if (query.isBlank()) true
                else {
                    m.memory.lowercase().contains(query) ||
                    m.keywords.any { it.lowercase().contains(query) }
                }
            }
            .sortedWith(compareByDescending<Pair<Int, Memory>> { it.second.isFavorite }.thenBy { it.first })
    }

    private fun topIndex(): Int {
        val size = when (currentTab) {
            1 -> personality.traits.size
            2 -> personality.likes.size
            3 -> getFilteredAndSortedMemories().size
            else -> 0
        }
        return scrollTop[currentTab].coerceIn(0, maxOf(0, size - visibleRows()))
    }

    override fun init() {
        val sw = width
        val isReadOnly = !SyncedConfig.allowClientPersonalityEditing

        clearWidgets()
        aboutEdit = null
        addInputEdit = null
        searchMemoryEditBox = null
        memoryEditBox = null
        memoryKeywordsEditBox = null
        memoryAddInputBox = null

        val tabW = 75
        val gap = 4
        val startTabX = sw / 2 - ((4 * tabW + 3 * gap) / 2)

        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.tab.general")) {
                saveCurrentAbout()
                currentTab = 0
                refreshWidgets()
            }.bounds(startTabX, 45, tabW, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.tab.traits_quirks")) {
                saveCurrentAbout()
                savePendingInput()
                currentTab = 1
                refreshWidgets()
            }.bounds(startTabX + tabW + gap, 45, tabW, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.tab.likes_dislikes")) {
                saveCurrentAbout()
                savePendingInput()
                currentTab = 2
                refreshWidgets()
            }.bounds(startTabX + (tabW + gap) * 2, 45, tabW, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.tab.memory_diary")) {
                saveCurrentAbout()
                savePendingInput()
                currentTab = 3
                refreshWidgets()
            }.bounds(startTabX + (tabW + gap) * 3, 45, tabW, 20).build()
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
        memoryAddInputBox?.let { pendingMemoryText = it.value }
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
        val memoriesJson = gson.toJson(memories)
        CobblebrainClientCommon.savePersonality?.invoke(pokemonUuid, newJson, memoriesJson)
        if (parentScreen is PersonalityListScreen) {
            parentScreen.updatePersonality(pokemonUuid, personality, memoriesJson)
        }
        minecraft?.setScreen(parentScreen)
    }

    private fun clampScroll() {
        val tab = currentTab
        val maxItems = when (tab) {
            1 -> maxOf(personality.traits.size, personality.quirks.size)
            2 -> maxOf(personality.likes.size, personality.dislikes.size)
            3 -> getFilteredAndSortedMemories().size
            else -> 0
        }
        scrollTop[tab] = scrollTop[tab].coerceIn(0, maxOf(0, maxItems - visibleRows()))
    }

    private fun refreshWidgets() {
        val sw = width
        val isReadOnly = !SyncedConfig.allowClientPersonalityEditing

        val wasSearchFocused = searchMemoryEditBox?.isFocused == true || focused == searchMemoryEditBox
        val oldSearchCursorPos = searchMemoryEditBox?.cursorPosition ?: 0

        aboutEdit?.let { removeWidget(it) }
        addInputEdit?.let { removeWidget(it) }
        searchMemoryEditBox?.let { removeWidget(it) }
        memoryEditBox?.let { removeWidget(it) }
        memoryKeywordsEditBox?.let { removeWidget(it) }
        memoryAddInputBox?.let { removeWidget(it) }

        children()
            .filter { w -> w is Button && w.y >= listAreaTop - 25 && w.y < height - bottomBarH + 10 }
            .toList()
            .forEach { removeWidget(it) }

        aboutEdit = null
        addInputEdit = null
        searchMemoryEditBox = null
        memoryEditBox = null
        memoryKeywordsEditBox = null
        memoryAddInputBox = null

        if (currentTab == 0) {
            val wBox = 260
            val editBox = EditBox(font, sw / 2 - wBox / 2, listAreaTop + 5, wBox, 20, Component.translatable("cobblebrain.screen.personality_edit.about_desc"))
            editBox.setMaxLength(9999)
            editBox.value = personality.about
            editBox.setEditable(!isReadOnly)
            editBox.setHint(Component.translatable("cobblebrain.placeholder.write_here").withStyle { it.withColor(0xFF555555.toInt()) })
            aboutEdit = editBox
            addRenderableWidget(editBox)
        } else if (currentTab == 3) {
            // ================= MEMORY DIARY TAB =================
            val leftX = sw / 2 - 145

            // Search Box at top left
            val searchBox = EditBox(font, leftX, listAreaTop - 21, 140, 16, Component.translatable("cobblebrain.screen.personality_edit.search_placeholder"))
            searchBox.setMaxLength(100)
            searchBox.value = searchMemoryQuery
            if (!wasSearchFocused && searchMemoryQuery.isEmpty()) {
                searchBox.setHint(Component.translatable("cobblebrain.screen.personality_edit.search_placeholder").withStyle { it.withColor(0xFF888888.toInt()) })
            }
            searchBox.setResponder { text ->
                if (searchMemoryQuery != text) {
                    searchMemoryQuery = text
                    clampScroll()
                    refreshWidgets()
                }
            }
            searchMemoryEditBox = searchBox
            addRenderableWidget(searchBox)

            if (wasSearchFocused) {
                searchBox.isFocused = true
                setFocused(searchBox)
                searchBox.setCursorPosition(oldSearchCursorPos.coerceIn(0, searchMemoryQuery.length))
            }

            clampScroll()
            val vis = visibleRows()
            val topIdx = topIndex()
            val filteredMemories = getFilteredAndSortedMemories()

            val editIdx = editingMemoryIndex
            if (editIdx != null && editIdx in memories.indices) {
                // Editing mode for specific memory with explicit labels (Memory + Keywords)
                val targetMemory = memories[editIdx]
                val boxW = 260
                val boxX = sw / 2 - boxW / 2

                val editBox = EditBox(font, boxX, listAreaTop + 14, boxW, 18, Component.translatable("cobblebrain.screen.personality_edit.memory_label"))
                editBox.setMaxLength(500)
                editBox.value = targetMemory.memory
                editBox.setEditable(!isReadOnly)
                editBox.setCursorPosition(0) // Guarantee cursor is at the start!
                memoryEditBox = editBox
                addRenderableWidget(editBox)

                val kwBox = EditBox(font, boxX, listAreaTop + 48, boxW, 18, Component.translatable("cobblebrain.screen.personality_edit.keywords_label"))
                kwBox.setMaxLength(200)
                kwBox.value = targetMemory.keywords.joinToString(", ")
                kwBox.setEditable(!isReadOnly)
                kwBox.setCursorPosition(0)
                memoryKeywordsEditBox = kwBox
                addRenderableWidget(kwBox)

                val btnY = listAreaTop + 74
                addRenderableWidget(
                    Button.builder(Component.translatable("cobblebrain.button.save")) {
                        val newText = editBox.value.trim()
                        val newKw = kwBox.value.split(",")
                            .map { it.trim().lowercase() }
                            .filter { it.isNotBlank() }

                        if (newText.isNotBlank()) {
                            memories[editIdx] = targetMemory.copy(
                                memory = newText,
                                keywords = newKw
                            )
                        }
                        editingMemoryIndex = null
                        refreshWidgets()
                    }.bounds(sw / 2 - 65, btnY, 60, 18).build()
                )

                addRenderableWidget(
                    Button.builder(Component.translatable("cobblebrain.button.cancel")) {
                        editingMemoryIndex = null
                        refreshWidgets()
                    }.bounds(sw / 2 + 5, btnY, 60, 18).build()
                )
            } else {
                // Normal Memory Diary List View
                for (rowIdx in 0 until vis) {
                    val dataIdx = topIdx + rowIdx
                    if (dataIdx >= filteredMemories.size) break

                    val itemY = listAreaTop + rowIdx * rowH
                    val (origIndex, mem) = filteredMemories[dataIdx]

                    // Favorite Toggle Button [★] / [☆]
                    if (!isReadOnly) {
                        val starLabel = if (mem.isFavorite) "★" else "☆"
                        addRenderableWidget(
                            Button.builder(Component.literal(starLabel)) {
                                memories[origIndex] = memories[origIndex].copy(isFavorite = !memories[origIndex].isFavorite)
                                clampScroll()
                                refreshWidgets()
                            }.bounds(leftX, itemY, 20, 18).build()
                        )

                        // Edit Memory Button
                        addRenderableWidget(
                            Button.builder(Component.translatable("cobblebrain.button.edit")) {
                                editingMemoryIndex = origIndex
                                refreshWidgets()
                            }.bounds(leftX + 225, itemY, 35, 18).build()
                        )

                        // Delete Memory Button
                        addRenderableWidget(
                            Button.builder(Component.literal("X")) {
                                memories.removeAt(origIndex)
                                clampScroll()
                                refreshWidgets()
                            }.bounds(leftX + 265, itemY, 20, 18).build()
                        )
                    }
                }

                // Add Memory Input Field at bottom
                if (!isReadOnly) {
                    val addY = height - bottomBarH - addAreaH + 4
                    val inputW = 190
                    val btnW = 75
                    val gap = 4
                    val totalW = inputW + gap + btnW
                    val startX = sw / 2 - totalW / 2

                    val memInputEdit = EditBox(font, startX, addY, inputW, 18, Component.translatable("cobblebrain.screen.personality_edit.memory_label"))
                    memInputEdit.setMaxLength(300)
                    memInputEdit.value = pendingMemoryText
                    memInputEdit.setHint(Component.translatable("cobblebrain.screen.personality_edit.memory_text").withStyle { it.withColor(0xFF555555.toInt()) })
                    memoryAddInputBox = memInputEdit
                    addRenderableWidget(memInputEdit)

                    addRenderableWidget(
                        Button.builder(Component.translatable("cobblebrain.screen.personality_edit.add_memory")) {
                            val text = memInputEdit.value.trim()
                            if (text.isNotBlank()) {
                                val extractedKeywords = text.lowercase()
                                    .split(Regex("[^a-zA-Z0-9áéíóúâêîôûãõç\\-]+"))
                                    .filter { it.length >= 3 }
                                    .distinct()

                                memories.add(
                                    Memory(
                                        participants = listOf(displayName),
                                        memory = text,
                                        keywords = extractedKeywords,
                                        createdTick = System.currentTimeMillis(),
                                        playerMessage = "",
                                        isFavorite = false
                                    )
                                )
                                memInputEdit.value = ""
                                pendingMemoryText = ""
                                clampScroll()
                                refreshWidgets()
                            }
                        }.bounds(startX + inputW + gap, addY, btnW, 18).build()
                    )
                }
            }
        } else {
            // ================= TRAITS / LIKES TABS =================
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

        val tabW = 75
        val gap = 4
        val startTabX = sw / 2 - ((4 * tabW + 3 * gap) / 2)
        val tabX = startTabX + currentTab * (tabW + gap)
        guiGraphics.fill(tabX, 43, tabX + tabW, 45, 0xFFFFA500.toInt())

        if (currentTab == 0) {
            guiGraphics.drawString(font, Component.translatable("cobblebrain.screen.personality_edit.about_desc"), sw / 2 - 130, listAreaTop - 10, 0xFFAAAAAA.toInt())
        } else if (currentTab == 3) {
            val leftX = sw / 2 - 145
            val vis = visibleRows()
            val topIdx = topIndex()
            val filteredMemories = getFilteredAndSortedMemories()
            val favCount = memories.count { it.isFavorite }

            // Memories Counter Text on top right
            val headerText = Component.translatable("cobblebrain.screen.personality_edit.memories_count", filteredMemories.size, favCount)
            guiGraphics.drawString(font, headerText, sw / 2 + 5, listAreaTop - 17, 0xFFFFA500.toInt())

            val editIdx = editingMemoryIndex
            if (editIdx != null && editIdx in memories.indices) {
                // Draw explicit labels in editing mode
                val boxW = 260
                val boxX = sw / 2 - boxW / 2

                val memLabel = Component.translatable("cobblebrain.screen.personality_edit.memory_label")
                val kwLabel = Component.translatable("cobblebrain.screen.personality_edit.keywords_label")

                guiGraphics.drawString(font, memLabel, boxX, listAreaTop + 3, 0xFFFFA500.toInt())
                guiGraphics.drawString(font, kwLabel, boxX, listAreaTop + 37, 0xFFFFA500.toInt())
            } else {
                val maxTextW = 195

                // Render memory rows with horizontal scroll and scissor
                for (rowIdx in 0 until vis) {
                    val dataIdx = topIdx + rowIdx
                    if (dataIdx >= filteredMemories.size) break
                    val itemY = listAreaTop + rowIdx * rowH + 4
                    val (_, m) = filteredMemories[dataIdx]
                    val starPrefix = if (m.isFavorite) "★ " else ""
                    val fullText = "$starPrefix${m.memory}"
                    val textColor = if (m.isFavorite) 0xFFFFD700.toInt() else 0xFFFFFFFF.toInt()
                    val textWidth = font.width(fullText)

                    if (textWidth <= maxTextW) {
                        guiGraphics.enableScissor(leftX + 24, itemY, leftX + 24 + maxTextW, itemY + font.lineHeight + 3)
                        guiGraphics.drawString(font, fullText, leftX + 24, itemY, textColor)
                        guiGraphics.disableScissor()
                    } else {
                        val maxShift = textWidth - maxTextW
                        val shift = (scrollMemoryOffsets[m.memory] ?: 0).coerceIn(0, maxShift)

                        guiGraphics.enableScissor(leftX + 24, itemY, leftX + 24 + maxTextW, itemY + font.lineHeight + 3)
                        guiGraphics.drawString(font, fullText, leftX + 24 - shift, itemY, textColor)

                        // Miniature horizontal scrollbar for memory row
                        val barY = itemY + font.lineHeight + 1
                        val barH = 1
                        guiGraphics.fill(leftX + 24, barY, leftX + 24 + maxTextW, barY + barH, 0x22FFFFFF)
                        val thumbW = maxOf(4, (maxTextW.toFloat() * maxTextW.toFloat() / textWidth.toFloat()).toInt())
                        val scrollPercent = shift.toFloat() / maxShift.toFloat()
                        val thumbX = leftX + 24 + ((maxTextW - thumbW) * scrollPercent).toInt()
                        guiGraphics.fill(thumbX, barY, thumbX + thumbW, barY + barH, 0x99FFA500.toInt())

                        guiGraphics.disableScissor()
                    }
                }

                // Vertical scrollbar for memory list
                if (filteredMemories.size > vis) {
                    val scrollbarX = sw / 2 + 151
                    val scrollbarY = listAreaTop
                    val scrollbarW = 5
                    val scrollbarH = vis * rowH

                    guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0x33FFFFFF)

                    val thumbH = maxOf(8, (scrollbarH.toFloat() * vis.toFloat() / filteredMemories.size.toFloat()).toInt())
                    val maxScrollTop = filteredMemories.size - vis
                    val scrollPercent = topIdx.toFloat() / maxScrollTop.toFloat()
                    val thumbY = scrollbarY + ((scrollbarH - thumbH) * scrollPercent).toInt()

                    guiGraphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0xFFFFA500.toInt())
                }
            }
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
        if (currentTab == 3 && editingMemoryIndex == null) {
            val sw = width
            val leftX = sw / 2 - 145
            val maxTextW = 195
            val topIdx = topIndex()
            val vis = visibleRows()
            val filteredMemories = getFilteredAndSortedMemories()

            // Check hover over memory row for horizontal text scrolling
            for (rowIdx in 0 until vis) {
                val dataIdx = topIdx + rowIdx
                if (dataIdx >= filteredMemories.size) break
                val itemY = listAreaTop + rowIdx * rowH
                if (mouseX >= leftX + 24 && mouseX <= leftX + 24 + maxTextW && mouseY >= itemY && mouseY < itemY + rowH) {
                    val (_, m) = filteredMemories[dataIdx]
                    val starPrefix = if (m.isFavorite) "★ " else ""
                    val fullText = "$starPrefix${m.memory}"
                    val textWidth = font.width(fullText)
                    if (textWidth > maxTextW) {
                        val maxShift = textWidth - maxTextW
                        val currentShift = scrollMemoryOffsets[m.memory] ?: 0
                        val delta = if (scrollY > 0) -8 else 8
                        scrollMemoryOffsets[m.memory] = (currentShift + delta).coerceIn(0, maxShift)
                        return true
                    }
                }
            }

            // Fallback: Vertical scroll for memory list
            val delta = if (scrollY > 0) -1 else 1
            scrollTop[3] = (scrollTop[3] + delta).coerceIn(0, maxOf(0, filteredMemories.size - visibleRows()))
            refreshWidgets()
            return true
        }

        if (currentTab != 0 && currentTab != 3) {
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
            val vis = visibleRows()

            if (currentTab == 3) {
                val filteredMemories = getFilteredAndSortedMemories()
                val leftX = sw / 2 - 145
                val maxTextW = 195
                val topIdx = topIndex()

                // Check vertical scrollbar click
                if (filteredMemories.size > vis) {
                    val scrollbarX = sw / 2 + 151
                    val scrollbarY = listAreaTop
                    val scrollbarW = 5
                    val scrollbarH = vis * rowH

                    if (mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarW && mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarH) {
                        isDraggingVerticalScrollbar = true
                        updateVerticalScrollFromMouse(mouseY, scrollbarY, scrollbarH, filteredMemories.size, vis)
                        return true
                    }
                }

                // Check memory row horizontal scrollbar click
                for (rowIdx in 0 until vis) {
                    val dataIdx = topIdx + rowIdx
                    if (dataIdx >= filteredMemories.size) break
                    val itemY = listAreaTop + rowIdx * rowH + 4
                    val (_, m) = filteredMemories[dataIdx]
                    val starPrefix = if (m.isFavorite) "★ " else ""
                    val fullText = "$starPrefix${m.memory}"
                    val textWidth = font.width(fullText)

                    if (textWidth > maxTextW) {
                        val barY = itemY + font.lineHeight + 1
                        if (mouseX >= leftX + 24 && mouseX <= leftX + 24 + maxTextW && mouseY >= barY - 1 && mouseY <= barY + 2) {
                            isDraggingHorizontalMemory = true
                            draggedItemKey = m.memory
                            updateMemoryHorizontalScrollFromMouse(mouseX, leftX + 24, maxTextW, textWidth)
                            return true
                        }
                    }
                }
            } else {
                val leftX = sw / 2 - 145
                val rightX = sw / 2 + 10
                val maxTextW = 108
                val topIdx = topIndex()

                val listLeft  = if (currentTab == 1) personality.traits  else personality.likes
                val listRight = if (currentTab == 1) personality.quirks  else personality.dislikes

                // Check vertical scrollbar click
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

                // Check left column horizontal scrollbar
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

                // Check right column horizontal scrollbar
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
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (button == 0) {
            val sw = width
            val vis = visibleRows()
            val maxItems = if (currentTab == 3) getFilteredAndSortedMemories().size else {
                val listLeft  = if (currentTab == 1) personality.traits  else personality.likes
                val listRight = if (currentTab == 1) personality.quirks  else personality.dislikes
                maxOf(listLeft.size, listRight.size)
            }

            if (isDraggingVerticalScrollbar) {
                val scrollbarY = listAreaTop
                val scrollbarH = vis * rowH
                updateVerticalScrollFromMouse(mouseY, scrollbarY, scrollbarH, maxItems, vis)
                return true
            }

            val key = draggedItemKey
            if (isDraggingHorizontalMemory && key != null) {
                val leftX = sw / 2 - 145 + 24
                val maxTextW = 195
                val textWidth = font.width("★ $key")
                updateMemoryHorizontalScrollFromMouse(mouseX, leftX, maxTextW, textWidth)
                return true
            }

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
            isDraggingHorizontalMemory = false
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

    private fun updateMemoryHorizontalScrollFromMouse(mouseX: Double, startX: Int, maxTextW: Int, textWidth: Int) {
        val key = draggedItemKey ?: return
        val maxShift = textWidth - maxTextW
        val relativeX = (mouseX - startX).coerceIn(0.0, maxTextW.toDouble())
        val scrollPercent = relativeX / maxTextW.toDouble()
        val newShift = (scrollPercent * maxShift).toInt().coerceIn(0, maxShift)
        scrollMemoryOffsets[key] = newShift
    }

    override fun tick() {
        super.tick()

        searchMemoryEditBox?.let { box ->
            if (box.isFocused || box.value.isNotEmpty()) {
                box.setHint(null)
            } else {
                box.setHint(Component.translatable("cobblebrain.screen.personality_edit.search_placeholder").withStyle { it.withColor(0xFF888888.toInt()) })
            }
        }
    }

    override fun shouldCloseOnEsc(): Boolean = true
}
