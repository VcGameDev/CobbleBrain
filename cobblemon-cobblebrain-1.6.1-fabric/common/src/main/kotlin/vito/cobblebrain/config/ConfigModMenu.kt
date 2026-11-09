package vito.cobblebrain.config

import com.google.gson.Gson
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
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.sounds.SoundEvents
import vito.cobblebrain.client.CobblebrainClientCommon
import vito.cobblebrain.config.ClientConfigHandler.clientConfig
import vito.cobblebrain.config.ConfigHandler.config
import java.util.Optional

object CobblebrainConfigScreen {
    private var backupConfigJson: String? = null
    private var backupClientConfigJson: String? = null
    private var activeSessionScreen: Screen? = null
    private var isReloading: Boolean = false
    private var isSaved: Boolean = false

    fun isCobblebrainSubScreen(screen: Screen?): Boolean {
        if (screen == null) return false
        if (screen is vito.cobblebrain.client.gui.PlayStoryScreen || screen is vito.cobblebrain.client.gui.StoryEditorScreen) return true
        val titleStr = screen.title?.string ?: ""
        if (titleStr.startsWith("Action Settings:") || titleStr == Component.translatable("cobblebrain.config.action_manager.title").string) return true
        return false
    }

    fun discardSession() {
        val gson = Gson()
        backupConfigJson?.let {
            try {
                ConfigHandler.config = gson.fromJson(it, CobblebrainConfig::class.java)
            } catch (e: Exception) {
                println("Failed to restore config snapshot: ${e.message}")
            }
        }
        backupClientConfigJson?.let {
            try {
                ClientConfigHandler.clientConfig = gson.fromJson(it, CobblebrainClientConfig::class.java)
            } catch (e: Exception) {
                println("Failed to restore clientConfig snapshot: ${e.message}")
            }
        }
        activeSessionScreen = null
        backupConfigJson = null
        backupClientConfigJson = null
        isSaved = false
    }

    fun isSessionModified(): Boolean {
        if (backupConfigJson == null && backupClientConfigJson == null) return false
        val gson = Gson()
        val currentConfigJson = gson.toJson(ConfigHandler.config)
        val currentClientConfigJson = gson.toJson(ClientConfigHandler.clientConfig)
        return (backupConfigJson != null && currentConfigJson != backupConfigJson) ||
               (backupClientConfigJson != null && currentClientConfigJson != backupClientConfigJson)
    }

    fun checkSession() {
        val currentScreen = Minecraft.getInstance().screen
        val active = activeSessionScreen ?: return

        if (currentScreen === active) {
            return
        }

        if (isReloading) {
            return
        }

        if (isCobblebrainSubScreen(currentScreen)) {
            return
        }

        if (!isSaved) {
            discardSession()
        } else {
            activeSessionScreen = null
            backupConfigJson = null
            backupClientConfigJson = null
        }
    }

    private fun effectiveForceOfflineMode(): Boolean {
        val mc = Minecraft.getInstance()
        return if (mc.isLocalServer) config.forceOfflineMode else SyncedConfig.forceOfflineMode
    }

    fun renderWithListScissor(
        guiGraphics: GuiGraphics,
        y: Int,
        itemHeight: Int,
        renderAction: () -> Unit
    ) {
        val mc = Minecraft.getInstance()
        val screenH = mc.screen?.height ?: mc.window.guiScaledHeight
        val top = 30
        val bottom = screenH - 32
        if (y + itemHeight <= top || y >= bottom) {
            return
        }
        renderAction()
        guiGraphics.flush()
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
                renderWithListScissor(guiGraphics, y, itemHeight) {
                    val font = Minecraft.getInstance().font
                    val drawX = if (alignLeft) x else (x + (listWidth / 2) - (font.width(fieldName) / 2))
                    guiGraphics.drawString(font, fieldName, drawX, y + (itemHeight / 2 - font.lineHeight / 2), color, true)
    
    
                }



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
                renderWithListScissor(guiGraphics, y, itemHeight) {
                    val font = Minecraft.getInstance().font
                    val textWidth = font.width(fieldName)
                    val centerX = x + (listWidth / 2) - (textWidth / 2)
                    guiGraphics.drawString(font, fieldName, centerX, y + (itemHeight / 2 - font.lineHeight / 2), color, false)

                }



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
                renderWithListScissor(guiGraphics, y, itemHeight) {
                    button.render(guiGraphics, mouseX, mouseY, delta)
                }
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
            "teleport", "attack", "protect", "eat", "buff", "debuff_enemy", "excavate", "build", "rest", "idle", "music", "song"
        )

        val mcPlayer = Minecraft.getInstance().player
        val isRemoteServer = mcPlayer != null && !mc.isLocalServer
        val isPrivileged = mc.isLocalServer || (mcPlayer?.hasPermissions(2) == true)
        val isNonPrivilegedClientOnServer = isRemoteServer && !isPrivileged

        val serverSectionColor = if (isNonPrivilegedClientOnServer) 0xFFA500 else 0xFFFF00
        category.entries.add(makeSubtitleEntry("ACTIONS MANAGER (SERVER)", serverSectionColor))
        if (isNonPrivilegedClientOnServer) {
            category.entries.add(makeSpacer(2))
            category.entries.add(makeDescriptionEntry(Component.translatable("cobblebrain.config.server_settings_restricted").string, 0xFFA500, 14))
            category.entries.add(makeSpacer(4))
        }
        category.entries.add(makeDescriptionEntry("Configure active state and custom options for each action.", 0xAAAAAA, 14))
        category.entries.add(makeSpacer(8))

        for (key in actionKeys) {
            val actionTransName = Component.translatable("cobblebrain.action.$key").string
            val isPlayerActive = SyncedConfig.isActionActiveForPlayer(key)
            val isAiActive = SyncedConfig.isActionActiveForAI(key)
            val statusText = when {
                isPlayerActive && isAiActive -> "✔ [PLAYER & AI]"
                isPlayerActive -> "👤 [PLAYER ONLY]"
                isAiActive -> "🤖 [AI ONLY]"
                else -> "❌ [DISABLED]"
            }
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

        var enabledForPlayerVal = when (actionKey) {
            "cook" -> actionSettings.cook.enabledForPlayer
            "grow" -> actionSettings.grow.enabledForPlayer
            "repair" -> actionSettings.repair.enabledForPlayer
            "shift" -> actionSettings.shift.enabledForPlayer
            "fish" -> actionSettings.fish.enabledForPlayer
            "nightmare" -> actionSettings.nightmare.enabledForPlayer
            "light" -> actionSettings.light.enabledForPlayer
            "scout" -> actionSettings.scout.enabledForPlayer
            "teleport" -> actionSettings.teleport.enabledForPlayer
            "attack" -> actionSettings.attack.enabledForPlayer
            "protect" -> actionSettings.protect.enabledForPlayer
            "eat" -> actionSettings.eat.enabledForPlayer
            "buff" -> actionSettings.buff.enabledForPlayer
            "debuff", "debuff_enemy", "debuff enemy" -> actionSettings.debuffEnemy.enabledForPlayer
            "excavate", "demolish" -> actionSettings.excavate.enabledForPlayer
            "build" -> actionSettings.build.enabledForPlayer
            "rest", "sit" -> actionSettings.rest.enabledForPlayer
            "idle" -> actionSettings.idle.enabledForPlayer
            "music", "sing" -> actionSettings.music.enabledForPlayer
            "song" -> actionSettings.song.enabledForPlayer
            else -> true
        }

        var enabledForAiVal = when (actionKey) {
            "cook" -> actionSettings.cook.enabledForAI
            "grow" -> actionSettings.grow.enabledForAI
            "repair" -> actionSettings.repair.enabledForAI
            "shift" -> actionSettings.shift.enabledForAI
            "fish" -> actionSettings.fish.enabledForAI
            "nightmare" -> actionSettings.nightmare.enabledForAI
            "light" -> actionSettings.light.enabledForAI
            "scout" -> actionSettings.scout.enabledForAI
            "teleport" -> actionSettings.teleport.enabledForAI
            "attack" -> actionSettings.attack.enabledForAI
            "protect" -> actionSettings.protect.enabledForAI
            "eat" -> actionSettings.eat.enabledForAI
            "buff" -> actionSettings.buff.enabledForAI
            "debuff", "debuff_enemy", "debuff enemy" -> actionSettings.debuffEnemy.enabledForAI
            "excavate", "demolish" -> actionSettings.excavate.enabledForAI
            "build" -> actionSettings.build.enabledForAI
            "rest", "sit" -> actionSettings.rest.enabledForAI
            "idle" -> actionSettings.idle.enabledForAI
            "music", "sing" -> actionSettings.music.enabledForAI
            "song" -> actionSettings.song.enabledForAI
            else -> true
        }

        var spawnCarpetVal = actionSettings.rest.spawnCarpet
        var restHealAmountVal = actionSettings.rest.healAmount
        var teleportCooldownVal = actionSettings.teleport.cooldownSeconds
        var excavateDamageVal = actionSettings.excavate.exhaustionDamagePerLayer
        var excavateMinHealthPercentVal = actionSettings.excavate.minHealthPercent

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

        val defaultPlayerEnabled = when (actionKey) {
            "music", "song" -> false
            else -> true
        }

        val enabledForPlayerEntry = entryBuilder.startBooleanToggle(
            Component.translatable("cobblebrain.config.action.enabled_for_player"),
            enabledForPlayerVal
        ).setDefaultValue(true)
            .setDefaultValue(defaultPlayerEnabled)
            .setSaveConsumer { value -> enabledForPlayerVal = value }
            .setTooltip(Component.translatable("cobblebrain.config.action.enabled_for_player.tooltip"))
            .build()

        val defaultAiEnabled = when (actionKey) {
            "excavate", "demolish", "teleport", "build" -> false
            else -> true
        }

        val enabledForAiEntry = entryBuilder.startBooleanToggle(
            Component.translatable("cobblebrain.config.action.enabled_for_ai"),
            enabledForAiVal
        ).setDefaultValue(defaultAiEnabled)
            .setSaveConsumer { value -> enabledForAiVal = value }
            .setTooltip(Component.translatable("cobblebrain.config.action.enabled_for_ai.tooltip"))
            .build()

        val mcPlayer = mc.player
        val isRemoteServer = mcPlayer != null && !mc.isLocalServer
        val isPrivileged = mc.isLocalServer || (mcPlayer?.hasPermissions(2) == true)
        val isNonPrivilegedClientOnServer = isRemoteServer && !isPrivileged

        val serverSectionColor = if (isNonPrivilegedClientOnServer) 0xFFA500 else 0xFFFF00
        category.entries.add(makeSubtitleEntry("ACTION: $actionTransName (SERVER)", serverSectionColor))
        if (isNonPrivilegedClientOnServer) {
            category.entries.add(makeSpacer(2))
            category.entries.add(makeDescriptionEntry(Component.translatable("cobblebrain.config.server_settings_restricted").string, 0xFFA500, 14))
            category.entries.add(makeSpacer(4))
        }
        category.entries.add(enabledForPlayerEntry)
        category.entries.add(enabledForAiEntry)

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
            "debuff", "debuff_enemy", "debuff enemy" -> {
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
            "teleport" -> {
                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.cooldown"),
                    teleportCooldownVal
                ).setDefaultValue(30).setMin(0).setMax(600)
                    .setSaveConsumer { value -> teleportCooldownVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.cooldown.tooltip")).build())
            }
            "excavate", "demolish" -> {
                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.excavate.damage_per_layer"),
                    excavateDamageVal
                ).setDefaultValue(2).setMin(0).setMax(50)
                    .setSaveConsumer { value -> excavateDamageVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.excavate.damage_per_layer.tooltip")).build())

                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.excavate.min_health_percent"),
                    excavateMinHealthPercentVal
                ).setDefaultValue(10).setMin(5).setMax(90)
                    .setSaveConsumer { value -> excavateMinHealthPercentVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.excavate.min_health_percent.tooltip")).build())
            }
            "rest", "sit" -> {
                category.entries.add(entryBuilder.startBooleanToggle(
                    Component.translatable("cobblebrain.config.action.rest.spawn_carpet"),
                    spawnCarpetVal
                ).setDefaultValue(true)
                    .setSaveConsumer { value -> spawnCarpetVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.rest.spawn_carpet.tooltip")).build())

                category.entries.add(entryBuilder.startIntField(
                    Component.translatable("cobblebrain.config.action.rest.heal_amount"),
                    restHealAmountVal
                ).setDefaultValue(1).setMin(0).setMax(100)
                    .setSaveConsumer { value -> restHealAmountVal = value }
                    .setTooltip(Component.translatable("cobblebrain.config.action.rest.heal_amount.tooltip")).build())
            }
        }

        builder.setSavingRunnable {
            val cfg = config
            when (actionKey) {
                "cook" -> {
                    cfg.actionSettings.cook.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.cook.enabledForAI = enabledForAiVal
                    cfg.actionSettings.cook.charcoalChancePercent = charcoalChanceVal
                    cfg.actionSettings.cook.cooldownTicks = cookCooldownTicksVal
                }
                "grow" -> {
                    cfg.actionSettings.grow.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.grow.enabledForAI = enabledForAiVal
                    cfg.actionSettings.grow.growIntervalTicks = growIntervalTicksVal
                }
                "repair" -> {
                    cfg.actionSettings.repair.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.repair.enabledForAI = enabledForAiVal
                    cfg.actionSettings.repair.maxRepairPercent = maxRepairVal
                    cfg.actionSettings.repair.cooldownTicks = repairCooldownTicksVal
                }
                "shift" -> {
                    cfg.actionSettings.shift.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.shift.enabledForAI = enabledForAiVal
                    cfg.actionSettings.shift.shiftDurationSeconds = shiftDurationVal
                    cfg.actionSettings.shift.effectLevel = shiftEffectLevelVal
                    cfg.actionSettings.shift.cooldownSeconds = shiftCooldownVal
                }
                "fish" -> {
                    cfg.actionSettings.fish.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.fish.enabledForAI = enabledForAiVal
                    cfg.actionSettings.fish.maxFishRewardCount = maxFishRewardsVal
                    cfg.actionSettings.fish.luckBonus = fishLuckBonusVal
                    cfg.actionSettings.fish.allowTreasureLoot = fishAllowTreasureVal
                }
                "nightmare" -> {
                    cfg.actionSettings.nightmare.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.nightmare.enabledForAI = enabledForAiVal
                    cfg.actionSettings.nightmare.nightmareRadius = nightmareRadiusVal
                    cfg.actionSettings.nightmare.durationSeconds = nightmareDurationVal
                    cfg.actionSettings.nightmare.effectLevel = nightmareEffectLevelVal
                    cfg.actionSettings.nightmare.cooldownSeconds = nightmareCooldownVal
                }
                "light" -> {
                    cfg.actionSettings.light.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.light.enabledForAI = enabledForAiVal
                    cfg.actionSettings.light.lightIntensity = lightIntensityVal
                }
                "scout" -> {
                    cfg.actionSettings.scout.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.scout.enabledForAI = enabledForAiVal
                    cfg.actionSettings.scout.scoutRadius = scoutRadiusVal
                    cfg.actionSettings.scout.scoutFindStructures = scoutFindStructuresVal
                    cfg.actionSettings.scout.scoutHighlightMobs = scoutHighlightMobsVal
                }
                "teleport" -> {
                    cfg.actionSettings.teleport.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.teleport.enabledForAI = enabledForAiVal
                    cfg.actionSettings.teleport.cooldownSeconds = teleportCooldownVal
                }
                "attack" -> {
                    cfg.actionSettings.attack.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.attack.enabledForAI = enabledForAiVal
                    cfg.actionSettings.attack.damageMultiplier = attackDamageMultVal
                }
                "protect" -> {
                    cfg.actionSettings.protect.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.protect.enabledForAI = enabledForAiVal
                    cfg.actionSettings.protect.damageMultiplier = protectDamageMultVal
                }
                "eat" -> {
                    cfg.actionSettings.eat.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.eat.enabledForAI = enabledForAiVal
                }
                "buff" -> {
                    cfg.actionSettings.buff.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.buff.enabledForAI = enabledForAiVal
                    cfg.actionSettings.buff.durationSeconds = buffDurationVal
                    cfg.actionSettings.buff.effectLevel = buffEffectLevelVal
                }
                "debuff", "debuff_enemy", "debuff enemy" -> {
                    cfg.actionSettings.debuffEnemy.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.debuffEnemy.enabledForAI = enabledForAiVal
                    cfg.actionSettings.debuffEnemy.durationSeconds = debuffDurationVal
                    cfg.actionSettings.debuffEnemy.effectLevel = debuffEffectLevelVal
                }
                "excavate", "demolish" -> {
                    cfg.actionSettings.excavate.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.excavate.enabledForAI = enabledForAiVal
                    cfg.actionSettings.excavate.exhaustionDamagePerLayer = excavateDamageVal
                    cfg.actionSettings.excavate.minHealthPercent = excavateMinHealthPercentVal
                }
                "build" -> {
                    cfg.actionSettings.build.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.build.enabledForAI = enabledForAiVal
                }
                "rest", "sit" -> {
                    cfg.actionSettings.rest.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.rest.enabledForAI = enabledForAiVal
                    cfg.actionSettings.rest.spawnCarpet = spawnCarpetVal
                    cfg.actionSettings.rest.healAmount = restHealAmountVal
                }
                "song" -> {
                    cfg.actionSettings.song.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.song.enabledForAI = enabledForAiVal
                }
                "music", "sing" -> {
                    cfg.actionSettings.music.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.music.enabledForAI = enabledForAiVal
                }
                "idle" -> {
                    cfg.actionSettings.idle.enabledForPlayer = enabledForPlayerVal
                    cfg.actionSettings.idle.enabledForAI = enabledForAiVal
                }
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
        val isRemoteServer = mc.player != null && !mc.isLocalServer

        return if (isRemoteServer && SyncedConfig.isServerControlled) synced else local
    }

    fun applyPreset(mode: String) {
        clientConfig.presetMode = mode
        clientConfig.isCustomizedFromPreset = false
        clientConfig.optimizedMode = true
        config.optimizedMode = true

        when (mode) {
            "Smart" -> {
                clientConfig.offlineMode = false
                clientConfig.offlineTalkMode = false
                clientConfig.maxInteractionSaves = 4
                clientConfig.enableAiMemoryRetrieval = true

                config.maxStoredMemories = 90
                config.maxRelevantMemories = 4
                config.enableAiMemoryRetrieval = true
                config.lowTokenMode = false
                config.outputWorldContext = true
                config.outputMobsContext = true
                config.outputQuests = true
                config.enableTraits = true
                config.outputDialogue = true
                config.outputActions = true
                config.outputFriendship = true
                config.outputMemories = true
                config.outputGuaranteedCatch = true
            }
            "Normal" -> {
                clientConfig.offlineMode = false
                clientConfig.offlineTalkMode = false
                clientConfig.maxInteractionSaves = 3
                clientConfig.enableAiMemoryRetrieval = false

                config.maxStoredMemories = 90
                config.maxRelevantMemories = 3
                config.enableAiMemoryRetrieval = false
                config.lowTokenMode = false
                config.outputWorldContext = true
                config.outputMobsContext = true
                config.outputQuests = true
                config.enableTraits = true
                config.outputDialogue = true
                config.outputActions = true
                config.outputFriendship = true
                config.outputMemories = true
                config.outputGuaranteedCatch = true
            }
            "Light" -> {
                clientConfig.offlineMode = false
                clientConfig.offlineTalkMode = false
                clientConfig.maxInteractionSaves = 2
                clientConfig.enableAiMemoryRetrieval = false

                config.maxStoredMemories = 90
                config.maxRelevantMemories = 2
                config.enableAiMemoryRetrieval = false
                config.lowTokenMode = true
                config.outputWorldContext = true
                config.outputMobsContext = false
                config.outputQuests = true
                config.enableTraits = true
                config.outputDialogue = true
                config.outputActions = true
                config.outputFriendship = true
                config.outputMemories = false
                config.outputGuaranteedCatch = true
            }
            "Offline" -> {
                clientConfig.offlineMode = true
                clientConfig.offlineTalkMode = true
            }
            "Custom" -> {
            }
        }
    }

    fun isPresetModified(mode: String): Boolean {
        return when (mode) {
            "Smart" -> {
                config.maxStoredMemories != 90 ||
                config.maxRelevantMemories != 4 ||
                !config.enableAiMemoryRetrieval ||
                config.lowTokenMode ||
                !config.outputWorldContext ||
                !config.outputMobsContext ||
                !config.outputQuests ||
                !config.enableTraits ||
                !config.outputDialogue ||
                !config.outputActions ||
                !config.outputFriendship ||
                !config.outputMemories ||
                !config.outputGuaranteedCatch ||
                clientConfig.maxInteractionSaves != 4 ||
                clientConfig.offlineMode ||
                clientConfig.offlineTalkMode ||
                !clientConfig.enableAiMemoryRetrieval
            }
            "Normal" -> {
                config.maxStoredMemories != 90 ||
                config.maxRelevantMemories != 3 ||
                config.enableAiMemoryRetrieval ||
                config.lowTokenMode ||
                !config.outputWorldContext ||
                !config.outputMobsContext ||
                !config.outputQuests ||
                !config.enableTraits ||
                !config.outputDialogue ||
                !config.outputActions ||
                !config.outputFriendship ||
                !config.outputMemories ||
                !config.outputGuaranteedCatch ||
                clientConfig.maxInteractionSaves != 3 ||
                clientConfig.offlineMode ||
                clientConfig.offlineTalkMode ||
                clientConfig.enableAiMemoryRetrieval
            }
            "Light" -> {
                config.maxStoredMemories != 90 ||
                config.maxRelevantMemories != 2 ||
                config.enableAiMemoryRetrieval ||
                !config.lowTokenMode ||
                !config.outputWorldContext ||
                config.outputMobsContext ||
                !config.outputQuests ||
                !config.enableTraits ||
                !config.outputDialogue ||
                !config.outputActions ||
                !config.outputFriendship ||
                config.outputMemories ||
                !config.outputGuaranteedCatch ||
                clientConfig.maxInteractionSaves != 2 ||
                clientConfig.offlineMode ||
                clientConfig.offlineTalkMode ||
                clientConfig.enableAiMemoryRetrieval
            }
            "Offline" -> {
                !clientConfig.offlineMode ||
                !clientConfig.offlineTalkMode
            }
            else -> false
        }
    }

    fun create(parent: Screen?): Screen {
        if (!isReloading && backupConfigJson == null) {
            val gson = Gson()
            backupConfigJson = gson.toJson(config)
            backupClientConfigJson = gson.toJson(clientConfig)
            isSaved = false
        }

        val mc = Minecraft.getInstance()
        val mcPlayer = mc.player
        val isRemoteServer = mcPlayer != null && !mc.isLocalServer
        val isPrivileged = mc.isLocalServer || (mcPlayer?.hasPermissions(2) == true)
        val isNonPrivilegedClientOnServer = isRemoteServer && !isPrivileged

        var useDefaultOutput = getConfigValue(SyncedConfig.useDefaultOutput, true)
        var outputDialogue = getConfigValue(SyncedConfig.outputDialogue, config.outputDialogue)
        var outputActions = getConfigValue(SyncedConfig.outputActions, config.outputActions)
        var outputFriendship = getConfigValue(SyncedConfig.outputFriendship, config.outputFriendship)
        var outputMemories = getConfigValue(SyncedConfig.outputMemories, config.outputMemories)
        var outputApril1 = getConfigValue(SyncedConfig.outputApril1, config.outputApril1)
        var outputQuests = getConfigValue(SyncedConfig.outputQuests, config.outputQuests)
        var outputPokemonLanguage = getConfigValue(SyncedConfig.outputPokemonLanguage, config.outputPokemonLanguage)
        var needsPokemonTranslator = getConfigValue(SyncedConfig.needsPokemonTranslator, config.needsPokemonTranslator)
        var outputGuaranteedCatch = getConfigValue(SyncedConfig.outputGuaranteedCatch, config.outputGuaranteedCatch)
        var enableKarma = getConfigValue(SyncedConfig.enableKarma, config.enableKarma)
        var maxStoredMemories = getConfigValue(SyncedConfig.maxStoredMemories, config.maxStoredMemories)
        var maxRelevantMemories = getConfigValue(SyncedConfig.maxRelevantMemories, config.maxRelevantMemories)
        var favoriteMemorySlots = getConfigValue(SyncedConfig.favoriteMemorySlots, config.favoriteMemorySlots)
        var baseCandidateMemories = getConfigValue(SyncedConfig.baseCandidateMemories, config.baseCandidateMemories)
        var enableAiMemoryRetrieval = getConfigValue(SyncedConfig.enableAiMemoryRetrieval, config.enableAiMemoryRetrieval)
        var optimizedMode = clientConfig.optimizedMode

        val initialTemp = clientConfig.temperature
        val initialDebug = clientConfig.debugLogging
        val initialReqTimeout = clientConfig.requestTimeoutSeconds
        val initialKeyRot = clientConfig.keyRotation
        val initialModelRot = clientConfig.modelRotation
        val initialChatEndpoint = clientConfig.useChatEndpoint

        if (clientConfig.presetMode != "Custom") {
            clientConfig.isCustomizedFromPreset = isPresetModified(clientConfig.presetMode)
        }

        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Cobblebrain Config"))

        val entryBuilder = builder.entryBuilder()

        /*
            return object : AbstractConfigListEntry<Unit>(
                Component.literal("Customize AI & Profiles"),
                false
            ) {
                private var button: net.minecraft.client.gui.components.Button =
                    net.minecraft.client.gui.components.Button.builder(
                        Component.translatable("cobblebrain.button.ai_customization")
                    ) {
                        Minecraft.getInstance().player?.playSound(
                            SoundEvents.UI_BUTTON_CLICK.value(),
                            1.0f,
                            1.0f
                        )
                        preserveCurrentInputValues()
                        val parentScreen = Minecraft.getInstance().screen
                        Minecraft.getInstance().setScreen(vito.cobblebrain.client.gui.AiCustomizationScreen(parentScreen))
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
                    renderWithListScissor(guiGraphics, y, itemHeight) {
                    button.render(guiGraphics, mouseX, mouseY, delta)
                }
                }

                override fun getItemHeight(): Int = 24
            }
        }

        */
        val personalityEditorButton = object : AbstractConfigListEntry<Unit>(
            Component.literal("Open Personality Editor"),
            false
        ) {
            private var button: net.minecraft.client.gui.components.Button =
                net.minecraft.client.gui.components.Button.builder(
                    Component.translatable("cobblebrain.button.open_personality_editor")
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
                renderWithListScissor(guiGraphics, y, itemHeight) {
                    button.render(guiGraphics, mouseX, mouseY, delta)
                }
            }

            override fun getItemHeight(): Int = 24
        }

        val storyActionsButton = object : AbstractConfigListEntry<Unit>(
            Component.literal("Story Actions"),
            false
        ) {
            private var playButton: net.minecraft.client.gui.components.Button =
                net.minecraft.client.gui.components.Button.builder(
                    Component.literal("▶ Play Story")
                ) {
                    Minecraft.getInstance().player?.playSound(
                        SoundEvents.UI_BUTTON_CLICK.value(),
                        1.0f,
                        1.0f
                    )
                    val parentScreen = Minecraft.getInstance().screen
                    Minecraft.getInstance().setScreen(vito.cobblebrain.client.gui.PlayStoryScreen(parentScreen))
                }.bounds(0, 0, 128, 20).build()

            private var editorButton: net.minecraft.client.gui.components.Button =
                net.minecraft.client.gui.components.Button.builder(
                    Component.literal("Story Editor (ALPHA)")
                ) {
                    Minecraft.getInstance().player?.playSound(
                        SoundEvents.UI_BUTTON_CLICK.value(),
                        1.0f,
                        1.0f
                    )
                    val parentScreen = Minecraft.getInstance().screen
                    Minecraft.getInstance().setScreen(vito.cobblebrain.client.gui.StoryEditorScreen(parentScreen))
                }.bounds(0, 0, 128, 20).build()

            override fun getValue(): Unit? = null
            override fun getDefaultValue(): Optional<Unit> = Optional.empty()
            override fun children(): MutableList<GuiEventListener> = mutableListOf(playButton, editorButton)
            override fun narratables(): MutableList<NarratableEntry> = mutableListOf(playButton, editorButton)

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
                renderWithListScissor(guiGraphics, y, itemHeight) {
                    val hasActiveWorld = Minecraft.getInstance().level != null
                    playButton.active = hasActiveWorld
                    playButton.tooltip = if (!hasActiveWorld) {
                        net.minecraft.client.gui.components.Tooltip.create(Component.literal("You must be in an active world to play stories."))
                    } else null

                    val startX = x + (listWidth / 2) - 130
                    playButton.x = startX
                    playButton.y = y
                    playButton.render(guiGraphics, mouseX, mouseY, delta)

                    editorButton.x = startX + 132
                    editorButton.y = y
                    editorButton.render(guiGraphics, mouseX, mouseY, delta)
                }
                /* val hasActiveWorld = Minecraft.getInstance().level != null
                playButton.active = hasActiveWorld
                playButton.tooltip = if (!hasActiveWorld) {
                    net.minecraft.client.gui.components.Tooltip.create(Component.literal("You must be in an active world to play stories."))
                } else null

                val startX = x + (listWidth / 2) - 130
                playButton.x = startX
                playButton.y = y
                playButton.render(guiGraphics, mouseX, mouseY, delta)

                editorButton.x = startX + 132
                editorButton.y = y
                editorButton.render(guiGraphics, mouseX, mouseY, delta) */
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
                renderWithListScissor(guiGraphics, y, itemHeight) {
                    button.render(guiGraphics, mouseX, mouseY, delta)
                }
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
        "Tip: If the AI takes too long to respond, consider deactivating some systems in 'Customize AI & Profiles'.",
        "Tip: If the AI takes too long to respond, consider reducing 'Recent Memories Limit'.",
        "Tip: If you want to change how Pokémon speak/behave, go to 'Instruct' and add your custom instructions.",
        "Tip: If you turn 'Listen To Chat' ON, the AI will respond to every message you send in chat.",
        "Tip: You can change how often Pokémon speak by themselves in 'Spontaneous Dialogue Chance'.",
        "Tip: Turn ON 'Need Pokémon Translator' to require an EXP SHARE equipped by the player for Pokémon to speak human language.",
        "Tip: Turn ON 'Debug Logging' to get detailed logs of messages and prompts in the cobblebrain folder.",
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

        val optimizedModeEntry = entryBuilder.startBooleanToggle(
            Component.literal("Optimized Pipeline Mode").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.optimizedMode
        ).setDefaultValue(true)
            .setSaveConsumer { value ->
                clientConfig.optimizedMode = value
                optimizedMode = value
            }
            .setTooltip(Component.literal("Splits AI processing into two stages: delivers immediate in-game dialogue and actions first, then resolves all other systems (memory, traits, quests, state updates) asynchronously in the background."))
            .build()

        val showHungerEntry = entryBuilder.startBooleanToggle(
            Component.literal("Show Hunger").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.showHunger
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.showHunger = value }
            .setTooltip(Component.translatable("cobblebrain.config.show_hunger.tooltip"))
            .build()

        val manualActionNavEntry = entryBuilder.startBooleanToggle(
            Component.translatable("cobblebrain.config.manual_action_nav").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.manualActionNav
        ).setDefaultValue(false)
            .setSaveConsumer { value -> clientConfig.manualActionNav = value }
            .setTooltip(Component.translatable("cobblebrain.config.manual_action_nav.tooltip"))
            .build()

        val anchorOptions = arrayOf(
            "RIGHT_CENTER",
            "RIGHT_TOP",
            "RIGHT_BOTTOM",
            "LEFT_CENTER",
            "LEFT_TOP",
            "LEFT_BOTTOM",
            "BOTTOM_CENTER"
        )

        val actionHudAnchorEntry = entryBuilder.startSelector(
            Component.translatable("cobblebrain.config.action_hud_anchor").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            anchorOptions,
            clientConfig.actionHudAnchor
        ).setDefaultValue("RIGHT_CENTER")
            .setNameProvider { anchor ->
                Component.translatable("cobblebrain.config.action_hud_anchor." + anchor.lowercase())
            }
            .setSaveConsumer { value -> clientConfig.actionHudAnchor = value }
            .setTooltip(Component.translatable("cobblebrain.config.action_hud_anchor.tooltip"))
            .build()

        val actionHudOffsetXEntry = entryBuilder.startIntSlider(
            Component.translatable("cobblebrain.config.action_hud_offset_x").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.actionHudOffsetX,
            -300,
            300
        ).setDefaultValue(0)
            .setTextGetter { value -> Component.literal("${value}px") }
            .setSaveConsumer { value -> clientConfig.actionHudOffsetX = value }
            .setTooltip(Component.translatable("cobblebrain.config.action_hud_offset_x.tooltip"))
            .build()

        val actionHudOffsetYEntry = entryBuilder.startIntSlider(
            Component.translatable("cobblebrain.config.action_hud_offset_y").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.actionHudOffsetY,
            -300,
            300
        ).setDefaultValue(0)
            .setTextGetter { value -> Component.literal("${value}px") }
            .setSaveConsumer { value -> clientConfig.actionHudOffsetY = value }
            .setTooltip(Component.translatable("cobblebrain.config.action_hud_offset_y.tooltip")).build()

        val showActionHudControlsEntry = entryBuilder.startBooleanToggle(
            Component.translatable("cobblebrain.config.show_action_hud_controls").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            clientConfig.showActionHudControls
        ).setDefaultValue(true)
            .setSaveConsumer { value -> clientConfig.showActionHudControls = value }
            .setTooltip(Component.translatable("cobblebrain.config.show_action_hud_controls.tooltip"))
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
                    renderWithListScissor(guiGraphics, y, itemHeight) {
                    button.render(guiGraphics, mouseX, mouseY, delta)
                }
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
                    renderWithListScissor(guiGraphics, y, itemHeight) {
                    button.render(guiGraphics, mouseX, mouseY, delta)
                }
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
            config.enableTraits
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.enableTraits = value }
            .setTooltip(Component.literal("If enabled, the AI will automatically generate and evolve Traits and Quirks for your Pokémon."))
            .build()

        val maxAiLikesEntry = entryBuilder.startIntField(
            Component.translatable("cobblebrain.config.max_ai_likes").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.maxAiLikes
        ).setDefaultValue(5).setMin(0).setMax(20)
            .setSaveConsumer { value -> config.maxAiLikes = value }
            .setTooltip(Component.translatable("cobblebrain.config.max_ai_likes.tooltip"))
            .build()

        val maxAiDislikesEntry = entryBuilder.startIntField(
            Component.translatable("cobblebrain.config.max_ai_dislikes").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.maxAiDislikes
        ).setDefaultValue(5).setMin(0).setMax(20)
            .setSaveConsumer { value -> config.maxAiDislikes = value }
            .setTooltip(Component.translatable("cobblebrain.config.max_ai_dislikes.tooltip"))
            .build()

        val maxNotableBlocksEntry = entryBuilder.startIntSlider(
            Component.translatable("cobblebrain.config.max_notable_blocks").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.maxNotableBlocks,
            0,
            20
        ).setDefaultValue(10)
            .setSaveConsumer { value -> config.maxNotableBlocks = value }
            .setTooltip(Component.translatable("cobblebrain.config.max_notable_blocks.tooltip"))
            .build()

        val allowClientPersonalityEditingEntry = entryBuilder.startBooleanToggle(
            Component.literal("Allow Client Personality Editing").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(SyncedConfig.allowClientPersonalityEditing, config.allowClientPersonalityEditing)
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.allowClientPersonalityEditing = value }
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
        ).setDefaultValue(90)
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
            Component.translatable("cobblebrain.config.decrease_friendship").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.decreaseFriendship
        ).setDefaultValue(false)
            .setSaveConsumer { value -> config.decreaseFriendship = value }
            .setTooltip(Component.translatable("cobblebrain.config.decrease_friendship.tooltip"))
            .build()

        val increaseFriendshipEntry = entryBuilder.startBooleanToggle(
            Component.translatable("cobblebrain.config.increase_friendship").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.increaseFriendship
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.increaseFriendship = value }
            .setTooltip(Component.translatable("cobblebrain.config.increase_friendship.tooltip"))
            .build()

        val showFriendshipEntry = entryBuilder.startBooleanToggle(
            Component.translatable("cobblebrain.config.show_friendship").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.showFriendship
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.showFriendship = value }
            .setTooltip(Component.translatable("cobblebrain.config.show_friendship.tooltip"))
            .build()

        val hidePersonalityCreationEntry = entryBuilder.startBooleanToggle(
            Component.translatable("cobblebrain.config.hide_personality_creation").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.hidePersonalityCreation
        ).setDefaultValue(false)
            .setSaveConsumer { value -> config.hidePersonalityCreation = value }
            .setTooltip(Component.translatable("cobblebrain.config.hide_personality_creation.tooltip"))
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
                }

                override fun getItemHeight(): Int = height
            }
        }

        val isPromptCustomizationAllowed = getConfigValue(SyncedConfig.allowCustomClientPrompt, config.allowCustomClientPrompt)
        val promptItemColor = if (isPromptCustomizationAllowed) 0xFFFFFF else 0xFF5555

        val instructEntry = entryBuilder.startStrList(
            Component.literal("Instruct").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(promptItemColor))),
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
            .setSaveConsumer { value -> clientConfig.instruct = ClientConfigHandler.ensureCreativePrompt(value) }
            .setTooltip(Component.translatable("cobblebrain.config.instruct.tooltip"))
            .build()

        val playerAboutEntry = entryBuilder.startStrList(
            Component.translatable("cobblebrain.config.player_about").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(promptItemColor))),
            clientConfig.playerAbout
        ).setDefaultValue(CobblebrainClientConfig.DEFAULT_PLAYER_ABOUT)
            .setSaveConsumer { value -> clientConfig.playerAbout = ClientConfigHandler.ensurePlayerAboutHeader(value) }
            .setTooltip(Component.translatable("cobblebrain.config.player_about.tooltip"))
            .build()

        val outputFormatEntry = object : AbstractConfigListEntry<Unit>(
            Component.literal("Custom Output").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(promptItemColor))),
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
                guiGraphics.drawString(font, fieldName, x, y, promptItemColor, true)
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

        val wildPokemonCanBeHostileEntry = entryBuilder.startBooleanToggle(
            Component.literal("Wild Pokémon Hostility").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            config.wildPokemonCanBeHostile
        ).setDefaultValue(false)
            .setSaveConsumer { value -> config.wildPokemonCanBeHostile = value }
            .setTooltip(Component.literal("Allows wild Pokémon to use the hostile action (H) to target and attack players."))
            .build()

        val hostileDamageMultiplierEntry = entryBuilder.startIntSlider(
            Component.literal("Hostile Damage Multiplier").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            (config.hostileDamageMultiplier * 100).toInt().coerceIn(10, 500),
            10,
            500
        )
            .setDefaultValue(75)
            .setSaveConsumer { value ->
                config.hostileDamageMultiplier = value / 100.0f
            }
            .setTextGetter { value ->
                Component.literal(String.format("%.2fx", value / 100.0f))
            }
            .setTooltip(Component.literal("Multiplies physical damage of wild Pokémon executing H and creatures generated in Raids (0.10x to 5.00x)."))
            .build()

        val allowCustomClientPromptEntry = entryBuilder.startBooleanToggle(
            Component.translatable("cobblebrain.config.allow_custom_client_prompt").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))),
            getConfigValue(SyncedConfig.allowCustomClientPrompt, config.allowCustomClientPrompt)
        ).setDefaultValue(true)
            .setSaveConsumer { value -> config.allowCustomClientPrompt = value }
            .setTooltip(Component.translatable("cobblebrain.config.allow_custom_client_prompt.tooltip"))
            .build()

        val actionManagerButton = makeButtonEntry(Component.translatable("cobblebrain.button.action_manager")) {
            val screen = createActionManagerScreen(Minecraft.getInstance().screen)
            Minecraft.getInstance().setScreen(screen)
        }


        fun preserveCurrentInputValues() {
            apiKeyEntry.value?.let { clientConfig.apiKey = it }
            aiModelEntry.value?.let { clientConfig.aiModel = it }
            apiBaseUrlEntry.value?.let { clientConfig.apiBaseUrl = it }
            customApiProviderEntry.value?.let { clientConfig.customApiProvider = it }
            preferredNameEntry.value?.let { clientConfig.preferredName = it }
            selectedLanguageEntry.value?.let { clientConfig.selectedLanguage = it }
            playerAboutEntry.value?.let { clientConfig.playerAbout = ClientConfigHandler.ensurePlayerAboutHeader(it) }
            instructEntry.value?.let { clientConfig.instruct = ClientConfigHandler.ensureCreativePrompt(it) }
            debugLoggingEntry.value?.let { clientConfig.debugLogging = it }
            maxInteractionSavesEntry.value?.let { clientConfig.maxInteractionSaves = it }
            manualActionNavEntry.value?.let { clientConfig.manualActionNav = it }
            actionHudAnchorEntry.value?.let { clientConfig.actionHudAnchor = it }
            actionHudOffsetXEntry.value?.let { clientConfig.actionHudOffsetX = it }
            actionHudOffsetYEntry.value?.let { clientConfig.actionHudOffsetY = it }
            showActionHudControlsEntry.value?.let { clientConfig.showActionHudControls = it }
            psychicTranslationEntry.value?.let { clientConfig.psychicTranslation = it }
            showHungerEntry.value?.let { clientConfig.showHunger = it }
            temperatureEntry.value?.let { clientConfig.temperature = it / 100.0 }
            aiProviderEntry.value?.let { clientConfig.aiProvider = it }
            reasoningEffortEntry.value?.let { clientConfig.reasoningEffort = it }
            requestTimeoutEntry.value?.let { clientConfig.requestTimeoutSeconds = it }
            keyRotationEntry.value?.let { clientConfig.keyRotation = it }
            keyRotationTriggerEntry.value?.let { clientConfig.keyRotationTrigger = it }
            modelRotationEntry.value?.let { clientConfig.modelRotation = it }
            modelRotationTriggerEntry.value?.let { clientConfig.modelRotationTrigger = it }
            outputPokemonLanguageEntry.value?.let { outputPokemonLanguage = it; config.outputPokemonLanguage = it }
        }

        fun reloadScreen(preserveScroll: Boolean = true) {
            isReloading = true
            val currentScreen = Minecraft.getInstance().screen
            val savedScroll = if (preserveScroll) {
                (currentScreen as? me.shedaniel.clothconfig2.gui.ClothConfigScreen)?.listWidget?.getScroll() ?: 0.0
            } else 0.0
            Minecraft.getInstance().tell {
                try {
                    me.shedaniel.clothconfig2.api.ScissorsHandler.INSTANCE.clearScissors()
                    val newScreen = create(parent)
                    activeSessionScreen = newScreen
                    Minecraft.getInstance().setScreen(newScreen)
                    if (savedScroll > 0.0) {
                        (newScreen as? me.shedaniel.clothconfig2.gui.ClothConfigScreen)?.listWidget?.let { lw ->
                            lw.capYPosition(savedScroll)
                            lw.scrollTo(savedScroll, false)
                        }
                    }
                } finally {
                    isReloading = false
                }
            }
        }

        val isOffline = clientConfig.presetMode.equals("Offline", ignoreCase = true)
        val isFullCustom = clientConfig.presetMode.equals("Custom", ignoreCase = true)
        val showAdvancedAi = isFullCustom || (!isOffline && clientConfig.showAdvancedAiOptions)

        val presetSelectorEntry = object : AbstractConfigListEntry<Unit>(
            Component.translatable("cobblebrain.config.preset.title"),
            false
        ) {
            private val smartButton: net.minecraft.client.gui.components.Button
            private val normalButton: net.minecraft.client.gui.components.Button
            private val lightButton: net.minecraft.client.gui.components.Button
            private val offlineButton: net.minecraft.client.gui.components.Button
            private val customButton: net.minecraft.client.gui.components.Button

            init {
                fun makeModeLabel(mode: String, key: String): Component {
                    val isCurrent = clientConfig.presetMode.equals(mode, ignoreCase = true)
                    val base = Component.translatable(key).string
                    val label = if (isCurrent) "✔ $base" else base
                    val color = if (isCurrent) 0x55FF55 else 0xFFFFFF
                    return Component.literal(label).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)))
                }

                fun onModeClick(targetMode: String) {
                    Minecraft.getInstance().player?.playSound(
                        SoundEvents.UI_BUTTON_CLICK.value(),
                        1.0f,
                        1.0f
                    )
                    preserveCurrentInputValues()
                    applyPreset(targetMode)
                    reloadScreen(preserveScroll = true)
                }

                smartButton = net.minecraft.client.gui.components.Button.builder(
                    makeModeLabel("Smart", "cobblebrain.config.preset.smart")
                ) { onModeClick("Smart") }
                    .tooltip(Tooltip.create(Component.translatable("cobblebrain.config.preset.desc.smart")))
                    .bounds(0, 0, 84, 20).build()

                normalButton = net.minecraft.client.gui.components.Button.builder(
                    makeModeLabel("Normal", "cobblebrain.config.preset.normal")
                ) { onModeClick("Normal") }
                    .tooltip(Tooltip.create(Component.translatable("cobblebrain.config.preset.desc.normal")))
                    .bounds(0, 0, 84, 20).build()

                lightButton = net.minecraft.client.gui.components.Button.builder(
                    makeModeLabel("Light", "cobblebrain.config.preset.light")
                ) { onModeClick("Light") }
                    .tooltip(Tooltip.create(Component.translatable("cobblebrain.config.preset.desc.light")))
                    .bounds(0, 0, 84, 20).build()

                offlineButton = net.minecraft.client.gui.components.Button.builder(
                    makeModeLabel("Offline", "cobblebrain.config.preset.offline")
                ) { onModeClick("Offline") }
                    .tooltip(Tooltip.create(Component.translatable("cobblebrain.config.preset.desc.offline")))
                    .bounds(0, 0, 80, 20).build()

                customButton = net.minecraft.client.gui.components.Button.builder(
                    makeModeLabel("Custom", "cobblebrain.config.preset.custom")
                ) { onModeClick("Custom") }
                    .tooltip(Tooltip.create(Component.translatable("cobblebrain.config.preset.desc.custom")))
                    .bounds(0, 0, 176, 20).build()
            }

            override fun getValue(): Unit? = null
            override fun getDefaultValue(): Optional<Unit> = Optional.empty()
            override fun isEdited(): Boolean = isSessionModified()
            override fun children(): MutableList<GuiEventListener> =
                mutableListOf(smartButton, normalButton, lightButton, offlineButton, customButton)
            override fun narratables(): MutableList<NarratableEntry> =
                mutableListOf(smartButton, normalButton, lightButton, offlineButton, customButton)

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
                val startX = x + (listWidth / 2) - 130

                renderWithListScissor(guiGraphics, y, itemHeight) {
                    val startX = x + (listWidth / 2) - 130

                    smartButton.x = startX
                    smartButton.y = y
                    smartButton.render(guiGraphics, mouseX, mouseY, delta)

                    normalButton.x = startX + 88
                    normalButton.y = y
                    normalButton.render(guiGraphics, mouseX, mouseY, delta)

                    lightButton.x = startX + 176
                    lightButton.y = y
                    lightButton.render(guiGraphics, mouseX, mouseY, delta)

                    offlineButton.x = startX
                    offlineButton.y = y + 24
                    offlineButton.render(guiGraphics, mouseX, mouseY, delta)

                    customButton.x = startX + 84
                    customButton.y = y + 24
                    customButton.render(guiGraphics, mouseX, mouseY, delta)
                }
            } /* smartButton.x = startX
                smartButton.y = y
                smartButton.render(guiGraphics, mouseX, mouseY, delta)

                normalButton.x = startX + 88
                normalButton.y = y
                normalButton.render(guiGraphics, mouseX, mouseY, delta)

                lightButton.x = startX + 176
                lightButton.y = y
                lightButton.render(guiGraphics, mouseX, mouseY, delta)

                offlineButton.x = startX
                offlineButton.y = y + 24
                offlineButton.render(guiGraphics, mouseX, mouseY, delta)

                customButton.x = startX + 84
                customButton.y = y + 24
                customButton.render(guiGraphics, mouseX, mouseY, delta)
            }

            */ override fun getItemHeight(): Int = 48
        }

        val currentModeKey = when (clientConfig.presetMode) {
            "Smart" -> "cobblebrain.config.preset.smart"
            "Light" -> "cobblebrain.config.preset.light"
            "Offline" -> "cobblebrain.config.preset.offline"
            "Custom" -> "cobblebrain.config.preset.custom"
            else -> "cobblebrain.config.preset.normal"
        }
        val currentModeName = Component.translatable(currentModeKey).string
        val statusSuffix = if (clientConfig.isCustomizedFromPreset && !isFullCustom) {
            Component.translatable("cobblebrain.config.preset.customized_suffix").string
        } else ""
        val activeStatusText = Component.translatable("cobblebrain.config.preset.active_status", "$currentModeName$statusSuffix").string

        val presetStatusEntry = makeSubtitleEntry(activeStatusText, 0x55FF55, bold = true)

        val presetDescText = if (clientConfig.isCustomizedFromPreset && !isFullCustom) {
            Component.translatable("cobblebrain.config.preset.customized_hint", currentModeName).string
        } else {
            val descKey = when (clientConfig.presetMode) {
                "Smart" -> "cobblebrain.config.preset.desc.smart"
                "Light" -> "cobblebrain.config.preset.desc.light"
                "Offline" -> "cobblebrain.config.preset.desc.offline"
                "Custom" -> "cobblebrain.config.preset.desc.custom"
                else -> "cobblebrain.config.preset.desc.normal"
            }
            Component.translatable(descKey).string
        }

        fun addWrappedDescription(text: String, color: Int = 0xAAAAAA) {
            val words = text.split(" ")
            val lines = mutableListOf<String>()
            var currentLine = ""
            for (word in words) {
                if (currentLine.length + word.length < 65) {
                    currentLine += "$word "
                } else {
                    lines.add(currentLine.trim())
                    currentLine = "$word "
                }
            }
            if (currentLine.isNotBlank()) lines.add(currentLine.trim())
            for (l in lines) {
                category.addEntry(makeDescriptionEntry(l, color, 14))
            }
        }

        fun addServerSubtitle(title: String, defaultColor: Int = 0xFFFF00) {
            val color = if (isNonPrivilegedClientOnServer) 0xFFA500 else defaultColor
            category.entries.add(makeSubtitleEntry(title, color))
            if (isNonPrivilegedClientOnServer) {
                category.entries.add(makeSpacer(2))
                addWrappedDescription(
                    Component.translatable("cobblebrain.config.server_settings_restricted").string,
                    0xFFA500
                )
                category.entries.add(makeSpacer(6))
            }
        }

        val advancedAiToggleButton = object : AbstractConfigListEntry<Unit>(
            Component.translatable(if (clientConfig.showAdvancedAiOptions) "cobblebrain.config.preset.hide_advanced_ai" else "cobblebrain.config.preset.show_advanced_ai"),
            false
        ) {
            private val button: net.minecraft.client.gui.components.Button =
                net.minecraft.client.gui.components.Button.builder(
                    Component.translatable(
                        if (clientConfig.showAdvancedAiOptions) "cobblebrain.config.preset.hide_advanced_ai"
                        else "cobblebrain.config.preset.show_advanced_ai"
                    ).withStyle(
                        Style.EMPTY.withColor(
                            TextColor.fromRgb(if (clientConfig.showAdvancedAiOptions) 0xAAAAAA else 0x55FFFF)
                        )
                    )
                ) {
                    Minecraft.getInstance().player?.playSound(
                        SoundEvents.UI_BUTTON_CLICK.value(),
                        1.0f,
                        1.0f
                    )
                    preserveCurrentInputValues()
                    clientConfig.showAdvancedAiOptions = !clientConfig.showAdvancedAiOptions
                    reloadScreen(preserveScroll = true)
                }.bounds(0, 0, 260, 20).build()

            override fun getValue(): Unit? = null
            override fun getDefaultValue(): Optional<Unit> = Optional.empty()
            override fun isEdited(): Boolean = isSessionModified()
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
                renderWithListScissor(guiGraphics, y, itemHeight) {
                    button.render(guiGraphics, mouseX, mouseY, delta)
                }
            }

            override fun getItemHeight(): Int = 24
        }

        fun makeCustomizeAiButton(): AbstractConfigListEntry<Unit> {
            return object : AbstractConfigListEntry<Unit>(
                Component.literal("Customize AI & Profiles"),
                false
            ) {
                private var button: net.minecraft.client.gui.components.Button =
                    net.minecraft.client.gui.components.Button.builder(
                        Component.translatable("cobblebrain.button.ai_customization")
                    ) {
                        Minecraft.getInstance().player?.playSound(
                            SoundEvents.UI_BUTTON_CLICK.value(),
                            1.0f,
                            1.0f
                        )
                        preserveCurrentInputValues()
                        val parentScreen = Minecraft.getInstance().screen
                        Minecraft.getInstance().setScreen(vito.cobblebrain.client.gui.AiCustomizationScreen(parentScreen))
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
                    renderWithListScissor(guiGraphics, y, itemHeight) {
                    button.render(guiGraphics, mouseX, mouseY, delta)
                }
                }

                override fun getItemHeight(): Int = 24
            }
        }

        category.entries.add(makeSubtitleEntry(Component.translatable("cobblebrain.config.preset.title").string, 0xFFFF00))
        category.entries.add(presetSelectorEntry)
        category.entries.add(presetStatusEntry)
        addWrappedDescription(presetDescText, 0xAAAAAA)
        category.entries.add(makeSpacer(6))

        category.entries.add(makeCustomizeAiButton())
        category.entries.add(personalityEditorButton)
        category.entries.add(storyActionsButton)

        category.entries.add(makeSpacer(10))

        if (isOffline) {
            category.entries.add(makeSubtitleEntry("CLIENT GAMEPLAY", 0xFFFF00))
            category.entries.add(manualActionNavEntry)
            category.entries.add(actionHudAnchorEntry)
            category.entries.add(actionHudOffsetXEntry)
            category.entries.add(actionHudOffsetYEntry)
            category.entries.add(showActionHudControlsEntry)
            category.entries.add(sttEntry)
            category.entries.add(hidePersonalityCreationEntry)
            category.entries.add(offlineModeEntry)
            category.entries.add(offlineTalkModeEntry)
            /*
            category.entries.add(makeSubtitleEntry("CLIENT GAMEPLAY", 0xFFFF00))
            category.entries.add(manualActionNavEntry)
            category.entries.add(actionHudAnchorEntry)
            category.entries.add(actionHudOffsetXEntry)
            category.entries.add(actionHudOffsetYEntry)
            category.entries.add(showActionHudControlsEntry)
            category.entries.add(sttEntry)
            category.entries.add(hidePersonalityCreationEntry)
            category.entries.add(offlineModeEntry)
            category.entries.add(offlineTalkModeEntry)

            */ addServerSubtitle("GAME AND INTERACTIONS (SERVER)")
            category.entries.add(needsPokemonTranslatorEntry)
            category.entries.add(dialogueInChatEntry)
            category.entries.add(chatbubblesEntry)
            category.entries.add(showHungerEntry)
            category.entries.add(spontaneousDialogueChanceEntry)
            category.entries.add(wildPokemonTalkChanceEntry)
            category.entries.add(wildQuestChanceEntry)
            category.entries.add(scheduleRaidEntry)
            category.entries.add(forceOfflineModeEntry)
            category.entries.add(disableWelcomeMessageEntry)
            category.entries.add(allowClientPersonalityEditingEntry)
            category.entries.add(allowCustomClientPromptEntry)
            category.entries.add(allowPokemonPVPEntry)
            category.entries.add(allowPokemonPVEEntry)
            category.entries.add(enableKarmaEntry)
            category.entries.add(dialogueOnDamageEntry)
            category.entries.add(dialogueOnBattleEntry)
            category.entries.add(decreaseFriendshipEntry)
            category.entries.add(increaseFriendshipEntry)
            category.entries.add(showFriendshipEntry)
            /*
            category.entries.add(maxAiLikesEntry)
            category.entries.add(maxAiDislikesEntry)
            */
            category.entries.add(maxNotableBlocksEntry)

            addServerSubtitle("ACTIONS MANAGER (SERVER)")
            category.entries.add(actionManagerButton)
            category.entries.add(makeSpacer(24))
        } else if (!isFullCustom) {
            category.entries.add(makeSubtitleEntry("AI CONFIGURATION", 0xFFFF00))
            category.entries.add(apiKeyEntry)
            category.entries.add(aiModelEntry)
            category.entries.add(apiBaseUrlEntry)
            category.entries.add(useChatEndpointEntry)
            category.entries.add(customApiProviderEntry)
            category.entries.add(debugLoggingEntry)
            category.entries.add(maxInteractionSavesEntry)
            category.entries.add(maxAiLikesEntry)
            category.entries.add(maxAiDislikesEntry)

            category.entries.add(makeSpacer(6))
            category.entries.add(advancedAiToggleButton)

            if (showAdvancedAi) {
                category.entries.add(makeSpacer(6))
                category.entries.add(makeSubtitleEntry("ADVANCED AI SETTINGS", 0x55FFFF, bold = false))
                category.entries.add(temperatureEntry)
                category.entries.add(aiProviderEntry)
                category.entries.add(reasoningEffortEntry)
                category.entries.add(requestTimeoutEntry)
                category.entries.add(keyRotationEntry)
                category.entries.add(keyRotationTriggerEntry)
                category.entries.add(modelRotationEntry)
                category.entries.add(modelRotationTriggerEntry)
            }

            category.entries.add(makeSpacer(10))
            category.entries.add(makeSubtitleEntry("CLIENT GAMEPLAY", 0xFFFF00))
            category.entries.add(preferredNameEntry)
            category.entries.add(selectedLanguageEntry)
            category.entries.add(manualActionNavEntry)
            category.entries.add(actionHudAnchorEntry)
            category.entries.add(actionHudOffsetXEntry)
            category.entries.add(actionHudOffsetYEntry)
            category.entries.add(showActionHudControlsEntry)
            category.entries.add(psychicTranslationEntry)
            category.entries.add(sttEntry)
            category.entries.add(hidePersonalityCreationEntry)

            /* PROMPT AND OUTPUT removed
            category.entries.add(makeSpacer(10))
            category.entries.add(makeSubtitleEntry("PROMPT AND OUTPUT", promptSectionColor))
            if (!isPromptCustomizationAllowed) {
                addWrappedDescription(
                    Component.translatable("cobblebrain.config.prompt_customization_blocked").string,
                    0xFFA500
                )
                category.entries.add(makeSpacer(6))
            }
            category.entries.add(makeCustomizeAiButton())

            category.entries.add(outputPokemonLanguageEntry)
            category.entries.add(allowClientPersonalityEditingEntry)
            category.entries.add(allowCustomClientPromptEntry)
            category.entries.add(allowCustomClientPromptEntry)
            category.entries.add(makeSpacer(15))

            */ addServerSubtitle("GAME AND INTERACTIONS (SERVER)")
            category.entries.add(needsPokemonTranslatorEntry)
            category.entries.add(allowClientPersonalityEditingEntry)
            category.entries.add(allowCustomClientPromptEntry)
            category.entries.add(listenToChatEntry)
            category.entries.add(dialogueInChatEntry)
            category.entries.add(chatbubblesEntry)
            category.entries.add(showHungerEntry)
            category.entries.add(spontaneousDialogueChanceEntry)
            category.entries.add(wildPokemonTalkChanceEntry)
            category.entries.add(wildQuestChanceEntry)
            category.entries.add(scheduleRaidEntry)
            category.entries.add(forceOfflineModeEntry)
            category.entries.add(disableWelcomeMessageEntry)
            category.entries.add(allowPokemonPVPEntry)
            category.entries.add(allowPokemonPVEEntry)
            category.entries.add(enableKarmaEntry)
            category.entries.add(dialogueOnDamageEntry)
            category.entries.add(dialogueOnBattleEntry)
            category.entries.add(decreaseFriendshipEntry)
            category.entries.add(increaseFriendshipEntry)
            category.entries.add(showFriendshipEntry)
            /*
            category.entries.add(maxAiLikesEntry)
            category.entries.add(maxAiDislikesEntry)
            */
            category.entries.add(maxNotableBlocksEntry)

            addServerSubtitle("ACTIONS MANAGER (SERVER)")
            category.entries.add(actionManagerButton)
            category.entries.add(makeSpacer(24))
        } else {
            category.entries.add(makeSubtitleEntry("AI CONFIGURATION", 0xFFFF00))
            category.entries.add(apiKeyEntry)
            category.entries.add(aiModelEntry)
            category.entries.add(apiBaseUrlEntry)
            category.entries.add(useChatEndpointEntry)
            category.entries.add(customApiProviderEntry)
            category.entries.add(temperatureEntry)
            category.entries.add(aiProviderEntry)
            category.entries.add(reasoningEffortEntry)
            category.entries.add(requestTimeoutEntry)
            category.entries.add(debugLoggingEntry)
            category.entries.add(keyRotationEntry)
            category.entries.add(keyRotationTriggerEntry)
            category.entries.add(modelRotationEntry)
            category.entries.add(modelRotationTriggerEntry)
            category.entries.add(maxInteractionSavesEntry)
            category.entries.add(maxAiLikesEntry)
            category.entries.add(maxAiDislikesEntry)
            category.entries.add(offlineModeEntry)
            category.entries.add(offlineTalkModeEntry)
            category.entries.add(optimizedModeEntry)

            category.entries.add(makeSpacer(10))
            category.entries.add(makeSubtitleEntry("CLIENT GAMEPLAY", 0xFFFF00))
            category.entries.add(preferredNameEntry)
            category.entries.add(selectedLanguageEntry)
            category.entries.add(psychicTranslationEntry)
            category.entries.add(sttEntry)
            category.entries.add(hidePersonalityCreationEntry)
            category.entries.add(manualActionNavEntry)
            category.entries.add(actionHudAnchorEntry)
            category.entries.add(actionHudOffsetXEntry)
            category.entries.add(actionHudOffsetYEntry)
            category.entries.add(showActionHudControlsEntry)

            /* PROMPT AND OUTPUT removed
            category.entries.add(makeSpacer(10))
            category.entries.add(makeSubtitleEntry("PROMPT AND OUTPUT", promptSectionColor))
            if (!isPromptCustomizationAllowed) {
                addWrappedDescription(
                    Component.translatable("cobblebrain.config.prompt_customization_blocked").string,
                    0xFFA500
                )
                category.entries.add(makeSpacer(6))
            }
            category.entries.add(makeCustomizeAiButton())

            category.entries.add(outputPokemonLanguageEntry)
            category.entries.add(allowClientPersonalityEditingEntry)
            category.entries.add(allowCustomClientPromptEntry)
            category.entries.add(allowCustomClientPromptEntry)
            category.entries.add(makeSpacer(6))

            category.entries.add(makeSpacer(15))

            */ addServerSubtitle("GAME AND INTERACTIONS (SERVER)")
            category.entries.add(needsPokemonTranslatorEntry)
            category.entries.add(allowClientPersonalityEditingEntry)
            category.entries.add(allowCustomClientPromptEntry)
            category.entries.add(listenToChatEntry)
            category.entries.add(dialogueInChatEntry)
            category.entries.add(chatbubblesEntry)
            category.entries.add(showHungerEntry)
            category.entries.add(spontaneousDialogueChanceEntry)
            category.entries.add(wildPokemonTalkChanceEntry)
            category.entries.add(wildQuestChanceEntry)
            category.entries.add(lowTokenModeEntry)
            category.entries.add(scheduleRaidEntry)
            category.entries.add(forceOfflineModeEntry)
            category.entries.add(disableWelcomeMessageEntry)
            category.entries.add(allowPokemonPVPEntry)
            category.entries.add(allowPokemonPVEEntry)
            category.entries.add(enableKarmaEntry)
            category.entries.add(dialogueOnDamageEntry)
            category.entries.add(dialogueOnBattleEntry)
            category.entries.add(decreaseFriendshipEntry)
            category.entries.add(increaseFriendshipEntry)
            category.entries.add(showFriendshipEntry)
            /*
            category.entries.add(maxAiLikesEntry)
            category.entries.add(maxAiDislikesEntry)
            */
            category.entries.add(maxNotableBlocksEntry)

            addServerSubtitle("ACTIONS MANAGER (SERVER)")
            category.entries.add(actionManagerButton)
            category.entries.add(makeSpacer(15))

            /* AI CAPABILITIES removed - handled in AiCustomizationScreen
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
            category.entries.add(maxAiLikesEntry)
            */

            addServerSubtitle("POKÉMON MEMORIES (SERVER)")
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

            addServerSubtitle("EXPERIMENTAL (SERVER)", 0xFFA500)
            category.entries.add(makeDescriptionEntry("These options may cause unexpected effects on the mod", 0xFFA500, 12))
            category.entries.add(makeDescriptionEntry("or the world, use with CAUTION.", 0xFFA500, 12))
            category.entries.add(makeSpacer(8))
            category.entries.add(characteristicsEntry)
            category.entries.add(wildPokemonCanBeHostileEntry)
            category.entries.add(hostileDamageMultiplierEntry)
            category.entries.add(outputApril1Entry)
            category.entries.add(onlyNearbyChatEntry)
            category.entries.add(makeSpacer(24))
        }

        category.entries.add(makeSpacer(10))
        category.entries.add(reportBugsButton)
        category.entries.add(makeSpacer(20))

        val initialForceOfflineMode = config.forceOfflineMode
        builder.setSavingRunnable {
            isSaved = true
            val forceOfflineChanged = config.forceOfflineMode != initialForceOfflineMode
            val mc = Minecraft.getInstance()
            val isRemoteServer = mc.player != null && !mc.isLocalServer

            if (!isRemoteServer) {
                // AI capabilities are managed per-profile in AiCustomizationScreen



                config.outputMemories = outputMemories
                config.outputApril1 = outputApril1

                config.outputPokemonLanguage = outputPokemonLanguage
                config.needsPokemonTranslator = needsPokemonTranslator

                config.enableKarma = enableKarma
                config.maxStoredMemories = maxStoredMemories
                config.maxRelevantMemories = maxRelevantMemories
                config.favoriteMemorySlots = favoriteMemorySlots
                config.baseCandidateMemories = baseCandidateMemories
                config.enableAiMemoryRetrieval = enableAiMemoryRetrieval
                config.optimizedMode = clientConfig.optimizedMode
            }

            if (clientConfig.presetMode != "Custom") {
                clientConfig.isCustomizedFromPreset = isPresetModified(clientConfig.presetMode)
            }

            if (!isRemoteServer) {
                ConfigHandler.save()
            }
            ClientConfigHandler.save()
            if (!isRemoteServer) {
                SyncedConfig.updateLocal(
                    config.useDefaultOutput,
                    config.outputDialogue,
                    config.outputActions,
                    config.outputFriendship,
                    outputMemories,
                    outputApril1,
                    config.outputQuests,
                    outputPokemonLanguage,
                    needsPokemonTranslator,
                    config.outputGuaranteedCatch,
                    enableKarma,
                    maxStoredMemories,
                    maxRelevantMemories,
                    favoriteMemorySlots,
                    baseCandidateMemories,
                    config.allowClientPersonalityEditing,
                    enableAiMemoryRetrieval,
                    clientConfig.optimizedMode,
                    config.allowCustomClientPrompt
                )
            }
            val syncName = clientConfig.preferredName.ifBlank { Minecraft.getInstance().user.name }
            CobblebrainClientCommon.sendNicknameToServer?.invoke(syncName)
            CobblebrainClientCommon.sendOfflineSettingsToServer?.invoke(
                clientConfig.offlineMode || effectiveForceOfflineMode(),
                clientConfig.offlineTalkMode
            )

            activeSessionScreen = null
            backupConfigJson = null
            backupClientConfigJson = null

            if (forceOfflineChanged) {
                val currentScreen = Minecraft.getInstance().screen
                Minecraft.getInstance().setScreen(vito.cobblebrain.client.ForceOfflineNoticeScreen(currentScreen))
            }
        }

        val builtScreen = builder.build()
        if (!isReloading) {
            activeSessionScreen = builtScreen
        }
        return builtScreen
    }
}
