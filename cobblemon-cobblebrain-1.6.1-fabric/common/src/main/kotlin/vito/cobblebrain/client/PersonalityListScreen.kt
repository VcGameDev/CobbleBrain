package vito.cobblebrain.client

import com.google.gson.JsonParser
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import vito.cobblebrain.config.SyncedConfig

class PersonalityListScreen(
    private val parentScreen: Screen?,
    partyJson: String,
    private val noWorld: Boolean = false
) : Screen(Component.translatable("cobblebrain.screen.personality_list.title")) {

    private data class PokemonEntry(
        val uuid: String,
        val displayName: String,
        val species: String,
        val personalityJson: String,
        val memoriesJson: String = "[]",
        val inParty: Boolean
    )

    private val partyPokemons = mutableListOf<PokemonEntry>()
    private val pcPokemons = mutableListOf<PokemonEntry>()

    init {
        if (!noWorld) {
            try {
                val array = JsonParser.parseString(partyJson).asJsonArray
                for (element in array) {
                    if (element.isJsonObject) {
                        val obj = element.asJsonObject
                        val inParty = obj.get("inParty")?.asBoolean ?: true
                        val entry = PokemonEntry(
                            obj.get("uuid").asString,
                            obj.get("displayName").asString,
                            obj.get("species").asString,
                            obj.get("personalityJson").asString,
                            obj.get("memoriesJson")?.asString ?: "[]",
                            inParty
                        )
                        if (inParty) partyPokemons.add(entry) else pcPokemons.add(entry)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val rowHeight = 28
    private val startY = 48
    private val sectionHeaderHeight = 16
    private val bottomBarH = 45

    // Vertical scroll offset (index of the first visible row item across both lists combined)
    private var scrollOffset = 0
    private var isDraggingScrollbar = false

    private fun getVisibleRows(): Int {
        val availableH = height - startY - bottomBarH
        return maxOf(1, availableH / rowHeight)
    }

    private fun getCombinedRows(): List<Any> {
        val list = mutableListOf<Any>()
        if (partyPokemons.isNotEmpty()) {
            list.add(Component.translatable("cobblebrain.screen.personality_list.party"))
            list.addAll(partyPokemons)
        }
        if (pcPokemons.isNotEmpty()) {
            list.add(Component.translatable("cobblebrain.screen.personality_list.pc"))
            list.addAll(pcPokemons)
        }
        return list
    }

    private fun removePokemon(uuid: String) {
        partyPokemons.removeIf { it.uuid == uuid }
        pcPokemons.removeIf { it.uuid == uuid }
        clampScroll()
        clearWidgets()
        init()
    }

    fun updatePersonality(uuid: String, personality: vito.cobblebrain.social.PokemonPersonality, memoriesJson: String = "") {
        val newJson = com.google.gson.Gson().toJson(personality)
        val partyIdx = partyPokemons.indexOfFirst { it.uuid == uuid }
        if (partyIdx >= 0) {
            val old = partyPokemons[partyIdx]
            partyPokemons[partyIdx] = old.copy(personalityJson = newJson, memoriesJson = if (memoriesJson.isNotBlank()) memoriesJson else old.memoriesJson)
        }
        val pcIdx = pcPokemons.indexOfFirst { it.uuid == uuid }
        if (pcIdx >= 0) {
            val old = pcPokemons[pcIdx]
            pcPokemons[pcIdx] = old.copy(personalityJson = newJson, memoriesJson = if (memoriesJson.isNotBlank()) memoriesJson else old.memoriesJson)
        }
    }

    private fun clampScroll() {
        val combined = getCombinedRows()
        val vis = getVisibleRows()
        scrollOffset = scrollOffset.coerceIn(0, maxOf(0, combined.size - vis))
    }

    override fun init() {
        val screenWidth = width
        val isReadOnly = !SyncedConfig.allowClientPersonalityEditing

        // Back Button (always shown at the very bottom)
        addRenderableWidget(
            Button.builder(Component.translatable("cobblebrain.button.back")) {
                minecraft?.setScreen(parentScreen)
            }.bounds(screenWidth / 2 - 100, height - 35, 200, 20).build()
        )

        if (noWorld) return

        clampScroll()
        val x = screenWidth / 2 - 165
        val vis = getVisibleRows()
        val combined = getCombinedRows()

        var currentY = startY

        // Rebuild only the widgets that fit within the viewport
        for (i in 0 until vis) {
            val dataIdx = scrollOffset + i
            if (dataIdx >= combined.size) break
            val item = combined[dataIdx]

            if (item is PokemonEntry) {
                val btnY = currentY
                val buttonTextKey = if (isReadOnly) "cobblebrain.button.view" else "cobblebrain.button.edit"
                addRenderableWidget(
                    Button.builder(Component.translatable(buttonTextKey)) {
                        minecraft?.setScreen(
                            PersonalityEditScreen(this, item.uuid, item.displayName, item.species, item.personalityJson, item.memoriesJson)
                        )
                    }.bounds(x + 215, btnY + 2, 55, 18).build()
                )
                if (!isReadOnly) {
                    val capturedPoke = item
                    addRenderableWidget(
                        Button.builder(Component.translatable("cobblebrain.button.reset")) {
                            minecraft?.setScreen(
                                ConfirmResetScreen(this, capturedPoke.displayName, capturedPoke.uuid, capturedPoke.inParty) {
                                    CobblebrainClientCommon.deletePersonality?.invoke(capturedPoke.uuid)
                                    if (capturedPoke.inParty) {
                                        val idx = partyPokemons.indexOfFirst { it.uuid == capturedPoke.uuid }
                                        if (idx >= 0) {
                                             partyPokemons[idx] = capturedPoke.copy(
                                                 personalityJson = "{}",
                                                 memoriesJson = "[]"
                                             )
                                        }
                                        clearWidgets(); init()
                                    } else {
                                        removePokemon(capturedPoke.uuid)
                                    }
                                }
                            )
                        }.bounds(x + 273, btnY + 2, 50, 18).build()
                    )
                }
                currentY += rowHeight
            } else {
                currentY += sectionHeaderHeight
            }
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val screenWidth = width
        val isReadOnly = !SyncedConfig.allowClientPersonalityEditing

        val titleText = when {
            noWorld -> Component.translatable("cobblebrain.screen.personality_list.no_world_title")
            isReadOnly -> Component.translatable("cobblebrain.screen.personality_list.read_only_title")
            else -> Component.translatable("cobblebrain.screen.personality_list.select_title")
        }
        guiGraphics.drawCenteredString(font, titleText, screenWidth / 2, 15, 0xFFFFA500.toInt())

        if (noWorld) {
            guiGraphics.drawCenteredString(font, Component.translatable("cobblebrain.screen.personality_list.no_world_line1"), screenWidth / 2, height / 2 - 22, 0xFFCCCCCC.toInt())
            guiGraphics.drawCenteredString(font, Component.translatable("cobblebrain.screen.personality_list.no_world_line2"), screenWidth / 2, height / 2 - 10, 0xFFCCCCCC.toInt())
            return
        }

        val combined = getCombinedRows()
        if (combined.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable("cobblebrain.screen.personality_list.empty"), screenWidth / 2, height / 2 - 10, 0xFFCCCCCC.toInt())
            return
        }

        val x = screenWidth / 2 - 165
        val vis = getVisibleRows()
        var currentY = startY

        // Scissor viewport to prevent text and row highlights from overlapping Back button area
        guiGraphics.enableScissor(x - 5, startY, x + 335, height - bottomBarH + 5)

        for (i in 0 until vis) {
            val dataIdx = scrollOffset + i
            if (dataIdx >= combined.size) break
            val item = combined[dataIdx]

            if (item is PokemonEntry) {
                val isPc = !item.inParty
                val rowColor = if (isPc) 0x44003366 else 0x55000000
                guiGraphics.fill(x, currentY, x + 325, currentY + 24, rowColor)

                val nameStr = item.displayName
                val speciesStr = "(${item.species})"
                val nameColor = if (isPc) 0xFFCCDDFF.toInt() else 0xFFFFFFFF.toInt()
                val speciesColor = if (isPc) 0xFF8899BB.toInt() else 0xFFAAAAAA.toInt()

                guiGraphics.drawString(font, nameStr, x + 8, currentY + 8, nameColor)
                guiGraphics.drawString(font, speciesStr, x + 8 + font.width(nameStr) + 4, currentY + 8, speciesColor)

                currentY += rowHeight
            } else if (item is Component) {
                val itemStr = item.string
                val headerColor = if (itemStr.contains("Party") || itemStr.contains("Equipe")) 0xFFFFA500.toInt() else 0xFF88AAFF.toInt()
                guiGraphics.drawString(font, item, x, currentY + 4, headerColor)
                currentY += sectionHeaderHeight
            }
        }
        guiGraphics.disableScissor()

        // Render scrollbar on the right side if content overflows
        if (combined.size > vis) {
            val scrollbarX = x + 332
            val scrollbarY = startY
            val scrollbarW = 5
            val scrollbarH = vis * rowHeight

            // Track background
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarW, scrollbarY + scrollbarH, 0x33FFFFFF)

            // Thumb
            val thumbH = maxOf(8, (scrollbarH.toFloat() * vis.toFloat() / combined.size.toFloat()).toInt())
            val maxScrollTop = combined.size - vis
            val scrollPercent = scrollOffset.toFloat() / maxScrollTop.toFloat()
            val thumbY = scrollbarY + ((scrollbarH - thumbH) * scrollPercent).toInt()

            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0xFFFFA500.toInt())
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!noWorld) {
            val delta = if (scrollY > 0) -1 else 1
            val combined = getCombinedRows()
            val vis = getVisibleRows()
            scrollOffset = (scrollOffset + delta).coerceIn(0, maxOf(0, combined.size - vis))
            clearWidgets()
            init()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!noWorld && button == 0) {
            val sw = width
            val x = sw / 2 - 165
            val combined = getCombinedRows()
            val vis = getVisibleRows()

            if (combined.size > vis) {
                val scrollbarX = x + 332
                val scrollbarY = startY
                val scrollbarW = 5
                val scrollbarH = vis * rowHeight

                if (mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarW && mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarH) {
                    isDraggingScrollbar = true
                    updateScrollFromMouse(mouseY, scrollbarY, scrollbarH, combined.size, vis)
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (isDraggingScrollbar) {
            val sw = width
            val x = sw / 2 - 165
            val combined = getCombinedRows()
            val vis = getVisibleRows()
            val scrollbarY = startY
            val scrollbarH = vis * rowHeight

            updateScrollFromMouse(mouseY, scrollbarY, scrollbarH, combined.size, vis)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            isDraggingScrollbar = false
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    private fun updateScrollFromMouse(mouseY: Double, scrollbarY: Int, scrollbarH: Int, totalItems: Int, vis: Int) {
        val maxScrollTop = totalItems - vis
        val relativeY = (mouseY - scrollbarY).coerceIn(0.0, scrollbarH.toDouble())
        val scrollPercent = relativeY / scrollbarH.toDouble()
        val newOffset = (scrollPercent * totalItems).toInt().coerceIn(0, maxScrollTop)

        if (newOffset != scrollOffset) {
            scrollOffset = newOffset
            clearWidgets()
            init()
        }
    }

    override fun shouldCloseOnEsc(): Boolean = true
}
