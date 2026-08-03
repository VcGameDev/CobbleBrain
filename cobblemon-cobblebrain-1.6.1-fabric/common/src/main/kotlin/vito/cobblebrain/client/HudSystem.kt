package vito.cobblebrain.client

import com.cobblemon.mod.common.client.CobblemonClient
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import kotlin.math.atan2
import kotlin.math.sin

object HudSystem {

    // Lista de comandos disponíveis
    private val commands = listOf("IDLE", "ATTACK", "PROTECT", "BUFF", "DEBUFF ENEMY", "EAT", "COOK", "GROW", "REPAIR", "SHIFT", "FISH", "NIGHTMARE", "LIGHT", "SCOUT", "TELEPORT", "SIT")
    private var selectedActionIndex = 0
    private var isVisible = true
    private val cooldowns = mutableMapOf<String, Long>()

    private val actionRequirements = mapOf(
        "cook" to "fire",
        "grow" to "grass",
        "repair" to "steel",
        "shift" to "ghost",
        "fish" to "water",
        "nightmare" to "dark",
        "light" to "electric",
        "scout" to "flying",
        "teleport" to "psychic"
    )

    private fun isCommandAvailable(
        command: String
    ): Boolean {
        if (!vito.cobblebrain.config.SyncedConfig.isActionActive(command)) {
            return false
        }

        val requiredType =
            actionRequirements[
                command.lowercase()
            ] ?: return true

        return CobblemonClient
            .storage
            .party
            .any { pokemon ->

                pokemon != null &&
                        pokemon.currentHealth > 0 &&
                        pokemon.types.any {
                            it.name.lowercase() == requiredType
                        }
            }
    }

    private fun getSortedCommands(): List<String> {
        return commands.sortedWith(
            compareByDescending<String> { isCommandAvailable(it) }
                .thenBy { commands.indexOf(it) }
        )
    }

    private fun getAvailableCommands(): List<String> {
        return getSortedCommands()
            .filter {
                isCommandAvailable(it)
            }
    }

    fun toggleVisibility() {
        isVisible = !isVisible
    }

    /**
     * Ponto de entrada principal para toda a renderização de HUD do CobbleBrain.
     */
    fun render(guiGraphics: GuiGraphics, tickDelta: Float) {
        val client = Minecraft.getInstance()
        if (client.player == null || client.options.hideGui) return

        // 1. HUD de Missões (Já funcional)
        renderQuestHud(guiGraphics, client)

        // 2. HUD de Comandos do Pokémon (Placeholder)
        renderCommandsHud(guiGraphics, client)

        // 4. Indicador de Ação (Próximo ao ícone do Cobblemon - Placeholder)
        renderActionIndicator(guiGraphics, client)
    }

    // ===================================================================================
    // 1. QUEST HUD SYSTEM
    // ===================================================================================
    private fun renderQuestHud(guiGraphics: GuiGraphics, client: Minecraft) {
        val questsJson = CobblebrainClientCommon.currentQuestsJson
        val screenWidth = client.window.guiScaledWidth
        val x = screenWidth - 140 // Adjusted for increased width (120 -> 140)
        var y = 10

        if (questsJson == "[]" || questsJson.isBlank()) {
            return
        }

        try {
            val questsArray = JsonParser.parseString(questsJson).asJsonArray
            val activeQuests = questsArray.filter {
                it.asJsonObject.get("status")?.asString == "IN_PROGRESS"
            }

            if (activeQuests.isEmpty()) {
                return
            }

            for (i in 0 until minOf(activeQuests.size, 2)) {
                val quest = activeQuests[i].asJsonObject
                val type = quest.get("type")?.asString ?: "generic"
                val isLarge = type == "ADVICE" || type == "ITEM"
                val currentHeight = if (isLarge) 34 else 24 // Even more compressed (38/28 -> 34/24)

                renderSingleQuest(guiGraphics, client, quest, x, y, currentHeight)
                y += currentHeight + 4
            }

        } catch (e: Exception) {}
    }

    private fun renderSingleQuest(guiGraphics: GuiGraphics, client: Minecraft, quest: com.google.gson.JsonObject, x: Int, y: Int, boxHeight: Int) {
        val type = quest.get("type")?.asString ?: return
        val giverName = quest.get("giverName")?.asString ?: "someone"

        // Background box (increased width to 130)
        guiGraphics.fill(x, y, x + 130, y + boxHeight, 0xAA000000.toInt())
        guiGraphics.fill(x, y, x + 2, y + boxHeight, 0xFF55FF55.toInt())

        val title = when(type) {
            "BATTLE" -> "Battle Mission"
            "ITEM" -> "Item Delivery"
            "ADVICE" -> "Advice Needed"
            "TREASURE" -> "Lost Item Search"
            else -> "Quest"
        }
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate((x + 6).toDouble(), (y + 3).toDouble(), 0.0)
        guiGraphics.pose().scale(0.95f, 0.95f, 1f) // Increased title scale
        guiGraphics.drawString(client.font, title, 0, 0, 0xFFFFCC00.toInt())
        guiGraphics.pose().popPose()

        var progress = 0f
        var progressText = ""

        when(type) {
            "BATTLE" -> {
                val target = quest.get("targetSpecies")?.asString ?: "Target"
                val status = quest.get("status")?.asString ?: "IN_PROGRESS"
                progress = if (status == "COMPLETED") 1f else 0f
                progressText = "Defeat $target in a battle"
            }
            "ITEM" -> {
                val target = quest.get("target")?.asString ?: "Item"
                val collected = quest.get("collected")?.asInt ?: 0
                val amount = quest.get("amount")?.asInt ?: 1
                progress = (collected.toFloat() / amount.toFloat()).coerceIn(0f, 1f)
                progressText = "Bring $amount $target to $giverName ($collected/$amount)"
            }
            "ADVICE" -> {
                val points = quest.get("points")?.asInt ?: 0
                val issue = quest.get("issue")?.asString ?: "problem"
                progress = ((points + 5) / 10f).coerceIn(0f, 1f)
                progressText = "Help $giverName with their $issue ($points/5)"
            }
            "LOCATION", "TREASURE" -> {
                val tx = quest.get("targetX")?.asInt ?: 0
                val tz = quest.get("targetZ")?.asInt ?: 0
                val playerPos = client.player?.position()
                if (playerPos != null) {
                    val dist = playerPos.distanceTo(net.minecraft.world.phys.Vec3(tx.toDouble(), playerPos.y, tz.toDouble()))
                    val angle = Math.toDegrees(atan2(tz - playerPos.z, tx - playerPos.x))
                    val playerYaw = client.player!!.yRot % 360
                    val relativeAngle = (angle - (playerYaw - 90) + 360) % 360 - 180
                    val arrow = when {
                        relativeAngle > -22.5 && relativeAngle <= 22.5 -> "↑"
                        relativeAngle > 22.5 && relativeAngle <= 67.5 -> "↗"
                        relativeAngle > 67.5 && relativeAngle <= 112.5 -> "→"
                        relativeAngle > 112.5 && relativeAngle <= 157.5 -> "↘"
                        relativeAngle > 157.5 || relativeAngle <= -157.5 -> "↓"
                        relativeAngle > -157.5 && relativeAngle <= -112.5 -> "↙"
                        relativeAngle > -112.5 && relativeAngle <= -67.5 -> "←"
                        else -> "↖"
                    }
                    progress = (1.0f - (dist.toFloat() / 400f)).coerceIn(0f, 1f)
                    progressText = "Target: ${dist.toInt()}m $arrow"
                }
            }
            else -> {
                progressText = "Active"
            }
        }
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate((x + 6).toDouble(), (y + 12).toDouble(), 0.0)
        guiGraphics.pose().scale(0.66f, 0.66f, 1f)

        // Split text with increased width (120 units approx)
        val maxWidth = (120 / 0.66f).toInt()
        val wrappedLines = client.font.split(net.minecraft.network.chat.Component.literal(progressText), maxWidth)

        var lineY = 0
        val lineLimit = if (boxHeight > 30) 2 else 1
        for (line in wrappedLines.take(lineLimit)) {
            guiGraphics.drawString(client.font, line, 0, lineY, 0xFFDDDDDD.toInt(), false)
            lineY += 10
        }

        guiGraphics.pose().popPose()

        val barWidth = 118
        val barY = y + boxHeight - 5 // Even closer to bottom
        guiGraphics.fill(x + 6, barY, x + 6 + barWidth, barY + 2, 0x44FFFFFF)
        guiGraphics.fill(x + 6, barY, x + 6 + (barWidth * progress).toInt(), barY + 2, 0xFF55FF55.toInt())

        val percent = (progress * 100).toInt()
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate((x + 108).toDouble(), (y + 3).toDouble(), 0.0)
        guiGraphics.pose().scale(0.75f, 0.75f, 1f)
        guiGraphics.drawString(client.font, "$percent%", 0, 0, 0xFFFFFFFF.toInt())
        guiGraphics.pose().popPose()
    }


    // ===================================================================================
    // 2. POKEMON COMMANDS HUD
    // ===================================================================================
    private fun renderCommandsHud(guiGraphics: GuiGraphics, client: Minecraft) {
        if (!isVisible) return

        val screenWidth = client.window.guiScaledWidth
        val screenHeight = client.window.guiScaledHeight

        // Centro-Direito (Compacto: Largura dinâmica, Item 10)
        val itemHeight = 10
        val sortedCommands = getSortedCommands()
        val totalCount = sortedCommands.size

        // Calcula a largura do menu com base no nome mais longo (escala 0.7)
        val textScale = 0.7f
        val minMenuWidth = 42
        val longestNameWidth = sortedCommands.maxOfOrNull { cmd ->
            val displayName = net.minecraft.client.resources.language.I18n.get("cobblebrain.action.${cmd.lowercase()}")
            ((client.font.width("> $displayName") * textScale) + 6).toInt()
        } ?: minMenuWidth
        val menuWidth = maxOf(minMenuWidth, longestNameWidth)

        val maxVisible = 10
        var startVisible = 0
        if (totalCount > maxVisible) {
            startVisible = selectedActionIndex - (maxVisible / 2)
            if (startVisible < 0) {
                startVisible = 0
            } else if (startVisible + maxVisible > totalCount) {
                startVisible = totalCount - maxVisible
            }
        }
        val endVisible = minOf(startVisible + maxVisible, totalCount)
        val visibleCount = endVisible - startVisible
        val totalHeight = visibleCount * itemHeight
        val x = screenWidth - menuWidth - 8
        val y = (screenHeight - totalHeight) / 2

        // Fundo
        guiGraphics.fill(x - 2, y - 2, x + menuWidth + 2, y + totalHeight + 2, 0x99000000.toInt())
        guiGraphics.fill(x - 2, y - 2, x - 1, y + totalHeight + 2, 0xFF5555FF.toInt())

        val hasAbove = startVisible > 0
        val hasBelow = endVisible < totalCount
        val centerX = x + (menuWidth / 2)

        if (hasAbove) {
            val upStr = "▲"
            val upWidth = client.font.width(upStr)
            guiGraphics.drawString(client.font, upStr, centerX - (upWidth / 2), y - 10, 0xFFFFFFFF.toInt(), false)
        }

        if (hasBelow) {
            val downStr = "▼"
            val downWidth = client.font.width(downStr)
            guiGraphics.drawString(client.font, downStr, centerX - (downWidth / 2), y + totalHeight + 2, 0xFFFFFFFF.toInt(), false)
        }

        for (visibleIndex in 0 until visibleCount) {
            val index = startVisible + visibleIndex
            val cmd = sortedCommands[index]
            val isSelected = index == selectedActionIndex
            val itemY = y + (visibleIndex * itemHeight)
            val cmdColor = getCommandColor(cmd)
            val remaining = getCooldownRemaining(cmd)

            guiGraphics.pose().pushPose()
            guiGraphics.pose().translate((x + 2).toDouble(), (itemY + 2).toDouble(), 0.0)
            guiGraphics.pose().scale(0.7f, 0.7f, 1f)

            val displayName = net.minecraft.client.resources.language.I18n.get("cobblebrain.action.${cmd.lowercase()}")

            if (isSelected) {
                val time = client.level?.gameTime ?: 0L
                val pulse = (sin(time.toDouble() / 4.0) * 20 + 50).toInt()
                guiGraphics.pose().popPose()

                // Se estiver em cooldown, pulsa em Vermelho, senão em Azul
                val pulseColor = if (remaining > 0) 0xFF5555 else 0x5555FF
                guiGraphics.fill(x, itemY, x + menuWidth, itemY + itemHeight - 1, (pulse shl 24) or pulseColor)

                guiGraphics.pose().pushPose()
                guiGraphics.pose().translate((x + 2).toDouble(), (itemY + 2).toDouble(), 0.0)
                guiGraphics.pose().scale(0.7f, 0.7f, 1f)

                if (remaining > 0) {
                    val timerText = formatTime(remaining)
                    val timerWidth = client.font.width(timerText)
                    guiGraphics.drawString(client.font, timerText, -timerWidth - 4, 0, 0xFFFF5555.toInt())
                    guiGraphics.drawString(client.font, "> $displayName", 0, 0, 0xFFAAAAAA.toInt()) // Cinza em cooldown
                } else {
                    guiGraphics.drawString(client.font, "> $displayName", 0, 0, 0xFFFFFFFF.toInt())
                }
            } else {
                if (remaining > 0) {
                    val timerText = formatTime(remaining)
                    val timerWidth = client.font.width(timerText)
                    guiGraphics.drawString(client.font, timerText, -timerWidth - 4, 0, 0xFFFF5555.toInt())
                }
                val available = isCommandAvailable(cmd)
                val renderColor =
                    if (!available)
                        darkenColor(cmdColor, 0.35f)
                    else if (remaining > 0)
                        darkenColor(cmdColor, 0.55f)
                    else
                        cmdColor
                guiGraphics.drawString(client.font, "  $displayName", 0, 0, renderColor)
            }
            guiGraphics.pose().popPose()
        }

        // Dicas separadas por "parágrafo" (linhas)
        val upKey = CobblebrainClientCommon.keyUp?.translatedKeyMessage?.string ?: "B"
        val downKey = CobblebrainClientCommon.keyDown?.translatedKeyMessage?.string ?: "V"
        val execKey = CobblebrainClientCommon.keyExecute?.translatedKeyMessage?.string ?: "Z"
        val toggleKey = CobblebrainClientCommon.keyToggle?.translatedKeyMessage?.string ?: "N"
        val pingKey = CobblebrainClientCommon.keyPing?.translatedKeyMessage?.string ?: "G"

        val selectUpMsg = net.minecraft.client.resources.language.I18n.get("cobblebrain.hud.select_up")
        val selectDownMsg = net.minecraft.client.resources.language.I18n.get("cobblebrain.hud.select_down")
        val confirmOrderMsg = net.minecraft.client.resources.language.I18n.get("cobblebrain.hud.confirm_order")
        val toggleHudMsg = net.minecraft.client.resources.language.I18n.get("cobblebrain.hud.toggle_hud")
        val markLocationMsg = net.minecraft.client.resources.language.I18n.get("cobblebrain.hud.mark_location")

        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate((x).toDouble(), (y + totalHeight + 6).toDouble(), 0.0)
        guiGraphics.pose().scale(0.5f, 0.5f, 1f) // Voltando para o tamanho anterior

        guiGraphics.drawString(client.font, "$upKey: $selectUpMsg", 0, 5, 0x99FFFFFF.toInt(), false)
        guiGraphics.drawString(client.font, "$downKey: $selectDownMsg", 0, 15, 0x99FFFFFF.toInt(), false)
        guiGraphics.drawString(client.font, "$execKey: $confirmOrderMsg", 0, 25, 0x99FFFFFF.toInt(), false)
        guiGraphics.drawString(client.font, "$toggleKey: $toggleHudMsg", 0, 35, 0x99FFFFFF.toInt(), false)
        guiGraphics.drawString(client.font, "$pingKey: $markLocationMsg", 0, 45, 0x99FFFFFF.toInt(), false)

        guiGraphics.pose().popPose()
    }

    private fun getCommandColor(cmd: String): Int {
        return when (cmd.uppercase()) {
            // Fire
            "COOK" ->
                0xFFFFAA00.toInt()
            // Grass
            "GROW" ->
                0xFF55FF55.toInt()
            // Steel
            "REPAIR" ->
                0xFFE6E6E6.toInt()
            // Ghost
            "SHIFT" ->
                0xFF7B1BC1.toInt()
            // Water
            "FISH" ->
                0xFF3FA9FF.toInt()
            // Dark
            "NIGHTMARE" ->
                0xFF4B2E2E.toInt()
            // Electric
            "LIGHT" ->
                0xFFFFFF55.toInt()
            // Flying
            "SCOUT" ->
                0xFF87CEEB.toInt()
            // Psychic
            "TELEPORT" ->
                0xFFFF55FF.toInt()
            else -> 0xFFAAAAAA.toInt() // Default Gray for neutral actions
        }
    }

    private fun darkenColor(
        color: Int,
        factor: Float = 0.4f,
        alpha: Int = 120
    ): Int {

        val r = ((color shr 16) and 255)
        val g = ((color shr 8) and 255)
        val b = (color and 255)

        return (
                (alpha shl 24) or
                        ((r * factor).toInt() shl 16) or
                        ((g * factor).toInt() shl 8) or
                        ((b * factor).toInt())
                )
    }

    // ===================================================================================
    // 3. ACTION INDICATOR (Placeholder)
    // ===================================================================================

    private fun renderActionIndicator(guiGraphics: GuiGraphics, client: Minecraft) {
        // Reservado para mostrar a ação atual do Pokémon (ex: "Buscando...", "Lutando...") próximo ao ícone do Cobblemon
    }

    // ===================================================================================
    // INPUT HANDLING
    // ===================================================================================
    fun navigateUp() {
        val sortedCommands =
            getSortedCommands()
        if (sortedCommands.isEmpty())
            return

        var nextIndex =
            selectedActionIndex
        do {
            nextIndex--
            if (nextIndex < 0) {
                nextIndex =
                    sortedCommands.lastIndex
            }
        } while (
            !isCommandAvailable(
                sortedCommands[nextIndex]
            ) &&
            nextIndex != selectedActionIndex
        )

        if (
            isCommandAvailable(
                sortedCommands[nextIndex]
            )
        ) {
            selectedActionIndex =
                nextIndex
            playSelectSound(
                Minecraft.getInstance()
            )
        }
    }

    fun navigateDown() {
        val sortedCommands =
            getSortedCommands()

        if (sortedCommands.isEmpty())
            return
        var nextIndex =
            selectedActionIndex

        do {
            nextIndex++

            if (
                nextIndex >=
                sortedCommands.size
            ) {

                nextIndex = 0
            }

        } while (
            !isCommandAvailable(
                sortedCommands[nextIndex]
            ) &&
            nextIndex != selectedActionIndex
        )

        if (
            isCommandAvailable(
                sortedCommands[nextIndex]
            )
        ) {
            selectedActionIndex =
                nextIndex
            playSelectSound(
                Minecraft.getInstance()
            )
        }
    }

    fun executeAction() {
        val sortedCommands = getSortedCommands()
        val cmd = sortedCommands[selectedActionIndex].uppercase()

        // Bloqueia se estiver em cooldown
        if (getCooldownRemaining(cmd) > 0) {
            return
        }

        val action = cmd.lowercase()
        CobblebrainClientCommon.callTeamAction?.invoke("#ALL:$action")
        playConfirmSound(Minecraft.getInstance())

        // Inicia cooldown
        val duration = when(cmd) {
            "BUFF" -> 150000L // 2:30 (150s)
            "REPAIR" -> 300000L // 5:00 (300s)
            "SHIFT" -> 240000L // 4:00 (240s)
            else -> 0L
        }
        if (duration > 0) {
            cooldowns[cmd] = System.currentTimeMillis() + duration
        }
    }

    private fun getCooldownRemaining(cmd: String): Long {
        val endTime = cooldowns[cmd.uppercase()] ?: return 0L
        val remaining = endTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%01d:%02d", minutes, seconds)
    }

    private fun playSelectSound(client: Minecraft) {
        client.level?.playSound(client.player, client.player!!.blockPosition(),
            SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.3f, 1.2f)
    }

    private fun playConfirmSound(client: Minecraft) {
        client.level?.playSound(client.player, client.player!!.blockPosition(),
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.5f, 1.0f)
    }

    fun updateCooldowns(buff: Long, repair: Long, shift: Long, debuff: Long) {
        val now = System.currentTimeMillis()
        if (buff > 0) cooldowns["BUFF"] = now + buff
        if (repair > 0) cooldowns["REPAIR"] = now + repair
        if (shift > 0) cooldowns["SHIFT"] = now + shift
        if (debuff > 0) cooldowns["DEBUFF ENEMY"] = now + debuff
    }
}
