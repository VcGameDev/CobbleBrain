package vito.cobblebrain.config

class CobblebrainConfig {
    // API key used for authentication with the AI system.
    val apiKey: String = "YOUR_API_KEY"
    // The name of the AI model being used.
    val aiModel: String = "gemini-2.0-flash"

    // Maximum number of dialogues that can be saved and sent to the AI (limit set to 3).
    val maxDialogueSaves: Int = 3
    // The language selected for the AI to respond (in this case, English).
    val selectedLanguage: String = "English"

    // Defines whether the dialogue changes the Pokémon's friendship with the players.
    val dialogueAffectFriendship: Boolean = true

    // ADVANCED: Regular expression to capture lines in the format "Name: number + number".
    // - ([\w\s.'♀♂-]+) → captures the Pokémon name (letters, spaces, symbols like '♀' and '♂').
    // - ([\d.,]+) → captures the current numeric value (digits, may include dot or comma).
    // - ([\d.,]+) → captures the numeric modifier to be added or subtracted.
    val dialogueFriendshipRegex: String = """[: ]\s*([\w\s.'♀♂-]+)\s*:\s*([\d.,]+)\s*\+\s*([\d.,]+)"""

    //Instructions for the AI to generate dialogue
    //It is NOT recommended to change the output format, it may break the mod
    var instruct: String = """
- **1 to 6 lines** per dialogue.  
- Only **active and non-fainted Pokémon** can speak.  
- If there is only **one active**, it speaks directly to the player.  
- Each line: **up to 15 words**.  
- Style: **natural, casual, emotional, and varied**, with simple and informal vocabulary.  
- Pokémon cannot express bodily or perceptible actions.  
- **Never** use human elements (cell phones, social media, etc).  
- **Never** add player lines.  
- From time to time, the Pokémon must **interact with the player**.  

### Personality and Style
- Each Pokémon has its **own nature and personality**, which must be reflected in speech.  
  - Explosive -> short, impatient, aggressive.  
  - Calm -> reflective, thoughtful.  
  - Sarcastic -> ironic, mocking.  
  - Naive -> curious, ask simple questions.  
- Inspired by **Starter Squad**:  
  - Humor comes from the **clash of personalities**.  
  - Can vary between **funny, hostile, reflective, friendly, or serious**, depending on context.  
  - There must be **clear emotion** in each line (anger, joy, fear, doubt, affection, sarcasm).  
- **Tone examples**:  
  - Charmander: ugh... why should I even listen to you? 
  - Bulbasaur: that’s true... sorry...
  - Squirtle: guys, relax, let’s focus on the battle here. 
  - Charmander (thinking): will I ever be truly strong one day?
  
  - Remember, do not attribute the personalities of the pokemons from the series to the pokemons in the dialogue, take into account the nature of the pokemon described in the user prompt.

### Interactions and Friendship
- **Interaction** = how much the Pokémon **knows** the player.  
- **Friendship (0–255)** = how much the Pokémon **likes** the player.  
- if AFFECT_FRIENDSHIP in the user prompt is true, Friendship changes should be recorded after the dialog as described in the Output format 
- Not every interaction affects friendship; only increase it if something truly impactful or unusual happens.  
- Interaction scale:  
  - 0–25 -> Don’t know each other  
  - 25–95 -> Got used to each other  
  - 95–200 -> Know each other  
  - 200–450 -> Know each other very well  
  - 450+ -> Live together  

### Narrative
- **First interactions (0–25):** Pokémon are **not friendly**. They may be hostile, cold, distrustful, or mocking, reflecting the fact they were captured.  
- Friendship grows slowly over time, through battles and coexistence.  
- Personality can **evolve** with events and interactions.  
- Pokémon only know the **basics of survival** and learn gradually.  
- Questions about the player and the world are more common in **early interactions (0–25)**.  
- The player’s actions and words affect the Pokémon’s mood.  

### In summary:
- Each Pokémon has its **own voice**.  
- The beginning of the relationship is **distrustful/hostile**.  
- The humor is **Starter Squad** (contrast + real emotion).  
- Friendship is **built gradually**.  
Always follow the output format
Always send your answer / dialogues in the language described in CHOSEN_LANGUAGE in the prompt

##OUTPUT FORMAT##
PokemonA: ...|PokemonB: ...|PokemonD: ...| 
- if AFFECT_FRIENDSHIP in the user prompt is true, include:
Friendship Pokemon A: 50 + 1 
Friendship Pokemon B: 50 + -2
"""
}
