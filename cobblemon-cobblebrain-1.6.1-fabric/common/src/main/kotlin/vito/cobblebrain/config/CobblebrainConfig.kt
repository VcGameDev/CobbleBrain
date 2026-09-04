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
    var enableKarma: Boolean = true,
    var scheduleRaids: Boolean = true,
    var wildPokemonTalkChance: Double = 0.10,
    var wildQuestChance: Double = 0.20,

    var dialogueInChat: Boolean = true,
    var chatbubbles: Boolean = true,
    var optimizedMode: Boolean = true,
    var forceOfflineMode: Boolean = false,
    var disableWelcomeMessage: Boolean = false,

    var characteristics: List<String> = listOf("TestPokemon: He likes to sing, he fell off a bike once, he is from a farm"),
    var lowTokenMode: Boolean = false,
    var dialogueOnDamage: Boolean = false,
    var dialogueOnBattle: Boolean = true,
    var spontaneousDialogueChance: Double = 0.05,
    var listenToChat: Boolean = false,
    var onlyNearbyChat: Boolean = false,
    var maxStoredMemories: Int = 100,
    var maxRelevantMemories: Int = 4,
    var favoriteMemorySlots: Int = 5,
    var baseCandidateMemories: Int = 10,
    var enableAiMemoryRetrieval: Boolean = false,
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
    var allowClientPersonalityEditing: Boolean = true,
    var enableTraits: Boolean = true,
    var actionSettings: ActionSettings = ActionSettings()
)

data class BaseActionConfig(
    var active: Boolean = true
)

data class FishActionConfig(
    var active: Boolean = true,
    var maxFishRewardCount: Int = 5,
    var luckBonus: Int = 0,
    var allowTreasureLoot: Boolean = true
)

data class LightActionConfig(
    var active: Boolean = true,
    var lightIntensity: Int = 15
)

data class CookActionConfig(
    var active: Boolean = true,
    var charcoalChancePercent: Int = 5,
    var cooldownTicks: Int = 22
)

data class RepairActionConfig(
    var active: Boolean = true,
    var maxRepairPercent: Int = 100,
    var cooldownTicks: Int = 40
)

data class ScoutActionConfig(
    var active: Boolean = true,
    var scoutRadius: Int = 50,
    var scoutFindStructures: Boolean = true,
    var scoutHighlightMobs: Boolean = true
)

data class NightmareActionConfig(
    var active: Boolean = true,
    var nightmareRadius: Int = 10,
    var durationSeconds: Int = 8,
    var effectLevel: Int = 1,
    var cooldownSeconds: Int = 120
)

data class ShiftActionConfig(
    var active: Boolean = true,
    var shiftDurationSeconds: Int = 30,
    var effectLevel: Int = 1,
    var cooldownSeconds: Int = 240
)

data class GrowActionConfig(
    var active: Boolean = true,
    var growIntervalTicks: Int = 20
)

data class AttackActionConfig(
    var active: Boolean = true,
    var damageMultiplier: Double = 1.0
)

data class ProtectActionConfig(
    var active: Boolean = true,
    var damageMultiplier: Double = 1.0
)

data class BuffActionConfig(
    var active: Boolean = true,
    var durationSeconds: Int = 30,
    var effectLevel: Int = 1
)

data class DebuffEnemyActionConfig(
    var active: Boolean = true,
    var durationSeconds: Int = 15,
    var effectLevel: Int = 1
)

data class ExcavateActionConfig(
    var active: Boolean = true,
    var maxBlocks: Int = 144,
    var breakDelayTicks: Int = 3,
    var dropChancePercent: Int = 30,
    var workingDistance: Double = 32.0
)

data class ActionSettings(
    var cook: CookActionConfig = CookActionConfig(),
    var grow: GrowActionConfig = GrowActionConfig(),
    var repair: RepairActionConfig = RepairActionConfig(),
    var shift: ShiftActionConfig = ShiftActionConfig(),
    var fish: FishActionConfig = FishActionConfig(),
    var nightmare: NightmareActionConfig = NightmareActionConfig(),
    var light: LightActionConfig = LightActionConfig(),
    var scout: ScoutActionConfig = ScoutActionConfig(),
    var teleport: BaseActionConfig = BaseActionConfig(),
    var attack: AttackActionConfig = AttackActionConfig(),
    var protect: ProtectActionConfig = ProtectActionConfig(),
    var eat: BaseActionConfig = BaseActionConfig(),
    var buff: BuffActionConfig = BuffActionConfig(),
    var debuffEnemy: DebuffEnemyActionConfig = DebuffEnemyActionConfig(),
    var excavate: ExcavateActionConfig = ExcavateActionConfig(),
    var demolish: ExcavateActionConfig = excavate,
    var rest: BaseActionConfig = BaseActionConfig(),
    var idle: BaseActionConfig = BaseActionConfig()
) {
    var sit: BaseActionConfig
        get() = rest
        set(value) { rest = value }
}
