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
- [Bugs 0.5.1](#bugs)

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

## 0.6.0 (Planned)
- [ ] Priority: basic integration with *Fight or Flight Reforged*, focused on AI knowing if Pokémon are being attacked, when they are chasing an entity and if they are attacking that entity
- [ ] Priority: Redesign memory system: AI generates a short memory summary for each Pokémon (including UUID) saved and reloaded into the prompt when active  
- [ ] Add command to hide chat for X seconds 
- [ ] Add fainting sensor for Pokémon in battle (check if any Pokémon has 0 HP)  
- [ ] Convert some config values into commands (`dialogueAffectFriendship`, `spontaneousDialogueChance`, `listenToChat`)  

---

## 0.7.0 (Future)
- [ ] Allow Pokémon to attack entities or protect the player if encouraged in the chat with *Fight or Flight Reforged*
- [ ] Switch `cobblebrain.json` to JSON5 format  
- [ ] Add config value for "blocks of interest" that Pokémon can comment  

---

## 0.8.0 (Future)
- [ ] Integration with riding/mount Pokémon mod 
- [ ] apply buffs/debuffs depending on player input (per battle round)  
- [ ] Integration with riding/mount Pokémon mod  

---

## 0.9.0 (Future)
- [ ] Allow players to talk to nearby Pokémon (wild or belonging to other players) 
- [ ] Integration with proximity chat to understand spoken input  

---

## 1.0.0 (Milestone)

- [ ] Fix major bugs and improve AI stability  
- [ ] Add more sensors
- [ ] integration with neoforge

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
