package vito.cobblebrain.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import vito.cobblebrain.client.StoryControlClient
import vito.cobblebrain.model.StoryMetadataFile
import vito.cobblebrain.model.StorySerializer
import java.io.File

data class PlayableStoryEntry(
    val file: File,
    val isZip: Boolean,
    val id: String,
    val title: String,
    val author: String,
    val version: String,
    val description: String
)

class PlayStoryScreen(
    private val parentScreen: Screen? = null
) : Screen(Component.literal("Play Story")) {

    private val stories = mutableListOf<PlayableStoryEntry>()
    private var selectedIndex: Int = -1
    private var scrollOffset: Int = 0
    private val itemHeight = 36

    private lateinit var btnStart: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnBack: Button

    override fun init() {
        super.init()
        refreshStories()

        val boxW = minOf(450, width - 40)
        val boxH = minOf(300, height - 40)
        val boxX = (width - boxW) / 2
        val boxY = (height - boxH) / 2
        val bottomY = boxY + boxH - 28

        // Start Story Button
        btnStart = Button.builder(Component.literal("▶ Start Story")) {
            triggerStartSelected()
        }.bounds(boxX + 10, bottomY, 130, 20).build()
        addRenderableWidget(btnStart)

        // Refresh List Button
        btnRefresh = Button.builder(Component.literal("🔄 Refresh")) {
            refreshStories()
        }.bounds(boxX + 150, bottomY, 80, 20).build()
        addRenderableWidget(btnRefresh)

        // Back / Cancel Button
        btnBack = Button.builder(Component.literal("Back")) {
            minecraft?.setScreen(parentScreen)
        }.bounds(boxX + boxW - 90, bottomY, 80, 20).build()
        addRenderableWidget(btnBack)

        updateStartButtonState()
    }

    private fun refreshStories() {
        stories.clear()
        selectedIndex = -1
        scrollOffset = 0

        val packs = StorySerializer.listStoryPacks()
        for (pack in packs) {
            val isZip = pack.isFile && pack.name.endsWith(".zip", ignoreCase = true)
            val metadata: StoryMetadataFile? = if (isZip) {
                StorySerializer.peekZipMetadata(pack)
            } else {
                val metaFile = pack.listFiles { _, n ->
                    n.endsWith("_metadata.json", ignoreCase = true) || n.equals("metadata.json", ignoreCase = true)
                }?.firstOrNull()
                if (metaFile != null && metaFile.exists()) {
                    try {
                        StorySerializer.gson.fromJson(metaFile.readText(), StoryMetadataFile::class.java)
                    } catch (_: Exception) { null }
                } else null
            }

            val id = metadata?.id?.ifBlank { pack.nameWithoutExtension } ?: pack.nameWithoutExtension
            val title = metadata?.name?.ifBlank { id } ?: id
            val author = metadata?.author?.ifBlank { "Unknown" } ?: "Unknown"
            val version = metadata?.version?.ifBlank { "1.0.0" } ?: "1.0.0"
            val description = metadata?.description ?: ""

            stories.add(
                PlayableStoryEntry(
                    file = pack,
                    isZip = isZip,
                    id = id,
                    title = title,
                    author = author,
                    version = version,
                    description = description
                )
            )
        }

        if (stories.isNotEmpty()) {
            selectedIndex = 0
        }
        updateStartButtonState()
    }

    private fun updateStartButtonState() {
        if (!::btnStart.isInitialized) return

        val player = Minecraft.getInstance().player
        val canStart = player?.hasPermissions(3) == true || Minecraft.getInstance().isSingleplayer
        val hasSelection = selectedIndex in 0 until stories.size

        btnStart.active = canStart && hasSelection
        btnStart.tooltip = when {
            !canStart -> Tooltip.create(Component.literal("Only administrators (Level 3+) can start stories on this server."))
            !hasSelection -> Tooltip.create(Component.literal("Select a story to play."))
            else -> null
        }
    }

    private fun triggerStartSelected() {
        val player = Minecraft.getInstance().player
        val canStart = player?.hasPermissions(3) == true || Minecraft.getInstance().isSingleplayer
        if (!canStart) return

        if (selectedIndex in 0 until stories.size) {
            val selected = stories[selectedIndex]
            Minecraft.getInstance().player?.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f)
            StoryControlClient.start(selected.id)
            minecraft?.setScreen(null) // Return directly to game
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Dark background overlay
        guiGraphics.fill(0, 0, width, height, 0xCC000000.toInt())

        val boxW = minOf(450, width - 40)
        val boxH = minOf(300, height - 40)
        val boxX = (width - boxW) / 2
        val boxY = (height - boxH) / 2

        // Main modal frame
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF1E1E24.toInt())
        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + 26, 0xFF2A2A34.toInt())
        guiGraphics.drawCenteredString(font, "🎮 Select Story to Play", width / 2, boxY + 8, 0xFF00FFCC.toInt())

        val listX = boxX + 10
        val listY = boxY + 32
        val listW = (boxW - 28) * 55 / 100
        val listH = boxH - 68

        val detailX = listX + listW + 8
        val detailY = listY
        val detailW = boxX + boxW - 10 - detailX
        val detailH = listH

        // 1. Render stories list
        val visibleItems = maxOf(1, listH / itemHeight)
        guiGraphics.enableScissor(listX, listY, listX + listW, listY + listH)

        for (i in 0 until visibleItems) {
            val idx = scrollOffset + i
            if (idx >= stories.size) break

            val item = stories[idx]
            val itemY = listY + i * itemHeight
            val isSelected = idx == selectedIndex

            val bgColor = when {
                isSelected -> 0xFF3D5AFE.toInt()
                mouseX in listX..(listX + listW) && mouseY in itemY..(itemY + itemHeight - 2) -> 0xFF2A2A38.toInt()
                else -> 0xFF222228.toInt()
            }

            guiGraphics.fill(listX, itemY, listX + listW, itemY + itemHeight - 2, bgColor)

            // Tag badge
            val badge = if (item.isZip) "🔒 [ZIP]" else "📦 [DIR]"
            val badgeColor = if (item.isZip) 0xFF81C784.toInt() else 0xFF4DD0E1.toInt()
            guiGraphics.drawString(font, badge, listX + 6, itemY + 5, badgeColor, false)

            // Title
            val titleX = listX + font.width(badge) + 10
            val maxTitleW = listW - (titleX - listX) - 6
            val trimmedTitle = font.plainSubstrByWidth(item.title, maxTitleW)
            guiGraphics.drawString(font, trimmedTitle, titleX, itemY + 5, if (isSelected) 0xFFFFFFFF.toInt() else 0xFFEEEEEE.toInt(), false)

            // Subtitle
            val sub = "v${item.version} • by ${item.author}"
            guiGraphics.drawString(font, font.plainSubstrByWidth(sub, listW - 12), listX + 6, itemY + 18, 0xFFAAAAAA.toInt(), false)
        }

        guiGraphics.disableScissor()

        // 2. Render details panel
        guiGraphics.fill(detailX, detailY, detailX + detailW, detailY + detailH, 0xFF17171C.toInt())
        guiGraphics.renderOutline(detailX, detailY, detailW, detailH, 0xFF32323E.toInt())

        if (selectedIndex in 0 until stories.size) {
            val story = stories[selectedIndex]
            guiGraphics.drawString(font, "STORY DETAILS", detailX + 8, detailY + 8, 0xFF00E5FF.toInt(), false)

            // Title
            guiGraphics.drawString(font, font.plainSubstrByWidth(story.title, detailW - 16), detailX + 8, detailY + 22, 0xFFFFFFFF.toInt(), false)

            // Author & Version
            guiGraphics.drawString(font, "by ${story.author} (v${story.version})", detailX + 8, detailY + 34, 0xFFAAAAAA.toInt(), false)

            // Format type
            val formatStr = if (story.isZip) "Format: Read-Only ZIP" else "Format: Local Folder"
            guiGraphics.drawString(font, formatStr, detailX + 8, detailY + 46, if (story.isZip) 0xFF81C784.toInt() else 0xFF4DD0E1.toInt(), false)

            // Separator
            guiGraphics.fill(detailX + 8, detailY + 58, detailX + detailW - 8, detailY + 59, 0xFF333340.toInt())

            // Description
            val descText = if (story.description.isNotBlank()) story.description else "No description provided."
            val descLines = font.split(Component.literal(descText), detailW - 16)
            var curY = detailY + 65
            for (line in descLines) {
                if (curY + font.lineHeight > detailY + detailH - 6) break
                guiGraphics.drawString(font, line, detailX + 8, curY, 0xFFCCCCCC.toInt(), false)
                curY += font.lineHeight + 1
            }
        } else {
            guiGraphics.drawCenteredString(font, "No stories found.", detailX + detailW / 2, detailY + detailH / 2 - 10, 0xFF888888.toInt())
            guiGraphics.drawCenteredString(font, "Put .zip or folders in", detailX + detailW / 2, detailY + detailH / 2 + 2, 0xFF666666.toInt())
            guiGraphics.drawCenteredString(font, "cobblebrain/storypacks/", detailX + detailW / 2, detailY + detailH / 2 + 14, 0xFF00B0FF.toInt())
        }

        updateStartButtonState()
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val boxW = minOf(450, width - 40)
        val boxH = minOf(300, height - 40)
        val boxX = (width - boxW) / 2
        val boxY = (height - boxH) / 2
        val listX = boxX + 10
        val listY = boxY + 32
        val listW = (boxW - 28) * 55 / 100
        val listH = boxH - 68

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            val clickIdx = scrollOffset + ((mouseY - listY) / itemHeight).toInt()
            if (clickIdx in 0 until stories.size) {
                if (clickIdx == selectedIndex) {
                    triggerStartSelected()
                } else {
                    selectedIndex = clickIdx
                    updateStartButtonState()
                }
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val boxH = minOf(300, height - 40)
        val listH = boxH - 68
        val visibleItems = maxOf(1, listH / itemHeight)
        val maxScroll = maxOf(0, stories.size - visibleItems)

        val delta = if (scrollY > 0) -1 else 1
        scrollOffset = (scrollOffset + delta).coerceIn(0, maxScroll)
        return true
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}
}
