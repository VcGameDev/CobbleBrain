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

    // Tabs: 0 = General, 1 = Traits & Quirks, 2 = Likes & Dislikes
    private var currentTab = 0

    // Widgets
    private var aboutEdit: EditBox? = null
    private var addInputEdit: EditBox? = null

    override fun init() {
        val screenWidth = width
        val isReadOnly = !SyncedConfig.allowClientPersonalityEditing

        clearWidgets()

        // --- Tab Selection Buttons ---
        addRenderableWidget(
            Button.builder(Component.literal("General")) {
                currentTab = 0
                refreshWidgets()
            }.bounds(screenWidth / 2 - 145, 45, 90, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Traits & Quirks")) {
                currentTab = 1
                refreshWidgets()
            }.bounds(screenWidth / 2 - 45, 45, 90, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Likes & Dislikes")) {
                currentTab = 2
                refreshWidgets()
            }.bounds(screenWidth / 2 + 55, 45, 90, 20).build()
        )

        // --- Bottom Save/Cancel Buttons ---
        val saveText = if (isReadOnly) "Close" else "Save"
        addRenderableWidget(
            Button.builder(Component.literal(saveText)) {
                if (!isReadOnly) {
                    saveCurrentAbout()
                    val newJson = gson.toJson(personality)
                    CobblebrainClientCommon.savePersonality?.invoke(pokemonUuid, newJson)
                }
                minecraft?.setScreen(parentScreen)
            }.bounds(screenWidth / 2 - 105, height - 35, 100, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Cancel")) {
                minecraft?.setScreen(parentScreen)
            }.bounds(screenWidth / 2 + 5, height - 35, 100, 20).build()
        )

        // --- Tab Content Setup ---
        refreshWidgets()
    }

    private fun saveCurrentAbout() {
        aboutEdit?.let {
            personality = personality.copy(about = it.value)
        }
    }

    private fun refreshWidgets() {
        val screenWidth = width
        val isReadOnly = !SyncedConfig.allowClientPersonalityEditing

        // Keep tabs and save/cancel buttons, clear dynamic list controls/boxes
        aboutEdit?.let { removeWidget(it) }
        addInputEdit?.let { removeWidget(it) }
        children().filter { it is Button && it.y >= 75 && it.y < height - 40 }.forEach {
            removeWidget(it)
        }

        if (currentTab == 0) {
            // General Tab: About field
            val editY = 100
            val widthBox = 260
            val editBox = EditBox(font, screenWidth / 2 - widthBox / 2, editY, widthBox, 20, Component.literal("About Description"))
            editBox.setMaxLength(500)
            editBox.value = personality.about
            editBox.setEditable(!isReadOnly)
            aboutEdit = editBox
            addRenderableWidget(editBox)
        } else {
            // Lists tabs (1 = Traits/Quirks, 2 = Likes/Dislikes)
            aboutEdit = null

            // Determine lists to edit in this tab
            val listLeft = if (currentTab == 1) personality.traits else personality.likes
            val listRight = if (currentTab == 1) personality.quirks else personality.dislikes
            val labelLeft = if (currentTab == 1) "Traits" else "Likes"
            val labelRight = if (currentTab == 1) "Quirks" else "Dislikes"

            // 1. Left List Rows
            val leftX = screenWidth / 2 - 145
            for (i in listLeft.indices) {
                val itemY = 100 + i * 22
                if (itemY > height - 85) break
                // Delete button
                if (!isReadOnly) {
                    addRenderableWidget(
                        Button.builder(Component.literal("X")) {
                            listLeft.removeAt(i)
                            refreshWidgets()
                        }.bounds(leftX + 115, itemY, 20, 18).build()
                    )
                }
            }

            // 2. Right List Rows
            val rightX = screenWidth / 2 + 10
            for (i in listRight.indices) {
                val itemY = 100 + i * 22
                if (itemY > height - 85) break
                // Delete button
                if (!isReadOnly) {
                    addRenderableWidget(
                        Button.builder(Component.literal("X")) {
                            listRight.removeAt(i)
                            refreshWidgets()
                        }.bounds(rightX + 115, itemY, 20, 18).build()
                    )
                }
            }

            // 3. Add Area at the bottom of the list area
            if (!isReadOnly) {
                val addInputY = height - 70
                val inputWidth = 140
                val inputEdit = EditBox(font, screenWidth / 2 - inputWidth - 30, addInputY, inputWidth, 18, Component.literal("New tag"))
                inputEdit.setMaxLength(40)
                addInputEdit = inputEdit
                addRenderableWidget(inputEdit)

                // Add to Left Button
                addRenderableWidget(
                    Button.builder(Component.literal("+ $labelLeft")) {
                        val text = inputEdit.value.trim()
                        if (text.isNotBlank() && !listLeft.contains(text)) {
                            listLeft.add(text)
                            inputEdit.value = ""
                            refreshWidgets()
                        }
                    }.bounds(screenWidth / 2 - 20, addInputY, 70, 18).build()
                )

                // Add to Right Button
                addRenderableWidget(
                    Button.builder(Component.literal("+ $labelRight")) {
                        val text = inputEdit.value.trim()
                        if (text.isNotBlank() && !listRight.contains(text)) {
                            listRight.add(text)
                            inputEdit.value = ""
                            refreshWidgets()
                        }
                    }.bounds(screenWidth / 2 + 55, addInputY, 70, 18).build()
                )
            }
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val screenWidth = width
        guiGraphics.drawCenteredString(font, "$displayName (${species})", screenWidth / 2, 15, 0xFFFFA500.toInt())

        // Draw active tab indicator outline
        val tabStartX = when (currentTab) {
            0 -> screenWidth / 2 - 145
            1 -> screenWidth / 2 - 45
            else -> screenWidth / 2 + 55
        }
        guiGraphics.fill(tabStartX, 43, tabStartX + 90, 45, 0xFFFFA500.toInt())

        if (currentTab == 0) {
            guiGraphics.drawString(font, "About & Description:", screenWidth / 2 - 130, 85, 0xFFAAAAAA.toInt())
        } else {
            val listLeft = if (currentTab == 1) personality.traits else personality.likes
            val listRight = if (currentTab == 1) personality.quirks else personality.dislikes
            val labelLeft = if (currentTab == 1) "Traits" else "Likes"
            val labelRight = if (currentTab == 1) "Quirks" else "Dislikes"

            // Draw Column Headers
            guiGraphics.drawString(font, labelLeft, screenWidth / 2 - 145, 85, 0xFFFFA500.toInt())
            guiGraphics.drawString(font, labelRight, screenWidth / 2 + 10, 85, 0xFFFFA500.toInt())

            // Render list items text
            val leftX = screenWidth / 2 - 145
            for (i in listLeft.indices) {
                val itemY = 100 + i * 22
                if (itemY > height - 85) break
                val truncatedText = font.plainSubstrByWidth(listLeft[i], 110)
                guiGraphics.drawString(font, "- $truncatedText", leftX, itemY + 5, 0xFFFFFFFF.toInt())
            }

            val rightX = screenWidth / 2 + 10
            for (i in listRight.indices) {
                val itemY = 100 + i * 22
                if (itemY > height - 85) break
                val truncatedText = font.plainSubstrByWidth(listRight[i], 110)
                guiGraphics.drawString(font, "- $truncatedText", rightX, itemY + 5, 0xFFFFFFFF.toInt())
            }
        }
    }

    override fun shouldCloseOnEsc(): Boolean {
        return true
    }
}
