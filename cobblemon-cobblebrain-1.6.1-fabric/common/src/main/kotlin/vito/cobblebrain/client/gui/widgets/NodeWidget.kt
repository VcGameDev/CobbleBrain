package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.NodeType
import vito.cobblebrain.model.PortData
import vito.cobblebrain.model.PortType
import kotlin.math.sqrt

enum class NodeRectShape {
    HORIZONTAL_RECTANGLE, // Retângulo Horizontal (Padrão: 160x55)
    SQUARE,               // Quadrado (Padrão: 90x90)
    VERTICAL_RECTANGLE    // Retângulo Vertical (Padrão: 130x100)
}

class NodeWidget(val node: NodeData) {

    var isSelected: Boolean = false
    var isDragging: Boolean = false

    // Destaque do Mini-Modo de Teste por Intervalo
    var isStartTestNode: Boolean = false
    var isEndTestNode: Boolean = false

    private val headerHeight = 20
    private val portRadius = 5.0

    fun getRectShape(): NodeRectShape {
        return when (node.nodeType) {
            NodeType.BEGIN_SCENE, NodeType.END_SCENE, NodeType.GATE,
            NodeType.LINK_SEND, NodeType.LINK_RECEIVE,
            NodeType.VARIABLE_GET -> NodeRectShape.HORIZONTAL_RECTANGLE

            NodeType.COMMENT, NodeType.VARIABLE_SET,
            NodeType.LOOP -> NodeRectShape.SQUARE

            NodeType.CONSTRUCTION, NodeType.BRANCH, NodeType.TRIGGER,
            NodeType.DIALOGUE, NodeType.ACTION,
            NodeType.TIMER -> NodeRectShape.VERTICAL_RECTANGLE
        }
    }

    // Cores Harmonizadas por Categoria
    fun getHeaderColor(): Int {
        return when (node.nodeType) {
            // SEÇÃO 1: Estrutura & Fluxo (Tons de Verde & Teal)
            NodeType.BEGIN_SCENE -> 0xFF2E7D32.toInt()  // Verde Esmeralda
            NodeType.END_SCENE -> 0xFF1B5E20.toInt()    // Verde Escuro
            NodeType.GATE -> 0xFF00838F.toInt()         // Teal Sincronizador
            NodeType.CONSTRUCTION -> 0xFF00695C.toInt()  // Teal Escuro Sub-grafo
            NodeType.LINK_SEND -> 0xFF009688.toInt()    // Verde Água Transmissor
            NodeType.LINK_RECEIVE -> 0xFF00796B.toInt() // Verde Água Receptor
            NodeType.LOOP -> 0xFF388E3C.toInt()         // Verde Repetidor

            // SEÇÃO 2: Variáveis & Decisões (Tons de Azul & Ciano Vibrante)
            NodeType.VARIABLE_GET -> 0xFF0288D1.toInt() // Azul Ciano Getter
            NodeType.VARIABLE_SET -> 0xFF0277BD.toInt() // Azul Oceano Setter
            NodeType.BRANCH -> 0xFF1565C0.toInt()       // Azul Safira If/Else
            NodeType.TRIGGER -> 0xFF0D47A1.toInt()      // Azul Cobalto Gatilho

            // SEÇÃO 3: Ações & Eventos (Tons de Vermelho, Laranja, Roxo & Amarelo)
            NodeType.DIALOGUE -> 0xFFC62828.toInt()     // Vermelho Vivo Diálogo
            NodeType.ACTION -> 0xFFD32F2F.toInt()       // Vermelho Carmim Ação
            NodeType.TIMER -> 0xFF8E24AA.toInt()        // Roxo Vibrante Timer
            NodeType.COMMENT -> 0xFFFBC02D.toInt()     // Amarelo Nota
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
        isModalOpen: Boolean = false,
        isCollidingWithOverlay: Boolean = false
    ) {
        when (getRectShape()) {
            NodeRectShape.HORIZONTAL_RECTANGLE -> {
                node.width = 160.0
                node.height = 55.0
            }
            NodeRectShape.SQUARE -> {
                node.width = 90.0
                node.height = 90.0
            }
            NodeRectShape.VERTICAL_RECTANGLE -> {
                node.width = 130.0
                node.height = 100.0
            }
        }

        val x = node.x.toInt()
        val y = node.y.toInt()
        val w = node.width.toInt()
        val h = node.height.toInt()

        val shouldHideText = isModalOpen || isCollidingWithOverlay

        // Bloco de Comentário (Estilo Post-It)
        if (node.nodeType == NodeType.COMMENT) {
            val noteBorder = if (isSelected) 0xFFFFD700.toInt() else 0xFFFBC02D.toInt()
            guiGraphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, noteBorder)
            guiGraphics.fill(x, y, x + w, y + h, 0xEE222016.toInt())
            guiGraphics.fill(x, y, x + w, y + 16, 0xFF3D3820.toInt())

            if (!shouldHideText) {
                guiGraphics.drawString(font, "📝 ${node.title}", x + 6, y + 4, 0xFFFBC02D.toInt(), false)
                val commentText = font.plainSubstrByWidth(node.content.ifBlank { "Sua nota..." }, w - 12)
                guiGraphics.drawString(font, commentText, x + 6, y + 24, 0xFFE0E0D0.toInt(), false)
            }
            return
        }

        val borderColor = when {
            isStartTestNode -> 0xFF00FFCC.toInt()
            isEndTestNode -> 0xFFFF5555.toInt()
            isSelected -> 0xFFFFD700.toInt()
            else -> 0xFF333338.toInt()
        }

        val headerColor = getHeaderColor()

        guiGraphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, borderColor)
        guiGraphics.fill(x, y, x + w, y + h, 0xFF1E1E24.toInt())
        guiGraphics.fill(x, y, x + w, y + headerHeight, headerColor)

        // Ocultar o texto do nó APENAS se ele estiver em colisão AABB com um menu/inspector sobreposto
        if (!shouldHideText) {
            val isBoundToScene = !node.parentSceneId.isNullOrEmpty()
            val maxTitleW = if (isBoundToScene) w - 24 else w - 8
            val titleText = font.plainSubstrByWidth(node.title, maxTitleW)
            guiGraphics.drawString(font, titleText, x + 6, y + 6, 0xFFFFFFFF.toInt(), true)

            if (isBoundToScene) {
                guiGraphics.drawString(font, "🎬", x + w - 16, y + 5, 0xFF00FFCC.toInt(), false)
            }

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
                        "VAR_MODIFY" -> "Var: ${node.params["varKey"] ?: "var"} ${node.params["varOp"] ?: "="} ${node.params["varValue"] ?: "1"}"
                        "TELEPORT" -> "TP: ${node.params["destX"] ?: "0"}, ${node.params["destY"] ?: "64"}, ${node.params["destZ"] ?: "0"}"
                        "SPAWN" -> "Spawn: ${node.params["species"] ?: "Pikachu"} Lvl ${node.params["level"] ?: "5"}"
                        "SOUND" -> "Som: ${node.params["soundId"] ?: "click"}"
                        else -> "Msg: ${node.content}"
                    }
                }
                NodeType.TIMER -> "Timer: ${node.params["timerSeconds"] ?: "5"}s"
                NodeType.BRANCH -> "If: ${node.params["varKey"] ?: "var"} ${node.params["varOp"] ?: "=="} ${node.params["varValue"] ?: "true"}"
                NodeType.CONSTRUCTION -> "Construção (${node.innerNodes.size} nós)"
                NodeType.END_SCENE -> "🛑 Finalizar Cena"
                NodeType.GATE -> "⚡ Portão GATE (${node.inputs.size} in)"
                NodeType.LINK_SEND -> "📡 Link: [${node.params["channelTag"] ?: "canal_1"}]"
                NodeType.LINK_RECEIVE -> "📡 Rec: [${node.params["channelTag"] ?: "canal_1"}]"
                NodeType.LOOP -> {
                    val mode = if (node.params["loopMode"] == "TIME") "Tempo" else "Qtd"
                    val detail = if (node.params["loopMode"] == "TIME") "${node.params["loopIntervalSec"] ?: "1.0"}s" else "${node.params["loopCount"] ?: "5"}x"
                    "🔄 Loop ($mode: $detail)"
                }
                NodeType.COMMENT -> "📝 ${node.content}"
                NodeType.VARIABLE_GET -> "🔹 Var: [${node.params["varKey"] ?: "var_nova"}]"
                NodeType.VARIABLE_SET -> "✏️ Set: ${node.params["varKey"] ?: "var_nova"} ${node.params["varOp"] ?: "="} ${node.params["varValue"] ?: "1"}"
            }

            val previewText = font.plainSubstrByWidth(rawSummary, w - 12)
            guiGraphics.drawString(font, previewText, x + 6, contentY, 0xFFA0A0A0.toInt(), false)

            if (isStartTestNode) {
                guiGraphics.drawString(font, "[▶ INÍCIO]", x + 6, y + h - 12, 0xFF00FFCC.toInt(), false)
            } else if (isEndTestNode) {
                guiGraphics.drawString(font, "[🛑 FIM]", x + 6, y + h - 12, 0xFFFF5555.toInt(), false)
            }
        }

        // Renderizar Portas de Entrada
        node.inputs.forEachIndexed { idx, port ->
            val (px, py) = getInputPortWorldPos(idx)
            val ipx = px.toInt()
            val ipy = py.toInt()
            val r = portRadius.toInt()
            val color = if (hoveredPort?.id == port.id) 0xFF55FF55.toInt() else 0xFF4CAF50.toInt()

            guiGraphics.fill(ipx - r, ipy - r, ipx + r, ipy + r, color)
            guiGraphics.fill(ipx - r + 1, ipy - r + 1, ipx + r - 1, ipy + r - 1, 0xFF1E1E24.toInt())
            guiGraphics.fill(ipx - r + 2, ipy - r + 2, ipx + r - 2, ipy + r - 2, color)

            if (!shouldHideText) {
                val pName = font.plainSubstrByWidth(port.name, 40)
                guiGraphics.drawString(font, pName, ipx + r + 3, ipy - 4, 0xFFCCCCCC.toInt(), false)
            }
        }

        // Renderizar Portas de Saída
        node.outputs.forEachIndexed { idx, port ->
            val (px, py) = getOutputPortWorldPos(idx)
            val opx = px.toInt()
            val opy = py.toInt()
            val r = portRadius.toInt()
            val color = if (hoveredPort?.id == port.id) 0xFFFFB74D.toInt() else 0xFFFF9800.toInt()

            guiGraphics.fill(opx - r, opy - r, opx + r, opy + r, color)
            guiGraphics.fill(opx - r + 1, opy - r + 1, opx + r - 1, opy + r - 1, 0xFF1E1E24.toInt())
            guiGraphics.fill(opx - r + 2, opy - r + 2, opx + r - 2, opy + r - 2, color)

            if (!shouldHideText) {
                val pName = font.plainSubstrByWidth(port.name, 40)
                val textW = font.width(pName)
                guiGraphics.drawString(font, pName, opx - r - 3 - textW, opy - 4, 0xFFCCCCCC.toInt(), false)
            }
        }
    }
}
