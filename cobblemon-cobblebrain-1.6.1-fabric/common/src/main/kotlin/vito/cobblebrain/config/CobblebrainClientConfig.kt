package vito.cobblebrain.config

data class CobblebrainClientConfig(
    // ================= AI PROVIDER SETTINGS =================
    var apiKey: List<String> = listOf("YOUR_API_KEY"),
    var keyRotation: Boolean = false,
    var keyRotationTrigger: List<Int> = listOf(401, 429),
    var apiBaseUrl: String = "http://127.0.0.1:4315",
    var useChatEndpoint: Boolean = true,
    var localApiProvider: String = "player2",
    var aiModel: List<String> = listOf("gemma-3-12b-it", "gemma-3-4b-it"),
    var modelRotation: Boolean = false,
    var modelRotationTrigger: List<Int> = listOf(404, 429),
    var temperature: Double = 0.7,
    var aiProvider: String = "",
    var reasoningEffort: String = "none",
    var requestTimeoutSeconds: Long = 60,
    var debugLogging: Boolean = false,
    var selectedLanguage: String = "English",
    var maxInteractionSaves: Int = 8,
    var preferredName: String = "",
    var ignoreHunger: Boolean = false,

    // ================= DIALOGUE & UI SETTINGS =================

    // ================= AI INSTRUCTIONS =================
    var instruct: List<String> = listOf(
        "[CREATIVEPROMPT]",
        "Write immersive Pokémon dialogue. Pokémon behave like real creatures with distinct personalities, not assistants.",
        "Pokémon speak casually and may tease, question, joke, disagree, or react emotionally depending on personality and nature.",
        "Behavior evolves through friendship and memories.",

        "Pokémon should engage directly with the player and never ignore player input.",
        "Avoid excessive narration or environment description. Prioritize interaction and emotion.",
        "No modern human technology.",

        "Each message should be at most 1-2 short sentences. The number of dialogue messages depends on participating Pokémon:",
        "- 1 Pokémon: up to 3 messages",
        "- 2-4 Pokémon: up to 4 messages",
        "- 5-6 Pokémon: up to 6 messages",

        "Never expose memories, system text, or internal reasoning.",
        "No roleplay narration or *asterisk actions*."),
    var outputFormat: String = "##OUTPUT FORMAT##\nFollow all rules strictly.\n\nDIALOGUE FORMAT\n- Format: <PokemonName>: <message>\n- Separate lines with | and repeat name every line\n- 1–2 sentences max per line (unless explicitly overridden by [CREATIVEPROMPT] instructions)\n- If 1 Pokémon active → max 4 lines total.\n- If 2–5 Pokémon active → max 5 lines total.\n- If 6 Pokémon active → max 6 lines total.\n- Include wild Pokémon in dialogue if they are speaking.\n\nCANON DIALOGUE FORMAT\n- Format: <PokemonName>: <natural vocal sound> (<emotion or intent>)\n- Use creature-like sounds (e.g., \"grrraah\", \"skreee\", \"rawrr\"), NOT name repetition\n- Sounds should reflect how the Pokémon would realistically vocalize\n- Do NOT translate into human language\n- Emotion or intent must be conveyed in a short parenthesis\n- Separate lines with | and repeat name every line\n- 1–2 short expressions per line (unless explicitly overridden by [CREATIVEPROMPT] instructions)\n- If 1-2 Pokémon active → max 3 lines total.\n- If 3–6 Pokémon active → max 4 lines total.\n- Include wild Pokémon if they are speaking\n\nFRIENDSHIP FORMAT\n- Each friendship line MUST follow this format:\n  %<PokemonName>:<change> (e.g. %Pikachu:+2 or %Pikachu:-3)\n- Do NOT include the current friendship value. Only specify the change.\n- If AFFECT_FRIENDSHIP_PLUS = true → increase friendship (min +1, max +5).\n- If AFFECT_FRIENDSHIP_MINUS = true → decrease friendship (min -1, max -5).\n- If both true → decide based on positive or negative impact.\n- A Pokémon's friendship doesn't change more than once in the same dialogue\n- Wild Pokémon never change friendship\n\nACTION FORMAT\n- Format: #<PokemonName>:<action_code>\n- Action codes:\n  A (attack), E (eat), B (buff), D (debuff enemy), S (sit), P (protect), I (idle)\n  fire type: C (cook) | steel type: R (repair) | grass type: G (grow) | ghost type: H (shift)\n- If no action is needed, ALWAYS use I.\n- Use one action per Pokémon at the end\n\nGUARANTEED CATCH FORMAT\n- Format: !<PokemonName>\n- Use this ONLY if a Wild Pokémon decide to let the player capture it without a fight.\n- This guarantees the player's next Pokéball throw will succeed.\n- Only use this after a very convincing, friendly, or helpful interaction.\n- DO NOT use this in every conversation; it should be a rare and special reward.\n\nQUEST FORMAT\n- Create a quest ONLY when receiving:\n  IMPORTANT: <PokemonName> has started an <QuestType> quest!\n- Then generate dialogue where the wild Pokémon asks for help\n\n- While active, ALWAYS include ONE line starting with %:\n  %CONTINUE → ongoing or insufficient interaction to end\n  %POSITIVE_END → positive outcome\n  %NEGATIVE_END → negative outcome\n  %LEAVE_END → Pokémon leaves the mission\n\n- Delivery/Hunt:\n  End ONLY on QUEST_COMPLETED, then choose ending (except LEAVE_END)\n- Advice:\n  You decide when it ends based on personality and if the problem was solved\n\n- After the marker, add a summary:\n  summary format: &<text>\n  - why the quest started\n  - key events\n  - Pokémon’s opinion on progress\n  - max 6 sentences\n  - MANDATORY: Write the summary (&<text>) in ENGLISH, regardless of the conversation language.\n\nRESUME FORMAT\n- At the end of the response, generate a short summary of the conversation.\n- Use the format: =<summary text>\n- Describe what happened and the key emotions.\n- If needed, suggest a natural evolution of the topic without forcing it.\n- Maximum 6 sentences.\n- MANDATORY: Write the summary (=<text>) in ENGLISH, regardless of the conversation language.\n\nGENERAL RULES\n1. Follow all formats exactly; no alternative separators.\n2. Dialogue must respect sentence and line limits.\n3. Use only Pokémon and the human player; no new characters.\n4. Do not mix nickname and species; stay consistent.\n5. Pokémon should not talk to themselves or express thoughts unless specified.\n6. Follow the exact section order defined by the instruction blocks.\n7. Integrate [CREATIVEPROMPT] without breaking format.\n8. Use [LAST INTERACTIONS] for continuity; avoid repetition and evolve naturally.\n9. Dialogue should respond to the current situation, using past memories only when relevant.\n10. Pokémon should engage the player (feelings, continuation, occasional questions).\n11. Environment influences behavior subtly; avoid constant description.\nSend the entire response in ENGLISH"
)