package vito.cobblebrain

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft
import net.minecraft.world.effect.MobEffects
import kotlin.math.sin

object CobbleBrainModClient : ClientModInitializer {
    override fun onInitializeClient() {
        println("Cobblebrain carregado no cliente")
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

                val pulse = ((sin(time / 10.0) + 1) / 2.0 * (maxAlpha - minAlpha) + minAlpha).toInt()
                val color = (pulse shl 24) or 0x00FFAA
                guiGraphics.fill(0, 0, width, height, color)
            }
        }
    }
}
