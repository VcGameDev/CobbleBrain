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
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = true,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class RestActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = true,
    var spawnCarpet: Boolean = true,
    var healAmount: Int = 1,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class FishActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = true,
    var maxFishRewardCount: Int = 5,
    var luckBonus: Int = 0,
    var allowTreasureLoot: Boolean = true,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class LightActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = true,
    var lightIntensity: Int = 15,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class CookActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = true,
    var charcoalChancePercent: Int = 5,
    var cooldownTicks: Int = 22,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class RepairActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = true,
    var maxRepairPercent: Int = 100,
    var cooldownTicks: Int = 40,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class ScoutActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = true,
    var scoutRadius: Int = 50,
    var scoutFindStructures: Boolean = true,
    var scoutHighlightMobs: Boolean = true,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class NightmareActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = true,
    var nightmareRadius: Int = 10,
    var durationSeconds: Int = 8,
    var effectLevel: Int = 1,
    var cooldownSeconds: Int = 120,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class ShiftActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = true,
    var shiftDurationSeconds: Int = 30,
    var effectLevel: Int = 1,
    var cooldownSeconds: Int = 240,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class GrowActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = true,
    var growIntervalTicks: Int = 20,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class AttackActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = true,
    var damageMultiplier: Double = 1.0,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class ProtectActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = true,
    var damageMultiplier: Double = 1.0,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class BuffActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = true,
    var durationSeconds: Int = 30,
    var effectLevel: Int = 1,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class DebuffEnemyActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = true,
    var durationSeconds: Int = 15,
    var effectLevel: Int = 1,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class ExcavateActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = false,
    var maxBlocks: Int = 144,
    var breakDelayTicks: Int = 3,
    var dropChancePercent: Int = 30,
    var workingDistance: Double = 32.0,
    var exhaustionDamagePerLayer: Int = 2,
    var minHealthPercent: Int = 10,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class TeleportActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = false,
    var cooldownSeconds: Int = 30,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class BuildActionConfig(
    var enabledForPlayer: Boolean = true,
    var enabledForAI: Boolean = false,
    @com.google.gson.annotations.SerializedName("active")
    var legacyActive: Boolean? = null
) {
    var active: Boolean
        get() = legacyActive ?: (enabledForPlayer || enabledForAI)
        set(value) {
            enabledForPlayer = value
            enabledForAI = value
            legacyActive = null
        }

    fun migrateLegacy() {
        legacyActive?.let {
            enabledForPlayer = it
            enabledForAI = it
            legacyActive = null
        }
    }
}

data class ActionSettings(
    var cook: CookActionConfig = CookActionConfig(),
    var grow: GrowActionConfig = GrowActionConfig(),
    var repair: RepairActionConfig = RepairActionConfig(),
    var shift: ShiftActionConfig = ShiftActionConfig(),
    var fish: FishActionConfig = FishActionConfig(),
    var nightmare: NightmareActionConfig = NightmareActionConfig(),
    var light: LightActionConfig = LightActionConfig(),
    var scout: ScoutActionConfig = ScoutActionConfig(),
    var teleport: TeleportActionConfig = TeleportActionConfig(),
    var attack: AttackActionConfig = AttackActionConfig(),
    var protect: ProtectActionConfig = ProtectActionConfig(),
    var eat: BaseActionConfig = BaseActionConfig(),
    var buff: BuffActionConfig = BuffActionConfig(),
    var debuffEnemy: DebuffEnemyActionConfig = DebuffEnemyActionConfig(),
    var excavate: ExcavateActionConfig = ExcavateActionConfig(),
    var demolish: ExcavateActionConfig = excavate,
    var build: BuildActionConfig = BuildActionConfig(),
    var rest: RestActionConfig = RestActionConfig(),
    var idle: BaseActionConfig = BaseActionConfig()
) {
    var sit: RestActionConfig
        get() = rest
        set(value) { rest = value }

    fun isActionActiveForPlayer(actionName: String): Boolean {
        val key = actionName.lowercase().trim().replace(" ", "_")
        return when (key) {
            "cook" -> cook.enabledForPlayer
            "grow" -> grow.enabledForPlayer
            "repair" -> repair.enabledForPlayer
            "shift" -> shift.enabledForPlayer
            "fish" -> fish.enabledForPlayer
            "nightmare" -> nightmare.enabledForPlayer
            "light" -> light.enabledForPlayer
            "scout" -> scout.enabledForPlayer
            "teleport" -> teleport.enabledForPlayer
            "attack" -> attack.enabledForPlayer
            "protect" -> protect.enabledForPlayer
            "eat" -> eat.enabledForPlayer
            "buff" -> buff.enabledForPlayer
            "debuff", "debuff_enemy" -> debuffEnemy.enabledForPlayer
            "excavate", "demolish" -> excavate.enabledForPlayer
            "build" -> build.enabledForPlayer
            "rest", "sit" -> rest.enabledForPlayer
            "idle" -> idle.enabledForPlayer
            else -> true
        }
    }

    fun isActionActiveForAI(actionName: String): Boolean {
        val key = actionName.lowercase().trim().replace(" ", "_")
        return when (key) {
            "cook" -> cook.enabledForAI
            "grow" -> grow.enabledForAI
            "repair" -> repair.enabledForAI
            "shift" -> shift.enabledForAI
            "fish" -> fish.enabledForAI
            "nightmare" -> nightmare.enabledForAI
            "light" -> light.enabledForAI
            "scout" -> scout.enabledForAI
            "teleport" -> teleport.enabledForAI
            "attack" -> attack.enabledForAI
            "protect" -> protect.enabledForAI
            "eat" -> eat.enabledForAI
            "buff" -> buff.enabledForAI
            "debuff", "debuff_enemy" -> debuffEnemy.enabledForAI
            "excavate", "demolish" -> excavate.enabledForAI
            "build" -> build.enabledForAI
            "rest", "sit" -> rest.enabledForAI
            "idle" -> idle.enabledForAI
            else -> true
        }
    }

    fun isActionActive(actionName: String): Boolean {
        return isActionActiveForPlayer(actionName) || isActionActiveForAI(actionName)
    }

    fun migrateLegacy() {
        cook.migrateLegacy()
        grow.migrateLegacy()
        repair.migrateLegacy()
        shift.migrateLegacy()
        fish.migrateLegacy()
        nightmare.migrateLegacy()
        light.migrateLegacy()
        scout.migrateLegacy()
        teleport.migrateLegacy()
        attack.migrateLegacy()
        protect.migrateLegacy()
        eat.migrateLegacy()
        buff.migrateLegacy()
        debuffEnemy.migrateLegacy()
        excavate.migrateLegacy()
        demolish.migrateLegacy()
        build.migrateLegacy()
        rest.migrateLegacy()
        idle.migrateLegacy()
    }
}
