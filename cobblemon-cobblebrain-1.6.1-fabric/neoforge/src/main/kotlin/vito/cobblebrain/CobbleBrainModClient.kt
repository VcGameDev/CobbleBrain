package vito.cobblebrain

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.KeyMapping
import net.minecraft.world.effect.MobEffects
import org.lwjgl.glfw.GLFW
import vito.cobblebrain.client.CobblebrainClientCommon
import vito.cobblebrain.client.HudSystem
import vito.cobblebrain.config.ClientConfigHandler
import vito.cobblebrain.config.CobblebrainConfigScreen
import vito.cobblebrain.config.SyncedConfig
import kotlin.math.sin

object CobbleBrainModClientNeoForge {
    // continua aqui, mas só carrega no CLIENT agora
    private val OPEN_CONFIG = KeyMapping(
        "key.cobblebrain.open_config",
        GLFW.GLFW_KEY_Y,
        "key.categories.cobblebrain"
    )


    private val CMD_UP = KeyMapping(
        "key.cobblebrain.cmd_up",
        GLFW.GLFW_KEY_B,
        "category.cobblebrain"
    )

    private val CMD_DOWN = KeyMapping(
        "key.cobblebrain.cmd_down",
        GLFW.GLFW_KEY_V,
        "category.cobblebrain"
    )

    private val CMD_EXECUTE = KeyMapping(
        "key.cobblebrain.cmd_execute",
        GLFW.GLFW_KEY_Z,
        "category.cobblebrain"
    )

    private val CMD_TOGGLE = KeyMapping(
        "key.cobblebrain.cmd_toggle",
        GLFW.GLFW_KEY_N,
        "category.cobblebrain"
    )

    private val KEY_PING = KeyMapping(
        "key.cobblebrain.ping",
        GLFW.GLFW_KEY_G,
        "category.cobblebrain"
    )

    fun init() {
        ClientConfigHandler.load()
        SyncedConfig.resetToLocal()
        println("Cobblebrain loaded on the client (NeoForge)")

        CobblebrainClientCommon.openConfigScreen = {
            Minecraft.getInstance().setScreen(
                CobblebrainConfigScreen.create(Minecraft.getInstance().screen)
            )
        }

        NeoForge.EVENT_BUS.addListener(::onClientTick)
        NeoForge.EVENT_BUS.register(this)

        // Referências para a HUD dinâmica
        CobblebrainClientCommon.keyUp = CMD_UP
        CobblebrainClientCommon.keyDown = CMD_DOWN
        CobblebrainClientCommon.keyExecute = CMD_EXECUTE
        CobblebrainClientCommon.keyToggle = CMD_TOGGLE
    }

    // registra keybind
    fun onRegisterKeybinds(event: RegisterKeyMappingsEvent) {
        event.register(OPEN_CONFIG)
        event.register(CMD_UP)
        event.register(CMD_DOWN)
        event.register(CMD_EXECUTE)
        event.register(CMD_TOGGLE)
        event.register(KEY_PING)
    }

    // tick
    fun onClientTick(event: ClientTickEvent.Post) {
        while (OPEN_CONFIG.consumeClick()) {
            CobblebrainClientCommon.openConfig()
        }
        while (CMD_UP.consumeClick()) {
            HudSystem.navigateUp()
        }
        while (CMD_DOWN.consumeClick()) {
            HudSystem.navigateDown()
        }
        while (CMD_EXECUTE.consumeClick()) {
            HudSystem.executeAction()
        }
        while (CMD_TOGGLE.consumeClick()) {
            HudSystem.toggleVisibility()
        }
        while (KEY_PING.consumeClick()) {
            vito.cobblebrain.client.PingClient.triggerPingRaycast()
        }
    }

    @SubscribeEvent
    fun onClientLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
        val config = ClientConfigHandler.clientConfig
        val name = config.preferredName.ifBlank { Minecraft.getInstance().user.name }
        CobblebrainClientCommon.sendNicknameToServer?.invoke(name)
        CobblebrainClientCommon.sendOfflineSettingsToServer?.invoke(config.offlineMode, config.offlineTalkMode)
    }

    // HUD
    @SubscribeEvent
    fun onHudRender(event: RenderGuiEvent.Post) {
        val guiGraphics = event.guiGraphics
        val client = Minecraft.getInstance()
        val player = client.player ?: return

        // Converte DeltaTracker para Float se necessário
        val delta = event.partialTick.gameTimeDeltaTicks

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

            guiGraphics.fill(0, 0, width, height, color)
        }

        // HUD SYSTEM
        HudSystem.render(guiGraphics, delta)
    }
}