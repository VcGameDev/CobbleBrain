package vito.cobblebrain.config

data class CobblebrainConfig(
    // ================= RELATIONSHIP SETTINGS =================
    var decreaseFriendship: Boolean = false,
    var increaseFriendship: Boolean = true,
    var showFriendship: Boolean = true,

    // ================= DIALOGUE & UI SETTINGS =================
    var pokemonTalk: Boolean = true,
    var allowPokemonPVP: Boolean = false,
    var allowPokemonPVE: Boolean = true,
    var scheduleRaids: Boolean = true,
    var wildPokemonTalkChance: Double = 0.25,
    var wildQuestChance: Double = 0.05
)