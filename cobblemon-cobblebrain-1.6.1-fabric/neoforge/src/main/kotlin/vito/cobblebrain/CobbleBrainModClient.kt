package vito.cobblebrain

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.common.NeoForge
import net.minecraft.client.Minecraft
import net.minecraft.client.KeyMapping
import net.minecraft.world.effect.MobEffects
import org.lwjgl.glfw.GLFW
import vito.cobblebrain.client.CobblebrainClientCommon
import vito.cobblebrain.config.ClientConfigHandler
import vito.cobblebrain.config.CobblebrainConfigScreen
import kotlin.math.sin

object CobbleBrainModClientNeoForge {

    // KEYBIND
    private val OPEN_CONFIG = KeyMapping(
        "key.cobblebrain.open_config",
        GLFW.GLFW_KEY_Y,
        "key.categories.cobblebrain"
    )

    fun init() {
        ClientConfigHandler.load()
        println("Cobblebrain carregado no cliente (NeoForge)")

        // conecta config screen
        CobblebrainClientCommon.openConfigScreen = {
            Minecraft.getInstance().setScreen(
                CobblebrainConfigScreen.create(Minecraft.getInstance().screen)
            )
        }

        // registra eventos
        NeoForge.EVENT_BUS.addListener(::onClientTick)
        NeoForge.EVENT_BUS.register(this)
    }

    // registra keybind
    fun onRegisterKeybinds(event: RegisterKeyMappingsEvent) {
        event.register(OPEN_CONFIG)
    }

    // detecta tecla
    fun onClientTick(event: ClientTickEvent.Post) {
        while (OPEN_CONFIG.consumeClick()) {
            CobblebrainClientCommon.openConfig()
        }
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