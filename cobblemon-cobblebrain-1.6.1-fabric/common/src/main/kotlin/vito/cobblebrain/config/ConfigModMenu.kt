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
import net.minecraft.sounds.SoundEvents
import vito.cobblebrain.client.CobblebrainClientCommon
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

    fun makeDescriptionEntry(text: String, color: Int = 0xAAAAAA, height: Int = 20): AbstractConfigListEntry<Unit> {
        return object : AbstractConfigListEntry<Unit>(
            Component.literal(text).withStyle(
                Style.EMPTY.withColor(TextColor.fromRgb(color))
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
                guiGraphics.drawString(font, fieldName, centerX, y + (itemHeight / 2 - font.lineHeight / 2), color, false)
            }

            override fun getItemHeight(): Int = height
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

    private fun <T> getConfigValue(
        synced: T,
        local: T
    ): T {
        val mc = Minecraft.getInstance()
        val isInWorld = mc.player != null

        return if (isInWorld && SyncedConfig.received) synced else local
    }

    fun create(parent: Screen?): Screen {
        var useDefaultOutput = true
        var outputDialogue = true
        var outputActions = true
        var outputFriendship = true
        var outputMemories = true
        var outputApril1 = false
        var outputQuests = true
        var outputPokemonLanguage = false
        var needsPokemonTranslator = false
        var outputGuaranteedCatch = true
        var enableKarma = true
        var maxStoredMemories = 100
        var maxRelevantMemories = 4

        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Cobblebrain Config"))

        val entryBuilder = builder.entryBuilder()

        val recommendedPromptButton = object : AbstractConfigListEntry<Unit>(
            Component.literal("Use Recommended Prompt"),
            false
        ) {

            private var clicked = false

            private var button: net.minecraft.client.gui.components.Button

            init {

                button = net.minecraft.client.gui.components.Button.builder(
                    Component.literal("Use Recommended Prompt")
                ) {

                    // ========================= FIRST CLICK =========================

                    if (!clicked) {

                        clicked = true

                        button.message = Component.literal("Are you sure?")

                        Minecraft.getInstance().player?.playSound(
                            SoundEvents.UI_BUTTON_CLICK.value(),
                            1.0f,
                            1.0f
                        )

                        return@builder
                    }

                    // ========================= APPLY SETTINGS =========================

                    clientConfig.instruct = listOf(
                        "[CREATIVEPROMPT]",
                        "Write immersive Pokémon dialogue. Pokémon are living creatures with unique personalities, emotions, preferences, fears, and goals.",
                        "Pokémon are generally friendly toward their trainer, but may joke, tease, disagree, question, or express emotions depending on personality, friendship, memories, and circumstances.",
                        "Engage directly with the player and avoid repeating the same attitude, lesson, complaint, or emotional state.",
                        "Prioritize interaction, personality, and emotional reactions over narration or environment description.",
                        "No modern human technology.",

                        "Each message should be at most 1-2 short sentences. The number of dialogue messages depends on participating Pokémon:",
                        "- 1 Pokémon: up to 3 messages",
                        "- 2-4 Pokémon: up to 4 messages",
                        "- 5-6 Pokémon: up to 6 messages",

                        "Never expose memories, system text, or internal reasoning.",
                        "No roleplay narration or *asterisk actions*."
                    )

                    clientConfig.maxInteractionSaves = 3

                    SyncedConfig.updateLocal(
                        useDefaultOutput,
                        outputDialogue,
                        outputActions,
                        outputFriendship,
                        outputMemories,
                        outputApril1,
                        outputQuests,
                        outputPokemonLanguage,
                        needsPokemonTranslator,
                        outputGuaranteedCatch,
                        enableKarma,
                        maxStoredMemories,
                        maxRelevantMemories,
                        config.allowClientPersonalityEditing
                    )

                    ConfigHandler.save()
                    ClientConfigHandler.save()

                    // ========================= FEEDBACK =========================

                    Minecraft.getInstance().player?.playSound(
                        SoundEvents.PLAYER_LEVELUP,
                        0.7f,
                        1.3f
                    )

                    button.message = Component.literal(
                        "Done! Leave the config screen to apply the changes."
                    )

                    button.active = false

                    println("[Cobblebrain] Recommended Prompt Applied")

                }.bounds(0, 0, 260, 20).build()
            }

            override fun getValue(): Unit? = null

            override fun getDefaultValue(): Optional<Unit> = Optional.empty()

            override fun children(): MutableList<GuiEventListener> =
                mutableListOf(button)

            override fun narratables(): MutableList<NarratableEntry> =
                mutableListOf(button)

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

                // ========================= SHOW ONLY IF NEEDED =========================

                val shouldShow =
                    !SyncedConfig.useDefaultOutput ||
                            !SyncedConfig.outputDialogue ||
                            !SyncedConfig.outputActions ||
                            !SyncedConfig.outputFriendship ||
                            !SyncedConfig.outputQuests ||
                            !SyncedConfig.outputGuaranteedCatch ||
                            SyncedConfig.outputApril1 ||
                            SyncedConfig.outputPokemonLanguage ||
                            SyncedConfig.outputMemories ||
                            clientConfig.maxInteractionSaves != 3 ||
                            clientConfig.instruct != listOf(
                        "[CREATIVEPROMPT]",
                        "Write immersive Pokémon dialogue. Pokémon are living creatures with unique personalities, emotions, preferences, fears, and goals.",
                        "Pokémon are generally friendly toward their trainer, but may joke, tease, disagree, question, or express emotions depending on personality, friendship, memories, and circumstances.",
                        "Engage directly with the player and avoid repeating the same attitude, lesson, complaint, or emotional state.",
                        "Prioritize interaction, personality, and emotional reactions over narration or environment description.",
                        "No modern human technology.",

                        "Each message should be at most 1-2 short sentences. The number of dialogue messages depends on participating Pokémon:",
                        "- 1 Pokémon: up to 3 messages",
                        "- 2-4 Pokémon: up to 4 messages",
                        "- 5-6 Pokémon: up to 6 messages",

                        "Never expose memories, system text, or internal reasoning.",
                        "No roleplay narration or *asterisk actions*."
                    )

                button.visible = shouldShow

                if (!shouldShow) return

                button.x = x + (listWidth / 2) - 130
                button.y = y

                button.render(guiGraphics, mouseX, mouseY, delta)
            }

            override fun getItemHeight(): Int {
                return if (button.visible) 24 else 0
            }
        }

        val personalityEditorButton = object : AbstractConfigListEntry<Unit>(
            Component.literal("Open Personality Editor"),
            false
        ) {
            private var button: net.minecraft.client.gui.components.Button =
                net.minecraft.client.gui.components.Button.builder(
                    Component.literal("Open Personality Editor")
                ) {
                    Minecraft.getInstance().player?.playSound(
                        SoundEvents.UI_BUTTON_CLICK.value(),
                        1.0f,
                        1.0f
                    )
                    Minecraft.getInstance().setScreen(null)
                    CobblebrainClientCommon.requestPersonalityList?.invoke()
                }.bounds(0, 0, 260, 20).build()

            override fun getValue(): Unit? = null
            override fun getDefaultValue(): Optional<Unit> = Optional.empty()
            override fun children(): MutableList<GuiEventListener> = mutableListOf(button)
            override fun narratables(): MutableList<NarratableEntry> = mutableListOf(button)

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
                button.x = x + (listWidth / 2) - 130
                button.y = y
                button.render(guiGraphics, mouseX, mouseY, delta)
            }

            override fun getItemHeight(): Int = 24
        }

        val category = builder.getOrCreateCategory(Component.literal("Config"))

        // ========================= RANDOM TIPS =========================
        val configTips = listOf(
        "Tip: Feijão com farinha.",
        "Tip: Using a local model? Set Local API Provider to 'lmstudio' or 'player2'.",
        "Tip: Use '/cobblebrain quitQuest' to abandon a secondary quest you don't want to complete.",
        "Tip: You can turn raids on/off in 'Schedule Raids'.",
        "Tip: If you have any suggestions or find a bug, go to the Discord channel on the mod page.",
        "Tip: Control how many interactions the AI remembers in 'Recent Memories Limit'.",
        "Tip: Disable 'Use Chat Endpoint' if your API URL doesn't use the standard /v1/chat/completions path.",
        "Tip: POKÉMON. ARE. ANIMALS.",
        "Tip: Too many words. 7.8/10",
        "Tip: Did someone say something about an insect collection?",
        "Tip: Clodsire is just a big happy potato, treat him with respect!",
        "Tip: If the AI takes too long to respond, consider deactivating some systems in 'AI CAPABILITIES'.",
        "Tip: If the AI takes too long to respond, consider reducing 'Recent Memories Limit'.",
        "Tip: If you want to change how Pokémon speak/behave, go to 'Instruct' and add your custom instructions.",
        "Tip: If you turn 'Listen To Chat' ON, the AI will respond to every message you send in chat.",
        "Tip: You can change how often Pokémon speak by themselves in 'Spontaneous Dialogue Chance'.",
        "Tip: Turn ON 'Need Pokémon Translator' to require an EXP SHARE equipped by the player for Pokémon to speak human language.",
        "Tip: Turn ON 'Debug Logging' to get detailed logs of messages and prompts in the cobblebrain-ai folder.",
        "Tip: If the AI takes too long to respond, consider activating 'Low Token Mode'.",
        "Tip: Never share your API key with anyone!",
        "Tip: If your friends play this mod together, each player must run their own AI model on their own device.",
        "Tip: The Action HUD works even without the AI being online!",
        "Tip: Try feeding your Pokémon various berries/vanilla foods; some of them can give buffs/XP to your Pokémon!",
        "Tip: You gain karma by completing quests and lose it by killing Pokémon or failing quests. Don't let it drop too low... or else...",
        "Tip: Use /cobblebrain karma to see how much karma you have with each Pokémon species.",
        "Tip: If you don't like Pokémon talking during battle, turn off 'Dialogue On Battle'.",
        "Tip: If you want Pokémon to react when you get hurt, turn on 'Dialogue On Damage'.",
        "Tip: Use /cobblebrain guide to receive a guide on the basic mechanics of the mod.",
        "Tip: If you convince a Pokémon to be captured through dialogue, a notification will appear and it will have a 100% catch rate!",
        "Tip: If you want a certain species or individual Pokémon to behave in a specific way, go to the 'Characteristics' setting.",
        "Tip: Activate 'key/model rotation' to use different inserted keys or AI models when one fails."
    )
        
        val randomTip = configTips.random()
        
        val words = randomTip.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        
        for (word in words) {
            if (currentLine.length + word.length < 70) {
                currentLine += "$word "
            } else {
                lines.add(currentLine.trim())
                currentLine = "$word "
            }
        }
        if (currentLine.isNotBlank()) {
            lines.add(currentLine.trim())
        }
        
        for (line in lines) {
            category.addEntry(makeDescriptionEntry(line, 0xFFFFFF, 14))
        }
        category.addEntry(makeSpacer(10))

        // ========================= AI CONFIGURATION =========================

        val apiKeyEntry = entryBuilder.startStrList(
            Component.literal("API Key").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.apiKey
        ).setDefaultValue(listOf("YOUR_API_KEY"))
            .setSaveConsumer { value -> clientConfig.apiKey = value }
            .setTooltip(Component.translatable("cobblebrain.config.api_key.tooltip"))
            .build()

        val keyRotationEntry = entryBuilder.startBooleanToggle(
            Component.literal("Key Rotation").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.keyRotation
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.keyRotation = value }
            .setTooltip(Component.translatable("cobblebrain.config.key_rotation.tooltip"))
            .build()

        val keyRotationTriggerEntry = entryBuilder.startIntList(
            Component.literal("Key Rotation Trigger").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.keyRotationTrigger
        ).setDefaultValue(listOf(401, 429))
            .setSaveConsumer { value -> clientConfig.keyRotationTrigger = value }
            .setTooltip(Component.translatable("cobblebrain.config.key_rotation_trigger.tooltip"))
            .build()

        val apiBaseUrlEntry = entryBuilder.startStrField(
            Component.literal("API Base URL").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.apiBaseUrl
        ).setDefaultValue("http://127.0.0.1:4315")
            .setSaveConsumer { value -> clientConfig.apiBaseUrl = value }
            .setTooltip(Component.translatable("cobblebrain.config.api_base_url.tooltip"))
            .build()

        val useChatEndpointEntry = entryBuilder.startBooleanToggle(
            Component.literal("Use Chat Endpoint").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.useChatEndpoint
        ).setDefaultValue(true)
            .setSaveConsumer { value -> clientConfig.useChatEndpoint = value }
            .setTooltip(Component.translatable("cobblebrain.config.use_chat_endpoint.tooltip"))
            .build()

        val localApiProviderEntry = entryBuilder.startStrField(
            Component.literal("Local API Provider").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.localApiProvider
        ).setDefaultValue("player2")
            .setSaveConsumer { value -> clientConfig.localApiProvider = value }
            .setTooltip(Component.translatable("cobblebrain.config.local_api_provider.tooltip"))
            .build()

        val aiModelEntry = entryBuilder.startStrList(
            Component.literal("AI Model").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.aiModel
        ).setDefaultValue(listOf("gemma-3-12b-it", "gemma-3-4b-it"))
            .setSaveConsumer { value -> clientConfig.aiModel = value }
            .setTooltip(Component.translatable("cobblebrain.config.ai_model.tooltip"))
            .build()

        val modelRotationEntry = entryBuilder.startBooleanToggle(
            Component.literal("Model Rotation").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.modelRotation
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.modelRotation = value }
            .setTooltip(Component.translatable("cobblebrain.config.model_rotation.tooltip"))
            .build()

        val modelRotationTriggerEntry = entryBuilder.startIntList(
            Component.literal("Model Rotation Trigger").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.modelRotationTrigger
        ).setDefaultValue(listOf(404, 429))
            .setSaveConsumer { value -> clientConfig.modelRotationTrigger = value }
            .setTooltip(Component.translatable("cobblebrain.config.model_rotation_trigger.tooltip"))
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
            .setTooltip(Component.translatable("cobblebrain.config.ai_provider.tooltip"))
            .build()

        val reasoningEffortEntry = entryBuilder.startStrField(
            Component.literal("Reasoning Effort").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.reasoningEffort
        ).setDefaultValue("none")
            .setSaveConsumer { value -> clientConfig.reasoningEffort = value }
            .setTooltip(Component.translatable("cobblebrain.config.reasoning_effort.tooltip"))
            .build()

        val requestTimeoutEntry = entryBuilder.startLongField(
            Component.literal("Request Timeout (s)").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.requestTimeoutSeconds
        ).setDefaultValue(60L)
            .setSaveConsumer { value -> clientConfig.requestTimeoutSeconds = value }
            .setTooltip(Component.translatable("cobblebrain.config.request_timeout.tooltip"))
            .build()

        val debugLoggingEntry = entryBuilder.startBooleanToggle(
            Component.literal("Debug Logging").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.debugLogging
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.debugLogging = value }
            .setTooltip(Component.translatable("cobblebrain.config.debug_logging.tooltip"))
            .build()

        val ignoreHungerEntry = entryBuilder.startBooleanToggle(
            Component.literal("Ignore Hunger").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.ignoreHunger
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.ignoreHunger = value }
            .setTooltip(Component.translatable("cobblebrain.config.ignore_hunger.tooltip"))
            .build()

        val useDefaultOutputEntry = entryBuilder.startBooleanToggle(
            Component.literal("[Recommended] Use Default Output").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            SyncedConfig.useDefaultOutput
        ).setDefaultValue(true)
            .setSaveConsumer { value -> useDefaultOutput = value }
            .setTooltip(Component.translatable("cobblebrain.config.use_default_output.tooltip"))
            .build()

        val selectedLanguageEntry = entryBuilder.startStrField(
            Component.literal("Selected Language").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.selectedLanguage
        ).setDefaultValue("English")
            .setSaveConsumer { value -> clientConfig.selectedLanguage = value }
            .setTooltip(Component.translatable("cobblebrain.config.selected_language.tooltip"))
            .build()

        val preferredNameEntry = entryBuilder.startStrField(
            Component.literal("Preferred Name")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.preferredName.ifBlank { Minecraft.getInstance().user.name }
        )
            .setDefaultValue(Minecraft.getInstance().user.name)
            .setSaveConsumer { value -> clientConfig.preferredName = value }
            .setTooltip(
                Component.translatable(
                    "cobblebrain.config.preferred_name.tooltip"
                )
            )
            .build()

        val offlineModeEntry = entryBuilder.startBooleanToggle(
            Component.literal("Offline Mode").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.offlineMode
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.offlineMode = value }
            .setTooltip(Component.translatable("cobblebrain.config.offline_mode.tooltip"))
            .build()

        val offlineTalkModeEntry = entryBuilder.startBooleanToggle(
            Component.literal("Offline Talk Mode").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.offlineTalkMode
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.offlineTalkMode = value }
            .setTooltip(Component.translatable("cobblebrain.config.offline_talk_mode.tooltip"))
            .build()

        val psychicTranslationEntry = entryBuilder.startBooleanToggle(
            Component.literal("Psychic Pokémon Translation").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.psychicTranslation
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.psychicTranslation = value }
            .setTooltip(Component.translatable("cobblebrain.config.psychic_translation.tooltip"))
            .build()

        val maxInteractionSavesEntry = entryBuilder.startIntField(
            Component.literal("Recent Context Limit").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.maxInteractionSaves
        ).setDefaultValue(3)
            .setSaveConsumer { value -> clientConfig.maxInteractionSaves = value }
            .setTooltip(Component.translatable("cobblebrain.config.max_interaction_saves.tooltip"))
            .build()

        val dialogueInChatEntry = entryBuilder.startBooleanToggle(
            Component.literal("Dialogue In Chat").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.dialogueInChat
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.dialogueInChat = value }
            .setTooltip(Component.translatable("cobblebrain.config.dialogue_in_chat.tooltip"))
            .build()

        val chatbubblesEntry = entryBuilder.startBooleanToggle(
            Component.literal("Chat Bubbles").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.chatbubbles
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.chatbubbles = value }
            .setTooltip(Component.translatable("cobblebrain.config.chatbubbles.tooltip"))
            .build()

        // ========================= GAME AND INTERACTIONS =========================

        val needsPokemonTranslatorEntry = entryBuilder.startBooleanToggle(
            Component.literal("Needs Pokémon Translator").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.needsPokemonTranslator,
                config.needsPokemonTranslator
            )
        ).setDefaultValue(false)
            .setSaveConsumer { value -> needsPokemonTranslator = value }
            .setTooltip(Component.translatable("cobblebrain.config.needs_pokemon_translator.tooltip"))
            .build()

        val allowPokemonPVPEntry = entryBuilder.startBooleanToggle(
            Component.literal("Allow Pokémon PVP").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.allowPokemonPVP
        ).setDefaultValue(false)
            .setSaveConsumer { value -> config.allowPokemonPVP = value }
            .setTooltip(Component.translatable("cobblebrain.config.allow_pokemon_pvp.tooltip"))
            .build()

        val allowPokemonPVEEntry = entryBuilder.startBooleanToggle(
            Component.literal("Allow Pokémon PVE").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.allowPokemonPVE
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.allowPokemonPVE = value }
            .setTooltip(Component.translatable("cobblebrain.config.allow_pokemon_pve.tooltip"))
            .build()

        val enableKarmaEntry = entryBuilder.startBooleanToggle(
            Component.literal("Enable Karma").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.enableKarma,
                config.enableKarma
            )
        ).setDefaultValue(true)
            .setSaveConsumer { value -> enableKarma = value }
            .setTooltip(Component.translatable("cobblebrain.config.enable_karma.tooltip"))
            .build()

        val scheduleRaidEntry = entryBuilder.startBooleanToggle(
            Component.literal("Schedule Raid").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.scheduleRaids
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.scheduleRaids = value }
            .setTooltip(Component.translatable("cobblebrain.config.schedule_raid.tooltip"))
            .build()

        val characteristicsEntry = entryBuilder.startStrList(
            Component.literal("[OUTDATED] Characteristics").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x888888))),
            config.characteristics
        ).setDefaultValue(listOf("TestPokemon: He likes to sing, he fell off a bike once, he is from a farm"))
            .setSaveConsumer { value -> config.characteristics = value }
            .setTooltip(Component.translatable("cobblebrain.config.characteristics.tooltip"))
            .build()

        val enableTraitsEntry = entryBuilder.startBooleanToggle(
            Component.literal("Enable Automatic Trait Creation").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(SyncedConfig.allowClientPersonalityEditing, config.enableTraits)
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.enableTraits = value }
            .setTooltip(Component.literal("If enabled, the AI will automatically generate and evolve Traits and Quirks for your Pokémon."))
            .build()

        val allowClientPersonalityEditingEntry = entryBuilder.startBooleanToggle(
            Component.literal("Allow Client Personality Editing").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(SyncedConfig.allowClientPersonalityEditing, config.allowClientPersonalityEditing)
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.allowClientPersonalityEditing = value; config.allowClientPersonalityEditing = value }
            .setTooltip(Component.literal("If enabled, players can use the Personality Editor to manually edit their Pokémon's personality. When disabled, the editor becomes read-only."))
            .build()

        val lowTokenModeEntry = entryBuilder.startBooleanToggle(
            Component.literal("Low Token Mode").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.lowTokenMode
        ).setDefaultValue(false)
            .setSaveConsumer { value -> config.lowTokenMode = value }
            .setTooltip(Component.translatable("cobblebrain.config.low_token_mode.tooltip"))
            .build()

        val dialogueOnDamageEntry = entryBuilder.startBooleanToggle(
            Component.literal("Dialogue On Damage").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.dialogueOnDamage
        ).setDefaultValue(false)
            .setSaveConsumer { value -> config.dialogueOnDamage = value }
            .setTooltip(Component.translatable("cobblebrain.config.dialogue_on_damage.tooltip"))
            .build()

        val dialogueOnBattleEntry = entryBuilder.startBooleanToggle(
            Component.literal("Dialogue On Battle").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.dialogueOnBattle
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.dialogueOnBattle = value }
            .setTooltip(Component.translatable("cobblebrain.config.dialogue_on_battle.tooltip"))
            .build()

        val wildPokemonTalkChanceEntry = entryBuilder.startIntSlider(
            Component.literal("Wild Pokemon Talk Chance").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            (config.wildPokemonTalkChance * 100).toInt(),
            0,
            100
        )
            .setDefaultValue(10)
            .setSaveConsumer { value ->
                config.wildPokemonTalkChance = value / 100.0
            }
            .setTextGetter { value ->
                Component.literal(String.format("%.2f", value / 100.0))
            }
            .setTooltip(Component.translatable("cobblebrain.config.wild_pokemon_talk_chance.tooltip"))
            .build()

        val wildQuestChanceEntry = entryBuilder.startIntSlider(
            Component.literal("Wild Quest Pokemon").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            (config.wildQuestChance * 100).toInt(),
            0,
            100
        )
            .setDefaultValue(20)
            .setSaveConsumer { value ->
                config.wildQuestChance = value / 100.0
            }
            .setTextGetter { value ->
                Component.literal(String.format("%.2f", value / 100.0))
            }
            .setTooltip(Component.translatable("cobblebrain.config.wild_quest_chance.tooltip"))
            .build()

        val spontaneousDialogueChanceEntry = entryBuilder.startIntSlider(
            Component.literal("Spontaneous Dialogue Chance").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            (config.spontaneousDialogueChance * 100).toInt(),
            0,
            100
        )
            .setDefaultValue(5)
            .setSaveConsumer { value ->
                config.spontaneousDialogueChance = value / 100.0
            }
            .setTextGetter { value ->
                Component.literal(String.format("%.2f", value / 100.0))
            }
            .setTooltip(Component.translatable("cobblebrain.config.spontaneous_dialogue_chance.tooltip"))
            .build()

        val listenToChatEntry = entryBuilder.startBooleanToggle(
            Component.literal("Listen To Chat").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.listenToChat
        ).setDefaultValue(false)
            .setSaveConsumer { value -> config.listenToChat = value }
            .setTooltip(Component.translatable("cobblebrain.config.listen_to_chat.tooltip"))
            .build()

        val onlyNearbyChatEntry = entryBuilder.startBooleanToggle(
            Component.literal("Only Nearby Chat").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.onlyNearbyChat
        ).setDefaultValue(false)
            .setSaveConsumer { value -> config.onlyNearbyChat = value }
            .setTooltip(Component.translatable("cobblebrain.config.only_nearby_chat.tooltip"))
            .build()

        val maxStoredMemoriesEntry = entryBuilder.startIntField(
            Component.literal("Max Stored Memories").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.maxStoredMemories,
                config.maxStoredMemories
            )
        ).setDefaultValue(100)
            .setSaveConsumer { value -> maxStoredMemories = value }
            .setTooltip(Component.translatable("cobblebrain.config.max_stored_memories.tooltip"))
            .build()

        val maxRelevantMemoriesEntry = entryBuilder.startIntField(
            Component.literal("Max Relevant Memories").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.maxRelevantMemories,
                config.maxRelevantMemories
            )
        ).setDefaultValue(4)
            .setSaveConsumer { value -> maxRelevantMemories = value }
            .setTooltip(Component.translatable("cobblebrain.config.max_relevant_memories.tooltip"))
            .build()

        val decreaseFriendshipEntry = entryBuilder.startBooleanToggle(
            Component.literal("Decrease Friendship").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.decreaseFriendship
        ).setDefaultValue(false)
            .setSaveConsumer { value -> config.decreaseFriendship = value }
            .setTooltip(Component.translatable("cobblebrain.config.decrease_friendship.tooltip"))
            .build()

        val increaseFriendshipEntry = entryBuilder.startBooleanToggle(
            Component.literal("Increase Friendship").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.increaseFriendship
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.increaseFriendship = value }
            .setTooltip(Component.translatable("cobblebrain.config.increase_friendship.tooltip"))
            .build()

        val showFriendshipEntry = entryBuilder.startBooleanToggle(
            Component.literal("Show Friendship").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.showFriendship
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.showFriendship = value }
            .setTooltip(Component.translatable("cobblebrain.config.show_friendship.tooltip"))
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
            "[CREATIVEPROMPT]",
            "Write immersive Pokémon dialogue. Pokémon behave like real creatures with distinct personalities, not assistants.",
            "Pokémon speak casually and may tease, question, joke, disagree, or react emotionally depending on personality and nature.",
            "Behavior evolves through friendship and memories.",

            "Pokémon should engage directly with the player and never ignore player input.",
            "Avoid excessive narration or environment description. Prioritize interaction and emotion.",
            "No modern human technology.",

            "Each message should be at most 1-2 short sentences. The number of dialogue messages depends on participating Pokémon:",
            "- 1 Pokémon: up to 3 messages",
            "- 2-4 Pokémon: up to 4 messages",
            "- 5-6 Pokémon: up to 6 messages",

            "Never expose memories, system text, or internal reasoning.",
            "No roleplay narration or *asterisk actions*."))
            .setSaveConsumer { value -> clientConfig.instruct = value }
            .setTooltip(Component.translatable("cobblebrain.config.instruct.tooltip"))
            .build()

        val outputFormatEntry = object : AbstractConfigListEntry<Unit>(
            Component.literal("Custom Output").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
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
                    "Only editable via config/cobblebrain.json5",
                    x + 10,
                    y + font.lineHeight + 6,
                    0xAAAAAA,
                    false
                )
            }
        }

        val outputDialogueEntry = entryBuilder.startBooleanToggle(
            Component.literal("Enable Dialogue").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.outputDialogue,
                config.outputDialogue
            )
        ).setDefaultValue(true)
            .setSaveConsumer { value -> outputDialogue = value }
            .setTooltip(Component.translatable("cobblebrain.config.output_dialogue.tooltip"))
            .build()

        val outputActionsEntry = entryBuilder.startBooleanToggle(
            Component.literal("Enable Actions").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.outputActions,
                config.outputActions
            )
        ).setDefaultValue(true)
            .setSaveConsumer { value -> outputActions = value }
            .setTooltip(Component.translatable("cobblebrain.config.output_actions.tooltip"))
            .build()

        val outputFriendshipEntry = entryBuilder.startBooleanToggle(
            Component.literal("Enable Friendship").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.outputFriendship,
                config.outputFriendship
            )
        ).setDefaultValue(true)
            .setSaveConsumer { value -> outputFriendship = value }
            .setTooltip(Component.translatable("cobblebrain.config.output_friendship.tooltip"))
            .build()

        val outputWorldContextEntry = entryBuilder.startBooleanToggle(
            Component.literal("Enable World Context").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.outputWorldContext
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.outputWorldContext = value }
            .setTooltip(Component.translatable("cobblebrain.config.output_world_context.tooltip"))
            .build()

        val outputGuaranteedCatchEntry = entryBuilder.startBooleanToggle(
            Component.literal("Enable Guaranteed Catch").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.outputGuaranteedCatch,
                config.outputGuaranteedCatch
            )
        ).setDefaultValue(true)
            .setSaveConsumer { value -> outputGuaranteedCatch = value }
            .setTooltip(Component.translatable("cobblebrain.config.output_guaranteed_catch.tooltip"))
            .build()

        val outputMobsContextEntry = entryBuilder.startBooleanToggle(
            Component.literal("Enable Mobs Context").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.outputMobsContext
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.outputMobsContext = value }
            .setTooltip(Component.translatable("cobblebrain.config.output_mobs_context.tooltip"))
            .build()

        val outputQuestsEntry = entryBuilder.startBooleanToggle(
            Component.literal("Enable Quests").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.outputQuests,
                config.outputQuests
            )
        ).setDefaultValue(true)
            .setSaveConsumer { value -> outputQuests = value }
            .setTooltip(Component.translatable("cobblebrain.config.output_quests.tooltip"))
            .build()

        // the fun one
        val outputApril1Entry = entryBuilder.startBooleanToggle(
            Component.literal("Enable April Fools Actions").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.outputApril1,
                config.outputApril1
            )
        ).setDefaultValue(false)
            .setSaveConsumer { value -> outputApril1 = value }
            .setTooltip(Component.translatable("cobblebrain.config.output_april1.tooltip"))
            .build()

        val outputPokemonLanguageEntry = entryBuilder.startBooleanToggle(
            Component.literal("Enable Pokemon Language").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.outputPokemonLanguage,
                config.outputPokemonLanguage
            )
        ).setDefaultValue(false)
            .setSaveConsumer { value -> outputPokemonLanguage = value }
            .setTooltip(Component.translatable("cobblebrain.config.output_pokemon_language.tooltip"))
            .build()

        val outputMemoriesEntry = entryBuilder.startBooleanToggle(
            Component.literal("Enable Memories").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.outputMemories,
                config.outputMemories
            )
        ).setDefaultValue(true)
            .setSaveConsumer { value -> outputMemories = value }
            .setTooltip(Component.translatable("cobblebrain.config.output_memories.tooltip"))
            .build()

        category.entries.add(recommendedPromptButton)
        category.entries.add(personalityEditorButton)
        category.entries.add(makeSubtitleEntry("AI CONFIGURATION (CLIENT)", 0xFFFF00))
        category.entries.add(apiBaseUrlEntry)
        category.entries.add(useChatEndpointEntry)
        category.entries.add(localApiProviderEntry)
        category.entries.add(temperatureEntry)
        category.entries.add(aiProviderEntry)
        category.entries.add(reasoningEffortEntry)
        category.entries.add(requestTimeoutEntry)
        category.entries.add(debugLoggingEntry)
        category.entries.add(apiKeyEntry)
        category.entries.add(keyRotationTriggerEntry)
        category.entries.add(aiModelEntry)
        category.entries.add(modelRotationTriggerEntry)
        category.entries.add(keyRotationEntry)
        category.entries.add(modelRotationEntry)
        category.entries.add(selectedLanguageEntry)
        category.entries.add(maxInteractionSavesEntry)
        category.entries.add(preferredNameEntry)
        category.entries.add(offlineModeEntry)
        category.entries.add(offlineTalkModeEntry)
        category.entries.add(psychicTranslationEntry)
        category.entries.add(makeSubtitleEntry("GAME AND INTERACTIONS (SERVER)", 0xFFFF00))
        category.entries.add(needsPokemonTranslatorEntry)
        category.entries.add(listenToChatEntry)
        category.entries.add(dialogueInChatEntry)
        category.entries.add(chatbubblesEntry)
        category.entries.add(spontaneousDialogueChanceEntry)
        category.entries.add(wildPokemonTalkChanceEntry)
        category.entries.add(wildQuestChanceEntry)
        category.entries.add(lowTokenModeEntry)
        category.entries.add(scheduleRaidEntry)
        category.entries.add(allowClientPersonalityEditingEntry)
        category.entries.add(allowPokemonPVPEntry)
        category.entries.add(allowPokemonPVEEntry)
        category.entries.add(enableKarmaEntry)
        category.entries.add(dialogueOnDamageEntry)
        category.entries.add(dialogueOnBattleEntry)
        category.entries.add(decreaseFriendshipEntry)
        category.entries.add(increaseFriendshipEntry)
        category.entries.add(showFriendshipEntry)
        category.entries.add(makeSubtitleEntry("PROMPT AND OUTPUT (CLIENT)", 0xFFFF00))
        category.entries.add(ignoreHungerEntry)
        category.entries.add(instructEntry)
        category.entries.add(makeSpacer(10))
        category.entries.add(outputFormatEntry)
        category.entries.add(makeSpacer(20))
        category.entries.add(makeSubtitleEntry("AI CAPABILITIES (SERVER)", 0xFFFF00))
        category.entries.add(makeDescriptionEntry("Controls if the AI is allowed to trigger/use these systems.", 0xFFFF00, 10))
        category.entries.add(makeDescriptionEntry("Some of them can be active without AI.", 0xFFFF00, 10))
        category.entries.add(makeSpacer(8))
        category.entries.add(useDefaultOutputEntry)
        category.entries.add(outputDialogueEntry)
        category.entries.add(outputActionsEntry)
        category.entries.add(outputFriendshipEntry)
        category.entries.add(outputWorldContextEntry)
        category.entries.add(outputGuaranteedCatchEntry)
        category.entries.add(outputMobsContextEntry)
        category.entries.add(outputQuestsEntry)
        category.entries.add(enableTraitsEntry)
        category.entries.add(makeSubtitleEntry("POKEMÓN MEMORIES (SERVER)", 0xFFFF00))
        category.entries.add(outputMemoriesEntry)
        category.entries.add(maxStoredMemoriesEntry)
        category.entries.add(maxRelevantMemoriesEntry)
        category.entries.add(makeSpacer(8))
        category.entries.add(makeSubtitleEntry("EXPERIMENTAL (SERVER)", 0xFFA500))
        category.entries.add(makeDescriptionEntry("These options may cause unexpected effects on the mod", 0xFFA500, 12))
        category.entries.add(makeDescriptionEntry("or the world, use with CAUTION.", 0xFFA500, 12))
        category.entries.add(makeSpacer(8))
        category.entries.add(characteristicsEntry)
        category.entries.add(outputApril1Entry)
        category.entries.add(outputPokemonLanguageEntry)
        category.entries.add(onlyNearbyChatEntry)
        builder.setSavingRunnable {
            ConfigHandler.save()
            ClientConfigHandler.save()
            SyncedConfig.updateLocal(
                useDefaultOutput,
                outputDialogue,
                outputActions,
                outputFriendship,
                outputMemories,
                outputApril1,
                outputQuests,
                outputPokemonLanguage,
                needsPokemonTranslator,
                outputGuaranteedCatch,
                enableKarma,
                maxStoredMemories,
                maxRelevantMemories,
                config.allowClientPersonalityEditing
            )
            val syncName = clientConfig.preferredName.ifBlank { Minecraft.getInstance().user.name }
            CobblebrainClientCommon.sendNicknameToServer?.invoke(syncName)
            CobblebrainClientCommon.sendOfflineSettingsToServer?.invoke(clientConfig.offlineMode, clientConfig.offlineTalkMode)
        }

        return builder.build()
    }
}
