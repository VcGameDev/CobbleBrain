package vito.cobblebrain.client.gui.widgets

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import vito.cobblebrain.client.AIClientHandler
import vito.cobblebrain.client.AIHandler
import vito.cobblebrain.client.gui.StoryEditorScreen
import vito.cobblebrain.model.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

class StoryEditorAIAssistantWidget(
    private val editor: StoryEditorScreen,
    private val font: Font
) {
    enum class ChatRole {
        USER,
        ASSISTANT,
        CALL_TRACE,
        QUESTION,
        ERROR
    }

    data class ChatEntry(
        val role: ChatRole,
        val badge: String,
        val text: String,
        val accentColor: Int
    )

    data class InteractiveQuestionState(
        val questionText: String,
        val options: List<String>, // 3 AI options + 4th "Outro (Digitar...)"
        val responseFuture: CompletableFuture<String>,
        var isTypingCustomOther: Boolean = false
    )

    data class CompletedInteractionMemory(
        val originalUserQuestion: String,
        val finalAiResponse: String
    )

    /**
     * A "Prancheta Compartilhada" (Blackboard) que passa de um robô (chamada) para o outro,
     * já que cada chamada de IA é uma mensagem única sem memória de sessão.
     */
    class SharedBlackboard(val userRequest: String, val playerFocusSummary: String) {
        var intentSummary: String = "Processando solicitação..."
        val markedSceneIds = LinkedHashSet<String>()
        val markedNodeIds = LinkedHashSet<String>()
        val qaPairs = mutableListOf<String>()         // Perguntas feitas pela IA e respostas do jogador
        val jsonAnalysisNotes = mutableListOf<String>() // Notas da Chamada 5 (apenas sobre os [MARKED])
        val blockGuideNotes = mutableListOf<String>()   // Notas da Chamada 2 (blocos escolhidos e opções)
        val executionNotes = mutableListOf<String>()    // Log do que a Chamada 3 já desenhou no projeto

        fun renderPaperForNextRobot(
            currentTaskInstruction: String,
            includeBlockGuideDraft: Boolean = true
        ): String {
            return buildString {
                appendLine("╔══════════════════════════════════════════════════════════════════╗")
                appendLine("║         PRANCHETA COMPARTILHADA ENTRE OS ROBÔS (BLACKBOARD)      ║")
                appendLine("╚══════════════════════════════════════════════════════════════════╝")
                if (recentCompletedInteractions.isNotEmpty()) {
                    appendLine("0. [MEMÓRIA DAS ÚLTIMAS ${recentCompletedInteractions.size} REQUISIÇÕES ANTERIORES DO JOGADOR]:")
                    recentCompletedInteractions.takeLast(2).forEachIndexed { idx, mem ->
                        appendLine("   • [Pedido Anterior #${idx + 1}]: ${mem.originalUserQuestion}")
                        appendLine("     [Resposta Final #${idx + 1}]: ${mem.finalAiResponse.take(350)}")
                    }
                }
                appendLine("1. [PEDIDO ORIGINAL DO JOGADOR ATUAL]: $userRequest")
                appendLine("2. [INTENÇÃO GERAL (CARIMBO ROBÔ 1)]: $intentSummary")
                appendLine("3. [TAREFA ESPECÍFICA DESTA CHAMADA]: $currentTaskInstruction")
                appendLine("4. [FOCO MARCADO PELO JOGADOR NA TELA]:")
                appendLine(playerFocusSummary.ifBlank { "Nenhum filtro manual — pode trabalhar na cena ativa." })
                if (qaPairs.isNotEmpty()) {
                    appendLine("5. [PERGUNTAS E RESPOSTAS DO JOGADOR (CHAMADA DE PERGUNTA)]:")
                    qaPairs.forEachIndexed { idx, qa -> appendLine("   (${idx + 1}) $qa") }
                }
                if (markedSceneIds.isNotEmpty() || markedNodeIds.isNotEmpty()) {
                    appendLine("6. [ITENS MARCADOS COM [MARKED] NO FUNIL JSON]:")
                    appendLine("   - Cenas [MARKED]: ${if (markedSceneIds.isEmpty()) " Cena Ativa" else markedSceneIds.joinToString(", ")}")
                    appendLine("   - Blocos [MARKED]: ${if (markedNodeIds.isEmpty()) "Todos da cena" else markedNodeIds.joinToString(", ")}")
                }
                if (jsonAnalysisNotes.isNotEmpty()) {
                    appendLine("7. [ANÁLISE DOS ARQUIVOS DE BLOCOS E CONEXÕES (ROBÔ 5)]:")
                    appendLine(jsonAnalysisNotes.joinToString("\n---\n"))
                }
                if (blockGuideNotes.isNotEmpty() && includeBlockGuideDraft) {
                    appendLine("8. [ESPECIFICAÇÃO DOS BLOCOS E OPÇÕES ESCOLHIDAS (ROBÔ 2)]:")
                    appendLine(blockGuideNotes.joinToString("\n---\n"))
                }
                if (executionNotes.isNotEmpty()) {
                    appendLine("9. [ALTERAÇÕES JÁ APLICADAS NO PROJETO (ROBÔ 3)]:")
                    appendLine(executionNotes.joinToString("\n---\n"))
                }
            }
        }
    }

    companion object {
        const val END_TOKEN = "[ENDTOKEN]"
        const val MARKED_TAG = "[MARKED]"

        // Persist chat history and state across screen rebuilds (init()) during the same editing session
        val chatHistory = CopyOnWriteArrayList<ChatEntry>()
        // Memory of the last 2 completed original user questions and their final AI answers
        val recentCompletedInteractions = CopyOnWriteArrayList<CompletedInteractionMemory>()
        val callCounts = java.util.concurrent.ConcurrentHashMap<Int, Int>()
        @Volatile var isRunningPipeline: Boolean = false
        @Volatile var activeCallStage: Int = 0 // 0=Idle, 1=Call1, 51=Call5a, 52=Call5b, 21=Call2a, 22=Call2b, 6=Question, 3=Call3, 4=Call4
        @Volatile var currentStatusText: String = Component.translatable("cobblebrain.story_editor.ai.status_ready").string
        @Volatile var completedCallsMask: Int = 0
        @Volatile var totalEstimatedTokens: Int = 0
        @Volatile var currentRequestTokens: Int = 0
        var savedInputText: String = ""

        // Feature 2: Player Focus Selection (marked nodes / scenes on canvas)
        @Volatile var focusOnSelectionEnabled: Boolean = true
        val pinnedFocusNodeIds = CopyOnWriteArrayList<String>()
        val pinnedFocusSceneIds = CopyOnWriteArrayList<String>()

        // Feature 3: Backup Snapshot for Accept / Reject Changes (Antigravity style)
        @Volatile var projectBackupSnapshot: StoryProject? = null
        @Volatile var pendingChangesCount: Int = 0

        // Feature 4: Interactive 4-Option Question State
        @Volatile var activeQuestion: InteractiveQuestionState? = null
    }

    var panelX: Int = 0
    var panelY: Int = 0
    var panelW: Int = 200
    var panelH: Int = 300

    private var chatScroll: Float = 0f
    private var lastTotalContentH: Float = 0f
    private var isDraggingScrollbar: Boolean = false
    private var dragStartMouseY: Double = 0.0
    private var dragStartScroll: Float = 0f
    private var autoScrollToBottom: Boolean = true

    private lateinit var inputBox: EditBox

    private fun getEffectiveFocusedNodes(): List<NodeData> {
        if (!focusOnSelectionEnabled) return emptyList()
        val allNodes = editor.project.getAllNodes()
        val combinedIds = LinkedHashSet<String>()
        // 1. Always include all nodes explicitly marked/pinned for AI Focus (cumulative multi-block list)
        combinedIds.addAll(pinnedFocusNodeIds)
        // 2. Also include any multi-selection on the canvas (Shift+click or Box drag of 2+ nodes)
        if (editor.selectedWidgets.size > 1) {
            editor.selectedWidgets.forEach { combinedIds.add(it.node.id) }
        }
        if (combinedIds.isEmpty()) return emptyList()
        return allNodes.filter { combinedIds.contains(it.id) }
    }

    private fun getEffectiveFocusedScene(): SceneData? {
        if (!focusOnSelectionEnabled) return null
        editor.activeSceneInspector?.scene?.let { return it }
        if (pinnedFocusSceneIds.isNotEmpty()) {
            return editor.project.scenes.find { pinnedFocusSceneIds.contains(it.id) }
        }
        return null
    }

    private fun buildPlayerFocusSummary(): String {
        if (!focusOnSelectionEnabled) {
            return "Modo de foco manual desativado pelo jogador (escopo livre na cena ativa)."
        }
        val focusedNodes = getEffectiveFocusedNodes()
        val focusedScene = getEffectiveFocusedScene()
        if (focusedNodes.isEmpty() && focusedScene == null) {
            val act = editor.project.getActiveScene()
            return "Nenhum bloco individual selecionado; cena ativa em foco: '${act?.title ?: "Nenhuma"}' (id=${act?.id ?: "none"})."
        }
        return buildString {
            if (focusedScene != null) {
                appendLine("• $MARKED_TAG Cena fixada pelo jogador: '${focusedScene.title}' (id=${focusedScene.id})")
            }
            if (focusedNodes.isNotEmpty()) {
                appendLine("• $MARKED_TAG Blocos selecionados pelo jogador para FOCO EXCLUSIVO (${focusedNodes.size}):")
                for (n in focusedNodes) {
                    val sc = editor.project.scenes.find { it.id == n.parentSceneId }
                    val relX = (n.x - (sc?.x ?: 0.0)).toInt()
                    val relY = (n.y - (sc?.y ?: 0.0)).toInt()
                    appendLine("  - id=\"${n.id}\" | type=${n.nodeType} | title=\"${n.title}\" | pos=($relX,$relY) | content=\"${n.content.take(60)}\" | params=${n.params}")
                }
            }
        }.trim()
    }

    fun updateLayout(screenWidth: Int, screenHeight: Int, toolbarHeight: Int) {
        panelW = (screenWidth / 4).coerceAtLeast(175)
        panelX = screenWidth - panelW
        panelY = toolbarHeight
        panelH = screenHeight - toolbarHeight

        val boxX = panelX + 6
        val boxY = panelY + panelH - 24
        val sendBtnW = 24
        val boxW = (panelW - 16 - sendBtnW).coerceAtLeast(60)

        if (!::inputBox.isInitialized) {
            inputBox = EditBox(font, boxX, boxY, boxW, 18, Component.translatable("cobblebrain.story_editor.ai.input_placeholder"))
            inputBox.setMaxLength(1000)
            inputBox.setHint(Component.translatable("cobblebrain.story_editor.ai.input_hint").withStyle(ChatFormatting.DARK_GRAY))
            inputBox.value = savedInputText
            inputBox.setResponder { savedInputText = it }
        } else {
            inputBox.x = boxX
            inputBox.y = boxY
            inputBox.width = boxW
        }
    }

    fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH
    }

    fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // 1. Main Panel Background (1/4 horizontal screen width)
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF410141F.toInt())
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFF00FFCC.toInt())

        // 2. Top Header Bar (height = 20)
        guiGraphics.fill(panelX + 1, panelY, panelX + panelW, panelY + 20, 0xFF1A2234.toInt())
        guiGraphics.fill(panelX + 1, panelY + 19, panelX + panelW, panelY + 20, 0xFF2D3B55.toInt())

        val headerTitle = Component.translatable("cobblebrain.story_editor.ai.title").string
        guiGraphics.drawString(font, headerTitle, panelX + 6, panelY + 6, 0xFF00FFCC.toInt(), true)

        // Close Button [✖]
        val closeX = panelX + panelW - 18
        val closeY = panelY + 4
        val isCloseHover = mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 12
        guiGraphics.fill(closeX, closeY, closeX + 14, closeY + 12, if (isCloseHover) 0xFFDC2626.toInt() else 0xFF334155.toInt())
        guiGraphics.drawString(font, "✖", closeX + 4, closeY + 2, 0xFFFFFFFF.toInt(), false)

        // Clear Chat Button [🗑]
        val clearX = closeX - 16
        val isClearHover = mouseX >= clearX && mouseX <= clearX + 14 && mouseY >= closeY && mouseY <= closeY + 12
        guiGraphics.fill(clearX, closeY, clearX + 14, closeY + 12, if (isClearHover) 0xFF475569.toInt() else 0xFF263147.toInt())
        guiGraphics.drawString(font, "🗑", clearX + 3, closeY + 2, 0xFFCBD5E1.toInt(), false)

        // Focus Selection Toggle Button [🎯] in Header
        val focusedCount = getEffectiveFocusedNodes().size
        val focusLabel = when {
            !focusOnSelectionEnabled -> "🎯" + Component.translatable("cobblebrain.story_editor.ai.focus.free").string
            focusedCount > 0 -> "🎯$focusedCount"
            getEffectiveFocusedScene() != null -> "🎯" + Component.translatable("cobblebrain.story_editor.ai.focus.scene").string
            else -> "🎯" + Component.translatable("cobblebrain.story_editor.ai.focus.auto").string
        }
        val focusBtnW = font.width(focusLabel) + 8
        val focusBtnX = clearX - focusBtnW - 3
        val isFocusHover = mouseX >= focusBtnX && mouseX <= focusBtnX + focusBtnW && mouseY >= closeY && mouseY <= closeY + 12
        val focusBtnBg = when {
            !focusOnSelectionEnabled -> 0xFF334155.toInt()
            focusedCount > 0 -> if (isFocusHover) 0xFF0D9488.toInt() else 0xFF0F766E.toInt()
            else -> if (isFocusHover) 0xFF0369A1.toInt() else 0xFF1E3A5F.toInt()
        }
        guiGraphics.fill(focusBtnX, closeY, focusBtnX + focusBtnW, closeY + 12, focusBtnBg)
        guiGraphics.drawString(font, focusLabel, focusBtnX + 4, closeY + 2, 0xFFFFFFFF.toInt(), false)

        // 3. Pipeline 6-Call Status Bar (y = panelY + 20 .. panelY + 52)
        val pipeY = panelY + 21
        guiGraphics.fill(panelX + 1, pipeY, panelX + panelW, pipeY + 31, 0xFF141B29.toInt())
        guiGraphics.fill(panelX + 1, pipeY + 30, panelX + panelW, pipeY + 31, 0xFF263147.toInt())

        // Render 6 call badges horizontally: [1.Ped] [5.JSON] [2.Bloc] [6.Perg] [3.Proj] [4.Rev]
        val stages = listOf(
            Triple(1, Component.translatable("cobblebrain.story_editor.ai.stage.1").string, 1 shl 1),
            Triple(5, Component.translatable("cobblebrain.story_editor.ai.stage.5").string, 1 shl 5),
            Triple(2, Component.translatable("cobblebrain.story_editor.ai.stage.2").string, 1 shl 2),
            Triple(6, Component.translatable("cobblebrain.story_editor.ai.stage.6").string, 1 shl 6),
            Triple(3, Component.translatable("cobblebrain.story_editor.ai.stage.3").string, 1 shl 3),
            Triple(4, Component.translatable("cobblebrain.story_editor.ai.stage.4").string, 1 shl 4)
        )
        val badgeGap = 2
        val availW = panelW - 10
        val badgeW = ((availW - badgeGap * (stages.size - 1)) / stages.size).coerceAtLeast(18)
        var bx = panelX + 5
        val by = pipeY + 3
        val bh = 12

        for ((stageNum, label, bit) in stages) {
            val isCurrent = (activeCallStage == stageNum) ||
                (stageNum == 5 && (activeCallStage == 51 || activeCallStage == 52)) ||
                (stageNum == 2 && (activeCallStage == 21 || activeCallStage == 22))
            val isDone = (completedCallsMask and bit) != 0
            val bgCol = when {
                isCurrent -> 0xFF0284C7.toInt()
                isDone -> 0xFF15803D.toInt()
                else -> 0xFF1E293B.toInt()
            }
            val borderCol = when {
                isCurrent -> 0xFF38BDF8.toInt()
                isDone -> 0xFF4ADE80.toInt()
                else -> 0xFF334155.toInt()
            }
            val txtCol = when {
                isCurrent -> 0xFFFFFFFF.toInt()
                isDone -> 0xFFDCFCE7.toInt()
                else -> 0xFF94A3B8.toInt()
            }

            guiGraphics.fill(bx, by, bx + badgeW, by + bh, bgCol)
            guiGraphics.fill(bx, by, bx + badgeW, by + 1, borderCol)
            val count = callCounts[stageNum] ?: 0
            val labelWithCount = if (count > 1) "$label×$count" else label
            val shortLabel = font.plainSubstrByWidth(labelWithCount, badgeW - 2)
            val tw = font.width(shortLabel)
            guiGraphics.drawString(font, shortLabel, bx + (badgeW - tw) / 2, by + 2, txtCol, false)
            bx += badgeW + badgeGap
        }

        // Status & Detailed Token Counter Line (Session + Current Request)
        val tokenStr = if (currentRequestTokens > 0) {
            "🪙~$totalEstimatedTokens (+$currentRequestTokens)"
        } else {
            "🪙~$totalEstimatedTokens"
        }
        val tokenW = font.width(tokenStr)
        guiGraphics.drawString(font, tokenStr, panelX + panelW - tokenW - 5, pipeY + 19, 0xFFFBBF24.toInt(), false)

        val statusMaxW = (panelW - tokenW - 14).coerceAtLeast(35)
        val statusTrimmed = font.plainSubstrByWidth(currentStatusText, statusMaxW)
        val statusCol = if (isRunningPipeline) 0xFF38BDF8.toInt() else 0xFF94A3B8.toInt()
        guiGraphics.drawString(font, statusTrimmed, panelX + 6, pipeY + 19, statusCol, false)

        // Calculate dynamic bottom overlays (Accept/Reject bar & 4-Option Question Overlay)
        val hasAcceptRejectBar = projectBackupSnapshot != null && pendingChangesCount > 0 && !isRunningPipeline
        val qState = activeQuestion
        val hasQuestionOverlay = qState != null

        val acceptBarH = if (hasAcceptRejectBar) 24 else 0
        val questionOverlayH = if (hasQuestionOverlay) 88 else 0
        val chatTop = panelY + 53
        val chatBottom = panelY + panelH - 30 - acceptBarH - questionOverlayH
        val chatH = (chatBottom - chatTop).coerceAtLeast(30)

        // 4. Scrollable Chat History Area
        guiGraphics.enableScissor(panelX + 2, chatTop, panelX + panelW - 2, chatBottom)

        var curY = chatTop + 6 - chatScroll.toInt()
        val msgMaxW = (panelW - 24).coerceAtLeast(60)

        if (chatHistory.isNotEmpty()) {
            for (entry in chatHistory) {
                val wrappedLines = font.split(Component.literal(entry.text), msgMaxW - 8)
                val isTrace = entry.role == ChatRole.CALL_TRACE
                val boxH = if (isTrace) {
                    wrappedLines.size * (font.lineHeight + 1) + 14
                } else {
                    wrappedLines.size * (font.lineHeight + 2) + 18
                }

                val cardX = if (entry.role == ChatRole.USER) panelX + 10 else panelX + 6
                val cardW = panelW - (if (entry.role == ChatRole.USER) 16 else 14)

                val bgColor = when (entry.role) {
                    ChatRole.USER -> 0xCC1E3A5F.toInt()
                    ChatRole.ASSISTANT -> 0xCC17253B.toInt()
                    ChatRole.CALL_TRACE -> 0x990F172A.toInt()
                    ChatRole.QUESTION -> 0xCC2E1065.toInt()
                    ChatRole.ERROR -> 0xCC3B181E.toInt()
                }

                if (curY + boxH >= chatTop - 20 && curY <= chatBottom + 20) {
                    guiGraphics.fill(cardX, curY, cardX + cardW, curY + boxH, bgColor)
                    guiGraphics.fill(cardX, curY, cardX + 2, curY + boxH, entry.accentColor)

                    val badgeText = font.plainSubstrByWidth(entry.badge, cardW - 10)
                    guiGraphics.drawString(font, badgeText, cardX + 6, curY + 4, entry.accentColor, false)

                    var textY = curY + 14
                    val textColor = when (entry.role) {
                        ChatRole.CALL_TRACE -> 0xFFCBD5E1.toInt()
                        ChatRole.ERROR -> 0xFFFCA5A5.toInt()
                        else -> 0xFFF1F5F9.toInt()
                    }
                    for (wLine in wrappedLines) {
                        guiGraphics.drawString(font, wLine, cardX + 6, textY, textColor, false)
                        textY += if (isTrace) (font.lineHeight + 1) else (font.lineHeight + 2)
                    }
                }

                curY += boxH + 5
            }
        }

        guiGraphics.disableScissor()

        // Update Scroll Bounds
        lastTotalContentH = (curY + chatScroll.toInt() - chatTop + 8).toFloat()
        val maxScroll = (lastTotalContentH - chatH).coerceAtLeast(0f)
        if (autoScrollToBottom) {
            chatScroll = maxScroll
            autoScrollToBottom = false
        } else {
            chatScroll = chatScroll.coerceIn(0f, maxScroll)
        }

        // Render Scrollbar if needed
        if (maxScroll > 0f) {
            val sbW = 3
            val sbX = panelX + panelW - sbW - 2
            val ratio = (chatH / lastTotalContentH).coerceIn(0.08f, 1f)
            val thumbH = (chatH * ratio).toInt().coerceAtLeast(16)
            val thumbY = chatTop + ((chatScroll / maxScroll) * (chatH - thumbH)).toInt()

            guiGraphics.fill(sbX, chatTop, sbX + sbW, chatBottom, 0x550F172A)
            guiGraphics.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, if (isDraggingScrollbar) 0xFF00FFCC.toInt() else 0xFF38BDF8.toInt())
        }

        var overlayTopY = chatBottom

        // 4B. Interactive 4-Option Question Overlay (when AI pauses to ask the player)
        if (hasQuestionOverlay && qState != null) {
            guiGraphics.fill(panelX + 1, overlayTopY, panelX + panelW, overlayTopY + questionOverlayH, 0xFF1E1B4B.toInt())
            guiGraphics.fill(panelX + 1, overlayTopY, panelX + panelW, overlayTopY + 1, 0xFFA855F7.toInt())

            val qHeader = if (qState.isTypingCustomOther) {
                Component.translatable("cobblebrain.story_editor.ai.question_typing").string
            } else {
                Component.translatable("cobblebrain.story_editor.ai.question_choose").string
            }
            guiGraphics.drawString(font, font.plainSubstrByWidth(qHeader, panelW - 12), panelX + 6, overlayTopY + 4, 0xFFE9D5FF.toInt(), false)

            var optY = overlayTopY + 16
            val optH = 15
            for (idx in 0 until 4) {
                val defaultOpt = if (idx == 3) Component.translatable("cobblebrain.story_editor.ai.question_other").string else "${idx + 1}. Option ${idx + 1}"
                val optLabel = qState.options.getOrElse(idx) { defaultOpt }
                val optX = panelX + 6
                val optW = panelW - 12
                val isOptHover = mouseX >= optX && mouseX <= optX + optW && mouseY >= optY && mouseY <= optY + optH
                val isSelectedOther = idx == 3 && qState.isTypingCustomOther
                val optBg = when {
                    isSelectedOther -> 0xFF7E22CE.toInt()
                    isOptHover -> 0xFF6D28D9.toInt()
                    else -> 0xFF312E81.toInt()
                }
                guiGraphics.fill(optX, optY, optX + optW, optY + optH, optBg)
                guiGraphics.fill(optX, optY, optX + 2, optY + optH, 0xFFC084FC.toInt())
                val trimmedOpt = font.plainSubstrByWidth(optLabel, optW - 8)
                guiGraphics.drawString(font, trimmedOpt, optX + 5, optY + 3, 0xFFFFFFFF.toInt(), false)
                optY += optH + 2
            }
            overlayTopY += questionOverlayH
        }

        // 4C. Antigravity-style Accept / Reject Changes Bar
        if (hasAcceptRejectBar) {
            guiGraphics.fill(panelX + 1, overlayTopY, panelX + panelW, overlayTopY + acceptBarH, 0xFF132A2E.toInt())
            guiGraphics.fill(panelX + 1, overlayTopY, panelX + panelW, overlayTopY + 1, 0xFF10B981.toInt())

            val infoTxt = font.plainSubstrByWidth(Component.translatable("cobblebrain.story_editor.ai.pending_changes", pendingChangesCount).string, (panelW - 108).coerceAtLeast(30))
            guiGraphics.drawString(font, infoTxt, panelX + 6, overlayTopY + 8, 0xFFA7F3D0.toInt(), false)

            val btnW = 46
            val btnH = 16
            val btnY = overlayTopY + 4
            val rejectX = panelX + panelW - btnW - 5
            val acceptX = rejectX - btnW - 4

            val isAcceptHover = mouseX >= acceptX && mouseX <= acceptX + btnW && mouseY >= btnY && mouseY <= btnY + btnH
            val isRejectHover = mouseX >= rejectX && mouseX <= rejectX + btnW && mouseY >= btnY && mouseY <= btnY + btnH

            guiGraphics.fill(acceptX, btnY, acceptX + btnW, btnY + btnH, if (isAcceptHover) 0xFF22C55E.toInt() else 0xFF15803D.toInt())
            guiGraphics.drawString(font, Component.translatable("cobblebrain.story_editor.ai.accept").string, acceptX + 4, btnY + 4, 0xFFFFFFFF.toInt(), false)

            guiGraphics.fill(rejectX, btnY, rejectX + btnW, btnY + btnH, if (isRejectHover) 0xFFEF4444.toInt() else 0xFFB91C1C.toInt())
            guiGraphics.drawString(font, Component.translatable("cobblebrain.story_editor.ai.reject").string, rejectX + 3, btnY + 4, 0xFFFFFFFF.toInt(), false)

            overlayTopY += acceptBarH
        }

        // 5. Bottom Input Area
        val inputAreaTop = panelY + panelH - 30
        guiGraphics.fill(panelX + 1, inputAreaTop, panelX + panelW, panelY + panelH, 0xFF161F30.toInt())
        guiGraphics.fill(panelX + 1, inputAreaTop, panelX + panelW, inputAreaTop + 1, 0xFF2D3B55.toInt())

        if (::inputBox.isInitialized) {
            inputBox.render(guiGraphics, mouseX, mouseY, partialTick)
        }

        // Send Button [➤]
        val sendX = panelX + panelW - 28
        val sendY = panelY + panelH - 24
        val sendW = 22
        val sendH = 18
        val isSendHover = mouseX >= sendX && mouseX <= sendX + sendW && mouseY >= sendY && mouseY <= sendY + sendH
        val canSendNow = !isRunningPipeline || (qState != null && qState.isTypingCustomOther)
        val sendBg = when {
            !canSendNow -> 0xFF334155.toInt()
            isSendHover -> 0xFF00E5FF.toInt()
            else -> 0xFF0284C7.toInt()
        }
        guiGraphics.fill(sendX, sendY, sendX + sendW, sendY + sendH, sendBg)
        val sendLabel = if (!canSendNow) "…" else "➤"
        guiGraphics.drawString(font, sendLabel, sendX + 7, sendY + 5, 0xFFFFFFFF.toInt(), false)
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!isMouseOver(mouseX, mouseY)) {
            if (::inputBox.isInitialized) inputBox.isFocused = false
            return false
        }

        if (button == 0) {
            // Close button [✖]
            val closeX = panelX + panelW - 18
            val closeY = panelY + 4
            if (mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 12) {
                if (::inputBox.isInitialized) inputBox.isFocused = false
                editor.toggleAiAssistant(false)
                return true
            }

            // Clear button [🗑]
            val clearX = closeX - 16
            if (mouseX >= clearX && mouseX <= clearX + 14 && mouseY >= closeY && mouseY <= closeY + 12) {
                if (!isRunningPipeline) {
                    chatHistory.clear()
                    recentCompletedInteractions.clear()
                    callCounts.clear()
                    completedCallsMask = 0
                    currentRequestTokens = 0
                    currentStatusText = Component.translatable("cobblebrain.story_editor.ai.status_cleared").string
                    chatScroll = 0f
                }
                return true
            }

            // Focus Selection Toggle Button [🎯]
            val focusedCount = getEffectiveFocusedNodes().size
            val focusLabel = when {
                !focusOnSelectionEnabled -> "🎯Livre"
                focusedCount > 0 -> "🎯$focusedCount"
                getEffectiveFocusedScene() != null -> "🎯Cena"
                else -> "🎯Auto"
            }
            val focusBtnW = font.width(focusLabel) + 8
            val focusBtnX = clearX - focusBtnW - 3
            if (mouseX >= focusBtnX && mouseX <= focusBtnX + focusBtnW && mouseY >= closeY && mouseY <= closeY + 12) {
                val liveSelectedNodes = editor.selectedWidgets.map { it.node.id }.toMutableList()
                editor.selectedWidget?.node?.id?.let { if (!liveSelectedNodes.contains(it)) liveSelectedNodes.add(it) }
                val newToPin = liveSelectedNodes.filter { !pinnedFocusNodeIds.contains(it) }

                if (!focusOnSelectionEnabled) {
                    focusOnSelectionEnabled = true
                    pinnedFocusNodeIds.clear()
                    pinnedFocusNodeIds.addAll(liveSelectedNodes)
                    currentStatusText = if (pinnedFocusNodeIds.isNotEmpty()) {
                        "🎯 Foco fixado em ${pinnedFocusNodeIds.size} bloco(s)"
                    } else {
                        "🎯 Modo de foco ativo (Clique direito -> Adicionar ao Foco IA)"
                    }
                } else if (newToPin.isNotEmpty()) {
                    pinnedFocusNodeIds.addAll(newToPin)
                    currentStatusText = "🎯 ${pinnedFocusNodeIds.size} bloco(s) em Foco da IA"
                } else if (pinnedFocusNodeIds.isNotEmpty() || pinnedFocusSceneIds.isNotEmpty()) {
                    pinnedFocusNodeIds.clear()
                    pinnedFocusSceneIds.clear()
                    currentStatusText = "🎯 Focos limpos (Trabalhando na cena ativa)"
                } else {
                    focusOnSelectionEnabled = false
                    pinnedFocusNodeIds.clear()
                    pinnedFocusSceneIds.clear()
                    currentStatusText = "🎯 Foco manual desativado (Cena inteira)"
                }
                return true
            }

            // Calculate dynamic bottom overlays
            val hasAcceptRejectBar = projectBackupSnapshot != null && pendingChangesCount > 0 && !isRunningPipeline
            val qState = activeQuestion
            val hasQuestionOverlay = qState != null
            val acceptBarH = if (hasAcceptRejectBar) 24 else 0
            val questionOverlayH = if (hasQuestionOverlay) 88 else 0
            val chatTop = panelY + 53
            val chatBottom = panelY + panelH - 30 - acceptBarH - questionOverlayH
            var overlayTopY = chatBottom

            // Check clicks on 4-Option Question Overlay
            if (hasQuestionOverlay && qState != null) {
                var optY = overlayTopY + 16
                val optH = 15
                val optX = panelX + 6
                val optW = panelW - 12
                for (idx in 0 until 4) {
                    if (mouseX >= optX && mouseX <= optX + optW && mouseY >= optY && mouseY <= optY + optH) {
                        if (idx == 3) {
                            // 4th Option = "Outro (Digitar...)"
                            qState.isTypingCustomOther = true
                            if (::inputBox.isInitialized) {
                                inputBox.value = ""
                                inputBox.setHint(Component.translatable("cobblebrain.story_editor.ai.input_question_hint").withStyle(ChatFormatting.DARK_PURPLE))
                                inputBox.isFocused = true
                            }
                        } else {
                            val chosenText = qState.options.getOrElse(idx) { "Opção ${idx + 1}" }
                            activeQuestion = null
                            if (::inputBox.isInitialized) {
                                inputBox.setHint(Component.translatable("cobblebrain.story_editor.ai.input_hint").withStyle(ChatFormatting.DARK_GRAY))
                            }
                            qState.responseFuture.complete(chosenText)
                        }
                        return true
                    }
                    optY += optH + 2
                }
                overlayTopY += questionOverlayH
            }

            // Check clicks on Accept / Reject Changes Bar
            if (hasAcceptRejectBar) {
                val btnW = 46
                val btnH = 16
                val btnY = overlayTopY + 4
                val rejectX = panelX + panelW - btnW - 5
                val acceptX = rejectX - btnW - 4

                if (mouseX >= acceptX && mouseX <= acceptX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                    acceptPendingChanges()
                    return true
                }
                if (mouseX >= rejectX && mouseX <= rejectX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                    rejectPendingChanges()
                    return true
                }
                overlayTopY += acceptBarH
            }

            // Send button [➤]
            val sendX = panelX + panelW - 28
            val sendY = panelY + panelH - 24
            if (mouseX >= sendX && mouseX <= sendX + 22 && mouseY >= sendY && mouseY <= sendY + 18) {
                submitUserMessage()
                return true
            }

            // Input Box
            if (::inputBox.isInitialized) {
                val clickedInput = inputBox.mouseClicked(mouseX, mouseY, button)
                inputBox.isFocused = clickedInput
                if (clickedInput) return true
            }

            // Scrollbar drag check
            val chatH = (chatBottom - chatTop).coerceAtLeast(30)
            val maxScroll = (lastTotalContentH - chatH).coerceAtLeast(0f)
            if (maxScroll > 0f && mouseX >= panelX + panelW - 8 && mouseY >= chatTop && mouseY <= chatBottom) {
                isDraggingScrollbar = true
                dragStartMouseY = mouseY
                dragStartScroll = chatScroll
                return true
            }
        }

        return true // Consume all clicks inside the 1/4 assistant panel so canvas doesn't click-through
    }

    private fun acceptPendingChanges() {
        val count = pendingChangesCount
        projectBackupSnapshot = null
        pendingChangesCount = 0
        editor.markDirty()
        appendEntry(
            ChatRole.CALL_TRACE,
            Component.translatable("cobblebrain.story_editor.ai.accepted_title").string,
            Component.translatable("cobblebrain.story_editor.ai.accepted_msg", count).string,
            0xFF22C55E.toInt()
        )
        currentStatusText = Component.translatable("cobblebrain.story_editor.ai.accepted_status").string
    }

    private fun rejectPendingChanges() {
        val backup = projectBackupSnapshot ?: return
        val restored = StorySerializer.deepCopyProject(backup)
        editor.project = restored
        editor.rebuildNodeWidgets()
        editor.markDirty()
        projectBackupSnapshot = null
        pendingChangesCount = 0
        appendEntry(
            ChatRole.CALL_TRACE,
            Component.translatable("cobblebrain.story_editor.ai.rejected_title").string,
            Component.translatable("cobblebrain.story_editor.ai.rejected_msg").string,
            0xFFEF4444.toInt()
        )
        currentStatusText = Component.translatable("cobblebrain.story_editor.ai.rejected_status").string
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (isDraggingScrollbar) {
            val chatTop = panelY + 53
            val chatBottom = panelY + panelH - 30
            val chatH = (chatBottom - chatTop).coerceAtLeast(30)
            val maxScroll = (lastTotalContentH - chatH).coerceAtLeast(0f)
            if (maxScroll > 0f) {
                val ratio = (chatH / lastTotalContentH).coerceIn(0.08f, 1f)
                val thumbH = (chatH * ratio).toInt().coerceAtLeast(16)
                val track = (chatH - thumbH).coerceAtLeast(1)
                val deltaY = (mouseY - dragStartMouseY).toFloat()
                chatScroll = (dragStartScroll + (deltaY / track) * maxScroll).coerceIn(0f, maxScroll)
            }
            return true
        }
        return isMouseOver(mouseX, mouseY)
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false
            return true
        }
        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false
        val chatTop = panelY + 53
        val chatBottom = panelY + panelH - 30
        val chatH = (chatBottom - chatTop).coerceAtLeast(30)
        val maxScroll = (lastTotalContentH - chatH).coerceAtLeast(0f)
        chatScroll = (chatScroll - scrollY.toFloat() * 18f).coerceIn(0f, maxScroll)
        return true
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (::inputBox.isInitialized && inputBox.isFocused) {
            return inputBox.charTyped(codePoint, modifiers)
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (::inputBox.isInitialized && inputBox.isFocused) {
            if (keyCode == 257 || keyCode == 335) { // Enter or Numpad Enter
                submitUserMessage()
                return true
            }
            if (keyCode == 256) { // ESC unfocuses input box
                inputBox.isFocused = false
                return true
            }
            if (inputBox.keyPressed(keyCode, scanCode, modifiers)) return true
            return true // Prevent editor hotkeys (like Space, Delete, Ctrl+C) from firing on canvas while typing in chat
        }
        return false
    }

    private fun appendEntry(role: ChatRole, badge: String, text: String, accentColor: Int) {
        val cleaned = text.trim()
        chatHistory.add(ChatEntry(role, badge, cleaned, accentColor))
        autoScrollToBottom = true
        AIHandler.logStoryEditor("[CHAT-UI][$role] $badge:\n$cleaned\n------------------------------------------------------------")
    }

    private fun estimateTokens(vararg texts: String): Int {
        val chars = texts.sumOf { it.length }
        return (chars / 4).coerceAtLeast(1)
    }

    private fun stageLabelForLog(stage: Int): String = when (stage) {
        1 -> "CALL_1 (Roteador / Intenção)"
        51 -> "CALL_5A (Funil JSON [MARKED])"
        52 -> "CALL_5B (Análise dos [MARKED] - 2 Arquivos)"
        21 -> "CALL_2A (Seleção de Nós de Interesse)"
        22 -> "CALL_2B (Guia Completo dos Blocos)"
        6 -> "CALL_6 (Pergunta Interativa de 4 Opções)"
        3 -> "CALL_3 (Execução de Blocos e Conexões no Projeto)"
        4 -> "CALL_4 (Revisão Final e [ENDTOKEN])"
        else -> "STAGE_$stage"
    }

    private fun callLLM(prompt: String, systemPrompt: String): String {
        val stageDesc = stageLabelForLog(activeCallStage)
        val inTok = estimateTokens(prompt, systemPrompt)
        totalEstimatedTokens += inTok
        currentRequestTokens += inTok

        AIHandler.logStoryEditor(
            buildString {
                appendLine("================================================================================")
                appendLine(">>> INICIANDO CHAMADA IA: $stageDesc")
                appendLine(">>> Tokens Estimados de Entrada: ~$inTok (Acumulado Sessão: ~$totalEstimatedTokens)")
                appendLine("--- [SYSTEM PROMPT] ---")
                appendLine(systemPrompt)
                appendLine("--- [USER PROMPT / PRANCHETA COMPARTILHADA] ---")
                appendLine(prompt)
                appendLine("================================================================================")
            }
        )

        val startMs = System.currentTimeMillis()
        val response = AIClientHandler.sendStandalonePrompt(prompt, systemPrompt).join()
        val elapsedMs = System.currentTimeMillis() - startMs

        val outTok = estimateTokens(response)
        totalEstimatedTokens += outTok
        currentRequestTokens += outTok

        AIHandler.logStoryEditor(
            buildString {
                appendLine("<<< RESPOSTA RECEBIDA: $stageDesc (${elapsedMs}ms | +~$outTok tokens)")
                appendLine(response)
                appendLine("================================================================================")
            }
        )

        if (response.startsWith("ERROR:") || response.startsWith("!Error")) {
            throw RuntimeException(response.removePrefix("ERROR:").removePrefix("!Error!").removePrefix("!Error").trim())
        }
        return response
    }

    private fun submitUserMessage() {
        if (!::inputBox.isInitialized) return
        val userText = inputBox.value.trim()
        if (userText.isBlank()) return

        // If the AI is currently waiting for the 4th option ("Outro - Digitar...") in a Question Call:
        val qState = activeQuestion
        if (qState != null && qState.isTypingCustomOther) {
            inputBox.value = ""
            savedInputText = ""
            inputBox.setHint(Component.translatable("cobblebrain.story_editor.ai.input_hint").withStyle(ChatFormatting.DARK_GRAY))
            activeQuestion = null
            qState.responseFuture.complete("Outro: $userText")
            return
        }

        if (isRunningPipeline) return

        inputBox.value = ""
        savedInputText = ""
        appendEntry(ChatRole.USER, Component.translatable("cobblebrain.story_editor.ai.user").string, userText, 0xFF38BDF8.toInt())

        runMultiCallPipeline(userText)
    }

    // =========================================================================
    // MULTI-CALL AGENTIC PIPELINE ("PRANCHETA COMPARTILHADA" ENTRE OS ROBÔS)
    // =========================================================================

    private data class CallStepResult(
        val outputSummary: String,
        val requestedNextCall: Int, // 1, 2, 3, 4, 5, or 6 (Question)
        val nextInstruction: String
    )

    private fun incrementCallCount(stageNum: Int): Int {
        val next = (callCounts[stageNum] ?: 0) + 1
        callCounts[stageNum] = next
        completedCallsMask = completedCallsMask or (1 shl stageNum)
        return next
    }

    private fun parseNextCallNumber(rawNext: String?, defaultNext: Int): Int {
        if (rawNext.isNullOrBlank()) return defaultNext
        val up = rawNext.uppercase().trim()
        return when {
            up.contains("6") || up.contains("QUESTION") || up.contains("PERGUNTA") || up.contains("ASK") -> 6
            up.contains("1") -> 1
            up.contains("5") -> 5
            up.contains("2") -> 2
            up.contains("3") -> 3
            up.contains("4") || up.contains("REVIEW") || up.contains("REVIS") -> 4
            else -> defaultNext
        }
    }

    private fun runMultiCallPipeline(userRequest: String) {
        isRunningPipeline = true
        completedCallsMask = 0
        callCounts.clear()
        currentRequestTokens = 0
        activeCallStage = 1
        currentStatusText = "1. Processando pedido..."

        // Capture the player's focused nodes/scenes on the canvas at the start of the request
        val initialFocusSummary = buildPlayerFocusSummary()
        val initialFocusedNodes = getEffectiveFocusedNodes()
        val initialFocusedScene = getEffectiveFocusedScene()

        Thread {
            try {
                // Create the Shared Blackboard ("Prancheta de Desenho") passed from robot to robot
                val blackboard = SharedBlackboard(userRequest, initialFocusSummary)
                initialFocusedScene?.let { blackboard.markedSceneIds.add(it.id) }
                initialFocusedNodes.forEach { blackboard.markedNodeIds.add(it.id) }

                var currentCall = 1
                var currentInstruction = userRequest
                var needCall2Fallback = false
                var needCall3Fallback = false

                var totalSteps = 0
                val maxTotalSteps = 14 // Safety guard against infinite loops
                var terminatedWithEndToken = false

                while (!terminatedWithEndToken && totalSteps < maxTotalSteps) {
                    totalSteps++

                    when (currentCall) {
                        // -------------------------------------------------------------
                        // CALL 1: PROCESS REQUEST & STAMP INTENT ON BLACKBOARD
                        // -------------------------------------------------------------
                        1 -> {
                            val passNum = incrementCallCount(1)
                            val call1Plan = executeCall1ProcessRequest(blackboard, currentInstruction, passNum)
                            blackboard.intentSummary = call1Plan.get("intentSummary")?.asString ?: blackboard.intentSummary
                            val needCall5 = call1Plan.get("needsJsonInspection")?.asBoolean ?: false
                            val jsonQuery = call1Plan.get("jsonInspectionQuery")?.asString ?: currentInstruction
                            val needQuestion = call1Plan.get("needsUserQuestion")?.asBoolean ?: false
                            needCall2Fallback = call1Plan.get("needsBlockSelection")?.asBoolean ?: false
                            needCall3Fallback = call1Plan.get("needsProjectExecution")?.asBoolean ?: false
                            val directDraft = call1Plan.get("directReply")?.asString ?: ""

                            val defaultFirstAfter1 = when {
                                needQuestion -> 6
                                needCall5 -> 5
                                needCall2Fallback -> 2
                                needCall3Fallback -> 3
                                else -> 4
                            }
                            val nextCallFrom1 = parseNextCallNumber(call1Plan.get("nextCall")?.asString, defaultFirstAfter1)
                            val nextInstFrom1 = call1Plan.get("nextCallInstruction")?.asString?.ifBlank { null }
                                ?: if (nextCallFrom1 == 5) jsonQuery else userRequest

                            if (directDraft.isNotBlank() && !needCall5 && !needCall2Fallback && !needCall3Fallback) {
                                blackboard.executionNotes.add("Rascunho de resposta direta (Robô 1): $directDraft")
                            }

                            appendEntry(
                                ChatRole.CALL_TRACE,
                                "🧭 [Chamada 1${if (passNum > 1) " #$passNum" else ""}] Roteador (Prancheta)",
                                "${blackboard.intentSummary}\nPassando a prancheta para: Chamada $nextCallFrom1 ($nextInstFrom1)",
                                0xFF38BDF8.toInt()
                            )

                            currentCall = nextCallFrom1
                            currentInstruction = nextInstFrom1
                        }

                        // -------------------------------------------------------------
                        // CALL 5: JSON FUNNEL WITH [MARKED] + SEPARATED NODES & CONNECTIONS
                        // -------------------------------------------------------------
                        5 -> {
                            val defaultAfter5 = when {
                                needCall2Fallback && blackboard.blockGuideNotes.isEmpty() -> 2
                                needCall3Fallback -> 3
                                else -> 4
                            }
                            val targetSc = getEffectiveFocusedScene() ?: editor.project.getActiveScene()
                            val isSceneFreshAndEmpty = targetSc != null &&
                                targetSc.nodes.size <= 1 &&
                                targetSc.connections.isEmpty() &&
                                getEffectiveFocusedNodes().isEmpty() &&
                                (needCall2Fallback || needCall3Fallback)

                            if (isSceneFreshAndEmpty && targetSc != null) {
                                // Skip redundant Call 5a + 5b when the active scene only has the initial BEGIN_SCENE node
                                completedCallsMask = completedCallsMask or (1 shl 5)
                                blackboard.markedSceneIds.add(targetSc.id)
                                targetSc.nodes.forEach { blackboard.markedNodeIds.add(it.id) }
                                val startNode = targetSc.nodes.firstOrNull()
                                val relX = ((startNode?.x ?: targetSc.x) - targetSc.x).toInt()
                                val relY = ((startNode?.y ?: targetSc.y) - targetSc.y).toInt()
                                val fastNote = "Cena ativa '${targetSc.title}' (id=${targetSc.id}) está no estado inicial (apenas BEGIN_SCENE id=\"${startNode?.id ?: "BEGIN_SCENE"}\" em relPos=($relX,$relY) e 0 conexões). Funil 5a/5b otimizado automaticamente."
                                blackboard.jsonAnalysisNotes.add("[Análise Rápida Cena Inicial]:\n$fastNote")
                                appendEntry(
                                    ChatRole.CALL_TRACE,
                                    "⚡ [Chamada 5] Cena Inicial Detectada → Próx: Chamada $defaultAfter5",
                                    fastNote,
                                    0xFFA855F7.toInt()
                                )
                                currentCall = defaultAfter5
                                currentInstruction = userRequest
                            } else {
                                val passNum = incrementCallCount(5)
                                val step5 = executeCall5ProjectJsonInspection(
                                    blackboard = blackboard,
                                    query = currentInstruction,
                                    passNum = passNum,
                                    defaultNextCall = defaultAfter5
                                )
                                blackboard.jsonAnalysisNotes.add("[Análise dos [MARKED] #$passNum]:\n${step5.outputSummary}")
                                currentCall = step5.requestedNextCall
                                currentInstruction = step5.nextInstruction.ifBlank { userRequest }
                            }
                        }

                        // -------------------------------------------------------------
                        // CALL 2 (Sub-call 2a + Sub-call 2b): BLOCK CATALOG & GUIDE
                        // -------------------------------------------------------------
                        2 -> {
                            val passNum = incrementCallCount(2)
                            val defaultAfter2 = if (needCall3Fallback) 3 else 4
                            val step2 = executeCall2SelectBlocks(
                                blackboard = blackboard,
                                currentFocusInstruction = currentInstruction,
                                passNum = passNum,
                                defaultNextCall = defaultAfter2
                            )
                            blackboard.blockGuideNotes.add("[Guia de Blocos #$passNum]:\n${step2.outputSummary}")
                            currentCall = step2.requestedNextCall
                            currentInstruction = step2.nextInstruction.ifBlank { userRequest }
                        }

                        // -------------------------------------------------------------
                        // CALL 6: INTERACTIVE 4-OPTION QUESTION TO THE PLAYER
                        // (3 AI options + 4th "Outro" written by player; stamps Q&A on Blackboard)
                        // -------------------------------------------------------------
                        6 -> {
                            val passNum = incrementCallCount(6)
                            val defaultAfter6 = when {
                                needCall2Fallback && blackboard.blockGuideNotes.isEmpty() -> 2
                                needCall3Fallback -> 3
                                else -> 4
                            }
                            val step6 = executeCall6AskQuestion(
                                blackboard = blackboard,
                                questionContext = currentInstruction,
                                passNum = passNum,
                                defaultNextCall = defaultAfter6
                            )
                            blackboard.qaPairs.add(step6.outputSummary)
                            currentCall = step6.requestedNextCall
                            currentInstruction = step6.nextInstruction.ifBlank { userRequest }
                        }

                        // -------------------------------------------------------------
                        // CALL 3: WORK ON PROJECT (POSITIONS NODES & CONNECTS AUTOMATICALLY)
                        // Takes a backup snapshot before modifying so player can Accept/Reject!
                        // -------------------------------------------------------------
                        3 -> {
                            val passNum = incrementCallCount(3)
                            val step3 = executeCall3WorkOnProject(
                                blackboard = blackboard,
                                taskDescription = currentInstruction,
                                passNum = passNum
                            )
                            blackboard.executionNotes.add("[Desenho no Projeto #$passNum]:\n${step3.outputSummary}")
                            currentCall = step3.requestedNextCall
                            currentInstruction = step3.nextInstruction.ifBlank { "Revisar todas as alterações aplicadas na prancheta." }
                        }

                        // -------------------------------------------------------------
                        // CALL 4: FINAL REVIEW & [ENDTOKEN] GATEKEEPER
                        // -------------------------------------------------------------
                        4 -> {
                            val passNum = incrementCallCount(4)
                            val isLastAllowedStep = totalSteps >= maxTotalSteps
                            val reviewObj = executeCall4ReviewWork(
                                blackboard = blackboard,
                                reviewRound = passNum,
                                forceFinalize = isLastAllowedStep
                            )

                            val rawStatus = reviewObj.get("status")?.asString?.uppercase() ?: "APPROVED"
                            val rawNextCall = reviewObj.get("nextCallNeeded")?.asString?.uppercase() ?: "NONE"
                            val followUpDesc = reviewObj.get("followUpDescription")?.asString?.trim() ?: ""
                            val rawEndToken = reviewObj.get("terminationCode")?.asString ?: ""
                            val rawFinalMsg = reviewObj.get("finalUserMessage")?.asString ?: "Revisão concluída! $END_TOKEN"

                            val hasEndToken = rawEndToken.contains(END_TOKEN, ignoreCase = true) ||
                                rawFinalMsg.contains(END_TOKEN, ignoreCase = true) ||
                                rawNextCall.contains(END_TOKEN, ignoreCase = true) ||
                                rawStatus.contains(END_TOKEN, ignoreCase = true)

                            val wantsMoreCalls = !isLastAllowedStep &&
                                !hasEndToken &&
                                rawStatus != "APPROVED" &&
                                rawNextCall != "NONE" &&
                                followUpDesc.isNotBlank()

                            if (!wantsMoreCalls) {
                                val cleanUserMessage = rawFinalMsg
                                    .replace(END_TOKEN, "", ignoreCase = true)
                                    .trim()
                                    .ifBlank { "Tudo revisado e concluído com sucesso!" }

                                // Save the last 2 original user requests and their final AI responses across requests
                                recentCompletedInteractions.add(
                                    CompletedInteractionMemory(
                                        originalUserQuestion = userRequest,
                                        finalAiResponse = cleanUserMessage
                                    )
                                )
                                while (recentCompletedInteractions.size > 2) {
                                    recentCompletedInteractions.removeAt(0)
                                }

                                appendEntry(
                                    ChatRole.CALL_TRACE,
                                    "🏁 [Chamada 4 #$passNum] Revisão Finalizada $END_TOKEN",
                                    "Prancheta aprovada. Código $END_TOKEN recebido — encerrando requisição.",
                                    0xFF22C55E.toInt()
                                )
                                appendEntry(
                                    ChatRole.ASSISTANT,
                                    Component.translatable("cobblebrain.story_editor.ai.assistant").string,
                                    cleanUserMessage,
                                    0xFF00FFCC.toInt()
                                )
                                terminatedWithEndToken = true
                            } else {
                                val targetCall = parseNextCallNumber(rawNextCall, 3)
                                if (rawNextCall.contains("3")) needCall3Fallback = true
                                appendEntry(
                                    ChatRole.CALL_TRACE,
                                    "🔄 [Chamada 4 #$passNum] Revisão passou a prancheta p/ Chamada $targetCall",
                                    "Instrução: $followUpDesc",
                                    0xFFF59E0B.toInt()
                                )
                                currentCall = targetCall
                                currentInstruction = followUpDesc
                            }
                        }

                        else -> {
                            currentCall = 4
                        }
                    }
                }

                currentStatusText = if (pendingChangesCount > 0) {
                    Component.translatable("cobblebrain.story_editor.ai.status_review_needed").string
                } else {
                    Component.translatable("cobblebrain.story_editor.ai.status_done", END_TOKEN).string
                }
            } catch (e: Exception) {
                e.printStackTrace()
                AIHandler.logStoryEditor("[EXCEPTION IN PIPELINE]: ${e.stackTraceToString()}")
                activeQuestion = null
                appendEntry(
                    ChatRole.ERROR,
                    "❌ Erro no Assistente IA",
                    e.message ?: "Falha inesperada durante a execução das chamadas.",
                    0xFFEF4444.toInt()
                )
                currentStatusText = Component.translatable("cobblebrain.story_editor.ai.status_error").string
            } finally {
                activeCallStage = 0
                isRunningPipeline = false
            }
        }.apply {
            name = "CobbleBrain-StoryEditor-AIAssistant"
            isDaemon = true
            start()
        }
    }

    // =========================================================================
    // CALL 1: ROUTER & INTENT PROCESSOR
    // =========================================================================
    private fun executeCall1ProcessRequest(
        blackboard: SharedBlackboard,
        currentFocus: String,
        passNum: Int
    ): JsonObject {
        activeCallStage = 1
        currentStatusText = "1. Analisando pedido${if (passNum > 1) " (#$passNum)" else ""}..."

        val proj = editor.project
        val activeScene = proj.getActiveScene()

        val projectOverview = buildString {
            appendLine("Project ID: ${proj.id} | Name: ${proj.name}")
            appendLine("Total Scenes: ${proj.scenes.size} | Active Scene: ${activeScene?.title ?: "None"} (ID: ${activeScene?.id ?: "none"}, Nodes: ${activeScene?.nodes?.size ?: 0}, Connections: ${activeScene?.connections?.size ?: 0})")
            appendLine("Variables (${proj.variables.size}): ${proj.variables.joinToString { "${it.id}(${it.type})" }}")
        }

        val systemPrompt = """
            You are Robot 1 (Call 1: Request Router & Planner) of the CobbleBrain Story Editor AI Assistant.
            Our architecture works like robots passing a shared drawing clipboard (Blackboard) from one to the next because each API call is stateless.
            Your job is to stamp the overall intent onto the Blackboard and choose which robot receives the clipboard first:
            - Call 6 ("CALL_6_QUESTION"): Choose this FIRST if the player's request is ambiguous or has creative choices where asking a 4-option multiple-choice question will help! (Or if the user explicitly asked you to ask them a question).
            - Call 5 ("CALL_5"): Project JSON Funnel ($MARKED_TAG marker + separated nodes/connections inspection). Set "needsJsonInspection": true ONLY if the active scene already has > 1 node or existing connections that need to be inspected. If the active scene only has 1 node (BEGIN_SCENE) and 0 connections and the player is creating a scene from scratch, set "needsJsonInspection": false and go directly to "CALL_2" (or "CALL_6_QUESTION")!
            - Call 2 ("CALL_2"): Block Catalog & Guide (2a choose nodes of interest by name, 2b read full guide & choices). Set "needsBlockSelection": true whenever we will create or edit blocks so we know their exact arguments and choices before building!
            - Call 3 ("CALL_3"): Project Builder (positions blocks & connects them logically). Set "needsProjectExecution": true if we will modify the project.
            - Call 4 ("CALL_4"): Final Reviewer & $END_TOKEN Gatekeeper.
            
            Respond ONLY with a valid JSON object in Portuguese:
            {
              "intentSummary": "Resumo curto de 1 frase do que será feito",
              "needsUserQuestion": false,
              "needsJsonInspection": true,
              "jsonInspectionQuery": "O que buscar e marcar com $MARKED_TAG no JSON do projeto",
              "needsBlockSelection": true,
              "needsProjectExecution": true,
              "nextCall": "CALL_5",
              "nextCallInstruction": "Instrução clara anotada na prancheta para o próximo robô",
              "directReply": "Se for apenas uma dúvida simples que vai direto para a Call 4, escreva a resposta base aqui"
            }
        """.trimIndent()

        val userPrompt = """
            [PROJECT OVERVIEW]
            $projectOverview
            
            ${blackboard.renderPaperForNextRobot(currentFocus)}
        """.trimIndent()

        val raw = callLLM(userPrompt, systemPrompt)
        return parseJsonObjectSafe(raw) ?: JsonObject().apply {
            addProperty("intentSummary", "Atendendo ao pedido no Story Editor.")
            addProperty("needsUserQuestion", false)
            addProperty("needsJsonInspection", (activeScene?.nodes?.size ?: 0) > 1)
            addProperty("jsonInspectionQuery", currentFocus)
            addProperty("needsBlockSelection", true)
            addProperty("needsProjectExecution", true)
            addProperty("nextCall", if ((activeScene?.nodes?.size ?: 0) > 1) "CALL_5" else "CALL_2")
            addProperty("nextCallInstruction", currentFocus)
            addProperty("directReply", raw)
        }
    }

    // =========================================================================
    // CALL 6: INTERACTIVE 4-OPTION QUESTION (WITH 4TH OPTION = "OUTRO")
    // =========================================================================
    private fun executeCall6AskQuestion(
        blackboard: SharedBlackboard,
        questionContext: String,
        passNum: Int,
        defaultNextCall: Int
    ): CallStepResult {
        activeCallStage = 6
        currentStatusText = "6. Criando pergunta p/ você (#$passNum)..."

        val sys6 = """
            You are Robot 6 (Call 6: Interactive Question Formulator) of the CobbleBrain Story Editor AI Assistant.
            Formulate ONE concise, helpful question for the player with 3 concrete options ("option1", "option2", "option3").
            The UI will automatically add a 4th option: "4. Outro (Digitar resposta...)" so the player can write a custom answer if they prefer.
            Also specify which call should receive the clipboard after the player answers ("nextCallAfterAnswer": "CALL_2", "CALL_3", "CALL_5", or "CALL_4").
            
            Respond ONLY with valid JSON in Portuguese:
            {
              "question": "Qual abordagem você prefere para esta parte da história?",
              "option1": "Opção A clara e curta",
              "option2": "Opção B clara e curta",
              "option3": "Opção C clara e curta",
              "nextCallAfterAnswer": "CALL_$defaultNextCall",
              "nextCallInstruction": "Instrução para o próximo robô usar a resposta do jogador"
            }
        """.trimIndent()

        val prompt6 = blackboard.renderPaperForNextRobot(questionContext)
        val raw6 = callLLM(prompt6, sys6)
        val obj6 = parseJsonObjectSafe(raw6)

        val questionText = obj6?.get("question")?.asString?.ifBlank { null }
            ?: "Como você prefere configurar este fluxo?"
        val opt1 = "1. " + (obj6?.get("option1")?.asString?.removePrefix("1.")?.trim() ?: "Simples e direto")
        val opt2 = "2. " + (obj6?.get("option2")?.asString?.removePrefix("2.")?.trim() ?: "Com diálogo e missão")
        val opt3 = "3. " + (obj6?.get("option3")?.asString?.removePrefix("3.")?.trim() ?: "Deixar a IA decidir tudo")
        val opt4 = "4. Outro (Digitar...)"

        val nextCall = parseNextCallNumber(obj6?.get("nextCallAfterAnswer")?.asString, defaultNextCall)
        val nextInst = obj6?.get("nextCallInstruction")?.asString ?: blackboard.userRequest

        appendEntry(
            ChatRole.QUESTION,
            "❓ [Chamada 6 #$passNum] Pergunta da IA",
            "$questionText\n• $opt1\n• $opt2\n• $opt3\n• $opt4",
            0xFFC084FC.toInt()
        )

        currentStatusText = "Aguardando sua resposta nas opções abaixo..."
        val future = CompletableFuture<String>()
        activeQuestion = InteractiveQuestionState(
            questionText = questionText,
            options = listOf(opt1, opt2, opt3, opt4),
            responseFuture = future
        )

        // Wait until the player clicks one of the 4 buttons (or types custom text for Option 4)
        val playerAnswer = future.join()

        appendEntry(
            ChatRole.USER,
            "👤 Sua Resposta",
            playerAnswer,
            0xFF38BDF8.toInt()
        )

        val qaStamped = "Pergunta: \"$questionText\" ➔ Resposta escolhida pelo jogador: \"$playerAnswer\""
        return CallStepResult(qaStamped, nextCall, "$nextInst (Resposta do jogador: $playerAnswer)")
    }

    // =========================================================================
    // CALL 5: JSON FUNNEL WITH [MARKED] + SEPARATED NODES & CONNECTIONS FILES
    // =========================================================================
    private fun executeCall5ProjectJsonInspection(
        blackboard: SharedBlackboard,
        query: String,
        passNum: Int,
        defaultNextCall: Int
    ): CallStepResult {
        val proj = editor.project

        // --- PHASE 5A: Funnel Scan Chunk by Chunk -> Mark interesting parts with [MARKED] ---
        activeCallStage = 51
        val allScenes = proj.scenes
        val activeScene = proj.getActiveScene()

        // If the player already manually selected nodes on the canvas, pre-mark them with [MARKED]!
        val focusedNodes = getEffectiveFocusedNodes()
        focusedNodes.forEach { blackboard.markedNodeIds.add(it.id) }
        val focusedScene = getEffectiveFocusedScene()
        focusedScene?.let { blackboard.markedSceneIds.add(it.id) }

        // Scope Funnel 5a to the focused/active scene by default so it doesn't waste chunks scanning unrelated scenes
        val targetSceneForFunnel = focusedScene ?: activeScene
        val candidateNodes = when {
            pinnedFocusSceneIds.size > 1 -> allScenes.filter { pinnedFocusSceneIds.contains(it.id) }.flatMap { it.nodes }
            targetSceneForFunnel != null && focusOnSelectionEnabled -> targetSceneForFunnel.nodes
            else -> allScenes.flatMap { it.nodes }
        }

        val chunkSize = 18
        val chunks = if (candidateNodes.isEmpty()) listOf(emptyList()) else candidateNodes.chunked(chunkSize)
        var inspectSectionMode = "BOTH_SEPARATED" // "NODES_ONLY", "CONNECTIONS_ONLY", or "BOTH_SEPARATED"
        val reasonsList = mutableListOf<String>()

        for ((chunkIdx, nodeChunk) in chunks.take(4).withIndex()) {
            currentStatusText = "5a. Funil JSON $MARKED_TAG (${chunkIdx + 1}/${chunks.size.coerceAtMost(4)})..."
            val chunkIndexText = buildChunkIndexFor5aFunnel(proj, nodeChunk, chunkIdx + 1, chunks.size.coerceAtMost(4), blackboard)

            val sys5a = """
                You are Robot 5a (Call 5a: JSON Funnel & $MARKED_TAG Marker) of the CobbleBrain Story Editor AI Assistant.
                Because a StoryProject JSON can be huge, you scan the project part by part and mark ONLY the relevant scenes and nodes with "$MARKED_TAG".
                Once all parts are scanned, Robot 5b will ONLY receive the items you marked with "$MARKED_TAG", separated into two clean views:
                1. The Blocks file (`<scene>_nodes.json` - node properties only)
                2. The Connections file (`<scene>_connections.json` - logical connections only)
                
                Respond ONLY with valid JSON:
                {
                  "markedSceneIds": ["$MARKED_TAG scene_id_1"],
                  "markedNodeIds": ["$MARKED_TAG node_id_1", "$MARKED_TAG node_id_2"],
                  "inspectFileMode": "BOTH_SEPARATED",
                  "scanReason": "Motivo curto em português para marcar estes blocos com $MARKED_TAG"
                }
                Note: "inspectFileMode" can be "BOTH_SEPARATED", "NODES_ONLY", or "CONNECTIONS_ONLY".
            """.trimIndent()

            val prompt5a = """
                ${blackboard.renderPaperForNextRobot(query)}
                
                [JSON FUNNEL PART ${chunkIdx + 1}]:
                $chunkIndexText
            """.trimIndent()

            val raw5a = callLLM(prompt5a, sys5a)
            val selObj = parseJsonObjectSafe(raw5a)

            selObj?.getAsJsonArray("markedSceneIds")?.forEach { el ->
                if (el.isJsonPrimitive) {
                    val cleaned = el.asString.replace(MARKED_TAG, "", ignoreCase = true).trim()
                    if (cleaned.isNotBlank()) blackboard.markedSceneIds.add(cleaned)
                }
            }
            selObj?.getAsJsonArray("markedNodeIds")?.forEach { el ->
                if (el.isJsonPrimitive) {
                    val cleaned = el.asString.replace(MARKED_TAG, "", ignoreCase = true).trim()
                    if (cleaned.isNotBlank()) blackboard.markedNodeIds.add(cleaned)
                }
            }
            selObj?.get("inspectFileMode")?.asString?.let { inspectSectionMode = it.uppercase() }
            selObj?.get("scanReason")?.asString?.takeIf { it.isNotBlank() }?.let { reasonsList.add(it) }
        }

        // Ensure at least the active scene and its tail/start nodes are marked if nothing was marked
        if (blackboard.markedSceneIds.isEmpty() && activeScene != null) {
            blackboard.markedSceneIds.add(activeScene.id)
        }
        if (blackboard.markedNodeIds.isEmpty() && activeScene != null) {
            activeScene.nodes.takeLast(8).forEach { blackboard.markedNodeIds.add(it.id) }
        }

        appendEntry(
            ChatRole.CALL_TRACE,
            "🌪️ [Chamada 5a #$passNum] Funil $MARKED_TAG (${blackboard.markedNodeIds.size} blocos marcados)",
            reasonsList.joinToString(" | ").ifBlank { "Blocos de interesse marcados com $MARKED_TAG para análise focada." },
            0xFFA855F7.toInt()
        )

        // --- PHASE 5B: Analyze ONLY the [MARKED] items, split into 2 separate virtual files (Nodes vs Connections) ---
        activeCallStage = 52
        currentStatusText = "5b. Analisando apenas os $MARKED_TAG..."

        val separatedMarkedFilesText = extractMarkedNodesAndConnectionsSeparated(proj, blackboard, inspectSectionMode)

        val sys5b = """
            You are Robot 5b (Call 5b: $MARKED_TAG Analyzer) of the CobbleBrain Story Editor AI Assistant.
            The JSON Funnel (5a) finished scanning the project and filtered it down ONLY to the blocks marked with $MARKED_TAG.
            To make your work easier, the scene data is separated into 2 distinct files:
            - FILE 1 (`<scene>_nodes.json`): Contains ONLY the $MARKED_TAG blocks and their parameters (no wire clutter).
            - FILE 2 (`<scene>_connections.json`): Contains ONLY the logical connections between blocks (`fromNodeId -> toNodeId`).
            
            Analyze these $MARKED_TAG blocks and connections, stamp your findings onto the Blackboard, and choose which robot receives the clipboard next:
            - "CALL_2" if we now need to choose blocks & read their full guide.
            - "CALL_3" if we already know the exact edits/connections to apply.
            - "CALL_6_QUESTION" if we should ask the player a 4-option question first.
            - "CALL_5" if we need another Funnel pass.
            - "CALL_4" if the player only wanted an analysis and we are ready for Final Review ($END_TOKEN).
            
            Respond ONLY with valid JSON in Portuguese:
            {
              "analysisSummary": "Análise focada nos blocos $MARKED_TAG, indicando qual ID de nó deve ser conectado/editado",
              "nextCall": "CALL_$defaultNextCall",
              "nextCallInstruction": "Instrução clara para o próximo robô na prancheta"
            }
        """.trimIndent()

        val prompt5b = """
            ${blackboard.renderPaperForNextRobot(query)}
            
            [SEPARATED SCENE FILES FOR $MARKED_TAG ITEMS ONLY]:
            $separatedMarkedFilesText
        """.trimIndent()

        val raw5b = callLLM(prompt5b, sys5b).trim()
        val obj5b = parseJsonObjectSafe(raw5b)
        val analysisText = obj5b?.get("analysisSummary")?.asString?.ifBlank { null } ?: raw5b
        val nextCall = parseNextCallNumber(obj5b?.get("nextCall")?.asString, defaultNextCall)
        val nextInst = obj5b?.get("nextCallInstruction")?.asString ?: blackboard.userRequest

        appendEntry(
            ChatRole.CALL_TRACE,
            "🔬 [Chamada 5b #$passNum] Análise $MARKED_TAG → Próx: Chamada $nextCall",
            analysisText.take(450) + if (analysisText.length > 450) "..." else "",
            0xFFC084FC.toInt()
        )
        return CallStepResult(analysisText, nextCall, nextInst)
    }

    private fun buildChunkIndexFor5aFunnel(
        proj: StoryProject,
        nodeChunk: List<NodeData>,
        partNum: Int,
        totalParts: Int,
        blackboard: SharedBlackboard
    ): String {
        return buildString {
            appendLine("Project: id='${proj.id}', activeSceneId='${proj.activeSceneId}' (Funnel Part $partNum of $totalParts)")
            appendLine("Variables (${proj.variables.size}): ${proj.variables.joinToString { "${it.id}:${it.type}=${it.defaultValue}" }}")
            appendLine("Scenes (${proj.scenes.size}): ${proj.scenes.joinToString { "${it.title}(id=${it.id}, nodes=${it.nodes.size}, conns=${it.connections.size})" }}")
            appendLine("Nodes in this Funnel Slice (${nodeChunk.size}):")
            for (n in nodeChunk) {
                val alreadyMarked = if (blackboard.markedNodeIds.contains(n.id)) " $MARKED_TAG" else ""
                val sub = n.params["actionSubtype"] ?: n.params["triggerType"] ?: n.params["questTrigger"] ?: ""
                val subStr = if (sub.isNotBlank()) "($sub)" else ""
                val sc = proj.scenes.find { it.id == n.parentSceneId }
                val relX = (n.x - (sc?.x ?: 0.0)).toInt()
                val relY = (n.y - (sc?.y ?: 0.0)).toInt()
                appendLine("  - id=\"${n.id}\"$alreadyMarked | scene=${n.parentSceneId} | type=${n.nodeType}$subStr | title=\"${n.title}\" | pos=($relX,$relY)")
            }
        }
    }

    private fun extractMarkedNodesAndConnectionsSeparated(
        proj: StoryProject,
        blackboard: SharedBlackboard,
        inspectMode: String
    ): String {
        val activeScene = proj.getActiveScene()
        val targetScenes = proj.scenes.filter { blackboard.markedSceneIds.contains(it.id) }.ifEmpty { listOfNotNull(activeScene) }

        val includeNodes = inspectMode != "CONNECTIONS_ONLY"
        val includeConnections = inspectMode != "NODES_ONLY"

        return buildString {
            for (sc in targetScenes) {
                val markedInScene = sc.nodes.filter {
                    blackboard.markedNodeIds.contains(it.id) ||
                        blackboard.markedNodeIds.contains("ALL_ACTIVE_SCENE") ||
                        blackboard.markedNodeIds.contains("*")
                }.ifEmpty { sc.nodes.takeLast(12) }

                if (includeNodes) {
                    appendLine("=== [ARQUIVO 1: scenes/${sc.title.lowercase().replace(" ", "_")}_nodes.json (SOMENTE BLOCOS $MARKED_TAG)] ===")
                    val nodesArr = JsonArray()
                    for (n in markedInScene.take(30)) {
                        val nObj = JsonObject()
                        nObj.addProperty("marked", MARKED_TAG)
                        nObj.addProperty("id", n.id)
                        nObj.addProperty("title", n.title)
                        nObj.addProperty("nodeType", n.nodeType.name)
                        nObj.addProperty("content", n.content)
                        nObj.addProperty("x", (n.x - sc.x).toInt())
                        nObj.addProperty("y", (n.y - sc.y).toInt())
                        nObj.add("portsOut", StorySerializer.gson.toJsonTree(n.outputs.map { it.name }))
                        nObj.add("portsIn", StorySerializer.gson.toJsonTree(n.inputs.map { it.name }))
                        nObj.add("params", StorySerializer.gson.toJsonTree(n.params))
                        nodesArr.add(nObj)
                    }
                    appendLine(StorySerializer.gson.toJson(nodesArr))
                }

                if (includeConnections) {
                    appendLine("\n=== [ARQUIVO 2: scenes/${sc.title.lowercase().replace(" ", "_")}_connections.json (SOMENTE CONEXÕES LÓGICAS)] ===")
                    if (sc.connections.isEmpty()) {
                        appendLine("[] (Nenhuma conexão na cena ainda)")
                    } else {
                        for (c in sc.connections) {
                            val fn = sc.nodes.find { it.id == c.fromNodeId }
                            val tn = sc.nodes.find { it.id == c.toNodeId }
                            appendLine("  • [FROM] id=\"${c.fromNodeId}\" ('${fn?.title ?: "?"}', port=${c.fromPortId}) ➔ [TO] id=\"${c.toNodeId}\" ('${tn?.title ?: "?"}', port=${c.toPortId})")
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // CALL 2: BLOCK CATALOG & SELECTION (SUB-CALL 2A + SUB-CALL 2B)
    // =========================================================================
    private fun executeCall2SelectBlocks(
        blackboard: SharedBlackboard,
        currentFocusInstruction: String,
        passNum: Int,
        defaultNextCall: Int
    ): CallStepResult {
        // --- SUB-CALL 2A: Send compact list of ALL existing nodes + arguments + Scene Structure ---
        activeCallStage = 21
        currentStatusText = "2a. Escolhendo nós de interesse (#$passNum)..."

        val compactCatalog2a = buildCompactNodesAndSceneStructureIndexFor2a()
        val sys2a = """
            You are Robot 2a (Call 2a: Block Interest Selector) of the CobbleBrain Story Editor AI Assistant.
            You receive the Shared Blackboard and the list of all existing node names and ACTION / TRIGGER subtype names (ONLY the names, without arguments or ports).
            Choose which specific nodes (and `ACTION:<SUBTYPE>` / `TRIGGER:<SUBTYPE>`) you want to receive the complete explanations, arguments, ports, and available choices for in Sub-call 2b.
            
            Respond ONLY with a valid JSON object:
            {
              "interestedNodes": [
                "DIALOGUE",
                "QUEST",
                "ACTION:SPAWN_COBBLEMON",
                "ACTION:ANIMATION",
                "TRIGGER:POKEMON_CATCH",
                "END_SCENE"
              ],
              "selectionReason": "Resumo curto em português explicando por que esses blocos foram escolhidos"
            }
        """.trimIndent()

        val prompt2a = """
            ${blackboard.renderPaperForNextRobot(currentFocusInstruction)}
            
            [EXISTING NODE & SUBTYPE NAMES (SUB-CALL 2A - ONLY NAMES)]:
            $compactCatalog2a
        """.trimIndent()

        val raw2a = callLLM(prompt2a, sys2a)
        val obj2a = parseJsonObjectSafe(raw2a)
        val interestedNodes = mutableListOf<String>()
        obj2a?.getAsJsonArray("interestedNodes")?.forEach { el ->
            if (el.isJsonPrimitive) interestedNodes.add(el.asString.trim().uppercase())
        }
        if (interestedNodes.isEmpty()) {
            interestedNodes.addAll(listOf("DIALOGUE", "QUEST", "ACTION", "END_SCENE"))
        }
        val reason2a = obj2a?.get("selectionReason")?.asString ?: "Nós de interesse selecionados."

        appendEntry(
            ChatRole.CALL_TRACE,
            "🧱 [Chamada 2a #$passNum] Nós de Interesse",
            "Escolhidos: ${interestedNodes.joinToString(", ")}\n$reason2a",
            0xFFF59E0B.toInt()
        )

        // --- SUB-CALL 2B: Deliver complete explanations & all available choices from story_editor_blocks_guide.txt ---
        activeCallStage = 22
        currentStatusText = "2b. Lendo guia completo dos blocos (#$passNum)..."

        val fullExplanationsForSelectedNodes = extractFullNodeExplanationsFromGuide(interestedNodes)

        val sys2b = """
            You are Robot 2b (Call 2b: Detailed Block Configuration Planner) of the CobbleBrain Story Editor AI Assistant.
            In Sub-call 2a you selected the nodes of interest: ${interestedNodes.joinToString(", ")}.
            Now you receive the COMPLETE EXPLANATIONS and ALL AVAILABLE CHOICES/OPTIONS for those selected nodes from `story_editor_blocks_guide.txt`.
            
            Stamp the exact block sequence, chosen options, and parameters onto the Shared Blackboard so Robot 3 can build them.
            You can also choose which robot receives the clipboard next:
            - "CALL_3" (default when building/editing) to position and connect the blocks on the project.
            - "CALL_6_QUESTION" if you saw multiple awesome options in the guide (e.g. quest triggers or speaker modes) and want to ask the player a 4-option question before building!
            - "CALL_2" if you want to read the guide of OTHER nodes first.
            - "CALL_5" if you need to inspect the JSON funnel first.
            - "CALL_4" if the user only asked a question about blocks and we are ready for Final Review ($END_TOKEN).
            
            Respond ONLY with valid JSON in Portuguese:
            {
              "blockConfigurationPlan": "Plano detalhado anotado na prancheta com cada nó, opções escolhidas, posição sugerida e em qual bloco cada um se conecta",
              "nextCall": "CALL_$defaultNextCall",
              "nextCallInstruction": "Instrução para o próximo robô"
            }
        """.trimIndent()

        val prompt2b = """
            ${blackboard.renderPaperForNextRobot(currentFocusInstruction)}
            
            [SELECTED NODES OF INTEREST (FROM 2A)]:
            ${interestedNodes.joinToString(", ")} ($reason2a)
            
            [COMPLETE EXPLANATIONS & ALL AVAILABLE CHOICES FOR SELECTED NODES (FROM story_editor_blocks_guide.txt)]:
            $fullExplanationsForSelectedNodes
        """.trimIndent()

        val raw2b = callLLM(prompt2b, sys2b).trim()
        val obj2b = parseJsonObjectSafe(raw2b)
        val finalBlockPlan2b = obj2b?.get("blockConfigurationPlan")?.asString?.ifBlank { null } ?: raw2b
        val nextCall = parseNextCallNumber(obj2b?.get("nextCall")?.asString, defaultNextCall)
        val nextInst = obj2b?.get("nextCallInstruction")?.asString ?: blackboard.userRequest

        appendEntry(
            ChatRole.CALL_TRACE,
            "📖 [Chamada 2b #$passNum] Guia Completo & Opções → Próx: Chamada $nextCall",
            finalBlockPlan2b.take(450) + if (finalBlockPlan2b.length > 450) "..." else "",
            0xFFFBBF24.toInt()
        )
        return CallStepResult(finalBlockPlan2b, nextCall, nextInst)
    }

    private fun buildCompactNodesAndSceneStructureIndexFor2a(): String {
        val nodeNames = NodeType.entries.joinToString(", ") { it.name }
        val actionNames = ActionRegistry.actions.joinToString(", ") { "ACTION:${it.id}" }
        val triggerNames = TriggerRegistry.triggers.joinToString(", ") { "TRIGGER:${it.id}" }
        return """
            === 1. SCENE STRUCTURE ===
            SCENE_STRUCTURE, StoryProject, SceneData (<scene>_nodes.json, <scene>_connections.json), StoryVariable (BOOLEAN, NUMBER, STRING, LIST), BEGIN_SCENE -> END_SCENE

            === 2. NODE TYPES (ONLY NAMES) ===
            $nodeNames

            === 3. ACTION SUBTYPES (ONLY NAMES) ===
            $actionNames

            === 4. TRIGGER / QUEST SUBTYPES (ONLY NAMES) ===
            $triggerNames
        """.trimIndent()
    }

    private fun loadFullBlocksGuideText(): String {
        return try {
            val externalFile = java.io.File("cobblebrain/story_editor_blocks_guide.txt")
            val resourceStream = StoryEditorAIAssistantWidget::class.java.getResourceAsStream("/assets/cobblebrain/story_editor_blocks_guide.txt")
            val resourceText = resourceStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

            if (resourceText.isNotBlank()) {
                runCatching {
                    externalFile.parentFile?.mkdirs()
                    if (!externalFile.exists() || externalFile.readText(Charsets.UTF_8) != resourceText) {
                        externalFile.writeText(resourceText, Charsets.UTF_8)
                    }
                }
                resourceText
            } else if (externalFile.exists() && externalFile.length() > 100) {
                externalFile.readText(Charsets.UTF_8)
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractFullNodeExplanationsFromGuide(interestedNodes: List<String>): String {
        val guideText = loadFullBlocksGuideText()
        if (guideText.isBlank()) {
            return "Guia de blocos não encontrado; use os parâmetros padrão dos nós: ${interestedNodes.joinToString(", ")}"
        }

        val extractedSections = LinkedHashSet<String>()

        fun extractBlock(tagType: String, tagName: String): String? {
            val startMarker = "=== [$tagType: $tagName] ==="
            val endMarker = "=== [/$tagType: $tagName] ==="
            val startIdx = guideText.indexOf(startMarker)
            if (startIdx == -1) return null
            val endIdx = guideText.indexOf(endMarker, startIdx)
            return if (endIdx != -1) {
                guideText.substring(startIdx, endIdx + endMarker.length).trim()
            } else {
                guideText.substring(startIdx).take(2500).trim()
            }
        }

        var addedActionHeader = false
        var addedTriggerHeader = false

        for (rawItem in interestedNodes) {
            val item = rawItem.trim().uppercase()
            when {
                item.startsWith("ACTION:") -> {
                    val rawSub = item.removePrefix("ACTION:").trim()
                    val sub = ActionRegistry.normalizeId(rawSub)
                    if (!addedActionHeader) {
                        extractedSections.add(
                            "=== [NODE: ACTION (CABEÇALHO)] ===\n" +
                                "NOME: ACTION | PORTAS: In -> Out | PARÂMETRO PRINCIPAL: params[\"actionSubtype\"] = \"<SUBTYPE>\"."
                        )
                        addedActionHeader = true
                    }
                    val subBlock = extractBlock("ACTION", sub)
                    if (subBlock != null) {
                        extractedSections.add(subBlock)
                    } else {
                        extractBlock("NODE", "ACTION")?.let { extractedSections.add(it) }
                    }
                    // If the AI asked about entity effects, also include ANIMATION & COMMAND_NODE tips so it knows how to sleep/wake or clear effects
                    if (sub == "ADD_ENTITY_EFFECT") {
                        extractBlock("ACTION", "ANIMATION")?.let { extractedSections.add(it) }
                    }
                }
                item.startsWith("TRIGGER:") -> {
                    val sub = item.removePrefix("TRIGGER:").trim()
                    if (!addedTriggerHeader) {
                        extractedSections.add(
                            "=== [NODE: TRIGGER (CABEÇALHO)] ===\n" +
                                "NOME: TRIGGER | PORTAS: In -> Out | PARÂMETROS GERAIS: params[\"triggerType\"] = \"<SUBTYPE>\", requireInputSignal (\"true\"=sequencial / \"false\"=ouvinte global), triggerCondition (\"IF\" / \"IF_NOT\")."
                        )
                        addedTriggerHeader = true
                    }
                    val subBlock = extractBlock("TRIGGER", sub)
                    if (subBlock != null) {
                        extractedSections.add(subBlock)
                    } else {
                        extractBlock("NODE", "TRIGGER")?.let { extractedSections.add(it) }
                    }
                }
                else -> {
                    extractBlock("NODE", item)?.let { extractedSections.add(it) }
                        ?: extractBlock("ACTION", ActionRegistry.normalizeId(item))?.let { extractedSections.add(it) }
                        ?: extractBlock("TRIGGER", item)?.let { extractedSections.add(it) }
                }
            }
        }

        return extractedSections.joinToString("\n\n")
    }

    // =========================================================================
    // CALL 3: EXECUTE GRAPH OPERATIONS ON STORY PROJECT
    // (Logical connections without wire coordinates + Automatic Backup Snapshot)
    // =========================================================================
    private fun executeCall3WorkOnProject(
        blackboard: SharedBlackboard,
        taskDescription: String,
        passNum: Int
    ): CallStepResult {
        activeCallStage = 3
        currentStatusText = "3. Trabalhando nos blocos do projeto (#$passNum)..."

        val proj = editor.project
        val activeScene = proj.getActiveScene()
        val currentSceneSnapshot = buildActiveSceneSeparatedSnapshotForCall3(proj, activeScene, blackboard)

        val systemPrompt = """
            You are Robot 3 (Call 3: Project Graph Builder & Logical Connector) of the CobbleBrain Story Editor AI Assistant.
            Read the Shared Blackboard and generate the JSON operations to place/edit/remove blocks and logically connect them.
            
            IMPORTANT CONNECTION & LAYOUT RULES:
            1. NEVER specify wire/pin coordinates! The mod calculates all wire pin coordinates automatically.
            2. You CAN organize block positions on the canvas using "x" and "y" (e.g. spacing blocks horizontally by ~220px, like x=260, x=480, x=700, or vertically by ~130px for branches), OR omit "x"/"y" to let the mod auto-position them cleanly from left to right.
            3. To connect blocks, you ONLY state WHICH block connects to WHICH block:
               - Either right inside `ADD_NODE` using `"connectFrom": "BEGIN_SCENE"` (or `"connectFrom": "n1"`, or `"connectFrom": "n2:Success"`),
               - Or using `{ "op": "CONNECT", "from": "n1", "to": "n2", "fromPort": "Out" }`!
               The mod automatically finds the ports and connects all wires!
            
            SUPPORTED OPERATIONS FORMAT:
            {
              "operations": [
                { "op": "ADD_VARIABLE", "varId": "coins", "varType": "NUMBER", "defaultValue": "0", "scope": "GLOBAL" },
                { "op": "EDIT_SCENE", "title": "Novo Nome da Cena", "description": "Descrição opcional da cena" },
                { "op": "ADD_SCENE", "tempId": "sc_new", "title": "Floresta", "description": "Cena 2", "setActive": true },
                {
                  "op": "ADD_NODE",
                  "tempId": "n1",
                  "nodeType": "DIALOGUE",
                  "subtype": "",
                  "title": "Fala do Pikachu",
                  "content": "Pika! Vamos explorar a floresta, {player}!",
                  "x": 260,
                  "y": 100,
                  "connectFrom": "BEGIN_SCENE",
                  "postDelayTicks": 20,
                  "params": {
                    "useAi": "false",
                    "speakerMode": "COBBLEBRAIN",
                    "speakerType": "BY_SPECIES",
                    "targetSpecies": "Pikachu",
                    "enableChatBubble": "true"
                  }
                },
                {
                  "op": "ADD_NODE",
                  "tempId": "n2",
                  "nodeType": "QUEST",
                  "title": "Missão: Capturar Caterpie",
                  "x": 480,
                  "y": 100,
                  "connectFrom": "n1",
                  "params": {
                    "questTitle": "Capture 2 Caterpie",
                    "questTrigger": "POKEMON_CATCH",
                    "targetSpecies": "Caterpie",
                    "targetCount": "2",
                    "showHud": "true"
                  }
                },
                { "op": "CONNECT", "from": "n2", "fromPort": "Success", "to": "n3" },
                { "op": "EDIT_NODE", "nodeId": "existing_id_or_tempId", "title": "Novo Título", "content": "...", "params": { "key": "val" } },
                { "op": "REMOVE_NODE", "nodeId": "node_id_to_delete" },
                { "op": "DISCONNECT", "from": "id1", "to": "id2" }
              ],
              "nextCall": "CALL_4",
              "nextCallInstruction": "Revisar o projeto após estas operações"
            }
        """.trimIndent()

        val userPrompt = """
            ${blackboard.renderPaperForNextRobot(taskDescription)}
            
            [CURRENT ACTIVE SCENE (SEPARATED NODES & LOGICAL CONNECTIONS)]:
            $currentSceneSnapshot
        """.trimIndent()

        val raw = callLLM(userPrompt, systemPrompt)
        val jsonObj = parseJsonObjectSafe(raw)
        val opsArray = jsonObj?.getAsJsonArray("operations")
        val nextCall = parseNextCallNumber(jsonObj?.get("nextCall")?.asString, 4)
        val nextInst = jsonObj?.get("nextCallInstruction")?.asString ?: "Revisar alterações aplicadas."

        if (opsArray == null || opsArray.size() == 0) {
            val msg = "Nenhuma operação estruturada retornada na Chamada 3 (#$passNum)."
            appendEntry(ChatRole.CALL_TRACE, "🛠️ [Chamada 3 #$passNum] Execução", msg, 0xFF10B981.toInt())
            return CallStepResult(msg, nextCall, nextInst)
        }

        // Apply operations safely on the Minecraft client thread and take a Backup Snapshot first!
        val appliedLogs = mutableListOf<String>()
        val latch = java.util.concurrent.CountDownLatch(1)

        Minecraft.getInstance().execute {
            try {
                // Save original backup snapshot if not already saved in this session so user can Accept or Reject!
                if (projectBackupSnapshot == null) {
                    projectBackupSnapshot = StorySerializer.deepCopyProject(editor.project)
                    pendingChangesCount = 0
                }
                applyOperationsToProject(opsArray, appliedLogs)
                pendingChangesCount += appliedLogs.size
                editor.markDirty()
                editor.rebuildNodeWidgets()
            } catch (e: Exception) {
                AIHandler.logStoryEditor("[EXCEPTION IN CALL 3 APPLY]: ${e.stackTraceToString()}")
                appliedLogs.add("Erro ao aplicar operação: ${e.message}")
            } finally {
                latch.countDown()
            }
        }
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS)

        val logText = if (appliedLogs.isEmpty()) "Nenhuma alteração aplicada." else appliedLogs.joinToString("\n")
        appendEntry(
            ChatRole.CALL_TRACE,
            "🛠️ [Chamada 3 #$passNum] Alterações (${appliedLogs.size}) → Próx: Chamada $nextCall",
            logText.take(500) + if (logText.length > 500) "..." else "",
            0xFF10B981.toInt()
        )
        return CallStepResult(logText, nextCall, nextInst)
    }

    private fun buildActiveSceneSeparatedSnapshotForCall3(
        proj: StoryProject,
        scene: SceneData?,
        blackboard: SharedBlackboard
    ): String {
        if (scene == null) return "No active scene."
        val focusedIds = getEffectiveFocusedNodes().map { it.id }.toSet()
        return buildString {
            appendLine("Active Scene: '${scene.title}' (id=${scene.id}, size=${scene.width.toInt()}x${scene.height.toInt()})")
            appendLine("Variables: ${proj.variables.joinToString { "${it.id}(${it.type}=${it.defaultValue})" }}")
            appendLine("--- [ARQUIVO 1: BLOCOS DA CENA (${scene.nodes.size}) - Coordenadas x/y relativas à cena] ---")
            for (n in scene.nodes) {
                val markBadge = when {
                    focusedIds.contains(n.id) -> " [FOCUSED_BY_PLAYER]"
                    blackboard.markedNodeIds.contains(n.id) -> " $MARKED_TAG"
                    else -> ""
                }
                val outPorts = n.outputs.joinToString { it.name }
                val relX = (n.x - scene.x).toInt()
                val relY = (n.y - scene.y).toInt()
                appendLine("  - id=\"${n.id}\"$markBadge | type=${n.nodeType} | title=\"${n.title}\" | pos=($relX,$relY) | outPorts=[$outPorts] | content=\"${n.content.take(55)}\" | params=${n.params}")
            }
            appendLine("--- [ARQUIVO 2: CONEXÕES LÓGICAS DA CENA (${scene.connections.size})] ---")
            for (c in scene.connections) {
                val fn = scene.nodes.find { it.id == c.fromNodeId }
                val tn = scene.nodes.find { it.id == c.toNodeId }
                appendLine("  - \"${c.fromNodeId}\" ('${fn?.title ?: "?"}', port=${c.fromPortId}) ➔ \"${c.toNodeId}\" ('${tn?.title ?: "?"}')")
            }
        }
    }

    private fun applyOperationsToProject(opsArray: JsonArray, logs: MutableList<String>) {
        val proj = editor.project
        var targetScene = getEffectiveFocusedScene() ?: proj.getActiveScene() ?: proj.scenes.firstOrNull()
        if (targetScene == null) {
            targetScene = SceneData.createWithStartNode(title = "Initial Scene", isStartScene = true)
            proj.scenes.add(targetScene)
            proj.activeSceneId = targetScene.id
        }

        val tempNodeIdMap = mutableMapOf<String, NodeData>()
        val deferredConnectOps = mutableListOf<Triple<String, String, NodeData>>() // (fromRef, fromPort, toNode)

        val focusedNodes = getEffectiveFocusedNodes()
        val anchorNode = focusedNodes.maxByOrNull { it.x + it.width }
            ?: targetScene.nodes.maxByOrNull { it.x + it.width }

        var nextAutoX = if (anchorNode != null) {
            anchorNode.x + anchorNode.width + 60.0
        } else {
            targetScene.x + 40.0
        }
        var nextAutoY = anchorNode?.y ?: (targetScene.y + 60.0)
        var lastExistingOrCreatedNode: NodeData? = anchorNode

        fun resolveNode(ref: String?): NodeData? {
            if (ref.isNullOrBlank()) return null
            val trimmed = ref.trim().substringBefore(':')
            tempNodeIdMap[trimmed]?.let { return it }
            val sc = targetScene ?: return null
            if (trimmed.equals("BEGIN_SCENE", true) || trimmed.equals("START", true) || trimmed.equals("SCENE_START", true)) {
                return sc.nodes.find { it.nodeType == NodeType.BEGIN_SCENE }
            }
            if (trimmed.equals("LAST_NODE", true)) {
                return lastExistingOrCreatedNode ?: sc.nodes.lastOrNull()
            }
            sc.nodes.find { it.id == trimmed }?.let { return it }
            proj.scenes.flatMap { it.nodes }.find { it.id == trimmed }?.let { return it }
            sc.nodes.find { it.title.equals(trimmed, ignoreCase = true) }?.let { return it }
            return null
        }

        fun connectNodesAutomatically(sc: SceneData, fromNode: NodeData, fromPortHint: String, toNode: NodeData, toPortHint: String = "In") {
            if (fromNode.id == toNode.id) return
            if (toNode.nodeType == NodeType.KEY_INPUT && toNode.inputs.isEmpty()) {
                toNode.inputs.add(PortData(id = "IN", name = "In", type = PortType.INPUT))
                toNode.params["triggerMode"] = "SEQUENTIAL"
                toNode.params["requireInputSignal"] = "true"
            }

            val outPort = fromNode.outputs.find {
                it.name.equals(fromPortHint, ignoreCase = true) || it.id.equals(fromPortHint, ignoreCase = true)
            } ?: fromNode.outputs.firstOrNull()

            val inPort = toNode.inputs.find {
                it.name.equals(toPortHint, ignoreCase = true) || it.id.equals(toPortHint, ignoreCase = true)
            } ?: toNode.inputs.firstOrNull()

            if (outPort != null && inPort != null) {
                val alreadyConnected = sc.connections.any {
                    it.fromNodeId == fromNode.id && it.fromPortId == outPort.id &&
                        it.toNodeId == toNode.id && it.toPortId == inPort.id
                }
                if (!alreadyConnected) {
                    sc.connections.add(
                        ConnectionData(
                            fromNodeId = fromNode.id,
                            fromPortId = outPort.id,
                            toNodeId = toNode.id,
                            toPortId = inPort.id
                        )
                    )
                    logs.add("• Conectado auto: '${fromNode.title}' (${outPort.name}) ➔ '${toNode.title}'")
                }
            }
        }

        for (el in opsArray) {
            if (!el.isJsonObject) continue
            val opObj = el.asJsonObject
            val opType = opObj.get("op")?.asString?.uppercase()?.trim() ?: continue

            when (opType) {
                "ADD_VARIABLE" -> {
                    val varId = opObj.get("varId")?.asString?.trim()?.replace(" ", "_") ?: continue
                    val varTypeStr = opObj.get("varType")?.asString?.uppercase() ?: "STRING"
                    val varType = runCatching { VariableType.valueOf(varTypeStr) }.getOrDefault(VariableType.STRING)
                    val defVal = opObj.get("defaultValue")?.asString ?: when (varType) {
                        VariableType.BOOLEAN -> "false"
                        VariableType.NUMBER -> "0"
                        else -> ""
                    }
                    val scopeStr = opObj.get("scope")?.asString?.uppercase() ?: "GLOBAL"
                    val scope = runCatching { VariableScope.valueOf(scopeStr) }.getOrDefault(VariableScope.GLOBAL)

                    val existing = proj.variables.find { it.id.equals(varId, true) }
                    if (existing != null) {
                        existing.type = varType
                        existing.defaultValue = defVal
                        logs.add("• Variável atualizada: $varId ($varType = $defVal)")
                    } else {
                        proj.variables.add(
                            StoryVariable(
                                id = varId,
                                name = varId,
                                type = varType,
                                defaultValue = defVal,
                                scope = scope,
                                sceneId = if (scope == VariableScope.SCENE_LOCAL) targetScene?.id else null
                            )
                        )
                        logs.add("• Variável criada: $varId ($varType = $defVal)")
                    }
                }

                "ADD_SCENE" -> {
                    val title = opObj.get("title")?.asString?.ifBlank { "Nova Cena" } ?: "Nova Cena"
                    val desc = opObj.get("description")?.asString ?: ""
                    val setActive = opObj.get("setActive")?.asBoolean ?: true
                    val lastSc = proj.scenes.lastOrNull()
                    val sx = if (lastSc != null) lastSc.x + lastSc.width + 100.0 else 0.0
                    val sy = lastSc?.y ?: 0.0
                    val newScene = SceneData.createWithStartNode(
                        title = title,
                        description = desc,
                        x = sx,
                        y = sy,
                        width = 600.0,
                        height = 380.0
                    )
                    proj.scenes.add(newScene)
                    if (setActive) {
                        proj.activeSceneId = newScene.id
                        targetScene = newScene
                        val startNode = newScene.nodes.firstOrNull()
                        nextAutoX = (startNode?.x ?: newScene.x) + (startNode?.width ?: 160.0) + 60.0
                        nextAutoY = startNode?.y ?: (newScene.y + 60.0)
                        lastExistingOrCreatedNode = startNode
                    }
                    logs.add("• Cena criada: '$title'")
                }

                "EDIT_SCENE", "RENAME_SCENE", "UPDATE_SCENE" -> {
                    val scId = opObj.get("sceneId")?.asString?.trim() ?: opObj.get("id")?.asString?.trim()
                    val scToEdit = if (!scId.isNullOrBlank()) {
                        proj.scenes.find { it.id == scId || it.title.equals(scId, ignoreCase = true) } ?: targetScene
                    } else targetScene
                    if (scToEdit != null) {
                        val newTitle = opObj.get("title")?.asString?.trim()
                            ?: opObj.get("newTitle")?.asString?.trim()
                            ?: opObj.get("sceneTitle")?.asString?.trim()
                            ?: opObj.get("name")?.asString?.trim()
                        if (!newTitle.isNullOrBlank()) {
                            val oldTitle = scToEdit.title
                            scToEdit.title = newTitle
                            logs.add("• Cena renomeada: '$oldTitle' ➔ '$newTitle'")
                        }
                        opObj.get("description")?.asString?.let { scToEdit.description = it }
                    }
                }

                "ADD_NODE" -> {
                    val sc = targetScene ?: continue
                    val titleHint = opObj.get("title")?.asString?.trim() ?: ""
                    val rawType = (opObj.get("nodeType")?.asString ?: opObj.get("type")?.asString)?.uppercase()?.trim()
                        ?: if (
                            titleHint.contains("Fim da Cena", ignoreCase = true) ||
                            titleHint.equals("Finish Scene", ignoreCase = true) ||
                            titleHint.equals("End Scene", ignoreCase = true) ||
                            titleHint.equals("Encerrar Cena", ignoreCase = true)
                        ) "END_SCENE" else "DIALOGUE"
                    val nodeType = parseNodeTypeFlexible(rawType)
                    val subtype = opObj.get("subtype")?.asString?.trim()
                        ?: opObj.getAsJsonObject("params")?.get("actionSubtype")?.asString
                        ?: opObj.getAsJsonObject("params")?.get("triggerType")?.asString

                    // AI can specify node canvas coordinates (x, y) relative to the scene (or world coordinates), or omit for auto-layout
                    val customX = opObj.get("x")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() }
                    val customY = opObj.get("y")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() }
                    val finalX = if (customX != null) {
                        if (sc.x > 50.0 && customX >= sc.x - 20.0) customX else (sc.x + customX.coerceAtLeast(20.0))
                    } else nextAutoX
                    val finalY = if (customY != null) {
                        if (sc.y > 50.0 && customY >= sc.y - 20.0) customY else (sc.y + customY.coerceAtLeast(30.0))
                    } else nextAutoY

                    val createdNode = createConfiguredNodeData(
                        proj = proj,
                        scene = sc,
                        type = nodeType,
                        subtype = subtype,
                        x = finalX,
                        y = finalY
                    )

                    opObj.get("title")?.asString?.takeIf { it.isNotBlank() }?.let { createdNode.title = it }
                    opObj.get("content")?.asString?.let { createdNode.content = it }
                    opObj.get("preDelayTicks")?.asInt?.let { createdNode.preDelayTicks = it.coerceAtLeast(0) }
                    opObj.get("postDelayTicks")?.asInt?.let { createdNode.postDelayTicks = it.coerceAtLeast(0) }

                    opObj.getAsJsonObject("params")?.entrySet()?.forEach { (k, v) ->
                        if (v.isJsonPrimitive) {
                            createdNode.params[k] = v.asString
                        }
                    }

                    normalizeNodeParamsAfterEdit(createdNode, subtype)

                    if (nodeType == NodeType.COMMAND_NODE) {
                        if (createdNode.content.isNotBlank() && createdNode.params["commands"].isNullOrBlank()) {
                            createdNode.params["commands"] = createdNode.content
                        } else if (createdNode.content.isBlank() && !createdNode.params["commands"].isNullOrBlank()) {
                            createdNode.content = createdNode.params["commands"]!!
                        }
                    }

                    if (nodeType == NodeType.DIALOGUE && createdNode.params["useAi"] == "true") {
                        if (createdNode.outputs.none { it.id == "OUT_FALLBACK" || it.name.equals("Fallback", true) }) {
                            createdNode.outputs.add(PortData(id = "OUT_FALLBACK", name = "Fallback", type = PortType.OUTPUT))
                        }
                    }

                    if (nodeType == NodeType.KEY_INPUT &&
                        (createdNode.params["triggerMode"] == "SEQUENTIAL" || createdNode.params["requireInputSignal"] == "true")) {
                        if (createdNode.inputs.isEmpty()) {
                            createdNode.inputs.add(PortData(id = "IN", name = "In", type = PortType.INPUT))
                        }
                    }

                    sc.nodes.add(createdNode)

                    val tempId = opObj.get("tempId")?.asString?.trim() ?: opObj.get("id")?.asString?.trim()
                    if (!tempId.isNullOrBlank()) {
                        tempNodeIdMap[tempId] = createdNode
                    }
                    lastExistingOrCreatedNode = createdNode

                    // Support automatic logical connection shorthand directly on ADD_NODE ("connectFrom": "n1" or "n1:Success")
                    val connectFromRaw = opObj.get("connectFrom")?.asString?.trim()
                    if (!connectFromRaw.isNullOrBlank()) {
                        val fromIdPart = connectFromRaw.substringBefore(':').trim()
                        val fromPortPart = if (connectFromRaw.contains(':')) connectFromRaw.substringAfter(':').trim() else (opObj.get("fromPort")?.asString ?: "Out")
                        deferredConnectOps.add(Triple(fromIdPart, fromPortPart, createdNode))
                    }

                    nextAutoX = createdNode.x + createdNode.width + 55.0
                    val requiredWidth = (createdNode.x + createdNode.width + 50.0) - sc.x
                    if (requiredWidth > sc.width) sc.width = requiredWidth
                    val requiredHeight = (createdNode.y + createdNode.height + 50.0) - sc.y
                    if (requiredHeight > sc.height) sc.height = requiredHeight

                    logs.add("• Bloco posicionado: [${createdNode.nodeType}] '${createdNode.title}' em (${(createdNode.x - sc.x).toInt()},${(createdNode.y - sc.y).toInt()})")
                }

                "EDIT_NODE" -> {
                    val sc = targetScene
                    val targetId = opObj.get("nodeId")?.asString ?: opObj.get("id")?.asString
                    val node = resolveNode(targetId)
                    if (node != null) {
                        (opObj.get("nodeType")?.asString ?: opObj.get("type")?.asString)?.takeIf { it.isNotBlank() }?.let { rawType ->
                            node.nodeType = parseNodeTypeFlexible(rawType)
                        }
                        opObj.get("sceneTitle")?.asString?.takeIf { it.isNotBlank() }?.let { newScTitle ->
                            if (sc != null) {
                                val oldScTitle = sc.title
                                sc.title = newScTitle
                                logs.add("• Cena renomeada: '$oldScTitle' ➔ '$newScTitle'")
                            }
                        }
                        val editSubtype = opObj.get("subtype")?.asString?.trim()
                            ?: opObj.getAsJsonObject("params")?.get("actionSubtype")?.asString?.trim()
                            ?: opObj.getAsJsonObject("params")?.get("actionId")?.asString?.trim()
                            ?: opObj.getAsJsonObject("params")?.get("triggerType")?.asString?.trim()

                        if (!editSubtype.isNullOrBlank()) {
                            if (node.nodeType == NodeType.ACTION) {
                                val newAct = ActionRegistry.find(editSubtype)
                                val oldSubtype = node.params["actionSubtype"] ?: node.params["actionId"]
                                if (oldSubtype != newAct.id) {
                                    // Clear leftover params from a previous subtype (e.g., SPAWN_STRUCTURE's structureId/posX/posY/posZ)
                                    node.params.clear()
                                    newAct.defaultParams.forEach { (k, v) -> node.params[k] = v }
                                }
                                node.params["actionSubtype"] = newAct.id
                                node.params["actionId"] = newAct.id
                            } else if (node.nodeType == NodeType.TRIGGER) {
                                val newTrig = TriggerRegistry.find(editSubtype)
                                node.params["triggerType"] = newTrig.id
                                newTrig.defaultParams.forEach { (k, v) ->
                                    if (!node.params.containsKey(k)) node.params[k] = v
                                }
                            }
                        }

                        opObj.get("title")?.asString?.takeIf { it.isNotBlank() }?.let { node.title = it }
                        opObj.get("content")?.asString?.let { node.content = it }
                        opObj.get("x")?.takeIf { it.isJsonPrimitive }?.let {
                            runCatching {
                                val cx = it.asDouble
                                val sx = sc?.x ?: 0.0
                                node.x = if (sx > 50.0 && cx >= sx - 20.0) cx else (sx + cx)
                            }.getOrNull()
                        }
                        opObj.get("y")?.takeIf { it.isJsonPrimitive }?.let {
                            runCatching {
                                val cy = it.asDouble
                                val sy = sc?.y ?: 0.0
                                node.y = if (sy > 50.0 && cy >= sy - 20.0) cy else (sy + cy)
                            }.getOrNull()
                        }
                        opObj.get("preDelayTicks")?.asInt?.let { node.preDelayTicks = it.coerceAtLeast(0) }
                        opObj.get("postDelayTicks")?.asInt?.let { node.postDelayTicks = it.coerceAtLeast(0) }
                        opObj.getAsJsonObject("params")?.entrySet()?.forEach { (k, v) ->
                            if (v.isJsonPrimitive) {
                                node.params[k] = v.asString
                            }
                        }
                        normalizeNodeParamsAfterEdit(node, editSubtype)
                        if (node.nodeType == NodeType.COMMAND_NODE && node.content.isNotBlank()) {
                            node.params["commands"] = node.content
                        }
                        if (node.nodeType == NodeType.DIALOGUE && node.params["useAi"] == "true") {
                            if (node.outputs.none { it.id == "OUT_FALLBACK" || it.name.equals("Fallback", true) }) {
                                node.outputs.add(PortData(id = "OUT_FALLBACK", name = "Fallback", type = PortType.OUTPUT))
                            }
                        }
                        logs.add("• Bloco editado: '${node.title}'")
                    } else {
                        logs.add("• [Aviso] Bloco '$targetId' não encontrado para edição.")
                    }
                }

                "REMOVE_NODE", "DELETE_NODE" -> {
                    val targetId = opObj.get("nodeId")?.asString ?: opObj.get("id")?.asString
                    val node = resolveNode(targetId)
                    if (node != null && node.nodeType != NodeType.BEGIN_SCENE) {
                        editor.deleteNode(node)
                        logs.add("• Bloco removido: '${node.title}'")
                    }
                }

                "CONNECT", "CONNECT_NODES" -> {
                    val sc = targetScene ?: continue
                    val fromRef = opObj.get("fromNodeId")?.asString ?: opObj.get("from")?.asString
                    val toRef = opObj.get("toNodeId")?.asString ?: opObj.get("to")?.asString
                    val fromPortName = if (fromRef?.contains(':') == true) {
                        fromRef.substringAfter(':').trim()
                    } else {
                        opObj.get("fromPort")?.asString ?: "Out"
                    }
                    val toPortName = opObj.get("toPort")?.asString ?: "In"

                    val fromNode = resolveNode(fromRef)
                    val toNode = resolveNode(toRef)

                    if (fromNode != null && toNode != null) {
                        connectNodesAutomatically(sc, fromNode, fromPortName, toNode, toPortName)
                    }
                }

                "DISCONNECT", "DISCONNECT_NODES" -> {
                    val sc = targetScene ?: continue
                    val fromRef = opObj.get("fromNodeId")?.asString ?: opObj.get("from")?.asString
                    val toRef = opObj.get("toNodeId")?.asString ?: opObj.get("to")?.asString
                    val fromNode = resolveNode(fromRef)
                    val toNode = resolveNode(toRef)
                    if (fromNode != null && toNode != null) {
                        val removed = sc.connections.removeIf { it.fromNodeId == fromNode.id && it.toNodeId == toNode.id }
                        if (removed) {
                            logs.add("• Desconectado: '${fromNode.title}' ✂ '${toNode.title}'")
                        }
                    }
                }
            }
        }

        // Execute any deferred `connectFrom` connections declared directly inside ADD_NODE
        val sc = targetScene
        if (sc != null) {
            for ((fromRef, fromPort, toNode) in deferredConnectOps) {
                val fromNode = resolveNode(fromRef)
                if (fromNode != null) {
                    connectNodesAutomatically(sc, fromNode, fromPort, toNode, "In")
                }
            }
        }
    }

    // =========================================================================
    // CALL 4: FINAL REVIEW, REPEAT-CALL ROUTER & [ENDTOKEN] GATEKEEPER
    // =========================================================================
    private fun executeCall4ReviewWork(
        blackboard: SharedBlackboard,
        reviewRound: Int,
        forceFinalize: Boolean
    ): JsonObject {
        activeCallStage = 4
        currentStatusText = "4. Revisando prancheta (#$reviewRound)..."

        val proj = editor.project
        val activeScene = proj.getActiveScene()
        val postStateSnapshot = buildActiveSceneSeparatedSnapshotForCall3(proj, activeScene, blackboard)

        val disconnectedWarnings = mutableListOf<String>()
        if (activeScene != null) {
            for (n in activeScene.nodes) {
                if (n.nodeType != NodeType.BEGIN_SCENE && n.nodeType != NodeType.COMMENT && n.nodeType != NodeType.LINK_RECEIVE && n.inputs.isNotEmpty()) {
                    val isAutonomousTrigger = n.nodeType == NodeType.TRIGGER && n.params["requireInputSignal"] == "false"
                    val hasIncoming = activeScene.connections.any { it.toNodeId == n.id }
                    if (!hasIncoming && !isAutonomousTrigger) {
                        disconnectedWarnings.add("Node '${n.title}' (id=${n.id}, type=${n.nodeType}) has NO incoming connection on its In port!")
                    }
                }
            }
        }

        val systemPrompt = """
            You are Robot 4 (Call 4: Final Quality Reviewer & $END_TOKEN Gatekeeper) of the CobbleBrain Story Editor AI Assistant.
            You receive the Shared Blackboard after all previous robots have added their notes and drawings.
            
            HOW TO DECIDE:
            1. Check if all requested blocks/dialogues/quests/actions/analyses on the Blackboard were completed and logically connected.
            2. If MORE work is needed (e.g. a node is disconnected, a block was missed, we need to ask the player a 4-option question, or inspect another part of the JSON funnel) AND forceFinalize is false:
               - Set "status": "NEEDS_MORE_WORK"
               - Set "nextCallNeeded" to whichever robot should receive the Blackboard next: "CALL_3", "CALL_2", "CALL_5", "CALL_6_QUESTION", or "CALL_1".
               - Write a clear "followUpDescription" explaining what that robot must do.
               - Set "terminationCode": "" (do NOT emit $END_TOKEN yet).
            3. Once everything has been reviewed and fulfills the player's request (or if forceFinalize is true):
               - Set "status": "APPROVED"
               - Set "nextCallNeeded": "NONE"
               - Set "terminationCode": "$END_TOKEN"
               - Write a clear, friendly "finalUserMessage" in Portuguese summarizing everything that was done for the player, and append "$END_TOKEN" at the end.
            
            Respond ONLY with valid JSON:
            {
              "status": "APPROVED",
              "nextCallNeeded": "NONE",
              "followUpDescription": "",
              "terminationCode": "$END_TOKEN",
              "finalUserMessage": "Resumo amigável para o jogador explicando tudo que foi feito e revisado. $END_TOKEN"
            }
        """.trimIndent()

        val userPrompt = """
            ${blackboard.renderPaperForNextRobot(
                currentTaskInstruction = "Revisão Final (#$reviewRound, forceFinalize=$forceFinalize)",
                includeBlockGuideDraft = blackboard.executionNotes.isEmpty()
            )}
            
            [DISCONNECTED NODE WARNINGS]:
            ${if (disconnectedWarnings.isEmpty()) "None! All flow nodes have incoming connections." else disconnectedWarnings.joinToString("\n")}
            [CURRENT ACTIVE SCENE STATE (NODES & CONNECTIONS)]:
            $postStateSnapshot
        """.trimIndent()

        val raw = callLLM(userPrompt, systemPrompt)
        return parseJsonObjectSafe(raw) ?: JsonObject().apply {
            addProperty("status", "APPROVED")
            addProperty("nextCallNeeded", "NONE")
            addProperty("followUpDescription", "")
            addProperty("terminationCode", END_TOKEN)
            addProperty("finalUserMessage", "${raw.ifBlank { "Alterações aplicadas e revisadas!" }} $END_TOKEN")
        }
    }

    // =========================================================================
    // NODE FACTORY (MATCHING StoryEditorScreen.addNode)
    // =========================================================================
    private fun normalizeNodeParamsAfterEdit(node: NodeData, rawSubtype: String?) {
        if (node.nodeType == NodeType.TIMER) {
            val sec = node.params["durationSeconds"]?.takeIf { it.isNotBlank() }
                ?: node.params["durationSec"]?.takeIf { it.isNotBlank() }
                ?: node.params["seconds"]?.takeIf { it.isNotBlank() }
                ?: node.params["duration"]?.takeIf { it.isNotBlank() }
            if (!sec.isNullOrBlank()) {
                node.params["timerSeconds"] = sec
                node.params.remove("durationSeconds")
                node.params.remove("durationSec")
                node.params.remove("seconds")
                node.params.remove("duration")
            }
        }

        if (node.nodeType == NodeType.ACTION) {
            val rawUpper = rawSubtype?.uppercase()?.trim()
                ?: node.params["actionSubtype"]?.uppercase()?.trim()
                ?: node.params["actionId"]?.uppercase()?.trim()
                ?: ""

            val resolvedAct = ActionRegistry.find(rawUpper.ifBlank { "SEND_CHAT_MESSAGE" })
            node.params["actionSubtype"] = resolvedAct.id
            node.params["actionId"] = resolvedAct.id

            // If AI requested a removal alias (e.g., REMOVE_ENTITY_EFFECT), set durationSec="0" to clear effect
            if (rawUpper in setOf("REMOVE_ENTITY_EFFECT", "CLEAR_ENTITY_EFFECT", "REMOVE_EFFECT", "CLEAR_EFFECT")) {
                node.params["durationSec"] = "0"
            }

            // Normalize common parameter aliases so NodeInspectorWidget and ActionExecutor both see the right keys
            node.params["durationSeconds"]?.takeIf { it.isNotBlank() }?.let {
                node.params["durationSec"] = it
                node.params.remove("durationSeconds")
            }

            when (resolvedAct.id) {
                "ADD_ENTITY_EFFECT", "KILL_ENTITY", "MODIFY_ENTITY_PROPERTIES", "TELEPORT" -> {
                    val tag = node.params["targetStoryTag"]?.takeIf { it.isNotBlank() }
                        ?: node.params["storyTag"]?.takeIf { it.isNotBlank() }
                        ?: node.params["targetIdentifier"]?.takeIf { it.isNotBlank() }
                    if (!tag.isNullOrBlank()) {
                        node.params["targetStoryTag"] = tag
                        node.params["targetMode"] = "STORY_TAG"
                    }
                }
                "ANIMATION" -> {
                    val tag = node.params["targetIdentifier"]?.takeIf { it.isNotBlank() }
                        ?: node.params["storyTag"]?.takeIf { it.isNotBlank() }
                        ?: node.params["targetStoryTag"]?.takeIf { it.isNotBlank() }
                    if (!tag.isNullOrBlank()) {
                        node.params["targetIdentifier"] = tag
                    }
                }
            }
        }
    }

    private fun parseNodeTypeFlexible(raw: String): NodeType {
        return when (raw.uppercase().trim()) {
            "BEGIN_SCENE", "START_SCENE", "SCENE_START" -> NodeType.BEGIN_SCENE
            "END_SCENE", "FINISH_SCENE", "SCENE_END" -> NodeType.END_SCENE
            "DIALOGUE", "DIALOG", "SPEECH", "AI_DIALOGUE" -> NodeType.DIALOGUE
            "ACTION", "MOVE_TO_BLOCK", "TAG_BLOCK" -> NodeType.ACTION
            "TRIGGER" -> NodeType.TRIGGER
            "QUEST", "MISSION" -> NodeType.QUEST
            "TIMER", "WAIT", "DELAY" -> NodeType.TIMER
            "CONDITION_NODE", "CONDITION", "BRANCH", "IF" -> NodeType.CONDITION_NODE
            "VARIABLE_SET", "VAR_SET", "SET_VARIABLE" -> NodeType.VARIABLE_SET
            "VARIABLE_GET", "VAR_GET", "GET_VARIABLE" -> NodeType.VARIABLE_GET
            "COMMAND_NODE", "COMMAND", "COMMANDS" -> NodeType.COMMAND_NODE
            "AUDIO", "SOUND", "MUSIC" -> NodeType.AUDIO
            "LOOP", "REPEATER" -> NodeType.LOOP
            "GATE", "AND_GATE" -> NodeType.GATE
            "LINK_SEND" -> NodeType.LINK_SEND
            "LINK_RECEIVE" -> NodeType.LINK_RECEIVE
            "SAVE_STATE_NODE", "SAVE_STATE", "CHECKPOINT_SAVE" -> NodeType.SAVE_STATE_NODE
            "LOAD_STATE_NODE", "LOAD_STATE", "CHECKPOINT_LOAD" -> NodeType.LOAD_STATE_NODE
            "TEXTURE", "SET_TEXTURE" -> NodeType.TEXTURE
            "KEY_INPUT", "QTE" -> NodeType.KEY_INPUT
            "COMMENT", "NOTE" -> NodeType.COMMENT
            "CONSTRUCTION" -> NodeType.CONSTRUCTION
            else -> runCatching { NodeType.valueOf(raw.uppercase()) }.getOrDefault(NodeType.DIALOGUE)
        }
    }

    private fun createConfiguredNodeData(
        proj: StoryProject,
        scene: SceneData,
        type: NodeType,
        subtype: String?,
        x: Double,
        y: Double
    ): NodeData {
        val inputs = mutableListOf<PortData>()
        val outputs = mutableListOf<PortData>()
        var w = 160.0
        var h = 55.0

        when (type) {
            NodeType.QUEST -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Success", type = PortType.OUTPUT, id = "SUCCESS_OUT"))
                outputs.add(PortData(name = "Fail", type = PortType.OUTPUT, id = "FAIL_OUT"))
                outputs.add(PortData(name = "Progress", type = PortType.OUTPUT, id = "PROGRESS_OUT"))
                w = 130.0; h = 110.0
            }
            NodeType.AUDIO -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 130.0; h = 100.0
            }
            NodeType.TEXTURE -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 150.0; h = 80.0
            }
            NodeType.VARIABLE_GET -> {
                outputs.add(PortData(name = "Val", type = PortType.OUTPUT))
                w = 160.0; h = 55.0
            }
            NodeType.VARIABLE_SET -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 90.0; h = 90.0
            }
            NodeType.COMMENT -> {
                w = 90.0; h = 90.0
            }
            NodeType.BEGIN_SCENE, NodeType.BEGIN_CONSTRUCTION, NodeType.LINK_RECEIVE -> {
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 160.0; h = 55.0
            }
            NodeType.END_SCENE, NodeType.END_CONSTRUCTION, NodeType.LINK_SEND -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                w = 160.0; h = 55.0
            }
            NodeType.TRIGGER -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 130.0; h = 100.0
            }
            NodeType.CONDITION_NODE -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(id = "OUT_IF", name = "IF", type = PortType.OUTPUT))
                outputs.add(PortData(id = "OUT_ELSE", name = "ELSE", type = PortType.OUTPUT))
                w = 140.0; h = 100.0
            }
            NodeType.COMMAND_NODE -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 140.0; h = 100.0
            }
            NodeType.GATE -> {
                inputs.add(PortData(name = "IN 1", type = PortType.INPUT))
                inputs.add(PortData(name = "IN 2", type = PortType.INPUT))
                outputs.add(PortData(name = "OUT", type = PortType.OUTPUT))
                w = 160.0; h = 55.0
            }
            NodeType.LOOP -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                inputs.add(PortData(name = "Stop", type = PortType.INPUT))
                outputs.add(PortData(name = "Cycle", type = PortType.OUTPUT))
                outputs.add(PortData(name = "Done", type = PortType.OUTPUT))
                w = 90.0; h = 90.0
            }
            NodeType.SAVE_STATE_NODE -> {
                inputs.add(PortData(id = "IN", name = "In", type = PortType.INPUT))
                outputs.add(PortData(id = "OUT_SUCCESS", name = "Success", type = PortType.OUTPUT))
                outputs.add(PortData(id = "OUT_ERROR", name = "Error", type = PortType.OUTPUT))
                w = 140.0; h = 100.0
            }
            NodeType.LOAD_STATE_NODE -> {
                inputs.add(PortData(id = "IN", name = "In", type = PortType.INPUT))
                outputs.add(PortData(id = "OUT_SUCCESS", name = "Success", type = PortType.OUTPUT))
                outputs.add(PortData(id = "OUT_NOT_FOUND", name = "Not Found", type = PortType.OUTPUT))
                outputs.add(PortData(id = "OUT_ERROR", name = "Error", type = PortType.OUTPUT))
                w = 140.0; h = 110.0
            }
            NodeType.KEY_INPUT -> {
                outputs.add(PortData(id = "OUT", name = "Out", type = PortType.OUTPUT))
                w = 150.0; h = 100.0
            }
            else -> {
                inputs.add(PortData(name = "In", type = PortType.INPUT))
                outputs.add(PortData(name = "Out", type = PortType.OUTPUT))
                w = 130.0; h = 100.0
            }
        }

        val defaultTitle = when (type) {
            NodeType.BEGIN_SCENE -> "Scene Start"
            NodeType.END_SCENE -> "Finish Scene"
            NodeType.DIALOGUE -> "Dialogue"
            NodeType.ACTION -> ActionRegistry.getAction(subtype)?.name ?: "Action"
            NodeType.TRIGGER -> TriggerRegistry.find(subtype).name
            NodeType.QUEST -> "Mission (Quest)"
            NodeType.TIMER -> "Timer"
            NodeType.CONDITION_NODE -> "Condition"
            NodeType.VARIABLE_GET -> "Var (Get)"
            NodeType.VARIABLE_SET -> "Var (Set)"
            NodeType.COMMAND_NODE -> "Execute Commands"
            NodeType.AUDIO -> "Audio / Music"
            NodeType.LOOP -> "Loop Repeater"
            NodeType.GATE -> "GATE Synchronizer"
            NodeType.LINK_SEND -> "Link Send"
            NodeType.LINK_RECEIVE -> "Link Receiver"
            NodeType.SAVE_STATE_NODE -> "Save Checkpoint"
            NodeType.LOAD_STATE_NODE -> "Load Checkpoint"
            NodeType.TEXTURE -> "Change Texture"
            NodeType.KEY_INPUT -> "Key Input / QTE"
            NodeType.COMMENT -> "Note"
            else -> type.name
        }

        val node = NodeData(
            id = UUID.randomUUID().toString(),
            parentSceneId = scene.id,
            title = defaultTitle,
            nodeType = type,
            content = "",
            x = x,
            y = y,
            width = w,
            height = h,
            inputs = inputs,
            outputs = outputs,
            params = mutableMapOf()
        )

        // Populate default params per NodeType
        when (type) {
            NodeType.ACTION -> {
                val actDef = ActionRegistry.find(subtype ?: "SEND_CHAT_MESSAGE")
                node.params["actionSubtype"] = actDef.id
                node.params["actionId"] = actDef.id
                actDef.defaultParams.forEach { (k, v) -> node.params[k] = v }
            }
            NodeType.TRIGGER -> {
                val trigDef = TriggerRegistry.find(subtype ?: "STORY_STARTED")
                node.params["triggerType"] = trigDef.id
                node.params["requireInputSignal"] = "true"
                trigDef.defaultParams.forEach { (k, v) -> node.params[k] = v }
            }
            NodeType.QUEST -> {
                val trigDef = TriggerRegistry.find(subtype ?: "POKEMON_CATCH")
                node.params["questTitle"] = "New Quest"
                node.params["questTrigger"] = trigDef.id
                node.params["targetCount"] = "1"
                node.params["timeLimitSec"] = "0"
                node.params["failOnDeath"] = "false"
                node.params["showHud"] = "true"
                trigDef.defaultParams.forEach { (k, v) -> node.params[k] = v }
            }
            NodeType.TIMER -> {
                node.params["timerSeconds"] = "3"
            }
            NodeType.AUDIO -> {
                node.params["audioMode"] = "PLAY_SOUND_EFFECT"
                node.params["audioId"] = "minecraft:entity.player.levelup"
                node.params["audioVolume"] = "1.0"
                node.params["audioPitch"] = "1.0"
                node.params["audioLoop"] = "false"
                node.params["spatialMode"] = "GLOBAL_2D"
            }
            NodeType.VARIABLE_GET, NodeType.VARIABLE_SET -> {
                val firstVarKey = proj.variables.firstOrNull()?.id ?: "var_new"
                node.params["varKey"] = firstVarKey
                node.params["varOp"] = "="
                node.params["varValue"] = "1"
            }
            NodeType.CONDITION_NODE -> {
                val firstVarKey = proj.variables.firstOrNull()?.id ?: "var_new"
                node.params["varKey"] = firstVarKey
                node.params["varKey_0"] = firstVarKey
                node.params["varOp"] = "=="
                node.params["varOp_0"] = "=="
                node.params["varValue"] = "true"
                node.params["varValue_0"] = "true"
                node.params["elseIfCount"] = "0"
                node.params["hasElse"] = "true"
            }
            NodeType.COMMAND_NODE -> {
                node.params["commandSource"] = "SERVER"
                node.params["silent"] = "true"
                node.params["commands"] = "say Hello {player}!"
            }
            NodeType.LINK_SEND, NodeType.LINK_RECEIVE -> {
                node.params["channelTag"] = "channel_1"
            }
            NodeType.LOOP -> {
                node.params["loopMode"] = "COUNT"
                node.params["loopCount"] = "5"
                node.params["loopIntervalSec"] = "1.0"
            }
            NodeType.SAVE_STATE_NODE -> {
                node.params["profileId"] = "checkpoint_1"
                node.params["scope"] = "PLAYER"
                node.params["modules"] = "ALL"
            }
            NodeType.LOAD_STATE_NODE -> {
                node.params["profileId"] = "checkpoint_1"
                node.params["scope"] = "PLAYER"
                node.params["mergeMode"] = "OVERWRITE"
                node.params["gracePeriodTicks"] = "60"
            }
            NodeType.KEY_INPUT -> {
                node.params["triggerMode"] = "STANDALONE"
                node.params["requireInputSignal"] = "false"
                node.params["inputMode"] = "PRESS"
                node.params["targetKey"] = "F"
            }
            else -> {}
        }

        return node
    }

    private fun parseJsonObjectSafe(raw: String): JsonObject? {
        return try {
            val cleaned = raw.trim()
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start >= 0 && end > start) {
                JsonParser.parseString(cleaned.substring(start, end + 1)).asJsonObject
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
