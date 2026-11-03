package vito.cobblebrain.client

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import vito.cobblebrain.config.ClientConfigHandler
import vito.cobblebrain.social.CobblebrainWorldSave

object LanguageNoticeChecker {
    private var lastMinecraftLanguage: String? = null
    private var lastConfigSelectedLanguage: String? = null
    private var pendingNotice: Boolean = false

    private fun isTraditionalChinese(lang: String): Boolean {
        val l = lang.trim().lowercase()
        return l == "zh_tw" || l == "zh_hk" || l == "traditional chinese" ||
               l == "chinese traditional" || l == "繁體中文" || l == "繁体中文" ||
               l == "zh-tw" || l == "zh-hk"
    }

    fun checkAndShow(client: Minecraft) {
        val currentMcLang = client.options?.languageCode?.lowercase() ?: ""
        val currentConfigLang = try {
            ClientConfigHandler.clientConfig.selectedLanguage.trim()
        } catch (_: Throwable) {
            ""
        }

        val isNowTraditionalChinese = isTraditionalChinese(currentMcLang) || isTraditionalChinese(currentConfigLang)

        // Initial setup for language-switch tracking
        if (lastMinecraftLanguage == null) {
            lastMinecraftLanguage = currentMcLang
            lastConfigSelectedLanguage = currentConfigLang
        } else {
            // Real-time language switch detected in Minecraft options
            if (currentMcLang.isNotEmpty() && currentMcLang != lastMinecraftLanguage) {
                val previous = lastMinecraftLanguage
                lastMinecraftLanguage = currentMcLang
                if (isTraditionalChinese(currentMcLang) && (previous == null || !isTraditionalChinese(previous))) {
                    pendingNotice = true
                }
            }

            // Real-time language switch detected in CobbleBrain config
            if (currentConfigLang.isNotEmpty() && !currentConfigLang.equals(lastConfigSelectedLanguage, ignoreCase = true)) {
                val previous = lastConfigSelectedLanguage
                lastConfigSelectedLanguage = currentConfigLang
                if (isTraditionalChinese(currentConfigLang) && (previous == null || !isTraditionalChinese(previous))) {
                    pendingNotice = true
                }
            }
        }

        val player = client.player ?: return
        val playerUuid = player.uuid.toString()

        // Check first-time join in this world by player UUID (mirrors CobblebrainWorldSave guide system)
        if (isNowTraditionalChinese) {
            val seenInWorld = try {
                CobblebrainWorldSave.hasSeenTraditionalChineseNotice(playerUuid)
            } catch (_: Throwable) {
                false
            }
            val seenInClient = ClientConfigHandler.clientConfig.seenTraditionalChineseNoticeUuids.contains(playerUuid)

            if (!seenInWorld && !seenInClient) {
                pendingNotice = true
            }
        }

        // If notice is triggered, display it and register player UUID so it won't repeat
        if (pendingNotice) {
            pendingNotice = false

            // Mark this player UUID in world save and client config
            try {
                CobblebrainWorldSave.markTraditionalChineseNoticeSeen(playerUuid)
            } catch (_: Throwable) {}

            if (!ClientConfigHandler.clientConfig.seenTraditionalChineseNoticeUuids.contains(playerUuid)) {
                ClientConfigHandler.clientConfig.seenTraditionalChineseNoticeUuids.add(playerUuid)
                ClientConfigHandler.save()
            }

            val prefixStyle = Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00)).withBold(true)
            val engStyle = Style.EMPTY.withColor(TextColor.fromRgb(0x55FFFF))
            val slashStyle = Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))
            val chiStyle = Style.EMPTY.withColor(TextColor.fromRgb(0xFFFF55))

            val message = Component.literal("[CobbleBrain] ").withStyle(prefixStyle)
                .append(Component.literal("Traditional Chinese translation by Suki").withStyle(engStyle))
                .append(Component.literal(" / ").withStyle(slashStyle))
                .append(Component.literal("繁體中文翻譯由 Suki 提供").withStyle(chiStyle))

            player.sendSystemMessage(message)
        }
    }
}
