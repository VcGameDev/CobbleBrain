package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

enum class ResourcePickerType {
    SOUND,
    ENTITY,
    STRUCTURE,
    ITEM,
    EFFECT
}

class ResourcePickerModalWidget(
    val pickerType: ResourcePickerType,
    val font: Font,
    val screenWidth: Int,
    val screenHeight: Int,
    val currentSelectedId: String,
    val onSelect: (String) -> Unit,
    val onClose: () -> Unit
) {
    private val modalWidth = 470.coerceAtMost(screenWidth - 20)
    private val modalHeight = 290.coerceAtMost(screenHeight - 20)
    private val modalX = maxOf(10, (screenWidth - modalWidth) / 2)
    private val modalY = maxOf(10, (screenHeight - modalHeight) / 2)

    private val searchBox: EditBox
    private val customIdBox: EditBox
    private val closeButton: Button
    private val applyCustomBtn: Button

    private var selectedCategoryIndex: Int = 0 // 0 = All, last = Manual/Custom
    private var scrollOffset: Double = 0.0
    private var categoryScrollOffset: Double = 0.0

    private val allResources: List<String>

    init {
        searchBox = EditBox(font, modalX + 15, modalY + 28, modalWidth - 30, 16, Component.literal("Search"))
        searchBox.setMaxLength(2000)
        searchBox.setHint(Component.literal("§8🔍 Type to filter catalog or type a custom ID..."))
        searchBox.setEditable(true)
        searchBox.active = true
        searchBox.setResponder {
            scrollOffset = 0.0
        }

        customIdBox = EditBox(font, modalX + 150, modalY + 80, modalWidth - 165, 18, Component.literal("Custom ID"))
        customIdBox.setMaxLength(2000)
        customIdBox.value = currentSelectedId
        customIdBox.setHint(Component.literal("§8e.g. my_datapack:my_resource"))
        customIdBox.setEditable(true)
        customIdBox.active = true

        applyCustomBtn = Button.builder(Component.literal("✔ Confirm and Use this ID")) {
            val raw = customIdBox.value.trim()
            if (raw.isNotBlank()) {
                val formatted = if (!raw.contains(":")) "minecraft:$raw" else raw
                onSelect(formatted)
                onClose()
            }
        }.bounds(modalX + 150, modalY + 160, modalWidth - 165, 20).build()

        closeButton = Button.builder(Component.literal("✖ Close")) {
            onClose()
        }.bounds(modalX + modalWidth - 75, modalY + 5, 65, 16).build()

        allResources = when (pickerType) {
            ResourcePickerType.SOUND -> {
                val registryList = try { BuiltInRegistries.SOUND_EVENT.keySet().map { it.toString() } } catch (_: Exception) { emptyList() }
                val fallbackList = listOf(
                    "minecraft:entity.player.levelup", "minecraft:entity.experience_orb.pickup",
                    "minecraft:ui.button.click", "minecraft:block.chest.open", "minecraft:block.chest.close",
                    "minecraft:block.portal.trigger", "minecraft:entity.wither.spawn", "minecraft:entity.ender_dragon.growl",
                    "minecraft:entity.villager.yes", "minecraft:entity.villager.no", "minecraft:entity.villager.trade",
                    "minecraft:music.game", "minecraft:music.creative", "minecraft:music.credits", "minecraft:music.dragon",
                    "minecraft:music.end", "minecraft:music.nether.nether_wastes", "minecraft:music.under_water",
                    "cobblemon:battle.victory", "cobblemon:battle.wild", "cobblemon:battle.trainer", "cobblemon:pc.on", "cobblemon:pc.off"
                )
                (registryList + fallbackList).distinct().sorted()
            }
            ResourcePickerType.ENTITY -> {
                val registryList = try { BuiltInRegistries.ENTITY_TYPE.keySet().map { it.toString() } } catch (_: Exception) { emptyList() }
                val fallbackList = listOf(
                    "minecraft:villager", "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper",
                    "minecraft:spider", "minecraft:enderman", "minecraft:iron_golem", "minecraft:pig",
                    "minecraft:cow", "minecraft:sheep", "minecraft:chicken", "minecraft:horse",
                    "minecraft:wolf", "minecraft:cat", "minecraft:blaze", "minecraft:witch",
                    "minecraft:wither", "minecraft:warden", "minecraft:allay", "minecraft:frog",
                    "cobblemon:pokemon"
                )
                (registryList + fallbackList).distinct().sorted()
            }
            ResourcePickerType.ITEM -> {
                val registryList = try { BuiltInRegistries.ITEM.keySet().map { it.toString() } } catch (_: Exception) { emptyList() }
                val fallbackList = listOf(
                    "minecraft:diamond", "minecraft:emerald", "minecraft:iron_ingot", "minecraft:gold_ingot",
                    "minecraft:netherite_ingot", "minecraft:stick", "minecraft:apple", "minecraft:golden_apple",
                    "minecraft:bread", "minecraft:cooked_beef", "minecraft:diamond_sword", "minecraft:diamond_pickaxe",
                    "minecraft:diamond_axe", "minecraft:diamond_shovel", "minecraft:diamond_helmet", "minecraft:diamond_chestplate",
                    "minecraft:diamond_leggings", "minecraft:diamond_boots", "minecraft:shield", "minecraft:bow",
                    "minecraft:potion", "cobblemon:poke_ball", "cobblemon:great_ball", "cobblemon:ultra_ball",
                    "cobblemon:master_ball", "cobblemon:potion", "cobblemon:super_potion", "cobblemon:hyper_potion",
                    "cobblemon:max_potion", "cobblemon:revive", "cobblemon:rare_candy", "cobblemon:exp_candy_xs",
                    "cobblemon:exp_candy_s", "cobblemon:exp_candy_m", "cobblemon:exp_candy_l", "cobblemon:exp_candy_xl"
                )
                (registryList + fallbackList).distinct().sorted()
            }
            ResourcePickerType.STRUCTURE -> {
                val fallbackList = listOf(
                    "minecraft:village_plains", "minecraft:village_desert", "minecraft:village_savanna",
                    "minecraft:village_snowy", "minecraft:village_taiga", "minecraft:ancient_city",
                    "minecraft:desert_pyramid", "minecraft:igloo", "minecraft:jungle_pyramid",
                    "minecraft:mansion", "minecraft:monument", "minecraft:mineshaft",
                    "minecraft:nether_fortress", "minecraft:bastion_remnant", "minecraft:end_city",
                    "minecraft:stronghold", "minecraft:swamp_hut", "minecraft:pillager_outpost",
                    "minecraft:trail_ruins", "minecraft:trial_chambers", "minecraft:shipwreck"
                )
                fallbackList.distinct().sorted()
            }
            ResourcePickerType.EFFECT -> {
                val registryList = try { BuiltInRegistries.MOB_EFFECT.keySet().map { it.toString() } } catch (_: Exception) { emptyList() }
                val fallbackList = listOf(
                    "minecraft:speed", "minecraft:slowness", "minecraft:haste", "minecraft:mining_fatigue",
                    "minecraft:strength", "minecraft:instant_health", "minecraft:instant_damage", "minecraft:jump_boost",
                    "minecraft:nausea", "minecraft:regeneration", "minecraft:resistance", "minecraft:fire_resistance",
                    "minecraft:water_breathing", "minecraft:invisibility", "minecraft:blindness", "minecraft:night_vision",
                    "minecraft:hunger", "minecraft:weakness", "minecraft:poison", "minecraft:wither",
                    "minecraft:health_boost", "minecraft:absorption", "minecraft:saturation", "minecraft:glowing",
                    "minecraft:levitation", "minecraft:luck", "minecraft:unluck", "minecraft:slow_falling",
                    "minecraft:conduit_power", "minecraft:dolphins_grace", "minecraft:bad_omen",
                    "minecraft:hero_of_the_village", "minecraft:darkness", "minecraft:trial_omen",
                    "minecraft:raid_omen", "minecraft:wind_charged", "minecraft:weaving",
                    "minecraft:oozing", "minecraft:infested"
                )
                (registryList + fallbackList).distinct().sorted()
            }
        }
    }

    private fun checkIdStatus(id: String): Triple<Boolean, Boolean, String> {
        val trimmed = id.trim().lowercase()
        if (trimmed.isBlank()) return Triple(false, false, "Empty ID")
        val rl = ResourceLocation.tryParse(trimmed) ?: return Triple(false, false, "Invalid format (use namespace:id)")

        val isRegistered = when (pickerType) {
            ResourcePickerType.SOUND -> (try { BuiltInRegistries.SOUND_EVENT.containsKey(rl) } catch (_: Exception) { false }) || allResources.contains(trimmed)
            ResourcePickerType.ENTITY -> (try { BuiltInRegistries.ENTITY_TYPE.containsKey(rl) } catch (_: Exception) { false }) || allResources.contains(trimmed)
            ResourcePickerType.ITEM -> (try { BuiltInRegistries.ITEM.containsKey(rl) } catch (_: Exception) { false }) || allResources.contains(trimmed)
            ResourcePickerType.STRUCTURE -> allResources.contains(trimmed)
            ResourcePickerType.EFFECT -> (try { BuiltInRegistries.MOB_EFFECT.containsKey(rl) } catch (_: Exception) { false }) || allResources.contains(trimmed)
        }

        return if (isRegistered) {
            Triple(true, true, "🟢 Registered in Built-in Game/Mod Registries")
        } else {
            Triple(true, false, "🟡 Valid Format (Datapack / Custom Resource)")
        }
    }

    private fun getCategories(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        list.add(Pair("ALL", "📁 All (${allResources.size})"))

        val namespaces = allResources.map { if (it.contains(":")) it.substringBefore(":") else "minecraft" }.distinct()
        namespaces.forEach { ns ->
            val count = allResources.count { it.startsWith("$ns:") }
            val label = when (ns.lowercase()) {
                "minecraft" -> "⛏️ Minecraft ($count)"
                "cobblemon" -> "🐾 Cobblemon ($count)"
                else -> "📦 $ns ($count)"
            }
            list.add(Pair(ns, label))
        }

        list.add(Pair("CUSTOM_MANUAL", "✏️ Custom ID / Datapack"))
        return list
    }

    private data class ResourceCard(
        val id: String,
        val displayName: String,
        val namespace: String,
        val icon: String,
        val isCustomCandidate: Boolean = false,
        val isRegistered: Boolean = true
    )

    private fun getFilteredItems(): List<ResourceCard> {
        val query = searchBox.value.trim().lowercase()
        val categories = getCategories()
        val selectedCatKey = categories.getOrNull(selectedCategoryIndex)?.first ?: "ALL"

        if (selectedCatKey == "CUSTOM_MANUAL") return emptyList()

        val defaultIcon = when (pickerType) {
            ResourcePickerType.SOUND -> "🎵"
            ResourcePickerType.ENTITY -> "👾"
            ResourcePickerType.STRUCTURE -> "🏛️"
            ResourcePickerType.ITEM -> "📦"
            ResourcePickerType.EFFECT -> "🧪"
        }

        val result = mutableListOf<ResourceCard>()

        if (query.isNotEmpty()) {
            val formattedQuery = if (!query.contains(":")) "minecraft:$query" else query
            val status = checkIdStatus(query)
            val isExactInList = allResources.any { it.equals(formattedQuery, ignoreCase = true) }

            if (!isExactInList) {
                result.add(
                    ResourceCard(
                        id = formattedQuery,
                        displayName = "Use Typed ID: \"$formattedQuery\"",
                        namespace = if (formattedQuery.contains(":")) formattedQuery.substringBefore(":") else "custom",
                        icon = "✨",
                        isCustomCandidate = true,
                        isRegistered = status.second
                    )
                )
            }
        }

        val matching = allResources.filter { id ->
            val ns = if (id.contains(":")) id.substringBefore(":") else "minecraft"
            val matchesCat = (selectedCatKey == "ALL" || ns.equals(selectedCatKey, ignoreCase = true))
            val matchesQuery = query.isEmpty() || id.lowercase().contains(query)
            matchesCat && matchesQuery
        }.map { id ->
            val path = if (id.contains(":")) id.substringAfter(":") else id
            val ns = if (id.contains(":")) id.substringBefore(":") else "minecraft"
            val icon = when {
                pickerType == ResourcePickerType.EFFECT && (path.contains("strength") || path.contains("speed") || path.contains("haste") || path.contains("jump")) -> "⚡"
                pickerType == ResourcePickerType.EFFECT && (path.contains("regen") || path.contains("health") || path.contains("absorption")) -> "💖"
                pickerType == ResourcePickerType.EFFECT && (path.contains("resist") || path.contains("fire_resist")) -> "🛡️"
                pickerType == ResourcePickerType.EFFECT && (path.contains("poison") || path.contains("wither") || path.contains("damage") || path.contains("slowness") || path.contains("weakness") || path.contains("blindness") || path.contains("darkness")) -> "💀"
                pickerType == ResourcePickerType.EFFECT && (path.contains("invisibility") || path.contains("glowing") || path.contains("night_vision")) -> "👁️"
                pickerType == ResourcePickerType.SOUND && path.contains("music") -> "🎼"
                pickerType == ResourcePickerType.SOUND && path.contains("player") -> "👤"
                pickerType == ResourcePickerType.SOUND && path.contains("entity") -> "🐾"
                pickerType == ResourcePickerType.ENTITY && ns == "cobblemon" -> "🐾"
                pickerType == ResourcePickerType.ITEM && (path.contains("sword") || path.contains("axe") || path.contains("bow")) -> "🗡️"
                pickerType == ResourcePickerType.ITEM && (path.contains("helmet") || path.contains("chestplate") || path.contains("leggings") || path.contains("boots")) -> "🛡️"
                pickerType == ResourcePickerType.ITEM && path.contains("ball") -> "🔴"
                else -> defaultIcon
            }
            ResourceCard(id, path, ns, icon, isCustomCandidate = false, isRegistered = true)
        }

        result.addAll(matching)
        return result
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Modal Frame
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF14141A.toInt())
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + 24, 0xFF22222E.toInt())
        guiGraphics.fill(modalX, modalY, modalX + 1, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX + modalWidth - 1, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())
        guiGraphics.fill(modalX, modalY + modalHeight - 1, modalX + modalWidth, modalY + modalHeight, 0xFF3D5AFE.toInt())

        val title = when (pickerType) {
            ResourcePickerType.SOUND -> "🎵 Native Sound Catalog (SoundEvents)"
            ResourcePickerType.ENTITY -> "👾 Native Entity Catalog (EntityTypes)"
            ResourcePickerType.STRUCTURE -> "🏛️ Registered Structures Catalog"
            ResourcePickerType.ITEM -> "📦 Native Item Catalog"
            ResourcePickerType.EFFECT -> "🧪 Native Potion & Mob Effects Catalog"
        }
        guiGraphics.drawString(font, title, modalX + 10, modalY + 7, 0xFF00FFCC.toInt(), false)

        closeButton.render(guiGraphics, mouseX, mouseY, partialTick)
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick)

        val contentY = modalY + 48
        val contentH = modalHeight - 56

        // 1. LEFT PANEL: Categories
        val catW = 125
        val catX = modalX + 12
        guiGraphics.fill(catX, contentY, catX + catW, contentY + contentH, 0xFF0D0D12.toInt())
        guiGraphics.fill(catX + catW, contentY, catX + catW + 1, contentY + contentH, 0xFF282836.toInt())

        val categories = getCategories()
        val isManualMode = categories.getOrNull(selectedCategoryIndex)?.first == "CUSTOM_MANUAL"

        guiGraphics.enableScissor(catX, contentY, catX + catW, contentY + contentH)
        categories.forEachIndexed { idx, (key, label) ->
            val cy = (contentY + 4 + idx * 22 - categoryScrollOffset).toInt()
            if (cy + 18 >= contentY && cy <= contentY + contentH - 2) {
                val isSelected = (idx == selectedCategoryIndex)
                val isHovered = mouseX >= catX + 2 && mouseX <= catX + catW - 2 && mouseY >= cy && mouseY <= cy + 18

                val bg = when {
                    isSelected -> if (key == "CUSTOM_MANUAL") 0xFF6200EA.toInt() else 0xFF3D5AFE.toInt()
                    isHovered -> 0xFF222230.toInt()
                    else -> 0x00000000
                }
                if (bg != 0) {
                    guiGraphics.fill(catX + 2, cy, catX + catW - 2, cy + 18, bg)
                }

                val textColor = if (isSelected) 0xFFFFFFFF.toInt() else if (isHovered) 0xFF00FFCC.toInt() else if (key == "CUSTOM_MANUAL") 0xFFFFD700.toInt() else 0xFFA0A0A0.toInt()
                val truncLabel = font.plainSubstrByWidth(label, catW - 8)
                guiGraphics.drawString(font, truncLabel, catX + 6, cy + 5, textColor, false)
            }
        }
        guiGraphics.disableScissor()

        // 2. RIGHT PANEL: Resource Cards or Manual Custom Tab
        val listX = catX + catW + 6
        val listW = modalWidth - (catW + 30)

        if (isManualMode) {
            guiGraphics.fill(listX, contentY, listX + listW, contentY + contentH, 0xFF0D0D12.toInt())

            guiGraphics.drawString(font, "✏️ Manual Identifier Entry:", listX + 10, contentY + 12, 0xFFFFFFFF.toInt(), false)
            guiGraphics.drawString(font, "Allows entering IDs from mods, datapacks, or custom resources.", listX + 10, contentY + 24, 0xFFA0A0A0.toInt(), false)

            customIdBox.x = listX + 10
            customIdBox.y = contentY + 40
            customIdBox.width = listW - 20
            customIdBox.render(guiGraphics, mouseX, mouseY, partialTick)

            val status = checkIdStatus(customIdBox.value)
            val statusColor = if (!status.first) 0xFFFF5555.toInt() else if (status.second) 0xFF00FFCC.toInt() else 0xFFFFD700.toInt()

            guiGraphics.fill(listX + 10, contentY + 68, listX + listW - 10, contentY + 98, 0xFF181824.toInt())
            guiGraphics.fill(listX + 10, contentY + 68, listX + 13, contentY + 98, statusColor)

            guiGraphics.drawString(font, "Registry Status:", listX + 18, contentY + 72, 0xFFA0A0A0.toInt(), false)
            val truncStatus = font.plainSubstrByWidth(status.third, listW - 35)
            guiGraphics.drawString(font, truncStatus, listX + 18, contentY + 84, statusColor, false)

            applyCustomBtn.x = listX + 10
            applyCustomBtn.y = contentY + 112
            applyCustomBtn.width = listW - 20
            applyCustomBtn.active = customIdBox.value.isNotBlank() && status.first
            applyCustomBtn.render(guiGraphics, mouseX, mouseY, partialTick)
        } else {
            val filtered = getFilteredItems()
            val itemH = 28

            guiGraphics.fill(listX, contentY, listX + listW, contentY + contentH, 0xFF0D0D12.toInt())

            if (filtered.isEmpty()) {
                guiGraphics.drawCenteredString(font, "No resources found.", listX + listW / 2, contentY + contentH / 2 - 4, 0xFF888899.toInt())
            } else {
                guiGraphics.enableScissor(listX, contentY, listX + listW, contentY + contentH)
                filtered.forEachIndexed { idx, card ->
                    val iy = (contentY + 4 + idx * (itemH + 4) - scrollOffset).toInt()
                    if (iy + itemH >= contentY && iy <= contentY + contentH) {
                        val isSelected = (card.id == currentSelectedId)
                        val isHovered = mouseX >= listX + 4 && mouseX <= listX + listW - 4 && mouseY >= iy && mouseY <= iy + itemH

                        val bg = when {
                            card.isCustomCandidate -> if (isHovered) 0xFF4A148C.toInt() else 0xFF2A0845.toInt()
                            isSelected -> 0xFF1B3A4B.toInt()
                            isHovered -> 0xFF222232.toInt()
                            else -> 0xFF16161E.toInt()
                        }
                        val border = when {
                            card.isCustomCandidate -> if (card.isRegistered) 0xFF00FFCC.toInt() else 0xFFFFD700.toInt()
                            isSelected -> 0xFF00FFCC.toInt()
                            isHovered -> 0xFFFFD700.toInt()
                            else -> 0xFF282836.toInt()
                        }

                        guiGraphics.fill(listX + 4, iy, listX + listW - 4, iy + itemH, bg)
                        guiGraphics.fill(listX + 4, iy, listX + listW - 4, iy + 1, border)
                        guiGraphics.fill(listX + 4, iy + itemH - 1, listX + listW - 4, iy + itemH, border)
                        guiGraphics.fill(listX + 4, iy, listX + 5, iy + itemH, border)
                        guiGraphics.fill(listX + listW - 5, iy, listX + listW - 4, iy + itemH, border)

                        // Icon & Title
                        guiGraphics.drawString(font, card.icon, listX + 10, iy + 6, 0xFFFFFFFF.toInt(), false)
                        val titleText = font.plainSubstrByWidth(card.displayName, listW - 45)
                        guiGraphics.drawString(font, titleText, listX + 28, iy + 4, if (card.isCustomCandidate) 0xFFFFD700.toInt() else 0xFFFFFFFF.toInt(), false)

                        // Full ID or verification tag
                        val subText = if (card.isCustomCandidate) {
                            if (card.isRegistered) "🟢 Registered in Game/Mod (Click to use)" else "🟡 Custom ID / Datapack (Click to use)"
                        } else {
                            card.id
                        }
                        val idText = font.plainSubstrByWidth(subText, listW - 45)
                        guiGraphics.drawString(font, idText, listX + 28, iy + 15, if (card.isCustomCandidate) (if (card.isRegistered) 0xFF00FFCC.toInt() else 0xFFFFD700.toInt()) else 0xFF888899.toInt(), false)

                        if (isSelected) {
                            guiGraphics.drawString(font, "✔", listX + listW - 20, iy + 10, 0xFF00FFCC.toInt(), false)
                        }
                    }
                }
                guiGraphics.disableScissor()
            }
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (closeButton.mouseClicked(mouseX, mouseY, button)) return true

        val categories = getCategories()
        val isManualMode = categories.getOrNull(selectedCategoryIndex)?.first == "CUSTOM_MANUAL"

        val clickedSearch = searchBox.mouseClicked(mouseX, mouseY, button)
        searchBox.isFocused = clickedSearch
        if (clickedSearch) return true

        val contentY = modalY + 48
        val contentH = modalHeight - 56
        val catW = 125
        val catX = modalX + 12

        // Category Click
        if (mouseX >= catX && mouseX <= catX + catW && mouseY >= contentY && mouseY <= contentY + contentH) {
            categories.forEachIndexed { idx, _ ->
                val cy = contentY + 4 + idx * 22 - categoryScrollOffset
                if (mouseY >= cy && mouseY <= cy + 18) {
                    selectedCategoryIndex = idx
                    scrollOffset = 0.0
                    return true
                }
            }
        }

        // Right side interaction
        val listX = catX + catW + 6
        val listW = modalWidth - (catW + 30)

        if (isManualMode) {
            val clickedCustom = customIdBox.mouseClicked(mouseX, mouseY, button)
            customIdBox.isFocused = clickedCustom
            if (clickedCustom) return true
            if (applyCustomBtn.mouseClicked(mouseX, mouseY, button)) return true
        } else {
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= contentY && mouseY <= contentY + contentH) {
                val filtered = getFilteredItems()
                val itemH = 28

                filtered.forEachIndexed { idx, card ->
                    val iy = contentY + 4 + idx * (itemH + 4) - scrollOffset
                    if (mouseY >= iy && mouseY <= iy + itemH) {
                        onSelect(card.id)
                        onClose()
                        return true
                    }
                }
            }
        }

        return true
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        val contentY = modalY + 48
        val contentH = modalHeight - 56
        val catW = 125
        val catX = modalX + 12

        if (mouseX >= catX && mouseX <= catX + catW && mouseY >= contentY && mouseY <= contentY + contentH) {
            val categories = getCategories()
            val totalCatH = categories.size * 22 + 4
            val maxScroll = maxOf(0.0, (totalCatH - contentH).toDouble())
            if (scrollY < 0) {
                categoryScrollOffset = (categoryScrollOffset + 24).coerceAtMost(maxScroll)
            } else if (scrollY > 0) {
                categoryScrollOffset = (categoryScrollOffset - 24).coerceAtLeast(0.0)
            }
            return true
        }

        val categories = getCategories()
        val isManualMode = categories.getOrNull(selectedCategoryIndex)?.first == "CUSTOM_MANUAL"

        if (!isManualMode) {
            val listX = catX + catW + 6
            val listW = modalWidth - (catW + 30)
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= contentY && mouseY <= contentY + contentH) {
                val filtered = getFilteredItems()
                val itemH = 28
                val totalItemsH = filtered.size * (itemH + 4) + 4
                val maxScroll = maxOf(0.0, (totalItemsH - contentH).toDouble())
                if (scrollY < 0) {
                    scrollOffset = (scrollOffset + 32).coerceAtMost(maxScroll)
                } else if (scrollY > 0) {
                    scrollOffset = (scrollOffset - 32).coerceAtLeast(0.0)
                }
                return true
            }
        }

        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val categories = getCategories()
        val isManualMode = categories.getOrNull(selectedCategoryIndex)?.first == "CUSTOM_MANUAL"

        if (isManualMode && customIdBox.isFocused) {
            if (customIdBox.keyPressed(keyCode, scanCode, modifiers)) return true
        } else if (searchBox.isFocused) {
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) return true
        }

        if (keyCode == 256) { // ESC
            onClose()
            return true
        }
        return false
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        val categories = getCategories()
        val isManualMode = categories.getOrNull(selectedCategoryIndex)?.first == "CUSTOM_MANUAL"

        if (isManualMode && customIdBox.isFocused) {
            return customIdBox.charTyped(codePoint, modifiers)
        } else if (searchBox.isFocused) {
            return searchBox.charTyped(codePoint, modifiers)
        }
        return false
    }
}
