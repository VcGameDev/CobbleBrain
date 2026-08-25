package vito.cobblebrain.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import vito.cobblebrain.engine.NodeExecutionStatus
import vito.cobblebrain.engine.StoryDebugLogEntry
import vito.cobblebrain.engine.StoryDebugger
import vito.cobblebrain.model.NodeType
import vito.cobblebrain.model.StoryProject
import vito.cobblebrain.model.StorySerializer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RuntimeDebugCategoryFilter {
    ALL,
    ACTIONS,
    VARIABLES,
    AI,
    ERRORS_ONLY
}

class StoryRuntimeDebugScreen(
    val parentScreen: Screen? = null,
    val initialStoryId: String? = null
) : Screen(Component.literal("Story Runtime Debugger")) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
    private val fullTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    private var activeFilter = RuntimeDebugCategoryFilter.ALL
    private var isAutoScrollPaused = false
    private var logScrollOffset = 0f
    private var varScrollOffset = 0f

    private lateinit var searchBox: EditBox

    private var actionToast: String? = null
    private var actionToastTimer = 0

    override fun init() {
        super.init()
        clearWidgets()

        val searchW = 150.coerceAtMost((width / 3).coerceAtLeast(100))
        val searchX = width - searchW - 10
        val searchY = 6

        searchBox = EditBox(font, searchX, searchY, searchW, 16, Component.literal("Search"))
        searchBox.setMaxLength(100)
        searchBox.setHint(Component.literal("§8🔍 Filter logs..."))
        searchBox.setEditable(true)
        searchBox.active = true
        searchBox.setResponder { logScrollOffset = 0f }
        addRenderableWidget(searchBox)

        // Footer buttons
        val btnY = height - 26
        val btnH = 20

        val bPauseW = 120.coerceAtMost(width / 4)
        val bPause = Button.builder(Component.literal(if (isAutoScrollPaused) "▶ Resume Auto-Scroll" else "⏸ Pause Auto-Scroll")) { btn ->
            isAutoScrollPaused = !isAutoScrollPaused
            btn.message = Component.literal(if (isAutoScrollPaused) "▶ Resume Auto-Scroll" else "⏸ Pause Auto-Scroll")
        }.bounds(10, btnY, bPauseW, btnH).build()
        addRenderableWidget(bPause)

        val bClearW = 85.coerceAtMost(width / 5)
        val bClearX = 10 + bPauseW + 6
        val bClear = Button.builder(Component.literal("🗑 Clear Logs")) {
            val targetId = initialStoryId?.takeIf { it.isNotBlank() }
            StoryDebugger.clearLogs(targetId)
            logScrollOffset = 0f
            showToast("Logs cleared.")
        }.bounds(bClearX, btnY, bClearW, btnH).build()
        addRenderableWidget(bClear)

        val bExportW = 135.coerceAtMost(width / 3)
        val bExportX = bClearX + bClearW + 6
        val bExport = Button.builder(Component.literal("💾 Export Report (.txt)")) {
            exportReport()
        }.bounds(bExportX, btnY, bExportW, btnH).build()
        addRenderableWidget(bExport)

        val bCloseW = 75
        val bClose = Button.builder(Component.literal("Close (Esc)")) {
            onClose()
        }.bounds(width - bCloseW - 10, btnY, bCloseW, btnH).build()
        addRenderableWidget(bClose)
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Empty no-op so super.render() does not wipe out screen contents with background fill
    }

    override fun onClose() {
        val mc = minecraft ?: Minecraft.getInstance()
        if (parentScreen != null) {
            mc.setScreen(parentScreen)
        } else {
            mc.setScreen(null)
        }
    }

    private fun showToast(msg: String) {
        actionToast = msg
        actionToastTimer = 60
    }

    private fun getFilteredLogs(): List<StoryDebugLogEntry> {
        val query = if (::searchBox.isInitialized) searchBox.value.trim().lowercase() else ""
        val allLogs = StoryDebugger.logs

        return allLogs.filter { log ->
            val matchCategory = when (activeFilter) {
                RuntimeDebugCategoryFilter.ALL -> true
                RuntimeDebugCategoryFilter.ACTIONS -> log.blockType in listOf(NodeType.ACTION, NodeType.TEXTURE, NodeType.AUDIO, NodeType.COMMAND_NODE, NodeType.QUEST) || log.getLogBadge() == "ACTION"
                RuntimeDebugCategoryFilter.VARIABLES -> log.blockType in listOf(NodeType.VARIABLE_SET, NodeType.VARIABLE_GET) || log.getLogBadge() == "SET_VAR"
                RuntimeDebugCategoryFilter.AI -> log.getLogBadge() == "AI_CALL" || log.message.contains("AI", ignoreCase = true)
                RuntimeDebugCategoryFilter.ERRORS_ONLY -> log.level.equals("ERROR", true) || log.status == NodeExecutionStatus.FAILED || log.getLogBadge() in listOf("ERROR", "WARN", "FALLBACK")
            }
            if (!matchCategory) return@filter false

            if (query.isBlank()) return@filter true

            log.message.lowercase().contains(query) ||
            log.blockId.lowercase().contains(query) ||
            log.blockType.name.lowercase().contains(query) ||
            log.getLogBadge().lowercase().contains(query) ||
            (log.details?.lowercase()?.contains(query) == true)
        }
    }

    private fun inferVariableType(valStr: String): String {
        val t = valStr.trim()
        if (t.equals("true", ignoreCase = true) || t.equals("false", ignoreCase = true)) return "Boolean"
        if (t.toIntOrNull() != null) return "Int"
        if (t.toDoubleOrNull() != null) return "Float"
        if (t.startsWith("[") && t.endsWith("]")) return "List"
        return "String"
    }

    private fun cleanBlockId(raw: String): String {
        return if (raw.length > 8 && raw.contains("-")) {
            raw.substring(0, 8)
        } else raw
    }

    private fun jumpToNode(storyId: String, blockId: String) {
        val mc = minecraft ?: Minecraft.getInstance()
        val safeStoryId = storyId.ifBlank { initialStoryId ?: "default_story" }

        if (parentScreen is StoryEditorScreen && (parentScreen.project.id.equals(safeStoryId, true) || parentScreen.project.name.equals(safeStoryId, true))) {
            mc.setScreen(parentScreen)
            parentScreen.focusOnNode(blockId)
            return
        }

        val loadedProject: StoryProject? = StorySerializer.loadByName(safeStoryId)
            ?: StorySerializer.loadByName("${safeStoryId}.json")
            ?: vito.cobblebrain.engine.StoryExecutor.activeStories.values.find { it.storyId.equals(safeStoryId, true) || it.project.id.equals(safeStoryId, true) }?.project
            ?: StoryProject(id = safeStoryId, name = safeStoryId)

        val editor = StoryEditorScreen(parentScreen = this, initialProject = loadedProject)
        mc.setScreen(editor)
        editor.focusOnNode(blockId)
    }

    private fun exportReport() {
        val mc = minecraft ?: Minecraft.getInstance()
        val player = mc.player
        val now = System.currentTimeMillis()
        val session = StoryDebugger.activeSessionState

        val sb = StringBuilder()
        sb.appendLine("=======================================================")
        sb.appendLine("COBBLEBRAIN STORY RUNTIME DIAGNOSTIC REPORT")
        sb.appendLine("Generated: ${fullTimeFormat.format(Date(now))}")
        sb.appendLine("=======================================================")
        sb.appendLine()
        sb.appendLine("[SYSTEM INFO]")
        sb.appendLine("Minecraft Version: ${mc.launchedVersion}")
        sb.appendLine("Player: ${player?.name?.string ?: "Unknown"}")
        sb.appendLine("OS: ${System.getProperty("os.name")}")
        sb.appendLine()
        sb.appendLine("[SESSION OVERVIEW]")
        sb.appendLine("Pack Name: ${session.packName.ifBlank { "CobbleBrain Story" }}")
        sb.appendLine("Story ID: ${session.storyId.ifBlank { initialStoryId ?: "default_story" }}")
        sb.appendLine("Active Scene: ${session.sceneName.ifBlank { "Main Scene" }}")
        sb.appendLine("Active Node: ${session.activeNodeType} #${session.activeNodeId}")
        sb.appendLine("Target Entity: ${session.targetEntityName.ifBlank { session.targetEntityTag.ifBlank { "None" } }}")
        sb.appendLine("Status: ${if (session.isActive) "ACTIVE / RUNNING" else "INACTIVE / STANDBY"}")
        sb.appendLine()
        sb.appendLine("[ACTIVE STORY VARIABLES (${session.variables.size})]")
        if (session.variables.isEmpty()) {
            sb.appendLine("  (No active variables registered)")
        } else {
            session.variables.forEach { (k, v) ->
                sb.appendLine("  • $k [${inferVariableType(v)}] = $v")
            }
        }
        sb.appendLine()
        sb.appendLine("[EXECUTION HISTORY LOGS (${StoryDebugger.logs.size})]")
        StoryDebugger.logs.forEach { entry ->
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date(entry.timestamp))
            val badge = entry.getLogBadge()
            sb.appendLine("  [$time] [$badge] [${entry.blockType.name} #${entry.blockId}] ${entry.message}")
            if (!entry.details.isNullOrBlank()) {
                sb.appendLine("    Details: ${entry.details.replace("\n", "\n    ")}")
            }
        }
        sb.appendLine("=======================================================")

        try {
            val debugDir = File(mc.gameDirectory, "cobblebrain/debug")
            if (!debugDir.exists()) debugDir.mkdirs()

            val file = File(debugDir, "report_${now}.txt")
            file.writeText(sb.toString())

            val clickStyle = Style.EMPTY.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_FILE, file.absolutePath)).withUnderlined(true)
            val msg = Component.literal("§a✅ Diagnostic report saved to: §f")
                .append(Component.literal(file.name).setStyle(clickStyle))

            player?.displayClientMessage(msg, false)
            showToast("Report exported: ${file.name}")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Failed to export report: ${e.message}")
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()

        // 1. Draw solid canvas background
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)

        // 2. Top Header Bar (52px high)
        guiGraphics.fill(0, 0, width, 50, 0xFF0F172A.toInt())
        guiGraphics.fill(0, 49, width, 50, 0xFF38BDF8.toInt())

        // Row 1: Title & Session Status
        val session = StoryDebugger.activeSessionState
        val currentStoryId = session.storyId.ifBlank { initialStoryId ?: "" }
        val isLive = session.isActive || currentStoryId.isNotBlank()

        val title = "🐞 Story Debugger & Variable Inspector"
        guiGraphics.drawString(font, title, 10, 8, 0xFF38BDF8.toInt(), true)

        val statusText = if (isLive) "§a● LIVE: §f${currentStoryId.ifBlank { "active" }}" else "§7○ STANDBY / IDLE"
        guiGraphics.drawString(font, statusText, 10 + font.width(title) + 12, 8, 0xFFFFFFFF.toInt(), false)

        // Row 2: Filter Category Tabs
        val filterTabs = listOf(
            Pair(RuntimeDebugCategoryFilter.ALL, "All (${StoryDebugger.logs.size})"),
            Pair(RuntimeDebugCategoryFilter.ACTIONS, "⚡ Actions"),
            Pair(RuntimeDebugCategoryFilter.VARIABLES, "📊 Variables"),
            Pair(RuntimeDebugCategoryFilter.AI, "🤖 AI"),
            Pair(RuntimeDebugCategoryFilter.ERRORS_ONLY, "❌ Errors (${StoryDebugger.getErrorCount()})")
        )

        var tabX = 10
        val tabY = 26
        val tabH = 18

        filterTabs.forEach { (filter, label) ->
            val tabW = font.width(label) + 12
            val isSelected = activeFilter == filter
            val isHover = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH
            val bg = when {
                isSelected -> 0xFF0284C7.toInt()
                isHover -> 0xFF334155.toInt()
                else -> 0xFF1E293B.toInt()
            }
            guiGraphics.fill(tabX, tabY, tabX + tabW, tabY + tabH, bg)
            guiGraphics.drawString(font, label, tabX + 6, tabY + 5, if (isSelected) 0xFFFFFFFF.toInt() else 0xFFCBD5E1.toInt(), false)
            tabX += tabW + 6
        }

        // 3. Split Body Layout
        val bodyTop = 54
        val bodyBottom = height - 34
        val bodyH = (bodyBottom - bodyTop).coerceAtLeast(10)

        val panelLeftX = 10
        val panelLeftW = ((width - 30) * 0.58).toInt().coerceAtLeast(50)
        val panelLeftH = bodyH

        val panelRightX = panelLeftX + panelLeftW + 10
        val panelRightW = (width - panelRightX - 10).coerceAtLeast(50)
        val panelRightH = bodyH

        // LEFT PANEL: Execution Logs
        renderLeftLogsPanel(guiGraphics, panelLeftX, bodyTop, panelLeftW, panelLeftH, mouseX, mouseY)

        // RIGHT PANEL: Session Overview & Variables
        renderRightStatePanel(guiGraphics, panelRightX, bodyTop, panelRightW, panelRightH, mouseX, mouseY)

        // 4. Footer Bar
        guiGraphics.fill(0, height - 34, width, height, 0xFF0F172A.toInt())
        guiGraphics.fill(0, height - 34, width, height - 33, 0xFF1E293B.toInt())

        // 5. Render child widgets (search box & footer buttons)
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // 6. Toast Feedback (rendered on top of everything)
        if (actionToastTimer > 0) {
            actionToastTimer--
            actionToast?.let { toast ->
                val tw = font.width(toast) + 16
                val tx = (width - tw) / 2
                val ty = height - 56
                guiGraphics.fill(tx, ty, tx + tw, ty + 16, 0xEE1E293B.toInt())
                guiGraphics.fill(tx, ty, tx + 2, ty + 16, 0xFF38BDF8.toInt())
                guiGraphics.drawString(font, toast, tx + 8, ty + 4, 0xFFFFD700.toInt(), false)
            }
        }
    }

    private fun renderLeftLogsPanel(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int) {
        // Container background
        guiGraphics.fill(x, y, x + w, y + h, 0xFF111827.toInt())
        guiGraphics.fill(x, y, x + w, y + 1, 0xFF1F2937.toInt())
        guiGraphics.fill(x, y, x + 1, y + h, 0xFF1F2937.toInt())
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0xFF1F2937.toInt())
        guiGraphics.fill(x, y + h - 1, x + w, y + h, 0xFF1F2937.toInt())

        // Header
        guiGraphics.fill(x, y, x + w, y + 20, 0xFF1E293B.toInt())
        val logs = getFilteredLogs()
        val countText = "📜 Execution Logs (${logs.size})"
        guiGraphics.drawString(font, countText, x + 8, y + 6, 0xFF38BDF8.toInt(), false)

        val scrollStateText = if (isAutoScrollPaused) "§e[Paused]" else "§7[Auto-Scroll]"
        guiGraphics.drawString(font, scrollStateText, x + w - font.width(scrollStateText) - 8, y + 6, 0xFF94A3B8.toInt(), false)

        // Viewport
        val contentLeft = x + 4
        val contentTop = y + 22
        val contentRight = x + w - 4
        val contentBottom = y + h - 4
        val viewportH = (contentBottom - contentTop).coerceAtLeast(1)

        guiGraphics.fill(contentLeft, contentTop, contentRight, contentBottom, 0xFF0B101B.toInt())

        val sLeft = contentLeft.coerceIn(0, width)
        val sTop = contentTop.coerceIn(0, height)
        val sRight = contentRight.coerceIn(sLeft, width)
        val sBottom = contentBottom.coerceIn(sTop, height)
        val hasScissor = sRight > sLeft && sBottom > sTop

        if (hasScissor) {
            guiGraphics.enableScissor(sLeft, sTop, sRight, sBottom)
        }

        try {
            val itemH = 22
            val itemW = contentRight - contentLeft - 8
            val scrollY = logScrollOffset.toInt()

            logs.forEachIndexed { idx, log ->
                val iy = contentTop + 4 + idx * 24 - scrollY
                if (iy + itemH >= contentTop && iy <= contentBottom) {
                    val isHover = mouseX >= contentLeft + 4 && mouseX <= contentLeft + 4 + itemW && mouseY >= iy && mouseY <= iy + itemH
                    val isError = log.level.equals("ERROR", true) || log.status == NodeExecutionStatus.FAILED
                    val isWarn = log.level.equals("WARN", true) || log.status == NodeExecutionStatus.FALLBACK_TRIGGERED

                    val bgCol = when {
                        isError && isHover -> 0x55DC2626
                        isError -> 0x22DC2626
                        isWarn && isHover -> 0x55D97706
                        isWarn -> 0x22D97706
                        isHover -> 0x44334155
                        else -> 0x221E293B
                    }

                    guiGraphics.fill(contentLeft + 4, iy, contentLeft + 4 + itemW, iy + itemH, bgCol)

                    // Timestamp
                    val timeStr = timeFormat.format(Date(log.timestamp))
                    guiGraphics.drawString(font, "§8$timeStr", contentLeft + 8, iy + 6, 0xFF94A3B8.toInt(), false)

                    // Badge
                    val badge = log.getLogBadge()
                    val badgeCol = log.getLogBadgeColor()
                    val badgeW = font.width(badge) + 8
                    guiGraphics.fill(contentLeft + 54, iy + 3, contentLeft + 54 + badgeW, iy + 17, (badgeCol and 0x00FFFFFF) or 0x33000000)
                    guiGraphics.drawString(font, badge, contentLeft + 58, iy + 5, badgeCol, false)

                    // Block Type & Friendly Name
                    val nodeBadge = "[${log.blockType.name}]"
                    guiGraphics.drawString(font, "§7$nodeBadge", contentLeft + 60 + badgeW, iy + 6, 0xFF94A3B8.toInt(), false)

                    // Message Text
                    val msgX = contentLeft + 66 + badgeW + font.width(nodeBadge)
                    val maxMsgW = (contentLeft + itemW - msgX - 55).coerceAtLeast(10)
                    val cleanMsg = log.message.replace(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), "#node")
                    val msgStr = font.plainSubstrByWidth(cleanMsg, maxMsgW)
                    guiGraphics.drawString(font, msgStr, msgX, iy + 6, if (isError) 0xFFFCA5A5.toInt() else 0xFFE2E8F0.toInt(), false)

                    // Jump ➔ Button
                    val jumpW = 46
                    val jumpH = 14
                    val jumpX = contentLeft + 4 + itemW - jumpW - 2
                    val jumpY = iy + (itemH - jumpH) / 2
                    val isJumpHover = mouseX >= jumpX && mouseX <= jumpX + jumpW && mouseY >= jumpY && mouseY <= jumpY + jumpH

                    guiGraphics.fill(jumpX, jumpY, jumpX + jumpW, jumpY + jumpH, if (isJumpHover) 0xFF0284C7.toInt() else 0xFF1E293B.toInt())
                    guiGraphics.drawString(font, "Jump ➔", jumpX + 4, jumpY + 3, 0xFFFFFFFF.toInt(), false)
                }
            }

            if (logs.isEmpty()) {
                guiGraphics.drawString(font, "No runtime debug logs recorded for active filter.", contentLeft + 14, contentTop + 14, 0xFF64748B.toInt(), false)
            }
        } finally {
            if (hasScissor) {
                guiGraphics.disableScissor()
            }
        }

        // Scrollbar
        val totalH = logs.size * 24 + 8
        val maxScroll = (totalH - viewportH).coerceAtLeast(0)
        if (maxScroll > 0) {
            val sbX = contentRight - 4
            val scrollRatio = viewportH.toFloat() / totalH
            val thumbH = (viewportH * scrollRatio).toInt().coerceAtLeast(15)
            val thumbY = contentTop + ((logScrollOffset / maxScroll) * (viewportH - thumbH)).toInt()

            guiGraphics.fill(sbX, contentTop, sbX + 3, contentBottom, 0xFF0F172A.toInt())
            guiGraphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFF38BDF8.toInt())
        }
    }

    private fun renderRightStatePanel(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int) {
        val session = StoryDebugger.activeSessionState
        val overviewH = 130.coerceAtMost(h / 2)

        // 1. Session Overview Card
        guiGraphics.fill(x, y, x + w, y + overviewH, 0xFF111827.toInt())
        guiGraphics.fill(x, y, x + w, y + 2, 0xFF38BDF8.toInt())
        guiGraphics.fill(x, y, x + 1, y + overviewH, 0xFF1F2937.toInt())
        guiGraphics.fill(x + w - 1, y, x + w, y + overviewH, 0xFF1F2937.toInt())
        guiGraphics.fill(x, y + overviewH - 1, x + w, y + overviewH, 0xFF1F2937.toInt())

        guiGraphics.drawString(font, "📊 Active Story Session Overview", x + 8, y + 8, 0xFF38BDF8.toInt(), true)

        var cardY = y + 24
        val rowH = 14

        val packName = session.packName.ifBlank { session.storyId.ifBlank { initialStoryId ?: "None" } }
        guiGraphics.drawString(font, "🏷️ Pack / Story: §f$packName", x + 8, cardY, 0xFF94A3B8.toInt(), false)
        cardY += rowH
        guiGraphics.drawString(font, "🎬 Active Scene: §f${session.sceneName.ifBlank { "Main Scene" }}", x + 8, cardY, 0xFF94A3B8.toInt(), false)
        cardY += rowH
        guiGraphics.drawString(font, "⚡ Current Node: §b${session.activeNodeType.ifBlank { "IDLE" }} #${cleanBlockId(session.activeNodeId)}", x + 8, cardY, 0xFF94A3B8.toInt(), false)
        cardY += rowH

        val targetDesc = when {
            session.targetEntitySlot.isNotBlank() -> "🐾 Cobblemon Slot ${session.targetEntitySlot} (${session.targetEntityName})"
            session.targetEntityTag.isNotBlank() -> "👤 Mob Tag: '${session.targetEntityTag}' (${session.targetEntityName})"
            session.targetEntityName.isNotBlank() -> "🎯 Target: ${session.targetEntityName}"
            else -> "👤 Player Initiated"
        }
        guiGraphics.drawString(font, "🎯 Target Entity: §f$targetDesc", x + 8, cardY, 0xFF94A3B8.toInt(), false)
        cardY += rowH

        val statusStr = if (session.isActive) "§a● RUNNING" else "§7○ STANDBY"
        guiGraphics.drawString(font, "Status: $statusStr", x + 8, cardY, 0xFF94A3B8.toInt(), false)

        // 2. Real-Time Variable Inspector (Bottom Half)
        val varY = y + overviewH + 8
        val varH = (h - overviewH - 8).coerceAtLeast(10)

        guiGraphics.fill(x, varY, x + w, varY + varH, 0xFF111827.toInt())
        guiGraphics.fill(x, varY, x + w, varY + 1, 0xFF1F2937.toInt())
        guiGraphics.fill(x, varY, x + 1, varY + varH, 0xFF1F2937.toInt())
        guiGraphics.fill(x + w - 1, varY, x + w, varY + varH, 0xFF1F2937.toInt())
        guiGraphics.fill(x, varY + varH - 1, x + w, varY + varH, 0xFF1F2937.toInt())

        // Header
        guiGraphics.fill(x, varY, x + w, varY + 20, 0xFF1E293B.toInt())
        val varsList = session.variables.toList()
        guiGraphics.drawString(font, "📊 Active Story Variables (${varsList.size})", x + 8, varY + 6, 0xFFF59E0B.toInt(), false)

        val vLeft = x + 4
        val vTop = varY + 22
        val vRight = x + w - 4
        val vBottom = varY + varH - 4
        val vViewportH = (vBottom - vTop).coerceAtLeast(1)

        guiGraphics.fill(vLeft, vTop, vRight, vBottom, 0xFF0B101B.toInt())

        val svLeft = vLeft.coerceIn(0, width)
        val svTop = vTop.coerceIn(0, height)
        val svRight = vRight.coerceIn(svLeft, width)
        val svBottom = vBottom.coerceIn(svTop, height)
        val hasVarScissor = svRight > svLeft && svBottom > svTop

        if (hasVarScissor) {
            guiGraphics.enableScissor(svLeft, svTop, svRight, svBottom)
        }

        try {
            val vItemH = 18
            val vItemW = vRight - vLeft - 8
            val vScrollY = varScrollOffset.toInt()

            varsList.forEachIndexed { idx, (key, value) ->
                val vy = vTop + 4 + idx * 20 - vScrollY
                if (vy + vItemH >= vTop && vy <= vBottom) {
                    val isUpdatedRecently = key == session.lastUpdatedVarKey && (System.currentTimeMillis() - session.lastVarUpdateTime < 2500L)
                    val typeName = inferVariableType(value)

                    val bgCol = when {
                        isUpdatedRecently -> 0x55F59E0B
                        idx % 2 == 0 -> 0x221E293B
                        else -> 0x111E293B
                    }

                    guiGraphics.fill(vLeft + 4, vy, vLeft + 4 + vItemW, vy + vItemH, bgCol)
                    if (isUpdatedRecently) {
                        guiGraphics.fill(vLeft + 4, vy, vLeft + 6, vy + vItemH, 0xFFF59E0B.toInt())
                    }

                    val varLine = "§f$key §7[$typeName] §8= §b$value"
                    guiGraphics.drawString(font, varLine, vLeft + 8, vy + 5, 0xFFE2E8F0.toInt(), false)
                }
            }

            if (varsList.isEmpty()) {
                guiGraphics.drawString(font, "No active story variables currently registered.", vLeft + 14, vTop + 14, 0xFF64748B.toInt(), false)
            }
        } finally {
            if (hasVarScissor) {
                guiGraphics.disableScissor()
            }
        }

        // Scrollbar
        val vTotalH = varsList.size * 20 + 8
        val vMaxScroll = (vTotalH - vViewportH).coerceAtLeast(0)
        if (vMaxScroll > 0) {
            val sbX = vRight - 4
            val scrollRatio = vViewportH.toFloat() / vTotalH
            val thumbH = (vViewportH * scrollRatio).toInt().coerceAtLeast(15)
            val thumbY = vTop + ((varScrollOffset / vMaxScroll) * (vViewportH - thumbH)).toInt()

            guiGraphics.fill(sbX, vTop, sbX + 3, vBottom, 0xFF0F172A.toInt())
            guiGraphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFFF59E0B.toInt())
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            // Filter tabs at tabY = 26, tabH = 18
            val tabY = 26
            val tabH = 18

            val filterTabs = listOf(
                Pair(RuntimeDebugCategoryFilter.ALL, "All (${StoryDebugger.logs.size})"),
                Pair(RuntimeDebugCategoryFilter.ACTIONS, "⚡ Actions"),
                Pair(RuntimeDebugCategoryFilter.VARIABLES, "📊 Variables"),
                Pair(RuntimeDebugCategoryFilter.AI, "🤖 AI"),
                Pair(RuntimeDebugCategoryFilter.ERRORS_ONLY, "❌ Errors (${StoryDebugger.getErrorCount()})")
            )

            var tabX = 10
            filterTabs.forEach { (filter, label) ->
                val tabW = font.width(label) + 12
                if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH) {
                    activeFilter = filter
                    logScrollOffset = 0f
                    return true
                }
                tabX += tabW + 6
            }

            // Left panel jump buttons
            val bodyTop = 54
            val bodyBottom = height - 34
            val bodyH = bodyBottom - bodyTop
            val panelLeftX = 10
            val panelLeftW = ((width - 30) * 0.58).toInt().coerceAtLeast(50)
            val contentLeft = panelLeftX + 4
            val contentTop = bodyTop + 22
            val contentRight = panelLeftX + panelLeftW - 4
            val contentBottom = bodyTop + bodyH - 4

            if (mouseX >= contentLeft && mouseX <= contentRight && mouseY >= contentTop && mouseY <= contentBottom) {
                val logs = getFilteredLogs()
                val itemW = contentRight - contentLeft - 8
                val scrollY = logScrollOffset.toInt()

                logs.forEachIndexed { idx, log ->
                    val iy = contentTop + 4 + idx * 24 - scrollY
                    if (mouseX >= contentLeft + 4 && mouseX <= contentLeft + 4 + itemW && mouseY >= iy && mouseY <= iy + 22) {
                        jumpToNode(log.storyId, log.blockId)
                        return true
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val bodyTop = 54
        val bodyBottom = height - 34
        val bodyH = bodyBottom - bodyTop

        val panelLeftX = 10
        val panelLeftW = ((width - 30) * 0.58).toInt().coerceAtLeast(50)
        val panelRightX = panelLeftX + panelLeftW + 10
        val panelRightW = width - panelRightX - 10

        if (mouseX >= panelLeftX && mouseX <= panelLeftX + panelLeftW && mouseY >= bodyTop && mouseY <= bodyBottom) {
            val logs = getFilteredLogs()
            val totalH = logs.size * 24 + 8
            val viewportH = bodyH - 26
            val maxScroll = (totalH - viewportH).coerceAtLeast(0).toFloat()
            if (maxScroll > 0f) {
                logScrollOffset = (logScrollOffset - scrollY.toFloat() * 24f).coerceIn(0f, maxScroll)
                return true
            }
        }

        if (mouseX >= panelRightX && mouseX <= panelRightX + panelRightW && mouseY >= bodyTop && mouseY <= bodyBottom) {
            val varsCount = StoryDebugger.activeSessionState.variables.size
            val vTotalH = varsCount * 20 + 8
            val vViewportH = bodyH / 2 - 26
            val vMaxScroll = (vTotalH - vViewportH).coerceAtLeast(0).toFloat()
            if (vMaxScroll > 0f) {
                varScrollOffset = (varScrollOffset - scrollY.toFloat() * 20f).coerceIn(0f, vMaxScroll)
                return true
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun isPauseScreen(): Boolean = false
}
