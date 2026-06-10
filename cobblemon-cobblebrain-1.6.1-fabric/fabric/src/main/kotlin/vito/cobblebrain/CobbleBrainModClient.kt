package vito.cobblebrain

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.world.effect.MobEffects
import org.lwjgl.glfw.GLFW
import vito.cobblebrain.client.CobblebrainClientCommon
import vito.cobblebrain.client.CobblebrainClientHandlerFabric.registerReceivers
import vito.cobblebrain.client.HudSystem
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


        val commandKeyQ = KeyMapping(
            "key.cobblebrain.cmd_up",
            GLFW.GLFW_KEY_B,
            "category.cobblebrain"
        )

        val commandKeyE = KeyMapping(
            "key.cobblebrain.cmd_down",
            GLFW.GLFW_KEY_V,
            "category.cobblebrain"
        )

        val commandKeyR = KeyMapping(
            "key.cobblebrain.cmd_execute",
            GLFW.GLFW_KEY_Z,
            "category.cobblebrain"
        )

        val commandKeyToggle = KeyMapping(
            "key.cobblebrain.cmd_toggle",
            GLFW.GLFW_KEY_N,
            "category.cobblebrain"
        )

        val keyPing = KeyMapping(
            "key.cobblebrain.ping",
            GLFW.GLFW_KEY_G,
            "category.cobblebrain"
        )

        // keybinds
        KeyBindingHelper.registerKeyBinding(openConfig)
        KeyBindingHelper.registerKeyBinding(commandKeyQ)
        KeyBindingHelper.registerKeyBinding(commandKeyE)
        KeyBindingHelper.registerKeyBinding(commandKeyR)
        KeyBindingHelper.registerKeyBinding(commandKeyToggle)
        KeyBindingHelper.registerKeyBinding(keyPing)

        // Passa as referências para a HUD dinâmica
        CobblebrainClientCommon.keyUp = commandKeyQ
        CobblebrainClientCommon.keyDown = commandKeyE
        CobblebrainClientCommon.keyExecute = commandKeyR
        CobblebrainClientCommon.keyToggle = commandKeyToggle

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            while (openConfig.consumeClick()) {
                CobblebrainClientCommon.openConfig()
            }
            while (commandKeyQ.consumeClick()) {
                HudSystem.navigateUp()
            }
            while (commandKeyE.consumeClick()) {
                HudSystem.navigateDown()
            }
            while (commandKeyR.consumeClick()) {
                HudSystem.executeAction()
            }
            while (commandKeyToggle.consumeClick()) {
                HudSystem.toggleVisibility()
            }
            while (keyPing.consumeClick()) {
                vito.cobblebrain.client.PingClient.triggerPingRaycast()
            }
        })

        HudRenderCallback.EVENT.register { guiGraphics, tickDelta ->
            val client = Minecraft.getInstance()
            val player = client.player ?: return@register

            // Converte DeltaTracker para Float se necessário
            val delta = tickDelta.gameTimeDeltaTicks

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

            // HUD SYSTEM
            HudSystem.render(guiGraphics, delta)
        }
        }
    }
