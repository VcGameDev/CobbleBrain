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

    // Dirty State Control
    var isDirty: Boolean = false
    private var showExitConfirmModal: Boolean = false

    // Construction Navigation State (Sub-graph)
    var editingConstructionNode: NodeData? = null

    // Persistent Visual Tabs Logic (Scenes and Constructions)
    private val openSceneIds = mutableSetOf<String>()
    private val openConstructionNodes = mutableListOf<NodeData>()
    private var tabBarScrollOffset: Int = 0

    // Pan & Zoom Transformations
    var panX: Double = 50.0
    var panY: Double = 50.0
    var zoom: Double = 1.0

    // Drag, Resize and Interactivity States
    private var isPanning: Boolean = false
    private var draggedScene: SceneData? = null
    private var resizingScene: SceneData? = null
    private var resizingNode: NodeData? = null
    private var lastClickedNodeId: String? = null
    private var lastClickedNodeTime: Long = 0L
    private var lastClickedSceneId: String? = null
    private var lastClickedSceneTime: Long = 0L
    private var lastMouseX: Double = 0.0
    private var lastMouseY: Double = 0.0

    // Viewport Culling Bounds (world coordinates)
    private var viewLeft: Double = 0.0
    private var viewRight: Double = 0.0
    private var viewTop: Double = 0.0
    private var viewBottom: Double = 0.0

    private var draggedWidget: NodeWidget? = null
    var selectedWidget: NodeWidget? = null
    val selectedWidgets: MutableSet<NodeWidget> = mutableSetOf()

    // Marquee / Box Selection State
    var isSelectionModeActive: Boolean = false
    private var isBoxSelecting: Boolean = false
    private var selectionHoldStartTime: Long = 0L
    private var selectionHoldOriginX: Double = 0.0
    private var selectionHoldOriginY: Double = 0.0
    private var isHoldingForSelection: Boolean = false
    private var lastEmptyCanvasClickTime: Long = 0L
    private var lastEmptyCanvasClickX: Double = 0.0
    private var lastEmptyCanvasClickY: Double = 0.0
    private var selectionOriginWorldX: Double = 0.0
    private var selectionOriginWorldY: Double = 0.0
    private var selectionCurrentWorldX: Double = 0.0
    private var selectionCurrentWorldY: Double = 0.0

    // Ongoing Connection
    private var connectingSourceNode: NodeData? = null
    private var connectingSourcePort: PortData? = null
    private var connectingSourceType: PortType? = null
    private var connectingSourceScene: SceneData? = null
    private var currentMouseWorldX: Double = 0.0
    private var currentMouseWorldY: Double = 0.0

    // Double click on port
    private var lastClickedPortId: String? = null
    private var lastClickedPortTime: Long = 0L

    private var hoveredPort: PortData? = null
    private var statusMessage: String = "CobbleBrain Story Editor loaded."
    private var statusTimer: Int = 0
    private var isStatusWarning: Boolean = false

    private val isInWorld: Boolean
        get() = minecraft?.level != null && minecraft?.player != null

    // Lateral Inspectors
    var activeInspector: NodeInspectorWidget? = null
    var activeSceneInspector: SceneInspectorWidget? = null

    // Dedicated Modals
    private var activeMetadataModal: StoryMetadataModalWidget? = null
    private var activeSaveProfileModal: SaveProfileModalWidget? = null
    private var activeAIDialogueModal: AIDialogueModalWidget? = null
    private var activeDocModal: StoryDocumentationModalWidget? = null
    private var activeVariableModal: StoryVariableManagerModalWidget? = null
    var activeVarSelectorModal: StoryVariableSelectorModalWidget? = null
    var activeActionTriggerPickerModal: ActionTriggerPickerModalWidget? = null
    var activePokemonConfigModal: PokemonConfigModalWidget? = null
    var activeResourcePickerModal: ResourcePickerModalWidget? = null
    var activeItemPickerModal: ItemPickerModalWidget? = null
    var activeEntityConfigModal: EntityConfigModalWidget? = null
    private var activeAnimationSelectorModal: AnimationSelectorModalWidget? = null
    private var activeTextureSelectorModal: TextureSelectorModalWidget? = null
    var activeCoordinateModal: CoordinateConfigModalWidget? = null

    // Context Menu (Right Click)
    var activeContextMenu: ContextMenuWidget? = null

    // Interactive Ghost Placement System
    var activePlacementNode: NodeData? = null

    // Range Test Selection Mode (Start and End Blocks)
    var isRangeTestSelectionMode: Boolean = false
    var rangeTestStartNode: NodeData? = null
    var rangeTestEndNode: NodeData? = null

    // Dropdown Block Palette Split into 3 DISTINCT SECTIONS
    data class PaletteEntry(val isHeader: Boolean, val label: String, val type: NodeType = NodeType.DIALOGUE, val presetSubtype: String? = null)
    private val paletteEntries = listOf(
        // SECTION 1: Structure & Flow
        PaletteEntry(true, "--- 1. STRUCTURE & FLOW ---"),
        PaletteEntry(false, "🟢 Scene Start", NodeType.BEGIN_SCENE),
        PaletteEntry(false, "🛑 Finish Scene", NodeType.END_SCENE),
        PaletteEntry(false, "🏗️ Begin Construction", NodeType.BEGIN_CONSTRUCTION),
        PaletteEntry(false, "🏁 End Construction", NodeType.END_CONSTRUCTION),
        PaletteEntry(false, "🏆 Mission (Quest)", NodeType.QUEST),
        PaletteEntry(false, "⚡ Synchronizer (GATE)", NodeType.GATE),
        PaletteEntry(false, "🏗️ Construction", NodeType.CONSTRUCTION),
        PaletteEntry(false, "📡 Link Sender", NodeType.LINK_SEND),
        PaletteEntry(false, "📡 Link Receiver", NodeType.LINK_RECEIVE),
        PaletteEntry(false, "🔄 Repeater (Loop)", NodeType.LOOP),

        // SECTION 2: Variables & Decisions
        PaletteEntry(true, "--- 2. VARIABLES & DECISIONS ---"),
        PaletteEntry(false, "🔹 Variable Block (Get)", NodeType.VARIABLE_GET),
        PaletteEntry(false, "✏️ Modifier Block (Set)", NodeType.VARIABLE_SET),
        PaletteEntry(false, "🔀 Condition (If/Else)", NodeType.CONDITION_NODE),
        PaletteEntry(false, "🟢 Trigger", NodeType.TRIGGER),

        // SECTION 3: Actions & Events
        PaletteEntry(true, "--- 3. ACTIONS & EVENTS ---"),
        PaletteEntry(false, "💬 Dialogue Block", NodeType.DIALOGUE),
        PaletteEntry(false, "⚡ Action", NodeType.ACTION),
        PaletteEntry(false, "🚶 Move / Pathfind Entity", NodeType.ACTION, "MOVE_TO_BLOCK"),
        PaletteEntry(false, "🏷️ Manage Story Tag", NodeType.ACTION, "TAG_BLOCK"),
        PaletteEntry(false, "⌨️ Command", NodeType.COMMAND_NODE),
        PaletteEntry(false, "⌨️ Key Input / QTE", NodeType.KEY_INPUT),
        PaletteEntry(false, "💾 Save Checkpoint", NodeType.SAVE_STATE_NODE),
        PaletteEntry(false, "🔄 Load Checkpoint", NodeType.LOAD_STATE_NODE),
        PaletteEntry(false, "🎵 Audio / Music", NodeType.AUDIO),
        PaletteEntry(false, "🎨 Change Texture", NodeType.TEXTURE),
        PaletteEntry(false, "⏱ Timer", NodeType.TIMER),
        PaletteEntry(false, "📝 Note", NodeType.COMMENT)
    )
    private var isBlockPaletteOpen: Boolean = false
    private var paletteScrollOffset: Int = 0
    private var isDraggingPaletteScrollbar: Boolean = false
    private var paletteDragStartMouseY: Double = 0.0
    private var paletteDragStartOffset: Int = 0

    // Top Bar Categorized Dropdown Menus
    private var isFileMenuOpen: Boolean = false
    private var isAddMenuOpen: Boolean = false
    private var isSystemMenuOpen: Boolean = false

    private var fileMenuX: Int = 0
    private var addMenuX: Int = 0
    private var systemMenuX: Int = 0

    // Story Test Dropdown Menu (Bottom Right Corner)
    private var isTestMenuOpen: Boolean = false
    private var testBtnX: Int = 0
    private var testBtnY: Int = 0

    private val toolbarHeight = 36
    private val sceneBarHeight = 16
    var pendingFocusNodeId: String? = null

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
                if (loaded != null) {
                    StorySerializer.ensureAllScenesLoaded(loaded)
                    return loaded
                }
            }
        }
        return StoryProject.createNew()
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
        val prevSelectedIds = selectedWidgets.map { it.node.id }.toSet()
        nodeWidgets.clear()
        selectedWidgets.clear()

        if (editingConstructionNode != null) {
            editingConstructionNode?.innerNodes?.forEach { node ->
                val widget = NodeWidget(node)
                widget.isStartTestNode = (rangeTestStartNode?.id == node.id)
                widget.isEndTestNode = (rangeTestEndNode?.id == node.id)
                if (prevSelectedIds.contains(node.id)) {
                    widget.isSelected = true
                    selectedWidgets.add(widget)
                }
                nodeWidgets.add(widget)
            }
        } else {
            project.scenes.forEach { scene ->
                scene.nodes.forEach { node ->
                    val widget = NodeWidget(node)
                    widget.isStartTestNode = (rangeTestStartNode?.id == node.id)
                    widget.isEndTestNode = (rangeTestEndNode?.id == node.id)
                    if (prevSelectedIds.contains(node.id)) {
                        widget.isSelected = true
                        selectedWidgets.add(widget)
                    }
                    nodeWidgets.add(widget)
                }
            }
            project.globalNodes.forEach { node ->
                val widget = NodeWidget(node)
                widget.isStartTestNode = (rangeTestStartNode?.id == node.id)
                widget.isEndTestNode = (rangeTestEndNode?.id == node.id)
                if (prevSelectedIds.contains(node.id)) {
                    widget.isSelected = true
                    selectedWidgets.add(widget)
                }
                nodeWidgets.add(widget)
            }
        }
        if (selectedWidget != null && selectedWidgets.none { it.node.id == selectedWidget?.node?.id }) {
            selectedWidget = selectedWidgets.firstOrNull()
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
        showStatus("Focus centered on ${scene.title}")
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

        // 1. Menu [📁 File ▾]
        val fileLabel = "📁 File ▾"
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

        // 2. Menu [➕ Add ▾]
        val addLabel = "➕ Add ▾"
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

        // 3. Menu [⚙ System ▾]
        val sysLabel = "⚙ System ▾"
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
            val backLabel = "← Back to Studio"
            val backW = getBtnWidth(backLabel)
            addRenderableWidget(
                Button.builder(Component.literal(backLabel)) {
                    editingConstructionNode = null
                    init()
                    rebuildNodeWidgets()
                    activeInspector = null
                    activeSceneInspector = null
                    showStatus("Returned to main Studio.")
                }.bounds(currentX, row2Y, backW, btnH).build()
            )
        }

        // Botão [▶ Test ▾] e Controles de Teste (Canto Inferior Direito - Sempre Visíveis)
        val session = vito.cobblebrain.engine.StoryDebugger.activeSessionState
        val isStoryActive = session.isActive || StoryExecutor.activeStories.containsKey(project.id)
        val isStoryPaused = session.isPaused

        val testLabel = if (isStoryPaused) "⏸ Test (Paused) ▾" else if (isStoryActive) "⚡ Testing... ▾" else "▶ Test ▾"
        val testW = getBtnWidth(testLabel).coerceAtLeast(60)
        val testH = 16
        val testX = width - testW - 10
        val testY = height - testH - 8
        testBtnX = testX
        testBtnY = testY

        // 1. [▶ Test ▾] Menu Button
        addRenderableWidget(
            Button.builder(Component.literal(testLabel)) {
                if (!isInWorld) {
                    showWarning("⚠️ You must be loaded into a world to test stories!")
                    return@builder
                }
                val next = !isTestMenuOpen
                closeAllTopMenus()
                isTestMenuOpen = next
            }.bounds(testX, testY, testW, testH).build()
        )

        // 2. [⏸ Pause / ▶ Resume] Button (Sempre adicionado)
        val pauseLabel = if (isStoryPaused) "▶ Resume" else "⏸ Pause"
        val pauseW = getBtnWidth(pauseLabel).coerceAtLeast(54)
        val pauseX = testX - pauseW - 4
        addRenderableWidget(
            Button.builder(Component.literal(pauseLabel)) {
                if (!isInWorld) {
                    showWarning("⚠️ You must be loaded into a world to control story tests!")
                    return@builder
                }
                val isCurrentlyPaused = vito.cobblebrain.engine.StoryDebugger.activeSessionState.isPaused
                if (isCurrentlyPaused) {
                    vito.cobblebrain.client.StoryControlClient.resume(project.id)
                    showStatus("Test execution resumed.")
                } else {
                    vito.cobblebrain.client.StoryControlClient.pause(project.id)
                    showStatus("Test execution paused.")
                }
                init()
            }.bounds(pauseX, testY, pauseW, testH).build()
        )

        // 3. [⏹ Stop] Button (Sempre adicionado)
        val stopLabel = "⏹ Stop"
        val stopW = getBtnWidth(stopLabel).coerceAtLeast(46)
        val stopX = pauseX - stopW - 4
        addRenderableWidget(
            Button.builder(Component.literal(stopLabel)) {
                if (!isInWorld) {
                    showWarning("⚠️ You must be loaded into a world to control story tests!")
                    return@builder
                }
                vito.cobblebrain.client.StoryControlClient.stop(project.id)
                showStatus("Test execution stopped.")
                init()
            }.bounds(stopX, testY, stopW, testH).build()
        )

        // 4. [🎯 Focus #] Button (quando houver bloco ativo)
        if (session.activeNodeId.isNotBlank()) {
            val focusLabel = "🎯 Focus #"
            val focusW = getBtnWidth(focusLabel).coerceAtLeast(56)
            val focusX = stopX - focusW - 4
            addRenderableWidget(
                Button.builder(Component.literal(focusLabel)) {
                    focusOnNode(session.activeNodeId)
                    showStatus("Focused on active block #${session.activeNodeId.take(8)}")
                }.bounds(focusX, testY, focusW, testH).build()
            )
        }

        val pendingId = pendingFocusNodeId
        if (pendingId != null) {
            pendingFocusNodeId = null
            focusOnNode(pendingId)
        }
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

    private fun addNode(type: NodeType, presetSubtype: String? = null) {
        val title = if (presetSubtype == "TAG_BLOCK") {
            "Manage Story Tag"
        } else if (presetSubtype == "MOVE_TO_BLOCK") {
            "Move / Pathfind Entity"
        } else when (type) {
            NodeType.BEGIN_SCENE -> "Scene Start"
            NodeType.TRIGGER -> "Trigger"
            NodeType.ACTION -> "Action"
            NodeType.COMMAND_NODE -> "Execute Commands"
            NodeType.TIMER -> "Timer"
            NodeType.CONDITION_NODE -> "Condition"
            NodeType.DIALOGUE -> "Dialogue"
            NodeType.CONSTRUCTION -> "Construction"
            NodeType.BEGIN_CONSTRUCTION -> "Begin Construction"
            NodeType.END_CONSTRUCTION -> "End Construction"
            NodeType.LINK_SEND -> "Link Send"
            NodeType.LINK_RECEIVE -> "Link Receiver"
            NodeType.LOOP -> "Loop Repeater"
            NodeType.COMMENT -> "Note"
            NodeType.END_SCENE -> "Finish Scene"
            NodeType.GATE -> "GATE Synchronizer"
            NodeType.VARIABLE_GET -> "Var (Get)"
            NodeType.VARIABLE_SET -> "Var (Set)"
            NodeType.QUEST -> "Mission (Quest)"
            NodeType.AUDIO -> "Audio / Music"
            NodeType.SAVE_STATE_NODE -> "Save Checkpoint"
            NodeType.LOAD_STATE_NODE -> "Load Checkpoint"
            NodeType.TEXTURE -> "Change Texture"
            NodeType.KEY_INPUT -> "Key Input / QTE"
        }

        val inputs = mutableListOf<PortData>()
        val outputs = mutableListOf<PortData>()

        var w = 160.0

        var h = 55.0

        when (type) {
            NodeType.QUEST -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Success", type = PortType.OUTPUT, id = "SUCCESS_OUT"))
                outputs.add(PortData(name = "Fail", type = PortType.OUTPUT, id = "FAIL_OUT"))
                outputs.add(PortData(name = "Progress", type = PortType.OUTPUT, id = "PROGRESS_OUT"))
                w = 130.0; h = 110.0
            }
            NodeType.AUDIO -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 130.0; h = 100.0
            }
            NodeType.TEXTURE -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 150.0; h = 80.0
            }
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
            NodeType.BEGIN_CONSTRUCTION -> {
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 160.0; h = 55.0
            }
            NodeType.END_CONSTRUCTION -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                w = 160.0; h = 55.0
            }
            NodeType.TRIGGER -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 130.0; h = 100.0
            }
            NodeType.CONDITION_NODE -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(id = "OUT_IF", name = "IF", type = PortType.OUTPUT))
                outputs.add(PortData(id = "OUT_ELSE", name = "ELSE", type = PortType.OUTPUT))
                w = 140.0; h = 100.0
            }
            NodeType.COMMAND_NODE -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 140.0; h = 100.0
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
            NodeType.SAVE_STATE_NODE -> {
                inputs.add(PortData(id = "IN", name = "In", type = PortType.INPUT))
                outputs.add(PortData(id = "OUT_SUCCESS", name = "Success", type = PortType.OUTPUT))
                outputs.add(PortData(id = "OUT_ERROR", name = "Error", type = PortType.OUTPUT))
                w = 140.0; h = 100.0
            }
            NodeType.LOAD_STATE_NODE -> {
                inputs.add(PortData(id = "IN", name = "In", type = PortType.INPUT))
                outputs.add(PortData(id = "OUT_SUCCESS", name = "Success", type = PortType.OUTPUT))
                outputs.add(PortData(id = "OUT_NOT_FOUND", name = "Not Found", type = PortType.OUTPUT))
                outputs.add(PortData(id = "OUT_ERROR", name = "Error", type = PortType.OUTPUT))
                w = 140.0; h = 110.0
            }
            NodeType.KEY_INPUT -> {
                // Standalone mode is default (no In port)
                outputs.add(PortData(id = "OUT", name = "Out", type = PortType.OUTPUT))
                w = 150.0; h = 100.0
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
            content = if (type == NodeType.DIALOGUE) "Hello Trainer!" else if (type == NodeType.COMMAND_NODE) "say Hello {player}!" else "",
            x = currentMouseWorldX - w / 2.0,
            y = currentMouseWorldY - h / 2.0,
            width = w,
            height = h,
            inputs = inputs,
            outputs = outputs,
            params = mutableMapOf()
        )

        if (type == NodeType.QUEST) {
            ghostNode.params["questTitle"] = "New Quest"
            ghostNode.params["questTrigger"] = "POKEMON_CATCH"
            ghostNode.params["targetCount"] = "1"
            ghostNode.params["timeLimitSec"] = "0"
            ghostNode.params["failOnDeath"] = "false"
            ghostNode.params["showHud"] = "true"
        } else if (type == NodeType.AUDIO) {
            ghostNode.params["audioMode"] = "PLAY_SOUND_EFFECT"
            ghostNode.params["audioId"] = "minecraft:entity.player.levelup"
            ghostNode.params["audioVolume"] = "1.0"
            ghostNode.params["audioPitch"] = "1.0"
            ghostNode.params["audioLoop"] = "false"
            ghostNode.params["spatialMode"] = "GLOBAL_2D"
        } else if (type == NodeType.VARIABLE_GET || type == NodeType.VARIABLE_SET) {
            val firstVarKey = project.variables.firstOrNull()?.id ?: "var_new"
            ghostNode.params["varKey"] = firstVarKey
            ghostNode.params["varOp"] = "="
            ghostNode.params["varValue"] = "1"
            if (type == NodeType.VARIABLE_GET) ghostNode.title = "Get: $firstVarKey"
            if (type == NodeType.VARIABLE_SET) ghostNode.title = "Set: $firstVarKey"
        } else if (type == NodeType.CONDITION_NODE) {
            val firstVarKey = project.variables.firstOrNull()?.id ?: "var_new"
            ghostNode.params["varKey"] = firstVarKey
            ghostNode.params["varKey_0"] = firstVarKey
            ghostNode.params["varOp"] = "=="
            ghostNode.params["varOp_0"] = "=="
            ghostNode.params["varValue"] = "true"
            ghostNode.params["varValue_0"] = "true"
            ghostNode.params["elseIfCount"] = "0"
            ghostNode.params["hasElse"] = "true"
        } else if (type == NodeType.COMMAND_NODE) {
            ghostNode.params["commandSource"] = "SERVER"
            ghostNode.params["silent"] = "true"
            ghostNode.params["commands"] = "say Hello {player}!"
        } else if (type == NodeType.TRIGGER) {
            ghostNode.params["requireInputSignal"] = "true"
        } else if (type == NodeType.LINK_SEND || type == NodeType.LINK_RECEIVE) {
            ghostNode.params["channelTag"] = "channel_1"
        } else if (type == NodeType.LOOP) {
            ghostNode.params["loopMode"] = "COUNT"
            ghostNode.params["loopCount"] = "5"
            ghostNode.params["loopIntervalSec"] = "1.0"
        } else if (type == NodeType.CONSTRUCTION) {
            if (ghostNode.innerNodes.isEmpty()) {
                val startConstr = NodeData(
                    title = "Begin Construction",
                    nodeType = NodeType.BEGIN_CONSTRUCTION,
                    x = 40.0,
                    y = 60.0,
                    width = 160.0,
                    height = 55.0,
                    inputs = mutableListOf(),
                    outputs = mutableListOf(PortData(name = "Out", type = PortType.OUTPUT)),
                    params = mutableMapOf(
                        "constructionName" to "New Construction",
                        "buildSpeedMode" to "INSTANT",
                        "tickDelayBetweenSteps" to "5",
                        "timeoutTicks" to "600"
                    )
                )
                val finishConstr = NodeData(
                    title = "End Construction",
                    nodeType = NodeType.END_CONSTRUCTION,
                    x = 280.0,
                    y = 60.0,
                    width = 160.0,
                    height = 55.0,
                    inputs = mutableListOf(PortData(name = "In", type = PortType.INPUT)),
                    outputs = mutableListOf(),
                    params = mutableMapOf(
                        "finalizeTags" to "true",
                        "playCompletionSound" to "true",
                        "completionSoundId" to "minecraft:block.anvil.use"
                    )
                )
                ghostNode.innerNodes.add(startConstr)
                ghostNode.innerNodes.add(finishConstr)
            }
        } else if (type == NodeType.BEGIN_CONSTRUCTION) {
            ghostNode.params["constructionName"] = "New Construction"
            ghostNode.params["buildSpeedMode"] = "INSTANT"
            ghostNode.params["tickDelayBetweenSteps"] = "5"
            ghostNode.params["timeoutTicks"] = "600"
        } else if (type == NodeType.END_CONSTRUCTION) {
            ghostNode.params["finalizeTags"] = "true"
            ghostNode.params["playCompletionSound"] = "true"
            ghostNode.params["completionSoundId"] = "minecraft:block.anvil.use"
        }

        if (presetSubtype == "TAG_BLOCK" || (type == NodeType.ACTION && presetSubtype == "TAG_BLOCK")) {
            ghostNode.title = "Manage Story Tag"
            ghostNode.params["actionSubtype"] = "TAG_BLOCK"
            ghostNode.params["actionId"] = "TAG_BLOCK"
            ActionRegistry.getAction("TAG_BLOCK")?.defaultParams?.forEach { (k, v) ->
                ghostNode.params[k] = v
            }
        }

        if (presetSubtype == "MOVE_TO_BLOCK" || (type == NodeType.ACTION && presetSubtype == "MOVE_TO_BLOCK")) {
            ghostNode.title = "Move / Pathfind Entity"
            ghostNode.params["actionSubtype"] = "MOVE_TO_BLOCK"
            ghostNode.params["actionId"] = "MOVE_TO_BLOCK"
            ActionRegistry.getAction("MOVE_TO_BLOCK")?.defaultParams?.forEach { (k, v) ->
                ghostNode.params[k] = v
            }
        }

        if (type == NodeType.SAVE_STATE_NODE) {
            ghostNode.params["profileId"] = "checkpoint_1"
            ghostNode.params["scope"] = "PLAYER"
            ghostNode.params["modules"] = "ALL"
        } else if (type == NodeType.LOAD_STATE_NODE) {
            ghostNode.params["profileId"] = "checkpoint_1"
            ghostNode.params["scope"] = "PLAYER"
            ghostNode.params["mergeMode"] = "OVERWRITE"
            ghostNode.params["jumpToTargetNodeId"] = ""
            ghostNode.params["gracePeriodTicks"] = "60"
            ghostNode.params["cleanStoryTag"] = ""
        } else if (type == NodeType.KEY_INPUT) {
            ghostNode.params["triggerMode"] = "STANDALONE"
            ghostNode.params["requireInputSignal"] = "false"
            ghostNode.params["inputMode"] = "PRESS"
            ghostNode.params["targetKey"] = "F"
            ghostNode.params["holdDurationSec"] = "2.0"
            ghostNode.params["pulseIntervalTicks"] = "10"
            ghostNode.params["mashTargetCount"] = "10"
            ghostNode.params["mashDecayPerSec"] = "2.0"
            ghostNode.params["timeoutSec"] = "0.0"
            ghostNode.params["promptText"] = ""
            ghostNode.params["showHud"] = "false"
            ghostNode.params["cancelOnMenuOpen"] = "true"
        }

        activePlacementNode = ghostNode
        isBlockPaletteOpen = false
        closeAllTopMenus()
        isTestMenuOpen = false
        markDirty()
        showStatus("Ghost Mode: Click to place block $title (ESC to cancel).")
    }

    private fun createNewSceneFrame() {
        val lastScene = project.scenes.lastOrNull()
        val newX = if (lastScene != null) lastScene.x + lastScene.width + 100.0 else screenToWorldX(width / 2.0) - 250.0
        val newY = lastScene?.y ?: (screenToWorldY(height / 2.0) - 175.0)

        val newScene = SceneData.createWithStartNode(
            title = "Scene ${project.scenes.size + 1}",
            description = "Scene Description",
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
        showStatus("New Scene created in Studio: ${newScene.title}")
        isBlockPaletteOpen = false
        closeAllTopMenus()
        isTestMenuOpen = false
    }

    private fun openInspectorForNode(node: NodeData) {
        val inspectorW = 140
        val inspectorX = width - inspectorW
        val inspectorY = 0
        val inspectorH = height

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
                    conns.removeAll { conn ->
                        (conn.fromNodeId == w.node.id && w.node.outputs.none { it.id == conn.fromPortId }) ||
                        (conn.toNodeId == w.node.id && w.node.inputs.none { it.id == conn.toPortId })
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
                showStatus("Construction sub-canvas opened: ${constrNode.title}")
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
            onOpenResourcePicker = { pickerType, onSelect ->
                if (pickerType == ResourcePickerType.ITEM) {
                    activeItemPickerModal = ItemPickerModalWidget(
                        font = font,
                        screenWidth = width,
                        screenHeight = height,
                        selectedItemId = "",
                        onSelect = { chosenId ->
                            onSelect(chosenId)
                        },
                        onClose = { activeItemPickerModal = null }
                    )
                } else {
                    activeResourcePickerModal = ResourcePickerModalWidget(
                        pickerType = pickerType,
                        font = font,
                        screenWidth = width,
                        screenHeight = height,
                        currentSelectedId = "",
                        onSelect = { chosenId ->
                            onSelect(chosenId)
                        },
                        onClose = { activeResourcePickerModal = null }
                    )
                }
            },
            onOpenEntityConfig = { targetNode ->
                activeEntityConfigModal = EntityConfigModalWidget(
                    node = targetNode,
                    font = font,
                    screenWidth = width,
                    screenHeight = height,
                    onOpenItemPicker = { currentVal, onSelect ->
                        activeItemPickerModal = ItemPickerModalWidget(
                            font = font,
                            screenWidth = width,
                            screenHeight = height,
                            selectedItemId = currentVal,
                            onSelect = { chosen ->
                                onSelect(chosen)
                                activeItemPickerModal = null
                            },
                            onClose = { activeItemPickerModal = null }
                        )
                    },
                    onClose = { activeEntityConfigModal = null },
                    onDataChanged = {
                        markDirty()
                        activeInspector?.buildUi()
                    }
                )
            },
            onDeleteNode = { target -> deleteNode(target) },
            onDissociateNode = { target -> dissociateNode(target) },
            onOpenProfileModal = { target -> openSaveProfileModalForNode(target) },
            onOpenAIDialogueModal = { target -> openAIDialogueModalForNode(target) },
            onOpenAnimationSelector = { target, onSelect -> openAnimationSelectorModalForNode(target, onSelect) },
            onOpenTextureSelector = { target, onSelect -> openTextureSelectorModalForNode(target, onSelect) },
            onOpenCoordinateModal = { target, onSaved ->
                closeAllTopMenus()
                activeCoordinateModal = CoordinateConfigModalWidget(
                    node = target,
                    font = font,
                    screenWidth = width,
                    screenHeight = height,
                    onClose = { activeCoordinateModal = null },
                    onDataChanged = {
                        onSaved()
                        markDirty()
                        activeInspector?.buildUi()
                    }
                )
            },
            projectVariables = project.variables
        )
    }

    fun openTextureSelectorModalForNode(node: NodeData, onSelected: (String) -> Unit) {
        closeAllTopMenus()
        activeTextureSelectorModal = TextureSelectorModalWidget(
            font = font,
            screenWidth = width,
            screenHeight = height,
            storyId = project.id.ifBlank { "default_story" },
            initialSelected = node.params["textureName"] ?: "",
            onSelect = { chosen ->
                onSelected(chosen)
            },
            onClose = { activeTextureSelectorModal = null }
        )
    }

    fun openDebugConsole() {
        if (!isInWorld) {
            showWarning("⚠️ You must be loaded into a world to use debug mode!")
            return
        }
        closeAllTopMenus()
        minecraft?.setScreen(StoryRuntimeDebugScreen(parentScreen = this, initialStoryId = project.id))
    }

    fun focusOnNode(blockId: String) {
        if (width <= 0 || height <= 0 || nodeWidgets.isEmpty()) {
            pendingFocusNodeId = blockId
            return
        }

        val targetNode = (project.scenes.flatMap { it.nodes } + project.globalNodes).find { it.id == blockId }
            ?: editingConstructionNode?.innerNodes?.find { it.id == blockId }
        if (targetNode != null) {
            val parentScene = project.scenes.find { scene -> scene.nodes.any { it.id == blockId } }
            if (parentScene != null && project.activeSceneId != parentScene.id) {
                project.activeSceneId = parentScene.id
                editingConstructionNode = null
                rebuildNodeWidgets()
            }

            panX = (width / 2.0) - (targetNode.x + targetNode.width / 2.0) * zoom
            panY = (height / 2.0) - (targetNode.y + targetNode.height / 2.0) * zoom

            selectedWidget = nodeWidgets.find { it.node.id == targetNode.id }
            nodeWidgets.forEach { it.isSelected = (it.node.id == targetNode.id) }
            openInspectorForNode(targetNode)
        }
    }

    fun openAnimationSelectorModalForNode(node: NodeData, onSelected: (String) -> Unit) {
        closeAllTopMenus()
        activeAnimationSelectorModal = AnimationSelectorModalWidget(
            font = font,
            screenWidth = width,
            screenHeight = height,
            selectedSystem = node.params["animationSystem"] ?: "COBBLEMON",
            initialSelected = node.params["animationId"] ?: "",
            onSelect = { chosenId ->
                onSelected(chosenId)
            },
            onClose = { activeAnimationSelectorModal = null }
        )
    }

    fun openSaveProfileModalForNode(node: NodeData) {
        closeAllTopMenus()
        activeSaveProfileModal = SaveProfileModalWidget(
            node = node,
            project = project,
            font = font,
            screenWidth = width,
            screenHeight = height,
            onClose = { activeSaveProfileModal = null },
            onDataChanged = {
                markDirty()
                activeInspector?.buildUi()
            }
        )
    }

    fun openAIDialogueModalForNode(node: NodeData) {
        closeAllTopMenus()
        activeAIDialogueModal = AIDialogueModalWidget(
            node = node,
            project = project,
            font = font,
            screenWidth = width,
            screenHeight = height,
            onClose = { activeAIDialogueModal = null },
            onDataChanged = {
                markDirty()
                activeInspector?.buildUi()
            }
        )
    }

    fun deleteNode(node: NodeData) {
        // 1. Remove from all scene children, global nodes, and construction sub-graphs
        project.scenes.forEach { scene ->
            scene.nodes.removeIf { it.id == node.id }
        }
        project.globalNodes.removeIf { it.id == node.id }
        editingConstructionNode?.innerNodes?.removeIf { it.id == node.id }
        openConstructionNodes.removeIf { it.id == node.id }

        // 2. Sever and remove all wire connections attached to this node across all scenes & project
        project.scenes.forEach { scene ->
            scene.connections.removeIf { it.fromNodeId == node.id || it.toNodeId == node.id }
        }
        editingConstructionNode?.innerConnections?.removeIf { it.fromNodeId == node.id || it.toNodeId == node.id }
        project.sceneConnections.removeIf { it.fromNodeId == node.id || it.toNodeId == node.id }

        // 3. Clear selection & close inspector if targeted
        selectedWidgets.removeIf { it.node.id == node.id }
        if (selectedWidget?.node?.id == node.id) {
            selectedWidget = selectedWidgets.firstOrNull()
        }
        if (activeInspector?.node?.id == node.id) {
            activeInspector = null
        }

        markDirty()
        rebuildNodeWidgets()
        showStatus("Node deleted: ${node.title}")
    }

    fun deleteNodes(nodes: List<NodeData>) {
        if (nodes.isEmpty()) return
        val nodeIds = nodes.map { it.id }.toSet()

        // 1. Remove from all scenes, global nodes, and construction sub-graphs
        project.scenes.forEach { scene ->
            scene.nodes.removeIf { nodeIds.contains(it.id) }
            scene.connections.removeIf { nodeIds.contains(it.fromNodeId) || nodeIds.contains(it.toNodeId) }
        }
        project.globalNodes.removeIf { nodeIds.contains(it.id) }
        editingConstructionNode?.innerNodes?.removeIf { nodeIds.contains(it.id) }
        editingConstructionNode?.innerConnections?.removeIf { nodeIds.contains(it.fromNodeId) || nodeIds.contains(it.toNodeId) }
        openConstructionNodes.removeIf { nodeIds.contains(it.id) }
        project.sceneConnections.removeIf { nodeIds.contains(it.fromNodeId) || nodeIds.contains(it.toNodeId) }

        // 2. Clear selection & close inspector
        selectedWidgets.removeIf { nodeIds.contains(it.node.id) }
        if (selectedWidget != null && nodeIds.contains(selectedWidget?.node?.id)) {
            selectedWidget = null
        }
        if (activeInspector != null && nodeIds.contains(activeInspector?.node?.id)) {
            activeInspector = null
        }

        markDirty()
        rebuildNodeWidgets()
        showStatus("${nodes.size} nodes deleted.")
    }

    fun dissociateNode(node: NodeData) {
        // 1. Duplicate/clone node with all parameters & configuration
        val inputs = node.inputs.map { PortData(name = it.name, type = it.type) }.toMutableList()
        val outputs = node.outputs.map { PortData(name = it.name, type = it.type) }.toMutableList()
        val cloneNode = NodeData(
            parentSceneId = null,
            title = node.title,
            nodeType = node.nodeType,
            content = node.content,
            x = currentMouseWorldX - node.width / 2.0,
            y = currentMouseWorldY - node.height / 2.0,
            width = node.width,
            height = node.height,
            preDelayTicks = node.preDelayTicks,
            postDelayTicks = node.postDelayTicks,
            inputs = inputs,
            outputs = outputs,
            params = HashMap(node.params),
            innerNodes = node.innerNodes.toMutableList(),
            innerConnections = node.innerConnections.toMutableList()
        )

        // 2. Remove original node and sever its connections
        deleteNode(node)

        // 3. Place cloned node in Ghost Mode
        activePlacementNode = cloneNode
        selectedWidget = null
        selectedWidgets.clear()
        activeInspector = null
        activeContextMenu = null
        showStatus("Node detached into Ghost Placement mode. Click canvas to place.")
    }

    private fun openSceneInspector(scene: SceneData) {
        val inspectorW = 140
        val inspectorX = width - inspectorW
        val inspectorY = 0
        val inspectorH = height

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
            showStatus("Saved to: ${file.name}")
        } else {
            showStatus("Error saving project!")
        }
    }

    private fun exportToZip() {
        // Save current canvas state to storypack folder first
        saveProject()

        showStatus("Opening Save Dialog...")
        StoryZipExporter.exportProjectToZipAsync(
            project = project,
            onSuccess = { zipFile ->
                showStatus("Exported successfully: ${zipFile.name}")
            },
            onError = { errorMsg ->
                showStatus("Export failed: $errorMsg")
            },
            onCancel = {
                showStatus("Export cancelled.")
            }
        )
    }

    private fun showStatus(msg: String) {
        statusMessage = msg
        statusTimer = 100
        isStatusWarning = false
    }

    private fun showWarning(msg: String) {
        statusMessage = msg
        statusTimer = 160
        isStatusWarning = true
    }

    private fun startRangeTestExecution() {
        if (!isInWorld) {
            showWarning("⚠️ You must be loaded into a world to run range tests!")
            isRangeTestSelectionMode = false
            rangeTestStartNode = null
            rangeTestEndNode = null
            rebuildNodeWidgets()
            return
        }
        val startNode = rangeTestStartNode
        if (startNode == null) return

        val serverPlayer = minecraft?.player?.uuid?.let { minecraft?.singleplayerServer?.playerList?.getPlayer(it) }
        val valRes = StoryExecutor.validatePrerequisites(project, serverPlayer, serverPlayer?.server)
        if (!valRes.isValid) {
            StoryExecutor.handlePrerequisiteFailure(project, serverPlayer, valRes)
            showStatus("Prerequisites failed: ${valRes.reason}")
            return
        }

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

    // AABB test to hide text ONLY on nodes colliding with overlapping menus or inspectors
    private fun isNodeCollidingWithOverlays(widget: NodeWidget): Boolean {
        val nodeX = worldToScreenX(widget.node.x)
        val nodeY = worldToScreenY(widget.node.y)
        val nodeW = widget.node.width * zoom
        val nodeH = widget.node.height * zoom

        val overlayRects = mutableListOf<ScreenRect>()

        if (activeInspector != null || activeSceneInspector != null) {
            val inspW = 140.0
            val topOffset = 0.0
            overlayRects.add(ScreenRect(width - inspW, topOffset, inspW, height.toDouble()))
        }

        val dropY = (toolbarHeight + sceneBarHeight + 2).toDouble()
        if (isFileMenuOpen) overlayRects.add(ScreenRect(fileMenuX.toDouble(), dropY, 140.0, 76.0))
        if (isAddMenuOpen) overlayRects.add(ScreenRect(addMenuX.toDouble(), dropY, 160.0, 94.0))
        if (isSystemMenuOpen) overlayRects.add(ScreenRect(systemMenuX.toDouble(), dropY, 155.0, 40.0))
        if (isBlockPaletteOpen) overlayRects.add(ScreenRect(10.0, dropY, 185.0, 184.0))

        if (isTestMenuOpen) {
            val dropdownH = 44.0
            val testW = 195.0
            val testX = (testBtnX + getBtnWidth("▶ Test ▾") - testW).coerceAtMost(width - testW - 5.0).coerceAtLeast(5.0)
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

        val isModalOpen = activeDocModal != null || showExitConfirmModal || activeVariableModal != null || activeMetadataModal != null || activeVarSelectorModal != null || activeActionTriggerPickerModal != null || activePokemonConfigModal != null || activeResourcePickerModal != null || activeItemPickerModal != null || activeEntityConfigModal != null || activeSaveProfileModal != null || activeAIDialogueModal != null || activeAnimationSelectorModal != null || activeTextureSelectorModal != null || activeCoordinateModal != null

        // 1. Canvas Background and Grid
        guiGraphics.fill(0, 0, width, height, 0xFF141418.toInt())
        renderGrid(guiGraphics)

        // 2. SCISSOR CLIPPING + CANVAS RENDER
        val topOffset = toolbarHeight + sceneBarHeight
        val cullingMargin = 80.0
        viewLeft = screenToWorldX(0.0) - cullingMargin
        viewRight = screenToWorldX(width.toDouble()) + cullingMargin
        viewTop = screenToWorldY(topOffset.toDouble()) - cullingMargin
        viewBottom = screenToWorldY(height.toDouble()) + cullingMargin

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

        // Render canvas nodes (with viewport culling + selective AABB collision check for visible nodes)
        for (i in 0 until nodeWidgets.size) {
            val widget = nodeWidgets[i]
            val node = widget.node
            val nw = if (node.width > 0.0) node.width else 160.0
            val nh = if (node.height > 0.0) node.height else 100.0
            if (node.x + nw < viewLeft || node.x > viewRight ||
                node.y + nh < viewTop || node.y > viewBottom) {
                continue // Viewport Culled!
            }
            val isColliding = isNodeCollidingWithOverlays(widget)
            widget.render(guiGraphics, font, if (isModalOpen || isColliding) null else hoveredPort, isModalOpen, isColliding, storyId = project.id)
        }
        renderGhostPlacementNode(guiGraphics)

        // Render Selection Box (Marquee)
        if (isBoxSelecting) {
            val bx1 = minOf(selectionOriginWorldX, selectionCurrentWorldX).toInt()
            val by1 = minOf(selectionOriginWorldY, selectionCurrentWorldY).toInt()
            val bx2 = maxOf(selectionOriginWorldX, selectionCurrentWorldX).toInt()
            val by2 = maxOf(selectionOriginWorldY, selectionCurrentWorldY).toInt()

            if (bx2 > bx1 && by2 > by1) {
                // Semi-transparent fill
                guiGraphics.fill(bx1, by1, bx2, by2, 0x334499FF)
                // 1px solid border
                guiGraphics.fill(bx1, by1, bx2, by1 + 1, 0xFF3399FF.toInt())
                guiGraphics.fill(bx1, by2 - 1, bx2, by2, 0xFF3399FF.toInt())
                guiGraphics.fill(bx1, by1, bx1 + 1, by2, 0xFF3399FF.toInt())
                guiGraphics.fill(bx2 - 1, by1, bx2, by2, 0xFF3399FF.toInt())
            }
        }

        guiGraphics.pose().popPose()
        guiGraphics.disableScissor()

        // 3. Toast Notifications
        if (statusTimer > 0) {
            statusTimer--
            val toastY = height - 16
            val accentCol = if (isStatusWarning) 0xFFFF4444.toInt() else 0xFFFFD700.toInt()
            val textCol = if (isStatusWarning) 0xFFFF8888.toInt() else 0xFFFFD700.toInt()
            val bgCol = if (isStatusWarning) 0xEE2A1010.toInt() else 0xDD181820.toInt()
            guiGraphics.fill(6, toastY - 3, font.width(statusMessage) + 16, toastY + 11, bgCol)
            guiGraphics.fill(6, toastY - 3, 8, toastY + 11, accentCol)
            guiGraphics.drawString(font, statusMessage, 12, toastY, textCol, false)
        }

        // 4. Native Screen Components
        val mX = if (isModalOpen) -9999 else mouseX
        val mY = if (isModalOpen) -9999 else mouseY
        super.render(guiGraphics, mX, mY, partialTick)

        // 5. Top Bar and Tabs
        renderTopBarAndMenus(guiGraphics, mX, mY)

        // 6. RENDER CATEGORIZED MENUS, BLOCK PALETTE AND INSPECTORS WITH MATRIX ELEVATION Z = 500f
        RenderSystem.enableDepthTest()
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(0.0f, 0.0f, 500.0f)

        activeInspector?.render(guiGraphics, mX, mY, partialTick)
        activeSceneInspector?.render(guiGraphics, mX, mY, partialTick)

        renderCategoryMenusDropdowns(guiGraphics, mX, mY)
        if (isBlockPaletteOpen) renderBlockPalette(guiGraphics, mX, mY)
        if (isTestMenuOpen) renderTestMenu(guiGraphics, mX, mY)
        activeContextMenu?.render(guiGraphics, mX, mY, width, height)

        // Diagnostic Node Error Hover Tooltips
        if (!isModalOpen && activeInspector == null && !isFileMenuOpen && !isAddMenuOpen && !isSystemMenuOpen) {
            for (widget in nodeWidgets) {
                val errTip = widget.getErrorTooltipAt(currentMouseWorldX, currentMouseWorldY, project.id)
                if (errTip != null) {
                    guiGraphics.renderTooltip(font, Component.literal(errTip), mouseX, mouseY)
                    break
                }
            }
        }

        guiGraphics.flush()
        guiGraphics.pose().popPose()

        // 7. RENDER MODALS WITH DEPTH TEST & MATRIX ELEVATION Z = 1000f
        if (isModalOpen) {
            renderModalOverlay(guiGraphics, mouseX, mouseY, partialTick)
            guiGraphics.flush()
        }

        // Asynchronous Background Auto-Save
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
                    showStatus("Auto-saved in background.")
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
            "${project.name}$dirtyIndicator · Studio > 🏗️ ${construction.title} (${nodeWidgets.size} nodes)"
        } else {
            "${project.name}$dirtyIndicator · Studio (${project.scenes.size} Scenes)"
        }
        val titleW = font.width(titleText)
        val centerX = (width - titleW) / 2
        guiGraphics.drawString(font, titleText, centerX, 4, 0xFF00FFCC.toInt(), false)

        // Debug Console Toolbar Toggle Button (Far Top-Right Corner) - Hidden/Disabled when Inspector is open
        if (activeInspector == null && activeSceneInspector == null) {
            val errCount = vito.cobblebrain.engine.StoryDebugger.getErrorCount(project.id)
            val debugBtnLabel = if (errCount > 0) "🐞 Debug ($errCount)" else "🐞 Debug"
            val debugBtnW = font.width(debugBtnLabel) + 12
            val debugBtnH = 14
            val debugBtnX = width - debugBtnW - 8
            val debugBtnY = 2
            val isDebugHover = mouseX >= debugBtnX && mouseX <= debugBtnX + debugBtnW && mouseY >= debugBtnY && mouseY <= debugBtnY + debugBtnH
            val debugBg = when {
                !isInWorld -> if (isDebugHover) 0xFF2A2228.toInt() else 0xFF1C1920.toInt()
                errCount > 0 -> if (isDebugHover) 0xFFDC2626.toInt() else 0xFF991B1B.toInt()
                isDebugHover -> 0xFF334155.toInt()
                else -> 0xFF1E293B.toInt()
            }
            guiGraphics.fill(debugBtnX, debugBtnY, debugBtnX + debugBtnW, debugBtnY + debugBtnH, debugBg)
            val debugTextCol = when {
                !isInWorld -> if (isDebugHover) 0xFFA1A1AA.toInt() else 0xFF71717A.toInt()
                errCount > 0 -> 0xFFFCA5A5.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            guiGraphics.drawString(font, debugBtnLabel, debugBtnX + 6, debugBtnY + 3, debugTextCol, false)
        }

        if (isRangeTestSelectionMode) {
            val modeMsg = if (rangeTestStartNode == null) {
                "🎯 SELECTION MODE: Click 1st Block (TEST START) - Free drag/pan"
            } else {
                "🎯 SELECTION MODE: Click 2nd Block (TEST END) - Free drag/pan"
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

        // Render ONLY the active top-level modal so underlying screens/modals are 100% invisible
        when {
            activeItemPickerModal != null -> activeItemPickerModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            activeResourcePickerModal != null -> activeResourcePickerModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            activePokemonConfigModal != null -> activePokemonConfigModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            activeEntityConfigModal != null -> activeEntityConfigModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            activeAnimationSelectorModal != null -> activeAnimationSelectorModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            activeTextureSelectorModal != null -> activeTextureSelectorModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            activeSaveProfileModal != null -> activeSaveProfileModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            activeAIDialogueModal != null -> activeAIDialogueModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            activeCoordinateModal != null -> activeCoordinateModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            activeActionTriggerPickerModal != null -> activeActionTriggerPickerModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            activeVarSelectorModal != null -> activeVarSelectorModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            activeMetadataModal != null -> activeMetadataModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            activeVariableModal != null -> activeVariableModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            activeDocModal != null -> activeDocModal?.render(guiGraphics, mouseX, mouseY, partialTick)
            showExitConfirmModal -> renderExitConfirmModal(guiGraphics, mouseX, mouseY)
        }

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

            val items = listOf("💾 Save", "📂 Load", "📦 Export ZIP", "📋 Metadata")
            items.forEachIndexed { idx, label ->
                val iy = dropY + 2 + idx * itemH
                val isHovered = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= iy && mouseY < iy + itemH
                guiGraphics.fill(dropX + 3, iy, dropX + dropW - 3, iy + itemH - 2, if (isHovered) 0xFF3D5AFE.toInt() else 0xFF222228.toInt())
                guiGraphics.drawString(font, label, dropX + 8, iy + 4, 0xFFFFFFFF.toInt(), false)
            }
        }

        if (isAddMenuOpen) {
            val dropW = 175
            val dropX = addMenuX
            val items = listOf("🎬 New Scene", "💬 Dialogue Block", "🚶 Move / Pathfind Entity", "🏷️ Manage Story Tag", "📋 Manage Variables", "🔹 Variable Block (Get)", "✏️ Modifier Block (Set)", "+ Blocks")
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

            val items = listOf("⚙ Settings", "❓ Guide / Documentation")
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
            val sbW = 5
            val sbX = palX + palW - sbW - 1
            val scrollRatio = maxVisibleItems.toFloat() / paletteEntries.size
            val thumbH = (visibleHeight * scrollRatio).toInt().coerceAtLeast(14)
            val maxScrollable = paletteEntries.size - maxVisibleItems
            val thumbY = palY + 2 + (paletteScrollOffset.toFloat() / maxScrollable * (visibleHeight - 4 - thumbH)).toInt()

            val isHover = mouseX >= sbX - 2 && mouseX <= sbX + sbW + 2 && mouseY >= palY + 2 && mouseY <= palY + visibleHeight - 2
            val thumbCol = if (isDraggingPaletteScrollbar) 0xFF38BDF8.toInt() else if (isHover) 0xFF00E5FF.toInt() else 0xFF00FFCC.toInt()

            guiGraphics.fill(sbX, palY + 2, sbX + sbW, palY + visibleHeight - 2, 0x552A2A36)
            guiGraphics.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, thumbCol)
        }
    }

    private fun renderTestMenu(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val session = vito.cobblebrain.engine.StoryDebugger.activeSessionState
        val isStoryActive = session.isActive || vito.cobblebrain.engine.StoryExecutor.activeStories.containsKey(project.id)
        val isStoryPaused = session.isPaused

        val menuItems = mutableListOf<Pair<String, Int>>()
        menuItems.add(Pair("▶ Live Test on Canvas", 0xFF00FFCC.toInt()))
        menuItems.add(Pair("▶ Test in-Game (Close)", 0xFFFFFFFF.toInt()))
        menuItems.add(Pair("🎯 Test Selection Range", 0xFFFFFFFF.toInt()))
        if (isStoryActive) {
            if (isStoryPaused) {
                menuItems.add(Pair("▶ Resume Test Execution", 0xFF38BDF8.toInt()))
            } else {
                menuItems.add(Pair("⏸ Pause Test Execution", 0xFFF59E0B.toInt()))
            }
            menuItems.add(Pair("⏹ Stop Test Execution", 0xFFEF4444.toInt()))
        }
        menuItems.add(Pair("🐞 Open Story Debugger", 0xFFA855F7.toInt()))

        val testW = 210
        val itemH = 20
        val dropdownH = itemH * menuItems.size + 4

        val testBtnW = getBtnWidth(if (isStoryPaused) "⏸ Test (Paused) ▾" else if (isStoryActive) "⚡ Testing... ▾" else "▶ Test ▾")
        val testX = (testBtnX + testBtnW - testW).coerceAtMost(width - testW - 5).coerceAtLeast(5)
        val testY = testBtnY - dropdownH - 4

        guiGraphics.fill(testX, testY, testX + testW, testY + dropdownH, 0xF018181C.toInt())
        val borderColor = if (isStoryActive) 0xFF38BDF8.toInt() else 0xFF4CAF50.toInt()
        guiGraphics.fill(testX, testY, testX + 1, testY + dropdownH, borderColor)
        guiGraphics.fill(testX + testW - 1, testY, testX + testW, testY + dropdownH, borderColor)
        guiGraphics.fill(testX, testY, testX + testW, testY + 1, borderColor)
        guiGraphics.fill(testX, testY + dropdownH - 1, testX + testW, testY + dropdownH, borderColor)

        menuItems.forEachIndexed { idx, (label, color) ->
            val iy = testY + 2 + idx * itemH
            val isHover = mouseX >= testX && mouseX <= testX + testW && mouseY >= iy && mouseY < iy + itemH
            val bg = if (isHover) 0xFF334155.toInt() else 0xFF222228.toInt()
            guiGraphics.fill(testX + 3, iy, testX + testW - 3, iy + itemH - 2, bg)
            guiGraphics.drawString(font, label, testX + 8, iy + 5, if (isHover) 0xFFFFFFFF.toInt() else color, false)
        }
    }

    private fun renderExitConfirmModal(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val modalW = 280
        val modalH = 100
        val modalX = (width - modalW) / 2
        val modalY = (height - modalH) / 2

        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF1C1C24.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalW, modalY + 20, 0xFFD32F2F.toInt())
        guiGraphics.drawString(font, "⚠️ Unsaved Changes", modalX + 10, modalY + 6, 0xFFFFFFFF.toInt(), true)

        guiGraphics.drawString(font, "There are unsaved modifications in the project.", modalX + 15, modalY + 32, 0xFFCCCCCC.toInt(), false)
        guiGraphics.drawString(font, "Do you want to save before exiting?", modalX + 15, modalY + 46, 0xFFA0A0A0.toInt(), false)

        val btnW = 80
        val btnH = 18
        val btnY = modalY + 70

        val b1X = modalX + 12
        val h1 = mouseX >= b1X && mouseX <= b1X + btnW && mouseY >= btnY && mouseY <= btnY + btnH
        guiGraphics.fill(b1X, btnY, b1X + btnW, btnY + btnH, if (h1) 0xFF388E3C.toInt() else 0xFF2E7D32.toInt())
        guiGraphics.drawString(font, "Save & Exit", b1X + 8, btnY + 5, 0xFFFFFFFF.toInt(), false)

        val b2X = modalX + 98
        val h2 = mouseX >= b2X && mouseX <= b2X + btnW && mouseY >= btnY && mouseY <= btnY + btnH
        guiGraphics.fill(b2X, btnY, b2X + btnW, btnY + btnH, if (h2) 0xFFD32F2F.toInt() else 0xFFC62828.toInt())
        guiGraphics.drawString(font, "Exit Without Saving", b2X + 6, btnY + 5, 0xFFFFFFFF.toInt(), false)

        val b3X = modalX + 184
        val h3 = mouseX >= b3X && mouseX <= b3X + btnW && mouseY >= btnY && mouseY <= btnY + btnH
        guiGraphics.fill(b3X, btnY, b3X + btnW, btnY + btnH, if (h3) 0xFF555566.toInt() else 0xFF333344.toInt())
        guiGraphics.drawString(font, "Cancel", b3X + 18, btnY + 5, 0xFFFFFFFF.toInt(), false)
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

        val subText = font.plainSubstrByWidth("Click to Place", gw - 8)
        guiGraphics.drawString(font, subText, gx + 6, gy + 26, 0xFFA0A0A0.toInt(), false)
    }

    private fun renderSceneContainers(guiGraphics: GuiGraphics, isModalOpen: Boolean = false) {
        val scenesToRender = if (editingConstructionNode != null) emptyList() else project.scenes

        scenesToRender.forEach { scene ->
            // Viewport Culling for Scene Containers
            if (scene.x + scene.width < viewLeft || scene.x > viewRight ||
                scene.y + scene.height < viewTop || scene.y > viewBottom) {
                return@forEach
            }

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
                    scene.isStartScene && scene.isEndScene -> " [START & END]"
                    scene.isStartScene -> " 🟢 [START]"
                    scene.isEndScene -> " 🛑 [END]"
                    else -> ""
                }
                guiGraphics.drawString(font, "🎬 SCENE: ${scene.title}$sceneBadge", sx + 8, sy - 14, 0xFF00FFCC.toInt(), false)
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
            if (iy in topOffset..<height) {
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
            if (imx in 0..<width) {
                guiGraphics.fill(imx, topOffset, imx + 1, height, majorGridColor)
            }
            mx += majorGridSize
        }

        var my = majorStartY
        while (my < height) {
            val imy = my.toInt()
            if (imy in topOffset..<height) {
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

                val fromNodeWidget = if (fromScene == null) nodeWidgets.find { it.node.id == conn.fromNodeId } else null
                val toNodeWidget = if (toScene == null) nodeWidgets.find { it.node.id == conn.toNodeId } else null

                val (x1, y1) = when {
                    fromScene != null -> Pair(fromScene.x + fromScene.width, fromScene.y + 40)
                    fromNodeWidget != null -> {
                        val idx = fromNodeWidget.node.outputs.indexOfFirst { it.id == conn.fromPortId }
                        if (idx >= 0) fromNodeWidget.getOutputPortWorldPos(idx) else Pair(fromNodeWidget.node.x, fromNodeWidget.node.y)
                    }
                    else -> Pair(0.0, 0.0)
                }

                val (x2, y2) = when {
                    toScene != null -> Pair(toScene.x, toScene.y + 40)
                    toNodeWidget != null -> {
                        val idx = toNodeWidget.node.inputs.indexOfFirst { it.id == conn.toPortId }
                        if (idx >= 0) toNodeWidget.getInputPortWorldPos(idx) else Pair(toNodeWidget.node.x, toNodeWidget.node.y)
                    }
                    else -> Pair(0.0, 0.0)
                }

                if (x1 != 0.0 || y1 != 0.0 || x2 != 0.0 || y2 != 0.0) {
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
        val halfThick = thickness / 2
        val extraThick = thickness % 2

        if (dx == 0 && dy == 0) {
            guiGraphics.fill(x1 - halfThick, y1 - halfThick, x1 + halfThick + extraThick, y1 + halfThick + extraThick, color)
            return
        }
        if (dy == 0) {
            val minX = minOf(x1, x2)
            val maxX = maxOf(x1, x2)
            guiGraphics.fill(minX - halfThick, y1 - halfThick, maxX + halfThick + extraThick, y1 + halfThick + extraThick, color)
            return
        }
        if (dx == 0) {
            val minY = minOf(y1, y2)
            val maxY = maxOf(y1, y2)
            guiGraphics.fill(x1 - halfThick, minY - halfThick, x1 + halfThick + extraThick, maxY + halfThick + extraThick, color)
            return
        }

        val sx = if (x1 < x2) 1 else -1
        val sy = if (y1 < y2) 1 else -1
        var err = dx - dy

        var cx = x1
        var cy = y1

        if (dx >= dy) {
            var spanStartX = cx
            while (true) {
                if (cx == x2 && cy == y2) {
                    val minX = minOf(spanStartX, cx)
                    val maxX = maxOf(spanStartX, cx)
                    guiGraphics.fill(minX - halfThick, cy - halfThick, maxX + halfThick + extraThick, cy + halfThick + extraThick, color)
                    break
                }
                val e2 = 2 * err
                if (e2 > -dy) {
                    err -= dy
                    cx += sx
                }
                if (e2 < dx) {
                    err += dx
                    val lastX = if (sx > 0) cx - 1 else cx + 1
                    val minX = minOf(spanStartX, lastX)
                    val maxX = maxOf(spanStartX, lastX)
                    guiGraphics.fill(minX - halfThick, cy - halfThick, maxX + halfThick + extraThick, cy + halfThick + extraThick, color)
                    cy += sy
                    spanStartX = cx
                }
            }
        } else {
            var spanStartY = cy
            while (true) {
                if (cx == x2 && cy == y2) {
                    val minY = minOf(spanStartY, cy)
                    val maxY = maxOf(spanStartY, cy)
                    guiGraphics.fill(cx - halfThick, minY - halfThick, cx + halfThick + extraThick, maxY + halfThick + extraThick, color)
                    break
                }
                val e2 = 2 * err
                if (e2 > -dy) {
                    err -= dy
                    val lastY = if (sy > 0) cy - 1 else cy + 1
                    val minY = minOf(spanStartY, lastY)
                    val maxY = maxOf(spanStartY, lastY)
                    guiGraphics.fill(cx - halfThick, minY - halfThick, cx + halfThick + extraThick, maxY + halfThick + extraThick, color)
                    cx += sx
                    spanStartY = cy
                }
                if (e2 < dx) {
                    err += dx
                    cy += sy
                }
            }
        }
    }

    private fun drawBezierCurve(guiGraphics: GuiGraphics, x1: Double, y1: Double, x2: Double, y2: Double, color: Int) {
        val minX = minOf(x1, x2)
        val maxX = maxOf(x1, x2)
        val minY = minOf(y1, y2)
        val maxY = maxOf(y1, y2)

        // Viewport Culling: skip curve if bounding box is completely off-screen
        if (maxX < viewLeft || minX > viewRight || maxY < viewTop || minY > viewBottom) {
            return
        }

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
        val segments = (dist / 20.0).toInt().coerceIn(12, 36)

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
            val node = widget.node
            if (currentMouseWorldX < node.x - 20.0 || currentMouseWorldX > node.x + node.width + 20.0 ||
                currentMouseWorldY < node.y - 20.0 || currentMouseWorldY > node.y + node.height + 20.0) {
                continue
            }
            val portPair = widget.getPortAtWorldPos(currentMouseWorldX, currentMouseWorldY)
            if (portPair != null) {
                hoveredPort = portPair.first
                break
            }
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (activeItemPickerModal != null) return activeItemPickerModal!!.mouseClicked(mouseX, mouseY, button)
        if (activeResourcePickerModal != null) return activeResourcePickerModal!!.mouseClicked(mouseX, mouseY, button)
        if (activePokemonConfigModal != null) return activePokemonConfigModal!!.mouseClicked(mouseX, mouseY, button)
        if (activeEntityConfigModal != null) return activeEntityConfigModal!!.mouseClicked(mouseX, mouseY, button)
        if (activeAnimationSelectorModal != null) {
            if (activeAnimationSelectorModal?.mouseClicked(mouseX, mouseY, button) == true) return true
        }
        if (activeTextureSelectorModal != null) {
            return activeTextureSelectorModal!!.mouseClicked(mouseX, mouseY, button)
        }
        if (activeSaveProfileModal != null) {
            if (activeSaveProfileModal?.mouseClicked(mouseX, mouseY, button) == true) return true
        }
        if (activeAIDialogueModal != null) {
            if (activeAIDialogueModal?.mouseClicked(mouseX, mouseY, button) == true) return true
        }
        if (activeCoordinateModal != null) {
            if (activeCoordinateModal?.mouseClicked(mouseX, mouseY, button) == true) return true
        }
        if (activeActionTriggerPickerModal != null) return activeActionTriggerPickerModal!!.mouseClicked(mouseX, mouseY, button)
        if (activeVarSelectorModal != null) return activeVarSelectorModal!!.mouseClicked(mouseX, mouseY, button)
        if (activeMetadataModal != null) return activeMetadataModal!!.mouseClicked(mouseX, mouseY, button)
        if (activeVariableModal != null) return activeVariableModal!!.mouseClicked(mouseX, mouseY, button)
        if (activeDocModal != null) return activeDocModal!!.mouseClicked(mouseX, mouseY, button)

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

        // Top Toolbar Debug Console Button Click (Far Top-Right Corner) - disabled when inspector is open
        if (activeInspector == null && activeSceneInspector == null) {
            val errCount = vito.cobblebrain.engine.StoryDebugger.getErrorCount(project.id)
            val debugBtnLabel = if (errCount > 0) "🐞 Debug ($errCount)" else "🐞 Debug"
            val debugBtnW = font.width(debugBtnLabel) + 12
            val debugBtnH = 14
            val debugBtnX = width - debugBtnW - 8
            val debugBtnY = 2
            if (mouseX >= debugBtnX && mouseX <= debugBtnX + debugBtnW && mouseY >= debugBtnY && mouseY <= debugBtnY + debugBtnH) {
                if (!isInWorld) {
                    showWarning("⚠️ You must be loaded into a world to use debug mode!")
                    return true
                }
                openDebugConsole()
                return true
            }
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
                            StorySerializer.ensureAllScenesLoaded(loaded)
                            project = loaded
                            editingConstructionNode = null
                            openSceneIds.clear()
                            openConstructionNodes.clear()
                            loaded.scenes.forEach { openSceneIds.add(it.id) }
                            isDirty = false
                            init()
                            rebuildNodeWidgets()
                            showStatus("Story loaded: ${loaded.name}")
                        })
                    }
                    2 -> exportToZip()
                    3 -> openMetadataModal()
                }
                isFileMenuOpen = false
                return true
            } else if (mouseY > toolbarHeight + sceneBarHeight) {
                isFileMenuOpen = false
            }
        }

        if (isAddMenuOpen) {
            val dropW = 175
            val dropX = addMenuX
            val itemsCount = 8
            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY < dropY + itemH * itemsCount) {
                val idx = ((mouseY - (dropY + 2)) / itemH).toInt()
                when (idx) {
                    0 -> createNewSceneFrame()
                    1 -> {
                        addNode(NodeType.DIALOGUE)
                        isAddMenuOpen = false
                    }
                    2 -> {
                        addNode(NodeType.ACTION, "MOVE_TO_BLOCK")
                        isAddMenuOpen = false
                    }
                    3 -> {
                        addNode(NodeType.ACTION, "TAG_BLOCK")
                        isAddMenuOpen = false
                    }
                    4 -> {
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
                    5 -> {
                        addNode(NodeType.VARIABLE_GET)
                        isAddMenuOpen = false
                    }
                    6 -> {
                        addNode(NodeType.VARIABLE_SET)
                        isAddMenuOpen = false
                    }
                    7 -> {
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
            val session = vito.cobblebrain.engine.StoryDebugger.activeSessionState
            val isStoryActive = session.isActive || vito.cobblebrain.engine.StoryExecutor.activeStories.containsKey(project.id)
            val isStoryPaused = session.isPaused

            val menuActions = mutableListOf<String>()
            menuActions.add("LIVE_TEST")
            menuActions.add("START_ALL")
            menuActions.add("RANGE_SELECT")
            if (isStoryActive) {
                if (isStoryPaused) {
                    menuActions.add("RESUME")
                } else {
                    menuActions.add("PAUSE")
                }
                menuActions.add("STOP")
            }
            menuActions.add("DEBUG")

            val testW = 210
            val itemH = 20
            val dropdownH = itemH * menuActions.size + 4

            val testBtnW = getBtnWidth(if (isStoryPaused) "⏸ Test (Paused) ▾" else if (isStoryActive) "⚡ Testing... ▾" else "▶ Test ▾")
            val testX = (testBtnX + testBtnW - testW).coerceAtMost(width - testW - 5).coerceAtLeast(5)
            val testY = testBtnY - dropdownH - 4

            if (mouseX >= testX && mouseX <= testX + testW && mouseY >= testY && mouseY <= testY + dropdownH) {
                if (!isInWorld) {
                    showWarning("⚠️ You must be loaded into a world to test stories or debug!")
                    isTestMenuOpen = false
                    return true
                }
                val idx = ((mouseY - (testY + 2)) / itemH).toInt()
                if (idx in menuActions.indices) {
                    when (menuActions[idx]) {
                        "LIVE_TEST" -> {
                            saveProject()
                            val serverPlayer = minecraft?.player?.uuid?.let { minecraft?.singleplayerServer?.playerList?.getPlayer(it) }
                            StoryExecutor.startStory(project, serverPlayer)
                            showStatus("⚡ Story test running live on canvas! Watching block execution.")
                            init()
                        }
                        "START_ALL" -> {
                            saveProject()
                            val serverPlayer = minecraft?.player?.uuid?.let { minecraft?.singleplayerServer?.playerList?.getPlayer(it) }
                            StoryExecutor.startStory(project, serverPlayer)
                            minecraft?.setScreen(null)
                        }
                        "RANGE_SELECT" -> {
                            isRangeTestSelectionMode = true
                            rangeTestStartNode = null
                            rangeTestEndNode = null
                            rebuildNodeWidgets()
                            showStatus("🎯 Click on the 1st block to set the START of the test. (Free move/pan on screen)")
                        }
                        "PAUSE" -> {
                            vito.cobblebrain.client.StoryControlClient.pause(project.id)
                            showStatus("Test execution paused.")
                            init()
                        }
                        "RESUME" -> {
                            vito.cobblebrain.client.StoryControlClient.resume(project.id)
                            showStatus("Test execution resumed.")
                            init()
                        }
                        "STOP" -> {
                            vito.cobblebrain.client.StoryControlClient.stop(project.id)
                            vito.cobblebrain.engine.StoryDebugger.clearNodeStatuses(project.id)
                            rebuildNodeWidgets()
                            showStatus("Test execution stopped.")
                            init()
                        }
                        "DEBUG" -> {
                            openDebugConsole()
                        }
                    }
                }
                isTestMenuOpen = false
                return true
            } else {
                if (!(mouseX >= testBtnX && mouseX <= testBtnX + testBtnW && mouseY >= testBtnY && mouseY <= testBtnY + 16)) {
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
                val maxScrollable = paletteEntries.size - maxVisibleItems
                if (maxScrollable > 0) {
                    val sbW = 5
                    val sbX = palX + palW - sbW - 1
                    if (mouseX >= sbX - 4 && mouseX <= palX + palW) {
                        isDraggingPaletteScrollbar = true
                        paletteDragStartMouseY = mouseY
                        paletteDragStartOffset = paletteScrollOffset
                        val scrollRatio = maxVisibleItems.toFloat() / paletteEntries.size
                        val thumbH = (visibleHeight * scrollRatio).toInt().coerceAtLeast(14)
                        val trackRange = visibleHeight - 4 - thumbH
                        if (trackRange > 0) {
                            val clickOffset = ((mouseY - (palY + 2) - thumbH / 2.0) / trackRange).coerceIn(0.0, 1.0)
                            paletteScrollOffset = (clickOffset * maxScrollable).toInt().coerceIn(0, maxScrollable)
                        }
                        return true
                    }
                }

                val idx = ((mouseY - (palY + 2)) / itemH).toInt() + paletteScrollOffset
                if (idx in paletteEntries.indices) {
                    val entry = paletteEntries[idx]
                    if (!entry.isHeader) {
                        addNode(entry.type, entry.presetSubtype)
                    }
                }
                isBlockPaletteOpen = false
                return true
            } else if (mouseY > toolbarHeight + sceneBarHeight) {
                isBlockPaletteOpen = false
            }
        }

        if (activeInspector?.mouseClicked(mouseX, mouseY, button) == true) return true
        if (activeSceneInspector?.mouseClicked(mouseX, mouseY, button) == true) return true

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
                        showStatus("Construction tab ${constr.title} closed.")
                    } else {
                        editingConstructionNode = constr
                        init()
                        rebuildNodeWidgets()
                        showStatus("Focus changed to construction ${constr.title}")
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
                            showStatus("Scene tab ${scene.title} closed.")
                        } else {
                            showStatus("Cannot close the only visible scene tab!")
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

        if (mouseY < toolbarHeight + sceneBarHeight) {
            return super.mouseClicked(mouseX, mouseY, button)
        }

        val worldX = screenToWorldX(mouseX)
        val worldY = screenToWorldY(mouseY)

        // RANGE TEST SELECTION MINI-MODE (Allows Panning / Moving screen if clicking empty space!)
        if (isRangeTestSelectionMode && button == 0) {
            if (!isInWorld) {
                showWarning("⚠️ You must be loaded into a world to run range tests!")
                isRangeTestSelectionMode = false
                rangeTestStartNode = null
                rangeTestEndNode = null
                rebuildNodeWidgets()
                return true
            }
            val clickedWidget = nodeWidgets.reversed().find { it.isWorldPosInside(worldX, worldY) }
            if (clickedWidget != null) {
                if (rangeTestStartNode == null) {
                    rangeTestStartNode = clickedWidget.node
                    rebuildNodeWidgets()
                    showStatus("▶ Start set: ${clickedWidget.node.title}. Click on 2nd block (END OF TEST).")
                } else {
                    rangeTestEndNode = clickedWidget.node
                    rebuildNodeWidgets()
                    showStatus("🛑 End set: ${clickedWidget.node.title}. Executing range test...")
                    startRangeTestExecution()
                }
                return true
            } else {
                // Clicked outside blocks: activate camera panning
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
                showStatus("Entered Construction ${targetNode.title}. Click to place internal block.")
                return true
            }

            ghost.x = worldX - ghost.width / 2.0
            ghost.y = worldY - ghost.height / 2.0

            val targetScene = project.scenes.find { scene -> isNodeInsideScene(ghost, scene) }

            if (targetScene != null && editingConstructionNode == null) {
                ghost.parentSceneId = targetScene.id
                project.globalNodes.removeIf { it.id == ghost.id }
                project.scenes.forEach { sc ->
                    if (sc.id != targetScene.id) {
                        sc.nodes.removeIf { it.id == ghost.id }
                    }
                }
                if (!targetScene.nodes.any { it.id == ghost.id }) {
                    targetScene.nodes.add(ghost)
                }
                updateVariableSceneBinding(ghost, targetScene.id)
                showStatus("Block linked to scene: ${targetScene.title}")
            } else if (editingConstructionNode != null) {
                ghost.parentSceneId = null
                val targetList = editingConstructionNode!!.innerNodes
                if (!targetList.any { it.id == ghost.id }) {
                    targetList.add(ghost)
                }
                showStatus("Block placed inside Construction.")
            } else {
                ghost.parentSceneId = null
                project.scenes.forEach { sc ->
                    sc.nodes.removeIf { it.id == ghost.id }
                }
                if (!project.globalNodes.any { it.id == ghost.id }) {
                    project.globalNodes.add(ghost)
                }
                showStatus("Block placed as global node in Studio.")
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
            openContextMenuForCanvas(mouseX.toInt(), mouseY.toInt(), worldX, worldY)
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
                        showStatus("Disconnected from Scene In port!")
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
                        showStatus("Disconnected from Scene Out port!")
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
                    val removedFromScenes = project.scenes.any { sc ->
                        sc.connections.removeIf { it.fromPortId == port.id || it.toPortId == port.id }
                    }
                    val removedFromInter = project.sceneConnections.removeIf { it.fromPortId == port.id || it.toPortId == port.id }
                    val removedFromConstr = editingConstructionNode?.innerConnections?.removeIf { it.fromPortId == port.id || it.toPortId == port.id } == true
                    if (removedFromScenes || removedFromInter || removedFromConstr) {
                        markDirty()
                        showStatus("Disconnected from port!")
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

        for (widget in nodeWidgets.reversed()) {
            if (widget.isWorldPosOnResizeHandle(worldX, worldY)) {
                resizingNode = widget.node
                selectedWidgets.forEach { it.isSelected = false }
                selectedWidgets.clear()
                selectedWidget = widget
                widget.isSelected = true
                selectedWidgets.add(widget)
                markDirty()
                return true
            }
        }

        val isShiftOrCtrl = hasShiftDown() || hasControlDown()

        var clickedOnNode = false
        for (widget in nodeWidgets.reversed()) {
            if (widget.isWorldPosInside(worldX, worldY)) {
                clickedOnNode = true
                val now = System.currentTimeMillis()
                val isDouble = (widget.node.id == lastClickedNodeId) && (now - lastClickedNodeTime < 300)
                lastClickedNodeId = widget.node.id
                lastClickedNodeTime = now

                if (isShiftOrCtrl) {
                    if (selectedWidgets.contains(widget)) {
                        widget.isSelected = false
                        selectedWidgets.remove(widget)
                        if (selectedWidget == widget) {
                            selectedWidget = selectedWidgets.firstOrNull()
                        }
                    } else {
                        widget.isSelected = true
                        selectedWidgets.add(widget)
                        selectedWidget = widget
                        if (isDouble) {
                            openInspectorForNode(widget.node)
                        }
                    }
                } else {
                    if (selectedWidgets.contains(widget) && selectedWidgets.size > 1) {
                        // Already in a multi-selection: drag all selected nodes together
                        draggedWidget = widget
                        selectedWidgets.forEach { it.isDragging = true }
                        if (isDouble) {
                            openInspectorForNode(widget.node)
                        }
                    } else {
                        // Single selection
                        selectedWidgets.forEach { it.isSelected = false }
                        selectedWidgets.clear()
                        selectedWidget = widget
                        widget.isSelected = true
                        widget.isDragging = true
                        selectedWidgets.add(widget)
                        draggedWidget = widget
                        if (isDouble) {
                            openInspectorForNode(widget.node)
                        }
                    }
                }
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
                    val now = System.currentTimeMillis()
                    val isDouble = (scene.id == lastClickedSceneId) && (now - lastClickedSceneTime < 300)
                    lastClickedSceneId = scene.id
                    lastClickedSceneTime = now

                    draggedScene = scene
                    project.activeSceneId = scene.id
                    rebuildNodeWidgets()
                    if (isDouble) {
                        openSceneInspector(scene)
                    }
                    return true
                }
            }
        }

        if (!clickedOnNode && draggedScene == null && resizingScene == null) {
            val now = System.currentTimeMillis()
            val isDoubleClick = (now - lastEmptyCanvasClickTime < 350) && (Math.hypot(mouseX - lastEmptyCanvasClickX, mouseY - lastEmptyCanvasClickY) < 15.0)
            val isShiftOrCtrl = hasShiftDown() || hasControlDown()

            lastEmptyCanvasClickTime = now
            lastEmptyCanvasClickX = mouseX
            lastEmptyCanvasClickY = mouseY

            if (button == 0) { // Left click
                if (isDoubleClick) {
                    // Double-click triggers immediate box selection
                    isSelectionModeActive = true
                    isBoxSelecting = true
                    isHoldingForSelection = false
                    selectionOriginWorldX = worldX
                    selectionOriginWorldY = worldY
                    selectionCurrentWorldX = worldX
                    selectionCurrentWorldY = worldY
                    return true
                }

                if (!isShiftOrCtrl) {
                    selectedWidgets.forEach { it.isSelected = false }
                    selectedWidgets.clear()
                    selectedWidget = null
                    activeInspector = null
                    activeSceneInspector = null
                    activeContextMenu = null
                    closeAllTopMenus()
                }

                selectionOriginWorldX = worldX
                selectionOriginWorldY = worldY
                selectionCurrentWorldX = worldX
                selectionCurrentWorldY = worldY

                if (isShiftOrCtrl) {
                    // Immediate Selection Box trigger with Shift/Ctrl
                    isBoxSelecting = true
                    isHoldingForSelection = false
                } else {
                    // Click-and-hold (Long press) or normal pan candidate
                    selectionHoldStartTime = now
                    selectionHoldOriginX = mouseX
                    selectionHoldOriginY = mouseY
                    isHoldingForSelection = true
                    lastMouseX = mouseX
                    lastMouseY = mouseY
                }
            } else if (button == 2 || button == 1) { // Middle or Right click: Pan
                isPanning = true
                lastMouseX = mouseX
                lastMouseY = mouseY
            }
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

    private fun openContextMenuForCanvas(screenX: Int, screenY: Int, worldX: Double, worldY: Double) {
        activeContextMenu = ContextMenuWidget(
            screenX = screenX,
            screenY = screenY,
            isCanvasMenu = true,
            font = font,
            onAction = { action ->
                when (action) {
                    ContextMenuAction.PASTE_NODES -> {
                        pasteCopiedNodesAt(worldX, worldY)
                    }
                    ContextMenuAction.SAVE_STORY -> {
                        saveProject()
                    }
                    ContextMenuAction.ADD_NODE -> {
                        isBlockPaletteOpen = true
                        closeAllTopMenus()
                        isTestMenuOpen = false
                        showStatus("Node creation palette opened.")
                    }
                    else -> {}
                }
            },
            onClose = { activeContextMenu = null }
        )
    }

    private fun pasteCopiedNodesAt(targetX: Double, targetY: Double) {
        if (!BlockDataClipboard.hasCopiedNodes()) return

        val targetScene = if (editingConstructionNode != null) null else project.scenes.find { targetX >= it.x && targetX <= it.x + it.width && targetY >= it.y && targetY <= it.y + it.height }
        val targetSceneId = targetScene?.id

        val (pastedNodes, pastedConns) = BlockDataClipboard.paste(targetX, targetY, targetSceneId)
        if (pastedNodes.isNotEmpty()) {
            if (editingConstructionNode != null) {
                editingConstructionNode?.innerNodes?.addAll(pastedNodes)
                editingConstructionNode?.innerConnections?.addAll(pastedConns)
            } else if (targetScene != null) {
                targetScene.nodes.addAll(pastedNodes)
                targetScene.connections.addAll(pastedConns)
            } else {
                pastedNodes.forEach { it.parentSceneId = null }
                project.globalNodes.addAll(pastedNodes)
                project.sceneConnections.addAll(pastedConns)
            }
            markDirty()
            selectedWidgets.forEach { it.isSelected = false }
            selectedWidgets.clear()
            rebuildNodeWidgets()

            val pastedIds = pastedNodes.map { it.id }.toSet()
            nodeWidgets.filter { pastedIds.contains(it.node.id) }.forEach {
                it.isSelected = true
                selectedWidgets.add(it)
            }

            if (selectedWidgets.size == 1) {
                selectedWidget = selectedWidgets.first()
                openInspectorForNode(selectedWidget!!.node)
                showStatus("Pasted 1 node: ${selectedWidget!!.node.title}")
            } else {
                selectedWidget = null
                activeInspector = null
                showStatus("Pasted ${pastedNodes.size} nodes.")
            }
        }
    }

    private fun handleContextMenuNodeAction(node: NodeData, action: ContextMenuAction) {
        val currentNodes = getActiveNodes()
        val currentConns = getActiveConnections()

        when (action) {
            ContextMenuAction.DELETE -> {
                deleteNode(node)
            }
            ContextMenuAction.DUPLICATE -> {
                val inputs = node.inputs.map { PortData(name = it.name, type = it.type) }.toMutableList()
                val outputs = node.outputs.map { 
                    val portId = if (it.id == "OUT_COND" || it.name.startsWith("Cond Out", ignoreCase = true)) "OUT_COND" else java.util.UUID.randomUUID().toString()
                    PortData(id = portId, name = it.name, type = it.type) 
                }.toMutableList()
                val cloneNode = NodeData(
                    parentSceneId = node.parentSceneId,
                    title = "${node.title} (Copy)",
                    nodeType = node.nodeType,
                    content = node.content,
                    x = node.x + 20.0,
                    y = node.y + 20.0,
                    width = node.width,
                    height = node.height,
                    preDelayTicks = node.preDelayTicks,
                    postDelayTicks = node.postDelayTicks,
                    inputs = inputs,
                    outputs = outputs,
                    params = HashMap(node.params)
                )
                if (editingConstructionNode != null) {
                    editingConstructionNode?.innerNodes?.add(cloneNode)
                } else if (node.parentSceneId != null) {
                    val scene = project.scenes.find { it.id == node.parentSceneId } ?: project.getActiveScene()
                    scene?.nodes?.add(cloneNode)
                } else {
                    project.globalNodes.add(cloneNode)
                }
                markDirty()
                rebuildNodeWidgets()
                openInspectorForNode(cloneNode)
                showStatus("Block duplicated!")
            }
            ContextMenuAction.DETACH_FROM_SCENE -> {
                dissociateNode(node)
            }
            ContextMenuAction.COPY_DATA -> {
                BlockDataClipboard.copyFrom(node)
                showStatus("Block data copied!")
            }
            ContextMenuAction.PASTE_DATA -> {
                if (BlockDataClipboard.pasteTo(node)) {
                    markDirty()
                    rebuildNodeWidgets()
                    openInspectorForNode(node)
                    showStatus("Data pasted to block!")
                } else {
                    showStatus("Incompatible block type!")
                }
            }
            ContextMenuAction.RESET_PROPERTIES -> {
                node.content = ""
                node.params.clear()
                markDirty()
                rebuildNodeWidgets()
                openInspectorForNode(node)
                showStatus("Block properties reset!")
            }
            ContextMenuAction.DISCONNECT_PORTS -> {
                val removed = currentConns.removeAll { it.fromNodeId == node.id || it.toNodeId == node.id }
                val removedGlobal = project.sceneConnections.removeAll { it.fromNodeId == node.id || it.toNodeId == node.id }
                if (removed || removedGlobal) markDirty()
                showStatus(if (removed || removedGlobal) "Ports disconnected!" else "No connections found.")
            }
            ContextMenuAction.PASTE_NODES -> {
                pasteCopiedNodesAt(currentMouseWorldX, currentMouseWorldY)
            }
            ContextMenuAction.SAVE_STORY -> {
                saveProject()
            }
            ContextMenuAction.ADD_NODE -> {
                isBlockPaletteOpen = true
            }
            else -> {}
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
                    showStatus("Scene deleted successfully!")
                } else {
                    showStatus("Cannot delete the only scene in the project!")
                }
            }
            ContextMenuAction.DUPLICATE -> {
                val cloneScene = SceneData(
                    title = "${scene.title} (Copy)",
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
                            preDelayTicks = n.preDelayTicks,
                            postDelayTicks = n.postDelayTicks,
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
                showStatus("Scene duplicated!")
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
                showStatus("Scene reset!")
            }
            else -> {}
        }
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (activeItemPickerModal != null) return activeItemPickerModal!!.mouseDragged(mouseX, mouseY, button, dragX, dragY)
        if (activeResourcePickerModal != null) return activeResourcePickerModal!!.mouseDragged(mouseX, mouseY, button, dragX, dragY)
        if (activeEntityConfigModal != null) return activeEntityConfigModal!!.mouseDragged(mouseX, mouseY, button, dragX, dragY)
        if (activeAnimationSelectorModal?.mouseDragged(mouseX, mouseY, button, dragX, dragY) == true) return true
        if (activeTextureSelectorModal?.mouseDragged(mouseX, mouseY, button, dragX, dragY) == true) return true
        if (activeSaveProfileModal?.mouseDragged(mouseX, mouseY, button, dragX, dragY) == true) return true
        if (activeMetadataModal?.mouseDragged(mouseX, mouseY, button, dragX, dragY) == true) return true
        if (activeDocModal?.mouseDragged(mouseX, mouseY, button, dragX, dragY) == true) return true
        if (activeVariableModal?.mouseDragged(mouseX, mouseY, button, dragX, dragY) == true) return true
        if (activeVarSelectorModal?.mouseDragged(mouseX, mouseY, button, dragX, dragY) == true) return true
        if (activeActionTriggerPickerModal?.mouseDragged(mouseX, mouseY, button, dragX, dragY) == true) return true
        if (showExitConfirmModal || activeDocModal != null || activeVariableModal != null || activeVarSelectorModal != null || activePokemonConfigModal != null || activeActionTriggerPickerModal != null || activeAIDialogueModal != null || activeAnimationSelectorModal != null || activeTextureSelectorModal != null || activeCoordinateModal != null) return true

        if (isDraggingPaletteScrollbar && isBlockPaletteOpen) {
            val palY = toolbarHeight + sceneBarHeight + 2
            val maxVisibleItems = 10
            val visibleHeight = maxVisibleItems * 18 + 4
            val maxScrollable = paletteEntries.size - maxVisibleItems
            if (maxScrollable > 0) {
                val scrollRatio = maxVisibleItems.toFloat() / paletteEntries.size
                val thumbH = (visibleHeight * scrollRatio).toInt().coerceAtLeast(14)
                val trackRange = visibleHeight - 4 - thumbH
                if (trackRange > 0) {
                    val deltaY = mouseY - paletteDragStartMouseY
                    val deltaOffset = (deltaY / trackRange * maxScrollable).toInt()
                    paletteScrollOffset = (paletteDragStartOffset + deltaOffset).coerceIn(0, maxScrollable)
                    return true
                }
            }
        }

        if (activeInspector?.mouseDragged(mouseX, mouseY, button, dragX, dragY) == true) return true
        if (activeSceneInspector?.mouseDragged(mouseX, mouseY, button, dragX, dragY) == true) return true

        val now = System.currentTimeMillis()

        if (isHoldingForSelection && !isBoxSelecting) {
            val holdDuration = now - selectionHoldStartTime
            val moveDist = Math.hypot(mouseX - selectionHoldOriginX, mouseY - selectionHoldOriginY)

            // If held down for >= 200ms before or while moving, it becomes a Box Selection!
            if (holdDuration >= 200) {
                isBoxSelecting = true
                isHoldingForSelection = false
                isPanning = false
            } else if (moveDist > 6.0) {
                // Moved fast within < 200ms: user wants to pan!
                isPanning = true
                isHoldingForSelection = false
            }
        }

        if (isBoxSelecting) {
            selectionCurrentWorldX = screenToWorldX(mouseX)
            selectionCurrentWorldY = screenToWorldY(mouseY)

            val selMinX = minOf(selectionOriginWorldX, selectionCurrentWorldX)
            val selMaxX = maxOf(selectionOriginWorldX, selectionCurrentWorldX)
            val selMinY = minOf(selectionOriginWorldY, selectionCurrentWorldY)
            val selMaxY = maxOf(selectionOriginWorldY, selectionCurrentWorldY)

            val isShiftOrCtrl = hasShiftDown() || hasControlDown()

            for (widget in nodeWidgets) {
                val nodeX = widget.node.x
                val nodeY = widget.node.y
                val nodeW = widget.node.width
                val nodeH = widget.node.height

                val intersects = nodeX < selMaxX && nodeX + nodeW > selMinX &&
                                 nodeY < selMaxY && nodeY + nodeH > selMinY

                if (intersects) {
                    widget.isSelected = true
                    selectedWidgets.add(widget)
                } else if (!isShiftOrCtrl) {
                    widget.isSelected = false
                    selectedWidgets.remove(widget)
                }
            }
            return true
        }

        val targetResizeNode = resizingNode
        if (targetResizeNode != null) {
            val worldX = screenToWorldX(mouseX)
            val worldY = screenToWorldY(mouseY)
            targetResizeNode.width = (worldX - targetResizeNode.x).coerceAtLeast(120.0)
            targetResizeNode.height = (worldY - targetResizeNode.y).coerceAtLeast(40.0)
            markDirty()
            return true
        }

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
            val dx = dragX / zoom
            val dy = dragY / zoom

            if (selectedWidgets.contains(targetWidget) && selectedWidgets.size > 1) {
                // Bulk simultaneous dragging for all selected nodes
                selectedWidgets.forEach { w ->
                    w.node.x += dx
                    w.node.y += dy
                }
            } else {
                targetWidget.node.x += dx
                targetWidget.node.y += dy
            }
            markDirty()
            return true
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        isDraggingPaletteScrollbar = false
        activeDocModal?.mouseReleased(mouseX, mouseY, button)
        activeTextureSelectorModal?.mouseReleased(mouseX, mouseY, button)
        activeVariableModal?.mouseReleased(mouseX, mouseY, button)
        activeVarSelectorModal?.mouseReleased(mouseX, mouseY, button)
        activeActionTriggerPickerModal?.mouseReleased(mouseX, mouseY, button)
        activeMetadataModal?.mouseReleased(mouseX, mouseY, button)
        activeInspector?.mouseReleased(mouseX, mouseY, button)
        activeSceneInspector?.mouseReleased(mouseX, mouseY, button)

        if (showExitConfirmModal || activeDocModal != null || activeVariableModal != null || activeMetadataModal != null || activeVarSelectorModal != null || activeTextureSelectorModal != null || activeActionTriggerPickerModal != null) return true

        isHoldingForSelection = false
        isSelectionModeActive = false
        resizingNode = null
        resizingScene = null

        if (isBoxSelecting) {
            isBoxSelecting = false
            if (selectedWidgets.size == 1) {
                selectedWidget = selectedWidgets.first()
            } else if (selectedWidgets.size > 1) {
                selectedWidget = null
                activeInspector = null
                showStatus("${selectedWidgets.size} nodes selected.")
            }
            return true
        }

        isPanning = false
        draggedScene = null

        val targetWidget = draggedWidget
        if (targetWidget != null) {
            val nodesToProcess = if (selectedWidgets.contains(targetWidget) && selectedWidgets.size > 1) {
                selectedWidgets.map { it.node }
            } else {
                listOf(targetWidget.node)
            }

            val primaryNode = targetWidget.node
            val targetScene = project.scenes.find { isNodeInsideScene(primaryNode, it) }

            if (targetScene != null && editingConstructionNode == null) {
                var linkedCount = 0
                nodesToProcess.forEach { node ->
                    val oldSceneId = node.parentSceneId
                    if (oldSceneId != null && oldSceneId != targetScene.id) {
                        // Moved from another scene: sever old scene-local connections!
                        project.scenes.find { it.id == oldSceneId }?.connections?.removeIf { it.fromNodeId == node.id || it.toNodeId == node.id }
                    }
                    // Remove from globalNodes and all other scenes
                    project.globalNodes.removeIf { it.id == node.id }
                    project.scenes.forEach { sc ->
                        if (sc.id != targetScene.id) {
                            sc.nodes.removeIf { it.id == node.id }
                        }
                    }
                    node.parentSceneId = targetScene.id
                    if (!targetScene.nodes.any { it.id == node.id }) {
                        targetScene.nodes.add(node)
                    }
                    updateVariableSceneBinding(node, targetScene.id)
                    linkedCount++
                }
                if (linkedCount > 1) {
                    showStatus("$linkedCount nodes linked to scene: ${targetScene.title}")
                } else {
                    showStatus("Node linked to scene: ${targetScene.title}")
                }
            } else if (editingConstructionNode == null) {
                // Dragged outside all scenes: detach and become global blocks!
                var detachedCount = 0
                nodesToProcess.forEach { node ->
                    val oldSceneId = node.parentSceneId
                    if (oldSceneId != null) {
                        // Sever old scene-local connections attached to this node
                        project.scenes.find { it.id == oldSceneId }?.connections?.removeIf { it.fromNodeId == node.id || it.toNodeId == node.id }
                        detachedCount++
                    }
                    // Remove from ALL scenes
                    project.scenes.forEach { sc ->
                        sc.nodes.removeIf { it.id == node.id }
                    }
                    node.parentSceneId = null
                    if (!project.globalNodes.any { it.id == node.id }) {
                        project.globalNodes.add(node)
                    }
                }
                if (detachedCount > 0) {
                    showStatus(if (detachedCount == 1) "Node detached as global block." else "$detachedCount nodes detached as global blocks.")
                }
            }
            markDirty()
        }

        draggedWidget?.isDragging = false
        draggedWidget = null
        selectedWidgets.forEach { it.isDragging = false }

        val sourceScene = connectingSourceScene
        if (sourceScene != null && connectingSourceType != null) {
            val worldX = screenToWorldX(mouseX)
            val worldY = screenToWorldY(mouseY)
            var connected = false

            // 1. Try connecting Scene to another Scene
            for (targetScene in project.scenes) {
                if (targetScene.id == sourceScene.id) continue
                val inY = targetScene.y + 40
                val r = 12

                if (connectingSourceType == PortType.OUTPUT && Math.hypot(worldX - targetScene.x, worldY - inY) <= r) {
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
                        showStatus("Inter-scene connection created!")
                    }
                    connected = true
                    break
                }
            }

            // 2. Try connecting Scene to a Block Node
            if (!connected) {
                for (tWidget in nodeWidgets) {
                    val targetPortPair = tWidget.getPortAtWorldPos(worldX, worldY)
                    if (targetPortPair != null) {
                        val (targetPort, targetType) = targetPortPair
                        if (connectingSourceType != targetType) {
                            val fromNodeId = if (connectingSourceType == PortType.OUTPUT) sourceScene.id else tWidget.node.id
                            val fromPortId = if (connectingSourceType == PortType.OUTPUT) sourceScene.outPort.id else targetPort.id
                            val toNodeId = if (connectingSourceType == PortType.INPUT) sourceScene.id else tWidget.node.id
                            val toPortId = if (connectingSourceType == PortType.INPUT) sourceScene.inPort.id else targetPort.id

                            val exists = project.sceneConnections.any {
                                it.fromNodeId == fromNodeId && it.fromPortId == fromPortId &&
                                it.toNodeId == toNodeId && it.toPortId == toPortId
                            }
                            if (!exists) {
                                project.sceneConnections.add(
                                    ConnectionData(
                                        fromNodeId = fromNodeId,
                                        fromPortId = fromPortId,
                                        toNodeId = toNodeId,
                                        toPortId = toPortId
                                    )
                                )
                                markDirty()
                                showStatus("Scene-Block connection created!")
                            }
                        }
                        break
                    }
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
            var connectedToScene = false

            // 1. Check if dropped on a Scene Port
            for (targetScene in project.scenes) {
                val inY = targetScene.y + 40
                val outY = targetScene.y + 40
                val r = 12

                if (sourceType == PortType.OUTPUT && Math.hypot(worldX - targetScene.x, worldY - inY) <= r) {
                    val exists = project.sceneConnections.any {
                        it.fromNodeId == sourceNode.id && it.fromPortId == sourcePort.id &&
                        it.toNodeId == targetScene.id && it.toPortId == targetScene.inPort.id
                    }
                    if (!exists) {
                        project.sceneConnections.add(
                            ConnectionData(
                                fromNodeId = sourceNode.id,
                                fromPortId = sourcePort.id,
                                toNodeId = targetScene.id,
                                toPortId = targetScene.inPort.id
                            )
                        )
                        markDirty()
                        showStatus("Block-Scene connection created!")
                    }
                    connectedToScene = true
                    break
                } else if (sourceType == PortType.INPUT && Math.hypot(worldX - (targetScene.x + targetScene.width), worldY - outY) <= r) {
                    val exists = project.sceneConnections.any {
                        it.fromNodeId == targetScene.id && it.fromPortId == targetScene.outPort.id &&
                        it.toNodeId == sourceNode.id && it.toPortId == sourcePort.id
                    }
                    if (!exists) {
                        project.sceneConnections.add(
                            ConnectionData(
                                fromNodeId = targetScene.id,
                                fromPortId = targetScene.outPort.id,
                                toNodeId = sourceNode.id,
                                toPortId = sourcePort.id
                            )
                        )
                        markDirty()
                        showStatus("Scene-Block connection created!")
                    }
                    connectedToScene = true
                    break
                }
            }

            // 2. Check if dropped on a Node Port
            if (!connectedToScene) {
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

                            val sceneA = project.scenes.find { it.nodes.any { n -> n.id == fromNodeId } || it.id == fromNodeId }
                            val sceneB = project.scenes.find { it.nodes.any { n -> n.id == toNodeId } || it.id == toNodeId }

                            val targetConnList: MutableList<ConnectionData> = when {
                                editingConstructionNode != null -> editingConstructionNode!!.innerConnections
                                sceneA != null && sceneA.id == sceneB?.id -> sceneA.connections
                                else -> project.sceneConnections
                            }

                            val exists = targetConnList.any {
                                it.fromNodeId == fromNodeId && it.fromPortId == fromPortId &&
                                it.toNodeId == toNodeId && it.toPortId == toPortId
                            }
                            if (!exists) {
                                targetConnList.add(
                                    ConnectionData(
                                        fromNodeId = fromNodeId,
                                        fromPortId = fromPortId,
                                        toNodeId = toNodeId,
                                        toPortId = toPortId
                                    )
                                )
                                markDirty()
                                showStatus("Connection created!")
                            }
                        }
                        break
                    }
                }
            }
        }

        connectingSourceNode = null
        connectingSourcePort = null
        connectingSourceType = null
        connectingSourceScene = null

        if (activeItemPickerModal != null) return activeItemPickerModal!!.mouseReleased(mouseX, mouseY, button)
        if (activeResourcePickerModal != null) return activeResourcePickerModal!!.mouseReleased(mouseX, mouseY, button)
        if (activeEntityConfigModal != null) return activeEntityConfigModal!!.mouseReleased(mouseX, mouseY, button)
        if (activeMetadataModal != null) return activeMetadataModal!!.mouseReleased(mouseX, mouseY, button)

        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (showExitConfirmModal) return true
        if (activeDocModal?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
        if (activeDocModal != null) return true

        if (activePokemonConfigModal?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
        if (activeItemPickerModal?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
        if (activeResourcePickerModal?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
        if (activeEntityConfigModal?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
        if (activeAnimationSelectorModal?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
        if (activeTextureSelectorModal?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
        if (activeSaveProfileModal?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
        if (activeAIDialogueModal?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
        if (activeCoordinateModal?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
        if (activeMetadataModal?.mouseScrolled(mouseX, mouseY, scrollY) == true) return true
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
        if (showExitConfirmModal) return true
        if (activeDocModal != null) {
            activeDocModal?.charTyped(codePoint, modifiers)
            return true
        }
        if (activeVariableModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeMetadataModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeVarSelectorModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeActionTriggerPickerModal?.charTyped(codePoint, modifiers) == true) return true
        if (activePokemonConfigModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeResourcePickerModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeItemPickerModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeEntityConfigModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeAnimationSelectorModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeTextureSelectorModal != null) {
            activeTextureSelectorModal?.charTyped(codePoint, modifiers)
            return true
        }
        if (activeSaveProfileModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeAIDialogueModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeCoordinateModal?.charTyped(codePoint, modifiers) == true) return true
        if (activeInspector?.charTyped(codePoint, modifiers) == true) return true
        if (activeSceneInspector?.charTyped(codePoint, modifiers) == true) return true
        return super.charTyped(codePoint, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (activeDocModal != null) {
            if (activeDocModal?.keyPressed(keyCode, scanCode, modifiers) == true) return true
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

        if (activeResourcePickerModal != null) {
            if (activeResourcePickerModal?.keyPressed(keyCode, scanCode, modifiers) == true) return true
            if (keyCode == 256) { activeResourcePickerModal = null; return true }
            return true
        }

        if (activeItemPickerModal != null) {
            if (activeItemPickerModal?.keyPressed(keyCode, scanCode, modifiers) == true) return true
            if (keyCode == 256) { activeItemPickerModal = null; return true }
            return true
        }

        if (activeEntityConfigModal != null) {
            if (activeEntityConfigModal?.keyPressed(keyCode, scanCode, modifiers) == true) return true
            if (keyCode == 256) { activeEntityConfigModal = null; return true }
            return true
        }

        if (activeAnimationSelectorModal != null) {
            if (activeAnimationSelectorModal?.keyPressed(keyCode, scanCode, modifiers) == true) return true
            if (keyCode == 256) { activeAnimationSelectorModal = null; return true }
            return true
        }

        if (activeTextureSelectorModal != null) {
            if (activeTextureSelectorModal?.keyPressed(keyCode, scanCode, modifiers) == true) return true
            if (keyCode == 256) { activeTextureSelectorModal = null; return true }
            return true
        }

        if (activeSaveProfileModal != null) {
            if (activeSaveProfileModal?.keyPressed(keyCode, scanCode, modifiers) == true) return true
            if (keyCode == 256) { activeSaveProfileModal = null; return true }
            return true
        }

        if (activeAIDialogueModal != null) {
            if (activeAIDialogueModal?.keyPressed(keyCode, scanCode, modifiers) == true) return true
            if (keyCode == 256) { activeAIDialogueModal = null; return true }
            return true
        }

        if (activeCoordinateModal != null) {
            if (activeCoordinateModal?.keyPressed(keyCode, scanCode, modifiers) == true) return true
            if (keyCode == 256) { activeCoordinateModal = null; return true }
            return true
        }

        if (showExitConfirmModal) {
            if (keyCode == 256) { showExitConfirmModal = false; return true }
            return true
        }

        if (activeInspector?.keyPressed(keyCode, scanCode, modifiers) == true) return true
        if (activeSceneInspector?.keyPressed(keyCode, scanCode, modifiers) == true) return true

        if (keyCode == 32) { // Space key
            // Consume space so it doesn't trigger focused buttons or open menus
            return true
        }

        if (hasControlDown()) {
            if (keyCode == 67) { // Ctrl+C (Copy)
                val nodesToCopy = if (selectedWidgets.isNotEmpty()) selectedWidgets.map { it.node }.toList() else (selectedWidget?.let { listOf(it.node) } ?: emptyList())
                if (nodesToCopy.isNotEmpty()) {
                    val conns = if (editingConstructionNode != null) {
                        editingConstructionNode!!.innerConnections
                    } else {
                        val allConns = mutableListOf<ConnectionData>()
                        project.scenes.forEach { allConns.addAll(it.connections) }
                        allConns.addAll(project.sceneConnections)
                        allConns
                    }
                    BlockDataClipboard.copy(nodesToCopy, conns)
                    if (nodesToCopy.size == 1) {
                        showStatus("Copied 1 node: ${nodesToCopy.first().title}")
                    } else {
                        showStatus("Copied ${nodesToCopy.size} nodes.")
                    }
                    return true
                }
            }

            if (keyCode == 86) { // Ctrl+V (Paste)
                pasteCopiedNodesAt(currentMouseWorldX, currentMouseWorldY)
                return true
            }

            if (keyCode == 65) { // Ctrl+A (Select All)
                selectedWidgets.clear()
                nodeWidgets.forEach {
                    it.isSelected = true
                    selectedWidgets.add(it)
                }
                selectedWidget = null
                activeInspector = null
                showStatus("Selected all ${selectedWidgets.size} nodes.")
                return true
            }
        }

        if (keyCode == 261 || keyCode == 259) { // Delete or Backspace
            if (selectedWidgets.isNotEmpty()) {
                val toDelete = selectedWidgets.map { it.node }.toList()
                deleteNodes(toDelete)
                return true
            } else if (selectedWidget != null) {
                deleteNode(selectedWidget!!.node)
                return true
            }
        }

        if (keyCode == 256) {
            if (isSelectionModeActive) {
                isSelectionModeActive = false
                showStatus("Selection mode exited.")
                return true
            }
            if (isRangeTestSelectionMode) {
                isRangeTestSelectionMode = false
                rangeTestStartNode = null
                rangeTestEndNode = null
                rebuildNodeWidgets()
                showStatus("Range test mode cancelled.")
                return true
            }
            if (activePlacementNode != null) {
                activePlacementNode = null
                showStatus("Placement cancelled.")
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

        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false
}
