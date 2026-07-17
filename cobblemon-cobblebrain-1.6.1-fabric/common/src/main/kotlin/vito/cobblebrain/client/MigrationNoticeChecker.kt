package vito.cobblebrain.client

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import vito.cobblebrain.config.ClientConfigHandler

object MigrationNoticeChecker {
    private var sessionChecked = false

    fun checkAndShow(client: Minecraft) {
        if (sessionChecked) return
        val player = client.player ?: return

        sessionChecked = true

        val config = ClientConfigHandler.clientConfig
        if (config.seenMigrationNotice140) return

        // Color styles: Blue, Yellow, and Aqua (Light Blue)
        val yellowStyle = Style.EMPTY.withColor(TextColor.fromRgb(0xFFFF55))
        val aquaStyle = Style.EMPTY.withColor(TextColor.fromRgb(0x55FFFF))

        val message = Component.literal("1.4 Update: Pokémon now develop unique personalities automatically after their first conversation. Disable ")
                    .withStyle(yellowStyle)
            .append(
                Component.literal("'Enable Trait Creation'")
                    .withStyle(aquaStyle)
            )
            .append(
                Component.literal(" in the settings if you prefer to create them manually in the ")
                    .withStyle(yellowStyle)
            )
            .append(
                Component.literal("Personality Editor")
                    .withStyle(aquaStyle)
            )
            .append(
                Component.literal(".")
                    .withStyle(yellowStyle)
            )

        player.sendSystemMessage(message)

        client.level?.playSound(
            player,
            player.blockPosition(),
            SoundEvents.UI_TOAST_IN,
            SoundSource.MASTER,
            0.6f,
            1.1f
        )

        config.seenMigrationNotice140 = true
        ClientConfigHandler.save()
    }
}
