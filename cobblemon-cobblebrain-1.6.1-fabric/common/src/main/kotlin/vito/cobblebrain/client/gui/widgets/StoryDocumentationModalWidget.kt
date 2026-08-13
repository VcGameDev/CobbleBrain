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

    private var activeTab: Int = 0 // 0: Estrutura, 1: Lógica, 2: Links & Loops
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

        val t1 = Button.builder(Component.literal("Estrutura & Cenas")) {
            activeTab = 0
            buildUi()
        }.bounds(modalX + 10, tabY, tabW, 16).build()
        if (activeTab == 0) t1.active = false

        val t2 = Button.builder(Component.literal("Lógica & Fluxo")) {
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
        // Fundo escurecido translúcido 100% da tela (0xF0101014)
        guiGraphics.fill(0, 0, screenW, screenH, 0xF0101014.toInt())

        // Janela do Modal sólida e opaca (0xFF121216) para bloquear 100% de visão dos elementos atrás
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF121216.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 22, 0xFF282836.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 1, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX, modalY + modalH - 1, modalX + modalW, modalY + modalH, 0xFF3D5AFE.toInt())

        guiGraphics.drawString(font, "📖 Guia Integrado dos Nós do Editor", modalX + 10, modalY + 7, 0xFF00FFCC.toInt(), false)

        childrenWidgets.toList().forEach { w ->
            if (w is Button) w.render(guiGraphics, mouseX, mouseY, partialTick)
        }

        val contentX = modalX + 12
        var currentY = modalY + 48

        when (activeTab) {
            0 -> { // Estrutura & Cenas
                drawDocItem(guiGraphics, contentX, currentY, "🟢 BEGIN_SCENE (Início da Cena)", "Entradas: Nenhuma | Saídas: OUT", "Ponto de entrada único obrigatório dentro de cada cena.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "🛑 END_SCENE (Finalizar Cena)", "Entradas: IN | Saídas: Nenhuma", "Encerra a cena atual e dispara a saída OUT da cena no grafo.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "⚡ GATE (Portão Sincronizador)", "Entradas: 2 a 5 IN | Saídas: OUT", "Só dispara a saída OUT quando TODAS as portas IN receberem o sinal.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "🏗️ CONSTRUCTION (Sub-Grafo)", "Entradas: IN | Saídas: OUT", "Encapsula um sub-canvas interno reutilizável para grafos complexos.")
            }
            1 -> { // Lógica & Fluxo
                drawDocItem(guiGraphics, contentX, currentY, "🟢 TRIGGER (Gatilho)", "Entradas: IN (opcional) | Saídas: OUT", "Dispara por início de história ou por colisão/posição X,Y,Z.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "⚡ ACTION (Ação do Mundo)", "Entradas: IN | Saídas: OUT", "Executa mensagens de chat/title, teleporte, spawn ou sons.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "⏱ TIMER (Temporizador)", "Entradas: IN | Saídas: OUT", "Aguarde rigorosamente N segundos de forma assíncrona antes do OUT.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "🔀 BRANCH (Ramificação IF)", "Entradas: IN | Saídas: 2 a 5 IFs", "Avalia condições e desvia o fluxo pela saída correspondente.")
            }
            2 -> { // Links & Loops
                drawDocItem(guiGraphics, contentX, currentY, "📡 LINK_SEND (Transmissor)", "Entradas: IN | Saídas: Nenhuma", "Transmite o sinal sem fio via canal nomeado (channelTag).")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "📡 LINK_RECEIVE (Receptor)", "Entradas: Nenhuma | Saídas: OUT", "Recebe o salto sem fio e dispara a saída OUT imediatamente.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "🔄 LOOP (Repetidor)", "Entradas: IN, STOP | Saídas: CYCLE, DONE", "Itera por contagem (N vezes) ou tempo continuo sem travar a thread.")
                currentY += 45
                drawDocItem(guiGraphics, contentX, currentY, "📝 COMMENT (Comentário)", "Entradas: Nenhuma | Saídas: Nenhuma", "Bloco visual discreto para notas e documentação no canvas.")
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
}
