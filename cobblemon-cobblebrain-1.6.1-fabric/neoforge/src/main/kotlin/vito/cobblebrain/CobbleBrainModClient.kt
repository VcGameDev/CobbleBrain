package vito.cobblebrain

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.minecraft.client.Minecraft
import net.minecraft.world.effect.MobEffects
import vito.cobblebrain.config.ClientConfigHandler
import kotlin.math.sin

object CobbleBrainModClientNeoForge {
    fun init() {
        ClientConfigHandler.load()
        println("Cobblebrain carregado no cliente (NeoForge)")
    }

    @SubscribeEvent
    fun onHudRender(event: RenderGuiEvent.Post) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return

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

            val pulse = ((sin(time / 20.0) + 1) / 2.0 * (maxAlpha - minAlpha) + minAlpha).toInt()
            val color = (pulse shl 24) or 0x3A0066

            val guiGraphics = event.guiGraphics
            guiGraphics.fill(0, 0, width, height, color)
        }
    }
}