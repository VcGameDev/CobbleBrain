package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.network.chat.Component

class StoryDocumentationModalWidget(
    val font: Font,
    val screenW: Int,
    val screenH: Int,
    val onClose: () -> Unit
) {
    val modalW = 380
    val modalH = 240
    val modalX = (screenW - modalW) / 2
    val modalY = (screenH - modalH) / 2

    private var activeTab: Int = 0 // 0: Structure, 1: Logic, 2: Links & Loops
    val childrenWidgets = mutableListOf<GuiEventListener>()

    init {
        buildUi()
    }

    fun buildUi() {
        childrenWidgets.clear()

        val closeBtn = Button.builder(Component.literal("✖")) {
            onClose()
        }.bounds(modalX + modalW - 22, modalY + 4, 18, 18).build()
        childrenWidgets.add(closeBtn)

        val tabW = 110
        val tabY = modalY + 26

        val t1 = Button.builder(Component.literal("Structure & Scenes")) {
            activeTab = 0
            buildUi()
        }.bounds(modalX + 10, tabY, tabW, 16).build()
        if (activeTab == 0) t1.active = false

        val t2 = Button.builder(Component.literal("Logic & Flow")) {
            activeTab = 1
            buildUi()
        }.bounds(modalX + 125, tabY, tabW, 16).build()
        if (activeTab == 1) t2.active = false

        val t3 = Button.builder(Component.literal("Links & Loops")) {
            activeTab = 2
            buildUi()
        }.bounds(modalX + 240, tabY, tabW, 16).build()
        if (activeTab == 2) t3.active = false

        childrenWidgets.add(t1)
        childrenWidgets.add(t2)
        childrenWidgets.add(t3)
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Translucent dark background over 100% of the screen
        guiGraphics.fill(0, 0, screenW, screenH, 0xF0101014.toInt())

        // Solid modal window
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF121216.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 22, 0xFF282836.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 1, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX, modalY + modalH - 1, modalX + modalW, modalY + modalH, 0xFF3D5AFE.toInt())

        guiGraphics.drawString(font, "📖 Integrated Editor Nodes Guide", modalX + 10, modalY + 7, 0xFF00FFCC.toInt(), false)

        childrenWidgets.toList().forEach { w ->
            if (w is Button) w.render(guiGraphics, mouseX, mouseY, partialTick)
        }

        val contentX = modalX + 12
        var currentY = modalY + 48

        when (activeTab) {
            0 -> { // Structure & Scenes
                drawDocItem(guiGraphics, contentX, currentY, "🟢 BEGIN_SCENE (Scene Start)", "Inputs: None | Outputs: OUT", "Mandatory single entry point inside each scene graph.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "🛑 END_SCENE (Finish Scene)", "Inputs: IN | Outputs: None", "Terminates current scene and fires scene OUT signal.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "🏗️ BEGIN_CONSTRUCTION (Build Start)", "Inputs: None | Outputs: OUT", "Entry point inside construction sub-graph. Fires OUT to start internal building flow.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "🏁 END_CONSTRUCTION (Build Finish)", "Inputs: IN | Outputs: None", "Exit point inside construction sub-graph. Completes build and fires outer OUT signal.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "⚡ GATE (Synchronizer Gate)", "Inputs: 2 to 5 IN | Outputs: OUT", "Fires OUT output only when ALL IN ports receive signal.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "🏗️ CONSTRUCTION (Sub-Graph)", "Inputs: IN | Outputs: OUT", "Encapsulates reusable internal sub-canvas for complex logic.")
            }
            1 -> { // Logic & Flow
                drawDocItem(guiGraphics, contentX, currentY, "🟢 TRIGGER", "Inputs: IN (optional) | Outputs: OUT", "Triggers on story start, coordinates, day/night, weather, level, or variable check.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "⚡ ACTION", "Inputs: IN | Outputs: OUT", "Executes chat/title messages, teleport, spawn, or audio.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "⌨️ COMMAND_NODE", "Inputs: IN | Outputs: OUT", "Executes commands via Server (OP 4) or Local Player with token interpolation.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "⏱ TIMER", "Inputs: IN | Outputs: OUT", "Waits specified N seconds asynchronously before OUT.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "🔀 CONDITION_NODE", "Inputs: IN | Outputs: IF, ELSE IF 1..N, ELSE", "Evaluates conditions in cascade and routes signal to the first true branch or ELSE.")
            }
            2 -> { // Links & Loops
                drawDocItem(guiGraphics, contentX, currentY, "📡 LINK_SEND (Sender)", "Inputs: IN | Outputs: None", "Transmits wireless signal via named channel (channelTag).")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "📡 LINK_RECEIVE (Receiver)", "Inputs: None | Outputs: OUT", "Receives wireless jump signal and fires OUT immediately.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "🔄 LOOP (Repeater)", "Inputs: IN, STOP | Outputs: CYCLE, DONE", "Iterates by count (N times) or continuous interval without blocking.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "📝 COMMENT (Note)", "Inputs: None | Outputs: None", "Discrete visual note block for canvas documentation.")
            }
        }
    }

    private fun drawDocItem(guiGraphics: GuiGraphics, x: Int, y: Int, title: String, ports: String, desc: String) {
        guiGraphics.drawString(font, title, x, y, 0xFFFFD700.toInt(), false)
        guiGraphics.drawString(font, ports, x, y + 11, 0xFF00FFCC.toInt(), false)
        guiGraphics.drawString(font, desc, x, y + 22, 0xFFA0A0A0.toInt(), false)
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
            onClose()
            return true
        }
        val snapshot = childrenWidgets.toList()
        for (w in snapshot) {
            if (w.mouseClicked(mouseX, mouseY, button)) return true
        }
        return true
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        if (scrollY < 0) {
            activeTab = (activeTab + 1) % 3
            buildUi()
            return true
        } else if (scrollY > 0) {
            activeTab = if (activeTab <= 0) 2 else activeTab - 1
            buildUi()
            return true
        }
        return false
    }
}
