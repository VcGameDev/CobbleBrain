package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import vito.cobblebrain.engine.NodeExecutionStatus
import vito.cobblebrain.engine.StoryDebugLogEntry
import vito.cobblebrain.engine.StoryDebugger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DebugLogLevelFilter {
    ALL,
    ERRORS_ONLY,
    WARNINGS_ONLY,
    INFO_ONLY
}

@Deprecated("Superseded by StoryRuntimeDebugScreen")
class DebugConsoleWidget(
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    val storyId: String,
    val onFocusNode: (blockId: String) -> Unit,
    val onClose: () -> Unit
) {
    val panelH = 190.coerceAtMost(screenHeight - 60)
    val panelW = screenWidth - 20
    val panelX = 10
    val panelY = screenHeight - panelH - 10

    private val contentLeft = panelX + 8
    private val contentTop = panelY + 54
    private val contentRight = panelX + panelW - 8
    private val contentBottom = panelY + panelH - 8
    private val viewportH = contentBottom - contentTop

    var activeFilter: DebugLogLevelFilter = DebugLogLevelFilter.ALL
    private var vScrollOffset: Float = 0f
    private val searchBox: EditBox
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    init {
        val searchW = 160
        searchBox = EditBox(font, panelX + panelW - searchW - 80, panelY + 28, searchW, 16, Component.literal("Search"))
        searchBox.setMaxLength(100)
        searchBox.setHint(Component.literal("§8🔍 Filter logs..."))
        searchBox.setEditable(true)
        searchBox.active = true
        searchBox.setResponder { vScrollOffset = 0f }
    }

    private fun getFilteredLogs(): List<StoryDebugLogEntry> {
        val query = searchBox.value.trim().lowercase()
        val allLogs = StoryDebugger.logs

        return allLogs.filter { log ->
            val matchStory = storyId.isBlank() || log.storyId.equals(storyId, ignoreCase = true) || log.storyId == "default_story"
            if (!matchStory) return@filter false

            val matchFilter = when (activeFilter) {
                DebugLogLevelFilter.ALL -> true
                DebugLogLevelFilter.ERRORS_ONLY -> log.level.equals("ERROR", true) || log.status == NodeExecutionStatus.FAILED
                DebugLogLevelFilter.WARNINGS_ONLY -> log.level.equals("WARN", true) || log.status == NodeExecutionStatus.FALLBACK_TRIGGERED
                DebugLogLevelFilter.INFO_ONLY -> log.level.equals("INFO", true) && log.status != NodeExecutionStatus.FAILED
            }
            if (!matchFilter) return@filter false

            if (query.isBlank()) return@filter true

            log.message.lowercase().contains(query) ||
            log.blockId.lowercase().contains(query) ||
            log.blockType.name.lowercase().contains(query) ||
            (log.details?.lowercase()?.contains(query) == true)
        }
    }

    private fun getTotalContentHeight(): Int {
        val count = getFilteredLogs().size
        return count * 26 + 4
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Container Background & Borders
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF00B1120.toInt())
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF38BDF8.toInt())
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0x4438BDF8)
        guiGraphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0x4438BDF8)
        guiGraphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0x4438BDF8)

        // Header Title
        val errorCount = StoryDebugger.getErrorCount(storyId)
        val warnCount = StoryDebugger.getWarningCount(storyId)
        val titleText = "🐞 Runtime Execution Tracer & Debug Console"
        guiGraphics.drawString(font, titleText, panelX + 10, panelY + 10, 0xFF38BDF8.toInt(), true)

        val counterText = "§c$errorCount Errors §8• §6$warnCount Warnings §8• Story: §f${storyId.ifBlank { "default" }}"
        guiGraphics.drawString(font, counterText, panelX + 280, panelY + 10, 0xFFCBD5E1.toInt(), false)

        // Close Button (✖)
        val closeX = panelX + panelW - 22
        val closeY = panelY + 8
        val isCloseHover = mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 14
        guiGraphics.fill(closeX, closeY, closeX + 14, closeY + 14, if (isCloseHover) 0xFFDC2626.toInt() else 0xFF1E293B.toInt())
        guiGraphics.drawString(font, "✖", closeX + 3, closeY + 3, 0xFFFFFFFF.toInt(), false)

        // Clear Logs Button [Clear Logs]
        val clearW = 68
        val clearH = 16
        val clearX = panelX + panelW - clearW - 24
        val clearY = panelY + 28
        val isClearHover = mouseX >= clearX && mouseX <= clearX + clearW && mouseY >= clearY && mouseY <= clearY + clearH
        guiGraphics.fill(clearX, clearY, clearX + clearW, clearY + clearH, if (isClearHover) 0xFF475569.toInt() else 0xFF1E293B.toInt())
        guiGraphics.drawString(font, "Clear Logs", clearX + 6, clearY + 4, 0xFFE2E8F0.toInt(), false)

        // Filter Tabs
        var tabX = panelX + 10
        val tabY = panelY + 28
        val tabH = 16

        val tabs = listOf(
            Pair(DebugLogLevelFilter.ALL, "All (${StoryDebugger.logs.size})"),
            Pair(DebugLogLevelFilter.ERRORS_ONLY, "❌ Errors ($errorCount)"),
            Pair(DebugLogLevelFilter.WARNINGS_ONLY, "⚠ Warnings ($warnCount)"),
            Pair(DebugLogLevelFilter.INFO_ONLY, "ℹ Info")
        )

        tabs.forEach { (filter, label) ->
            val tabW = font.width(label) + 14
            val isSelected = activeFilter == filter
            val isHover = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH

            val bgCol = when {
                isSelected -> 0xFF0284C7.toInt()
                isHover -> 0xFF334155.toInt()
                else -> 0xFF1E293B.toInt()
            }
            guiGraphics.fill(tabX, tabY, tabX + tabW, tabY + tabH, bgCol)
            guiGraphics.drawString(font, label, tabX + 7, tabY + 4, if (isSelected) 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt(), false)
            tabX += tabW + 4
        }

        // Search Input
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick)

        // Logs Viewport (Scissor Clipped)
        guiGraphics.fill(contentLeft, contentTop, contentRight, contentBottom, 0x880F172A.toInt())
        guiGraphics.enableScissor(contentLeft, contentTop, contentRight, contentBottom)

        val logs = getFilteredLogs()
        val scrollY = vScrollOffset.toInt()
        val itemW = panelW - 28
        val itemH = 22

        logs.forEachIndexed { idx, log ->
            val iy = contentTop + 4 + idx * 26 - scrollY
            if (iy + itemH >= contentTop && iy <= contentBottom) {
                val isHover = mouseX >= contentLeft + 4 && mouseX <= contentLeft + 4 + itemW && mouseY >= iy && mouseY <= iy + itemH
                val isError = log.level.equals("ERROR", true) || log.status == NodeExecutionStatus.FAILED
                val isWarn = log.level.equals("WARN", true) || log.status == NodeExecutionStatus.FALLBACK_TRIGGERED

                val bgCol = when {
                    isError && isHover -> 0x66DC2626
                    isError -> 0x33DC2626
                    isWarn && isHover -> 0x66D97706
                    isWarn -> 0x33D97706
                    isHover -> 0x44334155
                    else -> 0x221E293B
                }

                guiGraphics.fill(contentLeft + 4, iy, contentLeft + 4 + itemW, iy + itemH, bgCol)

                // Timestamp
                val timeStr = timeFormat.format(Date(log.timestamp))
                guiGraphics.drawString(font, "§8$timeStr", contentLeft + 8, iy + 6, 0xFF94A3B8.toInt(), false)

                // Level Badge
                val (lvlBadge, lvlCol) = when {
                    isError -> Pair("ERR", 0xFFEF4444.toInt())
                    isWarn -> Pair("WARN", 0xFFF59E0B.toInt())
                    else -> Pair("INFO", 0xFF38BDF8.toInt())
                }
                guiGraphics.fill(contentLeft + 78, iy + 3, contentLeft + 110, iy + 17, (lvlCol and 0x00FFFFFF) or 0x33000000)
                guiGraphics.drawString(font, lvlBadge, contentLeft + 82, iy + 5, lvlCol, false)

                // Block Type & ID Badge
                val blockBadge = "[${log.blockType.name} #${log.blockId}]"
                guiGraphics.drawString(font, "§b$blockBadge", contentLeft + 116, iy + 6, 0xFF38BDF8.toInt(), false)

                // Message Text
                val msgX = contentLeft + 120 + font.width(blockBadge)
                val maxMsgW = contentLeft + itemW - msgX - 70
                val msgStr = font.plainSubstrByWidth(log.message, maxMsgW)
                guiGraphics.drawString(font, msgStr, msgX, iy + 6, if (isError) 0xFFFCA5A5.toInt() else 0xFFE2E8F0.toInt(), false)

                // Jump to Block Action Pill
                val jumpW = 60
                val jumpH = 14
                val jumpX = contentLeft + 4 + itemW - jumpW - 4
                val jumpY = iy + (itemH - jumpH) / 2
                val isJumpHover = mouseX >= jumpX && mouseX <= jumpX + jumpW && mouseY >= jumpY && mouseY <= jumpY + jumpH

                guiGraphics.fill(jumpX, jumpY, jumpX + jumpW, jumpY + jumpH, if (isJumpHover) 0xFF0284C7.toInt() else 0xFF1E293B.toInt())
                guiGraphics.drawString(font, "Jump ➔", jumpX + 8, jumpY + 3, 0xFFFFFFFF.toInt(), false)
            }
        }

        if (logs.isEmpty()) {
            guiGraphics.drawString(font, "No debug execution logs matching active filter.", contentLeft + 14, contentTop + 14, 0xFF94A3B8.toInt(), false)
        }

        guiGraphics.disableScissor()

        // Scrollbar
        val totalH = getTotalContentHeight()
        val maxScroll = (totalH - viewportH).coerceAtLeast(0)
        if (maxScroll > 0) {
            val sbX = contentRight - 4
            val scrollRatio = viewportH.toFloat() / totalH
            val thumbH = (viewportH * scrollRatio).toInt().coerceAtLeast(15)
            val thumbY = contentTop + ((vScrollOffset / maxScroll) * (viewportH - thumbH)).toInt()

            guiGraphics.fill(sbX, contentTop, sbX + 3, contentBottom, 0xFF0F172A.toInt())
            guiGraphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFF38BDF8.toInt())
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return false

        // Close button
        val closeX = panelX + panelW - 22
        val closeY = panelY + 8
        if (mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 14) {
            onClose()
            return true
        }

        // Clear Logs button
        val clearW = 68
        val clearH = 16
        val clearX = panelX + panelW - clearW - 24
        val clearY = panelY + 28
        if (mouseX >= clearX && mouseX <= clearX + clearW && mouseY >= clearY && mouseY <= clearY + clearH) {
            StoryDebugger.clearLogs(storyId)
            return true
        }

        // Filter tabs
        var tabX = panelX + 10
        val tabY = panelY + 28
        val tabH = 16
        val errorCount = StoryDebugger.getErrorCount(storyId)
        val warnCount = StoryDebugger.getWarningCount(storyId)

        val tabs = listOf(
            Pair(DebugLogLevelFilter.ALL, "All (${StoryDebugger.logs.size})"),
            Pair(DebugLogLevelFilter.ERRORS_ONLY, "❌ Errors ($errorCount)"),
            Pair(DebugLogLevelFilter.WARNINGS_ONLY, "⚠ Warnings ($warnCount)"),
            Pair(DebugLogLevelFilter.INFO_ONLY, "ℹ Info")
        )

        tabs.forEach { (filter, label) ->
            val tabW = font.width(label) + 14
            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH) {
                activeFilter = filter
                vScrollOffset = 0f
                return true
            }
            tabX += tabW + 4
        }

        if (searchBox.mouseClicked(mouseX, mouseY, button)) return true

        // Row clicks & Jump to Block
        if (mouseX >= contentLeft && mouseX <= contentRight && mouseY >= contentTop && mouseY <= contentBottom) {
            val logs = getFilteredLogs()
            val scrollY = vScrollOffset.toInt()
            val itemW = panelW - 28

            logs.forEachIndexed { idx, log ->
                val iy = contentTop + 4 + idx * 26 - scrollY
                if (mouseX >= contentLeft + 4 && mouseX <= contentLeft + 4 + itemW && mouseY >= iy && mouseY <= iy + 22) {
                    onFocusNode(log.blockId)
                    return true
                }
            }
        }

        return mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        return searchBox.charTyped(codePoint, modifiers)
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 256) {
            onClose()
            return true
        }
        return searchBox.keyPressed(keyCode, scanCode, modifiers)
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        if (mouseX >= contentLeft && mouseX <= contentRight && mouseY >= contentTop && mouseY <= contentBottom) {
            val totalH = getTotalContentHeight()
            val maxScroll = (totalH - viewportH).coerceAtLeast(0).toFloat()
            if (maxScroll > 0f) {
                vScrollOffset = (vScrollOffset - scrollY.toFloat() * 18f).coerceIn(0f, maxScroll)
                return true
            }
        }
        return mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        return mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH
    }
}
