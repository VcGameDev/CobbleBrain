package vito.cobblebrain.client

import net.minecraft.client.KeyMapping
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import org.lwjgl.glfw.GLFW

object CobblebrainKeybindsNeoForge {
    val OPEN_CONFIG = KeyMapping(
        "key.cobblebrain.open_config",
        GLFW.GLFW_KEY_Y,
        "key.categories.cobblebrain"
    )

    fun register(event: RegisterKeyMappingsEvent) {
        event.register(OPEN_CONFIG)
    }
}