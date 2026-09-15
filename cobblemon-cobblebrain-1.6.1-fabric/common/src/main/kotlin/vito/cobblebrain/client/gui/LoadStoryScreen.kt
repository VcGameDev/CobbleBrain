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
    private var loadBtn: Button? = null

    override fun init() {
        super.init()
        refreshFileList()

        val cx = width / 2
        val bottomY = height - 32

        // Load Selected Button
        loadBtn = Button.builder(Component.literal("Load Selected")) {
            val selected = selectedFile
            if (selected != null && !selected.name.endsWith(".zip", ignoreCase = true)) {
                val loaded = StorySerializer.load(selected)
                if (loaded != null) {
                    onStoryLoaded(loaded)
                    minecraft?.setScreen(parentEditor)
                }
            }
        }.bounds(cx - 130, bottomY, 120, 20).build()
        addRenderableWidget(loadBtn!!)

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
        val files = children.filter {
            it.isFile && (it.name.endsWith(".json", ignoreCase = true) || it.name.endsWith(".zip", ignoreCase = true))
        }.sortedBy { it.name.lowercase() }

        fileList.addAll(dirs)
        fileList.addAll(files)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Update load button state (disabled for ZIP files)
        val isSelectedZip = selectedFile?.let { it.isFile && it.name.endsWith(".zip", ignoreCase = true) } == true
        loadBtn?.active = selectedFile != null && !isSelectedZip

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
            val isStoryPackDir = file.isDirectory && file.listFiles { _, n -> n.endsWith("_metadata.json", true) || n.equals("metadata.json", true) }?.isNotEmpty() == true
            val isZip = file.isFile && file.name.endsWith(".zip", ignoreCase = true)

            val displayName = when {
                isParentDir -> ".. [Parent Folder]"
                isStoryPackDir -> "📦 ${file.name} [Storypack]"
                isZip -> "🔒 ${file.name} [Playable - Read-Only]"
                file.isDirectory -> "📁 ${file.name}/"
                else -> "📄 ${file.name}"
            }

            val bgColor = when {
                isSelected && isZip -> 0xFF4A4A54.toInt()
                isSelected -> 0xFF3D5AFE.toInt()
                isStoryPackDir -> 0xFF2E3A48.toInt()
                isZip -> 0xFF24242A.toInt()
                file.isDirectory -> 0xFF282830.toInt()
                else -> 0xFF222228.toInt()
            }

            val textColor = when {
                isZip -> 0xFFA0A0A0.toInt()
                isSelected -> 0xFFFFFFFF.toInt()
                else -> 0xFFDDDDDD.toInt()
            }

            guiGraphics.fill(boxX + 8, itemY, boxX + boxW - 8, itemY + itemHeight - 2, bgColor)
            guiGraphics.drawString(font, displayName, boxX + 14, itemY + 6, textColor, false)
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

        if (mouseX >= boxX + 8 && mouseX <= boxX + boxW - 8 && mouseY >= listTop && mouseY < listBottom) {
            val clickIdx = scrollOffset + ((mouseY - listTop) / itemHeight).toInt()
            if (clickIdx in 0 until fileList.size) {
                val file = fileList[clickIdx]
                if (file == selectedFile) {
                    // Double click / quick action
                    if (file.isDirectory) {
                        val isPack = file.listFiles { _, n -> n.endsWith("_metadata.json", true) || n.equals("metadata.json", true) }?.isNotEmpty() == true
                        val loaded = if (isPack) StorySerializer.load(file) else null
                        if (loaded != null) {
                            onStoryLoaded(loaded)
                            minecraft?.setScreen(parentEditor)
                        } else {
                            currentDirectory = file
                            refreshFileList()
                        }
                    } else if (file.name.endsWith(".zip", ignoreCase = true)) {
                        // Read-only ZIP files cannot be opened in the story editor
                    } else if (file.name.endsWith(".json", ignoreCase = true)) {
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
        val boxH = height - 80
        val visibleItems = maxOf(1, (boxH - 64) / itemHeight)
        val maxScroll = maxOf(0, fileList.size - visibleItems)

        val delta = if (scrollY > 0) -1 else 1
        scrollOffset = (scrollOffset + delta).coerceIn(0, maxScroll)
        return true
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}
}
