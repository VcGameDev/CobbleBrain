# Roadmap – CobbleBrain

---

## Index
- [0.5.0](#050)
- [0.5.1](#051)
- [0.6.0](#060)
- [0.7.0](#070)
- [0.8.0](#080)
- [0.9.0](#090)
- [1.0.0](#100)
- [Bugs](#bugs)

---

## 0.5.0

- [X] Add language option in config  
- [X] Add option to make friendship optional in config  
- [X] Perform tests: singleplayer world, multiplayer (LAN), language, API key persistence  
- [X] Create CurseForge page for the mod  
- [X] Perform full virus scan on project folders  
- [X] Create tutorial explaining how to play the mod (account creation and prompt editing)  
- [X] Create GitHub repository  
- [X] Comment code and variables in English  

---

## 0.5.1

- [X] Fix: moveset only returned ID  
- [X] Player message reappears in chat when using `/msgpk`  
- [X] Add welcome message when entering the world, explaining `/msgpk` and `cobblebrain.json`  
- [X] Add Pokémon UUIDs in prompt to differentiate individuals of the same species  
- [X] Add configurable value for chance of spontaneous Pokémon dialogues  
- [X] Add option to toggle AI processing warning (red message)  
- [X] Pokémon now start messages with their nickname instead of species name  
- [X] Move `output format` into the prompt instead of instruct, reducing user errors  
- [x] General bug fixes  
- [X] Create video tutorial explaining how to edit `cobblebrain.json` and what each value does  

---

## 0.6.0
- [X] Priority: basic integration with *Fight or Flight Reforged*, focused on AI knowing if Pokémon are being attacked, when they are chasing an entity and if they are attacking that entity (managed to do this without Fight or Flight.)
- [X] Priority: Redesign memory system: AI generates a short memory summary for each Pokémon (including UUID) saved and reloaded into the prompt when active  
- [X] Allow Pokémon to attack entities or protect the player if encouraged in the chat
- [X] Add Actions to pokemon (like eat, sit, protect)
- [x] add more config values for greater customization of the mod (DialogueOnDamage, DialogueOnBattle, allowPokemonPVE, allowPokemonPVP)

---

## 0.7.0
- [x] Added chat bubbles for Pokémon
- [x] Pokémon now show particle effects (change depending on whether they liked or disliked what was said)
- [x] Pokémon sounds have pitch variation (shifts depending on their reaction to a message)
- [x] Friendship with Pokémon can go down when something bad happens (configurable in cobblebrain.json)
- [x] Reduce standard token usage
      
---

## 0.8.0 (Future)
- [ ] Integration with riding/mount Pokémon
- [ ] apply buffs/debuffs depending on player input (in the pokemon battle)
- [ ] Add command to hide chat for X seconds  
- [ ] Convert some config values into commands (`dialogueAffectFriendship`, `spontaneousDialogueChance`, `listenToChat`...)  

---

## 0.9.0 (Future)
- [ ] Add fainting sensor for Pokémon in battle (check if any Pokémon has 0 HP) 
- [ ] Allow players to talk to nearby Pokémon (wild or belonging to other players)
- [ ] Switch `cobblebrain.json` to JSON5 format  
- [ ] Add config value for "blocks of interest" that Pokémon can comment 

---

## 1.0.0 (Milestone)
- [ ] Integration with proximity chat to understand spoken input (Maybe impossible :/ )
- [ ] Fix major bugs and improve AI stability  
- [ ] Add more sensors
- [ ] integration with neoforge (maybe it will not be necessary... Sinytra connector might be enough.)

---

## 🐞 Bugs

## 0.5.1
- [X] Moveset only returned ID  
- [X] Critical: Pokémon friendship reset to 0 when reaching maximum with AI  
- [X] Player gets hurt, but Pokémon says it was the player’s action  
- [X] Adjust default prompt  
- [X] Pokémon sometimes freeze (possibly caused by LookAt or jump behavior)  
- [X] AI processing message (red) sometimes gets stuck (investigate missing reset)  
- [X] Verify if Pokémon talking to themselves send messages in chat → changed to `sendSystemMessage`  
- [X] Pokémon thought they lost when they actually won battles

## 0.6.0

- [x] AI seems to struggle with switching pokemon.
- [x] Sometimes, after exiting the game and rejoining after having changed the AI model, a name of a pokemon without nickname can be displayed as null

## 0.7.0

- [x] Remove old dialogue saves and interaction system...
- [x] Difficulty in interrupting certain actions, requiring manual recall to normalize the behavior.
- [x] AI seems to struggle with switching pokemon.

## 0.8.0
- [ ] Sometimes, the AI pastes a  "/" usually on its own, sometimes after a word or two.

Writing this for the future me: Next time, don't delay fixing the roadmap... otherwise, inconsistencies will happen... again...
