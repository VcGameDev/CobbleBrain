package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.StoryProject
import vito.cobblebrain.model.StoryVariable
import vito.cobblebrain.model.VariableScope
import vito.cobblebrain.model.VariableType

class StoryVariableManagerModalWidget(
    val project: StoryProject,
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    val onClose: () -> Unit,
    val onDataChanged: () -> Unit
) {

    private val modalWidth = 340
    private val modalHeight = 250
    private val modalX = (screenWidth - modalWidth) / 2
    private val modalY = (screenHeight - modalHeight) / 2

    private val closeButton: Button
    private val addVariableButton: Button

    private val expandedFolders = mutableSetOf<String>("GLOBAL_FOLDER")
    private var activeSceneAssignPickerForVar: StoryVariable? = null
    private var activeTypePickerForVar: StoryVariable? = null

    private val keyEditBoxes = mutableMapOf<String, EditBox>()
    private val valEditBoxes = mutableMapOf<String, EditBox>()
    private var focusedEditBox: EditBox? = null

    private var scrollOffset = 0

    init {
        closeButton = Button.builder(Component.literal("✖ Fechar")) {
            onClose()
        }.bounds(modalX + modalWidth - 75, modalY + 5, 65, 16).build()

        addVariableButton = Button.builder(Component.literal("➕ Nova Variável")) {
            var newId = "var_${project.variables.size + 1}"
            var count = 1
            while (project.variables.any { it.id == newId }) {
                count++
                newId = "var_${project.variables.size + count}"
            }
            val newVar = StoryVariable(
                id = newId,
                name = newId,
                type = VariableType.STRING,
                defaultValue = "texto",
                scope = VariableScope.GLOBAL,
                sceneId = null
            )
            project.variables.add(newVar)
            onDataChanged()
        }.bounds(modalX + 15, modalY + 30, 110, 16).build()
    }

    private data class ListRow(
        val isFolderHeader: Boolean,
        val folderKey: String,
        val folderLabel: String,
        val variable: StoryVariable? = null
    )

    private fun buildFlattenedRows(): List<ListRow> {
        val rows = mutableListOf<ListRow>()

        // 1. Pasta Global
        val globalVars = project.variables.filter { it.scope == VariableScope.GLOBAL }
        val globalHeaderKey = "GLOBAL_FOLDER"
        val globalExpanded = expandedFolders.contains(globalHeaderKey)
        rows.add(ListRow(true, globalHeaderKey, "🌐 Globais (${globalVars.size})"))

        if (globalExpanded) {
            globalVars.forEach { rows.add(ListRow(false, globalHeaderKey, "", it)) }
        }

        // 2. Pastas por Cena
        val localVars = project.variables.filter { it.scope == VariableScope.SCENE_LOCAL }
        val sceneGroups = localVars.groupBy { it.sceneId }

        project.scenes.forEach { scene ->
            val sceneVars = sceneGroups[scene.id] ?: emptyList()
            val folderKey = "SCENE_${scene.id}"
            val expanded = expandedFolders.contains(folderKey)
            rows.add(ListRow(true, folderKey, "📁 ${scene.title} (${sceneVars.size} vars)"))

            if (expanded) {
                sceneVars.forEach { rows.add(ListRow(false, folderKey, "", it)) }
            }
        }

        // Vars locais não atribuídas
        val unassignedLocal = localVars.filter { varItem -> project.scenes.none { it.id == varItem.sceneId } }
        if (unassignedLocal.isNotEmpty()) {
            val folderKey = "UNASSIGNED_LOCAL"
            val expanded = expandedFolders.contains(folderKey)
            rows.add(ListRow(true, folderKey, "📁 Outras Locais (${unassignedLocal.size})"))
            if (expanded) {
                unassignedLocal.forEach { rows.add(ListRow(false, folderKey, "", it)) }
            }
        }

        return rows
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF14141A.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 24, 0xFF22222E.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())

        guiGraphics.drawString(font, "📋 Catálogo Central de Variáveis", modalX + 10, modalY + 7, 0xFF00FFCC.toInt(), false)

        closeButton.render(guiGraphics, mouseX, mouseY, partialTick)
        addVariableButton.render(guiGraphics, mouseX, mouseY, partialTick)

        val listX = modalX + 15
        val listY = modalY + 52
        val listW = modalWidth - 30
        val itemH = 24
        val maxVisible = 7
        val listH = itemH * maxVisible

        guiGraphics.fill(listX, listY, listX + listW, listY + listH, 0xFF0D0D12.toInt())

        val rows = buildFlattenedRows()
        val startIndex = scrollOffset.coerceIn(0, maxOf(0, rows.size - maxVisible))
        val endIndex = (startIndex + maxVisible).coerceAtMost(rows.size)

        for (i in startIndex until endIndex) {
            val idx = i - startIndex
            val row = rows[i]
            val iy = listY + idx * itemH

            if (row.isFolderHeader) {
                val isHovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= iy && mouseY < iy + itemH
                val isExpanded = expandedFolders.contains(row.folderKey)
                val icon = if (isExpanded) "▼" else "▶"
                val bg = if (isHovered) 0xFF3D5AFE.toInt() else 0xFF1E1E2A.toInt()

                guiGraphics.fill(listX + 2, iy + 1, listX + listW - 2, iy + itemH - 1, bg)
                guiGraphics.drawString(font, "$icon ${row.folderLabel}", listX + 8, iy + 6, 0xFFFFD700.toInt(), false)
            } else {
                val v = row.variable ?: continue
                val isHovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= iy && mouseY < iy + itemH
                val bg = if (isHovered) 0xFF2A2A38.toInt() else 0xFF161620.toInt()

                guiGraphics.fill(listX + 2, iy + 1, listX + listW - 2, iy + itemH - 1, bg)

                // 1. Campo ID da Chave
                val keyBox = keyEditBoxes.getOrPut(v.id) {
                    val eb = EditBox(font, listX + 6, iy + 4, 70, 14, Component.literal("ID"))
                    eb.value = v.id
                    eb.setMaxLength(60)
                    eb.setResponder { valText ->
                        v.id = valText
                        v.name = valText
                        onDataChanged()
                    }
                    eb
                }
                keyBox.x = listX + 6
                keyBox.y = iy + 4
                keyBox.render(guiGraphics, mouseX, mouseY, partialTick)

                // 2. Botão Mini-Lista de Seleção de Tipo (BOOL / NUM / TXT / LIST)
                val typeLabel = when (v.type) {
                    VariableType.BOOLEAN -> "BOOL ▾"
                    VariableType.NUMBER -> "NUM ▾"
                    VariableType.STRING -> "TXT ▾"
                    VariableType.LIST -> "LIST ▾"
                }
                val typeBtnX = listX + 80
                val typeBtnW = 42
                val isTypeHovered = mouseX >= typeBtnX && mouseX <= typeBtnX + typeBtnW && mouseY >= iy + 4 && mouseY <= iy + 18
                guiGraphics.fill(typeBtnX, iy + 4, typeBtnX + typeBtnW, iy + 18, if (isTypeHovered) 0xFF00ACC1.toInt() else 0xFF00838F.toInt())
                guiGraphics.drawString(font, typeLabel, typeBtnX + 4, iy + 6, 0xFFFFFFFF.toInt(), false)

                // 3. Controle de Valor conforme o Tipo
                val valX = listX + 126
                val valW = 95

                if (v.type == VariableType.BOOLEAN) {
                    val isTrue = v.defaultValue.lowercase() == "true"
                    val boolLabel = if (isTrue) "✔ TRUE" else "✖ FALSE"
                    val boolBg = if (isTrue) 0xFF2E7D32.toInt() else 0xFFC62828.toInt()
                    val isBoolHovered = mouseX >= valX && mouseX <= valX + valW && mouseY >= iy + 4 && mouseY <= iy + 18

                    guiGraphics.fill(valX, iy + 4, valX + valW, iy + 18, if (isBoolHovered) boolBg or 0x404040 else boolBg)
                    guiGraphics.drawString(font, boolLabel, valX + 22, iy + 6, 0xFFFFFFFF.toInt(), false)
                } else {
                    val valBox = valEditBoxes.getOrPut("val_${v.id}") {
                        val eb = EditBox(font, valX, iy + 4, valW, 14, Component.literal("Valor"))
                        eb.value = v.defaultValue
                        eb.setMaxLength(100)
                        eb.setResponder { valText ->
                            if (v.type == VariableType.NUMBER) {
                                val filtered = valText.filter { char -> char.isDigit() || char == '-' || char == '.' }
                                if (filtered != valText) eb.value = filtered
                                v.defaultValue = filtered
                            } else if (v.type == VariableType.STRING) {
                                if (valText.matches(Regex("^-?\\d+(\\.\\d+)?$"))) {
                                    val filtered = valText + "_txt"
                                    eb.value = filtered
                                    v.defaultValue = filtered
                                } else {
                                    v.defaultValue = valText
                                }
                            } else {
                                v.defaultValue = valText
                            }
                            onDataChanged()
                        }
                        eb
                    }
                    valBox.x = valX
                    valBox.y = iy + 4
                    valBox.render(guiGraphics, mouseX, mouseY, partialTick)
                }

                // 4. Botão Mover Pasta / Escopo (APENAS OS 2 EMOJIS, SEM TEXTO)
                val assignBtnX = listX + 226
                val assignBtnW = 42
                val assignEmojis = if (v.scope == VariableScope.GLOBAL) "🌐 ⇄" else "📁 ⇄"

                val isAssignHovered = mouseX >= assignBtnX && mouseX <= assignBtnX + assignBtnW && mouseY >= iy + 4 && mouseY <= iy + 18
                val assignBg = if (isAssignHovered) 0xFF3D5AFE.toInt() else 0xFF1E1E2E.toInt()
                guiGraphics.fill(assignBtnX, iy + 4, assignBtnX + assignBtnW, iy + 18, assignBg)
                guiGraphics.drawString(font, assignEmojis, assignBtnX + 8, iy + 6, 0xFF00FFCC.toInt(), false)

                // 5. Botão Excluir (🗑)
                val delBtnX = listX + listW - 22
                val isDelHovered = mouseX >= delBtnX && mouseX <= delBtnX + 18 && mouseY >= iy + 4 && mouseY <= iy + 18
                guiGraphics.fill(delBtnX, iy + 4, delBtnX + 18, iy + 18, if (isDelHovered) 0xFFD32F2F.toInt() else 0xFFC62828.toInt())
                guiGraphics.drawString(font, "🗑", delBtnX + 4, iy + 5, 0xFFFFFFFF.toInt(), false)
            }
        }

        // Renderizar Mini-Lista Pop-up de Seleção de Tipo
        val typeVar = activeTypePickerForVar
        if (typeVar != null) {
            renderTypePickerPopup(guiGraphics, typeVar, mouseX, mouseY)
        }

        // Renderizar Pop-up de Atribuição de Cena
        val pickerVar = activeSceneAssignPickerForVar
        if (pickerVar != null) {
            renderSceneAssignPickerPopup(guiGraphics, pickerVar, mouseX, mouseY)
        }
    }

    private fun renderTypePickerPopup(guiGraphics: GuiGraphics, v: StoryVariable, mouseX: Int, mouseY: Int) {
        val popW = 95
        val itemH = 18
        val popH = itemH * 4 + 4
        val popX = (modalX + 90).coerceIn(10, screenWidth - popW - 10)
        val popY = (modalY + 60).coerceIn(10, screenHeight - popH - 10)

        guiGraphics.fill(popX, popY, popX + popW, popY + popH, 0xF0181824.toInt())
        guiGraphics.fill(popX, popY, popX + 1, popY + popH, 0xFF00ACC1.toInt())
        guiGraphics.fill(popX + popW - 1, popY, popX + popW, popY + popH, 0xFF00ACC1.toInt())
        guiGraphics.fill(popX, popY + popH - 1, popX + popW, popY + popH, 0xFF00ACC1.toInt())

        val types = listOf(
            Pair("BOOL (Lógico)", VariableType.BOOLEAN),
            Pair("NUM (Número)", VariableType.NUMBER),
            Pair("TXT (Texto)", VariableType.STRING),
            Pair("LIST (Lista)", VariableType.LIST)
        )

        types.forEachIndexed { idx, (label, t) ->
            val iy = popY + 2 + idx * itemH
            val isHovered = mouseX >= popX && mouseX <= popX + popW && mouseY >= iy && mouseY < iy + itemH
            guiGraphics.fill(popX + 2, iy, popX + popW - 2, iy + itemH - 2, if (isHovered) 0xFF00ACC1.toInt() else 0xFF22222E.toInt())
            guiGraphics.drawString(font, label, popX + 6, iy + 4, if (v.type == t) 0xFF00FFCC.toInt() else 0xFFFFFFFF.toInt(), false)
        }
    }

    private fun renderSceneAssignPickerPopup(guiGraphics: GuiGraphics, v: StoryVariable, mouseX: Int, mouseY: Int) {
        val popW = 160
        val optionsCount = project.scenes.size + 1
        val itemH = 18
        val popH = itemH * optionsCount + 4
        val popX = (modalX + (modalWidth - popW) / 2).coerceIn(10, screenWidth - popW - 10)
        val popY = (modalY + (modalHeight - popH) / 2).coerceIn(10, screenHeight - popH - 10)

        guiGraphics.fill(popX, popY, popX + popW, popY + popH, 0xF0181824.toInt())
        guiGraphics.fill(popX, popY, popX + 1, popY + popH, 0xFF00FFCC.toInt())
        guiGraphics.fill(popX + popW - 1, popY, popX + popW, popY + popH, 0xFF00FFCC.toInt())
        guiGraphics.fill(popX, popY + popH - 1, popX + popW, popY + popH, 0xFF00FFCC.toInt())

        // Opção 1: Global
        val isGlobHovered = mouseX >= popX && mouseX <= popX + popW && mouseY >= popY + 2 && mouseY < popY + 2 + itemH
        guiGraphics.fill(popX + 2, popY + 2, popX + popW - 2, popY + itemH, if (isGlobHovered) 0xFF3D5AFE.toInt() else 0xFF22222E.toInt())
        guiGraphics.drawString(font, "🌐 Mover p/ Global", popX + 6, popY + 6, 0xFF00FFCC.toInt(), false)

        // Opções das Cenas do Projeto
        project.scenes.forEachIndexed { sIdx, scene ->
            val iy = popY + 2 + (sIdx + 1) * itemH
            val isHovered = mouseX >= popX && mouseX <= popX + popW && mouseY >= iy && mouseY < iy + itemH
            guiGraphics.fill(popX + 2, iy, popX + popW - 2, iy + itemH - 2, if (isHovered) 0xFF3D5AFE.toInt() else 0xFF22222E.toInt())
            guiGraphics.drawString(font, "📁 Mover p/ ${scene.title}", popX + 6, iy + 4, 0xFFFFFFFF.toInt(), false)
        }
    }

    private fun setFocusedBox(target: EditBox?) {
        keyEditBoxes.values.forEach { it.isFocused = (it == target) }
        valEditBoxes.values.forEach { it.isFocused = (it == target) }
        focusedEditBox = target
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val typeVar = activeTypePickerForVar
        if (typeVar != null) {
            val popW = 95
            val itemH = 18
            val popH = itemH * 4 + 4
            val popX = (modalX + 90).coerceIn(10, screenWidth - popW - 10)
            val popY = (modalY + 60).coerceIn(10, screenHeight - popH - 10)

            if (mouseX >= popX && mouseX <= popX + popW && mouseY >= popY && mouseY <= popY + popH) {
                val idx = ((mouseY - (popY + 2)) / itemH).toInt()
                when (idx) {
                    0 -> { typeVar.type = VariableType.BOOLEAN; typeVar.defaultValue = "true" }
                    1 -> { typeVar.type = VariableType.NUMBER; typeVar.defaultValue = "0" }
                    2 -> { typeVar.type = VariableType.STRING; typeVar.defaultValue = "texto" }
                    3 -> { typeVar.type = VariableType.LIST; typeVar.defaultValue = "item1, item2" }
                }
                onDataChanged()
                activeTypePickerForVar = null
                return true
            } else {
                activeTypePickerForVar = null
                return true
            }
        }

        val pickerVar = activeSceneAssignPickerForVar
        if (pickerVar != null) {
            val popW = 160
            val optionsCount = project.scenes.size + 1
            val itemH = 18
            val popH = itemH * optionsCount + 4
            val popX = (modalX + (modalWidth - popW) / 2).coerceIn(10, screenWidth - popW - 10)
            val popY = (modalY + (modalHeight - popH) / 2).coerceIn(10, screenHeight - popH - 10)

            if (mouseX >= popX && mouseX <= popX + popW && mouseY >= popY && mouseY <= popY + popH) {
                val idx = ((mouseY - (popY + 2)) / itemH).toInt()
                if (idx == 0) {
                    pickerVar.scope = VariableScope.GLOBAL
                    pickerVar.sceneId = null
                } else if (idx - 1 in project.scenes.indices) {
                    val scene = project.scenes[idx - 1]
                    pickerVar.scope = VariableScope.SCENE_LOCAL
                    pickerVar.sceneId = scene.id
                }
                onDataChanged()
                activeSceneAssignPickerForVar = null
                return true
            } else {
                activeSceneAssignPickerForVar = null
                return true
            }
        }

        if (closeButton.mouseClicked(mouseX, mouseY, button)) return true
        if (addVariableButton.mouseClicked(mouseX, mouseY, button)) return true

        val listX = modalX + 15
        val listY = modalY + 52
        val listW = modalWidth - 30
        val itemH = 22
        val maxVisible = 7

        val rows = buildFlattenedRows()
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY < listY + itemH * maxVisible) {
            val idx = ((mouseY - listY) / itemH).toInt() + scrollOffset
            if (idx in rows.indices) {
                val row = rows[idx]
                if (row.isFolderHeader) {
                    if (expandedFolders.contains(row.folderKey)) {
                        expandedFolders.remove(row.folderKey)
                    } else {
                        expandedFolders.add(row.folderKey)
                    }
                    return true
                } else {
                    val v = row.variable ?: return true
                    val iy = listY + ((mouseY - listY) / itemH).toInt() * itemH

                    // Clique no campo ID
                    val keyBox = keyEditBoxes[v.id]
                    if (keyBox != null && keyBox.mouseClicked(mouseX, mouseY, button)) {
                        setFocusedBox(keyBox)
                        return true
                    }

                    // Clique no Botão Mini-Lista de Tipo
                    val typeBtnX = listX + 80
                    val typeBtnW = 42
                    if (mouseX >= typeBtnX && mouseX <= typeBtnX + typeBtnW && mouseY >= iy + 4 && mouseY <= iy + 18) {
                        activeTypePickerForVar = v
                        return true
                    }

                    // Clique no Controle de Valor
                    val valX = listX + 126
                    val valW = 95
                    if (v.type == VariableType.BOOLEAN) {
                        if (mouseX >= valX && mouseX <= valX + valW && mouseY >= iy + 4 && mouseY <= iy + 18) {
                            val isTrue = v.defaultValue.lowercase() == "true"
                            v.defaultValue = if (isTrue) "false" else "true"
                            onDataChanged()
                            return true
                        }
                    } else {
                        val valBox = valEditBoxes["val_${v.id}"]
                        if (valBox != null && valBox.mouseClicked(mouseX, mouseY, button)) {
                            setFocusedBox(valBox)
                            return true
                        }
                    }

                    // Clique no Botão Escopo / Mover Pasta (Apenas os 2 Emojis)
                    val assignBtnX = listX + 226
                    val assignBtnW = 42
                    if (mouseX >= assignBtnX && mouseX <= assignBtnX + assignBtnW && mouseY >= iy + 4 && mouseY <= iy + 18) {
                        activeSceneAssignPickerForVar = v
                        return true
                    }

                    // Clique no Botão Excluir (🗑)
                    val delBtnX = listX + listW - 22
                    if (mouseX >= delBtnX && mouseX <= delBtnX + 18 && mouseY >= iy + 4 && mouseY <= iy + 18) {
                        project.variables.remove(v)
                        keyEditBoxes.remove(v.id)
                        valEditBoxes.remove("val_${v.id}")
                        onDataChanged()
                        return true
                    }
                }
            }
        }
        setFocusedBox(null)
        return true
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        val rows = buildFlattenedRows()
        val maxVisible = 7
        if (rows.size > maxVisible) {
            if (scrollY > 0) {
                scrollOffset = (scrollOffset - 1).coerceAtLeast(0)
            } else if (scrollY < 0) {
                scrollOffset = (scrollOffset + 1).coerceAtMost(rows.size - maxVisible)
            }
            return true
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
        if (keyCode == 256) {
            if (activeTypePickerForVar != null) {
                activeTypePickerForVar = null
                return true
            }
            if (activeSceneAssignPickerForVar != null) {
                activeSceneAssignPickerForVar = null
                return true
            }
            onClose()
            return true
        }
        return false
    }
}
