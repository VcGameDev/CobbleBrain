package vito.cobblebrain.client.gui

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import vito.cobblebrain.config.AiProfile
import vito.cobblebrain.config.AiProfileManager
import vito.cobblebrain.config.ClientConfigHandler
import vito.cobblebrain.config.CobblebrainClientConfig
import vito.cobblebrain.config.ConfigHandler
import vito.cobblebrain.config.SyncedConfig
import java.util.UUID

class AiCustomizationScreen(
    private val parentScreen: Screen?
) : Screen(Component.translatable("cobblebrain.screen.ai_customization.title")) {

    private val workingProfiles = mutableListOf<AiProfile>()
    private var activeProfileId: String = "default"
    private var selectedProfileIndex: Int = 0
    private var currentTab: Int = 0 // 0 = Prompt & Lore, 1 = Capacidades & Formato
    private var sidebarScrollOffset: Int = 0
    private var capabilitiesScrollOffset: Int = 0
    private var saveFeedbackTicks: Int = 0

    // Scroll dragging state
    private val capManualScroll = mutableMapOf<Int, Int>()
    private var draggingHScrollCapIdx: Int = -1
    private var isDraggingCapVScroll: Boolean = false
    private var isDraggingSidebarVScroll: Boolean = false

    // Collapsible boxes state (closed by default upon entering)
    private var playerAboutOpen: Boolean = false
    private var instructOpen: Boolean = false

    // Widgets - Sidebar
    private lateinit var newProfileBtn: Button
    private lateinit var duplicateProfileBtn: Button
    private lateinit var setActiveBtn: Button
    private lateinit var deleteProfileBtn: Button

    // Widgets - Header Row (Profile Name & Trainer Name side by side)
    private lateinit var profileNameEdit: EditBox
    private lateinit var preferredNameEdit: EditBox

    // Widgets - Tab Bar (in a separate rectangle)
    private lateinit var tabPromptLoreBtn: Button
    private lateinit var tabCapabilitiesBtn: Button

    // Widgets - Tab 0: Prompt & Lore (Collapsible sections)
    private lateinit var togglePlayerAboutBtn: Button
    private lateinit var playerAboutBox: CustomMultiLineEditBox
    private lateinit var toggleInstructBtn: Button
    private lateinit var instructBox: CustomMultiLineEditBox

    // Widgets - Tab 1: Header / Mode
    private lateinit var outputStructureBtn: Button
    private lateinit var resetOutputBtn: Button
    private lateinit var outputFormatBox: CustomMultiLineEditBox

    // Capability items definition
    private data class CapabilityItem(
        val titleKey: String,
        val descKey: String,
        val getter: (AiProfile) -> Boolean,
        val setter: (AiProfile, Boolean) -> Unit
    ) {
        val title: String get() = Component.translatable(titleKey).string
        val description: String get() = Component.translatable(descKey).string
    }

    private val capabilities = listOf(
        CapabilityItem("cobblebrain.cap.dialogue", "cobblebrain.cap.dialogue.desc",
            { it.outputDialogue }, { p, v -> p.outputDialogue = v }),
        CapabilityItem("cobblebrain.cap.actions", "cobblebrain.cap.actions.desc",
            { it.outputActions }, { p, v -> p.outputActions = v }),
        CapabilityItem("cobblebrain.cap.friendship", "cobblebrain.cap.friendship.desc",
            { it.outputFriendship }, { p, v -> p.outputFriendship = v }),
        CapabilityItem("cobblebrain.cap.memories", "cobblebrain.cap.memories.desc",
            { it.outputMemories }, { p, v -> p.outputMemories = v }),
        CapabilityItem("cobblebrain.cap.world_context", "cobblebrain.cap.world_context.desc",
            { it.outputWorldContext }, { p, v -> p.outputWorldContext = v }),
        CapabilityItem("cobblebrain.cap.mobs_context", "cobblebrain.cap.mobs_context.desc",
            { it.outputMobsContext }, { p, v -> p.outputMobsContext = v }),
        CapabilityItem("cobblebrain.cap.quests", "cobblebrain.cap.quests.desc",
            { it.outputQuests }, { p, v -> p.outputQuests = v }),
        CapabilityItem("cobblebrain.cap.traits", "cobblebrain.cap.traits.desc",
            { it.enableTraits }, { p, v -> p.enableTraits = v }),
        CapabilityItem("cobblebrain.cap.guaranteed_catch", "cobblebrain.cap.guaranteed_catch.desc",
            { it.outputGuaranteedCatch }, { p, v -> p.outputGuaranteedCatch = v }),
        CapabilityItem("cobblebrain.cap.show_hunger", "cobblebrain.cap.show_hunger.desc",
            { it.showHunger }, { p, v -> p.showHunger = v }),
        CapabilityItem("cobblebrain.cap.psychic_translation", "cobblebrain.cap.psychic_translation.desc",
            { it.psychicTranslation }, { p, v -> p.psychicTranslation = v }),
        CapabilityItem("cobblebrain.cap.pokemon_language", "cobblebrain.cap.pokemon_language.desc",
            { it.outputPokemonLanguage }, { p, v -> p.outputPokemonLanguage = v })
    )

    // Widgets - Bottom Bar
    private lateinit var saveBtn: Button
    private lateinit var closeBtn: Button

    init {
        workingProfiles.clear()
        if (AiProfileManager.profiles.isEmpty()) {
            AiProfileManager.load()
        }
        AiProfileManager.profiles.forEach {
            workingProfiles.add(it.deepCopy())
        }
        if (workingProfiles.isEmpty()) {
            val defaultName = if (isPromptBlocked()) "Server 1" else "Default"
            val defaultId = if (isPromptBlocked()) "server" else "default"
            workingProfiles.add(AiProfile(id = defaultId, name = defaultName, useDefaultOutput = true))
        } else if (isPromptBlocked() && workingProfiles.none { isServerProfile(it) }) {
            val serverP = AiProfile(
                id = "server",
                name = "Server 1",
                useDefaultOutput = SyncedConfig.useDefaultOutput,
                outputDialogue = SyncedConfig.outputDialogue,
                outputActions = SyncedConfig.outputActions,
                outputFriendship = SyncedConfig.outputFriendship,
                outputMemories = SyncedConfig.outputMemories,
                outputWorldContext = ConfigHandler.config.outputWorldContext,
                outputMobsContext = ConfigHandler.config.outputMobsContext,
                outputQuests = SyncedConfig.outputQuests,
                enableTraits = ConfigHandler.config.enableTraits,
                outputGuaranteedCatch = SyncedConfig.outputGuaranteedCatch,
                outputPokemonLanguage = SyncedConfig.outputPokemonLanguage
            )
            workingProfiles.add(0, serverP)
            activeProfileId = "server"
        }
        activeProfileId = if (workingProfiles.any { it.id == activeProfileId }) {
            activeProfileId
        } else if (workingProfiles.any { it.id == AiProfileManager.activeProfileId }) {
            AiProfileManager.activeProfileId
        } else {
            workingProfiles.find { it.id == "default" }?.id ?: workingProfiles.first().id
        }
        selectedProfileIndex = workingProfiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0)
    }

    // Geometry Helpers
    private fun getSidebarX(): Int = 10
    private fun getSidebarW(): Int = 125
    private fun getMainX(): Int = getSidebarX() + getSidebarW() + 8
    private fun getMainW(): Int = width - getMainX() - 10
    private fun getTopOffset(): Int = 28
    private fun getBottomBarHeight(): Int = 30

    private fun getHeaderY(): Int = getTopOffset() + 5
    private fun getTabsCardY(): Int = getHeaderY() + 22
    private fun getTabsCardH(): Int = 24
    private fun getSettingsCardY(): Int = getTabsCardY() + getTabsCardH() + 4
    private fun getSettingsCardH(): Int = height - getBottomBarHeight() - 4 - getSettingsCardY()

    private fun getContentX(): Int = getMainX() + 6
    private fun getContentY(): Int = getSettingsCardY() + 6
    private fun getContentW(): Int = getMainW() - 12
    private fun getContentH(): Int = getSettingsCardH() - 12

    private fun getSidebarListTop(): Int = getTopOffset() + 5 + 20
    private fun getSidebarListBottom(): Int = height - getBottomBarHeight() - 5 - 42

    private fun getCapabilityListY(): Int = getContentY() + 23
    private fun getCapabilityListBottom(): Int = getContentY() + getContentH() - 4

    override fun init() {
        if (::profileNameEdit.isInitialized) {
            saveCurrentValuesToSelectedProfile()
        }
        super.init()
        clearWidgets()

        // Reset collapsible boxes to closed when entering
        playerAboutOpen = false
        instructOpen = false
        capabilitiesScrollOffset = 0
        draggingHScrollCapIdx = -1
        isDraggingCapVScroll = false
        isDraggingSidebarVScroll = false

        val sidebarX = getSidebarX()
        val sidebarY = getTopOffset() + 5
        val sidebarW = getSidebarW()
        val sidebarH = height - sidebarY - getBottomBarHeight() - 5

        val mainX = getMainX()
        val mainW = getMainW()

        // --- SIDEBAR BOTTOM BUTTONS ---
        val btnRow1Y = height - getBottomBarHeight() - 5 - 39
        val btnRow2Y = height - getBottomBarHeight() - 5 - 19
        val sideBtnW = (sidebarW - 6) / 2

        newProfileBtn = Button.builder(Component.translatable("cobblebrain.screen.ai_customization.new_profile")) {
            saveCurrentValuesToSelectedProfile()
            val isBlocked = isPromptBlocked()
            val newNum = if (isBlocked) workingProfiles.count { isServerProfile(it) } + 1 else workingProfiles.size + 1
            val fallback = if (isBlocked) "Server" else Component.translatable("cobblebrain.screen.ai_customization.default_profile_name").string
            val newP = AiProfile(
                id = UUID.randomUUID().toString().take(8),
                name = "$fallback $newNum",
                useDefaultOutput = true
            )
            workingProfiles.add(newP)
            selectedProfileIndex = workingProfiles.size - 1
            ensureProfileVisible(selectedProfileIndex)
            loadSelectedProfileToWidgets()
            playClickSound()
        }.tooltip(Tooltip.create(Component.translatable("cobblebrain.screen.ai_customization.new_profile.tooltip"))).bounds(sidebarX + 2, btnRow1Y, sideBtnW, 18).build()

        duplicateProfileBtn = Button.builder(Component.translatable("cobblebrain.screen.ai_customization.duplicate_profile")) {
            saveCurrentValuesToSelectedProfile()
            if (selectedProfileIndex in workingProfiles.indices) {
                val copy = workingProfiles[selectedProfileIndex].deepCopy()
                val suffix = Component.translatable("cobblebrain.screen.ai_customization.copy_suffix").string
                copy.name = "${copy.name}$suffix"
                copy.id = UUID.randomUUID().toString().take(8)
                workingProfiles.add(selectedProfileIndex + 1, copy)
                selectedProfileIndex++
                ensureProfileVisible(selectedProfileIndex)
                loadSelectedProfileToWidgets()
                playClickSound()
            }
        }.tooltip(Tooltip.create(Component.translatable("cobblebrain.screen.ai_customization.duplicate_profile.tooltip"))).bounds(sidebarX + 2 + sideBtnW + 2, btnRow1Y, sideBtnW, 18).build()

        setActiveBtn = Button.builder(Component.translatable("cobblebrain.screen.ai_customization.set_active")) {
            if (isPromptBlocked()) return@builder
            if (selectedProfileIndex in workingProfiles.indices) {
                activeProfileId = workingProfiles[selectedProfileIndex].id
                playClickSound()
            }
        }.tooltip(Tooltip.create(Component.translatable("cobblebrain.screen.ai_customization.set_active.tooltip"))).bounds(sidebarX + 2, btnRow2Y, sideBtnW, 18).build()
        setActiveBtn.active = !isPromptBlocked()

        deleteProfileBtn = Button.builder(Component.translatable("cobblebrain.screen.ai_customization.delete_profile")) {
            if (workingProfiles.size > 1 && selectedProfileIndex in workingProfiles.indices) {
                val removed = workingProfiles.removeAt(selectedProfileIndex)
                if (activeProfileId == removed.id) {
                    activeProfileId = workingProfiles.first().id
                }
                selectedProfileIndex = selectedProfileIndex.coerceIn(0, workingProfiles.size - 1)
                ensureProfileVisible(selectedProfileIndex)
                loadSelectedProfileToWidgets()
                playClickSound()
            }
        }.tooltip(Tooltip.create(Component.translatable("cobblebrain.screen.ai_customization.delete_profile.tooltip"))).bounds(sidebarX + 2 + sideBtnW + 2, btnRow2Y, sideBtnW, 18).build()

        addRenderableWidget(newProfileBtn)
        addRenderableWidget(duplicateProfileBtn)
        addRenderableWidget(setActiveBtn)
        addRenderableWidget(deleteProfileBtn)

        // --- ROW 1: PROFILE NAME & TRAINER NAME (SIDE BY SIDE) ---
        val headerY = getHeaderY()
        val halfInputW = ((mainW - 140) / 2).coerceIn(75, 140)

        profileNameEdit = EditBox(font, mainX + 34, headerY + 1, halfInputW, 16, Component.translatable("cobblebrain.screen.ai_customization.profile_name"))
        profileNameEdit.setMaxLength(26)
        profileNameEdit.setTooltip(Tooltip.create(Component.translatable("cobblebrain.screen.ai_customization.profile_name.tooltip")))
        profileNameEdit.setResponder { text ->
            if (selectedProfileIndex in workingProfiles.indices) {
                workingProfiles[selectedProfileIndex].name = text
            }
        }
        addRenderableWidget(profileNameEdit)

        val trainerLabelX = profileNameEdit.x + halfInputW + 8
        preferredNameEdit = EditBox(font, trainerLabelX + 56, headerY + 1, halfInputW, 16, Component.translatable("cobblebrain.screen.ai_customization.trainer_name"))
        preferredNameEdit.setMaxLength(32)
        preferredNameEdit.setHint(Component.translatable("cobblebrain.screen.ai_customization.trainer_name.hint"))
        preferredNameEdit.setTooltip(Tooltip.create(Component.translatable("cobblebrain.screen.ai_customization.trainer_name.tooltip")))
        preferredNameEdit.setResponder { text ->
            if (selectedProfileIndex in workingProfiles.indices) {
                workingProfiles[selectedProfileIndex].preferredName = text
            }
        }
        addRenderableWidget(preferredNameEdit)

        // --- ROW 2: TABS IN A SEPARATE RECTANGLE ---
        val tabsCardY = getTabsCardY()
        val tabBtnW = (mainW - 8) / 2

        tabPromptLoreBtn = Button.builder(Component.translatable("cobblebrain.screen.ai_customization.tab_prompt_lore")) {
            switchTab(0)
            playClickSound()
        }.tooltip(Tooltip.create(Component.translatable("cobblebrain.screen.ai_customization.tab_prompt_lore.tooltip"))).bounds(mainX + 3, tabsCardY + 3, tabBtnW, 18).build()

        tabCapabilitiesBtn = Button.builder(Component.translatable("cobblebrain.screen.ai_customization.tab_capabilities")) {
            switchTab(1)
            playClickSound()
        }.tooltip(Tooltip.create(Component.translatable("cobblebrain.screen.ai_customization.tab_capabilities.tooltip"))).bounds(mainX + 3 + tabBtnW + 2, tabsCardY + 3, tabBtnW, 18).build()

        addRenderableWidget(tabPromptLoreBtn)
        addRenderableWidget(tabCapabilitiesBtn)

        // --- ROW 3: SETTINGS CARD CONTENT ---
        val contentX = getContentX()
        val contentY = getContentY()
        val contentW = getContentW()
        val contentH = getContentH()

        // === TAB 0 WIDGETS (COLLAPSIBLE BOXES) ===
        togglePlayerAboutBtn = Button.builder(Component.empty()) {
            saveCurrentValuesToSelectedProfile()
            playerAboutOpen = !playerAboutOpen
            updateTab0Layout()
            playClickSound()
        }.tooltip(Tooltip.create(Component.translatable("cobblebrain.screen.ai_customization.accordion_lore.tooltip"))).build()

        playerAboutBox = CustomMultiLineEditBox(
            font, contentX, contentY + 26, contentW, 40,
            Component.translatable("cobblebrain.screen.ai_customization.lore_placeholder"), Component.empty()
        )

        toggleInstructBtn = Button.builder(Component.empty()) {
            saveCurrentValuesToSelectedProfile()
            instructOpen = !instructOpen
            updateTab0Layout()
            playClickSound()
        }.tooltip(Tooltip.create(Component.translatable("cobblebrain.screen.ai_customization.accordion_instruct.tooltip"))).build()

        instructBox = CustomMultiLineEditBox(
            font, contentX, contentY + 70, contentW, 40,
            Component.translatable("cobblebrain.screen.ai_customization.instruct_placeholder"), Component.empty()
        )

        addRenderableWidget(togglePlayerAboutBtn)
        addRenderableWidget(playerAboutBox)
        addRenderableWidget(toggleInstructBtn)
        addRenderableWidget(instructBox)

        // === TAB 1 WIDGETS ===
        outputStructureBtn = Button.builder(Component.empty()) {
            if (isPromptBlocked()) return@builder
            if (selectedProfileIndex in workingProfiles.indices) {
                val p = workingProfiles[selectedProfileIndex]
                p.useDefaultOutput = !p.useDefaultOutput
                if (p.useDefaultOutput) {
                    outputFormatBox.setValue(CobblebrainClientConfig.DEFAULT_OUTPUT_FORMAT)
                }
                updateTab1Widgets(p)
                updateTabWidgetVisibility()
                playClickSound()
            }
        }.tooltip(Tooltip.create(Component.translatable("cobblebrain.screen.ai_customization.output_structure.tooltip"))).bounds(contentX, contentY + 1, contentW, 18).build()
        addRenderableWidget(outputStructureBtn)

        resetOutputBtn = Button.builder(Component.translatable("cobblebrain.screen.ai_customization.reset_output")) {
            if (selectedProfileIndex in workingProfiles.indices) {
                val p = workingProfiles[selectedProfileIndex]
                p.outputFormat = CobblebrainClientConfig.DEFAULT_OUTPUT_FORMAT
                outputFormatBox.setValue(CobblebrainClientConfig.DEFAULT_OUTPUT_FORMAT)
                playClickSound()
            }
        }.tooltip(Tooltip.create(Component.translatable("cobblebrain.screen.ai_customization.reset_output.tooltip"))).bounds(contentX + contentW - 110, contentY + 1, 110, 18).build()
        addRenderableWidget(resetOutputBtn)

        val outputFormatBoxY = contentY + 23
        val outputFormatBoxH = contentH - 25
        outputFormatBox = CustomMultiLineEditBox(
            font, contentX, outputFormatBoxY, contentW, outputFormatBoxH,
            Component.empty(), Component.empty()
        )
        addRenderableWidget(outputFormatBox)

        // === BOTTOM BAR WIDGETS ===
        val bottomY = height - 25
        saveBtn = Button.builder(Component.translatable("cobblebrain.screen.ai_customization.save")) {
            saveCurrentValuesToSelectedProfile()
            AiProfileManager.profiles.clear()
            AiProfileManager.profiles.addAll(workingProfiles)
            AiProfileManager.activeProfileId = activeProfileId
            AiProfileManager.applyActiveProfileToClient()
            AiProfileManager.save()
            if (ClientConfigHandler.clientConfig.presetMode != "Custom") {
                ClientConfigHandler.clientConfig.isCustomizedFromPreset = vito.cobblebrain.config.CobblebrainConfigScreen.isPresetModified(ClientConfigHandler.clientConfig.presetMode)
            }
            ClientConfigHandler.save()
            ConfigHandler.save()
            saveFeedbackTicks = 60
            playClickSound()
        }.bounds(width / 2 - 100, bottomY, 95, 20).build()

        closeBtn = Button.builder(Component.translatable("cobblebrain.screen.ai_customization.close")) {
            playClickSound()
            Minecraft.getInstance().setScreen(parentScreen)
        }.bounds(width / 2 + 5, bottomY, 95, 20).build()

        addRenderableWidget(saveBtn)
        addRenderableWidget(closeBtn)

        // Initialize display
        loadSelectedProfileToWidgets()
        switchTab(currentTab)
    }

    private fun ensureProfileVisible(idx: Int) {
        val listTop = getSidebarListTop()
        val listBottom = getSidebarListBottom()
        val visibleCount = ((listBottom - listTop) / 18).coerceAtLeast(1)
        if (idx < sidebarScrollOffset) {
            sidebarScrollOffset = idx
        } else if (idx >= sidebarScrollOffset + visibleCount) {
            sidebarScrollOffset = (idx - visibleCount + 1).coerceAtLeast(0)
        }
    }

    private fun isPromptBlocked(): Boolean {
        val client = Minecraft.getInstance()
        val isAllowed = if (client.player != null && !client.isLocalServer) {
            SyncedConfig.allowCustomClientPrompt
        } else {
            ConfigHandler.config.allowCustomClientPrompt
        }
        return !isAllowed
    }

    private fun isServerProfile(p: AiProfile?): Boolean {
        if (p == null) return false
        return p.id == "server" || p.name.trim().startsWith("Server", ignoreCase = true)
    }

    private fun switchTab(tab: Int) {
        saveCurrentValuesToSelectedProfile()
        currentTab = tab
        capabilitiesScrollOffset = 0
        capManualScroll.clear()
        draggingHScrollCapIdx = -1
        isDraggingCapVScroll = false
        isDraggingSidebarVScroll = false
        setFocused(null)
        updateTabWidgetVisibility()
    }

    private fun updateTab0Layout() {
        val contentX = getContentX()
        val contentY = getContentY()
        val contentW = getContentW()
        val contentH = getContentH()

        togglePlayerAboutBtn.visible = (currentTab == 0)
        toggleInstructBtn.visible = (currentTab == 0)

        togglePlayerAboutBtn.message = Component.translatable(
            if (playerAboutOpen) "cobblebrain.screen.ai_customization.accordion_lore_open"
            else "cobblebrain.screen.ai_customization.accordion_lore_closed"
        ).withStyle(if (playerAboutOpen) ChatFormatting.YELLOW else ChatFormatting.WHITE)

        toggleInstructBtn.message = Component.translatable(
            if (instructOpen) "cobblebrain.screen.ai_customization.accordion_instruct_open"
            else "cobblebrain.screen.ai_customization.accordion_instruct_closed"
        ).withStyle(if (instructOpen) ChatFormatting.YELLOW else ChatFormatting.WHITE)

        val mc = Minecraft.getInstance()
        val isRemoteServer = mc.player != null && !mc.isLocalServer
        val isPromptBlocked = isPromptBlocked(); val topOffset = if (isPromptBlocked) 24 else 0

        when {
            !playerAboutOpen && !instructOpen -> {
                togglePlayerAboutBtn.x = contentX
                togglePlayerAboutBtn.y = contentY + 4 + topOffset
                togglePlayerAboutBtn.width = contentW
                togglePlayerAboutBtn.height = 20

                toggleInstructBtn.x = contentX
                toggleInstructBtn.y = contentY + 28 + topOffset
                toggleInstructBtn.width = contentW
                toggleInstructBtn.height = 20

                playerAboutBox.visible = false
                instructBox.visible = false
            }
            playerAboutOpen && !instructOpen -> {
                togglePlayerAboutBtn.x = contentX
                togglePlayerAboutBtn.y = contentY + 4 + topOffset
                togglePlayerAboutBtn.width = contentW
                togglePlayerAboutBtn.height = 20

                toggleInstructBtn.x = contentX
                toggleInstructBtn.y = contentY + contentH - 22
                toggleInstructBtn.width = contentW
                toggleInstructBtn.height = 20

                val boxY = contentY + 26 + topOffset
                val boxH = (toggleInstructBtn.y - 4 - boxY).coerceAtLeast(35)
                playerAboutBox.setBounds(contentX, boxY, contentW, boxH)
                playerAboutBox.visible = (currentTab == 0)
                playerAboutBox.active = (currentTab == 0 && !isPromptBlocked)

                instructBox.visible = false
            }
            !playerAboutOpen && instructOpen -> {
                togglePlayerAboutBtn.x = contentX
                togglePlayerAboutBtn.y = contentY + 4 + topOffset
                togglePlayerAboutBtn.width = contentW
                togglePlayerAboutBtn.height = 20

                playerAboutBox.visible = false

                toggleInstructBtn.x = contentX
                toggleInstructBtn.y = contentY + 28 + topOffset
                toggleInstructBtn.width = contentW
                toggleInstructBtn.height = 20

                val boxY = contentY + 50 + topOffset
                val boxH = (contentY + contentH - boxY).coerceAtLeast(35)
                instructBox.setBounds(contentX, boxY, contentW, boxH)
                instructBox.visible = (currentTab == 0)
                instructBox.active = (currentTab == 0 && !isPromptBlocked)
            }
            playerAboutOpen && instructOpen -> {
                togglePlayerAboutBtn.x = contentX
                togglePlayerAboutBtn.y = contentY + 4 + topOffset
                togglePlayerAboutBtn.width = contentW
                togglePlayerAboutBtn.height = 20

                val availableH = contentH - 52 - topOffset
                val boxH = (availableH / 2).coerceAtLeast(30)

                val box1Y = contentY + 26 + topOffset
                playerAboutBox.setBounds(contentX, box1Y, contentW, boxH)
                playerAboutBox.visible = (currentTab == 0)
                playerAboutBox.active = (currentTab == 0 && !isPromptBlocked)

                val btn2Y = box1Y + boxH + 4
                toggleInstructBtn.x = contentX
                toggleInstructBtn.y = btn2Y
                toggleInstructBtn.width = contentW
                toggleInstructBtn.height = 20

                val box2Y = btn2Y + 22
                val box2H = (contentY + contentH - box2Y).coerceAtLeast(30)
                instructBox.setBounds(contentX, box2Y, contentW, box2H)
                instructBox.visible = (currentTab == 0)
                instructBox.active = (currentTab == 0 && !isPromptBlocked)
            }
        }
    }

    private fun updateTabWidgetVisibility() {
        val p = workingProfiles.getOrNull(selectedProfileIndex)
        val isRecommended = p?.useDefaultOutput ?: true

        tabPromptLoreBtn.active = (currentTab != 0)
        tabCapabilitiesBtn.active = (currentTab != 1)

        // Tab 0
        if (currentTab == 0) {
            updateTab0Layout()
        } else {
            togglePlayerAboutBtn.visible = false
            toggleInstructBtn.visible = false
            playerAboutBox.visible = false
            instructBox.visible = false
        }

        // Tab 1 Header
        outputStructureBtn.visible = (currentTab == 1)
        outputStructureBtn.active = (currentTab == 1 && !isPromptBlocked())
        resetOutputBtn.visible = (currentTab == 1 && !isRecommended)
        resetOutputBtn.active = (currentTab == 1 && !isRecommended && !isPromptBlocked())
        outputFormatBox.visible = (currentTab == 1 && !isRecommended)
        outputFormatBox.active = (currentTab == 1 && !isRecommended && !isPromptBlocked())

        if (p != null) {
            updateTab1Widgets(p)
        }
    }

    private fun updateTab1Widgets(p: AiProfile) {
        val contentX = getContentX()
        val contentW = getContentW()

        if (p.useDefaultOutput) {
            outputStructureBtn.message = Component.translatable("cobblebrain.screen.ai_customization.output_recommended").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
            outputStructureBtn.width = contentW
        } else {
            outputStructureBtn.message = Component.translatable("cobblebrain.screen.ai_customization.output_custom").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            outputStructureBtn.width = contentW - 114
            resetOutputBtn.x = contentX + contentW - 110
        }
    }

    private fun saveCurrentValuesToSelectedProfile() {
        if (selectedProfileIndex !in workingProfiles.indices) return
        val p = workingProfiles[selectedProfileIndex]
        val fallback = Component.translatable("cobblebrain.screen.ai_customization.default_profile_name").string
        p.name = profileNameEdit.value.trim().ifBlank { fallback }
        p.preferredName = preferredNameEdit.value.trim()

        val aboutLines = playerAboutBox.value.lines().map { it.trim() }.filter { it.isNotBlank() }
        p.playerAbout = ClientConfigHandler.ensurePlayerAboutHeader(aboutLines)

        val instructLines = instructBox.value.lines().map { it.trim() }.filter { it.isNotBlank() }
        p.instruct = ClientConfigHandler.ensureCreativePrompt(instructLines)

        p.outputFormat = outputFormatBox.value
        if (p.outputFormat.trim() != CobblebrainClientConfig.DEFAULT_OUTPUT_FORMAT.trim()) {
            p.useDefaultOutput = false
        }
    }

    private fun loadSelectedProfileToWidgets() {
        if (selectedProfileIndex !in workingProfiles.indices) return
        val p = workingProfiles[selectedProfileIndex]
        profileNameEdit.value = p.name
        preferredNameEdit.value = p.preferredName

        playerAboutBox.setValue(p.playerAbout.joinToString("\n"))
        instructBox.setValue(p.instruct.joinToString("\n"))
        outputFormatBox.setValue(p.outputFormat)

        updateTab1Widgets(p)
        updateTabWidgetVisibility()
        deleteProfileBtn.active = (workingProfiles.size > 1 && !(isPromptBlocked() && isServerProfile(p)))

        val isServer = isPromptBlocked() && isServerProfile(p)
        if (isServer) {
            saveBtn.visible = false
            closeBtn.x = width / 2 - 47
            closeBtn.width = 95
        } else {
            saveBtn.visible = true
            saveBtn.x = width / 2 - 100
            saveBtn.width = 95
            closeBtn.x = width / 2 + 5
            closeBtn.width = 95
        }
        setActiveBtn.active = !isPromptBlocked()
    }

    private fun playClickSound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f))
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val topOffset = getTopOffset()
        val bottomBarHeight = getBottomBarHeight()

        // Solid backdrop
        guiGraphics.fill(0, 0, width, height, 0xFF12141A.toInt())

        // Header & footer bars
        guiGraphics.fill(0, 0, width, topOffset, 0xFF0B0D11.toInt())
        guiGraphics.fill(0, topOffset, width, topOffset + 1, 0xFF262A34.toInt())
        guiGraphics.fill(0, height - bottomBarHeight, width, height, 0xFF0B0D11.toInt())
        guiGraphics.fill(0, height - bottomBarHeight, width, height - bottomBarHeight + 1, 0xFF262A34.toInt())

        val sidebarX = getSidebarX()
        val sidebarY = topOffset + 5
        val sidebarW = getSidebarW()
        val sidebarH = height - sidebarY - bottomBarHeight - 5

        val mainX = getMainX()
        val mainW = getMainW()
        val tabsCardY = getTabsCardY()
        val tabsCardH = getTabsCardH()
        val settingsY = getSettingsCardY()
        val settingsH = getSettingsCardH()

        // Sidebar card
        guiGraphics.fill(sidebarX, sidebarY, sidebarX + sidebarW, sidebarY + sidebarH, 0xFF181B22.toInt())
        guiGraphics.renderOutline(sidebarX, sidebarY, sidebarW, sidebarH, 0xFF2C313C.toInt())

        // Tabs card (Separate rectangle from configurations)
        guiGraphics.fill(mainX, tabsCardY, mainX + mainW, tabsCardY + tabsCardH, 0xFF181B22.toInt())
        guiGraphics.renderOutline(mainX, tabsCardY, mainW, tabsCardH, 0xFF2C313C.toInt())

        // Configurations card (Settings panel)
        guiGraphics.fill(mainX, settingsY, mainX + mainW, settingsY + settingsH, 0xFF181B22.toInt())
        guiGraphics.renderOutline(mainX, settingsY, mainW, settingsH, 0xFF2C313C.toInt())
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val topOffset = getTopOffset()
        val sidebarX = getSidebarX()
        val sidebarY = topOffset + 5
        val sidebarW = getSidebarW()

        val mainX = getMainX()
        val mainW = getMainW()
        val headerY = getHeaderY()

        val contentX = getContentX()
        val contentY = getContentY()
        val contentW = getContentW()
        val contentH = getContentH()

        // Title
        guiGraphics.drawCenteredString(font, title, width / 2, 9, 0xFFFFFF)

        // Save confirmation text
        if (saveFeedbackTicks > 0) {
            saveFeedbackTicks--
            guiGraphics.drawString(font, Component.translatable("cobblebrain.screen.ai_customization.saved"), width / 2 + 105, height - 19, 0x55FF55, true)
        }

        // --- SIDEBAR PROFILES LIST ---
        guiGraphics.drawString(font, Component.translatable("cobblebrain.screen.ai_customization.profiles"), sidebarX + 6, sidebarY + 6, 0xFFFF55, true)

        val listTop = getSidebarListTop()
        val listBottom = getSidebarListBottom()
        val itemH = 18
        val availableH = listBottom - listTop
        val visibleCount = (availableH / itemH).coerceAtLeast(1)

        // Profile items
        for (i in 0 until visibleCount) {
            val idx = i + sidebarScrollOffset
            if (idx !in workingProfiles.indices) break
            val p = workingProfiles[idx]
            val itemY = listTop + (i * itemH)
            val isSelected = (idx == selectedProfileIndex)
            val isActive = (p.id == activeProfileId)

            val isHovered = mouseX in (sidebarX + 2)..(sidebarX + sidebarW - 2) && mouseY in itemY..(itemY + itemH - 2)
            val bgCol = when {
                isSelected -> 0xFF1D4ED8.toInt()
                isHovered -> 0xFF252B36.toInt()
                else -> 0xFF14161D.toInt()
            }

            guiGraphics.fill(sidebarX + 2, itemY, sidebarX + sidebarW - 2, itemY + itemH - 2, bgCol)
            if (isSelected) {
                guiGraphics.renderOutline(sidebarX + 2, itemY, sidebarW - 4, itemH - 2, 0xFF38BDF8.toInt())
            }

            if (isActive) {
                guiGraphics.fill(sidebarX + 2, itemY, sidebarX + 5, itemY + itemH - 2, 0xFF22C55E.toInt())
            }

            val maxTextW = sidebarW - 24
            val displayName = font.plainSubstrByWidth(p.name, maxTextW)
            val textCol = when {
                isActive && isSelected -> 0xAAFFAA
                isActive -> 0x55FF55
                isSelected -> 0xFFFFFF
                else -> 0xCCCCCC
            }
            guiGraphics.drawString(font, displayName, if (isActive) sidebarX + 7 else sidebarX + 5, itemY + 4, textCol, isActive)

            if (isActive) {
                guiGraphics.drawString(font, "▶", sidebarX + sidebarW - 13, itemY + 4, 0x55FF55, true)
            }
        }

        // Scrollbar - Sidebar
        if (workingProfiles.size > visibleCount) {
            val scrollTrackH = listBottom - listTop
            val thumbH = ((visibleCount.toFloat() / workingProfiles.size) * scrollTrackH).toInt().coerceAtLeast(12)
            val maxScroll = workingProfiles.size - visibleCount
            val thumbY = listTop + ((sidebarScrollOffset.toFloat() / maxScroll) * (scrollTrackH - thumbH)).toInt()
            val barX = sidebarX + sidebarW - 4
            val barW = 3
            val isThumbHovered = mouseX in (barX - 2)..(barX + barW + 2) && mouseY in thumbY..(thumbY + thumbH)
            val thumbCol = if (isDraggingSidebarVScroll || isThumbHovered) 0xFF7DD3FC.toInt() else 0xFF38BDF8.toInt()
            guiGraphics.fill(barX, listTop, barX + barW, listBottom, 0xFF262A34.toInt())
            guiGraphics.fill(barX, thumbY, barX + barW, thumbY + thumbH, thumbCol)
        }

        // --- ROW 1 LABELS: PRESET NAME & TRAINER NAME SIDE BY SIDE ---
        val halfInputW = ((mainW - 140) / 2).coerceIn(75, 140)
        val trainerLabelX = profileNameEdit.x + halfInputW + 8
        guiGraphics.drawString(font, Component.translatable("cobblebrain.screen.ai_customization.profile_name_label"), mainX + 2, headerY + 4, 0xCCCCCC, false)
        guiGraphics.drawString(font, Component.translatable("cobblebrain.screen.ai_customization.trainer_name_label"), trainerLabelX, headerY + 4, 0xCCCCCC, false)

        // Tab contents notices
        when (currentTab) {
            0 -> {
                val mc = Minecraft.getInstance()
                val isRemoteServer = mc.player != null && !mc.isLocalServer
                val isPromptBlocked = isPromptBlocked(); val topOffset = if (isPromptBlocked) 24 else 0
                if (isPromptBlocked) {
                    val bannerY = contentY + 3
                    val bannerH = 18
                    guiGraphics.fill(contentX, bannerY, contentX + contentW, bannerY + bannerH, 0x44AA2222.toInt())
                    guiGraphics.renderOutline(contentX, bannerY, contentW, bannerH, 0xFFAA4444.toInt())
                    guiGraphics.drawCenteredString(font, Component.translatable("cobblebrain.screen.ai_customization.blocked_notice"), contentX + (contentW / 2), bannerY + 5, 0xFFA500)
                }
            }
            1 -> {
                val p = workingProfiles.getOrNull(selectedProfileIndex)
                if (p?.useDefaultOutput == true) {
                    // Render Image-2 styled Capability Cards
                    val listY = getCapabilityListY()
                    val listBottom = getCapabilityListBottom()
                    val availableListH = listBottom - listY
                    val cardSlotH = 37
                    val cardH = 34
                    val visibleCapCount = (availableListH / cardSlotH).coerceAtLeast(1)
                    val hasScroll = capabilities.size > visibleCapCount
                    val cardW = if (hasScroll) contentW - 9 else contentW

                    for (i in 0 until visibleCapCount) {
                        val capIdx = i + capabilitiesScrollOffset
                        if (capIdx !in capabilities.indices) break
                        val cap = capabilities[capIdx]
                        val cardY = listY + (i * cardSlotH)
                        val isCardHovered = mouseX in contentX..(contentX + cardW) && mouseY in cardY..(cardY + cardH)

                        val bgCol = if (isCardHovered) 0xFF1D2433.toInt() else 0xFF151923.toInt()
                        val borderCol = if (isCardHovered) 0xFF3B4861.toInt() else 0xFF252D3D.toInt()

                        // Card body & outline
                        guiGraphics.fill(contentX, cardY, contentX + cardW, cardY + cardH, bgCol)
                        guiGraphics.renderOutline(contentX, cardY, cardW, cardH, borderCol)

                        // Title (line 1)
                        guiGraphics.drawString(font, cap.title, contentX + 7, cardY + 4, 0xFFFFFF, true)

                        // Description (line 2) with horizontal scrolling
                        val clipMinX = contentX + 7
                        val clipMaxX = contentX + cardW - 54
                        val visibleW = clipMaxX - clipMinX
                        val textW = font.width(cap.description)
                        val extraPadding = (textW * 0.10f).toInt().coerceAtLeast(16)
                        val totalContentW = textW + extraPadding
                        val overflow = if (textW > visibleW) totalContentW - visibleW else 0
                        val scrollOffset = (capManualScroll[capIdx] ?: 0).coerceIn(0, overflow)

                        val clipMinY = cardY + 14
                        val clipMaxY = cardY + 26

                        if (overflow > 0 && clipMaxX > clipMinX) {
                            guiGraphics.enableScissor(clipMinX, clipMinY, clipMaxX, clipMaxY)
                            guiGraphics.drawString(font, cap.description, clipMinX - scrollOffset, cardY + 16, 0x94A3B8, false)
                            guiGraphics.disableScissor()
                        } else {
                            val descText = font.plainSubstrByWidth(cap.description, visibleW)
                            guiGraphics.drawString(font, descText, contentX + 7, cardY + 16, 0x94A3B8, false)
                        }

                        // Horizontal Scrollbar (shown only when hovered and text overflows)
                        if (isCardHovered && overflow > 0) {
                            val trackX1 = clipMinX + 8
                            val trackX2 = clipMaxX - 8
                            val trackW = trackX2 - trackX1
                            val trackY = cardY + 28
                            val trackH = 3

                            // Left arrow indicator
                            val leftArrowHovered = mouseX in (clipMinX - 1)..(clipMinX + 7) && mouseY in (cardY + 25)..(cardY + 33)
                            val leftArrowCol = if (leftArrowHovered) 0xFF34D399.toInt() else 0xFF94A3B8.toInt()
                            val lax = clipMinX + 1
                            val lay = cardY + 27
                            guiGraphics.fill(lax + 2, lay, lax + 3, lay + 5, leftArrowCol)
                            guiGraphics.fill(lax + 1, lay + 1, lax + 2, lay + 4, leftArrowCol)
                            guiGraphics.fill(lax, lay + 2, lax + 1, lay + 3, leftArrowCol)

                            // Right arrow indicator
                            val rightArrowHovered = mouseX in (clipMaxX - 7)..(clipMaxX + 1) && mouseY in (cardY + 25)..(cardY + 33)
                            val rightArrowCol = if (rightArrowHovered) 0xFF34D399.toInt() else 0xFF94A3B8.toInt()
                            val rax = clipMaxX - 4
                            val ray = cardY + 27
                            guiGraphics.fill(rax - 2, ray, rax - 1, ray + 5, rightArrowCol)
                            guiGraphics.fill(rax - 1, ray + 1, rax, ray + 4, rightArrowCol)
                            guiGraphics.fill(rax, ray + 2, rax + 1, ray + 3, rightArrowCol)

                            // Track
                            guiGraphics.fill(trackX1, trackY, trackX2, trackY + trackH, 0xFF1E2638.toInt())

                            // Thumb
                            val thumbW = ((visibleW.toFloat() / totalContentW) * trackW).toInt().coerceIn(14, trackW - 4)
                            val progress = scrollOffset.toFloat() / overflow
                            val thumbX = trackX1 + (progress * (trackW - thumbW)).toInt()
                            val isThumbHovered = mouseX in thumbX..(thumbX + thumbW) && mouseY in (trackY - 2)..(trackY + trackH + 2)
                            val isDragging = (draggingHScrollCapIdx == capIdx)
                            val thumbBg = if (isDragging || isThumbHovered) 0xFF34D399.toInt() else 0xFF10B981.toInt()

                            guiGraphics.fill(thumbX, trackY - 1, thumbX + thumbW, trackY + trackH + 1, thumbBg)
                            guiGraphics.renderOutline(thumbX, trackY - 1, thumbW, trackH + 2, 0xFF6EE7B7.toInt())
                        }

                        // Toggle Pill Button (Right side, styled like Image 2)
                        val isOn = cap.getter(p)
                        val pillW = 38
                        val pillH = 19
                        val pillX = contentX + cardW - pillW - 6
                        val pillY = cardY + 7

                        val pillBg = if (isOn) 0xFF059669.toInt() else 0xFF27272A.toInt()
                        val pillBorder = if (isOn) 0xFF10B981.toInt() else 0xFF3F3F46.toInt()
                        val pillText = if (isOn) "ON" else "OFF"
                        val pillTextCol = if (isOn) 0xFFFFFF else 0x71717A

                        if (!isPromptBlocked()) {
                            guiGraphics.fill(pillX, pillY, pillX + pillW, pillY + pillH, pillBg)
                            guiGraphics.renderOutline(pillX, pillY, pillW, pillH, pillBorder)
                        }
                        val textX = pillX + (pillW - font.width(pillText)) / 2
                        val textCol = if (isPromptBlocked()) (if (isOn) 0x55FF55 else 0x71717A) else pillTextCol
                        guiGraphics.drawString(font, pillText, textX, pillY + 5, textCol, isOn)
                    }

                    // Scrollbar for Capabilities
                    if (hasScroll) {
                        val scrollTrackH = availableListH
                        val thumbH = ((visibleCapCount.toFloat() / capabilities.size) * scrollTrackH).toInt().coerceAtLeast(14)
                        val maxScroll = capabilities.size - visibleCapCount
                        val thumbY = listY + ((capabilitiesScrollOffset.toFloat() / maxScroll) * (scrollTrackH - thumbH)).toInt()
                        val barX = contentX + contentW - 5
                        val barW = 4

                        val isThumbHovered = mouseX in (barX - 2)..(barX + barW + 2) && mouseY in thumbY..(thumbY + thumbH)
                        val isBarDragging = isDraggingCapVScroll
                        val thumbBg = if (isBarDragging || isThumbHovered) 0xFF34D399.toInt() else 0xFF10B981.toInt()

                        guiGraphics.fill(barX, listY, barX + barW, listBottom, 0xFF1E2638.toInt())
                        guiGraphics.fill(barX, thumbY, barX + barW, thumbY + thumbH, thumbBg)
                        guiGraphics.renderOutline(barX, thumbY, barW, thumbH, 0xFF6EE7B7.toInt())
                    }
                }

                val mc = Minecraft.getInstance()
                val isRemoteServer = mc.player != null && !mc.isLocalServer
                if (isPromptBlocked()) {
                    guiGraphics.drawString(font, Component.translatable("cobblebrain.screen.ai_customization.blocked_notice"), contentX + 4, contentY + contentH - 8, 0xFFA500, false)
                } else if (isRemoteServer) {
                    guiGraphics.drawString(font, Component.translatable("cobblebrain.screen.ai_customization.server_notice"), contentX + 4, contentY + contentH - 8, 0x666666, false)
                }
            }
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val sidebarX = getSidebarX()
        val sidebarW = getSidebarW()
        val listTop = getSidebarListTop()
        val listBottom = getSidebarListBottom()
        val itemH = 18

        // Click on Sidebar Vertical Scrollbar
        val visibleSidebarCount = ((listBottom - listTop) / itemH).coerceAtLeast(1)
        if (button == 0 && workingProfiles.size > visibleSidebarCount) {
            val maxScroll = workingProfiles.size - visibleSidebarCount
            val scrollTrackH = listBottom - listTop
            val thumbH = ((visibleSidebarCount.toFloat() / workingProfiles.size) * scrollTrackH).toInt().coerceAtLeast(12)
            val barX = sidebarX + sidebarW - 4
            val barW = 3
            if (mouseX >= barX - 4 && mouseX <= sidebarX + sidebarW + 2 && mouseY >= listTop && mouseY <= listBottom) {
                val clickProgress = ((mouseY - listTop - (thumbH / 2.0)) / (scrollTrackH - thumbH)).coerceIn(0.0, 1.0)
                sidebarScrollOffset = Math.round(clickProgress * maxScroll).toInt()
                isDraggingSidebarVScroll = true
                return true
            }
        }

        // Click on Sidebar Profile
        if (button == 0 && mouseX >= sidebarX + 2 && mouseX <= sidebarX + sidebarW - 2 && mouseY >= listTop && mouseY <= listBottom) {
            val clickedRow = ((mouseY - listTop) / itemH).toInt()
            val targetIdx = clickedRow + sidebarScrollOffset
            if (targetIdx in workingProfiles.indices && targetIdx != selectedProfileIndex) {
                saveCurrentValuesToSelectedProfile()
                selectedProfileIndex = targetIdx
                playerAboutOpen = false
                instructOpen = false
                capabilitiesScrollOffset = 0
                capManualScroll.clear()
                draggingHScrollCapIdx = -1
                isDraggingCapVScroll = false
                isDraggingSidebarVScroll = false
                loadSelectedProfileToWidgets()
                playClickSound()
                return true
            }
        }

        // Click on Capabilities Vertical Scrollbar
        if (button == 0 && currentTab == 1) {
            val p = workingProfiles.getOrNull(selectedProfileIndex)
            if (p?.useDefaultOutput == true) {
                val contentX = getContentX()
                val contentW = getContentW()
                val listY = getCapabilityListY()
                val listBottom = getCapabilityListBottom()
                val availableListH = listBottom - listY
                val cardSlotH = 37
                val visibleCapCount = (availableListH / cardSlotH).coerceAtLeast(1)

                if (capabilities.size > visibleCapCount) {
                    val maxScroll = capabilities.size - visibleCapCount
                    val scrollTrackH = availableListH
                    val thumbH = ((visibleCapCount.toFloat() / capabilities.size) * scrollTrackH).toInt().coerceAtLeast(14)
                    val barX = contentX + contentW - 5
                    val barW = 4

                    if (mouseX >= barX - 4 && mouseX <= contentX + contentW + 2 && mouseY >= listY && mouseY <= listBottom) {
                        val clickProgress = ((mouseY - listY - (thumbH / 2.0)) / (scrollTrackH - thumbH)).coerceIn(0.0, 1.0)
                        capabilitiesScrollOffset = Math.round(clickProgress * maxScroll).toInt()
                        isDraggingCapVScroll = true
                        return true
                    }
                }
            }
        }

        // Click on Capability Cards (Tab 1, Recommended mode)
        if (button == 0 && currentTab == 1) {
            val p = workingProfiles.getOrNull(selectedProfileIndex)
            if (p?.useDefaultOutput == true) {
                val contentX = getContentX()
                val contentW = getContentW()
                val listY = getCapabilityListY()
                val listBottom = getCapabilityListBottom()
                val availableListH = listBottom - listY
                val cardSlotH = 37
                val cardH = 34
                val visibleCapCount = (availableListH / cardSlotH).coerceAtLeast(1)
                val cardW = if (capabilities.size > visibleCapCount) contentW - 9 else contentW

                if (mouseX >= contentX && mouseX <= contentX + cardW && mouseY >= listY && mouseY <= listBottom) {
                    val clickedRow = ((mouseY - listY) / cardSlotH).toInt()
                    val targetCapIdx = clickedRow + capabilitiesScrollOffset
                    if (targetCapIdx in capabilities.indices) {
                        val cap = capabilities[targetCapIdx]
                        val cardY = listY + (clickedRow * cardSlotH)

                        val clipMinX = contentX + 7
                        val clipMaxX = contentX + cardW - 54
                        val visibleW = clipMaxX - clipMinX
                        val textW = font.width(cap.description)
                        val extraPadding = (textW * 0.10f).toInt().coerceAtLeast(16)
                        val totalContentW = textW + extraPadding
                        val overflow = if (textW > visibleW) totalContentW - visibleW else 0

                        val trackX1 = clipMinX + 8
                        val trackX2 = clipMaxX - 8
                        val trackW = trackX2 - trackX1
                        val trackY = cardY + 28
                        val trackH = 3

                        if (overflow > 0) {
                            // Left arrow click (step left)
                            if (mouseX >= clipMinX - 2 && mouseX <= clipMinX + 7 && mouseY >= cardY + 25 && mouseY <= cardY + 33) {
                                val current = capManualScroll[targetCapIdx] ?: 0
                                capManualScroll[targetCapIdx] = (current - 24).coerceAtLeast(0)
                                playClickSound()
                                return true
                            }

                            // Right arrow click (step right)
                            if (mouseX >= clipMaxX - 7 && mouseX <= clipMaxX + 2 && mouseY >= cardY + 25 && mouseY <= cardY + 33) {
                                val current = capManualScroll[targetCapIdx] ?: 0
                                capManualScroll[targetCapIdx] = (current + 24).coerceAtMost(overflow)
                                playClickSound()
                                return true
                            }

                            // Click on horizontal scrollbar track / thumb to drag
                            if (mouseX >= trackX1 && mouseX <= trackX2 && mouseY >= trackY - 3 && mouseY <= trackY + trackH + 3) {
                                val thumbW = ((visibleW.toFloat() / totalContentW) * trackW).toInt().coerceIn(14, trackW - 4)
                                val clickProgress = ((mouseX - trackX1 - (thumbW / 2.0)) / (trackW - thumbW)).coerceIn(0.0, 1.0)
                                capManualScroll[targetCapIdx] = (clickProgress * overflow).toInt()
                                draggingHScrollCapIdx = targetCapIdx
                                return true
                            }
                        }

                        val pillW = 38
                        val pillH = 19
                        val pillX = contentX + cardW - pillW - 6
                        val pillY = cardY + 7

                        val isPillClicked = mouseX in pillX.toDouble()..(pillX + pillW).toDouble() && mouseY in pillY.toDouble()..(pillY + pillH).toDouble()
                        val isCardClicked = mouseX in contentX.toDouble()..(contentX + cardW).toDouble() && mouseY in cardY.toDouble()..(cardY + cardH).toDouble()

                        if (isPillClicked || isCardClicked) {
                            if (isPromptBlocked()) return true
                            val currentVal = cap.getter(p)
                            cap.setter(p, !currentVal)
                            playClickSound()
                            return true
                        }
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (button == 0) {
            // Dragging Capabilities Vertical Scrollbar
            if (currentTab == 1 && isDraggingCapVScroll) {
                val listY = getCapabilityListY()
                val listBottom = getCapabilityListBottom()
                val availableListH = listBottom - listY
                val cardSlotH = 37
                val visibleCapCount = (availableListH / cardSlotH).coerceAtLeast(1)

                if (capabilities.size > visibleCapCount) {
                    val maxScroll = capabilities.size - visibleCapCount
                    val scrollTrackH = availableListH
                    val thumbH = ((visibleCapCount.toFloat() / capabilities.size) * scrollTrackH).toInt().coerceAtLeast(14)
                    val dragProgress = ((mouseY - listY - (thumbH / 2.0)) / (scrollTrackH - thumbH)).coerceIn(0.0, 1.0)
                    capabilitiesScrollOffset = Math.round(dragProgress * maxScroll).toInt()
                    return true
                }
            }

            // Dragging Sidebar Vertical Scrollbar
            if (isDraggingSidebarVScroll) {
                val listTop = getSidebarListTop()
                val listBottom = getSidebarListBottom()
                val itemH = 18
                val visibleCount = ((listBottom - listTop) / itemH).coerceAtLeast(1)
                if (workingProfiles.size > visibleCount) {
                    val maxScroll = workingProfiles.size - visibleCount
                    val scrollTrackH = listBottom - listTop
                    val thumbH = ((visibleCount.toFloat() / workingProfiles.size) * scrollTrackH).toInt().coerceAtLeast(12)
                    val dragProgress = ((mouseY - listTop - (thumbH / 2.0)) / (scrollTrackH - thumbH)).coerceIn(0.0, 1.0)
                    sidebarScrollOffset = Math.round(dragProgress * maxScroll).toInt()
                    return true
                }
            }

            // Dragging Capability Horizontal Scrollbar
            if (currentTab == 1 && draggingHScrollCapIdx in capabilities.indices) {
                val p = workingProfiles.getOrNull(selectedProfileIndex)
                if (p?.useDefaultOutput == true) {
                    val contentX = getContentX()
                    val contentW = getContentW()
                    val listY = getCapabilityListY()
                    val listBottom = getCapabilityListBottom()
                    val availableListH = listBottom - listY
                    val cardSlotH = 37
                    val visibleCapCount = (availableListH / cardSlotH).coerceAtLeast(1)
                    val cardW = if (capabilities.size > visibleCapCount) contentW - 9 else contentW

                    val cap = capabilities[draggingHScrollCapIdx]
                    val clipMinX = contentX + 7
                    val clipMaxX = contentX + cardW - 54
                    val visibleW = clipMaxX - clipMinX
                    val textW = font.width(cap.description)
                    val extraPadding = (textW * 0.10f).toInt().coerceAtLeast(16)
                    val totalContentW = textW + extraPadding
                    val overflow = if (textW > visibleW) totalContentW - visibleW else 0

                    val trackX1 = clipMinX + 8
                    val trackX2 = clipMaxX - 8
                    val trackW = trackX2 - trackX1

                    if (overflow > 0 && trackW > 20) {
                        val thumbW = ((visibleW.toFloat() / totalContentW) * trackW).toInt().coerceIn(14, trackW - 4)
                        val dragProgress = ((mouseX - trackX1 - (thumbW / 2.0)) / (trackW - thumbW)).coerceIn(0.0, 1.0)
                        capManualScroll[draggingHScrollCapIdx] = (dragProgress * overflow).toInt()
                        return true
                    }
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            draggingHScrollCapIdx = -1
            isDraggingCapVScroll = false
            isDraggingSidebarVScroll = false
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val sidebarX = getSidebarX()
        val sidebarW = getSidebarW()
        val sidebarY = getTopOffset() + 5
        val sidebarH = height - sidebarY - getBottomBarHeight() - 5
        val listTop = getSidebarListTop()
        val listBottom = getSidebarListBottom()
        val itemH = 18
        val visibleCount = ((listBottom - listTop) / itemH).coerceAtLeast(1)

        // Scroll Sidebar
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarW && mouseY >= sidebarY && mouseY <= sidebarY + sidebarH) {
            val maxScroll = (workingProfiles.size - visibleCount).coerceAtLeast(0)
            if (scrollY > 0) {
                sidebarScrollOffset = (sidebarScrollOffset - 1).coerceAtLeast(0)
            } else if (scrollY < 0) {
                sidebarScrollOffset = (sidebarScrollOffset + 1).coerceAtMost(maxScroll)
            }
            return true
        }

        // Scroll Capability List (Tab 1, Recommended mode) - only scrolls the vertical list
        if (currentTab == 1) {
            val p = workingProfiles.getOrNull(selectedProfileIndex)
            if (p?.useDefaultOutput == true) {
                val contentX = getContentX()
                val contentW = getContentW()
                val listY = getCapabilityListY()
                val listBottom = getCapabilityListBottom()
                val availableListH = listBottom - listY
                val cardSlotH = 37
                val visibleCapCount = (availableListH / cardSlotH).coerceAtLeast(1)

                if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= listY && mouseY <= listBottom) {
                    val maxScroll = (capabilities.size - visibleCapCount).coerceAtLeast(0)
                    if (scrollY > 0) {
                        capabilitiesScrollOffset = (capabilitiesScrollOffset - 1).coerceAtLeast(0)
                    } else if (scrollY < 0) {
                        capabilitiesScrollOffset = (capabilitiesScrollOffset + 1).coerceAtMost(maxScroll)
                    }
                    return true
                }
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun shouldCloseOnEsc(): Boolean = true
}

private class CustomMultiLineEditBox(
    font: Font,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    placeholder: Component,
    message: Component
) : MultiLineEditBox(font, x, y, width, height, placeholder, message) {

    fun setBounds(newX: Int, newY: Int, newWidth: Int, newHeight: Int) {
        this.x = newX
        this.y = newY
        this.width = newWidth
        this.height = newHeight
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!this.visible || !this.active) return false
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (!this.visible || !this.active) return false
        return super.charTyped(codePoint, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!this.visible || !this.active) return false
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!this.visible || !this.active) return false
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }
}
