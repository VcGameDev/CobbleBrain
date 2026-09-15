package vito.cobblebrain.model

enum class TriggerCategory(val displayName: String, val icon: String) {
    STORY("📖 Story & Quests", "📖"),
    TIME("⏱️ Time & Schedule", "⏱️"),
    PLAYER("🧍 Player & Inventory", "🧍"),
    POKEMON("🐾 Cobblemon & Party", "🐾"),
    COMBAT("⚔️ Combat & Battles", "⚔️"),
    WORLD("🌍 World & Blocks", "🌍"),
    COBBLEBRAIN("🧠 CobbleBrain & AI", "🧠")
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
        // 📖 STORY
        TriggerDefinition(
            id = "STORY_STARTED",
            category = TriggerCategory.STORY,
            name = "Story Started",
            icon = "🟢",
            description = "Triggers immediately when the scene or story starts.",
            defaultParams = emptyMap()
        ),
        TriggerDefinition(
            id = "STORY_ENDED",
            category = TriggerCategory.STORY,
            name = "Story Ended",
            icon = "🛑",
            description = "Triggers when the previous scene or story finishes.",
            defaultParams = emptyMap()
        ),
        TriggerDefinition(
            id = "PREVIOUS_MISSION_COMPLETED",
            category = TriggerCategory.STORY,
            name = "Quest Completed",
            icon = "📜",
            description = "Triggers when a specific quest/objective is completed.",
            defaultParams = mapOf("missionId" to "mission_1")
        ),
        TriggerDefinition(
            id = "PREVIOUS_EVENT_EXECUTED",
            category = TriggerCategory.STORY,
            name = "Event Executed",
            icon = "⚡",
            description = "Triggers when a specific event tag was previously executed.",
            defaultParams = mapOf("eventTag" to "key_event")
        ),
        TriggerDefinition(
            id = "VARIABLE_VALUE_CHECK",
            category = TriggerCategory.STORY,
            name = "Variable Value Check",
            icon = "🔢",
            description = "Triggers when a variable reaches a specific condition (e.g. coins >= 100).",
            defaultParams = mapOf("varKey" to "var_1", "varOp" to ">=", "varValue" to "100")
        ),

        // ⏱️ TIME
        TriggerDefinition(
            id = "TIME_ELAPSED",
            category = TriggerCategory.TIME,
            name = "Time Elapsed",
            icon = "⏱️",
            description = "Triggers after a specified amount of seconds or ticks.",
            defaultParams = mapOf("timeSeconds" to "10")
        ),
        TriggerDefinition(
            id = "TIME_OF_DAY",
            category = TriggerCategory.TIME,
            name = "Time of Day",
            icon = "🌅",
            description = "Triggers when world time reaches value (0=Dawn, 6000=Noon, 18000=Midnight).",
            defaultParams = mapOf("timeOfDayTicks" to "6000")
        ),
        TriggerDefinition(
            id = "DAYS_PASSED",
            category = TriggerCategory.TIME,
            name = "Days Passed",
            icon = "📅",
            description = "Triggers after a specific number of in-game days.",
            defaultParams = mapOf("daysCount" to "1")
        ),
        TriggerDefinition(
            id = "DAY_NIGHT_CHECK",
            category = TriggerCategory.TIME,
            name = "Day / Night Check",
            icon = "☀️",
            description = "Triggers based on day period (DAY or NIGHT).",
            defaultParams = mapOf("timePeriod" to "DAY")
        ),

        // 🧍 PLAYER
        TriggerDefinition(
            id = "PLAYER_LEVEL",
            category = TriggerCategory.PLAYER,
            name = "Player EXP Level",
            icon = "⭐",
            description = "Triggers when player experience level reaches target.",
            defaultParams = mapOf("minLevel" to "10", "comparisonOp" to ">=")
        ),
        TriggerDefinition(
            id = "PLAYER_COORDINATES",
            category = TriggerCategory.PLAYER,
            name = "Coordinates Reached",
            icon = "📍",
            description = "Triggers when player enters radius of X, Y, Z coordinates.",
            defaultParams = mapOf("targetX" to "0", "targetY" to "64", "targetZ" to "0", "radius" to "5")
        ),
        TriggerDefinition(
            id = "PLAYER_BIOME",
            category = TriggerCategory.PLAYER,
            name = "Player Biome",
            icon = "🌲",
            description = "Triggers when player is inside a specific biome.",
            defaultParams = mapOf("biomeId" to "minecraft:plains")
        ),
        TriggerDefinition(
            id = "PLAYER_HELD_ITEM",
            category = TriggerCategory.PLAYER,
            name = "Held Item",
            icon = "🗡️",
            description = "Triggers when player holds specified item in main hand.",
            defaultParams = mapOf("heldItemId" to "minecraft:diamond_sword")
        ),
        TriggerDefinition(
            id = "PLAYER_INVENTORY_HAS_ITEM",
            category = TriggerCategory.PLAYER,
            name = "Has Item in Inventory",
            icon = "🎒",
            description = "Triggers when player inventory contains specified item.",
            defaultParams = mapOf("requiredItem" to "cobblemon:potion", "requiredCount" to "1")
        ),
        TriggerDefinition(
            id = "PLAYER_INVENTORY_ITEM_REMOVED",
            category = TriggerCategory.PLAYER,
            name = "Item Removed",
            icon = "🗑️",
            description = "Triggers when player drops or loses specified item.",
            defaultParams = mapOf("removedItemId" to "cobblemon:poke_ball")
        ),
        TriggerDefinition(
            id = "PLAYER_ITEM_COUNT",
            category = TriggerCategory.PLAYER,
            name = "Item Quantity",
            icon = "🔢",
            description = "Triggers when inventory item count meets condition.",
            defaultParams = mapOf("checkItemId" to "cobblemon:poke_ball", "minCount" to "10", "comparisonOp" to ">=")
        ),

        // 🐾 POKÉMON
        TriggerDefinition(
            id = "TALK_TO_POKEMON",
            category = TriggerCategory.POKEMON,
            name = "Talk to Pokémon",
            icon = "💬",
            description = "Triggers when player talks or opens chat with a Pokémon.",
            defaultParams = mapOf("targetSpecies" to "Pikachu")
        ),
        TriggerDefinition(
            id = "INTERACT_POKEMON",
            category = TriggerCategory.POKEMON,
            name = "Interact with Entity / Pokémon",
            icon = "🐾",
            description = "Triggers when right clicking a target entity or Pokémon.",
            defaultParams = mapOf(
                "targetType" to "COBBLEMON",
                "entityType" to "minecraft:villager",
                "requiredStoryTag" to "",
                "targetSpecies" to "Eevee",
                "form" to "",
                "minLevel" to "1",
                "maxLevel" to "100",
                "shinyMode" to "ANY",
                "pokemonStatus" to "ANY"
            )
        ),
        TriggerDefinition(
            id = "POKEMON_CATCH",
            category = TriggerCategory.POKEMON,
            name = "Catch Pokémon",
            icon = "🔴",
            description = "Triggers when player successfully catches a Pokémon species.",
            defaultParams = mapOf("targetSpecies" to "Pikachu", "requiredStoryTag" to "")
        ),
        TriggerDefinition(
            id = "HIGHEST_POKEMON_LEVEL",
            category = TriggerCategory.POKEMON,
            name = "Highest Level in Party",
            icon = "🏆",
            description = "Triggers when highest level Pokémon in party reaches target.",
            defaultParams = mapOf("targetLevel" to "20", "comparisonOp" to ">=")
        ),
        TriggerDefinition(
            id = "SPECIFIC_POKEMON_IN_PARTY",
            category = TriggerCategory.POKEMON,
            name = "Pokémon in Party",
            icon = "👥",
            description = "Triggers when a specific Pokémon species is in party.",
            defaultParams = mapOf("targetSpecies" to "Charizard")
        ),
        TriggerDefinition(
            id = "POKEMON_FRIENDSHIP",
            category = TriggerCategory.POKEMON,
            name = "Pokémon Friendship",
            icon = "❤️",
            description = "Triggers when Pokémon friendship/happiness reaches target (0-255).",
            defaultParams = mapOf("targetSpecies" to "Pikachu", "minFriendship" to "220")
        ),

        // ⚔️ COMBAT
        TriggerDefinition(
            id = "BATTLE_START",
            category = TriggerCategory.COMBAT,
            name = "Battle Start",
            icon = "⚔️",
            description = "Triggers when player starts battle against trainer or wild Pokémon.",
            defaultParams = mapOf("battleType" to "ANY")
        ),
        TriggerDefinition(
            id = "BATTLE_VICTORY",
            category = TriggerCategory.COMBAT,
            name = "Battle Victory",
            icon = "🏆",
            description = "Triggers when player wins a Pokémon battle.",
            defaultParams = mapOf("targetSpecies" to "")
        ),
        TriggerDefinition(
            id = "BATTLE_DEFEAT",
            category = TriggerCategory.COMBAT,
            name = "Battle Defeat",
            icon = "💀",
            description = "Triggers when entire player party faints in battle.",
            defaultParams = emptyMap()
        ),
        TriggerDefinition(
            id = "ENTITY_DIED",
            category = TriggerCategory.COMBAT,
            name = "Entity Death",
            icon = "☠️",
            description = "Triggers when a specific entity or Cobblemon dies/faints.",
            defaultParams = mapOf(
                "targetType" to "GENERIC",
                "entityType" to "minecraft:zombie",
                "requiredStoryTag" to "",
                "targetSpecies" to "Pikachu",
                "form" to "",
                "minLevel" to "1",
                "maxLevel" to "100",
                "shinyMode" to "ANY",
                "pokemonStatus" to "ANY"
            )
        ),
        TriggerDefinition(
            id = "ENTITY_DAMAGED",
            category = TriggerCategory.COMBAT,
            name = "Entity Took Damage",
            icon = "💥",
            description = "Triggers when a specific entity or Cobblemon takes damage.",
            defaultParams = mapOf(
                "targetType" to "GENERIC",
                "entityType" to "minecraft:player",
                "minDamage" to "1.0",
                "requiredStoryTag" to "",
                "targetSpecies" to "Pikachu",
                "form" to "",
                "minLevel" to "1",
                "maxLevel" to "100",
                "shinyMode" to "ANY",
                "pokemonStatus" to "ANY"
            )
        ),

        // 🌍 WORLD
        TriggerDefinition(
            id = "WEATHER_CHECK",
            category = TriggerCategory.WORLD,
            name = "Weather Check",
            icon = "🌧️",
            description = "Triggers based on weather condition (CLEAR, RAIN, THUNDER).",
            defaultParams = mapOf("weatherType" to "RAIN")
        ),
        TriggerDefinition(
            id = "BLOCK_INTERACTED",
            category = TriggerCategory.WORLD,
            name = "Block Interacted",
            icon = "🧱",
            description = "Triggers when player clicks block of specific type or position.",
            defaultParams = mapOf("blockId" to "minecraft:chest", "blockPos" to "")
        ),
        TriggerDefinition(
            id = "BLOCK_PLACED",
            category = TriggerCategory.WORLD,
            name = "Block Placed",
            icon = "📦",
            description = "Triggers when player places a specific block in the world.",
            defaultParams = mapOf("blockId" to "minecraft:stone")
        ),
        TriggerDefinition(
            id = "ENTITY_SPAWNED",
            category = TriggerCategory.WORLD,
            name = "Entity Spawned",
            icon = "👾",
            description = "Triggers when entity of specified type spawns in the world.",
            defaultParams = mapOf(
                "targetType" to "COBBLEMON",
                "entityType" to "cobblemon:pokemon",
                "requiredStoryTag" to "",
                "targetSpecies" to "Pikachu",
                "form" to "",
                "minLevel" to "1",
                "maxLevel" to "100",
                "shinyMode" to "ANY",
                "pokemonStatus" to "ANY"
            )
        ),
        TriggerDefinition(
            id = "ENTER_STRUCTURE_OR_ZONE",
            category = TriggerCategory.WORLD,
            name = "Entered Zone / Structure",
            icon = "🏛️",
            description = "Triggers when player enters structure or defined zone.",
            defaultParams = mapOf("structureId" to "minecraft:village_plains")
        ),

        // 🧠 COBBLEBRAIN
        TriggerDefinition(
            id = "KARMA_CHECK",
            category = TriggerCategory.COBBLEBRAIN,
            name = "Karma Check",
            icon = "⚖️",
            description = "Triggers based on player Karma/Moral score in story.",
            defaultParams = mapOf("targetKarma" to "0", "comparisonOp" to ">=")
        ),
        TriggerDefinition(
            id = "AI_EVALUATION",
            category = TriggerCategory.COBBLEBRAIN,
            name = "AI Decision Evaluation",
            icon = "🧠",
            description = "Triggers when AI response or decision matches intention.",
            defaultParams = mapOf("aiIntent" to "AGREE")
        )
    )

    fun find(id: String?): TriggerDefinition {
        if (id == null) return triggers.first()
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
