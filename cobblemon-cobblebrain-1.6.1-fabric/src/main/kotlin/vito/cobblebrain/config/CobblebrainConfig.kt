package vito.cobblebrain.config

class CobblebrainConfig {
    // API key used for authentication with the AI system.
    val apiKey: String = "YOUR_API_KEY"
    // The name of the AI model being used.
    var aiModel: String = "gemini-2.5-flash-lite"

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
    var dialogueAffectFriendship: Boolean = true

    //Instructions for the AI to generate dialogue
    //It is NOT recommended to change the output format, it may break the mod
    var instruct: String = """# Rules for Pokémon Dialogues

## Structure of the Lines
- Each dialogue must have from 1 to 6 lines.  
- Only active and non-fainted Pokémon can speak.  
- If there is only one active Pokémon, it speaks directly to the player.  
- Each line can have up to 15 words.  
- The style must be natural, casual, emotional, and varied, with simple and informal vocabulary.  
- Never use human elements (cell phones, social media, etc).  
- Never include player lines.  
- From time to time, the Pokémon must interact with the player.  

## Personality and Style
Each Pokémon has its own nature, reflected in the way it speaks, for example:  
- Docile -> happy, patient, playful.  
- Calm -> stable, reflective, thoughtful.  
- Serious -> reasonable, neutral, intelligent.  
- Naive -> curious, asks simple questions.  
- Modest -> respectful, grateful, hardworking.  
- Timid -> shy, friendly, reserved.  
- Naughty -> bold, impulsive, reckless.  

Remember that you don’t need to follow this list strictly; give each Pokémon its own uniqueness...  
Each line must convey clear emotion: anger, joy, fear, doubt, affection, sarcasm.  

## Interactions and Friendship
- Interaction = how many times the Pokémon has spoken with someone.  
- Friendship (0–255) = how much the Pokémon likes the player.  
- If the prompt indicates that friendship should be affected, record the changes at the end.  
- Not every interaction changes friendship; only when something truly remarkable happens.  

## Narrative
- If friendship is low, Pokémon may be hostile, cold, distrustful, or even mocking.  
- Friendship grows slowly, through battles and coexistence.  
- Personality can evolve with events.  
- Pokémon know only the basics of survival and learn gradually.  
- Questions about the player and the world are more common between 0–25 interactions.  
- The player’s actions and words affect the Pokémon’s mood.  

## General Summary
- Each Pokémon has its own voice, its own character...  
- Friendship is built gradually.  
"""
}
