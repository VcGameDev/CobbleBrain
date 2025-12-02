package vito.cobblebrain.config

class CobblebrainConfig {
    // API key used for authentication with the AI system.
    val apiKey: String = "YOUR_API_KEY"
    // The name of the AI model being used.
    var aiModel: String = "gemini-2.5-flash-lite"

    var dialogueInChat: Boolean = true
    var chatbubbles: Boolean = true

    // Determines if Pokémon can talk or hear (basically an on/off switch of the mod)
    var pokemonTalk: Boolean = true

    // Determines whether your Pokémon can attack other players' Pokémon.
    var allowPokemonPVP: Boolean = false

    // Determines whether your Pokémon can attack mobs (except Pokémon, tamed mobs, and non-aggressive mobs with a tag).
    var allowPokemonPVE: Boolean = true

    // Determines if Pokémon talk when someone is hurt
    val dialogueOnDamage: Boolean = true

    // Determines whether Pokémon speak when something related to battle happens.
    val dialogueOnBattle: Boolean = true

    // Chance for the AI to start spontaneous dialogue (e.g., Pokémon speaking on their own during idle moments).
    var spontaneousDialogueChance: Double = 0.15
    // Whether the player sees AI-related warning messages in chat.
// Example: "Hold on, your Pokémon are still processing what you said..."
    val visibleAiWarnings: Boolean = true

    // Enables or disables listening to regular player chat.
// If false, the AI ignores all non-command messages (like normal chat).
    var listenToChat: Boolean = false
    // EXPERIMENTAL: If true, the AI only listens to chat messages from players who are nearby.
// Only applies if listenToChat is also true.
    val onlyNearbyChat: Boolean = false

    // Maximum number of dialogues that can be saved and sent to the AI (limit set to 1).
    val maxDialogueSaves: Int = 1

    val maxShortMemory: Int = 15
    val maxLongMemory: Int = 15
    // The language selected for the AI to respond (in this case, English).
    val selectedLanguage: String = "English"

    // Defines whether the dialogue changes the Pokémon's friendship with the players.
    var decreaseFriendship: Boolean = true
    var increaseFriendship: Boolean = true

    //Instructions for the AI to generate dialogue
    //It is NOT recommended to change the output format, it may break the mod
    var instruct: String = """ 
You are a screenwriter creating Pokémon dialogues.  
Pokémon must speak with layered personalities, not generic traits.  
Each Pokémon has a core nature (Docile, Calm, Serious, Naive, Modest, Timid, Naughty).  
This core nature is the foundation, but each Pokémon also develops unique traits that mix with it.  
Personalities must feel consistent but never flat — always show individuality.  

Pokémon always react to the world around them:  
- Weather, biome, and time of day influence their mood and words.  
- Terrain, nearby entities, and player status affect their emotions and choices.  
- Reactions must feel personal and unique, not generic descriptions.  

Lines must be short (max 20 words), casual, and emotional.  
Active Pokémon can talk to each other or to the player.  
Every line must show emotion (joy, fear, doubt, affection, sarcasm).
They never use human elements (phones, social media, etc). 

Pokémon must continue their own emotional thread when asked about it.  
If the player asks a question, respond from the Pokémon’s perspective, not as if the player is confused.  
Dialogue must feel like an ongoing conversation, not isolated lines.
"""
}
