package vito.cobblebrain.client

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import vito.cobblebrain.client.gui.StoryRuntimeDebugScreen
import vito.cobblebrain.engine.StoryDebugger
import vito.cobblebrain.engine.StoryExecutor

object StoryDebugClientHandler {
    fun handleF8Pressed() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        val hasActive = StoryDebugger.hasActiveSession() || StoryExecutor.activeStories.isNotEmpty()
        if (hasActive) {
            mc.setScreen(StoryRuntimeDebugScreen(mc.screen))
        } else {
            player.displayClientMessage(Component.literal("§6⚠️ No active story session currently running"), true)
            player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.5f)
        }
    }
}
