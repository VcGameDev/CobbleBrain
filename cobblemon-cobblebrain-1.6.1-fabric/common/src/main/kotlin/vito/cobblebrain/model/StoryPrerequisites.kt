package vito.cobblebrain.model

data class StoryPrerequisites(
    // 1. World & Game Conditions
    var freshWorldOnly: Boolean = false,
    var freshWorldMaxMinutes: Int = 20,
    var requiredDimension: String = "",
    var requiredGameMode: String = "ANY", // "ANY", "SURVIVAL", "ADVENTURE", "CREATIVE"

    // 2. Cobblemon Party Constraints
    var minPartySize: Int = -1, // -1 to disable limit (1..6)
    var maxPartySize: Int = -1, // -1 to disable limit (1..6)
    var partyLevelCap: Int = -1, // -1 to disable limit
    var requiredPokemonType: String = "", // e.g. "fire", "water", empty for any

    // 3. Story Dependencies & Inventory
    var requiredCompletedStories: MutableList<String> = mutableListOf(),
    var emptyInventoryRequired: Boolean = false,

    // 4. Failure Handling
    var failureAction: String = "ALERT_MESSAGE", // "SILENT_IGNORE", "ALERT_MESSAGE"
    var failureMessage: String = "" // Custom warning text
)
