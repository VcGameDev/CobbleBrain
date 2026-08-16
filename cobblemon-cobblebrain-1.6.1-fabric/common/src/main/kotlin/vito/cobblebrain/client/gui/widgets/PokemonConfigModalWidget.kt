package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.NodeData

class PokemonConfigModalWidget(
    val node: NodeData,
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    val onClose: () -> Unit,
    val onDataChanged: () -> Unit
) {
    private val modalWidth = 340.coerceAtMost(screenWidth - 20)
    private val modalHeight = 250.coerceAtMost(screenHeight - 20)
    private val modalX = maxOf(10, (screenWidth - modalWidth) / 2)
    private val modalY = maxOf(10, (screenHeight - modalHeight) / 2)

    private val speciesBox: EditBox
    private val levelBox: EditBox
    private val natureBox: EditBox
    private val abilityBox: EditBox
    private val formBox: EditBox
    private val move1Box: EditBox

    private var isShiny: Boolean
    private var genderIndex: Int = 0 // 0=Random, 1=Male, 2=Female, 3=Genderless
    private val genderOptions = listOf("Random 🎲", "Male ♂", "Female ♀", "Genderless ⚪")

    private val shinyBtn: Button
    private val genderBtn: Button
    private val saveBtn: Button
    private val closeBtn: Button

    private val editBoxes = mutableListOf<EditBox>()
    private var focusedEditBox: EditBox? = null

    init {
        isShiny = node.params["shiny"] == "true"
        val savedGender = node.params["gender"] ?: "RANDOM"
        genderIndex = when (savedGender) {
            "MALE" -> 1
            "FEMALE" -> 2
            "GENDERLESS" -> 3
            else -> 0
        }

        val leftX = modalX + 15
        val rightX = modalX + 165
        val colW = 140

        // Row 1: Species and Level
        speciesBox = EditBox(font, leftX, modalY + 42, colW, 16, Component.literal("Species"))
        speciesBox.value = node.params["species"] ?: "Pikachu"
        editBoxes.add(speciesBox)

        levelBox = EditBox(font, rightX, modalY + 42, colW, 16, Component.literal("Level"))
        levelBox.value = node.params["level"] ?: "5"
        levelBox.setFilter { text -> text.isEmpty() || text.all { it.isDigit() } }
        editBoxes.add(levelBox)

        // Row 2: Shiny and Gender
        val shinyLabel = if (isShiny) "✨ Shiny: YES" else "⚪ Shiny: NO"
        shinyBtn = Button.builder(Component.literal(shinyLabel)) {
            isShiny = !isShiny
            shinyBtn.message = Component.literal(if (isShiny) "✨ Shiny: YES" else "⚪ Shiny: NO")
        }.bounds(leftX, modalY + 80, colW, 16).build()

        genderBtn = Button.builder(Component.literal("Gender: ${genderOptions[genderIndex]}")) {
            genderIndex = (genderIndex + 1) % genderOptions.size
            genderBtn.message = Component.literal("Gender: ${genderOptions[genderIndex]}")
        }.bounds(rightX, modalY + 80, colW, 16).build()

        // Row 3: Nature and Ability
        natureBox = EditBox(font, leftX, modalY + 118, colW, 16, Component.literal("Nature"))
        natureBox.value = node.params["nature"] ?: ""
        natureBox.setHint(Component.literal("e.g. Adamant, Jolly..."))
        editBoxes.add(natureBox)

        abilityBox = EditBox(font, rightX, modalY + 118, colW, 16, Component.literal("Ability"))
        abilityBox.value = node.params["ability"] ?: ""
        abilityBox.setHint(Component.literal("Cobblemon Ability"))
        editBoxes.add(abilityBox)

        // Row 4: Form / Variant and Moves
        formBox = EditBox(font, leftX, modalY + 156, colW, 16, Component.literal("Form/Variant"))
        formBox.value = node.params["form"] ?: ""
        formBox.setHint(Component.literal("e.g. alolan, hisuian..."))
        editBoxes.add(formBox)

        move1Box = EditBox(font, rightX, modalY + 156, colW, 16, Component.literal("Special Move"))
        move1Box.value = node.params["specialMove"] ?: ""
        editBoxes.add(move1Box)

        editBoxes.forEach {
            it.setEditable(true)
            it.active = true
        }
        move1Box.setHint(Component.literal("Move 1 (e.g. thunderbolt)"))

        // Action Buttons
        saveBtn = Button.builder(Component.literal("💾 Save Attributes")) {
            node.params["species"] = speciesBox.value.ifBlank { "Pikachu" }
            node.params["level"] = levelBox.value.ifBlank { "5" }
            node.params["shiny"] = isShiny.toString()
            node.params["gender"] = when (genderIndex) {
                1 -> "MALE"
                2 -> "FEMALE"
                3 -> "GENDERLESS"
                else -> "RANDOM"
            }
            if (natureBox.value.isNotBlank()) node.params["nature"] = natureBox.value.trim() else node.params.remove("nature")
            if (abilityBox.value.isNotBlank()) node.params["ability"] = abilityBox.value.trim() else node.params.remove("ability")
            if (formBox.value.isNotBlank()) node.params["form"] = formBox.value.trim() else node.params.remove("form")
            if (move1Box.value.isNotBlank()) node.params["move1"] = move1Box.value.trim() else node.params.remove("move1")

            onDataChanged()
            onClose()
        }.bounds(modalX + modalWidth - 145, modalY + modalHeight - 26, 130, 18).build()

        closeBtn = Button.builder(Component.literal("✖ Cancel")) {
            onClose()
        }.bounds(modalX + 15, modalY + modalHeight - 26, 80, 18).build()
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF14141A.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 24, 0xFF22222E.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())

        guiGraphics.drawString(font, "🐾 Detailed Pokémon Configuration", modalX + 10, modalY + 7, 0xFF00FFCC.toInt(), false)

        val leftX = modalX + 15
        val rightX = modalX + 165

        guiGraphics.drawString(font, "Species:", leftX, modalY + 30, 0xFFA0A0A0.toInt(), false)
        guiGraphics.drawString(font, "Level (1-100):", rightX, modalY + 30, 0xFFA0A0A0.toInt(), false)

        guiGraphics.drawString(font, "Shiny Variant:", leftX, modalY + 68, 0xFFA0A0A0.toInt(), false)
        guiGraphics.drawString(font, "Gender:", rightX, modalY + 68, 0xFFA0A0A0.toInt(), false)

        guiGraphics.drawString(font, "Nature (Optional):", leftX, modalY + 106, 0xFFA0A0A0.toInt(), false)
        guiGraphics.drawString(font, "Ability (Optional):", rightX, modalY + 106, 0xFFA0A0A0.toInt(), false)

        guiGraphics.drawString(font, "Form / Variant:", leftX, modalY + 144, 0xFFA0A0A0.toInt(), false)
        guiGraphics.drawString(font, "Special Move:", rightX, modalY + 144, 0xFFA0A0A0.toInt(), false)

        editBoxes.forEach { it.render(guiGraphics, mouseX, mouseY, partialTick) }
        shinyBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        genderBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        saveBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        closeBtn.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (shinyBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (genderBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (saveBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (closeBtn.mouseClicked(mouseX, mouseY, button)) return true

        editBoxes.forEach { box ->
            val clicked = box.mouseClicked(mouseX, mouseY, button)
            if (clicked) {
                box.isFocused = true
                focusedEditBox = box
            } else {
                box.isFocused = false
            }
        }
        return true
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        val focused = focusedEditBox
        if (focused != null && focused.isFocused) {
            return focused.charTyped(codePoint, modifiers)
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val focused = focusedEditBox
        if (focused != null && focused.isFocused) {
            if (focused.keyPressed(keyCode, scanCode, modifiers)) return true
        }
        if (keyCode == 256) {
            onClose()
            return true
        }
        return false
    }
}
