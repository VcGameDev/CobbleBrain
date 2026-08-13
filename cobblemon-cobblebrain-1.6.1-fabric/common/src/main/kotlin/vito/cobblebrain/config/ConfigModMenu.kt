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
    private fun effectiveForceOfflineMode(): Boolean {
        val mc = Minecraft.getInstance()
        return if (mc.isLocalServer) config.forceOfflineMode else SyncedConfig.forceOfflineMode
    }

    fun makeSubtitleEntry(text: String, color: Int = 0xFFFF00, bold: Boolean = true, alignLeft: Boolean = false): AbstractConfigListEntry<Unit> {
        return object : AbstractConfigListEntry<Unit>(
            Component.literal(text).withStyle(
                Style.EMPTY.withColor(TextColor.fromRgb(color)).withBold(bold)
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
                val drawX = if (alignLeft) x else (x + (listWidth / 2) - (font.width(fieldName) / 2))
                guiGraphics.drawString(font, fieldName, drawX, y + (itemHeight / 2 - font.lineHeight / 2), color, true)
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

    fun makeButtonEntry(text: Component, onClick: () -> Unit): AbstractConfigListEntry<Unit> {
        return object : AbstractConfigListEntry<Unit>(text, false) {
            private val button: net.minecraft.client.gui.components.Button =
                net.minecraft.client.gui.components.Button.builder(text) {
                    Minecraft.getInstance().player?.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f)
                    onClick()
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
    }

    fun createActionManagerScreen(parentScreen: Screen?): Screen {
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) {
            val builder = ConfigBuilder.create()
                .setParentScreen(parentScreen)
                .setTitle(Component.translatable("cobblebrain.config.action_manager.title"))

            val category = builder.getOrCreateCategory(Component.literal("Actions"))
            category.entries.add(makeSubtitleEntry("ACTIONS MANAGER (SERVER)", 0xFF5555))
            category.entries.add(makeSpacer(10))
            category.entries.add(makeDescriptionEntry(Component.translatable("cobblebrain.config.action_manager.no_world_line1").string, 0xFF5555, 14))
            category.entries.add(makeDescriptionEntry(Component.translatable("cobblebrain.config.action_manager.no_world_line2").string, 0xAAAAAA, 12))
            return builder.build()
        }

        val builder = ConfigBuilder.create()
            .setParentScreen(parentScreen)
            .setTitle(Component.translatable("cobblebrain.config.action_manager.title"))

        val category = builder.getOrCreateCategory(Component.literal("Actions"))
        val actionKeys = listOf(
            "cook", "grow", "repair", "shift", "fish", "nightmare", "light", "scout",
            "teleport", "attack", "protect", "eat", "buff", "debuff_enemy", "excavate", "prospect", "rest", "idle"
        )

        category.entries.add(makeSubtitleEntry("ACTIONS MANAGER (SERVER)", 0xFFFF00))
        category.entries.add(makeDescriptionEntry("Configure active state and custom options for each action.", 0xAAAAAA, 14))
        category.entries.add(makeSpacer(8))

        for (key in actionKeys) {
            val actionTransName = Component.translatable("cobblebrain.action.$key").string
            val isActive = SyncedConfig.isActionActive(key)
            val statusText = if (isActive) "✔ [ACTIVE]" else "❌ [DISABLED]"
            val buttonText = Component.literal("$statusText $actionTransName")

            val entry = makeButtonEntry(buttonText) {
                val detailScreen = createActionDetailScreen(parentScreen, key)
                Minecraft.getInstance().setScreen(detailScreen)
            }
            category.entries.add(entry)
        }

        return builder.build()
    }

    fun createActionDetailScreen(mainConfigParent: Screen?, actionKey: String): Screen {
        val actionTransName = Component.translatable("cobblebrain.action.$actionKey").string
        val builder = ConfigBuilder.create()
            .setParentScreen(createActionManagerScreen(mainConfigParent))
            .setTitle(Component.literal("Action Settings: $actionTransName"))

        val entryBuilder = builder.entryBuilder()
        val category = builder.getOrCreateCategory(Component.literal("Action Options"))

        val mc = Minecraft.getInstance()
        val actionSettings = if (mc.isLocalServer) config.actionSettings else SyncedConfig.actionSettings

        var activeVal = when (actionKey) {
            "cook" -> actionSettings.cook.active
            "grow" -> actionSettings.grow.active
            "repair" -> actionSettings.repair.active
            "shift" -> actionSettings.shift.active
            "fish" -> actionSettings.fish.active
            "nightmare" -> actionSettings.nightmare.active
            "light" -> actionSettings.light.active
            "scout" -> actionSettings.scout.active
            "teleport" -> actionSettings.teleport.active
            "attack" -> actionSettings.attack.active
            "protect" -> actionSettings.protect.active
            "eat" -> actionSettings.eat.active
            "buff" -> actionSettings.buff.active
            "debuff_enemy" -> actionSettings.debuffEnemy.active
            "excavate", "demolish" -> actionSettings.excavate.active
            "prospect" -> actionSettings.prospect.active
            "rest", "sit" -> actionSettings.rest.active
            "idle" -> actionSettings.idle.active
            else -> true
        }

        var maxFishRewardsVal = actionSettings.fish.maxFishRewardCount
        var fishLuckBonusVal = actionSettings.fish.luckBonus
        var fishAllowTreasureVal = actionSettings.fish.allowTreasureLoot

        var lightIntensityVal = actionSettings.light.lightIntensity

        var charcoalChanceVal = actionSettings.cook.charcoalChancePercent
        var cookCooldownTicksVal = actionSettings.cook.cooldownTicks

        var maxRepairVal = actionSettings.repair.maxRepairPercent
        var repairCooldownTicksVal = actionSettings.repair.cooldownTicks

        var scoutRadiusVal = actionSettings.scout.scoutRadius
        var scoutFindStructuresVal = actionSettings.scout.scoutFindStructures
        var scoutHighlightMobsVal = actionSettings.scout.scoutHighlightMobs

        var nightmareRadiusVal = actionSettings.nightmare.nightmareRadius
        var nightmareDurationVal = actionSettings.nightmare.durationSeconds
        var nightmareEffectLevelVal = actionSettings.nightmare.effectLevel
        var nightmareCooldownVal = actionSettings.nightmare.cooldownSeconds

        var shiftDurationVal = actionSettings.shift.shiftDurationSeconds
        var shiftEffectLevelVal = actionSettings.shift.effectLevel
        var shiftCooldownVal = actionSettings.shift.cooldownSeconds

        var growIntervalTicksVal = actionSettings.grow.growIntervalTicks

        var attackDamageMultVal = actionSettings.attack.damageMultiplier
        var protectDamageMultVal = actionSettings.protect.damageMultiplier

        var buffDurationVal = actionSettings.buff.durationSeconds
        var buffEffectLevelVal = actionSettings.buff.effectLevel

        var debuffDurationVal = actionSettings.debuffEnemy.durationSeconds
        var debuffEffectLevelVal = actionSettings.debuffEnemy.effectLevel

        val activeEntry = entryBuilder.startBooleanToggle(
            Component.translatable("cobblebrain.config.action.active"),
            activeVal
        ).setDefaultValue(true)
            .setSaveConsumer { value -> activeVal = value }
            .setTooltip(Component.translatable("cobblebrain.config.action.active.tooltip"))
            .build()

        category.entries.add(makeSubtitleEntry("ACTION: $actionTransName (SERVER)", 0xFFFF00))
        category.entries.add(activeEntry)

        when (actionKey) {
            "fish" -> {
                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.fish.max_rewards"),
                    maxFishRewardsVal
                ).setDefaultValue(5).setMin(1).setMax(64)
                    .setSaveConsumer { value -> maxFishRewardsVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.fish.max_rewards.tooltip")).build())

                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.fish.luck_bonus"),
                    fishLuckBonusVal
                ).setDefaultValue(0).setMin(0).setMax(10)
                    .setSaveConsumer { value -> fishLuckBonusVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.fish.luck_bonus.tooltip")).build())

                category.entries.add(entryBuilder.startBooleanToggle(
                    Component.translatable("cobblebrain.config.action.fish.allow_treasure"),
                    fishAllowTreasureVal
                ).setDefaultValue(true)
                    .setSaveConsumer { value -> fishAllowTreasureVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.fish.allow_treasure.tooltip")).build())
            }
            "light" -> {
                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.light.intensity"),
                    lightIntensityVal
                ).setDefaultValue(15).setMin(1).setMax(15)
                    .setSaveConsumer { value -> lightIntensityVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.light.intensity.tooltip")).build())
            }
            "cook" -> {
                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.cook.charcoal_chance"),
                    charcoalChanceVal
                ).setDefaultValue(5).setMin(0).setMax(100)
                    .setSaveConsumer { value -> charcoalChanceVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.cook.charcoal_chance.tooltip")).build())

                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.cook.cooldown_ticks"),
                    cookCooldownTicksVal
                ).setDefaultValue(22).setMin(1).setMax(1200)
                    .setSaveConsumer { value -> cookCooldownTicksVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.cook.cooldown_ticks.tooltip")).build())
            }
            "repair" -> {
                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.repair.max_percent"),
                    maxRepairVal
                ).setDefaultValue(100).setMin(1).setMax(100)
                    .setSaveConsumer { value -> maxRepairVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.repair.max_percent.tooltip")).build())

                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.repair.cooldown_ticks"),
                    repairCooldownTicksVal
                ).setDefaultValue(40).setMin(1).setMax(1200)
                    .setSaveConsumer { value -> repairCooldownTicksVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.repair.cooldown_ticks.tooltip")).build())
            }
            "scout" -> {
                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.scout.radius"),
                    scoutRadiusVal
                ).setDefaultValue(50).setMin(5).setMax(200)
                    .setSaveConsumer { value -> scoutRadiusVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.scout.radius.tooltip")).build())

                category.entries.add(entryBuilder.startBooleanToggle(
                    Component.translatable("cobblebrain.config.action.scout.find_structures"),
                    scoutFindStructuresVal
                ).setDefaultValue(true)
                    .setSaveConsumer { value -> scoutFindStructuresVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.scout.find_structures.tooltip")).build())

                category.entries.add(entryBuilder.startBooleanToggle(
                    Component.translatable("cobblebrain.config.action.scout.highlight_mobs"),
                    scoutHighlightMobsVal
                ).setDefaultValue(true)
                    .setSaveConsumer { value -> scoutHighlightMobsVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.scout.highlight_mobs.tooltip")).build())
            }
            "nightmare" -> {
                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.nightmare.radius"),
                    nightmareRadiusVal
                ).setDefaultValue(10).setMin(1).setMax(50)
                    .setSaveConsumer { value -> nightmareRadiusVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.nightmare.radius.tooltip")).build())

                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.duration"),
                    nightmareDurationVal
                ).setDefaultValue(8).setMin(1).setMax(120)
                    .setSaveConsumer { value -> nightmareDurationVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.duration.tooltip")).build())

                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.effect_level"),
                    nightmareEffectLevelVal
                ).setDefaultValue(1).setMin(1).setMax(5)
                    .setSaveConsumer { value -> nightmareEffectLevelVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.effect_level.tooltip")).build())

                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.cooldown"),
                    nightmareCooldownVal
                ).setDefaultValue(120).setMin(1).setMax(600)
                    .setSaveConsumer { value -> nightmareCooldownVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.cooldown.tooltip")).build())
            }
            "shift" -> {
                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.shift.duration"),
                    shiftDurationVal
                ).setDefaultValue(30).setMin(5).setMax(300)
                    .setSaveConsumer { value -> shiftDurationVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.shift.duration.tooltip")).build())

                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.effect_level"),
                    shiftEffectLevelVal
                ).setDefaultValue(1).setMin(1).setMax(5)
                    .setSaveConsumer { value -> shiftEffectLevelVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.effect_level.tooltip")).build())

                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.cooldown"),
                    shiftCooldownVal
                ).setDefaultValue(240).setMin(1).setMax(600)
                    .setSaveConsumer { value -> shiftCooldownVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.cooldown.tooltip")).build())
            }
            "grow" -> {
                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.grow.interval_ticks"),
                    growIntervalTicksVal
                ).setDefaultValue(20).setMin(1).setMax(200)
                    .setSaveConsumer { value -> growIntervalTicksVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.grow.interval_ticks.tooltip")).build())
            }
            "attack" -> {
                category.entries.add(entryBuilder.startDoubleField(
                    Component.translatable("cobblebrain.config.action.damage_multiplier"),
                    attackDamageMultVal
                ).setDefaultValue(1.0).setMin(0.1).setMax(10.0)
                    .setSaveConsumer { value -> attackDamageMultVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.damage_multiplier.tooltip")).build())
            }
            "protect" -> {
                category.entries.add(entryBuilder.startDoubleField(
                    Component.translatable("cobblebrain.config.action.damage_multiplier"),
                    protectDamageMultVal
                ).setDefaultValue(1.0).setMin(0.1).setMax(10.0)
                    .setSaveConsumer { value -> protectDamageMultVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.damage_multiplier.tooltip")).build())
            }
            "buff" -> {
                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.duration"),
                    buffDurationVal
                ).setDefaultValue(30).setMin(1).setMax(300)
                    .setSaveConsumer { value -> buffDurationVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.duration.tooltip")).build())

                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.effect_level"),
                    buffEffectLevelVal
                ).setDefaultValue(1).setMin(1).setMax(5)
                    .setSaveConsumer { value -> buffEffectLevelVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.effect_level.tooltip")).build())
            }
            "debuff_enemy" -> {
                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.duration"),
                    debuffDurationVal
                ).setDefaultValue(15).setMin(1).setMax(300)
                    .setSaveConsumer { value -> debuffDurationVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.duration.tooltip")).build())

                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.effect_level"),
                    debuffEffectLevelVal
                ).setDefaultValue(1).setMin(1).setMax(5)
                    .setSaveConsumer { value -> debuffEffectLevelVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.effect_level.tooltip")).build())
            }
        }

        builder.setSavingRunnable {
            val cfg = config
            when (actionKey) {
                "cook" -> {
                    cfg.actionSettings.cook.active = activeVal
                    cfg.actionSettings.cook.charcoalChancePercent = charcoalChanceVal
                    cfg.actionSettings.cook.cooldownTicks = cookCooldownTicksVal
                }
                "grow" -> {
                    cfg.actionSettings.grow.active = activeVal
                    cfg.actionSettings.grow.growIntervalTicks = growIntervalTicksVal
                }
                "repair" -> {
                    cfg.actionSettings.repair.active = activeVal
                    cfg.actionSettings.repair.maxRepairPercent = maxRepairVal
                    cfg.actionSettings.repair.cooldownTicks = repairCooldownTicksVal
                }
                "shift" -> {
                    cfg.actionSettings.shift.active = activeVal
                    cfg.actionSettings.shift.shiftDurationSeconds = shiftDurationVal
                    cfg.actionSettings.shift.effectLevel = shiftEffectLevelVal
                    cfg.actionSettings.shift.cooldownSeconds = shiftCooldownVal
                }
                "fish" -> {
                    cfg.actionSettings.fish.active = activeVal
                    cfg.actionSettings.fish.maxFishRewardCount = maxFishRewardsVal
                    cfg.actionSettings.fish.luckBonus = fishLuckBonusVal
                    cfg.actionSettings.fish.allowTreasureLoot = fishAllowTreasureVal
                }
                "nightmare" -> {
                    cfg.actionSettings.nightmare.active = activeVal
                    cfg.actionSettings.nightmare.nightmareRadius = nightmareRadiusVal
                    cfg.actionSettings.nightmare.durationSeconds = nightmareDurationVal
                    cfg.actionSettings.nightmare.effectLevel = nightmareEffectLevelVal
                    cfg.actionSettings.nightmare.cooldownSeconds = nightmareCooldownVal
                }
                "light" -> {
                    cfg.actionSettings.light.active = activeVal
                    cfg.actionSettings.light.lightIntensity = lightIntensityVal
                }
                "scout" -> {
                    cfg.actionSettings.scout.active = activeVal
                    cfg.actionSettings.scout.scoutRadius = scoutRadiusVal
                    cfg.actionSettings.scout.scoutFindStructures = scoutFindStructuresVal
                    cfg.actionSettings.scout.scoutHighlightMobs = scoutHighlightMobsVal
                }
                "teleport" -> cfg.actionSettings.teleport.active = activeVal
                "attack" -> {
                    cfg.actionSettings.attack.active = activeVal
                    cfg.actionSettings.attack.damageMultiplier = attackDamageMultVal
                }
                "protect" -> {
                    cfg.actionSettings.protect.active = activeVal
                    cfg.actionSettings.protect.damageMultiplier = protectDamageMultVal
                }
                "eat" -> cfg.actionSettings.eat.active = activeVal
                "buff" -> {
                    cfg.actionSettings.buff.active = activeVal
                    cfg.actionSettings.buff.durationSeconds = buffDurationVal
                    cfg.actionSettings.buff.effectLevel = buffEffectLevelVal
                }
                "debuff_enemy" -> {
                    cfg.actionSettings.debuffEnemy.active = activeVal
                    cfg.actionSettings.debuffEnemy.durationSeconds = debuffDurationVal
                    cfg.actionSettings.debuffEnemy.effectLevel = debuffEffectLevelVal
                }
                "excavate", "demolish" -> cfg.actionSettings.excavate.active = activeVal
                "prospect" -> cfg.actionSettings.prospect.active = activeVal
                "rest", "sit" -> cfg.actionSettings.rest.active = activeVal
                "idle" -> cfg.actionSettings.idle.active = activeVal
            }
            ConfigHandler.save()
        }

        return builder.build()
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
        var favoriteMemorySlots = 5
        var baseCandidateMemories = 10
        var enableAiMemoryRetrieval = false

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
                        favoriteMemorySlots,
                        baseCandidateMemories,
                        config.allowClientPersonalityEditing,
                        enableAiMemoryRetrieval
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

        val storyEditorButton = object : AbstractConfigListEntry<Unit>(
            Component.literal("Open Story Editor"),
            false
        ) {
            private var button: net.minecraft.client.gui.components.Button =
                net.minecraft.client.gui.components.Button.builder(
                    Component.literal("Open Story Editor")
                ) {
                    Minecraft.getInstance().player?.playSound(
                        SoundEvents.UI_BUTTON_CLICK.value(),
                        1.0f,
                        1.0f
                    )
                    val parentScreen = Minecraft.getInstance().screen
                    Minecraft.getInstance().setScreen(vito.cobblebrain.client.gui.StoryEditorScreen(parentScreen))
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

        val reportBugsButton = object : AbstractConfigListEntry<Unit>(
            Component.translatable("cobblebrain.button.report_bugs"),
            false
        ) {
            private var button: net.minecraft.client.gui.components.Button =
                net.minecraft.client.gui.components.Button.builder(
                    Component.translatable("cobblebrain.button.report_bugs")
                ) {
                    Minecraft.getInstance().player?.playSound(
                        SoundEvents.UI_BUTTON_CLICK.value(),
                        1.0f,
                        1.0f
                    )
                    net.minecraft.Util.getPlatform().openUri("https://docs.google.com/forms/d/e/1FAIpQLSddvxnQP-E2gUZYEmuqquldpSFkkhLScfkcNrCm-ZeMpjIRuw/viewform?usp=dialog")
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

        val customApiProviderEntry = entryBuilder.startStrField(
            Component.literal("Custom API Provider").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.customApiProvider
        ).setDefaultValue("player2")
            .setSaveConsumer { value -> clientConfig.customApiProvider = value }
            .setTooltip(Component.translatable("cobblebrain.config.custom_api_provider.tooltip"))
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

        val showHungerEntry = entryBuilder.startBooleanToggle(
            Component.literal("Show Hunger").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.showHunger
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.showHunger = value }
            .setTooltip(Component.translatable("cobblebrain.config.show_hunger.tooltip"))
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

        val isForcedByServer = effectiveForceOfflineMode()
        val offlineModeEntry: AbstractConfigListEntry<*> = if (isForcedByServer) {
            object : AbstractConfigListEntry<Boolean>(
                Component.literal("Offline Mode").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFF00))),
                false
            ) {
                private var button: net.minecraft.client.gui.components.Button =
                    net.minecraft.client.gui.components.Button.builder(
                        Component.literal("Yes").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x55FF55)))
                    ) {
                        val currentScreen = Minecraft.getInstance().screen
                        Minecraft.getInstance().setScreen(vito.cobblebrain.client.OfflineForcedNoticeScreen(currentScreen))
                    }.bounds(0, 0, 112, 20).build()

                override fun getValue(): Boolean = true
                override fun getDefaultValue(): Optional<Boolean> = Optional.of(false)
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
                    val labelText = Component.literal("Offline Mode").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFF00)))
                    guiGraphics.drawString(Minecraft.getInstance().font, labelText, x, y + 6, 0xFFFF00)
                    button.x = x + listWidth - 150
                    button.y = y
                    button.render(guiGraphics, mouseX, mouseY, delta)
                }

                fun getTooltip(): Optional<Array<Component>> {
                    return Optional.of(arrayOf(Component.translatable("cobblebrain.config.offline_mode.forced_tooltip")))
                }

                override fun getItemHeight(): Int = 24
            }
        } else {
            entryBuilder.startBooleanToggle(
                Component.literal("Offline Mode").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                clientConfig.offlineMode
            ).setDefaultValue(false)
                .setSaveConsumer { value -> clientConfig.offlineMode = value }
                .setTooltip(Component.translatable("cobblebrain.config.offline_mode.tooltip"))
                .build()
        }

        val offlineTalkModeEntry = entryBuilder.startBooleanToggle(
            Component.literal("Offline Talk Mode (v1.0)").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
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

        val sttEntry: AbstractConfigListEntry<*> = if (!CobblebrainClientCommon.isMcmtiInstalled()) {
            object : AbstractConfigListEntry<Boolean>(
                Component.literal("Speech-to-Text (STT)").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFF00))),
                false
            ) {
                private var button: net.minecraft.client.gui.components.Button =
                    net.minecraft.client.gui.components.Button.builder(
                        Component.literal("Disabled").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5555)))
                    ) {
                        val currentScreen = Minecraft.getInstance().screen
                        Minecraft.getInstance().setScreen(vito.cobblebrain.client.McmtiNotInstalledNoticeScreen(currentScreen))
                    }.bounds(0, 0, 112, 20).build()

                override fun getValue(): Boolean = false
                override fun getDefaultValue(): Optional<Boolean> = Optional.of(false)
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
                    val labelText = Component.literal("Speech-to-Text (STT)").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFF00)))
                    guiGraphics.drawString(Minecraft.getInstance().font, labelText, x, y + 6, 0xFFFF00)
                    button.x = x + listWidth - 150
                    button.y = y
                    button.render(guiGraphics, mouseX, mouseY, delta)
                }

                fun getTooltip(): Optional<Array<Component>> {
                    return Optional.of(arrayOf(Component.translatable("cobblebrain.config.enable_stt.tooltip")))
                }

                override fun getItemHeight(): Int = 24
            }
        } else {
            entryBuilder.startBooleanToggle(
                Component.literal("Speech-to-Text (STT)").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
                clientConfig.enableStt
            ).setDefaultValue(false)
                .setSaveConsumer { value -> clientConfig.enableStt = value }
                .setTooltip(Component.translatable("cobblebrain.config.enable_stt.tooltip"))
                .build()
        }

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

        val forceOfflineModeEntry = entryBuilder.startBooleanToggle(
            Component.literal("Force Offline Mode").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.forceOfflineMode
        ).setDefaultValue(false)
            .setSaveConsumer { value -> config.forceOfflineMode = value }
            .setTooltip(Component.translatable("cobblebrain.config.force_offline_mode.tooltip"))
            .build()

        val disableWelcomeMessageEntry = entryBuilder.startBooleanToggle(
            Component.literal("Disable Welcome Message").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.disableWelcomeMessage
        ).setDefaultValue(false)
            .setSaveConsumer { value -> config.disableWelcomeMessage = value }
            .setTooltip(Component.translatable("cobblebrain.config.disable_welcome_message.tooltip"))
            .build()

        val characteristicsEntry = entryBuilder.startStrList(
            Component.literal("[OUTDATED] Characteristics").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x888888))),
            config.characteristics
        ).setDefaultValue(listOf("TestPokemon: He likes to sing, he fell off a bike once, he is from a farm"))
            .setSaveConsumer { value -> config.characteristics = value }
            .setTooltip(Component.translatable("cobblebrain.config.characteristics.tooltip"))
            .build()

        val enableTraitsEntry = entryBuilder.startBooleanToggle(
            Component.literal("Enable Trait Creation").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
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

        val favoriteMemorySlotsEntry = entryBuilder.startIntField(
            Component.literal("Favorite Memory Slots").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.favoriteMemorySlots,
                config.favoriteMemorySlots
            )
        ).setDefaultValue(5)
            .setSaveConsumer { value -> favoriteMemorySlots = value }
            .setTooltip(Component.translatable("cobblebrain.config.favorite_memory_slots.tooltip"))
            .build()

        val baseCandidateMemoriesEntry = entryBuilder.startIntField(
            Component.literal("Base Candidate Memories").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.baseCandidateMemories,
                config.baseCandidateMemories
            )
        ).setDefaultValue(10)
            .setSaveConsumer { value -> baseCandidateMemories = value }
            .setTooltip(Component.translatable("cobblebrain.config.base_candidate_memories.tooltip"))
            .build()

        val enableAiMemoryRetrievalEntry = entryBuilder.startBooleanToggle(
            Component.literal("AI-Driven Memory Retrieval").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(
                SyncedConfig.enableAiMemoryRetrieval,
                config.enableAiMemoryRetrieval
            )
        ).setDefaultValue(false)
            .setSaveConsumer { value ->
                enableAiMemoryRetrieval = value
                clientConfig.enableAiMemoryRetrieval = value
            }
            .setTooltip(Component.translatable("cobblebrain.config.enable_ai_memory_retrieval.tooltip"))
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

        val actionManagerButton = makeButtonEntry(Component.translatable("cobblebrain.button.action_manager")) {
            val screen = createActionManagerScreen(Minecraft.getInstance().screen)
            Minecraft.getInstance().setScreen(screen)
        }

        category.entries.add(recommendedPromptButton)
        category.entries.add(personalityEditorButton)
        category.entries.add(storyEditorButton)
        category.entries.add(reportBugsButton)
        category.entries.add(makeSubtitleEntry("AI CONFIGURATION (CLIENT)", 0xFFFF00))
        category.entries.add(apiBaseUrlEntry)
        category.entries.add(useChatEndpointEntry)
        category.entries.add(customApiProviderEntry)
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
        category.entries.add(sttEntry)
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
        category.entries.add(forceOfflineModeEntry)
        category.entries.add(disableWelcomeMessageEntry)
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
        category.entries.add(showHungerEntry)
        category.entries.add(instructEntry)
        category.entries.add(makeSpacer(10))
        category.entries.add(outputFormatEntry)
        category.entries.add(makeSpacer(15))
        category.entries.add(makeSubtitleEntry("ACTIONS MANAGER (SERVER)", 0xFFFF00))
        category.entries.add(actionManagerButton)
        category.entries.add(makeSpacer(15))
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
        category.entries.add(makeSubtitleEntry("POKÉMON MEMORIES (SERVER)", 0xFFFF00))
        category.entries.add(outputMemoriesEntry)
        category.entries.add(maxStoredMemoriesEntry)
        category.entries.add(makeSpacer(6))
        category.entries.add(makeSubtitleEntry("Local Retrieval", 0x55FFFF, bold = false, alignLeft = false))
        category.entries.add(maxRelevantMemoriesEntry)
        category.entries.add(favoriteMemorySlotsEntry)
        category.entries.add(makeSpacer(6))
        category.entries.add(makeSubtitleEntry("AI-Driven Retrieval", 0x55FFFF, bold = false, alignLeft = false))
        category.entries.add(enableAiMemoryRetrievalEntry)
        category.entries.add(baseCandidateMemoriesEntry)
        category.entries.add(makeSpacer(8))
        category.entries.add(makeSubtitleEntry("EXPERIMENTAL (SERVER)", 0xFFA500))
        category.entries.add(makeDescriptionEntry("These options may cause unexpected effects on the mod", 0xFFA500, 12))
        category.entries.add(makeDescriptionEntry("or the world, use with CAUTION.", 0xFFA500, 12))
        category.entries.add(makeSpacer(8))
        category.entries.add(characteristicsEntry)
        category.entries.add(outputApril1Entry)
        category.entries.add(outputPokemonLanguageEntry)
        category.entries.add(onlyNearbyChatEntry)
        val initialForceOfflineMode = config.forceOfflineMode
        builder.setSavingRunnable {
            val forceOfflineChanged = config.forceOfflineMode != initialForceOfflineMode
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
                favoriteMemorySlots,
                baseCandidateMemories,
                config.allowClientPersonalityEditing,
                enableAiMemoryRetrieval
            )
            val syncName = clientConfig.preferredName.ifBlank { Minecraft.getInstance().user.name }
            CobblebrainClientCommon.sendNicknameToServer?.invoke(syncName)
            CobblebrainClientCommon.sendOfflineSettingsToServer?.invoke(
                clientConfig.offlineMode || effectiveForceOfflineMode(),
                clientConfig.offlineTalkMode
            )

            if (forceOfflineChanged) {
                val currentScreen = Minecraft.getInstance().screen
                Minecraft.getInstance().setScreen(vito.cobblebrain.client.ForceOfflineNoticeScreen(currentScreen))
            }
        }

        return builder.build()
    }
}
