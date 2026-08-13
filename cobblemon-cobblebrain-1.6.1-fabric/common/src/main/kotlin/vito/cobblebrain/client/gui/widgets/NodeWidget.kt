package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.NodeType
import vito.cobblebrain.model.PortData
import vito.cobblebrain.model.PortType
import kotlin.math.sqrt

class NodeWidget(val node: NodeData) {

    var isSelected: Boolean = false
    var isDragging: Boolean = false

    private val headerHeight = 20
    private val portRadius = 5.0

    fun getHeaderColor(): Int {
        return when (node.nodeType) {
            NodeType.BEGIN_SCENE -> 0xFF388E3C.toInt()  // Verde Início (Ponto de Entrada da Cena)
            NodeType.TRIGGER -> 0xFF2E7D32.toInt()      // Verde (Gatilho)
            NodeType.ACTION -> 0xFFC62828.toInt()       // Vermelho (Ação)
            NodeType.TIMER -> 0xFF6A1B9A.toInt()        // Roxo (Timer)
            NodeType.BRANCH -> 0xFFF57F17.toInt()       // Laranja (Ramificação)
            NodeType.DIALOGUE -> 0xFF1565C0.toInt()     // Azul (Diálogo)
            NodeType.CONSTRUCTION -> 0xFF00838F.toInt() // Teal (Construção)
            NodeType.END_SCENE -> 0xFFD32F2F.toInt()   // Vermelho Escuro (Finalizar Cena)
            NodeType.GATE -> 0xFF00B0FF.toInt()        // Ciano (Portão Sincronizador)
            NodeType.LINK_SEND -> 0xFF00E676.toInt()   // Verde Ciano (Emissor Link)
            NodeType.LINK_RECEIVE -> 0xFF0288D1.toInt()// Azul Ciano (Receptor Link)
            NodeType.LOOP -> 0xFFFF6D00.toInt()        // Laranja Vibrante (Repetidor Loop)
            NodeType.COMMENT -> 0xFFFBC02D.toInt()     // Amarelo Nota (Bloco de Comentário)
        }
    }

    fun getInputPortWorldPos(index: Int): Pair<Double, Double> {
        val total = maxOf(1, node.inputs.size)
        val step = (node.height - headerHeight) / (total + 1)
        val portX = node.x
        val portY = node.y + headerHeight + step * (index + 1)
        return Pair(portX, portY)
    }

    fun getOutputPortWorldPos(index: Int): Pair<Double, Double> {
        val total = maxOf(1, node.outputs.size)
        val step = (node.height - headerHeight) / (total + 1)
        val portX = node.x + node.width
        val portY = node.y + headerHeight + step * (index + 1)
        return Pair(portX, portY)
    }

    fun getPortAtWorldPos(worldX: Double, worldY: Double, radiusMultiplier: Double = 1.5): Pair<PortData, PortType>? {
        if (node.nodeType == NodeType.COMMENT) return null
        val effectiveRadius = portRadius * radiusMultiplier

        node.inputs.forEachIndexed { idx, port ->
            val (px, py) = getInputPortWorldPos(idx)
            val dist = sqrt((worldX - px) * (worldX - px) + (worldY - py) * (worldY - py))
            if (dist <= effectiveRadius) {
                return Pair(port, PortType.INPUT)
            }
        }

        node.outputs.forEachIndexed { idx, port ->
            val (px, py) = getOutputPortWorldPos(idx)
            val dist = sqrt((worldX - px) * (worldX - px) + (worldY - py) * (worldY - py))
            if (dist <= effectiveRadius) {
                return Pair(port, PortType.OUTPUT)
            }
        }

        return null
    }

    fun isWorldPosInside(worldX: Double, worldY: Double): Boolean {
        return worldX >= node.x && worldX <= node.x + node.width &&
               worldY >= node.y && worldY <= node.y + node.height
    }

    fun render(
        guiGraphics: GuiGraphics,
        font: Font,
        hoveredPort: PortData? = null,
        isModalOpen: Boolean = false
    ) {
        // Formatos compactos e estilizados por tipo
        when (node.nodeType) {
            NodeType.GATE -> {
                node.width = 180.0
                node.height = 60.0
            }
            NodeType.LINK_SEND, NodeType.LINK_RECEIVE -> {
                node.width = 160.0
                node.height = 55.0
            }
            NodeType.LOOP -> {
                node.width = 180.0
                node.height = 70.0
            }
            NodeType.COMMENT -> {
                node.width = 200.0
                node.height = 50.0
            }
            else -> {}
        }

        val x = node.x.toInt()
        val y = node.y.toInt()
        val w = node.width.toInt()
        val h = node.height.toInt()

        // Estilo Especial para Bloco de Comentário (Estilo Post-It/Nota Plana)
        if (node.nodeType == NodeType.COMMENT) {
            val noteBorder = if (isSelected) 0xFFFFD700.toInt() else 0xFFFBC02D.toInt()
            guiGraphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, noteBorder)
            guiGraphics.fill(x, y, x + w, y + h, 0xEE222016.toInt())

            guiGraphics.fill(x, y, x + w, y + 16, 0xFF3D3820.toInt())
            if (!isModalOpen) {
                guiGraphics.drawString(font, "📝 ${node.title}", x + 6, y + 4, 0xFFFBC02D.toInt(), false)
                val commentText = font.plainSubstrByWidth(node.content.ifBlank { "Sua nota / explicação..." }, w - 12)
                guiGraphics.drawString(font, commentText, x + 6, y + 24, 0xFFE0E0D0.toInt(), false)
            }
            return
        }

        // Sombra / Borda de seleção
        if (isSelected) {
            guiGraphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFFFFD700.toInt())
        } else {
            guiGraphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF333338.toInt())
        }

        // Fundo do nó
        guiGraphics.fill(x, y, x + w, y + h, 0xFF1E1E24.toInt())

        // Cabeçalho
        val headerColor = getHeaderColor()
        guiGraphics.fill(x, y, x + w, y + headerHeight, headerColor)

        // Se o modal estiver aberto, bloqueia 100% a emissão de textos do nó no canvas
        if (!isModalOpen) {
            // Título e Ícone no cabeçalho
            val isBoundToScene = !node.parentSceneId.isNullOrEmpty()
            val maxTitleW = if (isBoundToScene) w - 24 else w - 8
            val titleText = font.plainSubstrByWidth(node.title, maxTitleW)
            guiGraphics.drawString(font, titleText, x + 6, y + 6, 0xFFFFFFFF.toInt(), true)

            if (isBoundToScene) {
                guiGraphics.drawString(font, "🎬", x + w - 16, y + 5, 0xFF00FFCC.toInt(), false)
            }

            // Resumo do Conteúdo/Texto do nó
            val contentY = y + headerHeight + 4
            val rawSummary = when (node.nodeType) {
                NodeType.BEGIN_SCENE -> "🟢 Início da Cena"
                NodeType.TRIGGER -> {
                    val condMode = if (node.params["triggerCondition"] == "IF_NOT") "IF NOT" else "IF"
                    val trigType = node.params["triggerType"] ?: "INÍCIO"
                    "$condMode: $trigType"
                }
                NodeType.DIALOGUE -> node.content.ifBlank { "Sem fala..." }
                NodeType.ACTION -> {
                    val actionType = node.params["actionSubtype"] ?: "MESSAGE"
                    when (actionType) {
                        "TELEPORT" -> "TP: ${node.params["destX"] ?: "0"}, ${node.params["destY"] ?: "64"}, ${node.params["destZ"] ?: "0"}"
                        "SPAWN" -> "Spawn: ${node.params["species"] ?: "Pikachu"} Lvl ${node.params["level"] ?: "5"}"
                        "SOUND" -> "Som: ${node.params["soundId"] ?: "click"}"
                        else -> "Msg: ${node.content}"
                    }
                }
                NodeType.TIMER -> "Timer: ${node.params["timerSeconds"] ?: "5"}s"
                NodeType.BRANCH -> "Ramificação (${node.outputs.size} saídas)"
                NodeType.CONSTRUCTION -> "Construção (${node.innerNodes.size} nós)"
                NodeType.END_SCENE -> "🛑 Finalizar Cena"
                NodeType.GATE -> "⚡ Portão Sincronizador (${node.inputs.size} in)"
                NodeType.LINK_SEND -> "📡 Transmitir: [${node.params["channelTag"] ?: "canal_1"}]"
                NodeType.LINK_RECEIVE -> "📡 Receber: [${node.params["channelTag"] ?: "canal_1"}]"
                NodeType.LOOP -> {
                    val mode = if (node.params["loopMode"] == "TIME") "Tempo" else "Contagem"
                    val detail = if (node.params["loopMode"] == "TIME") "${node.params["loopIntervalSec"] ?: "1.0"}s" else "${node.params["loopCount"] ?: "5"}x"
                    "🔄 Loop ($mode: $detail)"
                }
                NodeType.COMMENT -> "📝 ${node.content}"
            }

            val previewText = font.plainSubstrByWidth(rawSummary, w - 12)
            guiGraphics.drawString(font, previewText, x + 6, contentY, 0xFFA0A0A0.toInt(), false)

            if (node.params.containsKey("messageType")) {
                val subText = font.plainSubstrByWidth("Modo: ${node.params["messageType"]}", w - 12)
                guiGraphics.drawString(font, subText, x + 6, contentY + font.lineHeight + 2, 0xFF777788.toInt(), false)
            }
        }

        // Renderizar Portas de Entrada (Esquerda)
        node.inputs.forEachIndexed { idx, port ->
            val (px, py) = getInputPortWorldPos(idx)
            val ipx = px.toInt()
            val ipy = py.toInt()
            val r = portRadius.toInt()
            val color = if (hoveredPort?.id == port.id) 0xFF55FF55.toInt() else 0xFF4CAF50.toInt()

            guiGraphics.fill(ipx - r, ipy - r, ipx + r, ipy + r, color)
            guiGraphics.fill(ipx - r + 1, ipy - r + 1, ipx + r - 1, ipy + r - 1, 0xFF1E1E24.toInt())
            guiGraphics.fill(ipx - r + 2, ipy - r + 2, ipx + r - 2, ipy + r - 2, color)

            if (!isModalOpen) {
                val pName = font.plainSubstrByWidth(port.name, 45)
                guiGraphics.drawString(font, pName, ipx + r + 3, ipy - 4, 0xFFCCCCCC.toInt(), false)
            }
        }

        // Renderizar Portas de Saída (Direita)
        node.outputs.forEachIndexed { idx, port ->
            val (px, py) = getOutputPortWorldPos(idx)
            val opx = px.toInt()
            val opy = py.toInt()
            val r = portRadius.toInt()
            val color = if (hoveredPort?.id == port.id) 0xFFFFB74D.toInt() else 0xFFFF9800.toInt()

            guiGraphics.fill(opx - r, opy - r, opx + r, opy + r, color)
            guiGraphics.fill(opx - r + 1, opy - r + 1, opx + r - 1, opy + r - 1, 0xFF1E1E24.toInt())
            guiGraphics.fill(opx - r + 2, opy - r + 2, opx + r - 2, opy + r - 2, color)

            if (!isModalOpen) {
                val pName = font.plainSubstrByWidth(port.name, 45)
                val textW = font.width(pName)
                guiGraphics.drawString(font, pName, opx - r - 3 - textW, opy - 4, 0xFFCCCCCC.toInt(), false)
            }
        }
    }
}
