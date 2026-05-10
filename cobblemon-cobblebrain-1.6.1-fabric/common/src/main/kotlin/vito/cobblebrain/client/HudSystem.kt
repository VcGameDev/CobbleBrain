package vito.cobblebrain.client

import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import kotlin.math.sin

object HudSystem {

    // Lista de comandos disponíveis
    private val commands = listOf("IDLE", "ATTACK", "PROTECT", "BUFF", "DEBUFF", "EAT", "COOK", "GROW", "REPAIR", "SHIFT")
    private var selectedActionIndex = 0
    private var isVisible = true

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
        val x = screenWidth - 150
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
                renderSingleQuest(guiGraphics, client, quest, x, y)
                y += 40
            }

        } catch (e: Exception) {}
    }

    private fun renderSingleQuest(guiGraphics: GuiGraphics, client: Minecraft, quest: com.google.gson.JsonObject, x: Int, y: Int) {
        val type = quest.get("type")?.asString ?: return
        val giverName = quest.get("giverName")?.asString ?: "someone"

        guiGraphics.fill(x, y, x + 144, y + 36, 0xAA000000.toInt()) 
        guiGraphics.fill(x, y, x + 2, y + 36, 0xFF55FF55.toInt())

        val title = when(type) {
            "BATTLE" -> "Battle Mission"
            "ITEM" -> "Item Delivery"
            "ADVICE" -> "Advice Needed"
            "TREASURE" -> "Treasure Hunt"
            else -> "Quest"
        }
        guiGraphics.drawString(client.font, title, x + 8, y + 4, 0xFFFFCC00.toInt())

        var progress = 0f
        var progressText = ""

        when(type) {
            "BATTLE" -> {
                val target = quest.get("targetSpecies")?.asString ?: "Target"
                val walked = quest.get("distanceWalked")?.asFloat ?: 0f
                val required = quest.get("requiredDistance")?.asFloat ?: 1000f
                progress = (walked / required).coerceIn(0f, 1f)
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
                    val angle = Math.toDegrees(Math.atan2(tz - playerPos.z, tx - playerPos.x))
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
        guiGraphics.pose().translate((x + 8).toDouble(), (y + 16).toDouble(), 0.0)
        guiGraphics.pose().scale(0.85f, 0.85f, 1f)
        guiGraphics.drawString(client.font, progressText, 0, 0, 0xFFDDDDDD.toInt(), false)
        guiGraphics.pose().popPose()

        val barWidth = 128
        guiGraphics.fill(x + 8, y + 28, x + 8 + barWidth, y + 32, 0x44FFFFFF.toInt())
        guiGraphics.fill(x + 8, y + 28, x + 8 + (barWidth * progress).toInt(), y + 32, 0xFF55FF55.toInt())

        val percent = (progress * 100).toInt()
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate((x + 115).toDouble(), (y + 4).toDouble(), 0.0)
        guiGraphics.pose().scale(0.8f, 0.8f, 1f)
        guiGraphics.drawString(client.font, "$percent%", 0, 0, 0xAAFFFFFF.toInt(), false)
        guiGraphics.pose().popPose()
    }


    // ===================================================================================
    // 2. POKEMON COMMANDS HUD
    // ===================================================================================
    private fun renderCommandsHud(guiGraphics: GuiGraphics, client: Minecraft) {
        if (!isVisible) return

        val screenWidth = client.window.guiScaledWidth
        val screenHeight = client.window.guiScaledHeight
        
        // Centro-Direito (Compacto: Largura 42, Item 10)
        val menuWidth = 42
        val itemHeight = 10
        val totalHeight = commands.size * itemHeight
        val x = screenWidth - menuWidth - 8
        val y = (screenHeight - totalHeight) / 2

        // Fundo
        guiGraphics.fill(x - 2, y - 2, x + menuWidth + 2, y + totalHeight + 2, 0x99000000.toInt())
        guiGraphics.fill(x - 2, y - 2, x - 1, y + totalHeight + 2, 0xFF5555FF.toInt())

        commands.forEachIndexed { index, cmd ->
            val isSelected = index == selectedActionIndex
            val itemY = y + (index * itemHeight)
            val cmdColor = getCommandColor(cmd)
            
            guiGraphics.pose().pushPose()
            guiGraphics.pose().translate((x + 2).toDouble(), (itemY + 2).toDouble(), 0.0)
            guiGraphics.pose().scale(0.7f, 0.7f, 1f) // Texto 30% menor

            if (isSelected) {
                val time = client.level?.gameTime ?: 0L
                val pulse = (sin(time.toDouble() / 4.0) * 20 + 50).toInt()
                // O fundo de seleção precisa ser desenhado antes do texto, mas fora do push/pop de escala para não distorcer o retângulo
                guiGraphics.pose().popPose()
                guiGraphics.fill(x, itemY, x + menuWidth, itemY + itemHeight - 1, (pulse shl 24) or 0x5555FF)
                guiGraphics.pose().pushPose()
                guiGraphics.pose().translate((x + 2).toDouble(), (itemY + 2).toDouble(), 0.0)
                guiGraphics.pose().scale(0.7f, 0.7f, 1f)
                guiGraphics.drawString(client.font, "> $cmd", 0, 0, 0xFFFFFFFF.toInt())
            } else {
                guiGraphics.drawString(client.font, "  $cmd", 0, 0, cmdColor)
            }
            guiGraphics.pose().popPose()
        }

        // Dicas separadas por "parágrafo" (linhas)
        val upKey = CobblebrainClientCommon.keyUp?.translatedKeyMessage?.string ?: "B"
        val downKey = CobblebrainClientCommon.keyDown?.translatedKeyMessage?.string ?: "V"
        val execKey = CobblebrainClientCommon.keyExecute?.translatedKeyMessage?.string ?: "Z"
        val toggleKey = CobblebrainClientCommon.keyToggle?.translatedKeyMessage?.string ?: "N"

        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate((x).toDouble(), (y + totalHeight + 6).toDouble(), 0.0)
        guiGraphics.pose().scale(0.5f, 0.5f, 1f) // Voltando para o tamanho anterior
        
        guiGraphics.drawString(client.font, "$upKey: Select Up", 0, 0, 0x99FFFFFF.toInt(), false)
        guiGraphics.drawString(client.font, "$downKey: Select Down", 0, 10, 0x99FFFFFF.toInt(), false)
        guiGraphics.drawString(client.font, "$execKey: Confirm Order", 0, 20, 0x99FFFFFF.toInt(), false)
        guiGraphics.drawString(client.font, "$toggleKey: Toggle HUD", 0, 30, 0x99FFFFFF.toInt(), false)
        
        guiGraphics.pose().popPose()
    }

    private fun getCommandColor(cmd: String): Int {
        return when (cmd.uppercase()) {
            "COOK" -> 0xFFFFAA00.toInt() // Orange (Fire)
            "GROW" -> 0xFF55FF55.toInt() // Green (Grass)
            "REPAIR" -> 0xFFE6E6E6.toInt() // Silver (Steel)
            "SHIFT" -> 0xFF7B1BC1.toInt() // Purple (Ghost)
            else -> 0xFFAAAAAA.toInt() // Default Gray for neutral actions
        }
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
        selectedActionIndex = if (selectedActionIndex <= 0) commands.size - 1 else selectedActionIndex - 1
        playSelectSound(Minecraft.getInstance())
    }

    fun navigateDown() {
        selectedActionIndex = (selectedActionIndex + 1) % commands.size
        playSelectSound(Minecraft.getInstance())
    }

    fun executeAction() {
        val action = commands[selectedActionIndex].lowercase()
        // Formato especial: #ALL:action
        CobblebrainClientCommon.callTeamAction?.invoke("#ALL:$action")
        playConfirmSound(Minecraft.getInstance())
    }

    private fun playSelectSound(client: Minecraft) {
        client.level?.playSound(client.player, client.player!!.blockPosition(), 
            SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.3f, 1.2f)
    }

    private fun playConfirmSound(client: Minecraft) {
        client.level?.playSound(client.player, client.player!!.blockPosition(), 
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.5f, 1.0f)
    }
}
