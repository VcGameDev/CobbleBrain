package vito.cobblebrain.model

enum class ActionCategory(val displayName: String, val icon: String) {
    MAP("🗺️ Map & Environment", "🗺️"),
    WORLD("🌎 World & Blocks", "🌎"),
    ENTITIES("👾 Entities & Mobs", "👾"),
    POKEMON("🐾 Cobblemon & Party", "🐾"),
    PLAYER("🧍 Player & Status", "🧍"),
    ITEMS("🎒 Items & Drops", "🎒"),
    FLOW("🧩 Story Flow", "🧩"),
    INTERFACE("💬 Interface & Messages", "💬"),
    EFFECTS("✨ Effects & Audio", "✨")
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
        // 🗺️ MAP
        ActionDefinition(
            id = "SPAWN_STRUCTURE",
            category = ActionCategory.MAP,
            name = "Spawn Structure",
            icon = "🏛️",
            description = "Places a predefined NBT structure in the world at coordinates.",
            defaultParams = mapOf("structureId" to "minecraft:small_house", "posX" to "~", "posY" to "~", "posZ" to "~")
        ),
        ActionDefinition(
            id = "TELEPORT",
            category = ActionCategory.MAP,
            name = "Teleport",
            icon = "🌀",
            description = "Teleports player or target entity to specified X, Y, Z coordinates.",
            defaultParams = mapOf(
                "targetMode" to "PLAYER",
                "targetStoryTag" to "",
                "coordinates" to "~ ~ ~",
                "safePosition" to "true",
                "snapToGround" to "true",
                "maxSearchRadius" to "5",
                "searchPriority" to "CLOSEST"
            )
        ),
        ActionDefinition(
            id = "CHANGE_WEATHER",
            category = ActionCategory.MAP,
            name = "Change Weather",
            icon = "🌧️",
            description = "Modifies current world weather conditions (Clear, Rain, Thunder).",
            defaultParams = mapOf("weatherType" to "CLEAR", "durationTicks" to "6000")
        ),
        ActionDefinition(
            id = "SET_TIME_OF_DAY",
            category = ActionCategory.MAP,
            name = "Set Time of Day",
            icon = "⏰",
            description = "Adjusts world time of day (0=Day, 6000=Noon, 13000=Night, 18000=Midnight).",
            defaultParams = mapOf("timeTicks" to "1000")
        ),

        // 🌎 WORLD
        ActionDefinition(
            id = "SPAWN_BLOCK",
            category = ActionCategory.WORLD,
            name = "Place Block",
            icon = "🧱",
            description = "Places or replaces a block at specified coordinates.",
            defaultParams = mapOf(
                "blockId" to "minecraft:stone",
                "coordinates" to "~ ~ ~",
                "safePosition" to "false",
                "snapToGround" to "false",
                "maxSearchRadius" to "5",
                "searchPriority" to "CLOSEST"
            )
        ),
        ActionDefinition(
            id = "MODIFY_BLOCK_PROPERTY",
            category = ActionCategory.WORLD,
            name = "Modify Block",
            icon = "🔧",
            description = "Changes block state properties (e.g. powered=true, open=true).",
            defaultParams = mapOf("coordinates" to "~ ~ ~", "propertyKey" to "open", "propertyValue" to "true")
        ),

        // 👾 ENTITIES
        ActionDefinition(
            id = "SPAWN_ENTITY",
            category = ActionCategory.ENTITIES,
            name = "Spawn Entity",
            icon = "👾",
            description = "Spawns an entity or mob (Vanilla or mod) at specified coordinates.",
            defaultParams = mapOf(
                "entityId" to "minecraft:villager",
                "customName" to "",
                "storyTag" to "",
                "coordinates" to "~ ~ ~",
                "safePosition" to "true",
                "snapToGround" to "true",
                "maxSearchRadius" to "5",
                "searchPriority" to "CLOSEST"
            )
        ),
        ActionDefinition(
            id = "KILL_ENTITY",
            category = ActionCategory.ENTITIES,
            name = "Kill Entity",
            icon = "☠️",
            description = "Removes or slays entities in a radius or matching tag/name.",
            defaultParams = mapOf("targetMode" to "AREA_NEAREST", "entitySelector" to "@e[type=zombie,distance=..10]", "targetStoryTag" to "")
        ),
        ActionDefinition(
            id = "MODIFY_ENTITY_PROPERTIES",
            category = ActionCategory.ENTITIES,
            name = "Entity Properties",
            icon = "📊",
            description = "Adjusts health, armor, speed, custom name, and AI of target entity.",
            defaultParams = mapOf("targetMode" to "AREA_NEAREST", "entitySelector" to "@e[type=!player,distance=..5,limit=1]", "targetStoryTag" to "", "health" to "20", "speedMultiplier" to "1.0", "customName" to "", "noAi" to "false")
        ),
        ActionDefinition(
            id = "ADD_ENTITY_EFFECT",
            category = ActionCategory.ENTITIES,
            name = "Apply Entity Effect",
            icon = "🧪",
            description = "Applies a potion effect to a specific target entity.",
            defaultParams = mapOf("targetMode" to "AREA_NEAREST", "entitySelector" to "@e[type=!player,distance=..5,limit=1]", "targetStoryTag" to "", "effectId" to "minecraft:glowing", "durationSec" to "15", "amplifier" to "1")
        ),
        ActionDefinition(
            id = "ADD_AREA_EFFECT",
            category = ActionCategory.ENTITIES,
            name = "Area Effect",
            icon = "🔮",
            description = "Applies potion effect to all entities within a radius.",
            defaultParams = mapOf("effectId" to "minecraft:slowness", "radius" to "8", "durationSec" to "10", "amplifier" to "1")
        ),
        ActionDefinition(
            id = "LOOK_AT",
            category = ActionCategory.ENTITIES,
            name = "Look At / Focus Entity",
            icon = "👀",
            description = "Rotates or continuously tracks player, mob, coordinates, sky, ground, or opposite direction with duration and AI override lock.",
            defaultParams = mapOf(
                "operationMode" to "APPLY_LOOK",
                "subjectType" to "PLAYER_POKEMON",
                "subjectIdentifier" to "1",
                "referenceType" to "PLAYER",
                "referenceIdentifier" to "",
                "lookMode" to "TOWARDS_REFERENCE",
                "durationMode" to "TEMPORARY",
                "durationTicks" to "60",
                "waitForCompletion" to "false"
            )
        ),
        ActionDefinition(
            id = "MOVE_TO_BLOCK",
            category = ActionCategory.ENTITIES,
            name = "Move / Pathfind Entity",
            icon = "🚶",
            description = "Commands a Cobblemon, NPC, or mob to walk, sprint, or sneak along a path to target coordinates or story anchor.",
            defaultParams = mapOf(
                "subjectType" to "PLAYER_POKEMON",
                "subjectIdentifier" to "1",
                "targetDestinationType" to "COORDINATES",
                "destinationIdentifier" to "~0 ~0 ~5",
                "speedMode" to "WALK",
                "customSpeedMultiplier" to "1.0",
                "waitForCompletion" to "true",
                "timeoutTicks" to "100",
                "onTimeoutBehavior" to "TELEPORT_TO_DESTINATION",
                "lockPositionOnArrival" to "true"
            )
        ),
        ActionDefinition(
            id = "ANIMATION",
            category = ActionCategory.ENTITIES,
            name = "Entity Animation",
            icon = "🎬",
            description = "Plays Cobblemon or vanilla NPC/Mob animation/pose with duration and AI override lock.",
            defaultParams = mapOf(
                "animationSystem" to "COBBLEMON",
                "targetIdentifier" to "1",
                "animationId" to "battle_idle",
                "durationMode" to "TEMPORARY",
                "durationTicks" to "60",
                "waitForCompletion" to "false",
                "overridePriority" to "true"
            )
        ),
        ActionDefinition(
            id = "SET_ENTITY_TEXTURE",
            category = ActionCategory.ENTITIES,
            name = "Set Entity Texture / Skin",
            icon = "🎨",
            description = "Dynamically swaps the texture/skin of a Cobblemon or NPC/Mob using custom PNG assets from the story.",
            defaultParams = mapOf(
                "targetType" to "PLAYER_POKEMON",
                "targetIdentifier" to "1",
                "pokemonSlot" to "1",
                "textureName" to "custom_texture.png",
                "resetToDefault" to "false"
            )
        ),
        ActionDefinition(
            id = "TAG_BLOCK",
            category = ActionCategory.ENTITIES,
            name = "Manage Story Tag",
            icon = "🏷️",
            description = "Dynamically adds, removes, or clears story tags on entities, world blocks, or players.",
            defaultParams = mapOf(
                "targetCategory" to "ENTITY",
                "targetSelector" to "PLAYER_POKEMON_SLOT",
                "selectorIdentifier" to "1",
                "operation" to "ADD_TAG",
                "tagName" to "story_tag_1"
            )
        ),

        // 🐾 POKÉMON
        ActionDefinition(
            id = "SPAWN_COBBLEMON",
            category = ActionCategory.POKEMON,
            name = "Spawn Cobblemon",
            icon = "🐾",
            description = "Spawns a Pokémon with configured level, shiny status, moves, and attributes.",
            defaultParams = mapOf(
                "species" to "Pikachu",
                "level" to "5",
                "shiny" to "false",
                "storyTag" to "",
                "coordinates" to "~ ~ ~",
                "safePosition" to "true",
                "snapToGround" to "true",
                "maxSearchRadius" to "5",
                "searchPriority" to "CLOSEST"
            )
        ),
        ActionDefinition(
            id = "GIVE_POKEMON",
            category = ActionCategory.POKEMON,
            name = "Give Pokémon",
            icon = "🎁",
            description = "Adds a configured Pokémon directly to the player's party.",
            defaultParams = mapOf("species" to "Eevee", "level" to "5", "shiny" to "false")
        ),
        ActionDefinition(
            id = "MODIFY_POKEMON_PROPERTIES",
            category = ActionCategory.POKEMON,
            name = "Modify Pokémon",
            icon = "📈",
            description = "Modifies HP, EXP, level, or friendship of a party Pokémon.",
            defaultParams = mapOf("targetType" to "PLAYER_PARTY", "targetSlot" to "1", "addExp" to "500", "addLevel" to "1", "healHp" to "true")
        ),
        ActionDefinition(
            id = "CHANGE_POKEMON_PERSONALITY",
            category = ActionCategory.POKEMON,
            name = "CobbleBrain Personality",
            icon = "🎭",
            description = "Changes AI dialogue style, personality preset, and conversation tone.",
            defaultParams = mapOf("slotIndex" to "1", "personalityPreset" to "Heroic")
        ),
        ActionDefinition(
            id = "ADD_POKEMON_PARTY_EFFECT",
            category = ActionCategory.POKEMON,
            name = "Party Pokémon Effect",
            icon = "✨",
            description = "Heals, cures status conditions, or restores PP for entire party.",
            defaultParams = mapOf("healFullParty" to "true", "cureStatus" to "true")
        ),

        // 🧍 PLAYER
        ActionDefinition(
            id = "KILL_PLAYER",
            category = ActionCategory.PLAYER,
            name = "Kill Player",
            icon = "💀",
            description = "Causes immediate defeat of player.",
            defaultParams = emptyMap()
        ),
        ActionDefinition(
            id = "DAMAGE_PLAYER",
            category = ActionCategory.PLAYER,
            name = "Damage Player",
            icon = "💔",
            description = "Deals specified damage amount to player.",
            defaultParams = mapOf("damageAmount" to "4.0")
        ),
        ActionDefinition(
            id = "GIVE_ITEM",
            category = ActionCategory.PLAYER,
            name = "Give Item to Player",
            icon = "📦",
            description = "Adds items directly to player inventory.",
            defaultParams = mapOf("itemId" to "cobblemon:poke_ball", "amount" to "5")
        ),
        ActionDefinition(
            id = "REMOVE_ITEM",
            category = ActionCategory.PLAYER,
            name = "Remove Item from Player",
            icon = "🗑️",
            description = "Removes item quantity from player inventory.",
            defaultParams = mapOf("itemId" to "cobblemon:poke_ball", "amount" to "1")
        ),
        ActionDefinition(
            id = "ADD_PLAYER_EFFECT",
            category = ActionCategory.PLAYER,
            name = "Player Effect",
            icon = "⚡",
            description = "Applies potion effect (e.g. Speed, Invisibility) to player.",
            defaultParams = mapOf("effectId" to "minecraft:speed", "durationSec" to "10", "amplifier" to "1", "showParticles" to "true")
        ),

        // 🎒 ITEMS
        ActionDefinition(
            id = "SPAWN_ITEM",
            category = ActionCategory.ITEMS,
            name = "Drop Item on Ground",
            icon = "💎",
            description = "Spawns a floating dropped item on ground at coordinates.",
            defaultParams = mapOf("itemId" to "minecraft:diamond", "amount" to "1", "posX" to "~", "posY" to "~", "posZ" to "~")
        ),

        // 🧩 FLOW
        ActionDefinition(
            id = "JUMP_TO_STORY_POINT",
            category = ActionCategory.FLOW,
            name = "Jump to Story Point",
            icon = "⏩",
            description = "Transfers execution immediately to another Scene or Node.",
            defaultParams = mapOf("targetSceneId" to "", "targetNodeId" to "")
        ),
        ActionDefinition(
            id = "REWIND_TO_STORY_POINT",
            category = ActionCategory.FLOW,
            name = "Rewind Story Point",
            icon = "⏪",
            description = "Restores state and rewinds execution to previous checkpoint.",
            defaultParams = mapOf("targetSceneId" to "")
        ),
        ActionDefinition(
            id = "BEGIN_CONSTRUCTION",
            category = ActionCategory.FLOW,
            name = "Begin Construction",
            icon = "🏗️",
            description = "Marks the start of a scoped construction sequence. Holds main flow until End Construction completes.",
            defaultParams = mapOf(
                "constructionName" to "New Construction",
                "buildSpeedMode" to "INSTANT",
                "tickDelayBetweenSteps" to "5",
                "timeoutTicks" to "600"
            )
        ),
        ActionDefinition(
            id = "END_CONSTRUCTION",
            category = ActionCategory.FLOW,
            name = "End Construction",
            icon = "🏁",
            description = "Marks completion of construction, finalizes placed blocks, and releases main story flow.",
            defaultParams = mapOf(
                "finalizeTags" to "true",
                "playCompletionSound" to "true",
                "completionSoundId" to "minecraft:block.anvil.use"
            )
        ),

        // 💬 INTERFACE
        ActionDefinition(
            id = "SEND_CHAT_MESSAGE",
            category = ActionCategory.INTERFACE,
            name = "Chat Message",
            icon = "💬",
            description = "Sends a chat message with color formatting and variable support.",
            defaultParams = mapOf("messageText" to "Hello!", "messageType" to "CHAT")
        ),
        ActionDefinition(
            id = "SHOW_TITLE_SCREEN",
            category = ActionCategory.INTERFACE,
            name = "Show Title Screen",
            icon = "🎬",
            description = "Displays large Title and Subtitle in center of player screen with animations.",
            defaultParams = mapOf(
                "mainTitle" to "Quest Completed!",
                "subTitle" to "Congratulations!",
                "titleColor" to "#FFAA00",
                "fadeIn" to "10",
                "stay" to "70",
                "fadeOut" to "20"
            )
        ),
        ActionDefinition(
            id = "CHANGE_SCREEN_TINT",
            category = ActionCategory.INTERFACE,
            name = "Screen Tint / Fade",
            icon = "🎨",
            description = "Applies temporary color filter or dark fade on player screen.",
            defaultParams = mapOf("tintColor" to "#FF0000", "alpha" to "0.5", "durationSec" to "3")
        ),

        // ✨ EFFECTS
        ActionDefinition(
            id = "SPAWN_PARTICLES",
            category = ActionCategory.EFFECTS,
            name = "Spawn Particles",
            icon = "✨",
            description = "Spawns visual particles (e.g. heart, flame, totem) at location.",
            defaultParams = mapOf("particleId" to "minecraft:totem_of_undying", "count" to "20", "posX" to "~", "posY" to "~", "posZ" to "~")
        ),
        ActionDefinition(
            id = "PLAY_SOUND",
            category = ActionCategory.EFFECTS,
            name = "Play Sound Effect",
            icon = "🔊",
            description = "Plays stereo or 3D positional audio effect for player.",
            defaultParams = mapOf("soundId" to "minecraft:entity.player.levelup", "volume" to "1.0", "pitch" to "1.0")
        ),
        ActionDefinition(
            id = "PLAY_MUSIC",
            category = ActionCategory.EFFECTS,
            name = "Play Background Music",
            icon = "🎵",
            description = "Starts or stops background music track playback.",
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
            "LOOK_AT_BLOCK" -> "LOOK_AT"
            "ANIMATION_BLOCK" -> "ANIMATION"
            "NAVIGATE_ENTITY", "MOVE_TO", "PATHFIND_ENTITY", "MOVE_ENTITY" -> "MOVE_TO_BLOCK"
            "TEXTURE_BLOCK", "ENTITY_TEXTURE", "SET_TEXTURE" -> "SET_ENTITY_TEXTURE"
            "MANAGE_TAG", "TAG_ACTION", "TAG", "MANAGE_TAGS" -> "TAG_BLOCK"
            else -> id
        }
        return actions.find { it.id == normalized } ?: actions.first()
    }

    fun getAction(id: String?): ActionDefinition? {
        if (id == null) return null
        return actions.find { it.id.equals(id, ignoreCase = true) } ?: find(id)
    }
}
