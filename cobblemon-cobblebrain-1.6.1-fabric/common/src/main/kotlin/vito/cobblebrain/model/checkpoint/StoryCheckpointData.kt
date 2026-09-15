package vito.cobblebrain.model.checkpoint

data class CheckpointPlayerPosition(
    val x: Double = 0.0,
    val y: Double = 64.0,
    val z: Double = 0.0,
    val pitch: Float = 0.0f,
    val yaw: Float = 0.0f,
    val dimension: String = "minecraft:overworld"
)

data class CheckpointPlayerStats(
    val health: Float = 20.0f,
    val hunger: Int = 20
)

data class CheckpointItemData(
    val slot: Int = 0,
    val itemId: String = "minecraft:air",
    val count: Int = 1,
    val nbt: String? = null
)

data class CheckpointPlayerData(
    val position: CheckpointPlayerPosition = CheckpointPlayerPosition(),
    val stats: CheckpointPlayerStats = CheckpointPlayerStats(),
    val mainInventory: List<CheckpointItemData>? = null,
    val armorAndOffhand: List<CheckpointItemData>? = null
)

data class CheckpointPokemonData(
    val species: String = "",
    val level: Int = 1,
    val currentHp: Int = 10,
    val maxHp: Int = 10,
    val nickname: String? = null,
    val status: String? = null,
    val moves: List<String> = emptyList()
)

data class CheckpointWorldData(
    val timeOfDay: Long = 1000L,
    val isRaining: Boolean = false,
    val isThundering: Boolean = false
)

data class StoryCheckpointData(
    val profileId: String = "checkpoint_1",
    val scope: String = "PLAYER",
    val savedAt: Long = System.currentTimeMillis(),
    val variables: Map<String, Any> = emptyMap(),
    val playerData: CheckpointPlayerData? = null,
    val cobblemonParty: List<CheckpointPokemonData> = emptyList(),
    val worldState: CheckpointWorldData? = null,
    val questProgress: Map<String, Int> = emptyMap()
)
