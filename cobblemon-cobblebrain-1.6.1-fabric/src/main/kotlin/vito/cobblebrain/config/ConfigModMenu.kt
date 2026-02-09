package vito.cobblebrain.config

import com.terraformersmc.modmenu.api.ModMenuApi
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import net.minecraft.client.gui.screens.Screen
import me.shedaniel.clothconfig2.api.ConfigBuilder
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.client.Minecraft
import vito.cobblebrain.config.ConfigHandler.config
import java.util.Optional

// ---------- Subtítulo centralizado ----------
fun makeSubtitleEntry(text: String, color: Int = 0xFFFF00): AbstractConfigListEntry<Unit> {
    return object : AbstractConfigListEntry<Unit>(
        Component.literal(text).withStyle(
            Style.EMPTY.withColor(TextColor.fromRgb(color)).withBold(true)
        ),
        false
    ) {
        override fun getValue(): Unit? = null
        override fun getDefaultValue(): Optional<Unit> = Optional.empty()
        override fun children(): MutableList<GuiEventListener> = mutableListOf()
        override fun narratables(): MutableList<NarratableEntry> = mutableListOf()

        override fun render(
            guiGraphics: GuiGraphics,
            index: Int,
            y: Int,
            x: Int,
            listWidth: Int,
            itemHeight: Int,
            mouseX: Int,
            mouseY: Int,
            isSelected: Boolean,
            delta: Float
        ) {
            val font = Minecraft.getInstance().font
            val textWidth = font.width(fieldName)
            val centerX = x + (listWidth / 2) - (textWidth / 2)
            guiGraphics.drawString(font, fieldName, centerX, y + (itemHeight / 2 - font.lineHeight / 2), color, true)
        }
    }
}

// ---------- Classe principal ----------
class ConfigModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<Screen> {
        return ConfigScreenFactory { parent: Screen ->
            val builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Cobblebrain Config"))

            val entryBuilder = builder.entryBuilder()
            val category = builder.getOrCreateCategory(Component.literal("Config"))

            // ========================= AI CONFIGURATION =========================

            val apiKeyEntry = entryBuilder.startStrList(
                Component.literal("API Key").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.apiKey
            ).setDefaultValue(listOf("YOUR_API_KEY"))
                .setSaveConsumer { value -> config.apiKey = value }
                .setTooltip(Component.literal("The API key used for authentication with the AI system. It can be a Bearer token or a Google API key depending on the provider."))
                .build()

            val keyRotationEntry = entryBuilder.startBooleanToggle(
                Component.literal("Key Rotation").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.keyRotation
            ).setDefaultValue(false)
                .setSaveConsumer { value -> config.keyRotation = value }
                .setTooltip(Component.literal("Enables API key rotation when errors occur. Useful for handling invalid or expired keys."))
                .build()

            val keyRotationTriggerEntry = entryBuilder.startIntList(
                Component.literal("Key Rotation Trigger").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.keyRotationTrigger
            ).setDefaultValue(listOf(401, 429))
                .setSaveConsumer { value -> config.keyRotationTrigger = value }
                .setTooltip(Component.literal("List of HTTP status codes that trigger key rotation. Defines error conditions for switching keys."))
                .build()

            val apiBaseUrlEntry = entryBuilder.startStrField(
                Component.literal("API Base URL").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.apiBaseUrl
            ).setDefaultValue("http://127.0.0.1:4315")
                .setSaveConsumer { value -> config.apiBaseUrl = value }
                .setTooltip(Component.literal("The base URL of the API endpoint. \n Examples include OpenAI, OpenRouter, Google AI Studio, or a local LM Studio server."))
                .build()

            val localApiProviderEntry = entryBuilder.startStrField(
                Component.literal("Local API Provider").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.localApiProvider
            ).setDefaultValue("player2")
                .setSaveConsumer { value -> config.localApiProvider = value }
                .setTooltip(Component.literal("If apiBaseUrl is a local address (127.0.0.1), \n the system uses the provider name in localApiProvider to adapt messages for the correct provider. \n Officially supported providers: player2, lmstudio"))
                .build()

            val aiModelEntry = entryBuilder.startStrList(
                Component.literal("AI Model").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.aiModel
            ).setDefaultValue(listOf("gemma-3-12b-it", "gemma-3-4b-it"))
                .setSaveConsumer { value -> config.aiModel = value }
                .setTooltip(Component.literal("The names of the AI models to use. \nExamples are gemini-2.5-flash, gemma-3-12b-it"))
                .build()

            val modelRotationEntry = entryBuilder.startBooleanToggle(
                Component.literal("Model Rotation").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.modelRotation
            ).setDefaultValue(false)
                .setSaveConsumer { value -> config.modelRotation = value }
                .setTooltip(Component.literal("Enables model rotation when errors occur. \nUseful for fallback to alternative models."))
                .build()

            val modelRotationTriggerEntry = entryBuilder.startIntList(
                Component.literal("Model Rotation Trigger").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.modelRotationTrigger
            ).setDefaultValue(listOf(404, 429))
                .setSaveConsumer { value -> config.modelRotationTrigger = value }
                .setTooltip(Component.literal("List of HTTP status codes that trigger model rotation. \n Defines error conditions for switching models."))
                .build()

            val temperatureEntry = entryBuilder.startFloatField(
                Component.literal("Temperature").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.temperature.toFloat()
            ).setDefaultValue(0.7f)
                .setSaveConsumer { value -> config.temperature = value.toDouble() }
                .setTooltip(Component.literal("Controls the randomness of responses. \n Lower values give precise answers, higher values make them more creative."))
                .build()

            val aiProviderEntry = entryBuilder.startStrField(
                Component.literal("OpenRouter hint").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.aiProvider
            ).setDefaultValue("")
                .setSaveConsumer { value -> config.aiProvider = value }
                .setTooltip(Component.literal("A provider hint used for routing in OpenRouter. \n This is ignored when using other provider."))
                .build()

            val reasoningEffortEntry = entryBuilder.startStrField(
                Component.literal("Reasoning Effort").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.reasoningEffort
            ).setDefaultValue("none")
                .setSaveConsumer { value -> config.reasoningEffort = value }
                .setTooltip(Component.literal("Defines the reasoning effort level for supported models. Options include high, medium, low, auto, or none."))
                .build()

            val requestTimeoutEntry = entryBuilder.startLongField(
                Component.literal("Request Timeout (s)").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.requestTimeoutSeconds
            ).setDefaultValue(60L)
                .setSaveConsumer { value -> config.requestTimeoutSeconds = value }
                .setTooltip(Component.literal("Defines the request timeout in seconds. Local models may require longer values."))
                .build()

            val debugLoggingEntry = entryBuilder.startBooleanToggle(
                Component.literal("Debug Logging").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.debugLogging
            ).setDefaultValue(true)
                .setSaveConsumer { value -> config.debugLogging = value }
                .setTooltip(Component.literal("Enables debug logging for troubleshooting. Logs are stored in the cobblebrain-ai/logs directory."))
                .build()

            val selectedLanguageEntry = entryBuilder.startStrField(
                Component.literal("Selected Language").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.selectedLanguage
            ).setDefaultValue("Portugues Brasil")
                .setSaveConsumer { value -> config.selectedLanguage = value }
                .setTooltip(Component.literal("The language the AI uses for responses. Determines dialogue output language."))
                .build()

            val dialogueInChatEntry = entryBuilder.startBooleanToggle(
                Component.literal("Dialogue In Chat").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.dialogueInChat
            ).setDefaultValue(true)
                .setSaveConsumer { value -> config.dialogueInChat = value }
                .setTooltip(Component.literal("Shows generated dialogue directly in the chat. This makes Pokémon conversations visible to players."))
                .build()

            val chatbubblesEntry = entryBuilder.startBooleanToggle(
                Component.literal("Chat Bubbles").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.chatbubbles
            ).setDefaultValue(true)
                .setSaveConsumer { value -> config.chatbubbles = value }
                .setTooltip(Component.literal("Enables chat bubbles above characters. Dialogue will appear visually instead of only in text chat."))
                .build()

            val pokemonTalkEntry = entryBuilder.startBooleanToggle(
                Component.literal("Pokémon Talk").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.pokemonTalk
            ).setDefaultValue(true)
                .setSaveConsumer { value -> config.pokemonTalk = value }
                .setTooltip(Component.literal("Toggles whether Pokémon can talk or listen. This acts as a simple on/off switch for dialogue."))
                .build()

            val allowPokemonPVPEntry = entryBuilder.startBooleanToggle(
                Component.literal("Allow Pokémon PVP").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.allowPokemonPVP
            ).setDefaultValue(false)
                .setSaveConsumer { value -> config.allowPokemonPVP = value }
                .setTooltip(Component.literal("Allows Pokémon to attack other players’ Pokémon. Disabling prevents player-versus-player battles."))
                .build()

            val allowPokemonPVEEntry = entryBuilder.startBooleanToggle(
                Component.literal("Allow Pokémon PVE").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.allowPokemonPVE
            ).setDefaultValue(true)
                .setSaveConsumer { value -> config.allowPokemonPVE = value }
                .setTooltip(Component.literal("Allows Pokémon to attack mobs in the world. Exceptions include tamed mobs and non-aggressive tagged mobs."))
                .build()

            // ========================= GAME AND INTERACTIONS =========================

            val lowTokenModeEntry = entryBuilder.startBooleanToggle(
                Component.literal("Low Token Mode").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.lowTokenMode
            ).setDefaultValue(false)
                .setSaveConsumer { value -> config.lowTokenMode = value }
                .setTooltip(Component.literal("Reduces world information sent to the AI. This helps conserve tokens and lower usage costs."))
                .build()

            val dialogueOnDamageEntry = entryBuilder.startBooleanToggle(
                Component.literal("Dialogue On Damage").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.dialogueOnDamage
            ).setDefaultValue(false)
                .setSaveConsumer { value -> config.dialogueOnDamage = value }
                .setTooltip(Component.literal("Makes Pokémon speak when someone is hurt. Dialogue is triggered by damage events."))
                .build()

            val dialogueOnBattleEntry = entryBuilder.startBooleanToggle(
                Component.literal("Dialogue On Battle").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.dialogueOnBattle
            ).setDefaultValue(true)
                .setSaveConsumer { value -> config.dialogueOnBattle = value }
                .setTooltip(Component.literal("Makes Pokémon speak during battle events. Dialogue reflects combat situations."))
                .build()

            val spontaneousDialogueChanceEntry = entryBuilder.startFloatField(
                Component.literal("Spontaneous Dialogue Chance").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.spontaneousDialogueChance.toFloat()
            ).setDefaultValue(0.1f)
                .setSaveConsumer { value -> config.spontaneousDialogueChance = value.toDouble() }
                .setTooltip(Component.literal("Sets the chance of spontaneous dialogue. Pokémon may speak randomly during idle moments."))
                .build()

            val listenToChatEntry = entryBuilder.startBooleanToggle(
                Component.literal("Listen To Chat").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.listenToChat
            ).setDefaultValue(true)
                .setSaveConsumer { value -> config.listenToChat = value }
                .setTooltip(Component.literal("Enables listening to regular player chat. If disabled, the AI ignores non-command messages."))
                .build()

            val onlyNearbyChatEntry = entryBuilder.startBooleanToggle(
                Component.literal("Only Nearby Chat").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.onlyNearbyChat
            ).setDefaultValue(false)
                .setSaveConsumer { value -> config.onlyNearbyChat = value }
                .setTooltip(Component.literal("Restricts listening to nearby players only. Works only if listenToChat is enabled."))
                .build()

            val maxShortMemoryEntry = entryBuilder.startIntField(
                Component.literal("Max Short Memory").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.maxShortMemory
            ).setDefaultValue(5)
                .setSaveConsumer { value -> config.maxShortMemory = value }
                .setTooltip(Component.literal("Maximum short-term memory size per Pokémon. Controls how much recent context is stored."))
                .build()

            val maxLongMemoryEntry = entryBuilder.startIntField(
                Component.literal("Max Long Memory").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.maxLongMemory
            ).setDefaultValue(5)
                .setSaveConsumer { value -> config.maxLongMemory = value }
                .setTooltip(Component.literal("Maximum long-term memory size per Pokémon. Controls how much persistent context is stored."))
                .build()

            val decreaseFriendshipEntry = entryBuilder.startBooleanToggle(
                Component.literal("Decrease Friendship").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.decreaseFriendship
            ).setDefaultValue(false)
                .setSaveConsumer { value -> config.decreaseFriendship = value }
                .setTooltip(Component.literal("Dialogue can decrease friendship with players. Used for negative interactions."))
                .build()

            val increaseFriendshipEntry = entryBuilder.startBooleanToggle(
                Component.literal("Increase Friendship").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.increaseFriendship
            ).setDefaultValue(true)
                .setSaveConsumer { value -> config.increaseFriendship = value }
                .setTooltip(Component.literal("Dialogue can increase friendship with players. Used for positive interactions."))
                .build()

            val showFriendshipEntry = entryBuilder.startBooleanToggle(
                Component.literal("Show Friendship").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                config.showFriendship
            ).setDefaultValue(true)
                .setSaveConsumer { value -> config.showFriendship = value }
                .setTooltip(Component.literal("Displays friendship values in chat. Players can see relationship changes."))
                .build()

            // ========================= PROMPT AND OUTPUT =========================

            fun makeSpacer(height: Int = 8): AbstractConfigListEntry<Unit> {
                return object : AbstractConfigListEntry<Unit>(
                    Component.empty(),
                    false
                ) {
                    override fun getValue(): Unit? = null
                    override fun getDefaultValue(): Optional<Unit> = Optional.empty()
                    override fun children(): MutableList<GuiEventListener> = mutableListOf()
                    override fun narratables(): MutableList<NarratableEntry> = mutableListOf()

                    override fun render(
                        guiGraphics: GuiGraphics,
                        index: Int,
                        y: Int,
                        x: Int,
                        listWidth: Int,
                        itemHeight: Int,
                        mouseX: Int,
                        mouseY: Int,
                        isSelected: Boolean,
                        delta: Float
                    ) {
                        // não desenha nada, apenas ocupa espaço
                    }

                    override fun getItemHeight(): Int = height
                }
            }


            val instructEntry = object : AbstractConfigListEntry<Unit>(
                Component.literal("Instruct").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                false
            ) {
                override fun getValue(): Unit? = null
                override fun getDefaultValue(): Optional<Unit> = Optional.empty()
                override fun children(): MutableList<GuiEventListener> = mutableListOf()
                override fun narratables(): MutableList<NarratableEntry> = mutableListOf()

                override fun render(
                    guiGraphics: GuiGraphics,
                    index: Int,
                    y: Int,
                    x: Int,
                    listWidth: Int,
                    itemHeight: Int,
                    mouseX: Int,
                    mouseY: Int,
                    isSelected: Boolean,
                    delta: Float
                ) {
                    val font = Minecraft.getInstance().font
                    guiGraphics.drawString(font, fieldName, x, y, 0xFFFFFF, true)
                    guiGraphics.drawString(
                        font,
                        "Only editable via /cobblebrain or config/cobblebrain.json5",
                        x + 10,
                        y + font.lineHeight + 4,
                        0xAAAAAA,
                        false
                    )
                }
            }

            val outputFormatEntry = object : AbstractConfigListEntry<Unit>(
                Component.literal("Output Format").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                false
            ) {
                override fun getValue(): Unit? = null
                override fun getDefaultValue(): Optional<Unit> = Optional.empty()
                override fun children(): MutableList<GuiEventListener> = mutableListOf()
                override fun narratables(): MutableList<NarratableEntry> = mutableListOf()

                override fun render(
                    guiGraphics: GuiGraphics,
                    index: Int,
                    y: Int,
                    x: Int,
                    listWidth: Int,
                    itemHeight: Int,
                    mouseX: Int,
                    mouseY: Int,
                    isSelected: Boolean,
                    delta: Float
                ) {
                    val font = Minecraft.getInstance().font
                    guiGraphics.drawString(font, fieldName, x, y, 0xFFFFFF, true)
                    guiGraphics.drawString(
                        font,
                        "Only editable via /cobblebrain or config/cobblebrain.json5",
                        x + 10,
                        y + font.lineHeight + 4,
                        0xAAAAAA,
                        false
                    )
                }
            }

            category.entries.add(makeSubtitleEntry("AI CONFIGURATION", 0xFFFF00))
            category.entries.add(apiBaseUrlEntry)
            category.entries.add(localApiProviderEntry)
            category.entries.add(temperatureEntry)
            category.entries.add(aiProviderEntry)
            category.entries.add(reasoningEffortEntry)
            category.entries.add(requestTimeoutEntry)
            category.entries.add(debugLoggingEntry)
            category.entries.add(selectedLanguageEntry)
            category.entries.add(lowTokenModeEntry)
            category.entries.add(apiKeyEntry)
            category.entries.add(keyRotationTriggerEntry)
            category.entries.add(aiModelEntry)
            category.entries.add(modelRotationTriggerEntry)
            category.entries.add(keyRotationEntry)
            category.entries.add(modelRotationEntry)
            category.entries.add(makeSubtitleEntry("GAME AND INTERACTIONS", 0xFFFF00))
            category.entries.add(dialogueInChatEntry)
            category.entries.add(chatbubblesEntry)
            category.entries.add(pokemonTalkEntry)
            category.entries.add(allowPokemonPVPEntry)
            category.entries.add(allowPokemonPVEEntry)
            category.entries.add(dialogueOnDamageEntry)
            category.entries.add(dialogueOnBattleEntry)
            category.entries.add(spontaneousDialogueChanceEntry)
            category.entries.add(listenToChatEntry)
            category.entries.add(onlyNearbyChatEntry)
            category.entries.add(maxShortMemoryEntry)
            category.entries.add(maxLongMemoryEntry)
            category.entries.add(decreaseFriendshipEntry)
            category.entries.add(increaseFriendshipEntry)
            category.entries.add(showFriendshipEntry)
            category.entries.add(makeSubtitleEntry("PROMPT AND OUTPUT", 0xFFFF00))
            category.entries.add(instructEntry)
            category.entries.add(makeSpacer(10))
            category.entries.add(outputFormatEntry)

            builder.setSavingRunnable {
                ConfigHandler.save()
            }

            builder.build()
        }
    }
}
