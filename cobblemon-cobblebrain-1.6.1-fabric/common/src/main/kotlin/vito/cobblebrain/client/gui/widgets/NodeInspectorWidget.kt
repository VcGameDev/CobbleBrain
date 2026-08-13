package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.NodeType
import vito.cobblebrain.model.PortData
import vito.cobblebrain.model.PortType

class NodeInspectorWidget(
    val node: NodeData,
    val panelX: Int,
    val panelY: Int,
    val panelWidth: Int = 140,
    val panelHeight: Int,
    val font: Font,
    val onClose: () -> Unit,
    val onDataChanged: () -> Unit,
    val onOpenConstruction: ((NodeData) -> Unit)? = null
) {
    val childrenWidgets = mutableListOf<GuiEventListener>()
    private var focusedEditBox: EditBox? = null

    init {
        buildUi()
    }

    fun buildUi() {
        childrenWidgets.clear()

        val inputX = panelX + 6
        val inputW = panelWidth - 12
        var currentY = panelY + 24

        // Botão Fechar no Canto Superior Direito
        val closeBtn = Button.builder(Component.literal("✖")) {
            onClose()
        }.bounds(panelX + panelWidth - 20, panelY + 3, 16, 16).build()
        childrenWidgets.add(closeBtn)

        // 1. Campo de Título do Nó
        val tEdit = EditBox(font, inputX, currentY + 10, inputW, 16, Component.literal("Título"))
        tEdit.setMaxLength(50)
        tEdit.value = node.title
        tEdit.setResponder { valText ->
            node.title = valText
            onDataChanged()
        }
        childrenWidgets.add(tEdit)
        currentY += 30

        // 2. Interface Dinâmica para os Tipos
        when (node.nodeType) {
            NodeType.COMMENT -> {
                val cEdit = EditBox(font, inputX, currentY + 10, inputW, 50, Component.literal("Nota / Comentário"))
                cEdit.setMaxLength(300)
                cEdit.value = node.content
                cEdit.setResponder { valText ->
                    node.content = valText
                    onDataChanged()
                }
                childrenWidgets.add(cEdit)
                currentY += 65
            }

            NodeType.LINK_SEND, NodeType.LINK_RECEIVE -> {
                val tagEdit = EditBox(font, inputX, currentY + 10, inputW, 16, Component.literal("Tag do Canal"))
                tagEdit.setMaxLength(50)
                tagEdit.value = node.params["channelTag"] ?: "canal_1"
                tagEdit.setResponder { valText ->
                    node.params["channelTag"] = valText
                    onDataChanged()
                }
                childrenWidgets.add(tagEdit)
                currentY += 30

                val isSend = node.nodeType == NodeType.LINK_SEND
                val infoText = if (isSend) "Transmite o sinal sem fio para os receptores da mesma Tag." else "Recebe o sinal do emissor Link Send com a mesma Tag."
                val lbl = EditBox(font, inputX, currentY + 10, inputW, 40, Component.literal("Info"))
                lbl.value = infoText
                lbl.active = false
                childrenWidgets.add(lbl)
                currentY += 54
            }

            NodeType.LOOP -> {
                val currentMode = node.params["loopMode"] ?: "COUNT"
                val modeY = currentY + 10
                val btnW = (inputW - 2) / 2

                val countBtn = Button.builder(Component.literal("Contagem")) {
                    node.params["loopMode"] = "COUNT"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, modeY, btnW, 14).build()
                if (currentMode == "COUNT") countBtn.active = false

                val timeBtn = Button.builder(Component.literal("Tempo")) {
                    node.params["loopMode"] = "TIME"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX + btnW + 2, modeY, btnW, 14).build()
                if (currentMode == "TIME") timeBtn.active = false

                childrenWidgets.add(countBtn)
                childrenWidgets.add(timeBtn)
                currentY += 28

                if (currentMode == "COUNT") {
                    val countEdit = createNumEdit(inputX, currentY + 10, inputW, "Repetições (Qtd)", node.params["loopCount"] ?: "5") { valText ->
                        node.params["loopCount"] = valText
                    }
                    childrenWidgets.add(countEdit)
                    currentY += 28
                }

                val intervalEdit = createNumEdit(inputX, currentY + 10, inputW, "Intervalo (Seg)", node.params["loopIntervalSec"] ?: "1.0") { valText ->
                    node.params["loopIntervalSec"] = valText
                }
                childrenWidgets.add(intervalEdit)
                currentY += 28
            }

            NodeType.BEGIN_SCENE -> {
                val labelText = "🟢 Ponto de entrada padrão da Cena. Dispara a porta OUT."
                val lbl = EditBox(font, inputX, currentY + 10, inputW, 36, Component.literal("Info"))
                lbl.value = labelText
                lbl.active = false
                childrenWidgets.add(lbl)
                currentY += 50
            }

            NodeType.END_SCENE -> {
                val labelText = "🛑 Finaliza a execução da Cena atual e dispara a saída OUT."
                val lbl = EditBox(font, inputX, currentY + 10, inputW, 36, Component.literal("Info"))
                lbl.value = labelText
                lbl.active = false
                childrenWidgets.add(lbl)
                currentY += 50
            }

            NodeType.GATE -> {
                val currentCount = node.inputs.size.coerceAtLeast(2)
                val f1 = createNumEdit(inputX, currentY + 10, inputW, "Qtd Entradas (2-5)", currentCount.toString()) { valText ->
                    val num = valText.toIntOrNull()?.coerceIn(2, 5) ?: 2
                    if (num != node.inputs.size) {
                        while (node.inputs.size < num) {
                            node.inputs.add(PortData(name = "IN ${node.inputs.size + 1}", type = PortType.INPUT))
                        }
                        while (node.inputs.size > num) {
                            node.inputs.removeAt(node.inputs.size - 1)
                        }
                        buildUi()
                        onDataChanged()
                    }
                }
                childrenWidgets.add(f1)
                currentY += 28
            }

            NodeType.CONSTRUCTION -> {
                val openBtn = Button.builder(Component.literal("🔍 Editar Interno")) {
                    onOpenConstruction?.invoke(node)
                }.bounds(inputX, currentY + 80, inputW, 18).build()
                childrenWidgets.add(openBtn)
                currentY += 104
            }

            NodeType.TRIGGER -> {
                // Alternador de Porta de Entrada (Porta IN)
                val requireInput = node.params["requireInputSignal"] != "false"
                val inBtnLabel = if (requireInput) "📥 Requer Sinal IN: SIM" else "📥 Requer Sinal IN: NÃO"
                val inToggleBtn = Button.builder(Component.literal(inBtnLabel)) {
                    val nextState = !(node.params["requireInputSignal"] != "false")
                    if (nextState) {
                        node.params["requireInputSignal"] = "true"
                        if (node.inputs.isEmpty()) {
                            node.inputs.add(PortData(name = "In", type = PortType.INPUT))
                        }
                    } else {
                        node.params["requireInputSignal"] = "false"
                        node.inputs.clear()
                    }
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, currentY + 10, inputW, 16).build()
                childrenWidgets.add(inToggleBtn)
                currentY += 28

                val currentCondMode = node.params["triggerCondition"] ?: "IF"
                val modeY = currentY + 10
                val btnW = (inputW - 2) / 2

                val ifBtn = Button.builder(Component.literal("IF")) {
                    node.params["triggerCondition"] = "IF"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, modeY, btnW, 14).build()
                if (currentCondMode == "IF") ifBtn.active = false

                val ifNotBtn = Button.builder(Component.literal("IF NOT")) {
                    node.params["triggerCondition"] = "IF_NOT"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX + btnW + 2, modeY, btnW, 14).build()
                if (currentCondMode == "IF_NOT") ifNotBtn.active = false

                childrenWidgets.add(ifBtn)
                childrenWidgets.add(ifNotBtn)
                currentY += 28

                val currentTrig = node.params["triggerType"] ?: "START"
                val subY = currentY + 10

                val startBtn = Button.builder(Component.literal("Início")) {
                    node.params["triggerType"] = "START"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, subY, btnW, 14).build()
                if (currentTrig == "START") startBtn.active = false

                val locBtn = Button.builder(Component.literal("Local")) {
                    node.params["triggerType"] = "LOCATION"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX + btnW + 2, subY, btnW, 14).build()
                if (currentTrig == "LOCATION") locBtn.active = false

                childrenWidgets.add(startBtn)
                childrenWidgets.add(locBtn)
                currentY += 28

                if (currentTrig == "LOCATION") {
                    val f1 = createNumEdit(inputX, currentY + 10, inputW, "Target X", node.params["targetX"] ?: "0") { node.params["targetX"] = it }
                    childrenWidgets.add(f1)
                    currentY += 28

                    val f2 = createNumEdit(inputX, currentY + 10, inputW, "Target Y", node.params["targetY"] ?: "64") { node.params["targetY"] = it }
                    childrenWidgets.add(f2)
                    currentY += 28

                    val f3 = createNumEdit(inputX, currentY + 10, inputW, "Target Z", node.params["targetZ"] ?: "0") { node.params["targetZ"] = it }
                    childrenWidgets.add(f3)
                    currentY += 28

                    val f4 = createNumEdit(inputX, currentY + 10, inputW, "Raio", node.params["radius"] ?: "5") { node.params["radius"] = it }
                    childrenWidgets.add(f4)
                    currentY += 28
                }
            }

            NodeType.ACTION -> {
                val currentAction = node.params["actionSubtype"] ?: "MESSAGE"
                val subY = currentY + 10
                val btnW = (inputW - 3) / 2

                val msgBtn = Button.builder(Component.literal("Msg")) {
                    node.params["actionSubtype"] = "MESSAGE"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, subY, btnW, 14).build()
                if (currentAction == "MESSAGE") msgBtn.active = false

                val tpBtn = Button.builder(Component.literal("TP")) {
                    node.params["actionSubtype"] = "TELEPORT"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX + btnW + 2, subY, btnW, 14).build()
                if (currentAction == "TELEPORT") tpBtn.active = false

                val spawnBtn = Button.builder(Component.literal("Spawn")) {
                    node.params["actionSubtype"] = "SPAWN"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, subY + 16, btnW, 14).build()
                if (currentAction == "SPAWN") spawnBtn.active = false

                val soundBtn = Button.builder(Component.literal("Som")) {
                    node.params["actionSubtype"] = "SOUND"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX + btnW + 2, subY + 16, btnW, 14).build()
                if (currentAction == "SOUND") soundBtn.active = false

                childrenWidgets.add(msgBtn)
                childrenWidgets.add(tpBtn)
                childrenWidgets.add(spawnBtn)
                childrenWidgets.add(soundBtn)
                currentY += 42

                when (currentAction) {
                    "TELEPORT" -> {
                        val f1 = createNumEdit(inputX, currentY + 10, inputW, "Dest X", node.params["destX"] ?: "0") { node.params["destX"] = it }
                        childrenWidgets.add(f1)
                        currentY += 28

                        val f2 = createNumEdit(inputX, currentY + 10, inputW, "Dest Y", node.params["destY"] ?: "64") { node.params["destY"] = it }
                        childrenWidgets.add(f2)
                        currentY += 28

                        val f3 = createNumEdit(inputX, currentY + 10, inputW, "Dest Z", node.params["destZ"] ?: "0") { node.params["destZ"] = it }
                        childrenWidgets.add(f3)
                        currentY += 28
                    }
                    "SPAWN" -> {
                        val f1 = EditBox(font, inputX, currentY + 10, inputW, 16, Component.literal("Espécie"))
                        f1.value = node.params["species"] ?: "Pikachu"
                        f1.setResponder { node.params["species"] = it; onDataChanged() }
                        childrenWidgets.add(f1)
                        currentY += 28

                        val f2 = createNumEdit(inputX, currentY + 10, inputW, "Nível", node.params["level"] ?: "5") { node.params["level"] = it }
                        childrenWidgets.add(f2)
                        currentY += 28
                    }
                    "SOUND" -> {
                        val f1 = EditBox(font, inputX, currentY + 10, inputW, 16, Component.literal("ID Som"))
                        f1.value = node.params["soundId"] ?: "minecraft:entity.player.levelup"
                        f1.setResponder { node.params["soundId"] = it; onDataChanged() }
                        childrenWidgets.add(f1)
                        currentY += 28

                        val f2 = EditBox(font, inputX, currentY + 10, inputW, 16, Component.literal("Volume"))
                        f2.value = node.params["volume"] ?: "1.0"
                        f2.setResponder { node.params["volume"] = it; onDataChanged() }
                        childrenWidgets.add(f2)
                        currentY += 28
                    }
                    else -> {
                        val cEdit = EditBox(font, inputX, currentY + 10, inputW, 28, Component.literal("Conteúdo"))
                        cEdit.setMaxLength(300)
                        cEdit.value = node.content
                        cEdit.setResponder { valText ->
                            node.content = valText
                            onDataChanged()
                        }
                        childrenWidgets.add(cEdit)
                        currentY += 42
                    }
                }
            }

            NodeType.TIMER -> {
                val f1 = createNumEdit(inputX, currentY + 10, inputW, "Segundos", node.params["timerSeconds"] ?: "5") { node.params["timerSeconds"] = it }
                childrenWidgets.add(f1)
                currentY += 28
            }

            NodeType.BRANCH -> {
                val currentCount = node.outputs.size.coerceAtLeast(2)
                val f1 = createNumEdit(inputX, currentY + 10, inputW, "Qtd Saídas (2-5)", currentCount.toString()) { valText ->
                    val num = valText.toIntOrNull()?.coerceIn(2, 5) ?: 2
                    if (num != node.outputs.size) {
                        while (node.outputs.size < num) {
                            node.outputs.add(PortData(name = "IF (Saída ${node.outputs.size + 1})", type = PortType.OUTPUT))
                        }
                        while (node.outputs.size > num) {
                            node.outputs.removeAt(node.outputs.size - 1)
                        }
                        buildUi()
                        onDataChanged()
                    }
                }
                childrenWidgets.add(f1)
                currentY += 28

                // Condição própria de cada saída (IF para cada porta de output)
                node.outputs.forEachIndexed { idx, port ->
                    val condEdit = EditBox(font, inputX, currentY + 10, inputW, 16, Component.literal("Condição ${idx + 1}"))
                    condEdit.value = port.name
                    condEdit.setResponder { valText ->
                        port.name = valText
                        onDataChanged()
                    }
                    childrenWidgets.add(condEdit)
                    currentY += 28
                }
            }

            NodeType.DIALOGUE -> {
                val cEdit = EditBox(font, inputX, currentY + 10, inputW, 28, Component.literal("Fala / Diálogo"))
                cEdit.setMaxLength(300)
                cEdit.value = node.content
                cEdit.setResponder { valText ->
                    node.content = valText
                    onDataChanged()
                }
                childrenWidgets.add(cEdit)
                currentY += 42

                val currentMsgType = node.params["messageType"] ?: "CHAT"
                val msgY = currentY + 10
                val btnW = (inputW - 4) / 3

                val chatBtn = Button.builder(Component.literal("Chat")) {
                    node.params["messageType"] = "CHAT"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, msgY, btnW, 14).build()
                if (currentMsgType == "CHAT") chatBtn.active = false

                val titleBtn = Button.builder(Component.literal("Title")) {
                    node.params["messageType"] = "TITLE"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX + btnW + 2, msgY, btnW, 14).build()
                if (currentMsgType == "TITLE") titleBtn.active = false

                val actionbarBtn = Button.builder(Component.literal("Bar")) {
                    node.params["messageType"] = "ACTION_BAR"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX + (btnW + 2) * 2, msgY, btnW, 14).build()
                if (currentMsgType == "ACTION_BAR") actionbarBtn.active = false

                childrenWidgets.add(chatBtn)
                childrenWidgets.add(titleBtn)
                childrenWidgets.add(actionbarBtn)
                currentY += 28
            }
        }
    }

    private fun createNumEdit(x: Int, y: Int, w: Int, label: String, initialVal: String, onUpdate: (String) -> Unit): EditBox {
        val eb = EditBox(font, x, y, w, 16, Component.literal(label))
        eb.value = initialVal
        eb.setFilter { text -> text.isEmpty() || text.all { it.isDigit() || it == '-' || it == '.' } }
        eb.setResponder { valText ->
            onUpdate(valText)
            onDataChanged()
        }
        return eb
    }

    private fun renderMiniMap(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        guiGraphics.fill(x, y, x + w, y + h, 0xFF0D0D12.toInt())
        guiGraphics.fill(x - 1, y - 1, x + w + 1, y, 0xFF3D5AFE.toInt())
        guiGraphics.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF3D5AFE.toInt())
        guiGraphics.fill(x - 1, y, x, y + h, 0xFF3D5AFE.toInt())
        guiGraphics.fill(x + w, y, x + w + 1, y + h, 0xFF3D5AFE.toInt())

        val inner = node.innerNodes
        if (inner.isEmpty()) {
            guiGraphics.drawString(font, "Vazio", x + w / 2 - 12, y + h / 2 - 4, 0xFF555566.toInt(), false)
            return
        }

        val minX = inner.minOf { it.x }
        val minY = inner.minOf { it.y }
        val maxX = inner.maxOf { it.x + it.width }.coerceAtLeast(minX + 1.0)
        val maxY = inner.maxOf { it.y + it.height }.coerceAtLeast(minY + 1.0)

        val boundsW = maxX - minX
        val boundsH = maxY - minY

        val scaleX = (w - 12) / boundsW
        val scaleY = (h - 12) / boundsH
        val scale = minOf(scaleX, scaleY).coerceIn(0.05, 0.5)

        node.innerConnections.forEach { conn ->
            val from = inner.find { it.id == conn.fromNodeId }
            val to = inner.find { it.id == conn.toNodeId }
            if (from != null && to != null) {
                val fx = (x + 6 + (from.x + from.width - minX) * scale).toInt()
                val fy = (y + 6 + (from.y + 20 - minY) * scale).toInt()
                val tx = (x + 6 + (to.x - minX) * scale).toInt()
                val ty = (y + 6 + (to.y + 20 - minY) * scale).toInt()
                guiGraphics.fill(minOf(fx, tx), minOf(fy, ty), maxOf(fx, tx) + 1, maxOf(fy, ty) + 1, 0xFF4CAF50.toInt())
            }
        }

        inner.forEach { sub ->
            val nx = (x + 6 + (sub.x - minX) * scale).toInt()
            val ny = (y + 6 + (sub.y - minY) * scale).toInt()
            val nw = (sub.width * scale).toInt().coerceAtLeast(4)
            val nh = (sub.height * scale).toInt().coerceAtLeast(4)

            val color = when (sub.nodeType) {
                NodeType.BEGIN_SCENE -> 0xFF388E3C.toInt()
                NodeType.TRIGGER -> 0xFF2E7D32.toInt()
                NodeType.ACTION -> 0xFFC62828.toInt()
                NodeType.TIMER -> 0xFF6A1B9A.toInt()
                NodeType.BRANCH -> 0xFFF57F17.toInt()
                NodeType.DIALOGUE -> 0xFF1565C0.toInt()
                NodeType.END_SCENE -> 0xFFD32F2F.toInt()
                NodeType.GATE -> 0xFF00B0FF.toInt()
                NodeType.LINK_SEND -> 0xFF00E676.toInt()
                NodeType.LINK_RECEIVE -> 0xFF0288D1.toInt()
                NodeType.LOOP -> 0xFFFF6D00.toInt()
                NodeType.COMMENT -> 0xFFFBC02D.toInt()
                else -> 0xFF00838F.toInt()
            }
            guiGraphics.fill(nx, ny, nx + nw, ny + nh, color)
        }
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0141418.toInt())
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 20, 0xFF22222A.toInt())

        val headerTitle = font.plainSubstrByWidth(node.title, panelWidth - 26)
        guiGraphics.drawString(font, headerTitle, panelX + 6, panelY + 5, 0xFF00FFCC.toInt(), false)

        var currentY = panelY + 24
        guiGraphics.drawString(font, "Título:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
        currentY += 30

        val actionSubtype = node.params["actionSubtype"] ?: "MESSAGE"
        val trigSubtype = node.params["triggerType"] ?: "START"
        val loopMode = node.params["loopMode"] ?: "COUNT"

        when (node.nodeType) {
            NodeType.COMMENT -> {
                guiGraphics.drawString(font, "Nota / Comentário:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
            }
            NodeType.LINK_SEND -> {
                guiGraphics.drawString(font, "Tag do Canal:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
            }
            NodeType.LINK_RECEIVE -> {
                guiGraphics.drawString(font, "Tag do Canal:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
            }
            NodeType.LOOP -> {
                guiGraphics.drawString(font, "Modo Operação:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                currentY += 28
                if (loopMode == "COUNT") {
                    guiGraphics.drawString(font, "Repetições (Qtd):", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                    currentY += 28
                }
                guiGraphics.drawString(font, "Intervalo (Seg):", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
            }
            NodeType.BEGIN_SCENE -> {
                guiGraphics.drawString(font, "Tipo: Início da Cena", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
            }
            NodeType.END_SCENE -> {
                guiGraphics.drawString(font, "Tipo: Finalização", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
            }
            NodeType.GATE -> {
                guiGraphics.drawString(font, "Sincronizador GATE:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
            }
            NodeType.CONSTRUCTION -> {
                guiGraphics.drawString(font, "Mini-Mapa Interno:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                renderMiniMap(guiGraphics, panelX + 6, currentY + 10, panelWidth - 12, 65)
                currentY += 80
            }
            NodeType.TRIGGER -> {
                guiGraphics.drawString(font, "Entrada IN:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                currentY += 28
                guiGraphics.drawString(font, "Modo Lógico:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                currentY += 28
                guiGraphics.drawString(font, "Tipo Gatilho:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                currentY += 28
                if (trigSubtype == "LOCATION") {
                    guiGraphics.drawString(font, "Target X:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                    currentY += 28
                    guiGraphics.drawString(font, "Target Y:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                    currentY += 28
                    guiGraphics.drawString(font, "Target Z:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                    currentY += 28
                    guiGraphics.drawString(font, "Raio (Blocos):", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                }
            }
            NodeType.ACTION -> {
                guiGraphics.drawString(font, "Tipo Ação:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                currentY += 42
                when (actionSubtype) {
                    "TELEPORT" -> {
                        guiGraphics.drawString(font, "Destino X:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                        currentY += 28
                        guiGraphics.drawString(font, "Destino Y:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                        currentY += 28
                        guiGraphics.drawString(font, "Destino Z:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                    }
                    "SPAWN" -> {
                        guiGraphics.drawString(font, "Espécie Pokémon:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                        currentY += 28
                        guiGraphics.drawString(font, "Nível:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                    }
                    "SOUND" -> {
                        guiGraphics.drawString(font, "ID Som:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                        currentY += 28
                        guiGraphics.drawString(font, "Volume:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                    }
                    else -> {
                        guiGraphics.drawString(font, "Texto / Conteúdo:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                    }
                }
            }
            NodeType.TIMER -> {
                guiGraphics.drawString(font, "Espera (Segundos):", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
            }
            NodeType.BRANCH -> {
                guiGraphics.drawString(font, "Qtd de Saídas:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                currentY += 28
                node.outputs.forEachIndexed { idx, _ ->
                    guiGraphics.drawString(font, "Condição IF #${idx + 1}:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                    currentY += 28
                }
            }
            NodeType.DIALOGUE -> {
                guiGraphics.drawString(font, "Fala / Diálogo:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
                currentY += 42
                guiGraphics.drawString(font, "Modo Exibição:", panelX + 6, currentY, 0xFFA0A0A0.toInt(), false)
            }
        }

        childrenWidgets.toList().forEach { widget ->
            if (widget is Button) widget.render(guiGraphics, mouseX, mouseY, partialTick)
            if (widget is EditBox) widget.render(guiGraphics, mouseX, mouseY, partialTick)
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (mouseX < panelX || mouseX > panelX + panelWidth || mouseY < panelY || mouseY > panelY + panelHeight) {
            focusedEditBox?.isFocused = false
            focusedEditBox = null
            return false
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
        if (focused != null && focused.isFocused) {
            return focused.charTyped(codePoint, modifiers)
        }
        val snapshot = childrenWidgets.toList()
        for (w in snapshot) {
            if (w is EditBox && w.isFocused) {
                if (w.charTyped(codePoint, modifiers)) return true
            }
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val focused = focusedEditBox
        if (focused != null && focused.isFocused) {
            if (focused.keyPressed(keyCode, scanCode, modifiers)) return true
        }
        val snapshot = childrenWidgets.toList()
        for (w in snapshot) {
            if (w is EditBox && w.isFocused) {
                if (w.keyPressed(keyCode, scanCode, modifiers)) return true
            }
        }
        return false
    }
}
