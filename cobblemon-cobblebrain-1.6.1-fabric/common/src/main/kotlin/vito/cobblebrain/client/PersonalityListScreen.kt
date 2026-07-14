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
) : Screen(Component.literal("Party Personality Editor")) {

    private class PokemonEntry(
        val uuid: String,
        val displayName: String,
        val species: String,
        val personalityJson: String,
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
    private val startY = 55
    private val sectionHeaderHeight = 16

    /** Compute rows for a section given list size */
    private fun sectionHeight(list: List<PokemonEntry>): Int {
        return if (list.isEmpty()) rowHeight else list.size * rowHeight
    }

    /** Remove a Pokémon from both lists (after reset) and re-init */
    private fun removePokemon(uuid: String) {
        partyPokemons.removeIf { it.uuid == uuid }
        pcPokemons.removeIf { it.uuid == uuid }
        clearWidgets()
        init()
    }

    override fun init() {
        val screenWidth = width
        val isReadOnly = !SyncedConfig.allowClientPersonalityEditing

        // Back Button (always shown)
        addRenderableWidget(
            Button.builder(Component.literal("Back")) {
                minecraft?.setScreen(parentScreen)
            }.bounds(screenWidth / 2 - 100, height - 35, 200, 20).build()
        )

        if (noWorld) return  // No pokemon buttons when not in world

        val x = screenWidth / 2 - 150
        var currentY = startY

        // --- Party Pokémon buttons ---
        if (partyPokemons.isNotEmpty()) {
            currentY += sectionHeaderHeight  // space for section label
            for (poke in partyPokemons) {
                val btnY = currentY
                val buttonText = if (isReadOnly) "View" else "Edit"
                addRenderableWidget(
                    Button.builder(Component.literal(buttonText)) {
                        minecraft?.setScreen(
                            PersonalityEditScreen(this, poke.uuid, poke.displayName, poke.species, poke.personalityJson)
                        )
                    }.bounds(x + 215, btnY + 2, 55, 18).build()
                )
                if (!isReadOnly) {
                    addRenderableWidget(
                        Button.builder(Component.literal("Reset")) {
                            minecraft?.setScreen(
                                ConfirmResetScreen(this, poke.displayName, poke.uuid, poke.inParty) {
                                    CobblebrainClientCommon.deletePersonality?.invoke(poke.uuid)
                                    // Party Pokémon: just clear personality locally, keep in list
                                    val idx = partyPokemons.indexOfFirst { it.uuid == poke.uuid }
                                    if (idx >= 0) {
                                        partyPokemons[idx] = PokemonEntry(
                                            poke.uuid, poke.displayName, poke.species,
                                            "{}", true
                                        )
                                    }
                                    clearWidgets(); init()
                                }
                            )
                        }.bounds(x + 273, btnY + 2, 50, 18).build()
                    )
                }
                currentY += rowHeight
            }
        }

        // --- PC Pokémon buttons (only if there are any) ---
        if (pcPokemons.isNotEmpty()) {
            currentY += sectionHeaderHeight  // space for section label
            for (poke in pcPokemons) {
                val btnY = currentY
                val buttonText = if (isReadOnly) "View" else "Edit"
                addRenderableWidget(
                    Button.builder(Component.literal(buttonText)) {
                        minecraft?.setScreen(
                            PersonalityEditScreen(this, poke.uuid, poke.displayName, poke.species, poke.personalityJson)
                        )
                    }.bounds(x + 215, btnY + 2, 55, 18).build()
                )
                if (!isReadOnly) {
                    val capturedPoke = poke
                    addRenderableWidget(
                        Button.builder(Component.literal("Reset")) {
                            minecraft?.setScreen(
                                ConfirmResetScreen(this, capturedPoke.displayName, capturedPoke.uuid, capturedPoke.inParty) {
                                    CobblebrainClientCommon.deletePersonality?.invoke(capturedPoke.uuid)
                                    removePokemon(capturedPoke.uuid)
                                }
                            )
                        }.bounds(x + 273, btnY + 2, 50, 18).build()
                    )
                }
                currentY += rowHeight
            }
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val screenWidth = width
        val isReadOnly = !SyncedConfig.allowClientPersonalityEditing

        // Header
        val titleText = when {
            noWorld -> "Personality Editor"
            isReadOnly -> "Personality List (READ ONLY)"
            else -> "Select a Pokémon to Edit"
        }
        guiGraphics.drawCenteredString(font, titleText, screenWidth / 2, 15, 0xFFFFA500.toInt())

        // No-world message
        if (noWorld) {
            guiGraphics.drawCenteredString(
                font,
                "You must be inside a world to edit",
                screenWidth / 2, height / 2 - 22, 0xFFCCCCCC.toInt()
            )
            guiGraphics.drawCenteredString(
                font,
                "Pokémon personalities.",
                screenWidth / 2, height / 2 - 10, 0xFFCCCCCC.toInt()
            )
            return
        }

        val x = screenWidth / 2 - 150
        var currentY = startY

        // Draw empty state
        if (partyPokemons.isEmpty() && pcPokemons.isEmpty()) {
            guiGraphics.drawCenteredString(font, "No Pokémon to show.", screenWidth / 2, height / 2 - 10, 0xFFCCCCCC.toInt())
            return
        }

        // --- Party section ---
        if (partyPokemons.isNotEmpty()) {
            guiGraphics.drawString(font, "⚔ Party", x, currentY, 0xFFFFA500.toInt())
            currentY += sectionHeaderHeight

            for (poke in partyPokemons) {
                // Row background
                guiGraphics.fill(x, currentY, x + 325, currentY + 24, 0x55000000)

                val nameStr = poke.displayName
                val speciesStr = "(${poke.species})"
                guiGraphics.drawString(font, nameStr, x + 8, currentY + 8, 0xFFFFFFFF.toInt())
                guiGraphics.drawString(font, speciesStr, x + 8 + font.width(nameStr) + 4, currentY + 8, 0xFFAAAAAA.toInt())

                currentY += rowHeight
            }
        }

        // --- PC section ---
        if (pcPokemons.isNotEmpty()) {
            if (partyPokemons.isNotEmpty()) currentY += 4  // small gap between sections
            guiGraphics.drawString(font, "📦 PC (Previously Edited)", x, currentY, 0xFF88AAFF.toInt())
            currentY += sectionHeaderHeight

            for (poke in pcPokemons) {
                // Row background (slightly different shade for PC)
                guiGraphics.fill(x, currentY, x + 325, currentY + 24, 0x44003366)

                val nameStr = poke.displayName
                val speciesStr = "(${poke.species})"
                guiGraphics.drawString(font, nameStr, x + 8, currentY + 8, 0xFFCCDDFF.toInt())
                guiGraphics.drawString(font, speciesStr, x + 8 + font.width(nameStr) + 4, currentY + 8, 0xFF8899BB.toInt())

                currentY += rowHeight
            }
        }
    }

    override fun shouldCloseOnEsc(): Boolean = true
}
