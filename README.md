# CobbleBrain – AI Dialogue System for Cobblemon

CobbleBrain is an open-source Minecraft mod that gives Pokémon a "brain," allowing them to think, talk, and interact with their surroundings in dynamic ways.  
Easy to set up and play!

![Version](https://img.shields.io/badge/version-0.5.1-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
![Status](https://img.shields.io/badge/status-active-success.svg)



---

## Table of Contents
- [About](#about)
- [Features](#features)
- [Installation](#installation)
- [How to Play](#how-to-play)
- [Configuration](#configuration (`cobblebrain.json`))
- [Development Status](#development-status)
- [FAQ](#faq)
- [Contributing](#contributing)
- [License](#license)
- [Contact](#contact)

---

## About

CobbleBrain enhances the **Cobblemon** experience by giving Pokémon dynamic personalities and dialogue.  
They can talk to you, interact with teammates, and even react to the world around them — making your adventures more immersive.

---

## Features

- Pokémon can talk to the player and to other team members.  
- Dialogue is influenced by:
  - Pokémon’s nature  
  - Friendship level  
  - Past interactions  
  - World conditions (weather, time of day, biome)  
- Pokémon can assist you on your adventures, warning you about nearby ores or blocks of interest *(configurable soon)*.  
- Your actions and words affect friendship with your Pokémon.  
- AI-driven dialogue system with memory of past interactions.  

---

## Installation

1. Download and install **Minecraft** with **Fabric (mod loader)** (compatible version).  
2. Install **Cobblemon and Fabric API (compatible version)**.  
3. Download the latest release of **CobbleBrain** from the [Releases](#) page.  
4. Place the `.jar` file into your `mods` folder, along with cobblemon and Fabric API.  

---

## How to Play

1. **Create a Google AI Studio account**  
   - ⚠️ *NOT Google Cloud!*  
   - Just accept the terms with your Google account.  

2. **Get your free API key**  
   - Copy or generate one in Google AI Studio.  
   - Paste it into the `apiKey` field inside `cobblebrain.json` in the `config` folder.  
   - No billing required.  

3. **Talk to your Pokémon**  
   - Use `/msgpk <message>`  
   - Or enable `listenToChat` in `cobblebrain.json` to let the AI listen to normal chat.  

🔗 [YouTube tutorial available here!](#)

> ⚠️ **Never share your API key with others!**

---

## Configuration (`cobblebrain.json`)

Located in: `run/config/cobblebrain.json`

| Field                     | Type     | Description |
|---------------------------|----------|-------------|
| `apiKey`                  | String   | Required for the mod to communicate with the AI server |
| `selectedLanguage`        | String   | Language the AI will use |
| `instruct`                | String   | Custom AI instructions (recommended to use default as a base) |
| `maxDialogueSaves`        | Int      | Number of past dialogues saved as "memory" (recommended: 3) |
| `dialogueAffectFriendship`| Boolean  | Whether dialogue affects friendship level |
| `spontaneousDialogueChance` | Double | Probability of spontaneous dialogue (default: 0.15) |
| `visibleAiWarnings`       | Boolean  | Show mod-related warning messages in chat |
| `listenToChat`            | Boolean  | If true, AI listens to normal chat messages |
| `onlyNearbyChat` *(exp)*  | Boolean  | If enabled, AI only listens to nearby players (15 blocks) |

---

## Development Status

- Current version: **0.5.1**  
- Actively in development.  
- Open-source: free to download, modify, and improve.  
- If you use this code (or parts of it) in another mod or software, please give proper credit.  

---

## Roadmap

You can check the full roadmap here: [ROADMAP.md](./ROADMAP.md)

## FAQ

**Q: Can I say anything to my Pokémon?**  
A: Technically yes, but remember it’s YOUR Google key being used. Keep interactions reasonable.  

**Q: Does the mod share my API key?**  
A: No. The key is stored locally and only used to communicate with Google’s servers.  

**Q: Will this mod continue to be updated?**  
A: Yes, but no guarantees. It’s open-source so others can improve or fork it.  

**Q: Can I use this mod in a modpack?**  
A: Yes, but beta versions may cause unknown issues or incompatibilities.  

---

## Contributing

Contributions are welcome!  
- Open an [issue](#) for bugs or feature requests.
- Before submitting code changes, read through [CONTRIBUTING](CONTRIBUTING) 

---

## License

This project is licensed under the **MIT License**.  
See the [LICENSE](LICENSE) file for details.  

---

## Contact

If you have questions, suggestions, or problems:  
- Leave a comment in the project.  
- Reach out via the YouTube channel (linked in the project). 
