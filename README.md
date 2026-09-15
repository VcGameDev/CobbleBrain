# Project CobbleBrain

![Version](https://img.shields.io/badge/version-1.5.3-blue.svg)
![License](https://img.shields.io/badge/license-MPL_2.0-green.svg)
![Status](https://img.shields.io/badge/status-active-success.svg)

**AI powers dynamic and emergent Pokémon dialogue, but all gameplay actions, built-in dialogue, quests, and the Story Creator can be enjoyed offline!**

---

**Discord: join the official <span style="color:#3598db"><a href="https://discord.gg/cobblemon" target="_blank" rel="nofollow">Cobblemon server</a></span>, check mods-and-plugins → CobbleBrain.**

❗**Report Bugs and Submit Suggestions [here](https://docs.google.com/forms/d/e/1FAIpQLSddvxnQP-E2gUZYEmuqquldpSFkkhLScfkcNrCm-ZeMpjIRuw/viewform?usp=dialog)!**

Note: If you notice the Pokémon not forming memories/personalities as much in previous versions, check the first question in the FAQ.

## 📖 Table of Contents
- [About](#about)
- [Features](#features)
- [Visual Story Creator (Alpha)](#visual-story-creator-alpha)
- [Installation](#installation)
- [How to Play](#how-to-play)
- [Pokémon Actions](#pokémon-actions)
- [Karma System](#karma-system)
- [Raids](#raids)
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

With the built-in **Visual Story Creator**, players can make custom interactive quests, branching RPG campaigns, minigames and more!

You can also fully customize each Pokémon's personality through the built-in **Personality Editor**, allowing every companion to develop a unique identity.

---

## Features
- Pokémon can talk to the player and to each other autonomously.
- **Speech-to-Text (Voice Input):** Talk and give commands to Pokémon directly using your microphone (requires MCMti).
- Pokémon can perform actions such as attacking, building, excavating, cooking, repairing tools, growing plants, fishing, scouting, creating light, teleporting, and more.
- **Per-Action Customization:** Toggle each action individually and customize cooldowns to balance gameplay.
- Dialogue influenced by friendship, nature, past interactions, world conditions, and memories.
- Memory system that stores previous interactions.
- Pokémon Personality Editor for customizing Traits, Quirks, Likes, Dislikes, and other personality attributes.
- Wild Pokémon quests and karma system that react to your actions.
- Raid events triggered when Pokémon species become hostile toward you.
- Fully configurable AI prompts, gameplay settings, gameplay systems, and behaviors through the Mod Menu configuration screen.
- Supports cloud AI models (Google AI Studio, OpenAI, OpenRouter, Player2), local models via LM Studio and Player2, and Offline gameplay.
- Multiplayer compatible, with each player managing their own AI processing.

---

## Visual Story Creator (Alpha)

The Story Creator is an in-game editor with visual scripting (blocks) to build campaigns, quests and minigames integrated with Cobblemon.

The goal is to let any player create Pokémon stories/fan-games in a simple, familiar environment. The system also supports emergent AI narratives to generate dynamic lines and progression while keeping full script and direction control in your hands (AI usage is completely *OPTIONAL*, the editor works perfectly fine without AI features).

Access the editor in-game on the mod menu.

You can easily export your Project in-menu: just click **File** → **Export ZIP**, select the location where you want to export and you're done—your project will be zipped and ready to publish! For others to play your story, they just need to place your project inside the `cobblebrain/storypacks` folder, go to **'Play story'** and enjoy!

<details>
  <summary>⚠️ IMPORTANT NOTICE (ALPHA)</summary>

At the moment, since this is the first version of the creator (ALPHA), a lot of bugs and future adjustments are expected. Also, I **DO NOT** recommend testing the story creator on servers or worlds you care about, **ESPECIALLY** if it's a shared story, because there might be bugs and vulnerabilities that could cause damage to the world/server, and I won’t offer support to "undo" such effects, but rather to fix the bugs that cause these effects.

</details>

---

## Installation
1. Install **Minecraft** with **Fabric Loader** (or NeoForge).
2. Install **Cobblemon** and **Fabric API** (or NeoForge equivalent).
3. Download the latest version of **CobbleBrain** from the *Releases* tab, CurseForge, or Modrinth.
4. Place the `.jar` file into the `mods` folder.
5. (Optional) Install **MCMti** if you want Speech-to-Text (Voice Input) support.

---

## Usage Recommendations
- **Low-end PC / simple laptop** → Cloud AI (Player2, Google AI Studio, OpenRouter, etc...).
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

```
Local mode runs AI models directly on your computer using LM Studio or Player2. 
WARNING: LOCAL MODELS MAY CAUSE PROBLEMS IF YOU RUN/INSTALL MODELS THAT ARE TOO HEAVY.

**Steps:**  
1. Install LM Studio (https://lmstudio.ai, available for Windows, Mac, Linux).  
2. Open LM Studio and set up a folder for storing models.  
3. Download a model (examples: LLaMA, Mistral).  
   - 4b–5b models → lightweight, fast, good for simple dialogues.  
   - 7b–8b models → balanced, deeper responses.  
   - 12b+ models → complex, detailed dialogues, requiring significant RAM and GPU.  
4. Prefer quantized versions (q4, q5, q8) to reduce resource usage.  
5. Start the LM Studio server; it will show a local API address (e.g., http://localhost:port).  
6. Open the Config menu and set:
   - apiBaseUrl: local server address (Example: http://localhost:1234) 
   - aiModel: ID or name of the model running in the server
   - localApiProvider: "lmstudio"
```

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
- With **MCMti** installed and `enableStt = true`, hold down the <kbd>H</kbd> key to talk using your microphone and release to send your message.

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

## Pokémon Actions

Use the action HUD to make all your Pokémon perform the chosen action or encourage Pokémon through chat to perform actions!  
Example: *Squirtle, defend me!* / *Bulbasaur, want to eat some berries I dropped?*

<details>
  <summary>Primary Type-based Actions</summary>

  These actions only work when the Pokémon has the right **primary** type.  
  Example: Charizard (fire, flying) can use cook, but Chandelure (ghost, fire) cannot.

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

  <details>
    <summary>Excavate (Steel)</summary>
    Digs and clears a tunnel through blocks, but each layer costs a fraction of the Pokémon's life, it stops automatically at 10% remaining life.
  </details>

</details>

<details>
  <summary>General Actions</summary>

  These actions work with any Pokémon!

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
    Pokémon grants the player a positive status effect based on its primary type (e.g., regeneration, speed).
  </details>

  <details>
    <summary>Debuff</summary>
    Pokémon applies a negative status effect to nearby mobs based on its primary type (e.g., slowness, weakness).
  </details>

  <details>
    <summary>Rest (Sit)</summary>
    Pokémon rests in place and slowly regenerates health over time (configurable heal rate).
  </details>

  <details>
    <summary>Build</summary>
    Pokémon places blocks from a marked chest to construct structures.
  </details>

  <details>
    <summary>Idle</summary>
    Pokémon cancels all active commands and returns to its normal behavior.
  </details>

</details>

---

## Karma System

Each Pokémon species tracks your behavior.

- Killing Pokémon or failing quests lowers karma.
- Helping Pokémon increases karma.

Karma influences how Pokémon react to you in future interactions.  
**To disable the karma system, go to the mod settings and turn off** `enable karma`.

---

## Raids

If your karma with a species becomes lower than -8, that species may organize a **raid**.  
A raid spawns multiple Pokémon that will attempt to defeat the player.

Worse karma = stronger raids.  
**To disable raids, go to the mod settings and turn off** `schedule raids`.

---

## Configuration

CobbleBrain can be configured directly in-game through the mod config menu screen. You can open it by pressing **Y** or typing `/cobblebrain openConfig` in chat.  
Advanced users can still edit the generated configuration files in the `/config` folder if needed.

The options below are grouped according to their category in the in-game Configuration Screen.

### AI Configuration (Client)

| Variable | Type | Description |
|--------------------------------|------|------------------------------------------------------------------------------------------------------------------------------------|
| `apiBaseUrl` | String | The base URL of the API endpoint. Examples include OpenRouter, Google AI Studio, or a local LM Studio server. |
| `useChatEndpoint` | Boolean | Automatically includes '/v1/chat/completions' in the ApiBaseUrl address. Disable it if you are having trouble accessing your AI API. |
| `Custom API Provider (customApiProvider)` | String | If apiBaseUrl is a local address (127.0.0.1), the system uses the provider name to adapt messages. Officially supported: player2, lmstudio |
| `temperature` | Double | Controls response randomness. |
| `OpenRouter Hint (aiProvider)` | String | A provider hint used for routing in OpenRouter. This is ignored when using other providers. |
| `reasoningEffort` | String | Defines the reasoning effort level for supported models. Options include high, medium, low, auto, or none. |
| `requestTimeoutSeconds` | Long | Defines the request timeout in seconds. Local models may require longer values. |
| `debugLogging` | Boolean | Enables debug logging for troubleshooting. Logs are stored in the cobblebrain-ai/logs directory. |
| `apiKey` | List of Strings | The API key used for authentication with the AI system. It can be a Bearer token or a Google API key depending on the provider. |
| `keyRotationTrigger` | List[Int] | List of HTTP status codes that trigger key rotation. Defines error conditions for switching keys. |
| `aiModel` | List of Strings | The names of the AI models to use. Examples are gemini-2.5-flash, gemma-3-12b-it. |
| `modelRotationTrigger` | List[Int] | List of HTTP status codes that trigger model rotation. Defines error conditions for switching models. |
| `keyRotation` | Boolean | Enables API key rotation when errors occur. Useful for handling invalid or expired keys. |
| `modelRotation` | Boolean | Enables model rotation when errors occur. Useful for fallback to alternative models. |
| `selectedLanguage` | String | The language the AI uses for responses. Determines dialogue output language. |
| `Recent Memories Limit (maxInteractionSaves)` | Integer | The max number of recent memories the AI/Pokémon can create. Higher values improve conversation flow but use more tokens. |
| `preferredName` | String | The preferred name the AI uses when referring to the player. |
| `offlineMode` | Boolean | Disables AI requests while keeping supported gameplay systems active. |
| `offlineTalkMode` | Boolean | Enables built-in Pokémon dialogue and reactions when Offline Mode is active. |
| `optimizedMode` | Boolean | Optimized two-step pipeline mode: evaluates interaction importance before updating memories or friendship, saving tokens and speeding up responses. |
| `psychicTranslation` | Boolean | Allows Psychic-type Pokémon to translate other Pokémon without requiring an Exp Share. |
| `enableStt` | Boolean | Enables Speech-to-Text / Voice Input via microphone with the MCMti mod. |

### Game and Interactions (Server)

| Variable | Type | Description |
|--------------------------------|------|------------------------------------------------------------------------------------------------------------------------------------|
| `Needs Pokémon Translator (needsPokemonTranslator)` | Boolean | When active, Pokémon speak normally if the player has the Exp Share equipped. Otherwise, Pokémon speak like animals. |
| `listenToChat` | Boolean | Enables listening to regular player chat. If disabled, the AI ignores non-command messages. |
| `dialogueInChat` | Boolean | Shows generated dialogue directly in the chat. |
| `chatbubbles` | Boolean | Enables chat bubbles above characters. Dialogue will appear visually instead of only in text chat. |
| `spontaneousDialogueChance` | Double | Sets the chance of spontaneous dialogue during idle moments. |
| `wildPokemonTalkChance` | Double | Sets the chance of wild Pokémon to participate in ongoing dialogues. |
| `wildQuestChance` | Double | Sets the chance for a Pokémon to offer a quest during a dialogue with wild Pokémon. |
| `lowTokenMode` | Boolean | Reduces world information sent to the AI. This helps conserve tokens and lower usage costs. |
| `scheduleRaids` | Boolean | Determines if raids can be scheduled. Raids happen when karma with a species falls below -8. |
| `forceOfflineMode` | Boolean | Forces all players to use CobbleBrain without AI, disabling AI-dependent features and adapting gameplay for offline use. |
| `disableWelcomeMessage` | Boolean | Disables the welcome message displayed when joining a world. |
| `allowClientPersonalityEditing` | Boolean | Allows servers to decide whether players can edit their Pokémon's personalities. |
| `allowPokemonPVP` | Boolean | Allows Pokémon to attack other players’ Pokémon. Disabling prevents player-versus-player battles. |
| `allowPokemonPVE` | Boolean | Allows Pokémon to attack mobs in the world. Exceptions include tamed mobs and non-aggressive tagged mobs. |
| `enableKarma` | Boolean | Enables or disables the species karma tracking system. |
| `dialogueOnDamage` | Boolean | Makes Pokémon speak when someone is hurt. Dialogue is triggered by damage events. |
| `dialogueOnBattle` | Boolean | Makes Pokémon speak during battle events. Dialogue reflects combat situations. |
| `decreaseFriendship` | Boolean | Dialogue can decrease friendship with players during negative interactions. |
| `increaseFriendship` | Boolean | Dialogue can increase friendship with players during positive interactions. |
| `showFriendship` | Boolean | Displays friendship values in chat. |

### Prompt and Output (Client)

| Variable | Type | Description |
|--------------------------------|------|------------------------------------------------------------------------------------------------------------------------------------|
| `Show Hunger (showHunger)` | Boolean | Shows or hides Pokémon hunger information from the AI. |
| `instruct` | List of Strings | Global AI instructions/prompts shaping how Pokémon behave, think, and respond. Always prefixed with `[CREATIVEPROMPT]`. |
| `Custom Output (outputFormat)` | String | Only editable via config/cobblebrain.json5 |

### Actions Manager (Server)

| Variable | Type | Description |
|--------------------------------|------|------------------------------------------------------------------------------------------------------------------------------------|
| `actionSettings` | Object | Per-action settings allowing individual enabling/disabling for players and AI, and custom cooldowns/parameters (cook, grow, repair, shift, fish, nightmare, light, scout, teleport, attack, protect, eat, buff, debuffEnemy, excavate, build, rest/sit, idle). |

### AI Capabilities (Server)
*Controls if the AI is allowed to trigger/use these systems. Some of them can be active without AI.*

| Variable | Type | Description |
|--------------------------------|------|------------------------------------------------------------------------------------------------------------------------------------|
| `useDefaultOutput` | Boolean | Uses the recommended and updated OUTPUT FORMAT of the mod version. Only disable it if you want to apply your own custom output. |
| `Enable Dialogue (outputDialogue)` | Boolean | Enables Pokémon natural language dialogue with the player. |
| `Enable Actions (outputActions)` | Boolean | Enables Pokémon to perform actions based on situation or command. |
| `Enable Friendship (outputFriendship)` | Boolean | Enables the AI to manage and update friendship levels based on interactions. |
| `Enable World Context (outputWorldContext)` | Boolean | Provides the AI with environment information (time of day, biome, weather, player status). |
| `Enable Guaranteed Catch (outputGuaranteedCatch)` | Boolean | Enables wild Pokémon to be convinced via dialogue to join you, guaranteeing the next catch. |
| `Enable Mobs Context (outputMobsContext)` | Boolean | Gives the AI awareness of nearby non-Pokémon entities. |
| `Enable Quests (outputQuests)` | Boolean | Enables the automated quest system, allowing Pokémon to offer tasks and rewards. |
| `Enable Trait Creation (enableTraits)` | Boolean | Automatically generates one Trait and one Quirk the first time a Pokémon is interacted with. |

### Pokémon Memories (Server)

| Variable | Type | Description |
|--------------------------------|------|------------------------------------------------------------------------------------------------------------------------------------|
| `Enable Memories (outputMemories)` | Boolean | Enables storing and retrieving relevant memories during conversations. |
| `maxStoredMemories` | Integer | Maximum number of memories permanently stored for each Pokémon. |
| `maxRelevantMemories` | Integer | Maximum number of relevant memories retrieved and sent to the AI during a conversation (Local Retrieval). |
| `favoriteMemorySlots` | Integer | Maximum number of favorite/pinned memories that will not be pruned during memory cleanup (Local Retrieval). |
| `AI-Driven Memory Retrieval (enableAiMemoryRetrieval)` | Boolean | Lets the AI model decide which previously stored memories should be retrieved for the current conversation (AI-Driven Retrieval). |
| `Base Candidate Memories (baseCandidateMemories)` | Integer | Sets the initial number of candidate memories gathered before they are sent to the AI (AI-Driven Retrieval). |

### Experimental (Server)
> **Warning:** These options may cause unexpected effects on the mod or the world, use with CAUTION.

| Variable | Type | Description |
|-----------------------------|------|------------------------------------------------------------------------------------------------------------------------------------|
| `characteristics` | List of Strings **(Legacy)** | Legacy configuration for defining Pokémon personalities. Replaced by the Pokémon Personality Editor. |
| `wildPokemonCanBeHostile` | Boolean | Allows wild Pokémon to become irritated and hostile toward the player through dialogue interactions. |
| `hostileDamageMultiplier` | Float | Damage multiplier applied to physical attacks from hostile wild Pokémon (default: 0.75x). |
| `Enable April Fools Actions (outputApril1)` | Boolean | Activates special joke actions (destructive, use with caution!). |
| `Enable Pokémon Language (outputPokemonLanguage)` | Boolean | Makes Pokémon speak using iconic vocalizations (e.g., 'Pika Pika'). Automatically deactivates outputDialogue when in use. |
| `Only Nearby Chat (onlyNearbyChat)` | Boolean | Restricts listening to nearby players only. Works only if listenToChat is enabled. |

---

## FAQ

<details>
  <summary>Why does it seem like the Pokémon isn’t making any more memories/personality?</summary>
  This is an intentional improvement! With Optimized Pipeline Mode, the AI updates memories, personality, and other systems only when actually necessary, instead of forcing updates on every single message.<br>In older versions, triggering everything constantly caused far more redundancy and hallucinations.
If you prefer the legacy behavior, you can disable 'Optimized Pipeline Mode' in the settings (though responses may become slower or worse in quality).</details>

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
  <summary>Can I talk to my Pokémon using my voice? How do I set it to my language?</summary>
  With the optional <b>MCMti</b> mod installed and <b>enableStt</b> enabled, you can use Speech-to-Text to talk and command your Pokémon using your microphone. Just hold down the <kbd>H</kbd> key to talk and release it to stop recording.<br><br>To set it to your language, go to the MCMti settings and change the Language setting to the language you want in the Locale Code format (e.g., <code>pt-br</code>, <code>en-us</code>...).
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
