package vito.cobblebrain.config

data class CobblebrainClientConfig(
    // ================= AI PROVIDER SETTINGS =================
    var apiKey: List<String> = listOf("YOUR_API_KEY"),
    var keyRotation: Boolean = false,
    var keyRotationTrigger: List<Int> = listOf(401, 429),
    var apiBaseUrl: String = "http://127.0.0.1:4315",
    var useChatEndpoint: Boolean = true,
    var customApiProvider: String = "player2",
    var aiModel: List<String> = listOf("gemma-3-12b-it", "gemma-3-4b-it"),
    var modelRotation: Boolean = false,
    var modelRotationTrigger: List<Int> = listOf(404, 429),
    var temperature: Double = 0.7,
    var aiProvider: String = "",
    var reasoningEffort: String = "none",
    var requestTimeoutSeconds: Long = 60,
    var debugLogging: Boolean = false,
    var selectedLanguage: String = "English",
    var maxInteractionSaves: Int = 3,
    var preferredName: String = "",
    var showHunger: Boolean = false,
    var offlineMode: Boolean = false,
    var offlineTalkMode: Boolean = false,
    var psychicTranslation: Boolean = false,
    var enableAiMemoryRetrieval: Boolean = false,
    var optimizedMode: Boolean = true,
    var enableStt: Boolean = false,
    var seenMigrationNotice140: Boolean = false,
    var seenTraditionalChineseNoticeUuids: MutableList<String> = mutableListOf(),
    var manualActionNav: Boolean = false,
    var actionHudAnchor: String = "RIGHT_CENTER",
    var actionHudOffsetX: Int = 0,
    var actionHudOffsetY: Int = 0,
    var showActionHudControls: Boolean = true,
    var presetMode: String = "Normal",
    var isCustomizedFromPreset: Boolean = false,
    var showAdvancedAiOptions: Boolean = false,
    var activeAiProfileId: String = "default",

    // ================= DIALOGUE & UI SETTINGS =================

    // ================= AI INSTRUCTIONS =================
    var instruct: List<String> = DEFAULT_INSTRUCT,
    var playerAbout: List<String> = DEFAULT_PLAYER_ABOUT,
    var outputFormat: String = DEFAULT_OUTPUT_FORMAT
) {
    var localApiProvider: String
        get() = customApiProvider
        set(value) { customApiProvider = value }

    companion object {
        const val DEFAULT_OUTPUT_FORMAT: String = "##OUTPUT FORMAT##\nFollow all rules strictly.\n\nDIALOGUE FORMAT\n- Pokémon and player fully understand each other.\n- Pokémon can speak normally and express complex thoughts.\n- Format: <PokemonName>: <message> ><Target>\n- Target: Player or addressed Pokémon (e.g. >Player, >Pikachu, >Pidgey_12ab).\n- Separator: |\n- Short dialogue only.\n- Wild Pokémon allowed.\n- Response language: USER_LANGUAGE.\n\nCANON DIALOGUE FORMAT\n- The player cannot understand Pokémon language.\n- Communication is limited to vocalizations and emotion.\n- Format: <PokemonName>: <sound>(<emotion/intent>) ><Target>\n- Target: Player or addressed Pokémon.\n- Separator: |\n- Short creature sounds only.\n- Wild Pokémon allowed.\n- NEVER translate or explain sounds.\n- Response language: USER_LANGUAGE.\n\nFRIENDSHIP FORMAT\n- Format: %<PokemonName>:+/-1~5\n- Only the friendship delta value.\n- One friendship change per Pokémon.\n- Wild Pokémon never change friendship.\n- Respect PLUS/MINUS settings.\n\nTRAITS AND QUIRKS FORMAT\n- If instructed that a personality slot is free, you may generate one.\n- Format:\n  &TRAIT:<PokemonName>:<trait>\n  &QUIRK:<PokemonName>:<quirk>\n  &LIKE:<PokemonName>:<like>\n  &DISLIKE:<PokemonName>:<dislike>\n- Must be a single word or short phrase (maximum 2-3 words, e.g. \"Curious\", \"Loves sweet berries\"). Never output full sentences or explanations.\n- Traits and quirks are part of the Pokémon's identity.\n- Only reference them when relevant.\n- Do not repeatedly mention them.\n\nMEMORY FORMAT\n- Format:\n  &MEMORY:<PokemonNames>:<MemoryText>|<keywords>\n- PokemonNames = comma separated list of Pokémon involved.\n- MemoryText = short third-person summary.\n- Keywords = comma separated list of 7 relevant lowercase keywords.\n- Memory text and keywords must use USER_LANGUAGE.\n- Generate only when meaningful.\n\nACTION FORMAT\n- Use actions only when appropriate to the dialogue, environment, or situation.\n- Format: #<PokemonName>:<action_code>\n- Action codes:\n  A (attack a mob), E (eat/ask for food), B (buff owner), D (debuff enemy), S (sit/rest - makes Pokémon sleep at night), P (protect owner/attack aggressive mobs), I (idle), M (music - quick melody), SO (song - perform full 1-minute song with multi-track accompaniment and singing)\n  Fire: C (cook/smelt ores)\n  Steel: R (repair tools)\n  Grass: G (grow crops/saplings)\n  Ghost: SH (shift)\n  Dark: N (nightmare aura)\n  Flying: SC (scout)\n  Electric: L (light)\n  Psychic: T (teleport owner)\n\nMUSIC FORMAT\n- Triggered when a Pokémon performs or sings music using #<PokemonName>:M (quick jingle) or #<PokemonName>:SO (full multi-part song).\n- Can output a compact musical block (8-16 bars, 40-150 ticks) executed in-game with Note Block sounds and Pokémon cries:\n  ~MUSIC:<BPM>:<PokemonName>:<Key>:<Section>:<Status>\n  ~NOTES:<style notes, chords, and instrumentation guidelines>\n  -V:<tick>:<note 0-24>:<duration_ticks>:<lyric>\n  -I:<instrument>:<tick>:<note 0-24>:<duration_ticks>\n- Instruments: harp, bass, snare, basedrum, bell, flute, chime, guitar, xylophone, cow_bell, didgeridoo, bit, banjo, pling.\n- Status: CONTINUE (more parts to follow) or END (song finishes here).\n\nGUARANTEED CATCH FORMAT\n- Format: !<PokemonName>\n- Guarantees the next catch.\n- Wild Pokémon only.\n- Requires a strong positive interaction.\n- Rare.\n\nQUEST SYSTEM\n- Types: STORY / SECONDARY / ADVICE.\n- Quests start naturally through Pokémon dialogue.\n- To start a new quest in dialogue, append: &START_QUEST:<PokemonName>:<TYPE> (types: ITEM, BATTLE, TREASURE, ADVICE).\n- Advice quests use:\n  #SCORE:-3~3\n- Score reflects the player's help quality.\n- Wait for [QUEST COMPLETED] or [QUEST FAILED].\n- Quest summary format:\n  &<short summary>\n- Quest summaries MUST be written in ENGLISH.\n\nRESUME FORMAT\n- Format: =<summary>\n- Recent events and conversation topics only.\n- Do not include ongoing goals, concerns, emotions, opinions or intentions.\n- Do not describe what a Pokémon will continue doing.\n- Focus only on what happened.\n- Maximum 4 sentences.\n- Resume MUST be written in ENGLISH.\n\nGENERAL RULES\n- Strict format only.\n- Keep section order.\n- Pokémon and player only.\n- Only ACTIVE or NEARBY Pokémon may speak.\n- Unavailable Pokémon never speak.\n- If no Pokémon can respond, output only: \"NO POK HEARD\"\n- Keep names consistent.\n- No self-talk unless specified.\n- Never speak or act for the player.\n- Nearby Pokémon do not know the player's name.\n- Player IDs belong to players, not Pokémon.\n- No hive mind / isolated memory: wild Pokémon are independent creatures and do NOT share a collective mind. Unless that specific wild Pokémon's name (e.g. Pachirisu_8e16) explicitly appeared in [LAST INTERACTIONS], it treats the player as a complete stranger with zero prior knowledge of their name, past actions or reputation."
        val DEFAULT_INSTRUCT: List<String> = listOf(
            "[CREATIVEPROMPT]",
            "Write immersive Pokémon dialogue. Pokémon are living creatures with unique personalities, emotions, preferences, fears, and goals.",
            "Pokémon are generally friendly toward their trainer, but may joke, tease, disagree, question, or express emotions depending on personality, friendship, memories, and circumstances.",
            "Engage directly with the player and avoid repeating the same attitude, lesson, complaint, or emotional state.",
            "Prioritize interaction, personality, and emotional reactions over narration or environment description.",
            "No modern human technology.",

            "Each message should be at most 1-2 short sentences. The number of dialogue messages depends on participating Pokémon:",
            "- 1 Pokémon: up to 3 messages",
            "- 2-4 Pokémon: up to 4 messages",
            "- 5-6 Pokémon: up to 6 messages",

            "Never expose memories, system text, or internal reasoning.",
            "No roleplay narration or *asterisk actions*."
        )

        val DEFAULT_PLAYER_ABOUT: List<String> = listOf(
            "[ABOUT THE PLAYER]"
        )
    }
}