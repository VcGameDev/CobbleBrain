# Changelog – CobbleBrain

All notable changes to this project will be documented here.  

---

## Index
- [0.5.1](#051---2025-10-27)
- [0.5.0](#050---2025-10-20)

---

## [0.5.1] - 2025-10-27

### New Features
- Players can use the normal chat (without `/msgpk`) to talk to their Pokémon again by activating the `listenToChat` value.
- Now the moveset of Pokémon is reported correctly in AI.
- Added a welcome message when entering the world, explaining the `/msgpk` command and the `cobblebrain.json` file.
- The `/msgpk` command now makes the player’s message reappear in their chat.
- Pokémon can now use their nicknames at the start of messages instead of their species name.
- Implemented use of UUIDs in the prompt to differentiate Pokémon of the same species.

#### New changes in `cobblebrain.json`
- Added the configurable value `spontaneousDialogueChance` to set the chance of Pokémon talking to each other on their own.
- Added the configurable value `listenToChat` to determine whether players can use the normal chat (without the `/msgpk` command) to talk to their Pokémon.
- Added the configurable value `visibleAiwarnings` to decide whether or not mod spam warnings (and others in the future) appear in the chat. (Does not include warnings from Google’s own AI.)
- Removed `dialogueFriendshipRegex` since it was useless as a value outside the mod modification environment.
- **(EXPERIMENTAL)** Added the configurable value `onlyNearbyChat` so that only players within a radius of 15 blocks can be heard by Pokémon. `listenToChat` needs to be active for this option to work.
- Changed handling of `output format` and `affect_friendship`: it is now placed directly in the prompt instead of the instruct value, reducing user errors.
- The default value of the `aiModel` has been changed to **gemini-2.5-flash-lite** (which has a request per day quota 5x larger than the other model — more requests per day = more messages available per day).  
  The `instruct` has also been changed for a more stable and player-friendly experience.

---

### Bug Fixes
- Fixed issue where the Pokémon moveset returned an ID in the prompt.
- Fixed critical bug where Pokémon friendship would reset to 0 when reaching the maximum with AI.
- Fixed issue where Pokémon thought they lost even after winning battles.
- Fixed bug where Pokémon got hurt and thought the player had gotten hurt.
- Fixed behavior where Pokémon talking to themselves would send messages in chat — now uses `sendSystemMessage`.

## [0.5.0] - 2025-10-20

- First public release of cobblebrain!

### Added
- Complete installation and usage tutorial.
- CurseForge page.

#### New changes in `cobblebrain.json`
- Language option in cobblebrain.json.
- Option to make friendship optional in cobblebrain.json.

---

