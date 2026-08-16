package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.SceneData

class SceneInspectorWidget(
    val scene: SceneData,
    val panelX: Int,
    val panelY: Int,
    val panelWidth: Int = 140,
    val panelHeight: Int,
    val font: Font,
    val onClose: () -> Unit,
    val onDataChanged: () -> Unit
) {
    private data class InspectorLabel(val text: String, val relY: Int)
    private data class InspectorWidgetItem(val widget: GuiEventListener, val relX: Int, val relY: Int, val width: Int, val height: Int)

    val childrenWidgets = mutableListOf<GuiEventListener>()
    private val widgetItems = mutableListOf<InspectorWidgetItem>()
    private val labels = mutableListOf<InspectorLabel>()

    private var focusedEditBox: EditBox? = null
    private var scrollOffset: Double = 0.0
    private var totalContentHeight: Double = 0.0

    private val closeBtn: Button

    init {
        closeBtn = Button.builder(Component.literal("✖")) {
            onClose()
        }.bounds(panelX + panelWidth - 20, panelY + 2, 16, 16).build()

        buildUi()
    }

    fun buildUi() {
        childrenWidgets.clear()
        widgetItems.clear()
        labels.clear()

        val inputX = panelX + 6
        val inputW = panelWidth - 12
        var relY = 4

        labels.add(InspectorLabel("Scene Name:", relY))
        relY += 12

        val tEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Name"))
        tEdit.setMaxLength(50)
        tEdit.value = scene.title
        tEdit.setResponder { valText ->
            scene.title = valText
            onDataChanged()
        }
        addWidgetItem(tEdit, relY, 16)
        relY += 22

        labels.add(InspectorLabel("Description:", relY))
        relY += 12

        val descEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 36, Component.literal("Description"))
        descEdit.setMaxLength(250)
        descEdit.value = scene.description
        descEdit.setResponder { valText ->
            scene.description = valText
            onDataChanged()
        }
        addWidgetItem(descEdit, relY, 36)
        relY += 42

        labels.add(InspectorLabel("Global Properties:", relY))
        relY += 12

        // Toggle Start Scene
        val startLabel = if (scene.isStartScene) "🟢 Start Scene: YES" else "⚪ Start Scene: NO"
        val startBtn = Button.builder(Component.literal(startLabel)) {
            scene.isStartScene = !scene.isStartScene
            buildUi()
            onDataChanged()
        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
        addWidgetItem(startBtn, relY, 16)
        relY += 22

        // Toggle End Scene
        val endLabel = if (scene.isEndScene) "🛑 End Scene: YES" else "⚪ End Scene: NO"
        val endBtn = Button.builder(Component.literal(endLabel)) {
            scene.isEndScene = !scene.isEndScene
            buildUi()
            onDataChanged()
        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
        addWidgetItem(endBtn, relY, 16)
        relY += 22

        totalContentHeight = relY.toDouble()
        updateWidgetPositions()
    }

    private fun addWidgetItem(widget: GuiEventListener, relY: Int, height: Int) {
        if (widget is EditBox) {
            widget.setEditable(true)
            widget.active = true
        }
        childrenWidgets.add(widget)
        val relX = when (widget) {
            is Button -> widget.x - panelX
            is EditBox -> widget.x - panelX
            else -> 6
        }
        val width = when (widget) {
            is Button -> widget.width
            is EditBox -> widget.width
            else -> panelWidth - 12
        }
        widgetItems.add(InspectorWidgetItem(widget, relX, relY, width, height))
    }

    private fun updateWidgetPositions() {
        widgetItems.forEach { item ->
            val px = panelX + item.relX
            val py = (panelY + 20 + item.relY - scrollOffset).toInt()
            if (item.widget is Button) {
                item.widget.x = px
                item.widget.y = py
                item.widget.width = item.width
            } else if (item.widget is EditBox) {
                item.widget.x = px
                item.widget.y = py
                item.widget.width = item.width
            }
        }
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Inspector Panel Background
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0141418.toInt())
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF3D5AFE.toInt())

        val viewportTop = panelY + 20
        val viewportBottom = panelY + panelHeight
        val viewportHeight = panelHeight - 20

        guiGraphics.enableScissor(panelX, viewportTop, panelX + panelWidth, viewportBottom)

        labels.forEach { lbl ->
            val ly = (viewportTop + lbl.relY - scrollOffset).toInt()
            if (ly >= viewportTop - 12 && ly <= viewportBottom) {
                guiGraphics.drawString(font, lbl.text, panelX + 6, ly, 0xFFA0A0A0.toInt(), false)
            }
        }

        childrenWidgets.toList().forEach { widget ->
            if (widget != closeBtn) {
                val wy = when (widget) {
                    is Button -> widget.y
                    is EditBox -> widget.y
                    else -> viewportTop
                }
                if (wy + 16 >= viewportTop && wy <= viewportBottom) {
                    if (widget is Button) widget.render(guiGraphics, mouseX, mouseY, partialTick)
                    if (widget is EditBox) widget.render(guiGraphics, mouseX, mouseY, partialTick)
                }
            }
        }

        guiGraphics.disableScissor()

        val maxScroll = maxOf(0.0, totalContentHeight - viewportHeight)
        if (maxScroll > 0) {
            val sbX = panelX + panelWidth - 3
            val thumbH = ((viewportHeight.toDouble() / totalContentHeight) * viewportHeight).toInt().coerceIn(12, viewportHeight)
            val thumbY = viewportTop + ((scrollOffset / maxScroll) * (viewportHeight - thumbH)).toInt()

            guiGraphics.fill(sbX, viewportTop, sbX + 2, viewportBottom, 0xFF1C1C24.toInt())
            guiGraphics.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, 0xFF00FFCC.toInt())
        }

        // Fixed Top Header
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 20, 0xFF22222A.toInt())
        guiGraphics.fill(panelX, panelY + 19, panelX + panelWidth, panelY + 20, 0xFF3D5AFE.toInt())

        val headerTitle = font.plainSubstrByWidth("Scene: ${scene.title}", panelWidth - 26)
        guiGraphics.drawString(font, headerTitle, panelX + 6, panelY + 5, 0xFF00FFCC.toInt(), false)

        closeBtn.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY + 20 && mouseY <= panelY + panelHeight) {
            val viewportHeight = panelHeight - 20
            val maxScroll = maxOf(0.0, totalContentHeight - viewportHeight)
            if (maxScroll > 0) {
                if (scrollY > 0) {
                    scrollOffset = (scrollOffset - 18.0).coerceAtLeast(0.0)
                } else if (scrollY < 0) {
                    scrollOffset = (scrollOffset + 18.0).coerceAtMost(maxScroll)
                }
                updateWidgetPositions()
                return true
            }
        }
        return false
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (mouseX < panelX || mouseX > panelX + panelWidth || mouseY < panelY || mouseY > panelY + panelHeight) {
            focusedEditBox?.isFocused = false
            focusedEditBox = null
            return false
        }

        if (closeBtn.mouseClicked(mouseX, mouseY, button)) return true

        val viewportTop = panelY + 20
        val viewportBottom = panelY + panelHeight

        var handled = false
        val snapshot = childrenWidgets.toList()
        for (w in snapshot) {
            if (w == closeBtn) continue

            val wy = when (w) {
                is Button -> w.y
                is EditBox -> w.y
                else -> viewportTop
            }

            if (wy + 14 >= viewportTop && wy <= viewportBottom) {
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
        }
        if (!handled) {
            focusedEditBox?.isFocused = false
            focusedEditBox = null
        }
        return true
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
