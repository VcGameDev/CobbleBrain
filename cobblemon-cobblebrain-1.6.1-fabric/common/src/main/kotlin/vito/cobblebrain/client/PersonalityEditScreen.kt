package vito.cobblebrain.client

import com.google.gson.Gson
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
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
    memoriesJson: String = "[]",
    private var isMemoriesLoading: Boolean = false
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
    private var saveButton: Button? = null

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
    private val maxItemTextW = 88

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
                currentTab = 3
                refreshWidgets()
            }.bounds(startTabX + (tabW + gap) * 3, 45, tabW, 20).build()
        )

        // Bottom action buttons: Save & Cancel
        val btnW = 90
        val bottomY = height - 28
        val startBtnX = sw / 2 - btnW - 4

        if (!isReadOnly) {
            val saveBtn = Button.builder(Component.translatable("cobblebrain.button.save")) {
                saveCurrentAbout()
                savePendingInput()
                val score = calculateTotalComplexity()
                if (score >= 300.0) {
                    val currentScreen = this
                    val compName = getComplexityLevel(score).first.string
                    minecraft?.setScreen(ConfirmPersonalityComplexityScreen(currentScreen, displayName, score) {
                        persistPersonality()
                    })
                } else {
                    persistPersonality()
                }
            }.bounds(startBtnX, bottomY, btnW, 20).build()
            
            saveBtn.active = !isMemoriesLoading
            saveButton = saveBtn
            addRenderableWidget(saveBtn)
        }

        val cancelText = if (isReadOnly) Component.translatable("cobblebrain.button.close") else Component.translatable("cobblebrain.button.cancel")
        val cancelX = if (isReadOnly) sw / 2 - btnW / 2 else startBtnX + btnW + 8
        addRenderableWidget(
            Button.builder(cancelText) {
                minecraft?.setScreen(parentScreen)
            }.bounds(cancelX, bottomY, btnW, 20).build()
        )

        refreshWidgets()
    }

    private fun saveCurrentAbout() {
        aboutEdit?.let {
            personality = personality.copy(about = it.value.trim())
        }
    }

    private fun savePendingInput() {
        addInputEdit?.let {
            pendingInputText = it.value
        }
        memoryAddInputBox?.let {
            pendingMemoryText = it.value
        }
    }

    private fun calculateTotalComplexity(): Double {
        var score = 0.0
        val currentAbout = aboutEdit?.value?.trim() ?: personality.about
        score += currentAbout.length * 0.25

        personality.traits.forEach { score += it.length * 0.5 + 4.0 }
        personality.quirks.forEach { score += it.length * 0.5 + 4.0 }
        personality.likes.forEach { score += it.length * 0.4 + 3.0 }
        personality.dislikes.forEach { score += it.length * 0.4 + 3.0 }

        val activeCount = maxOf(personality.traits.size, 1) +
                          maxOf(personality.quirks.size, 1) +
                          maxOf(personality.likes.size, 1) +
                          maxOf(personality.dislikes.size, 1)

        val diversityMultiplier = 1.0 + (activeCount * 0.05)
        return score * diversityMultiplier
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

        val buttonsToRemove = children().filterIsInstance<Button>().filter { btn ->
            val isTopTab = btn.y == 45
            val isBottomBar = btn.y == height - 28
            !isTopTab && !isBottomBar
        }
        buttonsToRemove.forEach { removeWidget(it) }

        if (currentTab == 0) {
            // ================= GENERAL TAB =================
            val editW = 260
            val editH = 20
            val startX = sw / 2 - editW / 2
            val startY = listAreaTop + 5

            val box = EditBox(font, startX, startY, editW, editH, Component.translatable("cobblebrain.screen.personality_edit.about_desc"))
            box.setMaxLength(300)
            box.value = personality.about
            box.setEditable(!isReadOnly)
            aboutEdit = box
            addRenderableWidget(box)

            // Behavior Toggles: Dialogue, Actions, Personality Evolution
            if (!isReadOnly) {
                val btnW = 84
                val btnGap = 4
                val toggleStartX = sw / 2 - 130
                val btnY = listAreaTop + 35

                val talkKey = if (personality.effectiveCanTalk) "cobblebrain.button.toggle_dialogue.on" else "cobblebrain.button.toggle_dialogue.off"
                addRenderableWidget(
                    Button.builder(Component.translatable(talkKey)) {
                        saveCurrentAbout()
                        val next = !personality.effectiveCanTalk
                        personality = personality.copy(canTalk = next)
                        refreshWidgets()
                    }.tooltip(Tooltip.create(Component.translatable("cobblebrain.button.toggle_dialogue.tooltip")))
                     .bounds(toggleStartX, btnY, btnW, 20).build()
                )

                val actKey = if (personality.effectiveCanAct) "cobblebrain.button.toggle_action.on" else "cobblebrain.button.toggle_action.off"
                addRenderableWidget(
                    Button.builder(Component.translatable(actKey)) {
                        saveCurrentAbout()
                        val next = !personality.effectiveCanAct
                        personality = personality.copy(canAct = next)
                        refreshWidgets()
                    }.tooltip(Tooltip.create(Component.translatable("cobblebrain.button.toggle_action.tooltip")))
                     .bounds(toggleStartX + btnW + btnGap, btnY, btnW, 20).build()
                )

                val charKey = if (personality.effectiveCanUpdatePersonality) "cobblebrain.button.toggle_personality.on" else "cobblebrain.button.toggle_personality.off"
                addRenderableWidget(
                    Button.builder(Component.translatable(charKey)) {
                        saveCurrentAbout()
                        val next = !personality.effectiveCanUpdatePersonality
                        personality = personality.copy(canUpdatePersonality = next)
                        refreshWidgets()
                    }.tooltip(Tooltip.create(Component.translatable("cobblebrain.button.toggle_personality.tooltip")))
                     .bounds(toggleStartX + (btnW + btnGap) * 2, btnY, btnW, 20).build()
                )
            }
        } else if (currentTab == 3) {
            if (isMemoriesLoading) {
                return
            }
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
            if (editIdx != null && editIdx in memories.indices && !isReadOnly) {
                // Editing Single Memory View
                val targetMemory = memories[editIdx]
                val editW = 260
                val startX = sw / 2 - editW / 2
                val startY = listAreaTop + 15

                val memBox = EditBox(font, startX, startY, editW, 18, Component.translatable("cobblebrain.screen.personality_edit.memory_label"))
                memBox.setMaxLength(300)
                memBox.value = targetMemory.memory
                memoryEditBox = memBox
                addRenderableWidget(memBox)

                val kwBox = EditBox(font, startX, startY + 30, editW, 18, Component.translatable("cobblebrain.screen.personality_edit.keywords_label"))
                kwBox.setMaxLength(200)
                kwBox.value = targetMemory.keywords.joinToString(", ")
                kwBox.setHint(Component.translatable("cobblebrain.screen.personality_edit.keywords").withStyle { it.withColor(0xFF888888.toInt()) })
                memoryKeywordsEditBox = kwBox
                addRenderableWidget(kwBox)

                val btnY = startY + 60
                addRenderableWidget(
                    Button.builder(Component.translatable("cobblebrain.button.save")) {
                        val newText = memBox.value.trim()
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
                    val btnWAdd = 75
                    val gapAdd = 4
                    val totalW = inputW + gapAdd + btnWAdd
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
                        }.bounds(startX + inputW + gapAdd, addY, btnWAdd, 18).build()
                    )
                }
            }
        } else {
            // ================= TRAITS / LIKES TABS =================
            clampScroll()
            val topIdx = topIndex()
            val listLeft  = if (currentTab == 1) personality.traits  else personality.likes
            val listRight = if (currentTab == 1) personality.quirks  else personality.dislikes
            val listLockedLeft  = if (currentTab == 1) personality.effectiveLockedTraits  else personality.effectiveLockedLikes
            val listLockedRight = if (currentTab == 1) personality.effectiveLockedQuirks else personality.effectiveLockedDislikes
            val labelLeft  = if (currentTab == 1) Component.translatable("cobblebrain.column.traits") else Component.translatable("cobblebrain.column.likes")
            val labelRight = if (currentTab == 1) Component.translatable("cobblebrain.column.quirks") else Component.translatable("cobblebrain.column.dislikes")

            val leftX  = sw / 2 - 145
            val rightX = sw / 2 + 10
            val vis = visibleRows()

            // Left lock and delete buttons
            for (rowIdx in 0 until vis) {
                val dataIdx = topIdx + rowIdx
                if (dataIdx >= listLeft.size) break
                if (!isReadOnly) {
                    val capturedIdx = dataIdx
                    val itemText = listLeft[capturedIdx]
                    val isLocked = listLockedLeft.any { it.equals(itemText, ignoreCase = true) }
                    val lockLabel = if (isLocked) "§6🔒" else "§7🔓"
                    val lockTooltip = if (isLocked) "cobblebrain.button.lock.locked.tooltip" else "cobblebrain.button.lock.unlocked.tooltip"

                    addRenderableWidget(
                        Button.builder(Component.literal(lockLabel)) {
                            val existingIdx = listLockedLeft.indexOfFirst { it.equals(itemText, ignoreCase = true) }
                            if (existingIdx != -1) {
                                listLockedLeft.removeAt(existingIdx)
                            } else {
                                listLockedLeft.add(itemText)
                            }
                            refreshWidgets()
                        }.bounds(leftX + 92, listAreaTop + rowIdx * rowH, 20, 18)
                        .tooltip(Tooltip.create(Component.translatable(lockTooltip)))
                        .build()
                    )

                    addRenderableWidget(
                        Button.builder(Component.literal("X")) {
                            val existingIdx = listLockedLeft.indexOfFirst { it.equals(itemText, ignoreCase = true) }
                            if (existingIdx != -1) listLockedLeft.removeAt(existingIdx)
                            listLeft.removeAt(capturedIdx)
                            clampScroll()
                            refreshWidgets()
                        }.bounds(leftX + 114, listAreaTop + rowIdx * rowH, 20, 18).build()
                    )
                }
            }

            // Right lock and delete buttons
            for (rowIdx in 0 until vis) {
                val dataIdx = topIdx + rowIdx
                if (dataIdx >= listRight.size) break
                if (!isReadOnly) {
                    val capturedIdx = dataIdx
                    val itemText = listRight[capturedIdx]
                    val isLocked = listLockedRight.any { it.equals(itemText, ignoreCase = true) }
                    val lockLabel = if (isLocked) "§6🔒" else "§7🔓"
                    val lockTooltip = if (isLocked) "cobblebrain.button.lock.locked.tooltip" else "cobblebrain.button.lock.unlocked.tooltip"

                    addRenderableWidget(
                        Button.builder(Component.literal(lockLabel)) {
                            val existingIdx = listLockedRight.indexOfFirst { it.equals(itemText, ignoreCase = true) }
                            if (existingIdx != -1) {
                                listLockedRight.removeAt(existingIdx)
                            } else {
                                listLockedRight.add(itemText)
                            }
                            refreshWidgets()
                        }.bounds(rightX + 92, listAreaTop + rowIdx * rowH, 20, 18)
                        .tooltip(Tooltip.create(Component.translatable(lockTooltip)))
                        .build()
                    )

                    addRenderableWidget(
                        Button.builder(Component.literal("X")) {
                            val existingIdx = listLockedRight.indexOfFirst { it.equals(itemText, ignoreCase = true) }
                            if (existingIdx != -1) listLockedRight.removeAt(existingIdx)
                            listRight.removeAt(capturedIdx)
                            clampScroll()
                            refreshWidgets()
                        }.bounds(rightX + 114, listAreaTop + rowIdx * rowH, 20, 18).build()
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

        // Tab indicator underlines
        val tabW = 75
        val gap = 4
        val startTabX = sw / 2 - ((4 * tabW + 3 * gap) / 2)
        val selectedX = startTabX + currentTab * (tabW + gap)
        guiGraphics.fill(selectedX, 66, selectedX + tabW, 68, 0xFFFFA500.toInt())

        // Complexity score calculation
        val complexityScore = calculateTotalComplexity()
        val (compName, compColor) = getComplexityLevel(complexityScore)

        val complexityPrefix = Component.translatable("cobblebrain.screen.personality_edit.complexity").string
        val complexityText = "$complexityPrefix: ${compName.string} (${complexityScore.toInt()})"
        guiGraphics.drawCenteredString(font, complexityText, sw / 2, complexityBarY, compColor)

        val barW = 200
        val barH = 4
        val barX = sw / 2 - barW / 2
        val barY = complexityBarY + 10
        guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0x55000000)

        val maxSafeScore = 300.0
        val fillW = (barW * (complexityScore / maxSafeScore).coerceIn(0.0, 1.0)).toInt()
        guiGraphics.fill(barX, barY, barX + fillW, barY + barH, compColor)

        if (currentTab == 0) {
            val editW = 260
            val startX = sw / 2 - editW / 2
            guiGraphics.drawString(font, Component.translatable("cobblebrain.screen.personality_edit.about_desc"), startX, listAreaTop - 7, 0xFFFFA500.toInt())
        } else if (currentTab == 3) {
            val leftX = sw / 2 - 145
            val vis = visibleRows()
            val topIdx = topIndex()
            val filteredMemories = getFilteredAndSortedMemories()

            if (isMemoriesLoading) {
                guiGraphics.drawCenteredString(font, Component.translatable("cobblebrain.screen.personality_edit.loading_memories").string, sw / 2, height / 2 - 10, 0xFFFFA500.toInt())
                return
            }

            val editIdx = editingMemoryIndex
            if (editIdx != null && editIdx in memories.indices) {
                val startX = sw / 2 - 130
                val startY = listAreaTop + 15
                guiGraphics.drawString(font, Component.translatable("cobblebrain.screen.personality_edit.memory_label"), startX, startY - 10, 0xFFFFA500.toInt())
                guiGraphics.drawString(font, Component.translatable("cobblebrain.screen.personality_edit.keywords_label"), startX, startY + 20, 0xFFFFA500.toInt())
            } else {
                val favoritesCount = memories.count { it.isFavorite }
                val labelMemories = Component.translatable("cobblebrain.screen.personality_edit.memories_count", memories.size, favoritesCount).string
                guiGraphics.drawString(font, labelMemories, leftX + 150, listAreaTop - 16, 0xFFFFA500.toInt())

                val maxTextW = 195
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
                        val barScrollY = itemY + font.lineHeight + 1
                        val barScrollH = 1
                        guiGraphics.fill(leftX + 24, barScrollY, leftX + 24 + maxTextW, barScrollY + barScrollH, 0x22FFFFFF)
                        val thumbW = maxOf(4, (maxTextW.toFloat() * maxTextW.toFloat() / textWidth.toFloat()).toInt())
                        val scrollPercent = shift.toFloat() / maxShift.toFloat()
                        val thumbX = leftX + 24 + ((maxTextW - thumbW) * scrollPercent).toInt()
                        guiGraphics.fill(thumbX, barScrollY, thumbX + thumbW, barScrollY + barScrollH, 0x99FFA500.toInt())

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

                if (textWidth <= maxItemTextW) {
                    guiGraphics.drawString(font, fullText, leftX, itemY, 0xFFFFFFFF.toInt())
                } else {
                    val maxShift = textWidth - maxItemTextW
                    val shift = (scrollLeftOffsets[originalText] ?: 0).coerceIn(0, maxShift)
                    
                    guiGraphics.enableScissor(leftX, itemY, leftX + maxItemTextW, itemY + font.lineHeight + 3)
                    guiGraphics.drawString(font, fullText, leftX - shift, itemY, 0xFFFFFFFF.toInt())
                    
                    // Miniature horizontal scrollbar
                    val barScrollY = itemY + font.lineHeight + 1
                    val barScrollH = 1
                    guiGraphics.fill(leftX, barScrollY, leftX + maxItemTextW, barScrollY + barScrollH, 0x22FFFFFF)
                    val thumbW = maxOf(4, (maxItemTextW.toFloat() * maxItemTextW.toFloat() / textWidth.toFloat()).toInt())
                    val scrollPercent = shift.toFloat() / maxShift.toFloat()
                    val thumbX = leftX + ((maxItemTextW - thumbW) * scrollPercent).toInt()
                    guiGraphics.fill(thumbX, barScrollY, thumbX + thumbW, barScrollY + barScrollH, 0x99FFA500.toInt())

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

                if (textWidth <= maxItemTextW) {
                    guiGraphics.drawString(font, fullText, rightX, itemY, 0xFFFFFFFF.toInt())
                } else {
                    val maxShift = textWidth - maxItemTextW
                    val shift = (scrollRightOffsets[originalText] ?: 0).coerceIn(0, maxShift)
                    
                    guiGraphics.enableScissor(rightX, itemY, rightX + maxItemTextW, itemY + font.lineHeight + 3)
                    guiGraphics.drawString(font, fullText, rightX - shift, itemY, 0xFFFFFFFF.toInt())
                    
                    // Miniature horizontal scrollbar
                    val barScrollY = itemY + font.lineHeight + 1
                    val barScrollH = 1
                    guiGraphics.fill(rightX, barScrollY, rightX + maxItemTextW, barScrollY + barScrollH, 0x22FFFFFF)
                    val thumbW = maxOf(4, (maxItemTextW.toFloat() * maxItemTextW.toFloat() / textWidth.toFloat()).toInt())
                    val scrollPercent = shift.toFloat() / maxShift.toFloat()
                    val thumbX = rightX + ((maxItemTextW - thumbW) * scrollPercent).toInt()
                    guiGraphics.fill(thumbX, barScrollY, thumbX + thumbW, barScrollY + barScrollH, 0x99FFA500.toInt())

                    guiGraphics.disableScissor()
                }
            }

            // Vertical scrollbar for traits/likes list
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
        val vis = visibleRows()
        val topIdx = topIndex()

        if (currentTab == 3) {
            val leftX = width / 2 - 145
            val maxTextW = 195
            val filteredMemories = getFilteredAndSortedMemories()

            for (rowIdx in 0 until vis) {
                val dataIdx = topIdx + rowIdx
                if (dataIdx >= filteredMemories.size) break
                val itemY = listAreaTop + rowIdx * rowH
                if (mouseX >= leftX + 24 && mouseX <= leftX + 24 + maxTextW && mouseY >= itemY && mouseY < itemY + rowH) {
                    val (_, m) = filteredMemories[dataIdx]
                    val starPrefix = if (m.isFavorite) "★ " else ""
                    val textWidth = font.width("$starPrefix${m.memory}")
                    if (textWidth > maxTextW) {
                        val maxShift = textWidth - maxTextW
                        val currentShift = scrollMemoryOffsets[m.memory] ?: 0
                        val delta = if (scrollY > 0) -8 else 8
                        scrollMemoryOffsets[m.memory] = (currentShift + delta).coerceIn(0, maxShift)
                        return true
                    }
                }
            }
        }

        if (currentTab != 0 && currentTab != 3) {
            val sw = width
            val leftX  = sw / 2 - 145
            val rightX = sw / 2 + 10

            val listLeft  = if (currentTab == 1) personality.traits  else personality.likes
            val listRight = if (currentTab == 1) personality.quirks  else personality.dislikes

            // Check left column hover for horizontal scrolling
            for (rowIdx in 0 until vis) {
                val dataIdx = topIdx + rowIdx
                if (dataIdx >= listLeft.size) break
                val itemY = listAreaTop + rowIdx * rowH
                if (mouseX >= leftX && mouseX <= leftX + maxItemTextW && mouseY >= itemY && mouseY < itemY + rowH) {
                    val originalText = listLeft[dataIdx]
                    val fullText = "- $originalText"
                    val textWidth = font.width(fullText)
                    if (textWidth > maxItemTextW) {
                        val maxShift = textWidth - maxItemTextW
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
                if (mouseX >= rightX && mouseX <= rightX + maxItemTextW && mouseY >= itemY && mouseY < itemY + rowH) {
                    val originalText = listRight[dataIdx]
                    val fullText = "- $originalText"
                    val textWidth = font.width(fullText)
                    if (textWidth > maxItemTextW) {
                        val maxShift = textWidth - maxItemTextW
                        val currentShift = scrollRightOffsets[originalText] ?: 0
                        val delta = if (scrollY > 0) -8 else 8
                        scrollRightOffsets[originalText] = (currentShift + delta).coerceIn(0, maxShift)
                        return true
                    }
                }
            }
        }

        if (scrollY != 0.0) {
            val maxItems = when (currentTab) {
                1 -> maxOf(personality.traits.size, personality.quirks.size)
                2 -> maxOf(personality.likes.size, personality.dislikes.size)
                3 -> getFilteredAndSortedMemories().size
                else -> 0
            }
            if (maxItems > vis) {
                val delta = if (scrollY > 0) -1 else 1
                scrollTop[currentTab] = (scrollTop[currentTab] + delta).coerceIn(0, maxItems - vis)
                refreshWidgets()
                return true
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && currentTab != 0) {
            val sw = width
            val vis = visibleRows()
            val topIdx = topIndex()

            if (currentTab == 3) {
                val filteredMemories = getFilteredAndSortedMemories()

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

                // Check memory horizontal scrollbar clicks
                val leftX = sw / 2 - 145 + 24
                val maxTextW = 195
                for (rowIdx in 0 until vis) {
                    val dataIdx = topIdx + rowIdx
                    if (dataIdx >= filteredMemories.size) break
                    val itemY = listAreaTop + rowIdx * rowH + 4
                    val (_, m) = filteredMemories[dataIdx]
                    val textWidth = font.width("★ ${m.memory}")
                    if (textWidth > maxTextW) {
                        val barY = itemY + font.lineHeight + 1
                        if (mouseX >= leftX && mouseX <= leftX + maxTextW && mouseY >= barY - 1 && mouseY <= barY + 2) {
                            isDraggingHorizontalMemory = true
                            draggedItemKey = m.memory
                            updateMemoryHorizontalScrollFromMouse(mouseX, leftX, maxTextW, textWidth)
                            return true
                        }
                    }
                }
            } else {
                val leftX = sw / 2 - 145
                val rightX = sw / 2 + 10

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
                    if (textWidth > maxItemTextW) {
                        val barY = itemY + font.lineHeight + 1
                        if (mouseX >= leftX && mouseX <= leftX + maxItemTextW && mouseY >= barY - 1 && mouseY <= barY + 2) {
                            isDraggingHorizontalLeft = true
                            draggedItemKey = originalText
                            updateHorizontalScrollFromMouse(mouseX, leftX, maxItemTextW, textWidth, true)
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
                    if (textWidth > maxItemTextW) {
                        val barY = itemY + font.lineHeight + 1
                        if (mouseX >= rightX && mouseX <= rightX + maxItemTextW && mouseY >= barY - 1 && mouseY <= barY + 2) {
                            isDraggingHorizontalRight = true
                            draggedItemKey = originalText
                            updateHorizontalScrollFromMouse(mouseX, rightX, maxItemTextW, textWidth, false)
                            return true
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (button == 0 && currentTab != 0) {
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
                val textWidth = font.width("- $key")
                updateHorizontalScrollFromMouse(mouseX, leftX, maxItemTextW, textWidth, true)
                return true
            }

            if (isDraggingHorizontalRight && key != null) {
                val rightX = sw / 2 + 10
                val textWidth = font.width("- $key")
                updateHorizontalScrollFromMouse(mouseX, rightX, maxItemTextW, textWidth, false)
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
                box.setHint(Component.empty())
            } else {
                box.setHint(Component.translatable("cobblebrain.screen.personality_edit.search_placeholder").withStyle { it.withColor(0xFF888888.toInt()) })
            }
        }
    }

    fun onMemoriesLoaded(uuid: String, json: String) {
        if (uuid != pokemonUuid) return
        try {
            val memoryListType = object : com.google.gson.reflect.TypeToken<MutableList<Memory>>() {}.type
            val loaded: MutableList<Memory> = gson.fromJson(json, memoryListType) ?: mutableListOf()
            memories.clear()
            memories.addAll(loaded)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isMemoriesLoading = false
        saveButton?.active = true
        if (parentScreen is PersonalityListScreen) {
            parentScreen.onMemoriesReceived(pokemonUuid, json)
        }
        if (currentTab == 3) {
            refreshWidgets()
        }
    }

    override fun shouldCloseOnEsc(): Boolean = true
}
