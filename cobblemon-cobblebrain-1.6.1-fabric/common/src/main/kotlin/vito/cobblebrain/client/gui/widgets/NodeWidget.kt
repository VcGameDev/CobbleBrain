package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.NodeType
import vito.cobblebrain.model.PortData
import vito.cobblebrain.model.PortType
import kotlin.math.sqrt

enum class NodeRectShape {
    HORIZONTAL_RECTANGLE, // Horizontal Rectangle (Default: 160x55)
    SQUARE,               // Square (Default: 90x90)
    VERTICAL_RECTANGLE    // Vertical Rectangle (Default: 130x100)
}

class NodeWidget(val node: NodeData) {

    var isSelected: Boolean = false
    var isDragging: Boolean = false

    // Range Test Selection Mini-Mode Highlights
    var isStartTestNode: Boolean = false
    var isEndTestNode: Boolean = false

    private val headerHeight = 20
    private val portRadius = 5.0

    fun getRectShape(): NodeRectShape {
        return when (node.nodeType) {
            NodeType.BEGIN_SCENE, NodeType.END_SCENE, NodeType.GATE,
            NodeType.BEGIN_CONSTRUCTION, NodeType.END_CONSTRUCTION,
            NodeType.LINK_SEND, NodeType.LINK_RECEIVE,
            NodeType.VARIABLE_GET -> NodeRectShape.HORIZONTAL_RECTANGLE

            NodeType.COMMENT, NodeType.VARIABLE_SET,
            NodeType.LOOP -> NodeRectShape.SQUARE

            NodeType.CONSTRUCTION, NodeType.CONDITION_NODE, NodeType.COMMAND_NODE, NodeType.TRIGGER,
            NodeType.DIALOGUE, NodeType.ACTION, NodeType.TIMER, NodeType.SAVE_STATE_NODE,
            NodeType.LOAD_STATE_NODE, NodeType.KEY_INPUT,
            NodeType.QUEST, NodeType.AUDIO, NodeType.TEXTURE -> NodeRectShape.VERTICAL_RECTANGLE
        }
    }

    // Harmonized Category Colors
    fun getHeaderColor(): Int {
        return when (node.nodeType) {
            // SECTION 1: Structure & Flow (Emerald Green & Teal Shades)
            NodeType.BEGIN_SCENE -> 0xFF2E7D32.toInt()  // Emerald Green
            NodeType.END_SCENE -> 0xFF1B5E20.toInt()    // Dark Green
            NodeType.BEGIN_CONSTRUCTION -> 0xFFD97706.toInt() // Amber Construction Start
            NodeType.END_CONSTRUCTION -> 0xFFB45309.toInt()   // Dark Amber Construction Finish
            NodeType.GATE -> 0xFF00838F.toInt()         // Synchronizer Teal
            NodeType.CONSTRUCTION -> 0xFF00695C.toInt()  // Sub-graph Dark Teal
            NodeType.LINK_SEND -> 0xFF009688.toInt()    // Transmitter Sea Green
            NodeType.LINK_RECEIVE -> 0xFF00796B.toInt() // Receiver Sea Green
            NodeType.LOOP -> 0xFF388E3C.toInt()         // Repeater Green
            NodeType.QUEST -> 0xFFFF8F00.toInt()        // Quest Amber Gold

            // SECTION 2: Variables & Decisions (Ocean Blue & Vibrant Cyan Shades)
            NodeType.VARIABLE_GET -> 0xFF0288D1.toInt() // Cyan Getter Blue
            NodeType.VARIABLE_SET -> 0xFF0277BD.toInt() // Ocean Setter Blue
            NodeType.CONDITION_NODE -> 0xFF1565C0.toInt() // Sapphire If/Else Blue
            NodeType.TRIGGER -> 0xFF0D47A1.toInt()      // Cobalt Trigger Blue

            // SECTION 3: Actions & Events (Red, Orange, Purple & Yellow Shades)
            NodeType.DIALOGUE -> 0xFFC62828.toInt()     // Bright Red Dialogue
            NodeType.ACTION -> 0xFFD32F2F.toInt()       // Crimson Action Red
            NodeType.COMMAND_NODE -> 0xFFD84315.toInt()  // Rust Orange Command
            NodeType.SAVE_STATE_NODE -> 0xFF4A148C.toInt() // Deep Purple Save Checkpoint
            NodeType.LOAD_STATE_NODE -> 0xFF311B92.toInt() // Deep Indigo Load Checkpoint
            NodeType.KEY_INPUT -> 0xFF0284C7.toInt()       // Electric Cyan Key Input / QTE
            NodeType.TIMER -> 0xFF8E24AA.toInt()        // Vibrant Purple Timer
            NodeType.COMMENT -> 0xFFFBC02D.toInt()     // Note Yellow
            NodeType.AUDIO -> 0xFF6A1B9A.toInt()       // Dark Purple Audio
            NodeType.TEXTURE -> 0xFF9333EA.toInt()     // Vibrant Magenta/Purple Texture
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

    fun getDefaultWidth(): Double {
        return when (getRectShape()) {
            NodeRectShape.HORIZONTAL_RECTANGLE -> 160.0
            NodeRectShape.SQUARE -> 90.0
            NodeRectShape.VERTICAL_RECTANGLE -> 130.0
        }
    }

    fun getDefaultHeight(): Double {
        return when {
            node.nodeType == NodeType.CONDITION_NODE -> maxOf(100.0, 30.0 + node.outputs.size * 22.0)
            node.nodeType == NodeType.COMMAND_NODE -> 100.0
            getRectShape() == NodeRectShape.HORIZONTAL_RECTANGLE -> 55.0
            getRectShape() == NodeRectShape.SQUARE -> 90.0
            else -> 100.0
        }
    }

    fun isWorldPosOnResizeHandle(worldX: Double, worldY: Double): Boolean {
        val rx = node.x + node.width - 12.0
        val ry = node.y + node.height - 12.0
        return worldX >= rx && worldX <= node.x + node.width + 4.0 &&
               worldY >= ry && worldY <= node.y + node.height + 4.0
    }
    fun getErrorTooltipAt(worldX: Double, worldY: Double, storyId: String = ""): String? = getTooltipAtWorldPos(worldX, worldY, storyId)

    fun getTooltipAtWorldPos(worldX: Double, worldY: Double, storyId: String = ""): String? {
        val inBounds = worldX >= node.x - 4.0 && worldX <= node.x + node.width + 4.0 &&
                       worldY >= node.y - 4.0 && worldY <= node.y + node.height + 4.0
        if (!inBounds) return null

        val sessionState = vito.cobblebrain.engine.StoryDebugger.activeSessionState
        val isStoryActive = sessionState.isActive || vito.cobblebrain.engine.StoryExecutor.activeStories.containsKey(storyId)
        if (!isStoryActive) return null

        val status = vito.cobblebrain.engine.StoryDebugger.getNodeStatus(storyId, node.id)
        val errMsg = vito.cobblebrain.engine.StoryDebugger.getNodeErrorMessage(storyId, node.id)
        return when (status) {
            vito.cobblebrain.engine.NodeExecutionStatus.FAILED -> "§c❌ Block Execution Error: §f${errMsg ?: "Unknown runtime failure"}"
            vito.cobblebrain.engine.NodeExecutionStatus.FALLBACK_TRIGGERED -> "§6⚠ Fallback Triggered: §f${errMsg ?: "Fallback path executed"}"
            vito.cobblebrain.engine.NodeExecutionStatus.RUNNING -> "§b⚡ Block currently running..."
            else -> null
        }
    }

    fun render(
        guiGraphics: GuiGraphics,
        font: Font,
        hoveredPort: PortData? = null,
        isModalOpen: Boolean = false,
        isCollidingWithOverlay: Boolean = false,
        storyId: String = ""
    ) {
        if (node.width <= 0.0) {
            node.width = getDefaultWidth()
        }
        if (node.height <= 0.0) {
            node.height = getDefaultHeight()
        }

        node.width = node.width.coerceAtLeast(120.0)
        node.height = node.height.coerceAtLeast(40.0)

        val x = node.x.toInt()
        val y = node.y.toInt()
        val w = node.width.toInt()
        val h = node.height.toInt()

        val shouldHideText = isModalOpen || isCollidingWithOverlay

        // Post-It Comment Block
        if (node.nodeType == NodeType.COMMENT) {
            val noteBorder = if (isSelected) 0xFFFFD700.toInt() else 0xFFFBC02D.toInt()
            guiGraphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, noteBorder)
            guiGraphics.fill(x, y, x + w, y + h, 0xEE222016.toInt())
            guiGraphics.fill(x, y, x + w, y + 16, 0xFF3D3820.toInt())

            if (!shouldHideText) {
                guiGraphics.drawString(font, "📝 ${node.title}", x + 6, y + 4, 0xFFFBC02D.toInt(), false)
                val commentText = font.plainSubstrByWidth(node.content.ifBlank { "Your note..." }, w - 12)
                guiGraphics.drawString(font, commentText, x + 6, y + 24, 0xFFE0E0D0.toInt(), false)
            }

            // Comment Resize Handle
            val rx = x + w - 8
            val ry = y + h - 8
            guiGraphics.fill(rx, ry, rx + 8, ry + 8, noteBorder)
            guiGraphics.fill(rx + 1, ry + 1, rx + 7, ry + 7, 0xEE222016.toInt())
            guiGraphics.fill(rx + 2, ry + 5, rx + 6, ry + 7, noteBorder)
            guiGraphics.fill(rx + 4, ry + 3, rx + 6, ry + 5, noteBorder)
            return
        }

        // Diagnostic Debug Status & Active Execution Detection
        val sessionState = vito.cobblebrain.engine.StoryDebugger.activeSessionState
        val isStoryActive = sessionState.isActive || vito.cobblebrain.engine.StoryExecutor.activeStories.containsKey(storyId)
        val rawDebugStatus = vito.cobblebrain.engine.StoryDebugger.getNodeStatus(storyId, node.id)
        val debugStatus = if (isStoryActive) rawDebugStatus else vito.cobblebrain.engine.NodeExecutionStatus.IDLE
        val debugErrMsg = vito.cobblebrain.engine.StoryDebugger.getNodeErrorMessage(storyId, node.id)

        // Only highlight live execution when story test is actively running and this node is current
        val isExecutingLive = isStoryActive && (sessionState.activeNodeId == node.id || debugStatus == vito.cobblebrain.engine.NodeExecutionStatus.RUNNING)

        // Pulsing Execution Outer Glow (Only while actively executing)
        if (isExecutingLive) {
            val pulse = ((kotlin.math.sin(System.currentTimeMillis() / 150.0) + 1.0) * 0.5 * 180 + 75).toInt().coerceIn(60, 255)
            val glowColor = (pulse shl 24) or 0x0038BDF8
            guiGraphics.fill(x - 5, y - 5, x + w + 5, y + h + 5, glowColor)
            guiGraphics.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF38BDF8.toInt())
        }

        val borderColor = when {
            isExecutingLive -> 0xFF38BDF8.toInt()
            debugStatus == vito.cobblebrain.engine.NodeExecutionStatus.FAILED -> 0xFFFF3333.toInt()
            debugStatus == vito.cobblebrain.engine.NodeExecutionStatus.FALLBACK_TRIGGERED -> 0xFFF59E0B.toInt()
            isSelected -> 0xFFFFD700.toInt()
            isStartTestNode -> 0xFF00FFCC.toInt()
            isEndTestNode -> 0xFFFF5555.toInt()
            isStoryActive && debugStatus == vito.cobblebrain.engine.NodeExecutionStatus.SUCCESS -> 0xFF225533.toInt() // Dim muted green indicating past execution during active test
            else -> 0xFF333338.toInt()
        }

        val headerColor = getHeaderColor()

        // Extra thick outline for active running/error states
        val borderThickness = if (isExecutingLive || debugStatus == vito.cobblebrain.engine.NodeExecutionStatus.FAILED) 3 else 2
        guiGraphics.fill(x - borderThickness, y - borderThickness, x + w + borderThickness, y + h + borderThickness, borderColor)
        guiGraphics.fill(x, y, x + w, y + h, 0xFF1E1E24.toInt())
        guiGraphics.fill(x, y, x + w, y + headerHeight, headerColor)

        // Diagnostic Top-Right Badges
        if (debugStatus == vito.cobblebrain.engine.NodeExecutionStatus.FAILED) {
            val bx = x + w - 14
            val by = y - 6
            guiGraphics.fill(bx, by, bx + 16, by + 14, 0xFFDC2626.toInt())
            guiGraphics.fill(bx, by, bx + 16, by + 1, 0xFFFFFFFF.toInt())
            guiGraphics.fill(bx, by, bx + 1, by + 14, 0xFFFFFFFF.toInt())
            guiGraphics.fill(bx + 15, by, bx + 16, by + 14, 0xFFFFFFFF.toInt())
            guiGraphics.fill(bx, by + 13, bx + 16, by + 14, 0xFFFFFFFF.toInt())
            guiGraphics.drawString(font, "!", bx + 5, by + 3, 0xFFFFFFFF.toInt(), true)
        } else if (debugStatus == vito.cobblebrain.engine.NodeExecutionStatus.FALLBACK_TRIGGERED) {
            val bx = x + w - 14
            val by = y - 6
            guiGraphics.fill(bx, by, bx + 16, by + 14, 0xFFD97706.toInt())
            guiGraphics.drawString(font, "⚠", bx + 3, by + 3, 0xFFFFFFFF.toInt(), false)
        } else if (isExecutingLive) {
            val bx = x + w - 14
            val by = y - 6
            guiGraphics.fill(bx, by, bx + 16, by + 14, 0xFF0284C7.toInt())
            guiGraphics.drawString(font, "⚡", bx + 3, by + 3, 0xFFFFFFFF.toInt(), false)
        }

        // Timing Delays Badge (Pre-Delay IN & Post-Delay OUT)
        val totalDelayTicks = node.preDelayTicks + node.postDelayTicks
        if (totalDelayTicks > 0) {
            val totalSec = totalDelayTicks / 20.0
            val formattedTime = if (totalDelayTicks % 20 == 0) "${totalDelayTicks / 20}s" else "${"%.1f".format(totalSec)}s"
            val badgeText = "⏱ $formattedTime"
            val badgeW = font.width(badgeText) + 6
            val badgeH = 11
            val badgeX = x + (w - badgeW) / 2
            val badgeY = y - 6
            guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xEE0F172A.toInt())
            guiGraphics.fill(badgeX - 1, badgeY, badgeX, badgeY + badgeH, 0xFF38BDF8.toInt())
            guiGraphics.fill(badgeX + badgeW, badgeY, badgeX + badgeW + 1, badgeY + badgeH, 0xFF38BDF8.toInt())
            guiGraphics.fill(badgeX, badgeY - 1, badgeX + badgeW, badgeY, 0xFF38BDF8.toInt())
            guiGraphics.fill(badgeX, badgeY + badgeH, badgeX + badgeW, badgeY + badgeH + 1, 0xFF38BDF8.toInt())
            guiGraphics.drawString(font, badgeText, badgeX + 3, badgeY + 2, 0xFF38BDF8.toInt(), false)
        }

        // Hide node text ONLY if colliding with overlay menu/inspector
        if (!shouldHideText) {
            val isBoundToScene = !node.parentSceneId.isNullOrEmpty()
            val hasBadge = debugStatus != vito.cobblebrain.engine.NodeExecutionStatus.IDLE
            val maxTitleW = if (isBoundToScene || hasBadge) w - 28 else w - 8
            val titleText = font.plainSubstrByWidth(node.title, maxTitleW)
            guiGraphics.drawString(font, titleText, x + 6, y + 6, 0xFFFFFFFF.toInt(), true)

            if (isBoundToScene) {
                guiGraphics.drawString(font, "🎬", x + w - 16, y + 5, 0xFF00FFCC.toInt(), false)
            }

            val contentY = y + headerHeight + 4
            val rawSummary = when (node.nodeType) {
                NodeType.BEGIN_SCENE -> "🟢 Scene Start"
                NodeType.END_SCENE -> "🛑 Finish Scene"
                NodeType.BEGIN_CONSTRUCTION -> "🏗️ Build: ${node.params["constructionName"] ?: "Construction"} (${node.params["buildSpeedMode"] ?: "INSTANT"})"
                NodeType.END_CONSTRUCTION -> "🏁 End Build (Sound: ${if (node.params["playCompletionSound"] == "true") "Yes" else "No"})"
                NodeType.TRIGGER -> {
                    val condMode = if (node.params["triggerCondition"] == "IF_NOT") "IF NOT" else "IF"
                    val trigType = node.params["triggerType"] ?: "START"
                    "$condMode: $trigType"
                }
                NodeType.DIALOGUE -> if (node.params["useAi"] == "true") "🤖 AI Prompt: ${node.content.ifBlank { "Prompt..." }}" else node.content.ifBlank { "No dialogue..." }
                NodeType.ACTION -> {
                    val actionType = node.params["actionSubtype"] ?: "MESSAGE"
                    when (actionType) {
                        "LOOK_AT", "LOOK_AT_BLOCK" -> {
                            val op = if (node.params["operationMode"] == "RESET_LOOK") "Reset AI" else "Focus"
                            val subj = if (node.params["subjectType"] == "NPC_TAG") (node.params["subjectIdentifier"]?.ifBlank { "NPC" } ?: "NPC") else "Slot ${node.params["subjectIdentifier"] ?: node.params["targetIdentifier"] ?: "1"}"
                            "👀 Look: $op ($subj)"
                        }
                        "MOVE_TO_BLOCK", "NAVIGATE_ENTITY", "MOVE_TO", "PATHFIND_ENTITY" -> {
                            val subj = if (node.params["subjectType"] == "NPC_TAG") (node.params["subjectIdentifier"]?.ifBlank { "NPC" } ?: "NPC") else "Slot ${node.params["subjectIdentifier"] ?: "1"}"
                            val dest = when (node.params["targetDestinationType"]) {
                                "PLAYER" -> "Player"
                                "ENTITY_TAG" -> "@${node.params["destinationIdentifier"]?.ifBlank { "mob" } ?: "mob"}"
                                "WORLD_BLOCK_TAG" -> "@block:${node.params["destinationIdentifier"]?.ifBlank { "block" } ?: "block"}"
                                else -> node.params["destinationIdentifier"]?.ifBlank { node.params["coordinates"] ?: "~ ~ ~" } ?: "~ ~ ~"
                            }
                            val spd = node.params["speedMode"] ?: "WALK"
                            "🚶 Move: $subj ➔ $dest ($spd)"
                        }
                        "TAG_BLOCK", "MANAGE_TAG", "TAG" -> {
                            val op = when (node.params["operation"]) {
                                "REMOVE_TAG" -> "Remove"
                                "CLEAR_TAGS" -> "Clear"
                                else -> "Add"
                            }
                            val tag = node.params["tagName"]?.ifBlank { "tag" } ?: "tag"
                            val cat = when (node.params["targetCategory"]) {
                                "WORLD_BLOCK" -> "Block"
                                "PLAYER" -> "Player"
                                else -> "Mob"
                            }
                            "🏷️ Tag: $op '$tag' ($cat)"
                        }
                        "VAR_MODIFY" -> "Var: ${node.params["varKey"] ?: "var"} ${node.params["varOp"] ?: "="} ${node.params["varValue"] ?: "1"}"
                        "TELEPORT" -> {
                            val coords = node.params["coordinates"]?.ifBlank { node.params["destTag"]?.ifBlank { "${node.params["destX"] ?: "0"} ${node.params["destY"] ?: "64"} ${node.params["destZ"] ?: "0"}" } ?: "${node.params["destX"] ?: "0"} ${node.params["destY"] ?: "64"} ${node.params["destZ"] ?: "0"}" } ?: "${node.params["destX"] ?: "0"} ${node.params["destY"] ?: "64"} ${node.params["destZ"] ?: "0"}"
                            "TP: $coords"
                        }
                        "SPAWN", "SPAWN_COBBLEMON" -> "Spawn: ${node.params["species"] ?: "Pikachu"} Lvl ${node.params["level"] ?: "5"}"
                        "SOUND" -> "Sound: ${node.params["soundId"] ?: "click"}"
                        else -> "Msg: ${node.content}"
                    }
                }
                NodeType.TIMER -> "Timer: ${node.params["timerSeconds"] ?: "5"}s"
                NodeType.CONDITION_NODE -> "If: ${node.params["varKey_0"] ?: node.params["varKey"] ?: "var"} ${node.params["varOp_0"] ?: node.params["varOp"] ?: "=="} ${node.params["varValue_0"] ?: node.params["varValue"] ?: "true"}"
                NodeType.COMMAND_NODE -> {
                    val firstCmd = node.content.lines().firstOrNull { it.isNotBlank() } ?: (node.params["commands"]?.lines()?.firstOrNull { it.isNotBlank() } ?: "say Hello")
                    "Cmd: $firstCmd"
                }
                NodeType.CONSTRUCTION -> "Construction (${node.innerNodes.size} nodes)"
                NodeType.END_SCENE -> "🛑 Finish Scene"
                NodeType.GATE -> "⚡ GATE Gate (${node.inputs.size} in)"
                NodeType.LINK_SEND -> "📡 Link: [${node.params["channelTag"] ?: "channel_1"}]"
                NodeType.LINK_RECEIVE -> "📡 Rec: [${node.params["channelTag"] ?: "channel_1"}]"
                NodeType.LOOP -> {
                    val mode = if (node.params["loopMode"] == "TIME") "Time" else "Count"
                    val detail = if (node.params["loopMode"] == "TIME") "${node.params["loopIntervalSec"] ?: "1.0"}s" else "${node.params["loopCount"] ?: "5"}x"
                    "🔄 Loop ($mode: $detail)"
                }
                NodeType.COMMENT -> "📝 ${node.content}"
                NodeType.VARIABLE_GET -> "🔹 Var: [${node.params["varKey"] ?: "new_var"}]"
                NodeType.VARIABLE_SET -> "✏️ Set: ${node.params["varKey"] ?: "new_var"} ${node.params["varOp"] ?: "="} ${node.params["varValue"] ?: "1"}"
                NodeType.QUEST -> "🏆 Quest: ${node.params["questTitle"] ?: node.title}"
                NodeType.AUDIO -> "🎵 Audio: ${node.params["audioId"] ?: "sound"}"
                NodeType.SAVE_STATE_NODE -> "💾 Save: [${node.params["profileId"] ?: "checkpoint_1"}]"
                NodeType.LOAD_STATE_NODE -> "🔄 Load: [${node.params["profileId"] ?: "checkpoint_1"}]"
                NodeType.KEY_INPUT -> {
                    val isStandalone = node.params["triggerMode"]?.equals("STANDALONE", true) ?: (node.params["requireInputSignal"] == "false" || node.inputs.none { it.type == PortType.INPUT })
                    val modePrefix = if (isStandalone) "⚡ [${node.params["targetKey"] ?: "F"}] Standalone" else "🔗 [${node.params["targetKey"] ?: "F"}] Flow"
                    "⌨️ $modePrefix (${node.params["inputMode"] ?: "PRESS"})"
                }
                NodeType.TEXTURE -> {
                    val target = if (node.params["targetType"] == "PLAYER_POKEMON") "Slot ${node.params["pokemonSlot"] ?: node.params["targetIdentifier"] ?: "1"}" else (node.params["targetIdentifier"] ?: "NPC")
                    val tex = node.params["textureName"]?.ifBlank { "None" } ?: "None"
                    val mode = if (node.params["textureMode"] == "CLEAR_TEXTURE") "Reset" else "Set: $tex"
                    "🎨 $mode ($target)"
                }
            }

            val previewText = font.plainSubstrByWidth(rawSummary, w - 12)
            guiGraphics.drawString(font, previewText, x + 6, contentY, 0xFFA0A0A0.toInt(), false)

            if (isStartTestNode) {
                guiGraphics.drawString(font, "[▶ START]", x + 6, y + h - 12, 0xFF00FFCC.toInt(), false)
            } else if (isEndTestNode) {
                guiGraphics.drawString(font, "[🛑 END]", x + 6, y + h - 12, 0xFFFF5555.toInt(), false)
            }
        }

        // Render Input Ports
        node.inputs.forEachIndexed { idx, port ->
            val (px, py) = getInputPortWorldPos(idx)
            val ipx = px.toInt()
            val ipy = py.toInt()
            val r = portRadius.toInt()
            val color = if (hoveredPort?.id == port.id) {
                0xFF55FF55.toInt()
            } else if (port.id == "BUILD_IN" || port.name.contains("Build", true)) {
                0xFFF59E0B.toInt()
            } else {
                0xFF4CAF50.toInt()
            }

            guiGraphics.fill(ipx - r, ipy - r, ipx + r, ipy + r, color)
            guiGraphics.fill(ipx - r + 1, ipy - r + 1, ipx + r - 1, ipy + r - 1, 0xFF1E1E24.toInt())
            guiGraphics.fill(ipx - r + 2, ipy - r + 2, ipx + r - 2, ipy + r - 2, color)

            if (!shouldHideText) {
                val pName = font.plainSubstrByWidth(port.name, 40)
                guiGraphics.drawString(font, pName, ipx + r + 3, ipy - 4, 0xFFCCCCCC.toInt(), false)
            }
        }

        // Render Output Ports
        node.outputs.forEachIndexed { idx, port ->
            val (px, py) = getOutputPortWorldPos(idx)
            val opx = px.toInt()
            val opy = py.toInt()
            val r = portRadius.toInt()
            val color = if (hoveredPort?.id == port.id) {
                0xFFFFB74D.toInt()
            } else when {
                port.id == "OUT_COND" || port.name.contains("Cond", true) -> if (node.params["condOutMode"]?.equals("LISTENER", true) == true) 0xFF00E5FF.toInt() else 0xFF38BDF8.toInt()
                port.id == "BUILD_OUT" || port.name.contains("Build", true) -> 0xFFF59E0B.toInt()
                port.id == "ON_CHANGED_OUT" || port.name.contains("Changed", true) -> 0xFF00E5FF.toInt()
                port.id == "OUT_IF" || port.name.equals("SE", true) || port.name.equals("IF", true) -> 0xFF4CAF50.toInt()
                port.name.contains("SENÃO SE", true) || port.name.contains("SENAO SE", true) || port.name.contains("ELSE IF", true) -> 0xFFFFB74D.toInt()
                port.id == "OUT_ELSE" || port.name.equals("SENÃO", true) || port.name.equals("SENAO", true) || port.name.equals("ELSE", true) -> 0xFFF44336.toInt()
                port.id == "OUT_PULSE" || port.name.contains("Pulse", true) -> 0xFFF59E0B.toInt()
                port.id == "OUT_RELEASE" || port.name.contains("Release", true) -> 0xFF10B981.toInt()
                port.id == "OUT_TIMEOUT" || port.name.contains("Timeout", true) -> 0xFFEF4444.toInt()
                port.name.contains("Success", true) || port.id.contains("SUCCESS", true) -> 0xFF4CAF50.toInt()
                port.name.contains("Fail", true) || port.id.contains("FAIL", true) -> 0xFFF44336.toInt()
                port.name.contains("Progress", true) || port.id.contains("PROGRESS", true) -> 0xFFFFEB3B.toInt()
                else -> 0xFFFF9800.toInt()
            }

            guiGraphics.fill(opx - r, opy - r, opx + r, opy + r, color)
            guiGraphics.fill(opx - r + 1, opy - r + 1, opx + r - 1, opy + r - 1, 0xFF1E1E24.toInt())
            guiGraphics.fill(opx - r + 2, opy - r + 2, opx + r - 2, opy + r - 2, color)

            if (!shouldHideText) {
                val pName = font.plainSubstrByWidth(port.name, 50)
                val textW = font.width(pName)
                guiGraphics.drawString(font, pName, opx - r - 3 - textW, opy - 4, 0xFFCCCCCC.toInt(), false)
            }
        }

        // Render Node Resize Handle at Bottom-Right Corner (8x8 px)
        val rx = x + w - 8
        val ry = y + h - 8
        val rw = 8
        val rh = 8
        val handleColor = if (isSelected) 0xFFFFD700.toInt() else 0xFF666677.toInt()

        guiGraphics.fill(rx, ry, rx + rw, ry + rh, handleColor)
        guiGraphics.fill(rx + 1, ry + 1, rx + rw - 1, ry + rh - 1, 0xFF18181C.toInt())
        guiGraphics.fill(rx + 2, ry + 5, rx + 6, ry + 7, handleColor)
        guiGraphics.fill(rx + 4, ry + 3, rx + 6, ry + 5, handleColor)
    }
}
