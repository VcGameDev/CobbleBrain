package vito.cobblebrain.config

data class CobblebrainConfig(
    // ================= RELATIONSHIP SETTINGS =================
    var decreaseFriendship: Boolean = false,
    var increaseFriendship: Boolean = true,
    var maxFriendship: Int = 255,
    var showFriendship: Boolean = true,

    // ================= DIALOGUE & UI SETTINGS =================
    var allowPokemonPVP: Boolean = false,
    var allowPokemonPVE: Boolean = true,
    var scheduleRaids: Boolean = true,
    var wildPokemonTalkChance: Double = 0.10,
    var wildQuestChance: Double = 0.20,

    var dialogueInChat: Boolean = true,
    var chatbubbles: Boolean = true,

    var characteristics: List<String> = listOf("TestPokemon: He likes to sing, he fell off a bike once, he is from a farm"),
    var lowTokenMode: Boolean = false,
    var dialogueOnDamage: Boolean = false,
    var dialogueOnBattle: Boolean = true,
    var spontaneousDialogueChance: Double = 0.05,
    var listenToChat: Boolean = false,
    var onlyNearbyChat: Boolean = false,
    var maxShortMemory: Int = 2,
    var maxLongMemory: Int = 5,
    var useDefaultOutput: Boolean = true,
    var outputDialogue: Boolean = true,
    var outputActions: Boolean = true,
    var outputFriendship: Boolean = true,
    var outputQuests: Boolean = true,
    var outputWorldContext: Boolean = true,
    var outputMobsContext: Boolean = true,
    val outputLastContext: Boolean = true,
    val outputBlockSensors: Boolean = true,
    var outputMemories: Boolean = false,
    var outputApril1: Boolean = false,
    var outputPokemonLanguage: Boolean = false,
    var needsPokemonTranslator: Boolean = false,
    var outputGuaranteedCatch: Boolean = true,
)