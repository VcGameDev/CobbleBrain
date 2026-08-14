package vito.cobblebrain.model

enum class TriggerCategory(val displayName: String, val icon: String) {
    STORY("📖 História & Missões", "📖"),
    TIME("⏱️ Tempo & Horário", "⏱️"),
    PLAYER("🧍 Jogador & Inventário", "🧍"),
    POKEMON("🐾 Cobblemon & Equipe", "🐾"),
    COMBAT("⚔️ Combate & Batalhas", "⚔️"),
    WORLD("🌍 Mundo & Blocos", "🌍"),
    COBBLEBRAIN("🧠 CobbleBrain & IA", "🧠")
}

data class TriggerDefinition(
    val id: String,
    val category: TriggerCategory,
    val name: String,
    val icon: String,
    val description: String,
    val defaultParams: Map<String, String> = emptyMap()
)

object TriggerRegistry {
    val triggers = listOf(
        // 📖 HISTÓRIA
        TriggerDefinition(
            id = "STORY_STARTED",
            category = TriggerCategory.STORY,
            name = "Início da História",
            icon = "🟢",
            description = "Dispara imediatamente quando a cena ou história é iniciada.",
            defaultParams = emptyMap()
        ),
        TriggerDefinition(
            id = "STORY_ENDED",
            category = TriggerCategory.STORY,
            name = "Fim da História",
            icon = "🛑",
            description = "Dispara quando a cena anterior ou história for finalizada.",
            defaultParams = emptyMap()
        ),
        TriggerDefinition(
            id = "PREVIOUS_MISSION_COMPLETED",
            category = TriggerCategory.STORY,
            name = "Missão Concluída",
            icon = "📜",
            description = "Dispara quando uma missão/objetivo específico for concluído.",
            defaultParams = mapOf("missionId" to "missao_1")
        ),
        TriggerDefinition(
            id = "PREVIOUS_EVENT_EXECUTED",
            category = TriggerCategory.STORY,
            name = "Evento Executado",
            icon = "⚡",
            description = "Dispara quando um evento/tag de gatilho for disparado anteriormente.",
            defaultParams = mapOf("eventTag" to "evento_chave")
        ),

        // ⏱️ TEMPO
        TriggerDefinition(
            id = "TIME_ELAPSED",
            category = TriggerCategory.TIME,
            name = "Tempo Decorrido",
            icon = "⏱️",
            description = "Dispara após a passagem de uma quantidade de segundos ou ticks.",
            defaultParams = mapOf("timeSeconds" to "10")
        ),
        TriggerDefinition(
            id = "TIME_OF_DAY",
            category = TriggerCategory.TIME,
            name = "Horário do Dia",
            icon = "🌅",
            description = "Dispara quando o horário do mundo atingir o valor definido (0=Amanhecer, 6000=Meio-dia, 18000=Meia-noite).",
            defaultParams = mapOf("timeOfDayTicks" to "6000")
        ),
        TriggerDefinition(
            id = "DAYS_PASSED",
            category = TriggerCategory.TIME,
            name = "Dias Passados",
            icon = "📅",
            description = "Dispara após um número específico de dias no jogo.",
            defaultParams = mapOf("daysCount" to "1")
        ),
        TriggerDefinition(
            id = "DAY_NIGHT_CHECK",
            category = TriggerCategory.TIME,
            name = "Checagem Dia / Noite",
            icon = "☀️",
            description = "Dispara conforme o período do dia (DAY ou NIGHT).",
            defaultParams = mapOf("timePeriod" to "DAY")
        ),

        // 🧍 JOGADOR
        TriggerDefinition(
            id = "PLAYER_LEVEL",
            category = TriggerCategory.PLAYER,
            name = "Nível de EXP do Jogador",
            icon = "⭐",
            description = "Dispara quando o nível de experiência do jogador atingir o alvo.",
            defaultParams = mapOf("minLevel" to "10", "comparisonOp" to ">=")
        ),
        TriggerDefinition(
            id = "PLAYER_COORDINATES",
            category = TriggerCategory.PLAYER,
            name = "Chegada em Coordenadas",
            icon = "📍",
            description = "Dispara quando o jogador entra no raio das coordenadas X, Y, Z.",
            defaultParams = mapOf("targetX" to "0", "targetY" to "64", "targetZ" to "0", "radius" to "5")
        ),
        TriggerDefinition(
            id = "PLAYER_BIOME",
            category = TriggerCategory.PLAYER,
            name = "Bioma do Jogador",
            icon = "🌲",
            description = "Dispara quando o jogador estiver em um bioma específico.",
            defaultParams = mapOf("biomeId" to "minecraft:plains")
        ),
        TriggerDefinition(
            id = "PLAYER_HELD_ITEM",
            category = TriggerCategory.PLAYER,
            name = "Item na Mão",
            icon = "🗡️",
            description = "Dispara quando o jogador estiver segurando o item especificado na mão principal.",
            defaultParams = mapOf("heldItemId" to "minecraft:diamond_sword")
        ),
        TriggerDefinition(
            id = "PLAYER_INVENTORY_HAS_ITEM",
            category = TriggerCategory.PLAYER,
            name = "Possui Item no Inventário",
            icon = "🎒",
            description = "Dispara quando o inventário do jogador contiver o item especificado.",
            defaultParams = mapOf("requiredItem" to "cobblemon:potion", "requiredCount" to "1")
        ),
        TriggerDefinition(
            id = "PLAYER_INVENTORY_ITEM_REMOVED",
            category = TriggerCategory.PLAYER,
            name = "Item Removido",
            icon = "🗑️",
            description = "Dispara quando o jogador descarta ou perde um item específico.",
            defaultParams = mapOf("removedItemId" to "cobblemon:poke_ball")
        ),
        TriggerDefinition(
            id = "PLAYER_ITEM_COUNT",
            category = TriggerCategory.PLAYER,
            name = "Quantidade de Itens",
            icon = "🔢",
            description = "Dispara quando a quantidade de um item no inventário satisfizer a condição.",
            defaultParams = mapOf("checkItemId" to "cobblemon:poke_ball", "minCount" to "10", "comparisonOp" to ">=")
        ),

        // 🐾 POKÉMON
        TriggerDefinition(
            id = "TALK_TO_POKEMON",
            category = TriggerCategory.POKEMON,
            name = "Conversar com Pokémon",
            icon = "💬",
            description = "Dispara quando o jogador dialoga ou abre chat com um Pokémon.",
            defaultParams = mapOf("targetSpecies" to "Pikachu")
        ),
        TriggerDefinition(
            id = "INTERACT_POKEMON",
            category = TriggerCategory.POKEMON,
            name = "Interagir com Pokémon",
            icon = "🐾",
            description = "Dispara ao clicar com botão direito em um Pokémon selvagem ou da equipe.",
            defaultParams = mapOf("targetSpecies" to "Eevee")
        ),
        TriggerDefinition(
            id = "POKEMON_CATCH",
            category = TriggerCategory.POKEMON,
            name = "Captura de Pokémon",
            icon = "🔴",
            description = "Dispara quando o jogador captura com sucesso uma espécie de Pokémon.",
            defaultParams = mapOf("targetSpecies" to "Pikachu")
        ),
        TriggerDefinition(
            id = "HIGHEST_POKEMON_LEVEL",
            category = TriggerCategory.POKEMON,
            name = "Maior Nível na Equipe",
            icon = "🏆",
            description = "Dispara quando o Pokémon de maior nível na equipe do jogador atingir o alvo.",
            defaultParams = mapOf("targetLevel" to "20", "comparisonOp" to ">=")
        ),
        TriggerDefinition(
            id = "SPECIFIC_POKEMON_IN_PARTY",
            category = TriggerCategory.POKEMON,
            name = "Pokémon na Equipe",
            icon = "👥",
            description = "Dispara quando uma determinada espécie de Pokémon estiver na equipe do jogador.",
            defaultParams = mapOf("targetSpecies" to "Charizard")
        ),
        TriggerDefinition(
            id = "POKEMON_FRIENDSHIP",
            category = TriggerCategory.POKEMON,
            name = "Amizade do Pokémon",
            icon = "❤️",
            description = "Dispara quando o nível de amizade/felicidade do Pokémon atingir o valor alvo (0-255).",
            defaultParams = mapOf("targetSpecies" to "Pikachu", "minFriendship" to "220")
        ),

        // ⚔️ COMBATE
        TriggerDefinition(
            id = "BATTLE_START",
            category = TriggerCategory.COMBAT,
            name = "Início de Batalha",
            icon = "⚔️",
            description = "Dispara quando o jogador inicia uma batalha contra treinador ou selvagem.",
            defaultParams = mapOf("battleType" to "ANY")
        ),
        TriggerDefinition(
            id = "BATTLE_VICTORY",
            category = TriggerCategory.COMBAT,
            name = "Vitória em Batalha",
            icon = "🏆",
            description = "Dispara quando o jogador vence uma batalha Pokémon.",
            defaultParams = mapOf("targetSpecies" to "")
        ),
        TriggerDefinition(
            id = "BATTLE_DEFEAT",
            category = TriggerCategory.COMBAT,
            name = "Derrota em Batalha",
            icon = "💀",
            description = "Dispara quando toda a equipe do jogador desmaia em batalha.",
            defaultParams = emptyMap()
        ),
        TriggerDefinition(
            id = "ENTITY_DIED",
            category = TriggerCategory.COMBAT,
            name = "Morte de Entidade",
            icon = "☠️",
            description = "Dispara quando uma entidade com ID ou Tag específica for derrotada/morta.",
            defaultParams = mapOf("entityType" to "minecraft:zombie", "entityTag" to "")
        ),
        TriggerDefinition(
            id = "ENTITY_DAMAGED",
            category = TriggerCategory.COMBAT,
            name = "Entidade Sofreu Dano",
            icon = "💥",
            description = "Dispara quando uma entidade específica sofrer dano.",
            defaultParams = mapOf("entityType" to "minecraft:player", "minDamage" to "1.0")
        ),

        // 🌍 MUNDO
        TriggerDefinition(
            id = "WEATHER_CHECK",
            category = TriggerCategory.WORLD,
            name = "Checagem de Clima",
            icon = "🌧️",
            description = "Dispara conforme a condição climática (CLEAR, RAIN, THUNDER).",
            defaultParams = mapOf("weatherType" to "RAIN")
        ),
        TriggerDefinition(
            id = "BLOCK_INTERACTED",
            category = TriggerCategory.WORLD,
            name = "Interação com Bloco",
            icon = "🧱",
            description = "Dispara quando o jogador clica em um bloco de tipo ou posição específica.",
            defaultParams = mapOf("blockId" to "minecraft:chest", "blockPos" to "")
        ),
        TriggerDefinition(
            id = "BLOCK_PLACED",
            category = TriggerCategory.WORLD,
            name = "Bloco Posicionado",
            icon = "📦",
            description = "Dispara quando o jogador colocar um bloco específico no mundo.",
            defaultParams = mapOf("blockId" to "minecraft:stone")
        ),
        TriggerDefinition(
            id = "ENTITY_SPAWNED",
            category = TriggerCategory.WORLD,
            name = "Entidade Gerada",
            icon = "👾",
            description = "Dispara quando uma entidade do tipo informado surgir no mundo.",
            defaultParams = mapOf("entityType" to "cobblemon:pokemon")
        ),
        TriggerDefinition(
            id = "ENTER_STRUCTURE_OR_ZONE",
            category = TriggerCategory.WORLD,
            name = "Entrada em Zona / Estrutura",
            icon = "🏛️",
            description = "Dispara quando o jogador entrar em uma estrutura (vila, templo) ou zona delimitada.",
            defaultParams = mapOf("structureId" to "minecraft:village_plains")
        ),

        // 🧠 COBBLEBRAIN
        TriggerDefinition(
            id = "KARMA_CHECK",
            category = TriggerCategory.COBBLEBRAIN,
            name = "Checagem de Karma",
            icon = "⚖️",
            description = "Dispara com base na pontuação de Karma/Moral do jogador na história.",
            defaultParams = mapOf("targetKarma" to "0", "comparisonOp" to ">=")
        ),
        TriggerDefinition(
            id = "AI_EVALUATION",
            category = TriggerCategory.COBBLEBRAIN,
            name = "Avaliação da IA",
            icon = "🧠",
            description = "Dispara quando a resposta ou decisão gerada pela IA coincidir com uma intenção.",
            defaultParams = mapOf("aiIntent" to "AGREE")
        )
    )

    fun find(id: String?): TriggerDefinition {
        if (id == null) return triggers.first()
        // Mapeamentos de compatibilidade com IDs legados
        val normalized = when (id) {
            "START" -> "STORY_STARTED"
            "LOCATION" -> "PLAYER_COORDINATES"
            "INTERACT_ENTITY" -> "INTERACT_POKEMON"
            "DEFEAT_POKEMON" -> "BATTLE_VICTORY"
            "CATCH_POKEMON" -> "POKEMON_CATCH"
            "ITEM_IN_INVENTORY" -> "PLAYER_INVENTORY_HAS_ITEM"
            else -> id
        }
        return triggers.find { it.id == normalized } ?: triggers.first()
    }
}
