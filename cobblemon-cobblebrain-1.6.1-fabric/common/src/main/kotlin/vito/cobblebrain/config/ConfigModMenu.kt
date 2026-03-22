package vito.cobblebrain.config

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
import vito.cobblebrain.config.ClientConfigHandler.clientConfig
import vito.cobblebrain.config.ConfigHandler.config
import java.util.Optional

object CobblebrainConfigScreen {
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
            }

            override fun getItemHeight(): Int = height
        }
    }

    fun create(parent: Screen?): Screen {
        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Cobblebrain Config"))

        val entryBuilder = builder.entryBuilder()
        val category = builder.getOrCreateCategory(Component.literal("Config"))

        // ========================= AI CONFIGURATION =========================

        val apiKeyEntry = entryBuilder.startStrList(
            Component.literal("API Key").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.apiKey
        ).setDefaultValue(listOf("YOUR_API_KEY"))
            .setSaveConsumer { value -> clientConfig.apiKey = value }
            .setTooltip(Component.literal("The API key used for authentication with the AI system. \nIt can be a Bearer token or a Google API key depending on the provider."))
            .build()

        val keyRotationEntry = entryBuilder.startBooleanToggle(
            Component.literal("Key Rotation").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.keyRotation
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.keyRotation = value }
            .setTooltip(Component.literal("Enables API key rotation when errors occur. \nUseful for handling invalid or expired keys."))
            .build()

        val keyRotationTriggerEntry = entryBuilder.startIntList(
            Component.literal("Key Rotation Trigger").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.keyRotationTrigger
        ).setDefaultValue(listOf(401, 429))
            .setSaveConsumer { value -> clientConfig.keyRotationTrigger = value }
            .setTooltip(Component.literal("List of HTTP status codes that trigger key rotation. \nDefines error conditions for switching keys."))
            .build()

        val apiBaseUrlEntry = entryBuilder.startStrField(
            Component.literal("API Base URL").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.apiBaseUrl
        ).setDefaultValue("http://127.0.0.1:4315")
            .setSaveConsumer { value -> clientConfig.apiBaseUrl = value }
            .setTooltip(Component.literal("The base URL of the API endpoint. \nExamples include \n- OpenRouter: https://openrouter.ai/api\n- Google AI Studio: https://generativelanguage.googleapis.com\n- Or a local LM Studio server."))
            .build()

        val localApiProviderEntry = entryBuilder.startStrField(
            Component.literal("Local API Provider").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.localApiProvider
        ).setDefaultValue("player2")
            .setSaveConsumer { value -> clientConfig.localApiProvider = value }
            .setTooltip(Component.literal("If apiBaseUrl is a local address (127.0.0.1), \nThe system uses the provider name to adapt messages for the correct provider. \nOfficially supported providers: player2, lmstudio"))
            .build()

        val aiModelEntry = entryBuilder.startStrList(
            Component.literal("AI Model").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.aiModel
        ).setDefaultValue(listOf("gemma-3-12b-it", "gemma-3-4b-it"))
            .setSaveConsumer { value -> clientConfig.aiModel = value }
            .setTooltip(Component.literal("The names of the AI models to use. \nExamples are gemini-2.5-flash, gemma-3-12b-it"))
            .build()

        val modelRotationEntry = entryBuilder.startBooleanToggle(
            Component.literal("Model Rotation").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.modelRotation
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.modelRotation = value }
            .setTooltip(Component.literal("Enables model rotation when errors occur. \nUseful for fallback to alternative models."))
            .build()

        val modelRotationTriggerEntry = entryBuilder.startIntList(
            Component.literal("Model Rotation Trigger").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.modelRotationTrigger
        ).setDefaultValue(listOf(404, 429))
            .setSaveConsumer { value -> clientConfig.modelRotationTrigger = value }
            .setTooltip(Component.literal("List of HTTP status codes that trigger model rotation. \nDefines error conditions for switching models."))
            .build()

        val temperatureEntry = entryBuilder.startIntSlider(
            Component.literal("Temperature"),
            (clientConfig.temperature * 100).toInt(),
            0,
            100
        )
            .setDefaultValue(70)
            .setSaveConsumer { value ->
                clientConfig.temperature = value / 100.0
            }
            .setTextGetter { value ->
                Component.literal(String.format("%.2f", value / 100.0))
            }
            .build()

        val aiProviderEntry = entryBuilder.startStrField(
            Component.literal("OpenRouter hint").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.aiProvider
        ).setDefaultValue("")
            .setSaveConsumer { value -> clientConfig.aiProvider = value }
            .setTooltip(Component.literal("A provider hint used for routing in OpenRouter. \nThis is ignored when using other provider."))
            .build()

        val reasoningEffortEntry = entryBuilder.startStrField(
            Component.literal("Reasoning Effort").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.reasoningEffort
        ).setDefaultValue("none")
            .setSaveConsumer { value -> clientConfig.reasoningEffort = value }
            .setTooltip(Component.literal("Defines the reasoning effort level for supported models. \nOptions include high, medium, low, auto, or none."))
            .build()

        val requestTimeoutEntry = entryBuilder.startLongField(
            Component.literal("Request Timeout (s)").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.requestTimeoutSeconds
        ).setDefaultValue(60L)
            .setSaveConsumer { value -> clientConfig.requestTimeoutSeconds = value }
            .setTooltip(Component.literal("Defines the request timeout in seconds. \nLocal models may require longer values."))
            .build()

        val debugLoggingEntry = entryBuilder.startBooleanToggle(
            Component.literal("Debug Logging").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.debugLogging
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.debugLogging = value }
            .setTooltip(Component.literal("Enables debug logging for troubleshooting. \nLogs are stored in the cobblebrain-ai/logs directory."))
            .build()

        val selectedLanguageEntry = entryBuilder.startStrField(
            Component.literal("Selected Language").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.selectedLanguage
        ).setDefaultValue("English")
            .setSaveConsumer { value -> clientConfig.selectedLanguage = value }
            .setTooltip(Component.literal("The language the AI uses for responses. \nDetermines dialogue output language."))
            .build()

        val dialogueInChatEntry = entryBuilder.startBooleanToggle(
            Component.literal("Dialogue In Chat").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.dialogueInChat
        ).setDefaultValue(true)
            .setSaveConsumer { value -> clientConfig.dialogueInChat = value }
            .setTooltip(Component.literal("Shows generated dialogue directly in the chat. \nThis makes Pokémon conversations visible to players."))
            .build()

        val chatbubblesEntry = entryBuilder.startBooleanToggle(
            Component.literal("Chat Bubbles").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.chatbubbles
        ).setDefaultValue(true)
            .setSaveConsumer { value -> clientConfig.chatbubbles = value }
            .setTooltip(Component.literal("Enables chat bubbles above characters. \nDialogue will appear visually instead of only in text chat."))
            .build()

        // ========================= GAME AND INTERACTIONS =========================

        val pokemonTalkEntry = entryBuilder.startBooleanToggle(
            Component.literal("Pokémon Talk").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.pokemonTalk
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.pokemonTalk = value }
            .setTooltip(Component.literal("Toggles whether Pokémon can talk or listen. \nThis acts as a simple on/off switch for dialogue."))
            .build()

        val allowPokemonPVPEntry = entryBuilder.startBooleanToggle(
            Component.literal("Allow Pokémon PVP").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.allowPokemonPVP
        ).setDefaultValue(false)
            .setSaveConsumer { value -> config.allowPokemonPVP = value }
            .setTooltip(Component.literal("Allows Pokémon to attack other players’ Pokémon. \nDisabling prevents player-versus-player battles."))
            .build()

        val allowPokemonPVEEntry = entryBuilder.startBooleanToggle(
            Component.literal("Allow Pokémon PVE").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.allowPokemonPVE
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.allowPokemonPVE = value }
            .setTooltip(Component.literal("Allows Pokémon to attack mobs in the world. \nExceptions include tamed mobs and non-aggressive tagged mobs."))
            .build()

        val scheduleRaidEntry = entryBuilder.startBooleanToggle(
            Component.literal("Schedule Raid").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.scheduleRaids
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.scheduleRaids = value }
            .setTooltip(Component.literal("Determines if raids can be created in the world.\nRaids can happen when your karma with a species falls below -11"))
            .build()

        val characteristicsEntry = entryBuilder.startStrList(
            Component.literal("Characteristics").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.characteristics
        ).setDefaultValue(listOf("TestPokemon: He likes to sing, he fell off a bike once, he is from a farm"))
            .setSaveConsumer { value -> clientConfig.characteristics = value }
            .setTooltip(Component.literal("list to define characteristics of a specific Pokémon for the AI. \nFormat: <pokemonName>: <text>"))
            .build()

        val lowTokenModeEntry = entryBuilder.startBooleanToggle(
            Component.literal("Low Token Mode").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.lowTokenMode
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.lowTokenMode = value }
            .setTooltip(Component.literal("Reduces world information sent to the AI. \nThis helps conserve tokens and lower usage costs."))
            .build()

        val dialogueOnDamageEntry = entryBuilder.startBooleanToggle(
            Component.literal("Dialogue On Damage").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.dialogueOnDamage
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.dialogueOnDamage = value }
            .setTooltip(Component.literal("Makes Pokémon speak when someone is hurt. \nDialogue is triggered by damage events."))
            .build()

        val dialogueOnBattleEntry = entryBuilder.startBooleanToggle(
            Component.literal("Dialogue On Battle").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.dialogueOnBattle
        ).setDefaultValue(true)
            .setSaveConsumer { value -> clientConfig.dialogueOnBattle = value }
            .setTooltip(Component.literal("Makes Pokémon speak during battle events. \nDialogue reflects combat situations."))
            .build()

        val wildPokemonTalkChanceEntry = entryBuilder.startIntSlider(
            Component.literal("Wild Pokemon Talk Chance").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            (config.wildPokemonTalkChance * 100).toInt(),
            0,
            100
        )
            .setDefaultValue(25)
            .setSaveConsumer { value ->
                config.wildPokemonTalkChance = value / 100.0
            }
            .setTextGetter { value ->
                Component.literal(String.format("%.2f", value / 100.0))
            }
            .setTooltip(Component.literal("Sets the chance of wild pokemon to participate in dialogue, not generate new ones. \nWild Pokémon may speak randomly during ongoing dialogues."))
            .build()

        val wildQuestChanceEntry = entryBuilder.startIntSlider(
            Component.literal("Wild Quest Pokemon").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            (config.wildQuestChance * 100).toInt(),
            0,
            100
        )
            .setDefaultValue(5)
            .setSaveConsumer { value ->
                config.wildQuestChance = value / 100.0
            }
            .setTextGetter { value ->
                Component.literal(String.format("%.2f", value / 100.0))
            }
            .setTooltip(Component.literal("Set the chance for a Pokémon to start a quest when you have a dialogue with wild Pokémon. \nThe quests can be of the type BATTLE, ITEM, or ADVICE."))
            .build()

        val spontaneousDialogueChanceEntry = entryBuilder.startIntSlider(
            Component.literal("Spontaneous Dialogue Chance").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            (clientConfig.spontaneousDialogueChance * 100).toInt(),
            0,
            100
        )
            .setDefaultValue(5)
            .setSaveConsumer { value ->
                clientConfig.spontaneousDialogueChance = value / 100.0
            }
            .setTextGetter { value ->
                Component.literal(String.format("%.2f", value / 100.0))
            }
            .setTooltip(Component.literal("Sets the chance of spontaneous dialogue. \nPokémon may speak randomly during idle moments."))
            .build()

        val listenToChatEntry = entryBuilder.startBooleanToggle(
            Component.literal("Listen To Chat").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.listenToChat
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.listenToChat = value }
            .setTooltip(Component.literal("Enables listening to regular player chat. \nIf disabled, the AI ignores non-command messages."))
            .build()

        val onlyNearbyChatEntry = entryBuilder.startBooleanToggle(
            Component.literal("[WIP] Only Nearby Chat").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.onlyNearbyChat
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.onlyNearbyChat = value }
            .setTooltip(Component.literal("EXPERIMENTAL: Restricts listening to nearby players only. \nWorks only if listenToChat is enabled."))
            .build()

        val maxShortMemoryEntry = entryBuilder.startIntField(
            Component.literal("Max Short Memory").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.maxShortMemory
        ).setDefaultValue(5)
            .setSaveConsumer { value -> clientConfig.maxShortMemory = value }
            .setTooltip(Component.literal("Maximum short-term memory size per Pokémon. \nControls how much recent context is stored."))
            .build()

        val maxLongMemoryEntry = entryBuilder.startIntField(
            Component.literal("Max Long Memory").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.maxLongMemory
        ).setDefaultValue(5)
            .setSaveConsumer { value -> clientConfig.maxLongMemory = value }
            .setTooltip(Component.literal("Maximum long-term memory size per Pokémon. \nControls how much persistent context is stored."))
            .build()

        val decreaseFriendshipEntry = entryBuilder.startBooleanToggle(
            Component.literal("Decrease Friendship").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.decreaseFriendship
        ).setDefaultValue(false)
            .setSaveConsumer { value -> config.decreaseFriendship = value }
            .setTooltip(Component.literal("Dialogue can decrease friendship with players. \nUsed for negative interactions."))
            .build()

        val increaseFriendshipEntry = entryBuilder.startBooleanToggle(
            Component.literal("Increase Friendship").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.increaseFriendship
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.increaseFriendship = value }
            .setTooltip(Component.literal("Dialogue can increase friendship with players. \nUsed for positive interactions."))
            .build()

        val showFriendshipEntry = entryBuilder.startBooleanToggle(
            Component.literal("Show Friendship").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.showFriendship
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.showFriendship = value }
            .setTooltip(Component.literal("Displays friendship values in chat. \nPlayers can see relationship changes."))
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

        val instructEntry = entryBuilder.startStrList(
            Component.literal("Instruct").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.instruct
        ).setDefaultValue(listOf(
            "[CREATIVE PROMPT]",
            "You are a screenwriter creating Pokémon dialogues.",
            "Pokémon must speak informally, with casual tone and natural flow.",
            "Humor, sarcasm, and playful banter are welcome, but not the only style.",
            "Pokémon must also show genuine emotions: joy, fear, doubt, affection, frustration, pride.",
            "Each Pokémon has a fixed core nature (Docile, Calm, Serious, Naive, Modest, Timid, Naughty).",
            "This nature is the foundation, but unique traits develop over time through memories, friendship changes, and experiences.",
            "Nature and traits are separate values, but both combine to define how each Pokémon talks.",
            "Dialogue must feel emotional and personal:",
            "- Show individuality, never flat or generic.",
            "- Reactions must reflect environment (weather, biome, time of day, terrain, nearby entities, player status).",
            "- If the player asks a question, respond from the Pokémon’s perspective.",
            "- Never use human elements (phones, social media, etc)."
        ))
            .setSaveConsumer { value -> clientConfig.instruct = value }
            .setTooltip(Component.literal("The Instructs as a whole works as a global prompt.\nDefines how the AI or Pokemon behave, think and responds.\nEach instruct (item on the list) shapes how the response is sent.\n"))
            .build()

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
        category.entries.add(scheduleRaidEntry)
        category.entries.add(characteristicsEntry)
        category.entries.add(dialogueInChatEntry)
        category.entries.add(chatbubblesEntry)
        category.entries.add(pokemonTalkEntry)
        category.entries.add(allowPokemonPVPEntry)
        category.entries.add(allowPokemonPVEEntry)
        category.entries.add(dialogueOnDamageEntry)
        category.entries.add(dialogueOnBattleEntry)
        category.entries.add(spontaneousDialogueChanceEntry)
        category.entries.add(wildPokemonTalkChanceEntry)
        category.entries.add(wildQuestChanceEntry)
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
            ClientConfigHandler.save()
        }

        return builder.build()
    }
}