package vito.cobblebrain

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.world.effect.MobEffects
import org.lwjgl.glfw.GLFW
import vito.cobblebrain.client.CobblebrainClientCommon
import vito.cobblebrain.client.CobblebrainClientHandlerFabric.registerReceivers
import vito.cobblebrain.config.ClientConfigHandler
import vito.cobblebrain.config.CobblebrainConfigScreen
import vito.cobblebrain.config.SyncedConfig
import kotlin.math.sin

object CobbleBrainModClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientConfigHandler.load()
        SyncedConfig.resetToLocal()
        registerReceivers()
        println("Cobblebrain loaded on the client (Fabric)")

        // conecta com o common
        CobblebrainClientCommon.openConfigScreen = {
            Minecraft.getInstance().setScreen(
                CobblebrainConfigScreen.create(Minecraft.getInstance().screen)
            )
        }

        val openConfig = KeyMapping(
            "key.cobblebrain.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "category.cobblebrain"
        )

        // keybind
        KeyBindingHelper.registerKeyBinding(openConfig)

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (openConfig.consumeClick()) {
                CobblebrainClientCommon.openConfig()
            }
        }

        HudRenderCallback.EVENT.register { guiGraphics, tickDelta ->
            val client = Minecraft.getInstance()
            val player = client.player ?: return@register

            val invis = player.hasEffect(MobEffects.INVISIBILITY)
            val jump = player.hasEffect(MobEffects.JUMP)
            val slowFall = player.hasEffect(MobEffects.SLOW_FALLING)
            val weakness = player.hasEffect(MobEffects.WEAKNESS)
            val speed = player.hasEffect(MobEffects.MOVEMENT_SPEED)

            if (invis && jump && slowFall && weakness && speed) {
                val width = client.window.guiScaledWidth
                val height = client.window.guiScaledHeight
                val time = client.level?.gameTime ?: 0
                val minAlpha = 50
                val maxAlpha = 180

                // pulsar mais lento (20.0 em vez de 10.0)
                val pulse = ((sin(time / 20.0) + 1) / 2.0 * (maxAlpha - minAlpha) + minAlpha).toInt()
                // roxo escuro discreto
                val color = (pulse shl 24) or 0x3A0066
                guiGraphics.fill(0, 0, width, height, color)
            }

            // QUEST HUD
            val questsJson = vito.cobblebrain.client.CobblebrainClientCommon.currentQuestsJson
            if (questsJson == "[]") return@register

            try {
                val questsArray = com.google.gson.JsonParser.parseString(questsJson).asJsonArray
                if (questsArray.size() == 0) return@register

                val screenWidth = client.window.guiScaledWidth
                val x = screenWidth - 150
                var y = 10

                for (i in 0 until minOf(questsArray.size(), 2)) {
                    val quest = questsArray[i].asJsonObject
                    val type = quest.get("type")?.asString ?: continue
                    val status = quest.get("status")?.asString ?: "IN_PROGRESS"
                    if (status != "IN_PROGRESS") continue

                    val giverName = quest.get("giverName")?.asString ?: "someone"

                    // Background Box (Glass effect) - Reduzido em 20%
                    guiGraphics.fill(x, y, x + 144, y + 36, 0xAA000000.toInt()) 
                    guiGraphics.fill(x, y, x + 2, y + 36, 0xFF55FF55.toInt()) // Border

                    val title = when(type) {
                        "BATTLE" -> "Battle Mission"
                        "ITEM" -> "Item Delivery"
                        "ADVICE" -> "Advice Needed"
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
                            progressText = "Defeat $target in a Pokémon battle"
                        }
                        "ITEM" -> {
                            val target = quest.get("target")?.asString ?: "Item"
                            val collected = quest.get("collected")?.asInt ?: 0
                            val amount = quest.get("amount")?.asInt ?: 1
                            progress = (collected.toFloat() / amount.toFloat()).coerceIn(0f, 1f)
                            progressText = "Drop $amount $target to $giverName ($collected/$amount)"
                        }
                        "ADVICE" -> {
                            val points = quest.get("points")?.asInt ?: 0
                            progress = ((points + 5) / 10f).coerceIn(0f, 1f)
                            progressText = "Help $giverName with their $issue: talk points: ($points/5)"
                        }
                    }

                    // Texto menor para caber na HUD reduzida
                    guiGraphics.pose().pushPose()
                    guiGraphics.pose().translate((x + 8).toDouble(), (y + 16).toDouble(), 0.0)
                    guiGraphics.pose().scale(0.85f, 0.85f, 1f)
                    guiGraphics.drawString(client.font, progressText, 0, 0, 0xFFDDDDDD.toInt(), false)
                    guiGraphics.pose().popPose()

                    // Bar - Reduzida
                    val barWidth = 128
                    guiGraphics.fill(x + 8, y + 28, x + 8 + barWidth, y + 32, 0x44FFFFFF.toInt()) 
                    guiGraphics.fill(x + 8, y + 28, x + 8 + (barWidth * progress).toInt(), y + 32, 0xFF55FF55.toInt())

                    val percent = (progress * 100).toInt()
                    guiGraphics.pose().pushPose()
                    guiGraphics.pose().translate((x + 115).toDouble(), (y + 4).toDouble(), 0.0)
                    guiGraphics.pose().scale(0.8f, 0.8f, 1f)
                    guiGraphics.drawString(client.font, "$percent%", 0, 0, 0xAAFFFFFF.toInt(), false)
                    guiGraphics.pose().popPose()

                    y += 40
                }
            } catch (e: Exception) {
                // Skip errors
            }
        }
    }
}
