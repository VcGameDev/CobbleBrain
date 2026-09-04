package vito.cobblebrain.client.gui.widgets

import net.minecraft.Util
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import vito.cobblebrain.social.StoryAssetManager
import java.io.File

class TextureSelectorModalWidget(
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    val storyId: String,
    val initialSelected: String = "",
    val onSelect: (String) -> Unit,
    val onClose: () -> Unit
) {
    private val modalWidth = 470.coerceAtMost(screenWidth - 20)
    private val modalHeight = 340.coerceAtMost(screenHeight - 20)
    private val modalX = maxOf(10, (screenWidth - modalWidth) / 2)
    private val modalY = maxOf(10, (screenHeight - modalHeight) / 2)

    private val contentLeft = modalX + 10
    private val contentTop = modalY + 68
    private val contentRight = modalX + modalWidth - 10
    private val contentBottom = modalY + modalHeight - 10
    private val viewportH = contentBottom - contentTop

    private var vScrollOffset: Float = 0f
    private var isDraggingScrollbar: Boolean = false
    private var dragStartMouseY: Double = 0.0
    private var dragStartScrollOffset: Float = 0f
    private val searchBox: EditBox
    private var hoveredIndex: Int = -1

    private var cachedFiles: List<File> = emptyList()

    init {
        refreshFiles()
        val searchW = modalWidth - 140
        searchBox = EditBox(font, modalX + 14, modalY + 44, searchW, 16, Component.literal("Search"))
        searchBox.setMaxLength(100)
        searchBox.setHint(Component.literal("§8🔍 Search texture filenames..."))
        searchBox.setEditable(true)
        searchBox.active = true
        searchBox.isFocused = true
        searchBox.setResponder { vScrollOffset = 0f }
    }

    private fun refreshFiles() {
        cachedFiles = StoryAssetManager.listStoryTextures(storyId)
    }

    private fun getFilteredList(): List<File> {
        val query = searchBox.value.trim().lowercase()
        return cachedFiles.filter { file ->
            query.isBlank() || file.name.lowercase().contains(query)
        }
    }

    private fun getTotalContentHeight(): Int {
        val count = getFilteredList().size
        return count * 36 + 8
    }

    private fun formatFileSize(bytes: Long): String {
        return if (bytes < 1024) "$bytes B" else "${bytes / 1024} KB"
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Outer Card Container
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xEE0F172A.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 1, 0xFF38BDF8.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0x33FFFFFF)
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0x33FFFFFF)
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0x33FFFFFF)

        // Header Title & Story ID
        guiGraphics.drawString(font, "🎨 Browse Story Textures & Skins", modalX + 14, modalY + 10, 0xFF38BDF8.toInt(), true)
        val subtitle = "Story: §f${storyId.ifBlank { "default" }} §8(${cachedFiles.size} textures found)"
        guiGraphics.drawString(font, subtitle, modalX + 14, modalY + 24, 0xFF94A3B8.toInt(), false)

        // Close Button (✖)
        val closeX = modalX + modalWidth - 24
        val closeY = modalY + 8
        val isCloseHovered = mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 14
        guiGraphics.fill(closeX, closeY, closeX + 16, closeY + 14, if (isCloseHovered) 0xFFDC2626.toInt() else 0xFF1E293B.toInt())
        guiGraphics.drawString(font, "✖", closeX + 4, closeY + 3, 0xFFFFFFFF.toInt(), false)

        // Open Folder Button [📂 Open Folder]
        val btnW = 100
        val btnH = 16
        val btnX = modalX + modalWidth - btnW - 14
        val btnY = modalY + 44
        val isFolderHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH
        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, if (isFolderHovered) 0xFF0284C7.toInt() else 0xFF1E293B.toInt())
        guiGraphics.drawString(font, "📂 Open Folder", btnX + 8, btnY + 4, 0xFFFFFFFF.toInt(), false)

        // Search Input Box
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick)

        // Content Viewport Scissor Clipping
        guiGraphics.fill(contentLeft, contentTop, contentRight, contentBottom, 0xAA0F172A.toInt())
        guiGraphics.enableScissor(contentLeft, contentTop, contentRight, contentBottom)

        val items = getFilteredList()
        val itemW = modalWidth - 36
        val itemH = 32
        val scrollY = vScrollOffset.toInt()

        hoveredIndex = -1

        items.forEachIndexed { idx, file ->
            val iy = contentTop + 4 + idx * 36 - scrollY
            if (iy + itemH >= contentTop && iy <= contentBottom) {
                val isHovered = mouseX >= contentLeft + 4 && mouseX <= contentLeft + 4 + itemW && mouseY >= iy && mouseY <= iy + itemH
                if (isHovered) hoveredIndex = idx

                val isSelected = file.name.equals(initialSelected, ignoreCase = true)
                val bgCol = when {
                    isSelected -> 0x550284C7
                    isHovered -> 0xAA1E293B.toInt()
                    else -> 0x77111827
                }
                val borderCol = when {
                    isSelected -> 0xFF38BDF8.toInt()
                    isHovered -> 0xFF0EA5E9.toInt()
                    else -> 0x334B5563
                }

                guiGraphics.fill(contentLeft + 4, iy, contentLeft + 4 + itemW, iy + itemH, bgCol)
                guiGraphics.fill(contentLeft + 4, iy, contentLeft + 4 + itemW, iy + 1, borderCol)
                guiGraphics.fill(contentLeft + 4, iy, contentLeft + 5, iy + itemH, borderCol)
                guiGraphics.fill(contentLeft + 4 + itemW - 1, iy, contentLeft + 4 + itemW, iy + itemH, borderCol)
                guiGraphics.fill(contentLeft + 4, iy + itemH - 1, contentLeft + 4 + itemW, iy + itemH, borderCol)

                // Icon badge
                guiGraphics.drawString(font, "🖼️", contentLeft + 10, iy + 10, 0xFFFFFFFF.toInt(), false)

                // Texture Name
                guiGraphics.drawString(font, file.name, contentLeft + 28, iy + 6, if (isSelected) 0xFF38BDF8.toInt() else 0xFFFFFFFF.toInt(), false)

                // File Size and Directory Hint
                val infoText = "Size: ${formatFileSize(file.length())} §8• ${file.parentFile.name}"
                guiGraphics.drawString(font, infoText, contentLeft + 28, iy + 18, 0xFF94A3B8.toInt(), false)

                // Select Action Pill
                val pillW = 44
                val pillH = 16
                val pillX = contentLeft + 4 + itemW - pillW - 6
                val pillY = iy + (itemH - pillH) / 2
                val pillHover = mouseX >= pillX && mouseX <= pillX + pillW && mouseY >= pillY && mouseY <= pillY + pillH

                guiGraphics.fill(pillX, pillY, pillX + pillW, pillY + pillH, if (pillHover) 0xFF0284C7.toInt() else 0xFF0EA5E9.toInt())
                guiGraphics.drawString(font, "Select", pillX + 8, pillY + 4, 0xFFFFFFFF.toInt(), false)
            }
        }

        if (items.isEmpty()) {
            if (cachedFiles.isEmpty()) {
                guiGraphics.drawString(font, "No .png textures found in story assets folder!", contentLeft + 14, contentTop + 20, 0xFFF87171.toInt(), true)
                guiGraphics.drawString(font, "Click [📂 Open Folder] to drop your custom PNG files in:", contentLeft + 14, contentTop + 36, 0xFFCBD5E1.toInt(), false)
                val dir = StoryAssetManager.getPrimaryTextureDir(storyId)
                val pathHint = font.plainSubstrByWidth(dir.absolutePath, itemW - 20)
                guiGraphics.drawString(font, pathHint, contentLeft + 14, contentTop + 50, 0xFF38BDF8.toInt(), false)
            } else {
                guiGraphics.drawString(font, "No matching textures found for '${searchBox.value}'", contentLeft + 20, contentTop + 20, 0xFF94A3B8.toInt(), false)
            }
        }

        guiGraphics.disableScissor()

        // Scrollbar
        val totalH = getTotalContentHeight()
        val maxScroll = (totalH - viewportH).coerceAtLeast(0)
        if (maxScroll > 0) {
            val sbW = 5
            val sbX = contentRight - sbW - 1
            val thumbH = ((viewportH.toFloat() / totalH) * viewportH).toInt().coerceIn(16, viewportH)
            val thumbY = contentTop + ((vScrollOffset / maxScroll) * (viewportH - thumbH)).toInt()

            val isHover = mouseX >= sbX - 2 && mouseX <= sbX + sbW + 2 && mouseY >= contentTop && mouseY <= contentBottom
            val thumbCol = if (isDraggingScrollbar) 0xFF38BDF8.toInt() else if (isHover) 0xFF00E5FF.toInt() else 0xFF0284C7.toInt()

            guiGraphics.fill(sbX, contentTop, sbX + sbW, contentBottom, 0x550F172A)
            guiGraphics.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, thumbCol)
        }
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (isDraggingScrollbar) {
            val totalH = getTotalContentHeight()
            val maxScroll = (totalH - viewportH).coerceAtLeast(0)
            if (maxScroll > 0) {
                val thumbH = ((viewportH.toFloat() / totalH) * viewportH).toInt().coerceIn(16, viewportH)
                val trackRange = viewportH - thumbH
                if (trackRange > 0) {
                    val deltaY = mouseY - dragStartMouseY
                    val scrollDelta = (deltaY / trackRange).toFloat() * maxScroll
                    vScrollOffset = (dragStartScrollOffset + scrollDelta).coerceIn(0f, maxScroll.toFloat())
                    return true
                }
            }
        }
        return mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= modalY && mouseY <= modalY + modalHeight
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false
            return true
        }
        return false
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return false

        // Close button
        val closeX = modalX + modalWidth - 24
        val closeY = modalY + 8
        if (mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 14) {
            onClose()
            return true
        }

        // Open Folder button
        val btnW = 100
        val btnH = 16
        val btnX = modalX + modalWidth - btnW - 14
        val btnY = modalY + 44
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            val dir = StoryAssetManager.getPrimaryTextureDir(storyId)
            Util.getPlatform().openFile(dir)
            refreshFiles()
            return true
        }

        // Check Scrollbar Click
        val totalH = getTotalContentHeight()
        val maxScroll = (totalH - viewportH).coerceAtLeast(0)
        if (maxScroll > 0) {
            val sbW = 5
            val sbX = contentRight - sbW - 1
            if (mouseX >= sbX - 4 && mouseX <= contentRight + 4 && mouseY >= contentTop && mouseY <= contentBottom) {
                val thumbH = ((viewportH.toFloat() / totalH) * viewportH).toInt().coerceIn(16, viewportH)
                val thumbY = contentTop + ((vScrollOffset / maxScroll) * (viewportH - thumbH)).toInt()

                isDraggingScrollbar = true
                dragStartMouseY = mouseY
                if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                    dragStartScrollOffset = vScrollOffset
                } else {
                    val trackRange = viewportH - thumbH
                    if (trackRange > 0) {
                        val clickOffset = ((mouseY - contentTop - thumbH / 2.0) / trackRange).coerceIn(0.0, 1.0).toFloat()
                        vScrollOffset = clickOffset * maxScroll
                        dragStartScrollOffset = vScrollOffset
                    }
                }
                return true
            }
        }

        val clickedSearch = searchBox.mouseClicked(mouseX, mouseY, button)
        searchBox.isFocused = clickedSearch
        if (clickedSearch) return true

        // Item row clicks
        if (mouseX >= contentLeft && mouseX <= contentRight && mouseY >= contentTop && mouseY <= contentBottom) {
            val items = getFilteredList()
            val itemW = modalWidth - 36
            val scrollY = vScrollOffset.toInt()

            items.forEachIndexed { idx, file ->
                val iy = contentTop + 4 + idx * 36 - scrollY
                if (mouseX >= contentLeft + 4 && mouseX <= contentLeft + 4 + itemW && mouseY >= iy && mouseY <= iy + 32) {
                    onSelect(file.name)
                    onClose()
                    return true
                }
            }
        }

        return mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= modalY && mouseY <= modalY + modalHeight
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        return searchBox.charTyped(codePoint, modifiers)
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 256) {
            onClose()
            return true
        }
        if (searchBox.keyPressed(keyCode, scanCode, modifiers)) return true
        if (searchBox.isFocused && (keyCode == 259 || keyCode == 261)) {
            return true
        }
        return searchBox.isFocused
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
        return mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= modalY && mouseY <= modalY + modalHeight
    }
}
