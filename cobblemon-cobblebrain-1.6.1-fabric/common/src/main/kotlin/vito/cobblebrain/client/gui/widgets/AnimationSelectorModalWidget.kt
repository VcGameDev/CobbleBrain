package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

class AnimationSelectorModalWidget(
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    var selectedSystem: String = "COBBLEMON",
    val initialSelected: String = "",
    val onSelect: (String) -> Unit,
    val onClose: () -> Unit
) {
    data class AnimationEntry(
        val id: String,
        val displayName: String,
        val system: String,
        val icon: String,
        val description: String
    )

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
    private val searchBox: EditBox
    private var hoveredIndex: Int = -1

    private val allAnimations = listOf(
        // Cobblemon Animations
        AnimationEntry("idle", "Idle", "COBBLEMON", "🐾", "Standard idle standing animation loop"),
        AnimationEntry("battle_idle", "Battle Idle", "COBBLEMON", "⚔️", "Combat ready stance and posture"),
        AnimationEntry("walk", "Walk", "COBBLEMON", "🚶", "Walking movement stride loop"),
        AnimationEntry("fly_idle", "Fly Idle", "COBBLEMON", "🦅", "Aerial flying and hovering wings loop"),
        AnimationEntry("hover", "Hover", "COBBLEMON", "✨", "Static levitation and float animation"),
        AnimationEntry("sit", "Sit", "COBBLEMON", "🪑", "Sitting down resting pose"),
        AnimationEntry("sleep", "Sleep", "COBBLEMON", "💤", "Lying down resting and deep sleep pose"),
        AnimationEntry("cry", "Cry / Call", "COBBLEMON", "🗣️", "Vocal call and expressive roar motion"),
        AnimationEntry("faint", "Faint", "COBBLEMON", "💀", "Defeat and fainting collapse motion"),
        AnimationEntry("happy", "Happy", "COBBLEMON", "💖", "Joyful hop, bounce, and smile"),
        AnimationEntry("jump", "Jump", "COBBLEMON", "🦘", "Air leap and bounce motion"),
        AnimationEntry("hurt", "Hurt", "COBBLEMON", "💔", "Damage flash and recoil reaction"),
        AnimationEntry("physical_attack", "Physical Attack", "COBBLEMON", "💥", "Physical strike and tackle motion"),
        AnimationEntry("special_attack", "Special Attack", "COBBLEMON", "🔮", "Special energy projectile channel"),

        // NPC / General Mob Poses & Actions
        AnimationEntry("CROUCHING", "Crouching (Sneak)", "NPC", "🥷", "Sneaking and lowered crouching posture"),
        AnimationEntry("SLEEPING", "Sleeping", "NPC", "💤", "Horizontal sleeping pose on ground/bed"),
        AnimationEntry("SWIMMING", "Swimming", "NPC", "🏊", "Horizontal swimming navigation posture"),
        AnimationEntry("SPIN_ATTACK", "Spin Attack", "NPC", "🌀", "Riptide trident spin attack rotation"),
        AnimationEntry("SITTING", "Sitting", "NPC", "🪑", "Vanilla sitting posture (Camel/Boat/Chair)"),
        AnimationEntry("CELEBRATE", "Celebrate", "NPC", "🎉", "Raid victory jumping celebrate pose"),
        AnimationEntry("ATTACK_SWING", "Attack Swing", "NPC", "⚔️", "Main hand arm swinging attack"),
        AnimationEntry("HURT", "Hurt Recoil", "NPC", "💔", "Damage hurt animation and flash"),
        AnimationEntry("CRITICAL_HIT", "Critical Hit", "NPC", "✨", "Critical strike motion with impact particles"),
        AnimationEntry("MAGIC_SPELL", "Magic Spell", "NPC", "🧙", "Evoker / Witch magic spell casting"),
        AnimationEntry("VILLAGER_HAPPY", "Villager Happy", "NPC", "💚", "Emerald trade particle happiness reaction"),
        AnimationEntry("VILLAGER_ANGRY", "Villager Angry", "NPC", "💢", "Thundercloud annoyance reaction"),
        AnimationEntry("JUMP", "Jump Leap", "NPC", "🦘", "Vertical upward leap jump")
    )

    init {
        val searchW = modalWidth - 28
        searchBox = EditBox(font, modalX + 14, modalY + 44, searchW, 16, Component.literal("Search"))
        searchBox.setMaxLength(100)
        searchBox.setHint(Component.literal("§8🔍 Search animations by name or description..."))
        searchBox.setEditable(true)
        searchBox.active = true
        searchBox.setResponder { vScrollOffset = 0f }
    }

    private fun getFilteredList(): List<AnimationEntry> {
        val query = searchBox.value.trim().lowercase()
        return allAnimations.filter { entry ->
            val matchesSystem = entry.system.equals(selectedSystem, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                    entry.id.lowercase().contains(query) ||
                    entry.displayName.lowercase().contains(query) ||
                    entry.description.lowercase().contains(query)
            matchesSystem && matchesQuery
        }
    }

    private fun getTotalContentHeight(): Int {
        val count = getFilteredList().size
        return count * 36 + 8
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Outer Card Container
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xEE0F172A.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 1, 0xFF38BDF8.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0x33FFFFFF)
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0x33FFFFFF)
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0x33FFFFFF)

        // Header Title
        guiGraphics.drawString(font, "🎬 Browse Animations & Poses", modalX + 14, modalY + 10, 0xFF38BDF8.toInt(), true)

        // Close Button (✖)
        val closeX = modalX + modalWidth - 24
        val closeY = modalY + 8
        val isCloseHovered = mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 14
        guiGraphics.fill(closeX, closeY, closeX + 16, closeY + 14, if (isCloseHovered) 0xFFDC2626.toInt() else 0xFF1E293B.toInt())
        guiGraphics.drawString(font, "✖", closeX + 4, closeY + 3, 0xFFFFFFFF.toInt(), false)

        // System Category Switcher Tabs
        val tabW = 120
        val tabCobbleX = modalX + 14
        val tabNpcX = tabCobbleX + tabW + 6
        val tabY = modalY + 24
        val isCobbleActive = selectedSystem.equals("COBBLEMON", ignoreCase = true)
        val isNpcActive = selectedSystem.equals("NPC", ignoreCase = true)

        val cobbleHover = mouseX >= tabCobbleX && mouseX <= tabCobbleX + tabW && mouseY >= tabY && mouseY <= tabY + 16
        val npcHover = mouseX >= tabNpcX && mouseX <= tabNpcX + tabW && mouseY >= tabY && mouseY <= tabY + 16

        guiGraphics.fill(tabCobbleX, tabY, tabCobbleX + tabW, tabY + 16, if (isCobbleActive) 0xFF0284C7.toInt() else if (cobbleHover) 0xFF334155.toInt() else 0xFF1E293B.toInt())
        guiGraphics.fill(tabCobbleX, tabY + 15, tabCobbleX + tabW, tabY + 16, if (isCobbleActive) 0xFF38BDF8.toInt() else 0xFF475569.toInt())
        guiGraphics.drawString(font, "🐾 Cobblemon", tabCobbleX + 22, tabY + 4, if (isCobbleActive) 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt(), false)

        guiGraphics.fill(tabNpcX, tabY, tabNpcX + tabW, tabY + 16, if (isNpcActive) 0xFF0284C7.toInt() else if (npcHover) 0xFF334155.toInt() else 0xFF1E293B.toInt())
        guiGraphics.fill(tabNpcX, tabY + 15, tabNpcX + tabW, tabY + 16, if (isNpcActive) 0xFF38BDF8.toInt() else 0xFF475569.toInt())
        guiGraphics.drawString(font, "👤 NPC & Mobs", tabNpcX + 22, tabY + 4, if (isNpcActive) 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt(), false)

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

        items.forEachIndexed { idx, entry ->
            val iy = contentTop + 4 + idx * 36 - scrollY
            if (iy + itemH >= contentTop && iy <= contentBottom) {
                val isHovered = mouseX >= contentLeft + 4 && mouseX <= contentLeft + 4 + itemW && mouseY >= iy && mouseY <= iy + itemH
                if (isHovered) hoveredIndex = idx

                val isSelected = entry.id.equals(initialSelected, ignoreCase = true)
                val bgCol = when {
                    isSelected -> 0x550284C7.toInt()
                    isHovered -> 0xAA1E293B.toInt()
                    else -> 0x77111827.toInt()
                }
                val borderCol = when {
                    isSelected -> 0xFF38BDF8.toInt()
                    isHovered -> 0xFF0EA5E9.toInt()
                    else -> 0x334B5563.toInt()
                }

                guiGraphics.fill(contentLeft + 4, iy, contentLeft + 4 + itemW, iy + itemH, bgCol)
                guiGraphics.fill(contentLeft + 4, iy, contentLeft + 4 + itemW, iy + 1, borderCol)
                guiGraphics.fill(contentLeft + 4, iy, contentLeft + 5, iy + itemH, borderCol)
                guiGraphics.fill(contentLeft + 4 + itemW - 1, iy, contentLeft + 4 + itemW, iy + itemH, borderCol)
                guiGraphics.fill(contentLeft + 4, iy + itemH - 1, contentLeft + 4 + itemW, iy + itemH, borderCol)

                // Icon badge
                guiGraphics.drawString(font, entry.icon, contentLeft + 10, iy + 10, 0xFFFFFFFF.toInt(), false)

                // Title & ID
                val titleText = "${entry.displayName} §8(${entry.id})"
                guiGraphics.drawString(font, titleText, contentLeft + 28, iy + 6, if (isSelected) 0xFF38BDF8.toInt() else 0xFFFFFFFF.toInt(), false)

                // Description
                val descText = font.plainSubstrByWidth(entry.description, itemW - 80)
                guiGraphics.drawString(font, descText, contentLeft + 28, iy + 18, 0xFF94A3B8.toInt(), false)

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
            guiGraphics.drawString(font, "No matching animations found for '${searchBox.value}'", contentLeft + 20, contentTop + 20, 0xFF94A3B8.toInt(), false)
        }

        guiGraphics.disableScissor()

        // Scrollbar
        val totalH = getTotalContentHeight()
        val maxScroll = (totalH - viewportH).coerceAtLeast(0)
        if (maxScroll > 0) {
            val sbX = contentRight - 5
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
        val closeX = modalX + modalWidth - 24
        val closeY = modalY + 8
        if (mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 14) {
            onClose()
            return true
        }

        // Tab switcher
        val tabW = 120
        val tabCobbleX = modalX + 14
        val tabNpcX = tabCobbleX + tabW + 6
        val tabY = modalY + 24
        if (mouseX >= tabCobbleX && mouseX <= tabCobbleX + tabW && mouseY >= tabY && mouseY <= tabY + 16) {
            selectedSystem = "COBBLEMON"
            vScrollOffset = 0f
            return true
        }
        if (mouseX >= tabNpcX && mouseX <= tabNpcX + tabW && mouseY >= tabY && mouseY <= tabY + 16) {
            selectedSystem = "NPC"
            vScrollOffset = 0f
            return true
        }

        if (searchBox.mouseClicked(mouseX, mouseY, button)) return true

        // Item row clicks
        if (mouseX >= contentLeft && mouseX <= contentRight && mouseY >= contentTop && mouseY <= contentBottom) {
            val items = getFilteredList()
            val itemW = modalWidth - 36
            val scrollY = vScrollOffset.toInt()

            items.forEachIndexed { idx, entry ->
                val iy = contentTop + 4 + idx * 36 - scrollY
                if (mouseX >= contentLeft + 4 && mouseX <= contentLeft + 4 + itemW && mouseY >= iy && mouseY <= iy + 32) {
                    onSelect(entry.id)
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
        return mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= modalY && mouseY <= modalY + modalHeight
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        return mouseX >= modalX && mouseX <= modalX + modalWidth && mouseY >= modalY && mouseY <= modalY + modalHeight
    }
}
