package vito.cobblebrain.client.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.network.chat.Component
import vito.cobblebrain.model.ActionRegistry
import vito.cobblebrain.model.NodeData
import vito.cobblebrain.model.NodeType
import vito.cobblebrain.model.PortData
import vito.cobblebrain.model.PortType
import vito.cobblebrain.model.StoryVariable
import vito.cobblebrain.model.TriggerRegistry
import vito.cobblebrain.model.VariableType

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
    val projectVariables: List<StoryVariable> = emptyList()
) {
    private data class InspectorLabel(val text: String, val relY: Int, val color: Int = 0xFFA0A0A0.toInt())
    private data class InspectorWidgetItem(val widget: GuiEventListener, val relY: Int, val height: Int)

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

        // 1. Campo de Título do Nó
        labels.add(InspectorLabel("Título:", relY))
        relY += 12

        val tEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Título"))
        tEdit.setMaxLength(50)
        tEdit.value = node.title
        tEdit.setResponder { valText ->
            node.title = valText
            onDataChanged()
        }
        addWidgetItem(tEdit, relY, 16)
        relY += 22

        // 2. Interface Contextual Dinâmica para os Tipos
        when (node.nodeType) {
            NodeType.ACTION -> {
                val currentActionId = node.params["actionSubtype"] ?: "MESSAGE"
                val actionDef = ActionRegistry.find(currentActionId)

                // Card do Tipo Ativo & Botão Alterar Tipo
                labels.add(InspectorLabel("Ação Atual:", relY))
                relY += 12

                val changeBtn = Button.builder(Component.literal("🔄 ${actionDef.icon} ${actionDef.name}")) {
                    onOpenActionTriggerPicker?.invoke(true) { chosenId ->
                        node.params["actionSubtype"] = chosenId
                        val newDef = ActionRegistry.find(chosenId)
                        if (node.title.isBlank() || node.title == "Nova Ação" || ActionRegistry.actions.any { node.title.contains(it.name) }) {
                            node.title = newDef.name
                        }
                        buildUi()
                        onDataChanged()
                    }
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(changeBtn, relY, 16)
                relY += 22

                // Inputs Estritamente Contextuais para a Ação Ativa
                when (actionDef.id) {
                    "SPAWN_STRUCTURE" -> {
                        labels.add(InspectorLabel("ID da Estrutura:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Estrutura"))
                        f1.value = node.params["structureId"] ?: "minecraft:small_house"
                        f1.setResponder { node.params["structureId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Posição X, Y, Z:", relY))
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
                        labels.add(InspectorLabel("Destino X:", relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "X", node.params["destX"] ?: "0") { node.params["destX"] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Destino Y:", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Y", node.params["destY"] ?: "64") { node.params["destY"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Destino Z:", relY))
                        relY += 12
                        val f3 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Z", node.params["destZ"] ?: "0") { node.params["destZ"] = it }
                        addWidgetItem(f3, relY, 16)
                        relY += 22
                    }

                    "CHANGE_WEATHER" -> {
                        val currentWeather = (node.params["weatherType"] ?: "CLEAR").uppercase()
                        val btnW = (inputW - 4) / 3
                        labels.add(InspectorLabel("Tipo de Clima:", relY))
                        relY += 12

                        val bClear = Button.builder(Component.literal("Limpo")) { node.params["weatherType"] = "CLEAR"; buildUi(); onDataChanged() }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (currentWeather == "CLEAR") bClear.active = false
                        val bRain = Button.builder(Component.literal("Chuva")) { node.params["weatherType"] = "RAIN"; buildUi(); onDataChanged() }.bounds(inputX + btnW + 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (currentWeather == "RAIN") bRain.active = false
                        val bThunder = Button.builder(Component.literal("Raio")) { node.params["weatherType"] = "THUNDER"; buildUi(); onDataChanged() }.bounds(inputX + (btnW + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                        if (currentWeather == "THUNDER") bThunder.active = false

                        addWidgetItem(bClear, relY, 14); addWidgetItem(bRain, relY, 14); addWidgetItem(bThunder, relY, 14)
                        relY += 20

                        labels.add(InspectorLabel("Duração (Ticks):", relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Ticks", node.params["durationTicks"] ?: "6000") { node.params["durationTicks"] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "SET_TIME_OF_DAY" -> {
                        labels.add(InspectorLabel("Horário (Ticks):", relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Ticks", node.params["timeTicks"] ?: "1000") { node.params["timeTicks"] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        val bW = (inputW - 4) / 3
                        val bDay = Button.builder(Component.literal("Dia")) { node.params["timeTicks"] = "1000"; buildUi(); onDataChanged() }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        val bNoon = Button.builder(Component.literal("Meio-Dia")) { node.params["timeTicks"] = "6000"; buildUi(); onDataChanged() }.bounds(inputX + bW + 2, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        val bNight = Button.builder(Component.literal("Noite")) { node.params["timeTicks"] = "13000"; buildUi(); onDataChanged() }.bounds(inputX + (bW + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        addWidgetItem(bDay, relY, 14); addWidgetItem(bNoon, relY, 14); addWidgetItem(bNight, relY, 14)
                        relY += 20
                    }

                    "SPAWN_BLOCK" -> {
                        labels.add(InspectorLabel("ID do Bloco:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Bloco"))
                        f1.value = node.params["blockId"] ?: "minecraft:stone"
                        f1.setResponder { node.params["blockId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Posição X, Y, Z:", relY))
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

                    "MODIFY_BLOCK_PROPERTY" -> {
                        labels.add(InspectorLabel("Chave da Propriedade:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Prop"))
                        f1.value = node.params["propertyKey"] ?: "open"
                        f1.setResponder { node.params["propertyKey"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Valor da Propriedade:", relY))
                        relY += 12
                        val f2 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Val"))
                        f2.value = node.params["propertyValue"] ?: "true"
                        f2.setResponder { node.params["propertyValue"] = it; onDataChanged() }
                        addWidgetItem(f2, relY, 16)
                        relY += 22
                    }

                    "SPAWN_ENTITY" -> {
                        labels.add(InspectorLabel("ID da Entidade:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Entidade"))
                        f1.value = node.params["entityId"] ?: "minecraft:villager"
                        f1.setResponder { node.params["entityId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Nome Customizado (Opcional):", relY))
                        relY += 12
                        val f2 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Nome"))
                        f2.value = node.params["customName"] ?: ""
                        f2.setResponder { node.params["customName"] = it; onDataChanged() }
                        addWidgetItem(f2, relY, 16)
                        relY += 22
                    }

                    "KILL_ENTITY" -> {
                        labels.add(InspectorLabel("Seletor de Alvo:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Seletor"))
                        f1.value = node.params["entitySelector"] ?: "@e[type=zombie,distance=..10]"
                        f1.setResponder { node.params["entitySelector"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "MODIFY_ENTITY_PROPERTIES" -> {
                        labels.add(InspectorLabel("Nome da Entidade:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Nome"))
                        f1.value = node.params["customName"] ?: ""
                        f1.setResponder { node.params["customName"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        val noAi = node.params["noAi"] == "true"
                        val noAiText = if (noAi) "🧠 Sem IA (NoAI): SIM" else "🧠 Sem IA (NoAI): NÃO"
                        val bAi = Button.builder(Component.literal(noAiText)) {
                            node.params["noAi"] = (!noAi).toString()
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(bAi, relY, 16)
                        relY += 22
                    }

                    "ADD_ENTITY_EFFECT", "ADD_AREA_EFFECT" -> {
                        labels.add(InspectorLabel("ID do Efeito:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Efeito"))
                        f1.value = node.params["effectId"] ?: "minecraft:glowing"
                        f1.setResponder { node.params["effectId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        if (actionDef.id == "ADD_AREA_EFFECT") {
                            labels.add(InspectorLabel("Raio (Blocos):", relY))
                            relY += 12
                            val fr = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Raio", node.params["radius"] ?: "8") { node.params["radius"] = it }
                            addWidgetItem(fr, relY, 16)
                            relY += 22
                        }

                        labels.add(InspectorLabel("Duração (Segundos):", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Seg", node.params["durationSec"] ?: "10") { node.params["durationSec"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22
                    }

                    "SPAWN_COBBLEMON", "SPAWN_POKEMON", "SPAWN", "GIVE_POKEMON" -> {
                        labels.add(InspectorLabel("Espécie Pokémon:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Espécie"))
                        f1.value = node.params["species"] ?: "Pikachu"
                        f1.setResponder { node.params["species"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Nível:", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Nível", node.params["level"] ?: "5") { node.params["level"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22

                        val isShiny = node.params["shiny"] == "true"
                        val shinyText = if (isShiny) "✨ Shiny: SIM" else "⚪ Shiny: NÃO"
                        labels.add(InspectorLabel(shinyText, relY, if (isShiny) 0xFFFFD700.toInt() else 0xFFA0A0A0.toInt()))
                        relY += 14

                        val cfgBtn = Button.builder(Component.literal("⚙️ Configurar Detalhes...")) {
                            onOpenPokemonConfig?.invoke(node)
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(cfgBtn, relY, 16)
                        relY += 22
                    }

                    "MODIFY_POKEMON_PROPERTIES" -> {
                        val healHp = node.params["healHp"] != "false"
                        val healText = if (healHp) "❤️ Curar HP: SIM" else "❤️ Curar HP: NÃO"
                        val bHeal = Button.builder(Component.literal(healText)) {
                            node.params["healHp"] = (!healHp).toString()
                            buildUi()
                            onDataChanged()
                        }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                        addWidgetItem(bHeal, relY, 16)
                        relY += 22
                    }

                    "CHANGE_POKEMON_PERSONALITY" -> {
                        labels.add(InspectorLabel("Preset de Personalidade:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Preset"))
                        f1.value = node.params["personalityPreset"] ?: "Heroic"
                        f1.setResponder { node.params["personalityPreset"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "ADD_POKEMON_PARTY_EFFECT" -> {
                        labels.add(InspectorLabel("✨ Cura completa e restauração da equipe.", relY, 0xFF00FFCC.toInt()))
                        relY += 20
                    }

                    "KILL_PLAYER" -> {
                        labels.add(InspectorLabel("💀 Elimina o jogador imediatamente.", relY, 0xFFFF4444.toInt()))
                        relY += 20
                    }

                    "DAMAGE_PLAYER" -> {
                        labels.add(InspectorLabel("Pontos de Dano:", relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Dano", node.params["damageAmount"] ?: "4.0") { node.params["damageAmount"] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "GIVE_ITEM", "REMOVE_ITEM", "SPAWN_ITEM" -> {
                        labels.add(InspectorLabel("ID do Item:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Item ID"))
                        f1.value = node.params["itemId"] ?: "cobblemon:poke_ball"
                        f1.setResponder { node.params["itemId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Quantidade:", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Qtd", node.params["amount"] ?: "1") { node.params["amount"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22
                    }

                    "ADD_PLAYER_EFFECT", "EFFECT" -> {
                        labels.add(InspectorLabel("ID do Efeito:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Efeito"))
                        f1.value = node.params["effectId"] ?: "minecraft:speed"
                        f1.setResponder { node.params["effectId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Duração (Segundos):", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Seg", node.params["durationSec"] ?: "10") { node.params["durationSec"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Amplificador (Nível):", relY))
                        relY += 12
                        val f3 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Amp", node.params["amplifier"] ?: "1") { node.params["amplifier"] = it }
                        addWidgetItem(f3, relY, 16)
                        relY += 22
                    }

                    "JUMP_TO_STORY_POINT", "REWIND_TO_STORY_POINT" -> {
                        labels.add(InspectorLabel("ID da Cena Alvo:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Cena ID"))
                        f1.value = node.params["targetSceneId"] ?: ""
                        f1.setResponder { node.params["targetSceneId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "SEND_CHAT_MESSAGE", "MESSAGE" -> {
                        labels.add(InspectorLabel("Texto da Mensagem:", relY))
                        relY += 12

                        val cEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 28, Component.literal("Mensagem"))
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

                        labels.add(InspectorLabel("Modo Exibição:", relY))
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
                        labels.add(InspectorLabel("Título Principal:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Título"))
                        f1.value = node.params["mainTitle"] ?: "Missão Concluída!"
                        f1.setResponder { node.params["mainTitle"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Subtítulo:", relY))
                        relY += 12
                        val f2 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Subtítulo"))
                        f2.value = node.params["subTitle"] ?: "Parabéns!"
                        f2.setResponder { node.params["subTitle"] = it; onDataChanged() }
                        addWidgetItem(f2, relY, 16)
                        relY += 22
                    }

                    "CHANGE_SCREEN_TINT" -> {
                        labels.add(InspectorLabel("Cor Hex (ex: #FF0000):", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Cor"))
                        f1.value = node.params["tintColor"] ?: "#FF0000"
                        f1.setResponder { node.params["tintColor"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "SPAWN_PARTICLES" -> {
                        labels.add(InspectorLabel("ID da Partícula:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Partícula"))
                        f1.value = node.params["particleId"] ?: "minecraft:totem_of_undying"
                        f1.setResponder { node.params["particleId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Quantidade:", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Qtd", node.params["count"] ?: "20") { node.params["count"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22
                    }

                    "PLAY_SOUND", "SOUND" -> {
                        labels.add(InspectorLabel("ID do Som:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Som"))
                        f1.value = node.params["soundId"] ?: "minecraft:entity.player.levelup"
                        f1.setResponder { node.params["soundId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
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
                        labels.add(InspectorLabel("ID da Trilha Sonora:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Música"))
                        f1.value = node.params["musicId"] ?: "minecraft:music.game"
                        f1.setResponder { node.params["musicId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "VAR_MODIFY" -> {
                        val currentVarKey = node.params["varKey"] ?: projectVariables.firstOrNull()?.id ?: "var_nova"

                        labels.add(InspectorLabel("Variável:", relY))
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
                        labels.add(InspectorLabel("Operação:", relY))
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
                            labels.add(InspectorLabel("Valor Target:", relY))
                            relY += 12

                            val valEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Valor"))
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

                // Card do Tipo Ativo & Botão Alterar Tipo
                labels.add(InspectorLabel("Gatilho Atual:", relY))
                relY += 12

                val changeBtn = Button.builder(Component.literal("🔄 ${trigDef.icon} ${trigDef.name}")) {
                    onOpenActionTriggerPicker?.invoke(false) { chosenId ->
                        node.params["triggerType"] = chosenId
                        val newDef = TriggerRegistry.find(chosenId)
                        if (node.title.isBlank() || node.title == "Novo Gatilho" || TriggerRegistry.triggers.any { node.title.contains(it.name) }) {
                            node.title = newDef.name
                        }
                        buildUi()
                        onDataChanged()
                    }
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(changeBtn, relY, 16)
                relY += 22

                // Controle de Entrada IN
                val requireInput = node.params["requireInputSignal"] != "false"
                val inBtnLabel = if (requireInput) "📥 Sinal IN: SIM" else "📥 Sinal IN: NÃO"

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

                // Modo Lógico IF / IF NOT
                val currentCondMode = node.params["triggerCondition"] ?: "IF"
                val btnW = (inputW - 2) / 2

                labels.add(InspectorLabel("Condição Lógica:", relY))
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

                // Inputs Estritamente Contextuais para o Gatilho Ativo
                when (trigDef.id) {
                    "STORY_STARTED", "START" -> {
                        labels.add(InspectorLabel("🟢 Inicia no fluxo da cena.", relY, 0xFF00FFCC.toInt()))
                        relY += 16
                    }

                    "STORY_ENDED" -> {
                        labels.add(InspectorLabel("🛑 Dispara ao finalizar a história.", relY, 0xFFFF4444.toInt()))
                        relY += 16
                    }

                    "PREVIOUS_MISSION_COMPLETED" -> {
                        labels.add(InspectorLabel("ID da Missão:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Missão ID"))
                        f1.value = node.params["missionId"] ?: "missao_1"
                        f1.setResponder { node.params["missionId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "PREVIOUS_EVENT_EXECUTED" -> {
                        labels.add(InspectorLabel("Tag do Evento:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Evento"))
                        f1.value = node.params["eventTag"] ?: "evento_chave"
                        f1.setResponder { node.params["eventTag"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "TIME_ELAPSED" -> {
                        labels.add(InspectorLabel("Tempo (Segundos):", relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Segundos", node.params["timeSeconds"] ?: "10") { node.params["timeSeconds"] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "TIME_OF_DAY" -> {
                        labels.add(InspectorLabel("Horário do Dia (Ticks):", relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Ticks", node.params["timeOfDayTicks"] ?: "6000") { node.params["timeOfDayTicks"] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "DAYS_PASSED" -> {
                        labels.add(InspectorLabel("Dias no Jogo:", relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Dias", node.params["daysCount"] ?: "1") { node.params["daysCount"] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "DAY_NIGHT_CHECK" -> {
                        val currentPeriod = node.params["timePeriod"] ?: "DAY"
                        val bW = (inputW - 2) / 2
                        labels.add(InspectorLabel("Período Requerido:", relY))
                        relY += 12
                        val bDay = Button.builder(Component.literal("☀️ Dia")) { node.params["timePeriod"] = "DAY"; buildUi(); onDataChanged() }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        if (currentPeriod == "DAY") bDay.active = false
                        val bNight = Button.builder(Component.literal("🌙 Noite")) { node.params["timePeriod"] = "NIGHT"; buildUi(); onDataChanged() }.bounds(inputX + bW + 2, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        if (currentPeriod == "NIGHT") bNight.active = false
                        addWidgetItem(bDay, relY, 14); addWidgetItem(bNight, relY, 14)
                        relY += 20
                    }

                    "PLAYER_LEVEL", "HIGHEST_POKEMON_LEVEL", "PLAYER_ITEM_COUNT", "KARMA_CHECK" -> {
                        val valLabel = when (trigDef.id) {
                            "PLAYER_LEVEL" -> "Nível de EXP:"
                            "HIGHEST_POKEMON_LEVEL" -> "Nível do Pokémon:"
                            "PLAYER_ITEM_COUNT" -> "Qtd Mínima:"
                            else -> "Karma Alvo:"
                        }
                        val keyName = when (trigDef.id) {
                            "PLAYER_LEVEL" -> "minLevel"
                            "HIGHEST_POKEMON_LEVEL" -> "targetLevel"
                            "PLAYER_ITEM_COUNT" -> "minCount"
                            else -> "targetKarma"
                        }
                        labels.add(InspectorLabel(valLabel, relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Valor", node.params[keyName] ?: "10") { node.params[keyName] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        if (trigDef.id == "PLAYER_ITEM_COUNT") {
                            labels.add(InspectorLabel("ID do Item:", relY))
                            relY += 12
                            val fi = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Item ID"))
                            fi.value = node.params["checkItemId"] ?: "cobblemon:poke_ball"
                            fi.setResponder { node.params["checkItemId"] = it; onDataChanged() }
                            addWidgetItem(fi, relY, 16)
                            relY += 22
                        }
                    }

                    "PLAYER_COORDINATES", "LOCATION" -> {
                        labels.add(InspectorLabel("Target X:", relY))
                        relY += 12
                        val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "X", node.params["targetX"] ?: "0") { node.params["targetX"] = it }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Target Y:", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Y", node.params["targetY"] ?: "64") { node.params["targetY"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Target Z:", relY))
                        relY += 12
                        val f3 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Z", node.params["targetZ"] ?: "0") { node.params["targetZ"] = it }
                        addWidgetItem(f3, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Raio (Blocos):", relY))
                        relY += 12
                        val f4 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Raio", node.params["radius"] ?: "5") { node.params["radius"] = it }
                        addWidgetItem(f4, relY, 16)
                        relY += 22
                    }

                    "PLAYER_BIOME" -> {
                        labels.add(InspectorLabel("ID do Bioma:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Bioma"))
                        f1.value = node.params["biomeId"] ?: "minecraft:plains"
                        f1.setResponder { node.params["biomeId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "PLAYER_HELD_ITEM", "PLAYER_INVENTORY_ITEM_REMOVED" -> {
                        labels.add(InspectorLabel("ID do Item:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Item ID"))
                        f1.value = node.params["heldItemId"] ?: "minecraft:diamond_sword"
                        f1.setResponder { node.params["heldItemId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "PLAYER_INVENTORY_HAS_ITEM", "ITEM_IN_INVENTORY" -> {
                        labels.add(InspectorLabel("ID do Item Requerido:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Item ID"))
                        f1.value = node.params["requiredItem"] ?: "cobblemon:potion"
                        f1.setResponder { node.params["requiredItem"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Quantidade Necessária:", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Qtd", node.params["requiredCount"] ?: "1") { node.params["requiredCount"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22
                    }

                    "TALK_TO_POKEMON", "INTERACT_POKEMON", "POKEMON_CATCH", "CATCH_POKEMON", "SPECIFIC_POKEMON_IN_PARTY", "BATTLE_VICTORY", "DEFEAT_POKEMON" -> {
                        labels.add(InspectorLabel("Espécie Pokémon:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Espécie"))
                        f1.value = node.params["targetSpecies"] ?: "Pikachu"
                        f1.setResponder { node.params["targetSpecies"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "POKEMON_FRIENDSHIP" -> {
                        labels.add(InspectorLabel("Espécie Pokémon:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Espécie"))
                        f1.value = node.params["targetSpecies"] ?: "Pikachu"
                        f1.setResponder { node.params["targetSpecies"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22

                        labels.add(InspectorLabel("Amizade Mínima (0-255):", relY))
                        relY += 12
                        val f2 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Amizade", node.params["minFriendship"] ?: "220") { node.params["minFriendship"] = it }
                        addWidgetItem(f2, relY, 16)
                        relY += 22
                    }

                    "BATTLE_START" -> {
                        labels.add(InspectorLabel("⚔️ Dispara no início de qualquer batalha.", relY, 0xFF00FFCC.toInt()))
                        relY += 16
                    }

                    "BATTLE_DEFEAT" -> {
                        labels.add(InspectorLabel("💀 Dispara ao ser derrotado em batalha.", relY, 0xFFFF4444.toInt()))
                        relY += 16
                    }

                    "ENTITY_DIED", "ENTITY_DAMAGED", "ENTITY_SPAWNED" -> {
                        labels.add(InspectorLabel("Tipo da Entidade:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Entidade"))
                        f1.value = node.params["entityType"] ?: "minecraft:zombie"
                        f1.setResponder { node.params["entityType"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "WEATHER_CHECK" -> {
                        val currentTarget = (node.params["weatherType"] ?: "RAIN").uppercase()
                        val bW = (inputW - 4) / 3
                        labels.add(InspectorLabel("Clima Requerido:", relY))
                        relY += 12
                        val bClear = Button.builder(Component.literal("Limpo")) { node.params["weatherType"] = "CLEAR"; buildUi(); onDataChanged() }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        if (currentTarget == "CLEAR") bClear.active = false
                        val bRain = Button.builder(Component.literal("Chuva")) { node.params["weatherType"] = "RAIN"; buildUi(); onDataChanged() }.bounds(inputX + bW + 2, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        if (currentTarget == "RAIN") bRain.active = false
                        val bThunder = Button.builder(Component.literal("Raio")) { node.params["weatherType"] = "THUNDER"; buildUi(); onDataChanged() }.bounds(inputX + (bW + 2) * 2, (panelY + 20 + relY - scrollOffset).toInt(), bW, 14).build()
                        if (currentTarget == "THUNDER") bThunder.active = false
                        addWidgetItem(bClear, relY, 14); addWidgetItem(bRain, relY, 14); addWidgetItem(bThunder, relY, 14)
                        relY += 20
                    }

                    "BLOCK_INTERACTED", "BLOCK_PLACED" -> {
                        labels.add(InspectorLabel("ID do Bloco:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Bloco"))
                        f1.value = node.params["blockId"] ?: "minecraft:chest"
                        f1.setResponder { node.params["blockId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "ENTER_STRUCTURE_OR_ZONE" -> {
                        labels.add(InspectorLabel("ID da Estrutura:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Estrutura"))
                        f1.value = node.params["structureId"] ?: "minecraft:village_plains"
                        f1.setResponder { node.params["structureId"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }

                    "AI_EVALUATION" -> {
                        labels.add(InspectorLabel("Intenção Esperada da IA:", relY))
                        relY += 12
                        val f1 = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Intenção"))
                        f1.value = node.params["aiIntent"] ?: "AGREE"
                        f1.setResponder { node.params["aiIntent"] = it; onDataChanged() }
                        addWidgetItem(f1, relY, 16)
                        relY += 22
                    }
                }
            }

            NodeType.VARIABLE_GET -> {
                val currentVarKey = node.params["varKey"] ?: projectVariables.firstOrNull()?.id ?: "var_nova"
                val selectedVar = projectVariables.find { it.id == currentVarKey }

                labels.add(InspectorLabel("Variável (Get):", relY))
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

                if (selectedVar?.type == VariableType.LIST) {
                    val listOps = listOf("CONTAINS", "SIZE", "IS_EMPTY", "GET_INDEX")
                    val currentOp = node.params["varOp"] ?: "SIZE"
                    val validOp = if (listOps.contains(currentOp)) currentOp else listOps.first()
                    node.params["varOp"] = validOp

                    labels.add(InspectorLabel("Consulta Lista:", relY))
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
                            "CONTAINS" -> "Item a Buscar:"
                            "SIZE" -> "Tamanho Esperado:"
                            "GET_INDEX" -> "Índice (0, 1, 2...):"
                            else -> "Parâmetro:"
                        }
                        labels.add(InspectorLabel(labelText, relY))
                        relY += 12

                        val valEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Valor"))
                        valEdit.value = node.params["varValue"] ?: "0"
                        valEdit.setResponder { text -> node.params["varValue"] = text; onDataChanged() }
                        addWidgetItem(valEdit, relY, 16)
                        relY += 22
                    }
                } else {
                    val typeStr = selectedVar?.type?.name ?: "STRING"
                    val scopeStr = selectedVar?.scope?.name ?: "GLOBAL"
                    labels.add(InspectorLabel("Tipo: $typeStr", relY))
                    relY += 12
                    labels.add(InspectorLabel("Escopo: $scopeStr", relY))
                    relY += 16
                }
            }

            NodeType.VARIABLE_SET -> {
                val currentVarKey = node.params["varKey"] ?: projectVariables.firstOrNull()?.id ?: "var_nova"
                val selectedVar = projectVariables.find { it.id == currentVarKey }
                val varType = selectedVar?.type ?: VariableType.STRING

                labels.add(InspectorLabel("Variável (Set):", relY))
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

                labels.add(InspectorLabel("Operação:", relY))
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
                        "ADD" -> "Item a Adicionar:"
                        "REMOVE" -> "Item a Remover:"
                        "REMOVE_AT" -> "Índice Numérico:"
                        "SET" -> "Lista (item1, item2):"
                        else -> "Valor Target:"
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
                labels.add(InspectorLabel("Nota / Comentário:", relY))
                relY += 12

                val cEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 50, Component.literal("Nota"))
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
                labels.add(InspectorLabel("Tag do Canal:", relY))
                relY += 12

                val tagEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Tag Canal"))
                tagEdit.setMaxLength(50)
                tagEdit.value = node.params["channelTag"] ?: "canal_1"
                tagEdit.setResponder { valText ->
                    node.params["channelTag"] = valText
                    onDataChanged()
                }
                addWidgetItem(tagEdit, relY, 16)
                relY += 22

                val isSend = node.nodeType == NodeType.LINK_SEND
                val infoText = if (isSend) "Transmite sinal sem fio." else "Recebe sinal do emissor."
                labels.add(InspectorLabel(infoText, relY))
                relY += 16
            }

            NodeType.LOOP -> {
                val currentMode = node.params["loopMode"] ?: "COUNT"

                labels.add(InspectorLabel("Modo Operação:", relY))
                relY += 12

                val btnW = (inputW - 2) / 2

                val countBtn = Button.builder(Component.literal("Contagem")) {
                    node.params["loopMode"] = "COUNT"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                if (currentMode == "COUNT") countBtn.active = false

                val timeBtn = Button.builder(Component.literal("Tempo")) {
                    node.params["loopMode"] = "TIME"
                    buildUi()
                    onDataChanged()
                }.bounds(inputX + btnW + 2, (panelY + 20 + relY - scrollOffset).toInt(), btnW, 14).build()
                if (currentMode == "TIME") timeBtn.active = false

                addWidgetItem(countBtn, relY, 14)
                addWidgetItem(timeBtn, relY, 14)
                relY += 20

                if (currentMode == "COUNT") {
                    labels.add(InspectorLabel("Repetições (Qtd):", relY))
                    relY += 12

                    val countEdit = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Qtd", node.params["loopCount"] ?: "5") { valText ->
                        node.params["loopCount"] = valText
                    }
                    addWidgetItem(countEdit, relY, 16)
                    relY += 22
                }

                labels.add(InspectorLabel("Intervalo (Seg):", relY))
                relY += 12

                val intervalEdit = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Seg", node.params["loopIntervalSec"] ?: "1.0") { valText ->
                    node.params["loopIntervalSec"] = valText
                }
                addWidgetItem(intervalEdit, relY, 16)
                relY += 22
            }

            NodeType.BEGIN_SCENE -> {
                labels.add(InspectorLabel("Ponto de Entrada da Cena.", relY))
                relY += 12
                labels.add(InspectorLabel("Dispara a saída OUT.", relY))
                relY += 16
            }

            NodeType.END_SCENE -> {
                labels.add(InspectorLabel("Finaliza a Cena atual.", relY))
                relY += 12
                labels.add(InspectorLabel("Dispara a saída OUT.", relY))
                relY += 16
            }

            NodeType.GATE -> {
                val currentCount = node.inputs.size.coerceAtLeast(2)

                labels.add(InspectorLabel("Sincronizador GATE:", relY))
                relY += 12

                labels.add(InspectorLabel("Entradas (2-5):", relY))
                relY += 12

                val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Qtd Entradas", currentCount.toString()) { valText ->
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
                labels.add(InspectorLabel("Sub-Grafo Interno:", relY))
                relY += 12

                val openBtn = Button.builder(Component.literal("🔍 Editar Interno")) {
                    onOpenConstruction?.invoke(node)
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 18).build()
                addWidgetItem(openBtn, relY, 18)
                relY += 24

                labels.add(InspectorLabel("Mini-Mapa Interno:", relY))
                relY += 14
                relY += 70
            }

            NodeType.BRANCH -> {
                val currentVarKey = node.params["varKey"] ?: projectVariables.firstOrNull()?.id ?: "var_nova"
                val selectedVar = projectVariables.find { it.id == currentVarKey }
                val varType = selectedVar?.type ?: VariableType.STRING

                labels.add(InspectorLabel("Variável:", relY))
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

                val ops = if (varType == VariableType.LIST) {
                    listOf("CONTAINS", "SIZE", "IS_EMPTY", "GET_INDEX")
                } else {
                    listOf("==", "!=", ">", "<", ">=", "<=")
                }

                val currentOp = node.params["varOp"] ?: ops.first()
                val validOp = if (ops.contains(currentOp)) currentOp else ops.first()
                node.params["varOp"] = validOp

                labels.add(InspectorLabel("Condição:", relY))
                relY += 12

                val opBtn = Button.builder(Component.literal("Op: $validOp")) {
                    val nextOp = ops[(ops.indexOf(validOp) + 1) % ops.size]
                    node.params["varOp"] = nextOp
                    buildUi()
                    onDataChanged()
                }.bounds(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16).build()
                addWidgetItem(opBtn, relY, 16)
                relY += 22

                if (validOp != "IS_EMPTY") {
                    val labelText = when (validOp) {
                        "CONTAINS" -> "Item Esperado:"
                        "SIZE" -> "Tamanho Esperado:"
                        "GET_INDEX" -> "Índice:Valor (ex: 0:item):"
                        else -> "Valor Alvo:"
                    }
                    labels.add(InspectorLabel(labelText, relY))
                    relY += 12

                    val valEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal(labelText))
                    valEdit.value = node.params["varValue"] ?: if (validOp == "SIZE") "0" else "item"
                    valEdit.setResponder { node.params["varValue"] = it; onDataChanged() }
                    addWidgetItem(valEdit, relY, 16)
                    relY += 22
                }

                val currentCount = node.outputs.size.coerceAtLeast(2)

                labels.add(InspectorLabel("Qtd de Saídas:", relY))
                relY += 12

                val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Qtd Saídas", currentCount.toString()) { valText ->
                    val num = valText.toIntOrNull()?.coerceIn(2, 5) ?: 2
                    if (num != node.outputs.size) {
                        while (node.outputs.size < num) {
                            node.outputs.add(PortData(name = "IF (Saída ${node.outputs.size + 1})", type = PortType.OUTPUT))
                        }
                        while (node.outputs.size > num) {
                            node.outputs.removeAt(node.outputs.size - 1)
                        }
                        buildUi()
                        onDataChanged()
                    }
                }
                addWidgetItem(f1, relY, 16)
                relY += 22

                node.outputs.forEachIndexed { idx, port ->
                    labels.add(InspectorLabel("Rótulo Saída #${idx + 1}:", relY))
                    relY += 12

                    val condEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 16, Component.literal("Saída #${idx + 1}"))
                    condEdit.value = port.name
                    condEdit.setResponder { valText ->
                        port.name = valText
                        onDataChanged()
                    }
                    addWidgetItem(condEdit, relY, 16)
                    relY += 22
                }
            }

            NodeType.DIALOGUE -> {
                labels.add(InspectorLabel("Fala / Diálogo:", relY))
                relY += 12

                val cEdit = EditBox(font, inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, 28, Component.literal("Fala"))
                cEdit.setMaxLength(300)
                cEdit.value = node.content
                cEdit.setResponder { valText ->
                    node.content = valText
                    onDataChanged()
                }
                addWidgetItem(cEdit, relY, 28)
                relY += 34

                val currentMsgType = node.params["messageType"] ?: "CHAT"
                val btnW = (inputW - 4) / 3

                labels.add(InspectorLabel("Modo Exibição:", relY))
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

            NodeType.TIMER -> {
                labels.add(InspectorLabel("Espera (Segundos):", relY))
                relY += 12

                val f1 = createNumEdit(inputX, (panelY + 20 + relY - scrollOffset).toInt(), inputW, "Segundos", node.params["timerSeconds"] ?: "5") { node.params["timerSeconds"] = it }
                addWidgetItem(f1, relY, 16)
                relY += 22
            }
        }

        totalContentHeight = relY.toDouble()
        updateWidgetPositions()
    }

    private fun addWidgetItem(widget: GuiEventListener, relY: Int, height: Int) {
        childrenWidgets.add(widget)
        widgetItems.add(InspectorWidgetItem(widget, relY, height))
    }

    private fun updateWidgetPositions() {
        val inputX = panelX + 6
        widgetItems.forEach { item ->
            val py = (panelY + 20 + item.relY - scrollOffset).toInt()
            if (item.widget is Button) {
                item.widget.y = py
            } else if (item.widget is EditBox) {
                item.widget.x = inputX
                item.widget.y = py
            }
        }
    }

    private fun createNumEdit(x: Int, y: Int, w: Int, label: String, initialVal: String, onUpdate: (String) -> Unit): EditBox {
        val eb = EditBox(font, x, y, w, 16, Component.literal(label))
        eb.value = initialVal
        eb.setFilter { text -> text.isEmpty() || text.all { it.isDigit() || it == '-' || it == '.' } }
        eb.setResponder { valText ->
            onUpdate(valText)
            onDataChanged()
        }
        return eb
    }

    private fun renderMiniMap(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        guiGraphics.fill(x, y, x + w, y + h, 0xFF0D0D12.toInt())
        guiGraphics.fill(x - 1, y - 1, x + w + 1, y, 0xFF3D5AFE.toInt())
        guiGraphics.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF3D5AFE.toInt())
        guiGraphics.fill(x - 1, y, x, y + h, 0xFF3D5AFE.toInt())
        guiGraphics.fill(x + w, y, x + w + 1, y + h, 0xFF3D5AFE.toInt())

        val inner = node.innerNodes
        if (inner.isEmpty()) {
            guiGraphics.drawString(font, "Vazio", x + w / 2 - 12, y + h / 2 - 4, 0xFF555566.toInt(), false)
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
                NodeType.BRANCH -> 0xFFF57F17.toInt()
                NodeType.DIALOGUE -> 0xFF1565C0.toInt()
                NodeType.END_SCENE -> 0xFFD32F2F.toInt()
                NodeType.GATE -> 0xFF00B0FF.toInt()
                NodeType.LINK_SEND -> 0xFF00E676.toInt()
                NodeType.LINK_RECEIVE -> 0xFF0288D1.toInt()
                NodeType.LOOP -> 0xFFFF6D00.toInt()
                NodeType.COMMENT -> 0xFFFBC02D.toInt()
                NodeType.VARIABLE_GET -> 0xFF00ACC1.toInt()
                NodeType.VARIABLE_SET -> 0xFFFFA000.toInt()
                NodeType.CONSTRUCTION -> 0xFF00838F.toInt()
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
        return handled
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
