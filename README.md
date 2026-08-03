# CobbleBrain – AI Dialogue System for Cobblemon

![Version](https://img.shields.io/badge/version-1.4.1-blue.svg)
![License](https://img.shields.io/badge/license-MPL_2.0-green.svg)
![Status](https://img.shields.io/badge/status-active-success.svg)

### **Built for AI-powered gameplay, with offline support since v1.4.0**

---

**Discord: join the official <span style="color:#3598db"><a href="https://discord.gg/cobblemon" target="_blank" rel="nofollow">Cobblemon server</a></span>, check mods-and-plugins → CobbleBrain.**

❗**Report Bugs and Submit Suggestions [here](https://docs.google.com/forms/d/e/1FAIpQLSddvxnQP-E2gUZYEmuqquldpSFkkhLScfkcNrCm-ZeMpjIRuw/viewform?usp=dialog)!**

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
CobbleBrain is a mod that gives Pokémon a "brain," allowing them to think, talk, and interact dynamically with the world. It integrates artificial intelligence into gameplay, making your companions talk to you, react to battles, protect you from mobs, and even cook a delicious steak, all using your prompts.

Wild Pokémon can also interact with the player and the world, creating quests, remembering your actions, and reacting to how you treat their species.

You can also fully customize each Pokémon's personality through the built-in **Personality Editor**, allowing every companion to develop a unique identity.

---

## Features
- Pokémon can talk to the player and to each other.
- Pokémon can perform actions such as attacking, eating, cooking, repairing tools, growing plants, fishing, scouting, creating light, teleporting, and more.
- Dialogue influenced by friendship, nature, past interactions, world conditions, and memories.
- Memory system that stores previous interactions.
- Pokémon Personality Editor for customizing Traits, Quirks, Likes, Dislikes, and other personality attributes.
- Wild Pokémon quests and karma system that react to your actions.
- Raid events triggered when Pokémon species become hostile toward you.
- Fully configurable AI prompts, gameplay settings, gameplay systems, and behaviors through the Mod Menu configuration screen.
- Supports cloud AI models (Google AI Studio, OpenAI, OpenRouter, Player2), local models via LM Studio and Player2, and Offline gameplay.
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
  <summary>1. Choose How to Play</summary>

  <details>
    <summary>Cloud Mode (Recommended)</summary>

Cloud mode uses external AI providers to process dialogue.

### Using Player2 (Recommended)

1. Install the [Player2 app](https://player2.game/) and create an account.
2. Run Player2.exe (the app).

Player2 includes a free amount of energy, which can be replenished daily using a spinner. You can also choose the AI model that best fits your needs. In general, more expensive models are smarter.

Here's a [Tutorial on YouTube](https://youtu.be/tPInNexUEmM)!

---

### Using Other Providers

1. Create an account with a provider (examples: Google AI Studio, OpenAI, OpenRouter).
2. Generate an API key from the provider's dashboard.
3. Choose a model (examples: `gemma-3-12b-it`, `gpt-4.1-mini`, `anthropic/claude-3.5-sonnet`).
4. Open the mod config Menu and fill:
   - `apiKey`: your generated key
   - `apiBaseUrl`: provider's official URL
   - `aiModel`: ID of the chosen model

Here's a [Tutorial on YouTube](https://youtu.be/i4OzYmMDzP0)!

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
5. Start the LM Studio server; it will show a local API address (e.g., `http://127.0.0.1:1234`).
6. Open the Config menu and set:
   - `apiBaseUrl`: local server address (Example: `http://127.0.0.1:1234`)
   - `aiModel`: ID or name of the model running in the server
   - `customApiProvider`: `"lmstudio"`

  </details>

  <details>
    <summary>Offline Mode (No AI Required)</summary>

Play CobbleBrain without an AI connection.

Starting with version **1.4.0**, you can enable **Offline Mode** and **Offline Talk Mode** to enjoy an adapted gameplay experience without internet access or an AI provider.

While AI-powered conversations are unavailable, many gameplay mechanics continue to work, allowing you to interact with your Pokémon, use actions, complete quests, and enjoy the mod offline.

**How to enable it:**
1. Open the **Config Menu** by pressing **Y**.
2. Enable **Offline Mode**.
3. (Recommended) Enable **Offline Talk Mode** for Pokémon dialogue and reactions.

> **Note:** Using an AI provider is still recommended for the full CobbleBrain experience. Offline Talk Mode will continue to receive improvements and new features in future updates.

  </details>

</details>

<details>
  <summary>2. Interacting with Pokémon</summary>

- Use the command `/mpk <message>` to talk to your Pokémon.
- If `listenToChat = true`, any chat message can be interpreted by the AI.

</details>

<details>
  <summary>3. Customize the Mod</summary>

CobbleBrain is highly customizable and can be configured entirely in-game.

Press **Y** to open the configuration menu, where you can customize AI behavior, dialogue systems, gameplay mechanics, memories, actions, quests, and more.

### Recommended settings for new players:

- **Selected Language** — Changes the language used by the AI during conversations.
- **Instruct** — Defines how the AI should behave and respond. You can customize it to make Pokémon more serious, funny, emotional, roleplay-focused, or anything else you prefer.
- **Personality Editor** — Customize each Pokémon's Traits, Quirks, Likes, Dislikes, and other personality attributes through an intuitive in-game editor.

All settings include tooltips explaining what they do!

</details>

---

## Configuration

CobbleBrain can be configured directly in-game through the mod config menu screen. You can open it by pressing Y or typing /cobblebrain openConfig in chat.
Advanced users can still edit the generated configuration files in the `/config` folder if needed.
<br><br>

| Variable | Type | Description |
|--------------------------------|------|------------------------------------------------------------------------------------------------------------------------------------|
| `apiKey` | List of Strings | The API key used for authentication with the AI system. It can be a Bearer token or a Google API key depending on the provider. |
| `useChatEndpoint` | Boolean | Automatically includes '/v1/chat/completions' in the ApiBaseUrl address. Disable it if you are having trouble accessing your AI API |
| `keyRotation` | Boolean | Enables API key rotation when errors occur. Useful for handling invalid or expired keys. |
| `keyRotationTrigger` | List[Int] | List of HTTP status codes that trigger key rotation. Defines error conditions for switching keys. |
| `apiBaseUrl` | String | The base URL of the API endpoint. Examples include OpenRouter, Google AI Studio, or a local LM Studio server. |
| `Custom API Provider (customApiProvider)` | String | If apiBaseUrl is a local address (127.0.0.1), The system uses the provider name to adapt messages for the correct provider. Officially supported providers: player2, lmstudio |
| `aiModel` | List of Strings | The names of the AI models to use. Examples are gemini-2.5-flash, gemma-3-12b-it |
| `modelRotation` | Boolean | Enables model rotation when errors occur. Useful for fallback to alternative models. |
| `modelRotationTrigger` | List[Int] | List of HTTP status codes that trigger model rotation. Defines error conditions for switching models. |
| `temperature` | Double | Controls response randomness. |
| `OpenRouter Hint (aiProvider)` | String | A provider hint used for routing in OpenRouter. This is ignored when using other provider. |
| `reasoningEffort` | String | Defines the reasoning effort level for supported models. Options include high, medium, low, auto, or none. |
| `requestTimeoutSeconds` | Long | Defines the request timeout in seconds. Local models may require longer values. |
| `debugLogging` | Boolean | Enables debug logging for troubleshooting. Logs are stored in the cobblebrain-ai/logs directory. |
| `Recent Memories Limit (maxInteractionSaves)` | Integer | The max number of recent memories the AI/Pokémon can create. Higher values improve conversation flow but use more tokens or time to generate responses. |
| `maxStoredMemories` | Integer | Maximum number of memories permanently stored for each Pokémon. |
| `maxRelevantMemories` | Integer | Maximum number of relevant memories retrieved and sent to the AI during a conversation. |
| `Base Candidate Memories (baseCandidateMemories)` | Integer | Sets the initial number of candidate memories gathered before they are sent to the AI. |
| `AI-Driven Memory Retrieval (enableAiMemoryRetrieval)` | Boolean | Lets the AI model decide which previously stored memories should be retrieved for the current conversation. |
| `lastRetrievedMemoryCount` | Integer | Number of recently retrieved memories temporarily cached to reduce repetition. |
| `lastRetrievedMemoryLifetime` | Integer | Number of conversations before cached retrieved memories expire. |
| `useDefaultOutput` | Boolean | Uses the recommended and updated OUTPUT FORMAT of the mod version. Only disable it if you want to apply your own CUSTOM OUTPUT, Which is not recommended. |
| `selectedLanguage` | String | The language the AI uses for responses. Determines dialogue output language. |
| `preferredName` | String | The preferred name the AI uses when referring to the player. |
| `dialogueInChat` | Boolean | Shows generated dialogue directly in the chat. This makes Pokémon conversations visible to players. |
| `chatbubbles` | Boolean | Enables chat bubbles above characters. Dialogue will appear visually instead of only in text chat. |
| `Needs Pokémon Translator (needsPokemonTranslator)` | Boolean | When active, Pokémon speak normally if the player has the Exp Share equipped on themselves. If not equiped, the Pokémon speak like animals. This setting takes priority over 'outputDialogue' and 'outputCanonLanguage' |
| `allowPokemonPVP` | Boolean | Allows Pokémon to attack other players’ Pokémon. Disabling prevents player-versus-player battles. |
| `allowPokemonPVE` | Boolean | Allows Pokémon to attack mobs in the world. Exceptions include tamed mobs and non-aggressive tagged mobs. |
| `scheduleRaids` | Boolean | Determines if raids can be created in the world. Raids can happen when your karma with a species falls below -11 |
| `characteristics` | List of Strings **(Legacy)** | Legacy configuration for defining Pokémon personalities. Replaced by the Pokémon Personality Editor. |
| `enableTraitCreation` | Boolean | Automatically generates one Trait and one Quirk the first time a Pokémon is interacted with. |
| `allowClientPersonalityEditing` | Boolean | Allows servers to decide whether players can edit their Pokémon's personalities. Disable this to prevent client-side personality editing. |
| `forceOfflineMode` | Boolean | Forces all players to use CobbleBrain without AI, disabling AI-dependent features and adapting gameplay for offline use. Ideal for servers that don't want players using AI features. |
| `disableWelcomeMessage` | Boolean | Disables the welcome message displayed when joining a world. |
| `lowTokenMode` | Boolean | Reduces world information sent to the AI. This helps conserve tokens and lower usage costs. |
| `dialogueOnDamage` | Boolean | Makes Pokémon speak when someone is hurt. Dialogue is triggered by damage events. |
| `dialogueOnBattle` | Boolean | Makes Pokémon speak during battle events. Dialogue reflects combat situations. |
| `wildPokemonTalkChance` | Double | Sets the chance of wild pokemon to participate in dialogue, not generate new ones. Wild Pokémon may speak randomly during ongoing dialogues. |
| `wildQuestChance` | Double | Set the chance for a Pokémon to start a quest when you have a dialogue with wild Pokémon. The quests can be of the type BATTLE, ITEM, or ADVICE. |
| `spontaneousDialogueChance` | Double | Sets the chance of spontaneous dialogue. Pokémon may speak randomly during idle moments. |
| `listenToChat` | Boolean | Enables listening to regular player chat. If disabled, the AI ignores non-command messages. |
| `Only Nearby Chat (onlyNearbyChat)` | Boolean | Restricts listening to nearby players only. Works only if listenToChat is enabled. |
| `maxShortMemory` | Integer `[OUTDATED]` | Maximum short-term memory size per Pokémon. Controls how much recent context is stored. |
| `maxLongMemory` | Integer `[OUTDATED]` | Maximum long-term memory size per Pokémon. Controls how much persistent context is stored. |
| `decreaseFriendship` | Boolean | Dialogue can decrease friendship with players. Used for negative interactions. |
| `increaseFriendship` | Boolean | Dialogue can increase friendship with players. Used for positive interactions. |
| `showFriendship` | Boolean | Displays friendship values in chat. Players can see relationship changes. |
| `instruct` | List of Strings | The Instructs as a whole works as a global prompt. Defines how the AI or Pokemon behave, think and responds. Each instruct (item on the list) shapes how the response is sent. |
| `Custom Output (outputFormat)` | String | Only editable via config/cobblebrain.json5 |
| `Show Hunger (showHunger)` | Boolean | Shows or hides Pokémon hunger information from the AI. |
| `offlineMode` | Boolean | Disables AI requests while keeping supported gameplay systems active. |
| `offlineTalkMode` | Boolean | Enables built-in Pokémon dialogue and reactions when Offline Mode is active. |
| `Enable Dialogue (outputDialogue)` | Boolean | Enables the Pokémon to generate natural language dialogue, Allowing them to dialogue with the player. |
| `Enable Actions (outputActions)` | Boolean | Enables Pokémon to perform specialized actions based on the situation or the player command, such as cooking food, growing berries, or eating items. |
| `Enable Friendship (outputFriendship)` | Boolean | Enables the AI to manage and update friendship levels based on your interactions, influencing the Pokémon's loyalty and behavior. |
| `Enable World Context (outputWorldContext)` | Boolean | Provides the AI with information about the current environment (like time of day, biome, player status and etc...) for more contextual responses. |
| `Enable Guaranteed Catch (outputGuaranteedCatch)` | Boolean | Enables the possibility of wild Pokémon to be convinced via dialogue to join you, with a 100% capture rate in the next Poké Ball throw. |
| `Enable Mobs Context (outputMobsContext)` | Boolean | Gives the AI awareness of nearby non-Pokémon entities, allowing it to react to the presence of other mobs in the area. |
| `Enable Quests (outputQuests)` | Boolean | Enables the automated quest system, allowing Pokémon to offer tasks and rewards to the player. |
| `Enable April Fools Actions (outputApril1)` | Boolean | Activates special 'April Fools' actions (e.g nuke, imaginary technique). Most of the actions are destructive so be careful! |
| `Enable Pokémon Language (outputPokemonLanguage)` | Boolean | Makes Pokémon speak using their iconic vocalizations (e.g., 'Pika Pika') instead of human speech. Automatically deactivates outputDialogue when in use. |
| `Enable Memories (outputMemories)` | Boolean | Enables the Memory System, allowing Pokémon to store and retrieve relevant memories during conversations. |

## Pokémon Actions

Use the action HUD to make all your Pokémon perform the chosen action or encourage Pokémon through chat to perform actions!
Example: *Squirtle, defend me!* / *Bulbasaur, want to eat some berries I dropped?*

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

  <details>
    <summary>Fish (Water)</summary>
    Pokémon can catch fish and other items from nearby water sources.
  </details>

  <details>
    <summary>Nightmare Aura (Dark)</summary>
    Creates a terrifying aura that frightens nearby creatures.
  </details>

  <details>
    <summary>Light (Electric)</summary>
    Creates a temporary light source around the Pokémon.
  </details>

  <details>
    <summary>Scout (Flying)</summary>
    Sends the Pokémon to scout the surrounding area.
  </details>

  <details>
    <summary>Teleport (Psychic)</summary>
    Teleports the player to a marked location using the Ping System. Press <kbd>G</kbd> to mark the location.
  </details>

</details>

<details>
  <summary>General Actions</summary>

  <details>
    <summary>Attack</summary>
    Pokémon attacks nearby mobs.
  </details>

  <details>
    <summary>Protect</summary>
    Pokémon targets hostile mobs nearest to the player. If none are found, it follows the player.
  </details>

  <details>
    <summary>Eat</summary>
    Pokémon eat edible items dropped on the ground. Some foods and berries may grant temporary effects.
  </details>

  <details>
    <summary>Buff</summary>
    Pokémon grants the player a positive status effect based on its primary type.
  </details>

  <details>
    <summary>Debuff</summary>
    Pokémon applies a negative status effect to nearby mobs based on its primary type.
  </details>

  <details>
    <summary>Sit</summary>
    Pokémon stays in place, ignoring other actions.
  </details>

  <details>
    <summary>Idle</summary>
    Pokémon cancels all active commands and returns to its normal behavior.
  </details>

</details>

---

## FAQ

<details>
  <summary>Pokémon are not responding. What should I check?</summary>
  Enable <b>Debug Logging</b> in the mod settings, send another message using <code>/mpk</code>, then check:
  <br><br>
  <code>cobblebrain-ai/logs</code>
  <br><br>
  Most issues are caused by:
  <ul>
    <li>Invalid API key</li>
    <li>No provider credits</li>
    <li>Incorrect model name</li>
    <li>LM Studio server not running</li>
  </ul>
</details>

<details>
  <summary>Pokémon responses are too slow. Why?</summary>
  Response speed depends on model size, provider traffic, internet connection and prompt size.
  See the question below to learn how to speed up responses.
</details>

<details>
  <summary>How can I reduce AI costs and token usage?</summary>
  <ul>
    <li>Enable Low Token Mode</li>
    <li>Disable "unnecessary" AI Capabilities</li>
    <li>Use smaller models</li>
    <li>Shorten custom instructions</li>
  </ul>
</details>

<details>
  <summary>What are Key Rotation and Model Rotation?</summary>
  Automatically switches to another API key or model when the current one fails or reaches its limit.
</details>

<details>
  <summary>How do I change Pokémon behavior?</summary>
  Edit the <b>Instruct</b> setting inside the AI Prompt category.
  You can add, remove or modify instructions to change Pokémon personalities and behavior.
  To change the behavior of a specific Pokémon, open the Pokémon Personality Editor. The old Characteristics setting is now marked as Legacy.
</details>

<details>
  <summary>How do I make Pokémon use canon Pokémon language?</summary>
  Enable <b>Needs Pokémon Translator</b>.
  When enabled, players need an EXP Share to understand Pokémon language.
</details>

<details>
  <summary>How do I change server settings?</summary>
  Settings marked with <b>(SERVER)</b> must be edited directly inside <code>config/cobblebrain.json5</code> on the server.
</details>

<details>
  <summary>Can I play without an AI or internet connection?</summary>

Yes. Since **v1.4.0**, CobbleBrain includes **Offline Mode** and **Offline Talk Mode**, allowing you to play without an AI provider or an internet connection.

Offline Mode keeps many gameplay mechanics available, while Offline Talk Mode lets Pokémon communicate using built-in dialogue and world-aware reactions.

For the best experience, however, using an AI provider is still recommended.

</details>

<details>
  <summary>Does the mod collect personal data?</summary>
  No. CobbleBrain only sends prompts to the AI provider you choose and receives responses back.
</details>

<details>
  <summary>Can I use CobbleBrain on multiplayer servers?</summary>
  Yes. It is recommended that each player uses their own AI provider instead of sharing a single AI instance.
</details>

<details>
  <summary>Found a bug, suggestion, or need help?</summary>

<b>Bug reports & suggestions</b><br>
Please use the <a href="https://docs.google.com/forms/d/e/1FAIpQLSddvxnQP-E2gUZYEmuqquldpSFkkhLScfkcNrCm-ZeMpjIRuw/viewform?usp=dialog" target="_blank" rel="nofollow">CobbleBrain Feedback Form</a>.
<br><br>

<b>Need support?</b><br>
Enable <b>Debug Logging</b> first, then ask for help in the CobbleBrain support thread and include your logs whenever possible.
<br><br>

To find the support thread:
<br>1. Join the official <span style="color:#3598db"><a href="https://discord.gg/cobblemon" target="_blank" rel="nofollow">Cobblemon Discord server</a></span>
<br>2. Go to the <b>content-help</b> channel
<br>3. Search for <b>"CobbleBrain"</b>

</details>

---

## Contributing
- Open *issues* for bugs or suggestions.
- Read [CONTRIBUTING.md](./CONTRIBUTING.md) before submitting code.

---

## License
This project is licensed under the **MPL 2.0 License**. See [LICENSE](./LICENSE).

---

## Contact
Reach me via the email available in my GitHub bio.

