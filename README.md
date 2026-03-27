# CobbleBrain – AI Dialogue System for Cobblemon

CobbleBrain is an open-source Minecraft mod that gives Pokémon a “brain,” allowing them to think, talk, and interact dynamically with the world. It integrates artificial intelligence into gameplay, making your companions more lively and responsive.

![Version](https://img.shields.io/badge/version-1.1.1-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
![Status](https://img.shields.io/badge/status-active-success.svg)

**Discord: join the official <span style="color:#3598db"><a href="https://discord.gg/cobblemon" target="_blank" rel="nofollow">Cobblemon server</a></span>, check mods-and-plugins → CobbleBrain.**

If you have installed older versions of the mod (<1.0.0) and are going to newer versions, make sure NOT to use the old json5. Let the mod generate a new json5 to play.

Disable the Catch indicator mod (present in Cobbleverse) when playing with cobblebrain.

## 📖 Table of Contents
- [About](#about)
- [Features](#features)
- [Installation](#installation)
- [How to Play](#how-to-play)
- [Configuration](#configuration)
- [Usage Recommendations](#usage-recommendations)
- [FAQ](#faq)
- [Contributing](#contributing)
- [License](#license)
- [Contact](#contact)

---

## About
CobbleBrain enhances the **Cobblemon** experience by giving Pokémon dynamic personalities and dialogue. They can talk to you, react to battles, respond to the environment, protect you from mobs, and even interact spontaneously during your adventure.

Wild Pokémon can also interact with the player and the world, creating quests, remembering your actions, and reacting to how you treat their species.

---

## Features
- Pokémon can talk to the player and to each other.
- Pokémon can perform actions such as attacking, eating, cooking, repairing tools, growing plants, and more.
- Dialogue influenced by friendship, nature, past interactions, and world conditions.
- Wild Pokémon quests and karma system that react to your actions.
- Raid events triggered when Pokémon species become hostile toward you.
- Fully configurable AI prompts, gameplay settings, and behaviors through the Mod Menu configuration screen.
- Supports cloud AI models (Google AI Studio, OpenAI, OpenRouter, Player2) and local models via LM Studio and Player2.
- Multiplayer compatible, with each player managing their own AI processing.

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

### Using Player2

1. Install the [Player2 app](https://player2.game/) and create an account.
2. Copy the `apiBaseUrl` from the app (Example: `http://127.0.0.1:4315`).
3. Open the mod config Menu and paste the value into `apiBaseUrl`.
4. Set `localApiProvider` to player2.

Optional: Choose a model directly inside the Player2 app (under Chat Model or Local LLM).  
Here's a [Tutorial on YouTube](https://youtu.be/tPInNexUEmM)!

---

### Using Other Providers

1. Create an account with a provider (examples: Google AI Studio, OpenAI, OpenRouter).
2. Generate an API key from the provider’s dashboard.
3. Choose a model (examples: `gemma-3-12b-it`, `gpt-4.1-mini`, `anthropic/claude-3.5-sonnet`).
4. Open the mod config Menu and fill:
   - `apiKey`: your generated key
   - `apiBaseUrl`: provider’s official URL
   - `aiModel`: ID of the chosen model

Here's a [Tutorial on YouTube](https://youtu.be/Th1ylIsnQlg?si=KYWN34hF8qAoB7as)!

  </details>

  <details>
    <summary>Local Mode</summary>

Local mode runs AI models directly on your computer using LM Studio or Player2.  
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
6. Open the Config menu and set:
   - `apiBaseUrl`: local server address (Example: `http://localhost:1234`)
   - `aiModel`: ID or name of the model running in the server
   - `localApiProvider`: `"lmstudio"`

  </details>

</details>

<details>
  <summary>2. Interacting with Pokémon</summary>

- Use the command `/mpk <message>` to talk to your Pokémon.
- If `listenToChat = true`, any chat message can be interpreted by the AI.

</details>

<details>
  <summary>3. Performance Adjustments</summary>

- Use `lowTokenMode` for faster and lighter responses.
- Adjust `maxShortMemory` and `maxLongMemory` to control how much dialogue memory Pokémon retain.

</details>

---

## Configuration

CobbleBrain can be configured directly in-game through the mod config menu screen. You can open it by pressing Y or typing /cobblebrain openConfig in chat.
Advanced users can still edit the generated configuration files in the `/config` folder if needed.


| Variable                       | Type | Description                                                                                                                        |
|--------------------------------|------|------------------------------------------------------------------------------------------------------------------------------------|
| `apiKey`                       | List of Strings | API key used for authentication with the AI system. Can be a Bearer token or a Google API key depending on the provider.           |
| `keyRotation`                  | Boolean | Enables API key rotation when errors occur. Useful for handling invalid or expired keys.                                           |
| `keyRotationTrigger`           | List[Int] | List of HTTP status codes that trigger key rotation. Defines error conditions for switching keys.                                  |
| `apiBaseUrl`                   | String | Base URL of the API endpoint. Examples include OpenRouter, Google AI Studio, or a local server such as LM Studio or Player2.       |
| `localApiProvider`             | String | Used when `apiBaseUrl` is local. Helps adapt requests for the correct provider. Supported: `player2`, `lmstudio`                   |
| `aiModel`                      | List of Strings | Names of the AI models to use. Multiple models can be provided for fallback/rotation.                                              |
| `modelRotation`                | Boolean | Enables model rotation when errors occur, allowing fallback to alternative models.                                                 |
| `modelRotationTrigger`         | List[Int] | List of HTTP status codes that trigger model rotation.                                                                             |
| `temperature`                  | Double | Controls response randomness. Range: 0.0 (deterministic) to 1.0 (creative). Default ~0.7.                                          |
| `aiProvider`                   | String | Provider hint used for routing in OpenRouter. Ignored for other providers.                                                         |
| `reasoningEffort`              | String | Defines reasoning level for supported models. Options: `high`, `medium`, `low`, `auto`, `none`                                     |
| `requestTimeoutSeconds`        | Long | Request timeout in seconds. Local models may require higher values.                                                                |
| `debugLogging`                 | Boolean | Enables debug logging. Logs are stored in `cobblebrain-ai/logs`                                                                    |
| `useDefaultOutput`             | Boolean | Enables the recommended and updated output_format of the mod version.                                                              |
| `selectedLanguage`             | String | Language used by the AI to generate responses.                                                                                     |
| `dialogueInChat`               | Boolean | Shows generated dialogue in the chat.                                                                                              |
| `chatbubbles`                  | Boolean | Enables visual chat bubbles above entities.                                                                                        |
| `pokemonTalk`                  | Boolean | Global toggle for Pokémon dialogue and listening.                                                                                  |
| `allowPokemonPVP`              | Boolean | Allows Pokémon to attack other players’ Pokémon.                                                                                   |
| `allowPokemonPVE`              | Boolean | Allows Pokémon to attack mobs (excluding certain exceptions).                                                                      |
| `scheduleRaids`                | Boolean | Enables raid events when karma conditions are met.                                                                                 |
| `characteristics`              | List of Strings | Custom traits for specific Pokémon. Format: `<pokemonName>: <text>`                                                                |
| `lowTokenMode`                 | Boolean | Reduces world/context data sent to AI, lowering cost and improving performance.                                                    |
| `dialogueOnDamage`             | Boolean | Triggers dialogue when entities take damage.                                                                                       |
| `dialogueOnBattle`             | Boolean | Enables dialogue during battle events.                                                                                             |
| `wildPokemonTalkChance`        | Double | Chance for wild Pokémon to participate in ongoing dialogue.                                                                        |
| `wildQuestChance`              | Double | Chance for wild Pokémon to generate quests during interaction.                                                                     |
| `spontaneousDialogueChance`    | Double | Chance for Pokémon to initiate dialogue spontaneously.                                                                             |
| `listenToChat`                 | Boolean | Enables AI to interpret normal player chat messages.                                                                               |
| `EXPERIMENTAL: onlyNearbyChat` | Boolean | Restricts chat listening to nearby players. Requires `listenToChat = true`                                                         |
| `decreaseFriendship`           | Boolean | Allows dialogue to decrease friendship levels.                                                                                     |
| `increaseFriendship`           | Boolean | Allows dialogue to increase friendship levels.                                                                                     |
| `showFriendship`               | Boolean | Displays friendship changes in chat.                                                                                               |
| `instruct`                     | List of Strings | Global prompt defining how the AI behaves, thinks, and responds. Each entry contributes to shaping dialogue style and personality. |
| `outputFormat`                 | String | System instructions for dialogue output format. Not recommended to modify.                                                         |

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
    Pokémon eat any edible item dropped on the ground.  
    Some foods and berries may grant temporary effects.
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
  Yes, many providers offer free models, but with usage limits. I recommend Deepseek V3.2 from Player2 (Default in the mod)
</details>

<details>
  <summary>My pokemon is not performing actions!</summary>
  Check in latest.log if prints like "Pokemon action detected" appear, if it appears, check if there is any mod that could interfere with the names/tags of the cobblemons, like the Catch Indicator mod and then disable them.
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
  1. Shorten the instruct.  
  2. Enable `lowTokenMode`.  
  3. Use smaller/quantized models.  
  4. Adjust `maxShortMemory` and `maxLongMemory`.  
  All these settings are in cobblebrain.json5 inside the config folder.
</details>

<details>
  <summary>I have issues with my key or provider. What should I do?</summary>
  CobbleBrain only bridges the game and the provider. If problems related to the provider occurs, contact the provider’s support directly.
</details>

<details>
  <summary>I found a bug / have a suggestion / have a question.</summary>
  Join the official <span style="color:#3598db"><a href="https://discord.gg/cobblemon" target="_blank" rel="nofollow">Cobblemon discord server</a></span>, check content-zone-help then search CobbleBrain.
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
  Delete or rename your current `cobblebrain.json5` file inside the config folder.  
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

