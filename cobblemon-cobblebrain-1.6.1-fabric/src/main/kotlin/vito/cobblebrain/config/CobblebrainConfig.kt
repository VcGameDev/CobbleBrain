package vito.cobblebrain.config

class CobblebrainConfig {
    // ================= AI PROVIDER SETTINGS =================

    // API key usada para autenticação com o sistema de IA.
    // Para OpenAI‑compatíveis: Bearer token.
    // Para Google AI Studio: chave da API do Google.
    var apiKey: String = "YOUR_API_KEY"

    // Base URL da API.
    // Exemplos:
    //  - OpenAI: https://api.openai.com
    //  - OpenRouter: https://openrouter.ai/api
    //  - Google AI Studio (Gemma/Gemini): https://generativelanguage.googleapis.com
    var apiBaseUrl: String = "https://generativelanguage.googleapis.com"

    // Nome do modelo de IA.
    // Exemplos:
    //  - Gemini: gemini-1.5-pro
    //  - Gemma: gemma-7b-it
    //  - OpenAI: gpt-4.1-mini
    //  - OpenRouter: anthropic/claude-3.5-sonnet
    var aiModel: String = "gemma-3-12b-it"

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
    var lowTokenMode: Boolean = true

    // Determines if Pokémon talk when someone is hurt
    val dialogueOnDamage: Boolean = true

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
}
