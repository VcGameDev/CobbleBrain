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
import vito.cobblebrain.client.MigrationNoticeChecker
import vito.cobblebrain.config.ClientConfigHandler
import vito.cobblebrain.config.CobblebrainConfigScreen
import vito.cobblebrain.config.SyncedConfig
import kotlin.math.sin

object CobbleBrainModClientNeoForge {
    private var wasVoiceKeyDown = false

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

    private val CMD_MODE = KeyMapping(
        "key.cobblebrain.cmd_mode",
        GLFW.GLFW_KEY_I,
        "category.cobblebrain"
    )

    private val KEY_PING = KeyMapping(
        "key.cobblebrain.ping",
        GLFW.GLFW_KEY_G,
        "category.cobblebrain"
    )

    private val KEY_VOICE = KeyMapping(
        "key.cobblebrain.voice_input",
        GLFW.GLFW_KEY_H,
        "category.cobblebrain"
    )

    private val KEY_DEBUG = KeyMapping(
        "key.cobblebrain.story_debug",
        GLFW.GLFW_KEY_F8,
        "category.cobblebrain"
    )

    fun init() {
        ClientConfigHandler.load()
        SyncedConfig.resetToLocal()
        println("Cobblebrain loaded on the client (NeoForge)")

        CobblebrainClientCommon.isMcmtiInstalled = {
            net.neoforged.fml.ModList.get().isLoaded("mcmti")
        }

        if (CobblebrainClientCommon.isMcmtiInstalled()) {
            vito.cobblebrain.client.mcmti.McmtiNeoForgeHandler.register()
        }

        CobblebrainClientCommon.openConfigScreen = {
            Minecraft.getInstance().setScreen(
                CobblebrainConfigScreen.create(Minecraft.getInstance().screen)
            )
        }

        NeoForge.EVENT_BUS.addListener(::onClientTick)
        NeoForge.EVENT_BUS.register(this)

        CobblebrainClientCommon.keyUp = CMD_UP
        CobblebrainClientCommon.keyDown = CMD_DOWN
        CobblebrainClientCommon.keyExecute = CMD_EXECUTE
        CobblebrainClientCommon.keyToggle = CMD_TOGGLE
        CobblebrainClientCommon.keyMode = CMD_MODE
        CobblebrainClientCommon.keyPing = KEY_PING
        CobblebrainClientCommon.keyVoice = KEY_VOICE
        CobblebrainClientCommon.keyDebug = KEY_DEBUG
    }

    fun onRegisterKeybinds(event: RegisterKeyMappingsEvent) {
        event.register(OPEN_CONFIG)
        event.register(CMD_UP)
        event.register(CMD_DOWN)
        event.register(CMD_EXECUTE)
        event.register(CMD_TOGGLE)
        event.register(CMD_MODE)
        event.register(KEY_PING)
        event.register(KEY_VOICE)
        event.register(KEY_DEBUG)
    }

    fun onClientTick(event: ClientTickEvent.Post) {
        MigrationNoticeChecker.checkAndShow(Minecraft.getInstance())
        vito.cobblebrain.client.KeyInputClientManager.clientTick()
        while (OPEN_CONFIG.consumeClick()) {
            CobblebrainClientCommon.openConfig()
        }
        while (KEY_DEBUG.consumeClick()) {
            vito.cobblebrain.client.StoryDebugClientHandler.handleF8Pressed()
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
        while (CMD_MODE.consumeClick()) {
            HudSystem.toggleTargetMode()
        }
        while (KEY_PING.consumeClick()) {
            vito.cobblebrain.client.PingClient.triggerPingRaycast()
        }
        val isVoiceDown = KEY_VOICE.isDown && Minecraft.getInstance().screen == null
        if (isVoiceDown && !wasVoiceKeyDown) {
            wasVoiceKeyDown = true
            if (!CobblebrainClientCommon.isMcmtiInstalled()) {
                Minecraft.getInstance().setScreen(
                    vito.cobblebrain.client.McmtiNotInstalledNoticeScreen(Minecraft.getInstance().screen)
                )
            } else if (!ClientConfigHandler.clientConfig.enableStt) {
                Minecraft.getInstance().player?.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§c[CobbleBrain] Speech-to-Text (STT) is disabled in the settings."),
                    true
                )
            } else {
                val started = vito.cobblebrain.client.mcmti.McmtiNeoForgeHandler.startRecording()
                if (started) {
                    Minecraft.getInstance().player?.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§a🎙 [CobbleBrain] Recording voice... (release [H] to send)"),
                        true
                    )
                } else {
                    Minecraft.getInstance().player?.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§c❌ [CobbleBrain] Could not start voice recording. Check MCMti keybinding."),
                        true
                    )
                }
            }
        } else if (!isVoiceDown && wasVoiceKeyDown) {
            wasVoiceKeyDown = false
            if (CobblebrainClientCommon.isVoiceRecording) {
                vito.cobblebrain.client.mcmti.McmtiNeoForgeHandler.stopRecording()
                Minecraft.getInstance().player?.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§e⏳ [CobbleBrain] Processing voice..."),
                    true
                )
            }
        }
    }

    @SubscribeEvent
    fun onClientLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
        val config = ClientConfigHandler.clientConfig
        val name = config.preferredName.ifBlank { Minecraft.getInstance().user.name }
        val forceOfflineMode = SyncedConfig.forceOfflineMode && !Minecraft.getInstance().isLocalServer
        CobblebrainClientCommon.sendNicknameToServer?.invoke(name)
        CobblebrainClientCommon.sendOfflineSettingsToServer?.invoke(
            config.offlineMode || forceOfflineMode,
            config.offlineTalkMode
        )
    }

    @SubscribeEvent
    fun onHudRender(event: RenderGuiEvent.Post) {
        val guiGraphics = event.guiGraphics
        val client = Minecraft.getInstance()
        val player = client.player ?: return

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
