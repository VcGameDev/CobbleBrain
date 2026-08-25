package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.StoryProject
import vito.cobblebrain.model.StorySerializer

class StoryMetadataInspectorWidget(
    val project: StoryProject,
    val panelX: Int,
    val panelY: Int,
    val panelWidth: Int = 150,
    val panelHeight: Int,
    val font: Font,
    val onOpenMetadataModal: (() -> Unit)? = null,
    val onClose: () -> Unit,
    val onDataChanged: () -> Unit,
    val onDuplicateStory: (StoryProject) -> Unit,
    val onStatus: (String) -> Unit
) {
    val childrenWidgets = mutableListOf<GuiEventListener>()
    private var focusedEditBox: EditBox? = null
    private var idError: Boolean = false

    // Scrolling Support
    private var scrollOffset: Double = 0.0
    private var isDraggingScrollbar: Boolean = false
    private var dragStartMouseY: Double = 0.0
    private var dragStartScrollOffset: Double = 0.0

    private val viewportY get() = panelY + 22
    private val viewportBottom get() = panelY + panelHeight
    private val viewportH get() = panelHeight - 24
    private val contentHeight = 315.0

    private lateinit var closeBtn: Button
    private lateinit var idEdit: EditBox
    private lateinit var nameEdit: EditBox
    private lateinit var descEdit: EditBox
    private lateinit var verEdit: EditBox
    private lateinit var prereqBtn: Button
    private lateinit var copyBtn: Button
    private lateinit var dupBtn: Button

    init {
        buildUi()
    }

    fun buildUi() {
        childrenWidgets.clear()

        val inputX = panelX + 6
        val inputW = panelWidth - 14
        var currentY = panelY + 24

        // Close Button on Top Right Corner (Static header)
        closeBtn = Button.builder(Component.literal("✖")) {
            onClose()
        }.bounds(panelX + panelWidth - 18, panelY + 3, 15, 15).build()

        // 1. Story ID Field
        idEdit = EditBox(font, inputX, currentY + 10, inputW, 16, Component.literal("ID"))
        idEdit.setMaxLength(2000)
        idEdit.value = project.id
        idEdit.setFilter { text -> text.isEmpty() || text.matches(Regex("^[a-zA-Z0-9_-]+$")) }
        idEdit.setResponder { valText ->
            if (valText.isBlank() || !valText.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                idError = true
            } else {
                idError = false
                project.id = valText
                onDataChanged()
            }
        }
        childrenWidgets.add(idEdit)
        currentY += 30

        // 2. Display Name
        nameEdit = EditBox(font, inputX, currentY + 10, inputW, 16, Component.literal("Display Name"))
        nameEdit.setMaxLength(2000)
        nameEdit.value = project.name
        nameEdit.setResponder { valText ->
            project.name = valText
            onDataChanged()
        }
        childrenWidgets.add(nameEdit)
        currentY += 30

        // 3. Description
        descEdit = EditBox(font, inputX, currentY + 10, inputW, 36, Component.literal("Description"))
        descEdit.setMaxLength(9999)
        descEdit.value = project.description
        descEdit.setResponder { valText ->
            project.description = valText
            onDataChanged()
        }
        childrenWidgets.add(descEdit)
        currentY += 50

        // 4. Version (e.g. 1.0.0)
        verEdit = EditBox(font, inputX, currentY + 10, inputW, 16, Component.literal("Version"))
        verEdit.setMaxLength(2000)
        verEdit.value = project.version
        verEdit.setResponder { valText ->
            project.version = valText
            onDataChanged()
        }
        childrenWidgets.add(verEdit)
        currentY += 34

        // 5. "Edit Prerequisites" Button
        prereqBtn = Button.builder(Component.literal("🔒 Edit Prerequisites...")) {
            onOpenMetadataModal?.invoke()
        }.bounds(inputX, currentY + 5, inputW, 16).build()
        childrenWidgets.add(prereqBtn)
        currentY += 22

        // 6. "Copy Data" Button
        copyBtn = Button.builder(Component.literal("📋 Copy Data")) {
            val jsonStr = StorySerializer.toJson(project)
            onStatus("Story data copied to clipboard!")
        }.bounds(inputX, currentY + 5, inputW, 16).build()
        childrenWidgets.add(copyBtn)
        currentY += 22

        // 7. "Duplicate Story" Button
        dupBtn = Button.builder(Component.literal("📋 Duplicate Story")) {
            val newId = StoryProject.generateUniqueNewStoryId()
            val duplicate = StorySerializer.fromJson(StorySerializer.toJson(project)) ?: project
            duplicate.id = newId
            duplicate.name = "${project.name} (Copy)"
            onDuplicateStory(duplicate)
            onStatus("Story duplicated successfully: ${duplicate.id}")
        }.bounds(inputX, currentY + 5, inputW, 16).build()
        childrenWidgets.add(dupBtn)

        updateWidgetPositions()
    }

    private fun updateWidgetPositions() {
        val scroll = scrollOffset.toInt()
        var currentY = panelY + 24 - scroll

        idEdit.y = currentY + 10
        currentY += 30

        nameEdit.y = currentY + 10
        currentY += 30

        descEdit.y = currentY + 10
        currentY += 50

        verEdit.y = currentY + 10
        currentY += 34

        prereqBtn.y = currentY + 5
        currentY += 22

        copyBtn.y = currentY + 5
        currentY += 22

        dupBtn.y = currentY + 5
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0141418.toInt())
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 20, 0xFF22222A.toInt())

        guiGraphics.drawString(font, "📋 Story Metadata", panelX + 6, panelY + 5, 0xFF00FFCC.toInt(), false)
        closeBtn.render(guiGraphics, mouseX, mouseY, partialTick)

        // Enable Scissor for scrollable panel area
        guiGraphics.enableScissor(panelX + 1, viewportY, panelX + panelWidth - 1, viewportBottom)

        val scroll = scrollOffset.toInt()
        var currentY = panelY + 24 - scroll

        val idLabelColor = if (idError) 0xFFFF5555.toInt() else 0xFFA0A0A0.toInt()
        guiGraphics.drawString(font, if (idError) "ID (invalid!):" else "Story ID:", panelX + 6, currentY, idLabelColor, false)
        currentY += 30

        guiGraphics.drawString(font, "Display Name:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
        currentY += 30

        guiGraphics.drawString(font, "Description:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
        val charCount = project.description.length
        if (charCount >= 9000) {
            val countText = "$charCount/9999"
            val countColor = if (charCount >= 9999) 0xFFFF5555.toInt() else 0xFFFFEE55.toInt()
            val countW = font.width(countText)
            guiGraphics.drawString(font, countText, panelX + panelWidth - 6 - countW, currentY, countColor, false)
        }
        currentY += 50

        guiGraphics.drawString(font, "Version (e.g. 1.0.0):", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)

        childrenWidgets.toList().forEach { widget ->
            if (widget is Button) widget.render(guiGraphics, mouseX, mouseY, partialTick)
            if (widget is EditBox) widget.render(guiGraphics, mouseX, mouseY, partialTick)
        }

        guiGraphics.disableScissor()

        // Render Scrollbar
        val maxScroll = maxOf(0.0, contentHeight - viewportH)
        if (maxScroll > 0) {
            val scrollbarX = panelX + panelWidth - 4
            val scrollbarW = 3
            guiGraphics.fill(scrollbarX, viewportY, scrollbarX + scrollbarW, viewportBottom, 0x33000000)

            val thumbH = maxOf(16.0, (viewportH.toDouble() / contentHeight) * viewportH)
            val thumbY = viewportY + (scrollOffset / maxScroll) * (viewportH - thumbH)
            guiGraphics.fill(scrollbarX, thumbY.toInt(), scrollbarX + scrollbarW, (thumbY + thumbH).toInt(), 0xFF3D5AFE.toInt())
        }
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight) {
            val maxScroll = maxOf(0.0, contentHeight - viewportH)
            scrollOffset = (scrollOffset - scrollY * 18.0).coerceIn(0.0, maxScroll)
            updateWidgetPositions()
            return true
        }
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (isDraggingScrollbar) {
            val maxScroll = maxOf(0.0, contentHeight - viewportH)
            if (maxScroll > 0) {
                val thumbH = maxOf(16.0, (viewportH.toDouble() / contentHeight) * viewportH)
                val trackRange = viewportH - thumbH
                if (trackRange > 0) {
                    val deltaY = mouseY - dragStartMouseY
                    val scrollDelta = (deltaY / trackRange) * maxScroll
                    scrollOffset = (dragStartScrollOffset + scrollDelta).coerceIn(0.0, maxScroll)
                    updateWidgetPositions()
                    return true
                }
            }
        }
        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        isDraggingScrollbar = false
        return false
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (mouseX < panelX || mouseX > panelX + panelWidth || mouseY < panelY || mouseY > panelY + panelHeight) {
            focusedEditBox?.isFocused = false
            focusedEditBox = null
            return false
        }

        if (closeBtn.mouseClicked(mouseX, mouseY, button)) return true

        // Check Scrollbar Click
        val maxScroll = maxOf(0.0, contentHeight - viewportH)
        if (maxScroll > 0 && mouseX >= panelX + panelWidth - 8 && mouseX <= panelX + panelWidth && mouseY >= viewportY && mouseY <= viewportBottom) {
            isDraggingScrollbar = true
            dragStartMouseY = mouseY
            dragStartScrollOffset = scrollOffset
            return true
        }

        var handled = false
        val snapshot = childrenWidgets.toList()
        for (w in snapshot) {
            if (w is EditBox) {
                val clicked = w.mouseClicked(mouseX, mouseY, button)
                if (clicked) {
                    w.isFocused = true
                    focusedEditBox = w
                    handled = true
                } else {
                    w.isFocused = false
                }
            } else if (w.mouseClicked(mouseX, mouseY, button)) {
                handled = true
            }
        }
        return handled
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        val focused = focusedEditBox
        if (focused != null && focused.isFocused) return focused.charTyped(codePoint, modifiers)
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val focused = focusedEditBox
        if (focused != null && focused.isFocused) return focused.keyPressed(keyCode, scanCode, modifiers)
        return false
    }
}
