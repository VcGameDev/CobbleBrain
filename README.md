# CobbleBrain – AI Dialogue System for Cobblemon

CobbleBrain is an open-source Minecraft mod that gives Pokémon a “brain,” allowing them to think, talk, and interact dynamically with the world. It integrates artificial intelligence into gameplay, making your companions more lively and responsive.

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
![Status](https://img.shields.io/badge/status-active-success.svg)

## 📖 Table of Contents
- [About](#about)
- [Features](#features)
- [Installation](#installation)
- [How to Play](#how-to-play)
- [Configuration](#Configuration)
- [Usage Recommendations](#usage-recommendations)
- [FAQ](#faq)
- [Contributing](#contributing)
- [License](#license)
- [Contact](#contact)

---

## About
CobbleBrain enhances the **Cobblemon** experience by giving Pokémon dynamic personalities and dialogue. They can talk to you, react to battles, respond to the environment, and even interact spontaneously during your adventure.

---

## Features
- Pokémon can talk to the player and to each other.  
- Dialogue influenced by friendship, nature, past interactions, and world conditions.  
- Memory system that stores previous interactions.
- Pokémon can perform actions such as cooking, repairing, and even growing trees.
- Configurable PvE/PvP, friendship, and dialogue style.  
- Supports **cloud AI models** (Google AI Studio, OpenAI, OpenRouter) and **local models** via LM Studio.

---

## Installation
1. Install **Minecraft** with **Fabric Loader**.  
2. Install **Cobblemon** and **Fabric API**.  
3. Download the latest version of **CobbleBrain** from the *Releases* tab. You can also download it from CurseForge or Modrinth.  
4. Place the `.jar` file into the `mods` folder.

---

## Usage Recommendations
- **Low-end PC / simple laptop** → Cloud AI.  
- **Powerful laptop with dedicated GPU** → Cloud AI or lightweight local models (4b–7b).  
- **Moderate–high-end PC** → Cloud AI or robust local models (8b–12b).  
- For local models, always prefer **quantized versions** (q4, q5, q8) to reduce RAM/GPU usage.

---

## How to Play

<details>
  <summary>1. Choosing AI Mode</summary>

  <details>
    <summary>Cloud Mode (Easiest)</summary>

    Cloud mode uses external AI providers to process dialogue.  

    **Steps:**  
    1. Create an account with a provider (examples: Google AI Studio, OpenAI, OpenRouter).  
    2. Generate an API key from the provider’s dashboard.  
    3. Choose a model (examples: `gemma-3-12b-it`, `gpt-4.1-mini`, `anthropic/claude-3.5-sonnet`).  
    4. Edit `/config/cobblebrain.json` with:  
       - `apiKey`: your generated key  
       - `apiBaseUrl`: provider’s official URL  
       - `aiModel`: ID of the chosen model  

  </details>

  <details>
    <summary>Local Mode</summary>

    Local mode runs AI models directly on your computer using LM Studio.
    WARNING: LOCAL MODELS MAY CAUSE PROBLEMS IF YOU RUN/INSTALL MODELS THAT ARE TOO HEAVY.

    **Steps:**  
    1. Install [LM Studio](https://lmstudio.ai) (available for Windows, Mac, Linux).  
    2. Open LM Studio and set up a folder for storing models.  
    3. Download a model (examples: LLaMA, Mistral).  
       - 4b–5b models → lightweight, fast, good for simple dialogues.  
       - 7b–8b models → balanced, deeper responses.  
       - 12b+ models → complex, detailed dialogues, requiring significant RAM and GPU.  
    4. Prefer quantized versions (q4, q5, q8) to reduce resource usage.  
    5. Start the LM Studio server; it will show a local API address (e.g., `http://localhost:port`).  
    6. Edit `/config/cobblebrain.json` with:  
       - `apiBaseUrl`: local server address (http://localhost:1234)
       - `aiModel`: ID or name of the model running in the server

  </details>

</details>

<details>
  <summary>2. Interacting with Pokémon</summary>

  - Use the command `/mpk <message>` to talk to your Pokémon.  
  - If `listenToChat = true`, any chat message can be interpreted by the AI.  
  - Enable `onlyNearbyChat = true` so only nearby players are considered.  

</details>

<details>
  <summary>3. Performance Adjustments</summary>

  - Use `lowTokenMode` for faster and lighter responses.  
  - Adjust `maxShortMemory` and `maxLongMemory` to control how much dialogue memory Pokémon retain.  

</details>

---

## Configuration (`cobblebrain.json`)

Location: `.minecraft/config/cobblebrain.json`

| Variable | Type | Description |
|----------|------|-------------|
| Variable | Type | Description |
|----------|------|-------------|
| `apiKey` | List of Strings | API key used for authentication with the AI system.|
| `apiBaseUrl` | String | Base URL of the API.<br>Examples:<br>• OpenAI: `https://api.openai.com`<br>• OpenRouter: `https://openrouter.ai/api`<br>• Google AI Studio (Gemma/Gemini): `https://generativelanguage.googleapis.com`<br>• Lm studio: `http://localhost:1234` |
| `aiModel` | List of Strings | Name of the AI model.<br>Examples:<br>• Gemini (Google): `gemini-2.5-flash`<br>• Gemma (Google): `gemma-3-12b-it`<br>• OpenAI: `gpt-4.1-mini`<br>• OpenRouter: `anthropic/claude-3.5-sonnet` |
| `temperature` | Double | Controls the randomness/creativity of the model’s responses.<br>Range: 0.0 (deterministic, repetitive) → 2.0 (very creative, unpredictable).<br>Recommended values:<br>• 0.0–0.3 → factual, precise answers<br>• 0.7–1.0 → balanced, natural conversation<br>• 1.2+ → highly creative or exploratory outputs |
| `aiProvider` | String | Provider hint for routing in OpenRouter.<br>Example: `"DeepInfra"`, `"OpenAI"`, `"Anthropic"`.<br>Ignored for Google AI Studio. |
| `reasoningEffort` | String | Reasoning effort for models that support it.<br>Accepted values: `"high"`, `"medium"`, `"low"`, `"auto"`, `"none"`.<br>`"none"` disables the reasoning block. |
| `debugLogging` | Boolean | Enables debug logging, logs are stored in `cobblebrain-ai/logs`. |
| `dialogueInChat` | Boolean | Shows dialogue in chat. |
| `chatbubbles` | Boolean | Enables chat bubbles. |
| `pokemonTalk` | Boolean | Determines if Pokémon can talk or hear (on/off switch). |
| `allowPokemonPVP` | Boolean | Determines whether Pokémon can attack other players’ Pokémon. |
| `allowPokemonPVE` | Boolean | Determines whether Pokémon can attack mobs (except Pokémon, tamed mobs, and non‑aggressive mobs with a tag). |
| `lowTokenMode` | Boolean | When active, omits some world information to use fewer tokens. |
| `dialogueOnDamage` | Boolean | Determines if Pokémon talk when someone is hurt. |
| `dialogueOnBattle` | Boolean | Determines whether Pokémon speak when something related to battle happens. |
| `spontaneousDialogueChance` | Double | Chance for the AI to start spontaneous dialogue (e.g., Pokémon speaking on their own during idle moments). |
| `requestTimeoutSeconds` | Long | Request timeout in seconds (local models may need longer). |
| `listenToChat` | Boolean | Enables or disables listening to regular player chat.<br>If false, the AI ignores all non‑command messages. |
| `EXPERIMENTAL: onlyNearbyChat` | Boolean | If true, the AI only listens to chat messages from nearby players.<br>Only applies if `listenToChat` is also true. |
| `maxShortMemory` | Int | Maximum short memory size of each Pokémon. |
| `maxLongMemory` | Int | Maximum long memory size of each Pokémon. |
| `selectedLanguage` | String | The language selected for the AI to respond. |
| `decreaseFriendship` | Boolean | Defines whether dialogue decreases the Pokémon’s friendship with players. |
| `increaseFriendship` | Boolean | Defines whether dialogue increases the Pokémon’s friendship with players. |
| `showFriendship` | Boolean | Defines whether friendship is shown in chat. |
| `instruct` | String | User prompt to generate dialogues.<br>|
| `outputFormat` | String | System instructions for the AI to generate dialogues.<br>⚠️ Not recommended to change the output format; doing so may break the mod. |
| `keyRotation` | Boolean | Enable or disable API key rotation when trigger errors occur. |
| `modelRotation` | Boolean | Enable or disable model rotation when trigger errors occur. |
| `keyRotationTrigger` | List[Int] | List of HTTP status codes that trigger API key rotation. |
| `modelRotationTrigger` | List[Int] | List of HTTP status codes that trigger model rotation. |

---

## Pokémon Actions

<details>
  <summary>Type-based Actions</summary>

  <details>
    <summary>Cook (Fire)</summary>
    Can cook food and smelt ores.  
    5% chance of item turning into charcoal.
  </details>

  <details>
    <summary>Grow (Plant)</summary>
    Grows tree saplings and crops.
  </details>

  <details>
    <summary>Repair (Metal)</summary>
    Repairs tools and weapons up to a certain durability threshold.
  </details>

  <details>
    <summary>Shift (Ghost)</summary>
    Transports the player to an alternate dimension.  
    Player becomes invisible, gains increased speed and jump height, but suffers from high weakness.
  </details>

</details>

---

<details>
  <summary>General Actions</summary>

  <details>
    <summary>Attack</summary>
    Pokémon attacks any mobs close to it.
  </details>

  <details>
    <summary>Protect</summary>
    Pokémon targets hostile mobs nearest to the player; if none are found, it follows the player.
  </details>

  <details>
    <summary>Eat</summary>
    Pokémon consumes edible items dropped on the ground (excluding powerful items like golden apples).  
    Food effects will be added in future updates.
  </details>

  <details>
    <summary>Buff</summary>
    Pokémon grants the player a positive status effect based on its primary type (e.g., regeneration, speed).
  </details>

  <details>
    <summary>Debuff</summary>
    Pokémon applies a negative status effect to nearby mobs based on its primary type (e.g., slowness, weakness).
  </details>

  <details>
    <summary>Sit</summary>
    Pokémon stays fixed in place, ignoring other actions.
  </details>

  <details>
    <summary>Idle</summary>
    Pokémon cancels all active commands and returns to normal behavior.
  </details>

</details>


---

## FAQ

<details>
  <summary>Can I play with a free model?</summary>
  Yes, many providers offer free models, but usually with usage limits.
</details>

<details>
  <summary>Pokémon responses are too slow. Why?</summary>
  It depends on:  
  1. Model size (larger models are slower).  
  2. Prompt length.  
  3. Internet connection quality.  
  4. Provider traffic load.  
</details>

<details>
  <summary>How can I speed up Pokémon responses?</summary>
  1. Shorten the prompt.  
  2. Enable `lowTokenMode`.  
  3. Use smaller/quantized models.  
  4. Adjust `maxShortMemory` and `maxLongMemory`.  
  All these settings are in cobblebrain.json inside the config folder.
</details>

<details>
  <summary>I have issues with my key or provider. What should I do?</summary>
  CobbleBrain only bridges the game and the provider. If problems occur, contact the provider’s support directly.
</details>

<details>
  <summary>I found a bug / have a suggestion / have a question.</summary>
  - Bugs and suggestions: open an *issue* on GitHub.  
  - Questions: join the official Cobblemon server, go to the content-zone-help section, and search for CobbleBrain.  
</details>

<details>
  <summary>Does the mod collect personal data?</summary>
  No. The mod only forwards the player’s prompt to the chosen AI provider and returns the response.  
  Local logs are generated only if `debugLogging = true`.  
</details>

<details>
  <summary>Can I use local models?</summary>
  Yes, via LM Studio. But be careful: large models may cause crashes or require strong hardware.  
</details>

<details>
  <summary>I want to revert my cobblebrain.json to default. What should I do?</summary>
  Delete or rename your current `cobblebrain.json` file inside `.minecraft/config/`.  
  When you restart the game, the mod will automatically generate a new config file with the default values.  
</details>

---

## Contributing
- Open *issues* for bugs or suggestions.  
- Read [CONTRIBUTING.md](./CONTRIBUTING.md) before submitting code.  

---

## License
This project is licensed under the **MIT License**. See [LICENSE](./LICENSE).

---

## Contact
Reach me via the email available in my GitHub bio.

