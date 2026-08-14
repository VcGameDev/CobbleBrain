package vito.cobblebrain.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import vito.cobblebrain.client.gui.widgets.*
import vito.cobblebrain.config.StoryEditorConfig
import vito.cobblebrain.config.StoryEditorConfigData
import vito.cobblebrain.engine.ActiveStoryInstance
import vito.cobblebrain.engine.StoryContext
import vito.cobblebrain.engine.StoryExecutor
import vito.cobblebrain.model.*
import java.io.File
import kotlin.math.abs

class StoryEditorScreen(
    private val parentScreen: Screen? = null,
    initialProject: StoryProject? = null
) : Screen(Component.literal("CobbleBrain - Story Editor")) {

    var configData: StoryEditorConfigData = StoryEditorConfig.load()
    var autoSaveEnabled: Boolean = configData.autoSaveEnabled
    var autoSaveIntervalSeconds: Int = configData.autoSaveIntervalSeconds
    var autoOpenLastProject: Boolean = configData.autoOpenLastProject
    private var lastAutoSaveTime: Long = System.currentTimeMillis()

    var project: StoryProject = initialProject ?: loadAutoOrNewProject()
    private val nodeWidgets = mutableListOf<NodeWidget>()

    // Controle de Alterações Pendentes (Dirty State)
    var isDirty: Boolean = false
    private var showExitConfirmModal: Boolean = false

    // Estado de Navegação em Construção (Sub-grafo)
    var editingConstructionNode: NodeData? = null

    // Lógica de Abas Visuais Persistentes (Cenas e Construções)
    private val openSceneIds = mutableSetOf<String>()
    private val openConstructionNodes = mutableListOf<NodeData>()
    private var tabBarScrollOffset: Int = 0

    // Transformações Pan & Zoom
    var panX: Double = 50.0
    var panY: Double = 50.0
    var zoom: Double = 1.0

    // Estados de Arraste, Redimensionamento e Interatividade
    private var isPanning: Boolean = false
    private var draggedScene: SceneData? = null
    private var resizingScene: SceneData? = null
    private var lastMouseX: Double = 0.0
    private var lastMouseY: Double = 0.0

    private var draggedWidget: NodeWidget? = null
    private var selectedWidget: NodeWidget? = null

    // Conexão em andamento
    private var connectingSourceNode: NodeData? = null
    private var connectingSourcePort: PortData? = null
    private var connectingSourceType: PortType? = null
    private var connectingSourceScene: SceneData? = null
    private var currentMouseWorldX: Double = 0.0
    private var currentMouseWorldY: Double = 0.0

    // Duplo clique na porta
    private var lastClickedPortId: String? = null
    private var lastClickedPortTime: Long = 0L

    private var hoveredPort: PortData? = null
    private var statusMessage: String = "Editor de Histórias CobbleBrain carregado."
    private var statusTimer: Int = 0

    // Inspectors Laterais
    var activeInspector: NodeInspectorWidget? = null
    var activeSceneInspector: SceneInspectorWidget? = null

    // Modais Dedicados Exclusivos
    var activeMetadataModal: StoryMetadataModalWidget? = null
    var activeDocModal: StoryDocumentationModalWidget? = null
    var activeVariableModal: StoryVariableManagerModalWidget? = null
    var activeVarSelectorModal: StoryVariableSelectorModalWidget? = null
    var activeActionTriggerPickerModal: ActionTriggerPickerModalWidget? = null
    var activePokemonConfigModal: PokemonConfigModalWidget? = null

    // Menu de Contexto (Clique Direito)
    var activeContextMenu: ContextMenuWidget? = null

    // Sistema de Posicionamento Interativo ("Ghost Placement")
    var activePlacementNode: NodeData? = null

    // Mini-Modo de Teste de Intervalo (Seleção de Blocos Início e Fim)
    var isRangeTestSelectionMode: Boolean = false
    var rangeTestStartNode: NodeData? = null
    var rangeTestEndNode: NodeData? = null

    // Paleta de Blocos Dropdown Dividida em 3 SEÇÕES DISTINTAS
    data class PaletteEntry(val isHeader: Boolean, val label: String, val type: NodeType = NodeType.DIALOGUE)
    private val paletteEntries = listOf(
        // SEÇÃO 1: Estrutura & Fluxo
        PaletteEntry(true, "--- 1. ESTRUTURA & FLUXO ---"),
        PaletteEntry(false, "🟢 Início da Cena", NodeType.BEGIN_SCENE),
        PaletteEntry(false, "🛑 Finalizar Cena", NodeType.END_SCENE),
        PaletteEntry(false, "⚡ Portão (GATE)", NodeType.GATE),
        PaletteEntry(false, "🏗️ Construção", NodeType.CONSTRUCTION),
        PaletteEntry(false, "📡 Transmissor Link", NodeType.LINK_SEND),
        PaletteEntry(false, "📡 Receptor Link", NodeType.LINK_RECEIVE),
        PaletteEntry(false, "🔄 Repetidor (Loop)", NodeType.LOOP),

        // SEÇÃO 2: Variáveis & Decisões
        PaletteEntry(true, "--- 2. VARIÁVEIS & DECISÕES ---"),
        PaletteEntry(false, "🔹 Bloco Variável (Get)", NodeType.VARIABLE_GET),
        PaletteEntry(false, "✏️ Bloco Modificador (Set)", NodeType.VARIABLE_SET),
        PaletteEntry(false, "🔀 Ramificação (If/Else)", NodeType.BRANCH),
        PaletteEntry(false, "🟢 Trigger (Gatilho)", NodeType.TRIGGER),

        // SEÇÃO 3: Ações & Eventos
        PaletteEntry(true, "--- 3. AÇÕES & EVENTOS ---"),
        PaletteEntry(false, "💬 Diálogo", NodeType.DIALOGUE),
        PaletteEntry(false, "⚡ Ação", NodeType.ACTION),
        PaletteEntry(false, "⏱ Timer", NodeType.TIMER),
        PaletteEntry(false, "📝 Comentário", NodeType.COMMENT)
    )
    private var isBlockPaletteOpen: Boolean = false
    private var paletteScrollOffset: Int = 0

    // Menus Dropdown Categorizados da Barra Superior
    private var isFileMenuOpen: Boolean = false
    private var isAddMenuOpen: Boolean = false
    private var isSystemMenuOpen: Boolean = false

    private var fileMenuX: Int = 0
    private var addMenuX: Int = 0
    private var systemMenuX: Int = 0

    // Menu Dropdown de Teste de História (Canto Inferior Direito)
    private var isTestMenuOpen: Boolean = false
    private var testBtnX: Int = 0
    private var testBtnY: Int = 0

    private val toolbarHeight = 36
    private val sceneBarHeight = 16

    init {
        project.scenes.forEach { openSceneIds.add(it.id) }
        rebuildNodeWidgets()
    }

    private fun loadAutoOrNewProject(): StoryProject {
        val cfg = StoryEditorConfig.load()
        if (cfg.autoOpenLastProject && cfg.lastProjectPath.isNotBlank()) {
            val file = File(cfg.lastProjectPath)
            if (file.exists()) {
                val loaded = StorySerializer.load(file)
                if (loaded != null) return loaded
            }
        }
        return StoryProject()
    }

    fun markDirty() {
        isDirty = true
    }

    fun getBtnWidth(label: String): Int = font.width(label) + 16

    fun closeAllTopMenus() {
        isFileMenuOpen = false
        isAddMenuOpen = false
        isSystemMenuOpen = false
        isBlockPaletteOpen = false
    }

    fun isNodeInsideScene(node: NodeData, scene: SceneData): Boolean {
        val centerX = node.x + node.width / 2.0
        val centerY = node.y + node.height / 2.0
        return centerX >= scene.x && centerX <= scene.x + scene.width &&
               centerY >= scene.y && centerY <= scene.y + scene.height
    }

    private fun updateVariableSceneBinding(node: NodeData, targetSceneId: String?) {
        val varKey = node.params["varKey"] ?: return
        val variable = project.variables.find { it.id == varKey } ?: return
        if (variable.scope == VariableScope.SCENE_LOCAL && targetSceneId != null) {
            variable.sceneId = targetSceneId
            markDirty()
        }
    }

    fun getActiveNodes(): MutableList<NodeData> {
        val construction = editingConstructionNode
        if (construction != null) return construction.innerNodes
        return project.getActiveScene()?.nodes ?: mutableListOf()
    }

    fun getActiveConnections(): MutableList<ConnectionData> {
        val construction = editingConstructionNode
        if (construction != null) return construction.innerConnections
        return project.getActiveScene()?.connections ?: mutableListOf()
    }

    private fun rebuildNodeWidgets() {
        nodeWidgets.clear()
        if (editingConstructionNode != null) {
            editingConstructionNode?.innerNodes?.forEach { node ->
                val widget = NodeWidget(node)
                widget.isStartTestNode = (rangeTestStartNode?.id == node.id)
                widget.isEndTestNode = (rangeTestEndNode?.id == node.id)
                nodeWidgets.add(widget)
            }
        } else {
            project.scenes.forEach { scene ->
                scene.nodes.forEach { node ->
                    val widget = NodeWidget(node)
                    widget.isStartTestNode = (rangeTestStartNode?.id == node.id)
                    widget.isEndTestNode = (rangeTestEndNode?.id == node.id)
                    nodeWidgets.add(widget)
                }
            }
        }
    }

    fun centerCameraOnScene(scene: SceneData) {
        project.activeSceneId = scene.id
        val targetX = scene.x + scene.width / 2.0
        val targetY = scene.y + scene.height / 2.0
        panX = (width / 2.0) - targetX * zoom
        panY = (height / 2.0) - targetY * zoom
        rebuildNodeWidgets()
        openSceneInspector(scene)
        showStatus("Foco centralizado na ${scene.title}")
    }

    fun screenToWorldX(screenX: Double): Double = (screenX - panX) / zoom
    fun screenToWorldY(screenY: Double): Double = (screenY - panY) / zoom
    fun worldToScreenX(worldX: Double): Double = worldX * zoom + panX
    fun worldToScreenY(worldY: Double): Double = worldY * zoom + panY

    override fun init() {
        super.init()
        clearWidgets()

        project.scenes.forEach { openSceneIds.add(it.id) }

        val spacing = 4
        val btnH = 14
        val row2Y = 18
        var currentX = 10

        // 1. Menu [📁 Arquivo ▾]
        val fileLabel = "📁 Arquivo ▾"
        val fileW = getBtnWidth(fileLabel)
        fileMenuX = currentX
        addRenderableWidget(
            Button.builder(Component.literal(fileLabel)) {
                val next = !isFileMenuOpen
                closeAllTopMenus()
                isFileMenuOpen = next
                isTestMenuOpen = false
            }.bounds(currentX, row2Y, fileW, btnH).build()
        )
        currentX += fileW + spacing

        // 2. Menu [➕ Adicionar ▾]
        val addLabel = "➕ Adicionar ▾"
        val addW = getBtnWidth(addLabel)
        addMenuX = currentX
        addRenderableWidget(
            Button.builder(Component.literal(addLabel)) {
                val next = !isAddMenuOpen
                closeAllTopMenus()
                isAddMenuOpen = next
                isTestMenuOpen = false
            }.bounds(currentX, row2Y, addW, btnH).build()
        )
        currentX += addW + spacing

        // 3. Menu [⚙ Sistema ▾]
        val sysLabel = "⚙ Sistema ▾"
        val sysW = getBtnWidth(sysLabel)
        systemMenuX = currentX
        addRenderableWidget(
            Button.builder(Component.literal(sysLabel)) {
                val next = !isSystemMenuOpen
                closeAllTopMenus()
                isSystemMenuOpen = next
                isTestMenuOpen = false
            }.bounds(currentX, row2Y, sysW, btnH).build()
        )
        currentX += sysW + spacing

        if (editingConstructionNode != null) {
            val backLabel = "← Voltar ao Estúdio"
            val backW = getBtnWidth(backLabel)
            addRenderableWidget(
                Button.builder(Component.literal(backLabel)) {
                    editingConstructionNode = null
                    init()
                    rebuildNodeWidgets()
                    activeInspector = null
                    activeSceneInspector = null
                    showStatus("Retornado ao Estúdio principal.")
                }.bounds(currentX, row2Y, backW, btnH).build()
            )
        }

        // Botão [▶ Testar ▾] (Canto Inferior Direito)
        val testLabel = "▶ Testar ▾"
        val testW = getBtnWidth(testLabel)
        val testH = 16
        val testX = width - testW - 10
        val testY = height - testH - 10
        testBtnX = testX
        testBtnY = testY
        addRenderableWidget(
            Button.builder(Component.literal(testLabel)) {
                val next = !isTestMenuOpen
                closeAllTopMenus()
                isTestMenuOpen = next
            }.bounds(testX, testY, testW, testH).build()
        )
    }

    private fun checkDirtyBeforeAction(onProceed: () -> Unit) {
        if (isDirty) {
            showExitConfirmModal = true
        } else {
            onProceed()
        }
    }

    private fun openMetadataModal() {
        closeAllTopMenus()
        activeMetadataModal = StoryMetadataModalWidget(
            project = project,
            font = font,
            screenWidth = width,
            screenHeight = height,
            onClose = { activeMetadataModal = null },
            onDataChanged = { markDirty() }
        )
    }

    private fun addNode(type: NodeType) {
        val title = when (type) {
            NodeType.BEGIN_SCENE -> "Início da Cena"
            NodeType.TRIGGER -> "Trigger"
            NodeType.ACTION -> "Ação"
            NodeType.TIMER -> "Timer"
            NodeType.BRANCH -> "Ramificação"
            NodeType.DIALOGUE -> "Diálogo"
            NodeType.CONSTRUCTION -> "Construção"
            NodeType.LINK_SEND -> "Link Send"
            NodeType.LINK_RECEIVE -> "Link Receive"
            NodeType.LOOP -> "Loop Repetidor"
            NodeType.COMMENT -> "Nota"
            NodeType.END_SCENE -> "Finalizar Cena"
            NodeType.GATE -> "Portão (GATE)"
            NodeType.VARIABLE_GET -> "Var (Get)"
            NodeType.VARIABLE_SET -> "Var (Set)"
        }

        val inputs = mutableListOf<PortData>()
        val outputs = mutableListOf<PortData>()

        var w = 160.0
        var h = 55.0

        when (type) {
            NodeType.VARIABLE_GET -> {
                outputs.add(PortData(name = "Val", type = PortType.OUTPUT))
                w = 160.0; h = 55.0
            }
            NodeType.VARIABLE_SET -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 90.0; h = 90.0
            }
            NodeType.COMMENT -> {
                w = 90.0; h = 90.0
            }
            NodeType.BEGIN_SCENE -> {
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 160.0; h = 55.0
            }
            NodeType.TRIGGER -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 130.0; h = 100.0
            }
            NodeType.BRANCH -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "IF (Saída 1)", type = PortType.OUTPUT))
                outputs.add(PortData(name = "IF (Saída 2)", type = PortType.OUTPUT))
                w = 130.0; h = 100.0
            }
            NodeType.END_SCENE -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                w = 160.0; h = 55.0
            }
            NodeType.GATE -> {
                inputs.add(PortData(name = "IN 1", type = PortType.INPUT))
                inputs.add(PortData(name = "IN 2", type = PortType.INPUT))
                outputs.add(PortData(name = "OUT", type = PortType.OUTPUT))
                w = 160.0; h = 55.0
            }
            NodeType.LINK_SEND -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                w = 160.0; h = 55.0
            }
            NodeType.LINK_RECEIVE -> {
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 160.0; h = 55.0
            }
            NodeType.LOOP -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                inputs.add(PortData(name = "Stop", type = PortType.INPUT))
                outputs.add(PortData(name = "Cycle", type = PortType.OUTPUT))
                outputs.add(PortData(name = "Done", type = PortType.OUTPUT))
                w = 90.0; h = 90.0
            }
            else -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 130.0; h = 100.0
            }
        }

        val ghostNode = NodeData(
            parentSceneId = null,
            title = title,
            nodeType = type,
            content = if (type == NodeType.DIALOGUE) "Olá Treinador!" else "",
            x = currentMouseWorldX - w / 2.0,
            y = currentMouseWorldY - h / 2.0,
            width = w,
            height = h,
            inputs = inputs,
            outputs = outputs,
            params = mutableMapOf()
        )

        if (type == NodeType.VARIABLE_GET || type == NodeType.VARIABLE_SET) {
            val firstVarKey = project.variables.firstOrNull()?.id ?: "var_nova"
            ghostNode.params["varKey"] = firstVarKey
            ghostNode.params["varOp"] = "="
            ghostNode.params["varValue"] = "1"
            if (type == NodeType.VARIABLE_GET) ghostNode.title = "Get: $firstVarKey"
            if (type == NodeType.VARIABLE_SET) ghostNode.title = "Set: $firstVarKey"
        } else if (type == NodeType.TRIGGER) {
            ghostNode.params["requireInputSignal"] = "true"
        } else if (type == NodeType.LINK_SEND || type == NodeType.LINK_RECEIVE) {
            ghostNode.params["channelTag"] = "canal_1"
        } else if (type == NodeType.LOOP) {
            ghostNode.params["loopMode"] = "COUNT"
            ghostNode.params["loopCount"] = "5"
            ghostNode.params["loopIntervalSec"] = "1.0"
        }

        activePlacementNode = ghostNode
        isBlockPaletteOpen = false
        closeAllTopMenus()
        isTestMenuOpen = false
        markDirty()
        showStatus("Modo Fantasma: Clique para posicionar o bloco $title (ESC para cancelar).")
    }

    private fun createNewSceneFrame() {
        val lastScene = project.scenes.lastOrNull()
        val newX = if (lastScene != null) lastScene.x + lastScene.width + 100.0 else screenToWorldX(width / 2.0) - 250.0
        val newY = lastScene?.y ?: (screenToWorldY(height / 2.0) - 175.0)

        val newScene = SceneData(
            title = "Cena ${project.scenes.size + 1}",
            description = "Descrição da Cena",
            x = newX,
            y = newY,
            width = 500.0,
            height = 350.0
        )
        project.scenes.add(newScene)
        openSceneIds.add(newScene.id)
        project.activeSceneId = newScene.id
        markDirty()
        rebuildNodeWidgets()
        openSceneInspector(newScene)
        showStatus("Nova Cena criada no Estúdio: ${newScene.title}")
        isBlockPaletteOpen = false
        closeAllTopMenus()
        isTestMenuOpen = false
    }

    private fun openInspectorForNode(node: NodeData) {
        val inspectorW = 140
        val inspectorX = width - inspectorW
        val inspectorY = toolbarHeight + sceneBarHeight
        val inspectorH = height - (toolbarHeight + sceneBarHeight)

        activeSceneInspector = null
        activeInspector = NodeInspectorWidget(
            node = node,
            panelX = inspectorX,
            panelY = inspectorY,
            panelWidth = inspectorW,
            panelHeight = inspectorH,
            font = font,
            onClose = { activeInspector = null },
            onDataChanged = {
                markDirty()
                val conns = getActiveConnections()
                nodeWidgets.forEach { w ->
                    if (w.node.inputs.isEmpty()) {
                        conns.removeAll { it.toNodeId == w.node.id }
                    }
                }
                rebuildNodeWidgets()
            },
            onOpenConstruction = { constrNode ->
                if (!openConstructionNodes.contains(constrNode)) {
                    openConstructionNodes.add(constrNode)
                }
                editingConstructionNode = constrNode
                init()
                rebuildNodeWidgets()
                activeInspector = null
                activeSceneInspector = null
                showStatus("Sub-canvas da Construção aberto: ${constrNode.title}")
            },
            onOpenVariableSelector = { onSelect ->
                activeVarSelectorModal = StoryVariableSelectorModalWidget(
                    project = project,
                    font = font,
                    screenWidth = width,
                    screenHeight = height,
                    onSelect = { selectedVar ->
                        onSelect(selectedVar)
                        activeVarSelectorModal = null
                    },
                    onClose = { activeVarSelectorModal = null }
                )
            },
            onOpenActionTriggerPicker = { isAction, onSelect ->
                val curId = if (isAction) (node.params["actionSubtype"] ?: "MESSAGE") else (node.params["triggerType"] ?: "START")
                activeActionTriggerPickerModal = ActionTriggerPickerModalWidget(
                    isAction = isAction,
                    font = font,
                    screenWidth = width,
                    screenHeight = height,
                    currentSelectedId = curId,
                    onSelect = { chosenId ->
                        onSelect(chosenId)
                    },
                    onClose = { activeActionTriggerPickerModal = null }
                )
            },
            onOpenPokemonConfig = { targetNode ->
                activePokemonConfigModal = PokemonConfigModalWidget(
                    node = targetNode,
                    font = font,
                    screenWidth = width,
                    screenHeight = height,
                    onClose = { activePokemonConfigModal = null },
                    onDataChanged = {
                        markDirty()
                        activeInspector?.buildUi()
                    }
                )
            },
            projectVariables = project.variables
        )
    }

    private fun openSceneInspector(scene: SceneData) {
        val inspectorW = 140
        val inspectorX = width - inspectorW
        val inspectorY = toolbarHeight + sceneBarHeight
        val inspectorH = height - (toolbarHeight + sceneBarHeight)

        activeInspector = null
        activeSceneInspector = SceneInspectorWidget(
            scene = scene,
            panelX = inspectorX,
            panelY = inspectorY,
            panelWidth = inspectorW,
            panelHeight = inspectorH,
            font = font,
            onClose = { activeSceneInspector = null },
            onDataChanged = {
                markDirty()
                rebuildNodeWidgets()
            }
        )
    }

    private fun saveProject() {
        val file = StorySerializer.save(project)
        if (file != null) {
            isDirty = false
            configData.lastProjectPath = file.absolutePath
            StoryEditorConfig.save(configData)
            showStatus("Salvo em: ${file.name}")
        } else {
            showStatus("Erro ao salvar projeto!")
        }
    }

    private fun exportJson() {
        val json = StorySerializer.toJson(project)
        try {
            minecraft?.keyboardHandler?.clipboard = json
            showStatus("JSON copiado para a área de transferência!")
        } catch (e: Exception) {
            showStatus("JSON gerado com sucesso (${json.length} chars).")
        }
    }

    private fun showStatus(msg: String) {
        statusMessage = msg
        statusTimer = 100
    }

    private fun startRangeTestExecution() {
        val startNode = rangeTestStartNode
        if (startNode == null) return

        saveProject()

        val serverPlayer = minecraft?.player?.uuid?.let { minecraft?.singleplayerServer?.playerList?.getPlayer(it) }
        val instance = ActiveStoryInstance(storyId = project.id, project = project, context = StoryContext(player = serverPlayer))
        StoryExecutor.activeStories[project.id] = instance

        StoryExecutor.executeNodeChain(instance, startNode)

        isRangeTestSelectionMode = false
        rangeTestStartNode = null
        rangeTestEndNode = null
        rebuildNodeWidgets()

        minecraft?.setScreen(null)
    }

    private data class ScreenRect(val x: Double, val y: Double, val w: Double, val h: Double)

    // Teste AABB para ocultar o texto APENAS nos nós que colidem com menus ou inspetores sobrepostos
    private fun isNodeCollidingWithOverlays(widget: NodeWidget): Boolean {
        val nodeX = worldToScreenX(widget.node.x)
        val nodeY = worldToScreenY(widget.node.y)
        val nodeW = widget.node.width * zoom
        val nodeH = widget.node.height * zoom

        val overlayRects = mutableListOf<ScreenRect>()

        if (activeInspector != null || activeSceneInspector != null) {
            val inspW = 140.0
            val topOffset = (toolbarHeight + sceneBarHeight).toDouble()
            overlayRects.add(ScreenRect(width - inspW, topOffset, inspW, height - topOffset))
        }

        val dropY = (toolbarHeight + sceneBarHeight + 2).toDouble()
        if (isFileMenuOpen) overlayRects.add(ScreenRect(fileMenuX.toDouble(), dropY, 140.0, 76.0))
        if (isAddMenuOpen) overlayRects.add(ScreenRect(addMenuX.toDouble(), dropY, 160.0, 94.0))
        if (isSystemMenuOpen) overlayRects.add(ScreenRect(systemMenuX.toDouble(), dropY, 155.0, 40.0))
        if (isBlockPaletteOpen) overlayRects.add(ScreenRect(10.0, dropY, 185.0, 184.0))

        if (isTestMenuOpen) {
            val dropdownH = 44.0
            val testW = 195.0
            val testX = (testBtnX + getBtnWidth("▶ Testar ▾") - testW).coerceAtMost(width - testW - 5.0).coerceAtLeast(5.0)
            val testY = testBtnY - dropdownH - 4.0
            overlayRects.add(ScreenRect(testX, testY, testW, dropdownH))
        }

        for (rect in overlayRects) {
            if (nodeX < rect.x + rect.w && nodeX + nodeW > rect.x &&
                nodeY < rect.y + rect.h && nodeY + nodeH > rect.y) {
                return true
            }
        }
        return false
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        currentMouseWorldX = screenToWorldX(mouseX.toDouble())
        currentMouseWorldY = screenToWorldY(mouseY.toDouble())

        val isModalOpen = activeDocModal != null || showExitConfirmModal || activeVariableModal != null || activeMetadataModal != null || activeVarSelectorModal != null || activeActionTriggerPickerModal != null || activePokemonConfigModal != null

        // 1. Fundo do Canvas e Grade
        guiGraphics.fill(0, 0, width, height, 0xFF141418.toInt())
        renderGrid(guiGraphics)

        // 2. SCISSOR CLIPPING + CANVAS RENDER
        val topOffset = toolbarHeight + sceneBarHeight
        guiGraphics.enableScissor(0, topOffset, width, height)

        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(panX.toFloat(), panY.toFloat(), 0.0f)
        guiGraphics.pose().scale(zoom.toFloat(), zoom.toFloat(), 1.0f)

        renderSceneContainers(guiGraphics, isModalOpen)
        renderConnections(guiGraphics)
        renderActiveConnectionPreview(guiGraphics)

        if (!isModalOpen) {
            updateHoveredPort()
        } else {
            hoveredPort = null
        }

        // Renderizar nós do canvas (com checagem seletiva de colisão AABB para cada nó)
        nodeWidgets.toList().forEach { widget ->
            val isColliding = isNodeCollidingWithOverlays(widget)
            widget.render(guiGraphics, font, if (isModalOpen || isColliding) null else hoveredPort, isModalOpen, isColliding)
        }
        renderGhostPlacementNode(guiGraphics)

        guiGraphics.pose().popPose()
        guiGraphics.disableScissor()

        guiGraphics.flush()

        // 3. Notificações Toast
        if (statusTimer > 0) {
            statusTimer--
            val toastY = height - 16
            guiGraphics.fill(6, toastY - 3, font.width(statusMessage) + 16, toastY + 11, 0xDD181820.toInt())
            guiGraphics.fill(6, toastY - 3, 8, toastY + 11, 0xFFFFD700.toInt())
            guiGraphics.drawString(font, statusMessage, 12, toastY, 0xFFFFD700.toInt(), false)
        }

        // 4. Componentes nativos do Screen
        val mX = if (isModalOpen) -9999 else mouseX
        val mY = if (isModalOpen) -9999 else mouseY
        super.render(guiGraphics, mX, mY, partialTick)
        guiGraphics.flush()

        // 5. Barra Superior (TopBar) e Abas
        renderTopBarAndMenus(guiGraphics, mX, mY)
        guiGraphics.flush()

        // 6. RENDERIZAÇÃO DE MENUS CATEGORIZADOS, PALETA DE BLOCOS E INSPECTORS COM ELEVAÇÃO MATRICIAL Z = 500f
        RenderSystem.enableDepthTest()
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(0.0f, 0.0f, 500.0f)

        activeInspector?.render(guiGraphics, mX, mY, partialTick)
        activeSceneInspector?.render(guiGraphics, mX, mY, partialTick)

        renderCategoryMenusDropdowns(guiGraphics, mX, mY)
        if (isBlockPaletteOpen) renderBlockPalette(guiGraphics, mX, mY)
        if (isTestMenuOpen) renderTestMenu(guiGraphics, mX, mY)
        activeContextMenu?.render(guiGraphics, mX, mY, width, height)

        guiGraphics.flush()
        guiGraphics.pose().popPose()

        // 7. RENDERIZAÇÃO DOS MODAIS COM DEPTH TEST & ELEVAÇÃO MATRICIAL Z = 1000f
        if (isModalOpen) {
            renderModalOverlay(guiGraphics, mouseX, mouseY, partialTick)
            guiGraphics.flush()
        }

        // Auto-Save Assíncrono em Segundo Plano
        if (autoSaveEnabled) {
            val now = System.currentTimeMillis()
            if (now - lastAutoSaveTime >= autoSaveIntervalSeconds * 1000L) {
                lastAutoSaveTime = now
                val projectSnapshot = project
                java.util.concurrent.CompletableFuture.runAsync {
                    val file = StorySerializer.save(projectSnapshot)
                    if (file != null) {
                        configData.lastProjectPath = file.absolutePath
                        StoryEditorConfig.save(configData)
                    }
                }.thenAccept {
                    isDirty = false
                    showStatus("Auto-save salvo em segundo plano.")
                }
            }
        }
    }

    private fun renderTopBarAndMenus(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        guiGraphics.fill(0, 0, width, toolbarHeight, 0xFF18181C.toInt())
        guiGraphics.fill(0, toolbarHeight - 1, width, toolbarHeight, 0xFF2F2F38.toInt())

        val construction = editingConstructionNode
        val dirtyIndicator = if (isDirty) " *" else ""
        val titleText = if (construction != null) {
            "${project.name}$dirtyIndicator · Estúdio > 🏗️ ${construction.title} (${nodeWidgets.size} nós)"
        } else {
            "${project.name}$dirtyIndicator · Estúdio (${project.scenes.size} Cenas)"
        }
        val titleW = font.width(titleText)
        val centerX = (width - titleW) / 2
        guiGraphics.drawString(font, titleText, centerX, 4, 0xFF00FFCC.toInt(), false)

        if (isRangeTestSelectionMode) {
            val modeMsg = if (rangeTestStartNode == null) {
                "🎯 MODO DE SELEÇÃO: Clique no 1º Bloco (INÍCIO DO TESTE) - Arraste/Pan livre"
            } else {
                "🎯 MODO DE SELEÇÃO: Clique no 2º Bloco (FIM DO TESTE) - Arraste/Pan livre"
            }
            guiGraphics.fill(0, toolbarHeight, width, toolbarHeight + 14, 0xEE00838F.toInt())
            guiGraphics.drawCenteredString(font, modeMsg, width / 2, toolbarHeight + 3, 0xFFFFD700.toInt())
        } else {
            renderSceneTabBar(guiGraphics, mouseX, mouseY)
        }
    }

    private fun renderModalOverlay(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        guiGraphics.flush()

        RenderSystem.enableDepthTest()
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(0.0f, 0.0f, 1000.0f)

        guiGraphics.fill(0, 0, width, height, 0xF0101014.toInt())

        activeDocModal?.render(guiGraphics, mouseX, mouseY, partialTick)
        if (showExitConfirmModal) renderExitConfirmModal(guiGraphics, mouseX, mouseY)
        activeVariableModal?.render(guiGraphics, mouseX, mouseY, partialTick)
        activeMetadataModal?.render(guiGraphics, mouseX, mouseY, partialTick)
        activeVarSelectorModal?.render(guiGraphics, mouseX, mouseY, partialTick)
        activeActionTriggerPickerModal?.render(guiGraphics, mouseX, mouseY, partialTick)
        activePokemonConfigModal?.render(guiGraphics, mouseX, mouseY, partialTick)

        guiGraphics.flush()
        guiGraphics.pose().popPose()
    }

    private fun renderCategoryMenusDropdowns(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val dropY = toolbarHeight + sceneBarHeight + 2
        val itemH = 18

        if (isFileMenuOpen) {
            val dropW = 140
            val dropX = fileMenuX
            guiGraphics.fill(dropX, dropY, dropX + dropW, dropY + itemH * 4 + 4, 0xF018181C.toInt())
            guiGraphics.fill(dropX, dropY, dropX + 1, dropY + itemH * 4 + 4, 0xFF3D5AFE.toInt())
            guiGraphics.fill(dropX + dropW - 1, dropY, dropX + dropW, dropY + itemH * 4 + 4, 0xFF3D5AFE.toInt())
            guiGraphics.fill(dropX, dropY + itemH * 4 + 3, dropX + dropW, dropY + itemH * 4 + 4, 0xFF3D5AFE.toInt())

            val items = listOf("💾 Salvar", "📂 Carregar", "📤 Exportar JSON", "📋 Metadados")
            items.forEachIndexed { idx, label ->
                val iy = dropY + 2 + idx * itemH
                val isHovered = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= iy && mouseY < iy + itemH
                guiGraphics.fill(dropX + 3, iy, dropX + dropW - 3, iy + itemH - 2, if (isHovered) 0xFF3D5AFE.toInt() else 0xFF222228.toInt())
                guiGraphics.drawString(font, label, dropX + 8, iy + 4, 0xFFFFFFFF.toInt(), false)
            }
        }

        if (isAddMenuOpen) {
            val dropW = 160
            val dropX = addMenuX
            val items = listOf("🎬 Nova Cena", "📋 Gerenciar Catálogo", "🔹 Bloco Variável (Get)", "✏️ Bloco Modificador (Set)", "+ Blocos")
            val dropH = itemH * items.size + 4

            guiGraphics.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0xF018181C.toInt())
            guiGraphics.fill(dropX, dropY, dropX + 1, dropY + dropH, 0xFF3D5AFE.toInt())
            guiGraphics.fill(dropX + dropW - 1, dropY, dropX + dropW, dropY + dropH, 0xFF3D5AFE.toInt())
            guiGraphics.fill(dropX, dropY + dropH - 1, dropX + dropW, dropY + dropH, 0xFF3D5AFE.toInt())

            items.forEachIndexed { idx, label ->
                val iy = dropY + 2 + idx * itemH
                val isHovered = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= iy && mouseY < iy + itemH
                guiGraphics.fill(dropX + 3, iy, dropX + dropW - 3, iy + itemH - 2, if (isHovered) 0xFF3D5AFE.toInt() else 0xFF222228.toInt())
                guiGraphics.drawString(font, label, dropX + 8, iy + 4, 0xFFFFFFFF.toInt(), false)
            }
        }

        if (isSystemMenuOpen) {
            val dropW = 155
            val dropX = systemMenuX
            guiGraphics.fill(dropX, dropY, dropX + dropW, dropY + itemH * 2 + 4, 0xF018181C.toInt())
            guiGraphics.fill(dropX, dropY, dropX + 1, dropY + itemH * 2 + 4, 0xFF3D5AFE.toInt())
            guiGraphics.fill(dropX + dropW - 1, dropY, dropX + dropW, dropY + itemH * 2 + 4, 0xFF3D5AFE.toInt())
            guiGraphics.fill(dropX, dropY + itemH * 2 + 3, dropX + dropW, dropY + itemH * 2 + 4, 0xFF3D5AFE.toInt())

            val items = listOf("⚙ Configurações", "❓ Guia / Documentação")
            items.forEachIndexed { idx, label ->
                val iy = dropY + 2 + idx * itemH
                val isHovered = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= iy && mouseY < iy + itemH
                guiGraphics.fill(dropX + 3, iy, dropX + dropW - 3, iy + itemH - 2, if (isHovered) 0xFF3D5AFE.toInt() else 0xFF222228.toInt())
                guiGraphics.drawString(font, label, dropX + 8, iy + 4, 0xFFFFFFFF.toInt(), false)
            }
        }
    }

    private fun renderSceneTabBar(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val barY = toolbarHeight
        val barH = sceneBarHeight

        guiGraphics.fill(0, barY, width, barY + barH, 0xFF14141A.toInt())
        guiGraphics.fill(0, barY + barH - 1, width, barY + barH, 0xFF282834.toInt())

        val visibleAreaStart = 10
        val visibleAreaEnd = width - 10

        var currentX = 10 - tabBarScrollOffset

        openConstructionNodes.toList().forEach { constr ->
            val isSelected = editingConstructionNode?.id == constr.id
            val label = "🏗️ ${constr.title}"
            val labelW = font.width(label)
            val tabW = labelW + 22
            val tabX = currentX

            if (tabX + tabW >= visibleAreaStart && tabX <= visibleAreaEnd) {
                val isHovered = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= barY + 2 && mouseY <= barY + 2 + 14
                val isCloseHovered = mouseX >= tabX + tabW - 14 && mouseX <= tabX + tabW - 2 && mouseY >= barY + 2 && mouseY <= barY + 2 + 14

                val bg = when {
                    isSelected -> 0xFF00ACC1.toInt()
                    isHovered -> 0xFF00838F.toInt()
                    else -> 0xFF004D40.toInt()
                }

                guiGraphics.fill(tabX, barY + 2, tabX + tabW, barY + 16, bg)
                guiGraphics.drawString(font, label, tabX + 4, barY + 4, 0xFFFFFFFF.toInt(), false)

                val closeColor = if (isCloseHovered) 0xFFFF5555.toInt() else 0xFFA0A0A0.toInt()
                guiGraphics.drawString(font, "✕", tabX + tabW - 12, barY + 4, closeColor, false)
            }
            currentX += tabW + 4
        }

        val visibleScenes = project.scenes.filter { openSceneIds.contains(it.id) }
        visibleScenes.forEach { scene ->
            val isSelected = project.activeSceneId == scene.id && editingConstructionNode == null
            val label = "🎬 ${scene.title}"
            val labelW = font.width(label)
            val tabW = labelW + 22
            val tabX = currentX

            if (tabX + tabW >= visibleAreaStart && tabX <= visibleAreaEnd) {
                val isHovered = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= barY + 2 && mouseY <= barY + 2 + 14
                val isCloseHovered = mouseX >= tabX + tabW - 14 && mouseX <= tabX + tabW - 2 && mouseY >= barY + 2 && mouseY <= barY + 2 + 14

                val bg = when {
                    isSelected -> 0xFF3D5AFE.toInt()
                    isHovered -> 0xFF2A2A3A.toInt()
                    else -> 0xFF1A1A24.toInt()
                }

                guiGraphics.fill(tabX, barY + 2, tabX + tabW, barY + 16, bg)
                guiGraphics.drawString(font, label, tabX + 4, barY + 4, if (isSelected) 0xFF00FFCC.toInt() else 0xFFA0A0A0.toInt(), false)

                val closeColor = if (isCloseHovered) 0xFFFF5555.toInt() else 0xFFA0A0A0.toInt()
                guiGraphics.drawString(font, "✕", tabX + tabW - 12, barY + 4, closeColor, false)
            }
            currentX += tabW + 4
        }
    }

    private fun renderBlockPalette(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val palX = 10
        val palY = toolbarHeight + sceneBarHeight + 2
        val palW = 185
        val itemH = 18

        val maxVisibleItems = 10
        val visibleHeight = maxVisibleItems * itemH + 4

        guiGraphics.fill(palX, palY, palX + palW, palY + visibleHeight, 0xF018181C.toInt())
        guiGraphics.fill(palX, palY, palX + 1, palY + visibleHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(palX + palW - 1, palY, palX + palW, palY + visibleHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(palX, palY + visibleHeight - 1, palX + palW, palY + visibleHeight, 0xFF3D5AFE.toInt())

        val startIndex = paletteScrollOffset.coerceIn(0, maxOf(0, paletteEntries.size - maxVisibleItems))
        val endIndex = (startIndex + maxVisibleItems).coerceAtMost(paletteEntries.size)

        for (i in startIndex until endIndex) {
            val idx = i - startIndex
            val entry = paletteEntries[i]
            val iy = palY + 2 + idx * itemH

            if (entry.isHeader) {
                guiGraphics.fill(palX + 3, iy, palX + palW - 3, iy + itemH - 2, 0xFF2A2A38.toInt())
                guiGraphics.drawString(font, entry.label, palX + 6, iy + 5, 0xFFFFD700.toInt(), false)
            } else {
                val isHovered = mouseX >= palX && mouseX <= palX + palW && mouseY >= iy && mouseY < iy + itemH
                val bg = if (isHovered) 0xFF3D5AFE.toInt() else 0xFF222228.toInt()

                guiGraphics.fill(palX + 3, iy, palX + palW - 3, iy + itemH - 2, bg)
                guiGraphics.drawString(font, entry.label, palX + 10, iy + 4, 0xFFFFFFFF.toInt(), false)
            }
        }

        if (paletteEntries.size > maxVisibleItems) {
            val sbX = palX + palW - 5
            val scrollRatio = maxVisibleItems.toFloat() / paletteEntries.size
            val thumbH = (visibleHeight * scrollRatio).toInt().coerceAtLeast(10)
            val maxScrollable = paletteEntries.size - maxVisibleItems
            val thumbY = palY + (paletteScrollOffset.toFloat() / maxScrollable * (visibleHeight - thumbH)).toInt()

            guiGraphics.fill(sbX, palY + 2, sbX + 3, palY + visibleHeight - 2, 0xFF2A2A36.toInt())
            guiGraphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFF00FFCC.toInt())
        }
    }

    private fun renderTestMenu(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val testW = 195
        val itemH = 20
        val dropdownH = itemH * 2 + 4

        val testX = (testBtnX + getBtnWidth("▶ Testar ▾") - testW).coerceAtMost(width - testW - 5).coerceAtLeast(5)
        val testY = testBtnY - dropdownH - 4

        guiGraphics.fill(testX, testY, testX + testW, testY + dropdownH, 0xF018181C.toInt())
        guiGraphics.fill(testX, testY, testX + 1, testY + dropdownH, 0xFF4CAF50.toInt())
        guiGraphics.fill(testX + testW - 1, testY, testX + testW, testY + dropdownH, 0xFF4CAF50.toInt())
        guiGraphics.fill(testX, testY, testX + testW, testY + 1, 0xFF4CAF50.toInt())
        guiGraphics.fill(testX, testY + dropdownH - 1, testX + testW, testY + dropdownH, 0xFF4CAF50.toInt())

        val h1 = mouseX >= testX && mouseX <= testX + testW && mouseY >= testY + 2 && mouseY < testY + 2 + itemH
        guiGraphics.fill(testX + 3, testY + 2, testX + testW - 3, testY + itemH, if (h1) 0xFF4CAF50.toInt() else 0xFF222228.toInt())
        guiGraphics.drawString(font, "▶ Testar do Início", testX + 8, testY + 6, 0xFFFFFFFF.toInt(), false)

        val h2 = mouseX >= testX && mouseX <= testX + testW && mouseY >= testY + 2 + itemH && mouseY < testY + 2 + itemH * 2
        guiGraphics.fill(testX + 3, testY + 2 + itemH, testX + testW - 3, testY + itemH * 2, if (h2) 0xFF4CAF50.toInt() else 0xFF222228.toInt())
        guiGraphics.drawString(font, "🎯 Testar por Intervalo (Blocos)", testX + 8, testY + 6 + itemH, 0xFFFFFFFF.toInt(), false)
    }

    private fun renderExitConfirmModal(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val modalW = 280
        val modalH = 100
        val modalX = (width - modalW) / 2
        val modalY = (height - modalH) / 2

        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF1C1C24.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 20, 0xFFD32F2F.toInt())
        guiGraphics.drawString(font, "⚠️ Alterações Não Salvas", modalX + 10, modalY + 6, 0xFFFFFFFF.toInt(), true)

        guiGraphics.drawString(font, "Há modificações não salvas no projeto.", modalX + 15, modalY + 32, 0xFFCCCCCC.toInt(), false)
        guiGraphics.drawString(font, "Deseja salvar antes de sair?", modalX + 15, modalY + 46, 0xFFA0A0A0.toInt(), false)

        val btnW = 80
        val btnH = 18
        val btnY = modalY + 70

        val b1X = modalX + 12
        val h1 = mouseX >= b1X && mouseX <= b1X + btnW && mouseY >= btnY && mouseY <= btnY + btnH
        guiGraphics.fill(b1X, btnY, b1X + btnW, btnY + btnH, if (h1) 0xFF388E3C.toInt() else 0xFF2E7D32.toInt())
        guiGraphics.drawString(font, "Salvar e Sair", b1X + 8, btnY + 5, 0xFFFFFFFF.toInt(), false)

        val b2X = modalX + 98
        val h2 = mouseX >= b2X && mouseX <= b2X + btnW && mouseY >= btnY && mouseY <= btnY + btnH
        guiGraphics.fill(b2X, btnY, b2X + btnW, btnY + btnH, if (h2) 0xFFD32F2F.toInt() else 0xFFC62828.toInt())
        guiGraphics.drawString(font, "Sair sem Salvar", b2X + 6, btnY + 5, 0xFFFFFFFF.toInt(), false)

        val b3X = modalX + 184
        val h3 = mouseX >= b3X && mouseX <= b3X + btnW && mouseY >= btnY && mouseY <= btnY + btnH
        guiGraphics.fill(b3X, btnY, b3X + btnW, btnY + btnH, if (h3) 0xFF555566.toInt() else 0xFF333344.toInt())
        guiGraphics.drawString(font, "Cancelar", b3X + 18, btnY + 5, 0xFFFFFFFF.toInt(), false)
    }

    private fun renderGhostPlacementNode(guiGraphics: GuiGraphics) {
        val ghost = activePlacementNode ?: return
        ghost.x = currentMouseWorldX - ghost.width / 2.0
        ghost.y = currentMouseWorldY - ghost.height / 2.0

        val gx = ghost.x.toInt()
        val gy = ghost.y.toInt()
        val gw = (ghost.width * 1.1).toInt()
        val gh = (ghost.height * 1.1).toInt()

        guiGraphics.fill(gx - 3, gy - 3, gx + gw + 3, gy + gh + 3, 0xAAFFD700.toInt())
        guiGraphics.fill(gx, gy, gx + gw, gy + gh, 0xCC1E1E24.toInt())

        val titleText = font.plainSubstrByWidth("👻 ${ghost.title}", gw - 8)
        guiGraphics.drawString(font, titleText, gx + 6, gy + 6, 0xFFFFFF00.toInt(), true)

        val subText = font.plainSubstrByWidth("Clique p/ Soltar", gw - 8)
        guiGraphics.drawString(font, subText, gx + 6, gy + 26, 0xFFA0A0A0.toInt(), false)
    }

    private fun renderSceneContainers(guiGraphics: GuiGraphics, isModalOpen: Boolean = false) {
        val scenesToRender = if (editingConstructionNode != null) emptyList() else project.scenes

        scenesToRender.forEach { scene ->
            val sx = scene.x.toInt()
            val sy = scene.y.toInt()
            val sw = scene.width.toInt()
            val sh = scene.height.toInt()

            val isHoveredByGhost = activePlacementNode != null &&
                currentMouseWorldX >= scene.x && currentMouseWorldX <= scene.x + scene.width &&
                currentMouseWorldY >= scene.y && currentMouseWorldY <= scene.y + scene.height

            val isSelectedScene = project.activeSceneId == scene.id
            val borderColor = when {
                isHoveredByGhost -> 0xFFFFD700.toInt()
                isSelectedScene -> 0xFF00FFCC.toInt()
                else -> 0xFF3D5AFE.toInt()
            }

            guiGraphics.fill(sx, sy, sx + sw, sy + sh, 0x1A1E1E28)
            guiGraphics.fill(sx - 1, sy - 1, sx + sw + 1, sy, borderColor)
            guiGraphics.fill(sx - 1, sy + sh, sx + sw + 1, sy + sh + 1, borderColor)
            guiGraphics.fill(sx - 1, sy, sx, sy + sh, borderColor)
            guiGraphics.fill(sx + sw, sy, sx + sw + 1, sy + sh, borderColor)

            guiGraphics.fill(sx, sy - 20, sx + sw, sy, 0xFF2A2A3A.toInt())

            val inY = sy + 40
            val outY = sy + 40
            val r = 5

            guiGraphics.fill(sx - r, inY - r, sx + r, inY + r, 0xFF4CAF50.toInt())
            guiGraphics.fill(sx + sw - r, outY - r, sx + sw + r, outY + r, 0xFFFF9800.toInt())

            if (!isModalOpen) {
                val sceneBadge = when {
                    scene.isStartScene && scene.isEndScene -> " [INÍCIO & FIM]"
                    scene.isStartScene -> " 🟢 [INÍCIO]"
                    scene.isEndScene -> " 🛑 [FIM]"
                    else -> ""
                }
                guiGraphics.drawString(font, "🎬 CENA: ${scene.title}$sceneBadge", sx + 8, sy - 14, 0xFF00FFCC.toInt(), false)
                guiGraphics.drawString(font, "In", sx + r + 3, inY - 4, 0xFFCCCCCC.toInt(), false)
                guiGraphics.drawString(font, "Out", sx + sw - r - 22, outY - 4, 0xFFCCCCCC.toInt(), false)
            }

            val rx = sx + sw - 14
            val ry = sy + sh - 14
            val rw = 14
            val rh = 14

            val isHoveredResize = currentMouseWorldX >= scene.x + scene.width - 14 && currentMouseWorldX <= scene.x + scene.width + 4 &&
                                  currentMouseWorldY >= scene.y + scene.height - 14 && currentMouseWorldY <= scene.y + scene.height + 4

            val handleColor = if (isHoveredResize) 0xFF00FFCC.toInt() else 0xFFFFD700.toInt()

            guiGraphics.fill(rx, ry, rx + rw, ry + rh, handleColor)
            guiGraphics.fill(rx + 2, ry + 2, rx + rw - 2, ry + rh - 2, 0xFF18181C.toInt())
            guiGraphics.fill(rx + 4, ry + 10, rx + 10, ry + 12, handleColor)
            guiGraphics.fill(rx + 7, ry + 7, rx + 12, ry + 9, handleColor)
            guiGraphics.fill(rx + 10, ry + 4, rx + 12, ry + 6, handleColor)

            if (isHoveredResize && !isModalOpen) {
                guiGraphics.drawString(font, "⤢", (sx + sw + 4), (sy + sh - 12), 0xFF00FFCC.toInt(), false)
            }
        }
    }

    private fun renderGrid(guiGraphics: GuiGraphics) {
        val isInsideSubCanvas = editingConstructionNode != null
        val minorGridColor = if (isInsideSubCanvas) 0xFF15222E.toInt() else 0xFF1C1C22.toInt()
        val majorGridColor = if (isInsideSubCanvas) 0xFF1D3B4D.toInt() else 0xFF2A2A34.toInt()
        val originLineColor = if (isInsideSubCanvas) 0xFF00E676.toInt() else 0xFF3D5AFE.toInt()

        val topOffset = toolbarHeight + sceneBarHeight

        val baseGridSize = 30.0
        val gridSize = (baseGridSize * zoom).coerceAtLeast(10.0)
        val majorGridSize = gridSize * 5

        var startX = (panX % gridSize)
        if (startX > 0) startX -= gridSize
        var startY = (panY % gridSize)
        if (startY > 0) startY -= gridSize

        var x = startX
        while (x < width) {
            val ix = x.toInt()
            if (ix >= 0 && ix < width) {
                guiGraphics.fill(ix, topOffset, ix + 1, height, minorGridColor)
            }
            x += gridSize
        }

        var y = startY
        while (y < height) {
            val iy = y.toInt()
            if (iy >= topOffset && iy < height) {
                guiGraphics.fill(0, iy, width, iy + 1, minorGridColor)
            }
            y += gridSize
        }

        var majorStartX = (panX % majorGridSize)
        if (majorStartX > 0) majorStartX -= majorGridSize
        var majorStartY = (panY % majorGridSize)
        if (majorStartY > 0) majorStartY -= majorGridSize

        var mx = majorStartX
        while (mx < width) {
            val imx = mx.toInt()
            if (imx >= 0 && imx < width) {
                guiGraphics.fill(imx, topOffset, imx + 1, height, majorGridColor)
            }
            mx += majorGridSize
        }

        var my = majorStartY
        while (my < height) {
            val imy = my.toInt()
            if (imy >= topOffset && imy < height) {
                guiGraphics.fill(0, imy, width, imy + 1, majorGridColor)
            }
            my += majorGridSize
        }

        val originX = panX.toInt()
        val originY = panY.toInt()

        if (originX in 0 until width) {
            guiGraphics.fill(originX, topOffset, originX + 2, height, originLineColor)
        }
        if (originY in topOffset until height) {
            guiGraphics.fill(0, originY, width, originY + 2, originLineColor)
        }
    }

    private fun renderConnections(guiGraphics: GuiGraphics) {
        if (editingConstructionNode != null) {
            val connections = getActiveConnections()
            connections.forEach { conn ->
                val fromWidget = nodeWidgets.find { it.node.id == conn.fromNodeId }
                val toWidget = nodeWidgets.find { it.node.id == conn.toNodeId }

                if (fromWidget != null && toWidget != null) {
                    val fromPortIdx = fromWidget.node.outputs.indexOfFirst { it.id == conn.fromPortId }
                    val toPortIdx = toWidget.node.inputs.indexOfFirst { it.id == conn.toPortId }

                    if (fromPortIdx >= 0 && toPortIdx >= 0) {
                        val (x1, y1) = fromWidget.getOutputPortWorldPos(fromPortIdx)
                        val (x2, y2) = toWidget.getInputPortWorldPos(toPortIdx)
                        drawBezierCurve(guiGraphics, x1, y1, x2, y2, 0xFF4CAF50.toInt())
                    }
                }
            }
        } else {
            project.scenes.forEach { scene ->
                scene.connections.forEach { conn ->
                    val fromWidget = nodeWidgets.find { it.node.id == conn.fromNodeId }
                    val toWidget = nodeWidgets.find { it.node.id == conn.toNodeId }

                    if (fromWidget != null && toWidget != null) {
                        val fromPortIdx = fromWidget.node.outputs.indexOfFirst { it.id == conn.fromPortId }
                        val toPortIdx = toWidget.node.inputs.indexOfFirst { it.id == conn.toPortId }

                        if (fromPortIdx >= 0 && toPortIdx >= 0) {
                            val (x1, y1) = fromWidget.getOutputPortWorldPos(fromPortIdx)
                            val (x2, y2) = toWidget.getInputPortWorldPos(toPortIdx)
                            drawBezierCurve(guiGraphics, x1, y1, x2, y2, 0xFF4CAF50.toInt())
                        }
                    }
                }
            }

            project.sceneConnections.forEach { conn ->
                val fromScene = project.scenes.find { it.id == conn.fromNodeId }
                val toScene = project.scenes.find { it.id == conn.toNodeId }
                if (fromScene != null && toScene != null) {
                    val x1 = fromScene.x + fromScene.width
                    val y1 = fromScene.y + 40
                    val x2 = toScene.x
                    val y2 = toScene.y + 40
                    drawBezierCurve(guiGraphics, x1, y1, x2, y2, 0xFF00FFCC.toInt())
                }
            }
        }
    }

    private fun renderActiveConnectionPreview(guiGraphics: GuiGraphics) {
        val sourceNode = connectingSourceNode
        val sourcePort = connectingSourcePort
        val sourceType = connectingSourceType
        val sourceScene = connectingSourceScene

        val (x1, y1) = if (sourceScene != null) {
            if (sourceType == PortType.OUTPUT) {
                Pair(sourceScene.x + sourceScene.width, sourceScene.y + 40)
            } else {
                Pair(sourceScene.x, sourceScene.y + 40)
            }
        } else if (sourceNode != null && sourcePort != null && sourceType != null) {
            val sourceWidget = nodeWidgets.find { it.node.id == sourceNode.id }
            if (sourceWidget != null) {
                if (sourceType == PortType.OUTPUT) {
                    val idx = sourceNode.outputs.indexOfFirst { it.id == sourcePort.id }
                    if (idx >= 0) sourceWidget.getOutputPortWorldPos(idx) else Pair(sourceNode.x, sourceNode.y)
                } else {
                    val idx = sourceNode.inputs.indexOfFirst { it.id == sourcePort.id }
                    if (idx >= 0) sourceWidget.getInputPortWorldPos(idx) else Pair(sourceNode.x, sourceNode.y)
                }
            } else {
                Pair(sourceNode.x, sourceNode.y)
            }
        } else return

        drawBezierCurve(guiGraphics, x1, y1, currentMouseWorldX, currentMouseWorldY, 0xFFFFD700.toInt())
    }

    private fun drawLine(guiGraphics: GuiGraphics, x1: Int, y1: Int, x2: Int, y2: Int, color: Int, thickness: Int = 2) {
        val dx = abs(x2 - x1)
        val dy = abs(y2 - y1)
        val sx = if (x1 < x2) 1 else -1
        val sy = if (y1 < y2) 1 else -1
        var err = dx - dy

        var cx = x1
        var cy = y1

        val halfThick = thickness / 2
        val extraThick = thickness % 2

        while (true) {
            guiGraphics.fill(cx - halfThick, cy - halfThick, cx + halfThick + extraThick, cy + halfThick + extraThick, color)

            if (cx == x2 && cy == y2) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                cx += sx
            }
            if (e2 < dx) {
                err += dx
                cy += sy
            }
        }
    }

    private fun drawBezierCurve(guiGraphics: GuiGraphics, x1: Double, y1: Double, x2: Double, y2: Double, color: Int) {
        val dx = abs(x2 - x1) * 0.5
        val p0x = x1
        val p0y = y1
        val p1x = x1 + dx
        val p1y = y1
        val p2x = x2 - dx
        val p2y = y2
        val p3x = x2
        val p3y = y2

        val dist = Math.hypot(x2 - x1, y2 - y1)
        val segments = (dist / 3.0).toInt().coerceIn(20, 300)

        var prevX = p0x
        var prevY = p0y

        for (i in 1..segments) {
            val t = i.toDouble() / segments
            val invT = 1.0 - t

            val bx = invT * invT * invT * p0x + 3 * invT * invT * t * p1x + 3 * invT * t * t * p2x + t * t * t * p3x
            val by = invT * invT * invT * p0y + 3 * invT * invT * t * p1y + 3 * invT * t * t * p2y + t * t * t * p3y

            drawLine(guiGraphics, prevX.toInt(), prevY.toInt(), bx.toInt(), by.toInt(), color, thickness = 2)

            prevX = bx
            prevY = by
        }
    }

    private fun updateHoveredPort() {
        hoveredPort = null
        for (widget in nodeWidgets) {
            val portPair = widget.getPortAtWorldPos(currentMouseWorldX, currentMouseWorldY)
            if (portPair != null) {
                hoveredPort = portPair.first
                break
            }
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (activeDocModal != null) return activeDocModal!!.mouseClicked(mouseX, mouseY, button)
        if (activeVariableModal != null) return activeVariableModal!!.mouseClicked(mouseX, mouseY, button)
        if (activeMetadataModal != null) return activeMetadataModal!!.mouseClicked(mouseX, mouseY, button)
        if (activeVarSelectorModal != null) return activeVarSelectorModal!!.mouseClicked(mouseX, mouseY, button)
        if (activeActionTriggerPickerModal != null) return activeActionTriggerPickerModal!!.mouseClicked(mouseX, mouseY, button)
        if (activePokemonConfigModal != null) return activePokemonConfigModal!!.mouseClicked(mouseX, mouseY, button)

        if (showExitConfirmModal) {
            val modalW = 280
            val modalH = 100
            val modalX = (width - modalW) / 2
            val modalY = (height - modalH) / 2
            val btnW = 80
            val btnH = 18
            val btnY = modalY + 70

            val b1X = modalX + 12
            val b2X = modalX + 98
            val b3X = modalX + 184

            if (mouseY >= btnY && mouseY <= btnY + btnH) {
                if (mouseX >= b1X && mouseX <= b1X + btnW) {
                    saveProject()
                    showExitConfirmModal = false
                    minecraft?.setScreen(parentScreen)
                    return true
                } else if (mouseX >= b2X && mouseX <= b2X + btnW) {
                    showExitConfirmModal = false
                    minecraft?.setScreen(parentScreen)
                    return true
                } else if (mouseX >= b3X && mouseX <= b3X + btnW) {
                    showExitConfirmModal = false
                    return true
                }
            }
            return true
        }

        val dropY = toolbarHeight + sceneBarHeight + 2
        val itemH = 18

        if (isFileMenuOpen) {
            val dropW = 140
            val dropX = fileMenuX
            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY < dropY + itemH * 4) {
                val idx = ((mouseY - (dropY + 2)) / itemH).toInt()
                when (idx) {
                    0 -> saveProject()
                    1 -> checkDirtyBeforeAction {
                        minecraft?.setScreen(LoadStoryScreen(this) { loaded ->
                            project = loaded
                            editingConstructionNode = null
                            openSceneIds.clear()
                            openConstructionNodes.clear()
                            loaded.scenes.forEach { openSceneIds.add(it.id) }
                            isDirty = false
                            init()
                            rebuildNodeWidgets()
                            showStatus("História carregada: ${loaded.name}")
                        })
                    }
                    2 -> exportJson()
                    3 -> openMetadataModal()
                }
                isFileMenuOpen = false
                return true
            } else if (mouseY > toolbarHeight + sceneBarHeight) {
                isFileMenuOpen = false
            }
        }

        if (isAddMenuOpen) {
            val dropW = 160
            val dropX = addMenuX
            val itemsCount = 5
            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY < dropY + itemH * itemsCount) {
                val idx = ((mouseY - (dropY + 2)) / itemH).toInt()
                when (idx) {
                    0 -> createNewSceneFrame()
                    1 -> {
                        activeVariableModal = StoryVariableManagerModalWidget(
                            project = project,
                            font = font,
                            screenWidth = width,
                            screenHeight = height,
                            onClose = { activeVariableModal = null },
                            onDataChanged = { markDirty() }
                        )
                        isAddMenuOpen = false
                    }
                    2 -> {
                        addNode(NodeType.VARIABLE_GET)
                        isAddMenuOpen = false
                    }
                    3 -> {
                        addNode(NodeType.VARIABLE_SET)
                        isAddMenuOpen = false
                    }
                    4 -> {
                        isBlockPaletteOpen = true
                        isAddMenuOpen = false
                    }
                }
                return true
            } else if (mouseY > toolbarHeight + sceneBarHeight) {
                isAddMenuOpen = false
            }
        }

        if (isSystemMenuOpen) {
            val dropW = 155
            val dropX = systemMenuX
            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY < dropY + itemH * 2) {
                val idx = ((mouseY - (dropY + 2)) / itemH).toInt()
                when (idx) {
                    0 -> minecraft?.setScreen(EditorSettingsScreen(this))
                    1 -> activeDocModal = StoryDocumentationModalWidget(font, width, height) { activeDocModal = null }
                }
                isSystemMenuOpen = false
                return true
            } else if (mouseY > toolbarHeight + sceneBarHeight) {
                isSystemMenuOpen = false
            }
        }

        if (isTestMenuOpen) {
            val testW = 195
            val dropdownH = itemH * 2 + 4

            val testX = (testBtnX + getBtnWidth("▶ Testar ▾") - testW).coerceAtMost(width - testW - 5).coerceAtLeast(5)
            val testY = testBtnY - dropdownH - 4

            if (mouseX >= testX && mouseX <= testX + testW && mouseY >= testY && mouseY <= testY + dropdownH) {
                val relY = mouseY - (testY + 2)
                if (relY >= 0 && relY < itemH) {
                    saveProject()
                    val serverPlayer = minecraft?.player?.uuid?.let { minecraft?.singleplayerServer?.playerList?.getPlayer(it) }
                    StoryExecutor.startStory(project, serverPlayer)
                    minecraft?.setScreen(null)
                } else if (relY >= itemH && relY < itemH * 2) {
                    isRangeTestSelectionMode = true
                    rangeTestStartNode = null
                    rangeTestEndNode = null
                    rebuildNodeWidgets()
                    showStatus("🎯 Clique no 1º bloco para definir o INÍCIO do teste. (Mova/Pan livre na tela)")
                }
                isTestMenuOpen = false
                return true
            } else {
                val testBtnW = getBtnWidth("▶ Testar ▾")
                val testBtnH = 16
                if (!(mouseX >= testBtnX && mouseX <= testBtnX + testBtnW && mouseY >= testBtnY && mouseY <= testBtnY + testBtnH)) {
                    isTestMenuOpen = false
                }
            }
        }

        if (activeContextMenu?.mouseClicked(mouseX, mouseY, button, width, height) == true) {
            return true
        }

        if (isBlockPaletteOpen) {
            val palX = 10
            val palY = toolbarHeight + sceneBarHeight + 2
            val palW = 185
            val maxVisibleItems = 10
            val visibleHeight = maxVisibleItems * itemH + 4

            if (mouseX >= palX && mouseX <= palX + palW && mouseY >= palY && mouseY < palY + visibleHeight) {
                val idx = ((mouseY - (palY + 2)) / itemH).toInt() + paletteScrollOffset
                if (idx in paletteEntries.indices) {
                    val entry = paletteEntries[idx]
                    if (!entry.isHeader) {
                        addNode(entry.type)
                    }
                }
                isBlockPaletteOpen = false
                return true
            } else if (mouseY > toolbarHeight + sceneBarHeight) {
                isBlockPaletteOpen = false
            }
        }

        if (mouseY >= toolbarHeight && mouseY <= toolbarHeight + sceneBarHeight && !isRangeTestSelectionMode) {
            val barY = toolbarHeight
            val tabH = 14
            var currentX = 10 - tabBarScrollOffset

            val constrSnapshot = openConstructionNodes.toList()
            for (constr in constrSnapshot) {
                val label = "🏗️ ${constr.title}"
                val labelW = font.width(label)
                val tabW = labelW + 22
                val tabX = currentX

                if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= barY + 2 && mouseY <= barY + 2 + tabH) {
                    if (mouseX >= tabX + tabW - 14 && mouseX <= tabX + tabW - 2) {
                        openConstructionNodes.remove(constr)
                        if (editingConstructionNode?.id == constr.id) {
                            editingConstructionNode = null
                            init()
                            rebuildNodeWidgets()
                        }
                        showStatus("Aba da construção ${constr.title} fechada.")
                    } else {
                        editingConstructionNode = constr
                        init()
                        rebuildNodeWidgets()
                        showStatus("Foco alterado para construção ${constr.title}")
                    }
                    return true
                }
                currentX += tabW + 4
            }

            val visibleScenes = project.scenes.filter { openSceneIds.contains(it.id) }
            for (scene in visibleScenes) {
                val label = "🎬 ${scene.title}"
                val labelW = font.width(label)
                val tabW = labelW + 22
                val tabX = currentX

                if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= barY + 2 && mouseY <= barY + 2 + tabH) {
                    if (mouseX >= tabX + tabW - 14 && mouseX <= tabX + tabW - 2) {
                        if (openSceneIds.size > 1) {
                            openSceneIds.remove(scene.id)
                            if (project.activeSceneId == scene.id) {
                                val remainingId = openSceneIds.firstOrNull() ?: project.scenes.first().id
                                project.activeSceneId = remainingId
                            }
                            rebuildNodeWidgets()
                            showStatus("Aba da cena ${scene.title} fechada.")
                        } else {
                            showStatus("Não é possível fechar a única aba de cena visível!")
                        }
                    } else {
                        editingConstructionNode = null
                        centerCameraOnScene(scene)
                    }
                    return true
                }
                currentX += tabW + 4
            }
            return true
        }

        if (activeInspector?.mouseClicked(mouseX, mouseY, button) == true) return true
        if (activeSceneInspector?.mouseClicked(mouseX, mouseY, button) == true) return true

        if (mouseY < toolbarHeight + sceneBarHeight) {
            return super.mouseClicked(mouseX, mouseY, button)
        }

        val worldX = screenToWorldX(mouseX)
        val worldY = screenToWorldY(mouseY)

        // MINI-MODO DE SELEÇÃO DE INTERVALO DE TESTE (Permite Panning / Mover a tela se clicar no espaço vazio!)
        if (isRangeTestSelectionMode && button == 0) {
            val clickedWidget = nodeWidgets.reversed().find { it.isWorldPosInside(worldX, worldY) }
            if (clickedWidget != null) {
                if (rangeTestStartNode == null) {
                    rangeTestStartNode = clickedWidget.node
                    rebuildNodeWidgets()
                    showStatus("▶ Início definido: ${clickedWidget.node.title}. Clique no 2º bloco (FIM DO TESTE).")
                } else {
                    rangeTestEndNode = clickedWidget.node
                    rebuildNodeWidgets()
                    showStatus("🛑 Fim definido: ${clickedWidget.node.title}. Executando teste do intervalo...")
                    startRangeTestExecution()
                }
                return true
            } else {
                // Clicou fora dos blocos: ativa o panning da câmera
                isPanning = true
                lastMouseX = mouseX
                lastMouseY = mouseY
                return true
            }
        }

        val ghost = activePlacementNode
        if (ghost != null && button == 0) {
            val targetConstructionWidget = nodeWidgets.find { it.node.nodeType == NodeType.CONSTRUCTION && it.isWorldPosInside(worldX, worldY) }
            if (targetConstructionWidget != null) {
                val targetNode = targetConstructionWidget.node
                if (!openConstructionNodes.contains(targetNode)) {
                    openConstructionNodes.add(targetNode)
                }
                editingConstructionNode = targetNode
                init()
                rebuildNodeWidgets()
                showStatus("Entrado na Construção ${targetNode.title}. Clique para posicionar o bloco interno.")
                return true
            }

            ghost.x = worldX - ghost.width / 2.0
            ghost.y = worldY - ghost.height / 2.0

            val targetScene = project.scenes.find { scene -> isNodeInsideScene(ghost, scene) }

            if (targetScene != null && editingConstructionNode == null) {
                ghost.parentSceneId = targetScene.id
                if (!targetScene.nodes.contains(ghost)) {
                    targetScene.nodes.add(ghost)
                }
                updateVariableSceneBinding(ghost, targetScene.id)
                showStatus("Bloco vinculado à cena: ${targetScene.title}")
            } else {
                ghost.parentSceneId = null
                val activeScene = project.getActiveScene()
                val targetList = if (editingConstructionNode != null) editingConstructionNode!!.innerNodes else activeScene?.nodes
                if (targetList != null && !targetList.contains(ghost)) {
                    targetList.add(ghost)
                }
                if (activeScene != null) {
                    updateVariableSceneBinding(ghost, activeScene.id)
                }
                showStatus("Bloco posicionado como nó livre no Estúdio.")
            }

            markDirty()
            rebuildNodeWidgets()
            openInspectorForNode(ghost)
            activePlacementNode = null
            return true
        }

        if (button == 1) {
            for (widget in nodeWidgets.reversed()) {
                if (widget.isWorldPosInside(worldX, worldY)) {
                    openContextMenuForNode(widget.node, mouseX.toInt(), mouseY.toInt())
                    return true
                }
            }
            if (editingConstructionNode == null) {
                for (scene in project.scenes) {
                    if (worldX >= scene.x && worldX <= scene.x + scene.width && worldY >= scene.y - 20 && worldY <= scene.y) {
                        openContextMenuForScene(scene, mouseX.toInt(), mouseY.toInt())
                        return true
                    }
                }
            }
            return true
        }

        if (editingConstructionNode == null) {
            for (scene in project.scenes) {
                val sx = scene.x
                val sy = scene.y
                val sw = scene.width
                val inY = sy + 40
                val outY = sy + 40
                val r = 8

                if (Math.hypot(worldX - sx, worldY - inY) <= r) {
                    val now = System.currentTimeMillis()
                    if (lastClickedPortId == scene.inPort.id && now - lastClickedPortTime < 350) {
                        project.sceneConnections.removeAll { it.toNodeId == scene.id && it.toPortId == scene.inPort.id }
                        markDirty()
                        showStatus("Desconectado da porta In da Cena!")
                        connectingSourceScene = null
                        lastClickedPortId = null
                        return true
                    }
                    lastClickedPortId = scene.inPort.id
                    lastClickedPortTime = now

                    connectingSourceScene = scene
                    connectingSourcePort = scene.inPort
                    connectingSourceType = PortType.INPUT
                    return true
                }

                if (Math.hypot(worldX - (sx + sw), worldY - outY) <= r) {
                    val now = System.currentTimeMillis()
                    if (lastClickedPortId == scene.outPort.id && now - lastClickedPortTime < 350) {
                        project.sceneConnections.removeAll { it.fromNodeId == scene.id && it.fromPortId == scene.outPort.id }
                        markDirty()
                        showStatus("Desconectado da porta Out da Cena!")
                        connectingSourceScene = null
                        lastClickedPortId = null
                        return true
                    }
                    lastClickedPortId = scene.outPort.id
                    lastClickedPortTime = now

                    connectingSourceScene = scene
                    connectingSourcePort = scene.outPort
                    connectingSourceType = PortType.OUTPUT
                    return true
                }
            }
        }

        for (widget in nodeWidgets.reversed()) {
            val portPair = widget.getPortAtWorldPos(worldX, worldY)
            if (portPair != null) {
                val (port, portType) = portPair
                val now = System.currentTimeMillis()

                if (lastClickedPortId == port.id && now - lastClickedPortTime < 350) {
                    val conns = getActiveConnections()
                    val removed = conns.removeAll { it.fromPortId == port.id || it.toPortId == port.id }
                    if (removed) {
                        markDirty()
                        showStatus("Desconectado da porta!")
                        connectingSourceNode = null
                        connectingSourcePort = null
                        connectingSourceType = null
                        lastClickedPortId = null
                        return true
                    }
                }

                lastClickedPortId = port.id
                lastClickedPortTime = now

                connectingSourceNode = widget.node
                connectingSourcePort = port
                connectingSourceType = portType
                return true
            }
        }

        var clickedOnNode = false
        for (widget in nodeWidgets.reversed()) {
            if (widget.isWorldPosInside(worldX, worldY)) {
                selectedWidget?.isSelected = false
                selectedWidget = widget
                widget.isSelected = true
                widget.isDragging = true
                draggedWidget = widget
                clickedOnNode = true
                openInspectorForNode(widget.node)
                break
            }
        }

        if (!clickedOnNode && editingConstructionNode == null) {
            for (scene in project.scenes) {
                val sx = scene.x
                val sy = scene.y
                val sw = scene.width
                val sh = scene.height

                if (worldX >= sx + sw - 14 && worldX <= sx + sw + 4 && worldY >= sy + sh - 14 && worldY <= sy + sh + 4) {
                    resizingScene = scene
                    project.activeSceneId = scene.id
                    markDirty()
                    return true
                }

                if (worldX >= sx && worldX <= sx + sw && worldY >= sy - 20 && worldY <= sy) {
                    draggedScene = scene
                    project.activeSceneId = scene.id
                    rebuildNodeWidgets()
                    openSceneInspector(scene)
                    return true
                }
            }
        }

        if (!clickedOnNode && draggedScene == null && resizingScene == null) {
            selectedWidget?.isSelected = false
            selectedWidget = null
            activeInspector = null
            activeSceneInspector = null
            activeContextMenu = null
            closeAllTopMenus()
            isPanning = true
            lastMouseX = mouseX
            lastMouseY = mouseY
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun openContextMenuForNode(node: NodeData, screenX: Int, screenY: Int) {
        activeContextMenu = ContextMenuWidget(
            screenX = screenX,
            screenY = screenY,
            targetNode = node,
            font = font,
            onAction = { action -> handleContextMenuNodeAction(node, action) },
            onClose = { activeContextMenu = null }
        )
    }

    private fun openContextMenuForScene(scene: SceneData, screenX: Int, screenY: Int) {
        activeContextMenu = ContextMenuWidget(
            screenX = screenX,
            screenY = screenY,
            targetScene = scene,
            font = font,
            onAction = { action -> handleContextMenuSceneAction(scene, action) },
            onClose = { activeContextMenu = null }
        )
    }

    private fun handleContextMenuNodeAction(node: NodeData, action: ContextMenuAction) {
        val currentNodes = getActiveNodes()
        val currentConns = getActiveConnections()

        when (action) {
            ContextMenuAction.DELETE -> {
                currentNodes.remove(node)
                currentConns.removeAll { it.fromNodeId == node.id || it.toNodeId == node.id }
                openConstructionNodes.remove(node)
                if (selectedWidget?.node?.id == node.id) selectedWidget = null
                if (activeInspector?.node?.id == node.id) activeInspector = null
                markDirty()
                rebuildNodeWidgets()
                showStatus("Bloco excluído!")
            }
            ContextMenuAction.DUPLICATE -> {
                val inputs = node.inputs.map { PortData(name = it.name, type = it.type) }.toMutableList()
                val outputs = node.outputs.map { PortData(name = it.name, type = it.type) }.toMutableList()
                val cloneNode = NodeData(
                    parentSceneId = node.parentSceneId,
                    title = "${node.title} (Cópia)",
                    nodeType = node.nodeType,
                    content = node.content,
                    x = node.x + 20.0,
                    y = node.y + 20.0,
                    width = node.width,
                    height = node.height,
                    inputs = inputs,
                    outputs = outputs,
                    params = HashMap(node.params)
                )
                currentNodes.add(cloneNode)
                markDirty()
                rebuildNodeWidgets()
                openInspectorForNode(cloneNode)
                showStatus("Bloco duplicado!")
            }
            ContextMenuAction.DETACH_FROM_SCENE -> {
                val parentScene = project.scenes.find { it.nodes.contains(node) || node.parentSceneId == it.id }
                if (parentScene != null) {
                    parentScene.nodes.remove(node)
                    parentScene.connections.removeAll { it.fromNodeId == node.id || it.toNodeId == node.id }
                    node.x = parentScene.x + parentScene.width + 40.0
                    node.y = parentScene.y
                    rebuildNodeWidgets()
                }

                node.parentSceneId = null
                activePlacementNode = null
                activeContextMenu = null
                activeInspector = null
                markDirty()
                showStatus("Bloco desvinculado!")
            }
            ContextMenuAction.COPY_DATA -> {
                BlockDataClipboard.copyFrom(node)
                showStatus("Dados do bloco copiados!")
            }
            ContextMenuAction.PASTE_DATA -> {
                if (BlockDataClipboard.pasteTo(node)) {
                    markDirty()
                    rebuildNodeWidgets()
                    openInspectorForNode(node)
                    showStatus("Dados colados no bloco!")
                } else {
                    showStatus("Tipo de bloco incompatível!")
                }
            }
            ContextMenuAction.RESET_PROPERTIES -> {
                node.content = ""
                node.params.clear()
                markDirty()
                rebuildNodeWidgets()
                openInspectorForNode(node)
                showStatus("Propriedades do bloco resetadas!")
            }
            ContextMenuAction.DISCONNECT_PORTS -> {
                val removed = currentConns.removeAll { it.fromNodeId == node.id || it.toNodeId == node.id }
                if (removed) markDirty()
                showStatus(if (removed) "Portas desconectadas!" else "Nenhuma conexão encontrada.")
            }
        }
    }

    private fun handleContextMenuSceneAction(scene: SceneData, action: ContextMenuAction) {
        when (action) {
            ContextMenuAction.DELETE -> {
                if (project.scenes.size > 1) {
                    project.scenes.remove(scene)
                    openSceneIds.remove(scene.id)
                    project.sceneConnections.removeAll { it.fromNodeId == scene.id || it.toNodeId == scene.id }
                    scene.nodes.forEach { n ->
                        project.scenes.forEach { s -> s.connections.removeAll { c -> c.fromNodeId == n.id || c.toNodeId == n.id } }
                    }
                    if (project.activeSceneId == scene.id) {
                        project.activeSceneId = project.scenes.first().id
                    }
                    selectedWidget = null
                    activeInspector = null
                    activeSceneInspector = null
                    activeContextMenu = null
                    markDirty()
                    rebuildNodeWidgets()
                    showStatus("Cena excluída com sucesso!")
                } else {
                    showStatus("Não é possível excluir a única cena do projeto!")
                }
            }
            ContextMenuAction.DUPLICATE -> {
                val cloneScene = SceneData(
                    title = "${scene.title} (Cópia)",
                    description = scene.description,
                    isStartScene = scene.isStartScene,
                    isEndScene = scene.isEndScene,
                    x = scene.x + 50.0,
                    y = scene.y + 50.0,
                    width = scene.width,
                    height = scene.height
                )
                scene.nodes.forEach { n ->
                    val inputs = n.inputs.map { PortData(name = it.name, type = it.type) }.toMutableList()
                    val outputs = n.outputs.map { PortData(name = it.name, type = it.type) }.toMutableList()
                    cloneScene.nodes.add(
                        NodeData(
                            parentSceneId = cloneScene.id,
                            title = n.title,
                            nodeType = n.nodeType,
                            content = n.content,
                            x = n.x + 50.0,
                            y = n.y + 50.0,
                            width = n.width,
                            height = n.height,
                            inputs = inputs,
                            outputs = outputs,
                            params = HashMap(n.params)
                        )
                    )
                }
                project.scenes.add(cloneScene)
                openSceneIds.add(cloneScene.id)
                markDirty()
                rebuildNodeWidgets()
                openSceneInspector(cloneScene)
                showStatus("Cena duplicada!")
            }
            ContextMenuAction.RESET_PROPERTIES -> {
                scene.nodes.clear()
                scene.connections.clear()
                scene.description = ""
                selectedWidget = null
                activeInspector = null
                activeSceneInspector = null
                activeContextMenu = null
                markDirty()
                rebuildNodeWidgets()
                openSceneInspector(scene)
                showStatus("Cena resetada!")
            }
            else -> {}
        }
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (showExitConfirmModal || activeDocModal != null || activeVariableModal != null || activeMetadataModal != null || activeVarSelectorModal != null) return true

        val targetResizeScene = resizingScene
        if (targetResizeScene != null) {
            val worldX = screenToWorldX(mouseX)
            val worldY = screenToWorldY(mouseY)
            targetResizeScene.width = (worldX - targetResizeScene.x).coerceAtLeast(200.0)
            targetResizeScene.height = (worldY - targetResizeScene.y).coerceAtLeast(150.0)
            markDirty()
            return true
        }

        val targetScene = draggedScene
        if (targetScene != null) {
            val dx = dragX / zoom
            val dy = dragY / zoom
            targetScene.x += dx
            targetScene.y += dy
            targetScene.nodes.filter { it.parentSceneId == targetScene.id || isNodeInsideScene(it, targetScene) }.forEach {
                it.x += dx
                it.y += dy
            }
            markDirty()
            return true
        }

        if (isPanning) {
            panX += dragX
            panY += dragY
            return true
        }

        val targetWidget = draggedWidget
        if (targetWidget != null) {
            targetWidget.node.x += dragX / zoom
            targetWidget.node.y += dragY / zoom
            markDirty()
            return true
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (showExitConfirmModal || activeDocModal != null || activeVariableModal != null || activeMetadataModal != null || activeVarSelectorModal != null) return true

        isPanning = false
        draggedScene = null
        resizingScene = null

        val targetWidget = draggedWidget
        if (targetWidget != null) {
            val node = targetWidget.node
            val targetScene = project.scenes.find { isNodeInsideScene(node, it) }
            if (targetScene != null) {
                node.parentSceneId = targetScene.id
                if (!targetScene.nodes.contains(node)) {
                    targetScene.nodes.add(node)
                }
                updateVariableSceneBinding(node, targetScene.id)
            } else {
                node.parentSceneId = null
            }
        }

        draggedWidget?.isDragging = false
        draggedWidget = null

        val sourceScene = connectingSourceScene
        if (sourceScene != null && connectingSourceType != null) {
            val worldX = screenToWorldX(mouseX)
            val worldY = screenToWorldY(mouseY)

            for (targetScene in project.scenes) {
                if (targetScene.id == sourceScene.id) continue
                val inY = targetScene.y + 40
                val r = 12

                if (Math.hypot(worldX - targetScene.x, worldY - inY) <= r && connectingSourceType == PortType.OUTPUT) {
                    val exists = project.sceneConnections.any {
                        it.fromNodeId == sourceScene.id && it.toNodeId == targetScene.id
                    }
                    if (!exists) {
                        project.sceneConnections.add(
                            ConnectionData(
                                fromNodeId = sourceScene.id,
                                fromPortId = sourceScene.outPort.id,
                                toNodeId = targetScene.id,
                                toPortId = targetScene.inPort.id
                            )
                        )
                        markDirty()
                        showStatus("Conexão entre Cenas criada!")
                    }
                    break
                }
            }
            connectingSourceScene = null
        }

        val sourceNode = connectingSourceNode
        val sourcePort = connectingSourcePort
        val sourceType = connectingSourceType

        if (sourceNode != null && sourcePort != null && sourceType != null) {
            val worldX = screenToWorldX(mouseX)
            val worldY = screenToWorldY(mouseY)

            for (tWidget in nodeWidgets) {
                if (tWidget.node.id == sourceNode.id) continue
                val targetPortPair = tWidget.getPortAtWorldPos(worldX, worldY)
                if (targetPortPair != null) {
                    val (targetPort, targetType) = targetPortPair

                    if (sourceType != targetType) {
                        val fromNodeId = if (sourceType == PortType.OUTPUT) sourceNode.id else tWidget.node.id
                        val fromPortId = if (sourceType == PortType.OUTPUT) sourcePort.id else targetPort.id
                        val toNodeId = if (sourceType == PortType.INPUT) sourceNode.id else tWidget.node.id
                        val toPortId = if (sourceType == PortType.INPUT) sourcePort.id else targetPort.id

                        val activeConns = getActiveConnections()
                        val exists = activeConns.any {
                            it.fromNodeId == fromNodeId && it.fromPortId == fromPortId &&
                            it.toNodeId == toNodeId && it.toPortId == toPortId
                        }
                        if (!exists) {
                            activeConns.add(
                                ConnectionData(
                                    fromNodeId = fromNodeId,
                                    fromPortId = fromPortId,
                                    toNodeId = toNodeId,
                                    toPortId = toPortId
                                )
                            )
                            markDirty()
                            showStatus("Conexão criada!")
                        }
                    }
                    break
                }
            }
        }

        connectingSourceNode = null
        connectingSourcePort = null
        connectingSourceType = null
        connectingSourceScene = null

        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (showExitConfirmModal || activeDocModal != null) return true

        if (activeVariableModal?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
        if (activeVarSelectorModal?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
        if (activeActionTriggerPickerModal?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
        if (activeInspector?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
        if (activeSceneInspector?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true

        if (mouseY >= toolbarHeight && mouseY <= toolbarHeight + sceneBarHeight) {
            if (scrollY < 0) {
                tabBarScrollOffset = (tabBarScrollOffset + 25).coerceAtMost(maxOf(0, (openConstructionNodes.size + openSceneIds.size) * 110 - width + 100))
            } else if (scrollY > 0) {
                tabBarScrollOffset = (tabBarScrollOffset - 25).coerceAtLeast(0)
            }
            return true
        }

        if (isBlockPaletteOpen) {
            val palX = 10
            val palY = toolbarHeight + sceneBarHeight + 2
            val palW = 185
            val maxVisible = 10
            val visibleH = maxVisible * 18 + 4

            if (mouseX >= palX && mouseX <= palX + palW && mouseY >= palY && mouseY <= palY + visibleH) {
                if (scrollY > 0) {
                    paletteScrollOffset = (paletteScrollOffset - 1).coerceAtLeast(0)
                } else if (scrollY < 0) {
                    paletteScrollOffset = (paletteScrollOffset + 1).coerceAtMost(maxOf(0, paletteEntries.size - maxVisible))
                }
                return true
            }
        }

        val zoomFactor = if (scrollY > 0) 1.1 else 0.9
        val oldZoom = zoom
        val newZoom = (zoom * zoomFactor).coerceIn(0.12, 3.0)

        if (oldZoom != newZoom) {
            val worldX = (mouseX - panX) / oldZoom
            val worldY = (mouseY - panY) / oldZoom

            panX = mouseX - worldX * newZoom
            panY = mouseY - worldY * newZoom
            zoom = newZoom
            return true
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (showExitConfirmModal || activeDocModal != null) return true
        if (activeVariableModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeMetadataModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeVarSelectorModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeActionTriggerPickerModal?.charTyped(codePoint, modifiers) == true) return true
        if (activePokemonConfigModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeInspector?.charTyped(codePoint, modifiers) == true) return true
        if (activeSceneInspector?.charTyped(codePoint, modifiers) == true) return true
        return super.charTyped(codePoint, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (activeDocModal != null) {
            if (keyCode == 256) { activeDocModal = null; return true }
            return true
        }

        if (activeVariableModal != null) {
            if (keyCode == 256) { activeVariableModal = null; return true }
            if (activeVariableModal?.keyPressed(keyCode, scanCode, modifiers) == true) return true
            return true
        }

        if (activeMetadataModal != null) {
            if (keyCode == 256) { activeMetadataModal = null; return true }
            if (activeMetadataModal?.keyPressed(keyCode, scanCode, modifiers) == true) return true
            return true
        }

        if (activeVarSelectorModal != null) {
            if (keyCode == 256) { activeVarSelectorModal = null; return true }
            if (activeVarSelectorModal?.keyPressed(keyCode, scanCode, modifiers) == true) return true
            return true
        }

        if (activeActionTriggerPickerModal != null) {
            if (keyCode == 256) { activeActionTriggerPickerModal = null; return true }
            if (activeActionTriggerPickerModal?.keyPressed(keyCode, scanCode, modifiers) == true) return true
            return true
        }

        if (activePokemonConfigModal != null) {
            if (keyCode == 256) { activePokemonConfigModal = null; return true }
            if (activePokemonConfigModal?.keyPressed(keyCode, scanCode, modifiers) == true) return true
            return true
        }

        if (showExitConfirmModal) {
            if (keyCode == 256) { showExitConfirmModal = false; return true }
            return true
        }

        if (keyCode == 256) {
            if (isRangeTestSelectionMode) {
                isRangeTestSelectionMode = false
                rangeTestStartNode = null
                rangeTestEndNode = null
                rebuildNodeWidgets()
                showStatus("Modo de teste por intervalo cancelado.")
                return true
            }
            if (activePlacementNode != null) {
                activePlacementNode = null
                showStatus("Posicionamento cancelado.")
                return true
            }
            if (activeContextMenu != null) {
                activeContextMenu = null
                return true
            }
            if (isFileMenuOpen || isAddMenuOpen || isSystemMenuOpen || isBlockPaletteOpen || isTestMenuOpen) {
                closeAllTopMenus()
                isTestMenuOpen = false
                return true
            }
            if (activeInspector != null || activeSceneInspector != null) {
                activeInspector = null
                activeSceneInspector = null
                return true
            }
            checkDirtyBeforeAction {
                minecraft?.setScreen(parentScreen)
            }
            return true
        }

        if (activeInspector?.keyPressed(keyCode, scanCode, modifiers) == true) return true
        if (activeSceneInspector?.keyPressed(keyCode, scanCode, modifiers) == true) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false
}
