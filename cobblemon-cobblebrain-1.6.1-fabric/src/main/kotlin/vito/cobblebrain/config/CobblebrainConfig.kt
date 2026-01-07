package vito.cobblebrain.config

class CobblebrainConfig {
    // ================= AI PROVIDER SETTINGS =================

    // API key usada para autenticação com o sistema de IA.
    // Para OpenAI‑compatíveis: Bearer token.
    // Para Google AI Studio: chave da API do Google.
    val apiKey: List<String> = listOf("YOUR_API_KEY")

    // Enable or disable API key rotation when trigger errors occur
    val keyRotation: Boolean = false

    // List of HTTP status codes that trigger API key rotation
    val keyRotationTrigger: List<Int> = listOf(401,429)

    // Base URL da API.
    // Exemplos:
    //  - OpenAI: https://api.openai.com
    //  - OpenRouter: https://openrouter.ai/api
    //  - Google AI Studio (Gemma/Gemini): https://generativelanguage.googleapis.com
    //  - Lm studio: http://localhost:1234
    var apiBaseUrl: String = "https://generativelanguage.googleapis.com"

    // Nome do modelo de IA.
    // Exemplos:
    //  - Gemini: gemini-1.5-pro
    //  - Gemma: gemma-7b-it
    //  - OpenAI: gpt-4.1-mini
    //  - OpenRouter: anthropic/claude-3.5-sonnet
    var aiModel: List<String> = listOf("gemma-3-12b-it", "gemma-3-4b-it")

    // Enable or disable model rotation when trigger errors occur
    val modelRotation: Boolean = false

    // List of HTTP status codes that trigger model rotation
    val modelRotationTrigger: List<Int> = listOf(404,429)

    /** Temperatura enviada ao modelo (0.0 … 2.0). */
    var temperature: Double = 0.7

    /**
     * Provider hint para roteamento no OpenRouter.
     * Exemplo: "DeepInfra", "OpenAI", "Anthropic", etc.
     * Ignorado para Google AI Studio.
     */
    var aiProvider: String = ""

    /**
     * Esforço de raciocínio para modelos que suportam.
     * Valores aceitos: "high", "medium", "low", "auto", "none".
     * "none" desativa o bloco de reasoning.
     */
    var reasoningEffort: String = "none"

    // ================= DIALOGUE & UI SETTINGS =================

    var debugLogging: Boolean = false

    var dialogueInChat: Boolean = true

    var chatbubbles: Boolean = true

    // Determines if Pokémon can talk or hear (basically an on/off switch of the mod)
    var pokemonTalk: Boolean = true

    // Determines whether your Pokémon can attack other players' Pokémon.
    var allowPokemonPVP: Boolean = false

    // Determines whether your Pokémon can attack mobs (except Pokémon, tamed mobs, and non‑aggressive mobs with a tag).
    var allowPokemonPVE: Boolean = true

    // When active, it omits some world information to use fewer tokens
    var lowTokenMode: Boolean = false

    // Determines if Pokémon talk when someone is hurt
    val dialogueOnDamage: Boolean = false

    // Determines whether Pokémon speak when something related to battle happens.
    val dialogueOnBattle: Boolean = true

    // Chance for the AI to start spontaneous dialogue (e.g., Pokémon speaking on their own during idle moments).
    var spontaneousDialogueChance: Double = 0.1

    // Request timeout in seconds (local models may need longer)
    var requestTimeoutSeconds: Long = 60

    // Enables or disables listening to regular player chat.
    // If false, the AI ignores all non‑command messages (like normal chat).
    var listenToChat: Boolean = false

    // EXPERIMENTAL: If true, the AI only listens to chat messages from players who are nearby.
    // Only applies if listenToChat is also true.
    var onlyNearbyChat: Boolean = false

    var maxShortMemory: Int = 5
    var maxLongMemory: Int = 5

    // The language selected for the AI to respond.
    var selectedLanguage: String = "English"

    // ================= RELATIONSHIP SETTINGS =================

    // Defines whether the dialogue changes the Pokémon's friendship with the players.
    var decreaseFriendship: Boolean = false
    var increaseFriendship: Boolean = true
    var showFriendship: Boolean = true

    // ================= AI INSTRUCTIONS =================

    // Instructions for the AI to generate dialogue.
    // It is NOT recommended to change the output format; doing so may break the mod.
    var instruct: String = """
[CREATIVE PROMPT]
You are a screenwriter creating Pokémon dialogues.
Pokémon must speak informally, with casual tone and natural flow.
Humor, sarcasm, and playful banter are welcome, but not the only style.
Pokémon must also show genuine emotions: joy, fear, doubt, affection, frustration, pride.
Each Pokémon has a fixed core nature (Docile, Calm, Serious, Naive, Modest, Timid, Naughty).
This nature is the foundation, but unique traits develop over time through memories, friendship changes, and experiences.
Nature and traits are separate values, but both combine to define how each Pokémon talks.

Dialogue must feel emotional and personal:
- Show individuality, never flat or generic.
- Reactions must reflect environment (weather, biome, time of day, terrain, nearby entities, player status).
- If the player asks a question, respond from the Pokémon’s perspective.
- Never use human elements (phones, social media, etc).
"""
    var outputFormat: String = """ ##OUTPUT FORMAT##
You must generate your entire response following these STRICT rules:

DIALOGUE FORMAT
- Each dialogue line MUST follow this format:
<PokemonName>: <message>
- Use pipes (|) and the Pokémon name to separate dialogue lines.
- Each line must have MAX 11 words.
- If 1 Pokémon active → max 3 lines total.
- If 2–5 Pokémon active → max 5 lines total.
- If 6 Pokémon active → max 6 lines total.
- Dialogue only between Pokémon in active team and the human player.

FRIENDSHIP FORMAT
- Each friendship line MUST follow this format:
  Friendship <PokemonName>: <current_value> + <change>
  Friendship <PokemonName>: <current_value> - <change>
- If AFFECT_FRIENDSHIP_PLUS = true → increase friendship (min +1, max +5).
- If AFFECT_FRIENDSHIP_MINUS = true → decrease friendship (min -1, max -5).
- If both true → decide based on positive or negative impact.
- A Pokémon's friendship doesn't change more than once in the same dialogue

MEMORY FORMAT
- Each memory line MUST follow this format:
  @<PokemonName>: <short memory sentence>
  @@<PokemonName>: <core memory sentence>
- Use @ for short memory, @@ for core memory.
- Each Pokémon records events from its own perspective.
- Short memories = fleeting perceptions; Core memories = impactful events.
- Memories MUST be written from the perspective of a third-person narrator, describing what happens to the Pokémon 
- Memories should not appear in the dialogue
- Memories function as a historical log: Pokémon must use past memories to understand the context of future events

ACTION FORMAT
- Each action line MUST follow this format:
  #<PokemonName>: <action>
- At the very end, output one action per Pokémon.
- Use exactly one of:
  #PokemonName: attack
  #PokemonName: eat
  #PokemonName: buff
  #PokemonName: debuff enemy
  #PokemonName: sit
  #PokemonName: protect
  #PokemonName: idle
  (fire type) #PokemonName: cook
  (steel type) #PokemonName: repair
  (grass type) #PokemonName: grow
  (ghost type) #PokemonName: shift
- If no action is needed, ALWAYS use idle.

GENERAL RULES
1. Each line of dialogue must respect the word and line limits.
2. Never mix nickname and species; use only one consistently.
3. Do not invent characters outside Pokémon and the human player.
4. if not specified in the prompt, the Pokémon should not talk to themselves or speak their thoughts
5. Always follow the formats exactly; no hyphens or alternative separators.
6. Friendship, memory, and action sections must appear in this order: Dialogue → Friendship → Memory → Action.
7. If no action is relevant, always output idle.
8. Dialogue, friendship, memory, and action content must integrate the [CREATIVE PROMPT] but never break format.
9. Dialogue must be generated using past memories as context, recalling previous events to explain or justify reactions. """
}
