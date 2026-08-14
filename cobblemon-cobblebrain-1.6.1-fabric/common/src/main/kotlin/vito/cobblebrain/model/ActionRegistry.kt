package vito.cobblebrain.model

enum class ActionCategory(val displayName: String, val icon: String) {
    MAP("🗺️ Mapa & Ambiente", "🗺️"),
    WORLD("🌎 Mundo & Blocos", "🌎"),
    ENTITIES("👾 Entidades & Mobs", "👾"),
    POKEMON("🐾 Cobblemon & Equipe", "🐾"),
    PLAYER("🧍 Jogador & Status", "🧍"),
    ITEMS("🎒 Itens & Drops", "🎒"),
    FLOW("🧩 Fluxo da História", "🧩"),
    INTERFACE("💬 Interface & Mensagens", "💬"),
    EFFECTS("✨ Efeitos & Áudio", "✨")
}

data class ActionDefinition(
    val id: String,
    val category: ActionCategory,
    val name: String,
    val icon: String,
    val description: String,
    val defaultParams: Map<String, String> = emptyMap()
)

object ActionRegistry {
    val actions = listOf(
        // 🗺️ MAPA
        ActionDefinition(
            id = "SPAWN_STRUCTURE",
            category = ActionCategory.MAP,
            name = "Gerar Estrutura",
            icon = "🏛️",
            description = "Posiciona uma estrutura NBT predefinida no mundo nas coordenadas indicadas.",
            defaultParams = mapOf("structureId" to "minecraft:small_house", "posX" to "~", "posY" to "~", "posZ" to "~")
        ),
        ActionDefinition(
            id = "TELEPORT",
            category = ActionCategory.MAP,
            name = "Teletransportar",
            icon = "🌀",
            description = "Teletransporta o jogador ou alvo para uma posição X, Y, Z ou cena.",
            defaultParams = mapOf("destX" to "0", "destY" to "64", "destZ" to "0")
        ),
        ActionDefinition(
            id = "CHANGE_WEATHER",
            category = ActionCategory.MAP,
            name = "Alterar Clima",
            icon = "🌧️",
            description = "Modifica as condições climáticas atuais do mundo (Limpo, Chuva, Tempestade).",
            defaultParams = mapOf("weatherType" to "CLEAR", "durationTicks" to "6000")
        ),
        ActionDefinition(
            id = "SET_TIME_OF_DAY",
            category = ActionCategory.MAP,
            name = "Definir Horário",
            icon = "⏰",
            description = "Ajusta o horário do dia do mundo (0=Dia, 6000=Meio-dia, 13000=Noite, 18000=Meia-noite).",
            defaultParams = mapOf("timeTicks" to "1000")
        ),

        // 🌎 MUNDO
        ActionDefinition(
            id = "SPAWN_BLOCK",
            category = ActionCategory.WORLD,
            name = "Colocar Bloco",
            icon = "🧱",
            description = "Coloca ou substitui um bloco nas coordenadas especificadas.",
            defaultParams = mapOf("blockId" to "minecraft:stone", "posX" to "~", "posY" to "~", "posZ" to "~")
        ),
        ActionDefinition(
            id = "MODIFY_BLOCK_PROPERTY",
            category = ActionCategory.WORLD,
            name = "Modificar Bloco",
            icon = "🔧",
            description = "Altera propriedades de estado de um bloco (ex: powered=true, open=true).",
            defaultParams = mapOf("posX" to "~", "posY" to "~", "posZ" to "~", "propertyKey" to "open", "propertyValue" to "true")
        ),

        // 👾 ENTIDADES
        ActionDefinition(
            id = "SPAWN_ENTITY",
            category = ActionCategory.ENTITIES,
            name = "Gerar Entidade",
            icon = "👾",
            description = "Gera uma entidade/mob (Vanilla ou mod) nas coordenadas especificadas.",
            defaultParams = mapOf("entityId" to "minecraft:villager", "customName" to "", "posX" to "~", "posY" to "~", "posZ" to "~")
        ),
        ActionDefinition(
            id = "KILL_ENTITY",
            category = ActionCategory.ENTITIES,
            name = "Eliminar Entidade",
            icon = "☠️",
            description = "Remove ou abate entidades em um raio ou com uma tag/nome específico.",
            defaultParams = mapOf("entitySelector" to "@e[type=zombie,distance=..10]")
        ),
        ActionDefinition(
            id = "MODIFY_ENTITY_PROPERTIES",
            category = ActionCategory.ENTITIES,
            name = "Propriedades da Entidade",
            icon = "📊",
            description = "Ajusta vida, escudo, velocidade, nome e inteligência artificial da entidade.",
            defaultParams = mapOf("health" to "20", "speedMultiplier" to "1.0", "customName" to "", "noAi" to "false")
        ),
        ActionDefinition(
            id = "ADD_ENTITY_EFFECT",
            category = ActionCategory.ENTITIES,
            name = "Efeito na Entidade",
            icon = "🧪",
            description = "Aplica um efeito de poção a uma entidade alvo específica.",
            defaultParams = mapOf("effectId" to "minecraft:glowing", "durationSec" to "15", "amplifier" to "1")
        ),
        ActionDefinition(
            id = "ADD_AREA_EFFECT",
            category = ActionCategory.ENTITIES,
            name = "Efeito em Área",
            icon = "🔮",
            description = "Aplica efeito de poção em todas as entidades dentro de um raio.",
            defaultParams = mapOf("effectId" to "minecraft:slowness", "radius" to "8", "durationSec" to "10", "amplifier" to "1")
        ),

        // 🐾 POKÉMON
        ActionDefinition(
            id = "SPAWN_COBBLEMON",
            category = ActionCategory.POKEMON,
            name = "Gerar Cobblemon",
            icon = "🐾",
            description = "Gera um Pokémon no mundo com nível, shiny, golpes e atributos configurados.",
            defaultParams = mapOf("species" to "Pikachu", "level" to "5", "shiny" to "false")
        ),
        ActionDefinition(
            id = "GIVE_POKEMON",
            category = ActionCategory.POKEMON,
            name = "Entregar Pokémon",
            icon = "🎁",
            description = "Adiciona um Pokémon configurado diretamente à equipe do jogador.",
            defaultParams = mapOf("species" to "Eevee", "level" to "5", "shiny" to "false")
        ),
        ActionDefinition(
            id = "MODIFY_POKEMON_PROPERTIES",
            category = ActionCategory.POKEMON,
            name = "Modificar Pokémon",
            icon = "📈",
            description = "Altera a vida, experiência, nível ou amizade de um Pokémon da equipe.",
            defaultParams = mapOf("slotIndex" to "0", "addExp" to "500", "addLevel" to "1", "healHp" to "true")
        ),
        ActionDefinition(
            id = "CHANGE_POKEMON_PERSONALITY",
            category = ActionCategory.POKEMON,
            name = "Personalidade CobbleBrain",
            icon = "🎭",
            description = "Altera o perfil de fala, personalidade e tom de conversa IA do Pokémon.",
            defaultParams = mapOf("slotIndex" to "0", "personalityPreset" to "Heroic")
        ),
        ActionDefinition(
            id = "ADD_POKEMON_PARTY_EFFECT",
            category = ActionCategory.POKEMON,
            name = "Efeito na Equipe Pokémon",
            icon = "✨",
            description = "Cura, remove condições de status ou restaura PP de toda a equipe Pokémon.",
            defaultParams = mapOf("healFullParty" to "true", "cureStatus" to "true")
        ),

        // 🧍 JOGADOR
        ActionDefinition(
            id = "KILL_PLAYER",
            category = ActionCategory.PLAYER,
            name = "Eliminar Jogador",
            icon = "💀",
            description = "Causa a derrota imediata do jogador.",
            defaultParams = emptyMap()
        ),
        ActionDefinition(
            id = "DAMAGE_PLAYER",
            category = ActionCategory.PLAYER,
            name = "Causar Dano ao Jogador",
            icon = "💔",
            description = "Aplica uma quantidade específica de pontos de dano ao jogador.",
            defaultParams = mapOf("damageAmount" to "4.0")
        ),
        ActionDefinition(
            id = "GIVE_ITEM",
            category = ActionCategory.PLAYER,
            name = "Dar Item ao Jogador",
            icon = "📦",
            description = "Adiciona itens diretamente ao inventário do jogador.",
            defaultParams = mapOf("itemId" to "cobblemon:poke_ball", "amount" to "5")
        ),
        ActionDefinition(
            id = "REMOVE_ITEM",
            category = ActionCategory.PLAYER,
            name = "Remover Item do Jogador",
            icon = "🗑️",
            description = "Retira uma quantidade de itens do inventário do jogador.",
            defaultParams = mapOf("itemId" to "cobblemon:poke_ball", "amount" to "1")
        ),
        ActionDefinition(
            id = "ADD_PLAYER_EFFECT",
            category = ActionCategory.PLAYER,
            name = "Efeito no Jogador",
            icon = "⚡",
            description = "Aplica um efeito de poção (ex: Velocidade, Invisibilidade) ao jogador.",
            defaultParams = mapOf("effectId" to "minecraft:speed", "durationSec" to "10", "amplifier" to "1")
        ),

        // 🎒 ITENS
        ActionDefinition(
            id = "SPAWN_ITEM",
            category = ActionCategory.ITEMS,
            name = "Dropar Item no Chão",
            icon = "💎",
            description = "Faz surgir um item flutuante dropado no chão nas coordenadas definidas.",
            defaultParams = mapOf("itemId" to "minecraft:diamond", "amount" to "1", "posX" to "~", "posY" to "~", "posZ" to "~")
        ),

        // 🧩 FLUXO
        ActionDefinition(
            id = "JUMP_TO_STORY_POINT",
            category = ActionCategory.FLOW,
            name = "Saltar para Ponto",
            icon = "⏩",
            description = "Transfere a execução imediatamente para outra Cena ou Nó da história.",
            defaultParams = mapOf("targetSceneId" to "", "targetNodeId" to "")
        ),
        ActionDefinition(
            id = "REWIND_TO_STORY_POINT",
            category = ActionCategory.FLOW,
            name = "Retroceder Ponto",
            icon = "⏪",
            description = "Restaura o estado e retrocede a execução para o checkpoint anterior.",
            defaultParams = mapOf("targetSceneId" to "")
        ),

        // 💬 INTERFACE
        ActionDefinition(
            id = "SEND_CHAT_MESSAGE",
            category = ActionCategory.INTERFACE,
            name = "Mensagem no Chat",
            icon = "💬",
            description = "Envia uma mensagem no chat com suporte a cores e variáveis.",
            defaultParams = mapOf("messageText" to "Olá!", "messageType" to "CHAT")
        ),
        ActionDefinition(
            id = "SHOW_TITLE_SCREEN",
            category = ActionCategory.INTERFACE,
            name = "Exibir Título na Tela",
            icon = "🎬",
            description = "Exibe um Título grande e Subtítulo no centro da tela do jogador.",
            defaultParams = mapOf("mainTitle" to "Missão Concluída!", "subTitle" to "Parabéns!", "fadeIn" to "10", "stay" to "40", "fadeOut" to "10")
        ),
        ActionDefinition(
            id = "CHANGE_SCREEN_TINT",
            category = ActionCategory.INTERFACE,
            name = "Coloração da Tela",
            icon = "🎨",
            description = "Aplica um filtro colorido temporário ou fade escuro na tela do jogador.",
            defaultParams = mapOf("tintColor" to "#FF0000", "alpha" to "0.5", "durationSec" to "3")
        ),

        // ✨ EFEITOS
        ActionDefinition(
            id = "SPAWN_PARTICLES",
            category = ActionCategory.EFFECTS,
            name = "Gerar Partículas",
            icon = "✨",
            description = "Faz surgir partículas visuais (ex: coração, fogo, totem) no local indicado.",
            defaultParams = mapOf("particleId" to "minecraft:totem_of_undying", "count" to "20", "posX" to "~", "posY" to "~", "posZ" to "~")
        ),
        ActionDefinition(
            id = "PLAY_SOUND",
            category = ActionCategory.EFFECTS,
            name = "Tocar Efeito Sonoro",
            icon = "🔊",
            description = "Reproduz um efeito de áudio estéreo/posicional para o jogador.",
            defaultParams = mapOf("soundId" to "minecraft:entity.player.levelup", "volume" to "1.0", "pitch" to "1.0")
        ),
        ActionDefinition(
            id = "PLAY_MUSIC",
            category = ActionCategory.EFFECTS,
            name = "Tocar Trilha Sonora",
            icon = "🎵",
            description = "Inicia ou para a reprodução de uma música ou trilha temática de fundo.",
            defaultParams = mapOf("musicId" to "minecraft:music.game", "loop" to "true")
        )
    )

    fun find(id: String?): ActionDefinition {
        if (id == null) return actions.first()
        val normalized = when (id) {
            "MESSAGE" -> "SEND_CHAT_MESSAGE"
            "SPAWN_POKEMON", "SPAWN" -> "SPAWN_COBBLEMON"
            "SOUND" -> "PLAY_SOUND"
            "EFFECT" -> "ADD_PLAYER_EFFECT"
            "COMMAND" -> "SPAWN_ENTITY"
            else -> id
        }
        return actions.find { it.id == normalized } ?: actions.first()
    }
}
