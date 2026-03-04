package vito.cobblebrain

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft
import net.minecraft.world.effect.MobEffects
import vito.cobblebrain.client.CobblebrainClientHandler
import vito.cobblebrain.config.CobblebrainClientConfig
import java.nio.file.Files
import kotlin.math.sin

object CobbleBrainModClient : ClientModInitializer {
    override fun onInitializeClient() {
        val clientConfigFile = Minecraft.getInstance().gameDirectory.toPath().resolve("cobblebrain_client_config.json")

        val clientConfig = if (Files.exists(clientConfigFile)) {
            Gson().fromJson(Files.readString(clientConfigFile), CobblebrainClientConfig::class.java)
        } else {
            val cfg = CobblebrainClientConfig()
            Files.writeString(clientConfigFile, GsonBuilder().setPrettyPrinting().create().toJson(cfg))
            cfg
        }
        CobblebrainClientHandler.registerReceivers()
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

                // pulsar mais lento (20.0 em vez de 10.0)
                val pulse = ((sin(time / 20.0) + 1) / 2.0 * (maxAlpha - minAlpha) + minAlpha).toInt()
                // roxo escuro discreto
                val color = (pulse shl 24) or 0x3A0066
                guiGraphics.fill(0, 0, width, height, color)
            }
        }
    }
}
