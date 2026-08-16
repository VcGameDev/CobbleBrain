package vito.cobblebrain.client.gui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.StoryProject
import vito.cobblebrain.model.StorySerializer
import java.io.File

class LoadStoryScreen(
    private val parentEditor: StoryEditorScreen,
    private val onStoryLoaded: (StoryProject) -> Unit
) : Screen(Component.literal("Load Story Package")) {

    private var currentDirectory: File = StorySerializer.storageDir
    private val fileList = mutableListOf<File>()
    private var selectedFile: File? = null
    private var scrollOffset: Int = 0
    private val itemHeight = 22

    override fun init() {
        super.init()
        refreshFileList()

        val cx = width / 2
        val bottomY = height - 32

        // Load Selected Button
        addRenderableWidget(
            Button.builder(Component.literal("Load Selected")) {
                val selected = selectedFile
                if (selected != null && selected.isFile && selected.name.endsWith(".json")) {
                    val loaded = StorySerializer.load(selected)
                    if (loaded != null) {
                        onStoryLoaded(loaded)
                        minecraft?.setScreen(parentEditor)
                    }
                }
            }.bounds(cx - 130, bottomY, 120, 20).build()
        )

        // Back/Cancel Button
        addRenderableWidget(
            Button.builder(Component.literal("Back")) {
                minecraft?.setScreen(parentEditor)
            }.bounds(cx + 10, bottomY, 120, 20).build()
        )
    }

    private fun refreshFileList() {
        fileList.clear()
        selectedFile = null

        val rootDir = StorySerializer.storageDir
        if (currentDirectory.canonicalPath != rootDir.canonicalPath && currentDirectory.parentFile != null) {
            fileList.add(currentDirectory.parentFile) // [..] parent dir
        }

        val children = currentDirectory.listFiles()?.toList() ?: emptyList()
        val dirs = children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
        val files = children.filter { it.isFile && it.name.endsWith(".json") }.sortedBy { it.name.lowercase() }

        fileList.addAll(dirs)
        fileList.addAll(files)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Dark background overlay
        guiGraphics.fill(0, 0, width, height, 0xCC000000.toInt())

        val boxW = 320
        val boxH = height - 80
        val boxX = (width - boxW) / 2
        val boxY = 40

        // Modal Frame
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF1E1E24.toInt())
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + 24, 0xFF2A2A34.toInt())
        guiGraphics.drawCenteredString(font, "Load Story - ${currentDirectory.name}", width / 2, boxY + 7, 0xFF00FFCC.toInt())

        // Render File List
        val listTop = boxY + 28
        val listBottom = boxY + boxH - 36
        val visibleItems = maxOf(1, (listBottom - listTop) / itemHeight)

        guiGraphics.enableScissor(boxX + 4, listTop, boxX + boxW - 4, listBottom)

        for (i in 0 until visibleItems) {
            val idx = scrollOffset + i
            if (idx >= fileList.size) break

            val file = fileList[idx]
            val itemY = listTop + i * itemHeight
            val isSelected = file == selectedFile

            val isParentDir = file.canonicalPath == currentDirectory.parentFile?.canonicalPath
            val displayName = when {
                isParentDir -> ".. [Parent Folder]"
                file.isDirectory -> "📁 ${file.name}/"
                else -> "📄 ${file.name}"
            }

            val bgColor = when {
                isSelected -> 0xFF3D5AFE.toInt()
                file.isDirectory -> 0xFF282830.toInt()
                else -> 0xFF222228.toInt()
            }

            guiGraphics.fill(boxX + 8, itemY, boxX + boxW - 8, itemY + itemHeight - 2, bgColor)
            guiGraphics.drawString(font, displayName, boxX + 14, itemY + 6, if (isSelected) 0xFFFFFFFF.toInt() else 0xFFDDDDDD.toInt(), false)
        }

        guiGraphics.disableScissor()

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val boxW = 320
        val boxH = height - 80
        val boxX = (width - boxW) / 2
        val boxY = 40
        val listTop = boxY + 28
        val listBottom = boxY + boxH - 36
        val visibleItems = maxOf(1, (listBottom - listTop) / itemHeight)

        if (mouseX >= boxX + 8 && mouseX <= boxX + boxW - 8 && mouseY >= listTop && mouseY < listBottom) {
            val clickIdx = scrollOffset + ((mouseY - listTop) / itemHeight).toInt()
            if (clickIdx in 0 until fileList.size) {
                val file = fileList[clickIdx]
                if (file == selectedFile) {
                    // Double click / quick action
                    if (file.isDirectory) {
                        currentDirectory = file
                        refreshFileList()
                    } else if (file.name.endsWith(".json")) {
                        val loaded = StorySerializer.load(file)
                        if (loaded != null) {
                            onStoryLoaded(loaded)
                            minecraft?.setScreen(parentEditor)
                        }
                    }
                } else {
                    selectedFile = file
                }
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val boxW = 320
        val boxH = height - 80
        val visibleItems = maxOf(1, (boxH - 64) / itemHeight)
        val maxScroll = maxOf(0, fileList.size - visibleItems)

        val delta = if (scrollY > 0) -1 else 1
        scrollOffset = (scrollOffset + delta).coerceIn(0, maxScroll)
        return true
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}
}
