package vito.cobblebrain.engine

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

object StoryTextFormatter {

    /**
     * Interpolates story variables and player context tokens in the input string.
     * Supported tokens: {nome_jogador}, {player_name}, {player}, {jogador}, {uuid}, {level}, {nivel}, and all story variables {var_name}.
     */
    fun interpolate(text: String, context: StoryContext?): String {
        if (text.isEmpty()) return text
        var result = text

        val player = context?.player
        val playerName = player?.scoreboardName ?: player?.name?.string ?: "Player"
        val playerUuid = player?.uuid?.toString() ?: ""
        val playerLvl = player?.experienceLevel?.toString() ?: "0"

        result = result.replace("{nome_jogador}", playerName, ignoreCase = true)
        result = result.replace("{player_name}", playerName, ignoreCase = true)
        result = result.replace("{player}", playerName, ignoreCase = true)
        result = result.replace("{jogador}", playerName, ignoreCase = true)
        result = result.replace("{uuid}", playerUuid, ignoreCase = true)
        result = result.replace("{level}", playerLvl, ignoreCase = true)
        result = result.replace("{nivel}", playerLvl, ignoreCase = true)

        context?.variables?.forEach { (k, v) ->
            result = result.replace("{$k}", v.toString())
        }

        return result
    }

    /**
     * Parses Minecraft legacy color/style codes (&0-&f, &l, &o, &n, &m, &k, &r, §0-§f...)
     * and Hexadecimal color codes (&#RRGGBB, <#RRGGBB>, #RRGGBB) into a rich MutableComponent.
     */
    fun parseFormatted(input: String): MutableComponent {
        if (input.isEmpty()) return Component.empty()

        val root = Component.empty()

        // Pattern matching Hex codes (&#RRGGBB, <#RRGGBB>, #RRGGBB) or Legacy formatting codes (&c, §c)
        val pattern = Regex("(?:&#|<#|#)([0-9a-fA-F]{6})>?|[&§]([0-9a-fk-orA-FK-OR])")

        var lastIdx = 0
        var currentColor: TextColor? = null
        var isBold = false
        var isItalic = false
        var isUnderlined = false
        var isStrikethrough = false
        var isObfuscated = false

        fun buildStyle(): Style {
            var style = Style.EMPTY
            if (currentColor != null) style = style.withColor(currentColor)
            if (isBold) style = style.withBold(true)
            if (isItalic) style = style.withItalic(true)
            if (isUnderlined) style = style.withUnderlined(true)
            if (isStrikethrough) style = style.withStrikethrough(true)
            if (isObfuscated) style = style.withObfuscated(true)
            return style
        }

        fun appendText(segment: String) {
            if (segment.isNotEmpty()) {
                root.append(Component.literal(segment).withStyle(buildStyle()))
            }
        }

        pattern.findAll(input).forEach { match ->
            val matchStart = match.range.first
            val matchEnd = match.range.last + 1

            if (matchStart > lastIdx) {
                appendText(input.substring(lastIdx, matchStart))
            }

            val hexGroup = match.groups[1]?.value
            val legacyGroup = match.groups[2]?.value

            if (hexGroup != null) {
                val rgb = hexGroup.toIntOrNull(16)
                if (rgb != null) {
                    currentColor = TextColor.fromRgb(rgb)
                    isBold = false
                    isItalic = false
                    isUnderlined = false
                    isStrikethrough = false
                    isObfuscated = false
                }
            } else if (legacyGroup != null) {
                val code = legacyGroup.first().lowercaseChar()
                when (code) {
                    '0' -> { currentColor = TextColor.fromRgb(0x000000); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    '1' -> { currentColor = TextColor.fromRgb(0x0000AA); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    '2' -> { currentColor = TextColor.fromRgb(0x00AA00); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    '3' -> { currentColor = TextColor.fromRgb(0x00AAAA); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    '4' -> { currentColor = TextColor.fromRgb(0xAA0000); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    '5' -> { currentColor = TextColor.fromRgb(0xAA00AA); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    '6' -> { currentColor = TextColor.fromRgb(0xFFAA00); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    '7' -> { currentColor = TextColor.fromRgb(0xAAAAAA); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    '8' -> { currentColor = TextColor.fromRgb(0x555555); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    '9' -> { currentColor = TextColor.fromRgb(0x5555FF); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    'a' -> { currentColor = TextColor.fromRgb(0x55FF55); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    'b' -> { currentColor = TextColor.fromRgb(0x55FFFF); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    'c' -> { currentColor = TextColor.fromRgb(0xFF5555); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    'd' -> { currentColor = TextColor.fromRgb(0xFF55FF); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    'e' -> { currentColor = TextColor.fromRgb(0xFFFF55); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    'f' -> { currentColor = TextColor.fromRgb(0xFFFFFF); isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                    'l' -> isBold = true
                    'o' -> isItalic = true
                    'n' -> isUnderlined = true
                    'm' -> isStrikethrough = true
                    'k' -> isObfuscated = true
                    'r' -> { currentColor = null; isBold = false; isItalic = false; isUnderlined = false; isStrikethrough = false; isObfuscated = false }
                }
            }

            lastIdx = matchEnd
        }

        if (lastIdx < input.length) {
            appendText(input.substring(lastIdx))
        }

        return root
    }

    /**
     * Fully formats an input template with variables and styling into a Component.
     */
    fun format(text: String, context: StoryContext?): MutableComponent {
        val interpolated = interpolate(text, context)
        return parseFormatted(interpolated)
    }
}
