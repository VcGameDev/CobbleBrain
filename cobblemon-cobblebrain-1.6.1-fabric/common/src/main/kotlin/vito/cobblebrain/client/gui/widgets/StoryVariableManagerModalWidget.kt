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
    private val modalWidth = 470.coerceAtMost(screenWidth - 20)
    private val modalHeight = 290.coerceAtMost(screenHeight - 20)
    private val modalX = maxOf(10, (screenWidth - modalWidth) / 2)
    private val modalY = maxOf(10, (screenHeight - modalHeight) / 2)

    private val closeButton: Button
    private val addVariableButton: Button

    // Left Panel (Tree / List)
    private val searchBox: EditBox
    private val expandedFolders = mutableSetOf<String>("GLOBAL_FOLDER")
    private var scrollOffset: Double = 0.0
    private var selectedVariable: StoryVariable? = project.variables.firstOrNull()

    // Right Panel (Detail Editor for Selected Variable)
    private val nameBox: EditBox
    private val valBox: EditBox
    private val deleteVarBtn: Button
    private val scopeBtn: Button
    private val boolToggleBtn: Button
    private val typeButtons = mutableListOf<Button>()

    private var focusedEditBox: EditBox? = null
    private var isUpdatingFields = false

    init {
        closeButton = Button.builder(Component.literal("✖ Close")) {
            onClose()
        }.bounds(modalX + modalWidth - 75, modalY + 5, 65, 16).build()

        addVariableButton = Button.builder(Component.literal("➕ New Variable")) {
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
                defaultValue = "text",
                scope = VariableScope.GLOBAL,
                sceneId = null
            )
            project.variables.add(newVar)
            selectedVariable = newVar
            syncEditorFields()
            setFocus(nameBox)
            onDataChanged()
        }.bounds(modalX + 150, modalY + 5, 100, 16).build()

        val listX = modalX + 12
        val listY = modalY + 26
        val listW = 175
        val listH = modalHeight - 34

        searchBox = EditBox(font, listX, listY, listW, 16, Component.literal("Search"))
        searchBox.setHint(Component.literal("🔍 Filter variables..."))
        searchBox.setEditable(true)
        searchBox.active = true
        searchBox.setResponder { scrollOffset = 0.0 }

        val detailX = listX + listW + 8
        val detailY = listY
        val detailW = modalWidth - (listW + 28)

        nameBox = EditBox(font, detailX + 8, detailY + 34, detailW - 16, 16, Component.literal("Variable ID"))
        nameBox.setHint(Component.literal("Variable identifier (e.g. quest_step)"))
        nameBox.setMaxLength(60)
        nameBox.setEditable(true)
        nameBox.active = true
        nameBox.setResponder { valText ->
            if (!isUpdatingFields) {
                selectedVariable?.let { v ->
                    v.id = valText
                    v.name = valText
                    onDataChanged()
                }
            }
        }

        val typeW = (detailW - 22) / 4
        val types = listOf(
            Pair(VariableType.BOOLEAN, "BOOL"),
            Pair(VariableType.NUMBER, "NUM"),
            Pair(VariableType.STRING, "TXT"),
            Pair(VariableType.LIST, "LIST")
        )

        types.forEachIndexed { idx, (vType, label) ->
            val btn = Button.builder(Component.literal(label)) {
                selectedVariable?.let { v ->
                    v.type = vType
                    when (vType) {
                        VariableType.BOOLEAN -> v.defaultValue = "true"
                        VariableType.NUMBER -> if (v.defaultValue.toDoubleOrNull() == null) v.defaultValue = "0"
                        VariableType.STRING -> if (v.defaultValue.isBlank()) v.defaultValue = "text"
                        VariableType.LIST -> if (v.defaultValue.isBlank()) v.defaultValue = "item1, item2"
                    }
                    syncEditorFields()
                    onDataChanged()
                }
            }.bounds(detailX + 8 + idx * (typeW + 2), detailY + 68, typeW, 16).build()
            typeButtons.add(btn)
        }

        valBox = EditBox(font, detailX + 8, detailY + 102, detailW - 16, 16, Component.literal("Default Value"))
        valBox.setHint(Component.literal("Initial default value"))
        valBox.setMaxLength(100)
        valBox.setEditable(true)
        valBox.active = true
        valBox.setResponder { valText ->
            if (!isUpdatingFields) {
                selectedVariable?.let { v ->
                    if (v.type == VariableType.NUMBER) {
                        val filtered = valText.filter { c -> c.isDigit() || c == '-' || c == '.' }
                        if (filtered != valText) valBox.value = filtered
                        v.defaultValue = filtered
                    } else {
                        v.defaultValue = valText
                    }
                    onDataChanged()
                }
            }
        }

        boolToggleBtn = Button.builder(Component.literal("✔ TRUE")) {
            selectedVariable?.let { v ->
                val isTrue = v.defaultValue.lowercase() == "true"
                v.defaultValue = if (isTrue) "false" else "true"
                syncEditorFields()
                onDataChanged()
            }
        }.bounds(detailX + 8, detailY + 102, detailW - 16, 18).build()

        scopeBtn = Button.builder(Component.literal("🌐 Global Scope")) {
            selectedVariable?.let { v ->
                // Cycle: Global -> Scene 0 -> Scene 1 -> ... -> Global
                if (v.scope == VariableScope.GLOBAL) {
                    val firstScene = project.scenes.firstOrNull()
                    if (firstScene != null) {
                        v.scope = VariableScope.SCENE_LOCAL
                        v.sceneId = firstScene.id
                    }
                } else {
                    val currentIdx = project.scenes.indexOfFirst { it.id == v.sceneId }
                    if (currentIdx >= 0 && currentIdx < project.scenes.size - 1) {
                        v.sceneId = project.scenes[currentIdx + 1].id
                    } else {
                        v.scope = VariableScope.GLOBAL
                        v.sceneId = null
                    }
                }
                syncEditorFields()
                onDataChanged()
            }
        }.bounds(detailX + 8, detailY + 138, detailW - 16, 16).build()

        deleteVarBtn = Button.builder(Component.literal("🗑️ Delete Variable")) {
            selectedVariable?.let { v ->
                project.variables.remove(v)
                selectedVariable = project.variables.firstOrNull()
                syncEditorFields()
                onDataChanged()
            }
        }.bounds(detailX + 8, detailY + listH - 24, detailW - 16, 18).build()

        syncEditorFields()
    }

    private fun syncEditorFields() {
        isUpdatingFields = true
        val v = selectedVariable
        if (v != null) {
            nameBox.value = v.id
            valBox.value = v.defaultValue
            val isTrue = v.defaultValue.lowercase() == "true"
            boolToggleBtn.message = Component.literal(if (isTrue) "✔ TRUE" else "✖ FALSE")

            val scopeText = if (v.scope == VariableScope.GLOBAL) {
                "🌐 Scope: Global (All Scenes) ▾"
            } else {
                val sceneName = project.scenes.find { it.id == v.sceneId }?.title ?: "Scene"
                "📁 Scope: Local (${font.plainSubstrByWidth(sceneName, 100)}) ▾"
            }
            scopeBtn.message = Component.literal(scopeText)
        }
        isUpdatingFields = false
    }

    private data class ListRow(
        val isFolderHeader: Boolean,
        val folderKey: String,
        val folderLabel: String,
        val variable: StoryVariable? = null
    )

    private fun buildFlattenedRows(): List<ListRow> {
        val query = searchBox.value.trim().lowercase()
        val rows = mutableListOf<ListRow>()

        // 1. Global Folder
        val globalVars = project.variables.filter {
            it.scope == VariableScope.GLOBAL && (query.isEmpty() || it.id.lowercase().contains(query) || it.name.lowercase().contains(query))
        }
        val globalHeaderKey = "GLOBAL_FOLDER"
        val globalExpanded = query.isNotEmpty() || expandedFolders.contains(globalHeaderKey)
        rows.add(ListRow(true, globalHeaderKey, "🌐 Global (${globalVars.size})"))

        if (globalExpanded) {
            globalVars.forEach { rows.add(ListRow(false, globalHeaderKey, "", it)) }
        }

        // 2. Per-Scene Folders
        val localVars = project.variables.filter { it.scope == VariableScope.SCENE_LOCAL }
        val sceneGroups = localVars.groupBy { it.sceneId }

        project.scenes.forEach { scene ->
            val sceneVars = (sceneGroups[scene.id] ?: emptyList()).filter {
                query.isEmpty() || it.id.lowercase().contains(query) || it.name.lowercase().contains(query)
            }
            val folderKey = "SCENE_${scene.id}"
            val expanded = query.isNotEmpty() || expandedFolders.contains(folderKey)
            rows.add(ListRow(true, folderKey, "📁 ${scene.title} (${sceneVars.size})"))

            if (expanded) {
                sceneVars.forEach { rows.add(ListRow(false, folderKey, "", it)) }
            }
        }

        return rows
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Modal Background Frame
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF14141A.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 22, 0xFF22222E.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())

        guiGraphics.drawString(font, "📋 Variable Manager", modalX + 10, modalY + 7, 0xFF00FFCC.toInt(), false)

        closeButton.render(guiGraphics, mouseX, mouseY, partialTick)
        addVariableButton.render(guiGraphics, mouseX, mouseY, partialTick)

        // --- LEFT PANEL (Tree / List) ---
        val listX = modalX + 12
        val listY = modalY + 26
        val listW = 175
        val listH = modalHeight - 34

        searchBox.render(guiGraphics, mouseX, mouseY, partialTick)

        val contentY = listY + 20
        val contentH = listH - 20
        guiGraphics.fill(listX, contentY, listX + listW, contentY + contentH, 0xFF0D0D12.toInt())
        guiGraphics.fill(listX + listW, contentY, listX + listW + 1, contentY + contentH, 0xFF282836.toInt())

        val rows = buildFlattenedRows()
        val itemH = 20
        val totalListHeight = rows.size * itemH + 4

        guiGraphics.enableScissor(listX, contentY, listX + listW, contentY + contentH)
        rows.forEachIndexed { idx, row ->
            val iy = (contentY + 4 + idx * itemH - scrollOffset).toInt()
            if (iy + itemH >= contentY && iy <= contentY + contentH) {
                if (row.isFolderHeader) {
                    val isExpanded = searchBox.value.isNotBlank() || expandedFolders.contains(row.folderKey)
                    val icon = if (isExpanded) "▼" else "▶"
                    val isHovered = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= iy && mouseY <= iy + itemH
                    val bg = if (isHovered) 0xFF222232.toInt() else 0xFF181822.toInt()

                    guiGraphics.fill(listX + 2, iy, listX + listW - 2, iy + itemH - 1, bg)
                    val truncLabel = font.plainSubstrByWidth("$icon ${row.folderLabel}", listW - 8)
                    guiGraphics.drawString(font, truncLabel, listX + 6, iy + 4, 0xFFFFD700.toInt(), false)
                } else {
                    val v = row.variable ?: return@forEachIndexed
                    val isSelected = (v == selectedVariable)
                    val isHovered = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= iy && mouseY <= iy + itemH
                    val bg = if (isSelected) 0xFF1B3A4B.toInt() else if (isHovered) 0xFF1F1F2C.toInt() else 0xFF14141C.toInt()
                    val border = if (isSelected) 0xFF00FFCC.toInt() else 0x00000000

                    guiGraphics.fill(listX + 2, iy, listX + listW - 2, iy + itemH - 1, bg)
                    if (border != 0) {
                        guiGraphics.fill(listX + 2, iy, listX + listW - 2, iy + 1, border)
                        guiGraphics.fill(listX + 2, iy + itemH - 2, listX + listW - 2, iy + itemH - 1, border)
                    }

                    val typeTag = when (v.type) {
                        VariableType.BOOLEAN -> "B"
                        VariableType.NUMBER -> "#"
                        VariableType.STRING -> "T"
                        VariableType.LIST -> "L"
                    }
                    val typeBg = when (v.type) {
                        VariableType.BOOLEAN -> 0xFF2E7D32.toInt()
                        VariableType.NUMBER -> 0xFFE65100.toInt()
                        VariableType.STRING -> 0xFF00838F.toInt()
                        VariableType.LIST -> 0xFF6A1B9A.toInt()
                    }

                    guiGraphics.fill(listX + 6, iy + 3, listX + 18, iy + 15, typeBg)
                    guiGraphics.drawString(font, typeTag, listX + 9, iy + 4, 0xFFFFFFFF.toInt(), false)

                    val varTitle = font.plainSubstrByWidth(v.id, listW - 32)
                    guiGraphics.drawString(font, varTitle, listX + 22, iy + 4, if (isSelected) 0xFF00FFCC.toInt() else 0xFFCCCCCC.toInt(), false)
                }
            }
        }
        guiGraphics.disableScissor()

        // --- RIGHT PANEL (Detail Editor for Selected Variable) ---
        val detailX = listX + listW + 8
        val detailY = listY
        val detailW = modalWidth - (listW + 28)
        val detailH = listH

        guiGraphics.fill(detailX, detailY, detailX + detailW, detailY + detailH, 0xFF0F0F16.toInt())
        guiGraphics.fill(detailX, detailY, detailX + detailW, detailY + 1, 0xFF282836.toInt())
        guiGraphics.fill(detailX, detailY + detailH - 1, detailX + detailW, detailY + detailH, 0xFF282836.toInt())
        guiGraphics.fill(detailX + detailW - 1, detailY, detailX + detailW, detailY + detailH, 0xFF282836.toInt())

        val v = selectedVariable
        if (v == null) {
            guiGraphics.drawCenteredString(font, "No variable selected.", detailX + detailW / 2, detailY + detailH / 2 - 10, 0xFF888899.toInt())
            guiGraphics.drawCenteredString(font, "Click [➕ New Variable] to create.", detailX + detailW / 2, detailY + detailH / 2 + 4, 0xFF666677.toInt())
        } else {
            // Header
            guiGraphics.drawString(font, "✏️ Edit Variable", detailX + 8, detailY + 6, 0xFF00FFCC.toInt(), false)

            // 1. Variable Name / ID
            guiGraphics.drawString(font, "Variable ID / Title:", detailX + 8, detailY + 22, 0xFFA0A0A0.toInt(), false)
            nameBox.render(guiGraphics, mouseX, mouseY, partialTick)

            // 2. Type Selector
            guiGraphics.drawString(font, "Type:", detailX + 8, detailY + 56, 0xFFA0A0A0.toInt(), false)
            val types = listOf(VariableType.BOOLEAN, VariableType.NUMBER, VariableType.STRING, VariableType.LIST)
            typeButtons.forEachIndexed { idx, btn ->
                btn.render(guiGraphics, mouseX, mouseY, partialTick)
                if (v.type == types[idx]) {
                    guiGraphics.fill(btn.x, btn.y + btn.height - 2, btn.x + btn.width, btn.y + btn.height, 0xFF00FFCC.toInt())
                }
            }

            // 3. Default Value
            guiGraphics.drawString(font, "Default Value:", detailX + 8, detailY + 90, 0xFFA0A0A0.toInt(), false)
            if (v.type == VariableType.BOOLEAN) {
                boolToggleBtn.render(guiGraphics, mouseX, mouseY, partialTick)
            } else {
                valBox.render(guiGraphics, mouseX, mouseY, partialTick)
            }

            // 4. Scope
            guiGraphics.drawString(font, "Scope Assignment:", detailX + 8, detailY + 126, 0xFFA0A0A0.toInt(), false)
            scopeBtn.render(guiGraphics, mouseX, mouseY, partialTick)

            // 5. Delete Button
            deleteVarBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        }
    }

    private fun setFocus(box: EditBox?) {
        searchBox.isFocused = (searchBox == box)
        nameBox.isFocused = (nameBox == box)
        valBox.isFocused = (valBox == box)
        focusedEditBox = box
        box?.isFocused = true
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (closeButton.mouseClicked(mouseX, mouseY, button)) return true
        if (addVariableButton.mouseClicked(mouseX, mouseY, button)) return true

        val listX = modalX + 12
        val listY = modalY + 26
        val listW = 175
        val listH = modalHeight - 34
        val contentY = listY + 20
        val contentH = listH - 20

        // Search Box
        if (searchBox.mouseClicked(mouseX, mouseY, button)) {
            setFocus(searchBox)
            return true
        }

        // Left List Click
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= contentY && mouseY <= contentY + contentH) {
            val rows = buildFlattenedRows()
            val itemH = 20
            rows.forEachIndexed { idx, row ->
                val iy = contentY + 4 + idx * itemH - scrollOffset
                if (mouseY >= iy && mouseY <= iy + itemH) {
                    if (row.isFolderHeader) {
                        if (expandedFolders.contains(row.folderKey)) {
                            expandedFolders.remove(row.folderKey)
                        } else {
                            expandedFolders.add(row.folderKey)
                        }
                    } else if (row.variable != null) {
                        selectedVariable = row.variable
                        syncEditorFields()
                        setFocus(nameBox)
                    }
                    return true
                }
            }
        }

        // Right Detail Panel Click
        val v = selectedVariable
        if (v != null) {
            if (nameBox.mouseClicked(mouseX, mouseY, button)) {
                setFocus(nameBox)
                return true
            }

            typeButtons.forEach { btn ->
                if (btn.mouseClicked(mouseX, mouseY, button)) return true
            }

            if (v.type == VariableType.BOOLEAN) {
                if (boolToggleBtn.mouseClicked(mouseX, mouseY, button)) return true
            } else {
                if (valBox.mouseClicked(mouseX, mouseY, button)) {
                    setFocus(valBox)
                    return true
                }
            }

            if (scopeBtn.mouseClicked(mouseX, mouseY, button)) return true
            if (deleteVarBtn.mouseClicked(mouseX, mouseY, button)) return true
        }

        setFocus(null)
        return true
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        val listX = modalX + 12
        val listY = modalY + 26
        val listW = 175
        val listH = modalHeight - 34
        val contentY = listY + 20
        val contentH = listH - 20

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= contentY && mouseY <= contentY + contentH) {
            val rows = buildFlattenedRows()
            val itemH = 20
            val totalH = rows.size * itemH + 4
            val maxScroll = maxOf(0.0, totalH.toDouble() - contentH)
            if (scrollY < 0) {
                scrollOffset = (scrollOffset + 24).coerceAtMost(maxScroll)
            } else if (scrollY > 0) {
                scrollOffset = (scrollOffset - 24).coerceAtLeast(0.0)
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
        if (keyCode == 256) { // ESC
            onClose()
            return true
        }
        return false
    }
}
