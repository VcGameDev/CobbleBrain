package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.*

class NodeInspectorWidget(
    val node: NodeData,
    val panelX: Int,
    val panelY: Int,
    val panelWidth: Int = 140,
    val panelHeight: Int,
    val font: Font,
    val onClose: () -> Unit,
    val onDataChanged: () -> Unit,
    val onOpenConstruction: ((NodeData) -> Unit)? = null,
    val onOpenVariableSelector: (((StoryVariable) -> Unit) -> Unit)? = null,
    val onOpenActionTriggerPicker: ((isAction: Boolean, onSelect: (String) -> Unit) -> Unit)? = null,
    val onOpenPokemonConfig: ((NodeData) -> Unit)? = null,
    val onOpenResourcePicker: ((ResourcePickerType, (String) -> Unit) -> Unit)? = null,
    val onOpenEntityConfig: ((NodeData) -> Unit)? = null,
    val onDeleteNode: ((NodeData) -> Unit)? = null,
    val onDissociateNode: ((NodeData) -> Unit)? = null,
    val onOpenProfileModal: ((NodeData) -> Unit)? = null,
    val onOpenAIDialogueModal: ((NodeData) -> Unit)? = null,
    val onOpenAnimationSelector: ((node: NodeData, onSelected: (String) -> Unit) -> Unit)? = null,
    val onOpenTextureSelector: ((node: NodeData, onSelected: (String) -> Unit) -> Unit)? = null,
    val onOpenCoordinateModal: ((node: NodeData, onSaved: () -> Unit) -> Unit)? = null,
    val projectVariables: List<StoryVariable> = emptyList()
) {
    private data class InspectorLabel(val text: String, val relY: Int, val color: Int = 0xFFA0A0A0.toInt())
    private data class InspectorWidgetItem(val widget: GuiEventListener, val relX: Int, val relY: Int, val width: Int, val height: Int)

    val childrenWidgets = mutableListOf<GuiEventListener>()
    private val widgetItems = mutableListOf<InspectorWidgetItem>()
    private val labels = mutableListOf<InspectorLabel>()

    private var focusedEditBox: EditBox? = null
    private var scrollOffset: Double = 0.0
    private var totalContentHeight: Double = 0.0

    private val closeBtn: Button

    init {
        closeBtn = Button.builder(Component.literal("✖")) {
            onClose()
        }.bounds(panelX + panelWidth - 20, panelY + 2, 16, 16).build()

        buildUi()
    }

    fun buildUi() {
        childrenWidgets.clear()
        widgetItems.clear()
        labels.clear()

        val inputX = panelX + 6
        val inputW = panelWidth - 12
        var relY = 4

        // 0. DIAGNOSTIC EXECUTION ALERT BANNER
        val storyId = (node.params["__story_id__"] ?: "").ifBlank { "default_story" }
        val debugStatus = vito.cobblebrain.engine.StoryDebugger.getNodeStatus(storyId, node.id)
        val debugErrMsg = vito.cobblebrain.engine.StoryDebugger.getNodeErrorMessage(storyId, node.id)

        if (debugStatus == vito.cobblebrain.engine.NodeExecutionStatus.FAILED || debugStatus == vito.cobblebrain.engine.NodeExecutionStatus.FALLBACK_TRIGGERED || debugErrMsg != null) {
            val isErr = debugStatus == vito.cobblebrain.engine.NodeExecutionStatus.FAILED || debugErrMsg != null
            val cardBorder = if (isErr) 0xFFEF4444.toInt() else 0xFFF59E0B.toInt()
            val cardTitle = if (isErr) "⚠️ Block Execution Failed" else "⚠ Fallback Path Triggered"

            labels.add(InspectorLabel(cardTitle, relY, cardBorder))
            relY += 12

            val errorMsg = debugErrMsg ?: "Runtime exception during block execution"
            val shortMsg = font.plainSubstrByWidth(errorMsg, inputW)
            labels.add(InspectorLabel(shortMsg, relY, 0xFFFCA5A5.toInt()))
            relY += 14

            val bDismiss = Button.builder(Component.literal("Dismiss / Retry Diagnostic")) {
                vito.cobblebrain.engine.StoryDebugger.dismissNodeError(storyId, node.id)
                buildUi()
                onDataChanged()
            }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
            addWidgetItem(bDismiss, relY, 16)
            relY += 24
        }

        // 1. Node Title Field
        labels.add(InspectorLabel("Title:", relY))
        relY += 12

        val tEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Title"))
        tEdit.setMaxLength(2000)
        tEdit.value = node.title
        tEdit.setResponder { valText ->
            node.title = valText
            onDataChanged()
        }
        addWidgetItem(tEdit, relY, 16)
        relY += 22

        // 2. Dynamic Contextual UI for Node Types
        when (node.nodeType) {

            NodeType.ACTION -> {
                val currentActionId = node.params["actionSubtype"] ?: "MESSAGE"
                val actionDef = ActionRegistry.find(currentActionId)

                // Active Type Card & Change Type Button
                labels.add(InspectorLabel("Current Action:", relY))
                relY += 12

                val changeBtn = Button.builder(Component.literal("🔄 ${actionDef.icon} ${actionDef.name}")) {
                    onOpenActionTriggerPicker?.invoke(true) { chosenId ->
                        node.params["actionSubtype"] = chosenId
                        val newDef = ActionRegistry.find(chosenId)
                        if (node.title.isBlank() || node.title == "New Action" || ActionRegistry.actions.any { node.title.contains(it.name) }) {
                            node.title = newDef.name
                        }
                        buildUi()
                        onDataChanged()
                    }
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(changeBtn, relY, 16)
                relY += 22

                // Strictly Contextual Inputs for Active Action
                when (actionDef.id) {
                    "LOOK_AT", "LOOK_AT_BLOCK" -> {
                        val operationMode = node.params["operationMode"] ?: "APPLY_LOOK"
                        val halfW = (inputW - 2) / 2

                        // 1. Operation Mode Selector
                        labels.add(InspectorLabel("Operation Mode:", relY))
                        relY += 12
                        val bApply = Button.builder(Component.literal("🎯 Apply Look")) {
                            node.params["operationMode"] = "APPLY_LOOK"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                        if (operationMode == "APPLY_LOOK") bApply.active = false

                        val bReset = Button.builder(Component.literal("🔄 Reset AI Look")) {
                            node.params["operationMode"] = "RESET_LOOK"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + halfW + 2, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                        if (operationMode == "RESET_LOOK") bReset.active = false

                        addWidgetItem(bApply, relY, 14); addWidgetItem(bReset, relY, 14)
                        relY += 20

                        // 2. Subject Fields (Who is performing the look)
                        val subjectType = node.params["subjectType"] ?: node.params["targetType"] ?: "PLAYER_POKEMON"
                        labels.add(InspectorLabel("Subject Type:", relY))
                        relY += 12
                        val bPoke = Button.builder(Component.literal("🐾 Cobblemon")) {
                            node.params["subjectType"] = "PLAYER_POKEMON"
                            node.params["targetType"] = "PLAYER_POKEMON"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                        if (subjectType == "PLAYER_POKEMON") bPoke.active = false

                        val bNpc = Button.builder(Component.literal("👤 NPC / Mob")) {
                            node.params["subjectType"] = "NPC_TAG"
                            node.params["targetType"] = "NPC_TAG"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + halfW + 2, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                        if (subjectType == "NPC_TAG") bNpc.active = false

                        addWidgetItem(bPoke, relY, 14); addWidgetItem(bNpc, relY, 14)
                        relY += 20

                        if (subjectType == "PLAYER_POKEMON") {
                            labels.add(InspectorLabel("Party Slot (1 - 6):", relY))
                            relY += 12
                            val rawSlot = node.params["subjectIdentifier"] ?: node.params["targetIdentifier"] ?: "0"
                            val curSlot = ((rawSlot.toIntOrNull() ?: 0) + 1).toString()
                            val fSlot = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Slot", curSlot) {
                                val slotIdx = (it.toIntOrNull() ?: 1).coerceIn(1, 6) - 1
                                node.params["subjectIdentifier"] = slotIdx.toString()
                                node.params["targetIdentifier"] = slotIdx.toString()
                            }
                            addWidgetItem(fSlot, relY, 16)
                            relY += 22
                        } else {
                            labels.add(InspectorLabel("Subject Story Tag:", relY))
                            relY += 12
                            val fTag = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                            fTag.setHint(Component.literal("§8e.g. guide_npc, quest_boss"))
                            fTag.value = node.params["subjectIdentifier"] ?: node.params["targetIdentifier"] ?: ""
                            fTag.setResponder {
                                node.params["subjectIdentifier"] = it
                                node.params["targetIdentifier"] = it
                                onDataChanged()
                            }
                            addWidgetItem(fTag, relY, 16)
                            relY += 22
                        }

                        // 3. APPLY_LOOK Controls
                        if (operationMode == "APPLY_LOOK") {
                            // Look Mode
                            val lookModes = listOf(
                                "TOWARDS_REFERENCE" to "🎯 Towards Reference",
                                "AWAY_FROM_REFERENCE" to "↪️ Away From Ref (180°)",
                                "SKY" to "☁️ Sky (Pitch -90°)",
                                "GROUND" to "⬇️ Ground (Pitch +90°)",
                                "OPPOSITE_SELF" to "🔄 Opposite Self (180°)"
                            )
                            val curLookMode = node.params["lookMode"] ?: "TOWARDS_REFERENCE"
                            val curModeLabel = lookModes.find { it.first == curLookMode }?.second ?: "🎯 Towards Reference"

                            labels.add(InspectorLabel("Direction Mode:", relY))
                            relY += 12
                            val bMode = Button.builder(Component.literal(curModeLabel)) {
                                val curIdx = lookModes.indexOfFirst { it.first == curLookMode }.coerceAtLeast(0)
                                val nextIdx = (curIdx + 1) % lookModes.size
                                node.params["lookMode"] = lookModes[nextIdx].first
                                buildUi()
                                onDataChanged()
                            }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                            addWidgetItem(bMode, relY, 16)
                            relY += 22

                            // Reference Fields (if Towards or Away)
                            if (curLookMode == "TOWARDS_REFERENCE" || curLookMode == "AWAY_FROM_REFERENCE") {
                                val refType = node.params["referenceType"] ?: "PLAYER"
                                val thirdW = (inputW - 4) / 3

                                labels.add(InspectorLabel("Reference Target:", relY))
                                relY += 12
                                val bRefPlayer = Button.builder(Component.literal("👤 Player")) {
                                    node.params["referenceType"] = "PLAYER"
                                    buildUi()
                                    onDataChanged()
                                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), thirdW, 14).build()
                                if (refType == "PLAYER") bRefPlayer.active = false

                                val bRefMob = Button.builder(Component.literal("👾 Mob")) {
                                    node.params["referenceType"] = "MOB_TAG"
                                    buildUi()
                                    onDataChanged()
                                }.bounds(inputX + thirdW + 2, (panelY + 20 + relY - scrollOffset).toInt(), thirdW, 14).build()
                                if (refType == "MOB_TAG") bRefMob.active = false

                                val bRefCoords = Button.builder(Component.literal("📍 Coords")) {
                                    node.params["referenceType"] = "COORDINATES"
                                    buildUi()
                                    onDataChanged()
                                }.bounds(inputX + (thirdW + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), thirdW, 14).build()
                                if (refType == "COORDINATES") bRefCoords.active = false

                                addWidgetItem(bRefPlayer, relY, 14); addWidgetItem(bRefMob, relY, 14); addWidgetItem(bRefCoords, relY, 14)
                                relY += 20

                                if (refType == "MOB_TAG") {
                                    labels.add(InspectorLabel("Target Mob Story Tag:", relY))
                                    relY += 12
                                    val fRefTag = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Target Tag"))
                                    fRefTag.setHint(Component.literal("§8Target Mob Story Tag"))
                                    fRefTag.value = node.params["referenceIdentifier"] ?: ""
                                    fRefTag.setResponder { node.params["referenceIdentifier"] = it; onDataChanged() }
                                    addWidgetItem(fRefTag, relY, 16)
                                    relY += 22
                                } else if (refType == "COORDINATES") {
                                    val initCoord = node.params["referenceIdentifier"]?.ifBlank { node.params["coordinates"] ?: "~ ~ ~" } ?: "~ ~ ~"
                                    relY = addCoordinateInputSection("Target Coordinates:", initCoord, inputX, inputW, relY, showSafetyControls = false) {
                                        node.params["referenceIdentifier"] = it
                                        node.params["coordinates"] = it
                                    }
                                }
                            }

                            // Duration Controls
                            val durationMode = node.params["durationMode"] ?: "TEMPORARY"
                            labels.add(InspectorLabel("Duration Mode:", relY))
                            relY += 12
                            val bTemp = Button.builder(Component.literal("⏳ Temporary")) {
                                node.params["durationMode"] = "TEMPORARY"
                                buildUi()
                                onDataChanged()
                            }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                            if (durationMode == "TEMPORARY") bTemp.active = false

                            val bIndef = Button.builder(Component.literal("♾️ Indefinite")) {
                                node.params["durationMode"] = "INDEFINITE"
                                buildUi()
                                onDataChanged()
                            }.bounds(inputX + halfW + 2, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                            if (durationMode == "INDEFINITE") bIndef.active = false

                            addWidgetItem(bTemp, relY, 14); addWidgetItem(bIndef, relY, 14)
                            relY += 20

                            if (durationMode == "TEMPORARY") {
                                labels.add(InspectorLabel("Duration (Ticks):", relY))
                                relY += 12
                                val curTicks = node.params["durationTicks"] ?: "60"
                                val fTicks = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Ticks", curTicks) {
                                    node.params["durationTicks"] = it
                                }
                                addWidgetItem(fTicks, relY, 16)
                                relY += 22

                                val isWait = node.params["waitForCompletion"] == "true"
                                val bWait = Button.builder(Component.literal(if (isWait) "☑ Wait For Completion" else "☐ Wait For Completion")) {
                                    node.params["waitForCompletion"] = if (isWait) "false" else "true"
                                    buildUi()
                                    onDataChanged()
                                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                                addWidgetItem(bWait, relY, 16)
                                relY += 22
                            }
                        } else {
                            labels.add(InspectorLabel("ℹ️ Clears active look lock and restores default wandering AI.", relY, 0xFF38BDF8.toInt()))
                            relY += 20
                        }
                    }

                    "ANIMATION", "ANIMATION_BLOCK" -> {
                        val animSystem = node.params["animationSystem"] ?: "COBBLEMON"
                        val halfW = (inputW - 2) / 2

                        labels.add(InspectorLabel("Animation System:", relY))
                        relY += 12
                        val bCobble = Button.builder(Component.literal("🐾 Cobblemon")) {
                            node.params["animationSystem"] = "COBBLEMON"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                        if (animSystem == "COBBLEMON") bCobble.active = false

                        val bNpc = Button.builder(Component.literal("👤 NPC / Mob")) {
                            node.params["animationSystem"] = "NPC"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + halfW + 2, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                        if (animSystem == "NPC") bNpc.active = false

                        addWidgetItem(bCobble, relY, 14); addWidgetItem(bNpc, relY, 14)
                        relY += 20

                        if (animSystem == "COBBLEMON") {
                            labels.add(InspectorLabel("Party Slot (1 - 6):", relY))
                            relY += 12
                            val curSlot = ((node.params["targetIdentifier"]?.toIntOrNull() ?: 0) + 1).toString()
                            val fSlot = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Slot", curSlot) {
                                val slotIdx = (it.toIntOrNull() ?: 1).coerceIn(1, 6) - 1
                                node.params["targetIdentifier"] = slotIdx.toString()
                            }
                            addWidgetItem(fSlot, relY, 16)
                            relY += 22
                        } else {
                            labels.add(InspectorLabel("Entity Story Tag:", relY))
                            relY += 12
                            val fTag = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                            fTag.setHint(Component.literal("§8Entity Story Tag (e.g. guard_1)"))
                            fTag.value = node.params["targetIdentifier"] ?: ""
                            fTag.setResponder { node.params["targetIdentifier"] = it; onDataChanged() }
                            addWidgetItem(fTag, relY, 16)
                            relY += 22
                        }

                        labels.add(InspectorLabel("Animation / Pose ID:", relY))
                        relY += 12
                        val fAnim = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Animation ID"))
                        fAnim.value = node.params["animationId"] ?: if (animSystem == "COBBLEMON") "battle_idle" else "CROUCHING"
                        fAnim.setResponder { node.params["animationId"] = it; onDataChanged() }
                        addWidgetItem(fAnim, relY, 16)
                        relY += 20

                        val bBrowse = Button.builder(Component.literal("🎬 Browse Animations...")) {
                            onOpenAnimationSelector?.invoke(node) { chosenId ->
                                node.params["animationId"] = chosenId
                                buildUi()
                                onDataChanged()
                            }
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(bBrowse, relY, 16)
                        relY += 22

                        val durMode = node.params["durationMode"] ?: "TEMPORARY"
                        labels.add(InspectorLabel("Duration Mode:", relY))
                        relY += 12
                        val bTemp = Button.builder(Component.literal("⏳ Temporary")) {
                            node.params["durationMode"] = "TEMPORARY"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                        if (durMode == "TEMPORARY") bTemp.active = false

                        val bPerm = Button.builder(Component.literal("♾️ Permanent")) {
                            node.params["durationMode"] = "PERMANENT"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + halfW + 2, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                        if (durMode == "PERMANENT") bPerm.active = false

                        addWidgetItem(bTemp, relY, 14); addWidgetItem(bPerm, relY, 14)
                        relY += 20

                        if (durMode == "TEMPORARY") {
                            labels.add(InspectorLabel("Duration in Ticks (20t = 1s):", relY))
                            relY += 12
                            val fDur = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Ticks", node.params["durationTicks"] ?: "60") {
                                node.params["durationTicks"] = it
                            }
                            addWidgetItem(fDur, relY, 16)
                            relY += 22
                        }

                        val waitForComplete = node.params["waitForCompletion"] == "true"
                        labels.add(InspectorLabel("Flow Execution:", relY))
                        relY += 12
                        val bWait = Button.builder(Component.literal(if (waitForComplete) "⏳ Delay OUT Until Duration Ends" else "⚡ Instant OUT Port Advance")) {
                            node.params["waitForCompletion"] = if (waitForComplete) "false" else "true"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(bWait, relY, 16)
                        relY += 22

                        val overrideAi = node.params["overridePriority"] != "false"
                        labels.add(InspectorLabel("AI Wander Lock:", relY))
                        relY += 12
                        val bOverride = Button.builder(Component.literal(if (overrideAi) "🔒 Lock AI (Prevent Wander Reset)" else "🔓 Normal AI (May Cancel Pose)")) {
                            node.params["overridePriority"] = if (overrideAi) "false" else "true"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(bOverride, relY, 16)
                        relY += 22
                    }

                    "SET_ENTITY_TEXTURE", "TEXTURE_BLOCK", "ENTITY_TEXTURE" -> {
                        val targetType = node.params["targetType"] ?: "PLAYER_POKEMON"
                        val halfW = (inputW - 2) / 2

                        labels.add(InspectorLabel("Target Entity Type:", relY))
                        relY += 12
                        val bPoke = Button.builder(Component.literal("🐾 Cobblemon")) {
                            node.params["targetType"] = "PLAYER_POKEMON"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                        if (targetType == "PLAYER_POKEMON") bPoke.active = false

                        val bNpc = Button.builder(Component.literal("👤 NPC / Mob")) {
                            node.params["targetType"] = "NPC_TAG"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + halfW + 2, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                        if (targetType == "NPC_TAG") bNpc.active = false

                        addWidgetItem(bPoke, relY, 14); addWidgetItem(bNpc, relY, 14)
                        relY += 20

                        if (targetType == "PLAYER_POKEMON") {
                            labels.add(InspectorLabel("Party Slot (1 - 6):", relY))
                            relY += 12
                            val curSlot = ((node.params["targetIdentifier"]?.toIntOrNull() ?: 0) + 1).toString()
                            val fSlot = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Slot", curSlot) {
                                val slotIdx = (it.toIntOrNull() ?: 1).coerceIn(1, 6) - 1
                                node.params["targetIdentifier"] = slotIdx.toString()
                            }
                            addWidgetItem(fSlot, relY, 16)
                            relY += 22
                        } else {
                            labels.add(InspectorLabel("Entity Story Tag:", relY))
                            relY += 12
                            val fTag = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                            fTag.setHint(Component.literal("§8Entity Story Tag (e.g. boss_zombie)"))
                            fTag.value = node.params["targetIdentifier"] ?: ""
                            fTag.setResponder { node.params["targetIdentifier"] = it; onDataChanged() }
                            addWidgetItem(fTag, relY, 16)
                            relY += 22
                        }

                        val isReset = node.params["resetToDefault"] == "true"
                        labels.add(InspectorLabel("Texture Action:", relY))
                        relY += 12
                        val bReset = Button.builder(Component.literal(if (isReset) "🔄 Reset to Default Appearance" else "🎨 Apply Custom Texture")) {
                            node.params["resetToDefault"] = if (isReset) "false" else "true"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(bReset, relY, 16)
                        relY += 22

                        if (!isReset) {
                            labels.add(InspectorLabel("Texture File (.png):", relY))
                            relY += 12
                            val fTex = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Texture Name"))
                            fTex.setHint(Component.literal("§8e.g. custom_skin.png"))
                            fTex.value = node.params["textureName"] ?: "custom_texture.png"
                            fTex.setResponder { node.params["textureName"] = it; onDataChanged() }
                            addWidgetItem(fTex, relY, 16)
                            relY += 20

                            val bBrowse = Button.builder(Component.literal("🎨 Browse Story Textures...")) {
                                onOpenTextureSelector?.invoke(node) { chosen ->
                                    node.params["textureName"] = chosen
                                    buildUi()
                                    onDataChanged()
                                }
                            }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                            addWidgetItem(bBrowse, relY, 16)
                            relY += 22
                        }
                    }

                    "TAG_BLOCK", "MANAGE_TAG", "TAG_ACTION", "TAG" -> {
                        val category = node.params["targetCategory"] ?: "ENTITY"
                        val thirdW = (inputW - 4) / 3

                        // 1. Target Category
                        labels.add(InspectorLabel("Target Category:", relY))
                        relY += 12
                        val bCatEnt = Button.builder(Component.literal("👾 Entity")) {
                            node.params["targetCategory"] = "ENTITY"
                            node.params["targetSelector"] = "CLOSEST_MOB"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), thirdW, 14).build()
                        if (category == "ENTITY") bCatEnt.active = false

                        val bCatBlock = Button.builder(Component.literal("🧱 Block")) {
                            node.params["targetCategory"] = "WORLD_BLOCK"
                            node.params["targetSelector"] = "LOOKING_AT_BLOCK"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + thirdW + 2, (panelY + 20 + relY - scrollOffset).toInt(), thirdW, 14).build()
                        if (category == "WORLD_BLOCK") bCatBlock.active = false

                        val bCatPlayer = Button.builder(Component.literal("👤 Player")) {
                            node.params["targetCategory"] = "PLAYER"
                            node.params["targetSelector"] = "INTERACTING_PLAYER"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + (thirdW + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), thirdW, 14).build()
                        if (category == "PLAYER") bCatPlayer.active = false

                        addWidgetItem(bCatEnt, relY, 14); addWidgetItem(bCatBlock, relY, 14); addWidgetItem(bCatPlayer, relY, 14)
                        relY += 20

                        // 2. Target Selector & Identifier
                        when (category.uppercase()) {
                            "ENTITY", "MOB" -> {
                                val selectors = listOf(
                                    "CLOSEST_MOB" to "📍 Nearest Mob in Radius",
                                    "LOOKING_AT_MOB" to "🎯 Crosshair Target Mob",
                                    "BY_EXISTING_TAG" to "🏷️ By Existing Tag",
                                    "PLAYER_POKEMON_SLOT" to "🐾 Player Pokémon Slot"
                                )
                                val curSelector = node.params["targetSelector"] ?: "CLOSEST_MOB"
                                val selLabel = selectors.find { it.first == curSelector }?.second ?: "📍 Nearest Mob in Radius"

                                labels.add(InspectorLabel("Target Selector:", relY))
                                relY += 12
                                val bSel = Button.builder(Component.literal(selLabel)) {
                                    val curIdx = selectors.indexOfFirst { it.first == curSelector }.coerceAtLeast(0)
                                    val nextIdx = (curIdx + 1) % selectors.size
                                    node.params["targetSelector"] = selectors[nextIdx].first
                                    buildUi()
                                    onDataChanged()
                                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                                addWidgetItem(bSel, relY, 16)
                                relY += 22

                                when (curSelector) {
                                    "CLOSEST_MOB" -> {
                                        labels.add(InspectorLabel("Search Radius (Blocks):", relY))
                                        relY += 12
                                        val fRad = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Radius", node.params["selectorIdentifier"] ?: "16") {
                                            node.params["selectorIdentifier"] = it
                                        }
                                        addWidgetItem(fRad, relY, 16)
                                        relY += 22
                                    }
                                    "LOOKING_AT_MOB" -> {
                                        labels.add(InspectorLabel("Max Raycast Distance:", relY))
                                        relY += 12
                                        val fDist = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Max Dist", node.params["selectorIdentifier"] ?: "32") {
                                            node.params["selectorIdentifier"] = it
                                        }
                                        addWidgetItem(fDist, relY, 16)
                                        relY += 22
                                    }
                                    "BY_EXISTING_TAG" -> {
                                        labels.add(InspectorLabel("Existing Story Tag:", relY))
                                        relY += 12
                                        val fExist = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Existing Tag"))
                                        fExist.setHint(Component.literal("§8Target mob existing tag"))
                                        fExist.value = node.params["selectorIdentifier"] ?: ""
                                        fExist.setResponder { node.params["selectorIdentifier"] = it; onDataChanged() }
                                        addWidgetItem(fExist, relY, 16)
                                        relY += 22
                                    }
                                    "PLAYER_POKEMON_SLOT" -> {
                                        labels.add(InspectorLabel("Party Slot (1 - 6):", relY))
                                        relY += 12
                                        val curSlot = ((node.params["selectorIdentifier"]?.toIntOrNull() ?: 0) + 1).toString()
                                        val fSlot = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Slot", curSlot) {
                                            val slotIdx = (it.toIntOrNull() ?: 1).coerceIn(1, 6) - 1
                                            node.params["selectorIdentifier"] = slotIdx.toString()
                                        }
                                        addWidgetItem(fSlot, relY, 16)
                                        relY += 22
                                    }
                                }
                            }
                            "WORLD_BLOCK", "BLOCK" -> {
                                val selectors = listOf(
                                    "LOOKING_AT_BLOCK" to "🎯 Crosshair Target Block",
                                    "BLOCK_UNDER_PLAYER" to "⬇️ Block Under Player",
                                    "COORDINATES" to "📍 Specific Coordinates"
                                )
                                val curSelector = node.params["targetSelector"] ?: "LOOKING_AT_BLOCK"
                                val selLabel = selectors.find { it.first == curSelector }?.second ?: "🎯 Crosshair Target Block"

                                labels.add(InspectorLabel("Block Selector:", relY))
                                relY += 12
                                val bSel = Button.builder(Component.literal(selLabel)) {
                                    val curIdx = selectors.indexOfFirst { it.first == curSelector }.coerceAtLeast(0)
                                    val nextIdx = (curIdx + 1) % selectors.size
                                    node.params["targetSelector"] = selectors[nextIdx].first
                                    buildUi()
                                    onDataChanged()
                                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                                addWidgetItem(bSel, relY, 16)
                                relY += 22

                                if (curSelector == "LOOKING_AT_BLOCK") {
                                    labels.add(InspectorLabel("Max Raycast Distance:", relY))
                                    relY += 12
                                    val fDist = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Max Dist", node.params["selectorIdentifier"] ?: "32") {
                                        node.params["selectorIdentifier"] = it
                                    }
                                    addWidgetItem(fDist, relY, 16)
                                    relY += 22
                                } else if (curSelector == "COORDINATES") {
                                    val initCoord = node.params["selectorIdentifier"]?.ifBlank { "~ ~ ~" } ?: "~ ~ ~"
                                    relY = addCoordinateInputSection("Target Block Coordinates:", initCoord, inputX, inputW, relY, showSafetyControls = false) {
                                        node.params["selectorIdentifier"] = it
                                    }
                                }
                            }
                            "PLAYER" -> {
                                labels.add(InspectorLabel("Target: Interacting Story Player", relY, 0xFF38BDF8.toInt()))
                                relY += 16
                            }
                        }

                        // 3. Operation Mode
                        val operation = node.params["operation"] ?: "ADD_TAG"
                        labels.add(InspectorLabel("Tag Operation:", relY))
                        relY += 12

                        val bOpAdd = Button.builder(Component.literal("➕ Add")) {
                            node.params["operation"] = "ADD_TAG"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), thirdW, 14).build()
                        if (operation == "ADD_TAG") bOpAdd.active = false

                        val bOpRem = Button.builder(Component.literal("➖ Remove")) {
                            node.params["operation"] = "REMOVE_TAG"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + thirdW + 2, (panelY + 20 + relY - scrollOffset).toInt(), thirdW, 14).build()
                        if (operation == "REMOVE_TAG") bOpRem.active = false

                        val bOpClr = Button.builder(Component.literal("🧹 Clear")) {
                            node.params["operation"] = "CLEAR_TAGS"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + (thirdW + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), thirdW, 14).build()
                        if (operation == "CLEAR_TAGS") bOpClr.active = false

                        addWidgetItem(bOpAdd, relY, 14); addWidgetItem(bOpRem, relY, 14); addWidgetItem(bOpClr, relY, 14)
                        relY += 20

                        // 4. Tag Name Field (if not CLEAR_TAGS)
                        if (operation != "CLEAR_TAGS") {
                            labels.add(InspectorLabel("Story Tag Name:", relY))
                            relY += 12
                            val fTag = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                            fTag.setHint(Component.literal("§8e.g. quest_boss, active_portal"))
                            fTag.value = node.params["tagName"] ?: ""
                            fTag.setResponder { node.params["tagName"] = it; onDataChanged() }
                            addWidgetItem(fTag, relY, 16)
                            relY += 22
                        } else {
                            labels.add(InspectorLabel("ℹ️ Clears all tags from the resolved target.", relY, 0xFFF59E0B.toInt()))
                            relY += 20
                        }
                    }
                    "AI_DIALOGUE" -> {
                        labels.add(InspectorLabel("AI Dialogue Configuration:", relY))
                        relY += 12
                        val aiBtn = Button.builder(Component.literal("🤖 Configure AI Dialogue...")) {
                            onOpenAIDialogueModal?.invoke(node)
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(aiBtn, relY, 16)
                        relY += 22
                    }

                    "SPAWN_STRUCTURE" -> {
                        val currentStructure = node.params["structureId"] ?: "minecraft:village_plains"
                        labels.add(InspectorLabel("Structure (Catalog):", relY))
                        relY += 12

                        val pickBtn = Button.builder(Component.literal("🏛️ $currentStructure")) {
                            onOpenResourcePicker?.invoke(ResourcePickerType.STRUCTURE) { chosen ->
                                node.params["structureId"] = chosen
                                buildUi()
                                onDataChanged()
                            }
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(pickBtn, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Position X, Y, Z:", relY))
                        relY += 12
                        val colW = (inputW - 4) / 3
                        val fx = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), colW, 16, Component.literal("X"))
                        fx.value = node.params["posX"] ?: "~"
                        fx.setResponder { node.params["posX"] = it; onDataChanged() }
                        val fy = EditBox(font, inputX + colW + 2, (panelY + 20 + relY - scrollOffset).toInt(), colW, 16, Component.literal("Y"))
                        fy.value = node.params["posY"] ?: "~"
                        fy.setResponder { node.params["posY"] = it; onDataChanged() }
                        val fz = EditBox(font, inputX + (colW + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), colW, 16, Component.literal("Z"))
                        fz.value = node.params["posZ"] ?: "~"
                        fz.setResponder { node.params["posZ"] = it; onDataChanged() }
                        addWidgetItem(fx, relY, 16); addWidgetItem(fy, relY, 16); addWidgetItem(fz, relY, 16)
                        relY += 22
                    }

                    "TELEPORT" -> {
                        val targetMode = node.params["targetMode"] ?: "PLAYER"
                        val btnW = (inputW - 2) / 2
                        labels.add(InspectorLabel("Target Entity:", relY))
                        relY += 12
                        val bPlayer = Button.builder(Component.literal("Player")) {
                            node.params["targetMode"] = "PLAYER"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (targetMode == "PLAYER") bPlayer.active = false

                        val bTag = Button.builder(Component.literal("Tagged Entity")) {
                            node.params["targetMode"] = "STORY_TAG"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + btnW + 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (targetMode == "STORY_TAG") bTag.active = false

                        addWidgetItem(bPlayer, relY, 14); addWidgetItem(bTag, relY, 14)
                        relY += 18

                        if (targetMode == "STORY_TAG") {
                            labels.add(InspectorLabel("Target Story Tag:", relY))
                            relY += 12
                            val fTag = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                            fTag.value = node.params["targetStoryTag"] ?: ""
                            fTag.setHint(Component.literal("§8e.g. quest_boss, npc_guide"))
                            fTag.setResponder { node.params["targetStoryTag"] = it; onDataChanged() }
                            addWidgetItem(fTag, relY, 16)
                            relY += 22
                        }

                        val initDest = node.params["coordinates"]?.ifBlank {
                            node.params["destTag"]?.ifBlank {
                                "${node.params["destX"] ?: "~"} ${node.params["destY"] ?: "~"} ${node.params["destZ"] ?: "~"}"
                            }
                        } ?: "${node.params["destX"] ?: "~"} ${node.params["destY"] ?: "~"} ${node.params["destZ"] ?: "~"}"

                        relY = addCoordinateInputSection("Destination Coordinates / Tag:", initDest, inputX, inputW, relY) {
                            node.params["coordinates"] = it
                            if (it.startsWith("@")) {
                                node.params["destTag"] = it.removePrefix("@").trim()
                            }
                        }
                    }

                    "CHANGE_WEATHER" -> {
                        val currentWeather = (node.params["weatherType"] ?: "CLEAR").uppercase()
                        val btnW = (inputW - 4) / 3
                        labels.add(InspectorLabel("Weather Type:", relY))
                        relY += 12

                        val bClear = Button.builder(Component.literal("Clear")) { node.params["weatherType"] = "CLEAR"; buildUi(); onDataChanged() }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (currentWeather == "CLEAR") bClear.active = false
                        val bRain = Button.builder(Component.literal("Rain")) { node.params["weatherType"] = "RAIN"; buildUi(); onDataChanged() }.bounds(inputX + btnW + 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (currentWeather == "RAIN") bRain.active = false
                        val bThunder = Button.builder(Component.literal("Thunder")) { node.params["weatherType"] = "THUNDER"; buildUi(); onDataChanged() }.bounds(inputX + (btnW + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (currentWeather == "THUNDER") bThunder.active = false

                        addWidgetItem(bClear, relY, 14); addWidgetItem(bRain, relY, 14); addWidgetItem(bThunder, relY, 14)
                        relY += 20

                        labels.add(InspectorLabel("Duration (Ticks):", relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Ticks", node.params["durationTicks"] ?: "6000") { node.params["durationTicks"] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "SET_TIME_OF_DAY" -> {
                        labels.add(InspectorLabel("Time (Ticks):", relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Ticks", node.params["timeTicks"] ?: "1000") { node.params["timeTicks"] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        val bW = (inputW - 4) / 3
                        val bDay = Button.builder(Component.literal("Day")) { node.params["timeTicks"] = "1000"; buildUi(); onDataChanged() }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        val bNoon = Button.builder(Component.literal("Noon")) { node.params["timeTicks"] = "6000"; buildUi(); onDataChanged() }.bounds(inputX + bW + 2, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        val bNight = Button.builder(Component.literal("Night")) { node.params["timeTicks"] = "13000"; buildUi(); onDataChanged() }.bounds(inputX + (bW + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        addWidgetItem(bDay, relY, 14); addWidgetItem(bNoon, relY, 14); addWidgetItem(bNight, relY, 14)
                        relY += 20
                    }

                    "SPAWN_BLOCK" -> {
                        labels.add(InspectorLabel("Block ID:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Block"))
                        f1.value = node.params["blockId"] ?: "minecraft:stone"
                        f1.setResponder { node.params["blockId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        val initCoords = node.params["coordinates"]?.ifBlank {
                            "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"
                        } ?: "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"

                        relY = addCoordinateInputSection("Block Coordinates:", initCoords, inputX, inputW, relY) {
                            node.params["coordinates"] = it
                        }
                    }

                    "MODIFY_BLOCK_PROPERTY" -> {
                        labels.add(InspectorLabel("Property Key:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Prop"))
                        f1.value = node.params["propertyKey"] ?: "open"
                        f1.setResponder { node.params["propertyKey"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Property Value:", relY))
                        relY += 12
                        val f2 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Val"))
                        f2.value = node.params["propertyValue"] ?: "true"
                        f2.setResponder { node.params["propertyValue"] = it; onDataChanged() }
                        addWidgetItem(f2, relY, 16)
                        relY += 22
                    }

                    "SPAWN_ENTITY" -> {
                        val currentEntity = node.params["entityId"] ?: "minecraft:villager"
                        labels.add(InspectorLabel("Entity (Catalog):", relY))
                        relY += 12

                        val pickBtn = Button.builder(Component.literal("👾 $currentEntity")) {
                            onOpenResourcePicker?.invoke(ResourcePickerType.ENTITY) { chosen ->
                                node.params["entityId"] = chosen
                                buildUi()
                                onDataChanged()
                            }
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(pickBtn, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Story Tag (Optional):", relY))
                        relY += 12
                        val tagBox = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                        tagBox.value = node.params["storyTag"] ?: node.params["entity_storyTag"] ?: ""
                        tagBox.setHint(Component.literal("§8e.g. quest_boss, npc_guide"))
                        tagBox.setResponder {
                            node.params["storyTag"] = it
                            node.params["entity_storyTag"] = it
                            onDataChanged()
                        }
                        addWidgetItem(tagBox, relY, 16)
                        relY += 22

                        val initCoords = node.params["coordinates"]?.ifBlank {
                            "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"
                        } ?: "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"

                        relY = addCoordinateInputSection("Spawn Coordinates:", initCoords, inputX, inputW, relY) {
                            node.params["coordinates"] = it
                        }

                        val cfgBtn = Button.builder(Component.literal("⚙️ Configure Entity...")) {
                            onOpenEntityConfig?.invoke(node)
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(cfgBtn, relY, 16)
                        relY += 22
                    }

                    "KILL_ENTITY" -> {
                        val targetMode = node.params["targetMode"] ?: "AREA_NEAREST"
                        val btnW = (inputW - 2) / 2
                        labels.add(InspectorLabel("Target By:", relY))
                        relY += 12
                        val bArea = Button.builder(Component.literal("Area / Selector")) {
                            node.params["targetMode"] = "AREA_NEAREST"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (targetMode == "AREA_NEAREST") bArea.active = false

                        val bTag = Button.builder(Component.literal("Story Tag")) {
                            node.params["targetMode"] = "STORY_TAG"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + btnW + 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (targetMode == "STORY_TAG") bTag.active = false

                        addWidgetItem(bArea, relY, 14); addWidgetItem(bTag, relY, 14)
                        relY += 18

                        if (targetMode == "STORY_TAG") {
                            labels.add(InspectorLabel("Target Story Tag:", relY))
                            relY += 12
                            val fTag = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                            fTag.value = node.params["targetStoryTag"] ?: ""
                            fTag.setHint(Component.literal("§8e.g. quest_boss"))
                            fTag.setResponder { node.params["targetStoryTag"] = it; onDataChanged() }
                            addWidgetItem(fTag, relY, 16)
                            relY += 22
                        } else {
                            labels.add(InspectorLabel("Entity Selector:", relY))
                            relY += 12
                            val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Selector"))
                            f1.value = node.params["entitySelector"] ?: "@e[type=zombie,distance=..10]"
                            f1.setResponder { node.params["entitySelector"] = it; onDataChanged() }
                            addWidgetItem(f1, relY, 16)
                            relY += 22
                        }
                    }

                    "MODIFY_ENTITY_PROPERTIES" -> {
                        val targetMode = node.params["targetMode"] ?: "AREA_NEAREST"
                        val btnW = (inputW - 2) / 2
                        labels.add(InspectorLabel("Target By:", relY))
                        relY += 12
                        val bArea = Button.builder(Component.literal("Area / Selector")) {
                            node.params["targetMode"] = "AREA_NEAREST"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (targetMode == "AREA_NEAREST") bArea.active = false

                        val bTag = Button.builder(Component.literal("Story Tag")) {
                            node.params["targetMode"] = "STORY_TAG"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + btnW + 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (targetMode == "STORY_TAG") bTag.active = false

                        addWidgetItem(bArea, relY, 14); addWidgetItem(bTag, relY, 14)
                        relY += 18

                        if (targetMode == "STORY_TAG") {
                            labels.add(InspectorLabel("Target Story Tag:", relY))
                            relY += 12
                            val fTag = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                            fTag.value = node.params["targetStoryTag"] ?: ""
                            fTag.setHint(Component.literal("§8e.g. quest_boss"))
                            fTag.setResponder { node.params["targetStoryTag"] = it; onDataChanged() }
                            addWidgetItem(fTag, relY, 16)
                            relY += 22
                        } else {
                            labels.add(InspectorLabel("Entity Selector:", relY))
                            relY += 12
                            val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Selector"))
                            f1.value = node.params["entitySelector"] ?: "@e[type=!player,distance=..5,limit=1]"
                            f1.setResponder { node.params["entitySelector"] = it; onDataChanged() }
                            addWidgetItem(f1, relY, 16)
                            relY += 22
                        }

                        val cfgBtn = Button.builder(Component.literal("⚙️ Configure Entity...")) {
                            onOpenEntityConfig?.invoke(node)
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(cfgBtn, relY, 16)
                        relY += 22
                    }

                    "ADD_ENTITY_EFFECT", "ADD_AREA_EFFECT" -> {
                        if (actionDef.id == "ADD_ENTITY_EFFECT") {
                            val targetMode = node.params["targetMode"] ?: "AREA_NEAREST"
                            val btnW = (inputW - 2) / 2
                            labels.add(InspectorLabel("Target By:", relY))
                            relY += 12
                            val bArea = Button.builder(Component.literal("Area / Nearest")) {
                                node.params["targetMode"] = "AREA_NEAREST"
                                buildUi()
                                onDataChanged()
                            }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                            if (targetMode == "AREA_NEAREST") bArea.active = false

                            val bTag = Button.builder(Component.literal("Story Tag")) {
                                node.params["targetMode"] = "STORY_TAG"
                                buildUi()
                                onDataChanged()
                            }.bounds(inputX + btnW + 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                            if (targetMode == "STORY_TAG") bTag.active = false

                            addWidgetItem(bArea, relY, 14); addWidgetItem(bTag, relY, 14)
                            relY += 18

                            if (targetMode == "STORY_TAG") {
                                labels.add(InspectorLabel("Target Story Tag:", relY))
                                relY += 12
                                val fTag = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                                fTag.value = node.params["targetStoryTag"] ?: ""
                                fTag.setHint(Component.literal("§8e.g. quest_boss"))
                                fTag.setResponder { node.params["targetStoryTag"] = it; onDataChanged() }
                                addWidgetItem(fTag, relY, 16)
                                relY += 22
                            }
                        }

                        labels.add(InspectorLabel("Effect ID:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Effect"))
                        f1.value = node.params["effectId"] ?: "minecraft:glowing"
                        f1.setResponder { node.params["effectId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        if (actionDef.id == "ADD_AREA_EFFECT") {
                            labels.add(InspectorLabel("Radius (Blocks):", relY))
                            relY += 12
                            val fr = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Radius", node.params["radius"] ?: "8") { node.params["radius"] = it }
                            addWidgetItem(fr, relY, 16)
                            relY += 22
                        }

                        labels.add(InspectorLabel("Duration (Seconds):", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Sec", node.params["durationSec"] ?: "10") { node.params["durationSec"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22
                    }

                    "SPAWN_COBBLEMON", "SPAWN_POKEMON", "SPAWN", "GIVE_POKEMON" -> {
                        labels.add(InspectorLabel("Pokémon Species:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Species"))
                        f1.value = node.params["species"] ?: "Pikachu"
                        f1.setResponder { node.params["species"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Level:", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Level", node.params["level"] ?: "5") { node.params["level"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Story Tag (Optional):", relY))
                        relY += 12
                        val fTag = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                        fTag.value = node.params["storyTag"] ?: ""
                        fTag.setHint(Component.literal("§8e.g. wild_legendary, boss"))
                        fTag.setResponder { node.params["storyTag"] = it; onDataChanged() }
                        addWidgetItem(fTag, relY, 16)
                        relY += 22

                        val isShiny = node.params["shiny"] == "true"
                        val shinyText = if (isShiny) "✨ Shiny: YES" else "⚪ Shiny: NO"
                        labels.add(InspectorLabel(shinyText, relY, if (isShiny) 0xFFFFD700.toInt() else 0xFFA0A0A0.toInt()))
                        relY += 14

                        val initCoords = node.params["coordinates"]?.ifBlank {
                            "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"
                        } ?: "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"

                        relY = addCoordinateInputSection("Spawn Coordinates:", initCoords, inputX, inputW, relY) {
                            node.params["coordinates"] = it
                        }

                        val cfgBtn = Button.builder(Component.literal("⚙️ Configure Details...")) {
                            onOpenPokemonConfig?.invoke(node)
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(cfgBtn, relY, 16)
                        relY += 22
                    }

                    "MODIFY_POKEMON_PROPERTIES" -> {
                        val healHp = node.params["healHp"] != "false"
                        val healText = if (healHp) "❤️ Heal HP: YES" else "❤️ Heal HP: NO"
                        val bHeal = Button.builder(Component.literal(healText)) {
                            node.params["healHp"] = (!healHp).toString()
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(bHeal, relY, 16)
                        relY += 22
                    }

                    "CHANGE_POKEMON_PERSONALITY" -> {
                        labels.add(InspectorLabel("Personality Preset:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Preset"))
                        f1.value = node.params["personalityPreset"] ?: "Heroic"
                        f1.setResponder { node.params["personalityPreset"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "ADD_POKEMON_PARTY_EFFECT" -> {
                        labels.add(InspectorLabel("✨ Full party heal and restore.", relY, 0xFF00FFCC.toInt()))
                        relY += 20
                    }

                    "KILL_PLAYER" -> {
                        labels.add(InspectorLabel("💀 Eliminates the player immediately.", relY, 0xFFFF4444.toInt()))
                        relY += 20
                    }

                    "DAMAGE_PLAYER" -> {
                        labels.add(InspectorLabel("Damage Points:", relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Damage", node.params["damageAmount"] ?: "4.0") { node.params["damageAmount"] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "GIVE_ITEM", "REMOVE_ITEM", "SPAWN_ITEM" -> {
                        val currentItem = node.params["itemId"] ?: "cobblemon:poke_ball"
                        labels.add(InspectorLabel("Item (Catalog):", relY))
                        relY += 12

                        val pickBtn = Button.builder(Component.literal("📦 $currentItem")) {
                            onOpenResourcePicker?.invoke(ResourcePickerType.ITEM) { chosen ->
                                node.params["itemId"] = chosen
                                buildUi()
                                onDataChanged()
                            }
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(pickBtn, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Quantity:", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Qty", node.params["amount"] ?: "1") { node.params["amount"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22
                    }

                    "ADD_PLAYER_EFFECT", "EFFECT" -> {
                        labels.add(InspectorLabel("Effect ID:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Effect"))
                        f1.value = node.params["effectId"] ?: "minecraft:speed"
                        f1.setResponder { node.params["effectId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Duration (Seconds):", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Sec", node.params["durationSec"] ?: "10") { node.params["durationSec"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Amplifier (Level):", relY))
                        relY += 12
                        val f3 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Amp", node.params["amplifier"] ?: "1") { node.params["amplifier"] = it }
                        addWidgetItem(f3, relY, 16)
                        relY += 22
                    }

                    "JUMP_TO_STORY_POINT", "REWIND_TO_STORY_POINT" -> {
                        labels.add(InspectorLabel("Target Scene ID:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Scene ID"))
                        f1.value = node.params["targetSceneId"] ?: ""
                        f1.setResponder { node.params["targetSceneId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "SEND_CHAT_MESSAGE", "MESSAGE" -> {
                        labels.add(InspectorLabel("Message Text:", relY))
                        relY += 12

                        val cEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 28, Component.literal("Message"))
                        cEdit.setMaxLength(300)
                        cEdit.value = node.params["messageText"] ?: node.content
                        cEdit.setResponder { valText ->
                            node.params["messageText"] = valText
                            node.content = valText
                            onDataChanged()
                        }
                        addWidgetItem(cEdit, relY, 28)
                        relY += 34

                        val currentMsgType = node.params["messageType"] ?: "CHAT"
                        val btnW = (inputW - 4) / 3

                        labels.add(InspectorLabel("Display Mode:", relY))
                        relY += 12

                        val chatBtn = Button.builder(Component.literal("Chat")) {
                            node.params["messageType"] = "CHAT"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (currentMsgType == "CHAT") chatBtn.active = false

                        val titleBtn = Button.builder(Component.literal("Title")) {
                            node.params["messageType"] = "TITLE"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + btnW + 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (currentMsgType == "TITLE") titleBtn.active = false

                        val actionbarBtn = Button.builder(Component.literal("Bar")) {
                            node.params["messageType"] = "ACTION_BAR"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + (btnW + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (currentMsgType == "ACTION_BAR") actionbarBtn.active = false

                        addWidgetItem(chatBtn, relY, 14)
                        addWidgetItem(titleBtn, relY, 14)
                        addWidgetItem(actionbarBtn, relY, 14)
                        relY += 20
                    }

                    "SHOW_TITLE_SCREEN" -> {
                        labels.add(InspectorLabel("Main Title:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Title"))
                        f1.value = node.params["mainTitle"] ?: "Quest Completed!"
                        f1.setResponder { node.params["mainTitle"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Subtitle (Optional):", relY))
                        relY += 12
                        val f2 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Subtitle"))
                        f2.value = node.params["subTitle"] ?: ""
                        f2.setResponder { node.params["subTitle"] = it; onDataChanged() }
                        addWidgetItem(f2, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Color (Hex/Code, e.g. #FFAA00):", relY))
                        relY += 12
                        val fColor = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Color"))
                        fColor.value = node.params["titleColor"] ?: "#FFAA00"
                        fColor.setResponder { node.params["titleColor"] = it; onDataChanged() }
                        addWidgetItem(fColor, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Fade In (Ticks):", relY))
                        relY += 12
                        val fIn = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Fade In", node.params["fadeIn"] ?: "10") { node.params["fadeIn"] = it }
                        addWidgetItem(fIn, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Stay / Duration (Ticks):", relY))
                        relY += 12
                        val fStay = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Stay", node.params["stay"] ?: "70") { node.params["stay"] = it }
                        addWidgetItem(fStay, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Fade Out (Ticks):", relY))
                        relY += 12
                        val fOut = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Fade Out", node.params["fadeOut"] ?: "20") { node.params["fadeOut"] = it }
                        addWidgetItem(fOut, relY, 16)
                        relY += 22
                    }

                    "CHANGE_SCREEN_TINT" -> {
                        labels.add(InspectorLabel("Hex Color (e.g. #FF0000):", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Color"))
                        f1.value = node.params["tintColor"] ?: "#FF0000"
                        f1.setResponder { node.params["tintColor"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "SPAWN_PARTICLES" -> {
                        labels.add(InspectorLabel("Particle ID:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Particle"))
                        f1.value = node.params["particleId"] ?: "minecraft:totem_of_undying"
                        f1.setResponder { node.params["particleId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Quantity:", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Qty", node.params["count"] ?: "20") { node.params["count"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22

                        val initCoords = node.params["coordinates"]?.ifBlank {
                            "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"
                        } ?: "${node.params["posX"] ?: "~"} ${node.params["posY"] ?: "~"} ${node.params["posZ"] ?: "~"}"

                        relY = addCoordinateInputSection("Particle Center Coordinates:", initCoords, inputX, inputW, relY, showSafetyControls = false) {
                            node.params["coordinates"] = it
                        }
                    }

                    "PLAY_SOUND", "SOUND" -> {
                        val currentSound = node.params["soundId"] ?: "minecraft:entity.player.levelup"
                        labels.add(InspectorLabel("Sound (Catalog):", relY))
                        relY += 12

                        val pickBtn = Button.builder(Component.literal("🎵 $currentSound")) {
                            onOpenResourcePicker?.invoke(ResourcePickerType.SOUND) { chosen ->
                                node.params["soundId"] = chosen
                                buildUi()
                                onDataChanged()
                            }
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(pickBtn, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Volume:", relY))
                        relY += 12
                        val f2 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Volume"))
                        f2.value = node.params["volume"] ?: "1.0"
                        f2.setResponder { node.params["volume"] = it; onDataChanged() }
                        addWidgetItem(f2, relY, 16)
                        relY += 22
                    }

                    "PLAY_MUSIC" -> {
                        val currentMusic = node.params["musicId"] ?: "minecraft:music.game"
                        labels.add(InspectorLabel("Music (Catalog):", relY))
                        relY += 12

                        val pickBtn = Button.builder(Component.literal("🎼 $currentMusic")) {
                            onOpenResourcePicker?.invoke(ResourcePickerType.SOUND) { chosen ->
                                node.params["musicId"] = chosen
                                buildUi()
                                onDataChanged()
                            }
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(pickBtn, relY, 16)
                        relY += 22
                    }

                    "VAR_MODIFY" -> {
                        val currentVarKey = node.params["varKey"] ?: projectVariables.firstOrNull()?.id ?: "var_new"

                        labels.add(InspectorLabel("Variable:", relY))
                        relY += 12

                        val varSelectBtn = Button.builder(Component.literal("🔍 $currentVarKey")) {
                            onOpenVariableSelector?.invoke { selected ->
                                node.params["varKey"] = selected.id
                                buildUi()
                                onDataChanged()
                            }
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(varSelectBtn, relY, 16)
                        relY += 22

                        val currentOp = node.params["varOp"] ?: "="
                        labels.add(InspectorLabel("Operation:", relY))
                        relY += 12

                        val opBtn = Button.builder(Component.literal("Op: $currentOp")) {
                            val ops = listOf("=", "+=", "-=", "TOGGLE", "ADD", "REMOVE", "REMOVE_AT", "CLEAR")
                            val nextOp = ops[(ops.indexOf(currentOp) + 1) % ops.size]
                            node.params["varOp"] = nextOp
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(opBtn, relY, 16)
                        relY += 22

                        if (currentOp != "TOGGLE" && currentOp != "CLEAR") {
                            labels.add(InspectorLabel("Target Value:", relY))
                            relY += 12

                            val valEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Value"))
                            valEdit.value = node.params["varValue"] ?: "1"
                            valEdit.setResponder { node.params["varValue"] = it; onDataChanged() }
                            addWidgetItem(valEdit, relY, 16)
                            relY += 22
                        }
                    }
                }
            }

            NodeType.TRIGGER -> {
                val currentTrigId = node.params["triggerType"] ?: "START"
                val trigDef = TriggerRegistry.find(currentTrigId)

                // Active Type Card & Change Type Button
                labels.add(InspectorLabel("Current Trigger:", relY))
                relY += 12

                val changeBtn = Button.builder(Component.literal("🔄 ${trigDef.icon} ${trigDef.name}")) {
                    onOpenActionTriggerPicker?.invoke(false) { chosenId ->
                        node.params["triggerType"] = chosenId
                        val newDef = TriggerRegistry.find(chosenId)
                        if (node.title.isBlank() || node.title == "New Trigger" || TriggerRegistry.triggers.any { node.title.contains(it.name) }) {
                            node.title = newDef.name
                        }
                        buildUi()
                        onDataChanged()
                    }
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(changeBtn, relY, 16)
                relY += 22

                // IN Signal Control
                val requireInput = node.params["requireInputSignal"] != "false"
                val inBtnLabel = if (requireInput) "📥 IN Signal: YES" else "📥 IN Signal: NO"

                val inToggleBtn = Button.builder(Component.literal(inBtnLabel)) {
                    val nextState = !(node.params["requireInputSignal"] != "false")
                    if (nextState) {
                        node.params["requireInputSignal"] = "true"
                        if (node.inputs.isEmpty()) {
                            node.inputs.add(PortData(name = "In", type = PortType.INPUT))
                        }
                    } else {
                        node.params["requireInputSignal"] = "false"
                        node.inputs.clear()
                    }
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(inToggleBtn, relY, 16)
                relY += 20

                // Logical Mode IF / IF NOT
                val currentCondMode = node.params["triggerCondition"] ?: "IF"
                val btnW = (inputW - 2) / 2

                labels.add(InspectorLabel("Logic Condition:", relY))
                relY += 12

                val ifBtn = Button.builder(Component.literal("IF")) {
                    node.params["triggerCondition"] = "IF"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                if (currentCondMode == "IF") ifBtn.active = false

                val ifNotBtn = Button.builder(Component.literal("IF NOT")) {
                    node.params["triggerCondition"] = "IF_NOT"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX + btnW + 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                if (currentCondMode == "IF_NOT") ifNotBtn.active = false

                addWidgetItem(ifBtn, relY, 14)
                addWidgetItem(ifNotBtn, relY, 14)
                relY += 20

                // Strictly Contextual Inputs for Active Trigger
                when (trigDef.id) {
                    "STORY_STARTED", "START" -> {
                        labels.add(InspectorLabel("🟢 Starts in scene flow.", relY, 0xFF00FFCC.toInt()))
                        relY += 16
                    }

                    "STORY_ENDED" -> {
                        labels.add(InspectorLabel("🛑 Triggers on story finish.", relY, 0xFFFF4444.toInt()))
                        relY += 16
                    }

                    "PREVIOUS_MISSION_COMPLETED" -> {
                        labels.add(InspectorLabel("Mission ID:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Mission ID"))
                        f1.value = node.params["missionId"] ?: "mission_1"
                        f1.setResponder { node.params["missionId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "PREVIOUS_EVENT_EXECUTED" -> {
                        labels.add(InspectorLabel("Event Tag:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Event"))
                        f1.value = node.params["eventTag"] ?: "key_event"
                        f1.setResponder { node.params["eventTag"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "TIME_ELAPSED" -> {
                        labels.add(InspectorLabel("Time (Seconds):", relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Seconds", node.params["timeSeconds"] ?: "10") { node.params["timeSeconds"] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "TIME_OF_DAY" -> {
                        labels.add(InspectorLabel("Time of Day (Ticks):", relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Ticks", node.params["timeOfDayTicks"] ?: "6000") { node.params["timeOfDayTicks"] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "DAYS_PASSED" -> {
                        labels.add(InspectorLabel("In-Game Days:", relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Days", node.params["daysCount"] ?: "1") { node.params["daysCount"] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "DAY_NIGHT_CHECK" -> {
                        val currentPeriod = node.params["timePeriod"] ?: "DAY"
                        val bW = (inputW - 2) / 2
                        labels.add(InspectorLabel("Required Period:", relY))
                        relY += 12
                        val bDay = Button.builder(Component.literal("☀️ Day")) { node.params["timePeriod"] = "DAY"; buildUi(); onDataChanged() }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        if (currentPeriod == "DAY") bDay.active = false
                        val bNight = Button.builder(Component.literal("🌙 Night")) { node.params["timePeriod"] = "NIGHT"; buildUi(); onDataChanged() }.bounds(inputX + bW + 2, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        if (currentPeriod == "NIGHT") bNight.active = false
                        addWidgetItem(bDay, relY, 14); addWidgetItem(bNight, relY, 14)
                        relY += 20
                    }

                    "PLAYER_LEVEL", "HIGHEST_POKEMON_LEVEL", "PLAYER_ITEM_COUNT", "KARMA_CHECK" -> {
                        val valLabel = when (trigDef.id) {
                            "PLAYER_LEVEL" -> "EXP Level:"
                            "HIGHEST_POKEMON_LEVEL" -> "Pokémon Level:"
                            "PLAYER_ITEM_COUNT" -> "Min Quantity:"
                            else -> "Target Karma:"
                        }
                        val keyName = when (trigDef.id) {
                            "PLAYER_LEVEL" -> "minLevel"
                            "HIGHEST_POKEMON_LEVEL" -> "targetLevel"
                            "PLAYER_ITEM_COUNT" -> "minCount"
                            else -> "targetKarma"
                        }
                        labels.add(InspectorLabel(valLabel, relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Value", node.params[keyName] ?: "10") { node.params[keyName] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        if (trigDef.id == "PLAYER_ITEM_COUNT") {
                            val currentItem = node.params["checkItemId"] ?: "cobblemon:poke_ball"
                            labels.add(InspectorLabel("Required Item (Catalog):", relY))
                            relY += 12
                            val pickBtn = Button.builder(Component.literal("📦 $currentItem")) {
                                onOpenResourcePicker?.invoke(ResourcePickerType.ITEM) { chosen ->
                                    node.params["checkItemId"] = chosen
                                    buildUi()
                                    onDataChanged()
                                }
                            }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                            addWidgetItem(pickBtn, relY, 16)
                            relY += 22
                        }
                    }

                    "PLAYER_COORDINATES", "LOCATION" -> {
                        val initCoords = node.params["coordinates"]?.ifBlank {
                            "${node.params["targetX"] ?: "0"} ${node.params["targetY"] ?: "64"} ${node.params["targetZ"] ?: "0"}"
                        } ?: "${node.params["targetX"] ?: "0"} ${node.params["targetY"] ?: "64"} ${node.params["targetZ"] ?: "0"}"

                        relY = addCoordinateInputSection("Target Center Coordinates:", initCoords, inputX, inputW, relY, showSafetyControls = false) {
                            node.params["coordinates"] = it
                        }

                        labels.add(InspectorLabel("Radius (Blocks):", relY))
                        relY += 12
                        val f4 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Radius", node.params["radius"] ?: "5") { node.params["radius"] = it }
                        addWidgetItem(f4, relY, 16)
                        relY += 22
                    }

                    "PLAYER_BIOME" -> {
                        labels.add(InspectorLabel("Biome ID:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Biome"))
                        f1.value = node.params["biomeId"] ?: "minecraft:plains"
                        f1.setResponder { node.params["biomeId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "PLAYER_HELD_ITEM", "PLAYER_INVENTORY_ITEM_REMOVED" -> {
                        val currentItem = node.params["heldItemId"] ?: "minecraft:diamond_sword"
                        labels.add(InspectorLabel("Item (Catalog):", relY))
                        relY += 12

                        val pickBtn = Button.builder(Component.literal("📦 $currentItem")) {
                            onOpenResourcePicker?.invoke(ResourcePickerType.ITEM) { chosen ->
                                node.params["heldItemId"] = chosen
                                buildUi()
                                onDataChanged()
                            }
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(pickBtn, relY, 16)
                        relY += 22
                    }

                    "PLAYER_INVENTORY_HAS_ITEM", "ITEM_IN_INVENTORY" -> {
                        val currentItem = node.params["requiredItem"] ?: "cobblemon:potion"
                        labels.add(InspectorLabel("Required Item (Catalog):", relY))
                        relY += 12

                        val pickBtn = Button.builder(Component.literal("📦 $currentItem")) {
                            onOpenResourcePicker?.invoke(ResourcePickerType.ITEM) { chosen ->
                                node.params["requiredItem"] = chosen
                                buildUi()
                                onDataChanged()
                            }
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(pickBtn, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Required Quantity:", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Qty", node.params["requiredCount"] ?: "1") { node.params["requiredCount"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22
                    }

                    "POKEMON_CATCH", "CATCH_POKEMON", "SPECIFIC_POKEMON_IN_PARTY", "BATTLE_VICTORY", "DEFEAT_POKEMON" -> {
                        labels.add(InspectorLabel("Pokémon Species:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Species"))
                        f1.value = node.params["targetSpecies"] ?: "Pikachu"
                        f1.setResponder { node.params["targetSpecies"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Required Story Tag (Optional):", relY))
                        relY += 12
                        val fTag = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                        fTag.value = node.params["requiredStoryTag"] ?: ""
                        fTag.setHint(Component.literal("§8e.g. quest_boss"))
                        fTag.setResponder { node.params["requiredStoryTag"] = it; onDataChanged() }
                        addWidgetItem(fTag, relY, 16)
                        relY += 22
                    }

                    "POKEMON_FRIENDSHIP" -> {
                        labels.add(InspectorLabel("Pokémon Species:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Species"))
                        f1.value = node.params["targetSpecies"] ?: "Pikachu"
                        f1.setResponder { node.params["targetSpecies"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Min Friendship (0-255):", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Friendship", node.params["minFriendship"] ?: "220") { node.params["minFriendship"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22
                    }

                    "BATTLE_START" -> {
                        labels.add(InspectorLabel("⚔️ Triggers at start of any battle.", relY, 0xFF00FFCC.toInt()))
                        relY += 16
                    }

                    "BATTLE_DEFEAT" -> {
                        labels.add(InspectorLabel("💀 Triggers when defeated in battle.", relY, 0xFFFF4444.toInt()))
                        relY += 16
                    }

                    "ENTITY_DIED", "ENTITY_DAMAGED", "ENTITY_SPAWNED", "TALK_TO_POKEMON", "INTERACT_POKEMON" -> {
                        val targetType = node.params["targetType"] ?: if (trigDef.id == "INTERACT_POKEMON" || trigDef.id == "TALK_TO_POKEMON") "COBBLEMON" else "GENERIC"
                        val btnW = (inputW - 2) / 2

                        labels.add(InspectorLabel("Target Type:", relY))
                        relY += 12
                        val bGen = Button.builder(Component.literal("👾 Generic Entity")) {
                            node.params["targetType"] = "GENERIC"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (targetType == "GENERIC") bGen.active = false

                        val bPoke = Button.builder(Component.literal("🐾 Cobblemon")) {
                            node.params["targetType"] = "COBBLEMON"
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX + btnW + 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (targetType == "COBBLEMON") bPoke.active = false

                        addWidgetItem(bGen, relY, 14); addWidgetItem(bPoke, relY, 14)
                        relY += 20

                        if (targetType == "COBBLEMON") {
                            labels.add(InspectorLabel("Pokémon Species:", relY))
                            relY += 12
                            val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Species"))
                            f1.value = node.params["targetSpecies"] ?: "Pikachu"
                            f1.setResponder { node.params["targetSpecies"] = it; onDataChanged() }
                            addWidgetItem(f1, relY, 16)
                            relY += 22

                            labels.add(InspectorLabel("Form / Variant (Optional):", relY))
                            relY += 12
                            val fForm = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Form"))
                            fForm.value = node.params["form"] ?: ""
                            fForm.setHint(Component.literal("§8e.g. alolan, hisuian..."))
                            fForm.setResponder { node.params["form"] = it; onDataChanged() }
                            addWidgetItem(fForm, relY, 16)
                            relY += 22

                            labels.add(InspectorLabel("Level Range (Min - Max):", relY))
                            relY += 12
                            val halfW = (inputW - 4) / 2
                            val minLvl = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), halfW, "Min", node.params["minLevel"] ?: "1") { node.params["minLevel"] = it }
                            val maxLvl = createNumEdit(inputX + halfW + 4, (panelY + 20 + relY - scrollOffset).toInt(), halfW, "Max", node.params["maxLevel"] ?: "100") { node.params["maxLevel"] = it }
                            addWidgetItem(minLvl, relY, 16); addWidgetItem(maxLvl, relY, 16)
                            relY += 22

                            val shinyMode = node.params["shinyMode"] ?: "ANY"
                            val shiny3W = (inputW - 4) / 3
                            labels.add(InspectorLabel("Shiny Variant:", relY))
                            relY += 12
                            val bShAny = Button.builder(Component.literal("✨ Any")) { node.params["shinyMode"] = "ANY"; buildUi(); onDataChanged() }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), shiny3W, 14).build()
                            if (shinyMode == "ANY") bShAny.active = false
                            val bShYes = Button.builder(Component.literal("✨ Yes")) { node.params["shinyMode"] = "YES"; buildUi(); onDataChanged() }.bounds(inputX + shiny3W + 2, (panelY + 20 + relY - scrollOffset).toInt(), shiny3W, 14).build()
                            if (shinyMode == "YES") bShYes.active = false
                            val bShNo = Button.builder(Component.literal("⚪ No")) { node.params["shinyMode"] = "NO"; buildUi(); onDataChanged() }.bounds(inputX + (shiny3W + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), shiny3W, 14).build()
                            if (shinyMode == "NO") bShNo.active = false
                            addWidgetItem(bShAny, relY, 14); addWidgetItem(bShYes, relY, 14); addWidgetItem(bShNo, relY, 14)
                            relY += 20

                            val statusMode = node.params["pokemonStatus"] ?: "ANY"
                            labels.add(InspectorLabel("Pokémon Status:", relY))
                            relY += 12
                            val bStAny = Button.builder(Component.literal("Any")) { node.params["pokemonStatus"] = "ANY"; buildUi(); onDataChanged() }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), shiny3W, 14).build()
                            if (statusMode == "ANY") bStAny.active = false
                            val bStWild = Button.builder(Component.literal("Wild")) { node.params["pokemonStatus"] = "WILD"; buildUi(); onDataChanged() }.bounds(inputX + shiny3W + 2, (panelY + 20 + relY - scrollOffset).toInt(), shiny3W, 14).build()
                            if (statusMode == "WILD") bStWild.active = false
                            val bStParty = Button.builder(Component.literal("Party")) { node.params["pokemonStatus"] = "PARTY"; buildUi(); onDataChanged() }.bounds(inputX + (shiny3W + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), shiny3W, 14).build()
                            if (statusMode == "PARTY") bStParty.active = false
                            addWidgetItem(bStAny, relY, 14); addWidgetItem(bStWild, relY, 14); addWidgetItem(bStParty, relY, 14)
                            relY += 20
                        } else {
                            val currentEntity = node.params["entityType"] ?: "minecraft:zombie"
                            labels.add(InspectorLabel("Entity Type (Catalog):", relY))
                            relY += 12

                            val pickBtn = Button.builder(Component.literal("👾 $currentEntity")) {
                                onOpenResourcePicker?.invoke(ResourcePickerType.ENTITY) { chosen ->
                                    node.params["entityType"] = chosen
                                    buildUi()
                                    onDataChanged()
                                }
                            }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                            addWidgetItem(pickBtn, relY, 16)
                            relY += 22
                        }

                        labels.add(InspectorLabel("Required Story Tag (Optional):", relY))
                        relY += 12
                        val fTag = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                        fTag.value = node.params["requiredStoryTag"] ?: ""
                        fTag.setHint(Component.literal("§8e.g. quest_boss, npc_guide"))
                        fTag.setResponder { node.params["requiredStoryTag"] = it; onDataChanged() }
                        addWidgetItem(fTag, relY, 16)
                        relY += 22

                        if (trigDef.id == "ENTITY_DAMAGED") {
                            labels.add(InspectorLabel("Min Damage:", relY))
                            relY += 12
                            val fDmg = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Min Dmg", node.params["minDamage"] ?: "1.0") { node.params["minDamage"] = it }
                            addWidgetItem(fDmg, relY, 16)
                            relY += 22
                        }
                    }

                    "WEATHER_CHECK" -> {
                        val currentTarget = (node.params["weatherType"] ?: "RAIN").uppercase()
                        val bW = (inputW - 4) / 3
                        labels.add(InspectorLabel("Required Weather:", relY))
                        relY += 12
                        val bClear = Button.builder(Component.literal("Clear")) { node.params["weatherType"] = "CLEAR"; buildUi(); onDataChanged() }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        if (currentTarget == "CLEAR") bClear.active = false
                        val bRain = Button.builder(Component.literal("Rain")) { node.params["weatherType"] = "RAIN"; buildUi(); onDataChanged() }.bounds(inputX + bW + 2, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        if (currentTarget == "RAIN") bRain.active = false
                        val bThunder = Button.builder(Component.literal("Thunder")) { node.params["weatherType"] = "THUNDER"; buildUi(); onDataChanged() }.bounds(inputX + (bW + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        if (currentTarget == "THUNDER") bThunder.active = false
                        addWidgetItem(bClear, relY, 14); addWidgetItem(bRain, relY, 14); addWidgetItem(bThunder, relY, 14)
                        relY += 20
                    }

                    "BLOCK_INTERACTED", "BLOCK_PLACED" -> {
                        labels.add(InspectorLabel("Block ID:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Block"))
                        f1.value = node.params["blockId"] ?: "minecraft:chest"
                        f1.setResponder { node.params["blockId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "ENTER_STRUCTURE_OR_ZONE" -> {
                        val currentStructure = node.params["structureId"] ?: "minecraft:village_plains"
                        labels.add(InspectorLabel("Structure (Catalog):", relY))
                        relY += 12

                        val pickBtn = Button.builder(Component.literal("🏛️ $currentStructure")) {
                            onOpenResourcePicker?.invoke(ResourcePickerType.STRUCTURE) { chosen ->
                                node.params["structureId"] = chosen
                                buildUi()
                                onDataChanged()
                            }
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(pickBtn, relY, 16)
                        relY += 22
                    }

                    "AI_EVALUATION" -> {
                        labels.add(InspectorLabel("Expected AI Intent:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Intent"))
                        f1.value = node.params["aiIntent"] ?: "AGREE"
                        f1.setResponder { node.params["aiIntent"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "VARIABLE_VALUE_CHECK" -> {
                        val currentVarKey = node.params["varKey"] ?: projectVariables.firstOrNull()?.id ?: "var_1"
                        labels.add(InspectorLabel("Watched Variable:", relY))
                        relY += 12

                        val varSelectBtn = Button.builder(Component.literal("🔍 Var: $currentVarKey")) {
                            onOpenVariableSelector?.invoke { selected ->
                                node.params["varKey"] = selected.id
                                buildUi()
                                onDataChanged()
                            }
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(varSelectBtn, relY, 16)
                        relY += 22

                        val ops = listOf("==", "!=", ">", "<", ">=", "<=", "CONTAINS")
                        val currentOp = node.params["varOp"] ?: ">="
                        val validOp = if (ops.contains(currentOp)) currentOp else ">="
                        node.params["varOp"] = validOp

                        labels.add(InspectorLabel("Comparison Operator:", relY))
                        relY += 12

                        val opBtn = Button.builder(Component.literal("Op: $validOp")) {
                            val nextOp = ops[(ops.indexOf(validOp) + 1) % ops.size]
                            node.params["varOp"] = nextOp
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(opBtn, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Target Value:", relY))
                        relY += 12

                        val valEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Target Value"))
                        valEdit.value = node.params["varValue"] ?: "100"
                        valEdit.setResponder { text ->
                            node.params["varValue"] = text
                            onDataChanged()
                        }
                        addWidgetItem(valEdit, relY, 16)
                        relY += 22
                    }
                }
            }

            NodeType.VARIABLE_GET -> {
                val currentVarKey = node.params["varKey"] ?: projectVariables.firstOrNull()?.id ?: "var_new"
                val selectedVar = projectVariables.find { it.id == currentVarKey }

                labels.add(InspectorLabel("Variable (Get):", relY))
                relY += 12

                val varSelectBtn = Button.builder(Component.literal("🔍 $currentVarKey")) {
                    onOpenVariableSelector?.invoke { selected ->
                        node.params["varKey"] = selected.id
                        node.title = "Get: ${selected.id}"
                        buildUi()
                        onDataChanged()
                    }
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(varSelectBtn, relY, 16)
                relY += 22

                val onChangedOn = node.params["enableOnChanged"] == "true"
                labels.add(InspectorLabel("Reactive Signal:", relY))
                relY += 12
                val onChangedBtn = Button.builder(Component.literal(if (onChangedOn) "⚡ ON_CHANGED: YES" else "⚡ ON_CHANGED: NO")) {
                    val nextState = !onChangedOn
                    node.params["enableOnChanged"] = nextState.toString()
                    if (nextState) {
                        if (node.outputs.none { it.id == "ON_CHANGED_OUT" }) {
                            node.outputs.add(PortData(id = "ON_CHANGED_OUT", name = "On Changed", type = PortType.OUTPUT))
                        }
                    } else {
                        node.outputs.removeAll { it.id == "ON_CHANGED_OUT" || it.name.equals("On Changed", ignoreCase = true) }
                    }
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(onChangedBtn, relY, 16)
                relY += 22

                if (selectedVar?.type == VariableType.LIST) {
                    val listOps = listOf("CONTAINS", "SIZE", "IS_EMPTY", "GET_INDEX")
                    val currentOp = node.params["varOp"] ?: "SIZE"
                    val validOp = if (listOps.contains(currentOp)) currentOp else listOps.first()
                    node.params["varOp"] = validOp

                    labels.add(InspectorLabel("List Query:", relY))
                    relY += 12

                    val opBtn = Button.builder(Component.literal("Op: $validOp")) {
                        val nextOp = listOps[(listOps.indexOf(validOp) + 1) % listOps.size]
                        node.params["varOp"] = nextOp
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(opBtn, relY, 16)
                    relY += 22

                    if (validOp != "IS_EMPTY") {
                        val labelText = when (validOp) {
                            "CONTAINS" -> "Search Item:"
                            "SIZE" -> "Expected Size:"
                            "GET_INDEX" -> "Index (0, 1, 2...):"
                            else -> "Parameter:"
                        }
                        labels.add(InspectorLabel(labelText, relY))
                        relY += 12

                        val valEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Value"))
                        valEdit.value = node.params["varValue"] ?: "0"
                        valEdit.setResponder { text -> node.params["varValue"] = text; onDataChanged() }
                        addWidgetItem(valEdit, relY, 16)
                        relY += 22
                    }
                } else {
                    val typeStr = selectedVar?.type?.name ?: "STRING"
                    val scopeStr = selectedVar?.scope?.name ?: "GLOBAL"
                    labels.add(InspectorLabel("Type: $typeStr", relY))
                    relY += 12
                    labels.add(InspectorLabel("Scope: $scopeStr", relY))
                    relY += 16
                }
            }

            NodeType.VARIABLE_SET -> {
                val currentVarKey = node.params["varKey"] ?: projectVariables.firstOrNull()?.id ?: "var_new"
                val selectedVar = projectVariables.find { it.id == currentVarKey }
                val varType = selectedVar?.type ?: VariableType.STRING

                labels.add(InspectorLabel("Variable (Set):", relY))
                relY += 12

                val varSelectBtn = Button.builder(Component.literal("🔍 $currentVarKey")) {
                    onOpenVariableSelector?.invoke { selected ->
                        node.params["varKey"] = selected.id
                        node.title = "Set: ${selected.id}"
                        buildUi()
                        onDataChanged()
                    }
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(varSelectBtn, relY, 16)
                relY += 22

                val ops = when (varType) {
                    VariableType.NUMBER -> listOf("=", "+", "-", "*")
                    VariableType.BOOLEAN -> listOf("=", "NOT")
                    VariableType.STRING -> listOf("=", "+")
                    VariableType.LIST -> listOf("ADD", "REMOVE", "REMOVE_AT", "CLEAR", "SET")
                }

                val currentOp = node.params["varOp"] ?: ops.first()
                val validOp = if (ops.contains(currentOp)) currentOp else ops.first()
                node.params["varOp"] = validOp

                labels.add(InspectorLabel("Operation:", relY))
                relY += 12

                val opBtn = Button.builder(Component.literal("Op: $validOp")) {
                    val nextOp = ops[(ops.indexOf(validOp) + 1) % ops.size]
                    node.params["varOp"] = nextOp
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(opBtn, relY, 16)
                relY += 22

                if (validOp != "NOT" && validOp != "CLEAR") {
                    val labelText = when (validOp) {
                        "ADD" -> "Item to Add:"
                        "REMOVE" -> "Item to Remove:"
                        "REMOVE_AT" -> "Numeric Index:"
                        "SET" -> "List (item1, item2):"
                        else -> "Target Value:"
                    }
                    labels.add(InspectorLabel(labelText, relY))
                    relY += 12

                    val valEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal(labelText))
                    valEdit.value = node.params["varValue"] ?: if (validOp == "REMOVE_AT") "0" else "item"
                    if (validOp == "REMOVE_AT" || varType == VariableType.NUMBER) {
                        valEdit.setFilter { text -> text.isEmpty() || text.all { it.isDigit() || it == '-' || it == '.' } }
                    }
                    valEdit.setResponder { text ->
                        node.params["varValue"] = text
                        onDataChanged()
                    }
                    addWidgetItem(valEdit, relY, 16)
                    relY += 22
                }
            }

            NodeType.COMMENT -> {
                labels.add(InspectorLabel("Note / Comment:", relY))
                relY += 12

                val cEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 50, Component.literal("Note"))
                cEdit.setMaxLength(300)
                cEdit.value = node.content
                cEdit.setResponder { valText ->
                    node.content = valText
                    onDataChanged()
                }
                addWidgetItem(cEdit, relY, 50)
                relY += 56
            }

            NodeType.LINK_SEND, NodeType.LINK_RECEIVE -> {
                labels.add(InspectorLabel("Channel Tag:", relY))
                relY += 12

                val tagEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Channel Tag"))
                tagEdit.setMaxLength(50)
                tagEdit.value = node.params["channelTag"] ?: "channel_1"
                tagEdit.setResponder { valText ->
                    node.params["channelTag"] = valText
                    onDataChanged()
                }
                addWidgetItem(tagEdit, relY, 16)
                relY += 22

                val isSend = node.nodeType == NodeType.LINK_SEND
                val infoText = if (isSend) "Transmits wireless signal." else "Receives signal from sender."
                labels.add(InspectorLabel(infoText, relY))
                relY += 16
            }

            NodeType.QUEST -> {
                labels.add(InspectorLabel("Quest Title:", relY))
                relY += 12
                val titleEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Title"))
                titleEdit.value = node.params["questTitle"] ?: node.title
                titleEdit.setResponder {
                    node.params["questTitle"] = it
                    if (node.title.isBlank() || node.title == "New Node" || node.title == "Mission (Quest)" || node.title == "Missão (Quest)") {
                        node.title = it
                    }
                    onDataChanged()
                }
                addWidgetItem(titleEdit, relY, 16)
                relY += 22

                val currentTrigId = node.params["questTrigger"] ?: "POKEMON_CATCH"
                val trigDef = TriggerRegistry.find(currentTrigId)
                labels.add(InspectorLabel("Objective Trigger:", relY))
                relY += 12
                val changeBtn = Button.builder(Component.literal("🔄 ${trigDef.icon} ${trigDef.name}")) {
                    onOpenActionTriggerPicker?.invoke(false) { chosenId ->
                        node.params["questTrigger"] = chosenId
                        buildUi()
                        onDataChanged()
                    }
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(changeBtn, relY, 16)
                relY += 22

                if (currentTrigId in listOf("POKEMON_CATCH", "INTERACT_POKEMON", "TALK_TO_POKEMON", "SPECIFIC_POKEMON_IN_PARTY", "BATTLE_VICTORY")) {
                    labels.add(InspectorLabel("Target Species (Optional):", relY))
                    relY += 12
                    val spEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Species"))
                    spEdit.value = node.params["targetSpecies"] ?: ""
                    spEdit.setHint(Component.literal("§8e.g. Pikachu (leave empty for any)"))
                    spEdit.setResponder { node.params["targetSpecies"] = it; onDataChanged() }
                    addWidgetItem(spEdit, relY, 16)
                    relY += 22
                }

                if (currentTrigId in listOf("ENTITY_DIED", "ENTITY_DAMAGED", "ENTITY_SPAWNED", "INTERACT_POKEMON", "TALK_TO_POKEMON", "POKEMON_CATCH")) {
                    labels.add(InspectorLabel("Required Story Tag (Optional):", relY))
                    relY += 12
                    val tagEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                    tagEdit.value = node.params["requiredStoryTag"] ?: ""
                    tagEdit.setHint(Component.literal("§8e.g. quest_boss (leave empty for any)"))
                    tagEdit.setResponder { node.params["requiredStoryTag"] = it; onDataChanged() }
                    addWidgetItem(tagEdit, relY, 16)
                    relY += 22
                }

                labels.add(InspectorLabel("Target Count:", relY))
                relY += 12
                val countEdit = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Qty", node.params["targetCount"] ?: "1") {
                    node.params["targetCount"] = it
                    onDataChanged()
                }
                addWidgetItem(countEdit, relY, 16)
                relY += 22

                labels.add(InspectorLabel("Time Limit (Sec, 0 = ∞):", relY))
                relY += 12
                val limitEdit = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Seconds", node.params["timeLimitSec"] ?: "0") {
                    node.params["timeLimitSec"] = it
                    onDataChanged()
                }
                addWidgetItem(limitEdit, relY, 16)
                relY += 22

                val failDeath = node.params["failOnDeath"] == "true"
                val failDeathBtn = Button.builder(Component.literal(if (failDeath) "💀 Fail on Death: YES" else "💀 Fail on Death: NO")) {
                    node.params["failOnDeath"] = (!failDeath).toString()
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(failDeathBtn, relY, 16)
                relY += 22

                val showHud = node.params["showHud"] != "false"
                val showHudBtn = Button.builder(Component.literal(if (showHud) "🖥️ Show HUD: YES" else "🖥️ Show HUD: NO")) {
                    node.params["showHud"] = (!showHud).toString()
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(showHudBtn, relY, 16)
                relY += 22
            }

            NodeType.AUDIO -> {
                val currentAudioMode = node.params["audioMode"] ?: "PLAY_SOUND_EFFECT"
                labels.add(InspectorLabel("Audio Mode:", relY))
                relY += 12

                val modeBtn = Button.builder(Component.literal("Mode: $currentAudioMode")) {
                    val modes = listOf("PLAY_SOUND_EFFECT", "PLAY_BACKGROUND_MUSIC", "STOP_ALL_MUSIC")
                    val nextMode = modes[(modes.indexOf(currentAudioMode) + 1) % modes.size]
                    node.params["audioMode"] = nextMode
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(modeBtn, relY, 16)
                relY += 22

                if (currentAudioMode != "STOP_ALL_MUSIC") {
                    val currentAudio = node.params["audioId"] ?: if (currentAudioMode == "PLAY_BACKGROUND_MUSIC") "minecraft:music.credits" else "cobblemon:battle.victory"
                    labels.add(InspectorLabel("Audio / Music (Catalog):", relY))
                    relY += 12

                    val pickBtn = Button.builder(Component.literal("🎵 $currentAudio")) {
                        onOpenResourcePicker?.invoke(ResourcePickerType.SOUND) { chosen ->
                            node.params["audioId"] = chosen
                            buildUi()
                            onDataChanged()
                        }
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(pickBtn, relY, 16)
                    relY += 22

                    labels.add(InspectorLabel("Volume (0.0 - 2.0):", relY))
                    relY += 12
                    val volEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Volume"))
                    volEdit.value = node.params["audioVolume"] ?: "1.0"
                    volEdit.setResponder { node.params["audioVolume"] = it; onDataChanged() }
                    addWidgetItem(volEdit, relY, 16)
                    relY += 22

                    labels.add(InspectorLabel("Pitch (0.5 - 2.0):", relY))
                    relY += 12
                    val pitchEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Pitch"))
                    pitchEdit.value = node.params["audioPitch"] ?: "1.0"
                    pitchEdit.setResponder { node.params["audioPitch"] = it; onDataChanged() }
                    addWidgetItem(pitchEdit, relY, 16)
                    relY += 22

                    val loopOn = node.params["audioLoop"] == "true"
                    val loopBtn = Button.builder(Component.literal(if (loopOn) "🔄 Loop: YES" else "🔄 Loop: NO")) {
                        node.params["audioLoop"] = (!loopOn).toString()
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(loopBtn, relY, 16)
                    relY += 22

                    val isPositional = node.params["spatialMode"] == "POSITIONAL_3D"
                    val spatialBtn = Button.builder(Component.literal(if (isPositional) "🔊 Mode: 3D Positional" else "🔊 Mode: 2D Global")) {
                        node.params["spatialMode"] = if (isPositional) "GLOBAL_2D" else "POSITIONAL_3D"
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(spatialBtn, relY, 16)
                    relY += 22

                    if (isPositional) {
                        labels.add(InspectorLabel("Position X, Y, Z:", relY))
                        relY += 12
                        val colW = (inputW - 4) / 3
                        val fx = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), colW, 16, Component.literal("X"))
                        fx.value = node.params["posX"] ?: "~"
                        fx.setResponder { node.params["posX"] = it; onDataChanged() }
                        val fy = EditBox(font, inputX + colW + 2, (panelY + 20 + relY - scrollOffset).toInt(), colW, 16, Component.literal("Y"))
                        fy.value = node.params["posY"] ?: "~"
                        fy.setResponder { node.params["posY"] = it; onDataChanged() }
                        val fz = EditBox(font, inputX + (colW + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), colW, 16, Component.literal("Z"))
                        fz.value = node.params["posZ"] ?: "~"
                        fz.setResponder { node.params["posZ"] = it; onDataChanged() }
                        addWidgetItem(fx, relY, 16)
                        addWidgetItem(fy, relY, 16)
                        addWidgetItem(fz, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Radius (Blocks):", relY))
                        relY += 12
                        val radEdit = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Radius", node.params["audioRadius"] ?: "16") { node.params["audioRadius"] = it }
                        addWidgetItem(radEdit, relY, 16)
                        relY += 22
                    }
                }
            }

            NodeType.LOOP -> {
                val currentMode = node.params["loopMode"] ?: "COUNT"

                labels.add(InspectorLabel("Operation Mode:", relY))
                relY += 12

                val btnW = (inputW - 2) / 2

                val countBtn = Button.builder(Component.literal("Count")) {
                    node.params["loopMode"] = "COUNT"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                if (currentMode == "COUNT") countBtn.active = false

                val timeBtn = Button.builder(Component.literal("Time")) {
                    node.params["loopMode"] = "TIME"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX + btnW + 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                if (currentMode == "TIME") timeBtn.active = false

                addWidgetItem(countBtn, relY, 14)
                addWidgetItem(timeBtn, relY, 14)
                relY += 20

                if (currentMode == "COUNT") {
                    labels.add(InspectorLabel("Repetitions (Qty):", relY))
                    relY += 12

                    val countEdit = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Qty", node.params["loopCount"] ?: "5") { valText ->
                        node.params["loopCount"] = valText
                    }
                    addWidgetItem(countEdit, relY, 16)
                    relY += 22
                }

                labels.add(InspectorLabel("Interval (Sec):", relY))
                relY += 12

                val intervalEdit = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Sec", node.params["loopIntervalSec"] ?: "1.0") { valText ->
                    node.params["loopIntervalSec"] = valText
                }
                addWidgetItem(intervalEdit, relY, 16)
                relY += 22
            }

            NodeType.BEGIN_SCENE -> {
                labels.add(InspectorLabel("Scene Entry Point.", relY))
                relY += 12
                labels.add(InspectorLabel("Fires OUT output.", relY))
                relY += 16
            }

            NodeType.END_SCENE -> {
                labels.add(InspectorLabel("Finishes current Scene.", relY))
                relY += 12
                labels.add(InspectorLabel("Fires OUT output.", relY))
                relY += 16
            }

            NodeType.GATE -> {
                val currentCount = node.inputs.size.coerceAtLeast(2)

                labels.add(InspectorLabel("GATE Synchronizer:", relY))
                relY += 12

                labels.add(InspectorLabel("Inputs (2-5):", relY))
                relY += 12

                val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Input Count", currentCount.toString()) { valText ->
                    val num = valText.toIntOrNull()?.coerceIn(2, 5) ?: 2
                    if (num != node.inputs.size) {
                        while (node.inputs.size < num) {
                            node.inputs.add(PortData(name = "IN ${node.inputs.size + 1}", type = PortType.INPUT))
                        }
                        while (node.inputs.size > num) {
                            node.inputs.removeAt(node.inputs.size - 1)
                        }
                        buildUi()
                        onDataChanged()
                    }
                }
                addWidgetItem(f1, relY, 16)
                relY += 22
            }

            NodeType.CONSTRUCTION -> {
                labels.add(InspectorLabel("Internal Sub-Graph:", relY))
                relY += 12

                val openBtn = Button.builder(Component.literal("🔍 Edit Internal")) {
                    onOpenConstruction?.invoke(node)
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 18).build()
                addWidgetItem(openBtn, relY, 18)
                relY += 24

                labels.add(InspectorLabel("Internal Mini-Map:", relY))
                relY += 14
                relY += 70
            }

            NodeType.CONDITION_NODE -> {
                val elseIfCount = node.params["elseIfCount"]?.toIntOrNull() ?: 0
                val hasElse = node.params["hasElse"] != "false"

                fun syncConditionPorts() {
                    if (node.outputs.isEmpty()) {
                        node.outputs.add(PortData(id = "OUT_IF", name = "IF", type = PortType.OUTPUT))
                    } else {
                        node.outputs[0].name = "IF"
                    }

                    for (i in 1..elseIfCount) {
                        if (i < node.outputs.size && (i < node.outputs.size - 1 || !hasElse)) {
                            node.outputs[i].name = "ELSE IF $i"
                        } else {
                            val insertIdx = if (hasElse && node.outputs.size > i) node.outputs.size - 1 else node.outputs.size
                            node.outputs.add(insertIdx, PortData(id = "OUT_ELSE_IF_$i", name = "ELSE IF $i", type = PortType.OUTPUT))
                        }
                    }

                    if (hasElse) {
                        val elseIdx = 1 + elseIfCount
                        if (node.outputs.size <= elseIdx) {
                            node.outputs.add(PortData(id = "OUT_ELSE", name = "ELSE", type = PortType.OUTPUT))
                        } else {
                            node.outputs[elseIdx].name = "ELSE"
                            while (node.outputs.size > elseIdx + 1) {
                                node.outputs.removeAt(node.outputs.size - 1)
                            }
                        }
                    } else {
                        while (node.outputs.size > 1 + elseIfCount) {
                            node.outputs.removeAt(node.outputs.size - 1)
                        }
                    }
                }

                // 1. Branch 0 (IF)
                val currentVarKey0 = node.params["varKey_0"] ?: node.params["varKey"] ?: projectVariables.firstOrNull()?.id ?: "var_new"
                val selectedVar0 = projectVariables.find { it.id == currentVarKey0 }
                val varType0 = selectedVar0?.type ?: VariableType.STRING

                labels.add(InspectorLabel("🔹 IF Condition:", relY, 0xFF4CAF50.toInt()))
                relY += 12

                labels.add(InspectorLabel("Variable:", relY))
                relY += 12
                val varSelectBtn0 = Button.builder(Component.literal("🔍 $currentVarKey0")) {
                    onOpenVariableSelector?.invoke { selected ->
                        node.params["varKey_0"] = selected.id
                        node.params["varKey"] = selected.id
                        buildUi()
                        onDataChanged()
                    }
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(varSelectBtn0, relY, 16)
                relY += 22

                val ops0 = if (varType0 == VariableType.LIST) {
                    listOf("CONTAINS", "SIZE", "IS_EMPTY", "GET_INDEX")
                } else {
                    listOf("==", "!=", ">", "<", ">=", "<=")
                }
                val currentOp0 = node.params["varOp_0"] ?: node.params["varOp"] ?: ops0.first()
                val validOp0 = if (ops0.contains(currentOp0)) currentOp0 else ops0.first()
                node.params["varOp_0"] = validOp0
                node.params["varOp"] = validOp0

                labels.add(InspectorLabel("Operator:", relY))
                relY += 12
                val opBtn0 = Button.builder(Component.literal("Op: $validOp0")) {
                    val nextOp = ops0[(ops0.indexOf(validOp0) + 1) % ops0.size]
                    node.params["varOp_0"] = nextOp
                    node.params["varOp"] = nextOp
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(opBtn0, relY, 16)
                relY += 22

                if (validOp0 != "IS_EMPTY") {
                    val labelText = when (validOp0) {
                        "CONTAINS" -> "Expected Item:"
                        "SIZE" -> "Expected Size:"
                        "GET_INDEX" -> "Index:Value (e.g. 0:item):"
                        else -> "Target Value:"
                    }
                    labels.add(InspectorLabel(labelText, relY))
                    relY += 12

                    val valEdit0 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal(labelText))
                    valEdit0.value = node.params["varValue_0"] ?: node.params["varValue"] ?: if (validOp0 == "SIZE") "0" else "true"
                    valEdit0.setResponder {
                        node.params["varValue_0"] = it
                        node.params["varValue"] = it
                        onDataChanged()
                    }
                    addWidgetItem(valEdit0, relY, 16)
                    relY += 22
                }

                // 2. Dynamic Else-If Branches 1..elseIfCount
                for (i in 1..elseIfCount) {
                    val currentVarKeyI = node.params["varKey_$i"] ?: projectVariables.firstOrNull()?.id ?: "var_new"
                    val selectedVarI = projectVariables.find { it.id == currentVarKeyI }
                    val varTypeI = selectedVarI?.type ?: VariableType.STRING

                    labels.add(InspectorLabel("🔸 ELSE IF $i:", relY, 0xFFFFB74D.toInt()))
                    relY += 12

                    labels.add(InspectorLabel("Variable:", relY))
                    relY += 12
                    val varSelectBtnI = Button.builder(Component.literal("🔍 $currentVarKeyI")) {
                        onOpenVariableSelector?.invoke { selected ->
                            node.params["varKey_$i"] = selected.id
                            buildUi()
                            onDataChanged()
                        }
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(varSelectBtnI, relY, 16)
                    relY += 22

                    val opsI = if (varTypeI == VariableType.LIST) {
                        listOf("CONTAINS", "SIZE", "IS_EMPTY", "GET_INDEX")
                    } else {
                        listOf("==", "!=", ">", "<", ">=", "<=")
                    }
                    val currentOpI = node.params["varOp_$i"] ?: opsI.first()
                    val validOpI = if (opsI.contains(currentOpI)) currentOpI else opsI.first()
                    node.params["varOp_$i"] = validOpI

                    labels.add(InspectorLabel("Operator:", relY))
                    relY += 12
                    val opBtnI = Button.builder(Component.literal("Op: $validOpI")) {
                        val nextOp = opsI[(opsI.indexOf(validOpI) + 1) % opsI.size]
                        node.params["varOp_$i"] = nextOp
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(opBtnI, relY, 16)
                    relY += 22

                    if (validOpI != "IS_EMPTY") {
                        val labelText = when (validOpI) {
                            "CONTAINS" -> "Expected Item:"
                            "SIZE" -> "Expected Size:"
                            "GET_INDEX" -> "Index:Value (e.g. 0:item):"
                            else -> "Target Value:"
                        }
                        labels.add(InspectorLabel(labelText, relY))
                        relY += 12

                        val valEditI = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal(labelText))
                        valEditI.value = node.params["varValue_$i"] ?: if (validOpI == "SIZE") "0" else "true"
                        valEditI.setResponder {
                            node.params["varValue_$i"] = it
                            onDataChanged()
                        }
                        addWidgetItem(valEditI, relY, 16)
                        relY += 22
                    }

                    val removeBtn = Button.builder(Component.literal("🗑️ Remove Else If $i")) {
                        if (i < node.outputs.size) {
                            node.outputs.removeAt(i)
                        }
                        for (j in i until elseIfCount) {
                            node.params["varKey_$j"] = node.params["varKey_${j + 1}"] ?: ""
                            node.params["varOp_$j"] = node.params["varOp_${j + 1}"] ?: "=="
                            node.params["varValue_$j"] = node.params["varValue_${j + 1}"] ?: ""
                        }
                        node.params.remove("varKey_$elseIfCount")
                        node.params.remove("varOp_$elseIfCount")
                        node.params.remove("varValue_$elseIfCount")
                        node.params["elseIfCount"] = maxOf(0, elseIfCount - 1).toString()

                        syncConditionPorts()
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 14).build()
                    addWidgetItem(removeBtn, relY, 14)
                    relY += 20
                }

                // Button [➕ Add Else If]
                val addElseIfBtn = Button.builder(Component.literal("➕ Add Else If")) {
                    val nextCount = elseIfCount + 1
                    node.params["elseIfCount"] = nextCount.toString()
                    node.params["varKey_$nextCount"] = projectVariables.firstOrNull()?.id ?: "var_new"
                    node.params["varOp_$nextCount"] = "=="
                    node.params["varValue_$nextCount"] = "true"

                    syncConditionPorts()
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(addElseIfBtn, relY, 16)
                relY += 22

                // Fallback ELSE Toggle
                labels.add(InspectorLabel("Fallback ELSE:", relY))
                relY += 12
                val elseToggleBtn = Button.builder(Component.literal(if (hasElse) "🍂 ELSE (Fallback): ENABLED" else "🍂 ELSE (Fallback): DISABLED")) {
                    val nextState = !hasElse
                    node.params["hasElse"] = nextState.toString()
                    if (nextState) {
                        if (node.outputs.none { it.id == "OUT_ELSE" || it.name.equals("ELSE", ignoreCase = true) || it.name.equals("SENÃO", ignoreCase = true) }) {
                            node.outputs.add(PortData(id = "OUT_ELSE", name = "ELSE", type = PortType.OUTPUT))
                        }
                    } else {
                        node.outputs.removeAll { it.id == "OUT_ELSE" || it.name.equals("ELSE", ignoreCase = true) || it.name.equals("SENÃO", ignoreCase = true) || it.name.equals("SENAO", ignoreCase = true) }
                    }
                    syncConditionPorts()
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(elseToggleBtn, relY, 16)
                relY += 22
            }

            NodeType.COMMAND_NODE -> {
                val currentSource = node.params["commandSource"] ?: "SERVER"
                labels.add(InspectorLabel("Execution Source:", relY))
                relY += 12

                val sourceBtn = Button.builder(Component.literal(if (currentSource == "SERVER") "🖥️ Server (OP 4)" else "👤 Local Player")) {
                    node.params["commandSource"] = if (currentSource == "SERVER") "PLAYER" else "SERVER"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(sourceBtn, relY, 16)
                relY += 22

                val isSilent = node.params["silent"] != "false"
                labels.add(InspectorLabel("Silent Mode:", relY))
                relY += 12

                val silentBtn = Button.builder(Component.literal(if (isSilent) "🔇 Silent: YES" else "🔊 Silent: NO")) {
                    node.params["silent"] = (!isSilent).toString()
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(silentBtn, relY, 16)
                relY += 22

                labels.add(InspectorLabel("Commands (1 per line):", relY))
                relY += 12

                val cmdEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 45, Component.literal("Commands"))
                cmdEdit.setMaxLength(1000)
                cmdEdit.value = node.content.ifBlank { node.params["commands"] ?: "" }
                cmdEdit.setResponder { valText ->
                    node.content = valText
                    node.params["commands"] = valText
                    onDataChanged()
                }
                addWidgetItem(cmdEdit, relY, 45)
                relY += 52

                labels.add(InspectorLabel("Tokens: {player}, {var_name}", relY, 0xFF00FFCC.toInt()))
                relY += 16
            }

            NodeType.DIALOGUE -> {
                val isAi = node.params["useAi"] == "true"
                val halfW = (inputW - 2) / 2

                labels.add(InspectorLabel("Generation Mode:", relY))
                relY += 12

                val normBtn = Button.builder(Component.literal("Fixed Text")) {
                    node.params["useAi"] = "false"
                    node.outputs.removeIf { it.id == "OUT_FALLBACK" || it.name.equals("Fallback", true) }
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                if (!isAi) normBtn.active = false

                val aiModeBtn = Button.builder(Component.literal("🤖 AI Generated")) {
                    node.params["useAi"] = "true"
                    if (node.outputs.none { it.id == "OUT_FALLBACK" || it.name.equals("Fallback", true) }) {
                        node.outputs.add(PortData(id = "OUT_FALLBACK", name = "Fallback", type = PortType.OUTPUT))
                    }
                    buildUi()
                    onDataChanged()
                }.bounds(inputX + halfW + 2, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                if (isAi) aiModeBtn.active = false

                addWidgetItem(normBtn, relY, 14)
                addWidgetItem(aiModeBtn, relY, 14)
                relY += 20

                if (isAi) {
                    labels.add(InspectorLabel("AI Instruction Prompt:", relY))
                    relY += 12

                    val cEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 28, Component.literal("Prompt"))
                    cEdit.setMaxLength(2000)
                    cEdit.value = node.content
                    cEdit.setResponder { valText ->
                        node.content = valText
                        onDataChanged()
                    }
                    addWidgetItem(cEdit, relY, 28)
                    relY += 34

                    labels.add(InspectorLabel("AI Configuration:", relY))
                    relY += 12
                    val aiSettingsBtn = Button.builder(Component.literal("🤖 AI Settings...")) {
                        onOpenAIDialogueModal?.invoke(node)
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(aiSettingsBtn, relY, 16)
                    relY += 22
                } else {
                    labels.add(InspectorLabel("Dialogue / Text:", relY))
                    relY += 12

                    val cEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 28, Component.literal("Dialogue"))
                    cEdit.setMaxLength(500)
                    cEdit.value = node.content
                    cEdit.setResponder { valText ->
                        node.content = valText
                        onDataChanged()
                    }
                    addWidgetItem(cEdit, relY, 28)
                    relY += 34
                }

                val currentSpeakerMode = node.params["speakerMode"] ?: "STANDARD"
                labels.add(InspectorLabel("Speech System:", relY))
                relY += 12

                val stdBtn = Button.builder(Component.literal("Standard")) {
                    node.params["speakerMode"] = "STANDARD"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                if (currentSpeakerMode == "STANDARD") stdBtn.active = false

                val cbBtn = Button.builder(Component.literal("CobbleBrain")) {
                    node.params["speakerMode"] = "COBBLEBRAIN"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX + halfW + 2, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                if (currentSpeakerMode == "COBBLEBRAIN") cbBtn.active = false

                addWidgetItem(stdBtn, relY, 14)
                addWidgetItem(cbBtn, relY, 14)
                relY += 20

                if (currentSpeakerMode == "STANDARD") {
                    val currentMsgType = node.params["messageType"] ?: "CHAT"
                    val btnW = (inputW - 4) / 3

                    labels.add(InspectorLabel("Display Mode:", relY))
                    relY += 12

                    val chatBtn = Button.builder(Component.literal("Chat")) {
                        node.params["messageType"] = "CHAT"
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                    if (currentMsgType == "CHAT") chatBtn.active = false

                    val titleBtn = Button.builder(Component.literal("Title")) {
                        node.params["messageType"] = "TITLE"
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX + btnW + 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                    if (currentMsgType == "TITLE") titleBtn.active = false

                    val actionbarBtn = Button.builder(Component.literal("Bar")) {
                        node.params["messageType"] = "ACTION_BAR"
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX + (btnW + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                    if (currentMsgType == "ACTION_BAR") actionbarBtn.active = false

                    addWidgetItem(chatBtn, relY, 14)
                    addWidgetItem(titleBtn, relY, 14)
                    addWidgetItem(actionbarBtn, relY, 14)
                    relY += 20

                    if (currentMsgType == "TITLE") {
                        labels.add(InspectorLabel("Subtitle (Optional):", relY))
                        relY += 12
                        val fSub = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Subtitle"))
                        fSub.value = node.params["subTitle"] ?: ""
                        fSub.setResponder { node.params["subTitle"] = it; onDataChanged() }
                        addWidgetItem(fSub, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Color (Hex/Code, e.g. #FFAA00):", relY))
                        relY += 12
                        val fColor = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Color"))
                        fColor.value = node.params["titleColor"] ?: "#FFAA00"
                        fColor.setResponder { node.params["titleColor"] = it; onDataChanged() }
                        addWidgetItem(fColor, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Fade In (Ticks):", relY))
                        relY += 12
                        val fIn = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Fade In", node.params["fadeIn"] ?: "10") { node.params["fadeIn"] = it }
                        addWidgetItem(fIn, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Stay / Duration (Ticks):", relY))
                        relY += 12
                        val fStay = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Stay", node.params["stay"] ?: "70") { node.params["stay"] = it }
                        addWidgetItem(fStay, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Fade Out (Ticks):", relY))
                        relY += 12
                        val fOut = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Fade Out", node.params["fadeOut"] ?: "20") { node.params["fadeOut"] = it }
                        addWidgetItem(fOut, relY, 16)
                        relY += 22
                    }
                } else {
                    val speakerTypes = listOf(
                        "PARTY_FIRST" to "1st Active Party",
                        "PARTY_SLOT" to "Specific Slot (1-6)",
                        "PARTY_RANDOM" to "Random Party",
                        "NEAREST_WILD" to "Nearest Wild",
                        "BY_SPECIES" to "By Species / Name",
                        "NPC" to "👤 Target Mob / NPC (Tag)",
                        "CUSTOM_NAME" to "Custom Name"
                    )
                    val currentSpeakerType = node.params["speakerType"] ?: "PARTY_FIRST"
                    val currentSpeakerLabel = speakerTypes.find { it.first == currentSpeakerType }?.second ?: "1st Active Party"

                    labels.add(InspectorLabel("Speaker:", relY))
                    relY += 12

                    val spkBtn = Button.builder(Component.literal("🗣️ $currentSpeakerLabel")) {
                        val curIdx = speakerTypes.indexOfFirst { it.first == currentSpeakerType }
                        val nextIdx = (curIdx + 1) % speakerTypes.size
                        node.params["speakerType"] = speakerTypes[nextIdx].first
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(spkBtn, relY, 16)
                    relY += 22

                    if (currentSpeakerType == "NPC") {
                        labels.add(InspectorLabel("Target Mob / NPC Tag:", relY))
                        relY += 12
                        val tagEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                        tagEdit.setHint(Component.literal("§8Entity Story Tag or Selector (e.g. guide_npc)"))
                        tagEdit.value = node.params["entityStoryTag"] ?: node.params["speakerIdentifier"] ?: ""
                        tagEdit.setResponder {
                            node.params["entityStoryTag"] = it
                            node.params["speakerIdentifier"] = it
                            onDataChanged()
                        }
                        addWidgetItem(tagEdit, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Speaker Display Name (Optional):", relY))
                        relY += 12
                        val nameEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Display Name"))
                        nameEdit.setHint(Component.literal("§8Display Name (empty = auto mob name)"))
                        nameEdit.value = node.params["customSpeakerName"] ?: ""
                        nameEdit.setResponder { node.params["customSpeakerName"] = it; onDataChanged() }
                        addWidgetItem(nameEdit, relY, 16)
                        relY += 22
                    } else if (currentSpeakerType == "PARTY_SLOT") {
                        labels.add(InspectorLabel("Party Slot (1 - 6):", relY))
                        relY += 12
                        val slotEdit = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Slot", node.params["partySlot"] ?: "1") {
                            val num = it.toIntOrNull()?.coerceIn(1, 6) ?: 1
                            node.params["partySlot"] = num.toString()
                        }
                        addWidgetItem(slotEdit, relY, 16)
                        relY += 22
                    } else if (currentSpeakerType == "BY_SPECIES") {
                        labels.add(InspectorLabel("Species / Nickname:", relY))
                        relY += 12
                        val specEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Species"))
                        specEdit.value = node.params["targetSpecies"] ?: "Pikachu"
                        specEdit.setResponder { node.params["targetSpecies"] = it; onDataChanged() }
                        addWidgetItem(specEdit, relY, 16)
                        relY += 22
                    } else if (currentSpeakerType == "CUSTOM_NAME") {
                        labels.add(InspectorLabel("Speaker Custom Name:", relY))
                        relY += 12
                        val nameEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Name"))
                        nameEdit.value = node.params["customSpeakerName"] ?: "Professor"
                        nameEdit.setResponder { node.params["customSpeakerName"] = it; onDataChanged() }
                        addWidgetItem(nameEdit, relY, 16)
                        relY += 22
                    }

                    val nameFormat = node.params["nameFormat"] ?: "PREFIX"
                    labels.add(InspectorLabel("Chat Name Display:", relY))
                    relY += 12
                    val fmtBtn = Button.builder(Component.literal(if (nameFormat == "PREFIX") "🏷️ [Name] Message" else "💬 Message Only")) {
                        node.params["nameFormat"] = if (nameFormat == "PREFIX") "NO_PREFIX" else "PREFIX"
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(fmtBtn, relY, 16)
                    relY += 22

                    val emotions = listOf(
                        "NEUTRAL" to "😐 Neutral (1.0x)",
                        "HAPPY" to "💖 Happy (1.25x)",
                        "SAD" to "💢 Sad/Angry (0.75x)",
                        "EXCITED" to "⚡ Excited (1.4x)",
                        "CUSTOM" to "⚙️ Custom Pitch"
                    )
                    val currentEmotion = node.params["emotionPitch"] ?: "NEUTRAL"
                    val currentEmotionLabel = emotions.find { it.first == currentEmotion }?.second ?: "😐 Neutral (1.0x)"

                    labels.add(InspectorLabel("Emotion / Voice Pitch:", relY))
                    relY += 12

                    val emoBtn = Button.builder(Component.literal(currentEmotionLabel)) {
                        val curIdx = emotions.indexOfFirst { it.first == currentEmotion }
                        val nextIdx = (curIdx + 1) % emotions.size
                        node.params["emotionPitch"] = emotions[nextIdx].first
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(emoBtn, relY, 16)
                    relY += 22

                    if (currentEmotion == "CUSTOM") {
                        labels.add(InspectorLabel("Pitch (0.5 to 2.0):", relY))
                        relY += 12
                        val pitchEdit = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Pitch", node.params["customPitch"] ?: "1.0") { node.params["customPitch"] = it }
                        addWidgetItem(pitchEdit, relY, 16)
                        relY += 22
                    }

                    val bubbleOn = node.params["enableChatBubble"] != "false"
                    labels.add(InspectorLabel("3D Chat Bubble:", relY))
                    relY += 12
                    val bubbleBtn = Button.builder(Component.literal(if (bubbleOn) "💭 3D Bubble: ON" else "💭 3D Bubble: OFF")) {
                        node.params["enableChatBubble"] = if (bubbleOn) "false" else "true"
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(bubbleBtn, relY, 16)
                    relY += 22

                    if (bubbleOn) {
                        labels.add(InspectorLabel("Bubble Duration (Ticks):", relY))
                        relY += 12
                        val bDurEdit = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Ticks", node.params["bubbleDuration"] ?: "100") { node.params["bubbleDuration"] = it }
                        addWidgetItem(bDurEdit, relY, 16)
                        relY += 22
                    }

                    val playCry = node.params["playCry"] != "false"
                    val cryBtn = Button.builder(Component.literal(if (playCry) "🔊 Play Cry: YES" else "🔊 Play Cry: NO")) {
                        node.params["playCry"] = if (playCry) "false" else "true"
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(cryBtn, relY, 16)
                    relY += 20

                    val socialLook = node.params["socialLook"] != "false"
                    val lookBtn = Button.builder(Component.literal(if (socialLook) "👀 Look at Player: YES" else "👀 Look at Player: NO")) {
                        node.params["socialLook"] = if (socialLook) "false" else "true"
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(lookBtn, relY, 16)
                    relY += 20

                    val jumpEffect = node.params["jumpEffect"] != "false"
                    val jumpBtn = Button.builder(Component.literal(if (jumpEffect) "🦘 Jump Effect: YES" else "🦘 Jump Effect: NO")) {
                        node.params["jumpEffect"] = if (jumpEffect) "false" else "true"
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(jumpBtn, relY, 16)
                    relY += 20

                    val sendToChat = node.params["sendToChat"] != "false"
                    val chatToggleBtn = Button.builder(Component.literal(if (sendToChat) "💬 Show in Chat: YES" else "💬 Show in Chat: NO")) {
                        node.params["sendToChat"] = if (sendToChat) "false" else "true"
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(chatToggleBtn, relY, 16)
                    relY += 22
                }
            }

            NodeType.TIMER -> {
                labels.add(InspectorLabel("Wait (Seconds):", relY))
                relY += 12

                val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Seconds", node.params["timerSeconds"] ?: "5") { node.params["timerSeconds"] = it }
                addWidgetItem(f1, relY, 16)
                relY += 22
            }

            NodeType.SAVE_STATE_NODE, NodeType.LOAD_STATE_NODE, NodeType.CHECKPOINT_NODE -> {
                val cfgBtn = Button.builder(Component.literal("⚙️ Configure Save/Load Profile...")) {
                    onOpenProfileModal?.invoke(node)
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(cfgBtn, relY, 16)
                relY += 22

                labels.add(InspectorLabel("Profile ID / Slot:", relY))
                relY += 12
                val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Profile ID"))
                f1.value = node.params["profileId"] ?: "checkpoint_1"
                f1.setResponder { node.params["profileId"] = it; onDataChanged() }
                addWidgetItem(f1, relY, 16)
                relY += 22

                val currentScope = node.params["scope"] ?: "PLAYER"
                val scopeBtn = Button.builder(Component.literal("Scope: $currentScope")) {
                    node.params["scope"] = if (currentScope == "PLAYER") "GLOBAL" else "PLAYER"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(scopeBtn, relY, 16)
                relY += 22

                if (node.nodeType == NodeType.SAVE_STATE_NODE || (node.nodeType == NodeType.CHECKPOINT_NODE && node.params["checkpointMode"] != "LOAD")) {
                    labels.add(InspectorLabel("Modules (ALL or list):", relY))
                    relY += 12
                    val fMod = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Modules"))
                    fMod.value = node.params["modules"] ?: "ALL"
                    fMod.setResponder { node.params["modules"] = it; onDataChanged() }
                    addWidgetItem(fMod, relY, 16)
                    relY += 22
                }

                if (node.nodeType == NodeType.LOAD_STATE_NODE || (node.nodeType == NodeType.CHECKPOINT_NODE && node.params["checkpointMode"] == "LOAD")) {
                    val currentMerge = node.params["mergeMode"] ?: "OVERWRITE"
                    val mergeBtn = Button.builder(Component.literal("Merge: $currentMerge")) {
                        node.params["mergeMode"] = if (currentMerge == "OVERWRITE") "SOFT_MERGE" else "OVERWRITE"
                        buildUi()
                        onDataChanged()
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(mergeBtn, relY, 16)
                    relY += 22

                    labels.add(InspectorLabel("Jump Target Node ID:", relY))
                    relY += 12
                    val fJump = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Target Node ID"))
                    fJump.value = node.params["jumpToTargetNodeId"] ?: ""
                    fJump.setResponder { node.params["jumpToTargetNodeId"] = it; onDataChanged() }
                    addWidgetItem(fJump, relY, 16)
                    relY += 22

                    labels.add(InspectorLabel("Grace Period (Ticks):", relY))
                    relY += 12
                    val fGrace = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Grace Ticks", node.params["gracePeriodTicks"] ?: "60") { node.params["gracePeriodTicks"] = it }
                    addWidgetItem(fGrace, relY, 16)
                    relY += 22

                    labels.add(InspectorLabel("Clean Story Tag:", relY))
                    relY += 12
                    val fClean = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                    fClean.value = node.params["cleanStoryTag"] ?: ""
                    fClean.setResponder { node.params["cleanStoryTag"] = it; onDataChanged() }
                    addWidgetItem(fClean, relY, 16)
                    relY += 22
                }
            }

            NodeType.TEXTURE -> {
                val targetType = node.params["targetType"] ?: "NPC_TAG"
                val halfW = (inputW - 2) / 2

                labels.add(InspectorLabel("Target Entity Type:", relY))
                relY += 12
                val bPoke = Button.builder(Component.literal("🐾 Cobblemon")) {
                    node.params["targetType"] = "PLAYER_POKEMON"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                if (targetType == "PLAYER_POKEMON") bPoke.active = false

                val bNpc = Button.builder(Component.literal("👤 NPC / Mob")) {
                    node.params["targetType"] = "NPC_TAG"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX + halfW + 2, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                if (targetType == "NPC_TAG") bNpc.active = false

                addWidgetItem(bPoke, relY, 14); addWidgetItem(bNpc, relY, 14)
                relY += 20

                if (targetType == "PLAYER_POKEMON") {
                    labels.add(InspectorLabel("Party Slot (0-5):", relY))
                    relY += 12
                    val fSlot = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Slot", node.params["pokemonSlot"] ?: "0") { node.params["pokemonSlot"] = it }
                    addWidgetItem(fSlot, relY, 16)
                    relY += 22
                } else {
                    labels.add(InspectorLabel("Entity Story Tag:", relY))
                    relY += 12
                    val fTag = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Story Tag"))
                    fTag.value = node.params["targetIdentifier"] ?: ""
                    fTag.setResponder { node.params["targetIdentifier"] = it; onDataChanged() }
                    addWidgetItem(fTag, relY, 16)
                    relY += 22
                }

                val textureMode = node.params["textureMode"] ?: "SET_TEXTURE"
                labels.add(InspectorLabel("Texture Action Mode:", relY))
                relY += 12
                val bSet = Button.builder(Component.literal("Apply Texture")) {
                    node.params["textureMode"] = "SET_TEXTURE"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                if (textureMode == "SET_TEXTURE") bSet.active = false

                val bClear = Button.builder(Component.literal("Reset Default")) {
                    node.params["textureMode"] = "CLEAR_TEXTURE"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX + halfW + 2, (panelY + 20 + relY - scrollOffset).toInt(), halfW, 14).build()
                if (textureMode == "CLEAR_TEXTURE") bClear.active = false

                addWidgetItem(bSet, relY, 14); addWidgetItem(bClear, relY, 14)
                relY += 20

                if (textureMode == "SET_TEXTURE") {
                    val currentTex = node.params["textureName"] ?: ""
                    labels.add(InspectorLabel("Selected Texture:", relY))
                    relY += 12
                    val fTex = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Texture Name"))
                    fTex.value = currentTex
                    fTex.setResponder { node.params["textureName"] = it; onDataChanged() }
                    addWidgetItem(fTex, relY, 16)
                    relY += 22

                    val bBrowse = Button.builder(Component.literal("📁 Browse Story Textures")) {
                        onOpenTextureSelector?.invoke(node) { chosen ->
                            node.params["textureName"] = chosen
                            buildUi()
                            onDataChanged()
                        }
                    }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                    addWidgetItem(bBrowse, relY, 16)
                    relY += 22
                }
            }
        }

        // 3. Node Lifecycle Actions: Dissociate & Delete
        relY += 10
        labels.add(InspectorLabel("─ Actions ─", relY, 0xFF555566.toInt()))
        relY += 14

        if (!node.parentSceneId.isNullOrEmpty()) {
            val detachBtn = Button.builder(Component.literal("✂️ Detach from Scene")) {
                onDissociateNode?.invoke(node)
            }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
            addWidgetItem(detachBtn, relY, 16)
            relY += 20
        }

        val deleteBtn = Button.builder(Component.literal("🗑️ Delete Node")) {
            onDeleteNode?.invoke(node)
        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
        addWidgetItem(deleteBtn, relY, 16)
        relY += 22

        totalContentHeight = relY.toDouble()
        updateWidgetPositions()
    }

    private class NumEditBox(font: Font, x: Int, y: Int, w: Int, h: Int, title: Component) : EditBox(font, x, y, w, h, title)

    private fun addWidgetItem(widget: GuiEventListener, relY: Int, height: Int) {
        if (widget is EditBox) {
            widget.setEditable(true)
            widget.active = true
            if (widget !is NumEditBox) {
                // Ensure all text EditBoxes have at least 2000 character capacity
                widget.setMaxLength(2000)
            }
        }
        childrenWidgets.add(widget)
        val relX = when (widget) {
            is Button -> widget.x - panelX
            is EditBox -> widget.x - panelX
            else -> 6
        }
        val width = when (widget) {
            is Button -> widget.width
            is EditBox -> widget.width
            else -> panelWidth - 12
        }
        widgetItems.add(InspectorWidgetItem(widget, relX, relY, width, height))
    }

    private fun updateWidgetPositions() {
        widgetItems.forEach { item ->
            val px = panelX + item.relX
            val py = (panelY + 20 + item.relY - scrollOffset).toInt()
            if (item.widget is Button) {
                item.widget.x = px
                item.widget.y = py
                item.widget.width = item.width
            } else if (item.widget is EditBox) {
                item.widget.x = px
                item.widget.y = py
                item.widget.width = item.width
            }
        }
    }

    private fun createNumEdit(x: Int, y: Int, w: Int, label: String, initialVal: String, onUpdate: (String) -> Unit): EditBox {
        val eb = NumEditBox(font, x, y, w, 16, Component.literal(label))
        eb.value = initialVal
        eb.setMaxLength(32)
        eb.setEditable(true)
        eb.active = true
        eb.setFilter { text -> text.isEmpty() || text.all { it.isDigit() || it == '-' || it == '.' } }
        eb.setResponder { valText ->
            onUpdate(valText)
            onDataChanged()
        }
        return eb
    }

    private fun addCoordinateInputSection(
        label: String,
        initialValue: String,
        inputX: Int,
        inputW: Int,
        relYStart: Int,
        showSafetyControls: Boolean = true,
        onUpdate: (String) -> Unit
    ): Int {
        var curY = relYStart
        labels.add(InspectorLabel(label, curY))
        curY += 12

        val coordEdit = EditBox(font, inputX, (panelY + 20 + curY - scrollOffset).toInt(), inputW, 16, Component.literal(label))
        coordEdit.setHint(Component.literal("§8~0 ~0 ~0, ^0 ^0 ^3, @tag"))
        coordEdit.value = initialValue
        coordEdit.setMaxLength(128)
        coordEdit.setResponder {
            onUpdate(it)
            onDataChanged()
        }
        addWidgetItem(coordEdit, curY, 16)
        curY += 20

        val bModal = Button.builder(Component.literal("🧭 Positioning & Safety...")) {
            onOpenCoordinateModal?.invoke(node) {
                buildUi()
                onDataChanged()
            }
        }.bounds(inputX, (panelY + 20 + curY - scrollOffset).toInt(), inputW, 16).build()
        addWidgetItem(bModal, curY, 16)
        curY += 18

        if (showSafetyControls) {
            val isSafe = node.params["safePosition"] != "false"
            val isSnap = node.params["snapToGround"] != "false"
            val prio = node.params["searchPriority"] ?: "CLOSEST"
            val safeIcon = if (isSafe) "§a🛡️Safe" else "§7🛡️Off"
            val snapIcon = if (isSnap) "§b⚓Ground" else "§7⚓Off"
            val prioIcon = when (prio) {
                "SURFACE" -> "§e☀️Surface"
                "UNDERGROUND" -> "§6⛏️Caves"
                "RANDOM" -> "§d🎲Random"
                else -> "§3🎯Closest"
            }
            labels.add(InspectorLabel("$safeIcon §8| $snapIcon §8| $prioIcon", curY, 0xFF94A3B8.toInt()))
            curY += 14
        }

        return curY
    }

    private fun renderMiniMap(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        guiGraphics.fill(x, y, x + w, y + h, 0xFF0D0D12.toInt())
        guiGraphics.fill(x - 1, y - 1, x + w + 1, y, 0xFF3D5AFE.toInt())
        guiGraphics.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF3D5AFE.toInt())
        guiGraphics.fill(x - 1, y, x, y + h, 0xFF3D5AFE.toInt())
        guiGraphics.fill(x + w, y, x + w + 1, y + h, 0xFF3D5AFE.toInt())

        val inner = node.innerNodes
        if (inner.isEmpty()) {
            guiGraphics.drawString(font, "Empty", x + w / 2 - 12, y + h / 2 - 4, 0xFF555566.toInt(), false)
            return
        }

        val minX = inner.minOf { it.x }
        val minY = inner.minOf { it.y }
        val maxX = inner.maxOf { it.x + it.width }.coerceAtLeast(minX + 1.0)
        val maxY = inner.maxOf { it.y + it.height }.coerceAtLeast(minY + 1.0)

        val boundsW = maxX - minX
        val boundsH = maxY - minY

        val scaleX = (w - 12) / boundsW
        val scaleY = (h - 12) / boundsH
        val scale = minOf(scaleX, scaleY).coerceIn(0.05, 0.5)

        node.innerConnections.forEach { conn ->
            val from = inner.find { it.id == conn.fromNodeId }
            val to = inner.find { it.id == conn.toNodeId }
            if (from != null && to != null) {
                val fx = (x + 6 + (from.x + from.width - minX) * scale).toInt()
                val fy = (y + 6 + (from.y + 20 - minY) * scale).toInt()
                val tx = (x + 6 + (to.x - minX) * scale).toInt()
                val ty = (y + 6 + (to.y + 20 - minY) * scale).toInt()
                guiGraphics.fill(minOf(fx, tx), minOf(fy, ty), maxOf(fx, tx) + 1, maxOf(fy, ty) + 1, 0xFF4CAF50.toInt())
            }
        }

        inner.forEach { sub ->
            val nx = (x + 6 + (sub.x - minX) * scale).toInt()
            val ny = (y + 6 + (sub.y - minY) * scale).toInt()
            val nw = (sub.width * scale).toInt().coerceAtLeast(4)
            val nh = (sub.height * scale).toInt().coerceAtLeast(4)

            val color = when (sub.nodeType) {
                NodeType.BEGIN_SCENE -> 0xFF388E3C.toInt()
                NodeType.TRIGGER -> 0xFF2E7D32.toInt()
                NodeType.ACTION -> 0xFFC62828.toInt()
                NodeType.TIMER -> 0xFF6A1B9A.toInt()
                NodeType.CONDITION_NODE -> 0xFF1565C0.toInt()
                NodeType.COMMAND_NODE -> 0xFFD84315.toInt()
                NodeType.DIALOGUE -> 0xFFC62828.toInt()
                NodeType.END_SCENE -> 0xFFD32F2F.toInt()
                NodeType.GATE -> 0xFF00B0FF.toInt()
                NodeType.LINK_SEND -> 0xFF00E676.toInt()
                NodeType.LINK_RECEIVE -> 0xFF0288D1.toInt()
                NodeType.LOOP -> 0xFFFF6D00.toInt()
                NodeType.COMMENT -> 0xFFFBC02D.toInt()
                NodeType.VARIABLE_GET -> 0xFF00ACC1.toInt()
                NodeType.VARIABLE_SET -> 0xFFFFA000.toInt()
                NodeType.CONSTRUCTION -> 0xFF00838F.toInt()
                NodeType.QUEST -> 0xFFFF8F00.toInt()
                NodeType.AUDIO -> 0xFF6A1B9A.toInt()
                NodeType.SAVE_STATE_NODE -> 0xFF4A148C.toInt()
                NodeType.LOAD_STATE_NODE -> 0xFF311B92.toInt()
                NodeType.CHECKPOINT_NODE -> 0xFF512DA8.toInt()
                NodeType.TEXTURE -> 0xFF9333EA.toInt()
            }
            guiGraphics.fill(nx, ny, nx + nw, ny + nh, color)
        }
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0141418.toInt())
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF3D5AFE.toInt())

        val viewportTop = panelY + 20
        val viewportBottom = panelY + panelHeight
        val viewportHeight = panelHeight - 20

        guiGraphics.enableScissor(panelX, viewportTop, panelX + panelWidth, viewportBottom)

        labels.forEach { lbl ->
            val ly = (viewportTop + lbl.relY - scrollOffset).toInt()
            if (ly >= viewportTop - 12 && ly <= viewportBottom) {
                guiGraphics.drawString(font, lbl.text, panelX + 6, ly, lbl.color, false)
            }
        }

        if (node.nodeType == NodeType.CONSTRUCTION) {
            val constrMiniMapRelY = labels.find { it.text == "Mini-Mapa Interno:" }?.relY ?: 50
            val miniMapY = (viewportTop + constrMiniMapRelY + 14 - scrollOffset).toInt()
            if (miniMapY + 65 >= viewportTop && miniMapY <= viewportBottom) {
                renderMiniMap(guiGraphics, panelX + 6, miniMapY, panelWidth - 12, 65)
            }
        }

        childrenWidgets.toList().forEach { widget ->
            if (widget != closeBtn) {
                val wy = when (widget) {
                    is Button -> widget.y
                    is EditBox -> widget.y
                    else -> viewportTop
                }
                if (wy + 16 >= viewportTop && wy <= viewportBottom) {
                    if (widget is Button) widget.render(guiGraphics, mouseX, mouseY, partialTick)
                    if (widget is EditBox) widget.render(guiGraphics, mouseX, mouseY, partialTick)
                }
            }
        }

        guiGraphics.disableScissor()

        val maxScroll = maxOf(0.0, totalContentHeight - viewportHeight)
        if (maxScroll > 0) {
            val sbX = panelX + panelWidth - 3
            val thumbH = ((viewportHeight.toDouble() / totalContentHeight) * viewportHeight).toInt().coerceIn(12, viewportHeight)
            val thumbY = viewportTop + ((scrollOffset / maxScroll) * (viewportHeight - thumbH)).toInt()

            guiGraphics.fill(sbX, viewportTop, sbX + 2, viewportBottom, 0xFF1C1C24.toInt())
            guiGraphics.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, 0xFF00FFCC.toInt())
        }

        // Cabeçalho Fixo no Topo
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 20, 0xFF22222A.toInt())
        guiGraphics.fill(panelX, panelY + 19, panelX + panelWidth, panelY + 20, 0xFF3D5AFE.toInt())

        val headerTitle = font.plainSubstrByWidth(node.title, panelWidth - 26)
        guiGraphics.drawString(font, headerTitle, panelX + 6, panelY + 5, 0xFF00FFCC.toInt(), false)

        closeBtn.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY + 20 && mouseY <= panelY + panelHeight) {
            val viewportHeight = panelHeight - 20
            val maxScroll = maxOf(0.0, totalContentHeight - viewportHeight)
            if (maxScroll > 0) {
                if (scrollY > 0) {
                    scrollOffset = (scrollOffset - 18.0).coerceAtLeast(0.0)
                } else if (scrollY < 0) {
                    scrollOffset = (scrollOffset + 18.0).coerceAtMost(maxScroll)
                }
                updateWidgetPositions()
                return true
            }
        }
        return false
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (mouseX < panelX || mouseX > panelX + panelWidth || mouseY < panelY || mouseY > panelY + panelHeight) {
            focusedEditBox?.isFocused = false
            focusedEditBox = null
            return false
        }

        if (closeBtn.mouseClicked(mouseX, mouseY, button)) return true

        val viewportTop = panelY + 20
        val viewportBottom = panelY + panelHeight

        var handled = false
        val snapshot = childrenWidgets.toList()
        for (w in snapshot) {
            if (w == closeBtn) continue

            val wy = when (w) {
                is Button -> w.y
                is EditBox -> w.y
                else -> viewportTop
            }

            if (wy + 14 >= viewportTop && wy <= viewportBottom) {
                if (w is EditBox) {
                    val clicked = w.mouseClicked(mouseX, mouseY, button)
                    if (clicked) {
                        w.isFocused = true
                        focusedEditBox = w
                        handled = true
                    } else {
                        w.isFocused = false
                    }
                } else if (w.mouseClicked(mouseX, mouseY, button)) {
                    handled = true
                }
            }
        }
        if (!handled) {
            focusedEditBox?.isFocused = false
            focusedEditBox = null
        }
        return true
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        val focused = focusedEditBox
        if (focused != null && focused.isFocused) {
            return focused.charTyped(codePoint, modifiers)
        }
        val snapshot = childrenWidgets.toList()
        for (w in snapshot) {
            if (w is EditBox && w.isFocused) {
                if (w.charTyped(codePoint, modifiers)) return true
            }
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val focused = focusedEditBox
        if (focused != null && focused.isFocused) {
            if (focused.keyPressed(keyCode, scanCode, modifiers)) return true
        }
        val snapshot = childrenWidgets.toList()
        for (w in snapshot) {
            if (w is EditBox && w.isFocused) {
                if (w.keyPressed(keyCode, scanCode, modifiers)) return true
            }
        }
        return false
    }
}
