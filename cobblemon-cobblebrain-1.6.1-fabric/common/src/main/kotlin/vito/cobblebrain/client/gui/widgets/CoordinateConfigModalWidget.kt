package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import vito.cobblebrain.engine.SearchLayerPriority
import vito.cobblebrain.model.NodeData

class CoordinateConfigModalWidget(
    val node: NodeData,
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    val onClose: () -> Unit,
    val onDataChanged: () -> Unit
) {
    private val modalWidth = 460.coerceAtMost(screenWidth - 20)
    private val modalHeight = 330.coerceAtMost(screenHeight - 20)
    private val modalX = maxOf(10, (screenWidth - modalWidth) / 2)
    private val modalY = maxOf(10, (screenHeight - modalHeight) / 2)

    private val coordBox: EditBox
    private val anchorTagBox: EditBox
    private val radiusBox: EditBox

    private var originMode: String = "PLAYER" // "PLAYER" or "MOB_TAG"
    private var isSafePosition: Boolean
    private var isSnapToGround: Boolean
    private var currentPriority: SearchLayerPriority

    private val originToggleBtn: Button
    private val absBtn: Button
    private val relBtn: Button
    private val frontBtn: Button
    private val pickPosBtn: Button

    private val safeBtn: Button
    private val snapBtn: Button
    private val priorityBtn: Button

    private val saveBtn: Button
    private val closeBtn: Button

    private val editBoxes = mutableListOf<EditBox>()
    private var focusedEditBox: EditBox? = null

    init {
        val initCoords = node.params["coordinates"]?.ifBlank {
            node.params["referenceIdentifier"]?.ifBlank {
                "${node.params["posX"] ?: node.params["destX"] ?: "~"} ${node.params["posY"] ?: node.params["destY"] ?: "~"} ${node.params["posZ"] ?: node.params["destZ"] ?: "~"}"
            } ?: "~ ~ ~"
        } ?: "~ ~ ~"

        // Auto-detect if coordinates start with @tag
        var initialTag = ""
        if (initCoords.trim().startsWith("@")) {
            originMode = "MOB_TAG"
            val spaceIdx = initCoords.trim().indexOf(' ')
            val token = if (spaceIdx != -1) initCoords.trim().substring(0, spaceIdx) else initCoords.trim()
            initialTag = token.removePrefix("@").removePrefix("tag:").removePrefix("mob:").trim()
        } else if (!node.params["destTag"].isNullOrBlank()) {
            initialTag = node.params["destTag"]?.trim() ?: ""
        }

        isSafePosition = node.params["safePosition"] != "false"
        isSnapToGround = node.params["snapToGround"] != "false"
        currentPriority = SearchLayerPriority.fromString(node.params["searchPriority"])

        val innerX = modalX + 16
        val innerW = modalWidth - 32

        // 1. Coordinate Edit Box
        coordBox = EditBox(font, innerX, modalY + 44, innerW, 18, Component.literal("Coordinates"))
        coordBox.setMaxLength(256)
        coordBox.value = initCoords
        coordBox.setHint(Component.literal("§8~0 ~0 ~0 (Player offset) or @guide_npc ^0 ^0 ^3 (Mob front)"))
        editBoxes.add(coordBox)

        // 2. Origin Reference Row (Player vs Tagged Mob)
        val originBtnW = 150
        val originLabel = if (originMode == "MOB_TAG") "🏷️ Origin: Tagged Mob" else "👤 Origin: Player"
        originToggleBtn = Button.builder(Component.literal(originLabel)) {
            originMode = if (originMode == "PLAYER") "MOB_TAG" else "PLAYER"
            originToggleBtn.message = Component.literal(if (originMode == "MOB_TAG") "🏷️ Origin: Tagged Mob" else "👤 Origin: Player")
            syncCoordsWithAnchor()
        }.bounds(innerX, modalY + 70, originBtnW, 16).build()

        anchorTagBox = EditBox(font, innerX + originBtnW + 6, modalY + 70, innerW - originBtnW - 6, 16, Component.literal("Story Tag"))
        anchorTagBox.setMaxLength(64)
        anchorTagBox.value = initialTag
        anchorTagBox.setHint(Component.literal("§8Story Tag (e.g. guide_npc, quest_boss)"))
        anchorTagBox.setResponder {
            if (originMode == "MOB_TAG") {
                syncCoordsWithAnchor()
            }
        }
        editBoxes.add(anchorTagBox)

        // 3. Preset Buttons (4 columns)
        val colW = (innerW - 9) / 4
        absBtn = Button.builder(Component.literal("🌐 Absolute")) {
            val p = Minecraft.getInstance().player
            val newCoords = if (p != null) "${p.blockX} ${p.blockY} ${p.blockZ}" else "0 64 0"
            coordBox.value = newCoords
        }.bounds(innerX, modalY + 94, colW, 16).build()

        relBtn = Button.builder(Component.literal("👤 Rel (~)")) {
            val tag = anchorTagBox.value.trim()
            if (originMode == "MOB_TAG" && tag.isNotBlank()) {
                coordBox.value = "@$tag ~0 ~0 ~0"
            } else if (originMode == "MOB_TAG") {
                coordBox.value = "@mob_tag ~0 ~0 ~0"
            } else {
                coordBox.value = "~0 ~0 ~0"
            }
        }.bounds(innerX + colW + 3, modalY + 94, colW, 16).build()

        frontBtn = Button.builder(Component.literal("👁️ Front (^)")) {
            val tag = anchorTagBox.value.trim()
            if (originMode == "MOB_TAG" && tag.isNotBlank()) {
                coordBox.value = "@$tag ^0 ^0 ^3"
            } else if (originMode == "MOB_TAG") {
                coordBox.value = "@mob_tag ^0 ^0 ^3"
            } else {
                coordBox.value = "^0 ^0 ^3"
            }
        }.bounds(innerX + (colW + 3) * 2, modalY + 94, colW, 16).build()

        pickPosBtn = Button.builder(Component.literal("📍 Pick Pos")) {
            val p = Minecraft.getInstance().player
            if (p != null) {
                coordBox.value = "${p.blockX} ${p.blockY} ${p.blockZ}"
            }
        }.bounds(innerX + (colW + 3) * 3, modalY + 94, colW, 16).build()

        // 4. Safety Controls
        val halfW = (innerW - 6) / 2
        val safeText = if (isSafePosition) "🛡️ Safe Position: ON" else "🛡️ Safe Position: OFF"
        safeBtn = Button.builder(Component.literal(safeText)) {
            isSafePosition = !isSafePosition
            safeBtn.message = Component.literal(if (isSafePosition) "🛡️ Safe Position: ON" else "🛡️ Safe Position: OFF")
        }.bounds(innerX, modalY + 142, halfW, 18).build()

        val snapText = if (isSnapToGround) "⚓ Snap to Ground: ON" else "⚓ Snap to Ground: OFF"
        snapBtn = Button.builder(Component.literal(snapText)) {
            isSnapToGround = !isSnapToGround
            snapBtn.message = Component.literal(if (isSnapToGround) "⚓ Snap to Ground: ON" else "⚓ Snap to Ground: OFF")
        }.bounds(innerX + halfW + 6, modalY + 142, halfW, 18).build()

        // 5. Layer Priority & Search Radius
        val priorityText = getPriorityLabel(currentPriority)
        priorityBtn = Button.builder(Component.literal(priorityText)) {
            currentPriority = when (currentPriority) {
                SearchLayerPriority.CLOSEST -> SearchLayerPriority.SURFACE
                SearchLayerPriority.SURFACE -> SearchLayerPriority.UNDERGROUND
                SearchLayerPriority.UNDERGROUND -> SearchLayerPriority.RANDOM
                SearchLayerPriority.RANDOM -> SearchLayerPriority.CLOSEST
            }
            priorityBtn.message = Component.literal(getPriorityLabel(currentPriority))
        }.bounds(innerX, modalY + 202, halfW, 18).build()

        radiusBox = EditBox(font, innerX + halfW + 6, modalY + 202, halfW, 18, Component.literal("Radius"))
        radiusBox.setMaxLength(4)
        radiusBox.value = node.params["maxSearchRadius"] ?: "5"
        radiusBox.setFilter { text -> text.isEmpty() || text.all { it.isDigit() } }
        editBoxes.add(radiusBox)

        editBoxes.forEach {
            it.setEditable(true)
            it.active = true
        }

        // Action Buttons
        saveBtn = Button.builder(Component.literal("💾 Apply Coordinates")) {
            val valText = coordBox.value.trim().ifBlank { "~ ~ ~" }
            node.params["coordinates"] = valText
            node.params["referenceIdentifier"] = valText
            node.params["selectorIdentifier"] = valText

            if (valText.startsWith("@")) {
                val spaceIdx = valText.indexOf(' ')
                val tagToken = if (spaceIdx != -1) valText.substring(0, spaceIdx) else valText
                node.params["destTag"] = tagToken.removePrefix("@").removePrefix("tag:").removePrefix("mob:").trim()
            } else if (originMode == "MOB_TAG" && anchorTagBox.value.isNotBlank()) {
                node.params["destTag"] = anchorTagBox.value.trim()
            }

            node.params["safePosition"] = isSafePosition.toString()
            node.params["snapToGround"] = isSnapToGround.toString()
            node.params["searchPriority"] = currentPriority.name
            node.params["maxSearchRadius"] = (radiusBox.value.toIntOrNull() ?: 5).coerceIn(1, 16).toString()

            onDataChanged()
            onClose()
        }.bounds(modalX + modalWidth - 160, modalY + modalHeight - 28, 144, 20).build()

        closeBtn = Button.builder(Component.literal("✖ Cancel")) {
            onClose()
        }.bounds(modalX + 16, modalY + modalHeight - 28, 90, 20).build()
    }

    private fun syncCoordsWithAnchor() {
        val current = coordBox.value.trim()
        val tag = anchorTagBox.value.trim()

        if (originMode == "MOB_TAG" && tag.isNotBlank()) {
            if (!current.startsWith("@")) {
                coordBox.value = "@$tag $current"
            } else {
                val spaceIdx = current.indexOf(' ')
                val rest = if (spaceIdx != -1) current.substring(spaceIdx + 1).trim() else "~0 ~0 ~0"
                coordBox.value = "@$tag $rest"
            }
        } else if (originMode == "PLAYER" && current.startsWith("@")) {
            val spaceIdx = current.indexOf(' ')
            val rest = if (spaceIdx != -1) current.substring(spaceIdx + 1).trim() else "~0 ~0 ~0"
            coordBox.value = rest
        }
    }

    private fun getPriorityLabel(priority: SearchLayerPriority): String {
        return when (priority) {
            SearchLayerPriority.SURFACE -> "☀️ Surface Only"
            SearchLayerPriority.UNDERGROUND -> "⛏️ Underground / Caves"
            SearchLayerPriority.RANDOM -> "🎲 Random Safe Spot"
            else -> "🎯 Closest Safe Spot"
        }
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Dark backdrop
        guiGraphics.fill(0, 0, screenWidth, screenHeight, 0x88000000.toInt())

        // Modal Frame
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF12141C.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 24, 0xFF1E2232.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 1, 0xFF38BDF8.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0xFF38BDF8.toInt())
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF38BDF8.toInt())
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0xFF38BDF8.toInt())

        // Header Title
        guiGraphics.drawString(font, "🧭 Coordinate & Positioning Settings", modalX + 14, modalY + 7, 0xFF38BDF8.toInt(), true)

        val innerX = modalX + 16
        val innerW = modalWidth - 32
        val halfW = (innerW - 6) / 2

        // Section 1: Coordinates & Anchor
        guiGraphics.drawString(font, "Target Coordinates (Resolved Vector / Anchor):", innerX, modalY + 32, 0xFFE2E8F0.toInt(), false)

        // Section 2: Safety
        guiGraphics.drawString(font, "Collision Clearance & Ground Snapping:", innerX, modalY + 126, 0xFFE2E8F0.toInt(), false)
        guiGraphics.drawString(font, "§8Prevents suffocating inside blocks and aligns on walkable ground.", innerX, modalY + 166, 0xFF94A3B8.toInt(), false)

        // Section 3: Layer & Radius
        guiGraphics.drawString(font, "Search Layer Priority:", innerX, modalY + 188, 0xFFE2E8F0.toInt(), false)
        guiGraphics.drawString(font, "Safe Search Radius (1-16):", innerX + halfW + 6, modalY + 188, 0xFFE2E8F0.toInt(), false)
        guiGraphics.drawString(font, "§8Surface (sky access), Caves (subterranean roof), Closest, or Random.", innerX, modalY + 226, 0xFF94A3B8.toInt(), false)

        // Render widgets
        coordBox.render(guiGraphics, mouseX, mouseY, partialTick)
        originToggleBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        anchorTagBox.render(guiGraphics, mouseX, mouseY, partialTick)
        radiusBox.render(guiGraphics, mouseX, mouseY, partialTick)

        absBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        relBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        frontBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        pickPosBtn.render(guiGraphics, mouseX, mouseY, partialTick)

        safeBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        snapBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        priorityBtn.render(guiGraphics, mouseX, mouseY, partialTick)

        saveBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        closeBtn.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (originToggleBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (absBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (relBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (frontBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (pickPosBtn.mouseClicked(mouseX, mouseY, button)) return true

        if (safeBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (snapBtn.mouseClicked(mouseX, mouseY, button)) return true
        if (priorityBtn.mouseClicked(mouseX, mouseY, button)) return true

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
        return mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= modalY && mouseY <= modalY + modalHeight
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

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        return mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= modalY && mouseY <= modalY + modalHeight
    }
}
