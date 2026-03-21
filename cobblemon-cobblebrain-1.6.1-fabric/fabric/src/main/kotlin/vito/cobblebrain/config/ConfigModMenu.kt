package vito.cobblebrain.config

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.minecraft.client.gui.screens.Screen

class ConfigModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<Screen> {
        return ConfigScreenFactory { parent ->
            CobblebrainConfigScreen.create(parent)
        }
    }
}