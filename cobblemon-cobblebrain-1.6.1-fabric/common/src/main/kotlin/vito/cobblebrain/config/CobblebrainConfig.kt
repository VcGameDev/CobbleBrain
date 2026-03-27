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
    var wildPokemonTalkChance: Double = 0.10,
    var wildQuestChance: Double = 0.20,

    var dialogueInChat: Boolean = true,
    var chatbubbles: Boolean = true,

    var selectedLanguage: String = "English",

    var characteristics: List<String> = listOf("TestPokemon: He likes to sing, he fell off a bike once, he is from a farm"),
    var lowTokenMode: Boolean = false,
    var dialogueOnDamage: Boolean = false,
    var dialogueOnBattle: Boolean = true,
    var spontaneousDialogueChance: Double = 0.05,
    var listenToChat: Boolean = false,
    var onlyNearbyChat: Boolean = false,
)